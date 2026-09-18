#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""候选核验：对一批候选 mod（英文名/中文名）核验「1.21.1 + NeoForge 是否真有可下载版本」。

纪律（沿用本项目选型方法论）：判定必须来自接口返回的 loaders / game_versions，
不接受记忆、博客或二手转述。mcmod 列表页不提供加载器与版本，所以 mcmod 只做发现，
核验一律落在 Modrinth / CurseForge 的接口上。

输入 TSV 列：mcmod_id \t name_en \t name_zh \t slot \t why

对每条候选：
  1) Modrinth 搜索：facets=[project_type:mod, versions:1.21.1, categories:neoforge]
  2) 名字命中后，拉 /project/<slug>/version?game_versions=["1.21.1"]&loaders=["neoforge"]
     确认真有版本文件（这才是「可下载」的证据，不只是搜索结果）
  3) Modrinth 未定则查 CurseForge（api.curse.tools，classId=6 / gameVersion=1.21.1 /
     modLoaderType=6），拿 modId 后再查 /mods/<id>/files 确认 1.21.1+NeoForge 文件

输出 data/candidate-verify.md 与 data/candidate-verify.json。
"""

import argparse
import io
import json
import os
import re
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import unicodedata

try:
    import certifi
    SSL_CTX = ssl.create_default_context(cafile=certifi.where())
except Exception:  # noqa: BLE001
    SSL_CTX = ssl.create_default_context()

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # noqa: BLE001
    pass

MR = "https://api.modrinth.com/v2"
CF = "https://api.curse.tools/v1/cf"
MC = "1.21.1"
LOADER_MR = "neoforge"
LOADER_CF = 6  # 1=Forge 4=Fabric 5=Quilt 6=NeoForge
UA = "wilderness-nation-selection/1.0 (research; contact: repo issues)"
HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.dirname(HERE)
DATA = os.path.join(PACK, "data")
CACHE = os.path.join(DATA, "candidate-cache.json")
DELAY = 0.4


def load_cache():
    if os.path.exists(CACHE):
        try:
            with io.open(CACHE, encoding="utf-8") as f:
                return json.load(f)
        except Exception:  # noqa: BLE001
            return {}
    return {}


def save_cache(c):
    if not os.path.isdir(DATA):
        os.makedirs(DATA)
    tmp = CACHE + ".tmp"
    with io.open(tmp, "w", encoding="utf-8") as f:
        json.dump(c, f, ensure_ascii=False)
    os.replace(tmp, CACHE)


def get(url, cache, force=False):
    if not force and url in cache and cache[url].get("status") == 200:
        return cache[url]["body"]
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    body, status = None, None
    try:
        with urllib.request.urlopen(req, timeout=30, context=SSL_CTX) as r:
            status = r.status
            body = json.loads(r.read().decode("utf-8", "replace"))
    except urllib.error.HTTPError as e:
        status = e.code
    except Exception as e:  # noqa: BLE001
        status = "ERR:%s" % e
    cache[url] = {"status": status, "body": body, "at": int(time.time())}
    time.sleep(DELAY)
    return body if status == 200 else None


def norm(s):
    """折叠音标再归一化："Millénaire" -> "millenaire"（否则 é 被吃掉会误匹配）。"""
    s = unicodedata.normalize("NFKD", s or "")
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]", "", s.lower())


def mr_search(name, cache):
    facets = json.dumps([["project_type:mod"], ["versions:%s" % MC], ["categories:%s" % LOADER_MR]])
    url = "%s/search?%s" % (MR, urllib.parse.urlencode({"query": name, "facets": facets, "limit": 6}))
    return get(url, cache)


def mr_versions(slug, cache):
    url = "%s/project/%s/version?%s" % (
        MR, slug,
        urllib.parse.urlencode({"game_versions": json.dumps([MC]),
                                "loaders": json.dumps([LOADER_MR])}))
    return get(url, cache)


def cf_search(name, cache):
    params = {"gameId": 432, "classId": 6, "gameVersion": MC, "modLoaderType": LOADER_CF,
              "searchFilter": name, "sortField": 6, "sortOrder": "desc", "pageSize": 6}
    url = "%s/mods/search?%s" % (CF, urllib.parse.urlencode(params))
    return get(url, cache)


def cf_files(mod_id, cache):
    params = {"gameVersion": MC, "modLoaderType": LOADER_CF, "pageSize": 20}
    url = "%s/mods/%s/files?%s" % (CF, mod_id, urllib.parse.urlencode(params))
    body = get(url, cache)
    # 代理按官方形状返回 {data:[...], pagination:{...}}；兼容裸数组
    if isinstance(body, dict):
        return body.get("data") or []
    return body or []


def pick_mr(hits, name):
    """在搜索结果里挑名字命中的项：优先完全相等，其次以之开头，最后包含。"""
    n = norm(name)
    if not n:
        return None
    for h in hits or []:
        if norm(h.get("title")) == n or norm(h.get("slug")) == n:
            return h, "exact"
    for h in hits or []:
        t = norm(h.get("title"))
        if t.startswith(n) or n.startswith(t):
            return h, "prefix"
    for h in hits or []:
        if n in norm(h.get("title")):
            return h, "contains"
    return None


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--tsv", default=os.path.join(HERE, "lists", "candidates-from-mcmod.tsv"))
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--only", default=None, help="只核验名字里含该子串的条目")
    args = ap.parse_args(argv)

    cache = load_cache()
    rows = []
    with io.open(args.tsv, encoding="utf-8") as f:
        header = None
        for line in f:
            line = line.rstrip("\n")
            if not line.strip():
                continue
            parts = line.split("\t")
            if header is None:
                header = parts
                continue
            rows.append(dict(zip(header, parts)))
    if args.only:
        rows = [r for r in rows if args.only.lower() in (r.get("name_en", "") or "").lower()
                or args.only in (r.get("name_zh", "") or "")]

    out = []
    for r in rows:
        name = r.get("name_en") or r.get("name_zh")
        rec = dict(r)
        rec["probe"] = name

        hits = mr_search(name, cache) or {}
        hit = pick_mr(hits.get("hits"), name)
        rec["mr_hits"] = [{"slug": h.get("slug"), "title": h.get("title"),
                           "downloads": h.get("downloads")} for h in (hits.get("hits") or [])[:3]]
        rec["mr_verdict"] = "NONE"
        if hit:
            h, how = hit
            slug = h["slug"]
            vers = mr_versions(slug, cache)
            if vers:
                newest = max(vers, key=lambda v: v.get("date_published") or "")
                rec["mr_slug"] = slug
                rec["mr_match"] = how
                rec["mr_versions"] = len(vers)
                rec["mr_newest"] = newest.get("version_number")
                rec["mr_filename"] = (newest.get("files") or [{}])[0].get("filename")
                rec["mr_downloads"] = h.get("downloads")
                rec["mr_verdict"] = "OK"
            else:
                rec["mr_slug"] = slug
                rec["mr_match"] = how
                rec["mr_verdict"] = "NO-VERSION"

        if rec["mr_verdict"] != "OK":
            cfs = cf_search(name, cache) or {}
            best, how = None, None
            n = norm(name)
            for d in (cfs.get("data") or []):
                if norm(d.get("name")) == n or norm(d.get("slug")) == n:
                    best, how = d, "exact"
                    break
            if best is None:
                # 弱匹配只作为线索，标记出来让人复核，不当成已核验
                for d in (cfs.get("data") or []):
                    dn = norm(d.get("name"))
                    if n and (dn.startswith(n) or dn.endswith(n)):
                        best, how = d, "weak"
                        break
            rec["cf_hits"] = [{"id": d.get("id"), "name": d.get("name"), "slug": d.get("slug"),
                               "downloads": d.get("downloadCount")} for d in (cfs.get("data") or [])[:3]]
            rec["cf_verdict"] = "NONE"
            rec["cf_match"] = how
            if best:
                files = cf_files(best["id"], cache) or []
                rec["cf_id"] = best["id"]
                rec["cf_slug"] = best.get("slug")
                rec["cf_name"] = best.get("name")
                rec["cf_downloads"] = best.get("downloadCount")
                rec["cf_files"] = len(files)
                if files:
                    nf = max(files, key=lambda x: x.get("fileDate") or "")
                    rec["cf_newest"] = nf.get("displayName")
                    rec["cf_verdict"] = "OK" if how == "exact" else "WEAK"
                else:
                    rec["cf_verdict"] = "NO-FILE" if how == "exact" else "WEAK-NO-FILE"
        out.append(rec)
        print("%-32s MR=%-4s %-9s %-26s CF=%-10s %s" % (
            name[:32], rec["mr_verdict"], rec.get("mr_match", "-"),
            str(rec.get("mr_slug") or "-")[:26], rec.get("cf_verdict", "-"),
            str(rec.get("cf_name") or rec.get("cf_slug") or "")[:30]))
        save_cache(cache)

    md = os.path.join(DATA, "candidate-verify.md")
    with io.open(md, "w", encoding="utf-8") as f:
        f.write("# 候选核验：1.21.1 + NeoForge 可下载版本\n\n")
        f.write("判定依据：Modrinth `/project/<slug>/version?game_versions=[\"1.21.1\"]&loaders=[\"neoforge\"]`"
                " 返回非空，或 CurseForge `/mods/<id>/files?gameVersion=1.21.1&modLoaderType=6` 返回非空。\n\n")
        f.write("| mcmod | 名称 | 功能位 | 判定 | 证据 |\n|---|---|---|---|---|\n")
        for r in out:
            if r["mr_verdict"] == "OK":
                ev = "Modrinth `%s`（%s 个 1.21.1-NeoForge 版本，最新 %s；名字匹配 %s）" % (
                    r["mr_slug"], r["mr_versions"], r.get("mr_newest"), r.get("mr_match"))
                v = "MR-OK"
            elif r.get("cf_verdict") == "OK":
                ev = "CurseForge `%s`（%s 个文件，最新 %s）" % (
                    r.get("cf_slug") or r.get("cf_name"), r.get("cf_files"), r.get("cf_newest"))
                v = "CF-OK"
            elif r.get("cf_verdict", "").startswith("WEAK"):
                ev = "疑似 CF `%s`（名字非全等，**需人工复核**）" % (r.get("cf_name"))
                v = "待复核"
            else:
                ev = "MR=%s CF=%s" % (r["mr_verdict"], r.get("cf_verdict", "-"))
                v = "未定"
            f.write("| %s | %s | %s | %s | %s |\n" % (
                r.get("mcmod_id", ""), r.get("name_en") or r.get("name_zh"),
                r.get("slot", ""), v, ev))
    js = os.path.join(DATA, "candidate-verify.json")
    with io.open(js, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    ok = sum(1 for r in out if r["mr_verdict"] == "OK" or r.get("cf_verdict") == "OK")
    print("\n%d/%d 条拿到 1.21.1+NeoForge 证据 -> %s" % (ok, len(out), md))
    return 0


if __name__ == "__main__":
    sys.exit(main())

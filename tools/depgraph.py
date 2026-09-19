#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""依赖图工具（进仓库（装机验证协议的核心工具））：算出"某组 slug 的依赖闭包"。

为什么需要：测试器要把"没选中的 mod"撤出去，但**前置库不能撤** ——
它们不在批次表里、却是选中 mod 的必需依赖。第一版把 pack/mods 里所有非批次条目都当"多余"撤掉，
结果 b1/b2 的 mod 全缺前置库，服务端直接崩（Mod ftbquests requires architectury…）。

正确做法：keep = 选中的批次 ∪ 它们的**依赖闭包**（只在父 mod 被保留时才保留其依赖）。
依赖关系向 Modrinth / CurseForge 查一次，缓存到 data/dep-graph.json。

用法：python tools/depgraph.py --build          # 建/刷新缓存
      python tools/depgraph.py --closure a,b,c  # 打印闭包
"""
import argparse
import io
import json
import sys
import time
import tomllib
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "data" / "dep-graph.json"
sys.path.insert(0, str(ROOT / "tools"))

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass

import verify_candidates as V  # noqa: E402


def pack_entries():
    """slug -> {id, src}（Modrinth 用 mod-id，CF 用 project-id）。"""
    out = {}
    for f in sorted((ROOT / "pack" / "mods").glob("*.pw.toml")):
        d = tomllib.loads(f.read_text(encoding="utf-8"))
        slug = f.name[:-len(".pw.toml")]
        mr = ((d.get("update") or {}).get("modrinth") or {}).get("mod-id")
        cf = ((d.get("update") or {}).get("curseforge") or {}).get("project-id")
        out[slug] = {"mr": mr, "cf": cf}
    return out


def load():
    if CACHE.is_file():
        try:
            return json.loads(CACHE.read_text(encoding="utf-8"))
        except Exception:  # noqa: BLE001
            pass
    return {"graph": {}, "ids": {}}


# CF 项目 id → 我们清单里的 slug。
# 为什么需要：CF 的依赖声明给的是 **CF 项目 id**，而我们清单里的库多半是 Modrinth 项目，
# 两边 id 体系不同；`/mods/{id}` 在公开代理上不返回 slug/name（实测返回空字段）。
# 这三条是用 **CF 按 slug 反查** 得到的实证映射（slug=architectury-api/create/puzzles-lib
# 查回来的 id 正好是这三个）。
CF_ID_ALIAS = {
    "419699": "architectury-api",   # ← ftb-* 系列声明依赖
    "328085": "create",             # ← copycats 等 Create 附属声明依赖
    "495476": "puzzles-lib",        # ← trading-post 声明依赖（缺了它服务端起不来）
}


def build(force=False):
    data = load()
    graph, ids = data.get("graph", {}), data.get("ids", {})
    entries = pack_entries()
    # id → slug 索引（两端都建）
    for slug, e in entries.items():
        if e["mr"]:
            ids["mr:" + e["mr"]] = slug
        if e["cf"]:
            ids["cf:%s" % e["cf"]] = slug
    cache = V.load_cache()
    todo = [s for s in entries if force or s not in graph]
    print("需要查依赖的条目 %d 个（已有缓存 %d）" % (len(todo), len(graph)))
    for i, slug in enumerate(todo, 1):
        e = entries[slug]
        deps = []
        if e["mr"]:
            vers = V.get("%s/project/%s/version?%s" % (V.MR, e["mr"], urllib.parse.urlencode(
                {"game_versions": json.dumps(["1.21.1"]), "loaders": json.dumps(["neoforge"])})), cache)
            if vers:
                nv = max(vers, key=lambda v: v.get("date_published") or "")
                deps = [d.get("project_id") for d in (nv.get("dependencies") or [])
                        if d.get("dependency_type") == "required"]
        cfdeps = []
        if e["cf"]:
            files = V.cf_files(e["cf"], cache)
            req = [f for f in files if V.MC in (f.get("gameVersions") or [])
                   and "NeoForge" in (f.get("gameVersions") or [])]
            if req:
                nf = max(req, key=lambda f: f.get("fileDate") or "")
                cfdeps = [d.get("modId") for d in (nf.get("dependencies") or [])
                          if d.get("relationType") == 3]
        graph[slug] = {"mr": deps, "cf": cfdeps}
        if i % 20 == 0:
            print("  …%d/%d" % (i, len(todo)), flush=True)
            CACHE.write_text(json.dumps({"graph": graph, "ids": ids}, ensure_ascii=False, indent=1),
                             encoding="utf-8")
    # 解析未知的 CF 依赖 id：CF 依赖给的是"CF 项目 id"，而我们的库多半是 Modrinth 项目，
    # 两边 id 体系不同 → 查一次 CF 拿它的 slug，再按"去掉非字母数字后相等"匹配到我们的条目。
    def norm(x):
        return "".join(ch for ch in (x or "").lower() if ch.isalnum())
    ours = {}
    for slug, e in entries.items():
        ours[norm(slug)] = slug
        d = tomllib.loads((ROOT / "pack" / "mods" / (slug + ".pw.toml")).read_text(encoding="utf-8"))
        ours[norm(d.get("filename"))] = slug
    for pid, slug in CF_ID_ALIAS.items():
        if slug in entries:
            ids["cf:%s" % pid] = slug
    unknown = sorted({pid for g in graph.values() for pid in g.get("cf", [])
                      if ("cf:%s" % pid) not in ids})
    print("需要解析的 CF 依赖 id：%d 个" % len(unknown))
    for pid in unknown:
        info = V.get("%s/mods/%s" % (V.CF, pid), cache)
        if not isinstance(info, dict):
            continue
        slug_cf = (info.get("slug") or "").lower()
        name_cf = info.get("name") or ""
        hit = ours.get(norm(slug_cf))
        if not hit:
            # 退一步：用名字匹配我们条目的 mod 名
            for s, e in entries.items():
                d = tomllib.loads((ROOT / "pack" / "mods" / (s + ".pw.toml")).read_text(encoding="utf-8"))
                if norm(d.get("name")) and norm(d.get("name")) == norm(name_cf):
                    hit = s
                    break
        if hit:
            ids["cf:%s" % pid] = hit
            print("   cf:%s (%s) → %s" % (pid, name_cf[:28], hit))
    CACHE.write_text(json.dumps({"graph": graph, "ids": ids}, ensure_ascii=False, indent=1),
                     encoding="utf-8")
    V.save_cache(cache)
    print("依赖图已写入 %s（%d 个条目）" % (CACHE, len(graph)))
    return 0


def closure(want, data=None):
    """want 的依赖闭包（含自身）。"""
    data = data or load()
    graph, ids = data.get("graph", {}), data.get("ids", {})
    seen, stack = set(want), list(want)
    while stack:
        s = stack.pop()
        for pid in graph.get(s, {}).get("mr", []):
            child = ids.get("mr:" + pid) or ids.get("cf:%s" % pid)
            if child and child not in seen:
                seen.add(child)
                stack.append(child)
        for pid in graph.get(s, {}).get("cf", []):
            child = ids.get("cf:%s" % pid) or ids.get("mr:" + str(pid))
            if child and child not in seen:
                seen.add(child)
                stack.append(child)
    return seen


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--build", action="store_true")
    ap.add_argument("--force", action="store_true")
    ap.add_argument("--closure", default="")
    args = ap.parse_args()
    if args.build:
        return build(args.force)
    if args.closure:
        want = {s.strip() for s in args.closure.split(",") if s.strip()}
        got = closure(want)
        extra = sorted(got - want)
        print("闭包共 %d 个（额外拉入 %d 个依赖）：" % (len(got), len(extra)))
        print("  " + ", ".join(extra))
        return 0
    ap.print_help()
    return 1


if __name__ == "__main__":
    sys.exit(main())

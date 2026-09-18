#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""MC百科 (mcmod.cn) 证据源工具。

用途：从中文社区目录里找「Modrinth / CurseForge 榜单不显眼、但中文服玩家常用」的 mod，
与 nation-pack/modlist.tsv 对差。

纪律：
  * robots.txt 只禁止 add/edit 这类提交路径，检索页允许访问（本工具只读检索页与 mod 页）。
  * 串行请求 + 固定间隔，不并发、不压站。
  * 每次请求立刻落盘缓存，被中断可续跑（--resume 默认开）。

子命令：
  --dump URL        抓一个页面存原始 HTML 到 data/mcmod-dump-<n>.html，用于看结构
  --probe           探测列表页参数（分页 / api / mcver 组合）并把结构报告打印出来
  --list            按筛选条件抓列表页，解析出 mod 条目
  --cmp             把抓到的目录与 modlist.tsv 对差，输出「百科有 / 清单无」候选

Python 3.8+，只用标准库。
"""

import argparse
import gzip
import html
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

# 本机 Python 的默认信任库不认 mcmod.cn 的中间证书（Windows 会自动补，OpenSSL 不会），
# 而 certifi 的 Mozilla 包能验通 —— 用 certifi，仍然完整校验，不做 unverified 降级。
try:
    import certifi
    SSL_CTX = ssl.create_default_context(cafile=certifi.where())
except Exception:  # noqa: BLE001
    SSL_CTX = ssl.create_default_context()

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # noqa: BLE001
    pass

BASE = "https://www.mcmod.cn"
UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/122.0 Safari/537.36 mcmod-research/1.0")

HERE = os.path.dirname(os.path.abspath(__file__))
PACK = os.path.dirname(HERE)
DATA = os.path.join(PACK, "data")
MODLIST = os.path.join(PACK, "modlist.tsv")
CACHE = os.path.join(DATA, "mcmod-cache.json")

DELAY = 1.2  # 秒/请求


# ---------------------------------------------------------------- http

def fetch(url, cache, key=None, force=False, timeout=30):
    """抓页面，带磁盘缓存（缓存只存解析结果或原始 HTML，由调用方决定 key）。"""
    k = key or url
    # 只把成功响应当缓存：失败/异常必须下次重试，否则一次网络抖动会被永久固化
    if not force and k in cache and cache[k].get("status") == 200:
        return cache[k]
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
        "Accept-Encoding": "gzip",
        "Connection": "close",
    })
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as r:
            raw = r.read()
            if r.headers.get("Content-Encoding") == "gzip":
                raw = gzip.GzipFile(fileobj=io.BytesIO(raw)).read()
            text = raw.decode("utf-8", "replace")
            status = r.status
    except urllib.error.HTTPError as e:
        text, status = "", e.code
    except Exception as e:  # noqa: BLE001
        text, status = "", "ERR:%s" % e
    cache[k] = {"status": status, "url": url, "text": text, "at": int(time.time())}
    time.sleep(DELAY)
    return cache[k]


def load_cache():
    if os.path.exists(CACHE):
        try:
            with open(CACHE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:  # noqa: BLE001
            return {}
    return {}


def save_cache(cache):
    if not os.path.isdir(DATA):
        os.makedirs(DATA)
    tmp = CACHE + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False)
    os.replace(tmp, CACHE)


# ---------------------------------------------------------------- parse

TAG_RE = re.compile(r"<[^>]+>")
WS_RE = re.compile(r"\s+")


def strip_tags(s):
    return WS_RE.sub(" ", html.unescape(TAG_RE.sub(" ", s))).strip()


# /class/<id>.html  后面紧跟中文名
CLASS_LINK_RE = re.compile(r'href="(?:https?://www\.mcmod\.cn)?/class/(\d+)\.html"[^>]*>(.*?)</a>',
                           re.S | re.I)

# 真实结构（2026-09 抓取）：
#   <div class="modlist-block">
#     <div class="intro"><a class="intro-content"><span>简介</span></a></div>
#     <div class="cover"><a><img alt="[前缀] 中文名 (English Name)"></a></div>
#     <div class="title"><p class="name"><a>中文名</a></p><p class="ename"><a>English Name</a></p></div>
#     <div class="items">相关物品缩略图</div>
#   </div>
BLOCK_SPLIT = '<div class="modlist-block">'
RE_NAME = re.compile(r'<p class="name">\s*<a[^>]*>(.*?)</a>', re.S)
RE_ENAME = re.compile(r'<p class="ename">\s*<a[^>]*>(.*?)</a>', re.S)
RE_INTRO = re.compile(r'class="intro-content"[^>]*>\s*<span>(.*?)</span>', re.S)
RE_ALT = re.compile(r'<img\s+alt="(.*?)"', re.S)
RE_CID = re.compile(r'/class/(\d+)\.html')


def parse_list(text):
    """从列表页 HTML 抠出条目：id / 中文名 / 英文名 / 简介（按页面顺序，即百科默认序）。"""
    out = []
    for chunk in text.split(BLOCK_SPLIT)[1:]:
        m = RE_CID.search(chunk)
        if not m:
            continue
        rec = {"id": m.group(1), "name": "", "ename": "", "intro": ""}
        for key, rx in (("name", RE_NAME), ("ename", RE_ENAME), ("intro", RE_INTRO)):
            mm = rx.search(chunk)
            if mm:
                rec[key] = strip_tags(mm.group(1))
        if not rec["ename"]:
            mm = RE_ALT.search(chunk)
            if mm:
                alt = strip_tags(mm.group(1))
                mm2 = re.search(r"^(?:\[[^\]]*\]\s*)?(.*?)\s*\((.*)\)\s*$", alt)
                if mm2:
                    rec["name"] = rec["name"] or mm2.group(1).strip()
                    rec["ename"] = mm2.group(2).strip()
        if rec["name"] or rec["ename"]:
            out.append(rec)
    return out


def parse_pagination(text):
    """返回 (最大页号, 总条数)。分页处形如：当前 1 / 334 页，共 10672 条"""
    pages = [int(x) for x in re.findall(r"[?&]page=(\d+)", text)]
    flat = strip_tags(text)
    m = re.search(r"共\s*(?:收录)?\s*(\d+)\s*条", flat)
    total = int(m.group(1)) if m else None
    m2 = re.search(r"/\s*(\d+)\s*页", flat)
    if m2:
        pages.append(int(m2.group(1)))
    return (max(pages) if pages else None), total


# ---------------------------------------------------------------- modlist.tsv

def read_modlist():
    rows = []
    if not os.path.exists(MODLIST):
        return rows
    with open(MODLIST, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            rows.append({"slug": parts[0].strip(),
                         "status": parts[1].strip(),
                         "slot": parts[2].strip() if len(parts) > 2 else ""})
    return rows


def norm(s):
    return re.sub(r"[^a-z0-9\u4e00-\u9fff]", "", s.lower())


TAG_PREFIX = re.compile(r"^\[[^\]]*\]\s*")


def name_candidates(it):
    """条目的候选名：ename 与 name 都算，并剥掉 [FFS] 这类前缀。

    mcmod 上不少条目没有独立英文名字段，英文名会带着标签前缀落在 name 里
    （如 name="[FFS] FTB Filter System"、ename=""），只用 ename 匹配会造出假缺口。
    """
    out = []
    for raw in (it.get("ename", ""), it.get("name", "")):
        s = TAG_PREFIX.sub("", (raw or "").strip())
        if not s:
            continue
        out.append(s)
        out.append(s.replace(" ", ""))
    return out


def matches_catalog_entry(it, keys, strong):
    for c in name_candidates(it):
        n = norm(c)
        if not n:
            continue
        if n in keys:
            return True
        if len(n) >= 6 and any(s in n for s in strong):
            return True
    return False


# ---------------------------------------------------------------- cmds

def cmd_dump(args, cache):
    url = args.dump if args.dump.startswith("http") else BASE + args.dump
    r = fetch(url, cache, force=args.force)
    if not os.path.isdir(DATA):
        os.makedirs(DATA)
    n = 1
    while os.path.exists(os.path.join(DATA, "mcmod-dump-%d.html" % n)):
        n += 1
    path = os.path.join(DATA, "mcmod-dump-%d.html" % n)
    with open(path, "w", encoding="utf-8") as f:
        f.write(r["text"] if isinstance(r["text"], str) else "")
    print("status=%s len=%d -> %s" % (r["status"], len(r["text"]), path))
    items = parse_list(r["text"])
    mx, total = parse_pagination(r["text"])
    print("parsed items=%d  page_max=%s  page_total=%s" % (len(items), mx, total))
    for it in items[:12]:
        print("   %-6s %s | %s" % (it["id"], it["name"], it["ename"]))
    return 0


def cmd_probe(args, cache):
    """探测：同一筛选下换 api / page 看返回条目是否变化，确认参数是否真的生效。"""
    tests = [
        ("baseline  1.21.1", "/modlist.html?mcver=1.21.1"),
        ("forge     1.21.1 api=1", "/modlist.html?mcver=1.21.1&api=1"),
        ("fabric    1.21.1 api=2", "/modlist.html?mcver=1.21.1&api=2"),
        ("neoforge? 1.21.1 api=3", "/modlist.html?mcver=1.21.1&api=3"),
        ("neoforge? 1.21.1 api=4", "/modlist.html?mcver=1.21.1&api=4"),
        ("page2     1.21.1", "/modlist.html?mcver=1.21.1&page=2"),
        ("crew      1.21.1 建筑", "/modlist.html?mcver=1.21.1&category=5"),
    ]
    for label, path in tests:
        r = fetch(BASE + path, cache, force=args.force)
        items = parse_list(r["text"])
        mx, total = parse_pagination(r["text"])
        sig = ",".join(i["id"] for i in items[:5])
        print("%-24s status=%-4s items=%-3d pagemax=%-4s total=%-5s first5=%s"
              % (label, r["status"], len(items), mx, total, sig))
    return 0


def cmd_list(args, cache):
    """抓列表页（支持分页），把 mod id + 中英名 + 默认序名次落进目录文件。"""
    base = "/modlist.html?" + args.query
    catfile = os.path.join(DATA, "mcmod-catalog.json")
    catalog = {}
    if os.path.exists(catfile):
        try:
            with open(catfile, "r", encoding="utf-8") as f:
                catalog = json.load(f)
        except Exception:  # noqa: BLE001
            catalog = {}
    meta_key = "meta::" + args.query
    rank = catalog.get(meta_key, {}).get("last_rank", 0)
    for p in range(1, args.pages + 1):
        url = BASE + base + ("&page=%d" % p if p > 1 else "")
        r = fetch(url, cache, force=args.force)
        if r["status"] != 200:
            print("page %d status=%s, stop" % (p, r["status"]))
            break
        items = parse_list(r["text"])
        mx, total = parse_pagination(r["text"])
        new = 0
        for it in items:
            rank += 1
            it["rank"] = rank
            it["page"] = p
            if it["id"] not in catalog:
                new += 1
            catalog[it["id"]] = it
        catalog[meta_key] = {"query": args.query, "last_page": p, "last_rank": rank,
                             "pages_total": mx, "items_total": total}
        print("page %-4d items=%-4d new=%-4d rank=%-6d pages_total=%s total=%s"
              % (p, len(items), new, rank, mx, total))
        save_cache(cache)
        with open(catfile, "w", encoding="utf-8") as f:
            json.dump(catalog, f, ensure_ascii=False, indent=1)
        if new == 0 and p > 1:
            print("no new items, stop")
            break
    real = [k for k in catalog if not k.startswith("meta::")]
    print("catalog size = %d -> %s" % (len(real), catfile))
    return 0


def cmd_cmp(args, cache):
    """把百科目录与 modlist.tsv 对差，按百科默认序名次分档输出候选。"""
    catfile = os.path.join(DATA, "mcmod-catalog.json")
    if not os.path.exists(catfile):
        print("先跑 --list")
        return 2
    with open(catfile, "r", encoding="utf-8") as f:
        raw = json.load(f)
    rows = read_modlist()
    catalog = [v for k, v in raw.items() if not k.startswith("meta::")]

    # 清单侧：slug 全串 + 分词 + 不含连字符的紧凑串
    keys = set()
    for r in rows:
        s = norm(r["slug"])
        keys.add(s)
        for tok in re.split(r"[-_ ]+", r["slug"]):
            t = norm(tok)
            if len(t) >= 4:
                keys.add(t)
    strong = {k for k in keys if len(k) >= 6}

    miss, hit = [], 0
    for it in catalog:
        if matches_catalog_entry(it, keys, strong):
            hit += 1
        else:
            miss.append(it)
    miss.sort(key=lambda x: x.get("rank", 10 ** 9))

    out = os.path.join(DATA, "mcmod-gap.md")
    with open(out, "w", encoding="utf-8") as f:
        f.write("# MC百科 1.21.1 目录 vs 本项目 modlist.tsv\n\n")
        f.write("百科条目 %d，命中清单 %d，**未命中 %d**。\n"
                "序为百科列表默认序（名次越小越靠前）。\n\n" % (len(catalog), hit, len(miss)))
        f.write("## 前 300 名内的未命中（最值得看）\n\n")
        for it in [x for x in miss if x.get("rank", 0) <= 300]:
            f.write("- `%s` **%s** — %s  _(#%s)_\n"
                    % (it["id"], it["ename"] or it["name"], it["intro"][:70], it.get("rank")))
        f.write("\n## 301~1200 名\n\n")
        for it in [x for x in miss if 300 < x.get("rank", 0) <= 1200]:
            f.write("- `%s` %s / %s\n" % (it["id"], it["ename"], it["name"]))
        rest = [x for x in miss if x.get("rank", 0) > 1200]
        f.write("\n## 1200 名以后：共 %d 条（只列 id 与英文名，见 mcmod-gap.json）\n\n" % len(rest))
    gapjson = os.path.join(DATA, "mcmod-gap.json")
    with open(gapjson, "w", encoding="utf-8") as f:
        json.dump(miss, f, ensure_ascii=False, indent=1)
    print("catalog=%d hit=%d miss=%d -> %s / %s" % (len(catalog), hit, len(miss), out, gapjson))
    return 0


def cmd_names(args, cache):
    """把 modlist.tsv 的每个 slug 对上百科条目，导出官方中文名索引。"""
    catfile = os.path.join(DATA, "mcmod-catalog.json")
    if not os.path.exists(catfile):
        print("先跑 --list")
        return 2
    with open(catfile, "r", encoding="utf-8") as f:
        raw = json.load(f)
    catalog = [v for k, v in raw.items() if not k.startswith("meta::")]
    rows = read_modlist()

    # 建英文名 -> 条目的索引（紧凑化后精确匹配优先）
    by_key = {}
    for it in catalog:
        for cand in name_candidates(it):
            k = norm(cand)
            if k:
                by_key.setdefault(k, it)

    out = os.path.join(DATA, "mcmod-names.tsv")
    found = 0
    with open(out, "w", encoding="utf-8") as f:
        f.write("# slug\t中文名\t百科id\t状态\t功能位\tintro\n")
        for r in rows:
            slug = r["slug"]
            keys = [norm(slug), norm(slug.replace("-", ""))]
            hit = None
            for k in keys:
                if k in by_key:
                    hit = by_key[k]
                    break
            if hit is None:
                # 松散：候选名紧凑串相等或含 slug 主词
                sl = norm(slug.replace("-", ""))
                if len(sl) >= 6:
                    for it in catalog:
                        for c in name_candidates(it):
                            nc = norm(c.replace(" ", ""))
                            if nc and (nc == sl or sl in nc):
                                hit = it
                                break
                        if hit:
                            break
            if hit:
                found += 1
            f.write("%s\t%s\t%s\t%s\t%s\t%s\n"
                    % (slug, hit["name"] if hit else "", hit["id"] if hit else "",
                       r["status"], r["slot"], (hit["intro"] if hit else "")[:60]))
    print("modlist rows=%d  matched=%d  -> %s" % (len(rows), found, out))
    return 0


SLOT_KEYWORDS = [
    ("领地/治理", ["领地", "圈地", "认领", "领土", "保护区域", "claim", "主权"]),
    ("任务/引导", ["任务", "任务书", "引导", "进度系统", "成就系统"]),
    ("建造/蓝图", ["建筑", "建造", "蓝图", "施工", "快速建造", "建筑师"]),
    ("装饰/家具", ["装饰", "家具", "美化", "装修", "陈设"]),
    ("农业/食物", ["农业", "作物", "食物", "烹饪", "料理", "农田", "种植", "食谱"]),
    ("自动化/机械", ["自动化", "机械", "管道", "电力", "能量传输", "工厂"]),
    ("交通/物流", ["载具", "交通", "运输", "物流", "铁路", "道路", "车辆"]),
    ("冒险/远征", ["地牢", "维度", "探险", "遗迹", "冒险", "boss", "秘境"]),
    ("威胁/敌人", ["怪物", "敌人", "入侵", "丧尸", "僵尸", "围城", "围攻", " raids"]),
    ("性能/优化", ["优化", "性能", "卡顿", "帧数", "内存占用", "流畅"]),
    ("界面/信息", ["界面", "hud", "小地图", "提示", "工具提示", "拼音", "输入法", "配方查看"]),
    ("存储/背包", ["存储", "背包", "仓库", "箱子", "容器"]),
    ("多人/联机", ["联机", "服务端", "多人", "团队", "服务器"]),
    ("世界生成", ["群系", "生物群系", "地形", "世界生成", "地貌"]),
    ("声音/氛围", ["音效", "声音", "环境音", "氛围", "音乐"]),
]


def cmd_slots(args, cache):
    """把缺口候选按「简介文本命中的功能位关键词」分桶，便于按功能位逐个 triage。"""
    gapfile = os.path.join(DATA, "mcmod-gap.json")
    if not os.path.exists(gapfile):
        print("先跑 --cmp")
        return 2
    with open(gapfile, "r", encoding="utf-8") as f:
        gap = json.load(f)

    per = {name: [] for name, _ in SLOT_KEYWORDS}
    for it in gap:
        blob = (it.get("name", "") + " " + it.get("ename", "") + " " + it.get("intro", "")).lower()
        if not blob.strip():
            continue
        for name, kws in SLOT_KEYWORDS:
            if any(k.lower() in blob for k in kws):
                per[name].append(it)

    top = args.top
    out = os.path.join(DATA, "mcmod-slots.md")
    with open(out, "w", encoding="utf-8") as f:
        f.write("# MC百科缺口候选 · 按功能位分桶\n\n")
        f.write("来源：MC百科 1.21.1 模组目录（%d 条）减去本项目 modlist.tsv 后的缺口，"
                "按条目简介命中的中文关键词分桶。\n"
                "**注意**：分桶只依据简介文本，不是已核验的选型；每条都仍需"
                "按 Modrinth/CurseForge 的 loaders+game_versions 核验。\n"
                "名次为百科默认排序，语义未经验证，仅供稳定遍历。\n\n" % len(gap))
        for name, _ in SLOT_KEYWORDS:
            items = sorted(per[name], key=lambda x: x.get("rank", 10 ** 9))[:top]
            f.write("## %s（缺口命中 %d，列出前 %d）\n\n" % (name, len(per[name]), len(items)))
            for it in items:
                f.write("- `%s` **%s** / %s — %s\n"
                        % (it["id"], it["ename"] or "?", it["name"],
                           (it.get("intro") or "").strip()[:80]))
            f.write("\n")
    print("gap=%d -> %s" % (len(gap), out))
    for name, _ in SLOT_KEYWORDS:
        print("  %-12s %d" % (name, len(per[name])))
    return 0


def main(argv=None):
    ap = argparse.ArgumentParser()
    ap.add_argument("--dump")
    ap.add_argument("--probe", action="store_true")
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--cmp", action="store_true")
    ap.add_argument("--names", action="store_true", help="导出 modlist.tsv 的百科中文名索引")
    ap.add_argument("--slots", action="store_true", help="把缺口按功能位关键词分桶")
    ap.add_argument("--top", type=int, default=25)
    ap.add_argument("--query", default="mcver=1.21.1")
    ap.add_argument("--pages", type=int, default=5)
    ap.add_argument("--force", action="store_true", help="忽略缓存重抓")
    args = ap.parse_args(argv)

    cache = load_cache()
    try:
        if args.dump:
            rc = cmd_dump(args, cache)
        elif args.probe:
            rc = cmd_probe(args, cache)
        elif args.list:
            rc = cmd_list(args, cache)
        elif args.cmp:
            rc = cmd_cmp(args, cache)
        elif args.names:
            rc = cmd_names(args, cache)
        elif args.slots:
            rc = cmd_slots(args, cache)
        else:
            ap.print_help()
            rc = 1
    except KeyboardInterrupt:
        print("interrupted")
        rc = 130
    finally:
        save_cache(cache)
    return rc


if __name__ == "__main__":
    sys.exit(main())

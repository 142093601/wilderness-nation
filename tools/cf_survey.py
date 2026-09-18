"""cf_survey.py -- 按类目/关键词扫 **CurseForge** 的 mod 目录（1.21.1 + NeoForge）。

为什么必须有它
--------------
之前的候选池只来自 **Modrinth**，于是有系统性盲区：CF 独占的 mod 完全看不见——
**任务书错选 Questlog（而 FTB Quests 才是标准答案）就是这么来的**。
`tools/reference_pack_diff.py` 只能靠"本机恰好装了的包"间接碰到 CF 内容，不是扫目录。

怎么做到的（不需要 API key）
---------------------------
CurseForge 官方 API 要 key，但存在**公开代理** `api.curse.tools`（按官方 API 形状转发）。
关键参数：
    gameId=432         Minecraft
    classId=6          **Mods**（不写就会混进整合包/材质包）
    gameVersion=1.21.1
    modLoaderType=6    NeoForge（1=Forge, 4=Fabric, 5=Quilt, 6=NeoForge）
    sortField=6        按总下载量（1=Featured, 2=Popularity, 3=LastUpdated, 6=TotalDownloads）

用法
----
    python cf_survey.py --md ../data/cf-survey.md --json ../data/cf-survey.json
    python cf_survey.py --categories 6484,407,436 --per-category 40   # Create/群系/食物
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
BASE = "https://api.curse.tools/v1/cf"
UA = "nation-pack-cf-survey/0.1 (personal modpack dev)"
GAME_ID, MODS_CLASS, MC, NEOFORGE = 432, 6, "1.21.1", 6

# 本包关心的 CF 类目（id 来自 /categories?gameId=432 的实测返回）
WANTED_CATEGORIES = {
    6484: "Create（CF 有专门的 Create 类目）",
    407: "群系",
    410: "维度",
    411: "生物",
    416: "农业",
    436: "食物",
    434: "装备、工具与武器",
    423: "地图与信息",
    435: "服务端工具",
    426: "附加（Addons）",
    6821: "Bug 修复",
    421: "API 与前置库",
    5314: "KubeJS",
}

DEFAULT_QUERIES = [
    "medieval", "kingdom", "village", "castle", "town", "building", "furniture",
    "decoration", "claim", "quest", "storage", "backpack", "structure", "dungeon",
]


def api_get(path: str, params: dict) -> dict:
    url = f"{BASE}{path}?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return {}
            time.sleep(1.5 * (attempt + 1))
        except Exception:
            time.sleep(1.5 * (attempt + 1))
    return {}


def load_cache(path: Path | None) -> dict:
    if path and path.is_file():
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}


def save_cache(path: Path | None, cache: dict) -> None:
    if not path:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")


def search(category_id: int | None, query: str | None, limit: int,
           cache: dict | None = None, cache_path: Path | None = None) -> list[dict]:
    """带磁盘缓存的搜索。

    为什么必须缓存：这个调查要打 27 次接口、约 1~3 分钟，而**长调用容易被中断**
    （实测连挂两次：一次跑到第 17 个查询被掐、输出全丢）。有了缓存就能分段跑——
    先 `--only categories`，再 `--only queries`，每次都短，且断点自动续。
    """
    key = f"category:{category_id}" if category_id else f"query:{query}"
    if cache is not None and key in cache:
        print(f"    （缓存命中 {key}，{len(cache[key])} 条）")
        return cache[key]

    params = {
        "gameId": GAME_ID, "classId": MODS_CLASS, "gameVersion": MC,
        "modLoaderType": NEOFORGE, "sortField": 6, "sortOrder": "desc", "pageSize": limit,
    }
    if category_id:
        params["categoryId"] = category_id
    if query:
        params["searchFilter"] = query
    data = api_get("/mods/search", params)
    out = []
    for m in (data.get("data") or []):
        out.append({
            "id": m.get("id"), "slug": m.get("slug"), "name": m.get("name"),
            "downloads": m.get("downloadCount", 0), "summary": (m.get("summary") or "")[:160],
            "updated": (m.get("dateModified") or "")[:10],
            "src": key,
        })
    if cache is not None:
        cache[key] = out
        save_cache(cache_path, cache)     # **每次请求后立刻落盘** —— 断了也不白跑
    return out


def family(s: str) -> str:
    s = (s or "").lower()
    s = re.sub(r"[-_]?(?:mc)?1\.\d+(?:\.\d+)*|[-_](?:neo)?forge|[-_]fabric", "", s)
    return re.sub(r"[^a-z0-9]", "", s)


def ours() -> set[str]:
    out: set[str] = set()
    p = ROOT / "modlist.tsv"
    if not p.is_file():
        return out
    for raw in p.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if parts and parts[0].strip():
            out.add(family(parts[0].strip()))
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="扫 CurseForge 的 mod 目录")
    ap.add_argument("--categories", default=",".join(str(k) for k in WANTED_CATEGORIES))
    ap.add_argument("--queries", default=",".join(DEFAULT_QUERIES))
    ap.add_argument("--per-category", type=int, default=50)
    ap.add_argument("--only", choices=["all", "categories", "queries"], default="all",
                    help="分段执行：长调查容易被中断，分两次跑，每次都短")
    ap.add_argument("--cache", default=str(ROOT / "data" / "cf-cache.json"),
                    help="接口结果的磁盘缓存；每次请求后立刻落盘，断点可续")
    ap.add_argument("--md")
    ap.add_argument("--json")
    args = ap.parse_args()

    cats = [int(c) for c in args.categories.split(",") if c.strip().isdigit()]
    queries = [q.strip() for q in args.queries.split(",") if q.strip()]
    cache_path = Path(args.cache) if args.cache else None
    cache = load_cache(cache_path)
    if cache:
        print(f"缓存已有 {len(cache)} 组结果")

    by_slug: dict[str, dict] = {}
    if args.only in ("all", "categories"):
        for cid in cats:
            hits = search(cid, None, args.per_category, cache, cache_path)
            label = WANTED_CATEGORIES.get(cid, str(cid))
            print(f"  类目 {cid:<6} {label:<28} {len(hits)} 条")
            for h in hits:
                by_slug.setdefault(h["slug"], h)
            time.sleep(0.3)
    if args.only in ("all", "queries"):
        for q in queries:
            hits = search(None, q, args.per_category, cache, cache_path)
            print(f"  查询 {q:<26} {len(hits)} 条")
            for h in hits:
                by_slug.setdefault(h["slug"], h)
            time.sleep(0.3)

    # 报告始终**从缓存全量重建**（分段跑时，第二次也要把第一次的结果算进去）
    for key, hits in cache.items():
        for h in hits:
            by_slug.setdefault(h["slug"], h)

    rows = sorted(by_slug.values(), key=lambda r: -r["downloads"])
    mine = ours()
    gap = [r for r in rows if family(r["slug"]) not in mine]
    print(f"\nCF 候选（去重）：{len(rows)} 个；其中**我们清单里没有**的：{len(gap)} 个")

    lines = [
        "# CurseForge 候选（1.21.1 + NeoForge）\n",
        f"> 数据源：CurseForge 公开代理 `api.curse.tools`，筛选 `gameId=432` · `classId=6`(Mods) · "
        f"`gameVersion={MC}` · `modLoaderType={NEOFORGE}`(NeoForge) · 按总下载量排序。",
        f"> 工具：`tools/cf_survey.py`，可重跑。\n",
        f"> 去重后 **{len(rows)}** 个；其中我们清单里没有的 **{len(gap)}** 个。\n",
        "| mod | 下载量 | slug | CF id | 摘要 |",
        "|---|---|---|---|---|",
    ]
    for r in gap[:300]:
        lines.append(f"| {r['name']} | {r['downloads']:,} | `{r['slug']}` | {r['id']} | "
                     f"{r['summary'][:70]} |")
    report = "\n".join(lines)
    if args.md:
        Path(args.md).parent.mkdir(parents=True, exist_ok=True)
        Path(args.md).write_text(report + "\n", encoding="utf-8")
        print(f"已写: {args.md}")
    if args.json:
        Path(args.json).write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"已写: {args.json}")
    print("\n前 25 个漏项：")
    for r in gap[:25]:
        print(f"  {r['downloads']:>12,}  {r['name'][:44]:<44} {r['slug']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

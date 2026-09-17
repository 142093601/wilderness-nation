"""survey_mods.py -- 按功能类目在 Modrinth 上拉候选 mod，产出可读的候选清单。

为什么要有它
------------
"这个包该装哪些内容 mod"不能靠回忆，也不能靠"我知道有个 mod 叫 X"。
Modrinth 的搜索接口支持**按类目 + MC 版本 + 加载器**做精确筛选，而且返回下载量与端侧标注——
这是能拿到的最硬的选型证据（比"描述页说它支持 1.21.1"更硬，因为它是接口返回的版本配对）。

产出
----
    CANDIDATES.md   按功能位分组的候选清单（人读）
    <json>          完整数据（脚本后续复用：筛选、去重、对比已装）

注意：**只查 neoforge**。Fabric 的 mod 在 NeoForge 上装不了（除非用 Sinytra Connector，
那会引入一整套兼容风险，本包不采用）。

用法
----
    python survey_mods.py --out data/mod-survey.json --md CANDIDATES.md
    python survey_mods.py --categories decoration,food,storage --per-category 40
"""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "nation-pack-survey/0.1 (personal modpack dev)"
HERE = Path(__file__).resolve().parent

# Modrinth 的类目 → 本包的功能位（用来把候选组织成"我们看得懂"的结构）
CATEGORY_TO_SLOT: dict[str, str] = {
    "worldgen": "世界与地理（群系 / 地形 / 结构）",
    "adventure": "冒险（结构 / 地牢 / 维度 / 战利品）",
    "mobs": "生物与威胁",
    "decoration": "建造与装饰（建材 / 家具 / 灯饰）",
    "food": "食物与农业",
    "storage": "存储与物流",
    "transportation": "交通",
    "equipment": "装备与工具",
    "magic": "魔法/特殊系统（谨慎）",
    "technology": "科技与自动化",
    "management": "管理与治理",
    "game-mechanics": "玩法机制（谨慎：改动大）",
    "utility": "生活质量与信息",
    "social": "社交与联机",
    "economy": "经济（本包暂不需要货币）",
    "minigame": "小游戏（本包不需要）",
    "cursed": "猎奇（本包不需要）",
    "optimization": "性能（已单独成组）",
    "library": "前置库（自动随依赖装）",
}

DEFAULT_CATEGORIES = [
    "worldgen", "adventure", "mobs", "decoration", "food", "storage",
    "transportation", "equipment", "technology", "management", "utility", "social",
]


def api_get(path: str, params: dict | None = None) -> dict:
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode(params, doseq=True)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(4):
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


def search_category(cat: str, mc: str, loader: str, limit: int) -> list[dict]:
    facets = json.dumps([
        [f"categories:{cat}"],
        [f"versions:{mc}"],
        [f"loaders:{loader}"],
        ["project_type:mod"],
    ])
    data = api_get("/search", {"facets": facets, "limit": limit, "index": "downloads"})
    return _hits(data, cat)


def search_query(query: str, mc: str, loader: str, limit: int) -> list[dict]:
    """按关键词搜。

    为什么需要它：Modrinth 的**类目抓不全系列 mod**。例如建材/家具那一大家子
    （Macaw's 的 roofs / bridges / doors / trapdoors / lights…）分属不同项目，
    类目页只露出其中几个；用关键词能一次捞全。
    """
    facets = json.dumps([
        [f"versions:{mc}"],
        [f"loaders:{loader}"],
        ["project_type:mod"],
    ])
    data = api_get("/search", {"query": query, "facets": facets, "limit": limit, "index": "downloads"})
    return _hits(data, f"查询:{query}")


def _hits(data: dict, tag: str) -> list[dict]:
    hits = data.get("hits", []) if isinstance(data, dict) else []
    out = []
    for h in hits:
        out.append({
            "slug": h.get("slug"),
            "title": h.get("title"),
            "downloads": h.get("downloads", 0),
            "followers": h.get("followers", 0),
            "categories": h.get("categories", []),
            "client_side": h.get("client_side"),
            "server_side": h.get("server_side"),
            "license": h.get("license"),
            "updated": (h.get("date_modified") or "")[:10],
            "category_queried": tag,
        })
    return out


def installed_slugs() -> set[str]:
    """读已装清单，用来在候选表里标出"已装"。

    踩过：第一版只找 `data/mods-manifest.json`，而 fetch_mods.py 实际把它写在
    **mods 目录里**（`<实例>/mods/mods-manifest.json`）——于是整张表"已装"全是 0，
    看起来像"一个都没装"，会误导选型。
    """
    import pack_paths   # 同目录模块：实例路径不进仓库（见 pack.local.example.json）
    p = pack_paths.paths()
    candidates = [
        Path(p["instance_dir"]) / "mods" / "mods-manifest.json",
        Path(p["server_dir"]) / "mods" / "mods-manifest.json",
        HERE.parent / "data" / "mods-manifest.json",
    ]
    out: set[str] = set()
    for p in candidates:
        if not p.is_file():
            continue
        try:
            m = json.loads(p.read_text(encoding="utf-8"))
            out |= set((m.get("installed") or {}).keys())
        except Exception:
            continue
    return out


def slot_of(r: dict) -> str:
    """决定候选归到哪个功能位。

    类目查询直接用类目映射；**关键词查询**（`查询:xxx`）没有类目可映射，
    就回退到这个项目**自己的** Modrinth 类目——否则关键词捞到的候选会被塞进
    一个叫"查询:家具"的假功能位里。
    """
    q = r.get("category_queried", "")
    if not q.startswith("查询:"):
        return CATEGORY_TO_SLOT.get(q, q)
    for c in (r.get("categories") or []):
        if c in CATEGORY_TO_SLOT:
            return CATEGORY_TO_SLOT[c]
    return "关键词补充"


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="按类目在 Modrinth 上拉候选 mod")
    ap.add_argument("--mc", default="1.21.1")
    ap.add_argument("--loader", default="neoforge")
    ap.add_argument("--categories", default=",".join(DEFAULT_CATEGORIES))
    ap.add_argument("--queries", default="", help="额外按关键词搜（逗号分隔），用于抓类目抓不全的系列 mod")
    ap.add_argument("--per-category", type=int, default=60)
    ap.add_argument("--out", default=str(HERE.parent / "data" / "mod-survey.json"))
    ap.add_argument("--md", default=str(HERE.parent / "CANDIDATES.md"))
    ap.add_argument("--brief", action="store_true", help="只在终端打印每个功能位的前 N 个（便于通读）")
    args = ap.parse_args()

    cats = [c.strip() for c in args.categories.split(",") if c.strip()]
    queries = [q.strip() for q in args.queries.split(",") if q.strip()]
    done = installed_slugs()
    print(f"目标：{args.mc} + {args.loader} · 类目 {len(cats)} 个 · 关键词 {len(queries)} 个 · 每项 {args.per_category}")

    by_slug: dict[str, dict] = {}
    for cat in cats:
        hits = search_category(cat, args.mc, args.loader, args.per_category)
        print(f"  {cat:<16} {len(hits)} 条")
        for h in hits:
            slug = h.get("slug")
            if not slug:
                continue
            if slug in by_slug:
                by_slug[slug].setdefault("also_in", []).append(cat)
                continue
            h["installed"] = slug in done
            by_slug[slug] = h
        time.sleep(0.4)   # 别把接口打急
    for q in queries:
        hits = search_query(q, args.mc, args.loader, args.per_category)
        print(f"  查询:{q:<16} {len(hits)} 条")
        for h in hits:
            slug = h.get("slug")
            if not slug:
                continue
            if slug in by_slug:
                by_slug[slug].setdefault("also_in", []).append(h["category_queried"])
                continue
            h["installed"] = slug in done
            by_slug[slug] = h
        time.sleep(0.4)

    rows = sorted(by_slug.values(), key=lambda r: -r.get("downloads", 0))
    print(f"\n去重后候选：{len(rows)} 个（其中已装 {sum(1 for r in rows if r.get('installed'))} 个）")

    if args.brief:
        brief_slots: dict[str, list[dict]] = {}
        for r in rows:
            brief_slots.setdefault(slot_of(r), []).append(r)
        for slot, items in sorted(brief_slots.items(), key=lambda kv: -len(kv[1])):
            print(f"\n## {slot}（{len(items)}）")
            for i in items[:18]:
                mark = "★已装 " if i.get("installed") else ""
                print(f"   {mark}{i['title']} (`{i['slug']}`) {i['downloads']:,} "
                      f"[{i.get('client_side')}/{i.get('server_side')}]")

    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    Path(args.out).write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"已写: {args.out}")

    # ── 生成人读的候选清单 ────────────────────────────────────────────────────
    lines: list[str] = []
    lines.append("# 候选 mod 清单（按功能位分组）\n")
    lines.append(f"> 数据源：Modrinth 搜索接口，筛选条件 **`{args.mc}` + `{args.loader}` + `project_type:mod`**，"
                 f"按下载量排序，每类取前 {args.per_category}。\n")
    lines.append("> 核查方式：`tools/survey_mods.py`（可重跑；候选会随 mod 生态变化）。\n")
    lines.append("> **这不是最终选型**——候选只回答「市场上有哪些」，"
                 "选不选还要过设计要求（对照 `DESIGN.md` 的六个时代与反目标）与端侧/依赖检查。\n")

    # 按功能位聚合
    slots: dict[str, list[dict]] = {}
    for r in rows:
        slots.setdefault(slot_of(r), []).append(r)

    lines.append("\n## 汇总\n")
    lines.append("| 功能位 | 候选数 | 已装 | 该类最高下载 |")
    lines.append("|---|---|---|---|")
    for slot, items in sorted(slots.items(), key=lambda kv: -len(kv[1])):
        inst = sum(1 for i in items if i.get("installed"))
        top = items[0]
        lines.append(f"| {slot} | {len(items)} | {inst} | {top['title']}（{top['downloads']:,}） |")
    lines.append(f"| **合计** | **{len(rows)}** | "
                 f"{sum(1 for r in rows if r.get('installed'))} | — |")

    for slot, items in sorted(slots.items(), key=lambda kv: -len(kv[1])):
        lines.append(f"\n## {slot}\n")
        lines.append("| mod | slug | 下载 | 端侧 | 最近更新 | 备注 |")
        lines.append("|---|---|---|---|---|---|")
        for i in items:
            side = f"{i.get('client_side')}/{i.get('server_side')}"
            note = "**已装**" if i.get("installed") else ""
            if i.get("also_in"):
                note += (" · " if note else "") + "也在 " + "/".join(i["also_in"])
            lines.append(f"| {i['title']} | `{i['slug']}` | {i['downloads']:,} | {side} | "
                         f"{i.get('updated','')} | {note} |")

    Path(args.md).write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"已写: {args.md}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""pack_composition.py -- 把某个整合包的 mod 列表按功能位归类，得出"正常包的构成"。

为什么需要它
------------
"我们的包 mod 太少"这句话，光看总数没用（479 个 jar 里可能一半是库和附属）。
真正要回答的是：**成熟包在哪些功能位上厚，而我们在哪些功能位上薄**。
做法：拿每个 jar 的 **SHA-1** 反查 Modrinth → 项目 → 官方类目，再按本包的功能位映射归类。

为什么这是硬证据
----------------
不像"我印象里某某包装了很多装饰 mod"，这里的每个类目都来自接口返回，
而且对象是**真实存在的包**（例如本机的 All the Mods 10，恰好也是 1.21.1 + NeoForge）。

用法
----
    python pack_composition.py --pack "All the Mods 10=D:\\path\\mods" --pack "nation-pack=..."
    python pack_composition.py --pack "name=path" --md data/pack-composition.md --cache data/mod-cache.json

缓存：`--cache` 里按 SHA-1 存已查过的结果，重跑几乎不花时间（换包也复用）。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "nation-pack-composition/0.1 (personal modpack dev)"
HERE = Path(__file__).resolve().parent

SLOT: dict[str, str] = {
    "worldgen": "世界与地理",
    "adventure": "冒险",
    "mobs": "生物与威胁",
    "decoration": "建造与装饰",
    "food": "食物与农业",
    "storage": "存储与物流",
    "transportation": "交通",
    "equipment": "装备与工具",
    "magic": "魔法/特殊系统",
    "technology": "科技与自动化",
    "management": "管理与治理",
    "game-mechanics": "玩法机制",
    "utility": "生活质量与信息",
    "social": "社交与联机",
    "economy": "经济",
    "minigame": "小游戏",
    "cursed": "猎奇",
    "optimization": "性能优化",
    "library": "前置库",
}

# 判定"内容 mod"还是"非内容"：库 / 性能 / 纯客户端工具不算内容
NON_CONTENT = {"前置库", "性能优化", "管理与治理", "生活质量与信息", "社交与联机"}


def sha1_of(path: Path) -> str:
    h = hashlib.sha1()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def api_get(path: str) -> dict:
    req = urllib.request.Request(API + path, headers={"User-Agent": UA})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return {}
            time.sleep(1.0 * (attempt + 1))
        except Exception:
            time.sleep(1.0 * (attempt + 1))
    return {}


def classify(jar: Path, cache: dict, projects: dict, verbose: bool = False) -> dict:
    sha = sha1_of(jar)
    if sha in cache:
        return cache[sha]
    ver = api_get(f"/version_file/{sha}")
    if not ver:
        rec = {"file": jar.name, "slug": None, "slot": "未识别", "side": None}
        cache[sha] = rec
        return rec
    pid = ver.get("project_id")
    if pid not in projects:
        projects[pid] = api_get(f"/project/{pid}") or {}
    proj = projects[pid]
    cats = proj.get("categories", []) or []
    # 一个项目可能有多个类目：非内容类目优先（库/性能优先归类为非内容）
    slots = [SLOT.get(c, c) for c in cats]
    for prefer in ("前置库", "性能优化"):
        if prefer in slots:
            slot = prefer
            break
    else:
        slot = slots[0] if slots else "未分类"
    rec = {
        "file": jar.name,
        "slug": proj.get("slug"),
        "title": proj.get("title"),
        "slot": slot,
        "slots": slots,
        "side": f"{proj.get('client_side')}/{proj.get('server_side')}",
    }
    cache[sha] = rec
    return rec


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="整合包构成分析")
    ap.add_argument("--pack", action="append", required=True,
                    help="`名字=mods目录`，可重复")
    ap.add_argument("--cache", default=str(HERE.parent / "data" / "mod-cache.json"))
    ap.add_argument("--md")
    args = ap.parse_args()

    cache_path = Path(args.cache)
    cache: dict = {}
    if cache_path.is_file():
        try:
            cache = json.loads(cache_path.read_text(encoding="utf-8"))
            print(f"缓存命中基线：{len(cache)} 条")
        except Exception:
            cache = {}
    projects: dict = {}
    before = len(cache)

    packs: list[tuple[str, Path]] = []
    for spec in args.pack:
        name, _, path = spec.partition("=")
        packs.append((name.strip(), Path(path.strip())))

    results: dict[str, list[dict]] = {}
    for name, mods_dir in packs:
        jars = sorted(mods_dir.glob("*.jar")) if mods_dir.is_dir() else []
        print(f"\n{name}: {len(jars)} 个 jar（{mods_dir}）")
        rows = []
        for i, jar in enumerate(jars, 1):
            rows.append(classify(jar, cache, projects))
            if i % 40 == 0:
                print(f"   ... {i}/{len(jars)}  缓存 {len(cache)}")
                cache_path.parent.mkdir(parents=True, exist_ok=True)
                cache_path.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
            time.sleep(0.08)
        results[name] = rows
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")
        print(f"   完成（新增缓存 {len(cache) - before} 条）")

    # ── 汇总 ──────────────────────────────────────────────────────────────────
    all_slots = sorted({r["slot"] for rows in results.values() for r in rows})
    lines = ["# 整合包构成对照（按功能位）\n",
             "> 数据源：每个 jar 的 SHA-1 反查 Modrinth 官方类目，映射到本包功能位。",
             "> `tools/pack_composition.py` 可重跑（带缓存）。\n"]
    header = "| 功能位 | " + " | ".join(results.keys()) + " |"
    lines.append(header)
    lines.append("|---" * (len(results) + 1) + "|")
    for slot in all_slots:
        cells = []
        for name in results:
            cells.append(str(sum(1 for r in results[name] if r["slot"] == slot)))
        lines.append(f"| {slot} | " + " | ".join(cells) + " |")
    lines.append("| **合计 jar** | " + " | ".join(str(len(results[n])) for n in results) + " |")
    for name in results:
        content = sum(1 for r in results[name] if r["slot"] not in NON_CONTENT and r["slot"] != "未识别")
        lines.append(f"| *其中「内容 mod」* | " + " | ".join(
            [str(sum(1 for r in results[n] if r["slot"] not in NON_CONTENT and r["slot"] != "未识别"))
             if n == name else "" for n in results]) + " |")

    report = "\n".join(lines)
    print("\n" + report)
    if args.md:
        Path(args.md).parent.mkdir(parents=True, exist_ok=True)
        Path(args.md).write_text(report + "\n", encoding="utf-8")
        print(f"\n已写: {args.md}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

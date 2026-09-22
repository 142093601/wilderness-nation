#!/usr/bin/env python3
"""参照包任务书**结构**统计（只读；不抄一个字，ATM10 的任务书是 ARR）。

为什么做这个
-----------
用户要求「任务量太少、流程要编排」，并让我去学本机其它整合包的任务线怎么写。
但直接把别人的任务抄过来是**许可问题**（ATM10 的 quests 是 All Rights Reserved，
我们仓库公开）。所以这里只统计**可复用的结构事实**：

  · 每章多少条、章内是"一条主轴串下去"还是"一个入口分叉"
  · 依赖密度（平均每条挂几个前置）
  · 判据类型的**配比**（item / advancement / checkmark / stat / kill …）
  · 写不写 desc、写多长
  · 形状与尺寸的使用（哪些地方用大图标当路标）
  · 用不用 quest_links（软链接）把主线指向 mod 章

产出写到 tools/out/（gitignore），控制台只打印 ASCII 摘要。

用法
----
    python tools/reference_questbook_style.py
    python tools/reference_questbook_style.py --pack <另一个包名>
"""

from __future__ import annotations

import argparse
import json
import re
import statistics as st
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out"
VERSIONS = Path(r"D:\game\PCL\.minecraft\versions")

PACKS = {
    "atm10": "All the Mods 10",
    "skyhive": "Beebeeblock_TheSkyhive_v2.08_cf_release",
    "dragon": "龙之冒险：新征程v2.3a",
    "ours": None,  # 本项目
}


def snbt_files(root: Path) -> list[Path]:
    return sorted(root.rglob("*.snbt"))


# --- 极简 SNBT 扫描：只取我们需要的字段，不追求完整解析 ---
RE_QUEST = re.compile(r"^\t\{", re.M)          # 章节文件里 quest 是 quests 数组的元素
RE_KEY = re.compile(r'(\w+):\s*("(?:[^"\\]|\\.)*"|[-\d.]+|\[|\{)')


def chapter_stats(path: Path) -> dict:
    text = path.read_text(encoding="utf-8", errors="replace")
    # quest 条目数：用 "tasks:[" 的出现次数更稳（每条 quest 必有 tasks）
    n_tasks_blocks = text.count("tasks: [")
    deps = len(re.findall(r"dependencies: \[", text))
    links = len(re.findall(r"quest_links: \[", text))
    types = Counter(re.findall(r'\{\s*id:\s*"[0-9A-F]{16}",\s*type:\s*"(\w+)"', text))
    if not types:
        types = Counter(re.findall(r'type:\s*"(\w+)"', text))
    shapes = Counter(re.findall(r'shape:\s*"(\w+)"', text))
    sizes = [float(x) for x in re.findall(r"size:\s*([\d.]+)", text)]
    icons = len(re.findall(r"icon:\s*\{", text))
    return {
        "file": path.name,
        "quests": n_tasks_blocks,
        "deps": deps,
        "links": links,
        "icons": icons,
        "types": types,
        "shapes": shapes,
        "size_med": st.median(sizes) if sizes else 0.0,
        "size_big": sum(1 for s in sizes if s >= 2.0),
    }


def lang_stats(root: Path) -> dict:
    """从 lang 文件（若存在 zh_cn 或 en_us）统计 desc 覆盖率与长度。"""
    out = {"files": 0, "title": 0, "subtitle": 0, "desc": 0, "desc_lens": []}
    for p in root.rglob("*.snbt"):
        text = p.read_text(encoding="utf-8", errors="replace")
        out["files"] += 1
        out["title"] += len(re.findall(r"\.title:", text))
        out["subtitle"] += len(re.findall(r"\.subtitle:", text))
        for m in re.finditer(r"\.description:\s*\[", text):
            seg = text[m.end(): m.end() + 400]
            lines = re.findall(r'"((?:[^"\\]|\\.)*)"', seg.split("]")[0])
            out["desc"] += 1
            out["desc_lens"].append(sum(len(x) for x in lines))
    return out


def analyze(name: str, quests_dir: Path) -> dict:
    chaps = [p for p in snbt_files(quests_dir) if p.parent.name in ("chapters", "quests")]
    stats = [chapter_stats(p) for p in chaps]
    stats = [s for s in stats if s["quests"] > 0]
    per = [s["quests"] for s in stats]
    all_types: Counter = Counter()
    all_shapes: Counter = Counter()
    for s in stats:
        all_types.update(s["types"])
        all_shapes.update(s["shapes"])
    langs = {"files": 0, "title": 0, "desc": 0, "desc_lens": []}
    for sub in ("lang",):
        d = quests_dir / sub
        if d.is_dir():
            ls = lang_stats(d)
            for k in ("files", "title", "subtitle", "desc"):
                langs[k] = langs.get(k, 0) + ls.get(k, 0)
            langs["desc_lens"] += ls["desc_lens"]
    return {
        "name": name,
        "dir": str(quests_dir),
        "chapters": len(stats),
        "quests": sum(per),
        "per_chapter_median": st.median(per) if per else 0,
        "per_chapter_mean": round(st.mean(per), 1) if per else 0,
        "per_chapter_max": max(per) if per else 0,
        "per_chapter_min": min(per) if per else 0,
        "quests_with_deps": sum(s["deps"] for s in stats),
        "quests_with_icon": sum(s["icons"] for s in stats),
        "quest_links": sum(s["links"] for s in stats),
        "types": dict(all_types.most_common()),
        "shapes": dict(all_shapes.most_common()),
        "big_icons": sum(s["size_big"] for s in stats),
        "lang": {
            "files": langs.get("files", 0),
            "titles": langs.get("title", 0),
            "subtitles": langs.get("subtitle", 0),
            "descs": langs.get("desc", 0),
            "desc_median_len": int(st.median(langs["desc_lens"])) if langs.get("desc_lens") else 0,
            "desc_mean_len": int(st.mean(langs["desc_lens"])) if langs.get("desc_lens") else 0,
        },
        "top_chapters": sorted(stats, key=lambda s: -s["quests"])[:6],
    }


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser()
    ap.add_argument("--pack", choices=list(PACKS), default=None)
    args = ap.parse_args()

    targets: list[tuple[str, Path]] = []
    for key, folder in PACKS.items():
        if args.pack and key != args.pack:
            continue
        if folder is None:
            d = HERE / "questbook" / "chapters"
            if d.is_dir():
                targets.append(("ours(TOML 源)", d))
            d2 = HERE.parent / "pack" / "config" / "ftbquests" / "quests" / "chapters"
            if d2.is_dir():
                targets.append(("ours(产物)", d2))
            continue
        d = VERSIONS / folder / "config" / "ftbquests" / "quests" / "chapters"
        if d.is_dir():
            targets.append((key, d))
        else:
            print(f"[skip] {key}: not found -> {d}")

    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for name, d in targets:
        r = analyze(name, d)
        results.append(r)
        print(f"{name:16s} chapters={r['chapters']:4d} quests={r['quests']:5d} "
              f"median/ch={r['per_chapter_median']:6.1f} max/ch={r['per_chapter_max']:4d} "
              f"links={r['quest_links']:4d}")

    p = OUT / "reference_style.json"
    p.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n-> {p}")

    # 可读报告
    lines: list[str] = []
    for r in results:
        lines.append(f"===== {r['name']} =====")
        lines.append(f"目录 {r['dir']}")
        lines.append(f"章 {r['chapters']} · 任务 {r['quests']} · 每章 中位 {r['per_chapter_median']}"
                     f" / 均 {r['per_chapter_mean']} / 最大 {r['per_chapter_max']}")
        lines.append(f"带依赖 {r['quests_with_deps']} ({r['quests_with_deps']*100//max(r['quests'],1)}%)"
                     f" · 带图标 {r['quests_with_icon']} ({r['quests_with_icon']*100//max(r['quests'],1)}%)"
                     f" · 大图标(>=2.0) {r['big_icons']} · quest_links {r['quest_links']}")
        lines.append(f"判据类型 {r['types']}")
        lines.append(f"形状 {r['shapes']}")
        L = r["lang"]
        lines.append(f"语言：条目 {L['files']} · 标题 {L['titles']} · 副标题 {L['subtitles']} · "
                     f"正文 {L['descs']}（占任务 {L['descs']*100//max(r['quests'],1)}%，"
                     f"中位 {L['desc_median_len']} 字 / 均 {L['desc_mean_len']}）")
        lines.append("最大的几章：")
        for c in r["top_chapters"]:
            lines.append(f"  {c['quests']:4d} 条  依赖 {c['deps']:4d}  图标 {c['icons']:4d}  {c['file']}")
        lines.append("")
    p2 = OUT / "reference_style.md"
    p2.write_text("\n".join(lines), encoding="utf-8")
    print(f"-> {p2}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""lang_drift_check.py -- 找出「语言文件里的标题/正文与 TOML 源不一致」的条目。

为什么需要它（2026-09-22 实测，是 `merge_lang` 的必然副作用）
-----------------------------------------------------------
`tools/questbook.py` 合并语言文件的纪律是 **「只补缺失的键，绝不覆盖已存在的」**
—— 这是为了**保护用户手改过的文案**（`PLAN-questbook.md` §五：文字归用户）。

但它有一个必然的副作用：**我（或 agent）后来改了同一个任务的标题，
语言文件里还是旧值**。表现是「TOML 里写着『阵亡者墓园』，游戏里显示『墓园』」，
而且**没有任何一处会报错** —— 离线校验只看键在不在，不看值对不对。

2026-09-22 实测：并行写作期间至少 3 条标题漂移（`defense_era` 的墓园、
`defense` 的固定炮座、以及一条被删任务留下的旧键）。

用法
----
    python tools/lang_drift_check.py            # 只报告
    python tools/lang_drift_check.py --fix       # 就地改标题（逐行替换，不动别的内容）
    python tools/lang_drift_check.py --fix --with-desc   # 连正文也一起对齐

**只改 title/subtitle** 是刻意的：`description` 一旦存在就可能是用户改过的碑文，
默认绝不动它；要动必须显式 `--with-desc`。
"""

from __future__ import annotations

import argparse
import importlib.util
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
LANG = ROOT / "pack" / "config" / "ftbquests" / "quests" / "lang" / "zh_cn" / "chapters"


def load_qb():
    spec = importlib.util.spec_from_file_location("qb", HERE / "questbook.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)          # type: ignore[union-attr]
    return mod


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="语言文件与 TOML 源的漂移检查")
    ap.add_argument("--fix", action="store_true", help="就地修（默认只报告）")
    ap.add_argument("--with-desc", action="store_true", help="正文也一起对齐（默认不动）")
    args = ap.parse_args()

    qb = load_qb()
    chapters = qb.load_chapters()
    comp = qb.Compiler()
    for ch in chapters:
        comp.compile_chapter(ch)

    total_drift = 0
    for ch in chapters:
        lp = LANG / f"{ch.key}.snbt"
        if not lp.is_file():
            continue
        text = lp.read_text(encoding="utf-8")
        idmap = comp.quest_ids.get(ch.key, {})
        by_title: dict[str, str] = {}
        for name, qid in idmap.items():
            q = next(x for x in ch.quests if x["name"] == name)
            if q.get("title"):
                by_title[f"quest.{qid}.title"] = str(q["title"])

        lines = text.split("\n")
        changed = 0
        for i, ln in enumerate(lines):
            m = re.match(r'^(\s*)(quest\.[0-9A-F]{16}\.title):\s*"(.*)"\s*$', ln)
            if not m:
                continue
            key, cur = m.group(2), m.group(3)
            want = by_title.get(key)
            if want is None or want == cur:
                continue
            total_drift += 1
            print(f"  {ch.key}: {key}\n      语言文件「{cur}」 -> TOML「{want}」")
            if args.fix:
                lines[i] = f'{m.group(1)}{key}: "{want}"'
                changed += 1
        if args.fix and changed:
            lp.write_text("\n".join(lines), encoding="utf-8", newline="\n")
            print(f"  -> {lp.name} 改了 {changed} 行标题")

    print()
    if total_drift == 0:
        print("PASS -- 语言文件里的标题与 TOML 源一致")
    else:
        print(f"{'已修' if args.fix else '发现'} {total_drift} 处标题漂移")
    return 0 if (total_drift == 0 or args.fix) else 1


if __name__ == "__main__":
    raise SystemExit(main())

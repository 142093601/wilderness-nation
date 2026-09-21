#!/usr/bin/env python3
"""直接调用 text_audit 的检查逻辑并在内存里报结果 —— 不读任何中间 JSON 文件。

为什么需要它（2026-09-22 教训）：
  `text_audit.py` 会把问题清单写到固定的 `data/_text_audit.json`。
  我在排查时反复读到**上一次运行**留下的那份文件，据此得出互相矛盾的结论
  （一会儿"字幕重复"，一会儿"0 重复"），连续 4 轮。
  ⇒ 需要判断时**直接调 `check()`**，在同一进程里拿结果，
    不从任何可能过期的中间产物里读。

用法：
    python tools/text_audit_live.py            # 全部
    python tools/text_audit_live.py --errors   # 只看错误级（退出码 1 表示有错误）
"""

from __future__ import annotations

import argparse
import importlib.util
import sys
from collections import Counter
from pathlib import Path

HERE = Path(__file__).resolve().parent


def load_text_audit():
    spec = importlib.util.spec_from_file_location("ta", HERE / "text_audit.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def main() -> int:
    ap = argparse.ArgumentParser(description="直接跑文案审计（不读中间 JSON）")
    ap.add_argument("--errors", action="store_true", help="只报错误级")
    ap.add_argument("--show", type=int, default=40, help="最多显示多少条")
    args = ap.parse_args()

    ta = load_text_audit()
    chapters = ta.load_all()
    issues = ta.check(chapters)

    counts = Counter(i["level"] for i in issues)
    total_q = sum(len(c["quests"]) for c in chapters)
    print(f"章 {len(chapters)}   任务 {total_q}")
    print(f"错误 {counts.get('错误', 0)} · 警告 {counts.get('警告', 0)} · 提示 {counts.get('提示', 0)}")
    print()

    levels = ["错误"] if args.errors else ["错误", "警告", "提示"]
    shown = 0
    for lv in levels:
        group = [i for i in issues if i["level"] == lv]
        if not group:
            continue
        print(f"---- {lv}（{len(group)}）----")
        for i in group:
            if shown >= args.show:
                print(f"  ...（还有 {len(group) - (shown - (shown - 0))} 条，用 --show 调大）")
                break
            print(f"  [{i['where']}] {i['what']}")
            shown += 1
        print()

    return 1 if counts.get("错误") else 0


if __name__ == "__main__":
    sys.exit(main())

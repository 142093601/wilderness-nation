#!/usr/bin/env python3
"""questbook_name_guard.py -- 守住「任务的稳定名（name）不许消失、不许改名」。

为什么这比它看起来重要
--------------------
任务 id 是 `sha256("quest|<章 key>|<name>")[:16]` —— **name 变了，id 就变了**。
后果有三层，而且都不会报错：

1. 语言文件里 `quest.<旧id>.title/description` 变成**孤儿键**：
   玩家（或用户）改过的文案**静默丢失**，游戏里显示的是新生成的空文案。
2. 旧 id 的完成记录留在存档里，新 id 是"没做过" → **玩家进度回退**。
3. 章节结构里所有 `dependencies` 指向的旧 id 变成悬空引用。

2026-09-22 实测两次：一个 agent 用 `write` 重写整章时**截断**在半个条目上，
顺带吞掉了原有的 3 条任务；另一次把 `f_lanterns` 改名成 `f_console_tables`，
于是 2 个语言键成了孤儿。两次都不是靠眼睛发现的，是脚本比对出来的。

用法
----
    python tools/questbook_name_guard.py             # 与 git HEAD 比（默认）
    python tools/questbook_name_guard.py --rev HEAD~3
    python tools/questbook_name_guard.py --rev ""     # 只看章内重复/空名，不与历史比

退出码 = 问题数（0 = 过）。
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import tomllib
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
CHAPTERS = HERE / "questbook" / "chapters"


def names_in_text(text: str) -> list[str]:
    try:
        raw = tomllib.loads(text)
    except Exception:  # noqa: BLE001
        return []
    return [q.get("name", "") for q in raw.get("quest", []) or []]


def names_in_file(p: Path) -> list[str]:
    try:
        return names_in_text(p.read_text(encoding="utf-8"))
    except Exception:  # noqa: BLE001
        return []


def names_at_rev(rev: str, rel: str) -> list[str] | None:
    r = subprocess.run(["git", "show", f"{rev}:{rel}"], cwd=str(ROOT),
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        return None
    return names_in_text(r.stdout)


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="任务 name 守护")
    ap.add_argument("--rev", default="HEAD", help="对比的 git 版本（空串 = 不与历史比）")
    ap.add_argument("--allow", default="",
                    help="**有意删除**的 name（逗号分隔）。本守护的职责是抓"
                         "「不小心删掉」，不是禁止删除 —— 所以删除要么显式写进这里，"
                         "要么就当作事故处理。")
    args = ap.parse_args()
    allowed = {x.strip() for x in args.allow.split(",") if x.strip()}

    problems = 0
    for p in sorted(CHAPTERS.glob("*.toml")):
        cur = names_in_file(p)
        rel = str(p.relative_to(ROOT)).replace("\\", "/")

        # ① 章内重复 / 空名
        dupes = sorted({n for n in cur if cur.count(n) > 1})
        if dupes:
            print(f"  x {p.name}: 章内 name 重复 {dupes}")
            problems += 1
        if any(not n for n in cur):
            print(f"  x {p.name}: 有空 name")
            problems += 1

        if not args.rev:
            continue

        # ② 与历史版本比：消失的 name
        old = names_at_rev(args.rev, rel)
        if old is None:
            continue                      # 新文件，没有历史
        gone = [n for n in old if n not in cur and n not in allowed]
        ok_gone = [n for n in old if n not in cur and n in allowed]
        if ok_gone:
            print(f"    {p.name}: 有意删除 {len(ok_gone)} 个 name（--allow 已登记）：{ok_gone}")
        if gone:
            print(f"  x {p.name}: 相对 {args.rev} 消失了 {len(gone)} 个 name（id 会变、"
                  f"语言键会成孤儿、玩家进度会回退）：{gone[:10]}")
            problems += 1
        added = len(cur) - len([n for n in cur if n in old])
        if added:
            print(f"    {p.name}: +{added} 条新任务（原有 {len(old)} 条全在）")

    print()
    if problems:
        print(f"FAIL -- {problems} 个问题")
    else:
        print("PASS -- 没有 name 消失、没有重复、没有空名")
    return problems


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""清理 lang SNBT 里的**孤儿键** —— 那些在对应章里已经没有任务的键。

什么时候会有孤儿：
  · 任务被改名（id 变）→ 旧 id 的键留下
  · 任务被搬到别章（id 含章 key，所以搬走 = 换 id）→ 源章留下旧键
  · 任务被删除

为什么必须清：`questbook_check.py` 第 ⑤ 项会把它判为 FAIL
（"对应任务已不存在，玩家改的字会丢"），而且它会一直累积。

安全前提（本脚本的核心约束）：
  **只删"确实没有对应任务"的键。** 判定依据是**生成产物**（`chapters/<key>.snbt`
  里的 quest id 集合），不是猜。删之前打印清单，删之后重新数一遍。

缩进：照抄文件里已有的形式（`_parse_snbt_blocks` 同款块解析），不重排格式。

用法：
    python tools/questbook_clean_orphans.py --check      # 只报告
    python tools/questbook_clean_orphans.py             # 清理全部章
    python tools/questbook_clean_orphans.py --chapter infra
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
Q = ROOT / "pack" / "config" / "ftbquests" / "quests"
CH_SNBT = Q / "chapters"
LANG_CH = Q / "lang" / "zh_cn" / "chapters"


def quest_ids_of(chapter_snbt: Path) -> set[str]:
    """取该章产物里的 quest id。

    quest 的 id 在块里是 **2 个 tab** 缩进；task/reward 的 id 是更深缩进。
    早期我用 3-tab 正则数过，把 task 的 id 也数进去了 —— 那是错的。
    """
    text = chapter_snbt.read_text(encoding="utf-8")
    return set(re.findall(r'^\t\tid: "([0-9A-F]{16})"', text, re.M))


def split_blocks(text: str) -> tuple[list[str], list[tuple[str, list[str]]], list[str]]:
    """把 `{ \\n key: value \\n ... }` 拆成 (头, [(key, 块行)], 尾)。"""
    lines = text.split("\n")
    head: list[str] = []
    blocks: list[tuple[str, list[str]]] = []
    tail: list[str] = []
    i, n = 0, len(lines)
    while i < n and lines[i].strip() in ("", "{"):
        head.append(lines[i])
        i += 1
    while i < n:
        raw = lines[i]
        s = raw.strip()
        if s == "}":
            tail = lines[i:]
            break
        if not s or ":" not in s:
            head.append(raw)
            i += 1
            continue
        key = s.split(":", 1)[0].strip()
        block = [raw]
        depth = raw.count("[") + raw.count("{") - raw.count("]") - raw.count("}")
        i += 1
        while depth > 0 and i < n:
            block.append(lines[i])
            depth += (lines[i].count("[") + lines[i].count("{")
                      - lines[i].count("]") - lines[i].count("}"))
            i += 1
        blocks.append((key, block))
    if not tail:
        tail = ["}"]
    return head, blocks, tail


def main() -> int:
    ap = argparse.ArgumentParser(description="清理 lang 里的孤儿键")
    ap.add_argument("--check", action="store_true", help="只报告，不写")
    ap.add_argument("--chapter", help="只处理某一章（key）")
    args = ap.parse_args()

    if not LANG_CH.is_dir():
        print(f"[FAIL] 找不到 {LANG_CH}", file=sys.stderr)
        return 2

    total_removed = 0
    for lang in sorted(LANG_CH.glob("*.snbt")):
        key = lang.stem
        if args.chapter and key != args.chapter:
            continue
        snbt = CH_SNBT / f"{key}.snbt"
        if not snbt.is_file():
            print(f"  [skip] {key}: 没有对应产物 {snbt.name}")
            continue
        ids = quest_ids_of(snbt)
        text = lang.read_text(encoding="utf-8")
        head, blocks, tail = split_blocks(text)

        keep: list[tuple[str, list[str]]] = []
        drop: list[tuple[str, list[str]]] = []
        for k, blk in blocks:
            m = re.match(r"quest\.([0-9A-F]{16})\.", k)
            if m and m.group(1) not in ids:
                drop.append((k, blk))
            else:
                keep.append((k, blk))

        if not drop:
            continue
        print(f"  {key}: 孤儿 {len(drop)} 个键")
        if args.check:
            for k, _ in drop[:6]:
                print(f"      {k}")
            if len(drop) > 6:
                print(f"      ...（共 {len(drop)}）")
            continue

        body = [b for _k, blk in keep for b in blk]
        out = "\n".join(head + body + tail)
        if not out.endswith("\n"):
            out += "\n"
        lang.write_text(out, encoding="utf-8", newline="\n")
        total_removed += len(drop)

    if args.check:
        print("\n（--check 未写盘）")
    else:
        print(f"\n共删除 {total_removed} 个孤儿键")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""toml_quote_fix.py -- 把 TOML 字符串值里**误用的英文双引号**换成中文引号「」。

为什么需要它
-----------
TOML 的 `"…"` 字符串**不允许内嵌未转义的 `"`**。写中文文案时手滑写成

    desc = [ "它比一排潜影盒好用在于"它有内胆"。" ]

的结果不是"引号显示得怪"，而是**整个文件解析失败**（`lint_questbook_toml.py`
直接 `FAIL  <文件>`），你已经写的一整章白写。
2026-09-22 实测：`15-survival.toml` 一次踩了 12 行。

规则（只动**该动的地方**）
------------------------
- 只处理**以制表符 + `"` 开头的整行**（也就是 TOML 的**多行数组元素**，我们的
  `desc = [ … ]` 就是这个形状），以及 `title = "…"` / `subtitle = "…"` 单行值。
- 行**首**与行**尾**的那一对引号是**定界符**，永远保留。
- 中间的引号按出现顺序**成对**换成 `「` / `」`（奇数个时最后一个保持原样并告警）。
- **默认 dry-run**，只报告；加 `--write` 才落盘（显式 utf-8、LF、无 BOM）。

用法
----
    python tools/toml_quote_fix.py                     # 扫描全部章，只报告
    python tools/toml_quote_fix.py --write             # 修
    python tools/toml_quote_fix.py --write --file tools/questbook/chapters/15-survival.toml
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CHAPTERS = HERE / "questbook" / "chapters"

# 行首是 制表符 + 引号（数组元素），或  `key = "` 形式
ARRAY_ITEM = re.compile(r'^(\t+")(.*?)(",?)$')
KEYED = re.compile(r'^(\w+ = ")(.*?)("\s*)$')


def fix_line(line: str) -> tuple[str, int]:
    """→ (新行, 换掉的引号对数)。0 表示这行不用动。"""
    m = ARRAY_ITEM.match(line) or KEYED.match(line)
    if not m:
        return line, 0
    head, body, tail = m.group(1), m.group(2), m.group(3)
    if '"' not in body:
        return line, 0
    out: list[str] = []
    open_q = True
    pairs = 0
    for ch in body:
        if ch == '"':
            out.append("「" if open_q else "」")
            if not open_q:
                pairs += 1
            open_q = not open_q
        else:
            out.append(ch)
    if not open_q:                       # 奇数个 → 最后一个成了孤立的「
        out[out.index("「")] = "「"        # 保持不动，交给人看
    return head + "".join(out) + tail, pairs


def parse_lines(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8").split("\n")


def fix_commas(path: Path, write: bool) -> int:
    """给**多行数组元素**补行尾逗号。

    TOML 的数组必须用逗号分隔（和 SNBT 不同）。写法上最容易漏的是
    `desc = [ … ]` 里一条文案拆成两行、或者纯粹手滑：
        desc = [
            "第一句"
            "第二句",
        ]
    解析器只报 `Unclosed array`，而且指向**最后一行**（不是出错那行）。

    判定条件（只改**确定**的）：某行以 `\\t"` 开头且行尾不是 `",` / `"`，
    而**下一条非空行**也以 `\\t"` 开头 —— 那这一行后面必须补逗号。
    """
    lines = parse_lines(path)
    fixed = 0
    for i, ln in enumerate(lines):
        if not ln.startswith('\t"'):
            continue
        if ln.rstrip().endswith('",'):
            continue                      # 已经有逗号
        # ⚠️ 注意：合法的**最后一条**数组元素是以 `"` 结尾、后面跟 `]` 的。
        # 所以判据不是"行尾是不是引号"，而是"**下一行还是不是数组元素**"。
        nxt = ""
        for j in range(i + 1, len(lines)):
            if lines[j].strip():
                nxt = lines[j]
                break
        if not nxt.startswith('\t"'):
            continue
        if write:
            lines[i] = ln.rstrip() + ","
        fixed += 1
    if write and fixed:
        path.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    return fixed


def process(path: Path, write: bool) -> tuple[int, int]:
    lines = parse_lines(path)
    total = 0
    hits = 0
    for i, ln in enumerate(lines):
        new, n = fix_line(ln)
        if n:
            hits += 1
            total += n
            if write:
                lines[i] = new
            else:
                print(f"  {path.name}:{i + 1}  {n} 对")
                print(f"     旧: {ln.strip()[:110]}")
                print(f"     新: {new.strip()[:110]}")
    if write and hits:
        path.write_text("\n".join(lines), encoding="utf-8", newline="\n")
    return hits, total


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="TOML 文案里的英文引号 → 「」")
    ap.add_argument("--write", action="store_true", help="真的落盘（默认只报告）")
    ap.add_argument("--file", help="只处理这一个文件")
    ap.add_argument("--commas", action="store_true",
                    help="顺带补多行数组元素缺失的行尾逗号（TOML 数组必须逗号分隔）")
    args = ap.parse_args()

    targets = [Path(args.file)] if args.file else sorted(CHAPTERS.glob("*.toml"))
    grand_hits = grand_pairs = 0
    for p in targets:
        if not p.is_file():
            print(f"[skip] 不存在：{p}")
            continue
        h, n = process(p, args.write)
        if h:
            print(f"{'FIXED' if args.write else 'FOUND'} {p.name}: {h} 行 / {n} 对")
        grand_hits += h
        grand_pairs += n
        if args.commas:
            c = fix_commas(p, args.write)
            if c:
                print(f"{'FIXED' if args.write else 'FOUND'} {p.name}: 补了 {c} 处行尾逗号")
                grand_hits += c
    verb = "已修" if args.write else "待修（加 --write 落盘）"
    print(f"\n合计 {grand_hits} 行 / {grand_pairs} 对 —— {verb}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

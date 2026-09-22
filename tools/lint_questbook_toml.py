#!/usr/bin/env python3
"""lint_questbook_toml.py -- 解析检查 questbook 下的所有 TOML，报出语法与常见笔误。

为什么单独一个工具
------------------
TOML 里有两类**肉眼看不出来、tomllib 一跑就报**的坑，本项目各踩过多次：

1. **在 `"…"` 字符串里再写 `"`**（写中文引号时手滑）。
   解析器的报错是 `Illegal character '\n'` 或 `Unclosed array`，
   位置指向**下一行**，很容易查错方向。→ 用「」。
2. **多行数组元素漏逗号**。TOML 的数组**必须**用逗号分隔（这点与 SNBT 不同，
   也和"看起来像 JSON5"的直觉相反）：
       desc = [
           "第一句"
           "第二句",     ← 第一行少了逗号
       ]
   报错同样是 `Unclosed array`，而它指向的却是**最后一行**。

2026-09-22 实测：这两类错误合计打断了 4 次撰写流程，每次都要人工二分定位。
所以这里在解析失败时**顺带把可疑行指出来**，而不是只丢一个行号。
"""
from __future__ import annotations

import sys
import tomllib
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "questbook"


def suspects(path: Path) -> list[tuple[int, str, str]]:
    """→ [(行号, 行内容, 原因)]，只报**确定**的可疑行，不猜。"""
    out: list[tuple[int, str, str]] = []
    try:
        lines = path.read_text(encoding="utf-8").split("\n")
    except Exception:  # noqa: BLE001
        return out
    for i, ln in enumerate(lines):
        stripped = ln.strip()
        # ① 制表符 + 引号开头的数组元素：行尾必须是 `",`（最后一条是 `"`）
        if ln.startswith('\t"'):
            if not ln.rstrip().endswith(('",', '"')):
                out.append((i + 1, stripped[:100], "数组元素行没有以 「\",」 收尾"))
            else:
                nxt = ""
                for j in range(i + 1, len(lines)):
                    if lines[j].strip():
                        nxt = lines[j]
                        break
                if nxt.startswith('\t"') and not ln.rstrip().endswith('",'):
                    out.append((i + 1, stripped[:100],
                                "下一条还是数组元素，但这一行末尾少了逗号"))
        # ② 行内引号数为奇数（多行字符串的续行除外——那种本工具也报，让人看一眼）
        if ln.count('"') % 2 == 1 and not ln.rstrip().endswith(('",', '"')) and ln.startswith('\t"'):
            out.append((i + 1, stripped[:100], "这一行的双引号数量是奇数"))
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass

    files = sorted(SRC.rglob("*.toml"))
    bad = 0
    for p in files:
        try:
            with p.open("rb") as fh:
                tomllib.load(fh)
            print(f"  OK    {p.relative_to(HERE)}")
        except tomllib.TOMLDecodeError as exc:
            bad += 1
            print(f"  FAIL  {p.relative_to(HERE)}")
            print(f"        {exc}")
            for ln, text, why in suspects(p)[:8]:
                print(f"        可疑 {ln}: [{why}] {text}")
    print()
    print(f"{len(files)} 个文件，{bad} 个解析失败")
    if bad:
        print("提示 ①：TOML 字符串里要写引号请用「」，不要嵌套双引号。")
        print("提示 ②：多行数组（desc = [ … ]）的**每一行末尾都要有逗号**（最后一行可省）。")
        print("提示 ③：不要把一条 desc 拆到两行 —— 一个元素一行，占不下就拆成两个元素。")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""lint_questbook_toml.py -- 解析检查 tasks/questbook 下的所有 TOML，报出语法与常见笔误。

为什么单独一个工具：TOML 里**在双引号字符串内再写双引号**是本项目反复踩的坑
（`tools/README.md` §16.1）。肉眼找不到，但 tomllib 一跑就报。
"""
from __future__ import annotations

import sys
import tomllib
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE / "questbook"


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
    print()
    print(f"{len(files)} 个文件，{bad} 个解析失败")
    if bad:
        print("提示：TOML 字符串里要写引号请用「」或 '…'，不要嵌套双引号。")
    return 1 if bad else 0


if __name__ == "__main__":
    raise SystemExit(main())

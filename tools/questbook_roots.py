#!/usr/bin/env python3
"""questbook_roots.py -- 列出各章的 key / 条数 / 根任务名 / 末条任务名。

写软链接（`[[link]]` 的 `to_chapter` + `to_quest`）时需要知道**目标章里某条任务的
稳定 name**，而从 SNBT 产物里看不到 name（产物里只有 id）。这个探针直接从 TOML 源读。

用法：python tools/questbook_roots.py [章 key 前缀…]
"""
from __future__ import annotations

import pathlib
import sys
import tomllib

SRC = pathlib.Path(__file__).resolve().parent / "questbook" / "chapters"


def main() -> int:
    prefixes = sys.argv[1:]
    for p in sorted(SRC.glob("*.toml")):
        if prefixes and not any(p.name.startswith(x) for x in prefixes):
            continue
        raw = tomllib.loads(p.read_text(encoding="utf-8"))
        qs = raw.get("quest", [])
        if not qs:
            continue
        roots = [q["name"] for q in qs if not (q.get("deps") or [])]
        print(f"{p.stem:22s} key={raw.get('key'):16s} n={len(qs):3d} "
              f"roots={roots[:4]} last={qs[-1]['name']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

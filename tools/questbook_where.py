#!/usr/bin/env python3
"""questbook_where.py -- 在产物 SNBT 里按任务 id 反查它属于哪一章、哪个 name、坐标多少。

用途：`questbook_check` 第 ⑧ 项（坐标重叠）报的是**产物里的 id**，而 TOML 里
用的是稳定 name —— 没有这张对照表就没法回去改。这个脚本把 id 翻译回
「章 TOML + name + title + x/y」，只读，不改。
"""
from __future__ import annotations

import importlib.util
import pathlib
import sys
import tomllib

HERE = pathlib.Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("qb", HERE / "questbook.py")
qb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qb)          # type: ignore[union-attr]

want = set(sys.argv[1:])
chapters = qb.load_chapters()
comp = qb.Compiler()
for ch in chapters:
    comp.compile_chapter(ch)

for ch in chapters:
    idmap = comp.quest_ids.get(ch.key, {})
    pos = qb.layout(ch)
    for name, qid in idmap.items():
        if qid in want:
            q = next(x for x in ch.quests if x["name"] == name)
            x, y = pos.get(name, (0, 0))
            if "x" in q and "y" in q:
                x, y = float(q["x"]), float(q["y"])
            print(f"{qid}  {ch.path.name:24s} name={name:26s} "
                  f"title={q.get('title')}  xy=({x},{y})")

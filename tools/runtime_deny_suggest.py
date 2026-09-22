#!/usr/bin/env python3
"""runtime_deny_suggest.py -- 为「真机拒绝过的 id」挑替代，**只从真机证明可用的 id 里挑**。

可靠性分级（这次踩透了，写下来）
------------------------------
  · tier 1 = **任务书里已用过、且两次真机扫描都没被拒**的 id → 真机证明可用
  · tier 2 = 只在 registry.json 里的 id → **不可信**：本次 31 条新坏 id 里有
    5 条正是我上一轮按 tier 2 挑的（`letsparkour:jelly_parkour_slab`、
    `waystones:sharestone`、`enhancedcelestials:meteor`、
    `create_dragons_plus:*_dye_bucket`）。**registry 只证明"jar 提到过它"。**
  · tier 3 = 原版 `minecraft:` id → 只要 registry 有就一定可用

所以本脚本**只从 tier 1 与 tier 3 里挑**，并按相似度排序。
"""
from __future__ import annotations

import difflib
import json
import pathlib
import re

HERE = pathlib.Path(__file__).resolve().parent
CHAPTERS = HERE / "questbook" / "chapters"

deny = set()
for ln in (HERE / "registry_runtime_deny.txt").read_text(encoding="utf-8").splitlines():
    ln = ln.split("#", 1)[0].strip()
    if ln and ":" in ln:
        deny.add(ln)

pat = re.compile(r'"([a-z0-9_]+:[a-z0-9_/]+)"')
used: set[str] = set()
for f in CHAPTERS.glob("*.toml"):
    used.update(pat.findall(f.read_text(encoding="utf-8")))

reg = json.loads((HERE / "registry.json").read_text(encoding="utf-8"))
known = set(reg["items"]) | set(reg["blocks"])

tier1 = {i for i in used if i not in deny}                     # 真机证明可用
tier3 = {i for i in known if i.startswith("minecraft:") and i not in deny}

out: list[str] = []
for bad in sorted(deny):
    if bad not in used:
        continue                       # 任务书没用到它，不用挑
    ns = bad.split(":", 1)[0]
    pool = sorted(i for i in tier1 if i.startswith(ns + ":"))
    src = "tier1 同命名空间"
    if not pool:
        pool = sorted(tier3)
        src = "tier3 原版（同命名空间无可信候选）"
    cands = difflib.get_close_matches(bad, pool, n=5, cutoff=0.0)
    out.append(f"{bad}    [{src} · 候选 {len(pool)}]")
    for c in cands:
        out.append(f"      -> {c}")

p = HERE / "out" / "suggest2.txt"
p.write_text("\n".join(out), encoding="utf-8")
print(f"deny {len(deny)} · 任务书用到 {sum(1 for b in deny if b in used)} 条")
print(f"-> {p}")

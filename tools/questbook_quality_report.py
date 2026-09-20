"""按章输出质量概览：任务数 / 终局 / 依赖链 / 自检占比 / 奖励分布 / 判据类型分布。

用途：一眼看出哪一章"设计意图没落地"（例如某章几乎全是自检、或没有任何判据）。
只读，不改任何东西。
"""
from __future__ import annotations

import re
import sys
import tomllib
from collections import Counter
from pathlib import Path

CH = Path(r"D:\project\nation-pack\tools\questbook\chapters")
OUT = Path(r"D:\project\nation-pack\data\_quality.txt")

L: list[str] = []
all_task_types: Counter[str] = Counter()
all_rewards: Counter[str] = Counter()
total_q = 0
total_cm = 0

for p in sorted(CH.glob("*.toml")):
    with p.open("rb") as fh:
        raw = tomllib.load(fh)
    key = raw.get("key", p.stem)
    title = raw.get("title", "?")
    quests = raw.get("quest", []) or []
    n = len(quests)
    total_q += n

    # 终局（hexagon）
    hexes = [q for q in quests if str(q.get("shape", "")) == "hexagon"]
    # 依赖链
    deps_total = sum(len(q.get("deps", []) or []) for q in quests)
    roots = [q for q in quests if not (q.get("deps", []) or [])]
    # 判据
    tt: Counter[str] = Counter()
    cm = 0
    for q in quests:
        for t in q.get("tasks", []) or []:
            ty = str(t.get("type", "?"))
            tt[ty] += 1
            all_task_types[ty] += 1
            if ty == "checkmark":
                cm += 1
                total_cm += 1
    # 奖励
    rw: Counter[str] = Counter()
    for q in quests:
        for r in q.get("rewards", []) or []:
            rt = str(r.get("type", "?"))
            rw[rt] += 1
            all_rewards[rt] += 1

    cm_pct = (cm / n * 100) if n else 0
    L.append(f"### {key:16s} {title}   任务 {n:3d} | 终局 {len(hexes)} | "
             f"依赖边 {deps_total:3d} | 链头 {len(roots)} | 自检 {cm:2d} ({cm_pct:.0f}%)")
    if tt:
        L.append(f"      判据：{dict(tt)}")
    if rw:
        L.append(f"      奖励：{dict(rw)}")
    # 预警
    if n and cm_pct > 50:
        L.append("      ⚠️ 自检占比过高（>50%）——检查是不是有可判定的信号没用上")
    if not hexes:
        L.append("      ⚠️ 没有终局大任务（hexagon）")
    if n > 3 and deps_total == 0:
        L.append("      ⚠️ 完全没有依赖边——任务会散成一片")

L.append("")
L.append(f"合计：{len(list(CH.glob('*.toml')))} 章 / {total_q} 任务 / 自检 {total_cm}"
         f" ({total_cm / total_q * 100:.1f}%)")
L.append(f"判据类型分布：{dict(all_task_types.most_common())}")
L.append(f"奖励类型分布：{dict(all_rewards.most_common())}")

OUT.write_text("\n".join(L) + "\n", encoding="utf-8", newline="\n")
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:  # noqa: BLE001
    pass
print("\n".join(L))
print(f"\nwritten {OUT}")

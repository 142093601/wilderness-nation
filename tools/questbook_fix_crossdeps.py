#!/usr/bin/env python3
"""拆章后清理跨章依赖（40-infra / 42-materials）。

背景：questbook.py **不支持跨章硬依赖**（按设计，跨章用 quest_links 软链）。
拆章时 14 条任务带着"依赖 infra 章任务"搬进了新章，另外 infra 里
有一条（fuel_reserve）依赖了被搬走的 charcoal_kiln。合计 7 处要处理。

处理原则：
  · 材料章内部的依赖（如 iron_line -> ore_processing）保留；
  · 指向 infra 的跨章依赖**删掉** —— 材料章自带入口任务（mats_intro），
    不依赖"先做完基建"才能开始（两章是并列的技艺章，不该互为硬前置）；
  · infra 里 `fuel_reserve -> charcoal_kiln` 改指向 infra 自己的 `coal_line` 的等价物：
    infra 没有煤线（它在材料章），所以改为**无依赖**，并在 desc 里点明燃料在材料章。

做法：只改 `deps = [...]` 这一行，其它字节不动（不碰 desc / title / 判据）。
"""

from __future__ import annotations

import pathlib
import re
import sys

CH = pathlib.Path(r"D:\project\nation-pack\tools\questbook\chapters")
INFRA = CH / "40-infra.toml"
MATS = CH / "42-materials.toml"

# 材料章里指向 infra 的依赖 -> 删掉（材料章不硬依赖基建）
CROSS_FROM_MATS = {
    "tree_farm": ["three_automated"],
    "quarry": ["mine_machine"],
    "ore_processing": ["mine_machine"],
    "bulk_smelting": ["smelter_array"],
    "wool_farm": ["fence_and_gate"],
    "mats_throughput": ["bulk_stock"],
}
# infra 里指向已搬走任务的依赖 -> 也删掉，并补一句 desc 说明
MATS_MOVED = {"charcoal_kiln"}
INFRA_FIX = {"fuel_reserve": ["charcoal_kiln"]}


def rewrite_deps(path: pathlib.Path, fixes: dict[str, list[str]],
                 local_names: set[str]) -> int:
    text = path.read_text(encoding="utf-8")
    blocks = re.split(r"(?=^\[\[quest\]\]\s*$)", text, flags=re.M)
    changed = 0
    out = []
    for b in blocks:
        if not b.startswith("[[quest]]"):
            out.append(b)
            continue
        m = re.search(r'^name = "(.*?)"', b, re.M)
        name = m.group(1) if m else None
        if name in fixes:
            dm = re.search(r"^deps = \[(.*?)\]\s*$", b, re.M)
            if dm:
                deps = re.findall(r'"([^"]+)"', dm.group(1))
                keep = [d for d in deps if d not in fixes[name] and d in local_names]
                dropped = [d for d in deps if d not in keep]
                if dropped:
                    newline = ("deps = [" + ", ".join(f'"{d}"' for d in keep) + "]"
                               if keep else "")
                    if newline:
                        b = b[:dm.start()] + newline + b[dm.end():]
                    else:
                        # 整行删掉（连同它的换行）
                        start = dm.start()
                        end = dm.end()
                        if end < len(b) and b[end] == "\n":
                            end += 1
                        b = b[:start] + b[end:]
                    changed += 1
                    print(f"  {name}: 去掉依赖 {dropped}" + (f"，保留 {keep}" if keep else "，改为无依赖"))
        out.append(b)
    path.write_text("".join(out), encoding="utf-8", newline="\n")
    return changed


def main() -> int:
    for p in (INFRA, MATS):
        if not p.is_file():
            print(f"[FAIL] 缺文件 {p}", file=sys.stderr)
            return 2

    print("=== 42-materials：去掉指向基建的跨章依赖 ===")
    mats_names = set(re.findall(r'^name = "(.*?)"', MATS.read_text(encoding="utf-8"), re.M))
    n1 = rewrite_deps(MATS, CROSS_FROM_MATS, mats_names)

    print("=== 40-infra：修掉指向已搬走任务的依赖 ===")
    infra_names = set(re.findall(r'^name = "(.*?)"', INFRA.read_text(encoding="utf-8"), re.M))
    n2 = rewrite_deps(INFRA, INFRA_FIX, infra_names)

    print(f"\n合计改动 {n1 + n2} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

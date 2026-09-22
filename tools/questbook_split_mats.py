#!/usr/bin/env python3
"""从 `40-infra` 拆出「材料与工业」新章（`42-materials`）。

为什么拆：项目原先否决过"40~60 章"，但那是**基于错误的量级判断**
（见 NEXT.md §十二 的撤销记录）。实测 ATM10 = 63 章，40 章级别不多。
`40-infra` 长到 54 条时混了"公共设施"与"材料产线"两类内容，该分家。

做法：
  1. 从 40-infra.toml 里**整块摘出**生产类任务（按 name 匹配）；
  2. 写进新章 42-materials.toml，并补足该章自己的内容；
  3. 40-infra 剩下的部分保持原样 —— 已有任务的名字/依赖**一个不动**
     （id = sha256("quest|infra|名字")，改名字就换 id、丢 lang 文案、断依赖）。

注意（写这个脚本时踩到的）：描述文本里**不能出现直双引号**，
TOML 字符串会被截断。中文引号一律用「」。
"""

from __future__ import annotations

import pathlib
import re
import sys

CH_DIR = pathlib.Path(r"D:\project\nation-pack\tools\questbook\chapters")
INFRA = CH_DIR / "40-infra.toml"
MATS = CH_DIR / "42-materials.toml"

MOVE = [
    "tree_farm", "quarry", "ore_processing", "bulk_smelting", "concrete_plant",
    "brick_factory", "stone_brick_line", "lumber_mill", "stoneworks",
    "wool_farm", "dye_line", "paper_line", "glass_line", "charcoal_kiln",
]

HEADER = """# 材料与工业（技艺章 · 从「基建」拆出，2026-09-22）
#
# 为什么有这一章
# --------------
# `40-infra` 长到 54 条时混了两类内容：**公共设施**（路/水/照明/生活）与
# **材料产线**（木/石/矿/烧炼/建材）。它们的心智模型不同 ——
# 前者回答「城里还缺什么」，后者回答「原料怎么变成建材」。
# 拆开之后两章各自能继续长（ATM10 的机械动力章有 90 条，材料类也是大章）。
#
# 设计约束（沿用本包规则）
# ----------------------
# · 不重复 mod 自带教学（Create 有 Ponder 动画）：desc 只讲「为什么需要、在流程里的位置」
# · 判据优先用已核验过的 vanilla 物品与确定存在的 Create 物品，不凭空造 mod id
# · 与 100-cat-building（有哪些建材 mod 可用）分工：这里讲「城里真要用起来的那几条线」

key = "materials"
title = "材料与工业"
subtitle = "原料进来、建材出去：一座城的实际吞吐量"
group = "craft"
icon = "minecraft:blast_furnace"
order_index = 22

grid = { step = 1.5, row_h = 1.75 }

"""

NEW = [
    ("mats_intro", "原料从哪来", "minecraft:iron_ore", [],
     [("item", "minecraft:iron_ore", 32)],
     ["这一章只回答一件事：一座城每天要吃多少原料、这些原料怎么稳定进来。",
      "先清点现有的：木、石、铁、煤各能撑多久。"]),

    ("ore_stock", "矿石分类堆放", "minecraft:raw_iron", ["mats_intro"],
     [("item", "minecraft:raw_iron", 64)],
     ["原矿、锭、块分开放。混在一起是最常见的「找不到东西」来源。"]),

    ("coal_line", "煤矿线", "minecraft:coal_ore", ["mats_intro"],
     [("item", "minecraft:coal", 64)],
     ["燃料是工业的血液。煤的稳定供应决定熔炉与蒸汽机能开多久。"]),

    ("iron_line", "铁的生产线", "minecraft:iron_ingot", ["ore_processing"],
     [("item", "minecraft:iron_ingot", 64)],
     ["铁是使用量最大的金属。从原矿到锭，这条线要能连续出。"]),

    ("copper_line", "铜线", "minecraft:copper_ingot", ["ore_processing"],
     [("item", "minecraft:copper_ingot", 64)],
     ["铜大量用于建筑装饰，也是 Create 黄铜的前置。用量比想象的大。"]),

    ("deep_ores", "深层矿", "minecraft:deepslate", ["ore_processing"],
     [("item", "minecraft:deepslate", 64)],
     ["深板岩层矿更多，但挖起来慢。要不要往下走，取决于上面几条线够不够用。"]),

    ("plank_line", "板材线", "minecraft:oak_planks", ["lumber_mill"],
     [("item", "minecraft:oak_planks", 128)],
     ["木板是所有木制品的基础。原木进来、板材出去，中间不用人。"]),

    ("stick_line", "木棍与把手", "minecraft:stick", ["plank_line"],
     [("item", "minecraft:stick", 128)],
     ["工具、栅栏、轨道都要木棍。它便宜但用量极大，值得单独一条线。"]),

    ("chest_line", "箱柜量产", "minecraft:chest", ["plank_line"],
     [("item", "minecraft:chest", 32)],
     ["储物是所有产线的终点。箱子要有安全库存，不然产线会堵。"]),

    ("smooth_stone", "平滑石与磨制石", "minecraft:smooth_stone", ["stoneworks"],
     [("item", "minecraft:smooth_stone", 64)],
     ["同一种石头的不同形态决定建筑质感。批量做，才能大面积铺。"]),

    ("deepslate_bricks", "深板岩建材", "minecraft:deepslate_bricks", ["deep_ores"],
     [("item", "minecraft:deepslate_bricks", 64)],
     ["深色建材做对比与基座。挖深层矿时顺手就有了原料。"]),

    ("clay_and_terracotta", "陶与黏土", "minecraft:terracotta", ["brick_factory"],
     [("item", "minecraft:terracotta", 64)],
     ["陶瓦是最耐看的屋面材料。黏土要成规模地找与囤。"]),

    ("quartz_line", "石英与白色建材", "minecraft:quartz_block", ["bulk_smelting"],
     [("item", "minecraft:quartz_block", 32)],
     ["白色建材做亮度与干净感。下界一趟能带回不少。"]),

    ("copper_blocks", "铜制建材", "minecraft:cut_copper", ["copper_line"],
     [("item", "minecraft:cut_copper", 64)],
     ["铜块会氧化变色，能做新旧对比。切割与打蜡都可以用机械手自动化。"]),

    ("nether_bricks", "下界砖", "minecraft:nether_bricks", ["brick_factory"],
     [("item", "minecraft:nether_bricks", 64)],
     ["下界的建材自带异域感，适合地标与要塞。"]),

    ("glass_pane", "玻璃板与采光", "minecraft:glass_pane", ["glass_line"],
     [("item", "minecraft:glass_pane", 64)],
     ["窗与温室都要玻璃板。它比整块玻璃省料，用量却更大。"]),

    ("concrete_powder", "混凝土粉", "minecraft:white_concrete_powder", ["concrete_plant"],
     [("item", "minecraft:white_concrete_powder", 64)],
     ["混凝土要先出粉再遇水固化。这一步靠鼓风机洗涤能全自动。"]),

    ("mats_throughput", "吞吐量达标", "create:item_vault",
     ["bulk_stock", "iron_line", "plank_line"],
     [("item", "create:item_vault", 8)],
     ["随机抽一条线看：从原料进到成品出，一个人都不需要站。",
      "这一条成立，城的建材供应才算脱离「人拉着走」。"]),

    ("mats_reserve", "建材战略储备", "minecraft:barrel", ["mats_throughput"],
     [("item", "minecraft:barrel", 16)],
     ["常用建材各留一仓。扩建时最怕的不是没矿，是矿在但没炼。"]),

    ("waste_disposal", "废料处理", "minecraft:lava_bucket", ["mats_reserve"],
     [("item", "minecraft:lava_bucket", 4)],
     ["多出来的圆石、泥土要能消掉，不然仓库永远爆满。"]),

    ("mats_monument", "工业碑", "minecraft:anvil",
     ["mats_throughput", "mats_reserve"],
     [("item", "minecraft:anvil", 2)],
     ["刻上这个时代投产的第一条全自动产线是什么。",
      "下一章往更大的机器走之前，原料这一层必须稳。"]),
]


def extract_blocks(text: str, names: set[str]) -> tuple[str, str]:
    parts = re.split(r"(?=^\[\[quest\]\]\s*$)", text, flags=re.M)
    kept, moved = [], []
    for p in parts:
        if not p.startswith("[[quest]]"):
            kept.append(p)
            continue
        m = re.search(r'^name = "(.*?)"', p, re.M)
        if m and m.group(1) in names:
            moved.append(p.rstrip("\n"))
        else:
            kept.append(p)
    return "".join(kept), "\n\n".join(moved)


def toml_block(name, title, icon, deps, tasks, desc) -> str:
    out = ["[[quest]]", f'name = "{name}"', f'title = "{title}"', "desc = ["]
    for d in desc:
        out.append(f'\t"{d}",')
    out += ["]", f'icon = "{icon}"', 'shape = "circle"', "size = 1.2"]
    if deps:
        out.append("deps = [" + ", ".join(f'"{d}"' for d in deps) + "]")
    out.append("tasks = [")
    for t in tasks:
        if t[0] == "adv":
            out.append(f'\t{{ type = "advancement", advancement = "{t[1]}" }},')
        elif t[0] == "checkmark":
            out.append('\t{ type = "checkmark" },')
        else:
            out.append(f'\t{{ type = "item", item = "{t[1]}", count = {t[2]} }},')
    out.append("]")
    return "\n".join(out)


def main() -> int:
    if MATS.exists():
        print(f"[FAIL] {MATS.name} 已存在 —— 本脚本只用于首次拆分", file=sys.stderr)
        return 2

    infra = INFRA.read_text(encoding="utf-8")
    before = len(re.findall(r"^\[\[quest\]\]", infra, re.M))

    kept, moved = extract_blocks(infra, set(MOVE))
    n_moved = len(re.findall(r"^\[\[quest\]\]", moved, re.M))
    if n_moved != len(MOVE):
        print(f"[FAIL] 期望摘出 {len(MOVE)} 条，实际 {n_moved} 条", file=sys.stderr)
        return 2

    INFRA.write_text(kept.rstrip("\n") + "\n", encoding="utf-8", newline="\n")
    after = len(re.findall(r"^\[\[quest\]\]", kept, re.M))
    print(f"40-infra: {before} -> {after}（摘出 {n_moved} 条）")

    moved_names = set(re.findall(r'^name = "(.*?)"', moved, re.M))
    new_names = [q[0] for q in NEW]
    clash = moved_names & set(new_names)
    if clash:
        print(f"[FAIL] 新任务名与搬来的重名：{clash}", file=sys.stderr)
        return 2

    body = HEADER
    body += "# ---------------- 从「基建」带过来的产线 ----------------\n\n"
    body += moved.rstrip("\n") + "\n\n"
    body += "# ---------------- 本段新增：原料、加工与储备 ----------------\n\n"
    for q in NEW:
        body += toml_block(*q) + "\n\n"

    MATS.write_text(body.rstrip("\n") + "\n", encoding="utf-8", newline="\n")
    print(f"42-materials: 新建，{n_moved} 条搬来 + {len(NEW)} 条新增 = {n_moved + len(NEW)} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

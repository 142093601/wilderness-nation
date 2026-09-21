#!/usr/bin/env python3
"""给 `40-infra`（基建）追加任务 —— 在现有 TOML 末尾追加，**不改动已有任务**。

为什么"不改动已有任务"是硬要求：
  id 由 `sha256("quest|infra|名字")` 派生。改名字 = 换 id = 丢 lang 文案 + 断依赖。
  实测事故见 INCIDENTS.md（2026-09-22 加任务把全包 id 打乱）。

做法：读现有 TOML → 找到最后一个 `[[quest]]` 块的结尾 → 在其后追加新块。
新增任务可以依赖旧任务（前向引用现在安全：id 是纯函数）。

新增内容按 6 个段落（不与 100-cat-building / 130-cat-storage 重复）：
  供水系统 / 照明与安全 / 生活设施 / 材料产线 / 交通延伸 / 批量与储备
"""

from __future__ import annotations

import pathlib
import re
import sys

TARGET = pathlib.Path(r"D:\project\nation-pack\tools\questbook\chapters\40-infra.toml")

# (name, title, icon, deps, tasks, desc)
# tasks: ("adv", id) | ("item", id, count) | ("checkmark",)
NEW = [
    # ================= 供水系统 =================
    ("water_tower", "水塔", "minecraft:cauldron", ["roads_500"],
     [("item", "create:fluid_tank", 2), ("item", "create:mechanical_pump", 1)],
     ["主城要有稳定的取水点，不能靠人一桶桶拎。",
      "泵 + 储罐 + 管道，把水从水源送到用水处——这是后面所有流体产线的样板。"]),

    ("irrigation", "灌溉渠", "minecraft:water_bucket", ["water_tower"],
     [("item", "minecraft:water_bucket", 8)],
     ["田地要能自己浇上水。耕地离水源远的时候，这一条决定农场能不能扩。"]),

    ("drainage", "排水", "minecraft:iron_trapdoor", ["water_tower"],
     [("item", "minecraft:iron_trapdoor", 4)],
     ["积水会淹掉矿道与地下室。排水做一遍，比每次被淹了再救省事。"]),

    # ================= 照明与安全 =================
    ("street_lights", "路灯成网", "minecraft:lantern", ["roads_500"],
     [("item", "minecraft:lantern", 32)],
     ["主路与街巷夜里不刷怪，靠的是连续照明而不是零散几盏。",
      "这个时代最常见的损失是「回家路上被摸黑打一顿」——路灯直接解决它。"]),

    ("mine_lighting", "矿道照明", "minecraft:torch", ["deep_mine"],
     [("item", "minecraft:torch", 64)],
     ["矿道黑一处就等于埋一个雷。照明铺满，掉落与迷路都会少。"]),

    ("fire_breaks", "防火隔离", "minecraft:stone_bricks", ["long_hall"],
     [("item", "minecraft:stone_bricks", 64)],
     ["木构区之间留出石质隔断。这是全包唯一防「一把火烧掉半个城」的手段。"]),

    ("fence_and_gate", "圈栏与门", "minecraft:oak_fence_gate", ["perimeter"],
     [("item", "minecraft:oak_fence", 64), ("item", "minecraft:oak_fence_gate", 4)],
     ["牲畜与农作物要拦住，城门要能控。围栏是城市与野地的实际边界。"]),

    ("watch_post", "瞭望位", "minecraft:scaffolding", ["perimeter"],
     [("item", "minecraft:scaffolding", 16)],
     ["视野高于城墙才看得远。发现得早，比打得好更重要。"]),

    # ================= 生活设施 =================
    ("dormitory", "宿舍", "minecraft:red_bed", ["long_hall"],
     [("item", "minecraft:red_bed", 4)],
     ["每人都有一张自己的床（而不是挤在一起）。重生点分散，才不会一锅端。"]),

    ("public_square", "广场", "minecraft:bell", ["canteen"],
     [("item", "minecraft:bell", 1)],
     ["集合点要有明确的中心。以后发号施令、分派任务都从这里开始。"]),

    ("local_warehouse", "分区仓库", "minecraft:chest", ["bulk_sorting"],
     [("item", "minecraft:chest", 16)],
     ["每个工坊旁边一个小仓，不要所有东西都往总仓跑。",
      "这一步省的是「来回走路」，它比任何自动化都先见效。"]),

    ("repair_bench", "修理台", "minecraft:grindstone", ["long_hall"],
     [("item", "minecraft:grindstone", 1), ("item", "minecraft:anvil", 1)],
     ["工具要有地方修。附魔与耐久管理的落脚点在这里。"]),

    # ================= 材料产线 =================
    ("tree_farm", "林木场", "minecraft:oak_sapling", ["three_automated"],
     [("item", "minecraft:oak_sapling", 16), ("item", "create:mechanical_saw", 1)],
     ["原木是最耗的原料。有计划地种与砍，比每次缺了才出去伐一片强。"]),

    ("quarry", "石料场", "minecraft:cobblestone", ["mine_machine"],
     [("item", "minecraft:cobblestone", 256)],
     ["建城的石头要成规模地来。一台钻机配一条传送带，就是一个石料场。"]),

    ("ore_processing", "矿石处理线", "create:crushing_wheel", ["mine_machine"],
     [("item", "create:crushing_wheel", 2)],
     ["矿石进来、粉碎、进熔炉——整条线一个人都不用站。",
      "这是「基建」这个时代最典型的产物。"]),

    ("bulk_smelting", "批量烧炼", "create:encased_fan", ["smelter_array"],
     [("item", "create:encased_fan", 2)],
     ["鼓风机吹熔岩做批量烧炼，比一排熔炉省燃料也省地方。"]),

    ("concrete_plant", "混凝土与建材", "minecraft:white_concrete", ["quarry"],
     [("item", "minecraft:white_concrete", 64)],
     ["建材要能成批出。混凝土让「大体积建筑」变得现实。"]),

    ("brick_factory", "砖窑", "minecraft:bricks", ["quarry"],
     [("item", "minecraft:bricks", 64)],
     ["砖是城里最常见的墙面材料。有了砖窑，建筑不再受「手上有什么」限制。"]),

    ("stone_brick_line", "石砖线", "minecraft:stone_bricks", ["quarry"],
     [("item", "minecraft:stone_bricks", 128)],
     ["石砖是城墙、道路、广场的通用件。批量产出，才有资格谈规模。"]),

    ("lumber_mill", "锯木场", "create:mechanical_saw", ["tree_farm"],
     [("item", "create:mechanical_saw", 2), ("item", "create:depot", 2)],
     ["原木进来、木板出去。把「砍」和「加工」放在同一处，省一趟运输。"]),

    ("stoneworks", "石作场", "create:mechanical_drill", ["quarry"],
     [("item", "create:mechanical_drill", 2)],
     ["石阶、石砖、磨制石料集中在一处加工。",
      "同一种原料的多种形态放在一起，找起来才不费劲。"]),

    ("wool_farm", "羊毛场", "minecraft:white_wool", ["fence_and_gate"],
     [("item", "minecraft:white_wool", 32)],
     ["羊毛是床、地毯、旗帜的原料，也是最早能自动化的畜产。"]),

    ("dye_line", "染料线", "minecraft:red_dye", ["wool_farm"],
     [("item", "minecraft:red_dye", 16)],
     ["有颜色才有区分度：队伍标识、建筑分区、地图标记都靠它。"]),

    ("paper_line", "造纸线", "minecraft:paper", ["tree_farm"],
     [("item", "minecraft:paper", 64)],
     ["纸是地图、书、图纸的前置。地图墙那一章要的纸从这里来。"]),

    ("glass_line", "玻璃线", "minecraft:glass", ["bulk_smelting"],
     [("item", "minecraft:glass", 64)],
     ["窗、温室、观察孔都要玻璃。用鼓风机批量烧，比一炉一炉快得多。"]),

    ("charcoal_kiln", "炭窑", "minecraft:charcoal", ["tree_farm"],
     [("item", "minecraft:charcoal", 64)],
     ["燃料要提前备。炭比原木耐烧，也免了跟煤抢产量。"]),

    # ================= 交通延伸 =================
    ("bridge", "桥", "minecraft:oak_planks", ["roads_500"],
     [("item", "minecraft:oak_planks", 64)],
     ["路遇到水就断了。桥是把路网连成网的关键一件。"]),

    ("tunnel", "隧道", "minecraft:stone", ["roads_500"],
     [("item", "minecraft:stone", 128)],
     ["穿山比绕山省时间。隧道打通一次，之后每次都省。"]),

    ("region_signs", "区域路牌", "minecraft:oak_sign", ["roads_500"],
     [("item", "minecraft:oak_sign", 16)],
     ["每个岔口有牌子指向目的地。多人一起玩时，这一条省的是所有人的问路时间。"]),

    ("supply_post", "补给点", "minecraft:barrel", ["rail_or_teleport"],
     [("item", "minecraft:barrel", 8)],
     ["远路上要有补给：食物、工具、备用装备。",
      "有补给点，远征半径才真的能扩大。"]),

    ("post_network", "邮包分拣", "create:package_frogport", ["rail_or_teleport"],
     [("item", "create:package_frogport", 4)],
     ["远距离点对点送东西，不用人跑。",
      "配合铁路/传送，这是「人不动、货能动」的开始。"]),

    # ================= 批量与储备 =================
    ("bulk_stock", "大宗储备", "create:item_vault", ["local_warehouse"],
     [("item", "create:item_vault", 8)],
     ["常用建材要有安全库存，不要每次现凑。",
      "多人建造最怕「材料不够、全员停工」——储备就是解决它。"]),

    ("tool_bench", "工位与工具墙", "minecraft:barrel", ["local_warehouse"],
     [("item", "minecraft:barrel", 8), ("item", "minecraft:item_frame", 8)],
     ["每个工坊有自己的工具位与备用工具。找工具的时间也是成本。"]),

    ("fuel_reserve", "燃料储备", "minecraft:coal", ["charcoal_kiln"],
     [("item", "minecraft:coal", 128)],
     ["熔炉、蒸汽机、列车都吃燃料。储备见底时整座城会一起停摆。"]),

    ("food_reserve", "粮食储备", "minecraft:hay_block", ["farm_automation"],
     [("item", "minecraft:hay_block", 16)],
     ["闹饥荒的代价远大于囤粮占的地方。这一条是「能撑过意外」的底线。"]),
]


def task_line(t) -> str:
    if t[0] == "adv":
        return f'\t{{ type = "advancement", advancement = "{t[1]}" }},'
    if t[0] == "checkmark":
        return '\t{ type = "checkmark" },'
    return f'\t{{ type = "item", item = "{t[1]}", count = {t[2]} }},'


def main() -> int:
    text = TARGET.read_text(encoding="utf-8")
    existing = re.findall(r'^name = "(.*?)"', text, re.M)
    known = set(existing)

    # 新名字不能与已有重复
    new_names = [q[0] for q in NEW]
    dup = [n for n in new_names if n in known]
    if dup:
        print(f"[FAIL] 与已有任务重名：{dup}", file=sys.stderr)
        return 2
    dup2 = {n for n in new_names if new_names.count(n) > 1}
    if dup2:
        print(f"[FAIL] 新任务内部重名：{dup2}", file=sys.stderr)
        return 2

    allnames = known | set(new_names)
    for q in NEW:
        for d in q[3]:
            if d not in allnames:
                print(f"[FAIL] {q[0]} 的依赖 {d!r} 不存在", file=sys.stderr)
                return 2

    # 追加（保证文件以换行结尾，然后接新块）
    body = text.rstrip("\n") + "\n\n"
    body += "# ================= 追加：基础服务与材料产线（2026-09-22）=================\n"
    body += "# 编排思路：这一章原先是「国策里程碑」，缺的是把它们之间的日常设施补齐。\n"
    body += "# 新增六段：供水系统 / 照明与安全 / 生活设施 / 材料产线 / 交通延伸 / 批量与储备。\n"
    body += "# 与 100-cat-building、130-cat-storage 分工：那两章讲「有哪些 mod 能用」，\n"
    body += "# 这里讲「城里真要用起来的那几样」。\n\n"
    for (name, title, icon, deps, tasks, desc) in NEW:
        body += "[[quest]]\n"
        body += f'name = "{name}"\n'
        body += f'title = "{title}"\n'
        body += "desc = [\n"
        for d in desc:
            body += f'\t"{d}",\n'
        body += "]\n"
        body += f'icon = "{icon}"\n'
        body += 'shape = "circle"\n'
        body += "size = 1.2\n"
        if deps:
            body += "deps = [" + ", ".join(f'"{d}"' for d in deps) + "]\n"
        body += "tasks = [\n"
        for t in tasks:
            body += task_line(t) + "\n"
        body += "]\n\n"

    TARGET.write_text(body, encoding="utf-8", newline="\n")
    n_total = len(existing) + len(NEW)
    print(f"追加 {len(NEW)} 条 -> {TARGET.name} 现共 {n_total} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

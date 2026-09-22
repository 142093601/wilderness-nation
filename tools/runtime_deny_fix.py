#!/usr/bin/env python3
"""runtime_deny_fix.py -- 把任务书里用到「真机拒绝过的 id」的地方换成**经过核对的替代 id**。

它和 `runtime_deny.py` 的分工
---------------------------
  · `runtime_deny.py --scan`   ：从服务端日志**发现**坏 id（证据入库）
  · `runtime_deny.py --check`  ：任务书有没有用到它们（判定）
  · **本脚本**                 ：按 `REPLACEMENTS` 表**修**

为什么替换表要写死在这里（而不是自动挑一个相似的）
------------------------------------------------
自动挑相似 id 会挑出"看起来像但语义不对"的，甚至挑到另一个坏 id。
而历史上我吃过两次亏：
  1. 挑到语义不对的（把"墓碑"换成"墓碑装饰件"，玩家会困惑）
  2. **用裸 `str.replace` 做替换，把更长的好 id 也改坏**
     （`create:package` → `create:cardboard_package_12x12`，
      连带把 `create:packager` 变成 `create:cardboard_package_12x12r`）
所以：**表必须人工确认，替换必须按整词**（正则加 `(?<![\w:])` / `(?![\w:])` 边界）。

替换的挑选优先级
--------------
  1. 同命名空间里**任务书别处已经用过**的 id —— 真机必然接受（最稳）
  2. 同命名空间里注册表有、但没人用过的 id —— 只能靠下一次真机验证确认
  3. 退回原版 id —— 一定安全，代价是丢掉那个 mod 的味道

用法
----
    python tools/runtime_deny_fix.py            # 只报告要改什么
    python tools/runtime_deny_fix.py --write    # 落盘
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CHAPTERS = HERE / "questbook" / "chapters"

# 坏 id -> (替代 id, 依据)
REPLACEMENTS: dict[str, tuple[str, str]] = {
    # Create 本体：包裹的真实注册名带尺寸后缀
    "create:package": ("create:cardboard_package_12x12", "已接受：真名带尺寸"),
    "create:rare_package": ("create:cardboard_package_10x8", "无此物：用另一尺寸"),
    # Create: Dragons Plus：染料是流体，物品形态是桶
    "create_dragons_plus:white_dye": ("create_dragons_plus:white_dye_bucket", "已接受：桶形态"),
    "create_dragons_plus:dragon_breath": ("create_dragons_plus:dragon_breath_bucket", "流体"),
    "create_dragons_plus:dye_depot_amber_dye":
        ("create_dragons_plus:dye_depot_amber_dye_bucket", "流体"),
    "create_dragons_plus:dyenamics_wine_dye":
        ("create_dragons_plus:dyenamics_wine_dye_bucket", "流体"),
    "create_dragons_plus:arts_and_crafts_bleached_dye":
        ("create_dragons_plus:arts_and_crafts_bleached_dye_bucket", "流体"),
    "create_dragons_plus:fragile_fluid_tank": ("create:fluid_tank", "无此物：用本体储罐"),
    "create:track_coupler": ("create:track", "Create 6 无独立联结器"),
    # Slice & Dice
    "sliceanddice:fertilizer": ("sliceanddice:sprinkler", "已接受：施肥靠洒水器"),
    "sliceanddice:wet_air": ("sliceanddice:floor_sprinkler", "已接受：地面洒水器"),
    # Another Furniture
    "another_furniture:red_awning": ("another_furniture:red_sofa", "无 awning 物品"),
    "another_furniture:oak_small_shelf": ("another_furniture:oak_table", "无 small_shelf"),
    "another_furniture:oak_grandfather_clock": ("another_furniture:oak_drawer", "无落地钟"),
    "another_furniture:lamp_connector": ("another_furniture:white_lamp", "无连接件物品"),
    # Refurbished Furniture
    "refurbished_furniture:light_freezer": ("refurbished_furniture:light_fridge", "并入冰箱"),
    # Small Ships
    "smallships:oak_rowboat": ("smallships:oak_cog", "oak 无 rowboat"),
    "smallships:oak_war_galley": ("smallships:oak_galley", "oak 无 war_galley"),
    # Seaworthy Boats：整 mod 零注册项
    "seaworthyboats:shipyard": ("smallships:sail", "该 mod 零注册项"),
    # Corail Tombstone
    "tombstone:tombstone": ("tombstone:decorative_tombstone", "真名带 decorative"),
    "tombstone:engraved_grave_plate": ("tombstone:grave_plate", "无 engraved 变体"),
    "tombstone:grave_normal": ("tombstone:grave_plate", "无 grave_normal 物品"),
    "tombstone:grave_cross": ("tombstone:decorative_grave_cross", "真名带 decorative"),
    # Let's Parkour：真实的是 base/illusion/jelly/slippery/slow/soft/trapdoor
    "letsparkour:fast_parkour_slab": ("letsparkour:jelly_parkour_slab", "无 fast 变体"),
    "letsparkour:jump_parkour_slab": ("letsparkour:slippery_parkour_slab", "无 jump 变体"),
    # 单个
    "betterarcheology:identified_artifact":
        ("betterarcheology:unidentified_artifact", "已接受：未鉴定才是物品"),
    "exposure:selfie_stick": ("exposure:camera_stand", "无自拍杆物品"),
    "waystones:portstone": ("waystones:white_portstone", "石座是带颜色的"),
    "supplementaries:planter_rich": ("minecraft:flower_pot", "该 id 不存在"),
    "storagedelight:oak_pantry_cabinet":
        ("storagedelight:oak_single_door_cabinet", "无 pantry_cabinet"),
    "miners_delight:rock_soup_cup": ("miners_delight:strider_stew_cup", "无 rock_soup_cup"),
    "endersdelight:crushed_voidpepper": ("endersdelight:voidpepper", "无 crushed 变体"),
    "mynethersdelight:nether_stove": ("mynethersdelight:nether_bricks_stove", "真名带材质"),
    "mynethersdelight:stuffed_hoglin": ("mynethersdelight:roast_stuffed_hoglin", "真名带 roast"),

    # ================= 第二轮（2026-09-22 服务端第二次真机，31 条新坏 id）=================
    #
    # 这一轮暴露了一条重要事实：**上一轮按"注册表里有"挑的替代 id 有一半也是坏的**
    # （letsparkour / waystones:sharestone / enhancedcelestials / *_dye_bucket …）。
    # 所以下面一律只从 **tier 1**（任务书已用过且两次真机都没被拒 = 真机证明可用）
    # 或 **tier 3**（原版 minecraft:）里挑。
    # 挑选报告：tools/out/suggest2.txt（可复现：tools/_probe_suggest2.py）

    # --- Create 本体 ---
    "create:fake_track": ("create:track", "tier1：轨道类只认 track"),
    "create:lectern_controller": ("create:controls", "tier1：控制件"),
    "create:steam_whistle_extension": ("create:steam_whistle", "tier1：汽笛无 extension 变体"),

    # --- Create: Dragons Plus：只有 world 染色桶可信，其它联动的都不存在 ---
    "create_dragons_plus:arts_and_crafts_bleached_dye_bucket":
        ("create_dragons_plus:white_dye_bucket", "tier1：联动色系不存在"),
    "create_dragons_plus:dye_depot_amber_dye_bucket":
        ("create_dragons_plus:white_dye_bucket", "tier1：联动色系不存在"),
    "create_dragons_plus:dyenamics_wine_dye_bucket":
        ("create_dragons_plus:white_dye_bucket", "tier1：联动色系不存在"),

    # --- 铁路导航 ---
    "createrailwaysnavigator:navigator_lectern":
        ("createrailwaysnavigator:navigator", "tier1：导航仪无 lectern 变体"),

    # --- Dungeons and Taverns：钥匙不是物品（tier3 用原版钥匙类替代）---
    "dnt:citadel_key": ("minecraft:trial_key", "tier3：该 mod 的钥匙不可得"),
    "dnt:citadel_boss_key": ("minecraft:ominous_trial_key", "tier3：同上"),
    "dnt:nether_keep_key": ("minecraft:ancient_debris", "tier3：下界要塞的战利品代指"),

    # --- Enhanced Celestials：陨石/太空苔藓不是物品 ---
    "enhancedcelestials:meteor": ("minecraft:blackstone", "tier3：陨石残骸的实物代指"),
    "enhancedcelestials:space_moss_block": ("minecraft:moss_block", "tier3：苔藓的实物代指"),

    # --- Hundred Years Warfare ---
    "hundred_years_war:scroll_cavalry":
        ("hundred_years_war:scroll_horse", "tier1：骑兵卷轴的真名是 scroll_horse"),

    # --- Let's Parkour：连 *_parkour_slab 都不可得 → 退回原版方块 ---
    "letsparkour:jelly_parkour_slab": ("minecraft:slime_block", "tier3：果冻板≈黏液块"),
    "letsparkour:slippery_parkour_slab": ("minecraft:packed_ice", "tier3：滑板≈浮冰"),

    # --- Miner's Delight：杯类物品不可得 ---
    "miners_delight:strider_stew_cup": ("minecraft:rabbit_stew", "tier3：碗装汤的代指"),

    # --- 玩家追踪罗盘 ---
    "playertrackingcompass:tracking_compass":
        ("minecraft:recovery_compass", "tier3：找回罗盘（语义最近）"),

    # --- Supplementaries ---
    "supplementaries:book_pile": ("minecraft:chiseled_bookshelf", "tier3：书堆的代指"),
    "supplementaries:cartographers_quill": ("minecraft:feather", "tier3：制图羽毛笔的代指"),

    # --- TFMG ---
    "tfmg:block_mold": ("tfmg:steel_ingot", "tier1：铸模的产物"),
    "tfmg:ingot_mold": ("tfmg:lead_ingot", "tier1：铸模的产物（另一种锭）"),
    "tfmg:casting_spout": ("tfmg:casting_basin", "tier1：浇铸盆"),
    "tfmg:charcoal_dust": ("minecraft:charcoal", "tier3：木炭粉即木炭"),
    "tfmg:large_transformer": ("tfmg:transformer", "tier1：大型变压器即变压器"),
    "tfmg:liquid_plastic_bucket": ("tfmg:plastic_sheet", "tier1：塑料的固体形态"),
    "tfmg:pumpjack_hammer_holder":
        ("tfmg:pumpjack_hammer", "tier1：抽油机锤无 holder 变体"),

    # --- Waystones ---
    "waystones:fleeting_memorial": ("waystones:epitaph", "tier1：瞬忆碑即墓志铭"),
    "waystones:sharestone": ("waystones:red_sharestone", "tier1：共享石碑是带颜色的"),
    "waystones:warp_portal": ("waystones:warp_plate", "tier1：传送门用石盘承载"),
    "waystones:warp_scroll_bound": ("waystones:warp_scroll", "tier1：绑定卷轴即卷轴"),

    # --- Amendments ---
    "amendments:double_cake": ("minecraft:cake", "tier3：蛋糕的代指"),
}


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="把真机拒绝过的 id 替换成核对过的替代 id")
    ap.add_argument("--write", action="store_true", help="落盘（默认只报告）")
    args = ap.parse_args()

    total = 0
    for p in sorted(CHAPTERS.glob("*.toml")):
        text = p.read_text(encoding="utf-8")
        orig = text
        for bad, (good, why) in REPLACEMENTS.items():
            # ⚠️ **按整词替换**：`create:package` 是 `create:packager` 的前缀，
            # 裸 replace 会把好 id 改坏（2026-09-22 实测，25 处）。
            pat = re.compile(r"(?<![\w:])" + re.escape(bad) + r"(?![\w:])")
            n = len(pat.findall(text))
            if n:
                text = pat.sub(good, text)
                total += n
                print(f"  {p.name}: {bad} -> {good}  x{n}   （{why}）")
        if text != orig and args.write:
            p.write_text(text, encoding="utf-8", newline="\n")
    verb = "已替换" if args.write else "待替换（加 --write 落盘）"
    print(f"\n{verb} {total} 处")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

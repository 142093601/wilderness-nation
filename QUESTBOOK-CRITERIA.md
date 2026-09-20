# 判据速查表（写任务书时查这里，别凭记忆）

> 来源与结论见 `QUESTBOOK-FORMAT.md`。这份表的用途：**写 `[[quest.tasks]]` 时挑判据**。
> 每条都标了「怎么判」与「能表达什么行为」——`item` 判据的坑就在这里。

---

## 一、先记住一件事：`item` 判据检测的是「持有/消耗」，不是「放置」

| 你想表达 | 能不能用 `item` | 该用什么 |
|---|---|---|
| 玩家**拥有**某物 | ✅ | `item` |
| 玩家**交出/消耗**某物 | ✅（`consume_items: true`） | `item` + `consume_items` |
| 玩家**放下**某方块 | ❌ **做不到** | `stat`（放置/交互类）或 `advancement` |
| 玩家**做过**某行为 | ❌ | `stat` / `advancement` |
| 玩家**到过**某地 | ❌ | `location` / `biome` / `structure` / `dimension` |
| 玩家**击杀**某物 | ❌ | `kill` |
| 游戏里**真的没有信号** | —— | `checkmark`（且必须写进例外清单） |

**这是任务书最容易被做坏的地方**：把"放置营火"写成 `item: campfire`，
玩家就会"拿到营火就完成任务"，于是任务退化成物品清单 —— 正是用户否掉的那种任务。

---

## 二、`stat` 判据：原版统计（**84 条，javap 自 `net/minecraft/stats/Stats.class` 实测**）

用法：`{ type = "stat", stat = "minecraft:<id>", value = <N> }`

### 移动 / 探索（最能表达"走多远、探多深"）

| stat id | 含义 |
|---|---|
| `minecraft:walk_one_cm` | 步行距离（cm）—— **500 格 = 50000** |
| `minecraft:sprint_one_cm` | 疾跑距离 |
| `minecraft:swim_one_cm` | 游泳距离 |
| `minecraft:fly_one_cm` | 飞行距离 |
| `minecraft:aviate_one_cm` | 鞘翅飞行 |
| `minecraft:boat_one_cm` / `minecraft:minecart_one_cm` | 船 / 矿车里程 |
| `minecraft:horse_one_cm` / `minecraft:pig_one_cm` / `minecraft:strider_one_cm` | 骑乘里程 |
| `minecraft:climb_one_cm` / `minecraft:fall_one_cm` | 攀爬 / 坠落 |
| `minecraft:walk_on_water_one_cm` / `minecraft:walk_under_water_one_cm` | 水上 / 水下 |
| `minecraft:jump` / `minecraft:drop` | 跳跃次数 / 丢弃次数 |
| `minecraft:crouch_one_cm` / `minecraft:sneak_time` | 潜行 |

### 生产 / 制作（**表达"真的动手做了"**）

| stat id | 含义 |
|---|---|
| `minecraft:crafted` | 合成次数（按物品细分时用 `mined`/`used`） |
| `minecraft:used` | 使用物品次数 |
| `minecraft:broken` | 用坏工具次数 |
| `minecraft:picked_up` / `minecraft:dropped` | 拾取 / 丢弃 |
| `minecraft:enchant_item` | 附魔次数 |
| `minecraft:play_noteblock` / `minecraft:tune_noteblock` | 音符盒 |
| `minecraft:pot_flower` | 种花盆 |
| `minecraft:fill_cauldron` / `minecraft:use_cauldron` | 炼药锅 |
| `minecraft:clean_armor` / `minecraft:clean_banner` / `minecraft:clean_shulker_box` | 洗涤 |
| `minecraft:target_hit` | 击中标靶 |
| `minecraft:trigger_trapped_chest` | 触发陷阱箱 |

### 交互（**"用过某个方块/设施"** —— 比 item 判据贴近行为）

| stat id | 含义 |
|---|---|
| `minecraft:interact_with_crafting_table` | 用过工作台 |
| `minecraft:interact_with_furnace` | 用过熔炉 |
| `minecraft:interact_with_blast_furnace` | 用过高炉 |
| `minecraft:interact_with_smoker` | 用过烟熏炉 |
| `minecraft:interact_with_stonecutter` | 用过切石机 |
| `minecraft:interact_with_anvil` | 用过铁砧 |
| `minecraft:interact_with_grindstone` | 用过砂轮 |
| `minecraft:interact_with_loom` | 用过织布机 |
| `minecraft:interact_with_cartography_table` | 用过制图台 |
| `minecraft:interact_with_smithing_table` | 用过锻造台 |
| `minecraft:interact_with_lectern` | 用过讲台 |
| `minecraft:interact_with_brewingstand` | 用过酿造台 |
| `minecraft:interact_with_beacon` | 用过信标 |
| `minecraft:inspect_dropper` / `inspect_hopper` / `inspect_dispenser` | 查看漏斗/发射器 |
| `minecraft:open_chest` / `open_barrel` / `open_shulker_box` / `open_enderchest` | 开箱 |
| `minecraft:sleep_in_bed` | **睡过床** |
| `minecraft:bell_ring` | **敲钟**（设计里的"敲钟集结"就靠它） |
| `minecraft:raid_trigger` / `minecraft:raid_win` | 触发 / 赢得袭击 |
| `minecraft:talked_to_villager` / `minecraft:traded_with_villager` | 与村民交谈 / 交易 |

### 战斗 / 生产统计

| stat id | 含义 |
|---|---|
| `minecraft:mob_kills` | 击杀生物数 |
| `minecraft:player_kills` | 击杀玩家数 |
| `minecraft:deaths` | 死亡次数 |
| `minecraft:damage_dealt` / `damage_taken` | 造成 / 承受伤害 |
| `minecraft:damage_blocked_by_shield` | **盾牌挡下的伤害** |
| `minecraft:damage_absorbed` / `damage_resisted` | 吸收 / 抗性减免 |
| `minecraft:animals_bred` | 繁殖动物数 |
| `minecraft:fish_caught` | 钓鱼数 |

### 时间类

| stat id | 含义 |
|---|---|
| `minecraft:play_time` | 累计游玩（tick） |
| `minecraft:total_world_time` | 世界总时间 |
| `minecraft:leave_game` | 退出次数 |
| `minecraft:time_since_death` / `time_since_rest` | 距上次死亡 / 休息 |

---

## 三、`advancement` 判据：原版进度（1399 条，实测来自客户端 jar 的 `data/minecraft/advancement/`）

用法：`{ type = "advancement", advancement = "minecraft:<path>" }`

**原版进度本身就是"玩家做过的行为"**，所以是比 item 更贴切的判据。
常用（完整清单见 `tools/_probe_stats_adv.txt`）：

| advancement | 对应行为 |
|---|---|
| `minecraft:story/root` | 打开物品栏（第一次玩） |
| `minecraft:story/mine_stone` | 挖到石头 |
| `minecraft:story/upgrade_tools` | 石制工具 |
| `minecraft:story/smelt_iron` | 炼出铁锭 |
| `minecraft:story/iron_tools` | 铁制工具 |
| `minecraft:story/obtain_armor` | 穿上盔甲 |
| `minecraft:story/lava_bucket` | 岩浆桶 |
| `minecraft:story/mine_diamond` | 挖到钻石 |
| `minecraft:story/shiny_gear` | 钻石装备 |
| `minecraft:story/enchant_item` | 附魔 |
| `minecraft:story/enter_the_nether` | 进入下界 |
| `minecraft:story/follow_ender_eye` | 找到要塞 |
| `minecraft:story/enter_the_end` | 进入末地 |
| `minecraft:husbandry/plant_seed` | **种下种子** |
| `minecraft:husbandry/breed_an_animal` | **繁殖动物** |
| `minecraft:husbandry/tame_an_animal` | 驯服动物 |
| `minecraft:husbandry/root` | 吃过东西（农牧业根） |
| `minecraft:husbandry/balanced_diet` | 吃过所有食物 |
| `minecraft:husbandry/fishy_business` | 钓鱼 |
| `minecraft:adventure/sleep_in_bed` | 睡过床 |
| `minecraft:adventure/kill_a_mob` | 杀过怪 |
| `minecraft:adventure/kill_all_mobs` | 杀过所有怪 |
| `minecraft:adventure/shoot_arrow` | 射箭 |
| `minecraft:adventure/trade` | 与村民交易 |
| `minecraft:adventure/sniper_duel` | 50 格外击杀骷髅 |
| `minecraft:adventure/hero_of_the_village` | 守住村庄袭击 |
| `minecraft:adventure/voluntary_exile` | 触发袭击 |
| `minecraft:adventure/totem_of_undying` | 不死图腾 |
| `minecraft:nether/obtain_blaze_rod` | 烈焰棒 |
| `minecraft:nether/create_beacon` | 信标 |
| `minecraft:nether/summon_wither` | 召唤凋灵 |
| `minecraft:end/kill_dragon` | 杀死末影龙 |
| `minecraft:end/elytra` | 拿到鞘翅 |

⚠️ **注意**：进度是全服**每人各自**的（FTB Quests 按玩家判定）。
集体任务要用**团队共享**的判据（物资/建筑）或 `checkmark`，别用"某人拿到某个进度"代表全队完成。

---

## 四、其它判据

| type | 用法 | 什么时候用 |
|---|---|---|
| `dimension` | `{ type = "dimension", dimension = "minecraft:the_nether" }` | 到达某维度 |
| `biome` | `{ type = "biome", biome = "…" }` | 到达某群系 |
| `structure` | `{ type = "structure", structure = "…" }` | 到达某结构 |
| `kill` | `{ type = "kill", entity = "minecraft:zombie", value = 10 }` | 击杀计数 |
| `location` | `{ type = "location", … }` | 到达坐标范围 |
| `xp` | `{ type = "xp", value = 30 }` | 经验等级 |

---

## 五、`checkmark` 例外清单（**只能多不能乱**）

出现即必须在 `tools/questbook_check.py` 的 `CHECKMARK_EXCEPTIONS` 里写清理由。
当前（期 1）：

| 稳定名 | 任务 | 为什么没有可检测信号 |
|---|---|---|
| `how_to_open` | 这本书怎么翻 | 说明书本身 |
| `how_to_read_arrows` | 箭头是顺序 | 说明书本身 |
| `chronicle_and_craft` | 四栏各管什么 | 说明书本身 |
| `eras_and_key` | 时代与钥匙 | 说明书本身 |
| `where_to_ask` | 卡住了看哪里 | 说明书本身 |
| `prologue_marker` | 从这里开始 | 说明书本身 |
| `survive_first_night` | 活着过第一晚 | 游戏不记录"天亮时你在不在附近" |
| `pick_ground` | 设立营地 | **选址是集体决策**，没有单一可检测事件 |

（`PLAN-questbook.md` §4.2 原列 4 条：活着过第一晚 / 定下约定 / 地图墙覆盖核心区 / 终局碑的刻字。
实际写的时候由游戏事实决定——见上表的变化，理由都记在这里。）

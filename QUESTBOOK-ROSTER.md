# QUESTBOOK-ROSTER：40 章内容清单（单一真源）

> 这份文件回答一个问题：**每一章装什么、目标多少条、这些条从哪个 mod 的哪些实物来。**
> 结构决策的依据见 `PLAN-questbook.md` §二；判据规范见 `QUESTBOOK-CRITERIA.md`；
> TOML 写法见 `QUESTBOOK-FORMAT.md`。
>
> **进度标记**：`[ ]` 未做 · `[~]` 在做 · `[x]` 完成（条数写实际值）
> 统计口径一律用 `python tools/questbook.py` 打完的 `章 N 任务 M`，不凭印象。

---

## 为什么是这份清单（三条硬约束）

1. **每章中位 40~56 条**（实测：ATM10 56 / 天空蜂巢 66 / 龙之冒险 58）。
   修订前我们是 14 —— 那才是"内容不够"的真正原因，不是章数。
2. **形状是免费语义**（`PLAN-questbook.md` §2.5）。`circle` 是默认，别全章都是 `circle`。
3. **一个 mod 家族只有一个家**：类目章里的 mod 不许在技艺章再出现一次（反之亦然），
   否则玩家会在两处看到同一件事，且依赖关系会跨章打架（生成器本来就禁止跨章硬依赖）。

---

## 一、编年（chronicle）· 12 章

| 标记 | key | 章名 | 现在 | 目标 | 内容来源 |
|---|---|---|---|---|---|
| [x] | `10-prologue` | 序 | 6 | 14 | 怎么用这本书 / 六时代 / 四栏 / 钥匙机制 / 只读任务 |
| [ ] | `15-survival` | 生存第一课 | — | 24 | 木石工具链 · 食物来源 · 床与重生 · 第一夜 · 洞穴入门 |
| [ ] | `20-landing` | 落地 | 10 | 28 | 选点 · 水/食物/庇护 · 第一块田 · 第一间屋 · 点灯 |
| [ ] | `30-founding` | 立国 | 15 | 34 | 界碑 · 议事厅 · 公共仓库 · 分工 · 第一份国策 |
| [ ] | `35-charter` | 立约 | — | 22 | 一页约定 · 法典 · 议事流程 · 权限与队伍（FTB Teams / OPAC） |
| [ ] | `40-infra` | 基建 | 40 | 56 | 路 · 桥 · 照明 · 供水 · 能源起步 · 公共工程验收 |
| [ ] | `45-expedition` | 出关 | 16 | 34 | 远行准备 · 导航 · 补给点 · 前哨 · 带回什么 |
| [ ] | `55-defense-era` | 御敌 | 17 | 34 | 城墙 · 瞭望 · 巡逻 · 尸潮 · 伤亡与善后 · 达标线 |
| [ ] | `65-civic` | 公共工程 | — | 28 | 医院/食堂/学校/剧场/碑林/图书馆等"城市服务"层 |
| [ ] | `80-civilization` | 文明 | 49 | 62 | 文化 · 学术 · 艺术 · 纪念物 · 城市规模 |
| [ ] | `85-endless` | 无尽纪元 | 9 | 30 | 终局彩排 · 巨构 · 全自动 · 跨维度 · 史册 |
| [ ] | `88-legacy` | 碑林与史册 | — | 20 | 历史碑 · 编年史 · 名人堂 · 归档 · 交接给下一代 |

## 二、邦交（diplomacy）· 3 章

| 标记 | key | 章名 | 现在 | 目标 | 内容来源 |
|---|---|---|---|---|---|
| [ ] | `50-diplomacy` | 邦交·接触与情报 | 13 | 30 | Statecraft：情报站 · 侦察 · 邻国档案 · 使节 |
| [ ] | `52-treaty` | 邦交·外交与条约 | — | 24 | 国书 · 通商 · 同盟 · 贡赋 · 条约文本 |
| [ ] | `54-conquest` | 邦交·图纸与夺城 | — | 24 | 攻城器械 · 夺城 · 图纸（Portable Blueprints）· 战后治理 |

## 三、技艺（craft）· 8 章

| 标记 | key | 章名 | 现在 | 目标 | 内容来源 |
|---|---|---|---|---|---|
| [x] | `42-materials` | 材料与工业 | 35 | 48 | 木/石/金属/合金/玻璃/砖/混凝土 的**加工链** |
| [ ] | `60-create` | 机械动力 | 68 | 90 | Create 本体：动力 → 传动 → 加工 → 装配 |
| [ ] | `62-logistics` | 机械动力·物流 | — | 34 | 带/臂/漏斗/打包/仓储/列车调度（产线视角，非"放东西"） |
| [ ] | `64-create-decor` | 机械动力·建材 | — | 30 | Design n' Decor · Copycats · Framed · Slice&Dice · TFMG · Dragons Plus |
| [ ] | `75-steam` | 蒸汽与铁路 | 14 | 40 | Steam 'n' Rails · 列车 · 信号 · 站台 · Railways Navigator |
| [ ] | `90-blueprints` | 图纸与工程 | 11 | 30 | Portable Blueprints · Pattern Schematics · 工程图 · 施工组织 |
| [ ] | `95-claims` | 领地与队伍 | 12 | 28 | OPAC 领地 · FTB Teams · 权限 · 队伍协作 · 冲突处置 |
| [ ] | `70-defense` | 防御与尸潮 | 15 | 40 | The Hordes · Undead Nights · 工事 · 武器平台 · 应急预案 |

## 四、类目（category）· 17 章

| 标记 | key | 章名 | 现在 | 目标 | mod 家族 |
|---|---|---|---|---|---|
| [ ] | `100-cat-building` | 建筑构件 | 14 | 40 | Macaw's ×8 · Chipped · Rechiseled(+Create) · FramedBlocks · Copycats · Every Compat · Diagonal Walls · Athena · Fusion · Effortless Building |
| [ ] | `104-cat-decor` | 装饰与细节 | — | 36 | Supplementaries · Amendments · Handcrafted · Builders Additions · Chipped 变体 · 画作三件套（Macaw's/Medieval/Immersive） |
| [ ] | `110-cat-furniture` | 家具 | 11 | 36 | Macaw's Furniture · Another Furniture · MrCrayfish Refurbished |
| [ ] | `114-cat-comfort` | 生活与出行 | — | 30 | Comforts · Carry On · Travelers Backpack · Small Ships · Elevator · TrashSlot |
| [ ] | `120-cat-food` | 食物与烹饪 | 17 | 40 | Farmer's Delight + 7 个 Delight 扩展 · Cooking for Blockheads · Brewin' & Chewin' · Vinery · Farm & Charm |
| [ ] | `124-cat-farm` | 农牧与种植 | — | 34 | Farming for Blockheads · Smarter Farmers · RightClickHarvest · Ecologics · Naturalist · More Villagers · Chef's Delight · Spice of Life |
| [ ] | `130-cat-storage` | 仓储 | 13 | 38 | Storage Delight · Cupboard · Travelers Backpack 内胆 · Shulker Box Tooltip · Create 保险库 |
| [ ] | `134-cat-trade` | 贸易与工坊辅助 | — | 30 | Trading Post · FTB Filter System · Jade · JEI/JER · Polymorph · Crafting Tweaks · Almost Unified |
| [ ] | `140-cat-gear` | 装备与武器 | 15 | 38 | Simply Swords · Epic Knights · Magistuarmory · Armor of the Ages · Hundred Years Warfare · Easy Anvils |
| [ ] | `144-cat-utility` | 实用与魔法 | — | 28 | Enchanting Infuser · Enchantment Descriptions · Curios · Waystones · Explorer's/Nature's Compass · TorchMaster · Exposure |
| [ ] | `150-cat-explore` | 地图与导航 | 14 | 38 | Xaero's 三件套 · Traveler's Titles · Nature's/Explorer's Compass · Waystones · XaeroPlus |
| [ ] | `154-cat-structures` | 结构与遗迹 | — | 40 | YUNG's ×10 · Dungeons and Taverns · When Dungeons Arise · Towns and Towers · Repurposed Structures · Medieval Buildings ×3 · Grim Kingdoms · Additional Structures · Tidal Towns · Better Archeology · Lootr |
| [ ] | `160-cat-combat` | 战斗与军械 | 14 | 38 | Better Combat · Simply Swords · Epic Knights · 攻城器械 · 军事单位 |
| [ ] | `164-cat-horde` | 尸潮与夜袭 | — | 30 | The Hordes · Undead Nights · Enhanced Celestials · Easy Mob Spawn Control · It Takes a Pillage · TorchMaster |
| [ ] | `170-cat-perf` | 性能与优化 | 9 | 34 | Embeddium · Lithium · FerriteCore · ModernFix · EntityCulling · More Culling · BadOptimizations · Dynamic FPS · ImmediatelyFast · ScalableLux · Noisium · ServerCore · Packet Fixer · Chunky · Chunk Plan · Spark |
| [ ] | `174-cat-client` | 信息与界面 | — | 32 | Jade · BetterF3 · AppleSkin · Item Highlighter · Legendary Tooltips · Controlling · Mouse Tweaks · Chat Heads · Colorful Hearts · 3D Skin Layers · Better Advancements · Ping Wheel |
| [ ] | `178-cat-audio` | 音画与氛围 | — | 26 | AmbientSounds · Sound Physics · Medieval Music · Particle Rain · LambDynamicLights · Not Enough Animations · EMF · ETF · Athena/Fusion（连接材质） |

> ⚠️ **2026-09-22 修正**：`Corail Tombstone / GraveStone / Corpse` **不在 178 章**，
> 它们在 `114-cat-comfort`（死后与失物）。初版 ROSTER 把它们误划给"音画"，
> 撰写 114 章的 agent 据实提出后已改。

### 4.1 已知的"一个 mod 出现在两章"例外（**只此两条，写清边界**）

| mod | 章 A（做这一半） | 章 B（做那一半） |
|---|---|---|
| `travelersbackpack` | `130-cat-storage`：**内胆与分区**（装什么、怎么分类、回仓卸货） | `114-cat-comfort`：**背负与出行**（背包本体、睡袋、升级链） |
| `create` | `60-create` / `62-logistics`：**产线本体** | `130-cat-storage`：**仓储视角**（保险库容量、库存开关、按需取货） |

（例外必须写进这张表才算数 —— 不写就是重复内容。）

### 4.2 已知**无法覆盖**的 mod（查证过，不是漏做）

这些 mod 在 `tools/registry.json` 里 **items=0、blocks=0、advancements=0**，
没有任何可写进 `item` / `icon` 的 id。写它们只会得到"永远做不完的任务"，
所以**不覆盖**，并在文件头注释里说明：

| mod | namespace | 为什么没有判据 |
|---|---|---|
| Carry On | `carryon` | 纯交互（空手搬方块/实体），无物品 |
| TrashSlot | `trashslot` | HUD 槽位，无物品形态 |
| Corpse | `corpse` | 只在死亡时生成容器，无静态 id |
| Jade / JEI / JER / Controlling / Mouse Tweaks 等信息类 | 各自 | 无物品（但**其配置产出的物品**如 FTB Filter 可写） |

**判据替代做法**：用它们**导致**的游戏事实表达 —— 例："真的死过一次"用
`{ type = "stat", stat = "minecraft:deaths", value = 1 }`，而不是 `checkmark`。


---

## 合计

| 分组 | 章数 | 目标条数 |
|---|---|---|
| 编年 | 12 | 386 |
| 邦交 | 3 | 78 |
| 技艺 | 8 | 330 |
| 类目 | 17 | 576 |
| **合计** | **40** | **1370** |

对照（`tools/reference_questbook_style.py` 实测）：
ATM10 63 章 / 4528 条 · 天空蜂巢 35 章 / 2901 条 · 龙之冒险 14 章 / 1566 条。
本包 mod 数 231（ATM10 479），按 mod 数折算的合理量级 ≈ 2180 ——
**1370 是下限，做完这一轮之后按"哪个 mod 还没被覆盖"继续补**。

---

## 覆盖检查（每轮结束时跑）

```powershell
# 用哪些命名空间的 id 被写进过任务（覆盖率自查）
python tools/item_index.py --stats
```

**规则**：`--stats` 里条数 ≥ 8 的命名空间，必须至少有一条任务引用它。
否则那个 mod 实际上不存在于引导里 —— 玩家装了却不知道有它。
（例外：纯库 mod —— Architectury / Balm / Cloth Config / Kotlin / GeckoLib / Moonlight 等，
这些是别的 mod 的依赖，不该有任务。）

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

> 2026-09-22 调整：初版的 `35-charter`（立约）与 `65-civic`（公共工程）**已取消** ——
> 前者与「立国」的 `pact_on_paper` / `meeting_hall` 重叠，后者与「文明」章的
> 市政厅/医馆/消防站/剧场/学堂大面积重叠。改成两章**确实没人写过**的：
> `58-people`（人口与民生：居民本身）与 `68-treasury`（国库与营建：制度与预算）。

| 标记 | key | 章名 | 现在 | 目标 | 内容来源 |
|---|---|---|---|---|---|
| [x] | `10-prologue` | 序 | **13** | 14 | 怎么用这本书 / 形状语义 / 跨章指路 / 奖励政策 · **3 条跨章软链接** |
| [x] | `15-survival` | 生存第一课 | **26** | 24 | 木石工具链 · 食物 · 洞穴 · 床与过夜 |
| [x] | `20-landing` | 落地 | **48** | 28 | 选址评估 · 井与排污 · 工作棚 · 围栏 · 第一批粮食 · 落成仪式 |
| [x] | `30-founding` | 立国 | **49** | 34 | 国号与旗 · 四至界碑 · 权限分层 · 议事规则 · 岗位表 · 五年计划 |
| [x] | `40-infra` | 基建 | **70** | 56 | 交通分级 · 水利 · 全城配电 · 公共设施接入 · 应急 · 施工组织 |
| [x] | `45-expedition` | 出关 | **75** | 34 | 远征编制 · 补给点网络 · 导航记录 · 交通工具 · 地形专项 · 前哨 |
| [x] | `55-defense-era` | 御敌 | **51** | 34 | 民兵编制 · 值守轮班 · 预警分级 · 战备储备 · 伤亡善后 · 复盘 |
| [x] | `58-people` | 人口与民生 | **30** | 28 | 床位 · **原版 15 种职业工作站** · 繁衍 · 治安 · 公共事务 |
| [x] | `68-treasury` | 国库与营建 | **20** | 20 | 硬通货 · 金库 · 账本 · 预算 · 结算 · 审计 · 铸块 |
| [x] | `80-civilization` | 文明 | **71** | 62 | 公共工程 · 历法与度量衡 · 节庆 · 教育 · 艺术 · 城市规划 · 卫生 |
| [x] | `85-endless` | 无尽纪元 | **40** | 30 | 终局彩排 · 巨构 · 全自动闭环 · 跨维度 · 世界改造 · 完备与极限 |
| [x] | `88-legacy` | 碑林与史册 | **21** | 20 | 碑 · 册 · 像 · 舆图 · 交接册 |

## 二、邦交（diplomacy）· 3 章

| 标记 | key | 章名 | 现在 | 目标 | 内容来源 |
|---|---|---|---|---|---|
| [x] | `50-diplomacy` | 邦交·接触与情报 | **30** | 30 | 情报站升级 · 边境侦察 · 邻国档案 · 使节 · 暗桩 · 情报网 · 反情报 |
| [x] | `52-treaty` | 邦交·外交与条约 | **25** | 24 | 国书 · 印信 · 定界 · 关税 · 通商 · 同盟 · 烽火预警 |
| [x] | `54-conquest` | 邦交·夺城与属地 | **29** | 24 | 攻城器械（siegemachines 10 件）· 侦察 · 夺城 · 驻军 · 属地治理 |

## 三、技艺（craft）· 8 章

| 标记 | key | 章名 | 现在 | 目标 | 内容来源 |
|---|---|---|---|---|---|
| [x] | `42-materials` | 材料与工业 | **63** | 48 | 林石 · 冶炼 · 建材加工 · **重工业 TFMG** · 库存规格 |
| [x] | `60-create` | 机械动力 | **93** | 90 | 动力 → 传动 → 加工 → **装配线** → 移动机器 → 控制与显示 → 蓝图炮 |
| [x] | `62-logistics` | 机械动力·物流 | **43** | 34 | 带路分流 · 机械臂规则 · 包裹地址 · 库存请求 · 节拍与吞吐 |
| [x] | `64-create-decor` | 机械动力·建材 | **42** | 30 | Design n' Decor · Framed · Slice&Dice · Dragons Plus · Hypertubes |
| [x] | `75-steam` | 蒸汽与铁路 | **95** | 40 | Steam 'n' Rails：轨距/道岔/信号/车厢/车钩/月台/导航仪/装卸 |
| [x] | `90-blueprints` | 图纸与工程 | **33** | 30 | Portable Blueprints 6/6 · Pattern Schematics 3/3 · 施工组织 |
| [x] | `95-claims` | 领地与队伍 | **49** | 28 | OPAC · FTB Teams · **Waystones 传送网络（33 个 id）** · 冲突处置 |
| [x] | `70-defense` | 防御与尸潮 | **57** | 40 | The Hordes · Undead Nights · TorchMaster · 工事 · 自动化防御 · 预案 |

## 四、类目（category）· 17 章

| 标记 | key | 章名 | 现在 | 目标 | mod 家族 |
|---|---|---|---|---|---|
| [x] | `100-cat-building` | 建筑构件 | **56** | 40 | Macaw's ×6 · Chipped · Rechiseled(+Create) · FramedBlocks · Copycats · Every Compat · Effortless Building |
| [x] | `104-cat-decor` | 装饰与细节 | **40** | 36 | Supplementaries · Amendments · Handcrafted · Builders Additions · 画作三件套 |
| [x] | `110-cat-furniture` | 家具 | **46** | 36 | Macaw's Furniture · Another Furniture · MrCrayfish Refurbished · Handcrafted |
| [x] | `114-cat-comfort` | 生活与出行 | **37** | 30 | Comforts · Travelers Backpack · Small Ships · Elevator · Tombstone/GraveStone · 跑酷 |
| [x] | `120-cat-food` | 食物与烹饪 | **51** | 40 | Farmer's Delight + 7 个 Delight 扩展 · Cooking for Blockheads · Brewin' & Chewin' · Vinery |
| [x] | `124-cat-farm` | 农牧与种植 | **36** | 34 | Farming for Blockheads · Ecologics · Naturalist · More Villagers · Spice of Life |
| [x] | `130-cat-storage` | 仓储 | **45** | 38 | Storage Delight · Cupboard · 背包内胆 · Create 保险库 · 库存开关 |
| [x] | `134-cat-trade` | 贸易与工坊辅助 | **37** | 30 | Trading Post · FTB Filter · JEI/JER · Polymorph · Crafting Tweaks · 分类提示 |
| [x] | `140-cat-gear` | 装备与武器 | **52** | 38 | Simply Swords · Magistuarmory · Armor of the Ages · Enchanting Infuser · 饰品 |
| [x] | `144-cat-utility` | 实用与奇物 | **37** | 28 | Waystones · 两种罗盘 · Exposure 摄影 · Better Archeology · Friends&Foes |
| [x] | `150-cat-explore` | 地图与导航 | **43** | 38 | Xaero's 三件套 · Traveler's Titles · 罗盘 · Waystones 传送网络（22 条） |
| [x] | `154-cat-structures` | 结构与遗迹 | **47** | 40 | YUNG's ×8 · Dungeons and Taverns · When Dungeons Arise · Towns and Towers · Medieval Buildings ×3 · Grim Kingdoms · Tidal Towns · Better Archeology · Lootr · Terralith |
| [x] | `160-cat-warfare` | 战斗与军械 | **46** | 38 | Hundred Years Warfare · Magistuarmory · It Takes a Pillage · 战场后勤 · 反制 |
| [x] | `164-cat-horde` | 尸潮与夜袭 | **31** | 30 | Enhanced Celestials（血月/超级月亮/丰收月）· Easy Mob Spawn Control · The Hordes **天数升档表** |
| [x] | `170-cat-perf` | 性能与优化 | **37** | 34 | Embeddium · Lithium · FerriteCore · ModernFix · EntityCulling · Chunky · Spark … |
| [x] | `174-cat-client` | 信息与界面 | **34** | 32 | Jade · BetterF3 · AppleSkin · Item Highlighter · Controlling · Chat Heads … |
| [x] | `178-cat-audio` | 音画与氛围 | **31** | 26 | AmbientSounds · Sound Physics · Medieval Music · Particle Rain · EMF · ETF · Fusion |
| [ ] | `170-cat-perf` | 性能与优化 | 9 | 34 | Embeddium · Lithium · FerriteCore · ModernFix · EntityCulling · More Culling · BadOptimizations · Dynamic FPS · ImmediatelyFast · ScalableLux · Noisium · ServerCore · Packet Fixer · Chunky · Chunk Plan · Spark |
| [ ] | `174-cat-client` | 信息与界面 | — | 32 | Jade · BetterF3 · AppleSkin · Item Highlighter · Legendary Tooltips · Controlling · Mouse Tweaks · Chat Heads · Colorful Hearts · 3D Skin Layers · Better Advancements · Ping Wheel |
| [ ] | `178-cat-audio` | 音画与氛围 | — | 26 | AmbientSounds · Sound Physics · Medieval Music · Particle Rain · LambDynamicLights · Not Enough Animations · EMF · ETF · Athena/Fusion（连接材质） |

> ⚠️ **2026-09-22 修正**：`Corail Tombstone / GraveStone / Corpse` **不在 178 章**，
> 它们在 `114-cat-comfort`（死后与失物）。初版 ROSTER 把它们误划给"音画"，
> 撰写 114 章的 agent 据实提出后已改。

### 4.1 已知的"一个 mod 出现在多章"例外（**只此七条，写清边界**）

| mod | 章 A（做这一半） | 章 B（做那一半） |
|---|---|---|
| `travelersbackpack` | `130-cat-storage`：**内胆与分区**（装什么、怎么分类、回仓卸货） | `114-cat-comfort`：**背负与出行**（背包本体、睡袋、升级链） |
| `create` | `60-create` / `62-logistics`：**产线本体与吞吐** | `130-cat-storage`：**仓储视角**（保险库容量、库存开关、按需取货） |
| `waystones` | `150-cat-explore`：**看路与认路**（9 种材质石碑 · 16 色石座 · 15 色共享石碑 · 卷轴） | `95-claims`：**权限与队伍**（谁能用、共享给谁）· `144-cat-utility`：**奇物层**（石盘 / 共鸣碎片 / 双生羽毛 / 墓志铭） |
| `betterarcheology` | `154-cat-structures`：**遗迹里的考古现场**（可疑沙土 / 战利品罐 / 化石） | `144-cat-utility`：**在城里干这件事**（考古台 / 刷子 / 图腾 / 神器鉴定） |
| `lootr` | `130-cat-storage`：**容器视角**（每个玩家独立战利品箱 / 桶 / 潜影盒 / 奖杯） | `154-cat-structures`：**遗迹视角**（挖开古罐 / 考古学家进度 / 陷阱箱） |
| `tfmg`（The Factory Must Grow） | `42-materials`：**加工链**（矿 → 锭 → 构件 → 化工品，84 处引用） | `64-create-decor`：**建材与装饰表皮**；`40-infra` 只借用沥青/配电网实物当验收物 |
| `mcwpaths` / `mcwbridges`（Macaw's） | `100-cat-building`：**有哪些构件可选** | `40-infra`：**哪一处要建、建成什么样**（路/桥的达标验收，共 14 处引用） |

> 三条 2026-09-22 补登（撰写 agent 据实提出）：Waystones / Better Archeology / Lootr。
> **两章引用同一个 mod 时，用的条目必须零重复**（这三组都已核对无重复）。
>
> **例外必须写进这张表才算数** —— 不写就是重复内容。
> 2026-09-22 另发现**一个 mod 跨 3 章**先例（Waystones）；上限就是 3，
> 再多就说明分类标准出了问题，应该回头改章而不是继续加例外。

### 4.1.0 另外三处**同信号不同用法**（不算重复，但要记下来）

| 信号 | 章 A | 章 B | 为什么不算重复 |
|---|---|---|---|
| `torchmaster:megatorch` / `dreadlamp` | `70-defense`：**基地照明与驱怪系统**（含 radius 配置） | `45-expedition`：**出关随身带的照明器材** | 一个是"装在家里"，一个是"带出门"；同一个物品的两种用法 |
| `undeadnights` 击杀计数 | `70-defense`：30/10/5（守住一波的基础线） | `164-cat-horde`：60/20/10（终局的加压线） | `kill` 是**累计**统计，两章是同一计数器上的不同阈值 —— 会一起涨，但不会"两个任务同一条件" |
| `enhancedcelestials` 天象 | `164-cat-horde`（唯一归属，实测判据 `meteor` / `space_moss_block`） | — | `70-defense` 原本也写了两条，发现与 164 逐字重合后**主动撤掉**并写进文件头 |

**纪律**：`kill` / `stat` 是**累计量**，可以被多章以不同阈值使用；
但 `item`（持有）与 `advancement`（一次性事件）**一旦重复就是真重复** —— 两个任务会同时亮。
这条区别要记住，它决定了"哪些信号可以复用"。

### 4.1.1 2026-09-22 并行撰写撞车记录（**给后面的人看的**）

一次派了十几个 agent 分头写 40 章，出现三类撞车。处置写在这里，
因为**它们的根因都是"我给的边界不完整"，不是 agent 的错**：

| # | 撞车 | 根因 | 处置 |
|---|---|---|---|
| 1 | `70-defense` 与 `164-cat-horde` 都写 TorchMaster | **ROSTER 自己把 TorchMaster 同时列进了 144 与 164 两行** | TorchMaster **归 70-defense**（照明是防御系统的一部分）；164 改写真没人写的 Enhanced Celestials / Easy Mob Spawn Control / Hordes 天数升档表 |
| 2 | `70-defense` 与 `160-cat-warfare` 都用 `takesapillage` 的两条 advancement | 70 是新写的，没看到 160 已用 | 那两条进度**归 160**（它先写，且掠夺者阵营本属战斗章）；70 改用实物与 `kill` 判据 |
| 3 | `55-defense-era` 与 `164-cat-horde` 都想讲"尸潮 mod 是什么" | 55 的文件头写明"mod 家族介绍让给 164"，但 55 又写了配置数字与实体表 | 55 保留**国策层**（编制/预警分级/善后），164 保留**家族层**；两边边界写进各自文件头 |

**纪律（新增）**：派活时**必须先检查 ROSTER 里同一个 mod 有没有出现在两行**，
并给每个 mod 指定**唯一归属章**；例外只能出现在 §4.1 那张表里。
跨章引用同一条 advancement / 同一个实体，等于两个任务会同时亮 —— 那不是"覆盖更全"，是重复。

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
python tools/item_index.py --coverage
```

它做的事：把 `registry.json` 的命名空间与「TOML 里真的被引用过的命名空间」求差，
列出**条数 ≥ 8、却在引导里一次都没出现**的那些 —— 也就是"玩家装了却不知道有它"的 mod。
`exit 1` 表示有缺口（脚本会写 `tools/out/coverage_gaps.txt`）。

**2026-09-22 首次运行**：命名空间 100 个，被引用 73 个，缺口 9 个
（`railways` 1951 · `tfmg` 656 · `dnt` 26 · `create_bb` 31 · `create_train_parts` 16 …
当时这些章正在被撰写，属于"还没写"而不是"写不到"）。

**例外的判定**：纯库 mod（Architectury / Balm / Cloth Config / Kotlin / GeckoLib /
Moonlight …）与**纯客户端界面 mod**（Jade / JEI / Controlling …）本来就没有物品，
它们已在脚本的 `libs` 白名单里 —— **白名单只放"没有注册物品"的**，
不放"我懒得写"的。往白名单里加东西要说明理由。

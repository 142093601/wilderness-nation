# 荒野建国 · mod 清单（权威版）

> **这份文件回答一个问题：这个包到底装哪些 mod。** 每个功能位一行，没有"以后再想"。
>
> **挑选纪律（很重要）**：
> 1. **清单必须同时覆盖 Modrinth 与 CurseForge** —— 只扫一个平台就是**系统性偏差**。
>    这曾导致两个后果：任务书错选 Questlog（FTB 在 CF，没进过候选池）、以及一批常见 mod 整批漏掉。
> 2. 候选来源有两条，都要用：① Modrinth 接口筛选（`tools/survey_mods.py`）；
>    ② **本机成熟包的 `mods/` 共识**（`tools/reference_pack_diff.py`），专门补 CF 侧。
> 3. 挑不到合适候选的功能位，一律标 ⏳**并写明缺什么**，绝不凭印象塞一个进去。
>
> 状态：✅ 已装已验 · 📌 已定待装 · ⏳ 待核实后才定 · ❌ 不装（含理由）
> 端侧：双 = 客户端+服务端都要 · 客 = 仅客户端 · 服 = 仅服务端

---

## 一、引擎与基础（13 + 6）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| 加载器 | NeoForge 21.1.250 + Java 21 | — | ✅ | 1.21.1 锁死小版本（多个主力 mod 只覆盖到它） |
| 渲染优化 | Embeddium | 客 | ✅ | |
| 服务端逻辑 | Lithium | 服 | ✅ | |
| 内存 | FerriteCore | 双 | ✅ | |
| 加载/修复 | ModernFix | 双 | ✅ | |
| 实体剔除 | EntityCulling | 客 | ✅ | |
| 界面即时渲染 | ImmediatelyFast | 客 | ✅ | |
| 经验球合并 | Clumps | 双 | ✅ | |
| 服务端通用 | ServerCore | 服 | ✅ | |
| 崩溃守护 | Neruina | 服 | ✅ | 单个坏实体不再拖垮全服 |
| 诊断 | spark | 双 | ✅ | |
| 预生成 | Chunky | 双 | ✅ | 世界边界内预生成，配合 RoadWeaver |
| 区块剔除 | MoreCulling | 客 | ✅ | |
| 光照 | ScalableLux | 双 | ✅ | |
| 区块并发 | ~~C2ME~~ | — | ❌ | 覆盖 ModernFix 四项优化、mixin 静态绑定违规、与 RoadWeaver 不兼容（证据在 `baseline.csv`） |
| **配方查询** | **JEI** | 双 | 📌 | 用户已定；EMI 职能重叠不装 |
| 方块/实体信息 | Jade | 双 | 📌 | 联机友好（"我在看什么"） |
| 崩溃可读提示 | Crash Assistant | 客 | 📌 | 朋友服实用：崩溃时给"怎么办"而不是堆栈 |
| 数据包自动加载 | Paxi | 双 | 📌 | 打包分发用：把 datapack/resourcepack 丢进文件夹即生效 |
| 红石性能 | Alternate Current | 服 | ⏳ | 装前要跑基线对比（与 ModernFix 可能重叠） |

## 二、世界生成（12）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| **群系与地形** | **Terralith** | 双（**客户端可选**） | 📌 | 用户已定。理由：RoadWeaver 正文**点名兼容**、只用原版方块、不必人人装 |
| 世界生成兼容库 | Lithostitched | 服 | 📌 | Terralith 2.6+ 的必需前置；提升多世界生成 mod 的兼容性 |
| **世界路网** | **RoadWeaver** | 双 | 📌 | "世界本来就有文明"的氛围；**道路不能在已加载区块生成** → 必须先预生成 |
| 结构（地牢） | YUNG's Better Dungeons | 服 | ✅ | |
| 结构（下界要塞） | YUNG's Better Nether Fortresses | 服 | 📌 | 同 API（yungs-api 已装）、同设计语言 → 低冲突扩容 |
| 结构（海底神殿） | YUNG's Better Ocean Monuments | 服 | 📌 | 同上 |
| 结构（废弃矿井） | YUNG's Better Mineshafts | 服 | 📌 | 同上 |
| 结构（丛林神庙） | YUNG's Better Jungle Temples | 服 | 📌 | 同上 |
| 结构（末地） | YUNG's Better End Island | 服 | 📌 | 同上 |
| 结构（要塞） | YUNG's Better Strongholds | 服 | 📌 | 同上 |
| 结构（大型地牢） | When Dungeons Arise | 服 | 📌 | 独立大型结构，补充"占领区"据点素材 |
| 结构（村镇） | Dungeons and Taverns | 服 | 📌 | 村庄/城镇变体 → 旧世痕迹 |
| **远征维度** | Dimensional Dungeons | 双 | ⏳ | **形态未核实**（开放区域 or 一次性副本）→ 决定时代 3 目标怎么写 |

## 三、领地与治理（4 + 1）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| **领地/队伍/权限** | Open Parties and Claims | 双 | ✅ | 核心：队伍共有领地（`partyOwnedClaims`）对应"国家领土" |
| 方块改动记录 | GriefLogger | 服 | 📌 | 多人服底线 |
| 回滚命令 | GLRA | 服 | 📌 | 误破坏可恢复；权限只收 1~2 人 |
| 备份 | Simple Backups | 服 | 📌 | **必须定期验证备份真的能恢复** |
| **约定/国史载体** | Patchouli | 双 | 📌 | 游戏内成书框架 → 填 `DESIGN.md` §17.7 空缺 #3（一页约定进游戏） |

## 四、建造与自动化（11）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| **自动化核心** | Create 6 | 双 | ✅ | 含机械/物流/**列车**（已核实：549 类） |
| 图纸打印 | Create 的 Schematicannon | 双 | ✅ | 已验证认得生成器产出的 `.nbt` |
| 工程制/服务端分发 | Portable Blueprints | 双 | 📌 | 图纸分发 + 材料代扣 → "工程制"的现成机制 |
| 建筑辅助 | Effortless Building | 双 | 📌 | 批量放置/镜像/阵列（替代被禁的 Litematica 自动放置） |
| 管理工具 | WorldEdit | 服 | 📌 | op 专用：整平、地形改造、事故修复 |
| 建材变体 | Chipped | 双 | 📌 | 同材质多形态 → 配合图纸生成器出更多风格，服务"统一配色规范" |
| 建材变体（凿刻） | Rechiseled | 双 | 📌 | 同上 |
| 建材变体（Create） | Rechiseled: Create | 双 | 📌 | 让 Create 的方块也能凿 |
| 装饰与功能方块 | Supplementaries | 双 | 📌 | 大量装饰/功能方块，与建造类包契合度高 |
| 家具 | Handcrafted | 双 | 📌 | |
| 家具 | Another Furniture | 双 | 📌 | |

## 五、交通（2 + 自带）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| **铁路** | **Create 6 自带列车** | 双 | ✅ | 已核实：轨道/信号/车站/时刻表 → **不需要额外铁路 mod** |
| **道路** | **RoadWeaver** | 双 | 📌 | 见"世界生成" |
| **传送节点** | Waystones | 双 | 📌 | 交通等级的最高一档；需定"何时解锁"（建议时代 5 或稀有材料门槛） |
| 载具（远征风味） | Create: Aeronautics | 双 | 📌 | 同生态；让远征有"载具"形态 |

## 六、威胁（2 + 1❌）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| **袭击排期引擎** | The Hordes | 双 | 📌 | `/hordes start|spawnWave|stop`；`hordesCommandOnly` 可关自然刷怪，完全由日历+国力触发 |
| **拆墙与难度档位** | Undead Nights | 双 | 📌 | 唯一有显式拆方块子系统（`blockBreakingTier` 1~4）；由我们的日历接管难度等级 |
| 怪物 AI 增强 | ~~Zombie Awareness~~ | 双 | ❌ | **第一版不装**：没有时代闸门（常驻 AI 改动，会破坏"威胁从时代 4 开始"的节奏）· 与上面两个都改怪物行为、三方兼容未验 · 更新停在 2024-09。→ 列为时代 4 的**验证候选** |
| 村庄自保 | Guard Villagers | 双 | ⏳ | 让世界"本来有文明"；但要验是否变成"NPC 替你打"（与反目标冲突） |

## 七、远征与冒险（10）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| 群系定位 | Nature's Compass | 双 | 📌 | 服务"选址"与"远征规划" |
| 结构定位 | Explorer's Compass | 双 | 📌 | 同上 |
| 小地图 | Xaero's Minimap | 客 | 📌 | |
| 世界地图 | Xaero's World Map | 客 | 📌 | 情报墙/地图墙的实现手段（服务端共享方式需核实） |
| 背包 | Traveler's Backpack | 双 | 📌 | 远征后勤 |
| 饰品槽 API | Curios API | 双 | 📌 | 前置（多个装备 mod 依赖） |
| 考古 | Better Archeology | 双 | 📌 | 挖遗迹得文物 → **与"旧世"设定天然契合**，给"残破图纸"以外的战利品维度 |
| 远征休憩 | Comforts | 双 | 📌 | 睡袋/吊床 |
| 武器 | Simply Swords | 双 | 📌 | |
| 附魔/铁砧便利 | Easy Anvils + Enchanting Infuser | 双 | 📌 | 减少"附魔台抽卡"的挫败 |

## 八、任务书（FTB 系列，6）

> **为什么不是 Questlog**：最初写 Questlog **不是因为它更好，而是因为它"能被核实"**——
> FTB 系列只在 CurseForge 分发，而候选池是从 **Modrinth 接口**拉的，它根本没进过候选池。
> 补上证据后（见下方实证文件名），改用 FTB Quests：真任务树、章节、依赖、奖励、**团队进度共享**，
> 而 Questlog 只是 checklist 式，撑不起"六章国策链"。

| 功能位 | 选定 | 端侧 | 来源 | 状态 | 说明 |
|---|---|---|---|---|---|
| **任务书载体** | **FTB Quests**（CF slug `ftb-quests-forge`） | 双 | CF | 📌 | 章节 + 任务树 + 依赖 + 奖励；`config/ftbquests` 是它的数据目录 |
| 任务书依赖·团队 | FTB Teams（CF slug `ftb-teams-forge`） | 双 | CF | 📌 | ⚠️ **需设成"全员同队"**（你们本来就是一个国家）→ 任务进度才能共享；具体配置项装好后实测 |
| 任务书前置 | FTB Library（CF slug `ftb-library-forge`） | 双 | CF | 📌 | 必需 |
| FTB 跨 mod 兼容 | FTB XMod Compat（`ftb-xmod-compat`） | 双 | CF | 📌 | 让 FTB 组件认识其他 mod 的方块/物品 |
| JEI 扩展 | FTB JEI Extras（`ftb-jei-extras`） | 双 | CF | 📌 | 配我们选的 JEI |
| 物品过滤 | FTB Filter System（`ftb-filter-system`） | 双 | CF | 📌 | 小工具，配合自动化 |
| ~~任务书备选~~ | ~~Questlog~~ | — | MR | ❌ | 降级为**备选**：若 FTB Quests 出问题再回退 |

> ⚠️ **前三个的 slug 必须是带 `-forge` 的 CF 形式**（2026-09-18 实测）：`ftb-quests` / `ftb-teams` / `ftb-library`
> 在 CF 接口里**查无**，写错的话 `packwiz curseforge add` 会直接失败——而这正是任务书的三件核心。
> 后三个（`ftb-xmod-compat` / `ftb-jei-extras` / `ftb-filter-system`）本身就是 CF 的真实 slug。
> 已按 slug 逐个解析到 CF 项目 id，证据见 `tools/lists/cf-verified.tsv`。

**CF 类的核验证据**（两重：CF 接口按 slug 解析出的 1.21.1+NeoForge 文件 + 本机参照包的 jar 文件名）：

```
CF 接口（2026-09-18 复核）：
  [NEOFORGE][1.21.1] FTB Quests 2101.1.36      （id 289412）
  [NEOFORGE][1.21.1] FTB Teams 2101.1.11       （id 404468）
  [NEOFORGE][1.21.1] FTB Library 2101.1.36     （id 404465）
  [NEOFORGE][1.21.1] FTB XMod Compat 21.1.12   · FTB JEI Extras 21.1.7 · FTB Filter System 21.1.4

本机参照包 jar 文件名（ATM10 与 Skyhive）：
ftb-quests-neoforge-2101.1.24.jar     ftb-teams-neoforge-2101.1.10.jar
ftb-library-neoforge-2101.1.31.jar    ftb-xmod-compat-neoforge-21.1.8.jar
ftb-jei-extras-21.1.7.jar             ftb-filter-system-neoforge-21.1.4.jar
```

（`2101.x` = NeoForge **21.1** 分支。来源：All the Mods 10 与 Skyhive，两个都是 1.21.1 NeoForge 的成熟包。）

> ⚠️ **这条早先写错了，已更正**：我以为 `packwiz curseforge add` 需要申请 CurseForge API key。
> **实测它内置了 CF 访问，不需要 key**：`packwiz curseforge add ftb-quests-forge -y` 直接成功，
> 连依赖都自动解析了（Architectury → FTB Library → FTB Teams → FTB Quests），
> 元数据写成 `mode = "metadata:curseforge"`（**不内嵌 jar**，由安装器从 CF 取——许可上最稳）。
>
> 两个要注意的：① **CF 的 slug 与 Modrinth 不同**（CF 上叫 `ftb-quests-forge`，Modrinth 上根本没有）；
> ② 这边访问 CF API **偶发 TLS 握手超时**（实测 `ftb-chunks-forge` 就超时了一次）→ 要能重试。

## 八·补、军事 / 带兵打仗（2026-09-18 定）

> **设计上为什么现在才有**：反目标 #3 原写作"不搞 NPC 替你打"，所以设计稿里一直没有军队。
> 需求方明确要"带兵打仗"后，把它按 **B 路线（玩家城邦）** 的边界重新划了一遍，规格见 `DESIGN.md` §17.9。

**边界（这条决定包的性质，不能含糊）**：

| 环节 | 交给 NPC？ | 理由 |
|---|---|---|
| 建造（盖房/修墙/铺路/公共工程） | ❌ **不行** | 这正是 B 路线的定义"每一块砖都是玩家自己放的" |
| 生产（资源/运输/合成/存储） | ⚠️ **卡时代** | 重复劳动可外包，但不能跳过 Create 的学习曲线 |
| 军事（守城/野战/攻城） | ✅ **可指挥** | 但必须靠国力供养，关键决策由玩家做 |

**主选：`hundred-years-warfare`（百年战争）** —— 以下全部为本次接口核验：

| 项 | 实测结果 |
|---|---|
| 1.21.1 + NeoForge 版本 | **23 个**（对比：AW3 的 NPC 模块只有 3 个） |
| 端侧 | 双端必需（client=required / server=required） |
| 指挥方式 | **指挥轮盘 · 指挥杖 · RTS 模式**——前两种第一人称即可用，**所以砍掉俯视角不影响它** |
| 兵种 | 弓箭抛射 · 骑兵冲锋 · 攻城器械，且**兵种相克** |
| 其他 | 招募系统 · NPC 势力与据点 · 工人/工作台（源自 Ancient Warfare 2）· 关系/团队系统 |
| 兼容 | 与 **`better-combat`（我们已在计划里）** · Epic Fight · TaCZ · Iron's Spells · Freecam 有专门兼容 |
| 性能 | 作者推荐的优化组合（Embeddium / EntityCulling / ImmediatelyFast / Lithium 类）**恰好就是我们已装的那套** |
| 许可 | All-Rights-Reserved：**允许放进整合包，需署名作者与来源、不得盈利** → 用 packwiz metadata 引用（不内嵌 jar）+ README 署名 |

**候选对比（为什么是它）**：`data/survey-army.json` 用 13 个军队/战争关键词扫出 82 个候选，逐个核版本后：

| 候选 | 1.21.1 版本 | 判断 |
|---|---|---|
| **`hundred-years-warfare`** | **23** | ✅ **主选**：维护活跃、指挥方式完整、与我们的战斗 mod 兼容 |
| `ancient-warfare-3-npcs` / `-worksites` | 3 / 2 | ⏳ 留档参照：祖师爷，但版本少且偏"NPC 干活" |
| `ballista` | 4 | ⏳ 攻城器械备选 |
| `illager-siege-weapons` | 1 | ⏳ 灾厄围攻题材契合，但只有 1 个版本 |
| `kingdom-civilization-expansion` | 2 | ⏳ 村庄→王国，双端 optional |
| `clay-soldiers-remake` / `tiny-soldiers` | 3 / 1 | ❌ 迷你军团，玩具尺度，撑不起"国家军队" |
| `minecolonies` / `minefortress` | **0 / 0** | ❌ **不支持我们的版本**，直接排除 |
| `guard-villagers` | 17 | ⏳ 已在清单（hold）；有了军队后它更可能是冗余 |

**已否决：俯视角（同期核验，全部有接口证据）**
`dungeons-perspective`（Dungeons 式俯视）只有 2 个版本、1.6 万下载，正文写明 **NeoForge 需 Sinytra Connector**，
且要求 Sodium、并明说"穿墙看不见就关掉实体剔除"——与已装的 `entityculling` 冲突；
`picture-mode`/`freecam` 只能看、不能边看边玩；第三人称类（`better-third-person` 2100 万下载）是**过肩**不是俯视。
→ 结论：**军队不需要俯视角也能成立**，而"俯视冒险"在 1.21.1 NeoForge 上没有低风险选项。

**⚠️ 待实测（已进 `DEFERRED.md`）**：三套袭击系统会不会打架（The Hordes 排期 / Undead Nights 拆墙 / 本 mod 的袭击扩展能否关或延后）·
指挥轮盘与指挥杖在第一人称下的实际手感 · "关系系统"会不会与 OPAC party、FTB Teams 形成**三套队伍** ·
兵力与实体预算（作者建议 150% 实体渲染距离，与 §12 红线冲突，必须量）· 工人系统与 Create 的重叠程度（决定时代闸门怎么切）。

## 八·补二、攻打据点（2026-09-18 补）

> **它不只是"多加内容"**：原来的威胁设计是**单向正反馈**（国力↑ → 袭击↑），玩家只能被动接、看不到尽头。
> **据点给了玩家一个杠杆**——袭击有了来源，就有了可以掐断的地方。它同时缝起三根墙：
> 威胁墙有了"来源" · 远征墙有了"目标" · 时间墙有了可见的转折点（**从守土到开疆**）。规格见 `DESIGN.md` §17.9 (7)。

**重要前提（本次核验推翻了一个想当然的做法）**：

> **不能把据点押在 `hundred-years-warfare` 上。** 它的正文写的是"currently includes **entity-focused** content
> （单位/器械/招募/工人）"，而"**NPC 势力与聚落**"是它的**目标**、结构仍在 **future plans** 里
> —— **现在没有可攻打的 NPC 聚落**。

**所以据点建在"原版本来就有的逻辑"上**（更稳，且不需要编）：
**原版的袭击本来就是从掠夺者前哨站发起的**——"袭击有来源"这件事在游戏里是**真的**。
据点三个来源：① 原版（前哨站 / 林地府邸 / 要塞）；② 已在清单的结构 mod（WDA 敌对船只与法师塔 · D&T 前哨站 · YUNG's 要塞）；
③ **手工布置的占领区**（设计已定"第一版手工布置"，数量与位置我们可控——最稳的兜底）。

**攻守转换三个阶段**：时代 3 **发现**（据点进情报墙）→ 时代 4 **挨打→反击**（清剿能降低袭击压力）→ 时代 5 **成建制攻城**（要塞级目标）。

**收益三条**（没有收益就没有动机）：① 拔点 → 袭击频率/规模**下降一段**（设计里第一次给玩家**降低威胁**的手段）；
② 据点出**高阶图纸** → 填掉空缺 #5「战利品与图纸掉落」；③ 占领后作**前哨**（远征补给点）。

**平衡三条**（防止把威胁墙架空）：① 据点**会重新被占据** → 清剿是周期性的；② 减益**有上限**（只能压制、不能清零）；
③ 攻城**消耗国力**（军队损耗 + 器械消耗）→ 与供养机制挂钩。

**选型（版本数 = 1.21.1+NeoForge，全部本次核验）**：

| 功能位 | 候选 | 版本 | 端侧 | 判断 |
|---|---|---|---|---|
| **敌方据点（势力化）** | `it-takes-a-pillage-continuation` | **8** | 双端必需 | ✅ **主选**：掠夺者扩张文明、建立营地与外哨 |
| **有守军的要塞** | `grim-kingdoms-lost-structures-ruins` | **8** | 服务端必需 / 客户端可选 | ✅ **主选**：炮台/城堡/要塞 + 独特怪 + 隐藏战利品 |
| 世界有"文明在自保" | `millager` | **13** | 双端必需 | ⏳ 待核实：给村民常备军，要确认不会变成"NPC 替你打" |
| 原版前哨站加强 | `improved-pillager-outpost` | 5 | 服务端必需（数据包） | ⏳ 备选 |
| 大型城堡 | `embers-castles-and-keeps` | 3 | 服务端必需 | ⏳ 加厚备选 |
| 阵营系统 | `mob-factions` | **1** | 双端必需 | ⏳ 只有 1 个版本，风险高 |
| 攻城器械 | HYW 自带 + `medieval-siege-machines`（已在清单）+ `ballista`（hold） | 4 | 双端必需 | ✅ 已有 / 备选 |

> **不要重复装**：`dungeons-and-taverns-pillager-outpost-overhaul` 与 `-stronghold-overhaul` 是 D&T 的
> **独立拆出版**，而 `dungeons-and-taverns` **已在清单里** → 装了内容重复，**不单独加**。

## 九、生活质量（11）

| 功能位 | 选定 | 端侧 | 状态 |
|---|---|---|---|
| 物品栏整理 | Mouse Tweaks | 客 | 📌 |
| 物品栏配置 | Inventory Profiles Next | 客 | 📌 |
| 潜影盒预览 | Shulker Box Tooltip | 客 | 📌 |
| 按键冲突查找 | Controlling | 客 | 📌 |
| 配置项搜索 | Searchables | 客 | 📌 |
| 更好的 F3 | BetterF3 | 客 | 📌 |
| 模型缝隙修复 | Model Gap Fix | 客 | 📌 |
| 网络包兼容 | Packet Fixer | 双 | 📌 |
| 附魔说明 | Enchantment Descriptions | 客 | 📌 |
| 提示框美化 | Legendary Tooltips | 客 | 📌 |
| 物品高亮 | Item Highlighter | 客 | 📌 |

## 十、视觉与音效（11 + 2⏳）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| 光影支持 | Iris Shaders | 客 | ⏳ | **装了要重算性能预算**（§12 的指标是硬约束）→ 先测再加 |
| 远景 | Distant Horizons | 双 | ⏳ | 同上，性能影响大 |
| 视频设置扩展 | Sodium Extra + Reese's Sodium Options | 客 | 📌 | 配合 Embeddium |
| 连接纹理 | Continuity + Athena | 客 | 📌 | 观感提升，且服务"统一配色规范" |
| 实体纹理 | Entity Texture Features | 客 | 📌 | |
| 实体模型 | Entity Model Features | 客 | 📌 | |
| 动画 | Not Enough Animations | 客 | 📌 | |
| 皮肤层 | 3D Skin Layers | 客 | 📌 | |
| 聊天头像 | Chat Heads | 客 | 📌 | 多人服辨识度 |
| 音效物理 | Sound Physics Remastered | 双 | 📌 | 洞窟/城墙内的回声 |
| 环境音 | AmbientSounds | 客 | 📌 | |
| 粒子 | Particle Rain | 客 | 📌 | |

## 十一、食物与农业（9）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| 食物核心 | Farmer's Delight | 双 | 📌 | 建造类包的日常内容支柱 |
| 食物扩展 | Expanded Delight · Ocean's Delight · Ender's Delight · My Nether's Delight | 双 | 📌 | 四方向扩展 |
| Create 食物链 | Create: Central Kitchen + Slice & Dice | 双 | 📌 | 与自动化主菜咬合（把食物做成产线） |
| 生活系列 | Let's Do: Farm & Charm · Vinery | 双 | 📌 | 农业与手作的生活感 |
| 厨师村民 | Chef's Delight | 双 | 📌 | 村民厨师 → 世界"本来有文明" |
| 农业便利 | Smarter Farmers（自动补种）· RightClickHarvest | 服/双 | 📌 | 降低重复劳动（注意别让材料经济过松） |

> **核验器抓到的第一个不合格项**：原清单里写了 `lets-do-bakery`，`tools/apply_modlist.py --verify`
> 查出它**在 1.21.1 + neoforge 上没有版本**（检索同类只有它的 Farm&Charm 补丁），于是按规则剔除、
> 换成池内已核验的 `chefs-delight`。**这就是"清单必须可核验"的意义**——不核验的话，这个 mod 会一路
> 混到装包时才炸。

## 十二、社交与身份（3）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| **国旗/国史/地图墙展示** | Immersive Paintings | 双 | 📌 | 自定义图片画作 → **国旗、国史插图、地图墙**（服务 C 身份认同） |
| 协作标记 | Ping Wheel | 双 | 📌 | 集结与协同 |
| 队友状态 | What Are They Up To (Watut) | 双 | 📌 | 看队友在做什么 → 服务"分工可见"与"有人掉队"（H4） |
| 语音 | ~~Simple Voice Chat / Plasmo Voice~~ | — | ❌ | **用户明确不要** |
| 表情 | Emotecraft | 双 | ⏳ | 可选，看朋友要不要 |

---

## 十二·补、中文化与字体（★ 2026-09-15 重大发现）

**结论先行**：中文化**不是**"自己翻译几百条"的活，而是**社区已经解决的工程**——
而且证据就在本机：**一个 479 个 mod 的 1.21.1 NeoForge 包（All the Mods 10）完整跑通了这套设施**。

原先的判断（"给 OPAC 补 613 条 zh_cn"）**作废**：那只是缺口清单，不是解决方案。真正的方案是四层：

| 层 | 组件 | 端侧 | 来源 | 状态 | 证据 / 说明 |
|---|---|---|---|---|---|
| ① 自动汉化 | **I18nUpdateMod** | 双 | CF | 📌 | **自动从社区仓库拉取各 mod 的汉化**——本机 ATM10 实证：`[自动汉化更新] I18nUpdateMod-3.7.0-all.jar` |
| ② 汉化资源包 | 社区补汉材质包 | 客 | — | 📌 | 加载顺序：**补汉包放在 i18n 包下面**（补丁说明明确要求） |
| ③ 硬编码汉化 | **VaultPatcher**（保险库补丁） | 客 | CF | ⏳ | 专治"写死在代码里、资源包翻不了"的文本。本机龙之冒险实证：`vaultpatcher-all-1.4.2+2.jar`。⚠️ **作者自己警告"不可能完全稳定、可能导致崩溃"** → 先用 ①②，不够再上它 |
| ④ 手册与任务书 | 帕秋莉手册汉化包 · FTB Quests 任务 lang | 客 | — | 📌 | 我们用 **Patchouli** 做"约定书"、用 **FTB Quests** 做国策链 → 这两处的界面文本都能汉化（本机补丁里就有 `帕秋莉汉化材质包` 与 `ftbquests/quests/lang/zh_cn.snbt`） |

**还有一层"注入式"做法**（另一个参照包 Skyhive 在用）：把 `kubejs/assets/<命名空间>/lang/zh_cn.json`
直接注入——ATM10 那个补丁用它覆盖了约 100 个命名空间。这是 ①②③ 之外的兜底手段。

**复核工具已经有了**：`tools/lang_audit.py` 会在装完这套设施后重新审计，
报出**仍然没有中文**的 mod（当前基线：19 jar 里 OPAC 613 键 0%）——用它验收，而不是靠感觉。

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| **自动汉化更新** | I18nUpdateMod | 双 | 📌 | 见上 |
| 硬编码汉化 | VaultPatcher | 客 | ⏳ | 有崩溃风险，最后再上 |
| 汉化资源包 | 社区补丁 + 帕秋莉手册包 | 客 | 📌 | 进 `pack/resourcepacks/` |
| **中文字体** | ⏳ **待选** | 客 | ⏳ | 原版默认字体对中文显示一般；而任务书/成书/告示牌**全是中文**。参照包里未见独立字体包，需另找候选 |

---

## 十三、明确不装（连同理由）

| 不装 | 理由 |
|---|---|
| 货币/经济类（整个 `economy` 类目） | 设计已定：私人服不上货币，避免"防刷"一整类麻烦 |
| VeinMiner（连锁挖矿） | 松化材料经济，打掉设计假设 H7、削弱"基建要有成本" |
| Serene Seasons（季节） | **与本包自己的日历冲突**：时间出现两个真相 |
| 宝可梦/小游戏/猎奇/PvP 向 | 与"回归者建国"无关（反目标） |
| Litematica 自动放置 | 客户端模拟点击，官方警告可能被反作弊判定 |
| 任何语音类 | 用户已定 |
| **FTB Essentials**（`/home` `/tpa` `/back`） | 🚫 **会打掉设计约束**：设计规定"**玩家级交通等级决定远征半径**"，免费传送一开这条就没了。真要传送，走 Waystones（有材料门槛、并且是我们设计的"交通等级最高一档"） |
| **FTB Ultimine**（连锁挖矿） | 🚫 与我们排除的 VeinMiner 同类：松化材料经济、削弱"基建要有成本"（H7） |
| **FTB Ranks**（权限/头衔） | 🚫 本包已定"**零头衔**"，Ranks 很容易滑向职位体系——设计明确删过"职位体系" |
| 重复职能的第二选择 | JEI/EMI 二选一 · 地图只用 Xaero · 背包只用 Traveler's · 存储用 Create 自带（不装 Tom's/AE2，避免职能重叠削弱"仓库是你们建的"） · 备份用 Simple Backups（不装 FTB Backups 2） |
| 科技/魔法大件（AE2 全家 · Ars Nouveau 全家 · Apotheosis · Draconic Evolution · EnderIO · allthemodium · Dyson Cube · Compact Machines…） | 参照包共识里它们很主流，但**那是大杂烩包的构成**；本包是主题包，自动化只认 Create 一套。加了会稀释主线、拉高学习成本与性能风险 |

## 十三·补、参照包共识补漏（2026-09-15）

**起因**：清单一度只扫 Modrinth，而**大量常见 mod 只在 CurseForge 分发** → **系统性漏项**。
用户一句"常见的 mod 你都没加入"点出了这个偏差（与"任务书错选 Questlog"是同一个根因）。

**方法**（可重跑、**全离线**）：`tools/reference_pack_diff.py` 扫本机 3 个成熟包的 `mods/`
（All the Mods 10 = 479 jar · Skyhive = 348 · 龙之冒险 = 235，**合计 881 个**），
把 jar 文件名归一成"家族"，**出现在 ≥2 个包里的视为主流**，再与我们的清单对差。

**结果**：家族 749 个 → 主流 261 个 → **我们漏了 230 个**（其中三个包全有的 37 个）。
筛掉与主题无关的科技魔法大件后，**补进 19 个**：

| 补进 | 出现在几个包 | 为什么该有 |
|---|---|---|
| **`twilightforest`** | **3** | ★ **远征维度候选，且 1.21.1 有版本**（`twilightforest-1.21.1-4.8.3345`）。它一直躺在"CF 侧未核实"的待核实项里——**证据其实就在本机** |
| **`lootr`** | **3** | ★ 每个玩家独立战利品箱 → **3~7 人同时探索不用抢箱**，远征主菜的刚需 |
| **`jecharacters`** | **3** | ★ **拼音搜索**（在 JEI 里用拼音搜英文物品名）→ 中文玩家刚需，**直接服务"中文化"那一项** |
| `appleskin` | 3 | 饥饿/饱和度显示——基础 QoL，原先居然漏了 |
| `torchmaster` | 3 | 火把抑制刷怪 → 基地与工事区不被刷怪，配"威胁从时代 4 开始" |
| `polymorph` | 3 | 配方冲突时让玩家选（多 mod 包实用） |
| `attributefix` | 3 | 属性上限修复（多 mod 叠加必备） |
| `solcarrot` | 3 | 食物多样性提升上限 → 与 Farmer's Delight 天生一对 |
| `cookingforblockheads` · `farmingforblockheads` | 2 | 厨房方块 / 市场方块 → 配食物系统与公共仓库 |
| `elevatorid` | 2 | 电梯（建筑内部交通） |
| `craftingtweaks` · `trashslot` · `betteradvancements` · `colorfulhearts` · `connectivity` | 2~3 | 小 QoL |
| `sophisticatedbackpacks` ⏳ | 3 | 背包（**与 Traveler's Backpack 二选一**，待实测手感） |
| `kubejs` ⏳ | 3 | **脚本层候选**：日历与国力要用脚本，设计说"第一版只做两个脚本资产" → 需评估 |
| `l_enders-cataclysm` ⏳ | 3 | 高难 boss 与结构 → 可作"占领区"素材 |

## 十三·补二、跨整合包共识（50 个 NeoForge 包）

**为什么还要做这一层**：本机只有 3 个参照包（其中一个还是 1.20 的）。要"把网撒大",
更硬的证据源是 **Modrinth 上的整合包本身**——`.mrpack` 里的 `modrinth.index.json`
**就是一份完整清单**，拿几十个包统计"某 mod 被多少个包选中"，等于**策展者的投票**。

**方法**：`tools/modpack_survey.py` → 搜 1.21.1 整合包（下载量前 90 + 11 个主题关键词）
→ 下载 `.mrpack` 读 `dependencies` 过滤出 **NeoForge** 包 → 抽取 mod 清单 →
`tools/consensus_gap.py` 与我们的清单对差（两者都可重跑）。

**结果**：

```
扫描 253 个包 → 成功解析 50 个 NeoForge 包 → 合计 1816 个不同 mod（1807 个拿到元数据）
被 ≥2 个包选中、而我们清单里没有的：789 个（含大量前置库）
```

**踩到并修掉的两个 bug**（都记在工具注释里）：
1. **不要用 `loaders:neoforge` 筛整合包**——实测 `modpack + 1.21.1 + neoforge` 只有 **1 个**结果，
   而 `modpack + 1.21.1` 有 **4882 个**：整合包作者极少标 loaders。正确信号在 `.mrpack` 的 `dependencies` 里。
2. **批量查项目是 `GET /projects?ids=[...]`**，不是 POST——写错会**静默失败**（表现为"解析 0 个项目"）。

**一个限制**：为控时我设了 30MB 上限，**误杀了一些 NeoForge 大包**
（`better-mc-neoforge-bmc5` 34MB · `cassetus-building-pack` 294MB · `villagecraft-3` 35MB）→ 下次跑要调高。

**找到的最相关的主题包**（它们是"被选中"这件事的来源）：

| 包 | mod 数 | 为什么相关 |
|---|---|---|
| **`create-kingdom-fallensprout`** | 272 | 名字就是「**Create 王国**」 |
| **`international-coalition-of-nations`** | 247 | 「**国家联盟**」——与"建国"主题直接对口 |
| `train-yard` · `create-rpg-plus` · `adventurecraft-modpack` · `explorisa` | 213~222 | Create 铁路向 / RPG / 冒险 |
| `stardew-village-3` | 167 | 村庄与农业向（星露谷式） |
| **`create.ultimate`** | 80 | **NeoForge 21.1.250——与我们版本号完全相同** |
| `medieval-explorers` · `mc-medieval` · `mightybuilding` · `jeff-stedis-building-modpack` | 31~171 | 中世纪 / 建筑向 |

**补进 20 个**（筛掉前置库与 Fabric 渲染器后的真正漏项）：| 补进 | 被几个包选中 | 为什么该有 |
|---|---|---|
| **`create-steam-n-rails`** | 12 | Create 的**铁路扩展**（蒸汽与铁轨）→ 直接服务"铁路环线"毕业工程 |
| **`copycats`**（Create: Copycats+） | 22 | 复制任意方块外观 → 建筑表现力 |
| `create-dragons-plus` | **23** | Create 扩展，下载量仅 296 万却 23 包选中（典型"冷门好 mod"） |
| `create-design-n-decor` | 12 | Create 装饰 |
| **`macaws-bridges`** · `macaws-windows` · `macaws-fences-and-walls` | 10~11 | ★ **正好补上原先"待补候选"的桥梁/窗户/围墙构件** |
| `amendments` | 12 | 装饰与功能方块（Supplementaries 同作者） |
| **`exposure`** | 11 | **摄影**：拍照片 → 国史插图、纪念碑、地图墙 |
| `towns-and-towers` | 11 | 村庄与塔结构 → 旧世痕迹 |
| **`gravestone-mod`** | 11 | 墓碑（死亡掉落保护）→ **与"事故碑"设定天然契合** |
| `carry-on` | 12 | 搬运方块/机器（建筑时挪箱子与机械） |
| `better-combat` | 14 | 战斗手感 → 威胁主菜的体感 |
| **`badoptimizations`** | **20** | ★ 性能优化——**我们性能组里漏了这个 20 包共识项** |
| **`dynamic-fps`** | **25** | ★ 后台自动降帧（25 包共识的性能/QoL） |
| `noisium` | 11 | 世界生成性能（配 Terralith 的世界生成开销） |
| `jade-addons-forge` | 14 | Jade 扩展（配我们选的 Jade） |
| `lambdynamiclights` | 13 | 动态光源（手持火把照亮） |
| `tectonic` ⏳ | 12 | 地形大修，与 Terralith 并列候选（RoadWeaver 说 mod 版兼容）→ 需二选一实测 |
| `e4mc` ⏳ | 13 | 局域网→公网（朋友免开服的联机方式，看你们怎么开服） |
| `connector`（Sinytra Connector）⏳ | 16 | 见下方生态事实 |

**两个生态事实（值得单独记）**：

1. **榜单前段几乎全是前置库**（Cloth Config 41 包 · Architectury 41 · Kotlin for Forge 39 ·
   Moonlight 32 · Geckolib 30 · YACL 28 …）。它们会**随依赖自动进来**，所以不该当"漏项"——
   这也说明"按数量算 mod"很容易被库灌水。
2. **16/50 个所谓 NeoForge 包其实靠 `Sinytra Connector` 跑 Fabric mod**
   （`forgified-fabric-api` 22 包、`sodium` 46 包就是证据）。这动摇了我们"只查 neoforge"的前提：
   若采用 Connector，可解锁 Fabric 独占 mod；代价是**兼容风险**（这正是它进 ⏳ 而不是 📌 的原因）。

### 第二次筛：冷门好 mod（被多个策展包选中、但下载量低）

B 表的判据是 **被 ≥2 个包选中 且 下载量 < 600 万**——这类往往是「**作者圈认可、但不靠量堆**」的。
这一轮又补进 **25 个**（清单 152 → **180 条**）：

| 方向 | 补进 | 为什么 |
|---|---|---|
| **Create 生态**（自动化主菜） | `create-pattern-schematics`（9 包）· `create-railways-navigator`（9）· `hypertube`（9）· `blocks-bogies`（8）· `create-tfmg`（8）· `create-framed`（9）· `create-train-parts`（6） | ★ **`create-pattern-schematics` 与我们的图纸系统直接相关**；铁路三件套服务"铁路环线"毕业工程；hypertube 是管道运输 |
| **建筑构件** | `macaws-trapdoors`（10）· `macaws-lights-and-lamps`（8）· `macaws-paths-and-pavings`（6）· `macaws-paintings`（5）· **`medieval-buildings`（5）** · `diagonal-walls`（5）· `framedblocks`（5） | Macaw's 系列补齐；**`medieval-buildings` 主题高度对口**；`macaws-paths-and-pavings` 服务世界路网；`macaws-paintings` 是国旗/国史展示的另一个手段 |
| **结构**（旧世痕迹 / 远征） | `repurposed-structures-forge`（5）· `additional-structures`（5）· `tidal-towns`（5） | 结构位扩容 |
| **玩法与体验** | **`corpse`（8）** · `almostunified`（6）· `small-ships`（5）· `exposure-polaroid`（5）· `friends-and-foes-forge`（6） | `corpse` 让"失败=掠夺但不烧家"落得更实；`almostunified` 归并多 mod 材料、降低混乱；`small-ships` 服务水上交通 |
| **食物** | `brewin-and-chewin`（6）· `miners-delight`（5）· `storage-delight`（5） | 配 Farmer's Delight |
| **留档待复议** | `c2me-neoforge`（9 包！）· `skills`（8）· `saturn`（7） | ⚠️ **C2ME 被 9/50 个包选中**——我们撤它有三条理由（alpha / 与 RoadWeaver 冲突 / 覆盖 ModernFix 优化），留档供复议；`skills` 与"零头衔/简洁"有张力 |

> ⚠️ **这一轮的已知缺口**：为控时我设了 30MB 上限，**85 个包被跳过**（多数是宝可梦/PvP，但含
> `cassetus-building-pack` 294MB · `blossoming-kingdoms` · `kingdom-network` · `medieval-mc-neoforge-mmc5`
> · `minecolonies-origins` · `better-mc-neoforge-bmc5` 等**主题对口的大包**）。
> 补跑这批（上限 350MB、16 个包）**已启动但按用户要求中止** → **这个缺口如实留着**，需要时再跑。

## 十三·补三、CurseForge 侧（★ 2026-09-15 补上的第二个系统性偏差）

**起因**：用户问"你扫过 CurseForge 的内容吗"——**答案是没有**。此前候选池只来自 Modrinth，
CF 只被"本机恰好装了的包"**间接**碰到。这与 Questlog 选错是同一个根因。

**为什么以前没做**：以为 CF 官方 API 要 key 就进不去。**实测：存在公开代理** `api.curse.tools`
（按官方 API 形状转发，无需 key）。关键参数一次踩对：

```
gameId=432        Minecraft
classId=6         **Mods**（不写会混进整合包/材质包 —— 第一次就踩了这个）
gameVersion=1.21.1
modLoaderType=6   NeoForge（1=Forge, 4=Fabric, 5=Quilt, 6=NeoForge）
sortField=6       按总下载量
```

**结果**：13 个类目 + 14 个主题关键词 × 50 条 → **1048 个 CF 候选，其中 964 个不在我们清单里**；
筛掉前置库与不相关题材后**补进 27 个**（清单 180 → **208 条**）。

| 补进 | 为什么 |
|---|---|
| **`every-compat`**（Wood Good） | ★ **木材兼容**：让所有木材 mod 互相通用——多建造 mod 包的刚需 |
| **`just-enough-resources-jer`** | JEI 的经典搭档（矿石/生物分布查询） |
| **`deeperdarker`** | ★ 远征维度的**第三个**候选（另有 Twilight Forest / Dimensional Dungeons） |
| **`enhanced-celestials`** | ★ 血月/蓝月等天象 → 与"尸潮按周期来"的排期设定契合 |
| **`medieval-siege-machines`** | ★ 中世纪攻城机械 → 与"御敌"主菜契合 |
| `yungs-better-caves` | 补全 YUNG 全家桶 |
| `corail-tombstone` | 墓碑（下载量比 `gravestone-mod` 更高） |
| `medieval-paintings` · `medieval-music` · `medieval-buildings-end/nether-edition` · `epic-knights-armor-and-weapons` · `armor-of-the-ages` | **中世纪系列**——CF 侧明显更厚的题材 |
| `choicetheorems-overhauled-village` · `stoneholm-forge` · `villager-names` · `more-villagers` · `trading-post` | 村庄与城镇：让"世界本来有文明"更有质感 |
| `naturalist` · `ecologics` | 生态与动物 |
| `refurbished-furniture` · `buildersaddition` · `macaws-furniture` | 家具与建材补全 |
| `create-horse-power` | Create 马力（马车 → 早期交通） |
| **`ftb-quests-optimizer`** · `ftb-quests-lang-splitter` · `extraquests` | ★ 我们在用 FTB Quests —— 这三个是它的**性能优化 / 文本拆分（中文化要用）/ 扩展任务类型** |
| `ftb-chunks-forge` ⏳ | CF 上领地的主流方案（1.3 亿下载）；OPAC 已实测通过 → 留档 |

**核验器因此多了第四条来源**：`data/cf-survey.json` 是 CF 接口按 `1.21.1 + NeoForge` 筛出来的，
**能返回就说明有版本** → 那 27 个"只在 CF 分发"的 mod（在 Modrinth 接口里一律 `PROJECT_NOT_FOUND`）
现在能被正确验过，`--apply` 也会自动改用 `packwiz curseforge add`。

**一条工具教训**：这个调查要打 27 次接口、约 1~3 分钟，**连续被打断两次**（跑到第 17 个查询被掐、输出全丢）。
修法两层：① **每次请求后立刻把结果落盘成缓存**（`data/cf-cache.json`），断了不白跑；
② 加 `--only categories|queries` **分段执行**，每次调用都短。

## 十三·补四、MC百科（mcmod.cn）目录对差（2026-09-18）：第四条腿 · 中文社区维度

**起因**：用户问"你能看一下 mcmod.cn 这个里面的内容吗"。

**这条腿补的是什么偏差**：前三条腿（本机参照包 / 跨包共识 / CurseForge）**全是英文社区视角**。
本包的用户是中文玩家，而中文社区有一批"自己圈子里常用、但在 Modrinth / CF 榜单上不显眼"的 mod
（典型：输入法冲突修复、提示框多语言、FTB 任务书的中文侧工具）。这是第四条腿。

**站点能力（实测，不是推测）**：

| 项 | 实测结果 |
|---|---|
| robots.txt | 只禁 `add/edit` 这类**提交**路径；检索页可访问 → 只读检索页与 mod 页 |
| 检索页 | 服务端渲染，支持 `?mcver=1.21.1` · `?category=N` · `?api=N` · `?page=N` |
| 加载器编号 | 1=Forge · 2=Fabric · 3=Rift · 4=LiteLoader · 5=数据包 · 6=命令方块 · 7=文件覆盖 · 8=行为包 —— **没有 NeoForge 这一档** |
| 排序参数 | 只有「默认排序」与「按收录时间」两个真实选项；`sort=views/hot/popular/downloads` 全被忽略 |
| 整合包区 `/modpack.html` | **整条路径带验证码**（带参数与不带参数都是 403 + 算式验证）→ **不使用**（那是绕过访问控制，不做） |

**两件"看不懂就当热度用"的诱惑，我都停住了**：

1. 默认排序**不是**时间序（首页同时混着 ID 1796 的老 mod 和 30958 的新 mod），很像热度序 ——
   但站点没有任何文字说明它是什么，而能证伪的「指数走势」/「贡献统计」两个入口**都跳 `/login/`**。
   → **默认序语义无法验证，因此名次只当稳定遍历顺序用，不当热度证据。**
2. 「按数量算 mod」会被前置库灌水（跨包共识那轮已经踩过），所以这轮不按数量交付。

**规模与对差**：`?mcver=1.21.1` 共 **334 页**（百科自称收录 10672 条），抓完去重 **9238 条**。
与 `modlist.tsv` 对差后缺口 **6853 条**，按**条目简介的中文关键词**分成 15 个功能位桶
（自动化/机械 160 · 多人/联机 246 · 冒险/远征 219 · 存储/背包 193 · 界面/信息 192 · 世界生成 177 …）。

**踩到并修掉的匹配 bug（会造成"假缺口"）**：mcmod 上不少条目没有独立英文名字段，
英文名带着标签前缀落在中文名字段里（`name="[FFS] FTB Filter System"`、`ename=""`）。
第一版只拿 `ename` 匹配 → **`FTB Filter System`、`OPAC` 这些我们清单里已有的 mod 被判成缺口**。
修法：`ename` 与 `name` 都作候选、剥掉 `[TAG]` 前缀、音标折叠（`Millénaire` → `millenaire`，否则 `é` 被吃掉会误匹配）。
命中数 **1150 → 2385**，缺口 **8088 → 6853**；顺带把已有 mod 的百科中文名覆盖做到 **179/208**（`data/mcmod-names.tsv`）。

**核验**（`tools/verify_candidates.py`，24 条候选 → **20 条拿到证据**）：mcmod 只做**发现**，
判定一律落回接口 —— Modrinth 用 `facets[versions:1.21.1, categories:neoforge]` 搜，
再过 `/project/<slug>/version` 确认真有文件；CF 用 `api.curse.tools` 搜 + `/mods/<id>/files` 确认。

**两个自查**（否则证据是假的）：

- `Millénaire` 第一次匹配到的是 `civilis-millenaire-compatibility`（**兼容补丁，不是本体**）→ 收紧成 CF 只认全等后正确命中本体。
- **CF 的 `gameVersion`/`modLoaderType` 过滤器万一被代理忽略**，`/files` 会返回全部文件、"CF-OK" 就是假的。
  逐条读返回载荷的 `gameVersions` 复核：所有命中文件都真含 `['1.21.1', 'NeoForge']`（Millénaire 只有 2 个文件，确实是移植版本身）。

**补进 12 条 `plan`**：

| 补进 | 功能位理由 |
|---|---|
| **`imblocker-original`** | ★ 输入法冲突修复 —— **中文玩家刚需**，开着输入法也能正常操作 |
| **`polyglottooltip`** | ★ 提示框补多语言名称行 → 直接缓解"未汉化 mod 读不懂" |
| **`quest-enhance`** | ★ FTB Quest Enhance：剪贴板贴图 / 章节画布 / 生物模型图标 → 直接服务我们要写的 90~110 个任务 |
| `certain-questing-additions` | FTB Quests 的 QoL 与动画补充 |
| **`stellarcreateoptimization`** | ★ Create 6.0.x 服务端 tick + 客户端渲染优化（基建时代的核心就是 Create） |
| **`bye-pregen`** | ★ 降低区块生成造成的 MSPT 尖峰与服务端冻结（与已装 `chunky` 预生成互补） |
| `xaeroplus` | 本包已用 Xaero 小地图 + 世界地图 → 性能与功能增强 |
| `put-a-plug-in-it!` | 内存泄漏修复，轻量、低风险 |
| **`chunk-plan`** | 服务端**探索配额** → 正好对应"交通等级决定远征半径"与服务器资源控制 |
| `easy-mob-spawn-control` | 时代**威胁闸门**的执行器（御敌时代的强度分级要靠它落地） |
| `todolist` | TeamTasks：多人共享待办，协作治国的轻量载体 |
| `travelers-titles` | 进入群系/维度显示标题 → 强化"开疆拓土"的仪式感 |

**6 条 `hold` 连同理由**（不装的理由也是结论）：

| hold | 为什么先不装 |
|---|---|
| `millenaire` | 主题强相关（村庄生成/当村长/村庄关系），但 1.21.1 移植版**只有 2 个文件** → 稳定与兼容风险待实测 |
| `orcinvasion` | 御敌时代内容对味，但**只有 275 次下载** → 采用率过低，兼容与质量都没验 |
| `dungeon-difficulty` | 按群系提难度，可能与**本包按时代推进的难度曲线**打架，需先定位 |
| `datatip` | 与 `polyglottooltip` 功能重叠 → 二选一 |
| `ftbquest-slot-rewards` | 任务书与 Curios 栏位互通，体量小、可选 |
| `offlineskins` | 离线皮肤缓存（本项目测试环境就是离线模式），优先级低 |
| `zstdnet-minekuai` | **不是包内容**：它是客户端↔服务端之间的**代理**（ZSTD 压缩流量）→ 属部署工具，装机时不要放进 `mods/` |

**3 条 `skip`**：`banner-claim`（旗帜圈地，主题很对味，但 Modrinth 有项目却**无 1.21.1+NeoForge 版本**，
且会与已实测通过的 OPAC 形成**双领地体系**）· `litematica-printer`（投影自动放置：无 1.21.1+NeoForge 版本，
且会削弱建造劳动）· `visualblueprintmaterials`（Modrinth 与 CF 均查无）。

**顺带修掉的工具问题**：`--verify` 原来把 `skip` 行也计入失败并返回非零退出码 →
"已定不装因而查无版本"被误报成回归，失败信号被噪声淹没。现在 skip 单列，不参与通过率。

**核验链打通**：`apply_modlist.py` 的 CF 证据来源原来只读 `data/cf-survey.json`，
而本轮逐条核验过的 CF 独占 mod 未必在它的结果里 → 已把 `data/candidate-verify.json` 的 CF-OK 结果并入，
否则这些**已经拿到硬证据**的 mod 会在 `--verify` 阶段被判 `CF_UNVERIFIED` 而装不上。

## 十四、待定项（唯一还没定的，以及缺什么才能定）

| # | 待定 | 缺什么 | 影响 |
|---|---|---|---|
| 0 | **渲染器选型：`embeddium` vs `sodium`** | 需要拍板。C3/C8/C10 **三处冲突都指向它**；换 Sodium 系可解锁 4 个 mod（`stellarcreateoptimization`、`create-aeronautics`、`sodium-extra`、`reeses-sodium-options`），但要重做渲染侧验证与性能基线 | 直接决定 `hold` 里 4 条的去留；见 `CONFLICTS.md` 决策点 |
| 1 | **远征维度选谁** | ✅ 已实证 1.21.1 有版本，但**装机时 `the-twilight-forest` 加进去就进不去世界**（C7，机制待查）→ 现在 Twilight Forest 处于 `hold`；备选 Dimensional Dungeons（形态仍未核实） | 时代 3「出关」的目标怎么写 |
| 2 | **FTB Teams 的"全员同队"怎么配** | 装好后实测配置项（目标：任务进度全队共享，且不引入第二个"队伍"概念） | 任务书能不能承载"国策级"团队任务 |
| 3 | **Iris 光影 + Distant Horizons** | 加装后跑性能基线（§12 指标）。**与"待定 0 渲染器选型"绑定**：Iris 依赖 Sodium 系 | 决定能不能给朋友开光影 |
| 4 | **Alternate Current** | 与 ModernFix 的优化是否重叠 | 影响服务端基线 |
| 5 | **Guard Villagers** | 实机判断会不会变成"NPC 替你打" | 与反目标 #3 冲突与否 |
| 6 | **桥梁/屋顶类建筑构件** | **候选池里没有合适的**（Macaw's 的 bridges/roofs 未出现在检索结果里）→ 需要单独补一轮候选 | 公共工程（跨海大桥/铁路环线）的构件 |
| 7 | **`datatip` vs `polyglottooltip` 二选一** | 装好后实测两者对"未汉化 mod"的实际补足程度 | 中文化设施最终装哪一个 |
| 8 | **`millenaire` 能不能用** | 实机跑一段，看村庄生成/性能，以及与 OPAC 领地、Create 是否冲突 | 立国时代"世界本来就有文明"这条线要不要加 |
| 9 | **`chunk-plan` 的配额怎么定** | 先量"1 人探索的区块/小时"，再换算到 3~7 人 | 远征半径与服务器流量的落地参数 |

## 十四·补、装机结论（2026-09-19）

**清单已全部落地**：`plan` 181 + 已装地基 16 = **197 条全部就位**（客户端 236 jar / 服务端 198 jar），
分 6 批（批 0 地基 + 批 1~5）逐批验证通过。
**装机过程中真撞到的冲突与缺陷逐条记在 `CONFLICTS.md`**（C1~C11 / T1~T17 / R1~R4 + 服务端侧观察）。

当前未就位的 45 条，全部有明确理由：

| 状态 | 条数 | 含义 |
|---|---|---|
| `hold` | 36 | **押后重审**：多为"与已装项冲突"或"要等渲染器决策"。**不是判死**，证据都在 `CONFLICTS.md` |
| `skip` | 9 | **确定不装**，含 C3 `stellarcreateoptimization`（硬要 sodium）· C4 `byepregen`（与 noisium 显式不兼容）· C5 `stoneholm-forge`（进世界即崩） |

> **本文件的状态列（✅/📌/⏳/❌）反映的是"选型时"的判定**；**权威装机状态在 `modlist.tsv`**（脚本读）
> 与 `CONFLICTS.md`（含证据）。

## 十五、数量账

| 分层 | 数量（估） |
|---|---|
| 性能组 | 13 |
| 引擎与基础 | 5（JEI/Jade/CrashAssistant/Paxi + 待定 1） |
| 世界生成 | 12 |
| 领地与治理 | 5 |
| 建造与自动化 | 11 |
| 交通 | 2 + Create 自带 |
| 威胁 | 2 |
| 远征与冒险 | 10 |
| **任务书（FTB 系列）** | **6**（Quests/Teams/Library/XModCompat/JEIExtras/FilterSystem） |
| 生活质量 | 11 |
| 视觉与音效 | 13（含 2 待定） |
| 食物与农业 | 9 |
| 社交与身份 | 3 |
| **内容/功能 mod 合计** | **约 214** |
| 前置库（随依赖自动进） | 约 25~40 |
| **jar 总数（估）** | **约 239~254** |

对照参照系：本机 All the Mods 10 是 **479 个 jar / 1.3 GB**（同版本）；跨包共识里解析的 50 个 NeoForge 包中，
**272（create-kingdom-fallensprout）、252（adventurecraft-modpack）、247（international-coalition-of-nations）**都在其中。
**本包定位是主题包不是大杂烩**，215~230 已经与那些主题包同量级。

### 清单核验（`tools/apply_modlist.py --verify`）

```
清单载入：242 条  hold=36 · installed=16 · plan=181 · skip=9
核验结果：239/239 通过（另有 3 条 skip 不参与）
装机结果：197/197 就位（客户端 236 jar · 服务端 198 jar）
```

五条**证据来源**（每一类都标明，不含"我觉得它有"）：

| 来源 | 条数 | 说明 |
|---|---|---|
| 本地候选池 | 125 | 两个 survey JSON 本身就是按 `1.21.1 + neoforge` 筛出来的（Modrinth） |
| **本地参照包实证** | 30 | 本机成熟包的 jar 文件名带版本号（FTB 系列、Twilight Forest、Lootr、I18nUpdateMod 等） |
| **CF 目录** | 27 + 9 | ★ `data/cf-survey.json` 与**逐条核验过的** `data/candidate-verify.json`（后者是 mcmod 那轮补的）——**专治"只在 CF 分发"的 mod** |
| 联网补查（Modrinth 接口） | 19 | 少数不在池里的 |
| 已装运行中 | 8 | 它正跑在这个包里，`baseline.csv` 有它的加载/TPS 证据 |
| ✗ 不合格 | 3（`skip`，不参与通过率） | 已定不装且查无版本：`banner-claim` · `litematica-printer` · `visualblueprintmaterials` |

## 十六、装载顺序（按时代，对接 `DESIGN.md` §17）

1. **建世界之前必须定**：Terralith + Lithostitched · RoadWeaver（要预生成）
2. **时代 0–1 之前**：JEI · Jade · Patchouli（约定载体）· Crash Assistant · Paxi · 结构 mod 补齐 · **FTB Quests 系列（任务书是时代 1 的国策链载体）**
3. **时代 2 之前**：Portable Blueprints · Effortless Building · WorldEdit · Chipped/Rechiseled · 装饰家具 · 食物组
4. **时代 3 之前**：维度（先核实形态）· 定位工具 · 地图 · 背包 · 考古 · 探险装备
5. **时代 4 之前**：**The Hordes + Undead Nights**（最不能开天窗）· **军事 `hundred-years-warfare` + 可攻打的据点（见 §八·补 / 八·补二）** ·（可选）Zombie Awareness 验证
6. **时代 5 之前**：WorldEdit 已就位 · 大工程构件（待补候选）
7. **全程**：GriefLogger + GLRA · Simple Backups
8. **每批之后**：`autotest` 冒烟 → `server_ctl --script tests/server_v0.txt` → `blueprint_check` / `lang_audit` → 记 `baseline.csv`

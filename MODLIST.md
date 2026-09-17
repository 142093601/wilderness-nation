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
| **任务书载体** | **FTB Quests** | 双 | CF | 📌 | 章节 + 任务树 + 依赖 + 奖励；`config/ftbquests` 是它的数据目录 |
| 任务书依赖·团队 | FTB Teams | 双 | CF | 📌 | ⚠️ **需设成"全员同队"**（你们本来就是一个国家）→ 任务进度才能共享；具体配置项装好后实测 |
| 任务书前置 | FTB Library | 双 | CF | 📌 | 必需 |
| FTB 跨 mod 兼容 | FTB XMod Compat | 双 | CF | 📌 | 让 FTB 组件认识其他 mod 的方块/物品 |
| JEI 扩展 | FTB JEI Extras | 双 | CF | 📌 | 配我们选的 JEI |
| 物品过滤 | FTB Filter System | 双 | CF | 📌 | 小工具，配合自动化 |
| ~~任务书备选~~ | ~~Questlog~~ | — | MR | ❌ | 降级为**备选**：若 FTB Quests 出问题再回退 |

**CF 类的核验证据**（本机参照包里的 jar 文件名，本身就是版本硬证据）：

```
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

## 十四、待定项（唯一还没定的，以及缺什么才能定）

| # | 待定 | 缺什么 | 影响 |
|---|---|---|---|
| 1 | **远征维度选谁** | ✅ **Twilight Forest 已实证 1.21.1 有版本**（参照包共识补漏时发现的）→ 现在有两个候选：Twilight Forest（成形、资料多）vs Dimensional Dungeons（形态仍未核实）。需实测取舍 | 时代 3「出关」的目标怎么写 |
| 2 | **FTB Teams 的"全员同队"怎么配** | 装好后实测配置项（目标：任务进度全队共享，且不引入第二个"队伍"概念） | 任务书能不能承载"国策级"团队任务 |
| 3 | **Iris 光影 + Distant Horizons** | 加装后跑性能基线（§12 指标） | 决定能不能给朋友开光影 |
| 4 | **Alternate Current** | 与 ModernFix 的优化是否重叠 | 影响服务端基线 |
| 5 | **Guard Villagers** | 实机判断会不会变成"NPC 替你打" | 与反目标 #3 冲突与否 |
| 6 | **桥梁/屋顶类建筑构件** | **候选池里没有合适的**（Macaw's 的 bridges/roofs 未出现在检索结果里）→ 需要单独补一轮候选 | 公共工程（跨海大桥/铁路环线）的构件 |

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
| **内容/功能 mod 合计** | **约 165** |
| 前置库（随依赖自动进） | 约 25~40 |
| **jar 总数（估）** | **约 190~205** |

对照参照系：本机 All the Mods 10 是 **479 个 jar / 1.3 GB**（同版本）；跨包共识里解析的 50 个 NeoForge 包中，
**272（create-kingdom-fallensprout）、252（adventurecraft-modpack）、247（international-coalition-of-nations）**都在其中。
**本包定位是主题包不是大杂烩**，190~205 已经落在一个成熟主题包的区间内。

### 清单核验（`tools/apply_modlist.py --verify`）

```
清单载入：180 条  hold=14 · installed=16 · plan=147 · skip=3
核验结果：180/180 通过
```

四条**证据来源**（每一类都标明，不含"我觉得它有"）：

| 来源 | 条数 | 说明 |
|---|---|---|
| 本地候选池 | 118 | 两个 survey JSON 本身就是按 `1.21.1 + neoforge` 筛出来的 |
| **本地参照包实证** | 28 | 本机成熟包的 jar 文件名带版本号 —— **专门补 CurseForge 侧**（FTB 系列、Twilight Forest、Lootr、I18nUpdateMod 等） |
| 联网补查（Modrinth 接口） | 26 | 少数不在池里的 |
| 已装运行中 | 8 | 它正跑在这个包里，`baseline.csv` 有它的加载/TPS 证据 |
| ✗ 不合格 | — | 会被拦下、不允许进清单（如 `lets-do-bakery`；`sinytra-connector` 的 slug 应写作 `connector`） |

## 十六、装载顺序（按时代，对接 `DESIGN.md` §17）

1. **建世界之前必须定**：Terralith + Lithostitched · RoadWeaver（要预生成）
2. **时代 0–1 之前**：JEI · Jade · Patchouli（约定载体）· Crash Assistant · Paxi · 结构 mod 补齐 · **FTB Quests 系列（任务书是时代 1 的国策链载体）**
3. **时代 2 之前**：Portable Blueprints · Effortless Building · WorldEdit · Chipped/Rechiseled · 装饰家具 · 食物组
4. **时代 3 之前**：维度（先核实形态）· 定位工具 · 地图 · 背包 · 考古 · 探险装备
5. **时代 4 之前**：**The Hordes + Undead Nights**（最不能开天窗）·（可选）Zombie Awareness 验证
6. **时代 5 之前**：WorldEdit 已就位 · 大工程构件（待补候选）
7. **全程**：GriefLogger + GLRA · Simple Backups
8. **每批之后**：`autotest` 冒烟 → `server_ctl --script tests/server_v0.txt` → `blueprint_check` / `lang_audit` → 记 `baseline.csv`

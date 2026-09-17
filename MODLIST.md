# 荒野建国 · mod 清单（权威版）

> **这份文件回答一个问题：这个包到底装哪些 mod。** 每个功能位一行，没有"以后再想"。
>
> **挑选纪律（很重要）**：本表所有 mod **只从已核实的候选池里挑**——
> `CANDIDATES.md`（472 个）与 `CANDIDATES-THICK.md`（662 个）里的条目，
> 其 `1.21.1 + neoforge` 支持来自 Modrinth **接口返回**（`tools/survey_mods.py`），不是记忆。
> 挑不到合适候选的功能位，一律标 ⏳**并写明缺什么**，绝不凭印象塞一个进去。
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

## 八、任务书（1）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| 任务书载体 | Questlog | 双 | ⏳ | 1.21.1 上唯一已核实的载体；**能否承载"章 + 清单 + 长文本"未核实** → 先试做一章（5 个任务 + 依赖）再决定 |

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

> **核验器抓到的第一个不合格项**：原清单里写了 `lets-do-bakery`，`tools/apply_modlist.py --verify`
> 查出它**在 1.21.1 + neoforge 上没有版本**（检索同类只有它的 Farm&Charm 补丁），于是按规则剔除、
> 换成池内已核验的 `chefs-delight`。**这就是"清单必须可核验"的意义**——不核验的话，这个 mod 会一路
> 混到装包时才炸。
| 农业便利 | Smarter Farmers（自动补种）· RightClickHarvest | 服/双 | 📌 | 降低重复劳动（注意别让材料经济过松） |

## 十二、社交与身份（3）

| 功能位 | 选定 | 端侧 | 状态 | 说明 |
|---|---|---|---|---|
| **国旗/国史/地图墙展示** | Immersive Paintings | 双 | 📌 | 自定义图片画作 → **国旗、国史插图、地图墙**（服务 C 身份认同） |
| 协作标记 | Ping Wheel | 双 | 📌 | 集结与协同 |
| 队友状态 | What Are They Up To (Watut) | 双 | 📌 | 看队友在做什么 → 服务"分工可见"与"有人掉队"（H4） |
| 语音 | ~~Simple Voice Chat / Plasmo Voice~~ | — | ❌ | **用户明确不要** |
| 表情 | Emotecraft | 双 | ⏳ | 可选，看朋友要不要 |

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
| 重复职能的第二选择 | JEI/EMI 二选一 · 地图只用 Xaero · 背包只用 Traveler's · 存储用 Create 自带（不装 Tom's/AE2，避免职能重叠削弱"仓库是你们建的"） |

## 十四、待定项（唯一还没定的，以及缺什么才能定）

| # | 待定 | 缺什么 | 影响 |
|---|---|---|---|
| 1 | **Dimensional Dungeons 的形态** | 读文档/实测：是"可探索区域"还是"一次性副本" | 时代 3「出关」的目标怎么写 |
| 2 | **Questlog 的承载能力** | 试做一章（5 任务 + 依赖链） | 任务书方案要改 / 退化 / 换载体 |
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
| 任务书 | 1 |
| 生活质量 | 11 |
| 视觉与音效 | 13（含 2 待定） |
| 食物与农业 | 9 |
| 社交与身份 | 3 |
| **内容/功能 mod 合计** | **约 97** |
| 前置库（随依赖自动进） | 约 20~30 |
| **jar 总数（估）** | **约 120~130** |

对照参照系：本机 All the Mods 10 是 **479 个 jar / 1.3 GB**（同版本），另一个主题包 235 个。
**本包定位是主题包不是大杂烩**，所以 120~130 是合理落点；不够再按"主菜优先"补，而不是按数字堆。

## 十六、装载顺序（按时代，对接 `DESIGN.md` §17）

1. **建世界之前必须定**：Terralith + Lithostitched · RoadWeaver（要预生成）
2. **时代 0–1 之前**：JEI · Jade · Patchouli（约定载体）· Crash Assistant · Paxi · 结构 mod 补齐
3. **时代 2 之前**：Portable Blueprints · Effortless Building · WorldEdit · Chipped/Rechiseled · 装饰家具 · 食物组
4. **时代 3 之前**：维度（先核实形态）· 定位工具 · 地图 · 背包 · 考古 · 探险装备
5. **时代 4 之前**：**The Hordes + Undead Nights**（最不能开天窗）·（可选）Zombie Awareness 验证
6. **时代 5 之前**：WorldEdit 已就位 · 大工程构件（待补候选）
7. **全程**：GriefLogger + GLRA · Simple Backups
8. **每批之后**：`autotest` 冒烟 → `server_ctl --script tests/server_v0.txt` → `blueprint_check` / `lang_audit` → 记 `baseline.csv`

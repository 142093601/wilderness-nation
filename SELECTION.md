# 荒野建国 · mod 选型决策表

**目标环境：Minecraft 1.21.1 + NeoForge（Java 21）** · 3~7 人私人服务器 · 约 150 小时毕业（3.5~7 个月）

证据状态标记：

| 标记 | 含义 |
|---|---|
| ✅ | 已由 Modrinth API 逐版本核实（加载器 + MC 版本精确配对） |
| ⚠️ | 版本可用，但**能力面未核实**（Modrinth API 不暴露配置项/机制细节） |
| ❓ | 该项目**不在 Modrinth**，仅 CurseForge/官方渠道分发，**版本未核实** |
| 🚫 | 已核实**不能用于本包**（加载器不符或机制不合），列入否决记录 |

> 纪律：本表**没有凭记忆断言任何版本支持**。凡未核实的格都标了 ❓ 或 ⚠️，且注明该怎么补。

---

## 一、核心功能位

| 功能位 | 选型 | 替代候选 | 解锁阶段 | 端侧 | 决策与理由 | 状态 |
|---|---|---|---|---|---|---|
| **领地与权限** | Open Parties and Claims (`open-parties-and-claims`) | Claim、Easy Factions and Claims | 1 | 双端 | 唯一同时提供**队伍（party）+ 领地（claim）+ 细粒度权限**的候选——"国家成员"这个概念直接落在队伍上，不需要自己造 | ✅ 1.20.1 Forge / 1.21.1 NeoForge |
| **自动化/机械** | Create 6 (`create`) | Mekanism、Immersive Engineering | 2 | 双端 | 一箭三雕：机械自动化 + **图纸打印机（Schematicannon）** + 物流（公共仓库的实现手段）。前期不需要第二套科技 mod | ✅ 1.21.1 NeoForge |
| **图纸：标准件打印** | Create 的 Schematicannon + Schematic and Quill | — | 2 | 双端 | `.nbt` 图纸放进 `schematics/` 即被识别；**服务端方块执行、逐块放置、消耗材料**——正好把"一键放置"变成"消耗资源的国力行为"，不是作弊 | ✅（来自 Create 本体） |
| **图纸：工程制与认捐** | Portable Blueprints (`portable-blueprints`) | Building Blueprint | 2 | 双端（`server_side: required`） | 唯一双向覆盖"扫描→存图纸→异地放"闭环的候选；自带 `/blueprintserver` 分发、限流 gamerule、**容器代扣材料**——工程制的现成机制 | ✅ 1.20.1 Forge / 1.21.1 Forge+NeoForge |
| **建筑辅助（玩家侧）** | Effortless Building (`effortless-building`) | WorldEdit（管理用） | 2 | 双端 | 批量放置/镜像/阵列，服务端许可的建造加速，替代被禁的 Litematica 自动放置 | ✅ 1.21.1 Fabric+NeoForge |
| **管理工具** | WorldEdit (`worldedit`) | — | 全程（op） | 服务端 | 只在 op 权限下用：整平场地、修地形、**事故修复**（配合回滚） | ✅ 1.21.1 NeoForge |
| **世界路网（氛围）** | RoadWeaver（阡陌交通, `roadweaver`） | 不装（世界更荒） | 3 | **双端必需** | 在村庄/结构间自动生成道路（贝塞尔平滑、遇山开隧道、遇水搭桥、路灯路标）→ "这世界本来就有文明"的沉浸感，也给远征提供路径。**⚠️ 作者明写 2.1.0 及以前与 C2ME 不兼容（道路上出现浮空树），而 1.21.1 最新为 2.0.9** → 必须核实是否已修；**⚠️ 道路不能在已加载区块生成**，须预生成完再放人 | ⚠️ 版本✅（1.21.1/NeoForge）/ **兼容性与运维约束待核实** |
| **群系扩充** | **Terralith (`terralith`)** ✅ 已定 | Biomes O' Plenty（+TerraBlender/GlitchCore）· 其他 | 全程（**必须在建世界前定**） | 双端（**客户端 optional**） | `DESIGN.md` 十一节明写"必须有一个群系扩充"且必须在 RoadWeaver 兼容名单内。**选 Terralith 的三条理由**：① **RoadWeaver 作者正文原文点名兼容**（"2.0.6+ 与 Tectonic-V2 / 史诗地形 / Terralith 兼容，与 Tectonic-V3 不兼容"）；② **客户端 optional**，不必逼每个朋友装；③ **只用原版方块** → 与图纸生成器、统一配色规范、建材经济零冲突。需前置 `lithostitched`。⚠️ **不能从已有世界移除、也不建议后加到旧世界** | ✅ 1.21.1 NeoForge（接口核实）；⚠️ 与 RoadWeaver 的**实测**仍需做 |
| **公共仓库** | **不装独立 mod**：Create 物流/库存网络 + 领地箱子权限 | ❓Stash（仅 CF 未核实；且 1.21.1 无版本） | 1 | 双端 | 1.21.1 上没找到可用的独立共享存储 mod；**改用 Create 物流 + 权限实现**反而更贴合"仓库是你们建的" | ✅（方案已成，非 mod 依赖） |
| **经济与交易** | **先不上货币 mod**：以物易物 + 公共仓库 + 机械交易设施 | Create: Numismatics（后期可选） | 2~3 | 双端 | 3~7 人私人服不需要货币系统；货币反而引入"防刷"这一整类麻烦。后期若真要，Numismatics 与 Create 同生态，是首选 | ✅ Numismatics 两版本皆可 |
| **冒险结构** | YUNG's Better Dungeons + When Dungeons Arise + Dungeons and Taverns | Stalwart Dungeons（止步 1.20.1） | 3 | 双端 | 三个都原生支持 1.21.1 NeoForge，**结构位冗余充足**，风险低 | ✅ 三者皆 1.21.1 |
| **远征维度** | Dimensional Dungeons (`dimensional-dungeons`) | ❓Twilight Forest（仅 CF，未核实） | 3 | 双端 | 1.21.1 上唯一已核实的维度候选；**且必须先核实它的形态**是否是可探索区域而非一次性副本 | ⚠️ 版本✅ / 形态未核实 |
| **袭击排期（波次引擎）** | **The Hordes** (`the-hordes`) | Horde Nights（数据包，月亮日历 + 仪式召唤） | 4 | 双端 | 外部可控性最好：`/hordes start <len>`、`/hordes spawnWave <n>`、`/hordes stop`；`hordesCommandOnly` 可**关掉自然刷怪、完全由脚本按时代+国力触发**；数据包条件含 `game_stages` 钩子。**不破坏方块**，敌人只锁定玩家 | ✅ 能力面已核实 |
| **拆墙与难度缩放** | **Undead Nights** (`undead-nights`) | — | 4 | 双端 | **唯一有显式拆方块子系统**：`blockBreaking` + `blockBreakingTier` 1–4（4 = 黑曜石）+ 携 TNT 的拆迁僵尸；档位**由难度等级驱动**（默认关闭，需逐级开启）。另有 `dynamicScaling` 按玩家数缩放属性、`numberOfPlayersToGetHordePerHordeEvent` 等多人项。**这才是让城墙成为真工事的那一块** | ✅ 能力面已核实（含源码） |
| **占领区（第二季）** | **Sculk Horde** (`sculk-horde`) | 第一版用手工地牢据点 | 5 | 双端 | 袭击是**地点级**的：成功/失败后该地点进入 `no_raid_zone` 冷却（480 分钟）→ 几乎就是"占领区"的现成实现。但它是**同化型**威胁（把墙转化成侵染方块而非拆除），风格极强、配置极多、单位上限 200，**不进第一版** | ✅ 能力面已核实 |
| **袭击内容多样性** | Illager Invasion (`illager-invasion`) —— **可选** | — | 4 | 双端 | **已降级为内容 mod**：它不生成袭击（寄生原版袭击）、无命令/API、波次数组硬编码不可调。当灾厄村民内容包用，**不当威胁系统** | ✅ 能力面已核实 |
| **任务书** | **FTB Quests (`ftb-quests`)** + FTB Teams / FTB Library | Questlog（降为备选） | 全程 | 双端 | **改用 FTB 的理由**：真任务树（章节 + 依赖 + 奖励 + **团队进度共享**），而 Questlog 只是 checklist 式，撑不起六章国策链。此前选 Questlog **不是因为它更好，而是因为候选池来自 Modrinth 接口、而 FTB 只在 CF 分发**——补上证据后换掉。⚠️ FTB Teams 需配成"全队同队" | ✅ **已实证**：`ftb-quests-neoforge-2101.1.24.jar` 等 6 个（本机两个 1.21.1 NeoForge 成熟包的 jar 文件名即版本证据） |
| **回滚与审计** | GriefLogger (`grieflogger`) + GLRA (`glra`) | Ledger（仅 Fabric）、CoreProtect（仅插件端 🚫） | 全程 | 服务端 | GriefLogger 记录方块改动，GLRA 提供 **rollback 命令**——"玩家误破坏可恢复"是多人服底线 | ✅ GriefLogger 两版本 / GLRA 1.21.1 NeoForge |
| **备份** | Simple Backups (`simple-backups`) | Advanced Backups（至 1.21.4） | 全程 | 服务端 | 备份必须停机或 `save-off` 后做；用 mod 自动化这件事，但**仍要定期验证备份能恢复** | ✅ 1.21.1 NeoForge |

## 二、性能优化组（**第一天就装，按组对照基线**）

这一组是本包选 1.21.1 的**主要原因**：1.20.1 Forge 上 Lithium / C2ME / MoreCulling / Starlight / Krypton 全无 Forge 版，Canary 停更于 1.20.4。

| 作用 | 选型 | 状态 |
|---|---|---|
| 渲染 | Embeddium (`embeddium`) | ✅ 1.21.1 NeoForge |
| 服务端逻辑 | Lithium (`lithium`) | ✅ 1.21.1 NeoForge |
| 区块并发 | C2ME (`c2me-neoforge`) | ✅ 1.21.1 NeoForge |
| 光照 | ScalableLux (`scalablelux`) | ✅ 1.21.1 NeoForge |
| 剔除 | MoreCulling + EntityCulling (`moreculling` / `entityculling`) | ✅ 1.21.1 |
| 内存 | FerriteCore (`ferrite-core`) | ✅ 1.21.1 |
| 加载/修复 | ModernFix (`modernfix`) | ✅ 1.21.1 |
| 界面/包 | ImmediatelyFast (`immediatelyfast`) | ✅ 1.21.1 |
| 实体堆积 | Clumps (`clumps`) | ✅ 1.21.1 |
| 服务端通用 | ServerCore (`servercore`) | ✅ 1.21.1 |
| **崩溃守护** | Neruina (`neruina`) | ✅ 1.21.1 |
| 诊断 | spark (`spark`) | ✅ 1.21.1 |
| 预生成 | Chunky (`chunky`) | ✅ 1.21.1 |

**Neruina 值得单独说**：它能在单个实体/方块实体出错时拦下来，而不是让整个服务器崩。对一个"自动化机器满地跑 + 多人同时在线"的包，这是防止"因为一个坏实体全服回档"的关键。

## 三、否决记录（为什么不用它们）

| 候选 | 否决原因 |
|---|---|
| **Litematica 的 Easy Place / printer** | 🚫 `server_side: unsupported`、客户端模拟点击；官方明文警告**可能被反作弊封禁**。仅允许"投影显示"辅助建造 |
| Modern Industrialization（1.20.1） | 🚫 1.20.1 只有 Fabric 版，Forge 上不可用（1.20.4 起 NeoForge 独占） |
| Simple Shops / Stash | 🚫 只有 1.20.1 Forge 版，**1.21.1 明确为 0 个版本** |
| Numismatic Overhaul | 🚫 仅 Fabric/Quilt，本包用不了（可用 Create: Numismatics 替代） |
| Odyssey Claims / Odyssey Quests | 🚫 止步 1.20.4 或已 unlisted |
| GriefPrevention / CoreProtect | 🚫 插件端（Bukkit/Paper）专属，Forge/NeoForge 服务器不能直接用 |
| Starlight | 🚫 仅 Fabric 且已归档；1.21.1 用 ScalableLux 替代 |
| Rubidium / Canary | 🚫 分别止步 1.20.1 / 1.20.4；1.21.1 用 Embeddium |
| Illager Invasion（当威胁系统用） | 🚫 不生成袭击（寄生原版袭击）、**无命令/API**、波次数组硬编码不可调。降级为可选内容 mod |

## 三·补：威胁能力面核实结论（已核实，含源码证据）

| 问题 | 结论 |
|---|---|
| 谁能拆墙 | **只有 Undead Nights**：`blockBreaking` + `blockBreakingTier` 1~4（4 = 黑曜石）+ 携 TNT 的拆迁僵尸。档位由**难度等级**驱动，默认关闭 |
| 谁能被外部调度 | **The Hordes 最强**：`/hordes start <len>`、`/hordes spawnWave <n>`、`/hordes stop`，`hordesCommandOnly` 可关掉自然刷怪 |
| 能否定向（朝基地推进） | ❌ **两家都不能**：敌人只锁定玩家，围绕玩家 70~75 格生成，没有坐标/方向手段 → **预警不能报方向**，见设计稿 10.1② |
| 会不会被高墙挡住 | ⚠️ `hordeMobsCanClimbEachOther`（叠人梯）**默认开启** → 高墙不是万能解，必须配合火力与通道控制 |
| 谁最像"围城" | **Sculk Horde**：地点级袭击 + 成功/失败后该地点冷却（`no_raid_zone` 480 分钟）+ 末影人侦察前置 + 波次配比。但它是**同化**（把墙变侵染方块）而非拆除，且配置极多、单位上限 200 → 第二季 |
| 怎么让难度跟着时代走 | 用 `/undeadnights difficulty auto_progression disable` 关掉它按 MC 天数的自动推进，再由我们的日历用 `/undeadnights difficulty set <levelIndex>` 接管 |

## 四、仍需补的核实

| # | 待核实 | 怎么补 | 不补的后果 |
|---|---|---|---|
| 1 | ✅ FTB Quests 等 6 个已实证（1.21.1 NeoForge）；仍待查 **Twilight Forest** | 本机参照包的 jar 文件名（`ftb-*-neoforge-2101.*`）；Twilight Forest 需另找渠道 | 维度位可能还缺一个候选 |
| 2 | **FTB Teams 的"全员同队"配置** | 装好后实测 `config/ftb-teams.snbt` | 任务进度无法全队共享，或与 OPAC party 形成两个"队伍"概念 |
| 3 | Dimensional Dungeons 是开放区域还是一次性副本 | 读其文档/试玩 | 阶段 3"出关远征"的设计要改写 |

> 原先的「威胁 mod 强度可调性」已核实完毕，结论见「三·补」。**核实结果反过来改变了设计**：城墙从"可能只能当掩体"变成"可以真被攻破"，而"预警报方向"被证明做不到。

本轮新增：**RoadWeaver 与 C2ME 的兼容是否已修**——作者明写「2.1.0 版本及以前与 C2ME / C2MEF / C3ME 不兼容（道路上出现浮空树）」，而 1.21.1 上最新是 **2.0.9**。查其 GitHub issues/changelog，或实测新世界有无浮空树。**不补的后果：要么牺牲性能组，要么放弃世界路网。**

## 五、落地顺序（按 `modpack-scaffold`）

1. **最小可启动集合**：加载器 + Create + OPAC + 性能组 + 一个结构 mod → **必须真启动一次**（验证这四块能共存，这是最早能暴露冲突的一步）
2. 按功能组加：领地/队伍 → 自动化 → 图纸（Schematicannon + Portable Blueprints）→ 结构三件套 → 维度 → 威胁 → 任务书 → 回滚/备份
3. **每加一组**：启动验证 + 记录基线（帧数 / 启动耗时 / TPS）
4. 全程记录到 `AGENTS.md` 的"已定取舍"表，避免下次会话重新讨论

---

## 六、时代 ↔ 主菜 ↔ mod 映射（选型的真正输入）

> 来源：`DESIGN.md` §17（六个时代的玩法规格，按 **150 小时**口径：12 / 20 / 25 / 25 / 30 / 38）。
> **规则**：每个时代的"主菜"决定**该时代开始前必须装好并验过**哪些 mod——玩家会在那个时代撞上它，**不能开天窗**。

| 时代 | 时长 | 主菜 | 主菜必需 | 状态 |
|---|---|---|---|---|
| 0 落地 | 12h | 协作生存 | 结构 mod（旧世痕迹）· 出生点景观 | YBD ✅ / WDA ⚠️ / D&T ⚠️ |
| 1 立国 | 20h | 领地与治理 | OPAC · Create 物流与保险库 | ✅ **均已装并验过（含联机）** |
| 2 基建 | 25h | 自动化产线 | Create（含列车）· Schematicannon · Portable Blueprints · Effortless Building · WorldEdit | Create ✅ / 其余 **⚠️ 未装** |
| 3 出关 | 25h | 远征 | 结构三件套 · Dimensional Dungeons · 交通等级 | **1/3 装**；维度未核实形态 |
| 4 御敌 | 30h | 威胁防御 | The Hordes · Undead Nights | ❌ **均未装** |
| 5 文明 | 38h | 公共工程 | WorldEdit · 大工程施工手段 | ⚠️ 未装 |

### 6.1 三张清单

**A. 已装已验**（19 个 jar = 性能组 13 + Create 6 + OPAC + YBD + 3 个前置）
其中 **4 个是纯客户端**，不上服务端（见 `tools/lists/server-mods.txt` / `client-only.txt`）。

**B. 已定未装**（12 项）
The Hordes · Undead Nights · FTB Quests 系列 · Dimensional Dungeons · When Dungeons Arise · Dungeons and Taverns · Portable Blueprints · Effortless Building · WorldEdit · RoadWeaver · GriefLogger + GLRA · Simple Backups
→ **按时代分批装**（见 §6.4），不要一次全装。

**C. 仍未选** → ✅ **已解决：`terralith`**（+ 前置 `lithostitched`）
理由：RoadWeaver 作者正文**点名兼容** · **客户端 optional**（不必人人装）· **只用原版方块**（与图纸生成器 / 配色规范 / 建材经济零冲突）。
⚠️ **不能从已有世界移除，也不建议后加到旧世界** → 必须在**开新世界前**就位。

> 📌 **最终清单不在这份表里**：本节是功能位的**决策理由**；**具体装哪些（含 slug 与状态）见 `MODLIST.md` + `modlist.tsv`**，
> 共 104 条、全部通过 `tools/apply_modlist.py --verify` 核验。

### 6.2 本轮新增的核实（都带证据）

| 结论 | 证据 |
|---|---|
| **Create 6 自带完整列车与物流** → 铁路与公共仓库**不需要额外 mod** | `tools/jar_probe.py` → `content/trains/` + `content/logistics/` 命中 **549** 条类；列车语言键 **155** 条（车站 / 信号 / 时刻表）。公共仓库可用 **Item Vault 保险库 + Packager 打包机** 实现 |
| Create 6 中文覆盖 **99%** | `tools/lang_audit.py` → 3619 / 3639 键 |
| **OPAC 无中文**（613 键 0%） | 同上 → 汉化只需给它补一份 `zh_cn` 资源包 |
| 图纸全部有效 | `tools/blueprint_check.py` → 结构 **10/10** · 方块 ID **12/12**（离线，用版本 jar + mod jar 的 blockstates 反查） |

### 6.3 与内容量的接口

`DESIGN.md` §15 已按 150 小时重算：**任务 27 → ~90~110**；**内容 mod 总量 80~120 现在正当**。
但本表的功能位只覆盖了 **~30 个 mod**——**差额已有清单**：见 **`MODPLAN.md`**
（用 `tools/survey_mods.py` 从 Modrinth 拉了 **472 个** 1.21.1+NeoForge 候选，按"服务哪个主菜"分层：
必备基础 / 六个时代的主菜支柱 / 协作治理 / 加厚层 / 排除，并给出数量账与装配顺序）。
那些差额属于"内容层"（群系 / 结构 / 装饰建材 / 食物 / 生活质量 / 交通 / 存储等），**必须先列候选再谈装不装**，否则"80~120"只是一句口号。

### 6.4 落地顺序（按时代排，替换原先的"按功能组加"）

1. **建世界之前必须定**：**群系扩充 = `terralith`（✅ 已定）** · RoadWeaver（要预生成）
2. **时代 0–1 之前**：结构 mod 补齐——**前 32 小时的体验决定整包死活**
3. **时代 2 之前**：Portable Blueprints / Effortless Building / WorldEdit——玩家摸到自动化之前就位
4. **时代 3 之前**：维度（且必须先核实形态）· 战利品与图纸掉落机制需要它
5. **时代 4 之前**：The Hordes + Undead Nights——**设计 B 形态的引擎**，也是最不能开天窗的一项
6. **全程**：GriefLogger + GLRA（回滚底线）· Simple Backups（且必须定期验证可恢复）
7. **每装一批**：跑现有自动套件（`autotest` / `game_agent` / `server_ctl` / `blueprint_check` / `lang_audit`）+ 记 `baseline.csv`

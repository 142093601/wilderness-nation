# 选型计划（MODPLAN）：从 472 个候选里选出本包要的

> ⚠️ **本文是"过程与候选"。定下来的最终清单在 `MODLIST.md`（人读）+ `modlist.tsv`（脚本读）**，
> 且每一条都过了 `tools/apply_modlist.py --verify` 的核验（**104/104 通过**）。要看"到底装哪些"，看 MODLIST。

> **数据源**：`tools/survey_mods.py` 查 Modrinth 搜索接口，筛选 **`1.21.1` + `neoforge` + `project_type:mod`**，按下载量取各类前 60，去重后 **472 个**。
> **原始候选**：`CANDIDATES.md`（按功能位分组，可重跑）。
>
> **纪律**：
> - 本表里"有 1.21.1 NeoForge 版本"**来自接口返回**，不是凭记忆；但**兼容性 / 冲突 / 性能影响必须实测**（工具链已就绪）。
> - 标 ❓ 的是**需要设计决策或实测**才能定的，不是"已选"。

---

## 0. 先把"数量"这件事算清楚

**"200+ mod"通常不是 200 个内容 mod**，而是三部分之和：

| 组成 | 典型占比 | 本包现状 |
|---|---|---|
| 前置库（API / Lib） | 20~30% | 已装 3 个（YungsApi / cloth-config / configurable） |
| 性能与生活质量（QoL） | 15~25% | 已装 13 个性能组，QoL **0** |
| 真正的内容 mod | 50~60% | Create + OPAC + YBD = 3 个 |

所以 **19 jar ≈ 3 个内容 mod + 3 个库 + 13 个性能组**。要走到 200+，缺的主要是**内容层**，而不是库。

**但顺序不能反过来**：先把六个时代的**主菜支柱**装满（那时大约 70~90 个内容 mod + 20~30 个库 + 13 性能 + QoL ≈ **110~140 jar**），**再用 D 层（装饰 / 建材 / 食物 / Create 扩展）加厚到 200+**。先定"200"这个数字会得到一堆没人用的 mod，而不是一个能玩的包。

> 已同步修改 `DESIGN.md` 反目标 #2：把"不做 200+ 堆砌"从**数量限制**改成**指标限制**（TPS / MSPT / 实体上限 / 启动时间）——因为现在这些都能自动测了，用指标防身比用数量防身准确得多。

---

## A. 必备基础层（不装玩不下去）

| mod | slug | 服务什么 | 备注 |
|---|---|---|---|
| **JEI**（已定 ✅，不装 EMI） | `jei` | 配方查询 | 121 个 mod 的包没有配方查询＝劝退。**用户已拍板选 JEI**；EMI 职能重叠，不装 |
| Jade | `jade` | 看方块/实体信息 | 联机友好（"我看的是什么"）；双端 |
| Geckolib / Curios API / Athena / Bookshelf / Sophisticated Core | 各自 slug | 前置库 | 随被依赖的 mod 自动带进来，别手动装 |
| Paxi | `paxi` | 数据包/资源包自动加载 | **打包分发用**：把 datapack / resourcepack 放进文件夹即生效，免手动装 |
| Crash Assistant | `crash-assistant` | 崩溃时给玩家可读提示 | 朋友服很值：崩溃了不是看堆栈，而是看"怎么办" |
| Alternate Current | `alternate-current` | 红石性能 | 服务端；与性能组同性质，**装前要跑基线** |

## B. 六个时代的主菜支柱（核心，按时代排）

### 时代 0 · 落地｜协作生存
| mod | 服务什么 |
|---|---|
| **群系扩充 → 选 `terralith`（已定 ✅）** | ✅ **RoadWeaver 作者原文点名兼容**（"2.0.6+ 与 Tectonic-V2 / 史诗地形 / **Terralith** 兼容，与 Tectonic-V3 不兼容"）；**客户端 optional**（不必逼每个朋友装）；**只用原版方块** → 与图纸生成器、统一配色规范、建材经济零冲突。需前置 `lithostitched`（服务端库）。⚠️ **不能从已有世界移除**，也**不建议后加到旧世界** → 必须在**建世界前**就位 |
| （备选）`biomes-o-plenty` | 50+ 群系（含下界/末地）+ **新植物/花/树/建材**。代价：**客户端 required（每人必装）** · 前置 `glitchcore`+`terrablender` · **RoadWeaver 未点名兼容** · 许可 **All Rights Reserved**。若要它，先验兼容 |
| `starter-kit` | ❓ 开局物资（可选）——但要小心削弱"落地"的生存感，建议只给最基础的几件 |
| `dungeons-and-taverns` · YUNG's 系列 | 出生点附近的"旧世痕迹"（填 §17.7 空缺 #2） |

### 时代 1 · 立国｜领地与治理
| mod | 服务什么 |
|---|---|
| ✅ `open-parties-and-claims`（已装已验） | 领地、队伍、权限 |
| **`patchouli`** ⭐ | **游戏内成书框架 → 直接填 §17.7 空缺 #3「约定的游戏内载体」**。约定书 / 国史 / 建筑手册都能做成"点开就能看的书"，比告示牌更像一本书 |

### 时代 2 · 基建｜自动化产线
| mod | 服务什么 |
|---|---|
| ✅ `create`（已装） | 机械、物流、**列车（已核实：549 类 / 155 语言键）** |
| ⚠️ `portable-blueprints` · `effortless-building` · `worldedit` | 图纸工程制 / 建筑辅助 / 大工程与事故修复（已在 SELECTION 决策表） |
| **Create 生态扩展（十余个）** | ⭐ **时代 2 的内容量主来源**：`createaddition`（电力/线材）· `create-deco` · `create-connected` · `create-diesel-generators` · `create-new-age` · `create-dreams-and-desires` · `create-ore-excavation` · `create-big-cannons` · `slice-and-dice` · `create-central-kitchen` · `create-enchantment-industry` · `interiors` · `bellsandwhistles` · `create-aeronautics`。**同生态冲突风险最低**，比"再加结构 mod"稳得多 |
| 建材变体 | `chipped`（22.4M）· `rechiseled`（+ `rechiseled-create`）→ 同材质多形态，配合图纸生成器能出更多风格，也服务"统一配色规范" |
| 桥梁/屋顶/窗户 | `macaws-bridges` 等 Macaw's 系列 → 服务公共工程（跨海大桥 / 铁路环线） |

### 时代 3 · 出关｜远征
| mod | 服务什么 |
|---|---|
| ✅ `yungs-better-dungeons` + **YUNG's 全家桶**（另外 6~7 个） | ⭐ **低冲突结构扩容**：同一家、同一 API（`yungs-api` 已装）、同一套设计语言。`yungs-better-nether-fortresses` / `-ocean-monuments` / `-mineshafts` / `-jungle-temples` / `-end-island` / `-strongholds` |
| `when-dungeons-arise` · `dungeons-and-taverns` | 已在决策表 |
| ⚠️ `dimensional-dungeons` | 维度位（**形态未核实**——是开放区域还是一次性副本，决定本时代目标怎么写） |
| `natures-compass` · `explorers-compass` | 生物群系 / 结构定位 → 服务"选址"与"远征规划" |
| `xaeros-minimap` + `xaeros-world-map` | 情报墙与地图墙的实现手段（**双端可选，服务端侧要核实共享/同步方式**） |
| `better-archeology` | 考古：挖遗迹得文物 → **与"旧世"设定天然契合**，也给"残破图纸"以外的战利品维度 |

### 时代 4 · 御敌｜威胁防御
| mod | 服务什么 |
|---|---|
| ❌ `the-hordes` | 排期引擎（`/hordes start|spawnWave|stop`、`hordesCommandOnly`） |
| ❌ `undead-nights` | 拆墙与难度档位（`blockBreaking` + `blockBreakingTier` 1~4） |
| `zombie-awareness` ⚠️ **第一版不装** | 把原版怪改成"**靠痕迹找你**"：**血味**（受伤流血，低血量会持续流血被追踪）· **声音**（**挖方块 / 放方块** / 爆炸 / 弓弩 / 吃东西 / 门 / 箱子 / 活塞…**越响影响越大**）· **光源**（远处可见的光源会被调查）· 可用活塞/TNT 引走。**贴合设定到几乎像为本包写的**（"尸潮被活人吸引"、"望风即引怪"、"聚集才安全"）。**但不进第一版**：① **没有时代闸门**（常驻 AI 改动），会破坏"威胁从时代 4 才开始"的节奏；② 与 The Hordes / Undead Nights 都改怪物行为，**三方兼容未验**；③ **更新停在 2024-09-17**（近两年未更），前置 `CoroUtil`。→ **列为时代 4 的验证候选** |
| `guard-villagers` ❓ | 村庄能自保 → 世界"本来有文明"；但要小心变成"NPC 替你打" |
| 军械库/工事图纸 | 走我们自己的图纸生成器（`tools/gen_structures.py`），不需要额外 mod |

### 时代 5 · 文明｜公共工程
| mod | 服务什么 |
|---|---|
| ⚠️ `worldedit` | 大工程施工、地形改造、事故修复（op 工具） |
| Macaw's 系列 · `supplementaries` · `handcrafted` · `another-furniture` | 建筑表现力（也是 D 层的加厚来源） |
| `immersive-paintings` · `joy-of-painting` | ⭐ **国旗、国史插图、地图墙的展示手段**（服务 C 身份认同） |

## C. 强烈建议（服务"多人协作 / 治理 / 身份"）

| mod | 服务什么 | 备注 |
|---|---|---|
| `what-are-they-up-to` (Watut) | 看队友在做什么 → 服务分工可见与"有人掉队"（H4 假设） | |
| `ping-wheel` | 标记/提醒 → 服务集结与协同 | |
| `grieflogger` + `glra` | 回滚底线（已在决策表） | 服务端 |
| `simple-backups` | 备份（且必须定期验证可恢复） | 服务端 |
| `hardcore-revival` ❓ | 倒地救援 → 对抗"团灭" | 与设计的"团灭不读档"需对齐 |
| `comforts` | 睡袋/吊床 | 远征后勤的小工具 |

## D. 可选：加厚层（先把主菜装满，再用它冲数量）

| 方向 | 候选（示例） |
|---|---|
| 装饰建材 | `supplementaries` · `handcrafted` · `another-furniture` · `macaws-furniture` · `macaws-windows` · `macaws-fences-and-walls` · `diagonal-fences` · `twigs` · `cobblefurnies` · `chipped` |
| 食物农业 | `farmers-delight` + 扩展（`expanded-delight` / `oceans-delight` / `enders-delight` / `my-nethers-delight` / `more-delight`）· Let's Do 系列（`lets-do-farm-charm` / `meadow` / `bakery` / `candlelight`）· `smarter-farmers-farmers-replant` · `rightclickharvest` |
| 生活质量 | `mouse-tweaks` · `inventory-profiles-next` · `shulkerboxtooltip` · `controlling` · `searchables` · `crafting-tweaks` · `betterf3` · `modelfix` · `packet-fixer` · `overflowing-bars` · `enchantment-descriptions` |
| 客户端观感 | `embeddedt`（Embeddium 已有）· `sodium-extra` · `reeses-sodium-options` · `iris`（光影，**性能预算要重算**）· `continuity` / `athena-ctm` / `fusion-connected-textures`（连接纹理）· `entitytexturefeatures` · `not-enough-animations` · `3dskinlayers` · `wavey-capes` · `chat-heads` · `distanthorizons`（❓远景，性能影响大） |
| 音效氛围 | `ambientsounds` · `sound-physics-remastered` · `particle-rain` |
| 装备工具 | `curios`（前置）· `travelersbackpack` · `sophisticated-backpacks` · `simply-swords` · `immersive-armors` · `easy-anvils` · `enchanting-infuser` |
| 存储 | `toms-storage` ❓ · `carry-on` · `iron-furnaces` · `ae2` ❓（与 Create 物流**职能重叠**，装了可能削弱"仓库是你们建的"） |

## E. 排除（连同理由）

| 排除 | 理由 |
|---|---|
| `serene-seasons` **已排除** | **与本包自己的日历冲突**：我们有全局累计算日（时代推进），再叠一套季节系统会让"时间"出现两个真相。**用户已确认排除** |
| `veinminer` **已排除** | 连锁挖矿会让**材料经济变松**（设计假设 H7），且会削弱"基建要有成本"这条。**用户已确认排除** |
| `cobblemon` / `rctmod` / `touhou-little-maid` 等 | 是别的包的题材，与"回归者建国"无关 |
| `euphoria-patches` / `physicsmod` / `fancymenu` | 炫技向，性能与稳定性风险不成比例 |
| 货币类（`economy` 类目） | 设计已明确：私人服不上货币，避免"防刷"一整类麻烦 |
| 小游戏 / 猎奇 / PvP 向 | 反目标已排除 |
| 重复职能 | 同一个功能位只留一个（JEI/EMI、地图 Xaero/其他、存储 Create/Tom's/AE2 三选一） |

---

## 本轮的关键发现（会反过来改设计的）

1. **`patchouli` → 直接填上 §17.7 空缺 #3「约定的游戏内载体」**。设计说"没人会读文档，但所有人都会点开任务书"——Patchouli 让"约定"变成一本点得开的书，比告示牌更接近设计想要的东西。
2. **Create 6 自带列车与物流**（已核实，549 类 / 155 语言键）→ 铁路与公共仓库**不需要额外 mod**，选型清单因此变短。
3. **YUNG's 全家桶是低冲突的结构扩容路径**——同 API（`yungs-api` 已装）、同设计语言，比混装三家结构 mod 稳。
4. **Create 生态的十几个扩展 = 时代 2 的内容量主来源**。这跟"不要靠加结构 mod 补内容"不矛盾：它们直接服务主菜（自动化），而不是"多几个地牢"。
5. **RoadWeaver 的兼容名单是公开的 → 群系扩充那道门槛有答案了**：作者正文原文点名"2.0.6+ 与 Tectonic-V2 / 史诗地形 / **Terralith** 兼容，与 Tectonic-V3 不兼容"。所以 **`terralith` 可以直接定**；BoP **未被点名**，属未验。

## 本轮已定（用户拍板）

| 事项 | 决定 |
|---|---|
| 配方查询 | **JEI**（不装 EMI） |
| 语音类 mod | **不装**（`simple-voice-chat` / `plasmo-voice` 全部移除） |
| 群系扩充 | **`terralith`**（+ 前置 `lithostitched`）——按我的建议定；除非你要 BoP 的建材 |
| `veinminer` | **排除**（松化材料经济，打掉 H7 假设） |
| `serene-seasons` | **排除**（与本包自己的日历冲突，时间出现两个真相） |

## 仍待你确认的一件事

**`zombie-awareness` 要不要在时代 4 上**（第一版不装）。它是**最贴合"尸潮"设定的一个 mod**，但没有时代闸门、且更新停在两年前。
我的建议：**第一版不装，到时代 4 真机验证时再决定**——那时能实测它对"建造活动"的压力到底是有趣还是折磨。
6. **群系扩充定了：`terralith`**（详见 B 表）。硬门槛（RoadWeaver 兼容）由作者正文直接回答——**点名兼容 Terralith、不兼容 Tectonic-V3**；BoP 未被点名，属未验。

## 数量账（分层计数）

| 层 | 估算 | 累计 |
|---|---|---|
| 已装 | 19 jar（含 13 性能 + 3 库 + 3 内容） | 19 |
| A 必备基础 | +6~8（JEI/EMI、Jade、Paxi、CrashAssistant、AlternateCurrent 等） | ~27 |
| B 主菜支柱 | +35~45（群系 2~3、结构 8~10、威胁 2~4、维度 1、图纸链 3、Create 扩展 10~14、任务书 1、展示 2~3） | **~70** |
| C 协作治理 | +5~7（Watut、Ping、审计 2、备份、救援；语音类已排除） | **~77** |
| 前置库 | +15~25（随依赖自动进） | **~100** |
| D 加厚层 | +50~80（装饰建材 ~15、食物 ~10、QoL ~12、客户端观感 ~15、音效 3、装备 6、存储 3） | **~160~180** |
| 继续加厚 | 更多装饰/建材/食物/Create 扩展 | **200+** ✅ 可到 |

**结论：200+ 完全到得了，但按这个顺序到——先把 A+B+C 装满（约 78 个内容 mod、~100 jar），再用 D 层加厚冲数量。** 每一批都要跑一次自动套件 + 记 `baseline.csv`。

## 装配顺序（对接六个时代）

1. **建世界之前必须定**：群系扩充 = **`terralith`（已定）** + RoadWeaver 自身兼容性（正文已确认与 Terralith 兼容；**C2ME 那条正文未提，仍需再核**）
2. **时代 0–1 之前**：A 层基础（**JEI**、Jade、Paxi、CrashAssistant）· Patchouli（约定载体）
3. **时代 2 之前**：Portable Blueprints / Effortless Building / WorldEdit · Create 生态扩展一批 · Chipped/Rechiseled
4. **时代 3 之前**：YUNG's 全家桶 + WDA + D&T · 维度（先核实形态）· Nature's Compass · 地图 mod
5. **时代 4 之前**：The Hordes + Undead Nights（**最不能开天窗的一项**）· Zombie Awareness 待此时验证
6. **全程**：GriefLogger + GLRA · Simple Backups
7. **每批之后**：`autotest` 冒烟 → `server_ctl --script tests/server_v0.txt` → `blueprint_check` / `lang_audit` → 记 `baseline.csv`

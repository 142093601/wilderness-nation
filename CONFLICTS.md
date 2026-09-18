# 冲突与问题总账（CONFLICTS.md）

> **这份文件的目的**：把"装机过程中真实撞到的冲突/缺陷"逐条记下来——每条都带**取证过程**与**接口证据**，
> 而不是"我感觉不兼容"。它回答的问题是：**这 187 条选型里，哪些装不到一起、哪些会崩、哪些需要改**。
>
> **范围**：只做"能跑起来 + 冲突清账"。**具体魔改（配方/数值/配置调参）不在本轮**，见文末「待魔改清单」。
>
> **状态**：进行中 —— 批 1 已装并验完（2026-09-19），批 2~5 待装。

---

## 摘要

| # | 问题 | 严重度 | 处置 |
|---|---|---|---|
| C1 | `certain-questing-additions` 让**专用服务端无法启动**（连带 FTB Quests 崩） | 🔴 致命（承重墙） | 改 `side="client"` ✅ 已修 |
| C2 | `Terralith 2.5.8` × `lithostitched 1.8.0+beta6` 错配 → **客户端开世界卡死** | 🔴 致命（开局即废） | pin Terralith **2.6.2** ✅ 已修 |
| C3 | `stellarcreateoptimization` 硬依赖 `sodium 0.6.9+`，本包用 `embeddium` | 🟠 装了就崩 | 改 `skip` ✅ 已改 |
| C4 | `byepregen` 与 `noisium` **显式不兼容**（mod 自己声明） | 🟠 装了就崩 | 取 `noisium`，`byepregen` 改 `skip` ✅ 已改 |
| T1 | 12 条 slug 取自 jar 文件名，与项目真 slug 不符 | 🟠 装机必失败 | 全部修正 + 加核验关卡 ✅ 已修 |
| T2 | packwiz 的 `side` 字段不可信（纯客户端 mod 标成 `both`） | 🟠 服务端会被污染 | 用 `side_check.py` 复核并修正 ✅ 已修 |
| T3 | `--batch` 落盘不含自动解析的依赖 → 缺 jar 启动崩 | 🟡 流程 | 改为无参数落盘 ✅ 已修 |
| T4 | 跨批次依赖：`xaeroplus` 需要 `xaeros-worldmap`（在批 4） | 🟡 流程 | `xaeroplus` 移到批 4 ✅ 已修 |

---

## C1 · `certain-questing-additions` 让专用服务端起不来（🔴 致命）

**现象**：`server_ctl.py --start` 秒退（exit code 0），日志：
```
Failed to start the minecraft server
  - FTB Quests (ftbquests) has failed to load correctly
  - ExtraQuests [FTB Quests] (extraquests) has failed to load correctly
Exception message: Attempted to load class net/minecraft/client/player/LocalPlayer for invalid dist DEDICATED_SERVER
```

**根因（堆栈直接指名）**：
```
java.lang.NoClassDefFoundError: net/minecraft/client/renderer/MultiBufferSource
  at ftblibrary .../icon/Icon.cqa$getEntityIconOrFallback(Icon.java:530)
  at ftblibrary .../icon/Icon.handler$bka000$certain_questing_additions$cqa$getEntityIcon(Icon.java:518)
  at ftblibrary .../icon/Icons.<clinit>(Icons.java:7)
  at ftbquests  .../quest/QuestObjectBase.<clinit>(QuestObjectBase.java:47)
```
mixin 处理器名里写着来源：**`certain_questing_additions`** 往 FTB Library 的 `Icon.getIcon` 注入，
其静态初始化触碰**客户端类** `MultiBufferSource`。专用服务端没有该类 → FTB Library 静态初始化失败
→ FTB Quests 连带失败 → **服务端拒绝启动**。

**为什么它进了服务端**：packwiz 元数据给它写的是 `side = "both"`，但 `tools/side_check.py`
（按 jar 内容离线判定）判它是**纯客户端**。

**影响面**：这不只是本机测试的问题 —— packwiz 的 `side` 就是**真实服务端管理员用 `-s server` 安装时的依据**，
写错会让所有人的服务端都被污染。

**修法**：`pack/mods/certain-questing-additions.pw.toml` → `side = "client"`（同时修了 `just-enough-resources-jer`、`xaeroplus`）。
**验证**：改完后服务端 `Done (131.336s)!` 正常启动，且生成了世界。

---

## C2 · Terralith 版本错配导致客户端开世界卡死（🔴 致命）

**现象**：游戏正常到主菜单（~44 秒），quickPlay 开世界时**无声卡住**：
`saves/<世界>/session.lock` 被写过，但 `latest.log` 里**既没有** `Starting integrated minecraft server`
**也没有** `Stopping`；Windows 事件日志**无 Java 崩溃记录**；JVM 无致命错误输出。→ **是挂起，不是崩溃**。

**取证阶梯（每一步一次实机启动）**：

| 配置 | 结果 |
|---|---|
| 世界生成三件（terralith + lithostitched + roadweaver）全撤 | ✅ 进世界 |
| 只放回 terralith + lithostitched（无 roadweaver） | ❌ 卡住 |
| 只放回 roadweaver（无 terralith/lithostitched） | ✅ 进世界 |
| 只放回 lithostitched | ✅ 进世界 |
| **换成 Terralith 2.6.2（保留 lithostitched + roadweaver）** | ✅ **进世界（完整 60 mod）** |

**根因**：我们装的是 **`Terralith 2.5.8`（2025-01-15）**，而它的依赖 `lithostitched` 被解析成
**`1.8.0+beta6`（2026-09-06）** —— **相差 20 个月**。而且 `2.5.8` 的元数据**不声明** lithostitched 依赖
（所以我们才手工把它加进清单），`2.6.2` 才正式声明。
根子在 **packwiz 的选版逻辑挑了旧版**：Terralith 在 1.21.1 上其实有 `2.6.2`（2026-06-09，release，neoforge 专用构建）。

**修法**：手动 pin `pack/mods/terralith.pw.toml` → 2.6.2（`version = IY93YaEe`，带 sha512）。
**验证**：用 **2.6.2** 由服务端重新生成世界 → 客户端完整 60 mod 成功进入。

> **一条沉淀下来的规则**：**世界生成类 mod 不能只核对"1.21.1 有版本"，还要核对"与它的前置库版本同期"**。
> 20 个月的错配不会报错，只会静默卡死。

---

## C3/C4 · 两条选型冲突（装机冒烟实测，非推测）

- **C3 `stellarcreateoptimization`**：FML 报 `requires sodium 0.6.9 or above`，`Actual version: [MISSING]`。
  本包渲染器选的是 **`embeddium`**（Sodium 的 Fork，**mod id 不同**）→ 永远满足不了。→ 改 `skip`。
  要用它就必须整体改用 Sodium 渲染器（连带 Iris 等，属另一项决策）。
- **C4 `byepregen` × `noisium`**：FML 报 `Mod 'byepregen' is incompatible with 'noisium'`（mod 自己声明的 incompatibility）。
  两者都是世界生成性能优化 → 取 `noisium`（跨整合包共识里采用更广），`byepregen` 改 `skip`。

---

## T 系列 · 工具链/元数据缺陷（已修，防复发）

- **T1 错 slug（12 条）**：参照包 jar 文件名会把连字符去掉（`sophisticatedbackpacks-1.21.1-…`），
  而项目 slug 是 `sophisticated-backpacks` → "参照包实证"只查文件名包含关系，于是错 slug 全部通过核验。
  已修 12 条（如 `twilightforest`→`the-twilight-forest`、`jecharacters`→`justenoughcharacters`、
  `elevatorid`→`openblocks-elevator`），并**加关卡**：参照包命中后必须确认 slug 在接口上真实存在。
- **T2 `side` 字段不可信**：见 C1。用 `side_check.py` 复核，已修正 3 条。
- **T3 `--batch` 落盘陷阱**：`apply_modlist --batch N --apply` 只写批次内的元数据，但 packwiz 会把
  **依赖**也写进 `pack/mods/` → 只按 batch 落盘就会缺依赖 jar（`architectury`、`extralib`）→ 启动崩。
  正确流程：apply 之后**无参数**跑 `materialize_pack.py`。
- **T4 跨批次依赖**：`xaeroplus` 硬依赖 `xaeros-worldmap`（原在批 4）→ 把它移到批 4，避免批 1 就断链。

---

## 取证经验（写给以后的自己）

1. **"崩了"和"卡死了"是两类问题**：崩溃看日志/崩溃报告；**卡死只能向 JVM 要线程转储**
   （`tools/hang_triage.py` 已就绪）。本轮的"卡死"没有异常、没有崩溃报告、没有系统事件 —— 全靠隔离实验定位。
2. **`Crash Assistant` 的"已崩溃但未生成崩溃报告"是误报**：它是游戏启动时拉起的**独立看守进程**
   （日志里有 `Parent PID` 与模组列表快照），游戏被超时回收后它也会弹窗。
3. **packwiz 的 `side` 与 slug 都不能全信**，装机前要用 `side_check.py` 与"接口存在性检查"各过一遍。
4. **世界生成 mod 要同时核对"前置库的版本同期性"**（见 C2）。
5. **测试世界必须用"当前 mod 集"生成**：Terralith 这类 mod 不能后加到旧世界，我们用它自己的服务端
   生成世界再拷给客户端 —— 这条流程现在是自动化的。

---

## 待魔改清单（本轮**不做**，留待之后）

> 用户明确说"具体的魔改可以留置一段时间再说"。这里只登记，不动手。

- 配方与产线衔接（Create 产线 ↔ 图纸材料 ↔ 食物链）
- 时代闸门（哪些内容在哪个时代解锁；工人系统与 Create 的重叠怎么切）
- 威胁数值（国力 → 难度映射、袭击规模曲线）
- 军队供养与兵力上限（机制空缺 #8）
- 据点的收益与重新占据节奏（机制空缺 #9）
- 中文化补全（OPAC 613 条等）
- `ftb-teams` 全员同队配置、`chunk-plan` 配额参数

---

## 待装批次进度

| 批 | 内容 | 状态 |
|---|---|---|
| 1 地基（33 条） | 世界生成三件 + 引擎 + 性能 + 中文化 + 运维 | ✅ 已装已验（修掉 C1~C4） |
| 2 世界内容（29 条） | 结构 / 维度 / 村庄 / 中世纪 / 据点结构 | ⏳ 待装 |
| 3 立国与基建（61 条） | Create 生态 / 图纸系 / 建材装饰 / 交通 / 食物农业 | ⏳ 待装 |
| 4 远征与生活（56 条） | 地图背包定位 / 战利品遗物 / QoL / 视觉音效 | ⏳ 待装 |
| 5 御敌与文明（8 条） | 威胁 / 军事单位 / 攻城 / 大工程 | ⏳ 待装 |

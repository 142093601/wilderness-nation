# 冲突与问题总账（CONFLICTS.md）

> **目的**：把装机过程中**真实撞到**的冲突/缺陷逐条记下来——每条都带**取证过程**与**接口证据**，
> 而不是"我感觉不兼容"。它回答：这 197 条选型里，**哪些装不到一起、哪些会崩、哪些需要改**。
>
> **范围**：只做"能跑起来 + 冲突清账"。**具体魔改（配方/数值/配置调参）不在本轮**，见文末「待魔改清单」。
>
> **进度**：197 条全部装完。
> **⚠️ 2026-09-19 傍晚重大更正**：批 4 与批 5 的"验收通过"曾是**假阳性**（客户端从未进世界，见 C13）。
> **已修好并用新判据重验**：16:13~16:24 四次运行（b1,b2 → 87 jar · b1~b3 → 157 · b1~b4 → 218 · b1~b5 → **231**）**全部 PASS**，
> 且每次都满足"关卡加载完成 + 本次零新崩溃报告"。批 1~3 的原结论因此**被证实**；批 4/批 5 的结论由这次重验取代。
> 因此"客户端 236 jar 进世界 PASS"这句**不成立**，已改。
> **服务端 198 jar · `Done (28s)`** 是另一条腿（不含客户端渲染路径），暂视为成立，但客户端修好后要复验一次联机。
> 最后更新 2026-09-19（傍晚）。

---

## 一、结论摘要

| # | 问题 | 严重度 | 处置 |
|---|---|---|---|
| **C1** | `certain-questing-additions` 让**专用服务端无法启动**（连带 FTB Quests 崩） | 🔴 致命（承重墙） | 改 `side="client"` ✅ **已修并验证** |
| **C2** | `Terralith 2.5.8` × `lithostitched 1.8.0+beta6` 错配 → **客户端开世界卡死** | 🔴 致命（开局即废） | pin Terralith **2.6.2** ✅ **已修并验证** |
| **C5** | `stoneholm-forge` **进世界即崩**（NPE，有崩溃报告） | 🔴 致命 | 改 `skip` ✅ |
| **C6** | `deeperdarker` **加它就进不去世界**（配对协议复验） | 🔴 致命 | 改 `hold` ⏳ 机制待查 |
| **C7** | `the-twilight-forest` **加它就进不去世界**（安全方向复验） | 🔴 致命 | 改 `hold` ⏳ 机制待查 |
| **C8** | `create-aeronautics` → `sable`（内嵌 `veil`）与 **`embeddium` + `scalablelux` 双硬冲突** | 🔴 致命 | 改 `hold` ✅ 证据完整 |
| **C9** | 全配对模式（同进程内先跑服务端再跑客户端）→ 客户端在 **mod 加载阶段**静默死亡 | 🟢 **已定论：测试流程问题，非 mod 冲突** | 改用**两步流程**（先生成世界、再单独跑客户端）✅ 已验证 |
| **C10** | `sodium-extra` / `reeses-sodium-options` 把 **`sodium`** 作为依赖拉进来 → 与我们的 **`embeddium`** 互斥 | 🔴 致命 | 两者改 `hold`，`sodium` 移出 pack ✅ |
| **C11** | **3 个纯客户端 mod 被装进了服务端**（`xaeroplus` / `moreculling` / `particle-rain`）：pack 元数据写 `side="both"`，而 Modrinth 官方标注是**仅客户端** | 🟠 服务端污染 | 改 `side="client"` + 从 `server/mods` 挪出（201 → **198**）✅ |
| **T14** | **pack/ 不包含那 16 个"已装"mod**（embeddium / scalablelux / OPAC / Create / 性能组…）→ pack 不能独立复现环境，也是 C10 的根源 | 🟠 结构性 | 全部纳入包（**批 0**，版本零漂移）✅ **已修并验证** |
| **T15** | **过期依赖图会静默抽走前置库**：`packwiz add` 不写依赖段，图没重建 → `configurable` 被测试器搬走 → 报 `Mod neruina requires configurable`（**看起来像真冲突的假冲突**） | 🟠 方法论 | 重建依赖图 + 测试器加**图过期绊线**（返回码 6）✅ |
| **T16** | **换行符会让 pack 失去可复现性**：本机 `core.autocrlf=true` 且仓库无 `.gitattributes` → 新克隆时 `pack/**` 被转成 CRLF → `pack/index.toml` 里的哈希全部失配，别人 `packwiz install` 直接失败 | 🟠 可复现性 | 新增 `.gitattributes`，`pack/**` 标 `-text`（字节精确）✅ |
| **T17** | **`index.toml` 里有一个陈旧哈希**：C2 手工 pin Terralith 后没 `packwiz refresh` → 安装器在 terralith 这一步必然失败（**本机不读 index.toml，所以永远看不到**） | 🟠 可复现性 | `packwiz refresh`（已确认幂等）✅ |
| **C12** | **客户端开世界即崩**：`InventoryProfilesNext 2.2.5` 在 `Minecraft.doWorldLoad` 时 `ConfigScreenSettings.<clinit>` NPE（libIPN 的 delegate 为空）→ **客户端进不了任何世界** | 🔴 致命（客户端全线瘫痪） | 三次复现；排除 Connector/FFAPI/continuity/陈旧配置后仍崩 → `inventory-profiles-next` 改 **`hold`**，移除后客户端正常加载世界 ✅ |
| **C13** | **验收协议假阳性（方法论）**：判据 `Starting integrated minecraft server` 在**开世界早期**就写，之后崩溃仍被判 PASS → **批 4、批 5 的"验收通过"是假的** | 🔴 致命（结论不可信） | 判据加"关卡加载完成"证据 + "本次零新崩溃报告"硬闸门；旧结论待重验 ✅ |
| **T18** | **`hold` 的 mod 被依赖拖进 pack，还混入了 Fabric 侧 mod**：批 4 选了 `continuity`（Fabric 侧，与已装 `fusion` 重复），它 `required connector` → packwiz 把 `connector` 写进 pack 并装入客户端（连带 FFAPI） | 🟠 流程 | `continuity` 改 `skip`；`connector`/FFAPI 移出；给 `apply_modlist.py` 加两道防线（拒 Fabric 侧 mod、拒被依赖带进来的 hold/skip）✅ |
| **C3** | `stellarcreateoptimization` 硬依赖 `sodium 0.6.9+`，本包用 `embeddium` | 🟠 装了就崩 | 改 `skip` ✅ |
| **C4** | `byepregen` 与 `noisium` **显式不兼容**（mod 自己声明） | 🟠 装了就崩 | 取 `noisium`，`byepregen` → `skip` ✅ |
| **T1** | 12 条 slug 取自 jar 文件名，与项目真 slug 不符 | 🟠 装机必失败 | 全部修正 + 加核验关卡 ✅ |
| **T2** | packwiz 的 `side` 字段不可信（纯客户端 mod 标成 `both`） | 🟠 服务端被污染 | 用 `side_check.py` 复核修正 ✅ |
| **T3** | `--batch` 落盘不含自动解析的依赖 → 缺 jar 启动崩 | 🟡 流程 | 改为无参数落盘 ✅ |
| **T4** | 跨批次依赖：`xaeroplus` 需要 `xaeros-worldmap`（在批 4） | 🟡 流程 | `xaeroplus` 移到批 4 ✅ |
| **T5** | 升版后旧 jar 残留 → 同一 mod 两个版本 | 🟡 流程 | `materialize --prune` ✅ |
| **T6** | **验证方法本身出过假结论**（见第五节，含一次 30 分钟的误判） | 🟠 方法论 | 改用「配对测试」协议 ✅ |
| **T7** | **判了 hold/skip 的 mod，jar 撤不出去**：元数据从 `pack/` 移除后，测试器既不在批次表里、也拿不到文件名 → 永久残留污染每一轮测试 | 🟠 方法论 | 新增**隔离清单** `tools/lists/quarantine.tsv`（不管状态，一律挪出）✅ |
| **T8** | **前置库被误当"多余"撤掉**：测试器若只按批次 slug 保留，包里的自动依赖（architectury/yungs-api/puzzleslib…）会被撤走 → `Mod ftbquests requires architectury` 启动崩 | 🟠 方法论 | ① 新增 `data/depgraph.py`（依赖闭包，含 CF 依赖与 CF id → 我们 slug 的映射）② 新增 `--all-keep`（顺序装机下验证最新一批=保留 pack 全部条目）✅ |
| **T9** | **同一 jar 文件名被两个 slug 引用**（`yungs-api` 与 `yungs-api-neoforge` 指向同一个 jar）→ 逐个 slug 处理时互相覆盖 | 🟠 方法论 | `set_side` 改为按**文件名集合**决策 ✅ |
| **T10** | **从 pack 移除 mod 不会删掉实例里的 jar**（`sable`/`create-aeronautics` 移除后仍在 mods 目录里 → 继续崩） | 🟠 流程 | 记入隔离清单 + 手工清理；`materialize --prune` 只管同项目换版，不管"整个 mod 被移除" ⏳ 待工具化 |
| **T11** | `--prune` 的"按第一个数字切分"启发式把 `supermartijn642corelib` 当成 `supermartijn642configlib` 的旧版**误删**（名字里带数字） | 🔴 自伤 | 改为**按项目 id 记账**（`data/materialize-manifest.json`）✅ |

**四条硬规律**（都是这次撞出来的，见第三节）：
**R1 世界与 mod 集必须匹配**（缺 mod 时开世界**静默失败**，没有任何报错）·
**R2 世界生成类 mod 之间要核对版本同期性**（错配不报错，只卡死）·
**R3 影响世界生成的 mod 有硬顺序**（必须建世界前就位）·
**R4 测试器搬走的 jar 必须能被解释**（"缺 mod"先查 `held-back/`，别急着当冲突）。

---

## 二、逐条详情

### C1 · `certain-questing-additions` 让专用服务端起不来（🔴 已修并验证）

**现象**：`server_ctl.py --start` 秒退（exit code 0）：
```
Failed to start the minecraft server
  - FTB Quests (ftbquests) has failed to load correctly
  - ExtraQuests [FTB Quests] (extraquests) has failed to load correctly
Exception message: Attempted to load class net/minecraft/client/player/LocalPlayer for invalid dist DEDICATED_SERVER
```
**根因（堆栈直接指名）**：
```
java.lang.NoClassDefFoundError: net/minecraft/client/renderer/MultiBufferSource
  at ftblibrary …/icon/Icon.cqa$getEntityIconOrFallback(Icon.java:530)
  at ftblibrary …/icon/Icon.handler$bka000$certain_questing_additions$cqa$getEntityIcon(Icon.java:518)
  at ftbquests  …/quest/QuestObjectBase.<clinit>(QuestObjectBase.java:47)
```
mixin 处理器名里写着来源：**`certain_questing_additions`** 往 FTB Library 的 `Icon.getIcon` 注入，
静态初始化触碰**客户端类** → 专用服务端没有该类 → FTB Library 崩 → FTB Quests 连带崩。

**为什么它进了服务端**：packwiz 元数据写 `side = "both"`，而 `side_check.py`（按 jar 内容离线判定）判它**纯客户端**。
**影响面**：`side` 就是真实服务端管理员用 `-s server` 安装的依据 → 写错会让所有人的服务端被污染。

**修法**：`side = "client"`（同修 `just-enough-resources-jer`、`xaeroplus`）。
**验证**：服务端 `Done (131.336s)!` 正常启动并生成世界。

### C2 · Terralith 版本错配导致客户端开世界卡死（🔴 已修并验证）

**现象**：到主菜单（~44 秒）后开世界**无声卡住**：`session.lock` 被写过，但日志里**既没有**
`Starting integrated minecraft server` **也没有** `Stopping`；**无异常、无崩溃报告、无系统崩溃事件** → 是挂起不是崩溃。

**取证阶梯**（每步一次实机启动）：三件全撤 ✅ / 只留 terralith+lithostitched ❌ / 只留 roadweaver ✅ /
只留 lithostitched ✅ / **换成 Terralith 2.6.2 ✅**（完整 60 mod 进世界）。

**根因**：装的是 `Terralith 2.5.8`（2025-01），依赖 `lithostitched` 被解析成 `1.8.0+beta6`（2026-09），**相差 20 个月**；
且 2.5.8 不声明 lithostitched 依赖（所以我们才手工加），2.6.2 才正式声明。
根子在 packwiz 选版挑了旧版——1.21.1 上其实有 2.6.2（release，neoforge 专用构建）。
**修法**：pin `pack/mods/terralith.pw.toml` → 2.6.2（`version = IY93YaEe` + sha512）。**验证**：配对新世界进世界成功。

### C5 · `stoneholm-forge` 进世界即崩（🔴 有崩溃报告）

```
java.lang.NullPointerException: Cannot invoke "…StructurePoolAccessor.getRawTemplates()" because "primaryPoolAccessor" is null
  at stoneholm …util/StructurePoolUtils.appendPool(StructurePoolUtils.java:19)
  at stoneholm …Stoneholm.onServerStarting(Stoneholm.java:58)
  at … IntegratedServer.initServer(IntegratedServer.java:81)
```
它的 `StructurePoolAccessor` mixin 没生效 → NPE → 服务器 tick 循环异常。
1.21.1 上已是最新（2.1.2）→ 改 `skip`（可选内容，损失小）。
崩溃报告：`crash-2026-09-19_03.33.53-server.txt`。

### C6 / C7 · 两个维度 mod 进不去世界（🔴 已复验，机制待查）

| mod | 版本 | 取证 | 结论 |
|---|---|---|---|
| `deeperdarker` | 1.4.1（1.21.1 最新，2026-06） | ① 只加它 → FAIL ② **配对协议**（服务端/客户端/世界三者同集）→ FAIL ③ 去掉 `terralith`+`lithostitched` 只加它 → 仍 FAIL | → `hold` |
| `the-twilight-forest` | 4.8.3345（1.21.1 最新，2026-03） | 安全方向复验（基准世界 ⊂ 客户端 mod）：只加它 → FAIL | → `hold` |

**共同点：两个都是维度 mod**（新增 dimension）。但也可能是它们与某个基础 mod 的交互 →
**尚未定位到机制**，所以先 `hold` 而不是 `skip`。替代品：`imensional-dungeons`（hold，形态未核实）。
> ⚠️ 早期一度把"暮色森林"当作唯一凶手，但当时用的验证方法有缺陷（见 T6），因此 **C6/C7 是重验后的结论**。

### C8 · `create-aeronautics` 与渲染器/光照引擎双硬冲突（🔴 证据完整）

**现象**：批 3 加进来后，客户端**启动即崩**（有崩溃报告 `crash-2026-09-19_06.55.02-fml.txt`）：

```
-- Mod loading issue for: veil --
  Mod file: .../mods/sable-neoforge-1.21.1-2.0.5.jar#478!/META-INF/jarjar/veil-neoforge-1.21.1-4.3.2.jar
  Failure message: Mod veil is incompatible with embeddium any
      Currently, embeddium is 1.0.15+mc1.21.1
      The reason is: Veil supports Sodium 0.8.12-alpha.2+mc1.21.1 and above
-- Mod loading issue for: sable --
  Failure message: Mod sable is incompatible with scalablelux any
```

**依赖链（从日志与依赖图读出来的）**：
`create-aeronautics` → 必需 `sable`（物理系统）→ ① `sable` 声明与 **`scalablelux`（我们的光照引擎）** 不兼容；
② `sable` **内嵌 `veil`（jar-in-jar）**，而 `veil` 要求 **`sodium 0.8.12-alpha+`**，与 **`embeddium`（我们的渲染器）** 不兼容。

**处置**：`create-aeronautics` → `hold`。要用它就得**同时换掉渲染器与光照引擎**（改用 Sodium 栈），
为一个"载具"内容 mod 动基础设施，代价大于收益。替代：Create 自带列车/物流 + `waystones`（都在清单里）。

> **这条是系统性的**：Create 生态的新附属越来越依赖 **Sodium + Veil 渲染栈**。
> 下一批（批 4）里 `sodium-extra`/`reeses-sodium-options` 也是 Sodium 系 → **渲染器选型（embeddium vs sodium）
> 迟早要重新决策一次**。已记入「待魔改清单」。

### C9 · 全配对模式让客户端静默死亡（🟢 已定论：测试流程问题，不是 mod 冲突）

**现象**：用"全配对"模式（同一进程里先起服务端生成世界、紧接着起客户端）时，客户端**在 mod 加载阶段静默死亡**
—— 日志停在版本检查/`modloading-worker` 构造期，**从未到达主菜单**（`Game took …` 那行都没出现）；
无崩溃报告、无 hs_err、Windows 事件日志无记录、无 OOM。四次全配对运行 **4/4 失败**。

**判定过程（关键对照实验）**：

| 实验 | 客户端 mod 集 | 世界 | 结果 |
|---|---|---|---|
| 全配对 | 140 jar（批 1+2+3 闭包） | 用同一套 mod 生成 | ❌ FAIL ×4 |
| **复用世界（两步）** | **同样 140 jar** | 同一套 mod 生成的世界，单独拷入 | ✅ **PASS** |
| 复用世界 + 批 1+2 世界 | 140 jar | 批 1+2 生成 | ✅ PASS |

**逻辑**：客户端**死在 mod 加载阶段**，那时**还没读世界** → 世界内容不可能影响它；而客户端 mod 集在两种模式下
逐 jar 比对 **0 缺失 0 多出**。所以差异只能来自"同一次运行里先跑过 6 分钟服务端"这件事本身
（资源/句柄/时序），**与 mod 无关**。

**修法（已落到测试器）**：
1. `data/paired-test.py` 加 `settle()`：客户端测试前等 java 进程归零、空闲内存回到阈值以上；
2. **首选两步流程**：`paired-test` 只负责生成世界 → 再用 `--reuse-world <世界目录>` **单独**跑客户端。
   两步流程本轮连续通过（08:04、以及批 1+2 的 07:32）。

**教训**：这类"环境/流程"问题会伪装成"某个 mod 有问题"，且症状与真冲突一模一样（启动期静默死亡）。
→ 判定顺序应当是：**先排除验证流程，再怀疑 mod**。

### C10 · Sodium 系 mod 与我们的渲染器互斥（🔴 证据完整）

**现象**：批 4 加进来后，客户端**启动即崩**（`crash-2026-09-19_09.04.28-fml.txt`）：

```
Mod file: .../mods/embeddium-1.0.15+mc1.21.1.jar
  Failure message: Mod embeddium is incompatible with sodium 0 or above
Mod file: .../mods/sodium-neoforge-0.8.13+mc1.21.1.jar#417!/META-INF/jarjar/net.caffeinemc.sodium-neoforge-0.8...
  Failure message: Mod sodium is incompatible with embeddium 0.0.1 or above
```

**依赖链**：批 4 的 `sodium-extra` 与 `reeses-sodium-options` 声明依赖 **`sodium`** → packwiz 自动把它写进 pack
→ 与实例里的 **`embeddium`**（Sodium 的 Fork）互斥。

**处置**：`sodium-extra` / `reeses-sodium-options` → `hold`；`sodium` 从 pack 移除。

#### ⚠️ 由此浮出的决策点：**渲染器选型（embeddium vs sodium）**

三处冲突全部指向同一个选择：

| 冲突 | 若改用 Sodium | 若保持 embeddium |
|---|---|---|
| **C3** `stellarcreateoptimization`（硬依赖 sodium） | ✅ 可用 | ❌ 不可用 |
| **C8** `create-aeronautics` → `sable` → `veil`（要求 sodium） | ✅ 可用（但 `sable`×`scalablelux` 仍冲突） | ❌ 不可用 |
| **C10** `sodium-extra` / `reeses-sodium-options` | ✅ 可用 | ❌ 不可用 |

也就是说：**换到 Sodium 栈能解锁 4 个 mod，代价是替换渲染器（并可能连带光照引擎）**。
本轮不做这个决定（属"魔改/基础设施"范畴），只把它登记为**待决策点**，并保持现状（embeddium）。

### T14 · pack/ 与实例不一致（🔴 已修并验证）

**事实**：`pack/` 里只有批次 1~4 的 plan 条目与它们的依赖；而实例里有 **16 个"已装"mod 不在 pack 里**：
性能组（embeddium / lithium / ferrite-core / modernfix / entityculling / immediatelyfast / clumps /
servercore / neruina / spark / chunky / moreculling / scalablelux）+ OPAC + Create + YBD。

**两个后果**：
1. **别人按 pack 装出来的包 ≠ 我们的环境**（拿不到 embeddium / OPAC / Create）；
2. **C10 的根源**：pack 里没有"我们用的是 embeddium"这个声明 → packwiz 才会心安理得地把 sodium 拉进来。

**修法（已做）**：把这 16 个逐个 `packwiz modrinth add` 纳为 **批 0（已装地基）**。
结果比预期好：16 条全部找到**与实例完全同版**的版本（版本零漂移，`materialize` 新下 0 个 jar），
所以上面担心的"拉较新版本 → 基线失效"**没有发生，性能基线无需重跑**。

同时把批 0 写进 `tools/lists/install-batches.tsv`，并让测试器**永远带上批 0**
（`parse_groups` 里以 `want = set(bg["0"])` 起手）——理由见 R4 与 T15。

### T15 · 过期依赖图会**静默抽走前置库**，伪装成 mod 冲突（🔴 已修，加了绊线）

**现象**：T14 纳入 16 个条目后复验，客户端 FAIL 未进世界。这次不是 C9 那种静默死亡，
而是留下了 FML 崩溃报告：

```
-- Mod loading issue for: neruina --
Failure message: Mod neruina requires configurable 3.5.1 or above
    Currently, configurable is not installed
```

**真因与 neruina / configurable 无关**，是测试器自己把 `configurable` 从客户端搬走了：

1. `packwiz modrinth add` **不写 `[dependencies]` 段** —— 依赖边全靠 `tools/depgraph.py`
   现查 Modrinth 接口，缓存进 `data/dep-graph.json`；
2. 该缓存是**增量**建的，T14 新加的 16 条还没进图 → `neruina` 在图里没有键；
3. `paired_check` 的 `want = closure(批次)` 因此**漏掉 configurable**；
4. `set_side` 的职责是"不在 want 里的 jar 一律搬去 `data/held-back/cli`" →
   **它把 configurable 搬走了**（该 jar 明明在 `pack/` 里、也在 `materialize-manifest.json` 里）；
5. 客户端于是缺前置库 → FML 崩溃。

**为什么这条最危险**：报错文本**与真实 mod 冲突一模一样**（"requires X / not installed"），
但它测的是测试器的记账，不是 mod 的兼容性。若不去 `held-back/cli` 里翻一眼那个 jar，
就会把它当成"neruina × configurable 冲突"写进总账 —— 一条**凭空的假冲突**。

**修法（两层）**：
- **数据层**：`python tools/depgraph.py --build` 重建（242 条；`neruina → configurable` 边已出现）。
  **纪律**：每次往 `pack/` 加/删条目，必须重建依赖图，否则下一次测试结论不可信。
- **工具层（防复发绊线）**：`paired_check.py` 在求闭包**之前**检查"`want` 里有没有图里不存在的条目"，
  有就立即停下、返回码 **6**，提示先 `--build` —— 宁可拒绝开测，也不带着过期图把前置库搬走。

### T16 · 换行符足以让 pack 失去可复现性（🔴 已修）

**事实**：本机 `git config core.autocrlf` = `true`，而仓库**没有 `.gitattributes`**。
于是 `pack/` 下的 `.pw.toml` / `index.toml` / `pack.toml` 在新克隆时会 LF → CRLF，
而 `pack/index.toml` 里存的是**每个文件的 sha512/sha1** → 换行一变，哈希全错，
别人 `packwiz install` / 启动器同步会直接失败。**而这是我们自己开发机上永远看不到的**。

**修法**：新增 `.gitattributes`，把 `pack/**` 标为 `-text`（当作二进制，不做任何换行转换），
其余源码/清单/文档统一 `eol=lf`。**注意 gitattributes 是"后匹配的规则赢"** ——
`pack/** -text` 必须写在 `*.toml text eol=lf` 这类规则**之后**，否则会被覆盖（第一版就写反了，
`git check-attr` 一眼看出 `text: set`，改序后变成 `text: unset`）。

### C11 · 3 个纯客户端 mod 被装进了服务端（🔴 已修并验证）

**怎么发现的**：批 5 装完后跑了一次**全量端侧取证** —— `side_check.py` 拿实例里 236 个 jar 的
**SHA-1** 逐个反查 Modrinth，读它官方标注的 `client_side` / `server_side`：

```
合计 236 个：服务端要装 180 · 纯客户端 38 · 未知 18
```

再拿这份结论去对 `pack/mods/*.pw.toml` 的 `side` 字段 —— **恰好 3 条对不上**，
而 `server/mods` 里正好躺着这 3 个 jar：

| slug | pack 里写的 | Modrinth 官方标注 | 结果 |
|---|---|---|---|
| `xaeroplus` | `both` | **仅客户端** | 装进了服务端 |
| `moreculling` | `both` | **仅客户端** | 装进了服务端 |
| `particle-rain` | `both` | **仅客户端** | 装进了服务端 |

**这是 T2 的翻版**：packwiz 的 `side` 字段不可信，而**只有服务端那边会露出来** ——
客户端多装几个纯客户端 mod 没问题，服务端多装就可能出事（这次它们只是白加载，没崩）。

**修法（已做并复验）**：改 `side = "client"` → `packwiz refresh`（守 T17 纪律）→
按 T10 把 3 个 jar 从 `server/mods` 挪出（**改 pack 不会自动删实例 jar**）→
**服务端 201 → 198 jar，启动 `Done (28.121s)`，日志里这 3 个 mod 已不再被扫描**。

**还剩 18 个"未知"**：它们是 **CF 独占分发**（FTB 全家桶 / WorldEdit / Spice of Life / Polyglot 等），
文件不在 Modrinth 的哈希索引里 → 查不到官方端侧标注。
`side_probe.py` 试着直接读 jar 自己的 `neoforge.mods.toml` 声明（`clientSideOnly`/`serverSideOnly`），
**实测 4/4 全是"未声明"** → 这条路在 NeoForge 1.21 上不成立（mod 普遍不写）。
→ 所以对这 18 个，**证据换成行为证据**：它们都在 198 jar 的服务端里，而服务端起得来、跑得完世界生成。

### T17 · `index.toml` 里躺着一个**陈旧哈希**（🔴 已修，本轮最后抓到的）

**怎么抓到的**：所有批次装完后，做了一次"pack 自身完整性"检查 ——
复制 `pack/index.toml` → 跑 `packwiz refresh` → 对比是否被改写。结果它**真的被改写了**：

```diff
 [[files]]
 file = "mods/terralith.pw.toml"
-hash = "da7ee99189ad35aac094919fe33cac647d98533b23c228f10d4811f08989e2b8"
+hash = "81c5c05f9ed4c07b9f027ff6c421939bb04e476075091f135d34aaa05af1f950"
```

**根因**：C2 修 Terralith 版本错配时是**手工编辑** `terralith.pw.toml`（pin 到 2.6.2 / `IY93YaEe`），
改完**没有** `packwiz refresh` → `index.toml` 里留的是旧版元数据的哈希。

**后果**：`index.toml` 是安装器校验与增量同步的依据 —— 哈希对不上，
**别人装这个包会在 terralith 这一步失败**。而**本机永远不会暴露**：
我们的实例是由 `materialize_pack.py` 直接落 jar 的，根本不读 `index.toml`。

**修法**：跑 `packwiz refresh`（已跑；再跑一次确认**幂等**，说明索引与元数据确实同步了）。
**纪律**：**任何手工改动 `pack/mods/*.pw.toml` 之后必须 `packwiz refresh`**；
走 `apply_modlist`（内部调 packwiz 命令）的自动化路径不会有这个问题。

### C3 / C4 · 两条选型冲突（装机冒烟直接报出，非推测）

- **C3 `stellarcreateoptimization`**：FML 报 `requires sodium 0.6.9 or above`，`Actual version: [MISSING]`。
  本包渲染器是 **`embeddium`**（Sodium 的 Fork，**mod id 不同**）→ 永远满足不了 → `skip`。
  要用它必须整体改用 Sodium 渲染器（连带 Iris 等，属另一项决策）。
- **C4 `byepregen` × `noisium`**：FML 报 `Mod 'byepregen' is incompatible with 'noisium'`（mod 自定义 incompatibility）。
  两者都是世界生成性能优化 → 取 `noisium`（跨整合包共识采用更广），`byepregen` → `skip`。

---

## 三、四条硬规律（本次撞出来的，值得写进选型纪律）

### R1 · 世界与 mod 集必须匹配；**缺 mod 时开世界是静默失败**

客户端 mod 必须是"生成世界的 mod 集"的**超集**：
- 客户端比世界多装 mod → 没问题（安全方向）
- 世界引用了客户端**没有**的生物群系/维度 → **开世界静默失败**：没有异常、没有崩溃报告、
  日志停在启动完成那一行，看起来像"挂死"

证据：`ptworld` 的 `level.dat` 里出现 `twilightforest:twilight_biomes`、`deeperdarker:*` 等命名空间，
而那次测试的客户端**没有**这些 mod → 直接进不去。**这条解释了本会话绝大多数"进不去世界"的现象。**

### R2 · 世界生成类 mod 之间要核对**版本同期性**

不能只核对"1.21.1 有版本"，还要看它与**前置库**是否同期。Terralith 2.5.8（2025-01）配
lithostitched 1.8.0-beta6（2026-09）差 20 个月 → **不报错，只卡死**。
（packwiz 的 `modrinth add` 默认选版可能挑到旧版 → 必要时手动 pin。）

### R3 · 影响世界生成的 mod 有硬顺序

群系扩充 / 结构 / 据点结构 / **维度 mod** 都属于此类：
**必须在建世界之前就位**，否则要么静默进不去，要么只能在新区块生效。
→ 所以装机批次按"不可逆性"排，且**每次验证都要用同集重新生成世界**（配对测试协议）。

### R4 · 测试器搬走的 jar 必须能被解释

`set_side` 会主动把不在 `want` 里的 jar 搬到 `data/held-back/`。这是二分定位的必需能力，
但它意味着**测试器有权限制造"缺 mod"现象**。所以往后任何一次"缺 mod / 进不去世界"，
第一件事就是去 `data/held-back/cli`、`data/held-back/srv`、`data/held-back/quarantine`
里翻有没有那个 jar：
- **有** → 是测试器搬走的，属 T15 / T6 同类（记账问题），不是 mod 冲突；
- **没有** → 才是真的缺 mod / 真冲突。

推论：**"已装地基"（批 0）这类一直该在场的 mod，不能只靠批次表表达**，
否则任何一次子集测试都会把它搬走。现在由 `parse_groups` 无条件并入批 0 来解决。

---

## 四、T 系列 · 工具链与元数据缺陷（已修，防复发）

> **T1~T6 是较早一轮的完整版**；**T7~T16** 的摘要见第一节的表格，其中 **T14 / T15 / T16** 有完整详情（第二节）。
> 全系列共 16 条，**全部已修**（T10 部分工具化）。

| # | 问题 | 修法 |
|---|---|---|
| **T1** | **12 条错 slug**：参照包 jar 文件名会把连字符去掉（`sophisticatedbackpacks-1.21.1-…`），而项目 slug 是 `sophisticated-backpacks` → "参照包实证"只查文件名包含关系 → 错 slug 全部通过核验、到装机才炸 | 修正 12 条（`twilightforest`→`the-twilight-forest`、`jecharacters`→`justenoughcharacters`、`elevatorid`→`openblocks-elevator`…）+ **加关卡**：参照包命中后必须确认 slug 在接口上真实存在，否则判 `SLUG_NOT_FOUND` |
| **T2** | **`side` 字段不可信**：见 C1 | `side_check.py` 复核；已修正 3 条 |
| **T3** | **`--batch` 落盘陷阱**：`apply_modlist --batch N --apply` 只写批次内元数据，但 packwiz 会把**依赖**也写进 `pack/mods/` → 只按 batch 落盘就缺依赖 jar（`architectury`、`extralib`）→ 启动崩 | apply 之后**无参数**跑 `materialize_pack.py` |
| **T4** | **跨批次依赖**：`xaeroplus` 硬依赖 `xaeros-worldmap`（原在批 4） | `xaeroplus` 移到批 4 |
| **T5** | **旧版本残留**：升版后旧 jar 留在目录里 → 同一 mod 两个版本（游戏报 "version differences that were not resolved"） | 新增 `materialize_pack.py --prune`（只删被 pack 管理的 mod 的其它版本，不碰手工装的） |
| **T6** | **验证方法本身出过假结论**（详见第五节） | 改用「配对测试」协议 + 文件名模糊兜底 |
| **T7~T13** | 隔离清单 · 依赖闭包被误撤 · 同名 jar 两 slug · 从 pack 移除不删实例 jar · `--prune` 启发式误删 · CF 误拒 · `Task` 不跨 pwsh 存活 | 见第一节摘要表逐条 |
| **T14** | **pack/ 与实例不一致**（16 个已装 mod 不在 pack 里）→ 也是 C10 的根源 | 纳入批 0（同版，零漂移）✅ 见第二节 |
| **T15** | **过期依赖图静默抽走前置库**，伪装成 `requires X` 假冲突 | 重建图 + 测试器加绊线（返回码 6）✅ 见第二节 |
| **T16** | **换行符让 pack 哈希失配**（`core.autocrlf=true` 且无 `.gitattributes`） | `pack/** -text` ✅ 见第二节 |

---

## 五、T6 详情 · 一次 30 分钟的误判，以及正确的验证协议

**踩的坑**：为了二分定位，我写了个测试器，它靠 `pack/mods/<slug>.pw.toml` 里的 `filename`
去把 jar 放进/移出客户端。后来我把 `the-twilight-forest` / `stoneholm-forge` 判为 skip/hold 并
**从 `pack/` 里移除**了元数据 —— 于是测试器**再也定位不到它们的 jar**，也就**无法把它们撤出客户端**。
结果：**从此每一次测试都带着它们**，全部 FAIL，看起来像"环境在退化"。

**后果**：我一度得出"批 1 单独也进不去了 → 环境被破坏"的错误判断，白折腾约半小时。

**修法（两条，都要）**：
1. **文件名模糊兜底**：元数据不在时，按"去掉非字母数字后包含 slug"去目录里匹配 jar。
2. **配对测试协议**（`data/paired-test.py`）：测"某组 mod 能不能进世界"时，
   **服务端 mods / 客户端 mods / 生成世界的 mod 集**必须三者为同一集合 —— 这正是 R1 的要求。
   另配 `data/client-subset.py`：用**一个已知安全的基准世界**（由更小集合生成）测客户端子集，
   因为"客户端 ⊇ 世界"是安全方向，可以省掉每轮 3 分钟的世界生成。

**教训一句话**：**验证方法本身的 bug 会伪装成"被测对象的问题"**，而且它比被测对象的 bug 更难发现 ——
因为它让你怀疑一切、却不怀疑手里的尺子。

---

## 六、取证经验（写给以后的自己）

1. **"崩了"和"卡死了"是两类问题**：崩溃看日志/崩溃报告；**卡死只能向 JVM 要线程转储**
   （`tools/hang_triage.py` 已就绪）。本轮那次卡死**没有异常、没有崩溃报告、没有系统事件**。
2. **`Crash Assistant` 的"已崩溃但未生成崩溃报告"是误报**：它是游戏启动时拉起的**独立看守进程**
   （日志里有 `Parent PID` + 模组列表快照），游戏被超时回收后它也会弹窗。
3. **游戏窗口截图抓不到 GL 内容**：`PrintWindow` 与 `CopyFromScreen` 都只拿到全白
   （普通窗口如 Crash Assistant 能正常抓）→ 涉及画面的判断暂时不能靠截图。
4. **packwiz 的 `side` 与 slug 都不能全信**：装机前用 `side_check.py` + "接口存在性检查"各过一遍。
5. **世界生成类 mod 要同时核对"前置库版本同期性"**（R2），并且**用同集重新生成世界**再验（R1/R3）。

---

## 七、待魔改清单（本轮**不做**，留待之后）

> 用户明确："具体的魔改可以留置一段时间再说"。这里只登记，不动手。

- 配方与产线衔接（Create 产线 ↔ 图纸材料 ↔ 食物链）
- 时代闸门（哪些内容在哪个时代解锁；工人系统与 Create 的重叠怎么切）
- 威胁数值（国力 → 难度映射、袭击规模曲线）
- 军队供养与兵力上限（`DESIGN.md` §17.7 空缺 #8）
- 据点的收益与重新占据节奏（空缺 #9）
- 中文化补全 —— **已用量化清单取代猜测**（`tools/lang_audit.py` 对 236 jar 实测，2026-09-19）：
  **129 个已有 `zh_cn`** · **58 个无文本**（库/性能，不面向玩家）· **48 个缺 `zh_cn`，合计 13 166 键**。
  缺口最大：`chipped` 7265 · `Design n' Decor` 905 · `refurbished_furniture` 654 ·
  **`OPAC` 613**（唯一"承重墙级"缺口：领地界面全靠它）· `collective` 561 · `buildersaddition2` 327 ·
  `xaeroworldmap` 322。**4 个带 `zh_tw` 等中文变体**（mcwwindows 326 / grieflogger 46 / polyglottooltip 15 /
  colorfulhearts 14）→ 这批可**直接转写**，成本最低。逐条清单见 `tools/lists/i18n-todo.txt`。
- `ftb-teams` 全员同队配置；`chunk-plan` 配额参数
- （可选加内容）`dye-depot` → 一次解锁 dndecor 预置好的 **256 个染色装饰变体**（见第九节）

---

## 八、批次进度

| 批 | 内容 | 状态 | 备注 |
|---|---|---|---|
| 0 已装地基（16 条） | 性能组 + OPAC + Create + YBD | ✅ **已纳入 pack 并验证** | 原本只存在于实例、不在 pack 里（T14）；纳入后 pack 才自洽。测试器无条件带上它（R4） |
| 1 地基（33 条） | 世界生成三件 + 引擎 + 性能 + 中文化 + 运维 | ✅ **已装已验** | 修掉 C1/C2/C3/C4 |
| 2 世界内容（29 条） | 结构 / 维度 / 村庄 / 中世纪 / 据点结构 | ✅ **已装已验**（26 条） | 剔除 C5 `stoneholm`（skip）、C6 `deeperdarker`、C7 `the-twilight-forest`（hold） |
| 3 立国与基建（59 条） | Create 生态 / 图纸系 / 建材装饰 / 交通 / 食物农业 | ✅ **已装已验** | 剔除 C8 `create-aeronautics`（hold）；C9 定为流程问题（见 C9 详情） |
| 4 远征与生活（54 条） | 地图背包定位 / 战利品遗物 / QoL / 视觉音效 | ✅ **已装已验（重验）** | 剔除 C10 的 sodium 系三个；`medieval-paintings`/`medieval-music` 用 packwiz `--addon-id/--file-id` 显式 id 补装（搜索匹配会误拒） |
| 5 御敌与文明（8 条） | 威胁 / 军事单位 / 攻城 / 大工程 | ✅ **已装已验（重验）** | 当时的"客户端 236 jar 进世界 PASS"是假阳性（C13）；服务端 198 jar `Done (28s)` 独立成立。期间修掉 T14/T15/T16/T17 与 C11 |

**装机结论**：`plan` 179 + 已装地基 16 = **195 条就位**（`continuity`/`inventory-profiles-next` 因 C12/T18 改判 hold/skip），
客户端 **231 jar 进世界 PASS（2026-09-19 16:24 用强判据重验）** / 服务端 **196 jar**。
`hold` / `skip` 的条目全部留在 `tools/lists/quarantine.tsv` 与 `modlist.tsv` 里，**原因逐条可查**。


**验证方式**（每批都要跑，**两步流程**，见 `tools/README.md` 第二十节）：
`apply_modlist --verify --batch N --apply` → `materialize_pack.py`（无参数，含依赖）→
`materialize_pack.py --side server --dest server/mods` →
① `paired_check.py --group b1,…,bN --world-only --backup-world data/bN-world`（生成并备份世界）→
② `paired_check.py --group b1,…,bN --reuse-world data/bN-world --world bNv`（单独跑客户端）。
**判定**：客户端日志出现 `Starting integrated minecraft server` = 进世界。

---

## 九、服务端侧观察（非致命，但都有日志证据）

> 服务端冒烟（198 jar）：**`Done (28.121s)`**，能起、能跑。
> 但日志里有 **1257 行 ERROR** —— 逐类归完之后，**没有一条能把服务端起崩**，
> 只有 **1 类是真缺陷**、**2 类是"兼容条目给缺席 mod 写的"**、其余是杂项。
> 记在这里是因为：**"起得来"不等于"干净"**，这些会在以后排查时变成噪音。

| 行数 | 来源 | 性质 | 结论 |
|---|---|---|---|
| **720** | `farmingforblockheads:market/*` 配方 | 市场配方为**缺席的 mod** 预生成（pamhc2trees / byg / croptopia / thermal / tropicraft / twilightforest / quark / ars_nouveau / betterendforge…） | 🟡 **无害噪音**：那些 mod 不在包里，配方本就该无效。FFH 没给它们加"mod 存在才加载"的条件 |
| **276** | `railways:track_*` 战利品表 | 同上：Create 铁路为缺席 mod 生成的轨枕变体（tfc / byg / biomesoplenty / twilightforest / natures_spirit） | 🟡 **同源噪音** |
| **256** | `dndecor:blocks/*` 战利品表 | **真缺陷**：Design n' Decor 的 **16 个非原版颜色**（amber / mint / slate / rose / navy / olive / verdant / maroon / coral / aqua / beige / tan / teal / indigo / ginger / forest）× 16 种构件 = 256 个变体，**物品没注册**，但 datagen 的战利品表照发 | 🟠 **见下**：这是**软依赖未装**，不是冲突 |
| 2 | `farmersdelight` / `farmersrespite` 配方 | 用了 `neoforge:never` 条件 codec，**当前 NeoForge 不认这个 codec** → 那两条配方加载失败（如西瓜汁） | 🟠 少 1~2 条配方，不影响启动 |
| 1 | `endersdelight:chorus_pie_slice` | 配方用 `{"id":…,"count":…}`，而 1.21.1 期望 `"item"` 键 → **该 mod 的数据是按更高 MC 版本写的**痕迹 | 🟠 少 1 条配方 |
| 1 行（4 个） | `Couldn't load advancements` | `minecraft:wander_add_map` · `minecraft:give_quest_trader_trade` · `dungeons_arise:find_thornborn_towers` · `find_fishing_hut` | 🟡 4 个成就缺失，无功能影响 |
| 1 | MoonlightLib：`Fabric API detected!` | 误报（它探到 Fabric 风格的 API 痕迹） | 🟢 无视 |
| 35 | `Ignoring unknown attribute 'forge:entity_gravity'` | 某 mod 仍按**旧 `forge:` 命名空间**注册属性，NeoForge 21.1 已不认 | 🟢 无害（属性被忽略） |
| 3 | `connectivity` × `packetfixer` mixin `@ModifyConstant` 互撞 | NeoForge **按优先级跳过**其中一个并明确记日志 | 🟢 **可控重叠**：两者共存，只丢 1 个 mixin |
| — | `roadweaver` 尝试 OpenCL | 专用服务端 classpath **没有 LWJGL 的 OpenCL 模块** → `NoClassDefFoundError: org/lwjgl/system/FunctionProvider` → **自动回退 CPU** | 🟠 解释了服务端世界生成 **166 秒**的一部分；属配置/魔改范畴 |
| — | 客户端额外杂项 | `Invalid path in pack: byg:textures/block/track/TODO.txt`（Railways 把 TODO.txt 打进资源包）· `Unable to parse animation: Shooting` · dev cape 拉取失败（离线，无害） | 🟡 打包疏漏类，无功能影响 |

### dndecor 那 256 行：**软依赖未装的典型形态**（证据链）

1. **观察到**：256 个 `dndecor:*` 战利品表报 `Unknown registry key in ResourceKey[minecraft:item]: dndecor:<名>`
   → 是**物品没注册**，不是战利品表本身写错（方块状态 847 个 / 战利品表 848 个都在 jar 里、语言键也在）。
2. **做对照**：把失败的 256 个与成功的 591 个按"颜色前缀"分类 ——
   失败的全是 **amber / mint / slate / rose / navy / olive / verdant / maroon / coral / aqua / beige /
   tan / teal / indigo / ginger / forest**（各 15~16 个），
   **原版 16 色（white/black/red/…/light_gray）全部正常**。
3. **读字节码定位**：在 `Design-n-Decor-1.21.1-2.2b.jar` 里搜，`dye_depot` 出现在
   `DnDecor.class` / `DnDecorDatagen.class` / `DnDecorUtils.class` ——
   那 16 个颜色正是 **Dye Depot** 的调色板。`neoforge.mods.toml` 里**没有**声明它 → **软依赖**。
4. **结论**：dndecor 把 Dye Depot 的染色变体**datagen 出来并打进 jar**（战利品表/方块状态无条件发），
   但方块/物品**只在 Dye Depot 存在时注册** → 未装就剩 256 个孤儿战利品表。
   **不是冲突，是无害噪音**；而且它给出了一条**可选的加内容路径**（见下）。

> **可选内容机会（记下，不在本轮做）**：加 `dye-depot` 就能一次解锁 **256 个染色装饰变体**
> （已被 dndecor 预先 datagen 好，只差颜色来源）。属于**加内容**的选型决策，不是修冲突。

## 十、最终结论

> 本轮任务：**把定案的 mod 全部装进 `pack/` 并逐批验证，把撞到的冲突与缺陷全部总结出来**。
> 魔改（配方/数值/配置）按用户指示押后，不在本轮。

### 1. 装机结论：**197/197 就位，五批全部验证通过**
| 项 | 结果 |
|---|---|
| 选中条目 | **197**（`plan` 181 + 已装地基 16）= 252 条候选里筛下来的 |
| 未就位 | **45** = `hold` 36 + `skip` 9，**逐条有理由**（`modlist.tsv` / `quarantine.tsv`） |
| 客户端 | ✅ **231 jar 进世界 PASS**（2026-09-19 16:24，新判据：关卡加载完成 + 本次零新崩溃报告）；原"236 jar PASS"是假阳性，已作废 |
| 服务端 | **198 jar**（= 端侧取证要求的 180 + 18 个 CF 独占分发、无声明可查的）· 启动 `Done (28.121s)` |
| `pack/` | **237 条元数据**，自洽、可复现（已补批 0，且 `pack/**` 锁字节精确） |

**判定为"能跑起来"**，不能判定"能玩"——后者要靠魔改与真人试玩。

### 2. 冲突结论：**9 条真·装不到一起（2 已修 / 7 已处置）· 1 条流程问题 · 1 条服务端污染**

11 条 C 系列里，**9 条是真冲突**（每条都有崩溃报告、接口证据或 mod 自声明的不兼容）：

| 真冲突 | 一句话原因 | 处置 |
|---|---|---|
| **C1** `certain-questing-additions` | mixin 进 FTB Library 触碰 `MultiBufferSource` → **专用服务端起不来**（连带任务书） | 改 `side=client` ✅ **已修并验证** |
| **C2** `Terralith 2.5.8` × `lithostitched` beta | 版本差 20 个月**错配 → 卡死**（不报错） | pin Terralith 2.6.2 ✅ **已修并验证** |
| **C3** `stellarcreateoptimization` | **硬依赖 `sodium`**，本包是 `embeddium`（mod id 不同）→ 永远满足不了 | `skip`（除非换渲染器） |
| **C4** `byepregen` × `noisium` | mod **自己声明**不兼容 | 取 `noisium`，`byepregen` `skip` |
| **C5** `stoneholm-forge` | 进世界即崩（`StructurePoolAccessor` NPE） | `skip` |
| **C6** `deeperdarker` | **加它就进不去世界**（配对协议复验，机制待查） | `hold` |
| **C7** `the-twilight-forest` | **加它就进不去世界**（安全方向复验，机制待查） | `hold` |
| **C8** `create-aeronautics` → `sable` | 与 `embeddium` + `scalablelux` **双硬冲突** | `hold`（等渲染器决策） |
| **C10** `sodium-extra` / `reeses-sodium-options` | 把 `sodium` 作为依赖拉进来 → 与 `embeddium` 互斥（真冲突）；**但根源是我们自己的 pack 没声明用 embeddium**（T14） | 两者 `hold`，`sodium` 移出 ✅ |

**唯一不是 mod 冲突的是 C9**：全配对模式（同进程先服务端再客户端）让客户端在 mod 加载阶段静默死亡，
4/4 复现、无任何崩溃痕迹；同样 140 jar 用两步流程一次通过 → **是验证流程问题**，改两步流程解决。

**C11 是另一类**：不是 mod 之间冲突，而是**我们自己的元数据把 3 个纯客户端 mod 送上了服务端**
（`side` 字段写错，T2 的翻版）→ 已修，服务端 201 → 198 jar。

**服务端那 1257 行 ERROR 全部归完类**（见第九节）：**没有一条能起崩服务端**，
其中 996 行是"兼容条目给缺席 mod 预生成"的无害噪音（FFH 市场 720 + railways 轨枕 276），
256 行是 dndecor 对 **Dye Depot** 软依赖未装留下的孤儿战利品表，
真正算缺陷的只有 `farmersdelight` / `farmersrespite` / `endersdelight` 共 4 条配方 + 4 个成就。

**最危险的一类**：**报错长得跟真冲突一样的假冲突**——不查清就会把自己工具的记账错误写进总账：

| 假冲突长什么样 | 真因 | 怎么一眼认出来 |
|---|---|---|
| `Mod X requires Y / not installed` | **依赖图过期**：测试器把前置库搬走了（T15） | 去 `data/held-back/` 翻有没有那个 jar（R4） |
| 进世界**静默失败**（无报错无崩溃） | 客户端 mod 集**不包含**世界的 mod 集（R1）；或缺了批 0 地基（R4） | `level.dat` 里的命名空间 ↔ 客户端 mods 对不对得上 |
| "卡死 / 挂了" | 流程问题（C9）或版本错配（C2），**不是崩溃** | 没有崩溃报告 = 先向 JVM 要 `jstack` 转储，别翻崩溃日志 |

工具层已加两道绊线：依赖图过期 → `paired_check` **返回码 6** 拒绝开测；
`pack/**` 换行符 → `.gitattributes` 锁字节精确（否则别人克隆下来哈希全错，T16）。

### 3. 还需要拍板的**唯一一件事**

**渲染器：留在 `embeddium`，还是换 `sodium` 系？**
C3 / C8 / C10 三处冲突都指向它。换过去可解锁 **4 个 mod**（`stellarcreateoptimization`、
`create-aeronautics`、`sodium-extra`、`reeses-sodium-options`），代价是重做渲染侧验证 + 重跑性能基线。
**这是设计取舍，不是技术障碍** —— 所以留给用户定，不在本轮擅自动手。

### 4. 本轮**没做**的（按用户指示押后）

配方衔接与产线 · 时代闸门 · 威胁数值 · 军队供养与兵力上限 · 据点收益与重占节奏 ·
中文化逐条补全 · 任务书 27 → 90~110 条 · `ftb-teams` 全员同队配置 · `chunk-plan` 配额参数。
见 §七 与 `DEFERRED.md`。

**一句话总结**：**这个包现在装得起来、进得去世界、冲突已经清账**；
它离"能玩"还差的是**内容层的魔改**，而不是兼容性。

---

## 十一、重大更正（2026-09-19 傍晚）：客户端开世界崩溃 + 验收协议失效

> 这一节是**对前面结论的更正**，不是新功能。触发点：M0 真机验证任务里子代理反复进不去世界，
> 于是回头查日志，发现**我此前报的验收有假**。

### C12 · `InventoryProfilesNext` 让客户端**进不了任何世界**

**现象**：客户端到主菜单没问题，一开世界就崩。今天同一签名崩了 **6 次**
（09:16:53 / 09:30:21 / 09:44:31 / 14:53:30 / 15:11:45 / 16:01:39）。

```
Description: Rendering screen
  ... Minecraft.doWorldLoad            ← 正在开世界
  → ClientHooks.drawScreen
  → inventoryprofilesnext ConfigScreenSettings.<clinit>
  → inventoryprofilesnext Features.getENABLE_PROFILES
  → NullPointerException（libIPN 的 AsDelegate 还是空的）
```

**关键判据**：崩溃报告里**没有任何"已加载关卡"的信息**（`Affected level` 段缺失）→ **玩家从没进过世界**。

**排除过程（四次实验，每次约 5 分钟）**：

| 实验 | 动作 | 结果 |
|---|---|---|
| 1 | 移出 `connector`（hold，却装在客户端） | 仍崩；且暴露下一环：FML 报 `Mod continuity requires connector any` |
| 2 | 再移出 `continuity`（Fabric 侧、与 `fusion` 重复） | **仍崩**（还是 IPN 同一栈）→ 证明 Connector 不是唯一元凶 |
| 3 | 再移出 Connector 的依赖 `forgified-fabric-api` + 清掉 IPN 陈旧配置 | **仍崩** |
| 4 | 把 `inventory-profiles-next` + `libipn` 判为 `hold` 并移出 | ✅ **客户端正常加载世界**：`LoggerChunkProgressListener: Time elapsed: 582 ms`、**本次零新崩溃报告**、此后一直存活 |

**顺带排除**：IPN 与 libIPN **不是版本错配**（IPN 2.2.5 声明 `libipn [6.6.3,6.7)`，装的就是 6.6.3，两者都是 1.21.1 最新）→ 是 IPN 自身在这个环境里的真 bug。
**处置**：`inventory-profiles-next` → `hold`（纯便利 mod，拿它换"客户端能玩"完全划算；证据是三次硬崩溃）。

### C13 · 验收协议**假阳性**（这一条比 C12 更严重）

**判据缺陷**：`Starting integrated minecraft server` 这行在**开世界早期**就写进日志，
关卡加载**之后**才崩的客户端也会被判成 PASS。

**铁证（时间差）**：

| 弱判据写入 | 崩溃报告 | 我的结论 |
|---|---|---|
| 09:30:21 前 | 09:30:21 | **09:31:10 报 PASS**（+49s）|
| 09:44:31 前 | 09:44:31 | **09:45:19 报 PASS**（+48s）|
| 15:57:57.446 | 15:57:58 | 实验 2 被判"进世界" |
| 16:01:38.125 | 16:01:39 | 实验 3 被判"进世界" |

→ **批 4（提交 09:17，崩溃 09:16:53）与批 5（提交 09:46，崩溃 09:44:31）的"验收通过"都是假的。**

**修法（已做）**：
1. 判据必须**同时**满足：① 弱标记 ② **关卡加载完成**证据
   （`LoggerChunkProgressListener` / `Preparing spawn area` / `Time elapsed:`）③ **本次零新崩溃报告**。
2. `autotest.py` 在等进世界时，一旦发现新崩溃报告或崩溃标记就**立刻判失败**（不再等超时）。
3. 修掉 `autotest.py` 里旧检查的误报：原来是"最近 600 秒内有崩溃报告就报警"，
   会把**上一轮**的报告算到这一轮账上（实验 4 明明没崩却被误报）→ 改成"只看本次启动之后写入的报告"。
4. 记一条**我自己犯的错**：一开始把 `joined the game` 当单机判据，结果白等一轮——
   那行是**连服务器**场景才有的；单机要用上面第 ① 条那组。

### T18 · `hold` 的 mod 被依赖拖进 pack

- 批 4 的 **65 个条目**里混进了 `connector.pw.toml`（清单里是 `hold`）与 `forgified-fabric-api`。
- 链条：`continuity`（Fabric 侧连接材质）→ `required: connector` → packwiz 解析依赖时写进 pack。
- 而 `continuity` 本身也是**选型错误**：它和已装的 `fusion`（NeoForge 原生连接材质）是**同一个功能位**，
  正是设计里"同一功能位只留一个"明令禁止的重复。
- **防线（本轮补上）**：`apply_modlist.py` 加两道关卡——
  ① **拒绝 Fabric 侧 mod**（jar 有 `fabric.mod.json` 且无 `neoforge.mods.toml` → 直接拒，本项目不走 Connector 路线）；
  ② **拒绝被依赖带进来的 `hold`/`skip` 条目**（解析 packwiz 实际写入的 slug，凡不在 plan/installed 名单里的就报错停下）。

### 附带：专用服务端 `-Xmx4G` 无人在线也会 OOM

子代理在跑"真客户端连专用服务端"时抓到：服务端 `Done (36.495s)` 后约 3 分钟
`OutOfMemoryError: Java heap space`（崩在 WorldEdit 7.3.8 的 `PlatformReadyEvent` 派发里，**当时无玩家在线**），
OOM 后自旋实测 **12 秒吃掉 86.9s CPU（7.2/8 核）**，把客户端饿死。
**处置**：把 `server/user_jvm_args.txt` 的堆调到 6~8G（3~7 人联机形态下必须修）。

### 由此产生的待办（按顺序）

1. 用新判据**重跑批 1~5 验收**（每批约 5 分钟，可连跑）。
2. 更新 README / CHANGELOG 里所有"进世界 PASS"的表述（当前不可信）。
3. 服务端堆调大并复验 `Done (`。
4. 然后回到 M0（HYW 单位能否刷出并攻击玩家）→ 计划 1。

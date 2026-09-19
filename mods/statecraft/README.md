# Statecraft（邦交）· 源码

《荒野建国》的配套 mod。**设计文档在仓库根的 `NATIONS.md`**，
实现计划是 `PLAN-statecraft-01-core.md`（生成骨架）与 `PLAN-statecraft-02-persist.md`（存档）。

## 模块

| 模块 | 内容 | 需要 Minecraft 吗 |
|---|---|---|
| `core` | 全部规则：生成、**结算引擎**、**外交状态机**、环境判定、文本模板、**存档编解码与迁移** | **不需要**（纯 Java，JUnit 直接跑） |
| `mod` | NeoForge 适配：`SavedData`、数据文件载入、命令 | 需要（ModDevGradle 2.0.147 + NeoForge 21.1.250）|

**为什么这样切**：`core` 不 import 任何 Minecraft 类 → 几个时代的推演可以在命令行里一次跑完，
不需要开游戏、不需要服务器。这是"少让人工测试"的技术前提。
**mod 层只允许放"什么时候调用"和"格式怎么翻译"，一旦把规则写进去，那些规则就再也测不了了。**

## 构建与测试

本机没装 Gradle，`gradlew` 会去下 130MB 分发（网络慢）。用缓存里的分发直接跑：

```powershell
# 只跑 core（离线、秒级）
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phgltp5f9b9e\gradle-8.11.1\bin\gradle.bat" `
  -p D:\project\nation-pack\mods\statecraft :core:test --console=plain

# 连 mod 一起构建（**首次**需要代理：ModDevGradle 插件与 MC 产物都在 maven.neoforged.net 上）
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phgltp5f9b9e\gradle-8.11.1\bin\gradle.bat" `
  -p D:\project\nation-pack\mods\statecraft :core:test :mod:build `
  "-Dhttp.proxyHost=127.0.0.1" "-Dhttp.proxyPort=7897" `
  "-Dhttps.proxyHost=127.0.0.1" "-Dhttps.proxyPort=7897"
```

产物 `mod/build/libs/statecraft-0.1.0.jar`（**core 的 class 直接打进去**，单 jar 交付）。
装法：拷到 `versions\1.21.1-NeoForge_21.1.250\mods\`。

**两个刻意选择**（都为了少踩坑）：

- `disableRecompilation = true`：跳过 NeoForm 的反编译/重编译，我们只需要**能编译**，
  不需要在 IDE 里读 MC 源码。首次建 MC 产物约 112 秒，之后 up-to-date。
- `neoforge.mods.toml` **不走 `ProcessResources.expand`**：那会把中文元数据交给平台编码解码，
  正是 `CONFLICTS.md` C14 那一类坑。代价是版本号有两处，所以加了构建期校验
  （`verifyModMetadata`）——已用 `-Pmod_version=9.9.9` 探针验证过它真的会让构建失败。

> ⚠️ **源码编码必须锁 UTF-8**（`core/build.gradle.kts` 与 `mod/build.gradle` 里的
> `tasks.withType<JavaCompile>`）。本包源码含中文（国名、事件文本），而 Gradle 默认按**平台编码**
> 读源码 —— 中文 Windows 上是 GBK，会把 UTF-8 源码错误解码、**字符串静默损坏**。
> 2026-09-19 实测踩到过（与 `CONFLICTS.md` C14 同一类坑）。


## 当前进度

### 计划 1 · 生成骨架（含独立审查修复）

| 组件 | 状态 |
|---|---|
| `DeterministicRandom` | ✅ `hash(seed,seq,tag)` 纯函数随机（可复现、可重放）；反向区间/NaN/null 标签一律拒绝 |
| `StatecraftConfig` | ✅ 全部默认值 + 钳制校验（dev 钳进 0..100、所有 double 查 finite、薄环带在**校验期**就报出 rmin/rmax —— 原来会静默退化到布点阶段再以错误的原因抛）|
| `Era` / `EraTable` | ✅ 时代**数据驱动**、按 era id 解锁、未知 id 归位（已知 id 优先）、表外 Era 按钳位语义（测 3/6/9 三套表）|
| `Culture` / `Nation` / `WorldState` | ✅ 数据模型，**NaN/负军力/空文本/负序号全部拒绝**（NaN 曾能穿透范围检查）|
| `WorldGenerator` | ✅ 布点（最小间距 + 环带）/ 规模 / **发展度与规模反比** / 倾向（`stanceBase` 真的接上了）/ **最近国主和 = 交换位置**（无主和候选时**抛异常**而不是静默放过）/ 国名按文化优先 + 回退、全局唯一 |

### 计划 2 · 存档（core 侧已完成）

| 组件 | 状态 |
|---|---|
| `StateNode` | ✅ 中性数据树（`Obj/Arr/Str/Int/Dec/Bool`）——core 与 NBT 的唯一接缝。`Int`/`Dec` 分开是为了 **long 种子不丢精度**；字段保插入序（不能用 `Map.copyOf`，它不保证顺序）|
| `StateCodec` | ✅ `WorldState ↔ StateNode` 往返；字段名与 `NATIONS.md` §五 一致；读失败时报**完整路径**（如 `world.nations[3].size`）|
| `StateMigration` / `StateMigrator` | ✅ 按 `schemaVersion` 选迁移链；**版本太新 / 缺迁移 / 内容损坏 → 只重置国家系统 + 中文警告，绝不让存档报废**；`eraId` 不在当前时代表里 → 归位；`eraId` 与 `eraOrdinal` 矛盾 → 以 id 为准 |
| `StateHash` / `SettlementLog` / `ReplayResult` | ✅ 事务日志与重放（计划 3）：日志记**输入**（结算序号 + 触发 + 小时数 + 动手前状态哈希），`replay` 逐步校验、`verify` 核对最终结果；支持从快照接着重放 |

**199 个 JUnit 用例全绿**（15 个测试类），全程离线、不需要开游戏。

> 🔒 **硬规则护栏**：`stanceAlwaysStaysInsideTheFormulaEnvelope` 保证最近国主和只能靠"交换位置"实现。
> 2026-09-19 用变异探针验证过它有牙齿：把交换改写成"翻转该国 stance"后，**只有这条断言失败**，
> 而旧的"最近国主和"断言照样绿。

## 加内容的入口（数据驱动）

| 想加什么 | 改哪里 |
|---|---|
| 一个时代 | `eras.json`（数量、时长、系数、解锁项都是数据；代码里不出现"六个时代"）|
| 一个国家名 | `nations.json` 的国名池 |
| 一座建筑与它的村民 | `buildings.json` + `staff.json`（计划 4）|
| 一条条约 / 一个事件 / 一封国书 | `treaties.json` / `events/*.json` / `letters/*.json`（计划 3）|
| 给存档加字段 | `WorldState` 加字段 + `CURRENT_SCHEMA_VERSION` **+1** + 加一级 `StateMigration`（旧档自动迁过来）|

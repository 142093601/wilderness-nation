# Statecraft（邦交）· 源码

《荒野建国》的配套 mod。**设计文档在仓库根的 `NATIONS.md`**，实现计划是 `PLAN-statecraft-01-core.md`。

## 模块

| 模块 | 内容 | 需要 Minecraft 吗 |
|---|---|---|
| `core` | 全部规则：生成、结算、外交状态机、环境判定、文本模板 | **不需要**（纯 Java，JUnit 直接跑） |
| `mod`（后续计划）| NeoForge 适配：SavedData、方块、实体、界面 | 需要 |

**为什么这样切**：`core` 不 import 任何 Minecraft 类 → 几个时代的推演可以在命令行里一次跑完，
不需要开游戏、不需要服务器。这是"少让人工测试"的技术前提。

## 构建与测试

本机没装 Gradle，`gradlew` 会去下 130MB 分发（网络慢）。用缓存里的分发直接跑：

```powershell
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phgltp5f9b9e\gradle-8.11.1\bin\gradle.bat" `
  -p D:\project\nation-pack\mods\statecraft :core:test --console=plain
```

> ⚠️ **源码编码必须锁 UTF-8**（`core/build.gradle.kts` 里的 `tasks.withType<JavaCompile>`）。
> 本包源码含中文（国名、事件文本），而 Gradle 默认按**平台编码**读源码 —— 中文 Windows 上是 GBK，
> 会把 UTF-8 源码错误解码、**字符串静默损坏**。2026-09-19 实测踩到过（与 `CONFLICTS.md` C14 同一类坑）。

## 当前进度（计划 1 完成 + 独立审查修复）

| 组件 | 状态 |
|---|---|
| `DeterministicRandom` | ✅ `hash(seed,seq,tag)` 纯函数随机（可复现、可重放）；反向区间/NaN/null 标签一律拒绝 |
| `StatecraftConfig` | ✅ 全部默认值 + 钳制校验（dev 钳进 0..100、所有 double 查 finite、薄环带在**校验期**就报出 rmin/rmax —— 原来会静默退化到布点阶段再以错误的原因抛）|
| `Era` / `EraTable` | ✅ 时代**数据驱动**、按 era id 解锁、未知 id 归位（已知 id 优先）、表外 Era 按钳位语义（测 3/6/9 三套表）|
| `Culture` / `Nation` / `WorldState` | ✅ 数据模型，**NaN/负军力/空文本/负序号全部拒绝**（NaN 曾能穿透范围检查）|
| `WorldGenerator` | ✅ 布点（最小间距 + 环带）/ 规模 / **发展度与规模反比** / 倾向（`stanceBase` 真的接上了）/ **最近国主和 = 交换位置**（无主和候选时**抛异常**而不是静默放过）/ 国名按文化优先 + 回退、全局唯一 |

**85 个 JUnit 用例全绿**（含 7 个测试类），全程离线、不需要开游戏。

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

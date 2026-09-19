# 计划 2：持久化（core 侧）—— schema / 迁移 / 编解码

> 目标（M2 的一部分）：**存档往返一致**、**时代表变更不废档**、**坏存档只重置国家系统、绝不报废世界**。
> 计划 1 的产物是 `mods/statecraft/core`（纯 Java，85 个 JUnit 用例全绿）。

---

## 前提与阻塞

| 项 | 状态 |
|---|---|
| NeoForge 的 `mod` 模块（`SavedData` 薄层） | ⛔ **阻塞**：`maven.neoforged.net` 直连超时，本机代理 `127.0.0.1:7897` 当时未监听；本机 Gradle 缓存里**没有**任何 `net.neoforged` 产物。等代理开起来再做 |
| core 侧持久化逻辑 | ✅ 不依赖 NeoForge，本计划做完 |

**为什么可以拆**：`core` 不 import 任何 Minecraft 类，所以"存档长什么样、旧版本怎么迁、时代表变了怎么归位"
都是纯数据变换，能用 JUnit 跑；NeoForge 那边只剩"中性数据树 ↔ `CompoundTag`"的翻译，几十行。

---

## 设计

### 1. 中性数据树 `StateNode`（core 与 NBT 的唯一接缝）

```
StateNode = Obj(Map<String,StateNode>) | Arr(List<StateNode>)
          | Str | Int(long) | Dec(double) | Bool | Nil
```

- **为什么不用 `Map<String,Object>`**：读字段的地方全都要自己 cast，版本迁移最容易出的错就是类型搞混，
  而编译器一点忙都帮不上。放一个 sealed 树，`asObj()/asLong()/asString()` 直接抛带字段名的错。
- **`Int(long)` 与 `Dec(double)` 分开**：world seed 是 long，超过 2^53 时用 double 存会**静默丢精度**
  （存档再也复现不出同一个世界）。
- **字段顺序确定**：`Obj` 用 `LinkedHashMap` 保插入顺序 → 同一状态序列化出来的字节稳定，便于 diff 与哈希。

### 2. `schemaVersion` 进 `WorldState`

设计（`NATIONS.md` §五）里 `WorldState` 就有 `schemaVersion`，计划 1 没实现。现在补上：
`WorldState.CURRENT_SCHEMA_VERSION = 1`。

### 3. 迁移 `StateMigration` / `StateMigrator`

| 情形 | 处理 |
|---|---|
| 版本号缺失（计划 1 之前写的档） | 当作 **v0** 走迁移链 |
| 版本号 == 当前 | 直接读 |
| 版本号 < 当前 且链完整 | 逐级迁到当前，记一条警告 |
| 版本号 < 当前 但**缺某一级**迁移 | **重置国家系统**（`reset=true`），世界不报废 |
| 版本号 > 当前（玩家降级了 mod） | **重置国家系统** + 明确警告；绝不试图"猜"未来格式 |
| `eraId` 不在当前时代表里 | 用 `EraTable.fallbackForUnknownId` 归位 + 一条警告，**不废档** |

`reset=true` 时 core **不自己造世界**（它不知道文化、出生点、时代表）——由调用方重新 `WorldGenerator.generate`。

### 4. 本计划**不做**（避免写没人用的东西）

- **事务日志 / 崩溃重放**：它要记"结算序号 + 输入哈希"，而"输入"由结算引擎（计划 3）定义。
  现在写就是凭空猜接口。
- `relations` / `events` / `letters` / `buildings` / `domains` / `playerLedger` 的编解码：
  这些字段本身还不存在（计划 3/4 才加）。**它们的加入方式恰好就是本计划要验证的迁移链**——
  到时候写 `v1→v2` 那一级即可。
- 持久化后端本身（NBT / 文件）：属于 `mod` 薄层。

---

## 任务

| # | 任务 | 验收 |
|---|---|---|
| 1 | `WorldState` 加 `schemaVersion` + 校验；`WorldGenerator` 写当前版本 | 既有 85 用例仍全绿 |
| 2 | `persist/StateNode`（sealed 树 + 取字段的带错工具） | 类型错/缺字段的报错能指出字段名 |
| 3 | `persist/StateCodec`：`WorldState ↔ StateNode` | 40 个 seed 往返**逐字段相等**；long seed 不丢精度 |
| 4 | `persist/StateMigration` + `StateMigrator` + `LoadOutcome` | 六种版本/时代表情形逐条断言 |
| 5 | 文档同步（`NATIONS.md` §五/§十三/§十七、`mods/statecraft/README.md`） | 文档与代码不再互相矛盾 |

**测试策略**：迁移链**只用测试注册的假迁移**（v0→v1）来验证框架，不在生产代码里留空链条——
生产里没有任何历史版本，"为不存在的旧版本写迁移"就是凭空造死代码。

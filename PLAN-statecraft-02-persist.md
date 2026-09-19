# 计划 2：持久化（core 侧）—— schema / 迁移 / 编解码

> 目标（M2）：**存档往返一致**、**时代表变更不废档**、**坏存档只重置国家系统、绝不报废世界**。
> 计划 1 的产物是 `mods/statecraft/core`（纯 Java）。

---

## 前提与阻塞

| 项 | 状态 |
|---|---|
| NeoForge 的 `mod` 模块（`SavedData` 薄层） | ✅ **已解除**：2026-09-19 20:00 代理 `127.0.0.1:7897` 起来后 `maven.neoforged.net` 可达。用 **ModDevGradle 2.0.147** + NeoForge **21.1.250**（与实例同版本），并开 `disableRecompilation = true` 跳过 NeoForm 的反编译/重编译（首次建 MC 产物约 112 秒，之后 up-to-date） |
| core 侧持久化逻辑 | ✅ 不依赖 NeoForge，已先做完 |

**为什么能拆**：`core` 不 import 任何 Minecraft 类，所以"存档长什么样、旧版本怎么迁、时代表变了怎么归位"
都是纯数据变换，能用 JUnit 跑；NeoForge 那边只剩"中性数据树 ↔ `CompoundTag`"的翻译。

**构建命令**（代理只在首次拉依赖时需要；Gradle 走标准 JVM 代理参数，不写进仓库配置）：

```powershell
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phgltp5f9b9e\gradle-8.11.1\bin\gradle.bat" `
  -p D:\project\nation-pack\mods\statecraft :core:test :mod:build `
  "-Dhttp.proxyHost=127.0.0.1" "-Dhttp.proxyPort=7897" `
  "-Dhttps.proxyHost=127.0.0.1" "-Dhttps.proxyPort=7897"
```

产物 `mod/build/libs/statecraft-0.1.0.jar`（**core 的 class 直接打进去**，单 jar 交付）。
安装 = 拷到 `versions\1.21.1-NeoForge_21.1.250\mods\`。

### mod 层只做这些（不含业务规则）

| 类 | 职责 | 为什么放这一层 |
|---|---|---|
| `data/JsonNodes` | Gson `JsonElement` → `StateNode` | 纯机械翻译；整数/小数靠字面量判断（给整数字段写 `12.0` 会报出字段名，不静默截断）|
| `data/NodeNbtCodec` | `StateNode` ↔ `CompoundTag` | core 与 MC 之间唯一的翻译层，**不含任何业务判断** |
| `data/StatecraftData` | 读内置 `eras.json` / `nations.json` | 只做"读文件 + 变中性树"，规则在 core 的 `DataFiles` |
| `data/StatecraftSavedData` | 挂到主世界 `DimensionDataStorage`；读档交给 core 迁移器；记住警告 | 只决定"什么时候读写"，不决定"数据对不对" |
| `StatecraftEvents` | 启动建库 + 注册 `/statecraft` 命令 | 只决定"什么时候调用"，不做计算 |

`neoforge.mods.toml` **不展开**（不走 `ProcessResources.expand`）：那会把中文元数据交给平台编码解码，
正是 `CONFLICTS.md` C14 那一类坑。代价是版本号有两处，所以加了构建期校验（`verifyModMetadata`），
写歪了直接构建失败——已用 `-Pmod_version=9.9.9` 探针验证过它真的会失败。

### M2 的验收怎么做（不用人看画面）

`/statecraft info`、`/statecraft roundtrip`（写进 NBT 再读回来，逐字段相等才打印 `STATECRAFT_ROUNDTRIP=OK`）
都带**稳定的 ASCII 标记**，因为中文回显在 `-Dfile.encoding=COMPAT`（GBK）下不可靠。
脚本在 `tools/tests/statecraft_v0.txt`（`cmd:` / `expect:` 格式）。

**落盘**必须跑**两次**才算证完：

| 第几次 | 期望日志 | 证明了什么 |
|---|---|---|
| 1 | `Statecraft 建库：seed=<世界种子> …` | 首建写入 |
| 2 | `Statecraft 读档成功：seed=<同一个> …` | **真的从 `.dat` 读回来了**（只跑一次只能证明内存里能往返）|

---

## 验收记录（2026-09-19 20:14 ~ 20:32，测试世界 `b5v3`）

| # | 证据 | 结论 |
|---|---|---|
| 1 | 真机客户端装载：`Found mod file "statecraft-0.1.0.jar"` / `Statecraft（邦交） 0.1.0 (statecraft)` / `Statecraft 数据已载入：6 个时代、2 个文化、国名池 24 个` | 内置 `eras.json` / `nations.json` 解析正确；中文元数据没有编码问题 |
| 2 | 第一次启动：`Statecraft 建库：seed=-6066409030490083615 时代=landing(0) 国家=12 个` | 世界种子派生 + 12 国生成，在真服务端主线程上完成 |
| 3 | `saves/b5v3/data/statecraft.dat`（1038 字节，gzip+NBT） | `SavedData` 接线正确、`setDirty()` 生效、真的落盘 |
| 4 | `tools/nbt_dump.py` 解析该文件：`{data:{state:{schemaVersion:1, seed:-6066409030490083615, eraId:"landing", eraOrdinal:0, seq:0, nations:[12]}}}`，`met` 是 byte | **字段名/类型与 `NATIONS.md` §五 完全一致**；long 种子**没有丢精度**（这正是 `Int`/`Dec` 分开的理由）|
| 5 | 12 国的名字与文化**对得上**（`n0 黄沙 desert_raider`、`n7 荒骨 bandit`…） | "国名优先本国文化池"在真机数据上成立 |
| 6 | 对真实数据算不变量：最近国 `n5 赤沙` r=2382 且 `stance=-41.5`（主和）；环带外 0 个；间距 <1500 的 0 对；12 个名字互不相同 | 硬规则 2/4 与布点在真机世界成立 |
| 7 | **第二次启动**（独立进程、20:28 启动）：`Statecraft 读档成功：seed=-6066409030490083615 时代=landing(0) 序号=0 国家=12`，`autotest` 退出码 **0** | **磁盘往返端到端成立**，且本次启动无新崩溃报告 |

**没做成的部分（如实记）**：`/statecraft info`、`/statecraft roundtrip` 两条命令**没能在真机上验成** ——
测试世界的玩家被卫道士杀死，**死亡屏吞掉所有按键**（`CONFLICTS.md` T19）。
命令背后的逻辑本身由 core 的 129 个用例覆盖（`StatecraftSavedData.roundTrip()` 与
`StateCodecTest.roundTripIsFieldByFieldIdentical` 是同一条路径），但"在游戏里敲进去"这一环**仍缺证据**，
**要补就得先有一个和平+创造的测试世界**（脚本已写好：`tools/tests/statecraft_v0.txt`）。


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

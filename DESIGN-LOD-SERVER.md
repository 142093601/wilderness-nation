# 服务端预生成 + LOD 分发（多人正解）

> **这份文档回答什么**：需求方提出「服务器里预先跑一张地图，然后把 LOD 分发给各个玩家」。
> 答案：**成立**，机制是 **LOD Server Support（LSS）**。
>
> 日期：2026-09-22 · 全部结论来自 Modrinth API + 拆包（非推测）

---

## 一、结论先说

**服务端预生成一次，所有玩家共享 LOD** —— 这比每个玩家各自预生成（`DESIGN-PREGEN.md` §5）
划算得多。而且**服务端不需要装 voxy**（拆包证实，见 §三）。

```
服务端：LSS + Chunky            →  预生成区块 → 建 LOD 库 → 分发给所有玩家
客户端：voxy + LSS              →  收 LOD → 渲染远景
```

---

## 二、一手证据：LSS 是什么

| 项 | 值 |
|---|---|
| 项目 | `lod-server-support`（Modrinth） |
| 标题 | LOD Server Support |
| 描述 | *"Voxy multiplayer support (unofficial). See fully rendered chunks and players thousands of blocks away in multiplayer without needing to explore the world first."* |
| 授权 | **MIT**（"redistribution with attribution is welcome, modpacks can reference the official Modrinth project directly"） |
| 端 | `client: optional` / `server: optional` |
| 加载器 | fabric / **neoforge** / paper / purpur / folia |
| 1.21.1/NeoForge | ✅ 有，最新 **`v0.15.1+neoforge+mc1.21.1`**（2026-09-21，21.73 MB） |

**与我们已核过的 `voxy-server-side` 是同一个 mod**（两者 `modId` **都是 `lss`**，
装任一个都会撞）——LSS 文档原文：*"Voxy Server Side is the same mod."*

---

## 三、拆包结论（这是最关键的证据）

### 3.1 服务端**不需要** voxy

```
LSS 服务端类里引用 voxy 的数量: 0
LSS 客户端/兼容类里引用 voxy 的数量: 1
```

LSS 服务端的 mixin 目标是**服务端原版类**（`ServerChunkCache` / `RegionFileStorage` /
`SimpleRegionStorage` / `IOWorker` / `Connection`）——它**直接读世界文件**并自己生成 LOD 列。
**voxy 只存在于客户端，负责渲染。**

⇒ 这解决了一个大顾虑：**不用给服务端编一个 voxy 出来**。

### 3.2 客户端网络层全是 LSS 自己的

`dev/vox/lss/networking/client/` 下一大批（非完整列举）：
`LSSClientNetworking`、`ClientColumnProcessor`、`ColumnCacheStore`、`ClientSessionGate`、
`ColumnStateMap`、`ClientIdentityResolver`、`FarPlayerRenderer`…

⇒ **LSS 自己收包、自己写进 voxy 的存储/ingest**，不改 voxy 的网络代码。

### 3.3 它与 voxy 的对接靠**反射 + 优雅降级**

`dev/vox/lss/compat/VoxyCompat` 里解析的类名（字符串常量，实测）：

```
me.cortex.voxy.commonImpl.VoxyCommon
me.cortex.voxy.commonImpl.VoxyInstance
me.cortex.voxy.client.VoxyClientInstance
me.cortex.voxy.client.config.VoxyConfig
me.cortex.voxy.commonImpl.WorldIdentifier
```

**这五个（+ `VoxyNeoForgeConfig`）在我们自己编的那个 voxy 里全部存在**（6/6 实测）。
失败时的降级文案也在里面：*"Voxy compat: class not found" / "method not found" /
"the Voxy half is unavailable this session"* —— **不会崩，只会报并退化**。

### 3.4 mixin 冲突风险可控

| 配置 | `required` | 目标 | 对我们 |
|---|---|---|---|
| `lss.neoforge.mixins.json` | `true` | 全部是 MC/NeoForge 类 | ✅ 不碰 Embeddium |
| `lss-sodium-legacy.mixins.json` | **`false`**（`defaultRequire: 0`） | Sodium 选项页 | ✅ **fail-soft**；LSS 文档说"Sodium 缺席或 pre-0.8 时无害" |

⇒ **我们没装 Sodium（用的是 Embeddium），不会因此崩。**

---

## 四、LSS 自带的"预生成/预热"能力（比让玩家各自跑 Chunky 强）

来自项目文档的配置项原文：

| 配置 | 默认 | 作用 |
|---|---|---|
| `enableChunkGeneration` | `true` | *"Generate missing chunks on demand, **so players see terrain nobody has visited**"* —— 按需在服务端生成区块 |
| `generationConcurrencyLimitGlobal` | `40` | 全服同时生成的区块上限 |
| `generationConcurrencyLimitPerPlayer` | `40` | 每玩家上限 |
| `lodStore` | `"on"`（新装） | 缓存每列压缩 LOD，*"repeat requests ... far less CPU and disk work per chunk"*，代价：**世界目录约 +70%** |
| `lodStoreBackfill` | `true` | *"**Pre-warms the store** with a low-priority background walk of your existing world, **so the first player to arrive already gets fast serves**"* |
| `lodStoreMaxMB` | `0` | 0 = 不限；设值则按最旧淘汰 |
| `lodDistanceChunks` | `512` | 最大 LOD 距离（区块） |

命令：

```
/lsslod stats                       # 每玩家传输统计
/lsslod diag                        # 详细诊断（配置/带宽/队列深度）
/lsslod store status                # LOD 库状态（状态、命中/未命中、大小）
/lsslod store backfill start|stop|status   # 后台预热（Paper 不支持）
/lsslod help
```

**官方对预生成的建议原文**：

> *"**Pre-generate the world.** Obviously the best thing you can do for player experience
> is avoid chunk generation by pre-generating your map with a tool such as Chunky."*

⇒ **需求方的思路与官方建议一致**：先用 Chunky 预生成，再让 LSS 分发。
（区别：LSS 的 `lodStoreBackfill` 是**预热 LOD 库**，Chunky 是**生成世界区块**，两者互补。）

---

## 五、客户端那条路的真实约束（必须知道）

LSS 文档给的 1.21.1/NeoForge 客户端方案是：

> *"**On NeoForge (1.21.1)** use **Roxy** to load the **Fabric** Voxy jar.
> Tested working with Roxy 0.2.0, **Voxy 0.2.16-beta**, **Sodium 0.8.12**,
> Forgified Fabric API 0.116.15."*

**但我们走的是另一条路**：自己编的 `j-shelfwood/voxy-neoforge`（Embeddium 版，0.2.9-alpha），
**没有 Sodium、没有 Roxy、没有 FFAPI**。

| | LSS 官方测试路径 | 我们的路径 |
|---|---|---|
| voxy | 原版 **Fabric** voxy 0.2.16-beta | 自编 **NeoForge** 移植 0.2.9-alpha（Embeddium 分支） |
| 加载方式 | Roxy 加载 Fabric jar | 原生 NeoForge 移植 |
| 渲染器 | **Sodium** 0.8.12 | **Embeddium** 1.0.15 |
| FFAPI | 需要 0.116.15 | 不需要 |

**⚠️ 这是本方案唯一没有验证的环节。**
支持我们这条路的两条证据：① LSS jar 自己的描述写 *"the client half needs a
**community Voxy build for NeoForge**"*；② §3.3 的反射目标类在我们移植版里 6/6 存在。

**⇒ 判定：可行，但必须实测。** 且有 fail-soft 兜底，最坏是"LOD 不显示"而不是崩溃。

---

## 六、落地步骤

### 服务端（`server/`，197 mod，`-Xmx5G`，已有 Chunky 1.4.23）

```
1. 装 LOD Server Support（NeoForge 1.21.1）到 server/mods/
2. 起服务端 → 生成 config/lss-server-config.json
3. 起 Chunky 预生成：/chunky radius <N>  →  /chunky start
   （按 DESIGN-PREGEN.md §5.1 的范围表决定 N；满视距 8192 方块 = 22h+，不现实）
4. 预生成完成后确认 lodStore="on"、lodStoreBackfill=true
5. /lsslod store status 看库建设进度；/lsslod store backfill status 看预热
```

### 客户端（每个玩家）

```
1. 已有：自己编的 voxy（Embeddium 版）+ Embeddium
2. 装 LOD Server Support（NeoForge 1.21.1）
3. 进服务器 → /lss diag 看连接与吞吐
4. 若远处 LOD 不显示：先试关掉环境雾（LSS 文档明确提到这一点）
```

> 注意：**服务端的世界（`server/world`，6.0 MB，几乎未生成）与需求方的单人世界是两个世界。**
> 要走这条路，等于把玩法搬到专用服务器上——这与"专注多人"的方向一致。

---

## 七、待验证清单（不猜，实施时用证据回答）

| # | 待验证 | 怎么验证 |
|---|---|---|
| 1 | LSS 的反射能否在我们 **0.2.9-alpha** 移植版上成功 | 客户端 `/lss diag`；日志里有没有 `Voxy compat: class not found / method not found` |
| 2 | 没有 Sodium 时 `SodiumStatusEntryHook` 会不会出问题 | 看启动日志有无 mixin 失败；该配置本身 fail-soft |
| 3 | LSS 服务端在我们 **197 mod** 的服务端包上能否启动 | 服务端起服日志；`:lsslod diag` |
| 4 | 预生成多大半径才值 | 按 §5.1 表 + `/chunky progress` 实测速率 |
| 5 | LOD 库的体积代价（文档说世界目录 +70%） | 看 `server/world/lss-lod/` 实际大小 |
| 6 | 与现有 `chunkplan` / `roadweaver` / 结构 mod 有无世界生成冲突 | 起服日志 + 生成区域抽样 |

---

## 八、这条路的优势（为什么值得做）

| | 各自预生成（`DESIGN-PREGEN.md`） | **服务端 + LSS（本文）** |
|---|---|---|
| 谁付出代价 | **每个玩家**各自熬几小时 | **服务端一次** |
| 新玩家 | 还要再熬一次 | 连上就有 |
| 没去过的地方 | 看不到 | `enableChunkGeneration` 按需生成 |
| 重复访问 | 每次都要重新 ingest | `lodStore` 缓存，重复请求极省 |
| 远景玩家 | 无 | `farPlayers`：2048 方块内显示其他玩家 |
| 分发 | 不涉及 | MIT，可直接引用 Modrinth 项目 |

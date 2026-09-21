# 性能与预生成方案（voxy 前置加载）

> **这份文档回答什么**：需求方说"本地加载地图太浪费时间，能不能预加载，
> 开启世界之前就加载出来，进去就能快点"。
>
> 日期：2026-09-22 · 数据来源：`logs/latest.log` + voxy 源码 + javap 字节码 + 本机实测

---

## 一、先把机制说清（这决定了"预加载"到底能不能做）

**voxy 的 LOD 是从"已存在的区块"吃出来的，它自己不生成区块。** 源码证据
（`MixinRenderSectionManager.voxy$tryIngestChunk`）：

```java
var chunk = chunkSource.getChunk(x, z, ChunkStatus.FULL, false);  // false = 不生成
if (chunk == null) { this.voxy$ingestChunkMissing++; return; }    // 没生成就放弃
if (VoxelIngestService.tryAutoIngestChunk(chunk)) { this.voxy$ingestSucceeded++; }
```

**⇒ 所以"在世界之外先把 LOD 建好"在原理上不成立**（没有区块就没有 LOD 可建）。
但需求方的**意图**可以达成：先把**区块**生成好，voxy 进游戏时直接读，不用边跑边生成。

---

## 二、实测数据（不是估算）

### 2.1 进世界时 voxy 各阶段（`logs/latest.log`，世界 `新的世界 (5)`）

```
00:08:44.121  Initializing voxy instance
00:08:44.151  Dedicated voxy thread pool size: 2
00:08:44.325  Creating new world engine
00:08:44.827  Creating and zeroing 2995MB geometry buffer（5.5GB free）
00:08:44.829  Successfully allocated the geometry buffer in 2ms   ← 分配只要 2ms，不是瓶颈
00:08:44.938  Falling back to NormalRenderPipeline（没装 Iris）
00:08:45.012  Voxy render system created，geometry capacity 3141369856
00:08:45.233  Dedicated voxy thread pool size: 1                    ← 掉到 1 个线程
00:09:09.4    renderOpaque first call                                ← 约 25 秒后
```

### 2.2 ingest 真实吞吐（`[VoxyDiag] ingestAttempts=...`）

```
00:09:28  ingestAttempts=200   succeeded=200  missingChunk=0  skippedCooldown=2206
00:11:01  ingestAttempts=1200  succeeded=1200 missingChunk=0  skippedCooldown=9327
```

| 读数 | 含义 |
|---|---|
| `succeeded = attempts`（1200/1200） | 每次尝试都成功 |
| **1200 区块 / 约 93 秒 ≈ 13 区块/秒** | **真实 ingest 吞吐** |
| `skippedCooldown = 9327` | 同一区块被反复跳过重试（冷却 2 秒）⇒ **需求远大于吞吐** |

**⇒ 瓶颈是 ingest 吞吐（~13 区块/秒），不是"区块没生成"。**
而 voxy 满视距需要 **105 万区块**，根本喂不满。

### 2.3 那个世界的生成程度（`saves/新的世界 (5)/region`）

**79 个 region 文件，几乎全是 0 MB**（只有 `r.-1.1.mca` 是 0.03 MB）
⇒ 世界基本上是空的，走进去才现场生成。这**加剧**了问题：生成与 ingest 抢同一批资源。

---

## 三、`serviceThreads` 是个真 bug 级的配置陷阱（已改）

源码 `VoxyClientInstance.updateDedicatedThreads()`：

```java
int target = VoxyConfig.CONFIG.getServiceThreads();
if (!VoxyConfig.CONFIG.dontUseEmbeddiumBuilderThreads()) {
    int builderThreads = RendererCompatManager.getBuilderThreadCount();
    if (builderThreads > 0) {
        this.setNumThreads(Math.max(1, target - builderThreads));   // ← 从 target 里减
        return;
    }
}
this.setNumThreads(target);
```

**`serviceThreads` 不是"voxy 自己的线程数"，而是 voxy + Embeddium 的总预算。**
默认 2，Embeddium 占 1 ⇒ **voxy 实得 1 个线程**（日志里先 2 后 1 就是这个原因）。
本机 CPU 是 **i3-12100F（4 核 8 线程）**，给 1 个线程建 LOD 明显偏少。

**已改** `config/voxy-client.toml`：`serviceThreads = 2 → 6`
（注释里写了机制与依据；配置的 "Range: 1~4" 是上游默认值，这里刻意越过，
若 F3 见 tick 变差就往下调）。

---

## 四、另一个独立问题：客户端 Xmx 12G 而机器只有 15.8G

从崩溃报告读到的实际值：

```
Memory: 3588151208 bytes (3421 MiB) / 12884901888 bytes (12288 MiB) up to 12884901888
                                                                       ^^^^^^^^^^^^ Xmx = 12 GB
```

机器总内存 **15.8 GB**。进世界时 voxy 还要额外要 **~3 GB 几何缓冲**，
当时日志报 "5.5GB of free memory"。**12G 堆 + 3G 缓冲 ≈ 15G，几乎顶满** ⇒ 会靠
内存压缩/交换撑着，体感就是"卡、慢"。

**建议：把 Xmx 降到 8G**（231 个 mod 够用），把省下的留给 voxy 的缓冲与系统。
这是需求方要自己决定的（改的是他实例的启动参数）。

---

## 五、预生成方案（Chunky，已在包里）

### 5.1 范围与耗时（这是决策的核心）

voxy 视距 `sectionRenderDistance=16` ⇒ `16 × 32 = 512 区块 = 8192 方块`。

| 覆盖范围 | 区块数 | 按 13/秒 | 按 20/秒（纯生成） |
|---|---:|---:|---:|
| 1024 方块（r=64） | 16,641 | 0.4 h | 0.2 h |
| **2048 方块（r=128）** | 66,049 | **1.4 h** | **0.9 h** |
| 4096 方块（r=256） | 263,169 | 5.6 h | 3.7 h |
| 8192 方块（r=512，= 满视距） | 1,050,625 | **22.4 h** | 14.6 h |

> **结论：想"整片预生成好"是 22 小时起步，不现实。**
> 现实做法是**只预生成核心区**，外围仍靠边玩边长。

### 5.2 做法（cheats 已开，实测三个世界 `allowCommands=1`）

在游戏里执行（Chunky 命令语法取自 jar 的 `CommandLiteral`）：

```
/chunky radius 128        # 或先 /chunky center <x> <z> 指定中心
/chunky start
/chunky progress          # 查进度
# 想停： /chunky pause   /chunky continue   /chunky cancel
```

**Chunky 与 voxy 同时跑会有 LOD 空洞风险**（DH 官方 wiki 对 DH 的同类警告；
voxy 侧未实测）。稳妥顺序：**先跑完预生成并退出，再进世界让 voxy 建 LOD**。

### 5.3 专用服务端做预生成（离线跑，不占自己机器）

`server/` 已有 **Chunky 1.4.23** 与 `-Xmx5G`，`run.bat` 可启动，RCON 凭据在
`tools/pack.local.json`（host 127.0.0.1 / port 25575）。

**但注意**：服务端的世界（`server/world`，11.4 MB）与需求方的**单人世界是两个世界**。
服务端预生成**帮不到单人档** —— 要帮多人，那需要 `LOD Server Support`
（服务端把 LOD 下发给所有装了 voxy 的客户端），是另一件事，见 §七。

---

## 六、诚实的边界：我没能端到端验证

- **`serviceThreads=6` 的效果我没有实测**（需要重启客户端进世界对比吞吐）。
- **预生成后 ingest 是否真的变快，我没有实测**。理论上会（`missingChunk` 与
  生成竞争减少），但 ingest 吞吐上限 13/秒是 voxy 自己的处理速度，
  **预生成不会提高这个上限**，只会让"每次尝试都命中"。
- 本节的耗时表是按 **13 区块/秒（ingest）** 与 **20 区块/秒（纯生成，估）** 两个
  口径给的；后者的 20/秒 是本机噪声下的估计，**不是实测**。

**⇒ 建议的验证顺序**：先只改 `serviceThreads` 进世界看体感 → 若仍慢，
再跑 `radius 128` 预生成 → 对比同一区域进世界后的 LOD 出现速度。

---

## 七、要真正解决"多人共享远景"，正确的路是服务端下发 LOD

需求方最终要的是**多人**。单人预生成只解决自己。
`LOD Server Support`（Modrinth，MIT，**有 1.21.1/neoforge 正式版**）
让服务端读/生成/缓存并**下发** voxy 列，客户端不探索也能看到远景。
它自带 `/dh pregen` 之外的预生成能力（服务端侧）。

> 上一轮我已核对过：`lod-server-support` 与 `voxy-server-side` 的 `modId` **都是 `lss`**，
> 装任一个都会撞 —— 只能选其一。较新的是 `lod-server-support v0.15.1+neoforge+mc1.21.1`。
> **本轮不实施**，记为后续选项。

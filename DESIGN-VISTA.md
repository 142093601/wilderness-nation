# 设计稿：史诗感 · 多人优先 · 超长视距

> **这份文档解决什么**：需求方看到一张「日落火山 + 超远视距」的截图，提出
> **放弃单人体验、专注多人**，用**预渲染 + 超长视距 + 地形放大**换**史诗感**，
> 并猜测预渲染能顺带缓解优化问题。
>
> 这不是"加两个 mod"的事：它要动**世界生成**（不可逆）、改**性能预算**、
> 并且会**要求开新档**。所以先出设计稿、把代价写清楚，再动手。
>
> 现状基线：`pack/` 231 个 mod · MC **1.21.1** + **NeoForge 21.1.250** · 渲染器是
> **Embeddium**（不是 Sodium）· 已有 **Terralith 2.6.2 + lithostitched**（地形/群系）
> · **已装 `chunky`** 与 `roadweaver`。
>
> 日期：2026-09-21 · 状态：**待拍板**（三个决策点见 §六）
>
> **已核完的一手证据**（不是读文档推断）：
> Modrinth API 的 `loaders` / `game_versions` / `dependencies` 字段 ·
> DH 官方 wiki 的 *Server Owners* 章节 · DH 3.3.1 changelog ·
> 本包 `pack/mods/` 的 231 份 `.pw.toml` 与 `embeddium` jar 内的 `neoforge.mods.toml` ·
> 以及我**下载并拆开**的 `voxy-server-side` / `lod-server-support` 两个 jar。
> 复查脚本留在 `tools/_dh_probe.py`、`tools/_vss_inspect.py`、`tools/_sodium_v.py`（已被 gitignore）。

---

## 〇、先纠正三条事实（都取自官方 API / 官方 changelog）

| 说法 | 事实 | 证据 |
|---|---|---|
| 加 **chunky** 预渲染 | ✅ **已经在包里了** | `pack/mods/chunky.pw.toml` → `Chunky-NeoForge-1.4.23.jar`。`roadweaver` 也在。 |
| **Distant Horizons 是替代** | ✅ 可用 | `distanthorizons` 3.3.1-1.21.1：`loaders: ["fabric","neoforge"]`、`environment: client_or_server_prefers_both`。**1.20.6+ 起 Fabric 与 NeoForge 共用一个 jar**。 |
| 加 **voxy** | ⚠️ **不是"没有"，是"卡在授权与依赖上"** | 见 §〇之补。**我曾判它"无 1.21.1 版本、仅 Fabric"——这个判断对原版 voxy 成立，但漏了服务端侧与社区移植，判早了。** |

**结论：目标可达，但走 voxy 要付的代价远高于 Distant Horizons（DH）。**

---

## 〇之补 · voxy 路线：把账算完（需求方补充了线索，我重查后修正结论）

需求方给出 [voxy-server-side-neoforge](https://github.com/wish131400/voxy-server-side-neoforge)。
我把它查到底了 —— **项目真实存在且质量不低，但它不是 voxy 本体的替代品，而是"服务端半边"。**

### 这条链上的四个自制件

| 件 | 是什么 | 1.21.1 / NeoForge 可用性 | 授权 |
|---|---|---|---|
| **原版 voxy** | LOD 渲染器**本体** | ❌ Modrinth `game_versions` 最老 **1.20.4**，无 **1.21.1**；`loaders: ["fabric"]` 仅 Fabric | **ARR**（All Rights Reserved） |
| **voxy-neoforge**（j-shelfwood） | 社区**非官方**移植到 NeoForge 1.21.1 | ⚠️ 作者自标 **Alpha**、"functional with known limitations" | 派生自 ARR 原作；README 只给"个人使用" |
| **VSS / LSS**（服务端半边） | 服务端读/生成/缓存/下发 Voxy 列 | ✅ **Modrinth 上有 neoforge + 1.21.1 正式版** | **MIT** |

### 我实际下载并拆开了那两个服务端 jar（一手证据，不是读文档）

`data/_probe/` 下两个 jar 的 `META-INF/neoforge.mods.toml`：

```
voxy-server-side-neoforge.jar      0.14.0   MIT   displayName="Voxy Server Side"        modId="lss"
lod-server-support-neoforge-...jar 0.15.1   MIT   displayName="LOD Server Support"      modId="lss"
```

两个关键事实（**这是拆包得出的，不是推断**）：

1. **它们是同一个 mod 的两次发布** —— `modId` **都是 `lss`**、描述同源、
   `VoxyCompat` 等同名类都在 `dev/vox/lss/`。
   → **装其中任一个都会和另一个撞 modId，不存在"两个一起上"。**
   较新的是 **LOD Server Support `v0.15.1+neoforge+mc1.21.1`**（2026-09-21，比 VSS 08-28 新）。
2. **jar 自己承认客户端缺一半**（描述原文）：
   > *"Server-side works with any LSS/VSS client; **the client half needs a community Voxy build for NeoForge**."*

   拆出的类里 `voxy-namespaced classes: 12`，**全是 `VoxyCompat` / `VoxyStorageOverride` 这类桥接类，没有一个渲染器**。
   → **服务端 jar 不含 voxy 渲染器，它只是把 LOD 喂给客户端那份社区构建的 voxy。**

### 于是这条路的真实代价（三条，任何一条都够卡住）

| # | 卡点 | 依据 |
|---|---|---|
| **1（致命）** | **voxy 客户端不能随包分发** | voxy-neoforge README 原文：*"Due to Voxy's ARR license, **compiled JARs are not distributed. You must build from source**."* → packwiz 发不出去，**每个玩家得自己 `./gradlew build`**。多人服务器上这不是"麻烦"，是**不可行**。 |
| **2（硬冲突）** | **必须用 Sodium 换掉 Embeddium** | 我读了包里 `embeddium-1.0.15+mc1.21.1.jar` 的 `neoforge.mods.toml`：<br>`modId = "sodium"` / `type = "incompatible"` / *"Embeddium cannot be used if Sodium is present."*<br>而 voxy-neoforge 要求 `Sodium mc1.21.1-0.6.13-neoforge`。<br>→ **两者不可能共存**，这条路等于**换渲染器**（违反反目标 2）。 |
| **3（额外负担）** | **多一层 Fabric 兼容层** | voxy-neoforge 依赖 **Forgified Fabric API `0.116.7+2.2.0+1.21.1`**（1.21.1 上已到 `0.116.15+2.3.5+1.21.1`）。我们包里没有任何 FFAPI / Connector —— 等于为了一个 Alpha 渲染器，往 231 mod 的 NeoForge 包里塞一整套 Fabric 模拟层。 |

### 还有两个应当记下的矛盾（实施前必须解，本轮不解）

1. **Sodium 版本打架**：voxy-neoforge 文档写需要 **0.6.13**，
   而 Sodium 在 1.21.1/NeoForge 上的最新 release 已是 **`mc1.21.1-0.8.13-neoforge`**。
   → LSS 的 mixin 配置里同时有 `lss-sodium-legacy.mixins.json`，
   说明作者在处理 0.6↔0.8 的差异，但**"voxy 移植针对 0.6.13 写的、能不能跑在 0.8.13 上"没有证据**。
2. **`lss` 的 mixin 直接改 Sodium** → 与本包"换渲染器"的历史教训（`CONFLICTS.md` **C10**）正面相撞。

### 裁决

> **DH 与 voxy 都能达成"服务端下发 LOD、玩家不探索就能看到远景"这个目标。
> 但 DH 是：两边各装一个 jar（Fabric/NeoForge 共用、`client_or_server_prefers_both`、无依赖声明、无授权问题）。
> voxy 是：换渲染器 + 加 Fabric 兼容层 + 每个玩家自行编译 ARR 源码。
> → 同一收益下，DH 的代价是 voxy 的零头。选 DH。**

**并且 LSS/VSS 这条路反而证明了 DH 的一个优势**：
DH 的服务端 LOD 下发是**内置**的，不需要再装第三方桥接 mod ——
省掉一整个"服务端桥 + 客户端 modId 冲突"的维护面。

### 如果需求方坚持走 voxy（我会照做，但先摆代价）

必须同时接受：**① 玩家自行编译** 或 **② 自建分发（需评估授权）**；
**③ Embeddium → Sodium 替换**（必须重跑 `CONFLICTS.md` C10 那一整轮）；
**④ 接受 voxy-neoforge 的 Alpha 状态**。这四项都不是我能替需求方承担的，
所以**这是一次需要明确点头的转向，不是一次 mod 安装**。
**我目前的建议是不走**，理由如上。

---

## 一、定位（一句话）

> **把"看得见多远"变成一种玩法资源**：站在自家城墙上能一眼望到几十公里外的雪山轮廓，
> 于是「选址 / 建塔 / 远征」第一次有了**视觉上的回报**。

配套的反目标（明确不做什么）：

| # | 反目标 | 理由 |
|---|---|---|
| 反 1 | **不为截图牺牲可玩性** | 视距开到 4096 谁都跑不动；先定一条"能跑"的下限，再往上调。 |
| 反 2 | **不换渲染器** | 本包是 Embeddium，且 `CONFLICTS.md` C10 记着 Sodium 系与我们互斥（sodium-extra 那次的教训）。DH **不要求**换渲染器。 |
| 反 3 | **不动已生成的存档** | 世界生成类 mod 加进已有世界 = 地形接缝。见 §四。 |
| 反 4 | **不为了史诗感加内容 mod** | `DESIGN.md` §十五 已否掉"靠加结构 mod 补内容"。这里只动"看得见"的层。 |

---

## 二、三个组件各自的真实作用

### 2.1 Distant Horizons（DH）——**真正降渲染压力的那个**

- 它把远处地形降成 **LOD**（细节层级），越远越粗 —— 这是"超长视距但不太贵"的机制本身。
- 默认 LOD 视距 **256 区块**，可到 4096（官方提示 >512 会吃满 VRAM/GPU）。
- **双端能力**（对我们最重要的一条，来自官方 README）：
  - **只在客户端装** → LOD 存在本地，**必须自己探索过才加载**；
  - **服务端也装** → **LOD 自动发给每个装了 DH 的玩家**（需客户端开 `Distant Generation Enabled`）。
- 官方支持**模组地形**，画廊里专门有一张 **Terralith 2.6.4** 的示例图 —— 与我们已装的 Terralith 对得上。

### 2.2 Chunky（已在包里）——**预生成区块**

用途是"先把地生成好"，不是"渲染更快"。官方命令（README 原文）：
`chunky radius 1000` + `chunky start`，另有 `pause/continue/cancel/progress/trim`。
需要一个 op（专用服务端）或单人开 cheats。

### 2.3 Tectonic（待装）——**地形放大**

- 我们所需的版本：**3.0.28-neoforge-21.1**（release，2026-09-11）。
- 作者原话：*"Tectonic's supported versions right now are **1.21.1** and 26.1"* —— 锁得很准。
- 内容：大陆/岛屿、**深海洋**（可探到 deepslate 层）、**绵延上万格的山脉**（可接近建造上限）、
  地下河、熔岩隧道、丛林巨柱、峡谷、沙丘、台地、湿地。
- **与 Terralith 的关系**：官方说 *"Tectonic has a special version with Terralith compatibility.
  This version is shipped in **the mod version** of Tectonic."*
  → **mod 版本已内置该兼容，不需要另找 terratonic 数据包。**

---

## 三、⚠️ 一个必须先解开的冲突：Chunky × DH

DH 官方 changelog 两次点名：

> **2.3.3-b**：*"Mark Chunky as incompatible — Chunky often causes **holes in the LODs**.
> To use Chunky run it before installing DH, then uninstall it and install DH.
> Otherwise use DH's built in world generator."*
>
> **3.3.0**：*"Improve database speed with Chunky and C2ME"* 但
> *"Using Chunky and DH **isn't recommended** due to the possibility of holes；
> having DH use the same thread count as Chunky should help reduce those issues."*

**也就是说：需求方想要的"Chunky + DH 同时在场"，正是 DH 官方劝退的组合。**
但注意 2.3.4-b 又写 *"Remove Chunky incompatibility"* —— 说明后来的版本允许共存，
只是**仍有 LOD 空洞的风险**（"on mis-configured systems"）。

### 三条可选路线（按推荐度）

| 路线 | 做法 | 代价 |
|---|---|---|
| **A（推荐）阶段化** | **先**只装 Chunky，把核心区预生成完（`chunky radius …` + `start`）→ **跑完再装 DH** | 要一次停机/换包；但完全避开"同时在场"的空洞风险。**DH 读已生成的区块是最稳的**。 |
| **B 用 DH 自己的预生成** | 服务端装 DH 后执行 **`/dh pregen`**（**官方推荐**，见下） | 最难空洞；但要求 DH 在服务端常驻 |
| **C 两者同时在场** | 照需求方原意 | 官方明说"不推荐"，可能出现 LOD 空洞/单柱体 |

### ⭐ 官方 wiki 的权威结论（这条**改写**了我上面的推荐）

我拉到 DH 官方 wiki 的 **Server Owners** 章节（作者 s809，2025-02-25 扩充），
它对"用什么预生成"给了一段**直接否定"靠 Chunky"**的说明：

> **Pre-generation → Server-side**：*"Easy way to pre-generate. **Avoid running it on a server
> you're actively playing on**, to avoid dealing with bad TPS while the generation is ongoing."*
> 命令：**`/dh pregen`**
>
> *"If you want generate chunks alongside LODs, set `generation.mode` to `INTERNAL_SERVER`
> before proceeding. Adding C2ME is recommended in that case as it will multithread the generation."*
>
> *"**Running Chunky will also generate & update LODs, but it's not recommended to rely on this
> behavior for pre-generation.** LODs and chunks are independent, and by loading a lot of chunks
> quickly you risk overloading DH and ending up with **a lot of holes**.
> Either use DH's pre-generation, or temporarily disable the mod entirely."*

**这同时回答了需求方的方案和我的备选方案：**

| 结论 | 内容 |
|---|---|
| ✅ 需求方"预渲染"的直觉 | **对** —— 官方就是这么做的，还专门有一节讲它 |
| ⚠️ 但"用 Chunky 预渲染" | **官方明确不推荐把 Chunky 当 LOD 预生成手段**（会过载 DH、产生大量空洞） |
| ✅ 正解 | **`/dh pregen`**（DH 自带预生成），且"**别在正在玩的服务器上跑**" |
| 🆕 变更 | DH **3.3.0 新增了"high-speed surface world generator"**（1.19.2+）→ DH 自带的生成能力这一版才真正够用 |
| 🆕 变更 | DH **3.3.0 起会把线程数与 Chunky/C2ME 联动**（"having DH use the same thread count as Chunky should help reduce those issues"）→ 说明作者仍在**降低**共存问题，而非仅劝退 |

**修正后的推荐：路线 B（`/dh pregen`）优先，Chunky 退为"独立用途"**（生成纯区块、与 LOD 无关的场合）。
需求方原意"用 chunky 预渲染"需要改成"**用 DH 自己的预生成**"，而且这反而更省事——
不用先跑完再换包（我原来推荐的路线 A 要一次停机换包，现在可以省掉）。

> ⚠️ 仍需实测：DH 官方 wiki 是**通用**文档，我们的包有 Terralith + 一堆结构 mod，
> `generation.mode=INTERNAL_SERVER` 在**模组地形**下的表现要按 §七 验收判据实测。

---

## 四、代价（必须接受的，不能只说好处）

| # | 代价 | 量级/依据 |
|---|---|---|
| 1 | **世界生成不可逆** | `CONFLICTS.md` **R2/R3** 与 `DESIGN.md`：Terralith 装了就不能从已有世界增删；加 Tectonic 同理。**已有世界会出现地形接缝**（旧区平原、新区巨山）。 |
| 2 | **磁盘** | Chunky 官方示例用 **radius 2000**（= 4000×4000 格 ≈ 1600 万格²，约 **25000 区块²** 量级）——**但预生成改走 `/dh pregen` 后，量级由 DH 自己的配置决定，需实测**。DH 的 LOD 数据库另有体积（3.3.0 起号称省 50% 磁盘 I/O，`data/` 下）。 |
| 3 | **服务端 CPU 时长** | 预生成以**小时**计（官方截图用 Ryzen 3700X 跑 3 个世界）。**这是"服务器上可以接受"那句判断的代价所在。** 官方要求**别在正在玩的服务器上跑**。 |
| 4 | **客户端 GPU/VRAM** | DH 视距 >512 区块会吃满 VRAM（官方原话）。这是**每台玩家机器**的门槛，不是服务器能替你扛的。 |
| 4b | **客户端 OpenGL 门槛** | DH **3.2.0 起最低要求 OpenGL 3.3**（changelog：`Increase the minimum required OpenGL version 3.2 -> 3.3`）。**多人意味着每个玩家的显卡都要过这一关**——这是"专注多人"新增的、服务器管不了的门槛。需要一份"玩家机器要求"说明。 |
| 5 | **开新档** | 见 §六 决策 1。 |
| 6 | **回归面变大** | 新引入一个 28 MB 的大 mod（DH）+ 一个世界生成 mod（Tectonic），要重跑装机冒烟、世界生成、性能基线。 |

### 四之补 · Tectonic 与包里**已有的结构 mod** 会打架（这是真正的风险面）

我核过 `pack/mods/`，本包**不是**"Terralith + 空白"——它压着**一整套地形依赖型结构**：

| 类别 | 已装 |
|---|---|
| YUNG 全家桶（10 个） | `YungsBetterCaves` `BetterDungeons` `BetterEndIsland` `BetterJungleTemples` `BetterMineshafts` `BetterNetherFortresses` **`BetterOceanMonuments`** `BetterStrongholds` + `YungsApi` |
| 大型结构集 | `repurposed_structures` `DungeonsArise` `dungeons-and-taverns` `AdditionalStructures` `grim-kingdoms` `tidal-towns` |
| 地形/群系基建 | `terralith` 2.6.2 · `lithostitched` 1.8.0+beta6 · `cristellib` |
| 其他地形敏感 | `waystones`（地表/村庄锚点）· `morevillagers` · `roadweaver`（**按地形铺路**） |

**为什么这是风险**（按 `worldgen-and-structures` 的三层思路拆）：

1. **放置高度层**：Tectonic 把海洋挖深、山脉拉到近建造上限。
   所有"按原版高度带放置"的结构（海洋 monument、村庄、矿道、要塞）会**移位、埋掉或悬空**。
   `BetterOceanMonuments` 与 `repurposed_structures` 是直接受害者。
2. **`roadweaver` 是双重敏感**：它既**读**地形铺路，其产出的路网又会**成为世界的一部分**。
   地形一改，旧路网语义就变了。这一个 mod 就可能需要重新调参。
3. **`lithostitched` 是共享基建**：Terralith 靠它注入。Tectonic 的"Terralith 兼容版"也在这一层做手脚。
   **两家都改同一层注入器 ⇒ 这是"世界生成重叠"类冲突，不是普通的配方打架。**
4. **`chunkplan` 也在包里**（区块加载策略）——与 Chunky/DH 的线程争抢会叠加。

> **处置原则**（沿用 `CONFLICTS.md` 的优先级）：
> 先**不改**这些结构 mod，而是**用一次可逆的新档验证**——新世界里飞 2000 格采样：
> 结构还在不在、有没有悬空/埋没。**只有在看到具体坏掉的结构后才动版本或换 mod**，
> 不预先"顺手升级"（`version-and-dependency-matrix` 的规矩：先读真实依赖，再决定动不动）。

**这条改变了决策 1 的分量**：加 Tectonic 不只是"地形变好看 / 要开新档"，
而是**要把整套结构 mod 的放置行为重新验一遍**——这是**天级**工作量，不是小时级。

---

## 五、回答那个性能疑问（**这部分要说准**）

> 需求方问：*"区块预渲染的话，是不是就能适当减轻一点游戏的优化问题？"*

**答：有一半对，但机制不是"预渲染让渲染更快"。**

### 预渲染**确实**能减的

1. **消除"边走边生成"的卡顿** —— 未生成区块走进去时要现场跑 worldgen（服务端 CPU 尖峰）。
   预生成之后，服务端只需从磁盘加载。这是**真实收益**，也是"预生成"这件事存在的理由。
2. **让 DH 有东西可读** —— LOD 是从"已生成的区块"建的。预生成完，玩家第一次抬头就能看到全景，
   而不是"边玩边等 LOD 长出来"（客户端单装 DH 时尤其明显）。
3. **避免 Chunky 与 DH 抢线程** —— 官方为此专门做了线程数联动（3.3.0）；
   更彻底的做法是用 `/dh pregen`，从根上不产生这场争抢（§三）。

### 预渲染**不能**减的（别误读）

1. **客户端绘制负载不变** —— 近处方块该渲染还是要渲染。DH 是**用 LOD 降级换**远视距，
   不是"预渲染让远处免费"。
2. **DH 自己的 LOD 构建与渲染开销** —— 那是 GPU/VRAM 与 CPU 线程的成本，
   视距开越高越大（官方：>512 区块吃满 VRAM）。
3. **磁盘只增不减** —— 预渲染把 CPU 时间换成磁盘空间，**不是净优化**。

### 一句话结论

> **预渲染买到的是"没有探索卡顿 + 全景立刻可见"；
> 买到"更流畅的远景"的是 DH 的 LOD 机制，不是预渲染。两者叠加才有那个画面。**

而"专注多人"确实**放大了预渲染的性价比**：单人开档前等几小时不可接受，
**专用服务端可以离线跑**（玩家不在时也能生成）—— 需求方这个直觉是对的。

---

## 六、三个必须拍板的决策

### 决策 1（**最关键**）：愿不愿意**开新档**，以及**这一轮要不要一起上 Tectonic**？

- **开新档 + 一起上 Tectonic**：地形放大 + 火山/巨山按截图那样出现，零接缝。
  但**必须验完整套结构 mod**（YUNG 全家桶 / repurposed_structures / tidal-towns / waystones …），
  这是**天级**工作量（§四之补）。好处是"重开一次"和"验结构"两笔成本只付一次。
- **开新档 + 本轮只上 DH**：DH 是**纯客户端渲染层，不动世界生成**，风险极低、
  当天可验收，史诗感先拿到**一半**（超长视距有了，地形还是原尺度）。
  Tectonic 留到下一轮——那时**已经知道结构们在新尺度下坏不坏**，可以慢慢调。
- **不开新档**：**Tectonic 不能加**（已有世界会出地形接缝，不可逆）。
  只能加 DH。

**我的建议：第二轮路线**（开新档这件事本身由需求方定，但 **Tectonic 先别一起上**）。
理由：DH 与 Tectonic 是**两个独立风险**，
DH 的风险是"渲染器兼容"（可当天证伪），Tectonic 的风险是"整套结构放置"（天上的工作量）。
**把它们绑在一轮里，等于用一个未验的大风险去挡一个小风险。**
先上 DH → 拿到确定收益 + 建立新档 → Tectonic 单独作为一轮做，验完再合。

> 若需求方要的是"截图那一眼的震撼"，那**关键是 DH 的视距**，地形放大是加成不是前提。

### 决策 2：预生成用 `/dh pregen` 还是 Chunky

**默认建议：`/dh pregen`**（DH 官方推荐；Chunky 只用于"生成纯区块"的独立场合）。
见 §三 那条官方 wiki 引用——**需求方原方案里"用 chunky 预渲染"这一环需要替换**。

### 决策 3：DH 视距目标值

| 档 | LOD 视距 | 依据 |
|---|---|---|
| 保守 | **256** | DH 默认值 |
| **推荐** | **512** | 官方示例（"Minecraft render distance 8 + mod distance 512"就是那张宣传图的配置） |
| 激进 | 1024+ | 官方提示 >512 起 VRAM 吃紧 |

**默认建议：512**，并把它写进性能预算；玩家侧提供"降档"说明（F3 之外，DH 有自己的配置界面）。

---

## 七、实施顺序（**顺序不能反**）

### 本轮（DH 轮）——风险低，当天可验收

```
0. 前提：Tectonic 不进这一轮（§六 决策 1 的建议）
1. 装 DH 3.3.1-1.21.1（客户端 + 服务端都装；共用一个 jar）
2. 起服务端 → 确认 /dh help 可用、服务端识别到 DH
3. 预生成：/dh pregen（可先设 generation.mode=INTERNAL_SERVER）
   ⚠️ 官方原话："Avoid running it on a server you're actively playing on"
   → 离线/挂机时段跑，跑完再放玩家进来
4. 客户端连同 DH 进世界 → 用 dsh-computer-use-win 截图验收（我自己能看）
5. 记性能基线：MSPT / TPS / DH 视距 / 显存占用 → 写进 DEFERRED.md 与 DESIGN.md §12
6. 若 LOD 出现空洞：先查 /dh 配置与线程数（3.3.0 起与 Chunky/C2ME 联动），
   再考虑改用「先 Chunky 纯生成区块 → 再装 DH」的路线 A
```

### 下一轮（Tectonic 轮）——风险高，天级工作量

```
1. 装 Tectonic 3.0.28-neoforge-21.1（保持 Terralith 2.6.2 / lithostitched / cristellib 不变）
2. packwiz refresh → 装机冒烟（必须在建世界之前，见 CONFLICTS.md R3）
3. 建**新**世界（旧世界不动、不删）
4. 结构放置验收：飞/传 2000 格，采样 YUNG 全家桶 / repurposed_structures /
   tidal-towns / waystones / roadweaver，看有没有悬空、埋没、缺失
5. 坏了才动：先 config → 再数据包 → 再换版本 → 最后才考虑换 mod（CONFLICTS.md 优先级）
6. 通过后再合并进正式世界生成
```

**验收判据（可判定，不靠感觉）**

| 项 | 判据 |
|---|---|
| 装得上 | autotest 进世界成功、无新增崩溃报告 |
| 视距确实超远 | F3 里 DH 显示 LOD 视距 = 配置值；截图能看到远处的山形轮廓 |
| 没有 LOD 空洞 | 沿直线走/飞 1000 格采样截图，看远景是否有洞（§三 的已知风险） |
| 性能不塌 | MSPT 与 TPS 与改造前对比（`DEFERRED.md` E1 那套基线方法） |
| 任务书不受影响 | `verify_questbook_on_server.py` 仍 VERIFY PASS（渲染层变了，任务数据没变） |
| （仅 Tectonic 轮）地形确实放大 | 目视：出现 >200 格连续山体 / 深海洋（截图留证） |
| （仅 Tectonic 轮）结构没坏 | §七 下一轮第 4 步的采样结果 |

---

## 八、明确不做（本轮）

- ❌ **voxy 路线** —— 授权（ARR，玩家须自行编译）+ 硬冲突（须换渲染器）
  + 额外兼容层（FFAPI），见 §〇之补。**若需求方明确要求，则另立一轮并先解决那三项前置。**
- ❌ **LSS / VSS 服务端桥** —— 与本轮无关：DH 的**服务端 LOD 下发是内置的**，不需要第三方桥。
  （另：那两个 Modrinth 项目 `modId` 都是 `lss`，彼此冲突。）
- ❌ **换 Sodium 系渲染器** —— C10 的教训；DH 不要求
- ❌ **把视距开到 1024+ 当默认** —— 先 512，跑得动再谈
- ❌ **本轮（DH 轮）就上 Tectonic** —— 结构验不完（§四之补）
- ❌ **在已有世界上加 Tectonic** —— 不可逆接缝
- ❌ **为截图改任务书内容** —— 任务书与渲染层解耦，两边独立验收

---

## 八之补 · 一个独立的正经收益（与渲染无关）

需求方说"完全舍弃个人游玩体验、专注多人"——这句话**顺带解决了一个已记录的旧问题**：

- `DEFERRED.md` 里挂着「内容量只有 300 小时」的复核。
  但**多人**的时间尺度与单人不同：**基建可以分工并行**，
  同样 297 个任务分摊到 3~5 人，**每人需要的时间反而更长**（协调成本 + 建造规模）。
  → 所以"内容量不够"在多人语境下**不一定是问题**，需要重估而不是直接补量。

这条**不阻塞**当前工作，但它说明：**是否补任务量，应该在多人假设下重新算一遍**，
而不是按单人 300 小时那把尺子。**先记着，不在本轮做。**

---

## 九、待核实（不猜，实施时用证据回答）

| # | 待核实 | 怎么核实 |
|---|---|---|
| 1 | DH 3.3.1 与 **Embeddium** 是否硬冲突 | 装后跑 autotest + 进世界截图；有崩溃就查 `CONFLICTS.md` 的分类流程 |
| 2 | DH 与 **Terralith** 的 LOD 是否正确（官方画廊用的是 Terralith 2.6.4） | 截图比对（我们的 Terralith 是 2.6.2，同一小版本线） |
| 3 | `chunky radius` 选多大 | 按"玩家 300 小时内可能走到多远"定；先用 radius 2000 试，看磁盘与时长 |
| 4 | 专用服务端内存预算是否够 | 现 `-Xmx5G`；DH 服务端侧与预生成都要内存，可能要调（`user_jvm_args.txt`） |

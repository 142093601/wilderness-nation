# 标准件生成器（原型）

> **目的**：用代码生成 Minecraft 建筑图纸，把"图纸库"从**手工盖 25 小时**降到**写生成器 + 游戏内验证**。
>
> 本文件记录**实测数据、已验证/未验证的边界、以及实测出来的限制**——不是设想，是跑过之后的结果。

---

## 一、实测结果（2026-09-14 本机）

| 项 | 结果 |
|---|---|
| 环境 | Python **3.11.6** + pip 26.0.1；`mcschematic` + `nbtlib 2.0.4` 安装成功 |
| 产出 | **6 个构件**（城墙段 / 哨塔 × 3 风格）× **两种格式** = 12 个文件 |
| **回读校验** | **6/6 通过**（尺寸 / 方块数 / 材质数完全一致） |
| `.nbt` 格式核对 | gzip 魔数 `1f8b` ✓ · 根键 = `size` / `palette` / `blocks` / `entities` ✓ · palette 只有 `Name`、无 `Properties` ✓ |
| **吞吐** | 6 个构件 **282 ms**（含 Python 启动）；单件 23~43 ms → **100 个构件只需几秒** |
| **体积** | 639 方块 → **1.8 KB**；1409 方块 → **3.8 KB**（≈2.6 字节/方块）——体积不构成问题 |
| **风格切换成本** | **4 行代码**（在 `STYLES` 里加一条）——三套风格复用同一套结构逻辑 |
| 参数化 | 长 / 高 / 厚 / 门洞宽高 / 层高 / 占地 全是参数 |
| 自动产出 | 材料清单统计 · 可通行性校验 · 内部净高校验 |

---

## 二、已验证 vs 未验证（**必须分清**）

### ✅ 已验证（本机实测）

- 生成 + 双格式导出（`.schem` / `.nbt`）都能产出
- `.nbt` 是 gzip NBT，且根键与**结构方块格式**一致
- 回读校验通过（文件内容与内存对象一致）
- 吞吐与体积（见上表）
- 风格 = 改一个 dict；尺寸 = 传参数
- 材料清单与两类校验自动产出

### ❌ 未验证（**必须进游戏才能确定**）

1. **Create 的 Schematicannon 是否接受这个 `.nbt`** —— 是否要求 `DataVersion`、是否容忍无属性方块、是否容忍缺 `nbt` 字段。**这是唯一的关键未知。**
2. **生成结果好不好看** —— 只能人眼看。
3. Portable Blueprints 能否直接吃 `.nbt` / `.schem`（若可以，玩家侧链路更短）。

---

## 三、一条实测出来的限制（决定"能省多少"）

生成器输出的是**纯方块 ID、不带任何属性**（`minecraft:stone_bricks`，不写 `[...]`）。

- **好处**：几乎**不可能产出非法方块状态**——这是"代码盖房子"最大的坑。
- **代价**：做不出**带方向的细节**——楼梯、台阶、半砖、栅栏、玻璃板、门、梯子、告示牌都需要属性（`facing` / `half` / `type`）。

所以生成器能做的是**体量与骨架**（墙体、楼体、房间分隔、垛口、窗缝开口），做不出**屋檐、栏杆、窗格、阶梯**。

> **替换比例：能替掉 60~75% 的工时，剩下 25~40% 仍需人工（细节 + 审美）。**
> 要做细节也可以，但引入属性后风险上升 —— **第一版不做**。

**关键收益**：**模块升级档位**（农田 I/II/III、城墙木→石→铁）从"重盖三遍"变成**改参数**，正好支撑设计稿里的"升级树"。

---

## 四、两种格式的分工（不是一回事）

| 格式 | 谁用 | 用法 |
|---|---|---|
| **`.nbt`**（结构方块格式，gzip） | Create 的 Schematicannon；Portable Blueprints（待核实） | 直接放进实例的 `schematics/` 目录 |
| **`.schem`**（Sponge 格式） | WorldEdit（需 op） | `//schem load <名>` + `//paste` |

若 Create 不认 `.nbt` → **退路**：用 WorldEdit 贴 `.schem`（成熟做法，但需 op、玩家侧不便），或去研究 Create 的确切格式要求。

---

## 五、用法

```powershell
cd <仓库路径>\tools
python gen_structures.py out
```

- **加一种风格**：在 `STYLES` 里加一条（4 行：主体 / 装饰 / 墙基 / 点缀四种材质）
- **改尺寸**：直接传参 —— `wall_segment(length=, height=, thickness=, gate_width=, gate_height=)`、`watchtower(footprint=, height=, floor_h=)`
- **输出**：`out/schem/` · `out/nbt/` · `out/report.json`（尺寸、材质清单、校验结果）

> ⚠️ **控制台里中文会乱码**（Windows 控制台编码），但**文件内容是正确的 UTF-8**。要看报告请打开 `out/report.json`。

---

## 六、待实测的那一步（10 分钟，需要你）

1. 把 `out/nbt/*.nbt` 放进整合包实例的 **`schematics/`** 目录
2. 用 **Create 的 Schematicannon** 对着放一次 `wall_ironhold`
3. 看三件事：**认不认文件** · **放出来对不对**（门洞能过、垛口整齐）· **好不好看**

三种结果对应三条路：

| 结果 | 下一步 |
|---|---|
| 认且好看 | 扩展成 **10 个标准件 × 3 风格**，并加功能占位 |
| 认但难看 | 调造型参数（比例、材质、垛口节奏）再出 |
| **不认** | 改走 `.schem` + WorldEdit；或去查 Create 的确切格式要求 |

---

## 七、对时间估算的影响

| 项 | 原估 | 现在估 | 说明 |
|---|---|---|---|
| **图纸库** | 25h（悲观 60h） | **10~14h** | 生成器 4~6h + 游戏内验证调参 4~6h + 细节人工 2~4h |

**省下的是重复劳动**（同类构件的多风格 / 多档位复制），**不省设计思考**（每类构件长什么样、要什么功能、放哪里，仍要人定）。**不要指望归零。**

---

## 八、踩到的坑

1. **校验必须按构件类型区分** —— 第一版拿"建筑物净高"去检查"城墙段"，城墙段本来就没有内部空间 → **假阳性**。校验器本身也需要被验证。
2. **控制台中文乱码** —— PowerShell 编码问题，文件内容正确。
3. **纯方块 vs 带属性** —— 安全性 vs 表现力的直接取舍（见第三节）。
4. **空气不记录**（稀疏体素）—— 文件小、不会误填；但要注意别让单个错位方块把包围盒撑大。

---

## 九、下一步扩展计划

1. **标准件扩到 10 类**：仓库 / 农田 / 熔炉阵列 / 道路 / 城门 / 哨塔 / 店铺（武器·防具·饰品·食物）/ 驿站 / 传送节点 × **3 风格**
2. **功能占位**：生成器出"壳"，但必须预留**箱子、工作台、商店方块、门**的槽位——否则会得到一堆**漂亮但没用的房子**
3. **可选（第二版）**：属性化细节（楼梯 / 栏杆 / 窗格）
4. **可选**：把"升级档位"做成参数（I / II / III）
5. **校验增强**：材料总成本、照明覆盖、门口连通性、与领地边界的尺寸对齐

---

## 十、复盘数据采集（自动采集 + 事后分析）

**目的**：玩的时候自动攒数据，事后回答"**我们当初的设计对不对**"——而不是靠感觉复盘。

### 三个文件

| 文件 | 作用 |
|---|---|
| `collect_snapshot.py` | 采集一份快照：原版 `world/stats/*.json`、`advancements/`、`level.dat`、`usercache.json`、`logs/latest.log` |
| `analyze_snapshots.py` | 把快照序列**差分**成时间线，产出 `report.md`（挂在设计假设 H1~H7 上） |
| `run_daily.ps1` | 采集 + 刷新报告的一键封装（建议每 30 分钟跑一次） |

### 数据从哪来（关键：**零 mod、零脚本、不改游戏**）

Minecraft 原版就在为每个玩家写 `world/stats/<uuid>.json`，里面有：`play_time`（累计在线 tick）、挖掘 / 使用 / 合成 / 击杀 / 死亡、移动距离（`*_one_cm`），以及大量自定义统计——**包括 `bell_ring`，正好是我们的"敲钟集结"**。

**核心机制是差分**：单份快照没有意义，**相邻快照的增量**才说明"这段时间做了什么"。所以只要让它按周期自动跑，时间线会自己长出来。

### 已实测（合成样例端到端）

`python _selftest.py` → **PASS**。链路：造合成世界 → 采集两轮 → 差分 → 出报告。样例见 `sample_report.md` 与 `sample_deltas.csv`。

### 自检抓到的两个真 bug（这就是为什么要自检）

1. **同一秒内跑两次 → 快照互相覆盖**（文件名只有秒级精度，第二次静默覆盖第一次）；
2. **用 `_2` 后缀补救是错的**：Windows 路径排序**大小写不敏感**（`_`(0x5F) 排在 `z`(0x7A) **前面**），补号反而排到前面 → 差分变成负数 → 被 clamp 成 0。
   → 修法：**定宽毫秒**写进文件名 + `~` 后缀兜底（`~`(0x7E) > `z`，排在后面）；**分析器始终按 JSON 里的 `collected_at` 排序**，文件名顺序只是给人看的。

### 它看不到什么（必须先知道，否则会误读报告）

| 缺口 | 补法 |
|---|---|
| **TPS / MSPT** | 装 `spark`，或让服务器周期性把 TPS 写进日志 |
| **任务 / 时代完成时间** | 人工维护 `milestones.csv`（已给模板），或让日历脚本写一行日志 |
| **袭击事件明细** | 看 mod 日志（hordes / undeadnights 关键字）；采集器已统计关键字命中数 |
| **"放置"统计** | 原版**没有**独立放置计数，用 `used` 近似 |
| **材料分类明细** | 现在只汇总总量；加 `--detail` 输出 top-N 即可（改一行） |

### 报告挂在设计假设上（H1~H7）

H1 时代节奏 · H2 内容是否发空 · H3 分工是否形成 · H4 是否有人掉队 · H5 性能红线 · H6 袭击是否成立 · H7 材料经济。
每条都给出"**当前判定 + 还缺什么数据**"——复盘是为了改设计，不是为了看数字。

### 用法

```powershell
# 手动跑一次
& .\run_daily.ps1 -World "C:\srv\world"

# 自动化（示例命令，未在本机验证；请按你的路径改）
schtasks /Create /SC MINUTE /MO 30 /TN "MC Review Snapshot" /TR "powershell -NoProfile -ExecutionPolicy Bypass -File \"<完整路径>\run_daily.ps1\" -World \"C:\srv\world\""
```

**输出**：`data/snapshots/*.json`（原始快照）· `data/timeline.csv`（时间线）· `data/report.md`（报告）· `data/deltas.csv`（差分明细）

**注意**：控制台里中文会乱码（Windows 控制台编码），但**文件内容是正确的 UTF-8**。

---

## 十一、AI 层（史官 / 顾问 / 设定文本）

三个 v0 组件，**都不需要 API key、不需要游戏内桥**：把素材准备好，拿去 DSH 会话里生成即可。

| 组件 | 文件 | 作用 |
|---|---|---|
| **素材层** | `state_brief.py` | 从快照 + 里程碑 + 日志抽出**事实清单**，产出两份简报：`chronicler_brief_<窗口>.md`（给史官）与 `advisor_brief.md`（给顾问），各带写作 / 回答约束 |
| **设定文本** | `lore_prompts.md` | 四套提示词模板（残言 / 碑文 / 事件旁白 / 任务描述）+ 质量判据 + **15 条已生成样例残言** |
| 样例 | `sample_chronicler_brief.md` · `sample_advisor_brief.md` | 简报样例（合成数据） |

### 为什么值得做（价值位置）

- **AI 史官**解决的**不是"写得好不好"，而是"没人愿意写"** —— 设计里明确写了国史「不仪式化就一定会烂尾」；
- **AI 顾问**解决新玩家 / 回归者的迷失感；
- **设定文本**解决"几十上百条残言手写会累死"；
- **残言可以是最高效的教学**：样例第 11 条「旧世的墙比我们高，还是没守住。区别不在墙」**一句话讲完了"城墙不是万能解"**（因为 `hordeMobsCanClimbEachOther` 默认开启）。

### 目前**不做**的（连同理由）

| 不做 | 理由 |
|---|---|
| AI 驱动的敌人 | MC 敌人是 **tick 级实时决策**，LLM 是秒级——物理上不匹配；成本与性能也不可接受 |
| 实时聊天式 NPC | 秒级延迟 + 每人每句一次请求，体验与成本双输，还会和"朋友之间聊天"竞争 |
| 会说话的 NPC / 邻国外交 AI | 与「**玩家城邦**」核心冲突（国家是你们亲手建的），最多留作第二季实验 |
| AI 当"规则裁判" | 3~7 人靠聊天就能解决——按本包判据属于"想象中的问题" |
| AI 做"防刷检测" | 方向对但**不该用 AI**：统计阈值脚本更准、更便宜、更可解释 |

### 现实约束

1. **延迟决定它只能做异步**（1~10 秒）：适合"几秒后出现在成书里"，不适合即时对话；
2. **成本要分档限流**：低频异步（每天几次）几乎不花钱，高频对话会变成持续支出；
3. **必须有降级**：API 挂了要表现为"史官今天没来"，而不是报错或卡住；
4. **最大的坑是变成噱头**——所以产出必须落在玩家本来就会看的地方（成书 / 时代碑 / 任务书 / 残言）。

### v0 → v1

- **v0（现状）**：素材 + 提示词 → 在 DSH 会话里生成 → **人工挑 20%** → 手粘进游戏；
- **v1**：接 **RCON** 把产出自动写回游戏（成书 / 告示牌 / `tellraw`）。需要服务器开 `enable-rcon`（一行配置），并给桥加降级逻辑；
- **待核实**：KubeJS / 服务端能否直接发 HTTP（决定"要不要外部桥"）· RCON 写回可行性 · **AI 内容在朋友服里是被当回事还是被当笑话**（只有真人试了才知道）。

---

## 十二、自动启动与冒烟测试（`autotest.py`）

**目的**：把"每次改完 mod 都要人肉开游戏看一眼"变成**跑一条命令**。只在"要动手玩"的时候才开游戏。

### 怎么做的（完全不用 PCL 界面）

读版本 JSON → 拼 classpath → 直接调 NeoForge 的 `BootstrapLauncher`，带 `--quickPlaySingleplayer <世界名>` 自动进世界。离线鉴权用**固定 UUID + 固定用户名**——这样 `world/stats/<uuid>.json` 的统计能连续累加，而不是每次多出一个新玩家。

```powershell
cd <仓库路径>\tools
python autotest.py --seconds 45      # 默认世界「新的世界」、6G 堆
python autotest.py --dry-run         # 只打印将执行的命令，不启动
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `--seconds` | 60 | 进世界后停留秒数 |
| `--world` | 新的世界 | 存档名 |
| `--startup-timeout` | 300 | 等"进世界"的上限（秒） |
| `--xmx` | 6G | 堆上限 |
| `--version-dir` · `--java` · `--username` · `--uuid` … | 见文件头 `DEFAULTS` | 换实例时覆盖 |

**流程**：启动 → 等**本次**日志出现进世界标记 → 停 N 秒 → 优雅退出 → 清 `session.lock` → 采一份快照 → 打印摘要（ERROR/WARN 计数 + ModernFix 三行耗时）。

### 它抓到的 3 个自己的 bug（都真实发生过）

| bug | 症状 | 修法 |
|---|---|---|
| classpath 没去重 | 游戏**根本起不来**：`IllegalStateException: Duplicate key gson-2.10.1.jar`（NeoForge profile 里同一个 jar 列了两次，官方启动器用 Set 去重） | 按序去重（实测 106 条全唯一） |
| **假阳性** | 去 `latest.log` 搜"进世界"，而**上一次**的日志里就有这行 → 误报成功 | 要求日志 `mtime > 本次启动时间`；java 输出改写入 `data/autotest-launch.log`（原来丢进 DEVNULL，排错时抓瞎） |
| 控制台编码 | 打印日志片段时 `UnicodeEncodeError: 'gbk' codec` | stdout 切 utf-8 + `errors="replace"` |

> 顺带一条**日志轮转**的坑：MC 每次启动都新写一份 `latest.log`（旧的压成 `.gz`），所以**永远不要按偏移量读日志**——必须整份重读。踩过这个坑的人才会知道。

### ⚠️ 它**不能**替代什么（别误读摘要）

它只能证明"**装得上、起得来、不崩、不报新错**"，**不能**证明功能是对的：

| 摘要说 | 它真正证明的 |
|---|---|
| 无新 ERROR/WARN 类别 | 加载期没炸（**运行期**的功能错误它看不见） |
| ModernFix 的 N 秒耗时 | 加载性能（**不含 TPS**；TPS 要进世界后另测） |
| 快照采集成功 | 存档能读（≠ 玩得对） |

**功能验收仍然必须有人进去动手**——这是 v0 唯一剩下的人工步骤：

1. 按 `'` 打开 OPAC 界面，**圈一块地**；
2. 在领地内**放一台 Create 机械**，确认没被保护拦下（OPAC 明确支持 Create）；
3. 用 **Schematicannon** 放一次 `wall_ironhold`（图纸链路最后一环）。

这三条做完，"装得上"才算升级成"**能用**"，然后才谈内容。

---

## 十三、真客户端自动化（`win_ctl.ps1` + `game_agent.py`）

**目标**：让 agent 能像人一样操作**真实客户端**（带着全部 mod），并且**把结果当文本读**。

**为什么必须用真客户端**：现成的机器人库（Mineflayer 一类）说的是**原版协议**、只认**原版方块**——
进不了 NeoForge 服务端（握手过不去），也不懂 Create 的应力和 OPAC 的领地。
真客户端没有这些问题：它就是游戏本身。

### 三层管线（全部实测可用）

| 层 | 手段 | 状态 |
|---|---|---|
| **输入** | `PostMessage` 发键盘消息（WM_KEYDOWN / WM_CHAR） | ✅ 窗口**被遮挡、非前台**都能用 |
| **输出** | 读 `logs/latest.log` 的文本增量 | ✅ **完全不需要看图** |
| **画面** | `PrintWindow(PW_CLIENTONLY \| PW_RENDERFULLCONTENT)` | ✅ 被遮挡也能截到 |

**最关键的一条发现**：Minecraft 客户端把聊天栏**每一行**都写进 `logs/latest.log`：

```
[22:39:03] [Render thread/INFO] [minecraft/ChatComponent]: [System] [CHAT] Claims: 1 / 500
[22:38:59] [Server thread/INFO] [...MinecraftServer/]: [<玩家>: Successfully claimed the chunk at (-2, -2) ...]
```

所以命令输出是**纯文本**：完整、不截断、可 grep、可断言。
（之前靠截图"看"输出，被窗口 DPI、字体大小、聊天栏可视行数反复坑——读完这段就知道为什么放弃那条路。）

### 用法

```powershell
cd <仓库路径>\tools
python game_agent.py --run "/oclaims about" --launch   # 单条命令，直接看输出
python game_agent.py --script tests\opac_v0.txt        # 回归脚本
python game_agent.py --run "/spark tps" --attach       # 附着在已开着的游戏上
```

脚本格式（`tests/*.txt`）：`cmd:` 要执行的命令 · `expect:` 正则断言 · `shot:` 顺便截图。
输出 `PASS/FAIL`，**退出码 = 失败条数**，可直接接进任何 CI。

**自愈**：一条命令没有任何输出，就说明有界面挡着（暂停菜单/聊天栏）→ 按 Esc 再重试，最多 3 次。
这样不需要精确知道当前界面状态。

### 踩到的 4 个坑（都很贵，别重犯）

| 坑 | 症状 | 修法 |
|---|---|---|
| **`--demo` 试玩模式** | 版本 JSON 里 `--demo` 是 `rules.features.is_demo_user` 的条件参数，而 `rule_allows` **只看 `os`、忽略 `features`** → 无条件加上了 `--demo` → 游戏跑在**试玩模式**（禁多人 + 5 游戏日上限），**此前所有自动测试全部作废** | `features` 参与判定；并在启动前**断言命令行里没有 `--demo`**，有就直接拒绝启动 |
| **DPI 缩放** | 系统 125% 缩放 + 进程 DPI 不感知 → `GetClientRect` 报 1024×576（真实 1280×720），`PrintWindow` 只渲染左上角，**正好切掉最新那行聊天** | 进程声明 DPI 感知（`SetProcessDPIAware`） |
| **抓到的是别人的窗口** | `CopyFromScreen` 抓"屏幕那块区域"，游戏被自己的 GUI 盖住时截到的是聊天界面 | 改用 `PrintWindow`（截窗口本身），并用亮度检测判断是否黑屏再兜底 |
| **后台一失焦就暂停** | 单人模式 `pauseOnLostFocus:true` → 窗口失焦游戏立刻暂停，后台自动化必炸 | 测试期间改 `options.txt`（另含 `rawMouseInput:false`），**先备份、跑完还原** |

> 另一条通用经验：**日志每次启动会轮转**（`latest.log` → `.gz`）。
> 所以**跨会话**永远不能按偏移量读日志；**同一会话内**（只追加、不轮转）才可以。

### 边界（不要误读）

- **能**：执行任何命令、读任何文本输出、截任何画面 → **单机就能确定**的功能 / 性能 / 界面项。
- **不能**：需要**两个真实玩家**的项（跨玩家领地权限、在别人领地里操作机械）——
  单机只有一个玩家，物理上测不出来。这类归到「专用服务端 + 多客户端」那一层（见下一节）。

---

## 十四、专用服务端与 RCON（`server_ctl.py` + `side_check.py`）

**为什么必须有这一层**：v0 之前**只验过客户端**，而**服务端与客户端的 mod 集合不同**
（纯客户端 mod 必须排除、服务端专用 mod 必须加入）——第一次起服务端才是真检验。

### 两条纪律

1. **哪些 mod 装服务端，靠证据不靠记忆**：`side_check.py` 用每个 jar 的 **SHA-1** 反查 Modrinth，
   读官方标注的 `client_side` / `server_side`。实测：19 个里 **15 个装服务端、
   4 个纯客户端必须排除**（Embeddium / EntityCulling / ImmediatelyFast / MoreCulling）。
   反直觉的一条：**`servercore` 是"服务端专用、客户端不支持"**——它一直白放在客户端目录里。
2. **断言优先走 RCON**：服务端控制台**永远英文**，而客户端聊天栏**跟随客户端语言**（本地 `zh_cn`）
   → 英文断言在客户端会全灭。所以测试脚本分两种步骤：
   `cmd:`（客户端做，只有玩家能做的事）与 `rcon:`（服务端断言，稳定）。

### 用法

```powershell
python side_check.py --mods <实例mods> --write-lists lists   # 端侧分类（SHA-1 反查 Modrinth）
python server_ctl.py --setup                                 # eula + server.properties + 按清单装 mod
python server_ctl.py --start --wait 300                      # 启动并等 "Done (...)"
python server_ctl.py --script tests\server_v0.txt            # RCON 回归（纯文本断言）
python server_ctl.py --cmd "tick query"                      # 单条命令
python server_ctl.py --stop
python game_agent.py --server 127.0.0.1:25565 --script tests\client_server_v0.txt   # 联机链路
```

### 实测数据（2026-09-15）

| 项 | 数值 |
|---|---|
| 服务端首次启动 | 世界生成后 `Done (7.502s)`；ModernFix 记的加载总时长 29.0s |
| 服务端 mods | **15 个**（客户端 19 个 − 4 个纯客户端） |
| 空闲 MSPT（0 人） | avg 0.4ms；P50 0.4 / P95 0.6 / P99 0.7 ms |
| **1 人在线 MSPT** | **avg 5.0ms；P50 4.6 / P95 8.4 / P99 9.2 ms**（设计预算 ≤30ms，余量充足） |
| 联机下 TPS | 20.0（5s/10s/1m/5m/15m 全窗口） |
| 套件结果 | 服务端 **22/22（连跑两次一致）** · 联机 **9/9** |
| 领地数据持久化 | 客户端重启 + 服务端重启后仍在（`The target chunk is already claimed`）——**前提是优雅退出** |

### 踩到的坑（第 5~8 个，接着上一节编号）

| 坑 | 症状 | 修法 |
|---|---|---|
| **系统 PATH 上是 JDK 17** | `run.bat` 调裸 `java` → `InvalidModuleDescriptorException: Unsupported major.minor version 65.0`，启动即死（65.0 = Java 21 的 class 版本） | 显式用 JDK 21 + `@win_args.txt`；**不要用 run.bat** |
| **`run.bat` 末尾有 `pause`** | 失败后**永久挂住等按键**，而等待循环还在傻等超时 | 不用 run.bat；等待循环同时盯**进程死活**，进程一崩立刻返回 |
| **回归测试不可重复执行** | `setblock` 设成**完全相同的状态**时原版 `Level.setBlock` 返回 false → "Could not set the block"，第二次跑必假失败 | 断言**不写在 setblock 上**：先清成空气、再放、最后用 `execute if block` 断言 `Test passed`（已做正反对照：错方块名确实回 `Test failed`） |
| **spark 的 tps 在 RCON 下没有输出** | 连试两次皆空，一度误判服务端没起来 | 服务端侧改用原版 `/tick query`（给 P50/P95/P99）；**spark 的 tps 反过来只在客户端可用**——两者正好互补 |
| **离线模式的 OP 白给** | `op 玩家名` 时玩家不在线 → 写进一个随机 UUID；而 `online-mode=false` 下服务端**按名字算离线 UUID**，两者对不上 | 把离线 UUID 直接写进 `ops.json`，或等玩家在线时再 op |
| **RCON 首包超时太短** | 服务端忙（`save-all` 同步存档）时来不及回 → **明明成功却收到空串**，`op` 看起来像没生效 | 首包等 8 秒，拿到内容后再用短超时收分片；步与步之间留 0.25s |

---

## 十五、两个**完全离线**的校验工具（不开游戏、不起服务端）

这两项以前被归在"只能人工看"里，其实**都静态可判定**——做成工具后它们就能进日常流程：
秒级完成、零成本重跑、改动后随时复验。

### `lang_audit.py` —— 中文本地化审计（回答"组5"）

mod 的文本就在 jar 里（`assets/<命名空间>/lang/<语言>.json`，少数在 `data/` 下）。
数一下有没有 `zh_cn`、缺多少键，就精确回答了"**哪些 mod 会显示英文**"。

```powershell
python lang_audit.py --mods <实例mods> --json data\lang-audit.json --worklist lists\i18n-todo.txt
```

实测（19 个 jar）：

| 结论 | 细节 |
|---|---|
| **MC 本体界面本来就是中文** | `options.txt` 里 `lang:zh_cn` |
| **create 6 已 99% 中文** | 3619 / 3639 键 —— 文本最重的 mod 不需要处理 |
| 6 个 mod 有中文 | modernfix 86% · neruina 99% · YBD 80% · moreculling 89% · cloth-config 98% · entityculling 100% |
| 🚨 **OPAC 0%** | **613 键全英文、没有任何 `zh_cn`** ← 唯一硬缺口 |
| 10 个无文本 | 库 / 性能 mod，不面向玩家 |

**所以"组5 中文化"的真身 = 给 OPAC 补一份 zh_cn 资源包（+ 几处小缺口）**，而不是"去找汉化包"。
（本机 mods 目录只有 19 个 jar，是当前装载集；覆盖表随装载集变化，改完重跑即可。）

### `blueprint_check.py` —— 图纸批量校验（回答"图纸能不能用"）

两层，全部离线：

1. **结构**：gzip 魔数 · 根键必须是 `size`/`palette`/`blocks`/`entities` ·
   **palette 里不能有 `Properties`**（Create 6 要裸方块 ID；带属性会产出非法方块状态）·
   方块坐标必须落在 `size` 内 · 方块数 ≤ 体积
2. **注册表**：把图纸里用到的每个方块 ID拿去 jar 的 **blockstates** 反查，确认该 ID 在包里真实存在
   ——**这才是真风险**：格式对但材质名写错，游戏里会当场失败

```powershell
python blueprint_check.py --dir <图纸目录> [--json data\blueprint-check.json]
python blueprint_check.py --dir <图纸目录> --verify-registry    # 可选：改成运行时 setblock 验证（要服务端在跑）
```

实测（实例 `schematics/`）：**结构 10/10 通过 · 方块 ID 12/12 存在**
（10 张里有 2 张是 Create 自己写出的 `uploaded/<玩家>/`，所以报告一律带相对路径，免得看成"重复行"）。

> 为什么不做"每张图纸都真放一遍"：同一套生成器写出的文件**格式与材质表是共享的**，
> 单点已验证 Create 接受该格式；本工具再覆盖"全部文件结构一致 + 全部方块 ID 真实"。
> 剩下的"好不好看"属于主观项，只能人判。

### 押后清单

需要"开服务端 / 两个客户端 / 人眼看画面"的项目全部攒到一次做，见项目根的 **`DEFERRED.md`**。
日常改包只需要跑离线 + 单机套件。

---

## 十六、写这些脚本时的纪律（全都是踩出来的）

| # | 纪律 | 踩过的样子 |
|---|---|---|
| 1 | **Python 里别用双引号套双引号** | `lines.append("……只是"市场上有哪些"……")` 直接 `SyntaxError`。这个会话犯了**两次**（`lang_audit.py`、`survey_mods.py`）。中文里要引号就写「」或转义 |
| 2 | **`.ps1` 只写 ASCII** | 脚本里出现中文会破坏 PowerShell 解析器，报"未终止的字符串" |
| 3 | **不要用 `Get-Content` 数行数** | 它按 ANSI/GBK 解码 UTF-8 文件，**会把换行字节吃进双字节汉字里**：`DESIGN.md` 真实 1307 行，它报 986 行。要数行用 Python |
| 4 | **后台任务的 `workdir` 必须事先存在** | 在命令里才 `mkdir` 的目录，进程启动时不存在 → `spawn failed: ENOENT` |
| 5 | **控制台里中文乱码不代表文件错** | Windows 控制台是 GBK；文件内容是 UTF-8 且正确。要看内容就打开文件，别信控制台回显 |
| 6 | **别用记忆代替接口** | 版本、命令名、能力面一律查接口/字节码（`survey_mods.py` / `jar_probe.py`）。这个会话里"OPAC 命令名"就是靠字节码纠正的 |

---

## 十七、本机配置：`pack.local.json`（不进仓库）

### 为什么有这个东西

工具原本把机器相关的值**写死在代码里**：实例路径、Java 路径、游戏 ID、UUID，以及
`server_ctl.py` 里的 **RCON 口令**（那是能远程执行服务器命令的口子）。

公开仓库里这有三个后果：

1. **密码与个人目录结构公开**；
2. **换台机器就废**——要在 6 个文件里改路径；
3. **实例改名就再废一次**（例如以后从 `1.21.1-NeoForge_21.1.250` 换成正式服目录名）。

现在改成：**真值放 `pack.local.json`（被 `.gitignore` 排除），模板 `pack.local.example.json` 进仓库。**

### 用法

```powershell
copy pack.local.example.json pack.local.json    # 然后改里面的路径 / 用户名 / 口令
python pack_paths.py                            # 自检：打印生效配置（口令自动打码）
```

没有 `pack.local.json` 时，工具不会报一堆 traceback，而是**回退到模板值并明确提示**；
真正要跑时会给出"复制模板 → 改两行"的照抄命令（`pack_paths.require_usable()`）。

### 用到它的工具

| 工具 | 用到的配置 |
|---|---|
| `autotest.py` · `game_agent.py` | `instance_dir` · `game_root` · `java` · `username` · `uuid` |
| `server_ctl.py` | `server_dir` · `instance_dir` · `java` · `rcon_*` |
| `blueprint_check.py` · `jar_probe.py` · `survey_mods.py` | `instance_dir` · `server_dir` |

测试脚本里凡是要写玩家名的地方，写 **`{{player}}`**，`game_agent.py` 会用配置里的 ID 替换
——这样**仓库里不出现任何人的游戏 ID**。

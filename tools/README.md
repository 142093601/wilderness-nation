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

## 十五·补、选型证据来源（第四条腿：MC百科）

前三条腿（本机参照包 / 跨包共识 / CurseForge）**全是英文社区视角**。本包用户是中文玩家，
所以要有一条中文社区的腿。两个工具，一个负责**发现**，一个负责**判定**。

### `mcmod_survey.py` —— MC百科目录（发现）

```bash
python tools/mcmod_survey.py --probe                    # 探清 URL 参数语义（加载器编号/分页/排序）
python tools/mcmod_survey.py --list --query "mcver=1.21.1" --pages 334
python tools/mcmod_survey.py --cmp                      # 与 modlist.tsv 对差 → data/mcmod-gap.{md,json}
python tools/mcmod_survey.py --slots                    # 缺口按简介中文关键词分 15 个功能位桶
python tools/mcmod_survey.py --names                    # 导出已有 mod 的百科官方中文名索引
```

**站点边界（实测，不要想当然）**：

| 项 | 实测 |
|---|---|
| robots.txt | 只禁 `add/edit` 提交路径 → 检索页可读；工具**只读**检索页 |
| 加载器编号 | 1=Forge · 2=Fabric · 3=Rift · 4=LiteLoader · 5=数据包 · 6=命令方块 · 7=文件覆盖 · 8=行为包 —— **没有 NeoForge 档** |
| 排序 | 只有「默认排序」与「按收录时间」；`sort=views/hot/popular/downloads` 全被静默忽略 |
| 整合包区 | `/modpack.html` **整条路径带验证码** → 该腿不用（不绕过访问控制） |

**这里最容易犯的错**：默认排序不是时间序（首页混着老 mod 与新 mod），很像热度序 ——
但站点没写它是什么，能证伪的「指数走势」跳 `/login/`。**语义无法验证的东西不能当证据用**，
所以名次只作稳定遍历顺序，不做排序依据。

**踩到并修掉的匹配 bug**：不少条目 `ename` 为空、英文名带 `[TAG]` 前缀落在中文名字段里
（`name="[FFS] FTB Filter System"`）→ 只按 `ename` 匹配会把**已有的 mod 判成缺口**。
修法：两个字段都作候选 + 剥前缀 + 音标折叠。命中 1150→2385，缺口 8088→6853。

### `verify_candidates.py` —— 候选核验（判定）

```bash
python tools/verify_candidates.py                       # 读 tools/lists/candidates-from-mcmod.tsv
python tools/verify_candidates.py --only Millenaire      # 只核验某条
```

判定链：Modrinth `facets[versions:1.21.1, categories:neoforge]` 搜 → `/project/<slug>/version` 确认真有文件；
CF `api.curse.tools` 搜 → `/mods/<id>/files` 确认。产出 `data/candidate-verify.{md,json}`。

**两个自查（不做的话证据是假的）**：

1. 名字全等仍可能是同名不同 mod，也会被松匹配误伤：`Millénaire` 第一次匹配到
   `civilis-millenaire-compatibility`（**兼容补丁，不是本体**）→ CF 侧改成只认全等，弱匹配标 `WEAK` 交人工。
2. **不要相信筛选参数，要读载荷自身字段**：若 CF 代理忽略了 `gameVersion`/`modLoaderType`，
   `/files` 会返回全部文件，"CF-OK" 就是假的 → 逐条读每个文件载荷的 `gameVersions`，
   确认真含 `['1.21.1','NeoForge']`。

**清单链路已打通**：`apply_modlist.py` 的 CF 证据来源现在读三处 —— `data/cf-survey.json`、
`data/candidate-verify.json`，以及**进仓库的结论表** `tools/lists/cf-verified.tsv`。
前两个是 gitignore 的产物，只用它们的话**新克隆会把 CF 独占的 mod 判成 `CF_UNVERIFIED` 而装不上**，
所以结论必须留一份在仓库里。

**同一轮抓到的一个真 bug**：CF 上 FTB 三件套的真实 slug 是 `ftb-quests-forge` / `ftb-teams-forge` /
`ftb-library-forge`，而清单里写的是不带 `-forge` 的短名 —— **CF 接口按 slug 查是"查无"**，
装机时 `packwiz curseforge add` 会直接失败；而这恰好是任务书的三件核心（本包承重墙之一）。
教训：跨平台的 **slug 不是同一个**，写错不会在核验阶段报错，只会在装机那一刻炸 → 现在按 slug 逐个解析到 CF 项目 id 并记进结论表。

---

## 十九、装机流程：分批 + 落盘 + 冒烟（2026-09-19 起）

**为什么分批**：187 条 `plan` 一次性装进去，失败无法归因；而且**影响世界生成的 mod 有硬顺序**
（`terralith` 装了就不能从已有世界增删）→ 必须"不可逆的先装、且在建世界之前装完"。

**六批（`tools/lists/install-batches.tsv`，进仓库）**：

| 批 | 内容 | 为什么这个顺序 |
|---|---|---|
| 0 已装地基 | 性能组 + OPAC + Create + YBD（16 条，**早已在实例里**但不在 pack 里） | 世界是用它生成的 → **测试时必须永远在场**（R4） |
| 1 地基 | 世界生成三件 + 引擎 + 性能 + 中文化 + 运维底线 | 不可逆项必须先就位 |
| 2 世界内容 | YUNG's 全家 / WDA / D&T / 结构 / 村庄 / 中世纪 / 维度 / **据点结构** | 结构也影响世界生成 → 同样要在建世界前 |
| 3 立国与基建 | Create 生态 / 图纸系 / 建材装饰 / Macaw's / 交通 / 食物农业 | 时代 1~2 主菜 |
| 4 远征与生活 | 地图背包定位 / 战利品遗物 / QoL / 视觉音效 / 社交 | 时代 3 之前 |
| 5 御敌与文明 | 威胁 / 军事单位 / 攻城 / 大工程 | 时代 4~5 |

**流程（每批四步）**：

```bash
python tools/apply_modlist.py --verify --batch 1              # 先核验这一批
python tools/apply_modlist.py --verify --batch 1 --apply      # 写进 pack/（**只写元数据**）
python tools/materialize_pack.py                              # 落 jar 进实例（**不要加 --batch**，见下）
python tools/autotest.py --seconds 45                         # L0 装载冒烟
```

### `materialize_pack.py` —— 把元数据落成 jar（带哈希校验）

`--apply` 只写元数据（`metadata:curseforge` 或直链），**不下载 jar**。本工具用同一份元数据自己下载并**校验哈希**：

- Modrinth 条目：`[download] url` + `sha512` → 直链下载 + 校验
- CurseForge 条目：只有 `file-id`/`project-id` → 经 CF 接口换地址 + `sha1` 校验

> ⚠️ **别用 `--batch` 落盘**：packwiz 会把依赖也写进 `pack/mods/`（`architectury`、`extralib`…），
> 而 `--batch` 只落批次内的 slug → 依赖缺 jar，**游戏启动直接崩**（2026-09-19 实测踩到）。
> 正确做法：`--apply` 之后**无参数**跑一次。

> ⚠️ **CF 换下载地址的端点形状**：`/mods/{projectId}/files/{fileId}` 在公开代理上是 **404**，
> 必须用 `/mods/{projectId}/files`（列表）再按 `file-id` 筛出 `downloadUrl`。

> ⚠️ **`Path.stem` 的坑**：`Path("jei.pw.toml").stem` 是 `"jei.pw"`（只去掉最后一个后缀）
> → 剥 slug 必须手工去 `.pw.toml`，否则批次过滤永远匹配 0 条。

### 第一批的实测结果（2026-09-19）

冒烟测试一次性抓出 **4 个真问题**（2 个选型错误 + 2 个流程错误）：

| 问题 | 性质 | 处理 |
|---|---|---|
| `architectury` / `extralib` 缺 jar | 流程（`--batch` 不含自动依赖） | 改为无参数落盘 |
| `xaeroplus` 需要 `xaeroworldmap` | 跨批次依赖（地图在批 4、增强在批 1） | 把 `xaeroplus` 移到批 4 |
| `stellarcreateoptimization` 硬依赖 `sodium 0.6.9+` | **选型冲突**（本包用 `embeddium`，mod id 对不上） | 改 `skip` + 写明理由 |
| `byepregen` 与 `noisium` 显式不兼容 | **选型冲突**（mod 自己声明） | 改 `skip`，取 `noisium` |

**⚠️ 挂起问题的最终定论（已解决，2026-09-19）**：那次"quickPlay 打开世界时卡住"，
根因是**验证流程本身**，不是 mod 冲突 —— 详见下一节的「两步流程」与 `CONFLICTS.md` 的 C9/C2。
**注意区分两种"进不去世界"**：
- **静默失败**（无异常、无崩溃报告）= 客户端 mod 集 ⊉ 世界 mod 集（R1），或流程问题（C9）；
- **崩溃报告**（FML `Mod loading has failed`）= 真缺依赖/真冲突 —— 但**先查 `data/held-back/` 里有没有那个 jar**（R4）。

`Crash Assistant` 弹的"已崩溃但未生成崩溃报告"是**误报**：它的日志显示它是启动时被拉起的**独立看守进程**
（`Parent PID` + 模组列表快照），游戏被 autotest 超时回收后它才弹窗。

---

## 二十、验证协议：两步流程 + 依赖图纪律（批 2~5 撞出来的）

### 为什么不能"同进程先服务端再客户端"（C9）

**全配对模式**（一个进程里先起专用服务端、再起客户端）会让客户端在 **mod 加载阶段静默死亡**：
4/4 次复现，**没有崩溃报告、没有 hs_err、Windows 事件日志也干净**。
而同样 140 个 jar 用**两步流程**跑 → 一次通过。→ 判定为**验证流程/资源问题**，不是 mod 冲突。

**所以固定为两步**：

```bash
# ① 用目标 mod 集生成世界，并备份下来（这步跑专用服务端）
python tools/paired_check.py --group b1,b2,b3,b4,b5 --world-only --backup-world data/b5-world
# ② 复用那份世界，单独启动真实客户端进世界（这步不跑服务端）
python tools/paired_check.py --group b1,b2,b3,b4,b5 --reuse-world data/b5-world --world b5v
```

**判定标记**：客户端日志出现 `Starting integrated minecraft server` = 进世界成功。

### 三条记账纪律（每条都是真实踩出来的）

1. **分批测试必须带批 0**：`parse_groups` 无条件并入批 0。批 0 是"一直该在场的地基"，
   只用批次表表达的话，任何一次子集测试都会把它搬走 → 世界里的方块/实体没了 → 静默失败（R4）。
2. **每次往 `pack/` 加/删条目，必须重建依赖图**：`python tools/depgraph.py --build`。
   `packwiz add` **不写 `[dependencies]`**，依赖边全靠 `depgraph.py` 现查并缓存；
   缓存过期 → 闭包漏掉前置库 → 测试器把前置库搬走 → FML 报"requires X"，**看起来像真冲突**（T15）。
   测试器已加绊线：`want` 里出现图里没有的条目 → 立即停下、**返回码 6**。
3. **"缺 mod"先查 `data/held-back/{cli,srv,quarantine}`**：测试器有权搬走 jar，
   所以任何"缺 mod / 进不去世界"的第一动作是翻隔离区 —— 有 → 是测试器干的（T15/T6）；
   没有 → 才是真冲突（R4）。

### 隔离清单（`tools/lists/quarantine.tsv`）

判了 `hold` / `skip` 的 mod，如果只把它们从 `pack/` 移除元数据，测试器就**再也定位不到它们的 jar**，
于是它们会永久残留在实例里、污染此后每一轮测试（T7）。所以另存一份清单：**不管状态，一律挪出**。

### `--prune` 按**项目 id** 记账（T11）

"同一个 mod 换了版本"必须删掉旧 jar，但**不能按文件名前缀猜**：
`SuperMartijn642's Core Lib` 与 `SuperMartijn642's Config Lib` 都以 `supermartijn642` 开头 ——
第一版按"第一个数字前的前缀"匹配，把 corelib 当成 configlib 的旧版**误删**。
→ 改为按 Modrinth mod-id / CF project-id 记账（`data/materialize-manifest.json`）。

### `.gitattributes`：换行符会让 pack 失效（T16）

本机 `core.autocrlf=true`，而仓库原本没有 `.gitattributes` → 新克隆时 `pack/**` 会被转成 CRLF →
`pack/index.toml` 里的哈希**全部失配**，别人 `packwiz install` 直接失败。
→ `pack/**` 标 `-text`（字节精确）。**注意 gitattributes 后匹配的规则赢**，
`pack/** -text` 必须写在 `*.toml text eol=lf` 之后（用 `git check-attr -a <路径>` 自检）。


---

## 二十一、任务书工具链（`questbook.py` 等四个工具，2026-09-20）

**为什么要有生成器**：FTB Quests 的 SNBT 数据里，**任务/判据/奖励三级各有 id**、
坐标要浮点、判据有 15 种类型、文案还要与结构分离放 `lang/`。
手写的后果很具体：**改一处会让别处 id 漂移**，依赖写错就变成"永远点不亮"。
所以人只写**意图**（这一章有哪些任务、判据是什么、连在谁后面），其余由工具生成。

| 工具 | 干什么 |
|---|---|
| `questbook.py` | 读 `tools/questbook/*.toml` → 写 `pack/config/ftbquests/quests/**`。id = `sha256(章key + 稳定名 + 序号)` 前 16 位大写十六进制，**跑两次字节一致** |
| `questbook_check.py` | 独立**读回磁盘产物**（自带 SNBT 解析器）做 9 项校验：结构自洽 · id 可复现 · 依赖无环/无悬空 · 类型白名单 · 语言键无缺无孤儿 · `checkmark` 只在例外清单 · **判据 id 真实存在** · 坐标不重叠 · **已登记进 `pack/index.toml`** |
| `lint_questbook_toml.py` | 逐个解析 TOML，一次列全所有语法错误 |
| `build_registry.py` | 扫实例 `mods/*.jar`（lang 键 + item 模型 + blockstates + 配方引用的 id）→ `tools/registry.json` |

**语法与判据速查**：`QUESTBOOK-FORMAT.md`（格式，取证过）· `QUESTBOOK-CRITERIA.md`（判据怎么挑）。

### 三条实测出来的纪律

1. **`item` 判据检测的是「持有/消耗」，不是「放置」。**
   这是设计任务书时最容易踩的坑：把"放下营火"写成 `item: campfire`，
   玩家就"拿到营火即完成"，任务退化成物品清单。
   表达行为要用 `stat`（原版统计）或 `advancement`。
2. **判据引用的 id 必须先在注册表里核实。**
   写错 `create:train_controls`（真名是 `create:track_station`）这类 id，
   游戏**不报错**——任务只是永远不亮。所以校验器有第 ⑦ 项。
   ⚠️ 原版 assets 不在本机，`minecraft:` 的 id 只能靠"被配方引用过"这一层证明，
   因此校验器**只对"同命名空间有其它 id 被扫到"的缺失报错**，避免假阴性。
3. **改完必须跑一次服务端看加载日志**。这是唯一能证明"游戏真的读懂了"的证据：

```
[FTB Quests/]: Loading quests from ...\config\ftbquests\quests
[FTB Quests/]: Loaded 5 chapter groups, 4 chapters, 46 quests, 0 reward tables
```

### 改任务书的完整循环

```powershell
python tools\lint_questbook_toml.py          # 1) TOML 能不能解析
python tools\questbook.py                    # 2) 生成 SNBT
cd pack; & "$env:USERPROFILE\go\bin\packwiz.exe" refresh; cd ..   # 3) 登记进索引
python tools\questbook_check.py --reproducible                     # 4) 9 项校验 + 字节可复现
python tools\server_ctl.py --start --wait 300                      # 5) 真机：看加载日志
```

> **`lang/zh_cn/` 归用户**：生成器默认**不覆盖已存在的 lang 文件**，只补缺失的键。
> 用户改的是字，不是结构。要强行重写才加 `--force-lang`（会盖掉他的文案）。

> ⚠️ **`packwiz refresh` 会登记 `pack/` 下的一切**（见 `INCIDENTS.md` 2026-09-20）。
> 往里放任何东西前先问"这要分发给玩家吗"。

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
| 7 | **失败响应不要写进缓存** | 只把 HTTP 200 当缓存命中；否则一次 TLS/网络抖动会被**永久固化**（`mcmod_survey.py` 真的踩到：改好证书后仍命中了上次的 `CERTIFICATE_VERIFY_FAILED`） |
| 8 | **别信筛选参数，读载荷自身字段** | 接口的 `gameVersion`/`modLoaderType` 过滤万一被忽略，返回的"有版本"就是假的 → 逐条看返回文件里的 `gameVersions` |
| 9 | **证书不认时不要降级成不校验** | 本机 Python 的信任库不认 mcmod.cn 的中间证书（Windows 会自动补、OpenSSL 不会）→ 用 `certifi` 的 CA 包，**仍完整校验**，不要 `_create_unverified_context()` |
| 10 | **名字匹配要折叠音标、按角色收紧** | `Millénaire` 的 `é` 被归一化吃掉后误配到 `civilis-millenaire-compatibility`；CF 侧只认全等，弱匹配必须标出来给人看 |
| 11 | **无法验证语义的排序不要当证据** | mcmod 默认序很像热度序，但站点没定义、热度页要登录 → 只能当遍历顺序，不能写进结论当"热门" |
| 12 | **跨平台的 slug 不是同一个** | CF 上 FTB 三件套叫 `ftb-quests-forge` / `ftb-teams-forge` / `ftb-library-forge`，短名在 CF 接口里"查无"。写错**不会在核验阶段报错**，只在装机那一刻炸 |
| 13 | **结论要进仓库，原始产物不用** | `data/` 是 gitignore 的 → 把核验结论（`tools/lists/*.tsv`）留一份在仓库里，否则新克隆的核验链是断的 |
| 14 | **"参照包里有这个 jar"不等于"slug 是对的"** | jar 文件名会把连字符去掉（`sophisticatedbackpacks-1.21.1-…`），而项目 slug 是 `sophisticated-backpacks` → 12 条错 slug 全部"通过核验"，直到装机才炸。现在参照包实证必须先确认 slug 在 Modrinth/CF 真的存在（`SLUG_NOT_FOUND`） |
| 15 | **一个工具要落盘，就得校验哈希** | `materialize_pack.py` 下载后按 `.pw.toml` 里的 sha512/sha1 复核；不校验的话"下载成功但文件损坏"会在很久以后以诡异崩溃的形式出现 |
| 16 | **"崩了"和"卡死了"要分开查** | 没有异常、没有崩溃报告、没有系统崩溃事件 = **挂起**，不是崩溃。挂起要向进程要 `jstack` 线程转储，别去翻崩溃日志 |

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

---

## 十八、推送这个仓库时的网络坑（实测踩过）

**症状**：`git push` 报

```
fatal: unable to access 'https://github.com/.../wilderness-nation.git/':
Failed to connect to github.com port 443 via 127.0.0.1 after 2065 ms
```

**原因**：`git config --global http.proxy` 指向 `http://127.0.0.1:7897`（Clash 的端口），
**但那个端口当时没在监听**（Clash 没开）。而 GitHub **直连其实是通的**。

**两种解法**（按情况选）：

```powershell
# ① Clash 没开时：一次性绕过代理（不改任何配置，最稳）
git -c http.proxy= -c https.proxy= push origin main

# ② GitHub 直连不通时：确认 Clash 在跑，用全局代理推
git push origin main
```

**自查命令**（先分清是代理问题还是网络问题）：

```powershell
git config --global --get http.proxy          # 看代理配的哪个端口
Test-NetConnection 127.0.0.1 -Port 7897       # 那个端口有没有在监听
Invoke-WebRequest https://github.com -Method Head   # 直连通不通
```


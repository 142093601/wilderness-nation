# NEXT：交接说明（给重启后 / 新窗口的我自己）

> **为什么有这份文件**：DSH 重启或新开会话之后，**上一段对话的历史就没了** —— 新会话只看得到仓库。
> 这份文件就是"上一个我"写给"下一个我"的交接：现状、判断、坑、下一步、以及**不要做什么**。
>
> **读到这份文件时按顺序做四件事**：
> 1. `git log --oneline -3` 看最新提交（本文件写于 `3b658e4`）；
> 2. 读 §三「现在在哪 / 下一步」——那是最重要的部分；
> 3. `get_goal` 看长工程目标在不在（在且被 disarm 就 `update_goal resume`）；
> 4. 读 §六「硬规则」——**里面每一条都是踩出来的事故**，违反它们会伤到用户的实际游戏。

---

## 一、这是什么项目

- **仓库**：`D:\project\nation-pack`（`github.com/142093601/wilderness-nation`，**公开**，`main`）
- **两条腿**：
  1. **整合包**（`pack/`，232 个 mod，packwiz 分发）——设计文档 `DESIGN.md`，冲突账本 `CONFLICTS.md`
  2. **自研 mod `Statecraft`（「邦交」）**（`mods/statecraft/`：`core` 纯 Java 零 MC 依赖 + `mod` NeoForge 薄层）
     ——设计文档 `NATIONS.md`
- **玩家**：3~7 人私人服务器，中文，合作建国 + 冒险开拓，全程约 300 小时
- **文档地图**：`DESIGN.md`（包侧总设计）· `NATIONS.md`（mod 侧）· `PLAN-*.md`（各阶段计划）
  · `DEFERRED.md`（押后验收清单 + 真机结论）· `INCIDENTS.md`（**我的事故档案**）
  · `NEEDS-YOU.md`（**只有用户能做的事**）· `OPEN-DESIGN.md`（待用户拍板的 5 条设计）
  · `TASKBOOK-300H.md`（旧任务书草稿）· `PLAN-questbook.md`（**新任务书设计，已定稿**）

---

## 二、当前状态（每条都有证据，不是印象）

| 层 | 状态 |
|---|---|
| Statecraft 计划 1/2/3（生成、存档+迁移+mod 薄层、结算+外交+事务日志） | ✅ 完成 |
| **Statecraft 计划 4 的 core 侧** | ✅ **完成**。2026-09-20 强制重跑取证：`gradle :core:test --rerun-tasks --no-build-cache` → `BUILD SUCCESSFUL`；解析 JUnit XML：**28 个类 / 380 用例 / 失败 0 / 错误 0 / 跳过 0** |
| Statecraft 阶段 3（mod 薄层 + 真机） | 🟡 主体已通：环境读数 · 建筑服务（§10.2 三条 + 重复自愈）· 结算调度（§七）· 玩家侧账本 + §10.1 门禁 · **十一组命令**真机跑过 |
| 真机验收脚本（`tools/tests/`） | `sc_scheduler` `sc_contact_letters` `sc_player_actions` `sc_player_side` `sc_blueprint_gate` `sc_duplicates` `sc_station_and_log` `sc_upgrade` —— 全绿 |
| M0（HYW 会不会主动打玩家） | ⬜ **未跑**。方案+脚本就绪（`PLAN-m0-hyw.md` / `tools/tests/m0_hyw.txt`）——它是**夺城（计划 5）的唯一前置**，需要真玩家当靶子 |
| 包侧中文补全 | ✅ 完成。自产资源包 116 个命名空间 / 5288 条界面文本；`tools/i18n_cover.py` 实测覆盖率 **79%→89%**、未覆盖 **11150→5862**、100% 覆盖 mod **52→127**。剩余 5862 条全是方块/物品名（有意不做） |
| **任务书** | 🟡 **设计已定稿**（`PLAN-questbook.md`，七段逐段与用户确认）→ **数据一个都还没写**（`config/ftbquests/quests` 在仓库和实例里都不存在） |
| 世界自清空平衡问题 | ✅ 实测过并修好：原参数末代只剩 1.0 国；调 `warScoreStep` 12→4、`absorptionWarScore` 50→120 后末代平均 6.2 国（`NATIONS.md` §八点五） |
| `treaties.json` 数据化 · 玩家侧落盘 | 有意没做，触发条件写在 `DEFERRED.md` §I |

---

## 三、现在在哪 / 下一步（**最重要**）

### 3.1 用户刚刚拍板的事

用户否掉了我"批量生成 199 条任务"的做法，原话：

> "任务我希望我们能够详细的设定一下，不要你一个人说 199 个就 199 个，我希望任务还是能起到很好的引导作用"
> "一定不能让任务悬于游戏之上，任务是游戏的导向这个根本目标不能变"

于是走了一轮完整的**设计对话**（职责 → 主线份量 → 架构 → 覆盖深度 → 引导载体 → 章数 → 七段设计），
结论全部写进 **`PLAN-questbook.md`**。**下一步就是按它实施**。

### 3.2 已定的关键决策（摘要，细节看文档）

- **任务总数不设配额**（"199 条"是"1 任务 ≈ 1.5 小时"公式的产物，**已废**）
- **22 章**：编年 8（序 · 落地 · 立国 · 基建 · 出关 · 御敌 · 文明 · 无尽纪元）· 邦交 1 · 技艺 5 · 类目 8
- **主线有牙齿**：完成本时代国策主线 → 拿到"时代钥匙" → 解锁"提前推进时代"；**不做也不会被卡**（时间到照样推进）
- **引导载体**：主线写中文说明（我写白话版，用户改碑文腔）；mod 章靠图标 + 依赖箭头；**不重复 Ponder/JEI 的教学**
- **判据全部落在游戏事实**上；`checkmark` 只留 4 条例外（逐条写明理由）
- **跨章只用 `quest_links` 软链接，不做硬前置**
- **奖励克制**：大多数任务不给；只给主线节点 / 里程碑 / 终局；不发稀有物、不破坏经济
- 已核实我们自己的 FTB Quests **2101.1.36** 可用判据 15 种、奖励 13 种（含 `command`，是钥匙的技术前提）

### 3.3 紧接着要做的（按顺序）

1. **进 `writing-plans`**：把 `PLAN-questbook.md` 变成实现计划（生成器 + 校验器 + 五章样板）
2. **写 `tools/questbook.py`**：读 `tools/questbook/*.toml`（人写的任务定义）→ 产出
   `pack/config/ftbquests/quests/chapters/*.snbt` + `lang/zh_cn/` 骨架；id 由"稳定名字+序号"哈希成 16 位十六进制
3. **写 `tools/questbook_check.py`**：离线校验 ① 服务端加载零错误（起一次 dev server 看日志）
   ② id 唯一且可复现 ③ 依赖无环、无悬空、link 双向可解析 ④ 判据类型在白名单内
   ⑤ 每个任务都有 lang 键、无孤儿键 ⑥ `checkmark` 只出现在例外清单
4. **先出五章样板**：**序 · 落地（E0）· 邦交 · 机械动力 · 防御与尸潮**
5. **请用户开游戏看一眼**这五章的手感与腔调（这一步用户只需要"看"，不需要操作）→ 定模板
6. 按模板铺满其余 17 章

### 3.4 另外一小块 mod 侧工作（单列，不混进任务书数据）

**"可被命令触发的提前推进时代"入口**（现状：`SettlementClock` 只按累计在线小时推进；
`/statecraft advance` 是开发用小结算，不是时代推进）。默认命令名 `/statecraft accelerate`、**不取代价**。
约束：走 core 纯逻辑 + JUnit + 真机脚本；**必须保留**"时间到自动推进"的老路径。

---

## 四、环境与本机事实（不知道这些会白折腾）

| 事实 | 数值/位置 |
|---|---|
| 客户端实例 | `D:\game\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250`（232 jar） |
| 游戏版本 | MC **1.21.1** + **NeoForge 21.1.250** + **JDK 21**（`C:\Program Files\Java\jdk-21.0.10`） |
| 专用服务端 | `D:\project\nation-pack\server`（gitignore，`user_jvm_args.txt` = `-Xmx5G`） |
| Gradle | 本机**没装**，用缓存分发：`%USERPROFILE%\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phgltp5f9b9e\gradle-8.11.1\bin\gradle.bat` |
| 内存 | 总 **15.8G**；客户端要 5~8G，服务端 3.4~5G，DSH Web（node）0.7~3G → **同时开服务端+客户端会挤爆** |
| 客户端进世界耗时 | **约 18 分钟**（232 mod，Exposure 等还会拉远程名单超时）；`game_agent.py` 已把等待改成 1800s/600s |
| 单机验收前提 | 服务端世界拷成单机存档后 `allowCommands=0` → 必须先跑 `tools/enable_cheats.py` |
| 长跑测试加速 | `/statecraft pending <hours>` 往待结算在线小时注水（不用真等 2 小时） |
| 现状残留 | 2 个 java 进程 = **我的 Gradle 守护进程**（jdk-17 daemon + jdk-21 worker），可用 `gradle --stop` 正规停掉 |
| 汉化资源包 | 已登记进 `pack/index.toml`，但**还没进实例**（packwiz 没跑过）——本地想测就手动把 `pack/resourcepacks/荒野建国-中文补全.zip` 拷到实例 `resourcepacks/` |

---

## 五、工具清单（怎么跑、验证什么）

| 工具 | 干什么 |
|---|---|
| `tools/server_ctl.py --setup --start` / `--stop` / `--script <文件>` | 起停开发服务端、跑 RCON 脚本。**两步以上验证一律用 `--script`**（见 §六.5） |
| `tools/game_agent.py` | 起客户端进世界跑脚本（`--script` 需配 `--launch`；`--run` 不会自动启动游戏） |
| `tools/autotest.py` | 单机冒烟/回归（`--seconds 45` 等） |
| `tools/mca_probe.py` | 直接读 region / `world/entities/*.mca` 的区块 NBT —— "东西到底在不在盘上"用它 |
| `tools/enable_cheats.py` | 翻转单机存档 `level.dat` 的 `allowCommands` |
| `tools/pid_guard.py` + `tools/pid_guard_selftest.py` | **自有 JVM 的登记/认领**（只杀自己启动的进程）+ 回归测试 |
| `tools/i18n_cover.py` | 汉化**真实缺口**（社区包 + 我们的包一起算）—— `lang_audit.py` 看不见资源包，别再用它下结论 |
| `tools/i18n_verify.py` | 汉化产出独立验收（键集合/占位符/空值/疑似英文），不采信子代理自检 |
| `tools/i18n_pack.py` | 合成汉化资源包（可评审源文件 + zip + 登记 packwiz 条目，`--index`） |
| `tools/lang_audit.py` | 只看 jar 内部有没有 zh_cn（历史工具，**不足以判断覆盖**） |
| `tools/wcu_probe.py` · `tools/COMPUTER-USE.md` | 电脑控制插件自检与用法（重启 DSH 后才可用） |
| `tools/tests/sc_*.txt`、`statecraft_build.txt`、`m0_hyw.txt` | 真机验收脚本 |
| 待写 | `tools/questbook.py`（生成器）· `tools/questbook_check.py`（校验器） |

**跑测试**：

```powershell
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phgltp5f9b9e\gradle-8.11.1\bin\gradle.bat" `
  -p D:\project\nation-pack\mods\statecraft :core:test :mod:build --console=plain
# 想拿"真跑"证据（防 Gradle 用缓存糊弄）：:core:test --rerun-tasks --no-build-cache
python tools\pid_guard_selftest.py --with-server     # 改过进程相关代码就重跑
```

**提交循环**：改代码 → 测试全绿 → 同步文档（`NATIONS.md`/`README`/计划文档）→ `git add -A`
→ 用 write 工具写提交信息文件 → `git commit -F <文件>` → `git -c http.proxy= -c https.proxy= push origin main`

---

## 六、硬规则（每条都是踩出来的，违反会伤到用户的机器或游戏）

1. **永远不按镜像名/特征杀进程**（`taskkill /IM`、`Get-Process java | Stop-Process` 都不许）。
   只按 `tools/pid_guard.py` 登记过的 pid 杀，杀前验"命令行含 java + 进程创建时刻一致"。
   *事故*：`paired_check.py` 原来用 `taskkill /F /IM javaw.exe` 清场 —— 会把**用户正在玩的游戏一起杀掉**，
   而被强杀的 JVM 不写崩溃报告，双方都被引去怀疑某个 mod（`INCIDENTS.md` 第一条）。
2. **不许从 PowerShell 控制台读中文**。本机 `Select-String`/`Get-Content` 按 GBK 解码 UTF-8 → 乱码。
   要读中文：用 `read` 工具，或让 python 写文件后再读，或打印 `ascii()` 转义。
   *事故*：我从乱码里"猜"出术语「圈占」，写进分发任务书，导致 108 处返工。
3. **写文件必须显式 `encoding="utf-8"` + `newline="\n"`，且只改自己那几行**。
   *事故*：用 `Path.write_text` 往 `pack/index.toml` 追一行，Windows 文本模式把全文 LF 转 CRLF、
   还吃掉空行 → 323 行文件变成 **2326 行 diff**。判据：`git diff --numstat` 行数远超实际改动就回滚重做。
4. **一律绝对路径**。（本会话真出现过 `cd` 后相对路径找不到文件。）
5. **两步以上的 RCON 验证必须用 `--script`**，不要用 `--cmd` 串起来 —— `--cmd` 每次重连，
   忙时会把上一条的回显晚一拍发出，**命令与输出错位**（`DEFERRED.md` §K）。
6. **不带 BOM 写字、不把 `.py` 写成内联 PowerShell/Python**（含中文与引号的东西写成文件再跑）。
7. **结论必须有原始证据**：不许"我记得"、不许从乱码里读、不许用不可靠的工具口径下结论
   （例：判断汉化覆盖率要用 `i18n_cover.py`，`lang_audit.py` 看不见资源包）。
8. **不许留未验证的代码**：动 Java 就 `:core:test`；动工具就跑它的自检；动任务书就起 dev server 看加载日志。

---

## 七、用户偏好（原话级，别忘）

- **反过度设计**：宁可少做，不要造没人读的抽象层。
- **尽量不让用户手工测试**（`偏好阁#4`）：能自动化的验收自己做完，只把"物理上必须人做"的留给他。
- **结论要有可核查的原始证据**（`偏好阁#6`）。
- **记下自己的错**（所以有 `INCIDENTS.md`）。
- **不要语音类 mod**（`偏好阁#5`）。
- **长任务自行持续推进，不要中途停下来问**（`偏好阁#7`）—— 但**设计类决策必须先问**（他刚亲自示范了一次）。
- **速度稍微提一提**：少寒暄，多做实事。

---

## 八、只有用户能做的事（`NEEDS-YOU.md` 摘要）

1. **启用汉化资源包**（一次性，10 秒）：进游戏 → 选项 → 资源包 → 启用「荒野建国-中文补全」并置顶。
2. **重启一次 DSH**：让 `dsh-computer-use-win` 上线（22 个 `mcp__wincu__*` 工具）。
   有它之后"看画面"的验收（村民外观、任务书界面、M0、§J）我能**自己截图判定**。
3. **跑客户端批次时腾内存**（关 League / Oopz，或允许我降客户端堆到 5G）。
4. `OPEN-DESIGN.md` 的 5 条设计（一国一城 vs 多城 · 国力合成 · 资源分类与上限 · 边镇实体 · 终局）—— 不阻塞当前工作。
5. 任务书正文的**碑文/方志腔**（我写白话版，他改成自己的字）。

---

## 九、我能自己做、但还没做的（离线待办）

| 优先级 | 事项 | 备注 |
|---|---|---|
| **高** | 任务书实施（§三.3） | 用户刚拍板，是当前主线 |
| 高 | Statecraft"提前推进时代"入口（§三.4） | 任务书钥匙的依赖 |
| 中 | `DEFERRED.md` E1：**纯原版性能基线**（组 0） | 只起服务端，我能跑；没有它无法回答"性能组省了多少" |
| 中 | 方块/物品名 5862 条的规则化补全 | 已有资源包流水线；触发条件写在 `DEFERRED.md` C 组 |
| 低 | 300h 内容量复核（`DEFERRED.md` §H2/H3） | 探索 ~50h 够不够、前两时代 64h 密度 |
| — | "免手工启用资源包"的小 mod | **必须开客户端才能验**，所以排队到客户端批次 |

## 十、需要客户端 / 多人的（一次性批次，别零散做）

- **§J 实体存了没装回世界**：盘上有（`mca_probe.py` 证实）、`execute if entity @e` 是 0 → 最可能是"没人在线时实体不装"的原版行为，要带一个客户端重跑（判据 `buildings` 的 `healthy=true` 且房里只有一个村民）
- **§J4 有玩家在线时结算真的推进**：`/statecraft pending 1.99` + 在线 → 日志出现 `STATECRAFT_SETTLE kind=PERIODIC`
- **M0**：HYW 单位会不会主动打玩家（**挡夺城/计划 5**）
- **A 组联机**（含 **A4 图纸 × 领地 = 项目关键路径**）· **B 组人眼** · **F 组军事 8 条**
- **已知阻碍**：客户端**按键送不进去**（`tap`/`chars`/`paste` 都失败、`[CHAT]` 与 `sc-probe` 都是 0，hwnd 与 `FOCUSED=1` 都对）→ 所以"看"类验收要等 §八.2 的 DSH 重启

## 十一、等用户拍板的

`OPEN-DESIGN.md` 5 条（见 §八.4）；`PLAN-questbook.md` §十 的 5 条已给**默认值**
（钥匙命令 `/statecraft accelerate` 不取代价 · 不用 `stage` 藏任务 · 全局 `progression_mode=flexible`
· 邦交章不拆 · 类目章具体归属期 2 再定），不同意他会说。

## 十二、明确不做 / 已废的思路

- ❌ **任务总数配额**（199 条）—— 已废，改为"由引导需要决定"
- ❌ **左侧 40~60 章** —— 否决（"一堵墙"），定 22 章
- ❌ **抄 ATM-10 的任务内容** —— 其 quests 目录是 **All Rights Reserved**，我们仓库公开，只能作结构参照
- ❌ **重复 mod 自带教学**（Create Ponder、JEI/EMI）
- ❌ **任务专属内容**、**硬门闸**（除时代钥匙）
- ❌ 靠"加更多结构 mod"补内容（`DESIGN.md` §十五 明说）

---

## 十三、交接时的工作区状态

- **HEAD = `origin/main` = `3b658e4`**（任务书设计文档），工作区干净
- **无后台任务在跑**（本会话的汉化子代理已全部收工）
- **进程**：2 个 Gradle 守护进程（我的，可 `gradle --stop`）；**没有**游戏/服务端在跑；空闲内存 7.3G
- **实例侧**：`mods/statecraft-0.1.0.jar` 在；`resourcepacks/` 里**只有社区汉化包**（我们的补全包还没装，见 §四）
- **实例里的测试残留**：`saves/scdev`（我拷的单机测试档，11MB，含 `level.dat.bak`）；`options.txt` 已还原成用户的
- **临时文件**：已清理（本会话的 `_atm10`、`_i18n_*.py`、`_msg_*.txt` 都删了）

---

## 十四、重启后怎么跟用户继续

直接说"我从 `NEXT.md` 第三节接着干"，然后开工 —— **不要重新问一遍需求**：
要做什么（`PLAN-questbook.md`）、为什么（用户原话在两份文档里）、不做什么（§十二）都已经写下来了。
如果长工程目标被 disarm，先 `get_goal` → `update_goal resume`。

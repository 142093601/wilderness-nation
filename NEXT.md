# NEXT：交接说明（给重启后 / 新窗口的我自己）

> **为什么有这份文件**：DSH 重启或新开会话之后，**上一段对话的历史就没了** —— 新会话只看得到仓库。
> 这份文件就是"上一个我"写给"下一个我"的交接：现状、判断、坑、下一步、以及**不要做什么**。
>
> **读到这份文件时按顺序做四件事**：
> 1. `git log --oneline -3` 看最新提交；
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
  · `QUESTBOOK-FORMAT.md`（**任务书数据格式规范，取证过**）
  · `QUESTBOOK-CRITERIA.md`（**判据速查表，写任务时查它**）
  · `DEFERRED.md`（押后验收清单 + 真机结论）· `INCIDENTS.md`（**我的事故档案**）
  · `NEEDS-YOU.md`（**只有用户能做的事**）· `OPEN-DESIGN.md`（待用户拍板的 5 条设计）
  · `TASKBOOK-300H.md`（旧任务书草稿，**配额已废，只作参考**）· `PLAN-questbook.md`（任务书设计，已定稿）

---

## 二、当前状态（每条都有证据，不是印象）

| 层 | 状态 |
|---|---|
| Statecraft 计划 1/2/3（生成、存档+迁移+mod 薄层、结算+外交+事务日志） | ✅ 完成 |
| **Statecraft 计划 4 的 core 侧** | ✅ **完成**。2026-09-20 强制重跑取证：`BUILD SUCCESSFUL`；JUnit **28 类 / 380 用例 / 失败 0 / 错误 0 / 跳过 0** |
| Statecraft 阶段 3（mod 薄层 + 真机） | 🟡 主体已通；**十一组命令**真机跑过（`NATIONS.md` §11） |
| M0（HYW 会不会主动打玩家） | ⬜ **未跑**（夺城/计划 5 的唯一前置，需要真玩家当靶子） |
| 包侧中文补全 | ✅ 完成。116 命名空间 / 5288 条界面文本；覆盖率 79%→89% |
| **任务书工具链** | ✅ **完成**（2026-09-20）：`tools/questbook.py`（生成器）+ `tools/questbook_check.py`（9 项校验）+ `tools/lint_questbook_toml.py` + `tools/build_registry.py` |
| **任务书数据（五章样板）** | ✅ **已生成并真机验证**：序 · 落地 · 邦交 · 机械动力 · 防御与尸潮 = **5 章 / 59 任务 / 136 个 id**；服务端日志 `Loaded 5 chapter groups, 5 chapters, 59 quests, 0 reward tables`，**FTB Quests 零 ERROR/零 WARN**；**已装进实例**（`install_questbook.py`） |
| **时代钥匙入口（mod 侧）** | ✅ **完成**（2026-09-20）：`/statecraft accelerate`。核过 JUnit **28 类 / 385 用例 / 0 失败**（`SettlementClockTest` 15 个）。它**只把累计小时推到时代边界**，推进仍走原有的 `ERA_ADVANCE` 路径——老路径一个字没改 |
| **C18：`quest_enhance` 硬冲突** | ✅ **已处置（移除）**。它让"按 N 打开任务书"必崩（mixin 签名不匹配）。升级/降级**都实测救不了**，详见 `CONFLICTS.md` C18 |
| **任务书「邦交」章** | ⛔ **有意未写**——理由见 §三.4（判据没有信号源） |
| 世界自清空平衡问题 | ✅ 实测过并修好（`NATIONS.md` §八点五） |
| `treaties.json` 数据化 · 玩家侧落盘 | 有意没做，触发条件写在 `DEFERRED.md` §I |

---

## 三、现在在哪 / 下一步（**最重要**）

### 3.1 这一轮做完的事（2026-09-20）

用户否掉了"批量生成 199 条任务"，走了七段设计对话 → 结论落在 `PLAN-questbook.md`。
本轮把那份**设计**变成了**能跑的数据**：

1. **取证**（不凭记忆）：从游戏自己写出的文件、三个真实整合包、jar 类表与常量池，
   把 SNBT 格式、字段名、**type 字符串**、`data.snbt` 版本号全部钉死 → `QUESTBOOK-FORMAT.md`
2. **生成器** `tools/questbook.py`：读 `tools/questbook/*.toml` → 产出 `pack/config/ftbquests/quests/**`
3. **校验器** `tools/questbook_check.py`：9 项（6 项来自 PLAN + 3 项追加：判据 id 真实性 / 坐标重叠 / packwiz 登记）
4. **五章样板**（序·落地·邦交·机械动力·防御与尸潮）+ 真机验证通过

### 3.2 一条关键的**设计发现**（比代码重要，别丢）

> **`item` 判据检测的是「持有/消耗」，不是「放置」。**

所以"放下营火""围起栅栏""圈下领地"这类**行为**，用 `item` 判据表达不了；
硬套就会让任务退化成"物品清单"——正是用户否掉的那种任务。
能表达行为的是：

| 想要的效果 | 该用什么 |
|---|---|
| 玩家做过某行为 | `stat`（原版统计，**84 条，javap 实测**） |
| 玩家达成某里程碑 | `advancement`（原版 1399 条 + Create 1150 条） |
| 游戏里真的没有信号 | `checkmark`（且必须写进例外清单并给理由） |

**明细与完整 id 表在 `QUESTBOOK-CRITERIA.md`——写任务前先读它。**

### 3.3 紧接着要做的（按顺序）

1. **请用户开游戏看一眼这五章的手感与腔调**（这一步用户只需要"看"，不需要操作）→ 定模板
   - 落地在 `pack/config/ftbquests/quests/`；**要进实例才能看到**：
     `python tools/materialize_pack.py` 之后 `python tools/autotest.py --seconds 45`
   - 具体看：左侧栏是否"一堵墙" · 依赖箭头读不读得懂 · 图标能否一眼认出 · 正文长度是否啰嗦
2. 按模板铺其余 17 章（22 章 − 已出 5 章）
3. 用户改写碑文/方志腔：**只动 `pack/config/ftbquests/quests/lang/zh_cn/`**，结构不动
4. 期 3：补 ATM 那套图片引导（要客户端截图，用 `dsh-computer-use-win` 可以自己截）

### 3.4 「邦交」章的处理（**已写，但有一条判据限制必须知道**）

`PLAN-questbook.md` 要求邦交章写"接触 → 情报 → 外交 → 图纸 → 夺城"。实测的限制：

- **Statecraft 不提供任何 advancement，也没有自定义 statistic**（jar 实测 `advancements=0`）。
- `NATIONS.md` §11.1 写的"**情报册（物品）**"在 v1 实现里其实是 **core 里的判定类 `IntelBook`**，
  玩家入口是命令/界面——**不是一个能当判据的物品**。

于是我把 `PLAN` 的两条根本约束放在一起读：**约束 1（判据必须落在游戏事实上）**+
**约束 3（任务不当门闸，除时代钥匙）**。结论是：
**这一章不该为了凑数塞一批判不了的任务**——那既违反约束 1（假判据），也违反约束 3。

**实际写法**：只放**有真实判据**的条目（9 个任务 / 3 条 checkmark），流程靠**任务正文**引导：

| 用到的真判据 | 证据来源 |
|---|---|
| `walk_one_cm`（接触邻邦的距离） | 原版统计 |
| **`minecraft:lectern` 持有量**（情报站锚点） | `buildings.json` 实测：`anchorBlock = minecraft:lectern` |
| 绿宝石/书架/绿宝石块（图纸费 + 建材） | `buildings.json`：`blueprintPrice = 64` |
| `adventure/trade`（通商） | 原版进度 |
| `raid_trigger` + `kill`（交战的资格） | 原版统计 |

**判不了的 3 条**（已写进 checkmark 例外清单并给理由）：翻开情报册 · 读详报 · **夺城成功**。

> ⚠️ **若要让这几条自动判定，需要在 mod 侧补信号**：`/statecraft` 的
> `contact` / `diplomacy` / 夺城成功三个点，各发一个 advancement 或自定义 statistic 即可。
> 那是 mod 侧的事（core 已有这些事件，接线成本不高）。

**同一类限制还有一处**：**The Hordes / Undead Nights 也不提供进度或统计**
（jar 实测 `advancements=0`），所以「防御与尸潮」里的
**「守住一次真正的大规模袭击」也是 `checkmark`**。
要让那一步自动判定，需要在 mod 侧暴露"波次开始/结束"事件。

### 3.5 mod 侧的另一小块（**已完成**）

**"可被命令触发的提前推进时代"入口**：`/statecraft accelerate`（2026-09-20 完成）。
设计要点（别再重新发明）：

- **不另造推进路径**：core 只加了一个纯算术函数
  `SettlementClock.hoursToBoundary(elapsed, boundary)` → 到边界还差多少小时；
  加速把它注进 pending，**推进本身仍由原有的 `SettlementClock.plan` + `SettlementEngine` 的
  `ERA_ADVANCE` 分支完成**。于是时代级结算（事件 / 国书 / 天下大势）一件都不会少，
  而且"时间到自动推进"那条老路径一个字没改。
- **`hoursToBoundary` 返回 `OptionalDouble`**：空 = 已经是最后一个时代 → **拒绝加速**
  （不是静默什么都不做）。
- **一处语义决定**：`SettlementScheduler` 新增 `externallyInjected` 标记。
  §七 的"不在线不推进"**只管自然累积**（服务器空转不该烧全服预算）；
  而命令塞进来的小时数是**刻意操作**——时代钥匙是玩家做完主线换来的，
  再拿"你不在线"拒绝它等于钥匙失效。所以这条路径不看在线数，
  这也是它能在 0 人在线的专用服务端上验收的原因。
- 真机验收脚本：`tools/tests/sc_accelerate.txt`（用 `wait:` 等调度器那一拍）。

---

## 四、环境与本机事实（不知道这些会白折腾）

| 事实 | 数值/位置 |
|---|---|
| 客户端实例 | `D:\game\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250`（232 jar） |
| 游戏版本 | MC **1.21.1** + **NeoForge 21.1.250** + **JDK 21**（`C:\Program Files\Java\jdk-21.0.10`） |
| 专用服务端 | `D:\project\nation-pack\server`（gitignore，`user_jvm_args.txt` = `-Xmx5G`） |
| Gradle | 本机**没装**，用缓存分发：`%USERPROFILE%\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phgltp5f9b9e\gradle-8.11.1\bin\gradle.bat` |
| packwiz | `%USERPROFILE%\go\bin\packwiz.exe`（**要 refresh 必须在 `pack/` 目录里跑**） |
| javap（反编译取证用） | `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\bin\javap.exe` |
| 内存 | 总 **15.8G**；客户端要 5~8G，服务端 3.4~5G，DSH Web（node）0.7~3G → **同时开服务端+客户端会挤爆** |
| 客户端进世界耗时 | **约 18 分钟**（232 mod，Exposure 等还会拉远程名单超时）；`game_agent.py` 已改成等 1800s/600s |
| 单机验收前提 | 服务端世界拷成单机存档后 `allowCommands=0` → 必须先跑 `tools/enable_cheats.py` |
| 长跑测试加速 | `/statecraft pending <hours>` 往待结算在线小时注水（不用真等 2 小时） |
| 服务端启动 | `python tools/server_ctl.py --start --wait 300`（**不是常驻**：命令退出后进程会被回收；要一边跑一边操作就用后台任务） |
| 任务书数据的**服务端**落点 | `server/config/ftbquests/quests/`（本轮已拷进去做验证；`server/` 是 gitignore 的测试环境） |
| 汉化资源包 | 已登记进 `pack/index.toml`，**还没进实例**（packwiz 没跑过）——本地想测就手动拷 zip |

---

## 五、工具清单（怎么跑、验证什么）

| 工具 | 干什么 |
|---|---|
| **`tools/questbook.py`** | **任务书生成器**：`tools/questbook/*.toml` → `pack/config/ftbquests/quests/**`。`--check-only` 只看不写；`--force-lang` 会盖掉用户改的文案（**别乱用**） |
| **`tools/questbook_check.py`** | **任务书校验器**（9 项）。`--reproducible` 会重跑生成器比对字节。**改完任务书必跑** |
| **`tools/lint_questbook_toml.py`** | 逐个解析 TOML；专抓"字符串里嵌套双引号"这个反复踩的坑（`?` 个文件一次列全） |
| **`tools/build_registry.py`** | 建 `tools/registry.json`（物品/方块 id，判据真实性校验的依据）。**换机器要重跑**：`--mods "<实例>/mods"` |
| `tools/server_ctl.py --setup --start --wait 300` / `--stop` / `--script <文件>` | 起停开发服务端、跑 RCON 脚本。**两步以上验证一律用 `--script`**（见 §六.5） |
| `tools/game_agent.py` | 起客户端进世界跑脚本（`--script` 需配 `--launch`；`--run` 不会自动启动游戏） |
| `tools/autotest.py` | 单机冒烟/回归（`--seconds 45` 等） |
| `tools/materialize_pack.py` | 把 pack 元数据落成实例里的 jar / 文件（**不要加 `--batch`**）。任务书要进实例靠它 |
| `tools/mca_probe.py` | 直接读 region / `world/entities/*.mca` 的区块 NBT —— "东西到底在不在盘上"用它 |
| `tools/enable_cheats.py` | 翻转单机存档 `level.dat` 的 `allowCommands` |
| `tools/pid_guard.py` + `pid_guard_selftest.py` | **自有 JVM 的登记/认领**（只杀自己启动的进程）+ 回归测试 |
| `tools/i18n_pack.py` | 合成汉化资源包。**源文件在仓库根 `i18n/zh_cn/`**（2026-09-20 移出 `pack/`，原因见 `INCIDENTS.md`） |
| **`tools/install_questbook.py`** | **把任务书装进客户端实例**（只碰 `config/ftbquests/`，绝不碰 saves/options；装完逐个比对字节）。`materialize_pack.py` 只管 jar，config 类数据用它 |
| `tools/i18n_cover.py` / `i18n_verify.py` | 汉化真实缺口 / 产出独立验收 |
| `tools/tests/sc_*.txt`、`statecraft_build.txt`、`m0_hyw.txt` | 真机验收脚本 |
| `tools/COMPUTER-USE.md` | 电脑控制插件用法（**已上线**，22 个 `mcp__wincu__*` 工具） |

**跑测试**：

```powershell
# 任务书（离线，秒级）
python tools\lint_questbook_toml.py
python tools\questbook.py
cd pack; & "$env:USERPROFILE\go\bin\packwiz.exe" refresh; cd ..
python tools\questbook_check.py --reproducible

# Statecraft（Java）
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phgltp5f9b9e\gradle-8.11.1\bin\gradle.bat" `
  -p D:\project\nation-pack\mods\statecraft :core:test :mod:build --console=plain
python tools\pid_guard_selftest.py --with-server     # 改过进程相关代码就重跑
```

**提交循环**：改代码 → 测试全绿 → 同步文档（`NATIONS.md`/`README`/计划文档/`NEXT.md`）→ `git add -A`
→ 用 write 工具写提交信息文件 → `git commit -F <文件>` → `git -c http.proxy= -c https.proxy= push origin main`

---

## 六、硬规则（每条都是踩出来的，违反会伤到用户的机器或游戏）

1. **永远不按镜像名/特征杀进程**（`taskkill /IM`、`Get-Process java | Stop-Process` 都不许）。
   只按 `tools/pid_guard.py` 登记过的 pid 杀，杀前验"命令行含 java + 进程创建时刻一致"。
   *事故*：`paired_check.py` 原来用 `taskkill /F /IM javaw.exe` 清场 —— 会把**用户正在玩的游戏一起杀掉**，
   而被强杀的 JVM 不写崩溃报告（`INCIDENTS.md` 第一条）。
2. **不许从 PowerShell 控制台读中文**。本机 `Select-String`/`Get-Content` 按 GBK 解码 UTF-8 → 乱码。
   要读中文：用 `read` 工具，或让 python 写文件后再读，或打印 `ascii()` 转义。
   **更不许从乱码里"猜"字**（*事故*：猜出「圈占」，108 处返工）。
3. **写文件必须显式 `encoding="utf-8"` + `newline="\n"`，且只改自己那几行**。
   *事故*：`Path.write_text` 往 `pack/index.toml` 追一行 → 323 行变 2326 行 diff。
   判据：`git diff --numstat` 行数远超实际改动就回滚重做。
4. **一律绝对路径**。（本会话真出现过 `cd` 后相对路径找不到文件。）
5. **两步以上的 RCON 验证必须用 `--script`**，不要用 `--cmd` 串起来（忙时会命令与输出错位）。
6. **不带 BOM 写字、不把 `.py`/.ps1 写成内联 PowerShell 命令**。
   本次实测：内联 `python -c "..."` 里含中文引号 → **连踩三次**语法错误。**写成文件再跑**。
7. **结论必须有原始证据**：不许"我记得"、不许从乱码里读、不许用不可靠的工具口径下结论
   （例：判断汉化覆盖率要用 `i18n_cover.py`，`lang_audit.py` 看不见资源包）。
8. **不许留未验证的代码**：动 Java 就 `:core:test`；动任务书就 `questbook_check.py --reproducible`
   **+ 起一次服务端看加载日志**；动工具就跑它的自检。
9. **`pack/` 是 packwiz 的领地**：往里放文件前先问"这要分发给玩家吗"。
   答不上来就放 `pack/` 外面——否则它会**静默进索引**，玩家多下一份，
   而且每次 `packwiz refresh` 都产生噪音 diff（*事故*：116 个汉化源文件，见 `INCIDENTS.md`）。
10. **TOML 字符串里不要嵌套双引号**：要引号就用「」或 `'…'`。改完先跑 `lint_questbook_toml.py`。

---

## 七、用户偏好（原话级，别忘）

- **反过度设计**：宁可少做，不要造没人读的抽象层。
- **尽量不让用户手工测试**：能自动化的验收自己做完，只把"物理上必须人做"的留给他。
- **结论要有可核查的原始证据**。
- **记下自己的错**（所以有 `INCIDENTS.md`）。
- **不要语音类 mod**。
- **长任务自行持续推进，不要中途停下来问** —— 但**设计类决策必须先问**。
- **速度稍微提一提**：少寒暄，多做实事。

---

## 八、只有用户能做的事（`NEEDS-YOU.md` 摘要）

1. **启用汉化资源包**（一次性，10 秒）：进游戏 → 选项 → 资源包 → 启用「荒野建国-中文补全」并置顶。
2. **看一眼任务书五章样板**并给腔调意见（§三.3 第 1 步）——这一步**必须进游戏**（或允许我起客户端，
   但那要约 18 分钟 + 6~8G 内存）。
3. **跑客户端批次时腾内存**（关 League / Oopz，或允许我降客户端堆到 5G）。
4. `OPEN-DESIGN.md` 的 5 条设计（一国一城 vs 多城 · 国力合成 · 资源分类与上限 · 边镇实体 · 终局）——
   不阻塞当前工作。
5. 任务书正文的**碑文/方志腔**（我写白话版，他改成自己的字）。**只动 `lang/zh_cn/`**。

---

## 九、我能自己做、但还没做的（离线待办）

| 优先级 | 事项 | 备注 |
|---|---|---|
| **高** | 铺其余 17 章（按五章样板定下的模板） | 等用户看过样板、腔调定了再铺，避免 18 章返工 |
| 高 | Statecraft"提前推进时代"入口（§3.5） | 任务书钥匙的依赖 |
| 中 | **给邦交/防御补 mod 侧信号**（§3.4） | 不补则这两块只能是 `checkmark` |
| 中 | `DEFERRED.md` E1：纯原版性能基线（组 0） | 只起服务端，我能跑 |
| 中 | 方块/物品名 5862 条的规则化补全 | 已有资源包流水线；触发条件见 `DEFERRED.md` C 组 |
| 低 | 300h 内容量复核（`DEFERRED.md` §H2/H3） | |
| 低 | 查清「5 chapter groups vs 我写的 4」 | FTB Quests 多报一个组，功能无影响，待确认它是不是隐式默认组 |

## 十、需要客户端 / 多人的（一次性批次，别零散做）

- **任务书五章真机看手感**（§三.3 第 1 步）——**当前最值得做的一件**
- **§J 实体存了没装回世界**：盘上有（`mca_probe.py` 证实）、`execute if entity @e` 是 0
- **§J4 有玩家在线时结算真的推进**：`/statecraft pending 1.99` + 在线 → 日志出现 `STATECRAFT_SETTLE kind=PERIODIC`
- **M0**：HYW 单位会不会主动打玩家（**挡夺城/计划 5**）
- **A 组联机**（含 **A4 图纸 × 领地 = 项目关键路径**）· **B 组人眼** · **F 组军事 8 条**
- ✅ **旧阻碍已解除**：`dsh-computer-use-win` 已上线（22 个工具），"看画面"类验收
  **我自己截图判定**（`tools/COMPUTER-USE.md`）。但**发送按键进游戏仍然不可靠**，
  所以"操作类"验收还需要人。

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
- ❌ **手写 SNBT** —— 一律由 `tools/questbook.py` 从 TOML 生成（id 才可复现、依赖才不写错）
- ❌ **用 `item` 判据表达"放置/做过"** —— 见 §3.2，那是错的判据

---

## 十三、交接时的工作区状态

- **HEAD = `origin/main` = `b05f877`**，工作区**干净**
  （任务书工具链 + 五章 + accelerate + C18 处置都已提交并推送）
- **无后台任务在跑**；**没有**游戏/服务端在跑（验证用的服务端已退出）
- **进程**：应无残留 JVM。若见到，先 `gradle --stop` / `tools/server_ctl.py --stop`，**不要按名字杀**
- **实例侧**：**任务书已装进实例**（2026-09-20，`tools/install_questbook.py`，12 个文件）
  → 位置 `<实例>/config/ftbquests/quests/`。**可以直接进游戏按 N 看**。
  存档目录数安装前后一致（29），未碰 `saves/` · `options.txt` · `ftbquests-client.snbt`
- **实例侧的 mod 变更**：`quest_enhance` 的 jar 与残留配置**已删除**（C18 处置）。
  它**不会再被装回来**（仓库元数据也 `git rm` 了 + `packwiz refresh`）。
- ⚠️ **实例里的 statecraft jar 还是旧的**：`/statecraft accelerate` 是新加的，
  要让它进实例需要**重编 + 重新落盘**（`gradle :mod:build` 之后把新 jar 拷进实例 mods）。
- **服务端侧（gitignore）**：`server/config/ftbquests/quests/` 有我拷进去验证用的数据；
  `server/world/` 是历史测试世界，未动
- **实例里的测试残留**：`saves/scdev`（我拷的单机测试档）；`options.txt` 已还原成用户的

---

## 十四、重启后怎么跟用户继续

直接说"我从 `NEXT.md` 第三节接着干"，然后开工 —— **不要重新问一遍需求**：
要做什么（`PLAN-questbook.md` + 五章样板）、为什么（用户原话在两份文档里）、
判据怎么选（`QUESTBOOK-CRITERIA.md`）、不做什么（§十二）都已经写下来了。

**当前主线**：五章样板已出、已真机验证 → **等用户看一眼定腔调** → 铺其余 17 章。
如果长工程目标被 disarm，先 `get_goal` → `update_goal resume`。

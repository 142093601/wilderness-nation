# 事故记录（我搞坏过什么，怎么坏的，后来怎么防住）

> 这份文件只记**我的错**，不记 mod 的 bug、也不记玩法设计。目的不是自责，是让同一个坑
> 不出现第二次 —— 每条都要有：现象 → 证据 → 根因 → 修法 → 防回归的测试。
> 最后更新：2026-09-21。

---

## 2026-09-20 汉化源文件放在 `pack/` 里，被 packwiz 当成分发物（会重复下载）

### 现象

第一次给任务书做 `packwiz refresh` 之后，`pack/index.toml` 多出 **124 条**而不是预期的 8 条：
其中 **116 条是 `i18n/zh_cn/*.json`**。

### 根因

`tools/i18n_pack.py` 的分工本身是对的（文件头写得很清楚）：

- **源文件** `pack/i18n/zh_cn/<命名空间>.json` —— 给人 diff / 评审 / 回滚用；
- **zip** `pack/resourcepacks/荒野建国-中文补全.zip` —— packwiz 实际分发的文件。

问题是**源文件放在了 `pack/` 底下**。而 `packwiz refresh` 的语义是「把 `pack/` 下的文件
同步进索引」——它对源文件和分发物一视同仁。于是：

1. 116 个源 JSON 被登记进索引，**玩家装包时会额外下载一份用不到的源文件**
   （真正生效的是 zip，两者内容重复）；
2. 每次 `packwiz refresh`（或 `i18n_pack.py --index`）都会重新登记它们，
   于是 **index.toml 永远有一份 116 条的噪音 diff**，真改动被淹。

### 修法

把源文件移出 packwiz 的领地：

```
pack/i18n/zh_cn/*.json   →   i18n/zh_cn/*.json      （仓库根，仍进 git、仍可 diff）
```

并同步改 `tools/i18n_pack.py` 的 `SRC_DIR`。**zip 与索引条目都不受影响**
（zip 仍在 `pack/resourcepacks/`，仍由 `--index` 登记）。

### 防回归

- 判据：改完之后 `packwiz refresh` 应当**只**反映真实新增/改动，不再出现
  `i18n/` 前缀的条目。跑一次 refresh，`git diff --numstat pack/index.toml` 应为 0（幂等）。
- 规则：**`pack/` 下只放要分发给玩家的东西。** 源文件、草稿、中间产物一律放仓库别处。
  （对照：`pack/mods/*.pw.toml` 是分发物所以放里面；`tools/` 是工具所以放外面。）

### 硬规则（新增）

6. **`pack/` 是 packwiz 的领地**：往里放任何文件之前先问"这要分发给玩家吗"。
   答不上来就放 `pack/` 外面 —— 否则它会静默进索引，既增大玩家下载量，
   又让每次 refresh 都产生噪音 diff。

---

## 2026-09-20 清场逻辑按**镜像名**杀 JVM，会连玩家正在玩的游戏一起杀

### 现象

玩家这边看到 **Crash Assistant 弹窗：“Minecraft 已崩溃但未生成崩溃报告!”**，
以及一个**点不动的卡死窗口**；我这边的客户端也一直进不了世界。

### 证据（都是原始文件里读出来的，不是推测）

| 证据 | 位置 | 内容 |
|---|---|---|
| 崩溃弹窗归属 | `logs/crash_assistant/crash_assistant_app.log` | `PID "10124" is not alive... Minecraft JVM appears to have stopped.` —— 10124 是**我自动化起的那个客户端**，不是玩家的游戏 |
| 那个客户端死在哪 | 同上 | `Reached first tick of TitleScreen: false`、`Joined world successfully: false` → 死在 mod 加载阶段，根本没到标题屏 |
| 有没有真崩溃 | `versions/…/crash-reports/` | 当天**没有**崩溃报告；最新一份是 9-19 的（与本次无关） |
| 有没有原生崩溃 | 全盘 `hs_err_pid*.log` | 最近两天**一个都没有** |
| 卡死窗口是谁 | Windows 应用日志 Id 1002 | `java.exe 21.0.10 停止与 Windows 交互并已关闭`（21:12:35）→ 卡死的是 Crash Assistant 自己那个 JVM，不是游戏 |
| 玩家今天起过游戏吗 | 各版本目录 `logs/latest.log` 的修改时间 | 只有 `1.21.1-NeoForge_21.1.250` 有今天的时间戳，且是 **20:35:55（我那次）**；其它版本目录今天没有任何日志 → 玩家的游戏今天**没起来过** |

### 根因

`tools/paired_check.py` 里的清场函数是这么写的：

```python
def kill_java():
    for im in ("java.exe", "javaw.exe"):
        subprocess.run(["taskkill", "/F", "/T", "/IM", im], capture_output=True)
```

`/IM` = **按镜像名杀**，也就是"这台机器上所有 JVM 全杀"。它的直接后果有两个：

1. **误杀风险**：玩家可能正开着游戏、开着 IDE、开着别的服务，`/F` 一律强杀；
2. **误诊**：被强杀的 JVM **不写崩溃报告**。于是"我杀了它"和"它自己崩了"在日志上都是
   "没有崩溃报告" —— 玩家看到 Crash Assistant 说"Minecraft 已崩溃"，我看到"没有任何崩溃证据"，
   **双方都被引到一个假方向**：去怀疑某个 mod 有问题。

真实发生的是第 2 条：这次被杀的是我自己的客户端（10124），玩家以为是自己游戏崩了。

### 修法

把"哪些进程是我们的"从三处各写一套，收敛到 **一个模块** `tools/pid_guard.py`：

- 启动时登记自己的 pid（客户端 `.client.pid`、服务端 `server/.server.pid`）；
- 认领时**验两件事**：命令行里含 `java`（防 pid 落到别的程序上）、进程创建时刻与登记一致
  （防 pid 被系统复用）；
- 验不过就**什么都不做**，宁可让清场失败报出来，也不猜；
- 停法：先 `taskkill /PID`（不带 `/F` = WM_CLOSE，给游戏存世界的机会），4 秒后还不走才 `/F /T`。

顺手收窄的另一处越界：自动测试结束时原来会删掉 `saves/*/session.lock`（**所有存档**的锁），
现在只删**本次测试那个世界**的锁 —— 删别人存档的锁，等于给"两写同一存档"开门。

### 踩出来的两个新坑（同一晚）

1. **锚点用错了**：pid 文件最初记的是"写文件时刻"。服务端是**就绪之后**才登记的（比进程创建晚
   约 1 分钟），于是自己的服务端被认成"pid 已复用"而不敢杀。改成记**进程真实创建时刻**，比对容差 5 秒。
   这个 bug 是 `--with-server` 那一项测出来的，不是看代码看出来的。
2. **测试失败留下游离服务端**：上一版 selftest 中途抛异常退出，服务端还在跑并占着 RCON 端口，
   导致下一次测试**根本起不来**。现在 selftest 用 `try/finally` 保证无论如何都停掉自己起的服务端。

### 防回归

```
python tools/pid_guard_selftest.py              # 第 1、3 项：pid 复用要拦住、无关 JVM 一个不许动
python tools/pid_guard_selftest.py --with-server # 额外：自己的服务端要认得出来、停得掉、锚点要对
```

实测输出（2026-09-20，机器上另有 3 个无关 JVM 时）：

```
测试前无关 java 进程: [1356, 17492, 23432]
  [1] pid 复用拦截 OK（进程 1356 启动于 2323 秒前，不肯认）
  [3] 未登记的 JVM 一个没动 OK
  .server.pid = '25908\t1789910548'（认领结果 25908）
  [2] 自己的服务端已停 OK
PASS 清场逻辑只动自己登记的 JVM
```

### 硬规则（写进工具里，不靠记性）

1. **永远不按镜像名/特征杀进程**（`/IM`、`Get-Process java | Stop-Process` 一律不许）。
   只按**自己登记过的 pid** 杀，且杀前验"这确实还是我启动的那个进程"。
2. 需要腾内存时，走正规路径：`python tools/server_ctl.py --stop`、`gradle --stop`，
   或者直接告诉玩家 —— 不要自己按占用大小动手。
3. 任何"清理"动作都要能回答：**我凭什么确定这东西是我的？** 答不上来就不动。

---

## 2026-09-20 从控制台乱码里读中文，把错误术语写进了分发任务书

### 现象

汉化子代理回报"术语冲突需裁决"：我给的分发任务书里写 **claim = 圈占**，
而项目里现成的社区汉化基准（`data/i18n-style/openpartiesandclaims.json`）通篇用 **认领**。
按我的任务书翻出来 108 处「圈占」，与整合包其余部分不一致。

### 根因

我早先是用 `Select-String` / `Get-Content` 看那份中文基准的，**PowerShell 控制台按 GBK 解码 UTF-8，
中文全变成乱码**（`Ŀ�������Ѿ������죡`）。我没有换一条能正确读出中文的通道，
而是**从乱码里"猜"出了「圈占」**，并把它当成"社区译法"写进了任务书。

同一天这个坑已经出现过两次（另一次是我把 `lang_audit` 的数字从乱码里读成别的意思）。
乱码不是"看不清"，是**会主动骗人**：看起来像字，实际是错的字节解释。

### 修法

1. 该批产出全量替换 `圈占 → 认领`（108 处），并复验产出里不再出现「圈占」；
2. 术语基准的权威来源改为**文件本身**：任务书写明"先读 `data/i18n-style/<命名空间>.json`，
   用词一律跟它对齐"，不再允许我凭印象给术语表；
3. 新增 `tools/i18n_verify.py`：所有产出由**我的**验收器统一判（键集合 / 占位符 / 空值），
   子代理自检只当参考 —— 它们的自检脚本是各自写的，口径不一致。

### 硬规则（新增）

4. **中文/任何非 ASCII 内容的判读，一律走能正确解码的通道**：
   用 `read` 工具读文件，或让 python 打印 `ascii()` 转义/写文件后再读。
   **不许从 PowerShell 控制台的输出里读中文**，更不许从乱码里"猜"字。
   （本机 `Select-String`/`Get-Content` 对 UTF-8 中文会按 GBK 解码成乱码，这是默认行为，不是偶发。）
5. **写文件必须显式指定 `encoding="utf-8"` 与 `newline="\n"`**，并且**只改自己那几行**。
   同一天踩到：用 `Path.write_text` 往 `pack/index.toml` 追一行，Windows 文本模式把全文
   `\n` 转成 `\r\n`、又把条目之间的空行吃掉 —— 323 行的文件变成 **2326 行 diff**，
   真正的改动被淹没在噪音里。判据：动手前先看 `git diff --numstat`，
   **行数远超"我改的那几行"就说明方式错了**，回滚重做，不要提交。

---

## 2026-09-21 批量清理探查脚本时，把**被 git 跟踪的正式文件**当垃圾删了

### 现象

调研 voxy / Distant Horizons 时我在 `tools/` 下写了一堆一次性探查脚本（`_dh_probe*.py`、
`_vss_probe*.py` …），收尾时按"`_` 前缀 = 临时文件"的模式批量删除并想保留几个：

```
Get-ChildItem tools -Filter '_*.py' | Where-Object { $keep -notcontains $_.Name } | Remove-Item -Force
```

删完 `git status` 出现：

```
 D tools/_selftest.py
```

**`tools/_selftest.py` 是仓库里被跟踪的正式文件（7380 字节），不是我的临时脚本。**
它同时也是 gitignore 里唯一一个 `!` 例外（`!tools/_selftest.py`）—— 这个例外的存在
本身就是"它是正式文件"的信号，我却在动手时没读。

好在 `git checkout -- tools/_selftest.py` 立刻恢复，**没有造成实际损失**。

### 根因

1. **用"文件名模式"判断所有权，而不是用 git 的事实。**
   `_` 前缀在我的习惯里=临时，但 `_selftest.py` 是别人的命名习惯被沿用下来的。
2. **批量删除没有先干跑（dry-run）。** 我是在删完之后才通过 `git status` 看到后果，
   顺序反了 —— 应该在删除**前**先看这批文件里有没有被跟踪的。
3. `.gitignore` 里那条 `!tools/_selftest.py` 例外**我其实早就知道**（是本项目约定的一部分），
   但动手那一刻没有把它当检查项。

### 修法

1. 立刻 `git checkout -- tools/_selftest.py` 恢复，并确认 7380 字节、`git status` 回到干净；
2. 保留有复用价值的探查脚本（`_dh_probe.py` / `_vss_inspect.py` / `_sodium_v.py` 等），
   它们已由现有 `tools/_*.py` 规则忽略，不会污染仓库。

### 硬规则（新增）

6. **批量删除任何文件之前，先跑 `git ls-files <该模式>` 看有没有被跟踪的**：
   被跟踪的文件**不在**批量删除的范围内，要删必须单独、显式、说得出理由。
   判据：删除命令执行**前**就知道"这批里 0 个被跟踪"，而不是执行后靠 `git status` 发现。
7. **`.gitignore` 里的 `!` 例外 = 该目录下的正式文件清单**。
   动 `tools/` 下任何东西之前，先看这份例外里有没有它。

### ⚠️ 同一天第二次踩同一个坑（说明规则本身不够）

<!-- 补记：写规则不能防止复犯 -->

写完上面两条硬规则的**同一次会话里**，我又用同样的模式删了同样的文件，
再次误删 `tools/_selftest.py`，再次靠 `git checkout --` 恢复。

**根因不是"我忘了规则"，是"规则不是可执行动作"。**
文字规则依赖我在动手那一刻想起来；而那一刻我的注意力在"要达成什么"上，不在"要检查什么"上。
同类错误出现两次 = 这个防线的形式错了。

### 修法（把规则变成工具）

新增 `tools/cleanup_temp.py`，把三步检查固化成代码：

1. 默认以**脚本自身所在仓库**为操作对象 —— 不是 `cwd`。
   （踩到的第二个坑：pwsh 的默认 cwd 是 `D:\project`，而 `D:\project` **自己也是一个 git 仓库**，
   「向上找 `.git`」会就近命中它，于是脚本静默地去搜 `D:\project\tools\`。锚在脚本位置才不会找错目标。）
2. `git ls-files` 判定所有权，**被跟踪的一律排除**并在输出里标 `[PROTECTED]`。
3. 默认**干跑**；检测到被跟踪文件混在批次里就 `exit 2`，即使加了 `--delete` 也不删它们；
   真删之后**复核** `git status`，若出现被跟踪文件丢失则 `exit 1` 并打印恢复命令。

用法：

```
python tools/cleanup_temp.py            # 干跑，看清单
python tools/cleanup_temp.py --delete   # 真删
```

**判据**：从 `D:\project`、`nation-pack\`、`nation-pack\tools\` 三个不同 cwd 运行，
都必须报 `仓库根: D:\project\nation-pack`、`[PROTECTED] tools/_selftest.py`、`exit 2`。

### ⚠️⚠️ 第三次踩同一个坑 —— 就在建完工具之后（这才是真正的教训）

同一次会话里、**建好 `cleanup_temp.py` 并验证通过之后**，我又误删了同一个 `tools/_selftest.py`。

经过：那次我是在收尾时顺手清掉一批一次性探查脚本，用的是

```powershell
Get-ChildItem tools -Filter '_*.py' | Where-Object { $keep -notcontains $_.Name } | Remove-Item -Force
```

—— **裸 `Remove-Item`，根本没调用我刚写好的 `cleanup_temp.py`。**

**所以第三次的根因和第二次不同，必须分清：**

| 次 | 根因 | 防线演进 |
|---|---|---|
| 第 1 次 | 不知道存在被跟踪的 `_selftest.py` | 写了两条文字硬规则 |
| 第 2 次 | 文字规则在"动手那一刻"想不起来 | 把规则做成工具 `cleanup_temp.py` |
| **第 3 次** | **有工具却不用** —— 裸命令更顺手，工具需要我主动想起来调用 | **见下方** |

**结论：一个"需要我记得去用"的工具，和一条"需要我记得去看"的规则，防线强度是一样的。**
两者都依赖我在收尾那一刻的注意力，而那一刻正是注意力最松的时候（活干完了，
只想赶紧清理干净）。**真正的修法只有两个方向：让正确做法成为唯一做法，或让错误做法失败。**

### 修法（第三次，做减法而不是加法）

1. **立即 `git checkout -- tools/_selftest.py` 恢复**（7380 字节，`git status` 回到只有 `M DESIGN-VISTA.md`）；
2. **改掉命名口径，让"临时文件"和"正式文件"在名字上不再混淆**：
   本项目此后**不再用 `_` 前缀存自己的临时脚本**。临时脚本一律叫
   `_probe_*.py` / `_scratch_*.py`（`.gitignore` 已有 `tools/_probe_*` `tools/_stats_*` 规则，
   必要时补 `tools/_scratch_*`），
   这样 `tools/_*.py` 这个宽口径通配就**再也不需要**用了 —— 我不再有任何理由写
   `tools/_*.py` 这种会扫到正式文件的模式。
3. **保留但降级 `cleanup_temp.py` 的角色**：它仍是干跑核对工具，但**不再是主要防线**；
   主要防线是第 2 条 —— **不给自己留下"会扫到正式文件"的删除模式**。

**判据**：此后任何一次批量清理，用的通配必须是 `tools/_probe_*.py` 或 `tools/_scratch_*.py`，
**不允许再出现 `tools/_*.py`**。这一条可以机械检查：
`git grep -n "tools/_\*\.py"` 在文档/脚本里只应出现在说明"不要这样用"的地方。

---

## 2026-09-22 任务书生成器**丢内容**：按行合并 SNBT，多行数组的正文被吃掉

### 现象

优化任务书文案时新增了 7 条 `description`，跑 `python tools/questbook.py` 之后
**产物里这些描述是空的**：

```
quest.79D9FD4E9FEDCCAD.description: [
}
```

数组开了头就没有内容，而且**文件被截断**（3 个文件括号不平衡，共丢 7 条）。
TOML 源是对的，`lint_questbook_toml.py` 说 0 失败，`questbook_check.py` 也 **PASS**
（它不检查"产物的 description 是否为空"）。

### 根因

`merge_lang()` 把 SNBT **按行**处理，用一个键 = 一行的假设去判断"这个键有没有"：

```python
for ln in new_lines:
    s = ln.strip()
    if s in ("{", "}") or ":" not in s:
        continue                        # ← 数组的后续行没有冒号，被丢掉
    if s.split(":", 1)[0].strip() not in old_keys:
        added.append("\t" + s)          # ← 只收第一行
```

而 `description` 是**多行数组**：

```
quest.XXXX.description: [      ← 含冒号，但值只开了个 `[` ⇒ 被当成"新键"收录
    "第一段"                    ← 不含冒号 ⇒ 被丢弃
    "第二段"                    ← 不含冒号 ⇒ 被丢弃
]
```

于是写出来就是 `description: [` 后面直接 `}`。

**这个 bug 一直存在，只是以前没暴露**：已存在的键走 `old_keys` 分支、整块不动，
所以只有"**新增**一个多行数组键"时才会触发。我第一次给某章补 description
就正好踩上。

### 第二次伤害：`--force-lang` 暴露了格式不一致

修 bug 过程中我用 `--force-lang` 全量重写 lang，结果发现：
生成器 `snbt()` 产出**一级缩进**（`\t"..."`），而仓库里已提交的
`lang/chapters/*.snbt` 是**两级**（`\t\t"..."`）。
`merge_lang` 的"保护已有键"把老格式留住了，所以这个不一致也一直藏着。
全量重写会把整个文件改成一级 ⇒ **14/14 行的大面积格式 diff**。

（顺带发现项目里格式本来就不一致：`landing.snbt` 两级、`create/defense.snbt` 一级。）

### 修法

1. **`merge_lang` 改成按块合并**（`_parse_snbt_blocks`）：一个键的块 =
   从 `key:` 那行起、到括号配平为止。多行数组整块带走。
   附单元测试：已有的键保持旧值 / 旧多行数组完整 / 新键多行数组完整 / 括号平衡 —— 4/4 通过。
2. **不再用 `--force-lang` 全量重写**，新增 `tools/lang_apply.py`：
   它**照抄目标文件自己的缩进**（`detect_indent` 实测，不假设），
   只插入/替换需要的键，其余字节不动。这样 diff 最小、格式不漂移。
3. 从 `git checkout HEAD -- .../lang` 恢复被截断的文件，再用 `lang_apply.py` 精确写入。

### 硬规则（新增）

8. **改生成器逻辑后，必须验证"最小输入也能产出完整文件"**，而不是只看 `PASS`。
   本项目的 `questbook_check.py` **不检查产物的 description 是否为空** ⇒
   它 PASS 不代表内容没丢。改完生成器要**人工看一段产物**。
9. **`--force-lang` 是危险开关**：它会抹掉 `merge_lang` 的保护并统一格式。
   日常改文案用 `tools/lang_apply.py`，别用 `--force-lang`
   （除非明确要统一格式，且接受大面积 diff）。
10. **校验器 PASS ≠ 内容正确**。这次的 7 条空描述在 9 项校验下全绿。
    "机械校验"覆盖的是结构与引用，**覆盖不到"这段文案是不是空的"**。

---

## 2026-09-22 我自己的检测器两次误报，差点改坏好文案

### 现象

写 `tools/text_audit.py` 审文案时，第一版报了 6 个"错误"，其中 **5 个是假的**：

| 报的 | 实际 |
|---|---|
| 口语化用词：贼 | 匹配串写错（我写成了单字），原文里根本没有"贼" |
| 夸张/中二用词：燃（×3） | 原文是"**燃料**""**燃烧值**""**点燃**"——正确技术用词 |

另外"读不出该做什么"报了 52 条，其中**大部分是误报**：类目章的定位就是
"介绍这个 mod 能干什么"，很多任务**故意**不给行动指令；而主线章那些
"熔炉阵列：≥8 台并联，一次投料连续工作"是**好文案**，只是我的"可执行动词"清单
太窄。

### 根因

**用单字/宽口径做文本匹配**，且**没有回看命中的原文**就下了判断。

### 修法

1. 口语化标记一律用**多字组合**，不用单字；中二标记改成**短语**（"燃起来了"而非"燃"）。
2. 类目章的"读不出该做什么"降级为**提示**并注明"多半是设计使然"；
   主线/技艺章才算警告。
3. **回看命中原文**再决定改不改 —— 这条是纪律，不是代码能替代的。

**判据**：改动文案前，先确认命中的原文确实有问题。
`text_audit.py` 的输出是**待复核清单**，不是"必须改的清单"。

---

## 2026-09-22 **加任务会把全包 id 打乱**：id 分配器的"冲突加盐"是数据损坏源

### 现象

把 Create 章从 15 条扩到 68 条（中间插入 53 条），生成后 `questbook_check.py` 报：

```
FAIL -- 13 条问题
  x [③ 依赖] chapters/create.snbt: 任务 237CFF0E5B694802 依赖 332CF44211BA55C8，后者不在本章内（悬空引用）
  x [⑤ 语言键] lang/chapters/create.snbt 有孤儿键 quest.36E73CFBDF0010C6.title（对应任务已不存在，玩家改的字会丢）
  ... 
```

进一步统计：**355 个孤儿 lang 键，覆盖全部 21 章** —— 不只是 create 章。

### 根因（两个独立的破坏，都来自同一处设计）

`IdAllocator.make()` 原本是：

```python
salt = 0
while True:
    raw = "|".join(parts) + (f"#{salt}" if salt else "")
    h = sha256(raw)[:16].upper()
    if h not in self.used:      # ← self.used 跨章共享
        self.used.add(h); return h
    salt += 1                    # ← 看情况加盐
```

**破坏 1：插入任务会全局位移 id。**
`self.used` 是跨章共享的，所以"加盐的次数"取决于**处理顺序**。
我在 create 章中间插入 53 个任务 → 该章后面任务的加盐次数全变 → id 全变。
而 `.snbt` 的依赖和 `lang/` 的文案键还指着旧 id ⇒ 悬空 + 孤儿。

**破坏 2：前向引用会拿到两个不同 id。**
`compile_chapter` 遇到"依赖先出现、任务后出现"（`deployer` 在第 28 位依赖第 43 位的 `brass`）时：

```python
did = id_map.get(d) or self.ids.make("quest", ch.key, d)   # 就地算一次
id_map.setdefault(d, did)
...
qid = self.ids.make("quest", ch.key, name)                  # 轮到它时又算一次
```

两次调用之间 `self.used` 已变 ⇒ 加盐兜底可能给出**不同**的 id。
`brass` / `mechanical_arm` 正是这样变成悬空目标的（`CC2EA0362BF3EBAB` vs 期望 `332CF44211BA55C8`）。

**⇒ 任何"看情况加盐"的逻辑都会让 id 依赖调用顺序。**
而 id 必须在**加任务 / 改顺序 / 前向引用**三种情况下都不变 —— 否则"加内容"这个动作
本身就是在破坏已有数据（玩家进度、手改的文案）。

### 修法

`IdAllocator` 改成**纯函数**：`id = sha256("片段|片段")[:16].upper()`，
**没有兜底、没有状态**。真撞车就 `raise`（那时应改名字或加长截断，而不是悄悄加盐）。

撞车概率：64 位截断，本项目 ~400 个 id 时期望冲突约 1e-15，实际不可能发生。

### 验证

改完后：`ids_total 799 / ids_unique 799`，零悬空、零孤儿；
`questbook_check --reproducible` PASS；服务端
`Loaded 5 chapter groups, 22 chapters, 350 quests` + 零 Unknown registry key + FTB Quests 零告警。

### 硬规则（新增）

11. **id 必须是"输入的唯一函数"** —— 不许有计数器、加盐、顺序依赖的兜底。
    判据：**在任意位置插入一条新任务后，其余任务的 id 一个都不许变**。
    这一条要能在评审时机械验证（插入 → 重新生成 → 比对 id 集合）。
12. **改完生成器的 id 逻辑，必须跑 `--reproducible` 并检查 `ids_unique`。**
    `quests` 数对不代表 id 对；这次 350 个任务数一直是对的，错的是 id。

---

## 2026-09-22 我第三次被"读 stale 文件"骗：连续 4 轮基于旧输出下结论

### 现象

排查上面那个 id 问题时，我反复用**上一次运行的输出文件**（`data\_qc*.txt`、`_text_audit.json`）
当证据，得出互相矛盾的结论：

- 一会儿"两个 id 都在文件里"，一会儿"都不在"
- 一会儿报"字幕重复"，一会儿"0 重复"
- 甚至把 3 条悬空依赖归因给"分配器修好了"，而实际文件还是旧态

最典型的一次：我用自己的正则数出"68 个 quest id，两个目标 id 都在"，
而校验器报"不在"——**因为校验器读的才是当前文件，我读的是缓存/旧文本**。

### 根因

**没有为"这次运行"建立证据边界。** 我连续跑了很多次生成+校验，
但每次读输出时用的是固定文件名（可能被覆盖、也可能没被覆盖），
而且中间又插入了其他操作，无从判断某个文件属于哪一轮。

### 修法（纪律，不是代码）

1. **每轮验证把输出写进唯一的、带序号的文件名**（`_qc_1.txt`、`_qc_2.txt`…），
   或**删除后重建**（`Remove-Item` → 跑 → 读）。
2. **读证据前先确认它的 mtime 晚于本次操作的开始时间。**
3. 当两个来源给出矛盾结论时，**优先信"直接读被操作文件本身"那个**，
   不要继续在推断层叠加推断 —— 立刻回到原始文件重读一次。

**判据**：任何一个"结论"要能指向**当次运行**产出的文件，且我报得出它的 mtime。

### 同一天相关的第二次

同一轮里我还把**控制台里 GBK 解码后的中文**当成原文来读，
两次据此判断"文案被截断了 / 内容不对"，而用 `read` 工具重读后发现原文完好。
这条已经在上一节记过（"不许从控制台读中文"），这次是复犯 —— 说明
**光有规则不够，要形成反射：看到中文输出先怀疑编码，不用它下结论。**





---

## 2026-09-22 重建 registry.json 时漏了 `--game-root`，把**原版物品全丢了**（211 条假报警）

### 现象

`tools/item_index.py --verify-toml` 从 **missing 0** 变成 **missing 211**，
而且缺的全是 `minecraft:anvil` / `minecraft:barrel` / `minecraft:stone_bricks` 这种
**最普通的原版物品**。第一反应是"agent 们写了一堆错 id"。

### 根因

我为了"让注册表反映真实装机状态"重建了 registry.json，但只传了 `--mods`：

    python tools/build_registry.py --mods <实例>/mods        # ← 少了 --game-root

而**原版 assets 不在 mods 目录里**，它来自 `<game_root>/libraries/net/minecraft/client/…-extra.jar`。
少了这一层，注册表就只剩 mod 的物品 → 所有原版 id 都判"不存在"。

正确的调用（`--game-root` 指的是 **`.minecraft` 那一级**，不是 `versions/<版本>` 那一级）：

    python tools/build_registry.py \
      --mods "D:\game\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250\mods" \
      --game-root "D:\game\PCL\.minecraft"

### 教训（比这次的具体错误更值钱）

- **校验器的红/绿只等于它读的那份注册表**。注册表本身错了，它会一本正经地
  报 211 条假 FAIL —— 而假 FAIL 会诱使我去"修"一堆本来正确的文案。
  **看到大批量、集中在最常见物品上的 FAIL，先怀疑工具与数据，不怀疑内容。**
- `build_registry.py` 的输出里有 `jars=nnn` 这一行：**改完必须回头看它**。
  少了原版 jar 时 `jars` 会比"mod 数 + 1"小 —— 这次的 231 vs 应有的 232 就是线索。
- 这条与"陈旧产物"是同一族问题：**产物不是真相，产生它的输入才是。**

### 硬规则（新增）

13. **重建任何"可查 id 的注册表"之后，第一件事是跑 `--verify-toml` 并核对
    `jars=` 是否等于「mod 数 + 1（原版）」**；数字不对就不要拿它的结论去改内容。

---

## 2026-09-22 大规模并行派 agent：15 个里 9 个中途失败，但**失败者已经写了文件**

### 现象

为了把任务书从 437 条铺到 1400 条量级，我一次派了 8 个 agent（随后又派 6 个）。
回报是：第一批 8 个成功 4 个、失败 4 个；第二批 6 个**全失败**，且失败者的
closing message 是空的。

### 根因（推断，有证据的部分我标出来）

- **有证据**：失败 agent 的**文件已经写进去了** —— `100-cat-building.toml` 已经 56 条、
  `140-cat-gear.toml` 已经 52 条，只是它们停在"写到一半、还没跑自检"的状态，
  留下 2 个 `Unclosed array` 的文件。
- **有证据**：并发 8 个时成功率约 50%，而在已有 7 个在跑时再派 6 个 → 全灭。
  同一时段**每个 agent 都要跑 python 自检**，本机只有 4 核。
- **推断**：失败更像**总负载/预算**问题，而不是单个 agent 的任务太难
  —— 因为失败者写出来的内容质量与成功者没有差别。

### 处置

1. **失败 ≠ 什么都没做**：立刻用 `tools/_probe_roots.py` 逐个文件列出条数，
   再跑 lint / `--check-only`，把"写到一半"的文件补完 —— 而不是重新派人重写。
2. 失败留下的两类硬伤是**机械可修的**，已做成工具：
   `tools/toml_quote_fix.py --write --commas`（补多行数组的行尾逗号 + 换引号），
   一次修好了 2 个文件 / 5 处。
3. **并发上限压到 3~5 个**，并且**每批之间要等前一批真正落地**再派下一批。

### 硬规则（新增）

14. **派出去的 agent 失败时，先盘点它留下的文件，再决定是"补"还是"重做"。**
    重做会覆盖已经写好的部分，是最贵的错误处置方式。
---

## 2026-09-22 我用**子串替换**修 id，把一批合法 id 也改坏了（25 处）

### 现象

服务端报 37 条 `Unknown registry key`（真机拒绝的 id），我写了个替换脚本按
"坏 id → 好 id" 全量 `str.replace`。替换本身生效了，但紧接着离线校验器报了**新的**
一批不存在 id：

    create:package_filter    -> create:cardboard_package_12x12_filter     ← 不存在
    create:package_frogport  -> create:cardboard_package_12x12_frogport   ← 不存在
    create:packager          -> create:cardboard_package_12x12r           ← 不存在
    create_dragons_plus:white_dye_bucket -> ..._bucket_bucket              ← 不存在

### 根因

`str.replace("create:package", "create:cardboard_package_12x12")` 会命中
**更长 id 的前缀**。而这几个"更长 id"恰恰是**正确**的（`create:package_filter`、
`create:packager` 都是真实注册项）。等价地：**坏 id 是好 id 的前缀**时，
一次朴素替换就把好 id 变成垃圾。

更糟的是第二层：`white_dye` → `white_dye_bucket` 这条，在文件里本来就是
`white_dye_bucket` 的地方又拼了一次后缀，得到 `..._bucket_bucket`。

### 修法

1. 立刻写了一支只做还原的脚本（`_probe_fix_damage.py`），把 25 处改回来；
2. 纪律：**替换注册名一律按整词匹配** —— 用
   `re.sub(r'(?<![\w:])' + re.escape(bad) + r'(?![\w])', good, text)`，
   或者先按 `"` 引号切出完整 id 再比较，**绝不裸 replace**。

### 硬规则（新增）

15. **批量改 id 前先数一遍"这个字符串是不是别的 id 的前缀"**。
    `create:package` 是 `create:packager` 的前缀 —— 这类前缀关系在 mod 里很常见
    （`x` / `x_filter` / `x_slab` / `x_block`）。批量替换后**必须**再跑
    `item_index.py --verify-toml`，因为它就是抓这个的。

---

## 2026-09-22 在 agent 还在写文件的时候去改同一批文件

### 现象

离线校验过了、真机也过了，我又在**若干 agent 仍在编辑**同一批
`tools/questbook/chapters/*.toml` 的时候，用脚本做了 90 处 id 替换。

### 风险（当时没有立刻暴露，但确实存在）

agent 的写入是"整文件重写自己缓冲区里的内容"。我在它写之前改的，会被它下一次
写入**覆盖掉** —— 于是"我修好了"与"它又写回来了"会互相抵消，
而两边的自检都可能是绿的（各自跑的时机不同）。

### 处置与纪律

- 所有跨文件的批量修改**放到 agent 全部落地之后**做（这一轮的最终验证就是这么排的）。
- 若不得不在中途改，就把那次修改当作**可能被回滚**，最后一轮必须**重跑**：
  `lint → verify-toml → runtime_deny --check → check-only → 服务端真机`。
- 判断"agent 是否还在写"不要靠感觉：`git status --short` 的文件 mtime + 自己那轮的
  文件清单对比，是唯一可核对的证据。
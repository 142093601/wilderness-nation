# NEXT：给"重启后的新会话"的交接说明

> **为什么有这份文件**：DSH 重启（或新开会话）之后，**上一段对话的历史就没了** ——
> 新会话只看得到仓库。所以恢复工作靠这份文件 + git + 长工程目标，不靠记忆。
> **读到这份文件时先做三件事**：① `git log --oneline -3` 看最新提交；② 读本文件的"下一步"；
> ③ 用 `get_goal` 看长工程目标是否还在（在的话 `update_goal` 用 `resume` 重新授权）。

---

## 一、这是什么项目

- **仓库**：`D:\project\nation-pack`（`github.com/142093601/wilderness-nation`，公开，main）
- **两条腿**：① 整合包（`pack/`，195 mod + `CONFLICTS.md` 冲突账本）；
  ② **自研 mod `Statecraft`（「邦交」）**，在 `mods/statecraft/`（`core` 纯 Java 零 MC 依赖 + `mod` NeoForge 薄层）
- **设计文档**：`NATIONS.md`（mod 侧）· `DESIGN.md`（包侧）· `OPEN-DESIGN.md`（**待定设计，搁置中**）
- **纪律**（用户明确要求）：反过度设计 · 尽量不让用户手工测试 · 结论必须有原始证据 · 记下自己的错

| 层 | 状态 |
|---|---|
| 计划 1（生成）、计划 2（存档+迁移+mod 薄层）、计划 3（结算+外交+事务日志） | ✅ 完成 |
| **计划 4 的 core 侧（阶段 2）** | ✅ 完成 —— 数据层（places/events/buildings/staff/letters 五份 json）· 文本渲染（地名池+模板）· 周报聚合 · 国书状态机+**触发侧**（§9.4）· 图纸授权（§10.1）· 情报册三档+**接触**（§11.1）· 村民绑定（§10.3） |
| **阶段 3（mod 薄层 + 真机）** | 🟡 **主体已通** —— 环境读数 · 建筑服务（§10.2 三条要求 + 重复自愈）· **结算调度**（§七）· 九条命令，都在真服务端上跑过 |
| M2 真机 | ✅ 命令通道全通（`info` / `roundtrip=OK` / `buildings`），存档落盘+读档已证 |
| M0（HYW 会不会打玩家） | 方案+脚本就绪（`PLAN-m0-hyw.md` / `tools/tests/m0_hyw.txt`），**未跑**，只挡计划 5 |
| `treaties.json` 数据化 · 玩家侧落盘 | **有意没做**，见 `DEFERRED.md` §I（各写了触发条件） |
| **玩家行为 → 态度**（§十二） | ✅ 已做（放置方块那条）—— 真机实测把 n1 逼到态度 −43.5 并**自行宣战**；「击杀其单位」那条等 HYW↔国家的映射 |
| **世界会不会自己清空** | ⚠️ **实测发现过**：原参数下末代只剩 1.0 国。已按实测表调 `warScoreStep`/`absorptionWarScore`，现在末代平均 6.2 国（`NATIONS.md` 八点五）|

**当前 379 个 JUnit 全绿**（`gradlew :core:test`，命令见 §三）。

**阶段 3 现在能用的 `/statecraft` 子命令**（都只在服务端跑，不需要客户端）：

| 命令 | 干什么 |
|---|---|
| `info` / `roundtrip` / `regen` | 建库、NBT 往返自检、重建（`info` 一行给 seed/era/seq/hours/buildings/letters/met）|
| `scan <pos>` | §10.5 的四个环境读数 + 每座建筑的档位判定 |
| `anchor <pos>` | 把锚点方块登记成一座建筑 |
| `buildings` | 登记表 + 绑定健康度 + 村民名字/职业/`no_ai` + 重复数 |
| `materialize` | 按 §10.3/§10.4 的幂等规则跑一次实体化（含清理重复村民）|
| `advance <hours>` | 手动推进一次周期小结算（脚本靠它几分钟跑完 300 小时）|
| `contact <pos>` | §11.1 的接触判定（走近 512 格）|
| `build <pos>` | 报告一次「在 pos 放置方块」（§十二 的态度变化；真机无客户端时的观察口）|
| `diplomacy <nation> <action> [dev]` | §11.2 的六个外交动作 |
| `letters` / `answer <id> accept\|refuse` | §9.4 的国书列表与回复 |

**真机踩坑记录**见 `DEFERRED.md` §J（实体存了没装回世界）、§K（`--cmd` 回显错位）、
§L（门槛值与惩罚值同值 → 规则变死代码）。

## 三、下一步（**从这里接着干**）

### 阶段 3 剩下的四件事（按优先级）

1. **实体验收要带客户端**（`DEFERRED.md` §J + §J4）：无人在线的服务端里，实体**在盘上有、
   在世界上没有**（`tools/mca_probe.py` 看过盘：文书都在 `world/entities/r.0.0.mca` 里、
   标签齐全；但 `execute if entity @e` 是 0 个）。最可能是原版行为 —— 实体要区块进入
   entity-ticking 才装，而那需要玩家在实体距离内。
   脚本已备好：`tools/tests/statecraft_build.txt`，
   `python tools/game_agent.py --server 127.0.0.1:25565 --script tools\tests\statecraft_build.txt`。
   （2026-09-20 试过一次：客户端 232 mod 在等待时间内没就绪 —— 先腾内存或加长等待再试。）
   §J4 还要顺带验"**有**人在线时结算真的会推进"（把 `pendingHours` 先改成 1.99 就不用等 2 小时）。
2. **玩家行为 → 态度**（§十二 的那张表：击杀其单位 −1/次、在都城 64 格内放置方块 −2/次、
   通商 +3、完成国书 +10）。**现在没有任何东西会拉低态度**，所以压力/宣战/国书只能靠
   `/statecraft diplomacy` 手动触发；这是让整个世界"活起来"的最后一块输入。
   都城坐标一直在 `Nation.x/z` 里，所以"在都城 N 格内放置方块"现在就能做（不需要等都城实体化）。
3. **自研村民实体**（§10.2）：现在用**原版 Villager + 两个持久化标签** + `setNoAi(true)`
   （禁繁殖/锁职业/游荡限制三条已经满足，见 `BuildingService.spawnStaff` 的注释）。
   换成自研实体换来的是**外观与对话**（自定义模型、对话树），而不是这三条行为。
4. **OPAC 领地接入**（§10.5 的第四个读数）：现在是 `ClaimProbe.unclaimedIsInside()` 的
   一次性警告回退（不接的话整条链路连试都试不了）。要等 `DEFERRED.md` A4
   （图纸 × 领地，联机验证）那一次一起做 —— 那时才知道该怎么问 OPAC。


### 再往后

```
M0（HYW 会不会打玩家）· M2 命令通道补验 · v1→v2 迁移实档确认   ← 一次性批量
计划 5：夺城 + 玩家侧账本（追踪清单 / 已购图纸 / playerLedger 落盘）
阶段 4：City + 国力合成 + 多城（等用户拍板，见 OPEN-DESIGN.md）
```

**跑测试的命令**（本机没装 Gradle，用缓存分发）：

```powershell
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phgltp5f9b9e\gradle-8.11.1\bin\gradle.bat" `
  -p D:\project\nation-pack\mods\statecraft :core:test :mod:build --console=plain
```

**服务端真机**（我们这个 mod 必须装进服务端 —— `tools/lists/server-mods.txt` 已含它）：

```powershell
python tools\server_ctl.py --setup --start
python tools\server_ctl.py --cmd "statecraft info"
```

（下面是阶段 1 当时记的细节，留作参考）

```
mods/statecraft/core/src/main/java/com/wildernessnation/statecraft/core/env/
    EnvironmentSnapshot.java     ✅ 已写（4 个读数；刻意没有方块/形状信息）
    EnvironRequirement.java      ✅ 已写（一档要求 + 逐条 unmet 原因）
    EnvironmentVerdict.java      ⬜ 待写（可用档位 + 下一个档位 + 未满足原因）
    EnvironmentRules.java        ⬜ 待写（evaluate(tiers, snapshot)：取满足的最高档）
mods/statecraft/core/src/test/java/.../core/env/EnvironmentRulesTest.java   ⬜ 待写
```

**测试要覆盖**：阈值边界（59.9% vs 60%、体积 11 vs 12）· 多条不满足时**全部**列出来 ·
档位取"满足的最高档" · 档位表空/重复 id 要抛 · **以及一条结构性护栏**：
断言 `EnvironmentSnapshot` 的 record 分量恰好是那 4 个名字（有人加"方块列表"就红）。

然后按 `PLAN-m1-env-idempotency.md` 的 Task 1 / 3 / 4 继续（Task 1 = `Building` + `WorldState.buildings` +
并入 v2 迁移；Task 3 = 幂等决策；Task 4 = 实体化节流），最后 Task 5 文档 + 提交。

**跑测试的命令**（本机没装 Gradle，用缓存分发）：

```powershell
& "$env:USERPROFILE\.gradle\wrapper\dists\gradle-8.11.1-bin\eac4u065zwes5phglpt5f9b9e\gradle-8.11.1\bin\gradle.bat" `
  -p D:\project\nation-pack\mods\statecraft :core:test --console=plain
```

**每个 Task 的循环**：写代码 → 测试全绿 → 文档同步（`NATIONS.md`/README/计划文档）→
`git add -A` → 用 write 工具写提交信息文件 → `git commit -F <文件>` → `git -c http.proxy= -c https.proxy= push origin main`。

## 四、之后的工作链（已与用户确认）

```
阶段 1  M1 收尾（环境判定 + 幂等决策）            ← 现在在这
阶段 2  计划 4 的 core 侧（数据层/文本渲染/周报/国书/图纸授权/情报册/村民绑定/占位内容）
   ↑ 以上全部离线，不用开游戏
阶段 3  mod 薄层 + 真机（**一次性批量**）：真实环境读数 · 占位方块/村民 · 图纸加农炮链路
        + 顺带清掉积压：M0 · M2 命令通道补验 · v1→v2 迁移实档确认
阶段 4  暂缓：City + 国力合成 + 多城（等用户拍板，见 OPEN-DESIGN.md）
```

## 五、等用户拍板的事（不要自己替他定）

`OPEN-DESIGN.md` 里 5 条：一国一城 vs 多城（含 B-lite 方案与四层难度评估）· 国力的合成方式 ·
城内资源分类与上限公式 · 边镇要不要有实体 · 终局。
**都不阻塞阶段 1/2**，所以搁着继续推进。

## 六、电脑控制插件（`dsh-computer-use-win`）

重启后模型侧应出现 **`mcp__wincu__*`** 共 22 个工具（用法见 `tools/COMPUTER-USE.md`）。
- **自检**：`python tools\wcu_probe.py`（握手 + 列工具 + health）
- **立刻能用上工作的三处**：
  1. **驱动 PCL 启动器**（原生控件程序，UIA 很强）：改内存、加 `-Dfile.encoding=UTF-8`、点安装器按钮 ——
     以前这些要用户自己去 GUI 里点；
  2. **抓画面做证据**：`snapshot` 直接返回图片（`type:"image"`），比自建的 `win_ctl` 截图更省事；
  3. **阶段三看游戏画面**：开世界后判读建筑/村民/情报册长什么样（MC 是自绘窗口，UIA 树是空的，
     所以游戏里用"截图 + 坐标点击 + 按键"）。
- 用之前先 `list_windows` 拿 `nativeWindowHandle`，后续调用都带上（焦点随时会跑）。
- 两个坑写在 `tools/COMPUTER-USE.md`：LSP 式 `Content-Length` 分帧、Junction 路径会让入口守卫失效。

## 七、重启后怎么跟用户继续

用户在等你**继续推长工程**。直接说"我从 `NEXT.md` 的第三节接着干"，然后开工；
如果目标被 disarm 了，先 `get_goal` → `update_goal resume` 再干。
**不要重新问一遍需求**：已定的都写在 `OPEN-DESIGN.md` 附录（时间口径 300h、不做 PvP、占领后规则、
计划 4 用占位内容、允许魔改）。

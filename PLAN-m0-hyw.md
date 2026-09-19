# M0 验证方案：HYW 单位会不会**主动攻击玩家**

> 为什么单独开一份：M0 是 `NATIONS.md` §十四 的风险 1，**只挡计划 5（夺城）**，
> 而它必须开游戏（要真玩家当靶子）。它跟计划 4（建筑/村民/情报站）不是一回事，
> 却和"必须开游戏才能验的一批事"攒在同一次会话里做。
> 脚本已经写好：`tools/tests/m0_hyw.txt`。

---

## 一、先看静态证据：HYW 的配置已经把答案写了一半

`config/hundredyearswar/hyw_main.json5`（本机当前值）：

| 配置项 | 值 | 含义 |
|---|---|---|
| `nullOwnerUnitsAggressive` | **true** | 无主单位**主动攻击所有活物**，除了自然生物（牲畜/鱼/海豚/蝙蝠）和 Enemy 阵营怪 |
| `nullOwnerUnitsFriendlyToEnemy` | **false** | 无主单位**不**对 Enemy 阵营友好 |

而 `/summon` 刷出来的单位**就是无主的**（没有主人 UUID）。玩家既不是自然生物也不是 Enemy 阵营怪，
所以按配置推断：**它会攻击玩家**。

`target_lists/hostile_target_list.json5` 顶上那句注释是关键：
> Hostile target list - **only used when `nullOwnerUnitsFriendlyToEnemy` is true**

也就是说那两组"敌对目标名单"（默认只有村民和铁傀儡）**当前根本没生效**；
真正决定行为的是 `nullOwnerUnitsAggressive`。

**但这只是"按配置应当如此"，不是运行时证据** —— 所以还是要真机跑一次。
（这正是本项目的纪律：静态证据能定方向，不能代替验收。）

## 二、判据：什么才算"它攻击了玩家"

| 判据 | 为什么用它 | 为什么不用别的 |
|---|---|---|
| **玩家血量真的掉了** | scoreboard 的 `health` 准则 + `if score` 比较 → 输出是我们自己 `say` 的 ASCII 标记，**与客户端语言无关** | `/execute if entity` 只证明"刷出来了"，不证明"会打人" |
| **印记行落进日志** | `say` 会广播并写进服务端日志 → 哪怕 RCON 回显不可靠（§十四 风险 2）也读得到 | `Test passed` 是命令回显，跟随客户端语言（本机 zh_cn），英文断言必然不匹配 |

**阴性对照**（没它就是伪证据）：动手之前先读一次血量，**必须是满血**。
另外先设 `doMobSpawning false`、`kill @e[type=!player,distance=..64]`、`naturalRegeneration false`，
把野生怪和自然回血这两个"血量变化"的替代解释排除掉。

## 三、通道：全部走 RCON，**不走客户端按键**

2026-09-19 那次 0/3 假失败有两层原因：命令发在服务端起来之前（T19），以及**死亡屏吞掉所有按键**。
而玩家被 HYW 单位打，**很可能会死** —— 只要判据依赖客户端按键，这个测试就天生不可靠。

RCON 是另一条通道：玩家死不死都照样能查血量。所以 M0 跑在
**专用服务端 + 一个真客户端加入**的形态上（也就是 3~7 人的真实部署形态）：

```powershell
python server_ctl.py --setup --start
python game_agent.py --server 127.0.0.1:25565 --script tests\m0_hyw.txt
```

为了让脚本能表达"等它自己走过来"，给脚本格式加了 `wait: <秒>` 步骤
（原来只有 `cmd/rcon/expect/shot`，想等时间只能拿一串无关命令去烧，既慢又骗人）。

## 四、脚本干了什么（`tools/tests/m0_hyw.txt`，20 步）

1. **通道自检** `rcon: list` → 断言 `There are N of a max of M players online`（先证明 RCON 有回显）
2. **清干扰**：关野生刷怪/昼夜/天气/自然回血，`difficulty normal`，`gamemode survival`，清效果，清掉附近非玩家实体
3. **阴性对照**：读一次血量，断言 `has 20`（满血）+ 截图 `m0/m0-before.png`
4. **刷无主单位**：`execute at {{player}} run summon hundred_years_war:bandit_soldier ~ ~ ~5`
   并断言 `M0_SPAWN=YES`（**没有这一步，"没挨打"就无法区分"没刷出来"**）
5. `wait: 45` → 等 AI 选目标、走过来、动手；截图 `m0/m0-during.png`
6. **通道仍活着** `say M0_CHANNEL=ALIVE`（否则下一步的"无输出"无法区分"没挨打"和"RCON 挂了"）
7. **判据**：`execute if score {{player}} m0hp < 20 run say M0_RESULT=ATTACKS_PLAYER` → 断言该标记
8. 收尾：清掉刷出来的单位

**输出怎么读**：
- 出现 `M0_RESULT=ATTACKS_PLAYER` → **M0 通过**，夺城的前置成立；
- 只有 `M0_CHANNEL=ALIVE` 而没有 `M0_RESULT` → **它会刷出来但不会主动打玩家** ——
  这才是真正的坏消息（夺城得换机制），而脚本已经把"没刷出来"排除掉了。

## 五、如果结果是"不打玩家"，按这个顺序查

1. 抄一份 `hyw_main.json5` 做备份，把 `nullOwnerUnitsFriendlyToEnemy` 改成 `true` 再跑一次 ——
   那会打开 `hostile_target_list` 那条路径。**这是另一个可比对的实验，不是同一件事**，
   两次结果都要记进证据里。
2. 确认**服务端与客户端都有** HYW（服务端列表 `tools/lists/server-mods.txt` 里有它；两边 jar 都在）。
3. 难度/模式（脚本已固定 normal + survival，创造模式下怪不打人）。
4. 若仍不打：查 HYW 是否有"派系/阵营"概念需要先建立敌意（`hyw_global_modifier.json5` 还没细看）。

## 六、顺带记两笔对计划 5 有直接影响的项

- `enableSupplySystem: true` → **士兵需要补给，否则吃惩罚**。夺城的守军要是长期没人管，
  会不会因为缺补给自己变弱，得在夺城环节单独验一次。
- `despawnNullOwnerUnitsWhenFriendlyToEnemy: false`（且只在 FriendlyToEnemy 为 true 时才谈得上）→
  无主单位默认不会自己消失：对"临时守军"是好事，但也要注意别在世界里留一堆。

## 七、这件事没验成之前不要做的事

**不要开始计划 5（夺城）**。夺城的整个设计（旗座 + 守军 + 指挥官 + 百姓隐藏）都建立在
"HYW 单位能被刷出来、并且会按我们的意思作战"之上 —— 这条不成立，计划 5 就得改设计而不是改代码。

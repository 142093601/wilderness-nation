# 章节撰写说明书（task brief）· 2026-09-22

> 给**任何**要写一章任务书的人（包括我自己、以及被派来做这件事的 agent）看。
> 读完这份 + 抄一眼 `tools/questbook/chapters/130-cat-storage.toml`（成品样板），
> 就可以开写，不需要再问任何人。
>
> 结构决策：`PLAN-questbook.md` §二（40 章架构、形状语义）
> 章节清单与目标条数：`QUESTBOOK-ROSTER.md`（**单一真源，先看它**）
> 数据格式：`QUESTBOOK-FORMAT.md`
> 判据怎么选：`QUESTBOOK-CRITERIA.md`

---

## 0. 一句话

写 `tools/questbook/chapters/<key>.toml` **一个文件**。结构由 `tools/questbook.py` 编译成 SNBT，
正文由它生成到 `lang/zh_cn/chapters/<key>.snbt`。**你只写这一个 TOML 文件。**

---

## 1. 硬性不可违反（违反 = 白干）

1. **判据 id 必须真实存在**。写之前用 `tools/item_index.py` 查：
   ```powershell
   python tools/item_index.py --ns create --out tools/out/idx_create.txt   # 这个 mod 有什么
   python tools/item_index.py --grep 储罐                                    # 哪个 mod 有"储罐"
   ```
   查完的清单用 `read` 工具看（**不要**用控制台 cat 中文 —— 本机控制台是 GBK，中文会糊）。
   写完自检：
   ```powershell
   python tools/item_index.py --verify-toml     # 必须 missing 0
   python tools/lint_questbook_toml.py          # 必须 0 个解析失败
   python tools/questbook.py --check-only       # 只看不写，必须 exit 0
   ```
   ⚠️ **id 写错的后果不是报错，是那个任务玩家永远做不完**（游戏里不会提示）。

2. **`deps` 里只能写本章内的 `name`**。跨章依赖生成器会直接报错（跨章只用软链接，
   现在还没做，所以**不要跨章**）。第一条任务不写 `deps`。

3. **`name` 章内唯一，且是稳定英文短名**（`b_bridge`、`w_steel`）。
   **任务 id = `sha256("quest|<章key>|<name>")[:16]`，改 `name` 会让 id 变** ——
   所以 `name` 一旦定下来就别再改（id 变了玩家存档里的完成记录就丢了）。

4. **判据至少一条**。`item` 判据表达的是「**持有或消耗**」，**不是「放下」**。
   要表达"放下/建成"就用 `checkmark` 或 `advancement`/`stat`（见 §5）。

5. **不要写 `title`/`desc` 到结构里** —— 你写的 `title`/`desc` 会进语言文件，这是对的；
   但**不要**手写 `pack/config/ftbquests/` 下的任何文件（那是生成物）。

6. **不要在 `title` / `desc` / `subtitle` 的值里用英文双引号 `"`**。
   TOML 的双引号字符串不允许内嵌 `"`，写了整个文件就解析不了
   （`lint_questbook_toml.py` 会报 `FAIL`，但你已经白写一遍）。
   **中文引号用「」**：`desc = [ "它也是你要记住「回家的那个方块」。" ]`
   —— 2026-09-22 实测：`15-survival.toml` 一次踩了 12 行。

7. **不要跑 `python tools/questbook.py`（不带 `--check-only`）**。
   生成是全仓库一起做的，多人同时跑会互相覆盖。交给汇总的人。

---

## 2. 一章的骨架

```toml
# 类目 · <章名>
#
# 本章的 mod：<逐个数出来>
#
# 与其它章的分工：<一句：为什么这些内容不在别的章里>

key = "cat_xxx"          # 必须与文件名 <key>.toml 一致，且在 ROSTER 里登记过
title = "<章名>"
subtitle = "<一句话：这章怎么走>"      # ≤ 30 字
group = "category"       # chronicle / diplomacy / craft / category
icon = "<真实物品 id>"
order_index = 40         # 看 ROSTER 里的顺序，不要撞号

grid = { step = 1.5, row_h = 1.75 }   # 坐标是自动排的，照抄这行即可

# ---------------- 簇 1：<簇名> ----------------
[[quest]]
...
```

`x` / `y` **不用写**：生成器会按依赖关系自动分层排布（主轴一行行往下）。
只有在自动排布明显不合理时才显式写 `x`/`y`。

---

## 3. 密度与形状

| 形状 | size | 用在哪 |
|---|---|---|
| `pentagon` | 2.0 | **簇头**：一个 mod 家族的第一条 |
| `rsquare` | 1.75 | 簇内的**主工序**（链上的必经步骤） |
| `square` | 1.5 | 批量节点（"做够 N 个"） |
| `diamond` | 1.5 | 分叉点（从这里起有两条并行线） |
| `circle` | 1.2 | **支线 / 变体 / 单件**（默认，最多） |
| `octagon` | 1.75 | 章内里程碑（每 10 条左右一个） |
| `hexagon` | 2.5 | **章终局，一章最多一个，必须放最后** |
| `gear` | 1.75 | "这一步是自动化/产线" |

- **目标条数**：ROSTER 里每章都写了。**不许低于目标**。类目章是 26~40，技艺/编年章 20~90。
- **簇的划分**：一个 mod 家族 = 一个簇（注释行 `# ---- 簇 N：名字 ----` 隔开）。
  簇内 8~20 条；簇头 `pentagon`，簇内默认 `circle`，主链用 `rsquare`。
- **每条任务必须有 `icon`**（真实 id）。图标 = "这件事要做的那个东西"，扫一眼就该知道干嘛。
- **奖励**：`{ type = "xp", xp = N }` 配 `{ type = "toast" }` 只给**簇头与终局**；
  普通条目**不给奖励**（ATM10 也不是每条都给）。**绝不发成品物品**（发了就跳过玩法了）。

---

## 4. 文案（三条用户明说的红线）

用户对文字的要求是原话级别的，`tools/text_audit.py` 会机械检查前两条：

1. **不许「不知所云」**：每条 `title` 必须能回答"这是什么东西/要做什么"。
   ❌ `"踏上旅途"` `"新的开始"`（说了等于没说）
   ✅ `"第一块田"` `"给背包加水箱"`
2. **不许「口语化」**：不用"咱们/咱/整一个/搞起来/贼/挺"这类词。
   ✅ `"先把水源定下来"` ❌ `"咱先把水搞起来"`
3. **不许「中二」**：不堆"宿命/永恒/无敌/觉醒/毁灭/降临"。这是方志体，不是网文。

**描述 `desc` 的规矩**：
- **只有簇头、主轴节点、终局、以及"为什么这一步现在做"才写 desc**。
- 每条 `desc` **1~3 句，≤120 字**，内容是：**为什么现在做它 / 它解决什么麻烦 / 在流程里的位置**。
- **不写"怎么做"** —— 那是 JEI/EMI/Ponder/手册的活（`PLAN-questbook.md` §十二 明确禁止重复 mod 教学）。
- **类目章的大多数条目不写 desc**，只给 `title`（避免文案海洋，ATM10 也是这个做法）。

---

## 5. 判据怎么选（按优先级）

| 想表达 | 用什么 | 例子 |
|---|---|---|
| 做出/持有某物 | `item` | `{ type = "item", item = "create:cogwheel", count = 4 }` |
| 到达某维度 | `dimension` | `{ type = "dimension", dimension = "minecraft:the_nether" }` |
| 到过某群系 | `biome` | 见 `QUESTBOOK-CRITERIA.md`（id 要先用 item_index 查不到，去 jar 查） |
| 找到某结构 | `structure` | 同上 |
| 达成原版/模组进度 | `advancement` | `{ type = "advancement", advancement = "minecraft:story/mine_diamond" }` |
| 击杀 N 个 | `kill` | `{ type = "kill", entity = "minecraft:zombie", value = 30 }` |
| 累计统计达标 | `stat` | `{ type = "stat", stat = "minecraft:walk_one_cm", value = 50000 }` |
| **游戏里真的没有信号** | `checkmark` | 必须**逐条写明理由**（注释里写），全包越少越好 |

`advancement` 的 id **也要用 item_index 的 `--verify-toml` 覆盖不到**，
所以：只有**确定存在**的原版进度才用（`minecraft:story/*`、`minecraft:nether/*`、
`minecraft:end/*`、`minecraft:adventure/*`、`minecraft:husbandry/*`）。
模组进度要先确认（`tools/registry.json` 的 `advancements` 里有 10229 条，可以查）。

---

## 5.5 跨章软链接 `[[link]]`（**指路，不是前置**）

编年/技艺章里想把人指到**别的章**去做某件事时，用软链接，**不要**用跨章 `deps`
（生成器直接禁止跨章依赖）。

```toml
[[link]]
to_chapter = "create"      # 目标**章**的 key（看 ROSTER 或文件名）
to_quest = "first_crank"   # 目标章里那条任务的**稳定 name**（不是标题）
x = -12.0                  # 可选，缺省排在画布左侧
y = -1.75
```

- 生成物是章级的 `quest_links`，在游戏里表现为一个**独立小格**，点了能跳过去。
- **不构成解锁条件**：它不做，你这边照样继续。
- `to_quest` 写错**生成器会报错**（因为它必须解析成真实的任务 id）；
  目标 name 要自己去那一章的 TOML 里查（产物 SNBT 里只有 id，看不出 name）。
- 一条软链接只指一个任务；同一个目标章最多挂 1~2 条，别把画布铺满。

## 6. 覆盖纪律（类目章特有）
**类目章的存在意义 = 覆盖**。ROSTER 里列出的 mod，**每一个都必须在章里至少出现一条**。
写完对着 ROSTER 的 mod 清单逐个数一遍，并在提交说明里写"覆盖 N/N"。

**例外**（不该有任务）：纯前置库 mod —— Architectury / Balm / Cloth Config / Kotlin for Forge /
GeckoLib / Moonlight / Puzzles Lib / Resourceful Lib / Cristel Lib / DragonLib / Fzzy Config /
SuperMartijn642's Core & Config / YUNG's API / CreativeCore / Iceberg / Prism / Athena(算库) /
Framework / JamLib / Collective / CoroUtil / Bookshelf / ExtraLib / Mejer 等。
它们只有被别的 mod 依赖的作用，不该出现在任务书里。

---

## 7. 自检清单（写完必须逐条跑，把结果贴回来）

```powershell
cd D:\project\nation-pack
python tools/lint_questbook_toml.py            # 期望：0 个解析失败
python tools/item_index.py --verify-toml       # 期望：missing 0
python tools/runtime_deny.py --check           # 期望：命中 0（真机拒绝过的 id）
python tools/questbook_name_guard.py           # 期望：没有 name 消失/重复/空名
python tools/questbook.py --check-only         # 期望：exit 0，无 error
```

`questbook_name_guard.py` 是**扩写已有章时最重要的一条**：任务 id 是
`sha256("quest|<章 key>|<name>")`，name 一消失，语言键成孤儿、玩家进度回退，
而且**都不会报错**。它会把"相对 git HEAD 消失了的 name"直接列出来。

还要自己数一遍：
- [ ] 条数 ≥ ROSTER 里的目标
- [ ] 章内 `name` 无重复（生成器会报）
- [ ] `deps` 全部指向本章内的 `name`
- [ ] 每条任务都有 `icon` 且是真实 id
- [ ] `checkmark` 条数 = 0，除非注释里写了理由
- [ ] ROSTER 里列的 mod 全部覆盖
- [ ] 没有"怎么做"式的 desc（不重复 mod 教学）
- [ ] 文案里没有 咱们/咱/整一个/搞起来/贼/宿命/永恒/觉醒 这类词

---

## 8. 两个"看不见的"陷阱（2026-09-22 实测，各踩过一次）

1. **写文件不要用 PowerShell 的 `Out-File -Encoding UTF8`**（Windows PowerShell 5.1
   会加 **BOM**）。带 BOM 的候选 id 清单会被 `--verify-toml` 把**首行误报成 missing**
   （`minecraft:paper` 被冤枉过一次）。要用就 `strip_bom.py`，或者直接用 editor/write 工具。
2. **不要用裸 `str.replace` 批量改 id**。`create:package` 是 `create:packager` 的**前缀**，
   一次全局替换把 25 处好 id 改成了不存在的（`create:cardboard_package_12x12r`）。
   要替换就用 `tools/runtime_deny_fix.py`（它按整词边界匹配）。

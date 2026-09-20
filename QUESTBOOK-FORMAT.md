# 任务书数据格式规范（FTB Quests 2101.1.36 · MC 1.21.1 + NeoForge）

> **这份文件解决什么**：`tools/questbook.py` 与 `tools/questbook_check.py` 都要按同一套事实写。
> 这里每条都是**取证过的**——样本来自游戏自己写出的文件，不是网上抄的、也不是我的记忆。
> 写生成器/校验器之前先读它；改格式先改它。
>
> 取证日期：2026-09-20 · 取证脚本：`tools/_probe_*.py`（产出在 `tools/_probe_*.txt`，gitignore）

---

## 一、证据来源（每条结论都要能追到这里）

| # | 证据 | 位置 | 给出了什么 |
|---|---|---|---|
| E1 | **游戏自己写的玩家进度文件** | `…\saves\*\ftbquests\*.snbt`（5 个存档，各 270 字节） | SNBT 的真实写法：键值无逗号、Tab 缩进、`uuid` 去连字符、空复合写作 `{ }` |
| E2 | **三个真实整合包的游戏产出** | `versions\{All the Mods 10, Beebeeblock_TheSkyhive_v2.08_cf_release, 龙之冒险：新征程v2.3a}\config\ftbquests\quests\` | `data.snbt` / `chapter_groups.snbt` / `chapters/*.snbt` / 语言文件的**完整字段** |
| E3 | **FTB 官方 SNBT 规范** | [docs.feed-the-beast.com/…/SNBT](https://docs.feed-the-beast.com/mod-docs/next/mods/technical/SNBT/) | 多行结构**逗号可选**、支持 `#` 注释、数字后缀 `d/f/l/s/b`、键可无引号、值必须引号 |
| E4 | **jar 类表** | `ftb-quests-neoforge-2101.1.36.jar` 的 `quest/task/*.class`、`quest/reward/*.class` | 判据 / 奖励的**类清单** |
| E5 | **jar 里的 type 字符串常量** | 同上 jar 的 `TaskTypes.class` / `RewardTypes.class` 常量池 | `type: "…"` 里到底填什么 |
| E6 | **Lang Splitter 字节码** | `ftbquestslangsplitter-1.0.7.jar` | 每语言一个 `.snbt`、`.snbt_merged` 是回退产物 |

⚠️ **法律边界**：E2 里 ATM-10 的 quests 目录是 **All Rights Reserved**（其 README 明确禁止再分发）。
本仓库**公开** —— 所有产出**全部自写**，E2 只用来**确认字段名与文件结构**，一个字都不抄。

---

## 二、目录结构（我们要生成的形状）

```
pack/config/ftbquests/quests/          ← 仓库源（packwiz 装到实例 config/ftbquests/quests/）
├── data.snbt                  全局默认（表 §3.1）
├── chapter_groups.snbt        四栏分组（表 §3.2）
├── chapters/<章 key>.snbt     22 章结构（表 §4）
└── lang/
    ├── zh_cn.snbt             合并后的语言表（由游戏/Lang Splitter 生成，**我们不手写**）
    └── zh_cn/
        ├── chapter.snbt       章标题/副标题
        ├── chapter_group.snbt 分组标题
        ├── quest.snbt         （可选）任务标题/正文总表
        └── chapters/<章 key>.snbt  按章分文件（我们的正文落点）
```

- **`chapters/<章 key>.snbt`**：`<章 key>` 是**人写的稳定短名**（如 `landing`、`create`），
  而**章 id 是 16 位十六进制**（E2 实测：`5B00676D79306EA2`）。两者不同，靠文件内 `filename` 字段对齐。
  - ATM-10 两种都有：`welcome.snbt`（key = 语义名）与 `5712300EF4E5E846.snbt`（TheSkyhive 常用 id 当 key）。
  - **我们用语义 key**：可读、diff 友好、`filename` 字段写 key。
- **章与分组的关系**：章内 `group: "<分组 id>"`（空串 = 不归组）。

---

## 三、全局文件

### 3.1 `data.snbt`

E2 实测两个独立整合包**都是 `version: 13`** → **我们写 13**。

```
{
	default_autoclaim_rewards: "disabled"
	default_consume_items: false
	default_quest_disable_jei: false
	default_quest_shape: "circle"
	default_reward_team: false
	detection_delay: 60
	disable_gui: false
	drop_book_on_death: false
	drop_loot_crates: false
	emergency_items_cooldown: 300
	grid_scale: 0.5d
	icon: {
		id: "ftbquests:book"
	}
	lock_message: ""
	loot_crate_no_drop: {
		boss: 0
		monster: 600
		passive: 4000
	}
	pause_game: false
	progression_mode: "flexible"
	show_lock_icons: true
	version: 13
}
```

- `progression_mode: "flexible"` —— 与 `PLAN-questbook.md` §十.4 的默认值一致。
- `icon` 用 `{ id: "…" }` 对象形式；也见 E2 里的 `components` 变体（自定义贴图图标）。
- `default_quest_shape: "circle"`（ATM-10 同款；具体形状我们在每个任务上显式写）。

### 3.2 `chapter_groups.snbt`

```
{
	chapter_groups: [
		{ id: "…16 位大写十六进制…" }
		…
	]
}
```

**只有 `id`，标题在语言文件里**：`lang/zh_cn/chapter_group.snbt` 的 `chapter_group.<ID>.title`。

四栏（`PLAN-questbook.md` §2.1）：**编年 / 邦交 / 技艺 / 类目**（4 个分组）。

---

## 四、章文件 `<章 key>.snbt`（E2 实测字段）

```
{
	default_hide_dependency_lines: false      # 章级默认：是否隐藏依赖线
	default_quest_shape: ""                   # 章级默认任务形状（空 = 继承全局）
	filename: "<章 key>"                       # 必须与文件名一致
	group: "<分组 id 或空串>"
	icon: { id: "minecraft:…" }               # 也可直接写字符串 icon: "minecraft:…"
	id: "<16 位大写十六进制>"
	images: [ … ]                             # 可选；期 1 不做（PLAN §九 明说不做图片引导）
	order_index: 0                            # 左侧栏顺序
	progression_mode: "flexible"              # 可选（章级覆盖）
	quest_links: [ ]                          # 跨章软链接（§6.4）
	quests: [ … ]                             # 见 §5
}
```

- `icon` **两种写法都合法**：`icon: "minecraft:grass_block"`（字符串）或 `icon: { id: "…" }`（对象）。
  对象形式可带 `components`（1.21 组件化，如 `"ftbquests:icon"` 自定义贴图）。
- 章标题/副标题**不在这里**，在 `lang/zh_cn/chapter.snbt`。

---

## 五、任务文件 `<章 key>.snbt` 里的 `quests` 数组

### 5.1 常用字段（E2 全部实测出现过）

| 字段 | 类型 | 含义 | 备注 |
|---|---|---|---|
| `id` | string | **16 位大写十六进制** | 必填 |
| `x` `y` | double | 在章节画布上的位置 | 必填；**必须带 `d` 后缀** |
| `tasks` | list | 判据（**至少 1 条**） | §6.1 |
| `rewards` | list | 奖励（**可以没有**） | §6.2 |
| `dependencies` | list<string> | 前置任务 id（同章） | 无逗号分隔的多行数组 |
| `dependency_requirement` | string | `all_completed`（默认）/ `one_completed` | 实测出现过 `one_completed` |
| `shape` | string | `circle` / `rsquare` / `pentagon` / `hexagon` / `gear` / `diamond` … | 主轴用 `rsquare`/`pentagon`，终局用 `hexagon` |
| `size` | double | 尺寸（默认 1.0） | 带 `d` |
| `icon` | string 或 object | **任务图标** | 字符串或 `{ id: … }`；**这是最有效的引导手段** |
| `icon_scale` | double | 图标缩放 | |
| `title` | string | 任务标题 | **⚠️ 我们不用它**——见 §7 |
| `subtitle` | string | 副标题 | **⚠️ 我们不用它**——见 §7 |
| `description` | list<string> | 正文段落数组 | **⚠️ 我们不用它**——见 §7 |
| `optional` | bool | 可选任务（不计入完成度） | |
| `invisible` | bool | 不在书上显示（仅作逻辑中转） | 实测：ATM 用 `invisible + optional` 做隐藏中转格 |
| `min_width` | int | 正文面板最小宽度 | |
| `can_repeat` | bool | 可重复 | |
| `hide_details_until_startable` | bool | | |
| `hide_until_deps_visible` | bool | | |

### 5.2 坐标与网格

- E2 实测：同一章内任务坐标**成规律网格**（如 `0 / 1.8 / 3.15 / 4.5`，步长 1.5 或 1.35），
  `y` 常出现 `-0.49642857142856656d`、`1.7999999999999998d` 这种浮点残留 —— 那是**游戏内拖拽**产生的。
- **我们生成时写规整值**（`0.0d`、`1.5d`…），人读 dify 友好，也不会影响显示。

---

## 六、判据 / 奖励 / 链接

### 6.1 `tasks` —— 判据类型（E5 实测 **type 字符串**）

| type 字符串 | 类 | 关键字段（按需） |
|---|---|---|
| `item` | ItemTask | `item: { count: 1, id: "minecraft:…" }` 或 `item: "minecraft:…"`；可带 `components`；`consume_items: true`、`count: 8L` |
| `dimension` | DimensionTask | `dimension: "minecraft:overworld"` |
| `biome` | BiomeTask | `biome: "…"` |
| `structure` | StructureTask | `structure: "…"` |
| `advancement` | AdvancementTask | `advancement: "…"` |
| `observation` | ObservationTask | 看见某物/某实体 |
| `stat` | StatTask | `stat: "minecraft:walk_one_cm"`、`value` |
| `kill` | KillTask | `entity: "…"`、`value` |
| `location` | LocationTask | 到达坐标/范围 |
| `fluid` | FluidTask | `fluid: "…"` |
| `xp` | XPTask | `value` |
| `gamestage` | StageTask | **⚠️ 注意：不是 `stage`，是 `gamestage`**（E5 实测） |
| `custom` | CustomTask | mod 侧判定 |
| `checkmark` | CheckmarkTask | **无任何字段**（"自觉打勾"） |
| `energy` | ForgeEnergyTask（neoforge 子包） | **⚠️ 不在 `TaskTypes` 注册表常量里**，怀疑注册名不同；**期 1 不用它**，要用先单独核实 |

### 6.2 `rewards` —— 奖励类型（E5 实测 type 字符串）

| type 字符串 | 类 | 关键字段 |
|---|---|---|
| `item` | ItemReward | `item: { count: 1, id: "…" }`；`count`、`random_bonus` |
| `xp` | XPReward | `xp: 100` |
| `xp_levels` | XPLevelsReward | **⚠️ `xp_levels`，不是 `xplevels`** |
| `loot` | LootReward | `table_id: 1214128256591864354L`（**long，带 `L`**）；`exclude_from_claim_all` |
| `random` | RandomReward | |
| `choice` | ChoiceReward | |
| `all_table` | AllTableReward | **⚠️ `all_table`，不是 `alltable`** |
| `command` | CommandReward | 命令（**时代钥匙的技术前提**） |
| `toast` | ToastReward | 世界内叙事反馈 |
| `gamestage` | StageReward | **⚠️ 同上，`gamestage`** |
| `advancement` | AdvancementReward | |
| `currency` | CurrencyReward | |
| `custom` | CustomReward | |

### 6.3 奖励/任务内每个对象都要有 `id`

E2 实测：`tasks[]` 里每项有 `id`，`rewards[]` 里每项**也有 `id`**，都是 16 位大写十六进制。
→ 生成器要给**任务、判据、奖励**三级都发 id。

### 6.4 `quest_links` —— 跨章软链接（**不做硬前置**）

`quest_links: [ ]` 在**章**级别。跨章连线用软链接，避免"主线导向 mod 章"变成作业
（`PLAN-questbook.md` §4.4）。期 1 先留空数组占位，铺满时再填。

---

## 七、语言键（E2 实测格式）

| 键 | 值类型 | 例 |
|---|---|---|
| `chapter.<章ID>.title` | String | `chapter.5B00676D79306EA2.title: "欢迎"` |
| `chapter.<章ID>.chapter_subtitle` | **List\<String\>** | `chapter.0E81CBCD6B1D1895.chapter_subtitle: ["和其他刷怪笼"]` |
| `chapter_group.<分组ID>.title` | String | `chapter_group.2084F3F6FB861C5B.title: "主线任务"` |
| `file.<ID>.title` | String | `file.0000000000000001.title: "All The Mods 10"` |

**任务级键**（`PLAN-questbook.md` §五 定的键名）：
`quest.<任务ID>.title` · `quest.<任务ID>.subtitle` · `quest.<任务ID>.description`

- **类型区分要小心**：`chapter_subtitle` 是**列表**，`title` 是**字符串**。
  （新版本的 `subtitle` 在 E2 的 LongAdventure 里也有列表写法：`subtitle: ["&a请务必仔细看完。"]`）
- **正文与结构分离**：任务文件里**不写** `title`/`subtitle`/`description`，全部放 `lang/zh_cn/`。
  这是 Lang Splitter 的用法，也让用户改文案时**碰不到结构**（`PLAN-questbook.md` §五）。

---

## 八、写文件纪律（踩过的，别再犯）

1. **显式 `encoding="utf-8"`、`newline="\n"`**，且只改自己那几行（`INCIDENTS.md` 第二条）。
2. **不带 BOM**。
3. **缩进用 Tab**（游戏自己写的文件就是 Tab；E1 实测）。
4. 生成器产出**必须可复现**：同一个输入跑两次，字节一致（id 由稳定名字哈希得来）。

---

## 九、还没核实、期 1 不碰的

| 项 | 状态 | 什么时候处理 |
|---|---|---|
| `energy` 判据的 type 字符串 | ⚠️ 不在 TaskTypes 常量表里 | 真要用之前单独核实（期 1 不用） |
| `images`（章节大图引导） | 字段名已知，未实测效果 | 期 3（要客户端截图） |
| `quest_links` 跨章链接的实际渲染 | 字段名已知，未实测 | 铺满 17 章时 |
| `reward_tables/*.snbt`（loot 表） | E2 里有，字段未细看 | 用到 `loot` 奖励时 |
| `stage` 隐藏任务 | `PLAN` §十.3 默认**不用** | 不改 |
| `data.snbt` 里 1.21.1 是否接受 `verify_on_load` 等新字段 | 未核实 | 生成时**只写上面 §3.1 那批**，不用未验证字段 |

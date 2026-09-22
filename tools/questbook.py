#!/usr/bin/env python3
"""questbook.py -- 把「人写的任务定义」编译成 FTB Quests 的 SNBT 数据。

它解决什么
----------
手写 SNBT 是错的：14 个字段、三级 id（任务/判据/奖励）、坐标要用浮点、语言要与结构分离。
人只应该写**意图**（这一章有哪些任务、判据是什么、连在谁后面），其余由本工具生成。

用法
----
    python tools/questbook.py                 # 生成（保护已有 lang 正文档）
    python tools/questbook.py --force-lang     # 连 lang 正文档也重写（危险：会盖掉用户的碑文）
    python tools/questbook.py --check-only     # 只报告将要发生什么，不写盘

输入
----
    tools/questbook/groups.toml     四栏分组
    tools/questbook/data.toml       全局默认（可选，缺省用内置模板）
    tools/questbook/chapters/*.toml 每章一个文件（见 QUESTBOOK-FORMAT.md 与下方「TOML 语法」）

输出（全部进 packwiz 源目录，由 pack index.toml 分发）
----
    pack/config/ftbquests/quests/data.snbt
    pack/config/ftbquests/quests/chapter_groups.snbt
    pack/config/ftbquests/quests/chapters/<key>.snbt
    pack/config/ftbquests/quests/lang/zh_cn/chapter.snbt       （章标题，生成）
    pack/config/ftbquests/quests/lang/zh_cn/chapter_group.snbt （分组标题，生成）
    pack/config/ftbquests/quests/lang/zh_cn/chapters/<key>.snbt（任务正文，**生成骨架后归用户**）

核心纪律
--------
1. **id 可复现**：由「章 key + 稳定名字」哈希成 16 位大写十六进制（sha256 取前 16）。
   同一个输入跑两次，输出字节一致；改一处不会让别处 id 漂移。
2. **正文与结构分离**：任务文件里**不写** title/subtitle/description，全在 lang/ 下。
3. **lang 归用户**：已存在的 lang 文件默认**不覆盖**，只补缺失的键。用户改的是字，不是结构。
4. **不猜**：判据/奖励的 type 字符串来自 QUESTBOOK-FORMAT.md（jar 实测），不受理未登记的类型。

TOML 语法（一章一个文件）
------------------------
    key         = "landing"          # 稳定短名 → 也是文件名
    title       = "落地"
    group       = "chronicle"        # groups.toml 里的 key
    icon        = "minecraft:campfire"
    order_index = 1

    [[quest]]
    name = "first_camp"              # 稳定名（章内唯一）→ 参与 id 哈希
    title = "第一间避难所"             # 文案（生成 lang 骨架用）
    desc = [ "为什么做…", "怎么做…" ]    # 可选，主线才写
    icon = "minecraft:red_bed"
    shape = "rsquare"                # circle/rsquare/pentagon/hexagon/gear/diamond…
    size = 1.5
    x = 0.0                          # 省略则自动排布
    y = 0.0
    deps = ["prev_name"]             # 前置（写章内的 name，不是 id）
    optional = false

      [[quest.tasks]]                # 判据（至少一条）
      type = "item"
      item = "minecraft:red_bed"

      [[quest.tasks]]
      type = "checkmark"

      [[quest.rewards]]              # 奖励（可为零条）
      type = "xp"
      xp = 10
"""

from __future__ import annotations

import argparse
import hashlib
import math
import re
import sys
import tomllib
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
QBOOK_DIR = HERE / "questbook"
CHAPTERS_SRC = QBOOK_DIR / "chapters"
OUT_QUESTS = ROOT / "pack" / "config" / "ftbquests" / "quests"
OUT_LANG = OUT_QUESTS / "lang" / "zh_cn"

# ---- 白名单：来自 QUESTBOOK-FORMAT.md §6（jar 常量池实测）----
TASK_TYPES = {
    "item", "dimension", "biome", "structure", "advancement", "observation",
    "stat", "kill", "location", "fluid", "xp", "gamestage", "custom", "checkmark",
}
REWARD_TYPES = {
    "item", "xp", "xp_levels", "loot", "random", "choice", "all_table",
    "command", "toast", "gamestage", "advancement", "currency", "custom",
}
SHAPES = {
    "circle", "rsquare", "pentagon", "hexagon", "gear", "diamond",
    "square", "rounded_square", "octagon", "heart",
}
# 判据里"必须由人提供"的字段（缺了会写出游戏读不懂的数据）
TASK_REQUIRED_FIELDS = {
    "item": ["item"],
    "dimension": ["dimension"],
    "biome": ["biome"],
    "structure": ["structure"],
    "advancement": ["advancement"],
    "stat": ["stat"],
    "kill": ["entity"],
    "fluid": ["fluid"],
    "observation": ["observation"],
    "location": [],
    "xp": [],
    "gamestage": ["stage"],
    "custom": [],
    "checkmark": [],
}

# 奖励类型的字段默认值（写 SNBT 时补齐）
#
# **为什么需要这个**：FTB Quests 的 `CommandReward` 默认 `permissionLevel = 0`
# （javap 实测从字节码常量里读到：构造器 `iconst_0` → putfield permissionLevel）。
# 而我们的 `/statecraft accelerate` 要求 `hasPermission(2)`。
# 不显式提升权限，任务书发的命令**会被拒绝执行、而且不会报错** ——
# 表现就是"做完主线拿到钥匙，但什么也没发生"。
#
# 所以：凡是 `command` 奖励，一律补 `permission_level = 2`（需要 op 才能被领奖者触发）。
# 放在生成器里而不是逐条写进 TOML，是为了以后新增 command 奖励时不会再漏。
REWARD_EXTRA_DEFAULTS: dict[str, dict] = {
    "command": {"permission_level": 2},
}

DATA_SNBT_TEMPLATE = """{{
\tdefault_autoclaim_rewards: "disabled"
\tdefault_consume_items: false
\tdefault_quest_disable_jei: false
\tdefault_quest_shape: "circle"
\tdefault_reward_team: false
\tdetection_delay: {detection_delay}
\tdisable_gui: false
\tdrop_book_on_death: false
\tdrop_loot_crates: false
\temergency_items_cooldown: 300
\tgrid_scale: 0.5d
\ticon: {{
\t\tid: "ftbquests:book"
\t}}
\tlock_message: ""
\tloot_crate_no_drop: {{
\t\tboss: 0
\t\tmonster: 600
\t\tpassive: 4000
\t}}
\tpause_game: false
\tprogression_mode: "{progression_mode}"
\tshow_lock_icons: true
\tversion: 13
}}
"""


# ======================================================================
# SNBT 序列化
# ======================================================================

class Long(int):
    """LongTag：序列化成 123L。用于 loot 的 table_id、item task 的 count。"""


def _snbt_string(s: str) -> str:
    """字符串字面量。优先双引号；含双引号时改用单引号（FTB SNBT 支持单引号）。"""
    s = str(s)
    if '"' in s and "'" not in s:
        return "'" + s + "'"
    if '"' in s and "'" in s:
        # 两种引号都有：退回双引号并转义（vanilla SNBT 支持 \" ）
        return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'
    return '"' + s.replace("\\", "\\\\") + '"'


def _scalar(v) -> str:
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, Long):
        return f"{int(v)}L"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, float):
        # 浮点一律带 d 后缀 —— 与游戏自己写出的文件一致
        t = repr(v)
        if t.endswith(".0"):
            t = t[:-2] + ".0"
        return f"{t}d"
    if isinstance(v, str):
        return _snbt_string(v)
    raise TypeError(f"SNBT 不支持的类型：{type(v).__name__}（值={v!r}）")


def snbt(value, indent: int = 0) -> str:
    """把 Python 结构写成 FTB 风格的 SNBT 文本。

    规则（证据：游戏自己写出的 world/ftbquests/*.snbt 与参考包 chapters/*.snbt）：
      - 复合/列表渲染成多行，**元素之间不写逗号**
      - 缩进用 Tab；列表元素的起始列与 `[` 对齐（游戏自己写的文件就是这样）
      - 浮点带 d 后缀、long 带 L 后缀、bool 裸写、字符串带引号
      - 空列表写 "[ ]"
    """
    pad = "\t" * indent
    pad_in = "\t" * (indent + 1)

    if isinstance(value, dict):
        if not value:
            return "{ }"
        lines = ["{"]
        for k, v in value.items():
            lines.append(f"{pad_in}{k}: {snbt(v, indent + 1)}")
        lines.append(pad + "}")
        return "\n".join(lines)

    if isinstance(value, (list, tuple)):
        if not value:
            return "[ ]"
        lines = ["["]
        for item in value:
            # 元素与 `[` 同列（游戏自己写的文件就是这样，见 QUESTBOOK-FORMAT.md §5.2）
            lines.append(pad + snbt(item, indent))
        lines.append(pad + "]")
        return "\n".join(lines)

    return _scalar(value)


# ======================================================================
# id 生成（可复现 + 唯一）
# ======================================================================

class IdAllocator:
    """由稳定名字派生 16 位大写十六进制 id —— **纯函数，结果与调用顺序无关**。

    ⚠️ **为什么不能有任何"冲突加盐"兜底**（2026-09-22 事故，两条一起犯的）：

    旧实现：`while h in self.used: salt += 1; 重新哈希`，且 `self.used` **跨章共享**。
    它制造了两个独立的破坏：

      1. **插入任务会全局位移 id**：在 create 章中间插入 53 个任务后，
         该章后面所有任务的 id 全变，`lang/` 里 355 个文案键变成孤儿、3 条依赖悬空。
      2. **前向引用会拿到两个不同 id**：`compile_chapter` 遇到"依赖先出现、任务后出现"
         时并地算一次 id（`id_map.setdefault`），但轮到该任务自己时**又调了一次 `make`**；
         两次之间 `used` 集合已变，加盐兜底给出**不同**结果 ——
         `brass` / `mechanical_arm` 就是这样变成悬空依赖的。

    ⇒ 结论：**任何"看情况加盐"的逻辑都会让 id 依赖调用顺序**，而 id 必须在
    "加任务 / 改顺序 / 前向引用"三种情况下都不变。

    现在的实现：id = sha256(片段用 `|` 连接) 的前 16 位，**没有兜底、没有状态**。
    撞车概率：64 位截断，本项目 ~400 个 id 时约 1e-15（生日界），实际不可能发生。
    真撞了就 `raise` —— 那时候应该换更长截断或改名字，而不是悄悄加盐。
    """

    def __init__(self) -> None:
        self.seen: dict[str, str] = {}   # id -> 生成它的 base（仅用于撞车时报错）

    def make(self, *parts: str) -> str:
        base = "|".join(parts)
        h = hashlib.sha256(base.encode("utf-8")).hexdigest()[:16].upper()
        prev = self.seen.get(h)
        if prev is not None and prev != base:
            raise RuntimeError(
                f"id 撞车：{h}\n  已有：{prev}\n  冲突：{base}\n"
                f"（64 位截断撞车极罕见；请改任务名或改用更长截断，不要加盐）")
        self.seen[h] = base
        return h


# ======================================================================
# 定义读取
# ======================================================================

class ChapterDef:
    def __init__(self, path: Path, raw: dict):
        self.path = path
        self.key: str = raw["key"]
        self.title: str = raw.get("title", self.key)
        self.subtitle: str | None = raw.get("subtitle")
        self.group: str = raw.get("group", "")
        self.icon: str = raw.get("icon", "ftbquests:book")
        self.order_index: int = int(raw.get("order_index", 0))
        self.progression_mode: str | None = raw.get("progression_mode")
        self.quests: list[dict] = raw.get("quest", [])
        # 跨章**软链接**（`[[link]]`）：在本书的画布上放一个指路牌，
        # 指向另一章的某条任务。**不是前置** —— 不做也能继续，
        # 这正是 PLAN-questbook.md §4.4 要的："主线导向 mod 章，但不变成作业"。
        self.links: list[dict] = raw.get("link", []) or []
        # 坐标排布参数（章级可覆盖）
        self.grid: dict = raw.get("grid", {})
        # 原始文本：**排版要用它读簇注释**（`# ---- 簇 N：名字 ----`）。
        # 为什么从注释读而不是加 TOML 字段：42 个章文件已经写好了这套注释，
        # 而"加一个 quest 级字段"要动全部文件、还会让已有的 name/id 逻辑多一层。
        # 注释是**本来就有的结构信息**，直接读它最省且不会漂。
        try:
            self.text: str = path.read_text(encoding="utf-8")
        except Exception:  # noqa: BLE001
            self.text = ""


def load_groups() -> list[dict]:
    f = QBOOK_DIR / "groups.toml"
    if not f.is_file():
        return []
    with f.open("rb") as fh:
        raw = tomllib.load(fh)
    return raw.get("group", [])


def load_data_opts() -> dict:
    f = QBOOK_DIR / "data.toml"
    opts = {"progression_mode": "flexible", "detection_delay": 60}
    if f.is_file():
        with f.open("rb") as fh:
            raw = tomllib.load(fh)
        opts.update({k: v for k, v in raw.items() if not isinstance(v, dict)})
    return opts


def load_chapters() -> list[ChapterDef]:
    if not CHAPTERS_SRC.is_dir():
        return []
    out: list[ChapterDef] = []
    for p in sorted(CHAPTERS_SRC.glob("*.toml")):
        with p.open("rb") as fh:
            raw = tomllib.load(fh)
        if "key" not in raw:
            raise SystemExit(f"{p.name}: 缺少 key 字段")
        out.append(ChapterDef(p, raw))
    return out


# ======================================================================
# 布局：人写意图，工具算坐标
# ======================================================================

def dep_cycles(quests: list[dict]) -> list[list[str]]:
    """找出**章内**依赖里的环，返回若干条环路（每个是 name 的列表，首尾相接）。

    为什么要有这个函数（2026-09-22 实测踩到）
    ----------------------------------------
    旧的 `layout()` 用裸递归算行号，**没有环检测**。八个 agent 并行写章时，
    其中一章写出了 `a → b → a`，结果不是"报错"，而是生成器整个
    `RecursionError: maximum recursion depth exceeded` 崩掉 ——
    错误信息里连是哪个文件都看不出来，只能一个个文件试。
    依赖成环是**必然会发生**的输入错误（人写的），生成器必须能指出它在哪。
    """
    names = {q["name"] for q in quests}
    local = {q["name"]: [d for d in (q.get("deps") or []) if d in names] for q in quests}
    state: dict[str, int] = {}          # 0/absent=未访问 1=在栈上 2=已完成
    found: list[list[str]] = []

    def dfs(node: str, stack: list[str]) -> None:
        state[node] = 1
        for dep in local.get(node, []):
            if state.get(dep) == 1:
                if dep in stack:
                    found.append(stack[stack.index(dep):] + [dep])
                else:
                    found.append([node, dep])
            elif state.get(dep) is None:
                dfs(dep, stack + [dep])
        state[node] = 2

    for n in local:
        if state.get(n) is None:
            dfs(n, [n])
    return found


def cluster_of(chapter: "ChapterDef") -> dict[str, int]:
    """从章文件的注释里读「每条任务属于哪个簇」。

    约定（作者一直在用）：`# ---- 簇 3：包裹与地址 ----` 之后的任务属于簇 3。
    没有簇注释的章 → 全章算**一个簇**（退化成"整章一棵树"，不会乱）。

    为什么排版要用簇而不是全局深度（2026-09-22 实测）
    ------------------------------------------------
    旧版把**全章同深度的任务排进同一行**。实测后果：
      · `100-cat-building` 56 条 → 65.0 × 7.0（横向长条），最宽一行 25 个
      · `55-defense-era` 51 条 → 66.4 × 10.5
      · `75-steam` 95 条 → **342 条依赖线交叉**（图变成一团毛线）
    而这些章的依赖**78% 都在簇内** —— 也就是说"同深度"里混着好几个互不相干的
    簇的成员，排成一行等于**排版在撒谎**：它告诉玩家"这 25 件事是并列的"，
    实际它们分属 5 个不同的主题。
    """
    out: dict[str, int] = {}
    cur = 0
    for ln in (chapter.text or "").split("\n"):
        m = re.match(r"^#\s*-+\s*簇\s*(\d+)", ln)
        if m:
            cur = int(m.group(1))
            continue
        m2 = re.match(r'^name = "([^"]+)"$', ln)
        if m2:
            out[m2.group(1)] = cur
    return out


def layout(chapter: ChapterDef) -> dict[str, tuple[float, float]]:
    """排布坐标：**整齐树（tidy tree）**，零交叉是结构性保证。

    为什么最终是这个算法（2026-09-22，绕了三版才定）
    ----------------------------------------------
    我先后试过：①全局分层（改前）②按簇分块 + 蛇形折列 + 网格装箱。
    前者的产物是一根 65 宽的横带、最宽一行 25 个；后者把交叉线搞到 375 条。
    两个都是在**没量参照物**的情况下调的。

    量了参照物之后结论完全不同（`tools/_probe_ref_crossings.py`）：

      | 包 | 章数 | 中位 宽×高 | 面积 | **依赖线交叉** |
      |---|---|---|---|---|
      | ATM10 | 61 | 18.5 × 15.0 | 240 | **0** |
      | 天空蜂巢 | 31 | 19.5 × 13.4 | 255 | **0** |

    253 条的 `productive_bees` 只用 21 × 29.5 且**一条交叉都没有**。
    能做到零交叉只有一个原因：**它们是整齐树** ——
      · 每个子树占一条**连续的横向带**（兄弟子树绝不交错）
      · 父节点 x = 子节点的中点
    这两条一成立，"两条边相交"在几何上就不可能发生。

    同时参照物给出了**间距公式**：ATM10 的邻接中心距是 0.5，而它的
    `size` 是 1.0、`grid_scale` 是 0.5 —— 正好等于 `(s₁+s₂)/2 × grid_scale`，
    也就是"两个图标刚刚好不重叠"。本包之前用固定步长 1.5~3.75，
    **比需要的松 3~5 倍**，这才是画布铺得开的真正原因（不是内容多）。
    """
    # ⚠️ `gap` 是**唯一**决定"看起来挤不挤"的参数（2026-09-22 用户实测反馈后改正）。
    #
    # 为什么只调它、不调 unit：
    #   中心距 = (s₁+s₂)/2 × unit + gap      节点视觉直径 = s × k（k 是 FTB 内部常数）
    #   相对空隙 = gap / (s × k) —— **与 unit 无关**。
    #   unit 只影响"整章占多大画布"（玩家要缩放多少），gap 才影响手感。
    #   所以我原来把两者一起按参照包反推是错的：ATM10 每章面积小，
    #   不等于它的**视觉空隙**也小。
    #
    # 取值依据是用户的直接观察："基本所有章节排版都太过于紧密"。
    # 旧值 0.18 对 size 1.2 的节点只有 **15% 图标宽**的净空隙（几乎贴在一起）。
    # 改成 0.75 ≈ **62% 图标宽**：两个图标之间看得清依赖线，不再糊成一团。
    # 用户明确说"游戏里可以放大缩小，没必要这么紧密"——那就给足空气，
    # 画布变大不成问题（缩一下就能看全）。
    g = {"gap": 0.75, "unit": 0.5, "origin_x": 0.0, "origin_y": 0.0}
    g.update(chapter.grid)
    gap = float(g["gap"])
    unit = float(g["unit"])          # = data.snbt 的 grid_scale

    quests = chapter.quests
    names = [q["name"] for q in quests]
    if not names:
        return {}
    by = {q["name"]: q for q in quests}
    idx = {n: i for i, n in enumerate(names)}
    size = {n: float(by[n].get("size", 1.5)) for n in names}
    w = {n: size[n] * unit for n in names}         # 图标在坐标空间里的实际宽度
    local = {n: [d for d in (by[n].get("deps") or []) if d in by] for n in names}

    # ---- 深度（迭代式不动点，环不会崩）----
    depth: dict[str, int] = {}
    for _ in range(len(names) + 2):
        progressed = False
        for n in names:
            if n in depth:
                continue
            ds = local[n]
            if all(d in depth for d in ds):
                depth[n] = 0 if not ds else 1 + max(depth[d] for d in ds)
                progressed = True
        if not progressed:
            break
    for n in names:
        depth.setdefault(n, 0)

    # ---- 主父节点：多前置的节点（DAG）挂在**最深的前置**下面 ----
    #
    # 为什么是"最深"而不是"第一个"（2026-09-22 实测：换成最深之后
    # 60-create 的交叉从 219 降到 85、45-expedition 从 198 降到 125）：
    #   · 挂"第一个前置"会把节点拉到树上很高的位置，
    #     它到**其它**前置的连线就要横跨大半个画布 → 一路穿线。
    #   · 挂"最深的前置"= 语义上"最后一个前提做完就能做它"，
    #     节点落在它实际可做的位置附近，多余的依赖线都变短。
    parent: dict[str, str | None] = {}
    for n in names:
        ds = local[n]
        parent[n] = max(ds, key=lambda d: (depth[d], -idx[d])) if ds else None
    kids: dict[str, list[str]] = {n: [] for n in names}
    for n in names:
        p = parent[n]
        if p:
            kids[p].append(n)

    # ---- 叶子按 **DFS 序**排槽 ----
    #
    # ⚠️ 这里是最关键的一步，写错了整个算法就白做（2026-09-22 实测：
    # 第一版按 TOML 顺序给叶子排槽，结果 75-steam 交叉线 396 条 —— 比改前还差）。
    # 原因：**整齐树零交叉的前提是"每个子树的叶子在横向上连续"**。
    # 按 TOML 顺序排槽，两个兄弟子树的叶子会交错（A1 B1 A2 B2），
    # 于是 A 的连线必然穿过 B —— 几何上就注定了。
    # 按 DFS 序排槽（A1 A2 B1 B2）则每个子树占一条独立的带，不可能相交。
    roots = [n for n in names if parent[n] is None]
    dfs_order: list[str] = []
    root_of: dict[str, str] = {}
    seen: set[str] = set()

    def walk(n: str, r: str) -> None:
        if n in seen:
            return
        seen.add(n)
        dfs_order.append(n)
        root_of[n] = r
        for k in kids[n]:
            walk(k, r)

    for r in roots:
        walk(r, r)
    for n in names:                      # 环里的节点兜底
        walk(n, root_of.get(n, n))

    # ---- 分组：**按五边形簇头**（这是作者标出来的分组信号，见 PLAN §2.5）----
    #
    # 为什么不用"根"来分组（第一版就是这么写的，结果毫无效果）：
    # 实测每章平均只有 **1.5 个根**（1776 条里只有 60 条没有依赖）——
    # 因为簇与簇之间本来就有依赖把它们连成一棵树。所以"按根分组"= 全章一组。
    # 作者真正用来标"这里开始是新的一类事"的是 **pentagon 形状的簇头**，
    # 而 `PLAN-questbook.md` §2.5 早就把这条语义定死了，只是排版没用上它。
    # 现在就用：每个节点归到"它最近的 pentagon 祖先"那一组，组间额外留白。
    shape = {n: str(by[n].get("shape", "circle")) for n in names}

    def group_head(n: str) -> str:
        cur: str | None = n
        guard = 0
        while cur is not None and guard < 10000:
            if shape[cur] == "pentagon" or parent[cur] is None:
                return cur
            cur = parent[cur]
            guard += 1
        return n

    group_of = {n: group_head(n) for n in names}

    # ---- 叶子按 DFS 序排槽，**并在"组与组之间"额外留白** ----
    #
    # 整齐树只保证"子树不交错"，两组之间原来只隔一个普通间距 →
    # 整章看起来是"一片平铺的图标"，看不出分组（用户看游戏截图后的反馈）。
    # 用一个明显更大的空档表达"换了一类事"，玩家一眼就知道分界在哪。
    # 这不影响零交叉（各组仍各自连续），只是把带与带拉开。
    group_gap = float(g.get("group_gap", 1.6))
    x: dict[str, float] = {}
    cursor = 0.0
    prev_group: str | None = None
    for n in dfs_order:
        if not kids[n]:                            # 叶子
            if prev_group is not None and group_of[n] != prev_group:
                cursor += group_gap                # ← 组与组之间的留白
            prev_group = group_of[n]
            x[n] = cursor + w[n] / 2.0
            cursor += w[n] + gap
    # 后序：先算完子节点再算父节点。深度从大到小就是合法的后序。
    for d in sorted({depth[n] for n in names}, reverse=True):
        for n in names:
            if depth[n] != d or not kids[n]:
                continue
            ks = kids[n]
            x[n] = (min(x[k] for k in ks) + max(x[k] for k in ks)) / 2.0
    width = cursor

    # ---- 同层消重叠：子树之间不能挤在一起（这一步保证"带"互不交错）----
    for _ in range(3):
        for d in sorted({depth[n] for n in names}):
            row = [n for n in names if depth[n] == d]
            row.sort(key=lambda n: x[n])
            for a, b in zip(row, row[1:]):
                need = (w[a] + w[b]) / 2.0 + gap
                if x[b] - x[a] < need:
                    shift = need - (x[b] - x[a])
                    x[b] += shift
                    for m in names:                # 带动整棵子树一起挪
                        if depth[m] > d and _is_desc(m, b, parent):
                            x[m] += shift
        # 重新居中父节点
        for d in sorted({depth[n] for n in names}, reverse=True):
            for n in names:
                if depth[n] == d and kids[n]:
                    ks = kids[n]
                    x[n] = (min(x[k] for k in ks) + max(x[k] for k in ks)) / 2.0

    # ---- y 按层累加（每层高度取该层最大图标）----
    y: dict[str, float] = {}
    cur = float(g["origin_y"])
    for d in sorted({depth[n] for n in names}):
        row = [n for n in names if depth[n] == d]
        for n in row:
            y[n] = round(cur, 2)
        cur -= max(w[m] for m in row) + gap

    # ---- 【2026-09-22 撤掉折行】----
    #
    # 试过"按组折行把画布压回方形"，**画出来一看更糟**：
    # 折行会在中间留下大片空洞，还把一个小簇孤立到整张图的最下面
    # （tools/out/preview/42-materials.png 能直接看到）。
    # 原因：带宽上限是按"组的宽度"判断的，一个 60 条的组本身就比上限宽，
    # 折不动；于是只有末尾那点小组被推到下一带 —— 得到"方但有洞"，
    # 而"宽但连续、分组清楚"明显更好读（用户能缩放，宽不是问题）。
    #
    # **教训**：这类几何决策必须画出来看（tools/questbook_layout_preview.py），
    # 只看"宽高比"这样的标量会得出相反的结论。
    lo_x = min(x[n] - w[n] / 2.0 for n in names)
    ox = float(g["origin_x"]) - lo_x
    return {n: (round(x[n] + ox, 2), y[n]) for n in names}


def _is_desc(node: str, root: str, parent: dict[str, str | None]) -> bool:
    """node 是不是 root 的后代（沿主父链上溯，带环保护）。

    消重叠时要"带动整棵子树一起挪"，就需要这个判断。
    """
    seen = 0
    cur = parent.get(node)
    while cur is not None and seen < 10000:
        if cur == root:
            return True
        cur = parent.get(cur)
        seen += 1
    return False


class Compiler:
    def __init__(self) -> None:
        self.ids = IdAllocator()
        self.errors: list[str] = []
        self.warnings: list[str] = []
        self.chapter_ids: dict[str, str] = {}
        self.group_ids: dict[str, str] = {}
        self.quest_ids: dict[str, dict[str, str]] = {}
        # 每章的原始 node（软链接第二遍解析时要改它）
        self.chapter_nodes: dict[str, dict] = {}
        self.group_ids: dict[str, str] = {}
        self.chapter_ids: dict[str, str] = {}
        self.quest_ids: dict[str, dict[str, str]] = {}   # chapter_key -> name -> id

    def err(self, where: str, msg: str) -> None:
        self.errors.append(f"{where}: {msg}")

    def warn(self, where: str, msg: str) -> None:
        self.warnings.append(f"{where}: {msg}")

    # ---- 分组 ----
    def compile_groups(self, groups: list[dict]) -> str:
        entries = []
        for g in groups:
            key = g["key"]
            gid = self.ids.make("group", key)
            self.group_ids[key] = gid
            entries.append({"id": gid})
        return snbt({"chapter_groups": entries}) + "\n", groups

    # ---- 章 ----
    def compile_chapter(self, ch: ChapterDef) -> tuple[str, list[dict]]:
        where = f"{ch.path.name}"
        if ch.key in self.chapter_ids:
            self.err(where, f"章 key 重复：{ch.key}")
        cid = self.ids.make("chapter", ch.key)
        self.chapter_ids[ch.key] = cid

        if ch.group and ch.group not in self.group_ids:
            self.err(where, f"group='{ch.group}' 在 groups.toml 里不存在")
        for shape_key in ("default_quest_shape",):
            pass

        names = [q.get("name") for q in ch.quests]
        if len(set(names)) != len(names):
            dupes = sorted({n for n in names if names.count(n) > 1})
            self.err(where, f"任务 name 重复：{dupes}")
        name_set = set(names)

        # 依赖成环 = 那几条任务**永远不可能解锁**（游戏里表现为永远暗着）。
        # 必须在生成阶段报出来 —— 见 dep_cycles() 的注释（旧版是 RecursionError 崩掉）。
        for cyc in dep_cycles(ch.quests):
            self.err(where, "依赖成环（这几条任务永远解不开）：" + " → ".join(cyc))

        positions = layout(ch)

        compiled_quests: list[dict] = []
        id_map: dict[str, str] = {}
        for q in ch.quests:
            name = q["name"]
            qid = self.ids.make("quest", ch.key, name)
            id_map[name] = qid

            def qerr(m: str) -> None:
                self.err(where, f"[{name}] {m}")

            # 依赖：TOML 里写的是章内稳定名，**必须换成任务 id** 才能进数据。
            deps = q.get("deps", []) or []
            dep_ids: list[str] = []
            for d in deps:
                if d not in name_set:
                    qerr(f"依赖 '{d}' 不在本章内（跨章请用 quest_links，不做硬前置）")
                    continue
                # id_map 里已有（按 TOML 顺序，前置一定排在后面之前）；
                # 若因排序异常还没算出来，就地按同一套哈希规则补算。
                did = id_map.get(d) or self.ids.make("quest", ch.key, d)
                id_map.setdefault(d, did)
                dep_ids.append(did)

            # 判据
            tasks_raw = q.get("tasks", []) or []
            if not tasks_raw:
                qerr("没有任何判据（tasks 至少一条）")
            tasks: list[dict] = []
            for i, t in enumerate(tasks_raw):
                ttype = t.get("type")
                if ttype not in TASK_TYPES:
                    qerr(f"判据 #{i + 1} 的 type='{ttype}' 不在白名单（见 QUESTBOOK-FORMAT.md §6.1）")
                    continue
                for req in TASK_REQUIRED_FIELDS.get(ttype, []):
                    if req not in t:
                        qerr(f"判据类型 {ttype} 需要字段 '{req}'")
                obj: dict = {}
                for k, v in t.items():
                    if k == "type":
                        continue
                    obj[k] = v
                obj["id"] = self.ids.make("task", ch.key, name, str(i))
                obj["type"] = "checkmark" if ttype == "checkmark" else ttype
                tasks.append(obj)

            # 奖励
            rewards: list[dict] = []
            for i, r in enumerate(q.get("rewards", []) or []):
                rtype = r.get("type")
                if rtype not in REWARD_TYPES:
                    qerr(f"奖励 #{i + 1} 的 type='{rtype}' 不在白名单（见 QUESTBOOK-FORMAT.md §6.2）")
                    continue
                obj = {k: v for k, v in r.items() if k != "type"}
                # 补齐该类型的必需默认（如 command 的 permission_level）
                for k, v in REWARD_EXTRA_DEFAULTS.get(str(rtype), {}).items():
                    obj.setdefault(k, v)
                if rtype == "command" and not obj.get("command"):
                    qerr(f"奖励 #{i + 1} 是 command 但没有 command 字段")
                obj["id"] = self.ids.make("reward", ch.key, name, str(i))
                obj["type"] = rtype
                rewards.append(obj)

            shape = q.get("shape", "circle")
            if shape not in SHAPES:
                qerr(f"shape='{shape}' 不在已知形状里")

            x, y = positions.get(name, (0.0, 0.0))
            if "x" in q and "y" in q:
                x, y = float(q["x"]), float(q["y"])
            # 抹掉排布累加产生的浮点尾巴（游戏自己写的文件里也常见这种残留，但我们写规整值）
            x, y = round(float(x), 2), round(float(y), 2)

            entry: dict = {}
            if dep_ids:
                entry["dependencies"] = dep_ids
                if q.get("dep_requirement"):
                    entry["dependency_requirement"] = q["dep_requirement"]
            if q.get("description") or q.get("desc"):
                # 不写进结构：正文归 lang（§7）。这里只做提示性报错。
                pass
            if q.get("icon"):
                entry["icon"] = {"id": q["icon"]}
            entry["id"] = qid
            if q.get("invisible"):
                entry["invisible"] = True
            if q.get("min_width"):
                entry["min_width"] = int(q["min_width"])
            if q.get("optional"):
                entry["optional"] = True
            if rewards:
                entry["rewards"] = rewards
            entry["shape"] = shape
            entry["size"] = float(q.get("size", 1.5))
            entry["tasks"] = tasks
            entry["x"] = float(x)
            entry["y"] = float(y)
            compiled_quests.append(entry)

        self.quest_ids[ch.key] = id_map

        node: dict = {
            "default_hide_dependency_lines": False,
            "default_quest_shape": "",
            "filename": ch.key,
            "group": self.group_ids.get(ch.group, "") if ch.group else "",
            "icon": {"id": ch.icon},
            "id": cid,
            "order_index": ch.order_index,
        }
        if ch.progression_mode:
            node["progression_mode"] = ch.progression_mode
        node["quest_links"] = []
        node["quests"] = compiled_quests
        # 留一份原始 node：软链接要等**所有**章都编译完（才知道目标任务的 id）
        # 才能解析，所以这里不能立刻序列化。见 apply_links()。
        self.chapter_nodes[ch.key] = node
        return snbt(node) + "\n", compiled_quests

    def apply_links(self, chapters: list[ChapterDef]) -> dict[str, str]:
        """把 `[[link]]` 解析成真正的 `quest_links`，并序列化每一章。

        为什么必须**第二遍**：软链接指向的是**别的章**里某条任务的 id，
        而那要等那一章编译完才有。所以流程是「先全部编译 → 再统一解析链接 → 再序列化」。

        FTB Quests 的 `quest_links` 元素结构（实测自天空蜂巢的 chapter 文件）：
            { id: "<链接自己的 id>" linked_quest: "<目标任务 id>" x: …d y: …d }
        —— 注意 `linked_quest` 是**任务 id**（不是章 id），x/y 是**本章画布**上的位置。

        目标不存在时**报错**（不是警告）：写错的指路牌会把玩家引到空白处，
        而且这种错在游戏里完全不报错。
        """
        texts: dict[str, str] = {}
        for ch in chapters:
            node = self.chapter_nodes[ch.key]
            links: list[dict] = []
            for i, lk in enumerate(ch.links):
                tgt_ch = str(lk.get("to_chapter", ""))
                tgt_q = str(lk.get("to_quest", ""))
                where = f"{ch.path.name} [[link]] #{i + 1} → {tgt_ch}/{tgt_q}"
                if tgt_ch == ch.key:
                    self.err(where, "软链接指向本章（章内连线请用 deps）")
                    continue
                tid = self.quest_ids.get(tgt_ch, {}).get(tgt_q)
                if not tid:
                    self.err(where, f"目标任务不存在（{tgt_ch} 章里没有名为 {tgt_q} 的任务）")
                    continue
                # 缺省位置：排在主轴左侧，按序号往下错开，避免重叠
                lx = float(lk.get("x", -10.0))
                ly = float(lk.get("y", -float(i) * 1.75))
                links.append({
                    "id": self.ids.make("link", ch.key, str(i)),
                    "linked_quest": tid,
                    "x": round(lx, 2),
                    "y": round(ly, 2),
                })
            node["quest_links"] = links
            texts[ch.key] = snbt(node) + "\n"
        return texts

    # ---- 语言 ----
    def lang_chapter(self, chapters: list[ChapterDef]) -> str:
        d: dict = {}
        for ch in sorted(chapters, key=lambda c: c.order_index):
            cid = self.chapter_ids[ch.key]
            if ch.subtitle:
                d[f"chapter.{cid}.chapter_subtitle"] = [ch.subtitle]
            d[f"chapter.{cid}.title"] = ch.title
        return snbt(d) + "\n"

    def lang_group(self, groups: list[dict]) -> str:
        d: dict = {}
        for g in groups:
            d[f"chapter_group.{self.group_ids[g['key']]}.title"] = g["title"]
        return snbt(d) + "\n"

    def lang_quests(self, ch: ChapterDef) -> str:
        d: dict = {}
        id_map = self.quest_ids.get(ch.key, {})
        for q in ch.quests:
            qid = id_map.get(q["name"])
            if not qid:
                continue
            if q.get("title"):
                d[f"quest.{qid}.title"] = q["title"]
            if q.get("subtitle"):
                d[f"quest.{qid}.subtitle"] = q["subtitle"]
            desc = q.get("desc") or q.get("description")
            if desc:
                d[f"quest.{qid}.description"] = list(desc)
        return snbt(d) + "\n"


# ======================================================================
# 写盘
# ======================================================================

def write_file(path: Path, text: str, *, protect: bool = False) -> str:
    """显式 UTF-8 + LF。protect=True 时，已存在的文件只补缺失的键（不覆盖用户的字）。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    if protect and path.is_file():
        old = path.read_text(encoding="utf-8")
        merged = merge_lang(old, text)
        if merged == old:
            return "unchanged"
        path.write_text(merged, encoding="utf-8", newline="\n")
        return "merged"
    path.write_text(text, encoding="utf-8", newline="\n")
    return "written"


def _parse_snbt_blocks(text: str):
    """把 `{ \n key: value \n ... }` 形状的 SNBT 拆成 (头, 尾, [(key, 该键的整块行)])。

    **为什么不能按行合并**（真事故，2026-09-22）：
    原实现把每个键当成一行，判断"这一行有没有冒号"。但 description 是**多行数组**：

        quest.XXXX.description: [
            "第一段"
            "第二段"
        ]

    第一行含冒号但值只开了个 `[`，被当成新键收录；后续 `"第一段"` 行**不含冒号**，
    被丢掉。结果写出 `description: [` 后面直接 `}` —— **数组内容整段丢失**。
    实测丢了 7 条（landing / create / defense）。所以必须**按块**处理：
    一个键的块 = 从 `key:` 那行起，到括号配平为止。
    """
    ls = text.split("\n")
    head, blocks, tail = [], [], []
    i, n = 0, len(ls)

    while i < n and ls[i].strip() in ("", "{"):
        head.append(ls[i])
        i += 1

    while i < n:
        raw = ls[i]
        s = raw.strip()
        if s == "}":
            tail = ls[i:]
            break
        if not s or ":" not in s:
            head.append(raw)      # 不该出现的散行：收着，别丢
            i += 1
            continue
        key = s.split(":", 1)[0].strip()
        block = [raw]
        depth = raw.count("[") + raw.count("{") - raw.count("]") - raw.count("}")
        i += 1
        while depth > 0 and i < n:
            block.append(ls[i])
            depth += (ls[i].count("[") + ls[i].count("{")
                      - ls[i].count("]") - ls[i].count("}"))
            i += 1
        blocks.append((key, block))

    if not tail:
        tail = ["}"]
    return head, tail, blocks


def merge_lang(old: str, new: str) -> str:
    """把新骨架里**缺失的键**补进旧文件；已存在的键保持旧值（那是用户的字）。

    按**块**合并 —— 见 `_parse_snbt_blocks` 里记的事故，按行合并会丢多行数组的内容。
    """
    head, _tail, old_blocks = _parse_snbt_blocks(old)
    _h2, _t2, new_blocks = _parse_snbt_blocks(new)
    old_keys = {k for k, _ in old_blocks}

    added = [b for key, blk in new_blocks if key not in old_keys for b in blk]
    body = [b for _k, blk in old_blocks for b in blk]

    text = "\n".join(head + body + added + ["}"])
    while "\n\n}" in text:
        text = text.replace("\n\n}", "\n}")
    return text if text.endswith("\n") else text + "\n"


# ======================================================================
# main
# ======================================================================

def main() -> int:
    ap = argparse.ArgumentParser(description="编译任务书定义 → FTB Quests SNBT")
    ap.add_argument("--force-lang", action="store_true",
                    help="连 lang 正文档也重写（默认保护用户已改的文案）")
    ap.add_argument("--check-only", action="store_true", help="只报告，不写盘")
    args = ap.parse_args()

    groups = load_groups()
    opts = load_data_opts()
    chapters = load_chapters()

    if not chapters:
        print(f"没有找到章节定义：{CHAPTERS_SRC}/*.toml", file=sys.stderr)
        return 1

    c = Compiler()
    groups_snbt, groups_raw = c.compile_groups(groups)
    for ch in chapters:
        c.compile_chapter(ch)          # 第一遍：只为拿到全部 id（含各章的 quest_ids）

    # 第二遍：软链接要等所有章都编译完才知道目标任务 id，所以统一在这里解析并序列化。
    chapter_texts = c.apply_links(chapters)

    if c.errors:
        print("=== 编译失败，未写盘 ===", file=sys.stderr)
        for e in c.errors:
            print("  ✗ " + e, file=sys.stderr)
        return 2

    for w in c.warnings:
        print("  ! " + w, file=sys.stderr)

    plan: list[tuple[Path, str, bool]] = [
        (OUT_QUESTS / "data.snbt", DATA_SNBT_TEMPLATE.format(**opts), False),
        (OUT_QUESTS / "chapter_groups.snbt", groups_snbt, False),
    ]
    for ch in chapters:
        plan.append((OUT_QUESTS / "chapters" / f"{ch.key}.snbt", chapter_texts[ch.key], False))
    plan.append((OUT_LANG / "chapter.snbt", c.lang_chapter(chapters), False))
    plan.append((OUT_LANG / "chapter_group.snbt", c.lang_group(groups), False))
    for ch in chapters:
        plan.append((OUT_LANG / "chapters" / f"{ch.key}.snbt", c.lang_quests(ch), not args.force_lang))

    n_quest = sum(len(ch.quests) for ch in chapters)
    print(f"章 {len(chapters)} · 任务 {n_quest} · 分组 {len(groups)}")
    for path, text, protect in plan:
        rel = path.relative_to(ROOT)
        if args.check_only:
            exists = path.is_file()
            old = path.read_text(encoding="utf-8") if exists else None
            status = "unchanged" if old == text else ("would-merge" if protect and exists else "would-write")
            print(f"  [{status}] {rel}")
        else:
            status = write_file(path, text, protect=protect)
            print(f"  [{status}] {rel}")

    if args.check_only:
        return 0
    print("\n完成。下一步：python tools/questbook_check.py（离线校验）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

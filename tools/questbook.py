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


def layout(chapter: ChapterDef) -> dict[str, tuple[float, float]]:
    """给没有显式 x/y 的任务排布坐标。

    约定（见 QUESTBOOK-FORMAT.md §5.2）：
      - 主线（shape = rsquare / pentagon）沿 y = 0 横向排开，左→右
      - 其余任务按"第一条依赖所在的行"决定行号：依赖主线 → 下一行；依赖支线 → 再下一行
      - 每行按该行任务的 size 计算间距，保持不重叠

    ⚠️ 行号用**迭代式不动点**算，不用递归：递归在依赖成环时会 `RecursionError`
    把生成器整个打挂（2026-09-22 实测）。这里的写法有环时**不会崩**，
    环里的节点留在第 0 行，由 `compile_chapter()` 报成可读的错误。
    """
    g = {"step": 1.5, "row_h": 1.5, "origin_x": 0.0, "origin_y": 0.0}
    g.update(chapter.grid)
    step = float(g["step"])
    row_h = float(g["row_h"])

    quests = chapter.quests
    by_name = {q["name"]: q for q in quests}
    # ⚠️ 只有**章内存在**的依赖才算数。跨章引用会被 compile_chapter 报错，
    # 但 layout() 先跑，所以这里必须自己过滤 —— 否则
    # `max()` 收到空序列会抛 ValueError，把生成器整个打挂
    # （2026-09-22 拆章时实测踩到：14 条任务带着跨章依赖搬进新章）。
    local_deps = {q["name"]: [d for d in (q.get("deps") or []) if d in by_name] for q in quests}

    row_of: dict[str, int] = {}
    for _ in range(len(quests) + 2):          # 最多 N+1 轮就能到不动点
        progressed = False
        for name, deps in local_deps.items():
            if name in row_of:
                continue
            if all(d in row_of for d in deps):
                row_of[name] = 0 if not deps else 1 + max(row_of[d] for d in deps)
                progressed = True
        if not progressed:
            break
    for name in local_deps:                   # 环里的节点兜底
        row_of.setdefault(name, 0)

    rows: dict[int, list[dict]] = {}
    for q in quests:
        rows.setdefault(row_of[q["name"]], []).append(q)

    pos: dict[str, tuple[float, float]] = {}
    for row, items in sorted(rows.items()):
        # 每行按 size 累加宽度，再整体居中
        widths = [max(step, float(it.get("size", 1.5)) * step + step * 0.5) for it in items]
        total = sum(widths)
        cursor = float(g["origin_x"]) - total / 2.0
        for it, w in zip(items, widths):
            pos[it["name"]] = (cursor + w / 2.0, float(g["origin_y"]) - row * row_h)
            cursor += w
    return pos


# ======================================================================
# 编译
# ======================================================================

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

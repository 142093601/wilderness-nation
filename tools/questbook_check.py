#!/usr/bin/env python3
"""questbook_check.py -- 任务书数据的离线校验器。

它解决什么
----------
生成器说"我写得对"不算证据。本工具**独立读回磁盘上的产物**（自己解析 SNBT），
再逐条判红绿。出错的代价很具体：任务书在游戏里加载失败 = 玩家看到空书。

六项（`PLAN-questbook.md` §八）+ 三项追加
----------------------------------------
  ① 结构自洽：所有文件能被解析、章/任务/判据/奖励三级 id 齐全且互不重复
  ② id 可复现：重跑生成器不漂移（--reproducible 时真的重跑并比对字节）
  ③ 依赖无环 / 无悬空 / quest_links 可解析
  ④ 判据与奖励的 type 在白名单内（QUESTBOOK-FORMAT.md §6）
  ⑤ 每个任务都有对应 lang 键；lang 里没有孤儿键
  ⑥ `checkmark` 只出现在例外清单里
  ⑦ 【追加】判据引用的物品/方块 id 在**真实注册表**里存在（registry.json）
  ⑧ 【追加】同章任务坐标不重叠
  ⑨ 【追加】产物已登记进 `pack/index.toml`（否则玩家装不到）

用法
----
    python tools/questbook_check.py
    python tools/questbook_check.py --reproducible   # 额外：重跑生成器比对字节
    python tools/questbook_check.py --json out.json
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
OUT_QUESTS = ROOT / "pack" / "config" / "ftbquests" / "quests"
OUT_LANG = OUT_QUESTS / "lang" / "zh_cn"
QBOOK_DIR = HERE / "questbook"
CHAPTERS_SRC = QBOOK_DIR / "chapters"
REGISTRY = HERE / "registry.json"
INDEX = ROOT / "pack" / "index.toml"

TASK_TYPES = {
    "item", "dimension", "biome", "structure", "advancement", "observation",
    "stat", "kill", "location", "fluid", "xp", "gamestage", "custom", "checkmark",
}
REWARD_TYPES = {
    "item", "xp", "xp_levels", "loot", "random", "choice", "all_table",
    "command", "toast", "gamestage", "advancement", "currency", "custom",
}
HEX16 = re.compile(r"^[0-9A-F]{16}$")

# checkmark 例外清单 —— 出现即必须有理由。清单外的任务用 checkmark 直接判 FAIL。
# 原始依据是 PLAN-questbook.md §4.2（4 条）；实际写下来后按**游戏事实**调整过，
# 每一条的变化都记在 QUESTBOOK-CRITERIA.md §五。
CHECKMARK_EXCEPTIONS = {
    # —— 序章：说明书本身，没有游戏事实可判 ——
    "how_to_open": "说明书：「按 N 打开任务书」没有可检测信号",
    "how_to_read_arrows": "说明书：读懂箭头不是游戏行为",
    "chronicle_and_craft": "说明书：看懂四栏归类不是游戏行为",
    "eras_and_key": "说明书：理解时代与钥匙机制不是游戏行为",
    "where_to_ask": "说明书：知道去哪查不是游戏行为",
    "prologue_marker": "说明书：序章的收尾格",
    # —— 落地 ——
    "pick_ground": "选址是集体决策，游戏里没有「大家同意了」这个信号",
    "survive_first_night": "游戏不记录「天亮时你在不在附近」",
    "fence_the_camp": "原版没有「放置方块」统计，围栏是否合围判不了",
    "claim_the_land": "OPAC 未提供可检测信号（待 mod 侧补，见交接说明）",
    # —— 邦交（Statecraft 不提供任何 advancement / statistic，见 CONFLICTS 与 NEXT.md §3.4）——
    "read_the_book": "模组不记录「翻过情报册」；这是引导流程的一步，不是门闸",
    "first_contact_list": "「读了详报」没有信号源（深挖动作模组不记账）",
    "first_diplomacy": "六个外交动作模组都不发进度、不记统计（实测 advancements=0）",
    "take_the_city": "夺城成功事件模组不提供，也不发进度",
    # —— 防御与尸潮 ——
    "first_wall": "原版没有「放置方块」统计，墙是否合围判不了",
    "hold_the_line": "The Hordes / Undead Nights 不提供进度或统计（实测），"
                     "「守住一次袭击」这个事件没有信号源；要自动判定需 mod 侧给波次事件",
    # —— 立国（时代 1）：OPAC 与 FTB Teams **完全没有物品**（lang 里 item 键为 0，实测） ——
    "claim_the_land": "OPAC 不提供物品/进度（它的 lang 里 item 键为 0），圈地与否判不了",
    "one_team": "FTB Teams 同样没有物品（实测），组队与否判不了",
    "pact_on_paper": "书的**内容**判不了（放下书架能检测，但「约定了」不能）",
    "founding_monument": "立碑动作没有信号源（碑文内容也不可检测）",
    # —— 基建（时代 2）——
    "map_wall": "「地图墙覆盖完整」是主观覆盖率（有没有空洞得自己看）",
    # —— 出关（时代 3）——
    "intel_wall": "情报墙「标全了没有」是主观覆盖率",
    "roster": "远征名册的**内容**判不了（写了什么无法检测）",
    "tracking": "情报站升级动作不发进度（statecraft 实测 advancements=0）",
    # —— 御敌（时代 4，编年）——
    "hold_a_wave": "同 hold_the_line：波次事件没有信号源（The Hordes / Undead Nights 不记账）",
    "under_attack": "被宣战是 mod 侧事件，不发进度也不记统计（实测）",
    # —— 文明（时代 5）——
    "stele_forest": "碑上刻了什么判不了（碑文不可检测）",
    # —— 无尽纪元 ——
    "epitaph_each_era": "立碑动作没有信号源",
    # —— 图纸与工程 ——
    "first_scan": "「扫描成功」是客户端/界面动作，模组不发进度（实测）",
    # —— 领地与队伍（OPAC / FTB Teams 完全没有物品，实测）——
    "claim_anatomy": "OPAC 无物品、无进度；边界是否画清判不了",
    "role_split": "权限分档是界面配置，没有信号源",
    "alley_check": "越界测试是人为验收（DEFERRED.md A1 记着它需要两个真实玩家）",
    "shared_chest": "「共用/私有怎么分」是约定问题，不是可检测事件",
    # —— 类目 · 地图与探索 ——
    "e_client_tools": "Xaero's / Jade / Traveler's Titles 全是纯客户端界面 mod，"
                      "实测零物品（命名空间在注册表里为空）→ 界面工具没有判据",
    # —— 类目 · 战斗与军械 ——
    # （本章已改为实物判据：钢械、攻城器械、掠夺者进度都能判）
    # —— 类目 · 性能与信息（全章知识型：性能 mod 实测零物品）——
    "p_optimized": "性能 mod 没有物品接口（spark/modernfix/embeddium 命名空间在注册表里为空）",
    "p_first_question": "知识型条目：教人分辨「崩溃」与「卡顿」，没有可检测信号",
    "p_spark": "知识型条目：spark 是命令，不是物品",
    "p_tps": "知识型条目：/tick query 是命令",
    "p_graphics": "知识型条目：客户端画面设置",
    "p_lag_source": "知识型条目：经验性排查顺序",
    "p_info_tools": "知识型条目：Jade / BetterF3 / AppleSkin 都是界面 mod，无物品",
    "p_chunk_watch": "知识型条目：备份与预生成的操作习惯",
    "p_done": "知识型条目：本章收尾格",
    # —— 技艺 · 蒸汽与铁路 ——
    "st_kitchen": "Create Central Kitchen 无物品接口（实测命名空间为空）",
}


# ======================================================================
# 极简 SNBT 解析（只覆盖 FTB Quests 用到的形状）
# ======================================================================

class SnbtError(ValueError):
    pass


class _P:
    def __init__(self, s: str):
        self.s = s
        self.i = 0

    def ws(self) -> None:
        while self.i < len(self.s) and self.s[self.i] in " \t\r\n":
            self.i += 1

    def peek(self) -> str:
        self.ws()
        return self.s[self.i] if self.i < len(self.s) else ""

    def parse_value(self):
        c = self.peek()
        if c == "{":
            return self.parse_compound()
        if c == "[":
            return self.parse_list()
        if c in "\"'":
            return self.parse_string()
        return self.parse_scalar()

    def parse_compound(self) -> dict:
        self.ws()
        if self.s[self.i] != "{":
            raise SnbtError(f"expected '{{' at {self.i}")
        self.i += 1
        out: dict = {}
        while True:
            self.ws()
            if self.i >= len(self.s):
                raise SnbtError("unterminated compound")
            if self.s[self.i] == "}":
                self.i += 1
                return out
            # key
            if self.s[self.i] in "\"'":
                key = self.parse_string()
            else:
                j = self.i
                while self.i < len(self.s) and self.s[self.i] not in ": \t\r\n":
                    self.i += 1
                key = self.s[j:self.i]
            self.ws()
            if self.i >= len(self.s) or self.s[self.i] != ":":
                raise SnbtError(f"expected ':' after key {key!r} at {self.i}")
            self.i += 1
            out[key] = self.parse_value()

    def parse_list(self) -> list:
        self.ws()
        if self.s[self.i] != "[":
            raise SnbtError("expected '['")
        self.i += 1
        out: list = []
        while True:
            self.ws()
            if self.i >= len(self.s):
                raise SnbtError("unterminated list")
            if self.s[self.i] == "]":
                self.i += 1
                return out
            out.append(self.parse_value())

    def parse_string(self) -> str:
        q = self.s[self.i]
        self.i += 1
        buf: list[str] = []
        while self.i < len(self.s):
            ch = self.s[self.i]
            if ch == "\\" and self.i + 1 < len(self.s):
                buf.append(self.s[self.i + 1])
                self.i += 2
                continue
            if ch == q:
                self.i += 1
                return "".join(buf)
            buf.append(ch)
            self.i += 1
        raise SnbtError("unterminated string")

    def parse_scalar(self):
        j = self.i
        while self.i < len(self.s) and self.s[self.i] not in ",]}\r\n":
            self.i += 1
        tok = self.s[j:self.i].strip()
        if tok in ("true", "false"):
            return tok == "true"
        if len(tok) > 1 and tok[-1] in "dDfF":
            try:
                return float(tok[:-1])
            except ValueError:
                return tok
        if len(tok) > 1 and tok[-1] in "lLsSbB":
            try:
                return int(tok[:-1])
            except ValueError:
                return tok
        try:
            return int(tok)
        except ValueError:
            pass
        try:
            return float(tok)
        except ValueError:
            return tok


def parse_snbt(text: str):
    p = _P(text)
    v = p.parse_value()
    p.ws()
    return v


# ======================================================================
# 校验
# ======================================================================

class Report:
    def __init__(self) -> None:
        self.fails: list[str] = []
        self.notes: list[str] = []
        self.counts: dict[str, int] = {}

    def fail(self, check: str, msg: str) -> None:
        self.fails.append(f"[{check}] {msg}")

    def note(self, msg: str) -> None:
        self.notes.append(msg)


def load_all(report: Report) -> dict:
    """读回磁盘产物。任何解析失败都是硬失败。"""
    data = {"data": None, "groups": None, "chapters": {}, "lang_chapter": None,
            "lang_group": None, "lang_quests": {}}

    def read(path: Path, label: str):
        if not path.is_file():
            report.fail("① 结构自洽", f"缺失文件：{path.relative_to(ROOT)}")
            return None
        try:
            return parse_snbt(path.read_text(encoding="utf-8"))
        except Exception as exc:  # noqa: BLE001
            report.fail("① 结构自洽", f"{label} 解析失败：{type(exc).__name__}: {exc}")
            return None

    data["data"] = read(OUT_QUESTS / "data.snbt", "data.snbt")
    data["groups"] = read(OUT_QUESTS / "chapter_groups.snbt", "chapter_groups.snbt")
    data["lang_chapter"] = read(OUT_LANG / "chapter.snbt", "lang/chapter.snbt")
    data["lang_group"] = read(OUT_LANG / "chapter_group.snbt", "lang/chapter_group.snbt")

    cdir = OUT_QUESTS / "chapters"
    if cdir.is_dir():
        for p in sorted(cdir.glob("*.snbt")):
            c = read(p, f"chapters/{p.name}")
            if c is not None:
                data["chapters"][p.stem] = c
    ldir = OUT_LANG / "chapters"
    if ldir.is_dir():
        for p in sorted(ldir.glob("*.snbt")):
            data["lang_quests"][p.stem] = read(p, f"lang/chapters/{p.name}")
    return data


def check_structure(d: dict, r: Report) -> None:
    if d["data"] is not None:
        if d["data"].get("version") != 13:
            r.fail("① 结构自洽", f"data.snbt version={d['data'].get('version')!r}，期望 13")
    if d["groups"] is not None:
        gs = d["groups"].get("chapter_groups", [])
        ids = [g.get("id") for g in gs]
        if len(ids) != len(set(ids)):
            r.fail("① 结构自洽", "chapter_groups 里有重复 id")
        for gid in ids:
            if not isinstance(gid, str) or not HEX16.match(gid):
                r.fail("① 结构自洽", f"分组 id 不是 16 位大写十六进制：{gid!r}")
        r.counts["groups"] = len(ids)

    all_ids: list[tuple[str, str]] = []
    chapter_ids = [c.get("id") for c in d["chapters"].values()]
    all_ids += [("chapter", i) for i in chapter_ids]

    for key, ch in d["chapters"].items():
        where = f"chapters/{key}.snbt"
        if not HEX16.match(str(ch.get("id", ""))):
            r.fail("① 结构自洽", f"{where}: 章 id 不是 16 位大写十六进制")
        if ch.get("filename") != key:
            r.fail("① 结构自洽", f"{where}: filename={ch.get('filename')!r} 与文件名 {key!r} 不一致")
        quests = ch.get("quests", [])
        if not isinstance(quests, list):
            r.fail("① 结构自洽", f"{where}: quests 不是列表")
            continue
        r.counts["quests"] = r.counts.get("quests", 0) + len(quests)
        for q in quests:
            qid = str(q.get("id", ""))
            if not HEX16.match(qid):
                r.fail("① 结构自洽", f"{where}: 任务 id 非法 {qid!r}")
            all_ids.append(("quest", qid))
            for t in q.get("tasks", []) or []:
                tid = str(t.get("id", ""))
                if not HEX16.match(tid):
                    r.fail("① 结构自洽", f"{where}: 判据 id 非法 {tid!r}")
                all_ids.append(("task", tid))
            for rw in q.get("rewards", []) or []:
                rid = str(rw.get("id", ""))
                if not HEX16.match(rid):
                    r.fail("① 结构自洽", f"{where}: 奖励 id 非法 {rid!r}")
                all_ids.append(("reward", rid))

    seen: dict[str, str] = {}
    for kind, i in all_ids:
        if i in seen:
            r.fail("① 结构自洽", f"id 重复：{i}（{seen[i]} 与 {kind}）")
        else:
            seen[i] = kind
    r.counts["ids_total"] = len(all_ids)
    r.counts["ids_unique"] = len(seen)


def check_deps_and_links(d: dict, r: Report) -> None:
    for key, ch in d["chapters"].items():
        quests = ch.get("quests", []) or []
        ids = {q.get("id") for q in quests}
        graph: dict[str, list[str]] = {}
        for q in quests:
            qid = q.get("id")
            deps = q.get("dependencies", []) or []
            graph[qid] = list(deps)
            for dep in deps:
                if dep not in ids:
                    r.fail("③ 依赖", f"chapters/{key}.snbt: 任务 {qid} 依赖 {dep}，"
                                     f"后者不在本章内（悬空引用）")
        # 环检测
        color: dict[str, int] = {}

        def dfs(n: str, stack: list[str]) -> None:
            color[n] = 1
            for m in graph.get(n, []):
                if m not in graph:
                    continue
                if color.get(m) == 1:
                    r.fail("③ 依赖", f"chapters/{key}.snbt: 依赖成环 {' -> '.join(stack + [m])}")
                    return
                if color.get(m) in (None, 0):
                    dfs(m, stack + [m])
                    if color.get(n) == 1:
                        pass
            color[n] = 2

        for n in list(graph):
            if color.get(n) in (None, 0):
                dfs(n, [n])

        # quest_links：本期只是占位，出现即要求可解析
        for link in ch.get("quest_links", []) or []:
            if isinstance(link, str) and link and link not in {
                q.get("id") for c in d["chapters"].values() for q in (c.get("quests") or [])
            }:
                r.fail("③ 依赖", f"chapters/{key}.snbt: quest_link {link} 无法解析到任何任务")


def check_types_and_registry(d: dict, r: Report) -> None:
    """第 ④ 项（类型白名单）**永远**要跑；第 ⑦ 项（id 真实性）需要 registry.json。

    两者必须解耦：少了 registry 不能顺带把白名单也放过去。
    """
    reg = {"items": set(), "blocks": set(), "referenced": set(), "advancements": set()}
    check_registry = REGISTRY.is_file()
    if check_registry:
        raw = json.loads(REGISTRY.read_text(encoding="utf-8"))
        reg["items"] = set(raw.get("items", []))
        reg["blocks"] = set(raw.get("blocks", []))
        reg["referenced"] = set(raw.get("referenced", []))
        reg["advancements"] = set(raw.get("advancements", []))
    else:
        r.note("registry.json 不存在 → 第 ⑦ 项（判据 id 真实性）本次跳过；"
               "生成它：python tools/build_registry.py --mods <实例>/mods")

    known = reg["items"] | reg["blocks"] | reg["referenced"]
    # **真物品**：有 item model 或 blockstate 的。`referenced` 只是"在配方/进度里被提到过"，
    # 它包含大量**不是物品**的字符串（例如背包升级的 model 名、注册名变体）。
    # 2026-09-21 实测踩到两次：`explorerscompass:explorers_compass` 与
    # `naturescompass:natures_compass` 都只在 referenced 里（真名多一个/少一个下划线），
    # 而服务端加载任务数据时报 `Unknown registry key` —— 判据永远不亮。
    real_items = reg["items"] | reg["blocks"]
    adv_known = reg["advancements"]
    # 原版 assets 不在本机，minecraft: 的 id 只能靠「被配方引用的 id」这一层证明。
    # 所以：只有当**同命名空间**在本地被扫到过，才判定"这个 id 不存在"（否则可能是假阴性）。
    namespaces = {x.split(":", 1)[0] for x in known}
    adv_namespaces = {x.split(":", 1)[0] for x in adv_known}

    def exists(iid: str) -> bool | None:
        """严格版：必须是**真物品**（有 model/blockstate）。

        比旧版（known 含 referenced）更严，因为宽松版放过过真错。
        """
        if iid in real_items:
            return True
        if iid in reg["referenced"]:
            return False           # 只在 referenced 里 → 不是物品（明确判错，不是推断）
        ns = iid.split(":", 1)[0]
        if ns not in namespaces:
            return None            # 该命名空间本地没有证据 → 不断言
        return False

    def adv_exists(aid: str) -> bool | None:
        """进度是否存在。原版（minecraft:）也扫到了——客户端 jar 里有 data/minecraft/advancement。"""
        if aid in adv_known:
            return True
        ns = aid.split(":", 1)[0]
        if ns not in adv_namespaces:
            return None          # 这个 mod 一条进度都没有 → 不能断言（它可能本来就不发进度）
        return False

    for key, ch in d["chapters"].items():
        for q in ch.get("quests", []) or []:
            where = f"chapters/{key}.snbt:{q.get('id')}"
            for t in q.get("tasks", []) or []:
                ttype = t.get("type")
                if ttype not in TASK_TYPES:
                    r.fail("④ 类型白名单", f"{where}: 判据 type={ttype!r} 不在白名单")
                if ttype == "item":
                    iid = ref_id(t.get("item"))
                    if not iid:
                        r.fail("④ 类型白名单", f"{where}: item 判据没有可解析的 id")
                    elif check_registry and exists(iid) is False:
                        r.fail("⑦ 判据 id 真实性",
                               f"{where}: 判据物品 {iid} 在注册表里找不到"
                               f"（同命名空间有其它 id 被扫到，所以不是假阴性）→ 玩家永远做不完")
                if ttype == "advancement":
                    aid = t.get("advancement")
                    if not aid:
                        r.fail("④ 类型白名单", f"{where}: advancement 判据没有 advancement 字段")
                    elif check_registry and adv_exists(str(aid)) is False:
                        r.fail("⑦ 判据 id 真实性",
                               f"{where}: 进度 {aid} 不存在"
                               f"（同命名空间有其它进度被扫到）→ 任务永远不亮")
                if ttype == "checkmark" and len(q.get("tasks", [])) > 1:
                    r.note(f"{where}: checkmark 与其它判据混用（通常是故意做「多者之一」）")
            for rw in q.get("rewards", []) or []:
                rtype = rw.get("type")
                if rtype not in REWARD_TYPES:
                    r.fail("④ 类型白名单", f"{where}: 奖励 type={rtype!r} 不在白名单")
                if rtype == "item":
                    iid = ref_id(rw.get("item"))
                    if check_registry and iid and exists(iid) is False:
                        r.fail("⑦ 判据 id 真实性", f"{where}: 奖励物品 {iid} 在注册表里找不到")
                if rtype == "loot" and "table_id" not in rw:
                    r.fail("④ 类型白名单", f"{where}: loot 奖励缺 table_id")
    if check_registry:
        r.counts["registry_ids"] = len(known)


def ref_id(item) -> str | None:
    if isinstance(item, str):
        return item
    if isinstance(item, dict):
        v = item.get("id")
        return v if isinstance(v, str) else None
    return None


def check_lang(d: dict, r: Report) -> None:
    chapter_keys: set[str] = set()
    for ch in d["chapters"].values():
        cid = ch.get("id")
        chapter_keys.add(f"chapter.{cid}.title")
        if d["lang_chapter"] is None:
            continue
    lc = d["lang_chapter"] or {}
    for k in chapter_keys:
        if k not in lc:
            r.fail("⑤ 语言键", f"lang/chapter.snbt 缺键 {k}")

    # 分组标题
    lg = d["lang_group"] or {}
    for g in (d["groups"] or {}).get("chapter_groups", []) or []:
        k = f"chapter_group.{g.get('id')}.title"
        if k not in lg:
            r.fail("⑤ 语言键", f"lang/chapter_group.snbt 缺键 {k}")

    # 任务键：每个任务必须有 title；lang 里不许有孤儿键
    live: set[str] = set()
    for key, ch in d["chapters"].items():
        lq = d["lang_quests"].get(key)
        if lq is None:
            r.fail("⑤ 语言键", f"缺 lang/chapters/{key}.snbt")
            continue
        for q in ch.get("quests", []) or []:
            qid = q.get("id")
            tk = f"quest.{qid}.title"
            live.add(tk)
            if tk not in lq:
                r.fail("⑤ 语言键", f"lang/chapters/{key}.snbt 缺键 {tk}"
                                   f"（任务 {qid} 在游戏里没有标题）")
            for extra in (f"quest.{qid}.subtitle", f"quest.{qid}.description"):
                if extra in lq:
                    live.add(extra)
        for k in lq:
            if k not in live:
                r.fail("⑤ 语言键", f"lang/chapters/{key}.snbt 有孤儿键 {k}"
                                   f"（对应任务已不存在，玩家改的字会丢）")


def check_checkmark(d: dict, r: Report) -> None:
    """⑥ checkmark 只能出现在例外清单里。

    例外清单的键是**稳定 name**，而 SNBT 里只有 id——所以这里用 TOML 定义做对照，
    再确认对应任务确实带 checkmark。
    """
    if not CHAPTERS_SRC.is_dir():
        r.note("没有 TOML 定义 → 跳过第 ⑥ 项")
        return
    import tomllib
    offenders: list[str] = []
    for p in sorted(CHAPTERS_SRC.glob("*.toml")):
        with p.open("rb") as fh:
            raw = tomllib.load(fh)
        for q in raw.get("quest", []) or []:
            has_cm = any((t.get("type") == "checkmark") for t in (q.get("tasks") or []))
            if has_cm and q.get("name") not in CHECKMARK_EXCEPTIONS:
                offenders.append(f"{raw.get('key')}/{q.get('name')}")
    if offenders:
        r.fail("⑥ checkmark 例外清单",
               f"以下任务用了 checkmark 但不在例外清单里（要么改判据，要么写进清单并给理由）：{offenders}")
    else:
        r.counts["checkmark_tasks"] = len(CHECKMARK_EXCEPTIONS)


def check_layout(d: dict, r: Report) -> None:
    for key, ch in d["chapters"].items():
        qs = ch.get("quests", []) or []
        for i in range(len(qs)):
            for j in range(i + 1, len(qs)):
                a, b = qs[i], qs[j]
                try:
                    ax, ay = float(a["x"]), float(a["y"])
                    bx, by = float(b["x"]), float(b["y"])
                except (KeyError, TypeError, ValueError):
                    r.fail("⑧ 坐标", f"chapters/{key}.snbt: 任务 {a.get('id')}/{b.get('id')} 坐标缺失或非数")
                    continue
                if abs(ax - bx) < 0.5 and abs(ay - by) < 0.5:
                    r.fail("⑧ 坐标", f"chapters/{key}.snbt: 任务 {a.get('id')} 与 {b.get('id')} 重叠"
                                     f"（({ax},{ay}) vs ({bx},{by})）")


def check_index(d: dict, r: Report) -> None:
    if not INDEX.is_file():
        r.note("pack/index.toml 不存在 → 跳过第 ⑨ 项")
        return
    text = INDEX.read_text(encoding="utf-8")
    if not d["chapters"]:
        return
    missing = []
    for p in sorted((OUT_QUESTS).rglob("*.snbt")):
        rel = p.relative_to(ROOT / "pack").as_posix()
        if f'file = "{rel}"' not in text:
            missing.append(rel)
    if missing:
        r.fail("⑨ packwiz 登记",
               f"{len(missing)} 个文件没登记进 pack/index.toml（玩家装不到）：{missing[:6]}"
               f"{' …' if len(missing) > 6 else ''}")
    else:
        r.counts["indexed_files"] = len(list((OUT_QUESTS).rglob("*.snbt")))


def check_reproducible(r: Report) -> None:
    """② 重跑生成器，比对所有产物的 sha256 是否不变。"""
    before = {}
    for p in sorted(OUT_QUESTS.rglob("*.snbt")):
        before[p] = hashlib.sha256(p.read_bytes()).hexdigest()
    proc = subprocess.run([sys.executable, str(HERE / "questbook.py")],
                          capture_output=True, text=True, cwd=str(ROOT))
    if proc.returncode != 0:
        r.fail("② id 可复现", f"重跑生成器失败（rc={proc.returncode}）：{proc.stderr.strip()[:400]}")
        return
    drift = []
    for p, h in before.items():
        if not p.is_file():
            drift.append(f"{p.name} 消失")
            continue
        now = hashlib.sha256(p.read_bytes()).hexdigest()
        if now != h:
            drift.append(f"{p.relative_to(ROOT)} 字节变了")
    if drift:
        r.fail("② id 可复现", f"重跑后产物漂移 {len(drift)} 处：{drift[:5]}")
    else:
        r.counts["reproducible_files"] = len(before)


def main() -> int:
    # 控制台是 GBK，直接 print 中文/符号会 UnicodeEncodeError（本机实测）
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass

    ap = argparse.ArgumentParser(description="任务书数据离线校验")
    ap.add_argument("--reproducible", action="store_true", help="重跑生成器并比对字节（第 ② 项）")
    ap.add_argument("--json", help="把结果写成 JSON")
    args = ap.parse_args()

    r = Report()
    d = load_all(r)
    check_structure(d, r)
    check_deps_and_links(d, r)
    check_types_and_registry(d, r)
    check_lang(d, r)
    check_checkmark(d, r)
    check_layout(d, r)
    check_index(d, r)
    if args.reproducible:
        check_reproducible(r)

    print("=== 任务书校验 ===")
    for k in sorted(r.counts):
        print(f"  {k:<22} {r.counts[k]}")
    for n in r.notes:
        print(f"  ~ {n}")
    print()
    if r.fails:
        print(f"FAIL -- {len(r.fails)} 条问题：")
        for f in r.fails:
            print("  x " + f)
    else:
        print("PASS -- 六项 + 三项追加校验全部通过")
    if args.json:
        Path(args.json).write_text(
            json.dumps({"counts": r.counts, "fails": r.fails, "notes": r.notes},
                       ensure_ascii=False, indent=2),
            encoding="utf-8", newline="\n")
    return 1 if r.fails else 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""任务书文案审计（离线，机械可查的部分）。

为什么需要它
------------
需求方给的文案标准是三条：

    1. **不能不知所云 / 不能当谜语人** —— 读完知道「这是什么、我该干什么」
    2. **不能太口语化** —— 不是聊天，不是"兄弟们快去挖矿"
    3. **不能太中二** —— 不堆砌"燃""炸裂""逆天改命"那套腔调

这三条本身是**主观判断**，但其中有一部分可以**机械查出来**。
这份脚本只做机械可查的部分，把"人读一遍"的工作量压缩到真正需要判断的地方。

**它不替代人读** —— 它只是把"明显不合格"的先筛掉，并且在改完之后给出
可对比的数字（改了 N 处，剩多少条待人工判断）。

用法
----
    python tools/text_audit.py                 # 全部检查，输出问题清单
    python tools/text_audit.py --stats         # 只看统计
    python tools/text_audit.py --chapter 20-landing
    python tools/text_audit.py --json data/_text_audit.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CHAPTERS = REPO_ROOT / "tools" / "questbook" / "chapters"

# ---------------------------------------------------------------- 判据阈值
# 这些阈值不是拍脑袋：它们来自对本项目 297 条现有文案的分布统计
# （见 `--stats` 输出），取"离群"的一端。
DESC_TOO_SHORT = 12        # 单段短于此 ⇒ 太薄，读者拿不到信息
DESC_TOO_LONG = 90         # 单段长于此 ⇒ 太啰嗦（本项目最长 88）
TOTAL_TOO_THIN = 15        # 一个任务的全部描述加起来短于此 ⇒ 信息量不足

# 口语化标记（第 2 条：不能太口语化）
#
# ⚠️ 教训：**单词级匹配会产生误报**。第一版我把裸词 `贼` 放进列表，结果它在
# "长剑、太刀、矛、戟、锤" 这类正常文本里被匹配到（因为匹配串写错成了单字），
# 而 `真香` 这种组合词必须整词匹配。所以：
#   1) 口语化一律用**多字组合**，不用单字；
#   2) 改完必须回看命中的原文，确认不是误报再动手。
COLLOQUIAL = [
    "兄弟们", "老铁", "咱们", "真香", "走起", "搞起", "冲鸭", "芜湖",
    "绷不住", "伤害不高", "拉满", "秒杀", "无敌", "乱杀", "贼快", "贼香",
    "整起来", "就完事了", "一把梭", "无脑", "闭眼", "随便造",
]

# 中二/夸张标记（第 3 条：不能太中二）
#
# ⚠️ 教训：`燃` 这个单字**不能**当标记 —— 本项目是 Create/蒸汽主题，
# "燃料""燃烧""点燃""燃值" 全是正确的技术用词（实测 3 处命中全是这类）。
# 中二的是"燃起来了"这种**夸张用法**，所以要匹配短语而不是字。
CHUUNI = [
    "燃起来了", "燃爆", "炸裂", "逆天", "改命", "封神", "降临", "觉醒",
    "宿命", "无双", "绝杀", "天命", "至强", "极致", "巅峰", "震撼",
    "史诗", "传奇", "神迹", "王者", "君临", "唯我", "天下无敌",
]

# 谜语人标记：只说"是什么"不说"干什么"的连接词开头
VAGUE_OPENERS = [
    "这一切", "冥冥之中", "命运", "有些东西", "某些", "或许", "也许",
    "没有人知道", "谁知道", "一切", "从此",
]

# 可执行的动词（用于判断"读完知道该干什么"）
ACTION_HINTS = [
    "按", "用", "做", "造", "建", "放", "拿", "去", "找", "挖", "种",
    "养", "练", "打", "守", "接", "交", "换", "查", "看", "跑", "修",
    "铺", "围", "圈", "装", "配", "合", "升", "扩", "存", "运", "卖",
    "买", "领", "雇", "派", "开", "关", "点", "选", "定", "写", "记",
    "先", "再", "然后", "之后", "别", "不要", "必须", "需要", "建议",
]


def parse_chapter(path: Path) -> dict:
    """把一个章 TOML 解析成结构化文案（只取审计需要的字段）。"""
    text = path.read_text(encoding="utf-8")

    def field(pat: str, src: str = text) -> str | None:
        m = re.search(pat, src, re.M)
        return m.group(1) if m else None

    chapter = {
        "file": path.stem,
        "title": field(r'^title\s*=\s*"(.*?)"\s*$'),
        "subtitle": field(r'^subtitle\s*=\s*"(.*?)"\s*$'),
        "quests": [],
    }

    for block in re.split(r"^\[\[quest\]\]\s*$", text, flags=re.M)[1:]:
        name = field(r'^name\s*=\s*"(.*?)"\s*$', block)
        qtitle = field(r'^title\s*=\s*"(.*?)"\s*$', block)
        dm = re.search(r"desc\s*=\s*\[(.*?)\n\]", block, re.S)
        segs = re.findall(r'"((?:[^"\\]|\\.)*)"', dm.group(1)) if dm else []
        tasks = re.findall(r'type\s*=\s*"(\w+)"', block)
        chapter["quests"].append({
            "name": name,
            "title": qtitle,
            "desc": segs,
            "tasks": tasks,
            "chars": sum(len(s) for s in segs),
        })
    return chapter


def load_all() -> list[dict]:
    return [parse_chapter(p) for p in sorted(CHAPTERS.glob("*.toml"))]


# ------------------------------------------------------------------ 检查项
def check(chapters: list[dict]) -> list[dict]:
    issues: list[dict] = []

    def add(level: str, where: str, what: str, why: str):
        issues.append({"level": level, "where": where, "what": what, "why": why})

    # [1] 字幕重复 —— 同一句话挂在多章上 = 玩家分不清这两章
    subs = defaultdict(list)
    for c in chapters:
        if c["subtitle"]:
            subs[c["subtitle"]].append(c["file"])
    for sub, files in subs.items():
        if len(files) > 1:
            add("错误", " / ".join(files),
                f"字幕重复：{sub}",
                "同一句字幕挂在多章上，读者分不清这两章的区别（违反第 1 条）")

    # [2] 章缺少字幕
    for c in chapters:
        if not c["subtitle"]:
            add("警告", c["file"], "缺字幕", "章没有一句概括，读者靠标题猜（违反第 1 条）")

    # [3] 逐任务检查
    for c in chapters:
        # 类目章（`NNN-cat-*`）的定位是"介绍这个 mod 能干什么"，很多任务**故意**不给行动指令。
        # 所以"读不出该做什么"在类目章里只算提示，在主线/技艺章里才算警告。
        is_category = "-cat-" in c["file"]
        for q in c["quests"]:
            where = f"{c['file']} / {q['title'] or q['name']}"
            # 3.1 完全没有描述
            if not q["desc"]:
                add("警告", where, "没有描述",
                    "只有标题与判据，玩家不知道为什么要做这件事")
                continue
            # 3.2 信息量不足
            if q["chars"] < TOTAL_TOO_THIN:
                add("警告", where, f"描述过薄（{q['chars']} 字）",
                    f"低于 {TOTAL_TOO_THIN} 字，通常说不清「为什么做」")
            # 3.3 单段过长/过短
            for seg in q["desc"]:
                if len(seg) < DESC_TOO_SHORT:
                    add("提示", where, f"段落太短（{len(seg)} 字）：{seg}",
                        f"低于 {DESC_TOO_SHORT} 字，多半是残句")
                if len(seg) > DESC_TOO_LONG:
                    add("提示", where, f"段落太长（{len(seg)} 字）",
                        f"超过 {DESC_TOO_LONG} 字，建议拆段")
            joined = "".join(q["desc"])
            # 3.4 口语化
            for w in COLLOQUIAL:
                if w in joined:
                    add("错误", where, f"口语化用词：{w}", "违反第 2 条")
            # 3.5 中二
            for w in CHUUNI:
                if w in joined:
                    add("错误", where, f"夸张/中二用词：{w}", "违反第 3 条")
            # 3.6 谜语人开头
            for w in VAGUE_OPENERS:
                if joined.startswith(w):
                    add("警告", where, f"谜语人式开头：{w}…", "违反第 1 条")
            # 3.7 没有任何可执行动词
            if not any(h in joined for h in ACTION_HINTS):
                add("提示" if is_category else "警告", where, "读不出「该做什么」",
                    ("类目章定位是介绍 mod，多半是设计使然" if is_category
                     else "全段没有可执行动词（按/做/去/找…），违反第 1 条"))

    # [4] 同章内任务标题重复
    for c in chapters:
        titles = [q["title"] for q in c["quests"] if q["title"]]
        for t, n in Counter(titles).items():
            if n > 1:
                add("错误", c["file"], f"任务标题重复 {n} 次：{t}",
                    "同一章里两个任务同名，玩家无法区分")

    # [5] 跨章任务标题重复（可能是复制粘贴残留）
    allt = defaultdict(list)
    for c in chapters:
        for q in c["quests"]:
            if q["title"]:
                allt[q["title"]].append(c["file"])
    for t, files in allt.items():
        uniq = sorted(set(files))
        if len(uniq) > 1:
            add("提示", t, f"标题在 {len(uniq)} 章里重复：{'/'.join(uniq)}",
                "不一定是错的（概念可能跨章出现），但要人工确认是否该换个说法")

    return issues


def stats(chapters: list[dict]) -> None:
    qs = [q for c in chapters for q in c["quests"]]
    charlist = sorted(q["chars"] for q in qs)
    n = len(charlist)
    print(f"章数 {len(chapters)}   任务 {n}")
    print(f"描述总字数 {sum(charlist)}")
    print(f"每任务字数：最小 {charlist[0]}  中位 {charlist[n//2]}  最大 {charlist[-1]}"
          f"  平均 {sum(charlist)/n:.0f}")
    print()
    print("最薄的 12 个任务（优先看这些）：")
    for q in sorted(qs, key=lambda x: x["chars"])[:12]:
        print(f"  {q['chars']:>4} 字  {q['title'] or q['name']}")
    print()
    with_desc = sum(1 for q in qs if q["desc"])
    print(f"有描述的任务 {with_desc}/{n}（{100*with_desc/n:.0f}%）")
    tasks = Counter(t for q in qs for t in q["tasks"])
    print(f"判据类型分布：{dict(tasks)}")


def main() -> int:
    ap = argparse.ArgumentParser(description="任务书文案审计（机械可查部分）")
    ap.add_argument("--stats", action="store_true", help="只输出统计")
    ap.add_argument("--chapter", help="只看某一章")
    ap.add_argument("--json", help="把问题清单写成 JSON")
    ap.add_argument("--level", choices=["错误", "警告", "提示"],
                    help="只看某个级别")
    args = ap.parse_args()

    chapters = load_all()
    if not chapters:
        print(f"[FAIL] 没找到章文件：{CHAPTERS}", file=sys.stderr)
        return 2

    if args.chapter:
        chapters = [c for c in chapters if c["file"] == args.chapter]
        if not chapters:
            print(f"[FAIL] 没有这一章：{args.chapter}", file=sys.stderr)
            return 2

    if args.stats:
        stats(chapters)
        return 0

    issues = check(chapters)
    if args.level:
        issues = [i for i in issues if i["level"] == args.level]

    # 输出（避开控制台编码问题：写文件为主，stdout 尽力而为）
    lines = []
    by_level = Counter(i["level"] for i in issues)
    lines.append(f"=== 文案审计：共 {len(issues)} 条 ===")
    lines.append(f"错误 {by_level.get('错误',0)} · 警告 {by_level.get('警告',0)}"
                 f" · 提示 {by_level.get('提示',0)}")
    lines.append("")
    for lv in ["错误", "警告", "提示"]:
        group = [i for i in issues if i["level"] == lv]
        if not group:
            continue
        lines.append(f"---- {lv}（{len(group)}）----")
        for i in group:
            lines.append(f"  [{i['where']}] {i['what']}")
            lines.append(f"        {i['why']}")
        lines.append("")

    text = "\n".join(lines)
    out = REPO_ROOT / "data" / "_text_audit.txt"
    out.write_text(text, encoding="utf-8")
    print(f"写入 {out}（{len(issues)} 条）")
    try:
        print(text)
    except UnicodeEncodeError:
        pass

    if args.json:
        Path(args.json).write_text(
            json.dumps(issues, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"JSON: {args.json}")

    # 有"错误"级问题 ⇒ 退出码 1（可当门禁用）
    return 1 if by_level.get("错误") else 0


if __name__ == "__main__":
    sys.exit(main())

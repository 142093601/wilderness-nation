#!/usr/bin/env python3
"""把任务描述精确写入 lang SNBT —— 保留文件既有的两级缩进格式。

**为什么需要这个脚本而不是直接重跑生成器**（2026-09-22 事故）：

生成器 `snbt()` 现在产出**一级**缩进（`\\t"..."`，与 `[` 同列），
而仓库里已提交的 `lang/chapters/*.snbt` 是**两级**（`\\t\\t"..."`）。
两者都是合法 SNBT，但混用会产生大面积格式 diff。
之前 `merge_lang` 的"保护已有键"把老格式留住了，所以问题一直没暴露；
一旦用 `--force-lang` 全量重写，整个文件被改成一级 ⇒ 噪音 diff。

这个脚本的做法：**照抄文件里已有的格式**（读取相邻块的缩进作为基准），
只插入/替换需要的键，其余一个字节都不动。缩进用**实测**而不是假设。

用法：
    python tools/lang_apply.py --check          # 只报告会改什么
    python tools/lang_apply.py                 # 应用
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
LANG = REPO_ROOT / "pack" / "config" / "ftbquests" / "quests" / "lang" / "zh_cn"


def detect_indent(text: str) -> tuple[str, str]:
    """从文件里**实测**缩进：返回 (键行前缀, 数组元素前缀)。

    做法：找一个已有的 `key: [` 块，看它的键行有几个 tab、
    它的元素行有几个 tab。这样无论项目用一级还是两级，都能照抄。
    """
    lines = text.split("\n")
    for i, ln in enumerate(lines):
        s = ln.strip()
        if s.endswith(": [") and i + 1 < len(lines):
            key_tabs = len(ln) - len(ln.lstrip("\t"))
            nxt = lines[i + 1]
            el_tabs = len(nxt) - len(nxt.lstrip("\t"))
            if nxt.strip().startswith('"'):
                return "\t" * key_tabs, "\t" * el_tabs
    # 退路：顶层键一行 tab、元素两行 tab（本仓库的现状）
    return "\t", "\t\t"


def esc(s: str) -> str:
    if '"' in s and "'" not in s:
        return "'" + s + "'"
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def upsert_description(text: str, key: str, segments: list[str]) -> tuple[str, str]:
    """把 `key.description` 的数组内容写全（键不存在则新建）。

    返回 (新文本, 动作)。动作 ∈ {inserted, replaced, unchanged, missing_key}
    """
    key_pad, el_pad = detect_indent(text)
    lines = text.split("\n")

    # 找该键
    idx = None
    for i, ln in enumerate(lines):
        if ln.strip().startswith(f"{key}:"):
            idx = i
            break

    block = [f"{key_pad}{key}: ["]
    block += [f"{el_pad}{esc(s)}" for s in segments]
    block.append(f"{key_pad}]")

    if idx is None:
        # 键不存在：插到最后一个 "}" 之前
        for j in range(len(lines) - 1, -1, -1):
            if lines[j].strip() == "}":
                lines[j:j] = block
                return "\n".join(lines), "inserted"
        return text, "missing_key"

    # 键存在：判断它当前覆盖几行。
    # ⚠️ 空数组的坑：上一次失败操作可能留下 `description: [` 后面直接跟 `}`
    # （见 merge_lang 的事故）。此时括号配平会一直找不到 depth<=0，
    # 于是把整个文件当"这个块"，行为变成"inserted"。所以：
    #   · 显式处理"`[` 后面紧跟 `}` 或文件结束"这种残缺块
    #   · 配平用 guard，找不到闭合就只替换到下一行的 `]` 或该行本身
    depth = 0
    end = idx
    closed = False
    for j in range(idx, len(lines)):
        depth += (lines[j].count("[") + lines[j].count("{")
                  - lines[j].count("]") - lines[j].count("}"))
        if depth <= 0:
            end = j
            closed = True
            break
        # 残缺块保护：碰到外层 "}" 就停手（说明这个块没闭合）
        if j > idx and lines[j].strip() == "}":
            end = j - 1
            break
    if not closed and end == idx:
        # 只有 key 那一行（值残缺）：替换这一行即可
        end = idx

    old_block = lines[idx:end + 1]
    if old_block == block:
        return text, "unchanged"
    lines[idx:end + 1] = block
    return "\n".join(lines), "replaced"


# 需要补写的：章文件 → [(quest id, 描述段)]
JOBS: dict[str, list[tuple[str, list[str]]]] = {
    "chapters/landing.snbt": [
        ("quest.79D9FD4E9FEDCCAD.description", [
            "石器只能应急。铁制工具做出来，采集与战斗才算真正开始。",
            "从这一步起，你们才有余力想「留下点什么」——落成碑要等到它能立得稳。",
        ]),
    ],
    "chapters/create.snbt": [
        ("quest.1FD98DE649FA479A.description", [
            "粉碎轮把原料磨成更细的东西，矿石、建材都从这里过一道。",
            "它是整条产线的第一道工序，也是后面所有配方的入口。",
        ]),
        ("quest.8354069EBDB1BF05.description", [
            "前面几步都要人守着。机械臂替你取放物品，箱子与机器之间才算真的连起来。",
            "它是第一条不需要人站在旁边的产线——从这里开始可以谈「自动化」了。",
        ]),
        ("quest.79D023D4CDBD2C41.description", [
            "构件本身不直接用在建筑上，它是高阶机器的原料。",
            "能做它，说明你的动力与产能已经撑得住后面那些大件了。",
        ]),
    ],
    "chapters/defense.snbt": [
        ("quest.835725F3170B04D7.description", [
            "外围总要有人清。累计一百个，说明这件事已经成了常态，不是偶然。",
            "清场做得越多，守城时正面压力越小——这是「打出去」的前提。",
        ]),
        ("quest.D77BB1623BE6A09D.description", [
            "门口那一段是敌人排队进来的地方，也是最省力的杀伤位置。",
            "靶子本身不致命，它的用处是把注意力引到你想让它去的位置。",
        ]),
        ("quest.C565DAC79FB25750.description", [
            "找到营地只是知道对手在哪；打掉它才算把这一轮攻势的根拔了。",
            "据点没了，下一波的规模会小一截——这是防守第一次真正变成反击。",
        ]),
    ],
}


# 需要**更新已有键**的：quest id → 新的描述段
# 背景：`merge_lang` 是"只加缺失的键，不覆盖已有键"（为保护用户手改的文案）。
# 所以只改 TOML、跑生成器，**已存在的键不会被更新** —— 实测踩到：
# 我在 TOML 里加厚了 6 条描述，产物里还是旧文本（校验器也不查这项，是静默的）。
UPDATE_JOBS: dict[str, list[tuple[str, list[str]]]] = {
    "chapters/cat_food.snbt": [
        ("quest.03FB683BE6B54C4F.description", [
            "锅里能放什么，先由砧板决定：切菜、剁肉、剔骨都在这里做完。",
            "它是烹饪的前置——没有它，很多菜谱根本进不了锅。",
        ]),
        ("quest.BB844AAB017F1E65.description", [
            "酒酿好之后要有个地方陈着，不然只能堆在箱子里占位置。",
            "架子本身也是陈设——食堂里摆一排，比一排箱子像样。",
        ]),
    ],
    "chapters/cat_warfare.snbt": [
        ("quest.C3A25B018E3751B8.description", [
            "攻城第一步不是爬墙，是开门——门开了，人才进得去。",
            "它比投石机省事：不用瞄准，推到门口就行。",
        ]),
    ],
    "chapters/create.snbt": [
        ("quest.36E73CFBDF0010C6.description", [
            "手摇曲柄只能应急。水车与风车让动力自己来，机器才开始连续运转。",
            "挑位置是这一步的真实成本：水车要水流，风车要高处，两者都不便宜。",
        ]),
        ("quest.332CF44211BA55C8.description", [
            "黄铜是精密件（机械臂、过滤器、控制器）的门槛。",
            "先把黄铜锭与黄铜机壳各做一份出来——后面每一台高阶机器都要它们。",
        ]),
        ("quest.98D95ADAA686F89C.description", [
            "从「靠自然」到「自己烧出一整片动力」——这是量变到质变的那一步。",
            "搭一台蒸汽机并让它转起来：燃料自己加，动力不再看天。",
        ]),
    ],
}


def main() -> int:
    ap = argparse.ArgumentParser(description="把任务描述精确写入 lang（保留既有缩进）")
    ap.add_argument("--check", action="store_true", help="只报告")
    args = ap.parse_args()

    if not LANG.is_dir():
        print(f"[FAIL] 找不到 {LANG}", file=sys.stderr)
        return 2

    total = 0
    for rel, jobs in list(JOBS.items()) + list(UPDATE_JOBS.items()):
        path = LANG / rel
        if not path.is_file():
            print(f"  [MISSING FILE] {rel}")
            continue
        text = path.read_text(encoding="utf-8")
        key_pad, el_pad = detect_indent(text)
        print(f"{rel}   缩进实测: 键={len(key_pad)} tab, 元素={len(el_pad)} tab")
        changed = False
        for key, segs in jobs:
            new, action = upsert_description(text, key, segs)
            print(f"    [{action}] {key}")
            if action in ("inserted", "replaced"):
                text = new
                changed = True
                total += 1
        if changed and not args.check:
            path.write_text(text, encoding="utf-8", newline="\n")

    print(f"\n共 {total} 处需要写入" + ("（--check 未写盘）" if args.check else "，已写盘"))
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""questbook_audit_mechanics.py -- 任务书的**机制完整性**审计（离线）。

`questbook_check.py` 管的是"数据合不合法"（id 唯一、引用有效、语言键齐）。
本工具管的是另一类问题：**设计上写了、但数据里没接上的东西**。
这类缺口不会让任何检查报错，只会让机制静默失效。

当前检查项
----------
A. **时代钥匙**：每个时代的终局大任务必须有 `command` 奖励调用 `/statecraft accelerate`
   （PLAN-questbook.md §三：拿到钥匙 → 点亮「提前推进时代」→ 奖励是一条 command）。
   缺了它 = 钥匙机制根本不存在，玩家做完主线什么也不会发生。
B. **时代钥匙的命令名**必须与 mod 侧实现一致（实测存在性由 mod 源码保证，这里比对常量）。
C. **终局大任务**（shape=hexagon）唯一性：每个时代章应当恰好一个。
D. **奖励克制度**：PLAN §六 要求"大多数任务不给奖励"，只有主线节点/里程碑/终局才给。
   这里报告带奖励任务的占比（仅提示，不判失败）。

用法
----
    python tools/questbook_audit_mechanics.py
    python tools/questbook_audit_mechanics.py --json out.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import tomllib
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
CH_DIR = HERE / "questbook" / "chapters"

# 时代钥匙要调的命令（PLAN-questbook.md §十.2 的默认值：不取代价）
KEY_COMMAND = "/statecraft accelerate"

# 六个「时代」对应的章（编年史里的时代章；序与无尽纪元不算）
#
# **为什么只有这六个要有钥匙**（免得以后被当成 bug 来"修"）：
#   · 时代钥匙的语义是"提前推进到**下一个**时代"，所以只有存在下一个时代的章才该给。
#   · 序（说明书）不是时代边界；无尽纪元是**开放式终局**（设计里"每 5 年一个新纪元、
#     不设数值膨胀"），它后面没有下一个时代可推 → **不给钥匙是正确的**。
#   · 技艺章与类目章是横向分支，不承担时代推进。
# 若哪天要改这个判断，改这里，并在 PLAN-questbook.md 里同步写清理由。
ERA_CHAPTERS = {
    "landing": "落地（时代 0）",
    "founding": "立国（时代 1）",
    "infra": "基建（时代 2）",
    "expedition": "出关（时代 3）",
    "defense_era": "御敌（时代 4）",
    "civilization": "文明（时代 5）",
}
# 明确**不该**有钥匙的章（有则提示，防止误加）
NO_KEY_CHAPTERS = {
    "prologue": "序（说明书，不是时代边界）",
    "endless": "无尽纪元（开放式终局，后面没有下一个时代）",
}


def load_chapters() -> dict[str, dict]:
    """按 TOML 里的 `key` 字段索引（**不是文件名**）。

    文件名带排序前缀（`20-landing.toml`），而章 key 是 `landing`。
    第一版用 p.stem 当索引，结果 A 段（时代钥匙）全部误报"找不到章"——
    工具自己的 bug 比它要查的问题还显眼，所以这里写清楚。
    """
    out: dict[str, dict] = {}
    for p in sorted(CH_DIR.glob("*.toml")):
        with p.open("rb") as fh:
            raw = tomllib.load(fh)
        key = str(raw.get("key") or p.stem)
        out[key] = {"raw": raw, "path": p}
    return out


def rewards_of(q: dict) -> list[dict]:
    return q.get("rewards", []) or []


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass

    ap = argparse.ArgumentParser(description="任务书机制完整性审计")
    ap.add_argument("--json", help="结果写 JSON")
    args = ap.parse_args()

    chapters = load_chapters()
    fails: list[str] = []
    notes: list[str] = []
    counts: dict[str, int] = {}

    total_q = 0
    with_reward = 0
    terminuses: dict[str, list[str]] = {}

    for ch_key, meta in chapters.items():
        raw = meta["raw"]
        quests = raw.get("quest", []) or []
        total_q += len(quests)
        for q in quests:
            if rewards_of(q):
                with_reward += 1
            if str(q.get("shape", "")) == "hexagon":
                terminuses.setdefault(ch_key, []).append(str(q.get("name", "?")))

    counts["章数"] = len(chapters)
    counts["任务数"] = total_q
    counts["带奖励的任务"] = with_reward

    # ---- A. 时代钥匙 ----
    print("=== A. 时代钥匙（每个时代的终局必须发 command 奖励）===")
    for ch_key, label in ERA_CHAPTERS.items():
        meta = chapters.get(ch_key)
        if meta is None:
            fails.append(f"{label}：找不到章 {ch_key}.toml")
            continue
        quests = meta["raw"].get("quest", []) or []
        hexes = [q for q in quests if str(q.get("shape", "")) == "hexagon"]
        if not hexes:
            fails.append(f"{label}：没有终局大任务（shape=hexagon）")
            continue
        term = hexes[-1]
        cmds = [r for r in rewards_of(term) if r.get("type") == "command"]
        if not cmds:
            fails.append(f"{label}：终局「{term.get('name')}」没有 command 奖励 → 时代钥匙失效")
            print(f"  [FAIL] {label:14s} {term.get('name')} — 无 command 奖励")
            continue
        # 命令内容是否指向 accelerate
        got = " ".join(str(r.get("command", "")) for r in cmds)
        if KEY_COMMAND.strip() not in got:
            fails.append(f"{label}：终局 command 是 `{got}`，不是 `{KEY_COMMAND}`")
            print(f"  [FAIL] {label:14s} command={got!r}")
        else:
            print(f"  [OK]   {label:14s} {term.get('name')} → {got}")

    # ---- B. 命令名与 mod 侧一致 ----
    print()
    print("=== B. 命令名与 mod 侧实现一致 ===")
    ev = ROOT / "mods" / "statecraft" / "mod" / "src" / "main" / "java" / "com" / "wildernessnation" / "statecraft" / "StatecraftEvents.java"
    if ev.is_file():
        src = ev.read_text(encoding="utf-8", errors="replace")
        m = re.search(r'Commands\.literal\("accelerate"\)', src)
        if m:
            print(f"  [OK]   源码里有 {KEY_COMMAND} 的注册（StatecraftEvents.java）")
        else:
            fails.append("mod 源码里找不到 accelerate 命令注册")
            print("  [FAIL] 源码里没有 accelerate")
    else:
        notes.append("找不到 StatecraftEvents.java，跳过命令名核对")

    # ---- A2. 不该有钥匙的章（防止误加）----
    print()
    print("=== A2. 不该有时代钥匙的章（序 / 无尽纪元）===")
    for ch_key, why in NO_KEY_CHAPTERS.items():
        meta = chapters.get(ch_key)
        if meta is None:
            continue
        quests = meta["raw"].get("quest", []) or []
        cmds = [r for q in quests for r in rewards_of(q) if r.get("type") == "command"]
        if cmds:
            fails.append(f"{ch_key}（{why}）不该有 command 奖励，但有 {len(cmds)} 条")
            print(f"  [FAIL] {ch_key:10s} — {why}：不该有钥匙，却有 {len(cmds)} 条 command")
        else:
            print(f"  [OK]   {ch_key:10s} — {why}：无钥匙（正确）")

    # ---- C. 终局唯一性 ----
    print()
    print("=== C. 终局大任务唯一性 ===")
    for ch_key, names in sorted(terminuses.items()):
        flag = "OK  " if len(names) == 1 else "WARN"
        if len(names) > 1:
            notes.append(f"{ch_key} 有 {len(names)} 个终局（hexagon）：{names}")
        print(f"  [{flag}] {ch_key:16s} {len(names)} 个：{names}")
    missing_term = [c for c in chapters if c not in terminuses]
    if missing_term:
        notes.append(f"没有终局（hexagon）的章：{missing_term}")

    # ---- D. 奖励克制度 ----
    print()
    print("=== D. 奖励克制度（PLAN §六：大多数任务不给奖励）===")
    pct = (with_reward / total_q * 100) if total_q else 0
    print(f"  {with_reward}/{total_q} 个任务带奖励（{pct:.1f}%）")
    if pct > 30:
        notes.append(f"带奖励任务占比 {pct:.1f}% 偏高（PLAN 要求克制）")

    # ---- 汇总 ----
    print()
    for n in notes:
        print("  ~ " + n)
    if fails:
        print(f"\nAUDIT FAIL —— {len(fails)} 项：")
        for f in fails:
            print("  x " + f)
    else:
        print("\nAUDIT PASS —— 机制接得上（时代钥匙 / 命令名 / 终局唯一）")

    if args.json:
        Path(args.json).write_text(
            json.dumps({"counts": counts, "fails": fails, "notes": notes},
                       ensure_ascii=False, indent=2),
            encoding="utf-8", newline="\n")
    return 1 if fails else 0


if __name__ == "__main__":
    raise SystemExit(main())

"""
现状简报生成器 —— AI 史官 与 AI 顾问 共用的"素材层"。

为什么要它：LLM 不知道你们的服务器发生了什么。要么让它凭空编（危险），
要么把**事实**整理好交给它。这个脚本就是那条"事实 -> 提示词"的流水线。

产出两份文件（都在 --out 下）：
  1. chronicler_brief_<窗口>.md  —— 给"史官"：这一段时间发生的事实 + 写作约束
  2. advisor_brief.md            —— 给"顾问"：当前状态 + 待办候选 + 回答约束

**为什么不让脚本直接调 LLM**：v0 先验证"AI 写的东西你们愿不愿意读"。
把素材准备好，拿去 DSH 会话里写就行 —— 不需要 API key、不需要游戏内桥。
v1 再接 RCON 自动写回游戏（见 tools/README.md）。

用法：
  python state_brief.py --data data [--window-hours 168] [--out data/briefs]
"""

from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

# 复用分析器的快照/差分逻辑，避免两份实现漂移
import analyze_snapshots as an


def hours_between(a: str, b: str) -> float | None:
    ta, tb = an.parse_ts(a), an.parse_ts(b)
    if ta is None or tb is None:
        return None
    return (tb - ta).total_seconds() / 3600


def build_facts(snaps: list[dict[str, Any]], milestones: list[dict[str, str]], window_hours: float):
    """窗口内的事实：取窗口起点那份快照为基线，与最新快照求差。"""
    last = snaps[-1]
    last_ts = an.parse_ts(last["collected_at"])
    if last_ts is None:
        return None
    cutoff = last_ts - timedelta(hours=window_hours)
    baseline = None
    for snap in snaps:
        ts = an.parse_ts(snap["collected_at"])
        if ts is not None and ts <= cutoff:
            baseline = snap
    if baseline is None:
        baseline = snaps[0]          # 窗口比历史还长，就用最早那份

    base_players = {p["uuid"]: p for p in baseline.get("players", [])}
    rows = []
    for p in last.get("players", []):
        b = base_players.get(p["uuid"], {})

        def d(key: str, scale: float = 1.0) -> float:
            cur = p.get(key) or 0
            old = b.get(key) or 0
            return max(0.0, (cur - old) / scale)

        def dn(label: str) -> float:
            cur = (p.get("notable") or {}).get(label, 0)
            old = (b.get("notable") or {}).get(label, 0)
            return max(0, cur - old)

        rows.append({
            "name": p.get("name"),
            "play_hours_total": p.get("play_time_hours") or 0,
            "play_hours_window": round(d("play_time_ticks", 20 * 3600), 2),
            "mined_window": int(d("mined")),
            "used_window": int(d("used")),
            "crafted_window": int(d("crafted")),
            "move_km_window": round(d("move_cm", 100000), 1),
            "killed_window": int(d("killed")),
            "deaths_window": int(dn("deaths")),
            "bell_ring_window": int(dn("bell_ring")),
            "advancements_total": p.get("advancements_done") or 0,
        })
    rows.sort(key=lambda r: -r["play_hours_window"])
    return {
        "window_hours": window_hours,
        "from": baseline.get("collected_at"),
        "to": last["collected_at"],
        "players": rows,
        "log": {
            "errors": last["log"].get("errors", 0),
            "warns": last["log"].get("warns", 0),
            "startup_seconds": last["log"].get("startup_seconds"),
            "joins": last["log"].get("joins", []),
            "leaves": last["log"].get("leaves", []),
            "deaths": last["log"].get("deaths", []),
            "markers": last["log"].get("markers", []),
        },
        "level": last.get("level", {}),
        "milestones": milestones,
        "data_gaps": [
            "任务书/时代完成进度（原版 stats 看不到）",
            "TPS / MSPT（需要 spark 或周期命令）",
            "袭击事件的开始/结束与胜负（需要 mod 日志或脚本打点）",
        ],
    }


def render_chronicler(facts: dict[str, Any]) -> str:
    span = facts["window_hours"]
    lines: list[str] = []
    lines.append(f"# 史官素材（窗口 {span:.0f} 小时）\n")
    lines.append(f"- 窗口：`{facts['from']}` → `{facts['to']}`")
    lv = facts["level"]
    if lv.get("mc_version"):
        lines.append(f"- 世界：MC `{lv['mc_version']}` · 世界天数 `{lv.get('day_time')}` ticks")
    lines.append("")
    lines.append("## 事实清单（只能写这里出现过的内容）\n")
    lines.append("| 成员 | 窗口内在线(h) | 累计在线(h) | 挖掘 | 使用 | 合成 | 移动(km) | 击杀 | 死亡 | 敲钟 |")
    lines.append("|---|---|---|---|---|---|---|---|---|---|")
    for r in facts["players"]:
        lines.append(f"| {r['name']} | {r['play_hours_window']} | {r['play_hours_total']} | "
                     f"{r['mined_window']} | {r['used_window']} | {r['crafted_window']} | "
                     f"{r['move_km_window']} | {r['killed_window']} | {r['deaths_window']} | "
                     f"{r['bell_ring_window']} |")
    lines.append("")
    lg = facts["log"]
    lines.append("## 日志侧的事实\n")
    lines.append(f"- 上线次数 **{len(lg['joins'])}** · 下线次数 **{len(lg['leaves'])}** · "
                 f"日志里的死亡行 **{len(lg['deaths'])}**")
    lines.append(f"- 错误 **{lg['errors']}** · 警告 **{lg['warns']}** · 最近启动耗时 "
                 f"**{lg['startup_seconds']}** 秒")
    if lg["deaths"]:
        lines.append(f"- 死亡记录（最近）：{'、'.join(lg['deaths'][-10:])}")
    if lg["markers"]:
        lines.append(f"- 威胁相关日志命中 **{len(lg['markers'])}** 条（horde/undeadnights/roadweaver/sculk）")
    lines.append("")
    if facts["milestones"]:
        lines.append("## 人工记录的里程碑\n")
        lines.append("| 时间 | 时代 | 里程碑 | 备注 |")
        lines.append("|---|---|---|---|")
        for m in facts["milestones"]:
            lines.append(f"| {m.get('time','')} | {m.get('era','')} | {m.get('milestone','')} | {m.get('note','')} |")
        lines.append("")
    lines.append("## 写作约束（给史官）\n")
    lines.append("1. **只写事实清单里出现过的事**。没出现的不要编——编一条，整篇就不可信了。")
    lines.append("2. 第三人称、编年体语气，**150~300 字**，一段或两段。")
    lines.append("3. 用**国名与年份**称呼（例：`1512 年，西仓失火`），不要写 `第 3 周`。")
    lines.append("4. 命名用设定词根：垣 / 渡 / 烽 / 渠 / 仓；威胁叫**潮**；旧文明叫**旧世**。")
    lines.append("5. **把代价写进去**。只记成功的史书没人看；事故、失败、死人都是内容。")
    lines.append("6. 不写对话、不写角色、不写剧情——**只记录发生了什么**。")
    lines.append("7. 结尾留一句「待续」式的悬念（例如「西边的林子还没有动静」），但不要承诺具体事件。")
    lines.append("")
    lines.append("## 数据缺口（写的时候要避开）\n")
    for gap in facts["data_gaps"]:
        lines.append(f"- {gap}")
    lines.append("")
    return "\n".join(lines)


def render_advisor(facts: dict[str, Any]) -> str:
    lines: list[str] = []
    lines.append("# 顾问简报\n")
    lines.append(f"- 观察窗口：最近 {facts['window_hours']:.0f} 小时（`{facts['from']}` → `{facts['to']}`）")
    lv = facts["level"]
    if lv.get("mc_version"):
        lines.append(f"- 世界：MC `{lv['mc_version']}` · 世界天数 `{lv.get('day_time')}` ticks")
    lines.append("")
    lines.append("## 现状（脚本能看到的部分）\n")
    lines.append("| 成员 | 窗口内在线(h) | 累计在线(h) | 窗口内挖掘 | 窗口内使用 | 移动(km) | 死亡 | 敲钟 |")
    lines.append("|---|---|---|---|---|---|---|---|")
    for r in facts["players"]:
        lines.append(f"| {r['name']} | {r['play_hours_window']} | {r['play_hours_total']} | "
                     f"{r['mined_window']} | {r['used_window']} | {r['move_km_window']} | "
                     f"{r['deaths_window']} | {r['bell_ring_window']} |")
    lines.append("")
    if facts["milestones"]:
        lines.append("## 最近的里程碑（人工记录）\n")
        for m in facts["milestones"][-8:]:
            lines.append(f"- `{m.get('time','')}` **{m.get('era','')}** — {m.get('milestone','')} {m.get('note','')}")
        lines.append("")
    lines.append("## 回答约束（给顾问）\n")
    lines.append("1. **先说依据**：每条建议都要指出它来自上面哪个数字或哪条记录。")
    lines.append("2. **看不到的就说看不到**：任务进度、TPS、袭击胜负都不在数据里，不要假装知道。")
    lines.append("3. **给选项而不是命令**：说清「选 A 的后果 / 选 B 的后果」，让玩家自己定。")
    lines.append("4. **优先看信号**：窗口内在线时长骤降 = 有人掉队；挖掘/使用比例失衡 = 分工没形成；")
    lines.append("   死亡聚集 = 某个区域或某个阶段太难；敲钟次数 = 预警机制有没有被真的用起来。")
    lines.append("5. 建议要**落在本包的六个时代与 27 个任务里**（见 DESIGN.md 第十五节），不要给游戏外建议。")
    lines.append("")
    lines.append("## 数据缺口\n")
    for gap in facts["data_gaps"]:
        lines.append(f"- {gap}")
    lines.append("")
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description="为 AI 史官 / AI 顾问 生成素材简报")
    ap.add_argument("--data", default="data")
    ap.add_argument("--window-hours", type=float, default=168.0)
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    data = Path(args.data).expanduser().resolve()
    out = Path(args.out).expanduser().resolve() if args.out else data / "briefs"
    out.mkdir(parents=True, exist_ok=True)

    snaps = an.load_snapshots(data)
    if not snaps:
        print("no snapshots found -- run collect_snapshot.py first")
        return 1
    milestones = an.load_milestones(data / "milestones.csv")
    facts = build_facts(snaps, milestones, args.window_hours)
    if facts is None:
        print("could not parse snapshot timestamps")
        return 1

    span = f"{int(args.window_hours)}h"
    chronicler_path = out / f"chronicler_brief_{span}.md"
    advisor_path = out / "advisor_brief.md"
    chronicler_path.write_text(render_chronicler(facts), encoding="utf-8")
    advisor_path.write_text(render_advisor(facts), encoding="utf-8")

    print(f"snapshots: {len(snaps)}  window: {span}  players: {len(facts['players'])}")
    print(f"chronicler: {chronicler_path}")
    print(f"advisor   : {advisor_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

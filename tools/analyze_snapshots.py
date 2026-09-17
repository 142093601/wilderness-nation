"""
快照分析器 —— 把 collect_snapshot.py 攒下的快照序列变成一份复盘报告。

核心是**差分**：快照里是累计值，相邻两次相减才是"这段时间做了什么"。
报告刻意挂在**本包的设计假设**上（H1~H7），而不是给一堆通用数字 ——
因为复盘的目的不是看数据，是回答"我们当初的设计对不对"。

用法：
  python analyze_snapshots.py [--data data] [--milestones data/milestones.csv]

输出：<data>/report.md（中文报告，UTF-8）、<data>/deltas.csv
控制台只打印 ASCII。
"""

from __future__ import annotations

import argparse
import csv
import json
from datetime import datetime
from pathlib import Path
from typing import Any

COUNTERS = ["mined", "used", "crafted", "broken", "picked_up", "killed", "killed_by",
            "advancements_done", "move_cm"]


def load_snapshots(data_dir: Path) -> list[dict[str, Any]]:
    snaps = []
    for path in sorted((data_dir / "snapshots").glob("*.json")):
        try:
            snaps.append(json.loads(path.read_text(encoding="utf-8")))
        except Exception:
            continue
    snaps.sort(key=lambda s: s.get("collected_at", ""))
    return snaps


def parse_ts(text: str) -> datetime | None:
    try:
        return datetime.fromisoformat(text.replace("Z", "+00:00"))
    except Exception:
        return None


def delta(prev: dict[str, Any] | None, cur: dict[str, Any]) -> dict[str, int]:
    """相邻快照的增量（缺上一个时按 0 起点算）。"""
    out: dict[str, int] = {}
    for key in COUNTERS:
        a = (prev or {}).get(key) or 0
        b = cur.get(key) or 0
        out[key] = max(0, int(b) - int(a))
    return out


def load_milestones(path: Path) -> list[dict[str, str]]:
    if not path.is_file():
        return []
    rows = []
    with path.open("r", encoding="utf-8-sig", newline="") as fh:
        for row in csv.DictReader(fh):
            rows.append({(k or "").strip(): (v or "").strip() for k, v in row.items()})
    return rows


def build_deltas(snaps: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """把每个快照转成"相对上一次的增量"记录。"""
    out: list[dict[str, Any]] = []
    prev_players: dict[str, dict[str, Any]] = {}
    prev_ts: datetime | None = None
    for snap in snaps:
        ts = parse_ts(snap.get("collected_at", ""))
        hours = (ts - prev_ts).total_seconds() / 3600 if (ts and prev_ts) else None
        row: dict[str, Any] = {"at": snap.get("collected_at"), "wall_hours": hours, "players": {}}
        for p in snap.get("players", []):
            uuid = p["uuid"]
            d = delta(prev_players.get(uuid), p)
            d["play_hours_cum"] = p.get("play_time_hours") or 0
            d["name"] = p.get("name")
            d["bell_ring"] = (p.get("notable") or {}).get("bell_ring", 0)
            d["deaths_cum"] = (p.get("notable") or {}).get("deaths", 0)
            row["players"][uuid] = d
            prev_players[uuid] = p
        row["team"] = {
            key: sum(v[key] for v in row["players"].values()) for key in COUNTERS
        }
        row["deaths_cum"] = sum(p.get("deaths_cum", 0) for p in row["players"].values())
        row["bell_ring_cum"] = sum(p.get("bell_ring", 0) for p in row["players"].values())
        out.append(row)
        prev_ts = ts or prev_ts
    return out


def fmt_table(rows: list[list[str]], header: list[str]) -> str:
    out = ["| " + " | ".join(header) + " |", "|" + "|".join(["---"] * len(header)) + "|"]
    for r in rows:
        out.append("| " + " | ".join(str(c) for c in r) + " |")
    return "\n".join(out)


def analyze(args: argparse.Namespace) -> int:
    data = Path(args.data).expanduser().resolve()
    snaps = load_snapshots(data)
    if len(snaps) < 2:
        print(f"need >=2 snapshots, found {len(snaps)} -- run collect_snapshot.py periodically first")
        return 1
    deltas = build_deltas(snaps)
    later = deltas[1:]  # 第一份没有前序，无法算增量
    milestones = load_milestones(Path(args.milestones).expanduser().resolve())

    first_ts = parse_ts(snaps[0]["collected_at"])
    last_ts = parse_ts(snaps[-1]["collected_at"])
    span_hours = (last_ts - first_ts).total_seconds() / 3600 if (first_ts and last_ts) else 0

    # 团队累计
    last = snaps[-1]
    team_play_hours = sum(p.get("play_time_hours") or 0 for p in last["players"])
    intervals = len(later)

    # 活跃度序列（每区间活动量 = 挖掘 + 使用 + 移动公里*权重）
    activity = [(r["at"][:16].replace("T", " "),
                 r["team"]["mined"], r["team"]["used"],
                 round(r["team"]["move_cm"] / 100000, 1),
                 r["team"]["mined"] + r["team"]["used"]) for r in later]

    # 每人汇总
    per_player: dict[str, dict[str, Any]] = {}
    for r in later:
        for uuid, d in r["players"].items():
            acc = per_player.setdefault(uuid, {"name": d["name"], "mined": 0, "used": 0, "crafted": 0,
                                              "deaths": 0, "move_cm": 0, "play_hours": 0.0,
                                              "zero_intervals": 0})
            for key in ("mined", "used", "crafted", "move_cm"):
                acc[key] += d[key]
            acc["deaths"] += d["mined"] and 0 or 0  # deaths 在 notable 里，单独取
            acc["play_hours"] = d["play_hours_cum"]
            if d["mined"] + d["used"] + r["players"][uuid]["move_cm"] == 0:
                acc["zero_intervals"] += 1
    # 死亡数从最后一份快照取
    for p in last["players"]:
        if p["uuid"] in per_player:
            per_player[p["uuid"]]["deaths"] = (p.get("notable") or {}).get("deaths", 0)

    lines: list[str] = []
    lines.append("# 复盘报告（自动生成）\n")
    lines.append(f"- 快照数：**{len(snaps)}**（可比较区间 {intervals} 个）")
    lines.append(f"- 覆盖时间：`{snaps[0]['collected_at']}` → `{snaps[-1]['collected_at']}`"
                 f"（约 **{span_hours:.1f}** 小时墙钟）")
    lines.append(f"- 团队累计在线时长：**{team_play_hours:.1f}** 小时（各玩家 play_time 之和）")
    mc_ver = last["level"].get("mc_version") or "未知（未读到 level.dat）"
    day_time = last["level"].get("day_time")
    day_txt = f"{day_time} ticks" if day_time is not None else "未知（未读到 level.dat）"
    lines.append(f"- MC 版本 / 世界天数：`{mc_ver}` / {day_txt}")
    key_found = (last.get("notes") or {}).get("play_time_key")
    lines.append(f"- play_time 实际键名：`{key_found}`\n")

    lines.append("## 一、活跃度曲线（找'发空'的时段）\n")
    lines.append(fmt_table(
        [[a, m, u, mv, m + u] for a, m, u, mv, _ in activity],
        ["时间", "挖掘", "使用", "移动(km)", "活动量"]
    ))
    if len(activity) >= 4:
        vals = [a[4] for a in activity]
        avg = sum(vals) / len(vals)
        low = [a[0] for a, v in zip(activity, vals) if v < avg * 0.4]
        lines.append(f"\n平均活动量 **{avg:.0f}**；低于均值 40% 的区间（疑似'发空'）："
                     + ("、".join(f"`{x}`" for x in low) if low else "**无**"))
    lines.append("")

    lines.append("## 二、每人参与度与分工分化\n")
    rows = []
    for uuid, acc in sorted(per_player.items(), key=lambda kv: -kv[1]["mined"]):
        total = acc["mined"] + acc["used"] + 1
        rows.append([
            acc["name"], f"{acc['play_hours']:.1f}", acc["mined"], acc["used"], acc["crafted"],
            round(acc["move_cm"] / 100000, 1), acc["deaths"],
            f"{100 * acc['mined'] / total:.0f}%", acc["zero_intervals"],
        ])
    lines.append(fmt_table(rows, ["玩家", "累计在线(h)", "挖掘", "使用", "合成", "移动(km)",
                                  "死亡", "挖掘占比", "零活动区间"]))
    lines.append("\n> **读法**：挖掘占比越分散，说明分工越分化（有人专挖、有人专建）；"
                 "**零活动区间 > 总区间一半**的人，就是掉队信号。\n")

    lines.append("## 三、事故与信号\n")
    lines.append(fmt_table(
        [[r["at"][:16].replace("T", " "), r["team"]["killed"], r["team"]["killed_by"],
          r["deaths_cum"] if "deaths_cum" in r else "", r["bell_ring_cum"]]
         for r in later],
        ["时间", "击杀", "被杀", "累计死亡", "累计敲钟"]
    ))
    lines.append(f"\n日志线索：错误 **{last['log'].get('errors', 0)}** · 警告 **{last['log'].get('warns', 0)}** · "
                 f"最近启动耗时 **{last['log'].get('startup_seconds')}** 秒\n")

    lines.append("## 四、设计假设核对（H1~H7）\n")
    h = []
    h.append(["H1 时代节奏", "时代时长（递增 12/20/25/25/30/38 小时）是否合适",
              f"团队累计在线 {team_play_hours:.1f} 小时（注意：团队口径下，多人同时在线会重复计入，"
              f"应看 milestones.csv 里时代推进的真实时间点）",
              "需要 milestones.csv（时代推进日期）" if not milestones else "可就现有数据判断"])
    h.append(["H2 内容是否发空", "时代 2~3 段（累计 32~82 小时）是否没东西做",
              f"见第一节'疑似发空'区间：{'有' if len(activity) >= 4 else '样本不足'}",
              "需要更多区间的快照"])
    h.append(["H3 分工是否形成", "是否真出现'有人专挖/专建/专探'",
              "见第二节挖掘占比分布",
              "需要更长序列；若所有人占比接近，则分工没发生"])
    h.append(["H4 是否有人掉队", "个人参与度是否分化到有人退出",
              "见第二节'零活动区间'",
              "需要连续区间的快照"])
    h.append(["H5 性能红线", "TPS/MSPT 是否守在目标内",
              f"日志错误 {last['log'].get('errors', 0)} / 警告 {last['log'].get('warns', 0)} / "
              f"启动 {last['log'].get('startup_seconds')} 秒",
              "**TPS/MSPT 不在原版统计里** —— 需要 spark 或周期命令输出"])
    h.append(["H6 袭击是否成立", "可预警、可守、有代价",
              f"日志 mod 关键字命中 {len(last['log'].get('markers', []))} 条；"
              f"累计死亡 {sum((p.get('notable') or {}).get('deaths', 0) for p in last['players'])}",
              "袭击事件本身不在原版统计里 —— 需要 mod 日志或脚本打点"])
    h.append(["H7 材料经济", "材料成本是否'真实'（图纸消耗 vs 采集量）",
              f"使用/挖掘 比值 {sum(p['used'] for p in last['players']) / max(1, sum(p['mined'] for p in last['players'])):.2f}",
              "需要按材料分类的明细（当前只有总量）"])
    lines.append(fmt_table(h, ["假设", "问的是什么", "当前判定", "还缺什么数据"]))
    lines.append("")

    lines.append("## 五、数据缺口（下一版补什么）\n")
    lines.append("| 缺口 | 为什么缺 | 补法 |")
    lines.append("|---|---|---|")
    lines.append("| **TPS / MSPT** | 原版统计里没有 | 装 `spark`，或让服务器周期性 echo TPS 到日志 |")
    lines.append("| **任务/时代完成时间** | 任务书进度不在原版统计 | 人工维护 `milestones.csv`，或让日历脚本写一行日志 |")
    lines.append("| **袭击事件明细** | 原版统计里没有 | 看 mod 日志（hordes / undeadnights 关键字），或脚本打点 |")
    lines.append("| **'放置'统计** | 原版只有 `used`（使用物品），没有独立放置计数 | 用 `used` 近似；若要精确需脚本打点 |")
    lines.append("| **材料分类** | 当前只汇总总量 | 采集器可加 `--detail` 输出 top-N 材料明细（改一行即可） |")
    lines.append("")

    if milestones:
        lines.append("## 六、里程碑（来自 milestones.csv）\n")
        lines.append(fmt_table([[m.get("time", ""), m.get("era", ""), m.get("milestone", ""),
                                 m.get("note", "")] for m in milestones],
                               ["时间", "时代", "里程碑", "备注"]))
        lines.append("")

    (data / "report.md").write_text("\n".join(lines), encoding="utf-8")

    with (data / "deltas.csv").open("w", newline="", encoding="utf-8-sig") as fh:
        w = csv.writer(fh)
        w.writerow(["at", "player", "d_mined", "d_used", "d_crafted", "d_killed", "d_move_km",
                    "play_hours_cum"])
        for r in later:
            for uuid, d in r["players"].items():
                w.writerow([r["at"], d["name"], d["mined"], d["used"], d["crafted"],
                            d["killed"], round(d["move_cm"] / 100000, 2), d["play_hours_cum"]])

    print(f"snapshots : {len(snaps)} (comparable intervals: {intervals})")
    print(f"span      : {span_hours:.1f} wall-hours")
    print(f"report    : {data / 'report.md'}")
    print(f"deltas    : {data / 'deltas.csv'}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="Minecraft 复盘快照分析器")
    ap.add_argument("--data", default="data")
    ap.add_argument("--milestones", default="data/milestones.csv")
    return analyze(ap.parse_args())


if __name__ == "__main__":
    raise SystemExit(main())

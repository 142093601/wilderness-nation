"""
自检：造一个合成的世界目录（两个玩家 + 日志），跑两轮采集 + 一次分析，
验证整条链路的**格式处理**是对的。

注意说清楚：这里验证的是"脚本能正确处理这种文件格式"，
**不是**验证真实数据长什么样 —— 真实数据要等你有服务器目录时跑一次才知道。

用法：python _selftest.py
"""

from __future__ import annotations

import json
import shutil
import sys
import tempfile
from pathlib import Path

import analyze_snapshots as an
import collect_snapshot as cs

UUID_A = "11111111222233334444555566667777"
UUID_B = "8888888899990000aaaabbbbccccdddd"


def stats_doc(play_time: int, mined: int, used: int, deaths: int, bell: int, walk: int) -> dict:
    return {
        "stats": {
            "minecraft:custom": {
                # 故意用新键名；采集器同时兼容旧的 play_one_minute
                "minecraft:play_time": play_time,
                "minecraft:deaths": deaths,
                "minecraft:bell_ring": bell,
                "minecraft:walk_one_cm": walk,
                "minecraft:mob_kills": deaths // 2,
                "minecraft:damage_taken": walk // 100,
            },
            "minecraft:mined": {"minecraft:stone": mined, "minecraft:oak_log": mined // 3},
            "minecraft:used": {"minecraft:stone_bricks": used, "minecraft:torch": used // 10},
            "minecraft:crafted": {"minecraft:stick": used // 4},
            "minecraft:killed": {"minecraft:zombie": max(0, deaths - 1)},
        },
        "DataVersion": 3955,
    }


def write_world(root: Path, a: dict, b: dict) -> None:
    world = root / "world"
    (world / "stats").mkdir(parents=True, exist_ok=True)
    (world / "advancements").mkdir(parents=True, exist_ok=True)
    (root / "logs").mkdir(parents=True, exist_ok=True)
    (world / "stats" / f"{UUID_A}.json").write_text(json.dumps(a), encoding="utf-8")
    (world / "stats" / f"{UUID_B}.json").write_text(json.dumps(b), encoding="utf-8")
    (world / "advancements" / f"{UUID_A}.json").write_text(
        json.dumps({"minecraft:story/root": {"done": True},
                    "minecraft:story/mine_stone": {"done": True}}), encoding="utf-8")
    (root / "usercache.json").write_text(json.dumps([
        {"name": "Alice", "uuid": UUID_A, "expiresOn": "2027-01-01 00:00:00 +0000"},
        {"name": "Bob", "uuid": UUID_B, "expiresOn": "2027-01-01 00:00:00 +0000"},
    ]), encoding="utf-8")
    (root / "logs" / "latest.log").write_text(
        "[12:00:00] [Server thread/INFO]: Done (42.315s)! For help, type \"help\"\n"
        "[12:00:10] [Server thread/INFO]: Alice joined the game\n"
        "[12:01:00] [Server thread/INFO]: Bob joined the game\n"
        "[12:20:00] [Server thread/WARN]: Can't keep up! Is the server overloaded?\n"
        "[12:30:00] [Server thread/INFO]: Bob was slain by Zombie\n"
        "[13:00:00] [Server thread/INFO]: Alice left the game\n",
        encoding="utf-8")
    # 故意不写 level.dat —— 验证采集器在缺它时仍能工作
    print(f"fixture: {root}")


def run_cycle(root: Path, out: Path, world_stats: tuple[dict, dict]) -> None:
    argv = sys.argv
    try:
        sys.argv = ["collect_snapshot.py", "--world", str(root / "world"),
                    "--log", str(root / "logs" / "latest.log"), "--out", str(out)]
        cs.main()
    finally:
        sys.argv = argv


def main() -> int:
    tmp = Path(tempfile.mkdtemp(prefix="mc_review_"))
    out = tmp / "data"
    failures: list[str] = []
    try:
        # 第一轮：早期状态
        write_world(tmp, stats_doc(20 * 3600, 500, 200, 1, 0, 50_000),
                    stats_doc(10 * 3600, 200, 90, 2, 1, 20_000))
        run_cycle(tmp, out, (None, None))

        # 第二轮：一段时间之后（数值增长，模拟真的玩了）
        write_world(tmp, stats_doc(20 * 3600 + 20 * 3600, 500 + 1800, 200 + 900, 1, 3, 50_000 + 900_000),
                    stats_doc(10 * 3600 + 4 * 3600, 200 + 100, 90 + 40, 3, 2, 20_000 + 300_000))
        run_cycle(tmp, out, (None, None))

        # 把**最早**那份快照的时间戳往前挪 6 小时，让"区间长度"看起来合理。
        # 注意：不能按文件名排序取"第一份"——Windows 上路径排序大小写不敏感，
        # 带补号的名字可能排在前面（这个坑自检抓到过）。按内容里的 collected_at 排。
        snap_dir = out / "snapshots"
        entries = []
        for p in snap_dir.glob("*.json"):
            doc = json.loads(p.read_text(encoding="utf-8"))
            ts = an.parse_ts(doc.get("collected_at", ""))
            entries.append((ts, p, doc))
        entries.sort(key=lambda e: (e[0] is None, e[0]))
        if len(entries) != 2:
            failures.append(f"expected 2 snapshots, got {len(entries)}")
        else:
            from datetime import timedelta
            (t_first, p_first, doc_first) = entries[0]
            (t_last, _, _) = entries[1]
            if t_first is None or t_last is None:
                failures.append("could not parse collected_at")
            else:
                doc_first["collected_at"] = (t_last - timedelta(hours=6)).isoformat(timespec="seconds")
                p_first.write_text(json.dumps(doc_first, ensure_ascii=False, indent=2), encoding="utf-8")

        # 分析
        argv = sys.argv
        try:
            sys.argv = ["analyze_snapshots.py", "--data", str(out),
                        "--milestones", str(out / "milestones.csv")]
            rc = an.main()
        finally:
            sys.argv = argv
        if rc != 0:
            failures.append(f"analyze returned {rc}")

        report = out / "report.md"
        if not report.is_file():
            failures.append("report.md not produced")
        else:
            text = report.read_text(encoding="utf-8")
            for needle in ("H1 时代节奏", "H7 材料经济", "数据缺口", "活跃度曲线",
                           "Alice", "Bob", "play_time 实际键名"):
                if needle not in text:
                    failures.append(f"report missing: {needle}")

        # 关键字段校验：增量是否算对。
        # 注意 stats_doc 把 mined 同时写进 stone 和 oak_log(mined//3)，所以总量是 mined*4/3 的整数部分。
        exp_mined = (2300 + 2300 // 3) - (500 + 500 // 3)   # = 2400
        deltas_csv = out / "deltas.csv"
        if not deltas_csv.is_file():
            failures.append("deltas.csv not produced")
        else:
            rows = list(csv_rows(deltas_csv))
            alice = [r for r in rows if r["player"] == "Alice"]
            if not alice or int(alice[-1]["d_mined"]) != exp_mined:
                failures.append(f"Alice d_mined wrong (want {exp_mined}): {alice[-1] if alice else None}")

        print("\n--- result ---")
        if failures:
            for f in failures:
                print("FAIL: " + f)
            return 1
        print("PASS: 采集 -> 差分 -> 报告 全链路通过")
        print(f"临时目录（可删）: {tmp}")
        tmp = None  # 成功就留着给用户看
        return 0
    finally:
        pass


def csv_rows(path: Path):
    import csv
    with path.open("r", encoding="utf-8-sig", newline="") as fh:
        for row in csv.DictReader(fh):
            yield row


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""perf_gaps.py -- 找日志里**最大的时间空档**：那几秒到底卡在哪个操作上。

为什么用它
---------
"启动花了 158 秒"这句话没法优化 —— 得知道**哪几步各花了多久**。
而日志的相邻两行之间如果有 8 秒空档，那 8 秒就是被**上一行那个操作**吃掉的
（下一行是它的结果）。所以：算相邻行的时间差，排序，看前后文。

这比"猜哪个 mod 慢"强得多：证据就在日志里，一次运行就能定位。

用法
----
    python tools/perf_gaps.py                          # 默认看实例的 debug.log
    python tools/perf_gaps.py --log <路径> --top 20
    python tools/perf_gaps.py --from 08:35:00 --to 08:38:00    # 只看启动窗口
    python tools/perf_gaps.py --min-gap 2.0            # 只列 >=2 秒的空档
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
TS = re.compile(r"\[(?:\d+月\d+\s+)?(\d{2}):(\d{2}):(\d{2})\.(\d{3})\]")
# 线程名：`[08:41:03] [main/INFO] [...]` 里的第二对方括号
THREAD = re.compile(r"\]\s+\[([^\]]+?)/(?:INFO|WARN|ERROR|DEBUG|TRACE|FATAL)\]")


def secs(m: re.Match) -> float:
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3)) + int(m.group(4)) / 1000.0


def default_log() -> pathlib.Path | None:
    try:
        sys.path.insert(0, str(HERE))
        import pack_paths  # noqa: E402
        d = pathlib.Path(pack_paths.paths()["instance_dir"]) / "logs"
    except Exception:  # noqa: BLE001
        return None
    for name in ("debug.log", "latest.log"):
        p = d / name
        if p.is_file() and p.stat().st_size > 100_000:
            return p
    return None


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="日志时间空档分析（找性能瓶颈）")
    ap.add_argument("--log")
    ap.add_argument("--top", type=int, default=15)
    ap.add_argument("--min-gap", type=float, default=1.0, help="只列大于这个秒数的空档")
    ap.add_argument("--from", dest="t_from", help="只看这个时刻之后（HH:MM:SS）")
    ap.add_argument("--to", dest="t_to", help="只看这个时刻之前（HH:MM:SS）")
    ap.add_argument("--thread", default="",
                    help="**只看这一个线程的相邻行**（推荐 `main` 或 `Render thread`）。"
                         "留空 = 所有行混在一起 —— 那样量出来的'空档'会跨线程，"
                         "把并行工作当成串行成本（我第一版就是这么误判的）。")
    ap.add_argument("--list-threads", action="store_true", help="先看有哪些线程")
    args = ap.parse_args()

    log = pathlib.Path(args.log) if args.log else default_log()
    if not log or not log.is_file():
        print("找不到日志；用 --log 指定")
        return 1
    lines = log.read_text(encoding="utf-8", errors="replace").splitlines()

    def bound(s: str | None) -> float | None:
        if not s:
            return None
        h, m, sec = (int(x) for x in s.split(":"))
        return h * 3600 + m * 60 + sec

    lo, hi = bound(args.t_from), bound(args.t_to)

    rows: list[tuple[float, float, str, str]] = []
    prev: tuple[float, str] | None = None
    for ln in lines:
        m = TS.search(ln)
        if not m:
            continue
        tm = THREAD.search(ln)
        th = tm.group(1) if tm else "?"
        if args.list_threads:
            continue
        if args.thread and th != args.thread:
            # 换了线程就断链：下一条同线程的行才是"它的下一步"
            prev = None
            continue
        cur = secs(m)
        if prev is not None:
            gap = cur - prev[0]
            if gap >= args.min_gap and (lo is None or prev[0] >= lo) and (hi is None or prev[0] <= hi):
                rows.append((gap, prev[0], prev[1], ln))
        prev = (cur, ln)

    if args.list_threads:
        c: dict[str, int] = {}
        for ln in lines:
            tm = THREAD.search(ln)
            if tm:
                c[tm.group(1)] = c.get(tm.group(1), 0) + 1
        print("# 日志里的线程（按行数）")
        for k, v in sorted(c.items(), key=lambda kv: -kv[1])[:16]:
            print(f"  {v:6d}  {k}")
        return 0

    rows.sort(key=lambda r: -r[0])
    total = sum(r[0] for r in rows)
    print(f"# {log.name}（{len(lines)} 行）")
    print(f"共 {len(rows)} 个 ≥{args.min_gap}s 的空档，合计 **{total:.1f} 秒**")
    print()
    print("| 空档 | 起始时刻 | 之前一行（被它吃掉的秒数） | 之后一行 |")
    print("|---|---|---|---|")
    for gap, at, before, after in rows[: args.top]:
        hhmmss = f"{int(at // 3600):02d}:{int(at % 3600 // 60):02d}:{int(at % 60):02d}"
        b = re.sub(r"\[[^\]]*\]\s*", "", before)[:96]
        a = re.sub(r"\[[^\]]*\]\s*", "", after)[:60]
        print(f"| **{gap:.1f}s** | {hhmmss} | {b} | {a} |")
    print()
    print("（'之前一行'就是吃掉这些秒数的操作；'之后一行'是它的结果）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

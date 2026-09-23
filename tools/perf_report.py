#!/usr/bin/env python3
"""perf_report.py -- 从**真机日志**里读出性能基线（加载各阶段耗时 + 卡顿事件）。

为什么要它
---------
用户的目标是"加载速度 + 帧数"。而这两个数字最容易造假的地方是：
  · 拿"感觉快了"当结论（没有前后数字）
  · 拿别人的机器/别人的包当基线
真机日志里有**带时间戳的事实**，先把它榨干再谈优化。

它读什么、报什么
--------------
1. **各阶段耗时**（从日志时间戳算）：JVM → ModLauncher → mod 构造/预初始化/初始化 →
   数据包与配方 → 建世界 → 进世界。**哪一段最贵就优化哪一段。**
2. **`Can't keep up!` 卡顿列表**：这是集成服务端（单人游戏的"服务端线程"）
   单帧超过 50ms 的官方告警。它和"帧数低/一卡一卡"是同一件事的两面，
   而且**带毫秒数与 tick 数**，是量化卡顿最好用的信号。
3. **备份/自动保存的耗时**（SimpleBackups 等）：它们是同步 I/O，经常就是卡顿源。
4. **GC 相关行**（如果 JVM 参数打开了 GC 日志）。

用法
----
    python tools/perf_report.py                        # 自动找最新的日志（实例 latest.log + 归档）
    python tools/perf_report.py --log <路径>           # 指定一个（.log 或 .log.gz 都行）
    python tools/perf_report.py --out data/perf/xxx.md  # 存成报告
"""

from __future__ import annotations

import argparse
import gzip
import pathlib
import re
import sys
import datetime as dt
from collections import Counter

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent

# NeoForge 的时间戳把**日期写在方括号里**：`[229月2026 23:45:19.161]`。
# 第一版写成 `\[(\d{2}:\d{2}:\d{2})` → 一条都匹配不上 → 阶段表全空，
# 而报告看起来只是"这份日志没有启动信息"。**解析失败必须能自己暴露**。
TS = re.compile(r"\[(?:\d+月\d+\s+)?(\d{2}:\d{2}:\d{2})(?:\.(\d{3}))?\]")

KEEPUP = re.compile(r"Can't keep up!.*?Running (\d+)ms or (\d+) ticks behind")
BACKUP = re.compile(r"备份在([\d.]+)s内完成 \(([\d.]+) MB \| ([\d.]+) MB\)")

PHASES = [
    ("JVM 启动",        r"Java Version|Starting: java version|ModLauncher running"),
    ("ModLauncher",     r"ModLauncher running"),
    ("mod 列表加载",     r"Loading (\d+) mods"),
    ("mod 构造",        r"Constructing mods"),
    ("预初始化",         r"Pre-initialization of|Pre-initialization"),
    ("初始化",          r"Initialization of (\d+) mods"),
    ("mod 加载完成",     r"Mod loading took|Loading took (\d+)"),
    ("数据包与配方",      r"Loaded \d+ recipes|Injecting loot tables|Reloading ResourceManager"),
    ("开始建世界",       r"Preparing spawn area"),
    ("进入世界",         r"Starting integrated minecraft server"),
    ("世界就绪",         r"Time elapsed: (\d+) ms"),
    # ⚠️ 这一行才是**玩家体感**的终点：`Time elapsed:` 只是**服务端**把出生区块准备好了，
    # 客户端这时还在"加载地形中..."。实测 r13：世界就绪 11:25:22 → 客户端真正进世界 11:26:18
    # （Xaero `Minimap updated server level id`）/ 11:26:20，**多等 56~58 秒**。
    # ⚠️⚠️ 这个 logger 会打**两次**：启动时装完数据包那次（`Loaded 9803 advancements`，
    # 在 `世界就绪` **之前**）和**真正加入世界**那次（`Loaded 253 advancements`）。
    # 同名的两次匹配只取第一个就会算出**负的间隔** —— 我连踩两次，所以这里显式声明 `last`。
    ("客户端可玩",       r"\[net\.minecraft\.advancements\.AdvancementTree/\]: Loaded \d+ advancements", "last"),
    ("完成",            r"Done \(([\d.]+)s\)!"),
]


def read_log(p: pathlib.Path) -> str:
    if p.suffix == ".gz":
        with gzip.open(p, "rb") as f:
            return f.read().decode("utf-8", errors="replace")
    return p.read_text(encoding="utf-8", errors="replace")


def seconds(s: str) -> float:
    h, m, sec = (int(x) for x in s.split(":"))
    return h * 3600 + m * 60 + sec


def find_default_log() -> pathlib.Path | None:
    try:
        sys.path.insert(0, str(HERE))
        import pack_paths  # noqa: E402
        vd = pathlib.Path(pack_paths.paths()["instance_dir"]) / "logs"
    except Exception:  # noqa: BLE001
        return None
    cands = sorted([p for p in vd.glob("*.log*") if p.stat().st_size > 20000],
                   key=lambda p: p.stat().st_mtime)
    return cands[-1] if cands else (vd / "latest.log" if (vd / "latest.log").is_file() else None)


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="真机日志性能基线")
    ap.add_argument("--log", help="日志路径（.log / .log.gz）")
    ap.add_argument("--out", help="报告输出路径（markdown）")
    ap.add_argument("--gc-log", help="GC 日志（-Xlog:gc* 的产出）；不带这项就量不到 GC")
    args = ap.parse_args()

    log = pathlib.Path(args.log) if args.log else find_default_log()
    if not log or not log.is_file():
        print("找不到日志；用 --log 指定")
        return 1
    text = read_log(log)
    lines = text.splitlines()

    # ---- 0. 先确认这份日志**只含一次会话** ----
    #
    # ⚠️ 2026-09-23 实测踩到：同时跑了两个客户端（一个我的受控启动、一个玩家自己开的），
    # 两个 JVM 往**同一个** `latest.log` 写 → 文件里两段会话交错，时间戳出现**回退**。
    # 后果：阶段耗时算成 277 秒（两段混在一起，没有意义），而"卡顿 433 秒"那条
    # 其实属于另一段会话。更要命的是**机器被两个 JVM 拖到假死**
    # （15.8G 内存、可用一度只剩 1.4G），玩家体感就是"卡住了"。
    # 所以：**日志被污染必须直接拒绝出数**，而不是给一堆看着像真的数字。
    def _sec(ts: str) -> int:
        h, m, s = (int(x) for x in ts.split(":"))
        return h * 3600 + m * 60 + s

    stamps = [m.group(1) for ln in lines if (m := TS.search(ln))]
    regress = [(i, stamps[i - 1], stamps[i]) for i in range(1, len(stamps))
               if _sec(stamps[i]) < _sec(stamps[i - 1]) - 2]
    if regress:
        print(f"!! 这份日志**混了不止一次会话**：时间戳回退 {len(regress)} 次，"
              f"首次在第 {regress[0][0]} 个时间戳（{regress[0][1]} → {regress[0][2]}）。")
        print("!! 多半是同时跑了两个客户端（都写同一个 latest.log）。")
        print("!! **本报告的数字不可用** —— 确认只有一个游戏进程后重新采一次。")
        return 2
    print("（会话检查：只有一次会话，时间戳单调 ✓）")

    out: list[str] = []
    def emit(s: str = "") -> None:
        print(s)
        out.append(s)

    emit(f"# 性能基线 · {log.name}")
    emit(f"（{len(lines)} 行 · {log.stat().st_size / 1024:.0f} KB · "
         f"mtime {dt.datetime.fromtimestamp(log.stat().st_mtime):%Y-%m-%d %H:%M}）")
    emit()

    # ---- 1. 阶段耗时 ----
    emit("## 1. 加载各阶段")
    marks: list[tuple[str, str, str]] = []
    for entry in PHASES:
        # 允许三元组 (label, pattern, "last")：有些 milestone 的同一行会在日志里出现多次
        # （例如 AdvancementTree 的 "Loaded N advancements"：启动一次、真正进世界再一次），
        # 取第一个会算出负的间隔，必须能显式声明"取最后一次"。
        label, pat = entry[0], entry[1]
        pick_last = len(entry) > 2 and entry[2] == "last"
        hits = []
        for ln in lines:
            m = re.search(pat, ln)
            if m:
                ts = TS.search(ln)
                if ts:
                    hits.append((ts.group(1), m.group(0)[:52]))
        if hits:
            ts, ev = hits[-1] if pick_last else hits[0]
            marks.append((label, ts, ev))
    # 去重（同一秒同一标签）
    seen: set[tuple[str, str]] = set()
    marks = [m for m in marks if not (m[0] in seen or seen.add((m[0], m[1])))]  # type: ignore[func-returns-value]
    if marks:
        t0 = seconds(marks[0][1])
        emit("| 阶段 | 时刻 | 距上一阶段 | 距启动 | 证据行 |")
        emit("|---|---|---|---|---|")
        prev = t0
        for label, ts, ev in marks:
            cur = seconds(ts)
            emit(f"| {label} | {ts} | {cur - prev:+.0f}s | {cur - t0:+.0f}s | `{ev}` |")
            prev = cur
        emit()
        emit(f"**从 `{marks[0][0]}` 到 `{marks[-1][0]}` 共 {prev - t0:.0f} 秒。**")
    else:
        emit("（这份日志里没有启动阶段行 —— 多半是只截取了会话尾部）")
    emit()

    # ---- 2. 卡顿 ----
    emit("## 2. 卡顿（`Can't keep up!` = 集成服务端单帧 > 50ms）")
    ku = [(TS.search(ln).group(1), int(m.group(1)), int(m.group(2)))
          for ln in lines if (m := KEEPUP.search(ln)) and TS.search(ln)]
    if not ku:
        emit("没有卡顿告警。")
    else:
        tot = sum(x[1] for x in ku)
        worst = max(ku, key=lambda x: x[1])
        emit(f"共 **{len(ku)} 次** · 累计落后 **{tot / 1000:.1f} 秒** · "
             f"最严重 **{worst[1]}ms / {worst[2]} ticks**（{worst[0]}）")
        emit()
        emit("| 时刻 | 落后(ms) | ticks |")
        emit("|---|---|---|")
        for ts, ms, tk in ku:
            emit(f"| {ts} | {ms} | {tk} |")
    emit()

    # ---- 3. 备份/保存 ----
    emit("## 3. 备份与保存（同步 I/O，常是卡顿源）")
    bks = [m.groups() for ln in lines if (m := BACKUP.search(ln))]
    if bks:
        for s, a, b in bks:
            emit(f"- 备份耗时 **{s}s**（{a} MB → {b} MB）")
    else:
        emit("（这份日志里没有备份完成行）")
    emit()

    # ---- 4. GC / 内存 ----
    emit("## 4. GC 与内存")
    gc_path = pathlib.Path(args.gc_log) if args.gc_log else None
    if gc_path and gc_path.is_file():
        gl = gc_path.read_text(encoding="utf-8", errors="replace").splitlines()
        pat = re.compile(r"Pause (\w[\w ()]*?)\s+(\d+)M->(\d+)M\((\d+)M\)\s+([\d.]+)ms")
        ps = [(m.group(1).strip(), int(m.group(2)), int(m.group(3)), int(m.group(4)),
               float(m.group(5))) for ln in gl if (m := pat.search(ln))]
        if ps:
            tot = sum(p[4] for p in ps) / 1000.0
            worst = max(ps, key=lambda p: p[4])
            emit(f"**{len(ps)} 次暂停 · 累计 {tot:.2f} 秒 · 最大 {worst[4]:.0f} ms**"
                 f"（堆上限 {max(p[3] for p in ps)} MB）")
            emit()
            kinds = Counter(p[0] for p in ps)
            emit("| 暂停类型 | 次数 |")
            emit("|---|---|")
            for k, v in kinds.most_common(6):
                emit(f"| {k} | {v} |")
            emit()
            emit("最长 5 次：")
            emit()
            emit("| 类型 | 前 | 后 | 上限 | 耗时 |")
            emit("|---|---|---|---|---|")
            for k, a, b, mx, ms in sorted(ps, key=lambda p: -p[4])[:5]:
                emit(f"| {k} | {a}M | {b}M | {mx}M | **{ms:.0f} ms** |")
            big = sum(1 for p in ps if p[4] >= 100)
            emit()
            emit(f"其中 **≥100ms 的暂停 {big} 次**（这些就是玩家能感觉到的顿挫）。")
        else:
            emit("（GC 日志里没有 Pause 行）")
    else:
        emit("（没有给 `--gc-log` 或文件不存在 —— 这一项只能靠猜，所以**必须**开 GC 日志："
             "`tools/perf_baseline.py` 会自动带上）")
    emit()

    # ---- 5. 高频 WARN/ERROR 排行（找"一直在报错"的 mod）----
    emit("## 5. 高频告警/报错（按 mod 归并，前 10）")
    c: Counter = Counter()
    for ln in lines:
        if " WARN " in ln or " ERROR " in ln:
            m = re.search(r"\[([a-zA-Z0-9_.]+)/[A-Z]+\]", ln) or re.search(r"\[([a-zA-Z0-9_.]+)\]", ln)
            if m:
                c[m.group(1).split(".")[-1]] += 1
    if c:
        emit("| 来源 | 条数 |")
        emit("|---|---|")
        for k, v in c.most_common(10):
            emit(f"| {k} | {v} |")
    else:
        emit("（没有 WARN/ERROR）")

    if args.out:
        p = pathlib.Path(args.out)
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("\n".join(out) + "\n", encoding="utf-8")
        print(f"\n-> {p}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

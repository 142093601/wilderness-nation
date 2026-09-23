#!/usr/bin/env python3
"""jfr_hotspots.py —— 从 JFR 录制里算**热点方法**（JDK 21 没有 `jfr view hot-methods`）。

为什么需要它
------------
`jfr view` 在 JDK 21 只有 JVM 级视图（gc-* / compiler-* / safepoints…），**没有方法热点视图**
（那是 JDK 22+ 的 `hot-methods`）。而 `jfr print --json` 的输出是**打包成 values 数组**的格式，
实测这份 31 MB 的录制导出了 **2 GB** JSON —— 不能用。

所以这里**流式解析 `jfr print` 的文本输出**（边读边聚合，不落临时文件、不吃内存）：

    jdk.ExecutionSample {
      startTime = 11:35:35.226 (2026-09-23)
      sampledThread = "main" (javaThreadId = 1)
      stackTrace = [
        <栈顶帧> line: N        ← 这一行才是"正在跑的代码"
        ...
      ]
    }

用法
----
    python tools/jfr_hotspots.py <file.jfr>                      # 全场 top 20（并给每线程 top5）
    python tools/jfr_hotspots.py <file.jfr> --window 164 300      # 只看录制开始后 164~300 秒
    python tools/jfr_hotspots.py <file.jfr> --thread "Server thread"
    python tools/jfr_hotspots.py <file.jfr> --mode total          # 计"出现在栈里"（看调用链，不是耗时）

口径（重要）
------------
* 默认 `--mode self` 只数**栈顶帧** —— 这才是"谁在吃 CPU"；`total` 会把调用者也数一遍。
* 时间窗零点 = **第一条采样**（本项目录制从 JVM 启动开始），可以直接用
  `perf_report.py` 报的"距 JVM 启动"秒数来切。
* **JFR 本身会拖慢启动**（实测 r14 比同期基线慢约 18%）⇒ 只拿它做**归因**，别当性能基线。
"""

from __future__ import annotations

import argparse
import collections
import pathlib
import re
import subprocess
import sys

JFR = r"C:\Program Files\Java\jdk-21.0.10\bin\jfr.exe"
TIME_RE = re.compile(r"startTime = (\d{2}):(\d{2}):(\d{2})\.(\d{3})")
THREAD_RE = re.compile(r'sampledThread = "([^"]+)"')


def short(frame: str) -> str:
    """把 `a.b.C.m(int, long) line: 12` 压成 `C.m`。"""
    head = frame.split("(")[0].strip()
    parts = [p for p in head.split(".") if p]
    return ".".join(parts[-2:]) if len(parts) >= 2 else head


def stream_samples(path: pathlib.Path, depth: int):
    cmd = [JFR, "print", "--events", "jdk.ExecutionSample", "--stack-depth", str(depth), str(path)]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                            text=True, encoding="utf-8", errors="replace")
    assert proc.stdout is not None
    t: float | None = None
    thread = "?"
    frames: list[str] = []
    in_stack = False
    for line in proc.stdout:
        s = line.strip()
        if s.startswith("jdk.ExecutionSample {"):
            t, thread, frames, in_stack = None, "?", [], False
        elif s.startswith("startTime ="):
            m = TIME_RE.search(s)
            if m:
                t = int(m.group(1)) * 3600 + int(m.group(2)) * 60 + int(m.group(3)) + int(m.group(4)) / 1000
        elif s.startswith("sampledThread ="):
            m = THREAD_RE.search(s)
            thread = m.group(1) if m else "?"
        elif s.startswith("stackTrace = ["):
            in_stack = True
        elif in_stack and s == "]":
            in_stack = False
            if t is not None and frames:
                yield t, thread, frames
        elif in_stack and s and s != "...":
            frames.append(s)
    proc.stdout.close()
    proc.wait()


def main() -> int:
    ap = argparse.ArgumentParser(description="JFR 热点方法聚合（流式解析 jfr print 文本）")
    ap.add_argument("jfr_file")
    ap.add_argument("--window", nargs=2, type=float, metavar=("LO", "HI"),
                    help="只看录制开始后 LO~HI 秒")
    ap.add_argument("--thread", default="", help="只看这个线程（子串匹配）")
    ap.add_argument("--mode", choices=["self", "total"], default="self")
    ap.add_argument("--top", type=int, default=20)
    ap.add_argument("--depth", type=int, default=24)
    args = ap.parse_args()

    src = pathlib.Path(args.jfr_file)
    if not src.is_file():
        print(f"找不到 {src}")
        return 1

    t0: float | None = None
    self_c: collections.Counter[str] = collections.Counter()
    total_c: collections.Counter[str] = collections.Counter()
    per_thread: dict[str, collections.Counter[str]] = collections.defaultdict(collections.Counter)
    used = 0
    seen_total = 0
    for t, thread, frames in stream_samples(src, args.depth):
        if t0 is None:
            t0 = t
            print(f"# 采样起点 {int(t // 3600):02d}:{int(t % 3600 // 60):02d}:{int(t % 60):02d}"
                  f"（窗口的 0 秒）", file=sys.stderr)
        seen_total += 1
        off = t - (t0 or t)
        if args.window and not (args.window[0] <= off <= args.window[1]):
            continue
        if args.thread and args.thread.lower() not in thread.lower():
            continue
        used += 1
        top = short(frames[0])
        self_c[top] += 1
        per_thread[thread][top] += 1
        if args.mode == "total":
            for nm in dict.fromkeys(short(f) for f in frames):
                total_c[nm] += 1

    counter = total_c if args.mode == "total" else self_c
    scope = f"窗口 {args.window[0]:.0f}~{args.window[1]:.0f}s" if args.window else "全场"
    who = f"，线程≈{args.thread}" if args.thread else ""
    print(f"# JFR 热点（{args.mode}）· {src.name} · {scope}{who}")
    print(f"（本次统计用了 {used} / {seen_total} 个采样）\n")
    print("| 排名 | 方法（栈顶 = self） | 采样数 | 占比 |")
    print("|---|---|---:|---:|")
    for i, (nm, c) in enumerate(counter.most_common(args.top), 1):
        print(f"| {i} | `{nm}` | {c} | {100.0 * c / max(used, 1):.1f}% |")
    if not args.thread and per_thread:
        print("\n## 按线程 top 5")
        for th, cnt in sorted(per_thread.items(), key=lambda kv: -sum(kv[1].values()))[:6]:
            tot = sum(cnt.values())
            tops = "、".join(f"{n}({100.0 * c / tot:.0f}%)" for n, c in cnt.most_common(5))
            print(f"- **{th}**（{tot} 采样）：{tops}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

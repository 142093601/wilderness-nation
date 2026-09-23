#!/usr/bin/env python3
"""perf_baseline.py -- 跑一次**受控**启动，产出可对比的性能基线。

为什么不能只看用户那次会话的日志
--------------------------------
用户那次是**手工**玩的：启动后有 388 秒的"主菜单 / 选存档"时间，
把它算成"加载耗时"是错的（那不是游戏的加载，是人的操作）。
受控启动用 `--quickPlaySingleplayer` 直接进世界 → 拿到的是**纯游戏耗时**。

它测什么、和谁比
--------------
1. **启动**：JVM → 主菜单（资源管理器重载）
2. **进世界**：integrated server 起 → `Time elapsed`（世界就绪）
3. **卡顿**：`Can't keep up!` 的次数与累计落后毫秒（集成服务端单帧 > 50ms）
4. **GC**：这一版才有的 —— 用 `-Xlog:gc*` 把 GC 日志写到 `data/perf/gc-<label>.log`，
   然后由 `perf_report.py` 汇总。**没有 GC 日志，"卡顿是不是 GC 造成的"就只能靠猜。**

用法
----
    python tools/perf_baseline.py --label base-8G --xmx 8G
    python tools/perf_baseline.py --label aikar-8G --xmx 8G --aikar
    python tools/perf_baseline.py --label base-8G --xmx 8G --seconds 180

每次都写：`data/perf/<label>.md`（报告）+ `data/perf/gc-<label>.log`（GC 原始日志）
+ `data/perf/<label>.json`（可比对的数字）。**优化前后各跑一次就是前后对比。**
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import subprocess
import sys
import time

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parent
PERF = ROOT / "data" / "perf"


def _java_processes() -> list[str]:
    """列出正在跑的 **Minecraft 客户端**（用来挡住"两个客户端同时跑"）。

    ⚠️ 第一版只按进程名数 `javaw`/`java`，于是**机器上任何别的 JVM 都会挡住基线** ——
    实测：一个不相干的 `-XX:+UseSerialGC` 进程让 Aikar 那次对照直接拒绝启动。
    判据必须落在"它是不是 Minecraft"上，用命令行特征：
      · `--gameDir`（启动器一定会传）
      · `forgeclient` / `quickPlaySingleplayer`（NeoForge 客户端与我这条链路一定会带）
    别的 JVM（Gradle daemon、其它工具）**不挡**。
    """
    try:
        r = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "Get-CimInstance Win32_Process -Filter \"Name='java.exe' OR Name='javaw.exe'\" | "
             "ForEach-Object { \"$($_.ProcessId)|$([int]($_.WorkingSet64/1MB))|$($_.CommandLine)\" }"],
            capture_output=True, text=True, timeout=40)
    except Exception:  # noqa: BLE001
        return []
    out: list[str] = []
    for ln in (r.stdout or "").splitlines():
        ln = ln.strip()
        if not ln:
            continue
        parts = ln.split("|", 2)
        if len(parts) < 3:
            continue
        pid, mb, cmd = parts
        if any(k in cmd for k in ("forgeclient", "--gameDir", "quickPlaySingleplayer")):
            out.append(f"{pid}:{mb}MB")
    return out


def _version_dir() -> pathlib.Path:
    sys.path.insert(0, str(HERE))
    import pack_paths  # noqa: E402
    return pathlib.Path(pack_paths.paths()["instance_dir"])


VERSION_DIR = _version_dir()

# Aikar's flags（Minecraft 社区针对 G1GC 的常用调参，2026 年仍是主流基线）
AIKAR = [
    "-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200",
    "-XX:+UnlockExperimentalVMOptions", "-XX:+DisableExplicitGC",
    "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40",
    "-XX:G1HeapRegionSize=8M", "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5",
    "-XX:G1MixedGCCountTarget=4", "-XX:InitiatingHeapOccupancyPercent=15",
    "-XX:G1MixedGCLiveThresholdPercent=90", "-XX:G1RSetUpdatingPauseTimePercent=5",
    "-XX:SurvivorRatio=32", "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
]

KEEPUP = re.compile(r"Can't keep up!.*?Running (\d+)ms or (\d+) ticks behind")


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="受控性能基线")
    ap.add_argument("--label", required=True, help="这次基线的名字（也当文件名）")
    ap.add_argument("--xmx", default="8G")
    ap.add_argument("--seconds", type=int, default=150, help="进世界后停留秒数")
    ap.add_argument("--world", default="scdev", help="**测试存档**（绝不用玩家的存档）")
    ap.add_argument("--aikar", action="store_true", help="加上 Aikar's flags")
    ap.add_argument("--extra", default="", help="额外 JVM 参数（空格分隔）")
    # 窗口分辨率：做 **FPS** 对照时必须固定画面像素数。
    # 854x480 下 GPU 只有三成占用（实测 F3），帧数瓶颈全在 CPU —— 在那上面测
    # "Fabulous→Fancy 省多少帧"只会得到"几乎没差"的假结论。
    # 留空 = 沿用 autotest 的 854x480（启动耗时对照的老口径）。
    ap.add_argument("--width", default="", help="窗口宽（留空=854）")
    ap.add_argument("--height", default="", help="窗口高（留空=480）")
    args = ap.parse_args()

    PERF.mkdir(parents=True, exist_ok=True)

    # ---- 前置闸门：**同时只能有一个客户端** ----
    #
    # 2026-09-23 实测踩到：我的受控启动与另一个客户端同时跑，
    # 两个 JVM 往同一个 latest.log 写 → 日志出现两段会话交错、时间戳回退，
    # 数字全部不可用；而且 15.8G 内存被两个 JVM 顶满（可用一度只剩 1.4G），
    # 机器被拖到假死 —— 用户的原话就是"似乎卡住了"。
    # 所以启动前必须先数进程，**有别的 java/javaw 就不启动**。
    others = _java_processes()
    if others:
        print(f"!! 检测到 {len(others)} 个 java/javaw 进程，拒绝启动：{others}")
        print("!! 两个客户端会互相污染日志并把内存顶满（这正是一次'卡住'的原因）。")
        print("!! 请先关掉其它游戏实例，再重跑基线。")
        return 3

    # GC 日志用**相对路径**（相对实例目录）：
    # ⚠️ `-Xlog` 的字段用冒号分隔，而 Windows 盘符 `D:` **本身是个冒号** ——
    # 写绝对路径会被当成多出来的字段，JVM 报
    # `Invalid decorator 'filecount=3'`，**GC 日志一条都写不出来**。
    # （我第一版就是这么写的，跑完发现 gc-*.log 是 0 字节。）
    gc_rel = f"logs/gc-{args.label}.log"
    gc_abs = VERSION_DIR / gc_rel
    if gc_abs.exists():
        gc_abs.unlink()

    jvm: list[str] = [
        f"-Xlog:gc*:file={gc_rel}:time,uptime,level,tags:filecount=3,filesize=8M",
    ]
    if args.aikar:
        jvm += AIKAR
    jvm += [a for a in args.extra.split() if a]

    cmd = [sys.executable, "tools/autotest.py",
           "--world", args.world, "--xmx", args.xmx,
           "--seconds", str(args.seconds),
           # ⚠️ 必须用 `--jvm-args=...` 的**等号形式**：值以 `-` 开头（`-Xlog:…`），
           # 用空格分隔时 argparse 会把它当成另一个选项，报
           # "argument --jvm-args: expected one argument"（第一次跑就踩了）。
           "--jvm-args=" + " ".join(jvm)]
    if args.width and args.height:
        cmd += ["--width", args.width, "--height", args.height]
        print(f"窗口：{args.width}x{args.height}")
    print("受控启动：", " ".join(cmd[:8]), "…")
    t0 = time.time()
    r = subprocess.run(cmd, cwd=str(ROOT), text=True, encoding="utf-8", errors="replace",
                       capture_output=True)
    took = time.time() - t0
    tail = [l for l in ((r.stdout or "") + (r.stderr or "")).splitlines() if l.strip()][-6:]
    print(f"autotest 退出码 {r.returncode}（耗时 {took:.0f}s）")
    for l in tail:
        print("  | " + l[:170])
    if r.returncode != 0:
        print("启动/进世界失败 —— 基线无效，先修启动问题")
        return 1

    # ---- 后置闸门：GC 日志必须真的有内容 ----
    gc_log = PERF / f"gc-{args.label}.log"
    if gc_abs.is_file() and gc_abs.stat().st_size > 0:
        import shutil
        shutil.copyfile(gc_abs, gc_log)
        print(f"GC 日志 {gc_log.stat().st_size / 1024:.0f} KB ✓")
    else:
        print("!! GC 日志是空的 —— '卡顿是不是 GC 造成的'这一项**没有证据**，"
              "别在报告里当成'没有 GC 问题'。")

    # 用同一个报告器解析（它自己会拒绝被污染的日志并退 2）
    rep = PERF / f"{args.label}.md"
    rr = subprocess.run([sys.executable, "tools/perf_report.py",
                         "--log", str(VERSION_DIR / "logs" / "latest.log"),
                         "--gc-log", str(gc_log), "--out", str(rep)],
                        cwd=str(ROOT))
    if rr.returncode == 2:
        print("!! 日志被污染（混了多个会话）→ 本次基线作废")
        return 2

    # 抽成可比的 JSON
    text = rep.read_text(encoding="utf-8") if rep.is_file() else ""
    m = re.search(r"共 \*\*(\d+) 次\*\* · 累计落后 \*\*([\d.]+) 秒\*\* · 最严重 \*\*(\d+)ms", text)

    # 加载阶段用**可自证**的口径：全部来自日志时间戳（JVM 第一行 → 各里程碑）。
    # 不用 ModernFix 那几句 "Game took / Time from main menu / Total time" ——
    # 它们的计时起点我在日志里看不到，2026-09-23 就是引用它们把一次 132 秒的会话
    # 记成了 221 秒（见 DESIGN-PERF §四之四）。
    phases: dict[str, int] = {}
    for name, cum in re.findall(
            r"\| (数据包与配方|进入世界|世界就绪|客户端可玩) \| [0-9:]+ \| \+\d+s \| \*{0,2}\+(\d+)s", text):
        phases[name] = int(cum)
    # 「世界就绪」那一列的"距启动"就是全程耗时 —— 不再去正文里抠一句话
    # （第一版抠的是 `共 **143** 秒`，可实际排版是整句加粗 `**从…共 143 秒。**`，
    #  于是永远匹配不到、JSON 里静默留空。锚在表格列上就不会再错。）
    if "世界就绪" in phases:
        phases["合计"] = phases["世界就绪"]

    stats = {
        "label": args.label,
        "xmx": args.xmx,
        "aikar": args.aikar,
        "world": args.world,
        "seconds": args.seconds,
        "width": args.width or "854",
        "height": args.height or "480",
        "phases": phases,
        "stalls": int(m.group(1)) if m else 0,
        "stall_seconds": float(m.group(2)) if m else 0.0,
        "worst_stall_ms": int(m.group(3)) if m else 0,
        "report": str(rep.relative_to(ROOT)),
        "gc_log": str(gc_log.relative_to(ROOT)),
    }
    (PERF / f"{args.label}.json").write_text(
        json.dumps(stats, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n本次基线：卡顿 {stats['stalls']} 次 · 累计 {stats['stall_seconds']}s · "
          f"最严重 {stats['worst_stall_ms']}ms")
    if phases:
        print("加载阶段（日志时间戳口径，距 JVM 启动）："
              + " · ".join(f"{k} +{v}s" for k, v in phases.items()))
    print(f"-> {rep}  ·  {gc_log}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

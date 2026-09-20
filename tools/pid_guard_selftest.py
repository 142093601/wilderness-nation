"""清场逻辑的回归测试 —— 保证"只杀自己启动的 JVM"这条线不会再断。

事故背景（2026-09-20，见 INCIDENTS.md）：
    清场用的 `taskkill /F /IM javaw.exe` 按**镜像名**杀进程，会把玩家正在玩的游戏一起杀掉。
    被强杀的 JVM 不写崩溃报告，玩家看到的是"游戏崩溃但未生成崩溃报告"。

本测试验三件事：
    1. pid 文件里的启动时刻对不上（pid 被复用）→ 一个都不许杀；
    2. pid 文件是我们自己写的、进程确实是我们的 → 要杀掉；
    3. 全程不许碰无关的 JVM（比如 Gradle 守护进程）。

用法：python tools/pid_guard_selftest.py          只跑第 1、3 项（不用起服务端）
      python tools/pid_guard_selftest.py --with-server   额外跑第 2 项（会真起一次服务端）
"""

from __future__ import annotations

import argparse
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import paired_check as pc  # noqa: E402  同目录模块


def java_pids() -> set:
    r = subprocess.run(["powershell", "-NoProfile", "-Command",
                        "Get-Process java,javaw -ErrorAction SilentlyContinue | "
                        "ForEach-Object { $_.Id }"],
                       capture_output=True, text=True, timeout=60)
    return {int(x) for x in (r.stdout or "").split() if x.strip().isdigit()}


def creation_epoch(pid: int) -> int:
    r = subprocess.run(["powershell", "-NoProfile", "-Command",
                        "[int][double]::Parse((Get-Date (Get-CimInstance Win32_Process "
                        "-Filter \"ProcessId=%d\").CreationDate).ToUniversalTime()"
                        ".Subtract([datetime]'1970-01-01').TotalSeconds)" % pid],
                       capture_output=True, text=True, timeout=60)
    try:
        return int((r.stdout or "").strip())
    except ValueError:
        return 0


def pid_from(raw: str) -> int:
    try:
        return int(raw.split("\t")[0].strip())
    except Exception:  # noqa: BLE001
        return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--with-server", action="store_true")
    args = ap.parse_args()

    fails = []
    before = java_pids()
    others = {p for p in before if p != 0}
    if not others:
        print("!! 机器上一个无关 java 进程都没有，第 3 项测不了（先跑一次构建再测）")
    print("测试前无关 java 进程: %s" % (sorted(others) or "无"))

    # ── 第 1 项：pid 复用必须拦住 ────────────────────────────────────────────
    if others:
        victim = sorted(others)[0]
        pc.CLIENT_PID_FILE.write_text("%d\t%d" % (victim, int(time.time())), encoding="utf-8")
        got = pc._own_pid(pc.CLIENT_PID_FILE)
        if got is None:
            print("  [1] pid 复用拦截 OK（进程 %d 启动于 %d 秒前，不肯认）"
                  % (victim, int(time.time()) - creation_epoch(victim)))
        else:
            fails.append("第 1 项失败：竟把无关进程 %d 认成自己的" % victim)
        pc.CLIENT_PID_FILE.unlink(missing_ok=True)

    # ── 第 3 项：pid 文件缺失时什么都不动 ──────────────────────────────────
    pc.stop_own_jvms()
    after = java_pids()
    killed = others - after
    if killed:
        fails.append("第 3 项失败：没登记也把无关 JVM %s 杀了" % sorted(killed))
    else:
        print("  [3] 未登记的 JVM 一个没动 OK")

    # ── 第 2 项：自己的服务端要能停掉 ──────────────────────────────────────
    if args.with_server:
        print("起服务端 ...")
        # 用 Popen 不等它就绪：我们要测的是"能不能停掉自己启动的 JVM"，
        # 不是等服务端跑完（等它要几分钟）。pid 文件在 Popen 之后马上写。
        try:
            subprocess.Popen([sys.executable, "-u", str(Path(pc.__file__).parent / "server_ctl.py"),
                              "--start"], cwd=str(pc.ROOT),
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            deadline = time.time() + 120
            while time.time() < deadline and not pc.SERVER_PID_FILE.is_file():
                time.sleep(2)
            if not pc.SERVER_PID_FILE.is_file():
                fails.append("第 2 项失败：服务端起来了却没写 pid 文件")
            else:
                raw = pc.SERVER_PID_FILE.read_text(encoding="utf-8").strip()
                # 回归：锚点必须是**进程创建时刻**，不能是"写文件时刻"。
                # 踩过：服务端就绪后才登记（晚约 1 分钟），拿写文件时刻当锚点 → 自己的服务端被误判 REUSED。
                _, _, stamp = raw.partition("\t")
                if pid_from(raw) and stamp:
                    real = creation_epoch(pid_from(raw))
                    if abs(int(stamp) - real) > 5:
                        fails.append("第 2 项失败：锚点不是进程创建时刻（登记 %s，实际 %d）" % (stamp, real))
                pid = pc._own_pid(pc.SERVER_PID_FILE)
                print("  .server.pid = %r（认领结果 %s）" % (raw, pid))
                if pid is None:
                    fails.append("第 2 项失败：自己的服务端 pid 没认出来（登记 %r）" % raw)
                else:
                    pc.stop_own_jvms()
                    time.sleep(3)
                    if pid in java_pids():
                        fails.append("第 2 项失败：服务端 %d 还活着" % pid)
                    else:
                        print("  [2] 自己的服务端已停 OK")
                    still = others - java_pids()
                    if still:
                        fails.append("第 2 项失败：顺带杀了无关 JVM %s" % sorted(still))
        finally:
            # 测试失败也不能把服务端留在后台：上一次失败就留下了一个游离服务端，
            # 它占着 RCON 端口，害得下一次测试根本起不来（真踩过）。
            pc.stop_own_jvms()

    print()
    if fails:
        for f in fails:
            print("FAIL " + f)
        return 1
    print("PASS 清场逻辑只动自己登记的 JVM")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

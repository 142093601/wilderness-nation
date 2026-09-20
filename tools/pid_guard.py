"""自有 JVM 的 pid 登记与认领 —— 一个地方决定"哪些进程是我们的"。

为什么要有这个模块（真事故，见 INCIDENTS.md 2026-09-20）：
    清场脚本用 `taskkill /F /IM javaw.exe` **按镜像名**杀进程，于是把玩家正在玩的游戏
    一起杀了。被强杀的 JVM 不写崩溃报告，玩家那边只看到"游戏崩溃但未生成崩溃报告"，
    我这边却查无实据 —— 两头都被误导。

现在的铁律：
    * 只停**自己登记过**的 pid（启动时写文件，退出时删文件）；
    * 认领前验两件事 —— 命令行里含 java（pid 可能落到别的程序上），以及进程创建时刻与
      登记时刻一致（pid 会被系统复用）；
    * 验不过就**什么都不做**，宁可让清场失败报出来，也不猜。

锚点为什么用"进程创建时刻"而不是"写文件时刻"：
    服务端是**就绪之后**才登记的（比进程创建晚约 1 分钟）。若拿写文件时刻当锚点，
    自己的服务端会被误判成"pid 已复用"而不敢杀 —— 这个 bug 在 selftest 里真出现过。

文件格式：`pid<TAB>进程创建时刻(epoch 秒)`。单列旧格式（只有 pid）仍能读，只是不查复用。
"""

from __future__ import annotations

import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
CLIENT_PID_FILE = ROOT / ".client.pid"
SERVER_PID_FILE = ROOT / "server" / ".server.pid"

# 进程创建时刻与登记时刻允许的偏差（秒）：两者都在本机取，正常误差 < 1 秒。
_TOLERANCE = 5

_PS_PROBE = (
    "$p = Get-CimInstance Win32_Process -Filter \"ProcessId=%d\" -ErrorAction SilentlyContinue; "
    "if (-not $p) { 'GONE 0' } "
    "elseif ($p.CommandLine -notmatch 'java') { 'NOTJAVA 0' } "
    "else { 'OK ' + [int][double]::Parse((Get-Date $p.CreationDate).ToUniversalTime()"
    ".Subtract([datetime]'1970-01-01').TotalSeconds) }"
)


def probe(pid: int) -> tuple:
    """看一个 pid：('OK'|'GONE'|'NOTJAVA', 创建时刻 epoch；非 OK 时为 0)。"""
    try:
        r = subprocess.run(["powershell", "-NoProfile", "-Command", _PS_PROBE % pid],
                           capture_output=True, text=True, timeout=60)
        line = (r.stdout or "").strip().splitlines()[-1] if r.stdout.strip() else "? 0"
        verdict, _, epoch = line.partition(" ")
        return verdict, int(epoch or 0)
    except Exception:  # noqa: BLE001
        return "?", 0


def write(pid_file: Path, pid: int) -> None:
    """登记自己启动的 pid，锚点取进程真实创建时刻（取不到就退化成当前时刻）。"""
    verdict, created = probe(pid)
    stamp = created if verdict == "OK" and created else int(time.time())
    try:
        Path(pid_file).write_text("%d\t%d" % (pid, stamp), encoding="utf-8")
    except Exception:  # noqa: BLE001
        pass


def clear(pid_file: Path) -> None:
    try:
        Path(pid_file).unlink(missing_ok=True)
    except Exception:  # noqa: BLE001
        pass


def read_pid(pid_file: Path) -> str:
    """读 pid 字符串（兼容单列旧格式、兼容已删文件），失败给空串。"""
    try:
        return Path(pid_file).read_text(encoding="utf-8").split("\t")[0].strip()
    except Exception:  # noqa: BLE001
        return ""


def claim(pid_file: Path, verbose: bool = True) -> int | None:
    """认领 pid 文件：确实是我们启动的那个活着的 JVM → 返回 pid，否则 None（并删掉废文件）。"""
    pid_file = Path(pid_file)
    try:
        raw = pid_file.read_text(encoding="utf-8").strip()
        pid_s, stamp_s = (raw.split("\t") + [""])[:2]
        pid = int(pid_s)
        # 单列旧格式没有锚点，只验"是 java"，不查复用
        recorded = int(stamp_s) if stamp_s else None
    except Exception:  # noqa: BLE001
        return None
    verdict, created = probe(pid)
    if verdict == "OK" and recorded is not None and abs(created - recorded) > _TOLERANCE:
        verdict = "REUSED"
    if verdict != "OK":
        if verbose and verdict != "GONE":
            print("  pid 文件 %s 已失效（%s）→ 不杀任何进程" % (pid_file.name, verdict))
        clear(pid_file)
        return None
    return pid


def stop_own(pid_files: tuple = (CLIENT_PID_FILE, SERVER_PID_FILE), verbose: bool = True) -> list:
    """停掉自己登记过的 JVM：先礼（WM_CLOSE，让游戏存世界）后兵（/F /T）。"""
    stopped = []
    for f in pid_files:
        if not Path(f).is_file():
            continue
        pid = claim(f, verbose=verbose)
        if pid is None:
            continue
        subprocess.run(["taskkill", "/PID", str(pid)], capture_output=True)
        time.sleep(4)
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], capture_output=True)
        stopped.append(pid)
        clear(f)
    if stopped and verbose:
        print("  已停掉自己启动的 JVM：%s" % ", ".join(str(p) for p in stopped))
    time.sleep(2)
    return stopped

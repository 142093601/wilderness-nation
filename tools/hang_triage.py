#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""挂起取证：对卡住的 Minecraft 进程取 jstack 线程转储，并标出阻塞在 mod 代码的线程。

为什么需要单独一个工具：**"崩溃"和"卡死"是两类问题，取证手段完全不同**。
  · 崩溃 → 有 crash-report / 异常栈 → 看日志（现有 crashlog-triage 覆盖这一类）
  · 卡死 → 没有异常、没有崩溃报告、Windows 事件日志也没有记录 → **只能问 JVM 要线程转储**
本包 2026-09-19 实测就撞上了后者：60 个 mod 全部加载无异常、到主菜单 43.9 秒，
但 quickPlay 开世界时卡住，`latest.log` 里既没有 "Starting integrated minecraft server" 也没有 "Stopping"。

用法：
  python tools/hang_triage.py                 # 找正在跑的 Minecraft 进程 → jstack → 分析
  python tools/hang_triage.py --wait 60       # 先等 60 秒（等它卡稳）再取
  python tools/hang_triage.py --pid 12345     # 指定 pid
  python tools/hang_triage.py --list          # 只列出候选进程
  python tools/hang_triage.py --dump <文件>   # 不联网/不取转储，直接分析已有转储

判定思路：jstack 转储里绝大多数线程是**正常挂起**（线程池 parked、GC、JIT、Netty）。
真正有用的是：① 主线程/Render thread/Server thread 的栈；② 栈里出现 **mod 自己的类** 的线程。
所以本工具给每个线程算一个"mod 帧数"（排除 JDK / Minecraft / 加载器 / 常见库），按它排序。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 不算"mod 帧"的包前缀（JDK / Minecraft / 加载器 / 常见第三方库）
BENIGN = (
    "java.", "javax.", "jdk.", "sun.", "com.sun.",
    "net.minecraft.", "net.neoforged.", "cpw.mods.", "net.minecraftforge.",
    "com.mojang.", "org.lwjgl.", "org.slf4j.", "org.apache.", "com.google.",
    "io.netty.", "it.unimi.dsi.", "org.jetbrains.", "com.electronwill.",
    "net.jodah.", "org.objectweb.", "org.spongepowered.", "com.mojang.",
    "org.joml.", "com.github.", "org.openjdk.", "oshi.", "com.ezylang.",
)

THREAD_RE = re.compile(r'^"(?P<name>[^"]+)"\s+#\d+.*?(?:nid=(?P<nid>\S+))?.*$')
STATE_RE = re.compile(r"java\.lang\.Thread\.State:\s*(?P<state>\S+)")
FRAME_RE = re.compile(r"^\s+at\s+(?P<frame>[\w$.<>/]+)")


def java_bin_dir() -> Path | None:
    """从 pack.local.json 的 java 路径推出 jstack 所在目录。"""
    cfg = ROOT / "tools" / "pack.local.json"
    if cfg.is_file():
        try:
            j = json.loads(cfg.read_text(encoding="utf-8")).get("java")
            if j:
                return Path(j).parent
        except Exception:  # noqa: BLE001
            pass
    return None


def find_jstack() -> str | None:
    d = java_bin_dir()
    if d:
        for name in ("jstack.exe", "jstack"):
            p = d / name
            if p.is_file():
                return str(p)
    # 退一步：PATH 上找
    from shutil import which
    return which("jstack")


def list_java_procs() -> list[dict]:
    """用 PowerShell 列 java 进程（含命令行与窗口标题）。"""
    ps = (
        "Get-CimInstance Win32_Process -Filter \"Name='java.exe' or Name='javaw.exe'\" | "
        "ForEach-Object { $t=''; try { $t=(Get-Process -Id $_.ProcessId -ErrorAction Stop).MainWindowTitle } catch {}; "
        "[pscustomobject]@{pid=$_.ProcessId; cmd=$_.CommandLine; title=$t} } | "
        "ConvertTo-Json -Compress -Depth 4"
    )
    try:
        out = subprocess.run(["powershell", "-NoProfile", "-Command", ps],
                             capture_output=True, text=True, encoding="utf-8", errors="replace",
                             timeout=60)
    except Exception as e:  # noqa: BLE001
        print(f"列进程失败: {e}")
        return []
    txt = (out.stdout or "").strip()
    if not txt:
        return []
    try:
        data = json.loads(txt)
    except Exception:  # noqa: BLE001
        return []
    if isinstance(data, dict):
        data = [data]
    return data


def pick_proc(procs: list[dict], want_pid: int | None, version_hint: str) -> dict | None:
    if want_pid:
        for p in procs:
            if int(p.get("pid") or 0) == want_pid:
                return p
        return None
    cand = [p for p in procs if "Minecraft" in (p.get("title") or "")]
    if not cand:
        cand = [p for p in procs if version_hint and version_hint in (p.get("cmd") or "")]
    if not cand:
        cand = procs
    return cand[0] if cand else None


def parse_dump(text: str) -> list[dict]:
    threads, cur = [], None
    for line in text.splitlines():
        m = THREAD_RE.match(line)
        if m and "tid=" in line:
            cur = {"name": m.group("name"), "header": line.strip(), "state": "", "frames": []}
            threads.append(cur)
            continue
        if cur is None:
            continue
        ms = STATE_RE.search(line)
        if ms:
            cur["state"] = ms.group("state")
            continue
        mf = FRAME_RE.match(line)
        if mf:
            cur["frames"].append(mf.group("frame"))
    for t in threads:
        t["mod_frames"] = [f for f in t["frames"]
                           if f and not f.startswith(BENIGN) and "$$Lambda" not in f]
    return threads


def report(threads: list[dict], top: int) -> None:
    print(f"\n线程总数 {len(threads)}")
    interesting = [t for t in threads if t["mod_frames"]]
    interesting.sort(key=lambda t: -len(t["mod_frames"]))
    print(f"栈里含 mod 类的线程 {len(interesting)} 个，按 mod 帧数排序（前 {top}）：\n")
    for t in interesting[:top]:
        print(f"  [{len(t['mod_frames']):>3} mod帧] {t['name']}  ({t['state'] or '?'})")
        for f in t["mod_frames"][:6]:
            print(f"         {f}")
        print()
    print("=" * 78)
    print("重点线程的完整栈（世界加载卡住时看这几个）：")
    for want in ("Render thread", "main", "Server thread", "Worker-Main-1", "IO-Worker-1"):
        for t in threads:
            if t["name"] == want:
                print(f"\n--- {want} ({t['state'] or '?'}) ---")
                for f in t["frames"][:28]:
                    print(f"    {f}")
                if len(t["frames"]) > 28:
                    print(f"    … 另有 {len(t['frames']) - 28} 帧")
                break


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass

    ap = argparse.ArgumentParser(description="挂起取证：jstack 线程转储 + mod 帧定位")
    ap.add_argument("--pid", type=int)
    ap.add_argument("--wait", type=int, default=0, help="取转储前先等这么多秒")
    ap.add_argument("--list", action="store_true", help="只列出候选进程")
    ap.add_argument("--dump", help="分析已有的 jstack 文本，不再取新转储")
    ap.add_argument("--out", help="转储保存路径（默认 data/jstack-<时间>.txt）")
    ap.add_argument("--top", type=int, default=12)
    args = ap.parse_args()

    if args.dump:
        text = Path(args.dump).read_text(encoding="utf-8", errors="replace")
        print(f"分析已有转储：{args.dump}")
        report(parse_dump(text), args.top)
        return 0

    version_hint = ""
    pt = ROOT / "pack" / "pack.toml"
    if pt.is_file():
        for line in pt.read_text(encoding="utf-8").splitlines():
            if line.startswith("name ="):
                version_hint = ""
    procs = list_java_procs()
    if args.list or not procs:
        print(f"java 进程 {len(procs)} 个：")
        for p in procs:
            print(f"  pid={p.get('pid'):<8} title={p.get('title')!r}")
        if not procs:
            print("  没有正在跑的 java 进程（游戏可能已退出或还没启动）")
        return 0

    if args.wait:
        print(f"等 {args.wait} 秒让它卡稳…")
        time.sleep(args.wait)

    proc = pick_proc(procs, args.pid, version_hint)
    if not proc:
        print("没能定位 Minecraft 进程（用 --pid 指定）")
        return 2
    pid = int(proc["pid"])
    print(f"目标进程 pid={pid} title={proc.get('title')!r}")

    jstack = find_jstack()
    if not jstack:
        print("找不到 jstack —— 它随 JDK 一起装；确认 pack.local.json 里的 java 指向 JDK（不是 JRE）")
        return 3
    print(f"jstack: {jstack}")
    try:
        out = subprocess.run([jstack, str(pid)], capture_output=True, text=True,
                             encoding="utf-8", errors="replace", timeout=120)
    except Exception as e:  # noqa: BLE001
        print(f"jstack 执行失败：{e}")
        return 4
    text = out.stdout or ""
    if not text.strip():
        print(f"jstack 无输出（stderr: {(out.stderr or '').strip()[:300]}）")
        return 5

    dest = Path(args.out) if args.out else ROOT / "data" / f"jstack-{time.strftime('%Y%m%d-%H%M%S')}.txt"
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(text, encoding="utf-8")
    print(f"转储已保存：{dest}  ({len(text)} 字符)")
    report(parse_dump(text), args.top)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

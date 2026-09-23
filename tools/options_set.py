#!/usr/bin/env python3
"""options_set.py —— 改**实例**的 `options.txt` 里指定的 `key:value`，用于性能对照的
"测量用配置"（例如关掉 vsync 才能量出真实帧数上限）。

为什么要写成工具，而不是随手改
------------------------------
* `options.txt` 是**玩家的配置**：必须**先整份备份**、改了什么要打出来、能一条命令还原。
* ⚠️ **游戏在跑的时候改它没用**：MC 退出时会把内存里的设置写回 `options.txt`，
  把改动覆盖掉。所以这里先数进程，**发现有实例在跑就拒绝执行**。
* 值里可能有冒号（`renderClouds:"true"`），所以按**第一个**冒号切分。

用法
----
    python tools/options_set.py enableVsync=false maxFps=260
    python tools/options_set.py --show graphicsMode particles renderClouds entityDistanceScaling
    python tools/options_set.py --restore          # 从备份整份还原
"""

from __future__ import annotations

import argparse
import pathlib
import shutil
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import pack_paths  # noqa: E402  同目录模块


def instance_options() -> pathlib.Path:
    return pathlib.Path(pack_paths.paths()["instance_dir"]) / "options.txt"


def other_clients_running() -> list[str]:
    """列出正在跑的、命令行里带本实例目录的 java 进程（= 游戏在跑）。"""
    try:
        out = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "Get-CimInstance Win32_Process -Filter \"Name like '%java%'\" | "
             "Select-Object -ExpandProperty CommandLine"],
            capture_output=True, text=True, timeout=40).stdout or ""
    except Exception:  # noqa: BLE001
        return []
    inst = str(pack_paths.paths()["instance_dir"]).lower()
    return [ln.strip()[:120] for ln in out.splitlines() if inst in ln.lower()]


def main() -> int:
    ap = argparse.ArgumentParser(description="改实例 options.txt（带备份与在跑检测）")
    ap.add_argument("pairs", nargs="*", help="key=value …")
    ap.add_argument("--show", nargs="*", help="只显示这些键的当前值")
    ap.add_argument("--restore", action="store_true", help="从备份整份还原")
    ap.add_argument("--force", action="store_true", help="游戏在跑时也强行改（一般别用）")
    args = ap.parse_args()

    opt = instance_options()
    if not opt.is_file():
        print(f"找不到 {opt}")
        return 1
    backup = opt.with_suffix(".txt.perfbackup")

    if args.restore:
        if not backup.is_file():
            print(f"没有备份可还原：{backup} 不存在")
            return 1
        shutil.copyfile(backup, opt)
        print(f"已从 {backup.name} 还原 {opt}")
        return 0

    if args.show is not None:
        want = set(args.show)
        for line in opt.read_text(encoding="utf-8", errors="replace").splitlines():
            k = line.split(":", 1)[0]
            if not want or k in want:
                print(line)
        return 0

    if not args.pairs:
        print("没给任何 key=value；用 --show 看、--restore 还原")
        return 1

    running = other_clients_running()
    if running and not args.force:
        print(f"!! 检测到 {len(running)} 个游戏进程在跑 —— 改了会在退出时被覆盖，拒绝执行：")
        for r in running:
            print("   " + r)
        return 2

    if not backup.is_file():
        shutil.copyfile(opt, backup)
        print(f"已备份原始配置 -> {backup}")

    lines = opt.read_text(encoding="utf-8").splitlines()
    changed, seen = [], set()
    for pair in args.pairs:
        if "=" not in pair:
            print(f"跳过（不是 key=value）：{pair}")
            continue
        key, val = pair.split("=", 1)
        for i, line in enumerate(lines):
            if line.split(":", 1)[0] == key:
                old = line.split(":", 1)[1] if ":" in line else ""
                if old != val:
                    lines[i] = f"{key}:{val}"
                    changed.append(f"  {key}: {old} -> {val}")
                seen.add(key)
                break
    for pair in args.pairs:
        key, val = pair.split("=", 1) if "=" in pair else ("", "")
        if key and key not in seen:
            lines.append(f"{key}:{val}")
            changed.append(f"  {key}: (缺失) -> {val}")

    opt.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("改动：" if changed else "没有任何改动（值已经一样）")
    for c in changed:
        print(c)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

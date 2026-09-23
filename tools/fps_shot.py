#!/usr/bin/env python3
"""fps_shot.py —— 给 FPS 对照做**轻量**采样截图，并把多次采样拼成一张长图。

为什么不用 MCP 的 `windows_computer_use_snapshot`
------------------------------------------------
1. 它抓整个窗口（1922x1112），并且**强制 GL 帧缓冲回读** —— 采样动作本身会拖慢
   正在测的那一帧（测帧数的工具不能是帧数的大头）。
2. 它每次还带回一整棵 UIA 树（约 1.5k token 的固定噪音），而我只要一行数字。
3. 8 次采样 = 8 张大图，上下文会被吃掉。

这里做三件事：只截 F3 覆盖层左上角那一小块（默认 560x230，约 500 token/张）、
采完拼成**一张**长图（一次读图看到全部采样）、每片下面写上采集时刻。

用法（**窗口必须先激活到前台**，否则截到的是别的窗口）
----------------------------------------------------
    python tools/fps_shot.py shot    --index 1 --out data/perf/shots/r4
    python tools/fps_shot.py full    --out data/perf/shots/r4      # 整条覆盖层，核对画质/显存
    python tools/fps_shot.py compose --out data/perf/shots/r4      # 拼长图

坐标默认值是按「1920x1080 窗口、窗口左上角在屏幕 (395,39)」量出来的：
F3 第一行 `NN fps / 120 fps 垂直同步` 在窗口内 y≈110..143，左列往下到
`内置服务器：NN 毫秒刻` 约 y≈330；右列（内存/GPU）在 x>1150，需要时用 `--w` 拉宽。
换分辨率或挪窗口后，先用 `full` 核一次坐标。
"""

from __future__ import annotations

import argparse
import pathlib
import sys
import time

from PIL import Image, ImageDraw, ImageGrab

# 屏幕坐标默认值（见模块 docstring 的推导）
DEF_X, DEF_Y, DEF_W, DEF_H = 400, 140, 560, 230


def grab(x: int, y: int, w: int, h: int, out: pathlib.Path) -> None:
    out.parent.mkdir(parents=True, exist_ok=True)
    img = ImageGrab.grab(bbox=(x, y, x + w, y + h), all_screens=True)
    img.save(out)
    print(f"shot -> {out}  ({w}x{h})  {time.strftime('%H:%M:%S')}")


def compose(pattern: str, w: int, h: int, out: pathlib.Path) -> int:
    """把 `<pattern>-NN.png` 竖着拼成一张图，每片下面标注采集时刻。"""
    parts = sorted(p for p in out.parent.glob(pathlib.Path(pattern).name + "-*.png")
                   if "full" not in p.name and "composed" not in p.name)
    if not parts:
        print(f"没有可拼的分片（找的是 {pattern}-*.png）")
        return 1
    label = 22
    canvas = Image.new("RGB", (w, (h + label) * len(parts)), (0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    for i, p in enumerate(parts):
        with Image.open(p) as im:
            canvas.paste(im.convert("RGB"), (0, i * (h + label)))
        stamp = f"{time.strftime('%H:%M:%S', time.localtime(p.stat().st_mtime))}  {p.name}"
        draw.text((4, i * (h + label) + h + 3), stamp, fill=(255, 255, 0))
    out.parent.mkdir(parents=True, exist_ok=True)
    canvas.save(out)
    print(f"composed {len(parts)} shots -> {out}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="FPS 采样截图（轻量 + 拼图）")
    ap.add_argument("action", choices=["shot", "full", "compose"])
    ap.add_argument("--index", type=int, default=1, help="shot 用：第几次采样（写进文件名）")
    ap.add_argument("--out", default="data/perf/shots/fps", help="输出前缀（不含扩展名）")
    ap.add_argument("--x", type=int, default=DEF_X)
    ap.add_argument("--y", type=int, default=DEF_Y)
    ap.add_argument("--w", type=int, default=DEF_W)
    ap.add_argument("--h", type=int, default=DEF_H)
    args = ap.parse_args()

    prefix = pathlib.Path(args.out)
    if args.action == "shot":
        grab(args.x, args.y, args.w, args.h, prefix.parent / f"{prefix.name}-{args.index:02d}.png")
        return 0
    if args.action == "full":
        # 整条覆盖层：左列 + 右列（1920 宽），用来核对 图像品质 / 内存 / GPU 占用
        grab(args.x - 5, args.y, 1920, args.h + 100, prefix.parent / f"{prefix.name}-full.png")
        return 0
    return compose(str(prefix), args.w, args.h, prefix.parent / f"{prefix.name}-composed.png")


if __name__ == "__main__":
    raise SystemExit(main())

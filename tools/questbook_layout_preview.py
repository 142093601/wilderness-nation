#!/usr/bin/env python3
"""questbook_layout_preview.py -- 把一章的排版**画成图**，用来人眼检查（不靠数字）。

为什么需要它（2026-09-22，我在这上面栽了三次）
--------------------------------------------
我调排版时一直在看"交叉线数量""画布宽高"这些数字，而用户看的是**画面**。
结果：我按参照包把间距压到 gap=0.18（15% 图标宽），数字上"面积变小了 = 更好了"，
用户进游戏第一句话是"所有章节排版都太过于紧密"。

**能画出来看的指标就不要只看数字。** 这个脚本画的是**接近游戏里的观感**：
按 FTB 的 `grid_scale` 把坐标换算成像素、按 `size` 画节点直径、画出依赖线、
按形状区分色调 —— 于是"挤不挤""分组看不看得出来""线有没有缠成一团"一眼就清楚。

用法
----
    python tools/questbook_layout_preview.py 60-create            # 单章
    python tools/questbook_layout_preview.py --all               # 每章一张
输出到 tools/out/preview/<章>.png（gitignore）
"""

from __future__ import annotations

import argparse
import importlib.util
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / "out" / "preview"

from PIL import Image, ImageDraw  # noqa: E402

# 形状 → 颜色（和游戏里一样：五边形簇头醒目、终局最大）
SHAPE_FILL = {
    "pentagon": (120, 96, 46),
    "rsquare": (58, 74, 96),
    "square": (58, 74, 96),
    "diamond": (74, 96, 58),
    "octagon": (96, 58, 84),
    "hexagon": (140, 60, 52),
    "gear": (96, 88, 48),
    "circle": (62, 62, 68),
}
EDGE = (168, 120, 120)
BG = (34, 36, 40)
GRID = (44, 47, 52)


def load_qb():
    spec = importlib.util.spec_from_file_location("qb", HERE / "questbook.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)          # type: ignore[union-attr]
    return mod


def render(qb, path: Path, scale: float = 46.0) -> Path:
    """scale = 每个画布单位多少像素（FTB 的 grid_scale 是 0.5，所以 46 ≈ 23px/半格）。"""
    raw = qb.tomllib.loads(path.read_text(encoding="utf-8"))
    ch = qb.ChapterDef(path, raw)
    pos = qb.layout(ch)
    size = {q["name"]: float(q.get("size", 1.5)) for q in ch.quests}
    shape = {q["name"]: str(q.get("shape", "circle")) for q in ch.quests}
    deps = {q["name"]: [d for d in (q.get("deps") or []) if d in pos] for q in ch.quests}

    pad = 70
    xs = [x for x, _ in pos.values()]
    ys = [y for _, y in pos.values()]
    minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
    W = int((maxx - minx) * scale) + pad * 2
    H = int((maxy - miny) * scale) + pad * 2
    img = Image.new("RGB", (max(W, 200), max(H, 200)), BG)
    d = ImageDraw.Draw(img)

    def px(p):
        x, y = p
        return (pad + (x - minx) * scale, pad + (maxy - y) * scale)

    # 依赖线
    for n, ds in deps.items():
        for dep in ds:
            d.line([px(pos[dep]), px(pos[n])], fill=EDGE, width=3)

    # 节点（按尺寸从大到小画，小的叠在上面）
    for n in sorted(pos, key=lambda k: -size[k]):
        cx, cy = px(pos[n])
        r = size[n] * scale / 2.0
        fill = SHAPE_FILL.get(shape[n], SHAPE_FILL["circle"])
        if shape[n] == "pentagon":
            pts = [(cx + r * math.cos(math.radians(-90 + 72 * i)),
                    cy + r * math.sin(math.radians(-90 + 72 * i))) for i in range(5)]
            d.polygon(pts, fill=fill, outline=(210, 210, 210))
        elif shape[n] in ("hexagon", "octagon"):
            k = 6 if shape[n] == "hexagon" else 8
            pts = [(cx + r * math.cos(math.radians(-90 + 360 / k * i)),
                    cy + r * math.sin(math.radians(-90 + 360 / k * i))) for i in range(k)]
            d.polygon(pts, fill=fill, outline=(210, 210, 210))
        elif shape[n] == "rsquare":
            d.rounded_rectangle([cx - r, cy - r, cx + r, cy + r], radius=r * 0.28,
                                fill=fill, outline=(210, 210, 210))
        elif shape[n] == "diamond":
            d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)],
                      fill=fill, outline=(210, 210, 210))
        else:
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill, outline=(210, 210, 210))

    info = (f"{ch.title}  ·  {len(ch.quests)} 条  ·  "
            f"外框 {(maxx - minx):.0f} x {(maxy - miny):.0f}")
    d.text((12, 10), info.encode("ascii", "replace").decode(), fill=(200, 200, 200))

    OUT.mkdir(parents=True, exist_ok=True)
    out = OUT / f"{path.stem}.png"
    img.save(out)
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="任务书排版预览")
    ap.add_argument("chapter", nargs="?", help="章文件名前缀，如 60-create")
    ap.add_argument("--all", action="store_true", help="每章一张")
    ap.add_argument("--scale", type=float, default=46.0, help="像素/画布单位")
    args = ap.parse_args()

    qb = load_qb()
    targets = ([p for p in sorted((HERE / "questbook" / "chapters").glob("*.toml"))]
               if args.all else
               [p for p in sorted((HERE / "questbook" / "chapters").glob("*.toml"))
                if args.chapter and p.stem.endswith(args.chapter)])
    if not targets:
        ap.error("给一个章名（或 --all）")
    for p in targets:
        out = render(qb, p, args.scale)
        print(f"  {out.relative_to(HERE.parent)}")
    print(f"\n共 {len(targets)} 张 -> tools/out/preview/")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

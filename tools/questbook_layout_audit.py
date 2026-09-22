#!/usr/bin/env python3
"""questbook_layout_audit.py -- 任务书画布排版的质量审计（**动手改排版之前先看这个**）。

为什么排版要单独审计
------------------
在 FTB Quests 里**位置就是引导**：
  · 左右 = 先后（玩家自然从左往右读）
  · 上下 = 分支（挂下去就是"选做"）
  · 留白 = 分组（两块之间空一格，玩家就知道这是两件事）
  · 形状 + 尺寸 = 层级（五边形簇头 / 六边形终局）

所以"排得下"不等于"排得对"：把 25 条同深度的任务摊成一行 25 个，
**语义上**是告诉玩家"这 25 件事并列"，但实际上它们属于好几个不同的簇 ——
那就是**排版在撒谎**。这个脚本量的就是这类谎。

四个指标
--------
1. **画布尺寸**（宽 × 高）：玩家要拖多远才能看完一章。
   对照：ATM10 的 90 条 Create 章只占 16.5 × 19 = 314。
2. **最宽一行**：超过 ~10 就说明"横向长条"，人眼读起来会累。
3. **簇内 / 跨簇依赖比**：**决定能不能按簇分块排版**。
   如果 90% 的依赖都在簇内，那么"一个簇排成一块"几乎不会产生跨块的连线，
   是最贴合语义的排法。
4. **依赖线交叉的粗略估计**：同一章里两条依赖线是否在几何上交叉。
   交叉多 = 图变成一团毛线，玩家看不出主线。

用法
----
    python tools/questbook_layout_audit.py                 # 全部章
    python tools/questbook_layout_audit.py --chapter create
    python tools/questbook_layout_audit.py --top 12
"""

from __future__ import annotations

import argparse
import importlib.util
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CHAPTERS = HERE / "questbook" / "chapters"


def load_qb():
    spec = importlib.util.spec_from_file_location("qb", HERE / "questbook.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)          # type: ignore[union-attr]
    return mod


def cluster_of(txt: str) -> dict[str, int]:
    """从 `# ---- 簇 N：… ----` 注释推断每个任务属于哪个簇（没有注释就算簇 0）。"""
    cur = 0
    out: dict[str, int] = {}
    for ln in txt.split("\n"):
        m = re.match(r"^#\s*-+\s*簇\s*(\d+)", ln)
        if m:
            cur = int(m.group(1))
        m2 = re.match(r'^name = "([^"]+)"$', ln)
        if m2:
            out[m2.group(1)] = cur
    return out


def seg_cross(a, b, c, d) -> bool:
    """线段 ab 与 cd 是否真交叉（不含共享端点）。"""
    def orient(p, q, r):
        v = (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
        return 0 if abs(v) < 1e-9 else (1 if v > 0 else -1)

    if len({a, b, c, d}) < 4:
        return False
    o1, o2 = orient(a, b, c), orient(a, b, d)
    o3, o4 = orient(c, d, a), orient(c, d, b)
    return o1 != o2 and o3 != o4


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="任务书排版审计")
    ap.add_argument("--chapter", help="只看这一章（文件名前缀，如 create）")
    ap.add_argument("--top", type=int, default=0, help="只列最大的 N 章")
    args = ap.parse_args()

    qb = load_qb()
    rows = []
    g_in = g_cross = 0
    for p in sorted(CHAPTERS.glob("*.toml")):
        if args.chapter and not p.stem.endswith(args.chapter):
            continue
        txt = p.read_text(encoding="utf-8")
        raw = qb.tomllib.loads(txt)
        if not raw.get("quest"):
            continue
        ch = qb.ChapterDef(p, raw)
        pos = qb.layout(ch)
        by = {q["name"]: q for q in ch.quests}
        cl = cluster_of(txt)

        xs = [x for x, _ in pos.values()]
        ys = [y for _, y in pos.values()]
        w, h = max(xs) - min(xs), max(ys) - min(ys)

        widths: dict[float, int] = {}
        for _, y in pos.values():
            widths[round(y, 2)] = widths.get(round(y, 2), 0) + 1

        inside = cross = 0
        edges = []
        for q in ch.quests:
            for d in q.get("deps") or []:
                if d not in pos or q["name"] not in pos:
                    continue
                if cl.get(d, -1) == cl.get(q["name"], -2):
                    inside += 1
                else:
                    cross += 1
                edges.append((pos[d], pos[q["name"]]))
        g_in += inside
        g_cross += cross

        # 交叉检测（O(E^2)，章内边数一般 <300，可接受）
        crossings = 0
        for i in range(len(edges)):
            a, b = edges[i]
            for j in range(i + 1, len(edges)):
                if seg_cross(a, b, edges[j][0], edges[j][1]):
                    crossings += 1

        ncl = len({v for v in cl.values()})
        rows.append((p.stem, len(ch.quests), ncl, w, h, max(widths.values()),
                     inside, cross, crossings))

    rows.sort(key=lambda r: -r[1])
    if args.top:
        rows = rows[: args.top]

    print("%-20s %5s %4s %7s %7s %7s %6s %6s %7s"
          % ("章", "条数", "簇", "宽x", "高y", "最宽行", "簇内", "跨簇", "线交叉"))
    for r in rows:
        print("%-20s %5d %4d %7.1f %7.1f %7d %6d %6d %7d"
              % (r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[7], r[8]))
    print()
    tot = g_in + g_cross
    print(f"合计：簇内依赖 {g_in} · 跨簇 {g_cross} → 簇内占 {100 * g_in / max(1, tot):.0f}%"
          f"（越高越适合「按簇分块」排版）")
    print(f"最宽行 {max(r[5] for r in rows)} · 最大画布 {max(r[3] for r in rows):.0f} x "
          f"{max(r[4] for r in rows):.0f}")
    print("对照：ATM10 的 90 条 Create 章占 16.5 x 19（面积 314）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

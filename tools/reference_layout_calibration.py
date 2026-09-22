#!/usr/bin/env python3
"""reference_layout_calibration.py -- 用同一套指标量**参照包**的排版（画布尺寸 + 依赖线交叉）。

为什么必须先量这个（2026-09-22）
-------------------------------
我在"降低交叉线"上迭代了三版，把 60-create 从 238 压到 168 又反弹到 375，
却**从没量过参照物**。如果 ATM10 自己的章节也有几百条交叉，那我优化的就是一个
没校准过的指标 —— 属于"把力气花在数字上，而不是玩家看得见的东西上"。

指标口径与 `questbook_layout_audit.py` 完全一致：同一套线段相交判定。

⚠️ **这个脚本曾经骗过我一次，务必看清**（2026-09-22）：
第一版的 SNBT 块正则在这两个包的文件上**一条依赖都解析不出来**，
于是 `edges=[]`、`crossings=0`，我据此得出"参照物零交叉"的结论并据此重写了排版。
真相是 ATM10 的交叉是 **3~19**（create 19 / bees 3 / storage 4），不是 0。
现在脚本会**同时打印边数**——边数为 0 就说明解析失败，而不是"没有交叉"。
**看到"0 交叉"先看边数。**
"""
from __future__ import annotations

import pathlib
import re
import statistics as st

VERSIONS = pathlib.Path(r"D:\game\PCL\.minecraft\versions")
PACKS = {
    "atm10": "All the Mods 10",
    "skyhive": "Beebeeblock_TheSkyhive_v2.08_cf_release",
    "ours": None,
}
QID = re.compile(r'id: "([0-9A-F]{16})"')


def parse_chapter(text: str):
    """从 SNBT 里取每条任务：id / x / y / dependencies。

    ⚠️ **这一段就是当初骗了我的那段代码**，第二版才修对。区别在**锚定行首制表符**：
      · 坏的写法 `\\n\\t\\t\\{\\n(.*?)\\n\\t\\t\\}` + 在块内 `re.search("dependencies: ...")`
        —— 在 ATM10 的文件上会把**相邻几条任务糊成一块**，于是 dependencies 找不到
        （块边界对不上），得到空依赖 → 交叉恒为 0。
      · 对的做法：用 `(?m)` 逐行锚定 `^\\t\\t\\{` / `^\\t\\t\\}$` / `^\\t\\t\\tid: …$`，
        字段必须**自成一行**才算数。
    """
    out = {}
    for m in re.finditer(r"(?m)^\t\t\{\n(.*?)^\t\t\}$", text, re.S):
        blk = m.group(1)
        mi = re.search(r'(?m)^\t\t\tid: "([0-9A-F]{16})"$', blk)
        mx = re.search(r"(?m)^\t\t\tx: (-?[\d.]+)d$", blk)
        my = re.search(r"(?m)^\t\t\ty: (-?[\d.]+)d$", blk)
        if not (mi and mx and my):
            continue
        deps = re.search(r"(?m)^\t\t\tdependencies: \[(.*?)\]", blk, re.S)
        dl = re.findall(r'"([0-9A-F]{16})"', deps.group(1)) if deps else []
        out[mi.group(1)] = (float(mx.group(1)), float(my.group(1)), dl)
    return out


def seg_cross(a, b, c, d) -> bool:
    def orient(p, q, r):
        v = (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])
        return 0 if abs(v) < 1e-9 else (1 if v > 0 else -1)

    if len({a, b, c, d}) < 4:
        return False
    return (orient(a, b, c) != orient(a, b, d)) and (orient(c, d, a) != orient(c, d, b))


def analyze(d: pathlib.Path):
    rows = []
    for p in sorted(d.glob("*.snbt")):
        qs = parse_chapter(p.read_text(encoding="utf-8", errors="replace"))
        if len(qs) < 20:
            continue
        pts = {k: (v[0], v[1]) for k, v in qs.items()}
        edges = [(pts[k], pts[dep]) for k, v in qs.items() for dep in v[2] if dep in pts]
        cr = 0
        for i in range(len(edges)):
            for j in range(i + 1, len(edges)):
                if seg_cross(edges[i][0], edges[i][1], edges[j][0], edges[j][1]):
                    cr += 1
        xs = [v[0] for v in qs.values()]
        ys = [v[1] for v in qs.values()]
        rows.append((p.name, len(qs), max(xs) - min(xs), max(ys) - min(ys), cr, len(edges)))
    return rows


def main() -> None:
    for name, folder in PACKS.items():
        d = (pathlib.Path("tools/questbook/chapters") if folder is None
             else VERSIONS / folder / "config" / "ftbquests" / "quests" / "chapters")
        if not d.is_dir():
            print(f"[skip] {name}")
            continue
        if folder is None:
            print(f"== {name}：我们的章是 TOML，这里只看参照包 ==")
            continue
        rows = analyze(d)
        if not rows:
            continue
        rows.sort(key=lambda r: -r[1])
        print(f"== {name}（{len(rows)} 个 >=20 条的章）==")
        # ⚠️ **一定同时看"边"这一列**：边 = 0 代表解析失败，不代表"没有交叉"。
        # 我第一版就是被"交叉 0、边没打印"骗了，据此重写了整个排版算法。
        print("   %-28s %5s %7s %7s %6s %7s" % ("文件", "条数", "宽", "高", "边", "交叉"))
        for r in rows[:6]:
            # 注意顺序：元组里是 (…, 交叉, 边)，打印时**换成 边 在前**
            print("   %-28s %5d %7.1f %7.1f %6d %7d"
                  % (r[0], r[1], r[2], r[3], r[5], r[4]))
        print("   中位：面积 %.0f · 交叉 %d · 宽 %.1f · 高 %.1f"
              % (st.median(r[2] * r[3] for r in rows), st.median(r[4] for r in rows),
                 st.median(r[2] for r in rows), st.median(r[3] for r in rows)))
        print()


if __name__ == "__main__":
    main()

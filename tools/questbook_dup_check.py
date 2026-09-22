#!/usr/bin/env python3
"""questbook_dup_check.py -- 找出**跨章的重复判据**（同一个东西被两个章同时要求）。

为什么必须有这个检查（2026-09-22，是我自己定的规矩却自己破了）
----------------------------------------------------------
`QUESTBOOK-ROSTER.md` §4.1 写着"一个 mod 只有一个家"。但那条规矩**只在 mod 级
被检查过**，物品级从来没有工具把关 —— 于是：

  · `130-cat-storage` 与 `60-create` 都要 `create:smart_chute` / `brass_funnel` /
    `depot` / `factory_gauge`（4 对）
  · `95-claims` / `144-cat-utility` / `150-cat-explore` 三章都要 waystones 的
    传送石盘 / 共鸣碎片 / 双生羽毛 / 石座 / 传送粉 / 卷轴（6 对）
  · `只收该收的` 在三个章里用同一个 `ftbfiltersystem:smart_filter`
  · `160-cat-warfare` 与 `54-conquest` 都要 `siegemachines:siege_workbench`

后果：玩家在两个章里做**同一件事**，两边的任务同时亮 —— 这正是那条规矩要防的。
根因是我派了 15 个 agent 分头写，**每个 agent 都守规矩，合起来却重了**，
而我在派活之前没有先建这个检查。

判据的性质决定了严重程度（`QUESTBOOK-ROSTER.md` §4.1.0 也记了这条）：
  · `item`（持有）与 `advancement`（一次性事件）**一重复就是真重复**（同时亮）
  · `kill` / `stat` 是**累计量**，不同阈值不算重复（两章各要一次是合理的）

用法
----
    python tools/questbook_dup_check.py                 # 全部（按严重度排）
    python tools/questbook_dup_check.py --only-modded   # 只看模组物品（原版基础件不算）
    python tools/questbook_dup_check.py --full          # 只看"标题+判据都一样"的完全重复
"""

from __future__ import annotations

import argparse
import collections
import sys
import tomllib
from pathlib import Path

HERE = Path(__file__).resolve().parent
CHAPTERS = HERE / "questbook" / "chapters"


def criteria(q: dict) -> tuple[frozenset, frozenset]:
    """→ (物品判据「id×数量」集, 进度判据 id 集)。**这两类重复才会同时亮**。

    ⚠️ 两条修正（2026-09-22，都是我自己先踩出来的假阳性）：

    1. **图标不算判据**。第一版把 `icon` 也收进来了，于是
       `154-cat-structures` 的「鉴定神器」（进度 `unidentified_artifact`）与
       `45-expedition` 的同名任务（进度 `artifact_shards`、物品 `unidentified_artifact`）
       被判成重复 —— 两条判据其实完全不同，只是**图标撞了**。
       图标不参与解锁，拿它当判据会产出假阳性（差点改坏两条正确的任务）。

    2. **数量要算进判据**。`count=1`（第一次拿到）与 `count=8`（攒够一批）
       不是同一个要求，做完 1 个只会点亮前者。不计数量的话，
       "用数量区分两章"这种正当处置在报告里看起来像没修。
    """
    items: set[str] = set()
    advs: set[str] = set()
    for t in q.get("tasks") or []:
        if t.get("type") == "item":
            v = t.get("item")
            iid = v if isinstance(v, str) else str((v or {}).get("id"))
            cnt = 1
            if isinstance(v, dict) and v.get("count"):
                cnt = int(v["count"])
            elif t.get("count"):
                cnt = int(t["count"])
            items.add(f"{iid}×{cnt}")
        elif t.get("type") == "advancement":
            advs.add(str(t.get("advancement")))
    return frozenset(x for x in items if x), frozenset(x for x in advs if x)


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="跨章重复判据检查")
    ap.add_argument("--only-modded", action="store_true",
                    help="忽略纯原版判据（原版基础件两章都要是合理的）")
    ap.add_argument("--full", action="store_true", help="只看完全重复（标题也一样）")
    args = ap.parse_args()

    quests: list[tuple[str, str, str, frozenset, frozenset]] = []
    for p in sorted(CHAPTERS.glob("*.toml")):
        raw = tomllib.loads(p.read_text(encoding="utf-8"))
        for q in raw.get("quest", []) or []:
            it, ad = criteria(q)
            quests.append((p.stem, q.get("name", ""), q.get("title", ""), it, ad))

    # 按判据建索引：值 -> 用了它的 (章, name, title)
    index_i: dict[str, list] = collections.defaultdict(list)
    index_a: dict[str, list] = collections.defaultdict(list)
    for ch, name, title, it, ad in quests:
        for x in it:
            index_i[x].append((ch, name, title))
        for x in ad:
            index_a[x].append((ch, name, title))

    pairs: dict[tuple, dict] = {}
    for kind, index in (("物品", index_i), ("进度", index_a)):
        for val, users in index.items():
            chapters = {c for c, _, _ in users}
            if len(chapters) < 2:
                continue
            if args.only_modded and val.startswith("minecraft:"):
                continue
            for i in range(len(users)):
                for j in range(i + 1, len(users)):
                    (c1, n1, t1), (c2, n2, t2) = users[i], users[j]
                    if c1 == c2:
                        continue
                    key = (c1, n1, c2, n2)
                    d = pairs.setdefault(key, {"kinds": set(), "vals": set(),
                                               "t1": t1, "t2": t2})
                    d["kinds"].add(kind)
                    d["vals"].add(val)

    rows = []
    for (c1, n1, c2, n2), d in pairs.items():
        full = d["t1"] == d["t2"] and d["t1"]
        if args.full and not full:
            continue
        rows.append((full, c1, n1, d["t1"], c2, n2, d["t2"],
                     sorted(d["vals"])[:3], len(d["vals"])))
    rows.sort(key=lambda r: (not r[0], -r[8]))

    full_n = sum(1 for r in rows if r[0])
    print(f"跨章重复判据：{len(rows)} 对 · 其中「标题也一样」的完全重复 {full_n} 对\n")
    print("%-4s %-20s %-16s <-> %-20s %-16s %s" % ("完全", "章A", "任务A", "章B", "任务B", "共同判据"))
    for full, c1, n1, t1, c2, n2, t2, vals, cnt in rows:
        mark = " ★ " if full else "   "
        print("%-4s %-20s %-16s <-> %-20s %-16s %s%s"
              % (mark, c1, n1[:16], c2, n2[:16], ", ".join(v[:34] for v in vals),
                 f"  (+{cnt - len(vals)})" if cnt > len(vals) else ""))
    print("\n★ = 标题与判据都一样（几乎可以确定是重复内容，优先处理）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

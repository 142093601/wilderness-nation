"""consensus_gap.py -- 把"跨整合包共识"与我们的清单对差，找出漏掉的 mod。

输入：
    data/modpack-consensus.json   （tools/modpack_survey.py 产出：usage = slug → 被几个包选中）
    modlist.tsv                   （我们的权威清单）

输出：
    A. 被 ≥N 个包选中、但我们没有的 —— 按被选中次数排序（**最该看的**）
    B. 其中"冷门好 mod"：下载量 <600 万却被多个包选中（策展者认可，但没靠量堆）

用法
----
    python consensus_gap.py --min-packs 2
    python consensus_gap.py --min-packs 3 --md ../data/consensus-gap.md
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent

VERSIONISH = re.compile(r"[-_]?(?:mc)?1\.\d+(?:\.\d+)*|[-_](?:neo)?forge|[-_]fabric|[-_]quilt"
                        r"|[-_]v?\d+\.\d+(?:\.\d+)*(?:[-+][a-z0-9.]+)?", re.I)


def family(name: str) -> str:
    s = name.lower()
    if s.endswith(".jar"):
        s = s[:-4]
    s = VERSIONISH.split(s)[0]
    return re.sub(r"[^a-z0-9]", "", s)


def our_families(tsv: Path) -> set[str]:
    out: set[str] = set()
    if not tsv.is_file():
        return out
    for raw in tsv.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split("\t")
        if parts and parts[0].strip():
            out.add(family(parts[0].strip()))
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="跨包共识 vs 我们清单")
    ap.add_argument("--min-packs", type=int, default=2)
    ap.add_argument("--niche-downloads", type=int, default=6_000_000)
    ap.add_argument("--md")
    ap.add_argument("--limit", type=int, default=200)
    args = ap.parse_args()

    src = ROOT / "data" / "modpack-consensus.json"
    if not src.is_file():
        print(f"缺少 {src}（先跑 tools/modpack_survey.py）")
        return 1
    data = json.loads(src.read_text(encoding="utf-8"))
    usage: dict[str, int] = data.get("usage") or {}
    meta: dict[str, dict] = data.get("meta") or {}

    # meta 是 project_id → 信息；建 slug → 信息 的索引
    by_slug: dict[str, dict] = {}
    for m in meta.values():
        if m.get("slug"):
            by_slug[m["slug"]] = m

    ours = our_families(ROOT / "modlist.tsv")
    ok = data.get("ok_packs") or []

    gap = []
    for slug, n in usage.items():
        if n < args.min_packs:
            continue
        if family(slug) in ours:
            continue
        info = by_slug.get(slug, {})
        gap.append((slug, n, info))
    gap.sort(key=lambda t: (-t[1], -(t[2].get("downloads") or 0)))

    niche = [g for g in gap if (g[2].get("downloads") or 0) < args.niche_downloads]

    print(f"参照：{len(ok)} 个 NeoForge 整合包 · {len(usage)} 个 mod 被用到")
    print(f"被 ≥{args.min_packs} 个包选中、且**我们清单里没有**的：{len(gap)} 个")
    print(f"其中「冷门好 mod」（下载 < {args.niche_downloads:,}）：{len(niche)} 个\n")

    print("## A. 共识漏项（按被选中次数排序）")
    for slug, n, info in gap[:80]:
        print(f"  {n:>2} 包 | {(info.get('downloads') or 0):>12,} | "
              f"{str(info.get('title'))[:38]:<38} | {slug}")
    print("\n## B. 冷门好 mod（被策展包选中、但下载量不高）")
    for slug, n, info in niche[:60]:
        print(f"  {n:>2} 包 | {(info.get('downloads') or 0):>12,} | "
              f"{str(info.get('title'))[:38]:<38} | {slug}")

    if args.md:
        lines = [
            "# 跨包共识漏项（vs 我们的清单）\n",
            f"> 参照：**{len(ok)} 个 NeoForge 整合包**（由 `tools/modpack_survey.py` 解析 `.mrpack` 得到，"
            f"合计 {len(usage)} 个 mod 被用到）。\n",
            f"> 判据：被 ≥{args.min_packs} 个包选中、且我们清单里没有。\n",
            "## A. 共识漏项\n",
            "| 被几个包选中 | 下载量 | mod | slug |",
            "|---|---|---|---|",
        ]
        for slug, n, info in gap[: args.limit]:
            lines.append(f"| **{n}** | {(info.get('downloads') or 0):,} | "
                         f"{info.get('title')} | `{slug}` |")
        lines += ["\n## B. 冷门好 mod（被策展包选中、下载量不高）\n",
                  "| 被几个包选中 | 下载量 | mod | slug |", "|---|---|---|---|"]
        for slug, n, info in niche[: args.limit]:
            lines.append(f"| **{n}** | {(info.get('downloads') or 0):,} | "
                         f"{info.get('title')} | `{slug}` |")
        Path(args.md).write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"\n已写: {args.md}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

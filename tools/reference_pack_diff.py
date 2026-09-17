"""reference_pack_diff.py -- 用本机成熟包做"共识分析"，找出我们清单里漏掉的常见 mod。

为什么需要它
------------
候选池只来自 **Modrinth 接口**，而大量常见 mod 只在 CurseForge 分发（FTB 系列、
以及一批中文社区常用 mod）。**只扫一个平台 = 系统性偏差**，表现就是"常见的 mod 怎么没加进来"。

本脚本走另一条证据路径：**本机其他成熟整合包的 `mods/` 目录**（ATM10 / Skyhive / 龙之冒险，
合计 800+ 个 jar）。出现在**多个**成熟包里的 mod，就是主流选择——
拿它和我们自己的清单对差，漏了什么一目了然。全程离线。

用法
----
    python reference_pack_diff.py                    # 列出"多包共识但我们没有"的
    python reference_pack_diff.py --min-packs 2
    python reference_pack_diff.py --md ../data/reference-diff.md
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
sys.path.insert(0, str(HERE))

VERSIONISH = re.compile(
    r"[-_]?(?:mc)?1\.\d+(?:\.\d+)*|[-_](?:neo)?forge|[-_]fabric|[-_]quilt"
    r"|[-_]v?\d+\.\d+(?:\.\d+)*(?:[-+][a-z0-9.]+)?",
    re.I,
)


def family(name: str) -> str:
    """把 jar 文件名归一成一个"家族"token，用于跨包比较。

    YungsApi-1.21.1-NeoForge-5.1.8.jar        -> yungsapi
    open-parties-and-claims-neoforge-1.21.1-0.31.6.jar -> openpartiesandclaims
    ftb-quests-neoforge-2101.1.24.jar         -> ftbquests
    """
    s = name.lower()
    if s.endswith(".jar"):
        s = s[:-4]
    s = VERSIONISH.split(s)[0]
    return re.sub(r"[^a-z0-9]", "", s)


def our_slugs(tsv: Path) -> dict[str, str]:
    out: dict[str, str] = {}
    if not tsv.is_file():
        return out
    for raw in tsv.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = [p.strip() for p in line.split("\t")]
        if len(parts) >= 2:
            out[family(parts[0])] = f"{parts[0]}（{parts[1]}）"
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="参照包共识分析")
    ap.add_argument("--min-packs", type=int, default=2, help="至少出现在几个参照包里才算主流")
    ap.add_argument("--md")
    ap.add_argument("--limit", type=int, default=120)
    args = ap.parse_args()

    import pack_paths
    raw = pack_paths.paths().get("reference_packs", "") or ""
    packs: list[tuple[str, Path]] = []
    for d in [x.strip() for x in raw.split(";") if x.strip()]:
        p = Path(d)
        if p.is_dir():
            packs.append((p.parent.name, p))
    if not packs:
        print("没有配置 reference_packs（见 pack.local.example.json）")
        return 1

    print("参照包：")
    fam_packs: dict[str, set[str]] = {}
    fam_sample: dict[str, str] = {}
    for name, d in packs:
        jars = sorted(d.glob("*.jar"))
        print(f"  {name:<34} {len(jars)} 个 jar")
        for j in jars:
            f = family(j.name)
            if not f:
                continue
            fam_packs.setdefault(f, set()).add(name)
            fam_sample.setdefault(f, j.name)

    mine = our_slugs(ROOT / "modlist.tsv")
    total_fam = len(fam_packs)
    consensus = {f: ps for f, ps in fam_packs.items() if len(ps) >= args.min_packs}
    missing = {f: ps for f, ps in consensus.items() if f not in mine}

    print(f"\n参照包家族总数 {total_fam}；出现在 ≥{args.min_packs} 个包的 {len(consensus)} 个；"
          f"其中**我们清单里没有**的 {len(missing)} 个")

    lines = [
        "# 参照包共识分析（找出我们漏掉的常见 mod）\n",
        f"> 数据源：本机 {len(packs)} 个成熟整合包的 `mods/` 目录（离线分析，`tools/reference_pack_diff.py`）。",
        f"> 出现在 **≥{args.min_packs} 个包**里的算「主流选择」；下面是其中**我们清单里没有**的。\n",
        "| 家族 | 出现在几个包 | 示例文件名 | 出现在 |",
        "|---|---|---|---|",
    ]
    for f, ps in sorted(missing.items(), key=lambda kv: (-len(kv[1]), kv[0]))[: args.limit]:
        lines.append(f"| `{f}` | {len(ps)} | {fam_sample[f]} | {', '.join(sorted(ps))} |")
    print("\n".join(lines[:8]))
    if args.md:
        Path(args.md).parent.mkdir(parents=True, exist_ok=True)
        Path(args.md).write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"\n已写: {args.md}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

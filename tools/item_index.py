#!/usr/bin/env python3
"""item_index.py -- 写任务时用的「物品/方块清单」查询工具。

为什么需要它
-----------
铺任务时 90% 的工作量是「这条任务该用哪个物品当图标/判据」。有两种错法：

1. **id 写错**（`create:cogwheel` 写成 `create:cogweel`）—— 后果是那个任务
   玩家永远做不完，且游戏里不报错。`questbook_check` 第 ⑦ 项能抓到，
   但那是**写完之后**才发现；边写边查更省一轮。
2. **id 对但语义错**（拿 `create:shaft` 当「传动轴升级」的判据）——
   校验器抓不到，只有先看清这个 mod 到底有哪些东西才避免得了。

所以本工具只做一件事：把 `tools/registry.json`（从 232 个 jar 实测出来的
25k 物品 + 7.7k 中文名）按**命名空间**或**中文名关键词**切成可读清单。

它**不产生**任何包内容，只读 registry.json，绝不改任务书数据。

用法
----
    # 某个 mod 有什么（写进文件，别从控制台读中文：本机控制台是 GBK）
    python tools/item_index.py --ns create --out tools/out/idx_create.txt

    # 按中文名找东西（"哪个 mod 有'储罐'"）
    python tools/item_index.py --grep 储罐

    # 只要方块 / 只要物品
    python tools/item_index.py --ns farmersdelight --kind block

    # 核对一批 id 是否真实存在（一行一个 id，或从 TOML 里扫）
    python tools/item_index.py --verify my_ids.txt
    python tools/item_index.py --verify-toml

    # 概览：每个命名空间有多少注册项（决定「够不够单独成章」）
    python tools/item_index.py --stats --out tools/out/idx_stats.txt

输出
----
`--out` 缺省写 `tools/out/idx_<参数>.txt`（UTF-8）。控制台只打印**纯 ASCII 摘要**
（命中数、文件路径），以避免本机 GBK 控制台把中文糊掉 —— 这是本项目的老坑
（见 `tools/README.md`「别从控制台读中文」）。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
REGISTRY = HERE / "registry.json"
OUT_DIR = HERE / "out"

# 判定「这一条是不是纯装饰」用的关键词（作者据此决定任务粒度）
DECOR_HINT = ("fence", "wall", "slab", "stairs", "trapdoor", "door", "pane",
              "button", "pressure_plate", "carpet", "lamp", "torch", "candle",
              "planks", "sign", "rail", "bars", "chain", "bricks", "_wall")


def load_registry() -> dict:
    if not REGISTRY.is_file():
        print(f"registry.json 不存在：{REGISTRY}")
        print("先生成它：python tools/build_registry.py --mods <实例>/mods")
        raise SystemExit(2)
    d = json.loads(REGISTRY.read_text(encoding="utf-8"))
    # registry.json 里 items/blocks 存的是 **list**（生成器那边按 list 落盘），
    # 这里统一成 set —— 后面全是集合运算（并/差/成员判断）。
    d["items"] = set(d["items"])
    d["blocks"] = set(d["blocks"])
    return d


def rows(reg: dict, ns: str | None, grep: str | None, kind: str) -> list[tuple[str, str, str]]:
    """→ [(id, 中文名, kind)]，已排序。"""
    items: set[str] = reg["items"]
    blocks: set[str] = reg["blocks"]
    names: dict[str, str] = reg["names"]
    everything = items | blocks

    out: list[tuple[str, str, str]] = []
    for iid in sorted(everything):
        this_ns = iid.split(":", 1)[0]
        if ns and this_ns != ns:
            continue
        if kind == "item" and iid in blocks and iid not in items:
            continue
        if kind == "block" and iid not in blocks:
            continue
        zh = names.get(iid, "")
        if grep and grep not in zh and grep not in iid:
            continue
        k = "block" if iid in blocks else "item"
        if iid in items and iid in blocks:
            k = "both"
        out.append((iid, zh, k))
    return out


def write_lines(path: Path, lines: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass

    ap = argparse.ArgumentParser(description="物品清单查询（作者用，不改包内容）")
    ap.add_argument("--ns", help="命名空间，如 create")
    ap.add_argument("--grep", help="中文名或 id 的子串")
    ap.add_argument("--kind", choices=["all", "item", "block"], default="all")
    ap.add_argument("--limit", type=int, default=0, help="最多输出条数（0=不限）")
    ap.add_argument("--out", help="输出文件（缺省 tools/out/idx_<参数>.txt）")
    ap.add_argument("--stats", action="store_true", help="按命名空间统计条数")
    ap.add_argument("--verify", help="核对一个文件里的 id（一行一个，# 注释）")
    ap.add_argument("--verify-toml", action="store_true",
                    help="核对 tools/questbook/chapters/*.toml 里所有 item/icon id")
    ap.add_argument("--coverage", action="store_true",
                    help="找出「条数>=8 但引导里从没出现过」的命名空间（覆盖缺口）")
    args = ap.parse_args()

    reg = load_registry()
    known = reg["items"] | reg["blocks"]

    # ---- 核对模式 ----
    # ⚠️ 报告文件**每次都重写**（哪怕 missing=0 也要写，写空文件）。
    # 只在有 miss 时写，会让上一次的旧报告留在盘上 —— 下一个人（或下一个我）
    # 看到过期的 `idx_missing_toml.txt` 会得出完全相反的结论。
    # 这是本项目反复踩过的「陈旧文件」坑（见 INCIDENTS.md）。
    if args.verify:
        ids = [ln.strip() for ln in Path(args.verify).read_text(encoding="utf-8").splitlines()]
        ids = [i for i in ids if i and not i.startswith("#")]
        bad = [i for i in ids if i not in known]
        print(f"checked {len(ids)} ids, missing {len(bad)}")
        p = OUT_DIR / "idx_missing.txt"
        write_lines(p, bad)
        print(f"missing list -> {p}（missing=0 时该文件为空，能区分「这次没问题」和「这是旧文件」）")
        return 1 if bad else 0

    if args.verify_toml:
        pat = re.compile(r'(?:item|icon)\s*=\s*"([a-z0-9_]+:[a-z0-9_/]+)"')
        seen: dict[str, list[str]] = {}
        for f in sorted((HERE / "questbook" / "chapters").glob("*.toml")):
            for iid in pat.findall(f.read_text(encoding="utf-8")):
                seen.setdefault(iid, []).append(f.name)
        bad = sorted(i for i in seen if i not in known)
        print(f"toml ids {len(seen)}, missing {len(bad)}")
        p = OUT_DIR / "idx_missing_toml.txt"
        write_lines(p, [f"{i}\t{','.join(sorted(set(seen[i])))}" for i in bad])
        print(f"missing list -> {p}（missing=0 时该文件为空）")
        return 1 if bad else 0

    # ---- 统计模式 ----
    if args.stats:
        counts: dict[str, int] = {}
        for iid in known:
            counts[iid.split(":", 1)[0]] = counts.get(iid.split(":", 1)[0], 0) + 1
        lines = [f"{n:6d}  {ns}" for ns, n in sorted(counts.items(), key=lambda kv: -kv[1])]
        p = Path(args.out) if args.out else OUT_DIR / "idx_stats.txt"
        write_lines(p, lines)
        print(f"namespaces {len(counts)} -> {p}")
        return 0

    # ---- 覆盖率检查模式 ----
    #
    # `QUESTBOOK-ROSTER.md` 写着一条规则：「条数 ≥ 8 的命名空间，必须至少有一条任务引用它」。
    # 但在此之前**没有任何工具能验证它** —— 规则写在文档里而没人执行，等于没有规则。
    # 这个模式就是那条规则的执行者：找出"装了但引导里从没出现过"的 mod。
    if args.coverage:
        # **查证过、确实不该有任务**的命名空间（每条都写了理由，不许往里塞"我懒得写"的）
        no_cover = {
            # 数据/标签命名空间：只有数据，没有物品
            "modifiers": "纯数据命名空间（护甲槽位 modifiers:chest/feet/…），不是物品",
            "custom_spawn_eggs": "其它 mod 生成的刷怪蛋集合命名空间，玩家不直接获取",
            # 源码里查不到对应 jar（见下），大概率是别的 mod 的**联动配方残留**
            "spookyjams": "本机 231 个 jar 里没有任何一个提供 assets/spookyjams/，"
                          "只剩别的 mod 的联动配方引用 → 游戏里拿不到",
            # patchouli：guide_book 是"所有 mod 手册的公共父物品"，本体不可合成；
            # 每个 mod 的手册是它自己的物品。写 patchouli:guide_book 会变成永远做不完的任务。
            "patchouli": "guide_book 是公共父物品（不可合成），各 mod 手册是各自的物品；"
                         "写它 = 永远做不完",
            # 2026-09-22 服务端真机实测：这两个命名空间的物品**全部**被拒
            "dnt": "Dungeons and Taverns 的物品全是**遗迹钥匙与药水变体**，"
                   "服务端实测 `dnt:citadel_key` 等 3 个 id 报 Unknown registry key；"
                   "该 mod 的价值是结构（用 `structure` 判据覆盖在 154-cat-structures）",
            "letsparkour": "9 个 `*_parkour_slab` **全部**被服务端拒绝"
                           "（`jelly_parkour_slab` / `slippery_parkour_slab` / `fast_…` 实测），"
                           "它们不是可持有的物品；跑酷玩法用原版方块代指（黏液块 / 浮冰）",
        }
        # 库 mod：别的 mod 的依赖，**不该**有任务
        libs = {
            "architectury", "balm", "clothconfig", "cloth_config", "kotlinforforge", "kotlin",
            "geckolib", "moonlight", "puzzleslib", "resourcefullib", "cristellib", "dragonlib",
            "fzzyconfig", "supermartijn642configlib", "supermartijn642corelib", "yungsapi",
            "creativecore", "iceberg", "prism", "athena", "framework", "jamlib", "collective",
            "coroutil", "bookshelf", "extralib", "lodestonelib", "modelfix", "mezzconfig",
            "smoothswapping", "yacl", "fabric_api", "playeranimator", "corgilib", "dataanchor",
            "resourcefulconfig", "searchables", "prickle", "configurable", "cupboard",
            "lithostitched", "terrablender", "glitchcore", "midnightlib", "cycletiles",
            "ftblibrary", "ftbteams", "ftbquests", "ftbfiltersystem", "jei", "jade", "jeresources",
            "almostunified", "polymorph", "craftingtweaks", "mousetweaks", "controlling",
            "legendarytooltips", "itemhighlighter", "betteradvancements", "chatheads",
            "colorfulhearts", "skinlayers3d", "pingwheel", "appleskin", "betterf3",
        }
        # TOML 里引用过的所有命名空间
        pat = re.compile(r'"([a-z0-9_]+):[a-z0-9_/]+"')
        used: set[str] = set()
        for f in sorted((HERE / "questbook" / "chapters").glob("*.toml")):
            used.update(pat.findall(f.read_text(encoding="utf-8")))
        counts: dict[str, int] = {}
        for iid in known:
            ns = iid.split(":", 1)[0]
            counts[ns] = counts.get(ns, 0) + 1
        gaps = [(n, c) for n, c in counts.items()
                if c >= 8 and n not in used and n not in libs and n not in no_cover]
        gaps.sort(key=lambda kv: -kv[1])
        p = Path(args.out) if args.out else OUT_DIR / "coverage_gaps.txt"
        write_lines(p, [f"{c:6d}  {n}" for n, c in gaps] or ["(无缺口：条数≥8 的命名空间都至少被引用过一次)"])
        print(f"命名空间 {len(counts)} · 被任务引用 {len(used)} · 条数>=8 但零引用的 {len(gaps)}")
        print(f"-> {p}")
        return 1 if gaps else 0

    # ---- 查询模式 ----
    if not args.ns and not args.grep:
        ap.error("至少给 --ns / --grep / --stats / --verify 之一")

    found = rows(reg, args.ns, args.grep, args.kind)
    if args.limit:
        found = found[: args.limit]

    label = "_".join(x for x in (args.ns, args.grep, args.kind if args.kind != "all" else None) if x)
    label = re.sub(r"[^\w\-]+", "-", label) or "all"
    p = Path(args.out) if args.out else OUT_DIR / f"idx_{label}.txt"

    lines = []
    for iid, zh, k in found:
        mark = "D" if (k != "item" and any(h in iid for h in DECOR_HINT)) else " "
        lines.append(f"{mark} {iid:<58} {zh}")
    write_lines(p, lines)

    with_zh = sum(1 for _, zh, _ in found if zh)
    print(f"hits {len(found)} (zh names {with_zh}) -> {p}")
    print("D = 装饰性方块（做任务时通常合并成一条，不做单条）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

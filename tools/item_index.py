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

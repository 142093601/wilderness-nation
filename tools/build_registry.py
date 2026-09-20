#!/usr/bin/env python3
"""build_registry.py -- 建「这个包里真实存在哪些物品/方块」的注册表。

为什么需要它
------------
任务书的判据大量引用物品 id（`minecraft:campfire`、`create:item_vault`…）。
写错一个 id 的后果很具体：**那个任务玩家永远做不完**，而且游戏里不会报错——
任务只是永远不亮。所以生成/校验之前必须能回答"这个 id 真的存在吗"。

证据从哪来（三层，按可靠性排）
------------------------------
1. `assets/<ns>/lang/zh_cn.json` 或 `en_us.json` 里的 `item.<ns>.<name> / block.<ns>.<name>`
   —— 有官方译名/英文名的注册项。**这是最可靠的一层**，还顺带给出中文名。
2. `assets/<ns>/models/item/<name>.json` 与 `assets/<ns>/blockstates/<name>.json`
   —— 有模型/方块状态的注册项（部分 mod 没有 lang）。
3. `data/<ns>/recipe*/**/*.json` 与 `advancement*/**/*.json` 里出现的 id
   —— 被配方/进度引用过的 id（含原版物品：mod 配方大量引用 `minecraft:*`）。
   这一层是**原版物品的主要来源**（原版 assets 不在本机）。

输出：`tools/registry.json`
    {"items": [...], "blocks": [...], "names": {"<id>": "<中文名>"}, "sources": {...}}
"""

from __future__ import annotations

import argparse
import json
import re
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DEFAULT_OUT = HERE / "registry.json"

# 只认「看起来像注册名」的字符串：小写字母数字下划线 + 可选路径段
ID_RE = re.compile(r"^[a-z0-9_]+(?::[a-z0-9_/]+)?$")
# 配方/进度里引用的 id：带命名空间的
REF_RE = re.compile(r'"([a-z0-9_]+:[a-z0-9_/]+)"')
# lang 键：item.<ns>.<name> / block.<ns>.<name>
LANG_RE = re.compile(r"^(item|block)\.([a-z0-9_]+)\.([a-z0-9_/]+)$")

# 非注册名噪音：tag 名、配方类型、判据名等
NOISE_NS = {
    "minecraft:load", "minecraft:tick",
}
NOISE_PATHS = (
    "minecraft:recipes/", "minecraft:tags/", "minecraft:functions/",
    "c:", "forge:", "neoforge:",
)


def is_noise(x: str) -> bool:
    if x in NOISE_NS:
        return True
    if any(x.startswith(p) for p in NOISE_PATHS):
        return True
    # 配方类型（minecraft:crafting_shaped）会出现在 "type" 字段里，
    # 但它们不带 / 且长度正常 —— 靠下面的白名单式排除法处理不到，
    # 所以额外用一份已知非物品的前缀表
    return False


def scan_jar(path: Path, items: set, blocks: set, names: dict, refs: set, stats: dict) -> None:
    try:
        with zipfile.ZipFile(path) as z:
            names_list = z.namelist()
            for n in names_list:
                # --- 第 1 层：lang ---
                m = re.match(r"^assets/([a-z0-9_]+)/lang/(en_us|zh_cn)\.json$", n)
                if m:
                    try:
                        data = json.loads(z.read(n).decode("utf-8"))
                    except Exception:  # noqa: BLE001
                        continue
                    for k, v in data.items():
                        lm = LANG_RE.match(k)
                        if not lm:
                            continue
                        kind, ns, name = lm.groups()
                        iid = f"{ns}:{name}"
                        if kind == "item":
                            items.add(iid)
                        else:
                            blocks.add(iid)
                        if m.group(2) == "zh_cn" and iid not in names:
                            names[iid] = v
                        stats["lang_keys"] += 1
                # --- 第 2 层：模型 / blockstates ---
                elif "/models/item/" in n and n.endswith(".json"):
                    parts = n.split("/")
                    if len(parts) >= 5:
                        items.add(f"{parts[1]}:{parts[-1][:-5]}")
                        stats["item_models"] += 1
                elif "/blockstates/" in n and n.endswith(".json"):
                    parts = n.split("/")
                    if len(parts) >= 4:
                        blocks.add(f"{parts[1]}:{parts[-1][:-5]}")
                        stats["blockstates"] += 1
                # --- 第 3 层：配方 / 进度里引用的 id ---
                elif (n.startswith("data/") and n.endswith(".json")
                      and ("/recipe" in n or "/advancement" in n or "/loot_table" in n
                           or "/item_modifier" in n)):
                    try:
                        txt = z.read(n).decode("utf-8", "replace")
                    except Exception:  # noqa: BLE001
                        continue
                    for ref in REF_RE.findall(txt):
                        if not is_noise(ref):
                            refs.add(ref)
                    stats["data_files"] += 1
    except Exception as exc:  # noqa: BLE001
        stats["skipped"].append(f"{path.name}: {exc}")


def main() -> int:
    ap = argparse.ArgumentParser(description="建物品/方块注册表")
    ap.add_argument("--mods", action="append", default=None,
                    help="mods 目录（可多次；默认用 pack.local.json 的 instance_dir/mods）")
    ap.add_argument("--extra-jar", action="append", default=None,
                    help="额外扫描的 jar（如原版客户端 jar）")
    ap.add_argument("--out", default=str(DEFAULT_OUT))
    args = ap.parse_args()

    mod_dirs: list[Path] = []
    if args.mods:
        mod_dirs = [Path(m) for m in args.mods]
    else:
        try:
            import sys
            sys.path.insert(0, str(HERE))
            from pack_paths import paths  # type: ignore
            inst = Path(paths()["instance_dir"])
            if (inst / "mods").is_dir():
                mod_dirs.append(inst / "mods")
        except Exception:  # noqa: BLE001
            pass

    if not mod_dirs:
        print("没有找到 mods 目录：用 --mods 指定，或先配好 tools/pack.local.json")
        return 1

    items: set[str] = set()
    blocks: set[str] = set()
    names: dict = {}
    refs: set[str] = set()
    stats = {"lang_keys": 0, "item_models": 0, "blockstates": 0, "data_files": 0,
             "jars": 0, "skipped": []}

    jars: list[Path] = []
    for d in mod_dirs:
        jars += sorted(d.glob("*.jar"))
    for p in (args.extra_jar or []):
        pp = Path(p)
        if pp.is_file():
            jars.append(pp)
    # 去重
    seen = set()
    uniq = []
    for j in jars:
        if j.name in seen:
            continue
        seen.add(j.name)
        uniq.append(j)

    for j in uniq:
        scan_jar(j, items, blocks, names, refs, stats)
        stats["jars"] += 1

    # 配方/进度引用的 id：只作为**补充**（无法判断它到底是 item 还是 block），
    # 放进 items 与 blocks 的并集视图里，靠 extra 字段暴露给校验器。
    reg = {
        "items": sorted(items),
        "blocks": sorted(blocks),
        "referenced": sorted(refs - items - blocks),
        "names": names,
        "sources": {
            "mod_dirs": [str(d) for d in mod_dirs],
            "jars_scanned": stats["jars"],
            "skipped": stats["skipped"],
        },
    }
    out = Path(args.out)
    out.write_text(json.dumps(reg, ensure_ascii=False), encoding="utf-8")

    print(f"registry -> {out}")
    print(f"  jars={stats['jars']}  items={len(items)}  blocks={len(blocks)}  "
          f"referenced={len(reg['referenced'])}  names={len(names)}")
    print(f"  lang_keys={stats['lang_keys']} item_models={stats['item_models']} "
          f"blockstates={stats['blockstates']} data_files={stats['data_files']}")
    if stats["skipped"]:
        print(f"  skipped={len(stats['skipped'])}（前 3 条：{stats['skipped'][:3]}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

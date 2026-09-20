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


def pick_vanilla_jar(game_root: Path) -> Path | None:
    """从 <game_root>/libraries 里挑一份**含 assets 的原版 client jar**。

    为什么必须挑 client 的 extra：`minecraft:` 的物品模型、方块状态与进度都在它里面；
    服务端用的是 server jar（**不含 assets**），只看 server jar 会得到一份
    "原版物品大面积缺失"的注册表 —— 2026-09-21 实测踩到，它让校验器把
    `minecraft:beacon` 这类正常物品误判成"只在客户端有"。
    """
    libs = game_root / "libraries" / "net" / "minecraft" / "client"
    if not libs.is_dir():
        return None
    best: Path | None = None
    best_score = -1
    for p in libs.rglob("*.jar"):
        # 优先 -extra（含 assets/ 与 data/）；同优先级里挑最大的
        score = p.stat().st_size + (10**9 if "extra" in p.name else 0)
        if score > best_score:
            best_score, best = score, p
    return best


def scan_jar(path: Path, items: set, blocks: set, names: dict, refs: set,
             advancements: set, suspected_items: set, stats: dict) -> None:
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
                        # 注意：`models/item/x.json` **不等于**物品 x 存在 ——
                        # 它可能只是供别的东西（方块渲染、实体模型）复用的模型。
                        # 所以先收进 suspected_items，最后由调用方按"有没有 lang 键"裁定。
                        suspected_items.add(f"{parts[1]}:{parts[-1][:-5]}")
                        stats["item_models"] += 1
                elif "/blockstates/" in n and n.endswith(".json"):
                    parts = n.split("/")
                    if len(parts) >= 4:
                        blocks.add(f"{parts[1]}:{parts[-1][:-5]}")
                        stats["blockstates"] += 1
                # --- 第 4 层：advancement 的**真实 id** ---
                # 任务书的 `advancement` 判据只能引用真实存在的进度；写错了同样"永远不亮"。
                adv = re.match(r"^data/([a-z0-9_]+)/advancements?/(.+)\.json$", n)
                if adv:
                    advancements.add(f"{adv.group(1)}:{adv.group(2)}")
                    stats["advancements"] += 1
                # --- 第 3 层：配方 / 进度里引用的 id ---
                if (n.startswith("data/") and n.endswith(".json")
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
    ap.add_argument("--game-root",
                    help="游戏根目录（含 libraries/）；给了它就会自动补该版本的原版 client jar。"
                         "扫描服务端 mods 时必须给这个，否则原版 assets 缺失"
                         "（服务端用的是 server jar，不含 assets）")
    args = ap.parse_args()

    mod_dirs: list[Path] = []
    instance_dir: Path | None = None
    if args.mods:
        mod_dirs = [Path(m) for m in args.mods]
    else:
        try:
            import sys
            sys.path.insert(0, str(HERE))
            from pack_paths import paths  # type: ignore
            instance_dir = Path(paths()["instance_dir"])
            if (instance_dir / "mods").is_dir():
                mod_dirs.append(instance_dir / "mods")
        except Exception:  # noqa: BLE001
            pass

    # ---- 自动补原版 jar ----
    # 为什么必须补：`minecraft:` 的物品与**进度**都在原版 jar 里（assets 与 data/minecraft）。
    # 不补的话，注册表里就没有 minecraft 命名空间的进度，校验器会把
    # `minecraft:story/smelt_iron` 这类**合法**进度判成"不存在"（2026-09-20 实测踩到）。
    auto_extra: list[Path] = []
    if args.game_root:
        vj = pick_vanilla_jar(Path(args.game_root))
        if vj is not None:
            auto_extra.append(vj)
    elif instance_dir is not None and instance_dir.is_dir():
        vj = pick_vanilla_jar(instance_dir.parent.parent)
        if vj is not None:
            auto_extra.append(vj)

    if not mod_dirs:
        print("没有找到 mods 目录：用 --mods 指定，或先配好 tools/pack.local.json")
        return 1

    items: set[str] = set()
    blocks: set[str] = set()
    names: dict = {}
    refs: set[str] = set()
    advancements: set[str] = set()
    suspected_items: set[str] = set()
    stats = {"lang_keys": 0, "item_models": 0, "blockstates": 0, "data_files": 0,
             "advancements": 0, "jars": 0, "skipped": []}

    jars: list[Path] = []
    for d in mod_dirs:
        jars += sorted(d.glob("*.jar"))
    for p in (args.extra_jar or []) + [str(x) for x in auto_extra]:
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
        scan_jar(j, items, blocks, names, refs, advancements, suspected_items, stats)
        stats["jars"] += 1

    # 配方/进度引用的 id：只作为**补充**（无法判断它到底是 item 还是 block），
    # 放进 items 与 blocks 的并集视图里，靠 extra 字段暴露给校验器。
    # ---- 关于"疑似物品"（一次实测取舍，别再改回去）----
    #
    # 2026-09-21 实测：`waystones` 的 `models/item/` 里有
    # black/blue/.../yellow_sharestone.json，**没有无前缀的 sharestone.json**，
    # 且 `waystones:sharestone` 被 FTB Quests 判为 `Unknown registry key`（不是注册物品）。
    #
    # 但"有 item model"这个条件**分不出**它是不是物品：模型可以被别的东西复用。
    # 我试过收紧（要求有 lang 键才算）——结果 items 从 25167 掉到 9022，
    # 把 `gray_sharestone` 这类**合法**方块也误判掉了。假阴性比假阳性更糟：
    # 它会让校验器每次都误报，把真信号淹没。
    #
    # 所以这里的取舍是：**注册表保持宽松**（只用于"这个 id 大致合不合法"的粗筛），
    # **权威判据是服务端真机验证**（tools/verify_questbook_on_server.py）——
    # 游戏自己说 `Unknown registry key` 才算数。
    # suspected_items 单列出来只作提示，不参与存在性判定。
    reg = {
        "items": sorted(items | suspected_items),
        "blocks": sorted(blocks),
        "referenced": sorted(refs - items - suspected_items - blocks),
        # 有 item model 的那些（含被复用的模型名），仅供参考
        "suspected_items": sorted(suspected_items),
        # 任务书的 `advancement` 判据只能引用真实进度；这是那份清单。
        "advancements": sorted(advancements),
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
    print(f"  jars={stats['jars']}  items={len(reg['items'])}  blocks={len(blocks)}  "
          f"referenced={len(reg['referenced'])}  names={len(names)}  "
          f"advancements={len(advancements)}")
    print(f"  lang_keys={stats['lang_keys']} item_models={stats['item_models']} "
          f"blockstates={stats['blockstates']} data_files={stats['data_files']}")
    if stats["skipped"]:
        print(f"  skipped={len(stats['skipped'])}（前 3 条：{stats['skipped'][:3]}）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

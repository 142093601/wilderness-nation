#!/usr/bin/env python3
"""runtime_deny.py -- 维护「**真机拒绝过**的物品 id」黑名单，并检查任务书有没有用到它们。

为什么必须有这份名单（2026-09-22 实测，代价很大）
------------------------------------------------
`tools/registry.json` 是从 **jar 的内容**推断出来的：语言键 `item.<ns>.<name>`、
模型文件、blockstates、配方里被引用过的字符串。它证明的是
**"某个 jar 提到过这个 id"**，而不是 **"游戏运行时注册了这个物品"**。

两者不等价，而且差得不少。实测：1181 条任务里 `--verify-toml` 报 **missing 0**，
但服务端一加载就报 **37 条** `Unknown registry key ... minecraft:item`：

    Tried to load invalid item: 'Unknown registry key in
      ResourceKey[minecraft:root / minecraft:item]: waystones:portstone'

这类 id 的典型来源：
  · mod 给**未安装的联动 mod** 准备的物品（语言键在，注册被条件跳过）
  · 只有 blockstate / 配方引用、并没有物品形态的方块
  · 版本迁移后残留的语言键

后果与"id 拼错"一样：**那个任务玩家永远做不完**，而且离线校验器会放它过去。
所以：**唯一可信的证据是真机日志**，这份文件就是它的沉淀。

用法
----
    # 从服务端日志里提取被拒绝的 id，合并进黑名单（并打印新增项）
    python tools/runtime_deny.py --scan server/logs/latest.log

    # 检查任务书里有没有用到黑名单里的 id（questbook_check 第 ⑩ 项也做这件事）
    python tools/runtime_deny.py --check

    # 看某个命名空间里"已被真机接受"的 id（换 id 时挑这些最稳）
    python tools/runtime_deny.py --ok-ns waystones
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DENY = HERE / "registry_runtime_deny.txt"
CHAPTERS = HERE / "questbook" / "chapters"
LOG_RE = re.compile(r"Unknown registry key in\s+ResourceKey\[minecraft:root / minecraft:item\]:\s*"
                    r"([a-z0-9_]+:[a-z0-9_/]+)")


def load_deny() -> list[str]:
    if not DENY.is_file():
        return []
    out = []
    for ln in DENY.read_text(encoding="utf-8").splitlines():
        ln = ln.split("#", 1)[0].strip()
        if ln:
            out.append(ln)
    return out


def toml_ids() -> dict[str, list[str]]:
    """→ {id: [文件名…]}，覆盖 item / icon 两种写法。"""
    pat = re.compile(r'(?:item|icon)\s*=\s*"([a-z0-9_]+:[a-z0-9_/]+)"')
    seen: dict[str, list[str]] = {}
    for f in sorted(CHAPTERS.glob("*.toml")):
        for iid in pat.findall(f.read_text(encoding="utf-8")):
            seen.setdefault(iid, []).append(f.name)
    return seen


def scan(log: Path) -> list[str]:
    """从日志里提取被拒绝的 id —— **必须同时满足两个条件**，否则会淹掉自己。

    实测（2026-09-22）：不过滤地扫 `Unknown registry key ... minecraft:item` 会得到
    **1286** 条，而真正与任务书有关的只有 **33** 条。多出来的是别的 mod 的
    datagen 残留（配方引用了未安装的联动 mod，例如 `railways:track_tfc_chestnut`
    需要 TFC、`dndecor:slate_dark_metal_plating` 需要某个染色联动）——
    它们由 `RecipeManager` 报，与我们写的 id 无关。

    过滤条件（与 `verify_questbook_on_server.py` 第 4.b 步完全一致，两处必须同口径）：
      1. 报错来源是 **`ItemStack`**（= 有人在解析一个物品引用。任务书的判据/图标就在这一步）
      2. 命名空间不在已知噪声名单里
    """
    NOISE_NS = ("dndecor", "railways", "everycomp", "framedblocks")
    text = log.read_text(encoding="utf-8", errors="replace")
    seen: list[str] = []
    for ln in text.splitlines():
        if "Unknown registry key" not in ln or "ItemStack" not in ln:
            continue
        m = LOG_RE.search(ln)
        if not m:
            continue
        iid = m.group(1)
        if iid.split(":", 1)[0] in NOISE_NS:
            continue
        if iid not in seen:
            seen.append(iid)
    return seen


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="真机拒绝过的物品 id 黑名单")
    ap.add_argument("--scan", help="从这份服务端日志里提取被拒绝的 id 并合并进黑名单")
    ap.add_argument("--check", action="store_true", help="检查任务书是否用到黑名单里的 id")
    ap.add_argument("--ok-ns", help="列出该命名空间里**已被真机接受**的 id（用于替换）")
    args = ap.parse_args()

    if args.scan:
        found = scan(Path(args.scan))
        old = load_deny()
        new = [x for x in found if x not in old]
        merged = old + new
        lines = [
            "# 真机拒绝过的物品 id（服务端报 Unknown registry key ... minecraft:item）",
            "#",
            "# 来源：tools/runtime_deny.py --scan <服务端日志>",
            "# 纪律：**只增不删**（除非有新的真机证据证明它现在能用了）。",
            "# 每条后面的注释写清证据来源与日期。",
            "",
        ]
        for i in merged:
            mark = "  # 新增（本次 scan 发现）" if i in new else ""
            lines.append(i + mark)
        DENY.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
        print(f"扫描到 {len(found)} 条 · 黑名单共 {len(merged)} 条（新增 {len(new)}）")
        for i in new:
            print("  + " + i)
        return 0

    if args.check:
        deny = set(load_deny())
        used = toml_ids()
        hits = {i: f for i, f in used.items() if i in deny}
        print(f"黑名单 {len(deny)} 条 · 任务书里命中 {len(hits)} 条")
        for i, fs in sorted(hits.items()):
            print(f"  x {i}  <- {','.join(sorted(set(fs)))}")
        return 1 if hits else 0

    if args.ok_ns:
        reg = json.loads((HERE / "registry.json").read_text(encoding="utf-8"))
        deny = set(load_deny())
        used = toml_ids()
        known = set(reg["items"]) | set(reg["blocks"])
        cands = sorted(i for i in known
                       if i.startswith(args.ok_ns + ":") and i not in deny)
        accepted = [i for i in cands if i in used]
        print(f"{args.ok_ns}: 注册表 {len(cands)} 条，其中**任务书已用过（=真机已接受）** {len(accepted)} 条")
        p = HERE / "out" / f"ok_{args.ok_ns}.txt"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text("\n".join(f"{'ACCEPTED' if i in used else '        '}  {i}" for i in cands),
                     encoding="utf-8")
        print(f"-> {p}")
        return 0

    ap.error("至少给 --scan / --check / --ok-ns 之一")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())

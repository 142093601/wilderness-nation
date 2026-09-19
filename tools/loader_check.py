"""loader_check.py -- 扫一个 mods 目录，判断每个 jar 是"NeoForge 原生 / 双元数据 / 纯 Fabric"，
并揪出**声明依赖 connector 的 mod**。

为什么需要它（2026-09-19 真实事故）
----------------------------------
批 4 选了 `continuity`（连接材质）。它的 jar 是**双元数据**（同时有 fabric.mod.json 和
neoforge.mods.toml），所以"看有没有 NeoForge 元数据"这种粗筛**拦不住它**；
它真正的问题是 `neoforge.mods.toml` 里写着 **requires connector** → packwiz 解析依赖时
把清单里状态为 `hold` 的 `connector` 写进了 pack、还装进了客户端（T18）。
而 Connector 在场 + IPN 在场 = 客户端开世界必崩（C12），进而让两次验收变成假阳性（C13）。

判据（都来自 jar 自己的元数据，不猜）：
· `fabric.mod.json` 有 + `neoforge.mods.toml` 无  → **纯 Fabric**：本项目不走 Connector 路线时它是哑的
· 两个都有                                        → 双元数据（要再看它依赖谁）
· `neoforge.mods.toml`/`mods.toml` 里出现 connector/minecraft 之外的依赖 → 逐条列出
· 任何位置出现 `connector` 依赖                   → **红牌**

用法
----
    python tools/loader_check.py --mods "<实例>\\mods"
    python tools/loader_check.py --mods server\\mods --json data\\loader-check.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

TOML_NAMES = ["META-INF/neoforge.mods.toml", "META-INF/mods.toml"]
DEP_BLOCK = re.compile(r"\[\[dependencies\.[^\]]+\]\](.*?)(?=\n\[|\Z)", re.S)


def inspect(jar: Path) -> dict:
    out = {"jar": jar.name, "kind": "unknown", "requires_connector": False, "deps": []}
    try:
        with zipfile.ZipFile(jar) as z:
            names = set(z.namelist())
            has_fabric = "fabric.mod.json" in names
            has_neo = any(t in names for t in TOML_NAMES)
            if has_neo and not has_fabric:
                out["kind"] = "neoforge"
            elif has_fabric and not has_neo:
                out["kind"] = "fabric"
            elif has_fabric and has_neo:
                out["kind"] = "dual"
            else:
                out["kind"] = "meta-less"   # 加载器插件 / 语言提供者之类，正常
            for t in TOML_NAMES:
                if t not in names:
                    continue
                txt = z.read(t).decode("utf-8", "replace")
                for m in DEP_BLOCK.finditer(txt):
                    blk = m.group(1)
                    mid = re.search(r'modId\s*=\s*"([^"]+)"', blk)
                    typ = re.search(r'type\s*=\s*"([^"]+)"', blk)
                    if not mid:
                        continue
                    name, kind = mid.group(1), (typ.group(1) if typ else "?")
                    out["deps"].append("%s(%s)" % (name, kind))
                    if name == "connector":
                        out["requires_connector"] = True
                break
    except zipfile.BadZipFile:
        out["kind"] = "bad-zip"
    except FileNotFoundError:
        out["kind"] = "missing"
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="扫 mods 目录的加载器元数据与 connector 依赖")
    ap.add_argument("--mods", required=True, help="mods 目录")
    ap.add_argument("--json", help="结果写入该 JSON")
    args = ap.parse_args()

    jars = sorted(Path(args.mods).glob("*.jar"))
    results = [inspect(j) for j in jars]

    fabric_only = [r for r in results if r["kind"] == "fabric"]
    dual = [r for r in results if r["kind"] == "dual"]
    red = [r for r in results if r["requires_connector"]]

    print("扫到 %d 个 jar：neoforge %d · 双元数据 %d · 纯 Fabric %d · 无元数据 %d"
          % (len(results),
             sum(1 for r in results if r["kind"] == "neoforge"), len(dual), len(fabric_only),
             sum(1 for r in results if r["kind"] == "meta-less")))

    if fabric_only:
        print("\n⚠️ 纯 Fabric（不做 Connector 方案时它们是哑的）：")
        for r in fabric_only:
            print("   " + r["jar"])
    if red:
        print("\n🔴 声明需要 connector（必须拦下）：")
        for r in red:
            print("   %-56s deps=%s" % (r["jar"], ",".join(r["deps"])))
    elif not fabric_only:
        print("\n✅ 没有纯 Fabric、也没有声明依赖 connector 的 jar。")

    if args.json:
        Path(args.json).parent.mkdir(parents=True, exist_ok=True)
        Path(args.json).write_text(json.dumps(results, ensure_ascii=False, indent=1), encoding="utf-8")
        print("已写 " + args.json)
    return 1 if red else 0


if __name__ == "__main__":
    raise SystemExit(main())

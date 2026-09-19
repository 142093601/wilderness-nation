"""side_probe.py -- 从 **jar 自己的元数据**判断它是"仅客户端 / 仅服务端 / 双端"。

为什么还要这个工具（`side_check.py` 已经有了）
--------------------------------------------
`side_check.py` 拿 jar 的 SHA-1 去 Modrinth 反查官方标注的端侧 —— 这条路对
**CurseForge 独占分发**的 mod 是断的：CF 的文件不在 Modrinth 的哈希索引里，
于是 FTB 全家桶、WorldEdit、Spice of Life 这类会被判成"未知"（本包实测 **18 个**）。

而**答案就写在 jar 里**：NeoForge/Forge 的 `META-INF/neoforge.mods.toml`（旧版 `mods.toml`）
支持 `clientSideOnly` / `serverSideOnly` 两个布尔声明；Fabric 的 `fabric.mod.json` 有 `environment`。
读自己的声明，比读别人的索引可靠。

证据优先级（本工具只做第 1、2 条，第 3 条交人）
----------------------------------------------
1. `neoforge.mods.toml` / `mods.toml` 的 `clientSideOnly` / `serverSideOnly`
2. `fabric.mod.json` 的 `environment`
3. 都没有 → **报"未声明"**，不猜（双端 mod 通常什么都不写，所以"未声明"≠"双端"，
   但**只有客户端内容**的 mod 又常常忘了声明 → 不能反推）

用法
----
    python side_probe.py --mods "<实例>\\mods"                 # 全部
    python side_probe.py --mods <dir> --only XaeroPlus,particlerain
    python side_probe.py --jar <单个 jar>
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

# NeoForge 1.21 用 neoforge.mods.toml；Forge 旧版 / 部分 mod 仍放 mods.toml
TOML_NAMES = ["META-INF/neoforge.mods.toml", "META-INF/mods.toml"]


def declared_side(jar: Path) -> dict:
    """返回 {side, evidence, mods}。side ∈ client / server / both / undeclared。"""
    out = {"jar": jar.name, "side": "undeclared", "evidence": "", "mods": []}
    try:
        with zipfile.ZipFile(jar) as z:
            names = set(z.namelist())
            for tn in TOML_NAMES:
                if tn in names:
                    txt = z.read(tn).decode("utf-8", "replace")
                    out["mods"] = re.findall(r'modId\s*=\s*"([^"]+)"', txt)
                    c = re.search(r"clientSideOnly\s*=\s*true", txt)
                    s = re.search(r"serverSideOnly\s*=\s*true", txt)
                    if c and s:
                        out.update(side="both", evidence=tn + " 同时声明 only（矛盾，交人）")
                    elif c:
                        out.update(side="client", evidence=tn + " clientSideOnly=true")
                    elif s:
                        out.update(side="server", evidence=tn + " serverSideOnly=true")
                    else:
                        out.update(evidence=tn + " 无端侧声明")
                    return out
            if "fabric.mod.json" in names:
                try:
                    d = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"))
                    env = d.get("environment")
                    out["mods"] = [d.get("id", "?")]
                    if env == "client":
                        out.update(side="client", evidence="fabric.mod.json environment=client")
                    elif env == "server":
                        out.update(side="server", evidence="fabric.mod.json environment=server")
                    else:
                        out.update(evidence="fabric.mod.json 无 environment 或为 *")
                except Exception as e:  # noqa: BLE001
                    out["evidence"] = "fabric.mod.json 解析失败：%s" % e
    except zipfile.BadZipFile:
        out["evidence"] = "不是合法 zip"
    except FileNotFoundError:
        out["evidence"] = "文件不存在"
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="从 jar 元数据判断端侧")
    ap.add_argument("--mods", help="mods 目录")
    ap.add_argument("--jar", help="单个 jar")
    ap.add_argument("--only", help="逗号分隔的文件名关键字白名单")
    ap.add_argument("--json", help="结果写入该 JSON")
    args = ap.parse_args()

    jars: list[Path] = []
    if args.jar:
        jars = [Path(args.jar)]
    elif args.mods:
        jars = sorted(Path(args.mods).glob("*.jar"))
    else:
        print("要 --mods 或 --jar")
        return 2
    if args.only:
        keys = [k.strip().lower() for k in args.only.split(",") if k.strip()]
        jars = [j for j in jars if any(k in j.name.lower() for k in keys)]

    results = [declared_side(j) for j in jars]
    tally = {"client": 0, "server": 0, "both": 0, "undeclared": 0}
    for r in results:
        tally[r["side"]] = tally.get(r["side"], 0) + 1
        flag = {"client": "仅客户端", "server": "仅服务端",
                "both": "双端（有声明）", "undeclared": "未声明"}[r["side"]]
        print("%-58s %-14s %s" % (r["jar"][:58], flag, r["evidence"]))
    print("\n合计 %d 个：%s" % (len(results), " · ".join(
        "%s %d" % (k, v) for k, v in tally.items() if v)))

    if args.json:
        Path(args.json).parent.mkdir(parents=True, exist_ok=True)
        Path(args.json).write_text(json.dumps(results, ensure_ascii=False, indent=1),
                                   encoding="utf-8")
        print("已写 " + args.json)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

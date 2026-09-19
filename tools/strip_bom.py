"""strip_bom.py -- 去掉 mod jar 里 JSON 文件的 UTF-8 BOM（修"GBK 环境下启动即崩"）。

为什么需要它（2026-09-19 用户实测事故）
--------------------------------------
现象：用户用 PCL 启动就崩：
    The specified resource 'magistuarmory.mixins.json' was invalid or could not be read
    Caused by: JsonSyntaxException: Expected BEGIN_OBJECT but was STRING at line 1 column 1

真因：**Epic Knights 10.15 的 `magistuarmory.mixins.json` 带 UTF-8 BOM**（`EF BB BF`），
而 PCL 会给 JVM 加 `-Dfile.encoding=COMPAT` → 在中文 Windows 上等于**默认编码 GBK**。
同一份文件：

    UTF-8 解码：\\ufeff{  → Mixin 的 Gson 容忍 → 正常
    GBK   解码：锘縶      → 解析器看到的第一样东西不是 { → 报 "was STRING"

我们的自动化启动**不带** COMPAT，所以一直是 UTF-8、从没暴露这个问题 —— **这是"只有真人玩才会撞到"的坑**。

两条修法
--------
A. 启动器侧（推荐给真人玩）：JVM 参数加 `-Dfile.encoding=UTF-8`。注意 **JVM 以最后一个 `-Dfile.encoding=` 为准**，
   实测：`COMPAT` 带着、后面再跟 `UTF-8` → 生效的是 UTF-8；顺序反过来则不生效。
B. 文件侧（本工具）：把 jar 内 JSON 的 BOM 去掉。**只改本地实例**，不重新分发任何 mod 文件。
   注意：packwiz / materialize_pack 重新下载该 jar 后会覆盖回去 → 那时**重跑一次本工具**即可。

用法
----
    python tools/strip_bom.py --mods "<实例>\\mods"              # 预览（默认 dry-run）
    python tools/strip_bom.py --mods "<实例>\\mods" --apply      # 真改
    python tools/strip_bom.py --mods server\\mods --apply
"""

from __future__ import annotations

import argparse
import shutil
import sys
import zipfile
from pathlib import Path

BOM = b"\xef\xbb\xbf"


def scan(jar: Path) -> list[str]:
    """返回该 jar 里带 BOM 的 .json 条目名。"""
    try:
        with zipfile.ZipFile(jar) as z:
            return [n for n in z.namelist()
                    if n.lower().endswith(".json") and z.read(n)[:3] == BOM]
    except (zipfile.BadZipFile, OSError):
        return []


def rewrite(jar: Path, targets: set[str]) -> bool:
    """重写 jar，把 targets 里的条目去 BOM。写临时文件再替换，避免写坏。"""
    tmp = jar.with_suffix(jar.suffix + ".stripping")
    try:
        with zipfile.ZipFile(jar) as src, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as dst:
            for item in src.infolist():
                data = src.read(item.filename)
                if item.filename in targets and data[:3] == BOM:
                    data = data[3:]
                dst.writestr(item, data)
        shutil.move(str(tmp), str(jar))
        return True
    except Exception as exc:  # noqa: BLE001
        print("   !! 重写失败 %s：%s" % (jar.name, exc))
        tmp.unlink(missing_ok=True)
        return False


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:  # noqa: BLE001
        pass
    ap = argparse.ArgumentParser(description="去掉 mod jar 内 JSON 的 UTF-8 BOM")
    ap.add_argument("--mods", required=True, help="mods 目录")
    ap.add_argument("--apply", action="store_true", help="真的改（默认只预览）")
    args = ap.parse_args()

    mods = Path(args.mods)
    jars = sorted(mods.glob("*.jar"))
    if not jars:
        print("目录里没有 jar：" + str(mods))
        return 2

    total_files, total_jars, done = 0, 0, 0
    for jar in jars:
        bad = scan(jar)
        if not bad:
            continue
        total_jars += 1
        total_files += len(bad)
        mixin = [n for n in bad if "mixin" in n.lower()]
        flag = "  ← 含 mixin 配置（不修必崩）" if mixin else ""
        print("  %-52s %3d 个%s" % (jar.name[:52], len(bad), flag))
        if args.apply and rewrite(jar, set(bad)):
            done += 1
            left = scan(jar)
            print("      → 已去 BOM，剩余 %d 个" % len(left))

    print("\n合计：%d 个 jar / %d 个 JSON 带 BOM" % (total_jars, total_files))
    if args.apply:
        print("已处理 %d 个 jar。" % done)
        print("提示：packwiz / materialize_pack 重新下载这些 jar 后需要重跑本工具。")
    else:
        print("（这是预览。真要改就加 --apply）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

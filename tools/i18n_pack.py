"""把汉化产出合成资源包：① 可复核的源文件 ② 可分发的 zip。

两个产物为什么都要
------------------
* **源文件** `i18n/zh_cn/<命名空间>.json`（就是 {键: 中文}，无外层包装）——
  进 git，能 diff、能评审、能回滚。zip 是二进制，不能当唯一真相。
  ⚠️ **放在 `pack/` 外面**：`pack/` 是 packwiz 的领地，里面的文件都会进索引、被分发；
  源文件与 zip 内容重复，放进 `pack/` 会让玩家多下一份没用的东西，还会让每次
  `packwiz refresh` 都产生噪音 diff（见 `INCIDENTS.md` 2026-09-20 那条）。
* **zip** `pack/resourcepacks/荒野建国-中文补全.zip` —— 给 packwiz 分发的实际文件。
  MC 加载语言时会把多个资源包的同名 lang 文件**合并**，所以这个包里只放
  **社区包没覆盖的那些键**就够了（放全量只会让包变大，还会盖住社区后续更新）。

用法
----
    python tools/i18n_pack.py                      # 合成（写源文件 + 建 zip）
    python tools/i18n_pack.py --verify             # 只校验已有 zip 与源文件一致
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_OUT = ROOT / "data" / "i18n-out"
SRC_DIR = ROOT / "i18n" / "zh_cn"
ZIP_PATH = ROOT / "pack" / "resourcepacks" / "荒野建国-中文补全.zip"
INDEX_TOML = ROOT / "pack" / "index.toml"
# 资源包在 packwiz 里的落地路径（相对**实例根**，不是 pack/ 目录）
ZIP_REL = "resourcepacks/荒野建国-中文补全.zip"
PACK_MCMETA = {
    "pack": {
        "pack_format": 34,          # 1.21.1 资源包格式
        "description": "荒野建国 · 中文补全（Statecraft 项目自产，补社区汉化包未覆盖的键）",
    }
}
# 资源包内文件时间戳固定，保证同样输入产出同样字节（可校验、可复现）
FIXED_DATE = (2026, 9, 20, 0, 0, 0)


def load_sources() -> dict:
    """data/i18n-out/*.json → {命名空间: {键: 中文}}"""
    out: dict[str, dict] = {}
    for f in sorted(SRC_OUT.glob("*.json")):
        got = json.loads(f.read_text(encoding="utf-8"))
        for ns, kv in got.items():
            out.setdefault(ns, {}).update(kv)
    return out


def write_sources(src: dict) -> int:
    SRC_DIR.mkdir(parents=True, exist_ok=True)
    old = {p.stem for p in SRC_DIR.glob("*.json")}
    n = 0
    for ns, kv in sorted(src.items()):
        (SRC_DIR / f"{ns}.json").write_text(
            json.dumps(dict(sorted(kv.items())), ensure_ascii=False, indent=1) + "\n",
            encoding="utf-8")
        old.discard(ns)
        n += len(kv)
    for stale in sorted(old):          # 源文件里不再有的命名空间要删掉，否则 zip 会带上旧内容
        (SRC_DIR / f"{stale}.json").unlink()
        print(f"  清理过期源文件: {stale}.json")
    return n


def build_zip(src: dict) -> str:
    ZIP_PATH.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ZIP_PATH, "w", zipfile.ZIP_DEFLATED) as z:
        def put(name: str, text: str) -> None:
            info = zipfile.ZipInfo(name, date_time=FIXED_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, text)
        put("pack.mcmeta", json.dumps(PACK_MCMETA, ensure_ascii=False, indent=2) + "\n")
        for ns, kv in sorted(src.items()):
            put(f"assets/{ns}/lang/zh_cn.json",
                json.dumps(dict(sorted(kv.items())), ensure_ascii=False, indent=1) + "\n")
    return hashlib.sha256(ZIP_PATH.read_bytes()).hexdigest()


def verify(src: dict) -> int:
    if not ZIP_PATH.is_file():
        print(f"!! zip 不存在: {ZIP_PATH}")
        return 1
    bad = []
    with zipfile.ZipFile(ZIP_PATH) as z:
        names = set(z.namelist())
        if "pack.mcmeta" not in names:
            bad.append("缺少 pack.mcmeta")
        for ns, kv in sorted(src.items()):
            entry = f"assets/{ns}/lang/zh_cn.json"
            if entry not in names:
                bad.append(f"{ns}: zip 里没有 {entry}")
                continue
            got = json.loads(z.read(entry).decode("utf-8"))
            if got != kv:
                bad.append(f"{ns}: 内容与源文件不一致（zip {len(got)} 键 / 源 {len(kv)} 键）")
        extra = [n for n in names
                 if n.startswith("assets/") and n.split("/")[1] not in src]
        for n in extra:
            bad.append(f"zip 里多出源文件没有的命名空间: {n}")
    if bad:
        for b in bad:
            print("!! " + b)
        return 1
    print(f"校验通过：zip 与源文件一致（{len(src)} 个命名空间 / "
          f"{sum(len(v) for v in src.values())} 键），sha256={hashlib.sha256(ZIP_PATH.read_bytes()).hexdigest()[:16]}…")
    return 0


def update_index() -> str:
    """把资源包登记进 packwiz 的 pack/index.toml（幂等：已有条目只改哈希）。

    为什么手写而不用 packwiz：本机没装 packwiz（`packwiz refresh` 才是正规做法）。
    条目格式与 `mods/*.pw.toml` 同款：非 metafile 的普通文件 =
    `[[files]] file = "<实例内相对路径>" hash = "<sha256>"`（metafile 省略即 false）。

    ⚠️ 两个必须守住的细节（都踩过，2026-09-20）：
      1. `Path.write_text` 在 Windows 上是**文本模式**，会把 `\\n` 全转成 `\\r\\n` ——
         323 行的 index.toml 会被"重写"成 2326 行 diff。必须显式 `newline="\\n"`。
      2. 只改自己那几行：**不要** split/join 整个文件，否则会把条目之间的空行吃掉。
    """
    digest = hashlib.sha256(ZIP_PATH.read_bytes()).hexdigest()
    text = INDEX_TOML.read_text(encoding="utf-8") if INDEX_TOML.is_file() else ""
    lines = text.split("\n")
    marker = f'file = "{ZIP_REL}"'

    if marker in text:
        i = next(k for k, l in enumerate(lines) if l.strip() == marker)
        start = max(k for k in range(i) if lines[k].strip() == "[[files]]")
        for k in range(start, len(lines)):
            if lines[k].startswith("hash = "):
                lines[k] = f'hash = "{digest}"'
                break
        action = "已更新"
    else:
        while lines and lines[-1] == "":          # 规范成"单个结尾换行"
            lines.pop()
        lines += ["", "[[files]]", f'file = "{ZIP_REL}"', f'hash = "{digest}"', ""]
        action = "已添加"

    with INDEX_TOML.open("w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
    return f"{action} index.toml 条目：{ZIP_REL}（sha256 {digest[:16]}…）"


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass
    ap = argparse.ArgumentParser(description="合成中文补全资源包")
    ap.add_argument("--verify", action="store_true", help="只校验已有 zip 与源文件是否一致")
    ap.add_argument("--index", action="store_true", help="顺带把资源包登记进 pack/index.toml")
    args = ap.parse_args()

    src = load_sources()
    if not src:
        print(f"没有找到任何产出: {SRC_OUT}")
        return 1

    if args.verify:
        return verify(src)

    keys = write_sources(src)
    digest = build_zip(src)
    size_kb = ZIP_PATH.stat().st_size / 1024
    print(f"源文件: {SRC_DIR}（{len(src)} 个命名空间 / {keys} 键）")
    print(f"资源包: {ZIP_PATH}（{size_kb:.0f} KB）")
    print(f"sha256: {digest}")
    if args.index:
        print(update_index())
    return verify(src)


if __name__ == "__main__":
    raise SystemExit(main())

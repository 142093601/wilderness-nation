"""jar_probe.py -- 探一个 mod jar 里有什么：类路径、内部字符串、语言键。

为什么需要它（这个会话里已经用了四次）
--------------------------------------
判断"某个 mod 到底支不支持某功能"时，最可靠的证据是**它自己的代码**，而不是描述页：
  · OPAC 的命令根名（`oclaims`/`oparties`/`opac`）＝ 从 ClaimsCommandRegister 的字符串挖出来的；
  · OPAC 的 claim 参数结构（from/to/dimension/sub-id/as）＝ 从 ClaimsClaimCommands 的常量池挖出来的；
  · Undead Nights 的拆墙档位与命令 ＝ 源码核实（见 SELECTION.md §三·补）。
描述页会漏、会过期、会吹；字节码不会。

用法
----
    # 1) 看类路径（判断有没有某个子系统）
    python jar_probe.py --jar <jar> --entries "trains|logistics"

    # 2) 在 class 常量池里搜字符串（挖命令名、配置项、资源路径）
    python jar_probe.py --jar <jar> --strings "blockBreaking|difficulty"

    # 3) 看语言键（判断某功能的玩家可见文本，顺带能看出功能清单）
    python jar_probe.py --jar <jar> --lang-keys "train|package"

    # 4) 不指定 jar 时，按名字在实例 mods 目录里找
    python jar_probe.py --find create --entries "trains"
"""

from __future__ import annotations

import argparse
import json
import re
import struct
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pack_paths  # noqa: E402  同目录模块：读取本机配置

DEFAULT_MODS = Path(pack_paths.paths()["instance_dir"]) / "mods"


def utf8_constants(data: bytes) -> list[str]:
    """解析 class 常量池，取出所有 UTF-8 字符串（干净版，不靠正则猜）。"""
    if data[:4] != b"\xca\xfe\xba\xbe":
        return []
    i = 8
    count = struct.unpack(">H", data[i:i + 2])[0]
    i += 2
    out: list[str] = []
    k = 1
    while k < count:
        tag = data[i]
        i += 1
        if tag == 1:
            ln = struct.unpack(">H", data[i:i + 2])[0]
            i += 2
            out.append(data[i:i + ln].decode("utf-8", "replace"))
            i += ln
        elif tag in (7, 8, 16, 19, 20):
            i += 2
        elif tag == 15:
            i += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            i += 4
        elif tag in (5, 6):
            i += 8
            k += 1
        else:
            break
        k += 1
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="探 mod jar 的内容")
    ap.add_argument("--jar")
    ap.add_argument("--mods-dir", default=str(DEFAULT_MODS))
    ap.add_argument("--find", help="在 mods 目录里按文件名子串找 jar")
    ap.add_argument("--entries", help="正则：匹配类路径/资源路径")
    ap.add_argument("--strings", help="正则：在 class 常量池里搜字符串")
    ap.add_argument("--lang-keys", help="正则：在语言文件的键值里搜")
    ap.add_argument("--limit", type=int, default=60)
    args = ap.parse_args()

    jar: Path | None = Path(args.jar) if args.jar else None
    if jar is None and args.find:
        cands = sorted(Path(args.mods_dir).glob(f"*{args.find}*.jar"))
        if not cands:
            print(f"没找到匹配 '{args.find}' 的 jar")
            return 1
        jar = cands[0]
    if jar is None or not jar.is_file():
        print("需要 --jar 或 --find")
        return 2
    print(f"jar: {jar.name}  ({jar.stat().st_size / 1e6:.1f} MB)")

    z = zipfile.ZipFile(jar)
    names = z.namelist()

    if args.entries:
        pat = re.compile(args.entries, re.I)
        hits = [n for n in names if pat.search(n)]
        print(f"\n== 类/资源路径匹配 '{args.entries}'：{len(hits)} 条 ==")
        for n in hits[:args.limit]:
            print("  " + n)
        if len(hits) > args.limit:
            print(f"  ... 还有 {len(hits) - args.limit} 条")

    if args.strings:
        pat = re.compile(args.strings, re.I)
        seen: set[str] = set()
        for n in names:
            if not n.endswith(".class"):
                continue
            for s in utf8_constants(z.read(n)):
                if pat.search(s) and s not in seen and len(s) <= 80:
                    seen.add(s)
        print(f"\n== class 常量池匹配 '{args.strings}'：{len(seen)} 条 ==")
        for s in sorted(seen)[:args.limit]:
            print("  " + s)
        if len(seen) > args.limit:
            print(f"  ... 还有 {len(seen) - args.limit} 条")

    if args.lang_keys:
        pat = re.compile(args.lang_keys, re.I)
        for n in names:
            if not re.match(r"^(assets|data)/[^/]+/lang/en_us\.json$", n):
                continue
            try:
                data = json.loads(z.read(n).decode("utf-8"))
            except Exception:
                continue
            hits = {k: v for k, v in data.items() if pat.search(k) or pat.search(str(v))}
            print(f"\n== {n} 匹配 '{args.lang_keys}'：{len(hits)} 条 ==")
            for k in sorted(hits)[:args.limit]:
                print(f"  {k} = {hits[k]}")
            if len(hits) > args.limit:
                print(f"  ... 还有 {len(hits) - args.limit} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

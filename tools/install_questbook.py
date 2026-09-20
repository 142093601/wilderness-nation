"""把任务书数据从 pack/ 装进客户端实例（只碰 config/ftbquests/，不碰存档与设置）。

为什么不用 materialize_pack.py：它只落 **jar**（`mods/*.pw.toml` → jar），
不处理 config/ 下的普通文件。任务书是数据不是 mod，所以单独装。

安全边界（写在这里，免得以后手滑扩大范围）：
  * 目标只有 <instance>/config/ftbquests/
  * 绝不动 saves/ · options.txt · servers.dat · config/ftbquests-client.snbt
  * 幂等：同样内容重复跑不会堆垃圾（先清掉目标目录再复制）
  * 只复制 12 个 .snbt（4 章数据 + 语言文件）
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "pack" / "config" / "ftbquests"

# 绝不能碰的路径（写下来当护栏）
FORBIDDEN = ("saves", "options.txt", "servers.dat", "usercache.json")


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass

    ap = argparse.ArgumentParser(description="把任务书装进客户端实例")
    ap.add_argument("--instance", help="实例目录（默认读 pack.local.json）")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    if not SRC.is_dir():
        print(f"源不存在：{SRC}（先跑 tools/questbook.py）")
        return 1

    if args.instance:
        inst = Path(args.instance)
    else:
        sys.path.insert(0, str(ROOT / "tools"))
        try:
            from pack_paths import paths  # type: ignore
            inst = Path(paths()["instance_dir"])
        except Exception as exc:  # noqa: BLE001
            print(f"读不到实例路径（用 --instance 指定）：{exc}")
            return 1

    if not inst.is_dir():
        print(f"实例目录不存在：{inst}")
        return 1

    dest = inst / "config" / "ftbquests"
    # 护栏：目标必须落在实例的 config/ 下
    if "saves" in str(dest).lower() or any(f in str(dest) for f in FORBIDDEN):
        print(f"拒绝：目标路径看起来不安全 → {dest}")
        return 2

    files = sorted(p for p in SRC.rglob("*.snbt") if p.is_file())
    print(f"实例：{inst}")
    print(f"目标：{dest}")
    print(f"待装：{len(files)} 个 .snbt")
    for f in files:
        print(f"  {f.relative_to(SRC)}  {f.stat().st_size}")

    if args.dry_run:
        print("\n--dry-run：没有写任何东西")
        return 0

    if dest.is_dir():
        shutil.rmtree(dest)
    shutil.copytree(SRC, dest)

    # 复核：逐个比对字节
    bad = []
    for f in files:
        rel = f.relative_to(SRC)
        tgt = dest / rel
        if not tgt.is_file() or tgt.read_bytes() != f.read_bytes():
            bad.append(str(rel))
    print()
    if bad:
        print(f"装完复核失败 {len(bad)} 个：{bad}")
        return 1
    print(f"已装 {len(files)} 个文件，逐个比对字节一致。")
    print("下一步：开游戏 → 进任意世界 → 按 N 打开任务书。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

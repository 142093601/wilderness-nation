"""enable_cheats.py -- 把单机存档的 `allowCommands` 打开（自动化测试要用命令就必须开）。

为什么需要
----------
服务端世界拷成单机存档之后 `level.dat` 里 `allowCommands=0`：
**单人模式不开作弊时玩家没有命令权限**，于是 `/statecraft ...`、`setblock` 全部不可用，
脚本会报一堆"未知命令"，看起来像 mod 坏了。

做法：NBT 是 gzip 过的树，`allowCommands` 是一个 TAG_Byte，名字后面紧跟那个值的字节。
直接把它改成 1 再压回去 —— 比引一个 NBT 库便宜（这台机器上没有），也比手工改二进制可靠。

用法
----
    python enable_cheats.py "<存档目录>"      # 例如 .../saves/scdev
    python enable_cheats.py "<存档目录>" --check   # 只看现在的值
"""

from __future__ import annotations

import argparse
import gzip
import shutil
import sys
from pathlib import Path

KEY = b"allowCommands"


def read_level_dat(path: Path) -> bytes:
    with gzip.open(path, "rb") as f:
        return f.read()


def find_value_offset(data: bytes) -> int:
    i = data.find(KEY)
    if i < 0:
        raise SystemExit("level.dat 里找不到 allowCommands（格式变了？）")
    off = i + len(KEY)
    if off >= len(data):
        raise SystemExit("allowCommands 后面没有值字节")
    return off


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("save_dir")
    ap.add_argument("--check", action="store_true", help="只报告当前值，不改")
    args = ap.parse_args()

    level = Path(args.save_dir) / "level.dat"
    if not level.is_file():
        print(f"!! 找不到 {level}")
        return 2

    data = bytearray(read_level_dat(level))
    off = find_value_offset(data)
    before = data[off]
    if args.check:
        print(f"{level}：allowCommands={before}")
        return 0
    if before == 1:
        print(f"{level}：已经是 1，不动")
        return 0

    backup = level.with_suffix(".dat.bak")
    if not backup.is_file():
        shutil.copy2(level, backup)
        print(f"已备份 {backup.name}")

    data[off] = 1
    with gzip.open(level, "wb") as f:
        f.write(bytes(data))
    print(f"{level}：allowCommands {before} → 1")
    return 0


if __name__ == "__main__":
    sys.exit(main())

"""mca_probe.py -- 直接读 region 文件里的区块 NBT，回答"实体到底写进存档了没有"。

为什么需要它
------------
2026-09-20 在真服务端上撞到"重启后整个世界 0 个实体"（`DEFERRED.md` §J）。
要知道是**存的时候没写**还是**读的时候没装**，就得看盘上那份数据 ——
而 `.mca` 是"每 4KiB 一个扇区、每区块 zlib 压缩"的格式，grep 是搜不出来的。

用法
----
    python mca_probe.py --region <world>/region/r.0.0.mca --chunk 6 6
    python mca_probe.py --region ... --chunk 6 6 --dump entities.json

输出：该区块里 `Entities` 列表的 id 清单（以及 `block_entities` 的条数）。
"""

from __future__ import annotations

import argparse
import gzip
import json
import struct
import sys
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from nbt_dump import Reader, read_payload, TAG_COMPOUND  # noqa: E402


def read_chunk(region: bytes, cx: int, cz: int) -> bytes | None:
    """取某个区块的原始 NBT 字节（未解压判定之后）。"""
    idx = (cx & 31) + (cz & 31) * 32
    header = struct.unpack_from(">I", region, idx * 4)[0]
    offset = (header >> 8) * 4096
    if offset == 0:
        return None
    length = struct.unpack_from(">I", region, offset)[0]
    compression = region[offset + 4]
    payload = region[offset + 5: offset + 4 + length]
    if compression == 1:
        return gzip.decompress(payload)
    if compression == 2:
        return zlib.decompress(payload)
    if compression == 3:
        return payload                      # 未压缩
    raise ValueError(f"不认识的压缩方式 {compression}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--region", required=True)
    ap.add_argument("--chunk", nargs=2, type=int, required=True, metavar=("CX", "CZ"))
    ap.add_argument("--dump")
    args = ap.parse_args()

    cx, cz = args.chunk
    data = read_chunk(Path(args.region).read_bytes(), cx, cz)
    if data is None:
        print(f"区块 ({cx},{cz}) 在 region 里不存在（从没被存过）")
        return 1

    r = Reader(data)
    # 区块 NBT 是「根标签」形态：先是一个字节的类型、再是名字，然后才是载荷
    root_tag = r.u1()
    if root_tag != TAG_COMPOUND:
        print(f"根标签不是 compound（是 {root_tag}）——格式变了？")
        return 2
    r.string()                              # 根名字（通常是空串）
    root = read_payload(r, TAG_COMPOUND, 0)
    print(f"区块 ({cx},{cz})：根键 {len(root)} 个")

    ents = root.get("Entities")
    if not isinstance(ents, list):
        print("  Entities：**没有这个键**（这个区块里一个实体都没有存下来）")
    else:
        print(f"  Entities：{len(ents)} 个")
        for e in ents:
            eid = e.get("id")
            pos = e.get("Pos")
            name = e.get("CustomName")
            extra = e.get("NeoForgeData") or e.get("ForgeData")
            where = "" if pos is None else " @ %.1f,%.1f,%.1f" % tuple(pos[:3])
            print(f"    - {eid}{where}"
                  + (f" name={name}" if name else "")
                  + (f" data={extra}" if extra else ""))

    block_ents = root.get("block_entities")
    if isinstance(block_ents, list):
        print(f"  block_entities：{len(block_ents)} 个")

    if args.dump:
        Path(args.dump).write_text(json.dumps(root, ensure_ascii=False, indent=2),
                                   encoding="utf-8")
        print(f"  完整 NBT 已写 {args.dump}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

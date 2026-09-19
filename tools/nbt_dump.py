"""最小 NBT 读取器 —— 把 gzip 过的 .dat 打成 JSON，用来核对"存档到底写对了没有"。

为什么不装 nbtlib：这台机器上没有，而 NBT 的标签类型一共就十来个，
自己读一遍比拉一个依赖、再担心版本兼容便宜得多。

用法：
    python nbt_dump.py <文件.dat|.nbt> [--json 输出.json] [--max-list 3]

`--max-list N` 只打印列表的前 N 项（默认 3），因为 `nations` 有 12 项、每项十几个字段，
全打出来在终端里看不清；要全量就用 `--max-list 0`（0 = 全部）。
"""

from __future__ import annotations

import argparse
import gzip
import json
import struct
import sys
from pathlib import Path

TAG_END, TAG_BYTE, TAG_SHORT, TAG_INT, TAG_LONG = 0, 1, 2, 3, 4
TAG_FLOAT, TAG_DOUBLE, TAG_BYTE_ARRAY, TAG_STRING = 5, 6, 7, 8
TAG_LIST, TAG_COMPOUND, TAG_INT_ARRAY, TAG_LONG_ARRAY = 9, 10, 11, 12

TAG_NAMES = {
    TAG_BYTE: "byte", TAG_SHORT: "short", TAG_INT: "int", TAG_LONG: "long",
    TAG_FLOAT: "float", TAG_DOUBLE: "double", TAG_BYTE_ARRAY: "byte[]",
    TAG_STRING: "string", TAG_LIST: "list", TAG_COMPOUND: "compound",
    TAG_INT_ARRAY: "int[]", TAG_LONG_ARRAY: "long[]",
}


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.pos = 0

    def take(self, n: int) -> bytes:
        chunk = self.data[self.pos:self.pos + n]
        if len(chunk) != n:
            raise EOFError(f"读到文件尾：想要 {n} 字节，只剩 {len(chunk)}")
        self.pos += n
        return chunk

    def u1(self) -> int:
        return self.take(1)[0]

    def i1(self) -> int:
        return struct.unpack(">b", self.take(1))[0]

    def i2(self) -> int:
        return struct.unpack(">h", self.take(2))[0]

    def i4(self) -> int:
        return struct.unpack(">i", self.take(4))[0]

    def i8(self) -> int:
        return struct.unpack(">q", self.take(8))[0]

    def f4(self) -> float:
        return struct.unpack(">f", self.take(4))[0]

    def f8(self) -> float:
        return struct.unpack(">d", self.take(8))[0]

    def string(self) -> str:
        # NBT 字符串是**无符号** short 长度 + 改了编码的 UTF-8
        length = struct.unpack(">H", self.take(2))[0]
        return self.take(length).decode("utf-8", errors="replace")


def read_payload(reader: Reader, tag: int, max_list: int):
    if tag == TAG_BYTE:
        return reader.i1()
    if tag == TAG_SHORT:
        return reader.i2()
    if tag == TAG_INT:
        return reader.i4()
    if tag == TAG_LONG:
        return reader.i8()
    if tag == TAG_FLOAT:
        return reader.f4()
    if tag == TAG_DOUBLE:
        return reader.f8()
    if tag == TAG_BYTE_ARRAY:
        return list(reader.take(reader.i4()))
    if tag == TAG_STRING:
        return reader.string()
    if tag == TAG_LIST:
        item_tag = reader.u1()
        size = reader.i4()
        items = [read_payload(reader, item_tag, max_list) for _ in range(size)]
        if max_list and len(items) > max_list:
            return {"__list_size__": size, "__first__": items[:max_list]}
        return items
    if tag == TAG_COMPOUND:
        out = {}
        while True:
            child = reader.u1()
            if child == TAG_END:
                return out
            name = reader.string()
            out[name] = read_payload(reader, child, max_list)
    if tag == TAG_INT_ARRAY:
        return [reader.i4() for _ in range(reader.i4())]
    if tag == TAG_LONG_ARRAY:
        return [reader.i8() for _ in range(reader.i4())]
    raise ValueError(f"未知标签类型 {tag}")


def load(path: Path, max_list: int = 3):
    raw = path.read_bytes()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    reader = Reader(raw)
    root_tag = reader.u1()
    if root_tag != TAG_COMPOUND:
        raise ValueError(f"根标签不是 compound，而是 {TAG_NAMES.get(root_tag, root_tag)}")
    root_name = reader.string()
    return root_name, read_payload(reader, TAG_COMPOUND, max_list)


def main() -> int:
    ap = argparse.ArgumentParser(description="把 NBT 文件打成 JSON（只读）")
    ap.add_argument("path")
    ap.add_argument("--json", help="写到这个文件（默认打到 stdout）")
    ap.add_argument("--max-list", type=int, default=3, help="列表只打印前 N 项，0 = 全部")
    args = ap.parse_args()

    path = Path(args.path)
    if not path.is_file():
        print(f"找不到文件：{path}")
        return 2
    name, payload = load(path, args.max_list)
    text = json.dumps({"__root_name__": name, **payload}, ensure_ascii=False, indent=2)
    if args.json:
        Path(args.json).write_text(text, encoding="utf-8")
        print(f"已写出 {args.json}（{len(text)} 字符）")
    else:
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())

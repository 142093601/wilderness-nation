"""blueprint_check.py -- 批量校验图纸（.nbt），两层检查：

第一层：结构（离线，任何环境都能跑）
    - gzip 魔数、根键必须是 size / palette / blocks / entities
    - palette 条目只有 Name、**没有 Properties**（Create 6 的 Schematicannon 要的是裸方块 ID；
      带属性会让生成器产出非法方块状态 —— 这是"代码盖房子"最大的坑）
    - 所有方块坐标必须落在 size 范围内（坐标越界会把包围盒撑大 / 贴歪）
    - 空气是稀疏的（不记录）→ 顺带核对"记录方块数 ≤ 体积"

第二层：注册表（在线，需要服务端 + RCON）
    - 把图纸里用到的**每个方块 ID**拿去真实游戏里 `/setblock` 一次
    - 回 "Unknown block type" = 这个 ID 在包里不存在 → **生成器材质表写错了**
    - 这条才是真风险：格式对但方块名错，游戏里会当场失败

为什么不做"全部图纸都真放一遍"
    放一遍要人工盯着 Schematicannon（GUI + 材料），而同一套生成器写出的文件
    **格式与材质表是共享的**：单点已验证 Create 接受该格式（wall_ironhold 实测通过），
    本工具再覆盖"全部文件的结构一致性 + 全部方块 ID 的真实性"。
    剩下的是审美（是否好看），那属于只能人判的主观项。

用法
----
    python blueprint_check.py --dir schematics
    python blueprint_check.py --dir schematics --verify-registry       # 需要服务端在跑
"""

from __future__ import annotations

import argparse
import gzip
import json
import re
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

BLOCK_ID_RE = re.compile(r"^[a-z0-9_.-]+:[a-z0-9_./-]+$")
REQUIRED_ROOT = {"size", "palette", "blocks", "entities"}


# ── 极简 NBT 解析（只够读结构文件，避免依赖 nbtlib 的版本差异）──────────────────

class NbtReader:
    def __init__(self, data: bytes):
        self.d = data
        self.i = 0

    def u1(self) -> int:
        v = self.d[self.i]
        self.i += 1
        return v

    def u2(self) -> int:
        v = int.from_bytes(self.d[self.i:self.i + 2], "big", signed=False)
        self.i += 2
        return v

    def i4(self) -> int:
        v = int.from_bytes(self.d[self.i:self.i + 4], "big", signed=True)
        self.i += 4
        return v

    def raw(self, n: int) -> bytes:
        v = self.d[self.i:self.i + n]
        self.i += n
        return v

    def string(self) -> str:
        return self.raw(self.u2()).decode("utf-8", "replace")

    def payload(self, tag: int):
        if tag == 1:
            return self.u1()
        if tag == 2:
            return int.from_bytes(self.raw(2), "big", signed=True)
        if tag == 3:
            return self.i4()
        if tag == 4:
            return int.from_bytes(self.raw(8), "big", signed=True)
        if tag == 5:
            import struct
            return struct.unpack(">f", self.raw(4))[0]
        if tag == 6:
            import struct
            return struct.unpack(">d", self.raw(8))[0]
        if tag == 7:
            return list(self.raw(self.i4()))
        if tag == 8:
            return self.string()
        if tag == 9:
            item = self.u1()
            n = self.i4()
            return [self.payload(item) for _ in range(n)]
        if tag == 10:
            out = {}
            while True:
                t = self.u1()
                if t == 0:
                    break
                name = self.string()
                out[name] = self.payload(t)
            return out
        if tag == 11:
            n = self.i4()
            return [self.i4() for _ in range(n)]
        if tag == 12:
            n = self.i4()
            return [int.from_bytes(self.raw(8), "big", signed=True) for _ in range(n)]
        raise ValueError(f"未知 NBT tag {tag}（偏移 {self.i}）")


def read_nbt(path: Path) -> dict:
    with path.open("rb") as fh:
        head = fh.read(2)
        fh.seek(0)
        raw = fh.read()
    if head != b"\x1f\x8b":
        raise ValueError("不是 gzip（魔数不是 1f8b）")
    data = gzip.decompress(raw)
    r = NbtReader(data)
    tag = r.u1()
    if tag != 10:
        raise ValueError(f"根标签不是 compound（是 {tag}）")
    r.string()          # 根名（结构文件一般是空串）
    return r.payload(10)


# ── 检查 ──────────────────────────────────────────────────────────────────────

def check_structure(path: Path, base: Path | None = None) -> dict:
    # 只印文件名会骗人：不同子目录下的同名文件看起来像"重复行"。
    # 所以一律带相对路径。
    label = str(path.relative_to(base)) if base else path.name
    res: dict = {"file": label, "path": str(path), "ok": False, "problems": [], "blocks": 0,
                 "palette": 0, "size": None, "block_ids": []}
    try:
        nbt = read_nbt(path)
    except Exception as exc:
        res["problems"].append(f"读取失败: {type(exc).__name__}: {exc}")
        return res

    missing = REQUIRED_ROOT - set(nbt)
    if missing:
        res["problems"].append(f"缺根键: {sorted(missing)}")
    if "Properties" in json.dumps(nbt.get("palette", [])):
        res["problems"].append("palette 里出现了 Properties（Create 需要裸方块 ID）")

    size = nbt.get("size")
    if isinstance(size, list) and len(size) == 3:
        res["size"] = size
    else:
        res["problems"].append(f"size 不是 3 元整数列表: {size!r}")

    palette = nbt.get("palette") or []
    ids: list[str] = []
    for entry in palette:
        name = (entry or {}).get("Name") if isinstance(entry, dict) else None
        if not name:
            res["problems"].append(f"palette 条目没有 Name: {entry!r}")
            continue
        if not BLOCK_ID_RE.match(name):
            res["problems"].append(f"方块 ID 形式可疑（缺命名空间或含非法字符）: {name}")
        ids.append(name)
    res["palette"] = len(ids)
    res["block_ids"] = sorted(set(ids))

    blocks = nbt.get("blocks") or []
    res["blocks"] = len(blocks)
    if res["size"]:
        sx, sy, sz = res["size"]
        volume = sx * sy * sz
        if len(blocks) > volume:
            res["problems"].append(f"方块数 {len(blocks)} 超过体积 {volume}")
        out_of_range = 0
        for b in blocks:
            pos = (b or {}).get("pos")
            if not (isinstance(pos, list) and len(pos) == 3):
                out_of_range += 1
                continue
            if not (0 <= pos[0] < sx and 0 <= pos[1] < sy and 0 <= pos[2] < sz):
                out_of_range += 1
        if out_of_range:
            res["problems"].append(f"{out_of_range} 个方块坐标越界（0..size-1）")
        if not blocks:
            res["problems"].append("blocks 为空（图纸里没有方块）")
    res["ok"] = not res["problems"]
    return res


def collect_known_blocks(asset_sources: list[Path]) -> set[str]:
    """从 jar 里的 blockstates 反查"这个方块真实存在"。

    为什么用 blockstates 而不是启动游戏：每个注册方块都会有
    `assets/<命名空间>/blockstates/<路径>.json`（原版在客户端 jar 里，mod 在自己的 jar 里）。
    这样**完全离线**就能判定"图纸里的方块 ID 在包里存不存在"，不需要服务端也不需要开游戏。
    """
    known: set[str] = set()
    for jar in asset_sources:
        if not jar.is_file():
            continue
        try:
            z = zipfile.ZipFile(jar)
        except Exception:
            continue
        for name in z.namelist():
            m = re.match(r"^assets/([^/]+)/blockstates/(.+)\.json$", name)
            if m:
                ns, path = m.groups()
                known.add(f"{ns}:{path}")
    return known


def verify_registry_offline(block_ids: list[str], asset_sources: list[Path]) -> dict[str, str]:
    known = collect_known_blocks(asset_sources)
    return {b: ("OK" if b in known else "MISSING") for b in block_ids}


def verify_registry(block_ids: list[str], scratch=(0, 150, 0)) -> dict[str, str]:
    """在真实游戏里逐个 setblock，判定方块 ID 是否存在。需要服务端在跑。"""
    from server_ctl import Rcon
    r = Rcon()
    results: dict[str, str] = {}
    try:
        r.cmd("forceload add 0 0")
        x, y, z = scratch
        for bid in block_ids:
            out = r.cmd(f"setblock {x} {y} {z} {bid}")
            if "Unknown block" in out or "未知的方块" in out or "Unknown block type" in out:
                results[bid] = "MISSING"
            elif out.strip() == "":
                results[bid] = "NO_OUTPUT"
            else:
                results[bid] = "OK"
            # 换成空气，避免"相同方块状态"导致的误判
            r.cmd(f"setblock {x} {y} {z} minecraft:air")
        r.cmd(f"setblock {x} {y} {z} minecraft:air")
        r.cmd("forceload remove 0 0")
    finally:
        r.close()
    return results


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    import pack_paths   # 同目录模块：读本机配置（实例路径不进仓库）
    _inst = Path(pack_paths.paths()["instance_dir"])
    ap = argparse.ArgumentParser(description="批量校验图纸 .nbt")
    ap.add_argument("--dir", required=True)
    ap.add_argument("--offline-registry", action="store_true", default=True,
                    help="离线校验方块 ID 是否真实存在（读 jar 里的 blockstates，默认开）")
    ap.add_argument("--version-jar", default=str(_inst / f"{_inst.name}.jar"))
    ap.add_argument("--mods-dir", default=str(_inst / "mods"))
    ap.add_argument("--verify-registry", action="store_true",
                    help="改用运行时验证：连服务端逐个 setblock（更权威但需要服务端在跑）")
    ap.add_argument("--json")
    args = ap.parse_args()

    base = Path(args.dir)
    files = sorted(base.rglob("*.nbt"))
    if not files:
        print(f"没有 .nbt: {args.dir}")
        return 1

    results = [check_structure(f, base) for f in files]
    all_ids: set[str] = set()
    print(f"{'文件':<38} {'尺寸':<14} {'方块':>6} {'材质':>5}  结构")
    print("-" * 82)
    for r in results:
        size = "x".join(str(v) for v in r["size"]) if r["size"] else "?"
        print(f"{r['file']:<38} {size:<14} {r['blocks']:>6} {r['palette']:>5}  "
              f"{'OK' if r['ok'] else 'FAIL'}")
        for p in r["problems"]:
            print(f"      x {p}")
        all_ids |= set(r["block_ids"])

    bad = [r for r in results if not r["ok"]]
    print(f"\n结构: {len(results) - len(bad)}/{len(results)} 通过；合计用到 {len(all_ids)} 种方块")
    rc = 1 if bad else 0

    registry: dict[str, str] = {}
    if args.verify_registry:
        print(f"\n[运行时] 连服务端验证 {len(all_ids)} 个方块 ID ...")
        registry = verify_registry(sorted(all_ids))
    else:
        sources = [Path(args.version_jar)] + sorted(Path(args.mods_dir).glob("*.jar"))
        have = sum(1 for s in sources if s.is_file())
        print(f"\n[离线] 校验 {len(all_ids)} 个方块 ID 是否真实存在"
              f"（数据源：版本 jar + {have - 1} 个 mod jar 的 blockstates）...")
        registry = verify_registry_offline(sorted(all_ids), sources)

    missing = [k for k, v in registry.items() if v != "OK"]
    for bid in sorted(registry):
        if registry[bid] != "OK":
            print(f"  x {bid} -> {registry[bid]}（这个 ID 在包里不存在，生成器的材质表要改）")
    print(f"注册表: {len(all_ids) - len(missing)}/{len(all_ids)} 通过")
    if missing:
        rc = 1

    if args.json:
        Path(args.json).write_text(
            json.dumps({"files": results, "registry": registry}, ensure_ascii=False, indent=2),
            encoding="utf-8")
        print(f"已写: {args.json}")
    return rc


if __name__ == "__main__":
    raise SystemExit(main())

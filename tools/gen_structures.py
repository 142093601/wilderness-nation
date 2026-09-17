"""
标准件生成器原型 —— 验证「代码盖房子」这条路能不能走通。

产出两种格式：
  1) .schem  —— WorldEdit 用（mcschematic 生成），走 //schem load + //paste
  2) .nbt    —— 结构方块格式（gzip NBT），Create 的 Schematicannon 读的就是这种

设计原则（与预设一致）：
  * 省略方块属性：只写 `minecraft:stone_bricks`，不写 `[...]`。
    由游戏补默认状态 —— 这一步把"非法属性组合"的风险几乎清零。
  * 生成器出的是"壳"：内部通路、机器、箱子等由人工或后续参数补。
  * 内置校验：门洞高度、内部净高、材料清单 —— 这些是手工建造不会系统去做的事。

用法：
  python gen_structures.py [输出目录]
"""

from __future__ import annotations

import gzip
import io
import json
import sys
from collections import Counter
from pathlib import Path
from typing import Dict, Iterable, Tuple

import mcschematic
import nbtlib

Vec3 = Tuple[int, int, int]

# ── 风格：不同材质 = 不同风格，结构逻辑完全复用 ──────────────────────────────


class Style:
    def __init__(self, name: str, main: str, trim: str, base: str, accent: str):
        self.name = name
        self.main = main      # 主体
        self.trim = trim      # 装饰线条 / 垛口
        self.base = base      # 墙基
        self.accent = accent  # 点缀（窗台、门框）


STYLES = {
    "ironhold": Style("ironhold", "minecraft:stone_bricks", "minecraft:chiseled_stone_bricks",
                      "minecraft:cobbled_deepslate", "minecraft:deepslate_bricks"),
    "timber":   Style("timber", "minecraft:oak_planks", "minecraft:stripped_oak_log",
                      "minecraft:cobblestone", "minecraft:spruce_planks"),
    "sandstone": Style("sandstone", "minecraft:sandstone", "minecraft:cut_sandstone",
                       "minecraft:smooth_sandstone", "minecraft:chiseled_sandstone"),
}

AIR = "minecraft:air"


class Structure:
    """稀疏体素：只在有方块时记录，空气不占空间（也就不会撑爆文件）。"""

    def __init__(self) -> None:
        self.blocks: Dict[Vec3, str] = {}

    def set(self, x: int, y: int, z: int, block: str) -> None:
        if block == AIR:
            self.blocks.pop((x, y, z), None)
        else:
            self.blocks[(x, y, z)] = block

    def get(self, x: int, y: int, z: int) -> str:
        return self.blocks.get((x, y, z), AIR)

    def fill(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, block: str) -> None:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block)

    def hollow_box(self, x0: int, y0: int, z0: int, x1: int, y1: int, z1: int, block: str) -> None:
        """四面墙（不含顶底）。"""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                for z in range(z0, z1 + 1):
                    on_edge = x in (x0, x1) or z in (z0, z1)
                    if on_edge:
                        self.set(x, y, z, block)

    # ── 边界 ──
    def bounds(self) -> Tuple[Vec3, Vec3]:
        xs = [p[0] for p in self.blocks]
        ys = [p[1] for p in self.blocks]
        zs = [p[2] for p in self.blocks]
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

    def size(self) -> Vec3:
        lo, hi = self.bounds()
        return (hi[0] - lo[0] + 1, hi[1] - lo[1] + 1, hi[2] - lo[2] + 1)

    def normalized(self) -> "Structure":
        """平移到原点。"""
        lo, _ = self.bounds()
        out = Structure()
        for (x, y, z), b in self.blocks.items():
            out.set(x - lo[0], y - lo[1], z - lo[2], b)
        return out

    def materials(self) -> Counter:
        return Counter(self.blocks.values())


# ── 构件 ─────────────────────────────────────────────────────────────────────


def crenellations(st: Structure, x0: int, x1: int, z0: int, z1: int, y: int, block: str, period: int = 2) -> None:
    """墙顶垛口：每隔 period 格留一个实心垛。"""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            on_edge = x in (x0, x1) or z in (z0, z1)
            if on_edge and ((x + z) % period == 0):
                st.set(x, y, z, block)


def wall_segment(style: Style, length: int = 11, height: int = 4, thickness: int = 2,
                 gate_width: int = 2, gate_height: int = 3) -> Structure:
    """
    城墙段：可拼接（ends 为整厚，便于首尾相接）。
    中间留一个门洞：宽 gate_width、高 gate_height，上方有过梁。
    """
    st = Structure()
    z0, z1 = 0, thickness - 1
    # 墙基 + 主体
    st.fill(0, 0, z0, length - 1, height - 1, z1, style.main)
    st.fill(0, 0, z0, length - 1, 0, z1, style.base)
    # 顶部走道用装饰材
    st.fill(0, height - 1, z0, length - 1, height - 1, z1, style.trim)
    # 门洞
    gx0 = (length - gate_width) // 2
    st.fill(gx0, 1, z0, gx0 + gate_width - 1, gate_height, z1, AIR)
    # 门框
    for y in range(1, gate_height + 1):
        st.set(gx0 - 1, y, z0, style.accent)
        st.set(gx0 + gate_width, y, z0, style.accent)
    st.fill(gx0 - 1, gate_height + 1, z0, gx0 + gate_width, gate_height + 1, z1, style.accent)
    # 垛口
    crenellations(st, 0, length - 1, z0, z1, height, style.main)
    return st


def watchtower(style: Style, footprint: int = 7, height: int = 10, floor_h: int = 3) -> Structure:
    """
    哨塔：外壳 + 分层地板 + 窗缝 + 门洞。内部上下通路留 1×1 竖井（梯子由人工补）。
    """
    st = Structure()
    s = footprint
    # 外壳
    st.hollow_box(0, 0, 0, s - 1, height - 1, s - 1, style.main)
    # 墙基
    st.hollow_box(0, 0, 0, s - 1, 0, s - 1, style.base)
    # 角柱
    for (cx, cz) in ((0, 0), (s - 1, 0), (0, s - 1), (s - 1, s - 1)):
        st.fill(cx, 0, cz, cx, height - 1, cz, style.trim)
    # 门洞（南面）
    mid = s // 2
    st.fill(mid - 1, 1, 0, mid, 3, 0, AIR)
    for y in range(1, 4):
        st.set(mid - 2, y, 0, style.accent)
        st.set(mid + 1, y, 0, style.accent)
    st.fill(mid - 2, 4, 0, mid + 1, 4, 0, style.accent)
    # 窗缝（四个方向，各层）
    for y in range(floor_h + 2, height - 1, floor_h):
        st.fill(1, y, 0, s - 2, y + 1, 0, AIR)          # 南
        st.fill(1, y, s - 1, s - 2, y + 1, s - 1, AIR)  # 北
        st.set(0, y, mid, AIR); st.set(0, y, mid - 1, AIR)
        st.set(s - 1, y, mid, AIR); st.set(s - 1, y, mid - 1, AIR)
    # 分层地板（留 1×1 竖井）
    for y in range(floor_h, height - 1, floor_h):
        st.fill(1, y, 1, s - 2, y, s - 2, style.accent)
        st.set(1, y, 1, AIR)
    # 屋顶 + 垛口
    st.fill(0, height - 1, 0, s - 1, height - 1, s - 1, style.trim)
    for dx in range(1, s - 1):
        for dz in range(1, s - 1):
            st.set(dx, height, dz, AIR)
    crenellations(st, 0, s - 1, 0, s - 1, height, style.main)
    st.set(1, height, 1, AIR)  # 竖井出口
    return st


# ── 校验 ─────────────────────────────────────────────────────────────────────


def check_walkability(st: Structure) -> list[str]:
    """检查所有外立面的门洞是否至少 2 格高（代码盖房子最常见的翻车点）。"""
    problems = []
    lo, hi = st.bounds()
    for y in range(lo[1], hi[1] + 1):
        for x in range(lo[0], hi[0] + 1):
            for z in (lo[2], hi[2]):
                if st.get(x, y, z) == AIR and y == 1:
                    # 找到门洞底，向上数
                    h = 0
                    while st.get(x, 1 + h, z) == AIR:
                        h += 1
                    if h < 2:
                        problems.append(f"门洞过矮（{h} 格）at x={x} z={z}")
    return problems


def check_headroom(st: Structure, min_height: int = 3) -> list[str]:
    """粗略检查：内部是否存在至少 min_height 的连续空腔。"""
    lo, hi = st.bounds()
    for x in range(lo[0] + 1, hi[0]):
        for z in range(lo[2] + 1, hi[2]):
            run = 0
            best = 0
            for y in range(lo[1] + 1, hi[1]):
                run = run + 1 if st.get(x, y, z) == AIR else 0
                best = max(best, run)
            if best >= min_height:
                return []
    return [f"未找到净高 ≥ {min_height} 的内部空间"]


# ── 导出 ─────────────────────────────────────────────────────────────────────


def save_schem(st: Structure, out_dir: Path, name: str) -> Path:
    """导出 WorldEdit 的 .schem（Sponge 格式）。"""
    norm = st.normalized()
    schem = mcschematic.MCSchematic()
    for (x, y, z), block in norm.blocks.items():
        schem.setBlock((x, y, z), block)
    out_dir.mkdir(parents=True, exist_ok=True)
    schem.save(str(out_dir), name, mcschematic.Version.JE_1_21_1)
    return out_dir / f"{name}.schem"


def save_nbt(st: Structure, out_dir: Path, name: str) -> Path:
    """导出结构方块格式的 .nbt（gzip NBT）——Create 的 Schematicannon 读这种。"""
    norm = st.normalized()
    size = norm.size()
    palette: list[str] = []
    index: dict[str, int] = {}
    for block in norm.blocks.values():
        if block not in index:
            index[block] = len(palette)
            palette.append(block)

    blocks_list = nbtlib.List[nbtlib.Compound]()
    for (x, y, z), block in sorted(norm.blocks.items()):
        blocks_list.append(nbtlib.Compound({
            "pos": nbtlib.List[nbtlib.Int]([nbtlib.Int(x), nbtlib.Int(y), nbtlib.Int(z)]),
            "state": nbtlib.Int(index[block]),
        }))

    root = nbtlib.Compound({
        "size": nbtlib.List[nbtlib.Int]([nbtlib.Int(v) for v in size]),
        "palette": nbtlib.List[nbtlib.Compound](
            [nbtlib.Compound({"Name": nbtlib.String(b)}) for b in palette]
        ),
        "blocks": blocks_list,
        "entities": nbtlib.List[nbtlib.Compound](),
    })
    out_dir.mkdir(parents=True, exist_ok=True)
    path = out_dir / f"{name}.nbt"
    nbtlib.File(root, gzipped=True).save(str(path))
    return path


def verify_nbt(path: Path, st: Structure) -> str:
    """回读校验：文件里的方块数/尺寸是否与内存里一致。"""
    f = nbtlib.load(str(path), gzipped=True)
    size = [int(v) for v in f["size"]]
    n_blocks = len(f["blocks"])
    n_palette = len(f["palette"])
    expect = st.normalized()
    ok = (tuple(size) == expect.size()) and (n_blocks == len(expect.blocks)) and (n_palette == len(expect.materials()))
    return (f"回读 {'OK ' if ok else '不一致 '} size={tuple(size)} blocks={n_blocks} palette={n_palette} "
            f"(期望 {expect.size()} / {len(expect.blocks)} / {len(expect.materials())})")


# ── 主流程 ───────────────────────────────────────────────────────────────────

def main() -> int:
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("out")
    report: list[dict] = []
    rows: list[str] = []

    builds = {
        "wall": lambda s: wall_segment(s, length=11, height=4, thickness=2),
        "tower": lambda s: watchtower(s, footprint=7, height=10, floor_h=3),
    }
    # 校验要按构件类型区分：城墙段本来就没有"内部空间"，
    # 用建筑物的净高检查去套它只会得到假阳性（第一次跑就踩到了）。
    checks = {
        "wall": lambda st: check_walkability(st),
        "tower": lambda st: check_walkability(st) + check_headroom(st),
    }

    for style_name, style in STYLES.items():
        for kind, factory in builds.items():
            st = factory(style)
            name = f"{kind}_{style_name}"
            p_schem = save_schem(st, out / "schem", name)
            p_nbt = save_nbt(st, out / "nbt", name)
            problems = checks[kind](st)
            mats = st.materials()
            report.append({
                "name": name,
                "size": st.size(),
                "blocks": len(st.blocks),
                "palette": len(mats),
                "materials": dict(mats.most_common()),
                "problems": problems,
                "schem_bytes": p_schem.stat().st_size,
                "nbt_bytes": p_nbt.stat().st_size,
                "verify": verify_nbt(p_nbt, st),
            })
            rows.append(f"{name}: {st.size()} {len(st.blocks)} 方块 / {len(mats)} 种材质 "
                        f"| schem {p_schem.stat().st_size}B | nbt {p_nbt.stat().st_size}B "
                        f"| 校验 {'通过' if not problems else '有 ' + str(len(problems)) + ' 项'}")

    (out / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"输出目录: {out.resolve()}")
    for r in rows:
        print("  " + r)
    print("\n回读校验:")
    for item in report:
        print(f"  {item['name']}: {item['verify']}")
    print("\n校验问题:")
    any_problem = False
    for item in report:
        for p in item["problems"]:
            any_problem = True
            print(f"  [{item['name']}] {p}")
    if not any_problem:
        print("  无")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

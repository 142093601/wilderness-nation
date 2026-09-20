"""汉化覆盖分析：jar 里的英文键，**社区汉化资源包**到底补了多少，剩下哪些是真没人翻。

为什么要单独有这么一个工具
--------------------------
`lang_audit.py` 只看 jar 内部有没有 `assets/<ns>/lang/zh_cn.json`，看不到
I18nUpdateMod 同步进来的那份社区汉化包（本机实例里它**已启用**，见 options.txt 的
`resourcePacks` 与 `logs/I18nUpdateMod.log`）。不先算这一步，就会去手翻上万条
**其实已经有人翻好**的键 —— 本会话差点这么干（audit 报了 48 个 mod / 13166 键）。

判据：一个键算"已覆盖"= 它在 ①jar 自带的 zh_cn 里，或 ②社区汉化包的 zh_cn 里。
两边都不是 → 才进待翻清单。

用法
----
    python tools/i18n_cover.py --mods <实例mods目录> --packs <资源包zip或目录> [--json 报告] [--worklist 待翻清单]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

LANG_RE = re.compile(r"^(assets|data)/([^/]+)/lang/([a-z]{2}(?:_[a-z]{2})?)\.json$")

# 未覆盖键的用途分类：决定"哪些值得手翻"。
# 实测（2026-09-20，本实例）：未覆盖 11150 条里，方块/物品名占 5870 条（可组合生成），
# 剩下的界面/提示才是玩家真正会读的句子。
NAME_KEY_RE = re.compile(r"^(block|item|entity|tile|painting|biome|fluid|enchantment|effect)\.")
UI_KEY_RE = re.compile(r"^(gui|screen|button|menu|config|option|tooltip|jei|emi|message|msg|chat|"
                       r"command|commands|text|error|warning|advancement|subtitles)\.")


def classify(key: str) -> str:
    if NAME_KEY_RE.match(key):
        return "names"
    return "ui"


def _load_json_in_zip(z: zipfile.ZipFile, name: str) -> dict:
    try:
        data = json.loads(z.read(name).decode("utf-8"))
        return data if isinstance(data, dict) else {}
    except Exception:  # noqa: BLE001
        return {}


def read_jar(path: Path) -> dict:
    """→ {ns: {"en": {...}, "zh": {...}}}（只收 en_us / zh_cn）。"""
    out: dict[str, dict[str, dict]] = {}
    try:
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                m = LANG_RE.match(name)
                if not m:
                    continue
                _, ns, code = m.groups()
                code = code.lower()
                if code not in ("en_us", "zh_cn"):
                    continue
                slot = "en" if code == "en_us" else "zh"
                out.setdefault(ns, {"en": {}, "zh": {}})[slot] = _load_json_in_zip(z, name)
    except Exception:  # noqa: BLE001
        pass
    return out


def read_pack(path: Path) -> dict:
    """读资源包（zip 或目录）里的 zh_cn → {ns: {key: value}}。"""
    zh: dict[str, dict] = {}
    try:
        if path.is_dir():
            for f in path.rglob("*.json"):
                m = LANG_RE.match(f.relative_to(path).as_posix())
                if not m or m.group(3).lower() != "zh_cn":
                    continue
                try:
                    data = json.loads(f.read_text(encoding="utf-8"))
                except Exception:  # noqa: BLE001
                    continue
                if isinstance(data, dict):
                    zh.setdefault(m.group(2), {}).update(data)
        else:
            with zipfile.ZipFile(path) as z:
                for name in z.namelist():
                    m = LANG_RE.match(name)
                    if not m or m.group(3).lower() != "zh_cn":
                        continue
                    zh.setdefault(m.group(2), {}).update(_load_json_in_zip(z, name))
    except Exception:  # noqa: BLE001
        pass
    return zh


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="算社区汉化包对每个 mod 的覆盖率")
    ap.add_argument("--mods", required=True, help="实例 mods 目录")
    ap.add_argument("--packs", nargs="+", required=True, help="汉化资源包（zip 或目录），可给多个")
    ap.add_argument("--json", help="完整报告写到哪")
    ap.add_argument("--worklist", help="真正没人翻的键写到哪（JSON：{jar: {ns: {key: en}}}）")
    ap.add_argument("--split", help="把待翻清单按用途拆成两份写到这个目录（ui.json / names.json）")
    ap.add_argument("--per-ns", dest="per_ns",
                    help="把界面/提示那部分**按命名空间**拆成一个个小文件写到这个目录，便于分发翻译")
    ap.add_argument("--style", help="给每个命名空间抽 12 条**已存在**的中英对照写到这个目录，"
                                    "当翻译时的风格/术语参照（保证与社区包用词一致）")
    ap.add_argument("--top", type=int, default=15, help="打印前几名（按未覆盖键数）")
    args = ap.parse_args()

    mods_dir = Path(args.mods)
    jars = sorted(mods_dir.glob("*.jar"))
    if not jars:
        print(f"目录里没有 jar: {mods_dir}")
        return 1

    packs = []
    for p in args.packs:
        pp = Path(p)
        if pp.is_dir():
            packs.extend(sorted(pp.glob("*.zip")))
        else:
            packs.append(pp)
    print(f"资源包 {len(packs)} 份：")
    for p in packs:
        print(f"  - {p.name}（{p.stat().st_size / 1024 / 1024:.1f} MB）")

    pack_zh: dict[str, dict] = {}
    for p in packs:
        got = read_pack(p)
        for ns, kv in got.items():
            pack_zh.setdefault(ns, {}).update(kv)
    print(f"社区汉化包共含 {len(pack_zh)} 个命名空间 / {sum(len(v) for v in pack_zh.values())} 条键\n")

    rows = []
    for jar in jars:
        per_ns = read_jar(jar)
        en_total = sum(len(v["en"]) for v in per_ns.values())
        if en_total == 0:
            continue
        jar_zh = sum(len(v["zh"]) for v in per_ns.values())
        covered = uncovered = 0
        missing: dict[str, dict] = {}
        for ns, v in per_ns.items():
            for k, val in v["en"].items():
                if k in v["zh"] or k in pack_zh.get(ns, {}):
                    covered += 1
                else:
                    uncovered += 1
                    missing.setdefault(ns, {})[k] = val
        rows.append({
            "file": jar.name,
            "en": en_total,
            "jar_zh": jar_zh,
            "pack": covered - min(jar_zh, covered),   # 社区包单独补上的条数（近似：总覆盖 − jar 自带）
            "covered": covered,
            "uncovered": uncovered,
            "missing": missing,
        })

    rows.sort(key=lambda r: -r["uncovered"])
    w = min(46, max(len(r["file"]) for r in rows))
    print(f"{'jar'.ljust(w)} {'en':>6} {'jar内':>6} {'覆盖后':>6} {'仍未覆盖':>8}  覆盖率")
    print("-" * (w + 44))
    for r in rows[:args.top]:
        cov = 100 * r["covered"] / r["en"] if r["en"] else 0
        print(f"{r['file'][:w].ljust(w)} {r['en']:>6} {r['jar_zh']:>6} {r['covered']:>6} "
              f"{r['uncovered']:>8}  {cov:.0f}%")

    en_all = sum(r["en"] for r in rows)
    cov_all = sum(r["covered"] for r in rows)
    unc_all = sum(r["uncovered"] for r in rows)
    print(f"\n合计 {len(rows)} 个有文本的 jar：en 键 {en_all} · 已覆盖 {cov_all} "
          f"({100 * cov_all / en_all:.0f}%) · **仍未覆盖 {unc_all}**")
    full = [r for r in rows if r["uncovered"] == 0]
    print(f"社区包+自带已经 100% 覆盖的 jar：{len(full)} 个")

    if args.json:
        Path(args.json).write_text(
            json.dumps([{k: v for k, v in r.items() if k != "missing"} for r in rows],
                       ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"已写报告: {args.json}")
    if args.worklist:
        wl = {r["file"]: r["missing"] for r in rows if r["uncovered"] > 0}
        Path(args.worklist).write_text(json.dumps(wl, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"已写待翻清单: {args.worklist}（{unc_all} 条）")
    if args.split:
        out = Path(args.split)
        out.mkdir(parents=True, exist_ok=True)
        ui: dict = {}
        names: dict = {}
        for r in rows:
            for ns, kv in r["missing"].items():
                for k, v in kv.items():
                    (ui if classify(k) == "ui" else names).setdefault(ns, {})[k] = v
        n_ui = sum(len(v) for v in ui.values())
        n_nm = sum(len(v) for v in names.values())
        (out / "ui.json").write_text(json.dumps(ui, ensure_ascii=False, indent=1), encoding="utf-8")
        (out / "names.json").write_text(json.dumps(names, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"已拆分: 界面/提示 {n_ui} 条 → {out / 'ui.json'}；方块/物品名 {n_nm} 条 → {out / 'names.json'}")
    if args.per_ns:
        out = Path(args.per_ns)
        out.mkdir(parents=True, exist_ok=True)
        ui_by_ns: dict = {}
        for r in rows:
            for ns, kv in r["missing"].items():
                for k, v in kv.items():
                    if classify(k) == "ui":
                        ui_by_ns.setdefault(ns, {})[k] = v
        for ns, kv in ui_by_ns.items():
            (out / f"{ns}.json").write_text(json.dumps({ns: kv}, ensure_ascii=False, indent=1),
                                            encoding="utf-8")
        total = sum(len(v) for v in ui_by_ns.values())
        print(f"已按命名空间拆分: {len(ui_by_ns)} 份 / {total} 条 → {out}")
    if args.style:
        out = Path(args.style)
        out.mkdir(parents=True, exist_ok=True)
        n_ns = n_pairs = 0
        for jar in jars:
            per_ns = read_jar(jar)
            for ns, v in per_ns.items():
                if not v["en"]:
                    continue
                # 已有的中文：jar 优先，社区包补齐
                have = dict(pack_zh.get(ns, {}))
                have.update(v["zh"])
                pairs = [(k, v["en"][k], have[k]) for k in sorted(v["en"]) if k in have][:12]
                if not pairs:
                    continue
                n_ns += 1
                n_pairs += len(pairs)
                (out / f"{ns}.json").write_text(
                    json.dumps({ns: {k: {"en": en, "zh": zh} for k, en, zh in pairs}},
                               ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"已抽风格参照: {n_ns} 个命名空间 / {n_pairs} 组中英对照 → {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

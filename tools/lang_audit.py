"""lang_audit.py -- 静态审计：每个 mod 有没有中文（zh_cn），缺多少。

为什么值得做成工具
------------------
"组 5：中文界面 + 中文字体"一直是悬着的一项，而它**完全可以静态判定**：
mod 的语言文件就在 jar 里的 `assets/<ns>/lang/*.json`（少数在 `data/<ns>/lang/`）。
不需要开游戏、不需要看界面，直接回答"哪些 mod 会显示英文"。

实测结论（2026-09-15）：MC 本体 `options.txt` 里 lang 已是 `zh_cn`，
所以**原版界面本来就是中文**；缺的是各 mod 自带文本的汉化 —— 本工具给出精确清单。

用法
----
    python lang_audit.py --mods <实例mods目录>
    python lang_audit.py --mods <目录> --json data/lang-audit.json --worklist lists/i18n-todo.txt
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import zipfile
from pathlib import Path

LANG_RE = re.compile(r"^(assets|data)/([^/]+)/lang/([a-z]{2}(?:_[a-z]{2})?)\.json$")


def audit_jar(path: Path) -> dict:
    entry: dict = {"file": path.name, "namespaces": {}, "error": None}
    try:
        z = zipfile.ZipFile(path)
    except Exception as exc:
        entry["error"] = f"{type(exc).__name__}: {exc}"
        return entry

    per_ns: dict[str, dict[str, int]] = {}
    for name in z.namelist():
        m = LANG_RE.match(name)
        if not m:
            continue
        _, ns, code = m.groups()
        try:
            data = json.loads(z.read(name).decode("utf-8"))
            count = len(data) if isinstance(data, dict) else -1
        except Exception:
            count = -1
        per_ns.setdefault(ns, {})[code] = count

    entry["namespaces"] = per_ns
    # 只统计有实际文本（en_us 或任意 en_*）的命名空间；纯数据包可能只有 data/lang
    entry["has_zh"] = any("zh_cn" in codes for codes in per_ns.values())
    entry["en_keys"] = sum(codes.get("en_us", 0) for codes in per_ns.values())
    entry["zh_keys"] = sum(codes.get("zh_cn", 0) for codes in per_ns.values())
    entry["other_langs"] = sorted({c for codes in per_ns.values() for c in codes if c not in ("en_us", "zh_cn")})
    return entry


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="审计 mod 的中文覆盖")
    ap.add_argument("--mods", required=True)
    ap.add_argument("--json")
    ap.add_argument("--worklist", help="把「没有中文」的 mod 写到这里")
    args = ap.parse_args()

    jars = sorted(Path(args.mods).glob("*.jar"))
    if not jars:
        print(f"目录里没有 jar: {args.mods}")
        return 1

    rows = [audit_jar(j) for j in jars]
    rows.sort(key=lambda r: (r["has_zh"], -r["en_keys"], r["file"]))

    w = min(50, max(len(r["file"]) for r in rows))
    print(f"{'jar'.ljust(w)}  {'zh_cn':<6} {'en键':>6} {'zh键':>6}  覆盖率")
    print("-" * (w + 34))
    for r in rows:
        if r["error"]:
            print(f"{r['file'][:w].ljust(w)}  ERROR  {r['error']}")
            continue
        cov = "—"
        if r["en_keys"] > 0:
            cov = f"{100 * r['zh_keys'] / r['en_keys']:.0f}%"
        print(f"{r['file'][:w].ljust(w)}  {'有' if r['has_zh'] else '无':<6} "
              f"{r['en_keys']:>6} {r['zh_keys']:>6}  {cov}")

    have = [r for r in rows if r["has_zh"] and not r["error"]]
    lack = [r for r in rows if not r["has_zh"] and not r["error"] and r["en_keys"] > 0]
    silent = [r for r in rows if not r["has_zh"] and not r["error"] and r["en_keys"] == 0]

    print(f"\n合计 {len(rows)} 个 jar：有中文 {len(have)} · **没中文 {len(lack)}** · 无文本 {len(silent)}")
    if lack:
        print("\n没中文的（会显示英文）：")
        for r in lack:
            print(f"  - {r['file']}  （en 键 {r['en_keys']}）")

    if args.worklist:
        out = Path(args.worklist)
        out.parent.mkdir(parents=True, exist_ok=True)
        lines = ["# 需要汉化的 mod（由 tools/lang_audit.py 生成）", ""]
        for r in lack:
            lines.append(f"{r['file']}\t{r['en_keys']} 键\t其他语言: {','.join(r['other_langs']) or '无'}")
        out.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"\n已写: {out}")
    if args.json:
        Path(args.json).write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"已写: {args.json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

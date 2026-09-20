"""汉化产出的独立验收：键集合 / 占位符 / 空值 / 残留英文 —— **不看子代理的自检**。

为什么要单独有它
----------------
翻译是并发分发的（每个命名空间一个产出文件），子代理各自报告"自检 OK"，
但**自检代码是它们自己写的**，口径不一致（有的只比键数、有的漏了 % 参数）。
这份验收器统一口径，并且把"合理保留英文"和"漏译"分开报：
    * 合理：纯占位符（"%s"）、纯符号数字（"-"、"%1$s x %2$s"）、品牌/模组名（对照白名单）
    * 可疑：含有成句英文单词（2 个以上连续英文词）却没人翻译

用法
----
    python tools/i18n_verify.py --batches data/i18n-batches --out data/i18n-out
    python tools/i18n_verify.py ... --report data/i18n-verify.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

# 占位符与格式：%s %d %1$s %2$d %% §a §l 必须一一对应（严格）
PH_RE = re.compile(r"%(?:\d+\$)?[sdf]|%%|§[0-9a-fk-or]")
# 换行只做**提示**不做判错：英文原文常按自己的宽度折行，中文重排后行数会变，
# 这不影响渲染（实测 everycomp 的 3 条 description 就是这种情况 —— 起初被我误判成"占位符不符"）。
NL_RE = re.compile(r"\n")
# 纯占位符/符号/数字：这些东西本来就没什么可翻
TRIVIAL_RE = re.compile(r"^[\s\W\d_]*$")
# 成句英文：连续两个及以上拉丁单词（品牌名一般是单个词，或首字母大写专名）
ENGLISH_PHRASE_RE = re.compile(r"\b[A-Za-z]{2,}(?:\s+[A-Za-z]{2,})+\b")
# 这些词出现在中文里是合理保留（模组/平台/技术缩写/品牌）。
# 白名单来自 2026-09-20 那次全量人工复核：91 条被标"疑似英文"的值里，
# 全部是命令字面量、模组名、曲名/作者、或刻意保留的技术词 —— 没有一条是漏译。
KEEP_WORDS = {
    "minecraft", "create", "ftb", "jei", "emi", "ae2", "nbt", "snbt", "tps", "rf", "fe", "su",
    "opac", "xaero", "curseforge", "modrinth", "github", "discord", "patreon", "youtube",
    "twitter", "marketplace", "neoforge", "forge", "fabric", "cloth config", "yacl", "jade",
    "xyz", "uuid", "json", "rgb", "argb", "gpu", "api", "url", "ui", "gui", "rpm", "mb",
    "kb", "gb", "kb/s", "mb/s", "hp", "xp", "cd", "dvd", "id", "ids", "ok", "y", "n", "mod",
    "mods", "wiki", "shift", "ctrl", "alt", "tab", "esc", "emc", "uri", "smp",
    # 模组名/品牌/平台（复核后确认要保留的）
    "every", "compat", "moonlight", "map", "atlases", "portable", "blueprints", "undead",
    "nights", "epic", "knights", "armor", "ages", "enhanced", "celestials", "colorful", "hearts",
    "small", "ships", "another", "furniture", "simply", "tooltips", "certain", "questing",
    "additions", "builders", "crafts", "cherished", "worlds", "deep", "resonance", "dye", "depot",
    "omega", "mute", "waystones", "wraith", "better", "pvp", "pick", "your", "poison", "mineprint",
    "ebe", "sulfur", "cube", "codecui", "codec", "schema", "map", "record", "bean", "raw",
    "dispatch", "either", "pair", "pattern", "optional", "long", "hex", "neoforge", "configured",
    "cloth", "config", "quark", "tinkers", "construct", "mcjtylib", "lootr", "resonance",
}
# 命令字面量：含 `/命令` 的串（如 "/track all"、"改用/locate structure #x:y"）一律不算漏译
COMMAND_RE = re.compile(r"/[a-z_]+")


def ph_multiset(s: str) -> list:
    return sorted(PH_RE.findall(s))


def suspicious_english(s: str) -> bool:
    """含成句英文（≥2 个连续英文词的片段），且该片段不在保留白名单里 → 可疑漏译。

    含 `/命令` 的串直接放行：命令字面量必须原样保留（"要查看请执行 /track help 3"）。
    """
    if COMMAND_RE.search(s):
        return False
    for m in ENGLISH_PHRASE_RE.finditer(s):
        frag = m.group(0).lower()
        if any(w in frag.split() for w in KEEP_WORDS):
            continue
        # 全是白名单词组成的片段也放行
        if all(w in KEEP_WORDS for w in re.findall(r"[a-z]+", frag)):
            continue
        return True
    return False


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="汉化产出独立验收")
    ap.add_argument("--batches", default="data/i18n-batches")
    ap.add_argument("--out", default="data/i18n-out")
    ap.add_argument("--report")
    ap.add_argument("--quiet-ok", action="store_true", help="只打印有问题的")
    args = ap.parse_args()

    bat, out = Path(args.batches), Path(args.out)
    rows, errors = [], []
    for f in sorted(out.glob("*.json")):
        ns = f.stem
        src_file = bat / f"{ns}.json"
        if not src_file.is_file():
            errors.append(f"{ns}: 没有对应的批次文件 {src_file}")
            continue
        src = json.loads(src_file.read_text(encoding="utf-8"))[ns]
        got = json.loads(f.read_text(encoding="utf-8")).get(ns, {})

        miss = sorted(set(src) - set(got))
        extra = sorted(set(got) - set(src))
        phbad = [k for k in set(src) & set(got) if ph_multiset(src[k]) != ph_multiset(got[k])]
        nl_diff = [k for k in set(src) & set(got)
                   if len(NL_RE.findall(src[k])) != len(NL_RE.findall(got[k]))]
        empty = [k for k in set(src) & set(got) if src[k].strip() and not str(got[k]).strip()]
        bad_empty = [k for k in set(src) & set(got) if not src[k].strip() and str(got[k]).strip()]
        leftover = [k for k in set(src) & set(got)
                    if not TRIVIAL_RE.match(str(got[k])) and suspicious_english(str(got[k]))]
        nontranslated = [k for k in set(src) & set(got)
                         if src[k].strip() and str(got[k]).strip() == src[k].strip()]
        ok = not (miss or extra or phbad or empty or bad_empty)
        if not ok:
            errors.append(f"{ns}: 缺{len(miss)} 多{len(extra)} 占位符{len(phbad)} 空{len(empty)} 违空{len(bad_empty)}")
        rows.append({
            "ns": ns, "keys": len(src), "translated": len(set(src) & set(got)),
            "missing": miss, "extra": extra, "phbad": phbad, "empty": empty,
            "nl_diff": nl_diff,
            "suspicious_english": leftover, "untranslated": nontranslated, "ok": ok,
        })

    rows.sort(key=lambda r: (r["ok"], -r["keys"]))
    cover = sum(r["translated"] for r in rows)
    total = sum(r["keys"] for r in rows)
    for r in rows:
        if args.quiet_ok and r["ok"] and not r["suspicious_english"] and not r["untranslated"]:
            continue
        flag = "OK " if r["ok"] else "!! "
        note = []
        if r["missing"]:
            note.append("缺%d" % len(r["missing"]))
        if r["extra"]:
            note.append("多%d" % len(r["extra"]))
        if r["phbad"]:
            note.append("占位符%d" % len(r["phbad"]))
        if r["empty"]:
            note.append("空%d" % len(r["empty"]))
        if r["untranslated"]:
            note.append("未译%d" % len(r["untranslated"]))
        if r["nl_diff"]:
            note.append("换行差异%d" % len(r["nl_diff"]))
        if r["suspicious_english"]:
            note.append("疑似英文%d" % len(r["suspicious_english"]))
        print(f"{flag}{r['ns']:<28} {r['translated']:>4}/{r['keys']:<4} {' '.join(note)}")

    print(f"\n合计 {len(rows)} 个命名空间：{cover} / {total} 条已产出")
    for r in rows:
        for k in r["untranslated"][:3]:
            print(f"  未译 {r['ns']}: {k}")
    bad = [r for r in rows if not r["ok"]]
    print(f"结构性问题（键/占位符/空值）：{len(bad)} 个命名空间")
    for e in errors:
        print("  !! " + e)

    if args.report:
        Path(args.report).write_text(json.dumps(
            {"cover": cover, "total": total, "rows": rows}, ensure_ascii=False, indent=1),
            encoding="utf-8")
        print(f"已写报告: {args.report}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())

"""一次性把"时代时长翻倍（150h → 300h）"同步到各文档。

为什么要写脚本而不是手改：这些数字散在 5 个文档里共 20+ 处，手改必漏；
但也**不能**盲目全局替换 —— `1500 年`（开局的虚构年份）里含 "150"，任务数
`90~110` 是按 150h 算出来的**分析结论**（不能直接翻倍当成已验证），
`CHANGELOG.md` 里那一处是**历史记录**（记的是当时的口径，不该被改写）。

所以规则写死在这里：只替换"时长口径"这个语义下的数字，并把需要重算的地方标出来。
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 只动"时长口径"语义：整条数字串 + "150 小时" + 依赖它的时长结论
COMMON = [
    ("12 / 20 / 25 / 25 / 30 / 38", "24 / 40 / 50 / 50 / 60 / 76"),
    ("12/20/25/25/30/38", "24/40/50/50/60/76"),
    ("150 小时", "300 小时"),
    ("3.5~7 个月", "7~14 个月"),
    ("15~30 周", "30~60 周"),
]

# 每个文件额外的、只属于它的替换
EXTRA = {
    "DESIGN.md": [
        # 27 个任务"只够撑约 1/4"是按 150h 算的；时长翻倍后这个比例也翻倍
        ("它只够撑约 1/4", "它只够撑约 1/8"),
    ],
}

# CHANGELOG 是历史记录：口径变更要**新增一条**，不能改写旧条目
SKIP = {"CHANGELOG.md"}


def main() -> int:
    changed_total = 0
    for name in ("DESIGN.md", "README.md", "SELECTION.md", "tools/sample_report.md",
                 "NATIONS.md", "CHANGELOG.md"):
        path = ROOT / name
        if not path.is_file():
            print(f"跳过（不存在）: {name}")
            continue
        text = path.read_text(encoding="utf-8")
        original = text
        hits = 0
        if name not in SKIP:
            for old, new in COMMON + EXTRA.get(name, []):
                n = text.count(old)
                if n:
                    text = text.replace(old, new)
                    hits += n
                    print(f"  {name}: {old!r} -> {new!r}  x{n}")
        if text != original:
            path.write_text(text, encoding="utf-8", newline="")
            changed_total += hits
        else:
            print(f"  {name}: 无改动")
    print(f"共替换 {changed_total} 处")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

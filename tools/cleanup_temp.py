#!/usr/bin/env python3
"""安全清理 tools/ 下的临时文件：先查 git，再删。被跟踪的一律不动。

为什么需要这个脚本：我两次用「文件名模式」（`tools/_*.py` = 我看上去的临时文件）
批量删除，两次都误删了 `tools/_selftest.py` —— 它名字带 `_`，但**是被 git 跟踪的
正式文件**（`.gitignore` 里唯一一条 `!tools/_selftest.py` 例外就是说这件事）。
两次都靠 `git checkout --` 恢复了，零损失，但同类错误出现两次说明：
**靠"记住一条规则"是防不住的，得把检查变成动作。**

用法：
    python tools/cleanup_temp.py                # 干跑，只列出将被删除的文件
    python tools/cleanup_temp.py --delete       # 真的删除
    python tools/cleanup_temp.py --pattern "tools/_*.py" --delete

退出码：0 = 安全（没有跟踪文件会被误删）；2 = 检测到跟踪文件，已中止。
"""

from __future__ import annotations

import argparse
import fnmatch
import subprocess
import sys
from pathlib import Path

# 默认清理目标：我惯用的临时文件命名
DEFAULT_PATTERN = "tools/_*.py"

# 被跟踪但仍想保留的（正式文件）—— 由 git 判定，不靠此表；
# 此表只用于"已知的正式文件"的醒目提示。
KNOWN_TRACKED = {"tools/_selftest.py"}


def repo_root(start: Path) -> Path:
    """向上找到含 .git 的目录；找不到就退回起始目录。"""
    cur = start.resolve()
    for cand in (cur, *cur.parents):
        if (cand / ".git").exists():
            return cand
    return cur


def default_root() -> Path:
    """默认操作对象：**本脚本所在的仓库**，而非当前工作目录。

    为什么不能用 cwd：本机的 pwsh 默认 cwd 是 `D:\\project`，而 `D:\\project`
    **自己也是一个 git 仓库**。于是「向上找 .git」会就近命中 `D:\\project`，
    脚本就去搜 `D:\\project\\tools\\` 而不是 nation-pack —— 一个静默的错目标。
    锚在脚本自身位置，无论从哪个目录调用都指向同一个仓库。
    """
    return repo_root(Path(__file__).resolve().parent)


def git_ls_files(root: Path) -> set[str]:
    """仓库内所有被 git 跟踪的文件（相对仓库根，正斜杠）。"""
    out = subprocess.run(
        ["git", "-C", str(root), "ls-files"],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if out.returncode != 0:
        print(f"[warn] git ls-files 失败: {out.stderr.strip()}", file=sys.stderr)
        return set()
    return {line.strip() for line in out.stdout.splitlines() if line.strip()}


def main() -> int:
    ap = argparse.ArgumentParser(description="安全清理临时文件（先查 git 再删）")
    ap.add_argument("--pattern", default=DEFAULT_PATTERN,
                    help=f"相对仓库根的 glob，默认 {DEFAULT_PATTERN!r}")
    ap.add_argument("--root", default=None,
                    help="仓库根；默认 = 本脚本所在仓库（不是 cwd）")
    ap.add_argument("--delete", action="store_true",
                    help="真的删除；不加则只干跑")
    args = ap.parse_args()

    root = Path(args.root).resolve() if args.root else default_root()
    tracked = git_ls_files(root)

    # 防呆：确认这个根确实是 git 仓库，否则 git ls-files 会返回空，
    # 于是"没有跟踪文件"这个结论是假的，会把正式文件当临时文件删掉。
    if not (root / ".git").exists():
        print(f"[FAIL] {root} 不是 git 仓库根 —— 无法安全判定哪些文件被跟踪。中止。",
              file=sys.stderr)
        return 2

    candidates: list[Path] = []
    for p in sorted(root.glob(args.pattern)):
        if p.is_file():
            candidates.append(p)

    if not candidates:
        print(f"没有匹配 {args.pattern!r} 的文件。")
        return 0

    rel = {p.relative_to(root).as_posix(): p for p in candidates}
    protected = sorted(r for r in rel if r in tracked)
    deletable = sorted(r for r in rel if r not in tracked)

    print(f"仓库根: {root}")
    print(f"模式:   {args.pattern}")
    print(f"匹配:   {len(rel)} 个文件\n")

    if protected:
        print("!! 以下文件【被 git 跟踪】，已排除，不会删除：")
        for r in protected:
            tag = "  <- 正式文件（.gitignore 的 ! 例外）" if r in KNOWN_TRACKED else ""
            print(f"   [PROTECTED] {r}{tag}")
        print()

    print(f"将被删除: {len(deletable)} 个")
    for r in deletable:
        print(f"   [del] {r}")

    if protected:
        print("\n[安全] 检测到被跟踪的文件混在这批里。这不是致命错误，"
              "但请逐个确认它们确实不该删。")

    if not args.delete:
        print("\n(干跑模式，未删除任何文件。加 --delete 才真的删。)")
        return 2 if protected else 0

    removed = 0
    for r in deletable:
        rel[r].unlink()
        removed += 1
    print(f"\n已删除 {removed} 个文件；保留 {len(protected)} 个被跟踪文件。")

    # 删完复核：git status 里不应出现被跟踪文件的 D
    after = subprocess.run(["git", "-C", str(root), "status", "--porcelain"],
                           capture_output=True, text=True, encoding="utf-8", errors="replace")
    deleted_tracked = [l for l in after.stdout.splitlines() if l.startswith(" D")]
    if deleted_tracked:
        print("\n[FAIL] 删除后 git status 显示有被跟踪文件丢失 —— 立即恢复：")
        for l in deleted_tracked:
            print("   ", l)
        print("   修复: git checkout -- <path>")
        return 1

    print("[OK] git status 无被跟踪文件丢失。")
    return 0


if __name__ == "__main__":
    sys.exit(main())

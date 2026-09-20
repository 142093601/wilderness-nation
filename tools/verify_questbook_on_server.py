#!/usr/bin/env python3
"""verify_questbook_on_server.py -- 任务书的**服务端真机验证**（一条命令走完）。

为什么需要它（这三个坑我各踩过至少一次）
--------------------------------------
1. **服务端没退干净 → 日志文件被占用**：`server_ctl --start` 会尝试删/写
   `server/logs/latest.log`，上一条服务端还活着时它直接抛 PermissionError，
   看起来像"启动失败"，其实只是没停干净。
2. **数据不是最新的**：任务书产物在 `pack/config/ftbquests/`，
   而服务端读的是 `server/config/ftbquests/`。改完不重新落盘，验证的还是旧数据。
3. **只看"加载成功"不够**：还得确认没有 `Unknown registry key` 这类
   "章节加载了但判据引用无效"的问题（那种任务永远不亮）。

它做什么
--------
1. 停掉自己登记过的服务端（只停自己起的；没在跑就跳过）
2. 把 `pack/config/ftbquests/` 覆盖到 `server/config/ftbquests/`（并逐文件校验字节）
3. 起服务端等就绪
4. 断言三件事：
     a. 加载行存在，且章数/任务数与产物一致
     b. **没有**与任务书相关的 `Unknown registry key`（按命名空间过滤掉与任务书无关的噪声）
     c. 没有 FTB Quests 自己的 ERROR/WARN
5. 退出码 = 失败项数

用法
----
    python tools/verify_questbook_on_server.py
    python tools/verify_questbook_on_server.py --keep-running   # 验完不关，留给人工看
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
SRC = ROOT / "pack" / "config" / "ftbquests"
SRV = ROOT / "server" / "config" / "ftbquests"
SRV_LOG = ROOT / "server" / "logs" / "latest.log"
CONSOLE = ROOT / "data" / "server-console.log"

# 与任务书无关的 Unknown registry key 噪声（其它 mod 自己的 datagen 残留，实测存在）
NOISE_NS = ("dndecor", "railways", "everycomp", "framedblocks")


def sh(args: list[str], timeout: int = 600) -> subprocess.CompletedProcess:
    """跑子进程。**必须显式 encoding**：本机控制台是 GBK，
    而 server_ctl.py 会输出中文，按 GBK 解码会 UnicodeDecodeError → stdout 变 None
    （2026-09-21 实测踩到，与项目既有的"别从控制台读中文"是同一类问题）。"""
    return subprocess.run([sys.executable, *args], cwd=str(ROOT),
                          capture_output=True, text=True, timeout=timeout,
                          encoding="utf-8", errors="replace")


def tail_of(r: subprocess.CompletedProcess, n: int = 1) -> str:
    """取子进程输出的最后 n 行；stdout/stderr 可能是 None，要兜住。"""
    text = ((r.stdout or "") + (r.stderr or "")).strip()
    lines = [ln for ln in text.splitlines() if ln.strip()]
    return "\n  ".join(lines[-n:]) if lines else "(无输出)"


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass

    ap = argparse.ArgumentParser(description="任务书服务端真机验证")
    ap.add_argument("--keep-running", action="store_true", help="验完不关服务端")
    ap.add_argument("--wait", type=int, default=400)
    args = ap.parse_args()

    fails: list[str] = []

    def step(msg: str) -> None:
        print(f"\n=== {msg} ===")

    # ---- 0. 前置：产物存在，且数与 TOML 一致 ----
    step("0. 检查产物")
    chapters = sorted((SRC / "quests" / "chapters").glob("*.snbt"))
    print(f"  产物章节 {len(chapters)} 个")
    if not chapters:
        print("  产物为空，先跑 tools/questbook.py")
        return 1

    # ---- 1. 停掉自己登记的服务端 ----
    step("1. 停掉先前登记的服务端（只停自己起的）")
    r = sh(["tools/server_ctl.py", "--stop"])
    print("  " + tail_of(r))
    time.sleep(4)

    # ---- 2. 落盘（并逐文件校验）----
    step("2. 把产物覆盖到服务端 config/")
    if SRV.exists():
        import shutil
        shutil.rmtree(SRV)
    import shutil
    shutil.copytree(SRC, SRV)
    src_files = sorted(p for p in SRC.rglob("*") if p.is_file())
    bad = [str(p.relative_to(SRC)) for p in src_files
           if not (SRV / p.relative_to(SRC)).is_file()
           or (SRV / p.relative_to(SRC)).read_bytes() != p.read_bytes()]
    print(f"  落盘 {len(src_files)} 个文件，字节不一致 {len(bad)} 个")
    if bad:
        fails.append(f"落盘后字节不一致：{bad[:5]}")

    # ---- 3. 起服务端 ----
    step("3. 起服务端等就绪")
    r = sh(["tools/server_ctl.py", "--start", "--wait", str(args.wait)], timeout=args.wait + 120)
    print("  " + tail_of(r, 3))

    # ---- 4. 断言 ----
    step("4. 断言")
    text = SRV_LOG.read_text(encoding="utf-8", errors="replace") if SRV_LOG.is_file() else ""

    # a. 加载行
    m = re.search(r"Loaded (\d+) chapter groups, (\d+) chapters, (\d+) quests", text)
    if not m:
        fails.append("日志里没有 FTB Quests 的 Loaded 行")
        print("  [FAIL] 没有 Loaded 行")
    else:
        groups, ch, qs = (int(x) for x in m.groups())
        print(f"  加载：{groups} 组 / {ch} 章 / {qs} 任务")

        # 与 TOML 源对比（TOML 是权威）
        toml_q = sum(len(re.findall(r"^\[\[quest\]\]", p.read_text(encoding="utf-8"), re.M))
                     for p in (HERE / "questbook" / "chapters").glob("*.toml"))
        toml_ch = len(list((HERE / "questbook" / "chapters").glob("*.toml")))
        print(f"  TOML 源：{toml_ch} 章 / {toml_q} 任务")
        if ch != toml_ch:
            fails.append(f"章数不符：服务端 {ch} vs TOML {toml_ch}")
        if qs != toml_q:
            fails.append(f"任务数不符：服务端 {qs} vs TOML {toml_q}")

    # b. 与任务书相关的 Unknown registry key
    #
    # 必须**同时**满足两个条件才算与任务书相关（2026-09-21 实测，否则会被淹没）：
    #   1. 报错来源是 `ItemStack`（= 有人在解析一个物品引用。任务书的 item 判据就在这一步）
    #      而不是 `RecipeManager` / `LootModifierManager` / `LootDataType`
    #      —— 那些是**别的 mod 的 datagen 残留**（实测 700+ 条，例如
    #      farmingforblockheads 的市场配方引用了未安装的群系 mod）。
    #   2. id 的命名空间不在 NOISE_NS 里。
    bad_items = []
    for line in text.splitlines():
        if "Unknown registry key" not in line:
            continue
        if "ItemStack" not in line:
            continue                      # 配方/战利品表的噪声，与任务书无关
        mm = re.search(r"minecraft:item\]:\s*([a-z0-9_]+):", line)
        if not mm:
            continue
        ns = mm.group(1)
        if ns in NOISE_NS:
            continue
        bad_items.append(line.strip()[:200])
    if bad_items:
        fails.append(f"有 {len(bad_items)} 条与任务书相关的 Unknown registry key")
        print(f"  [FAIL] Unknown registry key x{len(bad_items)}：")
        for b in bad_items[:8]:
            print("     " + b)
    else:
        print("  [OK] 没有与任务书相关的 Unknown registry key")

    # c. FTB Quests 自身的 ERROR/WARN
    fq = [ln.strip()[:150] for ln in text.splitlines()
          if ("FTB Quests" in ln or "ftbquests" in ln.lower())
          and ("ERROR" in ln or "WARN" in ln)
          and "Found mod file" not in ln]
    if fq:
        fails.append(f"FTB Quests 自身有 {len(fq)} 条 ERROR/WARN")
        for x in fq[:5]:
            print("     " + x)
    else:
        print("  [OK] FTB Quests 自身零 ERROR / 零 WARN")

    # ---- 5. 收尾 ----
    if not args.keep_running:
        step("5. 关掉服务端（--keep-running 可跳过）")
        r = sh(["tools/server_ctl.py", "--stop"])
        print("  " + tail_of(r))

    print()
    if fails:
        print(f"VERIFY FAIL —— {len(fails)} 项：")
        for f in fails:
            print("  x " + f)
    else:
        print("VERIFY PASS —— 章数/任务数与 TOML 一致，判据引用无失效，FTB Quests 零告警")
    return len(fails)


if __name__ == "__main__":
    raise SystemExit(main())

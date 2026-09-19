#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""配对测试（进仓库（装机验证协议的核心工具））：验证"某组 mod 能不能进世界"，并且**保证世界与 mod 集匹配**。

为什么必须配对（本会话最贵的教训）：
  **世界引用了客户端没有的生物群系/维度时，开世界是「静默失败」** —— 没有异常、没有崩溃报告，
  日志停在启动完成那一行，看起来像挂死。所以测"某组 mod 能不能进世界"时，
  服务端 mods / 客户端 mods / 生成世界的 mod 集必须是同一集合（或世界那侧是客户端的子集）。

两种模式：
  ① 全配对（默认）：服务端与客户端都设为 --group，**世界也用 --group 生成**（最严格，约 8 分钟）
  ② 复用世界（--reuse-world <目录>）：**完全不碰服务端**，直接把已有世界拷给客户端测
     —— 前提是世界那侧的 mod 集是客户端的**子集**（安全方向），用于快速二分（约 2 分钟）

用法：
  python tools/paired_check.py --group b1,b2
  python tools/paired_check.py --group b1,b2,b3 --reuse-world data/base2-world
  python tools/paired_check.py --group b1,b2,b3 --slugs create-tfmg,framedblocks --reuse-world data/base2-world
"""
import argparse
import io
import os
import shutil
import subprocess
import sys
import time
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INST = Path(r"D:\game\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250")
CLIENT_MODS = INST / "mods"
SERVER_MODS = ROOT / "server" / "mods"
HOLD = ROOT / "data" / "held-back"
GAME_LOG = INST / "logs" / "latest.log"
SERVER_LOG = ROOT / "data" / "server-console.log"
MARKER = "Starting integrated minecraft server"
QUARANTINE_FILE = ROOT / "tools" / "lists" / "quarantine.tsv"

sys.path.insert(0, str(ROOT / "tools"))
try:
    import depgraph as DG   # 依赖闭包：keep = 选中的批次 ∪ 它们的必需前置库
except Exception as _e:      # noqa: BLE001
    DG = None

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except Exception:
    pass


def log(msg):
    line = "[%s] %s" % (time.strftime("%H:%M:%S"), msg)
    print(line, flush=True)
    with io.open(ROOT / "data" / "paired.log", "a", encoding="utf-8") as f:
        f.write(line + "\n")


def batches():
    out = {}
    for line in io.open(ROOT / "tools/lists/install-batches.tsv", encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        p = line.split("\t")
        if len(p) >= 2:
            out.setdefault(p[0].strip(), []).append(p[1].strip())
    return out


def fname(slug):
    """slug → jar 文件名；元数据已移除时用模糊匹配兜底（否则根本撤不出去）。"""
    f = ROOT / "pack" / "mods" / (slug + ".pw.toml")
    if f.is_file():
        return tomllib.loads(f.read_text(encoding="utf-8")).get("filename")
    key = "".join(ch for ch in slug.lower() if ch.isalnum())
    if not key:
        return None
    for d in (CLIENT_MODS, SERVER_MODS, HOLD):
        for jar in d.glob("*.jar"):
            jk = "".join(ch for ch in jar.name.lower() if ch.isalnum())
            if key in jk:
                return jar.name
    return None


def kill_java():
    for im in ("java.exe", "javaw.exe"):
        subprocess.run(["taskkill", "/F", "/T", "/IM", im], capture_output=True)
    time.sleep(6)


def settle(min_free_gb=4.0, timeout=180):
    """等服务端彻底退出、系统内存回收后再启客户端。

    为什么必须等：本会话实测——**客户端紧跟一次 6 分钟的服务端运行启动时，会在 mod 加载阶段静默死亡**
    （无崩溃报告、无 hs_err、无系统事件）。而同一套 mod、同样世界、只要前面没跑服务端就能进世界。
    也就是说那是**环境/资源压力**，不是 mod 冲突 —— 但会被误读成"某个 mod 有问题"。
    """
    t0 = time.time()
    while time.time() - t0 < timeout:
        alive = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "(Get-Process java,javaw -ErrorAction SilentlyContinue | Measure-Object).Count"],
            capture_output=True, text=True, timeout=60)
        mem = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "[math]::Round((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1MB,2)"],
            capture_output=True, text=True, timeout=60)
        try:
            n_java = int((alive.stdout or "0").strip() or 0)
            free = float((mem.stdout or "0").strip() or 0)
        except ValueError:
            n_java, free = 1, 0.0
        if n_java == 0 and free >= min_free_gb:
            log("  资源已就绪（java 进程 0，空闲内存 %.1f GB，等了 %.0f 秒）" % (free, time.time() - t0))
            return True
        time.sleep(5)
    log("  !! settle 超时（java=%s 空闲=%.1f GB），仍继续" % (n_java, free))
    return False


def quarantine_jars():
    """把隔离清单里的 jar 一律挪出去（判了 hold/skip 的 mod 不该参与任何测试）。"""
    q = HOLD / "quarantine"
    q.mkdir(parents=True, exist_ok=True)
    moved = []
    if not QUARANTINE_FILE.is_file():
        return moved
    names = []
    for line in io.open(QUARANTINE_FILE, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        p = line.split("\t")
        if len(p) >= 2:
            names.append(p[1].strip())
    for dest in (CLIENT_MODS, SERVER_MODS):
        for n in names:
            f = dest / n
            if f.is_file():
                shutil.move(str(f), str(q / n))
                moved.append(n)
    return moved


def set_side(dest: Path, keep: set, managed: list, other_hold: Path):
    """把 dest 里"被管理的 jar"调成：keep 需要的留下，其余挪走。

    **按文件名集合决策，而不是逐个 slug**：不同 slug 可能解析到同一个 jar 文件名
    （例如 `yungs-api` 与 `yungs-api-neoforge` 指向同一个 jar）→ 逐个 slug 处理时，
    后一个"不保留"的 slug 会把前一个刚放回来的 jar 又挪出去（顺序依赖 bug）。
    """
    other_hold.mkdir(parents=True, exist_ok=True)
    dest.mkdir(parents=True, exist_ok=True)
    keep_files = {fn for fn in (fname(s) for s in keep) if fn}
    managed_files = {fn for fn in (fname(s) for s in managed) if fn}
    n = 0
    for fn in sorted(managed_files):
        cur, hld = dest / fn, other_hold / fn
        if fn in keep_files:
            if not cur.is_file() and hld.is_file():
                shutil.move(str(hld), str(cur))
            if cur.is_file():
                n += 1
        elif cur.is_file():
            shutil.move(str(cur), str(hld))
    return n


def parse_groups(spec, bg, label):
    want = set()
    for g in [x.strip().lower() for x in spec.split(",") if x.strip()]:
        key = g.lstrip("b")
        if key not in bg:
            print("没有批次 %s（可用：%s）" % (g, ",".join(sorted(bg))))
            sys.exit(2)
        want |= set(bg[key])
    return want


def run_client_test(world_name, timeout, want_desc):
    """让客户端进世界，返回 (entered, crashed)。"""
    GAME_LOG.unlink(missing_ok=True)
    try:
        subprocess.run([sys.executable, "-u", "tools/autotest.py", "--seconds", "15",
                        "--startup-timeout", str(timeout), "--world", world_name],
                       cwd=str(ROOT), capture_output=True, text=True, encoding="utf-8",
                       errors="replace", env=dict(os.environ, PYTHONIOENCODING="utf-8"),
                       timeout=timeout + 150)
    except subprocess.TimeoutExpired:
        log("  (autotest 超时未返回)")
    txt = GAME_LOG.read_text(encoding="utf-8", errors="replace") if GAME_LOG.is_file() else ""
    entered = MARKER in txt
    crashed = "Encountered an unexpected exception" in txt
    log("  >>> %s%s ｜ %s" % ("PASS 进世界" if entered else "FAIL 未进世界",
                              "（进世界后崩）" if crashed else "", want_desc))
    return entered, crashed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--group", default="b1", help="客户端要装的批次，如 b1,b2,b3")
    ap.add_argument("--world-group", default="", help="生成世界用的批次（默认同 --group）")
    ap.add_argument("--slugs", default="", help="额外纳入的 slug（逗号分隔）")
    ap.add_argument("--exclude", default="", help="强制排除的 slug")
    ap.add_argument("--world", default="ptworld")
    ap.add_argument("--reuse-world", default="", help="复用已有世界目录（纯客户端测试，不碰服务端）")
    ap.add_argument("--world-only", action="store_true",
                    help="只生成并备份世界，不跑客户端（两步流程的第一步；C9 证明同进程内跑完服务端再跑客户端不可靠）")
    ap.add_argument("--backup-world", default="", help="--world-only 时把世界备份到这个目录")
    ap.add_argument("--all-keep", action="store_true",
                    help="保留 pack/mods 的全部条目（批次是顺序应用的，验证最新一批时不需要子集；"
                         "只有二分定位嫌疑 mod 时才用 --group/--exclude）")
    ap.add_argument("--startup-timeout", type=int, default=240)
    args = ap.parse_args()

    bg = batches()
    # 被管理的 jar = **pack/mods 里的全部条目**（含 packwiz 自动解析的依赖），而不是只有批次里的 slug。
    # 踩过的坑：批 3 的依赖（veil / sable）不在任何批次表里 → 测试器撤不出去 → 它们和已装的
    # embeddium / scalablelux 硬冲突 → 连"批 1+2"都启动即崩，看起来像"批 1+2 坏了"。
    managed = sorted(f.name[:-len(".pw.toml")] for f in (ROOT / "pack" / "mods").glob("*.pw.toml"))
    all_slugs = managed
    want = parse_groups(args.group, bg, "client")
    want |= {s.strip() for s in args.slugs.split(",") if s.strip()}
    want -= {s.strip() for s in args.exclude.split(",") if s.strip()}
    world_set = parse_groups(args.world_group or args.group, bg, "world")
    world_set |= {s.strip() for s in args.slugs.split(",") if s.strip()}
    world_set -= {s.strip() for s in args.exclude.split(",") if s.strip()}
    if args.all_keep:
        # 顺序装机下，"验证最新一批"= 保留 pack/mods 全部条目（前置库自然都在）
        want = set(managed)
        world_set = set(managed)
    elif DG is not None:
        # 子集模式：必须把依赖闭包并进来（前置库不在批次表里，少了它们选中的 mod 起不来）
        want = DG.closure(want)
        world_set = DG.closure(world_set)

    log("# 测试 client=%s(%d) world=%s(%d)%s"
        % (args.group, len(want), args.world_group or args.group, len(world_set),
           " 复用" if args.reuse_world else ""))

    kill_java()
    qm = quarantine_jars()
    if qm:
        log("  已隔离 %d 个：%s" % (len(qm), ", ".join(sorted(set(qm)))))

    if args.reuse_world:
        # 纯客户端测试：世界那侧的 mod 必须是客户端的子集
        extra = world_set - want
        if extra:
            log("  !! 世界包含客户端没有的 mod（会静默失败）：%s" % ", ".join(sorted(extra)))
            return 5
        n = set_side(CLIENT_MODS, want, all_slugs, HOLD / "cli")
        log("  客户端 %d 个" % n)
        src = Path(args.reuse_world)
        if not src.is_dir():
            log("  !! 世界目录不存在：%s" % src)
            return 4
        dst = INST / "saves" / args.world
        shutil.rmtree(dst, ignore_errors=True)
        shutil.copytree(src, dst)
    else:
        n_srv = set_side(SERVER_MODS, world_set, all_slugs, HOLD / "srv")
        n_cli = set_side(CLIENT_MODS, want, all_slugs, HOLD / "cli")
        log("  服务端 %d 个 / 客户端 %d 个" % (n_srv, n_cli))
        shutil.rmtree(ROOT / "server" / "world", ignore_errors=True)
        SERVER_LOG.unlink(missing_ok=True)
        log("  启动服务端生成世界…")
        subprocess.Popen([sys.executable, "-u", "tools/server_ctl.py", "--start"], cwd=str(ROOT),
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        ok_done = False
        deadline = time.time() + 600
        while time.time() < deadline:
            time.sleep(5)
            if SERVER_LOG.is_file():
                txt = SERVER_LOG.read_text(encoding="utf-8", errors="replace")
                if "Done (" in txt:
                    ok_done = True
                    break
                if "Failed to start" in txt or "has failed to load" in txt:
                    break
        try:
            subprocess.run([sys.executable, "-u", "tools/server_ctl.py", "--stop"], cwd=str(ROOT),
                           capture_output=True, timeout=120)
        except Exception as e:  # noqa: BLE001
            log("  停服异常（忽略并强杀）：%s" % e)
        kill_java()
        if not ok_done:
            log("  !! 服务端没到 Done（服务端侧冲突）→ 无法判定客户端")
            return 3
        log("  服务端已生成世界")
        try:
            dst = INST / "saves" / args.world
            shutil.rmtree(dst, ignore_errors=True)
            shutil.copytree(ROOT / "server" / "world", dst)
            log("  世界已拷给客户端")
        except Exception as e:  # noqa: BLE001
            log("  !! 拷世界失败：%s" % e)
            return 6

    if args.world_only:
        if args.backup_world:
            try:
                shutil.rmtree(args.backup_world, ignore_errors=True)
                shutil.copytree(ROOT / "server" / "world", args.backup_world)
                log("  世界已备份 → %s（两步流程第一步完成）" % args.backup_world)
            except Exception as e:  # noqa: BLE001
                log("  !! 备份世界失败：%s" % e)
                return 7
        return 0

    settle()
    entered, _ = run_client_test(args.world, args.startup_timeout, args.group)
    kill_java()
    return 0 if entered else 1


if __name__ == "__main__":
    sys.exit(main())

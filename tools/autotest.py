"""
自动测试器 —— 启动游戏 → 自动进世界 → 待够时间 → 自动退出 → 收日志与快照。

为什么要它：手工"启动、进世界、待一会、退出"这套动作，每加一组 mod 就要重复一遍，
既繁琐又容易漏。这个脚本把它变成一条命令，日志与快照自动归档。

做法：直接用 java 启动（不用 PCL 界面），用 `--quickPlaySingleplayer <世界名>` 自动进世界。
版本 JSON 是自包含的（inheritsFrom 为空），所以只需替换占位符、拼 classpath。

用法：
  python autotest.py --dry-run                 # 只打印将要执行的命令，不启动
  python autotest.py                           # 默认：进世界待 60 秒后退出
  python autotest.py --seconds 120 --world 新的世界

要点：
  * 用与手工游玩相同的 username/UUID（取自日志），这样统计会累积到同一个玩家档案，
    复盘采集器能连上；UUID 与存档里 stats 文件名一致。
  * 退出优先"优雅关闭"（taskkill 不带 /F 会发 WM_CLOSE），让游戏正常保存世界与统计；
    只有超时未退才强杀。之后清理可能残留的 session.lock。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from typing import Any

# ── 本机默认值：读 tools/pack.local.json（不进仓库；模板见 pack.local.example.json）──
# 以前这些值是**写死在代码里**的（实例路径 / java / 用户名 / UUID）。
# 公开仓库里那等于把机器信息一起公开，而且换台机器要改 6 个文件。
sys.path.insert(0, str(Path(__file__).resolve().parent))
import pack_paths  # noqa: E402  同目录模块
import pid_guard   # noqa: E402  自有 JVM 的登记/认领（只杀自己启动的，见 INCIDENTS.md）

_P = pack_paths.paths()
DEFAULTS = {
    "version_dir": _P["instance_dir"],
    "version_id": Path(_P["instance_dir"]).name,   # PCL 的实例目录名就是版本 id
    "game_root": _P["game_root"],
    "java": _P["java"],
    "username": _P["username"],
    "uuid": _P["uuid"],
    "world": "新的世界",
    "xmx": "10G",
    "width": "854",
    "height": "480",
}

IN_WORLD_MARKER = re.compile(r"Starting integrated minecraft server")

# 2026-09-19 假阳性事故之后加的硬闸门。
# 事故：`Starting integrated minecraft server` 这行在**开世界早期**就写进日志了，
# 之后客户端在 `Minecraft.doWorldLoad` 里崩掉，这行依然在 → 连续被判成 PASS。
# 结果：批 4、批 5 两次"验收通过"都是假的（客户端从没进过世界）。
# 教训：判据必须**同时**满足 ① 有进世界标记 ② 本次启动没有新崩溃报告、日志里也没有崩溃标记。
CRASH_LOG_PATTERNS = (
    "Crash report saved",
    "A fatal error has been detected",
    "Encountered an unexpected exception",
)

# 单机模式的**真正**"关卡已加载"证据。
#
# 2026-09-19 第二次踩假阳性：原来这里是
#     r"LoggerChunkProgressListener|Preparing spawn area|Time elapsed:"
# 其中前两个都是**开始**生成区域时写的（`LoggerChunkProgressListener` 第一行就是"2%"），
# 比真正完成早得多 —— 实测 `LoggerChunkProgressListener` 在 20:14:07、真正完成在 20:14:22，
# 于是 game_agent 提前 15 秒就以为进了世界，脚本命令全发在服务端起来之前（详见 game_agent.wait_for_input_ready）。
# 现在只认**完成**标记：`Time elapsed: N ms` 由 ServerLevel 载入流程最后一行写出。
# 注意它是纯日志字符串、**不跟随客户端语言**（本机 zh_cn 下实测仍是英文），
# 而 "准备生成区域中：x%" 那行是翻译过的 —— 这也是不能用它的原因之一。
LEVEL_LOADED_MARKER = re.compile(r"Time elapsed:")

# ── 自有 JVM 的 pid 登记（2026-09-20 事故之后加的）─────────────────────────────
# 事故：清场用的 `taskkill /F /IM javaw.exe` 是**按镜像名**杀的，会把玩家正在玩的游戏一起
# 杀掉；被强杀的 JVM 不写崩溃报告，玩家只看到"游戏崩溃但未生成崩溃报告"。
# 现在只登记自己的 pid，清理时也只按登记文件杀 —— 逻辑集中在 tools/pid_guard.py。
CLIENT_PID_FILE = pid_guard.CLIENT_PID_FILE


def write_client_pid(proc: subprocess.Popen) -> None:
    pid_guard.write(CLIENT_PID_FILE, proc.pid)


def clear_client_pid() -> None:
    pid_guard.clear(CLIENT_PID_FILE)



def snapshot_crash_reports(version_dir: Path) -> set:
    """崩溃报告文件名快照，用来判断"本次启动有没有新崩溃"。"""
    d = version_dir / "crash-reports"
    if not d.is_dir():
        return set()
    return {p.name for p in d.glob("*.txt")}



# ── 版本 JSON 解析 ────────────────────────────────────────────────────────────

# 启动器的"特性开关"：版本 JSON 的 rules 里可以用 features 引用它们。
#
# ⚠️ 踩过最贵的一个坑（记在这里，别再犯）：
# 原来的 rule_allows **只看 os、完全忽略 features**，于是
#     {'rules':[{'action':'allow','features':{'is_demo_user':True}}], 'value':'--demo'}
# 被当成了"无条件适用" —— 命令行被加上 --demo，游戏进的是**试玩模式**。
# 试玩模式禁止多人游戏、有 5 游戏日上限，等于此前所有自动测试都跑在错的配置上。
# 现在 features 参与判定：**声明的每个 feature 都必须与这里的开关一致**才匹配。
FEATURES: dict[str, bool] = {
    "is_demo_user": False,               # 永远不要试玩模式
    "has_custom_resolution": True,       # 我们用 --width/--height 控制窗口
    "has_quick_plays_support": False,    # 关掉：不发 --quickPlayPath（顺带消掉 QuickPlayLog 写日志报错）
    "is_quick_play_singleplayer": True,  # 默认直接进单机存档
    "is_quick_play_multiplayer": False,  # --server 时才打开
    "is_quick_play_realms": False,
}


def apply_features(cfg: dict[str, Any]) -> None:
    """按本次要跑的形态决定 features 开关（必须在 build_command 之前调用）。"""
    FEATURES["is_quick_play_singleplayer"] = bool(cfg.get("world"))
    FEATURES["is_quick_play_multiplayer"] = bool(cfg.get("server"))
    FEATURES["has_custom_resolution"] = bool(cfg.get("width") and cfg.get("height"))


def rule_allows(entry: dict[str, Any]) -> bool:
    """按 rules 判断该条参数/库是否适用于本机（windows/amd64 + 我们的 features 开关）。"""
    rules = entry.get("rules")
    if not rules:
        return True
    allowed = False
    for rule in rules:
        action = rule.get("action")
        os_rule = rule.get("os") or {}
        match = True
        if "name" in os_rule and os_rule["name"] != "windows":
            match = False
        if "arch" in os_rule and os_rule["arch"] not in ("x86_64", "amd64"):
            match = False
        if "version" in os_rule:
            match = False        # 本机没用到带 os.version 的条目
        for key, want in (rule.get("features") or {}).items():
            if bool(FEATURES.get(key, False)) != bool(want):
                match = False
        if match:
            allowed = action == "allow"
    return allowed


def flatten_args(items: list[Any]) -> list[str]:
    """arguments 里可能是字符串或 {rules, value}；按 rules 过滤并摊平。"""
    out: list[str] = []
    for item in items:
        if isinstance(item, str):
            out.append(item)
        elif isinstance(item, dict) and rule_allows(item):
            value = item.get("value")
            if isinstance(value, str):
                out.append(value)
            elif isinstance(value, list):
                out.extend(str(v) for v in value)
    return out


def build_classpath(libs_dir: Path, libraries: list[dict[str, Any]]) -> list[str]:
    """构建 classpath。

    踩过的坑：NeoForge 的 profile 里**同一个 jar 会被列两次**（例如 gson），
    而 BootstrapLauncher 用 stream().collect(toMap(...)) 构建 union 文件系统，
    遇到重复 key 直接抛 IllegalStateException: Duplicate key 然后退出。
    官方启动器是去重的，所以这里也按顺序去重（Windows 路径不区分大小写，按小写判重）。
    """
    paths: list[str] = []
    seen: set[str] = set()
    for lib in libraries:
        if lib.get("rules") and not rule_allows(lib):
            continue
        artifact = (lib.get("downloads") or {}).get("artifact") or {}
        rel = artifact.get("path")
        if not rel:
            # 没有 artifact 的多半是 natives，跳过（native 走 -Djava.library.path）
            continue
        full = libs_dir / rel
        if not full.is_file():
            continue
        key = str(full).lower()
        if key in seen:
            continue
        seen.add(key)
        paths.append(str(full))
    return paths


def substitute(text: str, mapping: dict[str, str]) -> str:
    return re.sub(r"\$\{([a-zA-Z_]+)\}", lambda m: mapping.get(m.group(1), m.group(0)), text)


def build_command(cfg: dict[str, str], extra_game_args: list[str]) -> tuple[list[str], list[str], list[str]]:
    """返回 (完整命令行, 未能替换的占位符列表, 游戏参数列表)。"""
    apply_features(cfg)
    version_dir = Path(cfg["version_dir"])
    game_root = Path(cfg["game_root"])
    json_path = version_dir / f"{cfg['version_id']}.json"
    doc = json.loads(json_path.read_text(encoding="utf-8"))

    natives = version_dir / f"{cfg['version_id']}-natives"
    libraries = build_classpath(game_root / "libraries", doc.get("libraries", []))
    classpath = ";".join(libraries)

    args_block = doc.get("arguments", {})
    jvm_items = flatten_args(args_block.get("jvm", []))
    game_items = flatten_args(args_block.get("game", []))

    mapping = {
        "natives_directory": str(natives),
        "library_directory": str(game_root / "libraries"),
        "classpath": classpath,
        "classpath_separator": ";",
        "launcher_name": "PCL",
        "launcher_version": "2.9.0",
        "version_name": cfg["version_id"],
        "game_directory": str(version_dir),
        "assets_root": str(game_root / "assets"),
        "assets_index_name": (doc.get("assetIndex") or {}).get("id", "1.21"),
        "auth_player_name": cfg["username"],
        "auth_uuid": cfg["uuid"],
        "auth_access_token": "0",
        "auth_session": "0",
        "clientid": "",
        "auth_xuid": "",
        "user_type": "legacy",
        "version_type": doc.get("type", "release"),
        "user_properties": "{}",
        # 版本 JSON 里本来就有这些参数（--quickPlaySingleplayer ${...} 等），
        # 所以要么填值、要么填空串，绝不能留占位符——否则会把字面量当世界名传进去。
        "quickPlaySingleplayer": cfg["world"],
        "quickPlayMultiplayer": cfg.get("server", ""),
        "quickPlayPath": "",
        "quickPlayRealms": "",
        "resolution_width": cfg["width"],
        "resolution_height": cfg["height"],
    }

    jvm = [substitute(a, mapping) for a in jvm_items]
    game = [substitute(a, mapping) for a in game_items]
    game += extra_game_args

    cmd = [cfg["java"], f"-Xmx{cfg['xmx']}"] + jvm + ["-cp", classpath, doc["mainClass"]] + game

    unresolved: list[str] = []
    for a in jvm + game:
        unresolved += re.findall(r"\$\{([a-zA-Z_]+)\}", a)
    return cmd, sorted(set(unresolved)), game


# ── 启动 / 等待 / 优雅退出 ────────────────────────────────────────────────────

def tail_has(path: Path, pattern: re.Pattern, start_offset: int = 0) -> int:
    """在文件里找 pattern，返回找到时的文件大小；没找到返回 0。"""
    try:
        with path.open("rb") as fh:
            fh.seek(start_offset)
            data = fh.read().decode("utf-8", errors="replace")
    except FileNotFoundError:
        return 0
    m = pattern.search(data)
    return len(data) + start_offset if m else 0


def wait_for_in_world(log: Path, proc: subprocess.Popen, deadline: float,
                      launched_at: float | None = None,
                      pattern: re.Pattern | None = None) -> bool:
    """等游戏真正进入世界。

    教训（踩过一次假阳性）：不能直接去 latest.log 里搜 "Starting integrated minecraft server"——
    上一次会话的日志里本来就有这一行，于是会立刻"确认成功"。
    必须**先确认日志是本次启动之后写的**（mtime 晚于启动时刻），再在其中有意义地搜索。

    pattern 可覆盖判据：**连服务器时**不会出现 "Starting integrated minecraft server"，
    要改等 "joined the game"（服务端广播的加入消息会被客户端聊天栏记进日志）。
    """
    custom = pattern is not None
    probe = pattern or IN_WORLD_MARKER
    if launched_at is None:
        launched_at = time.time()
    version_dir = log.parent.parent
    crash_before = snapshot_crash_reports(version_dir)
    while time.time() < deadline:
        if proc.poll() is not None:
            print(f"  !! 游戏进程已退出（exit code {proc.returncode}）")
            return False
        try:
            fresh = log.is_file() and log.stat().st_mtime > launched_at
        except OSError:
            fresh = False
        if fresh:
            try:
                text = log.read_text(encoding="utf-8", errors="replace")
            except OSError:
                text = ""
            new_crashes = snapshot_crash_reports(version_dir) - crash_before
            if new_crashes or any(p in text for p in CRASH_LOG_PATTERNS):
                detail = ", ".join(sorted(new_crashes)) if new_crashes else "日志里有崩溃标记"
                print(f"  !! 客户端崩溃（{detail}）→ 判定未进世界")
                return False
            if probe.search(text) and (custom or LEVEL_LOADED_MARKER.search(text)):
                return True
        time.sleep(2)
    return False


def graceful_quit(pid: int, log: Path, wait_seconds: int = 30) -> str:
    """先发 WM_CLOSE（让游戏自己保存并退出），超时才强杀。"""
    try:
        subprocess.run(["taskkill", "/PID", str(pid)], capture_output=True, timeout=20)
    except Exception as exc:
        print(f"  taskkill 失败: {exc}")
    deadline = time.time() + wait_seconds
    while time.time() < deadline:
        if tail_has(log, re.compile(r"Stopping worker threads|All dimensions are saved")):
            pass
        if not process_alive(pid):
            return "graceful"
        time.sleep(1)
    try:
        subprocess.run(["taskkill", "/F", "/T", "/PID", str(pid)], capture_output=True, timeout=20)
    except Exception:
        pass
    for _ in range(15):
        if not process_alive(pid):
            return "forced"
        time.sleep(1)
    return "still-running"


def process_alive(pid: int) -> bool:
    out = subprocess.run(["tasklist", "/FI", f"PID eq {pid}", "/NH"],
                         capture_output=True, text=True)
    return str(pid) in (out.stdout or "")


# ── options.txt：自动化必须的两个开关 ─────────────────────────────────────────
#
# 两项都是"人玩时无所谓、机器人玩时致命"的设置：
#   pauseOnLostFocus:true  → 窗口一失去焦点游戏立刻暂停（单人模式默认），后台跑必炸
#   rawMouseInput:true     → 合成鼠标移动被忽略，将来点 GUI 全部无效
# 改之前整份备份，游戏退出后还原 —— 绝不留下用户原始设置被改过的痕迹。
AUTOMATION_OPTIONS = {
    "pauseOnLostFocus": "false",
    "rawMouseInput": "false",
}


def apply_automation_options(version_dir: Path) -> Path | None:
    path = version_dir / "options.txt"
    if not path.is_file():
        print("  options.txt 不存在，跳过")
        return None
    backup = version_dir / "options.txt.autotest-backup"
    if not backup.is_file():
        backup.write_bytes(path.read_bytes())
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    out: list[str] = []
    seen: set[str] = set()
    for line in lines:
        key = line.split(":", 1)[0] if ":" in line else ""
        if key in AUTOMATION_OPTIONS:
            seen.add(key)
            old = line.split(":", 1)[1] if ":" in line else ""
            if old != AUTOMATION_OPTIONS[key]:
                print(f"    {key}: {old} -> {AUTOMATION_OPTIONS[key]}")
            out.append(f"{key}:{AUTOMATION_OPTIONS[key]}")
        else:
            out.append(line)
    for key, val in AUTOMATION_OPTIONS.items():
        if key not in seen:
            print(f"    {key}: (缺失) -> {val}")
            out.append(f"{key}:{val}")
    path.write_text("\n".join(out) + "\n", encoding="utf-8")
    return backup


def restore_options(backup: Path | None, version_dir: Path) -> None:
    if backup is None or not backup.is_file():
        return
    try:
        (version_dir / "options.txt").write_bytes(backup.read_bytes())
        backup.unlink()
        print("  options.txt 已还原")
    except Exception as exc:
        print(f"  options.txt 还原失败: {exc}")


def main() -> int:
    # Windows 控制台是 GBK，直接 print 日志片段里的替换字符会 UnicodeEncodeError 崩掉。
    # 统一把 stdout 切成 utf-8 + replace，保住"不崩"这条底线。
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")   # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="自动启动游戏 → 进世界 → 计时 → 退出 → 收日志")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--seconds", type=int, default=60, help="进世界后停留秒数")
    ap.add_argument("--startup-timeout", type=int, default=300, help="等待进世界的上限（秒）")
    ap.add_argument("--world", default=DEFAULTS["world"])
    ap.add_argument("--jvm-args", default="",
                    help="额外 JVM 参数（空格分隔），用来模拟玩家的启动器。"
                         "例：--jvm-args \"-Dfile.encoding=COMPAT\" 复现 PCL 的编码环境")
    ap.add_argument("--server", default="", help="多人：host:port（留空=单机进存档）")
    ap.add_argument("--no-options", action="store_true",
                    help="不要动 options.txt（pauseOnLostFocus 保持原样，后台跑会被暂停打断）")
    ap.add_argument("--data", default=str(Path(__file__).resolve().parent.parent / "data"))
    for key in ("version_dir", "version_id", "game_root", "java", "username", "uuid", "xmx",
                "width", "height"):
        ap.add_argument(f"--{key.replace('_', '-')}", default=DEFAULTS[key])
    args = ap.parse_args()

    cfg = dict(DEFAULTS)
    # `world` 曾经**不在**这个列表里 → `cfg["world"]` 永远是 DEFAULTS 的「新的世界」，
    # 于是 `--world X` 从来就没生效过：它照旧启动玩家在玩的真实世界，而表头还打印你传的 X。
    # 2026-09-19 抓到（第二次落盘验证时，游戏参数里赫然是「新的世界」）。教训：表头必须打印
    # **真正用来启动的那个值**，否则它只会替 bug 打掩护。
    for key in ("version_dir", "version_id", "game_root", "java", "username", "uuid", "xmx",
                "width", "height", "world"):
        cfg[key] = getattr(args, key)
    cfg["server"] = args.server
    version_dir = Path(cfg["version_dir"])
    log = version_dir / "logs" / "latest.log"
    crash_dir = version_dir / "crash-reports"

    extra: list[str] = []          # quickPlay 参数已在版本 JSON 里，靠替换占位符生效
    try:
        cmd, unresolved, game_args = build_command(cfg, extra)
    except Exception as exc:
        print(f"构造启动命令失败: {type(exc).__name__}: {exc}")
        return 2

    # 模拟玩家的启动器参数（2026-09-19 C14 事故之后加的）。
    # PCL 会给 JVM 加 `-Dfile.encoding=COMPAT`（中文 Windows 上 = GBK），
    # 而我们的自动化一直不带它 → 用 UTF-8 → **从没暴露过 BOM 引起的启动崩溃**。
    # 所以"玩家视角"的验收必须能复现那套参数：JVM 以**最后一个同名 -D 为准**，
    # 这里插在 -cp 之前（即版本 JSON 的 jvm 参数之后），与"启动器把用户参数放后面"一致。
    extra_jvm = [a for a in (args.jvm_args or "").split() if a]
    if extra_jvm:
        try:
            at_cp = cmd.index("-cp")
        except ValueError:
            at_cp = len(cmd)
        cmd[at_cp:at_cp] = extra_jvm
        print(f"extra JVM args: {' '.join(extra_jvm)}")

    print(f"version : {cfg['version_id']}")
    print(f"java    : {cfg['java']}")
    print(f"world   : {cfg['world']}" + (f"  server: {args.server}" if args.server else ""))
    print(f"window  : {cfg['width']}x{cfg['height']}")
    print(f"classpath entries: {cmd[cmd.index('-cp') + 1].count(';') + 1}")

    # 这些检查必须在 dry-run **之前**，否则 dry-run 报"没问题"而实际参数是错的。
    print("features: " + ", ".join(f"{k}={v}" for k, v in FEATURES.items()))
    if "--demo" in cmd:
        print("!! 命令行里出现了 --demo（试玩模式）—— features 判定有 bug，拒绝启动")
        return 3
    print("game args: " + " ".join(game_args))

    if unresolved:
        print(f"未替换的占位符: {unresolved}")
    if args.dry_run:
        return 0

    # 记下启动前日志大小，方便定位本次会话的片段
    before = log.stat().st_size if log.is_file() else 0
    launch_log = Path(args.data) / "autotest-launch.log"
    launch_log.parent.mkdir(parents=True, exist_ok=True)

    # 防御性断言已在上面（dry-run 之前）做过，这里开始真正启动
    print("\n应用自动化设置：")
    backup = None if args.no_options else apply_automation_options(version_dir)

    print("启动中（窗口会弹出）...")
    print(f"  java 输出重定向到: {launch_log}")
    launched_at = time.time()
    try:
        with launch_log.open("wb") as out:
            proc = subprocess.Popen(cmd, cwd=str(version_dir), stdout=out, stderr=subprocess.STDOUT)
        print(f"  pid = {proc.pid}")
        write_client_pid(proc)

        deadline = time.time() + args.startup_timeout
        if not wait_for_in_world(log, proc, deadline, launched_at):
            print("未能确认进入世界。java 输出尾部：")
            try:
                tail = launch_log.read_text(encoding="utf-8", errors="replace").splitlines()[-25:]
                for l in tail:
                    print("  | " + l[:200])
            except Exception:
                pass
            if proc.poll() is None:
                graceful_quit(proc.pid, log)
            return 1
        print(f"  已进入世界，停留 {args.seconds} 秒 ...")
        time.sleep(args.seconds)

        how = graceful_quit(proc.pid, log)
        print(f"  退出方式: {how}")
    finally:
        restore_options(backup, version_dir)
        clear_client_pid()

    # 清理可能残留的锁：**只清本次世界那个**。
    # 以前是 `saves/*/session.lock` 全清 —— 顺带会把玩家自己存档的锁删掉，
    # 万一玩家正开着那个存档，等于给了两写同一存档的机会。
    try:
        lock = version_dir / "saves" / args.world / "session.lock"
        if lock.is_file():
            lock.unlink()
            print(f"  已清理残留锁: {args.world}/session.lock")
    except Exception:
        pass

    # 收集：复用采集器
    world_dir = version_dir / "saves" / args.world
    tool = Path(__file__).resolve().parent / "collect_snapshot.py"
    print("\n--- 采集快照 ---")
    subprocess.run([sys.executable, str(tool), "--world", str(world_dir),
                    "--log", str(log), "--out", args.data])

    # 摘要：只报本次会话的关键行
    # 注意：Minecraft 每次启动都会**轮转日志**（旧的转成 .gz、新建 latest.log），
    # 所以不能"从启动前的偏移量往后读"（那会读到新文件末尾之外，得到 0 条）。
    # latest.log 本身就是本次会话的完整日志，整份读即可。
    print("\n--- 本次会话摘要 ---")
    try:
        text = log.read_text(encoding="utf-8", errors="replace")
    except Exception:
        text = ""
    errs = [l for l in text.splitlines() if "/ERROR]" in l]
    warns = [l for l in text.splitlines() if "/WARN]" in l and "Reference map" not in l]
    print(f"ERROR {len(errs)} 条 / 非 refmap WARN {len(warns)} 条")
    for l in errs[:5]:
        print("  E " + l[-160:])
    for l in [x for x in text.splitlines() if "ModernFix/" in x and ("Game took" in x or "Total time" in x or "main menu to in-game" in x)]:
        print("  M " + l.split("]:", 1)[-1].strip())
    # 只认「本次运行之后写入」的崩溃报告。
    # 旧写法是"最近 600 秒内有崩溃报告就报警"，会把**上一轮**留下的报告算到这一轮头上
    # （2026-09-19 实测：实验 4 明明没崩，却被上一轮 16:01:39 的报告误报）。
    crashes = ([p for p in crash_dir.glob("*.txt") if p.stat().st_mtime > launched_at]
               if crash_dir.is_dir() else [])
    if crashes:
        newest = max(crashes, key=lambda p: p.stat().st_mtime)
        print(f"  !! 本次运行产生了崩溃报告: {newest.name}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""game_agent.py -- 用「真实客户端」执行游戏内命令，并把输出当**文本**读回来。

为什么不是截图 + 看图
----------------------
Minecraft 客户端会把聊天栏的每一行都写进 logs/latest.log：

    [22:39:03] [Render thread/INFO] [minecraft/ChatComponent]: [System] [CHAT] Claims: 1 / 500

所以命令输出根本不需要"看"：它是**纯文本**，完整、不截断、可 grep、可断言。
（这条是实测发现的 —— 之前靠截图读，被窗口 DPI、字体、可视行数反复坑。）

        输入：PostMessage 发键盘消息（窗口被遮挡/非前台也能用）
        输出：latest.log 的文本增量
        截图：只留给真正要看画面的检查（GUI / 中文化 / 建筑外观 / 图纸预览）

管线
----
启动（复用 autotest 的版本 JSON 解析与优雅退出）
  → 等进世界 → 发 Esc 清场 → T 开聊天 → 打字 → 回车
  → 读日志增量 → 交给断言

自愈：如果一条命令没有任何输出，说明有界面挡着（暂停菜单/聊天栏），
按 Esc 再重试，最多 3 次。这样不需要精确知道当前界面状态。

用法
----
    # 跑一个测试脚本（每步 cmd + expect）
    python game_agent.py --script tests/opac_v0.txt --launch

    # 单条命令，直接看输出
    python game_agent.py --run "/oclaims about" --launch

    # 附着在已经开着的游戏上（不启动/不退出）
    python game_agent.py --run "/spark tps" --attach

脚本格式（tests/*.txt）
----------------------
    # 井号开头是注释
    cmd: /oclaims claim
    expect: Successfully claimed|Already claimed
    expect: 圈地|claimed

    shot: out/claim.png          # 可选：这一步顺便截图
    cmd: /oclaims about
    expect: Claims: \\d+ / \\d+
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import autotest as at  # noqa: E402  复用启动/等待/退出逻辑

HERE = Path(__file__).resolve().parent
WIN_CTL = HERE / "win_ctl.ps1"
POWERSHELL = "powershell.exe"

# 客户端把聊天写进日志；服务端命令反馈另有一行 [player: ...]
CHAT_RE = re.compile(r"\[CHAT\]\s?(.*)$")
SERVER_FEEDBACK_RE = re.compile(r"\[(?:Server thread)/INFO\] \[net\.minecraft\.server\.MinecraftServer/\]: \[([^\]]+)\]")


# ── 低层：窗口输入 ────────────────────────────────────────────────────────────

def ps(action: str, value: str | None = None, vk: int | None = None, out: str | None = None) -> tuple[int, str]:
    cmd = [POWERSHELL, "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(WIN_CTL), "-Action", action]
    if value is not None:
        cmd += ["-Value", value]
    if vk is not None:
        cmd += ["-Vk", str(vk)]
    # 踩过一次：截图动作要的是 -Out，而这里一律塞成了 -Value →
    # 每张截图都返回 "ERR -Out required"，而断言全 PASS，很容易漏过去。
    if out is not None:
        cmd += ["-Out", out]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=90)
    return r.returncode, (r.stdout or "") + (r.stderr or "")


def window_present() -> bool:
    """--Action list 列出所有带标题的窗口，找得到 Minecraft 就说明游戏开着。"""
    code, out = ps("list")
    return "Minecraft" in out


def send_tap(vk: int) -> None:
    ps("tap", vk=vk)


def send_command(text: str) -> None:
    """开聊天 → 打字 → 回车。三步分开可避免第一个字符被吃掉/重复。"""
    ps("tap", vk=0x54)          # T
    time.sleep(0.45)
    ps("chars", value=text)
    time.sleep(0.25)
    ps("tap", vk=0x0D)          # Enter
    time.sleep(0.20)


def screenshot(path: Path) -> str:
    path.parent.mkdir(parents=True, exist_ok=True)
    code, out = ps("shot", out=str(path))
    if "ERR" in out or code != 0:
        return f"截图失败: {out.strip().splitlines()[-1] if out.strip() else code}"
    return out.strip().splitlines()[-1] if out.strip() else ""


# ── 日志读取 ──────────────────────────────────────────────────────────────────

def log_path(cfg: dict) -> Path:
    return Path(cfg["version_dir"]) / "logs" / "latest.log"


def log_size(p: Path) -> int:
    try:
        return p.stat().st_size
    except OSError:
        return 0


def read_delta(p: Path, offset: int) -> tuple[str, int]:
    """读 offset 之后的新增内容。会话内 latest.log 只追加、不轮转，所以偏移量安全。"""
    try:
        with p.open("rb") as fh:
            fh.seek(offset)
            data = fh.read()
    except OSError:
        return "", offset
    return data.decode("utf-8", errors="replace"), offset + len(data)


def clean_lines(raw: str) -> list[str]:
    """把日志行提炼成"人看到的"文本。

    同一条反馈会出现两次：客户端 ChatComponent 记一次、集成服务端再记一次。
    这里按顺序去重，避免断言和输出里到处是重复行。
    """
    out: list[str] = []
    for line in raw.splitlines():
        m = CHAT_RE.search(line)
        if m:
            out.append(m.group(1).strip())
            continue
        m2 = SERVER_FEEDBACK_RE.search(line)
        if m2:
            body = m2.group(1)
            # 服务端反馈形如 "<玩家名>: 文本"
            out.append(body.split(": ", 1)[1] if ": " in body else body)
            continue
        if "/ERROR]" in line or "/WARN]" in line:
            out.append(line)
    deduped: list[str] = []
    for l in out:
        if not l or (deduped and deduped[-1] == l):
            continue
        deduped.append(l)
    return deduped


# ── 输入就绪探针 ──────────────────────────────────────────────────────────────

def wait_for_input_ready(cfg: dict, timeout: float = 420.0, gap: float = 4.0) -> bool:
    """等"我发出去的按键真的被游戏收到了"。

    为什么不能只等日志里的"进世界"标记：`LoggerChunkProgressListener` / `Preparing spawn area`
    都是生成区域的**开始**（第一行就是 2%），实测比真正能交互早得多 ——
    2026-09-19 的 statecraft 脚本 3 条命令全发在服务端起来之前，结果是 0/3，
    而它看起来像"mod 功能坏了"。判据必须是**闭环**的：发一条探针，等它自己在日志里出现。

    探针用**普通聊天**而不是命令：聊天回显由服务端写进日志，不依赖任何 mod 注册命令，
    也不受客户端语言影响（原版命令的回显跟随 zh_cn 会变成中文，英文断言会全部失效）。
    """
    log = log_path(cfg)
    deadline = time.time() + timeout
    attempt = 0
    while time.time() < deadline:
        attempt += 1
        token = f"sc-probe-{attempt}"
        offset = log_size(log)
        send_command(token)
        time.sleep(1.5)
        raw, _ = read_delta(log, offset)
        if token in raw:
            print(f"  按键通道已就绪（第 {attempt} 次探针在日志里出现）")
            return True
        send_tap(0x1B)
        time.sleep(gap)
    return False


# ── 命令执行（含自愈） ────────────────────────────────────────────────────────

def run_command(cfg: dict, command: str, settle: float = 1.6, attempts: int = 3) -> dict:
    log = log_path(cfg)
    for attempt in range(1, attempts + 1):
        offset = log_size(log)
        send_command(command)
        time.sleep(settle)
        raw, _ = read_delta(log, offset)
        lines = clean_lines(raw)
        # 滤掉回显噪声：命令自身的回显行
        lines = [l for l in lines if l.strip() != command.strip()]
        if lines:
            return {"command": command, "attempt": attempt, "lines": lines, "ok": True}
        # 没输出 → 大概有界面挡着，Esc 清场重试
        send_tap(0x1B)
        time.sleep(0.7)
    return {"command": command, "attempt": attempts, "lines": [], "ok": False}


# ── 脚本执行 ──────────────────────────────────────────────────────────────────

def parse_script(path: Path) -> list[dict]:
    """脚本支持两种步骤：

      cmd:  ...   由**客户端**执行（真实玩家能做的事：圈地、开 GUI、放方块）
      rcon: ...   由**服务端**执行（断言首选）

    为什么要分：原版命令的回显**跟随客户端语言**（本地是 zh_cn），
    英文断言会全部不匹配（"Changed the block" vs "已更改位于…的方块"）；
    而 RCON / 服务端控制台的输出**永远是英文**，断言稳定。
    所以凡是"能由服务端问清的事实"，都用 rcon 步骤。
    """
    steps: list[dict] = []
    cur: dict | None = None
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, _, value = line.partition(":")
        key, value = key.strip().lower(), value.strip()
        if key in ("cmd", "rcon"):
            cur = {"kind": key, key: value, "expect": [], "shots": [], "line": lineno}
            steps.append(cur)
        elif key == "expect" and cur is not None:
            cur["expect"].append(value)
        elif key == "shot" and cur is not None:
            cur["shots"].append(value)
        else:
            print(f"  ! 脚本第 {lineno} 行无法识别: {line}")
    return steps


def expand(text: str, cfg: dict) -> str:
    """把脚本里的 `{{player}}` 换成**本机配置里的游戏 ID**。

    这样测试脚本可以进公开仓库，而不用把某个人的游戏 ID 写死在仓库里。
    """
    return text.replace("{{player}}", str(cfg.get("username", "")))


def run_script(cfg: dict, steps: list[dict], out_dir: Path) -> int:
    sys.path.insert(0, str(HERE))
    import server_ctl  # 延迟导入：只有脚本里真用了 rcon 才需要它

    rcon = None
    failures = 0
    try:
        for i, step in enumerate(steps, 1):
            kind = step.get("kind", "cmd")
            if kind == "rcon":
                if rcon is None:
                    rcon = server_ctl.Rcon()
                text = rcon.cmd(expand(step["rcon"], cfg))
                ok = True
            else:
                res = run_command(cfg, expand(step["cmd"], cfg))
                text = "\n".join(res["lines"])
                ok = res["ok"]
            label = expand(step.get("rcon") if kind == "rcon" else step["cmd"], cfg)
            if not ok:
                print(f"[{i}] FAIL(无输出) {label}")
                failures += 1
                continue
            bad = [p for p in step["expect"] if not re.search(p, text, re.MULTILINE)]
            if bad:
                failures += 1
            print(f"[{i}] {'PASS' if not bad else 'FAIL'} [{kind}] {label}")
            for line in text.splitlines()[:12]:
                print(f"      | {line}")
            for p in bad:
                print(f"      x 未匹配: {p}")
            for shot in step["shots"]:
                note = screenshot(out_dir / shot)
                print(f"      shot -> {shot}  {note}")
    finally:
        if rcon is not None:
            rcon.close()
    print(f"\n结果: {len(steps) - failures}/{len(steps)} 通过")
    return failures


# ── main ──────────────────────────────────────────────────────────────────────

def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="用真客户端跑命令并读文本输出")
    ap.add_argument("--script", help="测试脚本（cmd/expect/shot）")
    ap.add_argument("--run", help="单条命令")
    ap.add_argument("--attach", action="store_true", help="附着在已开着的游戏上（不启动不退出）")
    ap.add_argument("--launch", action="store_true", help="自己启动游戏（默认：脚本模式自动启动）")
    ap.add_argument("--hold", type=int, default=0, help="跑完后继续开着的秒数（0=跑完就退）")
    ap.add_argument("--out", default=str(HERE.parent / "data" / "shots"))
    ap.add_argument("--world", default=at.DEFAULTS["world"])
    ap.add_argument("--server", default="", help="连服务器：host:port（给了就连服务器，不再进单机存档）")
    for key in ("version_dir", "version_id", "game_root", "java", "username", "uuid", "xmx",
                "width", "height"):
        ap.add_argument(f"--{key.replace('_', '-')}", default=at.DEFAULTS[key])
    args = ap.parse_args()

    if not args.script and not args.run:
        ap.error("需要 --script 或 --run")

    cfg = dict(at.DEFAULTS)
    for key in ("version_dir", "version_id", "game_root", "java", "username", "uuid", "xmx",
                "width", "height"):
        cfg[key] = getattr(args, key)
    # 连服务器时不能同时给单机存档名，否则 features 会两个都打开
    cfg["server"] = args.server
    cfg["world"] = "" if args.server else args.world

    steps = parse_script(Path(args.script)) if args.script else [
        {"cmd": args.run, "expect": [], "shots": [], "line": 0}
    ]

    proc = None
    backup = None
    should_launch = args.launch or (not args.attach and args.script)
    try:
        if should_launch:
            cmd, unresolved, _ = at.build_command(cfg, [])
            if "--demo" in cmd:
                print("!! 命令行出现 --demo，拒绝启动")
                return 3
            version_dir = Path(cfg["version_dir"])
            backup = at.apply_automation_options(version_dir)
            launch_log = Path(HERE.parent / "data" / "game-agent-launch.log")
            launch_log.parent.mkdir(parents=True, exist_ok=True)
            print("启动游戏 ...")
            launched_at = time.time()
            with launch_log.open("wb") as out:
                proc = subprocess.Popen(cmd, cwd=str(version_dir), stdout=out, stderr=subprocess.STDOUT)
            # 连服务器时不会出现单机那行判据，要改等"加入游戏"的广播
            probe = re.compile(r"joined the game|Connecting to") if cfg["server"] else None
            if not at.wait_for_in_world(log_path(cfg), proc, time.time() + 420, launched_at, probe):
                print("未能进入世界")
                return 1
            print("  已进入世界")
            # 日志说"进了世界"不等于客户端已经能收按键（这个包从启动到服务端真正起来要 ~4 分钟）。
            # 不做这一步，脚本会对着还在加载的游戏打字，然后报一堆像"功能坏了"的假失败。
            if not wait_for_input_ready(cfg):
                print("!! 世界里一直收不到按键（客户端始终没就绪）→ 不跑脚本，免得产出假失败")
                return 1
            time.sleep(3)

        if not window_present():
            print("找不到 Minecraft 窗口")
            return 2

        send_tap(0x1B)          # 先清一次场
        time.sleep(0.8)

        failures = run_script(cfg, steps, Path(args.out))

        if args.hold:
            print(f"保持开着 {args.hold} 秒 ...")
            time.sleep(args.hold)
        return failures
    finally:
        if proc is not None and not args.attach:
            how = at.graceful_quit(proc.pid, log_path(cfg))
            print(f"退出方式: {how}")
            for lock in (Path(cfg["version_dir"]) / "saves").glob("*/session.lock"):
                try:
                    lock.unlink()
                except Exception:
                    pass
            at.restore_options(backup, Path(cfg["version_dir"]))


if __name__ == "__main__":
    raise SystemExit(main())

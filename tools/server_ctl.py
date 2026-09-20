"""server_ctl.py -- 把整合包当**专用服务端**跑起来，并用 RCON 执行命令、读文本输出。

为什么要有这一层
----------------
1. v0 至今只验过客户端。7 个人要连的是服务端，而服务端与客户端的 mod 集合**不一样**
   （纯客户端 mod 必须排除，服务端专用 mod 必须加入），第一次起服务端才是真检验。
2. RCON 让"命令 + 文本输出"完全脱离窗口：不需要截图、不需要发键盘、不需要界面状态，
   比单机那条路更硬。
3. 服务端是 7 人负载的唯一真实目标（并发压测按用户要求押后，但服务端本身要先能起）。

RCON 协议直接手写（不引入依赖）：包体 = 长度(4) + 请求id(4) + 类型(4) + 正文 + \0\0。

用法
----
    python server_ctl.py --setup                 # 生成 eula/server.properties/复制 mods
    python server_ctl.py --start                 # 启动并等到 "Done (...)"
    python server_ctl.py --cmd "list"            # RCON 执行一条命令
    python server_ctl.py --script tests/server_v0.txt
    python server_ctl.py --stop
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import socket
import struct
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pack_paths  # noqa: E402  同目录模块：读取本机配置
import pid_guard   # noqa: E402  自有 JVM 的登记/认领（只杀自己启动的，见 INCIDENTS.md）

_P = pack_paths.paths()
HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
DEFAULT_SERVER = Path(_P["server_dir"])
INSTANCE_MODS = Path(_P["instance_dir"]) / "mods"
JAVA = _P["java"]
NEOFORGE_VER = "21.1.250"
RCON_HOST, RCON_PORT, RCON_PASS = _P["rcon_host"], int(_P["rcon_port"]), _P["rcon_password"]

# 设计稿 §12 的性能目标；服务端用目标的**上限**，这样才能压出问题
SERVER_PROPS = {
    "enable-rcon": "true",
    "rcon.port": str(RCON_PORT),
    "rcon.password": RCON_PASS,
    "broadcast-rcon-to-ops": "true",
    "online-mode": "false",     # 测试客户端是离线鉴权（没有正版会话）；正式服应改回 true
    "max-players": "10",
    "view-distance": "8",       # 设计目标 6~8
    "simulation-distance": "6",  # 设计目标 4~6
    "spawn-protection": "0",    # 免得挡住建城测试，靠 OPAC 领地来保护
    "level-name": "world",
    "motd": "nation-pack v0",
    "gamemode": "survival",
    "difficulty": "normal",
    "allow-nether": "true",
    "enable-command-block": "true",
    "sync-chunk-writes": "false",
}


# ── 配置与文件 ────────────────────────────────────────────────────────────────

def write_props(server_dir: Path) -> list[str]:
    path = server_dir / "server.properties"
    lines = path.read_text(encoding="utf-8").splitlines() if path.is_file() else []
    changed: list[str] = []
    seen: set[str] = set()
    out: list[str] = []
    for line in lines:
        if "=" not in line or line.strip().startswith("#"):
            out.append(line)
            continue
        key, _, val = line.partition("=")
        if key in SERVER_PROPS:
            seen.add(key)
            want = SERVER_PROPS[key]
            if val != want:
                changed.append(f"{key}: {val} -> {want}")
            out.append(f"{key}={want}")
        else:
            out.append(line)
    for key, want in SERVER_PROPS.items():
        if key not in seen:
            out.append(f"{key}={want}")
            changed.append(f"{key}: (缺失) -> {want}")
    path.write_text("\n".join(out) + "\n", encoding="utf-8")
    return changed


def setup(server_dir: Path) -> int:
    server_dir.mkdir(parents=True, exist_ok=True)
    (server_dir / "eula.txt").write_text(
        "# 由 server_ctl.py 生成：继续使用即表示同意 https://aka.ms/MinecraftEULA\n"
        "eula=true\n", encoding="utf-8")
    print("eula.txt 已写入")

    if not (server_dir / "run.bat").is_file():
        print("!! 没找到 run.bat —— NeoForge 服务端还没安装（先跑安装器）")
        return 2

    # 用 side_check 的结论决定装哪些 mod —— 绝不靠记忆
    server_list = HERE / "lists" / "server-mods.txt"
    if not server_list.is_file():
        print(f"!! 缺少 {server_list}（先跑 side_check.py --write-lists lists）")
        return 2
    wanted = [l.strip() for l in server_list.read_text(encoding="utf-8").splitlines() if l.strip()]

    mods_dir = server_dir / "mods"
    mods_dir.mkdir(exist_ok=True)
    copied, missing = [], []
    for name in wanted:
        src = INSTANCE_MODS / name
        if not src.is_file():
            missing.append(name)
            continue
        dst = mods_dir / name
        if not dst.is_file() or dst.stat().st_size != src.stat().st_size:
            shutil.copy2(src, dst)
            copied.append(name)
    # 清掉不该在服务端出现的（曾经装错的纯客户端 mod）
    removed = []
    for jar in list(mods_dir.glob("*.jar")):
        if jar.name not in wanted:
            jar.unlink()
            removed.append(jar.name)

    print(f"服务端 mods: 应有 {len(wanted)} 个，实到 {len(list(mods_dir.glob('*.jar')))} 个"
          f"（新拷 {len(copied)}，清掉 {len(removed)}）")
    for n in removed:
        print(f"   - 移除 {n}")
    if missing:
        print("   !! 实例里找不到: " + ", ".join(missing))
    for c in write_props(server_dir):
        print(f"   server.properties {c}")
    return 0


# ── RCON ─────────────────────────────────────────────────────────────────────

class Rcon:
    def __init__(self, host: str = RCON_HOST, port: int = RCON_PORT, password: str = RCON_PASS):
        self.sock = socket.create_connection((host, port), timeout=10)
        self.rid = 0
        self._send(3, password)
        rid, _ = self._recv()
        if rid == -1:
            raise RuntimeError("RCON 认证失败（密码不对？）")

    def _send(self, ptype: int, body: str) -> None:
        self.rid += 1
        payload = struct.pack("<ii", self.rid, ptype) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _recv(self) -> tuple[int, str]:
        raw = self.sock.recv(4)
        if len(raw) < 4:
            raise RuntimeError("RCON 连接被关闭")
        (length,) = struct.unpack("<i", raw)
        data = b""
        while len(data) < length:
            chunk = self.sock.recv(length - len(data))
            if not chunk:
                break
            data += chunk
        rid, ptype = struct.unpack("<ii", data[:8])
        return rid, data[8:-2].decode("utf-8", errors="replace")

    def cmd(self, command: str, first_timeout: float = 8.0) -> str:
        """执行一条命令并收集响应。

        踩过的坑：一开始首包只等 2 秒。服务端在忙（例如 save-all 同步存档）时来不及回，
        于是**明明执行成功了却收到空串** —— 测试会误报失败、`op` 看起来像没生效。
        现在首包等 8 秒，拿到内容后再用短超时把后续分片收干净。
        """
        self._send(2, command)
        self.sock.settimeout(first_timeout)
        parts: list[str] = []
        try:
            _, body = self._recv()
            if body:
                parts.append(body)
        except socket.timeout:
            return ""
        self.sock.settimeout(0.6)
        while True:
            try:
                _, body = self._recv()
            except socket.timeout:
                break
            if not body:
                break
            parts.append(body)
            if len(parts) > 200:
                break
        self.sock.settimeout(10)
        return "\n".join(parts)

    def close(self) -> None:
        try:
            self.sock.close()
        except Exception:
            pass


# ── 启停 ─────────────────────────────────────────────────────────────────────

def server_log(server_dir: Path) -> Path:
    return server_dir / "logs" / "latest.log"


def wait_ready(server_dir: Path, timeout: int = 420, proc: subprocess.Popen | None = None) -> tuple[bool, list[str]]:
    """等服务端启动完成。判据是日志里出现 "Done (N.NNNs)!"。

    同时盯着**进程死活**：崩了就立刻返回，不必白等满超时（第一次实测就吃了这个亏：
    run.bat 因为 Java 版本崩掉，而等待循环还在傻等）。
    """
    log = server_log(server_dir)
    started = time.time()
    while time.time() - started < timeout:
        if proc is not None and proc.poll() is not None:
            return False, ["!! 服务端进程已退出（exit code %s）" % proc.returncode]
        try:
            text = log.read_text(encoding="utf-8", errors="replace")
        except OSError:
            text = ""
        m = re.search(r'Done \([\d.]+s\)! For help, type "help"', text)
        if m:
            return True, [l for l in text.splitlines() if l.strip()][-15:]
        time.sleep(3)
    try:
        text = log.read_text(encoding="utf-8", errors="replace")
    except OSError:
        text = ""
    return False, [l for l in text.splitlines() if l.strip()][-40:]


def start(server_dir: Path, wait: int = 420) -> int:
    """直接调 java + 参数文件启服务端。

    ⚠️ **不要用 run.bat**，两个坑都真实踩过：
      1. run.bat 调的是裸 `java`，本机 PATH 上是 **JDK 17**（JAVA_HOME 也是 17），
         而 NeoForge 21.1.250 的 class 是 Java 21（major 65）→
         `InvalidModuleDescriptorException: Unsupported major.minor version 65.0` 启动即死。
      2. run.bat 末尾有 `pause` → 失败后永久挂住等按键，自动化会一直卡在那里。
    """
    log = server_log(server_dir)
    if log.is_file():
        log.unlink()          # 服务端每次启动也会轮转；删掉避免读到上次的 Done

    args_file = server_dir / "libraries" / "net" / "neoforged" / "neoforge" / NEOFORGE_VER / "win_args.txt"
    if not args_file.is_file():
        print(f"!! 找不到参数文件: {args_file}（服务端没装好？）")
        return 2
    jvm_args = server_dir / "user_jvm_args.txt"
    if not jvm_args.is_file():
        jvm_args.write_text("-Xmx4G\n-XX:+UseG1GC\n", encoding="ascii")

    java_exe = JAVA if Path(JAVA).is_file() else "java"
    cmd = [java_exe, f"@{jvm_args.name}", f"@{args_file.relative_to(server_dir).as_posix()}", "nogui"]
    print("启动服务端 ...")
    print("  " + " ".join(cmd))
    out_path = ROOT / "data" / "server-console.log"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out = out_path.open("wb")
    proc = subprocess.Popen(cmd, cwd=str(server_dir), stdout=out, stderr=subprocess.STDOUT,
                            stdin=subprocess.DEVNULL)
    print(f"  pid = {proc.pid}（控制台输出: {out_path}）")
    # 立刻登记（不等就绪）：万一就绪超时或中途死掉，清理时才找得到它。
    # 两列格式（pid + 进程创建时刻）比单列 pid 安全：认领时验创建时刻，防 pid 被复用后误杀。
    pid_guard.write(server_dir / ".server.pid", proc.pid)
    ok, tail = wait_ready(server_dir, wait, proc)
    for l in tail:
        print("  | " + l[:200])
    if not ok:
        print("!! 服务端未在超时内就绪")
        if proc.poll() is None:
            try:
                proc.terminate()
            except Exception:
                pass
        return 1
    print("服务端就绪")
    return 0


def stop(server_dir: Path) -> int:
    """发 stop 并等端口关闭。

    注意：服务端处理 `stop` 时会**先关掉 RCON 连接**，所以本地读不到回包是正常现象，
    不是失败（第一版把它报成"RCON stop 失败"，很误导）。
    """
    sent = False
    try:
        r = Rcon()
        try:
            r.cmd("stop")
            sent = True
        except Exception:
            sent = True      # 连接被服务端关闭 = stop 已被接受
        finally:
            r.close()
    except Exception as exc:
        print(f"连不上 RCON（{exc}），退回按 pid 结束")
        pid_file = server_dir / ".server.pid"
        if pid_file.is_file():
            subprocess.run(["taskkill", "/PID", pid_guard.read_pid(pid_file)], capture_output=True)
    if sent:
        print("已发送 stop")
    # 等服务端把世界存完
    for _ in range(90):
        time.sleep(2)
        try:
            probe = Rcon()
            probe.close()
        except Exception:
            print("服务端已停止（端口已关闭）")
            pid_file = server_dir / ".server.pid"
            if pid_file.is_file():
                try:
                    pid_file.unlink()
                except Exception:
                    pass
            return 0
    print("!! 服务端仍在运行")
    return 1


# ── 脚本执行 ──────────────────────────────────────────────────────────────────

def parse_script(path: Path) -> list[dict]:
    steps: list[dict] = []
    cur: dict | None = None
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        key, _, value = line.partition(":")
        key, value = key.strip().lower(), value.strip()
        if key == "cmd":
            cur = {"cmd": value, "expect": [], "line": lineno}
            steps.append(cur)
        elif key == "expect" and cur is not None:
            cur["expect"].append(value)
        elif key in ("wait", "sleep") and cur is not None:
            cur["wait"] = float(value)
        else:
            print(f"  ! 脚本第 {lineno} 行无法识别: {line}")
    return steps


def run_script(server_dir: Path, steps: list[dict]) -> int:
    r = Rcon()
    failures = 0
    try:
        for i, step in enumerate(steps, 1):
            out = r.cmd(step["cmd"])
            if step.get("wait"):
                time.sleep(step["wait"])
            bad = [p for p in step["expect"] if not re.search(p, out, re.MULTILINE)]
            status = "PASS" if not bad else "FAIL"
            if bad:
                failures += 1
            print(f"[{i}] {status} {step['cmd']}")
            for line in out.splitlines()[:12]:
                print(f"      | {line}")
            for p in bad:
                print(f"      x 未匹配: {p}")
            time.sleep(0.25)   # 别把服务端逼到来不及回话
    finally:
        r.close()
    print(f"\n结果: {len(steps) - failures}/{len(steps)} 通过")
    return failures


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="专用服务端控制 + RCON")
    ap.add_argument("--server-dir", default=str(DEFAULT_SERVER))
    ap.add_argument("--setup", action="store_true")
    ap.add_argument("--start", action="store_true")
    ap.add_argument("--stop", action="store_true")
    ap.add_argument("--cmd")
    ap.add_argument("--script")
    ap.add_argument("--wait", type=int, default=420)
    args = ap.parse_args()

    server_dir = Path(args.server_dir)
    rc = 0
    if args.setup:
        rc |= setup(server_dir)
    if args.start:
        rc |= start(server_dir, args.wait)
    if args.cmd:
        r = Rcon()
        print(r.cmd(args.cmd))
        r.close()
    if args.script:
        rc = run_script(server_dir, parse_script(Path(args.script)))
    if args.stop:
        rc |= stop(server_dir)
    return rc


if __name__ == "__main__":
    raise SystemExit(main())

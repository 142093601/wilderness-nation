"""直接和 dsh-computer-use-win 的 MCP server 做一次 JSON-RPC 握手，确认它真能用。

为什么要裸测而不是等工具列表：插件是**我这个会话开始之后**才装的，
所以它不会出现在我当前的工具表里（工具表在会话建立时就固定了）。
但 server 本身现在就能跑 —— 先用 stdio 握一次手，把 22 个工具的真实名字和
health 结果拿出来，这样"能不能用"就有证据，而不是推测。

**坑（实测）**：这个 server 用的是 **LSP 式 `Content-Length` 分帧**
（server.mjs 第 704/715 行自己写出/解析 `Content-Length: N\r\n\r\n`），
不是 MCP 常见的按行 JSON。第一次按行发过去，它一句话都不回、stderr 也是空的。

**坑二（实测）**：`profiles\\web\\node_modules\\dsh-computer-use-win` 是指向
`.generations\\live\\...` 的 **Junction**。server.mjs 末尾的入口守卫是
`import.meta.url === pathToFileURL(process.argv[1]).href` —— 用 junction 路径启动时
两边不一致，脚本**什么都不做就退出**（无输出、stderr 也空，看起来像"没回话"）。
所以这里先 `realpath` 解开 junction。
"""

import json
import os
import subprocess
import sys
from pathlib import Path

SERVER = Path(os.path.realpath(
    Path(os.environ["APPDATA"]) / "dsh-desktop" / "harness" / "profiles" / "web" /
    "node_modules" / "dsh-computer-use-win" / "mcp" / "server.mjs"))


def main() -> int:
    if not SERVER.is_file():
        print(f"找不到 server: {SERVER}")
        return 2
    # 二进制模式：Content-Length 数的是**字节**，用文本模式会把长度算错
    proc = subprocess.Popen(
        ["node", str(SERVER)], stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )

    def send(obj):
        payload = json.dumps(obj).encode("utf-8")
        proc.stdin.write(f"Content-Length: {len(payload)}\r\n\r\n".encode("ascii") + payload)
        proc.stdin.flush()

    def recv():
        headers = {}
        while True:
            line = proc.stdout.readline()
            if not line:
                err = proc.stderr.read()[:2000].decode("utf-8", "replace")
                raise RuntimeError(f"server 没回话，stderr: {err}")
            line = line.decode("utf-8", "replace").strip()
            if not line:
                break
            key, _, value = line.partition(":")
            headers[key.strip().lower()] = value.strip()
        body = proc.stdout.read(int(headers["content-length"]))
        return json.loads(body.decode("utf-8"))

    send({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {
        "protocolVersion": "2024-11-05", "capabilities": {},
        "clientInfo": {"name": "dsh-probe", "version": "1"}}})
    init = recv()
    info = init.get("result", {}).get("serverInfo", {})
    print(f"[initialize] {info.get('name')} v{info.get('version')}")

    send({"jsonrpc": "2.0", "method": "notifications/initialized"})

    send({"jsonrpc": "2.0", "id": 2, "method": "tools/list"})
    tools = recv()["result"]["tools"]
    print(f"[tools/list] {len(tools)} 个工具")
    for t in tools:
        first = (t.get("description") or "").strip().splitlines()[0][:64]
        print(f"  - {t['name']}  {first}")

    # 真调一次 health：这一步会真的起 PowerShell + UIA + 截图后端
    send({"jsonrpc": "2.0", "id": 3, "method": "tools/call",
          "params": {"name": "windows_computer_use_health", "arguments": {}}})
    health = recv()
    content = health.get("result", {}).get("content", [])
    text = content[0].get("text") if content else json.dumps(health)
    print("[health] " + (text or "")[:1500])

    proc.stdin.close()
    proc.wait(timeout=10)
    return 0


if __name__ == "__main__":
    sys.exit(main())

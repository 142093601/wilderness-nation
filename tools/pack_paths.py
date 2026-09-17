"""pack_paths.py -- 读取「本机配置」，让工具在别人的机器上也能跑。

为什么要有这个文件
------------------
工具里原本把这些值**写死在代码里**：

    version_dir = r"D:\\你的实例\\versions\\1.21.1-NeoForge_21.1.250"
    java        = r"C:\\你的 JDK 21\\bin\\java.exe"
    username    = "某人"
    uuid        = "0000..."
    RCON_PASS   = "某口令"        # ← 能远程执行服务器命令的口令

公开仓库里有三个后果：① 密码与个人目录公开；② 换台机器要改 6 个文件；
③ 实例目录改名（例如以后变成正式服）又要全改一遍。

现在改成：**真值放 `pack.local.json`（不进 git），模板 `pack.local.example.json` 进 git**。
本模块负责读取，并在缺配置时给出**能照着做的提示**，而不是一堆 traceback。

用法
----
    from pack_paths import paths
    P = paths()
    P["instance_dir"]      # 客户端实例目录
    P["rcon_password"]     # RCON 口令

    python pack_paths.py   # 自检：打印当前生效的配置（口令打码）
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
LOCAL_FILE = HERE / "pack.local.json"
EXAMPLE_FILE = HERE / "pack.local.example.json"

# 模板值：既作为 example 文件的内容来源，也作为"没配置时"的兜底。
# 全部是占位符，故意不可用——这样一旦真的拿它去跑，会在"路径不存在"处明确报错。
EXAMPLE: dict[str, str] = {
    "instance_dir": r"C:\path\to\your\minecraft\versions\1.21.1-NeoForge_21.1.250",
    "game_root": r"C:\path\to\your\minecraft",
    "java": r"C:\path\to\jdk-21\bin\java.exe",
    "username": "YourMinecraftName",
    "uuid": "00000000000000000000000000000000",
    "server_dir": r"C:\path\to\your\nation-pack-server",
    "rcon_host": "127.0.0.1",
    "rcon_port": "25575",
    "rcon_password": "change-me",
}

SECRET_KEYS = {"rcon_password"}

_cache: dict[str, str] | None = None


def paths(reload: bool = False) -> dict[str, str]:
    """返回生效配置：`pack.local.json` 覆盖模板默认值。"""
    global _cache
    if _cache is not None and not reload:
        return _cache
    data = dict(EXAMPLE)
    if LOCAL_FILE.is_file():
        try:
            data.update(json.loads(LOCAL_FILE.read_text(encoding="utf-8")))
            data["_source"] = LOCAL_FILE.name
        except Exception as exc:                     # 配置写坏了要说清楚
            raise SystemExit(
                f"pack.local.json 解析失败：{type(exc).__name__}: {exc}\n"
                f"  检查这个文件是否是合法 JSON（逗号、引号、反斜杠要转义成 \\\\）。"
            ) from exc
    else:
        data["_source"] = f"{EXAMPLE_FILE.name}（模板兜底）"
    _cache = data
    return data


def require_usable() -> None:
    """在真正要跑之前调用：把"还是模板值"这件事说清楚，给出照抄命令。"""
    p = paths()
    if p["_source"].startswith(EXAMPLE_FILE.name):
        raise SystemExit(
            "还没有本机配置。\n"
            f"  1) 复制模板：  copy {EXAMPLE_FILE.name} {LOCAL_FILE.name}\n"
            f"  2) 改里面的路径 / 用户名 / RCON 口令\n"
            f"  （{LOCAL_FILE.name} 已被 .gitignore 排除，不会进仓库）"
        )


def mask(value: str) -> str:
    if not value:
        return "(空)"
    return value[:2] + "*" * max(0, len(value) - 4) + value[-2:] if len(value) > 6 else "*" * len(value)


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass
    p = paths(reload=True)
    print(f"配置来源：{p.get('_source')}")
    print(f"模板文件：{EXAMPLE_FILE.name}{'（存在）' if EXAMPLE_FILE.is_file() else '（缺失）'}")
    print(f"本机文件：{LOCAL_FILE.name}{'（存在）' if LOCAL_FILE.is_file() else '（不存在 → 正在用模板兜底）'}")
    print()
    for k, v in p.items():
        if k.startswith("_"):
            continue
        shown = mask(v) if k in SECRET_KEYS else v
        mark = "  ← 秘密（不要进仓库）" if k in SECRET_KEYS else ""
        print(f"  {k:<15} = {shown}{mark}")
    for k in ("instance_dir", "game_root", "server_dir"):
        v = p.get(k, "")
        if v and not Path(v).exists():
            print(f"  ! {k} 指向的路径不存在：{v}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""side_check.py -- 判断每个 mod 是"客户端/服务端/两端"，用于组装服务端 mods 目录。

为什么不能靠记忆或名字
----------------------
"这个 mod 是不是纯客户端"直接决定它能不能进服务端 `mods/`：
放错会报错、或者静默不加载，而 `generateServerModsList` 之类的手工清单一定会过期。

做法：拿**每个 jar 的 SHA-1** 去 Modrinth 反查版本 → 项目 → 读官方标注的
`client_side` / `server_side`（required / optional / unsupported）。
这是 Modrinth 页面上的同一份数据，比自己判断可靠。

用法
----
    python side_check.py --mods "<你的实例目录>\\mods"
    python side_check.py --mods <dir> --write-lists lists     # 顺便产出 server-only 清单
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "nation-pack-side-check/0.1 (personal modpack dev)"

# 自研 mod 的 jar 名前缀：Modrinth 上查不到，但**服务端一定要装**（见下方 server 清单处）。
LOCAL_SERVER_PREFIX = "statecraft-"


def api_get(path: str) -> dict | None:
    req = urllib.request.Request(API + path, headers={"User-Agent": UA})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            time.sleep(1.5 * (attempt + 1))
        except Exception:
            time.sleep(1.5 * (attempt + 1))
    return None


def sha1_of(path: Path) -> str:
    h = hashlib.sha1()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="按 SHA-1 反查 mod 的端侧支持")
    ap.add_argument("--mods", required=True, help="mods 目录")
    ap.add_argument("--write-lists", help="把结果清单写到这个目录")
    ap.add_argument("--json", help="把完整结果写到这个 JSON")
    args = ap.parse_args()

    mods_dir = Path(args.mods)
    jars = sorted(mods_dir.glob("*.jar"))
    if not jars:
        print(f"目录里没有 jar: {mods_dir}")
        return 1

    rows: list[dict] = []
    proj_cache: dict[str, dict] = {}
    for jar in jars:
        entry = {"file": jar.name, "sha1": sha1_of(jar)}
        ver = api_get(f"/version_file/{entry['sha1']}")
        if not ver:
            entry.update({"slug": None, "client_side": "?", "server_side": "?",
                          "note": "Modrinth 上没有这个文件（可能是 CF-only 或改过）"})
            rows.append(entry)
            continue
        pid = ver.get("project_id")
        if pid not in proj_cache:
            proj_cache[pid] = api_get(f"/project/{pid}") or {}
        proj = proj_cache[pid]
        entry.update({
            "slug": proj.get("slug") or ver.get("project_id"),
            "title": proj.get("title"),
            "client_side": proj.get("client_side", "?"),
            "server_side": proj.get("server_side", "?"),
            "loaders": ver.get("loaders"),
            "version_number": ver.get("version_number"),
        })
        rows.append(entry)

    # 判定：服务端要不要装
    def verdict(r: dict) -> str:
        if r.get("server_side") == "unsupported":
            return "客户端专用 → 不要装服务端"
        if r.get("server_side") in ("required", "optional"):
            return "服务端要装"
        return "未知，需人工确认"

    for r in rows:
        r["verdict"] = verdict(r)

    w = max(len(r["file"]) for r in rows)
    print(f"{'文件'.ljust(w)}  {'slug':<28} {'client':<10} {'server':<10} 判定")
    print("-" * (w + 62))
    for r in sorted(rows, key=lambda x: (x.get("server_side") or "?", x["file"])):
        print(f"{r['file'].ljust(w)}  {str(r.get('slug'))[:28]:<28} "
              f"{str(r.get('client_side')):<10} {str(r.get('server_side')):<10} {r['verdict']}")

    server = [r for r in rows if r.get("server_side") in ("required", "optional")]
    client_only = [r for r in rows if r.get("server_side") == "unsupported"]
    unknown = [r for r in rows if r.get("server_side") not in ("required", "optional", "unsupported")]

    # 未知的一律**并入**服务端清单。
    # 为什么不是"排除待确认"：Modrinth 查不到 ≠ 不要装。曾经按"排除"写过一版，
    # 结果 `ftb-quests` 被踢出去、而 `FTBQuestsOptimizer` 还在 —— 服务端直接起不来
    # （"Mod ftbqopt requires ftbquests"）。这是个**开发用**服务端：除了被明确标成
    # `unsupported` 的纯客户端 mod，其余全装才是安全解，宁可多装也不要少装。
    server.extend(unknown)

    # 自家 mod 永远进服务端清单。
    # 为什么必须在这里写死：这个脚本靠 Modrinth 的 SHA1 查 side，而我们自己的 jar
    # 当然查不到 → 会被上面的规则顺带并进去，但那条规则随时可能改，
    # 所以这里再钉一次（曾经真的发生过：`--setup` 把我们的 mod 当多余的删掉了）。
    for r in rows:
        if r["file"].startswith(LOCAL_SERVER_PREFIX):
            r["verdict"] = "本地自研 mod → 服务端必装"
            if r not in server:
                server.append(r)
    print(f"\n合计 {len(rows)} 个：服务端要装 {len(server)} · 纯客户端 {len(client_only)} · 未知 {len(unknown)}")
    print("  本地自研（无条件装服务端）: " + ", ".join(
        r["file"] for r in server if r["file"].startswith(LOCAL_SERVER_PREFIX)) or "  （没有）")

    if client_only:
        print("  纯客户端（务必排除）: " + ", ".join(r["file"] for r in client_only))
    if unknown:
        print("  未知（需人工确认）: " + ", ".join(r["file"] for r in unknown))

    if args.write_lists:
        out = Path(args.write_lists)
        out.mkdir(parents=True, exist_ok=True)
        (out / "server-mods.txt").write_text(
            "\n".join(sorted(r["file"] for r in server)) + "\n", encoding="utf-8")
        (out / "client-only.txt").write_text(
            "\n".join(r["file"] for r in client_only) + "\n", encoding="utf-8")
        print(f"已写: {out/'server-mods.txt'} / {out/'client-only.txt'}")
        print(f"  其中 {len(unknown)} 个是 Modrinth 查不到 side 的（已按「宁可多装」并入）")
    if args.json:
        Path(args.json).write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"已写: {args.json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

"""apply_modlist.py -- 把 modlist.tsv 变成"可核验、可执行"的动作。

为什么要有它
------------
清单写在 md 里是给人看的，但"文档说装了、实际没装"这种漂移迟早发生。
所以清单同时存一份 TSV 给脚本读，本脚本负责两件事：

    --verify   逐个查 Modrinth，确认每个 slug 在 **1.21.1 + neoforge** 上真有版本
    --apply    把状态为 plan 的项加进 packwiz 包（**会真的下载 jar**）

`--verify` 是这套流程的守门人：**任何没通过核验的 mod 不允许进清单**。
（这也挡住了"凭记忆写个 mod 名"——名字错了、或者 1.21.1 没版本，立刻会被抓出来。）

用法
----
    python apply_modlist.py --verify
    python apply_modlist.py --verify --json data/modlist-verify.json
    python apply_modlist.py --apply --pack pack        # 需要 packwiz 在 PATH 上
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
API = "https://api.modrinth.com/v2"
UA = "nation-pack-modlist/0.1 (personal modpack dev)"
MC, LOADER = "1.21.1", "neoforge"

VALID = {"installed", "plan", "hold", "skip"}


def load_tsv(path: Path) -> list[dict]:
    rows = []
    for lineno, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = [p.strip() for p in line.split("\t")]
        if len(parts) < 3:
            print(f"  ! 第 {lineno} 行字段不足（需要 slug<TAB>状态<TAB>功能位[<TAB>来源]）: {line}")
            continue
        slug, status, slot = parts[0], parts[1], parts[2]
        # 第 4 列：来源。mr=Modrinth（默认）· cf=CurseForge（如 FTB 系列）
        src = parts[3].lower() if len(parts) > 3 and parts[3] else "mr"
        if status not in VALID:
            print(f"  ! 第 {lineno} 行状态非法 '{status}'（应为 {sorted(VALID)}）")
            continue
        if src not in ("mr", "cf"):
            print(f"  ! 第 {lineno} 行来源非法 '{src}'（应为 mr / cf）")
            continue
        rows.append({"slug": slug, "status": status, "slot": slot, "src": src})
    return rows


def api_get(path: str, params: dict | None = None) -> object:
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode(params, doseq=True)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            time.sleep(1.2 * (attempt + 1))
        except Exception:
            time.sleep(1.2 * (attempt + 1))
    return None


def load_pool_slugs() -> set[str]:
    """本地已核验池：两个 survey JSON 本身就是按 **1.21.1 + neoforge** 筛出来的。

    踩过：第一版对 104 条**逐个联网**查版本，跑 600 秒都没跑完（国内到 Modrinth 的
    版本查询延迟很高）。而其中 85 条在本地池里就有——**能本地判定的绝不联网**。
    """
    pool: set[str] = set()
    for f in ("mod-survey.json", "mod-survey-thick.json"):
        p = ROOT / "data" / f
        if not p.is_file():
            continue
        try:
            for r in json.loads(p.read_text(encoding="utf-8")):
                if r.get("slug"):
                    pool.add(r["slug"])
        except Exception:
            continue
    return pool


def reference_pack_jars() -> dict[str, str]:
    """本机**参照包**里的 jar 文件名 → 路径。

    为什么需要：FTB 系列（Quests/Teams/Chunks…）**只在 CurseForge 分发**，
    Modrinth 上查不到 —— 用 Modrinth 的核验器会把它们全判成"查无"。
    但本机装着两个 1.21.1 NeoForge 的成熟包（All the Mods 10 / Skyhive），
    它们的 `mods/` 里就有 `ftb-quests-neoforge-2101.1.24.jar` 这种**带版本号的硬证据**。
    → 于是核验多了一条来源：**本地参照包实证**。
    """
    out: dict[str, str] = {}
    try:
        import pack_paths
        raw = pack_paths.paths().get("reference_packs", "") or ""
    except Exception:
        raw = ""
    for d in [x.strip() for x in raw.split(";") if x.strip()]:
        p = Path(d)
        if not p.is_dir():
            continue
        for jar in p.glob("*.jar"):
            out[jar.name.lower()] = str(jar)
    return out


def verify(rows: list[dict]) -> list[dict]:
    pool = load_pool_slugs()
    refs = reference_pack_jars()
    n_cf = sum(1 for r in rows if r.get("src") == "cf")
    local = sum(1 for r in rows if r.get("src", "mr") == "mr" and r["slug"] in pool)
    installed = sum(1 for r in rows if r["slug"] not in pool and r["status"] == "installed")
    print(f"  参照包 jar 索引 {len(refs)} 个（供 CF 类核实用）")
    print(f"  本地候选池命中 {local} 条；已装（活证据，免查） {installed} 条；"
          f"CF 类 {n_cf} 条；其余需联网")
    for i, row in enumerate(rows, 1):
        slug, src = row["slug"], row.get("src", "mr")
        if src == "cf":
            # 在参照包里找文件名含该 slug 的 jar（FTB 的命名就是 ftb-quests-neoforge-2101.1.24.jar）
            hit = next((name for name in refs if slug.replace("-", "") in name.replace("-", "")), None)
            if hit:
                row["verify"], row["source"] = "OK", "本地参照包实证"
                row["file"] = hit
            else:
                row["verify"], row["source"] = "CF_UNVERIFIED", "需要人工看 CurseForge"
        elif slug in pool:
            row["verify"], row["source"] = "OK", "本地候选池"
        elif row["status"] == "installed":
            # 它正跑在这个包里，且 baseline.csv 有它的加载/TPS 证据 —— 不需要再问接口
            row["verify"], row["source"] = "OK", "已装运行中"
        else:
            vers = api_get(f"/project/{slug}/version", {
                "loaders": json.dumps([LOADER]),
                "game_versions": json.dumps([MC]),
            })
            if vers is None:
                row["verify"], row["source"] = "PROJECT_NOT_FOUND", "接口"
            elif not vers:
                row["verify"], row["source"] = "NO_VERSION_FOR_TARGET", "接口"
            else:
                row["verify"], row["source"] = "OK", "接口"
                row["version_number"] = vers[0].get("version_number")
                row["file"] = (vers[0].get("files") or [{}])[0].get("filename")
            time.sleep(0.15)
        if row["verify"] != "OK":
            print(f"  [{i:>3}/{len(rows)}] 失败 {slug:<34} [{src}] {row['verify']}", flush=True)
    return rows


def apply_plan(rows: list[dict], pack_dir: Path) -> int:
    todo = [r for r in rows if r["status"] == "plan" and r.get("verify") == "OK"]
    print(f"\n要加入 packwiz 的：{len(todo)} 个（状态 plan 且已核验通过）")
    failed = []
    for i, r in enumerate(todo, 1):
        # 来源决定用哪个 packwiz 子命令：FTB 系列在 CurseForge，用 modrinth 加会失败
        sub = "curseforge" if r.get("src") == "cf" else "modrinth"
        cmd = ["packwiz", sub, "add", r["slug"], "-y"]
        p = subprocess.run(cmd, cwd=str(pack_dir), capture_output=True, text=True,
                           encoding="utf-8", errors="replace")
        ok = p.returncode == 0
        print(f"  [{i:>3}/{len(todo)}] {'OK  ' if ok else 'FAIL'} {r['slug']}")
        if not ok:
            failed.append(r["slug"])
            tail = (p.stderr or p.stdout or "").strip().splitlines()[-1:] or [""]
            print(f"        {tail[0][:150]}")
    if failed:
        print(f"\n失败 {len(failed)} 个：{', '.join(failed)}")
    return len(failed)


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="核验并应用 mod 清单")
    ap.add_argument("--tsv", default=str(ROOT / "modlist.tsv"))
    ap.add_argument("--verify", action="store_true")
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("--pack", default=str(ROOT / "pack"), help="packwiz 工作目录")
    ap.add_argument("--json")
    args = ap.parse_args()

    rows = load_tsv(Path(args.tsv))
    if not rows:
        print("清单为空")
        return 1
    by_status: dict[str, int] = {}
    for r in rows:
        by_status[r["status"]] = by_status.get(r["status"], 0) + 1
    print(f"清单载入：{len(rows)} 条  " + " · ".join(f"{k}={v}" for k, v in sorted(by_status.items())))

    if args.verify or args.apply:
        print(f"\n核验（目标 {MC} + {LOADER}）...")
        rows = verify(rows)
        bad = [r for r in rows if r["verify"] != "OK"]
        ok_n = len(rows) - len(bad)
        print(f"\n核验结果：{ok_n}/{len(rows)} 通过")
        if bad:
            print("未通过（这些**不允许进清单**，需改名或换候选）：")
            for r in bad:
                print(f"  x {r['slug']:<34} [{r['status']}] {r['verify']}")
        if args.json:
            Path(args.json).parent.mkdir(parents=True, exist_ok=True)
            Path(args.json).write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
            print(f"已写: {args.json}")
        if args.apply:
            if bad:
                print("\n有未通过项，先处理再 apply（避免装出半个包）")
                return 2
            n = apply_plan(rows, Path(args.pack))
            return 1 if n else 0
        return 1 if bad else 0
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 packwiz 的**元数据**落成真实 jar（带哈希校验），装进实例的 mods/。

为什么需要这个工具：`tools/apply_modlist.py --apply` 只往 `pack/` 里写**元数据**
（`metadata:curseforge` 或直接下载地址），**不下载 jar**。要冒烟测试就必须把 jar 落下来。
现有的 `packwiz-installer-bootstrap` 也能干这件事，但它是个外部 jar + 黑盒；
这个工具用同一份元数据自己下载并**校验哈希**，可控、可审计、可分批。

两种元数据形态（packwiz 的实际写法）：
  · Modrinth：有 `[download] url` + `hash-format = sha512` + hash → 直接下、直接校验
  · CurseForge：只有 `mode = "metadata:curseforge"` + `file-id` + `project-id`（hash 是 sha1）
    → 下载地址要经 CF 接口换：`/v1/mods/{projectId}/files/{fileId}` → `downloadUrl`（走公开代理）

用法：
  python tools/materialize_pack.py                            # 落 pack/ 里**全部**（推荐：含 packwiz 自动加的依赖）
  python tools/materialize_pack.py --dry-run                  # 只列出要做什么
  python tools/materialize_pack.py --only jei,terralith       # 只落指定 slug
  python tools/materialize_pack.py --dest <mods 目录>         # 覆盖目标（默认读 pack.local.json 的 instance_dir/mods）

⚠️ **不要用 `--batch` 来落盘**：packwiz 会自动把依赖也写进 `pack/mods/`（architectury、extralib…），
   而 `--batch` 只落批次内的 slug → 依赖缺 jar，游戏启动直接崩（2026-09-19 冒烟实测踩到）。
   装机流程是：`apply_modlist --batch N --apply` 之后，**无参数**跑一次本工具。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import ssl
import sys
import time
import tomllib
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

try:
    import certifi
    SSL_CTX = ssl.create_default_context(cafile=certifi.where())
except Exception:  # noqa: BLE001
    SSL_CTX = ssl.create_default_context()

ROOT = Path(__file__).resolve().parent.parent
CF = "https://api.curse.tools/v1/cf"
UA = "wilderness-nation-materialize/1.0 (local modpack tooling)"
HASHERS = {"sha512": hashlib.sha512, "sha1": hashlib.sha1, "sha256": hashlib.sha256, "md5": hashlib.md5}


def local_config() -> dict:
    p = ROOT / "tools" / "pack.local.json"
    if p.is_file():
        try:
            return json.loads(p.read_text(encoding="utf-8"))
        except Exception:  # noqa: BLE001
            return {}
    return {}


def http_json(url: str, timeout: int = 40):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as r:
        return json.loads(r.read().decode("utf-8", "replace"))


_CF_FILES: dict[int, list] = {}


def cf_files(project_id: int) -> list:
    """按项目拉文件列表（带进程内缓存）。"""
    if project_id not in _CF_FILES:
        try:
            d = http_json(f"{CF}/mods/{project_id}/files?pageSize=50")
            data = d.get("data") if isinstance(d, dict) else d
            _CF_FILES[project_id] = data or []
        except Exception as e:  # noqa: BLE001
            print(f"      CF 文件列表拉取失败：{e}")
            _CF_FILES[project_id] = []
        time.sleep(0.3)
    return _CF_FILES[project_id]


def cf_download_url(project_id: int, file_id: int) -> str | None:
    """CF 元数据只给了 id，下载地址要问接口。

    **端点形状踩过坑**：`/mods/{projectId}/files/{fileId}` 在公开代理上是 **404**，
    正确做法是 `/mods/{projectId}/files`（列表）再按 `file-id` 筛出 `downloadUrl`。
    """
    for f in cf_files(project_id):
        if f.get("id") == file_id:
            return f.get("downloadUrl")
    return None


def read_entries(pack_dir: Path) -> list[dict]:
    out = []
    for f in sorted((pack_dir / "mods").glob("*.pw.toml")):
        # 注意：Path.stem 只去掉最后一个后缀（jei.pw.toml → "jei.pw"），
        # 必须手工剥掉整个 ".pw.toml"，否则 slug 全带 ".pw" 尾巴、批次过滤永远匹配 0 条。
        slug = f.name[:-len(".pw.toml")] if f.name.endswith(".pw.toml") else f.stem
        try:
            d = tomllib.loads(f.read_text(encoding="utf-8"))
        except Exception as e:  # noqa: BLE001
            print(f"  ! 解析失败 {f.name}: {e}")
            continue
        dl = d.get("download") or {}
        out.append({
            "slug": slug,
            "name": d.get("name") or slug,
            "filename": d.get("filename") or f"{f.stem}.jar",
            "side": d.get("side") or "?",
            "url": dl.get("url"),
            "hash": dl.get("hash"),
            "hash_format": (dl.get("hash-format") or "").lower(),
            "mode": dl.get("mode"),
            "mod_id": ((d.get("update") or {}).get("modrinth") or {}).get("mod-id"),
            "cf_project": ((d.get("update") or {}).get("curseforge") or {}).get("project-id"),
            "cf_file": ((d.get("update") or {}).get("curseforge") or {}).get("file-id"),
        })
    return out


def file_matches(path: Path, fmt: str, want: str) -> bool:
    if not path.is_file() or fmt not in HASHERS:
        return False
    h = HASHERS[fmt]()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest().lower() == (want or "").lower()


def download(url: str, dest: Path, fmt: str, want: str) -> tuple[bool, str]:
    tmp = dest.with_suffix(dest.suffix + ".part")
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=120, context=SSL_CTX) as r, tmp.open("wb") as fo:
            while True:
                chunk = r.read(1 << 20)
                if not chunk:
                    break
                fo.write(chunk)
    except Exception as e:  # noqa: BLE001
        tmp.unlink(missing_ok=True)
        return False, f"下载失败 {e}"
    if fmt in HASHERS and want:
        h = HASHERS[fmt]()
        with tmp.open("rb") as fh:
            for chunk in iter(lambda: fh.read(1 << 20), b""):
                h.update(chunk)
        if h.hexdigest().lower() != want.lower():
            bad = h.hexdigest()
            tmp.unlink(missing_ok=True)
            return False, f"哈希不符（期望 {want[:12]}… 实际 {bad[:12]}…）"
    tmp.replace(dest)
    return True, "已下载并校验" if (fmt in HASHERS and want) else "已下载（无哈希可比）"


def entry_identity(e: dict) -> str:
    """条目的**唯一身份**：Modrinth 的 mod-id / CurseForge 的 project-id。

    为什么不能按文件名判断"是不是同一个 mod 的旧版本"：
    `SuperMartijn642's Core Lib` 与 `SuperMartijn642's Config Lib` 是两个不同的项目，
    但文件名都以 `supermartijn642` 开头 —— 第一版 prune 按"第一个数字前的前缀"匹配，
    于是把 corelib 当成了 configlib 的旧版本 **删掉了**（2026-09-19 真实踩到）。
    → 只有项目 id 是可靠的同一性判据。
    """
    if e.get("mod_id"):
        return "mr:" + e["mod_id"]
    if e.get("cf_project"):
        return "cf:%s" % e["cf_project"]
    return "slug:" + e["slug"]


def prune_stale(dest: Path, entries: list[dict], dry: bool) -> int:
    """按**项目 id 记账**删除旧版本文件（升级后残留会变成"同一 mod 两个版本"）。

    安全边界：只删"本工具上次为**同一个项目 id** 写下的、且与当前文件名不同的"文件。
    手工安装的、不在 pack 里的、以及别的 mod，一律不碰。
    """
    man_path = ROOT / "data" / "materialize-manifest.json"
    try:
        manifest = json.loads(man_path.read_text(encoding="utf-8")) if man_path.is_file() else {}
    except Exception:  # noqa: BLE001
        manifest = {}
    removed = 0
    new_manifest: dict[str, str] = {}
    for e in entries:
        ident = entry_identity(e)
        prev = manifest.get(ident)
        if prev and prev != e["filename"]:
            old = dest / prev
            if old.is_file():
                print(f"  prune 旧版本：{prev}（该项目现为 {e['filename']}）")
                if not dry:
                    old.unlink()
                removed += 1
        new_manifest[ident] = e["filename"]
    if not dry:
        man_path.parent.mkdir(parents=True, exist_ok=True)
        man_path.write_text(json.dumps(new_manifest, ensure_ascii=False, indent=1), encoding="utf-8")
    return removed


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:  # noqa: BLE001
        pass
    cfg = local_config()

    ap = argparse.ArgumentParser(description="把 packwiz 元数据落成真实 jar（带哈希校验）")
    ap.add_argument("--pack", default=str(ROOT / "pack"))
    ap.add_argument("--dest", default=None, help="目标 mods 目录（默认 <instance_dir>/mods）")
    ap.add_argument("--batch", type=int, help="只落 install-batches.tsv 里该批次的 slug")
    ap.add_argument("--batches-file", default=str(ROOT / "tools" / "lists" / "install-batches.tsv"))
    ap.add_argument("--only", help="逗号分隔的 slug 白名单")
    ap.add_argument("--side", choices=["client", "server"],
                    help="只落某一端需要的（读 .pw.toml 的 side 字段；both 两端都要）。"
                         "落服务端时用 --side server --dest <server>/mods")
    ap.add_argument("--prune", action="store_true",
                    help="删掉被 pack 管理的 mod 的**旧版本文件**（升版后的残渣）。"
                         "只按「文件名去掉版本号后的前缀」匹配，手工装的、不在 pack 里的一律不动。")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    pack_dir = Path(args.pack)
    if args.dest:
        dest = Path(args.dest)
    elif cfg.get("instance_dir"):
        dest = Path(cfg["instance_dir"]) / "mods"
    else:
        print("没有 --dest，且 pack.local.json 里没有 instance_dir")
        return 1
    if not (pack_dir / "mods").is_dir():
        print(f"{pack_dir}/mods 不存在（先跑 apply_modlist --apply）")
        return 1

    entries = read_entries(pack_dir)
    if args.batch:
        want = set()
        for line in Path(args.batches_file).read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            p = line.split("\t")
            if len(p) >= 2 and p[0].strip() == str(args.batch):
                want.add(p[1].strip())
        entries = [e for e in entries if e["slug"] in want]
        print(f"批次 {args.batch}：pack/ 里匹配到 {len(entries)} 条")
        print("  ⚠️ --batch 不含 packwiz 自动加入的依赖条目 → 依赖会缺 jar、游戏启动会崩。"
              "装机请改用无参数运行（落 pack/ 全部）。")
    if args.only:
        keep = {s.strip() for s in args.only.split(",")}
        entries = [e for e in entries if e["slug"] in keep]
    if args.side:
        # side 字段是 packwiz 的端侧标记：both / client / server
        want = {"both", args.side}
        before = len(entries)
        entries = [e for e in entries if (e.get("side") or "both") in want]
        print(f"端侧过滤 --side {args.side}：{before} → {len(entries)} 条（both + {args.side}）")

    print(f"pack: {pack_dir}  →  dest: {dest}")
    print(f"待处理 {len(entries)} 条\n")
    dest.mkdir(parents=True, exist_ok=True)

    n_ok = n_skip = n_fail = 0
    failed: list[str] = []
    for i, e in enumerate(entries, 1):
        target = dest / e["filename"]
        fmt, want = e["hash_format"], e["hash"] or ""
        if file_matches(target, fmt, want):
            n_skip += 1
            print(f"  [{i:>3}/{len(entries)}] 已有 {e['filename']}")
            continue
        if args.dry_run:
            src = "CF 接口换地址" if e.get("mode") == "metadata:curseforge" else "直链"
            print(f"  [{i:>3}/{len(entries)}] 将下载 {e['slug']:<34} {e['filename'][:44]:<44} [{src}]")
            continue
        url = e.get("url")
        if not url and e.get("mode") == "metadata:curseforge" and e.get("cf_project") and e.get("cf_file"):
            url = cf_download_url(int(e["cf_project"]), int(e["cf_file"]))
            time.sleep(0.3)
        if not url:
            n_fail += 1
            failed.append(e["slug"])
            print(f"  [{i:>3}/{len(entries)}] 无下载地址 {e['slug']}")
            continue
        ok, msg = download(url, target, fmt, want)
        if ok:
            n_ok += 1
            print(f"  [{i:>3}/{len(entries)}] OK   {e['slug']:<34} {msg}")
        else:
            n_fail += 1
            failed.append(e["slug"])
            print(f"  [{i:>3}/{len(entries)}] FAIL {e['slug']:<34} {msg}")

    print(f"\n结果：新下 {n_ok} · 已存在 {n_skip} · 失败 {n_fail}")
    if failed:
        print("失败：" + ", ".join(failed))
    if args.prune:
        print("\n清理旧版本残渣：")
        n_pruned = prune_stale(dest, entries, args.dry_run)
        print(f"  删除 {n_pruned} 个" + ("（dry-run，未真删）" if args.dry_run else ""))
    return 1 if n_fail else 0


if __name__ == "__main__":
    raise SystemExit(main())

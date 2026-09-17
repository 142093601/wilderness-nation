"""
mod 下载器 —— 按 Modrinth 公开 API 解析版本、递归解析前置、下载并校验 SHA-1。

为什么不用手点网页：手点会拿到错版本、漏前置、且**无法复现**。
这个脚本产出 `mods-manifest.json`：什么 slug、什么版本、哪个文件、SHA-1 是什么 ——
这就是 BOOTSTRAP.md 要求的"mods 列表备份"，也是以后迁移 packwiz 的输入。

用法：
  python fetch_mods.py --list v0-core-perf --out "C:\\instances\\nation\\mods" --dry-run
  python fetch_mods.py --list v0-core-perf --out "C:\\instances\\nation\\mods"
  python fetch_mods.py --mods create open-parties-and-claims --out ... --dry-run

设计约束：
  * 只用标准库（urllib / hashlib / json）—— 不装任何第三方包；
  * 必需前置（dependency_type=required）会递归解析，可选前置（optional）不自动装；
  * 已有同 SHA-1 的文件直接跳过；SHA-1 不匹配 **不落盘**（先删掉）；
  * Modrinth 要求带可识别的 User-Agent，这里照做。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any

API = "https://api.modrinth.com/v2"
UA = "nation-pack-bootstrap/0.1 (local modpack tooling; contact: local user)"
SLEEP = 0.25          # 对 API 客气一点
MAX_DEPTH = 5         # 前置递归深度上限

# v0 清单（与 BOOTSTRAP.md 的分组一一对应）
LISTS: dict[str, list[str]] = {
    "v0-core-perf": [
        "embeddium", "lithium", "ferrite-core", "modernfix", "entityculling",
        "immediatelyfast", "clumps", "servercore", "neruina", "spark", "chunky",
    ],
    "v0-perf-extra": ["moreculling", "scalablelux", "c2me-neoforge"],
    "v0-create": ["create"],
    "v0-claims": ["open-parties-and-claims"],
    "v0-structure": ["yungs-better-dungeons"],
    "v0-all": [
        # 注意顺序 = 建议的安装顺序（组 0 是纯加载器，无 mod）
        "embeddium", "lithium", "ferrite-core", "modernfix", "entityculling",
        "immediatelyfast", "clumps", "servercore", "neruina", "spark", "chunky",
        # 组 1.5（建议逐个加）
        "moreculling", "scalablelux", "c2me-neoforge",
        # 组 2
        "create",
        # 组 3
        "open-parties-and-claims",
        # 组 4
        "yungs-better-dungeons",
    ],
}


def api_get(path: str, params: dict[str, str] | None = None) -> Any:
    url = f"{API}{path}"
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode("utf-8"))


def pick_version(slug: str, loader: str, mc: str) -> dict[str, Any] | None:
    """取该项目在指定加载器 + MC 版本上的最新版本。"""
    facets = json.dumps([[f"loaders:{loader}"], [f"game_versions:{mc}"]])
    try:
        versions = api_get(f"/project/{slug}/version", {"loaders": json.dumps([loader]),
                                                        "game_versions": json.dumps([mc])})
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise
    if not versions:
        return None
    # API 返回按发布时间倒序，取第一个（最新）
    return versions[0]


def get_version_by_id(version_id: str) -> dict[str, Any]:
    return api_get(f"/version/{version_id}")


def primary_file(version: dict[str, Any]) -> dict[str, Any] | None:
    files = version.get("files") or []
    for f in files:
        if f.get("primary"):
            return f
    return files[0] if files else None


def sha1_of(path: Path) -> str:
    h = hashlib.sha1()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def download(url: str, dest: Path, expect_size: int | None = None,
             expect_sha1: str | None = None, attempts: int = 3) -> tuple[str, int]:
    """下载并校验；不通过就重试，最终仍不通过则抛异常。

    为什么要这么啰嗦：TCP 连接提前断开时 `read()` 会**正常返回空**，
    于是会静默写入一个**截断的文件**（大文件更容易中招）。
    所以要同时比对 Content-Length、文件大小与 SHA-1，并且失败就重试。
    实测依据：C2ME（3.5 MiB）与 Create（18 MiB）各失败过一次，重试后一个通过。
    """
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    last_error = "download failed"
    for attempt in range(1, attempts + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            got = 0
            with urllib.request.urlopen(req, timeout=180) as resp, tmp.open("wb") as fh:
                while True:
                    chunk = resp.read(1 << 20)
                    if not chunk:
                        break
                    fh.write(chunk)
                    got += len(chunk)
            sha = sha1_of(tmp)
            size = tmp.stat().st_size
            if expect_size is not None and size != expect_size:
                last_error = f"size {size} != declared {expect_size} (attempt {attempt}/{attempts})"
            elif expect_sha1 is not None and sha != expect_sha1:
                last_error = (f"sha1 {sha[:12]} != declared {expect_sha1[:12]} "
                              f"(attempt {attempt}/{attempts}, size {size})")
            else:
                tmp.replace(dest)
                return sha, size
        except Exception as exc:
            last_error = f"{type(exc).__name__}: {exc} (attempt {attempt}/{attempts})"
        time.sleep(1.5 * attempt)
    tmp.unlink(missing_ok=True)
    raise RuntimeError(last_error)


def resolve(slugs: list[str], loader: str, mc: str) -> tuple[list[dict[str, Any]], list[str], list[str]]:
    """解析出待装列表（含递归前置）。返回 (items, warnings, errors)。"""
    items: dict[str, dict[str, Any]] = {}     # 按 project_id 去重
    warnings: list[str] = []
    errors: list[str] = []
    visited_projects: set[str] = set()

    def visit(slug: str, reason: str, depth: int, want_version_id: str | None = None) -> None:
        if depth > MAX_DEPTH:
            warnings.append(f"{slug}: 前置递归超过 {MAX_DEPTH} 层，停在这里（{reason}）")
            return
        version = get_version_by_id(want_version_id) if want_version_id else pick_version(slug, loader, mc)
        if version is None:
            errors.append(f"{slug}: 在 {loader} + {mc} 上找不到版本（{reason}）")
            return
        pid = version.get("project_id") or slug
        if pid in visited_projects:
            return
        visited_projects.add(pid)
        item = {
            "slug": slug,
            "project_id": pid,
            "version_number": version.get("version_number"),
            "version_id": version.get("id"),
            "name": version.get("name"),
            "loaders": version.get("loaders"),
            "game_versions": version.get("game_versions"),
            "date_published": version.get("date_published"),
            "reason": reason,
            "file": None,
            "required_deps": [],
        }
        f = primary_file(version)
        if f is None:
            errors.append(f"{slug}: 该版本没有可下载文件")
            return
        item["file"] = {
            "filename": f.get("filename"),
            "url": f.get("url"),
            "size": f.get("size"),
            "sha1": (f.get("hashes") or {}).get("sha1"),
            "sha512": (f.get("hashes") or {}).get("sha512"),
        }
        items[pid] = item

        # 递归必需前置
        for dep in version.get("dependencies") or []:
            if dep.get("dependency_type") != "required":
                continue
            dep_pid = dep.get("project_id")
            dep_vid = dep.get("version_id")
            dep_slug = None
            if dep_pid:
                try:
                    proj = api_get(f"/project/{dep_pid}")
                    dep_slug = proj.get("slug")
                except Exception:
                    pass
            if not dep_slug:
                warnings.append(f"{slug}: 有个必需前置（project {dep_pid}）拿不到 slug，可能需手动装")
                continue
            item["required_deps"].append(dep_slug)
            visit(dep_slug, f"required-by:{slug}", depth + 1, dep_vid)

    for s in slugs:
        visit(s, "list", 0)
        time.sleep(SLEEP)

    return list(items.values()), warnings, errors


def main() -> int:
    ap = argparse.ArgumentParser(description="按 Modrinth API 下载 mod（含前置解析与 SHA-1 校验）")
    ap.add_argument("--list", choices=sorted(LISTS.keys()), default=None)
    ap.add_argument("--mods", nargs="*", default=None, help="直接给 slug 列表")
    ap.add_argument("--loader", default="neoforge")
    ap.add_argument("--mc", default="1.21.1")
    ap.add_argument("--out", required=True, help="mods 目录（实例的 mods/）")
    ap.add_argument("--dry-run", action="store_true", help="只解析与报告，不下载")
    args = ap.parse_args()

    slugs = LISTS[args.list] if args.list else (args.mods or [])
    if not slugs:
        print("no mods specified: use --list or --mods")
        return 2

    out = Path(args.out).expanduser().resolve()
    print(f"target : {out}")
    print(f"filter : loader={args.loader} mc={args.mc}  mods={len(slugs)}")
    print(f"mode   : {'DRY-RUN' if args.dry_run else 'DOWNLOAD'}")
    print("resolving ...")

    items, warnings, errors = resolve(slugs, args.loader, args.mc)

    total_bytes = sum((i["file"] or {}).get("size") or 0 for i in items)
    print(f"\nresolved {len(items)} files ({total_bytes/1048576:.1f} MiB) "
          f"from {len(slugs)} requested slugs\n")

    rows = [["slug", "version", "file", "MiB", "理由"]]
    for i in sorted(items, key=lambda x: x["reason"]):
        f = i["file"] or {}
        rows.append([i["slug"], str(i["version_number"]), str(f.get("filename")),
                     f"{(f.get('size') or 0)/1048576:.2f}", i["reason"]])
    width = [max(len(r[c]) for r in rows) for c in range(len(rows[0]))]
    for r in rows:
        print("  " + "  ".join(cell.ljust(width[c]) for c, cell in enumerate(r)))

    if warnings:
        print("\nWARNINGS:")
        for w in warnings:
            print("  ! " + w)
    if errors:
        print("\nERRORS:")
        for e in errors:
            print("  x " + e)

    if args.dry_run:
        print("\ndry-run: nothing downloaded")
        return 0 if not errors else 1

    out.mkdir(parents=True, exist_ok=True)
    manifest_path = out / "mods-manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8")) if manifest_path.exists() else {}
    manifest.setdefault("loader", args.loader)
    manifest.setdefault("mc", args.mc)
    manifest.setdefault("installed", {})

    downloaded, skipped, failed = [], [], []
    for i in items:
        f = i["file"] or {}
        fn, url, want = f.get("filename"), f.get("url"), f.get("sha1")
        if not (fn and url):
            failed.append(i["slug"])
            continue
        dest = out / fn
        if dest.exists() and want and sha1_of(dest) == want:
            skipped.append(fn)
            manifest["installed"][i["slug"]] = {**i, "status": "already-present", "at": time.strftime("%Y-%m-%dT%H:%M:%S")}
            continue
        try:
            sha, size = download(url, dest, expect_size=f.get("size"), expect_sha1=want)
            downloaded.append(f"{fn} ({size/1048576:.2f} MiB)")
            manifest["installed"][i["slug"]] = {**i, "status": "downloaded", "sha1_verified": bool(want),
                                                "at": time.strftime("%Y-%m-%dT%H:%M:%S")}
        except Exception as exc:
            failed.append(f"{i['slug']} ({exc})")
        time.sleep(SLEEP)

    manifest["updated_at"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"\ndownloaded {len(downloaded)}  skipped {len(skipped)}  failed {len(failed)}")
    if failed:
        print("FAILED:")
        for x in failed:
            print("  x " + x)
    print(f"manifest: {manifest_path}")
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())

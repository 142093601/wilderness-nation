"""modpack_survey.py -- 从**真实整合包**里挖 mod 共识（含冷门好 mod）。

为什么这条路比"扫类别榜单"强
--------------------------
Modrinth 的一个整合包（`.mrpack`）本质是个 zip，里面 `modrinth.index.json` 就是**完整 mod 清单**：
哪些项目、客户端/服务端哪一侧、哪个版本。于是可以：

    跨 N 个包统计"某个 mod 被多少个包选中" —— 这是**策展者的投票**，不是下载量榜单。

这解决两个具体问题：
  1. **补系统性偏差**：只看类别榜单会漏掉"作者才知道的好东西"（例如 Lootr、拼音搜索）。
  2. **找冷门好 mod**：被多个包选中、但**总下载量不高**的 → 口碑好而非靠量堆的。

还可以按包的主题筛选：中世纪/建国/生存类的共识，比 All the Mods 那种大杂烩更贴本包。

用法
----
    python modpack_survey.py --top 25 --queries "medieval,kingdom,building,survival,rpg"
    python modpack_survey.py --top 40 --md ../data/modpack-consensus.md
"""

from __future__ import annotations

import argparse
import io
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
API = "https://api.modrinth.com/v2"
UA = "nation-pack-modpack-survey/0.1 (personal modpack dev)"
MC, LOADER = "1.21.1", "neoforge"
PROJ_ID_IN_URL = re.compile(r"/data/([A-Za-z0-9]{8,10})/")


def api_get(path: str, params: dict | None = None) -> object:
    url = API + path
    if params:
        url += "?" + urllib.parse.urlencode(params, doseq=True)
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            time.sleep(1.5 * (attempt + 1))
        except Exception:
            time.sleep(1.5 * (attempt + 1))
    return None


def api_post(path: str, body: object) -> object:
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(API + path, data=data, method="POST",
                                headers={"User-Agent": UA, "Content-Type": "application/json"})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=40) as r:
                return json.loads(r.read().decode("utf-8"))
        except Exception:
            time.sleep(1.5 * (attempt + 1))
    return None


def find_packs(top: int, queries: list[str]) -> list[dict]:
    """找 1.21.1 的整合包：下载量前列 + 按主题关键词搜。

    ⚠️ 坑：**不要加 `loaders:neoforge` 筛选**。实测 `modpack + 1.21.1 + neoforge`
    只有 **1 个**结果，而 `modpack + 1.21.1` 有 **4882 个**——因为整合包作者极少标 loaders
    （排前面的还全是 Fabric 包）。真正的加载器信号在 `.mrpack` 的 `dependencies` 里，
    所以先放宽搜、再在解析阶段按 `dependencies` 过滤。
    """
    found: dict[str, dict] = {}

    def take(data: object, tag: str) -> None:
        if not isinstance(data, dict):
            return
        for h in data.get("hits", []):
            slug = h.get("slug")
            if not slug or slug in found:
                continue
            found[slug] = {"slug": slug, "title": h.get("title"),
                           "downloads": h.get("downloads", 0), "tags": [tag]}

    facets = json.dumps([["project_type:modpack"], [f"versions:{MC}"]])
    take(api_get("/search", {"facets": facets, "limit": top, "index": "downloads"}), "top-downloads")
    time.sleep(0.4)
    for q in queries:
        take(api_get("/search", {"query": q, "facets": facets, "limit": 20, "index": "relevance"}), f"query:{q}")
        time.sleep(0.4)
    return sorted(found.values(), key=lambda p: -p["downloads"])


def pack_mod_ids(pack: dict, max_mb: float = 30.0) -> tuple[set[str], str]:
    """下载 .mrpack 取 modrinth.index.json，抽出用到的 project id。

    只在 `dependencies` 里出现 **neoforge** 的包才算数——这是加载器的权威信号。
    """
    vers = api_get(f"/project/{pack['slug']}/version", {"game_versions": json.dumps([MC])})
    if not vers:
        return set(), "无 1.21.1 版本"
    v = vers[0]
    files = v.get("files") or []
    if not files:
        return set(), "版本没有文件"
    url, size = files[0].get("url"), files[0].get("size") or 0
    if not url:
        return set(), "没有下载地址"
    if size > max_mb * 1024 * 1024:
        return set(), f"文件过大（{size/1e6:.1f}MB > {max_mb}MB）"
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=60) as r:
            blob = r.read()
        z = zipfile.ZipFile(io.BytesIO(blob))
        idx = json.loads(z.read("modrinth.index.json").decode("utf-8"))
    except Exception as exc:
        return set(), f"读取失败 {type(exc).__name__}"
    deps = idx.get("dependencies") or {}
    if "neoforge" not in deps:
        return set(), f"不是 NeoForge 包（deps={','.join(deps) or '无'}）"
    pack["neoforge"] = deps.get("neoforge")
    ids: set[str] = set()
    for f in idx.get("files", []):
        path = f.get("path", "")
        if not path.startswith("mods/"):
            continue
        for u in f.get("downloads", []) or []:
            m = PROJ_ID_IN_URL.search(u)
            if m:
                ids.add(m.group(1))
    return ids, f"NeoForge {deps.get('neoforge')} · {len(ids)} 个 mod"


def resolve_ids(ids: list[str]) -> dict[str, dict]:
    """批量把 project id 换成 slug/名称/下载量。

    坑：批量查项目是 **GET /projects?ids=[...]**（JSON 数组做查询参数），
    不是 POST——写成 POST 会静默失败，结果是"解析 0 个项目的元数据"。
    """
    out: dict[str, dict] = {}
    for i in range(0, len(ids), 60):
        chunk = ids[i:i + 60]
        data = api_get("/projects", {"ids": json.dumps(chunk)})
        if isinstance(data, list):
            for p in data:
                out[p["id"]] = {"slug": p.get("slug"), "title": p.get("title"),
                                "downloads": p.get("downloads", 0),
                                "client_side": p.get("client_side"),
                                "server_side": p.get("server_side")}
        time.sleep(0.4)
    return out


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="从真实整合包挖 mod 共识")
    ap.add_argument("--top", type=int, default=25, help="按下载量取前 N 个整合包")
    ap.add_argument("--queries", default="medieval,kingdom,building,survival,rpg,village,colony",
                    help="额外按主题关键词搜整合包")
    ap.add_argument("--packs", default="",
                    help="直接给包 slug（逗号分隔），跳过搜索阶段——用于补跑上次被大小上限跳过的包")
    ap.add_argument("--max-mb", type=float, default=30.0,
                    help="单个 .mrpack 的大小上限（MB）。上次 30MB 误杀了多个 NeoForge 大包，补跑时调高")
    ap.add_argument("--merge", help="把结果并入这个已有的 consensus JSON（累加被选中次数）")
    ap.add_argument("--md")
    ap.add_argument("--json")
    ap.add_argument("--min-packs", type=int, default=2, help="至少被几个包选中才算共识")
    args = ap.parse_args()

    queries = [q.strip() for q in args.queries.split(",") if q.strip()]
    explicit = [s.strip() for s in args.packs.split(",") if s.strip()]
    if explicit:
        packs = [{"slug": s, "title": s, "downloads": 0, "tags": ["explicit"]} for s in explicit]
        print(f"显式指定 {len(packs)} 个包（跳过搜索），大小上限 {args.max_mb}MB")
    else:
        packs = find_packs(args.top, queries)
        print(f"找到 1.21.1+neoforge 整合包 {len(packs)} 个（下载量前列 + {len(queries)} 个主题关键词）")

    usage: Counter[str] = Counter()
    in_packs: dict[str, list[str]] = defaultdict(list)
    ok_packs: list[str] = []
    for i, p in enumerate(packs, 1):
        ids, note = pack_mod_ids(p, args.max_mb)
        print(f"  [{i:>3}/{len(packs)}] {p['slug']:<34} {note}", flush=True)
        if not ids:
            continue
        ok_packs.append(p["slug"])
        for pid in ids:
            usage[pid] += 1
            in_packs[pid].append(p["slug"])
        time.sleep(0.3)

    if not usage:
        print("没有取到任何 mod 清单")
        return 1

    print(f"\n成功解析 {len(ok_packs)} 个包，合计 {len(usage)} 个不同的 mod")
    meta = resolve_ids(sorted(usage, key=lambda k: -usage[k]))
    print(f"已解析 {len(meta)} 个项目的元数据")

    # ── 统一成 slug 键（输出格式如此），便于与基线合并 ──
    usage_by_slug: Counter[str] = Counter()
    meta_by_slug: dict[str, dict] = {}
    for pid, n in usage.items():
        m = meta.get(pid) or {}
        slug = m.get("slug") or pid
        usage_by_slug[slug] += n
        if m:
            meta_by_slug[slug] = m
    if args.merge:
        base_path = Path(args.merge)
        if base_path.is_file():
            bj = json.loads(base_path.read_text(encoding="utf-8"))
            for s, n in (bj.get("usage") or {}).items():
                usage_by_slug[s] += n
            for m in (bj.get("meta") or {}).values():
                s = m.get("slug")
                if s:
                    meta_by_slug.setdefault(s, m)
            ok_packs = list(bj.get("ok_packs") or []) + ok_packs
            print(f"已并入基线 {base_path.name}：现共 {len(ok_packs)} 个包 · {len(usage_by_slug)} 个 mod")

    consensus = [(s, n) for s, n in usage_by_slug.items() if n >= args.min_packs]
    consensus.sort(key=lambda kv: (-kv[1], (meta_by_slug.get(kv[0], {}).get("downloads") or 0)))

    # ── 找"冷门好 mod"：被多个包选中、但下载量不高 ──
    curated: list[tuple[str, int, dict]] = []
    for s, n in consensus:
        m = meta_by_slug.get(s) or {}
        if n >= 2 and (m.get("downloads") or 0) < 6_000_000:
            curated.append((s, n, m))
    curated.sort(key=lambda t: (-t[1], t[2].get("downloads") or 0))

    lines = [
        "# 跨整合包 mod 共识（从真实包挖出来的）\n",
        f"> 数据源：Modrinth 上整合包的 `.mrpack` 内 `modrinth.index.json`",
        f">（成功解析 **{len(ok_packs)} 个 NeoForge 包**）。工具：`tools/modpack_survey.py`，可重跑（支持 `--merge` 增量补跑）。\n",
        f"> 合计 {len(usage_by_slug)} 个不同 mod；**被 ≥{args.min_packs} 个包选中的 {len(consensus)} 个**。\n",
        "## 一、跨包共识（被越多包选中越主流）\n",
        "| mod | 被几个包选中 | 下载量 | 端侧 | slug |",
        "|---|---|---|---|---|",
    ]
    for slug, n in consensus[:300]:
        m = meta_by_slug.get(slug) or {}
        lines.append(f"| {m.get('title','?')} | **{n}** | {m.get('downloads',0):,} | "
                     f"{m.get('client_side')}/{m.get('server_side')} | `{slug}` |")

    lines += ["\n## 二、冷门好 mod（被多个策展包选中，但总下载量不高）\n",
              "> 判据：**被 ≥2 个包选中** 且 **下载量 < 600 万**。这类往往是「作者圈认可、但不靠量堆」的。\n",
              "| mod | 被几个包选中 | 下载量 | 端侧 | slug |",
              "|---|---|---|---|---|"]
    for slug, n, m in curated[:200]:
        lines.append(f"| {m.get('title','?')} | **{n}** | {m.get('downloads',0):,} | "
                     f"{m.get('client_side')}/{m.get('server_side')} | `{slug}` |")

    report = "\n".join(lines)
    if args.md:
        Path(args.md).parent.mkdir(parents=True, exist_ok=True)
        Path(args.md).write_text(report + "\n", encoding="utf-8")
        print(f"已写: {args.md}")
    if args.json:
        Path(args.json).write_text(json.dumps(
            {"packs": [p["slug"] for p in packs], "ok_packs": ok_packs,
             "usage": dict(usage_by_slug), "meta": meta_by_slug},
            ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"已写: {args.json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

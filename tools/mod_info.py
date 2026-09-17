"""mod_info.py -- 拉 Modrinth 项目详情（含正文），用来核实"这个 mod 到底有什么特征"。

为什么不能靠记忆描述 mod
------------------------
"Terralith 和 Biomes O' Plenty 有什么区别""RoadWeaver 兼容哪些群系 mod"这类问题，
靠记忆回答一定会错（版本、依赖、兼容名单都在变）。Modrinth 的 `/project/{slug}` 返回
**作者自己写的正文**，那里通常就是兼容名单与特性清单的原始出处。

用法
----
    python mod_info.py biomes-o-plenty terralith terrablender lithostitched
    python mod_info.py roadweaver --grep "兼容|compat|Terralith|Biomes|不兼容"
    python mod_info.py zombie-awareness --body 2000
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

API = "https://api.modrinth.com/v2"
UA = "nation-pack-mod-info/0.1 (personal modpack dev)"
TARGET_MC = "1.21.1"


def api_get(path: str) -> dict:
    req = urllib.request.Request(API + path, headers={"User-Agent": UA})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return {}
            time.sleep(1.5 * (attempt + 1))
        except Exception:
            time.sleep(1.5 * (attempt + 1))
    return {}


def strip_md(text: str) -> str:
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"[`*_>#]+", "", text)
    return text


def main() -> int:
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")  # type: ignore[attr-defined]
    except Exception:
        pass

    ap = argparse.ArgumentParser(description="拉 Modrinth 项目详情")
    ap.add_argument("slugs", nargs="+")
    ap.add_argument("--body", type=int, default=900, help="正文打印多少字符（0=不打印）")
    ap.add_argument("--grep", help="只打印正文里匹配这个正则的行（含上下文 1 行）")
    ap.add_argument("--json")
    args = ap.parse_args()

    results = []
    for slug in args.slugs:
        p = api_get(f"/project/{slug}")
        if not p:
            print(f"\n### {slug}: 拿不到（不存在或接口失败）")
            continue
        results.append(p)
        print("\n" + "=" * 78)
        print(f"### {p.get('title')}  (`{p.get('slug')}`)")
        print(f"  下载 {p.get('downloads', 0):,} · 关注 {p.get('followers', 0):,} · "
              f"端侧 client={p.get('client_side')} server={p.get('server_side')}")
        print(f"  类目: {', '.join(p.get('categories', []))}")
        print(f"  许可: {p.get('license', {}).get('id')} · 最近更新 {(p.get('updated') or '')[:10]}")
        if p.get("source_url"):
            print(f"  源码: {p['source_url']}")
        print(f"  一句话: {p.get('description', '')}")
        body = strip_md(p.get("body") or "")
        if args.grep:
            pat = re.compile(args.grep, re.I)
            lines = [l.strip() for l in body.splitlines() if l.strip()]
            hits = [(i, l) for i, l in enumerate(lines) if pat.search(l)]
            print(f"\n  ── 正文匹配 '{args.grep}'：{len(hits)} 处 ──")
            for i, l in hits[:40]:
                print(f"    · {l[:200]}")
            if len(hits) > 40:
                print(f"    ... 还有 {len(hits) - 40} 处")
        elif args.body:
            print(f"\n  ── 正文（前 {args.body} 字）──")
            print("  " + body[:args.body].replace("\n", "\n  "))
        time.sleep(0.4)

    if args.json:
        Path(args.json).write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"\n已写: {args.json}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

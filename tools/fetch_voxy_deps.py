#!/usr/bin/env python3
"""为构建 voxy-neoforge 准备本地 Maven 仓库。

为什么需要这个脚本
------------------
本机到 Maven Central 只有 13-20 KB/s，voxy 的构建要下约 85 MB 依赖，
Gradle 自己下载会长时间卡死（实测 10 分钟零进展）。
这个脚本用**多线程**把需要的工件拉进一个项目内的本地 Maven 仓库，
再让 Gradle 把这个目录当作 repository —— 依赖解析就不用碰网络了。

它下载的是 voxy **自己运行时真正要用**的库（不是可选依赖）：
  rocksdbjni   voxy 的默认 LOD 存储后端
  jedis        分布式存储后端
  commons-pool2  jedis 的传递依赖
  xz           voxy 的 Distant Horizons 导入支持
  sqlite-jdbc  SQLite 存储后端
  lwjgl-lmdb / lwjgl-zstd (+ natives)  LOD 数据库与压缩，MC 不提供

用法：
    python tools/fetch_voxy_deps.py              # 下载到 data/_voxym2
    python tools/fetch_voxy_deps.py --jobs 8     # 并发数
    python tools/fetch_voxy_deps.py --check      # 只检查已有，不下载

下载完成后，构建前先注入本地仓库：
    python tools/fetch_voxy_deps.py --init-gradle <voxy 源码目录>
它会写一个 init.gradle 并在构建时用 `--init-script` 加载。
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import os
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

# 仓库根 = 本脚本所在仓库（不是 cwd —— cwd 可能是 D:\project，那是另一个 git 仓库）
REPO_ROOT = Path(__file__).resolve().parent.parent
M2_DIR = REPO_ROOT / "data" / "_voxym2"

# 多个镜像，按速度测出来的顺序。哪一个先成功就用哪一个。
MIRRORS = [
    "https://maven.aliyun.com/repository/central/",
    "https://repo1.maven.org/maven2/",
    "https://maven.neoforged.net/releases/",
]

# 需要的工件（Maven 坐标 -> 相对路径由 group/artifact/version 推出）
ARTIFACTS = [
    # (group, artifact, version, classifier)
    ("org.rocksdb", "rocksdbjni", "10.2.1", None),
    ("redis.clients", "jedis", "5.1.0", None),
    ("org.apache.commons", "commons-pool2", "2.12.0", None),
    ("org.tukaani", "xz", "1.10", None),
    ("org.xerial", "sqlite-jdbc", "3.49.1.0", None),
    ("org.lwjgl", "lwjgl-bom", "3.3.3", None),
    ("org.lwjgl", "lwjgl-lmdb", "3.3.3", None),
    ("org.lwjgl", "lwjgl-zstd", "3.3.3", None),
    ("org.lwjgl", "lwjgl-lmdb", "3.3.3", "natives-windows"),
    ("org.lwjgl", "lwjgl-zstd", "3.3.3", "natives-windows"),
    # build.gradle 的 extractLwjglNatives 同时要 windows 与 linux 两套原生库
    # （跨平台打包）。只备 windows 会报 "Could not find lwjgl-lmdb-3.3.3-natives-linux.jar"。
    ("org.lwjgl", "lwjgl-lmdb", "3.3.3", "natives-linux"),
    ("org.lwjgl", "lwjgl-zstd", "3.3.3", "natives-linux"),
    # jedis 的传递依赖。Gradle 解析 POM 时需要它在线（离线模式会报
    # "No cached version of org.json:json:20231013 available for offline mode"）。
    # 逐个按"构建报缺什么"补，而不是用 --closure 递归 —— 递归会把 lwjgl-bom
    # 里的全部 60 个模块和 commons-parent 拖进的 Maven 插件（doxia 等）都拉下来，
    # 远超实际需要（我用 --closure 时下了 60 个坐标，其中绝大多数用不上）。
    ("org.json", "json", "20231013", None),
]

# 直接下 jar（不在 Maven 仓库里，从 Modrinth CDN 取）。
# Iris 只用于**编译**：voxy 的源码 import 了 net.irisshaders.iris.* ，
# 编译时必须有这些类；运行时没有 Iris 也能跑，因为 VoxyMixinPlugin
# 按"类是否在场"决定是否应用 Iris mixin（不在场就全部跳过）。
# 这也是仓库 build.gradle 原本的意图：它引用 .reference/iris/iris-neoforge.jar。
DIRECT_JARS = [
    # URL 取自 Modrinth API（project iris, version id t3ruzodq），不要手写猜 hash。
    ("iris-neoforge.jar",
     "https://cdn.modrinth.com/data/YL57xq9U/versions/t3ruzodq/iris-neoforge-1.8.12%2Bmc1.21.1.jar",
     2438548),
]
IRIS_DEST = REPO_ROOT / "data" / "_voxybuild" / ".reference" / "iris"


def rel_path(group: str, artifact: str, version: str, classifier: str | None, ext: str) -> str:
    g = group.replace(".", "/")
    name = f"{artifact}-{version}"
    if classifier:
        name += f"-{classifier}"
    return f"{g}/{artifact}/{version}/{name}.{ext}"


def http_get(url: str, timeout: int = 60) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "nation-pack/voxy-fetch"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def fetch_one(group: str, artifact: str, version: str, classifier: str | None) -> tuple[str, str, int]:
    """返回 (状态, 描述, 字节数)。状态 in {ok, cached, fail}

    注意：**jar 与 pom 都要下**。Gradle 解析依赖时要读 POM（拿传递依赖与 parent），
    只有 jar 没有 pom 会报 "Could not parse POM ... / No cached version of ..."。
    所以这里统一委托给 _fetch_artifact，jar 与 pom 各下一份。
    踩过的坑：旧版本里"jar 已存在就 return"，导致 pom 永远没被补下，
    表现为 `--check` 说 OK 但 gradle 说找不到（json-20231013 就是这样漏的）。
    """
    ext = "pom" if artifact.endswith("-bom") else "jar"
    label = f"{group}:{artifact}:{version}" + (f":{classifier}" if classifier else "")

    # 带分类器的构件（如 natives-windows）**没有自己的 POM** —— Maven 约定是
    # 读基础构件的 POM。所以只下 jar，别去要 `xxx-natives-windows.pom`（不存在，
    # 会误报 "pom 下载失败"）。
    if classifier:
        jar_status, _, jar_size = _fetch_artifact(group, artifact, version, classifier, "jar")
        return (jar_status, label, jar_size)

    # pom 先行：即使 jar 已缓存，也要确保 pom 在
    pom_status, _, _ = _fetch_artifact(group, artifact, version, classifier, "pom")
    if ext == "pom":
        pom_file = M2_DIR / rel_path(group, artifact, version, classifier, "pom")
        return (pom_status, label, pom_file.stat().st_size if pom_file.exists() else 0)

    jar_status, _, jar_size = _fetch_artifact(group, artifact, version, classifier, "jar")
    if jar_status == "fail":
        return (jar_status, label, 0)
    if pom_status == "fail":
        # pom 失败要报出来：否则 gradle 后面会以"找不到依赖"的形式再报一次
        return ("fail", f"{label} (pom 下载失败)", 0)
    return (jar_status, label, jar_size)


def write_maven_metadata(group: str, artifact: str, versions: list[str]) -> None:
    """生成 maven-metadata.xml。

    为什么必须生成：voxy 的 build.gradle 对 xz / sqlite-jdbc 用的是**版本范围**
    （`strictly '[1.9, 2.0)'`），Gradle 解析范围时要先读 maven-metadata.xml
    才能知道"范围内有哪些版本可用"。只有 jar/pom 而没有 metadata 时，
    离线模式会报 "No cached version listing ... available for offline mode"，
    即使那个版本的 jar 明明就在仓库里。
    """
    g = group.replace(".", "/")
    d = M2_DIR / g / artifact
    d.mkdir(parents=True, exist_ok=True)
    latest = versions[-1]
    xml = (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        "<metadata>\n"
        f"  <groupId>{group}</groupId>\n"
        f"  <artifactId>{artifact}</artifactId>\n"
        "  <versioning>\n"
        f"    <latest>{latest}</latest>\n"
        f"    <release>{latest}</release>\n"
        "    <versions>\n"
        + "".join(f"      <version>{v}</version>\n" for v in versions)
        + "    </versions>\n"
        "    <lastUpdated>20260921000000</lastUpdated>\n"
        "  </versioning>\n"
        "</metadata>\n"
    )
    (d / "maven-metadata.xml").write_text(xml, encoding="utf-8", newline="\n")


def ensure_metadata() -> int:
    """为所有工件所属的 groupId:artifactId 生成 maven-metadata.xml。"""
    by_artifact: dict[tuple[str, str], list[str]] = {}
    for g, a, v, _c in ARTIFACTS:
        by_artifact.setdefault((g, a), []).append(v)
    n = 0
    for (g, a), vs in by_artifact.items():
        # 只有本地确实有 jar/pom 的才写 metadata，避免凭空声明不存在的版本
        have = []
        for v in vs:
            jar = M2_DIR / rel_path(g, a, v, None, "jar")
            pom = M2_DIR / rel_path(g, a, v, None, "pom")
            if jar.exists() or pom.exists():
                have.append(v)
        if have:
            write_maven_metadata(g, a, sorted(have))
            n += 1
    return n


def fetch_direct_jars() -> list[tuple[str, str, int]]:
    """下载不在 Maven 仓库里的 jar（Iris）。"""
    IRIS_DEST.mkdir(parents=True, exist_ok=True)
    results = []
    for name, url, expect in DIRECT_JARS:
        dst = IRIS_DEST / name
        if dst.exists() and dst.stat().st_size == expect:
            results.append(("cached", name, dst.stat().st_size))
            continue
        last = None
        got = False
        for mirror in [url, url.replace("cdn.modrinth.com", "cdn-raw.modrinth.com")]:
            try:
                data = http_get(mirror, timeout=180)
                if not data:
                    last = "空内容"
                    continue
                tmp = dst.with_suffix(".part")
                tmp.write_bytes(data)
                tmp.replace(dst)
                results.append(("ok", name, len(data)))
                got = True
                break
            except Exception as e:
                last = f"{type(e).__name__}"
        if not got:
            results.append(("fail", f"{name} ({last})", 0))
    return results


def parse_pom(xml: str) -> dict:
    """从 POM 里取出 parent 与 compile/runtime 作用域的依赖。"""
    def tag(block: str, name: str) -> str | None:
        m = re.search(rf"<{name}>(.*?)</{name}>", block, re.S)
        return m.group(1).strip() if m else None

    parent = None
    pm = re.search(r"<parent>(.*?)</parent>", xml, re.S)
    if pm:
        b = pm.group(1)
        parent = (tag(b, "groupId"), tag(b, "artifactId"), tag(b, "version"))

    deps = []
    # 只看 <dependencies>（不是 <dependencyManagement>）
    dm = re.search(r"<dependencies>(.*?)</dependencies>", xml, re.S)
    if dm:
        for d in re.findall(r"<dependency>(.*?)</dependency>", dm.group(1), re.S):
            g, a, v = tag(d, "groupId"), tag(d, "artifactId"), tag(d, "version")
            scope = tag(d, "scope") or "compile"
            optional = (tag(d, "optional") or "false").lower() == "true"
            if scope not in ("compile", "runtime"):
                continue          # test / provided 不进闭包
            if optional:
                continue
            if g and a:
                deps.append((g, a, v))
    return {"parent": parent, "deps": deps}


def fetch_coordinates(coords: dict[tuple[str, str, str], None], jobs: int) -> int:
    """把一批坐标下到本地 M2（jar + pom）。"""
    items = list(coords.keys())
    if not items:
        return 0
    n_ok = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as ex:
        futs = []
        for g, a, v in items:
            ext = "pom" if a.endswith("-bom") or a.endswith("-parent") else "jar"
            futs.append(ex.submit(_fetch_artifact, g, a, v, None, ext))
        for f in concurrent.futures.as_completed(futs):
            status, label, size = f.result()
            if status in ("ok", "cached"):
                n_ok += 1
            else:
                print(f"     [warn] {label}")
    return n_ok


def _fetch_artifact(group, artifact, version, classifier, ext) -> tuple[str, str, int]:
    """下单个工件（jar 或 pom）到本地 M2。"""
    rel = rel_path(group, artifact, version, classifier, ext)
    dst = M2_DIR / rel
    label = f"{group}:{artifact}:{version}"
    if dst.exists() and dst.stat().st_size > 0:
        return ("cached", label, dst.stat().st_size)
    dst.parent.mkdir(parents=True, exist_ok=True)
    last_err = None
    for mirror in MIRRORS:
        try:
            data = http_get(mirror + rel, timeout=90)
            if not data:
                last_err = f"{mirror} 空内容"
                continue
            tmp = dst.with_suffix(dst.suffix + ".part")
            tmp.write_bytes(data)
            tmp.replace(dst)
            return ("ok", label, len(data))
        except urllib.error.HTTPError as e:
            last_err = f"HTTP {e.code}"
        except Exception as e:
            last_err = type(e).__name__
    return ("fail", f"{label} ({last_err})", 0)


def resolve_closure(jobs: int = 6) -> int:
    """从 ARTIFACTS 出发，递归解析 parent POM 与传递依赖，全部下到本地 M2。

    为什么需要：voxy 用 `jarJar(implementation('redis.clients:jedis'))` 这类声明，
    Gradle 解析 POM 时会连带要求 **parent POM**（如 `commons-pool2` 要
    `org.apache.commons:commons-parent:62`）和**传递依赖**（jedis 要 org.json / slf4j）。
    离线模式缺任何一环都会报 "Could not parse POM ... No cached version of ..."。
    手工列清单会漏（我漏过两次），所以改成按 POM 递归。
    """
    seen: set[tuple[str, str, str]] = set()
    queue: list[tuple[str, str, str]] = []
    for g, a, v, _c in ARTIFACTS:
        queue.append((g, a, v))

    # 无版本号的传递依赖，用这个表补（从真实 POM 里读出来的）
    managed = {
        ("org.slf4j", "slf4j-api"): "1.7.36",
        ("com.google.code.gson", "gson"): "2.10.1",
    }

    rounds = 0
    while queue and rounds < 8:
        rounds += 1
        batch = [c for c in queue if c not in seen]
        queue = []
        if not batch:
            break
        print(f"  解析第 {rounds} 轮：{len(batch)} 个坐标")
        fetch_coordinates({c: None for c in batch}, jobs)
        for g, a, v in batch:
            seen.add((g, a, v))
            pom = M2_DIR / rel_path(g, a, v, None, "pom")
            if not pom.exists():
                print(f"     [warn] 缺 POM，无法解析传递依赖: {g}:{a}:{v}")
                continue
            try:
                info = parse_pom(pom.read_text(encoding="utf-8", errors="replace"))
            except Exception as e:
                # 不静默吞异常：之前这里 except 后 continue，把 "NameError: re 未定义"
                # 变成了"闭包解析跑完了但什么都没做"，排查了半天。
                print(f"     [warn] 解析 POM 失败 {g}:{a}:{v} -> {type(e).__name__}: {e}")
                continue
            # parent POM
            par = info["parent"]
            if par and all(par) and par not in seen:
                queue.append(par)  # type: ignore[arg-type]
            # 传递依赖
            for dg, da, dv in info["deps"]:
                if not dv:
                    dv = managed.get((dg, da))
                    if not dv:
                        continue
                if (dg, da, dv) not in seen:
                    queue.append((dg, da, dv))

    # parent 可能没有 jar（只是 POM），补下 pom 即可
    for g, a, v in list(seen):
        fetch_coordinates({(g, a, v): None}, jobs)
    return len(seen)


def write_init_gradle(target_dir: Path) -> Path:
    """生成 init.gradle：把本地仓库插到最前面（不改上游源码）。"""
    init = target_dir / "init-local-m2.gradle"
    init.write_text(
        "// 由 tools/fetch_voxy_deps.py 生成——把本地 Maven 仓库插到解析顺序最前，\n"
        "// 这样 gradle 不用联网就能拿到 voxy 的依赖（本机到 Maven Central 只有 ~15 KB/s）。\n"
        "allprojects {\n"
        "    repositories {\n"
        f"        maven {{ url = uri('{(M2_DIR).as_posix()}') ; allowInsecureProtocol = false }}\n"
        "    }\n"
        "    buildscript {\n"
        "        repositories {\n"
        f"            maven {{ url = uri('{(M2_DIR).as_posix()}') ; allowInsecureProtocol = false }}\n"
        "        }\n"
        "    }\n"
        "}\n"
        "settingsEvaluated { settings ->\n"
        "    settings.pluginManagement {\n"
        "        repositories {\n"
        f"            maven {{ url = uri('{(M2_DIR).as_posix()}') ; allowInsecureProtocol = false }}\n"
        "        }\n"
        "    }\n"
        "}\n",
        encoding="utf-8",
        newline="\n",
    )
    return init


def main() -> int:
    ap = argparse.ArgumentParser(description="为 voxy 构建准备本地 Maven 仓库")
    ap.add_argument("--jobs", type=int, default=6, help="并发下载数，默认 6")
    ap.add_argument("--check", action="store_true", help="只报告状态，不下载")
    ap.add_argument("--jars-only", action="store_true",
                    help="只下直接引用的 jar（Iris），跳过 Maven 工件")
    ap.add_argument("--closure", action="store_true",
                    help="递归解析 parent POM 与传递依赖（Gradle 解析 POM 时需要）")
    ap.add_argument("--init-gradle", metavar="DIR", default=None,
                    help="在该目录生成 init-local-m2.gradle")
    args = ap.parse_args()

    print(f"仓库根:   {REPO_ROOT}")
    print(f"本地 M2:  {M2_DIR}")
    print(f"工件数:   {len(ARTIFACTS)}\n")

    if args.init_gradle:
        d = Path(args.init_gradle).resolve()
        if not d.is_dir():
            print(f"[FAIL] 目录不存在: {d}", file=sys.stderr)
            return 2
        n = ensure_metadata()
        print(f"已生成 {n} 份 maven-metadata.xml（版本范围解析需要，见函数注释）")
        init = write_init_gradle(d)
        print(f"已生成: {init}")
        print(f"\n构建时这样用：")
        print(f'  gradlew build --init-script "{init}"')
        return 0

    if args.check:
        for g, a, v, c in ARTIFACTS:
            ext = "pom" if a.endswith("-bom") else "jar"
            dst = M2_DIR / rel_path(g, a, v, c, ext)
            state = f"{dst.stat().st_size/1024/1024:.2f} MB" if dst.exists() else "MISSING"
            print(f"  {'OK ' if dst.exists() else '!! '} {g}:{a}:{v}{':'+c if c else '':<18} {state}")
        for name, _u, expect in DIRECT_JARS:
            dst = IRIS_DEST / name
            ok = dst.exists() and dst.stat().st_size == expect
            state = f"{dst.stat().st_size/1024/1024:.2f} MB" if dst.exists() else "MISSING"
            print(f"  {'OK ' if ok else '!! '} {name:<34} {state}")
        return 0

    if args.jars_only:
        print("下载直接引用的 jar：")
        rc = 0
        for status, label, size in fetch_direct_jars():
            mark = {"ok": "OK  ", "cached": "==  ", "fail": "FAIL"}[status]
            print(f"  [{mark}] {label:<40} {size/1024/1024:>7.2f} MB")
            if status == "fail":
                rc = 1
        return rc

    if args.closure:
        print("递归解析依赖闭包（parent POM + 传递依赖）：")
        n = resolve_closure(args.jobs)
        print(f"\n共处理 {n} 个坐标。生成 metadata …")
        m = ensure_metadata()
        print(f"已生成 {m} 份 maven-metadata.xml")
        print("\n下载直接引用的 jar：")
        for status, label, size in fetch_direct_jars():
            mark = {"ok": "OK  ", "cached": "==  ", "fail": "FAIL"}[status]
            print(f"  [{mark}] {label:<40} {size/1024/1024:>7.2f} MB")
        return 0

    t0 = time.time()
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as ex:
        futs = {ex.submit(fetch_one, *item): item for item in ARTIFACTS}
        for fut in concurrent.futures.as_completed(futs):
            status, label, size = fut.result()
            mark = {"ok": "OK  ", "cached": "==  ", "fail": "FAIL"}[status]
            print(f"  [{mark}] {label:<52} {size/1024/1024:>7.2f} MB")
            results.append((status, label, size))

    dt = time.time() - t0
    ok = sum(1 for s, _, _ in results if s == "ok")
    cached = sum(1 for s, _, _ in results if s == "cached")
    failed = [l for s, l, _ in results if s == "fail"]
    total = sum(sz for _, _, sz in results)

    print(f"\n完成: 新下 {ok} / 已有 {cached} / 失败 {len(failed)}   "
          f"共 {total/1024/1024:.1f} MB，用时 {dt:.0f}s")
    if failed:
        print("\n以下失败，需要重跑或换网络：")
        for l in failed:
            print("   ", l)
        return 1

    n = ensure_metadata()
    print(f"已生成 {n} 份 maven-metadata.xml（版本范围解析需要）")

    print("\n下载直接引用的 jar（Iris，仅编译期需要）：")
    for status, label, size in fetch_direct_jars():
        mark = {"ok": "OK  ", "cached": "==  ", "fail": "FAIL"}[status]
        print(f"  [{mark}] {label:<40} {size/1024/1024:>7.2f} MB")

    print(f"\n下一步生成 init.gradle 并构建：")
    print(f'  python tools/fetch_voxy_deps.py --init-gradle <voxy 源码目录>')
    return 0


if __name__ == "__main__":
    sys.exit(main())

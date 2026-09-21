#!/usr/bin/env python3
"""构建 voxy-neoforge（NeoForge 1.21.1 非官方移植）的编排脚本。

背景
----
本机到境外 Maven 镜像只有 13-20 KB/s，Gradle 自己解析依赖会长时间卡死。
依赖分两类，分别用不同办法解决：

1. **voxy 自己的库**（rocksdb / jedis / xz / sqlite-jdbc / lwjgl-lmdb / lwjgl-zstd）
   —— `tools/fetch_voxy_deps.py` 多线程下到 `data/_voxym2`，构建时用
   `--init-script` 把该目录插进解析顺序最前。
2. **embeddium / lithium**（走 `api.modrinth.com/maven`）
   —— 这两个 **本机已经有 jar**（整合包实例里的 embeddium、包里的 lithium），
   所以本脚本把它们镜像成 `files(...)` 依赖，**不需要联网**。

被判定的关键事实：embeddium 在 voxy 的 `neoforge.mods.toml` 里是
`type="optional"`，构建时只需要它的类做编译，运行时由整合包提供。
所以用本地 jar 替换 Modrinth 坐标不影响产物正确性。

用法：
    python tools/build_voxy.py                 # 完整：镜像源码 -> 打补丁 -> 构建
    python tools/build_voxy.py --patch-only    # 只生成补丁后的源码树
    python tools/build_voxy.py --src <dir>     # 指定已有的源码目录（默认 data/_voxybuild/voxy-neoforge）
"""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SRC = REPO_ROOT / "data" / "_voxybuild" / "voxy-neoforge"
WORK = REPO_ROOT / "data" / "_voxybuild" / "work"
M2 = REPO_ROOT / "data" / "_voxym2"
LIBS = REPO_ROOT / "data" / "_voxybuild" / "libs"

JDK21 = "C:/Program Files/Java/jdk-21.0.10"   # 正斜杠：gradle.properties 是 Java properties，
                                              # 反斜杠会被当转义符吃掉（实测变成 "C:Program FilesJavajdk-21.0.10"）
JDK21_ENV = r"C:\Program Files\Java\jdk-21.0.10"  # 给 JAVA_HOME 环境变量用，反斜杠正确

# 真 Python 解释器（本机 `python3` 是 Windows 商店占位 stub，会跳向不存在的 Python313）。
# 用**正斜杠**：Groovy 单引号字符串不允许反斜杠转义（实测报 "Unexpected character: '\''"）。
PYTHON = "C:/Users/87335/AppData/Local/Programs/Python/Python311/python.exe"
GRADLE_DIST = "gradle-8.12-all.zip"          # 本地已完整缓存的 Gradle

# 本地 jar 的来源（两个都在本机，无需下载）
LOCAL_JARS = {
    "embeddium-1.0.15+mc1.21.1.jar": Path(
        r"D:\game\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250\mods\embeddium-1.0.15+mc1.21.1.jar"
    ),
    "lithium-neoforge-0.15.4+mc1.21.1.jar": Path(
        r"D:\game\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250\mods\lithium-neoforge-0.15.4+mc1.21.1.jar"
    ),
}


def log(msg: str) -> None:
    print(msg, flush=True)


def stage_sources(src: Path) -> Path:
    """把源码树复制到 work/，忽略 .git / build / .gradle。"""
    if not (src / "build.gradle").is_file():
        raise SystemExit(f"[FAIL] 找不到 build.gradle，源码目录不对: {src}")
    if WORK.exists():
        shutil.rmtree(WORK, ignore_errors=True)
    shutil.copytree(
        src, WORK,
        ignore=shutil.ignore_patterns(".git", "build", ".gradle", ".reference"),
        dirs_exist_ok=False,
    )
    log(f"[1/5] 源码已复制到 {WORK}")
    return WORK


def mirror_local_jars() -> dict[str, Path]:
    """把本机已有的 embeddium / lithium jar 放到 `_voxym2/local/` 供 files(...) 引用。"""
    out_dir = M2 / "local"
    out_dir.mkdir(parents=True, exist_ok=True)
    got: dict[str, Path] = {}
    for name, origin in LOCAL_JARS.items():
        if not origin.is_file():
            log(f"[warn] 本地 jar 不存在，跳过: {origin}")
            continue
        dst = out_dir / name
        if not dst.exists() or dst.stat().st_size != origin.stat().st_size:
            shutil.copy2(origin, dst)
        got[name] = dst
        log(f"[2/5] 镜像本地依赖 {name}  ({dst.stat().st_size/1024/1024:.2f} MB)  <- {origin}")
    if not got:
        log("[warn] 没有任何本地依赖被镜像；构建大概率会失败")
    return got


def patch_build_gradle(tree: Path, jars: dict[str, Path]) -> None:
    """把 Modrinth 坐标换成 files(...)，并把 Gradle 发行版指到本地已有的。"""
    bg = tree / "build.gradle"
    text = bg.read_text(encoding="utf-8")
    original = text

    def rel(p: Path) -> str:
        return p.relative_to(tree).as_posix()

    # embeddium（compileOnly）：换成本地 jar。
    # 用**绝对路径**：构建树在 `data/_voxybuild/work/`，而本地仓库在 `data/_voxym2/`，
    # 相对路径要写 `../../../`——数错层级的后果是"类路径静默为空"，
    # 表现为一片 "找不到符号"（我踩过一次：写成 '../_voxym2/' 少了三层）。
    emb = next((n for n in jars if n.startswith("embeddium")), None)
    if emb:
        abs_emb = (M2 / "local" / emb).as_posix()
        text = text.replace(
            'compileOnly "maven.modrinth:embeddium:${project.embeddium_version}"',
            f"compileOnly files('{abs_emb}')",
        )
    else:
        log("[warn] 未找到 embeddium jar，Modrinth 坐标未替换 —— 构建会尝试联网")

    # lithium（implementation）：同理用绝对路径
    lit = next((n for n in jars if n.startswith("lithium")), None)
    if lit:
        abs_lit = (M2 / "local" / lit).as_posix()
        text = re.sub(
            r'implementation\("maven\.modrinth:lithium:[^"]+"\)',
            lambda m: f"implementation files('{abs_lit}')",
            text,
        )
    else:
        log("[warn] 未找到 lithium jar，Modrinth 坐标未替换 —— 构建会尝试联网")

    # Iris 的 compileOnly files(...) 原本指向 `${rootProject.projectDir}/.reference/iris/iris-neoforge.jar`，
    # 而我们把 Iris 放在了 `data/_voxybuild/.reference/iris/iris-neoforge.jar`（不在项目根下）。
    #
    # 为什么需要 Iris：voxy 的源码 import 了 `net.irisshaders.iris.*`（着色器集成），
    # **编译期**必须有这些类。运行时没有 Iris 也能跑 —— `VoxyMixinPlugin.shouldApplyMixin()`
    # 按"类是否在场"决定是否应用 Iris mixin，不在场就全部跳过（我核对过源码）。
    # 所以这是**仅编译期**依赖，不需要发给玩家。
    # 注意：Groovy 单引号串里反斜杠非法，必须用正斜杠。
    iris_jar = (REPO_ROOT / "data" / "_voxybuild" / ".reference" / "iris" / "iris-neoforge.jar").as_posix()
    t_iris = re.sub(
        r'compileOnly files\("\$\{rootProject\.projectDir\}/\.reference/iris/iris-neoforge\.jar"\)',
        lambda m: f"compileOnly files('{iris_jar}')  // [build_voxy.py] 编译期需要 Iris 的类；运行时不需要",
        text, flags=re.M,
    )
    text = t_iris

    if text == original:
        log("[3/5] build.gradle 无需改动（可能已打过补丁）")
    else:
        bg.write_text(text, encoding="utf-8", newline="\n")
        log("[3/5] build.gradle 已打补丁：embeddium/lithium 改为本地 files(...)，Iris 引用禁用")

    # Gradle wrapper 指到本地已有的发行版
    wp = tree / "gradle" / "wrapper" / "gradle-wrapper.properties"
    if wp.is_file():
        w = wp.read_text(encoding="utf-8")
        w2 = re.sub(r"gradle-[\d.]+(?:-all|-bin)\.zip", lambda m: GRADLE_DIST, w)
        if w2 != w:
            wp.write_text(w2, encoding="utf-8", newline="\n")
            log(f"[3/5] gradle wrapper 指向 {GRADLE_DIST}（本地已缓存）")

    # gradle.properties：JDK 路径 + NeoForge 版本用本地已有的 21.1.250
    gp = tree / "gradle.properties"
    if gp.is_file():
        g = gp.read_text(encoding="utf-8")
        g = re.sub(r"^org\.gradle\.java\.home=.*$", lambda m: f"org.gradle.java.home={JDK21}", g, flags=re.M)
        g = re.sub(r"^neoforge_version=.*$", lambda m: "neoforge_version=21.1.250", g, flags=re.M)
        gp.write_text(g, encoding="utf-8", newline="\n")
        log("[3/5] gradle.properties：java.home / neoforge_version=21.1.250 已设置")
    else:
        log("[3/5] gradle.properties 不存在（build.gradle 已改，继续）")

    # ModDevGradle 版本：2.0.42-beta 要 neoform-runtime:1.0.6（本地没有），
    # 而本地已有 2.0.147 + neoform-runtime:2.0.31（是 mods/statecraft 编译时留下的）。
    # 两者 `neoForge {}` 配置语法兼容（version / accessTransformers / parchment 都在），
    # 所以升到本地已有版本以复用缓存、避免再下 ~100 MB。
    bg2 = tree / "build.gradle"
    t2 = bg2.read_text(encoding="utf-8")
    t3 = re.sub(
        r"id\s+'net\.neoforged\.moddev'\s+version\s+['\"][^'\"]+['\"]",
        lambda m: "id 'net.neoforged.moddev' version '2.0.147'",
        t2,
    )
    if t3 != t2:
        bg2.write_text(t3, encoding="utf-8", newline="\n")
        log("[3/5] ModDevGradle -> 2.0.147（本地已有 neoform-runtime:2.0.31）")

    # Parchment = 参数名映射，只对"在 IDE 里读 MC 源码"有意义，编译产物不需要它。
    # 去掉可以省一次 ~10 MB 的下载（本机到境外源 15 KB/s）。statecraft 当初也是
    # 用 disableRecompilation = true 跳过整条反编译链的。
    t4 = bg2.read_text(encoding="utf-8")
    t5 = re.sub(
        r"\n\s*parchment\s*\{[^}]*\}\n",
        "\n    // [build_voxy.py] parchment 已移除：参数名映射对编译产物无用，省一次下载\n",
        t4,
    )
    if t5 != t4:
        bg2.write_text(t5, encoding="utf-8", newline="\n")
        log("[3/5] 已移除 parchment 块（省下载）")

    # 两个 mixin 校验任务写死了 `python3`。本机 `python3` 解析到 Windows 商店的
    # 占位 stub（WindowsApps\python3.exe），它跳向不存在的 Python313，
    # 于是报 "Failed to launch ... (0x80070003)"。真解释器是 Python311。
    # 改成绝对路径 —— 这两个脚本要留着：它们独立校验 @Inject/@Shadow 目标
    # （就是我手工用 javap 核对的那类东西），是比人工更硬的证据。
    #
    # 另外加 `-X utf8`：脚本会打印 `✓`(U+2713)，而 Windows 控制台默认 GBK，
    # 实测报 "UnicodeEncodeError: 'gbk' codec can't encode character '\u2713'"。
    # UTF-8 模式直接绕开这个问题，不用改上游脚本。
    t6 = bg2.read_text(encoding="utf-8")
    t7 = t6.replace("commandLine 'python3',", f"commandLine '{PYTHON}', '-X', 'utf8',")
    if t7 != t6:
        bg2.write_text(t7, encoding="utf-8", newline="\n")
        log(f"[3/5] 校验任务的 python3 -> {PYTHON} -X utf8（避开 GBK 编码崩溃）")

    # 去掉我们用不到的可选后端，收窄依赖闭包。
    #
    # 为什么可以去掉（这不是"为了编译方便"的借口，是三个功能我们确实不用）：
    #   jedis        —— Redis 分布式 LOD 存储。我们是单服务端，不需要分布式存储。
    #    xz          —— voxy 的 **Distant Horizons 导入**支持。我们不用 DH，用不上。
    #    sqlite-jdbc —— 另一种存储后端。默认后端是 RocksDB，够用。
    # 保留：rocksdbjni（默认存储后端，必需）、lwjgl-lmdb / lwjgl-zstd（MC 不提供）。
    #
    # 顺带解决的实际问题：jedis 会拖进 org.json / slf4j / gson，commons-pool2 的 POM
    # 又要求 parent `org.apache.commons:commons-parent:62`，离线模式全部解析不了。
    # 去掉这三项后，闭包只剩 rocksdbjni + lwjgl，全部已在本地仓库里。
    t8 = bg2.read_text(encoding="utf-8")
    _ = t8  # 保留：下面不再裁剪依赖，见下方注释
    # ⚠️ 我一度在这里裁掉 jedis / xz / sqlite-jdbc，理由是"单服务端 / 不用 DH 导入"。
    # **那是错的**：voxy 的**源码本身就 import 它们**
    #   common/config/storage/redis/RedisStorageBackend.java → redis.clients.jedis
    #   commonImpl/importers/DHImporter.java                 → org.tukaani.xz
    # 删掉依赖后编译报 19 个 "程序包 redis.clients.jedis 不存在 / org.tukaani.xz 不存在"。
    # 它们是**编译期必需**，不是可有可无的可选功能。教训：判断"能不能删"要看源码 import，
    # 不能看功能描述。（改用 tools/fetch_voxy_deps.py 把依赖闭包解全。）


def write_init_gradle(tree: Path) -> Path:
    """把本地 Maven 仓库插到解析顺序最前（并行 patch：也覆盖 init 的老内容）。"""
    init = tree / "init-local-m2.gradle"
    url = M2.as_posix()
    init.write_text(
        "// 由 tools/build_voxy.py 生成：本地 Maven 仓库优先，避免境外镜像的慢速下载。\n"
        "allprojects {\n"
        "    repositories {\n"
        f"        maven {{ url = uri('{url}') }}\n"
        "    }\n"
        "    buildscript {\n"
        "        repositories {\n"
        f"            maven {{ url = uri('{url}') }}\n"
        "        }\n"
        "    }\n"
        "}\n"
        "settingsEvaluated { settings ->\n"
        "    settings.pluginManagement {\n"
        "        repositories {\n"
        f"            maven {{ url = uri('{url}') }}\n"
        "        }\n"
        "    }\n"
        "}\n",
        encoding="utf-8", newline="\n",
    )
    return init


def main() -> int:
    ap = argparse.ArgumentParser(description="构建 voxy-neoforge（本地依赖，不联网）")
    ap.add_argument("--src", default=str(DEFAULT_SRC), help="源码目录")
    ap.add_argument("--patch-only", action="store_true", help="只生成补丁后的源码树，不构建")
    args = ap.parse_args()

    src = Path(args.src)
    log(f"源码:     {src}")
    log(f"工作树:   {WORK}")
    log(f"本地 M2:  {M2}\n")

    if not M2.is_dir():
        log(f"[FAIL] 本地 Maven 仓库不存在，先跑：python tools/fetch_voxy_deps.py")
        return 2

    tree = stage_sources(src)
    jars = mirror_local_jars()
    patch_build_gradle(tree, jars)
    init = write_init_gradle(tree)
    log(f"[4/5] 已生成 {init.name}")

    if args.patch_only:
        log("\n--patch-only：只打补丁，不构建。")
        return 0

    log("[5/5] 开始 gradle 构建 …\n")
    env = dict(**__import__("os").environ)
    env["JAVA_HOME"] = JDK21_ENV
    cmd = [
        str(tree / "gradlew.bat"), "build", "--no-daemon",
        "--init-script", str(init),
    ]
    log("  " + " ".join(cmd))
    p = subprocess.run(cmd, cwd=str(tree), env=env,
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    out = (p.stdout or "") + "\n--- stderr ---\n" + (p.stderr or "")
    logfile = tree.parent / "build-last.txt"
    logfile.write_text(out, encoding="utf-8", newline="\n")
    log(f"\n日志: {logfile}")
    log(f"退出码: {p.returncode}")
    log("\n--- 输出尾部 ---")
    log("\n".join(out.splitlines()[-40:]))
    return p.returncode


if __name__ == "__main__":
    sys.exit(main())

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
    """打印。强制走 UTF-8，避免 Windows 控制台 GBK 编码把中文/替换字符搞崩。

    踩过的坑：gradle 输出里有非法字节（解码成 U+FFFD），我自己的 print 撞上
    "UnicodeEncodeError: 'gbk' codec can't encode character '\\ufffd'" —— 于是
    **构建其实成功了，脚本却在打印阶段崩了**，产物看起来像"失败"。
    处理子进程输出时想到了 UTF-8，却没处理自己的 print，这是不一致。
    """
    try:
        print(msg, flush=True)
    except UnicodeEncodeError:
        enc = sys.stdout.encoding or "utf-8"
        sys.stdout.buffer.write(msg.encode(enc, errors="replace") + b"\n")
        sys.stdout.flush()


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
    # ⚠️ 移除内嵌的 sqlite-jdbc —— 这是**硬冲突**的修法，不是优化。
    #
    # 实测崩溃（首次装机冒烟，客户端在类加载阶段就退出）：
    #   java.lang.module.ResolutionException: Modules grieflogger and org.xerial.sqlitejdbc
    #   export package org.sqlite.date to module supplementaries
    # 根因：本包已装的 `grieflogger-1.2.10` **直接内嵌** org/sqlite/ 39 个类；
    # 而 voxy 的 jarJar 又内嵌 `sqlite-jdbc-3.49.1.0.jar`（同样 39 个类）。
    # 两个模块导出同一个包 ⇒ Java 模块系统在**类加载阶段**（早于 mixin）拒绝启动。
    # 我扫过实例里全部 232 个 jar（含递归内嵌），提供 org/sqlite/date/ 的**只有这两个**。
    #
    # 为什么删掉是安全的：voxy 源码里 sqlite **只有一处用途**，而且自带优雅降级：
    #   DHImporter.java:470  Class.forName("org.sqlite.JDBC");
    #   DHImporter.java:475  Logger.warn("Unable to load sqlite JDBC or lzma decompressor,
    #                                     DHImporting wont be available", e);
    # 即"没有 sqlite = 不能导入 Distant Horizons 数据库"，不崩、不影响渲染。
    # voxy 的默认存储后端是 RocksDB，sqlite 是**备选**后端。
    # 我们本来也不用 DH 导入（正在放弃 DH 那套），代价近似为零。
    t_sql = bg2.read_text(encoding="utf-8")
    lines_sql = t_sql.splitlines(keepends=True)
    out_sql: list[str] = []
    skip = 0
    removed_sqlite = False
    for ln in lines_sql:
        if skip > 0:
            skip += ln.count("{") - ln.count("}")
            if skip <= 0:
                skip = 0
            continue
        if "jarJar(implementation(" in ln and "sqlite-jdbc" in ln:
            out_sql.append(
                "    // [build_voxy.py] 移除内嵌 sqlite-jdbc：与 grieflogger 内嵌的 org.sqlite.* 包冲突，\n"
                "    // 导致 java.lang.module.ResolutionException（Modules grieflogger and org.xerial.sqlitejdbc\n"
                "    // export package org.sqlite.date）。voxy 只有 DH 导入用它，缺了会自己 warn 并降级。\n"
            )
            skip = 1
            removed_sqlite = True
            continue
        out_sql.append(ln)
    if removed_sqlite:
        bg2.write_text("".join(out_sql), encoding="utf-8", newline="\n")
        log("[3/5] 已移除内嵌 sqlite-jdbc（修 grieflogger 的模块包冲突）")
    else:
        log("[3/5] sqlite-jdbc 已不在依赖里（跳过）")

    # ── 修 j-shelfwood 移植版引入的纹理单元越界 bug（100% 崩在"进世界"）──
    #
    # 实测证据（crash-reports + debug.log 双份）：
    #   me.cortex.voxy.client.LoadException: Force crashing due to exception during on game join
    #   Caused by: java.lang.ArrayIndexOutOfBoundsException: Index 12 out of bounds for length 12
    #     at com.mojang.blaze3d.platform.GlStateManager._bindTexture(GlStateManager.java:547)
    #     at me.cortex.voxy.client.core.VoxyRenderSystem.<init>(VoxyRenderSystem.java:318)
    #     at net.minecraft.client.renderer.LevelRenderer.createRenderer
    #     at voxy$reloadVoxyRenderer → LevelRenderer.setLevel   ← 进世界时
    #
    # 根因：MC 1.21.1 的 `GlStateManager.TEXTURE_COUNT = 12`（javap 字节码 `bipush 12`），
    # `_bindTexture(0)` 拿 `activeTexture` 索引那张长度 12 的数组：
    #     4: getstatic TEXTURES:[Lcom/mojang/blaze3d/platform/GlStateManager$l;
    #     7: getstatic activeTexture
    #    10: aaload                  ← i>=12 时越界
    # 移植版循环上界写成 16，于是 i=12 必崩。
    #
    # 上游写的是 `i < 12`（核对了 MCRcortex/voxy 的 12111 / dev / mc12110 / mc_1217 四个
    # 分支，VoxyRenderSystem 里一律 `i < 12`）⇒ **移植引入，不是上游的**。
    #
    # 只改**含 `_bindTexture` 的循环**：另有 2 处 `for(i<16) glBindBufferBase(...)`
    # 是 SSBO 槽位、与纹理单元无关，**不能动**。用公开常量 TEXTURE_COUNT 而非魔数 12。
    trs = tree / "src/main/java/me/cortex/voxy/client/core/VoxyRenderSystem.java"
    if trs.is_file():
        ls = trs.read_text(encoding="utf-8").splitlines(keepends=True)
        n_fixed = 0
        for i, ln in enumerate(ls):
            if re.search(r"for \(int i = 0; i < 16; i\+\+\)", ln) and "_bindTexture" in "".join(ls[i:i + 6]):
                ls[i] = ln.replace("i < 16", "i < GlStateManager.TEXTURE_COUNT")
                n_fixed += 1
        if n_fixed:
            trs.write_text("".join(ls), encoding="utf-8", newline="\n")
            log(f"[3/5] 修纹理单元越界：{n_fixed} 处 i<16 -> i<GlStateManager.TEXTURE_COUNT(=12)")
        else:
            log("[3/5] 无需修 _bindTexture 循环（可能已打过补丁）")
    else:
        log(f"[warn] 找不到 VoxyRenderSystem.java，纹理越界 bug 未修 —— 进世界会崩")


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

    # 产物结构校验：不能只看 "BUILD SUCCESSFUL"。
    # 尤其要确认 sqlite 已从内嵌库里消失（否则会和 grieflogger 的 org.sqlite.* 撞包、
    # 客户端在类加载阶段就退出 —— 这是我实测踩到的崩溃）。
    libs = tree / "build" / "libs"
    jars = sorted(libs.glob("voxy*.jar")) if libs.is_dir() else []
    if jars:
        import zipfile
        jar = jars[-1]
        log(f"\n--- 产物校验: {jar.name} ({jar.stat().st_size/1024/1024:.2f} MB) ---")
        with zipfile.ZipFile(jar) as z:
            names = z.namelist()
            embedded = [n for n in names if n.startswith("META-INF/jarjar/") and n.endswith(".jar")]
            log(f"  内嵌 jar: {[n.split('/')[-1] for n in embedded]}")
            bad = [n for n in embedded if "sqlite" in n.lower()]
            conflict = [n for n in names if n.startswith("org/sqlite/")]
            if bad or conflict:
                log("  [FAIL] 产物里仍有 sqlite —— 会和 grieflogger 撞 org.sqlite.date，客户端起不来")
                log(f"         内嵌: {bad}  顶层类: {len(conflict)}")
                return 3
            log("  [OK] 无 sqlite（grieflogger 的包冲突已避开）")
            sod = [n for n in names if n.startswith("me/cortex/voxy/client/mixin/sodium/")]
            emb = [n for n in names if n.startswith("me/cortex/voxy/client/mixin/embeddium/")]
            log(f"  [{'OK' if not sod else 'WARN'}] sodium mixin: {len(sod)}（应为 0）  embeddium mixin: {len(emb)}")
            toml = [n for n in names if n.endswith("neoforge.mods.toml")]
            if toml:
                cfg = re.findall(r'config="([^"]+)"', z.read(toml[0]).decode("utf-8", "replace"))
                log(f"  mixin 配置: {cfg}（不应含 iris 配置）")
            nats = [n for n in names if n.endswith((".dll", ".so"))]
            log(f"  原生库: {nats}")
    return p.returncode


if __name__ == "__main__":
    sys.exit(main())

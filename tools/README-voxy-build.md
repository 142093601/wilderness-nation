# 构建 voxy（NeoForge 1.21.1 · Embeddium 分支）

> **这套脚本存在的唯一原因**：本机到境外 Maven 镜像只有 **13–20 KB/s**，
> Gradle 自己解析 voxy 的依赖会长时间卡死（实测 10 分钟零进展）。
> 所以把"下载"和"构建"拆成两步，用多线程下载 + 本地 Maven 仓库绕开。

## 为什么是 j-shelfwood 的 `embeddium-compat` 分支

我们**不能**用现成的预编译 voxy：

| 来源 | 问题 |
|---|---|
| 官方 `MCRcortex/voxy` | **没有任何 1.21.1 分支**（13 个分支：1.21 / 1.21.5 / 1.21.7 / 1.21.8 / 1.21.10 / 1.21.11 / 26.x），且**全是 Fabric** |
| `yarnobachmann/Voxy-Neoforge.-1.21.1` | 有预编译 release，但 `mods.toml` 里 **`sodium` 与 `fabric_api` 都是 required** → 必须换渲染器 + 加 Fabric 兼容层；它的 mixin 用的是 `sodium.*` 包 |
| **`j-shelfwood/voxy-neoforge` `embeddium-compat`** | ✅ 用 `embeddium.*` mixin 包，`embeddium` 是 `type="optional"`，**不需要换渲染器**。代价：没有预编译 jar，得自己编 |

## 一条命令

```powershell
python tools/build_voxy.py
```

它做五件事（幂等，可重复跑）：

1. 把源码复制到 `data/_voxybuild/work/`（不污染 clone）
2. 镜像本机已有的 `embeddium` / `lithium` jar 到 `data/_voxym2/local/`
3. 给 `build.gradle` / `gradle.properties` 打补丁（见下）
4. 生成 `init-local-m2.gradle`（把本地 Maven 仓库插到解析顺序最前）
5. `gradlew build --offline` → 产物 `data/_voxybuild/work/build/libs/voxy-0.2.9-alpha.jar`

只有 `--patch-only` 时不构建。

## 前置：先把依赖下下来（约 85 MB）

```powershell
python tools/fetch_voxy_deps.py --jobs 8     # 第一次
python tools/fetch_voxy_deps.py --check      # 查看是否齐全
python tools/fetch_voxy_deps.py --jars-only  # 只补 Iris（2.33 MB）
```

依赖清单与**为什么每一项都需要**（都读自 voxy 的 build.gradle 与源码 import，
不是猜的）：

| 工件 | 大小 | 用途 |
|---|---:|---|
| `org.rocksdb:rocksdbjni:10.2.1` | 69.40 MB | voxy 的**默认 LOD 存储后端** |
| `org.xerial:sqlite-jdbc:3.49.1.0` | 13.65 MB | SQLite 存储后端 |
| `redis.clients:jedis:5.1.0` | 0.85 MB | Redis 后端（`RedisStorageBackend.java` **源码 import 它，编译期必需**） |
| `org.json:json:20231013` | 0.07 MB | jedis 传递依赖 |
| `org.apache.commons:commons-pool2:2.12.0` | 0.14 MB | jedis 传递依赖 |
| `org.tukaani:xz:1.10` | 0.16 MB | `DHImporter.java` **源码 import 它，编译期必需** |
| `org.lwjgl:lwjgl-lmdb` / `lwjgl-zstd` (+ `natives-windows`/`natives-linux`) | ~0.5 MB | LOD 数据库与压缩，**Minecraft 不提供** |
| `iris-neoforge.jar`（Modrinth 直下） | 2.33 MB | **仅编译期**：voxy 源码 import `net.irisshaders.iris.*` |

> ⚠️ **踩过的坑**：我一度以为 jedis / xz / sqlite-jdbc 是"我们用不到的可选功能"
> 而把它们从依赖里删掉。结果编译报 19 个"程序包不存在"——
> 因为**源码本身就 import 它们**。判断"能不能删"要看源码 import，不能看功能描述。

## 打补丁改了什么（都可核对）

| 文件 | 改动 | 为什么 |
|---|---|---|
| `gradle.properties` | `org.gradle.java.home` → 本机 JDK21（**正斜杠**） | 原值是 macOS 路径；反斜杠会被 Java properties 当转义吃掉（实测变成 `C:Program FilesJavajdk-21.0.10`） |
| `gradle.properties` | `neoforge_version` 21.1.115 → **21.1.250** | 本地 Gradle 缓存已有 21.1.250 全套（mods/statecraft 编译时留下）；它满足 voxy 的 `[21.1.115,)` 约束 |
| `build.gradle` | ModDevGradle `2.0.42-beta` → **2.0.147** | 前者要 `neoform-runtime:1.0.6`（本地没有）；后者对应本地的 `2.0.31` |
| `build.gradle` | 移除 `parchment {}` 块 | 参数名映射只对"IDE 里读 MC 源码"有意义，编译产物不需要，省一次下载 |
| `build.gradle` | `embeddium` / `lithium` 的 Modrinth 坐标 → 本地 jar **绝对路径** | `api.modrinth.com` 在本机不可达（Cloudflare，~15 KB/s）。embeddium 在 `mods.toml` 里是 `optional`，只作编译用 |
| `build.gradle` | Iris 的 `compileOnly files(...)` → 指向我们下的 iris jar 绝对路径 | 原值指向 `.reference/iris/iris-neoforge.jar`（仓库里没有）。**Groovy 单引号串里反斜杠非法**，必须正斜杠 |
| `build.gradle` | 两个校验任务的 `python3` → 真 Python311 + `-X utf8` | 本机 `python3` 是 Windows 商店占位 stub（跳向不存在的 Python313）；且脚本打印 `✓` 会撞 GBK 编码 |

## 怎么确认构建真的成了

```powershell
# 1) 产物在
ls data/_voxybuild/work/build/libs/
# 2) 校验产物结构（比"BUILD SUCCESSFUL"更硬的证据）
```

产物结构应有（我实测核对过）：

- `META-INF/neoforge.mods.toml`：`embeddium` 是 `optional`；**只声明 `client.voxy.mixins.json` + `common.voxy.mixins.json`**（没有 iris 配置 → 运行时无 Iris 不会加载 Iris mixin）
- `me/cortex/voxy/client/mixin/embeddium/` **7 个类**，`.../mixin/sodium/` **0 个**
- `META-INF/jarjar/`：rocksdbjni / sqlite-jdbc / xz / jedis 四个内嵌库
- jar 根：`lwjgl_lmdb.dll`、`lwjgl_zstd.dll`、`liblwjgl_*.so`（LWJGL 类被展平进主 jar）

## 已知的非问题

- **`:validateMixins FAILED`**（`Process 'command 'bash'' finished with non-zero exit value 2`）
  不影响产物：它是仓库自带的一个 bash 自检脚本，Windows 上跑不了。
  **真正有意义的两个 Python 校验器已经通过**：`validateMixinConfig`
  （mixin 注册覆盖 / remap 标志 / JSON schema，Errors: 0）与 `validateMixinSignatures`。
- **`:validateMixinSignatures SKIPPED`**：需要 MC 反编译源码，本机没备。

## 授权

`voxy` 的 `LICENSE.md` 是 **All Rights Reserved / "Do not redistribute"**。
本脚本只为**本地构建自用**产物服务；**不要把编出来的 jar 分发出去**。
`iris` 是 LGPL-3.0，`embeddium` / `lithium` 各自按其许可，仅作编译引用。

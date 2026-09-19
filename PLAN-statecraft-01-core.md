# Statecraft 实现计划 · 计划 1／共 4：M0 验证 + core 骨架

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Statecraft 的"国家大脑"落到一个**纯 Java、零 Minecraft 依赖**的 `core` 模块里，并用 JUnit 把开局生成、确定性随机、时代表、配置默认值全部锁死；同时先验掉那个会决定整个夺城环节能否成立的技术未知项。

**Architecture:** `core` 只装规则（数据模型 / 生成 / 结算 / 外交状态机 / 环境判定），不 import 任何 Minecraft 类 → 可以用 JUnit 把几个时代一次跑完，85~90% 的逻辑不必开游戏。MC 相关的 `integration` / `presentation` 是后续计划，本次**不碰**。core 与 mod 层最终住同一个仓库、同一个 Gradle 工程（`mods/statecraft/`），但**分模块**：本次只建 `core` 模块。

**Tech Stack:** Java 21（`C:\Program Files\Java\jdk-21.0.10`）· Gradle 8.11.1（本机已有缓存分发）· JUnit 5.11.3（Maven Central）

---

## 前提：本机环境实测（2026-09-19）

| 项 | 实测结果 |
|---|---|
| JDK | ✅ `C:\Program Files\Java\jdk-21.0.10`（21.0.10+8-LTS）|
| Gradle | ⚠️ **未安装**，但 `~/.gradle/wrapper/dists` 里有缓存分发：`gradle-8.11.1-bin` / `gradle-8.12-all` / `gradle-9.5.1-bin` |
| Maven | ✅ 已装（`D:\apache-maven-3.8.2`），备用 |
| Maven Central | ✅ 200 |
| services.gradle.org | ✅ 200（生成 wrapper 时能下分发）|
| piston-meta.mojang.com | ✅ 200 |
| **maven.neoforged.net** | ❌ **超时**（直连与走 127.0.0.1:7897 代理都不通；该端口未监听）|
| 本地 NeoForge 产物 | 只有运行时 `neoforge-21.1.250-client.jar` / `-universal.jar`，**没有 userdev**（编译 mod 需要）|
| RCON 命令通道 | ❌ 不可靠（自带客户端与手写裸客户端都只拿到空响应）→ **测试不依赖命令** |

**结论**：本计划（M0 + M1）**全部可执行**；**M2 起需要先解决 `maven.neoforged.net` 的可达性**（见文末「后续计划与阻塞」）。

---

## 文件结构（本次要建立的全部文件）

```
mods/statecraft/
├── settings.gradle.kts                    # rootProject.name + include("core")
├── gradle.properties                      # JVM 参数
├── gradlew / gradlew.bat / gradle/wrapper # 由 Task 1 生成（固定 8.11.1）
└── core/
    ├── build.gradle.kts                   # java-library + JUnit5 + toolchain 21
    └── src/
        ├── main/java/com/wildernessnation/statecraft/core/
        │   ├── config/StatecraftConfig.java   # 全部默认值 + 校验钳制
        │   ├── rng/DeterministicRandom.java   # hash(seed, seq, tag) → 可复现随机
        │   ├── era/Era.java                   # 一条时代（id/名称/序号/时长/系数/解锁）
        │   ├── era/EraTable.java              # 时代表：按累计在线时间取当前时代、解锁判定、未知 id 归位
        │   ├── model/Culture.java             # 文化：id、显示名、国名前缀池
        │   ├── model/Nation.java              # 国家（不可变）
        │   ├── model/WorldState.java          # 世界状态（本计划只含 seed/nations/era/seq）
        │   └── gen/WorldGenerator.java        # 开局生成：布点 / 规模 / 发展度 / 倾向 / 国名
        └── test/java/com/wildernessnation/statecraft/core/
            ├── rng/DeterministicRandomTest.java
            ├── config/StatecraftConfigTest.java
            ├── era/EraTableTest.java
            └── gen/WorldGeneratorTest.java
```

**职责边界**：一个文件一件事。`EraTable` 不管 JSON（数据的加载属于后续 integration 层）；`WorldGenerator` 不读文件、不碰世界，只吃配置 + seed 吐出 `WorldState`。

---

## Task 0: M0 — 真机验证"能否刷出 HYW 单位并让它们攻击玩家"

**为什么排第一**：夺城环节整个挂在它上面。这一步不过，M4 就是空中楼阁。**先花 20 分钟验掉它，再写一行生产代码。**

**Files:**
- Create: `tools/tests/hyw_units.txt`（真客户端自动化脚本，供 `tools/game_agent.py --script` 使用）
- Modify: `NATIONS.md`（§十四 已知风险：把第 1 条从"未验证"改成实测结论）

- [ ] **Step 1: 确认 HYW 的实体 id**

HYW 的 jar 里 `assets/hundred_years_war/lang/en_us.json` 有 103 个 `entity.hundred_years_war.*` 键。
取三个做样本：`bandit_soldier`（普通兵）、`desert_raider_commander`（指挥官候选）、`battering_ram`（攻城器械）。

Run:
```powershell
cd D:\project\nation-pack
python -c "import zipfile,json,sys; sys.stdout.reconfigure(encoding='utf-8'); z=zipfile.ZipFile(r'D:\game\PCL\.minecraft\versions\1.21.1-NeoForge_21.1.250\mods\HundredYearsWar-0.7.1r-fix1-1.21.1-neoforge.jar'); d=json.loads(z.read('assets/hundred_years_war/lang/en_us.json').decode('utf-8')); print('\n'.join(k for k in d if k in ('entity.hundred_years_war.bandit_soldier','entity.hundred_years_war.desert_raider_commander','entity.hundred_years_war.battering_ram')))"
```
Expected: 三行 `entity.hundred_years_war.bandit_soldier` / `...desert_raider_commander` / `...battering_ram`

- [ ] **Step 2: 写自动化脚本**

Create `tools/tests/hyw_units.txt`：
```
# M0：验证能否刷出 HYW 单位、以及它们会不会攻击玩家
# 用法：python tools/game_agent.py --launch --script tests/hyw_units.txt
# 注意：不靠 /summon 的回显（不同版本回显不稳定），一律用 execute if entity 断言实体真的在。
cmd: /time set day
cmd: /gamemode survival
cmd: /effect give @s minecraft:resistance 60 4 true
cmd: /summon hundred_years_war:bandit_soldier ~ ~ ~4
cmd: /execute if entity @e[type=hundred_years_war:bandit_soldier,limit=1]
expect: Test passed
cmd: /summon hundred_years_war:desert_raider_commander ~ ~ ~6
cmd: /execute if entity @e[type=hundred_years_war:desert_raider_commander,limit=1]
expect: Test passed
cmd: /summon hundred_years_war:battering_ram ~ ~ ~8
cmd: /execute if entity @e[type=hundred_years_war:battering_ram,limit=1]
expect: Test passed
# 反面对照：证明这个断言真的会失败（否则"Test passed"可能只是没报错）
cmd: /execute if entity @e[type=hundred_years_war:nonexistent_probe,limit=1]
expect: Test failed
```
> 先给 60 秒抗性 5 是为了让"掉血"只可能来自敌人而不是摔伤；Step 4 会 `/effect clear` 再观察。

- [ ] **Step 3: 跑脚本**

Run（注意 `game_agent.py` 的 `--script` 是**相对 tools/ 目录**解析的，所以要先进 tools）：
```powershell
cd D:\project\nation-pack\tools
python game_agent.py --launch --script tests\hyw_units.txt
```
Expected: 全部 `PASS`，退出码 0；`logs/latest.log` 里能看到这些命令的聊天行（含 `Test passed` / `Test failed`）。

- [ ] **Step 4: 判定敌对性（两次血量对比，同一个 shell 里做完）**

```powershell
cd D:\project\nation-pack\tools
python game_agent.py --attach --run "/effect clear @s"
python game_agent.py --attach --run "/data get entity @s Health"
Start-Sleep -Seconds 15          # 站着别动，让它来打你
python game_agent.py --attach --run "/data get entity @s Health"
```
Expected（通过）: 第二次的数值**小于**第一次 → 它会攻击玩家。
若 `--attach` 报"找不到窗口"（上一步启动的客户端已退出）→ 改用 `--launch` 重进一次再读血量；
注意**实体是存在存档里的**，重进后那只 bandit 还在原地，所以这个兜底不会让实验失效。
Expected（不通过）: 两次相同 → 它不主动攻击，记录到 `NATIONS.md` 的 §十四，并触发下面的**退路**。


- [ ] **Step 5: 记录结论**

Modify `NATIONS.md` §十四 第 1 条，把"未验证"替换成实测结论，二选一：
- 通过 → `✅ 已验证（日期）：/summon 可刷出 HYW 单位，且 bandit_soldier 会主动攻击玩家（血量实测下降）。`
- 不通过 → 如实写"刷得出来但不主动攻击"，并列出退路：改用原版灾厄（`minecraft:pillager` / `minecraft:vindicator` / `minecraft:ravager`）当守城军，指挥官用 `minecraft:evoker`；HYW 单位只作装饰。

- [ ] **Step 6: 提交**

```powershell
git add tools/tests/hyw_units.txt NATIONS.md
git commit -m "test(m0): 验证 HYW 单位可刷出并具敌对性（夺城环节前置）"
```

---

## Task 1: Gradle 工程骨架（含 core 模块）

**Files:**
- Create: `mods/statecraft/settings.gradle.kts`
- Create: `mods/statecraft/gradle.properties`
- Create: `mods/statecraft/core/build.gradle.kts`

- [ ] **Step 1: 建目录与两个 Gradle 文件**

Create `mods/statecraft/settings.gradle.kts`：
```kotlin
rootProject.name = "statecraft"
include("core")
```

Create `mods/statecraft/gradle.properties`：
```properties
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.caching=true
```

Create `mods/statecraft/core/build.gradle.kts`：
```kotlin
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.3")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
```

- [ ] **Step 2: 用缓存里的 Gradle 生成 wrapper（固定 8.11.1）**

Run:
```powershell
# 本机没装 Gradle，但 ~/.gradle/wrapper/dists 里有缓存的分发，直接调它
$g = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists" -Recurse -Filter "gradle.bat" -ErrorAction SilentlyContinue |
     Where-Object { $_.FullName -match "gradle-8\.11\.1" } | Select-Object -First 1 -ExpandProperty FullName
if (-not $g) { $g = Get-ChildItem "$env:USERPROFILE\.gradle\wrapper\dists" -Recurse -Filter "gradle.bat" | Select-Object -First 1 -ExpandProperty FullName }
"用的 gradle: $g"
& $g -p D:\project\nation-pack\mods\statecraft wrapper --gradle-version 8.11.1 --console=plain
```
Expected: `BUILD SUCCESSFUL`，并且 `mods/statecraft/` 下出现 `gradlew`、`gradlew.bat`、`gradle/wrapper/`。
（若这一步下不动分发：`services.gradle.org` 实测可达；实在不行改用 Maven，见文末阻塞说明。）

- [ ] **Step 3: 验证工程能编译（此时还没有任何源文件）**

Run:
```powershell
cd D:\project\nation-pack\mods\statecraft
.\gradlew.bat :core:compileJava --console=plain
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```powershell
cd D:\project\nation-pack
git add mods/statecraft/settings.gradle.kts mods/statecraft/gradle.properties mods/statecraft/core/build.gradle.kts mods/statecraft/gradlew mods/statecraft/gradlew.bat mods/statecraft/gradle
git commit -m "build(statecraft): Gradle 骨架 + core 模块（Java 21 + JUnit5）"
```

---

## Task 2: `DeterministicRandom` —— 可复现的随机

**为什么先做它**：整个设计的一致性建立在"**同 seed 同输入必得同结果**"上（可复现、可测试、崩溃可重放）。它是后面所有模块的地基。

**Files:**
- Create: `mods/statecraft/core/src/main/java/com/wildernessnation/statecraft/core/rng/DeterministicRandom.java`
- Test: `mods/statecraft/core/src/test/java/com/wildernessnation/statecraft/core/rng/DeterministicRandomTest.java`

- [ ] **Step 1: 写失败的测试**

Create `DeterministicRandomTest.java`：
```java
package com.wildernessnation.statecraft.core.rng;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeterministicRandomTest {

    @Test
    void sameSeedSeqTagGivesSameValue() {
        DeterministicRandom a = new DeterministicRandom(42L);
        DeterministicRandom b = new DeterministicRandom(42L);
        assertEquals(a.hash(7L, "size#3"), b.hash(7L, "size#3"));
        assertEquals(a.nextDouble(7L, "size#3"), b.nextDouble(7L, "size#3"), 0.0);
    }

    @Test
    void differentSeedOrSeqOrTagChangesValue() {
        DeterministicRandom r = new DeterministicRandom(42L);
        assertNotEquals(r.hash(7L, "size#3"), r.hash(8L, "size#3"));
        assertNotEquals(r.hash(7L, "size#3"), r.hash(7L, "size#4"));
        assertNotEquals(r.hash(7L, "size#3"), new DeterministicRandom(43L).hash(7L, "size#3"));
    }

    @Test
    void nextDoubleIsInUnitInterval() {
        DeterministicRandom r = new DeterministicRandom(1234L);
        for (long seq = 0; seq < 500; seq++) {
            double d = r.nextDouble(seq, "probe");
            assertTrue(d >= 0.0 && d < 1.0, "nextDouble out of range: " + d);
        }
    }

    @Test
    void rangeStaysWithinBoundsInclusive() {
        DeterministicRandom r = new DeterministicRandom(99L);
        for (long seq = 0; seq < 500; seq++) {
            int v = r.range(seq, "size", 1, 10);
            assertTrue(v >= 1 && v <= 10, "range out of bounds: " + v);
        }
    }

    @Test
    void rangeDoubleStaysWithinBounds() {
        DeterministicRandom r = new DeterministicRandom(5L);
        for (long seq = 0; seq < 200; seq++) {
            double v = r.rangeDouble(seq, "jit", -8.0, 8.0);
            assertTrue(v >= -8.0 && v <= 8.0, "rangeDouble out of bounds: " + v);
        }
    }

    @Test
    void rejectsNonPositiveBound() {
        DeterministicRandom r = new DeterministicRandom(1L);
        assertThrows(IllegalArgumentException.class, () -> r.nextInt(0L, "x", 0));
    }

    @Test
    void rejectsInvertedRange() {
        DeterministicRandom r = new DeterministicRandom(1L);
        assertThrows(IllegalArgumentException.class, () -> r.range(0L, "x", 5, 4));
    }
}
```

- [ ] **Step 2: 跑测试，确认它失败**

Run:
```powershell
cd D:\project\nation-pack\mods\statecraft
.\gradlew.bat :core:test --tests "*DeterministicRandomTest*" --console=plain
```
Expected: 编译失败（`package com.wildernessnation.statecraft.core.rng does not exist`）

- [ ] **Step 3: 写实现**

Create `DeterministicRandom.java`：
```java
package com.wildernessnation.statecraft.core.rng;

/**
 * 可复现随机：所有取值都来自 hash(seed, seq, tag)，不使用可变状态。
 *
 * <p>为什么不用 {@link java.util.Random}：它是有状态的流，调用顺序一变结果就全变，
 * 于是"同 seed 复现""崩溃后重放""单测固定断言"都做不了。这里把随机变成<strong>纯函数</strong>：
 * 只要 (seed, 结算序号, 用途标签) 相同，任何机器任何时刻都得到同一个值。
 */
public final class DeterministicRandom {

    private final long seed;

    public DeterministicRandom(long seed) {
        this.seed = seed;
    }

    /** SplitMix64 的混合步：雪崩性好、无依赖、跨 JVM 稳定。 */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 用途标签的稳定哈希（不用 String.hashCode，避免受 JVM 实现影响）。 */
    private static long mixTag(long h, String tag) {
        for (int i = 0; i < tag.length(); i++) {
            h = mix(h ^ (tag.charAt(i) * 0x100000001B3L));
        }
        return h;
    }

    public long hash(long seq, String tag) {
        return mixTag(mix(seed ^ (seq * 0x9E3779B97F4A7C15L)), tag);
    }

    /** [0,1) 的双精度数（取高 53 位，与 double 的尾数精度一致）。 */
    public double nextDouble(long seq, String tag) {
        return (hash(seq, tag) >>> 11) * 0x1.0p-53;
    }

    /** [0, boundExclusive) 的整数。 */
    public int nextInt(long seq, String tag, int boundExclusive) {
        if (boundExclusive <= 0) {
            throw new IllegalArgumentException("bound 必须 > 0，实际 " + boundExclusive);
        }
        return (int) Math.floorMod(hash(seq, tag), boundExclusive);
    }

    /** [minInclusive, maxInclusive] 的整数。 */
    public int range(long seq, String tag, int minInclusive, int maxInclusive) {
        if (maxInclusive < minInclusive) {
            throw new IllegalArgumentException(
                    "max 不能小于 min：" + minInclusive + ".." + maxInclusive);
        }
        return minInclusive + nextInt(seq, tag, maxInclusive - minInclusive + 1);
    }

    /** [min, max] 的双精度数。 */
    public double rangeDouble(long seq, String tag, double min, double max) {
        return min + nextDouble(seq, tag) * (max - min);
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run:
```powershell
.\gradlew.bat :core:test --tests "*DeterministicRandomTest*" --console=plain
```
Expected: `BUILD SUCCESSFUL`，7 个测试全 `PASSED`

- [ ] **Step 5: 提交**

```powershell
cd D:\project\nation-pack
git add mods/statecraft/core/src
git commit -m "feat(core): DeterministicRandom —— hash(seed,seq,tag) 的纯函数随机"
```

---

## Task 3: `StatecraftConfig` —— 全部默认值 + 钳制

**Files:**
- Create: `mods/statecraft/core/src/main/java/com/wildernessnation/statecraft/core/config/StatecraftConfig.java`
- Test: `mods/statecraft/core/src/test/java/com/wildernessnation/statecraft/core/config/StatecraftConfigTest.java`

- [ ] **Step 1: 写失败的测试**

Create `StatecraftConfigTest.java`：
```java
package com.wildernessnation.statecraft.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatecraftConfigTest {

    @Test
    void defaultsMatchSpec() {
        StatecraftConfig c = StatecraftConfig.defaults();
        assertEquals(12, c.nationCount());
        assertEquals(1500, c.minNationSpacing());
        assertEquals(1, c.sizeMin());
        assertEquals(10, c.sizeMax());
        assertEquals(95.0, c.devBase(), 0.0);
        assertEquals(6.0, c.devPerSize(), 0.0);
        assertEquals(8.0, c.devJitter(), 0.0);
        assertEquals(20.0, c.devMin(), 0.0);
        assertEquals(60.0, c.stanceBase(), 0.0);
        assertEquals(30.0, c.stanceJitter(), 0.0);
    }

    @Test
    void validatedClampsOutOfRangeValues() {
        // 国家数 -5 → 钳到下限 8；内半径 100 < 国家间距 1500 → 抬到 1500
        StatecraftConfig wild = new StatecraftConfig(
                -5, 1500, 1, 10,
                95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0,
                100.0, 12000.0,
                8.0);
        StatecraftConfig c = wild.validated();
        assertEquals(8, c.nationCount(), "国家数下限 8");
        assertEquals(1500, c.minPlacementRadiusAsInt(), "内半径不能小于国家间距");
    }

    @Test
    void validatedKeepsGoodValues() {
        assertEquals(StatecraftConfig.defaults(), StatecraftConfig.defaults().validated());
    }

    @Test
    void rejectsNonSenseSpacing() {
        StatecraftConfig bad = new StatecraftConfig(
                12, 0, 1, 10,
                95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0,
                0.0, 0.0,
                8.0);
        assertThrows(IllegalStateException.class, bad::validated);
    }

    @Test
    void canRestrictSizeRange() {
        StatecraftConfig c = new StatecraftConfig(
                8, 1500, 3, 10,
                95.0, 6.0, 8.0, 20.0, 95.0,
                60.0, 30.0,
                2000.0, 12000.0,
                8.0).validated();
        assertEquals(3, c.sizeMin());
        assertEquals(10, c.sizeMax());
    }
}
```

- [ ] **Step 2: 跑测试，确认它失败**

Run:
```powershell
cd D:\project\nation-pack\mods\statecraft
.\gradlew.bat :core:test --tests "*StatecraftConfigTest*" --console=plain
```
Expected: 编译失败（`cannot find symbol: class StatecraftConfig`）

- [ ] **Step 3: 写实现**

Create `StatecraftConfig.java`：
```java
package com.wildernessnation.statecraft.core.config;

/**
 * 全部可调参数与默认值（对应 NATIONS.md §十二 的数值表）。
 *
 * <p>设计约定：<strong>默认值就是我们要的那套</strong>，改配置只为了试平衡，
 * 不是为了"让代码跑起来"。{@link #validated()} 负责钳制，非法组合直接抛异常，
 * 绝不把非法状态写进存档。
 */
public record StatecraftConfig(
        int nationCount,
        int minNationSpacing,
        int sizeMin,
        int sizeMax,
        double devBase,
        double devPerSize,
        double devJitter,
        double devMin,
        double devMax,
        double stanceBase,
        double stanceJitter,
        double minPlacementRadius,
        double maxPlacementRadius,
        double placementAngleStepDeg) {

    public static final int NATION_COUNT_MIN = 8;
    public static final int NATION_COUNT_MAX = 16;

    public static StatecraftConfig defaults() {
        return new StatecraftConfig(
                12,      // 国家数量
                1500,    // 国家之间最小间距（格）
                1, 10,   // 规模范围
                95.0,    // devBase：发展度基准
                6.0,     // devPerSize：每 +1 规模，发展度下降多少
                8.0,     // devJitter：发展度抖动
                20.0,    // devMin
                95.0,    // devMax
                60.0,    // stanceBase
                30.0,    // stanceJitter
                2000.0,  // 布点内半径
                12000.0, // 布点外半径
                8.0);    // 角度步长（度），仅用于调试可读性
    }

    public StatecraftConfig validated() {
        int nations = clamp(nationCount, NATION_COUNT_MIN, NATION_COUNT_MAX);
        if (minNationSpacing <= 0) {
            throw new IllegalStateException("minNationSpacing 必须 > 0，实际 " + minNationSpacing);
        }
        int smin = Math.max(1, sizeMin);
        int smax = Math.min(10, sizeMax);
        if (smax < smin) {
            throw new IllegalStateException("sizeMax 不能小于 sizeMin：" + smin + ".." + smax);
        }
        double rmin = Math.max(minPlacementRadius, minNationSpacing);
        double rmax = Math.max(maxPlacementRadius, rmin);
        if (rmax <= 0.0 || rmin <= 0.0) {
            throw new IllegalStateException("布点半径必须 > 0：" + rmin + ".." + rmax);
        }
        double dmin = Math.max(0.0, Math.min(devMin, devMax));
        double dmax = Math.max(dmin, Math.max(devMin, devMax));
        return new StatecraftConfig(nations, minNationSpacing, smin, smax,
                devBase, devPerSize, Math.max(0.0, devJitter), dmin, dmax,
                stanceBase, Math.max(0.0, stanceJitter),
                rmin, rmax, Math.max(1.0, placementAngleStepDeg));
    }

    /** 测试用：把内半径当整数看，避免在断言里写一长串浮点比较。 */
    public int minPlacementRadiusAsInt() {
        return (int) Math.round(minPlacementRadius);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run:
```powershell
.\gradlew.bat :core:test --tests "*StatecraftConfigTest*" --console=plain
```
Expected: `BUILD SUCCESSFUL`，5 个测试全 `PASSED`

- [ ] **Step 5: 提交**

```powershell
cd D:\project\nation-pack
git add mods/statecraft/core/src
git commit -m "feat(core): StatecraftConfig —— 默认值与钳制校验"
```

---

## Task 4: `Era` / `EraTable` —— 数据驱动的时代（**不写死数量**）

**为什么关键**：用户明确要求"时代可能被改"，所以代码里**不许出现 6**。解锁一律按 era id 声明，未知 id 要能归位而不是废档。

**Files:**
- Create: `mods/statecraft/core/src/main/java/com/wildernessnation/statecraft/core/era/Era.java`
- Create: `mods/statecraft/core/src/main/java/com/wildernessnation/statecraft/core/era/EraTable.java`
- Test: `mods/statecraft/core/src/test/java/com/wildernessnation/statecraft/core/era/EraTableTest.java`

- [ ] **Step 1: 写失败的测试**

Create `EraTableTest.java`：
```java
package com.wildernessnation.statecraft.core.era;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EraTableTest {

    private static Era era(String id, int ordinal, int hours, Set<String> unlocks) {
        return new Era(id, "时代" + ordinal, ordinal, hours, 1.0 + ordinal * 0.15, unlocks);
    }

    private static EraTable sixEras() {
        return new EraTable(List.of(
                era("landing", 0, 12, Set.of("intel_book")),
                era("founding", 1, 20, Set.of()),
                era("infra", 2, 25, Set.of("intel_station", "declaration")),
                era("expedition", 3, 25, Set.of()),
                era("defense", 4, 30, Set.of()),
                era("civilization", 5, 38, Set.of("intel_station_2"))));
    }

    private static EraTable threeEras() {
        return new EraTable(List.of(
                era("early", 0, 10, Set.of("intel_book")),
                era("mid", 1, 20, Set.of("intel_station")),
                era("late", 2, 40, Set.of("intel_station_2"))));
    }

    @Test
    void worksForAnyEraCount() {
        assertEquals(6, sixEras().size());
        assertEquals(3, threeEras().size());
        assertEquals(9, nineEras().size());
    }

    @Test
    void currentEraFollowsCumulativeOnlineHours() {
        EraTable t = sixEras();
        assertEquals("landing", t.currentFor(0.0).id());
        assertEquals("landing", t.currentFor(11.9).id());
        assertEquals("founding", t.currentFor(12.0).id());
        assertEquals("founding", t.currentFor(31.9).id());
        assertEquals("infra", t.currentFor(32.0).id());
    }

    @Test
    void beyondLastEraStaysOnLastEra() {
        EraTable t = sixEras();
        assertEquals("civilization", t.currentFor(9999.0).id());
        assertEquals("civilization", t.currentFor(-5.0).id());
    }

    @Test
    void unlockIsDeclaredByEraIdNotOrdinal() {
        EraTable t = sixEras();
        assertTrue(t.isUnlocked("intel_book", t.byId("landing").orElseThrow()));
        assertFalse(t.isUnlocked("intel_station", t.byId("landing").orElseThrow()));
        assertTrue(t.isUnlocked("intel_station", t.byId("infra").orElseThrow()));
        assertTrue(t.isUnlocked("intel_station", t.byId("civilization").orElseThrow()),
                "后期时代应保持已解锁");
    }

    @Test
    void unknownEraIdFallsBackToNearestOrdinal() {
        EraTable t = sixEras();
        assertEquals("infra", t.fallbackForUnknownId("legacy_era_x", 2).id());
        assertEquals("civilization", t.fallbackForUnknownId("legacy_era_x", 99).id());
        assertEquals("landing", t.fallbackForUnknownId("legacy_era_x", -3).id());
    }

    @Test
    void rejectsNonContiguousOrdinals() {
        assertThrows(IllegalArgumentException.class, () -> new EraTable(List.of(
                era("a", 0, 10, Set.of()),
                era("b", 2, 10, Set.of()))));
    }

    @Test
    void rejectsDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> new EraTable(List.of(
                era("a", 0, 10, Set.of()),
                era("a", 1, 10, Set.of()))));
    }

    @Test
    void rejectsEmptyTable() {
        assertThrows(IllegalArgumentException.class, () -> new EraTable(List.of()));
    }

    private static EraTable nineEras() {
        List<Era> list = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            list.add(era("e" + i, i, 10 + i, i == 8 ? Set.of("intel_station_2") : Set.of()));
        }
        return new EraTable(list);
    }
}
```

- [ ] **Step 2: 跑测试，确认它失败**

Run:
```powershell
cd D:\project\nation-pack\mods\statecraft
.\gradlew.bat :core:test --tests "*EraTableTest*" --console=plain
```
Expected: 编译失败（`cannot find symbol: class Era`）

- [ ] **Step 3: 写实现**

Create `Era.java`：
```java
package com.wildernessnation.statecraft.core.era;

import java.util.Set;

/**
 * 一条时代定义。**所有内容都来自数据（eras.json），代码不假设有多少个时代。**
 *
 * @param id            稳定标识（表意）。存档与事件记它，改时代表也不会失效
 * @param name          中文显示名
 * @param ordinal       序号（排序用，从 0 连续）
 * @param durationHours 该时代时长（累计在线小时）
 * @param coefficient   全局数值系数
 * @param unlocks       该时代解锁的能力键（如 intel_station / declaration）
 */
public record Era(
        String id,
        String name,
        int ordinal,
        int durationHours,
        double coefficient,
        Set<String> unlocks) {

    public Era {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Era.id 不能为空");
        }
        if (ordinal < 0) {
            throw new IllegalArgumentException("Era.ordinal 不能为负：" + ordinal);
        }
        if (durationHours <= 0) {
            throw new IllegalArgumentException("Era.durationHours 必须 > 0：" + durationHours);
        }
        unlocks = unlocks == null ? Set.of() : Set.copyOf(unlocks);
    }
}
```

Create `EraTable.java`：
```java
package com.wildernessnation.statecraft.core.era;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 时代表：按"累计在线小时"取当前时代、判定解锁、把未知 eraId 归位。
 *
 * <p>刻意的设计：<strong>解锁用 era id 判定，不用序号</strong>。
 * 如果写成"第 3 个时代解锁望楼"，那用户把六时代改成九时代时，望楼就会挪位置。
 */
public final class EraTable {

    private final List<Era> eras;

    public EraTable(List<Era> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("时代表不能为空");
        }
        List<Era> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingInt(Era::ordinal));

        Set<Integer> ordinals = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            Era e = sorted.get(i);
            if (e.ordinal() != i) {
                throw new IllegalArgumentException(
                        "时代序号必须从 0 连续：期望 " + i + "，实际 " + e.ordinal());
            }
            if (!ordinals.add(e.ordinal())) {
                throw new IllegalArgumentException("重复的时代序号：" + e.ordinal());
            }
            if (!ids.add(e.id())) {
                throw new IllegalArgumentException("重复的时代 id：" + e.id());
            }
        }
        this.eras = List.copyOf(sorted);
    }

    public int size() {
        return eras.size();
    }

    public List<Era> eras() {
        return eras;
    }

    public Era byOrdinal(int ordinal) {
        int clamped = Math.max(0, Math.min(eras.size() - 1, ordinal));
        return eras.get(clamped);
    }

    public Optional<Era> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return eras.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** 按累计在线小时取当前时代；超出末尾停在最后一个，负数按 0 处理。 */
    public Era currentFor(double elapsedOnlineHours) {
        double t = Math.max(0.0, elapsedOnlineHours);
        double acc = 0.0;
        for (Era e : eras) {
            acc += e.durationHours();
            if (t < acc) {
                return e;
            }
        }
        return eras.get(eras.size() - 1);
    }

    /** 该能力是否已在 current（含）之前的任何时代被声明解锁。 */
    public boolean isUnlocked(String unlockKey, Era current) {
        for (Era e : eras) {
            if (e.ordinal() > current.ordinal()) {
                break;
            }
            if (e.unlocks().contains(unlockKey)) {
                return true;
            }
        }
        return false;
    }

    /** 存档里出现未知 eraId 时的归位：钳到 [0, size-1]。 */
    public Era fallbackForUnknownId(String unknownId, int hintedOrdinal) {
        return byOrdinal(hintedOrdinal);
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run:
```powershell
.\gradlew.bat :core:test --tests "*EraTableTest*" --console=plain
```
Expected: `BUILD SUCCESSFUL`，8 个测试全 `PASSED`

- [ ] **Step 5: 提交**

```powershell
cd D:\project\nation-pack
git add mods/statecraft/core/src
git commit -m "feat(core): Era/EraTable —— 时代数据驱动、按 id 解锁、未知 id 归位"
```

---

## Task 5: 模型 + `WorldGenerator` —— 开局生成

**Files:**
- Create: `mods/statecraft/core/src/main/java/com/wildernessnation/statecraft/core/model/Culture.java`
- Create: `mods/statecraft/core/src/main/java/com/wildernessnation/statecraft/core/model/Nation.java`
- Create: `mods/statecraft/core/src/main/java/com/wildernessnation/statecraft/core/model/WorldState.java`
- Create: `mods/statecraft/core/src/main/java/com/wildernessnation/statecraft/core/gen/WorldGenerator.java`
- Test: `mods/statecraft/core/src/test/java/com/wildernessnation/statecraft/core/gen/WorldGeneratorTest.java`

- [ ] **Step 1: 写失败的测试**

Create `WorldGeneratorTest.java`：
```java
package com.wildernessnation.statecraft.core.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class WorldGeneratorTest {

    private static final List<Culture> CULTURES = List.of(
            new Culture("bandit", "匪帮", List.of(
                    "赤沙", "黑岩", "铁蹄", "灰狼", "断刃", "荒骨")),
            new Culture("desert_raider", "沙盗", List.of(
                    "白岩", "烈日", "黄沙", "孤驼", "枯井", "长夜")));

    private static WorldState generate(long seed, StatecraftConfig cfg) {
        return new WorldGenerator(cfg, seed)
                .generate(CULTURES, 0.0, 0.0, "landing", 0);
    }

    @Test
    void generatesRequestedNumberOfNations() {
        WorldState s = generate(1L, StatecraftConfig.defaults());
        assertEquals(12, s.nations().size());
    }

    @Test
    void respectsMinimumSpacingBetweenNations() {
        StatecraftConfig cfg = StatecraftConfig.defaults();
        WorldState s = generate(7L, cfg);
        List<Nation> ns = s.nations();
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                double d = Math.hypot(ns.get(i).x() - ns.get(j).x(), ns.get(i).z() - ns.get(j).z());
                assertTrue(d >= cfg.minNationSpacing(),
                        "国家 " + i + " 与 " + j + " 距离 " + d + " 小于 " + cfg.minNationSpacing());
            }
        }
    }

    @Test
    void sizeStaysInConfiguredRange() {
        WorldState s = generate(3L, StatecraftConfig.defaults());
        for (Nation n : s.nations()) {
            assertTrue(n.size() >= 1 && n.size() <= 10, "规模越界：" + n.size());
        }
    }

    @Test
    void bigNationsAreLessDeveloped() {
        // 反比公式是纯函数，直接断言它（比统计相关性更稳）
        StatecraftConfig cfg = StatecraftConfig.defaults();
        assertEquals(95.0, WorldGenerator.developmentFor(1, 0.0, cfg), 0.0);
        assertEquals(41.0, WorldGenerator.developmentFor(10, 0.0, cfg), 0.0); // 95 - 6*9
        assertTrue(WorldGenerator.developmentFor(10, 0.0, cfg)
                < WorldGenerator.developmentFor(1, 0.0, cfg));
        assertEquals(95.0, WorldGenerator.developmentFor(1, 8.0, cfg), 0.0, "上限钳到 95");
        assertEquals(33.0, WorldGenerator.developmentFor(10, -8.0, cfg), 0.0, "41 - 8 = 33");
    }

    @Test
    void stanceTracksDevelopment() {
        assertTrue(WorldGenerator.stanceFor(90.0, 0.0) < 0.0, "先进国家偏主和");
        assertTrue(WorldGenerator.stanceFor(20.0, 0.0) > 0.0, "落后国家偏主战");
    }

    @Test
    void nationNearestToSpawnIsPacific() {
        for (long seed = 0; seed < 40; seed++) {
            WorldState s = generate(seed, StatecraftConfig.defaults());
            Nation nearest = s.nations().stream()
                    .min((a, b) -> Double.compare(Math.hypot(a.x(), a.z()), Math.hypot(b.x(), b.z())))
                    .orElseThrow();
            assertTrue(nearest.stance() <= 0.0,
                    "seed " + seed + " 的最近国家 " + nearest.name() + " 倾向 " + nearest.stance());
        }
    }

    @Test
    void namesAndIdsAreUnique() {
        WorldState s = generate(11L, StatecraftConfig.defaults());
        assertEquals(s.nations().size(),
                s.nations().stream().map(Nation::name).collect(Collectors.toSet()).size());
        assertEquals(s.nations().size(),
                s.nations().stream().map(Nation::id).collect(Collectors.toSet()).size());
    }

    @Test
    void sameSeedProducesIdenticalWorld() {
        WorldState a = generate(2026L, StatecraftConfig.defaults());
        WorldState b = generate(2026L, StatecraftConfig.defaults());
        assertEquals(a.nations(), b.nations());
        assertEquals(a.seed(), b.seed());
    }

    @Test
    void differentSeedProducesDifferentWorld() {
        WorldState a = generate(1L, StatecraftConfig.defaults());
        WorldState b = generate(2L, StatecraftConfig.defaults());
        assertNotEquals(a.nations(), b.nations());
    }

    @Test
    void culturesComesFromProvidedPool() {
        WorldState s = generate(5L, StatecraftConfig.defaults());
        Set<String> allowed = CULTURES.stream().map(Culture::id).collect(Collectors.toSet());
        for (Nation n : s.nations()) {
            assertTrue(allowed.contains(n.cultureId()), "未知文化：" + n.cultureId());
        }
    }
}
```

- [ ] **Step 2: 跑测试，确认它失败**

Run:
```powershell
cd D:\project\nation-pack\mods\statecraft
.\gradlew.bat :core:test --tests "*WorldGeneratorTest*" --console=plain
```
Expected: 编译失败（`cannot find symbol: class Culture`）

- [ ] **Step 3: 写模型三个文件**

Create `Culture.java`：
```java
package com.wildernessnation.statecraft.core.model;

import java.util.List;

/**
 * 文化：决定国名池与外观（外观到 mod 层才用得上，core 不关心）。
 *
 * @param id            稳定标识（bandit / desert_raider / 自造）
 * @param displayName   中文显示名
 * @param namePrefixes  国名前缀池（保证全局唯一由生成器负责）
 */
public record Culture(String id, String displayName, List<String> namePrefixes) {

    public Culture {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Culture.id 不能为空");
        }
        if (namePrefixes == null || namePrefixes.isEmpty()) {
            throw new IllegalArgumentException("Culture.namePrefixes 不能为空：" + id);
        }
        namePrefixes = List.copyOf(namePrefixes);
    }
}
```

Create `Nation.java`：
```java
package com.wildernessnation.statecraft.core.model;

/**
 * 一个国家（不可变快照）。
 *
 * <p>坐标用 double 存世界坐标；这是<strong>抽象位置</strong>，不等于"已经放了建筑"——
 * 实体化由 mod 层负责。
 */
public record Nation(
        String id,
        String name,
        String cultureId,
        int size,
        double development,
        double stance,
        double military,
        double treasury,
        double x,
        double z,
        boolean met,
        double attitudeToParty) {

    public Nation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Nation.id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nation.name 不能为空：" + id);
        }
        if (size < 1 || size > 10) {
            throw new IllegalArgumentException("规模必须 1..10：" + size);
        }
        if (development < 0.0 || development > 100.0) {
            throw new IllegalArgumentException("发展度必须 0..100：" + development);
        }
        if (stance < -100.0 || stance > 100.0) {
            throw new IllegalArgumentException("政治倾向必须 -100..100：" + stance);
        }
        if (attitudeToParty < -100.0 || attitudeToParty > 100.0) {
            throw new IllegalArgumentException("态度必须 -100..100：" + attitudeToParty);
        }
    }

    /** 距世界原点的水平距离（出生点默认在原点附近时可直接比较）。 */
    public double distanceTo(double ox, double oz) {
        return Math.hypot(x - ox, z - oz);
    }

    public Nation withPosition(double nx, double nz) {
        return new Nation(id, name, cultureId, size, development, stance,
                military, treasury, nx, nz, met, attitudeToParty);
    }
}
```

Create `WorldState.java`：
```java
package com.wildernessnation.statecraft.core.model;

import java.util.List;

/**
 * 世界状态（本计划只含生成所需的字段）。
 *
 * <p>后续计划会往里加：relations / events / letters / buildings / domains。
 * 现在刻意不加，避免写出没人用的字段。
 */
public record WorldState(
        long seed,
        List<Nation> nations,
        String eraId,
        int eraOrdinal,
        long seq) {

    public WorldState {
        nations = List.copyOf(nations);
    }

    public WorldState withNations(List<Nation> replaced) {
        return new WorldState(seed, replaced, eraId, eraOrdinal, seq);
    }
}
```

- [ ] **Step 4: 写 `WorldGenerator`**

Create `WorldGenerator.java`：
```java
package com.wildernessnation.statecraft.core.gen;

import com.wildernessnation.statecraft.core.config.StatecraftConfig;
import com.wildernessnation.statecraft.core.model.Culture;
import com.wildernessnation.statecraft.core.model.Nation;
import com.wildernessnation.statecraft.core.model.WorldState;
import com.wildernessnation.statecraft.core.rng.DeterministicRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 开局生成：布点 → 规模 → 发展度 → 政治倾向 → 国名。
 *
 * <p>两条硬规则（来自设计）：
 * <ol>
 *   <li><strong>规模与发展度反比</strong>：大而落后、小而先进；倾向由发展度推出（落后更主战）</li>
 *   <li><strong>离出生点最近的国家必须主和</strong>，且是"从候选里挑"而不是"改它的数值"</li>
 * </ol>
 */
public final class WorldGenerator {

    private final StatecraftConfig cfg;
    private final long seed;
    private final DeterministicRandom rng;

    public WorldGenerator(StatecraftConfig cfg, long seed) {
        this.cfg = cfg.validated();
        this.seed = seed;
        this.rng = new DeterministicRandom(seed);
    }

    /** 发展度：与规模反比。纯函数，方便直接断言。 */
    public static double developmentFor(int size, double jitter, StatecraftConfig cfg) {
        double raw = cfg.devBase() - cfg.devPerSize() * (size - 1) + jitter;
        return Math.max(cfg.devMin(), Math.min(cfg.devMax(), raw));
    }

    /** 政治倾向：越落后越主战。正值 = 主战，负值 = 主和。 */
    public static double stanceFor(double development, double jitter) {
        double raw = 60.0 - development + jitter;
        return Math.max(-100.0, Math.min(100.0, raw));
    }

    public WorldState generate(
            List<Culture> cultures,
            double spawnX,
            double spawnZ,
            String startingEraId,
            int startingEraOrdinal) {
        if (cultures == null || cultures.isEmpty()) {
            throw new IllegalArgumentException("至少要有一个文化");
        }
        List<double[]> points = placeNations(spawnX, spawnZ);
        List<String> names = pickNames(cultures, points.size());

        List<Nation> nations = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            Culture culture = cultures.get(rng.nextInt(i, "culture#" + i, cultures.size()));
            int size = rng.range(i, "size#" + i, cfg.sizeMin(), cfg.sizeMax());
            double devJitter = rng.rangeDouble(i, "devJit#" + i, -cfg.devJitter(), cfg.devJitter());
            double development = developmentFor(size, devJitter, cfg);
            double stanceJitter =
                    rng.rangeDouble(i, "stanceJit#" + i, -cfg.stanceJitter(), cfg.stanceJitter());
            double stance = stanceFor(development, stanceJitter);
            double military = 10.0 + size * 2.0;
            double treasury = 20.0 + development * 0.5;
            nations.add(new Nation(
                    "n" + i,
                    names.get(i),
                    culture.id(),
                    size,
                    development,
                    stance,
                    military,
                    treasury,
                    points.get(i)[0],
                    points.get(i)[1],
                    false,
                    0.0));
        }

        nations = ensureNearestIsPacific(nations, spawnX, spawnZ);
        return new WorldState(seed, nations, startingEraId, startingEraOrdinal, 0L);
    }

    /**
     * 最近的国家必须主和：找一个倾向 ≤ 0 的国家与它<strong>交换位置</strong>。
     * 交换而不是改数值——否则"发展度决定倾向"的公式就被破坏了。
     */
    private static List<Nation> ensureNearestIsPacific(
            List<Nation> nations, double spawnX, double spawnZ) {
        int nearest = 0;
        for (int i = 1; i < nations.size(); i++) {
            if (nations.get(i).distanceTo(spawnX, spawnZ)
                    < nations.get(nearest).distanceTo(spawnX, spawnZ)) {
                nearest = i;
            }
        }
        if (nations.get(nearest).stance() <= 0.0) {
            return nations;
        }
        for (int j = 0; j < nations.size(); j++) {
            if (nations.get(j).stance() <= 0.0) {
                Nation a = nations.get(nearest);
                Nation b = nations.get(j);
                nations.set(nearest, a.withPosition(b.x(), b.z()));
                nations.set(j, b.withPosition(a.x(), a.z()));
                return nations;
            }
        }
        return nations;
    }

    /**
     * 布点：极坐标 + 拒绝采样，保证两两间距 ≥ minNationSpacing。
     * 用固定 seed → 同 seed 布点完全一致。
     */
    private List<double[]> placeNations(double spawnX, double spawnZ) {
        List<double[]> out = new ArrayList<>();
        int maxAttempts = cfg.nationCount() * 400;
        double rMin = cfg.minPlacementRadius();
        double rMax = cfg.maxPlacementRadius();

        for (int attempt = 0; attempt < maxAttempts && out.size() < cfg.nationCount(); attempt++) {
            double angle = rng.rangeDouble(attempt, "angle#" + attempt, 0.0, Math.PI * 2.0);
            double radius = rng.rangeDouble(attempt, "radius#" + attempt, rMin, rMax);
            double x = spawnX + Math.cos(angle) * radius;
            double z = spawnZ + Math.sin(angle) * radius;

            boolean ok = true;
            for (double[] p : out) {
                if (Math.hypot(p[0] - x, p[1] - z) < cfg.minNationSpacing()) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                out.add(new double[] {x, z});
            }
        }
        if (out.size() < cfg.nationCount()) {
            throw new IllegalStateException(
                    "布点失败：在 " + maxAttempts + " 次尝试内只放下 " + out.size()
                            + " 个国家（间距要求 " + cfg.minNationSpacing() + " 格太严？）");
        }
        return out;
    }

    /** 国名：跨文化取，遇到重名就换下一个候选，保证全局唯一。 */
    private List<String> pickNames(List<Culture> cultures, int count) {
        List<String> pool = new ArrayList<>();
        for (Culture c : cultures) {
            pool.addAll(c.namePrefixes());
        }
        if (pool.size() < count) {
            throw new IllegalStateException(
                    "国名池不够：" + pool.size() + " 个候选放不下 " + count + " 个国家");
        }
        List<String> picked = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int start = rng.nextInt(i, "name#" + i, pool.size());
            for (int step = 0; step < pool.size(); step++) {
                String candidate = pool.get((start + step) % pool.size());
                if (used.add(candidate)) {
                    picked.add(candidate);
                    break;
                }
            }
        }
        if (picked.size() < count) {
            throw new IllegalStateException("国名唯一性无法满足：只取到 " + picked.size());
        }
        return picked;
    }
}
```

- [ ] **Step 5: 跑测试，确认通过**

Run:
```powershell
.\gradlew.bat :core:test --tests "*WorldGeneratorTest*" --console=plain
```
Expected: `BUILD SUCCESSFUL`，10 个测试全 `PASSED`

- [ ] **Step 6: 跑整个 core 测试套件**

Run:
```powershell
.\gradlew.bat :core:test --console=plain
```
Expected: `BUILD SUCCESSFUL`，共 30 个测试（7 + 5 + 8 + 10）全通过

- [ ] **Step 7: 提交**

```powershell
cd D:\project\nation-pack
git add mods/statecraft/core/src
git commit -m "feat(core): 开局生成 —— 布点/规模/发展度反比/倾向/最近国主和"
```

---

## Task 6: 收尾 —— 文档同步

**Files:**
- Modify: `NATIONS.md`（§十四 风险 1 的结论、§十七 里程碑 M1 打勾）
- Create: `mods/statecraft/README.md`

- [ ] **Step 1: 更新 `NATIONS.md` 里程碑**

把 §十七 表格里 **M1** 那一行的验收列改成：`✅ core 骨架完成（30 个 JUnit 用例全绿，纯离线，无需开游戏）`，并保留 M0 的结论。

- [ ] **Step 2: 写 mod 的 README（给以后的自己/别人）**

Create `mods/statecraft/README.md`：
```markdown
# Statecraft（邦交）· 源码

《荒野建国》的配套 mod。设计文档在仓库根的 **`NATIONS.md`**。

## 模块

| 模块 | 内容 | 是否需要 Minecraft |
|---|---|---|
| `core` | 全部规则：生成、结算、外交状态机、环境判定、文本模板 | **不需要**（纯 Java，可用 JUnit 跑）|
| `mod`（后续计划）| NeoForge 适配：SavedData、方块、实体、界面 | 需要 |

**为什么这样切**：core 不 import 任何 Minecraft 类 → 几个时代的推演可以在命令行里一次跑完，
不需要开游戏、不需要服务器。这是"少让人工测试"的技术前提。

## 构建与测试

```powershell
cd mods/statecraft
.\gradlew.bat :core:test --console=plain
```

## 加内容的入口（数据驱动）

| 想加什么 | 改哪里 |
|---|---|
| 一个时代 | `eras.json`（数量、时长、系数、解锁项都是数据）|
| 一个国家名 | `nations.json` 的国名池 |
| 一座建筑与它的村民 | `buildings.json` + `staff.json`（后续计划）|
| 一条条约 / 一个事件 / 一封国书 | `treaties.json` / `events/*.json` / `letters/*.json` |

## 已知环境约束

- 本机 **没有安装 Gradle**，用的是 `~/.gradle/wrapper/dists` 里缓存的分发；
  仓库里带了 `gradlew`，所以照常用 `.\gradlew.bat` 即可。
- **`maven.neoforged.net` 不可达**（直连与代理均超时）→ `mod` 模块（需要 NeoForge 的
  Gradle 插件与 userdev 产物）**在解决网络前无法构建**。core 不受影响。
```

- [ ] **Step 3: 提交**

```powershell
cd D:\project\nation-pack
git add NATIONS.md mods/statecraft/README.md
git commit -m "docs(statecraft): core 完成 + mod 源码 README + 环境约束"
```

---

## 后续计划（各自单独写，不在本计划里）

| 计划 | 内容 | 前置 |
|---|---|---|
| **计划 2** | `mod` 模块骨架（NeoForge MDK）+ 持久化（SavedData / schema 与 era 迁移 / 事务日志）+ 命令 | ⚠️ **需先解决 `maven.neoforged.net` 可达性** |
| **计划 3** | 结算引擎（时代推进 / 国家战争 / 吸收与扩张疲劳 / 态度回归）+ 外交状态机 + 国家对玩家压力 | 计划 2 |
| **计划 4** | 建筑工 NPC + 图纸授权 + 情报站 + 文书村民 + 周报 + 国书 | 计划 2 |
| **计划 5** | 夺城环节（旗座 / 守军 / 指挥官 / 百姓隐藏恢复 / 占领碑）| 计划 4 + M0 结论 |

**阻塞与建议**：`maven.neoforged.net` 现在连不上（本机之前用 Clash 代理 `127.0.0.1:7897`，该端口当前没在监听）。
**建议在开始计划 2 之前把代理开起来再试一次**；若仍不通，退路是用 Maven + 本地 `libraries` 做离线编译（工作量大，届时另议）。

---

## 计划自检记录

**1. Spec 覆盖（对照 `NATIONS.md`）**

| Spec 要求 | 本计划的落点 |
|---|---|
| §七 时代数据驱动、不写死数量、未知 id 归位 | Task 4（含 3/6/9 三套表的测试）|
| §七 三种触发（时代/小结算/玩家动作） | 计划 3（结算引擎）|
| §六 开局生成（数量/间距/规模/发展度反比/倾向/最近国主和/国名唯一/文化） | Task 5（10 个用例逐条断言）|
| §十二 默认值全部走配置 | Task 3 |
| "同 seed 同输入必得同结果" | Task 2 + Task 5 的 `sameSeedProducesIdenticalWorld` |
| §十四 风险 1（能否刷 HYW 单位） | Task 0（含退路）|
| §四 架构（core 零 MC 依赖） | Task 1 的模块划分 + README |
| 环境阻塞（NeoForge maven） | 前提表 + 后续计划 |

**2. 占位符扫描**：无 TBD / TODO / "稍后补"；每个代码步骤都给了完整可编译代码；每条命令都带期望输出。

**3. 类型一致性**：`Era(id,name,ordinal,durationHours,coefficient,unlocks)` ·
`EraTable.currentFor/isUnlocked/byId/byOrdinal/fallbackForUnknownId` ·
`StatecraftConfig` 的字段名在测试与实现中一致 ·
`WorldGenerator.developmentFor/stanceFor/generate` ·
`Nation.withPosition/distanceTo` · `WorldState.withNations` —— 全部在测试与实现中出现同名同签名。

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

// 源码里有中文（国名/事件文本），而 Gradle 默认用**平台编码**读源码 ——
// 中文 Windows 上是 GBK → UTF-8 源码被错误解码 → 字符串损坏（2026-09-19 实测踩到，
// 与 CONFLICTS.md 的 C14 是同一类"编码"坑）。必须显式锁 UTF-8。
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    systemProperty("file.encoding", "UTF-8")
    defaultCharacterEncoding = "UTF-8"
}

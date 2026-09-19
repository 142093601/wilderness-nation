pluginManagement {
    repositories {
        // ModDevGradle 的插件标记只在 NeoForged 的 maven 上
        gradlePluginPortal()
        maven {
            name = "NeoForged"
            url = uri("https://maven.neoforged.net/releases")
        }
    }
}

rootProject.name = "statecraft"
include("core")
include("mod")

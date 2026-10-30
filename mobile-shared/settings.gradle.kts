pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        kotlin("multiplatform") version "2.4.10"
        kotlin("plugin.serialization") version "2.4.10"
        id("org.jetbrains.compose") version "1.10.3"
        id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "AyudixMobileShared"
include(":core", ":ui")

plugins { kotlin("multiplatform"); id("org.jetbrains.compose"); id("org.jetbrains.kotlin.plugin.compose") }
kotlin {
    jvmToolchain(21)
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "AyudixShared"
            isStatic = true
            export(project(":core"))
            binaryOption("bundleId", "pe.ayudix.shared")
        }
    }
    sourceSets {
        commonMain.dependencies {
            api(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        }
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("io.ktor:ktor-client-mock:3.3.3")
        }
    }
}
val jvmMainCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
tasks.register<JavaExec>("renderPreview") {
    dependsOn("jvmMainClasses")
    mainClass.set("pe.ayudix.shared.ui.PreviewKt")
    classpath(jvmMainCompilation.output.allOutputs, jvmMainCompilation.runtimeDependencyFiles)
    jvmArgs("-Djava.awt.headless=true")
    args(providers.gradleProperty("ayudixPreviewDir").getOrElse(layout.buildDirectory.dir("preview").get().asFile.absolutePath))
}

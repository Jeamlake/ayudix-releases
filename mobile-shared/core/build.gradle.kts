plugins { kotlin("multiplatform"); kotlin("plugin.serialization") }
kotlin {
    jvmToolchain(21)
    jvm()
    iosArm64()
    iosSimulatorArm64()
    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            api("io.ktor:ktor-client-core:3.3.3")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
            implementation("io.ktor:ktor-client-mock:3.3.3")
        }
        jvmMain.dependencies { implementation("io.ktor:ktor-client-cio:3.3.3") }
        iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.3.3") }
    }
}

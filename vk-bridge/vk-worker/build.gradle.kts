import java.net.URI

// Isolated in its own gradle module because SQLDelight can't create database in jvmMain despite
// having module name in its tasks (commonMain in my case, but all .sq files in jvmMain - looks like
// commonMain is hardcoded into task name)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "ru.herobrine1st.matrix"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = URI("https://git.herobrine1st.ru/api/packages/HeroBrine1st/maven")
        name = "forgejo"
    }
}

kotlin {
    jvmToolchain(17)

    jvm()
    // a workaround to isolate commonMain module dependency scope into commonMain
    // without that, you can freely use java-only libraries in common module and get a big surprise if you move on
//    linuxX64()

    sourceSets {
        commonMain.dependencies {
            api(libs.trixnity.bridge)
            api(projects.vkLibrary)

            implementation(libs.ktor.server.core)

            implementation(libs.ktor.server.resources)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.logging)

            implementation(libs.sqldelight.extensions.coroutines)
            implementation(libs.sqldelight.extensions.async)

            implementation(libs.kotlinLogging)
        }
    }
}

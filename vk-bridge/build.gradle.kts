import java.net.URI

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.ktor)
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

application {
    mainClass.set("ru.herobrine1st.matrix.bridge.MainKt")
}

dependencies {
    implementation(projects.vkBridge.vkWorker)
    implementation(libs.sqldelight.extensions.coroutines)
    implementation(libs.sqldelight.extensions.async)
    implementation(libs.sqldelight.driver.r2dbc)

    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.callLogging)

    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinLogging)

    implementation(libs.r2dbc.postgresql)
    implementation(libs.r2dbc.pool)
    implementation(libs.sql4j.simple)
}

kotlin {
    jvmToolchain(17)
}

sqldelight {
    databases {
        create("Database") {
            packageName.set("ru.herobrine1st.matrix.bridge")
            dialect(libs.sqldelight.dialect.postgresql)
            generateAsync.set(true)
        }
    }
}
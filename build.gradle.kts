plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.ktor) apply false
}

group = "ru.herobrine1st.matrix"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

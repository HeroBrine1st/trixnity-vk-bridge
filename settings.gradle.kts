plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}


rootProject.name = "trixnity-vk-bridge"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include("vk-library")
include("vk-bridge")
include("vk-bridge:vk-worker")
findProject(":vk-bridge:vk-worker")?.name = "vk-worker"

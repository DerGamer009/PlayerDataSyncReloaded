pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
    }
}

plugins {
    // Auto-provisions missing JDKs (needed for MC 26.x toolchain = Java 25) via Foojay Disco API.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "PlayerDataSyncReloaded"

include("api")
include("common")
include("plugin")
include("velocity")

// Version modules (Paper NMS handlers + Fabric/Forge adapters per line)
val versionModules = listOf(
    "v1_20_R1", "v1_21_R1", "v26_1_R1", "v26_2_R1"
)

// ForgeGradle 6.0.x cannot run on Gradle 9+ (hard version check). Opt in with -Ppds.enableForge=true
// once ForgeGradle publishes a Gradle-9-compatible release.
val enableForge = (extra.properties["pds.enableForge"] as String?)?.toBoolean() ?: false

versionModules.forEach {
    include("versions:$it")
    include("fabric-versions:$it")
    if (enableForge) {
        include("forge-versions:$it")
    }
}

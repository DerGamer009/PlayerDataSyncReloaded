plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "PlayerDataSyncReloaded"

include("api")
include("common")
include("plugin")

// Version modules
val versionModules = listOf(
    "v1_8_R3", "v1_9_R2", "v1_10_R1", "v1_11_R1", "v1_12_R1",
    "v1_13_R2", "v1_14_R1", "v1_15_R1", "v1_16_R3", "v1_17_R1",
    "v1_18_R2", "v1_19_R3", "v1_20_R1", "v1_21_R1", "v26_1_R1"
)

versionModules.forEach {
    include("versions:$it")
}

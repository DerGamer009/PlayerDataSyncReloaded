plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "PlayerDataSyncReloaded"

include("api")
include("common")
include("plugin")

// Version modules
val versionModules = listOf(
    "v1_20_R1", "v1_21_R1", "v26_1_R1"
)

versionModules.forEach {
    include("versions:$it")
}

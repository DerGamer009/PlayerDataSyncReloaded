// Fabric line for Minecraft 26.2. Yarn mappings from MC 1.21.11 are reused (no native 26.x mappings exist).
plugins {
    id("fabric-loom") version "1.17.12"
}

base {
    archivesName.set("playerdatasync-fabric")
}

loom {
    // Cross-mapping (1.21.11 yarn on MC 26.2) trips Mercury during source remap. We don't need remapped sources for the build.
    enableModProvidedJavadoc.set(false)
    // Same story: cross-mapping breaks the Minecraft-source decompilation task (`genSourcesWithCfr`). Not needed for the build.
    decompilers { clear() }
}

dependencies {
    minecraft("com.mojang:minecraft:26.2")
    mappings("net.fabricmc:yarn:1.21.11+build.6:v2")
    modImplementation("net.fabricmc:fabric-loader:0.19.3")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.153.0+26.2")

    implementation(project(":api"))
    implementation(project(":common"))
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

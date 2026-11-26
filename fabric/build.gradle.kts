// The single Fabric jar: bootstrap + every :fabric-versions:* adapter (remapped by Loom) + common/api and
// the storage libraries. Minecraft already ships Gson and SLF4J, so those stay out.
import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
    id("com.gradleup.shadow") version "8.3.10"
}

val adapters = listOf("v1_20_R1", "v1_21_R1", "v26_1_R1", "v26_2_R1")

dependencies {
    compileOnly("net.fabricmc:fabric-loader:0.15.11")
    implementation(project(":api"))
    implementation(project(":common"))
}

tasks {
    processResources {
        inputs.property("version", project.version)
        filesMatching("fabric.mod.json") { expand("version" to project.version) }
    }

    jar { enabled = false }

    shadowJar {
        archiveBaseName.set("PlayerDataSyncReloaded-Fabric")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
        mergeServiceFiles()

        dependencies {
            exclude(dependency("com.google.code.gson:gson"))
            exclude(dependency("org.slf4j:.*"))
        }

        adapters.forEach { adapter ->
            val remap = project(":fabric-versions:$adapter").tasks.named<AbstractArchiveTask>("remapJar")
            dependsOn(remap)
            from(remap.flatMap { it.archiveFile }.map { zipTree(it) }) {
                // Only the adapter classes: the bundle has its own fabric.mod.json and manifest.
                include("de/craftingstudiopro/playerDataSyncReloaded/fabric/$adapter/**")
            }
        }
    }

    val copyJar by registering(Copy::class) {
        from(shadowJar)
        into(rootProject.layout.buildDirectory.dir("libs"))
    }

    assemble { dependsOn(shadowJar) }
    build { dependsOn(copyJar) }
}

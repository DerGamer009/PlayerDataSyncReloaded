import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

plugins {
    id("xyz.jpenilla.run-paper") version "2.3.1"
    // 8.1.x uses ASM that cannot read Java 25 bytecode (major 69); GradleUp Shadow bundles current ASM.
    id("com.gradleup.shadow") version "8.3.10"
}

val fabricBundle =
    (rootProject.findProperty("pds.fabric.bundle") as String?)?.takeIf { it.isNotBlank() } ?: "v1_20_R1"
val forgeBundle =
    (rootProject.findProperty("pds.forge.bundle") as String?)?.takeIf { it.isNotBlank() } ?: "v1_20_R1"
val enableForge =
    (rootProject.findProperty("pds.enableForge") as String?)?.toBoolean() ?: false

dependencies {
    implementation(project(":api"))
    implementation(project(":common"))

    val versionModules = listOf(
        "v1_20_R1", "v1_21_R1", "v26_1_R1", "v26_2_R1"
    )

    versionModules.forEach {
        implementation(project(":versions:$it"))
    }

    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("dev.faststats.metrics:bukkit:0.22.0")
    implementation("org.jetbrains:annotations:24.1.0")
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}

tasks {
    shadowJar {
        // Paper plugin + embedded sibling JARs under bundled/ (Velocity / Fabric / Forge), not merged.
        archiveBaseName.set("PlayerDataSyncReloaded")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
        relocate("org.bstats", "de.craftingstudiopro.playerDataSyncReloaded.bstats")
        mergeServiceFiles()
        // Paper 1.21.x PluginRemapper uses ASM that cannot read MR-JAR stacks (e.g. class file 69 under META-INF/versions/).
        exclude("META-INF/versions/**")

        val forgeDeps = if (enableForge)
            listOf(project(":forge-versions:$forgeBundle").tasks.named("reobfJar")) else emptyList()

        dependsOn(
            listOf(
                project(":velocity").tasks.named("jar"),
                project(":fabric-versions:$fabricBundle").tasks.named("remapJar"),
                project(":fabric-versions:$fabricBundle").tasks.named("checkFabricModMetadata"),
            ) + forgeDeps
        )

        from(project(":velocity").tasks.named<Jar>("jar").flatMap { it.archiveFile }) {
            into("bundled")
            rename { _: String -> "playerdatasync-velocity.jar" }
        }
        from(project(":fabric-versions:$fabricBundle").tasks.named<AbstractArchiveTask>("remapJar").flatMap { it.archiveFile }) {
            into("bundled")
            rename { _: String -> "playerdatasync-fabric.jar" }
        }
        if (enableForge) {
            from(project(":forge-versions:$forgeBundle").tasks.named<Jar>("jar").flatMap { it.archiveFile }) {
                into("bundled")
                rename { _: String -> "playerdatasync-forge.jar" }
            }
        }
    }

    val copyJar by registering(Copy::class) {
        from(shadowJar)
        into(rootProject.layout.buildDirectory.dir("libs"))
    }

    build {
        dependsOn(copyJar)
    }

    runServer { minecraftVersion("26.1.2") }
    processResources {
        inputs.property("version", project.version)
        val props = mapOf("version" to project.version)
        filesMatching("plugin.yml") { expand(props) }
    }
}

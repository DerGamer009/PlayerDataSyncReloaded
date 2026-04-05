plugins {
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.4.1"
}

dependencies {
    implementation(project(":api"))
    implementation(project(":common"))

    val versionModules = listOf(
        "v1_8_R3", "v1_9_R2", "v1_10_R1", "v1_11_R1", "v1_12_R1",
        "v1_13_R2", "v1_14_R1", "v1_15_R1", "v1_16_R3", "v1_17_R1",
        "v1_18_R2", "v1_19_R3", "v1_20_R1", "v1_21_R1"
    )

    versionModules.forEach {
        implementation(project(":versions:$it"))
    }

    implementation("org.bstats:bstats-bukkit:3.1.0")
    implementation("org.jetbrains:annotations:24.1.0")
    compileOnly("org.spigotmc:spigot-api:1.10.2-R0.1-SNAPSHOT")
}

tasks {
    shadowJar {
        archiveBaseName.set("PlayerDataSyncReloaded")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
        relocate("org.bstats", "de.craftingstudiopro.playerDataSyncReloaded.bstats")
        mergeServiceFiles()
    }
    build { dependsOn(shadowJar) }
    runServer { minecraftVersion("26.1.1") }
    processResources {
        val props = mapOf("version" to project.version)
        filesMatching("plugin.yml") { expand(props) }
    }
}

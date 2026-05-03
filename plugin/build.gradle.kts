plugins {
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("com.gradleup.shadow") version "9.4.1"
}

dependencies {
    implementation(project(":api"))
    implementation(project(":common"))

    val versionModules = listOf(
        "v1_20_R1", "v1_21_R1", "v26_1_R1"
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
        archiveBaseName.set("PlayerDataSyncReloaded")
        archiveClassifier.set("")
        archiveVersion.set(project.version.toString())
        relocate("org.bstats", "de.craftingstudiopro.playerDataSyncReloaded.bstats")
        mergeServiceFiles()
    }
    build { dependsOn(shadowJar) }
    runServer { minecraftVersion("26.1.2") }
    processResources {
        val props = mapOf("version" to project.version)
        filesMatching("plugin.yml") { expand(props) }
    }
}

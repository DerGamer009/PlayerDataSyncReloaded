// Forge line aligned with Paper `v26_2_R1` (stub; Forge for MC 26.x not yet released); same MC/Forge as v1_21 until a dedicated 26.x game stack ships.
plugins {
    id("net.minecraftforge.gradle") version "6.0.36"
}

base {
    archivesName.set("PlayerDataSync")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

minecraft {
    mappings("official", "1.21.4")
}

dependencies {
    minecraft("net.minecraftforge:forge:1.21.4-54.1.15")

    implementation(project(":api"))
    implementation(project(":common"))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val expandProps = mapOf("mod_version" to project.version)
    inputs.properties(expandProps)
    filesMatching("META-INF/mods.toml") {
        expand(expandProps)
    }
}


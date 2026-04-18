plugins {
    `java-library`
}

allprojects {
    group = "de.craftingstudiopro"
    version = properties["version"] ?: "26.4"

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }

    apply(plugin = "java-library")
}

allprojects {
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }
}

subprojects {
    val moduleName = name
    
    val javaReleaseVersion = when {
        moduleName == "plugin" || moduleName == "api" || moduleName == "common" -> 25 // Build core with latest
        moduleName.startsWith("v1_20_") || moduleName.startsWith("v1_21_") -> 21
        moduleName.startsWith("v26_1_") -> 25
        else -> 21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(javaReleaseVersion)
    }

    configurations.matching { it.name.contains("Classpath") }.configureEach {
        if (isCanBeResolved) {
            attributes {
                attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
            }
        }
    }
}

plugins {
    `java-library`
}

allprojects {
    group = "de.craftingstudiopro"
    version = "26.4.1-SNAPSHOT"

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
        moduleName == "plugin" || moduleName == "api" || moduleName == "common" ||
        moduleName.startsWith("v1_8_") || moduleName.startsWith("v1_9_") ||
        moduleName.startsWith("v1_10_") || moduleName.startsWith("v1_11_") ||
        moduleName.startsWith("v1_12_") || moduleName.startsWith("v1_13_") ||
        moduleName.startsWith("v1_14_") || moduleName.startsWith("v1_15_") ||
        moduleName.startsWith("v1_16_") -> 8
        
        moduleName.startsWith("v1_17_") -> 16
        moduleName.startsWith("v1_18_") || moduleName.startsWith("v1_19_") -> 17
        moduleName.startsWith("v1_20_") || moduleName.startsWith("v1_21_") -> 21
        moduleName.startsWith("v26_1_") -> 25
        else -> 8
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

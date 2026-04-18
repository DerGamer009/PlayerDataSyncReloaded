import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    `maven-publish`
}

allprojects {
    group = "de.playerdatasync"
    version = properties["version"] ?: "26.4"

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.dergamer09.at/releases")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }

    apply(plugin = "java-library")
    
    dependencies {
        compileOnly("org.jetbrains:annotations:26.0.1")
    }
}

// Configure publishing for subprojects only
subprojects {
    // Only apply to code modules
    if (name != "versions" && project.parent?.name != "PlayerDataSyncReloaded" || name == "api" || name == "common" || name == "plugin") {
        apply(plugin = "maven-publish")
        
        afterEvaluate {
            configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("pds") {
                        // Special handling for the shaded plugin module
                        if (this@subprojects.name == "plugin") {
                            artifact(tasks.named("shadowJar"))
                        } else {
                            from(components["java"])
                        }
                        
                        artifactId = when {
                            this@subprojects.name == "api" -> "api"
                            this@subprojects.name == "common" -> "common"
                            this@subprojects.name == "plugin" -> "plugin"
                            else -> "adapter-${this@subprojects.name}"
                        }
                    }
                }
                repositories {
                    maven {
                        name = "Reposilite"
                        url = uri("https://repo.dergamer09.at/releases")
                        credentials {
                            username = project.findProperty("reposilite_username")?.toString() ?: ""
                            password = project.findProperty("reposilite_password")?.toString() ?: ""
                        }
                    }
                }
            }
        }
    }
}

allprojects {
    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }
}

subprojects {
    val moduleName = name
    
    val javaReleaseVersion = when {
        moduleName == "api" || moduleName == "common" -> 21 
        moduleName == "plugin" -> 25
        moduleName.startsWith("v1_20_") || moduleName.startsWith("v1_21_") -> 21
        moduleName.startsWith("v26_1_") -> 25
        else -> 21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(javaReleaseVersion)
    }
}

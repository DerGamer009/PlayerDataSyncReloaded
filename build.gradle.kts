import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-library`
    `maven-publish`
}

allprojects {
    group = "de.playerdatasync"
    version = properties["version"] ?: "26.6-Release"

    repositories {
        mavenLocal()
        mavenCentral()
        // maven("https://repo.dergamer09.at/releases") // Disabled: DNS not resolving locally
        maven("https://repo.faststats.dev/releases")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.codemc.io/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.minecraftforge.net/")
    }

    val skipJavaLibrary =
        project.path.startsWith(":fabric-versions:") || project.path.startsWith(":forge-versions:")
    if (!skipJavaLibrary) {
        apply(plugin = "java-library")
        
        dependencies {
            "compileOnly"("org.jetbrains:annotations:26.0.1")
        }
    }
}

// Configure publishing for subprojects only
subprojects {
    // Only apply to code modules
    val publishableModules = listOf("api", "common", "plugin", "velocity")
    if (name in publishableModules) {
        apply(plugin = "maven-publish")
        
        afterEvaluate {
            configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("pds") {
                        // Special handling for the shaded plugin module
                        if (this@subprojects.name == "plugin") {
                            artifact(tasks.named("shadowJar"))
                        } else {
                            val javaComponent = components.findByName("java")
                            if (javaComponent != null) {
                                from(javaComponent)
                            }
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
                            username = project.findProperty("reposilite_username")?.toString()?.takeIf(String::isNotBlank)
                                ?: System.getenv("PDS_REPOSILITE_USERNAME").orEmpty()
                            password = project.findProperty("reposilite_password")?.toString()?.takeIf(String::isNotBlank)
                                ?: System.getenv("PDS_REPOSILITE_PASSWORD").orEmpty()
                        }
                    }
                }
            }
        }
    }
}

allprojects {
    plugins.withType<JavaPlugin> {
        configure<JavaPluginExtension> {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        }
    }
}

subprojects {
    val moduleName = name
    val path = project.path

    // Note: modules under :versions: are configured in versions/build.gradle.kts (nested subprojects).
    val javaReleaseVersion = when {
        moduleName == "api" || moduleName == "common" -> 17
        moduleName == "plugin" || moduleName == "velocity" -> 21
        path == ":forge-versions:v1_20_R1" -> 17
        path.startsWith(":forge-versions:") -> 21
        path.startsWith(":fabric-versions:") -> 21
        else -> 21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(javaReleaseVersion)
    }
}

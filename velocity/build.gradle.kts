plugins {
    id("java")
}

val pluginBuildDir = layout.buildDirectory.dir("generated/plugin-build")

tasks.register("generatePluginBuildInfo") {
    val versionString = project.version.toString()
    inputs.property("version", versionString)
    outputs.dir(pluginBuildDir)

    doLast {
        val ver = versionString.replace("\\", "\\\\").replace("\"", "\\\"")
        val base = pluginBuildDir.get().asFile.resolve("de/craftingstudiopro/playerDataSyncReloaded/velocity")
        base.mkdirs()
        base.resolve("PluginBuildInfo.java").writeText(
            """
            package de.craftingstudiopro.playerDataSyncReloaded.velocity;

            public final class PluginBuildInfo {
                private PluginBuildInfo() {}

                public static final String VERSION = "$ver";
            }
            """.trimIndent() + "\n"
        )
    }
}

sourceSets.named("main") {
    java.srcDir(pluginBuildDir)
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn("generatePluginBuildInfo")
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("PlayerDataSyncReloaded-Velocity")
}

val copyJar by tasks.registering(Copy::class) {
    from(tasks.named("jar"))
    into(rootProject.layout.buildDirectory.dir("libs"))
}

tasks.named("build") {
    dependsOn(copyJar)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("org.jetbrains:annotations:26.0.1")
    implementation(project(":api"))
    implementation(project(":common"))
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
}

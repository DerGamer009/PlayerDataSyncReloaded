import org.gradle.api.attributes.java.TargetJvmVersion

dependencies {
    implementation(project(":api"))
    implementation(project(":common"))

    compileOnly("io.papermc.paper:paper-api:26.2.build.39-alpha")
}

// paper-api 26.x publishes a JVM-25 library variant; we still emit Java 21 bytecode (--release 21).
configurations.named("compileClasspath").configure {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}

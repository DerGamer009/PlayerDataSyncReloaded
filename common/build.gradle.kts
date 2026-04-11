dependencies {
    implementation(project(":api"))
    
    // Use the modern Paper API for the common module
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    
    // Database Drivers
    implementation("com.zaxxer:HikariCP:4.0.3") // Java 8 compatible version
    implementation("org.mariadb.jdbc:mariadb-java-client:2.7.12") // Java 8 compatible
    implementation("org.postgresql:postgresql:42.2.27") // Java 8 compatible
    implementation("org.mongodb:mongodb-driver-sync:4.11.1") // Java 8 compatible
    
    // Redis (Jedis 3.x is Java 8 compatible)
    implementation("redis.clients:jedis:3.10.0")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Vault API
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}

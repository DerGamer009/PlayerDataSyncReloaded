# ⚙️ Installation & Build

Setting up a robust synchronization network requires careful configuration. This guide walks you through the initial deployment and automated build processes.

---

## 📥 Prerequisite Checklist

Before you begin, ensure your infrastructure meets the following high-performance standards:

*   **Runtime**: [OpenJDK 25](https://adoptium.net/) or higher.
*   **Platform**: Paper, Purpur, or **Folia** (1.20 and up).
*   **Database**: A dedicated instance of MySQL, MariaDB, PostgreSQL, or MongoDB.
*   **Speed**: (Recommended) A **Redis** server for ultra-low latency updates.

:::warning
**Java Version Alert**  
Reloaded utilizes modern Java 25 features for deep memory management and GZIP efficiency. Older Java versions (17, 21) are NOT supported and will result in a crash on startup.
:::

---

## 🛠️ Building the Plugin

We use a modular Gradle architecture. This allows us to maintain compatibility across multiple Minecraft versions while keeping the core logic consistent.

### Compiling on your machine
```bash
# Clone the repository
git clone https://github.com/DerGamer009/PlayerDataSyncReloaded.git
cd PlayerDataSyncReloaded

# Build the shaded plugin jar
./gradlew clean :plugin:shadowJar
```

:::tip
**Finding the Jar**  
After a successful build, your production-ready JAR is located at:  
`plugin/build/libs/PlayerDataSyncReloaded-26.4.jar`
:::

---

## 🤖 CI/CD Integration

Automate your development workflow by integrating Reloaded into your build pipelines.

### Jenkins Pipeline Snippet
If you use a Jenkinsfile, you can use the following stage:

```groovy
stage('Build PDS') {
    steps {
        sh './gradlew clean :plugin:shadowJar'
        archiveArtifacts artifacts: 'plugin/build/libs/*.jar', fingerprint: true
    }
}
```

---

## 🌩️ Initial Deployment

1.  Place the JAR file into your `plugins/` directory.
2.  Start the server once to generate the default `config.yml`.
3.  Configure your database credentials (see [Configuration Guide](configuration.md)).
4.  **Restart** the server to establish the connection.

:::caution
**Network Sync**  
All servers in your network MUST be connected to the exact same database. If you use Redis, ensure all servers also use the same Redis channel and password.
:::

---

## 🩺 Diagnostics

*   **Log Check**: Look for `[PlayerDataSyncReloaded] Connected to storage!` in the console.
*   **Version Check**: Run `/pds version` to confirm you are running the latest release.
*   **Status Check**: Use `/pds status` to view active sync tasks and database latency.

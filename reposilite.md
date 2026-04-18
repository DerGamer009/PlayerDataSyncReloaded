# 📦 Reposilite Integration Guide

Host your own artifacts and distribute the **PlayerDataSync API** with maximum control. This guide covers how to set up, secure, and deploy PDS modules using [Reposilite](https://reposilite.com/).

---

## 🚀 Quick Start with Reposilite

Reposilite is a lightweight, open-source Maven repository manager. It is the recommended solution for hosting the PDS infrastructure.

:::tabs

:::tab{title="Docker (Recommended)"}
Deploy instantly with Docker Compose:

```yaml
version: '3.8'
services:
  reposilite:
    image: dzikoysk/reposilite:3.5.12
    container_name: reposilite
    ports:
      - '80:8080'
    volumes:
      - ./data:/app/data
    restart: unless-stopped
```
:::

:::tab{title="Standalone JAR"}
Run directly with Java 17+:

```bash
# Download the latest JAR
wget https://maven.reposilite.com/releases/com/reposilite/reposilite/3.5.12/reposilite-3.5.12-all.jar

# Start with custom parameters
java -Xmx512M -jar reposilite-3.5.12-all.jar --working-directory ./data
```
:::

:::

---

## 🏗️ Deploying the PDS API

To share the **PDS API** with other developers, you need to publish your artifacts to your Reposilite instance.

### 1. Configure the Build System
Add the `maven-publish` plugin to your `build.gradle.kts` (or `build.gradle`):

```kotlin
plugins {
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "de.playerdatasync"
            artifactId = "api"
            version = "26.4"
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "PDS-Repo"
            url = uri("https://repo.dergamer09.at/releases")
            credentials {
                username = project.property("reposilite.username").toString()
                password = project.property("reposilite.password").toString()
            }
        }
    }
}
```

:::warning
**Security Notice**  
Never hardcode your credentials. Use `gradle.properties` or environment variables to store your Reposilite tokens.
:::

---

## 📡 Accessing the API

Once hosted, developers can integrate your API with just a few lines of configuration.

| Logic | Path |
| :--- | :--- |
| **Repository URL** | `https://repo.dergamer09.at/releases` |
| **Artifact Group** | `de.playerdatasync` |
| **Release Type** | Stable & GA |

### 🛠️ Developer Integration

::::tabs

:::tab{title="Gradle (Kotlin)"}
```kotlin
repositories {
    maven("https://repo.dergamer09.at/releases")
}

dependencies {
    compileOnly("de.playerdatasync:api:26.4")
}
```
:::

:::tab{title="Maven"}
```xml
<repository>
    <id>pds-repo</id>
    <url>https://repo.dergamer09.at/releases</url>
</repository>

<dependency>
    <groupId>de.playerdatasync</groupId>
    <artifactId>api</artifactId>
    <version>26.4</version>
    <scope>provided</scope>
</dependency>
```
:::

::::

---

## 🔐 Token Management

To allow publishing, you must create a token in the Reposilite CLI or Dashboard:

```bash
# Example CLI command for PDS releases
token-generate pds-deploy rw /releases
```

:::tip
Give your tokens granular permissions (e.g., `rw` for the release path only) to maintain a secure environment for your API distribution.
:::

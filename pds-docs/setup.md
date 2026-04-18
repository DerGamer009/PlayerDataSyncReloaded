# ⚙️ Installation & Build

Get your synchronization network up and running. This guide covers how to build from source and deploy to your servers.

---

## 📥 Quick Installation

1.  Download the latest [Release Candidate](https://github.com/DerGamer009/PlayerDataSyncReloaded/releases).
2.  Install the `.jar` in your server's `plugins/` folder.
3.  **Requirements**:
    *   Java JDK 25 or higher.
    *   Paper, Spigot, or Folia (1.20+).
    *   A supported database (MySQL/MongoDB).

---

## 🛠️ Building from Source

We use Gradle to manage our build process. You can build the project locally or via CI/CD pipelines like Jenkins or GitHub Actions.

### Local Build
```bash
git clone https://github.com/DerGamer009/PlayerDataSyncReloaded.git
cd PlayerDataSyncReloaded
./gradlew clean :plugin:shadowJar
```
The final artifact will be located in: `plugin/build/libs/PlayerDataSyncReloaded-26.4.jar`

---

## 🤖 Jenkins Integration

Automate your builds with this freestyle project configuration:

### 1. Repository
*   **Git URL**: `https://github.com/DerGamer009/PlayerDataSyncReloaded.git`
*   **Branch**: `*/master`

### 2. Gradle Setup
| Option | Setting |
| :--- | :--- |
| **Tasks** | `clean :plugin:shadowJar` |
| **Wrapper** | Enabled (Recommended) |
| **Java** | JDK 25 |

### 3. Artifact Archive
Set the "Post-build Actions" to archive:
`plugin/build/libs/PlayerDataSyncReloaded-*.jar`

---

## ❓ Troubleshooting

*   **UnsupportedClassVersionError**: Ensure your server is running **Java 25**.
*   **Connection Refused**: Check your database firewall and credentials in `config.yml`.
*   **Data not syncing**: Ensure all servers in your network are connected to the SAME database.

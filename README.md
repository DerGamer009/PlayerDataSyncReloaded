# PlayerDataSyncReloaded

![Version](https://img.shields.io/badge/version-26.4--Release-blue.svg)
![Java](https://img.shields.io/badge/Java-25-orange.svg)
![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Folia%20(1.20+)-green.svg)

**PlayerDataSyncReloaded** is a high-performance synchronization solution for Minecraft player data across multiple server instances. Designed for networks and multi-server environments, it ensures a seamless player experience by keeping inventories, stats, and more consistent across the proxy.

## 🚀 Features

- **Multi-Database Support**: Sync data using MySQL, MariaDB, PostgreSQL, or MongoDB.
- **Redis Integration**: High-speed synchronization using Redis Pub/Sub for near-instant updates.
- **Folia Support**: Fully compatible with the Folia region-based multithreading engine.
- **AES Encryption**: Secure your player data with optional AES encryption.
- **Highly Configurable**: Choose exactly what data translates across servers.
- **Recent Java Support**: Built using Java 25 for maximum performance and modern features.

## 📦 What can be synchronized?

- [x] Inventory & Ender Chest
- [x] Health, Food & Experience
- [x] Potion Effects
- [x] Advancements & Statistics
- [x] Game Mode & Flight Status
- [x] Location (optional)
- [x] Attributes & Persistent Data Containers (PDC)

## 🛠️ Requirements

- **Java 25** or higher.
- A Paper or Folia based server (1.20 or higher).
- A database (MySQL, MariaDB, Postgres, or MongoDB).
- (Optional) Redis for improved performance.

## 📥 Installation

1. Download the latest version from the releases page.
2. Place the JAR file in the `plugins` folder of all servers you wish to sync.
3. Restart the servers to generate the configuration.
4. Configure the database settings in `plugins/PlayerDataSyncReloaded/config.yml`.
5. Restart the servers again to apply the changes.

## 💻 Commands

| Command | Alias | Description | Permission |
|---------|-------|-------------|------------|
| `/playerdatasync` | `/pds`, `/pdasync` | Main plugin command | `playerdatasync.admin` |

## 🛠️ Build from Source

To build the project yourself, ensure you have Java 25 installed:

```bash
./gradlew build
```

The output JARs will be located in the `plugin/build/libs/` directory.

---

### 📄 License

Distributed under the **MIT License**. See `LICENSE` for more information.

### 🌐 Links

- **Website**: [craftingstudiopro.de](https://craftingstudiopro.de)
- **Author**: CraftingStudioPro, DerGamer09

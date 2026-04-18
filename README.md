<p align="center">
  <img src="https://img.craftingstudiopro.de/pds_logo.png" alt="PlayerDataSync Reloaded Logo" width="200px">
</p>

<h1 align="center">🔄 PlayerDataSync Reloaded</h1>

<p align="center">
  <strong>The next-generation data synchronization engine for high-performance Minecraft networks.</strong>
</p>

<p align="center">
  <a href="https://github.com/DerGamer009/PlayerDataSyncReloaded/releases">
    <img src="https://img.shields.io/badge/version-26.4--Release-blue?style=for-the-badge" alt="Version">
  </a>
  <a href="https://www.oracle.com/java/technologies/javase/jdk25-archive-downloads.html">
    <img src="https://img.shields.io/badge/Java-25-orange?style=for-the-badge&logo=openjdk" alt="Java">
  </a>
  <a href="https://papermc.io/">
    <img src="https://img.shields.io/badge/Platform-Paper%20%7C%20Folia-green?style=for-the-badge" alt="Platform">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge" alt="License">
  </a>
</p>

---

**PlayerDataSync Reloaded** is a premium, open-source synchronization solution designed to handle massive amounts of player data across complex multi-server environments. Whether you're running a survival cluster or a competitive mini-game network, Reloaded ensures your players' inventories, stats, and custom metadata are always exactly where they need to be—instantly.

### 🌟 Key Features

*   **⚡ Blazing Performance**: Integrated GZIP compression for all serialized data and dedicated asynchronous thread pools to keep the main server thread untouched.
*   **🌀 Real-Time Synchronization**: Native support for **Redis Pub/Sub** messaging for near-instant data handovers between nodes.
*   **🛡️ Enterprise-Grade Security**: Optional **AES-256 encryption** for sensitive player profiles, keeping your data safe from unauthorized access.
*   **Universal Persistence**: Seamlessly connect your network with **MySQL, MariaDB, PostgreSQL, or MongoDB**.
*   **🌿 Modern Engine**: Fully compatible with **Folia** and its region-based multithreading architecture.
*   **Modular API**: Powerful events and expansion hooks for developers to sync custom data via **Persistent Data Containers (PDC)**.

---

### 📦 Synchronized Data Points

Reloaded synchronizes almost every aspect of the player state:

- [x] **Inventories**: Full support for player inventory, armor slots, and Ender Chests.
- [x] **Vital Stats**: Health, hunger, saturation, experience levels, and potion effects.
- [x] **Progress**: Advancements, complex statistics, and achievement history.
- [x] **State**: Game mode, flight status, location (optional), and custom attributes.
- [x] **Economy**: Direct integration with **Vault** for cross-network balance sync.
- [x] **Metadata**: Deep synchronization of **Persistent Data Containers (PDC)**.

---

### 📖 Documentation

For detailed installation guides, configuration references, and developer documentation, visit our official wiki:

> [!TIP]
> **Check out the [Official Documentation Portal](https://docs.titannode.de)**  
> *Or browse the local [Wiki Hub](./pds-docs/README.md) and [Migration Guide](./DATATRANSFER.md).*

---

### 🛠️ Quick Start

#### Requirements
- **Java 25** or higher.
- **Paper** or **Folia** (1.20+).
- A database backend (SQL or MongoDB).

#### Installation
1.  Download the latest release from [Releases](https://github.com/DerGamer009/PlayerDataSyncReloaded/releases).
2.  Drop the `.jar` into your `plugins/` directory on all servers.
3.  Configure your database credentials in `config.yml`.
4.  Restart and experience seamless synchronization!

---

### 💻 Developer API

Hook into the synchronization engine with our event-driven API:

```java
@EventHandler
public void onDataLoad(PlayerDataLoadEvent event) {
    Player player = event.getPlayer();
    PlayerData data = event.getData();
    // Data has been applied to the player!
}
```
*See the [API Docs](./pds-docs/api.md) for more information.*

---

### 🤝 Contributing & Support

- **Bug Reports**: Please open an [Issue](https://github.com/DerGamer009/PlayerDataSyncReloaded/issues) with detailed reproduction steps.
- **Documentation**: [docs.titannode.de](https://docs.titannode.de)
- **Website**: [craftingstudiopro.de](https://craftingstudiopro.de)
- **Discord**: Join our community for faster support.

*Developed with ❤️ by **CraftingStudioPro** & **DerGamer09***

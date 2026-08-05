# Store listing copy

Marketing copy for SpigotMC, Modrinth, Hangar and BuiltByBit.
Keep this in sync with what the code actually does — every claim below was checked against the source.

---

# 🧩 PlayerDataSync

<p align="center">
  <a href="https://www.spigotmc.org/resources/playerdatasync-1-8-1-21-11-cross-server-data-synchronization.123166/">
    <img src="https://img.shields.io/badge/Available%20on-SpigotMC-orange?style=flat&logo=spigotmc&logoColor=white">
  </a>
  <a href="https://hangar.papermc.io/DevVoxel/PlayerDataSync">
    <img src="https://img.shields.io/badge/Available%20on-Hangar-blue?style=flat&logo=papermc&logoColor=white">
  </a>
  <a href="https://builtbybit.com/resources/playerdatasync.89908/">
    <img src="https://img.shields.io/badge/Available%20on-BuiltByBit-purple?style=flat">
  </a>
  <a href="https://modrinth.com/plugin/playerdatasync">
    <img src="https://img.shields.io/badge/Available%20on-Modrinth-1bd96a?style=flat&logo=modrinth&logoColor=white">
  </a>
  <a href="https://craftingstudiopro.dev/listing/playerdatasync">
    <img src="https://img.shields.io/badge/Official%20Website-CraftingStudioPro-black?style=flat&logo=google-chrome&logoColor=white">
  </a>
</p>

<p align="center">
  <strong>Seamless Cross-Server Player Data Synchronization</strong><br>
  Keep inventories, Ender Chests, XP, health, attributes and more synchronized across every server in your network.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20--26.2-brightgreen?style=for-the-badge">
  <img src="https://img.shields.io/badge/Java-21+-orange?style=for-the-badge&logo=openjdk">
  <img src="https://img.shields.io/badge/Paper%20%7C%20Spigot%20%7C%20Fabric%20%7C%20Velocity-Supported-blue?style=for-the-badge">
  <img src="https://img.shields.io/badge/MySQL%20%7C%20MariaDB%20%7C%20PostgreSQL%20%7C%20MongoDB-Supported-4479A1?style=for-the-badge">
</p>

[![Servers & Players](https://faststats.dev/embed/default:5b846603-95f8-4e64-b64c-c5cb16879210:servers-and-players?w=800&h=300&theme=dark)](https://faststats.dev/project/playerdatasyncreloaded/minecraft-plugin)

---

# 🚀 Overview

PlayerDataSync keeps player data synchronized across every server in your Minecraft network.

Whether players move between Survival, Lobby, Creative, SkyBlock, Minigames or anything else, their progress follows them automatically.

No manual file transfers. No duplicated inventories. No lost progress.

Player data lives in one shared database. With Redis enabled, changes propagate between servers the moment they happen — your network behaves like a single world.

---

# ✨ What Gets Synchronized

Every entry is an individual toggle, so you decide exactly what travels with your players.

| Category | Included |
|----------|----------|
| **Items** | Inventory, Ender Chest, selected hotbar slot |
| **Progression** | Experience, levels, advancements, statistics |
| **Vitals** | Health, absorption, hunger, saturation, exhaustion, air, fire ticks, freeze ticks, arrows in body |
| **State** | Game mode, flight, walk & fly speed, fall distance, player time, player weather |
| **Advanced** | Attributes, Persistent Data Container, Vault economy balance |
| **Optional** | Location (disabled by default — see below) |

### 📍 About location sync

Location sync teleports players to their stored position on join. It ships **disabled** (`sync.location: false`), because it only makes sense when the destination server has a world of the same name. If that world is missing, the restore is skipped with a log warning instead of dropping the player somewhere wrong.

---

# 🧰 More Than Just Sync

- 🔴 **Redis live sync** — propagate changes between servers instantly instead of waiting for the next join
- 🔐 **AES encryption** — encrypt stored profiles with your own key
- 💾 **Automatic backups** — compressed snapshots before data is overwritten
- 🔁 **Storage migration** — move between MySQL, MariaDB, PostgreSQL and MongoDB with one command
- ⬆️ **Legacy import** — bring profiles over from the original PlayerDataSync
- 📤 **Import / export** — move individual player profiles in and out
- 💬 **Discord webhooks** — notifications for sync successes and failures
- ⏱️ **Autosave** — periodic saves as a crash safeguard
- 🚫 **Exclusions** — skip specific worlds or specific item types
- 📊 **bStats & FastStats** metrics
- 🧩 **Developer API** — events and Persistent Data Container hooks for your own plugins

---

# 🌟 Why PlayerDataSync?

Running player data across multiple servers without proper synchronization leads to:

- Lost inventories
- Item duplication
- Missing experience
- Inconsistent progress

PlayerDataSync removes those failure modes by synchronizing automatically whenever data changes.

### Benefits

- ✅ One shared player profile across your whole network
- ✅ Instant propagation with Redis
- ✅ Four database backends to choose from
- ✅ Asynchronous — the main thread stays free
- ✅ Skips redundant saves when nothing changed
- ✅ Open source (MIT) and actively maintained

---

# 🧱 Supported Platforms

| Component | Supported |
|-----------|-----------|
| **Minecraft** | 1.20 – 26.2 |
| **Server software** | Paper, Spigot, Fabric, Velocity (proxy bridge) |
| **Java** | 21+ — Minecraft 26.x servers require Java 25 |
| **Database** | MySQL, MariaDB, PostgreSQL, MongoDB |

The download is the Paper/Spigot plugin. The Fabric and Velocity builds are bundled inside it under `bundled/` — extract whichever you need.

**Please note:**

- **Fabric is not yet at feature parity.** Advancements and statistics are not synchronized on Fabric. The 1.20 and 1.21 Fabric builds additionally lack Ender Chest, potion effects and attributes; the 26.x builds have those.
- **Forge is currently unavailable** while its build tooling catches up with the Minecraft 26.x toolchain.
- **On Fabric, use MariaDB, PostgreSQL or MongoDB.** The MySQL driver is not bundled and mod loaders do not supply one. The MariaDB driver connects to MySQL servers just fine. Paper and Spigot are unaffected.
- **SQLite is not supported.**

---

# ⚙️ Configuration

Full documentation: 👉 **https://pds.devvoxel.de/config**

- **Paper / Spigot:** `plugins/PlayerDataSyncReloaded/config.yml`
- **Fabric:** `config/playerdatasync.properties`, created on first start

```yaml
storage:
  type: "mariadb"   # mysql, mariadb, postgres, mongodb
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "root"
  password: ""

redis:
  enabled: true     # strongly recommended for multi-server setups
  host: "localhost"
  port: 6379

sync:
  inventory: true
  ender_chest: true
  economy: true     # requires Vault + an economy plugin
  location: false   # teleports players on join
```

---

# 💬 Commands & Permissions

Everyday operation is fully automatic — players never touch a command. For administrators, everything lives under `/playerdatasync` (aliases `/pds`, `/pdasync`) behind the `playerdatasync.admin` permission.

| Command | Description |
|---------|-------------|
| `/pds status` | Sync statistics, error counters, excluded worlds |
| `/pds save <player>` | Force-save a player |
| `/pds saveall` | Force-save everyone online |
| `/pds load <player>` | Reload a player from storage |
| `/pds list` | List stored profiles |
| `/pds backup` | Create a backup |
| `/pds export` / `/pds import` | Move profiles in and out |
| `/pds migrate` | Migrate between storage backends |
| `/pds toggle` | Toggle syncing at runtime |
| `/pds debug on\|off` | Toggle verbose logging |
| `/pds reload` | Reload the configuration |

---

# 📈 Performance

- ⚡ Asynchronous database operations — the main thread is never blocked
- 🔄 Redundant saves are skipped when the inventory has not changed
- ⏳ Configurable minimum interval between saves
- 💾 Compressed backups
- 📉 Low CPU and memory footprint

Suitable for small communities and large networks alike.

---

# 📋 Requirements

- ☕ Java **21+** (Java **25** for Minecraft 26.x servers)
- 🗄️ MySQL, MariaDB, PostgreSQL or MongoDB
- 🧱 Paper, Spigot or Fabric server
- 🌐 Multiple servers pointed at the same database
- 🔴 Redis *(optional, recommended)* — instant cross-server propagation
- 💰 Vault + an economy plugin *(optional)* — required for economy sync

---

# 🛠️ Support

- 💬 Join our Discord server
- 🐛 Report bugs on GitHub
- 📚 Read the documentation
- 💡 Share feature requests and suggestions

---

# ❤️ Credits

Developed with ❤️ by **DerGamer09**

If PlayerDataSync helps your network, consider leaving a review on **SpigotMC**, **Modrinth**, **Hangar** or **BuiltByBit**. Your feedback shapes what gets built next.

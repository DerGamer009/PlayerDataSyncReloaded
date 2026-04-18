# 💻 Commands & Permissions

Manage PlayerDataSync Reloaded directly from within Minecraft or the server console.

---

## 🛠️ Admin Commands
**Alias**: `/pds`, `/pdasync`

| Command | Description | Permission |
| :--- | :--- | :--- |
| `/pds reload` | Hot-reloads configuration and database connections. | `playerdatasync.admin` |
| `/pds save <player>` | Manually forces a data save for a specific player. | `playerdatasync.admin` |
| `/pds load <player>` | Manually forces a data load for a specific player. | `playerdatasync.admin` |
| `/pds migrate` | Starts the data migration process (see [Migration](migration.md)). | `playerdatasync.admin` |
| `/pds backup export <name>` | Creates a .zip backup of all current storage data. | `playerdatasync.admin` |
| `/pds backup import <name>` | Restores data from a previously exported backup. | `playerdatasync.admin` |

---

## 🔑 Permissions Overview

*   `playerdatasync.admin`: Grants access to all plugin commands and administrative features.
*   `playerdatasync.sync`: (Implicit) Required for player data to be synchronized. Enabled by default for all players.

---

## 💡 Usage Tips

*   **Console Support**: All admin commands can be executed from the console without the core `/` slash.
*   **Tab Completion**: All commands feature intelligent tab-completion for player names and sub-commands.

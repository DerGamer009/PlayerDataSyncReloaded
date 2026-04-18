# ⚙️ Configuration Guide

The `config.yml` file is the heart of PlayerDataSync Reloaded. This guide covers every setting to help you optimize your network's data handling.

---

## 🏗️ Storage Configuration
Define where your player data is stored. We support both Relational (SQL) and Document (NoSQL) databases.

```yaml
storage:
  type: "mysql" # Options: mysql, mariadb, postgres, mongodb
  host: "localhost"
  port: 3306
  database: "minecraft"
  username: "root"
  password: ""
  # Optional: For MongoDB Atlas, use connection_url instead of separate host/port
  connection_url: ""
```

---

## 🌀 Redis Integration
Redis is used for **Pub/Sub event messaging**. While not strictly required, it is **highly recommended** for large networks to ensure data consistency between servers.

```yaml
redis:
  enabled: true
  host: "localhost"
  port: 6379
```
> [!TIP]
> Using Redis reduces the "sync delay" to near-zero by notifying other nodes immediately when a player logs out.

---

## 🔒 Security & Performance
Reloaded features built-in encryption and compression to keep your data safe and compact.

| Setting | Description | Recommended |
| :--- | :--- | :--- |
| `encryption_key` | AES-256 key (16+ chars). Encrypts data before storing. | Highly Recommended |
| `debug` | Logs detailed sync info to console. | False (uness debugging) |
| `autosave` | Prevents data loss during server crashes. | True |

---

## 🎒 Synchronization Toggles
Fine-tune exactly what data is transferred across your network.

*   `inventory`: Player items and armor.
*   `ender_chest`: Global ender chest contents.
*   `health` / `food` / `experience`: Vital player stats.
*   `pdc`: Persistent Data Containers (used by many other plugins).
*   `economy`: Vault-compatible economy balance.

---

## 🚫 Exclusions
Prevent synchronization in specific environments.

```yaml
exclusions:
  worlds:
    - "lobby_restricted"
  items:
    - "BARRIER"
```
*   **Worlds**: Players entering these worlds will retain their state until they leave for a synced world.
*   **Items**: Useful for blacklisting "admin items" or server-specific gear.

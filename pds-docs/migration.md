# 🔄 Data Migration & Transfers

Need to upgrade your database or move from the old PlayerDataSync? This guide covers the safest ways to handle your player data during transitions.

---

## 🚀 The /pds migrate System

Reloaded features a high-performance migration engine capable of moving thousands of player profiles between different database types (e.g., MySQL → MongoDB).

### 1. Preparation
Configure the **TARGET** database in the `migration:` section of your `config.yml`.

```yaml
migration:
  type: "mongodb"
  connection_url: "mongodb://your-new-cloud-db"
  legacy: false # Only true if moving from the OLD non-Reloaded plugin
```

### 2. Execution
Run the following command from your **Primary Server's Console**:
```bash
/pds migrate
```

### 3. Verification
Once the console logs `Migration completed successfully!`:
1.  Stop the server.
2.  Update your `storage:` block to the new credentials.
3.  Restart and verify player data.

---

## 📦 Backup & Recovery

We recommend performing local backups before any major infrastructure change.

### Manual Backups
You can create portable `.zip` archives of your entire database:
*   **Export**: `/pds backup export <name>`
*   **Import**: `/pds backup import <name>`

> [!CAUTION]
> Backups are stored in `/plugins/PlayerDataSyncReloaded/backups/`. Ensure this folder is protected and not accessible via web servers.

---

## 🛠️ Legacy Migration
Upgrading from the original **PlayerDataSync**?

1.  Set up your old database as the `storage:`.
2.  Set up your new database as the `migration:`.
3.  Set `migration.legacy: true`.
4.  Run `/pds migrate`.

The engine will automatically handle the mapping between the old NBT format and the new GZIP-compressed Reloaded format.

# 🔄 Data Migration & Transfers

Modernizing your infrastructure or switching database providers? PlayerDataSync Reloaded provides a rock-solid migration engine designed to handle thousands of player profiles with zero data loss.

---

## ⚡ The Migration Engine

The `/pds migrate` command is a powerful tool that reads data from your current active `storage` and writes it to a configured `migration` target.

:::tip
**Efficiency Matters**  
The migration engine runs in a separate thread pool to ensure your server performance is not impacted. However, we recommend performing large migrations during low-traffic hours or on a dedicated staging server.
:::

---

## 🛠️ Step-by-Step Migration

Follow these steps to move from one database type to another (e.g., MySQL → MongoDB).

### 1. Configure the Target
Open your `config.yml` and locate the `migration` block. Enter the credentials for the database you want to move **TO**.

```yaml
migration:
  type: "mongodb" # Options: mysql, mariadb, postgres, mongodb
  connection_url: "mongodb+srv://admin:password@cluster.example.com/pds"
  legacy: false # Set to true only if importing from the original PlayerDataSync
```

### 2. Execute the Migration
From your **Server Console**, run the migration command:

```bash
/pds migrate
```

:::warning
**Do not interrupt the process.**  
A progress bar will appear in the console. Interrupting the migration might lead to inconsistent data in the target database. Wait for the `Migration completed successfully!` message.
:::

### 3. Finalize the Switch
Once finished, swap your configuration:
1.  Copy the settings from the `migration` block to the main `storage` block.
2.  Set `migration.enabled: false`.
3.  Restart all servers in your network to point to the new database.

---

## 📦 Backup & Recovery

Before any migration, it is critical to have a restorable backup.

### Creating a Snapshot
Reloaded can export your entire database into a compressed `.pdsbackup` file.

*   **Export**: `/pds backup export <filename>`
*   **Import**: `/pds backup import <filename>`

:::caution
**Storage Safety**  
Backups are stored in `plugins/PlayerDataSyncReloaded/backups/`. Ensure this directory is included in your regular server backups and never share these files, as they contain sensitive player metadata.
:::

---

## 🏛️ Legacy Import (Original PDS)

If you are upgrading from the original **PlayerDataSync** (v1.x), the process is slightly different:

1.  Connect PDS Reloaded to your old database via the `storage` block.
2.  Configure your new database in the `migration` block.
3.  Set `migration.legacy: true`.
4.  Run `/pds migrate`.

Reloaded will automatically detect the old NBT structure, convert it to the new high-performance GZIP format, and transfer it to the new system.

---

## ❓ Troubleshooting

| Issue | Solution |
| :--- | :--- |
| **Authentication Failed** | Double-check your `connection_url`. Ensure special characters are properly URL-encoded. |
| **Timeout during migration** | Increase the database driver timeout in your `connection_url` (e.g., `?connectTimeout=10000`). |
| **Data missing after switch** | Check if you performed the migration on the same world/server where people were active. |

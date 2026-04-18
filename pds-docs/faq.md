# ❓ Frequently Asked Questions

Find answers to common questions about PlayerDataSync Reloaded.

---

### 1. Does PDS support Folia?
**Yes!** We are fully compatible with Folia's region-based multithreading. All database interactions are handled asynchronously to ensure zero impact on your region ticks.

### 2. Can I use different database types on different servers?
**No.** All servers in your network must use the same database type and point to the same database/cluster to ensure data consistency.

### 3. How do I prevent specific items from being synced?
You can use the `exclusions` section in your `config.yml`. Simply add the Material name of the item to the list:
```yaml
exclusions:
  items:
    - "BEDROCK"
    - "BARRIER"
```

### 4. What happens if the database goes down?
Reloaded includes an **Auto-Save** feature. While a database outage will prevent new data from being saved/loaded, the plugin will attempt to keep the current session safe. We recommend using a high-availability database cluster (like MongoDB Atlas or a MySQL Galera Cluster) for production networks.

### 5. Why is my data not syncing immediately?
If you skip using **Redis**, there might be a slight delay depending on your database latency. For near-instant synchronization, we highly recommend enabling the Redis Pub/Sub feature in your configuration.

---

## 🛠️ Still need help?
If your question isn't answered here, feel free to visit our [GitHub Repository](https://github.com/DerGamer009/PlayerDataSyncReloaded) or join our community discord.

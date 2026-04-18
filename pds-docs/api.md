# 🧩 Developer API

Integrate PlayerDataSync Reloaded into your own plugins. Our modular API allows you to monitor data lifecycles, access synchronized profiles, and extend functionality with zero friction.

---

## 🏗️ Dependency Management

To start building with the PDS API, add our official repository and the API module to your project configuration.

::::tabs

:::tab{title="Gradle (Kotlin)"}
```kotlin
repositories {
    maven("https://repo.dergamer09.at/releases")
}

dependencies {
    compileOnly("com.github.DerGamer009:PlayerDataSyncReloaded:api:26.4")
}
```
:::

:::tab{title="Gradle (Groovy)"}
```groovy
repositories {
    maven { url 'https://repo.dergamer09.at/releases' }
}

dependencies {
    compileOnly 'com.github.DerGamer009:PlayerDataSyncReloaded:api:26.4'
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
    <groupId>com.github.DerGamer009</groupId>
    <artifactId>PlayerDataSyncReloaded</artifactId>
    <version>api-26.4</version>
    <scope>provided</scope>
</dependency>
```
:::

::::

---

## 📡 Lifecycle Events

Reloaded fires events for every critical stage of a player's data journey. All events are fired **asynchronously** to maintain maximum server performance.

| Event | Thread | Description |
| :--- | :--- | :--- |
| `PlayerDataLoadEvent` | Async | Fired after data is retrieved and applied to the player. |
| `PlayerDataSaveEvent` | Async | Fired before data is serialized and sent to storage. |

### Event Example
Monitor how long data synchronization takes for your players:

```java
@EventHandler
public void onDataLoad(PlayerDataLoadEvent event) {
    long ms = event.getLoadTime();
    Player player = event.getPlayer();
    
    if (ms > 500) {
        getLogger().warning("Slow sync for " + player.getName() + ": " + ms + "ms");
    }
}
```

---

## 🔓 Using the API Service

Access the core synchronization logic via our service provider interface.

```java
PlayerDataSyncAPI api = Bukkit.getServicesManager().load(PlayerDataSyncAPI.class);

if (api != null) {
    // Fetch persisted data for any UUID
    api.getPlayerData(uuid).thenAccept(optionalData -> {
        optionalData.ifPresent(data -> {
            System.out.println("Balance: " + data.balance);
        });
    });
}
```

---

## 🛠️ Extending Data (PDC)

The most consistent way to sync custom plugin data is via the **Persistent Data Container (PDC)**. Since Reloaded synchronizes the entire PDC of every player, any key-value pairs you store there are automatically distributed across your network.

:::tip
**Auto-Sync Logic**  
You don't need to write a single line of PDS-specific code to sync custom data. Just use the standard Bukkit/Paper PDC API, and Reloaded handles the global distribution on every server switch or save.
:::

```java
NamespacedKey myKey = new NamespacedKey(myPlugin, "custom_points");
player.getPersistentDataContainer().set(myKey, PersistentDataType.INTEGER, 100);
// This value is now globally synced!
```

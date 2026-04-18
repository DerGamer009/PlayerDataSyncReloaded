# 🧩 Developer API

PlayerDataSync Reloaded provides a robust API for developers to interact with the synchronization engine or extend player data with custom fields.

---

## 🏗️ Getting Started

To use the PDS API, add our repository and dependency to your `build.gradle` or `pom.xml`.

### Gradle (Kotlin)
```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.DerGamer009:PlayerDataSyncReloaded:api:26.4")
}
```

---

## 🔓 Accessing the API

The entry point for all API interactions is the `PlayerDataSyncAPI` class (if available) or by fetching the service provider.

### Fetching the PlayerData
You can retrieve the current synchronized data of a player:

```java
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;

// Get data for a specific UUID
CompletableFuture<Optional<PlayerData>> futureData = api.getPlayerData(uuid);

futureData.thenAccept(optionalData -> {
    if (optionalData.isPresent()) {
        PlayerData data = optionalData.get();
        // Do something with health, exp, or PDC
    }
});
```

---

## 📡 Events

PDS fires several events that you can listen to in your plugin.

| Event | Description |
| :--- | :--- |
| `PlayerDataLoadEvent` | Fired on the main thread after a player's data has been successfully loaded and applied. |
| `PlayerDataSaveEvent` | Fired before a player's data is serialized and sent to the database. |

### Example Listener
```java
@EventHandler
public void onDataLoad(PlayerDataLoadEvent event) {
    Player player = event.getPlayer();
    PlayerData data = event.getData();
    
    player.sendMessage("Welcome back! Your data was synced in " + event.getLoadTime() + "ms.");
}
```

---

## 🛠️ Extending Data (PDC)

The best way to sync custom plugin data without touching the PDS core is via **Persistent Data Containers (PDC)**.

Since PDS Reloaded fully synchronizes the `PersistentDataContainer` of Every player, any data you store there using the Bukkit API will automatically be synced across your network.

```java
// Saving custom data (automatic sync)
player.getPersistentDataContainer().set(key, PersistentDataType.STRING, "your_custom_value");
```
PDS takes care of the rest!

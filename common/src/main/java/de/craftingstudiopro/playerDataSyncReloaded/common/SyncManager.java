package de.craftingstudiopro.playerDataSyncReloaded.common;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.redis.RedisManager;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SyncManager {
    private final JavaPlugin plugin;
    private final Storage storage;
    private final VersionHandler versionHandler;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Boolean> syncInProgress = new ConcurrentHashMap<>();
    private final boolean isFolia;
    private RedisManager redisManager;

    public SyncManager(JavaPlugin plugin, Storage storage, VersionHandler versionHandler) {
        this.plugin = plugin;
        this.storage = storage;
        this.versionHandler = versionHandler;
        this.logger = plugin.getLogger();
        this.isFolia = isFolia();
    }

    public void setRedisManager(RedisManager redisManager) {
        this.redisManager = redisManager;
        this.redisManager.subscribe(message -> {
            if (message.startsWith("saved:")) {
                String uuidStr = message.substring(6);
                UUID uuid = UUID.fromString(uuidStr);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    logger.info("Received Redis update for " + player.getName() + ", re-syncing...");
                    handleJoin(player); // Re-trigger load
                }
            }
        });
    }

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void handleJoin(Player player) {
        syncInProgress.put(player.getUniqueId(), true);
        
        storage.load(player.getUniqueId()).thenAccept(optionalData -> {
            if (optionalData.isPresent()) {
                PlayerData data = optionalData.get();
                
                if (isFolia) {
                    try {
                        // Use reflection to call Folia scheduler without direct dependency
                        Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
                        scheduler.getClass().getMethod("run", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Runnable.class)
                                .invoke(scheduler, plugin, (java.util.function.Consumer<Object>) (task) -> {
                                    applyData(player, data);
                                }, null);
                    } catch (Exception e) {
                        logger.severe("Failed to use Folia scheduler via reflection: " + e.getMessage());
                        // Fallback to standard if reflection fails (should not happen on Folia)
                        Bukkit.getScheduler().runTask(plugin, () -> applyData(player, data));
                    }
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        applyData(player, data);
                    });
                }
            } else {
                syncInProgress.remove(player.getUniqueId());
            }
        });
    }

    private void applyData(Player player, PlayerData data) {
        if (player.isOnline()) {
            versionHandler.apply(player, data);
            logger.info("Successfully synced data for player: " + player.getName());
        }
        syncInProgress.remove(player.getUniqueId());
    }

    public void handleQuit(Player player) {
        // Kick player if sync is still in progress (highly unlikely at quit)
        if (isSyncInProgress(player.getUniqueId())) {
             logger.warning("Player " + player.getName() + " quit while sync was still in progress!");
        }

        PlayerData data = versionHandler.capture(player);
        filterData(data); // Apply config filters
        
        storage.save(data).thenRun(() -> {
            logger.info("Saved data for player: " + player.getName());
            if (redisManager != null) {
                redisManager.publish("saved:" + player.getUniqueId().toString());
            }
        });
    }

    private void filterData(PlayerData data) {
        var config = plugin.getConfig();
        if (!config.getBoolean("sync.inventory", true)) data.inventoryContents = null;
        if (!config.getBoolean("sync.ender_chest", true)) data.enderChestContents = null;
        if (!config.getBoolean("sync.health", true)) data.health = 20.0;
        if (!config.getBoolean("sync.food", true)) {
            data.foodLevel = 20;
            data.saturation = 5.0f;
            data.exhaustion = 0.0f;
        }
        if (!config.getBoolean("sync.experience", true)) {
            data.level = 0;
            data.exp = 0;
            data.totalExperience = 0;
        }
        if (!config.getBoolean("sync.potion_effects", true)) data.potionEffects = null;
        if (!config.getBoolean("sync.game_mode", true)) data.gameMode = "SURVIVAL";
        if (!config.getBoolean("sync.location", true)) data.worldName = null;
        if (!config.getBoolean("sync.attributes", true)) data.attributes = null;
        if (!config.getBoolean("sync.pdc", true)) data.persistentDataContainer = null;
    }

    public boolean isSyncInProgress(UUID uuid) {
        return syncInProgress.getOrDefault(uuid, false);
    }
}

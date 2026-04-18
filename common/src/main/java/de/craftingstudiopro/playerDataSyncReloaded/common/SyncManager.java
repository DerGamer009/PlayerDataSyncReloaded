package de.craftingstudiopro.playerDataSyncReloaded.common;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.redis.RedisManager;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
import net.milkbowl.vault.economy.Economy;
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
    private final ConcurrentHashMap<UUID, Integer> inventoryHashes = new ConcurrentHashMap<>();
    private final boolean isFolia;
    private RedisManager redisManager;
    private Economy economy;

    public SyncManager(JavaPlugin plugin, Storage storage, VersionHandler versionHandler) {
        this.plugin = plugin;
        this.storage = storage;
        this.versionHandler = versionHandler;
        this.logger = plugin.getLogger();
        this.isFolia = isFolia();
        refreshExclusions();
    }

    public void refreshExclusions() {
        this.versionHandler.setItemExclusions(plugin.getConfig().getStringList("exclusions.items"));
    }

    public void setRedisManager(RedisManager redisManager) {
        this.redisManager = redisManager;
        this.redisManager.subscribe(message -> {
            if (message.startsWith("saved:")) {
                String uuidStr = message.substring(6);
                UUID uuid = UUID.fromString(uuidStr);
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    handleJoin(player);
                }
            }
        });
    }

    public void setEconomy(Economy economy) {
        this.economy = economy;
    }

    public void handleJoin(Player player) {
        if (isWorldExcluded(player.getWorld().getName())) {
            debug("Skipping join sync for " + player.getName() + " in excluded world: " + player.getWorld().getName());
            return;
        }

        syncInProgress.put(player.getUniqueId(), true);
        
        String syncStarted = plugin.getConfig().getString("messages.sync_started", "&7Syncing your data...");
        if (!syncStarted.isEmpty()) {
            player.sendMessage(format(syncStarted));
        }

        long startTime = System.currentTimeMillis();
        storage.load(player.getUniqueId()).thenAccept(optionalData -> {
            if (optionalData.isPresent()) {
                PlayerData data = optionalData.get();
                if (isFolia) {
                    Bukkit.getRegionScheduler().run(plugin, player.getLocation(), task -> applyData(player, data, startTime));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> applyData(player, data, startTime));
                }
            } else {
                syncInProgress.remove(player.getUniqueId());
            }
        }).exceptionally(ex -> {
            syncInProgress.remove(player.getUniqueId());
            String syncFailed = plugin.getConfig().getString("messages.sync_failed", "&cFailed to sync your data. Please contact an admin.");
            if (!syncFailed.isEmpty()) {
                player.sendMessage(format(syncFailed));
            }
            logger.severe("Failed to load data for " + player.getName() + ": " + ex.getMessage());
            return null;
        });
    }

    private void applyData(Player player, PlayerData data, long startTime) {
        if (player.isOnline()) {
            versionHandler.apply(player, data);
            
            // Economy Sync
            if (economy != null && plugin.getConfig().getBoolean("sync.economy", true)) {
                double current = economy.getBalance(player);
                double diff = data.balance - current;
                if (diff > 0) economy.depositPlayer(player, diff);
                else if (diff < 0) economy.withdrawPlayer(player, Math.abs(diff));
            }

            if (data.inventoryContents != null) {
                inventoryHashes.put(player.getUniqueId(), data.inventoryContents.hashCode());
            }

            // Fire API Event
            long duration = System.currentTimeMillis() - startTime;
            Bukkit.getServer().getPluginManager().callEvent(new de.craftingstudiopro.playerDataSyncReloaded.api.event.PlayerDataLoadEvent(player, data, duration));

            String syncComplete = plugin.getConfig().getString("messages.sync_complete", "&aData synced successfully!");
            if (!syncComplete.isEmpty()) {
                player.sendMessage(format(syncComplete));
            }
            logger.info("Successfully synced data for player: " + player.getName() + " (" + duration + "ms)");
        }
        syncInProgress.remove(player.getUniqueId());
    }

    private String format(String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bSync&8] &r");
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix + message);
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            logger.info("[DEBUG] " + message);
        }
    }

    public void handleQuit(Player player) {
        handleQuit(player, false);
    }

    public void handleQuit(Player player, boolean isAutosave) {
        if (isWorldExcluded(player.getWorld().getName())) {
            debug("Skipping quit sync for " + player.getName() + " in excluded world: " + player.getWorld().getName());
            return;
        }

        // Kick player if sync is still in progress (highly unlikely at quit)
        if (isSyncInProgress(player.getUniqueId())) {
             logger.warning("Player " + player.getName() + " quit while sync was still in progress!");
        }

        PlayerData data = versionHandler.capture(player);
        
        // Economy Capture
        if (economy != null && plugin.getConfig().getBoolean("sync.economy", true)) {
            data.balance = economy.getBalance(player);
        }

        filterData(data); // Apply config filters
        
        // Performance optimization: prevent redundant saves if inventory hasn't changed
        if (data.inventoryContents != null) {
            int currentHash = data.inventoryContents.hashCode();
            Integer lastHash = inventoryHashes.remove(player.getUniqueId());
            if (lastHash != null && lastHash == currentHash && !plugin.getConfig().getBoolean("sync.force_save_on_quit", false)) {
                debug("Skipping redundant save for " + player.getName() + " (Inventory unchanged)");
                return;
            }
        } else {
            inventoryHashes.remove(player.getUniqueId());
        }

        // Fire API Event
        Bukkit.getServer().getPluginManager().callEvent(new de.craftingstudiopro.playerDataSyncReloaded.api.event.PlayerDataSaveEvent(player, data));
        
        storage.save(data).thenRun(() -> {
            if (!isAutosave) {
                logger.info("Saved data for player: " + player.getName());
                if (redisManager != null) {
                    redisManager.publish("saved:" + player.getUniqueId().toString());
                }
            } else {
                debug("Auto-saved data for " + player.getName());
            }
        });
    }

    private void filterData(PlayerData data) {
        var config = plugin.getConfig();
        if (!config.getBoolean("sync.inventory", true)) data.inventoryContents = null;
        if (!config.getBoolean("sync.ender_chest", true)) data.enderChestContents = null;
        if (!config.getBoolean("sync.health", true)) data.health = 20.0;
        if (!config.getBoolean("sync.experience", true)) {
            data.exp = 0;
            data.level = 0;
            data.totalExperience = 0;
        }
    }

    private boolean isWorldExcluded(String worldName) {
        return plugin.getConfig().getStringList("exclusions.worlds").contains(worldName);
    }

    public boolean isSyncInProgress(UUID uuid) {
        return syncInProgress.getOrDefault(uuid, false);
    }

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregionscheduler.RegionScheduler");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

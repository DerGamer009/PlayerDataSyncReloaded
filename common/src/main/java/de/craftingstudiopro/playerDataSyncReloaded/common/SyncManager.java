package de.craftingstudiopro.playerDataSyncReloaded.common;

import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.redis.RedisManager;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
import de.craftingstudiopro.playerDataSyncReloaded.common.util.DiscordWebhookManager;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

public class SyncManager {
    private final Platform platform;
    private final Storage storage;
    private final VersionHandler versionHandler;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Boolean> syncInProgress = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> inventoryHashes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastSaveMillis = new ConcurrentHashMap<>();
    private final Set<String> excludedWorlds = ConcurrentHashMap.newKeySet();

    private final LongAdder loadAttempts = new LongAdder();
    private final LongAdder loadSuccess = new LongAdder();
    private final LongAdder loadFailed = new LongAdder();
    private final LongAdder saveAttempts = new LongAdder();
    private final LongAdder saveSuccess = new LongAdder();
    private final LongAdder saveFailed = new LongAdder();
    private final LongAdder skippedByWorld = new LongAdder();
    private final LongAdder skippedByCooldown = new LongAdder();
    private final LongAdder skippedByHash = new LongAdder();
    private final AtomicLong lastLoadDurationMs = new AtomicLong(-1L);
    private final AtomicLong lastSaveDurationMs = new AtomicLong(-1L);
    private final AtomicLong lastErrorAt = new AtomicLong(-1L);
    private volatile String lastErrorMessage = "";

    /** Identifies this server on the Redis channel so it can ignore its own published saves. */
    private final String nodeId = UUID.randomUUID().toString();

    private RedisManager redisManager;
    private DiscordWebhookManager discordManager;
    private Object economy; // Vault Economy — kept as Object so common/ stays Bukkit/Vault-free; accessed via reflection.

    public SyncManager(Platform platform, Storage storage, VersionHandler versionHandler) {
        this.platform = platform;
        this.storage = storage;
        this.versionHandler = versionHandler;
        this.logger = platform.getLogger();
        refreshExclusions();
    }

    public void refreshExclusions() {
        this.versionHandler.setItemExclusions(platform.getConfigStringList("exclusions.items"));
        excludedWorlds.clear();
        List<String> worlds = platform.getConfigStringList("exclusions.worlds");
        if (worlds != null) {
            worlds.stream()
                    .filter(w -> w != null && !w.isBlank())
                    .map(this::normalizeWorldName)
                    .forEach(excludedWorlds::add);
        }
    }

    public void setRedisManager(RedisManager redisManager) {
        this.redisManager = redisManager;
        this.redisManager.subscribe(message -> {
            if (!message.startsWith("saved:")) {
                return;
            }

            // Format: saved:<uuid>:<nodeId>. The nodeId is absent in messages from pre-26.8 nodes.
            String[] parts = message.split(":", 3);
            if (parts.length >= 3 && nodeId.equals(parts[2])) {
                // Our own publish echoing back. Reloading here would undo the save we just made.
                return;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(parts[1]);
            } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException ex) {
                logger.warning("Ignoring malformed Redis message: " + message);
                return;
            }

            platform.runTask(() -> {
                PDSPlayer player = platform.getPlayer(uuid);
                if (player == null) {
                    // Player is not on this server — nothing to refresh.
                    return;
                }
                logger.info("Redis: reloading data for " + player.getName() + " after remote save.");
                handleJoin(player);
            });
        });
    }

    public void clearRedisManager() {
        this.redisManager = null;
    }

    public void setDiscordManager(DiscordWebhookManager discordManager) {
        this.discordManager = discordManager;
    }

    public void clearDiscordManager() {
        this.discordManager = null;
    }

    public void setEconomy(Object economy) {
        this.economy = economy;
    }

    private double readBalance(PDSPlayer player) {
        if (economy == null) return 0.0;
        try {
            Class<?> offlinePlayerCls = Class.forName("org.bukkit.OfflinePlayer");
            return ((Number) economy.getClass()
                    .getMethod("getBalance", offlinePlayerCls)
                    .invoke(economy, player.getHandle())).doubleValue();
        } catch (Throwable t) {
            logger.warning("Failed to read balance for " + player.getName() + ": " + t.getMessage());
            return 0.0;
        }
    }

    private void writeBalance(PDSPlayer player, double target) {
        if (economy == null) return;
        try {
            Class<?> offlinePlayerCls = Class.forName("org.bukkit.OfflinePlayer");
            Object handle = player.getHandle();
            double current = ((Number) economy.getClass()
                    .getMethod("getBalance", offlinePlayerCls)
                    .invoke(economy, handle)).doubleValue();
            double delta = target - current;
            if (delta > 0.0) {
                economy.getClass()
                        .getMethod("depositPlayer", offlinePlayerCls, double.class)
                        .invoke(economy, handle, delta);
            } else if (delta < 0.0) {
                economy.getClass()
                        .getMethod("withdrawPlayer", offlinePlayerCls, double.class)
                        .invoke(economy, handle, -delta);
            }
        } catch (Throwable t) {
            logger.warning("Failed to write balance for " + player.getName() + ": " + t.getMessage());
        }
    }

    public void handleJoin(PDSPlayer player) {
        if (isWorldExcluded(player.getWorldName())) {
            skippedByWorld.increment();
            logger.info("Skipped loading for " + player.getName() + " in excluded world " + player.getWorldName());
            return;
        }

        loadAttempts.increment();
        syncInProgress.put(player.getUniqueId(), true);

        String syncStarted = platform.getConfigString("messages.sync_started", "&7Syncing your data...");
        if (!syncStarted.isEmpty()) {
            platform.sendMessage(player.getUniqueId(), syncStarted);
        }

        long startTime = System.currentTimeMillis();
        storage.load(player.getUniqueId()).thenAccept(optionalData -> {
            if (optionalData.isPresent()) {
                PlayerData data = optionalData.get();
                platform.runTask(() -> applyData(player, data, startTime));
            } else {
                syncInProgress.remove(player.getUniqueId());
                loadSuccess.increment();
            }
        }).exceptionally(ex -> {
            syncInProgress.remove(player.getUniqueId());
            loadFailed.increment();
            trackError("load data for " + player.getName(), ex);
            String syncFailed = platform.getConfigString("messages.sync_failed", "&cFailed to sync your data. Please contact an admin.");
            if (!syncFailed.isEmpty()) {
                platform.sendMessage(player.getUniqueId(), syncFailed);
            }

            if (discordManager != null && platform.getConfigBoolean("discord.events.sync_failed", true)) {
                discordManager.sendSyncFailed(player.getName(), ex.getMessage());
            }
            return null;
        });
    }

    private void applyData(PDSPlayer player, PlayerData data, long startTime) {
        if (platform.isOnline(player.getUniqueId())) {
            try {
                versionHandler.apply(player, data);

                if (platform.getConfigBoolean("sync.economy", true)) {
                    writeBalance(player, data.balance);
                }

                if (data.inventoryContents != null) {
                    inventoryHashes.put(player.getUniqueId(), data.inventoryContents.hashCode());
                }

                long duration = System.currentTimeMillis() - startTime;
                lastLoadDurationMs.set(duration);
                loadSuccess.increment();

                String syncComplete = platform.getConfigString("messages.sync_complete", "&aData synced successfully!");
                if (!syncComplete.isEmpty()) {
                    platform.sendMessage(player.getUniqueId(), syncComplete);
                }
                logger.info("Successfully synced data for player: " + player.getName() + " (" + duration + "ms)");

                if (discordManager != null && platform.getConfigBoolean("discord.events.sync_success", true)) {
                    discordManager.sendSyncSuccess(player.getName(), duration);
                }
            } catch (Exception ex) {
                loadFailed.increment();
                trackError("apply data for " + player.getName(), ex);
            }
        }
        syncInProgress.remove(player.getUniqueId());
    }

    public void handleQuit(PDSPlayer player) {
        handleQuit(player, false);
    }

    public void handleQuit(PDSPlayer player, boolean isAutosave) {
        if (isWorldExcluded(player.getWorldName())) {
            skippedByWorld.increment();
            logger.info("Skipped saving for " + player.getName() + " in excluded world " + player.getWorldName());
            return;
        }

        if (isSyncInProgress(player.getUniqueId())) {
            logger.warning("Player " + player.getName() + " quit while sync was still in progress!");
        }

        long now = System.currentTimeMillis();
        long minSaveIntervalMs = getConfigLong("sync.min_save_interval_ms", 0L);
        boolean enforceOnQuit = platform.getConfigBoolean("sync.enforce_cooldown_on_quit", false);
        if (minSaveIntervalMs > 0L && (isAutosave || enforceOnQuit)) {
            Long lastSaved = lastSaveMillis.get(player.getUniqueId());
            if (lastSaved != null && now - lastSaved < minSaveIntervalMs) {
                skippedByCooldown.increment();
                return;
            }
        }

        saveAttempts.increment();
        PlayerData data = versionHandler.capture(player);
        if (platform.getConfigBoolean("sync.economy", true)) {
            data.balance = readBalance(player);
        }
        filterData(data); // Apply config filters

        if (data.inventoryContents != null) {
            int currentHash = data.inventoryContents.hashCode();
            Integer lastHash = inventoryHashes.get(player.getUniqueId());
            if (lastHash != null && lastHash == currentHash && !platform.getConfigBoolean("sync.force_save_on_quit", false)) {
                skippedByHash.increment();
                return;
            }
        } else {
            inventoryHashes.remove(player.getUniqueId());
        }

        long startTime = System.currentTimeMillis();
        storage.save(data).thenRun(() -> {
            long duration = System.currentTimeMillis() - startTime;
            lastSaveDurationMs.set(duration);
            saveSuccess.increment();
            lastSaveMillis.put(player.getUniqueId(), System.currentTimeMillis());
            if (data.inventoryContents != null) {
                inventoryHashes.put(player.getUniqueId(), data.inventoryContents.hashCode());
            }
            if (!isAutosave) {
                logger.info("Saved data for player: " + player.getName());
                if (redisManager != null) {
                    redisManager.publish("saved:" + player.getUniqueId() + ":" + nodeId);
                }
            }
        }).exceptionally(ex -> {
            saveFailed.increment();
            trackError("save data for " + player.getName(), ex);
            return null;
        });
    }

    private void filterData(PlayerData data) {
        if (!platform.getConfigBoolean("sync.inventory", true)) data.inventoryContents = null;
        if (!platform.getConfigBoolean("sync.ender_chest", true)) data.enderChestContents = null;
        if (!platform.getConfigBoolean("sync.health", true)) data.health = 20.0;
        if (!platform.getConfigBoolean("sync.experience", true)) {
            data.exp = 0;
            data.level = 0;
            data.totalExperience = 0;
        }
        if (!platform.getConfigBoolean("sync.potion_effects", true)) data.potionEffects = null;
        if (!platform.getConfigBoolean("sync.food", true)) {
            data.foodLevel = 20;
            data.saturation = 5.0f;
        }
        if (!platform.getConfigBoolean("sync.game_mode", true)) data.gameMode = "SURVIVAL";
        if (!platform.getConfigBoolean("sync.flight", true)) {
            data.isFlying = false;
            data.canFly = false;
        }
        if (!platform.getConfigBoolean("sync.attributes", true)) data.attributes = null;
        if (!platform.getConfigBoolean("sync.pdc", true)) data.persistentDataContainer = null;
        // Location restore is opt-in: it teleports on join, so it must never turn on by surprise.
        if (!platform.getConfigBoolean("sync.location", false)) data.worldName = null;
        if (!platform.getConfigBoolean("sync.advancements", true)) data.advancements = null;
        if (!platform.getConfigBoolean("sync.statistics", true)) data.statistics = null;
        if (!platform.getConfigBoolean("sync.air_level", true)) data.airLevel = 300;
        if (!platform.getConfigBoolean("sync.fire_ticks", true)) data.fireTicks = 0;
        if (!platform.getConfigBoolean("sync.player_time", true)) data.playerTime = -1;
        if (!platform.getConfigBoolean("sync.player_weather", true)) data.playerWeather = null;
        if (!platform.getConfigBoolean("sync.freeze_ticks", true)) data.freezeTicks = 0;
        if (!platform.getConfigBoolean("sync.arrows_in_body", true)) data.arrowsInBody = 0;
        if (!platform.getConfigBoolean("sync.absorption", true)) data.absorptionAmount = 0.0;
        if (!platform.getConfigBoolean("sync.speeds", true)) {
            data.walkSpeed = 0.2f;
            data.flySpeed = 0.1f;
        }
        if (!platform.getConfigBoolean("sync.fall_distance", true)) data.fallDistance = 0.0f;
    }

    public boolean isSyncInProgress(UUID uuid) {
        return syncInProgress.getOrDefault(uuid, false);
    }

    public CompletableFuture<Optional<PlayerData>> loadStoredData(UUID uuid) {
        return storage.load(uuid);
    }

    public SyncStats getStatsSnapshot() {
        return new SyncStats(
                syncInProgress.size(),
                loadAttempts.sum(),
                loadSuccess.sum(),
                loadFailed.sum(),
                saveAttempts.sum(),
                saveSuccess.sum(),
                saveFailed.sum(),
                skippedByWorld.sum(),
                skippedByCooldown.sum(),
                skippedByHash.sum(),
                lastLoadDurationMs.get(),
                lastSaveDurationMs.get(),
                lastErrorAt.get(),
                lastErrorMessage,
                Collections.unmodifiableSet(excludedWorlds)
        );
    }

    private void trackError(String context, Throwable throwable) {
        String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
        lastErrorMessage = context + ": " + message;
        lastErrorAt.set(System.currentTimeMillis());
        logger.severe("Failed to " + context + ": " + message);
    }

    private boolean isWorldExcluded(String worldName) {
        return !excludedWorlds.isEmpty() && excludedWorlds.contains(normalizeWorldName(worldName));
    }

    private String normalizeWorldName(String worldName) {
        if (worldName == null) return "";
        return worldName.trim().toLowerCase(Locale.ROOT);
    }

    private long getConfigLong(String path, long defaultValue) {
        try {
            String value = platform.getConfigString(path, Long.toString(defaultValue));
            if (value == null || value.isBlank()) {
                return defaultValue;
            }
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public static final class SyncStats {
        public final int activeSyncs;
        public final long loadAttempts;
        public final long loadSuccess;
        public final long loadFailed;
        public final long saveAttempts;
        public final long saveSuccess;
        public final long saveFailed;
        public final long skippedByWorld;
        public final long skippedByCooldown;
        public final long skippedByHash;
        public final long lastLoadDurationMs;
        public final long lastSaveDurationMs;
        public final long lastErrorAtEpochMs;
        public final String lastErrorMessage;
        public final Set<String> excludedWorlds;

        public SyncStats(
                int activeSyncs,
                long loadAttempts,
                long loadSuccess,
                long loadFailed,
                long saveAttempts,
                long saveSuccess,
                long saveFailed,
                long skippedByWorld,
                long skippedByCooldown,
                long skippedByHash,
                long lastLoadDurationMs,
                long lastSaveDurationMs,
                long lastErrorAtEpochMs,
                String lastErrorMessage,
                Set<String> excludedWorlds
        ) {
            this.activeSyncs = activeSyncs;
            this.loadAttempts = loadAttempts;
            this.loadSuccess = loadSuccess;
            this.loadFailed = loadFailed;
            this.saveAttempts = saveAttempts;
            this.saveSuccess = saveSuccess;
            this.saveFailed = saveFailed;
            this.skippedByWorld = skippedByWorld;
            this.skippedByCooldown = skippedByCooldown;
            this.skippedByHash = skippedByHash;
            this.lastLoadDurationMs = lastLoadDurationMs;
            this.lastSaveDurationMs = lastSaveDurationMs;
            this.lastErrorAtEpochMs = lastErrorAtEpochMs;
            this.lastErrorMessage = lastErrorMessage;
            this.excludedWorlds = excludedWorlds;
        }
    }
}

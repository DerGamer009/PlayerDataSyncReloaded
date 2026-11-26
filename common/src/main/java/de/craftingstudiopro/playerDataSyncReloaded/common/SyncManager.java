package de.craftingstudiopro.playerDataSyncReloaded.common;

import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.redis.RedisManager;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
import de.craftingstudiopro.playerDataSyncReloaded.common.util.DiscordWebhookManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Logger;

public class SyncManager {
    private final Platform platform;
    private final Storage storage;
    private final VersionHandler versionHandler;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, Boolean> syncInProgress = new ConcurrentHashMap<>();
    /** A load requested while one is running; the value is true if any of the requests was forced. */
    private final ConcurrentHashMap<UUID, Boolean> reloadRequested = new ConcurrentHashMap<>();
    /** Players whose stored record has been applied on this server. Saves are refused for everyone else. */
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    /** savedAt of the record this server applied, so the same record is not applied twice. */
    private final ConcurrentHashMap<UUID, Long> appliedSavedAt = new ConcurrentHashMap<>();
    /** Serialized inventory last saved or applied; an identical inventory skips the save. */
    private final ConcurrentHashMap<UUID, String> lastSavedInventory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastSaveMillis = new ConcurrentHashMap<>();
    /** Server name stored in the applied record ({@code /pds info}). */
    private final ConcurrentHashMap<UUID, String> appliedSavedBy = new ConcurrentHashMap<>();
    /**
     * savedAt of the stored record this server's state is based on: 0 = none was applied,
     * {@link #BASIS_UNCHECKED} = unknown (after {@code /pds reload}), saves then skip the check.
     */
    private final ConcurrentHashMap<UUID, Long> basisSavedAt = new ConcurrentHashMap<>();
    /** savedAt of this server's newest save per player, so the recheck does not mistake it for a foreign record. */
    private final ConcurrentHashMap<UUID, Long> ownSavedAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> loadRetries = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastStaleReload = new ConcurrentHashMap<>();
    /** Saves refused for this player since the join ({@code /pds info}). */
    private final ConcurrentHashMap<UUID, Integer> refusedSaves = new ConcurrentHashMap<>();
    private final Set<UUID> recheckScheduled = ConcurrentHashMap.newKeySet();
    private static final long BASIS_UNCHECKED = -1L;
    private static final int MAX_LOAD_RETRIES = 3;
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
    /** Saves refused because another server had stored a newer record. */
    private final LongAdder refusedStale = new LongAdder();
    /** Joins where the recheck found a newer record and loaded it. */
    private final LongAdder outdatedReloads = new LongAdder();
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
        handleJoin(player, false);
    }

    /**
     * Loads the stored record and applies it. Loads are queued per player: a load requested while
     * another one is running starts afterwards, so the newest record is applied last.
     *
     * @param force apply the record even if it is the one already applied ({@code /pds load}, API)
     */
    public void handleJoin(PDSPlayer player, boolean force) {
        UUID uuid = player.getUniqueId();
        if (isWorldExcluded(player.getWorldName())) {
            skippedByWorld.increment();
            loaded.add(uuid);
            // No record applied: a later save must not overwrite one another server stored.
            basisSavedAt.putIfAbsent(uuid, 0L);
            logger.info("Skipped loading for " + player.getName() + " in excluded world " + player.getWorldName());
            return;
        }

        if (syncInProgress.putIfAbsent(uuid, true) != null) {
            reloadRequested.merge(uuid, force, Boolean::logicalOr);
            return;
        }

        loadAttempts.increment();
        String syncStarted = platform.getConfigString("messages.sync_started", "&7Syncing your data...");
        // Only on the first load: the proxy and Redis trigger further loads for players already in sync.
        if (!syncStarted.isEmpty() && !loaded.contains(uuid)) {
            platform.sendMessage(player.getUniqueId(), syncStarted);
        }

        long startTime = System.currentTimeMillis();
        storage.load(uuid).thenAccept(optionalData -> {
            if (optionalData.isPresent()) {
                PlayerData data = optionalData.get();
                platform.runTask(() -> applyData(player, data, startTime, force));
            } else {
                loaded.add(uuid);
                basisSavedAt.put(uuid, 0L);
                appliedSavedBy.remove(uuid);
                loadRetries.remove(uuid);
                loadSuccess.increment();
                finishLoad(player);
                scheduleRecheck(uuid);
            }
        }).exceptionally(ex -> {
            finishLoad(player);
            loadFailed.increment();
            trackError("load data for " + player.getName(), ex);

            int attempt = loadRetries.merge(uuid, 1, Integer::sum);
            if (attempt <= MAX_LOAD_RETRIES) {
                long delay = 1000L << (attempt - 1);
                logger.warning("Retrying load for " + player.getName() + " in " + delay + " ms (attempt " + attempt + "/" + MAX_LOAD_RETRIES + ").");
                CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> platform.runTask(() -> {
                    PDSPlayer current = platform.getPlayer(uuid);
                    if (current != null && platform.isOnline(uuid)) {
                        handleJoin(current, force);
                    }
                }));
                return null;
            }
            loadRetries.remove(uuid);

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

    private void finishLoad(PDSPlayer player) {
        UUID uuid = player.getUniqueId();
        syncInProgress.remove(uuid);
        Boolean force = reloadRequested.remove(uuid);
        if (force != null) {
            platform.runTask(() -> {
                if (platform.isOnline(uuid)) {
                    handleJoin(player, force);
                }
            });
        }
    }

    private void applyData(PDSPlayer player, PlayerData data, long startTime, boolean force) {
        UUID uuid = player.getUniqueId();
        if (platform.isOnline(uuid)) {
            Long applied = appliedSavedAt.get(uuid);
            if (!force && data.savedAt != 0L && applied != null && applied == data.savedAt) {
                // A late Redis notification for a record we already have; applying it again would
                // undo what the player did since.
                loadSuccess.increment();
                logger.info("Skipped reload for " + player.getName() + ": stored data is already applied.");
                finishLoad(player);
                return;
            }

            try {
                versionHandler.apply(player, data);
                appliedSavedAt.put(uuid, data.savedAt);
                basisSavedAt.put(uuid, data.savedAt);
                if (data.savedBy != null && !data.savedBy.isBlank()) {
                    appliedSavedBy.put(uuid, data.savedBy);
                } else {
                    appliedSavedBy.remove(uuid);
                }
                loadRetries.remove(uuid);
                loaded.add(uuid);

                if (platform.getConfigBoolean("sync.economy", true)) {
                    writeBalance(player, data.balance);
                }

                if (data.inventoryContents != null) {
                    lastSavedInventory.put(uuid, data.inventoryContents);
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
                scheduleRecheck(uuid);
            } catch (Exception ex) {
                loadFailed.increment();
                trackError("apply data for " + player.getName(), ex);
            }
        }
        finishLoad(player);
    }

    /**
     * Without the save handshake the new server can read the record before the old server's save
     * has landed. Look once more shortly after the join and load a newer record from another server.
     */
    private void scheduleRecheck(UUID uuid) {
        long delay = getConfigLong("sync.recheck_after_join_ms", 3000L);
        if (delay <= 0L || !recheckScheduled.add(uuid)) {
            return;
        }
        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
            if (!platform.isOnline(uuid) || !loaded.contains(uuid) || isSyncInProgress(uuid)) {
                return;
            }
            storage.load(uuid).thenAccept(optionalData -> {
                if (optionalData.isEmpty()) {
                    return;
                }
                long storedAt = optionalData.get().savedAt;
                Long basis = basisSavedAt.get(uuid);
                Long own = ownSavedAt.get(uuid);
                boolean newer = storedAt != 0L && basis != null && basis != BASIS_UNCHECKED
                        && storedAt != basis && (own == null || storedAt != own);
                if (!newer) {
                    return;
                }
                platform.runTask(() -> {
                    PDSPlayer player = platform.getPlayer(uuid);
                    if (player != null) {
                        outdatedReloads.increment();
                        logger.warning("Loaded an outdated record for " + player.getName() + "; another server saved a newer one. Loading it now.");
                        handleJoin(player, false);
                    }
                });
            }).exceptionally(ex -> null);
        });
    }

    private void reloadAfterStaleSave(PDSPlayer player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastStaleReload.get(uuid);
        if (last != null && now - last < 10_000L) {
            return; // e.g. a record this server cannot decrypt: do not reload on every autosave
        }
        lastStaleReload.put(uuid, now);
        platform.runTask(() -> {
            PDSPlayer current = platform.getPlayer(uuid);
            if (current != null && platform.isOnline(uuid)) {
                handleJoin(current, true);
            }
        });
    }

    public CompletableFuture<Void> handleQuit(PDSPlayer player) {
        return handleQuit(player, false);
    }

    /** The player left this server: save, then forget the per-session state. */
    public CompletableFuture<Void> handleDisconnect(PDSPlayer player) {
        CompletableFuture<Void> save = handleQuit(player, false);
        UUID uuid = player.getUniqueId();
        loaded.remove(uuid);
        appliedSavedAt.remove(uuid);
        appliedSavedBy.remove(uuid);
        reloadRequested.remove(uuid);
        basisSavedAt.remove(uuid);
        loadRetries.remove(uuid);
        recheckScheduled.remove(uuid);
        lastStaleReload.remove(uuid);
        refusedSaves.remove(uuid);
        save.whenComplete((unused, ex) -> {
            if (!platform.isOnline(uuid)) {
                ownSavedAt.remove(uuid);
            }
        });
        return save;
    }

    /** Treats the player's current state as applied, e.g. for players online during {@code /pds reload}. */
    public void markLoaded(PDSPlayer player) {
        loaded.add(player.getUniqueId());
        // The record this state came from is unknown: the next save is written without the check.
        basisSavedAt.put(player.getUniqueId(), BASIS_UNCHECKED);
    }

    /** Saves every given player; used on shutdown and reload. Failures do not fail the returned future. */
    public CompletableFuture<Void> saveAll(Iterable<PDSPlayer> players) {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (PDSPlayer player : players) {
            saves.add(handleQuit(player, false).exceptionally(ex -> null));
        }
        return CompletableFuture.allOf(saves.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<Void> handleQuit(PDSPlayer player, boolean isAutosave) {
        if (isWorldExcluded(player.getWorldName())) {
            skippedByWorld.increment();
            logger.info("Skipped saving for " + player.getName() + " in excluded world " + player.getWorldName());
            return CompletableFuture.completedFuture(null);
        }

        if (isSyncInProgress(player.getUniqueId())) {
            logger.warning("Skipped saving for " + player.getName() + ": data load still in progress.");
            return CompletableFuture.completedFuture(null);
        }

        if (!loaded.contains(player.getUniqueId())) {
            // Saving now would write this server's local player data, the state from the player's
            // last visit, over the record the previous server just saved.
            logger.warning("Skipped saving for " + player.getName() + ": stored data was never applied on this server (load failed?).");
            return CompletableFuture.completedFuture(null);
        }

        long now = System.currentTimeMillis();
        long minSaveIntervalMs = getConfigLong("sync.min_save_interval_ms", 0L);
        boolean enforceOnQuit = platform.getConfigBoolean("sync.enforce_cooldown_on_quit", false);
        if (minSaveIntervalMs > 0L && (isAutosave || enforceOnQuit)) {
            Long lastSaved = lastSaveMillis.get(player.getUniqueId());
            if (lastSaved != null && now - lastSaved < minSaveIntervalMs) {
                skippedByCooldown.increment();
                return CompletableFuture.completedFuture(null);
            }
        }

        saveAttempts.increment();
        PlayerData data = versionHandler.capture(player);
        data.savedAt = System.currentTimeMillis();
        data.savedBy = serverName();
        if (platform.getConfigBoolean("sync.economy", true)) {
            data.balance = readBalance(player);
        }
        filterData(data); // Apply config filters

        if (data.inventoryContents != null) {
            // Compare the full serialized inventory: a hash collision must not drop a changed inventory.
            if (data.inventoryContents.equals(lastSavedInventory.get(player.getUniqueId()))
                    && !platform.getConfigBoolean("sync.force_save_on_quit", false)) {
                skippedByHash.increment();
                return CompletableFuture.completedFuture(null);
            }
        } else {
            lastSavedInventory.remove(player.getUniqueId());
        }

        UUID uuid = player.getUniqueId();
        long basis = basisSavedAt.getOrDefault(uuid, BASIS_UNCHECKED);
        ownSavedAt.put(uuid, data.savedAt);
        long startTime = System.currentTimeMillis();
        CompletableFuture<Boolean> write = basis == BASIS_UNCHECKED
                ? storage.save(data).thenApply(v -> true)
                : storage.saveChecked(data, basis, nodeId);
        return write.thenAccept(written -> {
            if (!written) {
                onStaleSave(player);
                return;
            }
            if (platform.isOnline(uuid)) {
                // From now on our own record is the one this server's state is based on.
                basisSavedAt.computeIfPresent(uuid, (k, v) -> data.savedAt);
            }

            long duration = System.currentTimeMillis() - startTime;
            lastSaveDurationMs.set(duration);
            saveSuccess.increment();
            lastSaveMillis.put(uuid, System.currentTimeMillis());
            if (data.inventoryContents != null) {
                lastSavedInventory.put(uuid, data.inventoryContents);
            }
            if (!isAutosave) {
                logger.info("Saved data for player: " + player.getName());
                if (redisManager != null) {
                    redisManager.publish("saved:" + uuid + ":" + nodeId);
                }
            }
        }).whenComplete((unused, ex) -> {
            if (ex != null) {
                saveFailed.increment();
                trackError("save data for " + player.getName(), ex);
            }
        });
    }

    /** Another server stored a newer record after this server loaded the player. Nothing was written. */
    private void onStaleSave(PDSPlayer player) {
        UUID uuid = player.getUniqueId();
        boolean online = platform.isOnline(uuid);
        refusedStale.increment();
        refusedSaves.merge(uuid, 1, Integer::sum);
        logger.warning("Refused to save data for " + player.getName()
                + ": another server saved a newer record after this server loaded the player. Keeping the stored record"
                + (online ? " and loading it now." : "."));
        if (discordManager != null && platform.getConfigBoolean("discord.events.save_refused", true)) {
            discordManager.sendSaveRefused(player.getName(), serverName());
        }
        if (online) {
            reloadAfterStaleSave(player);
        }
    }

    /** {@code server_name} from the config, or what the platform can tell about itself. */
    private String serverName() {
        String configured = platform.getConfigString("server_name", "");
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return platform.getDefaultServerName();
    }

    public PlayerSyncState getPlayerState(UUID uuid) {
        return new PlayerSyncState(
                loaded.contains(uuid),
                isSyncInProgress(uuid),
                reloadRequested.containsKey(uuid),
                appliedSavedAt.getOrDefault(uuid, 0L),
                lastSaveMillis.getOrDefault(uuid, 0L),
                appliedSavedBy.getOrDefault(uuid, ""),
                refusedSaves.getOrDefault(uuid, 0)
        );
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
                refusedStale.sum(),
                outdatedReloads.sum(),
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

    /** Per-player state shown by {@code /pds info}. */
    public static final class PlayerSyncState {
        public final boolean loaded;
        public final boolean syncInProgress;
        public final boolean reloadQueued;
        public final long appliedSavedAt;
        public final long lastSavedAt;
        /** {@code server_name} of the server that saved the applied record, "" if unknown. */
        public final String appliedSavedBy;
        public final int refusedSaves;

        public PlayerSyncState(boolean loaded, boolean syncInProgress, boolean reloadQueued, long appliedSavedAt,
                               long lastSavedAt, String appliedSavedBy, int refusedSaves) {
            this.loaded = loaded;
            this.syncInProgress = syncInProgress;
            this.reloadQueued = reloadQueued;
            this.appliedSavedAt = appliedSavedAt;
            this.lastSavedAt = lastSavedAt;
            this.appliedSavedBy = appliedSavedBy;
            this.refusedSaves = refusedSaves;
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
        public final long refusedStale;
        public final long outdatedReloads;
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
                long refusedStale,
                long outdatedReloads,
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
            this.refusedStale = refusedStale;
            this.outdatedReloads = outdatedReloads;
            this.lastLoadDurationMs = lastLoadDurationMs;
            this.lastSaveDurationMs = lastSaveDurationMs;
            this.lastErrorAtEpochMs = lastErrorAtEpochMs;
            this.lastErrorMessage = lastErrorMessage;
            this.excludedWorlds = excludedWorlds;
        }
    }
}

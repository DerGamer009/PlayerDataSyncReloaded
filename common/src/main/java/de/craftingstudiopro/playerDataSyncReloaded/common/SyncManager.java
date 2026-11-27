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
    /**
     * The whole filtered record (without its stamps) as last saved in this session. A save of an
     * identical record is skipped. Cleared on every apply, so a record loaded from another server is
     * always written back once.
     */
    private final ConcurrentHashMap<UUID, String> lastSavedFingerprint = new ConcurrentHashMap<>();
    /** Saves still being written, so a quick rejoin loads only after this server's quit save has landed. */
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> inFlightSaves = new ConcurrentHashMap<>();
    private final com.google.gson.Gson fingerprintGson = new com.google.gson.Gson();
    /** Set once the server shuts down: disconnect saves after the shutdown save would only race with it. */
    private volatile boolean stopping;
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
    /**
     * Counts disconnects per player. A load only applies if no disconnect happened since it started.
     * The player object is not used for this: Fabric replaces it on every respawn.
     */
    private final ConcurrentHashMap<UUID, Long> sessions = new ConcurrentHashMap<>();
    /** Players whose load failed for good: still not loaded (no saves), but no longer locked on Paper. */
    private final Set<UUID> gaveUp = ConcurrentHashMap.newKeySet();
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
            return Double.NaN;
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

        CompletableFuture<Void> pendingSave = inFlightSaves.get(uuid);
        if (pendingSave != null && !pendingSave.isDone()) {
            // The player rejoined while this server's quit save is still being written: reading now
            // would apply the record before it and the save would then overwrite nothing useful.
            pendingSave.handle((unused, ex) -> null).thenRun(() -> platform.runTask(() -> {
                PDSPlayer current = platform.getPlayer(uuid);
                if (current != null) {
                    handleJoin(current, force);
                }
            }));
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
        long session = sessions.getOrDefault(uuid, 0L);
        gaveUp.remove(uuid);
        storage.load(uuid).thenAccept(optionalData -> {
            if (optionalData.isPresent()) {
                PlayerData data = optionalData.get();
                platform.runTask(() -> applyData(player, data, startTime, force, session));
            } else {
                loaded.add(uuid);
                basisSavedAt.put(uuid, 0L);
                appliedSavedBy.remove(uuid);
                loadRetries.remove(uuid);
                loadSuccess.increment();
                finishLoad(uuid);
                scheduleRecheck(uuid);
            }
        }).exceptionally(ex -> {
            onLoadFailed(player, force, ex);
            return null;
        });
    }

    /** A load or apply failed: retry after 1, 2 and 4 seconds, then tell the player and the team. */
    private void onLoadFailed(PDSPlayer player, boolean force, Throwable ex) {
        UUID uuid = player.getUniqueId();
        loadFailed.increment();
        trackError("load data for " + player.getName(), ex);

        int attempt = loadRetries.merge(uuid, 1, Integer::sum);
        if (attempt <= MAX_LOAD_RETRIES) {
            // The retry replaces any load queued meanwhile; running both would apply twice.
            syncInProgress.remove(uuid);
            Boolean queued = reloadRequested.remove(uuid);
            boolean retryForce = force || Boolean.TRUE.equals(queued);
            long delay = 1000L << (attempt - 1);
            logger.warning("Retrying load for " + player.getName() + " in " + delay + " ms (attempt " + attempt + "/" + MAX_LOAD_RETRIES + ").");
            CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> platform.runTask(() -> {
                PDSPlayer current = platform.getPlayer(uuid);
                if (current != null) {
                    handleJoin(current, retryForce);
                }
            }));
            return;
        }
        loadRetries.remove(uuid);
        finishLoad(uuid);

        if (loaded.contains(uuid)) {
            // A reload (proxy, Redis, recheck) for a player whose data is already in place: keep it.
            logger.warning("Reload for " + player.getName() + " failed; keeping the data applied earlier.");
            return;
        }
        // Saves stay refused, but the player is no longer held in place: /pds load or a rejoin retries.
        gaveUp.add(uuid);
        logger.severe("Giving up loading data for " + player.getName() + ". Their progress on this server is not saved until "
                + "/pds load " + player.getName() + " or a rejoin succeeds.");
        platform.runTask(() -> {
            String syncFailed = platform.getConfigString("messages.sync_failed", "&cFailed to sync your data. Please contact an admin.");
            if (!syncFailed.isEmpty() && platform.isOnline(uuid)) {
                platform.sendMessage(uuid, syncFailed);
            }
        });
        if (discordManager != null && platform.getConfigBoolean("discord.events.sync_failed", true)) {
            discordManager.sendSyncFailed(player.getName(), ex.getMessage());
        }
    }

    private void finishLoad(UUID uuid) {
        syncInProgress.remove(uuid);
        Boolean force = reloadRequested.remove(uuid);
        if (force != null) {
            platform.runTask(() -> {
                // Look the player up again: after a reconnect the old player object is gone.
                PDSPlayer current = platform.getPlayer(uuid);
                if (current != null) {
                    handleJoin(current, force);
                }
            });
        }
    }

    private void applyData(PDSPlayer loadingPlayer, PlayerData data, long startTime, boolean force, long session) {
        UUID uuid = loadingPlayer.getUniqueId();
        PDSPlayer player = platform.getPlayer(uuid);
        if (player == null || sessions.getOrDefault(uuid, 0L) != session) {
            // The player left, or left and reconnected, while the record was loading. Applying it then
            // would mark the new session as loaded with data meant for the old one.
            finishLoad(uuid);
            return;
        }
        // Apply to the current player object: on Fabric a respawn replaces it.
        Long applied = appliedSavedAt.get(uuid);
        if (!force && data.savedAt != 0L && applied != null && applied == data.savedAt) {
            // A late Redis notification for a record we already have; applying it again would
            // undo what the player did since.
            loadSuccess.increment();
            logger.info("Skipped reload for " + player.getName() + ": stored data is already applied.");
            finishLoad(uuid);
            return;
        }

        try {
            filterForApply(data);
            versionHandler.apply(player, data);
        } catch (Throwable ex) {
            // Includes linkage errors from a handler that does not fit this server version. The
            // player stays unloaded, so this server's local data is never saved over the record.
            onLoadFailed(player, force, ex);
            return;
        }

        appliedSavedAt.put(uuid, data.savedAt);
        basisSavedAt.put(uuid, data.savedAt);
        if (data.savedBy != null && !data.savedBy.isBlank()) {
            appliedSavedBy.put(uuid, data.savedBy);
        } else {
            appliedSavedBy.remove(uuid);
        }
        loadRetries.remove(uuid);
        lastSavedFingerprint.remove(uuid);
        loaded.add(uuid);

        if (data.isSynced("economy")) {
            writeBalance(player, data.balance);
        }

        long duration = System.currentTimeMillis() - startTime;
        lastLoadDurationMs.set(duration);
        loadSuccess.increment();

        String syncComplete = platform.getConfigString("messages.sync_complete", "&aData synced successfully!");
        if (!syncComplete.isEmpty()) {
            platform.sendMessage(uuid, syncComplete);
        }
        logger.info("Successfully synced data for player: " + player.getName() + " (" + duration + "ms)");

        if (discordManager != null && platform.getConfigBoolean("discord.events.sync_success", true)) {
            discordManager.sendSyncSuccess(player.getName(), duration);
        }
        scheduleRecheck(uuid);
        finishLoad(uuid);
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
        sessions.merge(uuid, 1L, Long::sum);
        gaveUp.remove(uuid);
        loaded.remove(uuid);
        appliedSavedAt.remove(uuid);
        appliedSavedBy.remove(uuid);
        reloadRequested.remove(uuid);
        basisSavedAt.remove(uuid);
        loadRetries.remove(uuid);
        recheckScheduled.remove(uuid);
        lastStaleReload.remove(uuid);
        refusedSaves.remove(uuid);
        lastSaveMillis.remove(uuid);
        lastSavedFingerprint.remove(uuid);
        save.whenComplete((unused, ex) -> platform.runTask(() -> {
            if (!platform.isOnline(uuid)) {
                ownSavedAt.remove(uuid);
                lastSavedFingerprint.remove(uuid);
            }
        }));
        return save;
    }

    /** Treats the player's current state as applied, e.g. for players online during {@code /pds reload}. */
    public void markLoaded(PDSPlayer player) {
        loaded.add(player.getUniqueId());
        // The record this state came from is unknown: the next save is written without the check.
        basisSavedAt.put(player.getUniqueId(), BASIS_UNCHECKED);
    }

    /** Whether the stored record has been applied to this player on this server. */
    public boolean isLoaded(UUID uuid) {
        return loaded.contains(uuid);
    }

    /** Whether the player waits for their stored data: not loaded and the load has not been given up. */
    public boolean isWaitingForData(UUID uuid) {
        return !loaded.contains(uuid) && !gaveUp.contains(uuid);
    }

    /** Saves every given player; used on reload. Failures do not fail the returned future. */
    public CompletableFuture<Void> saveAll(Iterable<PDSPlayer> players) {
        List<CompletableFuture<Void>> saves = new ArrayList<>();
        for (PDSPlayer player : players) {
            saves.add(save(player, false).exceptionally(ex -> null));
        }
        return CompletableFuture.allOf(saves.toArray(new CompletableFuture[0]));
    }

    /**
     * Saves every given player for the shutdown. Disconnect and quit saves that follow are skipped,
     * because they would race with these saves and be refused as stale.
     */
    public CompletableFuture<Void> shutdown(Iterable<PDSPlayer> players) {
        stopping = true;
        return saveAll(players);
    }

    public CompletableFuture<Void> handleQuit(PDSPlayer player, boolean isAutosave) {
        if (stopping) {
            return CompletableFuture.completedFuture(null);
        }
        return save(player, isAutosave);
    }

    private CompletableFuture<Void> save(PDSPlayer player, boolean isAutosave) {
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
        PlayerData data;
        try {
            data = versionHandler.capture(player);
        } catch (Throwable ex) {
            saveFailed.increment();
            trackError("capture data for " + player.getName(), ex);
            return CompletableFuture.failedFuture(ex);
        }
        if (platform.getConfigBoolean("sync.economy", true)) {
            data.balance = readBalance(player);
        }
        if (economy == null || Double.isNaN(data.balance)) {
            // No Vault economy here (or reading it failed): the stored 0 is not the player's balance,
            // and a server with an economy must not set them to 0.
            data.balance = 0.0;
            data.markNotSynced("economy");
        }
        filterData(data); // Apply config filters
        sanitize(data);

        UUID uuid = player.getUniqueId();
        // Compare the whole record, not only the inventory: XP, health, the ender chest and the rest
        // change without the inventory changing, and skipping those saves would roll them back.
        String fingerprint = fingerprintGson.toJson(data);
        if (fingerprint.equals(lastSavedFingerprint.get(uuid))
                && !platform.getConfigBoolean("sync.force_save_on_quit", false)) {
            skippedByHash.increment();
            return CompletableFuture.completedFuture(null);
        }
        data.savedAt = System.currentTimeMillis();
        data.savedBy = serverName();

        long basis = basisSavedAt.getOrDefault(uuid, BASIS_UNCHECKED);
        ownSavedAt.put(uuid, data.savedAt);
        long startTime = System.currentTimeMillis();
        CompletableFuture<Boolean> write = basis == BASIS_UNCHECKED
                ? storage.save(data).thenApply(v -> true)
                : storage.saveChecked(data, basis, nodeId);
        CompletableFuture<Void> result = write.thenAccept(written -> {
            if (!written) {
                onStaleSave(player);
                return;
            }
            if (platform.isOnline(uuid)) {
                // From now on our own record is the one this server's state is based on.
                basisSavedAt.computeIfPresent(uuid, (k, v) -> data.savedAt);
                // A load of our own record (proxy "load:", Redis) must not reapply it.
                appliedSavedAt.computeIfPresent(uuid, (k, v) -> data.savedAt);
                lastSavedFingerprint.put(uuid, fingerprint);
            }

            long duration = System.currentTimeMillis() - startTime;
            lastSaveDurationMs.set(duration);
            saveSuccess.increment();
            lastSaveMillis.put(uuid, System.currentTimeMillis());
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
        inFlightSaves.put(uuid, result);
        result.whenComplete((unused, ex) -> inFlightSaves.remove(uuid, result));
        return result;
    }

    /** NaN or infinite values (another plugin's bug) would make the record unwritable or unappliable. */
    private static void sanitize(PlayerData data) {
        data.health = finite(data.health, 20.0);
        data.healthScale = finite(data.healthScale, 20.0);
        data.absorptionAmount = finite(data.absorptionAmount, 0.0);
        data.saturation = (float) finite(data.saturation, 5.0);
        data.exhaustion = (float) finite(data.exhaustion, 0.0);
        data.exp = (float) Math.max(0.0, Math.min(1.0, finite(data.exp, 0.0)));
        data.walkSpeed = (float) finite(data.walkSpeed, 0.2);
        data.flySpeed = (float) finite(data.flySpeed, 0.1);
        data.fallDistance = (float) finite(data.fallDistance, 0.0);
        data.balance = finite(data.balance, 0.0);
        data.x = finite(data.x, 0.0);
        data.y = finite(data.y, 0.0);
        data.z = finite(data.z, 0.0);
        data.yaw = (float) finite(data.yaw, 0.0);
        data.pitch = (float) finite(data.pitch, 0.0);
        if (data.attributes != null) {
            data.attributes.entrySet().removeIf(e -> e.getValue() == null || !Double.isFinite(e.getValue()));
        }
    }

    private static double finite(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
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

    /**
     * Sync keys of {@code sync.*}. When one is off, the saving server stores a placeholder (kept for
     * servers older than 26.12-Alpha.2) and marks the key in {@link PlayerData#notSynced}, so newer
     * servers do not apply the placeholder over the player's own value.
     */
    static final String[] SYNC_KEYS = {
            "inventory", "ender_chest", "health", "experience", "potion_effects", "food", "game_mode",
            "flight", "attributes", "pdc", "advancements", "statistics", "air_level", "fire_ticks",
            "player_time", "player_weather", "freeze_ticks", "arrows_in_body", "absorption", "speeds",
            "fall_distance", "economy"
    };

    private boolean syncEnabled(String key) {
        return platform.getConfigBoolean("sync." + key, true);
    }

    private void filterData(PlayerData data) {
        for (String key : SYNC_KEYS) {
            if (!syncEnabled(key)) {
                data.markNotSynced(key);
            }
        }
        if (!syncEnabled("inventory")) data.inventoryContents = null;
        if (!syncEnabled("ender_chest")) data.enderChestContents = null;
        if (!syncEnabled("health")) data.health = 20.0;
        if (!syncEnabled("experience")) {
            data.exp = 0;
            data.level = 0;
            data.totalExperience = 0;
        }
        if (!syncEnabled("potion_effects")) data.potionEffects = null;
        if (!syncEnabled("food")) {
            data.foodLevel = 20;
            data.saturation = 5.0f;
        }
        if (!syncEnabled("game_mode")) data.gameMode = "SURVIVAL";
        if (!syncEnabled("flight")) {
            data.isFlying = false;
            data.canFly = false;
        }
        if (!syncEnabled("attributes")) data.attributes = null;
        if (!syncEnabled("pdc")) data.persistentDataContainer = null;
        // Location restore is opt-in: it teleports on join, so it must never turn on by surprise.
        if (!platform.getConfigBoolean("sync.location", false)) data.worldName = null;
        if (!syncEnabled("advancements")) data.advancements = null;
        if (!syncEnabled("statistics")) data.statistics = null;
        if (!syncEnabled("air_level")) data.airLevel = 300;
        if (!syncEnabled("fire_ticks")) data.fireTicks = 0;
        if (!syncEnabled("player_time")) data.playerTime = -1;
        if (!syncEnabled("player_weather")) data.playerWeather = null;
        if (!syncEnabled("freeze_ticks")) data.freezeTicks = 0;
        if (!syncEnabled("arrows_in_body")) data.arrowsInBody = 0;
        if (!syncEnabled("absorption")) data.absorptionAmount = 0.0;
        if (!syncEnabled("speeds")) {
            data.walkSpeed = 0.2f;
            data.flySpeed = 0.1f;
        }
        if (!syncEnabled("fall_distance")) data.fallDistance = 0.0f;
    }

    /**
     * Before applying: what this server does not sync is not applied either, whatever the saving
     * server stored. Location stays opt-in here as well.
     */
    private void filterForApply(PlayerData data) {
        for (String key : SYNC_KEYS) {
            if (!syncEnabled(key)) {
                data.markNotSynced(key);
            }
        }
        if (!platform.getConfigBoolean("sync.location", false)) data.worldName = null;
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
        // Fabric reports minecraft:the_nether where Paper reports world_nether: compare both as the dimension id.
        String dimension = WorldNames.toDimensionId(worldName);
        return dimension != null ? dimension : worldName.trim().toLowerCase(Locale.ROOT);
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

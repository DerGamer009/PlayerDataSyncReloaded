package de.craftingstudiopro.playerDataSyncReloaded.common;

import com.google.gson.Gson;
import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.common.util.Versions;
import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two backends (A and B) sharing one storage, driven the way the plugin's listeners drive SyncManager.
 * The storage stamps records like SqlStorage/MongoStorage do, so saveChecked() follows the same rules.
 */
class SyncManagerSwitchTest {

    /** In-memory storage with the stamp rules of SqlStorage#saveChecked and a gate to recreate the switch race. */
    static final class MemoryStorage implements Storage {
        private static final Gson GSON = new Gson();

        static final class Row {
            final String json;
            final long savedAt;
            final String savedBy;

            Row(String json, long savedAt, String savedBy) {
                this.json = json;
                this.savedAt = savedAt;
                this.savedBy = savedBy;
            }
        }

        final Map<UUID, Row> rows = new ConcurrentHashMap<>();
        volatile CompletableFuture<Void> gate = CompletableFuture.completedFuture(null);
        final AtomicInteger failLoads = new AtomicInteger();
        /** Holds checked saves back until it completes, to recreate a slow quit save. */
        volatile CompletableFuture<Void> saveGate = CompletableFuture.completedFuture(null);

        @Override public void init() {}
        @Override public void close() {}

        @Override
        public synchronized CompletableFuture<Void> save(PlayerData data) {
            rows.put(data.uuid, new Row(GSON.toJson(data), data.savedAt, null));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Boolean> saveChecked(PlayerData data, long basis, String nodeId) {
            String json = GSON.toJson(data);
            return saveGate.thenApplyAsync(v -> {
                synchronized (this) {
                    Row row = rows.get(data.uuid);
                    boolean ok = row == null || nodeId.equals(row.savedBy) || row.savedAt == basis;
                    if (ok) {
                        rows.put(data.uuid, new Row(json, data.savedAt, nodeId));
                    }
                    return ok;
                }
            });
        }

        @Override
        public CompletableFuture<Optional<PlayerData>> load(UUID uuid) {
            if (failLoads.getAndDecrement() > 0) {
                return CompletableFuture.failedFuture(new IllegalStateException("simulated database outage"));
            }
            Row row = rows.get(uuid); // read now: the stale moment
            Optional<PlayerData> result = row == null ? Optional.empty() : Optional.of(GSON.fromJson(row.json, PlayerData.class));
            return gate.thenApplyAsync(v -> result);
        }

        @Override public CompletableFuture<Optional<PlayerData>> loadLegacy(UUID uuid) { return load(uuid); }
        @Override public CompletableFuture<List<UUID>> getAllStoredUUIDs() { return CompletableFuture.completedFuture(new ArrayList<>(rows.keySet())); }

        String stored(UUID uuid) {
            Row row = rows.get(uuid);
            return row == null ? null : GSON.fromJson(row.json, PlayerData.class).inventoryContents;
        }
    }

    /** One backend: Platform + VersionHandler (= the server's local player state) + SyncManager. */
    static class Server implements Platform, VersionHandler {
        final String name;
        final Map<UUID, String> inventory = new ConcurrentHashMap<>();
        final Map<UUID, String> gameMode = new ConcurrentHashMap<>();
        final Map<UUID, Integer> level = new ConcurrentHashMap<>();
        /** The player object of the current session; a reconnect gets a new one. */
        final Map<UUID, Object> session = new ConcurrentHashMap<>();
        final List<Object> appliedTo = java.util.Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger failApply = new AtomicInteger();
        final Set<UUID> online = ConcurrentHashMap.newKeySet();
        final Map<String, String> config = new HashMap<>();
        final List<String> log = new ArrayList<>();
        final Logger logger;
        final SyncManager sync;

        Server(String name, Storage storage) {
            this.name = name;
            this.logger = Logger.getLogger("pds-test-" + name + "-" + UUID.randomUUID());
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override public void publish(LogRecord record) { synchronized (log) { log.add(record.getMessage()); } }
                @Override public void flush() {}
                @Override public void close() {}
            });
            config.put("server_name", name);
            config.put("sync.recheck_after_join_ms", "0");
            this.sync = new SyncManager(this, storage, this);
        }

        PDSPlayer player(UUID id) {
            Object handle = session.computeIfAbsent(id, k -> new Object());
            return new PDSPlayer() {
                @Override public UUID getUniqueId() { return id; }
                @Override public String getName() { return "Steve"; }
                @Override public Object getHandle() { return handle; }
                @Override public String getWorldName() { return "world"; }
            };
        }

        @Override public Logger getLogger() { return logger; }
        @Override public void runTask(Runnable task) { task.run(); }
        @Override public void runTaskAsync(Runnable task) { CompletableFuture.runAsync(task); }
        @Override public boolean isOnline(UUID uuid) { return online.contains(uuid); }
        @Override public PDSPlayer getPlayer(UUID uuid) { return online.contains(uuid) ? player(uuid) : null; }
        @Override public void sendMessage(UUID uuid, String message) {}
        @Override public String getConfigString(String path, String def) { return config.getOrDefault(path, def); }
        @Override public boolean getConfigBoolean(String path, boolean def) { return config.containsKey(path) ? Boolean.parseBoolean(config.get(path)) : def; }
        @Override public List<String> getConfigStringList(String path) { return List.of(); }

        @Override
        public PlayerData capture(PDSPlayer player) {
            PlayerData data = new PlayerData();
            data.uuid = player.getUniqueId();
            data.name = player.getName();
            data.health = 20;
            data.inventoryContents = inventory.get(player.getUniqueId());
            data.gameMode = gameMode.getOrDefault(player.getUniqueId(), "SURVIVAL");
            data.level = level.getOrDefault(player.getUniqueId(), 0);
            return data;
        }
        @Override
        public void apply(PDSPlayer player, PlayerData data) {
            // Mirrors BukkitBaseVersionHandler: only synced values are applied.
            if (failApply.getAndDecrement() > 0) {
                throw new IllegalStateException("simulated apply failure");
            }
            appliedTo.add(player.getHandle());
            if (data.isSynced("experience")) level.put(player.getUniqueId(), data.level);
            if (data.isSynced("inventory")) inventory.put(player.getUniqueId(), data.inventoryContents);
            if (data.isSynced("game_mode") && data.gameMode != null) gameMode.put(player.getUniqueId(), data.gameMode);
        }
        @Override public String serializeInventory(PDSPlayer player) { return inventory.get(player.getUniqueId()); }
        @Override public void deserializeInventory(PDSPlayer player, String data) { inventory.put(player.getUniqueId(), data); }
        @Override public void setItemExclusions(List<String> exclusions) {}

        /** The player disconnects and connects again: a new player object. */
        void reconnect(UUID id) {
            sync.handleDisconnect(player(id));
            online.remove(id);
            session.remove(id);
            online.add(id);
            sync.handleJoin(player(id));
        }

        void join(UUID id) throws Exception {
            online.add(id);
            sync.handleJoin(player(id));
            idle(id);
        }

        /** Join whose load reads now but completes only after the gate opens. */
        void joinStale(UUID id) {
            online.add(id);
            sync.handleJoin(player(id));
        }

        void idle(UUID id) throws Exception {
            long end = System.currentTimeMillis() + 5000;
            while (sync.isSyncInProgress(id) && System.currentTimeMillis() < end) {
                Thread.sleep(5);
            }
        }

        void quit(UUID id) throws Exception {
            CompletableFuture<Void> save = sync.handleDisconnect(player(id));
            online.remove(id);
            save.get(5, TimeUnit.SECONDS);
        }

        void autosave(UUID id) throws Exception {
            sync.handleQuit(player(id), true).get(5, TimeUnit.SECONDS);
        }

        boolean logged(String fragment) {
            synchronized (log) {
                return log.stream().anyMatch(line -> line.contains(fragment));
            }
        }
    }

    MemoryStorage storage;
    Server a;
    Server b;
    UUID steve;

    @BeforeEach
    void setUp() {
        storage = new MemoryStorage();
        a = new Server("A", storage);
        b = new Server("B", storage);
        steve = UUID.randomUUID();
    }

    /** A returns to A, reads the record before B's save landed, so A holds the 8-item record. */
    private void raceBackToA() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "8 items");
        a.quit(steve);
        b.join(steve);
        assertEquals("8 items", b.inventory.get(steve));
        b.inventory.put(steve, "15 items");

        storage.gate = new CompletableFuture<>();
        a.joinStale(steve);
        Thread.sleep(50);
        b.quit(steve);
        storage.gate.complete(null);
        a.idle(steve);
        assertEquals("8 items", a.inventory.get(steve), "the race: A applied the older record");
    }

    @Test
    void staleSaveIsRefusedAndNewerRecordLoaded() throws Exception {
        raceBackToA();
        a.inventory.put(steve, "8 items + 1 dirt");
        a.autosave(steve);
        Thread.sleep(100);
        a.idle(steve);

        assertEquals("15 items", storage.stored(steve), "B's save must survive");
        assertEquals("15 items", a.inventory.get(steve), "A loads the newer record");
        assertTrue(a.logged("Refused to save data for Steve"));
        assertEquals(1, a.sync.getStatsSnapshot().refusedStale);
        assertEquals("B", a.sync.getPlayerState(steve).appliedSavedBy);
        assertEquals(1, a.sync.getPlayerState(steve).refusedSaves);

        a.quit(steve);
        assertEquals("15 items", storage.stored(steve));
    }

    @Test
    void recheckAfterJoinHealsWithoutASave() throws Exception {
        a.config.put("sync.recheck_after_join_ms", "200");
        raceBackToA();
        Thread.sleep(600);
        a.idle(steve);
        assertEquals("15 items", a.inventory.get(steve));
        assertEquals(1, a.sync.getStatsSnapshot().outdatedReloads);
        assertTrue(a.logged("Loaded an outdated record for Steve"));
    }

    @Test
    void normalSwitchingNeverRefuses() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "1");
        a.autosave(steve);
        a.inventory.put(steve, "2");
        a.quit(steve);
        b.join(steve);
        assertEquals("2", b.inventory.get(steve));
        assertEquals("A", b.sync.getPlayerState(steve).appliedSavedBy);
        b.inventory.put(steve, "3");
        b.quit(steve);
        a.join(steve);
        assertEquals("3", a.inventory.get(steve));
        a.inventory.put(steve, "4");
        a.autosave(steve);
        a.inventory.put(steve, "5");
        a.quit(steve);
        a.join(steve);
        assertEquals("5", a.inventory.get(steve));
        a.inventory.put(steve, "6");
        a.quit(steve);

        assertEquals("6", storage.stored(steve));
        assertEquals(0, a.sync.getStatsSnapshot().refusedStale);
        assertEquals(0, b.sync.getStatsSnapshot().refusedStale);
    }

    @Test
    void reloadKeepsSaving() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "x");
        a.autosave(steve);

        Server reloaded = new Server("A", storage); // /pds reload creates a new SyncManager
        reloaded.online.add(steve);
        reloaded.inventory.put(steve, "y");
        reloaded.sync.markLoaded(reloaded.player(steve));
        reloaded.autosave(steve);
        assertEquals("y", storage.stored(steve));
        reloaded.inventory.put(steve, "z");
        reloaded.quit(steve);
        assertEquals("z", storage.stored(steve));
    }

    @Test
    void failedLoadIsRetriedInsteadOfKeepingLocalData() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "good");
        a.quit(steve);
        a.inventory.put(steve, "old local");
        storage.failLoads.set(1);
        a.join(steve);
        Thread.sleep(1500);
        a.idle(steve);

        assertEquals("good", a.inventory.get(steve));
        assertTrue(a.logged("Retrying load for Steve in 1000 ms (attempt 1/3)."));
        a.quit(steve);
        assertEquals("good", storage.stored(steve));
    }

    @Test
    void newPlayerOnTwoServersAtOnceKeepsTheFirstSave() throws Exception {
        // A applied "no record"; meanwhile B stored one. A must not overwrite it.
        storage.gate = new CompletableFuture<>();
        a.joinStale(steve);
        Thread.sleep(50);
        storage.gate.complete(null);
        a.idle(steve);
        storage.rows.put(steve, new MemoryStorage.Row(new Gson().toJson(record(steve, "from B")), 42L, "node-b"));

        a.inventory.put(steve, "from A");
        a.autosave(steve);
        Thread.sleep(100);
        a.idle(steve);
        assertEquals("from B", storage.stored(steve));
    }

    private static PlayerData record(UUID uuid, String inventory) {
        PlayerData data = new PlayerData();
        data.uuid = uuid;
        data.name = "Steve";
        data.inventoryContents = inventory;
        data.savedAt = 42L;
        return data;
    }

    @Test
    void disabledToggleOnSaverIsNotAppliedElsewhere() throws Exception {
        // Issue #7: A does not sync game mode; its placeholder "SURVIVAL" must not reach B.
        a.config.put("sync.game_mode", "false");
        a.join(steve);
        a.gameMode.put(steve, "CREATIVE");
        a.inventory.put(steve, "sword");
        a.quit(steve);

        PlayerData stored = storage.load(steve).get(5, TimeUnit.SECONDS).orElseThrow();
        assertTrue(stored.notSynced.contains("game_mode"));
        assertEquals("SURVIVAL", stored.gameMode, "placeholder kept for servers before 26.12-Alpha.2");

        b.gameMode.put(steve, "ADVENTURE"); // forced by B's server.properties
        b.join(steve);
        assertEquals("ADVENTURE", b.gameMode.get(steve), "B keeps its own game mode");
        assertEquals("sword", b.inventory.get(steve), "synced values are still applied");
    }

    @Test
    void disabledToggleOnLoaderIsNotApplied() throws Exception {
        a.join(steve);
        a.gameMode.put(steve, "CREATIVE");
        a.inventory.put(steve, "sword");
        a.quit(steve);

        b.config.put("sync.game_mode", "false");
        b.gameMode.put(steve, "ADVENTURE");
        b.join(steve);
        assertEquals("ADVENTURE", b.gameMode.get(steve));
        assertEquals("sword", b.inventory.get(steve));
    }

    @Test
    void oldRecordsWithoutMarkerApplyEverything() {
        PlayerData old = record(steve, "x");
        assertTrue(old.isSynced("game_mode"));
        assertTrue(old.isSynced("experience"));
    }

    @Test
    void worldNamesMapBetweenPlatforms() {
        assertEquals(WorldNames.OVERWORLD, WorldNames.toDimensionId("world"));
        assertEquals(WorldNames.NETHER, WorldNames.toDimensionId("world_nether"));
        assertEquals(WorldNames.END, WorldNames.toDimensionId("world_the_end"));
        assertEquals("minecraft:overworld", WorldNames.toDimensionId("minecraft:overworld"));
        assertEquals(null, WorldNames.toDimensionId("lobby"));
        assertEquals("NETHER", WorldNames.environmentFor("minecraft:the_nether"));
        assertEquals("NORMAL", WorldNames.environmentFor("world"));
        assertEquals(null, WorldNames.environmentFor("mymod:mining"));
    }

    @Test
    void changeWithoutInventoryChangeIsSaved() throws Exception {
        // Before 26.12-Alpha.3 an unchanged inventory skipped the whole save: XP, health, ender chest ...
        a.join(steve);
        a.inventory.put(steve, "pickaxe");
        a.quit(steve);
        b.join(steve);
        b.level.put(steve, 30); // XP farm, inventory untouched
        b.quit(steve);
        a.join(steve);
        assertEquals(30, a.level.get(steve));
        assertEquals("pickaxe", a.inventory.get(steve));
    }

    @Test
    void unchangedRecordIsNotWrittenTwice() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "x");
        a.autosave(steve);
        a.autosave(steve);
        assertEquals(1, a.sync.getStatsSnapshot().skippedByHash);
    }

    @Test
    void quickRejoinWaitsForTheQuitSave() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "1");
        a.quit(steve);

        a.join(steve);
        a.inventory.put(steve, "2");
        storage.saveGate = new CompletableFuture<>();
        a.sync.handleDisconnect(a.player(steve)); // save of "2" is still being written
        a.online.remove(steve);
        a.online.add(steve);
        a.inventory.put(steve, "local");
        a.sync.handleJoin(a.player(steve));
        Thread.sleep(100);
        assertEquals("local", a.inventory.get(steve), "the load waits for the save");
        storage.saveGate.complete(null);
        Thread.sleep(200);
        a.idle(steve);

        assertEquals("2", a.inventory.get(steve));
        assertEquals("2", storage.stored(steve));
        a.inventory.put(steve, "3");
        a.quit(steve);
        assertEquals("3", storage.stored(steve));
    }

    @Test
    void failedApplyKeepsThePlayerUnloadedAndRetries() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "good");
        a.quit(steve);

        b.inventory.put(steve, "stale local");
        b.failApply.set(1);
        b.join(steve);
        assertTrue(!b.sync.isLoaded(steve), "a failed apply must not count as loaded");
        b.autosave(steve);
        assertEquals("good", storage.stored(steve), "the local inventory is never saved over the record");

        Thread.sleep(1500);
        b.idle(steve);
        assertTrue(b.sync.isLoaded(steve));
        assertEquals("good", b.inventory.get(steve));
    }

    @Test
    void loadForAPreviousSessionIsNotApplied() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "stored");
        a.quit(steve);

        storage.gate = new CompletableFuture<>();
        a.joinStale(steve);
        Object first = a.session.get(steve);
        Thread.sleep(50);
        a.reconnect(steve);
        Object second = a.session.get(steve);
        storage.gate.complete(null);
        Thread.sleep(200);
        a.idle(steve);

        assertTrue(!a.appliedTo.contains(first), "the record must not go to the old player object");
        assertTrue(a.appliedTo.contains(second), "the new session gets the record");
        assertTrue(a.sync.isLoaded(steve));
    }

    @Test
    void balanceIsNotSyncedWithoutAnEconomy() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "x");
        a.quit(steve);
        PlayerData stored = storage.load(steve).get(5, TimeUnit.SECONDS).orElseThrow();
        assertTrue(!stored.isSynced("economy"), "a server without Vault must not store balance 0 as the balance");
    }

    @Test
    void shutdownSkipsTheDisconnectSavesThatFollow() throws Exception {
        a.join(steve);
        a.inventory.put(steve, "before stop");
        a.sync.shutdown(List.of(a.player(steve))).get(5, TimeUnit.SECONDS);
        a.inventory.put(steve, "after stop");
        a.quit(steve);
        assertEquals("before stop", storage.stored(steve));
    }

    @Test
    void excludedWorldsMatchAcrossPlatforms() {
        a.config.put("dummy", "x");
        Server fabric = new Server("F", storage) {
            @Override public List<String> getConfigStringList(String path) {
                return path.equals("exclusions.worlds") ? List.of("world_nether") : List.of();
            }
        };
        fabric.sync.refreshExclusions();
        UUID id = UUID.randomUUID();
        fabric.online.add(id);
        PDSPlayer inNether = new PDSPlayer() {
            @Override public UUID getUniqueId() { return id; }
            @Override public String getName() { return "Alex"; }
            @Override public Object getHandle() { return null; }
            @Override public String getWorldName() { return "minecraft:the_nether"; }
        };
        fabric.sync.handleJoin(inNether);
        assertEquals(1, fabric.sync.getStatsSnapshot().skippedByWorld);
    }

    @Test
    void pluginVersionsCompare() {
        assertTrue(Versions.compare("26.12-Alpha.3", "26.12-Alpha.2") > 0);
        assertTrue(Versions.compare("26.12-Alpha.1", "26.11-Stable") > 0);
        assertTrue(Versions.compare("26.11-Stable", "26.11-Beta.4") > 0);
        assertTrue(Versions.compare("26.10-ALPHA.001", "26.10-Alpha.1") == 0);
        assertTrue(Versions.compare("26.9-Release", "26.10-ALPHA.001") < 0);
        assertTrue(Versions.compare("26.11-Stable", "26.12-Alpha.2") < 0);
        assertTrue(Versions.isPreRelease("26.12-Alpha.2"));
        assertTrue(!Versions.isPreRelease("26.11-Stable"));
    }

    @Test
    void handlerChoiceFollowsTheServerVersion() {
        assertEquals("v1_20_R1", Versions.paperHandlerFor("1.20.6-R0.1-SNAPSHOT"));
        assertEquals("v1_21_R1", Versions.paperHandlerFor("1.21.1-R0.1-SNAPSHOT"));
        assertEquals("v26_1", Versions.paperHandlerFor("1.21.4-R0.1-SNAPSHOT"));
        assertEquals("v26_1", Versions.paperHandlerFor("26.1-R0.1-SNAPSHOT"));
        assertEquals("v26_1", Versions.paperHandlerFor("26.1.3-R0.1-SNAPSHOT"));
        assertEquals("v26_2", Versions.paperHandlerFor("26.2-R0.1-SNAPSHOT"));
        assertEquals("v26_2", Versions.paperHandlerFor("26.3-R0.1-SNAPSHOT"));
        assertEquals("v26_2", Versions.paperHandlerFor("26.20-R0.1-SNAPSHOT"));
        assertEquals(null, Versions.paperHandlerFor("1.19.4-R0.1-SNAPSHOT"));

        assertEquals("v1_20_R1", Versions.fabricAdapterFor("1.20.1"));
        assertEquals(null, Versions.fabricAdapterFor("1.20.4"));
        assertEquals("v1_21_R1", Versions.fabricAdapterFor("1.21.4"));
        assertEquals(null, Versions.fabricAdapterFor("1.21.5"));
        assertEquals("v26_1_R1", Versions.fabricAdapterFor("26.1"));
        assertEquals("v26_1_R1", Versions.fabricAdapterFor("26.1.2"));
        assertEquals(null, Versions.fabricAdapterFor("26.10"));
        assertEquals("v26_2_R1", Versions.fabricAdapterFor("26.2"));
        assertEquals(null, Versions.fabricAdapterFor("26.3"));
    }
    @Test
    void respawnDuringLoadStillApplies() throws Exception {
        // Fabric replaces the player object on respawn; the load must still reach the player.
        a.join(steve);
        a.inventory.put(steve, "stored");
        a.quit(steve);

        storage.gate = new CompletableFuture<>();
        a.joinStale(steve);
        Thread.sleep(50);
        Object respawned = new Object();
        a.session.put(steve, respawned);
        storage.gate.complete(null);
        Thread.sleep(200);
        a.idle(steve);

        assertTrue(a.sync.isLoaded(steve));
        assertTrue(a.appliedTo.contains(respawned));
    }

    @Test
    void lockIsLiftedWhenTheLoadFailsForGood() throws Exception {
        a.join(steve);
        a.quit(steve);
        storage.failLoads.set(10);
        a.join(steve);
        assertTrue(a.sync.isWaitingForData(steve));
        Thread.sleep(7600);
        a.idle(steve);
        assertTrue(!a.sync.isLoaded(steve), "saves stay refused");
        assertTrue(!a.sync.isWaitingForData(steve), "but the player is no longer held in place");
    }
}

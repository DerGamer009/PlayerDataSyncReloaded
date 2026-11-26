package de.craftingstudiopro.playerDataSyncReloaded.common;

import com.google.gson.Gson;
import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
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

        @Override public void init() {}
        @Override public void close() {}

        @Override
        public synchronized CompletableFuture<Void> save(PlayerData data) {
            rows.put(data.uuid, new Row(GSON.toJson(data), data.savedAt, null));
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public synchronized CompletableFuture<Boolean> saveChecked(PlayerData data, long basis, String nodeId) {
            Row row = rows.get(data.uuid);
            boolean ok = row == null || nodeId.equals(row.savedBy) || row.savedAt == basis;
            if (ok) {
                rows.put(data.uuid, new Row(GSON.toJson(data), data.savedAt, nodeId));
            }
            return CompletableFuture.completedFuture(ok);
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
    static final class Server implements Platform, VersionHandler {
        final String name;
        final Map<UUID, String> inventory = new ConcurrentHashMap<>();
        final Map<UUID, String> gameMode = new ConcurrentHashMap<>();
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
            return new PDSPlayer() {
                @Override public UUID getUniqueId() { return id; }
                @Override public String getName() { return "Steve"; }
                @Override public Object getHandle() { return null; }
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
            return data;
        }
        @Override
        public void apply(PDSPlayer player, PlayerData data) {
            // Mirrors BukkitBaseVersionHandler: only synced values are applied.
            if (data.isSynced("inventory")) inventory.put(player.getUniqueId(), data.inventoryContents);
            if (data.isSynced("game_mode") && data.gameMode != null) gameMode.put(player.getUniqueId(), data.gameMode);
        }
        @Override public String serializeInventory(PDSPlayer player) { return inventory.get(player.getUniqueId()); }
        @Override public void deserializeInventory(PDSPlayer player, String data) { inventory.put(player.getUniqueId(), data); }
        @Override public void setItemExclusions(List<String> exclusions) {}

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
}

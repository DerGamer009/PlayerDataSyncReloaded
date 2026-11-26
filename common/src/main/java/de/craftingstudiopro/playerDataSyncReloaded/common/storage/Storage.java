package de.craftingstudiopro.playerDataSyncReloaded.common.storage;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage {
    void init();
    void close();

    CompletableFuture<Void> save(PlayerData data);

    /**
     * Saves only if the stored record is still the one this server based its data on.
     *
     * @param basis  savedAt of the record this server applied, 0 if it applied none
     * @param nodeId this server's node id
     * @return false if another server stored a different record in the meantime; nothing was written
     */
    default CompletableFuture<Boolean> saveChecked(PlayerData data, long basis, String nodeId) {
        return save(data).thenApply(v -> true);
    }
    CompletableFuture<Optional<PlayerData>> load(UUID uuid);
    CompletableFuture<Optional<PlayerData>> loadLegacy(UUID uuid);

    CompletableFuture<java.util.List<UUID>> getAllStoredUUIDs();
}

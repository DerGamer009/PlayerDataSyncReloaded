package de.craftingstudiopro.playerDataSyncReloaded.common.storage;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Storage {
    void init();
    void close();

    CompletableFuture<Void> save(PlayerData data);
    CompletableFuture<Optional<PlayerData>> load(UUID uuid);
}

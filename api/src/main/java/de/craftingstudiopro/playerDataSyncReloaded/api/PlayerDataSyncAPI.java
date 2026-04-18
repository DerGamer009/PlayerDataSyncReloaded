package de.craftingstudiopro.playerDataSyncReloaded.api;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for PlayerDataSync Reloaded.
 */
public interface PlayerDataSyncAPI {

    /**
     * Retrieves the current (persisted) data for a player.
     * @param uuid The player's UUID.
     * @return A future containing the player data if present.
     */
    CompletableFuture<Optional<PlayerData>> getPlayerData(UUID uuid);

    /**
     * Forces a data save for an online player.
     * @param uuid The player's UUID.
     * @return A future that completes when the data is saved.
     */
    CompletableFuture<Void> forceSave(UUID uuid);

    /**
     * Forces a data reload for an online player.
     * @param uuid The player's UUID.
     * @return A future that completes when the data is reloaded.
     */
    CompletableFuture<Void> forceReload(UUID uuid);
}

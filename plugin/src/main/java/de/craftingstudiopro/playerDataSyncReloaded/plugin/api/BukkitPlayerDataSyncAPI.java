package de.craftingstudiopro.playerDataSyncReloaded.plugin.api;

import de.craftingstudiopro.playerDataSyncReloaded.PlayerDataSyncReloaded;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerDataSyncAPI;
import de.craftingstudiopro.playerDataSyncReloaded.plugin.BukkitPDSPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BukkitPlayerDataSyncAPI implements PlayerDataSyncAPI {
    private final PlayerDataSyncReloaded plugin;

    public BukkitPlayerDataSyncAPI(PlayerDataSyncReloaded plugin) {
        this.plugin = plugin;
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> getPlayerData(UUID uuid) {
        return plugin.getSyncManager().loadStoredData(uuid);
    }

    @Override
    public CompletableFuture<Void> forceSave(UUID uuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                future.completeExceptionally(new IllegalArgumentException("Player is not online: " + uuid));
                return;
            }

            try {
                // Complete when the save has been written, not when it was started.
                plugin.getSyncManager().handleQuit(new BukkitPDSPlayer(player)).whenComplete((unused, ex) -> {
                    if (ex != null) {
                        future.completeExceptionally(ex);
                    } else {
                        future.complete(null);
                    }
                });
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> forceReload(UUID uuid) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                future.completeExceptionally(new IllegalArgumentException("Player is not online: " + uuid));
                return;
            }

            try {
                plugin.getSyncManager().handleJoin(new BukkitPDSPlayer(player), true);
                future.complete(null);
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }
}

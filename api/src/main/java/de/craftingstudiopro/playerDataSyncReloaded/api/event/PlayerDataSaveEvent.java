package de.craftingstudiopro.playerDataSyncReloaded.api.event;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a player's data is saved to the storage.
 */
public class PlayerDataSaveEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final PlayerData data;
    private boolean cancelled = false;

    public PlayerDataSaveEvent(Player player, PlayerData data) {
        super();
        this.player = player;
        this.data = data;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerData getData() {
        return data;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

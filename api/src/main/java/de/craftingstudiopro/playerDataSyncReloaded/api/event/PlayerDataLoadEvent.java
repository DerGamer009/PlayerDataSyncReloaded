package de.craftingstudiopro.playerDataSyncReloaded.api.event;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player's data has been successfully loaded from the storage.
 */
public class PlayerDataLoadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final PlayerData data;
    private final long loadTime;

    public PlayerDataLoadEvent(Player player, PlayerData data, long loadTime) {
        super(true); // Async as it often happens in async tasks
        this.player = player;
        this.data = data;
        this.loadTime = loadTime;
    }

    public Player getPlayer() {
        return player;
    }

    public PlayerData getData() {
        return data;
    }

    public long getLoadTime() {
        return loadTime;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

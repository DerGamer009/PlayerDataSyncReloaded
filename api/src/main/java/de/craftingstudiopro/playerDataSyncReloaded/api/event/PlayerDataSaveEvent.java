package de.craftingstudiopro.playerDataSyncReloaded.api.event;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a player's data is saved to the storage.
 */
public class PlayerDataSaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final PlayerData data;

    public PlayerDataSaveEvent(Player player, PlayerData data) {
        super(true);
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}

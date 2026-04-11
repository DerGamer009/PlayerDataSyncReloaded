package de.craftingstudiopro.playerDataSyncReloaded.api;

import org.bukkit.entity.Player;

public interface VersionHandler {
    /**
     * Captures current player state into PlayerData object.
     */
    PlayerData capture(Player player);

    /**
     * Applies PlayerData back to the player.
     */
    void apply(Player player, PlayerData data);
    
    /**
     * Serializes items to string (Base64/NBT).
     */
    String serializeInventory(Player player);
    
    /**
     * Deserializes inventory from string.
     */
    void deserializeInventory(Player player, String inventory);
}

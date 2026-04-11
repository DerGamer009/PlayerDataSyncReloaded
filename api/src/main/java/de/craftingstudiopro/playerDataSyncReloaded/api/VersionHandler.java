package de.craftingstudiopro.playerDataSyncReloaded.api;

import org.bukkit.entity.Player;

import java.util.List;

public interface VersionHandler {

    /**
     * Captures player data.
     */
    PlayerData capture(Player player);

    /**
     * Applies player data.
     */
    void apply(Player player, PlayerData data);

    /**
     * Serializes inventory to string.
     */
    String serializeInventory(Player player);

    /**
     * Deserializes inventory from string.
     */
    void deserializeInventory(Player player, String inventory);

    /**
     * Sets the list of materials to be excluded from synchronization.
     */
    void setItemExclusions(List<String> materials);
}

package de.craftingstudiopro.playerDataSyncReloaded.v1_20_R1;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.common.BaseVersionHandler;
import org.bukkit.entity.Player;

public class VersionHandlerImpl extends BaseVersionHandler {
    @Override
    public PlayerData capture(Player player) {
        return super.capture(player);
    }

    @Override
    public void apply(Player player, PlayerData data) {
        super.apply(player, data);
    }

    @Override
    protected double getMaxHealth(Player player) {
        org.bukkit.attribute.AttributeInstance attr = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr != null ? attr.getBaseValue() : 20.0;
    }
}

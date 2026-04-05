package de.craftingstudiopro.playerDataSyncReloaded.v1_8_R3;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.common.BaseVersionHandler;
import org.bukkit.entity.Player;

public class VersionHandlerImpl extends BaseVersionHandler {

    @Override
    public PlayerData capture(Player player) {
        PlayerData data = super.capture(player);
        data.healthScale = player.isHealthScaled() ? player.getHealthScale() : 20.0;
        return data;
    }

    @Override
    public void apply(Player player, PlayerData data) {
        super.apply(player, data);
        if (data.healthScale > 0) {
            player.setHealthScale(data.healthScale);
        }
    }

    @Override
    protected double getMaxHealth(Player player) {
        // In 1.8.8, we use the direct getMaxHealth method
        return player.getMaxHealth();
    }
}

package de.craftingstudiopro.playerDataSyncReloaded.v26_2;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import de.craftingstudiopro.playerDataSyncReloaded.common.BukkitBaseVersionHandler;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class VersionHandlerImpl extends BukkitBaseVersionHandler {

    @Override
    public PlayerData capture(PDSPlayer pdsPlayer) {
        PlayerData data = super.capture(pdsPlayer);
        Player player = (Player) pdsPlayer.getHandle();
        data.healthScale = player.getHealthScale();

        Map<String, Double> attrMap = new HashMap<>();
        org.bukkit.Registry.ATTRIBUTE.forEach(attr -> {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) {
                attrMap.put(attr.getKey().asString(), inst.getBaseValue());
            }
        });
        data.attributes = attrMap;

        return data;
    }

    @Override
    public void apply(PDSPlayer pdsPlayer, PlayerData data) {
        super.apply(pdsPlayer, data);
        Player player = (Player) pdsPlayer.getHandle();
        player.setHealthScale(data.healthScale);

        if (data.attributes != null) {
            data.attributes.forEach((key, val) -> {
                try {
                    org.bukkit.attribute.Attribute attr = org.bukkit.Registry.ATTRIBUTE.get(org.bukkit.NamespacedKey.fromString(key));
                    if (attr != null) {
                        AttributeInstance inst = player.getAttribute(attr);
                        if (inst != null) {
                            inst.setBaseValue(val);
                        }
                    }
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Override
    protected double getMaxHealth(Player player) {
        AttributeInstance inst = player.getAttribute(Attribute.MAX_HEALTH);
        return inst != null ? inst.getValue() : 20.0;
    }
}

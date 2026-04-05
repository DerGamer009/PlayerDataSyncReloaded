package de.craftingstudiopro.playerDataSyncReloaded.common;

import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.util.SerializationUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class BaseVersionHandler implements VersionHandler {

    @Override
    public PlayerData capture(Player player) {
        PlayerData data = new PlayerData();
        data.uuid = player.getUniqueId();
        data.name = player.getName();

        // Items
        data.inventoryContents = serializeInventory(player);
        data.enderChestContents = SerializationUtil.toBase64(player.getEnderChest().getContents());
        data.selectedSlot = player.getInventory().getHeldItemSlot();

        // Stats
        data.health = player.getHealth();
        data.foodLevel = player.getFoodLevel();
        data.saturation = player.getSaturation();
        data.exhaustion = player.getExhaustion();

        // Exp
        data.level = player.getLevel();
        data.exp = player.getExp();
        data.totalExperience = player.getTotalExperience();

        // Status
        data.gameMode = player.getGameMode().name();
        
        try {
            data.isFlying = player.isFlying();
            data.canFly = player.getAllowFlight();
        } catch (NoSuchMethodError ignored) {}

        // Effects
        data.potionEffects = SerializationUtil.toBase64(player.getActivePotionEffects());

        // Location
        Location loc = player.getLocation();
        data.worldName = loc.getWorld().getName();
        data.x = loc.getX();
        data.y = loc.getY();
        data.z = loc.getZ();
        data.yaw = loc.getYaw();
        data.pitch = loc.getPitch();

        return data;
    }

    @Override
    public void apply(Player player, PlayerData data) {
        // Stats
        player.setHealth(Math.min(data.health, getMaxHealth(player)));
        player.setFoodLevel(data.foodLevel);
        player.setSaturation(data.saturation);
        player.setExhaustion(data.exhaustion);

        // Exp
        player.setLevel(data.level);
        player.setExp(data.exp);
        player.setTotalExperience(data.totalExperience);

        // Status
        player.setGameMode(GameMode.valueOf(data.gameMode));
        
        try {
            player.setAllowFlight(data.canFly);
            player.setFlying(data.isFlying);
        } catch (NoSuchMethodError ignored) {}

        // Effects
        player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
        try {
            Collection<PotionEffect> effects = (Collection<PotionEffect>) SerializationUtil.fromBase64(data.potionEffects);
            player.addPotionEffects(effects);
        } catch (Exception ignored) {}

        // Inventory
        deserializeInventory(player, data.inventoryContents);
        try {
            ItemStack[] ec = (ItemStack[]) SerializationUtil.fromBase64(data.enderChestContents);
            player.getEnderChest().setContents(ec);
        } catch (Exception ignored) {}
        
        player.getInventory().setHeldItemSlot(data.selectedSlot);
    }
    
    protected abstract double getMaxHealth(Player player);

    @Override
    public String serializeInventory(Player player) {
        return SerializationUtil.toBase64(player.getInventory().getContents());
    }

    @Override
    public void deserializeInventory(Player player, String inventory) {
        try {
            ItemStack[] contents = (ItemStack[]) SerializationUtil.fromBase64(inventory);
            player.getInventory().setContents(contents);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

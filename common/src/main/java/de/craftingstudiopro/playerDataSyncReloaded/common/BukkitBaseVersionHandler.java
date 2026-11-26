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

import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;

import java.util.*;

public abstract class BukkitBaseVersionHandler implements VersionHandler {
    protected List<String> itemExclusions = new ArrayList<>();

    @Override
    public void setItemExclusions(List<String> materials) {
        this.itemExclusions = materials;
    }

    @Override
    public PlayerData capture(PDSPlayer pdsPlayer) {
        Player player = (Player) pdsPlayer.getHandle();
        PlayerData data = new PlayerData();
        data.uuid = player.getUniqueId();
        data.name = player.getName();

        // Items
        data.inventoryContents = serializeInventory(pdsPlayer);
        data.enderChestContents = SerializationUtil.toBase64(filterItems(player.getEnderChest().getContents()));
        data.selectedSlot = player.getInventory().getHeldItemSlot();

        // Stats
        data.health = player.getHealth();
        data.foodLevel = player.getFoodLevel();
        data.saturation = player.getSaturation();
        data.exhaustion = player.getExhaustion();
        data.airLevel = player.getRemainingAir();
        data.fireTicks = player.getFireTicks();
        data.freezeTicks = player.getFreezeTicks();
        data.arrowsInBody = player.getArrowsInBody();
        data.absorptionAmount = player.getAbsorptionAmount();

        // Exp
        data.level = player.getLevel();
        data.exp = player.getExp();
        data.totalExperience = player.getTotalExperience();

        // Status
        data.gameMode = player.getGameMode().name();
        
        try {
            data.isFlying = player.isFlying();
            data.canFly = player.getAllowFlight();
            data.walkSpeed = player.getWalkSpeed();
            data.flySpeed = player.getFlySpeed();
            data.fallDistance = player.getFallDistance();
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
        data.playerTime = player.getPlayerTime();
        data.playerWeather = player.getPlayerWeather() != null ? player.getPlayerWeather().name() : null;

        // PDC
        try {
            // Using reflection to support varied API versions for getValues()
            java.lang.reflect.Method getValuesMethod = player.getPersistentDataContainer().getClass().getMethod("getValues");
            Map<org.bukkit.NamespacedKey, Object> values = (Map<org.bukkit.NamespacedKey, Object>) getValuesMethod.invoke(player.getPersistentDataContainer());
            data.persistentDataContainer = SerializationUtil.toBase64(values);
        } catch (Exception ignored) {
            // Fallback for older versions or issues
        }

        // Attributes
        data.attributes = captureAttributes(player);

        // Stats & Advancements
        data.statistics = SerializationUtil.toBase64(captureStatistics(player));
        data.advancements = SerializationUtil.toBase64(captureAdvancements(player));

        return data;
    }

    @Override
    public void apply(PDSPlayer pdsPlayer, PlayerData data) {
        Player player = (Player) pdsPlayer.getHandle();
        // Stats
        // A player who left on the death screen is stored with 0 health; applying it kills them again.
        // Every block checks data.isSynced(...): a value the saving server or this server does not
        // sync is a placeholder and must not replace the player's own value (issue #7).
        if (data.isSynced("health") && data.health > 0) {
            player.setHealth(Math.min(data.health, getMaxHealth(player)));
        }
        if (data.isSynced("food")) {
            player.setFoodLevel(data.foodLevel);
            player.setSaturation(data.saturation);
            player.setExhaustion(data.exhaustion);
        }
        if (data.isSynced("air_level")) player.setRemainingAir(data.airLevel);
        if (data.isSynced("fire_ticks")) player.setFireTicks(data.fireTicks);
        if (data.isSynced("freeze_ticks")) player.setFreezeTicks(data.freezeTicks);
        if (data.isSynced("arrows_in_body")) player.setArrowsInBody(data.arrowsInBody);
        if (data.isSynced("absorption")) player.setAbsorptionAmount(data.absorptionAmount);

        // Exp
        if (data.isSynced("experience")) {
            player.setLevel(data.level);
            player.setExp(data.exp);
            player.setTotalExperience(data.totalExperience);
        }

        // Status
        if (data.isSynced("game_mode") && data.gameMode != null) {
            player.setGameMode(GameMode.valueOf(data.gameMode));
        }

        try {
            if (data.isSynced("flight")) {
                player.setAllowFlight(data.canFly);
                player.setFlying(data.isFlying);
            }
            if (data.isSynced("speeds")) {
                player.setWalkSpeed(data.walkSpeed);
                player.setFlySpeed(data.flySpeed);
            }
            if (data.isSynced("fall_distance")) player.setFallDistance(data.fallDistance);
        } catch (NoSuchMethodError ignored) {}

        // Effects
        if (data.isSynced("potion_effects")) {
            player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
            try {
                Collection<PotionEffect> effects = (Collection<PotionEffect>) SerializationUtil.fromBase64(data.potionEffects);
                player.addPotionEffects(effects);
            } catch (Exception ignored) {}
        }

        // Inventory
        if (data.isSynced("inventory") && data.inventoryContents != null) {
            deserializeInventory(pdsPlayer, data.inventoryContents);
            player.getInventory().setHeldItemSlot(data.selectedSlot);
        }
        if (data.isSynced("ender_chest")) {
            try {
                ItemStack[] ec = (ItemStack[]) SerializationUtil.fromBase64(data.enderChestContents);
                player.getEnderChest().setContents(filterItems(ec));
            } catch (Exception ignored) {}
        }

        // PDC
        if (data.isSynced("pdc") && data.persistentDataContainer != null) {
            try {
                Map<org.bukkit.NamespacedKey, Object> values = (Map<org.bukkit.NamespacedKey, Object>) SerializationUtil.fromBase64(data.persistentDataContainer);
                values.forEach((key, val) -> player.getPersistentDataContainer().set(key, (org.bukkit.persistence.PersistentDataType) getDataType(val), val));
            } catch (Exception ignored) {}
        }

        // Attributes
        if (data.isSynced("attributes")) {
            applyAttributes(player, data.attributes);
        }

        // Stats & Advancements
        if (data.isSynced("statistics")) {
            try {
                applyStatistics(player, (Map<String, Integer>) SerializationUtil.fromBase64(data.statistics));
            } catch (Exception ignored) {}
        }
        if (data.isSynced("advancements")) {
            try {
                applyAdvancements(player, (java.util.List<String>) SerializationUtil.fromBase64(data.advancements));
            } catch (Exception ignored) {}
        }

        // Time & Weather
        if (data.isSynced("player_time")) {
            if (data.playerTime != -1) {
                player.setPlayerTime(data.playerTime, false);
            } else {
                player.resetPlayerTime();
            }
        }
        if (data.isSynced("player_weather")) {
            if (data.playerWeather != null) {
                try {
                    player.setPlayerWeather(org.bukkit.WeatherType.valueOf(data.playerWeather));
                } catch (Exception ignored) {}
            } else {
                player.resetPlayerWeather();
            }
        }

        // Location: filterData() nulls worldName unless sync.location is on, so this is the opt-in gate.
        // The target world only exists if the destination server actually has it loaded.
        if (data.worldName != null) {
            org.bukkit.World world = Bukkit.getWorld(data.worldName);
            if (world == null) {
                // A record saved on Fabric names the dimension (minecraft:the_nether); use this server's world of that kind.
                String environment = WorldNames.environmentFor(data.worldName);
                if (environment != null) {
                    for (org.bukkit.World candidate : Bukkit.getWorlds()) {
                        if (candidate.getEnvironment().name().equals(environment)) {
                            world = candidate;
                            break;
                        }
                    }
                }
            }
            if (world == null) {
                Bukkit.getLogger().warning("[PlayerDataSync] Skipping location restore for " + player.getName()
                        + ": world '" + data.worldName + "' does not exist on this server.");
            } else {
                player.teleport(new Location(world, data.x, data.y, data.z, data.yaw, data.pitch));
            }
        }
    }
    
    protected Map<String, Double> captureAttributes(Player player) {
        Map<String, Double> map = new HashMap<>();
        for (Attribute attr : Attribute.values()) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) {
                map.put(attr.name(), inst.getBaseValue());
            }
        }
        return map;
    }

    protected void applyAttributes(Player player, Map<String, Double> attributes) {
        if (attributes == null) return;
        attributes.forEach((name, value) -> {
            try {
                Attribute attr = Attribute.valueOf(name);
                AttributeInstance inst = player.getAttribute(attr);
                if (inst != null) inst.setBaseValue(value);
            } catch (Exception ignored) {}
        });
    }

    protected Map<String, Integer> captureStatistics(Player player) {
        Map<String, Integer> stats = new HashMap<>();
        for (org.bukkit.Statistic stat : org.bukkit.Statistic.values()) {
            try {
                if (stat.getType() == org.bukkit.Statistic.Type.UNTYPED) {
                    stats.put(stat.name(), player.getStatistic(stat));
                }
            } catch (Exception ignored) {}
        }
        return stats;
    }

    protected void applyStatistics(Player player, Map<String, Integer> stats) {
        if (stats == null) return;
        stats.forEach((name, val) -> {
            try {
                player.setStatistic(org.bukkit.Statistic.valueOf(name), val);
            } catch (Exception ignored) {}
        });
    }

    protected java.util.List<String> captureAdvancements(Player player) {
        java.util.List<String> list = new java.util.ArrayList<>();
        java.util.Iterator<org.bukkit.advancement.Advancement> it = Bukkit.advancementIterator();
        while (it.hasNext()) {
            org.bukkit.advancement.Advancement adv = it.next();
            if (player.getAdvancementProgress(adv).isDone()) {
                list.add(adv.getKey().toString());
            }
        }
        return list;
    }

    protected void applyAdvancements(Player player, java.util.List<String> advancements) {
        if (advancements == null) return;
        advancements.forEach(keyStr -> {
            org.bukkit.advancement.Advancement adv = Bukkit.getAdvancement(org.bukkit.NamespacedKey.fromString(keyStr));
            if (adv != null) {
                org.bukkit.advancement.AdvancementProgress progress = player.getAdvancementProgress(adv);
                if (!progress.isDone()) {
                    progress.getRemainingCriteria().forEach(progress::awardCriteria);
                }
            }
        });
    }

    private Object getDataType(Object val) {
        if (val instanceof String) return org.bukkit.persistence.PersistentDataType.STRING;
        if (val instanceof Integer) return org.bukkit.persistence.PersistentDataType.INTEGER;
        if (val instanceof Double) return org.bukkit.persistence.PersistentDataType.DOUBLE;
        if (val instanceof Byte) return org.bukkit.persistence.PersistentDataType.BYTE;
        return org.bukkit.persistence.PersistentDataType.STRING; // Fallback
    }

    protected abstract double getMaxHealth(Player player);

    @Override
    public String serializeInventory(PDSPlayer pdsPlayer) {
        Player player = (Player) pdsPlayer.getHandle();
        return SerializationUtil.toBase64(filterItems(player.getInventory().getContents()));
    }

    @Override
    public void deserializeInventory(PDSPlayer pdsPlayer, String inventory) {
        Player player = (Player) pdsPlayer.getHandle();
        try {
            ItemStack[] contents = (ItemStack[]) SerializationUtil.fromBase64(inventory);
            player.getInventory().setContents(filterItems(contents));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected ItemStack[] filterItems(ItemStack[] contents) {
        if (itemExclusions == null || itemExclusions.isEmpty()) return contents;
        ItemStack[] filtered = contents.clone();
        for (int i = 0; i < filtered.length; i++) {
            if (filtered[i] != null && itemExclusions.contains(filtered[i].getType().name())) {
                filtered[i] = null;
            }
        }
        return filtered;
    }
}

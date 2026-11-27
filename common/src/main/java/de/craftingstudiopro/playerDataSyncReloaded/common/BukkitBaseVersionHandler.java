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
        // -1 = scaling off. getHealthScale() reports 20 even when scaling is off. Records without this
        // information (0: older builds, 1.20/1.21, Fabric) leave the scaling as it is.
        data.healthScale = player.isHealthScaled() ? player.getHealthScale() : -1.0;

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
        // Before 26.12-Alpha.3 this called a getValues() method the API does not have, so the PDC was
        // never stored. Now the container's own NBT bytes, with a prefix to tell the format apart.
        try {
            byte[] bytes = player.getPersistentDataContainer().serializeToBytes();
            data.persistentDataContainer = PDC_PREFIX + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception | LinkageError e) {
            Bukkit.getLogger().warning("[PlayerDataSync] Could not store the persistent data of " + player.getName() + ": " + e.getMessage());
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
        // Every block checks data.isSynced(...): a value the saving server or this server does not
        // sync is a placeholder and must not replace the player's own value (issue #7).

        // Attributes first: health is clamped to the maximum, so a stored 40 health with a max
        // health of 40 would otherwise be cut to the 20 the player has before the attributes apply.
        if (data.isSynced("attributes")) {
            applyAttributes(player, data.attributes);
        }
        // Advancements before experience: their rewards would add to the synced experience.
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

        // Stats (health is set further down, after effects and items that raise the maximum)
        if (data.isSynced("health")) {
            if (data.healthScale > 0) {
                player.setHealthScale(data.healthScale);
            } else if (data.healthScale == -1.0) {
                player.setHealthScaled(false);
            }
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
            player.getInventory().setHeldItemSlot(Math.max(0, Math.min(8, data.selectedSlot)));
        }
        if (data.isSynced("ender_chest") && data.enderChestContents != null) {
            player.getEnderChest().setContents(mergeExcluded(player, player.getEnderChest().getContents(),
                    readItems(data.enderChestContents, "ender chest")));
        }

        // Health last: the maximum includes health boost effects and armor applied above.
        // A player who left on the death screen is stored with 0 health; applying it kills them again.
        if (data.isSynced("health") && data.health > 0) {
            player.setHealth(Math.min(data.health, getMaxHealth(player)));
        }

        // PDC: merged, so keys other plugins keep per server are not removed.
        if (data.isSynced("pdc") && data.persistentDataContainer != null && data.persistentDataContainer.startsWith(PDC_PREFIX)) {
            try {
                byte[] bytes = java.util.Base64.getDecoder().decode(data.persistentDataContainer.substring(PDC_PREFIX.length()));
                player.getPersistentDataContainer().readFromBytes(bytes, false);
            } catch (Exception | LinkageError e) {
                Bukkit.getLogger().warning("[PlayerDataSync] Could not apply the persistent data of " + player.getName() + ": " + e.getMessage());
            }
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

    /**
     * Base values of all attributes, keyed by namespaced key. Uses the registry and {@link org.bukkit.Keyed}
     * rather than the enum: Attribute is an enum up to 1.21.1 and an interface from 1.21.2 on.
     */
    protected Map<String, Double> captureAttributes(Player player) {
        Map<String, Double> map = new HashMap<>();
        for (Object entry : org.bukkit.Registry.ATTRIBUTE) {
            try {
                AttributeInstance inst = player.getAttribute((Attribute) entry);
                if (inst != null) {
                    map.put(((org.bukkit.Keyed) entry).getKey().toString(), inst.getBaseValue());
                }
            } catch (Exception ignored) {}
        }
        return map;
    }

    protected void applyAttributes(Player player, Map<String, Double> attributes) {
        if (attributes == null) return;
        attributes.forEach((name, value) -> {
            if (value == null) return;
            Attribute attr = resolveAttribute(name);
            if (attr == null) return;
            try {
                AttributeInstance inst = player.getAttribute(attr);
                if (inst != null) inst.setBaseValue(value);
            } catch (Exception ignored) {}
        });
    }

    /**
     * Finds an attribute for any stored name: {@code minecraft:max_health} (1.21.2+),
     * {@code minecraft:generic.max_health} (up to 1.21.1), or the enum names {@code MAX_HEALTH} and
     * {@code GENERIC_MAX_HEALTH} older records used. Without this, records moved between versions
     * dropped every attribute.
     */
    protected static Attribute resolveAttribute(String stored) {
        if (stored == null || stored.isBlank()) return null;
        String name = stored.trim().toLowerCase(java.util.Locale.ROOT);
        String namespace = "minecraft";
        int colon = name.indexOf(':');
        if (colon >= 0) {
            namespace = name.substring(0, colon);
            name = name.substring(colon + 1);
        }
        if (!stored.contains(".") && !stored.contains(":")) {
            // Enum constant: GENERIC_MAX_HEALTH, MAX_HEALTH, PLAYER_BLOCK_INTERACTION_RANGE ...
            for (String group : new String[]{"generic_", "player_", "zombie_", "horse_"}) {
                if (name.startsWith(group)) {
                    name = name.substring(group.length());
                    break;
                }
            }
        }
        String base = name;
        int dot = base.indexOf('.');
        if (dot >= 0) {
            base = base.substring(dot + 1);
        }
        for (String candidate : new String[]{name, base, "generic." + base, "player." + base, "zombie." + base, "horse." + base}) {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString(namespace + ":" + candidate);
            if (key == null) continue;
            Object attr = org.bukkit.Registry.ATTRIBUTE.get(key);
            if (attr != null) return (Attribute) attr;
        }
        return null;
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

    private static final String PDC_PREFIX = "pdc1:";

    /** Current maximum health including modifiers (armor, effects), read after the attributes are applied. */
    protected double getMaxHealth(Player player) {
        Attribute maxHealth = resolveAttribute("max_health");
        if (maxHealth != null) {
            AttributeInstance inst = player.getAttribute(maxHealth);
            if (inst != null) return inst.getValue();
        }
        return 20.0;
    }

    @Override
    public String serializeInventory(PDSPlayer pdsPlayer) {
        Player player = (Player) pdsPlayer.getHandle();
        return SerializationUtil.toBase64(filterItems(player.getInventory().getContents()));
    }

    @Override
    public void deserializeInventory(PDSPlayer pdsPlayer, String inventory) {
        Player player = (Player) pdsPlayer.getHandle();
        ItemStack[] stored = readItems(inventory, "inventory");
        player.getInventory().setContents(mergeExcluded(player, player.getInventory().getContents(), stored));
    }

    /**
     * Reads stored items. Fails loudly: swallowing the error kept the server's local inventory, marked
     * the player as loaded and later saved that local inventory over the real record.
     */
    protected ItemStack[] readItems(String serialized, String what) {
        Object value;
        try {
            value = SerializationUtil.fromBase64(serialized);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read the stored " + what
                    + ". It was probably saved by a Fabric server or another Minecraft version (" + e.getMessage() + ")", e);
        }
        if (!(value instanceof ItemStack[])) {
            throw new IllegalStateException("The stored " + what + " is not a Paper item list.");
        }
        return filterItems((ItemStack[]) value);
    }

    /**
     * Excluded items are not stored, so their slots arrive empty. Keep the player's local excluded
     * items there instead of deleting them; one whose slot is now taken goes into a free slot, or is
     * dropped at the player's feet.
     */
    protected ItemStack[] mergeExcluded(Player player, ItemStack[] local, ItemStack[] stored) {
        if (itemExclusions == null || itemExclusions.isEmpty() || local == null) return stored;
        ItemStack[] merged = java.util.Arrays.copyOf(stored, Math.max(stored.length, 0));
        List<ItemStack> homeless = new ArrayList<>();
        for (int i = 0; i < local.length; i++) {
            ItemStack item = local[i];
            if (item == null || !itemExclusions.contains(item.getType().name())) continue;
            if (i < merged.length && (merged[i] == null || merged[i].getType().isAir())) {
                merged[i] = item;
            } else {
                homeless.add(item);
            }
        }
        for (ItemStack item : homeless) {
            boolean placed = false;
            for (int i = 0; i < merged.length && i < 36; i++) {
                if (merged[i] == null || merged[i].getType().isAir()) {
                    merged[i] = item;
                    placed = true;
                    break;
                }
            }
            if (!placed) player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        return merged;
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

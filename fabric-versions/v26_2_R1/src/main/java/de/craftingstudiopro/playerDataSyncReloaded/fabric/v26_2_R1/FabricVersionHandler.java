package de.craftingstudiopro.playerDataSyncReloaded.fabric.v26_2_R1;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.WorldNames;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FabricVersionHandler implements VersionHandler {
    private static final Logger LOG = LoggerFactory.getLogger("PlayerDataSync");

    private List<String> itemExclusions = new ArrayList<>();

    @Override
    public PlayerData capture(PDSPlayer pdsPlayer) {
        ServerPlayer player = (ServerPlayer) pdsPlayer.getHandle();
        PlayerData data = new PlayerData();
        data.uuid = player.getUUID();
        data.name = player.getName().getString();

        data.health = player.getHealth();
        data.foodLevel = player.getFoodData().getFoodLevel();
        data.saturation = player.getFoodData().getSaturationLevel();
        data.exp = player.experienceProgress;
        data.level = player.experienceLevel;
        data.totalExperience = player.totalExperience;

        data.inventoryContents = serializeInventory(pdsPlayer);
        data.enderChestContents = serializeContainer(player, player.getEnderChestInventory());
        data.potionEffects = serializeEffects(player);
        data.attributes = captureAttributes(player);

        data.gameMode = player.gameMode.getGameModeForPlayer().name();

        // Location; SyncManager#filterData drops it unless sync.location is on.
        data.worldName = player.level().dimension().identifier().toString();
        data.x = player.getX();
        data.y = player.getY();
        data.z = player.getZ();
        data.yaw = player.getYRot();
        data.pitch = player.getXRot();

        // Not captured on Fabric: mark them, so Paper does not apply the empty values
        // (walk speed 0, no air, no flight, balance 0 ...).
        for (String key : new String[]{"air_level", "fire_ticks", "freeze_ticks", "arrows_in_body", "absorption", "speeds", "fall_distance", "flight", "pdc", "advancements", "statistics", "player_time", "player_weather", "economy"}) {
            data.markNotSynced(key);
        }

        return data;
    }

    @Override
    public void apply(PDSPlayer pdsPlayer, PlayerData data) {
        ServerPlayer player = (ServerPlayer) pdsPlayer.getHandle();

        // Values the saving server or this server does not sync are placeholders (issue #7).
        // Attributes first: setHealth clamps to the current max health.
        if (data.isSynced("attributes")) {
            applyAttributes(player, data.attributes);
        }
        if (data.isSynced("food")) {
            player.getFoodData().setFoodLevel(data.foodLevel);
            player.getFoodData().setSaturation(data.saturation);
        }
        if (data.isSynced("game_mode") && data.gameMode != null) {
            try {
                player.setGameMode(net.minecraft.world.level.GameType.valueOf(data.gameMode));
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown game mode '{}' for {}", data.gameMode, player.getName().getString());
            }
        }
        if (data.isSynced("experience")) {
            player.experienceProgress = data.exp;
            player.experienceLevel = data.level;
            player.totalExperience = data.totalExperience;
        }

        if (data.isSynced("inventory") && data.inventoryContents != null) {
            deserializeInventory(pdsPlayer, data.inventoryContents);
        }
        if (data.isSynced("ender_chest") && data.enderChestContents != null) {
            applyContainer(player, player.getEnderChestInventory(), data.enderChestContents);
        }
        if (data.isSynced("potion_effects") && data.potionEffects != null) {
            applyEffects(player, data.potionEffects);
        }
        // Health last: setHealth clamps to the maximum, which attributes, effects and armor raise.
        // A player who left on the death screen is stored with 0 health; applying it kills them again.
        if (data.isSynced("health") && data.health > 0) {
            player.setHealth((float) Math.min(data.health, player.getMaxHealth()));
        }

        // Location: only present with sync.location: true (filtered on save and before apply).
        if (data.worldName != null) {
            String dimension = WorldNames.toDimensionId(data.worldName);
            Identifier key = dimension == null ? null : Identifier.tryParse(dimension);
            net.minecraft.server.level.ServerLevel level = key == null ? null : player.level().getServer()
                    .getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, key));
            if (level == null) {
                LoggerFactory.getLogger("PlayerDataSync").warn("Skipping location restore for {}: world '{}' does not exist on this server.",
                        player.getName().getString(), data.worldName);
            } else {
                player.teleportTo(level, data.x, data.y, data.z, java.util.Set.of(), data.yaw, data.pitch, true);
            }
        }
    }

    @Override
    public String serializeInventory(PDSPlayer pdsPlayer) {
        ServerPlayer player = (ServerPlayer) pdsPlayer.getHandle();
        return serializeContainer(player, player.getInventory());
    }

    @Override
    public void deserializeInventory(PDSPlayer pdsPlayer, String inventory) {
        ServerPlayer player = (ServerPlayer) pdsPlayer.getHandle();
        applyContainer(player, player.getInventory(), inventory);
    }

    /** Slot-indexed JSON so a container can be restored even if its size changed between versions. */
    private String serializeContainer(ServerPlayer player, Container container) {
        try {
            var registries = player.level().registryAccess();
            JsonArray arr = new JsonArray();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                JsonObject slot = new JsonObject();
                slot.addProperty("slot", i);
                if (stack.isEmpty() || isExcluded(stack, registries)) {
                    slot.add("item", JsonNull.INSTANCE);
                } else {
                    DataResult<JsonElement> result =
                        ItemStack.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack);
                    slot.add("item", result.getOrThrow());
                }
                arr.add(slot);
            }
            return arr.toString();
        } catch (Exception e) {
            // Returning null stored the record without the inventory, and the next server kept its
            // own local inventory. Failing the save keeps the stored record instead.
            throw new IllegalStateException("Could not store the items of " + player.getName().getString(), e);
        }
    }

    /**
     * Fails loudly when the stored items are not in this adapter's format (saved by Paper or by a
     * Fabric 1.20/1.21 server): keeping the local items silently let them be saved over the record.
     * A single unreadable item is skipped and logged.
     */
    private void applyContainer(ServerPlayer player, Container container, String json) {
        var registries = player.level().registryAccess();
        NonNullList<ItemStack> staging =
                NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        JsonArray slots;
        try {
            slots = JsonParser.parseString(json).getAsJsonArray();
        } catch (Exception e) {
            throw new IllegalStateException("The stored items of " + player.getName().getString()
                    + " were saved by Paper or by a Fabric server for another Minecraft version and cannot be read here.", e);
        }
        for (JsonElement el : slots) {
            JsonObject slot = el.getAsJsonObject();
            int idx = slot.get("slot").getAsInt();
            JsonElement itemEl = slot.get("item");
            if (itemEl == null || itemEl.isJsonNull()) continue;
            // A shrunken container (version downgrade) must not blow up the whole restore.
            if (idx < 0 || idx >= staging.size()) continue;
            try {
                DataResult<ItemStack> result =
                    ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), itemEl);
                staging.set(idx, result.getOrThrow());
            } catch (Exception e) {
                LOG.error("Skipped an unreadable item in slot {} of {}: {}", idx, player.getName().getString(), e.getMessage());
            }
        }

        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, staging.get(i));
        }
    }

    private boolean isExcluded(ItemStack stack, RegistryAccess registries) {
        if (itemExclusions.isEmpty()) {
            return false;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null) {
            return false;
        }
        // Accept both "minecraft:diamond" and the Bukkit-style "DIAMOND" spelling.
        return itemExclusions.contains(id.toString())
                || itemExclusions.contains(id.getPath().toUpperCase(Locale.ROOT));
    }

    private String serializeEffects(ServerPlayer player) {
        try {
            var registries = player.level().registryAccess();
            JsonArray arr = new JsonArray();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                DataResult<JsonElement> result = MobEffectInstance.CODEC
                        .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), effect);
                arr.add(result.getOrThrow());
            }
            return arr.toString();
        } catch (Exception e) {
            LOG.warn("Failed to serialize potion effects for {}", player.getName().getString(), e);
            return null;
        }
    }

    private void applyEffects(ServerPlayer player, String json) {
        try {
            var registries = player.level().registryAccess();
            player.removeAllEffects();
            for (JsonElement el : JsonParser.parseString(json).getAsJsonArray()) {
                DataResult<MobEffectInstance> result = MobEffectInstance.CODEC
                        .parse(registries.createSerializationContext(JsonOps.INSTANCE), el);
                player.addEffect(result.getOrThrow());
            }
        } catch (Exception e) {
            LOG.warn("Failed to restore potion effects for {}", player.getName().getString(), e);
        }
    }

    private Map<String, Double> captureAttributes(ServerPlayer player) {
        Map<String, Double> map = new HashMap<>();
        for (AttributeInstance instance : player.getAttributes().getSyncableAttributes()) {
            instance.getAttribute().unwrapKey()
                    .ifPresent(key -> map.put(key.identifier().toString(), instance.getBaseValue()));
        }
        return map;
    }

    private void applyAttributes(ServerPlayer player, Map<String, Double> attributes) {
        if (attributes == null) {
            return;
        }
        attributes.forEach((id, value) -> {
            Identifier parsed = Identifier.tryParse(id);
            if (parsed == null) {
                return;
            }
            BuiltInRegistries.ATTRIBUTE.get(parsed).ifPresent(holder -> {
                AttributeInstance instance = player.getAttribute(holder);
                if (instance != null) {
                    instance.setBaseValue(value);
                }
            });
        });
    }

    @Override
    public void setItemExclusions(List<String> materials) {
        this.itemExclusions = materials == null ? new ArrayList<>() : materials;
    }
}

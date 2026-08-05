package de.craftingstudiopro.playerDataSyncReloaded.fabric;

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

        return data;
    }

    @Override
    public void apply(PDSPlayer pdsPlayer, PlayerData data) {
        ServerPlayer player = (ServerPlayer) pdsPlayer.getHandle();

        player.setHealth((float) data.health);
        player.getFoodData().setFoodLevel(data.foodLevel);
        player.experienceProgress = data.exp;
        player.experienceLevel = data.level;
        player.totalExperience = data.totalExperience;

        if (data.inventoryContents != null) {
            deserializeInventory(pdsPlayer, data.inventoryContents);
        }
        if (data.enderChestContents != null) {
            applyContainer(player, player.getEnderChestInventory(), data.enderChestContents);
        }
        if (data.potionEffects != null) {
            applyEffects(player, data.potionEffects);
        }
        applyAttributes(player, data.attributes);
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
            LOG.warn("Failed to serialize container for {}", player.getName().getString(), e);
            return null;
        }
    }

    private void applyContainer(ServerPlayer player, Container container, String json) {
        try {
            var registries = player.level().registryAccess();
            NonNullList<ItemStack> staging =
                    NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);

            for (JsonElement el : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject slot = el.getAsJsonObject();
                int idx = slot.get("slot").getAsInt();
                JsonElement itemEl = slot.get("item");
                if (itemEl == null || itemEl.isJsonNull()) continue;
                // A shrunken container (version downgrade) must not blow up the whole restore.
                if (idx < 0 || idx >= staging.size()) continue;
                DataResult<ItemStack> result =
                    ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), itemEl);
                staging.set(idx, result.getOrThrow());
            }

            for (int i = 0; i < container.getContainerSize(); i++) {
                container.setItem(i, staging.get(i));
            }
        } catch (Exception e) {
            LOG.warn("Failed to restore container for {}", player.getName().getString(), e);
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

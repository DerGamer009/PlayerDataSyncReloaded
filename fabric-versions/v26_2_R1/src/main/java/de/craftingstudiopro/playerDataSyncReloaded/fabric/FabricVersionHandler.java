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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FabricVersionHandler implements VersionHandler {
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
    }

    @Override
    public String serializeInventory(PDSPlayer pdsPlayer) {
        ServerPlayer player = (ServerPlayer) pdsPlayer.getHandle();
        try {
            Inventory inv = player.getInventory();
            var registries = player.level().registryAccess();
            JsonArray arr = new JsonArray();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                JsonObject slot = new JsonObject();
                slot.addProperty("slot", i);
                if (stack.isEmpty()) {
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
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void deserializeInventory(PDSPlayer pdsPlayer, String inventory) {
        ServerPlayer player = (ServerPlayer) pdsPlayer.getHandle();
        try {
            Inventory inv = player.getInventory();
            var registries = player.level().registryAccess();
            NonNullList<ItemStack> staging = NonNullList.withSize(inv.getContainerSize(), ItemStack.EMPTY);

            JsonElement root = JsonParser.parseString(inventory);
            for (JsonElement el : root.getAsJsonArray()) {
                JsonObject slot = el.getAsJsonObject();
                int idx = slot.get("slot").getAsInt();
                JsonElement itemEl = slot.get("item");
                if (itemEl == null || itemEl.isJsonNull()) continue;
                DataResult<ItemStack> result =
                    ItemStack.CODEC.parse(registries.createSerializationContext(JsonOps.INSTANCE), itemEl);
                staging.set(idx, result.getOrThrow());
            }

            for (int i = 0; i < inv.getContainerSize(); i++) {
                inv.setItem(i, staging.get(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setItemExclusions(List<String> materials) {
        this.itemExclusions = materials == null ? new ArrayList<>() : materials;
    }
}

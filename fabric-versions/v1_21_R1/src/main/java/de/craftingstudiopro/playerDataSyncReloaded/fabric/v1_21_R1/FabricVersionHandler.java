package de.craftingstudiopro.playerDataSyncReloaded.fabric.v1_21_R1;

import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

public class FabricVersionHandler implements VersionHandler {
    private static final int NBT_COMPOUND_ID = 10;
    private List<String> itemExclusions;

    @Override
    public PlayerData capture(PDSPlayer pdsPlayer) {
        ServerPlayerEntity player = (ServerPlayerEntity) pdsPlayer.getHandle();
        PlayerData data = new PlayerData();
        data.uuid = player.getUuid();
        data.name = player.getName().getString();

        // Stats
        data.health = player.getHealth();
        data.foodLevel = player.getHungerManager().getFoodLevel();
        data.saturation = player.getHungerManager().getSaturationLevel();
        data.exp = player.experienceProgress;
        data.level = player.experienceLevel;
        data.totalExperience = player.totalExperience;

        // Inventory
        data.inventoryContents = serializeInventory(pdsPlayer);

        // GameMode
        data.gameMode = player.interactionManager.getGameMode().name();

        return data;
    }

    @Override
    public void apply(PDSPlayer pdsPlayer, PlayerData data) {
        ServerPlayerEntity player = (ServerPlayerEntity) pdsPlayer.getHandle();

        // A player who left on the death screen is stored with 0 health; applying it kills them again.
        if (data.health > 0) {
            player.setHealth((float) data.health);
        }
        player.getHungerManager().setFoodLevel(data.foodLevel);
        player.experienceProgress = data.exp;
        player.experienceLevel = data.level;
        player.totalExperience = data.totalExperience;

        if (data.inventoryContents != null) {
            deserializeInventory(pdsPlayer, data.inventoryContents);
        }
    }

    @Override
    public String serializeInventory(PDSPlayer pdsPlayer) {
        ServerPlayerEntity player = (ServerPlayerEntity) pdsPlayer.getHandle();
        try {
            NbtList list = new NbtList();
            player.getInventory().writeNbt(list);
            NbtCompound root = new NbtCompound();
            root.put("Inventory", list);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void deserializeInventory(PDSPlayer pdsPlayer, String inventory) {
        ServerPlayerEntity player = (ServerPlayerEntity) pdsPlayer.getHandle();
        try {
            byte[] bytes = Base64.getDecoder().decode(inventory);
            NbtCompound root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtSizeTracker.ofUnlimitedBytes());
            NbtList list = root.getList("Inventory", NBT_COMPOUND_ID);
            player.getInventory().readNbt(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setItemExclusions(List<String> materials) {
        this.itemExclusions = materials;
    }
}

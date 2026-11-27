package de.craftingstudiopro.playerDataSyncReloaded.fabric.v1_21_R1;

import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.WorldNames;
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
    /** Written into the stored inventory; NBT item data of another Minecraft version is not read back. */
    private static final String MC_VERSION = "1.21.4";
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

        // Location; SyncManager#filterData drops it unless sync.location is on.
        data.worldName = player.getServerWorld().getRegistryKey().getValue().toString();
        data.x = player.getX();
        data.y = player.getY();
        data.z = player.getZ();
        data.yaw = player.getYaw();
        data.pitch = player.getPitch();

        // Not captured on Fabric: mark them, so Paper does not apply the empty values
        // (walk speed 0, no air, no flight, balance 0 ...).
        for (String key : new String[]{"air_level", "fire_ticks", "freeze_ticks", "arrows_in_body", "absorption", "speeds", "fall_distance", "flight", "pdc", "advancements", "statistics", "player_time", "player_weather", "economy", "ender_chest", "potion_effects", "attributes"}) {
            data.markNotSynced(key);
        }

        return data;
    }

    @Override
    public void apply(PDSPlayer pdsPlayer, PlayerData data) {
        ServerPlayerEntity player = (ServerPlayerEntity) pdsPlayer.getHandle();

        // A player who left on the death screen is stored with 0 health; applying it kills them again.
        // Values the saving server or this server does not sync are placeholders (issue #7).
        if (data.isSynced("health") && data.health > 0) {
            player.setHealth((float) Math.min(data.health, player.getMaxHealth()));
        }
        if (data.isSynced("food")) {
            player.getHungerManager().setFoodLevel(data.foodLevel);
            player.getHungerManager().setSaturationLevel(data.saturation);
        }
        if (data.isSynced("game_mode") && data.gameMode != null) {
            try {
                player.changeGameMode(net.minecraft.world.GameMode.valueOf(data.gameMode));
            } catch (IllegalArgumentException e) {
                org.slf4j.LoggerFactory.getLogger("PlayerDataSync").warn("Unknown game mode '{}' for {}", data.gameMode, player.getName().getString());
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

        // Location: only present with sync.location: true (filtered on save and before apply).
        if (data.worldName != null) {
            String dimension = WorldNames.toDimensionId(data.worldName);
            net.minecraft.util.Identifier key = dimension == null ? null : net.minecraft.util.Identifier.tryParse(dimension);
            net.minecraft.server.world.ServerWorld world = key == null ? null : player.getServerWorld().getServer()
                    .getWorld(net.minecraft.registry.RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, key));
            if (world == null) {
                org.slf4j.LoggerFactory.getLogger("PlayerDataSync").warn("Skipping location restore for {}: world '{}' does not exist on this server.",
                        player.getName().getString(), data.worldName);
            } else {
                player.teleport(world, data.x, data.y, data.z, java.util.Set.of(), data.yaw, data.pitch, true);
            }
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
            root.putString("PdsMinecraft", MC_VERSION);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            NbtIo.writeCompressed(root, bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            // Returning null stored the record without the inventory; fail the save instead.
            throw new IllegalStateException("Could not store the inventory of " + player.getName().getString(), e);
        }
    }

    @Override
    public void deserializeInventory(PDSPlayer pdsPlayer, String inventory) {
        ServerPlayerEntity player = (ServerPlayerEntity) pdsPlayer.getHandle();
        NbtCompound root;
        try {
            // Gzip NBT starts with H4sI in Base64; Paper (rO0...) and Fabric 26.x ([...) use other formats.
            if (!inventory.startsWith("H4sI")) {
                throw new IllegalArgumentException("not a Fabric 1.20/1.21 inventory");
            }
            byte[] bytes = Base64.getDecoder().decode(inventory);
            root = NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtSizeTracker.ofUnlimitedBytes());
        } catch (Exception e) {
            throw new IllegalStateException("The stored inventory of " + player.getName().getString()
                    + " was saved by Paper or by a Fabric server for another Minecraft version and cannot be read here.", e);
        }
        String savedOn = root.getString("PdsMinecraft");
        if (!savedOn.isEmpty() && !savedOn.equals(MC_VERSION)) {
            throw new IllegalStateException("The stored inventory of " + player.getName().getString()
                    + " was saved on Minecraft " + savedOn + "; this server runs " + MC_VERSION + ".");
        }
        player.getInventory().readNbt(root.getList("Inventory", NBT_COMPOUND_ID));
    }

    @Override
    public void setItemExclusions(List<String> materials) {
        this.itemExclusions = materials;
    }
}

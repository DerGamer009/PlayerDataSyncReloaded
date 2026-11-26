package de.craftingstudiopro.playerDataSyncReloaded.fabric.v1_21_R1;

import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class FabricPDSPlayer implements PDSPlayer {
    private final ServerPlayerEntity player;

    public FabricPDSPlayer(ServerPlayerEntity player) {
        this.player = player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUuid();
    }

    @Override
    public String getName() {
        return player.getName().getString();
    }

    @Override
    public Object getHandle() {
        return player;
    }

    @Override
    public String getWorldName() {
        return player.getWorld().getRegistryKey().getValue().toString();
    }
}

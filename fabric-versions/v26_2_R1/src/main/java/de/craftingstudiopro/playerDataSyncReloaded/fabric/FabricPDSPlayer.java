package de.craftingstudiopro.playerDataSyncReloaded.fabric;

import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class FabricPDSPlayer implements PDSPlayer {
    private final ServerPlayer player;

    public FabricPDSPlayer(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUUID();
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
        return player.level().dimension().identifier().toString();
    }
}

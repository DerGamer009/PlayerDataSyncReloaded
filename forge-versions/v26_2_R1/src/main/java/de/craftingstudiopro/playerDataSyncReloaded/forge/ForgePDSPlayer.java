package de.craftingstudiopro.playerDataSyncReloaded.forge;

import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class ForgePDSPlayer implements PDSPlayer {
    private final ServerPlayer player;

    public ForgePDSPlayer(ServerPlayer player) {
        this.player = player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUUID();
    }

    @Override
    public String getName() {
        return player.getGameProfile().getName();
    }

    @Override
    public Object getHandle() {
        return player;
    }

    @Override
    public String getWorldName() {
        return player.level().dimension().location().toString();
    }
}

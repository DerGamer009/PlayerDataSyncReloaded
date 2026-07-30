package de.craftingstudiopro.playerDataSyncReloaded.forge;

import de.craftingstudiopro.playerDataSyncReloaded.common.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class ForgePlatform implements Platform {
    private final Logger logger = Logger.getLogger("PlayerDataSync");

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public void runTask(Runnable task) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(task);
    }

    @Override
    public void runTaskAsync(Runnable task) {
        new Thread(task).start();
    }

    @Override
    public boolean isOnline(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.getPlayerList().getPlayer(uuid) != null;
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(Component.literal(message.replace("&", "§")));
            }
        }
    }

    @Override
    public String getConfigString(String path, String def) {
        return def;
    }

    @Override
    public boolean getConfigBoolean(String path, boolean def) {
        return def;
    }

    @Override
    public List<String> getConfigStringList(String path) {
        return Collections.emptyList();
    }
}

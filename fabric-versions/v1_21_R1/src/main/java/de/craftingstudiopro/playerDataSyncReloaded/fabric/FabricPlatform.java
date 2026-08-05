package de.craftingstudiopro.playerDataSyncReloaded.fabric;

import de.craftingstudiopro.playerDataSyncReloaded.common.FileConfig;
import de.craftingstudiopro.playerDataSyncReloaded.common.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class FabricPlatform implements Platform {
    private final MinecraftServer server;
    private final Logger logger = Logger.getLogger("PlayerDataSync");

    private final FileConfig config;

    public FabricPlatform(MinecraftServer server) {
        this.server = server;
        this.config = new FileConfig(
                FabricLoader.getInstance().getConfigDir().resolve("playerdatasync.properties"), logger);
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public void runTask(Runnable task) {
        server.execute(task);
    }

    @Override
    public void runTaskAsync(Runnable task) {
        new Thread(task).start(); // Simple async for Fabric
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return server.getPlayerManager().getPlayer(uuid) != null;
    }

    @Override
    public de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer getPlayer(UUID uuid) {
        var player = server.getPlayerManager().getPlayer(uuid);
        return player != null ? new FabricPDSPlayer(player) : null;
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        var player = server.getPlayerManager().getPlayer(uuid);
        if (player != null) {
            player.sendMessage(Text.literal(message.replace("&", "§")), false);
        }
    }

    @Override
    public String getConfigString(String path, String def) {
        return config.getString(path, def);
    }

    @Override
    public boolean getConfigBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    @Override
    public List<String> getConfigStringList(String path) {
        return config.getStringList(path);
    }
}

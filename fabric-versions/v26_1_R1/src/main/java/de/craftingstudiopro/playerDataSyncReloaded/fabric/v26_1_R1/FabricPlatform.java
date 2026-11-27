package de.craftingstudiopro.playerDataSyncReloaded.fabric.v26_1_R1;

import de.craftingstudiopro.playerDataSyncReloaded.common.FileConfig;
import de.craftingstudiopro.playerDataSyncReloaded.common.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

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
        new Thread(task).start();
    }

    @Override
    public boolean isOnline(UUID uuid) {
        // SyncManager asks from database threads too; the server's player list is a plain HashMap.
        return online.contains(uuid);
    }

    /** Players between JOIN and DISCONNECT, kept in a concurrent set for {@link #isOnline}. */
    private final java.util.Set<UUID> online = java.util.concurrent.ConcurrentHashMap.newKeySet();

    void markOnline(UUID uuid) {
        online.add(uuid);
    }

    void markOffline(UUID uuid) {
        online.remove(uuid);
    }

    @Override
    public de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer getPlayer(UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        return player != null ? new FabricPDSPlayer(player) : null;
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message.replace("&", "§")));
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

    @Override
    public String getDefaultServerName() {
        return "port " + server.getPort();
    }
}

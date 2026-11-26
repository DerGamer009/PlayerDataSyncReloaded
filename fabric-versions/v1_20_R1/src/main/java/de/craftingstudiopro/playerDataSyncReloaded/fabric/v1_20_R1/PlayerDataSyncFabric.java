package de.craftingstudiopro.playerDataSyncReloaded.fabric.v1_20_R1;

import de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer;
import de.craftingstudiopro.playerDataSyncReloaded.common.SyncManager;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.SqlStorage;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PlayerDataSyncFabric implements ModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("PlayerDataSync");

    private SyncManager syncManager;
    private Storage storage;
    private FabricPlatform platform;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(this::setup);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                PdsCommand.register(dispatcher, () -> syncManager,
                        () -> platform == null ? "unknown" : platform.getConfigString("storage.type", "mariadb")));
        // Players still online when the server stops never fire DISCONNECT in time: save them here.
        ServerLifecycleEvents.SERVER_STOPPING.register(srv -> {
            if (syncManager == null) {
                return;
            }
            List<PDSPlayer> players = new ArrayList<>();
            for (ServerPlayerEntity player : srv.getPlayerManager().getPlayerList()) {
                players.add(new FabricPDSPlayer(player));
            }
            syncManager.saveAll(players);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(srv -> {
            if (storage != null) {
                storage.close();
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (syncManager != null) {
                syncManager.handleJoin(new FabricPDSPlayer(handler.player));
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (syncManager != null) {
                syncManager.handleDisconnect(new FabricPDSPlayer(handler.player));
            }
        });
    }

    private void setup(MinecraftServer server) {
        this.platform = new FabricPlatform(server);

        this.storage = new SqlStorage(
                platform.getLogger(),
                platform.getConfigString("storage.type", "sqlite"),
                platform.getConfigString("storage.host", ""),
                parsePort(platform.getConfigString("storage.port", "0")),
                platform.getConfigString("storage.database", "playerdata.db"),
                platform.getConfigString("storage.username", ""),
                platform.getConfigString("storage.password", ""));
        try {
            this.storage.init();
        } catch (Exception e) {
            LOG.error("Failed to initialize storage", e);
        }

        this.syncManager = new SyncManager(platform, storage, new FabricVersionHandler());
        platform.getLogger().info("PlayerDataSync Fabric initialized!");

        Identifier syncId = new Identifier("pds", "sync");
        ServerPlayNetworking.registerGlobalReceiver(syncId, (srv, player, handler, buf, responseSender) -> {
            // Raw UTF-8 without a length prefix, as Velocity and Paper send it.
            byte[] raw = new byte[buf.readableBytes()];
            buf.readBytes(raw);
            String msg = new String(raw, StandardCharsets.UTF_8);
            srv.execute(() -> {
                if (syncManager == null) {
                    return;
                }
                if (msg.startsWith("save:")) {
                    // Save handshake: answer once the save finished, failed or was skipped.
                    syncManager.handleQuit(new FabricPDSPlayer(player)).whenComplete((unused, ex) -> srv.execute(() -> {
                        if (platform.isOnline(player.getUuid())) {
                            PacketByteBuf ack = PacketByteBufs.create();
                            ack.writeBytes(("saved:" + player.getUuid()).getBytes(StandardCharsets.UTF_8));
                            ServerPlayNetworking.send(player, syncId, ack);
                        }
                    }));
                } else if (msg.startsWith("load:")) {
                    syncManager.handleJoin(new FabricPDSPlayer(player));
                }
            });
        });
    }

    private int parsePort(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            LOG.warn("storage.port is not a number ('{}'), using 0", raw);
            return 0;
        }
    }
}

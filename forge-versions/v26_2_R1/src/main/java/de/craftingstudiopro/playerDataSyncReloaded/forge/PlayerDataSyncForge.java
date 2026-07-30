package de.craftingstudiopro.playerDataSyncReloaded.forge;

import de.craftingstudiopro.playerDataSyncReloaded.common.SyncManager;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.SqlStorage;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("playerdatasync")
public class PlayerDataSyncForge {
    private static final Logger LOGGER = LogManager.getLogger();

    private SyncManager syncManager;
    private Storage storage;
    private ForgePlatform platform;

    public static final SimpleChannel CHANNEL = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath("pds", "sync"))
        .networkProtocolVersion(1)
        .simpleChannel();

    public PlayerDataSyncForge() {
        MinecraftForge.EVENT_BUS.register(this);

        CHANNEL.messageBuilder(String.class, 0)
            .encoder((msg, buf) -> buf.writeUtf(msg))
            .decoder(buf -> buf.readUtf(32767))
            .consumerMainThread((msg, ctx) -> {
                if (syncManager == null) {
                    return;
                }
                var player = ctx.getSender();
                if (player != null) {
                    if (msg.startsWith("save:")) {
                        syncManager.handleQuit(new ForgePDSPlayer(player));
                    } else if (msg.startsWith("load:")) {
                        syncManager.handleJoin(new ForgePDSPlayer(player));
                    }
                }
            })
            .add();

        CHANNEL.build();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        this.platform = new ForgePlatform();
        this.storage = new SqlStorage(platform.getLogger(), "sqlite", "", 0, "playerdata.db", "", "");
        try {
            this.storage.init();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize storage", e);
        }
        this.syncManager = new SyncManager(platform, storage, new ForgeVersionHandler());
        platform.getLogger().info("PlayerDataSync Forge initialized!");
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (syncManager != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            syncManager.handleJoin(new ForgePDSPlayer(player));
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (syncManager != null && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            syncManager.handleQuit(new ForgePDSPlayer(player));
        }
    }
}

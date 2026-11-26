package de.craftingstudiopro.playerDataSyncReloaded.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Plugin(
        id = "playerdatasync",
        name = "PlayerDataSync Velocity",
        version = PluginBuildInfo.VERSION,
        authors = {"CraftingStudioPro"}
)
public class PlayerDataSyncVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    public static final MinecraftChannelIdentifier IDENTIFIER = MinecraftChannelIdentifier.from("pds:sync");

    /** Switches waiting for the source server's {@code saved:<uuid>} answer. */
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> pendingSaves = new ConcurrentHashMap<>();
    /** Backends that did not answer in time; switches away from them no longer wait until they answer again. */
    private final Set<String> silentServers = ConcurrentHashMap.newKeySet();
    private long saveAckTimeoutMs = 2000L;

    @Inject
    public PlayerDataSyncVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(IDENTIFIER);
        loadConfig();
        logger.info("PlayerDataSync Velocity Integration initialized (save ack timeout: {} ms).", saveAckTimeoutMs);
    }

    private void loadConfig() {
        Properties props = new Properties();
        Path file = dataDirectory.resolve("playerdatasync.properties");
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(dataDirectory);
                try (OutputStream out = Files.newOutputStream(file)) {
                    props.setProperty("save_ack_timeout_ms", Long.toString(saveAckTimeoutMs));
                    props.store(out, "How long a server switch waits for the source server to confirm its save. 0 = don't wait.");
                }
                return;
            }
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
            saveAckTimeoutMs = Long.parseLong(props.getProperty("save_ack_timeout_ms", "2000").trim());
        } catch (IOException | NumberFormatException e) {
            logger.warn("Could not read {}: {} - using default {} ms.", file, e.getMessage(), saveAckTimeoutMs);
        }
    }

    /**
     * Save handshake: ask the current server to save and hold the switch until it answers
     * {@code saved:<uuid>}, so the next server does not read the record before it is written.
     */
    @Subscribe
    public EventTask onServerPreConnect(ServerPreConnectEvent event) {
        ServerConnection current = event.getPlayer().getCurrentServer().orElse(null);
        if (current == null) {
            return null;
        }

        UUID uuid = event.getPlayer().getUniqueId();
        String serverName = current.getServerInfo().getName();
        CompletableFuture<Void> ack = new CompletableFuture<>();
        pendingSaves.put(uuid, ack);
        current.sendPluginMessage(IDENTIFIER, ("save:" + uuid).getBytes(StandardCharsets.UTF_8));
        logger.info("Triggered fast-save for {} on {}", event.getPlayer().getUsername(), serverName);

        if (saveAckTimeoutMs <= 0L || silentServers.contains(serverName)) {
            return null;
        }
        return EventTask.async(() -> {
            try {
                ack.get(saveAckTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                silentServers.add(serverName);
                logger.warn("{} did not confirm the save for {} within {} ms; connecting anyway and no longer waiting on that server.",
                        serverName, event.getPlayer().getUsername(), saveAckTimeoutMs);
            } catch (Exception ignored) {
                // interrupted or cancelled: connect without waiting
            } finally {
                pendingSaves.remove(uuid, ack);
            }
        });
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!IDENTIFIER.equals(event.getIdentifier())) {
            return;
        }
        if (!(event.getSource() instanceof ServerConnection source)) {
            return;
        }
        // Backend-to-proxy messages; never forward them to the client.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        String msg = new String(event.getData(), StandardCharsets.UTF_8);
        if (!msg.startsWith("saved:")) {
            return;
        }
        UUID uuid;
        try {
            uuid = UUID.fromString(msg.substring("saved:".length()).trim());
        } catch (IllegalArgumentException e) {
            logger.warn("Ignoring malformed save ack from {}: {}", source.getServerInfo().getName(), msg);
            return;
        }
        silentServers.remove(source.getServerInfo().getName());
        CompletableFuture<Void> ack = pendingSaves.remove(uuid);
        if (ack != null) {
            ack.complete(null);
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        // Notify the target server that the player has connected and data should be ready or loaded
        byte[] data = ("load:" + event.getPlayer().getUniqueId()).getBytes(StandardCharsets.UTF_8);
        event.getServer().sendPluginMessage(IDENTIFIER, data);
        logger.info("Notified {} of connection for {}", event.getServer().getServerInfo().getName(), event.getPlayer().getUsername());
    }
}

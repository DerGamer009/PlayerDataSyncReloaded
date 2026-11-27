package de.craftingstudiopro.playerDataSyncReloaded.plugin.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.craftingstudiopro.playerDataSyncReloaded.PlayerDataSyncReloaded;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class UpdateChecker implements Listener {

    private final PlayerDataSyncReloaded plugin;
    private final String currentVersion;
    private final String projectId = "playerdatasync"; // Modrinth slug
    private volatile String latestVersion;
    private volatile boolean updateAvailable = false;

    public UpdateChecker(PlayerDataSyncReloaded plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();

        // Register listener for join notifications
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void check() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Only Paper entries: since 26.10 every platform has its own Modrinth entry, and the
                // newest one may be the Velocity or Fabric jar with a different version number.
                String loaders = URLEncoder.encode("[\"paper\"]", StandardCharsets.UTF_8);
                URL url = new URL("https://api.modrinth.com/v2/project/" + projectId + "/version?loaders=" + loaders);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "PlayerDataSyncReloaded-UpdateChecker");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == 200) {
                    JsonArray versions = JsonParser.parseReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)).getAsJsonArray();
                    latestVersion = newestFor(versions, isPreRelease(currentVersion));

                    if (latestVersion != null) {
                        // Only a newer version counts: a backport published after a newer build, or a
                        // local build ahead of Modrinth, is not an update.
                        if (de.craftingstudiopro.playerDataSyncReloaded.common.util.Versions.compare(latestVersion, currentVersion) > 0) {
                            updateAvailable = true;
                            plugin.getLogger().warning("§e[PlayerDataSync] A new version is available: §f" + latestVersion);
                            plugin.getLogger().warning("§e[PlayerDataSync] You are currently running: §f" + currentVersion);
                            plugin.getLogger().warning("§e[PlayerDataSync] Download it here: §fhttps://modrinth.com/plugin/" + projectId);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Could not check for updates on Modrinth: " + e.getMessage());
            }
        });
    }

    /**
     * Newest entry Modrinth lists (newest first). A release server is only pointed to releases;
     * a server running an alpha or beta is pointed to the newest build of any channel.
     */
    static String newestFor(JsonArray versions, boolean includePreReleases) {
        for (int i = 0; i < versions.size(); i++) {
            JsonObject version = versions.get(i).getAsJsonObject();
            String type = version.has("version_type") ? version.get("version_type").getAsString() : "release";
            if (includePreReleases || "release".equalsIgnoreCase(type)) {
                return version.get("version_number").getAsString();
            }
        }
        return null;
    }

    static boolean isPreRelease(String version) {
        return de.craftingstudiopro.playerDataSyncReloaded.common.util.Versions.isPreRelease(version);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (updateAvailable && player.hasPermission("playerdatasync.admin")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.sendMessage("§b§l[PlayerDataSync] §eUpdate available!");
                player.sendMessage("§8 » §7Version: §f" + latestVersion + " §8(Current: " + currentVersion + ")");
                player.sendMessage("§8 » §7Download: §bmodrinth.com/plugin/" + projectId);
            }, 40L); // 2 second delay
        }
    }
}

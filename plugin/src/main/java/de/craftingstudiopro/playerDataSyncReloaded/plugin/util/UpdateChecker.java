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

public class UpdateChecker implements Listener {

    private final PlayerDataSyncReloaded plugin;
    private final String currentVersion;
    private final String projectId = "playerdatasync"; // Modrinth slug
    private String latestVersion;
    private boolean updateAvailable = false;

    public UpdateChecker(PlayerDataSyncReloaded plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
        
        // Register listener for join notifications
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void check() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL("https://api.modrinth.com/v2/project/" + projectId + "/version");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "PlayerDataSyncReloaded-UpdateChecker");

                if (connection.getResponseCode() == 200) {
                    JsonArray versions = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonArray();
                    if (versions.size() > 0) {
                        latestVersion = versions.get(0).getAsJsonObject().get("version_number").getAsString();
                        
                        if (!currentVersion.equalsIgnoreCase(latestVersion)) {
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

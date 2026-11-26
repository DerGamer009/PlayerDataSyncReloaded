package de.craftingstudiopro.playerDataSyncReloaded.common.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscordWebhookManager {
    private final Logger logger;
    private final String webhookUrl;
    private final String username;
    private final String avatarUrl;
    private final boolean enabled;

    public DiscordWebhookManager(Logger logger, String webhookUrl, String username, String avatarUrl, boolean enabled) {
        this.logger = logger;
        this.webhookUrl = webhookUrl;
        this.username = username;
        this.avatarUrl = avatarUrl;
        this.enabled = enabled;
    }

    public void sendSyncSuccess(String playerName, long duration) {
        sendEmbed("Sync Success", "Successfully synced data for **" + playerName + "**.", 0x00FF00, "Duration: " + duration + "ms");
    }

    public void sendSyncFailed(String playerName, String error) {
        sendEmbed("Sync Failed", "Failed to sync data for **" + playerName + "**.", 0xFF0000, "Error: " + error);
    }

    public void sendSaveRefused(String playerName, String serverName) {
        sendEmbed("Save Refused", "Did not save **" + playerName + "** on **" + serverName
                + "**: another server had stored a newer record. The newer record was kept and loaded.", 0xFFAA00,
                "This server had read an older record, usually because a server switch did not wait for the previous server's save.");
    }

    public void sendError(String error) {
        sendEmbed("System Error", "An internal error occurred.", 0xFFAA00, "Error: " + error);
    }

    public void sendBackup(String fileName, long size) {
        sendEmbed("Backup Created", "A new backup has been created.", 0x00AAFF, "File: " + fileName + " (" + (size / 1024) + " KB)");
    }

    private void sendEmbed(String title, String description, int color, String footer) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) return;

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("username", username);
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    json.addProperty("avatar_url", avatarUrl);
                }

                JsonArray embeds = new JsonArray();
                JsonObject embed = new JsonObject();
                embed.addProperty("title", title);
                embed.addProperty("description", description);
                embed.addProperty("color", color);

                JsonObject footerJson = new JsonObject();
                footerJson.addProperty("text", footer);
                embed.add("footer", footerJson);

                embeds.add(embed);
                json.add("embeds", embeds);

                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = json.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode >= 400) {
                    logger.warning("Discord Webhook returned code " + responseCode);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send Discord webhook", e);
            }
        });
    }
}

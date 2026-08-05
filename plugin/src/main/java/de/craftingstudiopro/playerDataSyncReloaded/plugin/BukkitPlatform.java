package de.craftingstudiopro.playerDataSyncReloaded.plugin;

import de.craftingstudiopro.playerDataSyncReloaded.common.Platform;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class BukkitPlatform implements Platform {
    private final JavaPlugin plugin;

    public BukkitPlatform(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Logger getLogger() {
        return plugin.getLogger();
    }

    @Override
    public void runTask(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runTaskAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return Bukkit.getPlayer(uuid) != null;
    }

    @Override
    public de.craftingstudiopro.playerDataSyncReloaded.api.PDSPlayer getPlayer(UUID uuid) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
        return player != null ? new BukkitPDSPlayer(player) : null;
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bSync&8] &r");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', prefix + message));
        }
    }

    @Override
    public String getConfigString(String path, String def) {
        return plugin.getConfig().getString(path, def);
    }

    @Override
    public boolean getConfigBoolean(String path, boolean def) {
        return plugin.getConfig().getBoolean(path, def);
    }

    @Override
    public List<String> getConfigStringList(String path) {
        return plugin.getConfig().getStringList(path);
    }
}

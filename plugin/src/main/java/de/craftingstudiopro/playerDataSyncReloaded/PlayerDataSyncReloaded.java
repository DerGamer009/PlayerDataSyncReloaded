package de.craftingstudiopro.playerDataSyncReloaded;

import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.SyncManager;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.MongoStorage;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.SqlStorage;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
import de.craftingstudiopro.playerDataSyncReloaded.v26_1.VersionHandlerImpl;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerDataSyncReloaded extends JavaPlugin implements Listener {

    private VersionHandler versionHandler;
    private Storage storage;
    private SyncManager syncManager;

    @Override
    public void onEnable() {
        sendBanner();
        saveDefaultConfig();
        
        if (!setupVersionHandler()) {
            getLogger().severe("§cCould not support your server version: " + Bukkit.getBukkitVersion());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupStorage()) {
            getLogger().severe("§cFailed to initialize storage. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("§aSuccessfully connected to storage backend.");

        this.syncManager = new SyncManager(this, storage, versionHandler);
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getCommand("playerdatasync").setExecutor(new de.craftingstudiopro.playerDataSyncReloaded.plugin.command.PDSCommand(this));
        getCommand("playerdatasync").setTabCompleter(new de.craftingstudiopro.playerDataSyncReloaded.plugin.command.PDSCommand(this));

        // Initialize bStats
        new org.bstats.bukkit.Metrics(this, 30594);

        getLogger().info("PlayerDataSyncReloaded version " + getDescription().getVersion() + " enabled.");
    }

    private boolean setupVersionHandler() {
        String versionString = Bukkit.getServer().getClass().getPackage().getName();
        String nmsVersion = versionString.substring(versionString.lastIndexOf('.') + 1);
        
        getLogger().info("Detected NMS Version: " + nmsVersion);

        try {
            if (nmsVersion.equals("v1_8_R3")) {
                this.versionHandler = new de.craftingstudiopro.playerDataSyncReloaded.v1_8_R3.VersionHandlerImpl();
            } else if (nmsVersion.startsWith("v26") || nmsVersion.startsWith("v1_21")) {
                this.versionHandler = new de.craftingstudiopro.playerDataSyncReloaded.v26_1.VersionHandlerImpl();
            } else {
                // Default to modern handler as it's the most compatible with recent years
                this.versionHandler = new de.craftingstudiopro.playerDataSyncReloaded.v26_1.VersionHandlerImpl();
                getLogger().warning("Unknown NMS version! Using modern fallback (v26.1). Might have issues.");
            }
            return true;
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Version handler setup failed", e);
            return false;
        }
    }

    private boolean setupStorage() {
        FileConfiguration config = getConfig();
        ConfigurationSection dbConfig = config.getConfigurationSection("storage");
        if (dbConfig == null) return false;

        String type = dbConfig.getString("type", "mysql").toLowerCase();
        String host = dbConfig.getString("host", "localhost");
        int port = dbConfig.getInt("port", 3306);
        String database = dbConfig.getString("database", "minecraft");
        String user = dbConfig.getString("username", "root");
        String password = dbConfig.getString("password", "");
        String connectionUrl = dbConfig.getString("connection_url", "");
        String encryptionKey = config.getString("security.encryption_key", "");

        if (type.equals("mongodb")) {
            MongoStorage mongo = new MongoStorage(getLogger(), connectionUrl, database);
            mongo.setEncryptionKey(encryptionKey);
            storage = mongo;
        } else {
            SqlStorage sql = new SqlStorage(getLogger(), type, host, port, database, user, password);
            sql.setEncryptionKey(encryptionKey);
            storage = sql;
        }

        try {
            storage.init();
            return true;
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Storage initialization failed", e);
            return false;
        }
    }

    private void sendBanner() {
        String version = getDescription().getVersion();
        String authors = "CraftingStudioPro, DerGamer09";
        
        Bukkit.getConsoleSender().sendMessage("§b");
        Bukkit.getConsoleSender().sendMessage("§b  ____  ____   ____    ____  _____ _      ___  ____  ____  _____ ____  ");
        Bukkit.getConsoleSender().sendMessage("§b |  _ \\|  _  | / ___|  |  _ \\| ____| |    / _ \\|  _ \\|  _ \\| ____|  _ \\ ");
        Bukkit.getConsoleSender().sendMessage("§b | |_) | | | | \\___ \\  | |_) |  _| | |   | | | | |_) | | | |  _| | | | |");
        Bukkit.getConsoleSender().sendMessage("§b |  __/| |_| |  ___) | |  _ <| |___| |___| |_| |  _ <| |_| | |___| |_| |");
        Bukkit.getConsoleSender().sendMessage("§b |_|   |_____/ |____/  |_| \\_\\_____|_____|\\___/|_| \\_\\____/|_____|____/ ");
        Bukkit.getConsoleSender().sendMessage("§b ");
        Bukkit.getConsoleSender().sendMessage("§8 > §fVersion: §b" + version);
        Bukkit.getConsoleSender().sendMessage("§8 > §fAuthors: §b" + authors);
        Bukkit.getConsoleSender().sendMessage("§8 > §fStatus:  §aRunning on " + Bukkit.getServer().getName());
        Bukkit.getConsoleSender().sendMessage("§b");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        syncManager.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        syncManager.handleQuit(event.getPlayer());
    }
}

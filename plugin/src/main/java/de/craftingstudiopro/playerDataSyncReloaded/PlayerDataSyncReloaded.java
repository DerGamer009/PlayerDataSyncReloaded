package de.craftingstudiopro.playerDataSyncReloaded;

import de.craftingstudiopro.playerDataSyncReloaded.api.VersionHandler;
import de.craftingstudiopro.playerDataSyncReloaded.common.Migrator;
import de.craftingstudiopro.playerDataSyncReloaded.common.SyncManager;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.MongoStorage;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.SqlStorage;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
// import de.craftingstudiopro.playerDataSyncReloaded.v26_1.VersionHandlerImpl;
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
import dev.faststats.bukkit.BukkitMetrics;
import dev.faststats.core.Metrics;


public final class PlayerDataSyncReloaded extends JavaPlugin implements Listener {

    private VersionHandler versionHandler;
    private Storage storage;
    private SyncManager syncManager;
    private de.craftingstudiopro.playerDataSyncReloaded.common.redis.RedisManager redisManager;
    private net.milkbowl.vault.economy.Economy economy;
    private de.craftingstudiopro.playerDataSyncReloaded.common.BackupManager backupManager;
    private final Metrics metrics = BukkitMetrics.factory()
            .token("744d645fca7c2275b2986db7cd58da0c")
            .create(this);


    @Override
    public void onEnable() {
        sendBanner();
        saveDefaultConfig();
        
        if (!setupVersionHandler()) {
            getLogger().severe("§cCould not support your server version: " + Bukkit.getBukkitVersion());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        debug("Version handler " + versionHandler.getClass().getSimpleName() + " initialized.");

        if (!setupStorage()) {
            getLogger().severe("§cFailed to initialize storage. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        getLogger().info("§aSuccessfully connected to storage backend.");

        this.syncManager = new SyncManager(this, storage, versionHandler);
        this.backupManager = new de.craftingstudiopro.playerDataSyncReloaded.common.BackupManager(getLogger(), storage, new java.io.File(getDataFolder(), "backups"));
        setupVault();
        setupRedis();
        
        Bukkit.getPluginManager().registerEvents(this, this);
        
        getCommand("playerdatasync").setExecutor(new de.craftingstudiopro.playerDataSyncReloaded.plugin.command.PDSCommand(this));
        getCommand("playerdatasync").setTabCompleter(new de.craftingstudiopro.playerDataSyncReloaded.plugin.command.PDSCommand(this));

        // Initialize bStats
        new org.bstats.bukkit.Metrics(this, 30594);

        // Run Update Checker
        new de.craftingstudiopro.playerDataSyncReloaded.plugin.util.UpdateChecker(this).check();

        metrics.ready();


        startAutoSaveTask();

        getLogger().info("PlayerDataSyncReloaded version " + getDescription().getVersion() + " enabled.");
    }

    private boolean setupVersionHandler() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        
        getLogger().info("Detected Bukkit Version: " + bukkitVersion);

        try {
            if (bukkitVersion.contains("1.21.4") || bukkitVersion.contains("26.1.1") || bukkitVersion.contains("26.1.2")) {
                this.versionHandler = new de.craftingstudiopro.playerDataSyncReloaded.v26_1.VersionHandlerImpl();
            } else if (bukkitVersion.contains("1.21.1") || bukkitVersion.startsWith("1.21")) {
                this.versionHandler = new de.craftingstudiopro.playerDataSyncReloaded.v1_21_R1.VersionHandlerImpl();
            } else if (bukkitVersion.startsWith("1.20")) {
                // Actually, v1_20_R1 might not be implemented yet or has a different name
                // For now we'll assume a standard naming or fallback to 1.21
                try {
                    this.versionHandler = new de.craftingstudiopro.playerDataSyncReloaded.v1_20_R1.VersionHandlerImpl();
                } catch (NoClassDefFoundError e) {
                    this.versionHandler = new de.craftingstudiopro.playerDataSyncReloaded.v1_21_R1.VersionHandlerImpl();
                    getLogger().warning("1.20 implementation not found, falling back to 1.21 handler.");
                }
            } else {
                // Default to 1.21 handler as it's the most stable modern version
                this.versionHandler = new de.craftingstudiopro.playerDataSyncReloaded.v1_21_R1.VersionHandlerImpl();
                getLogger().warning("Unsupported Bukkit version! Using 1.21 fallback. Might have issues.");
            }
            return true;
        } catch (NoClassDefFoundError | Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Version handler setup failed. This is likely due to missing version-specific code in the jar.", e);
            return false;
        }
    }

    private void setupRedis() {
        FileConfiguration config = getConfig();
        if (config.getBoolean("redis.enabled", false)) {
            String host = config.getString("redis.host", "localhost");
            int port = config.getInt("redis.port", 6379);
            String password = config.getString("redis.password", "");
            boolean ssl = config.getBoolean("redis.ssl", false);
            
            this.redisManager = new de.craftingstudiopro.playerDataSyncReloaded.common.redis.RedisManager(getLogger(), host, port, password, ssl);
            try {
                this.redisManager.init();
                this.syncManager.setRedisManager(this.redisManager);
                debug("Redis Pub/Sub subscription initialized.");
                getLogger().info("§aRedis synchronization enabled!");
            } catch (Exception e) {
                getLogger().severe("§cCould not connect to Redis! Synchronization might be delayed.");
                this.redisManager = null;
            }
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
            getLogger().severe("§cCould not connect to the database!");
            getLogger().severe("§cPlease check your credentials in the §fconfig.yml§c.");
            // We only log the detailed error in debug mode or just keep it simple
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
        Bukkit.getConsoleSender().sendMessage("§8 > §fVersion: §d" + version);
        Bukkit.getConsoleSender().sendMessage("§8 > §fAuthors: §b" + authors);
        Bukkit.getConsoleSender().sendMessage("§8 > §fStatus:  §aRunning on " + Bukkit.getServer().getName());
        Bukkit.getConsoleSender().sendMessage("§8 > §fUpdate:  §eVersion 26.5.3 \"Paper Stability Update\"");
        Bukkit.getConsoleSender().sendMessage("§b");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            storage.close();
        }
        if (redisManager != null) {
            redisManager.close();
        }
    }

    private void setupVault() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return;
        org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.economy.Economy> rsp = getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (rsp == null) return;
        this.economy = rsp.getProvider();
        this.syncManager.setEconomy(this.economy);
        getLogger().info("§aVault Economy detected and linked!");
    }

    public void debug(String message) {
        if (getConfig().getBoolean("debug", false)) {
            getLogger().info("§7[DEBUG] " + message);
        }
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }

    private void startAutoSaveTask() {
        FileConfiguration config = getConfig();
        if (!config.getBoolean("autosave.enabled", true)) return;

        long interval = config.getLong("autosave.interval", 300) * 20L; // Convert seconds to ticks

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            debug("Starting auto-save for all players...");
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                // We capture on main thread
                Bukkit.getScheduler().runTask(this, () -> {
                    if (player.isOnline()) {
                        syncManager.handleQuit(player, true); // True = isAutosave
                    }
                });
            }
        }, interval, interval);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        syncManager.handleJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        syncManager.handleQuit(event.getPlayer());
    }

    public void reloadPlugin() {
        onDisable(); // Properly close storage and redis
        reloadConfig();
        
        if (!setupVersionHandler()) {
             getLogger().severe("§cCould not support your server version during reload!");
             return;
        }

        if (!setupStorage()) {
            getLogger().severe("§cFailed to re-initialize storage!");
            return;
        }

        this.syncManager = new SyncManager(this, storage, versionHandler);
        setupVault();
        setupRedis();
        
        getLogger().info("§aPlugin successfully reloaded.");
    }

    public de.craftingstudiopro.playerDataSyncReloaded.common.BackupManager getBackupManager() {
        return backupManager;
    }

    public void startMigration(org.bukkit.command.CommandSender sender) {
        FileConfiguration config = getConfig();
        ConfigurationSection migConfig = config.getConfigurationSection("migration");
        if (migConfig == null) {
            sender.sendMessage("§cMigration target not configured in config.yml.");
            return;
        }

        Storage targetStorage;
        String type = migConfig.getString("type", "mysql").toLowerCase();
        String host = migConfig.getString("host", "localhost");
        int port = migConfig.getInt("port", 3306);
        String database = migConfig.getString("database", "minecraft");
        String user = migConfig.getString("username", "root");
        String password = migConfig.getString("password", "");
        String connectionUrl = migConfig.getString("connection_url", "");
        String encryptionKey = config.getString("security.encryption_key", "");

        if (type.equals("mongodb")) {
            targetStorage = new MongoStorage(getLogger(), connectionUrl, database);
        } else {
            targetStorage = new SqlStorage(getLogger(), type, host, port, database, user, password);
        }
        
        if (targetStorage instanceof MongoStorage) ((MongoStorage) targetStorage).setEncryptionKey(encryptionKey);
        if (targetStorage instanceof SqlStorage) ((SqlStorage) targetStorage).setEncryptionKey(encryptionKey);

        try {
            targetStorage.init();
            Migrator migrator = new Migrator(getLogger(), this.storage, targetStorage);
            migrator.setLegacy(config.getBoolean("migration.legacy", false));
            
            migrator.run().thenRun(() -> {
                sender.sendMessage("§aMigration finished! Please update your §fconfig.yml §ato the new storage and reload.");
                targetStorage.close();
            }).exceptionally(ex -> {
                sender.sendMessage("§cMigration failed: " + ex.getMessage());
                targetStorage.close();
                return null;
            });
        } catch (Exception e) {
            sender.sendMessage("§cCould not connect to the migration target database.");
        }
    }
}

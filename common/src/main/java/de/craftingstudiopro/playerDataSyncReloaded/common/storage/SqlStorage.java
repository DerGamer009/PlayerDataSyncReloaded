package de.craftingstudiopro.playerDataSyncReloaded.common.storage;

import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.common.LegacyMigrator;
import de.craftingstudiopro.playerDataSyncReloaded.common.util.CryptoUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SqlStorage implements Storage {
    private final String dbType; // mysql, mariadb, postgres
    private final String host, database, user, password;
    private final int port;
    private HikariDataSource dataSource;
    private final Logger logger;
    private final Gson gson = new Gson();
    private String encryptionKey = "";
    private ExecutorService dbExecutor;

    public SqlStorage(Logger logger, String type, String host, int port, String database, String user, String password) {
        this.logger = logger;
        this.dbType = type.toLowerCase();
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    public void setEncryptionKey(String key) {
        this.encryptionKey = key;
    }

    @Override
    public void init() {
        HikariConfig config = new HikariConfig();
        
        String jdbcUrl;
        if (dbType.equals("postgres")) {
            jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + database;
            config.setDriverClassName("org.postgresql.Driver");
        } else if (dbType.equals("mariadb")) {
            jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + database;
            config.setDriverClassName("org.mariadb.jdbc.Driver");
        } else {
            jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database;
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        
        if (dbType.contains("mysql")) {
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        dataSource = new HikariDataSource(config);
        dbExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "PlayerDataSync-SqlPool");
            t.setDaemon(true);
            return t;
        });
        createTables();
    }

    private void createTables() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(16) NOT NULL," +
                "data TEXT NOT NULL," +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(createTableSql)) {
                ps.execute();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create database tables", e);
        }
    }

    @Override
    public void close() {
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                dbExecutor.shutdownNow();
            }
        }
        if (dataSource != null) dataSource.close();
    }

    @Override
    public CompletableFuture<Void> save(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            String sql = dbType.equals("postgres") ?
                "INSERT INTO player_data (uuid, name, data, last_updated) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (uuid) DO UPDATE SET name = EXCLUDED.name, data = EXCLUDED.data, last_updated = EXCLUDED.last_updated" :
                "INSERT INTO player_data (uuid, name, data, last_updated) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name), data = VALUES(data), last_updated = VALUES(last_updated)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                String jsonData = gson.toJson(data);
                
                if (encryptionKey != null && encryptionKey.length() >= 16) {
                    try {
                        jsonData = CryptoUtil.encrypt(jsonData, encryptionKey);
                    } catch (Exception e) {
                        logger.severe("CRITICAL: Failed to encrypt personal data for " + data.uuid);
                    }
                }

                ps.setString(1, data.uuid.toString());
                ps.setString(2, data.name);
                ps.setString(3, jsonData);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Could not save player data for " + data.uuid, e);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT data FROM player_data WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String rawData = rs.getString("data");
                        if (encryptionKey != null && encryptionKey.length() >= 16 && !rawData.startsWith("{")) {
                            try {
                                rawData = CryptoUtil.decrypt(rawData, encryptionKey);
                            } catch (Exception e) {
                                logger.severe("CRITICAL: Failed to decrypt personal data for " + uuid);
                                return Optional.empty();
                            }
                        }
                        return Optional.of(gson.fromJson(rawData, PlayerData.class));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Could not load player data for " + uuid, e);
            }
            return Optional.empty();
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> loadLegacy(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM player_data WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        LegacyMigrator mapper = new LegacyMigrator(logger);
                        return Optional.of(mapper.mapSql(rs));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Could not load legacy player data for " + uuid, e);
            }
            return Optional.empty();
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<List<UUID>> getAllStoredUUIDs() {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> uuids = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid FROM player_data")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        uuids.add(UUID.fromString(rs.getString("uuid")));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Could not get all stored UUIDs", e);
            }
            return uuids;
        }, dbExecutor);
    }
}

package de.craftingstudiopro.playerDataSyncReloaded.common.storage;

import com.google.gson.Gson;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;

import de.craftingstudiopro.playerDataSyncReloaded.common.util.CryptoUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
        
        // Performance optimizations for MySQL if applicable
        if (dbType.contains("mysql")) {
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        dataSource = new HikariDataSource(config);
        createTables();
    }

    private void createTables() {
        String createTableSql = "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid VARCHAR(36) PRIMARY KEY," +
                "name VARCHAR(16) NOT NULL," +
                "data TEXT NOT NULL," + // JSON serialized
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
        
        try (Connection conn = dataSource.getConnection()) {
            // Create table if it doesn't exist
            try (PreparedStatement ps = conn.prepareStatement(createTableSql)) {
                ps.execute();
            }
            
            // Check if 'data' column exists (migration for older versions)
            if (!columnExists(conn, "player_data", "data")) {
                logger.info("Migrating database: Adding missing 'data' column to 'player_data' table...");
                String alterTableSql = "ALTER TABLE player_data ADD COLUMN data TEXT NOT NULL AFTER name";
                try (PreparedStatement ps = conn.prepareStatement(alterTableSql)) {
                    ps.execute();
                }
                logger.info("Database migration successful!");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create or update database tables", e);
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    @Override
    public void close() {
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
        });
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
        });
    }
}

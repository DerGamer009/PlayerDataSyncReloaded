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
        String normalized = type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT);
        this.dbType = normalized.equals("postgresql") ? "postgres" : normalized;
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
        if (!dbType.equals("mysql") && !dbType.equals("mariadb") && !dbType.equals("postgres")) {
            // Without this check "sqlite" or a typo silently became a MySQL connection to ":0".
            throw new IllegalStateException("storage.type '" + dbType + "' is not supported. Use mysql, mariadb, postgres or mongodb.");
        }
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
                "data " + dataColumnType() + " NOT NULL," +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(createTableSql)) {
                ps.execute();
            }

            // Check for missing columns (migration from legacy versions)
            checkAndAddColumn(conn, "name", "VARCHAR(16) NOT NULL DEFAULT 'Unknown'");
            checkAndAddColumn(conn, "data", dataColumnType());
            widenDataColumn(conn);
            checkAndAddColumn(conn, "last_updated", "TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
            // Stamp of the stored record (savedAt and the writing node) for saveChecked().
            checkAndAddColumn(conn, "pds_saved_at", "BIGINT NULL");
            checkAndAddColumn(conn, "pds_saved_by", "VARCHAR(36) NULL");

            // Special migration: Handle 'data_json' column found in some older Reloaded versions
            if (columnExists(conn, "data_json")) {
                logger.info("Detected 'data_json' column. Starting migration to new 'data' column...");
                try {
                    // 1. Copy data from data_json to data for all records where data is currently empty
                    String updateSql = "UPDATE player_data SET data = data_json WHERE (data IS NULL OR data = '') AND data_json IS NOT NULL AND data_json != ''";
                    try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                        int updated = ps.executeUpdate();
                        logger.info("Migrated " + updated + " records from 'data_json' to 'data'.");
                    }

                    // 2. Drop the old column so future INSERTS don't fail due to "no default value"
                    // We only do this if we are sure the data column now exists and is populated
                    if (columnExists(conn, "data")) {
                        logger.info("Dropping legacy 'data_json' column...");
                        try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE player_data DROP COLUMN data_json")) {
                            ps.execute();
                            logger.info("Successfully dropped 'data_json' column.");
                        }
                    }
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "Migration of 'data_json' partially failed. The plugin will still try to work, but you should check your database.", e);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Could not create database tables", e);
        }
    }

    /** MySQL/MariaDB TEXT holds 64 KB, which a full inventory with shulker boxes plus advancements exceeds. */
    private String dataColumnType() {
        return dbType.equals("postgres") ? "TEXT" : "LONGTEXT";
    }

    /** Tables created before 26.12-Alpha.3 have a TEXT column on MySQL/MariaDB: widen it once. */
    private void widenDataColumn(Connection conn) {
        if (dbType.equals("postgres")) {
            return;
        }
        String query = "SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'player_data' AND COLUMN_NAME = 'data'";
        try (PreparedStatement ps = conn.prepareStatement(query); ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return;
            }
            String type = rs.getString(1);
            if (type == null || type.equalsIgnoreCase("longtext")) {
                return;
            }
            logger.info("Widening column 'data' from " + type + " to LONGTEXT (large inventories did not fit). "
                    + "This copies the table once and can take a moment on large tables...");
            // MODIFY replaces the whole definition: keep NOT NULL, after filling rows that have none.
            try (PreparedStatement fill = conn.prepareStatement("UPDATE player_data SET data = '' WHERE data IS NULL")) {
                fill.executeUpdate();
            }
            try (PreparedStatement alter = conn.prepareStatement("ALTER TABLE player_data MODIFY data LONGTEXT NOT NULL")) {
                alter.execute();
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not widen column 'data' to LONGTEXT. Saves of large records may fail.", e);
        }
    }

    private void checkAndAddColumn(Connection conn, String column, String definition) {
        if (columnExists(conn, column)) return;

        // Column likely doesn't exist
        logger.info("Adding missing column '" + column + "' to player_data table...");
        String sql = "ALTER TABLE player_data ADD COLUMN " + column + " " + definition;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Could not add column '" + column + "'. This might be okay if it already exists.", ex);
        }
    }

    private boolean columnExists(Connection conn, String column) {
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + column + " FROM player_data LIMIT 0")) {
            ps.executeQuery();
            return true;
        } catch (SQLException e) {
            return false;
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
                "INSERT INTO player_data (uuid, name, data, last_updated, pds_saved_at, pds_saved_by) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, NULL) " +
                "ON CONFLICT (uuid) DO UPDATE SET name = EXCLUDED.name, data = EXCLUDED.data, last_updated = EXCLUDED.last_updated, " +
                "pds_saved_at = EXCLUDED.pds_saved_at, pds_saved_by = EXCLUDED.pds_saved_by" :
                "INSERT INTO player_data (uuid, name, data, last_updated, pds_saved_at, pds_saved_by) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, NULL) " +
                "ON DUPLICATE KEY UPDATE name = VALUES(name), data = VALUES(data), last_updated = VALUES(last_updated), " +
                "pds_saved_at = VALUES(pds_saved_at), pds_saved_by = VALUES(pds_saved_by)";

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, data.uuid.toString());
                ps.setString(2, data.name);
                ps.setString(3, serialize(data));
                ps.setLong(4, data.savedAt);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Could not save player data for " + data.uuid, e);
                throw new IllegalStateException("Could not save player data for " + data.uuid, e);
            }
        }, dbExecutor);
    }

    private String serialize(PlayerData data) {
        String jsonData = gson.toJson(data);
        if (encryptionKey != null && encryptionKey.length() >= 16) {
            try {
                jsonData = CryptoUtil.encrypt(jsonData, encryptionKey);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to encrypt player data for " + data.uuid + "; refusing to store it unencrypted", e);
            }
        }
        return jsonData;
    }

    /**
     * Writes only if the stored row is unstamped, was written by this node, or is still the record
     * this node applied ({@code pds_saved_at == basis}). Otherwise another server stored a newer
     * record after this server loaded the player, and writing would overwrite it with stale data.
     */
    @Override
    public CompletableFuture<Boolean> saveChecked(PlayerData data, long basis, String nodeId) {
        return CompletableFuture.supplyAsync(() -> {
            String update = "UPDATE player_data SET name = ?, data = ?, last_updated = CURRENT_TIMESTAMP, pds_saved_at = ?, pds_saved_by = ? " +
                    "WHERE uuid = ? AND (pds_saved_at IS NULL OR pds_saved_by = ? OR pds_saved_at = ?)";
            String insert = "INSERT INTO player_data (uuid, name, data, last_updated, pds_saved_at, pds_saved_by) VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?, ?)";
            String select = "SELECT pds_saved_at, pds_saved_by FROM player_data WHERE uuid = ?";

            try (Connection conn = dataSource.getConnection()) {
                String json = serialize(data);
                String uuid = data.uuid.toString();

                try (PreparedStatement ps = conn.prepareStatement(update)) {
                    ps.setString(1, data.name);
                    ps.setString(2, json);
                    ps.setLong(3, data.savedAt);
                    ps.setString(4, nodeId);
                    ps.setString(5, uuid);
                    ps.setString(6, nodeId);
                    ps.setLong(7, basis);
                    if (ps.executeUpdate() > 0) {
                        return true;
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(select)) {
                    ps.setString(1, uuid);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            long storedAt = rs.getLong(1);
                            String storedBy = rs.getString(2);
                            // MySQL reports 0 changed rows when nothing differs; that is not a conflict.
                            return nodeId.equals(storedBy) || storedAt == basis || storedAt == data.savedAt;
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setString(1, uuid);
                    ps.setString(2, data.name);
                    ps.setString(3, json);
                    ps.setLong(4, data.savedAt);
                    ps.setString(5, nodeId);
                    ps.executeUpdate();
                    return true;
                } catch (SQLException e) {
                    // Another server inserted the row between our UPDATE and INSERT.
                    if (e.getSQLState() != null && e.getSQLState().startsWith("23")) {
                        return false;
                    }
                    throw e;
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Could not save player data for " + data.uuid, e);
                throw new IllegalStateException("Could not save player data for " + data.uuid, e);
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_data WHERE uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String rawData = null;

                        // Check 'data' column first
                        try {
                            rawData = rs.getString("data");
                        } catch (SQLException ignored) {}

                        // Fallback to 'data_json' if 'data' is empty or didn't exist in the result
                        if (rawData == null || rawData.isEmpty()) {
                            try {
                                rawData = rs.getString("data_json");
                            } catch (SQLException ignored) {}
                        }

                        // 1. If both are empty, it's likely a legacy record (multi-column)
                        if (rawData == null || rawData.isEmpty()) {
                             return loadLegacyInternal(uuid);
                        }

                        // 2. Handle encryption
                        if (encryptionKey != null && encryptionKey.length() >= 16 && !rawData.trim().startsWith("{")) {
                            try {
                                rawData = CryptoUtil.decrypt(rawData, encryptionKey);
                            } catch (Exception e) {
                                // Falling back to the old legacy columns here would apply ancient data
                                // and then save it over the real record: fail the load instead.
                                throw new IllegalStateException("Could not decrypt the record of " + uuid
                                        + ". Is security.encryption_key the same on every server?", e);
                            }
                        }

                        // 3. A record that is still not JSON is encrypted with a key this server does not have.
                        if (rawData == null || !rawData.trim().startsWith("{")) {
                            throw new IllegalStateException("The record of " + uuid
                                    + " is not readable JSON. It is probably encrypted and security.encryption_key is missing or different here.");
                        }

                        return Optional.of(gson.fromJson(rawData, PlayerData.class));
                    }
                }
            } catch (SQLException e) {
                // Reporting "no record" here would make the server keep its local, possibly outdated
                // inventory and later write it over the stored record.
                logger.log(Level.SEVERE, "Could not load player data for " + uuid, e);
                throw new IllegalStateException("Could not load player data for " + uuid, e);
            }
            return Optional.empty();
        }, dbExecutor);
    }

    private Optional<PlayerData> loadLegacyInternal(UUID uuid) {
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
            logger.log(Level.SEVERE, "Could not load legacy player data during fallback for " + uuid, e);
            throw new IllegalStateException("Could not load player data for " + uuid, e);
        }
        return Optional.empty();
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

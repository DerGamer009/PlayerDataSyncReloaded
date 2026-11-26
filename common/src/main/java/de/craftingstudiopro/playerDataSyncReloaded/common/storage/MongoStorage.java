package de.craftingstudiopro.playerDataSyncReloaded.common.storage;

import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.UpdateResult;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.common.LegacyMigrator;
import de.craftingstudiopro.playerDataSyncReloaded.common.util.CryptoUtil;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.mongodb.client.model.Filters.eq;

public class MongoStorage implements Storage {
    private final String connectionUrl, database;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private MongoCollection<Document> collection;
    private final Logger logger;
    private final Gson gson = new Gson();
    private String encryptionKey = "";
    private ExecutorService dbExecutor;

    public MongoStorage(Logger logger, String connectionUrl, String database) {
        this.logger = logger;
        this.connectionUrl = connectionUrl;
        this.database = database;
    }

    public void setEncryptionKey(String key) {
        this.encryptionKey = key;
    }

    @Override
    public void init() {
        mongoClient = MongoClients.create(connectionUrl);
        mongoDatabase = mongoClient.getDatabase(database);
        collection = mongoDatabase.getCollection("player_data");
        dbExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "PlayerDataSync-MongoPool");
            t.setDaemon(true);
            return t;
        });
        logger.info("MongoDB connection (Cloud/URL) established.");
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
        if (mongoClient != null) mongoClient.close();
    }

    @Override
    public CompletableFuture<Void> save(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            Document doc = toDocument(data, null);
            collection.replaceOne(eq("_id", data.uuid.toString()), doc, new ReplaceOptions().upsert(true));
        }, dbExecutor);
    }

    /** The stored document: plain JSON fields, or {@code encrypted_data}; plus the stamp for saveChecked(). */
    private Document toDocument(PlayerData data, String nodeId) {
        String jsonData = gson.toJson(data);
        Document doc;
        if (encryptionKey != null && encryptionKey.length() >= 16) {
            try {
                jsonData = CryptoUtil.encrypt(jsonData, encryptionKey);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to encrypt player data for " + data.uuid + "; refusing to store it unencrypted", e);
            }
            doc = new Document("_id", data.uuid.toString());
            doc.put("encrypted_data", jsonData);
        } else {
            doc = Document.parse(jsonData);
            doc.put("_id", data.uuid.toString());
        }
        doc.put("pds_saved_at", data.savedAt);
        doc.put("pds_saved_by", nodeId);
        return doc;
    }

    /** Same rules as {@link SqlStorage#saveChecked}: write only over an unstamped document, our own, or the one we applied. */
    @Override
    public CompletableFuture<Boolean> saveChecked(PlayerData data, long basis, String nodeId) {
        return CompletableFuture.supplyAsync(() -> {
            String id = data.uuid.toString();
            Document doc = toDocument(data, nodeId);
            UpdateResult result = collection.replaceOne(Filters.and(
                    eq("_id", id),
                    Filters.or(Filters.exists("pds_saved_at", false), eq("pds_saved_by", nodeId), eq("pds_saved_at", basis))
            ), doc);
            if (result.getMatchedCount() > 0) {
                return true;
            }

            Document stored = collection.find(eq("_id", id)).first();
            if (stored != null) {
                Object storedAt = stored.get("pds_saved_at");
                long at = storedAt instanceof Number ? ((Number) storedAt).longValue() : -1L;
                return nodeId.equals(stored.getString("pds_saved_by")) || at == basis || at == data.savedAt;
            }

            try {
                collection.insertOne(doc);
                return true;
            } catch (MongoWriteException e) {
                // Another server inserted the document between our replace and insert.
                if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                    return false;
                }
                throw e;
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> load(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = collection.find(eq("_id", uuid.toString())).first();
            if (doc != null) {
                String jsonData;
                if (doc.containsKey("encrypted_data")) {
                    String encrypted = doc.getString("encrypted_data");
                    try {
                        jsonData = CryptoUtil.decrypt(encrypted, encryptionKey);
                    } catch (Exception e) {
                        // Reporting "no record" would make the server keep its local data and later
                        // overwrite the encrypted record with it.
                        logger.severe("CRITICAL: Failed to decrypt personal data for " + uuid);
                        throw new IllegalStateException("Could not decrypt player data for " + uuid, e);
                    }
                } else {
                    doc.remove("_id");
                    doc.remove("pds_saved_at");
                    doc.remove("pds_saved_by");
                    jsonData = doc.toJson();
                }
                return Optional.of(gson.fromJson(jsonData, PlayerData.class));
            }
            return Optional.empty();
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Optional<PlayerData>> loadLegacy(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            Document doc = collection.find(eq("_id", uuid.toString())).first();
            if (doc != null) {
                LegacyMigrator mapper = new LegacyMigrator(logger);
                return Optional.of(mapper.mapMongo(doc));
            }
            return Optional.empty();
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<List<UUID>> getAllStoredUUIDs() {
        return CompletableFuture.supplyAsync(() -> {
            List<UUID> uuids = new ArrayList<>();
            for (Document doc : collection.find().projection(new Document("_id", 1))) {
                uuids.add(UUID.fromString(doc.getString("_id")));
            }
            return uuids;
        }, dbExecutor);
    }
}

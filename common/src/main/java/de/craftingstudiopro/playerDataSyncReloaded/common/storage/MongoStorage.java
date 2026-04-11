package de.craftingstudiopro.playerDataSyncReloaded.common.storage;

import com.google.gson.Gson;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.common.util.CryptoUtil;
import org.bson.Document;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
        logger.info("MongoDB connection (Cloud/URL) established.");
    }

    @Override
    public void close() {
        if (mongoClient != null) mongoClient.close();
    }

    @Override
    public CompletableFuture<Void> save(PlayerData data) {
        return CompletableFuture.runAsync(() -> {
            String jsonData = gson.toJson(data);
            if (encryptionKey != null && encryptionKey.length() >= 16) {
                try {
                    jsonData = CryptoUtil.encrypt(jsonData, encryptionKey);
                    Document doc = new Document("_id", data.uuid.toString());
                    doc.put("encrypted_data", jsonData);
                    collection.replaceOne(eq("_id", data.uuid.toString()), doc, new ReplaceOptions().upsert(true));
                    return;
                } catch (Exception e) {
                    logger.severe("CRITICAL: Failed to encrypt personal data for " + data.uuid);
                }
            }

            Document doc = Document.parse(jsonData);
            doc.put("_id", data.uuid.toString());
            collection.replaceOne(eq("_id", data.uuid.toString()), doc, new ReplaceOptions().upsert(true));
        });
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
                        logger.severe("CRITICAL: Failed to decrypt personal data for " + uuid);
                        return Optional.empty();
                    }
                } else {
                    doc.remove("_id");
                    jsonData = doc.toJson();
                }
                return Optional.of(gson.fromJson(jsonData, PlayerData.class));
            }
            return Optional.empty();
        });
    }
}

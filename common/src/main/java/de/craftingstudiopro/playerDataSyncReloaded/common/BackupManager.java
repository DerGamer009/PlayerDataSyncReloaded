package de.craftingstudiopro.playerDataSyncReloaded.common;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;
import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BackupManager {
    private final Storage storage;
    private final Logger logger;
    private final File backupDir;
    private final Gson gson = new Gson();

    public BackupManager(Logger logger, Storage storage, File backupDir) {
        this.logger = logger;
        this.storage = storage;
        this.backupDir = backupDir;
        if (!backupDir.exists()) backupDir.mkdirs();
    }

    public CompletableFuture<Void> exportBackup(String name) {
        File file = new File(backupDir, name + ".json.gz");
        logger.info("Starting backup export to: " + file.getName());
        
        return storage.getAllStoredUUIDs().thenCompose(uuids -> {
            List<PlayerData> allData = new ArrayList<>();
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            
            AtomicInteger count = new AtomicInteger(0);
            for (UUID uuid : uuids) {
                chain = chain.thenCompose(v -> storage.load(uuid).thenAccept(opt -> {
                   opt.ifPresent(allData::add);
                   int current = count.incrementAndGet();
                   if (current % 50 == 0 || current == uuids.size()) {
                       logger.info("Export Progress: " + current + "/" + uuids.size());
                   }
                }));
            }
            
            return chain.thenRun(() -> {
                try (OutputStream os = new GZIPOutputStream(new FileOutputStream(file))) {
                    String json = gson.toJson(allData);
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                    logger.info("Successfully exported " + allData.size() + " players to backup.");
                } catch (IOException e) {
                    throw new RuntimeException("Failed to write backup file", e);
                }
            });
        });
    }

    public CompletableFuture<Void> importBackup(String name) {
        File file = new File(backupDir, name + ".json.gz");
        if (!file.exists()) {
            return CompletableFuture.failedFuture(new FileNotFoundException("Backup file not found: " + name));
        }

        return CompletableFuture.runAsync(() -> {
            logger.info("Starting backup import from: " + file.getName());
            try (InputStream is = new GZIPInputStream(new FileInputStream(file))) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                List<PlayerData> dataList = gson.fromJson(reader, new TypeToken<List<PlayerData>>(){}.getType());
                
                logger.info("Found " + dataList.size() + " players in backup. Importing...");
                
                CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
                AtomicInteger count = new AtomicInteger(0);
                for (PlayerData data : dataList) {
                    chain = chain.thenCompose(v -> storage.save(data).thenRun(() -> {
                        int current = count.incrementAndGet();
                        if (current % 50 == 0 || current == dataList.size()) {
                            logger.info("Import Progress: " + current + "/" + dataList.size());
                        }
                    }));
                }
                chain.join(); // Wait for all saves
                logger.info("Successfully imported backup.");
            } catch (IOException e) {
                throw new RuntimeException("Failed to read backup file", e);
            }
        });
    }
}

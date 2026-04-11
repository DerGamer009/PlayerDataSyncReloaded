package de.craftingstudiopro.playerDataSyncReloaded.common;

import de.craftingstudiopro.playerDataSyncReloaded.common.storage.Storage;
import de.craftingstudiopro.playerDataSyncReloaded.api.PlayerData;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class Migrator {
    private final Storage source;
    private final Storage target;
    private final Logger logger;
    private boolean legacy = false;
    private final LegacyMigrator legacyMapper;

    public Migrator(Logger logger, Storage source, Storage target) {
        this.logger = logger;
        this.source = source;
        this.target = target;
        this.legacyMapper = new LegacyMigrator(logger);
    }

    public void setLegacy(boolean legacy) {
        this.legacy = legacy;
    }

    public CompletableFuture<Void> run() {
        logger.info("Starting data migration (Legacy: " + legacy + ")...");
        return source.getAllStoredUUIDs().thenCompose(uuids -> {
            logger.info("Found " + uuids.size() + " players to migrate.");
            AtomicInteger count = new AtomicInteger(0);
            
            CompletableFuture<Void> all = CompletableFuture.completedFuture(null);
            for (UUID uuid : uuids) {
                all = all.thenCompose(v -> migratePlayer(uuid, count, uuids.size()));
            }
            return all;
        }).thenRun(() -> logger.info("Migration completed successfully!"));
    }

    private CompletableFuture<Void> migratePlayer(UUID uuid, AtomicInteger count, int total) {
        CompletableFuture<Optional<PlayerData>> loader = legacy ? source.loadLegacy(uuid) : source.load(uuid);
        
        return loader.thenCompose(optionalData -> {
            if (optionalData.isPresent()) {
                PlayerData data = optionalData.get();
                return target.save(data).thenRun(() -> {
                    int current = count.incrementAndGet();
                    if (current % 10 == 0 || current == total) {
                        logger.info("Migration Progress: " + current + "/" + total + " (" + (current * 100 / total) + "%)");
                    }
                });
            }
            return CompletableFuture.completedFuture(null);
        });
    }
}

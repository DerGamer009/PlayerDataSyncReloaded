package de.craftingstudiopro.playerDataSyncReloaded.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Plain-text output of {@code /pds status} and {@code /pds info} for platforms without Bukkit colors (Fabric). */
public final class CommandText {
    private CommandText() {
    }

    public static List<String> status(SyncManager.SyncStats stats, String storageType) {
        List<String> lines = new ArrayList<>();
        lines.add("PlayerDataSync - Runtime Status");
        lines.add("Storage: " + storageType);
        lines.add("Active syncs: " + stats.activeSyncs);
        lines.add("Loads: " + stats.loadAttempts + "/" + stats.loadSuccess + "/" + stats.loadFailed + " (attempt/success/fail)");
        lines.add("Saves: " + stats.saveAttempts + "/" + stats.saveSuccess + "/" + stats.saveFailed + " (attempt/success/fail)");
        lines.add("Skipped: world=" + stats.skippedByWorld + " | cooldown=" + stats.skippedByCooldown + " | hash=" + stats.skippedByHash);
        lines.add("Newer record elsewhere: refused saves=" + stats.refusedStale + " | reloaded after join=" + stats.outdatedReloads);
        lines.add("Last durations: load=" + duration(stats.lastLoadDurationMs) + " | save=" + duration(stats.lastSaveDurationMs));
        if (stats.lastErrorAtEpochMs > 0L && stats.lastErrorMessage != null && !stats.lastErrorMessage.isBlank()) {
            lines.add("Last error (" + time(stats.lastErrorAtEpochMs, "yyyy-MM-dd HH:mm:ss") + "): " + stats.lastErrorMessage);
        }
        return lines;
    }

    public static List<String> info(String playerName, SyncManager.PlayerSyncState state) {
        List<String> lines = new ArrayList<>();
        lines.add("Sync state for " + playerName);
        lines.add("Data applied: " + (state.loaded ? "yes" : "no (saves are refused)")
                + " | Load in progress: " + (state.syncInProgress ? "yes" : "no")
                + " | Reload queued: " + (state.reloadQueued ? "yes" : "no"));
        lines.add("Applied record saved at: " + time(state.appliedSavedAt, "yyyy-MM-dd HH:mm:ss.SSS")
                + " | Last save here: " + time(state.lastSavedAt, "yyyy-MM-dd HH:mm:ss.SSS"));
        lines.add("Applied record saved by: " + (state.appliedSavedBy.isEmpty() ? "unknown" : state.appliedSavedBy)
                + " | Refused saves since join: " + state.refusedSaves);
        return lines;
    }

    private static String duration(long ms) {
        return ms < 0 ? "n/a" : ms + "ms";
    }

    private static String time(long epochMs, String pattern) {
        if (epochMs <= 0L) return "never";
        return DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
    }
}

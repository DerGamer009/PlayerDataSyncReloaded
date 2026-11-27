package de.craftingstudiopro.playerDataSyncReloaded.plugin.command;

import de.craftingstudiopro.playerDataSyncReloaded.PlayerDataSyncReloaded;
import de.craftingstudiopro.playerDataSyncReloaded.common.SyncManager;
import de.craftingstudiopro.playerDataSyncReloaded.plugin.BukkitPDSPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PDSCommand implements CommandExecutor, TabCompleter {
    private final PlayerDataSyncReloaded plugin;

    public PDSCommand(PlayerDataSyncReloaded plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("playerdatasync.admin")) {
            sender.sendMessage("\u00A7cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage("\u00A7aConfiguration and connections reloaded.");
                return true;
            case "save":
                if (args.length < 2) {
                    sender.sendMessage("\u00A7cUsage: /pds save <player>");
                    return true;
                }
                org.bukkit.entity.Player saveTarget = Bukkit.getPlayer(args[1]);
                if (saveTarget == null) {
                    sender.sendMessage("\u00A7cPlayer not found.");
                    return true;
                }
                if (!plugin.getSyncManager().isLoaded(saveTarget.getUniqueId())) {
                    sender.sendMessage("\u00A7cNot saved: the stored data of " + saveTarget.getName()
                            + " has not been applied on this server yet (see /pds info).");
                    return true;
                }
                String savedName = saveTarget.getName();
                sender.sendMessage("\u00A77Saving data for " + savedName + "...");
                plugin.getSyncManager().handleQuit(new BukkitPDSPlayer(saveTarget)).whenComplete((unused, ex) ->
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(ex == null
                                ? "\u00A7aSaved data for " + savedName + "."
                                : "\u00A7cCould not save data for " + savedName + ": " + ex.getMessage())));
                return true;
            case "load":
                if (args.length < 2) {
                    sender.sendMessage("\u00A7cUsage: /pds load <player>");
                    return true;
                }
                org.bukkit.entity.Player loadTarget = Bukkit.getPlayer(args[1]);
                if (loadTarget == null) {
                    sender.sendMessage("\u00A7cPlayer not found.");
                    return true;
                }
                plugin.getSyncManager().handleJoin(new BukkitPDSPlayer(loadTarget), true);
                sender.sendMessage("\u00A7aManually loading data for " + loadTarget.getName());
                return true;
            case "saveall":
                int online = 0;
                for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                    // Not the autosave path: no cooldown, and other servers are notified over Redis.
                    plugin.getSyncManager().handleQuit(new BukkitPDSPlayer(player), false);
                    online++;
                }
                sender.sendMessage("\u00A7aTriggered save for " + online + " online player(s).");
                return true;
            case "status":
                sendStatus(sender);
                return true;
            case "info":
                if (args.length < 2) {
                    sender.sendMessage("\u00A7cUsage: /pds info <player>");
                    return true;
                }
                org.bukkit.entity.Player infoTarget = Bukkit.getPlayer(args[1]);
                if (infoTarget == null) {
                    sender.sendMessage("\u00A7cPlayer not found.");
                    return true;
                }
                sendInfo(sender, infoTarget);
                return true;
            case "debug":
                return handleDebug(sender, args);
            case "backup":
                return handleBackup(sender, args);
            case "migrate":
                sender.sendMessage("\u00A7eStarting migration process...");
                plugin.startMigration(sender);
                return true;
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleBackup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("\u00A7cUsage: /pds backup <export|import|list> [name]");
            return true;
        }

        String action = args[1].toLowerCase();
        if (action.equals("list")) {
            List<String> backups = plugin.getBackupManager().listBackups();
            if (backups.isEmpty()) {
                sender.sendMessage("\u00A77No backups found in " + plugin.getBackupManager().getBackupDirectory().getName() + ".");
                return true;
            }
            sender.sendMessage("\u00A7bBackups (" + backups.size() + "): \u00A7f" + String.join(", ", backups));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage("\u00A7cUsage: /pds backup " + action + " <name>");
            return true;
        }

        String name = args[2];
        if (action.equals("export")) {
            sender.sendMessage("\u00A7eStarting backup export: " + name);
            plugin.getBackupManager().exportBackup(name).whenComplete((unused, throwable) -> {
                if (throwable == null) {
                    sender.sendMessage("\u00A7aBackup exported successfully.");
                } else {
                    sender.sendMessage("\u00A7cBackup export failed: " + throwable.getMessage());
                }
            });
            return true;
        }

        if (action.equals("import")) {
            sender.sendMessage("\u00A7eStarting backup import: " + name);
            plugin.getBackupManager().importBackup(name).whenComplete((unused, throwable) -> {
                if (throwable == null) {
                    sender.sendMessage("\u00A7aBackup imported successfully.");
                } else {
                    sender.sendMessage("\u00A7cBackup import failed: " + throwable.getMessage());
                }
            });
            return true;
        }

        sender.sendMessage("\u00A7cUnknown backup action: " + action);
        return true;
    }

    private boolean handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("\u00A77Debug mode is currently: " + (plugin.getConfig().getBoolean("debug", false) ? "\u00A7aON" : "\u00A7cOFF"));
            sender.sendMessage("\u00A7cUsage: /pds debug <on|off|toggle>");
            return true;
        }

        String mode = args[1].toLowerCase();
        boolean current = plugin.getConfig().getBoolean("debug", false);
        boolean target;
        if (mode.equals("on")) {
            target = true;
        } else if (mode.equals("off")) {
            target = false;
        } else if (mode.equals("toggle")) {
            target = !current;
        } else {
            sender.sendMessage("\u00A7cUsage: /pds debug <on|off|toggle>");
            return true;
        }

        plugin.setDebugMode(target);
        sender.sendMessage("\u00A7aDebug mode is now " + (target ? "\u00A7aON" : "\u00A7cOFF") + "\u00A7a.");
        return true;
    }

    private void sendStatus(CommandSender sender) {
        SyncManager.SyncStats stats = plugin.getSyncManager().getStatsSnapshot();
        sender.sendMessage("\u00A7bPlayerDataSync Reloaded \u00A77- Runtime Status");
        sender.sendMessage("\u00A77Storage: \u00A7f" + plugin.getStorageType());
        sender.sendMessage("\u00A77Redis: " + (plugin.isRedisEnabled() ? "\u00A7aenabled" : "\u00A7cdisabled")
                + " \u00A78| \u00A77Discord: " + (plugin.isDiscordEnabled() ? "\u00A7aenabled" : "\u00A7cdisabled")
                + " \u00A78| \u00A77Autosave: " + (plugin.isAutosaveEnabled() ? "\u00A7aenabled" : "\u00A7cdisabled"));
        sender.sendMessage("\u00A77Active syncs: \u00A7f" + stats.activeSyncs);
        sender.sendMessage("\u00A77Loads: \u00A7f" + stats.loadAttempts + "\u00A78/\u00A7a" + stats.loadSuccess + "\u00A78/\u00A7c" + stats.loadFailed
                + " \u00A78(attempt/success/fail)");
        sender.sendMessage("\u00A77Saves: \u00A7f" + stats.saveAttempts + "\u00A78/\u00A7a" + stats.saveSuccess + "\u00A78/\u00A7c" + stats.saveFailed
                + " \u00A78(attempt/success/fail)");
        sender.sendMessage("\u00A77Skipped: \u00A7fworld=" + stats.skippedByWorld + " \u00A78| \u00A7fcooldown=" + stats.skippedByCooldown
                + " \u00A78| \u00A7fhash=" + stats.skippedByHash);
        sender.sendMessage("\u00A77Newer record elsewhere: \u00A7frefused saves=" + (stats.refusedStale > 0 ? "\u00A7e" : "") + stats.refusedStale
                + " \u00A78| \u00A7freloaded after join=" + (stats.outdatedReloads > 0 ? "\u00A7e" : "") + stats.outdatedReloads);
        sender.sendMessage("\u00A77Last durations: \u00A7fload=" + formatDuration(stats.lastLoadDurationMs)
                + " \u00A78| \u00A7fsave=" + formatDuration(stats.lastSaveDurationMs));
        sender.sendMessage("\u00A77Excluded worlds: \u00A7f" + (stats.excludedWorlds.isEmpty() ? "-" : String.join(", ", stats.excludedWorlds)));

        if (stats.lastErrorAtEpochMs > 0L && stats.lastErrorMessage != null && !stats.lastErrorMessage.isBlank()) {
            String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(Instant.ofEpochMilli(stats.lastErrorAtEpochMs).atZone(ZoneId.systemDefault()));
            sender.sendMessage("\u00A77Last error (" + time + "): \u00A7c" + stats.lastErrorMessage);
        }
    }

    private void sendInfo(CommandSender sender, org.bukkit.entity.Player target) {
        SyncManager.PlayerSyncState state = plugin.getSyncManager().getPlayerState(target.getUniqueId());
        sender.sendMessage("\u00A7bSync state for \u00A7f" + target.getName());
        sender.sendMessage("\u00A77Data applied: " + (state.loaded ? "\u00A7ayes" : "\u00A7cno (saves are refused)")
                + " \u00A78| \u00A77Load in progress: " + (state.syncInProgress ? "\u00A7eyes" : "\u00A7fno")
                + " \u00A78| \u00A77Reload queued: " + (state.reloadQueued ? "\u00A7eyes" : "\u00A7fno"));
        sender.sendMessage("\u00A77Applied record saved at: \u00A7f" + formatTime(state.appliedSavedAt)
                + " \u00A78| \u00A77Last save here: \u00A7f" + formatTime(state.lastSavedAt));
        sender.sendMessage("\u00A77Applied record saved by: \u00A7f" + (state.appliedSavedBy.isEmpty() ? "unknown" : state.appliedSavedBy)
                + " \u00A78| \u00A77Refused saves since join: " + (state.refusedSaves > 0 ? "\u00A7e" : "\u00A7f") + state.refusedSaves);
    }

    private String formatTime(long epochMs) {
        if (epochMs <= 0L) return "never";
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()));
    }

    private String formatDuration(long durationMs) {
        if (durationMs < 0) return "n/a";
        return durationMs + "ms";
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("\u00A7bPlayerDataSyncReloaded \u00A77- Admin Interface");
        sender.sendMessage("\u00A77/pds status \u00A7f- Show runtime status and metrics");
        sender.sendMessage("§7/pds info <player> §f- Show a player's sync state");
        sender.sendMessage("\u00A77/pds reload \u00A7f- Reload configuration");
        sender.sendMessage("\u00A77/pds save <player> \u00A7f- Manually save player data");
        sender.sendMessage("\u00A77/pds load <player> \u00A7f- Manually load player data");
        sender.sendMessage("\u00A77/pds saveall \u00A7f- Trigger save for all online players");
        sender.sendMessage("\u00A77/pds debug <on|off|toggle> \u00A7f- Change debug mode live");
        sender.sendMessage("\u00A77/pds backup list \u00A7f- List all backups");
        sender.sendMessage("\u00A77/pds backup export <name> \u00A7f- Export a data backup");
        sender.sendMessage("\u00A77/pds backup import <name> \u00A7f- Import a data backup");
        sender.sendMessage("\u00A77/pds migrate \u00A7f- Start migration to target backend");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("status", "info", "reload", "save", "load", "saveall", "debug", "migrate", "backup").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("backup")) {
            return Arrays.asList("export", "import", "list").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            return Arrays.asList("on", "off", "toggle").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("load") || args[0].equalsIgnoreCase("info"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new java.util.ArrayList<>();
    }
}

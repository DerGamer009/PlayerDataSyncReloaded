package de.craftingstudiopro.playerDataSyncReloaded.plugin.command;

import de.craftingstudiopro.playerDataSyncReloaded.PlayerDataSyncReloaded;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

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
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§bPlayerDataSyncReloaded §7- Admin Interface");
            sender.sendMessage("§7/pds reload §f- Reload configuration");
            sender.sendMessage("§7/pds save <player> §f- Manually save player data");
            sender.sendMessage("§7/pds load <player> §f- Manually load player data");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage("§aConfiguration reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("save") && args.length > 1) {
            org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(args[1]);
            if (target != null) {
                plugin.getSyncManager().handleQuit(target);
                sender.sendMessage("§aManually saved data for " + target.getName());
            } else {
                sender.sendMessage("§cPlayer not found.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("load") && args.length > 1) {
            org.bukkit.entity.Player target = org.bukkit.Bukkit.getPlayer(args[1]);
            if (target != null) {
                plugin.getSyncManager().handleJoin(target);
                sender.sendMessage("§aManually loading data for " + target.getName());
            } else {
                sender.sendMessage("§cPlayer not found.");
            }
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "save", "load").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }
}

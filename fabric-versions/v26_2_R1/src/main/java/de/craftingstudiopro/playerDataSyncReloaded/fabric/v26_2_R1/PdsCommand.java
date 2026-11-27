package de.craftingstudiopro.playerDataSyncReloaded.fabric.v26_2_R1;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.craftingstudiopro.playerDataSyncReloaded.common.CommandText;
import de.craftingstudiopro.playerDataSyncReloaded.common.SyncManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.function.Supplier;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** {@code /pds} (alias {@code /playerdatasync}) on Fabric: status, info, save, load, saveall. Operators only. */
final class PdsCommand {
    private PdsCommand() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, Supplier<SyncManager> sync, Supplier<String> storageType) {
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(literal("pds")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("status").executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    send(ctx, CommandText.status(manager.getStatsSnapshot(), storageType.get()));
                    return 1;
                }))
                .then(literal("info").then(argument("player", EntityArgument.player()).executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    send(ctx, CommandText.info(player.getName().getString(), manager.getPlayerState(player.getUUID())));
                    return 1;
                })))
                .then(literal("save").then(argument("player", EntityArgument.player()).executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    String name = player.getName().getString();
                    if (!manager.isLoaded(player.getUUID())) {
                        send(ctx, List.of("Not saved: the stored data of " + name + " has not been applied on this server yet (see /pds info)."));
                        return 0;
                    }
                    send(ctx, List.of("Saving data for " + name + "..."));
                    var source = ctx.getSource();
                    manager.handleQuit(new FabricPDSPlayer(player)).whenComplete((unused, ex) -> source.getServer().execute(() ->
                            sendTo(source, ex == null ? "Saved data for " + name + "." : "Could not save data for " + name + ": " + ex.getMessage())));
                    return 1;
                })))
                .then(literal("load").then(argument("player", EntityArgument.player()).executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
                    manager.handleJoin(new FabricPDSPlayer(player), true);
                    send(ctx, List.of("Loading data for " + player.getName().getString()));
                    return 1;
                })))
                .then(literal("saveall").executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    int count = 0;
                    for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                        manager.handleQuit(new FabricPDSPlayer(player), false);
                        count++;
                    }
                    send(ctx, List.of("Triggered save for " + count + " online player(s)."));
                    return 1;
                })));
        dispatcher.register(literal("playerdatasync").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).redirect(node));
    }

    private static SyncManager manager(CommandContext<CommandSourceStack> ctx, Supplier<SyncManager> sync) {
        SyncManager manager = sync.get();
        if (manager == null) {
            ctx.getSource().sendFailure(Component.literal("PlayerDataSync is not initialized. Check the storage settings and the server log."));
        }
        return manager;
    }

    private static void sendTo(CommandSourceStack source, String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }

    private static void send(CommandContext<CommandSourceStack> ctx, List<String> lines) {
        for (String line : lines) {
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
    }
}

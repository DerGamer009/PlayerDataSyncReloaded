package de.craftingstudiopro.playerDataSyncReloaded.fabric.v1_20_R1;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.craftingstudiopro.playerDataSyncReloaded.common.CommandText;
import de.craftingstudiopro.playerDataSyncReloaded.common.SyncManager;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Supplier;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** {@code /pds} (alias {@code /playerdatasync}) on Fabric: status, info, save, load, saveall. Operators only. */
final class PdsCommand {
    private PdsCommand() {
    }

    static void register(CommandDispatcher<ServerCommandSource> dispatcher, Supplier<SyncManager> sync, Supplier<String> storageType) {
        LiteralCommandNode<ServerCommandSource> node = dispatcher.register(literal("pds")
                .requires(source -> source.hasPermissionLevel(2))
                .then(literal("status").executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    send(ctx, CommandText.status(manager.getStatsSnapshot(), storageType.get()));
                    return 1;
                }))
                .then(literal("info").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                    send(ctx, CommandText.info(player.getName().getString(), manager.getPlayerState(player.getUuid())));
                    return 1;
                })))
                .then(literal("save").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                    manager.handleQuit(new FabricPDSPlayer(player));
                    send(ctx, List.of("Saving data for " + player.getName().getString()));
                    return 1;
                })))
                .then(literal("load").then(argument("player", EntityArgumentType.player()).executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    ServerPlayerEntity player = EntityArgumentType.getPlayer(ctx, "player");
                    manager.handleJoin(new FabricPDSPlayer(player), true);
                    send(ctx, List.of("Loading data for " + player.getName().getString()));
                    return 1;
                })))
                .then(literal("saveall").executes(ctx -> {
                    SyncManager manager = manager(ctx, sync);
                    if (manager == null) return 0;
                    int count = 0;
                    for (ServerPlayerEntity player : ctx.getSource().getServer().getPlayerManager().getPlayerList()) {
                        manager.handleQuit(new FabricPDSPlayer(player), true);
                        count++;
                    }
                    send(ctx, List.of("Triggered save for " + count + " online player(s)."));
                    return 1;
                })));
        dispatcher.register(literal("playerdatasync").requires(source -> source.hasPermissionLevel(2)).redirect(node));
    }

    private static SyncManager manager(CommandContext<ServerCommandSource> ctx, Supplier<SyncManager> sync) {
        SyncManager manager = sync.get();
        if (manager == null) {
            ctx.getSource().sendError(Text.literal("PlayerDataSync is not initialized. Check the storage settings and the server log."));
        }
        return manager;
    }

    private static void send(CommandContext<ServerCommandSource> ctx, List<String> lines) {
        for (String line : lines) {
            ctx.getSource().sendFeedback(() -> Text.literal(line), false);
        }
    }
}

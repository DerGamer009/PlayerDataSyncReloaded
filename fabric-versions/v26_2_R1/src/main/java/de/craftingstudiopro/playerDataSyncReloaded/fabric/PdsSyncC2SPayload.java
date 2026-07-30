package de.craftingstudiopro.playerDataSyncReloaded.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client-to-server sync control payload (MC 26.x + yarn 1.21.11 mappings — mojmaps-aligned).
 */
public record PdsSyncC2SPayload(String message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PdsSyncC2SPayload> PACKET_ID =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("pds", "sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PdsSyncC2SPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PdsSyncC2SPayload::message,
            PdsSyncC2SPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

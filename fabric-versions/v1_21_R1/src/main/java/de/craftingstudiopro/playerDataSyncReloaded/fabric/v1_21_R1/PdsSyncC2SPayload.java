package de.craftingstudiopro.playerDataSyncReloaded.fabric.v1_21_R1;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client-to-server sync control (Fabric 1.21+ {@link CustomPayload} API).
 */
public record PdsSyncC2SPayload(String message) implements CustomPayload {
    public static final CustomPayload.Id<PdsSyncC2SPayload> PACKET_ID =
        new CustomPayload.Id<>(Identifier.of("pds", "sync"));
    public static final PacketCodec<RegistryByteBuf, PdsSyncC2SPayload> CODEC =
        PacketCodec.tuple(PacketCodecs.STRING, PdsSyncC2SPayload::message, PdsSyncC2SPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}

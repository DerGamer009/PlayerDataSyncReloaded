package de.craftingstudiopro.playerDataSyncReloaded.fabric.v26_2_R1;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * The {@code pds:sync} channel in both directions (MC 26.x, mojmap names).
 * Velocity and Paper send the message as raw UTF-8 bytes without a length prefix, so the codec does the same.
 */
public record PdsSyncPayload(String message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PdsSyncPayload> PACKET_ID =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("pds", "sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PdsSyncPayload> CODEC = StreamCodec.ofMember(
        (payload, buf) -> buf.writeBytes(payload.message().getBytes(StandardCharsets.UTF_8)),
        buf -> {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new PdsSyncPayload(new String(bytes, StandardCharsets.UTF_8));
        });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }
}

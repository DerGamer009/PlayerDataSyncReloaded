package de.craftingstudiopro.playerDataSyncReloaded.fabric.v1_21_R1;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/**
 * The {@code pds:sync} channel in both directions (Fabric 1.21+ {@link CustomPayload} API).
 * Velocity and Paper send the message as raw UTF-8 bytes without a length prefix, so the codec does the same.
 */
public record PdsSyncPayload(String message) implements CustomPayload {
    public static final CustomPayload.Id<PdsSyncPayload> PACKET_ID =
        new CustomPayload.Id<>(Identifier.of("pds", "sync"));
    public static final PacketCodec<RegistryByteBuf, PdsSyncPayload> CODEC = PacketCodec.of(
        (payload, buf) -> buf.writeBytes(payload.message().getBytes(StandardCharsets.UTF_8)),
        buf -> {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new PdsSyncPayload(new String(bytes, StandardCharsets.UTF_8));
        });

    @Override
    public Id<? extends CustomPayload> getId() {
        return PACKET_ID;
    }
}

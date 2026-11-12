package net.aerborne.client.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The round-trip diagnostics channel. The server sends a {@link Ping} after each join and the client answers with a
 * {@link Pong}; both carry an opaque blob the {@link RttService} fills in and reads. Kept deliberately plain: the
 * channel reads as a latency probe and the payloads carry no structure of their own.
 */
final class RttPayloads {
    static final String CHANNEL = "aerborne:rtt";
    private static final int MAX = 8192;

    private RttPayloads() { }

    record Ping(byte[] data) implements CustomPacketPayload {
        static final Type<Ping> TYPE = new Type<>(Identifier.parse(CHANNEL));
        static final StreamCodec<RegistryFriendlyByteBuf, Ping> CODEC = new StreamCodec<>() {
            @Override public Ping decode(RegistryFriendlyByteBuf buffer) {
                int length = buffer.readableBytes();
                if (length > MAX) throw new IllegalArgumentException("oversized");
                byte[] data = new byte[length];
                buffer.readBytes(data);
                return new Ping(data);
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Ping payload) { buffer.writeBytes(payload.data()); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    record Pong(byte[] data) implements CustomPacketPayload {
        static final Type<Pong> TYPE = new Type<>(Identifier.parse(CHANNEL));
        static final StreamCodec<RegistryFriendlyByteBuf, Pong> CODEC = new StreamCodec<>() {
            @Override public Pong decode(RegistryFriendlyByteBuf buffer) {
                int length = buffer.readableBytes();
                if (length > MAX) throw new IllegalArgumentException("oversized");
                byte[] data = new byte[length];
                buffer.readBytes(data);
                return new Pong(data);
            }
            @Override public void encode(RegistryFriendlyByteBuf buffer, Pong payload) { buffer.writeBytes(payload.data()); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}

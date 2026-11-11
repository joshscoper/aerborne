package net.aerborne.client.fabric;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Paper world UUID prevents overworld-based island waypoints leaking into another island. */
public record WorldPayload(UUID world) implements CustomPacketPayload {
    public static final Type<WorldPayload> TYPE = new Type<>(Identifier.parse("aerborne:world"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldPayload> CODEC = new StreamCodec<>() {
        @Override public WorldPayload decode(RegistryFriendlyByteBuf b) {
            if (b.readableBytes() != 16) throw new IllegalArgumentException("Invalid world payload");
            return new WorldPayload(new UUID(b.readLong(), b.readLong()));
        }
        @Override public void encode(RegistryFriendlyByteBuf b, WorldPayload p) {
            b.writeLong(p.world().getMostSignificantBits()); b.writeLong(p.world().getLeastSignificantBits());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

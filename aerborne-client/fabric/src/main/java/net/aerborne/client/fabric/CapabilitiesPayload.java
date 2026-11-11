package net.aerborne.client.fabric;

import net.aerborne.client.protocol.Capabilities;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record CapabilitiesPayload(Capabilities capabilities) implements CustomPacketPayload {
    public static final Type<CapabilitiesPayload> TYPE = new Type<>(Identifier.parse(Capabilities.CHANNEL));
    public static final StreamCodec<RegistryFriendlyByteBuf, CapabilitiesPayload> CODEC = new StreamCodec<>() {
        @Override public CapabilitiesPayload decode(RegistryFriendlyByteBuf buffer) {
            if (buffer.readableBytes() != Capabilities.WIRE_BYTES) {
                throw new IllegalArgumentException("Invalid Aerborne capability payload size");
            }
            return new CapabilitiesPayload(new Capabilities(buffer.readInt(), buffer.readInt()));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, CapabilitiesPayload payload) {
            buffer.writeBytes(payload.capabilities().encode());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

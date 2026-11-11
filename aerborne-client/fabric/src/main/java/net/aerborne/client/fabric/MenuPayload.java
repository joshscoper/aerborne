package net.aerborne.client.fabric;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Sent by NerdSMP's ClientFeeds as one of its chest menus opens, version 1: the menu's kind and plain title. */
public record MenuPayload(String kind, String title) implements CustomPacketPayload {
    public static final Type<MenuPayload> TYPE = new Type<>(Identifier.parse("aerborne:menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MenuPayload> CODEC = new StreamCodec<>() {
        @Override public MenuPayload decode(RegistryFriendlyByteBuf b) {
            int version = b.readInt();
            if (version != 1) throw new IllegalArgumentException("Unsupported menu payload " + version);
            return new MenuPayload(b.readUtf(256), b.readUtf(1024));
        }
        @Override public void encode(RegistryFriendlyByteBuf b, MenuPayload p) { b.writeInt(1); b.writeUtf(p.kind); b.writeUtf(p.title); }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

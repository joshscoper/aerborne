package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Status effects on the creatures the server labels (see PlatesPayload), from NerdSMP's ClientFeeds, version 1. The
 * client only sees other creatures' effect particles, so the server says which effects they carry and for how long.
 */
public record EffectsPayload(List<Entry> entries) implements CustomPacketPayload {
    /** {@code ticks} is the time left when sent, or -1 for an effect with no end. */
    public record Effect(String key, int amplifier, int ticks) { }
    public record Entry(int entityId, List<Effect> effects) { }

    public static final Type<EffectsPayload> TYPE = new Type<>(Identifier.parse("aerborne:effects"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EffectsPayload> CODEC = new StreamCodec<>() {
        @Override public EffectsPayload decode(RegistryFriendlyByteBuf b) {
            int version = b.readInt();
            if (version != 1) throw new IllegalArgumentException("Unsupported effects payload " + version);
            int count = b.readInt();
            if (count < 0 || count > 128) throw new IllegalArgumentException("Too many creatures with effects");
            List<Entry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int id = b.readInt(), effects = b.readUnsignedByte();
                if (effects > 32) throw new IllegalArgumentException("Too many effects");
                List<Effect> list = new ArrayList<>(effects);
                for (int e = 0; e < effects; e++) list.add(new Effect(b.readUtf(128), b.readUnsignedByte(), b.readInt()));
                entries.add(new Entry(id, List.copyOf(list)));
            }
            return new EffectsPayload(List.copyOf(entries));
        }
        @Override public void encode(RegistryFriendlyByteBuf b, EffectsPayload p) {
            b.writeInt(1); b.writeInt(p.entries.size());
            for (Entry entry : p.entries) {
                b.writeInt(entry.entityId); b.writeByte(entry.effects.size());
                for (Effect e : entry.effects) { b.writeUtf(e.key); b.writeByte(e.amplifier); b.writeInt(e.ticks); }
            }
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

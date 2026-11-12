package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The creatures to draw nameplates over, from NerdSMP's ClientFeeds, version 1. Health is read from the entity. */
public record PlatesPayload(List<Plate> plates) implements CustomPacketPayload {
    /** {@code flags}: 1 boss, 2 elite, 4 townsperson (NPC). The name keeps section-sign colours. */
    public record Plate(int entityId, String name, int flags) {
        boolean boss() { return (flags & 1) != 0; }
        boolean elite() { return (flags & 2) != 0; }
        /** An NPC: named in the target frame, but no health plate drawn over it (it wears its own name). */
        boolean npc() { return (flags & 4) != 0; }
    }

    public static final Type<PlatesPayload> TYPE = new Type<>(Identifier.parse("aerborne:plates"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlatesPayload> CODEC = new StreamCodec<>() {
        @Override public PlatesPayload decode(RegistryFriendlyByteBuf b) {
            int version = b.readInt();
            if (version != 1) throw new IllegalArgumentException("Unsupported nameplate payload " + version);
            int count = b.readInt();
            if (count < 0 || count > 128) throw new IllegalArgumentException("Too many nameplates");
            List<Plate> plates = new ArrayList<>(count);
            for (int i = 0; i < count; i++) plates.add(new Plate(b.readInt(), b.readUtf(2048), b.readByte()));
            return new PlatesPayload(List.copyOf(plates));
        }
        @Override public void encode(RegistryFriendlyByteBuf b, PlatesPayload p) {
            b.writeInt(1); b.writeInt(p.plates.size());
            for (Plate plate : p.plates) { b.writeInt(plate.entityId); b.writeUtf(plate.name); b.writeByte(plate.flags); }
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

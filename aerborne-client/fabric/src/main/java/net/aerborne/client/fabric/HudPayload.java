package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The player's real stats from the server (NerdSMP's ClientHudFeed), version 1. Health is the true value, not the
 * twenty the vanilla heart row is scaled to. Version 2 adds the skill experience toast. Text keeps the server's colours as section-sign codes.
 */
public record HudPayload(float health, float maxHealth, float absorption, float defense, boolean recoveryHeld, String food,
                         int level, long experience, long experienceNeeded, String rank, long marks,
                         String progressTitle, String progressLine, String overlayTitle, List<String> overlay,
                         List<String> appendix, String notice, String skill, float skillProgress) implements CustomPacketPayload {
    public static final Type<HudPayload> TYPE = new Type<>(Identifier.parse("aerborne:hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> CODEC = new StreamCodec<>() {
        @Override public HudPayload decode(RegistryFriendlyByteBuf b) {
            int version = b.readInt();
            if (version != 1 && version != 2) throw new IllegalArgumentException("Unsupported HUD payload " + version);
            return new HudPayload(b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readUtf(),
                    b.readInt(), b.readLong(), b.readLong(), b.readUtf(), b.readLong(), b.readUtf(), b.readUtf(),
                    b.readUtf(), lines(b), lines(b), b.readUtf(), version >= 2 ? b.readUtf() : "", version >= 2 ? b.readFloat() : 0F);
        }
        @Override public void encode(RegistryFriendlyByteBuf b, HudPayload p) {
            b.writeInt(2); b.writeFloat(p.health); b.writeFloat(p.maxHealth); b.writeFloat(p.absorption); b.writeFloat(p.defense);
            b.writeBoolean(p.recoveryHeld); b.writeUtf(p.food); b.writeInt(p.level); b.writeLong(p.experience);
            b.writeLong(p.experienceNeeded); b.writeUtf(p.rank); b.writeLong(p.marks); b.writeUtf(p.progressTitle);
            b.writeUtf(p.progressLine); b.writeUtf(p.overlayTitle); write(b, p.overlay); write(b, p.appendix); b.writeUtf(p.notice);
            b.writeUtf(p.skill); b.writeFloat(p.skillProgress);
        }
    };
    private static List<String> lines(RegistryFriendlyByteBuf b) {
        int n = b.readInt();
        if (n < 0 || n > 16) throw new IllegalArgumentException("Too many HUD lines");
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(b.readUtf());
        return List.copyOf(out);
    }
    private static void write(RegistryFriendlyByteBuf b, List<String> lines) { b.writeInt(lines.size()); lines.forEach(b::writeUtf); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

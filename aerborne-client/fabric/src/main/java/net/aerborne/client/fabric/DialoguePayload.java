package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * One page of an NPC conversation from NerdSMP's ClientFeeds, version 1. A page with an empty name ends the
 * conversation with that token. Text is plain; the server has already stripped colour codes.
 */
public record DialoguePayload(String token, String name, String title, List<String> body, List<Reply> replies)
        implements CustomPacketPayload {
    public record Reply(String text, boolean available, boolean highlighted, List<String> details) { }

    boolean closes() { return name.isEmpty(); }

    public static final Type<DialoguePayload> TYPE = new Type<>(Identifier.parse("aerborne:dialogue"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialoguePayload> CODEC = new StreamCodec<>() {
        @Override public DialoguePayload decode(RegistryFriendlyByteBuf b) {
            int version = b.readInt();
            if (version != 1) throw new IllegalArgumentException("Unsupported dialogue payload " + version);
            String token = b.readUtf(64), name = b.readUtf(256), title = b.readUtf(256);
            List<String> body = strings(b, 24);
            int count = b.readInt();
            if (count < 0 || count > 5) throw new IllegalArgumentException("Too many replies");
            List<Reply> replies = new ArrayList<>(count);
            for (int i = 0; i < count; i++) replies.add(new Reply(b.readUtf(), b.readBoolean(), b.readBoolean(), strings(b, 6)));
            return new DialoguePayload(token, name, title, body, List.copyOf(replies));
        }
        @Override public void encode(RegistryFriendlyByteBuf b, DialoguePayload p) {
            b.writeInt(1); b.writeUtf(p.token); b.writeUtf(p.name); b.writeUtf(p.title); write(b, p.body);
            b.writeInt(p.replies.size());
            for (Reply r : p.replies) { b.writeUtf(r.text); b.writeBoolean(r.available); b.writeBoolean(r.highlighted); write(b, r.details); }
        }
    };
    private static List<String> strings(RegistryFriendlyByteBuf b, int max) {
        int n = b.readInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("Too many dialogue lines");
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(b.readUtf());
        return List.copyOf(out);
    }
    private static void write(RegistryFriendlyByteBuf b, List<String> lines) { b.writeInt(lines.size()); lines.forEach(b::writeUtf); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}

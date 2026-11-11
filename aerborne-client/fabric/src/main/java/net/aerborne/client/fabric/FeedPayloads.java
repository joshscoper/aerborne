package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The party, toast and quest book feeds from NerdSMP's ClientFeeds and QuestService, each version 1. */
final class FeedPayloads {
    private FeedPayloads() { }

    private static void version(RegistryFriendlyByteBuf b, String what) {
        int version = b.readInt();
        if (version != 1) throw new IllegalArgumentException("Unsupported " + what + " payload " + version);
    }

    private static int count(RegistryFriendlyByteBuf b, int max) {
        int n = b.readInt();
        if (n < 0 || n > max) throw new IllegalArgumentException("Too many entries: " + n);
        return n;
    }

    private static List<String> strings(RegistryFriendlyByteBuf b, int max) {
        int n = count(b, max);
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(b.readUtf());
        return List.copyOf(out);
    }

    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> readOnly(java.util.function.Function<RegistryFriendlyByteBuf, T> read) {
        return new StreamCodec<>() {
            @Override public T decode(RegistryFriendlyByteBuf b) { return read.apply(b); }
            @Override public void encode(RegistryFriendlyByteBuf b, T value) { throw new UnsupportedOperationException("Server to client only"); }
        };
    }

    // ------------------------------------------------------------------ party

    record Member(String name, boolean leader, boolean online, float health, float maxHealth, int distance) { }

    record Party(List<Member> members) implements CustomPacketPayload {
        static final Type<Party> TYPE = new Type<>(Identifier.parse("aerborne:party"));
        static final StreamCodec<RegistryFriendlyByteBuf, Party> CODEC = readOnly(b -> {
            version(b, "party");
            int n = count(b, 16);
            List<Member> members = new ArrayList<>(n);
            for (int i = 0; i < n; i++)
                members.add(new Member(b.readUtf(64), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readFloat(), b.readInt()));
            return new Party(List.copyOf(members));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ------------------------------------------------------------------ toast

    record Toast(String kind, String title, String line) implements CustomPacketPayload {
        static final Type<Toast> TYPE = new Type<>(Identifier.parse("aerborne:toast"));
        static final StreamCodec<RegistryFriendlyByteBuf, Toast> CODEC = readOnly(b -> {
            version(b, "toast");
            return new Toast(b.readUtf(32), b.readUtf(1024), b.readUtf(1024));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ------------------------------------------------------------------ quest book

    record Objective(String description, int have, int count, String zone) { }
    record Reward(String skill, long amount) { }

    /** {@code status}: 0 available, 1 active, 2 ready to turn in, 3 completed. */
    record Quest(String id, String title, String chain, int status, String giver, String turnIn, int minimumLevel, boolean daily,
                 List<String> offer, List<Objective> objectives, long marks, List<Reward> experience, List<String> items) { }

    record Quests(boolean open, int completed, String tracked, List<Quest> quests) implements CustomPacketPayload {
        static final Type<Quests> TYPE = new Type<>(Identifier.parse("aerborne:quests"));
        static final StreamCodec<RegistryFriendlyByteBuf, Quests> CODEC = readOnly(b -> {
            version(b, "quest book");
            boolean open = b.readBoolean();
            int completed = b.readInt();
            String tracked = b.readUtf(64);
            int n = count(b, 128);
            List<Quest> quests = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                String id = b.readUtf(64), title = b.readUtf(), chain = b.readUtf();
                int status = b.readByte();
                String giver = b.readUtf(), turnIn = b.readUtf();
                int level = b.readInt();
                boolean daily = b.readBoolean();
                List<String> offer = strings(b, 8);
                int objectives = count(b, 6);
                List<Objective> list = new ArrayList<>(objectives);
                for (int o = 0; o < objectives; o++) list.add(new Objective(b.readUtf(), b.readInt(), b.readInt(), b.readUtf()));
                long marks = b.readLong();
                int xp = count(b, 16);
                List<Reward> rewards = new ArrayList<>(xp);
                for (int r = 0; r < xp; r++) rewards.add(new Reward(b.readUtf(64), b.readLong()));
                List<String> items = strings(b, 8);
                quests.add(new Quest(id, title, chain, status, giver, turnIn, level, daily, offer, List.copyOf(list), marks,
                        List.copyOf(rewards), items));
            }
            return new Quests(open, completed, tracked, List.copyOf(quests));
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}

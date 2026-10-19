package net.nerdsmp.paper.item;

import net.nerdsmp.paper.forge.GemType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Keeps the existing type:tier|type:tier format, without silently discarding bad entries. */
public final class SocketCodec {
    public static final int MAXIMUM_SOCKETS = 4;
    private static final int MAXIMUM_ENCODED_LENGTH = 128;

    private SocketCodec() {}

    public static Decoded decode(String encoded, int capacity) {
        if (capacity < 0 || capacity > MAXIMUM_SOCKETS) return Decoded.invalid();
        if (encoded == null || encoded.isEmpty()) return new Decoded(true, List.of());
        if (encoded.length() > MAXIMUM_ENCODED_LENGTH) return Decoded.invalid();
        String[] parts = encoded.split("\\|", -1);
        if (parts.length > capacity) return Decoded.invalid();
        List<PaperItemService.SocketedGem> sockets = new ArrayList<>(parts.length);
        for (String part : parts) {
            String[] fields = part.split(":", -1);
            if (fields.length != 2 || !fields[0].matches("[a-z_]+")
                || !fields[1].matches("[1-4]")) return Decoded.invalid();
            GemType type = GemType.fromId(fields[0]);
            if (type == null) return Decoded.invalid();
            sockets.add(new PaperItemService.SocketedGem(type, Integer.parseInt(fields[1])));
        }
        return new Decoded(true, sockets);
    }

    public static String encode(List<PaperItemService.SocketedGem> sockets) {
        Objects.requireNonNull(sockets, "sockets");
        if (sockets.size() > MAXIMUM_SOCKETS || sockets.stream().anyMatch(socket ->
            socket == null || socket.type() == null || !SocketRules.validTier(socket.tier()))) {
            throw new IllegalArgumentException("Invalid socket data");
        }
        return sockets.stream().map(socket -> socket.type().id() + ":" + socket.tier())
            .collect(Collectors.joining("|"));
    }

    public record Decoded(boolean valid, List<PaperItemService.SocketedGem> sockets) {
        public Decoded { sockets = List.copyOf(sockets); }
        private static Decoded invalid() { return new Decoded(false, List.of()); }
    }
}

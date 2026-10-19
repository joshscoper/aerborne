package net.nerdsmp.paper.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class SocketRules {
    private SocketRules() {}

    public static boolean validTier(int tier) { return tier >= 1 && tier <= 4; }

    public static int dustCost(int tier) {
        if (!validTier(tier)) throw new IllegalArgumentException("Gem tier must be between 1 and 4");
        return 4 + tier * 4;
    }

    public static Optional<Removal> remove(List<PaperItemService.SocketedGem> sockets, int index,
                                           PaperItemService.SocketedGem expected) {
        if (sockets == null || expected == null || index < 0 || index >= sockets.size()
            || !expected.equals(sockets.get(index)) || expected.type() == null
            || !validTier(expected.tier())) return Optional.empty();
        List<PaperItemService.SocketedGem> remaining = new ArrayList<>(sockets);
        PaperItemService.SocketedGem recovered = remaining.remove(index);
        return Optional.of(new Removal(recovered, remaining));
    }

    public record Removal(PaperItemService.SocketedGem recovered, List<PaperItemService.SocketedGem> remaining) {
        public Removal { remaining = List.copyOf(remaining); }
    }
}

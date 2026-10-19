package net.nerdsmp.paper.activity;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Catch-location safety and earned habitat unlocks; Minecraft decides whether a catch occurred. */
public final class IslandFishingPolicy {
    public static final int MAX_BLOCK_READS = 0;

    private IslandFishingPolicy() { }

    public enum PondHabitat {
        FRESHWATER("freshwater", "Freshwater", 0, FishingCatch.Habitat.INLAND),
        RIVER("river", "River", 2, FishingCatch.Habitat.RIVER),
        WETLAND("wetland", "Wetland", 3, FishingCatch.Habitat.SWAMP),
        OCEAN("ocean", "Ocean", 4, FishingCatch.Habitat.OCEAN),
        COLD("cold", "Cold Water", 5, FishingCatch.Habitat.COLD);

        private final String key;
        private final String title;
        private final int stage;
        private final FishingCatch.Habitat fishingHabitat;

        PondHabitat(String key, String title, int stage, FishingCatch.Habitat fishingHabitat) {
            this.key = key;
            this.title = title;
            this.stage = stage;
            this.fishingHabitat = fishingHabitat;
        }

        public String key() { return key; }
        public String title() { return title; }
        public int stage() { return stage; }
        FishingCatch.Habitat fishingHabitat() { return fishingHabitat; }

        public static PondHabitat parse(String key) {
            if (key == null) return FRESHWATER;
            return Arrays.stream(values()).filter(value -> value.key.equals(key.toLowerCase(Locale.ROOT)))
                .findFirst().orElse(FRESHWATER);
        }
    }

    public static List<PondHabitat> unlocked(int stage) {
        return Arrays.stream(PondHabitat.values()).filter(value -> value.stage <= Math.max(0, stage)).toList();
    }

    public static PondHabitat effective(PondHabitat selected, int stage) {
        return selected != null && selected.stage <= Math.max(0, stage) ? selected : PondHabitat.FRESHWATER;
    }

    public static PondHabitat next(PondHabitat selected, int stage) {
        List<PondHabitat> unlocked = unlocked(stage);
        int current = unlocked.indexOf(effective(selected, stage));
        return unlocked.get((current + 1) % unlocked.size());
    }

    public static long experience(long configuredBase, FishingRolls.Rarity rarity) {
        long base = Math.max(1L, Math.min(1_000_000L, configuredBase));
        return base + switch (rarity) {
            case COMMON -> 0L;
            case UNCOMMON -> 12L;
            case RARE -> 40L;
            case LEGENDARY -> 120L;
        };
    }

    public static double islandLuck(int resourceStage) {
        return Math.max(0, Math.min(5, resourceStage));
    }

    public enum PondStatus {
        VALID,
        OUTSIDE_ISLAND,
        UNLOADED,
        INVALID_HEIGHT
    }

    public interface PondAccess {
        int minHeight();
        int maxHeight();
        boolean loaded(int x, int z);
        boolean belongsToIsland(int x, int z);
    }

    /** Validate only the bobber's location. No water, depth, cover, or surrounding-block reads. */
    public static PondStatus inspect(PondAccess access, int hookX, int hookY, int hookZ) {
        if (!access.belongsToIsland(hookX, hookZ)) return PondStatus.OUTSIDE_ISLAND;
        if (!access.loaded(hookX, hookZ)) return PondStatus.UNLOADED;
        if (hookY < access.minHeight() || hookY >= access.maxHeight()) return PondStatus.INVALID_HEIGHT;
        return PondStatus.VALID;
    }
}

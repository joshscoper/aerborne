package net.nerdsmp.paper.zone;

import org.bukkit.entity.EntityType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Reviewed ordinary mob types. World bosses, NPCs and utility entities need dedicated encounter logic. */
public final class ZoneMobTypes {
    private static final Set<String> VANILLA = Arrays.stream((
        "bogged camel cat cave_spider chicken cow creeper donkey drowned "
        + "enderman endermite fox frog goat hoglin horse husk llama magma_cube "
        + "mooshroom mule ocelot panda pig piglin piglin_brute pillager polar_bear "
        + "rabbit ravager sheep silverfish skeleton skeleton_horse slime sniffer spider "
        + "stray strider turtle vindicator witch wither_skeleton wolf zoglin zombie "
        + "zombie_horse zombie_villager zombified_piglin armadillo").split(" "))
        .map(id -> "vanilla:" + id).collect(Collectors.toUnmodifiableSet());
    private ZoneMobTypes() { }
    public static List<String> vanillaIds() { return VANILLA.stream().sorted().toList(); }
    public static boolean allowed(EntityType type) {
        return type != null && VANILLA.contains("vanilla:" + type.name().toLowerCase(Locale.ROOT));
    }
    public static boolean validId(String id) {
        return id != null && (VANILLA.contains(id) || id.matches("custom:[a-z0-9][a-z0-9_-]{0,63}"));
    }
    public static Optional<EntityType> vanilla(String id) {
        if (!VANILLA.contains(id)) return Optional.empty();
        return Optional.of(EntityType.valueOf(id.substring("vanilla:".length()).toUpperCase(Locale.ROOT)));
    }
}

package net.nerdsmp.paper.expedition.content;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The ability kit encounter mobs draw on, by id. Every ability is telegraphed (a ring, a line or a
 * sound) before it lands, so a careful party can always dodge or answer it. Elites without listed
 * abilities roll one from {@link #ELITE_POOL}; mini-bosses roll two.
 */
public final class Abilities {
    /** One ability: what it does for guides and the cooldown in seconds. */
    public record Info(String id, String title, String description, int cooldownSeconds) { }

    private static final Map<String, Info> KIT = new java.util.LinkedHashMap<>();

    static {
        add("charge", "Charge", "Lowers its head, then rushes the farthest player in a line, knocking aside anyone in the way.", 10);
        add("cleave", "Cleave", "Winds up, then sweeps everything in front of it. Step behind or out of the arc.", 7);
        add("slam", "Ground Slam", "A ring marks the ground around it, then a shockwave hits everyone inside.", 12);
        add("leap", "Leap", "Leaps onto a far player and stuns everyone near the landing spot.", 14);
        add("blink", "Blink", "Vanishes in smoke and reappears behind its target.", 9);
        add("fire_pools", "Fire Pools", "Marks the ground under each player; the marks burst into fire a moment later.", 15);
        add("meteor", "Meteor", "A shadow grows on one player; share the impact with them to split the damage.", 18);
        add("summon", "Summon", "Calls two helpers from the dark. Kill them before they swarm the healer.", 25);
        add("shield", "Bone Shield", "Shields itself while its helpers live. Kill the helpers to break it.", 30);
        add("frost_nova", "Frost Nova", "Freezes and slows everyone close by. Keep your distance.", 14);
        add("poison_cloud", "Poison Cloud", "Leaves a lingering cloud of poison. Move out of it.", 12);
        add("tether", "Tether", "Chains the nearest player and drags them in. Break line of sight to snap it.", 16);
        add("barrage", "Barrage", "Looses a volley of arrows at random players.", 10);
        add("mend", "Mend", "Heals itself and wounded allies around it. Interrupt it with burst damage.", 16);
        add("web", "Web", "Throws a web that pins a player in place.", 12);
    }

    /** Abilities an elite can roll when it has none listed. */
    public static final List<String> ELITE_POOL = List.of("charge", "cleave", "blink", "frost_nova", "poison_cloud", "barrage", "mend", "web");

    private Abilities() { }

    private static void add(String id, String title, String description, int cooldown) {
        KIT.put(id, new Info(id, title, description, cooldown));
    }

    public static boolean known(String id) { return id != null && KIT.containsKey(id); }

    public static Optional<Info> info(String id) { return Optional.ofNullable(KIT.get(id)); }

    public static List<String> ids() { return List.copyOf(KIT.keySet()); }
}

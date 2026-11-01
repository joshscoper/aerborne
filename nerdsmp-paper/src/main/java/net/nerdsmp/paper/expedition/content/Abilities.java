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
    /**
     * One ability: what it does for guides and the cooldown in seconds. A cast shows a bar over the
     * mob for {@code castSeconds} before it lands; {@code interruptHits} charged hits stop it, and 0
     * means it can't be stopped, only dodged or blocked.
     */
    public record Info(String id, String title, String description, int cooldownSeconds, double castSeconds, int interruptHits) {
        public boolean casted() { return castSeconds > 0; }
        public boolean interruptible() { return interruptHits > 0; }
    }

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
        cast("mend", "Mend", "Heals itself and wounded allies around it. A charged hit stops it.", 16, 2.5, 1);
        add("web", "Web", "Throws a web that pins a player in place.", 12);
        add("wrath", "Wrath", "A wide ring of flame marks the ground, then a blast throws everyone inside outward. Keep your back to safe ground.", 20);
        add("volatile_mark", "Volatile Mark", "Marks one player. Get away from the others before it bursts.", 17);
        cast("bolt", "Soul Bolt", "Hurls a bolt at whoever it's fighting. A charged hit stops it.", 5, 1.8, 1);
        cast("ward", "Warding Word", "Wraps its most wounded ally in a ward that blunts every hit. A charged hit stops it.", 14, 2.0, 1);
        cast("dread_howl", "Dread Howl", "A long howl that slows and blinds everyone close. A charged hit stops it.", 16, 2.5, 1);
        cast("rally", "Rally", "Drives its allies on, and they hit harder for ten seconds. A charged hit stops it.", 18, 2.0, 1);
        cast("heavy_blow", "Heavy Blow", "Winds up on whoever it's fighting. Raise a shield or get out of reach.", 11, 2.0, 0);
        cast("soul_rend", "Soul Rend", "A long channel that tears at the whole party when it finishes. It takes three charged hits to break.", 26, 4.0, 3);
        add("grave_grasp", "Grave Grasp", "Bones burst from the floor under a player. Step off the ring or be pinned.", 11);
        add("bone_burst", "Bone Burst", "It comes apart as it falls, and the shards fly out a moment later. Step away from the body.", 0);
        add("pin_shot", "Pinning Shot", "A red line marks the target, then a heavy arrow flies down it and slows whoever it hits. Sidestep.", 9);
        add("arrow_rain", "Arrow Rain", "Marks the ground, then arrows fall across the ring.", 13);
        add("cursed_ground", "Cursed Ground", "Soul fire pools under a player and stays a while. Move out of it.", 12);
        add("ambush", "Ambush", "Melts out of sight, then steps out behind its target and strikes.", 12);
        add("bone_throw", "Hurled Bones", "Heaves a mass of bone at a marked spot.", 10);
        add("bone_wave", "Bone Wave", "A ring of bone rolls out across the floor. Jump as it reaches you.", 14);
        add("venom_spit", "Venom Spit", "A green line marks the shot, then venom flies down it and poisons whoever it hits. Sidestep.", 8);
        add("root_snare", "Root Snare", "Roots burst from the deck under a player. Step off the ring or be held.", 11);
        add("web_wrap", "Web Wrap", "Wraps a player in silk, poisoning them until the cocoon at their feet is broken. Anyone can break it, the player included.", 18);
        add("gust_shot", "Gust", "Throws a slow ball of wind that pushes whoever it hits a little. Watch it come, and sidestep.", 6);
        add("ember_burst", "Ember Burst", "It glows, and a ring shows how far the flame will reach. Back away before it bursts.", 9);
        add("frost_bolt", "Frost Bolt", "Fires a bolt of frost you can see coming at a marked spot. It leaves ice that slows anyone on it.", 7);
        cast("arc_bolt", "Arcing Bolt", "Lightning strikes one player and jumps to anyone standing close to them. Spread out, or a charged hit stops it.", 8, 1.5, 1);
        cast("charged_pulse", "Charged Pulse", "A long wind-up, then a blast throws everyone close back. Step out, or a charged hit stops it.", 12, 2, 1);
        add("spear_fall", "Spears of Light", "Spears of light fall on marked spots, stand a moment, then burst. Don't linger near one.", 14);
        add("dancing_blade", "Dancing Blade", "A red line marks the throw. The blade spins out along it and comes back the same way.", 9);
        add("rime_share", "Shared Frost", "A ring closes on it and the frost is split between everyone inside. Stand together; anyone outside takes all of it.", 18);
        add("hoarfrost_chase", "Hoarfrost Pursuit", "It picks one player and runs them down, leaving ice behind it. Keep moving and keep away from your friends.", 22);
        add("cataclysm", "Cataclysm", "It throws its aegis to the floor and brings the sky down. Stand in the ring round the aegis or take the whole of it.", 34);
        add("echo_ward", "Echo Ward", "Calls an echo of each Warden onto its plate, and can't be hurt while any of them stands.", 900);
        cast("void_bolt", "Void Bolt", "A bolt of nothing at one player. A charged hit stops it.", 6, 1.6, 1);
        add("gravity_well", "Gravity Well", "A well opens under someone and drags everyone close towards it. Be out of the middle when it closes.", 20);
        add("void_rift", "Void Rift", "Pools of nothing open under each player and stay open. Step out, and keep out.", 16);
        add("void_bond", "Void Bond", "Ties two players together. Get far enough apart before it closes, or it hurts you both.", 24);
        add("eye_of_the_void", "Eye of the Void", "Eyes open in the air and cast until they're killed.", 30);
        add("black_harvest", "Black Harvest", "Rings of black run out across the floor, each with one gap. Stand in the gap.", 40);
        add("void_step", "Void Step", "Steps through nothing three times, and each spot it leaves bursts a moment later.", 22);
        add("drone_ward", "Drone Ward", "Calls valve drones to run the pumps. It can't be hurt until they're all stopped, and then it takes double for a while.", 45);
        add("geyser", "Geyser", "A marked circle under someone bursts a few seconds later and throws everyone in it into the air.", 8);
        add("waterspout", "Waterspout", "Stands in a column of water and calls its guard. Nothing gets through it until they're dead, and it throws back anyone close.", 90);
        add("tidal_wave", "Tidal Wave", "A wall of water crosses the whole floor. Get behind something solid.", 26);
        add("overpressure", "Overpressure", "A tank's valve starts to go. Break it in time, or the tank bursts, hits everyone and makes it stronger.", 16);
        add("binding_vines", "Binding Vines", "Roots tie two players together. Pull too far apart and they drag you back and bite.", 22);
        add("mudslide", "Mudslide", "A long line of mud down a marked path. Step off it.", 11);
        add("awaken_silt", "Awaken the Silt", "Rings burst one after another. Move to ground that's already gone off.", 24);
        add("sigil_brand", "Sigil Brand", "Everyone is branded with a seal. Reach that seal's plate before the brand burns in, or be found unworthy.", 26);
        add("spore_burst", "Spore Burst", "It bursts as it dies, and a cloud of spores hangs where it fell. Step away from the body.", 0);
    }

    /** Abilities an elite can roll when it has none listed. */
    public static final List<String> ELITE_POOL = List.of("charge", "cleave", "blink", "frost_nova", "poison_cloud", "barrage", "mend", "web");

    private Abilities() { }

    private static void add(String id, String title, String description, int cooldown) {
        KIT.put(id, new Info(id, title, description, cooldown, 0, 0));
    }

    private static void cast(String id, String title, String description, int cooldown, double seconds, int hits) {
        KIT.put(id, new Info(id, title, description, cooldown, seconds, hits));
    }

    public static boolean known(String id) { return id != null && KIT.containsKey(id); }

    public static Optional<Info> info(String id) { return Optional.ofNullable(KIT.get(id)); }

    public static List<String> ids() { return List.copyOf(KIT.keySet()); }
}

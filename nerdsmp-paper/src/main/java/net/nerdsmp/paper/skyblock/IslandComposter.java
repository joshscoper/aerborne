package net.nerdsmp.paper.skyblock;

import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;

/**
 * The island composter's rules, after Hypixel's garden composter: surplus crops and greenery go in as organic matter,
 * and a few minutes per load the composter turns it into Compost, even while the co-op is away. Compost ripens the crops
 * around where it's used. The menu and the bookkeeping live in {@link IslandFarmstead}.
 */
public final class IslandComposter {
    private IslandComposter() { }

    /** Organic matter one Compost takes, how long one takes, and how much either can hold. */
    public static final double ORGANIC_PER_COMPOST = 40;
    public static final long MILLIS_PER_COMPOST = 4 * 60_000L;
    public static final double MAX_ORGANIC = 4000;
    public static final int MAX_COMPOST = 128;
    /** Compost ripens crops this many blocks out from where it lands, either way: a 5×5 patch. */
    public static final int RADIUS = 2;

    /** What each item is worth as organic matter. */
    static final Map<Material, Double> ORGANIC = Map.ofEntries(
        Map.entry(Material.WHEAT, 1.0), Map.entry(Material.WHEAT_SEEDS, 0.25), Map.entry(Material.CARROT, 1.0), Map.entry(Material.POTATO, 1.0),
        Map.entry(Material.POISONOUS_POTATO, 1.0), Map.entry(Material.BEETROOT, 1.0), Map.entry(Material.BEETROOT_SEEDS, 0.25),
        Map.entry(Material.MELON_SLICE, 0.5), Map.entry(Material.MELON, 4.0), Map.entry(Material.PUMPKIN, 3.0), Map.entry(Material.SUGAR_CANE, 1.0),
        Map.entry(Material.NETHER_WART, 1.0), Map.entry(Material.COCOA_BEANS, 1.0), Map.entry(Material.APPLE, 2.0), Map.entry(Material.CACTUS, 1.0),
        Map.entry(Material.KELP, 0.5), Map.entry(Material.ROTTEN_FLESH, 0.5), Map.entry(Material.SWEET_BERRIES, 0.5),
        Map.entry(Material.OAK_LEAVES, 0.25), Map.entry(Material.BIRCH_LEAVES, 0.25), Map.entry(Material.SPRUCE_LEAVES, 0.25),
        Map.entry(Material.JUNGLE_LEAVES, 0.25), Map.entry(Material.ACACIA_LEAVES, 0.25), Map.entry(Material.DARK_OAK_LEAVES, 0.25),
        Map.entry(Material.CHERRY_LEAVES, 0.25), Map.entry(Material.MANGROVE_LEAVES, 0.25),
        Map.entry(Material.OAK_SAPLING, 0.5), Map.entry(Material.BIRCH_SAPLING, 0.5), Map.entry(Material.SPRUCE_SAPLING, 0.5),
        Map.entry(Material.JUNGLE_SAPLING, 0.5), Map.entry(Material.ACACIA_SAPLING, 0.5), Map.entry(Material.DARK_OAK_SAPLING, 0.5));

    /** A composter's contents: organic matter waiting, Compost ready, and when it last worked. */
    public record State(double organic, int compost, long at) { }

    /** Works the composter up to {@code now}: one Compost per load of organic matter per four minutes, up to what it can hold. */
    public static State work(State state, long now) {
        if (state.at() <= 0) return new State(state.organic(), state.compost(), now);
        long elapsed = Math.max(0, now - state.at());
        int byTime = (int) (elapsed / MILLIS_PER_COMPOST);
        int byMatter = (int) Math.floor(state.organic() / ORGANIC_PER_COMPOST);
        int room = MAX_COMPOST - state.compost();
        int made = Math.max(0, Math.min(byTime, Math.min(byMatter, room)));
        // Time that couldn't be used (nothing to compost, or full) isn't banked.
        long at = made < byTime ? now : state.at() + made * MILLIS_PER_COMPOST;
        return new State(state.organic() - made * ORGANIC_PER_COMPOST, state.compost() + made, at);
    }

    /** Adds organic matter, up to what the composter holds; returns the new state and how much went in. */
    public static State add(State state, double organic) {
        return new State(Math.min(MAX_ORGANIC, state.organic() + Math.max(0, organic)), state.compost(), state.at());
    }

    static NamespacedKey key(Plugin plugin) { return new NamespacedKey(plugin, "compost"); }

    /** One Compost: bone meal by look, but it ripens a whole patch and only works on an island. */
    public static ItemStack compostItem(Plugin plugin) {
        ItemStack item = new ItemStack(Material.BONE_MEAL);
        item.editMeta(meta -> {
            meta.itemName(Chat.render("<green>Compost</green>"));
            meta.lore(List.of(
                Chat.render("<!i><gray>Right-click a crop on your island to ripen</gray>"),
                Chat.render("<!i><gray>every crop in a 5×5 patch around it.</gray>"),
                Chat.render("<!i><dark_gray>Made in the Farmstead composter.</dark_gray>")));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        });
        return item;
    }

    public static boolean isCompost(Plugin plugin, ItemStack item) {
        return item != null && item.getType() == Material.BONE_MEAL && item.hasItemMeta()
            && item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }
}

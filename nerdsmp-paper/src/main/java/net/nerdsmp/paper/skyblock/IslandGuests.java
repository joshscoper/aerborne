package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.item.FoodItems;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.ClientFeeds;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Island visitors, after Hypixel SkyBlock's garden visitors. Folk of the Reach turn up at an island over time (even
 * while the co-op is away), each asking for a little of what the island grows, mines, cooks or catches, and paying
 * Marks, island XP, Favour and, from the rarer ones, a bonus item.
 * <ul>
 *   <li>Every visitor has a rarity: rarer folk only come to bigger islands, ask for more and pay far better. Special
 *       visitors only come during the Double XP Weekend, and some only come once the co-op has done something (a quest
 *       chain, the world boss, an expedition, a crop milestone, a contest medal).</li>
 *   <li>The co-op meets them in person at the island spawn ({@link IslandGuestCrowd}), accepts or refuses each offer,
 *       and fills in a visitor log. Serving the same visitor again and again makes a friend of them, with gifts.</li>
 *   <li>Favour, the visitors' own currency, is spent at the Visitor's Stall on benches, swifter arrivals and goods.</li>
 *   <li>Milestones for visitors met and offers accepted pay Favour and open perks.</li>
 * </ul>
 * Requests only ever name things the island can make at its current resource stage.
 */
public final class IslandGuests implements Listener, AutoCloseable {
    /** A new visitor every this often, while there's a free spot, before upgrades. */
    static final long ARRIVAL_MILLIS = 20 * 60_000L;
    /** The most visitors that can ever wait at once: five from island level, one from the stall, one from milestones. */
    static final int MAX_SLOTS = 7;

    /** How rare a visitor is: how often they come, the island level they need, how much they ask and how well they pay. */
    public enum Rarity {
        UNCOMMON("Uncommon", "green", 60, 1, 1.0, 1.0, 0.0, 5, 0.0),
        RARE("Rare", "blue", 28, 5, 1.2, 1.4, 0.08, 12, 0.35),
        LEGENDARY("Legendary", "gold", 10, 15, 1.5, 2.0, 0.3, 30, 0.5),
        MYTHIC("Mythic", "light_purple", 2, 30, 2.0, 3.5, 1.0, 75, 0.6),
        SPECIAL("Special", "red", 14, 1, 1.3, 2.5, 0.5, 40, 0.5);

        final String label, color;
        final int weight, level, favour;
        final double ask, pay, bonusChance, specialChance;
        Rarity(String label, String color, int weight, int level, double ask, double pay, double bonusChance, int favour, double specialChance) {
            this.label = label; this.color = color; this.weight = weight; this.level = level; this.ask = ask; this.pay = pay;
            this.bonusChance = bonusChance; this.favour = favour; this.specialChance = specialChance;
        }
        public String label() { return label; }
        /** The rarity as a coloured MiniMessage tag, e.g. "RARE" in blue. */
        public String tag() { return "<" + color + "><b>" + label.toUpperCase(Locale.ROOT) + "</b></" + color + ">"; }
        public String paint(String text) { return "<" + color + ">" + text + "</" + color + ">"; }
        /** Whether this rarity can visit: an island level for most, the Double XP Weekend for Special visitors. */
        boolean open(int islandLevel, boolean weekend) { return this == SPECIAL ? weekend : islandLevel >= level; }
    }

    /** One thing a visitor asks for: a plain item, or a special one ("meal:id" or "item:id") shown by its icon. */
    record Want(Material material, int amount, String item) {
        Want(Material material, int amount) { this(material, amount, ""); }
        boolean special() { return !item.isEmpty(); }
    }
    /** A waiting visitor. {@code bonus} is "" for none, "booster", or "item_id:amount". */
    record Guest(String id, int who, List<Want> wants, long marks, long xp, String bonus) {
        Guest(String id, int who, List<Want> wants, long marks, long xp) { this(id, who, wants, marks, xp, ""); }
    }
    record State(long nextAt, int served, List<Guest> guests) { }

    /**
     * Who comes calling: a name, what they say (and what they say to an old friend), what they tend to ask for, how rare
     * they are, how they look, and, for some, what the co-op must have done first ({@code gate}) with a hint for the log.
     */
    record Visitor(String name, String line, String friendLine, Material icon, List<Pool> pools, Rarity rarity, String profession, String biome,
                   String gate, String hint) {
        Visitor(String name, String line, String friendLine, Material icon, List<Pool> pools, Rarity rarity, String profession, String biome) {
            this(name, line, friendLine, icon, pools, rarity, profession, biome, "", "");
        }
    }
    enum Pool { FARM, WOOD, MINE, FOOD, FISH }

    /** The roster. New visitors go on the end: a saved visitor is stored by its place in this list. */
    static final List<Visitor> VISITORS = List.of(
        new Visitor("Tilly Miller", "Ada sent me. The mill's hungry again.", "I tell the whole mill your wheat's the best in the sky. They don't believe me. Their loss.", Material.WHEAT, List.of(Pool.FARM, Pool.FARM, Pool.FOOD), Rarity.UNCOMMON, "farmer", "plains"),
        new Visitor("Garth's Apprentice", "Master Garth says your metal's the cleanest in the sky.", "Master Garth let me forge a blade from your iron. He said 'not bad'. He never says 'not bad'.", Material.IRON_INGOT, List.of(Pool.MINE, Pool.MINE), Rarity.UNCOMMON, "armorer", "plains"),
        new Visitor("Highcrown Baker", "The city's ovens never sleep, and neither do I.", "I named a loaf after your island. It sells out by noon.", Material.BREAD, List.of(Pool.FARM, Pool.FOOD), Rarity.UNCOMMON, "butcher", "plains"),
        new Visitor("Dockmaster Ebb", "A skyship needs timber, and a crew needs supper.", "The crews fight over who gets to fly out here. I let the winners carry the crates.", Material.OAK_BOAT, List.of(Pool.WOOD, Pool.FISH), Rarity.RARE, "fisherman", "swamp"),
        new Visitor("Wandering Tinker", "A bit of this, a bit of that. Mostly that.", "I've stopped wandering, mostly. I just wander here.", Material.CHEST, List.of(Pool.FARM, Pool.WOOD, Pool.MINE), Rarity.UNCOMMON, "nitwit", "savanna"),
        new Visitor("Warden Quartermaster", "Hale's lot march on their stomachs and their boots.", "The wardens call your crates 'the good crates'. High praise, from them.", Material.SHIELD, List.of(Pool.MINE, Pool.FOOD), Rarity.RARE, "weaponsmith", "taiga"),
        new Visitor("Rift Scholar Vell", "Stones from the high stages hum near the Rift. I'd like to hear yours.", "Your stones sing in a key I've never heard. I've written three papers on it.", Material.AMETHYST_SHARD, List.of(Pool.MINE), Rarity.LEGENDARY, "librarian", "snow"),
        new Visitor("Millbrook Carter", "First cart west leaves at dawn. It isn't full yet.", "Your crates ride up front now. Nobody argues.", Material.HAY_BLOCK, List.of(Pool.FARM, Pool.WOOD), Rarity.UNCOMMON, "farmer", "savanna"),
        new Visitor("Skyfarer Courier", "The Guild's patching a hull. Planks, please, and quickly.", "The Guild wants to know your secret. I told them it's just good timber and better people.", Material.FEATHER, List.of(Pool.WOOD), Rarity.UNCOMMON, "cartographer", "plains"),
        new Visitor("Brother Tobin's Novice", "The chapel feeds anyone who knocks. We're running low.", "Brother Tobin keeps your island in his prayers. And in his pantry.", Material.CANDLE, List.of(Pool.FOOD, Pool.FARM), Rarity.UNCOMMON, "cleric", "plains"),
        new Visitor("Pip Brindle", "The bees want sugar and I want pie. Can you help either of us?", "The bees have forgiven me. I think it's your sugar.", Material.HONEY_BOTTLE, List.of(Pool.FARM, Pool.FOOD), Rarity.UNCOMMON, "shepherd", "jungle"),
        new Visitor("Jory Fenn", "River's high, nets are empty. You've a pond, I hear.", "I've stopped fishing the river. Your pond's friendlier.", Material.FISHING_ROD, List.of(Pool.FISH, Pool.WOOD), Rarity.UNCOMMON, "fisherman", "swamp"),
        new Visitor("Hedge Witch Moira", "Roots and fish-bones. Don't ask what for.", "Fine. It's soup. It was always soup.", Material.BREWING_STAND, List.of(Pool.FARM, Pool.FISH), Rarity.RARE, "cleric", "swamp"),
        new Visitor("Kingsbridge Toll-Keeper", "Nobody pays the toll in coin any more. Bread, though, they'll pay in bread.", "For you, the bridge is free. Don't tell anyone.", Material.IRON_BARS, List.of(Pool.FOOD, Pool.MINE), Rarity.RARE, "mason", "taiga"),
        new Visitor("Orla of the Fletchers", "A good shaft wants straight wood and a sharp head.", "Every warden's quiver has a little of your island in it now.", Material.BOW, List.of(Pool.WOOD, Pool.MINE), Rarity.RARE, "fletcher", "taiga"),
        new Visitor("Saltwind Angler", "The big ones bite at dusk. Today they didn't.", "Caught a big one yesterday. Used your bait. Coincidence? No.", Material.COD, List.of(Pool.FISH, Pool.FISH, Pool.FOOD), Rarity.RARE, "fisherman", "desert"),
        new Visitor("Warden Hale", "Even a warden eats. And my wall needs stone, lots of it.", "The wall's standing because of you. I don't say that lightly.", Material.IRON_SWORD, List.of(Pool.MINE, Pool.FOOD), Rarity.LEGENDARY, "weaponsmith", "taiga",
            "chain:reach_prologue", "Comes once you've finished the Reach prologue."),
        new Visitor("Guildmistress Ada", "My mill grinds for half the Reach. I hear you farm for the other half.", "Half the Reach eats because of us two. Not a bad partnership.", Material.GOLDEN_HOE, List.of(Pool.FARM, Pool.FARM, Pool.FOOD), Rarity.LEGENDARY, "farmer", "plains"),
        new Visitor("Archivist Sorrel", "Every stone from a sky-isle tells you where it fell from.", "Your island has its own shelf in the archive now.", Material.WRITABLE_BOOK, List.of(Pool.MINE, Pool.WOOD), Rarity.LEGENDARY, "librarian", "desert"),
        new Visitor("The Sky Cartographer", "I'm mapping every island in the Reach. Yours smells of good soil.", "On my maps, your island's drawn in gold ink. Only three are.", Material.FILLED_MAP, List.of(Pool.FARM, Pool.WOOD, Pool.MINE, Pool.FISH), Rarity.MYTHIC, "cartographer", "snow"),
        new Visitor("Envoy of Highcrown", "The Crown has heard of this island. It would like a taste.", "The Crown asks for your island by name now. I've never seen that.", Material.GOLDEN_HELMET, List.of(Pool.FOOD, Pool.MINE, Pool.FARM), Rarity.MYTHIC, "librarian", "savanna"),
        new Visitor("Festival Barker", "Double the harvest, double the fun! Have you got enough for a feast?", "Best stall on the festival road, and it's all your stock!", Material.FIREWORK_ROCKET, List.of(Pool.FOOD, Pool.FARM), Rarity.SPECIAL, "nitwit", "jungle"),
        new Visitor("Lantern Keeper Mira", "The weekend lanterns need oil, wicks and supper for the keepers.", "Your lantern's the first one I light now.", Material.LANTERN, List.of(Pool.FISH, Pool.WOOD, Pool.FOOD), Rarity.SPECIAL, "cleric", "snow"),
        new Visitor("Barrow Keeper Wynn", "The barrow's quiet since you went down. I'd like to keep it that way.", "The elder sleeps easy. So do I, mostly.", Material.SKELETON_SKULL, List.of(Pool.MINE, Pool.FOOD), Rarity.RARE, "mason", "taiga",
            "chain:millbrook_elder", "Comes once you've learned what the barrow remembers."),
        new Visitor("Gleaner Hettie", "Every plot's worth gleaning twice. Yours, three times.", "My girls glean your fields first now. They say it's for luck.", Material.WHEAT_SEEDS, List.of(Pool.FARM, Pool.FARM), Rarity.RARE, "farmer", "savanna",
            "chain:millbrook_gleaners", "Comes once you've helped the Millbrook gleaners."),
        new Visitor("Thunderhide Tracker", "I've followed that beast for years. You've stood in front of it.", "Next time it comes, I want to stand where you stood.", Material.LIGHTNING_ROD, List.of(Pool.MINE, Pool.FOOD, Pool.FISH), Rarity.LEGENDARY, "leatherworker", "savanna",
            "boss", "Comes once you've stood against Thunderhide."),
        new Visitor("Expedition Veteran Kael", "I've been down where you've been. I'm hungry for real food.", "You and I, we've both seen the deep places. Eat with me.", Material.RECOVERY_COMPASS, List.of(Pool.FOOD, Pool.FISH, Pool.MINE), Rarity.LEGENDARY, "weaponsmith", "snow",
            "expedition", "Comes once you've cleared an expedition."),
        new Visitor("Prize Grower Bram", "I grow the biggest pumpkins in the Vale. Or I did, until I heard about you.", "Fine. Yours are bigger. Teach me.", Material.CARVED_PUMPKIN, List.of(Pool.FARM, Pool.FARM, Pool.FOOD), Rarity.RARE, "farmer", "plains",
            "crops", "Comes once any crop reaches milestone III."),
        new Visitor("Contest Judge Ellery", "I judge the harvest contests. I'd like to see the soil that won a medal.", "I'm not supposed to have favourites. I have one.", Material.GOLDEN_CARROT, List.of(Pool.FARM, Pool.FOOD), Rarity.LEGENDARY, "librarian", "plains",
            "contest", "Comes once you've won a farming contest medal."),
        new Visitor("Collector Pell", "I collect collectors. You've caught my eye.", "My cabinet has a whole drawer for you now.", Material.BUNDLE, List.of(Pool.MINE, Pool.WOOD, Pool.FISH, Pool.FARM), Rarity.LEGENDARY, "cartographer", "desert",
            "collector", "Comes once you've reached 15 collection tiers."));

    /** What each item is worth to a visitor (Marks each) and how many they ask for at island level 1. */
    record Price(double marks, int base) { }
    static final Map<Material, Price> PRICES = new LinkedHashMap<>();
    static {
        PRICES.put(Material.WHEAT, new Price(1, 48)); PRICES.put(Material.CARROT, new Price(1, 48));
        PRICES.put(Material.POTATO, new Price(1, 48)); PRICES.put(Material.BEETROOT, new Price(1.2, 32));
        PRICES.put(Material.SUGAR_CANE, new Price(1.5, 32)); PRICES.put(Material.PUMPKIN, new Price(3, 12));
        PRICES.put(Material.MELON_SLICE, new Price(0.6, 64));
        PRICES.put(Material.OAK_LOG, new Price(2, 32)); PRICES.put(Material.OAK_PLANKS, new Price(0.6, 64)); PRICES.put(Material.APPLE, new Price(4, 6));
        PRICES.put(Material.BREAD, new Price(3.5, 16)); PRICES.put(Material.BAKED_POTATO, new Price(2, 24)); PRICES.put(Material.PUMPKIN_PIE, new Price(7, 6));
        PRICES.put(Material.COD, new Price(3, 8)); PRICES.put(Material.SALMON, new Price(4, 6));
        PRICES.put(Material.COBBLESTONE, new Price(0.5, 64)); PRICES.put(Material.COAL, new Price(2, 24));
        PRICES.put(Material.COPPER_INGOT, new Price(2, 24)); PRICES.put(Material.IRON_INGOT, new Price(5, 16));
        PRICES.put(Material.GOLD_INGOT, new Price(8, 8)); PRICES.put(Material.LAPIS_LAZULI, new Price(3, 16));
        PRICES.put(Material.REDSTONE, new Price(2, 24)); PRICES.put(Material.QUARTZ, new Price(4, 12));
        PRICES.put(Material.OBSIDIAN, new Price(6, 4)); PRICES.put(Material.DIAMOND, new Price(30, 2)); PRICES.put(Material.EMERALD, new Price(40, 2));
    }
    private static final Map<Material, Material> ORE_DROPS = Map.of(Material.COBBLESTONE, Material.COBBLESTONE, Material.COAL_ORE, Material.COAL,
        Material.COPPER_ORE, Material.COPPER_INGOT, Material.IRON_ORE, Material.IRON_INGOT, Material.GOLD_ORE, Material.GOLD_INGOT,
        Material.LAPIS_ORE, Material.LAPIS_LAZULI, Material.REDSTONE_ORE, Material.REDSTONE, Material.NETHER_QUARTZ_ORE, Material.QUARTZ,
        Material.OBSIDIAN, Material.OBSIDIAN, Material.DIAMOND_ORE, Material.DIAMOND);

    /**
     * Special requests, from rarer visitors: a cooked meal from the kitchen, a fish from the island's waters, a refined
     * expedition metal. {@code key} is "meal:dish" or "item:catalog_id".
     */
    record Special(String key, String name, Material icon, double marks, int base, Pool pool, Rarity min) { }
    static final List<Special> SPECIALS = List.of(
        new Special("meal:orchard_loaf", "Orchard Loaf", Material.BREAD, 20, 4, Pool.FARM, Rarity.RARE),
        new Special("meal:garden_omelette", "Garden Omelette", Material.COOKED_CHICKEN, 22, 3, Pool.FARM, Rarity.RARE),
        new Special("meal:beetroot_borscht", "Beetroot Borscht", Material.BEETROOT_SOUP, 22, 3, Pool.FOOD, Rarity.RARE),
        new Special("meal:pumpkin_tart", "Pumpkin Tart", Material.PUMPKIN_PIE, 26, 3, Pool.FOOD, Rarity.RARE),
        new Special("meal:homestead_supper", "Homestead Supper", Material.RABBIT_STEW, 34, 2, Pool.FOOD, Rarity.LEGENDARY),
        new Special("meal:wardens_roast", "Warden's Roast", Material.COOKED_BEEF, 34, 2, Pool.FOOD, Rarity.LEGENDARY),
        new Special("meal:golden_roast", "Golden Roast", Material.COOKED_MUTTON, 48, 2, Pool.FOOD, Rarity.MYTHIC),
        new Special("meal:anglers_chowder", "Angler's Chowder", Material.MUSHROOM_STEW, 24, 3, Pool.FISH, Rarity.RARE),
        new Special("meal:cod_fritters", "Cod Fritters", Material.COOKED_COD, 24, 3, Pool.FISH, Rarity.RARE),
        new Special("meal:seafarers_platter", "Seafarer's Platter", Material.COOKED_SALMON, 48, 2, Pool.FISH, Rarity.MYTHIC),
        new Special("item:river_sprat", "River Sprat", Material.COD, 12, 4, Pool.FISH, Rarity.RARE),
        new Special("item:redfin_perch", "Redfin Perch", Material.SALMON, 12, 4, Pool.FISH, Rarity.RARE),
        new Special("item:moonlit_koi", "Moonlit Koi", Material.TROPICAL_FISH, 40, 2, Pool.FISH, Rarity.LEGENDARY),
        new Special("item:stormfin_tuna", "Stormfin Tuna", Material.COD, 90, 1, Pool.FISH, Rarity.MYTHIC),
        new Special("item:tideglass", "Tideglass", Material.PRISMARINE_CRYSTALS, 40, 2, Pool.MINE, Rarity.LEGENDARY),
        new Special("item:briar_resin", "Briar Resin", Material.HONEYCOMB, 32, 2, Pool.FARM, Rarity.LEGENDARY),
        new Special("item:gloomsteel_ingot", "Gloomsteel Ingot", Material.IRON_INGOT, 45, 2, Pool.MINE, Rarity.LEGENDARY),
        new Special("item:tempered_sigil", "Tempered Sigil", Material.AMETHYST_SHARD, 55, 2, Pool.MINE, Rarity.MYTHIC),
        new Special("item:cindersteel_ingot", "Cindersteel Ingot", Material.NETHERITE_SCRAP, 60, 2, Pool.MINE, Rarity.MYTHIC));
    static final Set<String> ALL_SPECIALS = SPECIALS.stream().map(Special::key).collect(java.util.stream.Collectors.toUnmodifiableSet());

    /** The bonus items rarer visitors may bring: raw expedition materials, refined at a furnace. */
    static final List<String> RARE_BONUS = List.of("tidepearl", "thornheart_sap", "gloomsteel_ore");
    static final List<String> LEGENDARY_BONUS = List.of("tidepearl", "thornheart_sap", "gloomsteel_ore", "cinder_shard", "sigil_shard");
    /** What a bonus item is worth in Marks when the item can't be made (its catalog entry is missing). */
    static final long BONUS_FALLBACK_MARKS = 150;

    /** Milestones for visitors met ({@code met}) and offers accepted, each paying Favour once; some open a perk. */
    record Milestone(String key, boolean met, int need, int favour, String perk) {
        String title() { return met ? "Met " + need + " visitors" : "Accepted " + need + " offers"; }
    }
    static final List<Milestone> MILESTONES = List.of(
        new Milestone("met:5", true, 5, 100, ""),
        new Milestone("met:10", true, 10, 250, "+1 visitor bench"),
        new Milestone("met:15", true, 15, 500, ""),
        new Milestone("met:20", true, 20, 1000, "visitors come 15% sooner"),
        new Milestone("met:25", true, 25, 2500, ""),
        new Milestone("served:25", false, 25, 150, ""),
        new Milestone("served:100", false, 100, 400, "+10% Favour from every offer"),
        new Milestone("served:250", false, 250, 800, ""),
        new Milestone("served:500", false, 500, 1500, ""),
        new Milestone("served:1000", false, 1000, 3000, ""));

    /** Friendship with one visitor: how many times served for each tier, its name, and the Favour it gifts. */
    static final int[] FRIEND_AT = {5, 15, 30};
    static final String[] FRIEND_NAMES = {"Acquaintance", "Friend", "Old Friend"};
    static final int[] FRIEND_FAVOUR = {50, 150, 400};

    /** What the co-op has done and can buy, as the visitor rules need it. */
    record Params(int level, int stage, double marksBonus, boolean weekend, Set<String> gates, Set<String> specials) {
        Params { gates = Set.copyOf(gates); specials = Set.copyOf(specials); }
        static Params basic(int level, int stage, double marksBonus, boolean weekend) { return new Params(level, stage, marksBonus, weekend, Set.of(), ALL_SPECIALS); }
    }

    // ------------------------------------------------------------------ the rules, pure

    /** How many visitors can wait at once from island level alone: 2, one more at island levels 10, 25 and 40. */
    static int slots(int islandLevel) {
        return 2 + (islandLevel >= 10 ? 1 : 0) + (islandLevel >= 25 ? 1 : 0) + (islandLevel >= 40 ? 1 : 0);
    }

    /** All the island's benches: from island level, the stall's extra bench, and the met-10 milestone. */
    static int slots(int islandLevel, int boughtBenches, int met) {
        return Math.min(MAX_SLOTS, slots(islandLevel) + Math.clamp(boughtBenches, 0, 1) + (met >= 10 ? 1 : 0));
    }

    /** The wait between visitors: 10% shorter for each stall upgrade (up to three), 15% more at the met-20 milestone. */
    static long interval(int swift, int met) {
        double factor = 1 - 0.1 * Math.clamp(swift, 0, 3) - (met >= 20 ? 0.15 : 0);
        return Math.round(ARRIVAL_MILLIS * factor);
    }

    static boolean gateOpen(Visitor visitor, Set<String> gates) { return visitor.gate().isEmpty() || gates.contains(visitor.gate()); }

    static List<Material> pool(Pool pool, int stage) {
        return switch (pool) {
            case FARM -> List.of(Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT, Material.SUGAR_CANE, Material.PUMPKIN, Material.MELON_SLICE);
            case WOOD -> List.of(Material.OAK_LOG, Material.OAK_PLANKS, Material.APPLE);
            case FOOD -> List.of(Material.BREAD, Material.BAKED_POTATO, Material.PUMPKIN_PIE);
            case FISH -> List.of(Material.COD, Material.SALMON);
            case MINE -> {
                List<Material> ores = new ArrayList<>();
                for (Material block : GeneratorLoot.resourceTable(stage).keySet()) {
                    Material drop = ORE_DROPS.get(block);
                    if (drop != null && !ores.contains(drop)) ores.add(drop);
                }
                if (stage >= 5 && !ores.contains(Material.EMERALD)) ores.add(Material.EMERALD);
                yield ores;
            }
        };
    }

    /** The rarities that can send someone right now: open for this island, with at least one visitor whose gate is open. */
    static Set<Rarity> rarities(Params params) {
        Set<Rarity> open = EnumSet.noneOf(Rarity.class);
        for (Visitor visitor : VISITORS)
            if (visitor.rarity().open(params.level(), params.weekend()) && gateOpen(visitor, params.gates())) open.add(visitor.rarity());
        return open;
    }

    /** Which rarity the next visitor is, by weight among those that can come. */
    static Rarity rollRarity(Random random, Set<Rarity> open) {
        int total = 0;
        for (Rarity rarity : open) total += rarity.weight;
        if (total <= 0) return Rarity.UNCOMMON;
        int roll = random.nextInt(total);
        for (Rarity rarity : Rarity.values()) {
            if (!open.contains(rarity)) continue;
            roll -= rarity.weight;
            if (roll < 0) return rarity;
        }
        return Rarity.UNCOMMON;
    }

    static Guest generate(long seed, int islandLevel, int stage, double marksBonus) { return generate(seed, Params.basic(islandLevel, stage, marksBonus, false)); }
    static Guest generate(long seed, int islandLevel, int stage, double marksBonus, boolean weekend) { return generate(seed, Params.basic(islandLevel, stage, marksBonus, weekend)); }

    /** A new visitor for this island, decided by the seed alone. */
    static Guest generate(long seed, Params params) {
        Random random = new Random(seed);
        Rarity rarity = rollRarity(random, rarities(params));
        List<Integer> roster = new ArrayList<>();
        for (int i = 0; i < VISITORS.size(); i++)
            if (VISITORS.get(i).rarity() == rarity && gateOpen(VISITORS.get(i), params.gates())) roster.add(i);
        if (roster.isEmpty()) roster.add(0);
        return build(random, seed, roster.get(random.nextInt(roster.size())), params);
    }

    /** A fresh offer from one particular visitor: what a reroll at the stall gives. */
    static Guest offerFrom(long seed, int who, Params params) { return build(new Random(seed), seed, who, params); }

    private static Guest build(Random random, long seed, int who, Params params) {
        Visitor visitor = VISITORS.get(who);
        Rarity rarity = visitor.rarity();
        int count = switch (rarity) {
            case UNCOMMON -> random.nextInt(100) < 40 ? 2 : 1;
            case RARE, SPECIAL -> random.nextInt(100) < 70 ? 2 : 1;
            case LEGENDARY -> 2 + (random.nextInt(100) < 30 ? 1 : 0);
            case MYTHIC -> 3;
        };
        List<Want> wants = new ArrayList<>();
        Set<String> used = new HashSet<>();
        double scale = (1 + params.level() / 40.0) * rarity.ask;
        double value = 0;
        for (int i = 0; i < count; i++) {
            Pool pool = visitor.pools().get(random.nextInt(visitor.pools().size()));
            // After the first ask, a rarer visitor may want something special: a meal, a fish, a refined metal.
            if (i > 0 && random.nextDouble() < rarity.specialChance) {
                List<Special> specials = SPECIALS.stream().filter(s -> s.pool() == pool && s.min().ordinal() <= Math.min(rarity.ordinal(), Rarity.MYTHIC.ordinal())
                    && params.specials().contains(s.key()) && !used.contains(s.key())).toList();
                if (!specials.isEmpty()) {
                    Special special = specials.get(random.nextInt(specials.size()));
                    used.add(special.key());
                    int amount = Math.max(1, (int) Math.round(special.base() * (0.75 + random.nextDouble() * 0.5) * Math.min(rarity.ask, 1.5)));
                    wants.add(new Want(special.icon(), amount, special.key()));
                    value += amount * special.marks();
                    continue;
                }
            }
            List<Material> choices = pool(pool, params.stage()).stream().filter(m -> !used.contains(m.name())).toList();
            // That pool is used up: anything else this visitor asks for will do.
            if (choices.isEmpty()) choices = visitor.pools().stream().distinct().flatMap(p -> pool(p, params.stage()).stream())
                .filter(m -> !used.contains(m.name())).distinct().toList();
            if (choices.isEmpty()) continue;
            Material material = choices.get(random.nextInt(choices.size()));
            used.add(material.name());
            Price price = PRICES.get(material);
            int amount = Math.max(1, (int) Math.round(price.base() * scale * (0.75 + random.nextDouble() * 0.5)));
            wants.add(new Want(material, amount));
            value += amount * price.marks();
        }
        if (wants.isEmpty()) wants.add(new Want(Material.WHEAT, 16));
        long marks = Math.max(10, Math.round(value * 1.6 * rarity.pay * (1 + params.marksBonus())));
        long xp = Math.clamp(Math.round(value / 2 * rarity.pay), 20, 2000);
        return new Guest(Long.toHexString(seed & 0xffffffffffL), who, List.copyOf(wants), marks, xp, bonus(random, rarity));
    }

    /** The bonus a visitor brings, if any: rarer visitors are likelier to, and Mythic ones always do. */
    static String bonus(Random random, Rarity rarity) {
        if (random.nextDouble() >= rarity.bonusChance) return "";
        return switch (rarity) {
            case UNCOMMON -> "";
            case RARE -> RARE_BONUS.get(random.nextInt(RARE_BONUS.size())) + ":" + (1 + random.nextInt(2));
            case LEGENDARY -> LEGENDARY_BONUS.get(random.nextInt(LEGENDARY_BONUS.size())) + ":" + (2 + random.nextInt(2));
            case MYTHIC -> random.nextBoolean() ? "booster" : "sovereign_ash:" + (2 + random.nextInt(2));
            case SPECIAL -> random.nextInt(100) < 30 ? "booster" : LEGENDARY_BONUS.get(random.nextInt(LEGENDARY_BONUS.size())) + ":2";
        };
    }

    static State catchUp(State state, long now, int islandLevel, int stage, double marksBonus, long seedBase) {
        return catchUp(state, now, islandLevel, stage, marksBonus, seedBase, false);
    }

    static State catchUp(State state, long now, int islandLevel, int stage, double marksBonus, long seedBase, boolean weekend) {
        return catchUp(state, now, Params.basic(islandLevel, stage, marksBonus, weekend), slots(islandLevel), ARRIVAL_MILLIS, seedBase);
    }

    /** Fills free spots for the time that has passed; returns the new state. */
    static State catchUp(State state, long now, Params params, int slots, long interval, long seedBase) {
        List<Guest> guests = new ArrayList<>(state.guests());
        long nextAt = state.nextAt() <= 0 ? now : state.nextAt();
        int arrivals = 0;
        while (nextAt <= now && guests.size() < slots) {
            Guest guest = generate(seedBase ^ (nextAt * 31 + state.served() + arrivals), params);
            // Hypixel's rule: the same visitor never waits twice at once. Someone else comes instead.
            for (int retry = 1; retry < 8 && waiting(guests, guest.who()); retry++)
                guest = generate(seedBase ^ (nextAt * 31 + state.served() + arrivals) ^ (retry * 0x9E3779B97F4A7C15L), params);
            if (!waiting(guests, guest.who())) guests.add(guest);
            nextAt += interval;
            arrivals++;
        }
        // A full island doesn't bank arrivals: the next visitor comes a full wait after a spot opens.
        if (guests.size() >= slots && nextAt <= now) nextAt = now + interval;
        return new State(nextAt, state.served(), List.copyOf(guests));
    }

    private static boolean waiting(List<Guest> guests, int who) { return guests.stream().anyMatch(g -> g.who() == who); }

    /** The milestones newly reached at these counts, that haven't paid yet. */
    static List<Milestone> due(int met, int served, Collection<String> paid) {
        return MILESTONES.stream().filter(m -> !paid.contains(m.key()) && (m.met() ? met : served) >= m.need()).toList();
    }

    /** The friendship tier reached after serving a visitor this many times: 0 (none) to 3 (Old Friend). */
    static int friendship(int times) {
        int tier = 0;
        for (int need : FRIEND_AT) if (times >= need) tier++;
        return tier;
    }

    /** Favour for an accepted offer: the rarity's rate, plus 10% from the served-100 milestone. */
    static long favour(Rarity rarity, boolean bonus) { return Math.round(rarity.favour * (bonus ? 1.1 : 1.0)); }

    // ------------------------------------------------------------------ storage

    private final NerdSmpPlugin plugin;
    private final SkyblockController islands;
    private final String path, favourPath;
    private final IslandGuestCrowd crowd;
    private final NamespacedKey dishKey;
    private BukkitTask task;

    IslandGuests(NerdSmpPlugin plugin, SkyblockController islands, String season) {
        this.plugin = plugin;
        this.islands = islands;
        this.path = "seasons." + season + ".island-guests";
        this.favourPath = "seasons." + season + ".favour";
        this.crowd = new IslandGuestCrowd(plugin, islands, this);
        this.dishKey = new NamespacedKey(plugin, "harvest_dish");
    }

    void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 1200L);
        Bukkit.getPluginManager().registerEvents(crowd, plugin);
        crowd.start();
    }

    State read(AccountId owner) {
        return plugin.playerData().read(owner, yaml -> read(yaml, path));
    }

    static State read(YamlConfiguration yaml, String path) {
        List<Guest> guests = new ArrayList<>();
        for (String raw : yaml.getStringList(path + ".guests")) {
            try { guests.add(decode(raw)); } catch (RuntimeException skipped) { /* an unreadable visitor simply leaves */ }
        }
        return new State(yaml.getLong(path + ".next-at", 0L), yaml.getInt(path + ".served", 0), guests);
    }

    private void write(AccountId owner, State state) {
        plugin.playerData().update(owner, yaml -> {
            yaml.set(path + ".next-at", state.nextAt());
            yaml.set(path + ".served", state.served());
            yaml.set(path + ".guests", state.guests().stream().map(IslandGuests::encode).toList());
        });
    }

    static String encode(Guest g) {
        StringBuilder wants = new StringBuilder();
        for (Want w : g.wants()) {
            if (!wants.isEmpty()) wants.append(',');
            wants.append(w.material().name()).append(':').append(w.amount());
            if (w.special()) wants.append(':').append(w.item().replace(':', '='));
        }
        return g.id() + "|" + g.who() + "|" + wants + "|" + g.marks() + "|" + g.xp() + "|" + g.bonus();
    }

    static Guest decode(String raw) {
        String[] parts = raw.split("\\|");
        List<Want> wants = new ArrayList<>();
        for (String want : parts[2].split(",")) {
            String[] pair = want.split(":");
            wants.add(new Want(Material.valueOf(pair[0]), Integer.parseInt(pair[1]), pair.length > 2 ? pair[2].replace('=', ':') : ""));
        }
        int who = Math.floorMod(Integer.parseInt(parts[1]), VISITORS.size());
        return new Guest(parts[0], who, List.copyOf(wants), Long.parseLong(parts[3]), Long.parseLong(parts[4]), parts.length > 5 ? parts[5] : "");
    }

    /** How many times each visitor has been served on this island: the visitor log. */
    Map<Integer, Integer> log(AccountId owner) {
        return plugin.playerData().read(owner, yaml -> readLog(yaml.getStringList(path + ".log")));
    }

    static Map<Integer, Integer> readLog(List<String> raw) {
        Map<Integer, Integer> log = new LinkedHashMap<>();
        for (String entry : raw) {
            String[] pair = entry.split(":");
            try { log.merge(Integer.parseInt(pair[0]), Integer.parseInt(pair[1]), Integer::sum); } catch (RuntimeException skipped) { /* a bad line is dropped */ }
        }
        return log;
    }

    private int logServed(AccountId owner, int who) {
        int[] times = {0};
        plugin.playerData().update(owner, yaml -> {
            Map<Integer, Integer> log = readLog(yaml.getStringList(path + ".log"));
            times[0] = log.merge(who, 1, Integer::sum);
            yaml.set(path + ".log", log.entrySet().stream().map(e -> e.getKey() + ":" + e.getValue()).toList());
        });
        return times[0];
    }

    private Set<String> strings(AccountId owner, String key) {
        return plugin.playerData().read(owner, yaml -> new LinkedHashSet<>(yaml.getStringList(path + "." + key)));
    }

    private boolean addString(AccountId owner, String key, String value) {
        boolean[] added = {false};
        plugin.playerData().update(owner, yaml -> {
            Set<String> set = new LinkedHashSet<>(yaml.getStringList(path + "." + key));
            added[0] = set.add(value);
            yaml.set(path + "." + key, List.copyOf(set));
        });
        return added[0];
    }

    private int upgrade(AccountId owner, String key) { return plugin.playerData().read(owner, yaml -> yaml.getInt(path + "." + key, 0)); }
    private void setUpgrade(AccountId owner, String key, int value) { plugin.playerData().update(owner, yaml -> yaml.set(path + "." + key, value)); }

    public int served(AccountId owner) { return read(owner).served(); }
    public int unique(AccountId owner) { return log(owner).size(); }
    /** Visitors waiting right now, and benches for them, without letting anyone new arrive. */
    public int waiting(AccountId owner) { return read(owner).guests().size(); }
    public int benches(AccountId owner) { return slotsFor(owner); }
    /** A visitor body's plate text ("Name · Rare Visitor"), or null when the entity isn't one. */
    public String label(org.bukkit.entity.Entity entity) { return crowd.label(entity); }

    int slotsFor(AccountId owner) { return slots(islands.development().progress(owner).level(), upgrade(owner, "benches"), unique(owner)); }
    long intervalFor(AccountId owner) {
        long base = interval(upgrade(owner, "swift"), unique(owner));
        return plugin.perk(net.nerdsmp.paper.calendar.WardensCouncil.Perk.OPEN_DOORS) ? Math.round(base * 0.8) : base;
    }

    // ------------------------------------------------------------------ Favour

    /** A player's Favour: the visitors' own currency, paid for accepted offers, spent at the Visitor's Stall. */
    public long favour(AccountId account) { return plugin.playerData().read(account, yaml -> yaml.getLong(favourPath, 0L)); }

    public void addFavour(AccountId account, long amount) {
        if (amount <= 0) return;
        plugin.playerData().update(account, yaml -> yaml.set(favourPath, yaml.getLong(favourPath, 0L) + amount));
    }

    /** Takes Favour if the player has enough; returns whether it was taken. */
    boolean spendFavour(AccountId account, long amount) {
        boolean[] spent = {false};
        plugin.playerData().update(account, yaml -> {
            long balance = yaml.getLong(favourPath, 0L);
            if (balance < amount) return;
            yaml.set(favourPath, balance - amount);
            spent[0] = true;
        });
        return spent[0];
    }

    // ------------------------------------------------------------------ what the co-op has opened

    /** Special visitors come on Double XP Weekends and during festivals. */
    private boolean weekend() {
        return plugin.boosters() != null && plugin.boosters().weekendMultiplier() > 1D || plugin.festivals() != null && plugin.festivals().current() != null;
    }

    /** The visitor gates this co-op has opened. Once open, a gate stays open (it's saved), even if the deed is forgotten. */
    Set<String> gates(AccountId owner) {
        Set<String> open = strings(owner, "gates");
        Set<String> wanted = new HashSet<>();
        for (Visitor visitor : VISITORS) if (!visitor.gate().isEmpty() && !open.contains(visitor.gate())) wanted.add(visitor.gate());
        if (wanted.isEmpty()) return open;
        IslandShowcase.Context context = islands.showcaseContext(owner);
        IslandService.Island island = islands.islands().owned(owner).orElse(null);
        List<AccountId> crew = new ArrayList<>();
        if (island != null) { crew.add(island.owner()); crew.addAll(island.members()); }
        for (String gate : wanted) {
            boolean reached = switch (gate.contains(":") ? gate.substring(0, gate.indexOf(':')) : gate) {
                case "chain" -> context.questChains().contains(gate.substring(gate.indexOf(':') + 1));
                case "boss" -> context.bossKills() > 0;
                case "expedition" -> !context.expeditions().isEmpty();
                case "crops" -> islands.crops() != null && crew.stream().anyMatch(member -> islands.crops().bestTier(member) >= 3);
                case "contest" -> islands.contests() != null && crew.stream().anyMatch(member -> islands.contests().medalCount(member) > 0);
                case "collector" -> plugin.itemCollections() != null && crew.stream().anyMatch(member -> plugin.itemCollections().totalTiers(member) >= net.nerdsmp.paper.collection.Collections.COLLECTOR_TIERS);
                default -> false;
            };
            if (reached) {
                addString(owner, "gates", gate);
                open.add(gate);
            }
        }
        return open;
    }

    /** The special requests this server can actually make: their meal or catalog item exists. */
    private Set<String> specials() {
        Set<String> available = new HashSet<>();
        for (Special special : SPECIALS) {
            if (special.key().startsWith("meal:")) { available.add(special.key()); continue; }
            try { if (plugin.items().itemName(new ItemId(special.key().substring(5))).isPresent()) available.add(special.key()); }
            catch (RuntimeException missing) { /* not in this server's catalog */ }
        }
        return available;
    }

    Params params(AccountId owner) {
        int level = islands.development().progress(owner).level(), stage = islands.resources().effectiveStage(owner);
        double bonus = IslandShowcase.milestones(islands.showcaseContext(owner).collected()) * IslandShowcase.VISITOR_BONUS_PER_MILESTONE;
        return new Params(level, stage, bonus, weekend(), gates(owner), specials());
    }

    /** The island's visitors now, after any that have arrived since it was last looked at. Tells online members of new ones. */
    State refresh(AccountId owner) {
        State before = read(owner);
        State after = catchUp(before, System.currentTimeMillis(), params(owner), slotsFor(owner), intervalFor(owner), owner.value().getMostSignificantBits());
        if (!after.equals(before)) {
            write(owner, after);
            List<Guest> arrived = after.guests().stream().filter(g -> !before.guests().contains(g)).toList();
            if (!arrived.isEmpty()) announce(owner, arrived);
        }
        return after;
    }

    private void announce(AccountId owner, List<Guest> arrived) {
        IslandService.Island island = islands.islands().owned(owner).orElse(null);
        if (island == null) return;
        Guest best = arrived.stream().max(java.util.Comparator.comparingInt(g -> VISITORS.get(g.who()).rarity().ordinal())).orElseThrow();
        Visitor visitor = VISITORS.get(best.who());
        boolean grand = visitor.rarity().ordinal() >= Rarity.LEGENDARY.ordinal();
        for (Player member : Bukkit.getOnlinePlayers()) {
            AccountId account = plugin.sessions().account(member.getUniqueId()).orElse(null);
            if (account == null || !island.member(account)) continue;
            String who = arrived.size() > 1 ? arrived.size() + " visitors have" : visitor.rarity().paint(Chat.escape(visitor.name())) + " <aqua>has";
            Chat.send(member, "<aqua>✦ " + who + " arrived at " + Chat.escape(island.name()) + ".</aqua>"
                + (arrived.size() == 1 ? " " + visitor.rarity().tag() : "")
                + " <gray>Meet them at your island spawn, or see</gray> " + Chat.command("island guests") + "<gray>.</gray>");
            member.playSound(member.getLocation(), grand ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_CHIME, grand ? 0.5F : 0.6F, grand ? 1.2F : 1.4F);
            if (grand) ClientFeeds.toast(member, "drop", Chat.render(visitor.rarity().paint(visitor.rarity().label() + " Visitor")),
                Chat.render("<white>" + Chat.escape(visitor.name()) + "</white> <gray>is at " + Chat.escape(island.name()) + "</gray>"));
        }
    }

    /** Once a minute: visitors arrive for every island with someone online. */
    private void tick() {
        Set<AccountId> owners = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
            if (account == null) continue;
            islands.islands().homes(account).forEach(island -> owners.add(island.owner()));
        }
        for (AccountId owner : owners) {
            try { refresh(owner); }
            catch (RuntimeException failure) { plugin.getLogger().log(Level.WARNING, "Island visitors could not refresh for " + owner, failure); }
        }
    }

    /** Two or three lines for the board over the island spawn. */
    String board(AccountId owner) {
        State state = read(owner);
        int slots = slotsFor(owner);
        StringBuilder text = new StringBuilder("<aqua><b>✦ Visitors</b> " + state.guests().size() + "/" + slots + "</aqua>");
        long wait = state.nextAt() - System.currentTimeMillis();
        text.append('\n').append(state.guests().size() >= slots ? "<gray>Every bench taken</gray>"
            : "<gray>Next in</gray> <white>" + Chat.duration(java.time.Duration.ofMillis(Math.max(0, wait))) + "</white>");
        state.guests().stream().map(g -> VISITORS.get(g.who()).rarity()).filter(r -> r.ordinal() >= Rarity.LEGENDARY.ordinal())
            .max(java.util.Comparator.comparingInt(Enum::ordinal))
            .ifPresent(r -> text.append('\n').append(r.paint("★ " + r.label() + " visitor waiting")));
        return text.toString();
    }

    // ------------------------------------------------------------------ serving

    private boolean matches(ItemStack stack, Want want) {
        if (stack == null || stack.getType().isAir()) return false;
        // Crafted bread, pumpkin pie and the like carry the kitchen's staple tags; they're still the plain item to a visitor.
        if (!want.special()) return stack.getType() == want.material() && FoodItems.plain(stack);
        if (want.item().startsWith("meal:")) {
            if (!stack.hasItemMeta()) return false;
            String dish = stack.getItemMeta().getPersistentDataContainer().get(dishKey, PersistentDataType.STRING);
            return want.item().substring(5).equals(dish);
        }
        return plugin.items().describe(stack).map(d -> d.itemId().value().equals(want.item().substring(5))).orElse(false);
    }

    private int has(Player player, Want want) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) if (matches(stack, want)) count += stack.getAmount();
        return count;
    }

    private void take(Player player, Want want) {
        int amount = want.amount();
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack stack = contents[i];
            if (!matches(stack, want)) continue;
            int used = Math.min(amount, stack.getAmount());
            stack.setAmount(stack.getAmount() - used);
            amount -= used;
            contents[i] = stack.getAmount() <= 0 ? null : stack;
        }
        player.getInventory().setStorageContents(contents);
    }

    static String wantName(Want want) {
        if (!want.special()) return name(want.material());
        return SPECIALS.stream().filter(s -> s.key().equals(want.item())).map(Special::name).findFirst()
            .orElse(net.nerdsmp.paper.presentation.VisualFormat.titleCase(want.item().substring(want.item().indexOf(':') + 1).replace('_', ' ')));
    }

    private IslandService.Island memberIsland(Player player, AccountId owner, String doing) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalArgumentException("Your account is still loading."));
        IslandService.Island island = islands.islands().owned(owner).orElseThrow(() -> new IllegalArgumentException("That island is gone."));
        if (!island.member(account)) throw new IllegalArgumentException("Only this co-op can " + doing + ".");
        return island;
    }

    Guest guest(AccountId owner, String guestId) {
        return read(owner).guests().stream().filter(g -> g.id().equals(guestId)).findFirst().orElse(null);
    }

    private void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(spill -> player.getWorld().dropItemNaturally(player.getLocation(), spill));
    }

    /** Accepts a visitor's offer: takes what they asked for and pays out. Returns whether they were served. */
    boolean serve(Player player, AccountId owner, String guestId) {
        memberIsland(player, owner, "serve its visitors");
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow();
        State state = read(owner);
        Guest guest = state.guests().stream().filter(g -> g.id().equals(guestId)).findFirst().orElse(null);
        if (guest == null) { Chat.error(player, "That visitor has already gone."); return false; }
        for (Want want : guest.wants())
            if (has(player, want) < want.amount()) {
                Chat.error(player, "You need " + want.amount() + " " + wantName(want) + " (you have " + has(player, want) + ").");
                return false;
            }
        boolean first = !log(owner).containsKey(guest.who());
        List<Guest> left = new ArrayList<>(state.guests());
        left.remove(guest);
        State next = new State(state.nextAt(), state.served() + 1, List.copyOf(left));
        write(owner, next);
        int times = logServed(owner, guest.who());
        for (Want want : guest.wants()) take(player, want);
        Visitor visitor = VISITORS.get(guest.who());
        long xp = first ? Math.round(guest.xp() * 1.5) : guest.xp();
        long marks = guest.marks();
        String bonusText = "";
        if (guest.bonus().equals("booster")) {
            if (plugin.boosters() != null) plugin.boosters().earn(player, "island-guest:" + owner.value() + ":" + guest.id());
            bonusText = " <light_purple>+1 XP Booster</light_purple>";
        } else if (!guest.bonus().isEmpty()) {
            ItemStack item = bonusItem(guest.bonus());
            if (item != null) {
                bonusText = " <light_purple>+" + item.getAmount() + "× " + bonusName(guest.bonus()) + "</light_purple>";
                give(player, item);
            } else marks += BONUS_FALLBACK_MARKS;
        }
        Set<String> paid = strings(owner, "milestones");
        long favour = Math.round(favour(visitor.rarity(), paid.contains("served:100")) * (plugin.perk(net.nerdsmp.paper.calendar.WardensCouncil.Perk.CROWNS_FAVOUR) ? 1.25 : 1.0));
        addFavour(account, favour);
        plugin.economy().credit(account, marks, new EconomyService.TransactionReason("island-guest", visitor.name() + " was served"),
                "island-guest:" + owner.value() + ":" + guest.id())
            .exceptionally(error -> { plugin.getLogger().log(Level.WARNING, "Visitor payment failed", error); return null; });
        islands.development().grant(owner, xp);
        Chat.success(player, visitor.rarity().paint(Chat.escape(visitor.name())) + " <green>accepts your offer!</green> <gold>+" + Chat.marks(marks)
            + "</gold> <aqua>+" + xp + " island XP</aqua> <yellow>+" + favour + " Favour</yellow>" + bonusText);
        if (first) Chat.send(player, "<yellow>✦ New visitor!</yellow> <gray>" + Chat.escape(visitor.name()) + " goes in your visitor log (+50% island XP for a first visit). "
            + "You've met</gray> <white>" + log(owner).size() + "/" + VISITORS.size() + "</white><gray>.</gray>");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.7F, 1.1F);
        if (visitor.rarity().ordinal() >= Rarity.LEGENDARY.ordinal()) player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.3F);
        befriend(player, account, owner, guest.who(), times);
        payMilestones(player, account, owner, next.served());
        for (IslandShowcase showcase : IslandShowcase.values())
            if (showcase.kind() == IslandShowcase.Kind.GUESTS && showcase.need() == next.served())
                Chat.send(player, "<gold>✦ New showcase:</gold> <white>" + Chat.escape(showcase.title()) + "</white> <gray>— see</gray> " + Chat.command("island showcase") + "<gray>.</gray>");
        if (plugin.skills() != null) {
            Skill skill = guest.wants().stream().anyMatch(w -> pool(Pool.MINE, 5).contains(w.material())) ? Skill.MINING : Skill.FARMING;
            plugin.skills().awardMilestone(player, skill, Math.max(10, xp / 2), "island-guest:" + owner.value() + ":" + guest.id());
        }
        crowd.depart(owner, guest.id(), true);
        if (plugin.festivals() != null) plugin.festivals().progress(player, 10);
        return true;
    }

    /** A friendship tier reached with this visitor: a new title, their warmer line and a gift, once each. */
    private void befriend(Player player, AccountId account, AccountId owner, int who, int times) {
        int tier = friendship(times);
        if (tier == 0 || friendship(times - 1) == tier || !addString(owner, "friends", who + ":" + tier)) return;
        Visitor visitor = VISITORS.get(who);
        addFavour(account, FRIEND_FAVOUR[tier - 1]);
        String gift = "<yellow>+" + FRIEND_FAVOUR[tier - 1] + " Favour</yellow>";
        if (tier >= 2) { give(player, new ItemStack(Material.NAME_TAG)); gift += " <white>+1 Name Tag</white>"; }
        if (tier == 3) {
            String bonus = bonus(new Random(owner.value().getLeastSignificantBits() ^ who), visitor.rarity().ordinal() < Rarity.RARE.ordinal() ? Rarity.LEGENDARY : visitor.rarity());
            if (bonus.isEmpty()) bonus = "tidepearl:3";
            if (bonus.equals("booster")) { if (plugin.boosters() != null) plugin.boosters().earn(player, "island-friend:" + owner.value() + ":" + who); gift += " <light_purple>+1 XP Booster</light_purple>"; }
            else { ItemStack item = bonusItem(bonus); if (item != null) { give(player, item); gift += " <light_purple>+" + item.getAmount() + "× " + bonusName(bonus) + "</light_purple>"; } }
        }
        Chat.send(player, "<gold>♥ " + Chat.escape(visitor.name()) + " is now your " + FRIEND_NAMES[tier - 1] + "!</gold> " + gift);
        Chat.send(player, visitor.rarity().paint(Chat.escape(visitor.name())) + "<white>:</white> <gray><i>\"" + Chat.escape(visitor.friendLine()) + "\"</i></gray>");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 0.8F, 1F);
    }

    private void payMilestones(Player player, AccountId account, AccountId owner, int served) {
        for (Milestone milestone : due(unique(owner), served, strings(owner, "milestones"))) {
            if (!addString(owner, "milestones", milestone.key())) continue;
            addFavour(account, milestone.favour());
            Chat.send(player, "<gold>✦ Visitor milestone:</gold> <white>" + milestone.title() + "</white> <yellow>+" + milestone.favour() + " Favour</yellow>"
                + (milestone.perk().isEmpty() ? "" : " <aqua>· " + milestone.perk() + "</aqua>"));
            ClientFeeds.toast(player, "achievement", Chat.render("<gold>Visitor Milestone</gold>"), Chat.render("<white>" + milestone.title() + "</white>"));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.1F);
        }
    }

    /** Refuses a visitor's offer: they leave with nothing, and someone else comes along. */
    void dismiss(Player player, AccountId owner, String guestId) {
        memberIsland(player, owner, "send its visitors away");
        State state = read(owner);
        List<Guest> left = new ArrayList<>(state.guests());
        Guest gone = left.stream().filter(g -> g.id().equals(guestId)).findFirst().orElse(null);
        if (gone == null) return;
        left.remove(gone);
        write(owner, new State(state.nextAt(), state.served(), List.copyOf(left)));
        Chat.info(player, Chat.escape(VISITORS.get(gone.who()).name()) + " shrugs and heads home. Another visitor will come along.");
        crowd.depart(owner, gone.id(), false);
    }

    static long rerollCost(Rarity rarity) { return 20L * (rarity.ordinal() + 1); }

    /** Pays Favour for the same visitor to make a different offer. */
    void reroll(Player player, AccountId owner, String guestId) {
        memberIsland(player, owner, "change its visitors' offers");
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow();
        State state = read(owner);
        Guest guest = state.guests().stream().filter(g -> g.id().equals(guestId)).findFirst().orElse(null);
        if (guest == null) { Chat.error(player, "That visitor has already gone."); return; }
        long cost = rerollCost(VISITORS.get(guest.who()).rarity());
        if (!spendFavour(account, cost)) { Chat.error(player, "That costs " + cost + " Favour. You have " + favour(account) + "."); return; }
        Guest rolled = offerFrom(new Random().nextLong(), guest.who(), params(owner));
        // Only the request changes: the bonus stays, or rerolling would be a cheap way to fish for boosters.
        Guest fresh = new Guest(rolled.id(), rolled.who(), rolled.wants(), rolled.marks(), rolled.xp(), guest.bonus());
        List<Guest> guests = new ArrayList<>(state.guests());
        guests.set(guests.indexOf(guest), fresh);
        write(owner, new State(state.nextAt(), state.served(), List.copyOf(guests)));
        crowd.rename(owner, guest.id(), fresh.id());
        Chat.info(player, Chat.escape(VISITORS.get(guest.who()).name()) + " thinks again. <yellow>-" + cost + " Favour</yellow>");
        offer(player, owner, fresh.id());
    }

    private ItemStack bonusItem(String bonus) {
        String[] pair = bonus.split(":");
        try {
            ItemStack item = plugin.items().create(new ItemId(pair[0]));
            if (item == null || item.getType().isAir()) return null;
            item.setAmount(pair.length > 1 ? Integer.parseInt(pair[1]) : 1);
            return item;
        } catch (RuntimeException missing) {
            return null;
        }
    }

    static String bonusName(String bonus) {
        if (bonus.equals("booster")) return "XP Booster";
        return net.nerdsmp.paper.presentation.VisualFormat.titleCase(bonus.split(":")[0].replace('_', ' '));
    }

    static String bonusLine(String bonus) {
        if (bonus.isEmpty()) return null;
        if (bonus.equals("booster")) return "<light_purple>✦ 1× XP Booster</light_purple>";
        String[] pair = bonus.split(":");
        return "<light_purple>✦ " + (pair.length > 1 ? pair[1] : "1") + "× " + bonusName(bonus) + "</light_purple>";
    }

    // ------------------------------------------------------------------ the Visitor's Stall

    /** What Favour buys. Bench and Swift are island upgrades; the rest are goods for the buyer. */
    enum StallItem {
        BENCH("Extra Visitor Bench", Material.OAK_STAIRS, "One more visitor can wait at once.", 1200),
        SWIFT("Swift Arrivals", Material.CLOCK, "Visitors come 10% sooner. Three tiers.", 300),
        LURE("Visitor Lure", Material.BELL, "The next visitor comes right now. Three a day.", 60),
        NAME_TAG("Name Tag", Material.NAME_TAG, "Name a beast on your island, or nickname gear at the forge.", 40),
        COMPOST("Compost ×8", Material.BONE_MEAL, "Ripens the crops around where it's used.", 80),
        GLOOMSTEEL("Gloomsteel Ore ×4", Material.RAW_IRON, "Smelts into Gloomsteel Ingots.", 200),
        TIDEPEARL("Tidepearl ×4", Material.HEART_OF_THE_SEA, "Smelts into Tideglass.", 200),
        BOOSTER("XP Booster", Material.EXPERIENCE_BOTTLE, "+50% XP for everyone online, for an hour.", 3000);

        final String title, about; final Material icon; final long cost;
        StallItem(String title, Material icon, String about, long cost) { this.title = title; this.icon = icon; this.about = about; this.cost = cost; }
    }

    /** What the next purchase of this item costs here; -1 when there's nothing more to buy. */
    long price(StallItem item, AccountId owner) {
        return switch (item) {
            case BENCH -> upgrade(owner, "benches") >= 1 ? -1 : item.cost;
            case SWIFT -> switch (upgrade(owner, "swift")) { case 0 -> 300; case 1 -> 900; case 2 -> 2000; default -> -1; };
            default -> item.cost;
        };
    }

    /** Lures a day per island, so Favour can't be spent fishing for Mythic visitors. */
    static final int LURES_PER_DAY = 3;
    private static final StallItem[] STALL = StallItem.values();
    private static final int[] STALL_SLOTS = {19, 20, 21, 22, 23, 24, 25, 31};

    void openStall(Player player, AccountId owner) {
        memberIsland(player, owner, "use its Visitor's Stall");
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow();
        Holder holder = new Holder(player.getUniqueId(), owner, Holder.Page.STALL);
        long balance = favour(account);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<yellow>Visitor's Stall · " + balance + " Favour</yellow>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(Material.SUNFLOWER, "<yellow>Favour: " + balance + "</yellow>", List.of(
            "<gray>Visitors pay Favour for every accepted offer.</gray>",
            "<gray>Rarer visitors, friends and milestones pay more.</gray>")));
        for (int i = 0; i < STALL.length; i++) {
            StallItem item = STALL[i];
            long price = price(item, owner);
            List<String> lore = new ArrayList<>();
            lore.add(price < 0 ? "<green>Fully upgraded.</green>" : (balance >= price ? "<yellow>" : "<red>") + price + " Favour" + (balance >= price ? "</yellow>" : "</red>"));
            lore.add("<gray>" + item.about + "</gray>");
            if (item == StallItem.SWIFT) lore.add("<gray>Tier</gray> <white>" + upgrade(owner, "swift") + "/3</white>");
            if (price >= 0) lore.add(balance >= price ? "<yellow>Click to buy.</yellow>" : "<gray>Not enough Favour yet.</gray>");
            holder.inventory.setItem(STALL_SLOTS[i], MenuKit.item(item.icon, (price < 0 ? "<green>" : "<white>") + item.title + (price < 0 ? "</green>" : "</white>"), lore));
        }
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>All visitors</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private void buy(Player player, AccountId owner, StallItem item) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow();
        long price = price(item, owner);
        if (price < 0) return;
        String today = "lures." + java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        if (item == StallItem.LURE) {
            State state = read(owner);
            if (state.guests().size() >= slotsFor(owner)) { Chat.error(player, "Every bench is taken. Serve or refuse someone first."); return; }
            if (upgrade(owner, today) >= LURES_PER_DAY) { Chat.error(player, "The visitors won't be hurried more than " + LURES_PER_DAY + " times a day."); return; }
        }
        if (!spendFavour(account, price)) { Chat.error(player, "That costs " + price + " Favour. You have " + favour(account) + "."); return; }
        switch (item) {
            case BENCH -> setUpgrade(owner, "benches", 1);
            case SWIFT -> setUpgrade(owner, "swift", upgrade(owner, "swift") + 1);
            case LURE -> {
                setUpgrade(owner, today, upgrade(owner, today) + 1);
                State state = read(owner);
                write(owner, new State(0, state.served(), state.guests()));
                refresh(owner);
            }
            case NAME_TAG -> give(player, new ItemStack(Material.NAME_TAG));
            case COMPOST -> { ItemStack compost = IslandComposter.compostItem(plugin); compost.setAmount(8); give(player, compost); }
            case GLOOMSTEEL, TIDEPEARL -> {
                ItemStack made = bonusItem((item == StallItem.GLOOMSTEEL ? "gloomsteel_ore" : "tidepearl") + ":4");
                if (made == null) { addFavour(account, price); Chat.error(player, "The stall is out of that. Your Favour is back."); return; }
                give(player, made);
            }
            case BOOSTER -> { if (plugin.boosters() != null) plugin.boosters().earn(player, "favour:" + UUID.randomUUID()); }
        }
        Chat.success(player, "Bought <white>" + item.title + "</white> for <yellow>" + price + " Favour</yellow>.");
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 0.7F, 1.1F);
        openStall(player, owner);
    }

    // ------------------------------------------------------------------ the board

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24, 25};

    void open(Player player, AccountId owner) {
        IslandService.Island island = islands.islands().owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null || !island.member(account)) throw new IllegalArgumentException("Only this co-op can see its visitors.");
        State state = refresh(owner);
        int level = islands.development().progress(owner).level(), slots = slotsFor(owner);
        Map<Integer, Integer> log = log(owner);
        Holder holder = new Holder(player.getUniqueId(), owner, Holder.Page.BOARD);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<aqua>Island Visitors · " + state.guests().size() + "/" + slots + "</aqua>"));
        MenuKit.frame(holder.inventory);
        long wait = Math.max(0, state.nextAt() - System.currentTimeMillis());
        int collected = islands.showcaseContext(owner).collected();
        holder.inventory.setItem(4, MenuKit.item(Material.BELL, "<aqua>" + Chat.escape(island.name()) + "'s visitors</aqua>", List.of(
            "<gray>Served so far:</gray> <white>" + state.served() + "</white> <gray>· Met:</gray> <white>" + log.size() + "/" + VISITORS.size() + "</white>",
            state.guests().size() >= slots ? "<gray>Every bench is taken. Serve someone to make room.</gray>"
                : "<gray>Next visitor in</gray> <white>" + Chat.duration(java.time.Duration.ofMillis(wait)) + "</white>",
            "<gray>Folk of the Reach stop by, even while you're away.</gray>",
            "<gray>Meet them at your island spawn. They pay Marks, island XP and Favour.</gray>",
            "<gray>Benches:</gray> <white>" + slots + "/" + MAX_SLOTS + "</white> <dark_gray>(island levels 10, 25, 40; the stall; met 10)</dark_gray>",
            "<gray>Showcase bonus:</gray> <gold>+" + Math.round(IslandShowcase.milestones(collected) * IslandShowcase.VISITOR_BONUS_PER_MILESTONE * 100) + "% Marks</gold>")));
        for (int i = 0; i < SLOTS.length; i++) {
            if (i >= slots) {
                holder.inventory.setItem(SLOTS[i], MenuKit.item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>Locked bench</dark_gray>",
                    List.of("<gray>More benches come with island levels,</gray>", "<gray>the Visitor's Stall and meeting visitors.</gray>")));
                continue;
            }
            if (i >= state.guests().size()) {
                holder.inventory.setItem(SLOTS[i], MenuKit.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Empty bench</gray>", List.of("<gray>Someone will be along.</gray>")));
                continue;
            }
            Guest guest = state.guests().get(i);
            Visitor visitor = VISITORS.get(guest.who());
            // What they want comes first: the companion's card view shows only the opening lines.
            List<String> lore = new ArrayList<>();
            boolean ready = true;
            for (Want want : guest.wants()) {
                int have = has(player, want);
                ready &= have >= want.amount();
                lore.add(wantLine(want, have));
            }
            int times = log.getOrDefault(guest.who(), 0);
            lore.add(visitor.rarity().tag() + (times == 0 ? " <yellow><b>NEW</b></yellow>" : friendship(times) > 0 ? " <gold>♥ " + FRIEND_NAMES[friendship(times) - 1] + "</gold>" : ""));
            lore.add("<gray>Pays</gray> <gold>" + Chat.marks(guest.marks()) + "</gold> <gray>and</gray> <aqua>" + guest.xp() + " island XP</aqua>");
            String bonus = bonusLine(guest.bonus());
            if (bonus != null) lore.add(bonus);
            lore.add("<yellow>Click to see their offer.</yellow>");
            lore.add("<dark_gray>Shift-right-click to refuse it.</dark_gray>");
            holder.inventory.setItem(SLOTS[i], MenuKit.item(visitor.icon(), visitor.rarity().paint((ready ? "✔ " : "") + Chat.escape(visitor.name())), lore));
            if (ready) holder.inventory.getItem(SLOTS[i]).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
            holder.guests[i] = guest.id();
        }
        long balance = favour(account);
        holder.inventory.setItem(37, MenuKit.item(Material.WRITABLE_BOOK, "<yellow>Visitor Log</yellow>", List.of(
            "<gray>Met</gray> <white>" + log.size() + "/" + VISITORS.size() + "</white> <gray>visitors.</gray>", "<gray>Friends, rarities and who's still to come.</gray>", "<yellow>Click to open.</yellow>")));
        long reached = MILESTONES.stream().filter(m -> strings(owner, "milestones").contains(m.key())).count();
        holder.inventory.setItem(38, MenuKit.item(Material.GOLDEN_APPLE, "<gold>Visitor Milestones</gold>", List.of(
            "<white>" + reached + "/" + MILESTONES.size() + "</white> <gray>reached.</gray>", "<gray>Favour, benches and faster arrivals.</gray>", "<yellow>Click to open.</yellow>")));
        holder.inventory.setItem(39, MenuKit.item(Material.SUNFLOWER, "<yellow>Visitor's Stall</yellow>", List.of(
            "<gray>You have</gray> <yellow>" + balance + " Favour</yellow><gray>.</gray>", "<gray>Benches, swifter visits and goods.</gray>", "<yellow>Click to open.</yellow>")));
        holder.inventory.setItem(41, MenuKit.item(Material.ITEM_FRAME, "<gold>Showcases</gold>", List.of("<gray>Serve 10, 50 and 200 visitors</gray>", "<gray>for three of them.</gray>", "<yellow>Click to open.</yellow>")));
        holder.inventory.setItem(42, MenuKit.item(Material.NETHER_STAR, "<light_purple>Visitor rarities</light_purple>", rarityLore(level)));
        holder.inventory.setItem(43, MenuKit.item(Material.COMPOSTER, "<green>Farmstead</green>", List.of(
            "<gray>Composter, crop milestones</gray>", "<gray>and farming contests.</gray>", "<yellow>Click to open.</yellow>")));
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Back to your island</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private List<String> rarityLore(int level) {
        List<String> lore = new ArrayList<>();
        boolean weekend = weekend();
        for (Rarity rarity : Rarity.values()) {
            String when = rarity == Rarity.SPECIAL ? (weekend ? "<green>visiting this weekend</green>" : "<dark_gray>only on Double XP Weekends</dark_gray>")
                : rarity.open(level, weekend) ? "<green>visiting</green>" : "<dark_gray>island level " + rarity.level + "</dark_gray>";
            lore.add(rarity.tag() + " <gray>pays ×" + rarity.pay + " ·</gray> " + when);
        }
        lore.add("<gray>Rarer visitors ask for more, pay far better</gray>");
        lore.add("<gray>and may bring a bonus item.</gray>");
        return lore;
    }

    private static String wantLine(Want want, int have) {
        boolean enough = have >= want.amount();
        return (enough ? "<green>✓ " : "<red>✗ ") + want.amount() + " " + wantName(want) + "</" + (enough ? "green" : "red") + "> <dark_gray>(" + have + ")</dark_gray>"
            + (want.special() ? " <light_purple>★</light_purple>" : "");
    }

    /** One visitor's offer, Hypixel-style: what they want, what they give, accept, refuse or ask again. */
    void offer(Player player, AccountId owner, String guestId) {
        memberIsland(player, owner, "see its visitors' offers");
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow();
        Guest guest = guest(owner, guestId);
        if (guest == null) { Chat.error(player, "That visitor has already gone."); return; }
        Visitor visitor = VISITORS.get(guest.who());
        int times = log(owner).getOrDefault(guest.who(), 0), tier = friendship(times);
        Holder holder = new Holder(player.getUniqueId(), owner, Holder.Page.OFFER);
        holder.offer = guestId;
        holder.openedAt = System.currentTimeMillis();
        holder.inventory = Bukkit.createInventory(holder, 27, Chat.render(visitor.rarity().paint(Chat.escape(visitor.name()))));
        MenuKit.frame(holder.inventory);
        String next = tier < FRIEND_AT.length ? "<gray>" + (FRIEND_AT[tier] - times) + " more visits to " + FRIEND_NAMES[tier] + ".</gray>" : "<gold>An old friend of the island.</gold>";
        holder.inventory.setItem(4, MenuKit.item(visitor.icon(), visitor.rarity().paint(Chat.escape(visitor.name())), List.of(
            visitor.rarity().tag() + " <gray>visitor</gray>" + (times == 0 ? " <yellow><b>NEW</b></yellow>" : tier > 0 ? " <gold>♥ " + FRIEND_NAMES[tier - 1] + "</gold>" : ""),
            "<gray><i>\"" + Chat.escape(tier >= 2 ? visitor.friendLine() : visitor.line()) + "\"</i></gray>",
            times == 0 ? "<gray>First visit: +50% island XP.</gray>" : "<gray>Served here</gray> <white>" + times + "</white> <gray>time" + (times == 1 ? "" : "s") + ".</gray>",
            next)));
        List<String> request = new ArrayList<>();
        boolean ready = true;
        for (Want want : guest.wants()) {
            int have = has(player, want);
            ready &= have >= want.amount();
            request.add(wantLine(want, have));
        }
        if (guest.wants().stream().anyMatch(Want::special)) request.add("<light_purple>★</light_purple> <gray>a special request: cooked, caught or refined</gray>");
        holder.inventory.setItem(10, MenuKit.item(guest.wants().getFirst().material(), "<white>Their request</white>", request));
        List<String> rewards = new ArrayList<>();
        rewards.add("<gold>" + Chat.marks(guest.marks()) + "</gold>");
        rewards.add("<aqua>" + (times == 0 ? Math.round(guest.xp() * 1.5) : guest.xp()) + " island XP</aqua> <yellow>+" + favour(visitor.rarity(), strings(owner, "milestones").contains("served:100")) + " Favour</yellow>");
        String bonus = bonusLine(guest.bonus());
        if (bonus != null) rewards.add(bonus);
        holder.inventory.setItem(12, MenuKit.item(Material.GOLD_INGOT, "<gold>Their reward</gold>", rewards));
        holder.inventory.setItem(14, MenuKit.item(ready ? Material.LIME_TERRACOTTA : Material.GRAY_TERRACOTTA, ready ? "<green>Accept offer</green>" : "<gray>Accept offer</gray>",
            List.of(ready ? "<gray>Hand over the items and get paid.</gray>" : "<red>You don't have everything yet.</red>", ready ? "<yellow>Click to accept.</yellow>" : "<gray>Bring the items, then come back.</gray>")));
        if (ready) holder.inventory.getItem(14).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        holder.inventory.setItem(16, MenuKit.item(Material.RED_TERRACOTTA, "<red>Refuse offer</red>",
            List.of("<gray>They leave with nothing,</gray>", "<gray>and someone else comes along.</gray>", "<yellow>Click to refuse.</yellow>")));
        long cost = rerollCost(visitor.rarity());
        holder.inventory.setItem(24, MenuKit.item(Material.SUNFLOWER, "<yellow>Ask for something else</yellow>",
            List.of("<yellow>" + cost + " Favour</yellow> <gray>(you have " + favour(account) + ")</gray>", "<gray>Same visitor, a new offer.</gray>", "<yellow>Click to ask.</yellow>")));
        holder.inventory.setItem(18, MenuKit.item(Material.ARROW, "<yellow>All visitors</yellow>", List.of()));
        holder.inventory.setItem(22, MenuKit.close());
        player.openInventory(holder.inventory);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 0.6F, 1.1F);
    }

    private static final int[] LOG_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    /** The visitor log: every visitor, how rare they are, friendship, and how to meet the ones still to come. */
    void openLog(Player player, AccountId owner) {
        memberIsland(player, owner, "read its visitor log");
        Map<Integer, Integer> log = log(owner);
        int level = islands.development().progress(owner).level();
        Set<String> gates = gates(owner);
        Holder holder = new Holder(player.getUniqueId(), owner, Holder.Page.LOG);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<yellow>Visitor Log · " + log.size() + "/" + VISITORS.size() + "</yellow>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(Material.WRITABLE_BOOK, "<yellow>Visitor Log</yellow>", List.of(
            "<gray>Met</gray> <white>" + log.size() + "/" + VISITORS.size() + "</white> <gray>· Served</gray> <white>" + served(owner) + "</white> <gray>times</gray>",
            "<gray>First visits pay +50% island XP. Serve someone</gray>",
            "<gray>5, 15 and 30 times to make a friend of them.</gray>")));
        List<Integer> order = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) for (int i = 0; i < VISITORS.size(); i++) if (VISITORS.get(i).rarity() == rarity) order.add(i);
        for (int n = 0; n < order.size() && n < LOG_SLOTS.length; n++) {
            int who = order.get(n);
            Visitor visitor = VISITORS.get(who);
            int times = log.getOrDefault(who, 0), tier = friendship(times);
            if (times > 0) {
                holder.inventory.setItem(LOG_SLOTS[n], MenuKit.item(visitor.icon(), visitor.rarity().paint(Chat.escape(visitor.name())), List.of(
                    "<gray>Served</gray> <white>" + times + "</white> <gray>time" + (times == 1 ? "" : "s") + "</gray>" + (tier > 0 ? " <gold>♥ " + FRIEND_NAMES[tier - 1] + "</gold>" : ""),
                    visitor.rarity().tag(),
                    "<gray><i>\"" + Chat.escape(tier >= 2 ? visitor.friendLine() : visitor.line()) + "\"</i></gray>")));
            } else {
                String when = !gateOpen(visitor, gates) ? "<gold>" + Chat.escape(visitor.hint()) + "</gold>"
                    : visitor.rarity() == Rarity.SPECIAL ? "<gray>Comes only on Double XP Weekends.</gray>"
                    : level >= visitor.rarity().level ? "<gray>Could visit any time now.</gray>" : "<gray>Visits from island level " + visitor.rarity().level + ".</gray>";
                holder.inventory.setItem(LOG_SLOTS[n], MenuKit.item(Material.GRAY_DYE, "<dark_gray>??? </dark_gray>" + visitor.rarity().tag(), List.of(when, "<dark_gray>Not met yet.</dark_gray>")));
            }
        }
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>All visitors</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    private static final int[] MILESTONE_SLOTS = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};

    void openMilestones(Player player, AccountId owner) {
        memberIsland(player, owner, "see its visitor milestones");
        int met = unique(owner), served = served(owner);
        Set<String> paid = strings(owner, "milestones");
        Holder holder = new Holder(player.getUniqueId(), owner, Holder.Page.MILESTONES);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<gold>Visitor Milestones</gold>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(Material.GOLDEN_APPLE, "<gold>Visitor Milestones</gold>", List.of(
            "<gray>Met</gray> <white>" + met + "</white> <gray>· Accepted</gray> <white>" + served + "</white>",
            "<gray>Top row: visitors met. Bottom row: offers accepted.</gray>")));
        for (int i = 0; i < MILESTONES.size(); i++) {
            Milestone m = MILESTONES.get(i);
            int have = m.met() ? met : served;
            boolean done = paid.contains(m.key()) || have >= m.need();
            List<String> lore = new ArrayList<>();
            lore.add(done ? "<green>✓ Reached</green>" : net.nerdsmp.paper.presentation.VisualFormat.progressBar(have, m.need(), 10) + " <white>" + have + "/" + m.need() + "</white>");
            lore.add("<yellow>+" + m.favour() + " Favour</yellow>" + (m.perk().isEmpty() ? "" : " <aqua>· " + m.perk() + "</aqua>"));
            holder.inventory.setItem(MILESTONE_SLOTS[i], MenuKit.item(done ? Material.LIME_DYE : m.met() ? Material.PLAYER_HEAD : Material.PAPER,
                (done ? "<green>" : "<white>") + m.title() + (done ? "</green>" : "</white>"), lore));
        }
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>All visitors</yellow>", List.of()));
        holder.inventory.setItem(49, MenuKit.close());
        player.openInventory(holder.inventory);
    }

    static String name(Material material) {
        return net.nerdsmp.paper.presentation.VisualFormat.titleCase(material.name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.viewer.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        boolean refuse = event.isShiftClick() && event.isRightClick();
        // A held mouse button from right-clicking the visitor must not land on a button of the offer it opened.
        if (holder.page == Holder.Page.OFFER && System.currentTimeMillis() - holder.openedAt < 350) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != holder.inventory) return;
            try {
                switch (holder.page) {
                    case BOARD -> {
                        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == slot && holder.guests[i] != null) {
                            if (refuse) { dismiss(player, holder.owner, holder.guests[i]); open(player, holder.owner); }
                            else offer(player, holder.owner, holder.guests[i]);
                            return;
                        }
                        if (slot == 37) openLog(player, holder.owner);
                        else if (slot == 38) openMilestones(player, holder.owner);
                        else if (slot == 39) openStall(player, holder.owner);
                        else if (slot == 41) player.performCommand("island showcase");
                        else if (slot == 43) player.performCommand("island farm");
                        else if (slot == 45) player.performCommand("island");
                        else if (slot == 49) player.closeInventory();
                    }
                    case OFFER -> {
                        if (slot == 14) { if (serve(player, holder.owner, holder.offer)) player.closeInventory(); else offer(player, holder.owner, holder.offer); }
                        else if (slot == 16) { dismiss(player, holder.owner, holder.offer); player.closeInventory(); }
                        else if (slot == 24) reroll(player, holder.owner, holder.offer);
                        else if (slot == 18) open(player, holder.owner);
                        else if (slot == 22) player.closeInventory();
                    }
                    case STALL -> {
                        for (int i = 0; i < STALL.length; i++) if (STALL_SLOTS[i] == slot) { buy(player, holder.owner, STALL[i]); return; }
                        if (slot == 45) open(player, holder.owner);
                        else if (slot == 49) player.closeInventory();
                    }
                    case LOG, MILESTONES -> {
                        if (slot == 45) open(player, holder.owner);
                        else if (slot == 49) player.closeInventory();
                    }
                }
            } catch (IllegalArgumentException refused) { Chat.error(player, Chat.escape(refused.getMessage())); }
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true); }

    @Override public void close() {
        if (task != null) task.cancel();
        crowd.close();
    }

    private static final class Holder implements InventoryHolder {
        enum Page { BOARD, OFFER, LOG, STALL, MILESTONES }
        final UUID viewer; final AccountId owner; final Page page; final String[] guests = new String[SLOTS.length]; String offer; long openedAt; Inventory inventory;
        Holder(UUID viewer, AccountId owner, Page page) { this.viewer = viewer; this.owner = owner; this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }
}

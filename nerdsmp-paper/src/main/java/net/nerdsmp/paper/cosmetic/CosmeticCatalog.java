package net.nerdsmp.paper.cosmetic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every cosmetic Aerborne offers, matching the website's Companions page and store.
 *
 * <p>Store purchases arrive as entitlements named after the store item: {@code cosmetic.companion.pufflet},
 * {@code cosmetic.trail.cloudstep}, {@code cosmetic.rank.starweaver}, {@code cosmetic.title.skyborne},
 * {@code cosmetic.aura.glimmer-halo}, {@code cosmetic.finisher.starburst} and {@code cosmetic.bundle.cloudberry}.
 * Cosmetics earned in play are granted the same way, under the same ids. Nothing here changes gameplay.
 * Pure data so it can be tested without a server.
 */
public final class CosmeticCatalog {
    public static final String COMPANION = "cosmetic.companion.";
    public static final String TRAIL = "cosmetic.trail.";
    public static final String RANK = "cosmetic.rank.";
    public static final String BUNDLE = "cosmetic.bundle.";
    public static final String TITLE = "cosmetic.title.";
    public static final String AURA = "cosmetic.aura.";
    public static final String FINISHER = "cosmetic.finisher.";
    /** The older subscription products. They carry every supporter perk, shown with the classic gold star. */
    public static final Set<String> LEGACY_SUPPORTER = Set.of("supporter_monthly", "supporter_annual");

    /**
     * A companion, built as a block sculpture from companion-models.yml.
     *
     * @param model    the sculpture's name in companion-models.yml
     * @param scale    overall size; 1 = 16 pixels per block
     * @param flying   hovers beside the owner instead of walking behind
     * @param particle ambient particle name, or empty
     * @param free     owned by everyone without a purchase
     * @param source   where a companion that isn't sold comes from, or null for the store
     */
    public record Companion(String slug, String name, String species, String model, double scale,
                            boolean flying, String particle, String color, boolean free, String source) {
        public Companion(String slug, String name, String species, String model, double scale,
                         boolean flying, String particle, String color, boolean free) {
            this(slug, name, species, model, scale, flying, particle, color, free, null);
        }

        public String product() { return COMPANION + slug; }
    }

    /** A companion that isn't sold stays a secret until someone has it. */
    public static boolean hidden(Companion companion, Ownership own) {
        return companion.source() != null && !own.companions().contains(companion.slug());
    }

    /** A particle trail. {@code pattern} picks the shape; see {@link TrailPatterns}. */
    public record Trail(String slug, String name, String summary, String pattern, String particle, String color, boolean free) {
        public String product() { return TRAIL + slug; }
    }

    /**
     * A supporter tier. Higher tiers include everything below them.
     *
     * @param level 1 = Skyfarer, 2 = Cloudkeeper, 3 = Starweaver
     * @param palette name colours the member can choose from (Adventure named colours)
     */
    public record Tier(String slug, String name, int level, String badge, String color, List<String> palette,
                       boolean emotes, boolean presets, boolean keepsake) {
        public String product() { return RANK + slug; }
    }

    public record Bundle(String slug, String name, List<String> contents) {
        public String product() { return BUNDLE + slug; }
    }

    public record Emote(String id, String name, String action, String particle) {}

    /**
     * A title worn after the name in chat and the tab list.
     *
     * @param earned what earns it, as an {@link #earnedBy} key, or null when it's sold
     * @param how    how to get it, in words, for the wardrobe
     */
    public record Title(String slug, String name, String color, String earned, String how) {
        public String product() { return TITLE + slug; }
    }

    /** A particle aura round a player standing still. {@code pattern} picks the shape; see {@link AuraPatterns}. */
    public record Aura(String slug, String name, String summary, String pattern, String particle, String color, String earned, String how) {
        public String product() { return AURA + slug; }
    }

    /** A burst where a player's kill falls. {@code pattern} picks the shape; see {@link AuraPatterns#finisher}. */
    public record Finisher(String slug, String name, String summary, String pattern, String particle, String color, String earned, String how) {
        public String product() { return FINISHER + slug; }
    }

    /** What a player owns once their entitlements are read. */
    public record Ownership(Set<String> companions, Set<String> trails, Optional<Tier> tier,
                            Set<String> titles, Set<String> auras, Set<String> finishers) {
        public static final Ownership NONE = new Ownership(Set.of(), Set.of(), Optional.empty());

        public Ownership(Set<String> companions, Set<String> trails, Optional<Tier> tier) {
            this(companions, trails, tier, Set.of(), Set.of(), Set.of());
        }
        public boolean supporter() { return tier.isPresent(); }
        public boolean emotes() { return tier.map(Tier::emotes).orElse(false); }
        public boolean presets() { return tier.map(Tier::presets).orElse(false); }
        public boolean keepsake() { return tier.map(Tier::keepsake).orElse(false); }
        public List<String> palette() { return tier.map(Tier::palette).orElse(List.of()); }
    }

    private final Map<String, Companion> companions = new LinkedHashMap<>();
    private final Map<String, Trail> trails = new LinkedHashMap<>();
    private final Map<String, Tier> tiers = new LinkedHashMap<>();
    private final Map<String, Bundle> bundles = new LinkedHashMap<>();
    private final Map<String, Emote> emotes = new LinkedHashMap<>();
    private final Map<String, Title> titles = new LinkedHashMap<>();
    private final Map<String, Aura> auras = new LinkedHashMap<>();
    private final Map<String, Finisher> finishers = new LinkedHashMap<>();
    private final Tier legacy;

    public CosmeticCatalog(Collection<Companion> companions, Collection<Trail> trails, Collection<Tier> tiers,
                           Collection<Bundle> bundles, Collection<Emote> emotes) {
        this(companions, trails, tiers, bundles, emotes, List.of(), List.of(), List.of());
    }

    public CosmeticCatalog(Collection<Companion> companions, Collection<Trail> trails, Collection<Tier> tiers,
                           Collection<Bundle> bundles, Collection<Emote> emotes,
                           Collection<Title> titles, Collection<Aura> auras, Collection<Finisher> finishers) {
        titles.forEach(t -> this.titles.put(t.slug(), t));
        auras.forEach(a -> this.auras.put(a.slug(), a));
        finishers.forEach(f -> this.finishers.put(f.slug(), f));
        companions.forEach(c -> this.companions.put(c.slug(), c));
        trails.forEach(t -> this.trails.put(t.slug(), t));
        tiers.forEach(t -> this.tiers.put(t.slug(), t));
        bundles.forEach(b -> this.bundles.put(b.slug(), b));
        emotes.forEach(e -> this.emotes.put(e.id(), e));
        Tier top = this.tiers.values().stream().max(java.util.Comparator.comparingInt(Tier::level)).orElse(null);
        legacy = top == null ? null : new Tier("supporter", "Supporter", top.level(), "<gold>✦</gold>", "gold",
            top.palette(), top.emotes(), top.presets(), top.keepsake());
        for (Bundle bundle : this.bundles.values()) {
            for (String product : bundle.contents()) {
                if (!knownSingle(product)) throw new IllegalArgumentException("Bundle " + bundle.slug() + " includes unknown " + product);
            }
        }
    }

    /** The catalog the website describes. Server owners change looks and bundles in config.yml. */
    public static CosmeticCatalog defaults() {
        return new CosmeticCatalog(
            List.of(
                new Companion("pufflet", "Pufflet", "Cloud puff", "pufflet", 1.0D, true, "CLOUD", "white", false),
                new Companion("kitekit", "Kitekit", "Sky fox", "kitekit", 1.0D, false, "", "aqua", false),
                new Companion("mossbun", "Mossbun", "Moss bunny", "mossbun", 1.0D, false, "HAPPY_VILLAGER", "green", false),
                new Companion("cindlet", "Cindlet", "Ember sprite", "cindlet", 1.0D, true, "SMALL_FLAME", "gold", false),
                new Companion("moonmoth", "Moonmoth", "Moon moth", "moonmoth", 1.0D, true, "END_ROD", "light_purple", false),
                new Companion("starwhisk", "Starwhisk", "Star cat", "starwhisk", 1.0D, false, "WAX_OFF", "yellow", false),
                new Companion("lilypip", "Lilypip", "Lily frog", "lilypip", 0.85D, false, "DRIPPING_WATER", "green", false),
                new Companion("rattlebones", "Rattlebones", "Barrow skeleton", "rattlebones", 0.9D, false, "", "gray", false, "The Warden of Bones, very rarely"),
                new Companion("weepling", "Weepling", "Grieving wisp", "weepling", 0.9D, true, "SOUL", "dark_aqua", false, "The Keening Widow, very rarely"),
                new Companion("hollow-hound", "Hollow Hound", "Huntsman's hound", "hollowhound", 1.0D, false, "SOUL_FIRE_FLAME", "dark_red", false, "The Hollow Huntsman, very rarely"),
                new Companion("silkling", "Silkling", "Nest spiderling", "silkling", 0.9D, false, "", "white", false, "The Briar Matron, very rarely"),
                new Companion("thistlewing", "Thistlewing", "Ranger's hawk", "thistlewing", 1.0D, true, "", "dark_green", false, "Verrin, very rarely"),
                new Companion("sproutling", "Sproutling", "Heart Tree seedling", "sproutling", 0.9D, false, "SPORE_BLOSSOM_AIR", "green", false, "The Thornheart Shaman, very rarely"),
                new Companion("windpip", "Windpip", "Tame Cloudling", "windpip", 0.9D, true, "CLOUD", "aqua", false, "The Old Sentinel, very rarely"),
                new Companion("pipling", "Pipling", "A spare part of Old Bess", "pipling", 0.9D, false, "CLOUD", "gold", false, "Old Bess, very rarely"),
                new Companion("siltpup", "Siltpup", "Fresh from the mud", "siltpup", 0.9D, false, "", "dark_green", false, "The Silt Mother, very rarely"),
                new Companion("tidelet", "Tidelet", "A small, cold wave", "tidelet", 0.8D, true, "SPLASH", "aqua", false, "The Tidewarden, very rarely"),
                new Companion("drakeling", "Drakeling", "Hatched in the bridge's cables", "drakeling", 0.8D, true, "SMALL_FLAME", "red", false, "Cindermaw, very rarely"),
                new Companion("ashling", "Ashling", "A very small Sovereign", "ashling", 0.8D, false, "SMALL_FLAME", "gold", false, "Pyrrhax, very rarely"),
                new Companion("cogling", "Cogling", "Runaway mine engine", "cogling", 0.9D, false, "", "gold", false, "The Blastmaster, very rarely"),
                new Companion("embermutt", "Embermutt", "Slag hound pup", "embermutt", 0.9D, false, "SMALL_FLAME", "red", false, "The Slag Hound, very rarely"),
                new Companion("lampwick", "Lampwick", "The last miner's lamp", "lampwick", 0.8D, true, "", "yellow", false, "The Deep Foreman, very rarely"),
                new Companion("smeltling", "Smeltling", "A spark of the Colossus", "smeltling", 0.9D, false, "SMALL_FLAME", "gold", false, "Something in the Foundry, very rarely"),
                new Companion("kilnling", "Kilnling", "A brazier that followed you", "kilnling", 0.9D, false, "SMALL_FLAME", "gold", false, "The Ember Warden, very rarely"),
                new Companion("rimepup", "Rimepup", "Runt of the Warden's pack", "rimepup", 0.9D, false, "SNOWFLAKE", "aqua", false, "The Frost Warden, very rarely"),
                new Companion("pebblewarden", "Pebblewarden", "A very small colossus", "pebblewarden", 0.8D, false, "", "green", false, "The Stone Warden, very rarely"),
                new Companion("inkling", "Inkling", "A blot that got off the page", "inkling", 0.8D, true, "REVERSE_PORTAL", "dark_purple", false, "Oriveth, very rarely"),
                new Companion("glimmerbee", "Glimmerbee", "Lantern bee", "glimmerbee", 0.9D, true, "WAX_ON", "yellow", false),
                new Companion("bramblehog", "Bramblehog", "Hedge hedgehog", "bramblehog", 0.9D, false, "", "gold", false),
                new Companion("driftfin", "Driftfin", "Sky koi", "driftfin", 1.0D, true, "BUBBLE_POP", "red", false)),
            List.of(
                new Trail("cloudstep", "Cloudstep", "Small puffs of cloud with every step.", "steps", "CLOUD", "white", false),
                new Trail("petal-skip", "Petal Skip", "Petals that skip along behind you.", "scatter", "CHERRY_LEAVES", "light_purple", false),
                new Trail("firefly-drift", "Firefly Drift", "A few fireflies that follow you at dusk.", "orbit", "FIREFLY", "yellow", false),
                new Trail("rune-ripple", "Rune Ripple", "Faint arcane runes that ripple out as you walk.", "ring", "ENCHANT", "light_purple", false),
                new Trail("bubblehop", "Bubblehop", "Bubbles that bob up and pop.", "rise", "BUBBLE_POP", "aqua", false),
                new Trail("comet-tail", "Comet Tail", "A streak of starlight behind you.", "streak", "END_ROD", "white", false),
                new Trail("dandelion-drift", "Dandelion Drift", "Dandelion seeds carried on the breeze.", "drift", "WHITE_ASH", "white", false),
                new Trail("stardust-waltz", "Stardust Waltz", "Stardust that swirls when you turn.", "swirl", "WAX_ON", "gold", false),
                new Trail("snowdrift", "Snowdrift", "A soft fall of snow wherever you go.", "drift", "SNOWFLAKE", "white", false),
                new Trail("ember-wake", "Ember Wake", "A wake of embers off your heels.", "streak", "SMALL_FLAME", "gold", false),
                new Trail("glowspore", "Glowspore", "Glowing spores that drift up behind you.", "rise", "GLOW", "green", false)),
            List.of(
                new Tier("skyfarer", "Skyfarer", 1, "<aqua>✦</aqua>", "aqua", List.of("aqua", "green", "yellow"),
                    false, false, false),
                new Tier("cloudkeeper", "Cloudkeeper", 2, "<dark_aqua>☁</dark_aqua>", "dark_aqua",
                    List.of("aqua", "green", "yellow", "dark_aqua", "blue"), true, false, false),
                new Tier("starweaver", "Starweaver", 3, "<light_purple>✧</light_purple>", "light_purple",
                    List.of("aqua", "green", "yellow", "dark_aqua", "blue", "light_purple", "gold"), true, true, true)),
            List.of(
                new Bundle("cloudberry", "Cloudberry", List.of(COMPANION + "pufflet", TRAIL + "cloudstep", RANK + "skyfarer")),
                new Bundle("blooming-skies", "Blooming Skies", List.of(COMPANION + "mossbun", TRAIL + "petal-skip", RANK + "skyfarer")),
                new Bundle("celestial-dreams", "Celestial Dreams", List.of(COMPANION + "starwhisk", TRAIL + "stardust-waltz", RANK + "skyfarer"))),
            List.of(
                new Emote("wave", "Wave", "waves", "WAX_OFF"),
                new Emote("cheer", "Cheer", "cheers", "HAPPY_VILLAGER"),
                new Emote("heart", "Heart", "sends some love", "HEART"),
                new Emote("bow", "Bow", "takes a bow", "END_ROD"),
                new Emote("dance", "Dance", "dances", "NOTE"),
                new Emote("sparkle", "Sparkle", "sparkles", "ELECTRIC_SPARK")),
            List.of(
                new Title("skyborne", "Skyborne", "aqua", null, "From the store"),
                new Title("stormchaser", "Stormchaser", "dark_aqua", null, "From the store"),
                new Title("starlit", "Starlit", "light_purple", null, "From the store"),
                new Title("windrider", "Windrider", "white", null, "From the store"),
                new Title("fledgling", "Fledgling", "gray", "expedition:first_flight", "Clear First Flight"),
                new Title("barrow-breaker", "Barrow Breaker", "dark_green", "expedition:hollow_barrow", "Clear the Hollow Barrow"),
                new Title("canopy-ranger", "Canopy Ranger", "green", "expedition:thornwood_canopy", "Clear Thornwood Canopy"),
                new Title("floodwalker", "Floodwalker", "aqua", "expedition:sunken_aqueduct", "Clear the Sunken Aqueduct"),
                new Title("deep-delver", "Deep Delver", "gold", "expedition:gloomforge_mines", "Clear Gloomforge Mines"),
                new Title("seal-breaker", "Seal Breaker", "dark_purple", "expedition:hall_of_wardens", "Clear the Hall of the Three Wardens"),
                new Title("sovereigns-bane", "Sovereign's Bane", "red", "expedition:cinder_keep", "Defeat Pyrrhax in Cinder Keep"),
                new Title("keystone-master", "Keystone Master", "gold", "tier:mythic", "Clear any expedition on Mythic")),
            List.of(
                new Aura("glimmer-halo", "Glimmer Halo", "A faint halo of light over your head.", "halo", "END_ROD", "white", null, "From the store"),
                new Aura("petal-orbit", "Petal Orbit", "Petals that circle you slowly.", "orbit", "CHERRY_LEAVES", "light_purple", null, "From the store"),
                new Aura("snow-globe", "Snow Globe", "Your own little flurry.", "flurry", "SNOWFLAKE", "white", null, "From the store"),
                new Aura("void-motes", "Void Motes", "Specks of the Unwritten that won't let you go.", "orbit", "REVERSE_PORTAL", "dark_purple", "expedition:hall_of_wardens:heroic", "Clear the Hall of the Three Wardens on Heroic or Mythic"),
                new Aura("sovereigns-embers", "Sovereign's Embers", "Embers off the Molten Heart, still rising.", "rise", "SMALL_FLAME", "gold", "expedition:cinder_keep:heroic", "Clear Cinder Keep on Heroic or Mythic")),
            List.of(
                new Finisher("starburst", "Starburst", "A burst of sparks where your foe falls.", "burst", "FIREWORK", "yellow", null, "From the store"),
                new Finisher("confetti-pop", "Confetti Pop", "A celebration, whether they wanted one or not.", "fountain", "TOTEM_OF_UNDYING", "green", null, "From the store"),
                new Finisher("soul-release", "Soul Release", "The fallen rise as a column of soul fire.", "column", "SOUL_FIRE_FLAME", "aqua", null, "From the store"),
                new Finisher("sovereigns-wrath", "Sovereign's Wrath", "Your kills go up the way the Heart did.", "column", "LAVA", "red", "expedition:cinder_keep:mythic", "Clear Cinder Keep on Mythic")));
    }

    /** What a run's clear earns: {@code expedition:<id>}, {@code expedition:<id>:<tier>} (Heroic counts for Heroic and up), and {@code tier:<tier>}. */
    public static List<String> clearKeys(String expedition, String tier) {
        List<String> keys = new ArrayList<>(List.of("expedition:" + expedition, "tier:" + tier));
        if (tier.equals("heroic") || tier.equals("mythic")) keys.add("expedition:" + expedition + ":heroic");
        if (tier.equals("mythic")) keys.add("expedition:" + expedition + ":mythic");
        return keys;
    }

    /** Every product an earned key unlocks. */
    public List<String> earnedBy(Collection<String> keys) {
        List<String> out = new ArrayList<>();
        for (Title title : titles.values()) if (title.earned() != null && keys.contains(title.earned())) out.add(title.product());
        for (Aura aura : auras.values()) if (aura.earned() != null && keys.contains(aura.earned())) out.add(aura.product());
        for (Finisher finisher : finishers.values()) if (finisher.earned() != null && keys.contains(finisher.earned())) out.add(finisher.product());
        return out;
    }

    public Collection<Companion> companions() { return companions.values(); }
    /** Upgrade only the former two-item defaults; preserve genuinely customised bundle lists. */
    static List<String> configuredBundleContents(Bundle defaults, List<String> configured) {
        List<String> former = defaults.contents().stream().filter(id -> !id.equals(RANK + "skyfarer")).toList();
        if (configured.size() == former.size() && new LinkedHashSet<>(configured).equals(new LinkedHashSet<>(former))) {
            return defaults.contents();
        }
        return List.copyOf(configured);
    }

    public Collection<Trail> trails() { return trails.values(); }
    public Collection<Tier> tiers() { return tiers.values(); }
    public Collection<Bundle> bundles() { return bundles.values(); }
    public Collection<Emote> emotes() { return emotes.values(); }
    public Optional<Companion> companion(String slug) { return Optional.ofNullable(companions.get(normalise(slug))); }
    public Optional<Trail> trail(String slug) { return Optional.ofNullable(trails.get(normalise(slug))); }
    public Optional<Tier> tier(String slug) { return Optional.ofNullable(tiers.get(normalise(slug))); }
    public Optional<Emote> emote(String id) { return Optional.ofNullable(emotes.get(normalise(id))); }
    public Collection<Title> titles() { return titles.values(); }
    public Collection<Aura> auras() { return auras.values(); }
    public Collection<Finisher> finishers() { return finishers.values(); }
    public Optional<Title> title(String slug) { return Optional.ofNullable(titles.get(normalise(slug))); }
    public Optional<Aura> aura(String slug) { return Optional.ofNullable(auras.get(normalise(slug))); }
    public Optional<Finisher> finisher(String slug) { return Optional.ofNullable(finishers.get(normalise(slug))); }

    /** True for every product the store may grant: the listed cosmetics, bundles, tiers and the old subscriptions. */
    public boolean known(String product) {
        String id = product == null ? "" : product.toLowerCase(Locale.ROOT);
        return LEGACY_SUPPORTER.contains(id) || knownSingle(id)
            || id.startsWith(BUNDLE) && bundles.containsKey(id.substring(BUNDLE.length()));
    }

    private boolean knownSingle(String id) {
        return id.startsWith(COMPANION) && companions.containsKey(id.substring(COMPANION.length()))
            || id.startsWith(TRAIL) && trails.containsKey(id.substring(TRAIL.length()))
            || id.startsWith(RANK) && tiers.containsKey(id.substring(RANK.length()))
            || id.startsWith(TITLE) && titles.containsKey(id.substring(TITLE.length()))
            || id.startsWith(AURA) && auras.containsKey(id.substring(AURA.length()))
            || id.startsWith(FINISHER) && finishers.containsKey(id.substring(FINISHER.length()));
    }

    /** Works out what a set of active entitlements unlocks, adding the free cosmetics. */
    public Ownership ownership(Collection<String> products) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String raw : products) {
            String id = raw.toLowerCase(Locale.ROOT);
            expanded.add(id);
            if (id.startsWith(BUNDLE)) {
                Bundle bundle = bundles.get(id.substring(BUNDLE.length()));
                if (bundle != null) expanded.addAll(bundle.contents());
            }
        }
        Set<String> ownedCompanions = new LinkedHashSet<>();
        for (Companion companion : companions.values()) {
            if (companion.free() || expanded.contains(companion.product())) ownedCompanions.add(companion.slug());
        }
        Set<String> ownedTrails = new LinkedHashSet<>();
        for (Trail trail : trails.values()) {
            if (trail.free() || expanded.contains(trail.product())) ownedTrails.add(trail.slug());
        }
        Tier best = null;
        for (Tier tier : tiers.values()) {
            if (expanded.contains(tier.product()) && (best == null || tier.level() > best.level())) best = tier;
        }
        if (best == null && legacy != null && expanded.stream().anyMatch(LEGACY_SUPPORTER::contains)) best = legacy;
        Set<String> ownedTitles = new LinkedHashSet<>(), ownedAuras = new LinkedHashSet<>(), ownedFinishers = new LinkedHashSet<>();
        for (Title title : titles.values()) if (expanded.contains(title.product())) ownedTitles.add(title.slug());
        for (Aura aura : auras.values()) if (expanded.contains(aura.product())) ownedAuras.add(aura.slug());
        for (Finisher finisher : finishers.values()) if (expanded.contains(finisher.product())) ownedFinishers.add(finisher.slug());
        return new Ownership(Set.copyOf(ownedCompanions), Set.copyOf(ownedTrails), Optional.ofNullable(best),
            Set.copyOf(ownedTitles), Set.copyOf(ownedAuras), Set.copyOf(ownedFinishers));
    }

    /** Readable name for a product id, for receipts and the wardrobe. */
    public String describe(String product) {
        String id = product.toLowerCase(Locale.ROOT);
        if (LEGACY_SUPPORTER.contains(id)) return "Supporter";
        if (id.startsWith(COMPANION)) return companion(id.substring(COMPANION.length())).map(c -> c.name() + " companion").orElse(id);
        if (id.startsWith(TRAIL)) return trail(id.substring(TRAIL.length())).map(t -> t.name() + " trail").orElse(id);
        if (id.startsWith(RANK)) return tier(id.substring(RANK.length())).map(t -> t.name() + " rank").orElse(id);
        if (id.startsWith(TITLE)) return title(id.substring(TITLE.length())).map(t -> t.name() + " title").orElse(id);
        if (id.startsWith(AURA)) return aura(id.substring(AURA.length())).map(a -> a.name() + " aura").orElse(id);
        if (id.startsWith(FINISHER)) return finisher(id.substring(FINISHER.length())).map(f -> f.name() + " finisher").orElse(id);
        if (id.startsWith(BUNDLE)) {
            Bundle bundle = bundles.get(id.substring(BUNDLE.length()));
            return bundle == null ? id : bundle.name() + " bundle";
        }
        return id;
    }

    public List<String> companionSlugs() { return new ArrayList<>(companions.keySet()); }
    public List<String> trailSlugs() { return new ArrayList<>(trails.keySet()); }
    public List<String> emoteIds() { return new ArrayList<>(emotes.keySet()); }

    static String normalise(String slug) {
        return slug == null ? "" : slug.toLowerCase(Locale.ROOT).trim().replace(' ', '-').replace('_', '-');
    }
}

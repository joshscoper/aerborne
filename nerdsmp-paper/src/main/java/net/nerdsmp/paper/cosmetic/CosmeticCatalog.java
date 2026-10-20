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
 * {@code cosmetic.trail.cloudstep}, {@code cosmetic.rank.starweaver} and {@code cosmetic.bundle.cloudberry}.
 * Nothing here changes gameplay. Pure data so it can be tested without a server.
 */
public final class CosmeticCatalog {
    public static final String COMPANION = "cosmetic.companion.";
    public static final String TRAIL = "cosmetic.trail.";
    public static final String RANK = "cosmetic.rank.";
    public static final String BUNDLE = "cosmetic.bundle.";
    /** The older subscription products. They carry every supporter perk, shown with the classic gold star. */
    public static final Set<String> LEGACY_SUPPORTER = Set.of("supporter_monthly", "supporter_annual");

    /**
     * A companion and the vanilla creature that plays it until custom models exist.
     *
     * @param entity  Bukkit entity type name, e.g. {@code FOX}
     * @param variant entity variant where the type has one (fox type, cat type, rabbit type), or empty
     * @param baby    show the young form
     * @param flying  hovers beside the owner instead of walking behind
     * @param particle ambient particle name, or empty
     * @param free    owned by everyone without a purchase
     */
    public record Companion(String slug, String name, String species, String entity, String variant, boolean baby,
                            boolean flying, String particle, String color, boolean free) {
        public String product() { return COMPANION + slug; }
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

    /** What a player owns once their entitlements are read. */
    public record Ownership(Set<String> companions, Set<String> trails, Optional<Tier> tier) {
        public static final Ownership NONE = new Ownership(Set.of(), Set.of(), Optional.empty());
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
    private final Tier legacy;

    public CosmeticCatalog(Collection<Companion> companions, Collection<Trail> trails, Collection<Tier> tiers,
                           Collection<Bundle> bundles, Collection<Emote> emotes) {
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
                new Companion("pufflet", "Pufflet", "Cloud puff", "HAPPY_GHAST", "", true, true, "CLOUD", "white", false),
                new Companion("kitekit", "Kitekit", "Sky fox", "FOX", "SNOW", true, false, "", "aqua", false),
                new Companion("mossbun", "Mossbun", "Moss bunny", "RABBIT", "BROWN", true, false, "HAPPY_VILLAGER", "green", false),
                new Companion("cindlet", "Cindlet", "Ember sprite", "FOX", "RED", true, false, "SMALL_FLAME", "gold", false),
                new Companion("moonmoth", "Moonmoth", "Moon moth", "ALLAY", "", false, true, "END_ROD", "light_purple", false),
                new Companion("starwhisk", "Starwhisk", "Star cat", "CAT", "ALL_BLACK", true, false, "WAX_OFF", "yellow", false)),
            List.of(
                new Trail("cloudstep", "Cloudstep", "Small puffs of cloud with every step.", "steps", "CLOUD", "white", false),
                new Trail("petal-skip", "Petal Skip", "Petals that skip along behind you.", "scatter", "CHERRY_LEAVES", "light_purple", false),
                new Trail("firefly-drift", "Firefly Drift", "A few fireflies that follow you at dusk.", "orbit", "FIREFLY", "yellow", false),
                new Trail("rune-ripple", "Rune Ripple", "Faint arcane runes that ripple out as you walk.", "ring", "ENCHANT", "light_purple", false),
                new Trail("bubblehop", "Bubblehop", "Bubbles that bob up and pop.", "rise", "BUBBLE_POP", "aqua", false),
                new Trail("comet-tail", "Comet Tail", "A streak of starlight behind you.", "streak", "END_ROD", "white", false),
                new Trail("dandelion-drift", "Dandelion Drift", "Dandelion seeds carried on the breeze.", "drift", "WHITE_ASH", "white", false),
                new Trail("stardust-waltz", "Stardust Waltz", "Stardust that swirls when you turn.", "swirl", "WAX_ON", "gold", false)),
            List.of(
                new Tier("skyfarer", "Skyfarer", 1, "<aqua>✦</aqua>", "aqua", List.of("aqua", "green", "yellow"),
                    false, false, false),
                new Tier("cloudkeeper", "Cloudkeeper", 2, "<dark_aqua>☁</dark_aqua>", "dark_aqua",
                    List.of("aqua", "green", "yellow", "dark_aqua", "blue"), true, false, false),
                new Tier("starweaver", "Starweaver", 3, "<light_purple>✧</light_purple>", "light_purple",
                    List.of("aqua", "green", "yellow", "dark_aqua", "blue", "light_purple", "gold"), true, true, true)),
            List.of(
                new Bundle("cloudberry", "Cloudberry", List.of(COMPANION + "pufflet", TRAIL + "cloudstep")),
                new Bundle("blooming-skies", "Blooming Skies", List.of(COMPANION + "mossbun", TRAIL + "petal-skip")),
                new Bundle("celestial-dreams", "Celestial Dreams", List.of(COMPANION + "starwhisk", TRAIL + "stardust-waltz"))),
            List.of(
                new Emote("wave", "Wave", "waves", "WAX_OFF"),
                new Emote("cheer", "Cheer", "cheers", "HAPPY_VILLAGER"),
                new Emote("heart", "Heart", "sends some love", "HEART"),
                new Emote("bow", "Bow", "takes a bow", "END_ROD"),
                new Emote("dance", "Dance", "dances", "NOTE"),
                new Emote("sparkle", "Sparkle", "sparkles", "ELECTRIC_SPARK")));
    }

    public Collection<Companion> companions() { return companions.values(); }
    public Collection<Trail> trails() { return trails.values(); }
    public Collection<Tier> tiers() { return tiers.values(); }
    public Collection<Bundle> bundles() { return bundles.values(); }
    public Collection<Emote> emotes() { return emotes.values(); }
    public Optional<Companion> companion(String slug) { return Optional.ofNullable(companions.get(normalise(slug))); }
    public Optional<Trail> trail(String slug) { return Optional.ofNullable(trails.get(normalise(slug))); }
    public Optional<Tier> tier(String slug) { return Optional.ofNullable(tiers.get(normalise(slug))); }
    public Optional<Emote> emote(String id) { return Optional.ofNullable(emotes.get(normalise(id))); }

    /** True for every product the store may grant: the listed cosmetics, bundles, tiers and the old subscriptions. */
    public boolean known(String product) {
        String id = product == null ? "" : product.toLowerCase(Locale.ROOT);
        return LEGACY_SUPPORTER.contains(id) || knownSingle(id)
            || id.startsWith(BUNDLE) && bundles.containsKey(id.substring(BUNDLE.length()));
    }

    private boolean knownSingle(String id) {
        return id.startsWith(COMPANION) && companions.containsKey(id.substring(COMPANION.length()))
            || id.startsWith(TRAIL) && trails.containsKey(id.substring(TRAIL.length()))
            || id.startsWith(RANK) && tiers.containsKey(id.substring(RANK.length()));
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
        return new Ownership(Set.copyOf(ownedCompanions), Set.copyOf(ownedTrails), Optional.ofNullable(best));
    }

    /** Readable name for a product id, for receipts and the wardrobe. */
    public String describe(String product) {
        String id = product.toLowerCase(Locale.ROOT);
        if (LEGACY_SUPPORTER.contains(id)) return "Supporter";
        if (id.startsWith(COMPANION)) return companion(id.substring(COMPANION.length())).map(c -> c.name() + " companion").orElse(id);
        if (id.startsWith(TRAIL)) return trail(id.substring(TRAIL.length())).map(t -> t.name() + " trail").orElse(id);
        if (id.startsWith(RANK)) return tier(id.substring(RANK.length())).map(t -> t.name() + " rank").orElse(id);
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

package net.nerdsmp.paper.forge.arcane;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One validated, immutable snapshot of the {@code arcane-forge} config section. A reload builds a
 * complete new snapshot and only replaces the live one if {@link #errors()} is empty, so gameplay
 * never sees a half-read config. Core progression is available by default. Runtime effects and
 * signatures remain separately gated; an operator's explicit disabled switch is always respected.
 */
public record ArcaneSettings(
    boolean enabled,
    boolean codex,
    Research research,
    Imbuement imbuement,
    Capacity capacity,
    Overcharge overcharge,
    Signatures signatures,
    boolean anticheatBridge,
    Experience experience,
    Effects effects,
    Corruption corruption,
    Attunement attunement,
    List<String> errors
) {
    public ArcaneSettings {
        errors = List.copyOf(errors);
    }

    public record Research(
        boolean enabled,
        int unlockLevel,
        int specializedLevel,
        int signatureLevel,
        int dustPerReagent,
        Map<ArcaneTier, Integer> progress,
        int partialGain,
        int partialCap,
        int clueThreshold,
        List<Double> gemTierMultipliers,
        double prismaticMultiplier,
        int fruitlessRepeats,
        Map<ArcaneTier, Long> discoveryExperience,
        long clueExperience,
        int masteryBinds
    ) {
        public Research {
            progress = Map.copyOf(progress);
            gemTierMultipliers = List.copyOf(gemTierMultipliers);
            discoveryExperience = Map.copyOf(discoveryExperience);
        }

        public double gemMultiplier(int tier) {
            return gemTierMultipliers.get(Math.max(1, Math.min(gemTierMultipliers.size(), tier)) - 1);
        }

        public int requiredLevel(ArcaneTier tier) {
            return switch (tier) {
                case FUNDAMENTAL -> unlockLevel;
                case SPECIALIZED -> specializedLevel;
                case SIGNATURE -> signatureLevel;
            };
        }
    }

    public record Imbuement(
        boolean enabled,
        List<Integer> levelRequirements,
        Map<ArcaneTier, Integer> dustPerLevel,
        int signatureDust,
        int signatureGemTier,
        int masteryDiscountPercent
    ) {
        public Imbuement {
            levelRequirements = List.copyOf(levelRequirements);
            dustPerLevel = Map.copyOf(dustPerLevel);
        }
    }

    public record Capacity(
        Map<String, Integer> byRarity,
        int fallback,
        int perQualityTier,
        int itemLevelDivisor,
        int fundamentalPerLevel,
        int specializedPerLevel,
        int signatureCost
    ) {
        public Capacity {
            byRarity = Map.copyOf(byRarity);
        }
    }

    /** Enchanting XP per action. Research XP is under {@code research}. */
    public record Experience(int tablePerDust, int tomePerLevel, int imbuePerDust, int signatureBind, int corruption) {
    }

    /** Switches and bounds for the three effect enchantments. */
    public record Effects(boolean bloodletter, boolean veinseeker, boolean chainHarvest, int bleedSeconds,
                          boolean bleedPvp, int maximumBleeds, int blocksPerTick, int maximumJobs, int maximumChain) {
        public boolean enabled(net.nerdsmp.paper.forge.ArcaneEnchantment enchantment) {
            return switch (enchantment) {
                case BLOODLETTER -> bloodletter;
                case VEINSEEKER -> veinseeker;
                case CHAIN_HARVEST -> chainHarvest;
                default -> true;
            };
        }
    }

    public record Corruption(boolean enabled, int requiredLevel, int dust, int cleanseDust, int capacityCost,
                             int durabilityMultiplier) {
    }

    public record Attunement(boolean enabled, int requiredLevel, int dust, int bonusPercent, int signatureCooldownPercent) {
    }

    public record Overcharge(boolean enabled, int maximumPercent, int requiredLevel, int stabilizeDustPerPoint,
                             int cooldownPercentPerPoint) {
    }

    public record Signatures(boolean enabled, Map<ArcaneSignature, SignatureSettings> each) {
        public Signatures {
            each = Map.copyOf(each);
        }

        public boolean active(ArcaneSignature signature) {
            return enabled && each.get(signature).enabled();
        }

        public SignatureSettings of(ArcaneSignature signature) {
            return each.get(signature);
        }
    }

    /** Numbers shared by all three signatures; each reads only the ones it uses. */
    public record SignatureSettings(
        boolean enabled,
        long cooldownSeconds,
        int threshold,
        int targets,
        double range,
        double damagePercent,
        double falloff,
        boolean pvp,
        long chargeDecaySeconds,
        int particles,
        int absorptionHearts,
        int protectionSeconds
    ) {
    }

    public static ArcaneSettings defaults() {
        return from(new YamlConfiguration());
    }

    /** Reads {@code arcane-forge} from a root config, filling every missing key with its default. */
    public static ArcaneSettings from(ConfigurationSection root) {
        ConfigurationSection section = root.getConfigurationSection("arcane-forge");
        if (section == null) section = new YamlConfiguration();
        List<String> errors = new ArrayList<>();
        Reader read = new Reader(section, errors);

        Map<ArcaneTier, Integer> progress = new EnumMap<>(ArcaneTier.class);
        progress.put(ArcaneTier.FUNDAMENTAL, read.integer("research.progress.fundamental", 100, 1, 100));
        progress.put(ArcaneTier.SPECIALIZED, read.integer("research.progress.specialized", 50, 1, 100));
        progress.put(ArcaneTier.SIGNATURE, read.integer("research.progress.signature", 25, 1, 100));
        Map<ArcaneTier, Long> experience = new EnumMap<>(ArcaneTier.class);
        experience.put(ArcaneTier.FUNDAMENTAL, (long) read.integer("research.discovery-xp.fundamental", 40, 0, 5_000));
        experience.put(ArcaneTier.SPECIALIZED, (long) read.integer("research.discovery-xp.specialized", 80, 0, 5_000));
        experience.put(ArcaneTier.SIGNATURE, (long) read.integer("research.discovery-xp.signature", 200, 0, 5_000));
        List<Double> multipliers = read.doubles("research.gem-tier-multipliers", List.of(1.0D, 1.25D, 1.5D, 2.0D), 4, 0.1D, 10.0D);
        int partialCap = read.integer("research.partial-progress-cap", 60, 0, 99);
        Research research = new Research(
            read.bool("research.enabled", true),
            read.integer("research.unlock-level", 5, 0, 50),
            read.integer("research.specialized-level", 10, 0, 50),
            read.integer("research.signature-level", 30, 0, 50),
            read.integer("research.dust-per-reagent", 4, 0, 64),
            progress,
            read.integer("research.partial-progress", 10, 0, 100),
            partialCap,
            read.integer("research.clue-threshold", 30, 1, 100),
            multipliers,
            read.decimal("research.prismatic-multiplier", 1.5D, 1.0D, 10.0D),
            read.integer("research.fruitless-repeats", 1, 1, 10),
            experience,
            read.integer("research.clue-xp", 10, 0, 1_000),
            read.integer("research.mastery-binds", 3, 1, 100)
        );

        Map<ArcaneTier, Integer> dust = new EnumMap<>(ArcaneTier.class);
        dust.put(ArcaneTier.FUNDAMENTAL, read.integer("imbuement.dust-per-level.fundamental", 6, 1, 640));
        dust.put(ArcaneTier.SPECIALIZED, read.integer("imbuement.dust-per-level.specialized", 8, 1, 640));
        dust.put(ArcaneTier.SIGNATURE, 0);
        List<Integer> levels = read.integers("imbuement.level-requirements", List.of(0, 0, 10, 25, 40), 5, 0, 50);
        for (int index = 1; index < levels.size(); index++) {
            if (levels.get(index) < levels.get(index - 1)) {
                errors.add("imbuement.level-requirements must not decrease");
                levels = List.of(0, 0, 10, 25, 40);
                break;
            }
        }
        Imbuement imbuement = new Imbuement(
            read.bool("imbuement.enabled", true),
            levels,
            dust,
            read.integer("imbuement.signature-dust", 64, 0, 2_304),
            read.integer("imbuement.signature-gem-tier", 3, 1, 4),
            read.integer("imbuement.mastery-discount-percent", 25, 0, 90)
        );

        Map<String, Integer> rarity = new LinkedHashMap<>();
        Map<String, Integer> rarityDefaults = new LinkedHashMap<>();
        rarityDefaults.put("STARTER", 6);
        rarityDefaults.put("COMMON", 10);
        rarityDefaults.put("CRAFTED", 10);
        rarityDefaults.put("UNCOMMON", 14);
        rarityDefaults.put("RARE", 18);
        rarityDefaults.put("EPIC", 22);
        rarityDefaults.put("RELIC", 22);
        rarityDefaults.put("LEGENDARY", 26);
        rarityDefaults.put("MYTHIC", 30);
        rarityDefaults.forEach((key, fallback) ->
            rarity.put(key, read.integer("capacity.by-rarity." + key.toLowerCase(Locale.ROOT), fallback, 0, 500)));
        Capacity capacity = new Capacity(
            rarity,
            read.integer("capacity.unknown-rarity", 10, 0, 500),
            read.integer("capacity.per-quality-tier", 2, 0, 100),
            read.integer("capacity.item-levels-per-point", 10, 1, 1_000),
            read.integer("capacity.cost.fundamental-per-level", 1, 0, 100),
            read.integer("capacity.cost.specialized-per-level", 2, 0, 100),
            read.integer("capacity.cost.signature", 12, 0, 500)
        );

        Overcharge overcharge = new Overcharge(
            read.bool("overcharge.enabled", true),
            read.integer("overcharge.maximum-percent", 25, 0, 100),
            read.integer("overcharge.level", 20, 0, 50),
            read.integer("overcharge.stabilize-dust-per-point", 4, 0, 640),
            read.integer("overcharge.signature-cooldown-percent-per-point", 5, 0, 100)
        );

        Map<ArcaneSignature, SignatureSettings> each = new EnumMap<>(ArcaneSignature.class);
        each.put(ArcaneSignature.STORMWAKE, signature(read, ArcaneSignature.STORMWAKE, 8, 5, 3, 4.0D, 40.0D, 0.7D, 8, 12, 0, 0));
        each.put(ArcaneSignature.HEART_OF_THE_MOUNTAIN, signature(read, ArcaneSignature.HEART_OF_THE_MOUNTAIN, 0, 48, 0, 0, 0, 0, 0, 16, 0, 0));
        each.put(ArcaneSignature.LAST_BASTION, signature(read, ArcaneSignature.LAST_BASTION, 600, 0, 0, 0, 0, 0, 0, 24, 4, 4));
        Signatures signatures = new Signatures(read.bool("signatures.enabled", false), each);

        Experience enchantingXp = new Experience(
            read.integer("enchanting-xp.table-per-dust", 3, 0, 100),
            read.integer("enchanting-xp.tome-per-level", 15, 0, 1_000),
            read.integer("enchanting-xp.imbue-per-dust", 1, 0, 100),
            read.integer("enchanting-xp.signature-bind", 150, 0, 5_000),
            read.integer("enchanting-xp.corruption", 40, 0, 5_000)
        );
        Effects effects = new Effects(
            read.bool("effects.bloodletter.enabled", false),
            read.bool("effects.veinseeker.enabled", false),
            read.bool("effects.chain-harvest.enabled", false),
            read.integer("effects.bloodletter.duration-seconds", 3, 1, 10),
            read.bool("effects.bloodletter.pvp", false),
            read.integer("effects.bloodletter.maximum-bleeding", 256, 1, 4_096),
            read.integer("effects.chain.blocks-per-tick", 8, 1, 32),
            read.integer("effects.chain.maximum-active", 16, 1, 64),
            read.integer("effects.chain.maximum-blocks", 10, 1, 32)
        );
        Corruption corruption = new Corruption(
            read.bool("corruption.enabled", true),
            read.integer("corruption.level", 25, 0, 50),
            read.integer("corruption.dust", 48, 0, 2_304),
            read.integer("corruption.cleanse-dust", 24, 0, 2_304),
            read.integer("corruption.capacity-cost", 8, 0, 100),
            read.integer("corruption.durability-multiplier", 2, 1, 5)
        );
        Attunement attunement = new Attunement(
            read.bool("attunement.enabled", true),
            read.integer("attunement.level", 15, 0, 50),
            read.integer("attunement.dust", 16, 0, 2_304),
            read.integer("attunement.bonus-percent", 10, 0, 25),
            read.integer("attunement.signature-cooldown-percent", 15, 0, 50)
        );

        return new ArcaneSettings(
            read.bool("enabled", true),
            read.bool("codex.enabled", true),
            research,
            imbuement,
            capacity,
            overcharge,
            signatures,
            read.bool("anticheat-bridge.enabled", true),
            enchantingXp,
            effects,
            corruption,
            attunement,
            errors
        );
    }

    private static SignatureSettings signature(Reader read, ArcaneSignature signature, long cooldown, int threshold,
                                               int targets, double range, double damage, double falloff,
                                               long decay, int particles, int absorption, int protection) {
        String base = "signatures." + signature.configKey() + ".";
        return new SignatureSettings(
            read.bool(base + "enabled", false),
            read.integer(base + "cooldown-seconds", (int) cooldown, 0, 86_400),
            read.integer(base + "threshold", threshold, 0, 10_000),
            read.integer(base + "maximum-targets", targets, 0, 8),
            read.decimal(base + "range-blocks", range, 0.0D, 8.0D),
            read.decimal(base + "damage-percent", damage, 0.0D, 200.0D),
            read.decimal(base + "falloff", falloff, 0.0D, 1.0D),
            read.bool(base + "pvp", false),
            read.integer(base + "charge-decay-seconds", (int) decay, 0, 600),
            read.integer(base + "particles", particles, 0, 64),
            read.integer(base + "absorption-hearts", absorption, 0, 20),
            read.integer(base + "protection-seconds", protection, 0, 30)
        );
    }

    /** Typed, bounded reads that record an error and fall back instead of throwing. */
    private record Reader(ConfigurationSection section, List<String> errors) {
        boolean bool(String path, boolean fallback) {
            if (!section.contains(path)) return fallback;
            if (!section.isBoolean(path)) {
                errors.add("arcane-forge." + path + " must be true or false");
                return false; // A malformed switch must never activate paid gameplay work.
            }
            return section.getBoolean(path);
        }

        int integer(String path, int fallback, int minimum, int maximum) {
            if (!section.contains(path)) return fallback;
            if (!section.isInt(path)) {
                errors.add("arcane-forge." + path + " must be a whole number");
                return fallback;
            }
            int value = section.getInt(path);
            if (value < minimum || value > maximum) {
                errors.add("arcane-forge." + path + " must be between " + minimum + " and " + maximum);
                return fallback;
            }
            return value;
        }

        double decimal(String path, double fallback, double minimum, double maximum) {
            if (!section.contains(path)) return fallback;
            if (!section.isDouble(path) && !section.isInt(path)) {
                errors.add("arcane-forge." + path + " must be a number");
                return fallback;
            }
            double value = section.getDouble(path);
            if (!Double.isFinite(value) || value < minimum || value > maximum) {
                errors.add("arcane-forge." + path + " must be between " + minimum + " and " + maximum);
                return fallback;
            }
            return value;
        }

        List<Integer> integers(String path, List<Integer> fallback, int size, int minimum, int maximum) {
            if (!section.contains(path)) return fallback;
            List<?> raw = section.getList(path);
            if (raw == null || raw.size() != size || raw.stream().anyMatch(value -> !(value instanceof Integer number)
                || number < minimum || number > maximum)) {
                errors.add("arcane-forge." + path + " must list " + size + " whole numbers between " + minimum + " and " + maximum);
                return fallback;
            }
            return raw.stream().map(Integer.class::cast).toList();
        }

        List<Double> doubles(String path, List<Double> fallback, int size, double minimum, double maximum) {
            if (!section.contains(path)) return fallback;
            List<?> raw = section.getList(path);
            if (raw == null || raw.size() != size || raw.stream().anyMatch(value -> !(value instanceof Number number)
                || !Double.isFinite(number.doubleValue()) || number.doubleValue() < minimum || number.doubleValue() > maximum)) {
                errors.add("arcane-forge." + path + " must list " + size + " numbers between " + minimum + " and " + maximum);
                return fallback;
            }
            return raw.stream().map(value -> ((Number) value).doubleValue()).toList();
        }
    }
}

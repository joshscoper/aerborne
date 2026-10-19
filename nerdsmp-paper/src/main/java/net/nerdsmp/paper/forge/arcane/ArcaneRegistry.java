package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.ArcaneEnchantment;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every researchable definition, keyed by stable id and by its exact Aspect set. Each Aspect set
 * resolves to at most one definition, so an experiment always has a deterministic answer.
 *
 * <p>The twelve existing enchantments keep their ids, levels and stats; this registry only adds a
 * Codex classification. The three Arcane Signatures are new.
 */
public final class ArcaneRegistry {
    /** Names for two-Aspect disciplines, used for research clues. Pairs without a name are simply "an unnamed discipline". */
    private static final Map<String, String> DISCIPLINES = Map.ofEntries(
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.MIGHT, ArcaneAspect.PRECISION)), "Rending"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.MIGHT, ArcaneAspect.CELERITY)), "Momentum"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.MIGHT, ArcaneAspect.VITALITY)), "Bloodbond"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.AEGIS, ArcaneAspect.VITALITY)), "Fortification"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.WARD, ArcaneAspect.AFFINITY)), "Spellguard"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.FORTUNE, ArcaneAspect.AFFINITY)), "Resonance"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.CELERITY, ArcaneAspect.FORTUNE)), "Tidecall"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.FORTUNE, ArcaneAspect.RESOLVE)), "Delving"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.AEGIS, ArcaneAspect.RESOLVE)), "Bulwark"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.CELERITY, ArcaneAspect.AFFINITY)), "Stormcall"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.RESOLVE, ArcaneAspect.AFFINITY)), "Earthsong"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.PRECISION, ArcaneAspect.VITALITY)), "Hemorrhage"),
        Map.entry(ArcaneAspect.key(EnumSet.of(ArcaneAspect.VITALITY, ArcaneAspect.CELERITY)), "Quickening")
    );

    private final Map<String, ArcaneDefinition> byId;
    private final Map<String, ArcaneDefinition> byAspects;

    public ArcaneRegistry(Collection<ArcaneDefinition> definitions) {
        List<String> errors = validate(definitions);
        if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid Arcane registry: " + String.join("; ", errors));
        Map<String, ArcaneDefinition> ids = new LinkedHashMap<>();
        Map<String, ArcaneDefinition> keys = new HashMap<>();
        for (ArcaneDefinition definition : definitions) {
            ids.put(definition.id(), definition);
            keys.put(definition.aspectKey(), definition);
        }
        byId = java.util.Collections.unmodifiableMap(ids);
        byAspects = Map.copyOf(keys);
    }

    public static ArcaneRegistry defaults() {
        return new ArcaneRegistry(defaultDefinitions());
    }

    public Collection<ArcaneDefinition> all() {
        return byId.values();
    }

    public Optional<ArcaneDefinition> byId(String id) {
        return Optional.ofNullable(id == null ? null : byId.get(id));
    }

    public ArcaneDefinition of(ArcaneEnchantment enchantment) {
        return byId.get(enchantment.id());
    }

    public ArcaneDefinition of(ArcaneSignature signature) {
        return byId.get(signature.id());
    }

    /** The one definition with exactly these Aspects, if any. */
    public Optional<ArcaneDefinition> exact(Set<ArcaneAspect> aspects) {
        return Optional.ofNullable(byAspects.get(ArcaneAspect.key(aspects)));
    }

    /** Definitions that contain every one of these Aspects and at least one more. */
    public List<ArcaneDefinition> supersets(Set<ArcaneAspect> aspects) {
        return byId.values().stream()
            .filter(definition -> definition.aspects().size() > aspects.size() && definition.aspects().containsAll(aspects))
            .toList();
    }

    public List<ArcaneDefinition> tier(ArcaneTier tier) {
        return byId.values().stream().filter(definition -> definition.tier() == tier).toList();
    }

    public static Optional<String> discipline(Set<ArcaneAspect> aspects) {
        return Optional.ofNullable(aspects.size() == 2 ? DISCIPLINES.get(ArcaneAspect.key(aspects)) : null);
    }

    /** Every problem with a set of definitions; empty when it is safe to publish. */
    public static List<String> validate(Collection<ArcaneDefinition> definitions) {
        List<String> errors = new ArrayList<>();
        Map<String, String> ids = new HashMap<>();
        Map<String, String> keys = new HashMap<>();
        for (ArcaneDefinition definition : definitions) {
            String id = definition.id();
            if (id == null || !id.matches("[a-z][a-z0-9_]{1,47}")) errors.add("invalid id '" + id + "'");
            if (ids.put(id, id) != null) errors.add("duplicate id " + id);
            if (definition.aspects().size() != definition.tier().aspectCount()) {
                errors.add(id + " has " + definition.aspects().size() + " Aspects but tier " + definition.tier().id()
                    + " needs " + definition.tier().aspectCount());
            }
            String previous = keys.put(definition.aspectKey(), id);
            if (previous != null) errors.add("ambiguous recipe: " + previous + " and " + id + " share " + definition.aspectKey());
            if (definition.isSignature() ? definition.maximumLevel() != 1 : definition.maximumLevel() < 1
                || definition.maximumLevel() > 10) errors.add(id + " has an invalid maximum level");
            if (definition.isSignature() != (definition.tier() == ArcaneTier.SIGNATURE)) {
                errors.add(id + " tier does not match its kind");
            }
            if (definition.enchantment() != null && (!definition.enchantment().id().equals(id)
                || definition.enchantment().maximumLevel() != definition.maximumLevel())) {
                errors.add(id + " does not match its enchantment");
            }
            if (definition.signature() != null && !definition.signature().id().equals(id)) {
                errors.add(id + " does not match its signature");
            }
            if (definition.displayName() == null || definition.displayName().isBlank()) errors.add(id + " has no name");
        }
        return List.copyOf(errors);
    }

    static List<ArcaneDefinition> defaultDefinitions() {
        List<ArcaneDefinition> definitions = new ArrayList<>();
        add(definitions, ArcaneEnchantment.KEEN_EDGE, "Melee weapons", Material.IRON_SWORD, ArcaneAspect.MIGHT);
        add(definitions, ArcaneEnchantment.MARKSMAN, "Bows and crossbows", Material.BOW, ArcaneAspect.PRECISION);
        add(definitions, ArcaneEnchantment.WARDING, "Armor and shields", Material.IRON_CHESTPLATE, ArcaneAspect.AEGIS);
        add(definitions, ArcaneEnchantment.PRESERVATION, "Anything with durability", Material.ANVIL, ArcaneAspect.RESOLVE);
        add(definitions, ArcaneEnchantment.REAPING, "Hoes and harvest tools", Material.IRON_HOE, ArcaneAspect.VITALITY);
        add(definitions, ArcaneEnchantment.SCHOLAR, "Any equipment", Material.BOOK, ArcaneAspect.AFFINITY);
        add(definitions, ArcaneEnchantment.PROSPECTOR, "Pickaxes and mining tools", Material.IRON_PICKAXE, ArcaneAspect.FORTUNE);
        add(definitions, ArcaneEnchantment.EXECUTIONER, "Weapons", Material.IRON_AXE, ArcaneAspect.MIGHT, ArcaneAspect.PRECISION);
        add(definitions, ArcaneEnchantment.SOULSIPHON, "Weapons", Material.GHAST_TEAR, ArcaneAspect.MIGHT, ArcaneAspect.VITALITY);
        add(definitions, ArcaneEnchantment.VENOMBOUND, "Weapons", Material.SPIDER_EYE, ArcaneAspect.WARD, ArcaneAspect.AFFINITY);
        add(definitions, ArcaneEnchantment.ANGLER, "Fishing rods", Material.FISHING_ROD, ArcaneAspect.CELERITY, ArcaneAspect.FORTUNE);
        add(definitions, ArcaneEnchantment.ARCHAEOLOGIST, "Shovels and brushes", Material.BRUSH, ArcaneAspect.FORTUNE, ArcaneAspect.RESOLVE);
        add(definitions, ArcaneEnchantment.BLOODLETTER, "Melee weapons", Material.REDSTONE, ArcaneAspect.PRECISION, ArcaneAspect.VITALITY);
        add(definitions, ArcaneEnchantment.VEINSEEKER, "Pickaxes", Material.RAW_IRON, ArcaneAspect.RESOLVE, ArcaneAspect.AFFINITY);
        add(definitions, ArcaneEnchantment.CHAIN_HARVEST, "Hoes and harvest tools", Material.WHEAT, ArcaneAspect.VITALITY, ArcaneAspect.CELERITY);
        for (ArcaneSignature signature : ArcaneSignature.values()) {
            definitions.add(new ArcaneDefinition(signature.id(), signature.displayName(), ArcaneTier.SIGNATURE,
                signature.aspects(), 1, signature.summary(), signature.equipment(), null, signature.icon(), null, signature));
        }
        return List.copyOf(definitions);
    }

    private static void add(List<ArcaneDefinition> definitions, ArcaneEnchantment enchantment, String equipment,
                            Material icon, ArcaneAspect first, ArcaneAspect... rest) {
        Set<ArcaneAspect> aspects = EnumSet.of(first, rest);
        ArcaneTier tier = ArcaneTier.forAspectCount(aspects.size());
        definitions.add(new ArcaneDefinition(enchantment.id(), enchantment.displayName(), tier, aspects,
            enchantment.maximumLevel(), enchantment.description(), equipment, discipline(aspects).orElse(null), icon,
            enchantment, null));
    }
}

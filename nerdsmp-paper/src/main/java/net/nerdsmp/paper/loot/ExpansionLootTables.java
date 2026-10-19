package net.nerdsmp.paper.loot;

import net.nerdsmp.api.model.ItemId;
import org.bukkit.configuration.ConfigurationSection;
import java.util.*;

/** One bounded optional gear roll; rarity weights are conditional on the initial drop roll succeeding. */
public record ExpansionLootTables(boolean enabled, double unidentifiedChance, Map<Source, Rule> rules) {
    public enum Source { STANDARD, ELITE, BOSS, CHEST }
    public enum Rarity { UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC }
    public record Rule(double chance, Map<Rarity, Double> weights) {
        public Rule {
            probability(chance);weights=Map.copyOf(weights);
            if (!weights.keySet().equals(EnumSet.allOf(Rarity.class))
                || weights.values().stream().anyMatch(value -> !Double.isFinite(value) || value < 0 || value > 100)
                || weights.values().stream().mapToDouble(Double::doubleValue).sum() <= 0)
                throw new IllegalArgumentException("Invalid expansion rarity weights");
        }
    }
    public ExpansionLootTables {
        probability(unidentifiedChance);rules=Map.copyOf(rules);
        if (!rules.keySet().equals(EnumSet.allOf(Source.class))) throw new IllegalArgumentException("Missing expansion loot source");
    }
    public static ExpansionLootTables read(ConfigurationSection yaml) {
        if (!yaml.isInt("schema-version") || yaml.getInt("schema-version") != 1 || !yaml.isBoolean("enabled"))
            throw new IllegalArgumentException("Invalid skyforge-loot schema");
        Map<Source,Rule> rules=new EnumMap<>(Source.class);
        for (Source source:Source.values()) {
            String path="tables."+source.name().toLowerCase(Locale.ROOT);
            Map<Rarity,Double> weights=new EnumMap<>(Rarity.class);
            for (Rarity rarity:Rarity.values()) weights.put(rarity,number(yaml,path+".weights."+rarity.name().toLowerCase(Locale.ROOT)));
            rules.put(source,new Rule(number(yaml,path+".chance"),weights));
        }
        return new ExpansionLootTables(yaml.getBoolean("enabled"),number(yaml,"unidentified-chance"),rules);
    }
    private static double number(ConfigurationSection section,String path) {
        Object value=section.get(path);
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Missing or nonnumeric expansion loot field: "+path);
        return number.doubleValue();
    }
    private static void probability(double value) {
        if (!Double.isFinite(value)||value<0||value>1) throw new IllegalArgumentException("Invalid expansion drop probability");
    }
    private static void roll(double value) {
        if (!Double.isFinite(value)||value<0||value>=1) throw new IllegalArgumentException("Invalid random roll");
    }
    public void validatePools(Map<Rarity,List<ItemId>> pools) {
        if (!enabled) return;
        for (var rule:rules.values()) for (var weight:rule.weights.entrySet())
            if (rule.chance>0&&weight.getValue()>0&&pools.getOrDefault(weight.getKey(),List.of()).isEmpty())
                throw new IllegalArgumentException("No drop-only expansion items for "+weight.getKey());
    }
    public Optional<ItemId> select(Source source,double chanceRoll,double rarityRoll,double itemRoll,Map<Rarity,List<ItemId>> pools) {
        roll(chanceRoll);roll(rarityRoll);roll(itemRoll);
        Rule rule=rules.get(Objects.requireNonNull(source));
        if (!enabled||chanceRoll>=rule.chance) return Optional.empty();
        double target=rarityRoll*rule.weights.values().stream().mapToDouble(Double::doubleValue).sum();
        Rarity selected=null;
        for (Rarity rarity:Rarity.values()) {
            double weight=rule.weights.get(rarity);if (weight<=0) continue;
            selected=rarity;if (target<weight) break;target-=weight;
        }
        List<ItemId> pool=pools.getOrDefault(selected,List.of());
        if (pool.isEmpty()) throw new IllegalStateException("Expansion drop pool is empty: "+selected);
        return Optional.of(pool.get(Math.min(pool.size()-1,(int)(itemRoll*pool.size()))));
    }
}

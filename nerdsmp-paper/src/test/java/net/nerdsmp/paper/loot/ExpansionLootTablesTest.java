package net.nerdsmp.paper.loot;

import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.paper.loot.ExpansionLootTables.Rarity;
import net.nerdsmp.paper.loot.ExpansionLootTables.Rule;
import net.nerdsmp.paper.loot.ExpansionLootTables.Source;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ExpansionLootTablesTest {
    private static final Path CONTENT = Path.of("src/main/resources/content");

    private YamlConfiguration yaml() throws Exception {
        var yaml = new YamlConfiguration();
        yaml.loadFromString(Files.readString(CONTENT.resolve("skyforge-loot.yml")));
        return yaml;
    }

    private Map<Rarity, Double> weights(double uncommon, double rare, double epic, double legendary, double mythic) {
        return Map.of(Rarity.UNCOMMON, uncommon, Rarity.RARE, rare, Rarity.EPIC, epic,
            Rarity.LEGENDARY, legendary, Rarity.MYTHIC, mythic);
    }

    private ExpansionLootTables tables(boolean enabled, double chance, Map<Rarity, Double> weights) {
        var rule = new Rule(chance, weights);
        Map<Source, Rule> rules = new EnumMap<>(Source.class);
        for (Source source : Source.values()) rules.put(source, rule);
        return new ExpansionLootTables(enabled, 0.8, rules);
    }

    private Map<Rarity, List<ItemId>> pools() {
        Map<Rarity, List<ItemId>> pools = new EnumMap<>(Rarity.class);
        for (Rarity rarity : Rarity.values()) pools.put(rarity,
            List.of(new ItemId(rarity.name().toLowerCase(java.util.Locale.ROOT) + "_item")));
        return pools;
    }

    @Test void bundledYamlDefinesEverySourceAndEveryRarityWithNumericProbabilities() throws Exception {
        var tables = ExpansionLootTables.read(yaml());
        assertTrue(tables.enabled());
        assertEquals(0.8, tables.unidentifiedChance());
        assertEquals(java.util.Set.of(Source.values()), tables.rules().keySet());
        for (Source source : Source.values()) {
            Rule rule = tables.rules().get(source);
            assertEquals(java.util.Set.of(Rarity.values()), rule.weights().keySet());
            assertEquals(100.0, rule.weights().values().stream().mapToDouble(Double::doubleValue).sum(), 1.0e-12);
        }
        tables.validatePools(pools());
    }

    @Test void yamlRejectsWrongSchemaBooleanMissingAndStringEncodedNumbers() throws Exception {
        for (Object invalid : List.of(0, 2, "1", true)) {
            var yaml = yaml(); yaml.set("schema-version", invalid);
            assertThrows(IllegalArgumentException.class, () -> ExpansionLootTables.read(yaml), "schema=" + invalid);
        }
        for (Object invalid : List.of("true", 1)) {
            var yaml = yaml(); yaml.set("enabled", invalid);
            assertThrows(IllegalArgumentException.class, () -> ExpansionLootTables.read(yaml));
        }
        for (String path : List.of("enabled", "unidentified-chance", "tables.standard.chance", "tables.elite.weights.epic")) {
            var missing = yaml(); missing.set(path, null);
            assertThrows(IllegalArgumentException.class, () -> ExpansionLootTables.read(missing), "missing " + path);
        }
        for (String path : List.of("unidentified-chance", "tables.chest.chance", "tables.boss.weights.mythic")) {
            var string = yaml(); string.set(path, "0.5");
            assertThrows(IllegalArgumentException.class, () -> ExpansionLootTables.read(string), "string " + path);
        }
    }

    @Test void yamlRejectsNonFiniteOutOfRangeAndEmptyWeightedTables() throws Exception {
        for (String path : List.of("unidentified-chance", "tables.standard.chance"))
            for (double value : new double[]{-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                var yaml = yaml(); yaml.set(path, value);
                assertThrows(IllegalArgumentException.class, () -> ExpansionLootTables.read(yaml), path + "=" + value);
            }
        for (double value : new double[]{-1, 101, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var yaml = yaml(); yaml.set("tables.boss.weights.epic", value);
            assertThrows(IllegalArgumentException.class, () -> ExpansionLootTables.read(yaml));
        }
        var empty = yaml();
        for (Rarity rarity : Rarity.values()) empty.set("tables.chest.weights." + rarity.name().toLowerCase(java.util.Locale.ROOT), 0);
        assertThrows(IllegalArgumentException.class, () -> ExpansionLootTables.read(empty));
    }

    @Test void constructorsRequireCompleteRulesAndRarityWeightMaps() {
        assertThrows(IllegalArgumentException.class, () -> new Rule(1, Map.of(Rarity.MYTHIC, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> new Rule(1, weights(0, 0, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new ExpansionLootTables(true, 0.8, Map.of()));
        var complete = tables(true, 1, weights(1, 1, 1, 1, 1));
        assertThrows(UnsupportedOperationException.class, () -> complete.rules().clear());
        assertThrows(UnsupportedOperationException.class, () -> complete.rules().get(Source.BOSS).weights().clear());
    }

    @Test void conditionalWeightedBoundariesReachAllFiveRaritiesWithoutInclusiveUpperEdges() {
        var tables = tables(true, 1, weights(1, 1, 1, 1, 1));
        var pools = pools();
        Rarity[] rarities = Rarity.values();
        for (int index = 0; index < rarities.length; index++) {
            double lower = index / 5.0;
            double upper = (index + 1) / 5.0;
            ItemId expected = pools.get(rarities[index]).getFirst();
            assertEquals(Optional.of(expected), tables.select(Source.STANDARD, 0, lower, 0, pools), "lower bin " + index);
            assertEquals(Optional.of(expected), tables.select(Source.STANDARD, 0, (lower + upper) / 2, 0, pools), "middle bin " + index);
            assertEquals(Optional.of(expected), tables.select(Source.STANDARD, 0, Math.nextDown(upper), 0, pools), "upper bin " + index);
        }
    }

    @Test void weightingIsConditionalOnDropSuccessAndZeroWeightRaritiesAreSkipped() {
        var tables = tables(true, 0.25, weights(0, 0, 2, 0, 1));
        var pools = pools();
        assertEquals(Optional.of(pools.get(Rarity.EPIC).getFirst()), tables.select(Source.ELITE, 0, 0, 0, pools));
        assertEquals(Optional.of(pools.get(Rarity.EPIC).getFirst()), tables.select(Source.ELITE, 0, Math.nextDown(2.0 / 3), 0, pools));
        assertEquals(Optional.of(pools.get(Rarity.MYTHIC).getFirst()), tables.select(Source.ELITE, 0, 2.0 / 3, 0, pools));
        assertTrue(tables.select(Source.ELITE, 0.25, 0.99, 0, pools).isEmpty());
    }

    @Test void chanceBoundariesAndDisabledTablesNeverAccidentallyCreateDrops() {
        var weights = weights(1, 1, 1, 1, 1);
        var pools = pools();
        var quarter = tables(true, 0.25, weights);
        assertTrue(quarter.select(Source.CHEST, Math.nextDown(0.25), 0, 0, pools).isPresent());
        assertTrue(quarter.select(Source.CHEST, 0.25, 0, 0, pools).isEmpty());
        assertTrue(quarter.select(Source.CHEST, Math.nextUp(0.25), 0, 0, pools).isEmpty());
        assertTrue(tables(true, 0, weights).select(Source.BOSS, 0, 0, 0, pools).isEmpty());
        assertTrue(tables(true, 1, weights).select(Source.BOSS, Math.nextDown(1.0), 0, 0, pools).isPresent());
        assertTrue(tables(false, 1, weights).select(Source.BOSS, 0, 0, 0, Map.of()).isEmpty());
    }

    @Test void poolValidationRequiresOnlyRaritiesWithAnEnabledPositiveChanceAndWeight() {
        var complete = tables(true, 1, weights(1, 1, 1, 1, 1));
        for (Rarity rarity : Rarity.values()) {
            var missing = new EnumMap<>(pools()); missing.remove(rarity);
            assertThrows(IllegalArgumentException.class, () -> complete.validatePools(missing), "missing " + rarity);
            var empty = new EnumMap<>(pools()); empty.put(rarity, List.of());
            assertThrows(IllegalArgumentException.class, () -> complete.validatePools(empty), "empty " + rarity);
        }
        var mythicOnly = tables(true, 1, weights(0, 0, 0, 0, 1));
        assertDoesNotThrow(() -> mythicOnly.validatePools(Map.of(Rarity.MYTHIC, List.of(new ItemId("mythic")))));
        assertDoesNotThrow(() -> tables(false, 1, weights(1, 1, 1, 1, 1)).validatePools(Map.of()));
        assertDoesNotThrow(() -> tables(true, 0, weights(1, 1, 1, 1, 1)).validatePools(Map.of()));
        assertThrows(IllegalStateException.class, () -> mythicOnly.select(Source.BOSS, 0, 0, 0, Map.of()));
    }

    @Test void everyRandomInputMustBeFiniteAndWithinTheHalfOpenUnitInterval() {
        var tables = tables(true, 1, weights(1, 1, 1, 1, 1));
        for (double invalid : new double[]{-Double.MIN_VALUE, -1, 1, Math.nextUp(1), Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> tables.select(Source.STANDARD, invalid, 0, 0, pools()));
            assertThrows(IllegalArgumentException.class, () -> tables.select(Source.STANDARD, 0, invalid, 0, pools()));
            assertThrows(IllegalArgumentException.class, () -> tables.select(Source.STANDARD, 0, 0, invalid, pools()));
        }
        assertThrows(IllegalArgumentException.class,
            () -> tables(false, 1, weights(1, 1, 1, 1, 1)).select(Source.BOSS, Double.NaN, 0, 0, Map.of()));
    }

    @Test void equalItemBinsSelectDeterministicallyAtTheirBoundariesWithoutMapOrderDependence() {
        var tables = tables(true, 1, weights(0, 0, 0, 0, 1));
        List<ItemId> items = List.of(new ItemId("a"), new ItemId("b"), new ItemId("c"));
        Map<Rarity, List<ItemId>> pool = Map.of(Rarity.MYTHIC, items);
        Map<Rarity, List<ItemId>> enumPool = new EnumMap<>(Rarity.class); enumPool.put(Rarity.MYTHIC, items);
        for (int index = 0; index < items.size(); index++) {
            double lower = index / 3.0;
            double middle = (index + 0.5) / 3.0;
            assertEquals(Optional.of(items.get(index)), tables.select(Source.BOSS, 0, 0, lower, pool));
            assertEquals(Optional.of(items.get(index)), tables.select(Source.BOSS, 0, 0, middle, pool));
            assertEquals(tables.select(Source.BOSS, 0, 0, middle, pool), tables.select(Source.BOSS, 0, 0, middle, enumPool));
        }
        assertEquals(Optional.of(items.getLast()), tables.select(Source.BOSS, 0, 0, Math.nextDown(1.0), pool));
    }

    @Test void bundledAbsoluteMythicOddsAreBoundedAndEveryNamedMythicSharesThePoolEqually() throws Exception {
        var tables = ExpansionLootTables.read(yaml());
        Map<Source, Double> expected = Map.of(Source.STANDARD, 0.0, Source.ELITE, 0.0006,
            Source.BOSS, 0.025, Source.CHEST, 0.0001);
        var catalog = new YamlConfiguration();
        catalog.loadFromString(Files.readString(CONTENT.resolve("skyforge-items.yml")));
        var items = catalog.getConfigurationSection("items");
        assertNotNull(items);
        List<ItemId> mythics = items.getKeys(false).stream().filter(id -> "mythic".equals(items.getString(id + ".rarity")))
            .map(ItemId::new).sorted(java.util.Comparator.comparing(ItemId::value)).toList();
        assertEquals(10, mythics.size());
        for (Source source : Source.values()) {
            Rule rule = tables.rules().get(source);
            double absolute = rule.chance() * rule.weights().get(Rarity.MYTHIC)
                / rule.weights().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(expected.get(source), absolute, 1.0e-12, source.name());
            assertEquals(expected.get(source) / 10, absolute / mythics.size(), 1.0e-12, "named item " + source);
            if (absolute == 0) continue;
            for (int index = 0; index < mythics.size(); index++)
                assertEquals(Optional.of(mythics.get(index)), tables.select(source, 0, Math.nextDown(1.0),
                    (index + 0.5) / mythics.size(), Map.of(Rarity.MYTHIC, mythics)));
        }
    }
}

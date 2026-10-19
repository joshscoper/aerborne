package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.model.SkillTreeCatalog;
import net.nerdsmp.paper.anticheat.ArcaneBreakScope;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneIntegrationTest {
    private final ArcaneRegistry registry = ArcaneRegistry.defaults();

    @Test
    void enchantingIsASkillWithItsOwnTreeAndNoJobBranch() {
        var tree = SkillTreeCatalog.tree(Skill.ENCHANTING);
        assertEquals(4, tree.branches().size());
        List<String> stats = tree.branches().stream().map(branch -> branch.nodes().getFirst().stat()).toList();
        assertTrue(stats.containsAll(List.of("skill-xp-percent", "imbue-dust-discount-percent", "research-progress-percent",
            "signature-cooldown-reduction-percent")), stats.toString());
        assertFalse(stats.contains("job-marks-percent"), "Enchanting is not paid work");
        assertEquals("Enchanting", Skill.ENCHANTING.displayName());
    }

    @Test
    void everyEnchantingTalentHasAConsumerInTheForge() throws Exception {
        String forge = Files.readString(Path.of("src/main/java/net/nerdsmp/paper/forge/arcane/ArcaneForge.java"));
        for (String stat : List.of("imbue-dust-discount-percent", "research-progress-percent", "signature-cooldown-reduction-percent")) {
            assertTrue(forge.contains("\"" + stat + "\""), stat);
        }
    }

    @Test
    void effectEnchantmentsHaveUniqueRecipesAndFitTheirTools() {
        assertTrue(ArcaneRegistry.validate(registry.all()).isEmpty());
        assertEquals(EnumSet.of(ArcaneAspect.PRECISION, ArcaneAspect.VITALITY), registry.of(ArcaneEnchantment.BLOODLETTER).aspects());
        assertEquals(ArcaneTier.SPECIALIZED, registry.of(ArcaneEnchantment.VEINSEEKER).tier());
        assertTrue(ArcaneEnchantment.BLOODLETTER.supports(Material.IRON_SWORD));
        assertFalse(ArcaneEnchantment.BLOODLETTER.supports(Material.BOW));
        assertTrue(ArcaneEnchantment.VEINSEEKER.supports(Material.DIAMOND_PICKAXE));
        assertFalse(ArcaneEnchantment.VEINSEEKER.supports(Material.DIAMOND_SHOVEL));
        assertTrue(ArcaneEnchantment.CHAIN_HARVEST.supports(Material.IRON_HOE));
        assertEquals(10.0D, ArcaneEnchantment.VEINSEEKER.stats(5).get("vein-size"));
    }

    @Test
    void withEffectsOffTheObtainableListIsExactlyThePre20List() {
        ArcaneEnchantment[] obtainable = ArcaneForge.obtainable(null);
        assertEquals(12, obtainable.length);
        assertEquals(Arrays.asList(ArcaneEnchantment.values()).subList(0, 12), Arrays.asList(obtainable));
        assertTrue(Arrays.stream(obtainable).noneMatch(ArcaneForge::effectEnchantment));
    }

    @Test
    void attunementRaisesOnlyMatchingEnchantmentsAndIsBounded() throws Exception {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString("arcane-forge:\n  attunement:\n    enabled: true\n    bonus-percent: 10\n");
            ArcaneItemEffects.publish(ArcaneSettings.from(yaml));
            assertEquals(27.5D, ArcaneItemEffects.attuned(ArcaneEnchantment.KEEN_EDGE, ArcaneAspect.MIGHT, 25.0D), 1e-9);
            assertEquals(25.0D, ArcaneItemEffects.attuned(ArcaneEnchantment.KEEN_EDGE, ArcaneAspect.AEGIS, 25.0D), 1e-9);
            assertEquals(25.0D, ArcaneItemEffects.attuned(ArcaneEnchantment.KEEN_EDGE, null, 25.0D), 1e-9);
            yaml.loadFromString("arcane-forge:\n  attunement:\n    enabled: true\n    bonus-percent: 99\n");
            assertFalse(ArcaneSettings.from(yaml).errors().isEmpty(), "bonus above 25% is rejected");
            yaml.loadFromString("arcane-forge:\n  attunement:\n    enabled: false\n");
            ArcaneItemEffects.publish(ArcaneSettings.from(yaml));
            assertEquals(25.0D, ArcaneItemEffects.attuned(ArcaneEnchantment.KEEN_EDGE, ArcaneAspect.MIGHT, 25.0D), 1e-9,
                "switching attunement off makes it dormant immediately");
        } finally {
            ArcaneItemEffects.publish(ArcaneSettings.defaults());
        }
    }

    @Test
    void corruptionsAreValidFixedTradesForTheirEquipment() {
        for (ArcaneCorruption corruption : ArcaneCorruption.values()) {
            assertFalse(corruption.stats().isEmpty());
            assertTrue(corruption.stats().values().stream().allMatch(value -> value > 0.0D && value <= 10.0D), corruption.name());
            assertFalse(corruption.bonusText().isBlank());
            assertEquals(corruption, ArcaneCorruption.fromId(corruption.id()).orElseThrow());
        }
        assertTrue(ArcaneCorruption.fromId("from_the_future").isEmpty());
    }

    @Test
    void safeCoreSectionsAreOnButRuntimeEffectsStayGatedInProduction() throws Exception {
        ArcaneSettings production = ArcaneSettings.from(load("src/main/resources/config.yml"));
        assertTrue(production.errors().isEmpty(), production.errors().toString());
        assertTrue(production.corruption().enabled() && production.attunement().enabled());
        for (ArcaneEnchantment effect : List.of(ArcaneEnchantment.BLOODLETTER, ArcaneEnchantment.VEINSEEKER, ArcaneEnchantment.CHAIN_HARVEST)) {
            assertFalse(production.effects().enabled(effect), effect.name());
        }
        assertEquals(ArcaneSettings.defaults().effects(), production.effects());
        assertEquals(ArcaneSettings.defaults().corruption(), production.corruption());
        assertEquals(ArcaneSettings.defaults().attunement(), production.attunement());
        assertEquals(ArcaneSettings.defaults().experience(), production.experience());
        ArcaneSettings local = ArcaneSettings.from(load("../deploy/test/config.yml"));
        assertTrue(local.errors().isEmpty(), local.errors().toString());
        assertTrue(local.corruption().enabled() && local.attunement().enabled() && local.effects().bloodletter()
            && local.effects().veinseeker() && local.effects().chainHarvest());
    }

    @Test
    void researchTalentScalesProgressButRevealsNothingNew() {
        var rules = ArcaneSettings.defaults().research();
        var reagents = List.of(new ArcaneResearch.Reagent(ArcaneAspect.MIGHT, 1), new ArcaneResearch.Reagent(ArcaneAspect.PRECISION, 1));
        var plain = ArcaneResearch.plan(registry, rules, ResearchProfile.empty(), reagents, false, 10);
        var talented = ArcaneResearch.plan(registry, rules, ResearchProfile.empty(), reagents, false, 10, 25.0D);
        assertEquals(plain.status(), talented.status());
        assertEquals(plain.multiplier() * 1.25D, talented.multiplier(), 1e-9);
    }

    @Test
    void chainBreakScopeIsOneUseNonNestingAndEndsOnException() {
        AtomicBoolean nested = new AtomicBoolean(true);
        assertFalse(ArcaneBreakScope.breaking(null, null, () -> true));
        // The scope needs real Player and Block objects only for automated(event); nesting rules apply regardless.
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class}, (proxy, method, args) -> null);
        org.bukkit.block.Block block = (org.bukkit.block.Block) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{org.bukkit.block.Block.class}, (proxy, method, args) ->
                method.getName().equals("equals") ? proxy == args[0] : null);
        assertTrue(ArcaneBreakScope.breaking(player, block, () -> {
            assertTrue(ArcaneBreakScope.inside());
            nested.set(ArcaneBreakScope.breaking(player, block, () -> true));
            return true;
        }));
        assertFalse(nested.get(), "a chain break can never open another");
        assertFalse(ArcaneBreakScope.inside());
        try {
            ArcaneBreakScope.breaking(player, block, () -> { throw new IllegalStateException("boom"); });
        } catch (IllegalStateException expected) {
            // The scope must still close.
        }
        assertFalse(ArcaneBreakScope.inside());
    }

    private static YamlConfiguration load(String path) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(path));
        return yaml;
    }
}

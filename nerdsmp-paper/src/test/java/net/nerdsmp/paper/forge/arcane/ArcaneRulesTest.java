package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.paper.forge.ArcaneEnchantment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArcaneRulesTest {
    private final ArcaneSettings settings = ArcaneSettings.defaults();
    private final ArcaneRegistry registry = ArcaneRegistry.defaults();

    @Test
    void defaultsEnableCoreProgressionButKeepRuntimePowersOff() throws Exception {
        assertTrue(settings.errors().isEmpty());
        assertTrue(settings.enabled() && settings.codex());
        assertTrue(settings.research().enabled());
        assertTrue(settings.imbuement().enabled());
        assertTrue(settings.overcharge().enabled());
        assertTrue(settings.attunement().enabled() && settings.corruption().enabled());
        assertFalse(settings.signatures().enabled());
        for (ArcaneSignature signature : ArcaneSignature.values()) assertFalse(settings.signatures().active(signature));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("arcane-forge:\n  signatures:\n    enabled: true\n    stormwake:\n      enabled: true\n");
        ArcaneSettings partial = ArcaneSettings.from(yaml);
        assertTrue(partial.signatures().active(ArcaneSignature.STORMWAKE));
        assertFalse(partial.signatures().active(ArcaneSignature.LAST_BASTION), "each signature has its own switch");
    }

    @Test
    void invalidConfigIsReportedAndFallsBackInsteadOfApplying() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
            arcane-forge:
              research:
                enabled: maybe
                dust-per-reagent: 9999
              imbuement:
                level-requirements: [0, 30, 10, 25, 40]
              signatures:
                stormwake:
                  maximum-targets: 50
            """);
        ArcaneSettings parsed = ArcaneSettings.from(yaml);
        assertEquals(4, parsed.errors().size(), parsed.errors().toString());
        assertFalse(parsed.research().enabled());
        assertEquals(4, parsed.research().dustPerReagent());
        assertEquals(List.of(0, 0, 10, 25, 40), parsed.imbuement().levelRequirements());
        assertEquals(3, parsed.signatures().of(ArcaneSignature.STORMWAKE).targets());
    }

    @Test
    void capacityComesFromRarityQualityAndItemLevel() {
        var rules = settings.capacity();
        assertEquals(10, ArcaneCapacity.capacity(rules, "CRAFTED", 0, 0));
        assertEquals(30 + 8 + 4, ArcaneCapacity.capacity(rules, "mythic", 45, 4));
        assertEquals(rules.fallback(), ArcaneCapacity.capacity(rules, "SOMETHING_NEW", 0, 0));
        assertEquals(5, ArcaneCapacity.cost(rules, registry.of(ArcaneEnchantment.KEEN_EDGE), 5));
        assertEquals(10, ArcaneCapacity.cost(rules, registry.of(ArcaneEnchantment.SOULSIPHON), 5));
        assertEquals(12, ArcaneCapacity.cost(rules, registry.of(ArcaneSignature.STORMWAKE), 1));
    }

    @Test
    void legacyItemsOverCapacityAreGrandfatheredAndCanAlwaysBeReducedButNotGrown() {
        var rules = settings.capacity();
        int load = ArcaneCapacity.load(rules, registry, Map.of(ArcaneEnchantment.KEEN_EDGE, 5,
            ArcaneEnchantment.SOULSIPHON, 5, ArcaneEnchantment.EXECUTIONER, 5), null);
        assertEquals(25, load);
        int capacity = ArcaneCapacity.capacity(rules, "CRAFTED", 0, 0);
        assertTrue(ArcaneCapacity.grandfathered(capacity, load));
        assertEquals(ArcaneCapacity.Fit.FITS, ArcaneCapacity.evaluate(capacity, load, 10, 0, false, 0).fit());
        var signature = ArcaneCapacity.evaluate(capacity, load, 0, 12, false, 25);
        assertEquals(ArcaneCapacity.Fit.OVER_CAPACITY, signature.fit());
        assertEquals(27, signature.shortfall());
    }

    @Test
    void overchargeIsTheWholeExcessAndBoundedByItsPercentage() {
        var within = ArcaneCapacity.evaluate(40, 36, 0, 12, true, 25);
        assertEquals(ArcaneCapacity.Fit.OVERCHARGE, within.fit());
        assertEquals(8, within.overcharge());
        assertEquals(10, within.overchargeLimit());
        assertEquals(ArcaneCapacity.Fit.OVER_CAPACITY, ArcaneCapacity.evaluate(40, 40, 0, 12, true, 25).fit());
        assertEquals(ArcaneCapacity.Fit.OVER_CAPACITY, ArcaneCapacity.evaluate(40, 36, 0, 12, false, 25).fit());
        assertEquals(ArcaneCapacity.Fit.FITS, ArcaneCapacity.evaluate(40, 20, 0, 12, true, 25).fit());
    }

    @Test
    void chosenImbuementIsDeterministicAndPricedPerLevelGained() {
        var rules = settings.imbuement();
        ArcaneDefinition keen = registry.of(ArcaneEnchantment.KEEN_EDGE);
        assertEquals(6 * (1 + 2 + 3 + 4 + 5), ImbueRules.dust(rules, keen.tier(), 0, 5, false));
        assertEquals(6 * (4 + 5), ImbueRules.dust(rules, keen.tier(), 3, 5, false));
        assertEquals((int) Math.ceil(6 * 9 * 0.75), ImbueRules.dust(rules, keen.tier(), 3, 5, true));
        assertEquals(8 * 3, ImbueRules.dust(rules, ArcaneTier.SPECIALIZED, 2, 3, false));
        assertEquals(2, ImbueRules.highestLevel(rules, 0, 5));
        assertEquals(5, ImbueRules.highestLevel(rules, 40, 5));
        assertEquals(ImbueRules.Status.READY, ImbueRules.quote(rules, keen, true, false, true, 0, 2, 0).status());
        assertEquals(ImbueRules.Status.NEEDS_LEVEL, ImbueRules.quote(rules, keen, true, false, true, 0, 3, 9).status());
        assertEquals(ImbueRules.Status.UNDISCOVERED, ImbueRules.quote(rules, keen, false, false, true, 0, 1, 50).status());
        assertEquals(ImbueRules.Status.NO_UPGRADE, ImbueRules.quote(rules, keen, true, false, true, 3, 3, 50).status());
        assertEquals(ImbueRules.Status.INCOMPATIBLE, ImbueRules.quote(rules, keen, true, false, false, 0, 1, 50).status());
    }

    @Test
    void chargesFillOncePerTickDecayAndReleaseIntoACooldown() {
        var counter = SignatureRules.Counter.EMPTY;
        long now = 10_000;
        for (int hit = 1; hit < 5; hit++) {
            var step = SignatureRules.gain(counter, now + hit * 500L, hit, 5, 8_000, 8_000);
            assertTrue(step.counted() && !step.released());
            counter = step.counter();
        }
        assertFalse(SignatureRules.gain(counter, now + 2_000, 4, 5, 8_000, 8_000).counted(), "one gain per tick");
        var release = SignatureRules.gain(counter, now + 3_000, 5, 5, 8_000, 8_000);
        assertTrue(release.released());
        assertEquals(0, release.counter().value());
        assertFalse(SignatureRules.gain(release.counter(), now + 4_000, 6, 5, 8_000, 8_000).counted(), "cooling down");
        var decayed = SignatureRules.gain(new SignatureRules.Counter(4, now, 1, 0), now + 9_000, 2, 5, 8_000, 0);
        assertEquals(1, decayed.counter().value());
        assertFalse(decayed.released());
        assertEquals(12_000L, SignatureRules.cooldownMillis(8, 10, 5));
    }

    @Test
    void chainLightningIsBoundedNeverRevisitsAndFallsOff() {
        UUID origin = UUID.randomUUID();
        var start = new SignatureRules.Point(origin, 0, 64, 0);
        var near = new SignatureRules.Point(UUID.randomUUID(), 2, 64, 0);
        var next = new SignatureRules.Point(UUID.randomUUID(), 5, 64, 0);
        var far = new SignatureRules.Point(UUID.randomUUID(), 30, 64, 0);
        var strikes = SignatureRules.chain(start, List.of(far, next, near, near, start), 3, 4.0D, 10.0D, 0.5D);
        assertEquals(List.of(near.id(), next.id()), strikes.stream().map(SignatureRules.Strike::target).toList());
        assertEquals(10.0D, strikes.get(0).damage(), 1e-9);
        assertEquals(5.0D, strikes.get(1).damage(), 1e-9);
        assertTrue(SignatureRules.chain(start, List.of(near), 0, 4.0D, 10.0D, 0.5D).isEmpty());
        assertEquals(1, SignatureRules.chain(start, List.of(near, next), 1, 10.0D, 10.0D, 0.5D).size());
    }
}

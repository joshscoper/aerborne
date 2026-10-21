package net.nerdsmp.paper.expedition.content;

import net.nerdsmp.paper.expedition.flow.ReadyCheck;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PartyScalingAndAlliesTest {
    private static final Instant T0 = Instant.parse("2026-10-20T12:00:00Z");

    @Test void smallerPartiesMeetFewerSofterEnemies() {
        PartyScaling scaling = PartyScaling.DEFAULT;
        assertEquals(0.52, scaling.countFactor(1, 5), 1e-9);
        assertEquals(0.6, scaling.healthFactor(1, 5), 1e-9);
        assertEquals(1.0, scaling.countFactor(5, 5), 1e-9);
        assertEquals(1.0, scaling.healthFactor(5, 5), 1e-9);
        assertEquals(2, scaling.scaledCount(3, 1, 5), "Three authored zombies become two for a solo player");
        assertEquals(1, scaling.scaledCount(1, 1, 5), "Never fewer than one of an authored mob");
        assertEquals(3, scaling.scaledCount(3, 5, 5));
        assertEquals(0, scaling.scaledCount(0, 1, 5));
    }

    @Test void alliesCountAsPartOfAPlayer() {
        PartyScaling scaling = PartyScaling.DEFAULT;
        assertEquals(1 + 4 * 0.6, scaling.effectiveSize(1, 4, 5), 1e-9);
        assertEquals(5, scaling.effectiveSize(5, 4, 5), 1e-9, "Never above the full party");
        assertEquals(1, scaling.effectiveSize(0, 0, 5), 1e-9, "An empty room still scales as one player");
        assertTrue(scaling.countFactor(scaling.effectiveSize(1, 4, 5), 5) < 1, "A solo player with four allies still meets fewer enemies than five players");
    }

    @Test void scalingLoadsFromConfigAndRejectsNonsense() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("scaling.count-floor", 0.7);
        assertEquals(new PartyScaling(0.7, 0.5, 0.6), PartyScaling.load(yaml.getConfigurationSection("scaling")));
        assertEquals(PartyScaling.DEFAULT, PartyScaling.load(null));
        assertThrows(IllegalArgumentException.class, () -> new PartyScaling(0, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new PartyScaling(0.5, 0.5, 2));
    }

    @Test void alliesAreOfferedOnlyAfterTheWaitAndOnlyForEmptySlots() {
        Allies allies = Allies.DEFAULT;
        assertFalse(allies.offerable(T0, T0.plusSeconds(119), 1, 5));
        assertTrue(allies.offerable(T0, T0.plusSeconds(120), 1, 5));
        assertFalse(allies.offerable(T0, T0.plusSeconds(600), 5, 5), "A full group has no slots");
        assertEquals(4, allies.slots(1, 5));
        assertEquals(1, allies.slots(4, 5));
        assertEquals(1, allies.slots(1, 2), "Cinder Keep sized for two needs one ally");
        Allies off = new Allies(false, Duration.ZERO, 4, 1.5, Allies.DEFAULT.roles());
        assertEquals(0, off.slots(1, 5));
        assertFalse(off.offerable(T0, T0.plusSeconds(999), 1, 5));
    }

    @Test void rolesFillInConfiguredOrderAndRepeat() {
        assertEquals(List.of(Allies.Role.BULWARK, Allies.Role.BLADE, Allies.Role.HEDGE_WITCH), Allies.DEFAULT.pick(3));
        Allies archers = new Allies(true, Duration.ofSeconds(30), 4, 1, List.of(Allies.Role.ARCHER, Allies.Role.HEDGE_WITCH));
        assertEquals(List.of(Allies.Role.ARCHER, Allies.Role.HEDGE_WITCH, Allies.Role.ARCHER), archers.pick(3));
        assertEquals("hedge-witch", Allies.Role.HEDGE_WITCH.key());
        assertEquals(Allies.Role.HEDGE_WITCH, Allies.Role.parse("Hedge-Witch"));
        assertThrows(IllegalArgumentException.class, () -> Allies.Role.parse("dragon"));
    }

    @Test void allySettingsLoadFromConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("allies.offer-after-seconds", 45);
        yaml.set("allies.max", 2);
        yaml.set("allies.roles", List.of("archer"));
        Allies loaded = Allies.load(yaml.getConfigurationSection("allies"));
        assertEquals(Duration.ofSeconds(45), loaded.offerAfter());
        assertEquals(2, loaded.slots(1, 5));
        assertEquals(List.of(Allies.Role.ARCHER, Allies.Role.ARCHER), loaded.pick(2));
        yaml.set("allies.max", 9);
        assertThrows(IllegalArgumentException.class, () -> Allies.load(yaml.getConfigurationSection("allies")));
    }

    @Test void readyChecksCarryTheirHiredAllies() {
        UUID leader = UUID.randomUUID(), friend = UUID.randomUUID();
        ReadyCheck check = new ReadyCheck("cinder_keep", "normal", List.of(leader, friend), List.of(), T0, Duration.ofSeconds(30), 3);
        assertEquals(3, check.allies());
        assertEquals(0, new ReadyCheck("cinder_keep", "normal", List.of(leader), List.of(), T0, Duration.ofSeconds(30)).allies());
        assertThrows(IllegalArgumentException.class, () -> new ReadyCheck("x", "normal", List.of(leader), List.of(), T0, Duration.ofSeconds(30), -1));
    }
}

package net.nerdsmp.paper.digsite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DigSiteCooldownTest {
    @Test
    void acceptsUnitDurationsAndBareSeconds() {
        assertEquals(90, DigSiteCommand.cooldown("90s"));
        assertEquals(600, DigSiteCommand.cooldown("10m"));
        assertEquals(3_600, DigSiteCommand.cooldown("1h"));
        assertEquals(300, DigSiteCommand.cooldown("300"));
    }

    @Test
    void rejectsMalformedCooldowns() {
        assertThrows(IllegalArgumentException.class, () -> DigSiteCommand.cooldown("soon"));
        assertThrows(IllegalArgumentException.class, () -> DigSiteCommand.cooldown("0"));
    }
}

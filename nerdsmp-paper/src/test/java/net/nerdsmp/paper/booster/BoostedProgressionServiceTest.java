package net.nerdsmp.paper.booster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoostedProgressionServiceTest {
    @Test void aBoosterRaisesEveryAwardButStaffGrants() {
        assertEquals(15, BoostedProgressionService.boosted(10, "mining:abc", BoosterService.MULTIPLIER));
        assertEquals(10, BoostedProgressionService.boosted(10, "mining:abc", 1D), "nothing changes without a booster");
        assertEquals(10, BoostedProgressionService.boosted(10, "admin:abc", BoosterService.MULTIPLIER), "staff grants stay as asked");
        assertEquals(2, BoostedProgressionService.boosted(1, null, BoosterService.MULTIPLIER), "small awards round, they don't vanish");
        assertEquals(0, BoostedProgressionService.boosted(0, "quest:x", BoosterService.MULTIPLIER));
        assertEquals(-5, BoostedProgressionService.boosted(-5, "quest:x", BoosterService.MULTIPLIER), "a negative change is never made bigger");
    }
}

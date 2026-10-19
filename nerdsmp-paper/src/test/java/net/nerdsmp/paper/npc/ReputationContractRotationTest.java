package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.paper.engagement.QuestAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class ReputationContractRotationTest {
    private static final FactionId FACTION = new FactionId("frontier_wardens");

    @Test
    void twoItemPoolsRotateDeterministicallyAtTheNextPeriod() {
        List<ReputationContract> pool = List.of(contract("road_watch"), contract("timber_detail"));
        Instant firstDay = Instant.parse("2026-09-01T12:00:00Z");
        Instant nextDay = Instant.parse("2026-09-02T12:00:00Z");

        String first = ReputationContractRotation.active(pool, FACTION, firstDay, 89_021L).getFirst().id();
        assertEquals(first, ReputationContractRotation.active(pool, FACTION, firstDay, 89_021L).getFirst().id());
        assertNotEquals(first, ReputationContractRotation.active(pool, FACTION, nextDay, 89_021L).getFirst().id());
    }

    @Test
    void utcPeriodsUseMidnightMondayAndTheFirstOfTheMonth() {
        Instant now = Instant.parse("2026-09-01T12:00:00Z");
        ReputationContract.Period daily = ReputationContract.Cadence.DAILY.period(now);
        ReputationContract.Period weekly = ReputationContract.Cadence.WEEKLY.period(now);
        ReputationContract.Period monthly = ReputationContract.Cadence.MONTHLY.period(now);

        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), daily.startsAt());
        assertEquals(Instant.parse("2026-09-02T00:00:00Z"), daily.endsAt());
        assertEquals(Instant.parse("2026-08-31T00:00:00Z"), weekly.startsAt());
        assertEquals(Instant.parse("2026-09-07T00:00:00Z"), weekly.endsAt());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), monthly.startsAt());
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"), monthly.endsAt());
    }

    private ReputationContract contract(String id) {
        return new ReputationContract(
            id, FACTION, ReputationContract.Cadence.DAILY, id, "Complete useful field work.",
            QuestAction.SLAY_MONSTER, 10, 12, 100L, 1
        );
    }
}

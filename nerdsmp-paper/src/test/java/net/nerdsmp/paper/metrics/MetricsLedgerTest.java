package net.nerdsmp.paper.metrics;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class MetricsLedgerTest {
    private static long t(String date) { return Instant.parse(date).toEpochMilli(); }
    private static JsonObject range(MetricsLedger m,long now) { return m.report(now).getAsJsonObject("ranges").getAsJsonObject("30"); }
    @Test void utcMidnightSplitsOnlineAndAfkAdjustedTimeWithoutDuplicatingAJoin() {
        long start=t("2026-10-01T23:59:55Z"); var m=new MetricsLedger(start,180); UUID id=UUID.randomUUID();
        m.join(id,true,start,false); m.join(id,true,start+1,false);
        m.sample(id,start+10_000,true,"MMORPG"); m.sample(id,start+20_000,false,"MMORPG"); m.quit(id,start+20_000);
        var days=m.report(start+20_000).getAsJsonArray("days");
        assertEquals(5000,days.get(0).getAsJsonObject().get("activeMs").getAsLong());
        assertEquals(5000,days.get(1).getAsJsonObject().get("activeMs").getAsLong());
        var r=range(m,start+20_000); assertEquals(20_000,r.get("onlineMs").getAsLong()); assertEquals(10_000,r.get("activeMs").getAsLong());
        assertEquals(1,r.getAsJsonObject("counters").get("session.started").getAsInt()); assertEquals(1,r.get("completedSessions").getAsInt());
        assertEquals(20_000,r.get("averageSessionMs").getAsLong()); assertEquals(1,r.get("players").getAsInt());
    }
    @Test void retentionUsesExactCompletedCalendarDaysAndEligibleDenominators() {
        long start=t("2026-10-01T12:00:00Z");var m=new MetricsLedger(start,180); UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        m.join(a,true,start,false);m.join(b,true,start,false);m.quit(a,start+1000);m.quit(b,start+1000);m.pulse(start,2,20,10,50);
        long d1=t("2026-10-02T12:00:00Z");m.join(a,false,d1,false);m.quit(a,d1+1000);m.pulse(d1,1,20,10,50);
        assertEquals(2,range(m,t("2026-10-03T00:00:00Z")).getAsJsonArray("retention").get(0).getAsJsonObject().get("missing").getAsInt(),"partial-day tracking cannot prove churn");
        // Simulate a fully sampled target day without making a real-time test wait a day.
        var observed=m.copyForSave();observed.days.get("2026-10-02").coverage=86_400_000;
        m=MetricsLedger.restore(MetricsLedger.encode(observed),d1+1000);
        var r=range(m,t("2026-10-03T00:00:00Z")).getAsJsonArray("retention");
        assertEquals(2,r.get(0).getAsJsonObject().get("eligible").getAsInt()); assertEquals(.5,r.get(0).getAsJsonObject().get("rate").getAsDouble());
        assertFalse(r.get(1).getAsJsonObject().has("rate"));
        assertEquals(2,r.get(1).getAsJsonObject().get("pending").getAsInt());
        assertFalse(range(m,d1).getAsJsonArray("retention").get(0).getAsJsonObject().has("rate"),"today is incomplete");
        var later=range(m,t("2026-10-10T00:00:00Z")).getAsJsonArray("retention").get(1).getAsJsonObject();
        assertEquals(2,later.get("missing").getAsInt());assertFalse(later.has("rate"),"an unobserved target date is not zero retention");
    }
    @Test void establishedPlayersAreNotNewCohortsAndMilestonesDoNotRequireOrderedFunnelSteps() {
        long start=t("2026-10-01T12:00:00Z");var m=new MetricsLedger(start,180);UUID old=UUID.randomUUID(),fresh=UUID.randomUUID();
        m.join(old,false,start,false);m.join(fresh,true,start,false);
        m.counter(fresh,"quest.completed",1,start);m.counter(fresh,"quest.completed",1,start);
        var r=range(m,start);assertEquals(1,r.get("newPlayers").getAsInt());assertEquals(1,r.get("returning").getAsInt());
        assertEquals(1,r.getAsJsonObject("milestones").get("quest").getAsInt());assertEquals(0,r.getAsJsonObject("milestones").get("xp").getAsInt());
    }
    @Test void persistedSnapshotsAreDetachedAndRestartsCannotInflateCompletedSessionLengths() {
        long start=t("2026-10-01T12:00:00Z");var m=new MetricsLedger(start,180);UUID id=UUID.randomUUID();m.join(id,true,start,false);
        var copy=m.copyForSave();m.counter(id,"quest.completed",1,start);assertFalse(copy.days.get("2026-10-01").counters.containsKey("quest.completed"));
        var restored=MetricsLedger.restore(MetricsLedger.encode(copy),start+3_600_000);
        restored.join(id,false,start+3_600_000,true);restored.quit(id,start+7_200_000);
        var r=range(restored,start+7_200_000);assertEquals(0,r.get("completedSessions").getAsInt());
        assertEquals(1,r.getAsJsonObject("counters").get("session.interrupted").getAsInt());assertFalse(r.has("averageSessionMs"));
    }
    @Test void lateSamplesAreCappedAndDimensionsAreBoundedIncludingUnicode() {
        long start=t("2026-10-01T12:00:00Z");var m=new MetricsLedger(start,180);UUID id=UUID.randomUUID();m.join(id,true,start,false);
        m.sample(id,start+600_000,true,"MMORPG");m.pulse(start+600_000,1,20,30,100);
        assertEquals(30_000,range(m,start+600_000).get("activeMs").getAsLong());
        assertEquals(570_000,m.report(start+600_000).get("sampleGapMs").getAsLong());
        for(int i=0;i<600;i++)m.counter(null,"test."+i,1,start);
        assertTrue(range(m,start).getAsJsonObject("counters").size()<=MetricsLedger.MAX_COUNTERS+1);
        assertTrue(m.report(start).get("lost").getAsLong()>0);assertTrue(MetricsLedger.dimension("🌤 İ Island").length()<=64);
    }
    @Test void damagedOrUnsupportedFilesAreRejectedInsteadOfResettingHistory() {
        long now=t("2026-10-01T12:00:00Z");assertThrows(RuntimeException.class,()->MetricsLedger.restore("{",now));
        assertThrows(IllegalArgumentException.class,()->MetricsLedger.restore("{\"started\":1}",now));
        var m=new MetricsLedger(now,180);m.pulse(now,1,20,20,20);var s=m.copyForSave();s.schema=2;
        assertThrows(IllegalArgumentException.class,()->MetricsLedger.restore(MetricsLedger.encode(s),now));
        s.schema=1;s.days.values().iterator().next().coverage=-1;
        assertThrows(IllegalArgumentException.class,()->MetricsLedger.restore(MetricsLedger.encode(s),now));
    }
    @Test void oldDatesAndInactiveIdentitiesArePrunedTogetherWithoutDanglingVisits() {
        long start=t("2026-01-01T12:00:00Z");var m=new MetricsLedger(start,90);UUID id=UUID.randomUUID();m.join(id,true,start,false);m.quit(id,start+1000);
        long later=t("2026-10-01T12:00:00Z");m.pulse(later,0,20,10,20);
        var state=m.copyForSave();assertTrue(state.people.isEmpty());assertEquals(1,state.days.size());
        assertDoesNotThrow(()->MetricsLedger.restore(MetricsLedger.encode(state),later));
    }
}

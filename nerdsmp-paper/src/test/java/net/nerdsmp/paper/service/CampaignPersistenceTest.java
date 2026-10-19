package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.CampaignService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CampaignPersistenceTest {
    @TempDir Path directory;
    final Instant now=Instant.now();
    InMemoryCampaignService service(Path file,String season,Instant start,Instant end) {
        return new InMemoryCampaignService("test","Test",start,end,100,Set.of("gathering"),Map.of("first",50L),file.toFile(),season);
    }
    InMemoryCampaignService service(Path file,String season) { return service(file,season,now.minusSeconds(60),now.plusSeconds(600)); }
    @Test void restartCannotMoveCampaignDatesOrReactivateAnExpiredCampaign() {
        Path file=directory.resolve("state.yml");
        var first=service(file,"s1"); var snapshot=first.activeCampaign().toCompletableFuture().join().orElseThrow();
        var restarted=service(file,"s1",now,now.plusSeconds(99999));
        assertEquals(snapshot.startsAt(),restarted.activeCampaign().toCompletableFuture().join().orElseThrow().startsAt());
        assertEquals(snapshot.endsAt(),restarted.activeCampaign().toCompletableFuture().join().orElseThrow().endsAt());
        Path expired=directory.resolve("expired.yml"); service(expired,"s1",now.minusSeconds(500),now.minusSeconds(1));
        assertTrue(service(expired,"s1").activeCampaign().toCompletableFuture().join().isEmpty());
    }
    @Test void totalsAndClaimsDoNotLeakIntoAnotherSeason() {
        Path file=directory.resolve("state.yml"); AccountId account=AccountId.random();
        var first=service(file,"s1"); first.contribute(account,"gathering",75,"one").toCompletableFuture().join();
        assertTrue(first.claimReward(account,"first").toCompletableFuture().join());
        var next=service(file,"s2"); assertEquals(0,next.activeCampaign().toCompletableFuture().join().orElseThrow().totalPoints());
        assertFalse(next.claimReward(account,"first").toCompletableFuture().join());
        assertEquals(CampaignService.ContributionResult.APPLIED,next.contribute(account,"gathering",75,"one").toCompletableFuture().join());
        assertEquals(75,service(file,"s1").activeCampaign().toCompletableFuture().join().orElseThrow().totalPoints());
    }
    @Test void legacyStateMigratesOnceWithoutDiscardingItsSnapshot() throws Exception {
        Path file=directory.resolve("state.yml"); var yaml=new YamlConfiguration();
        yaml.set("campaigns.test.categories.gathering",75L); yaml.save(file.toFile());
        assertEquals(75,service(file,"s1").activeCampaign().toCompletableFuture().join().orElseThrow().totalPoints());
        assertEquals(0,service(file,"s2").activeCampaign().toCompletableFuture().join().orElseThrow().totalPoints());
        var saved=new YamlConfiguration(); saved.load(file.toFile());
        assertEquals(75,saved.getLong("campaigns.test.categories.gathering")); assertEquals("s1",saved.getString("legacy-season"));
    }
    @Test void failedSaveCannotPublishContributionOrConsumeReward() throws Exception {
        Path file=directory.resolve("state.yml"); AccountId account=AccountId.random(); var service=service(file,"s1");
        service.contribute(account,"gathering",50,"first").toCompletableFuture().join();
        String backup=Files.readString(file); Files.delete(file); Files.createDirectory(file);
        assertThrows(IllegalStateException.class,()->service.contribute(account,"gathering",25,"second"));
        assertThrows(IllegalStateException.class,()->service.claimReward(account,"first"));
        assertEquals(50,service.activeCampaign().toCompletableFuture().join().orElseThrow().totalPoints());
        Files.delete(file); Files.writeString(file,backup);
        assertEquals(CampaignService.ContributionResult.APPLIED,service.contribute(account,"gathering",25,"second").toCompletableFuture().join());
        assertTrue(service.claimReward(account,"first").toCompletableFuture().join());
    }
    @Test void malformedYamlAndOverflowingContributionsFailClosed() throws Exception {
        Path file=directory.resolve("state.yml"); Files.writeString(file,"campaigns: [broken");
        assertThrows(IllegalStateException.class,()->service(file,"s1"));
        var valid=service(directory.resolve("good.yml"),"s1");
        assertEquals(CampaignService.ContributionResult.DAILY_CAP_REACHED,valid.contribute(AccountId.random(),"gathering",Long.MAX_VALUE,"huge").toCompletableFuture().join());
        assertEquals(0,valid.activeCampaign().toCompletableFuture().join().orElseThrow().totalPoints());
    }
}

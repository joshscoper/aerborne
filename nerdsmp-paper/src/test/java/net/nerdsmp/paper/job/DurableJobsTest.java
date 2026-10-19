package net.nerdsmp.paper.job;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.service.InMemoryEconomyService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.concurrent.*;
import java.util.logging.Logger;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class DurableJobsTest {
    @TempDir Path directory;
    final AccountId account=AccountId.random();
    final Instant day=Instant.parse("2026-09-29T23:59:00Z");
    PlayerDataStore store() { return new PlayerDataStore(directory.toFile(),Logger.getAnonymousLogger()); }
    DiminishingJobService jobs(EconomyService economy,String season,Instant time) {
        return new DiminishingJobService(economy,2,2,4,store(),season,Clock.fixed(time,ZoneOffset.UTC));
    }
    @Test void allowanceReceiptsAndEarningsSurviveRestart() {
        var economy=new InMemoryEconomyService(); var first=jobs(economy,"s1",day);
        assertEquals(4,first.recordWork(account,Skill.MINING,8,"ore").toCompletableFuture().join().marks());
        var restarted=jobs(economy,"s1",day);
        assertTrue(restarted.recordWork(account,Skill.MINING,1,"ore").toCompletableFuture().join().duplicate());
        assertEquals(0,restarted.recordWork(account,Skill.MINING,1,"new").toCompletableFuture().join().marks());
        assertEquals(8,restarted.snapshot(account).toCompletableFuture().join().workUnits().get(Skill.MINING));
        assertEquals(4,restarted.snapshot(account).toCompletableFuture().join().earnedMarks().get(Skill.MINING));
    }
    @Test void utcDayAndSeasonHaveIndependentAllowancesAndPaymentKeys() {
        var economy=new InMemoryEconomyService();
        for(var service:new DiminishingJobService[]{jobs(economy,"s1",day),jobs(economy,"s2",day),jobs(economy,"s1",day.plusSeconds(120))})
            assertEquals(4,service.recordWork(account,Skill.MINING,8,"same").toCompletableFuture().join().marks());
        assertEquals(12,economy.balance(account).toCompletableFuture().join());
    }
    @Test void crashAfterCreditBeforeReceiptRecoversWithoutDoublePayment() {
        var economy=new InMemoryEconomyService();
        EconomyService interrupted=(EconomyService)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{EconomyService.class},(proxy,method,args)-> {
            Object result=method.invoke(economy,args);
            if(method.getName().equals("credit")) return ((CompletionStage<?>)result).thenCompose(ignored->CompletableFuture.failedFuture(new IllegalStateException("lost acknowledgment")));
            return result;
        });
        assertThrows(CompletionException.class,()->jobs(interrupted,"s1",day).recordWork(account,Skill.MINING,8,"ore").toCompletableFuture().join());
        var restarted=jobs(economy,"s1",day); restarted.recover(account).toCompletableFuture().join();
        restarted.recover(account).toCompletableFuture().join();
        assertEquals(4,economy.balance(account).toCompletableFuture().join());
        assertEquals(4,restarted.snapshot(account).toCompletableFuture().join().earnedMarks().get(Skill.MINING));
    }
    @Test void fractionalBonusSurvivesRestartAndMalformedInputCannotReserveWork() {
        var economy=new InMemoryEconomyService();
        for(int i=0;i<5;i++) new DiminishingJobService(economy,100,0,0,store(),"s1",Clock.fixed(day,ZoneOffset.UTC))
            .recordWork(account,Skill.MINING,1,"ore"+i,20).toCompletableFuture().join();
        assertEquals(6,economy.balance(account).toCompletableFuture().join());
        assertThrows(CompletionException.class,()->jobs(economy,"s1",day).recordWork(account,Skill.MINING,1,"bad",Double.NaN).toCompletableFuture().join());
    }
    @Test void corruptStoredAllowanceFailsClosed() {
        store().update(account,yaml->{ yaml.set("jobs.s1.schema",55); yaml.set("jobs.s1.day",day.toString()); });
        assertThrows(IllegalStateException.class,()->jobs(new InMemoryEconomyService(),"s1",day).snapshot(account));
    }
    @Test void preCookingSaveKeepsAllowancesReceiptsAndUnrelatedProgress() throws Exception {
        var economy=new InMemoryEconomyService();
        jobs(economy,"s1",day).recordWork(account,Skill.MINING,8,"ore").toCompletableFuture().join();
        store().update(account,yaml->{
            yaml.set("jobs.s1.skills.COOKING",null);
            yaml.set("progress.s1.skills.MINING.xp",12345L);
        });
        Path file=directory.resolve("player_data").resolve(account+".yml");
        byte[] before=Files.readAllBytes(file);
        var restarted=jobs(economy,"s1",day);
        restarted.recover(account).toCompletableFuture().join();
        var snapshot=restarted.snapshot(account).toCompletableFuture().join();
        assertEquals(8,snapshot.workUnits().get(Skill.MINING));
        assertEquals(4,snapshot.earnedMarks().get(Skill.MINING));
        assertEquals(0,snapshot.workUnits().get(Skill.COOKING));
        assertEquals(0,snapshot.earnedMarks().get(Skill.COOKING));
        assertArrayEquals(before,Files.readAllBytes(file));
        assertTrue(restarted.recordWork(account,Skill.MINING,1,"ore").toCompletableFuture().join().duplicate());
        assertEquals(0,restarted.recordWork(account,Skill.MINING,1,"new ore").toCompletableFuture().join().marks());
        assertEquals(2,restarted.recordWork(account,Skill.COOKING,2,"bread").toCompletableFuture().join().marks());
        assertEquals(6,economy.balance(account).toCompletableFuture().join());
        long miningXp=store().read(account,yaml->yaml.getLong("progress.s1.skills.MINING.xp"));
        long cookingWork=store().read(account,yaml->yaml.getLong("jobs.s1.skills.COOKING.work"));
        assertEquals(12345,miningXp);
        assertEquals(2,cookingWork);
        assertEquals(8,jobs(economy,"s1",day).snapshot(account).toCompletableFuture().join().workUnits().get(Skill.MINING));
    }
    @Test void preCookingSaveRecoversPendingCreditExactlyOnce() {
        var economy=new InMemoryEconomyService();
        EconomyService interrupted=(EconomyService)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{EconomyService.class},(proxy,method,args)-> {
            Object result=method.invoke(economy,args);
            if(method.getName().equals("credit")) return ((CompletionStage<?>)result).thenCompose(ignored->CompletableFuture.failedFuture(new IllegalStateException("lost acknowledgment")));
            return result;
        });
        assertThrows(CompletionException.class,()->jobs(interrupted,"s1",day).recordWork(account,Skill.MINING,8,"ore").toCompletableFuture().join());
        String pendingKey=store().read(account,yaml->{
            assertTrue(yaml.contains("jobs.s1.pending"));
            return yaml.getString("jobs.s1.pending.key");
        });
        store().update(account,yaml->yaml.set("jobs.s1.skills.COOKING",null));
        var restarted=jobs(economy,"s1",day);
        restarted.recover(account).toCompletableFuture().join();
        jobs(economy,"s1",day).recover(account).toCompletableFuture().join();
        assertEquals(4,economy.balance(account).toCompletableFuture().join());
        assertEquals(4,restarted.snapshot(account).toCompletableFuture().join().earnedMarks().get(Skill.MINING));
        boolean pending=store().read(account,yaml->yaml.contains("jobs.s1.pending"));
        assertFalse(pending);
        assertTrue(restarted.recordWork(account,Skill.MINING,1,"ore").toCompletableFuture().join().duplicate());
        assertNotNull(pendingKey);
    }
    @Test void preCookingSaveKeepsFractionalBonusAcrossMigrationAndRestart() {
        var economy=new InMemoryEconomyService();
        jobs(economy,"s1",day).recordWork(account,Skill.MINING,1,"ore0",20).toCompletableFuture().join();
        store().update(account,yaml->yaml.set("jobs.s1.skills.COOKING",null));
        for(int i=1;i<5;i++) jobs(economy,"s1",day).recordWork(account,Skill.MINING,1,"ore"+i,20).toCompletableFuture().join();
        // The normal 2/2/4 allowance earns 3 base Marks plus floor(3 * 20%).
        assertEquals(3,economy.balance(account).toCompletableFuture().join());
        assertEquals(0.6,store().read(account,yaml->yaml.getDouble("jobs.s1.skills.MINING.carry")),1E-9);
    }
    @Test void missingOldSkillOrPartialCookingEntryStillFailsClosedWithoutWriting() throws Exception {
        for(String brokenPath:new String[]{"MINING", "COOKING.work", "COOKING.earned", "COOKING.carry"}) {
            AccountId player=AccountId.random();
            var economy=new InMemoryEconomyService();
            jobs(economy,"s1",day).recordWork(player,Skill.MINING,1,"ore").toCompletableFuture().join();
            store().update(player,yaml->yaml.set("jobs.s1.skills."+brokenPath,null));
            Path file=directory.resolve("player_data").resolve(player+".yml");
            byte[] before=Files.readAllBytes(file);
            var restarted=jobs(economy,"s1",day);
            assertThrows(CompletionException.class,()->restarted.recover(player).toCompletableFuture().join());
            assertArrayEquals(before,Files.readAllBytes(file));
            assertEquals(1,economy.balance(player).toCompletableFuture().join());
        }
    }
}

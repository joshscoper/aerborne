package net.nerdsmp.probe;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService.TransactionReason;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.metrics.MetricsLedger;
import java.nio.file.Files;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Real service wiring, callback queues, panel publication and atomic persistence on disposable Paper. */
final class MetricsProbe {
    static Runnable begin(NerdSmpPlugin core,BiConsumer<Boolean,String> check) throws Exception {
        var metrics=core.metrics();
        var tick=metrics.getClass().getDeclaredMethod("tick");tick.setAccessible(true);tick.invoke(metrics);
        check.accept(metrics.snapshot().get("enabled").getAsBoolean(),"metrics starts independently of the disabled panel");
        var before=counters(metrics.snapshot());
        long xp=value(before,"xpSource.metricsprobe"),credit=value(before,"marks.credit.metrics.probe"),debit=value(before,"marks.debit.metrics.probe"),transfer=value(before,"marks.transfer.metrics.probe");
        var account=AccountId.random();var other=AccountId.random();var reason=new TransactionReason("metrics.probe","Disposable verification only");
        String key=UUID.randomUUID().toString();
        core.progression().awardExperience(account,Skill.SMITHING,37,"metricsprobe:"+key).toCompletableFuture().join();
        core.progression().awardExperience(account,Skill.SMITHING,37,"metricsprobe:"+key).toCompletableFuture().join();
        core.economy().credit(account,100,reason,key+":credit").toCompletableFuture().join();
        core.economy().credit(account,100,reason,key+":credit").toCompletableFuture().join();
        core.economy().debit(account,1000,reason,key+":refused").toCompletableFuture().join();
        core.economy().debit(account,10,reason,key+":debit").toCompletableFuture().join();
        core.economy().transfer(account,other,5,reason,key+":transfer").toCompletableFuture().join();
        tick.invoke(metrics);
        var after=counters(metrics.snapshot());
        check.accept(value(after,"xpSource.metricsprobe")==xp+37,"accepted XP crosses the real wrapper and queue once; duplicate reward excluded");
        check.accept(value(after,"marks.credit.metrics.probe")==credit+100,"committed currency credit counted once through the real economy service");
        check.accept(value(after,"marks.debit.metrics.probe")==debit+10,"refused debit does not inflate recorded spending");
        check.accept(value(after,"marks.transfer.metrics.probe")==transfer+5,"transfer tracked once, separately from credits and debits");
        var panelType=Class.forName("net.nerdsmp.paper.anticheat.PanelData");
        var constructor=panelType.getDeclaredConstructor(NerdSmpPlugin.class);constructor.setAccessible(true);var panel=constructor.newInstance(core);
        var publish=panelType.getDeclaredMethod("publish");publish.setAccessible(true);publish.invoke(panel);
        var status=panelType.getDeclaredMethod("status");status.setAccessible(true);
        var payload=JsonParser.parseString((String)status.invoke(panel)).getAsJsonObject().getAsJsonObject("metrics");
        check.accept(value(counters(payload),"xpSource.metricsprobe")==xp+37&&!payload.has("people")&&!payload.has("sessions"),"panel receives aggregates without per-player analytics identifiers");
        var save=metrics.getClass().getDeclaredMethod("save",boolean.class);save.setAccessible(true);save.invoke(metrics,false);
        return ()->{try {
            var path=core.getDataFolder().toPath().resolve("metrics/history-v1.json");
            var restored=MetricsLedger.restore(Files.readString(path),System.currentTimeMillis());
            check.accept(value(counters(restored.report(System.currentTimeMillis())),"xpSource.metricsprobe")==xp+37,"background atomic save restores the verified analytics totals");
        }catch(Exception error){throw new AssertionError("Metrics persistence failed",error);}};
    }
    private static JsonObject counters(JsonObject snapshot){return snapshot.getAsJsonObject("ranges").getAsJsonObject("7").getAsJsonObject("counters");}
    private static long value(JsonObject counters,String key){return counters.has(key)?counters.get(key).getAsLong():0;}
}

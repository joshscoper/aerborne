package net.nerdsmp.paper.job;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.storage.PlayerDataStore;
import org.bukkit.configuration.file.YamlConfiguration;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Daily allowance and payment outbox are committed before the idempotent credit. */
public final class DiminishingJobService {
    private final EconomyService economy;
    private final long fullRateUnits, halfRateUnits, quarterRateUnits, capacity;
    private final PlayerDataStore store;
    private final String path, season;
    private final Clock clock;
    private final Map<AccountId, State> states = new HashMap<>();
    private final Map<AccountId, CompletableFuture<?>> queues = new HashMap<>();
    private int queued;
    public DiminishingJobService(EconomyService economy,long full,long half,long quarter) {
        this(economy,full,half,quarter,null,"test",Clock.systemUTC());
    }
    public DiminishingJobService(EconomyService economy,long full,long half,long quarter,PlayerDataStore store,String season) {
        this(economy,full,half,quarter,store,season,Clock.systemUTC());
    }
    DiminishingJobService(EconomyService economy,long full,long half,long quarter,PlayerDataStore store,String season,Clock clock) {
        if(full<0 || half<0 || quarter<0 || !season.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid job configuration");
        capacity=Math.addExact(Math.addExact(full,half),quarter);
        if(capacity>100_000) throw new IllegalArgumentException("Job allowance is too large");
        this.economy=economy; fullRateUnits=full; halfRateUnits=half; quarterRateUnits=quarter;
        this.store=store; this.season=season; path="jobs."+season; this.clock=clock;
    }
    public CompletionStage<JobReward> recordWork(AccountId account,Skill skill,long units,String fingerprint) {
        return recordWork(account,skill,units,fingerprint,0);
    }
    public CompletionStage<JobReward> recordWork(AccountId account,Skill skill,long units,String fingerprint,double bonus) {
        Objects.requireNonNull(account); Objects.requireNonNull(skill); Objects.requireNonNull(fingerprint);
        if(units<=0 || fingerprint.isBlank() || fingerprint.length()>512 || !Double.isFinite(bonus) || bonus<0 || bonus>1000)
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid job activity"));
        return enqueue(account,()->settle(account).thenCompose(ignored->reserve(account,skill,units,fingerprint,bonus)));
    }
    public CompletionStage<Void> recover(AccountId account) { return enqueue(account,()->settle(account).thenApply(ignored->null)); }
    private synchronized <T> CompletionStage<T> enqueue(AccountId account,Supplier<CompletionStage<T>> action) {
        if(queued>=1024) return CompletableFuture.failedFuture(new IllegalStateException("Job settlement queue is full"));
        queued++;
        CompletableFuture<?> prior=queues.getOrDefault(account,CompletableFuture.completedFuture(null));
        CompletableFuture<T> next=prior.handle((value,error)->null).thenCompose(ignored->action.get()).toCompletableFuture();
        queues.put(account,next);
        next.whenComplete((value,error)-> { synchronized(this) { queued--; queues.remove(account,next); } });
        return next;
    }
    private synchronized CompletionStage<JobReward> reserve(AccountId account,Skill skill,long units,String fingerprint,double bonus) {
        State original=state(account), next=original.copy();
        LocalDate today=LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if(!next.day.equals(today)) next=new State(today);
        String token=hash(skill.name()+":"+fingerprint);
        long current=next.work.getOrDefault(skill,0L);
        if(next.fingerprints.contains(token)) return CompletableFuture.completedFuture(new JobReward(0,current,true));
        if(current>=capacity) return CompletableFuture.completedFuture(new JobReward(0,current,false));
        long after=current+Math.min(units,capacity-current);
        long base=rewardAt(after)-rewardAt(current);
        double fraction=next.carry.getOrDefault(skill,0D)+base*bonus/100D;
        long whole=(long)Math.floor(fraction+1E-9);
        next.work.put(skill,after); next.carry.put(skill,Math.max(0,fraction-whole)); next.fingerprints.add(token);
        long reward=Math.addExact(base,whole);
        if(reward>0) next.pending=new Payment(skill,reward,"job:"+season+":"+account+":"+next.day+":"+token);
        publish(account,next);
        if(reward==0) return CompletableFuture.completedFuture(new JobReward(0,after,false));
        return settle(account).thenApply(applied->new JobReward(applied,after,false));
    }
    private synchronized CompletionStage<Long> settle(AccountId account) {
        Payment pending=state(account).pending;
        if(pending==null) return CompletableFuture.completedFuture(0L);
        return economy.credit(account,pending.marks,new EconomyService.TransactionReason(
            "job."+pending.skill.name().toLowerCase(Locale.ROOT),"Diminishing daily job reward"),pending.key).thenApply(result-> {
            synchronized(this) {
                if(result.status()!=EconomyService.TransactionResult.Status.APPLIED && result.status()!=EconomyService.TransactionResult.Status.DUPLICATE)
                    throw new IllegalStateException("Job payment refused: "+result.status());
                State next=state(account).copy();
                if(!pending.equals(next.pending)) throw new IllegalStateException("Job payment changed during settlement");
                next.earned.merge(pending.skill,pending.marks,Math::addExact); next.pending=null;
                publish(account,next);
                return result.status()==EconomyService.TransactionResult.Status.APPLIED ? pending.marks : 0L;
            }
        });
    }
    public synchronized CompletionStage<JobSnapshot> snapshot(AccountId account) {
        State state=state(account);
        EnumMap<Skill,Long> work=new EnumMap<>(Skill.class), earned=new EnumMap<>(Skill.class);
        boolean today=state.day.equals(LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        for(Skill skill:Skill.values()) { work.put(skill,today?state.work.getOrDefault(skill,0L):0); earned.put(skill,today?state.earned.getOrDefault(skill,0L):0); }
        return CompletableFuture.completedFuture(new JobSnapshot(work,earned,capacity));
    }
    private State state(AccountId account) {
        return states.computeIfAbsent(account,id->store==null ? new State(LocalDate.now(clock.withZone(ZoneOffset.UTC))) : store.read(id,yaml->read(yaml,id)));
    }
    private State read(YamlConfiguration yaml,AccountId account) {
        if(!yaml.contains(path)) return new State(LocalDate.now(clock.withZone(ZoneOffset.UTC)));
        if(yaml.getInt(path+".schema")!=1) throw new IllegalStateException("Unsupported job state");
        State state=new State(LocalDate.parse(Objects.requireNonNull(yaml.getString(path+".day"))));
        for(Skill skill:Skill.values()) {
            String key=path+".skills."+skill.name();
            // Schema 1 predates Cooking. Its entire entry may legitimately be absent;
            // do not reset the other skills, activity receipts, or a pending payment.
            // Partially written Cooking entries still go through strict validation.
            // Enchanting was added later still and never records work; its entry may be absent too.
            if((skill==Skill.COOKING || skill==Skill.ENCHANTING) && !yaml.contains(key)) continue;
            if(!(yaml.get(key+".work") instanceof Number) || !(yaml.get(key+".earned") instanceof Number)
                || !(yaml.get(key+".carry") instanceof Number)) throw new IllegalStateException("Missing or malformed job counters for "+skill.name());
            long work=yaml.getLong(key+".work"), earned=yaml.getLong(key+".earned"); double carry=yaml.getDouble(key+".carry");
            if(work<0 || earned<0 || work>100_000 || !Double.isFinite(carry) || carry<0 || carry>=1) throw new IllegalStateException("Invalid daily job balance");
            state.work.put(skill,work); state.earned.put(skill,earned); state.carry.put(skill,carry);
        }
        List<String> fingerprints=yaml.getStringList(path+".fingerprints");
        if(!yaml.isList(path+".fingerprints") || yaml.getList(path+".fingerprints").size()!=fingerprints.size())
            throw new IllegalStateException("Malformed job activity receipts");
        if(fingerprints.size()>Skill.values().length*100_000 || fingerprints.stream().anyMatch(value->!value.matches("[a-f0-9]{64}")))
            throw new IllegalStateException("Invalid job activity receipts");
        state.fingerprints.addAll(fingerprints);
        if(yaml.contains(path+".pending")) {
            Skill skill=Skill.valueOf(Objects.requireNonNull(yaml.getString(path+".pending.skill")));
            long marks=yaml.getLong(path+".pending.marks"); String key=yaml.getString(path+".pending.key");
            String prefix="job:"+season+":"+account+":"+state.day+":";
            if(marks<=0 || marks>1_100_000 || key==null || !key.startsWith(prefix)
                || !key.substring(prefix.length()).matches("[a-f0-9]{64}")) throw new IllegalStateException("Invalid job outbox");
            state.pending=new Payment(skill,marks,key);
        }
        return state;
    }
    private void publish(AccountId account,State state) {
        if(store!=null) store.update(account,yaml-> {
            yaml.set(path,null); yaml.set(path+".schema",1); yaml.set(path+".day",state.day.toString());
            for(Skill skill:Skill.values()) {
                String key=path+".skills."+skill.name();
                yaml.set(key+".work",state.work.getOrDefault(skill,0L)); yaml.set(key+".earned",state.earned.getOrDefault(skill,0L));
                yaml.set(key+".carry",state.carry.getOrDefault(skill,0D));
            }
            yaml.set(path+".fingerprints",state.fingerprints.stream().sorted().toList());
            if(state.pending!=null) {
                yaml.set(path+".pending.skill",state.pending.skill.name()); yaml.set(path+".pending.marks",state.pending.marks);
                yaml.set(path+".pending.key",state.pending.key);
            }
        });
        states.put(account,state);
    }
    private long rewardAt(long units) {
        long full=Math.min(units,fullRateUnits), half=Math.min(Math.max(0,units-fullRateUnits),halfRateUnits);
        long quarter=Math.min(Math.max(0,units-fullRateUnits-halfRateUnits),quarterRateUnits);
        return full+half/2+quarter/4;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
    private record Payment(Skill skill,long marks,String key) { }
    private static final class State {
        final LocalDate day; final EnumMap<Skill,Long> work=new EnumMap<>(Skill.class), earned=new EnumMap<>(Skill.class);
        final EnumMap<Skill,Double> carry=new EnumMap<>(Skill.class); final Set<String> fingerprints=new HashSet<>(); Payment pending;
        State(LocalDate day) { this.day=day; }
        State copy() { State copy=new State(day); copy.work.putAll(work); copy.earned.putAll(earned); copy.carry.putAll(carry); copy.fingerprints.addAll(fingerprints); copy.pending=pending; return copy; }
    }
    public record JobReward(long marks,long workUnitsToday,boolean duplicate) { }
    public record JobSnapshot(Map<Skill,Long> workUnits,Map<Skill,Long> earnedMarks,long maximumWorkUnits) {
        public JobSnapshot { workUnits=Map.copyOf(workUnits); earnedMarks=Map.copyOf(earnedMarks); }
    }
}

package net.nerdsmp.paper.metrics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/** Main-thread aggregates. UTC calendar cohorts, never inferred from last-login timestamps. */
public final class MetricsLedger {
    private static final Gson JSON = new Gson();
    private static final long MAX = 9_000_000_000_000_000L;
    // A short uptime window cannot establish that a player failed to return that day.
    private static final long RETENTION_COVERAGE = 77_760_000L; // 90% of a UTC day.
    public static final int MAX_PEOPLE = 100_000, MAX_DAILY_PEOPLE = 20_000, MAX_COUNTERS = 512;
    public record Person(String cohort, String seen, int milestones) { }
    public record Visit(long online, long active) { }
    public record Session(long start, long last, boolean complete) { }
    public static final class Day {
        public Map<String,Visit> players = new HashMap<>();
        public Map<String,Long> counters = new TreeMap<>();
        public long coverage, samples, onlineSum, peak, closed, duration;
        public double tps, mspt;
        public long entities;
        public long[] lengths = new long[5];
        Day copy() {
            Day d=new Day(); d.players=Map.copyOf(players); d.counters=Map.copyOf(counters);
            d.coverage=coverage; d.samples=samples; d.onlineSum=onlineSum; d.peak=peak; d.closed=closed; d.duration=duration;
            d.tps=tps; d.mspt=mspt; d.entities=entities; d.lengths=lengths.clone(); return d;
        }
    }
    public static final class State {
        public int schema=1, retention=180;
        public long started, lost, sampleGap;
        public Map<String,Person> people=new HashMap<>();
        public Map<String,Day> days=new TreeMap<>();
        public Map<String,Session> sessions=new HashMap<>();
    }
    private final State data;
    private long pulseAt;
    public MetricsLedger(long now, int retention) {
        data=new State(); data.started=now; data.retention=Math.clamp(retention,90,365); pulseAt=now;
    }
    private MetricsLedger(State data,long now) {
        this.data=data; pulseAt=now;
        // Saved sessions have no trustworthy logout boundary after a restart/crash.
        long interrupted=data.sessions.values().stream().filter(Session::complete).count();
        data.sessions.clear(); counter(null,"session.interrupted",interrupted,now); trim(now);
    }
    public static MetricsLedger restore(String json,long now) {
        var tree=com.google.gson.JsonParser.parseString(json);
        if(!tree.isJsonObject()||!tree.getAsJsonObject().keySet().equals(Set.of("schema","retention","started","lost","sampleGap","people","days","sessions")))
            throw new IllegalArgumentException("Incomplete or unsupported metrics state");
        State s=JSON.fromJson(json,State.class);
        if(s==null||s.schema!=1||s.started<=0||s.started>now||s.lost<0||s.sampleGap<0||s.retention<90||s.retention>365||s.people==null||s.days==null||s.sessions==null
            ||s.people.size()>MAX_PEOPLE||s.days.size()>366||s.sessions.size()>MAX_DAILY_PEOPLE) throw new IllegalArgumentException("Invalid metrics state");
        for(var e:s.people.entrySet()) { UUID.fromString(e.getKey()); Person p=e.getValue();
            if(p==null||p.seen()==null||p.milestones()<0||p.milestones()>7)throw new IllegalArgumentException("Invalid metrics person");
            LocalDate.parse(p.seen()); if(p.cohort()!=null) LocalDate.parse(p.cohort());
        }
        for(var e:s.days.entrySet()) {
            LocalDate.parse(e.getKey()); Day d=e.getValue();
            if(d==null||d.players==null||d.counters==null||d.players.size()>MAX_DAILY_PEOPLE||d.counters.size()>MAX_COUNTERS+1
                ||d.lengths==null||d.lengths.length!=5||d.coverage<0||d.samples<0||d.closed<0||d.duration<0||d.onlineSum<0||d.peak<0||d.entities<0
                ||!Double.isFinite(d.tps)||!Double.isFinite(d.mspt)||d.tps<0||d.mspt<0) throw new IllegalArgumentException("Invalid metrics day");
            d.players.forEach((id,v)->{UUID.fromString(id);if(v==null||v.online()<0||v.active()<0||v.active()>v.online()||!s.people.containsKey(id))throw new IllegalArgumentException("Invalid visit");});
            d.counters.forEach((k,v)->{if(!validKey(k)||v==null||v<0)throw new IllegalArgumentException("Invalid counter");});
            for(long n:d.lengths)if(n<0)throw new IllegalArgumentException("Invalid session histogram");
        }
        s.sessions.forEach((id,v)->{UUID.fromString(id);if(v==null||v.start()<0||v.last()<v.start()||!s.people.containsKey(id))throw new IllegalArgumentException("Invalid saved session");});
        return new MetricsLedger(s,now);
    }
    public long started() { return data.started; }
    public void lost(long count) { data.lost=plus(data.lost,Math.max(0,count)); }
    private static boolean validKey(String key) { return key!=null&&key.matches("[a-zA-Z0-9_.:-]{1,150}"); }
    public static String dimension(String value) { String safe=value==null?"unknown":value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:-]","_"); return safe.substring(0,Math.min(64,safe.length())); }
    private static long plus(long a,long b) { return b>MAX-a?MAX:a+b; }
    public static String date(long now) { return Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate().toString(); }
    private Day day(long now) { return data.days.computeIfAbsent(date(now),ignored->new Day()); }
    private boolean visit(String id,long now) {
        Day d=day(now);
        if(!data.people.containsKey(id)||!d.players.containsKey(id)&&d.players.size()>=MAX_DAILY_PEOPLE) { lost(1); return false; }
        d.players.putIfAbsent(id,new Visit(0,0));
        Person p=data.people.get(id); if(!p.seen().equals(date(now)))data.people.put(id,new Person(p.cohort(),date(now),p.milestones()));
        return true;
    }
    public void join(UUID uuid,boolean newcomer,long now,boolean recovered) {
        String id=uuid.toString(); if(data.sessions.containsKey(id))return;
        if(!data.people.containsKey(id)) {
            if(data.people.size()>=MAX_PEOPLE) { lost(1); return; }
            data.people.put(id,new Person(newcomer&&!recovered?date(now):null,date(now),0));
        }
        if(!visit(id,now))return;
        data.sessions.put(id,new Session(now,now,!recovered));
        if(!recovered)counter(null,"session.started",1,now);
    }
    public void sample(UUID uuid,long now,boolean active,String world) {
        String id=uuid.toString(); Session s=data.sessions.get(id); if(s==null||now<=s.last())return;
        long start=Math.max(s.last(),now-30_000);
        segments(start,now,(at,elapsed)->{
            if(!visit(id,at))return;
            Day d=day(at); Visit v=d.players.get(id);
            d.players.put(id,new Visit(plus(v.online(),elapsed),plus(v.active(),active?elapsed:0)));
            if(active)counter(null,"world."+dimension(world)+".ms",elapsed,at);
        });
        data.sessions.put(id,new Session(s.start(),now,s.complete()));
    }
    public void quit(UUID uuid,long now) {
        Session s=data.sessions.remove(uuid.toString()); if(s==null||!s.complete())return;
        long duration=Math.max(0,now-s.start()); Day d=day(now); d.closed++; d.duration=plus(d.duration,duration);
        int bucket=duration<300_000?0:duration<900_000?1:duration<1_800_000?2:duration<3_600_000?3:4; d.lengths[bucket]++;
    }
    public void pulse(long now,int online,double tps,double mspt,long entities) {
        if(now>pulseAt) {
            data.sampleGap=plus(data.sampleGap,Math.max(0,now-pulseAt-30_000));
            segments(Math.max(pulseAt,now-30_000),now,(at,ms)->day(at).coverage=plus(day(at).coverage,ms)); pulseAt=now;
        }
        Day d=day(now); d.samples++; d.onlineSum+=Math.max(0,online); d.peak=Math.max(d.peak,online);
        d.tps+=Double.isFinite(tps)?Math.clamp(tps,0,20):0; d.mspt+=Double.isFinite(mspt)?Math.max(0,mspt):0;
        d.entities=Math.max(d.entities,entities); trim(now);
    }
    @FunctionalInterface private interface Segment { void add(long at,long elapsed); }
    private static void segments(long from,long to,Segment consumer) {
        while(from<to) { long end=Math.min(to,Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            consumer.add(from,end-from); from=end;
        }
    }
    public void counter(UUID player,String key,long amount,long now) {
        if(amount<=0||!validKey(key))return;
        Day d=day(now); String bucket=d.counters.containsKey(key)||d.counters.size()<MAX_COUNTERS?key:"overflow";
        if(bucket.equals("overflow")&&!key.equals(bucket))lost(1);
        d.counters.merge(bucket,Math.min(MAX,amount),MetricsLedger::plus);
        if(player!=null&&data.people.containsKey(player.toString())) {
            String id=player.toString(); Person p=data.people.get(id);
            int bit=key.startsWith("xp.")?1:key.equals("quest.completed")?2:key.equals("expedition.participated")?4:0;
            if(bit!=0)data.people.put(id,new Person(p.cohort(),p.seen(),p.milestones()|bit));
        }
    }
    public State copyForSave() {
        State s=new State(); s.started=data.started; s.retention=data.retention; s.lost=data.lost; s.sampleGap=data.sampleGap;
        s.people=Map.copyOf(data.people); s.sessions=Map.copyOf(data.sessions);
        data.days.forEach((date,day)->s.days.put(date,day.copy())); return s;
    }
    public static String encode(State state) { return JSON.toJson(state); }
    private void trim(long now) {
        String cutoff=LocalDate.parse(date(now)).minusDays(data.retention-1).toString();
        data.days.keySet().removeIf(day->day.compareTo(cutoff)<0);
        data.people.entrySet().removeIf(e->e.getValue().seen().compareTo(cutoff)<0&&!data.sessions.containsKey(e.getKey()));
    }
    public JsonObject report(long now) {
        LocalDate today=LocalDate.parse(date(now)); JsonObject root=new JsonObject();
        root.addProperty("enabled",true); root.addProperty("started",data.started); root.addProperty("updated",now);
        root.addProperty("timezone","UTC"); root.addProperty("retentionDays",data.retention); root.addProperty("lost",data.lost); root.addProperty("sampleGapMs",data.sampleGap);
        JsonArray days=new JsonArray();
        for(int i=89;i>=0;i--) {
            String key=today.minusDays(i).toString(); if(key.compareTo(date(data.started))<0)continue;
            Day d=data.days.get(key); JsonObject row=new JsonObject(); row.addProperty("day",key); row.addProperty("observed",d!=null&&d.samples>0);
            if(d!=null) {
                row.addProperty("players",d.players.size()); row.addProperty("newPlayers",d.players.keySet().stream().filter(id->key.equals(data.people.get(id).cohort())).count());
                row.addProperty("onlineMs",d.players.values().stream().mapToLong(Visit::online).sum()); row.addProperty("activeMs",d.players.values().stream().mapToLong(Visit::active).sum());
                row.addProperty("peak",d.peak); row.addProperty("coverageMs",d.coverage);
                row.addProperty("tps",d.samples==0?0:d.tps/d.samples); row.addProperty("mspt",d.samples==0?0:d.mspt/d.samples); row.addProperty("entities",d.entities);
            }
            days.add(row);
        }
        root.add("days",days); JsonObject ranges=new JsonObject();
        for(int length:new int[]{7,30,90})ranges.add(Integer.toString(length),summary(today,length));
        root.add("ranges",ranges); return root;
    }
    private JsonObject summary(LocalDate today,int length) {
        String from=today.minusDays(length-1).toString(), through=today.toString();
        Set<String> unique=new HashSet<>(); Map<String,Long> totals=new TreeMap<>(); long active=0,online=0,closed=0,duration=0,peak=0,coverage=0,samples=0; double tps=0,mspt=0;
        long[] histogram=new long[5];
        for(var entry:data.days.entrySet()) {
            if(entry.getKey().compareTo(from)<0||entry.getKey().compareTo(through)>0)continue;
            Day d=entry.getValue(); unique.addAll(d.players.keySet());
            active+=d.players.values().stream().mapToLong(Visit::active).sum(); online+=d.players.values().stream().mapToLong(Visit::online).sum();
            closed+=d.closed; duration+=d.duration; peak=Math.max(peak,d.peak); coverage+=d.coverage; samples+=d.samples; tps+=d.tps; mspt+=d.mspt;
            d.counters.forEach((key,value)->totals.merge(key,value,MetricsLedger::plus));
            for(int i=0;i<5;i++)histogram[i]+=d.lengths[i];
        }
        List<Map.Entry<String,Person>> newcomers=data.people.entrySet().stream().filter(e->e.getValue().cohort()!=null&&e.getValue().cohort().compareTo(from)>=0&&e.getValue().cohort().compareTo(through)<=0).toList();
        JsonObject result=new JsonObject(); result.addProperty("from",from); result.addProperty("through",through); result.addProperty("players",unique.size());
        result.addProperty("newPlayers",newcomers.size()); result.addProperty("returning",unique.stream().filter(id->data.people.get(id).cohort()==null||data.people.get(id).cohort().compareTo(from)<0).count());
        result.addProperty("activeMs",active); result.addProperty("onlineMs",online); result.addProperty("completedSessions",closed);
        if(closed>0)result.addProperty("averageSessionMs",(double)duration/closed);
        result.addProperty("peak",peak); result.addProperty("coverageMs",coverage); if(samples>0){result.addProperty("tps",tps/samples);result.addProperty("mspt",mspt/samples);}
        result.add("counters",JSON.toJsonTree(totals)); result.add("sessionLengths",JSON.toJsonTree(histogram));
        JsonObject milestones=new JsonObject(); milestones.addProperty("joined",newcomers.size());
        for(var step:Map.of("xp",1,"quest",2,"expedition",4).entrySet())milestones.addProperty(step.getKey(),newcomers.stream().filter(e->(e.getValue().milestones()&step.getValue())!=0).count());
        result.add("milestones",milestones);
        JsonArray retention=new JsonArray(); for(int offset:new int[]{1,7,30})retention.add(retention(newcomers,today,offset)); result.add("retention",retention);
        JsonArray cohorts=new JsonArray();
        newcomers.stream().map(e->e.getValue().cohort()).distinct().sorted(Comparator.reverseOrder()).forEach(cohort->{
            var members=newcomers.stream().filter(e->cohort.equals(e.getValue().cohort())).toList();
            JsonObject row=new JsonObject(); row.addProperty("day",cohort); row.addProperty("players",members.size());
            for(int offset:new int[]{1,7,30})row.add("d"+offset,retention(members,today,offset)); cohorts.add(row);
        }); result.add("cohorts",cohorts); return result;
    }
    private JsonObject retention(List<Map.Entry<String,Person>> people,LocalDate today,int offset) {
        long eligible=0,returned=0,pending=0,missing=0;
        for(var e:people) {
            LocalDate target=LocalDate.parse(e.getValue().cohort()).plusDays(offset);
            if(!target.isBefore(today)){pending++;continue;}
            Day d=data.days.get(target.toString()); if(d==null||d.samples==0||d.coverage<RETENTION_COVERAGE){missing++;continue;}
            eligible++; if(d.players.containsKey(e.getKey()))returned++;
        }
        JsonObject result=new JsonObject(); result.addProperty("day",offset); result.addProperty("eligible",eligible); result.addProperty("returned",returned);
        result.addProperty("pending",pending); result.addProperty("missing",missing); if(eligible>0)result.addProperty("rate",(double)returned/eligible); return result;
    }
}

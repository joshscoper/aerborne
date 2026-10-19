package net.nerdsmp.paper.zone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;

/** Main-thread, ephemeral reservations. Only successful living spawns occupy capacity. */
public final class ZoneSpawnPolicy {
    public static final int GLOBAL_CAP = 256;
    public static final int ATTEMPTS_PER_CYCLE = 8;
    public static final int POINT_PROBES_PER_CYCLE = 64;
    public static final long FAILURE_RETRY_MILLIS = 5_000;
    private record Point(String zoneId, int index) { }
    private record Reservation(Point point, int respawnSeconds) { }
    private final Map<UUID,Reservation> living = new HashMap<>();
    private final Map<Point,UUID> occupied = new HashMap<>();
    private final Map<Point,Long> readyAt = new HashMap<>();
    private final Map<String,Integer> nextPoints = new HashMap<>();
    private int nextZone;

    public int alive() { return living.size(); }
    public int alive(String zoneId) {
        return (int)living.values().stream().filter(value->value.point().zoneId().equals(zoneId)).count();
    }

    public boolean available(ZoneDefinition zone, int pointIndex, long nowMillis) {
        if (!zone.enabled() || zone.safe() || zone.cap() <= 0 || pointIndex < 0
            || pointIndex >= zone.points().size() || zone.mobs().isEmpty()
            || alive() >= GLOBAL_CAP || alive(zone.id()) >= zone.cap()) return false;
        Point point = new Point(zone.id(),pointIndex);
        return !occupied.containsKey(point) && readyAt.getOrDefault(point,Long.MIN_VALUE) <= nowMillis;
    }

    public boolean spawned(ZoneDefinition zone, int pointIndex, UUID entityId, long nowMillis) {
        if (living.containsKey(entityId) || !available(zone,pointIndex,nowMillis)) return false;
        Point point = new Point(zone.id(),pointIndex);
        living.put(entityId,new Reservation(point,zone.respawnSeconds()));
        occupied.put(point,entityId);
        readyAt.remove(point);
        return true;
    }

    /** Death, leash cleanup and unload all start the delay at removal, never at initial spawn. */
    public void removed(UUID entityId, long nowMillis) {
        Reservation reservation = living.remove(entityId);
        if (reservation == null) return;
        occupied.remove(reservation.point(),entityId);
        readyAt.put(reservation.point(),deadline(nowMillis,Math.max(1,reservation.respawnSeconds())*1_000L));
    }

    public void failed(ZoneDefinition zone, int pointIndex, long nowMillis) {
        readyAt.put(new Point(zone.id(),pointIndex),deadline(nowMillis,FAILURE_RETRY_MILLIS));
    }

    public int nextPoint(ZoneDefinition zone) {
        if (zone.points().isEmpty()) return -1;
        int next=Math.floorMod(nextPoints.getOrDefault(zone.id(),0),zone.points().size());
        nextPoints.put(zone.id(),(next+1)%zone.points().size());
        return next;
    }

    /** The cursor advances on every probe, including inactive and capped zones. */
    public ZoneDefinition nextZone(List<ZoneDefinition> zones) {
        if (zones.isEmpty()) return null;
        nextZone=Math.floorMod(nextZone,zones.size());
        ZoneDefinition zone=zones.get(nextZone);
        nextZone=(nextZone+1)%zones.size();
        return zone;
    }

    public void forget(String zoneId) {
        List<UUID> ids=living.entrySet().stream().filter(entry->entry.getValue().point().zoneId().equals(zoneId))
            .map(Map.Entry::getKey).toList();
        for(UUID id:ids) occupied.remove(living.remove(id).point());
        readyAt.keySet().removeIf(point->point.zoneId().equals(zoneId));
        nextPoints.remove(zoneId);
    }

    public void retain(Set<String> zoneIds) {
        Set<String> known=new HashSet<>(nextPoints.keySet());
        readyAt.keySet().forEach(point->known.add(point.zoneId()));
        living.values().forEach(reservation->known.add(reservation.point().zoneId()));
        known.removeAll(zoneIds);
        known.forEach(this::forget);
    }

    public void clear() {
        living.clear(); occupied.clear(); readyAt.clear(); nextPoints.clear(); nextZone=0;
    }

    public static ZoneDefinition.MobEntry choose(List<ZoneDefinition.MobEntry> table, RandomGenerator random) {
        if (table.isEmpty()) throw new IllegalArgumentException("An enabled combat zone needs a mob table");
        long total=0;
        for(var entry:table) {
            if(entry.weight()<=0) throw new IllegalArgumentException("Mob weights must be positive");
            total=Math.addExact(total,entry.weight());
        }
        long selected=random.nextLong(total);
        for(var entry:table) {
            selected-=entry.weight();
            if(selected<0) return entry;
        }
        throw new IllegalStateException("Weighted mob selection exceeded its table");
    }

    static long deadline(long now,long delay) {
        return now>Long.MAX_VALUE-delay?Long.MAX_VALUE:now+delay;
    }
}

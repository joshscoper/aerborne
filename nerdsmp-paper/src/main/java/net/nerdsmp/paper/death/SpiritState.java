package net.nerdsmp.paper.death;

import java.util.UUID;

/** Durable death state contains no items. Minecraft remains the sole owner of the retained inventory. */
public record SpiritState(Soul soul, Restoration restoration, long sicknessUntil, long protectedUntil) {
    public SpiritState {
        if (sicknessUntil < 0 || protectedUntil < 0 || restoration != null && soul == null) throw new IllegalArgumentException("Invalid spirit state");
    }
    public static SpiritState alive() { return new SpiritState(null, null, 0, 0); }
    public boolean ghost() { return soul != null; }
    public boolean sick(long now) { return sicknessUntil > now; }
    public boolean protectedNow(long now) { return protectedUntil > now; }
    public SpiritState resurrect(Point target, boolean healer, long now, long sicknessMillis, long protectionMillis) {
        if (soul == null || restoration != null) throw new IllegalArgumentException("No spirit awaiting resurrection");
        return new SpiritState(soul, new Restoration(target, healer ? 1 : .5), healer && soul.recoverable() ? Math.addExact(now, sicknessMillis) : 0, Math.addExact(now, protectionMillis));
    }
    public SpiritState restored() { return new SpiritState(null, null, sicknessUntil, protectedUntil); }
    public SpiritState endProtection() { return new SpiritState(soul, restoration, sicknessUntil, 0); }
    public static boolean within(Point left, Point right, double radius) {
        if (!Double.isFinite(radius) || radius < 0 || !left.world().equals(right.world())) return false;
        double x=left.x()-right.x(), y=left.y()-right.y(), z=left.z()-right.z();
        return x*x+y*y+z*z <= radius*radius;
    }
    public static String direction(Point from,Point to){
        if(!from.world().equals(to.world()))return "Other world";
        double x=to.x()-from.x(),z=to.z()-from.z();if(Math.hypot(x,z)<1)return "Here";
        return new String[]{"N","NE","E","SE","S","SW","W","NW"}[Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(x,-z))/45),8)];
    }
    public record Point(UUID world, double x, double y, double z, float yaw, float pitch) {
        public Point {
            if (world == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(x)>30_000_000 || Math.abs(z)>30_000_000 || Math.abs(y)>8192) throw new IllegalArgumentException("Invalid spirit location");
        }
    }
    public record Abilities(String mode, boolean invulnerable, boolean allowFlight, boolean flying, Point compass) {
        public Abilities {
            if (!java.util.Set.of("SURVIVAL", "ADVENTURE").contains(mode) || compass == null || flying && !allowFlight) throw new IllegalArgumentException("Invalid saved player abilities");
        }
    }
    public record Soul(UUID id, Point corpse, Point sanctuary, String sanctuaryName, boolean recoverable, long diedAt, long readyAt, Abilities abilities) {
        public Soul {
            if (id==null || corpse==null || sanctuary==null || abilities==null || sanctuaryName==null || sanctuaryName.isBlank() || sanctuaryName.length()>80
                || diedAt<0 || readyAt<diedAt) throw new IllegalArgumentException("Invalid soul record");
        }
        public boolean ready(long now) { return now>=readyAt; }
    }
    public record Restoration(Point destination, double healthFraction) {
        public Restoration { if(destination==null || !Double.isFinite(healthFraction) || healthFraction<=0 || healthFraction>1) throw new IllegalArgumentException("Invalid resurrection"); }
    }
}

package net.nerdsmp.paper.expedition.combat;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * Warning shapes drawn before an attack lands: rings, cones, lines and markers, in coloured dust.
 * This is the one place expedition code spawns a particle that needs data, and it always passes it.
 */
public final class Telegraph {
    public static final Color DANGER = Color.fromRGB(230, 40, 30);
    public static final Color FIRE = Color.fromRGB(255, 140, 20);
    public static final Color FROST = Color.fromRGB(120, 200, 255);
    public static final Color POISON = Color.fromRGB(90, 200, 60);
    public static final Color SHADOW = Color.fromRGB(70, 20, 90);
    public static final Color HOLY = Color.fromRGB(255, 230, 120);

    private Telegraph() { }

    private static void dust(World world, double x, double y, double z, Color color, float size) {
        world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(color, size));
    }

    /** Shards of a block flying off, for floors breaking and bones gathering. */
    public static void shards(Location at, int count, double spread, org.bukkit.Material block) {
        at.getWorld().spawnParticle(Particle.BLOCK, at, count, spread, .1, spread, 0, block.createBlockData());
    }

    /** A flat ring on the ground around {@code center}. */
    public static void ring(Location center, double radius, Color color) {
        World world = center.getWorld();
        int points = Math.max(12, (int) (radius * 10));
        for (int index = 0; index < points; index++) {
            double angle = 2 * Math.PI * index / points;
            dust(world, center.getX() + Math.cos(angle) * radius, center.getY() + .15, center.getZ() + Math.sin(angle) * radius, color, 1.4F);
        }
    }

    /** A filled disc, for markers that must read from any angle. */
    public static void disc(Location center, double radius, Color color) {
        ring(center, radius, color);
        ring(center, radius * .55, color);
        dust(center.getWorld(), center.getX(), center.getY() + .15, center.getZ(), color, 2F);
    }

    /** A cone in front of {@code origin} along its facing. */
    public static void cone(Location origin, double radius, double degrees, Color color) {
        World world = origin.getWorld();
        Vector facing = origin.getDirection().setY(0);
        if (facing.lengthSquared() < 1e-6) facing = new Vector(0, 0, 1);
        facing.normalize();
        double half = Math.toRadians(degrees / 2);
        for (double r = 1; r <= radius; r += .8)
            for (double a = -half; a <= half; a += .18) {
                Vector step = facing.clone().rotateAroundY(a).multiply(r);
                dust(world, origin.getX() + step.getX(), origin.getY() + .2, origin.getZ() + step.getZ(), color, 1.2F);
            }
    }

    /** A straight line between two points. */
    public static void line(Location from, Location to, Color color) {
        World world = from.getWorld();
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 1e-6) return;
        path.normalize();
        for (double step = 0; step <= length; step += .5)
            dust(world, from.getX() + path.getX() * step, from.getY() + .2 + path.getY() * step, from.getZ() + path.getZ() * step, color, 1.3F);
    }

    /** True when {@code point} lies inside the cone in front of {@code origin}. */
    public static boolean inCone(Location origin, Location point, double radius, double degrees) {
        Vector facing = origin.getDirection().setY(0);
        Vector to = point.toVector().subtract(origin.toVector()).setY(0);
        if (to.lengthSquared() > radius * radius) return false;
        if (to.lengthSquared() < .25) return true;
        if (facing.lengthSquared() < 1e-6) return true;
        return Math.toDegrees(facing.angle(to)) <= degrees / 2;
    }
}

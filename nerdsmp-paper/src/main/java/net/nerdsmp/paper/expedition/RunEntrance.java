package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.combat.Telegraph;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Entrance;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.EntranceStyle;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Point;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * How bosses arrive. A boss is held under the floor, untouchable, brought up with its effects and let
 * loose once every camera is back. A second attempt takes a second and doesn't wait. {@code REANIMATE}
 * is the long form: a pile of bones lies in the room from the start, the room's soul fires are drawn
 * down into it one by one, and the bones lift and close around the boss as it comes up.
 */
final class RunEntrance {
    interface Host {
        World world();
        boolean live();
        boolean cutsceneRunning();
        void log(String line);
        /** The boss is free to fight. */
        void loose(Room room, String id, LivingEntity boss);
    }

    private static final double DEPTH = 3;
    private static final int PIECES = 26, WISP_TICKS = 24;
    private static final List<Material> BONES = List.of(Material.BONE, Material.BONE, Material.BONE, Material.BONE_BLOCK, Material.SKELETON_SKULL);

    /** One bone of a pile: where it lies, and how it rises. */
    private record Piece(ItemDisplay display, double angle, double radius, double height, double start) { }

    private record Wisp(BlockDisplay flame, Location from, int born) { }

    private final Plugin plugin;
    private final Host host;
    private final Set<String> entered = new HashSet<>();
    private final Map<String, List<Piece>> piles = new HashMap<>();

    RunEntrance(Plugin plugin, Host host) {
        this.plugin = plugin;
        this.host = host;
    }

    /** Lays the bone piles, so they're lying in the room before anyone gets there. */
    void begin(List<Room> rooms) {
        for (Room room : rooms) {
            Entrance entrance = room.encounter().entrance();
            if (entrance == null || entrance.style() != EntranceStyle.REANIMATE || room.points().isEmpty()) continue;
            Point at = room.points().getFirst();
            ThreadLocalRandom random = ThreadLocalRandom.current();
            List<Piece> pile = new ArrayList<>();
            for (int index = 0; index < PIECES; index++) {
                double angle = random.nextDouble(Math.TAU), radius = .25 + Math.sqrt(random.nextDouble()) * 1.9;
                Location rest = spot(at, angle, radius, .1 + random.nextDouble(.25));
                ItemStack bone = new ItemStack(BONES.get(index % BONES.size()));
                float size = (float) random.nextDouble(.6, .95);
                ItemDisplay display = host.world().spawn(rest, ItemDisplay.class, piece -> {
                    piece.setItemStack(bone);
                    piece.setPersistent(false);
                    piece.setTeleportDuration(2);
                    piece.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY((float) angle * 3).rotateX((float) Math.PI / 2),
                        new Vector3f(size, size, size), new Quaternionf()));
                });
                pile.add(new Piece(display, angle, radius, random.nextDouble(.2, 2.2), random.nextDouble(.3)));
            }
            piles.put(room.id(), pile);
        }
    }

    private Location spot(Point at, double angle, double radius, double height) {
        return new Location(host.world(), at.x() + .5 + Math.cos(angle) * radius, at.y() + height, at.z() + .5 + Math.sin(angle) * radius);
    }

    /** Starts a boss's arrival; returns how many ticks it takes, at least. */
    int enter(Room room, String id, LivingEntity boss) {
        Entrance entrance = room.encounter().entrance();
        boolean first = entered.add(room.id());
        int delay = first ? entrance.delaySeconds() * 20 : 0, length = first ? entrance.seconds() * 20 : 20;
        boolean reanimating = first && entrance.style() == EntranceStyle.REANIMATE;
        Location home = boss.getLocation();
        boss.setInvulnerable(true);
        if (boss instanceof Mob mob) mob.setAI(false);
        boss.teleport(home.clone().subtract(0, DEPTH, 0));
        host.log(id + " entering");
        List<Wisp> wisps = new ArrayList<>();
        int[] clock = {-delay};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!host.live() || !boss.isValid()) {
                task.cancel();
                wisps.forEach(wisp -> wisp.flame().remove());
                clear(room, entrance);
                return;
            }
            int tick = clock[0]++;
            if (tick < 0) return;
            if (tick < length) {
                if (reanimating) reanimate(room, entrance, boss, home, tick, length, wisps);
                else rise(boss, home, tick, length);
            }
            if (tick == length) {
                boss.teleport(home);
                arrive(room, entrance, home);
            }
            if (tick < length || host.cutsceneRunning() && tick < length + 400) return;
            task.cancel();
            boss.setInvulnerable(false);
            if (boss instanceof Mob mob) mob.setAI(true);
            host.log(id + " loose");
            host.loose(room, id, boss);
        }, 0L, 1L);
        return delay + length;
    }

    private static double eased(double t) { return t * t * (3 - 2 * t); }

    private void rise(LivingEntity boss, Location home, int tick, int length) {
        double done = tick / (double) length;
        boss.teleport(home.clone().subtract(0, DEPTH * (1 - eased(done)), 0));
        Location floor = home.clone().add(0, .15, 0);
        Telegraph.shards(floor, 10, .7, Material.DEEPSLATE_BRICKS);
        host.world().spawnParticle(Particle.SOUL_FIRE_FLAME, floor, 6, .6, .1, .6, .02);
        if (tick % 12 == 0) host.world().playSound(home, Sound.BLOCK_DEEPSLATE_BREAK, 1F, .5F + (float) done * .4F);
    }

    /** The fires go out one by one, their flames fly into the bones, and the bones close in around the boss. */
    private void reanimate(Room room, Entrance entrance, LivingEntity boss, Location home, int tick, int length, List<Wisp> wisps) {
        World world = host.world();
        double done = tick / (double) length;
        Location heart = home.clone().add(0, .8, 0);
        List<Point> fires = entrance.sources();
        for (int index = 0; index < fires.size(); index++) {
            if (tick != index * (int) (length * .3) / fires.size()) continue;
            Block fire = world.getBlockAt(fires.get(index).x(), fires.get(index).y(), fires.get(index).z());
            light(fire, false);
            Location from = fire.getLocation().add(.5, .5, .5);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, from, 26, .2, .4, .2, .06);
            world.playSound(from, Sound.BLOCK_FIRE_EXTINGUISH, 1.2F, .6F);
            wisps.add(new Wisp(world.spawn(from, BlockDisplay.class, flame -> {
                flame.setBlock(Material.SOUL_FIRE.createBlockData());
                flame.setPersistent(false);
                flame.setTeleportDuration(1);
                flame.setBrightness(new Display.Brightness(15, 15));
                flame.setTransformation(new Transformation(new Vector3f(-.35F, -.35F, -.35F), new Quaternionf(), new Vector3f(.7F, .7F, .7F), new Quaternionf()));
            }), from, tick));
        }
        wisps.removeIf(wisp -> {
            double flown = (tick - wisp.born()) / (double) WISP_TICKS;
            if (flown >= 1) {
                wisp.flame().remove();
                world.spawnParticle(Particle.SOUL, heart, 14, .5, .4, .5, .05);
                world.playSound(heart, Sound.PARTICLE_SOUL_ESCAPE, 1.5F, .7F);
                return true;
            }
            Location at = wisp.from().clone().add(heart.toVector().subtract(wisp.from().toVector()).multiply(eased(flown)));
            at.add(0, Math.sin(flown * Math.PI) * 3, 0);
            wisp.flame().teleport(at);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 4, .1, .1, .1, .01);
            return false;
        });
        if (tick % 2 == 0) for (Piece piece : piles.getOrDefault(room.id(), List.of())) {
            double lifted = Math.clamp((done - .3 - piece.start()) / .4, 0D, 1D);
            if (lifted <= 0) continue;
            double radius = piece.radius() * (1 - lifted * .7), angle = piece.angle() + lifted * 5 + done * 6;
            piece.display().teleport(heart.clone().add(Math.cos(angle) * radius, -.7 + eased(lifted) * (.9 + piece.height()), Math.sin(angle) * radius));
        }
        if (done > .3 && tick % 3 == 0) Telegraph.shards(heart, 4, .9, Material.BONE_BLOCK);
        if (tick % 14 == 0) world.playSound(home, done < .3 ? Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD : Sound.ENTITY_SKELETON_STEP, 1.1F, .5F + (float) done * .5F);
        double rise = Math.max(0, (done - .7) / .3);
        boss.teleport(home.clone().subtract(0, DEPTH * (1 - eased(rise)), 0));
    }

    private void arrive(Room room, Entrance entrance, Location home) {
        World world = host.world();
        Location body = home.clone().add(0, 1, 0);
        world.spawnParticle(Particle.SOUL, body, 45, .7, .9, .7, .06);
        world.spawnParticle(Particle.EXPLOSION, body, 2, .3, .3, .3, 0);
        world.playSound(home, Sound.ENTITY_WITHER_SKELETON_DEATH, 1.2F, .5F);
        clear(room, entrance);
    }

    /** The pile is gone, and the fires come back, all at once. */
    private void clear(Room room, Entrance entrance) {
        List<Piece> pile = piles.remove(room.id());
        if (pile == null) return;
        pile.forEach(piece -> piece.display().remove());
        for (Point fire : entrance.sources()) {
            Block block = host.world().getBlockAt(fire.x(), fire.y(), fire.z());
            light(block, true);
            host.world().spawnParticle(Particle.SOUL_FIRE_FLAME, block.getLocation().add(.5, .6, .5), 30, .2, .5, .2, .08);
        }
        if (!entrance.sources().isEmpty()) {
            Point first = entrance.sources().getFirst();
            host.world().playSound(new Location(host.world(), first.x(), first.y(), first.z()), Sound.ITEM_FIRECHARGE_USE, 1.4F, .6F);
        }
    }

    private static void light(Block block, boolean on) {
        if (!(block.getBlockData() instanceof Lightable lightable)) return;
        lightable.setLit(on);
        block.setBlockData(lightable, false);
    }

    void close() {
        piles.values().forEach(pile -> pile.forEach(piece -> piece.display().remove()));
        piles.clear();
    }
}

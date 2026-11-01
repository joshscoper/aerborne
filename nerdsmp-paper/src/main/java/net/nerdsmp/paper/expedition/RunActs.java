package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.combat.Telegraph;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Mines' boss entrances, staged in display entities. Each act runs once a tick for its length, and the boss is
 * moved by hand until the last tick, when {@link RunEntrance} sets it loose. {@code set} is the act's props: they're
 * removed when the act ends, however it ends.
 */
final class RunActs {
    private final Plugin plugin;
    private final World world;
    /** What each act has made, past the list of props: the moving parts it needs by name. */
    private final Map<String, Display> named = new HashMap<>();

    RunActs(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    private static double clamp(double value) { return Math.clamp(value, 0D, 1D); }

    private static double eased(double t) { return t * t * (3 - 2 * t); }

    private BlockDisplay box(Location root, Material block, float x, float y, float z, float wide, float high, float deep, int light) {
        return world.spawn(root, BlockDisplay.class, display -> {
            display.setBlock(block.createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(1);
            display.setBrightness(new Display.Brightness(light, light));
            display.setTransformation(new Transformation(new Vector3f(x, y, z), new Quaternionf(), new Vector3f(wide, high, deep), new Quaternionf()));
        });
    }

    private static void move(Display display, Transformation to, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(to);
    }

    /** Sends a piece spinning off along {@code x, z}, shrinking away to nothing as it lands. */
    private static void fling(Display piece, double x, double up, double z, int ticks) {
        Transformation now = piece.getTransformation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Quaternionf spin = new Quaternionf().rotateXYZ((float) random.nextDouble(-2, 2), (float) random.nextDouble(-2, 2), (float) random.nextDouble(-2, 2));
        move(piece, new Transformation(new Vector3f(now.getTranslation()).add((float) x, (float) up, (float) z), spin, new Vector3f(now.getScale()).mul(.25F), new Quaternionf()), ticks);
    }

    private static void teleportAll(List<Display> set, Location to) {
        for (Display piece : set) piece.teleport(to);
    }

    /** A blast of rubble in display blocks, thrown out from {@code at}. */
    private void debris(List<Display> set, Location at, Material[] kinds, int count, double reach, int ticks) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int index = 0; index < count; index++) {
            float size = (float) random.nextDouble(.4, 1.1);
            BlockDisplay piece = box(at, kinds[index % kinds.length], -size / 2, 0, -size / 2, size, size, size, 13);
            set.add(piece);
            double angle = random.nextDouble(Math.TAU), far = random.nextDouble(reach * .4, reach);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (piece.isValid()) fling(piece, Math.cos(angle) * far, random.nextDouble(1, reach * .7), Math.sin(angle) * far, ticks);
            }, 2L);
        }
    }

    /** A wave of fire running out across the floor from {@code at}, plate by plate. */
    private void shockwave(List<Display> set, Location at, double reach, int ticks) {
        int plates = 28;
        for (int index = 0; index < plates; index++) {
            double angle = index * Math.TAU / plates;
            Quaternionf facing = new Quaternionf().rotateY((float) (-angle + Math.PI / 2));
            BlockDisplay plate = world.spawn(at.clone().add(0, .1, 0), BlockDisplay.class, display -> {
                display.setBlock(Material.MAGMA_BLOCK.createBlockData());
                display.setPersistent(false);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTransformation(new Transformation(new Vector3f((float) Math.cos(angle) * 1.5F, 0, (float) Math.sin(angle) * 1.5F), facing, new Vector3f(1.4F, .9F, .5F), new Quaternionf()));
            });
            set.add(plate);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (plate.isValid()) move(plate, new Transformation(new Vector3f((float) (Math.cos(angle) * reach), 0, (float) (Math.sin(angle) * reach)), facing, new Vector3f(3.4F, .05F, .3F), new Quaternionf()), ticks);
            }, 2L);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Grell: the hoist cage, cut loose from the top of the cave.
    // ---------------------------------------------------------------------------------------------

    private static final double CAGE_DROP = 24, CAGE_BACK = 3, CAGE_LANDS = .5, CAGE_OPENS = .72;

    void hoist(List<Display> set, LivingEntity boss, Location home, int tick, int length) {
        double done = tick / (double) length;
        if (set.isEmpty()) {
            float high = (float) boss.getHeight() + 1.2F;
            set.add(box(home, Material.STRIPPED_SPRUCE_WOOD, -2.5F, 0, -2.5F, 5, .35F, 5, 11));
            set.add(box(home, Material.DARK_OAK_PLANKS, -2.5F, high, -2.5F, 5, .35F, 5, 11));
            for (int[] post : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                set.add(box(home, Material.DARK_OAK_LOG, post[0] * 2.3F - .2F, 0, post[1] * 2.3F - .2F, .4F, high, .4F, 11));
                set.add(box(home, Material.IRON_CHAIN, post[0] * 2F - .5F, high, post[1] * 2F - .5F, 1, 22, 1, 9));
            }
            for (int slat = 0; slat < 4; slat++) {
                float at = .5F + slat * (high - .6F) / 4;
                set.add(box(home, Material.SPRUCE_PLANKS, 2.3F, at, -2.5F, .2F, .45F, 5, 11));
                set.add(box(home, Material.SPRUCE_PLANKS, -2.5F, at, -2.5F, .2F, .45F, 5, 11));
                set.add(box(home, Material.SPRUCE_PLANKS, -2.5F, at, 2.3F, 5, .45F, .2F, 11));
            }
            BlockDisplay gate = box(home, Material.SPRUCE_TRAPDOOR, -2.3F, 0, -2.5F, 4.6F, high, .2F, 11);
            set.add(gate);
            named.put("gate", gate);
        }
        double fall = clamp(done / CAGE_LANDS);
        Location landing = home.clone().add(0, 0, CAGE_BACK);
        Location at = landing.clone().add(0, CAGE_DROP * (1 - fall * fall), 0);
        if (tick == 0) boss.setVisibleByDefault(true);
        if (done <= CAGE_LANDS + .01) {
            teleportAll(set, at);
            boss.teleport(at.clone().add(0, .4, 0));
            if (tick % 3 == 0) world.spawnParticle(Particle.CRIT, at.clone().add(0, 8, 0), 6, 2, .4, 2, .1);
            if (tick % 5 == 0) world.playSound(at, Sound.BLOCK_CHAIN_STEP, 1.8F, .5F + (float) fall);
        }
        if (tick == 2) {
            world.playSound(home, Sound.BLOCK_CHAIN_BREAK, 2F, .5F);
            world.playSound(home, Sound.BLOCK_BELL_USE, 2F, .6F);
        }
        if (tick == (int) (length * CAGE_LANDS)) {
            world.spawnParticle(Particle.EXPLOSION_EMITTER, landing, 1);
            world.spawnParticle(Particle.CLOUD, landing.clone().add(0, .4, 0), 90, 4, .3, 4, .12);
            Telegraph.shards(landing.clone().add(0, .4, 0), 80, 4, Material.SPRUCE_PLANKS);
            world.playSound(home, Sound.ENTITY_GENERIC_EXPLODE, 1.6F, .6F);
            world.playSound(home, Sound.BLOCK_ANVIL_LAND, 1.6F, .5F);
        }
        if (tick == (int) (length * CAGE_OPENS)) {
            Display gate = named.remove("gate");
            if (gate != null) {
                Transformation shut = gate.getTransformation();
                move(gate, new Transformation(new Vector3f(shut.getTranslation()).add(0, .3F, 0), new Quaternionf().rotateX((float) -Math.PI / 2), shut.getScale(), new Quaternionf()), 7);
            }
            world.playSound(home, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.8F, .7F);
        }
        if (done > CAGE_OPENS) {
            double walked = eased(clamp((done - CAGE_OPENS) / (1 - CAGE_OPENS)));
            boss.teleport(landing.clone().add(0, 0, -walked * CAGE_BACK));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The Blastmaster: in on a powder cart, off it at the last moment, and it goes up behind him.
    // ---------------------------------------------------------------------------------------------

    private static final double CART_JUMPS = .46, CART_BLOWS = .68;

    void powder(List<Display> set, LivingEntity boss, Location home, List<Location> track, int tick, int length) {
        double done = tick / (double) length;
        Location from = track.get(0), to = track.get(1);
        if (set.isEmpty()) {
            set.add(box(from, Material.BLACKSTONE, -1.4F, .3F, -.7F, 2.8F, .5F, 1.4F, 12));
            set.add(box(from, Material.SPRUCE_PLANKS, -1.5F, .8F, -.75F, 3, .8F, .12F, 12));
            set.add(box(from, Material.SPRUCE_PLANKS, -1.5F, .8F, .63F, 3, .8F, .12F, 12));
            set.add(box(from, Material.SPRUCE_PLANKS, -1.5F, .8F, -.75F, .12F, .8F, 1.5F, 12));
            set.add(box(from, Material.SPRUCE_PLANKS, 1.38F, .8F, -.75F, .12F, .8F, 1.5F, 12));
            for (int index = 0; index < 3; index++) set.add(box(from, Material.TNT, -1.25F + index * .85F, .85F, -.4F, .8F, .8F, .8F, 13));
            for (int[] wheel : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) set.add(box(from, Material.IRON_BLOCK, wheel[0] * .9F - .22F, 0, wheel[1] * .78F - .1F, .44F, .44F, .2F, 12));
        }
        double run = eased(clamp(done / CART_BLOWS)) * .4 + clamp(done / CART_BLOWS) * .6;
        Location at = from.clone().add(to.toVector().subtract(from.toVector()).multiply(run));
        if (done < CART_BLOWS) {
            teleportAll(set, at);
            world.spawnParticle(Particle.SMALL_FLAME, at.clone().add(-1.4, .2, 0), 4, .1, .1, .3, .01);
            world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 1.8, 0), 1, .3, .1, .3, .01);
            if (tick % 3 == 0) world.playSound(at, Sound.ENTITY_MINECART_INSIDE, 1.6F, 1.2F);
        }
        if (tick == 0) {
            boss.setVisibleByDefault(true);
            world.playSound(from, Sound.BLOCK_BELL_USE, 2F, .8F);
        }
        if (done < CART_JUMPS) boss.teleport(at.clone().add(0, 1.7, 0));
        else {
            double leap = eased(clamp((done - CART_JUMPS) / .16));
            Location jump = at.clone().add(0, 1.7, 0);
            Location landed = jump.clone().add(home.toVector().subtract(jump.toVector()).multiply(leap)).add(0, Math.sin(leap * Math.PI) * 4, 0);
            landed.setYaw(home.getYaw());
            boss.teleport(landed);
        }
        if (tick == (int) (length * CART_BLOWS)) {
            set.forEach(Display::remove);
            set.clear();
            world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 2, 1.5, .5, 1.5, 0);
            world.spawnParticle(Particle.FLAME, at.clone().add(0, 1, 0), 200, 3, 1.5, 3, .2);
            world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2F, .5F);
            world.playSound(at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.6F, .6F);
            debris(set, at.clone().add(0, 1, 0), new Material[]{Material.SPRUCE_PLANKS, Material.TNT, Material.BLACKSTONE, Material.IRON_BLOCK}, 26, 9, 12);
            shockwave(set, at, 14, 14);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The Slag Hound: up through the crust of the pit.
    // ---------------------------------------------------------------------------------------------

    private static final double CRUST_BREAKS = .45;

    void erupt(List<Display> set, LivingEntity boss, Location home, int tick, int length) {
        double done = tick / (double) length;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Location floor = home.clone().add(0, .02, 0);
        if (set.isEmpty()) {
            for (int ring = 0; ring < 2; ring++) for (int index = 0; index < 8 + ring * 4; index++) {
                double angle = index * Math.TAU / (8 + ring * 4), radius = 1.4 + ring * 2;
                float x = (float) (Math.cos(angle) * radius), z = (float) (Math.sin(angle) * radius);
                set.add(box(floor, index % 3 == 0 ? Material.MAGMA_BLOCK : Material.BLACKSTONE, x - .9F, 0, z - .9F, 1.8F, .3F, 1.8F, 13));
            }
        }
        if (done < CRUST_BREAKS) {
            for (Display plate : set) {
                Transformation now = plate.getTransformation();
                Vector3f base = new Vector3f(now.getTranslation()).setComponent(1, (float) (random.nextDouble(0, .12 + done * .9)));
                move(plate, new Transformation(base, now.getLeftRotation(), now.getScale(), new Quaternionf()), 2);
            }
            world.spawnParticle(Particle.LAVA, floor, 3, 2.5, .1, 2.5, 0);
            if (tick % 8 == 0) world.playSound(home, Sound.ENTITY_WARDEN_HEARTBEAT, 2F, .5F + (float) done);
        }
        if (tick == (int) (length * CRUST_BREAKS)) {
            for (Display plate : set) {
                Vector3f out = new Vector3f(plate.getTransformation().getTranslation()).normalize();
                fling(plate, out.x * 8, random.nextDouble(4, 9), out.z * 8, 12);
            }
            BlockDisplay spout = box(floor, Material.ORANGE_CONCRETE, -1.2F, 0, -1.2F, 2.4F, .2F, 2.4F, 15);
            BlockDisplay heart = box(floor, Material.SHROOMLIGHT, -.7F, 0, -.7F, 1.4F, .2F, 1.4F, 15);
            set.add(spout);
            set.add(heart);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (spout.isValid()) move(spout, new Transformation(new Vector3f(-1.6F, 0, -1.6F), new Quaternionf(), new Vector3f(3.2F, 12, 3.2F), new Quaternionf()), 6);
                if (heart.isValid()) move(heart, new Transformation(new Vector3f(-.9F, .1F, -.9F), new Quaternionf(), new Vector3f(1.8F, 14, 1.8F), new Quaternionf()), 6);
            }, 2L);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (spout.isValid()) move(spout, new Transformation(new Vector3f(-.2F, 0, -.2F), new Quaternionf(), new Vector3f(.4F, .1F, .4F), new Quaternionf()), 16);
                if (heart.isValid()) move(heart, new Transformation(new Vector3f(-.1F, 0, -.1F), new Quaternionf(), new Vector3f(.2F, .1F, .2F), new Quaternionf()), 16);
            }, 12L);
            boss.setVisibleByDefault(true);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, home, 1);
            world.spawnParticle(Particle.LAVA, home.clone().add(0, 2, 0), 90, 2, 2, 2, 0);
            world.playSound(home, Sound.ENTITY_RAVAGER_ROAR, 2F, .5F);
            world.playSound(home, Sound.ENTITY_GENERIC_EXPLODE, 1.6F, .5F);
        }
        if (done >= CRUST_BREAKS) {
            double up = eased(clamp((done - CRUST_BREAKS) / .3));
            Location at = home.clone().subtract(0, 4 * (1 - up), 0);
            // It shakes the slag off like water.
            double shaking = clamp((done - .72) / .2);
            at.setYaw((float) (home.getYaw() + (shaking > 0 && shaking < 1 ? Math.sin(tick * 1.4) * 34 : 0)));
            boss.teleport(at);
            world.spawnParticle(Particle.FALLING_LAVA, at.clone().add(0, 2, 0), 8, 1.2, .6, 1.2, 0);
            if (shaking > 0 && shaking < 1 && tick % 2 == 0) world.spawnParticle(Particle.LAVA, at.clone().add(0, 1.5, 0), 10, .6, .6, .6, .05);
            if (tick == (int) (length * .72)) world.playSound(home, Sound.ENTITY_WOLF_SHAKE, 2F, .5F);
        } else boss.teleport(home.clone().subtract(0, 4, 0));
    }

    // ---------------------------------------------------------------------------------------------
    // The Deep Foreman: poured from the hearth, then hammered into a man.
    // ---------------------------------------------------------------------------------------------

    private static final double POURED = .26, RISEN = .4;
    private static final double[] BLOWS = {.5, .63, .76};
    private static final Material[] COOLING = {Material.SHROOMLIGHT, Material.ORANGE_CONCRETE, Material.RED_TERRACOTTA, Material.BLACKSTONE};
    /** The figure as shares of its height: across, up, ahead, width, tall, deep. */
    private static final double[][] MAN = {
        {-.17, 0, -.1, .15, .42, .2}, {.02, 0, -.1, .15, .42, .2}, {-.2, .42, -.14, .4, .36, .28}, {-.34, .46, -.08, .12, .34, .16}, {.22, .46, -.08, .12, .34, .16},
        {-.11, .78, -.11, .22, .2, .22}};

    void forged(List<Display> set, LivingEntity boss, Location home, Location hearth, int tick, int length) {
        double done = tick / (double) length, height = boss.getHeight();
        Location floor = home.clone().add(0, .06, 0);
        if (set.isEmpty()) {
            Location start = hearth.clone().add(0, .08, 0);
            double reach = home.distance(hearth);
            BlockDisplay stream = box(start, Material.SHROOMLIGHT, -.6F, 0, 0, 1.2F, .1F, .1F, 15);
            set.add(stream);
            named.put("stream", stream);
            named.put("pool", box(floor, Material.ORANGE_CONCRETE, 0, 0, 0, .1F, .08F, .1F, 15));
            set.add(named.get("pool"));
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (stream.isValid()) move(stream, new Transformation(new Vector3f(-.6F, 0, 0), new Quaternionf(), new Vector3f(1.2F, .1F, (float) reach), new Quaternionf()), (int) (length * POURED));
            }, 2L);
            world.playSound(hearth, Sound.BLOCK_LAVA_POP, 2F, .5F);
            world.playSound(hearth, Sound.ENTITY_BLAZE_AMBIENT, 2F, .5F);
        }
        if (tick % 3 == 0 && done < RISEN) world.spawnParticle(Particle.LAVA, hearth.clone().add(hearth.clone().subtract(home).toVector().multiply(-done / POURED)), 3, .4, .1, .4, 0);
        if (tick == (int) (length * POURED)) {
            Display pool = named.get("pool");
            if (pool != null) move(pool, new Transformation(new Vector3f(-3, 0, -3), new Quaternionf(), new Vector3f(6, .1F, 6), new Quaternionf()), 8);
            world.playSound(home, Sound.BLOCK_LAVA_EXTINGUISH, 1.8F, .6F);
            for (int index = 0; index < MAN.length; index++) {
                double[] part = MAN[index];
                BlockDisplay limb = box(floor, COOLING[0], (float) (part[0] * height), 0, (float) (part[2] * height), (float) (part[3] * height), .02F, (float) (part[5] * height), 15);
                set.add(limb);
                named.put("man" + index, limb);
            }
            // Two great trip hammers swing in from either side.
            for (int side : new int[]{-1, 1}) {
                Location pivot = home.clone().add(side * 5.5, 9, 0);
                BlockDisplay arm = box(pivot, Material.DARK_OAK_LOG, -.3F, -7, -.3F, .6F, 7, .6F, 11);
                BlockDisplay head = box(pivot, Material.POLISHED_BLACKSTONE, -1.1F, -8.6F, -1.1F, 2.2F, 1.8F, 2.2F, 11);
                set.add(arm);
                set.add(head);
                named.put("arm" + side, arm);
                named.put("head" + side, head);
            }
        }
        if (done >= POURED && done < RISEN) {
            double up = eased((done - POURED) / (RISEN - POURED));
            for (int index = 0; index < MAN.length; index++) {
                Display limb = named.get("man" + index);
                if (limb == null) continue;
                double[] part = MAN[index];
                move(limb, new Transformation(new Vector3f((float) (part[0] * height), (float) (part[1] * height * up), (float) (part[2] * height)), new Quaternionf(), new Vector3f((float) (part[3] * height), (float) (part[4] * height * up), (float) (part[5] * height)), new Quaternionf()), 1);
            }
            world.spawnParticle(Particle.LAVA, floor, 2, 1.5, .1, 1.5, 0);
        }
        for (int blow = 0; blow < BLOWS.length; blow++) {
            int strike = (int) (length * BLOWS[blow]), side = blow % 2 == 0 ? -1 : 1;
            for (int swinging : new int[]{-1, 1}) {
                float back = swinging * 1.1F, in = swinging * -.62F;
                if (tick == strike - 8) { swing(swinging, back, 6); }
                if (swinging == side && tick == strike - 2) swing(swinging, in, 2);
                if (swinging == side && tick == strike + 3) swing(swinging, back * .6F, 6);
            }
            if (tick != strike) continue;
            Location struck = home.clone().add(0, height * .7, 0);
            world.spawnParticle(Particle.EXPLOSION, struck, 2, .3, .3, .3, 0);
            world.spawnParticle(Particle.LAVA, struck, 50, .5, .6, .5, .1);
            world.spawnParticle(Particle.CRIT, struck, 40, .6, .6, .6, .6);
            world.playSound(home, Sound.BLOCK_ANVIL_USE, 2F, .5F + blow * .05F);
            world.playSound(home, Sound.BLOCK_ANVIL_LAND, 1.4F, .5F);
            Material cooled = COOLING[blow + 1];
            for (int index = 0; index < MAN.length; index++) if (named.get("man" + index) instanceof BlockDisplay limb) {
                limb.setBlock(cooled.createBlockData());
                int light = 15 - blow * 3;
                limb.setBrightness(new Display.Brightness(light, light));
            }
        }
        if (tick == (int) (length * .92)) {
            for (int index = 0; index < MAN.length; index++) {
                Display limb = named.remove("man" + index);
                if (limb != null) limb.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(), new Quaternionf()));
            }
            boss.setVisibleByDefault(true);
            boss.teleport(home);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, home.clone().add(0, 1, 0), 1);
            world.playSound(home, Sound.ENTITY_RAVAGER_ROAR, 2F, .5F);
        }
    }

    private void swing(int side, float angle, int ticks) {
        for (String part : new String[]{"arm", "head"}) {
            Display display = named.get(part + side);
            if (display == null) continue;
            Transformation now = display.getTransformation();
            move(display, new Transformation(now.getTranslation(), new Quaternionf().rotateZ(angle), now.getScale(), new Quaternionf()), ticks);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The Molten Colossus: the slag boils, the ring bursts, it stands up out of the sea, and it jumps.
    // ---------------------------------------------------------------------------------------------

    private static final double BOIL = .22, RISES = .58, CROUCH = .63, LANDS = .78, ROARS = .86, DEEP = 15;

    void colossus(List<Display> set, LivingEntity boss, MoltenBody body, Location home, Location spot, int tick, int length) {
        double done = tick / (double) length;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (tick == 0) {
            boss.setVisibleByDefault(true);
            body.eyes(false);
            for (int index = 0; index < 18; index++) {
                double angle = index * Math.TAU / 18, radius = 5.4;
                float size = (float) random.nextDouble(.9, 1.7);
                set.add(box(spot, index % 4 == 0 ? Material.CRYING_OBSIDIAN : Material.BASALT, (float) (Math.cos(angle) * radius) - size / 2, .6F, (float) (Math.sin(angle) * radius) - size / 2, size, size, size, 12));
            }
            named.put("first", set.getFirst());
            world.playSound(spot, Sound.AMBIENT_NETHER_WASTES_MOOD, 2F, .5F);
        }
        Location surface = spot.clone().add(0, 1, 0);
        // The slag comes to the boil, and the ring begins to lift.
        if (done < BOIL) {
            double heat = done / BOIL;
            world.spawnParticle(Particle.LAVA, surface, 8 + (int) (heat * 30), 3, .1, 3, 0);
            world.spawnParticle(Particle.FLAME, surface, (int) (heat * 20), 3, .1, 3, .04);
            if (tick % 6 == 0) world.playSound(spot, Sound.BLOCK_LAVA_POP, 2F, .4F + (float) heat * .4F);
            if (tick % 10 == 0) world.playSound(spot, Sound.ENTITY_WARDEN_HEARTBEAT, 2F, .5F);
            if (tick % 3 == 0) for (Display rock : set) {
                Transformation now = rock.getTransformation();
                move(rock, new Transformation(new Vector3f(now.getTranslation()).add(0, (float) (random.nextDouble(-.04, .12) * heat), 0), now.getLeftRotation(), now.getScale(), new Quaternionf()), 3);
            }
        }
        if (tick == (int) (length * BOIL)) {
            for (Display rock : new ArrayList<>(set)) {
                Vector3f out = new Vector3f(rock.getTransformation().getTranslation());
                out.y = 0;
                out.normalize();
                fling(rock, out.x * 18, random.nextDouble(6, 16), out.z * 18, 18);
            }
            world.spawnParticle(Particle.EXPLOSION_EMITTER, surface, 3, 3, 1, 3, 0);
            world.spawnParticle(Particle.LAVA, surface.clone().add(0, 3, 0), 200, 4, 4, 4, .1);
            world.playSound(spot, Sound.ENTITY_GENERIC_EXPLODE, 2F, .5F);
            world.playSound(spot, Sound.ENTITY_WITHER_SPAWN, 1.6F, .5F);
            spout(set, spot, 22, 40);
        }
        // Up out of the sea, slowly, with its arms lifting the slag away.
        if (done < RISES) {
            double up = eased(clamp((done - BOIL) / (RISES - BOIL)));
            Location at = spot.clone().subtract(0, DEEP * (1 - up), 0);
            at.setYaw(spot.getYaw());
            boss.teleport(at);
            body.stance((float) (Math.sin(up * Math.PI) * 2.2F), 0);
            if (done > BOIL) {
                world.spawnParticle(Particle.FALLING_LAVA, at.clone().add(0, 8, 0), 30, 3, 3, 3, 0);
                world.spawnParticle(Particle.LAVA, surface, 14, 5, .1, 5, 0);
                if (tick % 9 == 0) world.playSound(spot, Sound.BLOCK_LAVA_AMBIENT, 2F, .5F);
                if (tick % 16 == 0) world.playSound(spot, Sound.ENTITY_IRON_GOLEM_STEP, 2F, .5F);
            }
        }
        if (tick == (int) (length * RISES)) {
            body.eyes(true);
            world.spawnParticle(Particle.FLAME, spot.clone().add(0, 10, 2), 60, 1, .5, 1, .05);
            world.playSound(spot, Sound.ENTITY_RAVAGER_ROAR, 2F, .5F);
            world.playSound(spot, Sound.ENTITY_WARDEN_ROAR, 2F, .6F);
        }
        // It crouches, and turns to face the Foundry.
        if (done >= RISES && done < CROUCH) {
            double crouch = eased((done - RISES) / (CROUCH - RISES));
            Location at = spot.clone();
            at.setYaw((float) (spot.getYaw() + (facing(spot, home) - spot.getYaw()) * crouch));
            boss.teleport(at);
            body.stance(-.9F * (float) crouch, (float) crouch * 2.2F);
        }
        // And it jumps.
        if (done >= CROUCH && done < LANDS) {
            double flown = (done - CROUCH) / (LANDS - CROUCH);
            Location at = spot.clone().add(home.toVector().subtract(spot.toVector()).multiply(flown)).add(0, Math.sin(flown * Math.PI) * 15, 0);
            at.setYaw(facing(spot, home));
            boss.teleport(at);
            body.stance(1.7F, 0);
            world.spawnParticle(Particle.LAVA, at.clone().add(0, 4, 0), 12, 2, 3, 2, 0);
            world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0, 3, 0), 6, 2, 2, 2, .02);
            if (tick == (int) (length * CROUCH)) world.playSound(at, Sound.ENTITY_WITHER_SHOOT, 2F, .5F);
        }
        if (tick == (int) (length * LANDS)) {
            boss.teleport(home);
            body.stance(-1F, 1.6F);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, home, 4, 3, .5, 3, 0);
            world.spawnParticle(Particle.LAVA, home.clone().add(0, 2, 0), 220, 7, 2, 7, .1);
            world.playSound(home, Sound.ENTITY_GENERIC_EXPLODE, 2F, .4F);
            world.playSound(home, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6F, .5F);
            world.playSound(home, Sound.BLOCK_ANVIL_LAND, 2F, .5F);
            shockwave(set, home.clone().subtract(0, .02, 0), 19, 14);
            debris(set, home.clone().add(0, .5, 0), new Material[]{Material.BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS, Material.MAGMA_BLOCK}, 34, 13, 14);
        }
        if (tick == (int) (length * ROARS)) {
            body.stance(2.5F, 0);
            world.playSound(home, Sound.ENTITY_RAVAGER_ROAR, 2F, .4F);
            world.playSound(home, Sound.ENTITY_ENDER_DRAGON_GROWL, 2F, .5F);
            world.spawnParticle(Particle.FLAME, home.clone().add(0, 10, 0), 160, 2.5, 2, 2.5, .25);
        }
        if (tick == length - 12) body.stance(0, 0);
    }

    private static float facing(Location from, Location to) {
        return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
    }

    /** A column of fire that shoots up from {@code at} and falls away again. */
    private void spout(List<Display> set, Location at, float high, int holds) {
        BlockDisplay column = box(at, Material.ORANGE_CONCRETE, -3, .6F, -3, 6, .2F, 6, 15);
        BlockDisplay heart = box(at, Material.SHROOMLIGHT, -2, .6F, -2, 4, .2F, 4, 15);
        set.add(column);
        set.add(heart);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (column.isValid()) move(column, new Transformation(new Vector3f(-3, .6F, -3), new Quaternionf(), new Vector3f(6, high, 6), new Quaternionf()), 6);
            if (heart.isValid()) move(heart, new Transformation(new Vector3f(-2, .6F, -2), new Quaternionf(), new Vector3f(4, high + 2, 4), new Quaternionf()), 6);
        }, 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (column.isValid()) move(column, new Transformation(new Vector3f(-.3F, .6F, -.3F), new Quaternionf(), new Vector3f(.6F, .1F, .6F), new Quaternionf()), 22);
            if (heart.isValid()) move(heart, new Transformation(new Vector3f(-.3F, .6F, -.3F), new Quaternionf(), new Vector3f(.6F, .1F, .6F), new Quaternionf()), 22);
        }, holds);
    }

    void reset() { named.clear(); }
}

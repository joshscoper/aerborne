package net.nerdsmp.paper.expedition;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A figure far bigger than any mob's model: the boss itself is hidden and this body is hung round it, swinging its limbs
 * as it walks. {@code colossus} is black rock and running fire; {@code unwritten} is a hooded shape that doesn't touch the
 * ground, with a stone of each seal turning round it. Sizes are in blocks, and each part hangs from a joint of the frame.
 */
final class MoltenBody {
    private static final int FRAME_TICKS = 3;
    private static final float SWING = .55F;

    /** Which joint a part hangs from. */
    private enum Joint {
        TRUNK(0, 0), LEFT_ARM(3.7F, 7.4F), RIGHT_ARM(-3.7F, 7.4F), LEFT_LEG(1.5F, 3.6F), RIGHT_LEG(-1.5F, 3.6F), ORBIT(0, 0);
        final float x, y;

        Joint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /** A box measured from its joint: {@code x, y, z} is its near-bottom corner. On an orbit they're its start angle, height and reach. */
    private record Part(Joint joint, Material block, float x, float y, float z, float wide, float high, float deep, boolean lit) { }

    private static final List<Part> PARTS = List.of(
        // Legs, with the fire showing at the knee.
        new Part(Joint.LEFT_LEG, Material.BLACKSTONE, -1F, -1.7F, -1F, 2F, 1.7F, 2F, false),
        new Part(Joint.LEFT_LEG, Material.MAGMA_BLOCK, -.8F, -2.2F, -.8F, 1.6F, .6F, 1.6F, true),
        new Part(Joint.LEFT_LEG, Material.BASALT, -1.1F, -3.6F, -1.3F, 2.2F, 1.5F, 2.6F, false),
        new Part(Joint.RIGHT_LEG, Material.BLACKSTONE, -1F, -1.7F, -1F, 2F, 1.7F, 2F, false),
        new Part(Joint.RIGHT_LEG, Material.MAGMA_BLOCK, -.8F, -2.2F, -.8F, 1.6F, .6F, 1.6F, true),
        new Part(Joint.RIGHT_LEG, Material.BASALT, -1.1F, -3.6F, -1.3F, 2.2F, 1.5F, 2.6F, false),
        // The trunk: broad across the shoulders, narrow at the waist, a furnace showing through the ribs.
        new Part(Joint.TRUNK, Material.BLACKSTONE, -2F, 3.3F, -1.4F, 4F, 1.4F, 2.8F, false),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -1.7F, 4.6F, -1.2F, 3.4F, .7F, 2.4F, true),
        new Part(Joint.TRUNK, Material.BASALT, -2.7F, 5.2F, -1.8F, 5.4F, 3.3F, 3.6F, false),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, -1.1F, 5.9F, 1.65F, 2.2F, 1.7F, .3F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -1.6F, 5.5F, 1.75F, 3.2F, .3F, .2F, true),
        new Part(Joint.TRUNK, Material.BLACKSTONE, -3.4F, 8.1F, -1.5F, 6.8F, 1.1F, 3F, false),
        // A low head set forward between the shoulders, with a crown of cooling slag.
        new Part(Joint.TRUNK, Material.BLACKSTONE, -1.2F, 9F, -.6F, 2.4F, 2F, 2.5F, false),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, -.85F, 9.9F, 1.88F, .6F, .25F, .1F, true),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, .25F, 9.9F, 1.88F, .6F, .25F, .1F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -1.4F, 10.8F, -.4F, .7F, 1.1F, .7F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, .7F, 10.8F, -.4F, .7F, 1.4F, .7F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -.3F, 10.9F, .6F, .6F, .9F, .6F, true),
        // Huge arms ending in fists of rock.
        new Part(Joint.LEFT_ARM, Material.BASALT, -1.5F, -1.2F, -1.6F, 3F, 2.6F, 3.2F, false),
        new Part(Joint.LEFT_ARM, Material.MAGMA_BLOCK, -.9F, -3.6F, -.9F, 1.8F, 2.5F, 1.8F, true),
        new Part(Joint.LEFT_ARM, Material.BLACKSTONE, -1.1F, -5F, -1.1F, 2.2F, 1.6F, 2.2F, false),
        new Part(Joint.LEFT_ARM, Material.BASALT, -1.6F, -7F, -1.6F, 3.2F, 2.2F, 3.2F, false),
        new Part(Joint.RIGHT_ARM, Material.BASALT, -1.5F, -1.2F, -1.6F, 3F, 2.6F, 3.2F, false),
        new Part(Joint.RIGHT_ARM, Material.MAGMA_BLOCK, -.9F, -3.6F, -.9F, 1.8F, 2.5F, 1.8F, true),
        new Part(Joint.RIGHT_ARM, Material.BLACKSTONE, -1.1F, -5F, -1.1F, 2.2F, 1.6F, 2.2F, false),
        new Part(Joint.RIGHT_ARM, Material.BASALT, -1.6F, -7F, -1.6F, 3.2F, 2.2F, 3.2F, false));

    private static final List<Part> UNWRITTEN = List.of(
        // The robe, widening to a hem that stops short of the floor and burns.
        new Part(Joint.TRUNK, Material.CRYING_OBSIDIAN, -2.3F, 1.2F, -2F, 4.6F, .5F, 4F, true),
        new Part(Joint.TRUNK, Material.BLACK_CONCRETE, -2.1F, 1.6F, -1.8F, 4.2F, 1.9F, 3.6F, false),
        new Part(Joint.TRUNK, Material.POLISHED_BLACKSTONE, -1.7F, 3.4F, -1.45F, 3.4F, 1.9F, 2.9F, false),
        new Part(Joint.TRUNK, Material.PURPLE_CONCRETE, -.5F, 1.7F, 1.75F, 1F, 3.6F, .2F, true),
        new Part(Joint.TRUNK, Material.BLACK_CONCRETE, -1.4F, 5.2F, -1.2F, 2.8F, 1.7F, 2.4F, false),
        // A gold collar across narrow shoulders: the last thing about it that looks like the Hall.
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, -2.4F, 6.7F, -1.3F, 4.8F, .35F, 2.6F, true),
        new Part(Joint.TRUNK, Material.POLISHED_BLACKSTONE, -2.2F, 7F, -1.2F, 4.4F, .8F, 2.4F, false),
        // A deep hood, nothing in it but the eyes, and horns of black crystal.
        new Part(Joint.TRUNK, Material.BLACK_CONCRETE, -1.15F, 7.7F, -1.05F, 2.3F, 2.2F, 2.3F, false),
        new Part(Joint.TRUNK, Material.BLACK_CONCRETE, -.8F, 9.8F, -.8F, 1.6F, .7F, 1.7F, false),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, -.7F, 8.8F, 1.28F, .45F, .25F, .1F, true),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, .25F, 8.8F, 1.28F, .45F, .25F, .1F, true),
        new Part(Joint.TRUNK, Material.AMETHYST_BLOCK, -1.5F, 9.7F, -.3F, .5F, 1.8F, .5F, true),
        new Part(Joint.TRUNK, Material.AMETHYST_BLOCK, 1F, 9.7F, -.3F, .5F, 1.8F, .5F, true),
        new Part(Joint.TRUNK, Material.AMETHYST_BLOCK, -.25F, 10.3F, -.2F, .5F, 1.5F, .5F, true),
        // Long sleeves, and a dagger of the same crystal in the right hand.
        new Part(Joint.LEFT_ARM, Material.BLACK_CONCRETE, -1.9F, -3.4F, -.55F, 1.1F, 3.6F, 1.1F, false),
        new Part(Joint.LEFT_ARM, Material.PEARLESCENT_FROGLIGHT, -1.75F, -3.9F, -.4F, .8F, .6F, .8F, true),
        new Part(Joint.RIGHT_ARM, Material.BLACK_CONCRETE, .8F, -3.4F, -.55F, 1.1F, 3.6F, 1.1F, false),
        new Part(Joint.RIGHT_ARM, Material.PEARLESCENT_FROGLIGHT, .95F, -3.9F, -.4F, .8F, .6F, .8F, true),
        new Part(Joint.RIGHT_ARM, Material.AMETHYST_BLOCK, 1.2F, -6.4F, -.15F, .3F, 2.6F, .3F, true),
        new Part(Joint.RIGHT_ARM, Material.GOLD_BLOCK, .95F, -4.2F, -.3F, .8F, .2F, .6F, true),
        // A stone of each seal, and one of the dark, turning.
        new Part(Joint.ORBIT, Material.MAGMA_BLOCK, 0F, 7F, 4.6F, 1.1F, 1.1F, 1.1F, true),
        new Part(Joint.ORBIT, Material.BLUE_ICE, 1.57F, 6.2F, 4.6F, 1.1F, 1.1F, 1.1F, true),
        new Part(Joint.ORBIT, Material.MOSS_BLOCK, 3.14F, 7F, 4.6F, 1.1F, 1.1F, 1.1F, true),
        new Part(Joint.ORBIT, Material.CRYING_OBSIDIAN, 4.71F, 6.2F, 4.6F, 1.1F, 1.1F, 1.1F, true));

    private final LivingEntity boss;
    private final List<Part> parts;
    private final Material glow;
    private final Particle trail;
    private final float hover;
    private final List<BlockDisplay> displays = new ArrayList<>();
    private Location last;
    private double stride;
    private int tick;
    /** Held poses for an entrance: both arms raised or thrown back, and how far it's crouched. */
    private float arms, crouch;

    private MoltenBody(LivingEntity boss, boolean unwritten) {
        this.boss = boss;
        parts = unwritten ? UNWRITTEN : PARTS;
        glow = unwritten ? Material.PEARLESCENT_FROGLIGHT : Material.SHROOMLIGHT;
        trail = unwritten ? Particle.REVERSE_PORTAL : Particle.LAVA;
        hover = unwritten ? .35F : .08F;
        last = boss.getLocation();
    }

    /** Hides the boss and hangs the body on it. It goes when the boss goes. */
    static MoltenBody dress(Plugin plugin, LivingEntity boss, String look) {
        boss.setInvisible(true);
        boss.setCustomNameVisible(false);
        boss.setSilent(false);
        MoltenBody body = new MoltenBody(boss, "unwritten".equals(look));
        for (Part part : body.parts) body.displays.add(boss.getWorld().spawn(boss.getLocation(), BlockDisplay.class, display -> {
            display.setBlock((part.block() == Material.SHROOMLIGHT ? body.glow : part.block()).createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(2);
            display.setInterpolationDuration(FRAME_TICKS);
            display.setViewRange(3F);
            if (part.lit()) display.setBrightness(new Display.Brightness(15, 15));
        }));
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!body.step()) task.cancel();
        }, 1L, 1L);
        return body;
    }

    /** Holds a pose until told otherwise: {@code arms} in radians, up from hanging, and {@code crouch} in blocks. */
    void stance(float arms, float crouch) {
        this.arms = arms;
        this.crouch = crouch;
    }

    /** Eyes dark until it wakes. */
    void eyes(boolean lit) {
        for (int index = 0; index < parts.size(); index++) {
            Part part = parts.get(index);
            if (part.block() == Material.SHROOMLIGHT && part.high() < .5F) displays.get(index).setBlock((lit ? glow : Material.BLACKSTONE).createBlockData());
        }
    }

    /** False once the boss is gone. */
    private boolean step() {
        if (!boss.isValid()) {
            Location at = last.clone().add(0, 5, 0);
            at.getWorld().spawnParticle(trail, at, 120, 2.5, 4, 2.5, 0);
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 60, 2.5, 4, 2.5, .02);
            displays.forEach(BlockDisplay::remove);
            return false;
        }
        Location at = boss.getLocation();
        at.setPitch(0);
        // Held for an entrance, it faces where it was put.
        if (boss instanceof org.bukkit.entity.Mob mob && mob.hasAI()) at.setYaw(boss.getBodyYaw());
        if (at.getWorld() == last.getWorld()) stride += Math.min(.5, at.distance(last)) * .9;
        last = at;
        for (BlockDisplay display : displays) display.teleport(at);
        if (tick++ % FRAME_TICKS == 0) pose();
        if (tick % 12 == 0) at.getWorld().spawnParticle(trail, at.clone().add(0, 6, 0), 3, 2, 3, 2, 0);
        return true;
    }

    private void pose() {
        float walk = (float) Math.sin(stride) * SWING, breath = (float) Math.sin(tick * .05) * hover;
        for (int index = 0; index < parts.size(); index++) {
            Part part = parts.get(index);
            if (part.joint() == Joint.ORBIT) {
                float turn = tick * .05F + part.x(), bob = (float) Math.sin(tick * .07 + part.x()) * .5F;
                displays.get(index).setInterpolationDelay(0);
                displays.get(index).setTransformationMatrix(new Matrix4f().rotateY(turn).translate(part.z(), part.y() + bob, 0)
                    .rotateXYZ(tick * .09F, tick * .06F, 0).scale(part.wide(), part.high(), part.deep()).translate(-.5F, -.5F, -.5F));
                continue;
            }
            float swing = switch (part.joint()) {
                case LEFT_ARM -> walk - arms;
                case RIGHT_ARM -> -walk - arms;
                case RIGHT_LEG -> walk;
                case LEFT_LEG -> -walk;
                case TRUNK, ORBIT -> 0;
            };
            boolean upper = part.joint() == Joint.TRUNK || part.joint() == Joint.LEFT_ARM || part.joint() == Joint.RIGHT_ARM;
            float lift = upper ? breath - crouch : 0;
            Matrix4f shape = new Matrix4f().translate(part.joint().x, part.joint().y + lift, 0).rotateX(swing)
                .translate(part.x(), part.y(), part.z()).scale(part.wide(), part.high(), part.deep());
            BlockDisplay display = displays.get(index);
            display.setInterpolationDelay(0);
            display.setTransformationMatrix(shape);
        }
    }
}

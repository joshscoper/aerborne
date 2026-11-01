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
 * A thing of black rock and running fire, far bigger than any mob's model: the boss itself is hidden and this body is
 * hung round it, swinging its limbs as it walks. Sizes are in blocks, and each part hangs from a joint of the frame.
 */
final class MoltenBody {
    private static final int FRAME_TICKS = 3;
    private static final float SWING = .55F;

    /** Which joint a part hangs from. */
    private enum Joint {
        TRUNK(0, 0), LEFT_ARM(3.7F, 7.4F), RIGHT_ARM(-3.7F, 7.4F), LEFT_LEG(1.5F, 3.6F), RIGHT_LEG(-1.5F, 3.6F);
        final float x, y;

        Joint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }

    /** A box measured from its joint: {@code x, y, z} is its near-bottom corner. */
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

    private final LivingEntity boss;
    private final List<BlockDisplay> displays = new ArrayList<>();
    private Location last;
    private double stride;
    private int tick;

    private MoltenBody(LivingEntity boss) {
        this.boss = boss;
        last = boss.getLocation();
    }

    /** Hides the boss and hangs the body on it. It goes when the boss goes. */
    static void dress(Plugin plugin, LivingEntity boss) {
        boss.setInvisible(true);
        boss.setCustomNameVisible(false);
        boss.setSilent(false);
        MoltenBody body = new MoltenBody(boss);
        for (Part part : PARTS) body.displays.add(boss.getWorld().spawn(boss.getLocation(), BlockDisplay.class, display -> {
            display.setBlock(part.block().createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(2);
            display.setInterpolationDuration(FRAME_TICKS);
            display.setViewRange(3F);
            if (part.lit()) display.setBrightness(new Display.Brightness(15, 15));
        }));
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!body.step()) task.cancel();
        }, 1L, 1L);
    }

    /** False once the boss is gone. */
    private boolean step() {
        if (!boss.isValid()) {
            Location at = last.clone().add(0, 5, 0);
            at.getWorld().spawnParticle(Particle.LAVA, at, 120, 2.5, 4, 2.5, 0);
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 60, 2.5, 4, 2.5, .02);
            displays.forEach(BlockDisplay::remove);
            return false;
        }
        Location at = boss.getLocation();
        at.setPitch(0);
        at.setYaw(boss.getBodyYaw());
        if (at.getWorld() == last.getWorld()) stride += Math.min(.5, at.distance(last)) * .9;
        last = at;
        for (BlockDisplay display : displays) display.teleport(at);
        if (tick++ % FRAME_TICKS == 0) pose();
        if (tick % 12 == 0) at.getWorld().spawnParticle(Particle.LAVA, at.clone().add(0, 6, 0), 3, 2, 3, 2, 0);
        return true;
    }

    private void pose() {
        float walk = (float) Math.sin(stride) * SWING, breath = (float) Math.sin(tick * .05) * .08F;
        for (int index = 0; index < PARTS.size(); index++) {
            Part part = PARTS.get(index);
            float swing = switch (part.joint()) {
                case LEFT_ARM, RIGHT_LEG -> walk;
                case RIGHT_ARM, LEFT_LEG -> -walk;
                case TRUNK -> 0;
            };
            float lift = part.joint() == Joint.TRUNK || part.joint() == Joint.LEFT_ARM || part.joint() == Joint.RIGHT_ARM ? breath : 0;
            Matrix4f shape = new Matrix4f().translate(part.joint().x, part.joint().y + lift, 0).rotateX(swing)
                .translate(part.x(), part.y(), part.z()).scale(part.wide(), part.high(), part.deep());
            BlockDisplay display = displays.get(index);
            display.setInterpolationDelay(0);
            display.setTransformationMatrix(shape);
        }
    }
}

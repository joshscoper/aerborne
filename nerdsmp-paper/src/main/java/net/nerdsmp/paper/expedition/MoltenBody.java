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
 * ground, with a stone of each seal turning round it; {@code old_bess} is a pump engine on piston legs; {@code tidewarden}
 * is hooded water with a bronze mantle and a trident; {@code cindermaw} is a drake on the wing; {@code pyrrhax} is a god,
 * from the waist up. Sizes are in blocks, and each part hangs from a joint of the frame.
 */
final class MoltenBody {
    private static final int FRAME_TICKS = 3;
    private static final float SWING = .55F;

    /** Which joint a part hangs from. */
    private enum Joint {
        WING_L(1.2F, 4.6F), WING_R(-1.2F, 4.6F),
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

    private static final List<Part> PYRRHAX = List.of(
        // A trunk of black rock split by fire, from the waist; he has never needed legs to be seen.
        new Part(Joint.TRUNK, Material.BASALT, -3.4F, 1F, -2.6F, 6.8F, 3F, 5.2F, false),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -3F, 3.8F, -2.2F, 6F, .6F, 4.4F, true),
        new Part(Joint.TRUNK, Material.BLACKSTONE, -3.8F, 4.3F, -2.7F, 7.6F, 4.3F, 5.4F, false),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, -1.8F, 5F, 2.65F, 3.6F, 2.6F, .3F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -.5F, 4.4F, 2.7F, 1F, 3.6F, .35F, true),
        new Part(Joint.TRUNK, Material.BASALT, -4.8F, 8.2F, -2.2F, 9.6F, 1.5F, 4.4F, false),
        // Great shoulders, and gold set in them that the Wardens never got out.
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, -4.9F, 9.1F, -1F, 9.8F, .3F, 2F, true),
        new Part(Joint.TRUNK, Material.POLISHED_BLACKSTONE, -6F, 8.4F, -1.9F, 2.4F, 2.2F, 3.8F, false),
        new Part(Joint.TRUNK, Material.POLISHED_BLACKSTONE, 3.6F, 8.4F, -1.9F, 2.4F, 2.2F, 3.8F, false),
        // A low head, heavy-browed, with fire in the mouth and eyes.
        new Part(Joint.TRUNK, Material.BLACKSTONE, -1.7F, 9.6F, -1.1F, 3.4F, 3F, 3.4F, false),
        new Part(Joint.TRUNK, Material.POLISHED_BLACKSTONE, -1.9F, 11F, 1.8F, 3.8F, .5F, .8F, false),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, -1.2F, 10.7F, 2.32F, .9F, .3F, .1F, true),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, .3F, 10.7F, 2.32F, .9F, .3F, .1F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -1F, 9.8F, 2.32F, 2F, .35F, .1F, true),
        // The crown he was bound in: spikes of slag and old gold.
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, -2F, 12.5F, -1.3F, 4F, .5F, 3.8F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -1.8F, 13F, -.8F, .7F, 2F, .7F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, 1.1F, 13F, -.8F, .7F, 2F, .7F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -.4F, 13F, .9F, .8F, 2.8F, .8F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -.4F, 13F, -1.2F, .8F, 2.3F, .8F, true),
        // Arms like towers, broken chains still on the wrists.
        new Part(Joint.LEFT_ARM, Material.BASALT, -1F, -1.4F, -1.8F, 3.4F, 3F, 3.6F, false),
        new Part(Joint.LEFT_ARM, Material.MAGMA_BLOCK, -.3F, -4.4F, -1.1F, 2.2F, 3.2F, 2.2F, true),
        new Part(Joint.LEFT_ARM, Material.BLACKSTONE, -.5F, -6.4F, -1.3F, 2.6F, 2.2F, 2.6F, false),
        new Part(Joint.LEFT_ARM, Material.GOLD_BLOCK, -.6F, -6F, -1.4F, 2.8F, .4F, 2.8F, true),
        new Part(Joint.LEFT_ARM, Material.BASALT, -.9F, -8.6F, -1.7F, 3.4F, 2.4F, 3.4F, false),
        new Part(Joint.RIGHT_ARM, Material.BASALT, -2.4F, -1.4F, -1.8F, 3.4F, 3F, 3.6F, false),
        new Part(Joint.RIGHT_ARM, Material.MAGMA_BLOCK, -1.9F, -4.4F, -1.1F, 2.2F, 3.2F, 2.2F, true),
        new Part(Joint.RIGHT_ARM, Material.BLACKSTONE, -2.1F, -6.4F, -1.3F, 2.6F, 2.2F, 2.6F, false),
        new Part(Joint.RIGHT_ARM, Material.GOLD_BLOCK, -2.2F, -6F, -1.4F, 2.8F, .4F, 2.8F, true),
        new Part(Joint.RIGHT_ARM, Material.BASALT, -2.5F, -8.6F, -1.7F, 3.4F, 2.4F, 3.4F, false),
        // The hammer: a haft in the right fist, and a head the size of a house.
        new Part(Joint.RIGHT_ARM, Material.POLISHED_BLACKSTONE, -1.1F, -7.6F, .4F, .7F, .7F, 7F, false),
        new Part(Joint.RIGHT_ARM, Material.GILDED_BLACKSTONE, -2.6F, -9F, 6.4F, 3.7F, 3.4F, 3.2F, false),
        new Part(Joint.RIGHT_ARM, Material.MAGMA_BLOCK, -2.7F, -8F, 7.4F, 3.9F, 1.2F, 1.2F, true));

    private static final List<Part> CINDERMAW = List.of(
        // A long body, ribbed in black, with fire in the belly.
        new Part(Joint.TRUNK, Material.BLACKSTONE, -1.5F, 2.8F, -2.6F, 3F, 2.6F, 6F, false),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -1.1F, 2.5F, -2F, 2.2F, .5F, 4.8F, true),
        new Part(Joint.TRUNK, Material.BASALT, -.3F, 5.3F, -2.4F, .6F, .5F, 5.6F, false),
        // A neck going up and forward, and a long wedge of a head.
        new Part(Joint.TRUNK, Material.BLACKSTONE, -.9F, 3.8F, 3.2F, 1.8F, 1.8F, 2.4F, false),
        new Part(Joint.TRUNK, Material.BASALT, -.7F, 4.8F, 5.2F, 1.4F, 1.5F, 2.2F, false),
        new Part(Joint.TRUNK, Material.POLISHED_BLACKSTONE, -1F, 5.4F, 7F, 2F, 1.2F, 2.8F, false),
        new Part(Joint.TRUNK, Material.BLACKSTONE, -.7F, 4.9F, 8.2F, 1.4F, .5F, 2.2F, false),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -.6F, 5.3F, 8.6F, 1.2F, .2F, 1.7F, true),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, -.95F, 6.1F, 8.6F, .25F, .25F, .4F, true),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, .7F, 6.1F, 8.6F, .25F, .25F, .4F, true),
        new Part(Joint.TRUNK, Material.GILDED_BLACKSTONE, -.8F, 6.5F, 6.6F, .35F, 1.1F, .35F, false),
        new Part(Joint.TRUNK, Material.GILDED_BLACKSTONE, .45F, 6.5F, 6.6F, .35F, 1.1F, .35F, false),
        // A tail, thinning away behind it.
        new Part(Joint.TRUNK, Material.BLACKSTONE, -1F, 3.2F, -5.4F, 2F, 1.8F, 3F, false),
        new Part(Joint.TRUNK, Material.BASALT, -.7F, 3.3F, -8F, 1.4F, 1.3F, 2.8F, false),
        new Part(Joint.TRUNK, Material.BLACKSTONE, -.4F, 3.4F, -10.6F, .8F, .8F, 2.8F, false),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -.2F, 3.5F, -12.4F, .4F, .4F, 2F, true),
        // Great wings: an arm of bone, and sails of something that burned long ago.
        new Part(Joint.WING_L, Material.BLACKSTONE, 0F, -.3F, -.6F, 10F, .6F, 1.2F, false),
        new Part(Joint.WING_L, Material.RED_NETHER_BRICKS, .5F, -.15F, -4.6F, 9F, .3F, 4F, false),
        new Part(Joint.WING_L, Material.NETHER_BRICKS, 3F, -.15F, -7F, 6F, .3F, 2.4F, false),
        new Part(Joint.WING_R, Material.BLACKSTONE, -10F, -.3F, -.6F, 10F, .6F, 1.2F, false),
        new Part(Joint.WING_R, Material.RED_NETHER_BRICKS, -9.5F, -.15F, -4.6F, 9F, .3F, 4F, false),
        new Part(Joint.WING_R, Material.NETHER_BRICKS, -9F, -.15F, -7F, 6F, .3F, 2.4F, false),
        new Part(Joint.LEFT_LEG, Material.BLACKSTONE, -.5F, -3.4F, -.6F, 1F, 3.4F, 1.4F, false),
        new Part(Joint.RIGHT_LEG, Material.BLACKSTONE, -.5F, -3.4F, -.6F, 1F, 3.4F, 1.4F, false));

    private static final List<Part> OLD_BESS = List.of(
        // Two piston legs on iron feet.
        new Part(Joint.LEFT_LEG, Material.POLISHED_BLACKSTONE, -1.2F, -3.6F, -1.6F, 2.4F, 1F, 3.2F, false),
        new Part(Joint.LEFT_LEG, Material.IRON_BLOCK, -.45F, -2.7F, -.45F, .9F, 2.7F, .9F, false),
        new Part(Joint.LEFT_LEG, Material.WAXED_WEATHERED_COPPER, -1F, -.6F, -1F, 2F, 1.3F, 2F, false),
        new Part(Joint.RIGHT_LEG, Material.POLISHED_BLACKSTONE, -1.2F, -3.6F, -1.6F, 2.4F, 1F, 3.2F, false),
        new Part(Joint.RIGHT_LEG, Material.IRON_BLOCK, -.45F, -2.7F, -.45F, .9F, 2.7F, .9F, false),
        new Part(Joint.RIGHT_LEG, Material.WAXED_WEATHERED_COPPER, -1F, -.6F, -1F, 2F, 1.3F, 2F, false),
        // The boiler for a body, banded, with a firebox door in its chest and a stack on top.
        new Part(Joint.TRUNK, Material.WAXED_OXIDIZED_COPPER, -3F, 3.4F, -2.4F, 6F, 5.4F, 4.8F, false),
        new Part(Joint.TRUNK, Material.POLISHED_BLACKSTONE, -3.1F, 4.5F, -2.5F, 6.2F, .4F, 5F, false),
        new Part(Joint.TRUNK, Material.POLISHED_BLACKSTONE, -3.1F, 7.4F, -2.5F, 6.2F, .4F, 5F, false),
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, -1.5F, 5.1F, 2.35F, 3F, 2.1F, .15F, false),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, -1.1F, 5.4F, 2.45F, 2.2F, .4F, .1F, true),
        new Part(Joint.TRUNK, Material.MAGMA_BLOCK, -1.1F, 6F, 2.45F, 2.2F, .8F, .1F, true),
        new Part(Joint.TRUNK, Material.WAXED_WEATHERED_COPPER, -2.4F, 8.8F, -1.8F, 4.8F, .8F, 3.6F, false),
        new Part(Joint.TRUNK, Material.POLISHED_BLACKSTONE, -.8F, 9.5F, -1.4F, 1.6F, 3.8F, 1.6F, false),
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, -1F, 13.2F, -1.6F, 2F, .3F, 2F, false),
        // A pressure dial for a face, and a great crank arm either side ending in a valve wheel.
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, -.9F, 9F, 1.6F, 1.8F, 1.6F, .2F, false),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, -.5F, 9.5F, 1.82F, 1F, .25F, .1F, true),
        new Part(Joint.LEFT_ARM, Material.IRON_BLOCK, -.5F, -4.6F, -.5F, 1F, 5F, 1F, false),
        new Part(Joint.LEFT_ARM, Material.WAXED_WEATHERED_COPPER, -1.1F, -.8F, -1.1F, 2.2F, 1.8F, 2.2F, false),
        new Part(Joint.LEFT_ARM, Material.POLISHED_BLACKSTONE, -1.7F, -6.4F, -1.7F, 3.4F, 2F, 3.4F, false),
        new Part(Joint.RIGHT_ARM, Material.IRON_BLOCK, -.5F, -4.6F, -.5F, 1F, 5F, 1F, false),
        new Part(Joint.RIGHT_ARM, Material.WAXED_WEATHERED_COPPER, -1.1F, -.8F, -1.1F, 2.2F, 1.8F, 2.2F, false),
        new Part(Joint.RIGHT_ARM, Material.POLISHED_BLACKSTONE, -1.7F, -6.4F, -1.7F, 3.4F, 2F, 3.4F, false));

    private static final List<Part> TIDEWARDEN = List.of(
        // She's the water itself, hooded, with the bronze of her statue on her shoulders and a crown of gold.
        new Part(Joint.TRUNK, Material.LIGHT_BLUE_STAINED_GLASS, -3F, .2F, -2.8F, 6F, 2.4F, 5.6F, true),
        new Part(Joint.TRUNK, Material.BLUE_STAINED_GLASS, -2.6F, 2.4F, -2.3F, 5.2F, 2.6F, 4.6F, true),
        new Part(Joint.TRUNK, Material.LIGHT_BLUE_STAINED_GLASS, -2.1F, 4.9F, -1.8F, 4.2F, 2.4F, 3.6F, true),
        new Part(Joint.TRUNK, Material.WAXED_OXIDIZED_COPPER, -3F, 7.2F, -1.8F, 6F, 1.1F, 3.6F, false),
        new Part(Joint.TRUNK, Material.BLUE_STAINED_GLASS, -1.4F, 8.2F, -1.3F, 2.8F, 2.6F, 2.8F, true),
        new Part(Joint.TRUNK, Material.BLACK_CONCRETE, -.9F, 8.7F, 1.45F, 1.8F, 1.5F, .1F, false),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, -.7F, 9.5F, 1.57F, .5F, .25F, .1F, true),
        new Part(Joint.TRUNK, Material.SHROOMLIGHT, .2F, 9.5F, 1.57F, .5F, .25F, .1F, true),
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, -1.6F, 10.6F, -1.5F, 3.2F, .3F, 3.2F, true),
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, -.2F, 10.8F, 1.2F, .4F, 1.4F, .3F, true),
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, -1.2F, 10.8F, .9F, .35F, 1F, .3F, true),
        new Part(Joint.TRUNK, Material.GOLD_BLOCK, .85F, 10.8F, .9F, .35F, 1F, .3F, true),
        new Part(Joint.LEFT_ARM, Material.LIGHT_BLUE_STAINED_GLASS, -1.5F, -4.6F, -.8F, 1.6F, 5.2F, 1.6F, true),
        new Part(Joint.RIGHT_ARM, Material.LIGHT_BLUE_STAINED_GLASS, .1F, -4.6F, -.8F, 1.6F, 5.2F, 1.6F, true),
        // Her trident, carried upright.
        new Part(Joint.RIGHT_ARM, Material.WAXED_OXIDIZED_COPPER, .7F, -7F, .9F, .35F, 13F, .35F, false),
        new Part(Joint.RIGHT_ARM, Material.GOLD_BLOCK, .1F, 5.8F, .9F, 1.5F, .35F, .35F, true),
        new Part(Joint.RIGHT_ARM, Material.GOLD_BLOCK, .1F, 5.8F, .9F, .3F, 1.4F, .3F, true),
        new Part(Joint.RIGHT_ARM, Material.GOLD_BLOCK, .7F, 5.9F, .9F, .35F, 2F, .35F, true),
        new Part(Joint.RIGHT_ARM, Material.GOLD_BLOCK, 1.3F, 5.8F, .9F, .3F, 1.4F, .3F, true),
        // Globes of water, turning.
        new Part(Joint.ORBIT, Material.LIGHT_BLUE_STAINED_GLASS, 0F, 6F, 5F, 1.3F, 1.3F, 1.3F, true),
        new Part(Joint.ORBIT, Material.LIGHT_BLUE_STAINED_GLASS, 2.09F, 4.5F, 5F, 1F, 1F, 1F, true),
        new Part(Joint.ORBIT, Material.LIGHT_BLUE_STAINED_GLASS, 4.19F, 7F, 5F, 1.1F, 1.1F, 1.1F, true));

    private final LivingEntity boss;
    private final List<Part> parts;
    private final Material glow;
    private final Particle trail;
    private final float hover;
    /** How much bigger than authored, and how far down it stands from the mob that carries it. */
    private float size = 1, drop;
    private boolean winged;
    /** Held arms for a blow, in radians from hanging: either can be up while the other hangs. */
    private float leftArm, rightArm;
    private boolean sunk;
    private float sinking;
    private int fallen = -1;
    private final List<BlockDisplay> displays = new ArrayList<>();
    private Location last;
    private double stride;
    private int tick;
    /** Held poses for an entrance: both arms raised or thrown back, and how far it's crouched. */
    private float arms, crouch;

    private MoltenBody(LivingEntity boss, String look) {
        this.boss = boss;
        switch (look) {
            case "unwritten" -> {
                parts = UNWRITTEN;
                glow = Material.PEARLESCENT_FROGLIGHT;
                trail = Particle.REVERSE_PORTAL;
                hover = .35F;
            }
            case "old_bess" -> {
                parts = OLD_BESS;
                glow = Material.SHROOMLIGHT;
                trail = Particle.CLOUD;
                hover = .08F;
            }
            case "pyrrhax" -> {
                parts = PYRRHAX;
                glow = Material.SHROOMLIGHT;
                trail = Particle.LAVA;
                hover = .1F;
                size = 4;
                drop = 18;
            }
            case "cindermaw" -> {
                parts = CINDERMAW;
                glow = Material.SHROOMLIGHT;
                trail = Particle.FLAME;
                hover = .2F;
                size = 1.7F;
                winged = true;
            }
            case "tidewarden" -> {
                parts = TIDEWARDEN;
                glow = Material.SEA_LANTERN;
                trail = Particle.SPLASH;
                hover = .3F;
            }
            default -> {
                parts = PARTS;
                glow = Material.SHROOMLIGHT;
                trail = Particle.LAVA;
                hover = .08F;
            }
        }
        last = boss.getLocation();
    }

    /** Hides the boss and hangs the body on it. It goes when the boss goes. */
    static MoltenBody dress(Plugin plugin, LivingEntity boss, String look) {
        boss.setInvisible(true);
        boss.setCustomNameVisible(false);
        boss.setSilent(false);
        MoltenBody body = new MoltenBody(boss, look);
        // A god's hit box fills the end of the arena, and it shouldn't shove anyone off it.
        if (body.size > 2) boss.setCollidable(false);
        for (Part part : body.parts) body.displays.add(boss.getWorld().spawn(boss.getLocation(), BlockDisplay.class, display -> {
            display.setBlock((part.block() == Material.SHROOMLIGHT ? body.glow : part.block()).createBlockData());
            display.setPersistent(false);
            display.setTeleportDuration(2);
            display.setInterpolationDuration(FRAME_TICKS);
            display.setViewRange(3F + body.size);
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

    /** Holds one arm up for a blow, or lets it go with {@code 0}. */
    void strike(int side, float arm) {
        if (side >= 0) leftArm = arm;
        if (side <= 0) rightArm = arm;
    }

    /** Sends it under the surface, or brings it back up. */
    void sink(boolean under) { sunk = under; }

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
            // Anything this size goes down slowly.
            if (size > 2) {
                fallen++;
                sinking = fallen * .5F;
                if (fallen == 0) {
                    arms = 2.2F;
                    eyes(false);
                    last.getWorld().playSound(last, org.bukkit.Sound.ENTITY_WITHER_DEATH, 5F, .4F);
                }
                if (fallen % 3 == 0) last.getWorld().spawnParticle(Particle.LAVA, last.clone().add(0, 8, 8), 60, 16, 4, 12, 0);
                if (fallen % 20 == 0) last.getWorld().playSound(last, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 4F, .5F);
                if (tick++ % FRAME_TICKS == 0) pose();
                if (fallen < 140) return true;
            }
            Location at = last.clone().add(0, 5, 0);
            at.getWorld().spawnParticle(trail, at, 120, 2.5, 4, 2.5, 0);
            at.getWorld().spawnParticle(Particle.LARGE_SMOKE, at, 60, 2.5, 4, 2.5, .02);
            displays.forEach(BlockDisplay::remove);
            return false;
        }
        sinking += ((sunk ? 44 : 0) - sinking) * .03F;
        Location at = boss.getLocation();
        at.setPitch(0);
        // Held for an entrance, it faces where it was put.
        if (size <= 2 && boss instanceof org.bukkit.entity.Mob mob && mob.hasAI()) at.setYaw(boss.getBodyYaw());
        if (at.getWorld() == last.getWorld()) stride += Math.min(.5, at.distance(last)) * .9;
        last = at;
        for (BlockDisplay display : displays) display.teleport(at);
        if (tick++ % FRAME_TICKS == 0) pose();
        if (tick % 12 == 0) at.getWorld().spawnParticle(trail, at.clone().add(0, 6, 0), 3, 2, 3, 2, 0);
        return true;
    }

    private void pose() {
        float walk = (float) Math.sin(stride) * SWING, breath = (float) Math.sin(tick * .05) * hover;
        float below = -drop - sinking;
        for (int index = 0; index < parts.size(); index++) {
            Part part = parts.get(index);
            if (part.joint() == Joint.WING_L || part.joint() == Joint.WING_R) {
                float flap = (float) Math.sin(tick * .14) * .7F * (part.joint() == Joint.WING_L ? 1 : -1);
                displays.get(index).setInterpolationDelay(0);
                displays.get(index).setTransformationMatrix(new Matrix4f().scale(size).translate(part.joint().x, part.joint().y + breath, 0).rotateZ(flap)
                    .translate(part.x(), part.y(), part.z()).scale(part.wide(), part.high(), part.deep()));
                continue;
            }
            if (part.joint() == Joint.ORBIT) {
                float turn = tick * .05F + part.x(), bob = (float) Math.sin(tick * .07 + part.x()) * .5F;
                displays.get(index).setInterpolationDelay(0);
                displays.get(index).setTransformationMatrix(new Matrix4f().rotateY(turn).translate(part.z(), part.y() + bob, 0)
                    .rotateXYZ(tick * .09F, tick * .06F, 0).scale(part.wide(), part.high(), part.deep()).translate(-.5F, -.5F, -.5F));
                continue;
            }
            float swing = switch (part.joint()) {
                case LEFT_ARM -> (leftArm != 0 ? 0 : walk) - arms - leftArm;
                case RIGHT_ARM -> (rightArm != 0 ? 0 : -walk) - arms - rightArm;
                case RIGHT_LEG -> walk;
                case LEFT_LEG -> -walk;
                case TRUNK, ORBIT, WING_L, WING_R -> 0;
            };
            boolean upper = part.joint() == Joint.TRUNK || part.joint() == Joint.LEFT_ARM || part.joint() == Joint.RIGHT_ARM;
            float lift = upper ? breath - crouch : 0;
            Matrix4f shape = new Matrix4f().translate(0, below, 0).scale(size).translate(part.joint().x, part.joint().y + lift, 0).rotateX(swing)
                .translate(part.x(), part.y(), part.z()).scale(part.wide(), part.high(), part.deep());
            BlockDisplay display = displays.get(index);
            display.setInterpolationDelay(0);
            display.setTransformationMatrix(shape);
        }
    }
}

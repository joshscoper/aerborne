package net.nerdsmp.paper.anticheat.physics;

import net.nerdsmp.paper.anticheat.physics.MovementSimulator.Box;
import net.nerdsmp.paper.anticheat.physics.MovementSimulator.Collider;
import net.nerdsmp.paper.anticheat.physics.MovementSimulator.Profile;
import net.nerdsmp.paper.anticheat.physics.MovementSimulator.Surface;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Trajectories here are written out tick by tick from vanilla's own update order (move with the current velocity,
 * then speed factor, gravity, drag and friction), the same way MotionModelTest replays jump arcs. Vanilla runs must
 * match to well inside the flag offset; cheats must be caught.
 */
class MovementSimulatorTest {
    private static final float EAST = -90.0F; // yaw facing +X
    private static final float DRAG = 0.6F * 0.91F;
    private static final double TIGHT = 1.0E-6D;

    /** Minimal block world: full cubes, slabs, stairs and custom surfaces. */
    private static final class World implements MovementSimulator.BlockView {
        private final List<Collider> boxes = new ArrayList<>();
        private final Map<Long, Surface> surfaces = new HashMap<>();

        World floor(int fromX, int toX, int fromZ, int toZ, int y, Surface surface, double top) {
            for (int x = fromX; x <= toX; x++) {
                for (int z = fromZ; z <= toZ; z++) {
                    boxes.add(new Collider(x, y, z, new Box(x, y, z, x + 1, y + top, z + 1)));
                    if (surface != null) surfaces.put(key(x, y, z), surface);
                }
            }
            return this;
        }

        World floor(int fromX, int toX, int y) {
            return floor(fromX, toX, -5, 5, y, null, 1.0D);
        }

        World box(int x, int y, int z, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            boxes.add(new Collider(x, y, z, new Box(x + minX, y + minY, z + minZ, x + maxX, y + maxY, z + maxZ)));
            return this;
        }

        @Override public void collect(Box area, List<Collider> out) {
            for (Collider collider : boxes) if (collider.box().intersects(area)) out.add(collider);
        }

        @Override public Surface surface(int x, int y, int z) {
            return surfaces.getOrDefault(key(x, y, z), Surface.DEFAULT);
        }

        private static long key(int x, int y, int z) {
            return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
        }
    }

    /** Ground acceleration for forward input: speed x 0.216 / friction^3, times vanilla's 0.98 input scale. */
    private static double groundAcceleration(double speed, float friction) {
        float influenced = (float) speed * (0.21600002F / (friction * friction * friction));
        return 0.98F * (double) influenced;
    }

    /** Places the player and gives the simulator one still tick so it knows the velocity. */
    private static MovementSimulator standing(World world, Profile profile, double x, double y) {
        MovementSimulator sim = new MovementSimulator();
        sim.place(world, profile, x, y, 0.0D);
        sim.step(world, profile, x, y, 0.0D, EAST, 1);
        assertTrue(sim.onGround());
        return sim;
    }

    private static MovementSimulator.Result step(MovementSimulator sim, World world, Profile profile, double x, double y, double z) {
        return sim.step(world, profile, x, y, z, EAST, 1);
    }

    // ---------------------------------------------------------------------------------------------------
    // Vanilla trajectories never flag
    // ---------------------------------------------------------------------------------------------------

    @Test void sprintThenSprintJumpOnFlatGroundIsExact() {
        World world = new World().floor(-5, 200, -1);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
        double acceleration = groundAcceleration(0.13D, 0.6F);
        double x = 0.5D;
        double v = 0.0D;
        for (int tick = 0; tick < 30; tick++) {
            v += acceleration;
            x += v;
            var result = step(sim, world, profile, x, 0.0D, 0.0D);
            assertTrue(result.offset() < TIGHT, "tick " + tick + ": " + result.describe());
            v *= DRAG;
        }
        assertEquals(0.2806D, v / DRAG, 1.0E-3D, "steady sprint is vanilla's 5.6 m/s");

        // Sprint jumps: +0.2 along the heading on take-off, then 0.026 air acceleration with 0.91 air drag.
        for (int hop = 0; hop < 3; hop++) {
            Arc arc = new Arc(x, v, 0.0D);
            for (int tick = 0; arc.airborne; tick++) {
                arc.tick(0.0D);
                var result = step(sim, world, profile, arc.x, arc.y, 0.0D);
                assertTrue(result.offset() < TIGHT, "hop " + hop + " tick " + tick + ": " + result.describe());
                assertTrue(tick < 20, "should land");
            }
            x = arc.x;
            v = arc.v;
            assertTrue(sim.onGround());
        }
    }

    /** Vanilla sprint-jump arc along +X from flat ground at y=0, with an optional cheat boost on take-off. */
    private static final class Arc {
        double x, v, y;
        double vy = 0.42F;
        boolean ground = true;
        boolean airborne = true;

        Arc(double x, double v, double y) {
            this.x = x;
            this.v = v;
            this.y = y;
        }

        void tick(double takeOffBoost) {
            v += ground ? 0.2D + groundAcceleration(0.13D, 0.6F) + takeOffBoost : 0.98F * (double) 0.025999999F;
            x += v;
            double dy = vy;
            if (y + dy <= 0.0D) {
                dy = -y;
                airborne = false;
            }
            y += dy;
            v *= ground ? DRAG : 0.91F;
            vy = (vy - 0.08D) * 0.98F;
            ground = false;
        }
    }

    @Test void walkingIntoAWallStopsFlushAndStaysClean() {
        World world = new World().floor(-5, 20, -1).floor(3, 3, 0).floor(3, 3, 1);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
        double acceleration = groundAcceleration(0.1D, 0.6F);
        double x = 0.5D;
        double v = 0.0D;
        boolean hit = false;
        for (int tick = 0; tick < 30; tick++) {
            if (Math.hypot(v, 0) < 0.003D) v = 0.0D;
            v += acceleration;
            double move = Math.min(v, 3.0D - (x + 0.3D));
            if (move < v) {
                hit = true;
                v = 0.0D; // vanilla zeroes velocity on the blocked axis
            }
            x += move;
            var result = step(sim, world, profile, x, 0.0D, 0.0D);
            assertTrue(result.offset() < TIGHT, "tick " + tick + ": " + result.describe());
            v *= DRAG;
        }
        assertTrue(hit);
        assertEquals(2.7D, x, 1.0E-9D);
    }

    @Test void walkingThroughAWallIsCaught() {
        World world = new World().floor(-5, 20, -1).floor(3, 3, 0).floor(3, 3, 1);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 2.7D, 0.0D);
        assertTrue(step(sim, world, profile, 2.8D, 0.0D, 0.0D).flagged(), "phasing 0.1 into a wall");
    }

    @Test void steppingOntoSlabsAndUpStairs() {
        // Slab row from x=3, then a stair (bottom half from x=6, top half from x=6.5) leading onto full blocks at y=1.
        World world = new World().floor(-5, 30, -1);
        for (int x = 3; x <= 5; x++) for (int z = -5; z <= 5; z++) world.box(x, 0, z, 0, 0, 0, 1, 0.5D, 1);
        for (int z = -5; z <= 5; z++) world.box(6, 0, z, 0, 0, 0, 1, 0.5D, 1).box(6, 0, z, 0.5D, 0.5D, 0, 1, 1, 1);
        world.floor(7, 30, -5, 5, 0, null, 1.0D);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
        double acceleration = groundAcceleration(0.1D, 0.6F);
        double x = 0.5D;
        double y = 0.0D;
        double v = 0.0D;
        int steps = 0;
        for (int tick = 0; tick < 60; tick++) {
            v += acceleration;
            double front = x + 0.3D + v;
            double ground = front > 6.5D ? 1.0D : front > 3.0D ? 0.5D : 0.0D;
            if (ground > y) {
                steps++;
                y = ground; // step-up keeps the full horizontal move
            }
            x += v;
            var result = step(sim, world, profile, x, y, 0.0D);
            assertTrue(result.offset() < TIGHT, "tick " + tick + " at x " + x + ": " + result.describe());
            v *= DRAG;
        }
        assertEquals(2, steps, "slab, then the stair's top half");
        assertEquals(1.0D, y);
    }

    @Test void stepTallerThanStepHeightIsCaught() {
        World world = new World().floor(-5, 20, -1).floor(3, 3, 0);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 2.6D, 0.0D);
        assertTrue(step(sim, world, profile, 2.7D, 1.0D, 0.0D).flagged(), "a full block is above the 0.6 step height");
    }

    @Test void sneakingStopsAtTheEdgeAndReleasingKeepsTheBuiltUpVelocity() {
        // Floor ends at x=3. Holding W while sneaking walks until the box hangs over the edge, then stops.
        World world = new World().floor(-5, 2, -1).floor(-5, 20, -6);
        Profile profile = Profile.vanilla().sneaking();
        MovementSimulator sim = standing(world, profile, 1.0D, 0.0D);
        double acceleration = (0.98F * 0.3F) * (double) ((float) 0.1D * (0.21600002F / (0.6F * 0.6F * 0.6F)));
        double x = 1.0D;
        double v = 0.0D;
        for (int tick = 0; tick < 40; tick++) {
            v += acceleration;
            double move = v;
            while (move != 0.0D && x - 0.3D + 1.0E-7D + move >= 3.0D) {
                if (Math.abs(move) <= 0.05D) {
                    move = 0.0D;
                    break;
                }
                move -= 0.05D;
            }
            x += move;
            var result = step(sim, world, profile, x, 0.0D, 0.0D);
            assertTrue(result.offset() < TIGHT, "tick " + tick + ": " + result.describe());
            v *= DRAG; // the back-off moves the box but leaves velocity alone
        }
        assertTrue(x > 3.0D && x < 3.3D, "hangs over the edge without falling, x " + x);
        assertTrue(sim.onGround());

        // Release sneak, keep W: the velocity that built up against the edge carries the player off at once.
        // Vertical collision resolves before horizontal, so the drop starts one tick after leaving the edge.
        Profile standingUp = Profile.vanilla();
        v += groundAcceleration(0.1D, 0.6F);
        x += v;
        var result = step(sim, world, standingUp, x, 0.0D, 0.0D);
        assertTrue(result.offset() < TIGHT, result.describe());
        v = v * DRAG + groundAcceleration(0.1D, 0.6F);
        x += v;
        result = step(sim, world, standingUp, x, (0.0D - 0.08D) * 0.98F, 0.0D);
        assertTrue(result.offset() < TIGHT, result.describe());
        assertFalse(sim.onGround());
    }

    @Test void iceAcceleratesSlowlyAndSlidesFar() {
        Surface ice = new Surface(0.98D, 1.0D, 1.0D);
        World world = new World().floor(-5, 300, -5, 5, -1, ice, 1.0D);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
        double acceleration = groundAcceleration(0.1D, 0.98F);
        float drag = 0.98F * 0.91F;
        double x = 0.5D;
        double v = 0.0D;
        double released = 0.0D;
        for (int tick = 0; tick < 80; tick++) {
            if (Math.abs(v) < 0.003D) v = 0.0D;
            if (tick < 40) v += acceleration; // walk, then let go and slide
            else if (tick == 40) released = x;
            x += v;
            var result = step(sim, world, profile, x, 0.0D, 0.0D);
            assertTrue(result.offset() < TIGHT, "tick " + tick + ": " + result.describe());
            v *= drag;
        }
        assertTrue(x - released > 1.5D, "slides far after letting go (stone stops within 0.3), slid " + (x - released));
    }

    @Test void soulSandAndHoneySlowWalkingAndHoneyHalvesTheJump() {
        // Soul sand's top is at 14/16, honey's at 15/16: the player's feet are inside the block, which sets the speed factor.
        for (double top : new double[] {0.875D, 0.9375D}) {
            boolean honey = top > 0.9D;
            Surface surface = new Surface(0.6D, 0.4D, honey ? 0.5D : 1.0D);
            World world = new World().floor(-5, 100, -5, 5, 0, surface, top);
            Profile profile = Profile.vanilla();
            MovementSimulator sim = standing(world, profile, 0.5D, top);
            double acceleration = groundAcceleration(0.1D, 0.6F);
            double x = 0.5D;
            double v = 0.0D;
            for (int tick = 0; tick < 30; tick++) {
                v += acceleration;
                x += v;
                var result = step(sim, world, profile, x, top, 0.0D);
                assertTrue(result.offset() < TIGHT, "tick " + tick + ": " + result.describe());
                v *= 0.4F;
                v *= DRAG;
            }
            assertEquals(acceleration / (1.0D - 0.4F * DRAG), v / (0.4F * DRAG), 1.0E-6D, "steady state on the slow block");

            // Moving at normal walking speed on it is a no-slow cheat.
            double cheat = 0.2158D;
            assertTrue(step(sim, world, profile, x + cheat, top, 0.0D).flagged(), honey ? "honey" : "soul sand");

            if (honey) {
                MovementSimulator jumper = standing(world, profile, 0.5D, top);
                double jump = 0.42F * 0.5F;
                assertTrue(step(jumper, world, profile, 0.5D, top + jump, 0.0D).offset() < TIGHT, "honey jump is half height");
                MovementSimulator cheater = standing(world, profile, 0.5D, top);
                assertTrue(step(cheater, world, profile, 0.5D, top + 0.42F, 0.0D).flagged(), "full jump off honey");
            }
        }
    }

    @Test void standingStillWithSilentTicksIsClean() {
        World world = new World().floor(-5, 5, -1);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
        for (int i = 0; i < 10; i++) {
            var result = sim.step(world, profile, 0.5D, 0.0D, 0.0D, EAST, 20); // the 20-tick position reminder
            assertTrue(result.evaluated() && !result.flagged(), result.describe());
        }
    }

    @Test void diagonalSneakingInEitherScalingIsClean() {
        World world = new World().floor(-50, 50, -50, 50, -1, null, 1.0D);
        Profile profile = Profile.vanilla().sneaking();
        for (boolean legacy : new boolean[] {false, true}) {
            MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
            var input = new MovementSimulator.Input(1, 1, false, true, true, false, false, legacy);
            float[] move = MovementSimulator.moveVector(input, 0.3D);
            double a = Math.hypot(move[0], move[1]) * (float) 0.1D;
            double x = 0.5D;
            double z = 0.0D;
            double v = 0.0D;
            for (int tick = 0; tick < 20; tick++) {
                v += a;
                // Facing +X, W+A heads +X and -Z... strafe left of east is north (-Z).
                x += v * Math.sqrt(0.5D);
                z -= v * Math.sqrt(0.5D);
                var result = step(sim, world, profile, x, 0.0D, z);
                assertTrue(result.offset() < 1.0E-4D, (legacy ? "legacy " : "") + "tick " + tick + ": " + result.describe());
                v *= DRAG;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------
    // Cheats are caught
    // ---------------------------------------------------------------------------------------------------

    @Test void fivePercentSpeedIsCaughtEveryTick() {
        World world = new World().floor(-5, 400, -1);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
        double acceleration = groundAcceleration(0.13D, 0.6F);
        double x = 0.5D;
        double v = 0.0D;
        int flagged = 0;
        for (int tick = 0; tick < 40; tick++) {
            v += acceleration;
            v *= 1.05D; // the cheat scales the client's motion every tick
            x += v;
            if (step(sim, world, profile, x, 0.0D, 0.0D).flagged()) flagged++;
            v *= DRAG;
        }
        assertTrue(flagged >= 38, "1.05x sprint flagged on " + flagged + " of 40 ticks");
    }

    @Test void bhopBoostIsCaught() {
        // Sprint-jumping with an extra 0.1 b/t on every take-off, a typical bhop speed.
        World world = new World().floor(-5, 400, -1);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
        double x = 0.5D;
        double v = 0.0D;
        int flaggedTakeOffs = 0;
        int flaggedAirTicks = 0;
        for (int hop = 0; hop < 5; hop++) {
            Arc arc = new Arc(x, v, 0.0D);
            boolean takeOff = true;
            while (arc.airborne) {
                arc.tick(0.1D);
                boolean flagged = step(sim, world, profile, arc.x, arc.y, 0.0D).flagged();
                if (flagged && takeOff) flaggedTakeOffs++;
                if (flagged && !takeOff) flaggedAirTicks++;
                takeOff = false;
            }
            x = arc.x;
            v = arc.v;
        }
        assertEquals(5, flaggedTakeOffs, "every boosted take-off");
        assertEquals(0, flaggedAirTicks, "the rest of each arc is vanilla, so the check re-synchronises after the boost");
    }

    @Test void airStrafeIsCaught() {
        // Sprint jump east, then turn the camera 10 degrees a tick in the air. Vanilla only nudges velocity by 0.026 a
        // tick; the strafe cheat points the whole velocity wherever the player looks.
        World world = new World().floor(-50, 50, -50, 50, -1, null, 1.0D);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
        Arc arc = new Arc(0.5D, 0.0D, 0.0D);
        arc.tick(0.0D);
        assertFalse(step(sim, world, profile, arc.x, arc.y, 0.0D).flagged(), "the jump itself is vanilla");
        double speed = arc.v;
        double x = arc.x;
        double y = arc.y;
        double z = 0.0D;
        double vy = arc.vy;
        int flagged = 0;
        for (int tick = 1; tick <= 6; tick++) {
            float yaw = EAST + 10.0F * tick;
            speed = speed + 0.98F * (double) 0.025999999F;
            x += -Math.sin(Math.toRadians(yaw)) * speed;
            z += Math.cos(Math.toRadians(yaw)) * speed;
            y += vy;
            if (sim.step(world, profile, x, y, z, yaw, 1).flagged()) flagged++;
            speed *= 0.91F;
            vy = (vy - 0.08D) * 0.98F;
        }
        assertEquals(6, flagged);
    }

    @Test void hoverIsCaught() {
        World world = new World().floor(-5, 5, -1);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = standing(world, profile, 0.5D, 0.0D);
        step(sim, world, profile, 0.5D, 0.42F, 0.0D);
        int flagged = 0;
        for (int i = 0; i < 10; i++) if (step(sim, world, profile, 0.5D, 0.42F, 0.0D).flagged()) flagged++;
        assertEquals(10, flagged);
    }

    @Test void resetAndPlacementNeverFlag() {
        World world = new World().floor(-5, 50, -1);
        Profile profile = Profile.vanilla();
        MovementSimulator sim = new MovementSimulator();
        assertFalse(sim.step(world, profile, 0.5D, 0.0D, 0.0D, EAST, 1).evaluated());
        assertFalse(sim.step(world, profile, 5.0D, 0.0D, 0.0D, EAST, 1).evaluated(), "the first move only learns velocity");
        sim.reset();
        assertFalse(sim.step(world, profile, 20.0D, 3.0D, 0.0D, EAST, 1).evaluated());
        assertTrue(sim.at(20.0D, 3.0D, 0.0D));
    }

    @Test void lookupTableMatchesVanillaAngles() {
        assertEquals(-1.0F, MovementSimulator.sin(EAST * ((float) Math.PI / 180.0F)));
        assertEquals(0.0F, MovementSimulator.cos(EAST * ((float) Math.PI / 180.0F)));
        assertEquals(1.0F, MovementSimulator.cos(0.0D));
    }
}

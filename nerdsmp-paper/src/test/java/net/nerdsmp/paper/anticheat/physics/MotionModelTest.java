package net.nerdsmp.paper.anticheat.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MotionModelTest {
    private static final double SPRINT = 0.13D; // vanilla walk 0.1 x 1.3 sprint modifier
    private static final double WALK = 0.1D;

    private static MotionModel.Environment env(boolean before, boolean after, double speed, boolean sprinting) {
        return new MotionModel.Environment(before, after, false, 0.6D, 0.6D, speed, sprinting, 0.08D, false, 0.42D, 0.6D, false);
    }

    private static MotionModel warmed(double speed) {
        MotionModel model = new MotionModel();
        model.step(0, 0, 0, 1, env(true, true, speed, true));
        return model;
    }

    /** Replays vanilla's own tick order: move with current velocity, then gravity and drag. */
    @Test void vanillaStandingJumpArcIsClean() {
        MotionModel model = warmed(WALK);
        assertEquals(0.0D, model.step(0, 0, 0, 1, env(true, true, WALK, false)).verticalError());
        double y = 0.0D;
        double vy = 0.42D;
        boolean grounded = true;
        for (int tick = 0; tick < 40; tick++) {
            double dy = vy;
            boolean landed = y + dy <= 0.0D;
            if (landed) dy = -y;
            MotionModel.Result result = model.step(0, dy, 0, 1, env(grounded, landed, WALK, false));
            assertEquals(0.0D, result.verticalError(), 1.0E-12, "tick " + tick + " dy " + dy + " " + result.detail());
            y += dy;
            grounded = landed;
            if (landed) break;
            vy = (vy - 0.08D) * 0.98D;
            if (Math.abs(vy) < 0.003D) vy = 0.0D;
        }
        assertTrue(grounded, "the arc should land");
    }

    @Test void vanillaSprintAndSprintJumpSpeedsAreClean() {
        MotionModel model = warmed(SPRINT);
        double v = 0.0D;
        for (int tick = 0; tick < 30; tick++) {
            v = v + SPRINT; // ground acceleration on normal blocks: speed x 0.216 / 0.6^3 = speed
            assertEquals(0.0D, model.step(v, 0, 0, 1, env(true, true, SPRINT, true)).horizontalExcess(), "tick " + tick);
            v *= 0.6D * 0.91D;
        }
        double jump = v + SPRINT + 0.2D;
        assertEquals(0.0D, model.step(jump, 0.42D, 0, 1, env(true, false, SPRINT, true)).horizontalExcess());
        double air = jump * 0.91D + 0.026D;
        assertEquals(0.0D, model.step(air, 0.3332D, 0, 1, env(false, false, SPRINT, true)).horizontalExcess());
    }

    @Test void hoveringInTheAirIsCaught() {
        MotionModel model = warmed(WALK);
        model.step(0, 0.42D, 0, 1, env(true, false, WALK, false));
        double worst = 0.0D;
        for (int i = 0; i < 10; i++) worst = Math.max(worst, model.step(0, 0.0D, 0, 1, env(false, false, WALK, false)).verticalError());
        assertTrue(worst > 0.05D, "hover should exceed gravity by a wide margin, was " + worst);
    }

    @Test void highJumpAndTallStepAreCaughtButNormalStepIsNot() {
        assertTrue(warmed(WALK).step(0, 0.7D, 0, 1, env(true, false, WALK, false)).verticalError() > 0.2D);
        assertEquals(0.0D, warmed(WALK).step(0.1D, 0.5D, 0, 1, env(true, true, WALK, false)).verticalError());
        assertTrue(warmed(WALK).step(0.1D, 1.0D, 0, 1, env(true, true, WALK, false)).verticalError() > 0.3D);
    }

    @Test void groundSpeedHackIsCaught() {
        MotionModel model = warmed(SPRINT);
        double worst = 0.0D;
        for (int i = 0; i < 10; i++) worst = Math.max(worst, model.step(0.5D, 0, 0, 1, env(true, true, SPRINT, true)).horizontalExcess());
        assertTrue(worst > 0.1D, "0.5 blocks/tick on ground is far above sprint speed, excess " + worst);
    }

    @Test void speedPotionRaisesTheLimitThroughTheAttribute() {
        double speedTwo = SPRINT * 1.4D; // Speed II: +20% per level on the attribute
        MotionModel model = warmed(speedTwo);
        double v = 0.0D;
        for (int tick = 0; tick < 30; tick++) {
            v += speedTwo;
            assertEquals(0.0D, model.step(v, 0, 0, 1, env(true, true, speedTwo, true)).horizontalExcess());
            v *= 0.546D;
        }
    }

    @Test void specialStatesAndResetNeverFlag() {
        MotionModel model = warmed(WALK);
        var special = new MotionModel.Environment(false, false, false, 0.6D, 0.6D, WALK, false, 0.08D, false, 0.42D, 0.6D, true);
        assertEquals(MotionModel.Result.CLEAN, model.step(3.0D, 2.0D, 0, 1, special));
        model.reset();
        assertEquals(MotionModel.Result.CLEAN, model.step(5.0D, 5.0D, 0, 1, env(false, false, WALK, false)));
    }

    @Test void slowFallingUsesReducedGravityFromTheLedgeOnwards() {
        MotionModel model = new MotionModel();
        var ground = new MotionModel.Environment(true, true, false, 0.6D, 0.6D, WALK, false, 0.08D, true, 0.42D, 0.6D, false);
        var air = new MotionModel.Environment(false, false, false, 0.6D, 0.6D, WALK, false, 0.08D, true, 0.42D, 0.6D, false);
        model.step(0, 0, 0, 1, ground);
        model.step(0.1D, 0, 0, 1, ground);
        double vy = (0.0D - 0.01D) * 0.98D; // velocity carried off the ledge under Slow Falling
        var firstFall = new MotionModel.Environment(true, false, false, 0.6D, 0.6D, WALK, false, 0.08D, true, 0.42D, 0.6D, false);
        assertEquals(0.0D, model.step(0, vy, 0, 1, firstFall).verticalError(), 1.0E-12);
        for (int i = 0; i < 10; i++) {
            vy = (vy - 0.01D) * 0.98D;
            assertEquals(0.0D, model.step(0, vy, 0, 1, air).verticalError(), 1.0E-12, "tick " + i);
        }
        assertTrue(model.step(0, (vy - 0.08D) * 0.98D * 3, 0, 1, air).verticalError() < -0.005D, "falling at full speed under Slow Falling is wrong");
    }
}

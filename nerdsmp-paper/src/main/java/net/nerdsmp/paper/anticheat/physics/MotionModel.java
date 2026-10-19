package net.nerdsmp.paper.anticheat.physics;

/**
 * Predicts a player's next movement with vanilla's own physics constants: gravity and 0.98 air drag vertically,
 * block-friction inertia plus input acceleration horizontally. It compares what the client actually moved against
 * what physics allows and reports the difference. It never guesses: anything it cannot model (liquids, climbing,
 * elytra, knockback and so on) arrives as {@link Environment#special()} and simply re-synchronises the state.
 *
 * <p>Vanilla order per tick: the movement uses the velocity carried from the previous tick (plus this tick's input
 * acceleration and any jump), then velocity is reduced by gravity and drag for the next tick. The state stored here
 * is therefore "the vertical displacement physics expects next tick".
 */
public final class MotionModel {
    /** Inputs a caller measures from the world for one movement step. */
    public record Environment(
        boolean groundedBefore,
        boolean groundedAfter,
        boolean headBlocked,
        double frictionBefore,
        double frictionAfter,
        double movementSpeed,
        boolean sprinting,
        double gravity,
        boolean slowFalling,
        double jumpPower,
        double stepHeight,
        boolean special
    ) {
        public static Environment flatGround(double movementSpeed, boolean sprinting) {
            return new Environment(true, true, false, 0.6D, 0.6D, movementSpeed, sprinting, 0.08D, false, 0.42D, 0.6D, false);
        }
    }

    /** Positive verticalError: moved higher than physics allows. Negative: fell faster. horizontalExcess: blocks too far. */
    public record Result(double verticalError, double horizontalExcess, boolean jumped, String detail) {
        public static final Result CLEAN = new Result(0.0D, 0.0D, false, "");
    }

    private static final double DRAG_AIR = 0.98D;
    private static final double INERTIA_AIR = 0.91D;
    private static final double AIR_ACCELERATION = 0.026D; // 0.02 flying speed, x1.3 when sprinting (upper bound)
    private static final double SPRINT_JUMP_BOOST = 0.2D;
    private static final double VERTICAL_TOLERANCE = 0.005D;
    private static final double HORIZONTAL_TOLERANCE = 0.01D;

    private boolean initialised;
    private double nextVertical;
    private double lastHorizontal;
    private boolean lastGrounded;
    private double lastFriction = 0.6D;

    /** Forget prediction state, e.g. after a teleport, knockback or respawn. */
    public void reset() {
        initialised = false;
    }

    public Result step(double dx, double dy, double dz, int ticks, Environment env) {
        int n = Math.max(1, Math.min(ticks, 20));
        double horizontal = Math.hypot(dx, dz);
        if (!initialised || env.special()) {
            resync(dy, horizontal, n, env);
            return Result.CLEAN;
        }
        double verticalError = 0.0D;
        boolean jumped = false;
        String detail = "";

        if (env.groundedBefore() && dy > 1.0E-4D) {
            // Leaving the ground upwards: a jump, or stepping up a block edge.
            double highestJump = jumpHeight(env, n);
            double allowed = Math.max(highestJump, env.groundedAfter() ? env.stepHeight() : highestJump);
            if (dy > allowed + VERTICAL_TOLERANCE) {
                verticalError = dy - allowed;
                detail = String.format(java.util.Locale.ROOT, "rose %.3f from ground, physics allows %.3f", dy, allowed);
            }
            jumped = !env.groundedAfter(); // left the ground upwards: velocity now follows the jump arc
        } else if (!env.groundedBefore() || dy < -1.0E-4D) {
            double expected = predictedFall(n, env);
            double error = dy - expected;
            if (error > VERTICAL_TOLERANCE && !(env.groundedAfter() && dy <= 0.0D)) {
                // Higher than gravity allows. Landing early is fine; rising or hovering is not.
                if (!(env.groundedBefore() && dy <= env.stepHeight())) {
                    verticalError = error;
                    detail = String.format(java.util.Locale.ROOT, "moved %.3f vertically, gravity expects %.3f", dy, expected);
                }
            } else if (error < -VERTICAL_TOLERANCE && !env.headBlocked() && !env.groundedAfter()) {
                verticalError = error;
                detail = String.format(java.util.Locale.ROOT, "fell %.3f, gravity expects %.3f", dy, expected);
            }
        }

        double allowedHorizontal = horizontalBound(n, env, jumped);
        double horizontalExcess = 0.0D;
        if (horizontal > allowedHorizontal) {
            horizontalExcess = horizontal - allowedHorizontal;
            detail = (detail.isEmpty() ? "" : detail + "; ")
                + String.format(java.util.Locale.ROOT, "moved %.3f horizontally, physics allows %.3f", horizontal, allowedHorizontal);
        }

        resync(dy, horizontal, n, env, jumped);
        return verticalError == 0.0D && horizontalExcess == 0.0D ? Result.CLEAN : new Result(verticalError, horizontalExcess, jumped, detail);
    }

    private void resync(double dy, double horizontal, int n, Environment env) {
        resync(dy, horizontal, n, env, false);
    }

    private void resync(double dy, double horizontal, int n, Environment env, boolean jumped) {
        initialised = true;
        double lastStep = dy / n;
        if (env.groundedAfter() && !jumped) nextVertical = clampTiny((0.0D - gravity(0.0D, env)) * DRAG_AIR);
        else if (env.headBlocked() && lastStep > 0.0D) nextVertical = clampTiny((0.0D - gravity(0.0D, env)) * DRAG_AIR);
        else nextVertical = clampTiny((lastStep - gravity(lastStep, env)) * DRAG_AIR);
        lastHorizontal = horizontal / n;
        lastGrounded = env.groundedAfter();
        lastFriction = env.frictionAfter();
    }

    /** Highest rise reachable within n ticks of a jump: jump velocity then gravity and drag. */
    private double jumpHeight(Environment env, int n) {
        double velocity = env.jumpPower();
        double height = 0.0D;
        double best = 0.0D;
        for (int i = 0; i < n; i++) {
            height += velocity;
            best = Math.max(best, height);
            velocity = clampTiny((velocity - gravity(velocity, env)) * DRAG_AIR);
        }
        return best;
    }

    private double predictedFall(int n, Environment env) {
        double velocity = nextVertical;
        double total = 0.0D;
        for (int i = 0; i < n; i++) {
            total += velocity;
            velocity = clampTiny((velocity - gravity(velocity, env)) * DRAG_AIR);
        }
        return total;
    }

    /** Upper bound on horizontal travel over n ticks: inertia from last tick plus maximum acceleration each tick. */
    private double horizontalBound(int n, Environment env, boolean jumped) {
        double velocity = lastHorizontal * (lastGrounded ? lastFriction * INERTIA_AIR : INERTIA_AIR);
        double total = 0.0D;
        double speed = env.movementSpeed() * (env.sprinting() ? 1.0D : 1.3D); // sprint state can trail the client by a packet
        for (int i = 0; i < n; i++) {
            boolean ground = i == 0 ? env.groundedBefore() : env.groundedAfter();
            double friction = i == 0 ? env.frictionBefore() : env.frictionAfter();
            double acceleration = ground ? speed * (0.21600002D / (friction * friction * friction)) : AIR_ACCELERATION;
            velocity += acceleration + (i == 0 && jumped ? SPRINT_JUMP_BOOST : 0.0D);
            total += velocity;
            velocity *= ground ? friction * INERTIA_AIR : INERTIA_AIR;
        }
        return total * 1.003D + HORIZONTAL_TOLERANCE;
    }

    private static double gravity(double velocity, Environment env) {
        return env.slowFalling() && velocity <= 0.0D ? Math.min(env.gravity(), 0.01D) : env.gravity();
    }

    private static double clampTiny(double velocity) {
        return Math.abs(velocity) < 0.003D ? 0.0D : velocity;
    }
}

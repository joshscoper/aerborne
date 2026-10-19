package net.nerdsmp.paper.anticheat.physics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Collision-accurate replay of vanilla player movement, one client tick at a time, in the style of Grim.
 *
 * <p>For every tick it tries each input the client could have pressed (the eight WASD directions or none, with and
 * without sprint, sneak, item use and jump) and replays vanilla's own code path for each one: the tiny-velocity clamp,
 * the jump, input acceleration scaled by the friction of the block underfoot, sneak edge back-off, axis-by-axis
 * collision against the world's real block shapes with step-up, the block speed factor (soul sand, honey), then
 * gravity, drag and friction. The candidate that lands closest to the position the client reported explains the
 * tick. Only when no candidate comes within {@link #FLAG_OFFSET} does the tick count against the player.
 *
 * <p>The client can't show us its velocity, and two inputs can leave the player at the same position with different
 * velocities (holding forward against a sneak edge, for example). Up to {@link #MAX_HYPOTHESES} such states are
 * carried forward until a later tick tells them apart. After a mismatch the state resynchronises to what the client
 * actually did, so one odd tick never cascades.
 *
 * <p>Pure Java: block data arrives through {@link BlockView}, which the caller must back with data that is safe to
 * read on the calling thread. Watchdog calls this from PlayerMoveEvent, on the main thread.
 */
public final class MovementSimulator {
    /** A tick whose best candidate misses the reported position by more than this counts as a violation. */
    public static final double FLAG_OFFSET = 1.0E-3D;
    /** Candidates within this distance are equally good explanations and are all kept. */
    private static final double ADOPT_OFFSET = 1.0E-4D;
    /** The client sends no position while it moves less than this per tick. */
    private static final double SILENT_MOVE = 2.0E-4D;
    private static final int MAX_HYPOTHESES = 4;
    private static final float RADIANS = (float) Math.PI / 180.0F;
    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin(i * Math.PI * 2.0D / 65536.0D);
    }

    /** Axis-aligned box in world coordinates. */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public Box move(double x, double y, double z) {
            return new Box(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
        }

        public Box expandTowards(double x, double y, double z) {
            return new Box(minX + Math.min(0.0D, x), minY + Math.min(0.0D, y), minZ + Math.min(0.0D, z),
                maxX + Math.max(0.0D, x), maxY + Math.max(0.0D, y), maxZ + Math.max(0.0D, z));
        }

        public boolean intersects(Box o) {
            return minX < o.maxX && maxX > o.minX && minY < o.maxY && maxY > o.minY && minZ < o.maxZ && maxZ > o.minZ;
        }
    }

    /** One collision box of the block at (x, y, z). Vanilla picks the block a player stands on by its position. */
    public record Collider(int x, int y, int z, Box box) { }

    /** Movement properties of a block: friction (ice 0.98), speed factor (soul sand 0.4) and jump factor (honey 0.5). */
    public record Surface(double friction, double speedFactor, double jumpFactor) {
        public static final Surface DEFAULT = new Surface(0.6D, 1.0D, 1.0D);
    }

    /** The world as the simulation sees it. Implementations must be safe to read on the simulating thread. */
    public interface BlockView {
        /** Adds every collision box that intersects {@code area}. */
        void collect(Box area, List<Collider> out);

        Surface surface(int x, int y, int z);
    }

    /**
     * The player's live values. walkSpeed is the movement-speed attribute without the sprint modifier; the
     * simulation applies sprint itself because the server's sprint flag can trail the client's by a tick.
     * mayBeSneaking and mayBeUsingItem allow the slowed inputs; they should stay true for a few ticks after the
     * server last saw the state, since the client changes it first.
     */
    public record Profile(double walkSpeed, double sneakSpeed, double jumpStrength, double jumpBoost, double gravity,
                          boolean slowFalling, double stepHeight, double movementEfficiency, double width, double height,
                          boolean mayBeSneaking, boolean mayBeUsingItem) {
        public static Profile vanilla() {
            return new Profile(0.1D, 0.3D, 0.42D, 0.0D, 0.08D, false, 0.6D, 0.0D, 0.6D, 1.8D, false, false);
        }

        public Profile sneaking() {
            return new Profile(walkSpeed, sneakSpeed, jumpStrength, jumpBoost, gravity, slowFalling, stepHeight, movementEfficiency,
                width, 1.5D, true, mayBeUsingItem);
        }

        public Profile withWalkSpeed(double speed) {
            return new Profile(speed, sneakSpeed, jumpStrength, jumpBoost, gravity, slowFalling, stepHeight, movementEfficiency,
                width, height, mayBeSneaking, mayBeUsingItem);
        }
    }

    /**
     * One input candidate. forward: 1 = W, -1 = S. strafe: 1 = A, -1 = D. slow = crouching slowdown, edge = shift
     * held (stops at block edges); they differ only under a low ceiling. legacyDiagonal uses the pre-1.21.5 diagonal
     * scaling for slowed diagonal input, kept as an alternative because it is never faster.
     */
    public record Input(int forward, int strafe, boolean sprint, boolean slow, boolean edge, boolean usingItem, boolean jump,
                        boolean legacyDiagonal) {
        public String describe() {
            StringBuilder text = new StringBuilder();
            if (forward > 0) text.append('W');
            if (forward < 0) text.append('S');
            if (strafe > 0) text.append('A');
            if (strafe < 0) text.append('D');
            if (text.isEmpty()) text.append("no keys");
            if (sprint) text.append(" sprint");
            if (slow) text.append(edge ? " sneak" : " crouched");
            if (usingItem) text.append(" using item");
            if (jump) text.append(" jump");
            return text.toString();
        }
    }

    /** Offsets are reported minus predicted, for the best candidate of the reported tick. */
    public record Result(boolean evaluated, double offsetX, double offsetY, double offsetZ, double predictedHorizontal,
                         double observedHorizontal, double predictedVertical, double observedVertical, Input input) {
        public static final Result UNCHECKED = new Result(false, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, null);

        public double horizontalOffset() {
            return Math.hypot(offsetX, offsetZ);
        }

        public double offset() {
            return Math.sqrt(offsetX * offsetX + offsetY * offsetY + offsetZ * offsetZ);
        }

        public boolean flagged() {
            return evaluated && offset() > FLAG_OFFSET;
        }

        public String describe() {
            return String.format(Locale.ROOT, "moved %.4f across and %.4f up; the closest vanilla input (%s) gives %.4f and %.4f, %.4f off",
                observedHorizontal, observedVertical, input == null ? "none" : input.describe(), predictedHorizontal, predictedVertical, offset());
        }
    }

    /** One possible player state, or a candidate's outcome from one. */
    private static final class Body {
        double x, y, z, vx, vy, vz, fallDistance;
        boolean onGround, supported;
        int supportX, supportY, supportZ;
        // How a trial got here.
        Body origin;
        Input input;
        double moveX, moveY, moveZ, stepX, stepY, stepZ, score;
        boolean collidedX, collidedY, collidedZ;
        float friction = 1.0F;

        boolean sameState(Body o) {
            return onGround == o.onGround && Math.abs(vx - o.vx) < 1.0E-9D && Math.abs(vy - o.vy) < 1.0E-9D && Math.abs(vz - o.vz) < 1.0E-9D
                && Math.abs(fallDistance - o.fallDistance) < 1.0E-6D;
        }
    }

    private static final List<Input>[] CANDIDATES = buildCandidates();

    private List<Body> bodies = new ArrayList<>();
    private boolean velocityKnown;

    /** Forget everything, e.g. after a teleport, knockback or an exempt state. The next step only places the player. */
    public void reset() {
        bodies = new ArrayList<>();
        velocityKnown = false;
    }

    /** True when the simulation already stands at this position (the start of the next move). */
    public boolean at(double x, double y, double z) {
        if (bodies.isEmpty()) return false;
        Body body = bodies.getFirst();
        return Math.abs(body.x - x) < 1.0E-6D && Math.abs(body.y - y) < 1.0E-6D && Math.abs(body.z - z) < 1.0E-6D;
    }

    /** Starts tracking from a known position with unknown velocity. The next step learns the velocity, then checks begin. */
    public void place(BlockView world, Profile profile, double x, double y, double z) {
        Body body = new Body();
        body.x = x;
        body.y = y;
        body.z = z;
        Box box = box(profile, x, y, z);
        List<Collider> near = new ArrayList<>();
        world.collect(box.expandTowards(0.0D, -1.0D, 0.0D), near);
        body.onGround = supportedBy(near, box);
        support(near, profile, body, 0.0D, 0.0D);
        bodies = new ArrayList<>(List.of(body));
        velocityKnown = false;
    }

    /** Whether the most likely state is on the ground. */
    public boolean onGround() {
        return !bodies.isEmpty() && bodies.getFirst().onGround;
    }

    int hypotheses() {
        return bodies.size();
    }

    /**
     * Advances by one reported position. ticks is the number of client ticks it covers: the ticks before the last
     * sent no position, which vanilla only skips while moving less than 2e-4 blocks a tick.
     */
    public Result step(BlockView world, Profile profile, double x, double y, double z, float yaw, int ticks) {
        if (bodies.isEmpty()) {
            place(world, profile, x, y, z);
            return Result.UNCHECKED;
        }
        if (!velocityKnown) {
            Body synced = resync(world, profile, bodies.getFirst(), null, x, y, z);
            bodies = new ArrayList<>(List.of(synced));
            velocityKnown = true;
            return Result.UNCHECKED;
        }
        int n = Math.max(1, Math.min(ticks, 20));
        for (int i = 1; i < n; i++) advance(world, profile, yaw, Double.NaN, 0.0D, 0.0D);
        return advance(world, profile, yaw, x, y, z);
    }

    private Result advance(BlockView world, Profile profile, float yaw, double ox, double oy, double oz) {
        boolean silent = Double.isNaN(ox);
        List<Body> trials = new ArrayList<>();
        List<Collider> near = new ArrayList<>();
        for (Body from : bodies) {
            near.clear();
            world.collect(searchArea(profile, from), near);
            double vy = Math.abs(from.vy) < 0.003D ? 0.0D : from.vy;
            // Since 1.21 players clamp tiny horizontal velocity by its length; older code did it per axis. Try both when they differ.
            boolean tinyLength = from.vx * from.vx + from.vz * from.vz < 9.0E-6D;
            double ax = tinyLength ? 0.0D : from.vx;
            double az = tinyLength ? 0.0D : from.vz;
            double bx = Math.abs(from.vx) < 0.003D ? 0.0D : from.vx;
            double bz = Math.abs(from.vz) < 0.003D ? 0.0D : from.vz;
            for (Input input : candidates(from.onGround, profile)) {
                trials.add(score(simulate(world, profile, near, from, ax, vy, az, input, yaw), silent, ox, oy, oz));
                if (ax != bx || az != bz) trials.add(score(simulate(world, profile, near, from, bx, vy, bz, input, yaw), silent, ox, oy, oz));
            }
        }
        trials.sort((a, b) -> Double.compare(a.score, b.score));
        Body best = trials.getFirst();
        double accept = silent ? SILENT_MOVE + ADOPT_OFFSET : ADOPT_OFFSET;
        List<Body> next = new ArrayList<>();
        // Equally good explanations: keep held keys first (players rarely change input every tick), then the rest.
        for (int pass = 0; pass < 2 && next.size() < MAX_HYPOTHESES; pass++) {
            for (Body trial : trials) {
                if (trial.score > accept || next.size() == MAX_HYPOTHESES) break;
                if ((pass == 0) != trial.input.equals(trial.origin.input)) continue;
                boolean duplicate = false;
                for (Body kept : next) duplicate |= kept.sameState(trial);
                if (!duplicate) next.add(trial);
            }
        }
        if (silent) {
            if (next.isEmpty()) next.add(best);
            bodies = next;
            return Result.UNCHECKED;
        }
        Body origin = best.origin;
        double dx = ox - origin.x;
        double dy = oy - origin.y;
        double dz = oz - origin.z;
        Result result = new Result(true, dx - best.stepX, dy - best.stepY, dz - best.stepZ, Math.hypot(best.stepX, best.stepZ),
            Math.hypot(dx, dz), best.stepY, dy, best.input);
        if (next.isEmpty()) {
            next.add(resync(world, profile, origin, best, ox, oy, oz));
        } else {
            for (Body kept : next) {
                kept.x = ox;
                kept.y = oy;
                kept.z = oz;
            }
        }
        bodies = next;
        return result;
    }

    private static Body score(Body trial, boolean silent, double ox, double oy, double oz) {
        if (silent) {
            trial.score = Math.sqrt(trial.stepX * trial.stepX + trial.stepY * trial.stepY + trial.stepZ * trial.stepZ);
        } else {
            double ex = ox - trial.x;
            double ey = oy - trial.y;
            double ez = oz - trial.z;
            trial.score = Math.sqrt(ex * ex + ey * ey + ez * ez);
        }
        return trial;
    }

    /** One vanilla tick (LivingEntity.aiStep, travelInAir and Entity.move) from {@code from} with this input. */
    private static Body simulate(BlockView world, Profile p, List<Collider> near, Body from, double vx, double vy, double vz, Input input, float yaw) {
        Body t = new Body();
        t.origin = from;
        t.input = input;
        Box box = box(p, from.x, from.y, from.z);
        if (input.jump()) {
            float power = (float) p.jumpStrength() * (float) jumpFactor(world, from) + (float) p.jumpBoost();
            if (power > 1.0E-5F) {
                vy = Math.max(power, vy);
                if (input.sprint()) {
                    float angle = yaw * RADIANS;
                    vx += -sin(angle) * 0.2D;
                    vz += cos(angle) * 0.2D;
                }
            }
        }
        float friction = from.onGround ? (float) world.surface(belowX(from), belowY(from.y), belowZ(from)).friction() : 1.0F;
        float speed = from.onGround
            ? (float) (p.walkSpeed() * (input.sprint() ? 1.3D : 1.0D)) * (0.21600002F / (friction * friction * friction))
            : input.sprint() ? 0.025999999F : 0.02F;
        float[] move = moveVector(input, p.sneakSpeed());
        double lengthSqr = (double) move[0] * move[0] + (double) move[1] * move[1];
        if (lengthSqr >= 1.0E-7D) {
            double sx = move[0];
            double sz = move[1];
            if (lengthSqr > 1.0D) {
                double length = Math.sqrt(lengthSqr);
                sx /= length;
                sz /= length;
            }
            sx *= speed;
            sz *= speed;
            float angle = yaw * RADIANS;
            float sin = sin(angle);
            float cos = cos(angle);
            vx += sx * cos - sz * sin;
            vz += sz * cos + sx * sin;
        }

        double mx = vx;
        double my = vy;
        double mz = vz;
        if (input.edge() && my <= 0.0D && aboveGround(near, box, from, p.stepHeight())) {
            double[] backed = backOffFromEdge(near, box, mx, mz, p.stepHeight());
            mx = backed[0];
            mz = backed[1];
        }
        double[] c = collide(near, box, mx, my, mz, from.onGround, p.stepHeight());
        double moved = c[0] * c[0] + c[1] * c[1] + c[2] * c[2];
        boolean commit = moved > 1.0E-7D || mx * mx + my * my + mz * mz - moved < 1.0E-7D;
        t.stepX = commit ? c[0] : 0.0D;
        t.stepY = commit ? c[1] : 0.0D;
        t.stepZ = commit ? c[2] : 0.0D;
        t.x = from.x + t.stepX;
        t.y = from.y + t.stepY;
        t.z = from.z + t.stepZ;
        t.moveX = mx;
        t.moveY = my;
        t.moveZ = mz;
        t.collidedX = Math.abs(mx - c[0]) >= 1.0E-5F;
        t.collidedZ = Math.abs(mz - c[2]) >= 1.0E-5F;
        t.collidedY = my != c[1];
        t.onGround = t.collidedY && my < 0.0D;
        support(near, p, t, t.stepX, t.stepZ);
        if (t.collidedX) vx = 0.0D;
        if (t.collidedZ) vz = 0.0D;
        if (t.collidedY) vy = 0.0D;
        t.friction = friction;
        finish(world, p, t, vx, vy, vz);
        t.fallDistance = t.onGround ? 0.0D : from.fallDistance - Math.min(0.0D, c[1]);
        return t;
    }

    /** Block speed factor, then gravity, vertical drag and horizontal friction: the rest of the vanilla tick. */
    private static void finish(BlockView world, Profile p, Body t, double vx, double vy, double vz) {
        double feet = world.surface(floor(t.x), floor(t.y), floor(t.z)).speedFactor();
        float factor = (float) (feet != 1.0D ? feet : world.surface(belowX(t), belowY(t.y), belowZ(t)).speedFactor());
        factor = factor + (float) p.movementEfficiency() * (1.0F - factor);
        vx *= factor;
        vz *= factor;
        vy -= p.slowFalling() && vy <= 0.0D ? Math.min(p.gravity(), 0.01D) : p.gravity();
        float drag = t.friction * 0.91F;
        t.vx = vx * drag;
        t.vy = vy * 0.98F;
        t.vz = vz * drag;
    }

    /**
     * No candidate explained the tick: take the client's movement as the truth so the next tick is judged on its own.
     * Collisions are only honoured where the client really stopped against them.
     */
    private static Body resync(BlockView world, Profile p, Body from, Body trial, double ox, double oy, double oz) {
        Body r = new Body();
        r.input = trial == null ? null : trial.input;
        r.x = ox;
        r.y = oy;
        r.z = oz;
        double dx = ox - from.x;
        double dy = oy - from.y;
        double dz = oz - from.z;
        Box box = box(p, ox, oy, oz);
        List<Collider> near = new ArrayList<>();
        world.collect(new Box(box.minX() - 0.5D, box.minY() - 1.0D, box.minZ() - 0.5D, box.maxX() + 0.5D, box.maxY() + 0.5D, box.maxZ() + 0.5D), near);
        double vx = dx;
        double vy = dy;
        double vz = dz;
        if (trial != null && trial.collidedX && Math.abs(dx - trial.stepX) < 1.0E-4D) vx = 0.0D;
        if (trial != null && trial.collidedZ && Math.abs(dz - trial.stepZ) < 1.0E-4D) vz = 0.0D;
        r.onGround = supportedBy(near, box) && (trial == null || trial.moveY <= 0.0D || dy <= 0.0D);
        if (r.onGround || trial != null && trial.collidedY && Math.abs(dy - trial.stepY) < 1.0E-4D) vy = 0.0D;
        support(near, p, r, dx, dz);
        r.friction = trial != null ? trial.friction
            : from.onGround ? (float) world.surface(belowX(from), belowY(from.y), belowZ(from)).friction() : 1.0F;
        finish(world, p, r, vx, vy, vz);
        r.fallDistance = r.onGround ? 0.0D : from.fallDistance - Math.min(0.0D, dy);
        return r;
    }

    // ---------------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------------

    /** LocalPlayer.modifyInput: 0.98 scale, item use, sneaking, then the 1.21.5 square-to-circle correction. */
    static float[] moveVector(Input input, double sneakSpeed) {
        float x = input.strafe();
        float z = input.forward();
        if (x == 0.0F && z == 0.0F) return new float[] {0.0F, 0.0F};
        x *= 0.98F;
        z *= 0.98F;
        if (input.usingItem()) {
            x *= 0.2F;
            z *= 0.2F;
        }
        if (input.slow()) {
            float f = (float) sneakSpeed;
            x *= f;
            z *= f;
        }
        if (input.legacyDiagonal()) return new float[] {x, z};
        float length = (float) Math.sqrt(x * x + z * z);
        float nx = x * (1.0F / length);
        float nz = z * (1.0F / length);
        float ax = Math.abs(nx);
        float az = Math.abs(nz);
        float ratio = az > ax ? ax / az : az / ax;
        float scale = Math.min(length * (float) Math.sqrt(1.0F + ratio * ratio), 1.0F);
        return new float[] {nx * scale, nz * scale};
    }

    private static List<Input> candidates(boolean onGround, Profile profile) {
        return CANDIDATES[(onGround ? 1 : 0) | (profile.mayBeSneaking() ? 2 : 0) | (profile.mayBeUsingItem() ? 4 : 0)];
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Input>[] buildCandidates() {
        List<Input>[] sets = new List[8];
        for (int key = 0; key < 8; key++) {
            boolean ground = (key & 1) != 0;
            boolean sneak = (key & 2) != 0;
            boolean item = (key & 4) != 0;
            List<Input> list = new ArrayList<>();
            for (int forward : new int[] {1, 0, -1}) {
                for (int strafe : new int[] {0, 1, -1}) {
                    for (int sprint = 0; sprint < (forward > 0 ? 2 : 1); sprint++) { // vanilla stops sprinting without forward input
                        for (int mode = 0; mode < (sneak ? 3 : 1); mode++) {
                            boolean slow = mode > 0;
                            boolean edge = mode == 1;
                            for (int using = 0; using < (item ? 2 : 1); using++) {
                                for (int jump = 0; jump < (ground ? 2 : 1); jump++) {
                                    list.add(new Input(forward, strafe, sprint == 1, slow, edge, using == 1, jump == 1, false));
                                    if ((slow || using == 1) && forward != 0 && strafe != 0) {
                                        list.add(new Input(forward, strafe, sprint == 1, slow, edge, using == 1, jump == 1, true));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            sets[key] = List.copyOf(list);
        }
        return sets;
    }

    // ---------------------------------------------------------------------------------------------------
    // Collision (Entity.collide, Shapes.collide, maybeBackOffFromEdge)
    // ---------------------------------------------------------------------------------------------------

    private static double[] collide(List<Collider> near, Box box, double mx, double my, double mz, boolean onGround, double stepHeight) {
        if (mx == 0.0D && my == 0.0D && mz == 0.0D) return new double[] {0.0D, 0.0D, 0.0D};
        List<Collider> shapes = within(near, box.expandTowards(mx, my, mz));
        double[] c = collideWithShapes(mx, my, mz, box, shapes);
        boolean cx = mx != c[0];
        boolean cy = my != c[1];
        boolean cz = mz != c[2];
        if (stepHeight > 0.0D && (onGround || cy && my < 0.0D) && (cx || cz)) {
            Box base = onGround ? box : box.move(0.0D, c[1], 0.0D);
            Box area = base.expandTowards(mx, stepHeight, mz);
            if (!onGround) area = area.expandTowards(0.0D, -1.0E-5F, 0.0D);
            List<Collider> stepShapes = within(near, area);
            for (float height : stepHeights(base, stepShapes, (float) stepHeight, (float) c[1])) {
                double[] v = collideWithShapes(mx, height, mz, base, stepShapes);
                if (v[0] * v[0] + v[2] * v[2] > c[0] * c[0] + c[2] * c[2]) {
                    return new double[] {v[0], v[1] - (box.minY() - base.minY()), v[2]};
                }
            }
        }
        return c;
    }

    private static float[] stepHeights(Box base, List<Collider> shapes, float maxStep, float skip) {
        float[] heights = new float[shapes.size() * 2];
        int count = 0;
        for (Collider shape : shapes) {
            for (double coordinate : new double[] {shape.box().minY(), shape.box().maxY()}) {
                float height = (float) (coordinate - base.minY());
                if (height < 0.0F || height == skip || height > maxStep) continue;
                boolean seen = false;
                for (int i = 0; i < count; i++) seen |= heights[i] == height;
                if (!seen) heights[count++] = height;
            }
        }
        float[] result = Arrays.copyOf(heights, count);
        Arrays.sort(result);
        return result;
    }

    /** Y first, then the larger horizontal axis last (Direction.axisStepOrder). */
    private static double[] collideWithShapes(double mx, double my, double mz, Box box, List<Collider> shapes) {
        if (shapes.isEmpty()) return new double[] {mx, my, mz};
        double rx = 0.0D;
        double ry = 0.0D;
        double rz = 0.0D;
        if (my != 0.0D) {
            ry = collideAxis(1, box, shapes, my);
            box = box.move(0.0D, ry, 0.0D);
        }
        boolean zFirst = Math.abs(mx) < Math.abs(mz);
        if (zFirst && mz != 0.0D) {
            rz = collideAxis(2, box, shapes, mz);
            box = box.move(0.0D, 0.0D, rz);
        }
        if (mx != 0.0D) {
            rx = collideAxis(0, box, shapes, mx);
            box = box.move(rx, 0.0D, 0.0D);
        }
        if (!zFirst && mz != 0.0D) rz = collideAxis(2, box, shapes, mz);
        return new double[] {rx, ry, rz};
    }

    /** VoxelShape.collide for boxes: limit d so the box stops at the first shape ahead of it on this axis. */
    private static double collideAxis(int axis, Box box, List<Collider> shapes, double d) {
        if (Math.abs(d) < 1.0E-7D) return 0.0D;
        for (Collider collider : shapes) {
            Box s = collider.box();
            boolean overlaps = switch (axis) {
                case 0 -> s.maxY() > box.minY() + 1.0E-7D && s.minY() < box.maxY() - 1.0E-7D && s.maxZ() > box.minZ() + 1.0E-7D && s.minZ() < box.maxZ() - 1.0E-7D;
                case 1 -> s.maxX() > box.minX() + 1.0E-7D && s.minX() < box.maxX() - 1.0E-7D && s.maxZ() > box.minZ() + 1.0E-7D && s.minZ() < box.maxZ() - 1.0E-7D;
                default -> s.maxX() > box.minX() + 1.0E-7D && s.minX() < box.maxX() - 1.0E-7D && s.maxY() > box.minY() + 1.0E-7D && s.minY() < box.maxY() - 1.0E-7D;
            };
            if (!overlaps) continue;
            double shapeMin = axis == 0 ? s.minX() : axis == 1 ? s.minY() : s.minZ();
            double shapeMax = axis == 0 ? s.maxX() : axis == 1 ? s.maxY() : s.maxZ();
            double boxMin = axis == 0 ? box.minX() : axis == 1 ? box.minY() : box.minZ();
            double boxMax = axis == 0 ? box.maxX() : axis == 1 ? box.maxY() : box.maxZ();
            if (d > 0.0D && shapeMin > boxMax - 1.0E-7D) d = Math.min(d, shapeMin - boxMax);
            else if (d < 0.0D && shapeMax <= boxMin + 1.0E-7D) d = Math.max(d, shapeMax - boxMin);
        }
        return Math.abs(d) < 1.0E-7D ? 0.0D : d;
    }

    private static boolean aboveGround(List<Collider> near, Box box, Body from, double stepHeight) {
        return from.onGround || from.fallDistance < stepHeight && !canFallAtLeast(near, box, 0.0D, 0.0D, stepHeight - from.fallDistance);
    }

    private static double[] backOffFromEdge(List<Collider> near, Box box, double x, double z, double stepHeight) {
        double stepX = Math.signum(x) * 0.05D;
        double stepZ = Math.signum(z) * 0.05D;
        while (x != 0.0D && canFallAtLeast(near, box, x, 0.0D, stepHeight)) {
            if (Math.abs(x) <= 0.05D) {
                x = 0.0D;
                break;
            }
            x -= stepX;
        }
        while (z != 0.0D && canFallAtLeast(near, box, 0.0D, z, stepHeight)) {
            if (Math.abs(z) <= 0.05D) {
                z = 0.0D;
                break;
            }
            z -= stepZ;
        }
        while (x != 0.0D && z != 0.0D && canFallAtLeast(near, box, x, z, stepHeight)) {
            if (Math.abs(x) <= 0.05D) x = 0.0D;
            else x -= stepX;
            if (Math.abs(z) <= 0.05D) z = 0.0D;
            else z -= stepZ;
        }
        return new double[] {x, z};
    }

    private static boolean canFallAtLeast(List<Collider> near, Box box, double dx, double dz, double distance) {
        Box below = new Box(box.minX() + 1.0E-7D + dx, box.minY() - distance - 1.0E-7D, box.minZ() + 1.0E-7D + dz,
            box.maxX() - 1.0E-7D + dx, box.minY(), box.maxZ() - 1.0E-7D + dz);
        for (Collider collider : near) if (collider.box().intersects(below)) return false;
        return true;
    }

    // ---------------------------------------------------------------------------------------------------
    // Supporting block (Entity.checkSupportingBlock and getBlockPosBelowThatAffectsMyMovement)
    // ---------------------------------------------------------------------------------------------------

    private static boolean supportedBy(List<Collider> near, Box box) {
        Box slab = new Box(box.minX(), box.minY() - 1.0E-6D, box.minZ(), box.maxX(), box.minY(), box.maxZ());
        for (Collider collider : near) if (collider.box().intersects(slab)) return true;
        return false;
    }

    /** The block a grounded player stands on: the touching block whose centre is nearest the player's feet. */
    private static void support(List<Collider> near, Profile p, Body body, double movedX, double movedZ) {
        body.supported = false;
        if (!body.onGround) return;
        Box box = box(p, body.x, body.y, body.z);
        Box slab = new Box(box.minX(), box.minY() - 1.0E-6D, box.minZ(), box.maxX(), box.minY(), box.maxZ());
        if (!nearestSupport(near, slab, body)) nearestSupport(near, slab.move(-movedX, 0.0D, -movedZ), body);
    }

    private static boolean nearestSupport(List<Collider> near, Box slab, Body body) {
        double best = Double.MAX_VALUE;
        for (Collider collider : near) {
            if (!collider.box().intersects(slab)) continue;
            double cx = collider.x() + 0.5D - body.x;
            double cy = collider.y() + 0.5D - body.y;
            double cz = collider.z() + 0.5D - body.z;
            double distance = cx * cx + cy * cy + cz * cz;
            if (distance < best) {
                best = distance;
                body.supported = true;
                body.supportX = collider.x();
                body.supportY = collider.y();
                body.supportZ = collider.z();
            }
        }
        return body.supported;
    }

    private static int belowX(Body body) {
        return body.supported ? body.supportX : floor(body.x);
    }

    private static int belowY(double y) {
        return floor(y - 0.500001F);
    }

    private static int belowZ(Body body) {
        return body.supported ? body.supportZ : floor(body.z);
    }

    private static double jumpFactor(BlockView world, Body body) {
        double feet = world.surface(floor(body.x), floor(body.y), floor(body.z)).jumpFactor();
        return feet == 1.0D ? world.surface(belowX(body), belowY(body.y), belowZ(body)).jumpFactor() : feet;
    }

    // ---------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------

    private static Box box(Profile p, double x, double y, double z) {
        double half = p.width() / 2.0D;
        return new Box(x - half, y, z - half, x + half, y + p.height(), z + half);
    }

    /** Everything any candidate could touch this tick, fetched once and filtered per query. */
    private static Box searchArea(Profile p, Body from) {
        Box box = box(p, from.x, from.y, from.z);
        // A tick adds at most ~0.4 of input acceleration and 0.2 of sprint-jump boost to the carried velocity.
        double horizontal = Math.max(Math.abs(from.vx), Math.abs(from.vz)) + 1.0D;
        double up = Math.max(from.vy, p.jumpStrength() + p.jumpBoost()) + p.stepHeight() + 0.5D;
        double down = Math.abs(from.vy) + p.stepHeight() + 0.5D;
        return new Box(box.minX() - horizontal, box.minY() - down, box.minZ() - horizontal, box.maxX() + horizontal, box.maxY() + up, box.maxZ() + horizontal);
    }

    private static List<Collider> within(List<Collider> near, Box area) {
        List<Collider> out = new ArrayList<>();
        for (Collider collider : near) if (collider.box().intersects(area)) out.add(collider);
        return out;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    /** Mth.sin: vanilla's 65536-entry lookup table, so rotations match the client to the bit. */
    static float sin(double radians) {
        return SIN[(int) ((long) (radians * 10430.378350470453D) & 65535L)];
    }

    static float cos(double radians) {
        return SIN[(int) ((long) (radians * 10430.378350470453D + 16384.0D) & 65535L)];
    }
}

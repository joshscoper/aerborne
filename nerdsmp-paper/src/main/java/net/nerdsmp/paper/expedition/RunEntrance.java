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
 * How bosses arrive. A boss is held out of sight, untouchable, brought in with its effects and let loose
 * once every camera is back; a second attempt takes a second and doesn't wait.
 *
 * <p>{@code REANIMATE} builds the body in front of the camera: a skeleton's bones lie scattered in the
 * room from the start, the room's soul fires are drawn into them, and each bone flies to its place from
 * the feet up. Fire kindles in the ribs, the bones blacken, the skull lifts, and only then does the
 * real boss take the figure's place. {@code SUMMON} is a ritual: the room's braziers catch one by one, their
 * flames run along the floor to a turning circle, and the boss rises in a column of fire and slams down.
 * {@code DESCEND} lowers the boss on a single thread from high overhead. {@code BLOOM} sends fire from the
 * room's sources to a cage of roots that grows up around the spot and bursts open with the boss inside.
 * The rest are acts staged in {@link RunActs}, each with props of its own.
 */
final class RunEntrance {
    interface Host {
        World world();
        boolean live();
        boolean cutsceneRunning();
        void log(String line);
        /** The boss is free to fight. */
        void loose(Room room, String id, LivingEntity boss);
        /** The body hung on this boss, if it has one. */
        MoltenBody body(LivingEntity boss);
    }

    private static final Set<EntranceStyle> ACTS = Set.of(EntranceStyle.HOIST, EntranceStyle.POWDER, EntranceStyle.ERUPT, EntranceStyle.FORGED, EntranceStyle.COLOSSUS);

    private static final double DEPTH = 3, BODY = 3.6;
    private static final int LOOSE_BONES = 14, WISP_TICKS = 24, FLIGHT_TICKS = 8, CIRCLE_PLATES = 18;
    private static final double SNUFFED = .28, ASSEMBLED = .72, KINDLED = .78, RAISED = .9;
    private static final double RING_LIT = .35, CIRCLE_DONE = .62, BOSS_SEEN = .68, BOSS_RAISED = .94, CIRCLE_RADIUS = 4.6;
    private static final double THREAD_SEEN = .2, THREAD_HEIGHT = 15;
    private static final double VEINS_LIT = .3, CAGE_GROWN = .75, CAGE_OPENS = .86, CAGE_RADIUS = 3.2, CAGE_ROD = 1.25;
    private static final int CAGE_RIBS = 9, CAGE_SEGMENTS = 6;
    private static final List<Material> DEBRIS = List.of(Material.BONE, Material.BONE, Material.BONE_BLOCK, Material.SKELETON_SKULL);

    /** Each bone of a standing body, feet first: across, up, ahead, then width, height, depth, all as shares of its height. */
    private static final double[][] SKELETON = {
        {-.09, .12, 0, .05, .24, .05}, {.09, .12, 0, .05, .24, .05},
        {-.09, .33, 0, .055, .2, .055}, {.09, .33, 0, .055, .2, .055},
        {0, .44, 0, .26, .05, .09},
        {0, .6, 0, .05, .3, .05},
        {0, .55, .01, .2, .025, .12}, {0, .6, .01, .24, .025, .13}, {0, .65, .01, .28, .025, .14}, {0, .7, .01, .3, .025, .14},
        {0, .76, 0, .4, .04, .07},
        {-.21, .66, 0, .045, .2, .045}, {.21, .66, 0, .045, .2, .045},
        {-.21, .47, .03, .045, .2, .045}, {.21, .47, .03, .045, .2, .045},
    };
    private static final double SKULL_HEIGHT = .89, SKULL_SIZE = .38, HEART_HEIGHT = .62;

    private record Wisp(BlockDisplay flame, Location from, int born) { }

    /** One bone: its display, how it lies and how it stands, and the tick it leaves the floor. */
    private static final class Bone {
        final Display display;
        final double[] shape;
        final Transformation rest;
        Transformation set;
        int start;

        Bone(Display display, double[] shape, Transformation rest) {
            this.display = display;
            this.shape = shape;
            this.rest = rest;
        }
    }

    /** A body lying in pieces, and the loose debris around it that never gets up. */
    private static final class Figure {
        final Location root;
        final List<Bone> bones = new ArrayList<>();
        final List<Display> extras = new ArrayList<>();
        Bone skull;
        Quaternionf facing;
        double height = BODY;
        Material body = Material.BONE_BLOCK;

        Figure(Location root) { this.root = root; }

        void remove() {
            bones.forEach(bone -> bone.display.remove());
            extras.forEach(Display::remove);
        }
    }

    private final Plugin plugin;
    private final Host host;
    private final Set<String> entered = new HashSet<>();
    /** Bosses held for an entrance whose names show once they're loose. */
    private final Set<java.util.UUID> named = new HashSet<>();
    private final Map<String, Figure> figures = new HashMap<>();
    /** The flames, plates and columns of a ritual in progress, by room. */
    private final Map<String, List<Display>> rituals = new HashMap<>();
    private RunActs acts;

    RunEntrance(Plugin plugin, Host host) {
        this.plugin = plugin;
        this.host = host;
    }

    /** Scatters the bones, so they're lying in the room before anyone gets there. */
    void begin(List<Room> rooms) {
        for (Room room : rooms) {
            Entrance entrance = room.encounter().entrance();
            if (entrance == null || entrance.style() != EntranceStyle.REANIMATE || room.points().isEmpty()) continue;
            Point at = room.points().getFirst();
            Figure figure = new Figure(new Location(host.world(), at.x() + .5, at.y(), at.z() + .5));
            Material stone = entrance.block() == null ? null : Material.matchMaterial(entrance.block());
            if (stone != null) figure.body = stone;
            figure.facing = new Quaternionf().rotateY((float) Math.toRadians(-at.yaw()));
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (double[] shape : SKELETON) {
                Transformation rest = lying(shape, random);
                figure.bones.add(new Bone(bone(figure.root, rest, figure.body), shape, rest));
            }
            figure.skull = new Bone(item(figure.root, new ItemStack(stone == null ? Material.WITHER_SKELETON_SKULL : stone), restingSkull(random)), null, restingSkull(random));
            List<Material> scraps = stone == null ? DEBRIS : List.of(Material.COBBLESTONE, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS);
            for (int index = 0; index < LOOSE_BONES; index++) figure.extras.add(item(figure.root, new ItemStack(scraps.get(index % scraps.size())), debris(random)));
            figures.put(room.id(), figure);
        }
    }

    private Transformation lying(double[] shape, ThreadLocalRandom random) {
        double angle = random.nextDouble(Math.TAU), radius = .4 + Math.sqrt(random.nextDouble()) * 1.7;
        Vector3f size = size(shape, BODY);
        Quaternionf turn = new Quaternionf().rotateY((float) random.nextDouble(Math.TAU));
        // Upright rods are laid on their side; anything already flat just turns.
        if (size.y > size.x) turn.rotateZ((float) Math.PI / 2);
        return centered(new Vector3f((float) (Math.cos(angle) * radius), .08F, (float) (Math.sin(angle) * radius)), turn, size);
    }

    private static Transformation restingSkull(ThreadLocalRandom random) {
        Quaternionf turn = new Quaternionf().rotateY((float) random.nextDouble(Math.TAU)).rotateX(.4F);
        float scale = (float) (BODY * SKULL_SIZE);
        return new Transformation(new Vector3f(.3F, .3F, -.3F), turn, new Vector3f(scale, scale, scale), new Quaternionf());
    }

    private static Transformation debris(ThreadLocalRandom random) {
        double angle = random.nextDouble(Math.TAU), radius = .5 + Math.sqrt(random.nextDouble()) * 2.3;
        float size = (float) random.nextDouble(.55, .9);
        return new Transformation(new Vector3f((float) (Math.cos(angle) * radius), (float) random.nextDouble(.08, .25), (float) (Math.sin(angle) * radius)),
            new Quaternionf().rotateY((float) random.nextDouble(Math.TAU)).rotateX((float) Math.PI / 2), new Vector3f(size, size, size), new Quaternionf());
    }

    private static Vector3f size(double[] shape, double height) {
        return new Vector3f((float) (shape[3] * height), (float) (shape[4] * height), (float) (shape[5] * height));
    }

    /** A display turns about its corner; this puts a box's middle at {@code center}. */
    private static Transformation centered(Vector3f center, Quaternionf turn, Vector3f size) {
        return new Transformation(new Vector3f(center).sub(new Vector3f(size).mul(.5F).rotate(turn)), new Quaternionf(turn), new Vector3f(size), new Quaternionf());
    }

    private BlockDisplay bone(Location root, Transformation rest, Material body) {
        return host.world().spawn(root, BlockDisplay.class, display -> {
            display.setBlock(body.createBlockData());
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(11, 11));
            display.setTransformation(rest);
        });
    }

    private ItemDisplay item(Location root, ItemStack item, Transformation rest) {
        return host.world().spawn(root, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(11, 11));
            display.setTransformation(rest);
        });
    }

    private static void move(Display display, Transformation to, int ticks) {
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(ticks);
        display.setTransformation(to);
    }

    /**
     * Hides a boss as it's created: without this it stands on its post, in plain sight, until {@link #enter}
     * can move it. It isn't sent to anyone at all, because an invisible mob still shows what it's wearing.
     */
    void hold(LivingEntity boss) {
        if (boss.isCustomNameVisible()) named.add(boss.getUniqueId());
        boss.setCustomNameVisible(false);
        boss.setVisibleByDefault(false);
    }

    /** Starts a boss's arrival; returns how many ticks it takes, at least. */
    int enter(Room room, String id, LivingEntity boss) {
        Entrance entrance = room.encounter().entrance();
        boolean first = entered.add(room.id());
        int delay = first ? entrance.delaySeconds() * 20 : 0, length = first ? entrance.seconds() * 20 : 20;
        Figure figure = first && entrance.style() == EntranceStyle.REANIMATE ? figures.get(room.id()) : null;
        boolean ritual = first && entrance.style() == EntranceStyle.SUMMON;
        boolean thread = first && entrance.style() == EntranceStyle.DESCEND, bloom = first && entrance.style() == EntranceStyle.BLOOM;
        boolean act = first && ACTS.contains(entrance.style());
        Location home = boss.getLocation();
        boolean showName = named.remove(boss.getUniqueId());
        boss.setInvulnerable(true);
        if (boss instanceof Mob mob) mob.setAI(false);
        boss.teleport(home.clone().subtract(0, DEPTH, 0));
        host.log(id + " entering");
        if (!first && entrance.style() == EntranceStyle.SUMMON) lightAll(entrance, true);
        if (figure != null) prepare(figure, boss, length);
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
                if (figure != null) reanimate(figure, entrance, tick, length, wisps);
                else if (ritual) summon(room, entrance, boss, home, tick, length, wisps);
                else if (thread) descend(room, boss, home, tick, length);
                else if (bloom) bloom(room, entrance, boss, home, tick, length, wisps);
                else if (act) act(room, entrance, boss, home, tick, length);
                else rise(boss, home, tick, length);
            }
            if (tick == length) {
                boss.setVisibleByDefault(true);
                boss.teleport(home);
                if (ritual) slam(home);
                else if (thread) touchdown(home);
                else if (!bloom && !act) arrive(home);
                clear(room, entrance);
            }
            if (tick < length || host.cutsceneRunning() && tick < length + 400) return;
            task.cancel();
            boss.setInvulnerable(false);
            boss.setCustomNameVisible(showName);
            if (boss instanceof Mob mob) mob.setAI(true);
            host.log(id + " loose");
            host.loose(room, id, boss);
        }, 0L, 1L);
        return delay + length;
    }

    private static double eased(double t) { return t * t * (3 - 2 * t); }

    /** One tick of an act. {@code sources} says where it starts: the lane of a cart, the mouth of a hearth, the spot in the slag. */
    private void act(Room room, Entrance entrance, LivingEntity boss, Location home, int tick, int length) {
        if (acts == null) acts = new RunActs(plugin, host.world());
        List<Display> set = rituals.computeIfAbsent(room.id(), ignored -> new ArrayList<>());
        List<Location> marks = new ArrayList<>();
        for (Point at : entrance.sources()) marks.add(new Location(host.world(), at.x() + .5, at.y(), at.z() + .5, at.yaw(), 0));
        switch (entrance.style()) {
            case HOIST -> acts.hoist(set, boss, home, tick, length);
            case POWDER -> acts.powder(set, boss, home, marks, tick, length);
            case ERUPT -> acts.erupt(set, boss, home, tick, length);
            case FORGED -> acts.forged(set, boss, home, marks.getFirst(), tick, length);
            case COLOSSUS -> acts.colossus(set, boss, host.body(boss), home, marks.getFirst(), tick, length);
            default -> { }
        }
    }

    private void rise(LivingEntity boss, Location home, int tick, int length) {
        double done = tick / (double) length;
        // Seen only once it's under the floor.
        if (tick == 1) boss.setVisibleByDefault(true);
        boss.teleport(home.clone().subtract(0, DEPTH * (1 - eased(done)), 0));
        Location floor = home.clone().add(0, .15, 0);
        Telegraph.shards(floor, 10, .7, Material.DEEPSLATE_BRICKS);
        host.world().spawnParticle(Particle.SOUL_FIRE_FLAME, floor, 6, .6, .1, .6, .02);
        if (tick % 12 == 0) host.world().playSound(home, Sound.BLOCK_DEEPSLATE_BREAK, 1F, .5F + (float) done * .4F);
    }

    // ---------------------------------------------------------------------------------------------
    // Reanimation
    // ---------------------------------------------------------------------------------------------

    /** Sizes the standing body to the boss, gives each bone its turn, and adds what the boss carries. */
    private void prepare(Figure figure, LivingEntity boss, int length) {
        figure.height = boss.getHeight();
        double height = figure.height;
        int count = figure.bones.size();
        for (int index = 0; index < count; index++) {
            Bone bone = figure.bones.get(index);
            Vector3f center = new Vector3f((float) (bone.shape[0] * height), (float) (bone.shape[1] * height), (float) (bone.shape[2] * height)).rotate(figure.facing);
            bone.set = centered(center, figure.facing, size(bone.shape, height));
            bone.start = (int) (length * (SNUFFED + (ASSEMBLED - SNUFFED - .08) * index / count));
        }
        float skull = (float) (height * SKULL_SIZE);
        Vector3f top = new Vector3f(0, (float) (height * SKULL_HEIGHT), (float) (height * .02)).rotate(figure.facing);
        // The head hangs until the fire reaches it.
        figure.skull.set = new Transformation(top, new Quaternionf(figure.facing).rotateX(.75F), new Vector3f(skull, skull, skull), new Quaternionf());
        figure.skull.start = (int) (length * (ASSEMBLED - .06));
        var gear = boss.getEquipment();
        if (gear == null) return;
        carried(figure, gear.getItemInMainHand(), -1, length);
        carried(figure, gear.getItemInOffHand(), 1, length);
    }

    /** A weapon or shield rises off the floor to the hand that holds it, last of all. */
    private void carried(Figure figure, ItemStack item, int side, int length) {
        if (item == null || item.getType().isAir()) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float size = (float) (figure.height * .34);
        Transformation rest = new Transformation(new Vector3f((float) random.nextDouble(-1.5, 1.5), .15F, (float) random.nextDouble(-1.5, 1.5)),
            new Quaternionf().rotateX((float) Math.PI / 2), new Vector3f(size, size, size), new Quaternionf());
        Bone held = new Bone(item(figure.root, item, rest), null, rest);
        Vector3f hand = new Vector3f((float) (side * .24 * figure.height), (float) (.38 * figure.height), (float) (.1 * figure.height)).rotate(figure.facing);
        held.set = new Transformation(hand, new Quaternionf(figure.facing), new Vector3f(size, size, size), new Quaternionf());
        held.start = (int) (length * ASSEMBLED);
        figure.bones.add(held);
    }

    private void reanimate(Figure figure, Entrance entrance, int tick, int length, List<Wisp> wisps) {
        World world = host.world();
        double done = tick / (double) length, height = figure.height;
        Location root = figure.root, heart = root.clone().add(0, height * HEART_HEIGHT, 0);
        drawFires(entrance, tick, (int) (length * SNUFFED), root.clone().add(0, .6, 0), wisps);
        flyWisps(wisps, tick, root.clone().add(0, .6, 0), () -> {
            world.spawnParticle(Particle.SOUL, root.clone().add(0, .5, 0), 16, .9, .2, .9, .04);
            world.playSound(root, Sound.PARTICLE_SOUL_ESCAPE, 1.6F, .7F);
            // The pile stirs as each flame lands.
            for (Display piece : figure.extras) if (piece.getTransformation() != null) nudge(piece);
        });
        List<Bone> all = new ArrayList<>(figure.bones);
        all.add(figure.skull);
        for (Bone bone : all) fly(bone, tick, root, height);
        if (done > SNUFFED && done < KINDLED && tick % 3 == 0) world.spawnParticle(Particle.SOUL_FIRE_FLAME, root.clone().add(0, height * done * .9, 0), 5, .35, .25, .35, .01);
        int kindle = (int) (length * KINDLED);
        if (tick == kindle) kindle(figure, heart);
        if (tick >= kindle) blacken(figure, tick - kindle, (int) (length * (RAISED - KINDLED)));
        if (tick == (int) (length * RAISED)) raiseHead(figure);
        if (tick > kindle) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, heart, 4, height * .08, height * .12, height * .08, .01);
            if (tick % 10 == 0) world.playSound(root, Sound.BLOCK_SOUL_SAND_BREAK, 1.2F, .6F);
        }
        if (tick % 18 == 0) world.playSound(root, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.2F, .6F + (float) done * .5F);
    }

    private static void nudge(Display piece) {
        Transformation now = piece.getTransformation();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vector3f shifted = new Vector3f(now.getTranslation()).add((float) random.nextDouble(-.12, .12), 0, (float) random.nextDouble(-.12, .12));
        move(piece, new Transformation(shifted, now.getLeftRotation(), now.getScale(), now.getRightRotation()), 4);
    }

    /** A bone hops off the pile, arcs to where it belongs, and clacks home. */
    private void fly(Bone bone, int tick, Location root, double height) {
        if (bone.set == null) return;
        if (tick == bone.start) {
            Vector3f middle = new Vector3f(bone.rest.getTranslation()).lerp(bone.set.getTranslation(), .5F).add(0, (float) (height * .22), 0);
            move(bone.display, new Transformation(middle, new Quaternionf(bone.rest.getLeftRotation()).slerp(bone.set.getLeftRotation(), .5F),
                new Vector3f(bone.rest.getScale()).lerp(bone.set.getScale(), .5F), new Quaternionf()), FLIGHT_TICKS);
            host.world().playSound(root, Sound.ENTITY_SKELETON_AMBIENT, .7F, 1.5F);
        } else if (tick == bone.start + FLIGHT_TICKS) move(bone.display, bone.set, FLIGHT_TICKS);
        else if (tick == bone.start + FLIGHT_TICKS * 2) {
            Location at = root.clone().add(bone.set.getTranslation().x, bone.set.getTranslation().y, bone.set.getTranslation().z);
            host.world().spawnParticle(Particle.SOUL, at, 4, .1, .1, .1, .02);
            host.world().playSound(root, Sound.BLOCK_BONE_BLOCK_PLACE, 1.3F, .6F + (float) (at.getY() - root.getY()) * .15F);
        }
    }

    /** A flame catches in the ribs, and two small lights behind the skull. */
    private void kindle(Figure figure, Location heart) {
        World world = host.world();
        double height = figure.height;
        BlockDisplay flame = world.spawn(figure.root, BlockDisplay.class, display -> {
            display.setBlock(Material.SOUL_FIRE.createBlockData());
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(centered(new Vector3f(0, (float) (height * HEART_HEIGHT), 0), new Quaternionf(), new Vector3f(.05F, .05F, .05F)));
        });
        float size = (float) (height * .3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (flame.isValid()) move(flame, centered(new Vector3f(0, (float) (height * HEART_HEIGHT), 0), new Quaternionf(), new Vector3f(size, size, size)), 14);
        }, 2L);
        figure.extras.add(flame);
        for (int side : new int[]{-1, 1}) {
            Vector3f eye = new Vector3f((float) (side * height * .04), (float) (height * (SKULL_HEIGHT + .005)), (float) (height * .1)).rotate(figure.facing);
            float pupil = (float) (height * .022);
            figure.extras.add(world.spawn(figure.root, BlockDisplay.class, display -> {
                display.setBlock(Material.LIGHT_BLUE_CONCRETE.createBlockData());
                display.setPersistent(false);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setTransformation(centered(eye, figure.facing, new Vector3f(pupil, pupil, pupil)));
            }));
        }
        world.spawnParticle(Particle.SOUL, heart, 30, .4, .5, .4, .05);
        world.playSound(heart, Sound.ITEM_FIRECHARGE_USE, 1.4F, .5F);
        world.playSound(heart, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.4F, .5F);
    }

    /** The fire climbs the body and the bones char black behind it, from the feet up. */
    private void blacken(Figure figure, int since, int over) {
        int count = figure.bones.size();
        for (int index = 0; index < count; index++) {
            if (since != index * over / count || !(figure.bones.get(index).display instanceof BlockDisplay rod)) continue;
            rod.setBlock((figure.body == Material.BONE_BLOCK ? Material.BLACKSTONE : Material.WARPED_WART_BLOCK).createBlockData());
            Location at = figure.root.clone().add(0, figure.bones.get(index).shape[1] * figure.height, 0);
            host.world().spawnParticle(Particle.SOUL_FIRE_FLAME, at, 8, .3, .1, .3, .02);
        }
    }

    private void raiseHead(Figure figure) {
        Transformation bowed = figure.skull.set;
        move(figure.skull.display, new Transformation(bowed.getTranslation(), figure.facing, bowed.getScale(), new Quaternionf()), 12);
        host.world().playSound(figure.root, Sound.ENTITY_WITHER_SKELETON_STEP, 1.6F, .5F);
        host.world().playSound(figure.root, Sound.ENTITY_WARDEN_HEARTBEAT, 1.8F, .7F);
    }

    private void arrive(Location home) {
        World world = host.world();
        Location body = home.clone().add(0, 1, 0);
        world.spawnParticle(Particle.SOUL, body, 45, .7, .9, .7, .06);
        world.spawnParticle(Particle.EXPLOSION, body, 2, .3, .3, .3, 0);
        world.playSound(home, Sound.ENTITY_WITHER_SKELETON_DEATH, 1.2F, .5F);
    }

    // ---------------------------------------------------------------------------------------------
    // Summoning
    // ---------------------------------------------------------------------------------------------

    private void summon(Room room, Entrance entrance, LivingEntity boss, Location home, int tick, int length, List<Wisp> wisps) {
        World world = host.world();
        double done = tick / (double) length;
        Location center = home.clone().subtract(0, .95, 0);
        List<Display> ritual = rituals.computeIfAbsent(room.id(), ignored -> new ArrayList<>());
        catchFires(entrance, tick, (int) (length * RING_LIT), center, wisps);
        flyWisps(wisps, tick, center, () -> {
            world.spawnParticle(Particle.LAVA, center, 6, .4, .1, .4, 0);
            world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 1.6F, .6F);
        });
        // The lit channels keep burning towards the middle.
        if (done > RING_LIT * .4 && done < BOSS_SEEN && tick % 6 == 0) for (Point fire : entrance.sources())
            Telegraph.line(new Location(world, fire.x() + .5, center.getY(), fire.z() + .5), center, Telegraph.FIRE);
        int circleFrom = (int) (length * RING_LIT), circleTo = (int) (length * CIRCLE_DONE);
        if (tick >= circleFrom && tick < circleTo && (tick - circleFrom) % Math.max(1, (circleTo - circleFrom) / CIRCLE_PLATES) == 0 && ritual.size() < CIRCLE_PLATES) {
            ritual.add(plate(center));
            world.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, .9F, .6F + ritual.size() * .04F);
        }
        if (tick % 4 == 0) turn(ritual, center, tick);
        if (tick >= circleFrom && tick % 5 == 0) {
            world.spawnParticle(Particle.FLAME, center.clone().add(0, .3, 0), 10, CIRCLE_RADIUS * .6, .05, CIRCLE_RADIUS * .6, .02);
            world.spawnParticle(Particle.LAVA, center.clone().add(0, .3, 0), 2, CIRCLE_RADIUS * .5, .05, CIRCLE_RADIUS * .5, 0);
        }
        if (tick == circleFrom) world.playSound(center, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.6F, .6F);
        int seen = (int) (length * BOSS_SEEN);
        if (tick == (int) (length * CIRCLE_DONE)) column(ritual, center);
        if (tick == seen) {
            boss.setVisibleByDefault(true);
            world.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1.1F, .7F);
        }
        if (tick >= seen) {
            double raised = Math.min(1, (done - BOSS_SEEN) / (BOSS_RAISED - BOSS_SEEN));
            Location held = home.clone().add(0, -1.6 + eased(raised) * 4.2, 0);
            held.setYaw((float) (home.getYaw() + (1 - eased(raised)) * 540));
            boss.teleport(held);
            world.spawnParticle(Particle.FLAME, held.clone().add(0, 1, 0), 14, .6, 1, .6, .04);
            world.spawnParticle(Particle.LARGE_SMOKE, held.clone().add(0, 3, 0), 3, .6, .6, .6, .02);
        }
        if (tick > (int) (length * CIRCLE_DONE) && tick % 8 == 0) world.playSound(center, Sound.BLOCK_FIRE_AMBIENT, 2F, .5F);
    }

    private BlockDisplay plate(Location center) {
        return host.world().spawn(center, BlockDisplay.class, display -> {
            display.setBlock(Material.MAGMA_BLOCK.createBlockData());
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(centered(new Vector3f(), new Quaternionf(), new Vector3f(0, 0, 0)));
        });
    }

    /** The circle's plates turn slowly around its middle. */
    private void turn(List<Display> plates, Location center, int tick) {
        Vector3f size = new Vector3f(.95F, .06F, .4F);
        int index = 0;
        for (Display plate : plates) {
            if (!(plate instanceof BlockDisplay) || plate.getScoreboardTags().contains("column")) continue;
            double angle = index++ * Math.TAU / CIRCLE_PLATES + tick * .012;
            Quaternionf facing = new Quaternionf().rotateY((float) -angle + (float) Math.PI / 2);
            move(plate, centered(new Vector3f((float) (Math.cos(angle) * CIRCLE_RADIUS), .05F, (float) (Math.sin(angle) * CIRCLE_RADIUS)), facing, size), 4);
        }
    }

    /** Fire stretched up to the rafters, growing out of the tomb. */
    private void column(List<Display> ritual, Location center) {
        World world = host.world();
        BlockDisplay flame = world.spawn(center, BlockDisplay.class, display -> {
            display.setBlock(Material.FIRE.createBlockData());
            display.setPersistent(false);
            display.addScoreboardTag("column");
            display.setBrightness(new Display.Brightness(15, 15));
            display.setTransformation(centered(new Vector3f(), new Quaternionf(), new Vector3f(2.4F, .1F, 2.4F)));
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (flame.isValid()) move(flame, new Transformation(new Vector3f(-1.5F, 0, -1.5F), new Quaternionf(), new Vector3f(3F, 13F, 3F), new Quaternionf()), 16);
        }, 2L);
        ritual.add(flame);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1, 0), 3, 1, .4, 1, 0);
        world.playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.6F, .5F);
        world.playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, .9F, .6F);
    }

    private void slam(Location home) {
        World world = host.world();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, home, 1);
        world.spawnParticle(Particle.FLAME, home.clone().add(0, .4, 0), 160, 3.5, .3, 3.5, .18);
        world.playSound(home, Sound.ENTITY_GENERIC_EXPLODE, 1.6F, .5F);
        world.playSound(home, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.2F, .5F);
        for (int ring = 1; ring <= 5; ring++) {
            double radius = ring * 2.2;
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (host.live()) Telegraph.ring(home, radius, Telegraph.FIRE); }, ring * 2L);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Descent
    // ---------------------------------------------------------------------------------------------

    private void descend(Room room, LivingEntity boss, Location home, int tick, int length) {
        World world = host.world();
        double done = tick / (double) length;
        List<Display> strand = rituals.computeIfAbsent(room.id(), ignored -> new ArrayList<>());
        double lowered = eased(Math.clamp((done - THREAD_SEEN) / (1 - THREAD_SEEN), 0D, 1D));
        double height = THREAD_HEIGHT * (1 - lowered);
        if (strand.isEmpty()) strand.add(world.spawn(home, BlockDisplay.class, display -> {
            display.setBlock(Material.WHITE_CONCRETE.createBlockData());
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(13, 13));
        }));
        float from = (float) (height + boss.getHeight() * .7);
        move(strand.getFirst(), new Transformation(new Vector3f(-.05F, from, -.05F), new Quaternionf(), new Vector3f(.1F, (float) THREAD_HEIGHT + 12 - from, .1F), new Quaternionf()), 1);
        if (tick == (int) (length * THREAD_SEEN)) {
            boss.setVisibleByDefault(true);
            world.playSound(home, Sound.ENTITY_SPIDER_AMBIENT, 2F, .5F);
        }
        Location at = home.clone().add(0, height, 0);
        at.setYaw((float) (home.getYaw() + (1 - lowered) * 720));
        boss.teleport(at);
        if (tick % 2 == 0) world.spawnParticle(Particle.ITEM_COBWEB, at.clone().add(0, boss.getHeight(), 0), 3, .2, .4, .2, .02);
        if (tick % 6 == 0) world.playSound(at, Sound.BLOCK_WOOL_STEP, 1.4F, .6F);
        if (tick % 14 == 0) world.playSound(at, Sound.ENTITY_SPIDER_STEP, 1.4F, .6F);
    }

    private void touchdown(Location home) {
        World world = host.world();
        Telegraph.shards(home.clone().add(0, .4, 0), 60, 2.4, Material.WHITE_WOOL);
        world.spawnParticle(Particle.EXPLOSION, home.clone().add(0, .5, 0), 2, .6, .2, .6, 0);
        world.playSound(home, Sound.ENTITY_GENERIC_EXPLODE, 1F, .7F);
        world.playSound(home, Sound.ENTITY_SPIDER_DEATH, 1.8F, .5F);
    }

    // ---------------------------------------------------------------------------------------------
    // Bloom
    // ---------------------------------------------------------------------------------------------

    private void bloom(Room room, Entrance entrance, LivingEntity boss, Location home, int tick, int length, List<Wisp> wisps) {
        World world = host.world();
        double done = tick / (double) length;
        Location center = home.clone().add(0, .5, 0);
        List<Display> cage = rituals.computeIfAbsent(room.id(), ignored -> new ArrayList<>());
        catchFires(entrance, tick, (int) (length * VEINS_LIT), center, wisps);
        flyWisps(wisps, tick, center, () -> {
            world.spawnParticle(Particle.LAVA, center, 8, .5, .2, .5, 0);
            world.playSound(center, Sound.BLOCK_ROOTED_DIRT_BREAK, 1.8F, .5F);
        });
        int total = CAGE_RIBS * CAGE_SEGMENTS, grown = (int) (total * Math.clamp((done - VEINS_LIT * .7) / (CAGE_GROWN - VEINS_LIT * .7), 0D, 1D));
        while (cage.size() < grown) {
            cage.add(rib(home, cage.size()));
            if (cage.size() % 3 == 0) world.playSound(home, Sound.BLOCK_ROOTS_PLACE, 1.8F, .6F + cage.size() * .02F);
        }
        if (done > CAGE_GROWN && tick % 4 == 0) {
            world.spawnParticle(Particle.FLAME, home.clone().add(0, 2, 0), 14, 1, 1.4, 1, .03);
            world.playSound(home, Sound.ENTITY_WARDEN_HEARTBEAT, 2F, .6F + (float) done * .5F);
        }
        if (tick != (int) (length * CAGE_OPENS)) return;
        boss.setVisibleByDefault(true);
        boss.teleport(home);
        for (int index = 0; index < cage.size(); index++) {
            double angle = (index % CAGE_RIBS) * Math.TAU / CAGE_RIBS;
            Transformation now = cage.get(index).getTransformation();
            Vector3f flung = new Vector3f(now.getTranslation()).add((float) Math.cos(angle) * 7, -1.5F, (float) Math.sin(angle) * 7);
            move(cage.get(index), new Transformation(flung, new Quaternionf(now.getLeftRotation()).rotateX(1.2F), new Vector3f(now.getScale()).mul(.3F), new Quaternionf()), 8);
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, home.clone().add(0, 1, 0), 1);
        Telegraph.shards(home.clone().add(0, 1, 0), 120, 3.5, Material.DARK_OAK_LEAVES);
        world.playSound(home, Sound.BLOCK_WOOD_BREAK, 2F, .5F);
        world.playSound(home, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2F, .6F);
    }

    /** One length of the cage: ribs curve from the floor to a point above the spot, lowest first. */
    private BlockDisplay rib(Location home, int index) {
        int segment = index / CAGE_RIBS;
        double angle = (index % CAGE_RIBS) * Math.TAU / CAGE_RIBS + segment * .12, rise = (segment + .5) / CAGE_SEGMENTS;
        double radius = CAGE_RADIUS * Math.cos(rise * Math.PI / 2) + .25, height = Math.sin(rise * Math.PI / 2) * CAGE_ROD * CAGE_SEGMENTS * .85 + .2;
        Vector3f middle = new Vector3f((float) (Math.cos(angle) * radius), (float) height, (float) (Math.sin(angle) * radius));
        Quaternionf lean = new Quaternionf().rotateY((float) -angle).rotateZ((float) (rise * Math.PI / 2));
        Vector3f full = new Vector3f(.5F, (float) CAGE_ROD, .5F);
        BlockDisplay rod = host.world().spawn(home, BlockDisplay.class, display -> {
            display.setBlock((index % 7 == 3 ? Material.SHROOMLIGHT : Material.DARK_OAK_LOG).createBlockData());
            display.setPersistent(false);
            display.setBrightness(new Display.Brightness(10, 10));
            display.setTransformation(centered(middle, lean, new Vector3f(.05F, .05F, .05F)));
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (rod.isValid()) move(rod, centered(middle, lean, full), 4); }, 2L);
        return rod;
    }

    // ---------------------------------------------------------------------------------------------
    // Fires
    // ---------------------------------------------------------------------------------------------

    /** The room's fires go out one by one, and their flames fly to {@code to}. */
    private void drawFires(Entrance entrance, int tick, int over, Location to, List<Wisp> wisps) {
        World world = host.world();
        List<Point> fires = entrance.sources();
        for (int index = 0; index < fires.size(); index++) {
            if (tick != index * over / fires.size()) continue;
            Block fire = world.getBlockAt(fires.get(index).x(), fires.get(index).y(), fires.get(index).z());
            light(fire, false);
            Location from = fire.getLocation().add(.5, .5, .5);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, from, 26, .2, .4, .2, .06);
            world.playSound(from, Sound.BLOCK_FIRE_EXTINGUISH, 1.2F, .6F);
            wisps.add(new Wisp(flame(from, Material.SOUL_FIRE), from, tick));
        }
    }

    /** The room's fires catch one by one, and a flame runs from each to {@code to}. */
    private void catchFires(Entrance entrance, int tick, int over, Location to, List<Wisp> wisps) {
        World world = host.world();
        List<Point> fires = entrance.sources();
        for (int index = 0; index < fires.size(); index++) {
            if (tick != index * over / fires.size()) continue;
            Block fire = world.getBlockAt(fires.get(index).x(), fires.get(index).y(), fires.get(index).z());
            light(fire, true);
            Location from = fire.getLocation().add(.5, .5, .5);
            world.spawnParticle(Particle.FLAME, from, 40, .3, .5, .3, .08);
            world.spawnParticle(Particle.LAVA, from, 6, .2, .2, .2, 0);
            world.playSound(from, Sound.ITEM_FIRECHARGE_USE, 1.4F, .7F);
            wisps.add(new Wisp(flame(from, Material.FIRE), from, tick));
        }
    }

    private BlockDisplay flame(Location from, Material fire) {
        return host.world().spawn(from, BlockDisplay.class, flame -> {
            flame.setBlock(fire.createBlockData());
            flame.setPersistent(false);
            flame.setTeleportDuration(1);
            flame.setBrightness(new Display.Brightness(15, 15));
            flame.setTransformation(new Transformation(new Vector3f(-.35F, -.35F, -.35F), new Quaternionf(), new Vector3f(.7F, .7F, .7F), new Quaternionf()));
        });
    }

    private void flyWisps(List<Wisp> wisps, int tick, Location to, Runnable landed) {
        World world = host.world();
        wisps.removeIf(wisp -> {
            double flown = (tick - wisp.born()) / (double) WISP_TICKS;
            if (flown >= 1) {
                wisp.flame().remove();
                landed.run();
                return true;
            }
            Location at = wisp.from().clone().add(to.toVector().subtract(wisp.from().toVector()).multiply(eased(flown)));
            at.add(0, Math.sin(flown * Math.PI) * 2.4, 0);
            wisp.flame().teleport(at);
            world.spawnParticle(wisp.flame().getBlock().getMaterial() == Material.FIRE ? Particle.FLAME : Particle.SOUL_FIRE_FLAME, at, 4, .1, .1, .1, .01);
            return false;
        });
    }

    /** Whatever the entrance built is gone. Drawn fires come back; a ritual's fires stay lit for the fight. */
    private void clear(Room room, Entrance entrance) {
        List<Display> ritual = rituals.remove(room.id());
        if (ritual != null) ritual.forEach(Display::remove);
        Figure figure = figures.remove(room.id());
        if (figure == null) return;
        figure.remove();
        lightAll(entrance, true);
        for (Point fire : entrance.sources()) host.world().spawnParticle(Particle.SOUL_FIRE_FLAME, new Location(host.world(), fire.x() + .5, fire.y() + .6, fire.z() + .5), 30, .2, .5, .2, .08);
        if (!entrance.sources().isEmpty()) {
            Point first = entrance.sources().getFirst();
            host.world().playSound(new Location(host.world(), first.x(), first.y(), first.z()), Sound.ITEM_FIRECHARGE_USE, 1.4F, .6F);
        }
    }

    private void lightAll(Entrance entrance, boolean on) {
        for (Point fire : entrance.sources()) light(host.world().getBlockAt(fire.x(), fire.y(), fire.z()), on);
    }

    /** A wipe: a ritual's fires die with the fight, and a second attempt lights them again. */
    void reset(Room room) {
        Entrance entrance = room.encounter().entrance();
        if (entrance != null && entrance.style() == EntranceStyle.SUMMON) lightAll(entrance, false);
    }

    private static void light(Block block, boolean on) {
        if (!(block.getBlockData() instanceof Lightable lightable)) return;
        lightable.setLit(on);
        block.setBlockData(lightable, false);
    }

    void close() {
        figures.values().forEach(Figure::remove);
        figures.clear();
        rituals.values().forEach(ritual -> ritual.forEach(Display::remove));
        rituals.clear();
    }
}

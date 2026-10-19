package net.nerdsmp.paper.anticheat;

import io.papermc.paper.event.entity.EntityKnockbackEvent;
import net.kyori.adventure.text.Component;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.anticheat.ml.CombatWindow;
import net.nerdsmp.paper.anticheat.ml.ModelService;
import net.nerdsmp.paper.anticheat.packet.ClientPacket;
import net.nerdsmp.paper.anticheat.packet.PacketTap;
import net.nerdsmp.paper.anticheat.physics.ClickProfile;
import net.nerdsmp.paper.anticheat.physics.HitboxMath;
import net.nerdsmp.paper.anticheat.physics.MotionModel;
import net.nerdsmp.paper.anticheat.physics.MovementSimulator;
import net.nerdsmp.paper.anticheat.physics.TimerWindow;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Watchdog anti-cheat hub. Three layers feed one evidence pipeline:
 * <ul>
 *   <li>Packet checks on each player's Netty thread (timer, protocol violations, click rhythm, combat telemetry);</li>
 *   <li>World checks on the main thread (vanilla-physics movement prediction, lag-compensated reach and hitbox
 *       ray tests, mining speed);</li>
 *   <li>The combat model, which scores whole fights and can act on its own once validated.</li>
 * </ul>
 * Every rule check accumulates a per-player buffer that decays on clean samples, so single odd ticks caused by lag
 * or edge cases never flag; only repeated, physically impossible behaviour does.
 */
public final class WatchdogService implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final File file;
    private final boolean autonomous;
    private final double alertScore;
    private final double setbackScore;
    private final double kickScore;
    private final double decayPerSecond;
    private final boolean simulateMovement;
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();
    private final Map<UUID, PacketState> packets = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<HitboxMath.Box>> boxHistory = new HashMap<>();
    /** Main thread only: world -> block key -> server tick it last changed, so moves near fresh changes aren't judged. */
    private final Map<UUID, Map<Long, Integer>> recentBlocks = new HashMap<>();
    private final ConcurrentLinkedQueue<PendingFlag> pendingFlags = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Map.Entry<UUID, CombatWindow>> finishedWindows = new ConcurrentLinkedQueue<>();
    private final List<Evidence> evidence = new ArrayList<>();
    private final ModelService model;
    private PacketTap tap;
    private BukkitTask ticker;
    private boolean evidenceDirty;

    public WatchdogService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "watchdog.yml");
        autonomous = plugin.getConfig().getString("anti-cheat.mode", "autonomous").equalsIgnoreCase("autonomous");
        alertScore = plugin.getConfig().getDouble("anti-cheat.alert-score", 12.0D);
        setbackScore = plugin.getConfig().getDouble("anti-cheat.setback-score", 24.0D);
        kickScore = plugin.getConfig().getDouble("anti-cheat.kick-score", 50.0D);
        decayPerSecond = plugin.getConfig().getDouble("anti-cheat.decay-per-second", 0.10D);
        simulateMovement = plugin.getConfig().getBoolean("anti-cheat.movement-simulation", true);
        model = new ModelService(plugin);
        load();
    }

    /** Called once listeners are registered: opens the packet layer, the model and the per-tick task. */
    public void start() {
        tap = new PacketTap(plugin, this::receive);
        Bukkit.getPluginManager().registerEvents(tap, plugin);
        for (Player player : Bukkit.getOnlinePlayers()) track(player);
        tap.start();
        model.start();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public ModelService model() {
        return model;
    }

    // ===================================================================================================
    // Packet layer (runs on each player's Netty thread)
    // ===================================================================================================

    private void receive(UUID playerId, ClientPacket packet) {
        PacketState state = packets.get(playerId);
        if (state == null || state.bypass) return;
        switch (packet) {
            case ClientPacket.TickEnd tick -> onTickEnd(playerId, state, tick.nanos());
            case ClientPacket.Move move -> onMovePacket(playerId, state, move);
            case ClientPacket.Attack attack -> onAttackPacket(playerId, state, attack);
            case ClientPacket.Swing swing -> onSwing(playerId, state);
            case ClientPacket.Dig dig -> onDig(state, dig);
        }
    }

    private void onTickEnd(UUID id, PacketState state, long nanos) {
        state.ticks++;
        state.ticksSincePosition++;
        if (state.attackAwaitingSwing) {
            // Vanilla always sends the arm swing in the same tick as the attack.
            state.attackAwaitingSwing = false;
            CombatWindow window = state.window.get();
            if (window != null) window.missingSwing();
            state.swingBuffer += 1.0D;
            if (state.swingBuffer >= 3.0D) {
                state.swingBuffer = 1.5D;
                queue(id, Check.BAD_PACKETS, 6.0D, "attacked without swinging an arm (repeated)");
            }
        }
        if (nanos - state.joinedNanos < 5_000_000_000L) return;
        TimerWindow.Reading reading = state.timerLong.tick(nanos);
        if (reading != null) {
            if (reading.violation()) {
                state.timerBuffer += 1.0D;
                if (state.timerBuffer >= 2.0D) {
                    state.timerBuffer = 1.0D;
                    queue(id, Check.TIMER, 8.0D, String.format(Locale.ROOT, "game clock ran at %.1f%% speed for 10 s (%d ms ahead)",
                        reading.speed() * 100, reading.earlyMillis()));
                }
            } else state.timerBuffer = Math.max(0.0D, state.timerBuffer - 0.5D);
        }
        TimerWindow.Reading burst = state.timerShort.tick(nanos);
        if (burst != null && burst.violation()) {
            queue(id, Check.TIMER, 10.0D, String.format(Locale.ROOT, "game clock ran at %.0f%% speed for 3 s", burst.speed() * 100));
        }
    }

    private void onMovePacket(UUID id, PacketState state, ClientPacket.Move move) {
        if (move.hasPos()) {
            if (!Double.isFinite(move.x()) || !Double.isFinite(move.y()) || !Double.isFinite(move.z())
                || Math.abs(move.x()) > 3.0E7D || Math.abs(move.z()) > 3.0E7D || Math.abs(move.y()) > 2.0E7D) {
                queue(id, Check.BAD_PACKETS, 20.0D, "sent an impossible position");
            }
            // The yaw the client moved with this tick: the movement simulation rotates input by it.
            double yaw = move.hasRot() ? move.yaw() : state.hasRotation ? state.lastYaw : Double.NaN;
            state.positions.add(new double[] {move.x(), move.y(), move.z(), Math.max(1, state.ticksSincePosition), yaw});
            state.ticksSincePosition = 0;
            while (state.positions.size() > 100) state.positions.poll();
        }
        if (move.hasRot()) {
            if (!Float.isFinite(move.pitch()) || Math.abs(move.pitch()) > 90.0001F) {
                queue(id, Check.BAD_PACKETS, 20.0D, String.format(Locale.ROOT, "looked %.1f degrees up or down; the game limit is 90", move.pitch()));
            }
            if (state.hasRotation && state.ticks - state.lastAttackTick <= 40) {
                CombatWindow window = state.window.get();
                if (window != null) window.rotation(HitboxMath.wrap(move.yaw() - state.lastYaw), move.pitch() - state.lastPitch);
            }
            state.lastYaw = move.yaw();
            state.lastPitch = move.pitch();
            state.hasRotation = true;
        }
    }

    private void onAttackPacket(UUID id, PacketState state, ClientPacket.Attack attack) {
        if (attack.entityId() == state.entityId) queue(id, Check.BAD_PACKETS, 20.0D, "attacked itself");
        state.attackAwaitingSwing = true;
        state.lastAttackTick = state.ticks;
        CombatWindow window = state.window.get();
        if (window == null || window.closed(attack.nanos())) {
            CombatWindow fresh = new CombatWindow(attack.nanos());
            if (state.window.compareAndSet(window, fresh)) {
                if (window != null) finishedWindows.add(Map.entry(id, window));
                window = fresh;
            } else window = state.window.get();
        }
        if (window != null) {
            window.attack(attack.entityId(), attack.nanos());
            window.ping(state.ping);
        }
    }

    private void onSwing(UUID id, PacketState state) {
        if (state.attackAwaitingSwing) {
            state.attackAwaitingSwing = false;
            state.swingBuffer = Math.max(0.0D, state.swingBuffer - 0.25D);
        }
        if (state.digging) return; // holding attack on a block swings every tick; that is mining, not clicking
        int interval = state.lastSwingTick < 0 ? -1 : (int) Math.min(Integer.MAX_VALUE, state.ticks - state.lastSwingTick);
        state.lastSwingTick = state.ticks;
        if (interval < 0) return;
        state.clicks.add(interval);
        CombatWindow window = state.window.get();
        if (window != null && state.ticks - state.lastAttackTick <= 40) window.click(interval);
        if (++state.clicksSinceEvaluation >= 25 && state.clicks.size() >= 40) {
            state.clicksSinceEvaluation = 0;
            ClickProfile.Summary summary = state.clicks.summary();
            if (summary.robotic()) {
                state.clickBuffer += 1.0D;
                if (state.clickBuffer >= 2.0D) {
                    state.clickBuffer = 1.0D;
                    queue(id, Check.AUTOCLICKER, 6.0D, String.format(Locale.ROOT, "%.1f clicks/s with %.0f%% identical spacing",
                        summary.clicksPerSecond(), summary.modeFraction() * 100));
                }
            } else state.clickBuffer = Math.max(0.0D, state.clickBuffer - 0.5D);
        }
    }

    private void onDig(PacketState state, ClientPacket.Dig dig) {
        switch (dig.action()) {
            case START -> { state.digging = true; state.digStartTick = state.ticks; state.lastDigTicks = -1; }
            case STOP -> {
                state.digging = false;
                state.lastDigTicks = state.digStartTick < 0 ? -1 : (int) (state.ticks - state.digStartTick);
                state.lastSwingTick = -1L;
            }
            case ABORT -> { state.digging = false; state.lastSwingTick = -1L; }
            default -> { }
        }
    }

    private void queue(UUID id, Check check, double weight, String detail) {
        if (pendingFlags.size() < 1_000) pendingFlags.add(new PendingFlag(id, check, weight, detail));
    }

    // ===================================================================================================
    // Main-thread tick
    // ===================================================================================================

    private void tick() {
        for (PendingFlag pending; (pending = pendingFlags.poll()) != null; ) {
            Player player = Bukkit.getPlayer(pending.playerId());
            if (player != null && !player.hasPermission("nerdsmp.anticheat.bypass")) flag(player, pending.check(), pending.weight(), pending.detail());
        }
        long now = System.nanoTime();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PacketState packetState = packets.get(player.getUniqueId());
            if (packetState != null) {
                packetState.movementExempt = movementExempt(player);
                packetState.bypass = player.hasPermission("nerdsmp.anticheat.bypass");
                packetState.ping = player.getPing();
                packetState.entityId = player.getEntityId();
                CombatWindow window = packetState.window.get();
                if (window != null && window.closed(now) && packetState.window.compareAndSet(window, null)) finishWindow(player, window);
            }
            Deque<HitboxMath.Box> history = boxHistory.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
            history.addFirst(box(player.getBoundingBox()));
            while (history.size() > 30) history.removeLast();
            PlayerState state = states.get(player.getUniqueId());
            if (state != null && !state.pendingHits.isEmpty()) {
                // No rotation update arrived for a while: evaluate with the rotation we have.
                state.pendingHits.removeIf(hit -> {
                    if (Bukkit.getCurrentTick() - hit.serverTick() < 3) return false;
                    evaluateHit(player, state, hit, player.getLocation().getYaw(), player.getLocation().getPitch());
                    return true;
                });
            }
        }
        for (Map.Entry<UUID, CombatWindow> finished; (finished = finishedWindows.poll()) != null; ) {
            Player player = Bukkit.getPlayer(finished.getKey());
            if (player != null) finishWindow(player, finished.getValue());
        }
        if (Bukkit.getCurrentTick() % 100 == 0) {
            int expired = Bukkit.getCurrentTick() - 200;
            recentBlocks.values().forEach(changes -> changes.values().removeIf(tick -> tick < expired));
            recentBlocks.values().removeIf(Map::isEmpty);
        }
        if (evidenceDirty && Bukkit.getCurrentTick() % 1_200 == 0) save();
    }

    private void finishWindow(Player player, CombatWindow window) {
        if (!window.usable()) return;
        double probability = model.onWindow(player, window.features());
        PlayerState state = states.get(player.getUniqueId());
        if (state != null) state.lastProbability = probability;
    }

    // ===================================================================================================
    // Movement: collision-accurate vanilla replay, with the conservative bound as fallback
    // ===================================================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        PlayerState state = state(player);
        Location from = event.getFrom();
        Location to = event.getTo();
        MovePath path = consumePath(player, state, to);
        int ticks = path.ticks();
        evaluatePendingHits(player, state, to.getYaw(), to.getPitch());
        state.previousX = from.getX();
        state.previousY = from.getY();
        state.previousZ = from.getZ();
        int now = Bukkit.getCurrentTick();
        // Crouching and crawling both slow input like sneaking.
        if (player.isSneaking() || player.getPose() == Pose.SNEAKING || player.getPose() == Pose.SWIMMING) state.sneakSeenTick = now;
        if (player.isHandRaised()) state.itemSeenTick = now;
        if (!from.getWorld().equals(to.getWorld())) { resetMovement(state); return; }
        if (movementExempt(player) || System.currentTimeMillis() < state.graceUntil) {
            resetMovement(state);
            if (groundedIn(player.getWorld(), player.getBoundingBox())) state.lastSafe = to.clone();
            return;
        }
        BoundingBox boxFrom = player.getBoundingBox();
        BoundingBox boxTo = boxFrom.clone().shift(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
        MotionModel.Environment env = environment(player, from, boxFrom, boxTo);
        boolean setback;
        boolean clean;
        if (simulateMovement && path.exact() && !env.special()) {
            state.motion.reset(); // the fallback starts fresh whenever it is next needed
            int[] outcome = simulate(player, state, from, to, path);
            setback = outcome[0] != 0;
            clean = outcome[1] != 0;
        } else {
            // No per-packet path (packet layer off, or the server rewrote the move) or a state the replay doesn't model.
            state.simulator.reset();
            MotionModel.Result result = state.motion.step(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ(), ticks, env);
            setback = false;
            if (result.verticalError() != 0.0D) {
                double severity = Math.min(1.0D, Math.abs(result.verticalError()) * 20.0D) * (result.verticalError() > 0 ? 1.0D : 0.5D);
                setback |= buffer(player, state, Check.FLIGHT, severity, 2.0D, 6.0D, result.detail() + ticksNote(ticks));
            } else clean(state, Check.FLIGHT, 0.05D);
            if (result.horizontalExcess() > 0.0D) {
                setback |= buffer(player, state, Check.SPEED, Math.min(1.0D, result.horizontalExcess() * 10.0D), 2.5D, 5.0D, result.detail() + ticksNote(ticks));
            } else clean(state, Check.SPEED, 0.05D);
            clean = result == MotionModel.Result.CLEAN;
        }
        if (setback && state.lastSafe != null && state.lastSafe.getWorld().equals(to.getWorld())) {
            event.setTo(state.lastSafe.clone());
            resetMovement(state);
            grantGrace(player, 1_000L);
            return;
        }
        if (env.groundedAfter() && clean) state.lastSafe = to.clone();
    }

    /**
     * Replays every client tick of this move. Each tick no vanilla input explains feeds SPEED (horizontal) or FLIGHT
     * (vertical). Returns {setback, clean}.
     */
    private int[] simulate(Player player, PlayerState state, Location from, Location to, MovePath path) {
        MovementSimulator simulator = state.simulator;
        int now = Bukkit.getCurrentTick();
        int pingTicks = player.getPing() / 50;
        WorldBlocks blocks = new WorldBlocks(from.getWorld(), recentBlocks.get(from.getWorld().getUID()), now - 2 * pingTicks - 10);
        int window = pingTicks + 10;
        MovementSimulator.Profile profile = profile(player, now - state.sneakSeenTick <= window, now - state.itemSeenTick <= window);
        MovementSimulator.Profile settled = new MovementSimulator.Profile(profile.walkSpeed(), profile.sneakSpeed(), profile.jumpStrength(),
            profile.jumpBoost(), profile.gravity(), profile.slowFalling(), profile.stepHeight(), profile.movementEfficiency(), 0.0D, 0.0D, false, false);
        if (!settled.equals(state.lastProfile)) {
            // Potions and attribute changes reach the client a round trip later: don't judge until both sides agree.
            if (state.lastProfile != null) state.profileChangedTick = now;
            state.lastProfile = settled;
        }
        boolean unsure = now - state.profileChangedTick <= window;
        if (!simulator.at(from.getX(), from.getY(), from.getZ())) simulator.place(blocks, profile, from.getX(), from.getY(), from.getZ());
        boolean setback = false;
        boolean clean = true;
        for (double[] step : path.steps()) {
            float yaw = Double.isNaN(step[4]) ? to.getYaw() : (float) step[4];
            MovementSimulator.Result result = simulator.step(blocks, profile, step[0], step[1], step[2], yaw, (int) step[3]);
            if (!result.evaluated()) continue;
            if (!result.flagged()) {
                clean(state, Check.SPEED, 0.05D);
                clean(state, Check.FLIGHT, 0.05D);
                continue;
            }
            clean = false;
            // The client may stand in a world or with attributes the server already changed: re-synchronised, not counted.
            if (blocks.uncertain() || unsure) continue;
            String detail = result.describe() + ticksNote((int) step[3]);
            double horizontal = result.horizontalOffset();
            if (horizontal > MovementSimulator.FLAG_OFFSET) {
                // Moving less than vanilla predicts gains nothing; it still counts a little so a strafe that keeps speed is caught.
                boolean faster = result.observedHorizontal() > result.predictedHorizontal() + MovementSimulator.FLAG_OFFSET * 0.5D;
                setback |= buffer(player, state, Check.SPEED, Math.min(1.0D, horizontal * 20.0D) * (faster ? 1.0D : 0.25D), 2.5D, 5.0D, detail);
            } else clean(state, Check.SPEED, 0.05D);
            double vertical = result.offsetY();
            if (Math.abs(vertical) > MovementSimulator.FLAG_OFFSET) {
                setback |= buffer(player, state, Check.FLIGHT, Math.min(1.0D, Math.abs(vertical) * 20.0D) * (vertical > 0 ? 1.0D : 0.5D), 2.0D, 6.0D, detail);
            } else clean(state, Check.FLIGHT, 0.05D);
            if (setback) break;
        }
        return new int[] {setback ? 1 : 0, clean ? 1 : 0};
    }

    private static MovementSimulator.Profile profile(Player player, boolean mayBeSneaking, boolean mayBeUsingItem) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        double walkSpeed = speed == null ? 0.1D : speed.getValue();
        AttributeModifier sprint = speed == null ? null : speed.getModifier(NamespacedKey.minecraft("sprinting"));
        if (sprint != null && sprint.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) walkSpeed /= 1.0D + sprint.getAmount();
        PotionEffect jumpBoost = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        return new MovementSimulator.Profile(walkSpeed, attribute(player, Attribute.SNEAKING_SPEED, 0.3D), attribute(player, Attribute.JUMP_STRENGTH, 0.42D),
            jumpBoost == null ? 0.0D : 0.1D * (jumpBoost.getAmplifier() + 1), attribute(player, Attribute.GRAVITY, 0.08D),
            player.getPotionEffect(PotionEffectType.SLOW_FALLING) != null, attribute(player, Attribute.STEP_HEIGHT, 0.6D),
            attribute(player, Attribute.MOVEMENT_EFFICIENCY, 0.0D), player.getWidth(), player.getHeight(), mayBeSneaking, mayBeUsingItem);
    }

    private static void resetMovement(PlayerState state) {
        state.motion.reset();
        state.simulator.reset();
    }

    /** The positions the client reported since the last move, each {x, y, z, client ticks, yaw}. */
    private record MovePath(List<double[]> steps, int ticks, boolean exact) { }

    /** Client ticks covered by this move: exact from the packet layer, estimated from time otherwise. */
    private MovePath consumePath(Player player, PlayerState state, Location to) {
        long nowMillis = System.currentTimeMillis();
        int estimate = (int) Math.max(1L, Math.min(20L, Math.round((nowMillis - state.lastMoveMillis) / 50.0D)));
        state.lastMoveMillis = nowMillis;
        PacketState packetState = packets.get(player.getUniqueId());
        if (packetState == null || tap == null || !tap.ready()) return new MovePath(List.of(), estimate, false);
        int ticks = 0;
        List<double[]> steps = new ArrayList<>();
        for (double[] position; (position = packetState.positions.poll()) != null; ) {
            ticks += (int) position[3];
            steps.add(position);
            if (Math.abs(position[0] - to.getX()) < 1.0E-7D && Math.abs(position[1] - to.getY()) < 1.0E-7D && Math.abs(position[2] - to.getZ()) < 1.0E-7D) {
                return new MovePath(steps, Math.max(1, Math.min(20, ticks)), steps.size() <= 40);
            }
        }
        return new MovePath(List.of(), ticks > 0 ? Math.max(1, Math.min(20, ticks)) : estimate, false);
    }

    private MotionModel.Environment environment(Player player, Location from, BoundingBox boxFrom, BoundingBox boxTo) {
        World world = from.getWorld();
        boolean groundedBefore = groundedIn(world, boxFrom);
        boolean groundedAfter = groundedIn(world, boxTo);
        boolean headBlocked = world.hasCollisionsIn(new BoundingBox(boxTo.getMinX() + 1.0E-3, boxTo.getMaxY() + 1.0E-5, boxTo.getMinZ() + 1.0E-3,
            boxTo.getMaxX() - 1.0E-3, boxTo.getMaxY() + 0.03D, boxTo.getMaxZ() - 1.0E-3))
            || world.hasCollisionsIn(new BoundingBox(boxFrom.getMinX() + 1.0E-3, boxFrom.getMaxY() + 1.0E-5, boxFrom.getMinZ() + 1.0E-3,
            boxFrom.getMaxX() - 1.0E-3, boxFrom.getMaxY() + 0.03D, boxFrom.getMaxZ() - 1.0E-3));
        Material belowFrom = world.getBlockAt(from.getBlockX(), (int) Math.floor(boxFrom.getMinY() - 0.5000001D), from.getBlockZ()).getType();
        Material belowTo = world.getBlockAt((int) Math.floor(boxTo.getCenterX()), (int) Math.floor(boxTo.getMinY() - 0.5000001D),
            (int) Math.floor(boxTo.getCenterZ())).getType();
        PotionEffect jumpBoost = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        double jumpPower = attribute(player, Attribute.JUMP_STRENGTH, 0.42D) * (belowFrom == Material.HONEY_BLOCK ? 0.5D : 1.0D)
            + (jumpBoost == null ? 0.0D : 0.1D * (jumpBoost.getAmplifier() + 1));
        boolean special = player.isInWater() || player.isInLava() || player.isInBubbleColumn() || player.isInPowderedSnow()
            || player.isSwimming() || player.isClimbing() || player.isGliding() || player.isRiptiding() || player.isInsideVehicle()
            || player.getPotionEffect(PotionEffectType.LEVITATION) != null || player.getPotionEffect(PotionEffectType.DOLPHINS_GRACE) != null
            || bounce(belowFrom) || bounce(belowTo) || touchesSpecialBlock(world, boxFrom.clone().union(boxTo).expand(0.3D, 0.6D, 0.3D), Math.min(boxFrom.getMinY(), boxTo.getMinY()))
            || crowded(player);
        return new MotionModel.Environment(groundedBefore, groundedAfter, headBlocked, friction(belowFrom), friction(belowTo),
            attribute(player, Attribute.MOVEMENT_SPEED, 0.1D), player.isSprinting(), attribute(player, Attribute.GRAVITY, 0.08D),
            player.getPotionEffect(PotionEffectType.SLOW_FALLING) != null, jumpPower, attribute(player, Attribute.STEP_HEIGHT, 0.6D), special);
    }

    private static boolean groundedIn(World world, BoundingBox box) {
        return world.hasCollisionsIn(new BoundingBox(box.getMinX() + 1.0E-3, box.getMinY() - 0.03D, box.getMinZ() + 1.0E-3,
            box.getMaxX() - 1.0E-3, box.getMinY() - 1.0E-5, box.getMaxZ() - 1.0E-3));
    }

    /** Blocks whose movement the replay doesn't model. Honey only counts beside the player (wall sliding), not underfoot. */
    private static boolean touchesSpecialBlock(World world, BoundingBox area, double feetY) {
        for (int x = (int) Math.floor(area.getMinX()); x <= (int) Math.floor(area.getMaxX()); x++) {
            for (int y = (int) Math.floor(area.getMinY()); y <= (int) Math.floor(area.getMaxY()); y++) {
                for (int z = (int) Math.floor(area.getMinZ()); z <= (int) Math.floor(area.getMaxZ()); z++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type == Material.COBWEB || type == Material.POWDER_SNOW || type == Material.SWEET_BERRY_BUSH
                        || type == Material.HONEY_BLOCK && y + 0.9375D > feetY + 1.0E-3D || type == Material.SCAFFOLDING
                        || type == Material.BUBBLE_COLUMN || type == Material.WATER || type == Material.LAVA || type == Material.SLIME_BLOCK
                        || type == Material.PISTON_HEAD || type == Material.MOVING_PISTON || type == Material.BIG_DRIPLEAF
                        || Tag.CLIMBABLE.isTagged(type) || Tag.BEDS.isTagged(type) || Tag.SHULKER_BOXES.isTagged(type)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean crowded(Player player) {
        for (Entity nearby : player.getNearbyEntities(0.6D, 0.6D, 0.6D)) if (nearby instanceof org.bukkit.entity.LivingEntity living && living.isCollidable() || nearby instanceof org.bukkit.entity.Boat) return true;
        return false;
    }

    private static boolean bounce(Material type) {
        return type == Material.SLIME_BLOCK || Tag.BEDS.isTagged(type);
    }

    private static double friction(Material type) {
        return switch (type) {
            case ICE, PACKED_ICE, FROSTED_ICE -> 0.98D;
            case BLUE_ICE -> 0.989D;
            case SLIME_BLOCK -> 0.8D;
            default -> 0.6D;
        };
    }

    /**
     * The world for the movement replay: real block collision shapes and surface factors, read on the main thread and
     * cached for one move. It never loads a chunk; touching an unloaded one, or a block changed within the player's
     * round trip, marks the move uncertain so a mismatch only re-synchronises.
     */
    private static final class WorldBlocks implements MovementSimulator.BlockView {
        private static final MovementSimulator.Surface SOUL_SAND = new MovementSimulator.Surface(0.6D, 0.4D, 1.0D);
        private static final MovementSimulator.Surface HONEY = new MovementSimulator.Surface(0.6D, 0.4D, 0.5D);
        private final World world;
        private final Map<Long, Integer> recent;
        private final int changedSince;
        private final Map<Long, List<MovementSimulator.Collider>> cells = new HashMap<>();
        private boolean uncertain;

        WorldBlocks(World world, Map<Long, Integer> recent, int changedSince) {
            this.world = world;
            this.recent = recent;
            this.changedSince = changedSince;
        }

        boolean uncertain() {
            return uncertain;
        }

        @Override
        public void collect(MovementSimulator.Box area, List<MovementSimulator.Collider> out) {
            int minY = Math.max(world.getMinHeight(), (int) Math.floor(area.minY()) - 1); // fences and walls reach up from the block below
            int maxY = Math.min(world.getMaxHeight() - 1, (int) Math.floor(area.maxY()));
            for (int x = (int) Math.floor(area.minX()); x <= (int) Math.floor(area.maxX()); x++) {
                for (int z = (int) Math.floor(area.minZ()); z <= (int) Math.floor(area.maxZ()); z++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (MovementSimulator.Collider collider : cell(x, y, z)) if (collider.box().intersects(area)) out.add(collider);
                    }
                }
            }
        }

        private List<MovementSimulator.Collider> cell(int x, int y, int z) {
            long key = blockKey(x, y, z);
            List<MovementSimulator.Collider> cached = cells.get(key);
            if (cached != null) return cached;
            List<MovementSimulator.Collider> shapes = List.of();
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                uncertain = true;
            } else {
                Integer changed = recent == null ? null : recent.get(key);
                if (changed != null && changed >= changedSince) uncertain = true;
                Block block = world.getBlockAt(x, y, z);
                if (!block.getType().isAir()) {
                    List<MovementSimulator.Collider> list = new ArrayList<>(2);
                    for (BoundingBox box : block.getCollisionShape().getBoundingBoxes()) {
                        list.add(new MovementSimulator.Collider(x, y, z, new MovementSimulator.Box(x + box.getMinX(), y + box.getMinY(), z + box.getMinZ(),
                            x + box.getMaxX(), y + box.getMaxY(), z + box.getMaxZ())));
                    }
                    shapes = list;
                }
            }
            cells.put(key, shapes);
            return shapes;
        }

        @Override
        public MovementSimulator.Surface surface(int x, int y, int z) {
            if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) return MovementSimulator.Surface.DEFAULT;
            Material type = world.getBlockAt(x, y, z).getType();
            if (type == Material.SOUL_SAND) return SOUL_SAND;
            if (type == Material.HONEY_BLOCK) return HONEY;
            double friction = friction(type);
            return friction == 0.6D ? MovementSimulator.Surface.DEFAULT : new MovementSimulator.Surface(friction, 1.0D, 1.0D);
        }
    }

    private static double attribute(Player player, Attribute attribute, double fallback) {
        var instance = player.getAttribute(attribute);
        return instance == null ? fallback : instance.getValue();
    }

    private static String ticksNote(int ticks) {
        return ticks > 1 ? " over " + ticks + " ticks" : "";
    }

    // ===================================================================================================
    // Combat: lag-compensated reach and hitbox ray tests
    // ===================================================================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || combatExempt(player)) {
            return;
        }
        // One server-made signature arc, authorized for exactly this player, target and damage. The
        // player's own swings, movement and packets are judged as usual.
        if (ArcaneEffectScope.covers(event)) return;
        PlayerState state = state(player);
        Entity target = event.getEntity();
        double range = attribute(player, Attribute.ENTITY_INTERACTION_RANGE, 3.0D);
        Location eye = player.getEyeLocation();
        HitboxMath.Box now = box(target.getBoundingBox());
        double gross = HitboxMath.distance(eye.getX(), eye.getY(), eye.getZ(), now);
        int pingTicks = (int) Math.ceil(player.getPing() / 50.0D) + 3;
        if (gross > range + 3.0D + pingTicks * 0.3D) {
            event.setCancelled(true);
            flag(player, Check.REACH, 12.0D, String.format(Locale.ROOT, "hit from %.2f blocks; reach is %.2f", gross, range));
            return;
        }
        List<HitboxMath.Box> candidates = new ArrayList<>();
        Deque<HitboxMath.Box> history = target instanceof Player ? boxHistory.get(target.getUniqueId()) : null;
        if (history != null && !history.isEmpty()) {
            // Client sees the target where it was up to its ping ago, interpolating between ticks: test every
            // recorded tick box and the space between consecutive ones.
            HitboxMath.Box previous = null;
            int index = 0;
            for (HitboxMath.Box recorded : history) {
                if (index++ > pingTicks) break;
                candidates.add(recorded.expand(0.03D));
                if (previous != null) candidates.add(union(previous, recorded).expand(0.03D));
                previous = recorded;
            }
        } else {
            candidates.add(now.expand(Math.min(0.6D, 0.08D * pingTicks) + 0.03D));
        }
        float sneakOffset = player.isSneaking() ? 0.35F : -0.35F;
        state.pendingHits.add(new PendingHit(target.getUniqueId(), eye.getX(), eye.getY(), eye.getZ(), state.previousX,
            state.previousY + (eye.getY() - player.getLocation().getY()), state.previousZ, sneakOffset,
            eye.getYaw(), eye.getPitch(), range, candidates, Bukkit.getCurrentTick()));
        while (state.pendingHits.size() > 8) state.pendingHits.removeFirst();
    }

    private void evaluatePendingHits(Player player, PlayerState state, float yaw, float pitch) {
        if (state.pendingHits.isEmpty()) return;
        for (PendingHit hit : state.pendingHits) evaluateHit(player, state, hit, yaw, pitch);
        state.pendingHits.clear();
    }

    /**
     * The crosshair at the moment of the click lies between the rotation the server had at the attack and the
     * client's next rotation, and the camera between the previous and current tick positions. If no combination of
     * those, against any position the target held within the player's ping, puts the ray through the hitbox, the
     * hit was not aimed at the target.
     */
    private void evaluateHit(Player player, PlayerState state, PendingHit hit, float nextYaw, float nextPitch) {
        double[][] eyes = {
            {hit.eyeX(), hit.eyeY(), hit.eyeZ()}, {hit.prevEyeX(), hit.prevEyeY(), hit.prevEyeZ()},
            {hit.eyeX(), hit.eyeY() + hit.sneakOffset(), hit.eyeZ()}
        };
        double nearestRay = Double.MAX_VALUE;
        double nearestDistance = Double.MAX_VALUE;
        double smallestOffset = Double.MAX_VALUE;
        for (double t = 0.0D; t <= 1.0001D; t += 0.25D) {
            double[] direction = HitboxMath.direction(HitboxMath.lerpAngle(hit.yaw(), nextYaw, t), (float) (hit.pitch() + (nextPitch - hit.pitch()) * t));
            for (double[] eye : eyes) {
                for (HitboxMath.Box box : hit.boxes()) {
                    double ray = HitboxMath.rayHit(eye[0], eye[1], eye[2], direction, box);
                    if (ray >= 0.0D) nearestRay = Math.min(nearestRay, ray);
                    smallestOffset = Math.min(smallestOffset, HitboxMath.offsetToCenter(eye[0], eye[1], eye[2], direction, box));
                    if (t == 0.0D) nearestDistance = Math.min(nearestDistance, HitboxMath.distance(eye[0], eye[1], eye[2], box));
                }
            }
        }
        boolean inside = nearestRay != Double.MAX_VALUE;
        double reach = inside ? nearestRay : nearestDistance;
        PacketState packetState = packets.get(player.getUniqueId());
        CombatWindow window = packetState == null ? null : packetState.window.get();
        if (window != null) window.hit(reach, smallestOffset, inside);
        double allowed = hit.range() + 0.03D;
        if (!inside) {
            buffer(player, state, Check.HITBOX, 1.0D, 3.0D, 9.0D,
                String.format(Locale.ROOT, "hit landed outside the target's hitbox (%.1f degrees off, %.2f blocks away)", smallestOffset, nearestDistance));
        } else {
            clean(state, Check.HITBOX, 0.5D);
            if (nearestRay > allowed) {
                buffer(player, state, Check.REACH, Math.min(1.0D, (nearestRay - allowed) * 5.0D), 2.0D, 8.0D,
                    String.format(Locale.ROOT, "reach %.3f blocks; allowed %.3f (lag-compensated)", nearestRay, hit.range()));
            } else clean(state, Check.REACH, 0.25D);
        }
    }

    // ===================================================================================================
    // Mining speed
    // ===================================================================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        state(event.getPlayer()).breakSpeedAtStart = event.getBlock().getBreakSpeed(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (net.nerdsmp.paper.gathering.TreeHarvestBridge.automated(event)) return;
        // One server-made Veinseeker or Chain Harvest break: not a client dig, so there are no dig ticks to judge.
        if (ArcaneBreakScope.automated(event)) return;
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE || combatExempt(player)) return;
        PacketState packetState = packets.get(player.getUniqueId());
        if (packetState == null || tap == null || !tap.ready()) return;
        int clientTicks = packetState.lastDigTicks;
        packetState.lastDigTicks = -1;
        if (clientTicks < 0) return;
        PlayerState state = state(player);
        Block block = event.getBlock();
        double fastest = Math.max(state.breakSpeedAtStart, block.getBreakSpeed(player));
        if (fastest <= 0.0F || fastest >= 1.0F) return; // instant or unbreakable
        int expected = (int) Math.ceil(1.0D / fastest);
        if (clientTicks < expected - 1) {
            boolean stop = buffer(player, state, Check.FAST_BREAK, 1.0D, 2.0D, 9.0D,
                String.format(Locale.ROOT, "%s broken in %d ticks; takes at least %d", block.getType(), clientTicks, expected));
            if (stop || clientTicks < expected * 0.7D) event.setCancelled(true);
        } else clean(state, Check.FAST_BREAK, 1.0D);
    }

    // ===================================================================================================
    // Grace periods and lifecycle
    // ===================================================================================================

    public void grantGrace(Player player, long millis) {
        PlayerState state = state(player);
        state.graceUntil = Math.max(state.graceUntil, System.currentTimeMillis() + millis);
        resetMovement(state);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        track(event.getPlayer());
        grantGrace(event.getPlayer(), 5_000L);
    }

    private void track(Player player) {
        PlayerState state = state(player);
        state.lastSafe = player.getLocation().clone();
        state.previousX = player.getLocation().getX();
        state.previousY = player.getLocation().getY();
        state.previousZ = player.getLocation().getZ();
        packets.computeIfAbsent(player.getUniqueId(), ignored -> new PacketState());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        states.remove(id);
        boxHistory.remove(id);
        PacketState packetState = packets.remove(id);
        if (packetState != null) {
            CombatWindow window = packetState.window.getAndSet(null);
            if (window != null) finishWindow(event.getPlayer(), window);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { grantGrace(event.getPlayer(), 3_000L); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) { grantGrace(event.getPlayer(), 2_500L + Math.min(2_000L, event.getPlayer().getPing() * 2L)); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) { grantGrace(event.getPlayer(), 3_000L); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) { grantGrace(event.getPlayer(), 3_000L); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent event) { if (event.getExited() instanceof Player player) grantGrace(player, 1_500L); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) { pistonGrace(event.getBlock().getLocation()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) { pistonGrace(event.getBlock().getLocation()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackEvent event) {
        // Explosions and wind charges push players without a velocity event.
        if (event.getEntity() instanceof Player player) grantGrace(player, 2_500L + Math.min(2_000L, player.getPing() * 2L));
    }

    // Block changes reach the client a round trip after the server. Cancelled places and breaks count too: the
    // client showed the ghost block until the server corrected it.

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlaceChange(BlockPlaceEvent event) { markChanged(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreakChange(BlockBreakEvent event) { markChanged(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { event.blockList().forEach(this::markChanged); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { event.blockList().forEach(this::markChanged); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) { markChanged(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) { markChanged(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { markChanged(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRedstone(BlockRedstoneEvent event) { markChanged(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) { markChanged(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractChange(PlayerInteractEvent event) {
        // Doors, trapdoors and gates opened by hand, and farmland trampled underfoot.
        Block block = event.getClickedBlock();
        if (block != null && (event.getAction() == Action.PHYSICAL || block.getBlockData() instanceof Openable)) markChanged(block);
    }

    private void markChanged(Block block) {
        recentBlocks.computeIfAbsent(block.getWorld().getUID(), ignored -> new HashMap<>()).put(blockKey(block.getX(), block.getY(), block.getZ()), Bukkit.getCurrentTick());
    }

    private static long blockKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (long) y & 0xFFFL;
    }

    private void pistonGrace(Location piston) {
        for (Player player : piston.getWorld().getPlayers()) if (player.getLocation().distanceSquared(piston) < 16.0D) grantGrace(player, 1_000L);
    }

    @Override
    public void close() {
        if (ticker != null) ticker.cancel();
        if (tap != null) tap.close();
        model.close();
        save();
    }

    // ===================================================================================================
    // Scoring, buffers, alerts and automatic action
    // ===================================================================================================

    /** Adds severity to a check's buffer; flags (returning the setback decision) only once the buffer passes threshold. */
    private boolean buffer(Player player, PlayerState state, Check check, double severity, double threshold, double weight, String detail) {
        double value = state.buffers.getOrDefault(check, 0.0D) + severity;
        if (value < threshold) {
            state.buffers.put(check, value);
            return false;
        }
        state.buffers.put(check, threshold * 0.5D);
        return flag(player, check, weight, detail);
    }

    private void clean(PlayerState state, Check check, double decay) {
        Double value = state.buffers.get(check);
        if (value != null) state.buffers.put(check, Math.max(0.0D, value - decay));
    }

    private boolean flag(Player player, Check check, double weight, String detail) {
        PlayerState state = state(player);
        long now = System.currentTimeMillis();
        if (now - state.lastViolationAt > 120_000L) state.counts.clear();
        state.lastViolationAt = now;
        state.score = WatchdogMath.decay(state.score, now - state.scoreUpdatedAt, decayPerSecond) + weight;
        state.scoreUpdatedAt = now;
        state.counts.merge(check, 1, Integer::sum);
        PacketState packetState = packets.get(player.getUniqueId());
        CombatWindow window = packetState == null ? null : packetState.window.get();
        if (window != null) window.ruleFlag();
        Evidence entry = new Evidence(Instant.now(), player.getUniqueId(), player.getName(), check, detail, state.score, player.getPing(), false, "BUFFERED");
        addEvidence(entry);
        if (state.score >= alertScore && now - state.lastAlertAt > 5_000L) {
            state.lastAlertAt = now;
            alert(player.getName(), check, detail, state.score);
            save();
        }
        int checkCount = state.counts.getOrDefault(check, 0);
        int distinct = (int) state.counts.values().stream().filter(count -> count >= 2).count();
        if (autonomous && state.score >= kickScore && (distinct >= 2 || checkCount >= 10)) {
            evidence.set(evidence.size() - 1, new Evidence(entry.createdAt, entry.playerId, entry.playerName, entry.check,
                entry.detail, entry.score, entry.ping, entry.bedrock, "KICK"));
            save();
            Bukkit.getScheduler().runTask(plugin, () -> player.kick(Component.text(
                "Watchdog detected repeated impossible gameplay. Staff can review the saved evidence.")));
            state.score = 0.0D;
            state.counts.clear();
            return true;
        }
        return autonomous && state.score >= setbackScore;
    }

    /** Records an action the combat model took or would take, with the same alerting as rule checks. */
    public void recordModelAction(UUID playerId, String name, double probability, String action, String detail) {
        Evidence entry = new Evidence(Instant.now(), playerId, name, Check.MODEL, detail, probability * 100.0D, 0, false, action);
        addEvidence(entry);
        alert(name, Check.MODEL, detail, probability * 100.0D);
        save();
    }

    private void addEvidence(Evidence entry) {
        evidence.add(entry);
        if (evidence.size() > 2_000) evidence.removeFirst();
        evidenceDirty = true;
    }

    private void alert(String name, Check check, String detail, double score) {
        String message = "<dark_gray>[</dark_gray><gold><bold>WATCHDOG</bold></gold><dark_gray>]</dark_gray> <white>"
            + escape(name) + "</white> <yellow>" + check + "</yellow> <gray>" + escape(detail) + "</gray> <red>" + decimal(score) + "</red>";
        Bukkit.getOnlinePlayers().stream().filter(staff -> staff.hasPermission("nerdsmp.staff.anticheat")).forEach(staff -> staff.sendRichMessage(message));
        plugin.getLogger().warning("Watchdog: " + name + " " + check + " " + detail + " score=" + decimal(score));
    }

    private PlayerState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), ignored -> new PlayerState());
    }

    private double currentScore(PlayerState state) {
        return WatchdogMath.decay(state.score, System.currentTimeMillis() - state.scoreUpdatedAt, decayPerSecond);
    }

    private static boolean movementExempt(Player player) {
        return player.hasPermission("nerdsmp.anticheat.bypass") || player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR || player.getAllowFlight() || player.isFlying()
            || player.isGliding() || player.isInsideVehicle() || player.isRiptiding() || player.isDead() || player.isSleeping();
    }

    private static boolean combatExempt(Player player) {
        return player.hasPermission("nerdsmp.anticheat.bypass") || player.getGameMode() == GameMode.SPECTATOR;
    }

    private static HitboxMath.Box box(BoundingBox box) {
        return new HitboxMath.Box(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    private static HitboxMath.Box union(HitboxMath.Box a, HitboxMath.Box b) {
        return new HitboxMath.Box(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()),
            Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
    }

    // ===================================================================================================
    // Staff command and console data
    // ===================================================================================================

    public boolean handle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nerdsmp.staff.anticheat")) {
            sender.sendRichMessage("<red>You do not have permission to inspect Watchdog.</red>");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            long watched = states.values().stream().filter(state -> currentScore(state) >= alertScore).count();
            sender.sendRichMessage("<gold><bold>Watchdog</bold></gold> <gray>Mode:</gray> <white>" + (autonomous ? "autonomous" : "alerts-only")
                + "</white> <dark_gray>•</dark_gray> <yellow>" + watched + " watched</yellow> <dark_gray>•</dark_gray> <gray>packets:</gray> <white>"
                + (tap == null ? "off" : escape(tap.status())) + "</white>");
            sender.sendRichMessage("<gray>Combat model:</gray> <white>" + escape(model.panelSnapshot().get("note").getAsString()) + "</white>");
            return true;
        }
        if (args[0].equalsIgnoreCase("recent")) {
            evidence.stream().sorted(Comparator.comparing(Evidence::createdAt).reversed()).limit(10)
                .forEach(entry -> sender.sendRichMessage("<dark_gray>" + entry.createdAt + "</dark_gray> <white>" + escape(entry.playerName)
                    + "</white> <yellow>" + entry.check + "</yellow> <gray>" + escape(entry.detail) + "</gray> <red>" + decimal(entry.score) + "</red>"));
            return true;
        }
        if (args[0].equalsIgnoreCase("train")) {
            sender.sendRichMessage(model.train("requested by " + sender.getName())
                ? "<green>Training the combat model in the background. Results appear in /watchdog status and the console.</green>"
                : "<yellow>A training run is already in progress.</yellow>");
            return true;
        }
        if (args[0].equalsIgnoreCase("label") && args.length >= 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (target == null) { sender.sendRichMessage("<red>No player by that name has joined.</red>"); return true; }
            int label = switch (args[2].toLowerCase(Locale.ROOT)) { case "cheat", "cheating" -> 1; case "legit", "clean" -> 0; case "clear" -> -1; default -> -2; };
            if (label == -2) { sender.sendRichMessage("<red>Use cheat, legit or clear.</red>"); return true; }
            long hours = 24;
            if (args.length >= 4) try { hours = Math.max(1, Math.min(24 * 90, Long.parseLong(args[3]))); } catch (NumberFormatException ignored) { }
            long now = System.currentTimeMillis();
            int changed = model.labelPlayer(target.getUniqueId(), label, now - hours * 3_600_000L, now, sender.getName());
            sender.sendRichMessage("<green>Labelled " + changed + " combat windows from the last " + hours + " h for " + escape(String.valueOf(target.getName()))
                + ". The model retrains a minute after the last label.</green>");
            return true;
        }
        if ((args[0].equalsIgnoreCase("player") || args[0].equalsIgnoreCase("clear")) && args.length == 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendRichMessage("<red>That player is not online.</red>"); return true; }
            PlayerState state = state(target);
            if (args[0].equalsIgnoreCase("clear")) {
                state.score = 0.0D;
                state.counts.clear();
                state.buffers.clear();
                sender.sendRichMessage("<green>Cleared live Watchdog buffers for " + escape(target.getName()) + ".</green>");
            } else {
                sender.sendRichMessage("<gold>" + escape(target.getName()) + "</gold> <gray>score</gray> <red>" + decimal(currentScore(state))
                    + "</red> <gray>ping</gray> <white>" + target.getPing() + "ms</white>");
                state.counts.forEach((check, count) -> sender.sendRichMessage("<gray>" + check + ":</gray> <white>" + count + "</white>"));
            }
            return true;
        }
        sender.sendRichMessage("<red>Use /watchdog [status|recent|player|clear|label|train] [player] [cheat|legit|clear] [hours].</red>");
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nerdsmp.staff.anticheat")) return List.of();
        if (args.length == 1) return List.of("status", "recent", "player", "clear", "label", "train");
        if (args.length == 2 && List.of("player", "clear", "label").contains(args[0].toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("label")) return List.of("cheat", "legit", "clear");
        return List.of();
    }

    /** Capture on the game thread; the HTTP panel receives only the resulting detached JSON. */
    public com.google.gson.JsonObject panelSnapshot() {
        var root = new com.google.gson.JsonObject();
        root.addProperty("mode", autonomous ? "autonomous" : "alerts-only");
        root.addProperty("alertScore", alertScore);
        root.addProperty("setbackScore", setbackScore);
        root.addProperty("kickScore", kickScore);
        root.addProperty("decayPerSecond", decayPerSecond);
        root.addProperty("packetLayer", tap == null ? "Not started" : tap.status());
        root.addProperty("packetErrors", tap == null ? 0L : tap.decodeErrors());
        var checks = new com.google.gson.JsonArray();
        for (Check check : Check.values()) if (check != Check.COMBAT_RATE) checks.add(check.name());
        root.add("checks", checks);
        var array = new com.google.gson.JsonArray();
        evidence.stream().sorted(Comparator.comparing(Evidence::createdAt).reversed()).limit(400).forEach(entry -> array.add(evidenceJson(entry)));
        root.add("evidence", array);
        var live = new com.google.gson.JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerState state = states.get(player.getUniqueId());
            if (state == null) continue;
            var row = new com.google.gson.JsonObject();
            row.addProperty("uuid", player.getUniqueId().toString());
            row.addProperty("name", player.getName());
            row.addProperty("score", round(currentScore(state)));
            row.addProperty("exempt", movementExempt(player));
            row.addProperty("probability", round(state.lastProbability));
            var counts = new com.google.gson.JsonObject();
            state.counts.forEach((check, count) -> counts.addProperty(check.name(), count));
            row.add("counts", counts);
            live.add(row);
        }
        root.add("live", live);
        root.add("model", model.panelSnapshot());
        return root;
    }

    public com.google.gson.JsonObject panelPlayer(UUID playerId) {
        var root = new com.google.gson.JsonObject();
        PlayerState state = states.get(playerId);
        root.addProperty("score", state == null ? 0.0D : round(currentScore(state)));
        var counts = new com.google.gson.JsonObject();
        if (state != null) state.counts.forEach((check, count) -> counts.addProperty(check.name(), count));
        root.add("counts", counts);
        var array = new com.google.gson.JsonArray();
        evidence.stream().filter(entry -> entry.playerId().equals(playerId)).sorted(Comparator.comparing(Evidence::createdAt).reversed())
            .limit(200).forEach(entry -> array.add(evidenceJson(entry)));
        root.add("evidence", array);
        root.add("model", model.panelPlayer(playerId));
        return root;
    }

    public double liveScore(UUID playerId) {
        PlayerState state = states.get(playerId);
        return state == null ? 0.0D : round(currentScore(state));
    }

    /** Clears live buffers only; saved evidence is kept for review. */
    public boolean clear(UUID playerId) {
        PlayerState state = states.get(playerId);
        if (state == null) return false;
        state.score = 0.0D;
        state.counts.clear();
        state.buffers.clear();
        return true;
    }

    private com.google.gson.JsonObject evidenceJson(Evidence entry) {
        var row = new com.google.gson.JsonObject();
        row.addProperty("time", entry.createdAt().toString());
        row.addProperty("uuid", entry.playerId().toString());
        row.addProperty("player", entry.playerName());
        row.addProperty("check", entry.check().name());
        row.addProperty("score", round(entry.score()));
        row.addProperty("ping", entry.ping());
        row.addProperty("action", entry.action());
        row.addProperty("detail", entry.detail());
        return row;
    }

    // ===================================================================================================
    // Persistence
    // ===================================================================================================

    private void load() {
        if (!file.isFile()) return;
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
        var root = yaml.getConfigurationSection("evidence");
        if (root == null) return;
        for (String index : root.getKeys(false)) {
            try {
                String path = "evidence." + index;
                evidence.add(new Evidence(Instant.parse(yaml.getString(path + ".created-at")), UUID.fromString(yaml.getString(path + ".player-id")),
                    yaml.getString(path + ".player-name", "Unknown"), Check.valueOf(yaml.getString(path + ".check", "SPEED")),
                    yaml.getString(path + ".detail", ""), yaml.getDouble(path + ".score"), yaml.getInt(path + ".ping"),
                    yaml.getBoolean(path + ".bedrock"), yaml.getString(path + ".action", "BUFFERED")));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Ignoring malformed Watchdog evidence " + index);
            }
        }
    }

    private synchronized void save() {
        evidenceDirty = false;
        var yaml = new org.bukkit.configuration.file.YamlConfiguration();
        int start = Math.max(0, evidence.size() - 1_000);
        for (int index = start; index < evidence.size(); index++) {
            Evidence entry = evidence.get(index);
            String path = "evidence." + String.format(Locale.ROOT, "%06d", index - start);
            yaml.set(path + ".created-at", entry.createdAt.toString());
            yaml.set(path + ".player-id", entry.playerId.toString());
            yaml.set(path + ".player-name", entry.playerName);
            yaml.set(path + ".check", entry.check.name());
            yaml.set(path + ".detail", entry.detail);
            yaml.set(path + ".score", entry.score);
            yaml.set(path + ".ping", entry.ping);
            yaml.set(path + ".bedrock", entry.bedrock);
            yaml.set(path + ".action", entry.action);
        }
        try {
            AtomicYamlFiles.save(yaml, file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save Watchdog evidence: " + exception.getMessage());
        }
    }

    private static String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private static String escape(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }

    // ===================================================================================================
    // Types
    // ===================================================================================================

    /** Saved names are part of the evidence file format: append only. COMBAT_RATE remains for old evidence. */
    enum Check {
        SPEED, FLIGHT, REACH, COMBAT_RATE, FAST_BREAK, TIMER, BAD_PACKETS, AUTOCLICKER, HITBOX, MODEL
    }

    private record PendingFlag(UUID playerId, Check check, double weight, String detail) { }

    private record PendingHit(UUID targetId, double eyeX, double eyeY, double eyeZ, double prevEyeX, double prevEyeY, double prevEyeZ,
                              float sneakOffset, float yaw, float pitch, double range, List<HitboxMath.Box> boxes, int serverTick) { }

    private static final class PlayerState {
        private final MotionModel motion = new MotionModel();
        private final MovementSimulator simulator = new MovementSimulator();
        private MovementSimulator.Profile lastProfile;
        private int profileChangedTick = Integer.MIN_VALUE / 2;
        private int sneakSeenTick = Integer.MIN_VALUE / 2;
        private int itemSeenTick = Integer.MIN_VALUE / 2;
        private final EnumMap<Check, Integer> counts = new EnumMap<>(Check.class);
        private final EnumMap<Check, Double> buffers = new EnumMap<>(Check.class);
        private final Deque<PendingHit> pendingHits = new ArrayDeque<>();
        private Location lastSafe;
        private long graceUntil;
        private long lastMoveMillis = System.currentTimeMillis();
        private double previousX, previousY, previousZ;
        private float breakSpeedAtStart;
        private double score;
        private long scoreUpdatedAt = System.currentTimeMillis();
        private long lastAlertAt;
        private long lastViolationAt;
        private double lastProbability = -1.0D;
    }

    private record Evidence(Instant createdAt, UUID playerId, String playerName, Check check, String detail, double score,
                            int ping, boolean bedrock, String action) { }
}

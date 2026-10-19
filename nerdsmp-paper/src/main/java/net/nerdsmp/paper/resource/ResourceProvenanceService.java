package net.nerdsmp.paper.resource;

import net.nerdsmp.paper.listener.CropHarvestRules;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Chunk-owned provenance: persisted by Paper with normal world saves, not a global placement cache. */
public final class ResourceProvenanceService implements Listener {
    private static final long SCHEMA = 1;
    private static final int MAX_PENDING_FARM_GROWTH = 4096;
    private static final Set<String> RENEWABLE_FARM_MATERIALS = Set.of(
        "MELON", "PUMPKIN", "SUGAR_CANE", "CACTUS", "BAMBOO", "TORCHFLOWER"
    );
    private final SessionRegistry sessions;
    private final Function<String, NamespacedKey> keyFactory;
    private final Consumer<Runnable> nextTick;
    private final BooleanSupplier isMainThread;
    private final Function<UUID, World> worlds;
    private final Logger logger;
    private final HarvestClaims<BlockBreakEvent> harvests = new HarvestClaims<>();
    private final Map<Integer, NamespacedKey> keys = new HashMap<>();
    private final Set<Section> failed = new HashSet<>();
    private final Map<Position, UUID> growthBatches = new HashMap<>();
    private final Map<Position, UUID> pendingTrees = new HashMap<>();
    private final Map<Position, UUID> pendingGenerators = new HashMap<>();
    private final Map<Position, UUID> pendingFarmGrowth = new HashMap<>();
    private int queuedFarmGrowth;

    /** Called only by the island generator handler after a water/lava formation. */
    public void renewGenerator(org.bukkit.event.block.BlockFormEvent event) {
        mainThread();
        Block block = event.getBlock();
        Position cell = position(block);
        UUID token = UUID.randomUUID();
        String expected = event.getNewState().getBlockData().getAsString();
        pendingGenerators.put(cell, token);
        nextTick.accept(() -> {
            boolean active = pendingGenerators.remove(cell, token);
            World world = worlds.apply(cell.world());
            if (!active || event.isCancelled() || world == null || !world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) return;
            Block current = world.getBlockAt(cell.x(), cell.y(), cell.z());
            if (expected.equals(current.getBlockData().getAsString())) change(List.of(current), true);
        });
    }

    public ResourceProvenanceService(JavaPlugin plugin, SessionRegistry sessions) {
        this(sessions, key -> new NamespacedKey(plugin, key), task -> Bukkit.getScheduler().runTask(plugin, task),
            Bukkit::isPrimaryThread, Bukkit::getWorld, plugin.getLogger());
    }

    ResourceProvenanceService(SessionRegistry sessions, Function<String, NamespacedKey> keyFactory,
                              Consumer<Runnable> nextTick, BooleanSupplier isMainThread,
                              Function<UUID, World> worlds, Logger logger) {
        this.sessions = sessions;
        this.keyFactory = keyFactory;
        this.nextTick = nextTick;
        this.isMainThread = isMainThread;
        this.worlds = worlds;
        this.logger = logger;
    }

    public Optional<HarvestClaims.Harvest> claim(BlockBreakEvent event, HarvestClaims.Channel channel) {
        mainThread();
        if (event.isCancelled()) return Optional.empty();
        return harvests.claim(event, channel, () -> decide(event));
    }

    /** Read-only preview for retained crop-drop bonuses; actual rewards still require claim(). */
    public boolean cropRewardEligible(Block block) {
        mainThread();
        try {
            ResourceLedger ledger = read(block);
            boolean ripeField = block.getBlockData() instanceof Ageable age
                && CropHarvestRules.mature(block.getType().name(), age.getAge(), age.getMaximumAge());
            return !failed.contains(section(block)) && (ripeField || !ledger.blocked(block.getX() & 15, block.getY(), block.getZ() & 15));
        } catch (RuntimeException error) { fail(block, error); return false; }
    }

    /** Read-only tree preview; actual XP and bonuses still require the native break's claim. */
    public boolean naturalLog(Block block) {
        mainThread();
        if(!ResourceMaterials.log(block.getType().name())) return false;
        try { return !failed.contains(section(block))&&!read(block).blocked(block.getX()&15,block.getY(),block.getZ()&15); }
        catch(RuntimeException error) { fail(block,error); return false; }
    }

    /** Trusted managed-node renewal only, after the catalog controller restores the expected block. */
    public void renewManaged(Block block, org.bukkit.Material expected) {
        mainThread();
        if (block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) && block.getType() == expected)
            change(List.of(block), true);
    }

    private boolean decide(BlockBreakEvent event) {
        Block block = event.getBlock();
        pendingTrees.remove(position(block));
        pendingGenerators.remove(position(block));
        pendingFarmGrowth.remove(position(block));
        boolean crop = block.getBlockData() instanceof Ageable age
            && CropHarvestRules.mature(block.getType().name(), age.getAge(), age.getMaximumAge());
        boolean eligible = false;
        try {
            ResourceLedger ledger = read(block);
            eligible = crop || !ledger.blocked(block.getX() & 15, block.getY(), block.getZ() & 15);
            // Keep a tombstone after consumption. A known tree-growth event may renew it later.
            ResourceLedger consumed = ledger.withBlocked(List.of(cell(block)));
            if (consumed != ledger) write(block, consumed);
        } catch (RuntimeException error) { fail(block, error); }
        GameMode mode = event.getPlayer().getGameMode();
        return eligible && !failed.contains(section(block)) && event.isDropItems()
            && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE)
            && sessions.account(event.getPlayer().getUniqueId()).isPresent();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        if (event instanceof BlockMultiPlaceEvent multi) {
            change(multi.getReplacedBlockStates().stream().map(BlockState::getBlock).toList(), false);
        } else change(List.of(event.getBlock()), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.isCancelled()) return;
        moved(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.isCancelled()) return;
        BlockFace direction = event.getBlock().getBlockData() instanceof Directional piston
            ? piston.getFacing().getOppositeFace() : event.getDirection();
        moved(event.getBlocks(), direction);
    }

    private void moved(List<Block> blocks, BlockFace direction) {
        List<Block> changed = new ArrayList<>(blocks.size() * 2);
        for (Block block : blocks) { changed.add(block); changed.add(block.getRelative(direction)); }
        change(changed, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (event.isCancelled()) return;
        if (event.getEntity() instanceof FallingBlock || event.getEntity() instanceof Enderman) {
            change(List.of(event.getBlock()), false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(StructureGrowEvent event) {
        if (event.isCancelled()) return;
        // Even dispenser bonemeal can report isFromBonemeal=false. Verify actual growth next tick.
        queueTrees(event.getLocation().getBlock(), event::getBlocks, () -> !event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFarmGrow(BlockGrowEvent event) {
        // Form/spread events have separate handlers and may also represent non-farming changes.
        if (event.isCancelled() || event instanceof BlockFormEvent) return;
        queueFarmGrowth(event.getBlock(), () -> List.of(event.getNewState()), () -> !event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFarmSpread(BlockSpreadEvent event) {
        if (event.isCancelled() || event.getSource().getType() != org.bukkit.Material.BAMBOO
            || event.getNewState().getType() != org.bukkit.Material.BAMBOO) return;
        queueFarmGrowth(event.getBlock(), () -> List.of(event.getNewState()), () -> !event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFertilize(BlockFertilizeEvent event) {
        // This later event can veto or filter the preceding StructureGrowEvent.
        growthBatches.remove(position(event.getBlock()));
        // A fertilization veto must also invalidate a pending renewal for one of its farm cells.
        pendingFarmGrowth.remove(position(event.getBlock()));
        if (event.getBlocks().size() <= MAX_PENDING_FARM_GROWTH) {
            for (BlockState state : event.getBlocks()) pendingFarmGrowth.remove(position(state.getBlock()));
        }
        if (!event.isCancelled()) {
            queueTrees(event.getBlock(), event::getBlocks, () -> !event.isCancelled());
            queueFarmGrowth(event.getBlock(), event::getBlocks, () -> !event.isCancelled());
        }
    }

    private void queueFarmGrowth(Block origin, Supplier<List<BlockState>> finalStates, BooleanSupplier accepted) {
        mainThread();
        if (queuedFarmGrowth >= MAX_PENDING_FARM_GROWTH) return;
        List<BlockState> initial = finalStates.get();
        if (initial.size() > MAX_PENDING_FARM_GROWTH) return;
        UUID originWorld = origin.getWorld().getUID();
        Map<Position, String> expected = new HashMap<>();
        for (BlockState state : initial) {
            // Never launder an already placed fruit/stalk just because something grew nearby.
            Block block = state.getBlock();
            if (newFarmGrowth(state) && block.getWorld().getUID().equals(originWorld)) {
                expected.put(position(block), state.getBlockData().getAsString());
            }
        }
        if (expected.isEmpty() || pendingFarmGrowth.size() + expected.size() > MAX_PENDING_FARM_GROWTH) return;
        UUID token = UUID.randomUUID();
        expected.keySet().forEach(cell -> pendingFarmGrowth.put(cell, token));
        queuedFarmGrowth++;
        try {
            nextTick.accept(() -> {
                try {
                    boolean active = accepted.getAsBoolean();
                    List<BlockState> retainedStates = finalStates.get();
                    if (retainedStates.size() > MAX_PENDING_FARM_GROWTH) active = false;
                    Set<Position> retained = new HashSet<>();
                    if (active) for (BlockState state : retainedStates) {
                        Position cell = position(state.getBlock());
                        if (state.getBlockData().getAsString().equals(expected.get(cell))) retained.add(cell);
                    }
                    List<Block> renewed = new ArrayList<>();
                    for (Map.Entry<Position, String> entry : expected.entrySet()) {
                        Position cell = entry.getKey();
                        boolean unchanged = pendingFarmGrowth.remove(cell, token);
                        World world = worlds.apply(cell.world());
                        if (!active || !unchanged || !retained.contains(cell) || world == null
                            || !world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) continue;
                        Block current = world.getBlockAt(cell.x(), cell.y(), cell.z());
                        if (current.getBlockData().getAsString().equals(entry.getValue())) renewed.add(current);
                    }
                    change(renewed, true);
                } finally {
                    expected.keySet().forEach(cell -> pendingFarmGrowth.remove(cell, token));
                    queuedFarmGrowth--;
                }
            });
        } catch (RuntimeException error) {
            expected.keySet().forEach(cell -> pendingFarmGrowth.remove(cell, token));
            queuedFarmGrowth--;
            throw error;
        }
    }

    private static boolean newFarmGrowth(BlockState state) {
        Block block = state.getBlock();
        if (RENEWABLE_FARM_MATERIALS.contains(state.getType().name()) && block.getType() != state.getType()) return true;
        if (block.getType() != state.getType()) return false;
        String material = state.getType().name();
        if ((material.equals("CAVE_VINES") || material.equals("CAVE_VINES_PLANT"))
            && block.getBlockData() instanceof CaveVinesPlant before
            && state.getBlockData() instanceof CaveVinesPlant after) {
            return !before.hasBerries() && after.hasBerries();
        }
        if (material.equals("SWEET_BERRY_BUSH") && block.getBlockData() instanceof Ageable before
            && state.getBlockData() instanceof Ageable after) {
            return after.getAge() >= 2 && after.getAge() > before.getAge()
                && after.getAge() <= after.getMaximumAge();
        }
        return false;
    }

    private void queueTrees(Block origin, Supplier<List<BlockState>> finalStates, BooleanSupplier accepted) {
        mainThread();
        Map<Position, String> expected = new HashMap<>();
        for (BlockState state : finalStates.get()) {
            // Existing logs must not be renewed merely because a tree grew around them.
            if (ResourceMaterials.log(state.getType().name()) && !ResourceMaterials.log(state.getBlock().getType().name())) {
                expected.put(position(state.getBlock()), state.getBlockData().getAsString());
            }
        }
        if (expected.isEmpty()) return;
        UUID token = UUID.randomUUID();
        Position source = position(origin);
        growthBatches.put(source, token);
        expected.keySet().forEach(cell -> pendingTrees.put(cell, token));
        nextTick.accept(() -> {
            boolean active = token.equals(growthBatches.get(source)) && accepted.getAsBoolean();
            Set<Position> retained = new HashSet<>();
            if (active) for (BlockState state : finalStates.get()) {
                Position cell = position(state.getBlock());
                if (state.getBlockData().getAsString().equals(expected.get(cell))) retained.add(cell);
            }
            List<Block> renewed = new ArrayList<>();
            expected.forEach((cell, data) -> {
                boolean unchanged = pendingTrees.remove(cell, token);
                var world = worlds.apply(cell.world());
                if (!active || !unchanged || !retained.contains(cell) || world == null || !world.isChunkLoaded(cell.x() >> 4, cell.z() >> 4)) return;
                Block block = world.getBlockAt(cell.x(), cell.y(), cell.z());
                if (block.getBlockData().getAsString().equals(data)) renewed.add(block);
            });
            growthBatches.remove(source, token);
            change(renewed, true);
        });
    }

    private void change(Collection<Block> blocks, boolean renew) {
        mainThread();
        Map<Section, List<Block>> groups = new HashMap<>();
        for (Block block : blocks) {
            pendingTrees.remove(position(block));
            pendingGenerators.remove(position(block));
            pendingFarmGrowth.remove(position(block));
            groups.computeIfAbsent(section(block), ignored -> new ArrayList<>()).add(block);
        }
        for (List<Block> group : groups.values()) {
            Block representative = group.getFirst();
            try {
                ResourceLedger ledger = read(representative);
                List<ResourceLedger.Cell> cells = group.stream().map(ResourceProvenanceService::cell).toList();
                ResourceLedger changed = renew ? ledger.withRenewed(cells) : ledger.withBlocked(cells);
                if (changed != ledger) write(representative, changed);
            } catch (RuntimeException error) { fail(representative, error); }
        }
    }

    private ResourceLedger read(Block block) {
        if (failed.contains(section(block))) throw new IllegalStateException("Resource section is quarantined");
        var data = block.getChunk().getPersistentDataContainer();
        NamespacedKey key = key(block.getY());
        if (!data.has(key)) return ResourceLedger.empty();
        long[] stored = data.get(key, PersistentDataType.LONG_ARRAY);
        if (stored == null || stored.length == 0 || stored.length > 4097 || stored[0] != SCHEMA) {
            throw new IllegalStateException("Invalid resource provenance schema or size");
        }
        int sectionY = Math.floorDiv(block.getY(), 16);
        for (int index = 1; index < stored.length; index++) {
            long encodedY = stored[index] >> 8;
            if (encodedY < Integer.MIN_VALUE || encodedY > Integer.MAX_VALUE
                || Math.floorDiv((int) encodedY, 16) != sectionY) {
                throw new IllegalStateException("Resource marker belongs to another section");
            }
        }
        return ResourceLedger.from(Arrays.copyOfRange(stored, 1, stored.length));
    }

    private void write(Block block, ResourceLedger ledger) {
        long[] cells = ledger.serialize();
        long[] stored = new long[cells.length + 1];
        stored[0] = SCHEMA;
        System.arraycopy(cells, 0, stored, 1, cells.length);
        // Bound work to a 16x16x16 section instead of rewriting the whole world or chunk.
        block.getChunk().getPersistentDataContainer().set(key(block.getY()), PersistentDataType.LONG_ARRAY, stored);
    }

    private NamespacedKey key(int y) {
        return keys.computeIfAbsent(Math.floorDiv(y, 16), section -> keyFactory.apply("resource_cells_" + section));
    }
    private static ResourceLedger.Cell cell(Block block) { return new ResourceLedger.Cell(block.getX() & 15, block.getY(), block.getZ() & 15); }
    private static Section section(Block block) {
        return new Section(block.getWorld().getUID(), block.getX() >> 4, Math.floorDiv(block.getY(), 16), block.getZ() >> 4);
    }
    private static Position position(Block block) { return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    private void fail(Block block, RuntimeException error) {
        Section id = section(block);
        if (failed.add(id)) logger.log(Level.SEVERE, "Resource rewards disabled for section " + id + "; preserve its chunk data for review", error);
    }
    private void mainThread() { if (!isMainThread.getAsBoolean()) throw new IllegalStateException("Resource provenance requires the main server thread"); }
    private record Section(UUID world, int x, int y, int z) { }
    private record Position(UUID world, int x, int y, int z) { }
}

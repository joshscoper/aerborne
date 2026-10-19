package net.nerdsmp.paper.digsite;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.zone.ZoneDefinition;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import static net.nerdsmp.paper.digsite.DigSitePolicy.*;

/** Staff-captured public digging nodes. Never generates terrain or loads a chunk. */
public final class DigSiteService implements Listener, AutoCloseable {
    private static final int CHECKS_PER_PULSE = 128;
    private static final int MAXIMUM_COOLDOWN_SECONDS = 3600;
    private final NerdSmpPlugin plugin;
    private final DigSiteStore store;
    private final Map<Position, Cell> cells = new HashMap<>();
    private final Set<CellId> released = new HashSet<>();
    private final Set<CellId> quarantined = new HashSet<>();
    private final Map<Position, Attempt> pending = new HashMap<>();
    private List<Cell> scan = List.of();
    private int cursor;
    private BukkitTask task;

    public DigSiteService(NerdSmpPlugin plugin, DigSiteStore store) {
        this.plugin = Objects.requireNonNull(plugin);
        this.store = Objects.requireNonNull(store);
        rebuild();
    }

    public DigSiteStore store() { return store; }

    public void start() {
        mainThread();
        if (task != null) return;
        // Startup does not synchronously touch thousands of blocks. Ready snapshots
        // are released as the bounded loaded-chunk sweep encounters them.
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, 1L, 10L);
    }

    @Override public void close() {
        mainThread();
        if (task != null) task.cancel();
        task = null;
        pending.clear();
    }

    /** Save the catalog before any resource provenance is released. */
    public void register(DigSiteDefinition definition) throws IOException {
        mainThread();
        validateAuthoring(definition);
        validateBlockData(definition);
        store.find(definition.id()).ifPresent(previous -> {
            if (previous.generation().equals(definition.generation())
                && (!previous.nodes().equals(definition.nodes()) || !previous.worldId().equals(definition.worldId())))
                throw new IllegalArgumentException("A recaptured dig site requires a fresh generation UUID.");
        });
        store.put(definition);
        rebuild();
        if (definition.enabled()) for (DigSiteDefinition.Node node : definition.nodes()) {
            Cell cell = cells.get(new Position(definition.worldId(), node.x(), node.y(), node.z()));
            Block block = loadedBlock(cell);
            if (block != null && active(cell) && state(cell, block) == BlockState.EXPECTED
                && read(cell, block).state() == MarkerState.ABSENT) release(cell, block);
        }
    }

    public void setEnabled(String id, boolean enabled) throws IOException {
        mainThread();
        DigSiteDefinition site = store.find(id).orElseThrow(() -> new IllegalArgumentException("No dig site named " + id + "."));
        DigSiteDefinition updated = new DigSiteDefinition(site.id(), site.zoneId(), site.worldId(), site.generation(),
            enabled, site.cooldownSeconds(), site.nodes());
        if (enabled) validateAuthoring(updated);
        store.put(updated);
        rebuild();
    }

    public void remove(String id) throws IOException {
        mainThread();
        store.remove(id);
        rebuild();
    }

    /** Read-only eligibility: several protection/reward listeners may ask about one event. */
    public boolean mayHarvest(BlockBreakEvent event) {
        mainThread();
        if (event.isCancelled() || !event.isDropItems() || event.getPlayer().isDead()
            || event.getPlayer().getGameMode() != GameMode.SURVIVAL
            || plugin.sessions().account(event.getPlayer().getUniqueId()).isEmpty()) return false;
        Cell cell = cells.get(position(event.getBlock()));
        if (cell == null || !active(cell) || pending.containsKey(cell.position()) || quarantined.contains(cell.id())
            || !released.contains(cell.id())) return false;
        Marker marker = read(cell, event.getBlock());
        return decide(true, quarantined.contains(cell.id()), marker, state(cell, event.getBlock()), false,
            System.currentTimeMillis()) == Decision.READY;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void guardBreak(BlockBreakEvent event) {
        Cell cell = cells.get(position(event.getBlock()));
        if (cell != null && active(cell) && !mayHarvest(event)) {
            event.setCancelled(true);
            event.getPlayer().sendRichMessage("<yellow>This dig node is not ready. Staff can disable its dig site to edit it.</yellow>");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordBreak(BlockBreakEvent event) {
        Cell cell = cells.get(position(event.getBlock()));
        if (!mayHarvest(event)) {
            // A listener after the protection guard may suppress drops. Do not let
            // that deplete an active node without its persistent renewal marker.
            if (cell != null && active(cell) && !event.isDropItems()) event.setCancelled(true);
            return;
        }
        long due = due(System.currentTimeMillis(), cell.site().cooldownSeconds());
        try {
            event.getBlock().getChunk().getPersistentDataContainer().set(cell.key(), PersistentDataType.LONG, due);
        } catch (RuntimeException failure) {
            fail(cell, failure);
            event.setCancelled(true);
            return;
        }
        released.remove(cell.id());
        Attempt attempt = new Attempt(UUID.randomUUID(), cell, event);
        pending.put(cell.position(), attempt);
        Bukkit.getScheduler().runTask(plugin, () -> finish(attempt));
    }

    private void finish(Attempt attempt) {
        if (!pending.remove(attempt.cell().position(), attempt)) return;
        Cell cell = attempt.cell();
        Block block = loadedBlock(cell);
        if (block == null) return; // Persisted cooldown handles the eventual loaded-chunk visit.
        if ((attempt.event().isCancelled() || !attempt.event().isDropItems())
            && state(cell, block) == BlockState.EXPECTED) {
            try {
                block.getChunk().getPersistentDataContainer().remove(cell.key());
                Cell current = cells.get(cell.position());
                if (current != null && current.id().equals(cell.id()) && active(current)
                    && state(current, block) == BlockState.EXPECTED) release(current, block);
            } catch (RuntimeException failure) { fail(cell, failure); }
        }
    }

    private void pulse() {
        mainThread();
        int checks = Math.min(CHECKS_PER_PULSE, scan.size());
        for (int checked = 0; checked < checks; checked++) {
            if (cursor >= scan.size()) cursor = 0;
            Cell cell = scan.get(cursor++);
            if (!active(cell) || pending.containsKey(cell.position()) || quarantined.contains(cell.id())) continue;
            Block block = loadedBlock(cell);
            if (block == null) continue;
            Marker marker = read(cell, block);
            BlockState state = state(cell, block);
            long now = System.currentTimeMillis();
            boolean occupied = marker.state() == MarkerState.PRESENT && marker.due() <= now
                && state == BlockState.AIR && occupied(block);
            Decision decision = decide(true, quarantined.contains(cell.id()), marker, state, occupied, now);
            try {
                if (decision == Decision.READY) {
                    if (!released.contains(cell.id())) release(cell, block);
                } else if (decision == Decision.RESTORE) {
                    // Recheck everything immediately before mutation; foreign builds are sacred.
                    if (!active(cell) || block.getType() != Material.AIR || occupied(block)) continue;
                    block.setBlockData(cell.data().clone(), false);
                    if (state(cell, block) != BlockState.EXPECTED) { fail(cell, new IllegalStateException("Dig-node restoration changed its snapshot")); continue; }
                    plugin.resources().renewManaged(block, Material.valueOf(cell.node().material()));
                    block.getChunk().getPersistentDataContainer().remove(cell.key());
                    released.add(cell.id());
                } else if (decision == Decision.RELEASE_RETAINED) {
                    // The event was never committed before a restart. Do not replace the block.
                    plugin.resources().renewManaged(block, Material.valueOf(cell.node().material()));
                    block.getChunk().getPersistentDataContainer().remove(cell.key());
                    released.add(cell.id());
                }
            } catch (RuntimeException failure) { fail(cell, failure); }
        }
    }

    public Status status(String id) {
        mainThread();
        DigSiteDefinition site = store.find(id).orElseThrow(() -> new IllegalArgumentException("No dig site named " + id + "."));
        int loaded = 0, ready = 0, cooling = 0, blocked = 0, inactive = 0;
        for (DigSiteDefinition.Node node : site.nodes()) {
            Cell cell = cells.get(new Position(site.worldId(), node.x(), node.y(), node.z()));
            if (!active(cell)) { inactive++; continue; }
            Block block = loadedBlock(cell);
            if (block == null) continue;
            loaded++;
            Decision result = decide(true, quarantined.contains(cell.id()), read(cell, block), state(cell, block), false, System.currentTimeMillis());
            if (result == Decision.READY && !pending.containsKey(cell.position()) && released.contains(cell.id())) ready++;
            else if (result == Decision.WAITING || result == Decision.RESTORE || result == Decision.RELEASE_RETAINED) cooling++;
            else blocked++;
        }
        return new Status(site.nodes().size(), loaded, ready, cooling, blocked, inactive);
    }

    public record Status(int nodes, int loaded, int ready, int cooling, int blocked, int inactive) { }

    private void release(Cell cell, Block block) {
        plugin.resources().renewManaged(block, Material.valueOf(cell.node().material()));
        released.add(cell.id());
    }

    private Marker read(Cell cell, Block block) {
        try {
            PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
            boolean present = data.has(cell.key());
            boolean type = data.has(cell.key(), PersistentDataType.LONG);
            Marker marker = marker(present, type, type ? data.get(cell.key(), PersistentDataType.LONG) : null,
                System.currentTimeMillis(), MAXIMUM_COOLDOWN_SECONDS);
            if (marker.state() == MarkerState.CORRUPT) fail(cell, new IllegalStateException("Invalid dig-node cooldown type or timestamp"));
            return marker;
        } catch (RuntimeException failure) {
            fail(cell, failure);
            return new Marker(MarkerState.CORRUPT, 0);
        }
    }

    private void fail(Cell cell, RuntimeException failure) {
        if (quarantined.add(cell.id())) plugin.getLogger().log(Level.SEVERE,
            "Dig node quarantined at " + cell.position() + " in " + cell.site().id() + "; preserve chunk data for staff review", failure);
    }

    private void rebuild() {
        Map<Position, Cell> updated = new HashMap<>();
        for (DigSiteDefinition site : store.all()) for (DigSiteDefinition.Node node : site.nodes()) {
            BlockData blockData = parse(node);
            Position position = new Position(site.worldId(), node.x(), node.y(), node.z());
            CellId id = new CellId(position, site.generation());
            String marker = markerKey(site.generation(), node.x(), node.y(), node.z());
            updated.put(position, new Cell(site, node, position, id, new NamespacedKey(plugin, marker), blockData));
        }
        cells.clear(); cells.putAll(updated);
        scan = updated.values().stream().sorted(Comparator.comparing((Cell cell) -> cell.site().id())
            .thenComparingInt(cell -> cell.node().x()).thenComparingInt(cell -> cell.node().y())
            .thenComparingInt(cell -> cell.node().z())).toList();
        cursor = 0;
        Set<CellId> valid = new HashSet<>(scan.stream().map(Cell::id).toList());
        released.retainAll(valid); quarantined.retainAll(valid);
    }

    private void validateAuthoring(DigSiteDefinition site) {
        World world = Bukkit.getWorld(site.worldId());
        if (world == null || plugin.skyblock() != null && plugin.skyblock().isIslandWorld(world))
            throw new IllegalArgumentException("Dig sites belong in loaded public worlds, not private island worlds.");
        ZoneDefinition zone = plugin.zones().find(site.zoneId()).orElseThrow(() -> new IllegalArgumentException("Choose an existing zone."));
        if (!zone.enabled() || !zone.worldId().equals(site.worldId())) throw new IllegalArgumentException("Choose an enabled zone in this world.");
        for (DigSiteDefinition.Node node : site.nodes()) if (!zone.bounds().contains(node.x(), node.y(), node.z())
            || node.y() < world.getMinHeight() || node.y() >= world.getMaxHeight())
            throw new IllegalArgumentException("Every dig node must be inside the current zone and world height.");
    }

    private static void validateBlockData(DigSiteDefinition site) { site.nodes().forEach(DigSiteService::parse); }

    private static BlockData parse(DigSiteDefinition.Node node) {
        BlockData data = Bukkit.createBlockData(node.blockData());
        if (data.getMaterial() != Material.valueOf(node.material()) || !data.getAsString().equals(node.blockData()))
            throw new IllegalArgumentException("Dig nodes require exact canonical block data for " + node.material() + ".");
        return data;
    }

    private boolean active(Cell cell) {
        if (cell == null || !cell.site().enabled()) return false;
        World world = Bukkit.getWorld(cell.position().world());
        if (world == null || plugin.skyblock() != null && plugin.skyblock().isIslandWorld(world)) return false;
        ZoneDefinition zone = plugin.zones().find(cell.site().zoneId()).orElse(null);
        return zone != null && zone.enabled() && zone.worldId().equals(cell.site().worldId())
            && zone.bounds().contains(cell.node().x(), cell.node().y(), cell.node().z());
    }

    private Block loadedBlock(Cell cell) {
        if (cell == null) return null;
        World world = Bukkit.getWorld(cell.position().world());
        if (world == null || !world.isChunkLoaded(cell.node().x() >> 4, cell.node().z() >> 4)) return null;
        return world.getBlockAt(cell.node().x(), cell.node().y(), cell.node().z());
    }

    private static BlockState state(Cell cell, Block block) {
        if (block.getType() == Material.AIR) return BlockState.AIR;
        return block.getBlockData().getAsString().equals(cell.node().blockData()) ? BlockState.EXPECTED : BlockState.FOREIGN;
    }

    private static boolean occupied(Block block) {
        BoundingBox bounds = new BoundingBox(block.getX(), block.getY(), block.getZ(), block.getX()+1D, block.getY()+1D, block.getZ()+1D);
        return !block.getWorld().getNearbyEntities(bounds,
            entity -> (entity instanceof LivingEntity || entity instanceof Vehicle) && entity.getBoundingBox().overlaps(bounds)).isEmpty();
    }

    private boolean protectedCell(Block block) { return block != null && active(cells.get(position(block))); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        boolean affected = event instanceof BlockMultiPlaceEvent multiple
            ? multiple.getReplacedBlockStates().stream().anyMatch(state -> protectedCell(state.getBlock()))
            : protectedCell(event.getBlock());
        if (affected) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pistonExtend(BlockPistonExtendEvent event) {
        if (protectedCell(event.getBlock()) || protectedCell(event.getBlock().getRelative(event.getDirection()))
            || pistonTouches(event.getBlocks(), event.getDirection())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pistonRetract(BlockPistonRetractEvent event) {
        BlockFace direction = event.getBlock().getBlockData() instanceof Directional piston
            ? piston.getFacing().getOppositeFace() : event.getDirection();
        if (protectedCell(event.getBlock()) || pistonTouches(event.getBlocks(), direction)) event.setCancelled(true);
    }

    private boolean pistonTouches(List<Block> blocks, BlockFace direction) {
        return blocks.stream().anyMatch(block -> protectedCell(block) || protectedCell(block.getRelative(direction)));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockExplosion(BlockExplodeEvent event) { event.blockList().removeIf(this::protectedCell); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityExplosion(EntityExplodeEvent event) { event.blockList().removeIf(this::protectedCell); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fluid(BlockFromToEvent event) { if (protectedCell(event.getBlock()) || protectedCell(event.getToBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityChange(EntityChangeBlockEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spread(BlockSpreadEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void grow(BlockGrowEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fade(BlockFadeEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fertilize(BlockFertilizeEvent event) {
        if (event.getBlocks().stream().anyMatch(state -> protectedCell(state.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void tree(StructureGrowEvent event) {
        if (event.getBlocks().stream().anyMatch(state -> protectedCell(state.getBlock()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void form(BlockFormEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void burn(BlockBurnEvent event) { if (protectedCell(event.getBlock())) event.setCancelled(true); }

    private static Position position(Block block) { return new Position(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    private static void mainThread() { if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Dig sites require the main server thread"); }
    private record Position(UUID world, int x, int y, int z) { }
    private record CellId(Position position, UUID generation) { }
    private record Cell(DigSiteDefinition site, DigSiteDefinition.Node node, Position position, CellId id, NamespacedKey key, BlockData data) { }
    private record Attempt(UUID token, Cell cell, BlockBreakEvent event) { }
}

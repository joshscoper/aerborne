package net.nerdsmp.paper.grave;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.BlockState;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GraveManager implements Listener {
    private final JavaPlugin plugin;
    private final SessionRegistry sessions;
    private final Duration lifetime;
    private final NamespacedKey ownerKey;
    private final NamespacedKey expiresKey;
    private final Map<AccountId, List<GraveRecord>> graves = new ConcurrentHashMap<>();

    public GraveManager(JavaPlugin plugin, SessionRegistry sessions, Duration lifetime) {
        this(plugin, sessions, lifetime, new NamespacedKey(plugin, "grave_owner"),
            new NamespacedKey(plugin, "grave_expires_at"));
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireGraves, 20L * 60L, 20L * 60L);
    }

    GraveManager(JavaPlugin plugin, SessionRegistry sessions, Duration lifetime,
                 NamespacedKey ownerKey, NamespacedKey expiresKey) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.lifetime = lifetime;
        this.ownerKey = ownerKey;
        this.expiresKey = expiresKey;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (event.getKeepInventory() || event.getDrops().isEmpty()) {
            return;
        }
        AccountId owner = sessions.account(event.getPlayer().getUniqueId()).orElse(null);
        if (owner == null) {
            return;
        }

        List<ItemStack> items = event.getDrops().stream().map(ItemStack::clone).toList();
        Location location = safeLocation(event.getPlayer().getLocation());
        if (plugin instanceof net.nerdsmp.paper.NerdSmpPlugin nerd && nerd.skyblock() != null)
            location = nerd.skyblock().graveLocation(location);
        Optional<GraveRecord> grave = createGrave(owner, location, items);
        if (grave.isEmpty()) {
            plugin.getLogger().warning("Could not create grave for " + event.getPlayer().getName() + "; leaving normal drops.");
            return;
        }
        event.getDrops().clear();
        graves.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(grave.get());
        event.getPlayer().sendRichMessage("<gold>Your items are protected in a grave at <white>"
            + coordinates(location) + "</white> for " + lifetime.toHours() + " hours.</gold>");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !isGrave(event.getInventory())) {
            return;
        }
        Container container = (Container) event.getInventory().getHolder();
        AccountId owner = graveOwner(container).orElse(null);
        AccountId actor = sessions.account(player.getUniqueId()).orElse(null);
        if (owner == null || !owner.equals(actor)) {
            event.setCancelled(true);
            player.sendRichMessage("<red>This grave belongs to another player.</red>");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGraveBreak(BlockBreakEvent event) {
        if (!isGrave(event.getBlock().getState())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendRichMessage("<yellow>Open the grave and remove its contents instead.</yellow>");
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Container container) || graveOwner(container).isEmpty()) {
            return;
        }
        AccountId owner = graveOwner(container).orElseThrow();
        Long expiry = container.getPersistentDataContainer().get(expiresKey, PersistentDataType.LONG);
        if (event.getInventory().isEmpty() && expiry != null
            && container.getBlock().getState() instanceof Container current
            && matchesGrave(current, owner, expiry)) {
            removeGraveBlock(container.getBlock());
            List<GraveRecord> records = graves.get(owner);
            if (records != null) {
                records.removeIf(record -> record.location.equals(container.getLocation())
                    && record.expiresAt.toEpochMilli() == expiry);
                if (records.isEmpty()) {
                    graves.remove(owner, records);
                }
            }
        }
    }

    public Optional<Location> latestLocation(AccountId accountId) {
        return graves.getOrDefault(accountId, List.of()).stream()
            .filter(record -> record.expiresAt.isAfter(Instant.now()))
            .max(Comparator.comparing(GraveRecord::createdAt))
            .map(record -> record.location.clone());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (isGrave(event.getSource()) || isGrave(event.getDestination())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickup(InventoryPickupItemEvent event) {
        if (isGrave(event.getInventory())) {
            event.setCancelled(true);
        }
    }

    private boolean isGrave(Inventory inventory) {
        return inventory.getHolder() instanceof Container container && isGrave(container);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!mayUseInventory(event.getView().getTopInventory(), event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!mayUseInventory(event.getView().getTopInventory(), event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private boolean mayUseInventory(Inventory inventory, UUID playerId) {
        if (!isGrave(inventory)) {
            return true;
        }
        AccountId actor = sessions.account(playerId).orElse(null);
        return actor != null && graveOwner((Container) inventory.getHolder()).filter(actor::equals).isPresent();
    }

    private boolean isGrave(BlockState state) {
        return state instanceof Container container
            && container.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isGrave(block.getState()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isGrave(block.getState()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (isGrave(event.getBlock().getState())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (isGrave(event.getBlock().getState())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (isGrave(event.getBlock().getState())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (isGrave(event.getToBlock().getState())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isGrave(event.getBlockReplacedState()) || event instanceof BlockMultiPlaceEvent multi
            && multi.getReplacedBlockStates().stream().anyMatch(this::isGrave)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (isGrave(event.getBlock().getRelative(event.getDirection()).getState())
            || touchesGrave(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        BlockFace facing = event.getBlock().getBlockData() instanceof Directional directional
            ? directional.getFacing() : event.getDirection().getOppositeFace();
        if (isGrave(event.getBlock().getRelative(facing).getState())
            || touchesGrave(event.getBlocks(), facing.getOppositeFace())) {
            event.setCancelled(true);
        }
    }

    private boolean touchesGrave(List<Block> blocks, BlockFace direction) {
        return blocks.stream().anyMatch(block -> isGrave(block.getState())
            || isGrave(block.getRelative(direction).getState()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        event.getBlocks().removeIf(state -> isGrave(state.getBlock().getState()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTreeGrow(StructureGrowEvent event) {
        event.getBlocks().removeIf(state -> isGrave(state.getBlock().getState()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (event.getBlocks().stream().anyMatch(state -> isGrave(state.getBlock().getState()))) {
            event.setCancelled(true);
        }
    }

    public RecoveryResult recoverLatest(AccountId accountId, Player player) {
        Optional<GraveRecord> recordOptional = graves.getOrDefault(accountId, List.of()).stream()
            .filter(record -> record.expiresAt.isAfter(Instant.now()))
            .max(Comparator.comparing(GraveRecord::createdAt));
        if (recordOptional.isEmpty()) {
            return RecoveryResult.NOT_FOUND;
        }
        GraveRecord record = recordOptional.get();
        Block block = record.location.getBlock();
        if (!(block.getState() instanceof Container container)
            || !matchesGrave(container, accountId, record.expiresAt.toEpochMilli())) {
            removeRecord(accountId, record);
            return RecoveryResult.NOT_FOUND;
        }
        Inventory inventory = container.getInventory();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
            overflow.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        inventory.clear();
        removeGraveBlock(block);
        removeRecord(accountId, record);
        return RecoveryResult.RECOVERED;
    }

    public boolean isGraveOwner(Block block, AccountId accountId) {
        return block.getState() instanceof Container container
            && graveOwner(container).filter(accountId::equals).isPresent();
    }

    private Optional<GraveRecord> createGrave(AccountId owner, Location location, List<ItemStack> items) {
        Block block = findPlacement(location);
        if (block == null) {
            return Optional.empty();
        }
        block.setType(Material.BARREL, false);
        if (!(block.getState() instanceof Container container)) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        Instant expiresAt = now.plus(lifetime);
        PersistentDataContainer data = container.getPersistentDataContainer();
        data.set(ownerKey, PersistentDataType.STRING, owner.toString());
        data.set(expiresKey, PersistentDataType.LONG, expiresAt.toEpochMilli());
        container.customName(net.kyori.adventure.text.Component.text("Protected Grave"));
        container.update(true, false);
        items.forEach(item -> {
            Map<Integer, ItemStack> overflow = container.getInventory().addItem(item);
            overflow.values().forEach(leftover -> block.getWorld().dropItemNaturally(block.getLocation(), leftover));
        });
        return Optional.of(new GraveRecord(block.getLocation(), now, expiresAt));
    }

    private Block findPlacement(Location preferred) {
        World world = preferred.getWorld();
        Location safe = preferred.clone();
        if (safe.getBlockY() <= world.getMinHeight() + 1) {
            safe.setY(world.getHighestBlockYAt(safe) + 1.0D);
        }
        for (int dy = 0; dy <= 4; dy++) {
            Block candidate = world.getBlockAt(safe.getBlockX(), safe.getBlockY() + dy, safe.getBlockZ());
            if (candidate.isEmpty() || candidate.isLiquid() || candidate.isReplaceable()) {
                return candidate;
            }
        }
        Block surface = world.getHighestBlockAt(safe).getRelative(0, 1, 0);
        return surface.isEmpty() || surface.isReplaceable() ? surface : null;
    }

    private Location safeLocation(Location location) {
        Location safe = location.clone();
        if (safe.getBlockY() <= safe.getWorld().getMinHeight() + 1) {
            safe.setY(safe.getWorld().getHighestBlockYAt(safe) + 1.0D);
        }
        return safe;
    }

    private Optional<AccountId> graveOwner(Container container) {
        String raw = container.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(AccountId.parse(raw));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private boolean matchesGrave(Container container, AccountId owner, long expiry) {
        Long storedExpiry = container.getPersistentDataContainer().get(expiresKey, PersistentDataType.LONG);
        return graveOwner(container).filter(owner::equals).isPresent()
            && storedExpiry != null && storedExpiry == expiry;
    }

    private void expireGraves() {
        Instant now = Instant.now();
        graves.forEach((owner, records) -> records.removeIf(record -> {
            if (record.expiresAt.isAfter(now)) {
                return false;
            }
            Block block = record.location.getBlock();
            if (block.getState() instanceof Container container
                && matchesGrave(container, owner, record.expiresAt.toEpochMilli())) {
                for (ItemStack item : container.getInventory().getContents()) {
                    if (item != null && !item.getType().isAir()) {
                        block.getWorld().dropItemNaturally(block.getLocation(), item);
                    }
                }
                removeGraveBlock(block);
            }
            return true;
        }));
    }

    private void removeRecord(AccountId accountId, GraveRecord record) {
        List<GraveRecord> records = graves.get(accountId);
        if (records == null) {
            return;
        }
        records.remove(record);
        if (records.isEmpty()) {
            graves.remove(accountId, records);
        }
    }

    private void removeGraveBlock(Block block) {
        block.setType(Material.AIR, false);
    }

    private String coordinates(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + ", "
            + location.getBlockY() + ", " + location.getBlockZ();
    }

    public enum RecoveryResult {
        RECOVERED,
        NOT_FOUND
    }

    private record GraveRecord(Location location, Instant createdAt, Instant expiresAt) {
        private GraveRecord {
            location = location.clone();
        }
    }
}

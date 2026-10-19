package net.nerdsmp.paper.grave;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GraveManagerProtectionTest {
    private static final NamespacedKey OWNER = new NamespacedKey("nerdsmp", "grave_owner");
    private static final NamespacedKey EXPIRY = new NamespacedKey("nerdsmp", "grave_expires_at");
    private final SessionRegistry sessions = new SessionRegistry();
    private final GraveManager manager = new GraveManager(null, sessions, Duration.ofHours(24), OWNER, EXPIRY);
    private final AccountId owner = AccountId.random();

    @Test
    void hoppersCannotTakeFromOrInsertIntoAGrave() {
        GraveBlock grave = new GraveBlock(owner, Instant.now().plusSeconds(600).toEpochMilli());
        Inventory ordinary = ordinaryInventory();
        InventoryMoveItemEvent taking = new InventoryMoveItemEvent(grave.inventory, new EventItem(), ordinary, false);
        InventoryMoveItemEvent inserting = new InventoryMoveItemEvent(ordinary, new EventItem(), grave.inventory, true);

        manager.onInventoryMove(taking);
        manager.onInventoryMove(inserting);

        assertTrue(taking.isCancelled());
        assertTrue(inserting.isCancelled());
    }

    @Test
    void ordinaryStorageAutomationStillWorks() {
        InventoryMoveItemEvent event = new InventoryMoveItemEvent(ordinaryInventory(), new EventItem(),
            ordinaryInventory(), true);
        manager.onInventoryMove(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void onlyTheAuthenticatedOwnerCanOpenAGrave() {
        GraveBlock grave = new GraveBlock(owner, Instant.now().plusSeconds(600).toEpochMilli());
        Player ownerPlayer = player(owner);
        Player stranger = player(AccountId.random());
        InventoryOpenEvent own = new InventoryOpenEvent(view(grave.inventory, ownerPlayer));
        InventoryOpenEvent other = new InventoryOpenEvent(view(grave.inventory, stranger));

        manager.onInventoryOpen(own);
        manager.onInventoryOpen(other);

        assertFalse(own.isCancelled());
        assertTrue(other.isCancelled());
    }

    @Test
    void malformedGraveOwnershipFailsClosed() {
        GraveBlock grave = new GraveBlock(owner, Instant.now().plusSeconds(600).toEpochMilli());
        grave.metadata.put(OWNER, "not-an-account");
        InventoryOpenEvent event = new InventoryOpenEvent(view(grave.inventory, player(owner)));
        manager.onInventoryOpen(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void explosionsRemoveOnlyTheGraveFromTheirDestructionList() {
        GraveBlock grave = new GraveBlock(owner, Instant.now().plusSeconds(600).toEpochMilli());
        Block ordinary = proxy(Block.class, (instance, method, args) -> switch (method.getName()) {
            case "getState" -> proxy(BlockState.class, (state, call, arguments) -> null);
            case "equals" -> instance == args[0];
            default -> null;
        });
        BlockExplodeEvent event = new BlockExplodeEvent(ordinary, ordinary.getState(),
            new ArrayList<>(List.of(ordinary, grave.block)), 1.0F, ExplosionResult.DESTROY);
        manager.onBlockExplode(event);
        assertEquals(List.of(ordinary), event.blockList());
    }

    @Test
    void aPistonCannotMoveAGraveEvenWithinTheSameTerritory() {
        GraveBlock grave = new GraveBlock(owner, Instant.now().plusSeconds(600).toEpochMilli());
        BlockPistonExtendEvent event = new BlockPistonExtendEvent(grave.block, List.of(grave.block), BlockFace.EAST);
        manager.onPistonExtend(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void aStaleRecordCannotRecoverAnotherPlayersReplacementGrave() throws ReflectiveOperationException {
        Instant expiry = Instant.now().plusSeconds(600);
        GraveBlock replacement = new GraveBlock(AccountId.random(), expiry.toEpochMilli());
        record(owner, replacement, expiry);

        assertEquals(GraveManager.RecoveryResult.NOT_FOUND, manager.recoverLatest(owner, player(owner)));
        assertEquals(Material.BARREL, replacement.material);
        assertFalse(replacement.cleared);
    }

    @Test
    void aStaleRecordCannotRecoverTheSameOwnersNewerGrave() throws ReflectiveOperationException {
        Instant oldExpiry = Instant.now().plusSeconds(300);
        GraveBlock replacement = new GraveBlock(owner, oldExpiry.plusSeconds(300).toEpochMilli());
        record(owner, replacement, oldExpiry);

        assertEquals(GraveManager.RecoveryResult.NOT_FOUND, manager.recoverLatest(owner, player(owner)));
        assertEquals(Material.BARREL, replacement.material);
        assertFalse(replacement.cleared);
    }

    @Test
    void theMatchingOwnerStillRecoversTheirGrave() throws ReflectiveOperationException {
        Instant expiry = Instant.now().plusSeconds(600);
        GraveBlock grave = new GraveBlock(owner, expiry.toEpochMilli());
        record(owner, grave, expiry);

        assertEquals(GraveManager.RecoveryResult.RECOVERED, manager.recoverLatest(owner, player(owner)));
        assertEquals(Material.AIR, grave.material);
        assertTrue(grave.cleared);
        assertTrue(manager.latestLocation(owner).isEmpty());
    }

    @Test
    void anOldExpiryCannotDeleteAReplacementAtTheSameLocation() throws ReflectiveOperationException {
        Instant oldExpiry = Instant.now().minusSeconds(60);
        GraveBlock replacement = new GraveBlock(owner, Instant.now().plusSeconds(600).toEpochMilli());
        record(owner, replacement, oldExpiry);

        expire();

        assertEquals(Material.BARREL, replacement.material);
        assertFalse(replacement.cleared);
    }

    @Test
    void theMatchingGraveStillExpiresNormally() throws ReflectiveOperationException {
        Instant expiry = Instant.now().minusSeconds(60);
        GraveBlock grave = new GraveBlock(owner, expiry.toEpochMilli());
        record(owner, grave, expiry);

        expire();

        assertEquals(Material.AIR, grave.material);
    }

    @Test
    void closingAnEmptiedGraveRemovesItsStaleLocationRecord() throws ReflectiveOperationException {
        Instant expiry = Instant.now().plusSeconds(600);
        GraveBlock grave = new GraveBlock(owner, expiry.toEpochMilli());
        record(owner, grave, expiry);

        manager.onInventoryClose(new InventoryCloseEvent(view(grave.inventory, player(owner))));

        assertEquals(Material.AIR, grave.material);
        assertTrue(manager.latestLocation(owner).isEmpty());
    }

    @SuppressWarnings("unchecked")
    private void record(AccountId account, GraveBlock block, Instant expiry) throws ReflectiveOperationException {
        Class<?> recordType = Class.forName(GraveManager.class.getName() + "$GraveRecord");
        var constructor = recordType.getDeclaredConstructor(Location.class, Instant.class, Instant.class);
        constructor.setAccessible(true);
        Object record = constructor.newInstance(block.location, expiry.minusSeconds(86_400), expiry);
        var field = GraveManager.class.getDeclaredField("graves");
        field.setAccessible(true);
        Map<AccountId, List<Object>> records = (Map<AccountId, List<Object>>) field.get(manager);
        records.computeIfAbsent(account, ignored -> new ArrayList<>()).add(record);
    }

    private void expire() throws ReflectiveOperationException {
        var method = GraveManager.class.getDeclaredMethod("expireGraves");
        method.setAccessible(true);
        method.invoke(manager);
    }

    private Inventory ordinaryInventory() {
        return proxy(Inventory.class, (instance, method, args) -> null);
    }

    private Player player(AccountId account) {
        UUID id = UUID.randomUUID();
        sessions.join(id, account);
        return proxy(Player.class, (instance, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "sendRichMessage" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private InventoryView view(Inventory inventory, Player player) {
        return proxy(InventoryView.class, (instance, method, args) -> switch (method.getName()) {
            case "getTopInventory" -> inventory;
            case "getPlayer" -> player;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    /** Only event identity is needed; avoid starting Paper's item registry in unit tests. */
    private static final class EventItem extends ItemStack {
        private EventItem() {
            super();
        }
    }

    private static final class GraveBlock {
        private final Map<NamespacedKey, Object> metadata = new HashMap<>();
        private final World world;
        private final Location location;
        private final Block block;
        private final Container container;
        private final Inventory inventory;
        private Material material = Material.BARREL;
        private boolean cleared;

        private GraveBlock(AccountId owner, long expiry) {
            metadata.put(OWNER, owner.toString());
            metadata.put(EXPIRY, expiry);
            UUID worldId = UUID.randomUUID();
            world = proxy(World.class, (instance, method, args) -> switch (method.getName()) {
                case "getUID" -> worldId;
                case "getBlockAt" -> currentBlock();
                case "equals" -> instance == args[0];
                case "hashCode" -> worldId.hashCode();
                default -> throw new UnsupportedOperationException(method.getName());
            });
            location = new Location(world, 10, 64, 10);
            PersistentDataContainer data = proxy(PersistentDataContainer.class,
                (instance, method, args) -> switch (method.getName()) {
                    case "get" -> metadata.get(args[0]);
                    case "has" -> metadata.containsKey(args[0]);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
            container = proxy(Container.class, (instance, method, args) -> switch (method.getName()) {
                case "getPersistentDataContainer" -> data;
                case "getBlock" -> currentBlock();
                case "getLocation" -> location;
                case "getInventory" -> currentInventory();
                default -> throw new UnsupportedOperationException(method.getName());
            });
            inventory = proxy(Inventory.class, (instance, method, args) -> switch (method.getName()) {
                case "getHolder" -> container;
                case "isEmpty" -> true;
                case "getContents" -> new ItemStack[0];
                case "clear" -> { cleared = true; yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
            block = proxy(Block.class, (instance, method, args) -> switch (method.getName()) {
                case "getState" -> container;
                case "getLocation" -> location;
                case "getRelative" -> currentBlock();
                case "setType" -> { material = (Material) args[0]; yield null; }
                case "equals" -> instance == args[0];
                case "hashCode" -> System.identityHashCode(instance);
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private Block currentBlock() { return block; }
        private Inventory currentInventory() { return inventory; }
    }
}

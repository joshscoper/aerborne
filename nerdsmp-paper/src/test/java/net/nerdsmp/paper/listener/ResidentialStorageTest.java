package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.service.InMemoryClaimService;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.event.inventory.*;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.inventory.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class ResidentialStorageTest {
    private final UUID worldId = UUID.randomUUID();
    private final AccountId owner = AccountId.random(), resident = AccountId.random(), neighbor = AccountId.random();
    private final SessionRegistry sessions = new SessionRegistry();
    private final InMemoryClaimService claims = new InMemoryClaimService(4);
    private final ClaimProtectionListener listener = new ClaimProtectionListener(claims, sessions, null);
    private final World world = proxy(World.class, (p, m, a) -> switch (m.getName()) {
        case "getUID" -> worldId;
        case "getBlockAt" -> block(a[0] instanceof Location location ? location.getBlockX() : (Integer) a[0]);
        case "equals" -> p == a[0];
        case "hashCode" -> worldId.hashCode();
        default -> throw new UnsupportedOperationException(m.getName());
    });
    ResidentialStorageTest() {
        claims.createClaim(owner, key(0)); claims.createClaim(owner, key(1));
        claims.setTrustAll(owner, resident, ClaimRole.RESIDENT); claims.setTrustAll(owner, neighbor, ClaimRole.RESIDENT);
        claims.assignSteward(owner, key(0), resident);
    }
    @Test void openChestLosesAccessImmediatelyWhenHomeIsReassigned() {
        var view = view(storage(1, false), player(resident));
        var open = new InventoryOpenEvent(view); listener.onOpenInventory(open); assertFalse(open.isCancelled());
        claims.assignSteward(owner, key(0), neighbor);
        var click = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 30, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        listener.onInventoryClick(click); assertTrue(click.isCancelled());
    }
    @Test void revokedLocalGuestCannotDragIntoPreviouslyOpenStorage() {
        claims.setTrust(resident, key(0), neighbor, ClaimRole.CONTAINER);
        var view = view(storage(1, false), player(neighbor));
        claims.resetTrust(resident, key(0), neighbor);
        var drag = new InventoryDragEvent(view, null, new EventItem(), false, Map.of());
        listener.onInventoryDrag(drag); assertTrue(drag.isCancelled());
    }
    @Test void ownerRetainsStorageAccessAndCustomMenusAreNotWorldStorage() {
        var own = new InventoryOpenEvent(view(storage(1, false), player(owner)));
        listener.onOpenInventory(own); assertFalse(own.isCancelled());
        Inventory menu = proxy(Inventory.class, (p, m, a) -> m.getName().equals("getHolder") ? null : 54);
        var event = new InventoryClickEvent(view(menu, player(neighbor)), InventoryType.SlotType.CONTAINER, 1, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        listener.onInventoryClick(event); assertFalse(event.isCancelled());
    }
    @Test void mobileHopperCannotEnterPrivateHomeAndExtractItsContents() {
        var take = new InventoryMoveItemEvent(storage(1, false), new EventItem(), storage(2, true), false);
        listener.onInventoryMove(take); assertTrue(take.isCancelled());
        var shared = new InventoryMoveItemEvent(storage(17, false), new EventItem(), storage(18, true), false);
        listener.onInventoryMove(shared); assertFalse(shared.isCancelled());
    }
    @Test void mobileHopperCannotPickupPrivateDropsButSharedFarmStillWorks() {
        var blocked = new InventoryPickupItemEvent(storage(2, true), item(2));
        listener.onInventoryPickup(blocked); assertTrue(blocked.isCancelled());
        var shared = new InventoryPickupItemEvent(storage(18, true), item(18));
        listener.onInventoryPickup(shared); assertFalse(shared.isCancelled());
    }
    @Test void stationaryHoppersRespectPrivateDomainsButCanSpanOneResidentsHome() {
        var shared = new InventoryMoveItemEvent(storage(15, false), new EventItem(), storage(16, false), true);
        listener.onInventoryMove(shared); assertTrue(shared.isCancelled());
        claims.assignSteward(owner, key(1), resident);
        var same = new InventoryMoveItemEvent(storage(15, false), new EventItem(), storage(16, false), true);
        listener.onInventoryMove(same); assertFalse(same.isCancelled());
        claims.assignSteward(owner, key(1), neighbor);
        var other = new InventoryMoveItemEvent(storage(15, false), new EventItem(), storage(16, false), true);
        listener.onInventoryMove(other); assertTrue(other.isCancelled());
    }
    @Test void waterCannotFlowFromSharedLandIntoAResidentsHome() {
        var event = new BlockFromToEvent(block(16), block(15));
        listener.onFlow(event); assertTrue(event.isCancelled());
    }
    @Test void doubleChestRequiresAccessToBothHalves() {
        Inventory left = storage(15, false), right = storage(16, false);
        DoubleChestInventory halves = proxy(DoubleChestInventory.class, (p, m, a) -> switch (m.getName()) {
            case "getLeftSide" -> left;
            case "getRightSide" -> right;
            default -> throw new UnsupportedOperationException(m.getName());
        });
        DoubleChest holder = new DoubleChest(halves);
        Inventory combined = proxy(Inventory.class, (p, m, a) -> m.getName().equals("getHolder") ? holder : 54);
        var other = new InventoryOpenEvent(view(combined, player(neighbor)));
        listener.onOpenInventory(other); assertTrue(other.isCancelled());
        var own = new InventoryOpenEvent(view(combined, player(resident)));
        listener.onOpenInventory(own); assertFalse(own.isCancelled());
    }
    private ChunkKey key(int x) { return new ChunkKey(worldId, x, 0); }
    private Location location(int x) { return new Location(world, x, 64, 0); }
    private Block block(int x) { return proxy(Block.class, (p, m, a) -> switch (m.getName()) {
        case "getWorld" -> world;
        case "getX" -> x;
        case "getY" -> 64;
        case "getZ" -> 0;
        case "getLocation" -> location(x);
        default -> throw new UnsupportedOperationException(m.getName());
    }); }
    private Inventory storage(int x, boolean mobile) {
        Object holder = mobile ? proxy(HopperMinecart.class, (p, m, a) -> location(x))
            : proxy(Container.class, (p, m, a) -> m.getName().equals("getLocation") ? location(x) : block(x));
        return proxy(Inventory.class, (p, m, a) -> switch (m.getName()) {
            case "getHolder" -> holder;
            case "getLocation" -> location(x);
            case "getSize" -> 27;
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    private Item item(int x) { return proxy(Item.class, (p, m, a) -> location(x)); }
    private Player player(AccountId account) {
        UUID id = UUID.randomUUID(); sessions.join(id, account);
        return proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            case "hasPermission" -> false;
            case "sendActionBar" -> null;
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    private InventoryView view(Inventory top, Player player) {
        return proxy(InventoryView.class, (p, m, a) -> switch (m.getName()) {
            case "getTopInventory" -> top;
            case "getPlayer" -> player;
            case "convertSlot" -> a[0];
            default -> throw new UnsupportedOperationException(m.getName());
        });
    }
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
    private static final class EventItem extends ItemStack {
        private EventItem() { super(); }
        @Override public ItemStack clone() { return new EventItem(); }
    }
}

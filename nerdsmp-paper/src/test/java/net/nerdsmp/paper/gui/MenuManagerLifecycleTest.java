package net.nerdsmp.paper.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@ResourceLock("bukkit-server")
final class MenuManagerLifecycleTest {
    @Test
    void shutdownReturnsForgeInputsExactlyOnceWithAndWithoutCloseEvents() throws Exception {
        MenuManager menus = new MenuManager(null);
        ForgeView registered = new ForgeView(menus, MenuManager.View.FORGE, true);
        ForgeView unregistered = new ForgeView(menus, MenuManager.View.FORGE, false);
        ForgeView ordinary = new ForgeView(menus, MenuManager.View.HOME, false);
        ItemStack gear = new TestItem();
        ItemStack catalyst = new TestItem();
        registered.inputs.put(ForgeSlotPolicy.PRIMARY_SLOT, gear);
        registered.inputs.put(ForgeSlotPolicy.CATALYST_SLOT, catalyst);
        unregistered.inputs.put(ForgeSlotPolicy.PRIMARY_SLOT, gear);
        unregistered.inputs.put(ForgeSlotPolicy.CATALYST_SLOT, catalyst);
        ordinary.inputs.put(ForgeSlotPolicy.PRIMARY_SLOT, gear);

        var serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        Object previousServer = serverField.get(null);
        Server server = proxy(Server.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getOnlinePlayers" -> List.of(registered.player, unregistered.player, ordinary.player);
            default -> throw new UnsupportedOperationException(method.getName());
        });
        try {
            serverField.set(null, server);
            menus.close();
            menus.close();
        } finally {
            serverField.set(null, previousServer);
        }

        for (ForgeView forge : List.of(registered, unregistered)) {
            assertEquals(1, forge.closes);
            assertEquals(2, forge.returned.size());
            assertSame(gear, forge.returned.get(0));
            assertSame(catalyst, forge.returned.get(1));
            assertNull(forge.inputs.get(ForgeSlotPolicy.PRIMARY_SLOT));
            assertNull(forge.inputs.get(ForgeSlotPolicy.CATALYST_SLOT));
        }
        assertEquals(0, ordinary.closes);
        assertEquals(0, ordinary.returned.size());
        assertSame(gear, ordinary.inputs.get(ForgeSlotPolicy.PRIMARY_SLOT));
    }

    @Test
    void emptyInputVariantsAreClearedWithoutReturningPhantomItems() throws Exception {
        MenuManager menus = new MenuManager(null);
        for (Material air : List.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR)) {
            ForgeView forge = new ForgeView(menus, MenuManager.View.FORGE, true);
            forge.inputs.put(ForgeSlotPolicy.PRIMARY_SLOT, new TestItem(air, 1));
            forge.inputs.put(ForgeSlotPolicy.CATALYST_SLOT, new TestItem(Material.STONE, 0));

            forge.player.closeInventory();

            assertEquals(0, forge.returned.size());
            assertNull(forge.inputs.get(ForgeSlotPolicy.PRIMARY_SLOT));
            assertNull(forge.inputs.get(ForgeSlotPolicy.CATALYST_SLOT));
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

    private static final class ForgeView {
        private final Map<Integer, ItemStack> inputs = new HashMap<>();
        private final List<ItemStack> returned = new ArrayList<>();
        private final Player player;
        private int closes;
        private boolean open = true;

        private ForgeView(MenuManager menus, MenuManager.View type, boolean deliverEvent) throws Exception {
            Class<?> holderClass = Class.forName(MenuManager.class.getName() + "$MenuHolder");
            var constructor = holderClass.getDeclaredConstructor(MenuManager.View.class);
            constructor.setAccessible(true);
            InventoryHolder holder = (InventoryHolder) constructor.newInstance(type);
            Inventory inventory = proxy(Inventory.class, (instance, method, arguments) -> switch (method.getName()) {
                case "getHolder" -> open ? holder : null;
                case "getItem" -> inputs.get((int) arguments[0]);
                case "setItem" -> {
                    inputs.put((int) arguments[0], (ItemStack) arguments[1]);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
            PlayerInventory carried = proxy(PlayerInventory.class, (instance, method, arguments) -> {
                if (!method.getName().equals("addItem")) throw new UnsupportedOperationException(method.getName());
                returned.addAll(List.of((ItemStack[]) arguments[0]));
                return new HashMap<Integer, ItemStack>();
            });
            Player[] actor = new Player[1];
            InventoryView view = proxy(InventoryView.class, (instance, method, arguments) -> switch (method.getName()) {
                case "getTopInventory" -> inventory;
                case "getPlayer" -> actor[0];
                default -> throw new UnsupportedOperationException(method.getName());
            });
            player = proxy(Player.class, (instance, method, arguments) -> switch (method.getName()) {
                case "getOpenInventory" -> view;
                case "getInventory" -> carried;
                case "closeInventory" -> {
                    closes++;
                    if (deliverEvent) menus.onClose(new InventoryCloseEvent(view));
                    open = false;
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
            actor[0] = player;
        }
    }

    /** Avoid starting Paper's item registry for a test that only returns inventory references. */
    private static final class TestItem extends ItemStack {
        private final Material material;
        private final int amount;
        private TestItem() { this(Material.STONE, 1); }
        private TestItem(Material material, int amount) {
            super();
            this.material = material;
            this.amount = amount;
        }
        @Override public Material getType() { return material; }
        @Override public int getAmount() { return amount; }
    }
}

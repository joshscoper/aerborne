package net.aerborne.client.fabric;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Aerborne skin for the server's chest menus. The server tags its own menus as they open (MenuPayload); a chest
 * screen with that title is drawn in the Aerborne panel instead of the chest art. The glass panes the plugin uses
 * as filler are left out, real entries sit on tiles, and the plugin's Back, Close and page arrows become icon
 * buttons. Every click still goes to the same slot, so the menus behave exactly as before.
 */
public final class AerborneMenus {
    private AerborneMenus() { }

    private static final Identifier PANEL = sprite("menu/panel");
    private static final Identifier BUTTON = sprite("menu/button");
    private static final int GOLD = 0xFFF4C76B, TILE = 0xFF1A2A44, TILE_EDGE = 0xFF31435F, SHADE = 0xFF0D1A2B;
    /** How long a tag waits for its screen. */
    private static final long PENDING_NANOS = 3_000_000_000L;

    private static final Map<Screen, Boolean> SKINNED = Collections.synchronizedMap(new WeakHashMap<>());
    private static String pendingTitle;
    private static long pendingAt;

    private static Identifier sprite(String path) { return Identifier.fromNamespaceAndPath("aerborne_client", path); }

    /** A menu tag arrived: skin the open screen if it already has that title, otherwise the next one that does. */
    static void tagged(Minecraft mc, MenuPayload menu) {
        if (mc.gui.screen() instanceof ContainerScreen open && open.getTitle().getString().equals(menu.title())) {
            SKINNED.put(open, true);
            return;
        }
        pendingTitle = menu.title();
        pendingAt = System.nanoTime();
    }

    /** Called as a screen finishes opening. */
    static void opened(Screen screen) {
        if (!(screen instanceof ContainerScreen) || pendingTitle == null) return;
        if (System.nanoTime() - pendingAt > PENDING_NANOS) { pendingTitle = null; return; }
        if (screen.getTitle().getString().equals(pendingTitle)) { SKINNED.put(screen, true); pendingTitle = null; }
    }

    public static boolean skinned(Object screen) { return screen instanceof Screen s && SKINNED.containsKey(s); }

    /** The plugin's filler: a glass pane with a blank name. */
    public static boolean filler(ItemStack item) {
        if (item.isEmpty()) return false;
        String id = item.getItem().builtInRegistryHolder().key().identifier().getPath();
        return id.endsWith("glass_pane") && item.getHoverName().getString().isBlank();
    }

    /** The icon for one of the plugin's navigation items, or null for an ordinary entry. */
    public static Identifier navigation(ItemStack item) {
        if (!item.is(Items.ARROW) && !item.is(Items.BARRIER)) return null;
        String name = item.getHoverName().getString().toLowerCase(java.util.Locale.ROOT);
        if (item.is(Items.BARRIER)) return name.contains("close") ? Ui.control("close") : null;
        if (name.contains("previous page")) return Ui.control("page_left");
        if (name.contains("next page")) return Ui.control("page_right");
        if (name.startsWith("← back") || name.startsWith("back") || name.equals("cancel")) return Ui.control("back");
        return null;
    }

    /** Draws the frame, the title and a tile under every slot that holds something real. */
    public static void background(GuiGraphicsExtractor g, ContainerScreen screen, int left, int top, int width, int height) {
        var font = Minecraft.getInstance().font;
        Ui.window(g, left - 6, top - 8, width + 12, height + 14);
        int dividerY = Integer.MAX_VALUE;
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;
            boolean player = slot.container instanceof Inventory;
            if (player) dividerY = Math.min(dividerY, top + slot.y - 14);
            if (!player && (slot.getItem().isEmpty() || filler(slot.getItem()))) continue;
            g.blitSprite(RenderPipelines.GUI_TEXTURED, Ui.SLOT, left + slot.x - 1, top + slot.y - 1, 18, 18);
        }
        if (dividerY != Integer.MAX_VALUE) {
            g.fill(left + 8, dividerY + 4, left + width - 8, dividerY + 5, 0x804A5A75);
            g.text(font, "Your pack", left + 8, dividerY + 7, 0xFF8E9AAF, false);
        }
        String title = font.plainSubstrByWidth(screen.getTitle().getString(), width - 16);
        g.text(font, title, left + 12, top - 3, GOLD, true);
    }

    // ------------------------------------------------------------------ rarity

    private static final String[] RARITIES = {"RELIC", "MYTHIC", "LEGENDARY", "EPIC", "RARE", "UNCOMMON", "COMMON"};
    private static final int[] RARITY_COLOURS = {0x3FD7C9, 0xE0405A, 0xF4B13A, 0xB061FF, 0x4A8CFF, 0x55D46A, 0x9AA3B5};

    /** The item's rarity as the slot art names it (uncommon ... relic), or null for none worth showing. */
    public static String rarityName(ItemStack item) {
        int colour = rarityColour(item);
        for (int i = 0; i < RARITY_COLOURS.length; i++) if (colour == RARITY_COLOURS[i]) return RARITIES[i].toLowerCase(java.util.Locale.ROOT);
        return null;
    }

    /** The colour of the item's rarity, from the rarity word in its lore, or 0 when it has none worth showing. */
    public static int rarityColour(ItemStack item) {
        if (item.isEmpty() || filler(item)) return 0;
        var lore = item.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) {
            for (var line : lore.lines()) {
                String text = line.getString().toUpperCase(java.util.Locale.ROOT);
                for (int i = 0; i < RARITIES.length; i++)
                    if (text.matches(".*\\b" + RARITIES[i] + "\\b.*")) return i == RARITIES.length - 1 ? 0 : RARITY_COLOURS[i];
            }
        }
        var vanilla = item.get(net.minecraft.core.component.DataComponents.RARITY);
        if (vanilla == null) return 0;
        return switch (vanilla) {
            case UNCOMMON -> RARITY_COLOURS[5];
            case RARE -> RARITY_COLOURS[4];
            case EPIC -> RARITY_COLOURS[3];
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ search

    private static final Map<Screen, net.minecraft.client.gui.components.EditBox> SEARCH =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Adds the search box above a chest screen's grid. */
    static void addSearch(ContainerScreen screen, int left, int top, int width) {
        var font = Minecraft.getInstance().font;
        // The Aerborne search field: its art behind a borderless text box, the magnifier at the left.
        var box = new net.minecraft.client.gui.components.EditBox(font, left + width - 96, top - 27, 74, 12,
                net.minecraft.network.chat.Component.literal("Search")) {
            @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
                g.blitSprite(RenderPipelines.GUI_TEXTURED, isFocused() ? Ui.SEARCH_FOCUSED : Ui.SEARCH, getX() - 24, getY() - 5, getWidth() + 30, 20);
                super.extractWidgetRenderState(g, mouseX, mouseY, delta);
            }
        };
        box.setBordered(false);
        box.setTextColor(0xFFF4F9FF);
        box.setHint(net.minecraft.network.chat.Component.literal("Search…").withColor(0xFF8E9AAF));
        box.setMaxLength(40);
        net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(screen).add(box);
        SEARCH.put(screen, box);
    }

    public static net.minecraft.client.gui.components.EditBox search(Object screen) {
        return screen instanceof Screen s ? SEARCH.get(s) : null;
    }

    /** True when the screen has a search typed in and this item does not match it. */
    public static boolean hiddenBySearch(Object screen, ItemStack item) {
        var box = search(screen);
        if (box == null || box.getValue().isBlank()) return false;
        if (item.isEmpty() || filler(item)) return true;
        String query = box.getValue().toLowerCase(java.util.Locale.ROOT).strip();
        if (item.getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(query)) return false;
        var lore = item.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) for (var line : lore.lines())
            if (line.getString().toLowerCase(java.util.Locale.ROOT).contains(query)) return false;
        return true;
    }

    // ------------------------------------------------------------------ item lock

    /** The saved index of a player inventory slot, or -1 for any other slot. */
    public static int lockIndex(Slot slot) {
        return slot != null && slot.container instanceof Inventory ? slot.getContainerSlot() : -1;
    }

    public static boolean locked(Slot slot) {
        int index = lockIndex(slot);
        return index >= 0 && ClientState.preferences.lockedSlots.contains(index);
    }

    public static boolean lockedIndex(int index) { return ClientState.preferences.lockedSlots.contains(index); }

    /** Locks or unlocks the player inventory slot under the cursor. */
    public static void toggleLock(Slot slot) {
        int index = lockIndex(slot);
        if (index < 0) return;
        var locked = ClientState.preferences.lockedSlots;
        if (!locked.remove(index)) locked.add(index);
        ClientState.save();
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT, locked.contains(index) ? 1.4F : 0.8F));
    }

    /** A small gold padlock in a slot's corner. */
    public static void lockBadge(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 10, y + 9, x + 16, y + 15, 0xFF0D1A2B);
        g.fill(x + 11, y + 11, x + 15, y + 14, GOLD);
        g.fill(x + 11, y + 9, x + 12, y + 11, GOLD);
        g.fill(x + 14, y + 9, x + 15, y + 11, GOLD);
        g.fill(x + 12, y + 8, x + 14, y + 9, GOLD);
    }

    /** Draws a navigation item as a button with its icon, in slot coordinates. */
    public static void navigationButton(GuiGraphicsExtractor g, Identifier icon, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, Ui.BUTTON.get(true, false), x - 1, y - 1, 18, 18);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x + 1, y + 1, 14, 14);
    }
}

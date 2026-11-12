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
    private static String pendingTitle, pendingKind;
    private static long pendingAt;
    /** Chest screens the player switched back to with "Classic view": they stay as slot grids. */
    static final Map<Screen, Boolean> CLASSIC = Collections.synchronizedMap(new WeakHashMap<>());
    /**
     * Staff editors keep the slot view. Storage grids (bank, trade offers, equipment, loot to take) open in the native
     * storage screen, which keeps every slot's position. Every other plugin menu opens as a native Aerborne screen; stations that act on
     * what you carry (repair, forge, alchemy, kitchen) show your bag inside it.
     */
    private static final java.util.List<String> SLOT_MENUS = java.util.List.of("RecipeStudio", "StudioUi", "ContentEditor", "StaffManager");
    /** Grids where position matters, drawn natively cell for cell: vault, trade offers, equipment doll, loot. */
    private static final java.util.List<String> STORAGE_MENUS = java.util.List.of("BankService", "BankHolder", "TradeManager",
            "CharacterEquipmentService", "RunLoot");

    private static Identifier sprite(String path) { return Identifier.fromNamespaceAndPath("aerborne_client", path); }

    /** A menu tag arrived: skin the open screen if it already has that title, otherwise the next one that does. */
    static void tagged(Minecraft mc, MenuPayload menu) {
        if (mc.gui.screen() instanceof ContainerScreen open && open.getTitle().getString().equals(menu.title())) {
            mark(mc, open, menu.kind());
            return;
        }
        pendingTitle = menu.title();
        pendingKind = menu.kind();
        pendingAt = System.nanoTime();
    }

    /** Called as a screen finishes opening. */
    static void opened(Screen screen) {
        if (!(screen instanceof ContainerScreen chest) || pendingTitle == null) return;
        if (System.nanoTime() - pendingAt > PENDING_NANOS) { pendingTitle = null; return; }
        if (screen.getTitle().getString().equals(pendingTitle)) { mark(Minecraft.getInstance(), chest, pendingKind); pendingTitle = null; }
    }

    private static void mark(Minecraft mc, ContainerScreen screen, String kind) {
        SKINNED.put(screen, true);
        if (CLASSIC.containsKey(screen) || !ClientState.preferences.nativeMenus) return;
        if (STORAGE_MENUS.stream().anyMatch(kind::contains)) {
            mc.execute(() -> { if (mc.gui.screen() == screen) mc.gui.setScreen(new StorageMenuScreen(screen, kind)); });
            return;
        }
        if (SLOT_MENUS.stream().anyMatch(kind::contains)) return;
        // Swap in the native screen once this one has finished opening.
        mc.execute(() -> { if (mc.gui.screen() == screen) mc.gui.setScreen(new NativeMenuScreen(screen, kind)); });
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
        Ui.windowTitle(g, font, screen.getTitle().getString(), left - 6, top - 8, width + 12, 0);
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

    /**
     * The colour of the item's rarity, or 0 when it has none worth showing. Only real items count: the plugin writes
     * their rarity as a capitals line such as "LEGENDARY SWORD". Menu buttons (anything that says "Click to") never
     * get a frame, even when their description mentions a rarity.
     */
    public static int rarityColour(ItemStack item) {
        if (item.isEmpty() || filler(item)) return 0;
        var lore = item.get(net.minecraft.core.component.DataComponents.LORE);
        if (lore != null) {
            for (var line : lore.lines()) if (line.getString().toLowerCase(java.util.Locale.ROOT).contains("click to")) return 0;
            for (var line : lore.lines()) {
                String text = line.getString().strip();
                if (text.isEmpty() || !text.equals(text.toUpperCase(java.util.Locale.ROOT))) continue;
                for (int i = 0; i < RARITIES.length; i++)
                    if (text.equals(RARITIES[i]) || text.startsWith(RARITIES[i] + " ")) return i == RARITIES.length - 1 ? 0 : RARITY_COLOURS[i];
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ menu icons

    /** Words in a menu entry's name and the hand-drawn icon each calls for (tools/make_menu_icons.py, make_skill_icons.py). */
    private static final String[][] MENU_ICONS = {
        // Crafting words first: "workshop" contains "shop".
        {"workshop", "skills/smithing"}, {"forge", "skills/smithing"},
        {"auction", "menu_icons/trade"}, {"shop", "menu_icons/trade"}, {"market", "menu_icons/trade"}, {"trade", "menu_icons/trade"},
        {"merchant", "menu_icons/trade"}, {"store", "menu_icons/trade"}, {"bank", "menu_icons/bank"}, {"vault", "menu_icons/bank"},
        {"island", "menu_icons/island"}, {"homestead", "menu_icons/island"}, {"friend", "menu_icons/friends"},
        {"party", "menu_icons/party"}, {"community", "menu_icons/party"}, {"social", "menu_icons/party"}, {"guild", "menu_icons/party"},
        {"setting", "menu_icons/settings"}, {"appearance", "menu_icons/settings"}, {"tutorial", "menu_icons/help"},
        {"help", "menu_icons/help"}, {"guide", "menu_icons/help"}, {"quest", "menu_icons/quests"}, {"journal", "menu_icons/quests"},
        {"achievement", "menu_icons/achievements"}, {"milestone", "menu_icons/achievements"}, {"leaderboard", "menu_icons/leaderboards"},
        {"ranking", "menu_icons/leaderboards"}, {"expedition", "menu_icons/expeditions"}, {"dungeon", "menu_icons/expeditions"},
        {"travel", "menu_icons/travel"}, {"flight", "menu_icons/travel"}, {"warp", "menu_icons/travel"}, {"aerborne", "menu_icons/travel"},
        {"cosmetic", "menu_icons/cosmetics"}, {"title", "menu_icons/cosmetics"}, {"wardrobe", "menu_icons/cosmetics"},
        {"mail", "menu_icons/mail"}, {"character", "menu_icons/character"}, {"profile", "menu_icons/character"},
        {"equipment", "menu_icons/character"}, {"gear", "menu_icons/character"}, {"adventure", "menu_icons/map"},
        {"campaign", "menu_icons/map"}, {"map", "menu_icons/map"}, {"waypoint", "menu_icons/map"},
        {"alchem", "skills/alchemy"}, {"brew", "skills/alchemy"}, {"cook", "skills/cooking"}, {"kitchen", "skills/cooking"},
        {"fish", "skills/fishing"}, {"excavat", "skills/excavation"}, {"dig", "skills/excavation"}, {"mining", "skills/mining"},
        {"farm", "skills/farming"}, {"forest", "skills/forestry"}, {"hunt", "skills/hunting"}, {"enchant", "skills/enchanting"},
        {"build", "skills/building"}, {"smith", "skills/smithing"},
        {"craft", "skills/smithing"}, {"repair", "skills/smithing"}, {"skill", "skills/adventurer"}, {"talent", "skills/adventurer"},
        {"adventurer", "skills/adventurer"},
    };

    /** The hand-drawn icon for a menu entry with this name, or null when none fits. */
    public static Identifier menuIcon(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        for (String[] entry : MENU_ICONS) if (lower.contains(entry[0])) return Ui.sprite(entry[1]);
        return null;
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

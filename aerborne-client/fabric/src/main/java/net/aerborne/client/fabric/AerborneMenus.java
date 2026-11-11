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
        if (item.is(Items.BARRIER)) return name.contains("close") ? sprite("icon/nav/close") : null;
        if (name.contains("previous page")) return sprite("icon/nav/left");
        if (name.contains("next page")) return sprite("icon/nav/right");
        if (name.startsWith("← back") || name.startsWith("back") || name.equals("cancel")) return sprite("icon/nav/back");
        return null;
    }

    /** Draws the frame, the title and a tile under every slot that holds something real. */
    public static void background(GuiGraphicsExtractor g, ContainerScreen screen, int left, int top, int width, int height) {
        var font = Minecraft.getInstance().font;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, left - 6, top - 6, width + 12, height + 12);
        int dividerY = Integer.MAX_VALUE;
        for (Slot slot : screen.getMenu().slots) {
            if (!slot.isActive()) continue;
            boolean player = slot.container instanceof Inventory;
            if (player) dividerY = Math.min(dividerY, top + slot.y - 14);
            if (!player && (slot.getItem().isEmpty() || filler(slot.getItem()))) continue;
            int x = left + slot.x - 1, y = top + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, TILE_EDGE);
            g.fill(x + 1, y + 1, x + 17, y + 17, player ? SHADE : TILE);
        }
        if (dividerY != Integer.MAX_VALUE) {
            g.fill(left + 8, dividerY + 4, left + width - 8, dividerY + 5, 0x804A5A75);
            g.text(font, "Your pack", left + 8, dividerY + 7, 0xFF8E9AAF, false);
        }
        String title = font.plainSubstrByWidth(screen.getTitle().getString(), width - 16);
        g.text(font, title, left + (width - font.width(title)) / 2, top + 5, GOLD, true);
    }

    /** Draws a navigation item as a button with its icon, in slot coordinates. */
    public static void navigationButton(GuiGraphicsExtractor g, Identifier icon, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON, x - 1, y - 1, 18, 18);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, 16, 16);
    }
}

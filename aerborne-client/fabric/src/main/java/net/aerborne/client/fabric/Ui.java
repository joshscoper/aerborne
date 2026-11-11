package net.aerborne.client.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The native UI art (tools/slice_ui_pack.py, from docs/ASSET_PROMPT.md) and the few ways it is drawn. Frames and
 * bars are nine-slice sprites, so they take any size; icons are 16x16 and slots 16x16, drawn at whole multiples.
 */
public final class Ui {
    private Ui() { }
    public static final int TEXT = 0xFFF4F9FF, MUTED = 0xFFACBBD0;
    public static String fit(net.minecraft.client.gui.Font font, String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
    }

    public static Identifier sprite(String path) { return Identifier.fromNamespaceAndPath("aerborne_client", "ui/" + path); }

    public static final Identifier WINDOW = sprite("window/large_window"), TITLE_STAR = sprite("window/title_star"),
            INSET = sprite("window/inner_inset_panel"), DIVIDER = sprite("window/divider"),
            SEARCH = sprite("window/search_box"), SEARCH_FOCUSED = sprite("window/search_box_focused"),
            SCROLL_TRACK = sprite("window/scrollbar_track"), SCROLL_THUMB = sprite("window/scrollbar_thumb"),
            TAB = sprite("window/tab_active"), TAB_INACTIVE = sprite("window/tab_inactive"), TAB_HOVER = sprite("window/tab_hover");
    public static final WidgetSprites BUTTON = new WidgetSprites(sprite("window/button_secondary"),
            sprite("window/button_disabled"), sprite("window/button_primary"));
    public static final Identifier BAR_FRAME = sprite("bars/bar_frame"), HEALTH = sprite("bars/health_fill"),
            ABSORPTION = sprite("bars/absorption_fill"), ADVENTURER_XP = sprite("bars/adventurer_xp_fill"),
            SKILL_XP = sprite("bars/skill_xp_fill"), BOSS = sprite("bars/boss_health_fill"), CAST = sprite("bars/cast_charge_fill"),
            NAME_TAB = sprite("bars/dialogue_name_tab"), BOOK = sprite("screens/quest_book_spread"),
            SLOT = sprite("slots/slot_normal"), SLOT_HOVER = sprite("slots/slot_hover"),
            CURSOR = sprite("minimap/player_cursor"), MAP_PIN = sprite("minimap/tracked_waypoint"),
            CAP_LEFT = sprite("minimap/compass_endcap_left"), CAP_RIGHT = sprite("minimap/compass_endcap_right");

    /** A window: the gold frame with its title bar, and the title bar's star centred above the content. */
    public static void window(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW, x, y, w, h);
        g.blitSprite(RenderPipelines.GUI_TEXTURED, TITLE_STAR, x + w / 2 - 10, y + 1, 20, 15);
    }

    public static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, INSET, x, y, w, h);
    }

    /** A framed bar filled to {@code ratio} with one of the fill sprites. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, Identifier fill, float ratio) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_FRAME, x, y, w, h);
        int inner = w - 4, filled = Math.round(inner * Math.clamp(ratio, 0F, 1F));
        if (filled > 0) {
            // Clip a full-width fill: ornament stays stable instead of stretching as the value changes.
            g.enableScissor(x + 2, y + 2, x + 2 + filled, y + h - 2);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, fill, x + 2, y + 2, inner, h - 4);
            g.disableScissor();
        }
    }

    /** The toast banner for a kind of toast from the server. */
    public static Identifier toast(String kind) {
        return sprite("bars/" + switch (kind) {
            case "level", "skill" -> "level_up_toast";
            case "quest" -> "quest_complete_toast";
            case "drop" -> "rare_drop_toast";
            case "achievement" -> "achievement_toast";
            default -> "toast_frame";
        });
    }

    /** The frame for an item rarity, or null for none. */
    public static Identifier rarity(String rarity) { return rarity == null ? null : sprite("slots/rarity_" + rarity); }

    public static Identifier control(String name) { return sprite("controls/" + name); }
    public static Identifier quest(String name) { return sprite("tree/quest_" + name); }
    public static Identifier icon(String group, String name) { return sprite(group + "/" + name); }

    public static void icon(GuiGraphicsExtractor g, Identifier icon, int x, int y, int size) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, icon, x, y, size, size);
    }
}

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
    public static final int TEXT = 0xFFF4F9FF, MUTED = 0xFFACBBD0, GOLD = 0xFFF4C76B, GOLD_LIGHT = 0xFFFFE49A;
    /** The Aerborne heading font (tools/make_font.py): serif capitals for titles, names and headings. */
    public static final net.minecraft.network.chat.FontDescription HEADING_FONT =
            new net.minecraft.network.chat.FontDescription.Resource(Identifier.fromNamespaceAndPath("aerborne_client", "heading"));

    public static net.minecraft.network.chat.MutableComponent heading(String text) {
        return net.minecraft.network.chat.Component.literal(text).withStyle(style -> style.withFont(HEADING_FONT));
    }
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

    /** A window: the gold frame with its title bar (rows 3 to 14 of the art). Name it with {@link #windowTitle}. */
    public static void window(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, WINDOW, x, y, w, h);
    }

    /** The heading text, cut with "..." (the heading font has no ellipsis glyph) to fit {@code width} in that font. */
    public static net.minecraft.network.chat.MutableComponent heading(net.minecraft.client.gui.Font font, String text, int width) {
        String plain = text.replaceAll("§.", "").strip();
        if (font.width(heading(plain)) <= width) return heading(plain);
        while (!plain.isEmpty() && font.width(heading(plain + "...")) > width) plain = plain.substring(0, plain.length() - 1).stripTrailing();
        return heading(plain + "...");
    }

    /**
     * Draws a heading in {@code width}: whole if it fits, else shrunk (to three quarters at most, kept on the same
     * middle line) so long names stay readable, and cut with "..." only past that.
     */
    public static void headingFit(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int y, int width, int colour) {
        var whole = heading(text.replaceAll("§.", "").strip());
        int natural = font.width(whole);
        if (natural <= width) { g.text(font, whole, x, y, colour, true); return; }
        float scale = Math.max(0.75F, width / (float) natural);
        var shown = scale * natural <= width ? whole : heading(font, text, (int) (width / scale));
        g.pose().pushMatrix();
        g.pose().translate(x, y + Math.round(8 * (1 - scale) / 2));
        g.pose().scale(scale, scale);
        g.text(font, shown, 0, 0, colour, true);
        g.pose().popMatrix();
    }

    /**
     * A window's title, centred in its title bar between two short gold rules, as in a raid UI's frames. Anything at
     * the right of the bar (a search box, {@code reserveRight} wide) is kept clear by the same margin on both sides;
     * when that leaves too little room the title sits at the left instead. With no title the bar gets its crest.
     */
    public static void windowTitle(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int y, int w, int reserveRight) {
        if (text == null || text.isBlank()) {
            g.blitSprite(RenderPipelines.GUI_TEXTURED, TITLE_STAR, x + w / 2 - 15, y + 2, 31, 13);
            return;
        }
        titleBar(g, font, text, x, y + 3, w, 11, reserveRight);
    }

    /** A title centred in a bar of {@code barHeight} at {@code barY}, flanked by rules; see {@link #windowTitle}. */
    public static void titleBar(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, String text, int x, int barY, int w, int barHeight, int reserveRight) {
        // The heading font's capitals stand 8 high just below the text's y; centre them in the bar.
        int ty = barY + (barHeight - 8) / 2, middle = barY + barHeight / 2;
        int side = Math.max(16, reserveRight + 12), room = w - 2 * side;
        if (room < 80) {
            var title = heading(font, text, w - 24 - reserveRight);
            g.text(font, title, x + 12, ty, GOLD, true);
            return;
        }
        var title = heading(font, text, room - 40);
        int tw = font.width(title), left = x + w / 2 - tw / 2;
        g.text(font, title, left, ty, GOLD, true);
        int rule = Math.min(36, (room - tw) / 2 - 8);
        if (rule < 8) return;
        int a = left - 6, b = left + tw + 6;
        g.fill(a - rule, middle, a - 3, middle + 1, 0xFF8A6A2E);
        g.fill(b + 3, middle, b + rule, middle + 1, 0xFF8A6A2E);
        diamond(g, a - 1, middle);
        diamond(g, b, middle);
    }

    private static void diamond(GuiGraphicsExtractor g, int cx, int cy) {
        g.fill(cx - 1, cy, cx + 2, cy + 1, GOLD);
        g.fill(cx, cy - 1, cx + 1, cy + 2, GOLD);
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

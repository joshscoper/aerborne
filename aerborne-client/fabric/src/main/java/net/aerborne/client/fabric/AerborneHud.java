package net.aerborne.client.fabric;

import java.util.Locale;
import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;

public final class AerborneHud {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private AerborneHud() { }
    public static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        var p = ClientState.preferences;
        // Fabric's inherited CHAT render condition respects F1; screens (edit mode among them) also suppress this.
        if (!ClientState.available() || !p.hud || mc.player == null || mc.gui.screen() != null) return;
        AerborneOverlays.screenWide(g, mc, p, p.trackedFor(ClientState.scope()).orElse(null));
        HudElements.drawAll(g, mc, p, false);
    }

    /** The character plate at (0, 0): the server's numbers when it sends them, else the game's own. */
    static boolean player(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p) {
        var player = mc.player;
        if (player == null) return false;
        HudPayload server = ClientState.hud;
        if (server != null) {
            // The server's own numbers: true health (not the twenty the hearts are scaled to), Defense, the
            // Adventurer's level and XP, and Marks. Food is left out: the server holds hunger still.
            String hp = number(server.health()) + " / " + number(server.maxHealth()) + (server.absorption() > 0 ? "  +" + number(server.absorption()) : "");
            long need = server.experienceNeeded();
            vitals(g, 0, 0, player.getGameProfile().name(), server.rank(), server.level(),
                    server.health(), server.maxHealth(), server.recoveryHeld(), hp,
                    need <= 0 ? 1 : server.experience(), need <= 0 ? 1 : need,
                    need <= 0 ? "MAX" : compact(server.experience()) + " / " + compact(need),
                    number(server.defense()), compact(server.marks()) + " Marks", p);
        } else {
            vitals(g, 0, 0, player.getGameProfile().name(), "Adventurer", player.experienceLevel,
                    player.getHealth(), player.getMaxHealth(), false, number(player.getHealth()) + " / " + number(player.getMaxHealth()),
                    player.experienceProgress, 1, Math.round(player.experienceProgress * 100) + "%",
                    Integer.toString(player.getArmorValue()), "Food " + player.getFoodData().getFoodLevel(), p);
        }
        return true;
    }

    /**
     * The objectives stack at (0, 0), like a raid UI's quest tracker: the story progress, the tracked quest, the
     * server's quest or expedition overlay, and the tracked waypoint, one panel under another.
     */
    static boolean objectives(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, boolean preview) {
        if (mc.player == null) return false;
        var font = mc.font;
        HudPayload server = ClientState.hud;
        int y = 0;
        if (server != null && !(server.progressTitle().isBlank() && server.progressLine().isBlank())) {
            // The progress panel grows with its title and wraps its line instead of cutting them off.
            var title = Ui.heading(server.progressTitle().replaceAll("§.", ""));
            int w = panelWidth(font.width(title) + 16);
            var titleLines = font.split(title, w - 16);
            var lines = font.split(net.minecraft.network.chat.Component.literal(server.progressLine()), w - 16);
            int h = 8 + titleLines.size() * 11 + lines.size() * 10 + 2;
            box(g, 0, y, w, h, p);
            int row = y + 5;
            for (var line : titleLines) { g.text(font, line, 8, row, Ui.GOLD, true); row += 11; }
            for (var line : lines) { g.text(font, line, 8, row, Ui.TEXT, false); row += 10; }
            y += h + 4;
        }
        y = AerborneTracker.panel(g, font, 0, y, p);
        y = AerborneOverlays.panels(g, font, 0, y, server, p);
        var tracked = p.trackedFor(ClientState.scope());
        if (tracked.isPresent()) {
            var w = tracked.get();
            String distance = String.format(Locale.ROOT, "%.0fm", w.distance(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            int pw = panelWidth(font.width(w.name()) + font.width(distance) + 30);
            box(g, 0, y, pw, 18, p);
            g.text(font, "◆ " + font.plainSubstrByWidth(w.name(), pw - 30 - font.width(distance)), 6, y + 5, p.theme.accent, false);
            g.text(font, distance, pw - 6 - font.width(distance), y + 5, Ui.TEXT, false);
            y += 22;
        }
        if (y == 0 && preview) {
            box(g, 0, 0, 184, 44, p);
            g.text(font, Ui.heading("The Ember Accord"), 8, 5, Ui.GOLD, true);
            g.text(font, "Speak with the Warden at the gate", 8, 18, Ui.TEXT, false);
            g.text(font, "Bandits driven off  2/5", 8, 29, Ui.MUTED, false);
            y = 48;
        }
        return y > 0;
    }

    /** Coordinates and facing as one slim line. */
    static boolean coordinates(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p) {
        var player = mc.player;
        if (player == null || player.isReducedDebugInfo()) return false;
        var pos = player.blockPosition();
        String text = pos.getX() + "  " + pos.getY() + "  " + pos.getZ();
        String facing = player.getDirection().getName().substring(0, 1).toUpperCase(Locale.ROOT);
        int w = mc.font.width(text) + mc.font.width(facing) + 18;
        box(g, 0, 0, w, 16, p);
        g.text(mc.font, text, 6, 4, Ui.TEXT, false);
        g.text(mc.font, facing, w - 6 - mc.font.width(facing), 4, Ui.GOLD, false);
        return true;
    }

    /** Worn and held gear as a row of slots, each with a thin bar for what durability is left. */
    static boolean gear(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, boolean preview) {
        var player = mc.player;
        if (player == null) return false;
        // Only what is worn or held, so the row stays as short as the kit.
        java.util.List<net.minecraft.world.item.ItemStack> worn = new java.util.ArrayList<>();
        for (EquipmentSlot slot : SLOTS) if (!player.getItemBySlot(slot).isEmpty()) worn.add(player.getItemBySlot(slot));
        if (worn.isEmpty()) {
            if (!preview) return false;
            worn.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
            worn.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        }
        int w = 6 + worn.size() * 20;
        box(g, 0, 0, w, 26, p);
        for (int i = 0; i < worn.size(); i++) {
            var item = worn.get(i);
            int ix = 3 + i * 20;
            g.item(item, ix + 1, 4);
            if (item.isDamageableItem()) {
                float left = (item.getMaxDamage() - item.getDamageValue()) / (float) item.getMaxDamage();
                int colour = left < 0.2F ? 0xFFE05A5A : left < 0.5F ? 0xFFF6C85F : 0xFF6FD08C;
                g.fill(ix + 1, 21, ix + 17, 23, 0xFF0D1626);
                g.fill(ix + 1, 21, ix + 1 + Math.round(16 * left), 23, colour);
            }
        }
        return true;
    }

    static String number(float value) {
        return Math.rint(value) == value ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }
    static String compact(long value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 10_000) return String.format(Locale.ROOT, "%.1fk", value / 1000.0);
        return Long.toString(value);
    }
    public static void drawPreview(GuiGraphicsExtractor g, int x, int y, ClientPreferences p) {
        vitals(g, x, y, "Aeronaut", "Adventurer", 24, 128, 160, false, "128 / 160", 3240, 5000, "3.2k / 5.0k", "42", "1,840 Marks", p);
    }

    /** The HUD's own 9x9 icons (tools/make_skill_icons.py), drawn at their exact size. */
    private static final net.minecraft.resources.Identifier HEART = Ui.icon("hud", "heart"), XP = Ui.icon("hud", "compass"),
            SHIELD = Ui.icon("hud", "shield"), COIN = Ui.icon("hud", "coin");

    /**
     * The character plate: the player's name in the heading font with rank and level, health and Adventurer XP bars
     * with their icons and figures, and Defense and Marks along the foot. 184 by 62.
     */
    static void vitals(GuiGraphicsExtractor g, int x, int y, String name, String rank, int level, float health, float maxHealth,
                       boolean held, String healthLabel, float xp, float xpNeed, String xpLabel, String defense, String marks,
                       ClientPreferences p) {
        var font = Minecraft.getInstance().font;
        box(g, x, y, 184, 62, p);
        String levelText = "Lv " + level;
        var levelLine = Ui.heading(levelText);
        int levelWidth = font.width(levelLine);
        g.text(font, levelLine, x + 176 - levelWidth, y + 6, Ui.GOLD_LIGHT, true);
        g.text(font, Ui.heading(font, name, 160 - levelWidth), x + 8, y + 6, Ui.GOLD, true);

        Ui.icon(g, HEART, x + 8, y + 21, 9);
        Ui.bar(g, x + 20, y + 19, 156, 13, Ui.HEALTH, maxHealth > 0 ? health / maxHealth : 0);
        // Health held back by recovery is shown dimmed.
        if (held) g.fill(x + 22, y + 21, x + 174, y + 30, 0x900D1A2B);
        g.text(font, healthLabel, x + 98 - font.width(healthLabel) / 2, y + 22, Ui.TEXT, true);

        Ui.icon(g, XP, x + 8, y + 35, 9);
        Ui.bar(g, x + 20, y + 35, 156, 9, Ui.ADVENTURER_XP, xpNeed > 0 ? xp / xpNeed : 0);
        g.pose().pushMatrix();
        g.pose().translate(x + 98, y + 36);
        g.pose().scale(0.75F, 0.75F);
        g.text(font, xpLabel, -font.width(xpLabel) / 2, 0, Ui.TEXT, true);
        g.pose().popMatrix();

        Ui.icon(g, SHIELD, x + 8, y + 49, 9);
        g.text(font, defense, x + 20, y + 50, 0xFFB8E8C0, true);
        int marksWidth = font.width(marks);
        // The rank sits quietly in the space between Defense and Marks.
        int from = x + 20 + font.width(defense) + 8, to = x + 176 - marksWidth - 13 - 6;
        String shownRank = font.plainSubstrByWidth(rank, Math.max(0, to - from));
        g.text(font, shownRank, (from + to) / 2 - font.width(shownRank) / 2, y + 50, Ui.MUTED, false);
        Ui.icon(g, COIN, x + 176 - marksWidth - 12, y + 49, 9);
        g.text(font, marks, x + 176 - marksWidth, y + 50, Ui.GOLD, true);
    }

    static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, ClientPreferences p) {
        Ui.inset(g, x, y, w, h);
        HudElements.track(x, y, w, h);
    }
    /** A framed bar; the colour picks the fill art (red health, gold, sky-blue Adventurer XP, green skill XP). */
    static void bar(GuiGraphicsExtractor g, int x, int y, int w, float value, float max, int color, String label) {
        var fill = switch (color & 0xFFFFFF) {
            case 0xF4C76B -> Ui.ABSORPTION;
            case 0x77C8F5 -> Ui.ADVENTURER_XP;
            case 0x70DBC3 -> Ui.SKILL_XP;
            default -> Ui.HEALTH;
        };
        Ui.bar(g, x, y, w, 14, fill, max > 0 ? value / max : 0);
        // Keep the numeric label readable over both bright fill and decorative highlights.
        g.fill(x + 3, y + 2, x + w - 3, y + 12, 0x700D1A2B);
        // Health held back by recovery is shown dimmed.
        if ((color & 0xFFFFFF) == 0x8C2F39) g.fill(x + 2, y + 2, x + w - 2, y + 12, 0x900D1A2B);
        g.text(Minecraft.getInstance().font, label, x + 5, y + 3, 0xFFF4F9FF, true);
    }

    /** Side panels are 184 wide and grow to fit a long title, up to 260; text beyond that wraps. */
    static int panelWidth(int needed) { return Math.clamp(needed, 184, 260); }
}

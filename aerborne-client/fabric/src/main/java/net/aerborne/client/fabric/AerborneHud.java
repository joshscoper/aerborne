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
        // Fabric's inherited CHAT render condition respects F1; screens also suppress this overlay.
        if (!ClientState.available() || !p.hud || mc.player == null || mc.gui.screen() != null) return;
        var player = mc.player;
        var target = p.trackedFor(ClientState.scope());
        int fullHeight = 94 + (ClientState.hud != null ? 36 : 0) + (p.coordinates ? 30 : 0) + (p.durability ? 46 : 0) + (target.isPresent() ? 38 : 0);
        float scale = Math.min(p.scale / 100F, Math.min((g.guiWidth() - 16) / 184F, (g.guiHeight() - 48) / (float) fullHeight));
        int x = p.topRight ? Math.max(8, (int) (g.guiWidth() / scale) - 192) : 8;
        g.pose().pushMatrix(); g.pose().scale(scale, scale);
        int y = 8;
        HudPayload server = ClientState.hud;
        if (server != null) {
            // The server's own numbers: true health (not the twenty the hearts are scaled to), Defense, the
            // Adventurer's level and XP, and Marks. Food is left out: the server holds hunger still.
            box(g, x, y, 184, 72, p);
            g.text(mc.font, "AERBORNE", x + 8, y + 7, p.theme.accent);
            g.text(mc.font, server.rank() + " · Lv " + server.level(), x + 66, y + 7, 0xFFF4F9FF);
            String hp = String.format(Locale.ROOT, "%s / %s", number(server.health()), number(server.maxHealth()))
                    + (server.absorption() > 0 ? " +" + number(server.absorption()) : "");
            bar(g, x + 8, y + 20, 168, server.health(), server.maxHealth(), server.recoveryHeld() ? 0xFF8C2F39 : 0xFFE05A5A, "Health " + hp);
            long need = server.experienceNeeded();
            bar(g, x + 8, y + 37, 168, need <= 0 ? 1 : server.experience(), need <= 0 ? 1 : need, 0xFF77C8F5,
                    need <= 0 ? "Adventurer XP  MAX" : "XP " + compact(server.experience()) + " / " + compact(need));
            g.text(mc.font, "Defense " + number(server.defense()), x + 8, y + 56, 0xFF9BE0A4);
            String marks = compact(server.marks()) + " Marks";
            g.text(mc.font, marks, x + 176 - mc.font.width(marks), y + 56, 0xFFF4C76B);
            y += 78;
            box(g, x, y, 184, 30, p);
            g.text(mc.font, mc.font.plainSubstrByWidth(server.progressTitle(), 168), x + 8, y + 6, 0xFFF4F9FF);
            g.text(mc.font, mc.font.plainSubstrByWidth(server.progressLine(), 168), x + 8, y + 18, 0xFFF4F9FF);
            y += 36;
        } else {
            box(g, x, y, 184, 72, p);
            var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(player.getUUID());
            g.text(mc.font, "AERBORNE", x + 8, y + 7, p.theme.accent);
            if (info != null) g.text(mc.font, info.getLatency() + " ms", x + 132, y + 7, 0xFF8E9AAF);
            bar(g, x + 8, y + 22, 168, player.getHealth(), player.getMaxHealth(), 0xFF70DBC3,
                    String.format(Locale.ROOT, "Health %.0f / %.0f", player.getHealth(), player.getMaxHealth()));
            bar(g, x + 8, y + 40, 168, player.getFoodData().getFoodLevel(), 20, 0xFFF4C76B,
                    "Food " + player.getFoodData().getFoodLevel() + " / 20");
            g.text(mc.font, "Armor " + player.getArmorValue() + "   XP level " + player.experienceLevel, x + 8, y + 59, 0xFFF4F9FF);
            y += 78;
        }
        if (p.coordinates && !player.isReducedDebugInfo()) {
            box(g, x, y, 184, 24, p);
            var pos = player.blockPosition();
            g.text(mc.font, pos.getX() + " / " + pos.getY() + " / " + pos.getZ() + "  " + player.getDirection().getName().toUpperCase(Locale.ROOT), x + 8, y + 8, 0xFFF4F9FF);
            y += 30;
        }
        if (p.durability) {
            box(g, x, y, 184, 40, p);
            for (int i = 0; i < SLOTS.length; i++) {
                var item = player.getItemBySlot(SLOTS[i]);
                int ix = x + 8 + i * 28;
                if (!item.isEmpty()) {
                    g.item(item, ix, y + 5);
                    if (item.isDamageableItem()) {
                        int percent = Math.round(100F * (item.getMaxDamage() - item.getDamageValue()) / item.getMaxDamage());
                        g.text(mc.font, percent + "%", ix - 1, y + 26, percent < 20 ? 0xFFFF8A80 : 0xFF8E9AAF);
                    }
                }
            }
            y += 46;
        }
        if (target.isPresent()) {
            var w = target.get();
            box(g, x, y, 184, 38, p);
            g.text(mc.font, mc.font.plainSubstrByWidth(w.name(), 168), x + 8, y + 7, p.theme.accent);
            g.text(mc.font, String.format(Locale.ROOT, "%.0f blocks away", w.distance(player.getX(), player.getY(), player.getZ())), x + 8, y + 23, 0xFFF4F9FF);
        }
        if (target.isPresent()) y += 44;
        if (server != null) y = AerborneOverlays.panels(g, mc.font, x, y, server, p);
        y = AerborneExtras.session(g, mc.font, x, y, p);
        y = AerborneSocial.party(g, mc.font, x, y, p);
        g.pose().popMatrix();
        AerborneOverlays.screenWide(g, mc, server, p, target.orElse(null));
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
        box(g, x, y, 184, 72, p);
        var font = Minecraft.getInstance().font;
        g.text(font, "AERBORNE", x + 8, y + 7, p.theme.accent);
        bar(g, x + 8, y + 22, 168, 18, 20, 0xFF70DBC3, "Health 18 / 20");
        bar(g, x + 8, y + 40, 168, 16, 20, 0xFFF4C76B, "Food 16 / 20");
        g.text(font, "Armor 12   XP level 24", x + 8, y + 59, 0xFFF4F9FF);
    }
    static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, ClientPreferences p) {
        g.fill(x, y, x + w, y + h, 0xE817243B);
        g.outline(x, y, w, h, 0xFF354760);
        g.fill(x, y, x + 2, y + h, p.theme.accent);
    }
    static void bar(GuiGraphicsExtractor g, int x, int y, int w, float value, float max, int color, String label) {
        g.fill(x, y, x + w, y + 14, 0xFF283752);
        g.fill(x, y, x + Math.round(w * Math.clamp(max > 0 ? value / max : 0, 0F, 1F)), y + 14, (color & 0x00FFFFFF) | 0x99000000);
        g.text(Minecraft.getInstance().font, label, x + 4, y + 3, 0xFFF4F9FF);
    }
}

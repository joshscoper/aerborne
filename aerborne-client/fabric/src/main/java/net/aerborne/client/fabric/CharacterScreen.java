package net.aerborne.client.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.EquipmentSlot;

/** Uses the real client inventory. Custom accessory slots stay in the server Equipment menu. */
public final class CharacterScreen extends AerborneScreen {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final String[] LABELS = {"Head", "Chest", "Legs", "Feet", "Main hand", "Off hand"};
    private int page, rows;
    public CharacterScreen(Screen parent) { super("Character Overview", parent); }
    @Override protected void init() {
        frame(460, 280);
        rows = Math.max(1, Math.min(3, (panelHeight - 126) / 42));
        int pages = (3 + rows - 1) / rows;
        page = Math.min(page, pages - 1);
        if (pages > 1) {
            button(left + panelWidth - 112, top + panelHeight - 30, 44, "<", () -> { page--; rebuildWidgets(); }).active = page > 0;
            button(left + panelWidth - 60, top + panelHeight - 30, 44, ">", () -> { page++; rebuildWidgets(); }).active = page < pages - 1;
        }
    }
    @Override protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var player = minecraft.player;
        if (player == null) { g.text(font, "Join Aerborne to view your character.", left + 14, top + 48, 0xFFF4F9FF); return; }
        g.text(font, fit(player.getName().getString(), panelWidth - 30), left + 14, top + 44, 0xFFF4C76B);
        String stats = String.format(java.util.Locale.ROOT, "Health %.0f/%.0f   Food %d/20   Armor %d", player.getHealth(), player.getMaxHealth(), player.getFoodData().getFoodLevel(), player.getArmorValue());
        g.text(font, fit(stats, panelWidth - 28), left + 14, top + 59, 0xFFF4F9FF);
        g.text(font, "Vanilla equipment - hover for full item details", left + 14, top + 75, 0xFF8E9AAF);
        int tileWidth = (panelWidth - 34) / 2;
        for (int i = 0; i < rows * 2 && page * rows * 2 + i < SLOTS.length; i++) {
            int index = page * rows * 2 + i;
            var stack = player.getItemBySlot(SLOTS[index]);
            int x = left + 12 + (i % 2) * (tileWidth + 10), y = top + 92 + (i / 2) * 42;
            g.fill(x, y, x + tileWidth, y + 36, 0xCC17243B);
            g.text(font, LABELS[index], x + 28, y + 4, 0xFF8E9AAF);
            g.text(font, fit(stack.isEmpty() ? "Empty" : stack.getHoverName().getString(), tileWidth - 32), x + 28, y + 16, 0xFFF4F9FF);
            if (!stack.isEmpty()) {
                g.item(stack, x + 5, y + 10);
                if (stack.isDamageableItem()) {
                    float remaining = (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
                    g.fill(x + 28, y + 29, x + tileWidth - 6, y + 32, 0xFF283752);
                    g.fill(x + 28, y + 29, x + 28 + Math.round((tileWidth - 34) * remaining), y + 32,
                            remaining < .2F ? 0xFFFF8A80 : ClientState.preferences.theme.accent);
                }
                if (mx >= x && mx < x + tileWidth && my >= y && my < y + 36) g.setTooltipForNextFrame(font, stack, mx, my);
            }
        }
    }
}

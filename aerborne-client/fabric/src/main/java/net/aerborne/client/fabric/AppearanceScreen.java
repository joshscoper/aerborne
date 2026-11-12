package net.aerborne.client.fabric;

import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public final class AppearanceScreen extends AerborneScreen {
    public AppearanceScreen(Screen parent) { super("Appearance & HUD", parent); }
    @Override protected void init() {
        frame(440, 276);
        var p = ClientState.preferences;
        int gap = 8, w = (panelWidth - 32) / 2, x = left + 12, y = top + 45;
        button(x, y, w, "Theme: " + p.theme.label, () -> change(() -> p.theme = ClientPreferences.Theme.values()[(p.theme.ordinal() + 1) % 3]));
        button(x + w + gap, y, w, "HUD: " + on(p.hud), () -> change(() -> p.hud = !p.hud));
        button(x, y + 26, w, "HUD size: " + p.scale + "%", () -> change(() -> p.scale = p.scale >= 125 ? 75 : p.scale + 25));
        button(x + w + gap, y + 26, w, "Edit HUD layout...", () -> minecraft.gui.setScreen(new HudEditScreen(this)));
        button(x, y + 52, w, "Coordinates: " + on(p.coordinates), () -> change(() -> p.coordinates = !p.coordinates));
        button(x + w + gap, y + 52, w, "Durability: " + on(p.durability), () -> change(() -> p.durability = !p.durability));
        button(left + panelWidth - 12 - w, top + panelHeight - 30, w, "Companion features...", () -> minecraft.gui.setScreen(new FeaturesScreen(this)));
    }
    private static String on(boolean value) { return value ? "On" : "Off"; }
    private void change(Runnable action) { action.run(); ClientState.save(); rebuildWidgets(); }
    @Override protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int y = top + 128;
        if (panelHeight >= 258) {
            g.text(font, "HUD PREVIEW / SAMPLE VALUES", left + 14, y - 12, Ui.MUTED);
            AerborneHud.drawPreview(g, left + 14, y, ClientState.preferences);
            g.textWithWordWrap(font, net.minecraft.network.chat.Component.literal(
                    "Your theme, your sky. Changes save automatically and apply on Aerborne."),
                    left + 220, y + 8, Math.max(70, panelWidth - 234), Ui.MUTED);
        } else {
            g.text(font, "Saved automatically", left + 14, y, 0xFF8E9AAF);
        }
    }
}

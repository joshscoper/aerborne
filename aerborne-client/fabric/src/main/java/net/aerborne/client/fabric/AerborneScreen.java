package net.aerborne.client.fabric;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Shared frame and navigation for native client screens. */
public abstract class AerborneScreen extends Screen {
    protected final Screen parent;
    protected int left, top, panelWidth, panelHeight;
    protected AerborneScreen(String title, Screen parent) { super(Component.literal(title)); this.parent = parent; }
    protected void frame(int preferredWidth, int preferredHeight) {
        panelWidth = Math.min(preferredWidth, width - 16);
        panelHeight = Math.min(preferredHeight, height - 16);
        left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
        button(left + 12, top + panelHeight - 30, 74, "Back", this::onClose);
    }
    protected Button button(int x, int y, int w, String label, Runnable action) {
        return addRenderableWidget(new AerborneMenuScreen.SpriteButton(x, y, w, 20,
                Component.literal(label), null, 0, false, button -> action.run()));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        drawFrame(g);
        drawContent(g, mx, my, delta);
        super.extractRenderState(g, mx, my, delta);
        if (!ClientState.saveError.isEmpty()) g.text(font, fit(ClientState.saveError, panelWidth - 106), left + 94, top + panelHeight - 12, 0xFFFFBBA0);
    }
    protected void drawFrame(GuiGraphicsExtractor g) {
        Ui.inset(g, left, top, panelWidth, panelHeight);
        g.fill(left + 4, top + 4, left + panelWidth - 4, top + 22, 0xFF1C2B42);
        g.fill(left + 4, top + 22, left + panelWidth - 4, top + 23, 0xFF8A6A2E);
        Ui.titleBar(g, font, title.getString(), left + 4, top + 4, panelWidth - 8, 18, 0);
    }
    protected abstract void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta);
    protected String fit(String s, int pixels) {
        return font.width(s) <= pixels ? s : font.plainSubstrByWidth(s, Math.max(0, pixels - font.width("..."))) + "...";
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}

package net.aerborne.client.fabric;

import net.aerborne.client.protocol.ClientPreferences;
import net.aerborne.client.protocol.HudLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * HUD edit mode, after the raid UIs players know: every element shows with sample content where it has none, and can
 * be dragged anywhere (snapping to the screen's edges and centre and to other elements; Shift drags freely), scaled
 * with the mouse wheel or the panel's buttons, and hidden with a right click. Arrow keys nudge the selected element.
 * Layouts save as they change and keep each element anchored to its nearest corner or edge.
 */
public final class HudEditScreen extends Screen {
    private static final int SNAP = 5, MARGIN = 4, GUIDE = 0xC0F4C76B;
    private static final float SCALE_STEP = 0.05F;
    private final Screen parent;
    private HudElements.Element selected, dragging;
    private double grabX, grabY;
    private int guideX = -1, guideY = -1;
    private int panelX, panelY, panelW, panelH;
    /** Where the player dragged the panel, from its place above centre; kept while the game runs. */
    private static int panelOffsetX, panelOffsetY;
    private boolean movingPanel;
    private double panelGrabX, panelGrabY;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Edit HUD layout"));
        this.parent = parent;
    }

    private ClientPreferences prefs() { return ClientState.preferences; }

    @Override protected void init() {
        panelW = 200;
        panelH = selected == null ? 62 : 88;
        // A little above centre, clear of the alerts over the hotbar, or wherever the player dragged it.
        panelX = Math.clamp(width / 2 - panelW / 2 + panelOffsetX, 0, Math.max(0, width - panelW));
        panelY = Math.clamp(height / 2 - panelH / 2 - 14 + panelOffsetY, 0, Math.max(0, height - panelH));
        int row = panelY + panelH - 26;
        button(panelX + 6, row, 64, "Reset all", () -> { prefs().layout.clear(); restoreSwitches(); changed(); });
        button(panelX + panelW - 70, row, 64, "Done", this::onClose);
        if (selected != null) {
            var e = selected;
            int y = panelY + 37;
            button(panelX + 6, y, 18, "-", () -> scale(e, -SCALE_STEP));
            button(panelX + 62, y, 18, "+", () -> scale(e, SCALE_STEP));
            button(panelX + 86, y, 52, e.visible(prefs()) ? "Hide" : "Show", () -> { e.setVisible(prefs(), !e.visible(prefs())); changed(); });
            button(panelX + 142, y, 52, "Reset", () -> { prefs().layout.remove(e.id()); e.setVisible(prefs(), true); changed(); });
        }
    }

    private void button(int x, int y, int w, String label, Runnable action) {
        addRenderableWidget(new AerborneMenuScreen.SpriteButton(x, y, w, 20, Component.literal(label), null, 0, false, b -> action.run()));
    }

    /** Reset all also brings back every element that was switched off. */
    private void restoreSwitches() { for (var e : HudElements.ALL) e.setVisible(prefs(), true); }

    private void changed() { ClientState.save(); rebuildWidgets(); }

    private void scale(HudElements.Element e, float step) {
        HudLayout layout = e.layout(prefs());
        float next = Math.round((layout.scale() + step) * 20) / 20F;
        prefs().layout.put(e.id(), layout.withScale(next));
        ClientState.save();
    }

    // ------------------------------------------------------------------ drawing

    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        // The world stays in view under a light shade, with faint thirds and the centre lines to place against.
        g.fill(0, 0, width, height, 0x38000000);
        for (int i = 1; i < 3; i++) {
            g.fill(width * i / 3, 0, width * i / 3 + 1, height, 0x18FFFFFF);
            g.fill(0, height * i / 3, width, height * i / 3 + 1, 0x18FFFFFF);
        }
        g.fill(width / 2, 0, width / 2 + 1, height, 0x30FFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, 0x30FFFFFF);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        extractBackground(g, mx, my, delta);
        var mc = minecraft;
        var p = prefs();
        HudElements.Element hovered = dragging != null ? dragging : at(mx, my);
        for (var e : HudElements.ALL) {
            int[] b = HudElements.bounds(e, p, width, height);
            if (e.visible(p)) HudElements.draw(g, mc, p, e, true);
            else {
                // Hidden: a shaded outline where it would be, so it can still be found and shown again.
                g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0x70101820);
                String label = e.label() + " (hidden)";
                g.text(font, font.plainSubstrByWidth(label, b[2] - 4), b[0] + 3, b[1] + b[3] / 2 - 4, 0xFF9AA3B5, true);
            }
            boolean focus = e == selected, hover = e == hovered;
            int colour = focus ? 0xFFF4C76B : hover ? 0xFFE6EEF8 : e.visible(p) ? 0x9077C8F5 : 0x60FFFFFF;
            g.outline(b[0] - 1, b[1] - 1, b[2] + 2, b[3] + 2, colour);
            if (focus || hover) {
                String tag = e.label() + "  " + Math.round(HudElements.scale(e, p) * 100) + "%";
                int ty = b[1] - 11 >= 0 ? b[1] - 11 : b[1] + b[3] + 2;
                int tw = font.width(tag) + 6;
                int tx = Math.clamp(b[0], 0, Math.max(0, width - tw));
                g.fill(tx, ty, tx + tw, ty + 10, 0xE00D1626);
                g.text(font, tag, tx + 3, ty + 1, focus ? 0xFFF4C76B : 0xFFE6EEF8, false);
            }
        }
        if (dragging != null) {
            if (guideX >= 0) g.fill(guideX, 0, guideX + 1, height, GUIDE);
            if (guideY >= 0) g.fill(0, guideY, width, guideY + 1, GUIDE);
        }
        drawPanel(g);
        super.extractRenderState(g, mx, my, delta);
    }

    private void drawPanel(GuiGraphicsExtractor g) {
        Ui.inset(g, panelX, panelY, panelW, panelH);
        // The title row is the panel's handle: drag it out of the way of whatever is being placed.
        g.fill(panelX + 3, panelY + 3, panelX + panelW - 3, panelY + 16, 0xFF1C2B42);
        String title = selected == null ? "Edit HUD" : selected.label();
        g.text(font, Ui.heading(font, title, panelW - 30), panelX + 7, panelY + 5, Ui.GOLD, true);
        g.text(font, "≡", panelX + panelW - 12, panelY + 5, Ui.MUTED, false);
        AerborneExtras.small(g, font, "Drag to move · Wheel to scale", panelX + 7, panelY + 20, Ui.MUTED);
        AerborneExtras.small(g, font, "Right-click hides · Shift: no snapping", panelX + 7, panelY + 27, Ui.MUTED);
        if (selected != null) {
            String scale = Math.round(selected.layout(prefs()).scale() * 100) + "%";
            g.text(font, scale, panelX + 43 - font.width(scale) / 2, panelY + 43, Ui.TEXT, true);
        }
    }

    // ------------------------------------------------------------------ input

    /** The topmost element under the point, hidden ones included. */
    private HudElements.Element at(double x, double y) {
        if (x >= panelX && x < panelX + panelW && y >= panelY && y < panelY + panelH) return null;
        var all = HudElements.ALL;
        for (int i = all.size() - 1; i >= 0; i--) {
            int[] b = HudElements.bounds(all.get(i), prefs(), width, height);
            if (x >= b[0] && x < b[0] + b[2] && y >= b[1] && y < b[1] + b[3]) return all.get(i);
        }
        return null;
    }

    private void select(HudElements.Element e) {
        if (e != selected) { selected = e; rebuildWidgets(); }
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.x() >= panelX && event.x() < panelX + panelW && event.y() >= panelY && event.y() < panelY + 17) {
            movingPanel = true;
            panelGrabX = event.x() - panelX;
            panelGrabY = event.y() - panelY;
            return true;
        }
        var e = at(event.x(), event.y());
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && e != null) {
            e.setVisible(prefs(), !e.visible(prefs()));
            select(e);
            changed();
            return true;
        }
        select(e);
        if (e != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int[] b = HudElements.bounds(e, prefs(), width, height);
            dragging = e;
            grabX = event.x() - b[0];
            grabY = event.y() - b[1];
        }
        return e != null;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (movingPanel) {
            int x = (int) Math.round(event.x() - panelGrabX), y = (int) Math.round(event.y() - panelGrabY);
            panelOffsetX = x - (width / 2 - panelW / 2);
            panelOffsetY = y - (height / 2 - panelH / 2 - 14);
            rebuildWidgets();
            return true;
        }
        if (dragging == null) return super.mouseDragged(event, dx, dy);
        int[] b = HudElements.bounds(dragging, prefs(), width, height);
        int x = (int) Math.round(event.x() - grabX), y = (int) Math.round(event.y() - grabY);
        guideX = -1; guideY = -1;
        if (!event.hasShiftDown()) {
            int[] sx = snap(x, b[2], true), sy = snap(y, b[3], false);
            x = sx[0]; guideX = sx[1]; y = sy[0]; guideY = sy[1];
        }
        x = Math.clamp(x, 0, Math.max(0, width - b[2]));
        y = Math.clamp(y, 0, Math.max(0, height - b[3]));
        place(dragging, x, y, b[2], b[3]);
        return true;
    }

    /**
     * Snaps one axis: to the screen margin at either side, to the centre, and to other elements' edges (side by side
     * or lined up). Returns the position and where to draw the guide, or -1 for none.
     */
    private int[] snap(int pos, int size, boolean horizontal) {
        int screen = horizontal ? width : height;
        int best = pos, guide = -1, distance = SNAP + 1;
        int[][] targets = {{MARGIN, MARGIN}, {screen - MARGIN - size, screen - MARGIN}, {screen / 2 - size / 2, screen / 2}};
        for (int[] t : targets) if (Math.abs(pos - t[0]) < distance) { distance = Math.abs(pos - t[0]); best = t[0]; guide = t[1]; }
        for (var other : HudElements.ALL) {
            if (other == dragging) continue;
            int[] o = HudElements.bounds(other, prefs(), width, height);
            int start = horizontal ? o[0] : o[1], end = start + (horizontal ? o[2] : o[3]);
            int[][] edges = {{start, start}, {end - size, end}, {end + 2, end + 1}, {start - size - 2, start - 1}};
            for (int[] t : edges) if (Math.abs(pos - t[0]) < distance) { distance = Math.abs(pos - t[0]); best = t[0]; guide = t[1]; }
        }
        return new int[] {best, distance <= SNAP ? guide : -1};
    }

    private void place(HudElements.Element e, int x, int y, int w, int h) {
        HudLayout old = e.layout(prefs());
        prefs().layout.put(e.id(), HudLayout.at(x, y, w, h, width, height, old.scale(), old.visible()));
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (movingPanel) { movingPanel = false; return true; }
        if (dragging != null) { dragging = null; guideX = -1; guideY = -1; ClientState.save(); return true; }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        var e = at(mx, my);
        if (e == null) e = selected;
        if (e == null || dy == 0) return super.mouseScrolled(mx, my, dx, dy);
        select(e);
        scale(e, dy > 0 ? SCALE_STEP : -SCALE_STEP);
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (selected != null) {
            int step = event.hasShiftDown() ? 5 : 1, dx = 0, dy = 0;
            switch (event.key()) {
                case GLFW.GLFW_KEY_LEFT -> dx = -step;
                case GLFW.GLFW_KEY_RIGHT -> dx = step;
                case GLFW.GLFW_KEY_UP -> dy = -step;
                case GLFW.GLFW_KEY_DOWN -> dy = step;
                case GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_BACKSPACE -> { selected.setVisible(prefs(), !selected.visible(prefs())); changed(); return true; }
                default -> { }
            }
            if (dx != 0 || dy != 0) {
                int[] b = HudElements.bounds(selected, prefs(), width, height);
                place(selected, Math.clamp(b[0] + dx, 0, Math.max(0, width - b[2])), Math.clamp(b[1] + dy, 0, Math.max(0, height - b[3])), b[2], b[3]);
                ClientState.save();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override public void onClose() {
        ClientState.save();
        minecraft.gui.setScreen(parent);
    }

    @Override public boolean isPauseScreen() { return false; }
}

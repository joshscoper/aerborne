package net.aerborne.client.fabric;

import java.util.List;
import java.util.UUID;
import net.aerborne.client.protocol.ClientPreferences.Waypoint;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class WaypointScreen extends AerborneScreen {
    private EditBox name;
    private int page, rows;
    private String scope = "", draft = "", message = "";
    public WaypointScreen(Screen parent) { super("Personal Waypoints", parent); }
    private List<Waypoint> points() { return ClientState.preferences.waypoints.stream().filter(w -> w.scope().equals(scope)).toList(); }
    @Override protected void init() {
        frame(460, 284);
        scope = ClientState.scope();
        int x = left + 12;
        name = addRenderableWidget(new EditBox(font, x, top + 47, Math.max(80, panelWidth - 152), 20, Component.literal("Waypoint name")));
        name.setMaxLength(32); name.setHint(Component.literal("Name this location")); name.setValue(draft);
        name.setResponder(value -> draft = value);
        button(left + panelWidth - 132, top + 47, 120, "Save here", this::saveHere).active = !scope.isEmpty();
        rows = Math.max(1, (panelHeight - 128) / 32);
        List<Waypoint> points = points();
        int pages = Math.max(1, (points.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (int i = 0; i < rows && page * rows + i < points.size(); i++) {
            Waypoint point = points.get(page * rows + i);
            int y = top + 90 + i * 32;
            boolean tracked = point.id().equals(ClientState.preferences.tracked);
            button(left + panelWidth - 126, y + 1, 70, tracked ? "Untrack" : "Track", () -> {
                ClientState.preferences.tracked = tracked ? "" : point.id(); ClientState.save(); rebuildWidgets();
            });
            button(left + panelWidth - 50, y + 1, 38, "X", () -> minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    ClientState.preferences.waypoints.removeIf(w -> w.id().equals(point.id()));
                    if (point.id().equals(ClientState.preferences.tracked)) ClientState.preferences.tracked = "";
                    ClientState.save();
                }
                minecraft.gui.setScreen(this);
            }, Component.literal("Delete waypoint?"), Component.literal(point.name()))));
        }
        button(left + panelWidth - 112, top + panelHeight - 30, 44, "<", () -> { page--; rebuildWidgets(); }).active = page > 0;
        button(left + panelWidth - 60, top + panelHeight - 30, 44, ">", () -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
    }
    private void saveHere() {
        if (scope.isEmpty() || !scope.equals(ClientState.scope()) || minecraft.player == null) return;
        if (draft.isBlank()) { message = "Give this location a name first."; return; }
        if (ClientState.preferences.waypoints.size() >= 32) { message = "32 saved locations reached. Delete one to add more."; return; }
        var pos = minecraft.player.blockPosition();
        try {
            Waypoint w = new Waypoint(UUID.randomUUID().toString(), scope, draft.strip(), pos.getX(), pos.getY(), pos.getZ());
            ClientState.preferences.waypoints.add(w); ClientState.preferences.tracked = w.id();
            ClientState.save(); draft = ""; message = "Location saved and tracked."; rebuildWidgets();
        } catch (IllegalArgumentException e) { message = "Use a short, readable name for this location."; }
    }
    @Override public void tick() {
        if (!scope.equals(ClientState.scope())) { message = ""; page = 0; rebuildWidgets(); }
    }
    @Override protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        String note = scope.isEmpty() ? "Waypoints need world data from the server bridge." : message.isEmpty() ? "Only locations in this world are shown. No teleporting." : message;
        g.text(font, fit(note, panelWidth - 28), left + 14, top + 76, 0xFF8E9AAF);
        List<Waypoint> points = points();
        if (points.isEmpty()) g.textWithWordWrap(font, Component.literal(scope.isEmpty()
                ? "Saving is unavailable until the server runs AerborneClientBridge and sends world data. Local menus and HUD still work."
                : "Save a favorite place to track it on your HUD."), left + 14, top + 100, panelWidth - 28, 0xFFF4F9FF);
        for (int i = 0; i < rows && page * rows + i < points.size(); i++) {
            var point = points.get(page * rows + i);
            int y = top + 90 + i * 32;
            g.text(font, fit(point.name(), panelWidth - 154), left + 14, y + 2, ClientState.preferences.theme.accent);
            g.text(font, fit(point.x() + " / " + point.y() + " / " + point.z(), panelWidth - 154), left + 14, y + 14, 0xFF8E9AAF);
        }
    }
}

package net.aerborne.client.fabric;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Switches for every companion feature, two columns, saved as they change. */
public final class FeaturesScreen extends AerborneScreen {
    private int page, pages, rows;
    private record Toggle(String label, Predicate<ClientPreferences> get, Consumer<ClientPreferences> flip) { }

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle("Minimap", p -> p.minimap, p -> p.minimap = !p.minimap),
            new Toggle("Compass", p -> p.compass, p -> p.compass = !p.compass),
            new Toggle("Enemy nameplates", p -> p.target, p -> p.target = !p.target),
            new Toggle("Effects on nameplates", p -> p.plateEffects, p -> p.plateEffects = !p.plateEffects),
            new Toggle("Target frame", p -> p.targetFrame, p -> p.targetFrame = !p.targetFrame),
            new Toggle("Damage numbers", p -> p.damageNumbers, p -> p.damageNumbers = !p.damageNumbers),
            new Toggle("Effect timers", p -> p.effects, p -> p.effects = !p.effects),
            new Toggle("Low health warning", p -> p.vignette, p -> p.vignette = !p.vignette),
            new Toggle("Waypoint marker", p -> p.waypointMarker, p -> p.waypointMarker = !p.waypointMarker),
            new Toggle("Death waypoint", p -> p.deathWaypoint, p -> p.deathWaypoint = !p.deathWaypoint),
            new Toggle("Session rates", p -> p.session, p -> p.session = !p.session),
            new Toggle("Party frames", p -> p.partyFrames, p -> p.partyFrames = !p.partyFrames),
            new Toggle("Hide vanilla bars", p -> p.hideVanilla, p -> p.hideVanilla = !p.hideVanilla),
            new Toggle("Native menus", p -> p.nativeMenus, p -> p.nativeMenus = !p.nativeMenus),
            new Toggle("Throttle when unfocused", p -> p.throttleUnfocused, p -> p.throttleUnfocused = !p.throttleUnfocused),
            new Toggle("Rarity colours", p -> p.rarity, p -> p.rarity = !p.rarity),
            new Toggle("Aerborne chat style", p -> p.styledChat, p -> p.styledChat = !p.styledChat),
            new Toggle("Chat timestamps", p -> p.chatTimestamps, p -> p.chatTimestamps = !p.chatTimestamps),
            new Toggle("Chat mentions", p -> p.chatMentions, p -> p.chatMentions = !p.chatMentions),
            new Toggle("Fold repeated chat", p -> p.chatStack, p -> p.chatStack = !p.chatStack),
            new Toggle("Track chat coordinates", p -> p.chatCoordinates, p -> p.chatCoordinates = !p.chatCoordinates));

    public FeaturesScreen(Screen parent) { super("Companion Features", parent); }

    @Override protected void init() {
        frame(480, 300);
        rows = Math.max(1, Math.min(8, (panelHeight - 106) / 24));
        int perPage = rows * 2;
        pages = (TOGGLES.size() + perPage - 1) / perPage;
        page = Math.min(page, pages - 1);
        var p = ClientState.preferences;
        int gap = 8, w = (panelWidth - 32) / 2, x = left + 12, y = top + 45;
        for (int i = 0; i < perPage && page * perPage + i < TOGGLES.size(); i++) {
            Toggle toggle = TOGGLES.get(page * perPage + i);
            button(x + (i % 2) * (w + gap), y + (i / 2) * 24, w, toggle.label() + ": " + (toggle.get().test(p) ? "On" : "Off"), () -> {
                toggle.flip().accept(p);
                ClientState.save();
                rebuildWidgets();
            });
        }
        if (pages > 1) {
            button(left + panelWidth - 132, top + panelHeight - 30, 56, "Previous", () -> { page--; rebuildWidgets(); }).active = page > 0;
            button(left + panelWidth - 70, top + panelHeight - 30, 58, "Next", () -> { page++; rebuildWidgets(); }).active = page + 1 < pages;
        }
    }

    @Override protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.text(font, "Choose what stays on your screen.", left + 14, top + 28, Ui.MUTED);
        g.text(font, fit("Item lock: press L over an inventory slot.", panelWidth - 28), left + 14, top + panelHeight - 48, Ui.MUTED);
        if (pages > 1) g.text(font, (page + 1) + " / " + pages, left + 98, top + panelHeight - 24, Ui.MUTED);
    }
}

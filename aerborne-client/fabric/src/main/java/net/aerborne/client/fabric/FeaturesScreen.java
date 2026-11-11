package net.aerborne.client.fabric;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/** Switches for every companion feature, two columns, saved as they change. */
public final class FeaturesScreen extends AerborneScreen {
    private record Toggle(String label, Predicate<ClientPreferences> get, Consumer<ClientPreferences> flip) { }

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle("Minimap", p -> p.minimap, p -> p.minimap = !p.minimap),
            new Toggle("Compass", p -> p.compass, p -> p.compass = !p.compass),
            new Toggle("Enemy nameplates", p -> p.target, p -> p.target = !p.target),
            new Toggle("Damage numbers", p -> p.damageNumbers, p -> p.damageNumbers = !p.damageNumbers),
            new Toggle("Effect timers", p -> p.effects, p -> p.effects = !p.effects),
            new Toggle("Low health warning", p -> p.vignette, p -> p.vignette = !p.vignette),
            new Toggle("Waypoint marker", p -> p.waypointMarker, p -> p.waypointMarker = !p.waypointMarker),
            new Toggle("Death waypoint", p -> p.deathWaypoint, p -> p.deathWaypoint = !p.deathWaypoint),
            new Toggle("Session rates", p -> p.session, p -> p.session = !p.session),
            new Toggle("Party frames", p -> p.partyFrames, p -> p.partyFrames = !p.partyFrames),
            new Toggle("Rarity colours", p -> p.rarity, p -> p.rarity = !p.rarity),
            new Toggle("Chat timestamps", p -> p.chatTimestamps, p -> p.chatTimestamps = !p.chatTimestamps),
            new Toggle("Chat mentions", p -> p.chatMentions, p -> p.chatMentions = !p.chatMentions),
            new Toggle("Fold repeated chat", p -> p.chatStack, p -> p.chatStack = !p.chatStack),
            new Toggle("Track chat coordinates", p -> p.chatCoordinates, p -> p.chatCoordinates = !p.chatCoordinates));

    public FeaturesScreen(Screen parent) { super("Companion Features", parent); }

    @Override protected void init() {
        int rows = (TOGGLES.size() + 1) / 2;
        frame(440, 70 + rows * 24 + 34);
        var p = ClientState.preferences;
        int gap = 8, w = (panelWidth - 32) / 2, x = left + 12, y = top + 45;
        for (int i = 0; i < TOGGLES.size(); i++) {
            Toggle toggle = TOGGLES.get(i);
            button(x + (i % 2) * (w + gap), y + (i / 2) * 24, w, toggle.label() + ": " + (toggle.get().test(p) ? "On" : "Off"), () -> {
                toggle.flip().accept(p);
                ClientState.save();
                rebuildWidgets();
            });
        }
    }

    @Override protected void drawContent(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int rows = (TOGGLES.size() + 1) / 2;
        g.text(font, "Item lock: press L over an inventory slot.", left + 14, top + 45 + rows * 24 + 4, 0xFF8E9AAF);
    }
}

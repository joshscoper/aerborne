package net.aerborne.client.fabric;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.aerborne.client.protocol.ClientPreferences;
import net.aerborne.client.protocol.HudLayout;
import net.aerborne.client.protocol.HudLayout.Anchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The HUD as separate elements a player can move, scale and hide in edit mode (HudEditScreen), as in a raid UI.
 * Each element draws itself at (0, 0) in its own space; this class places it from its layout and scales it. An
 * element's size is measured from what it drew (panels through {@link #track}), and the last size places the next
 * frame, so anchoring to a right or bottom edge holds as content grows.
 */
public final class HudElements {
    private HudElements() { }

    @FunctionalInterface interface Painter { boolean paint(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, boolean preview); }
    /** A preference that already switches the element on or off (FeaturesScreen); null to use the layout's own flag. */
    record Switch(Predicate<ClientPreferences> get, java.util.function.BiConsumer<ClientPreferences, Boolean> set) { }

    record Element(String id, String label, HudLayout fallback, int[] guess, Switch toggle, Painter painter) {
        HudLayout layout(ClientPreferences p) { return p.layout.getOrDefault(id, fallback); }
        boolean visible(ClientPreferences p) { return toggle != null ? toggle.get().test(p) : layout(p).visible(); }
        void setVisible(ClientPreferences p, boolean value) {
            if (toggle != null) toggle.set().accept(p, value);
            else p.layout.put(id, layout(p).withVisible(value));
        }
    }

    private static HudLayout at(Anchor anchor, int dx, int dy, float scale) { return new HudLayout(anchor, dx, dy, scale, true); }

    /** Every element, back to front. The defaults keep the middle of the screen clear: frames at the top left,
     * map, buffs and objectives at the top right, gear and rates at the bottom right, alerts over the hotbar. */
    static final List<Element> ALL = List.of(
            new Element("player", "Player frame", at(Anchor.TOP_LEFT, 4, 4, 0.75F), new int[] {184, 62}, null,
                    (g, mc, p, preview) -> AerborneHud.player(g, mc, p)),
            new Element("target", "Target frame", at(Anchor.TOP_LEFT, 146, 4, 0.75F), new int[] {AerborneTargetFrame.WIDTH, 69},
                    new Switch(p -> p.targetFrame, (p, v) -> p.targetFrame = v), AerborneTargetFrame::paint),
            new Element("party", "Party frames", at(Anchor.TOP_LEFT, 4, 56, 0.75F), new int[] {140, 66},
                    new Switch(p -> p.partyFrames, (p, v) -> p.partyFrames = v),
                    (g, mc, p, preview) -> AerborneSocial.party(g, mc.font, 0, 0, p, AerborneSocial.members(preview)) > 0),
            new Element("minimap", "Minimap", at(Anchor.TOP_RIGHT, -4, 4, 0.8F), new int[] {AerborneOverlays.MAP_SIZE, AerborneOverlays.MAP_SIZE},
                    new Switch(p -> p.minimap, (p, v) -> p.minimap = v),
                    (g, mc, p, preview) -> AerborneOverlays.minimapElement(g, mc, p.trackedFor(ClientState.scope()).orElse(null))),
            new Element("compass", "Compass", at(Anchor.TOP_RIGHT, -4, 74, 0.8F), new int[] {AerborneOverlays.COMPASS_WIDTH, AerborneOverlays.COMPASS_HEIGHT},
                    new Switch(p -> p.compass, (p, v) -> p.compass = v),
                    (g, mc, p, preview) -> AerborneOverlays.compassElement(g, mc, p.trackedFor(ClientState.scope()).orElse(null))),
            new Element("coordinates", "Coordinates", at(Anchor.TOP_RIGHT, -4, 89, 0.8F), new int[] {90, 16},
                    new Switch(p -> p.coordinates, (p, v) -> p.coordinates = v), (g, mc, p, preview) -> AerborneHud.coordinates(g, mc, p)),
            new Element("effects", "Buffs and debuffs", at(Anchor.TOP_RIGHT, -76, 4, 0.85F), new int[] {102, 25},
                    new Switch(p -> p.effects, (p, v) -> p.effects = v), AerborneExtras::buffs),
            new Element("objectives", "Objectives", at(Anchor.TOP_RIGHT, -4, 106, 0.7F), new int[] {184, 60}, null, AerborneHud::objectives),
            new Element("session", "Session rates", at(Anchor.BOTTOM_RIGHT, -4, -32, 0.75F), new int[] {170, 18},
                    new Switch(p -> p.session, (p, v) -> p.session = v), (g, mc, p, preview) -> AerborneExtras.session(g, mc.font, p, preview)),
            new Element("gear", "Gear durability", at(Anchor.BOTTOM_RIGHT, -4, -4, 0.8F), new int[] {126, 26},
                    new Switch(p -> p.durability, (p, v) -> p.durability = v), AerborneHud::gear),
            new Element("toasts", "Toasts", at(Anchor.TOP, 0, 62, 0.85F), new int[] {AerborneSocial.TOAST_WIDTH, AerborneSocial.TOAST_HEIGHT}, null,
                    (g, mc, p, preview) -> AerborneSocial.toasts(g, mc, preview)),
            new Element("alerts", "Notices and skill XP", at(Anchor.BOTTOM, 0, -58, 0.85F), new int[] {AerborneOverlays.ALERT_WIDTH, 42}, null,
                    (g, mc, p, preview) -> AerborneOverlays.alerts(g, mc, ClientState.hud, p, preview)));

    static Element byId(String id) {
        for (Element e : ALL) if (e.id().equals(id)) return e;
        return null;
    }

    // ------------------------------------------------------------------ measuring

    private static int right, bottom;
    private static final Map<String, int[]> sizes = new HashMap<>();

    /** Records a drawn rectangle in the current element's own space, so the element's size is known. */
    static void track(int x, int y, int w, int h) {
        right = Math.max(right, x + w);
        bottom = Math.max(bottom, y + h);
    }

    /** The element's last measured unscaled size, or its guess before it has drawn. */
    static int[] size(Element e) { return sizes.getOrDefault(e.id(), e.guess()); }

    /** The total scale: the element's own times the HUD size setting. */
    static float scale(Element e, ClientPreferences p) { return e.layout(p).scale() * p.scale / 100F; }

    /** Where the element sits on this screen, scaled: {x, y, w, h}, kept on screen. */
    static int[] bounds(Element e, ClientPreferences p, int screenWidth, int screenHeight) {
        int[] size = size(e);
        float scale = scale(e, p);
        int w = Math.max(8, Math.round(size[0] * scale)), h = Math.max(8, Math.round(size[1] * scale));
        HudLayout layout = e.layout(p);
        int x = Math.clamp(layout.left(screenWidth, w), 0, Math.max(0, screenWidth - w));
        int y = Math.clamp(layout.top(screenHeight, h), 0, Math.max(0, screenHeight - h));
        return new int[] {x, y, w, h};
    }

    // ------------------------------------------------------------------ drawing

    /** Draws one element in place; returns whether it drew anything. */
    static boolean draw(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, Element e, boolean preview) {
        int[] at = bounds(e, p, g.guiWidth(), g.guiHeight());
        float scale = scale(e, p);
        g.pose().pushMatrix();
        g.pose().translate(at[0], at[1]);
        g.pose().scale(scale, scale);
        right = 0; bottom = 0;
        boolean drew;
        try { drew = e.painter().paint(g, mc, p, preview); }
        finally { g.pose().popMatrix(); }
        if (drew && right > 0 && bottom > 0) sizes.put(e.id(), new int[] {right, bottom});
        return drew;
    }

    static void drawAll(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, boolean preview) {
        for (Element e : ALL) if (e.visible(p)) draw(g, mc, p, e, preview);
    }
}

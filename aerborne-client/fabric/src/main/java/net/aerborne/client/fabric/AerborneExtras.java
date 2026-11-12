package net.aerborne.client.fabric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * The companion extras on the HUD: effect timers in place of the vanilla icons, a red edge at low health,
 * damage numbers over creatures, Marks and XP earned per hour this session, a marker in the world for the
 * tracked waypoint, and a waypoint set where the player last died. Each can be switched off in FeaturesScreen.
 */
final class AerborneExtras {
    private AerborneExtras() { }

    private static final int TEXT = 0xFFF4F9FF, MUTED = 0xFF8E9AAF, GOLD = 0xFFF4C76B;

    // ------------------------------------------------------------------ effects

    /** Active effects as a column of icon, name and time, under the map in the free corner. */
    private static final int TILE = 18, STEP = 21, ROW = 29, PER_ROW = 8;

    /** Sample effects for edit mode, with how full each tile's drain bar is. */
    private static List<AerborneEffects.Shown> sampleEffects(long now) {
        List<AerborneEffects.Shown> list = new ArrayList<>();
        String[][] sample = {{"poison", "1", "8"}, {"slowness", "0", "40"}, {"strength", "1", "220"}, {"speed", "0", "95"}, {"night_vision", "0", "-1"}};
        for (String[] e : sample) {
            Identifier key = Identifier.withDefaultNamespace(e[0]);
            long seconds = Long.parseLong(e[2]);
            list.add(new AerborneEffects.Shown(key, net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getValue(key),
                    Integer.parseInt(e[1]), seconds < 0 ? -1 : now + seconds * 1000));
        }
        return list;
    }

    /**
     * The player's effects as a grid of icon tiles, debuffs first: red-edged for harm, gold for help, each with a
     * drain bar and the time left beneath, blinking when nearly over. Returns false when there are none.
     */
    static boolean buffs(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, boolean preview) {
        if (mc.player == null) return false;
        long now = System.currentTimeMillis();
        List<AerborneEffects.Shown> active = AerborneEffects.on(mc.player);
        boolean sample = active.isEmpty() && preview;
        if (active.isEmpty() && !sample) return false;
        List<AerborneEffects.Shown> list = sample ? sampleEffects(now) : active;
        int count = list.size(), cols = Math.min(PER_ROW, count), rows = (count + PER_ROW - 1) / PER_ROW;
        HudElements.track(0, 0, cols * STEP - (STEP - TILE), rows * ROW - 4);
        for (int i = 0; i < count; i++) {
            AerborneEffects.Shown s = list.get(i);
            int x = (i % PER_ROW) * STEP, y = (i / PER_ROW) * ROW;
            float fraction = sample ? (s.endless() ? 1 : 0.2F + 0.15F * i) : s.fraction(now);
            effectTile(g, mc.font, s, x, y, fraction, s.secondsLeft(now), now);
        }
        return true;
    }

    /** One effect tile: icon on a tinted square, level at the corner, drain bar and time beneath. */
    static void effectTile(GuiGraphicsExtractor g, Font font, AerborneEffects.Shown s, int x, int y, float fraction, long seconds, long now) {
        boolean harmful = s.harmful();
        g.fill(x, y, x + TILE, y + TILE, harmful ? 0xD03A1218 : 0xD0142236);
        g.outline(x, y, TILE, TILE, harmful ? 0xFFB84A4A : 0xFF8A6A2E);
        boolean ending = seconds >= 0 && seconds <= 5;
        float alpha = ending ? 0.5F + 0.5F * (float) Math.abs(Math.sin(now / 180.0)) : 1F;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, s.sprite(), x + 1, y + 1, TILE - 2, TILE - 2, net.minecraft.util.ARGB.white(alpha));
        if (s.amplifier() > 0) {
            String level = AerborneEffects.roman(s.amplifier() + 1);
            small(g, font, level, x + TILE - 1 - Math.round(font.width(level) * 0.75F), y + 1, 0xFFFFFFFF);
        }
        g.fill(x, y + TILE + 1, x + TILE, y + TILE + 3, 0xFF0D1626);
        g.fill(x, y + TILE + 1, x + Math.round(TILE * Math.clamp(fraction, 0F, 1F)), y + TILE + 3, harmful ? 0xFFD05050 : GOLD);
        String time = seconds < 0 ? "∞" : AerborneEffects.shortTime(seconds);
        small(g, font, time, x + TILE / 2 - Math.round(font.width(time) * 0.375F), y + TILE + 5, ending ? 0xFFF6C85F : MUTED);
    }

    static void small(GuiGraphicsExtractor g, Font font, String text, int x, int y, int colour) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.75F, 0.75F);
        g.text(font, text, 0, 0, colour, true);
        g.pose().popMatrix();
    }

    private static String roman(int value) {
        return switch (value) { case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> Integer.toString(value); };
    }

    static String clock(long seconds) {
        return seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    // ------------------------------------------------------------------ low health

    static void vignette(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p) {
        if (!p.vignette || mc.player == null) return;
        HudPayload hud = ClientState.hud;
        float health = hud != null ? hud.health() : mc.player.getHealth();
        float max = hud != null ? hud.maxHealth() : mc.player.getMaxHealth();
        float ratio = max <= 0 ? 1 : health / max;
        if (ratio > 0.3F || health <= 0) return;
        // Stronger as health falls, with a slow pulse.
        float strength = (0.3F - ratio) / 0.3F;
        float pulse = 0.75F + 0.25F * (float) Math.sin(System.nanoTime() / 2.5e8);
        int alpha = Math.round(150 * Math.min(1, 0.35F + strength) * pulse);
        int edge = alpha << 24 | 0x8A0F14;
        int w = g.guiWidth(), h = g.guiHeight(), band = Math.max(24, h / 6);
        g.fillGradient(0, 0, w, band, edge, 0x008A0F14);
        g.fillGradient(0, h - band, w, h, 0x008A0F14, edge);
        for (int i = 0; i < band; i += 2) {
            int a = Math.round(alpha * (1 - i / (float) band));
            g.fill(i, 0, i + 2, h, a << 24 | 0x8A0F14);
            g.fill(w - i - 2, 0, w - i, h, a << 24 | 0x8A0F14);
        }
    }

    // ------------------------------------------------------------------ damage numbers

    private record Popup(Vec3 at, float amount, long born, double drift) { }
    private static final Map<Integer, Float> lastHealth = new HashMap<>();
    private static final List<Popup> popups = new ArrayList<>();
    private static final long POPUP_NANOS = 900_000_000L;

    /** Each tick: a creature that lost or regained health gets a number over it. */
    static void tick(Minecraft mc) {
        deathWaypoint(mc);
        if (mc.level == null || mc.player == null || !ClientState.preferences.damageNumbers) { lastHealth.clear(); return; }
        Map<Integer, Float> seen = new HashMap<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || entity.distanceToSqr(mc.player) > 24 * 24) continue;
            float health = living.getHealth();
            seen.put(entity.getId(), health);
            Float before = lastHealth.get(entity.getId());
            if (before == null || Math.abs(before - health) < 0.05F || popups.size() > 48) continue;
            popups.add(new Popup(living.position().add(0, living.getBbHeight() * 0.75, 0), health - before, System.nanoTime(),
                    (Math.random() - 0.5) * 0.6));
        }
        lastHealth.clear();
        lastHealth.putAll(seen);
    }

    static void damageNumbers(GuiGraphicsExtractor g, Minecraft mc) {
        if (popups.isEmpty()) return;
        long now = System.nanoTime();
        Iterator<Popup> it = popups.iterator();
        while (it.hasNext()) {
            Popup popup = it.next();
            float age = (now - popup.born()) / (float) POPUP_NANOS;
            if (age >= 1) { it.remove(); continue; }
            float[] at = project(mc, g, popup.at().add(popup.drift(), age * 0.9, 0));
            if (at == null) continue;
            boolean heal = popup.amount() > 0;
            String text = (heal ? "+" : "") + AerborneHud.number(Math.round(Math.abs(popup.amount()) * 10) / 10F);
            int alpha = Math.round(255 * Math.min(1, (1 - age) * 2.5F));
            int colour = alpha << 24 | (heal ? 0x55E58A : popup.amount() < -10 ? 0xFFB347 : 0xFF6B6B);
            float scale = 1.2F - age * 0.3F;
            g.pose().pushMatrix();
            g.pose().translate(at[0], at[1]);
            g.pose().scale(scale, scale);
            g.text(mc.font, text, -mc.font.width(text) / 2, -4, colour, true);
            g.pose().popMatrix();
        }
    }

    /** Screen position of a point in the world, or null when it is behind the camera or off screen. */
    static float[] project(Minecraft mc, GuiGraphicsExtractor g, Vec3 point) {
        Camera camera = mc.gameRenderer.mainCamera();
        if (!camera.isInitialized()) return null;
        Vec3 eye = camera.position();
        Vector4f clip = new Vector4f((float) (point.x - eye.x), (float) (point.y - eye.y), (float) (point.z - eye.z), 1F);
        camera.getViewRotationProjectionMatrix(new Matrix4f()).transform(clip);
        if (clip.w <= 0.05F) return null;
        float nx = clip.x / clip.w, ny = clip.y / clip.w;
        if (Math.abs(nx) > 1.2F || Math.abs(ny) > 1.2F) return null;
        return new float[] {(nx + 1) / 2 * g.guiWidth(), (1 - ny) / 2 * g.guiHeight()};
    }

    // ------------------------------------------------------------------ waypoint marker

    private static final Identifier PIN = Identifier.fromNamespaceAndPath("aerborne_client", "icon/menu/your_island");

    /** A marker over the tracked waypoint, with its name and distance; at the screen edge when it is behind you. */
    static void waypointMarker(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, ClientPreferences.Waypoint tracked) {
        if (!p.waypointMarker || tracked == null || mc.player == null) return;
        Vec3 point = new Vec3(tracked.x() + 0.5, tracked.y() + 1.5, tracked.z() + 0.5);
        double distance = point.distanceTo(mc.player.position());
        if (distance < 4) return;
        float[] at = project(mc, g, point);
        if (at == null) return;
        quietMarker(g, mc, at, GOLD, tracked.name(), distance);
    }

    /**
     * A marker that stays out of the way: a small translucent pin with the distance in small type. The name only
     * shows while the player looks towards it (the marker is near the middle of the screen).
     */
    static void quietMarker(GuiGraphicsExtractor g, Minecraft mc, float[] at, int colour, String name, double distance) {
        int x = Math.round(Math.max(12, Math.min(g.guiWidth() - 12, at[0])));
        int y = Math.round(Math.max(12, Math.min(g.guiHeight() - 40, at[1])));
        double fromCentre = Math.hypot(x - g.guiWidth() / 2.0, y - g.guiHeight() / 2.0);
        boolean looking = fromCentre < 48;
        int alpha = looking ? 0xF0 : 0xA0;
        int body = alpha << 24 | colour & 0xFFFFFF, edge = alpha << 24 | 0x0D1A2B;
        // The pin: a round head on a short point.
        g.fill(x - 3, y - 8, x + 4, y - 2, edge);
        g.fill(x - 2, y - 9, x + 3, y - 1, edge);
        g.fill(x - 2, y - 7, x + 3, y - 3, body);
        g.fill(x - 1, y - 8, x + 2, y - 2, body);
        g.fill(x - 1, y - 2, x + 2, y, edge);
        g.fill(x, y - 2, x + 1, y + 1, body);
        String distanceText = distance >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fkm", distance / 1000) : Math.round(distance) + "m";
        g.pose().pushMatrix();
        g.pose().translate(x, y + 3);
        g.pose().scale(0.75F, 0.75F);
        g.text(mc.font, distanceText, -mc.font.width(distanceText) / 2, 0, alpha << 24 | 0xF4F9FF, true);
        if (looking) g.text(mc.font, name, -mc.font.width(name) / 2, 11, alpha << 24 | colour & 0xFFFFFF, true);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ session

    private static long sessionStart, marksEarned, xpEarned;
    private static HudPayload previous;

    /** Counts what the server's figures went up by since joining. */
    static void onHud(HudPayload next) {
        if (previous == null) { previous = next; sessionStart = System.nanoTime(); marksEarned = 0; xpEarned = 0; return; }
        if (next.marks() > previous.marks()) marksEarned += next.marks() - previous.marks();
        if (next.level() == previous.level()) xpEarned += Math.max(0, next.experience() - previous.experience());
        else if (next.level() > previous.level())
            xpEarned += Math.max(0, previous.experienceNeeded() - previous.experience()) + next.experience();
        previous = next;
    }

    static void resetSession() { previous = null; }

    /** One line under the trackers: Marks and Adventurer XP per hour, and how long this session has run. */
    /** Marks and XP per hour this session, as one slim line; a sample in edit mode. False when nothing earned yet. */
    static boolean session(GuiGraphicsExtractor g, Font font, ClientPreferences p, boolean preview) {
        long seconds = Math.max(1, (System.nanoTime() - sessionStart) / 1_000_000_000L);
        boolean real = previous != null && (marksEarned != 0 || xpEarned != 0);
        if (!real && !preview) return false;
        double hours = seconds / 3600D;
        String line = real ? compact(marksEarned / hours) + " Marks/h  ·  " + compact(xpEarned / hours) + " XP/h" : "1.2k Marks/h  ·  840 XP/h";
        String time = real ? clock(seconds) : "0:42:10";
        int width = font.width(line) + font.width(time) + 22;
        AerborneHud.box(g, 0, 0, width, 18, p);
        g.text(font, line, 6, 5, TEXT, false);
        g.text(font, time, width - 6 - font.width(time), 5, MUTED, false);
        return true;
    }

    private static String compact(double value) {
        if (value >= 1_000_000) return String.format(Locale.ROOT, "%.1fm", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.ROOT, "%.1fk", value / 1_000);
        return Long.toString(Math.round(value));
    }

    // ------------------------------------------------------------------ death waypoint

    private static boolean dead;

    private static void deathWaypoint(Minecraft mc) {
        if (mc.player == null) { dead = false; return; }
        boolean now = mc.player.isDeadOrDying();
        if (now && !dead && ClientState.preferences.deathWaypoint) {
            String scope = ClientState.scope();
            if (!scope.isEmpty()) {
                var p = ClientState.preferences;
                p.waypoints.removeIf(w -> w.scope().equals(scope) && w.name().equals("Last death"));
                if (p.waypoints.size() < 32) {
                    var point = new ClientPreferences.Waypoint(UUID.randomUUID().toString(), scope, "Last death",
                            mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
                    p.waypoints.add(point);
                    p.tracked = point.id();
                    ClientState.save();
                }
            }
        }
        dead = now;
    }
}

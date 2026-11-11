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
    static void effects(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, int top) {
        if (!p.effects || mc.player == null) return;
        List<MobEffectInstance> active = new ArrayList<>(mc.player.getActiveEffects());
        if (active.isEmpty()) return;
        active.sort((a, b) -> Integer.compare(b.getDuration(), a.getDuration()));
        int width = 112, x = p.topRight ? 12 : g.guiWidth() - 12 - width, y = top;
        for (MobEffectInstance effect : active) {
            if (!effect.showIcon()) continue;
            boolean harmful = !effect.getEffect().value().isBeneficial();
            g.fill(x, y, x + width, y + 20, 0xC017243B);
            g.fill(x, y, x + 2, y + 20, harmful ? 0xFFE05A5A : GOLD);
            var key = effect.getEffect().unwrapKey();
            if (key.isPresent()) g.blitSprite(RenderPipelines.GUI_TEXTURED, key.get().identifier().withPrefix("mob_effect/"), x + 4, y + 1, 18, 18);
            String name = effect.getEffect().value().getDisplayName().getString()
                    + (effect.getAmplifier() > 0 ? " " + roman(effect.getAmplifier() + 1) : "");
            g.text(mc.font, mc.font.plainSubstrByWidth(name, width - 28), x + 25, y + 2, TEXT, false);
            String time = effect.isInfiniteDuration() ? "∞" : clock(effect.getDuration() / 20);
            boolean ending = !effect.isInfiniteDuration() && effect.getDuration() < 200;
            g.text(mc.font, time, x + 25, y + 11, ending ? 0xFFF6C85F : MUTED, false);
            y += 22;
        }
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
        int x = Math.round(Math.max(16, Math.min(g.guiWidth() - 16, at[0])));
        int y = Math.round(Math.max(16, Math.min(g.guiHeight() - 40, at[1])));
        g.fill(x - 1, y - 9, x + 1, y - 3, GOLD);
        g.fill(x - 4, y - 4, x + 5, y + 5, 0xFF0D1A2B);
        g.fill(x - 3, y - 3, x + 4, y + 4, GOLD);
        String label = tracked.name() + "  " + Math.round(distance) + "m";
        int w = mc.font.width(label);
        g.fill(x - w / 2 - 3, y + 7, x + w / 2 + 3, y + 18, 0xB00D1A2B);
        g.text(mc.font, label, x - w / 2, y + 9, TEXT, false);
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
    static int session(GuiGraphicsExtractor g, Font font, int x, int y, ClientPreferences p) {
        if (!p.session || previous == null) return y;
        long seconds = Math.max(1, (System.nanoTime() - sessionStart) / 1_000_000_000L);
        if (marksEarned == 0 && xpEarned == 0) return y;
        double hours = seconds / 3600D;
        AerborneHud.box(g, x, y, 184, 22, p);
        String line = compact(marksEarned / hours) + " Marks/h  ·  " + compact(xpEarned / hours) + " XP/h";
        g.text(font, line, x + 8, y + 7, TEXT, false);
        String time = clock(seconds);
        g.text(font, time, x + 176 - font.width(time), y + 7, MUTED, false);
        return y + 28;
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

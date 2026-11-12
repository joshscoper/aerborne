package net.aerborne.client.fabric;

import java.util.List;
import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The target frame: a panel beside the player's own frame for the creature they look at, with its name and rank,
 * health, and every status effect on it with time left. It holds the last target a few seconds after the crosshair
 * leaves it, so a fight's target stays up while the player turns or dodges.
 */
public final class AerborneTargetFrame {
    private AerborneTargetFrame() { }

    private static final double RANGE = 32;
    private static final long HOLD_MILLIS = 4000;
    static final int WIDTH = 184;
    private static final int ICON_STEP = 21, MAX_ICONS = 7;
    private static int lastId = -1;
    private static long lastSeen;

    static void clear() { lastId = -1; }

    /** The creature under the crosshair out to {@value #RANGE} blocks, or the one held from a moment ago. */
    static LivingEntity target(Minecraft mc) {
        if (mc.level == null || mc.player == null) return null;
        long now = System.currentTimeMillis();
        LivingEntity looked = look(mc);
        if (looked != null) { lastId = looked.getId(); lastSeen = now; return looked; }
        if (lastId < 0 || now - lastSeen > HOLD_MILLIS) return null;
        Entity held = mc.level.getEntity(lastId);
        if (held instanceof LivingEntity living && shown(mc, living) && living.distanceToSqr(mc.player) <= RANGE * RANGE) return living;
        lastId = -1;
        return null;
    }

    private static boolean shown(Minecraft mc, LivingEntity living) {
        return living != mc.player && living.isAlive() && !living.isInvisible() && !living.isSpectator() && !(living instanceof ArmorStand);
    }

    private static LivingEntity look(Minecraft mc) {
        if (mc.crosshairPickEntity instanceof LivingEntity near && shown(mc, near)) return near;
        Entity camera = mc.getCameraEntity();
        if (camera == null) return null;
        Vec3 eye = camera.getEyePosition(1F), view = camera.getViewVector(1F), end = eye.add(view.scale(RANGE));
        HitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
        double reach = block.getType() == HitResult.Type.MISS ? RANGE * RANGE : block.getLocation().distanceToSqr(eye);
        AABB sweep = camera.getBoundingBox().expandTowards(view.scale(RANGE)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, eye, end, sweep,
                e -> e instanceof LivingEntity l && e.isPickable() && shown(mc, l), reach);
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }

    /** The frame at (0, 0) for the creature in view, or a sample in edit mode; false when there is none. */
    static boolean paint(GuiGraphicsExtractor g, Minecraft mc, ClientPreferences p, boolean preview) {
        LivingEntity target = target(mc);
        long now = System.currentTimeMillis();
        if (target != null) {
            PlatesPayload.Plate plate = AerborneNameplates.plate(target.getId());
            if (plate != null && plate.npc()) {
                npc(g, mc.font, p, plate);
                return true;
            }
            MutableComponent name = plate != null ? AerborneNameplates.name(plate)
                    : Component.literal(target.getDisplayName().getString()).withColor(0xF2F2F2);
            frame(g, mc.font, p, name, plate != null && plate.boss(), plate != null && plate.elite(),
                    target.getHealth(), target.getMaxHealth(), target.getAbsorptionAmount(), AerborneEffects.on(target), now);
            return true;
        }
        if (!preview) return false;
        MutableComponent name = Component.empty().append(Component.literal("[Lv 12] ").withColor(0x9AA3B5))
                .append(Component.literal("Ashen Marauder").withColor(0xF4C76B));
        List<AerborneEffects.Shown> sample = List.of(
                sample("poison", 1, now + 6_000), sample("weakness", 0, now + 25_000), sample("resistance", 2, now + 180_000));
        frame(g, mc.font, p, name, false, true, 31, 48, 0, sample, now);
        return true;
    }

    /** A townsperson: their name and an "NPC" tag, and their title where a creature's health would be. */
    private static void npc(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, ClientPreferences p, PlatesPayload.Plate plate) {
        AerborneHud.box(g, 0, 0, WIDTH, 36, p);
        String plain = plate.name().replaceAll("§[0-9a-fk-orx]", "").replaceAll("§#?[0-9a-fA-F]{6}", "").strip();
        int split = plain.indexOf(" · ");
        String who = split < 0 ? plain : plain.substring(0, split), title = split < 0 ? "" : plain.substring(split + 3);
        String rank = "NPC";
        int rankWidth = font.width(Ui.heading(rank)) + 6;
        var nameLine = font.substrByWidth(Component.literal(who).withColor(0x8FD3FF), WIDTH - 16 - rankWidth);
        g.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(nameLine), 8, 5, 0xFFFFFFFF, true);
        g.text(font, Ui.heading(rank), WIDTH - 8 - font.width(Ui.heading(rank)), 5, 0xFF8FD3FF, true);
        if (!title.isEmpty()) {
            String line = font.plainSubstrByWidth(title, WIDTH - 16);
            g.text(font, line, WIDTH / 2 - font.width(line) / 2, 20, Ui.MUTED, true);
        }
        HudElements.track(0, 0, WIDTH, 36);
    }

    private static AerborneEffects.Shown sample(String id, int amplifier, long ends) {
        var key = net.minecraft.resources.Identifier.withDefaultNamespace(id);
        return new AerborneEffects.Shown(key, net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getValue(key), amplifier, ends);
    }

    /** The frame: name and rank, a health bar with figures, and a row of effect tiles when there are any. */
    private static void frame(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, ClientPreferences p, MutableComponent name,
                              boolean boss, boolean elite, float health, float maxHealth, float absorption,
                              List<AerborneEffects.Shown> effects, long now) {
        int h = effects.isEmpty() ? 36 : 69;
        AerborneHud.box(g, 0, 0, WIDTH, h, p);
        String rank = boss ? "BOSS" : elite ? "ELITE" : "";
        int rankWidth = rank.isEmpty() ? 0 : font.width(Ui.heading(rank)) + 6;
        var nameLine = font.substrByWidth(name, WIDTH - 16 - rankWidth);
        g.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(nameLine), 8, 5, 0xFFFFFFFF, true);
        if (!rank.isEmpty()) g.text(font, Ui.heading(rank), WIDTH - 8 - font.width(Ui.heading(rank)), 5, boss ? 0xFFC9A2FF : Ui.GOLD, true);

        float max = Math.max(1, maxHealth), shown = Math.max(0, Math.min(max, health));
        Ui.bar(g, 8, 17, WIDTH - 16, 13, boss ? Ui.BOSS : Ui.HEALTH, shown / max);
        String label = AerborneHud.number(shown) + " / " + AerborneHud.number(max)
                + (absorption > 0 ? "  +" + AerborneHud.number(absorption) : "") + "   " + Math.round(100 * shown / max) + "%";
        g.text(font, label, WIDTH / 2 - font.width(label) / 2, 20, Ui.TEXT, true);
        if (effects.isEmpty()) return;

        int count = Math.min(MAX_ICONS, effects.size());
        for (int i = 0; i < count; i++) {
            AerborneEffects.Shown s = effects.get(i);
            long seconds = s.secondsLeft(now);
            float fraction = s.fraction(now);
            AerborneExtras.effectTile(g, font, s, 8 + i * ICON_STEP, 35, fraction, seconds, now);
        }
        if (effects.size() > count) g.text(font, "+" + (effects.size() - count), 8 + count * ICON_STEP + 2, 40, Ui.MUTED, true);
    }
}

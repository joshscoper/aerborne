package net.aerborne.client.fabric;

import java.util.ArrayDeque;
import java.util.Deque;
import net.aerborne.client.protocol.ClientPreferences;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * Party frames under the HUD panels, and toasts for level-ups, skill levels, achievements, quests and rare drops,
 * which the server sends in place of its big titles.
 */
final class AerborneSocial {
    private AerborneSocial() { }

    private static final int TEXT = 0xFFF4F9FF, MUTED = 0xFF8E9AAF, GOLD = 0xFFF4C76B;

    // ------------------------------------------------------------------ party frames

    static FeedPayloads.Party party;

    /** One compact frame per party member: name, a health bar, and how far away they are. */
    static final java.util.List<FeedPayloads.Member> SAMPLE_PARTY = java.util.List.of(
            new FeedPayloads.Member("Wren", true, true, 34, 40, 12),
            new FeedPayloads.Member("Corvin", false, true, 11, 38, 40),
            new FeedPayloads.Member("Isolde", false, false, 0, 30, -1));

    /** The party's members, or the sample in edit mode; empty when there is no party. */
    static java.util.List<FeedPayloads.Member> members(boolean preview) {
        FeedPayloads.Party current = party;
        if (current != null && !current.members().isEmpty()) return current.members();
        return preview ? SAMPLE_PARTY : java.util.List.of();
    }

    /** A raid-style stack: one slim row per member with name, distance and a health bar. Returns the height. */
    static int party(GuiGraphicsExtractor g, Font font, int x, int y, ClientPreferences p, java.util.List<FeedPayloads.Member> members) {
        if (members.isEmpty()) return 0;
        int width = 140, height = 6 + members.size() * 20;
        AerborneHud.box(g, x, y, width, height, p);
        int row = y + 4;
        for (FeedPayloads.Member member : members) {
            String name = (member.leader() ? "★ " : "") + font.plainSubstrByWidth(member.name(), 84);
            g.text(font, name, x + 6, row, member.online() ? TEXT : MUTED, false);
            String where = !member.online() ? "offline" : member.distance() < 0 ? "away" : member.distance() + "m";
            g.text(font, where, x + width - 6 - font.width(where), row, MUTED, false);
            float ratio = member.online() && member.maxHealth() > 0 ? Math.min(1, member.health() / member.maxHealth()) : 0;
            Ui.bar(g, x + 6, row + 9, width - 12, 7, Ui.HEALTH, ratio);
            row += 20;
        }
        return height;
    }

    // ------------------------------------------------------------------ toasts

    private record Shown(FeedPayloads.Toast toast, long born) { }
    private static final Deque<Shown> toasts = new ArrayDeque<>();
    private static final long TOAST_NANOS = 4_500_000_000L, SLIDE_NANOS = 250_000_000L;

    static void toast(FeedPayloads.Toast toast) {
        if (toasts.size() >= 3) toasts.removeFirst();
        toasts.addLast(new Shown(toast, System.nanoTime()));
        var sound = switch (toast.kind()) {
            case "level", "achievement" -> SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            case "drop" -> SoundEvents.AMETHYST_BLOCK_CHIME;
            default -> SoundEvents.PLAYER_LEVELUP;
        };
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 0.5F));
    }

    private static int accent(String kind) {
        return switch (kind) {
            case "level" -> 0xFFC59AFA;
            case "skill" -> 0xFF8CE8ED;
            case "achievement" -> GOLD;
            case "quest" -> 0xFF55E58A;
            case "drop" -> 0xFFE0405A;
            default -> TEXT;
        };
    }

    /** Toasts stack down from the top centre, below the boss bars' first row, sliding in and fading out. */
    static final int TOAST_WIDTH = 240, TOAST_HEIGHT = 34, TOAST_CAP = 26;

    /** Toasts stacked down from (0, 0) within {@link #TOAST_WIDTH}, centred; a sample in edit mode. */
    static boolean toasts(GuiGraphicsExtractor g, Minecraft mc, boolean preview) {
        long now = System.nanoTime();
        toasts.removeIf(shown -> now - shown.born() > TOAST_NANOS);
        java.util.Collection<Shown> list = toasts;
        if (list.isEmpty() && !preview) return false;
        if (list.isEmpty()) list = java.util.List.of(
                new Shown(new FeedPayloads.Toast("level", "Level Up!", "You reached Adventurer level 12"), now - SLIDE_NANOS),
                new Shown(new FeedPayloads.Toast("quest", "Quest Complete", "The Ember Accord"), now - SLIDE_NANOS));
        int y = 0;
        for (Shown shown : list) {
            long age = now - shown.born();
            float slide = Math.min(1, age / (float) SLIDE_NANOS);
            float fade = Math.min(1, (TOAST_NANOS - age) / (float) SLIDE_NANOS);
            int alpha = Math.round(255 * Math.min(slide, fade));
            if (alpha < 8) continue;
            String line = shown.toast().line();
            // The banner has a gem at each end (12 wide), so the words centre on the whole banner between them.
            var heading = Ui.heading(mc.font, shown.toast().title(), TOAST_WIDTH - 2 * TOAST_CAP);
            int w = Math.max(190, Math.max(mc.font.width(heading), mc.font.width(line)) + 2 * TOAST_CAP);
            w = Math.min(w, TOAST_WIDTH);
            if (mc.font.width(line) > w - 2 * TOAST_CAP) line = Ui.fit(mc.font, line, w - 2 * TOAST_CAP);
            int x = TOAST_WIDTH / 2 - w / 2, top = y - Math.round((1 - slide) * 12);
            int a = alpha << 24, centre = x + w / 2;
            g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, Ui.toast(shown.toast().kind()), x, top, w, TOAST_HEIGHT,
                    net.minecraft.util.ARGB.white(alpha / 255F));
            g.text(mc.font, heading, centre - mc.font.width(heading) / 2, top + 8, Ui.GOLD & 0xFFFFFF | a, true);
            g.text(mc.font, line, centre - mc.font.width(line) / 2, top + 20, MUTED & 0xFFFFFF | a, false);
            y += TOAST_HEIGHT + 4;
        }
        // The whole stack, so edit mode outlines every banner and a bottom anchor holds them all.
        HudElements.track(0, 0, TOAST_WIDTH, Math.max(TOAST_HEIGHT, y - 4));
        return true;
    }
}

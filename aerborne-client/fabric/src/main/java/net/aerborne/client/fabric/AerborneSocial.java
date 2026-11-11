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
    static int party(GuiGraphicsExtractor g, Font font, int x, int y, ClientPreferences p) {
        FeedPayloads.Party current = party;
        if (!p.partyFrames || current == null || current.members().isEmpty()) return y;
        int height = 8 + current.members().size() * 22;
        AerborneHud.box(g, x, y, 184, height, p);
        g.text(font, "Party", x + 8, y + 4, p.theme.accent, false);
        int row = y + 14;
        for (FeedPayloads.Member member : current.members()) {
            String name = (member.leader() ? "★ " : "") + font.plainSubstrByWidth(member.name(), 110);
            g.text(font, name, x + 8, row, member.online() ? TEXT : MUTED, false);
            String where = !member.online() ? "offline" : member.distance() < 0 ? "away" : member.distance() + "m";
            g.text(font, where, x + 176 - font.width(where), row, MUTED, false);
            float ratio = member.online() && member.maxHealth() > 0 ? Math.min(1, member.health() / member.maxHealth()) : 0;
            g.fill(x + 8, row + 10, x + 176, row + 14, 0xFF283752);
            int colour = ratio > 0.5F ? 0xFF55E58A : ratio > 0.25F ? 0xFFF6C85F : 0xFFFF5D73;
            if (member.online()) g.fill(x + 8, row + 10, x + 8 + Math.round(168 * ratio), row + 14, colour);
            row += 22;
        }
        return y + height + 6;
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
    static void toasts(GuiGraphicsExtractor g, Minecraft mc) {
        long now = System.nanoTime();
        toasts.removeIf(shown -> now - shown.born() > TOAST_NANOS);
        int y = 34;
        for (Shown shown : toasts) {
            long age = now - shown.born();
            float slide = Math.min(1, age / (float) SLIDE_NANOS);
            float fade = Math.min(1, (TOAST_NANOS - age) / (float) SLIDE_NANOS);
            int alpha = Math.round(255 * Math.min(slide, fade));
            if (alpha < 8) continue;
            String title = shown.toast().title(), line = shown.toast().line();
            int w = Math.max(160, Math.max(mc.font.width(title), mc.font.width(line)) + 28);
            int x = g.guiWidth() / 2 - w / 2, top = y - Math.round((1 - slide) * 12);
            int a = alpha << 24;
            g.fill(x, top, x + w, top + 30, (Math.round(alpha * 0.85F) << 24) | 0x0D1A2B);
            int accent = accent(shown.toast().kind()) & 0xFFFFFF | a;
            g.fill(x, top, x + w, top + 1, accent);
            g.fill(x, top + 29, x + w, top + 30, accent);
            g.fill(x, top, x + 3, top + 30, accent);
            g.text(mc.font, title, x + w / 2 - mc.font.width(title) / 2, top + 5, TEXT & 0xFFFFFF | a, true);
            g.text(mc.font, line, x + w / 2 - mc.font.width(line) / 2, top + 17, MUTED & 0xFFFFFF | a, false);
            y += 34;
        }
    }
}

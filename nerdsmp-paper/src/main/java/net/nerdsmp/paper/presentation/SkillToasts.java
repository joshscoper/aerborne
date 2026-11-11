package net.nerdsmp.paper.presentation;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Skill experience shows here, not on the action bar: a bar at the top of the screen that fills with
 * the skill's level, adds up gains that come quickly together, and goes away when they stop.
 */
public final class SkillToasts {
    private static final long SHOWN_TICKS = 70;
    private static final Map<UUID, Toast> SHOWING = new HashMap<>();

    private static final class Toast {
        final BossBar bar;
        String skill;
        long earned;
        BukkitTask hide;

        Toast(BossBar bar, String skill) {
            this.bar = bar;
            this.skill = skill;
        }
    }

    private SkillToasts() { }

    /** {@code progress} is how far through the level the skill is, from 0 to 1; {@code note} is any bonus worth naming. */
    public static void show(Plugin plugin, Player player, String skill, int level, long earned, double progress, String note) {
        // The Aerborne client shows the gain in its own HUD, so the bar at the top stays free for bosses.
        if (ClientHudFeed.modded(player)) {
            Toast current = SHOWING.get(player.getUniqueId());
            long total = earned;
            if (current != null && current.skill.equals(skill)) total += current.earned;
            if (current != null) { player.hideBossBar(current.bar); if (current.hide != null) current.hide.cancel(); }
            Toast tally = new Toast(BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 0F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS), skill);
            tally.earned = total;
            SHOWING.put(player.getUniqueId(), tally);
            tally.hide = Bukkit.getScheduler().runTaskLater(plugin, () -> SHOWING.remove(player.getUniqueId(), tally), SHOWN_TICKS);
            if (ClientHudFeed.skill(player, Chat.render("<aqua>" + Chat.escape(skill) + " " + level + "</aqua> <white>+" + total + " XP</white>"
                + (note == null || note.isEmpty() ? "" : " <dark_gray>•</dark_gray> " + note)), progress)) return;
        }
        Toast toast = SHOWING.computeIfAbsent(player.getUniqueId(), id -> {
            Toast created = new Toast(BossBar.bossBar(net.kyori.adventure.text.Component.empty(), 0F, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS), skill);
            player.showBossBar(created.bar);
            return created;
        });
        if (!toast.skill.equals(skill)) {
            toast.skill = skill;
            toast.earned = 0;
        }
        toast.earned += earned;
        toast.bar.name(Chat.render("<aqua>" + Chat.escape(skill) + " " + level + "</aqua> <white>+" + toast.earned + " XP</white>"
            + (note == null || note.isEmpty() ? "" : " <dark_gray>•</dark_gray> " + note)));
        toast.bar.progress((float) Math.clamp(progress, 0D, 1D));
        if (toast.hide != null) toast.hide.cancel();
        toast.hide = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.hideBossBar(toast.bar);
            SHOWING.remove(player.getUniqueId());
        }, SHOWN_TICKS);
    }
}

package net.nerdsmp.paper.expedition;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Lure;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hidden fishing spots. Vanilla bobbers only float on water, so a line cast into a lure's lava is held on the
 * surface here. After a while something takes it, and whoever reels in on the bite hauls up the lure's secret
 * room, if their Fishing level can hold it. Nothing about these goes on a guide.
 */
final class RunLures {
    private static final int BITE_WINDOW_TICKS = 50, SHORTEST_WAIT = 80, LONGEST_WAIT = 220;
    private final NerdSmpPlugin plugin;
    private final ExpeditionRun run;
    private final Map<UUID, Line> lines = new HashMap<>();

    private static final class Line {
        Lure lure;
        Location rest;
        int tick, biteAt;
        boolean biting;
    }

    RunLures(NerdSmpPlugin plugin, ExpeditionRun run) {
        this.plugin = plugin;
        this.run = run;
    }

    void fished(PlayerFishEvent event) {
        FishHook hook = event.getHook();
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            watch(event.getPlayer(), hook);
            return;
        }
        Line line = lines.remove(hook.getUniqueId());
        if (line != null && line.biting) landed(event.getPlayer(), line.lure);
    }

    private void watch(Player player, FishHook hook) {
        if (run.expedition.story().lures().isEmpty()) return;
        UUID id = hook.getUniqueId();
        Line line = new Line();
        lines.put(id, line);
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!hook.isValid() || run.state != ExpeditionRun.State.ACTIVE || !lines.containsKey(id)) {
                lines.remove(id);
                task.cancel();
                return;
            }
            if (line.lure == null) settle(line, hook);
            else hold(line, hook, player);
        }, 1L, 1L);
    }

    /** The bobber has come down in a lure's lava: it floats here from now on. */
    private void settle(Line line, FishHook hook) {
        Location at = hook.getLocation();
        if (at.getBlock().getType() != Material.LAVA) return;
        for (Lure lure : run.expedition.story().lures().values()) {
            if (Math.hypot(at.getX() - lure.at().x() - .5, at.getZ() - lure.at().z() - .5) > lure.radius() || Math.abs(at.getY() - lure.at().y()) > 3) continue;
            line.lure = lure;
            line.rest = new Location(at.getWorld(), at.getX(), lure.at().y() + .9, at.getZ());
            line.biteAt = ThreadLocalRandom.current().nextInt(SHORTEST_WAIT, LONGEST_WAIT);
            hook.setGravity(false);
            return;
        }
    }

    private void hold(Line line, FishHook hook, Player player) {
        hook.setVelocity(new Vector());
        hook.teleport(line.rest);
        hook.setFireTicks(0);
        line.tick++;
        if (line.tick % 15 == 0) line.rest.getWorld().spawnParticle(Particle.LAVA, line.rest, 2, .2, .1, .2, 0);
        if (!line.biting && line.tick >= line.biteAt) {
            line.biting = true;
            line.rest.getWorld().spawnParticle(Particle.FLAME, line.rest, 40, .4, .3, .4, .06);
            line.rest.getWorld().playSound(line.rest, Sound.BLOCK_LAVA_EXTINGUISH, 1.4F, .6F);
            player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1F, .5F);
            Chat.bar(player, "<gold><bold>Something takes the line!</bold></gold>");
        } else if (line.biting && line.tick >= line.biteAt + BITE_WINDOW_TICKS) {
            // It let go. It'll come back.
            line.biting = false;
            line.biteAt = line.tick + ThreadLocalRandom.current().nextInt(SHORTEST_WAIT, LONGEST_WAIT);
        }
    }

    private void landed(Player player, Lure lure) {
        int level = plugin.presentation().progression(player).skills()
            .getOrDefault(Skill.FISHING, new ProgressionService.SkillProgress(0L, 0)).level();
        if (level < lure.fishingLevel()) {
            player.setFireTicks(40);
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_BURN, 1F, .7F);
            Chat.send(player, "<red>The line goes rigid and burns through.</red> <gray>Whatever that was, it's far too strong for you. <dark_gray>(Fishing " + lure.fishingLevel() + ")</dark_gray></gray>");
            return;
        }
        if (!run.surfaces(lure.room())) {
            Chat.notice(player, "The line goes slack. Nothing down there wants to come up right now.");
            return;
        }
        run.broadcast("<gold>" + Chat.escape(player.getName()) + "'s line snaps taut.</gold> <gray>The whole sea of fire heaves. Something enormous is coming up with it.</gray>");
    }

    /** Once a second: the slag at each spot boils, for anyone paying attention. */
    void tick() {
        for (Lure lure : run.expedition.story().lures().values()) {
            Location at = new Location(run.world, lure.at().x() + .5, lure.at().y() + 1, lure.at().z() + .5);
            if (at.getChunk().isLoaded()) run.world.spawnParticle(Particle.LAVA, at, 3, lure.radius() * .4, 0, lure.radius() * .4, 0);
        }
    }

    /** A lure's boss has a body of its own. */
    void dress(Room room, LivingEntity boss) {
        for (Lure lure : run.expedition.story().lures().values())
            if (room.id().equals(lure.room()) && lure.body() != null) MoltenBody.dress(plugin, boss);
    }
}

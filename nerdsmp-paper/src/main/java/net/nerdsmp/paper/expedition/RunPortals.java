package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Point;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Portal;
import net.nerdsmp.paper.expedition.flow.RunProgress;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tears in the map. Nothing is built: a portal is drawn in particles once the rooms it {@code requires} are cleared, and
 * anyone who walks into it goes to its far side. It stays open until its room is won, so a wiped party can come back.
 */
final class RunPortals {
    private static final long FRAME_TICKS = 2L;
    private static final double TALL = 4.2;
    private static final Particle[] STREAMS = {Particle.FLAME, Particle.SNOWFLAKE, Particle.COMPOSTER};

    private final Plugin plugin;
    private final ExpeditionRun run;
    private final Map<String, Long> open = new HashMap<>();

    RunPortals(Plugin plugin, ExpeditionRun run) {
        this.plugin = plugin;
        this.run = run;
    }

    /** Once a second: opens whatever has come due. */
    void tick() {
        for (Portal portal : run.expedition.story().portals().values()) {
            if (open.containsKey(portal.id()) || !portal.requires().stream().allMatch(run::roomCleared)) continue;
            open.put(portal.id(), 0L);
            run.broadcast("<dark_purple><bold>" + Chat.escape(portal.title()) + "</bold></dark_purple>");
            run.world.playSound(run.location(portal.at()), Sound.BLOCK_END_PORTAL_SPAWN, 4F, .6F);
            run.opened(portal);
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                if (!run.isLive() || run.roomCleared(portal.room())) {
                    task.cancel();
                    return;
                }
                draw(portal);
                take(portal);
            }, 1L, FRAME_TICKS);
        }
    }

    /** A disc of ink turning on its edge, three streams of the seals running into it, and a rim that won't sit still. */
    private void draw(Portal portal) {
        long frame = open.merge(portal.id(), 1L, Long::sum);
        Location at = run.location(portal.at()).add(0, TALL / 2, 0);
        for (int arm = 0; arm < 5; arm++) {
            double turn = frame * .13 + arm * Math.TAU / 5;
            for (double reach = .4; reach < 1; reach += .15) {
                double angle = turn + reach * 3, x = Math.cos(angle) * reach * TALL / 1.4, y = Math.sin(angle) * reach * TALL / 1.4;
                run.world.spawnParticle(Particle.SQUID_INK, at.clone().add(x, y, 0), 1, 0, 0, 0, 0);
            }
        }
        for (int spot = 0; spot < 16; spot++) {
            double angle = spot * Math.TAU / 16 - frame * .08;
            double wobble = 1 + Math.sin(frame * .2 + spot * 2) * .06;
            Location rim = at.clone().add(Math.cos(angle) * TALL / 1.4 * wobble, Math.sin(angle) * TALL / 1.4 * wobble, 0);
            run.world.spawnParticle(Particle.WITCH, rim, 1, 0, 0, 0, 0);
        }
        run.world.spawnParticle(Particle.REVERSE_PORTAL, at, 6, 1.2, 1.2, .3, .02);
        run.world.spawnParticle(Particle.PORTAL, at, 8, .8, .8, .4, .6);
        List<Point> feeds = portal.feeds();
        for (int index = 0; index < feeds.size(); index++) {
            Location from = run.location(feeds.get(index)).add(0, .6, 0);
            double along = (frame % 20) / 20D;
            Location point = from.clone().add(at.clone().subtract(from).toVector().multiply(along));
            run.world.spawnParticle(STREAMS[index % STREAMS.length], point, 3, .1, .1, .1, 0);
        }
        if (frame % 30 == 0) run.world.playSound(at, Sound.BLOCK_PORTAL_AMBIENT, 2F, .6F);
    }

    private void take(Portal portal) {
        Location at = run.location(portal.at());
        for (Player player : run.online()) {
            Location here = player.getLocation();
            if (Math.abs(here.getY() - at.getY()) > TALL || Math.hypot(here.getX() - at.getX(), here.getZ() - at.getZ()) > portal.radius()) continue;
            player.playSound(here, Sound.BLOCK_PORTAL_TRAVEL, .5F, 1.4F);
            run.send(player, run.location(portal.to()));
        }
    }
}

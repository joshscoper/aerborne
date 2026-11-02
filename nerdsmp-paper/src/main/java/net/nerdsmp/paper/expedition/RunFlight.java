package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Room;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

/**
 * A boss on the wing. It circles out of reach over the room, untouchable, until the party has put enough harpoons in it, and
 * then it's dragged down where they can get at it. A {@code flight} script line sends it up; a {@code landed} script runs
 * when it comes down. The ballistas are any dispensers in the room, each good for one shot per flight.
 */
final class RunFlight {
    private static final long FRAME_TICKS = 1L;
    private static final int LANDING_TICKS = 40;

    private final Plugin plugin;
    private final ExpeditionRun run;
    private final Set<Block> fired = new HashSet<>();
    private LivingEntity boss;
    private Location center, ground;
    private double acrossReach, alongReach, angle;
    private int height, hits, need, landing = -1;
    private boolean airborne;
    private Location from;

    RunFlight(Plugin plugin, ExpeditionRun run) {
        this.plugin = plugin;
        this.run = run;
    }

    /** Up it goes, and it stays up until it's harpooned down: circling {@code across} by {@code along}, {@code height} above the room's middle. */
    void takeOff(LivingEntity flyer, Room room, int across, int along, int height) {
        if (airborne && boss != null && boss.isValid() || flyer == null) return;
        boss = flyer;
        ground = run.location(room.points().getFirst());
        center = room.points().size() > 1 ? run.location(room.points().get(1)) : ground;
        acrossReach = across;
        alongReach = along;
        this.height = height;
        airborne = true;
        hits = 0;
        landing = -1;
        fired.clear();
        need = Math.clamp(1 + (run.online().size() + 1) / 3, 2, 4);
        boss.setInvulnerable(true);
        boss.setGravity(false);
        if (boss instanceof Mob mob) {
            mob.getPathfinder().stopPathfinding();
            mob.setAI(false);
        }
        run.broadcast("<gold>It's taken to the air!</gold> <gray>Get to the ballistas: " + need + " harpoons will bring it down.</gray>");
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!run.isLive() || !boss.isValid() || !airborne) {
                task.cancel();
                return;
            }
            step();
        }, 1L, FRAME_TICKS);
    }

    private void step() {
        if (landing >= 0) {
            double t = landing++ / (double) LANDING_TICKS, eased = t * t * (3 - 2 * t);
            Location at = from.clone().add(ground.toVector().subtract(from.toVector()).multiply(eased));
            at.setYaw(from.getYaw());
            boss.teleport(at);
            if (landing % 4 == 0) run.world.spawnParticle(Particle.LARGE_SMOKE, at, 10, 3, 1, 3, .02);
            if (landing > LANDING_TICKS) touchdown();
            return;
        }
        angle += .012;
        double x = center.getX() + Math.cos(angle) * acrossReach, z = center.getZ() + Math.sin(angle) * alongReach;
        double nextX = center.getX() + Math.cos(angle + .02) * acrossReach, nextZ = center.getZ() + Math.sin(angle + .02) * alongReach;
        Location at = new Location(run.world, x, center.getY() + height + Math.sin(angle * 3) * 3, z);
        at.setYaw((float) Math.toDegrees(Math.atan2(-(nextX - x), nextZ - z)));
        boss.teleport(at);
    }

    private void touchdown() {
        airborne = false;
        boss.setInvulnerable(false);
        boss.setGravity(true);
        if (boss instanceof Mob mob) mob.setAI(true);
        Location at = boss.getLocation();
        run.world.spawnParticle(Particle.EXPLOSION_EMITTER, at, 2, 2, .4, 2, 0);
        run.world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2F, .6F);
        run.world.playSound(at, Sound.ENTITY_ENDER_DRAGON_HURT, 2F, .6F);
        run.broadcast("<yellow>It's down!</yellow>");
        run.scriptHere("landed");
    }

    /** True if that was a ballista. */
    boolean interact(Player player, Block block) {
        if (block.getType() != Material.DISPENSER) return false;
        if (!airborne || landing >= 0) {
            Chat.bar(player, "<gray>Nothing to aim at.</gray>");
            return true;
        }
        if (!fired.add(block)) {
            Chat.bar(player, "<red>That one's spent.</red>");
            return true;
        }
        Location bow = block.getLocation().add(.5, 2, .5), at = boss.getLocation().add(0, 2, 0);
        Vector line = at.toVector().subtract(bow.toVector());
        for (double along = 0; along <= 1; along += .02) run.world.spawnParticle(Particle.CRIT, bow.clone().add(line.clone().multiply(along)), 2, .05, .05, .05, 0);
        run.world.playSound(bow, Sound.ITEM_CROSSBOW_SHOOT, 2F, .5F);
        run.world.playSound(at, Sound.ENTITY_ENDER_DRAGON_HURT, 2F, .7F);
        hits++;
        run.broadcast("<gold>" + Chat.escape(player.getName()) + "</gold> <gray>puts a harpoon in it: " + hits + "/" + need + "</gray>");
        if (hits >= need) {
            landing = 0;
            from = boss.getLocation();
            run.broadcast("<gold>The chains go taut!</gold> <gray>It's coming down!</gray>");
        }
        return true;
    }
}

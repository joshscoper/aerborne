package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The milestone effects ({@link IslandMilestone#auras}) a co-op member has while working on their own island. They
 * are endless while the player stays, so the HUD shows them without a ticking timer, and they come off as soon as
 * the player leaves, changes game mode or turns them off. An effect from a potion or beacon that is stronger is
 * left alone. Each effect we give is ambient with no particles, which is how a stale one is recognized after a
 * crash and taken off at join.
 */
public final class IslandPerks implements Listener, AutoCloseable {
    static final String SETTING = "settings.island-perk-effects";

    private final NerdSmpPlugin plugin;
    private final SkyblockController islands;
    private final Map<UUID, List<IslandMilestone.Aura>> given = new HashMap<>();
    private final Map<UUID, Boolean> enabled = new HashMap<>();
    private final BukkitTask task;

    IslandPerks(NerdSmpPlugin plugin, SkyblockController islands) {
        this.plugin = plugin;
        this.islands = islands;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::pass, 40L, 40L);
    }

    /** Whether this player wants the island's effects; on unless they turned them off. */
    public boolean enabled(Player player) {
        return enabled.computeIfAbsent(player.getUniqueId(), id -> {
            AccountId account = plugin.sessions().account(id).orElse(null);
            return account == null || plugin.playerData().read(account, yaml -> yaml.getBoolean(SETTING, true));
        });
    }

    public void toggle(Player player) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElseThrow(() -> new IllegalArgumentException("Your account is still loading."));
        boolean next = !enabled(player);
        plugin.playerData().update(account, yaml -> yaml.set(SETTING, next ? null : false));
        enabled.put(player.getUniqueId(), next);
        tick(player);
    }

    private void pass() {
        for (Player player : islands.world().getPlayers()) tick(player);
        // Players who left the island world since the last pass.
        for (UUID id : List.copyOf(given.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) given.remove(id);
            else if (!islands.isIslandWorld(player.getWorld())) apply(player, List.of());
        }
    }

    private void tick(Player player) {
        AccountId owner = islands.activityIsland(player);
        List<IslandMilestone.Aura> wanted = owner == null || !enabled(player) ? List.of()
            : IslandMilestone.auras(islands.development().progress(owner).level());
        apply(player, wanted);
    }

    private void apply(Player player, List<IslandMilestone.Aura> wanted) {
        List<IslandMilestone.Aura> before = given.getOrDefault(player.getUniqueId(), List.of());
        for (IslandMilestone.Aura old : before)
            if (wanted.stream().noneMatch(aura -> aura.equals(old))) strip(player, old.type(), old.amplifier());
        for (IslandMilestone.Aura aura : wanted) {
            PotionEffect current = player.getPotionEffect(aura.type());
            if (current != null && ours(current, aura.amplifier())) continue;
            // A potion, beacon or food effect: keep it unless ours is stronger and it is about to run out anyway.
            if (current != null && (current.getAmplifier() >= aura.amplifier() || current.getDuration() > 100 || current.isInfinite())) continue;
            player.addPotionEffect(new PotionEffect(aura.type(), PotionEffect.INFINITE_DURATION, aura.amplifier(), true, false, true));
        }
        // Remember everything wanted, given or not: the game can hold ours under a stronger potion and bring it back.
        if (wanted.isEmpty()) given.remove(player.getUniqueId()); else given.put(player.getUniqueId(), List.copyOf(wanted));
    }

    private static boolean ours(PotionEffect effect, int amplifier) {
        return effect.isInfinite() && effect.isAmbient() && !effect.hasParticles() && effect.getAmplifier() == amplifier;
    }

    private static void strip(Player player, PotionEffectType type, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && ours(current, amplifier)) player.removePotionEffect(type);
    }

    /** After a crash a player can log in still carrying an island effect; take it off and let the next pass decide. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        for (PotionEffect effect : List.copyOf(player.getActivePotionEffects()))
            if (effect.isInfinite() && effect.isAmbient() && !effect.hasParticles() && isAuraType(effect.getType()))
                player.removePotionEffect(effect.getType());
    }

    private static boolean isAuraType(PotionEffectType type) {
        return type.equals(PotionEffectType.SPEED) || type.equals(PotionEffectType.HASTE) || type.equals(PotionEffectType.REGENERATION);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        apply(player, List.of());
        given.remove(player.getUniqueId());
        enabled.remove(player.getUniqueId());
    }

    @Override
    public void close() {
        task.cancel();
        for (UUID id : List.copyOf(given.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) apply(player, List.of());
        }
        given.clear();
    }
}

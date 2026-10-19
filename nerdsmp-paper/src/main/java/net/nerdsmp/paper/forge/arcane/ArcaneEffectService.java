package net.nerdsmp.paper.forge.arcane;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.anticheat.ArcaneBreakScope;
import net.nerdsmp.paper.anticheat.ArcaneEffectScope;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.gathering.HoeHarvestBreakEvent;
import net.nerdsmp.paper.gathering.TreeHarvestBridge;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Runtime for the effect enchantments and for corruption wear. Each enchantment effect has its own
 * switch under {@code arcane-forge.effects} and is off by default.
 *
 * <ul>
 *   <li><b>Bloodletter</b> observes only final, uncancelled, full-strength melee hits and never
 *       changes that event. Each later bleed tick is a new damage event attributed to the player,
 *       so PvP, party, claim and NPC protections apply to it, and it runs inside a one-use
 *       {@link ArcaneEffectScope} so it is not mistaken for a swing and cannot start another bleed.</li>
 *   <li><b>Veinseeker</b> and <b>Chain Harvest</b> break connected blocks through
 *       {@code Player.breakBlock} inside a one-break {@link ArcaneBreakScope}: every protection,
 *       provenance claim, skill award, drop rule and durability cost runs exactly as for a normal
 *       break, and any cancellation stops the chain. Work is bounded per tick, per job and in total.</li>
 *   <li><b>Corruption</b> multiplies the durability an item loses, after Preservation and other
 *       saves have had their chance.</li>
 * </ul>
 */
public final class ArcaneEffectService implements Listener, AutoCloseable {
    private static final double FULL_STRENGTH = 0.9D;
    private static final int[][] NEIGHBOURS = neighbours();

    private final NerdSmpPlugin plugin;
    private final ArcaneForge forge;
    private final NamespacedKey npcKey;
    private final Map<UUID, Double> attackStrength = new HashMap<>();
    private final Map<UUID, Bleed> bleeding = new LinkedHashMap<>();
    private final LinkedHashMap<UUID, Chain> chains = new LinkedHashMap<>();
    private BukkitTask bleedTask;
    private BukkitTask chainTask;
    private boolean closed;

    public ArcaneEffectService(NerdSmpPlugin plugin, ArcaneForge forge) {
        this.plugin = plugin;
        this.forge = forge;
        npcKey = new NamespacedKey(plugin, "npc_instance");
    }

    public void start() {
        bleedTask = Bukkit.getScheduler().runTaskTimer(plugin, this::bleedPulse, 20L, 20L);
        chainTask = Bukkit.getScheduler().runTaskTimer(plugin, this::chainPulse, 1L, 1L);
    }

    // ---------------------------------------------------------------------------------------------
    // Bloodletter
    // ---------------------------------------------------------------------------------------------

    private record Bleed(UUID attacker, double perTick, int ticksLeft) {
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack() || !forge.settings().effects().bloodletter()) return;
        UUID player = event.getPlayer().getUniqueId();
        attackStrength.put(player, (double) event.getPlayer().getAttackCooldown());
        Bukkit.getScheduler().runTask(plugin, () -> attackStrength.remove(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (ArcaneEffectScope.inside()) return; // a bleed tick or an arc never starts a bleed
        if (!(event.getDamager() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || !(event.getEntity() instanceof LivingEntity target) || target instanceof ArmorStand || event.getFinalDamage() <= 0.0D) return;
        ArcaneSettings settings = forge.settings();
        if (!settings.enabled() || !settings.effects().bloodletter() || !settings.anticheatBridge() || !fighting(player)) return;
        Double strength = attackStrength.remove(player.getUniqueId());
        if (strength == null || strength < FULL_STRENGTH || !eligible(player, target, settings.effects().bleedPvp())) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        int level = plugin.items().enchantmentLevel(weapon, ArcaneEnchantment.BLOODLETTER);
        if (level <= 0) return;
        double percent = plugin.items().gear(weapon).map(gear -> gear.stat("bleed-percent")).orElse(0.0D);
        int ticks = settings.effects().bleedSeconds();
        double perTick = event.getFinalDamage() * Math.min(100.0D, percent) / 100.0D / ticks;
        if (!(perTick > 0.01D)) return;
        Bleed current = bleeding.get(target.getUniqueId());
        // A fresh bleed replaces a weaker remaining one; bleeds never stack.
        if (current != null && current.perTick() * current.ticksLeft() >= perTick * ticks) return;
        if (current == null && bleeding.size() >= settings.effects().maximumBleeds()) return;
        bleeding.put(target.getUniqueId(), new Bleed(player.getUniqueId(), perTick, ticks));
    }

    private void bleedPulse() {
        if (closed || bleeding.isEmpty()) return;
        boolean active = forge.settings().enabled() && forge.settings().effects().bloodletter();
        Iterator<Map.Entry<UUID, Bleed>> iterator = bleeding.entrySet().iterator();
        List<Runnable> strikes = new ArrayList<>();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Bleed bleed = entry.getValue();
            Player attacker = Bukkit.getPlayer(bleed.attacker());
            if (!active || !(Bukkit.getEntity(entry.getKey()) instanceof LivingEntity target) || !target.isValid() || target.isDead()
                || attacker == null || !fighting(attacker) || !attacker.getWorld().equals(target.getWorld())
                || attacker.getLocation().distanceSquared(target.getLocation()) > 32 * 32) {
                iterator.remove();
                continue;
            }
            if (bleed.ticksLeft() <= 1) iterator.remove();
            else entry.setValue(new Bleed(bleed.attacker(), bleed.perTick(), bleed.ticksLeft() - 1));
            strikes.add(() -> {
                DamageSource source = DamageSource.builder(DamageType.MAGIC).withCausingEntity(attacker).withDirectEntity(attacker).build();
                ArcaneEffectScope.run(attacker.getUniqueId(), "bloodletter", target.getUniqueId(), bleed.perTick(), () -> {
                    target.damage(bleed.perTick(), source);
                    return true;
                });
                target.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, target.getLocation().add(0, target.getHeight() * 0.6, 0),
                    3, 0.2, 0.2, 0.2, 0.0);
            });
        }
        // Damage after iterating: a death or another plugin's reaction can never disturb the map mid-walk.
        for (Runnable strike : strikes) {
            try {
                strike.run();
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "A Bloodletter tick failed", failure);
            }
        }
    }

    private boolean eligible(Player player, LivingEntity living, boolean pvp) {
        if (living.equals(player) || living.isInvulnerable()) return false;
        if (living instanceof Player other && (!pvp || other.getGameMode() == GameMode.CREATIVE || other.getGameMode() == GameMode.SPECTATOR)) return false;
        if (living instanceof Tameable tame && tame.isTamed() && player.getUniqueId().equals(tame.getOwnerUniqueId())) return false;
        return !living.getPersistentDataContainer().has(npcKey);
    }

    // ---------------------------------------------------------------------------------------------
    // Veinseeker and Chain Harvest
    // ---------------------------------------------------------------------------------------------

    private static final class Chain {
        final Player player;
        final ItemStack tool;
        final Block root;
        final List<BlockState> blocks;
        int cursor;
        boolean started;

        Chain(Player player, ItemStack tool, Block root, List<BlockState> blocks) {
            this.player = player;
            this.tool = tool;
            this.root = root;
            this.blocks = blocks;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        // Automated breaks (ours or tree felling) and right-click crop harvests never start a chain.
        if (closed || ArcaneBreakScope.inside() || TreeHarvestBridge.automated(event) || event instanceof HoeHarvestBreakEvent
            || !event.isDropItems()) return;
        ArcaneSettings settings = forge.settings();
        if (!settings.enabled() || !settings.anticheatBridge()) return;
        Player player = event.getPlayer();
        if (!fighting(player) || player.getGameMode() != GameMode.SURVIVAL || player.isSneaking() || chains.containsKey(player.getUniqueId())
            || chains.size() >= settings.effects().maximumJobs()) return;
        Block root = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        ArcaneEnchantment enchantment;
        if (ArcaneSignatureService.ore(root.getType())) enchantment = ArcaneEnchantment.VEINSEEKER;
        else if (ripe(root)) enchantment = ArcaneEnchantment.CHAIN_HARVEST;
        else return;
        if (!settings.effects().enabled(enchantment) || plugin.items().enchantmentLevel(tool, enchantment) <= 0) return;
        String stat = enchantment == ArcaneEnchantment.VEINSEEKER ? "vein-size" : "chain-harvest";
        int size = (int) Math.min(settings.effects().maximumChain(),
            Math.floor(plugin.items().gear(tool).map(gear -> gear.stat(stat)).orElse(0.0D)));
        if (size <= 0) return;
        List<BlockState> planned = plan(root, size);
        if (planned.isEmpty()) return;
        Chain chain = new Chain(player, normalize(tool), root, planned);
        chains.put(player.getUniqueId(), chain);
        Material rootType = root.getType();
        // Start next tick, and only if the original break really happened.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || root.getType() == rootType) {
                chains.remove(player.getUniqueId(), chain);
                return;
            }
            chain.started = true;
        });
    }

    /** Connected blocks of the root's exact type (ripe, for crops), nearest first, within loaded chunks. */
    private List<BlockState> plan(Block root, int size) {
        List<BlockState> found = new ArrayList<>();
        java.util.ArrayDeque<Block> frontier = new java.util.ArrayDeque<>();
        java.util.Set<Block> seen = new java.util.HashSet<>();
        frontier.add(root);
        seen.add(root);
        Material type = root.getType();
        boolean crop = !ArcaneSignatureService.ore(type);
        while (!frontier.isEmpty() && found.size() < size && seen.size() < 512) {
            Block current = frontier.poll();
            for (int[] offset : NEIGHBOURS) {
                Block next = current.getRelative(offset[0], offset[1], offset[2]);
                if (!seen.add(next) || !next.getWorld().isChunkLoaded(next.getX() >> 4, next.getZ() >> 4)) continue;
                if (next.getType() != type || crop && !ripe(next)) continue;
                found.add(next.getState());
                frontier.add(next);
                if (found.size() >= size) break;
            }
        }
        return found;
    }

    private void chainPulse() {
        if (closed || chains.isEmpty()) return;
        int budget = forge.settings().effects().blocksPerTick();
        boolean worked = true;
        while (budget > 0 && worked) {
            worked = false;
            for (Chain chain : List.copyOf(chains.values())) {
                if (budget <= 0) break;
                if (!chain.started) continue;
                if (chain.cursor >= chain.blocks.size() || !valid(chain)) {
                    chains.remove(chain.player.getUniqueId(), chain);
                    continue;
                }
                BlockState expected = chain.blocks.get(chain.cursor++);
                Block target = expected.getBlock();
                if (!target.getWorld().isChunkLoaded(target.getX() >> 4, target.getZ() >> 4)
                    || !target.getBlockData().getAsString().equals(expected.getBlockData().getAsString())) continue;
                budget--;
                worked = true;
                try {
                    boolean broken = ArcaneBreakScope.breaking(chain.player, target, () -> chain.player.breakBlock(target));
                    // A cancelled or refused break ends the chain: protection always wins.
                    if (!broken || target.getType() == expected.getType()) chains.remove(chain.player.getUniqueId(), chain);
                } catch (RuntimeException failure) {
                    plugin.getLogger().log(Level.WARNING, "An Arcane chain stopped at " + target.getLocation(), failure);
                    chains.remove(chain.player.getUniqueId(), chain);
                }
            }
        }
    }

    private boolean valid(Chain chain) {
        Player player = chain.player;
        return fighting(player) && player.getGameMode() == GameMode.SURVIVAL && !player.isSneaking()
            && Bukkit.getPlayer(player.getUniqueId()) == player && player.getWorld().equals(chain.root.getWorld())
            && player.getLocation().distanceSquared(chain.root.getLocation()) <= 12 * 12
            && chain.tool.equals(normalize(player.getInventory().getItemInMainHand()));
    }

    private static boolean ripe(Block block) {
        return block.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge()
            && !block.getType().name().contains("STEM") && block.getType() != Material.SUGAR_CANE && block.getType() != Material.CACTUS
            && block.getType() != Material.BAMBOO && block.getType() != Material.KELP && block.getType() != Material.FIRE;
    }

    private static ItemStack normalize(ItemStack item) {
        ItemStack copy = item.clone();
        if (copy.getItemMeta() instanceof Damageable damage) {
            damage.setDamage(0);
            copy.setItemMeta(damage);
        }
        return copy;
    }

    private static int[][] neighbours() {
        List<int[]> offsets = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            if (x != 0 || y != 0 || z != 0) offsets.add(new int[]{x, y, z});
        }
        offsets.sort(java.util.Comparator.comparingInt(offset -> Math.abs(offset[0]) + Math.abs(offset[1]) + Math.abs(offset[2])));
        return offsets.toArray(int[][]::new);
    }

    // ---------------------------------------------------------------------------------------------
    // Corruption wear
    // ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWear(PlayerItemDamageEvent event) {
        if (!ArcaneItemEffects.corruptionActive() || plugin.items().arcaneCorruption(event.getItem()).isEmpty()) return;
        event.setDamage(event.getDamage() * ArcaneItemEffects.durabilityMultiplier());
    }

    // ---------------------------------------------------------------------------------------------

    private boolean fighting(Player player) {
        return player.isOnline() && !player.isDead()
            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && forge.account(player).isPresent();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        attackStrength.remove(id);
        chains.remove(id);
        bleeding.values().removeIf(bleed -> bleed.attacker().equals(id));
    }

    public String stateSummary() {
        return "bleeding " + bleeding.size() + ", chains " + chains.size() + ", trusted breaks " + ArcaneBreakScope.granted();
    }

    @Override
    public void close() {
        closed = true;
        if (bleedTask != null) bleedTask.cancel();
        if (chainTask != null) chainTask.cancel();
        bleeding.clear();
        chains.clear();
    }
}

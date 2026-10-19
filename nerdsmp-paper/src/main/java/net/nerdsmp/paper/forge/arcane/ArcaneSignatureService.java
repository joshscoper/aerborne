package net.nerdsmp.paper.forge.arcane;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.anticheat.ArcaneEffectScope;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Runtime for the three Arcane Signatures. Each is off unless both {@code signatures.enabled} and its
 * own switch are on. None of them is a raw event side effect:
 *
 * <ul>
 *   <li><b>Stormwake</b> observes only final, uncancelled, full-strength melee hits (MONITOR, never
 *       mutating that event). Its arcs are new damage events attributed to the player, so PvP,
 *       party, claim and NPC protections still apply to each one. Each arc runs inside a one-use
 *       {@link ArcaneEffectScope} so weapon stats, life steal, coatings and Watchdog's reach checks
 *       do not mistake it for a swing, and so it can never trigger itself.</li>
 *   <li><b>Heart of the Mountain</b> is fed by the skill pipeline only after the provenance ledger
 *       accepted a natural ore, and counts it only next tick once the break is final. Its reward is
 *       the block's own vanilla drops, computed before removal, delivered once.</li>
 *   <li><b>Last Bastion</b> uses the server's own death-protection step, which runs after every
 *       damage calculation. A real Totem of Undying always goes first; damage that ignores
 *       invulnerability (void, /kill) never reaches it.</li>
 * </ul>
 */
public final class ArcaneSignatureService implements Listener {
    private static final double FULL_STRENGTH = 0.9D;
    private static final int MAXIMUM_CANDIDATES = 24;

    private final NerdSmpPlugin plugin;
    private final ArcaneForge forge;
    private final NamespacedKey npcKey;
    private final Map<UUID, SignatureRules.Counter> charges = new HashMap<>();
    private final Map<UUID, SignatureRules.Counter> resonance = new HashMap<>();
    private final Map<UUID, Long> bastionReady = new HashMap<>();
    private final Map<UUID, Double> attackStrength = new HashMap<>();

    public ArcaneSignatureService(NerdSmpPlugin plugin, ArcaneForge forge) {
        this.plugin = plugin;
        this.forge = forge;
        npcKey = new NamespacedKey(plugin, "npc_instance");
    }

    // ---------------------------------------------------------------------------------------------
    // Stormwake
    // ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack() || !forge.settings().signatures().active(ArcaneSignature.STORMWAKE)) return;
        UUID player = event.getPlayer().getUniqueId();
        attackStrength.put(player, (double) event.getPlayer().getAttackCooldown());
        Bukkit.getScheduler().runTask(plugin, () -> attackStrength.remove(player));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (ArcaneEffectScope.inside()) return; // an arc never charges or releases another arc
        if (!(event.getDamager() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK
            || !(event.getEntity() instanceof LivingEntity target) || target instanceof ArmorStand
            || event.getFinalDamage() <= 0.0D) return;
        ArcaneSettings settings = forge.settings();
        // Arcs need the trusted-effect bridge; without it weapon stats and Watchdog would misread them.
        if (!settings.signatures().active(ArcaneSignature.STORMWAKE) || !settings.anticheatBridge() || !fighting(player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (plugin.items().arcaneSignature(weapon).orElse(null) != ArcaneSignature.STORMWAKE) return;
        Double strength = attackStrength.remove(player.getUniqueId());
        if (strength == null || strength < FULL_STRENGTH) return;
        ArcaneSettings.SignatureSettings numbers = settings.signatures().of(ArcaneSignature.STORMWAKE);
        long cooldown = forge.signatureCooldownMillis(player, weapon, ArcaneSignature.STORMWAKE, numbers.cooldownSeconds());
        SignatureRules.Step step = SignatureRules.gain(charges.getOrDefault(player.getUniqueId(), SignatureRules.Counter.EMPTY),
            System.currentTimeMillis(), Bukkit.getCurrentTick(), numbers.threshold(), numbers.chargeDecaySeconds() * 1000L, cooldown);
        charges.put(player.getUniqueId(), step.counter());
        if (!step.counted()) {
            if (step.counter().cooling(System.currentTimeMillis())) forge.telemetry().increment(ArcaneTelemetry.Metric.STORMWAKE_BLOCKED);
            return;
        }
        if (!step.released()) {
            player.sendActionBar(Chat.render("<aqua>Static Charge " + step.counter().value() + "/" + numbers.threshold() + "</aqua>"));
            return;
        }
        double hit = event.getFinalDamage();
        // Release after this event has fully finished, so no listener sees a nested damage event.
        Bukkit.getScheduler().runTask(plugin, () -> release(player, target, hit, numbers));
    }

    private void release(Player player, LivingEntity origin, double hit, ArcaneSettings.SignatureSettings numbers) {
        if (!fighting(player) || !origin.isValid() || !origin.getWorld().equals(player.getWorld())) return;
        long started = System.nanoTime();
        forge.telemetry().increment(ArcaneTelemetry.Metric.STORMWAKE_RELEASES);
        double search = numbers.range() * Math.max(1, numbers.targets());
        List<SignatureRules.Point> candidates = new ArrayList<>();
        Map<UUID, LivingEntity> byId = new HashMap<>();
        for (Entity nearby : origin.getNearbyEntities(search, search, search)) {
            if (candidates.size() >= MAXIMUM_CANDIDATES) break;
            if (!(nearby instanceof LivingEntity living) || !eligible(player, living, numbers)) continue;
            Location at = living.getLocation();
            candidates.add(new SignatureRules.Point(living.getUniqueId(), at.getX(), at.getY(), at.getZ()));
            byId.put(living.getUniqueId(), living);
        }
        Location start = origin.getLocation();
        List<SignatureRules.Strike> strikes = SignatureRules.chain(
            new SignatureRules.Point(origin.getUniqueId(), start.getX(), start.getY(), start.getZ()),
            candidates, numbers.targets(), numbers.range(), hit * numbers.damagePercent() / 100.0D, numbers.falloff());
        LivingEntity previous = origin;
        for (SignatureRules.Strike strike : strikes) {
            LivingEntity target = byId.get(strike.target());
            if (target == null || !target.isValid() || !previous.hasLineOfSight(target)) break;
            DamageSource source = DamageSource.builder(DamageType.LIGHTNING_BOLT)
                .withCausingEntity(player).withDirectEntity(player).build();
            boolean struck = ArcaneEffectScope.run(player.getUniqueId(), ArcaneSignature.STORMWAKE.id(), target.getUniqueId(),
                strike.damage(), () -> {
                    target.damage(strike.damage(), source);
                    return true;
                });
            if (!struck) break;
            forge.telemetry().increment(ArcaneTelemetry.Metric.STORMWAKE_STRIKES);
            arc(previous.getLocation().add(0, previous.getHeight() / 2, 0), target.getLocation().add(0, target.getHeight() / 2, 0),
                numbers.particles());
            previous = target;
        }
        player.getWorld().playSound(origin.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6F, 1.6F);
        if (System.nanoTime() - started > 2_000_000L) forge.telemetry().increment(ArcaneTelemetry.Metric.SLOW_EFFECTS);
    }

    private boolean eligible(Player player, LivingEntity living, ArcaneSettings.SignatureSettings numbers) {
        if (living.equals(player) || living.isDead() || living.isInvulnerable() || living instanceof ArmorStand) return false;
        if (living instanceof Player other && (!numbers.pvp() || other.getGameMode() == GameMode.SPECTATOR
            || other.getGameMode() == GameMode.CREATIVE)) return false;
        if (living instanceof Tameable tame && tame.isTamed() && player.getUniqueId().equals(tame.getOwnerUniqueId())) return false;
        return !living.getPersistentDataContainer().has(npcKey);
    }

    private static void arc(Location from, Location to, int particles) {
        if (particles <= 0 || !from.getWorld().equals(to.getWorld())) return;
        Vector step = to.toVector().subtract(from.toVector()).multiply(1.0D / particles);
        Location point = from.clone();
        for (int index = 0; index < particles; index++) {
            from.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.02, 0.02, 0.02, 0.0);
            point.add(step);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Heart of the Mountain
    // ---------------------------------------------------------------------------------------------

    /**
     * Called by the skill pipeline for a break the provenance ledger accepted as natural Mining. Only
     * ores count; the count and any reward wait until the break is final.
     */
    public void onVerifiedMining(BlockBreakEvent event) {
        ArcaneSettings settings = forge.settings();
        if (!settings.signatures().active(ArcaneSignature.HEART_OF_THE_MOUNTAIN)) return;
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material ore = block.getType();
        if (!ore(ore) || !fighting(player)) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (plugin.items().arcaneSignature(tool).orElse(null) != ArcaneSignature.HEART_OF_THE_MOUNTAIN) return;
        // The block is still in place at MONITOR, so these are exactly its natural drops for this tool.
        List<ItemStack> drops = block.getDrops(tool, player).stream().limit(8).map(ItemStack::clone).toList();
        Location location = block.getLocation().add(0.5D, 0.5D, 0.5D);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || !event.isDropItems() || !fighting(player) || block.getType() == ore) {
                forge.telemetry().increment(ArcaneTelemetry.Metric.MOUNTAIN_BLOCKED);
                return;
            }
            ArcaneSettings.SignatureSettings numbers = forge.settings().signatures().of(ArcaneSignature.HEART_OF_THE_MOUNTAIN);
            if (!forge.settings().signatures().active(ArcaneSignature.HEART_OF_THE_MOUNTAIN)) return;
            long cooldown = forge.signatureCooldownMillis(player, tool, ArcaneSignature.HEART_OF_THE_MOUNTAIN, numbers.cooldownSeconds());
            SignatureRules.Step step = SignatureRules.gain(resonance.getOrDefault(player.getUniqueId(), SignatureRules.Counter.EMPTY),
                System.currentTimeMillis(), Bukkit.getCurrentTick(), numbers.threshold(), 0L, cooldown);
            resonance.put(player.getUniqueId(), step.counter());
            if (!step.counted()) return;
            if (!step.released()) {
                if (step.counter().value() % 8 == 0) {
                    player.sendActionBar(Chat.render("<gold>Resonance " + step.counter().value() + "/" + numbers.threshold() + "</gold>"));
                }
                return;
            }
            for (ItemStack drop : drops) location.getWorld().dropItemNaturally(location, drop.clone());
            location.getWorld().spawnParticle(Particle.WAX_ON, location, Math.max(0, numbers.particles()), 0.3, 0.3, 0.3, 0.0);
            player.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
            player.sendActionBar(Chat.render("<gold>Heart of the Mountain: the ore yields twice.</gold>"));
            forge.telemetry().increment(ArcaneTelemetry.Metric.MOUNTAIN_RELEASES);
        });
    }

    static boolean ore(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || material == Material.ANCIENT_DEBRIS;
    }

    // ---------------------------------------------------------------------------------------------
    // Last Bastion
    // ---------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onResurrect(EntityResurrectEvent event) {
        // Not cancelled means a real totem is saving the player: it always goes first.
        if (!(event.getEntity() instanceof Player player) || !event.isCancelled()) return;
        ArcaneSettings settings = forge.settings();
        if (!settings.signatures().active(ArcaneSignature.LAST_BASTION) || !fighting(player)) return;
        ItemStack piece = null;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (plugin.items().arcaneSignature(armor).orElse(null) == ArcaneSignature.LAST_BASTION) {
                piece = armor;
                break;
            }
        }
        if (piece == null) return;
        ArcaneSettings.SignatureSettings numbers = settings.signatures().of(ArcaneSignature.LAST_BASTION);
        if (!numbers.pvp() && player.getLastDamageCause() instanceof EntityDamageByEntityEvent lethal && playerSource(lethal.getDamager())) return;
        UUID account = forge.account(player).map(id -> id.value()).orElse(player.getUniqueId());
        long now = System.currentTimeMillis();
        if (now < bastionReady.getOrDefault(account, 0L)) {
            forge.telemetry().increment(ArcaneTelemetry.Metric.BASTION_COOLDOWN);
            return;
        }
        long cooldown = forge.signatureCooldownMillis(player, piece, ArcaneSignature.LAST_BASTION, numbers.cooldownSeconds());
        bastionReady.values().removeIf(readyAt -> readyAt <= now);
        bastionReady.put(account, now + cooldown);
        // The server now applies its totem behaviour: one health, the totem animation and totem effects.
        event.setCancelled(false);
        forge.telemetry().increment(ArcaneTelemetry.Metric.BASTION_SAVES);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead()) return;
            try {
                // Swap the totem's long regeneration for Last Bastion's short, configured protection.
                player.removePotionEffect(PotionEffectType.REGENERATION);
                player.removePotionEffect(PotionEffectType.ABSORPTION);
                player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                int ticks = Math.max(1, numbers.protectionSeconds()) * 20;
                if (numbers.absorptionHearts() > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, ticks,
                        Math.max(0, (numbers.absorptionHearts() + 1) / 2 - 1), false, true, true));
                }
                if (numbers.protectionSeconds() > 0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ticks, 1, false, true, true));
                }
                player.sendActionBar(Chat.render("<gold>Last Bastion held. Ready again in "
                    + Chat.duration(java.time.Duration.ofMillis(cooldown)) + ".</gold>"));
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Last Bastion follow-up failed for " + player.getUniqueId(), failure);
            }
        });
    }

    private static boolean playerSource(Entity damager) {
        return damager instanceof Player || damager instanceof Projectile projectile && projectile.getShooter() instanceof Player;
    }

    // ---------------------------------------------------------------------------------------------
    // Shared
    // ---------------------------------------------------------------------------------------------

    private boolean fighting(Player player) {
        return player.isOnline() && !player.isDead()
            && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)
            && forge.account(player).isPresent();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        charges.remove(id);
        resonance.remove(id);
        attackStrength.remove(id);
        // Last Bastion's cooldown is per account and deliberately survives a reconnect.
    }

    /** Live state sizes for {@code /forgeadmin status}; bounded by online players and accounts that triggered Last Bastion. */
    public String stateSummary() {
        return "charges " + charges.size() + ", resonance " + resonance.size() + ", bastion cooldowns " + bastionReady.size();
    }
}

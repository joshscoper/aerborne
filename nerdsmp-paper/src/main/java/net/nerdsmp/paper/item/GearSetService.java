package net.nerdsmp.paper.item;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Function;

public final class GearSetService implements Listener {
    private final JavaPlugin plugin;
    private final PaperItemService items;
    private final Function<Player, ProgressionService.ProgressionSnapshot> progression;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final AttackCooldownTracker attackCooldowns = new AttackCooldownTracker();
    private final Set<java.util.UUID> pendingRefresh = new HashSet<>();
    private final double criticalHitMultiplier;

    public GearSetService(
        JavaPlugin plugin,
        PaperItemService items,
        Function<Player, ProgressionService.ProgressionSnapshot> progression
    ) {
        this.plugin = plugin;
        this.items = items;
        this.progression = progression;
        criticalHitMultiplier = Math.max(1.0D,
            plugin.getConfig().getDouble("combat.critical-hit-multiplier", 1.5D));
    }

    public GearProfile profile(Player player) {
        return profile(player, player.getInventory().getItemInMainHand());
    }

    public GearProfile profile(Player player, ItemStack mainHand) {
        EnumMap<EquipmentSlot, ItemStack> equipped = new EnumMap<>(EquipmentSlot.class);
        equipped.put(EquipmentSlot.HAND, mainHand);
        equipped.put(EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand());
        equipped.put(EquipmentSlot.HEAD, player.getInventory().getHelmet());
        equipped.put(EquipmentSlot.CHEST, player.getInventory().getChestplate());
        equipped.put(EquipmentSlot.LEGS, player.getInventory().getLeggings());
        equipped.put(EquipmentSlot.FEET, player.getInventory().getBoots());
        GearProfile equipment = profile(equipped);
        Map<String, Double> stats = new HashMap<>(equipment.stats());
        var training = progression.apply(player);
        PassiveTalentStats.resolve(training.talents()).forEach((stat, value) -> stats.merge(stat, value, Double::sum));
        return new GearProfile(stats, equipment.setPieces(), equipment.activeBonuses());
    }

    public GearProfile profile(Map<EquipmentSlot, ItemStack> equipped) {
        Map<String, Double> stats = new HashMap<>();
        Map<String, Set<ItemId>> pieces = new HashMap<>();

        equipped.forEach((slot, item) -> items.gear(item).ifPresent(gear -> {
            if (!gear.slot().matches(slot)) {
                return;
            }
            gear.stats().forEach((stat, value) -> stats.merge(stat, value, Double::sum));
            if (gear.setId() != null) {
                pieces.computeIfAbsent(gear.setId(), ignored -> new HashSet<>()).add(gear.itemId());
            }
        }));

        Map<String, Integer> pieceCounts = new HashMap<>();
        List<ActiveBonus> activeBonuses = new ArrayList<>();
        pieces.forEach((setId, uniqueItems) -> {
            int count = uniqueItems.size();
            pieceCounts.put(setId, count);
            items.setDefinition(setId).ifPresent(set -> set.bonuses().forEach((requiredPieces, bonus) -> {
                if (count >= requiredPieces) {
                    bonus.stats().forEach((stat, value) -> stats.merge(stat, value, Double::sum));
                    activeBonuses.add(new ActiveBonus(setId, set.name(), requiredPieces, bonus.description()));
                }
            }));
        });

        return new GearProfile(stats, pieceCounts, activeBonuses);
    }

    public OptionalDouble customWeaponDamage(Player player) {
        Optional<DamageBreakdown> breakdown = damageBreakdown(player);
        return breakdown.isEmpty()
            ? OptionalDouble.empty()
            : OptionalDouble.of(breakdown.get().finalDamage());
    }

    public Optional<DamageBreakdown> damageBreakdown(Player player) {
        return damageBreakdown(player, player.getAttackCooldown());
    }

    private Optional<DamageBreakdown> damageBreakdown(Player player, double attackCooldown) {
        return damageBreakdown(player, player.getInventory().getItemInMainHand(), "damage", attackCooldown, 0.0D);
    }

    private Optional<DamageBreakdown> projectileDamageBreakdown(Player player, AbstractArrow arrow) {
        ItemStack weaponItem = arrow.getWeapon();
        return damageBreakdown(player, weaponItem, "projectile-damage", 1.0D,
            profile(player, weaponItem).stat("projectile-damage-percent"));
    }

    private Optional<DamageBreakdown> damageBreakdown(
        Player player,
        ItemStack weaponItem,
        String damageStat,
        double attackCooldown,
        double specializedPercentBonus
    ) {
        var weapon = items.gear(weaponItem);
        if (weapon.isEmpty() || !weapon.get().slot().matches(EquipmentSlot.HAND)) {
            return Optional.empty();
        }
        double baseDamage = weapon.get().stats().getOrDefault(damageStat, 0.0D);
        if (baseDamage <= 0.0D) {
            return Optional.empty();
        }
        GearProfile profile = profile(player, weaponItem);
        ProgressionService.ProgressionSnapshot progress = progression.apply(player);
        double cooldown = Math.max(0.0D, Math.min(1.0D, attackCooldown));
        double flatBonus = profile.stat("damage-flat");
        double percentBonus = profile.stat("damage-percent") + specializedPercentBonus
            + progress.adventurerStat("damage-percent");
        double finalDamage = GearMath.calculateDamage(
            baseDamage,
            flatBonus,
            percentBonus,
            cooldown
        );
        return Optional.of(new DamageBreakdown(
            weapon.get().itemId(), baseDamage, flatBonus, percentBonus, cooldown, finalDamage
        ));
    }

    public void refreshInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            items.refresh(item);
        }
    }

    public Component summary(Player player) {
        GearProfile profile = profile(player);
        if (profile.activeBonuses().isEmpty()) {
            return miniMessage.deserialize("<gray>No gear-set bonuses are active.</gray>");
        }
        String joined = profile.activeBonuses().stream()
            .map(bonus -> miniMessage.escapeTags(bonus.setName()) + " " + bonus.requiredPieces() + "pc")
            .distinct()
            .sorted()
            .reduce((left, right) -> left + "<dark_gray>, </dark_gray>" + right)
            .orElse("");
        return miniMessage.deserialize("<gold>Active: <white>" + joined + "</white></gold>");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack()) {
            return;
        }
        var captured = attackCooldowns.capture(
            event.getPlayer().getUniqueId(),
            event.getAttacked().getUniqueId(),
            event.getPlayer().getAttackCooldown()
        );
        Bukkit.getScheduler().runTask(plugin, () -> attackCooldowns.expire(
            event.getPlayer().getUniqueId(),
            event.getAttacked().getUniqueId(),
            captured
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = null;
        GearProfile attackProfile = null;
        Optional<DamageBreakdown> damage = Optional.empty();
        if (net.nerdsmp.paper.anticheat.ArcaneEffectScope.covers(event)) {
            // A signature's arc: its damage is already final. Not a swing, so no weapon stats. Defense still applies below.
        } else if (event.getDamager() instanceof Player directAttacker) {
            attacker = directAttacker;
            double attackCooldown = attackCooldowns.consume(
                attacker.getUniqueId(),
                event.getEntity().getUniqueId(),
                attacker.getAttackCooldown()
            );
            damage = damageBreakdown(attacker, attackCooldown);
            attackProfile = profile(attacker);
        } else if (event.getDamager() instanceof AbstractArrow arrow
            && arrow.getShooter() instanceof Player projectileAttacker) {
            attacker = projectileAttacker;
            damage = projectileDamageBreakdown(attacker, arrow);
            ItemStack weapon = arrow.getWeapon();
            attackProfile = profile(attacker, weapon);
        }
        if (attacker != null && damage.isPresent()) {
            double huntingBonus = event.getEntity() instanceof Player ? 0.0D
                : progression.apply(attacker).talentStat(Skill.HUNTING, "hunting-damage-percent");
            double resolvedDamage = GearMath.applyPercentBonus(damage.get().finalDamage(), huntingBonus);
            if (event.getEntity() instanceof LivingEntity living && living.getHealth() > 0.0D
                && living.getAttribute(Attribute.MAX_HEALTH) != null
                && living.getHealth() / living.getAttribute(Attribute.MAX_HEALTH).getValue() <= 0.30D) {
                resolvedDamage = GearMath.applyPercentBonus(resolvedDamage,
                    attackProfile.stat("execute-damage-percent"));
            }
            event.setDamage(GearMath.applyCritical(resolvedDamage, event.isCritical(), criticalHitMultiplier));
        } else if (attacker != null && attackProfile != null) {
            // Armor/set bonuses also affect ordinary weapons. Vanilla has already applied
            // cooldown and critical multipliers on this path; never apply them twice.
            double percent = attackProfile.stat("damage-percent") + progression.apply(attacker).adventurerStat("damage-percent");
            if (event.getDamager() instanceof AbstractArrow) percent += attackProfile.stat("projectile-damage-percent");
            double resolved = GearMath.applyPercentBonus(event.getDamage() + attackProfile.stat("damage-flat"), percent);
            if (!(event.getEntity() instanceof Player)) resolved = GearMath.applyPercentBonus(resolved,
                progression.apply(attacker).talentStat(Skill.HUNTING, "hunting-damage-percent"));
            if (event.getEntity() instanceof LivingEntity living && living.getAttribute(Attribute.MAX_HEALTH) != null
                && living.getHealth() / living.getAttribute(Attribute.MAX_HEALTH).getValue() <= 0.30D)
                resolved = GearMath.applyPercentBonus(resolved, attackProfile.stat("execute-damage-percent"));
            event.setDamage(resolved);
        }
        if (event.getEntity() instanceof Player defender) {
            double defense = profile(defender).stat("defense")
                + progression.apply(defender).adventurerStat("defense");
            if (defense > 0.0D) {
                event.setDamage(GearMath.mitigateDamage(event.getDamage(), defense));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLifeSteal(EntityDamageByEntityEvent event) {
        if (net.nerdsmp.paper.anticheat.ArcaneEffectScope.covers(event)) return; // one hit, one heal
        Player attacker;
        GearProfile attackProfile;
        if (event.getDamager() instanceof Player directAttacker) {
            attacker = directAttacker;
            attackProfile = profile(attacker);
        } else if (event.getDamager() instanceof AbstractArrow arrow
            && arrow.getShooter() instanceof Player projectileAttacker) {
            attacker = projectileAttacker;
            ItemStack weapon = arrow.getWeapon();
            attackProfile = profile(attacker, weapon);
        } else {
            return;
        }
        if (event.getEntity() instanceof Player) {
            return;
        }
        double percent = attackProfile.stat("life-steal-percent");
        if (percent <= 0.0D || event.getFinalDamage() <= 0.0D) {
            return;
        }
        if(!(event.getEntity() instanceof LivingEntity target)||event.getEntity() instanceof org.bukkit.entity.ArmorStand) return;
        double targetBudget = target.getHealth()+target.getAbsorptionAmount();
        Player recipient=attacker;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(event.isCancelled()||event.getFinalDamage()<=0||!recipient.isOnline()||recipient.isDead()
                ||Bukkit.getPlayer(recipient.getUniqueId())!=recipient) return;
            double maximumHealth=recipient.getAttribute(Attribute.MAX_HEALTH)==null?20:recipient.getAttribute(Attribute.MAX_HEALTH).getValue();
            recipient.setHealth(Math.min(maximumHealth,recipient.getHealth()+GearMath.lifeSteal(event.getFinalDamage(),targetBudget,percent)));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack base = event.getInventory().getItem(0);
        if (items.describe(base).isPresent()) {
            event.setResult(null);
        }
    }

    private void scheduleRefresh(Player player) {
        if (!pendingRefresh.add(player.getUniqueId())) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingRefresh.remove(player.getUniqueId());
            if (player.isOnline()) {
                refreshInventory(player);
                if (plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core && player.getGameMode() == org.bukkit.GameMode.SURVIVAL)
                    core.achievements().setAtLeast(player, net.nerdsmp.paper.achievement.AchievementMetric.ACTIVE_SET_BONUSES, profile(player).activeBonuses().size());
            }
        });
    }

    public record GearProfile(
        Map<String, Double> stats,
        Map<String, Integer> setPieces,
        List<ActiveBonus> activeBonuses
    ) {
        public GearProfile {
            stats = Map.copyOf(stats);
            setPieces = Map.copyOf(setPieces);
            activeBonuses = List.copyOf(activeBonuses);
        }

        public double stat(String id) {
            return stats.getOrDefault(id, 0.0D);
        }
    }

    public record ActiveBonus(String setId, String setName, int requiredPieces, String description) {
    }

    public record DamageBreakdown(
        ItemId itemId,
        double baseDamage,
        double flatBonus,
        double percentBonus,
        double cooldown,
        double finalDamage
    ) {
    }
}

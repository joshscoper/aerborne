package net.nerdsmp.paper.item;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.block.data.type.Cake;
import org.bukkit.event.block.Action;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** One food-independent recovery loop for islands, adventure maps and expedition worlds. */
public final class RecoveryService implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final RecoveryRules rules;
    private final RecoveryRepository repository;
    private final boolean enabled;
    private final Map<UUID, Active> meals = new HashMap<>();
    private final Map<UUID, Long> blocked = new HashMap<>(), failures = new HashMap<>();
    private BukkitTask task;

    public RecoveryService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        enabled = plugin.getConfig().getBoolean("food-and-recovery.enabled", true);
        rules = new RecoveryRules(plugin.getConfig().getInt("food-and-recovery.combat-delay-seconds", 8),
            plugin.getConfig().getDouble("food-and-recovery.health-percent-per-second", 5),
            plugin.getConfig().getInt("food-and-recovery.well-fed-seconds", 300),
            plugin.getConfig().getDouble("food-and-recovery.well-fed-recovery-percent", 25));
        repository = new RecoveryRepository(plugin.playerData());
        FoodItems.configure(enabled, rules);
    }
    public boolean enabled() { return enabled; }
    public RecoveryRules rules() { return rules; }
    public RecoveryRules.WellFed food(Player player) {
        var active = meals.get(player.getUniqueId());
        return active == null ? RecoveryRules.WellFed.NONE : active.food();
    }
    public long blockedUntil(Player player) { return blocked.getOrDefault(player.getUniqueId(), 0L); }
    public void start() {
        if (!enabled) return;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getOnlinePlayers().forEach(this::initialize);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }
    private static boolean playable(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }
    private boolean alive(Player player) {
        return player.isOnline() && !player.isDead() && playable(player)
            && plugin.sessions().account(player.getUniqueId()).isPresent()
            && plugin.presentation().progressionReady(player)
            && (plugin.spirits() == null || !plugin.spirits().ghost(player.getUniqueId()));
    }
    private void initialize(Player player) {
        delay(player);
        if (playable(player)) normalize(player);
        prepareFood(player);
    }
    private static void prepareFood(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            int slot = i;
            FoodItems.upgrade(inventory.getItem(slot)).ifPresent(food -> inventory.setItem(slot, food));
        }
    }
    private void delay(Player player) { blocked.put(player.getUniqueId(), System.currentTimeMillis() + rules.combatDelaySeconds() * 1000L); }
    private static void normalize(Player player) {
        if (player.getFoodLevel() != 20) player.setFoodLevel(20);
        if (player.getSaturation() != 0) player.setSaturation(0);
        if (player.getExhaustion() != 0) player.setExhaustion(0);
    }
    private Active load(Player player) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) return null;
        Active active = meals.get(player.getUniqueId());
        if (active != null && active.account().equals(account)) return active;
        Active loaded = new Active(account, repository.load(account));
        meals.put(player.getUniqueId(), loaded);
        return loaded;
    }
    private void save(Player player, Active active, RecoveryRules.WellFed food) {
        repository.save(active.account(), food);
        meals.put(player.getUniqueId(), new Active(active.account(), food));
    }
    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            prepareFood(player);
            if (playable(player)) normalize(player);
            if (!alive(player)) continue;
            try {
                Active active = load(player);
                if (active == null || player.hasPotionEffect(PotionEffectType.POISON) || player.hasPotionEffect(PotionEffectType.WITHER)) continue;
                var maximum = player.getAttribute(Attribute.MAX_HEALTH);
                if (maximum == null) continue;
                double healing = rules.healing(player.getHealth(), maximum.getValue(), now, blockedUntil(player), active.food());
                if (healing <= 0) continue;
                EntityRegainHealthEvent regain = new EntityRegainHealthEvent(player, healing, EntityRegainHealthEvent.RegainReason.CUSTOM);
                Bukkit.getPluginManager().callEvent(regain);
                if (!regain.isCancelled() && Double.isFinite(regain.getAmount()) && regain.getAmount() > 0 && alive(player))
                    player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), player.getHealth() + regain.getAmount()));
            } catch (RuntimeException failure) { fail(player, failure); }
        }
    }
    private void fail(Player player, RuntimeException failure) {
        long now = System.currentTimeMillis();
        if (failures.getOrDefault(player.getUniqueId(), 0L) > now) return;
        failures.put(player.getUniqueId(), now + 60_000);
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not load/save food recovery for " + player.getUniqueId(), failure);
    }
    public String status(Player player) {
        if (!enabled || !alive(player)) return "";
        long now = System.currentTimeMillis();
        var fed = food(player);
        return fed.active(now) ? " <dark_gray>│</dark_gray> <gold>Well Fed " + clock((fed.until() - now + 999) / 1000) + "</gold>" : "";
    }
    /** True while a recent hit is holding this wounded player's recovery back. */
    public boolean delayed(Player player) {
        return enabled && alive(player) && player.getHealth() < player.getAttribute(Attribute.MAX_HEALTH).getValue() && blockedUntil(player) > System.currentTimeMillis();
    }
    private static String clock(long seconds) { return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hunger(FoodLevelChangeEvent event) { if (event.getEntity() instanceof Player p && playable(p)) { event.setCancelled(true); normalize(p); } }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void exhaust(EntityExhaustionEvent event) { if (event.getEntity() instanceof Player p && playable(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void vanillaRegen(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player p && playable(p) && event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void starvation(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && playable(p) && event.getCause() == EntityDamageEvent.DamageCause.STARVATION) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (event.getDamage() <= 0) return;
        if (event.getEntity() instanceof Player p && playable(p)) delay(p);
        if (event instanceof EntityDamageByEntityEvent hit) {
            Player attacker = hit.getDamager() instanceof Player p ? p
                : hit.getDamager() instanceof Projectile shot && shot.getShooter() instanceof Player p ? p : null;
            if (attacker != null && playable(attacker)) delay(attacker);
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        boolean milk = event.getItem().getType() == Material.MILK_BUCKET;
        if (!milk && event.getItem().getData(DataComponentTypes.FOOD) == null || !alive(player)) return;
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        ItemStack eaten = event.getItem().clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || !alive(player) || Bukkit.getPlayer(player.getUniqueId()) != player
                || plugin.sessions().account(player.getUniqueId()).filter(account::equals).isEmpty()) return;
            try {
                Active active = load(player); if (active == null) return;
                if (milk) { save(player, active, RecoveryRules.WellFed.NONE); return; }
                int nutrition = 0;
                if (eaten.hasItemMeta()) {
                    var pdc = eaten.getItemMeta().getPersistentDataContainer();
                    int schema = pdc.getOrDefault(new NamespacedKey(plugin, "harvest_dish_schema"), org.bukkit.persistence.PersistentDataType.INTEGER, 0);
                    if (schema == 1 || schema == 2) nutrition = pdc.getOrDefault(new NamespacedKey(plugin, "meal_nutrition"), org.bukkit.persistence.PersistentDataType.INTEGER, 0);
                }
                feed(player, active, nutrition);
            } catch (RuntimeException failure) { fail(player, failure); }
        });
    }
    private void feed(Player player, Active active, int nutrition) {
        var next = rules.meal(System.currentTimeMillis(), nutrition);
        save(player, active, next);
        normalize(player);
        PlayerStatsService.message(player, Chat.render("<gold>Well Fed</gold> <gray>+" + PlayerStatsService.number(next.recoveryPercent()) + "% recovery · " + clock(rules.foodSeconds()) + "</gray>"));
    }
    /** Cake checks hunger in block logic rather than the held item's food component. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cake(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
            || event.useInteractedBlock() == Event.Result.DENY || event.getClickedBlock() == null
            || event.getClickedBlock().getType() != Material.CAKE || !alive(event.getPlayer())) return;
        Player player = event.getPlayer();
        event.setCancelled(true);
        try {
            Active active = load(player); if (active == null) return;
            feed(player, active, 0);
            var block = event.getClickedBlock(); Cake cake = (Cake) block.getBlockData();
            if (cake.getBites() == cake.getMaximumBites()) block.setType(Material.AIR);
            else { cake.setBites(cake.getBites() + 1); block.setBlockData(cake); }
            player.incrementStatistic(Statistic.CAKE_SLICES_EATEN);
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1, 1);
        } catch (RuntimeException failure) { fail(player, failure); }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent event) { initialize(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void held(PlayerItemHeldEvent event) { prepareFood(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void respawn(PlayerRespawnEvent event) { delay(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void died(PlayerDeathEvent event) {
        Player player = event.getPlayer(); delay(player);
        try { var active = load(player); if (active != null) save(player, active, RecoveryRules.WellFed.NONE); }
        catch (RuntimeException failure) { meals.remove(player.getUniqueId()); fail(player, failure); }
    }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) {
        meals.remove(event.getPlayer().getUniqueId()); blocked.remove(event.getPlayer().getUniqueId()); failures.remove(event.getPlayer().getUniqueId());
    }
    @Override public void close() {
        if (task != null) task.cancel(); HandlerList.unregisterAll(this); FoodItems.enabled(false);
        meals.clear(); blocked.clear(); failures.clear();
    }
    private record Active(AccountId account, RecoveryRules.WellFed food) { }
}

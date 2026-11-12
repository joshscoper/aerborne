package net.nerdsmp.probe;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.death.*;
import net.nerdsmp.paper.item.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import java.nio.file.*;
import java.util.*;

/** Disposable, seeded feature verification. Native clients eat and attack; never loaded in production. */
public final class FoodRecoveryProbe extends JavaPlugin implements Listener {
    private NerdSmpPlugin core;
    private Player player, watcher;
    private World arena;
    private AccountId account;
    private int phase, waits, consumes, cancelled;
    private boolean cancelNext, cancelHealing;
    private final List<String> checks = new ArrayList<>();
    private final List<Double> heals = new ArrayList<>();
    private long savedUntil;
    private double healthBefore;
    private final Map<String, Object> observations = new LinkedHashMap<>();
    private static final int Y = 160;
    @Override public void onEnable() {
        phase = Integer.getInteger("nerdsmp.foodProbe", 0);
        if (phase < 1 || phase > 2 || !Bukkit.getIp().equals("127.0.0.1")) throw new IllegalStateException("Loopback disposable fixture only");
        core = (NerdSmpPlugin) Bukkit.getPluginManager().getPlugin("NerdSMP");
        Bukkit.getPluginManager().registerEvents(this, this);
        later(100, this::ready);
    }
    private void ready() throws Exception {
        player = Bukkit.getPlayerExact("F_Player"); watcher = Bukkit.getPlayerExact("F_Watcher");
        if (++waits > 120) throw new AssertionError("Clients did not initialize");
        if (player == null || watcher == null || !core.presentation().progressionReady(player)
            || !core.presentation().progressionReady(watcher) || core.sessions().account(player.getUniqueId()).isEmpty()) { later(20, this::ready); return; }
        account = core.sessions().account(player.getUniqueId()).orElseThrow();
        arena = Bukkit.getWorld("RecoveryArena");
        if (arena == null) arena = new WorldCreator("RecoveryArena").type(WorldType.FLAT).generateStructures(false).createWorld();
        arena.setDifficulty(Difficulty.NORMAL); arena.setTime(6000);
        for (int x = -30; x <= 30; x++) for (int z = -30; z <= 30; z++) {
            arena.getBlockAt(x, Y - 1, z).setType(Material.STONE);
            for (int y = Y; y <= Y + 3; y++) arena.getBlockAt(x, y, z).setType(Material.AIR);
        }
        watcher.setInvulnerable(true); watcher.teleport(new Location(arena, .5, Y, 5.5));
        player.closeInventory(); player.teleport(new Location(arena, .5, Y, .5));
        core.watchdog().grantGrace(player, 600000); core.watchdog().grantGrace(watcher, 600000);
        if (phase == 2) { afterRestart(); return; }
        player.setGameMode(GameMode.SURVIVAL); player.setInvulnerable(false); player.getInventory().clear();
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20);
        check(core.recovery().enabled(), "Food and recovery enabled by default");
        check(core.recovery().rules().combatDelaySeconds() == 8 && core.recovery().rules().healthPercentPerSecond() == 5, "Testing shipped recovery rates, not shortened test rates");
        ItemStack carrot = FoodItems.upgrade(new ItemStack(Material.CARROT, 3)).orElseThrow();
        check(carrot.getData(DataComponentTypes.FOOD).canAlwaysEat(), "Food component allows eating at full hunger");
        check(CustomRecipeService.ordinaryIngredient(carrot) && !CraftingGuard.protectedIngredient(carrot), "Food component does not break ordinary recipe ingredients");
        ItemStack named = carrot.clone(); named.editMeta(meta -> meta.displayName(net.kyori.adventure.text.Component.text("Special carrot")));
        check(!CustomRecipeService.ordinaryIngredient(named), "Unrelated food metadata still cannot substitute for ordinary ingredients");
        check(net.nerdsmp.paper.gathering.CropReplanting.seedSlot(new ItemStack[]{carrot}, Material.CARROT) == 0, "Carrots remain valid replanting seeds after food upgrade");
        check(net.nerdsmp.paper.gathering.CropReplanting.seedSlot(new ItemStack[]{named}, Material.CARROT) == -1, "Renamed food is still protected from replanting consumption");
        var hoeSeed = net.nerdsmp.paper.gathering.HoeHarvestService.class.getDeclaredMethod("plainSeed", ItemStack.class, Material.class); hoeSeed.setAccessible(true);
        check((boolean) hoeSeed.invoke(null, carrot, Material.CARROT) && !(boolean) hoeSeed.invoke(null, named, Material.CARROT), "Hoe auto-replant distinguishes ordinary upgraded food from special food");
        var machine = net.nerdsmp.paper.skyblock.machine.IslandMachines.class.getDeclaredMethod("allows", net.nerdsmp.paper.skyblock.machine.MachineKind.class, int.class, ItemStack.class); machine.setAccessible(true);
        check((boolean) machine.invoke(null, net.nerdsmp.paper.skyblock.machine.MachineKind.COMPOSTER, 0, carrot), "Compost machines accept upgraded ordinary crops");
        check(!(boolean) machine.invoke(null, net.nerdsmp.paper.skyblock.machine.MachineKind.COMPOSTER, 0, named), "Compost machines still reject special food");
        var objective = new net.nerdsmp.paper.quest.QuestDefinition.Objective(net.nerdsmp.paper.quest.QuestDefinition.Type.DELIVER, Set.of("CARROT"), null, 2, "Carry carrots");
        var held = net.nerdsmp.paper.quest.QuestService.class.getDeclaredMethod("held", Player.class, net.nerdsmp.paper.quest.QuestDefinition.Objective.class); held.setAccessible(true);
        player.getInventory().setItem(0, carrot); player.getInventory().setItem(1, named);
        check((int) held.invoke(core.quests(), player, objective) == 3, "Food delivery objectives count only upgraded ordinary carrots");
        var take = net.nerdsmp.paper.quest.QuestService.class.getDeclaredMethod("take", Player.class, net.nerdsmp.paper.quest.QuestDefinition.Objective.class); take.setAccessible(true); take.invoke(core.quests(), player, objective);
        check(player.getInventory().getItem(0).getAmount() == 1 && player.getInventory().getItem(1).getAmount() == 3, "Food delivery consumes the exact objective amount and preserves special carrots");
        player.getInventory().clear();
        ItemStack meal = Bukkit.getRecipe(new NamespacedKey(core, "meal_orchard_loaf")).getResult();
        check(!CustomRecipeService.ordinaryIngredient(meal) && CraftingGuard.protectedIngredient(meal), "Named meals remain protected from ingredient substitution");
        player.setFoodLevel(2); player.setSaturation(0); player.setExhaustion(3.5f);
        later(25, () -> {
            hunger();
            var regain = new EntityRegainHealthEvent(player, 5, EntityRegainHealthEvent.RegainReason.SATIATED); Bukkit.getPluginManager().callEvent(regain);
            check(regain.isCancelled(), "Vanilla hunger regeneration is cancelled");
            var magic = new EntityRegainHealthEvent(player, 1, EntityRegainHealthEvent.RegainReason.MAGIC_REGEN); Bukkit.getPluginManager().callEvent(magic);
            check(!magic.isCancelled(), "Potion regeneration event remains available during combat");
            var starvation = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.STARVATION, 1); Bukkit.getPluginManager().callEvent(starvation);
            check(starvation.isCancelled(), "Starvation cannot damage a player");
            injury(10); player.sendMessage("F_SPRINT"); later(80, () -> {
                hunger(); check(close(player.getHealth(), 10), "No passive healing during the first four seconds after damage");
                player.teleport(new Location(arena, .5, Y, .5)); later(120, this::baseline);
            });
        });
    }
    private void baseline() throws Exception {
        check(player.getHealth() > 10 && player.getHealth() <= 14.1, "Passive recovery begins after eight-second damage cooldown");
        check(heals.stream().anyMatch(value -> close(value, 1)), "Unfed recovery restores exactly 5% of actual 20 health each pulse");
        observations.put("unfed-pulses", List.copyOf(heals));
        player.setHealth(max()); held(new ItemStack(Material.BREAD, 2));
        later(10, () -> { player.sendMessage("F_EAT"); later(55, this::bread); });
    }
    private void bread() throws Exception {
        check(player.getInventory().getItemInMainHand().getAmount() == 1, "Native right-click eats exactly one bread at full health and full hunger");
        check(consumes >= 1, "Server received a real client food consumption event");
        var food = core.recovery().food(player); check(food.active(System.currentTimeMillis()) && close(food.recoveryPercent(), 25), "Ordinary bread grants one 25% Well Fed benefit");
        check(food.until() - System.currentTimeMillis() > 295000, "Ordinary food uses the five-minute default duration"); hunger();
        var meal = chefMeal(); held(meal);
        later(10, () -> { player.sendMessage("F_EAT"); later(55, this::chef); });
    }
    private void chef() throws Exception {
        check(player.hasPotionEffect(PotionEffectType.SPEED), "Existing Cooking speed effect applies while hunger is full");
        check(close(core.recovery().food(player).recoveryPercent(), 40), "Prepared Nourishment quality improves Well Fed by 15 percentage points");
        check(player.getInventory().getItemInMainHand().getType().isAir(), "Prepared serving is actually consumed");
        check(core.recovery().status(player).contains("Well Fed"), "Vitals include active food benefit and remaining time");
        heals.clear(); injury(10); later(205, () -> {
            check(heals.stream().anyMatch(value -> close(value, 1.4)), "Chef quality produces actual 7% maximum-health recovery pulses");
            observations.put("chef-pulses", List.copyOf(heals));
            savedUntil = core.recovery().food(player).until(); held(new ItemStack(Material.APPLE, 2)); cancelNext = true;
            later(10, () -> { player.sendMessage("F_EAT"); later(55, this::cancelledFood); });
        });
    }
    private void cancelledFood() throws Exception {
        check(cancelled == 1 && player.getInventory().getItemInMainHand().getAmount() == 2, "Cancelled native consumption spends no food");
        check(core.recovery().food(player).until() == savedUntil, "Cancelled consumption grants or refreshes no Well Fed benefit");
        held(new ItemStack(Material.MILK_BUCKET)); later(10, () -> { player.sendMessage("F_EAT"); later(55, this::milk); });
    }
    private void milk() throws Exception {
        check(!core.recovery().food(player).active(System.currentTimeMillis()), "Real milk consumption clears Well Fed");
        check(!player.hasPotionEffect(PotionEffectType.SPEED), "Milk retains native removal of Cooking potion effects");
        check(player.getInventory().getItemInMainHand().getType() == Material.BUCKET, "Milk returns exactly one native bucket");
        held(new ItemStack(Material.MUSHROOM_STEW)); later(10, () -> { player.sendMessage("F_EAT"); later(55, this::stew); });
    }
    private void stew() throws Exception {
        check(player.getInventory().getItemInMainHand().getType() == Material.BOWL, "Native full-hunger stew consumption returns a bowl");
        check(close(core.recovery().food(player).recoveryPercent(), 25), "Ordinary stew grants Well Fed without stacking previous food"); hunger();
        held(new ItemStack(Material.GOLDEN_APPLE)); later(10, () -> { player.sendMessage("F_EAT"); later(55, this::goldenApple); });
    }
    private void goldenApple() throws Exception {
        check(player.hasPotionEffect(PotionEffectType.REGENERATION) && player.hasPotionEffect(PotionEffectType.ABSORPTION), "Golden apple retains native regeneration and absorption");
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType())); player.setAbsorptionAmount(0);
        player.setHealth(10); Zombie target = arena.spawn(player.getLocation().clone().add(1.5, 0, 0), Zombie.class, zombie -> { zombie.setAI(false); zombie.setSilent(true); zombie.setShouldBurnInDay(false); });
        held(new ItemStack(Material.DIAMOND_SWORD)); later(10, () -> { player.sendMessage("F_ATTACK"); later(15, () -> {
            check(target.getHealth() < target.getAttribute(Attribute.MAX_HEALTH).getValue(), "A real client attack damages a live target");
            check(core.recovery().blockedUntil(player) > System.currentTimeMillis() + 6000, "Dealing damage pauses recovery for the attacker too");
            target.remove(); later(60, () -> { check(close(player.getHealth(), 10), "Food cannot enable passive healing while attacking"); later(140, this::poison); });
        }); });
    }
    private void poison() throws Exception {
        player.setHealth(10); heals.clear(); player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
        later(45, () -> {
            check(heals.isEmpty(), "Poison suppresses passive recovery instead of being healed through");
            player.removePotionEffect(PotionEffectType.POISON); injury(10); cancelHealing = true;
            later(210, () -> {
                check(close(player.getHealth(), 10), "Custom recovery honors another plugin cancelling the regain-health event");
                cancelHealing = false; held(chefMeal());
                later(10, () -> { player.sendMessage("F_EAT"); later(55, this::restartPrepared); });
            });
        });
    }
    private void restartPrepared() throws Exception {
        check(close(core.recovery().food(player).recoveryPercent(), 40), "Chef food is active before real server restart");
        injury(10); player.saveData();
        Files.writeString(Path.of("food-restart-until.txt"), Long.toString(core.recovery().food(player).until()));
        finish();
    }
    private void afterRestart() throws Exception {
        savedUntil = Long.parseLong(Files.readString(Path.of("food-restart-until.txt")));
        later(5, () -> {
            check(core.recovery().food(player).until() == savedUntil && close(core.recovery().food(player).recoveryPercent(), 40), "Well Fed quality and absolute deadline survive real stop/start");
            check(close(player.getHealth(), 10), "Logout/restart grants no offline healing");
            hunger();
            player.setGameMode(GameMode.ADVENTURE); player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200); injury(100); heals.clear();
            later(210, () -> {
                check(heals.stream().anyMatch(value -> close(value, 14)), "Adventure-mode recovery scales to the actual 200-health pool with food quality");
                observations.put("scaled-pulses", List.copyOf(heals));
                player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20); player.setHealth(20); held(new ItemStack(Material.APPLE, 2));
                later(10, () -> { player.sendMessage("F_EAT"); later(55, this::adventureFood); });
            });
        });
    }
    private void adventureFood() throws Exception {
        check(player.getInventory().getItemInMainHand().getAmount() == 1, "Adventure mode also supports native eating at full hunger");
        check(close(core.recovery().food(player).recoveryPercent(), 25), "Latest serving replaces chef quality rather than extending it with cheaper food");
        World world = Bukkit.getWorld("MMORPG"); Location shrine = new Location(world, 780.5, Y, -50.5), body = shrine.clone().add(25, 0, 0);
        for (int x = 775; x < 812; x++) for (int z = -55; z < -45; z++) { world.getBlockAt(x, Y - 1, z).setType(Material.STONE); for (int y = Y; y < Y + 4; y++) world.getBlockAt(x, y, z).setType(Material.AIR); }
        core.spirits().sanctuaries().put(new SanctuaryStore.Sanctuary("food_probe", "Food Test Sanctuary", SpiritService.point(shrine)));
        player.setGameMode(GameMode.SURVIVAL); player.teleport(body); held(new ItemStack(Material.APPLE, 2)); player.setHealth(0);
        later(70, this::ghost);
    }
    private void ghost() throws Exception {
        check(core.spirits().ghost(player.getUniqueId()) && !player.isDead(), "Actual MMORPG death releases the player as a spirit");
        check(!core.recovery().food(player).active(System.currentTimeMillis()), "Death removes Well Fed in canonical saved state");
        check(player.getInventory().getItemInMainHand().getAmount() == 2, "Death still retains food inventory");
        player.closeInventory(); player.setHealth(10); heals.clear(); player.sendMessage("F_EAT");
        later(205, () -> {
            check(close(player.getHealth(), 10) && heals.isEmpty(), "Ghosts never receive passive recovery");
            check(player.getInventory().getItemInMainHand().getAmount() == 2 && !core.recovery().food(player).active(System.currentTimeMillis()), "Ghost cannot consume food or receive Well Fed");
            player.sendMessage("F_SAY /spirit healer"); later(15, () -> { player.sendMessage("F_CLICK 24"); later(30, this::resurrected); });
        });
    }
    private void resurrected() throws Exception {
        check(!core.spirits().ghost(player.getUniqueId()) && close(player.getHealth(), max()), "Spirit Healer restoration still returns full actual health"); hunger();
        player.sendMessage("F_SAY /island create"); later(100, () -> {
            check(core.skyblock().isIslandWorld(player.getWorld()), "Client enters a real private island"); hunger();
            injury(max() / 2); heals.clear(); later(210, () -> {
                check(!heals.isEmpty() && player.getHealth() > max() / 2, "Private islands use the same free passive recovery");
                player.setHealth(max());
                var at = player.getLocation().clone().add(1, 0, 0).getBlock(); at.getRelative(org.bukkit.block.BlockFace.DOWN).setType(Material.STONE); at.setType(Material.CAKE);
                held(new ItemStack(Material.STICK));
                later(10, () -> { player.sendMessage("F_CAKE"); later(25, () -> {
                    check(at.getBlockData() instanceof org.bukkit.block.data.type.Cake cake && cake.getBites() == 1, "Native full-hunger cake interaction consumes exactly one slice");
                    check(core.recovery().food(player).active(System.currentTimeMillis()), "Placed cake also grants Well Fed at full hunger");
                    player.setGameMode(GameMode.CREATIVE); player.setFoodLevel(12);
                    later(30, () -> { check(player.getFoodLevel() == 12, "Creative mode retains its own food handling"); finish(); });
                }); });
            });
        });
    }
    private ItemStack chefMeal() {
        var item = Bukkit.getRecipe(new NamespacedKey(core, "meal_orchard_loaf")).getResult().clone();
        item.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey(core, "meal_nutrition"), PersistentDataType.INTEGER, 15));
        return item;
    }
    private void held(ItemStack item) { player.closeInventory(); player.getInventory().setHeldItemSlot(0); player.getInventory().setItem(0, FoodItems.upgrade(item).orElse(item)); }
    private double max() { return player.getAttribute(Attribute.MAX_HEALTH).getValue(); }
    private void injury(double health) { player.setInvulnerable(false); player.setNoDamageTicks(0); player.damage(1); player.setHealth(health); }
    private void hunger() {
        check(player.getFoodLevel() == 20 && player.getSaturation() == 0 && player.getExhaustion() == 0, "Food stays full without saturation or exhaustion in " + player.getWorld().getName() + "/" + player.getGameMode());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void consumption(PlayerItemConsumeEvent event) {
        if (!event.getPlayer().getName().equals("F_Player")) return;
        if (cancelNext) { cancelNext = false; event.setCancelled(true); cancelled++; return; }
        consumes++;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void healing(EntityRegainHealthEvent event) {
        if (event.getEntity() != player || event.getRegainReason() != EntityRegainHealthEvent.RegainReason.CUSTOM) return;
        if (cancelHealing) { event.setCancelled(true); return; }
        heals.add(event.getAmount());
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < .001; }
    private void check(boolean condition, String text) { if (!condition) throw new AssertionError(text); checks.add(text); getLogger().info("PASS " + text); }
    private void finish() throws Exception {
        observations.put("native-consumes", consumes); observations.put("cancelled-consumes", cancelled);
        Files.writeString(Path.of("food-phase" + phase + "-observations.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(observations));
        Files.writeString(Path.of("food-phase" + phase + "-result.txt"), "PASS " + checks.size() + "\n" + String.join("\n", checks)); Bukkit.shutdown();
    }
    private void later(long ticks, Step step) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { step.run(); } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "Food probe failed", failure);
            try { Files.writeString(Path.of("food-phase" + phase + "-result.txt"), "FAIL " + failure + "\n" + String.join("\n", checks)); } catch (Exception ignored) { }
            Bukkit.shutdown();
        } }, ticks);
    }
    @FunctionalInterface private interface Step { void run() throws Exception; }
}

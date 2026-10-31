package net.nerdsmp.paper.command;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.item.SkillLamps;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/**
 * Developer tools for testing: god mode, flight, items, levels, mobs and jumping around a run. They're
 * for ops only by default, and every use is written to the server log. God mode ends when the player
 * leaves; nothing else here outlives the command.
 */
final class DevCommands implements Listener {
    static final String PERMISSION = "nerdsmp.dev";
    private static final Map<String, String> HELP = new java.util.LinkedHashMap<>();
    private static final Map<String, SkillLamps.Grade> BOTTLES = Map.of("lesson", SkillLamps.Grade.PLAIN, "lifetime", SkillLamps.Grade.GREATER,
        "legend", SkillLamps.Grade.GRAND);

    static {
        HELP.put("god", "[player]: take no damage and never go hungry");
        HELP.put("heal", "[player]: full health and food, no fire or effects");
        HELP.put("fly", "[player]: toggle flight");
        HELP.put("speed", "<1-10> [player]: walk and fly speed (1 is normal)");
        HELP.put("item", "<id> [amount] [player]: any item; lesson, lifetime and legend are the bottles");
        HELP.put("gear", "<id> [item levels] [affixes] [player]: a rolled piece of loot");
        HELP.put("charm", "<companion> [player]: a companion charm");
        HELP.put("level", "<skill|all> <level> [player]: set a skill's level, up or down");
        HELP.put("marks", "<amount> [player]: add Marks");
        HELP.put("mob", "<id> [count]: spawn a creature where you stand");
        HELP.put("kill", "[radius]: kill the enemies around you, with credit for the kills");
        HELP.put("goto", "<room>: jump to a room of the expedition you're in");
    }

    static final String USAGE = "/dev <" + String.join("|", HELP.keySet()) + "> [arguments]";

    private final NerdSmpPlugin plugin;
    private final Set<UUID> gods = new HashSet<>();

    DevCommands(NerdSmpPlugin plugin) {
        this.plugin = plugin;
    }

    boolean command(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            Chat.noPermission(sender);
            return true;
        }
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (!HELP.containsKey(action)) {
            sender.sendRichMessage("<gold>Developer commands</gold>");
            HELP.forEach((name, text) -> sender.sendRichMessage("<yellow>/dev " + name + "</yellow> <gray>" + Chat.escape(text) + "</gray>"));
            return true;
        }
        plugin.getLogger().info("[dev] " + sender.getName() + ": /dev " + String.join(" ", args));
        switch (action) {
            case "god" -> god(sender, args);
            case "heal" -> heal(sender, args);
            case "fly" -> fly(sender, args);
            case "speed" -> speed(sender, args);
            case "item" -> item(sender, args);
            case "gear" -> gear(sender, args);
            case "charm" -> charm(sender, args);
            case "level" -> level(sender, args);
            case "marks" -> marks(sender, args);
            case "mob" -> mob(sender, args);
            case "kill" -> kill(sender, args);
            case "goto" -> jump(sender, args);
            default -> { }
        }
        return true;
    }

    /** The player named at {@code index}, or the sender; tells the sender when there's nobody to act on. */
    private Player target(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player named = Bukkit.getPlayerExact(args[index]);
            if (named == null) sender.sendRichMessage("<red>" + Chat.escape(args[index]) + " isn't online.</red>");
            return named;
        }
        if (sender instanceof Player player) return player;
        sender.sendRichMessage("<red>Name a player when using this from the console.</red>");
        return null;
    }

    private static Integer number(String raw) {
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    private void usage(CommandSender sender, String action) {
        Chat.usage(sender, "/dev " + action + " " + HELP.get(action).replaceFirst(":.*", ""));
    }

    private void done(CommandSender sender, Player target, String text) {
        sender.sendRichMessage("<green>" + text + (sender.equals(target) ? "" : " <gray>(" + Chat.escape(target.getName()) + ")</gray>") + "</green>");
    }

    private void god(CommandSender sender, String[] args) {
        Player target = target(sender, args, 1);
        if (target == null) return;
        boolean on = gods.add(target.getUniqueId());
        if (!on) gods.remove(target.getUniqueId());
        done(sender, target, "God mode " + (on ? "on." : "off."));
    }

    /** Runs first, so no damage rule or combat system ever sees the hit. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent event) {
        if (gods.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHunger(FoodLevelChangeEvent event) {
        if (gods.contains(event.getEntity().getUniqueId()) && event.getFoodLevel() < event.getEntity().getFoodLevel()) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gods.remove(event.getPlayer().getUniqueId());
    }

    private void heal(CommandSender sender, String[] args) {
        Player target = target(sender, args, 1);
        if (target == null) return;
        var health = target.getAttribute(Attribute.MAX_HEALTH);
        target.setHealth(health == null ? 20 : health.getValue());
        target.setFoodLevel(20);
        target.setSaturation(20);
        target.setFireTicks(0);
        target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType()));
        done(sender, target, "Healed.");
    }

    private void fly(CommandSender sender, String[] args) {
        Player target = target(sender, args, 1);
        if (target == null) return;
        boolean on = !target.getAllowFlight();
        target.setAllowFlight(on);
        target.setFlying(on);
        done(sender, target, "Flight " + (on ? "on." : "off."));
    }

    private void speed(CommandSender sender, String[] args) {
        Integer speed = args.length > 1 ? number(args[1]) : null;
        Player target = target(sender, args, 2);
        if (target == null) return;
        if (speed == null || speed < 1 || speed > 10) {
            usage(sender, "speed");
            return;
        }
        target.setWalkSpeed(Math.min(1F, .2F + (speed - 1) * .09F));
        target.setFlySpeed(Math.min(1F, .1F * speed));
        done(sender, target, "Speed " + speed + ".");
    }

    private void give(Player target, ItemStack item) {
        target.getInventory().addItem(item).values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        target.playSound(target.getLocation(), Sound.ENTITY_ITEM_PICKUP, .6F, 1.2F);
    }

    private void item(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "item");
            return;
        }
        Integer amount = args.length > 2 ? number(args[2]) : Integer.valueOf(1);
        Player target = target(sender, args, 3);
        if (target == null) return;
        if (amount == null || amount < 1) {
            usage(sender, "item");
            return;
        }
        Optional<ItemStack> made = made(args[1].toLowerCase(Locale.ROOT));
        if (made.isEmpty()) {
            sender.sendRichMessage("<red>No item called " + Chat.escape(args[1]) + ".</red>");
            return;
        }
        for (int left = amount; left > 0; ) {
            ItemStack stack = made.get().clone();
            stack.setAmount(Math.min(left, stack.getMaxStackSize()));
            left -= stack.getAmount();
            give(target, stack);
        }
        done(sender, target, "Gave " + amount + " " + Chat.escape(args[1]) + ".");
    }

    private Optional<ItemStack> made(String id) {
        if (BOTTLES.containsKey(id) && plugin.skillLamps() != null) return Optional.of(plugin.skillLamps().bottle(BOTTLES.get(id), 1));
        if (plugin.items().itemIds().stream().anyMatch(item -> item.value().equals(id))) return Optional.of(plugin.items().create(new ItemId(id)));
        Material material = Material.matchMaterial(id);
        return material == null || !material.isItem() ? Optional.empty() : Optional.of(new ItemStack(material));
    }

    private void gear(CommandSender sender, String[] args) {
        if (args.length < 2 || plugin.items().itemIds().stream().noneMatch(item -> item.value().equals(args[1].toLowerCase(Locale.ROOT)))) {
            usage(sender, "gear");
            return;
        }
        Integer levels = args.length > 2 ? number(args[2]) : Integer.valueOf(0), affixes = args.length > 3 ? number(args[3]) : Integer.valueOf(2);
        Player target = target(sender, args, 4);
        if (target == null) return;
        if (levels == null || affixes == null || levels < 0 || levels > 100 || affixes < 0 || affixes > 5) {
            usage(sender, "gear");
            return;
        }
        give(target, plugin.items().createLoot(new ItemId(args[1].toLowerCase(Locale.ROOT)), affixes, false, ThreadLocalRandom.current().nextLong(), levels));
        done(sender, target, "Gave a rolled " + Chat.escape(args[1]) + ", " + levels + " levels up.");
    }

    private void charm(CommandSender sender, String[] args) {
        if (args.length < 2) {
            usage(sender, "charm");
            return;
        }
        Player target = target(sender, args, 2);
        if (target == null) return;
        Optional<ItemStack> charm = plugin.cosmetics() == null ? Optional.empty() : plugin.cosmetics().charm(args[1]);
        if (charm.isEmpty()) {
            sender.sendRichMessage("<red>No companion called " + Chat.escape(args[1]) + ".</red>");
            return;
        }
        give(target, charm.get());
        done(sender, target, "Gave a charm.");
    }

    private void level(CommandSender sender, String[] args) {
        if (args.length < 3) {
            usage(sender, "level");
            return;
        }
        Integer level = number(args[2]);
        Player target = target(sender, args, 3);
        if (target == null) return;
        List<Skill> skills = new ArrayList<>();
        if (args[1].equalsIgnoreCase("all")) skills.addAll(Arrays.asList(Skill.values()));
        else for (Skill skill : Skill.values()) if (skill.name().equalsIgnoreCase(args[1])) skills.add(skill);
        if (level == null || level < 0 || level > ProgressionService.MAX_LEVEL || skills.isEmpty()) {
            usage(sender, "level");
            return;
        }
        AccountId account = plugin.sessions().account(target.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(sender);
            return;
        }
        long experience = ProgressionService.experienceForLevel(level);
        var work = skills.stream().map(skill -> plugin.progression().setExperience(account, skill, experience).toCompletableFuture())
            .toArray(java.util.concurrent.CompletableFuture[]::new);
        java.util.concurrent.CompletableFuture.allOf(work).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (error != null) {
                sender.sendRichMessage("<red>Couldn't set the level: " + Chat.escape(String.valueOf(error.getMessage())) + "</red>");
                return;
            }
            if (plugin.presentation() != null) plugin.presentation().refresh(target);
            done(sender, target, (skills.size() == 1 ? skills.getFirst().displayName() : "Every skill") + " set to level " + level + ".");
        }));
    }

    private void marks(CommandSender sender, String[] args) {
        Integer amount = args.length > 1 ? number(args[1]) : null;
        Player target = target(sender, args, 2);
        if (target == null) return;
        AccountId account = plugin.sessions().account(target.getUniqueId()).orElse(null);
        if (amount == null || amount < 1 || account == null) {
            usage(sender, "marks");
            return;
        }
        plugin.economy().credit(account, amount, new EconomyService.TransactionReason("admin.credit", sender.getName()), "dev-credit:" + UUID.randomUUID())
            .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> done(sender, target, "Added " + amount + " Marks.")));
    }

    private void mob(CommandSender sender, String[] args) {
        Integer count = args.length > 2 ? number(args[2]) : Integer.valueOf(1);
        if (!(sender instanceof Player player) || args.length < 2 || count == null || count < 1 || count > 25) {
            usage(sender, "mob");
            return;
        }
        try {
            for (int copy = 0; copy < count; copy++) plugin.mobs().spawn(args[1], player.getLocation());
            done(sender, player, "Spawned " + count + " " + Chat.escape(args[1]) + ".");
        } catch (IllegalArgumentException unknown) {
            sender.sendRichMessage("<red>" + Chat.escape(String.valueOf(unknown.getMessage())) + "</red>");
        }
    }

    /** Characters and bosses still on their way in are invulnerable, so they're left alone. */
    private void kill(CommandSender sender, String[] args) {
        Integer radius = args.length > 1 ? number(args[1]) : Integer.valueOf(16);
        if (!(sender instanceof Player player) || radius == null || radius < 1 || radius > 128) {
            usage(sender, "kill");
            return;
        }
        int killed = 0;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Enemy) || !(entity instanceof LivingEntity living) || entity.isInvulnerable() || living.isDead()) continue;
            living.damage(1_000_000D, player);
            killed++;
        }
        done(sender, player, "Killed " + killed + ".");
    }

    private void jump(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || args.length < 2) {
            usage(sender, "goto");
            return;
        }
        if (plugin.expeditions() == null || !plugin.expeditions().jump(player, args[1].toLowerCase(Locale.ROOT)))
            sender.sendRichMessage("<red>That isn't a room of an expedition you're in.</red>");
    }

    List<String> complete(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) return List.copyOf(HELP.keySet());
        String action = args[0].toLowerCase(Locale.ROOT);
        List<String> players = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (args.length == 2) return switch (action) {
            case "god", "heal", "fly" -> players;
            case "item" -> Stream.concat(BOTTLES.keySet().stream(), plugin.items().itemIds().stream().map(ItemId::value)).toList();
            case "gear" -> plugin.items().itemIds().stream().map(ItemId::value).toList();
            case "charm" -> plugin.cosmetics() == null ? List.of() : plugin.cosmetics().catalog().companionSlugs();
            case "level" -> Stream.concat(Stream.of("all"), Arrays.stream(Skill.values()).map(skill -> skill.name().toLowerCase(Locale.ROOT))).toList();
            case "mob" -> List.copyOf(plugin.mobs().ids());
            case "goto" -> sender instanceof Player player && plugin.expeditions() != null ? plugin.expeditions().rooms(player) : List.of();
            default -> List.of();
        };
        boolean lastIsPlayer = switch (action) {
            case "speed", "marks", "charm" -> args.length == 3;
            case "item", "level" -> args.length == 4;
            case "gear" -> args.length == 5;
            default -> false;
        };
        return lastIsPlayer ? players : List.of();
    }
}

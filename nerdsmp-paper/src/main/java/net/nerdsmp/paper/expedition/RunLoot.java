package net.nerdsmp.paper.expedition;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.AdventurerProgression;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.ChestKind;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.LootChest;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.Point;
import net.nerdsmp.paper.expedition.content.ExpeditionLoot;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.item.SkillLamps;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A run's loot: what enemies drop by role, the crates, chests and secret caches every player loots
 * once with their own roll, and the end-of-run reward chests (a free one whose quality comes from
 * the run's grade, and a better bonus chest bought with Marks). Each chest is private: its roll, its
 * window and the glimmer over it belong to one player, and gear and stacks grow with that player's
 * level and the difficulty. Rules live in {@link ExpeditionLoot}.
 */
final class RunLoot {
    /** A chest window: the items the player can take. Bonus windows show a price until bought. */
    static final class Window implements InventoryHolder {
        final RunLoot loot;
        final UUID player;
        final String title;
        final long price;
        boolean paid;
        Inventory inventory;

        Window(RunLoot loot, UUID player, String title, long price) {
            this.loot = loot;
            this.player = player;
            this.title = title;
            this.price = price;
        }

        @Override public Inventory getInventory() { return inventory; }
    }

    static final int BUY_SLOT = 49;
    /** How far under something the floor can be and still be the floor it was standing on. */
    private static final int FLOOR_REACH = 5;
    private static final String BOSS_CHEST = "boss:";

    private final NerdSmpPlugin plugin;
    private final ExpeditionRun run;
    private final ExpeditionLoot.Table table;
    private final double luck;
    /** Chests each player has opened, by chest id. */
    private final Map<String, Set<UUID>> looted = new HashMap<>();
    private final Set<String> secretsFound = new HashSet<>();
    /** End-of-run chests, rolled per player at the finish; removed once taken. */
    private final Map<UUID, List<ItemStack>> rewards = new HashMap<>();
    private final Map<UUID, List<ItemStack>> bonuses = new HashMap<>();
    private long bonusPrice;
    private boolean finished;
    /** Adventurer levels, read as the run starts. */
    private final Map<UUID, Integer> levels = new HashMap<>();
    /** The chests of fallen bosses, and whose. */
    private final Map<Block, ExpeditionDefinition.Room> bossChests = new HashMap<>();

    RunLoot(NerdSmpPlugin plugin, ExpeditionRun run, ExpeditionLoot.Table table, double luck) {
        this.plugin = plugin;
        this.run = run;
        this.table = table;
        this.luck = luck;
    }

    int secretsFound() { return secretsFound.size(); }

    /** Reads a member's adventurer level; rewards scale with it. */
    void readLevel(UUID member) {
        plugin.sessions().account(member).ifPresent(account -> plugin.progression().snapshot(account).whenComplete((snapshot, error) -> {
            if (error == null && snapshot != null) Bukkit.getScheduler().runTask(plugin,
                () -> levels.put(member, AdventurerProgression.fromSkills(snapshot.skills()).level()));
        }));
    }

    /** One player's level, or for shared drops on the ground, the party's. */
    private int level(UUID member) {
        if (member != null) return levels.getOrDefault(member, run.expedition.level());
        return (int) levels.values().stream().mapToInt(Integer::intValue).average().orElse(run.expedition.level());
    }

    private int levelBonus(UUID member) {
        return ExpeditionLoot.itemLevelBonus(run.tier.id(), run.key == null ? 0 : run.key.key().level(), run.expedition.level(), level(member));
    }

    /** A reward stack for this player. */
    int scaled(UUID member, int amount) {
        return ExpeditionLoot.scaled(amount, luck, run.expedition.level(), level(member));
    }

    // ---------------------------------------------------------------------------------------------
    // Items
    // ---------------------------------------------------------------------------------------------

    List<ItemStack> stacks(List<ExpeditionLoot.Drop> drops, UUID owner) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ExpeditionLoot.Drop drop : drops) stack(drop, owner).ifPresent(stacks::add);
        return stacks;
    }

    private Optional<ItemStack> stack(ExpeditionLoot.Drop drop, UUID owner) {
        try {
            return switch (drop.kind()) {
                case LAMP -> {
                    var grade = SkillLamps.Grade.parse(drop.item()).orElse(SkillLamps.Grade.PLAIN);
                    yield Optional.of(plugin.skillLamps() == null ? new ItemStack(Material.EXPERIENCE_BOTTLE, drop.amount())
                        : plugin.skillLamps().bottle(grade, drop.amount()));
                }
                case GEAR -> Optional.of(plugin.items().createLoot(new ItemId(drop.item()), drop.affixes(), drop.unidentified(),
                    ThreadLocalRandom.current().nextLong(), levelBonus(owner)));
                case ITEM -> {
                    ItemStack item = plugin.items().create(new ItemId(drop.item()));
                    item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), scaled(owner, drop.amount()))));
                    yield Optional.of(item);
                }
            };
        } catch (IllegalArgumentException unknown) {
            plugin.getLogger().warning("Expedition loot names an unknown item: " + drop.item());
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Enemies
    // ---------------------------------------------------------------------------------------------

    /** An enemy fell: drop its role's loot where it died. */
    void enemyDied(ExpeditionDefinition.Role role, Location at) {
        List<ItemStack> drops = stacks(ExpeditionLoot.roll(table, source(role), luck, ThreadLocalRandom.current()), null);
        Location spot = reachable(at);
        for (ItemStack drop : drops) spot.getWorld().dropItemNaturally(spot, drop);
        if (role == ExpeditionDefinition.Role.BOSS && !drops.isEmpty())
            at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1, 0), 30, .5, .6, .5, .2);
    }

    /** The floor under a spot, if there is any close enough to count, and it isn't a rail. */
    private static Block floorUnder(Location at) {
        Block block = at.getBlock();
        for (int drop = 0; drop < FLOOR_REACH && block.isPassable(); drop++) block = block.getRelative(org.bukkit.block.BlockFace.DOWN);
        return block.isPassable() || block.getType().name().endsWith("FENCE") ? null : block;
    }

    /** Something that dies over a drop leaves its loot beside the nearest player, not down in the dark. */
    private Location reachable(Location at) {
        if (floorUnder(at) != null) return at;
        return run.online().stream().filter(player -> player.getWorld() == at.getWorld()).map(Player::getLocation)
            .min(java.util.Comparator.comparingDouble(spot -> spot.distanceSquared(at))).orElse(at);
    }

    /** Where a boss's chest goes: where it fell, unless that's off the floor of its room, and then the middle of the room. */
    private Block chestSpot(ExpeditionDefinition.Room room, Location at) {
        Block floor = floorUnder(at);
        boolean inRoom = floor != null && (room.region() == null || room.region().contains(floor.getX() + .5, floor.getY() + 1, floor.getZ() + .5));
        if (!inRoom && !room.points().isEmpty()) {
            Point middle = room.points().getFirst();
            return at.getWorld().getBlockAt(middle.x(), middle.y(), middle.z());
        }
        Block block = floor == null ? at.getBlock() : floor.getRelative(org.bukkit.block.BlockFace.UP);
        while (!block.isPassable() && block.getY() < at.getBlockY() + 3) block = block.getRelative(org.bukkit.block.BlockFace.UP);
        return block;
    }

    /** A boss's chest stands where it fell. Everyone gets their own roll from it, as with any chest. */
    void bossFell(ExpeditionDefinition.Room room, Location at) {
        Block block = chestSpot(room, at);
        block.setType(Material.CHEST);
        bossChests.put(block, room);
        at.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, block.getLocation().add(.5, 1, .5), 30, .5, .6, .5, .2);
        at.getWorld().playSound(block.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.2F, .8F);
        run.broadcast("<gold>A chest stands where the boss fell.</gold> <gray>Each of you has your own share in it.</gray>");
    }

    private void openBossChest(Player player, ExpeditionDefinition.Room room) {
        if (!looted.computeIfAbsent(BOSS_CHEST + room.id(), id -> new HashSet<>()).add(player.getUniqueId())) {
            Chat.notice(player, "You've already taken your share.");
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1F, 1F);
        List<ItemStack> share = stacks(ExpeditionLoot.bossChest(table, room.id(), luck, ThreadLocalRandom.current()), player.getUniqueId());
        companionCharm(player, room).ifPresent(share::add);
        show(player, "Spoils: " + room.title(), share, 0);
    }

    /** Everyone present learns from a kill, not only whoever landed the last hit. */
    void enemyLessons(ExpeditionDefinition.Role role, UUID enemy) {
        if (run.test || plugin.skills() == null) return;
        long experience = ExpeditionLoot.skillExperience(source(role), run.expedition.level(), luck);
        for (Player player : run.online()) plugin.skills().awardMilestone(player, Skill.HUNTING, experience, "expedition:" + run.id + ":" + enemy);
    }

    /** The run's own lesson, paid to one player as it's won. */
    void finishLessons(Player player) {
        if (run.test || plugin.skills() == null) return;
        long experience = ExpeditionLoot.finishExperience(run.expedition.level(), luck);
        plugin.skills().awardMilestone(player, Skill.HUNTING, experience, "expedition-finish:" + run.id);
        // A running booster raises the award as it's stored, so say what actually lands.
        double boost = plugin.boosters() == null ? 1D : plugin.boosters().multiplier();
        Chat.success(player, "You learned from the run: <green>+" + Math.round(experience * boost) + " Hunting XP</green>"
            + (boost > 1D ? " <gold>(XP booster +50%)</gold>" : "") + ".");
    }

    private static ExpeditionLoot.Source source(ExpeditionDefinition.Role role) {
        return switch (role) {
            case BOSS -> ExpeditionLoot.Source.BOSS;
            case MINIBOSS -> ExpeditionLoot.Source.MINIBOSS;
            case ELITE -> ExpeditionLoot.Source.ELITE;
            default -> ExpeditionLoot.Source.NORMAL;
        };
    }

    /** The secret in a boss's chest: each player's own roll, as they open it, at the charm of that boss's companion. */
    private Optional<ItemStack> companionCharm(Player player, ExpeditionDefinition.Room room) {
        var drop = run.expedition.story().companionDrops().get(room.id());
        if (drop == null || run.test || plugin.cosmetics() == null) return Optional.empty();
        if (ThreadLocalRandom.current().nextDouble() >= ExpeditionLoot.rareChance(drop.chance(), luck)) return Optional.empty();
        Optional<ItemStack> charm = plugin.cosmetics().charm(drop.companion());
        if (charm.isEmpty()) {
            plugin.getLogger().warning(run.expedition.id() + " drops an unknown companion: " + drop.companion());
            return charm;
        }
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1F, 1.6F);
        player.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, .5, .8, .5, .3);
        run.broadcast("<gold>✦ " + Chat.escape(player.getName()) + " found something rare:</gold> <white>"
            + Chat.escape(plugin.cosmetics().catalog().companion(drop.companion()).map(c -> c.name()).orElse(drop.companion())) + " Charm</white>");
        return charm;
    }

    // ---------------------------------------------------------------------------------------------
    // Chests
    // ---------------------------------------------------------------------------------------------

    /** A player right-clicked a block: a loot chest or one of the reward chests. */
    boolean interact(Player player, Block block) {
        var spoils = bossChests.get(block);
        if (spoils != null) {
            openBossChest(player, spoils);
            return true;
        }
        for (LootChest chest : run.expedition.story().chests().values())
            if (at(chest.at(), block)) {
                openChest(player, chest, block);
                return true;
            }
        var places = run.expedition.story().rewardChests();
        if (finished && places != null) {
            if (at(places.free(), block)) { openReward(player); return true; }
            if (places.bonus() != null && at(places.bonus(), block)) { openBonus(player); return true; }
        }
        return false;
    }

    private static boolean at(Point point, Block block) {
        return point != null && point.x() == block.getX() && point.y() == block.getY() && point.z() == block.getZ();
    }

    /** Once a second, for each player alone: a glimmer over what they can still open. Secrets show only up close. */
    void glimmer() {
        var places = run.expedition.story().rewardChests();
        for (Player player : run.online()) {
            UUID id = player.getUniqueId();
            for (LootChest chest : run.expedition.story().chests().values()) {
                if (looted.getOrDefault(chest.id(), Set.of()).contains(id)) continue;
                Location spot = spot(chest.at());
                if (chest.kind() == ChestKind.SECRET && player.getLocation().distanceSquared(spot) > 36) continue;
                player.spawnParticle(Particle.WAX_OFF, spot, 6, .22, .2, .22, .4);
            }
            for (var spoils : bossChests.entrySet())
                if (!looted.getOrDefault(BOSS_CHEST + spoils.getValue().id(), Set.of()).contains(id))
                    player.spawnParticle(Particle.WAX_OFF, spoils.getKey().getLocation().add(.5, 1.3, .5), 6, .22, .2, .22, .4);
            if (!finished || places == null) continue;
            if (rewards.containsKey(id)) player.spawnParticle(Particle.END_ROD, spot(places.free()), 5, .22, .2, .22, .01);
            if (places.bonus() != null && bonuses.containsKey(id)) player.spawnParticle(Particle.WITCH, spot(places.bonus()), 6, .22, .2, .22, 0);
        }
    }

    private Location spot(Point at) { return new Location(run.world, at.x() + .5, at.y() + 1.3, at.z() + .5); }

    private void openChest(Player player, LootChest chest, Block block) {
        if (!looted.computeIfAbsent(chest.id(), id -> new HashSet<>()).add(player.getUniqueId())) {
            Chat.notice(player, "You've already emptied this " + chest.title().toLowerCase(java.util.Locale.ROOT) + ".");
            return;
        }
        ExpeditionLoot.Source source = switch (chest.kind()) {
            case CRATE -> ExpeditionLoot.Source.CRATE;
            case CHEST -> ExpeditionLoot.Source.CHEST;
            case SECRET -> ExpeditionLoot.Source.SECRET;
        };
        if (chest.kind() == ChestKind.SECRET && secretsFound.add(chest.id()))
            run.broadcast("<light_purple>✦ " + Chat.escape(player.getName()) + " found a secret:</light_purple> <gray>"
                + Chat.escape(chest.title()) + " <dark_gray>(" + secretsFound.size() + "/" + run.expedition.story().secrets() + ")</dark_gray></gray>");
        player.playSound(block.getLocation(), block.getType() == Material.BARREL ? Sound.BLOCK_BARREL_OPEN : Sound.BLOCK_CHEST_OPEN, 1F, 1F);
        show(player, chest.title(), stacks(ExpeditionLoot.roll(table, source, luck, ThreadLocalRandom.current()), player.getUniqueId()), 0);
    }

    /** Opens a window with these items; whatever's left when it closes goes to the player. */
    private void show(Player player, String title, List<ItemStack> items, long price) {
        Window window = new Window(this, player.getUniqueId(), title, price);
        window.inventory = Bukkit.createInventory(window, price > 0 ? 54 : 27, Chat.render("<dark_gray>" + Chat.escape(title) + "</dark_gray>"));
        for (int index = 0; index < items.size() && index < 27; index++) window.inventory.setItem(index, items.get(index));
        if (plugin.gearUpgrades() != null) plugin.gearUpgrades().mark(player, window.inventory);
        if (price > 0) {
            window.inventory.setItem(BUY_SLOT, MenuKit.item(Material.GOLD_INGOT, "<gold>Open for " + price + " Marks</gold>", List.of(
                "<gray>The bonus chest holds better odds and always</gray>", "<gray>a piece of set gear. Pay to take its contents.</gray>")));
        }
        player.openInventory(window.inventory);
    }

    /** The window closed: anything still in it (except the price button) goes to the player. */
    void closed(Player player, Window window) {
        if (window.price > 0 && !window.paid) return; // An unbought bonus chest stays on offer.
        List<ItemStack> left = new ArrayList<>();
        for (int index = 0; index < Math.min(27, window.inventory.getSize()); index++) {
            ItemStack item = window.inventory.getItem(index);
            if (item != null && !item.getType().isAir()) left.add(item);
        }
        window.inventory.clear();
        if (left.isEmpty()) return;
        run.give(player, left);
    }

    /** A click in a bonus window's price button: pay, and the chest's items become takeable. */
    void buy(Player player, Window window) {
        if (window.paid || window.price <= 0) return;
        Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        if (account.isEmpty() || run.test) {
            if (run.test) {
                window.paid = true;
                window.inventory.setItem(BUY_SLOT, null);
                bonuses.remove(player.getUniqueId());
                Chat.notice(player, "Test run: the bonus chest is free.");
            }
            return;
        }
        plugin.economy().debit(account.get(), window.price, new EconomyService.TransactionReason("expedition.bonus-chest", run.expedition.id()),
            "expedition-bonus:" + run.id + ":" + account.get()).thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.status() == EconomyService.TransactionResult.Status.INSUFFICIENT_FUNDS) {
                    Chat.error(player, "You need " + window.price + " Marks to open the bonus chest.");
                    return;
                }
                if (result.status() != EconomyService.TransactionResult.Status.APPLIED
                    && result.status() != EconomyService.TransactionResult.Status.DUPLICATE) return;
                window.paid = true;
                window.inventory.setItem(BUY_SLOT, null);
                bonuses.remove(player.getUniqueId());
                player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1F, 1.2F);
                Chat.success(player, "Bonus chest opened for " + Chat.marks(window.price) + ". Take what's inside.");
            }));
    }

    // ---------------------------------------------------------------------------------------------
    // Reward chests
    // ---------------------------------------------------------------------------------------------

    /**
     * The run is won: roll each player's free and bonus chests from the grade, add the expedition's
     * fixed rewards to the free chest, and place the chests. {@code fixed} is per player.
     */
    void finish(Map<UUID, List<ItemStack>> fixed, ExpeditionLoot.Grade grade, Set<UUID> lockedOut, long price) {
        finished = true;
        bonusPrice = price;
        Random random = ThreadLocalRandom.current();
        for (var entry : fixed.entrySet()) {
            if (lockedOut.contains(entry.getKey())) continue;
            List<ItemStack> free = new ArrayList<>(entry.getValue());
            free.addAll(stacks(ExpeditionLoot.chest(table, false, grade, luck, random), entry.getKey()));
            rewards.put(entry.getKey(), free);
            bonuses.put(entry.getKey(), stacks(ExpeditionLoot.chest(table, true, grade, luck, random), entry.getKey()));
        }
        var places = run.expedition.story().rewardChests();
        if (places != null) {
            place(places.free(), Material.CHEST);
            if (places.bonus() != null) place(places.bonus(), Material.ENDER_CHEST);
        }
        for (UUID member : rewards.keySet()) {
            Player player = Bukkit.getPlayer(member);
            if (player == null) continue;
            Chat.send(player, "<gold>Your reward chest is ready.</gold> <green><click:run_command:'/expedition rewards'><hover:show_text:'Open your free reward chest'>[Open reward chest]</hover></click></green>"
                + "  <light_purple><click:run_command:'/expedition bonus'><hover:show_text:'Better odds and a piece of set gear, for Marks'>[Bonus chest: " + price + " Marks]</hover></click></light_purple>");
        }
    }

    private void place(Point point, Material material) {
        Block block = run.world.getBlockAt(point.x(), point.y(), point.z());
        block.setType(material);
        run.world.spawnParticle(Particle.END_ROD, block.getLocation().add(.5, 1, .5), 20, .3, .5, .3, .02);
        run.world.playSound(block.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1F, .8F);
    }

    void openReward(Player player) {
        List<ItemStack> items = rewards.remove(player.getUniqueId());
        if (items == null) {
            Chat.notice(player, finished ? "You've already taken your reward chest." : "The reward chest opens when the expedition is won.");
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1F, 1F);
        show(player, "Reward Chest", items, 0);
    }

    void openBonus(Player player) {
        List<ItemStack> items = bonuses.get(player.getUniqueId());
        if (items == null) {
            Chat.notice(player, finished ? "There's no bonus chest left for you." : "The bonus chest opens when the expedition is won.");
            return;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1F, 1F);
        show(player, "Bonus Chest", items, bonusPrice);
    }

    /** Where a player still has a chest to open, or null: nobody should walk out past their own loot unwarned. */
    String owedAt(UUID player) {
        if (rewards.containsKey(player)) return "your reward chest";
        for (ExpeditionDefinition.Room room : bossChests.values())
            if (!looted.getOrDefault(BOSS_CHEST + room.id(), Set.of()).contains(player)) return "the boss chest in " + room.title();
        return null;
    }

    /** At the close, an untaken free chest goes with the player rather than being lost. */
    List<ItemStack> unclaimed(UUID player) {
        List<ItemStack> items = rewards.remove(player);
        return items == null ? List.of() : items;
    }
}

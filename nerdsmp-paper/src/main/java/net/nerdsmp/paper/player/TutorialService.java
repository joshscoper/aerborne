package net.nerdsmp.paper.player;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;
import java.util.UUID;

public final class TutorialService implements Listener {
    private final NerdSmpPlugin plugin;
    private final TutorialProgress progress;
    public TutorialService(NerdSmpPlugin plugin) { this.plugin = plugin; progress = new TutorialProgress(plugin.playerData()); }
    public TutorialProgress progress() { return progress; }

    public boolean handle(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        AccountId actor = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (actor == null) { Chat.accountLoading(player); return true; }
        try {
            if (args.length > 0 && args[0].equalsIgnoreCase("pause")) {
                progress.pause(actor, true); player.closeInventory(); Chat.notice(player, "Tutorial paused. " + Chat.command("tutorial resume") + " continues where you left off.");
            } else if (args.length > 0 && args[0].equalsIgnoreCase("restart")) {
                MenuKit.openConfirm(plugin, player, "Restart tutorial?", MenuKit.item(Material.BOOK, "<aqua>Restart the guide</aqua>", List.of("<gray>Reset only your read checkpoints.</gray>")), List.of("<gray>Your character and rewards are unaffected.</gray>"),
                    p -> { if (same(p, actor)) safely(p, () -> { progress.restart(actor); open(p, actor, 0); }); }, p -> { if (same(p, actor)) open(p, actor, progress.state(actor).next()); });
            } else {
                if (args.length > 0 && args[0].equalsIgnoreCase("resume")) progress.pause(actor, false);
                int page = progress.state(actor).next();
                if (args.length > 0 && !args[0].equalsIgnoreCase("resume")) {
                    try { page = Integer.parseInt(args[0]) - 1; } catch (NumberFormatException bad) { throw new IllegalArgumentException("Use /tutorial [1–8|pause|resume|restart]."); }
                    if (page < 0 || page >= TutorialProgress.LESSONS.size()) throw new IllegalArgumentException("Choose a tutorial lesson from 1 to 8.");
                }
                open(player, actor, page);
            }
        } catch (IllegalArgumentException failure) { Chat.error(player, Chat.escape(failure.getMessage())); }
        catch (IllegalStateException failure) { failed(player, failure); }
        return true;
    }

    public void ready(Player player) {
        AccountId actor = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (actor == null || !plugin.getConfig().getBoolean("tutorial.invite-on-first-login", true)) return;
        safely(player, () -> {
            var state = progress.state(actor);
            if (!state.invited() && !state.paused() && !state.complete()) {
                progress.invite(actor);
                Chat.info(player, "<aqua>New to Aerborne?</aqua> " + Chat.command("tutorial") + " opens eight short lessons. Read at your pace; your place is saved.");
            }
        });
    }

    private void open(Player player, AccountId actor, int page) {
        if (!same(player, actor)) return;
        var state = progress.state(actor);
        List<Lesson> lessons = lessons(); Lesson lesson = lessons.get(page);
        Page holder = new Page(player.getUniqueId(), actor, page);
        holder.inventory = Bukkit.createInventory(holder, 54, Chat.render("<aqua>Tutorial · " + (page + 1) + "/8</aqua>"));
        MenuKit.frame(holder.inventory);
        holder.inventory.setItem(4, MenuKit.item(Material.BOOK, "<aqua>Your Aerborne Guide</aqua>", List.of("<gray>Read " + state.read().size() + "/8 lessons · " + (state.complete() ? "Guide complete" : "Progress saved") + "</gray>", "<gray>Try each feature, then return with /tutorial.</gray>", "<gray>Reading marks a lesson done; no rewards are spent.</gray>")));
        for (int i = 0; i < lessons.size(); i++) {
            Lesson choice = lessons.get(i);
            holder.inventory.setItem(10 + i, MenuKit.item(choice.icon(), (page == i ? "<yellow>" : "<aqua>") + (i + 1) + ". " + choice.title() + (page == i ? "</yellow>" : "</aqua>"), List.of(state.read().contains(TutorialProgress.LESSONS.get(i)) ? "<green>Read</green>" : "<gray>Ready to read</gray>", "<yellow>Click to review.</yellow>")));
        }
        holder.inventory.setItem(22, MenuKit.item(lesson.icon(), "<gold>" + lesson.title() + "</gold>", lesson.lines().stream().map(s -> "<gray>" + s + "</gray>").toList()));
        for (int i = 0; i < lesson.actions().size(); i++) {
            Action action = lesson.actions().get(i);
            holder.inventory.setItem(29 + i * 2, MenuKit.item(action.icon(), "<aqua>" + action.label() + "</aqua>", List.of("<gray>/" + Chat.escape(action.command()) + "</gray>", "<yellow>Click to try it. Return with /tutorial.</yellow>")));
        }
        holder.inventory.setItem(45, MenuKit.item(Material.ARROW, "<yellow>Previous lesson</yellow>", List.of()));
        holder.inventory.setItem(48, MenuKit.item(Material.CLOCK, "<yellow>Pause guide</yellow>", List.of("<gray>Keep your progress for later.</gray>")));
        holder.inventory.setItem(49, MenuKit.close());
        holder.inventory.setItem(53, MenuKit.item(Material.LIME_DYE, page == 7 ? "<green>I've read this · Finish</green>" : "<green>I've read this · Next</green>", List.of("<gray>Save this read checkpoint.</gray>")));
        player.openInventory(holder.inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Page page)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !page.viewer.equals(player.getUniqueId()) || !same(player, page.actor) || !event.isLeftClick()) return;
        int slot = event.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getOpenInventory().getTopInventory() != page.inventory || !same(player, page.actor)) return;
            safely(player, () -> {
                if (slot >= 10 && slot <= 17) open(player, page.actor, slot - 10);
                else if (slot == 45) open(player, page.actor, Math.max(0, page.lesson - 1));
                else if (slot == 48) { progress.pause(page.actor, true); player.closeInventory(); Chat.notice(player, "Guide paused. Resume with " + Chat.command("tutorial") + "."); }
                else if (slot == 49) player.closeInventory();
                else if (slot == 53) {
                    progress.acknowledge(page.actor, TutorialProgress.LESSONS.get(page.lesson));
                    if (progress.state(page.actor).complete()) { player.closeInventory(); Chat.success(player, "Guide complete. Revisit any lesson with " + Chat.command("tutorial") + ". Welcome to Aerborne!"); }
                    else open(player, page.actor, page.lesson == 7 ? progress.state(page.actor).next() : page.lesson + 1);
                } else if (slot >= 29 && slot <= 33 && slot % 2 == 1) {
                    int index = (slot - 29) / 2; var actions = lessons().get(page.lesson).actions();
                    if (index < actions.size()) { player.closeInventory(); player.performCommand(actions.get(index).command()); }
                }
            });
        });
    }
    @EventHandler(priority = EventPriority.LOWEST) public void drag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Page) event.setCancelled(true); }

    private List<Lesson> lessons() {
        boolean sky = plugin.skyblockEnabled();
        return List.of(
            new Lesson("Welcome & navigation", Material.COMPASS, List.of("Aerborne is an RPG: train through play,", "build a home, and adventure with friends.", "Use /menu as your hub and /quickstart", "for a compact command reference.", "Hunger stays full. Health recovers after combat.", "Eat for Well Fed and Cooking buffs, even at full health.", "This guide saves the lessons you mark read.", "You can explore its tabs in any order."), List.of(new Action("Open the hub", Material.COMPASS, "menu"), new Action("Your settings", Material.COMPARATOR, "settings"))),
            new Lesson(sky ? "Your island & co-op" : "Protect your home", Material.GRASS_BLOCK, sky ? List.of("Create a home with /island create, or join", "a trusted friend's invite. /island home returns.", "Mine stone beside the lava for your generator.", "Right-click ripe crops with a hoe to replant.", "Members share building and storage access.", "Visitors can sightsee without building.", "/island homes lists all your co-op homes.") : List.of("Use /claim create to protect your chunk.", "Review access before inviting other players.", "/claim directory finds nearby settlements.", "Applications need the owner's approval.", "Residential plots keep residents' homes private."), List.of(new Action(sky ? "Island controls" : "Homestead controls", Material.GRASS_BLOCK, sky ? "island" : "claim"))),
            new Lesson("Train your skills", Material.EXPERIENCE_BOTTLE, List.of("Mine, chop, farm, fish, dig, fight, and craft", "to earn XP in their matching skills.", "Generated ores and regrown trees give XP.", "Placed ore and ordinary placed soil do not.", "Use fresh soil or /digsite list for excavation.", "Open /skills to see levels and talent points.", "Choose up to two masteries from level 25."), List.of(new Action("View all skills", Material.EXPERIENCE_BOTTLE, "skills"), new Action("Gathering guide", Material.FISHING_ROD, "fishing"))),
            new Lesson("Equipment that works with E", Material.IRON_CHESTPLATE, List.of("Equip armor and weapons the usual ways.", "Shift-click an accessory in your E inventory", "to equip it. Filled paired slots offer a choice.", "Sneak + F or /equipment opens all 16 slots.", "Compare stats before replacing gear; F closes.", "Necklaces, rings, trinkets and other accessories", "add bonuses alongside your worn armor.", "Use /recipes to find craftable equipment."), List.of(new Action("Your equipment", Material.IRON_CHESTPLATE, "equipment"), new Action("Equipment recipes", Material.CRAFTING_TABLE, "recipes"))),
            new Lesson("Three kinds of talent trees", Material.OAK_SAPLING, List.of("Each profession has its own branching tree.", "Adventurer talents improve your whole journey.", "Follow connected nodes and read requirements", "before spending. Previewing never spends points.", sky ? "Your island earns one shared point every two" : "Train several skills to grow your Adventurer level.", sky ? "levels; its owner confirms co-op talent choices." : "Adventurer and profession points are separate.", "Build a role that complements your friends."), sky ? List.of(new Action("Skill trees", Material.EXPERIENCE_BOTTLE, "skills"), new Action("Adventurer tree", Material.NETHER_STAR, "adventurer"), new Action("Island tree", Material.OAK_SAPLING, "island talents")) : List.of(new Action("Skill trees", Material.EXPERIENCE_BOTTLE, "skills"), new Action("Adventurer tree", Material.NETHER_STAR, "adventurer"))),
            new Lesson("Marks, jobs & trading", Material.GOLD_INGOT, List.of("Daily contracts and jobs are reliable income.", "Shops show prices before you confirm a trade.", "Use /trade for player exchanges: review both", "offers before confirming. Never drop valuables", "as payment. /pay sends Marks directly.", "The auction house and player stores offer", "more ways to buy and sell. /bank stores gear."), List.of(new Action("Daily contracts", Material.CLOCK, "daily"), new Action("Find work", Material.WRITABLE_BOOK, "jobs"), new Action("Browse shops", Material.CHEST, "shops"))),
            new Lesson("Your next adventure", Material.ENDER_EYE, List.of(sky ? "/island journal shows chapter and ore unlocks." : "/journal tracks your quests and next objectives.", sky ? "/island machines explains processing recipes." : "Follow quest directions to meet city specialists.", "Train together, then take up to five players", "on private expeditions with bosses and loot.", "Expedition deaths keep your gear but spend", "a shared party life. Read the entry requirements.", "Death keeps inventory and XP in every world.", "In adventure worlds, release as a spirit.", "Walk to your body or use the Spirit Healer.", "Visit city stations for forging and alchemy.", "/spawn returns you to the city."), List.of(new Action("Your journal", Material.BOOK, sky ? "island journal" : "journal"), new Action("Expedition guide", Material.ENDER_EYE, "expedition"))),
            new Lesson("Community, website & help", Material.BELL, List.of("Visit www.aerborne.net with /website.", "Use /settings for privacy and /tips off to", "mute rotating tips, including website notices.", "Describe your problem with /helpme to save", "a staff request. Staff respond privately.", "Replies are saved offline; staff can review", "transcripts. /support chat selects private chat.", "/support leave restores your previous channel.", "Use /report for player misconduct."), List.of(new Action("Aerborne website", Material.PAPER, "website"), new Action("Support instructions", Material.BELL, "support help"), new Action("Privacy settings", Material.COMPARATOR, "settings")))
        );
    }
    private boolean same(Player player, AccountId actor) { return player.isOnline() && plugin.sessions().account(player.getUniqueId()).filter(actor::equals).isPresent(); }
    private void safely(Player player, Runnable action) { try { action.run(); } catch (IllegalStateException failure) { failed(player, failure); } }
    private void failed(Player player, RuntimeException failure) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Tutorial checkpoint could not be saved", failure); Chat.error(player, "Your tutorial progress could not be saved. Please try again."); }
    private record Lesson(String title, Material icon, List<String> lines, List<Action> actions) { }
    private record Action(String label, Material icon, String command) { }
    private static final class Page implements InventoryHolder {
        final UUID viewer; final AccountId actor; final int lesson; Inventory inventory;
        Page(UUID viewer, AccountId actor, int lesson) { this.viewer = viewer; this.actor = actor; this.lesson = lesson; }
        @Override public Inventory getInventory() { return inventory; }
    }
}

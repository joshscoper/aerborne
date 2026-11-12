package net.nerdsmp.paper.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.service.InMemoryClaimService;
import net.nerdsmp.paper.skyblock.machine.IslandMachines;
import net.nerdsmp.paper.skyblock.machine.MachineKind;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.scheduler.BukkitTask;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Island UI and Paper lifecycle. Domain mutations remain in IslandService. */
public final class SkyblockController implements Listener, AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final IslandService islands;
    private final World world;
    private final Set<UUID> travelling = new HashSet<>();
    private final Set<UUID> advancing = new HashSet<>();
    private final BukkitTask accessTask;
    private final IslandPresentation presentation;
    private final IslandDevelopment development;
    private final BukkitTask developmentTask;
    private final IslandAutomation automation;
    private final IslandResourceProgression resources;
    private final IslandMachines processingMachines;
    private final IslandBorders borders;
    private final IslandPerks perks;
    private final IslandGuests guests;
    private final IslandFarmstead farmstead;
    private final FarmingContests contests;
    private final IslandWorkers workers;
    private final IslandRanking ranking;
    private final String season;
    private final Set<UUID> claiming = new HashSet<>();

    public SkyblockController(NerdSmpPlugin plugin) throws Exception {
        this.plugin = plugin;
        season = plugin.getConfig().getString("season.id", "season-1");
        islands = new IslandService(plugin.playerData(), season);
        development = new IslandDevelopment(plugin.playerData(), islands, season, () -> java.time.LocalDate.now(java.time.ZoneOffset.UTC));
        // The double XP weekend and XP boosters raise island XP like skill XP.
        development.xpMultiplier(() -> plugin.boosters() == null ? 1D : plugin.boosters().multiplier());
        development.onLevelUp(this::leveled);
        resources = new IslandResourceProgression(plugin.playerData(), islands, development, season);
        world = IslandWorld.open(plugin, season);
        ((InMemoryClaimService) plugin.claims()).setTerritoryOverlay(new IslandTerritories(world.getUID(), islands));
        accessTask = Bukkit.getScheduler().runTaskTimer(plugin, this::recheckVisitors, 20, 20);
        presentation = new IslandPresentation(plugin, islands, world);
        borders = new IslandBorders(plugin, islands, world);
        automation = new IslandAutomation(plugin, this);
        Bukkit.getPluginManager().registerEvents(automation, plugin);
        processingMachines = new IslandMachines(plugin, this, resources::effectiveStage);
        processingMachines.start();
        Bukkit.getPluginManager().registerEvents(processingMachines, plugin);
        perks = new IslandPerks(plugin, this);
        farmstead = new IslandFarmstead(plugin, this, season);
        Bukkit.getPluginManager().registerEvents(farmstead, plugin);
        contests = new FarmingContests(plugin, this);
        Bukkit.getPluginManager().registerEvents(contests, plugin);
        contests.start();
        guests = new IslandGuests(plugin, this, season);
        Bukkit.getPluginManager().registerEvents(guests, plugin);
        guests.start();
        workers = new IslandWorkers(plugin, this, season);
        Bukkit.getPluginManager().registerEvents(workers, plugin);
        ranking = new IslandRanking(plugin, this, season);
        Bukkit.getPluginManager().registerEvents(ranking, plugin);
        ranking.start();
        Bukkit.getPluginManager().registerEvents(perks, plugin);
        developmentTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try { development.flush(); }
            catch (RuntimeException error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Island XP flush failed; buffered work retained for retry", error); }
        }, 200, 200);
    }
    public World world() { return world; }
    public IslandService islands() { return islands; }
    public IslandDevelopment development() { return development; }
    public IslandAutomation automation() { return automation; }
    public IslandResourceProgression resources() { return resources; }
    public IslandMachines processingMachines() { return processingMachines; }
    public IslandBorders borders() { return borders; }
    public IslandPerks perks() { return perks; }
    public IslandGuests guests() { return guests; }
    public IslandFarmstead farmstead() { return farmstead; }
    /** Crop milestones live in the Farmstead. */
    public IslandFarmstead crops() { return farmstead; }
    public FarmingContests contests() { return contests; }
    public IslandWorkers workers() { return workers; }
    public IslandRanking ranking() { return ranking; }

    /**
     * What this island has done toward its showcases: chapter, level and stage, plus the best achievement score,
     * the quest chains finished and the expeditions cleared by anyone in the co-op, and visitors served.
     */
    public IslandShowcase.Context showcaseContext(AccountId owner) {
        IslandService.Island island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        List<AccountId> crew = new ArrayList<>();
        crew.add(island.owner());
        crew.addAll(island.members());
        int points = 0, bossKills = 0;
        Set<String> chains = new HashSet<>(), cleared = new HashSet<>();
        for (AccountId member : crew) {
            try { if (plugin.achievements() != null) points = Math.max(points, plugin.achievements().points(member)); }
            catch (RuntimeException unreadable) { plugin.getLogger().log(java.util.logging.Level.FINE, "Achievements unreadable for " + member, unreadable); }
            if (plugin.quests() != null) chains.addAll(plugin.quests().completedChains(member));
            bossKills = Math.max(bossKills, plugin.playerData().read(member, yaml -> yaml.getInt(net.nerdsmp.paper.reach.WorldBossService.KILLS, 0)));
            if (plugin.expeditions() != null) for (IslandShowcase showcase : IslandShowcase.values())
                if (showcase.kind() == IslandShowcase.Kind.EXPEDITION && plugin.expeditions().clears(member, showcase.key()) > 0) cleared.add(showcase.key());
        }
        return new IslandShowcase.Context(island.tier(), development.progress(owner).level(), resources.effectiveStage(owner),
            points, chains, cleared, guests.served(owner), workers.unlocks(owner), bossKills, development.stars(owner));
    }
    public AccountId activityIsland(Player player) {
        if (!isIslandWorld(player.getWorld()) || player.getGameMode() != GameMode.SURVIVAL) return null;
        return islands.at(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4)
            .filter(i -> i.member(account(player))).map(IslandService.Island::owner).orElse(null);
    }
    public boolean isIslandWorld(World candidate) { return world.equals(candidate); }
    public Location graveLocation(Location original) {
        if (!landingBlock(original.getBlock())) return original;
        int slot = IslandGrid.slotAt(original.getBlockX() >> 4, original.getBlockZ() >> 4);
        return new Location(world, IslandGrid.blockX(slot) + 3, IslandGrid.SURFACE_Y + 1, IslandGrid.blockZ(slot));
    }
    public void ready(Player player) {
        automation.discover(player);
        processingMachines.discover(player);
        if(isIslandWorld(player.getWorld())) islands.owned(account(player)).ifPresent(i->StarterGeneratorRepair.repair(world,i.slot()));
        if (isIslandWorld(player.getWorld()) && !canEnter(player, player.getLocation())) returnToHub(player);
    }
    /**
     * Every subcommand players see, in help order. The usage line and tab completion are built
     * from this list. Hidden aliases ({@code go}, {@code generator}, {@code borders},
     * {@code gallery}, {@code decline}, {@code cancel}) still work but are not suggested.
     */
    static final List<String> SUBCOMMANDS = List.of("create", "home", "homes", "visit", "directory", "journal", "rewards", "guests", "stall", "farm", "composter", "crops", "contest", "workers", "top", "resources",
        "level", "talents", "machines", "border", "chat", "showcase", "advance", "invite", "invitations", "accept", "deny",
        "kick", "leave", "visitors", "rename", "fixgenerator", "hub");
    static String usage() { return "/island [" + String.join("|", SUBCOMMANDS) + "]"; }
    /** A refusal that is best answered with the command's usage line. */
    private static final class UsageException extends IllegalArgumentException {
        @java.io.Serial private static final long serialVersionUID = 1L;
        private UsageException(String syntax) { super(syntax); }
    }
    public boolean handle(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("performance")) {
            if (!sender.hasPermission("nerdsmp.admin")) { Chat.noPermission(sender); return true; }
            Chat.info(sender, Chat.escape(presentation.diagnostics() + "\n" + automation.diagnostics()
                + "\n" + processingMachines.diagnostics() + "\n" + borders.diagnostics()));
            return true;
        }
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        AccountId account = account(player);
        if (account == null) { Chat.accountLoading(player); return true; }
        try {
            String action = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            switch (action) {
                case "menu" -> open(player, islands.owned(account).orElse(null));
                case "create" -> { islands.create(account); home(player, account); }
                case "home", "go" -> home(player, args.length > 1 ? resolve(args[1]) : account);
                case "visit" -> { requireArgs(args, "/island visit <player>"); home(player, resolve(args[1])); }
                case "homes" -> list(player, islands.homes(account), "Your islands");
                case "directory" -> list(player, islands.directory(), "Open islands");
                case "journal" -> journal(player, args.length > 1 ? resolve(args[1]) : account);
                case "resources", "generator" -> resourceJournal(player, args.length > 1 ? resolve(args[1]) : account);
                case "rewards", "milestones" -> milestones(player, args.length > 1 ? resolve(args[1]) : account);
                case "guests", "visitors-board" -> guests.open(player, args.length > 1 ? resolve(args[1]) : account);
                case "stall", "favour" -> guests.openStall(player, ownIsland(account));
                case "visitorlog" -> guests.openLog(player, ownIsland(account));
                case "farm", "farmstead" -> farmstead.openHub(player, ownIsland(account));
                case "composter", "compost" -> farmstead.openComposter(player, ownIsland(account));
                case "crops", "cropmilestones" -> farmstead.openCrops(player, ownIsland(account));
                case "contest", "contests" -> {
                    if (args.length > 1 && player.hasPermission("nerdsmp.admin") && args[1].equalsIgnoreCase("start")) {
                        java.time.Instant now = java.time.Instant.now();
                        contests.begin(now, now.plus(java.time.Duration.ofMinutes(args.length > 2 ? Integer.parseInt(args[2]) : 20)));
                    } else if (args.length > 1 && player.hasPermission("nerdsmp.admin") && args[1].equalsIgnoreCase("stop")) contests.stop();
                    else contests.open(player, ownIsland(account));
                }
                case "workers", "lodge" -> workers.open(player, args.length > 1 ? resolve(args[1]) : account);
                case "top", "rankings", "leaderboard" -> ranking.open(player, args.length > 1 && args[1].equalsIgnoreCase("season") ? false : true);
                case "level", "talents" -> talents(player, args.length > 1 ? resolve(args[1]) : account);
                case "machines" -> machines(player, args.length > 1 ? resolve(args[1]) : account);
                case "border", "borders" -> {
                    if (args.length > 2 || args.length == 2 && !Set.of("on", "off").contains(args[1].toLowerCase(Locale.ROOT)))
                        throw new UsageException("/island border [on|off]");
                    if (args.length == 2 && args[1].equalsIgnoreCase("off")) { borders.forget(player); Chat.info(player, "Island outline hidden."); break; }
                    IslandService.Island target = isIslandWorld(player.getWorld())
                        ? islands.at(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4).orElse(null) : null;
                    if (target == null) throw new IllegalArgumentException("Stand on an island to show its outline.");
                    if (args.length == 1) borders.toggle(player, target.owner()); else borders.set(player, target.owner(), true);
                }
                case "chat" -> plugin.chat().handleIsland(sender, Arrays.copyOfRange(args, 1, args.length));
                case "showcase", "gallery" -> {
                    if (args.length > 1) display(account, IslandShowcase.parse(args[1].toLowerCase(Locale.ROOT)));
                    gallery(player);
                }
                case "performance" -> {
                    if (!player.hasPermission("nerdsmp.admin")) { Chat.noPermission(player); break; }
                    Chat.info(player, Chat.escape(presentation.diagnostics()));
                }
                case "advance" -> advance(player, args.length > 1 ? resolve(args[1]) : account);
                case "rename" -> {
                    requireArgs(args, "/island rename <name>");
                    islands.rename(account, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                    Chat.success(player, "Island renamed."); open(player, islands.owned(account).orElseThrow());
                }
                case "fixgenerator" -> {
                    IslandService.Island own=islands.owned(account).orElseThrow(()->new IllegalArgumentException("Create an island first."));
                    if(!isIslandWorld(player.getWorld()) || !own.owner().equals(activityIsland(player)) && !player.hasPermission("nerdsmp.admin")) {
                        Chat.error(player,"Stand on your own island to check its starter generator."); break;
                    }
                    if (StarterGeneratorRepair.repair(world,own.slot())) Chat.success(player, "The starter generator's water outlet is repaired.");
                    else Chat.info(player, "No unchanged starter generator needs this repair. Your blocks were left alone.");
                }
                case "visitors" -> {
                    if (args.length != 2 || !(args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off"))) throw new UsageException("/island visitors <on|off>");
                    boolean welcome = args[1].equalsIgnoreCase("on");
                    islands.visitors(account, welcome); recheckVisitors();
                    Chat.success(player, welcome ? "Visitors are welcome on your island." : "Your island is closed to visitors.");
                    open(player, islands.owned(account).orElseThrow());
                }
                case "invite" -> {
                    requireArgs(args, "/island invite <player>"); AccountId target = resolve(args[1]);
                    Player invited = plugin.sessions().player(target).map(Bukkit::getPlayer).orElse(null);
                    if (!plugin.social().canContact(player, invited, net.nerdsmp.paper.player.PlayerSettingsService.Setting.COOP_INVITES))
                        throw new IllegalArgumentException("That player is not accepting co-op invitations from you.");
                    islands.invite(account, target, Instant.now());
                    Chat.success(player, "Invitation sent to <white>" + Chat.escape(name(target)) + "</white>. It expires in five minutes.");
                    for (Player online : Bukkit.getOnlinePlayers()) if (target.equals(account(online)))
                        Chat.notice(online, "<white>" + Chat.escape(player.getName()) + "</white> invited you to their island co-op. "
                            + Chat.command("island accept " + player.getName()) + " <gray>or</gray> " + Chat.command("island deny " + player.getName()));
                }
                case "invitations" -> list(player, islands.invitations(account, Instant.now()), "Island invitations");
                case "accept" -> {
                    requireArgs(args, "/island accept <player>"); AccountId owner = resolve(args[1]);
                    if (!plugin.settings().enabled(player.getUniqueId(), net.nerdsmp.paper.player.PlayerSettingsService.Setting.COOP_INVITES)
                        || plugin.social().blocked(account, owner)) throw new IllegalArgumentException("That co-op invitation is no longer available. Check your settings and ignored players.");
                    islands.accept(account, owner, Instant.now()); Chat.success(player, "You joined the co-op. Your own island and items stay yours."); open(player, islands.owned(owner).orElseThrow());
                }
                case "deny", "decline" -> {
                    requireArgs(args, "/island deny <player>"); AccountId owner = resolve(args[1]);
                    if (islands.invitations(account, Instant.now()).stream().noneMatch(island -> island.owner().equals(owner)))
                        throw new IllegalArgumentException("You have no invitation from that island.");
                    islands.remove(account, owner, account); recheckVisitors(); Chat.notice(player, "Invitation denied.");
                }
                case "kick", "cancel" -> { requireArgs(args, "/island kick <player>"); islands.remove(account, account, resolve(args[1])); recheckVisitors(); Chat.success(player, "Co-op access and any invitation have been removed."); }
                case "leave" -> { requireArgs(args, "/island leave <owner>"); islands.remove(account, resolve(args[1]), account); recheckVisitors(); Chat.success(player, "You no longer belong to that co-op or hold its invitation."); }
                case "hub" -> plugin.travel().handle("spawn", sender, new String[0]);
                case "reset" -> {
                    if (!player.hasPermission("nerdsmp.admin")) { Chat.noPermission(player); break; }
                    requireArgs(args, "/island reset <player>");
                    AccountId target = resolve(args[1]);
                    int cleared = islands.reset(target);
                    recheckVisitors();
                    Player online = Bukkit.getOnlinePlayers().stream().filter(p -> target.equals(account(p))).findFirst().orElse(null);
                    if (online != null && isIslandWorld(online.getWorld())) returnToHub(online);
                    if (online != null) Chat.notice(online, "Staff reset your island. Use /island to start a fresh one.");
                    Chat.success(player, cleared == 0 ? Chat.escape(args[1]) + " had no island or co-op to reset."
                        : "Reset " + Chat.escape(args[1]) + ": " + cleared + " island record" + (cleared == 1 ? "" : "s") + " cleared. Their next /island builds a fresh island.");
                }
                default -> Chat.usage(player, usage());
            }
        } catch (UsageException usage) { Chat.usage(player, usage.getMessage()); }
        catch (IllegalArgumentException error) { Chat.error(player, Chat.escape(error.getMessage())); }
        catch (RuntimeException error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Island command failed", error); Chat.error(player, "The island change could not be saved. Please contact staff."); }
        return true;
    }
    public List<String> complete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player) || account(player) == null) return List.of();
        if (args.length == 1) return matching(SUBCOMMANDS, args[0]);
        if (args.length != 2) return List.of();
        AccountId actor = account(player);
        List<String> choices = switch (args[0].toLowerCase(Locale.ROOT)) {
            case "showcase", "gallery" -> Arrays.stream(IslandShowcase.values()).map(IslandShowcase::id).toList();
            case "visitors" -> List.of("on", "off");
            case "border", "borders" -> List.of("on", "off");
            case "home", "go", "journal", "rewards", "guests", "workers", "resources", "generator", "level", "talents", "machines", "chat", "advance" -> ownerNames(islands.homes(actor));
            case "leave" -> ownerNames(islands.homes(actor).stream().filter(i -> !i.owner().equals(actor)).toList());
            case "visit" -> ownerNames(islands.directory());
            case "accept", "deny", "decline" -> ownerNames(islands.invitations(actor, Instant.now()));
            case "invite" -> Bukkit.getOnlinePlayers().stream().filter(p -> !p.equals(player) && player.canSee(p)).map(Player::getName).toList();
            case "kick", "cancel" -> islands.owned(actor).map(i -> names(java.util.stream.Stream.concat(i.members().stream(), i.invites().keySet().stream()).toList())).orElse(List.of());
            default -> List.of();
        };
        return matching(choices, args[1]);
    }
    /** Owner names for tab completion. Account UUIDs are still accepted as input, but never suggested. */
    private List<String> ownerNames(List<IslandService.Island> entries) { return names(entries.stream().map(IslandService.Island::owner).toList()); }
    private List<String> names(Collection<AccountId> accounts) {
        return accounts.stream().filter(id -> !name(id).equals(id.toString())).map(this::name)
            .distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
    static List<String> matching(List<String> choices, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        return choices.stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    /**
     * The island hub. The companion draws a menu's bottom row as its footer, so everything here sits in the three rows
     * above it: what you do daily on top, how the island grows in the middle, travel and browsing below. Each button's
     * first two lines are what a card shows, so they say what it is and where it stands.
     */
    private void open(Player player, IslandService.Island island) {
        Page page = page(player, "Island", 54);
        if (island == null) {
            button(page, 22, Material.GRASS_BLOCK, "Start your island", List.of("A tree, crops, water and a generator.", "Your starter gear comes with your account.", "No automatic resets or upkeep."), "create your island", p -> handle(p, new String[]{"create"}));
        } else {
            AccountId me = account(player);
            boolean member = island.member(me), owner = island.owner().equals(me);
            var progress = development.progress(island.owner());
            int stars = development.stars(island.owner());
            info(page, 4, island.showcase().material(), island.name() + (stars > 0 ? " " + "★".repeat(stars) : ""),
                List.of("Island level " + progress.level() + " • " + island.width() + " × " + island.width(),
                    "Co-op: " + (island.members().size() + 1) + "/6 • Chapter " + island.tier() + "/3", IslandShowcase.progress(island)));
            // Row 1: the everyday things.
            button(page, 10, Material.ENDER_PEARL, "Return home", List.of("Travel to your landing."), "travel", p -> home(p, island.owner()));
            if (member) {
                int unclaimed = unclaimed(player, progress.level()).size();
                button(page, 11, Material.WRITABLE_BOOK, "Island journal", List.of(unclaimed > 0 ? unclaimed + " level reward" + (unclaimed == 1 ? "" : "s") + " to claim" : "Rewards, stages, land, talents",
                    "Stage: " + IslandResourceStage.at(resources.effectiveStage(island.owner())).title()), "open", p -> journal(p, island.owner()));
                if (unclaimed > 0) page.inventory.getItem(11).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                int waiting = guests.refresh(island.owner()).guests().size();
                button(page, 12, Material.BELL, "Island visitors", List.of(waiting > 0 ? waiting + " waiting with requests" : "Nobody waiting right now",
                    "They pay Marks and island XP."), "see who's here", p -> guests.open(p, island.owner()));
                if (waiting > 0) page.inventory.getItem(12).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                button(page, 13, Material.BARREL, "Workers' Lodge", List.of("Workers gather while you're away.", "Collect, hire and upgrade."), "open the lodge", p -> workers.open(p, island.owner()));
                button(page, 14, Material.EXPERIENCE_BOTTLE, "Talents • level " + progress.level(), List.of(progress.availablePoints() + " talent point" + (progress.availablePoints() == 1 ? "" : "s") + " to spend",
                    progress.progressLabel()), "open the talent tree", p -> talents(p, island.owner()));
                if (progress.availablePoints() > 0) page.inventory.getItem(14).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
                button(page, 15, Material.FURNACE, "Workshop", List.of("Machines, hoppers and spawners."), "open", p -> machines(p, island.owner()));
                button(page, 16, Material.FISHING_ROD, "Island fishing", List.of("Pond habitats and catches."), "open the Angler's Journal", p -> plugin.fishing().open(p));
            }
            // Row 2: the co-op and how the island looks.
            button(page, 19, Material.PLAYER_HEAD, "Co-op roster", roster(island), "see the co-op commands", p -> {
                p.closeInventory(); Chat.usage(p, "/island invite <player>"); Chat.usage(p, "/island kick <player>");
            });
            if (member) button(page, 20, Material.OAK_SIGN, "Co-op chat", List.of("Talk privately with your co-op.", "/channel global leaves it."), "talk in co-op chat", p -> {
                p.closeInventory(); plugin.chat().handleIsland(p, new String[]{island.owner().toString()});
            });
            if (owner) {
                button(page, 21, Material.AMETHYST_CLUSTER, "Showcase", List.of("Now: " + island.showcase().title(), IslandShowcase.values().length + " centerpieces to collect."), "open", this::gallery);
                button(page, 22, Material.OAK_DOOR, island.visitors() ? "Open to visitors" : "Closed to visitors", List.of(island.visitors() ? "Anyone may look around." : "Only your co-op may enter.",
                    "Visitors never build or loot."), island.visitors() ? "close your island" : "welcome visitors", p -> {
                    IslandService.Island current = islands.owned(island.owner()).orElseThrow();
                    if (current.visitors() != island.visitors()) { open(p, current); return; }
                    islands.visitors(account(p), !current.visitors()); recheckVisitors(); open(p, islands.owned(account(p)).orElseThrow());
                });
                button(page, 23, Material.NAME_TAG, "Rename island", List.of("Now: " + island.name(), "Free; keeps all progress."), "type a new name", p -> {
                    p.closeInventory(); Chat.usage(p, "/island rename <name>");
                });
            }
            boolean outline = borders.enabled(player, island.owner());
            button(page, 24, Material.LIGHT_BLUE_DYE, outline ? "Border shown" : "Border hidden", List.of("Outlines your building area.", "Only you see it."),
                outline ? "hide it" : "show it", p -> { borders.toggle(p, island.owner()); open(p, islands.owned(island.owner()).orElseThrow()); });
            button(page, 25, Material.GOLDEN_HELMET, "Island rankings", List.of("This week's race and the season.", "Top three paid every Monday."), "open", p -> ranking.open(p, true));
        }
        // Row 3: travel and other islands.
        button(page, 29, Material.COMPASS, "My islands", List.of("Your home and joined co-ops."), "open", p -> list(p, islands.homes(account(p)), "Your islands"));
        button(page, 30, Material.MAP, "Visit an island", List.of("Homes open to visitors."), "browse", p -> list(p, islands.directory(), "Open islands"));
        button(page, 32, Material.PAPER, "Invitations", List.of("Co-op invites waiting for you."), "open", p -> list(p, islands.invitations(account(p), Instant.now()), "Island invitations"));
        button(page, 33, Material.RECOVERY_COMPASS, "Return to the city", List.of("Shops, markets and services."), "travel", p -> plugin.travel().handle("spawn", p, new String[0]));
        back(page, "Menu", p -> plugin.menus().openHome(p));
        close(page);
        player.openInventory(page.inventory);
    }
    private List<String> roster(IslandService.Island i) {
        List<String> lines = new ArrayList<>(); lines.add("Owner: " + name(i.owner()));
        i.members().stream().sorted(Comparator.comparing(AccountId::toString)).forEach(id -> lines.add("Member: " + name(id)));
        return lines;
    }
    private void talents(Player player, AccountId owner) { talents(player, owner, 0); }
    private void talents(Player player, AccountId owner, int treePage) {
        IslandService.Island island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        if (!island.member(account(player))) throw new IllegalArgumentException("Only this co-op can open its talent tree.");
        development.flush(owner);
        var progress = development.progress(owner);
        int currentPage = Math.clamp(treePage, 0, 1);
        Page page = page(player, "Island Talent Tree " + (currentPage + 1) + "/2", 54);
        info(page, 4, Material.EXPERIENCE_BOTTLE, island.name() + " • Level " + progress.level(), List.of(progress.progressLabel(),
            progress.availablePoints() + " points available • 1 per 2 levels", "25 points at level 50; 48 ranks to choose from.",
            "Rank II opens either fork. Follow a fork to its capstone.", "The owner spends points for the whole co-op."));
        for (int column=0; column<2; column++) {
            final int branch=currentPage*2+column;
            var talents=java.util.Arrays.stream(IslandTalent.values()).filter(t->t.column()==branch).sorted(java.util.Comparator.comparingInt(IslandTalent::depth)).toList();
            info(page, 2+column*4, talents.getFirst().icon(), talents.getFirst().branch(), List.of("Choose either middle path, or invest in both."));
            boolean linked=progress.rank(talents.getFirst())>=2;
            for(int slot:net.nerdsmp.paper.gui.TalentTreeLayout.islandLinks(column)) {
                boolean path = slot < 36 ? linked : progress.rank(talents.get(1))>=2 || progress.rank(talents.get(2))>=2;
                info(page,slot,path?Material.YELLOW_STAINED_GLASS_PANE:Material.GRAY_STAINED_GLASS_PANE,"↙  Connected paths  ↘",List.of("A connected prerequisite must reach rank II."));
            }
            for (int node=0;node<talents.size();node++) {
                IslandTalent talent=talents.get(node); int rank=progress.rank(talent);
                boolean ready=rank<3 && progress.availablePoints()>0 && progress.level()>=talent.requiredLevel() && talent.requirementsMet(progress.talents());
                List<String> lore=new ArrayList<>(List.of(talent.branch()+" • Rank "+rank+"/3", "------------------------", talent.description(),
                    "Now: "+talent.effect(rank), "Next: "+talent.effect(Math.min(3,rank+1)), "Island level "+talent.requiredLevel(), "Cost: 1 island talent point"));
                if(!talent.prerequisiteOptions().isEmpty()) {
                    lore.add("Requires any connected talent at rank II:");
                    talent.prerequisiteOptions().forEach(parent->lore.add((progress.rank(parent)>=2?"✓ ":"○ ")+parent.title()+" 2/3"));
                }
                lore.add(rank==3?"Fully trained":ready?"Ready to learn":"Meet the requirements above.");
                if(!owner.equals(account(player))) lore.add("Only the island owner can spend these points.");
                int slot=net.nerdsmp.paper.gui.TalentTreeLayout.islandNode(column,node);
                button(page,slot,node==3?Material.NETHER_STAR:talent.icon(),talent.title(),lore,
                    ready&&owner.equals(account(player))?"review this rank":null,p->{
                        if(ready&&owner.equals(account(p))) confirmTalent(p,owner,talent,rank,currentPage);
                    });
                String color=ready?"green":rank>0?"gold":"gray";
                page.inventory.getItem(slot).editMeta(meta->{
                    meta.itemName(Chat.render("<"+color+">"+Chat.escape(talent.title()+" "+rank+"/3")+"</"+color+">"));
                    meta.setEnchantmentGlintOverride(rank>0);
                });
            }
        }
        if(currentPage>0) button(page,45,Material.ARROW,"← Industry & Nature",List.of(),"previous paths",p->talents(p,owner,0));
        else button(page,53,Material.ARROW,"Automation & Stewardship →",List.of(),"next paths",p->talents(p,owner,1));
        button(page,51,Material.CRAFTING_TABLE,"Machines and recipes",List.of("See the co-op's current machine limits."),"open the workshop",p->machines(p,owner));
        back(page,"Island",p->open(p,islands.owned(owner).orElseThrow())); close(page); player.openInventory(page.inventory);
    }
    private void confirmTalent(Player player, AccountId owner, IslandTalent talent, int rank, int treePage) {
        if (!owner.equals(account(player))) throw new IllegalArgumentException("Only the island owner can spend talent points.");
        var progress=development.progress(owner);
        if(rank>=3) throw new IllegalArgumentException("This talent is fully trained.");
        if(progress.rank(talent)!=rank) { talents(player,owner,treePage); return; }
        if(progress.availablePoints()<1) throw new IllegalArgumentException("Earn another talent point by leveling your island.");
        if(progress.level()<talent.requiredLevel()) throw new IllegalArgumentException("Reach island level "+talent.requiredLevel()+" first.");
        if(!talent.requirementsMet(progress.talents())) throw new IllegalArgumentException("Train a connected prerequisite to rank 2 first.");
        Page page=page(player,"Learn Talent?",27);
        info(page,MenuKit.CONFIRM_SUMMARY_SLOT,talent.icon(),talent.title()+" • Rank "+(rank+1),List.of(talent.description(),"Costs 1 island talent point.","This choice lasts for the season."));
        nav(page,MenuKit.CONFIRM_CANCEL_SLOT,MenuKit.cancel(),p->talents(p,owner,treePage));
        nav(page,MenuKit.CONFIRM_SLOT,MenuKit.confirm(MenuKit.withHint(List.of("<gray>Cost</gray> <white>1 talent point</white>"),"learn this rank")),p->{
            development.learn(account(p),owner,talent,rank); talents(p,owner,treePage);
            Chat.success(p,"<white>"+Chat.escape(talent.title())+"</white> is now rank "+(rank+1)+".");
        }); player.openInventory(page.inventory);
    }
    private void machines(Player player, AccountId owner) {
        IslandService.Island island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        if (!island.member(account(player))) throw new IllegalArgumentException("Only this co-op can view its workshop.");
        var progress = development.progress(owner);
        int stage = resources.effectiveStage(owner);
        Page page = page(player, "Island Workshop", 54);
        info(page, 4, Material.CRAFTING_TABLE, island.name() + " • Workshop", List.of("Resource stage: " + IslandResourceStage.at(stage).title(),
            "Processing machines: " + processingMachines.count(owner) + "/16", "Spawners per stack: " + progress.stackLimit(),
            "Chunk hoppers: " + automation.collectorCount(owner) + "/" + progress.hopperLimit(),
            "Machines run in loaded chunks; redstone pauses them."));
        int[] slots = {11, 13, 15, 29};
        for (MachineKind kind : MachineKind.values()) {
            List<String> lore = new ArrayList<>();
            lore.add(stage >= kind.stage() ? "Unlocked" : "Unlock: " + IslandResourceStage.at(kind.stage()).title());
            lore.addAll(processingMachines.recipeDescriptions(kind));
            lore.add("Place on your island and right-click to load it.");
            button(page, slots[kind.ordinal()], stage >= kind.stage() ? kind.icon() : Material.GRAY_DYE,
                kind.title(), lore, "see its recipes", p -> machineRecipes(p, owner, kind));
        }
        info(page, 31, Material.HOPPER, "Chunk hopper", List.of("Craft: a hopper surrounded by 8 copper ingots.", "Requires Collection network or Freight network in the talent tree.", "One per chunk; outputs through its normal pipe.", "Sneak + right-click with an item to filter drops by material. Empty hand clears the filter.", "Redstone pauses collection. Empty it before pickup.", "Sneak and break to recover it; filters reset on pickup.", "Never collects coin purses, protected drops or player-thrown items."));
        button(page, 33, Material.SPAWNER, "Spawner recipes", List.of("13 species, with new options unlocked through play.",
            "Four iron blocks, four mob ingredients, one diamond.", "Old spawners and stacks keep working."), "see recipes and your unlocks", p -> spawnerRecipes(p, owner));
        button(page, 39, Material.KNOWLEDGE_BOOK, "Discover machine recipes", List.of("Adds the recipes to your crafting book.", "Materials and island unlocks are still required."), "add them", p -> {
            automation.discover(p); processingMachines.discover(p); Chat.success(p, "The machine recipes are in your recipe book.");
        });
        button(page, 41, Material.WRITABLE_BOOK, "Island journal", List.of("Stages, rewards and land."), "open", p -> journal(p, owner));
        button(page, 43, Material.EXPERIENCE_BOTTLE, "Talent tree", List.of("Spend island talent points."), "open", p -> talents(p, owner));
        back(page, "Island", p -> open(p, islands.owned(owner).orElseThrow()));
        close(page);
        player.openInventory(page.inventory);
    }

    private void machineRecipes(Player player, AccountId owner, MachineKind kind) {
        var island = islands.owned(owner).orElseThrow();
        if (!island.member(account(player))) throw new IllegalArgumentException("Only this co-op can use its workshop.");
        Page page = page(player, "Workshop • " + kind.title(), 27);
        List<String> lore = new ArrayList<>(processingMachines.recipeDescriptions(kind));
        lore.add("Unlock: " + IslandResourceStage.at(kind.stage()).title());
        lore.add("Top row: materials. Middle row: fuel.");
        lore.add("Bottom row: finished goods. Take them out normally.");
        lore.add("Drag items into the input and fuel rows.");
        lore.add("Hoppers cannot move items through these stations.");
        lore.add("Named, enchanted and other altered items are not consumed.");
        lore.add("Redstone pauses work. No offline production or machine XP.");
        lore.add("Empty it, then sneak and break to pick it up.");
        info(page, 13, kind.icon(), kind.title(), lore);
        back(page, "Workshop", p -> machines(p, owner));
        close(page);
        player.openInventory(page.inventory);
    }

    private void spawnerRecipes(Player player, AccountId owner) {
        var island = islands.owned(owner).orElseThrow();
        if (!island.member(account(player))) throw new IllegalArgumentException("Only this co-op can use its workshop.");
        int stage = resources.effectiveStage(owner);
        Page page = page(player, "Spawner Recipes", 54);
        info(page, 4, Material.SPAWNER, "Build your mob farm", List.of("Recipe grid: IFI / FDF / IFI",
            "I = iron block, F = species ingredient, D = diamond.", "Stack limit: " + development.progress(owner).stackLimit(),
            "Craft anywhere; place and stack on your co-op island."));
        int index = 0;
        for (var definition : IslandSpawnerCatalog.all()) {
            int slot = 10 + index / 7 * 9 + index % 7; index++;
            boolean unlocked = definition.minimumResourceStage() <= stage;
            info(page, slot, unlocked ? definition.ingredient() : Material.GRAY_DYE, definition.title(),
                List.of("4 iron blocks + 4 " + materialName(definition.ingredient()) + " + 1 diamond",
                    definition.legacy() ? "Original recipe • always available" : "Unlock: " + definition.unlockStage().title(),
                    unlocked ? "Ready to place on this island" : "Complete the resource milestone first",
                    "Use normal ingredients, not named or modified items.",
                    "Right-click a matching spawner with another to stack.",
                    "Sneak and break to recover the entire stack."));
        }
        button(page, 40, Material.KNOWLEDGE_BOOK, "Discover recipes", List.of("Adds all spawner recipes to your crafting book."), "add them", p -> {
            automation.discover(p); Chat.success(p, "Spawner recipes are in your crafting book.");
        });
        back(page, "Workshop", p -> machines(p, owner));
        close(page);
        player.openInventory(page.inventory);
    }
    /** Shows a showcase above the player's own island, if anything the co-op has done unlocks it. */
    private void display(AccountId owner, IslandShowcase choice) {
        development.flush(owner);
        islands.showcase(owner, choice, showcaseContext(owner));
    }

    private String showcaseMilestonePath() { return "seasons." + season + ".showcase-milestones"; }

    private void gallery(Player player) { gallery(player, 0); }
    private void gallery(Player player, int sheet) {
        IslandService.Island island = islands.owned(account(player)).orElseThrow(() -> new IllegalArgumentException("Create your own island first."));
        development.flush(island.owner());
        IslandShowcase.Context context = showcaseContext(island.owner());
        List<IslandShowcase> all = List.of(IslandShowcase.values());
        int owned = context.collected();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        int sheets = (all.size() + slots.length - 1) / slots.length, current = Math.clamp(sheet, 0, sheets - 1);
        Page page = page(player, "Island Showcase " + (current + 1) + "/" + sheets, 54);
        int reached = IslandShowcase.milestones(owned);
        info(page, 4, Material.ITEM_FRAME, "Your collection", List.of(owned + "/" + all.size() + " unlocked",
            "Earned by the whole co-op: chapters, island levels, stages,", "achievements, quest chains, expeditions and visitors.",
            "Every " + IslandShowcase.MILESTONE_STEP + " collected is a milestone: Marks for each member,",
            "and +" + Math.round(IslandShowcase.VISITOR_BONUS_PER_MILESTONE * 100) + "% visitor Marks for good (now +"
                + Math.round(reached * IslandShowcase.VISITOR_BONUS_PER_MILESTONE * 100) + "%).",
            "Now showing: " + island.showcase().title()));
        for (int n = current * slots.length, i = 0; n < all.size() && i < slots.length; n++, i++) {
            IslandShowcase choice = all.get(n);
            boolean unlocked = choice.unlocked(context);
            int slot = slots[i];
            String source = switch (choice.kind()) {
                case CHAPTER -> "Land chapter"; case LEVEL -> "Island level"; case STAGE -> "Resource stage";
                case ACHIEVEMENT -> "Achievements"; case QUEST -> "Quest chain"; case EXPEDITION -> "Expedition"; case GUESTS -> "Visitors";
                case WORKERS -> "Workers' Lodge"; case BOSS -> "World boss"; case PRESTIGE -> "Prestige";
            };
            List<String> lore = List.of("[ " + choice.rarity() + " ] • " + source, "------------------------",
                unlocked ? (choice == island.showcase() ? "Currently displayed" : "Unlocked") : choice.requirement() + " to unlock",
                "------------------------", "Floats above your island landing.", "A keepsake of your co-op's progress.");
            // Locked ones show their real item too (with a gray name), so players can see what they're working toward.
            button(page, slot, choice.material(), choice.title(), lore,
                unlocked && choice != island.showcase() ? "display it" : null, p -> {
                    if (!unlocked || choice == island.showcase()) return;
                    display(account(p), choice); gallery(p, current);
                });
            page.inventory.getItem(slot).editMeta(meta -> {
                meta.itemName(Component.text(choice.title(), unlocked ? choice.color() : NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
                meta.setEnchantmentGlintOverride(choice == island.showcase());
            });
        }
        // Collection milestones, each claimed once a season by each member.
        Set<Integer> claimed = new HashSet<>(plugin.playerData().read(account(player), yaml -> yaml.getIntegerList(showcaseMilestonePath())));
        int ready = 0;
        for (int n = 1; n <= reached; n++) if (!claimed.contains(n)) ready++;
        int nextMilestone = (reached + 1) * IslandShowcase.MILESTONE_STEP;
        List<String> milestoneLore = new ArrayList<>(List.of(reached + " reached • next at " + nextMilestone + " showcases",
            "Each pays every member Marks once a season:", "500 for the 1st, 1,000 for the 2nd, and so on."));
        milestoneLore.add(ready > 0 ? ready + " ready to claim!" : "Nothing to claim right now.");
        int toClaim = ready;
        button(page, 38, Material.GOLD_BLOCK, "Collection milestones", milestoneLore, toClaim > 0 ? "claim" : null, p -> {
            if (toClaim > 0) claimShowcaseMilestones(p, reached); gallery(p, current);
        });
        if (toClaim > 0) page.inventory.getItem(38).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        button(page, 42, Material.COMPARATOR, "Display settings", List.of("Hide island displays or reduce celebration effects.", "Applies only to your view."), "open Player Settings", p -> p.performCommand("settings"));
        button(page, 39, Material.NETHER_STAR, "Level rewards", List.of("Ten showcases come from island levels.", "See what each level earns."), "open", p -> milestones(p, island.owner()));
        button(page, 41, Material.BELL, "Island visitors", List.of("Three showcases come from serving visitors."), "open", p -> guests.open(p, island.owner()));
        if (current > 0) nav(page, MenuKit.previousSlot(54), MenuKit.previous(current, sheets), p -> gallery(p, current - 1));
        if (current + 1 < sheets) nav(page, MenuKit.nextSlot(54), MenuKit.next(current, sheets), p -> gallery(p, current + 1));
        back(page, "Island", p -> open(p, islands.owned(account(p)).orElseThrow()));
        close(page);
        player.openInventory(page.inventory);
    }

    private void claimShowcaseMilestones(Player player, int reached) {
        AccountId account = account(player);
        if (account == null) return;
        List<Integer> claimed = new ArrayList<>(plugin.playerData().read(account, yaml -> yaml.getIntegerList(showcaseMilestonePath())));
        long total = 0;
        for (int n = 1; n <= reached; n++) {
            if (claimed.contains(n)) continue;
            claimed.add(n);
            long marks = IslandShowcase.milestoneMarks(n);
            total += marks;
            plugin.economy().credit(account, marks, new net.nerdsmp.api.service.EconomyService.TransactionReason("island-showcase", "Showcase milestone " + n),
                "showcase-milestone:" + season + ":" + n + ":" + account.value());
        }
        if (total == 0) return;
        List<Integer> saved = List.copyOf(claimed);
        plugin.playerData().update(account, yaml -> yaml.set(showcaseMilestonePath(), saved));
        Chat.success(player, "Collection milestones claimed: <gold>+" + Chat.marks(total) + "</gold>");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.3F);
    }
    private void list(Player player, List<IslandService.Island> entries, String title) { list(player, entries, title, 0); }
    private void list(Player player, List<IslandService.Island> entries, String title, int index) {
        Page page = page(player, title, 54);
        int pages = Math.max(1, (entries.size() + 20) / 21);
        int current = Math.max(0, Math.min(index, pages - 1)), start = current * 21;
        boolean invitation = title.equals("Island invitations");
        for (int n = start; n < Math.min(start + 21, entries.size()); n++) {
            IslandService.Island island = entries.get(n);
            int offset = n - start, slot = 10 + offset / 7 * 9 + offset % 7;
            button(page, slot, island.showcase().material(), island.name(), List.of("Owner: " + name(island.owner()), "Chapter " + island.tier() + "/3 • " + island.showcase().title(), "Area: " + island.width() + " × " + island.width()),
                invitation ? "accept full co-op access" : "open", p -> {
                if (invitation) handle(p, new String[]{"accept", island.owner().toString()});
                else { IslandService.Island latest = islands.owned(island.owner()).orElseThrow();
                    if (!latest.canEnter(account(p))) { Chat.error(p, "This island is closed."); return; } open(p, latest); }
            });
        }
        if (entries.isEmpty()) info(page, 22, Material.GLASS_BOTTLE, invitation ? "No pending invitations" : "No islands to show",
            List.of(invitation ? "Invitations expire after five minutes." : "Check back as more islands open to visitors."));
        if (current > 0) nav(page, MenuKit.previousSlot(54), MenuKit.previous(current, pages), p -> list(p, entries, title, current - 1));
        if (current + 1 < pages) nav(page, MenuKit.nextSlot(54), MenuKit.next(current, pages), p -> list(p, entries, title, current + 1));
        back(page, "Island", p -> open(p, islands.owned(account(p)).orElse(null)));
        close(page);
        player.openInventory(page.inventory);
    }
    /** The journal's front page: where the island stands, and the way into each part of it. */
    private void journal(Player player, AccountId owner) {
        IslandService.Island island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        if (!island.member(account(player))) throw new IllegalArgumentException("Only this co-op can use its journal.");
        development.flush(owner);
        var progress = development.progress(owner);
        int level = progress.level();
        Page page = page(player, "Island Journal", 54);
        List<String> header = new ArrayList<>(List.of("Level " + level + " " + bar(progress.levelFraction()), progress.progressLabel(),
            progress.availablePoints() + " talent point" + (progress.availablePoints() == 1 ? "" : "s") + " to spend"));
        IslandMilestone.next(level).ifPresentOrElse(next -> header.add("Next reward: " + next.title() + " at level " + next.level()
                + " (" + (IslandDevelopment.experienceForLevel(next.level()) - progress.xp()) + " XP to go)"),
            () -> {
                long prestige = development.prestigeXp(owner);
                int stars = IslandDevelopment.stars(prestige);
                header.add("Prestige: " + "★".repeat(stars) + "☆".repeat(IslandDevelopment.MAX_STARS - stars) + " (+" + stars + "% skill XP here)");
                if (stars < IslandDevelopment.MAX_STARS) header.add((IslandDevelopment.XP_PER_STAR * (stars + 1) - prestige) + " island XP to the next star");
            });
        info(page, 4, Material.WRITABLE_BOOK, island.name() + " • Island level " + level, header);
        List<IslandMilestone> ready = unclaimed(player, level);
        List<String> rewards = new ArrayList<>(List.of(IslandMilestone.reachedBy(level).size() + "/" + IslandMilestone.values().length + " reached",
            "Every 5 levels: a lasting perk for the co-op", "and Marks for each member to claim."));
        IslandMilestone.next(level).ifPresent(next -> rewards.add("Next: " + next.perk()));
        if (!ready.isEmpty()) rewards.add(ready.size() + " ready to claim!");
        button(page, 20, Material.NETHER_STAR, "Level rewards", rewards, "open", p -> milestones(p, owner));
        if (!ready.isEmpty()) page.inventory.getItem(20).editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        var snapshot = resources.snapshot(owner);
        button(page, 22, Material.DIAMOND_ORE, "Resource stages", List.of("Now: " + snapshot.resourceStage().title(),
            snapshot.nextStage().map(stage -> "Next: " + stage.title() + (stage.islandLevel() > 1 ? " (island level " + stage.islandLevel() + ")" : "")).orElse("Every stage unlocked."),
            "Generator ores, machines, spawners and pond habitats."), "open", p -> resourceJournal(p, owner));
        button(page, 24, Material.MAP, "Land chapters", List.of("Chapter " + island.tier() + "/3", "Building area: " + island.width() + " × " + island.width()),
            "open", p -> landJournal(p, owner));
        button(page, 30, Material.EXPERIENCE_BOTTLE, "Talent tree", List.of(progress.availablePoints() + " points to spend", "One point every two island levels."),
            "open", p -> talents(p, owner));
        button(page, 32, Material.FURNACE, "Island workshop", List.of("Machines, chunk hoppers and spawners."), "open", p -> machines(p, owner));
        List<String> how = new ArrayList<>(List.of("Skill on your island to earn island XP:", "up to " + IslandDevelopment.MAX_AWARD + " per action and "
            + String.format(Locale.ROOT, "%,d", IslandDevelopment.DAILY_XP_CAP) + " a day.", "Every co-op member's work counts."));
        if (plugin.boosters() != null && plugin.boosters().multiplier() > 1D) how.add("Raised now: " + plugin.boosters().label() + ".");
        else how.add("Double XP Weekends (Sat-Sun) and XP boosters raise it.");
        info(page, 40, Material.CLOCK, "Earning island XP", how);
        button(page, 38, Material.BELL, "Island visitors", List.of("Serve visitors for Marks and island XP."), "open", p -> guests.open(p, owner));
        button(page, 34, Material.BARREL, "Workers' Lodge", List.of("Hire workers who gather while you're away."), "open", p -> workers.open(p, owner));
        button(page, 28, Material.GOLDEN_HELMET, "Island rankings", List.of("This week's race and the season board."), "open", p -> ranking.open(p, true));
        if (owner.equals(account(player))) button(page, 42, Material.ITEM_FRAME, "Island showcase", List.of("Your collection of centerpieces."), "open", this::gallery);
        back(page, "Island", p -> open(p, islands.owned(owner).orElseThrow()));
        close(page);
        player.openInventory(page.inventory);
    }

    private static String bar(double fraction) {
        int filled = (int) Math.round(Math.clamp(fraction, 0D, 1D) * 10);
        return "■".repeat(filled) + "□".repeat(10 - filled);
    }

    private String milestonePath() { return "seasons." + season + ".island-milestones"; }

    private Set<String> claimed(AccountId account) {
        return new HashSet<>(plugin.playerData().read(account, yaml -> yaml.getStringList(milestonePath())));
    }

    /** The milestones this island has reached that this player hasn't claimed yet. */
    private List<IslandMilestone> unclaimed(Player player, int islandLevel) {
        AccountId account = account(player);
        if (account == null) return List.of();
        Set<String> done = claimed(account);
        return IslandMilestone.reachedBy(islandLevel).stream().filter(m -> !done.contains(m.id())).toList();
    }

    /** Level rewards: ten milestones, each a perk for the co-op and a reward each member claims once a season. */
    private void milestones(Player player, AccountId owner) {
        IslandService.Island island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        if (!island.member(account(player))) throw new IllegalArgumentException("Only this co-op can claim its level rewards.");
        development.flush(owner);
        var progress = development.progress(owner);
        int level = progress.level();
        Set<String> done = claimed(account(player));
        Page page = page(player, "Island Level Rewards", 54);
        info(page, 4, Material.NETHER_STAR, island.name() + " • Level " + level, List.of(bar(progress.levelFraction()) + " " + progress.progressLabel(),
            "Perks work for every member on this island.", "Each member claims each reward once a season,",
            "on whichever island earned it first."));
        int[] slots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
        for (IslandMilestone milestone : IslandMilestone.values()) {
            boolean reached = milestone.reached(level), taken = done.contains(milestone.id());
            List<String> lore = new ArrayList<>(List.of("Island level " + milestone.level(), "------------------------",
                "Perk: " + milestone.perk(), reached ? "✓ Active on this island" : "Unlocks at level " + milestone.level() + " ("
                    + (IslandDevelopment.experienceForLevel(milestone.level()) - progress.xp()) + " island XP to go)",
                "Reward: " + milestone.rewardLabel(), "Showcase: " + showcaseFor(milestone).title(),
                taken ? "✓ Claimed" : reached ? "Ready to claim" : "Reach the level to claim"));
            int slot = slots[milestone.ordinal()];
            button(page, slot, milestone.icon(), milestone.title(), lore, reached && !taken ? "claim the reward" : null, p -> {
                if (reached && !taken) claim(p, owner, milestone);
            });
            String color = reached && !taken ? "green" : reached ? "gold" : "gray";
            page.inventory.getItem(slot).editMeta(meta -> {
                meta.itemName(Chat.render("<" + color + ">" + Chat.escape("Lv " + milestone.level() + " • " + milestone.title()) + "</" + color + ">"));
                meta.setEnchantmentGlintOverride(reached && !taken);
            });
        }
        boolean effects = perks.enabled(player);
        button(page, 40, effects ? Material.BEACON : Material.GLASS, effects ? "Island effects: on" : "Island effects: off",
            List.of("Speed, Haste and Regeneration from level rewards.", "Only while you're on this island in survival.", "Applies only to you."),
            effects ? "turn them off" : "turn them on", p -> { perks.toggle(p); milestones(p, owner); });
        back(page, "Island Journal", p -> journal(p, owner));
        close(page);
        player.openInventory(page.inventory);
    }

    /** The showcase each level reward unlocks: every milestone has one. */
    static IslandShowcase showcaseFor(IslandMilestone milestone) {
        for (IslandShowcase showcase : IslandShowcase.values())
            if (showcase.kind() == IslandShowcase.Kind.LEVEL && showcase.need() == milestone.level()) return showcase;
        throw new IllegalStateException("No showcase for island level " + milestone.level());
    }

    private void claim(Player player, AccountId owner, IslandMilestone milestone) {
        AccountId account = account(player);
        if (account == null) { Chat.accountLoading(player); return; }
        IslandService.Island island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        if (!island.member(account)) throw new IllegalArgumentException("Only this co-op can claim its level rewards.");
        development.flush(owner);
        if (!milestone.reached(development.progress(owner).level())) throw new IllegalArgumentException("Reach island level " + milestone.level() + " first.");
        if (claimed(account).contains(milestone.id())) { Chat.info(player, "You've already claimed this reward this season."); milestones(player, owner); return; }
        if (!claiming.add(player.getUniqueId())) return;
        String key = "island-milestone:" + season + ":" + milestone.id() + ":" + account.value();
        var reason = new net.nerdsmp.api.service.EconomyService.TransactionReason("island-milestone", "Island level " + milestone.level() + " reward");
        plugin.economy().credit(account, milestone.marks(), reason, key).whenComplete((paid, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            claiming.remove(player.getUniqueId());
            var status = paid == null ? null : paid.status();
            if (error != null || status != net.nerdsmp.api.service.EconomyService.TransactionResult.Status.APPLIED
                && status != net.nerdsmp.api.service.EconomyService.TransactionResult.Status.DUPLICATE) {
                if (error != null) plugin.getLogger().log(java.util.logging.Level.WARNING, "Island reward for " + player.getName() + " failed", error);
                if (player.isOnline()) Chat.error(player, "The reward could not be paid. Try again shortly.");
                return;
            }
            plugin.playerData().update(account, yaml -> {
                List<String> list = new ArrayList<>(yaml.getStringList(milestonePath()));
                if (!list.contains(milestone.id())) list.add(milestone.id());
                yaml.set(milestonePath(), list);
            });
            if (milestone.booster() && plugin.boosters() != null)
                plugin.boosters().earn(account, "island-milestone:" + season + ":" + milestone.id());
            if (!player.isOnline()) return;
            Chat.success(player, "Claimed <white>" + Chat.escape(milestone.title()) + "</white>: <gold>" + Chat.marks(milestone.marks()) + "</gold>"
                + (milestone.booster() ? " and an <gold>XP booster</gold> <gray>(use it with /booster use)</gray>" : "") + ".");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7F, 1.4F);
            milestones(player, owner);
        }));
    }

    /** Tells the co-op's online members their island leveled, and what that earned. */
    private void leveled(AccountId owner, int from, int to) {
        IslandService.Island island = islands.owned(owner).orElse(null);
        if (island == null) return;
        if (from >= IslandDevelopment.MAX_LEVEL) {
            // Past level 50, "levels" are prestige stars.
            int stars = to - IslandDevelopment.MAX_LEVEL;
            for (Player member : Bukkit.getOnlinePlayers()) {
                AccountId account = account(member);
                if (account == null || !island.member(account)) continue;
                Chat.send(member, "<gold>★ " + Chat.escape(island.name()) + " earned prestige star " + stars + "!</gold> <gray>+1% skill XP on the island per star"
                    + (stars >= IslandDevelopment.MAX_STARS ? ". That's every star there is." : ". The next comes after "
                    + String.format(Locale.ROOT, "%,d", IslandDevelopment.XP_PER_STAR) + " more island XP.") + "</gray>");
                member.playSound(member.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.4F);
            }
            return;
        }
        List<IslandMilestone> earned = java.util.Arrays.stream(IslandMilestone.values())
            .filter(m -> m.level() > from && m.level() <= to).toList();
        int points = IslandDevelopment.earnedPoints(to) - IslandDevelopment.earnedPoints(from);
        for (Player member : Bukkit.getOnlinePlayers()) {
            AccountId account = account(member);
            if (account == null || !island.member(account)) continue;
            Chat.success(member, "<white>" + Chat.escape(island.name()) + "</white> reached <gold>island level " + to + "</gold>!"
                + (points > 0 ? " <gray>+" + points + " talent point" + (points == 1 ? "" : "s") + " in</gray> " + Chat.command("island talents") + "<gray>.</gray>" : ""));
            for (IslandMilestone milestone : earned)
                Chat.send(member, "<gold>✦ Level reward: " + Chat.escape(milestone.title()) + "</gold> <gray>" + Chat.escape(milestone.perk())
                    + " New showcase: <white>" + Chat.escape(showcaseFor(milestone).title()) + "</white>. Claim <white>"
                    + Chat.escape(milestone.rewardLabel()) + "</white> in</gray> " + Chat.command("island rewards") + "<gray>.</gray>");
            member.playSound(member.getLocation(), earned.isEmpty() ? Sound.ENTITY_PLAYER_LEVELUP : Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.2F);
        }
    }

    private void resourceJournal(Player player, AccountId owner) {
        IslandService.Island island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        if (!island.member(account(player))) throw new IllegalArgumentException("Only this co-op can use its journal.");
        var snapshot = resources.snapshot(owner);
        int current = snapshot.stage();
        Page page = page(player, "Resource Journal", 54);
        String next = snapshot.nextStage().map(stage -> "Next: " + stage.title()).orElse("Every resource stage is unlocked.");
        info(page, 4, Material.WRITABLE_BOOK, island.name() + " • " + snapshot.resourceStage().title(),
            List.of("Island level " + snapshot.islandLevel() + " • " + next,
                "Train skills, then share your progress below.", "Different teammates can cover different skills.",
                "Already-earned chapters and talents still count."));
        int[] slots = {11, 13, 15, 29, 31, 33};
        Material[] icons = {Material.COBBLESTONE, Material.GOLD_ORE, Material.REDSTONE_ORE,
            Material.DIAMOND_ORE, Material.NETHER_QUARTZ_ORE, Material.EMERALD_ORE};
        for (IslandResourceStage stage : IslandResourceStage.values()) {
            List<String> lore = new ArrayList<>();
            lore.add(stage.index() <= current ? "Unlocked" : stage.index() == current + 1 ? "Your next milestone" : "Finish the earlier milestone first.");
            if (stage.islandLevel() > 1) lore.add((snapshot.islandLevel() >= stage.islandLevel() ? "[x] " : "[ ] ")
                + "Island level " + stage.islandLevel());
            stage.requirements().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                boolean certified = stage.index() <= current || (stage.index() <= 3
                    ? island.proofs().containsKey((stage.index() - 1) + ":" + entry.getKey().name())
                    : snapshot.proofs().containsKey(IslandResourceProgression.Proof.forRequirement(stage, entry.getKey())));
                lore.add((certified ? "[x] " : "[ ] ") + entry.getKey().displayName() + " level " + entry.getValue());
            });
            lore.add("Generator • base chances before talents");
            GeneratorLoot.resourceTable(stage.index()).forEach((material, percent) ->
                lore.add(materialName(material) + ": " + percent + "%"));
            if (stage.index() > 0 && stage.index() <= 3) lore.add("Land expansion and showcase from chapter " + stage.index() + ".");
            for (MachineKind kind : MachineKind.values()) if (kind.stage() == stage.index()) lore.add("Machine: " + kind.title());
            String spawners = IslandSpawnerCatalog.all().stream().filter(definition -> !definition.legacy()
                && definition.minimumResourceStage() == stage.index()).map(IslandSpawnerCatalog.Definition::title)
                .collect(java.util.stream.Collectors.joining(", "));
            if (!spawners.isEmpty()) lore.add("Spawners: " + spawners);
            String habitat = switch (stage.index()) { case 0 -> "Freshwater"; case 2 -> "River"; case 3 -> "Wetland";
                case 4 -> "Ocean"; case 5 -> "Cold Water"; default -> ""; };
            if (!habitat.isEmpty()) lore.add("Pond habitat: " + habitat + " • choose in /fishing");
            if (stage.index() == 4) lore.add("Sifter: soul sand to quartz; red sand to gold.");
            if (stage.index() == 5) lore.add("Sifter: end stone to diamonds.");
            info(page, slots[stage.index()], icons[stage.index()], stage.title(), lore);
        }
        button(page, 38, Material.MAP, "Land chapters", List.of("Your three original expansions and their rewards.",
            "Resource milestones never move or shrink your island."), "open", p -> landJournal(p, owner));
        button(page, 40, Material.FURNACE, "Island workshop", List.of("Turn gathered materials into useful supplies.",
            "See recipes and machine unlocks."), "open", p -> machines(p, owner));
        button(page, 42, Material.EXPERIENCE_BOTTLE, "Share your skill progress", List.of("Record skills you have already trained.",
            "No items, Marks, XP or talent points are spent.", "When every requirement is met, the next stage unlocks."), "share your progress", p -> advance(p, owner));
        back(page, "Island Journal", p -> journal(p, owner));
        close(page);
        player.openInventory(page.inventory);
    }

    private static String materialName(Material material) {
        return net.nerdsmp.paper.presentation.VisualFormat.titleCase(material.name().toLowerCase(Locale.ROOT));
    }

    private void landJournal(Player player, AccountId owner) {
        IslandService.Island island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        if (!island.member(account(player))) throw new IllegalArgumentException("Only this co-op can use its journal.");
        Page page = page(player, "Land Chapters", 45);
        for (IslandChapter chapter : IslandChapter.values()) {
            List<String> lore = new ArrayList<>();
            lore.add(chapter.ordinal() < island.tier() ? "Completed" : chapter.ordinal() == island.tier() ? "Current chapter" : "Complete the earlier chapter first.");
            chapter.requirements().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> lore.add(
                (island.proofs().containsKey(chapter.ordinal() + ":" + e.getKey().name()) ? "[x] " : "[ ] ")
                    + e.getKey().displayName() + " level " + e.getValue()));
            lore.add("Reward: " + ((chapter.ordinal() + 1) * 2 + 3) * 16 + " × " + ((chapter.ordinal() + 1) * 2 + 3) * 16 + " building area");
            lore.add("New generator ores; no Marks charge.");
            IslandShowcase unlock = IslandShowcase.values()[chapter.ordinal() + 1];
            lore.add("Showcase: " + unlock.title() + " [" + unlock.rarity() + "]");
            info(page, 11 + chapter.ordinal() * 2, chapter.ordinal() < island.tier() ? Material.ENCHANTED_BOOK : Material.BOOK, chapter.title(), lore);
        }
        button(page, 31, Material.EXPERIENCE_BOTTLE, "Share your skill progress", List.of("Put your trained skills toward the next chapter.", "Teammates can cover different skills.", "Your contribution stays with this island."), "share your progress", p -> advance(p, owner));
        back(page, "Island Journal", p -> journal(p, owner));
        close(page);
        player.openInventory(page.inventory);
    }
    private void advance(Player player, AccountId owner) {
        AccountId actor = account(player);
        IslandService.Island before = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("Create an island first."));
        int expectedStage = resources.effectiveStage(owner);
        if (!before.member(actor)) throw new IllegalArgumentException("You are not in this co-op.");
        if (!advancing.add(player.getUniqueId())) return;
        plugin.progression().snapshot(actor).whenComplete((skills, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            advancing.remove(player.getUniqueId());
            if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player || !actor.equals(account(player))) return;
            if (error != null) { Chat.error(player, "Your skills could not be loaded. Try again shortly."); return; }
            try {
                development.flush(owner);
                boolean unlocked = before.tier() < IslandChapter.values().length
                    ? islands.advance(actor, owner, before.tier(), skills)
                    : resources.contribute(actor, owner, expectedStage, skills);
                if (unlocked) presentation.celebrate(islands.owned(owner).orElseThrow());
                if (unlocked) Chat.success(player, "<white>" + resources.snapshot(owner).resourceStage().title() + "</white> unlocked! Check your journal for new materials and recipes.");
                else if (expectedStage == 5) Chat.info(player, "Every resource stage is already unlocked.");
                else Chat.success(player, "Your trained skills have been recorded in the journal.");
                resourceJournal(player, owner);
            } catch (IllegalArgumentException failure) { Chat.error(player, Chat.escape(failure.getMessage())); }
            catch (RuntimeException failure) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Island chapter save failed", failure); Chat.error(player, "The chapter could not be saved. Contact staff."); }
        }));
    }
    private void home(Player player, AccountId owner) {
        AccountId actor = account(player);
        IslandService.Island island = islands.owned(owner).orElseThrow(() -> new IllegalArgumentException("That player has no island yet."));
        if (!island.canEnter(actor)) throw new IllegalArgumentException("That island is closed to visitors.");
        if (!travelling.add(player.getUniqueId())) return;
        player.closeInventory();
        world.getChunkAtAsync(IslandGrid.chunkX(island.slot()), IslandGrid.chunkZ(island.slot())).whenComplete((chunk, error) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                travelling.remove(player.getUniqueId());
                if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player || !actor.equals(account(player))) return;
                IslandService.Island current = islands.owned(owner).orElse(null);
                if (error != null || current == null || !current.canEnter(actor)) { Chat.error(player, "Island travel is unavailable. Try again shortly."); if (player.getY() < world.getMinHeight()) returnToHub(player); return; }
                Location destination = landing(current);
                if(current.member(actor)) StarterGeneratorRepair.repair(world,current.slot());
                if (!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()) { Chat.error(player, "The island landing is blocked. Ask staff to clear it."); if (player.getY() < world.getMinHeight()) returnToHub(player); return; }
                player.setFallDistance(0);
                if (!player.teleport(destination)) { Chat.error(player, "Travel was cancelled."); if (player.getY() < world.getMinHeight()) returnToHub(player); }
            }));
    }
    private Location landing(IslandService.Island island) { return new Location(world, IslandGrid.blockX(island.slot()) + .5, IslandGrid.SURFACE_Y + 1, IslandGrid.blockZ(island.slot()) + .5); }
    private boolean canEnter(Player player, Location location) {
        if (!isIslandWorld(location.getWorld())) return true;
        if (player.hasPermission("nerdsmp.claim.bypass")) return true;
        AccountId actor = account(player);
        return actor != null && islands.at(location.getBlockX() >> 4, location.getBlockZ() >> 4).map(i -> i.canEnter(actor)).orElse(false);
    }
    private void recheckVisitors() {
        for (Player player : world.getPlayers()) if (!canEnter(player, player.getLocation())) returnToHub(player);
    }
    private void returnToHub(Player player) {
        Location configured=plugin.travel()==null?null:plugin.travel().publicSpawn();
        if(configured!=null && player.teleport(configured)) return;
        World hub = Bukkit.getWorlds().stream().filter(w -> !w.equals(world) && w.getEnvironment() == World.Environment.NORMAL).findFirst().orElse(null);
        if (hub == null || !player.teleport(hub.getSpawnLocation())) player.kick(Component.text("Your island access changed. Please reconnect."));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || (event.getFrom().getWorld().equals(event.getTo().getWorld())
            && event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
            && event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4)) return;
        if (!canEnter(event.getPlayer(), event.getTo())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && !canEnter(event.getPlayer(), event.getTo())) { event.setCancelled(true); Chat.error(event.getPlayer(), "That island is closed or outside the unlocked area."); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (isIslandWorld(event.getFrom().getWorld())) { event.setCancelled(true); Chat.notice(event.getPlayer(), "Portals are closed on islands. Use " + Chat.command("expedition") + " or " + Chat.command("warps") + " to visit public areas."); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (isIslandWorld(event.getFrom().getWorld()) || event.getTo() != null && isIslandWorld(event.getTo().getWorld())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        if (plugin.spirits() != null && plugin.spirits().ghost(event.getPlayer().getUniqueId())) return;
        if (!isIslandWorld(event.getPlayer().getWorld()) && !isIslandWorld(event.getRespawnLocation().getWorld())) return;
        AccountId actor = account(event.getPlayer());
        islands.owned(actor).ifPresentOrElse(i -> event.setRespawnLocation(landing(i)), () -> {
            Location configured = plugin.travel() == null ? null : plugin.travel().publicSpawn();
            if (configured != null) { event.setRespawnLocation(configured); return; }
            Bukkit.getWorlds().stream().filter(w -> !w.equals(world) && w.getEnvironment() == World.Environment.NORMAL).findFirst().ifPresent(hub -> event.setRespawnLocation(hub.getSpawnLocation()));
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVoid(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isIslandWorld(player.getWorld()) && event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true); player.setFallDistance(0);
            var island = islands.at(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
            if (island.isPresent() && canEnter(player, landing(island.get()))) home(player, island.get().owner()); else returnToHub(player);
        }
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isIslandWorld(player.getWorld())) {
            var i = islands.at(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4);
            if (!player.hasPermission("nerdsmp.claim.bypass") && (i.isEmpty() || !i.get().member(account(player)))) event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGenerator(BlockFormEvent event) {
        if (!isIslandWorld(event.getBlock().getWorld()) || event instanceof EntityBlockFormEvent) return;
        Material current = event.getBlock().getType(), formed = event.getNewState().getType();
        if ((current != Material.LAVA && current != Material.WATER) || (formed != Material.COBBLESTONE && formed != Material.STONE)) return;
        var island = islands.at(event.getBlock().getX() >> 4, event.getBlock().getZ() >> 4);
        if (island.isEmpty()) { event.setCancelled(true); return; }
        var progress = development.progress(island.get().owner());
        int stage = resources.effectiveStage(island.get().owner());
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Material rolled = GeneratorLoot.rollResources(stage, random.nextInt(100));
        if (rolled == Material.COBBLESTONE && random.nextInt(100) < 10 * progress.rank(IslandTalent.PROSPECTING) + IslandMilestone.stoneRerollPercent(progress.level()))
            rolled = GeneratorLoot.rollResources(stage, random.nextInt(100));
        if (rolled != Material.COBBLESTONE && random.nextInt(100) < 10 * progress.rank(IslandTalent.RICH_SEAMS) + 5 * progress.rank(IslandTalent.ORE_MASTERY)
            + IslandMilestone.oreRerollPercent(progress.level()))
            rolled = GeneratorLoot.rarer(rolled, GeneratorLoot.rollResources(stage, random.nextInt(100)));
        event.getNewState().setType(rolled);
        plugin.resources().renewGenerator(event);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCropGrowth(BlockGrowEvent event) {
        if (!isIslandWorld(event.getBlock().getWorld())) return;
        islands.at(event.getBlock().getX() >> 4, event.getBlock().getZ() >> 4).ifPresent(i -> {
            var progress = development.progress(i.owner());
            int chance = progress.rank(IslandTalent.CULTIVATION) * 15 + IslandMilestone.cropGrowthPercent(progress.level());
            if (ThreadLocalRandom.current().nextInt(100) < chance) growCrop(event.getNewState(), 1);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (!isIslandWorld(event.getBlock().getWorld()) || event.getPlayer() == null) return;
        islands.at(event.getBlock().getX() >> 4, event.getBlock().getZ() >> 4).filter(i -> i.member(account(event.getPlayer()))).ifPresent(i -> {
            int rank = development.progress(i.owner()).rank(IslandTalent.NURSERY);
            event.getBlocks().stream().filter(s -> islands.at(s.getX() >> 4, s.getZ() >> 4).map(a -> a.id().equals(i.id())).orElse(false)).forEach(s -> growCrop(s, rank));
        });
    }
    private static void growCrop(org.bukkit.block.BlockState state, int stages) {
        if (!Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART, Material.COCOA).contains(state.getType())) return;
        if (state.getBlockData() instanceof org.bukkit.block.data.Ageable crop && stages > 0) {
            crop.setAge(Math.min(crop.getMaximumAge(), crop.getAge() + stages)); state.setBlockData(crop);
        }
    }
    private boolean landingBlock(Block b) {
        if (!isIslandWorld(b.getWorld())) return false;
        int slot = IslandGrid.slotAt(b.getX() >> 4, b.getZ() >> 4);
        return slot >= 0 && Math.abs(b.getX() - IslandGrid.blockX(slot)) <= 1 && Math.abs(b.getZ() - IslandGrid.blockZ(slot)) <= 1
            && b.getY() >= IslandGrid.SURFACE_Y && b.getY() <= IslandGrid.SURFACE_Y + 3;
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingPlace(BlockPlaceEvent event) {
        if (landingBlock(event.getBlock()) || event instanceof BlockMultiPlaceEvent multi && multi.getReplacedBlockStates().stream().anyMatch(s -> landingBlock(s.getBlock()))) {
            event.setCancelled(true);
            landingNotice(event.getPlayer());
        }
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingBreak(BlockBreakEvent event) { if (landingBlock(event.getBlock())) { event.setCancelled(true); landingNotice(event.getPlayer()); } }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingBucket(PlayerBucketEmptyEvent event) { if (landingBlock(event.getBlock())) { event.setCancelled(true); landingNotice(event.getPlayer()); } }
    private void landingNotice(Player player) {
        Chat.notice(player, "Keep the 3 by 3 island landing clear. Build and place machines at least 2 blocks from its center.");
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingFlow(BlockFromToEvent event) { if (landingBlock(event.getToBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingPiston(BlockPistonExtendEvent event) { if (landingBlock(event.getBlock().getRelative(event.getDirection())) || event.getBlocks().stream().anyMatch(b -> landingBlock(b) || landingBlock(b.getRelative(event.getDirection())))) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingRetract(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(b -> landingBlock(b) || landingBlock(b.getRelative(event.getDirection().getOppositeFace())))) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingChange(EntityChangeBlockEvent event) { if (landingBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingDispense(BlockDispenseEvent event) {
        if (event.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional direction
            && landingBlock(event.getBlock().getRelative(direction.getFacing()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingGrow(BlockGrowEvent event) { if (landingBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingSpread(BlockSpreadEvent event) { if (landingBlock(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingTree(org.bukkit.event.world.StructureGrowEvent event) { event.getBlocks().removeIf(s -> landingBlock(s.getBlock())); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onLandingFertilize(BlockFertilizeEvent event) { event.getBlocks().removeIf(s -> landingBlock(s.getBlock())); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortalCreate(org.bukkit.event.world.PortalCreateEvent event) { if (isIslandWorld(event.getWorld())) event.setCancelled(true); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { travelling.remove(event.getPlayer().getUniqueId()); advancing.remove(event.getPlayer().getUniqueId()); claiming.remove(event.getPlayer().getUniqueId()); presentation.forget(event.getPlayer()); borders.forget(event.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLandingInteract(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
            || event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.BEDROCK || !landingBlock(event.getClickedBlock())) return;
        Player player = event.getPlayer();
        if (player.isSneaking() || account(player) == null) return;
        var island = islands.at(event.getClickedBlock().getX() >> 4, event.getClickedBlock().getZ() >> 4);
        if (island.isEmpty() || !island.get().canEnter(account(player))) return;
        event.setCancelled(true); open(player, island.get());
    }

    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElse(null); }
    /** The island this player belongs to (their own, or the first co-op they're in). */
    private AccountId ownIsland(AccountId account) {
        if (islands.owned(account).isPresent()) return account;
        return islands.homes(account).stream().findFirst().map(IslandService.Island::owner)
            .orElseThrow(() -> new IllegalArgumentException("Create an island first: /island create"));
    }
    private AccountId resolve(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null && account(online) != null) return account(online);
        try { AccountId id = AccountId.parse(input); if (plugin.playerData().knownAccounts().contains(id)) return id; } catch (IllegalArgumentException ignored) { }
        List<AccountId> matches = plugin.playerData().knownAccounts().stream().filter(id -> name(id).equalsIgnoreCase(input)).toList();
        if (matches.size() != 1) throw new IllegalArgumentException("Use a known player name or their account UUID.");
        return matches.getFirst();
    }
    private String name(AccountId id) { return plugin.playerData().read(id, yaml -> yaml.getString("identity.last-known-name", id.toString())); }
    private static void requireArgs(String[] args, String syntax) { if (args.length < 2) throw new UsageException(syntax); }
    private Page page(Player player, String title, int size) {
        Page page = new Page(account(player));
        page.inventory = Bukkit.createInventory(page, size, Chat.render("<dark_aqua><bold>" + Chat.escape(title) + "</bold></dark_aqua>"));
        MenuKit.frame(page.inventory);
        return page;
    }
    /** {@code hint} finishes "Click to …"; null leaves the hint off, for items that only explain a refusal. */
    private static ItemStack icon(Material material, String title, List<String> lore, String hint) {
        List<String> lines = wrapLore(lore).stream().map(line -> "<gray>" + Chat.escape(line) + "</gray>").toList();
        return MenuKit.item(material, "<aqua>" + Chat.escape(title) + "</aqua>", hint == null ? lines : MenuKit.withHint(lines, hint));
    }
    private static List<String> wrapLore(List<String> lines) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            while (line.length() > 46) {
                int split = line.lastIndexOf(' ', 46); if (split <= 0) split = 46;
                result.add(line.substring(0, split)); line = line.substring(split).stripLeading();
            }
            result.add(line);
        }
        return result;
    }
    private void button(Page page, int slot, Material material, String title, List<String> lore, String hint, Consumer<Player> action) {
        nav(page, slot, icon(material, title, lore, hint), action);
    }
    private void info(Page page, int slot, Material material, String title, List<String> lore) {
        page.inventory.setItem(slot, icon(material, title, lore, null)); page.actions.remove(slot);
    }
    private void nav(Page page, int slot, ItemStack item, Consumer<Player> action) {
        page.inventory.setItem(slot, item); page.actions.put(slot, action);
    }
    private void back(Page page, String parent, Consumer<Player> action) {
        nav(page, MenuKit.backSlot(page.inventory.getSize()), MenuKit.back(parent), action);
    }
    private void close(Page page) {
        nav(page, MenuKit.closeSlot(page.inventory.getSize()), MenuKit.close(), Player::closeInventory);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Page page)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || page.busy || !page.account.equals(account(player))) return;
        Consumer<Player> action = page.actions.get(event.getRawSlot());
        if (action == null || !event.isLeftClick() || event.isShiftClick()) return;
        page.busy = true;
        MenuKit.clickSound(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try { if (player.isOnline() && page.account.equals(account(player)) && player.getOpenInventory().getTopInventory() == page.inventory) action.accept(player); }
            catch (IllegalArgumentException error) { Chat.error(player, Chat.escape(error.getMessage())); }
            catch (RuntimeException error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Island menu action failed", error); Chat.error(player, "The island action failed. Please contact staff."); }
            finally { page.busy = false; }
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Page) event.setCancelled(true); }
    @Override public void close() {
        accessTask.cancel(); developmentTask.cancel(); perks.close(); guests.close(); contests.close(); ranking.close(); borders.close(); processingMachines.close(); automation.close(); presentation.close();
        try { development.close(); } catch (RuntimeException error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Island XP could not be flushed on shutdown", error); }
    }
    private static final class Page implements InventoryHolder {
        final AccountId account; final Map<Integer, Consumer<Player>> actions = new HashMap<>(); Inventory inventory; boolean busy;
        Page(AccountId account) { this.account = account; }
        @Override public Inventory getInventory() { return inventory; }
    }
}

package net.nerdsmp.paper.claim;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ClaimService;
import net.nerdsmp.api.service.ClaimService.ClaimResult;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.gui.MenuKit;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;

/** Player-facing territory controls. Every click revalidates its account and the service's authority. */
public final class HomesteadController implements Listener {
    private final NerdSmpPlugin plugin;
    private final ClaimService claims;

    public HomesteadController(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.claims = plugin.claims();
    }

    public boolean handle(CommandSender sender, String[] input) {
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        AccountId actor = account(player);
        if (actor == null) { Chat.accountLoading(player); return true; }
        ChunkKey here = chunk(player);
        AccountId context = read(claims.find(here)).map(ClaimService.ClaimView::owner).orElse(actor);
        String[] args = input;
        if (args.length > 0 && args[0].equalsIgnoreCase("home")) {
            context = args.length >= 2 ? resolve(args[1]) : null;
            if (context == null) { Chat.usage(player, "/claim home <owner> [" + String.join("|", HOME_ACTIONS) + "]"); return true; }
            args = Arrays.copyOfRange(args, 2, args.length);
            if (args.length == 0) { overview(player, context); return true; }
            if (!HOME_ACTIONS.contains(args[0].toLowerCase(Locale.ROOT))) {
                Chat.error(player, "That command uses the plot you are standing in. Run it without /claim home."); return true;
            }
        }
        if (args.length == 0) { open(player); return true; }
        AccountId owner = context;
        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "info" -> openAt(player, here);
                case "map" -> map(player, owner);
                case "mine" -> homes(player, false, 0, owner);
                case "directory" -> homes(player, true, 0, owner);
                case "members" -> members(player, owner, 0);
                case "plots", "list" -> plots(player, owner, 0);
                case "flags" -> flags(player, here, false);
                case "invitations" -> invitations(player, 0, owner);
                case "applications" -> applications(player, owner, 0);
                case "history" -> history(player, owner, 0);
                case "access" -> {
                    AccountId target = args.length == 2 ? resolve(args[1]) : actor;
                    if (target == null) Chat.usage(player, "/claim access [player]");
                    else access(player, here, target);
                }
                case "create" -> {
                    AccountId target = args.length > 1 ? resolve(args[1]) : actor;
                    if (target == null) Chat.error(player, "That player hasn't joined yet, or the name is ambiguous.");
                    else result(player, claims.createClaimFor(actor, target, here));
                }
                case "delete" -> releaseConfirmation(player, here);
                case "name", "charter" -> {
                    String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    result(player, action.equals("charter") ? claims.renameHomestead(actor, text) : claims.setName(actor, here, text));
                }
                case "trust", "trustall" -> {
                    AccountId target = args.length == 3 ? resolve(args[1]) : null;
                    if (target != null && args[2].equalsIgnoreCase("inherit") && action.equals("trust")) {
                        var plot = read(claims.find(here));
                        if (plot.isEmpty()) Chat.error(player, "Stand in a claimed plot first.");
                        else confirmPlot(player, plot.get(), "Inherit access?", "Remove the local role for " + name(target) + ".",
                            () -> result(player, claims.resetTrust(actor, here, target)));
                        break;
                    }
                    ClaimRole role = args.length == 3 ? role(args[2]) : null;
                    if (target == null || role == null || role == ClaimRole.OWNER) Chat.usage(player, "/claim " + action + " <player> <none|build|container|redstone|resident|manage" + (action.equals("trust") ? "|inherit>" : ">"));
                    else if (action.equals("trustall")) {
                        if (!owner.equals(actor)) Chat.error(player, "Only the homestead owner can assign shared roles.");
                        else confirm(player, owner, "Change shared role?", name(target) + " -> " + label(role) + ". Local roles stay unless removing a resident.",
                            () -> result(player, claims.setTrustAll(actor, target, role)));
                    } else result(player, claims.setTrust(actor, here, target, role));
                }
                case "invite" -> inviteCommand(player, actor, owner, args);
                case "accept", "deny", "decline" -> {
                    if (args.length == 1) invitations(player, 0, owner);
                    else {
                        AccountId invitedOwner = resolve(args[1]);
                        if (invitedOwner == null) Chat.error(player, "No matching owner. Check " + Chat.command("claim invitations") + ".");
                        else result(player, action.equals("accept") ? claims.acceptInvite(actor, invitedOwner, Instant.now())
                            : claims.cancelInvite(actor, invitedOwner, actor));
                    }
                }
                case "leave" -> {
                    AccountId leaveOwner = args.length == 2 ? resolve(args[1]) : owner;
                    if (leaveOwner == null || leaveOwner.equals(actor)) { Chat.usage(player, "/claim leave <owner>"); Chat.info(player, "Owners keep their land."); }
                    else confirm(player, leaveOwner, "Leave homestead?", "Your shared and local access will be removed.",
                        () -> result(player, claims.leaveHomestead(actor, leaveOwner)));
                }
                case "flag" -> {
                    ClaimFlag flag = args.length >= 3 ? flag(args[1]) : null;
                    if (flag == null || !Set.of("on", "off", "inherit").contains(args[2].toLowerCase(Locale.ROOT)) || args.length > 4) {
                        Chat.usage(player, "/claim flag <flag> <on|off|inherit> [all]"); Chat.info(player, "Inherit is plot-only."); break;
                    }
                    boolean all = args.length == 4 && args[3].equalsIgnoreCase("all");
                    if (args[2].equalsIgnoreCase("inherit")) {
                        var found = read(claims.find(here));
                        if (all || found.isEmpty()) Chat.error(player, "Inherit applies to one claimed plot.");
                        else confirmPlot(player, found.get(), "Inherit this rule?", label(flag) + " will follow homestead defaults.",
                            () -> result(player, claims.resetFlag(actor, here, flag)));
                    } else result(player, all ? claims.setFlagAll(actor, owner, flag, args[2].equalsIgnoreCase("on"))
                        : claims.setFlag(actor, here, flag, args[2].equalsIgnoreCase("on")));
                }
                case "plot" -> plotCommand(player, actor, here, args);
                case "bulletin" -> {
                    if (args.length == 1) bulletin(player, owner);
                    else result(player, claims.setBulletin(actor, owner, args.length == 2 && args[1].equalsIgnoreCase("clear")
                        ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length))));
                }
                case "publish", "recruiting" -> {
                    if (args.length != 2 || !Set.of("on", "off").contains(args[1].toLowerCase(Locale.ROOT))) Chat.usage(player, "/claim " + action + " <on|off>");
                    else result(player, action.equals("publish") ? claims.setListed(actor, owner, args[1].equalsIgnoreCase("on"))
                        : claims.setRecruiting(actor, owner, args[1].equalsIgnoreCase("on")));
                }
                case "apply", "withdraw" -> {
                    AccountId destination = args.length == 2 ? resolve(args[1]) : null;
                    if (destination == null) { Chat.usage(player, "/claim " + action + " <owner>"); Chat.info(player, "Browse settlements with " + Chat.command("claim directory") + "."); }
                    else result(player, action.equals("apply") ? claims.applyTo(actor, destination, Instant.now()) : claims.withdrawApplication(actor, destination));
                }
                case "show" -> boundary(player, here);
                default -> help(player);
            }
        } catch (RuntimeException exception) { failure(player, exception); }
        return true;
    }

    private void inviteCommand(Player player, AccountId actor, AccountId owner, String[] args) {
        if (args.length == 3 && (args[1].equalsIgnoreCase("deny") || args[1].equalsIgnoreCase("decline"))) {
            AccountId invitedOwner = resolve(args[2]);
            if (invitedOwner == null) Chat.error(player, "No matching owner. Check " + Chat.command("claim invitations") + ".");
            else result(player, claims.cancelInvite(actor, invitedOwner, actor));
        } else if (args.length == 3 && args[1].equalsIgnoreCase("cancel")) {
            AccountId target = resolve(args[2]);
            if (target == null) Chat.error(player, "That player hasn't joined yet.");
            else result(player, claims.cancelInvite(actor, owner, target));
        } else {
            AccountId target = args.length == 2 ? resolve(args[1]) : null;
            if (target == null) { Chat.usage(player, "/claim invite <player>|cancel <player>|deny <owner>"); return; }
            ClaimResult outcome = read(claims.invite(actor, owner, target, Instant.now()));
            result(player, outcome);
            if (outcome == ClaimResult.INVITED) plugin.sessions().player(target).map(Bukkit::getPlayer).ifPresent(invited ->
                Chat.notice(invited, "You were invited to <white>" + Chat.escape(read(claims.homestead(owner)).name()) + "</white>. Answer in "
                    + Chat.command("claim invitations") + " within five minutes."));
        }
    }

    private void plotCommand(Player player, AccountId actor, ChunkKey key, String[] args) {
        var found = read(claims.find(key));
        if (found.isEmpty()) { Chat.error(player, "Stand in a claimed plot first."); return; }
        var plot = found.get();
        if (args.length == 1) { plotDetail(player, plot); return; }
        if (args.length == 3 && args[1].equalsIgnoreCase("type")) {
            PlotType type;
            try { type = PlotType.valueOf(args[2].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { Chat.error(player, "Choose commons, residential, farm, workshop, or preserve."); return; }
            typeConfirmation(player, plot, type);
        } else if (args.length == 3 && args[1].equalsIgnoreCase("assign")) {
            AccountId target = resolve(args[2]);
            if (target == null) Chat.error(player, "Choose a resident who has joined this homestead.");
            else stewardConfirmation(player, plot, target);
        } else if (args.length == 2 && args[1].equalsIgnoreCase("unassign")) stewardConfirmation(player, plot, null);
        else Chat.usage(player, "/claim plot [type <type>|assign <resident>|unassign]");
    }

    public void open(Player player) { openAt(player, chunk(player)); }

    private void openAt(Player player, ChunkKey location) {
        AccountId actor = account(player);
        if (actor == null) return;
        var plot = read(claims.find(location));
        if (plot.isPresent()) { plotDetail(player, plot.get()); return; }
        Page page = page(player, "Unclaimed land", actor);
        info(page, 13, Material.GRASS_BLOCK, claims.isReserved(location) ? "Server reserve" : "Wilderness",
            List.of(worldName(location) + " | " + location.x() + ", " + location.z(), "No plot is selected."));
        control(page, 22, Material.LIME_CONCRETE, "Claim this chunk", List.of("Uses your own available capacity.",
            "Stand in this chunk to claim it.", "Click to claim it."), !claims.isReserved(location), () -> {
                if (!chunk(player).equals(location)) Chat.error(player, "Return to that chunk before claiming it.");
                else result(player, claims.createClaim(actor, location));
                open(player);
            });
        button(page, 29, Material.MAP, "Local map", List.of("Inspect neighboring land.", "Click to open."), () -> map(player, actor));
        button(page, 31, Material.OAK_DOOR, "My homesteads", List.of("Your own home and shared memberships.", "Click to open."), () -> homes(player, false, 0, actor));
        button(page, 33, Material.COMPASS, "Settlement directory", List.of("Meet your next neighbors.", "Click to browse."), () -> homes(player, true, 0, actor));
        topFooter(page, player);
        show(player, page);
    }

    private void overview(Player player, AccountId owner) {
        AccountId actor = account(player);
        if (actor == null) return;
        var home = read(claims.homestead(owner));
        Page page = page(player, home.name(), owner);
        boolean member = HomesteadMenuPolicy.member(actor, home);
        boolean manager = HomesteadMenuPolicy.manager(actor, home);
        info(page, 4, Material.BELL, home.name(), List.of("Owner: " + name(owner), home.chunks() + " / " + home.capacity() + " chunks",
            home.members().size() + " residents | No upkeep", home.recruiting() && home.listed() ? "Accepting applications" : "Invitation only"));
        control(page, 19, Material.FILLED_MAP, "Plots", List.of("Homes, farms and common ground. Residents only.", "Click to open."), member, () -> plots(player, owner, 0));
        button(page, 21, Material.PLAYER_HEAD, "Residents", List.of("People and their shared roles.", "Click to open."), () -> members(player, owner, 0));
        List<String> notice = new ArrayList<>(wrap(home.bulletin().isBlank() ? "No notice posted." : home.bulletin()));
        notice.add("Click to open the notice board.");
        button(page, 23, Material.WRITABLE_BOOK, "Notice board", notice, () -> bulletin(player, owner));
        control(page, 25, Material.BOOK, "History", List.of("Latest 100 changes. Residents only.", "Click to open."), member, () -> history(player, owner, 0));
        button(page, 28, Material.COMPASS, "Find a settlement", List.of("Published homes and recruitment.", "Click to browse."), () -> homes(player, true, 0, owner));
        button(page, 30, Material.OAK_DOOR, "My homesteads", List.of("Choose which home to manage.", "Click to open."), () -> homes(player, false, 0, owner));
        button(page, 32, Material.PAPER, "Invitations", List.of("Accept or deny your invitations.", "Click to open."), () -> invitations(player, 0, owner));
        if (member) control(page, 34, Material.FEATHER, "Settlement chat", List.of("Owner and current residents only.", "Click to talk in settlement chat."), true,
            () -> { player.closeInventory(); player.performCommand("claim chat " + owner); });
        else control(page, 34, Material.WRITABLE_BOOK, "Apply to join", List.of("A manager reviews your request.", "No build access until approved.", "Click to apply."),
            home.listed() && home.recruiting() && home.chunks() > 0, () -> {
                result(player, claims.applyTo(actor, owner, Instant.now())); overview(player, owner);
            });
        control(page, 39, Material.COMPARATOR, "Recruitment and notices", List.of("Publication, applications and bulletin.", "Owners and shared managers.", "Click to open."), manager, () -> bulletin(player, owner));
        if (owner.equals(actor)) {
            long cost = plugin.getConfig().getLong("economy.claim-expansion-cost", 500L);
            nav(page, 41, MenuKit.item(Material.OAK_FENCE, "<gold>Expand capacity</gold>", MenuKit.withHint(List.of(
                "<gray>Cost</gray> " + Chat.marksTag(cost), "<gray>One additional chunk. No recurring upkeep.</gray>"), "review")),
                () -> confirm(player, owner, "Expand capacity?", List.of("<gray>Cost</gray> " + Chat.marksTag(cost), "<gray>Adds one chunk to your own capacity.</gray>"),
                    () -> player.performCommand("claim expand")));
        } else if (member) button(page, 41, Material.RED_BED, "Leave homestead", List.of("Review before removing your access.", "Click to review."), () -> confirm(player, owner,
            "Leave homestead?", "Your stewardship and local access will be removed.", () -> { result(player, claims.leaveHomestead(actor, owner)); overview(player, owner); }));
        topFooter(page, player);
        show(player, page);
    }

    private void map(Player player, AccountId owner) {
        if (account(player) == null) return;
        Page page = page(player, "Territory map", owner);
        ChunkKey center = chunk(player);
        AccountId actor = account(player);
        for (int dz = -2; dz <= 2; dz++) for (int dx = -3; dx <= 3; dx++) {
            ChunkKey key = new ChunkKey(center.worldId(), center.x() + dx, center.z() + dz);
            var plot = read(claims.find(key));
            boolean own = plot.isPresent() && plot.get().owner().equals(actor);
            boolean friend = plot.isPresent() && read(claims.roleAt(actor, key)) != ClaimRole.NONE;
            Material color = claims.isReserved(key) ? Material.GRAY_STAINED_GLASS_PANE
                : plot.isEmpty() ? Material.WHITE_STAINED_GLASS_PANE : own ? Material.LIME_STAINED_GLASS_PANE
                : friend ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
            String title = dx == 0 && dz == 0 ? "You are here" : key.x() + ", " + key.z();
            button(page, (dz + 2) * 9 + dx + 4, color, title,
                List.of(claims.isReserved(key) ? "Server reserve" : plot.map(p -> p.name() + " | " + name(p.owner())).orElse("Wilderness"),
                    "Click to inspect. Map does not claim remotely."), () -> openAt(player, key));
        }
        footer(page, player);
        show(player, page);
    }

    private void homes(Player player, boolean directory, int requested, AccountId context) {
        var entries = read(directory ? claims.directory() : claims.settlementsFor(account(player)));
        int index = HomesteadMenuPolicy.page(requested, entries.size());
        Page page = page(player, directory ? "Settlement directory" : "My homesteads", context);
        info(page, 4, Material.COMPASS, directory ? "Published settlements" : "Choose a home",
            List.of("Page " + (index + 1), "Membership does not transfer land or Marks."));
        for (int n = 0; n < 21 && index * 21 + n < entries.size(); n++) {
            var home = entries.get(index * 21 + n);
            button(page, HomesteadMenuPolicy.slot(n), Material.BELL, home.name(), List.of("Owner: " + name(home.owner()),
                home.chunks() + " chunks | " + home.members().size() + " residents",
                home.recruiting() && home.listed() ? "Accepting applications" : "Invitation only", "Click to open."), () -> overview(player, home.owner()));
        }
        if (entries.isEmpty()) info(page, 22, Material.GLASS_BOTTLE, "No homes to show", List.of(directory
            ? "Owners can publish their settlement from its notice board." : "Claim land or accept a friend's invitation."));
        pagination(page, index, entries.size(), p -> homes(player, directory, p, context));
        footer(page, player); show(player, page);
    }

    private void plots(Player player, AccountId owner, int requested) {
        var home = read(claims.homestead(owner));
        if (!HomesteadMenuPolicy.member(account(player), home)) {
            Chat.error(player, "Only residents can browse the full plot register. Inspect nearby land with " + Chat.command("claim map") + "."); return;
        }
        var entries = read(claims.listClaims(owner));
        int index = HomesteadMenuPolicy.page(requested, entries.size());
        Page page = page(player, "Plots | " + (index + 1), owner);
        info(page, 4, Material.MAP, home.name(), List.of("Click a plot to inspect it. No teleport or remote claiming."));
        for (int n = 0; n < 21 && index * 21 + n < entries.size(); n++) {
            var plot = entries.get(index * 21 + n);
            button(page, HomesteadMenuPolicy.slot(n), plot.type().privateAccess() ? Material.OAK_DOOR : Material.GRASS_BLOCK,
                plot.name(), List.of(label(plot.type()), worldName(plot.chunk()) + " | " + plot.chunk().x() + ", " + plot.chunk().z(),
                    "Steward: " + (plot.steward() == null ? "Unassigned" : name(plot.steward())), "Click to inspect."), () -> openAt(player, plot.chunk()));
        }
        if (entries.isEmpty()) info(page, 22, Material.GLASS_BOTTLE, "No land yet", List.of("Stand on suitable land and use /claim create."));
        pagination(page, index, entries.size(), p -> plots(player, owner, p));
        footer(page, player); show(player, page);
    }

    private void plotDetail(Player player, ClaimService.ClaimView plot) {
        AccountId actor = account(player);
        Page page = page(player, "Plot | " + plot.name(), plot.owner());
        var home = read(claims.homestead(plot.owner()));
        info(page, 4, Material.GRASS_BLOCK, plot.name(), List.of(home.name() + " | Owner: " + name(plot.owner()), label(plot.type()),
            worldName(plot.chunk()) + " | " + plot.chunk().x() + ", " + plot.chunk().z(),
            "Steward: " + (plot.steward() == null ? "Unassigned" : name(plot.steward()))));
        button(page, 20, Material.SPYGLASS, "Your access", List.of("See your role and where it comes from.", "Click to open."), () -> access(player, plot.chunk(), actor));
        button(page, 22, Material.COMPARATOR, "Plot rules", List.of("Explicit rules and inherited defaults.", "Click to open."), () -> flags(player, plot.chunk(), false));
        control(page, 24, Material.PLAYER_HEAD, "Local guests", List.of("Plot-only permissions.", "Owner or assigned steward.", "Click to open."),
            HomesteadMenuPolicy.guests(actor, plot), () -> guests(player, plot, 0));
        control(page, 29, Material.OAK_SIGN, "Land-use preset", List.of("Review before replacing local rule overrides.", "Click to choose a preset."),
            actor.equals(plot.owner()) || !plot.type().privateAccess() && HomesteadMenuPolicy.manager(actor, home), () -> types(player, plot));
        control(page, 31, Material.RED_BED, "Assign a home", List.of("Owner only. Select a resident as steward.", "Old local guests are cleared.", "Click to choose a resident."),
            actor.equals(plot.owner()), () -> stewards(player, plot, 0));
        control(page, 33, Material.RED_CONCRETE, "Release plot", List.of("Owner only. Removes protection after review.", "Click to review."),
            actor.equals(plot.owner()), () -> releaseConfirmation(player, plot.chunk()));
        button(page, 40, Material.GLOWSTONE_DUST, "Show boundary", List.of("Visible in the plot's dimension.", "Click to show it."), () -> boundary(player, plot.chunk()));
        footer(page, player); show(player, page);
    }

    private void members(Player player, AccountId owner, int requestedPage) {
        if (account(player) == null) return;
        var home = read(claims.homestead(owner));
        List<Map.Entry<AccountId, ClaimRole>> residents = home.members().entrySet().stream()
            .sorted(Comparator.comparing(entry -> name(entry.getKey()).toLowerCase(Locale.ROOT))).toList();
        int index = Math.max(0, Math.min(requestedPage, Math.max(0, (residents.size() - 1) / 21)));
        Page page = page(player, "Residents | " + (index + 1), owner);
        button(page, 4, Material.BELL, home.name(), List.of("Owner: " + name(owner),
            "Invite nearby: /claim invite <player>", "Whole homestead: /claim trustall <player> <role>",
            "One plot: /claim trust <player> <role>", "Rename your homestead: /claim charter <name>"), () -> {});
        for (int n = 0; n < 21 && index * 21 + n < residents.size(); n++) {
            var member = residents.get(index * 21 + n);
            control(page, 10 + n / 7 * 9 + n % 7, Material.PLAYER_HEAD, name(member.getKey()),
                List.of("Role: " + label(member.getValue()), "Applies to current and future chunks.",
                    "Plot-specific access can override this role.", "Click to change this membership."),
                owner.equals(account(player)), () -> roles(player, owner, member.getKey()));
        }
        pagination(page, index, residents.size(), p -> members(player, owner, p));
        control(page, 40, Material.PAPER, "Invite a resident", List.of("Select a player who has joined the server.", "Click to choose a player."),
            HomesteadMenuPolicy.manager(account(player), home), () -> inviteCandidates(player, owner, 0));
        footer(page, player);
        show(player, page);
    }

    private void inviteCandidates(Player player, AccountId owner, int requested) {
        var home = read(claims.homestead(owner));
        AccountId actor = account(player);
        if (!HomesteadMenuPolicy.manager(actor, home)) { Chat.error(player, "Only the owner or a shared manager can invite residents."); return; }
        var candidates = plugin.playerData().knownAccounts().stream().filter(id -> !id.equals(owner) && !home.members().containsKey(id))
            .sorted(Comparator.comparing(this::name)).toList();
        int index = HomesteadMenuPolicy.page(requested, candidates.size());
        Page page = page(player, "Invite a resident", owner);
        info(page, 4, Material.PAPER, home.name(), List.of("Invitations last five minutes.", "Players can accept from /claim invitations."));
        for (int n = 0; n < 21 && index * 21 + n < candidates.size(); n++) {
            AccountId target = candidates.get(index * 21 + n);
            button(page, HomesteadMenuPolicy.slot(n), Material.PLAYER_HEAD, name(target), List.of("Invite as a resident. No immediate access.", "Click to invite."), () -> {
                inviteCommand(player, actor, owner, new String[]{"invite", target.toString()}); members(player, owner, 0);
            });
        }
        if (candidates.isEmpty()) info(page, 22, Material.GLASS_BOTTLE, "No players to invite", List.of("New players appear here after joining the server."));
        pagination(page, index, candidates.size(), p -> inviteCandidates(player, owner, p)); footer(page, player); show(player, page);
    }

    private void roles(Player player, AccountId owner, AccountId target) {
        if (!owner.equals(account(player))) { Chat.error(player, "Only the owner can assign roles."); return; }
        Page page = page(player, "Role | " + name(target), owner);
        ClaimRole expectedRole = read(claims.homestead(owner)).members().get(target);
        ClaimRole[] roles = {ClaimRole.BUILD, ClaimRole.CONTAINER, ClaimRole.REDSTONE, ClaimRole.RESIDENT, ClaimRole.MANAGE};
        String[] details = {"Build and landscape; no storage access.", "Use storage; cannot break containers.",
            "Operate controls; cannot build or open storage.", "Build, storage, and redstone access.",
            "Resident access, flags, invitations, and territory expansion."};
        for (int i = 0; i < roles.length; i++) {
            ClaimRole role = roles[i];
            button(page, 20 + i, Material.NAME_TAG, label(role), List.of(details[i], "Does not grant ownership or release rights.", "Click to review."),
                () -> confirm(player, owner, "Change shared role?", name(target) + " becomes " + label(role) + ". Local overrides stay.",
                    () -> {
                        if (!Objects.equals(expectedRole, read(claims.homestead(owner)).members().get(target))) Chat.error(player, "This membership changed. Review it again.");
                        else result(player, claims.setTrustAll(account(player), target, role));
                        members(player, owner, 0);
                    }));
        }
        button(page, 31, Material.TNT, "Remove resident", List.of("Remove all shared and plot-specific access.", "Click to review."),
            () -> confirm(player, owner, "Remove resident?", name(target) + " loses shared access, local trust and stewardship.",
                () -> {
                    if (!Objects.equals(expectedRole, read(claims.homestead(owner)).members().get(target))) Chat.error(player, "This membership changed. Review it again.");
                    else result(player, claims.setTrustAll(account(player), target, ClaimRole.NONE));
                    members(player, owner, 0);
                }));
        footer(page, player);
        show(player, page);
    }

    private void flags(Player player, ChunkKey key, boolean all) {
        AccountId actor = account(player);
        if (actor == null) return;
        var found = read(claims.find(key));
        if (found.isEmpty()) { Chat.error(player, "Stand in a claimed chunk to manage its rules."); return; }
        var plot = found.get();
        var home = read(claims.homestead(plot.owner()));
        Set<ClaimFlag> enabledFlags = all ? home.flags() : plot.flags();
        Page page = page(player, all ? "Homestead defaults" : "Plot rules", plot.owner());
        button(page, 4, Material.SPYGLASS, all ? "Scope: entire homestead" : "Scope: this plot",
            List.of(all ? "Explicit local overrides are preserved." : plot.name() + " | " + key.x() + ", " + key.z(),
                "Residential plots always disable public access.", "Click to change scope."),
            () -> flags(player, key, !all));
        int i = 0;
        for (ClaimFlag flag : ClaimFlag.values()) {
            int slot = 10 + i / 7 * 9 + i % 7;
            i++;
            boolean enabled = enabledFlags.contains(flag);
            boolean canEdit = all ? HomesteadMenuPolicy.manager(actor, home) : read(claims.roleAt(actor, key)).allows(ClaimRole.MANAGE);
            boolean suppressed = !all && plot.type().privateAccess() && flag.name().startsWith("PUBLIC_");
            control(page, slot, enabled ? Material.LIME_DYE : Material.GRAY_DYE, label(flag) + (enabled ? " | ON" : " | OFF"),
                List.of(description(flag), suppressed ? "Forced off: private residence" : all ? "Homestead default"
                    : plot.flagOverrides().containsKey(flag) ? "Source: explicit plot rule" : "Source: inherited default", "Click to see options."),
                canEdit && !suppressed, () -> flagOptions(player, plot, flag, all));
        }
        footer(page, player);
        show(player, page);
    }

    private void flagOptions(Player player, ClaimService.ClaimView plot, ClaimFlag flag, boolean all) {
        Page page = page(player, label(flag), plot.owner());
        AccountId actor = account(player);
        for (boolean enabled : new boolean[]{true, false}) {
            button(page, enabled ? 20 : 22, enabled ? Material.LIME_DYE : Material.GRAY_DYE, enabled ? "On" : "Off",
                List.of(description(flag), all ? "Changes defaults, not explicit plot rules." : "Creates an explicit rule for this plot.", "Click to review."),
                () -> confirmPlot(player, plot, "Change land rule?", (all ? "Homestead default: " : "This plot: ") + label(flag) + " = " + enabled, () -> {
                    result(player, all ? claims.setFlagAll(actor, plot.owner(), flag, enabled) : claims.setFlag(actor, plot.chunk(), flag, enabled));
                    flags(player, plot.chunk(), all);
                }));
        }
        if (!all) button(page, 24, Material.STRING, "Inherit", List.of("Remove this override and follow the homestead default.", "Click to review."),
            () -> confirmPlot(player, plot, "Inherit land rule?", label(flag) + " will follow future default changes.", () -> {
                result(player, claims.resetFlag(actor, plot.chunk(), flag)); flags(player, plot.chunk(), false);
            }));
        footer(page, player); show(player, page);
    }

    private void invitations(Player player, int requested, AccountId context) {
        AccountId actor = account(player);
        if (actor == null) return;
        Page page = page(player, "Homestead invitations", context);
        List<AccountId> invitations = read(claims.invitations(actor, Instant.now()));
        int index = HomesteadMenuPolicy.page(requested, invitations.size());
        for (int i = 0; i < 21 && index * 21 + i < invitations.size(); i++) {
            AccountId owner = invitations.get(index * 21 + i);
            button(page, 10 + i / 7 * 9 + i % 7, Material.PAPER, read(claims.homestead(owner)).name(),
                List.of("Invited by: " + name(owner), "Join as a resident. No Marks or land transfer.", "Click to answer."),
                () -> invitation(player, owner));
        }
        if (invitations.isEmpty()) info(page, 22, Material.GLASS_BOTTLE, "No pending invitations",
            List.of("An owner or manager can invite you.", "Invitations expire after five minutes."));
        pagination(page, index, invitations.size(), p -> invitations(player, p, context));
        footer(page, player);
        show(player, page);
    }

    private void invitation(Player player, AccountId owner) {
        Page page = page(player, "Join " + read(claims.homestead(owner)).name(), owner);
        AccountId actor = account(player);
        button(page, 20, Material.LIME_CONCRETE, "Accept invitation", List.of("Join as a resident. Keep your own land.", "Click to accept."), () -> {
            result(player, claims.acceptInvite(actor, owner, Instant.now())); overview(player, owner);
        });
        button(page, 24, Material.RED_CONCRETE, "Deny invitation", List.of("No access or money changes.", "Click to deny."), () -> {
            result(player, claims.cancelInvite(actor, owner, actor)); invitations(player, 0, owner);
        });
        footer(page, player); show(player, page);
    }

    private void releaseConfirmation(Player player, ChunkKey key) {
        AccountId actor = account(player);
        var found = read(claims.find(key));
        if (actor == null || found.isEmpty() || !found.get().owner().equals(actor)) {
            Chat.error(player, "Only the owner can release this plot."); return;
        }
        confirmPlot(player, found.get(), "Release this plot?", worldName(key) + " | " + key.x() + ", " + key.z() + " loses protection.", () -> {
            result(player, claims.deleteClaim(actor, key)); overview(player, found.get().owner());
        });
    }

    private void confirm(Player player, AccountId owner, String title, String detail, Runnable action) {
        confirm(player, owner, title, wrap(detail).stream().map(line -> "<gray>" + Chat.escape(line) + "</gray>").toList(), action);
    }

    /** {@code detail} is MiniMessage markup, shown on the Confirm button. */
    private void confirm(Player player, AccountId owner, String title, List<String> detail, Runnable action) {
        Page page = page(player, title, owner);
        nav(page, MenuKit.CONFIRM_CANCEL_SLOT, MenuKit.cancel(), () -> overview(player, owner));
        nav(page, MenuKit.CONFIRM_SLOT, MenuKit.confirm(MenuKit.withHint(detail, "confirm")), () -> { player.closeInventory(); action.run(); });
        show(player, page);
    }

    private void confirmPlot(Player player, ClaimService.ClaimView expected, String title, String detail, Runnable action) {
        confirm(player, expected.owner(), title, detail, () -> {
            var current = read(claims.find(expected.chunk()));
            if (current.isEmpty() || !HomesteadMenuPolicy.unchanged(expected, current.get())) {
                Chat.error(player, "This plot changed since you opened it. Review it again."); return;
            }
            action.run();
        });
    }

    private void types(Player player, ClaimService.ClaimView plot) {
        Page page = page(player, "Land-use presets", plot.owner());
        int n = 0;
        for (PlotType type : PlotType.values()) {
            List<String> details = switch (type) {
                case COMMONS -> List.of("Shared membership. Inherit all town defaults.");
                case RESIDENTIAL -> List.of("Private home. Owner, steward and local guests only.");
                case FARM -> List.of("Public crops and animal care. Storage stays closed.");
                case WORKSHOP -> List.of("Public stations and controls. Storage stays closed.");
                case PRESERVE -> List.of("No public activity or natural hostile spawning.");
            };
            boolean ownerOnly = type.privateAccess() || plot.type().privateAccess();
            control(page, 20 + n++, Material.OAK_SIGN, label(type), List.of(details.getFirst(), "Click to review."), !ownerOnly || plot.owner().equals(account(player)),
                () -> typeConfirmation(player, plot, type));
        }
        info(page, 40, Material.PAPER, "Preset scope", List.of("Replaces this plot's flag overrides.", "Review the confirmation before applying."));
        footer(page, player); show(player, page);
    }

    private void typeConfirmation(Player player, ClaimService.ClaimView plot, PlotType type) {
        confirmPlot(player, plot, "Apply " + label(type) + "?", "Replaces local rule overrides. "
            + (type.privateAccess() ? "This home will not inherit resident access." : plot.type().privateAccess()
                ? "Ends private housing; shared residents regain access." : "Membership roles stay unchanged."), () -> {
                    result(player, claims.setPlotType(account(player), plot.chunk(), type)); openAt(player, plot.chunk());
                });
    }

    private void stewards(Player player, ClaimService.ClaimView plot, int requested) {
        if (!plot.owner().equals(account(player))) { Chat.error(player, "Only the owner can assign housing."); return; }
        var members = read(claims.homestead(plot.owner())).members().keySet().stream().sorted(Comparator.comparing(this::name)).toList();
        int index = HomesteadMenuPolicy.page(requested, members.size());
        Page page = page(player, "Choose a steward", plot.owner());
        info(page, 4, Material.RED_BED, plot.name(), List.of("Assignment makes this plot Residential.", "Old local guests are removed."));
        for (int n = 0; n < 21 && index * 21 + n < members.size(); n++) {
            AccountId target = members.get(index * 21 + n);
            button(page, HomesteadMenuPolicy.slot(n), Material.PLAYER_HEAD, name(target), List.of("Assign this resident as steward.", "Click to review."),
                () -> stewardConfirmation(player, plot, target));
        }
        button(page, 40, Material.SHEARS, "Unassign housing", List.of("Home stays private under the owner.", "Clears the steward and old guests.", "Click to review."), () -> stewardConfirmation(player, plot, null));
        pagination(page, index, members.size(), p -> stewards(player, plot, p)); footer(page, player); show(player, page);
    }

    private void stewardConfirmation(Player player, ClaimService.ClaimView plot, AccountId target) {
        confirmPlot(player, plot, target == null ? "Unassign this home?" : "Assign this home?",
            "Clears local guests and makes the plot Residential. Steward: " + (target == null ? "none; owner only" : name(target)), () -> {
                result(player, claims.assignSteward(account(player), plot.chunk(), target)); openAt(player, plot.chunk());
            });
    }

    private void guests(Player player, ClaimService.ClaimView plot, int requested) {
        if (!HomesteadMenuPolicy.guests(account(player), plot)) { Chat.error(player, "Only the owner or residential steward can set local guests."); return; }
        var accounts = plugin.playerData().knownAccounts().stream().filter(id -> !id.equals(plot.owner()) && !id.equals(plot.steward()))
            .sorted(Comparator.comparing(this::name)).toList();
        int index = HomesteadMenuPolicy.page(requested, accounts.size());
        Page page = page(player, "Plot guests | " + (index + 1), plot.owner());
        info(page, 4, Material.PAPER, plot.name(), List.of("Choose a known player to review local access.", "Inherit removes an override; None denies access."));
        for (int n = 0; n < 21 && index * 21 + n < accounts.size(); n++) {
            AccountId target = accounts.get(index * 21 + n);
            ClaimRole local = plot.localMembers().get(target);
            button(page, HomesteadMenuPolicy.slot(n), Material.PLAYER_HEAD, name(target), List.of("Local role: " + (local == null ? "Inherited" : label(local)),
                "Effective: " + label(read(claims.roleAt(target, plot.chunk()))), "Click to change."), () -> guestRole(player, plot, target));
        }
        pagination(page, index, accounts.size(), p -> guests(player, plot, p)); footer(page, player); show(player, page);
    }

    private void guestRole(Player player, ClaimService.ClaimView plot, AccountId target) {
        Page page = page(player, "Guest | " + name(target), plot.owner());
        ClaimRole[] roles = {ClaimRole.NONE, ClaimRole.BUILD, ClaimRole.CONTAINER, ClaimRole.REDSTONE, ClaimRole.RESIDENT, ClaimRole.MANAGE};
        for (int i = 0; i < roles.length; i++) {
            ClaimRole role = roles[i];
            control(page, 19 + i, Material.NAME_TAG, label(role), List.of("Explicit permission on this plot only.", "Click to review."),
                role != ClaimRole.MANAGE || plot.owner().equals(account(player)), () -> confirmPlot(player, plot, "Change local access?",
                    name(target) + " -> " + label(role) + ".", () -> { result(player, claims.setTrust(account(player), plot.chunk(), target, role)); openAt(player, plot.chunk()); }));
        }
        button(page, 31, Material.STRING, "Inherit", List.of("Remove this plot-specific role.", "Private homes do not inherit shared membership.", "Click to review."),
            () -> confirmPlot(player, plot, "Inherit access?", "Remove the local role for " + name(target) + ".", () -> {
                result(player, claims.resetTrust(account(player), plot.chunk(), target)); openAt(player, plot.chunk());
            }));
        footer(page, player); show(player, page);
    }

    private void access(Player player, ChunkKey key, AccountId target) {
        var found = read(claims.find(key));
        if (found.isEmpty()) { Chat.info(player, claims.isReserved(key) ? "This land is reserved by the server." : "This is unclaimed land."); return; }
        var plot = found.get();
        Page page = page(player, "Access | " + name(target), plot.owner());
        ClaimRole role = read(claims.roleAt(target, key));
        String source = claims.isReserved(key) ? "Server reserve" : target.equals(plot.owner()) ? "Homestead ownership"
            : target.equals(plot.steward()) ? "Assigned housing steward" : plot.localMembers().containsKey(target) ? "Explicit local role"
            : plot.type().privateAccess() ? "Private home: shared membership does not apply" : "Homestead membership or visitor";
        info(page, 13, Material.SPYGLASS, label(role), List.of(source, "Public rules may separately permit visitor actions.", "Staff bypass is separate from this role."));
        ClaimRole[] permissions = {ClaimRole.BUILD, ClaimRole.CONTAINER, ClaimRole.REDSTONE, ClaimRole.MANAGE};
        for (int i = 0; i < permissions.length; i++) info(page, 20 + i, role.allows(permissions[i]) ? Material.LIME_DYE : Material.GRAY_DYE,
            label(permissions[i]), List.of(role.allows(permissions[i]) ? "Granted by this role" : "Not granted by this role"));
        button(page, 31, Material.COMPARATOR, "Visitor and environment rules", List.of("Inspect effective flags and inheritance.", "Click to open."), () -> flags(player, key, false));
        footer(page, player); show(player, page);
    }

    private void bulletin(Player player, AccountId owner) {
        var home = read(claims.homestead(owner));
        AccountId actor = account(player);
        boolean manager = HomesteadMenuPolicy.manager(actor, home);
        Page page = page(player, "Notice board", owner);
        info(page, 13, Material.WRITABLE_BOOK, home.name(), wrap(home.bulletin().isBlank() ? "No notice posted." : home.bulletin()));
        control(page, 20, Material.COMPASS, "Directory: " + (home.listed() ? "Published" : "Hidden"), List.of("Never grants access or teleportation.", "Click to change publication."), manager,
            () -> confirm(player, owner, "Change publication?", "Directory listing: " + !home.listed(), () -> { result(player, claims.setListed(actor, owner, !home.listed())); bulletin(player, owner); }));
        control(page, 22, Material.OAK_DOOR, "Recruitment: " + (home.recruiting() ? "Open" : "Closed"), List.of("Requires a published listing.", "Existing residents are not removed.",
            home.recruiting() ? "Click to close recruitment." : "Click to open recruitment."), manager,
            () -> { result(player, claims.setRecruiting(actor, owner, !home.recruiting())); bulletin(player, owner); });
        control(page, 24, Material.PAPER, "Applications", List.of("Review membership requests.", "Click to open."), manager, () -> applications(player, owner, 0));
        control(page, 31, Material.FEATHER, "Write a notice", List.of("160 characters. Plain text only.", "Click to get the command in chat."), manager,
            () -> { player.closeInventory(); Chat.usage(player, "/claim home " + commandName(owner) + " bulletin <notice>"); });
        control(page, 33, Material.BUCKET, "Clear notice", List.of("Remove the published bulletin.", "Click to review."), manager && !home.bulletin().isBlank(),
            () -> confirm(player, owner, "Clear notice?", "Remove this homestead's notice.", () -> { result(player, claims.setBulletin(actor, owner, "")); bulletin(player, owner); }));
        footer(page, player); show(player, page);
    }

    private void applications(Player player, AccountId owner, int requested) {
        AccountId actor = account(player);
        if (!HomesteadMenuPolicy.manager(actor, read(claims.homestead(owner)))) { Chat.error(player, "Owners and shared managers review applications."); return; }
        var entries = read(claims.applications(actor, owner, Instant.now()));
        int index = HomesteadMenuPolicy.page(requested, entries.size());
        Page page = page(player, "Applications | " + (index + 1), owner);
        info(page, 4, Material.PAPER, "Pending applications", List.of("Requests last seven days.", "Approval grants Resident access to shared land."));
        for (int n = 0; n < 21 && index * 21 + n < entries.size(); n++) {
            var entry = entries.get(index * 21 + n);
            button(page, HomesteadMenuPolicy.slot(n), Material.PLAYER_HEAD, name(entry.applicant()), List.of("Expires: " + entry.expiresAt(), "Click to review."), () -> {
                Page review = page(player, "Application | " + name(entry.applicant()), owner);
                for (boolean accept : new boolean[]{true, false}) button(review, accept ? 20 : 24, accept ? Material.LIME_CONCRETE : Material.RED_CONCRETE,
                    accept ? "Approve resident" : "Deny request", List.of(accept ? "Member limit and invitation seats are rechecked." : "No membership or money changes.",
                        accept ? "Click to approve." : "Click to deny."), () -> {
                        var current = read(claims.applications(actor, owner, Instant.now())).stream().filter(a -> a.applicant().equals(entry.applicant())).findFirst();
                        if (current.isEmpty() || !current.get().equals(entry)) Chat.error(player, "That application changed. Review it again.");
                        else result(player, claims.reviewApplication(actor, owner, entry.applicant(), accept, Instant.now()));
                        applications(player, owner, 0);
                    });
                footer(review, player); show(player, review);
            });
        }
        if (entries.isEmpty()) info(page, 22, Material.GLASS_BOTTLE, "No pending applications", List.of("Publish and open recruitment from your notice board."));
        pagination(page, index, entries.size(), p -> applications(player, owner, p)); footer(page, player); show(player, page);
    }

    private void history(Player player, AccountId owner, int requested) {
        if (!HomesteadMenuPolicy.member(account(player), read(claims.homestead(owner)))) { Chat.error(player, "The change history is for residents."); return; }
        var entries = new ArrayList<>(read(claims.history(account(player), owner)));
        entries.sort(Comparator.comparing(ClaimService.AuditEntry::at).reversed());
        int index = HomesteadMenuPolicy.page(requested, entries.size());
        Page page = page(player, "History | " + (index + 1), owner);
        info(page, 4, Material.CLOCK, "Recent changes", List.of("Newest first. Last 100 entries.", "A history, not a block rollback tool."));
        for (int n = 0; n < 21 && index * 21 + n < entries.size(); n++) {
            var entry = entries.get(index * 21 + n);
            List<String> detail = new ArrayList<>(List.of("By: " + name(entry.actor()), entry.at().toString()));
            detail.addAll(wrap(entry.detail()));
            info(page, HomesteadMenuPolicy.slot(n), Material.PAPER, entry.action().toLowerCase(Locale.ROOT).replace('_', ' '), detail);
        }
        if (entries.isEmpty()) info(page, 22, Material.GLASS_BOTTLE, "No changes recorded", List.of("New settlement changes appear here."));
        pagination(page, index, entries.size(), p -> history(player, owner, p)); footer(page, player); show(player, page);
    }

    private void help(Player player) {
        Chat.send(player, "<gold><bold>Homesteads</bold></gold> <gray>protect your builds without raids or upkeep.</gray>");
        Chat.usage(player, usage());
        for (String line : List.of(
            "/claim opens your map, residents, land rules, and invitations.",
            "/claim create [owner] | /claim show | /claim charter <name>",
            "/claim invite <player> | /claim accept [owner] | /claim deny [owner] | /claim leave <owner>",
            "/claim trust <player> <role> sets one plot; trustall sets your whole homestead.",
            "Build, container, and redstone are separate roles. Resident includes all three.",
            "Managers set rules and invite residents. Only owners assign roles or release land.",
            "Public rules never grant building rights. PvP still requires mutual consent.",
            "/claim plot type <type> | plot assign <resident> | plot unassign",
            "/claim publish on | recruiting on | apply <owner> | withdraw <owner>",
            "/claim bulletin <notice> | applications | history | chat <owner> [message]",
            "/claim home <owner> selects remote settlement controls, not remote claiming.",
            "Use inherit instead of on/off or a local role to remove a plot override."))
            Chat.info(player, Chat.escape(line));
    }

    /**
     * Every subcommand players see, in help order. The help line and tab completion are built
     * from this list; {@code list} and {@code decline} still work as hidden aliases.
     * {@code chat} and {@code expand} are answered by CommandRouter before this handler.
     */
    static final List<String> SUBCOMMANDS = List.of("create", "delete", "info", "map", "mine", "directory", "members", "plots",
        "plot", "flags", "flag", "access", "invite", "invitations", "accept", "deny", "leave", "trust", "trustall", "charter",
        "name", "show", "bulletin", "publish", "recruiting", "apply", "withdraw", "applications", "history", "home", "chat",
        "expand", "help");
    /** What {@code /claim home <owner> …} can do for a settlement you are not standing in. */
    static final List<String> HOME_ACTIONS = List.of("members", "plots", "bulletin", "publish", "recruiting", "invite",
        "applications", "history", "leave", "trustall");
    static String usage() { return "/claim [" + String.join("|", SUBCOMMANDS) + "]"; }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) return SUBCOMMANDS;
        String typed = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices(args).stream().filter(choice -> choice.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }

    private List<String> choices(String[] args) {
        if (args.length > 1 && args[0].equalsIgnoreCase("home")) {
            if (args.length == 2) return knownNames();
            if (args.length == 3) return HOME_ACTIONS;
            return choices(Arrays.copyOfRange(args, 2, args.length));
        }
        if (args.length <= 1) return SUBCOMMANDS;
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && Set.of("invite", "trust", "trustall", "create", "accept", "deny", "decline", "leave", "access", "apply", "withdraw", "chat").contains(action)) {
            List<String> names = new ArrayList<>(knownNames());
            if (action.equals("invite")) names.addAll(List.of("cancel", "deny"));
            return names;
        }
        if (args.length == 2 && Set.of("publish", "recruiting").contains(action)) return List.of("on", "off");
        if (args.length == 2 && action.equals("bulletin")) return List.of("clear");
        if (args.length == 3 && action.equals("invite")) return knownNames();
        if (action.equals("plot")) {
            if (args.length == 2) return List.of("type", "assign", "unassign");
            if (args.length == 3 && args[1].equalsIgnoreCase("type")) return Arrays.stream(PlotType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toList();
            if (args.length == 3 && args[1].equalsIgnoreCase("assign")) return knownNames();
        }
        if (args.length == 3 && Set.of("trust", "trustall").contains(action)) return action.equals("trust")
            ? List.of("none", "build", "container", "redstone", "resident", "manage", "inherit") : List.of("none", "build", "container", "redstone", "resident", "manage");
        if (action.equals("flag")) {
            if (args.length == 2) return Arrays.stream(ClaimFlag.values()).map(f -> f.name().toLowerCase(Locale.ROOT).replace('_', '-')).toList();
            if (args.length == 3) return List.of("on", "off", "inherit");
            if (args.length == 4) return List.of("all");
        }
        return List.of();
    }

    /** Player names for tab completion. Accounts without a known name are left out rather than shown as UUIDs. */
    private List<String> knownNames() {
        return plugin.playerData().knownAccounts().stream().map(this::knownName).flatMap(Optional::stream).distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof Page page)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(page.viewer)
            || !page.account.equals(account(player)) || event.getRawSlot() < 0 || event.getRawSlot() >= 54
            || !(event.isLeftClick() || event.isRightClick()) || page.busy) return;
        Runnable action = page.actions.get(event.getRawSlot());
        if (action == null) return;
        page.busy = true;
        MenuKit.clickSound(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != page.inventory
                || !page.account.equals(account(player))) return;
            try { action.run(); } catch (RuntimeException exception) { failure(player, exception); }
            finally { page.busy = false; }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof Page) event.setCancelled(true);
    }

    private Page page(Player player, String title, AccountId owner) {
        Page page = new Page(player.getUniqueId(), Objects.requireNonNull(account(player)), owner);
        page.inventory = Bukkit.createInventory(page, 54, Chat.render("<gold><bold>" + Chat.escape(title) + "</bold></gold>"));
        MenuKit.frame(page.inventory);
        return page;
    }

    private void footer(Page page, Player player) {
        nav(page, MenuKit.backSlot(54), MenuKit.back("Homestead"), () -> overview(player, page.owner));
        footerTools(page, player);
    }

    /** The footer for top-level screens, whose Back leads to the hub page they were opened from. */
    private void topFooter(Page page, Player player) {
        nav(page, MenuKit.backSlot(54), MenuKit.back("World & Trade"), () -> plugin.menus().open(player, MenuManager.View.WORLD));
        footerTools(page, player);
    }

    private void footerTools(Page page, Player player) {
        nav(page, MenuKit.closeSlot(54), MenuKit.close(), player::closeInventory);
        button(page, 50, Material.BOOK, "How it works", List.of("Commands, roles, and protection rules.", "Click to read the guide in chat."),
            () -> { player.closeInventory(); help(player); });
    }

    private void button(Page page, int slot, Material material, String title, List<String> lore, Runnable action) {
        nav(page, slot, icon(material, title, lore), action);
    }

    private void nav(Page page, int slot, ItemStack item, Runnable action) {
        page.inventory.setItem(slot, item);
        page.actions.put(slot, action);
    }

    private void info(Page page, int slot, Material material, String title, List<String> lore) {
        page.inventory.setItem(slot, icon(material, title, lore));
        page.actions.remove(slot);
    }

    private void control(Page page, int slot, Material material, String title, List<String> lore, boolean enabled, Runnable action) {
        if (enabled) button(page, slot, material, title, lore, action);
        else {
            List<String> unavailable = new ArrayList<>(lore);
            if (!unavailable.isEmpty() && unavailable.getLast().startsWith("Click")) unavailable.removeLast();
            unavailable.add("Unavailable for your role or this settlement.");
            info(page, slot, Material.GRAY_DYE, title, unavailable);
        }
    }

    private void pagination(Page page, int index, int count, java.util.function.IntConsumer open) {
        int pages = Math.max(1, (count + 20) / 21);
        if (index > 0) nav(page, MenuKit.previousSlot(54), MenuKit.previous(index, pages), () -> open.accept(index - 1));
        if ((index + 1) * 21 < count) nav(page, MenuKit.nextSlot(54), MenuKit.next(index, pages), () -> open.accept(index + 1));
    }

    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        String remaining = text;
        while (remaining.length() > 42) {
            int split = remaining.lastIndexOf(' ', 42);
            if (split < 12) split = 42;
            lines.add(remaining.substring(0, split));
            remaining = remaining.substring(split).stripLeading();
        }
        if (!remaining.isEmpty()) lines.add(remaining);
        return List.copyOf(lines);
    }

    /** Lore is plain text. A final line starting with "Click" becomes the yellow click hint, after a blank line. */
    private static ItemStack icon(Material material, String title, List<String> lore) {
        List<String> lines = new ArrayList<>();
        for (int n = 0; n < lore.size(); n++) {
            String line = lore.get(n);
            boolean hint = n == lore.size() - 1 && line.startsWith("Click");
            if (hint && !lines.isEmpty()) lines.add("");
            lines.add((hint ? "<yellow>" : "<gray>") + Chat.escape(line) + (hint ? "</yellow>" : "</gray>"));
        }
        return MenuKit.item(material, "<gold>" + Chat.escape(title) + "</gold>", lines);
    }

    private void show(Player player, Page page) {
        if (player.isOnline() && page.account.equals(account(player))) player.openInventory(page.inventory);
    }

    private void boundary(Player player, ChunkKey key) {
        if (!player.getWorld().getUID().equals(key.worldId())) return;
        double y = player.getLocation().getY() + 0.2;
        for (int n = 0; n <= 16; n++) {
            for (double[] point : new double[][]{{key.x() * 16D + n, key.z() * 16D}, {key.x() * 16D + n, key.z() * 16D + 16},
                {key.x() * 16D, key.z() * 16D + n}, {key.x() * 16D + 16, key.z() * 16D + n}}) {
                player.spawnParticle(Particle.DUST, point[0], y, point[1], 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.AQUA, 1.4F));
            }
        }
    }

    private AccountId resolve(String raw) {
        try {
            AccountId id = AccountId.parse(raw);
            if (plugin.playerData().knownAccounts().contains(id)) return id;
        } catch (IllegalArgumentException ignored) { }
        List<AccountId> matches = plugin.playerData().knownAccounts().stream().filter(id -> name(id).equalsIgnoreCase(raw)).toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private String name(AccountId account) {
        return knownName(account).orElse(account.toString().substring(0, 8));
    }
    private Optional<String> knownName(AccountId account) {
        return Optional.ofNullable(plugin.playerData().read(account, yaml -> yaml.getString("identity.last-known-name")));
    }
    /** What a player types to name this account: its name when known, otherwise the full UUID. */
    private String commandName(AccountId account) { return knownName(account).orElse(account.toString()); }
    private AccountId account(Player player) { return plugin.sessions().account(player.getUniqueId()).orElse(null); }
    private ChunkKey chunk(Player player) { return new ChunkKey(player.getWorld().getUID(), player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4); }
    private String worldName(ChunkKey key) { World world = Bukkit.getWorld(key.worldId()); return world == null ? key.worldId().toString().substring(0, 8) : world.getName(); }
    // Claim implementations expose immediate memory snapshots; never wait for IO on the server thread.
    private static <T> T read(CompletionStage<T> stage) {
        var future = stage.toCompletableFuture();
        if (!future.isDone()) throw new IllegalStateException("Claim snapshot was not immediately available");
        return future.join();
    }
    private static ClaimRole role(String name) { try { return ClaimRole.valueOf(name.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; } }
    private static ClaimFlag flag(String name) { try { return ClaimFlag.valueOf(name.toUpperCase(Locale.ROOT).replace('-', '_')); } catch (IllegalArgumentException e) { return null; } }
    private static String label(Enum<?> value) {
        String text = value.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    private static String description(ClaimFlag flag) {
        return switch (flag) {
            case EXPLOSIONS -> "Allow explosions to damage terrain.";
            case FIRE_SPREAD -> "Allow fire to spread and burn blocks.";
            case MOB_GRIEFING -> "Allow mobs to alter terrain.";
            case PUBLIC_INTERACT -> "Visitors may use doors, buttons, and public stations.";
            case PUBLIC_CONTAINERS -> "Visitors may open storage. They cannot break it.";
            case PUBLIC_HARVEST -> "Visitors may harvest mature crops and replant.";
            case PUBLIC_ANIMALS -> "Visitors may feed, breed, milk, and shear animals.";
            case HOSTILE_SPAWNING -> "Allow natural hostile mob spawning.";
            case PVP -> "Allow consensual PvP. This never enables forced combat.";
        };
    }
    private void result(Player player, CompletionStage<ClaimResult> stage) { result(player, read(stage)); }
    private void result(Player player, ClaimResult result) {
        String message = switch (result) {
            case CREATED -> "Chunk claimed. Your homestead's rules and residents apply here.";
            case DELETED -> "Plot released. This chunk is no longer protected.";
            case TRUST_UPDATED -> "Access updated.";
            case NAME_UPDATED -> "Name updated.";
            case FLAG_UPDATED -> "Land rule updated.";
            case CAPACITY_UPDATED -> "Capacity increased.";
            case INVITED -> "Invitation sent. It expires in five minutes.";
            case JOINED -> "Welcome to the homestead. You now have resident access.";
            case LEFT -> "You left the homestead.";
            case NO_INVITATION -> "That invitation is missing or expired.";
            case ALREADY_MEMBER -> "That player already belongs to this homestead.";
            case MEMBER_LIMIT -> "This homestead has reached its resident limit.";
            case PLOT_UPDATED -> "Plot preset applied. Check its rules for the effective settings.";
            case STEWARD_UPDATED -> "Housing assignment updated; previous local guests were cleared.";
            case NOT_MEMBER -> "Choose a current resident of this homestead.";
            case BULLETIN_UPDATED -> "Settlement notice updated.";
            case DIRECTORY_UPDATED -> "Settlement listing settings updated.";
            case INVALID_TEXT -> "Use up to 160 plain-text characters without color codes or line breaks.";
            case APPLIED -> "Application submitted. You gain no access until a manager approves it.";
            case APPLICATION_REMOVED -> "Application removed.";
            case NO_APPLICATION -> "That application is missing or expired.";
            case APPLICATION_LIMIT -> "Application limit reached: five active requests per player, 32 per homestead.";
            case NOT_RECRUITING -> "That settlement is not published and accepting applications.";
            case INVITATION_CANCELLED -> "Invitation cancelled.";
            case RESERVED -> "This chunk is reserved for the server.";
            case WOULD_SPLIT -> "Releasing that plot would split your territory. Release an outer plot first.";
            case INVALID_ROLE -> "That role cannot be assigned here. Ownership is not transferable.";
            case ALREADY_CLAIMED -> "Someone has already claimed this chunk.";
            case NOT_CLAIMED -> "This chunk is not claimed.";
            case NOT_OWNER -> "Your role does not allow that change.";
            case CAPACITY_REACHED -> "That homestead has no spare chunks. The owner can expand its capacity.";
            case MAX_CAPACITY_REACHED -> "The seasonal capacity limit has been reached.";
            case NOT_ADJACENT -> "Claim next to your existing land in this dimension.";
            case INVALID_NAME -> "Use 3-24 letters, numbers, spaces, apostrophes, hyphens, or underscores.";
            case NO_CLAIMS -> "Claim your first chunk before inviting residents.";
        };
        switch (outcome(result)) {
            case 1 -> Chat.success(player, message);
            case 0 -> Chat.notice(player, message);
            default -> Chat.error(player, message);
        }
    }
    /** 1 when the change happened, 0 when it is now waiting on someone else, -1 when it was refused. */
    static int outcome(ClaimResult result) {
        return switch (result) {
            case CREATED, DELETED, TRUST_UPDATED, NAME_UPDATED, FLAG_UPDATED, CAPACITY_UPDATED, INVITED, JOINED, LEFT,
                PLOT_UPDATED, STEWARD_UPDATED, BULLETIN_UPDATED, DIRECTORY_UPDATED, APPLICATION_REMOVED, INVITATION_CANCELLED -> 1;
            case APPLIED -> 0;
            default -> -1;
        };
    }
    private void failure(Player player, RuntimeException exception) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Homestead action failed for " + player.getUniqueId(), exception);
        Chat.error(player, "That change could not be saved. Staff have been notified.");
    }
    private static final class Page implements InventoryHolder {
        private final UUID viewer;
        private final AccountId account;
        private final AccountId owner;
        private final Map<Integer, Runnable> actions = new HashMap<>();
        private Inventory inventory;
        private boolean busy;
        private Page(UUID viewer, AccountId account, AccountId owner) { this.viewer = viewer; this.account = account; this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }
}

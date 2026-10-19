package net.nerdsmp.paper.command;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.PartyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.chat.ChannelChatManager.ChatChannel;
import net.nerdsmp.paper.grave.GraveManager;
import net.nerdsmp.paper.gui.RemoteStation;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public final class CommandRouter implements CommandExecutor, TabCompleter, Listener {
    private final NerdSmpPlugin plugin;
    static final List<String> ADMIN_SUBCOMMANDS = List.of("releasecheck", "stacks", "api", "marks", "xp", "damage",
        "relic", "set", "starter", "mob", "geode", "gem", "tome", "entitlement");
    static final String NERDADMIN_USAGE = "/nerdadmin <" + String.join("|", ADMIN_SUBCOMMANDS) + "> [arguments]";
    private final ClaimExpansionPurchase claimExpansions = new ClaimExpansionPurchase();

    public CommandRouter(NerdSmpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "permissions" -> plugin.staffPermissions().command(sender, args);
            case "zone" -> plugin.zoneCommands().command(sender,args);
            case "digsite" -> plugin.digSiteCommands().command(sender,args);
            case "mobloot" -> plugin.mobLootCommands().command(sender,args);
            case "itemengine" -> { if(sender instanceof Player player) plugin.studioUi().cancel(player); yield plugin.contentEditor().handleItems(sender,args); }
            case "mobengine" -> { if(sender instanceof Player player) plugin.studioUi().cancel(player); yield plugin.contentEditor().handleMobs(sender,args); }
            case "studio" -> plugin.developerStudio().command(sender,args);
            case "lootstudio" -> plugin.lootStudio().command(sender,args);
            case "worldstudio" -> plugin.worldStudio().command(sender,args);
            case "npcstudio" -> plugin.npcStudio().command(sender,args);
            case "recipestudio" -> plugin.recipeStudio().command(sender,args);
            case "expedition" -> {
                if (plugin.expeditions() == null) {
                    Chat.notice(sender, "Expeditions aren't part of this server. Travel with " + Chat.command("warps") + ".");
                    yield true;
                }
                yield plugin.expeditions().command(sender, args);
            }
            case "island" -> {
                if (plugin.skyblock() == null) {
                    Chat.notice(sender, "Islands aren't part of this server. Manage your land with " + Chat.command("claim") + ".");
                    yield true;
                }
                yield plugin.skyblock().handle(sender, args);
            }
            case "marks" -> marks(sender, args);
            case "pay" -> plugin.payments().command(sender, args);
            case "leaderboard" -> plugin.leaderboards().command(sender, args);
            case "weblink" -> net.nerdsmp.paper.webapi.WebLinkCommand.run(plugin, sender);
            case "cooking" -> { if (player(sender) instanceof Player player) plugin.recipes().showCooking(player); yield true; }
            case "skills" -> skills(sender);
            case "adventurer" -> adventurer(sender);
            case "mastery" -> mastery(sender, args);
            case "jobs" -> jobs(sender, args);
            case "market" -> market(sender, args);
            case "ah" -> plugin.auctionController().handle(sender, args);
            case "store" -> plugin.marketController().handleStore(sender, args);
            case "shops" -> plugin.npcs().showShops(sender, args);
            case "claim" -> claim(sender, args);
            case "party" -> party(sender, args);
            case "partychat" -> plugin.chat().handleDirect(sender, ChatChannel.PARTY, args);
            case "relics" -> relics(sender, args);
            case "campaign" -> campaign(sender, args);
            case "grave" -> grave(sender, args);
            case "duel" -> duel(sender, args);
            case "cosmetics" -> cosmetics(sender);
            case "menu" -> menu(sender);
            case "profile" -> profile(sender);
            case "hud" -> hud(sender);
            case "forge" -> forge(sender);
            case "enchants" -> enchants(sender);
            case "recipes" -> recipes(sender);
            case "daily" -> daily(sender);
            case "playtime" -> playtime(sender);
            case "npc" -> plugin.npcs().handleNpcCommand(sender, args);
            case "reputation" -> plugin.npcs().showReputation(sender, args);
            case "staff" -> plugin.staff().handleStaffCommand(sender, args);
            case "report" -> plugin.staff().handleReportCommand(sender, args);
            case "settings" -> plugin.guide().handleSettings(sender, args);
            case "social" -> plugin.social().handleSocial(sender);
            case "friends" -> plugin.social().handleFriends(sender, args);
            case "ignore" -> plugin.social().handleIgnore(sender, args, false);
            case "unignore" -> plugin.social().handleIgnore(sender, args, true);
            case "msg" -> plugin.social().handleMessage(sender, args, false);
            case "reply" -> plugin.social().handleMessage(sender, args, true);
            case "channel" -> plugin.chat().handleChannel(sender, args);
            case "globalchat" -> plugin.chat().handleDirect(sender, ChatChannel.GLOBAL, args);
            case "localchat" -> plugin.chat().handleDirect(sender, ChatChannel.LOCAL, args);
            case "tradechat" -> plugin.chat().handleDirect(sender, ChatChannel.TRADE, args);
            case "staffchat" -> plugin.chat().handleDirect(sender, ChatChannel.STAFF, args);
            case "trade" -> plugin.trade().handle(sender, args);
            case "spawn", "warp", "warps", "setwarp", "delwarp", "setspawn", "tpa", "tpahere",
                "tpaccept", "tpdeny", "tpcancel", "back" -> plugin.travel().handle(command.getName(), sender, args);
            case "quickstart" -> plugin.guide().handleQuickstart(sender);
            case "tips" -> plugin.guide().handleTips(sender, args);
            case "alchemy" -> openAlchemy(sender);
            case "fishing" -> openFishing(sender);
            case "excavation" -> openExcavation(sender);
            case "achievements" -> achievements(sender);
            case "watchdog" -> plugin.watchdog().handle(sender, args);
            case "identify" -> identify(sender, args);
            case "bank" -> bank(sender);
            case "repairall" -> repairAll(sender);
            case "nerdadmin" -> admin(sender, args);
            default -> false;
        };
    }

    private boolean achievements(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.achievements().open(player);
        }
        return true;
    }

    private boolean marks(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        AccountId account = account(player);
        if (account == null) {
            return true;
        }
        if (args.length == 0) {
            plugin.economy().balance(account).thenAccept(balance -> sync(() ->
                player.sendRichMessage("<gray>Balance</gray> " + Chat.marksTag(balance))));
            return true;
        }
        if (args[0].equalsIgnoreCase("pay")) return plugin.payments().command(sender, Arrays.copyOfRange(args, 1, args.length));
        Chat.usage(player, "/marks");
        return true;
    }

    private boolean skills(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.SKILLS);
        return true;
    }

    private boolean openAlchemy(CommandSender sender) {
        Player player = player(sender);
        if (player != null && canOpenRemoteStation(player, RemoteStation.ALCHEMY)) {
            plugin.alchemy().open(player);
        }
        return true;
    }

    private boolean openFishing(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.fishing().open(player);
        }
        return true;
    }

    private boolean openExcavation(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.excavation().open(player);
        }
        return true;
    }

    private boolean identify(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player != null && canOpenRemoteStation(player, RemoteStation.IDENTIFY)) {
            if (args.length == 0) plugin.identification().open(player);
            else if (args.length == 1) plugin.identification().openAction(player, args[0].toLowerCase(Locale.ROOT));
            else Chat.usage(player, "/identify [held|all|scrap]");
        }
        return true;
    }

    private boolean bank(CommandSender sender) {
        Player player = player(sender);
        if (player != null && canOpenRemoteStation(player, RemoteStation.BANK)) {
            plugin.bank().open(player);
        }
        return true;
    }

    private boolean repairAll(CommandSender sender) {
        Player player = player(sender);
        if (player != null && canOpenRemoteStation(player, RemoteStation.REPAIR)) {
            plugin.repairs().open(player);
        }
        return true;
    }

    private boolean adventurer(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.menus().openAdventurerRanks(player);
        }
        return true;
    }

    private boolean mastery(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        if (args.length != 2 || !(args[0].equalsIgnoreCase("select") || args[0].equalsIgnoreCase("remove"))) {
            Chat.usage(player, "/mastery <select|remove> <skill>");
            return true;
        }
        AccountId account = account(player);
        if (account == null) {
            return true;
        }
        Skill skill = parseSkill(args[1]);
        if (skill == null) {
            Chat.error(player, "Unknown skill. Choose one of: " + Arrays.stream(Skill.values())
                .map(value -> value.name().toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.joining(", ")) + ".");
            return true;
        }
        if (args[0].equalsIgnoreCase("select")) {
            plugin.progression().selectMastery(account, skill).thenAccept(result -> sync(() ->
                player.sendRichMessage(result == net.nerdsmp.api.service.ProgressionService.MasteryResult.SELECTED
                    ? "<green>Selected " + skill.displayName() + " mastery.</green>"
                    : "<red>" + masteryProblem(result, skill) + "</red>")));
            return true;
        }
        if (args[0].equalsIgnoreCase("remove")) {
            long cost = plugin.getConfig().getLong("season.mastery-respec-cost-marks", 10000L);
            Instant requestedAt = Instant.now();
            String requestId = UUID.randomUUID().toString();
            plugin.progression().snapshot(account).thenCompose(snapshot -> {
                if (!snapshot.masteries().contains(skill) || snapshot.nextRespecAt().isAfter(requestedAt)) {
                    return java.util.concurrent.CompletableFuture.completedFuture("NOT_AVAILABLE");
                }
                return plugin.economy().debit(
                    account,
                    cost,
                    new EconomyService.TransactionReason("mastery.respec", skill.name()),
                    "mastery-respec:" + requestId
                ).thenCompose(debit -> {
                    if (debit.status() != EconomyService.TransactionResult.Status.APPLIED) {
                        return java.util.concurrent.CompletableFuture.completedFuture("INSUFFICIENT_MARKS");
                    }
                    return plugin.progression().removeMastery(account, skill, requestedAt).thenCompose(result -> {
                        if (result == net.nerdsmp.api.service.ProgressionService.MasteryResult.REMOVED) {
                            return java.util.concurrent.CompletableFuture.completedFuture(result.name());
                        }
                        return plugin.economy().credit(
                            account,
                            cost,
                            new EconomyService.TransactionReason("mastery.respec_refund", result.name()),
                            "mastery-respec-refund:" + requestId
                        ).thenApply(ignored -> result.name());
                    });
                });
            }).thenAccept(result -> sync(() -> player.sendRichMessage(switch (result) {
                case "REMOVED" -> "<green>Removed your " + skill.displayName() + " mastery for " + Chat.marks(cost) + ".</green>";
                case "INSUFFICIENT_MARKS" -> "<red>Removing a mastery costs " + Chat.marks(cost) + ".</red>";
                case "NOT_AVAILABLE" -> "<red>You can't remove that mastery right now. It may not be selected, or your respec is on cooldown.</red>";
                default -> "<red>" + masteryProblem(net.nerdsmp.api.service.ProgressionService.MasteryResult.valueOf(result), skill)
                    + " Your " + Chat.marks(cost) + " were refunded.</red>";
            })));
            return true;
        }
        Chat.usage(player, "/mastery <select|remove> <skill>");
        return true;
    }

    private String masteryProblem(net.nerdsmp.api.service.ProgressionService.MasteryResult result, Skill skill) {
        return switch (result) {
            case SELECTED, REMOVED -> "Your mastery changed.";
            case ALREADY_SELECTED -> skill.displayName() + " is already one of your masteries.";
            case NOT_SELECTED -> skill.displayName() + " isn't one of your masteries.";
            case LEVEL_TOO_LOW -> "Reach " + skill.displayName() + " level "
                + plugin.getConfig().getInt("season.mastery-level", 25) + " to choose it as a mastery.";
            case LIMIT_REACHED -> "You already have the most masteries allowed this season.";
            case RESPEC_ON_COOLDOWN -> "Your mastery respec is still on cooldown.";
        };
    }

    private boolean jobs(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        AccountId account = account(player);
        if (account == null) {
            return true;
        }
        if (args.length == 0) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.JOBS);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("orders")) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.ORDERS);
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("fulfill")) {
            if (plugin.skyblockEnabled()) {
                Chat.notice(player, "Sell goods to the city merchants instead. " + Chat.command("jobs orders")
                    + " shows the trading guide, and " + Chat.command("spawn") + " takes you there.");
                return true;
            }
            Integer amount = positiveInt(args[2]);
            var order = plugin.orders().order(args[1]);
            ItemStack held = player.getInventory().getItemInMainHand();
            if (order == null) {
                Chat.error(player, "There's no active order called " + Chat.escape(args[1]) + ". See " + Chat.command("jobs orders") + ".");
                return true;
            }
            if (amount == null || held.getType() != order.material() || held.getAmount() < amount) {
                Chat.error(player, "Hold at least that many of the requested material in your main hand.");
                Chat.usage(player, "/jobs fulfill <order> <amount>");
                return true;
            }
            ItemStack submitted = held.clone();
            submitted.setAmount(amount);
            held.setAmount(held.getAmount() - amount);
            plugin.orders().fulfill(account, order.id(), amount, "order:" + UUID.randomUUID())
                .thenAccept(result -> sync(() -> {
                    if (result.status() == net.nerdsmp.paper.job.ServerOrderService.Status.APPLIED) {
                        player.sendRichMessage("<green>Order fulfilled for " + Chat.marks(result.marks())
                            + ". " + result.remainingItems() + " items still wanted.</green>");
                    } else {
                        player.getInventory().addItem(submitted).values()
                            .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                        player.sendRichMessage("<red>" + switch (result.status()) {
                            case UNKNOWN_ORDER -> "That order has closed.";
                            case INVALID_AMOUNT -> "That order doesn't need that many.";
                            case CAP_REACHED -> "You've reached today's limit for that order.";
                            case DUPLICATE -> "That delivery was already counted.";
                            case ECONOMY_FAILURE, APPLIED -> "The payment couldn't be made.";
                        } + " Your items were returned.</red>");
                    }
                }));
            return true;
        }
        Chat.usage(player, plugin.skyblockEnabled() ? "/jobs [orders]" : "/jobs [orders|fulfill <order> <amount>]");
        return true;
    }

    private boolean claim(CommandSender sender, String[] args) {
        if (plugin.skyblockEnabled()) {
            Chat.notice(sender, "Islands replace homesteads here. Manage your island, co-op and upgrades with "
                + Chat.command("island") + ".");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("chat")) {
            return plugin.chat().handleHomestead(sender, Arrays.copyOfRange(args, 1, args.length));
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("expand")) {
            return plugin.homesteads().handle(sender, args);
        }
        Player player = player(sender);
        if (player == null) return true;
        AccountId account = account(player);
        if (account == null) return true;
        if (args.length != 1) { Chat.usage(player, "/claim expand"); return true; }
        long cost = plugin.getConfig().getLong("economy.claim-expansion-cost", 500L);
        int maximum = plugin.getConfig().getInt("claims.maximum-capacity", 64);
        if (cost <= 0L || maximum <= 0) {
            plugin.getLogger().severe("Invalid claim expansion price or capacity limit; purchase refused");
            player.sendRichMessage("<red>Claim expansion is unavailable. Please contact staff.</red>");
            return true;
        }
        String operation = UUID.randomUUID().toString();
        claimExpansions.expand(account, maximum,
            () -> plugin.claims().capacity(account),
            () -> plugin.economy().debit(account, cost,
                new EconomyService.TransactionReason("claim.capacity", "One additional chunk"),
                "claim-expand:" + operation),
            () -> plugin.claims().addCapacity(account, 1),
            () -> plugin.economy().credit(account, cost,
                new EconomyService.TransactionReason("claim.capacity.refund", "Capacity update failed"),
                "claim-expand-refund:" + operation)
        ).thenAccept(result -> {
            if (result.outcome() == ClaimExpansionPurchase.Outcome.FAILED
                || result.outcome() == ClaimExpansionPurchase.Outcome.REFUND_FAILED) {
                plugin.getLogger().severe("Claim expansion " + operation + " for " + account + ": "
                    + result.outcome() + " " + result.detail());
            }
            sync(() -> {
                if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
                    || plugin.sessions().account(player.getUniqueId()).filter(account::equals).isEmpty()) {
                    return;
                }
                player.sendRichMessage(switch (result.outcome()) {
                    case EXPANDED -> "<green>Claim capacity increased by one chunk.</green>";
                    case MAXIMUM -> "<red>You have reached the seasonal claim-capacity limit.</red>";
                    case INSUFFICIENT_FUNDS -> "<red>Another chunk costs " + Chat.marks(cost) + ".</red>";
                    case REFUNDED -> "<yellow>Capacity did not change. Your " + Chat.marks(cost) + " were refunded.</yellow>";
                    case BUSY -> "<yellow>Your claim expansion is already being processed.</yellow>";
                    case REFUND_FAILED -> "<red>Capacity did not change, but the refund needs staff review. Reference: "
                        + operation.substring(0, 8) + ".</red>";
                    case FAILED -> "<red>Claim expansion could not finish. Contact staff with reference "
                        + operation.substring(0, 8) + ".</red>";
                });
            });
        });
        return true;
    }

    private boolean party(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        AccountId account = account(player);
        if (account == null) {
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            plugin.parties().partyOf(account).thenAccept(found -> sync(() -> {
                if (!currentSession(player, account)) return;
                if (found.isEmpty()) {
                    player.sendRichMessage("<gray>You are not in a party. Invite someone with <white>/party invite \\<player></white>.</gray>");
                    return;
                }
                PartyService.PartyView party = found.get();
                player.sendRichMessage("<light_purple><bold>Your party</bold></light_purple> <gray>("
                    + party.members().size() + "/" + plugin.getConfig().getInt("parties.maximum-size", 5) + ")</gray>");
                party.members().forEach(member -> {
                    Player online = plugin.sessions().player(member).map(Bukkit::getPlayer).orElse(null);
                    if (online != null && !player.canSee(online)) online = null;
                    String name = plugin.social().accountName(member);
                    player.sendRichMessage((member.equals(party.leader()) ? "<gold>★ " : "<gray>• ")
                        + escape(name) + (online == null ? " <dark_gray>(offline)</dark_gray>" : ""));
                });
            }));
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("accept") && args.length == 1) {
            plugin.parties().pendingInvite(account, Instant.now()).thenAccept(invite -> sync(() -> {
                if (!currentSession(player, account)) return;
                if (invite.isEmpty() || !plugin.settings().enabled(player.getUniqueId(), net.nerdsmp.paper.player.PlayerSettingsService.Setting.PARTY_REQUESTS)
                    || plugin.social().blocked(account, invite.get().leader())) {
                    Chat.error(player, "That party invitation is no longer available. Check your settings and ignored players."); return;
                }
                plugin.parties().accept(account, invite.get().partyId(), Instant.now()).thenAccept(result -> sync(() -> {
                    if (currentSession(player, account)) player.sendRichMessage(partyResult(result));
                }));
            }));
            return true;
        }
        if ((action.equals("leave") || action.equals("disband") || action.equals("deny")) && args.length == 1) {
            CompletionStage<PartyService.PartyResult> result = switch (action) {
                case "leave" -> plugin.parties().leave(account);
                case "deny" -> plugin.parties().decline(account);
                default -> plugin.parties().disband(account);
            };
            result.thenAccept(value -> sync(() -> { if (currentSession(player, account)) player.sendRichMessage(partyResult(value)); }));
            return true;
        }
        if ((action.equals("invite") || action.equals("kick") || action.equals("promote")) && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            AccountId targetAccount = action.equals("invite") ? target == null ? null : account(target) : plugin.social().knownAccount(args[1]).orElse(null);
            if (targetAccount == null) {
                Chat.error(player, action.equals("invite") ? "That player must be online." : "Choose a party member by name.");
                return true;
            }
            if (action.equals("invite") && !plugin.social().canContact(player, target, net.nerdsmp.paper.player.PlayerSettingsService.Setting.PARTY_REQUESTS)) {
                Chat.error(player, "That player is not accepting party invitations from you."); return true;
            }
            CompletionStage<PartyService.PartyResult> result = switch (action) {
                case "invite" -> plugin.parties().invite(account, targetAccount, Instant.now());
                case "kick" -> plugin.parties().kick(account, targetAccount);
                default -> plugin.parties().promote(account, targetAccount);
            };
            result.thenAccept(value -> sync(() -> {
                if (!currentSession(player, account)) return;
                player.sendRichMessage(partyResult(value));
                if (value == PartyService.PartyResult.INVITED && target != null && currentSession(target, targetAccount)) {
                    target.sendRichMessage("<light_purple>" + escape(player.getName())
                        + " invited you to a party. Use <white>/party accept</white> or <white>/party deny</white>.</light_purple>");
                }
            }));
            return true;
        }
        Chat.usage(player, "/party [info|invite <player>|accept|deny|leave|kick <player>|promote <player>|disband]");
        return true;
    }

    private boolean currentSession(Player player, AccountId account) {
        return player.isOnline() && Bukkit.getPlayer(player.getUniqueId()) == player
            && plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent();
    }

    private boolean recipes(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.recipes().showRecipes(player);
        }
        return true;
    }

    private boolean daily(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.QUESTS);
        }
        return true;
    }

    private boolean playtime(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.engagement().showPlaytime(player);
        }
        return true;
    }

    private boolean relics(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        if (args.length == 0) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.RELICS);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("sets")) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.RELICS);
            return true;
        }
        if (args.length != 1 || !args[0].equalsIgnoreCase("inspect")) {
            Chat.usage(player, "/relics [inspect|sets]");
            return true;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        plugin.items().refresh(held);
        var descriptor = plugin.items().describe(held);
        if (descriptor.isEmpty()) {
            player.sendRichMessage("<gray>Your held item is not an authentic relic.</gray>");
        } else {
            player.sendRichMessage("<gold>Relic: <white>" + descriptor.get().itemId() + "</white>, schema "
                + descriptor.get().schemaVersion() + ", origin " + descriptor.get().origin() + ".</gold>");
        }
        return true;
    }

    private boolean market(CommandSender sender, String[] args) {
        return plugin.marketController().handle(sender, args);
    }

    private boolean campaign(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        AccountId account = account(player);
        if (account == null) {
            return true;
        }
        if (args.length == 0) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.CAMPAIGN);
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("contribute")) {
            Long amount = positiveLong(args[2]);
            ItemStack held = player.getInventory().getItemInMainHand();
            if (amount == null || amount > held.getAmount() || held.getType().isAir()) {
                Chat.error(player, "Hold at least that many items in your main hand.");
                Chat.usage(player, "/campaign contribute <category> <amount>");
                return true;
            }
            ItemStack contributed = held.clone();
            contributed.setAmount(amount.intValue());
            held.setAmount(held.getAmount() - amount.intValue());
            plugin.campaigns().contribute(account, args[1].toLowerCase(Locale.ROOT), amount,
                "campaign:" + UUID.randomUUID()).thenAccept(result -> sync(() -> {
                if (result == net.nerdsmp.api.service.CampaignService.ContributionResult.APPLIED) {
                    player.sendRichMessage("<green>Contributed " + amount + " campaign points.</green>");
                    plugin.serverProgression().refresh();
                } else {
                    player.getInventory().addItem(contributed).values()
                        .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                    player.sendRichMessage("<red>" + switch (result) {
                        case NO_ACTIVE_CAMPAIGN -> "No campaign is running right now.";
                        case INVALID_CATEGORY -> "That isn't a campaign category. Choose one of: "
                            + String.join(", ", plugin.getConfig().getStringList("campaign.categories")) + ".";
                        case DAILY_CAP_REACHED -> "You've reached today's contribution limit.";
                        case INVALID_POINTS -> "That amount can't be contributed.";
                        case DUPLICATE -> "That contribution was already counted.";
                        case APPLIED -> "The contribution changed while it was saved.";
                    } + " Your items were returned.</red>");
                }
            }));
            return true;
        }
        Chat.usage(player, "/campaign [contribute <category> <amount>]");
        return true;
    }

    private boolean grave(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        AccountId account = account(player);
        if (account == null) {
            return true;
        }
        if (args.length != 1) {
            Chat.usage(player, "/grave <locate|recover>");
            return true;
        }
        if (args[0].equalsIgnoreCase("locate")) {
            Optional<Location> location = plugin.graves().latestLocation(account);
            player.sendRichMessage(location.map(value -> "<gold>Latest grave: <white>" + value.getWorld().getName()
                    + " " + value.getBlockX() + ", " + value.getBlockY() + ", " + value.getBlockZ() + "</white></gold>")
                .orElse("<gray>No active grave found.</gray>"));
            return true;
        }
        if (args[0].equalsIgnoreCase("recover")) {
            long cost = plugin.getConfig().getLong("economy.remote-grave-recovery-cost", 250L);
            if (plugin.graves().latestLocation(account).isEmpty()) {
                player.sendRichMessage("<gray>No active grave found.</gray>");
                return true;
            }
            plugin.economy().debit(account, cost,
                new EconomyService.TransactionReason("grave.remote_recovery", player.getName()),
                "grave-recovery:" + UUID.randomUUID()).thenAccept(result -> sync(() -> {
                if (result.status() != EconomyService.TransactionResult.Status.APPLIED) {
                    player.sendRichMessage("<red>Remote grave recovery costs " + Chat.marks(cost) + ".</red>");
                    return;
                }
                GraveManager.RecoveryResult recovery = plugin.graves().recoverLatest(account, player);
                if (recovery == GraveManager.RecoveryResult.RECOVERED) {
                    player.sendRichMessage("<green>Your grave was recovered.</green>");
                } else {
                    refund(account, cost, "grave.recovery_refund");
                    player.sendRichMessage("<red>Grave was unavailable; recovery fee refunded.</red>");
                }
            }));
            return true;
        }
        Chat.usage(player, "/grave <locate|recover>");
        return true;
    }

    private boolean duel(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        AccountId account = account(player);
        if (account == null) {
            return true;
        }
        if (args.length != 1) {
            Chat.usage(player, "/duel <on|off|status>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> plugin.pvp().setEnabled(account, true);
            case "off" -> plugin.pvp().setEnabled(account, false);
            case "status" -> {
            }
            default -> {
                Chat.usage(player, "/duel <on|off|status>");
                return true;
            }
        }
        player.sendRichMessage("<yellow>Consensual PvP: <white>" + (plugin.pvp().enabled(account) ? "enabled" : "disabled")
            + "</white>.</yellow>");
        return true;
    }

    private boolean cosmetics(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.COSMETICS);
        return true;
    }

    private boolean menu(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.menus().openHome(player);
        }
        return true;
    }

    private boolean profile(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.PROFILE);
        }
        return true;
    }

    private boolean hud(CommandSender sender) {
        Player player = player(sender);
        if (player != null) {
            boolean visible = plugin.presentation().toggleHud(player);
            player.sendRichMessage("<yellow>Sidebar HUD: <white>" + (visible ? "visible" : "hidden") + "</white>.</yellow>");
        }
        return true;
    }

    private boolean forge(CommandSender sender) {
        Player player = player(sender);
        if (player != null && canOpenRemoteStation(player, RemoteStation.FORGE)) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.FORGE);
        }
        return true;
    }

    private boolean enchants(CommandSender sender) {
        Player player = player(sender);
        if (player != null && canOpenRemoteStation(player, RemoteStation.ENCHANTING)) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.ENCHANTMENTS);
        }
        return true;
    }

    // plugin.yml also guards these commands with nerdsmp.station.remote and the same wording.
    private boolean canOpenRemoteStation(Player player, RemoteStation station) {
        if (player.hasPermission("nerdsmp.station.remote")) {
            return true;
        }
        player.sendRichMessage(station.refusalMarkup());
        return false;
    }


    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nerdsmp.admin")) {
            Chat.noPermission(sender);
            return true;
        }
        if (args.length == 0) {
            Chat.usage(sender, NERDADMIN_USAGE);
            return true;
        }
        if (args[0].equalsIgnoreCase("releasecheck") && args.length == 1) {
            net.nerdsmp.paper.release.ReleaseReadiness.show(plugin, sender); return true;
        }
        if (args[0].equalsIgnoreCase("stacks") && args.length == 1) {
            if (plugin.skyblock() == null) Chat.notice(sender, "Island mob stacking is only available in Skyblock.");
            else Chat.info(sender, Chat.escape(plugin.skyblock().automation().diagnostics()));
            return true;
        }
        if (args[0].equalsIgnoreCase("api") && args.length == 1) {
            Chat.info(sender, plugin.webApi() == null ? "The web API is disabled or could not start. Check the startup log and security/web-api.yml."
                : Chat.escape(plugin.webApi().status()));
            return true;
        }
        if (args[0].equalsIgnoreCase("marks") && args.length == 3) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            Long amount = positiveLong(args[2]);
            if (amount == null) {
                Chat.usage(sender, "/nerdadmin marks <player> <amount>");
                return true;
            }
            resolveKnownAccount(target).thenAccept(optional -> {
                if (optional.isEmpty()) {
                    sync(() -> sender.sendRichMessage("<red>Unknown player identity; no account was created.</red>"));
                    return;
                }
                plugin.economy().credit(optional.get(), amount,
                    new EconomyService.TransactionReason("admin.credit", sender.getName()),
                    "admin-credit:" + UUID.randomUUID()).thenAccept(result -> sync(() ->
                    sender.sendRichMessage("<yellow>Admin credit result: " + result.status() + ".</yellow>")));
            });
            return true;
        }
        if (args[0].equalsIgnoreCase("xp") && args.length == 4) {
            Player target = Bukkit.getPlayerExact(args[1]);
            Skill skill = parseSkill(args[2]);
            Long amount = positiveLong(args[3]);
            AccountId targetAccount = target == null ? null : account(target);
            if (target == null || targetAccount == null || skill == null || amount == null) {
                Chat.usage(sender, "/nerdadmin xp <online-player> <skill> <amount>");
                return true;
            }
            plugin.progression().awardExperience(
                targetAccount,
                skill,
                amount,
                "admin:" + UUID.randomUUID()
            ).thenAccept(result -> sync(() -> sender.sendRichMessage(
                "<green>Awarded " + amount + " " + skill.displayName() + " XP to " + target.getName()
                    + ". They are now level " + result.progress().level() + ".</green>"
            )));
            return true;
        }
        if (args[0].equalsIgnoreCase("damage") && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                Chat.usage(sender, "/nerdadmin damage <online-player>");
                return true;
            }
            var breakdown = plugin.gearSets().damageBreakdown(target);
            if (breakdown.isEmpty()) {
                sender.sendRichMessage("<red>" + target.getName()
                    + " is not holding an authenticated weapon.</red>");
                return true;
            }
            var damage = breakdown.get();
            double defense = plugin.gearSets().profile(target).stat("defense")
                + plugin.presentation().progression(target).adventurerStat("defense");
            sender.sendRichMessage("<gold>Damage check — " + target.getName() + "</gold>\n"
                + "<gray>Weapon:</gray> <white>" + damage.itemId().value() + "</white>\n"
                + "<gray>Base:</gray> <white>" + decimal(damage.baseDamage()) + "</white>"
                + " <dark_gray>+</dark_gray> <gray>flat:</gray> <white>" + decimal(damage.flatBonus()) + "</white>"
                + " <dark_gray>•</dark_gray> <gray>bonus:</gray> <white>" + decimal(damage.percentBonus()) + "%</white>\n"
                + "<gray>Cooldown:</gray> <white>" + decimal(damage.cooldown() * 100.0D) + "%</white>"
                + " <dark_gray>•</dark_gray> <gray>pre-mitigation hit:</gray> <aqua>"
                + decimal(damage.finalDamage()) + "</aqua>"
                + " <dark_gray>•</dark_gray> <gray>defense:</gray> <white>" + decimal(defense) + "</white>");
            return true;
        }
        if (args[0].equalsIgnoreCase("relic") && args.length == 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendRichMessage("<red>Player must be online.</red>");
                return true;
            }
            try {
                ItemStack item = plugin.items().create(new ItemId(args[2]));
                target.getInventory().addItem(item).values()
                    .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
                sender.sendRichMessage("<green>Relic granted.</green>");
            } catch (IllegalArgumentException exception) {
                sender.sendRichMessage("<red>" + Chat.escape(String.valueOf(exception.getMessage())) + "</red>");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("starter") && args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendRichMessage("<red>Player must be online.</red>");
                return true;
            }
            plugin.starterKit().give(target);
            sender.sendRichMessage("<green>Starter kit granted to " + target.getName() + ".</green>");
            return true;
        }
        if (args[0].equalsIgnoreCase("set") && args.length == 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            List<ItemId> setItems = plugin.items().itemIdsForSet(args[2]);
            if (target == null || setItems.isEmpty()) {
                sender.sendRichMessage("<red>Use an online player and a known gear-set id.</red>");
                return true;
            }
            setItems.stream().map(plugin.items()::create).forEach(item ->
                target.getInventory().addItem(item).values()
                    .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover)));
            sender.sendRichMessage("<green>Granted " + setItems.size() + " pieces from " + Chat.escape(args[2])
                + " to " + Chat.escape(target.getName()) + ".</green>");
            return true;
        }
        if (args[0].equalsIgnoreCase("mob") && args.length == 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendRichMessage("<red>Player must be online.</red>");
                return true;
            }
            try {
                plugin.mobs().spawn(args[2], target.getLocation());
                sender.sendRichMessage("<green>Creature spawned.</green>");
            } catch (IllegalArgumentException exception) {
                sender.sendRichMessage("<red>" + Chat.escape(String.valueOf(exception.getMessage())) + "</red>");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("geode") && args.length == 3) {
            Player target = Bukkit.getPlayerExact(args[1]);
            net.nerdsmp.paper.forge.GeodeGrade grade = net.nerdsmp.paper.forge.GeodeGrade.fromId(args[2]);
            if (target == null || grade == null) {
                sender.sendRichMessage("<red>Use an online player and one of: rough, resonant, luminous.</red>");
                return true;
            }
            target.getInventory().addItem(plugin.forge().createGeode(grade)).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            sender.sendRichMessage("<green>Gave " + target.getName() + " a " + grade.displayName() + ".</green>");
            return true;
        }
        if (args[0].equalsIgnoreCase("gem") && args.length == 4) {
            Player target = Bukkit.getPlayerExact(args[1]);
            net.nerdsmp.paper.forge.GemType type = net.nerdsmp.paper.forge.GemType.fromId(args[2]);
            Integer tier = positiveInt(args[3]);
            if (target == null || type == null || tier == null || tier > 4) {
                Chat.usage(sender, "/nerdadmin gem <online-player> <type> <1-4>");
                return true;
            }
            target.getInventory().addItem(plugin.forge().createGem(type, tier)).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            sender.sendRichMessage("<green>Gave " + target.getName() + " a " + type.displayName() + " gem.</green>");
            return true;
        }
        if (args[0].equalsIgnoreCase("tome") && args.length == 4) {
            Player target = Bukkit.getPlayerExact(args[1]);
            net.nerdsmp.paper.forge.ArcaneEnchantment enchantment =
                net.nerdsmp.paper.forge.ArcaneEnchantment.fromId(args[2]);
            Integer level = positiveInt(args[3]);
            if (target == null || enchantment == null || level == null || level > enchantment.maximumLevel()) {
                Chat.usage(sender, "/nerdadmin tome <online-player> <enchantment> <level>");
                return true;
            }
            target.getInventory().addItem(plugin.forge().createTome(enchantment, level)).values()
                .forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
            sender.sendRichMessage("<green>Gave " + target.getName() + " a " + enchantment.displayName() + " tome.</green>");
            return true;
        }
        if (args[0].equalsIgnoreCase("entitlement") && args.length >= 3) {
            if (!sender.hasPermission("nerdsmp.store.fulfill")) {
                Chat.noPermission(sender);
                return true;
            }
            if (args[1].equalsIgnoreCase("revoke") && args.length >= 4) {
                String reason = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                plugin.entitlements().revoke(args[2], reason).thenAccept(result -> sync(() ->
                    sender.sendRichMessage("<yellow>Entitlement revoke result: " + result + ".</yellow>")));
                return true;
            }
            if (args[1].equalsIgnoreCase("grant") && args.length >= 5) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                String product = args[3].toLowerCase(Locale.ROOT);
                String transaction = args[4];
                Integer days = args.length >= 6 ? positiveInt(args[5]) : null;
                if (!allowedPaidProduct(product)) {
                    sender.sendRichMessage("<red>Product is outside the cosmetic-only allowlist.</red>");
                    return true;
                }
                Instant expires = days == null ? null : Instant.now().plus(Duration.ofDays(days));
                resolveKnownAccount(target).thenAccept(optional -> {
                    if (optional.isEmpty()) {
                        sync(() -> sender.sendRichMessage("<red>Unknown player; entitlement held for support review.</red>"));
                        return;
                    }
                    plugin.entitlements().grant(optional.get(), product, transaction, expires).thenAccept(result -> sync(() ->
                        sender.sendRichMessage("<yellow>Entitlement grant result: " + result + ".</yellow>")));
                });
                return true;
            }
            Chat.usage(sender, "/nerdadmin entitlement grant <player> <product> <transaction> [days]");
            Chat.usage(sender, "/nerdadmin entitlement revoke <transaction> <reason>");
            return true;
        }
        Chat.usage(sender, NERDADMIN_USAGE);
        return true;
    }

    private CompletionStage<Optional<AccountId>> resolveKnownAccount(OfflinePlayer player) {
        Player online = player.getPlayer();
        if (online != null) {
            AccountId account = account(online);
            return java.util.concurrent.CompletableFuture.completedFuture(Optional.ofNullable(account));
        }
        PlatformIdentity identity = new PlatformIdentity(PlatformIdentity.Platform.JAVA, player.getUniqueId().toString());
        return plugin.accounts().find(identity);
    }

    private boolean allowedPaidProduct(String product) {
        return product.equals("supporter_monthly")
            || product.equals("supporter_annual")
            || product.startsWith("cosmetic.");
    }

    private void refund(AccountId accountId, long amount, String reason) {
        plugin.economy().credit(accountId, amount,
            new EconomyService.TransactionReason(reason, "Automatic compensation"),
            "refund:" + UUID.randomUUID());
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.playersOnly(sender);
        return null;
    }

    private AccountId account(Player player) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(player);
        }
        return account;
    }

    private Skill parseSkill(String raw) {
        try {
            return Skill.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }


    private Long positiveLong(String raw) {
        try {
            long value = Long.parseLong(raw);
            return value > 0L ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer positiveInt(String raw) {
        Long value = positiveLong(raw);
        return value == null || value > Integer.MAX_VALUE ? null : value.intValue();
    }

    private void sync(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    /** Hides commands that belong to the other game mode from players' command lists. */
    @EventHandler
    public void hideInactiveCommands(PlayerCommandSendEvent event) {
        List<String> hidden = plugin.skyblockEnabled()
            ? List.of("claim", "homestead", "territory")
            : List.of("island", "is", "skyblock", "expedition", "expeditions");
        event.getCommands().removeIf(name -> hidden.contains(name.startsWith("nerdsmp:") ? name.substring("nerdsmp:".length()) : name));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> choices = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "permissions" -> plugin.staffPermissions().complete(sender, args);
            case "expedition" -> args.length == 1 && plugin.expeditions() != null ? plugin.expeditions().names() : List.of();
            case "zone" -> plugin.zoneCommands().tabComplete(sender,args);
            case "digsite" -> plugin.digSiteCommands().complete(sender,args);
            case "mobloot" -> plugin.mobLootCommands().complete(sender,args);
            case "itemengine" -> sender.hasPermission("nerdsmp.content.items") ? plugin.contentEditor().tab(false,args) : List.of();
            case "mobengine" -> sender.hasPermission("nerdsmp.content.mobs") ? plugin.contentEditor().tab(true,args) : List.of();
            case "studio" -> plugin.developerStudio().complete(sender,args);
            case "lootstudio" -> plugin.lootStudio().complete(sender,args);
            case "npcstudio" -> plugin.npcStudio().complete(sender,args);
            case "recipestudio" -> plugin.recipeStudio().complete(sender,args);
            case "worldstudio" -> !sender.hasPermission("nerdsmp.studio.world") ? List.of()
                : args.length==1 ? List.of("zones","zone","digsites","warps","npcs")
                : args.length==2 && args[0].equalsIgnoreCase("zone") ? plugin.zones().all().stream().map(net.nerdsmp.paper.zone.ZoneDefinition::id).toList() : List.of();
            case "island" -> plugin.skyblock() == null ? List.of() : plugin.skyblock().complete(sender, args);
            case "marks" -> List.of();
            case "pay" -> args.length == 1 ? java.util.stream.Stream.concat(onlinePlayers(sender).stream(), java.util.stream.Stream.of("status")).toList()
                : args.length == 2 ? List.of("10", "100", "1000") : List.of();
            case "leaderboard" -> args.length == 1 ? List.of("marks", "adventurer", "skills", "island", "achievements", "cooking") : List.of();
            case "identify" -> args.length == 1 ? net.nerdsmp.paper.item.IdentificationService.SUBCOMMANDS : List.of();
            case "mastery" -> args.length == 1 ? List.of("select", "remove")
                : args.length == 2 ? Arrays.stream(Skill.values()).map(skill -> skill.name().toLowerCase(Locale.ROOT)).toList()
                : List.of();
            case "jobs" -> args.length == 1 ? plugin.skyblockEnabled() ? List.of("orders") : List.of("orders", "fulfill")
                : args.length == 2 && args[0].equalsIgnoreCase("fulfill")
                    ? plugin.orders().activeOrders().stream().map(order -> order.id()).toList() : List.of();
            case "market" -> plugin.marketController().tabComplete(sender, args);
            case "ah" -> plugin.auctionController().complete(sender, args);
            case "store" -> plugin.marketController().completeStore(sender, args);
            case "shops" -> plugin.npcs().completeShops(args);
            case "claim" -> plugin.skyblockEnabled() ? List.of() : plugin.homesteads().tabComplete(sender, args);
            case "party" -> args.length == 1
                ? List.of("info", "invite", "accept", "deny", "leave", "kick", "promote", "disband")
                : args.length == 2 && (args[0].equalsIgnoreCase("invite") || args[0].equalsIgnoreCase("kick")
                    || args[0].equalsIgnoreCase("promote")) ? partyTargets(sender, args[0]) : List.of();
            case "campaign" -> args.length == 1 ? List.of("contribute")
                : args.length == 2 ? plugin.getConfig().getStringList("campaign.categories") : List.of();
            case "grave" -> args.length == 1 ? List.of("locate", "recover") : List.of();
            case "duel" -> args.length == 1 ? List.of("on", "off", "status") : List.of();
            case "relics" -> args.length == 1 ? List.of("inspect", "sets") : List.of();
            case "npc" -> plugin.npcs().tabCompleteNpc(sender, args);
            case "reputation" -> args.length == 1
                ? plugin.npcs().content().factions().keySet().stream().map(net.nerdsmp.api.model.FactionId::value).toList()
                : List.of();
            case "staff", "report" -> plugin.staff().tabComplete(command.getName(), args);
            case "settings", "tips" -> plugin.guide().tabComplete(command.getName(), args);
            case "friends", "ignore", "unignore", "msg", "reply" ->
                plugin.social().tabComplete(command.getName(), sender, args);
            case "channel", "globalchat", "localchat", "tradechat", "staffchat", "partychat" ->
                plugin.chat().tabComplete(command.getName(), args);
            case "trade" -> plugin.trade().tabComplete(sender, args);
            case "spawn", "warp", "warps", "setwarp", "delwarp", "setspawn", "tpa", "tpahere",
                "tpaccept", "tpdeny", "tpcancel", "back" -> plugin.travel().tabComplete(command.getName(), sender, args);
            case "watchdog" -> plugin.watchdog().tabComplete(sender, args);
            case "nerdadmin" -> args.length == 1
                ? ADMIN_SUBCOMMANDS
                : args.length == 3 && args[0].equalsIgnoreCase("xp")
                    ? Arrays.stream(Skill.values()).map(skill -> skill.name().toLowerCase(Locale.ROOT)).toList()
                : args.length == 3 && args[0].equalsIgnoreCase("mob") ? plugin.mobs().ids()
                : args.length == 3 && args[0].equalsIgnoreCase("geode")
                    ? Arrays.stream(net.nerdsmp.paper.forge.GeodeGrade.values())
                        .map(net.nerdsmp.paper.forge.GeodeGrade::id).toList()
                : args.length == 3 && args[0].equalsIgnoreCase("gem")
                    ? Arrays.stream(net.nerdsmp.paper.forge.GemType.values())
                        .map(net.nerdsmp.paper.forge.GemType::id).toList()
                : args.length == 3 && args[0].equalsIgnoreCase("tome")
                    ? Arrays.stream(net.nerdsmp.paper.forge.ArcaneEnchantment.values())
                        .map(net.nerdsmp.paper.forge.ArcaneEnchantment::id).toList()
                : args.length == 4 && args[0].equalsIgnoreCase("gem") ? List.of("1", "2", "3", "4")
                : args.length == 4 && args[0].equalsIgnoreCase("tome")
                    ? java.util.stream.IntStream.rangeClosed(1, java.util.Optional.ofNullable(
                        net.nerdsmp.paper.forge.ArcaneEnchantment.fromId(args[2])).map(e -> e.maximumLevel()).orElse(3))
                        .mapToObj(Integer::toString).toList()
                : args.length == 3 && args[0].equalsIgnoreCase("relic")
                    ? plugin.items().itemIds().stream().map(ItemId::value).toList()
                : args.length == 3 && args[0].equalsIgnoreCase("set")
                    ? plugin.items().setDefinitions().stream().map(set -> set.id()).toList() : List.of();
            default -> List.of();
        };
        if (args.length == 0) {
            return choices;
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return new ArrayList<>(choices.stream().filter(value -> value.startsWith(prefix)).toList());
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private List<String> onlinePlayers(CommandSender sender) {
        return Bukkit.getOnlinePlayers().stream()
            .filter(player -> !(sender instanceof Player source) || !player.getUniqueId().equals(source.getUniqueId()))
            .map(Player::getName).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }


    private Boolean parseToggle(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes" -> true;
            case "off", "false", "no" -> false;
            default -> null;
        };
    }

    private List<String> partyTargets(CommandSender sender, String action) {
        if (!(sender instanceof Player player)) return List.of();
        if (action.equalsIgnoreCase("invite")) return Bukkit.getOnlinePlayers().stream()
            .filter(target -> plugin.social().canContact(player, target, net.nerdsmp.paper.player.PlayerSettingsService.Setting.PARTY_REQUESTS))
            .map(Player::getName).sorted().toList();
        AccountId actor = plugin.sessions().account(player.getUniqueId()).orElse(null); if (actor == null) return List.of();
        var party = plugin.parties().partyOf(actor).toCompletableFuture().getNow(java.util.Optional.empty());
        return party.filter(view -> view.leader().equals(actor)).map(view -> view.members().stream()
            .filter(member -> !member.equals(actor)).map(plugin.social()::accountName).sorted().toList()).orElse(List.of());
    }

    private String partyResult(PartyService.PartyResult result) {
        return switch (result) {
            case INVITED -> "<green>Party invite sent.</green>";
            case INVITE_PENDING -> "<yellow>That player already has a pending party invitation.</yellow>";
            case DECLINED -> "<yellow>Party invitation declined.</yellow>";
            case JOINED -> "<green>You joined the party.</green>";
            case LEFT -> "<yellow>You left the party.</yellow>";
            case KICKED -> "<yellow>Player removed from the party.</yellow>";
            case PROMOTED -> "<green>Party leader transferred.</green>";
            case DISBANDED -> "<yellow>Party disbanded.</yellow>";
            case PARTY_FULL -> "<red>That party is full.</red>";
            case NO_INVITE, INVITE_EXPIRED -> "<red>You do not have an active party invite.</red>";
            case NOT_LEADER -> "<red>Only the party leader can do that.</red>";
            case LEADER_MUST_PROMOTE_OR_DISBAND -> "<red>Promote another member or disband the party first.</red>";
            case TARGET_ALREADY_IN_PARTY, ALREADY_IN_PARTY -> "<red>That player is already in a party.</red>";
            case TARGET_NOT_MEMBER -> "<red>That player is not in your party.</red>";
            case CANNOT_TARGET_SELF -> "<red>You cannot target yourself.</red>";
            case NOT_IN_PARTY -> "<red>You are not in a party.</red>";
        };
    }


    private String escape(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }

}

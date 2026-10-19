package net.nerdsmp.paper.player;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerGuideService implements AutoCloseable {
    private final NerdSmpPlugin plugin;
    private final PlayerSettingsService settings;
    private final List<String> tips;
    private final Map<UUID, Integer> nextTip = new ConcurrentHashMap<>();
    private BukkitTask task;

    public PlayerGuideService(NerdSmpPlugin plugin, PlayerSettingsService settings) {
        this.plugin = plugin;
        this.settings = settings;
        List<String> configured = plugin.getConfig().getStringList(plugin.skyblockEnabled() ? "skyblock.tips" : "tips.messages");
        List<String> selected = configured.isEmpty() ? (plugin.skyblockEnabled() ? skyblockTips() : defaultTips()) : configured;
        tips = selected.stream().map(PlayerGuideService::publicTip).toList();
    }

    public void start() {
        long initial = guidanceTicks(plugin.getConfig().getLong("tips.initial-delay-seconds", 120L), 30L);
        long period = guidanceTicks(plugin.getConfig().getLong("tips.interval-seconds", 600L), 60L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastNext, initial, period);
    }

    public boolean handleQuickstart(CommandSender sender) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        player.sendRichMessage(net.nerdsmp.paper.presentation.BrandPalette.title(plugin.displayName() + " Quickstart"));
        List<String> steps = plugin.skyblockEnabled() ? List.of(
            Chat.command("island create") + " starts your home. " + Chat.command("island home") + " takes you back.",
            "Break the stone beside the lava to run your generator. Right-click ripe crops with a hoe to harvest and replant.",
            "Train " + Chat.command("skills") + " on your island to level it up. " + Chat.command("island talents") + " spends the points.",
            Chat.command("island journal") + " shows your next material unlock. " + Chat.command("island machines") + " lists processing recipes.",
            Chat.command("daily") + " and " + Chat.command("jobs") + " earn Marks. " + Chat.command("shops") + " buys and sells from anywhere.",
            "Trade with players through " + Chat.command("store") + ", " + Chat.command("ah") + " or " + Chat.command("trade") + ".",
            Chat.command("expedition") + " opens shared gathering areas. " + Chat.command("spawn") + " returns to the city.",
            "Invite trusted friends with " + Chat.syntax("/island invite <player>") + ". " + Chat.command("settings") + " controls who can reach you."
        ) : List.of(
            "Open " + Chat.command("skills") + " and train by doing the work.",
            "Protect a chunk with " + Chat.command("claim create") + ", or find neighbours in " + Chat.command("claim directory") + ".",
            "Check " + Chat.command("daily") + " and " + Chat.command("jobs") + " for paid work.",
            "Meet players through " + Chat.command("social") + " and choose a chat with " + Chat.command("channel") + ".",
            "Use " + Chat.command("recipes") + " for crafted gear. Any anvil opens the forge for gems.",
            "Brewing stands open alchemy. Try " + Chat.command("fishing") + " and " + Chat.command("excavation") + " for deeper gathering.",
            "Store valuables with Orin at the bank. Take sealed loot to Nyra to identify it.",
            "Track long-term milestones with " + Chat.command("achievements") + "."
        );
        for (int index = 0; index < steps.size(); index++) {
            player.sendRichMessage("<white>" + (index + 1) + ".</white> <gray>" + steps.get(index) + "</gray>");
        }
        player.sendRichMessage("<gray>Everything else is in " + Chat.command("menu") + ". Turn tips on or off with "
            + Chat.command("tips") + ".</gray>");
        return true;
    }

    public boolean handleTips(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        if (args.length == 0) {
            boolean enabled = settings.enabled(player.getUniqueId(), PlayerSettingsService.Setting.TIPS);
            player.sendRichMessage("<gray>Tips are " + (enabled ? "<green>on</green>" : "<yellow>off</yellow>") + ".</gray>");
            Chat.usage(player, "/tips [on|off|next]");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "on" -> {
                settings.set(player.getUniqueId(), PlayerSettingsService.Setting.TIPS, true);
                player.sendRichMessage("<green>Tip messages enabled.</green>");
            }
            case "off" -> {
                settings.set(player.getUniqueId(), PlayerSettingsService.Setting.TIPS, false);
                player.sendRichMessage("<yellow>Tip messages disabled.</yellow>");
            }
            case "next" -> sendNext(player);
            default -> Chat.usage(player, "/tips [on|off|next]");
        }
        return true;
    }

    public boolean handleSettings(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) {
            return true;
        }
        if (args.length == 0) {
            plugin.menus().open(player, net.nerdsmp.paper.gui.MenuManager.View.SETTINGS);
            return true;
        }
        PlayerSettingsService.Setting setting = PlayerSettingsService.Setting.parse(args[0]);
        if (setting == null || args.length != 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            Chat.usage(player, "/settings [<setting> <on|off>]");
            player.sendRichMessage("<gray>Open " + Chat.command("settings") + " to see every option.</gray>");
            return true;
        }
        boolean enabled = args[1].equalsIgnoreCase("on");
        settings.set(player.getUniqueId(), setting, enabled);
        player.sendRichMessage("<green>" + setting.displayName() + " " + (enabled ? "enabled" : "disabled") + ".</green>");
        return true;
    }

    public List<String> tabComplete(String command, String[] args) {
        if (command.equalsIgnoreCase("tips") && args.length == 1) {
            return List.of("on", "off", "next");
        }
        if (command.equalsIgnoreCase("settings")) {
            if (args.length == 1) {
                return Arrays.stream(PlayerSettingsService.Setting.values()).map(PlayerSettingsService.Setting::id).toList();
            }
            if (args.length == 2) {
                return List.of("on", "off");
            }
        }
        return List.of();
    }

    @Override
    public void close() {
        if (task != null) {
            task.cancel();
        }
    }

    private void broadcastNext() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (settings.enabled(player.getUniqueId(), PlayerSettingsService.Setting.TIPS)) {
                sendNext(player);
            }
        }
    }

    private void sendNext(Player player) {
        if (tips.isEmpty()) {
            return;
        }
        int index = Math.floorMod(nextTip.getOrDefault(player.getUniqueId(), player.getUniqueId().hashCode()), tips.size());
        player.sendRichMessage("<dark_gray>[</dark_gray><aqua><bold>TIP</bold></aqua><dark_gray>]</dark_gray> " + tips.get(index));
        nextTip.put(player.getUniqueId(), index + 1);
    }

    private List<String> defaultTips() {
        List<String> values = new ArrayList<>();
        values.add("<gray>Choose two skill masteries after level 25. Open <white>/skills</white> to plan ahead.</gray>");
        values.add("<gray>Physical grave recovery is free. <white>/grave recover</white> is the paid shortcut.</gray>");
        values.add("<gray>Geodes can hold socket gems. Hold matching gems and open <white>/forge</white> to merge them.</gray>");
        values.add("<gray>Local chat only reaches nearby players. Switch with <white>/channel local</white>.</gray>");
        values.add("<gray>Claim the chunk beneath your base with <white>/claim create</white>.</gray>");
        values.add("<gray>Looking for neighbors? <white>/claim directory</white> lists settlements. Applying never gives build access until approved.</gray>");
        values.add("<gray>Residential plots keep your home private from other residents. Owners assign them through <white>/claim plot</white>.</gray>");
        values.add("<gray>Daily contracts and server orders are reliable ways to earn Marks.</gray>");
        values.add("<gray>Enchanting uses Arcane Dust, crafted from lapis and amethyst. It never spends experience levels.</gray>");
        values.add("<gray>Brewing stands or <white>/alchemy</white> open twenty potion families, arcane brews, and eight poisons.</gray>");
        values.add("<gray>Catch streaks improve fishing rewards. Track yours with <white>/fishing</white>.</gray>");
        values.add("<gray>Quick excavation chains improve buried finds. Player-placed blocks never count.</gray>");
        values.add("<gray>Unidentified loot grants no stats until Nyra reveals it with <white>/identify</white>.</gray>");
        values.add("<gray>Your account bank starts with nine slots. Open it with <white>/bank</white>.</gray>");
        values.add("<gray>Prismatic Gems are exceptionally strong and exceptionally rare. Luminous geodes offer the best chance.</gray>");
        values.add("<gray>Elian exchanges pottery sherds, Relic records, and sniffer eggs for Rift Scholar reputation.</gray>");
        values.add("<gray>Track milestone rewards and Achievement Points with <white>/achievements</white>.</gray>");
        values.add("<gray>Different waters favor different fish. Ivo records journal catches for Free Delver reputation.</gray>");
        return List.copyOf(values);
    }

    private List<String> skyblockTips() {
        return List.of(
            "<gray>Craft meals from <white>/cooking</white> to train Cooking. A skilled cook's meals last longer, even when traded.</gray>",
            "<gray>Island talents award one point every two levels. Read each column in <white>/island talents</white> to plan your co-op's strengths.</gray>",
            "<gray>Send Marks with <white>/pay \\<player> \\<amount></white>. If a payment is pending, check <white>/pay status</white> instead of sending it again.</gray>",
            "<gray>Compare your progress in <white>/leaderboard</white>. Offline players are included.</gray>",
            "<gray><white>/island chat [owner]</white> keeps a conversation inside one co-op. Use <white>/channel global</white> to switch back.</gray>",
            "<gray>Trade from anywhere with <white>/trade \\<player></white>. Check both offers before confirming.</gray>",
            "<gray><white>/settings</white> controls private messages and invitations. <white>/ignore \\<player></white> also stops their requests.</gray>",
            "<gray>Your home is one command away: <white>/island home</white>.</gray>",
            "<gray><white>/expedition</white> leads to renewable veins, timber, excavation finds and fishing.</gray>",
            "<gray>Train skills together on your island to level it up. Every two island levels earns a talent point.</gray>",
            "<gray>Right-click ripe crops with a hoe to harvest and replant. Keep plain seeds handy if the harvest doesn't supply one.</gray>",
            "<gray><white>/island talents</white> upgrades generators, crops and automation. Your owner confirms each choice.</gray>",
            "<gray><white>/island machines</white> shows crushers, compost bins, sawmills, sifters and automation recipes.</gray>",
            "<gray>Machine inputs go in the top row, coal or charcoal in the middle, and finished goods in the bottom row. Redstone pauses work.</gray>",
            "<gray>Your next ore unlock is in <white>/island journal</white>. Existing skills count when you share your progress.</gray>",
            "<gray>NPC replies are private. Open chat to click a response, or type <white>/npc reply 1</white>.</gray>",
            "<gray>Sneak + right-click a chunk hopper with an item to filter its drops. An empty hand clears the filter.</gray>",
            "<gray><white>/shops [item]</white> buys supplies and sells surplus from anywhere. Left-click to buy, right-click to sell, then confirm.</gray>",
            "<gray>Train different skills with your co-op, then share your progress through <white>/island journal</white>.</gray>",
            "<gray>New island chapters unlock more space and better generator ores.</gray>",
            "<gray>Complete island chapters to collect centerpieces. Preview them in <white>/island showcase</white>.</gray>",
            "<gray>Right-click your island's bedrock landing to open its controls. <white>/settings</white> can hide displays or celebration effects.</gray>",
            "<gray><white>/island visitors on</white> allows sightseeing, not building or storage access.</gray>",
            "<gray>Only invite trusted players to your co-op. Members share building and storage access.</gray>",
            "<gray>Joining a co-op keeps your own island. Find all your homes with <white>/island homes</white>.</gray>",
            "<gray>Island void falls return you safely. Your city and expedition journeys have their own risks.</gray>",
            "<gray>Visit city stations for forging, enchanting and alchemy. Check <white>/daily</white> for contracts.</gray>",
            "<gray>Generated ores and regrown trees earn skill XP; placing an ore and breaking it does not.</gray>",
            "<gray>Excavation needs fresh soil or marked dig-site nodes. Find renewable digging with <white>/digsite list</white>; ordinary placed soil gives no XP.</gray>");
    }

    /** Correct only known obsolete stock wording; saved operator tip lists are not overwritten. */
    public static String publicTip(String message) {
        return message.replace("Every five island levels earns a talent point.", "Every two island levels earns a talent point.")
            .replace("and open-water fishing.", "and fishing.");
    }

    public static long guidanceTicks(long seconds, long minimumSeconds) {
        return Math.clamp(seconds, minimumSeconds, 86_400L) * 20L;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Chat.playersOnly(sender);
        return null;
    }
}

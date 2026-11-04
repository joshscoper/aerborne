package net.nerdsmp.paper.vote;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyPair;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Votes on server list sites. Sites reach the server through Votifier ({@link VotifierListener}); each vote
 * pays Marks, a little more for every day in a row up to a week, and every so many votes earns an XP booster.
 * A player who's offline is paid all the same and told when they next join. {@code /vote} lists the sites with
 * when each can be voted on again, and {@code /vote top} the month's top voters.
 */
public final class VoteService implements Listener, AutoCloseable {
    /** A vote site: its menu tile, vote page and the name it sends with each vote. {@code slot} is -1 to place it automatically. */
    record Site(String name, String url, String service, String icon, int slot, String description) {}

    /** The server's daily vote goal. {@code daily} 0 turns it off. */
    record Goal(int daily, boolean booster, long voterMarks) {}

    record Settings(boolean enabled, String host, int port, VoteLedger.Rules rules, String title, List<Site> sites, Goal goal) {
        /** The sites in the menu, by the key their votes are recorded under. */
        java.util.Set<String> siteKeys() {
            return sites.stream().map(site -> VoteLedger.key(site.service())).collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        static Settings read(ConfigurationSection yaml) {
            int port = yaml.getInt("listener.port", 9027);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("votes.yml listener.port must be 1-65535");
            long marks = yaml.getLong("rewards.marks-per-vote", 100);
            long bonus = yaml.getLong("rewards.streak-bonus-marks", 10);
            int cap = yaml.getInt("rewards.streak-cap-days", 7);
            int every = yaml.getInt("rewards.booster-every", 20);
            int repeat = yaml.getInt("rewards.repeat-hours", 6);
            int perDay = yaml.getInt("rewards.max-paid-per-day", 8);
            long siteBonus = yaml.getLong("rewards.site-bonus-marks", 25);
            long allSites = yaml.getLong("rewards.all-sites-bonus-marks", 250);
            int daily = yaml.getInt("community.daily-goal", 25);
            long voterMarks = yaml.getLong("community.voter-marks", 150);
            if (marks < 0 || marks > 10_000 || bonus < 0 || bonus > 1_000 || siteBonus < 0 || siteBonus > 1_000 || allSites < 0 || allSites > 10_000
                || voterMarks < 0 || voterMarks > 10_000) {
                throw new IllegalArgumentException("votes.yml Marks rewards must be 0-10000 (bonuses per day or site 0-1000)");
            }
            if (daily < 0 || daily > 100_000) throw new IllegalArgumentException("votes.yml community.daily-goal must be 0-100000");
            if (cap < 1 || cap > 365 || every < 0 || repeat < 1 || repeat > 48 || perDay < 1 || perDay > 50) {
                throw new IllegalArgumentException("votes.yml has a reward setting out of range");
            }
            List<Site> sites = new ArrayList<>();
            java.util.Set<Integer> taken = new java.util.HashSet<>();
            for (Map<?, ?> raw : yaml.getMapList("sites")) {
                String name = text(raw, "name", "");
                String url = text(raw, "url", "");
                URI uri;
                try { uri = URI.create(url); }
                catch (IllegalArgumentException invalid) { uri = URI.create("invalid:x"); }
                if (name.isEmpty() || name.length() > 40 || !"https".equals(uri.getScheme()) || url.matches(".*['\"<>\\s].*")) {
                    throw new IllegalArgumentException("votes.yml site '" + name + "' needs a name (up to 40 letters) and an https:// url");
                }
                int slot = raw.get("slot") instanceof Number number ? number.intValue() : -1;
                if (slot != -1 && (!VoteMenu.siteSlot(slot) || !taken.add(slot))) {
                    throw new IllegalArgumentException("votes.yml site '" + name + "' has slot " + slot + "; use a free slot from 10-16, 19-25 or 28-34");
                }
                sites.add(new Site(name, url, text(raw, "service", name), text(raw, "icon", "paper").toLowerCase(Locale.ROOT), slot, text(raw, "description", "")));
            }
            if (sites.size() > VoteMenu.SITE_SLOTS.length) {
                throw new IllegalArgumentException("votes.yml lists more sites than the menu has room for (" + VoteMenu.SITE_SLOTS.length + ")");
            }
            return new Settings(yaml.getBoolean("listener.enabled", true), yaml.getString("listener.host", "0.0.0.0"), port,
                new VoteLedger.Rules(marks, bonus, cap, every, Duration.ofHours(repeat), perDay, siteBonus, allSites),
                yaml.getString("menu.title", "<gold><bold>Vote for Aerborne</bold></gold>"), List.copyOf(sites),
                new Goal(daily, yaml.getBoolean("community.booster", true), voterMarks));
        }

        private static String text(Map<?, ?> raw, String key, String fallback) {
            Object value = raw.get(key);
            return value == null ? fallback : String.valueOf(value).strip();
        }
    }

    private record Received(String site, String username, long at, String outcome) {}

    private final NerdSmpPlugin plugin;
    private final File file;
    private Settings settings;
    private final VoteMenu menu;
    private final File tallyFile;
    private final ZoneId zone = ZoneId.systemDefault();
    private final Deque<Received> recent = new ArrayDeque<>();
    private String month;
    private final Map<String, Integer> tally = new LinkedHashMap<>();
    private final VoteGoal goal = new VoteGoal();
    private VotifierListener listener;
    private String problem;

    public VoteService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "votes.yml");
        if (!file.exists()) plugin.saveResource("votes.yml", false);
        settings = Settings.read(YamlConfiguration.loadConfiguration(file));
        menu = new VoteMenu(plugin, this);
        tallyFile = new File(plugin.getDataFolder(), "vote-tally.yml");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(tallyFile);
        month = saved.getString("month", YearMonth.now(zone).toString());
        ConfigurationSection counts = saved.getConfigurationSection("counts");
        if (counts != null) for (String name : counts.getKeys(false)) tally.put(name, counts.getInt(name));
        goal.load(saved.getConfigurationSection("community"));
    }

    /** Opens the Votifier port. A problem here is logged and shown in {@code /nerdadmin vote}; the rest of the server carries on. */
    public void start() {
        Bukkit.getPluginManager().registerEvents(menu, plugin);
        if (!settings.enabled()) return;
        try {
            Path security = plugin.getDataFolder().toPath().resolve("security");
            java.nio.file.Files.createDirectories(security);
            String token = net.nerdsmp.paper.anticheat.PanelSync.loadOrCreateToken(security.resolve("votifier-token.txt"));
            KeyPair keys = VotifierKeys.loadOrCreate(security.resolve("votifier"));
            listener = new VotifierListener(new InetSocketAddress(settings.host(), settings.port()), Map.of("default", token), keys.getPrivate(),
                vote -> Bukkit.getScheduler().runTask(plugin, () -> receive(vote.service(), vote.username())), plugin.getLogger());
            plugin.getLogger().info("Votifier listening on " + settings.host() + ":" + listener.port() + " (v1 and v2). Give vote sites the token in"
                + " security/votifier-token.txt, or the public key in security/votifier/public.key; neither is ever printed.");
        } catch (Exception failure) {
            problem = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            plugin.getLogger().log(Level.SEVERE, "Votifier could not start on port " + settings.port() + "; votes from sites won't arrive", failure);
        }
    }

    /** One vote from a site, on the main thread. */
    void receive(String service, String username) {
        String site = siteName(service);
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            AccountId account = plugin.sessions().account(online.getUniqueId()).orElse(null);
            if (account != null) pay(account, online.getName(), site, service);
            else note(site, username, "not signed in yet; nothing paid");
            return;
        }
        OfflinePlayer known = Bukkit.getOfflinePlayerIfCached(username);
        if (known == null || !known.hasPlayedBefore()) {
            note(site, username, "has never played here; nothing paid");
            return;
        }
        String name = known.getName() == null ? username : known.getName();
        plugin.accounts().find(new PlatformIdentity(PlatformIdentity.Platform.JAVA, known.getUniqueId().toString()))
            .thenAccept(found -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (found.isPresent()) pay(found.get(), name, site, service);
                else note(site, username, "has no account; nothing paid");
            }));
    }

    private void pay(AccountId account, String name, String site, String service) {
        long now = System.currentTimeMillis();
        VoteLedger.Result[] result = new VoteLedger.Result[1];
        java.util.Set<String> menuSites = settings.siteKeys();
        plugin.playerData().update(account, data -> result[0] = VoteLedger.record(data, service, now, zone, settings.rules(), menuSites));
        VoteLedger.Result vote = result[0];
        if (vote.status() == VoteLedger.Status.REPEAT) {
            note(site, name, "repeat within " + settings.rules().repeat().toHours() + "h; nothing paid");
            return;
        }
        if (vote.status() == VoteLedger.Status.DAILY_LIMIT) {
            note(site, name, "over today's limit; nothing paid");
            Player player = Bukkit.getPlayerExact(name);
            if (player != null) Chat.info(player, "Thanks for voting on <white>" + Chat.escape(site) + "</white>! You've had today's vote rewards already.");
            return;
        }
        count(name);
        java.time.LocalDate today = java.time.LocalDate.now(zone);
        VoteGoal.Step step = goal.add(account.value().toString(), today, settings.goal().daily());
        saveTally();
        note(site, name, "paid " + Chat.marks(vote.marks()) + (vote.booster() ? " and an XP booster" : ""));
        List<String> votedToday = plugin.playerData().read(account, data -> VoteLedger.sitesToday(data, now, zone));
        long left = menuSites.stream().filter(key -> !votedToday.contains(key)).count();
        var reason = new EconomyService.TransactionReason("vote", "Vote on " + site);
        plugin.economy().credit(account, vote.marks(), reason, "vote:" + account.value() + ":" + VoteLedger.key(service) + ":" + now)
            .thenAccept(paid -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (paid.status() != EconomyService.TransactionResult.Status.APPLIED) {
                    plugin.getLogger().warning("Vote Marks for " + name + " were not paid: " + paid.status());
                    return;
                }
                Player player = Bukkit.getPlayerExact(name);
                if (player != null) {
                    List<String> why = new ArrayList<>();
                    if (vote.site() > 1) why.add(ordinal(vote.site()) + " site today");
                    if (vote.streak() > 1) why.add(vote.streak() + "-day streak");
                    Chat.success(player, "Thanks for voting on <white>" + Chat.escape(site) + "</white>! " + Chat.marksTag(vote.marks())
                        + (why.isEmpty() ? "" : " <gray>(" + String.join(", ", why) + ")</gray>"));
                    if (vote.allSites()) {
                        Chat.success(player, "That's every site today, so it includes a bonus of " + Chat.marksTag(settings.rules().allSitesBonus()) + ".");
                    } else if (left > 0 && settings.rules().siteBonus() > 0) {
                        Chat.info(player, "Your next site today pays " + Chat.marks(settings.rules().siteBonus()) + " more"
                            + (settings.rules().allSitesBonus() > 0 ? ", and all " + menuSites.size() + " pay a bonus of " + Chat.marks(settings.rules().allSitesBonus()) : "")
                            + ": " + Chat.command("/vote"));
                    }
                } else {
                    plugin.playerData().update(account, data -> {
                        data.set("votes.unseen-votes", data.getInt("votes.unseen-votes") + 1);
                        data.set("votes.unseen-marks", data.getLong("votes.unseen-marks") + vote.marks());
                    });
                }
            }));
        if (vote.booster()) {
            plugin.boosters().earn(account, "vote:" + vote.total()).thenAccept(applied -> Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayerExact(name);
                if (applied && player != null) Chat.success(player, "That's vote number " + vote.total() + ": you earned an XP booster! Use it with "
                    + Chat.command("/booster use") + ".");
            }));
        }
        String progress = step.goal() > 0 && !step.reached() ? " <gray>(" + step.votes() + "/" + step.goal() + " toward today's goal)</gray>" : "";
        String line = "<gold>✦</gold> <white>" + Chat.escape(name) + "</white> <gray>voted for the server on</gray> <white>" + Chat.escape(site)
            + "</white>" + progress + "<gray>. Vote too:</gray> " + Chat.command("/vote");
        for (Player player : Bukkit.getOnlinePlayers()) if (!player.getName().equalsIgnoreCase(name)) Chat.send(player, line);
        if (step.reachedNow()) reachGoal(today);
        else if (step.reached()) payGoal(account, today, true);
        else if (step.quarter() > 0) {
            String quarter = "<aqua><bold>✦</bold> Today's vote goal is " + step.quarter() + "% there:</aqua> <white>" + step.votes() + "/" + step.goal()
                + "</white> <gray>votes. Reach it and everyone who voted gets</gray> " + Chat.marksTag(settings.goal().voterMarks())
                + (settings.goal().booster() ? "<gray>, and everyone online an hour of +50% XP</gray>" : "") + "<gray>.</gray> " + Chat.command("/vote");
            for (Player player : Bukkit.getOnlinePlayers()) Chat.send(player, quarter);
        }
    }

    /** The server just reached today's goal: pay everyone who voted today and, if set, boost everyone online. */
    private void reachGoal(java.time.LocalDate today) {
        Goal rules = settings.goal();
        String line = "<gold><bold>✦ Today's vote goal is reached!</bold></gold> <gray>" + rules.daily() + " votes. Everyone who voted gets</gray> "
            + Chat.marksTag(rules.voterMarks()) + "<gray>, and so does anyone else who votes before midnight:</gray> " + Chat.command("/vote");
        for (Player player : Bukkit.getOnlinePlayers()) Chat.send(player, line);
        plugin.getLogger().info("Today's vote goal of " + rules.daily() + " was reached.");
        if (rules.booster()) plugin.boosters().gift("today's voters", "<gold><bold>✦</bold> Today's voters filled the vote goal!</gold>");
        for (String voter : goal.voters(today)) payGoal(new AccountId(java.util.UUID.fromString(voter)), today, false);
    }

    /** Pays one voter the goal's reward, once a day: the transaction key makes a second payment a no-op. */
    private void payGoal(AccountId account, java.time.LocalDate today, boolean late) {
        long marks = settings.goal().voterMarks();
        if (marks <= 0) return;
        var reason = new EconomyService.TransactionReason("vote.goal", "Daily vote goal " + today);
        plugin.economy().credit(account, marks, reason, "vote-goal:" + today + ":" + account.value())
            .thenAccept(paid -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (paid.status() != EconomyService.TransactionResult.Status.APPLIED) return;
                Player player = online(account);
                if (player != null) Chat.success(player, (late ? "Today's vote goal is already reached, so your vote also pays " : "Your share of today's vote goal: ")
                    + Chat.marksTag(marks) + ".");
                else plugin.playerData().update(account, data -> data.set("votes.unseen-goal-marks", data.getLong("votes.unseen-goal-marks") + marks));
            }));
    }

    private Player online(AccountId account) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent()) return player;
        }
        return null;
    }

    private static String ordinal(int number) {
        int tens = number % 100;
        String suffix = tens >= 11 && tens <= 13 ? "th" : switch (number % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
        return number + suffix;
    }

    private void count(String name) {
        String now = YearMonth.now(zone).toString();
        if (!now.equals(month)) {
            month = now;
            tally.clear();
        }
        tally.merge(name, 1, Integer::sum);
    }

    private void saveTally() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("month", month);
        tally.forEach((player, votes) -> yaml.set("counts." + player, votes));
        goal.save(yaml.createSection("community"));
        try { AtomicYamlFiles.save(yaml, tallyFile); }
        catch (IOException failure) { plugin.getLogger().log(Level.WARNING, "Could not save vote-tally.yml", failure); }
    }

    private void note(String site, String username, String outcome) {
        recent.addFirst(new Received(site, username, System.currentTimeMillis(), outcome));
        while (recent.size() > 8) recent.removeLast();
        plugin.getLogger().info("Vote on " + site + " for " + username + ": " + outcome + ".");
    }

    private String siteName(String service) {
        String key = VoteLedger.key(service);
        return settings.sites().stream().filter(site -> VoteLedger.key(site.service()).equals(key)).map(Site::name).findFirst().orElse(service);
    }

    /** {@code /vote [top]}. */
    public boolean command(CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
            top(sender);
            return true;
        }
        // Players get the menu; the console, and anyone who asks for /vote list, get the same in chat.
        if (sender instanceof Player opener && (args.length == 0 || !args[0].equalsIgnoreCase("list"))) {
            menu.open(opener);
            return true;
        }
        VoteLedger.Rules rules = settings.rules();
        Chat.send(sender, "<gold><bold>Vote for Aerborne</bold></gold> <gray>· each vote pays</gray> " + Chat.marksTag(rules.marksPerVote())
            + (rules.streakBonus() > 0 ? "<gray>, and " + Chat.marks(rules.streakBonus()) + " more for each day in a row</gray>" : ""));
        if (rules.siteBonus() > 0 || rules.allSitesBonus() > 0 && settings.sites().size() >= 2) {
            Chat.send(sender, "<gray>Each more site the same day pays " + Chat.marks(rules.siteBonus()) + " more"
                + (rules.allSitesBonus() > 0 && settings.sites().size() >= 2 ? ", and voting on all " + settings.sites().size() + " pays a bonus of "
                + Chat.marks(rules.allSitesBonus()) : "") + ".</gray>");
        }
        if (settings.goal().daily() > 0) {
            int[] today = goalToday();
            Chat.send(sender, "<aqua>Today's vote goal:</aqua> <white>" + today[0] + "/" + today[1] + "</white> "
                + (today[2] == 1 ? "<green>reached! Everyone who votes today gets</green> " : "<gray>· reach it and everyone who voted gets</gray> ")
                + Chat.marksTag(settings.goal().voterMarks()));
        }
        Player player = sender instanceof Player p ? p : null;
        AccountId account = player == null ? null : plugin.sessions().account(player.getUniqueId()).orElse(null);
        long now = System.currentTimeMillis();
        if (settings.sites().isEmpty()) Chat.send(sender, "<gray>No vote sites are listed yet.</gray>");
        for (Site site : settings.sites()) {
            String status = "";
            if (account != null) {
                long next = plugin.playerData().read(account, data -> VoteLedger.nextVote(data, site.service(), now));
                status = next == 0 ? " <green>ready</green>" : " <gray>again in " + Chat.duration(Duration.ofMillis(next - now)) + "</gray>";
            }
            Chat.send(sender, " <gray>▸</gray> <click:open_url:'" + site.url() + "'><hover:show_text:'<gray>Open the vote page</gray>'><aqua><u>"
                + Chat.escape(site.name()) + "</u></aqua></hover></click>" + status);
        }
        if (account != null) {
            int total = plugin.playerData().read(account, data -> data.getInt("votes.total"));
            int streak = plugin.playerData().read(account, data -> VoteLedger.streak(data, now, zone));
            String booster = rules.boosterEvery() > 0 ? " · XP booster in <white>" + (rules.boosterEvery() - total % rules.boosterEvery()) + "</white>" : "";
            Chat.send(sender, "<gray>Your votes: <white>" + total + "</white> in all · <white>" + monthCount(player.getName())
                + "</white> this month · streak <white>" + streak + "</white> day" + (streak == 1 ? "" : "s") + booster + "</gray>");
        }
        Chat.send(sender, "<gray>This month's top voters:</gray> " + Chat.command("/vote top"));
        return true;
    }

    private int monthCount(String name) {
        if (!YearMonth.now(zone).toString().equals(month)) return 0;
        return tally.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).mapToInt(Map.Entry::getValue).findFirst().orElse(0);
    }

    void top(CommandSender sender) {
        List<Map.Entry<String, Integer>> best = leaders(10);
        Chat.send(sender, "<gold><bold>Top voters</bold></gold> <gray>· " + YearMonth.now(zone).getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH) + "</gray>");
        if (best.isEmpty()) Chat.send(sender, "<gray>No votes yet this month. Be the first: </gray>" + Chat.command("/vote"));
        int place = 1;
        for (Map.Entry<String, Integer> entry : best) {
            Chat.send(sender, " <gold>" + place++ + ".</gold> <white>" + Chat.escape(entry.getKey()) + "</white> <gray>" + entry.getValue()
                + " vote" + (entry.getValue() == 1 ? "" : "s") + "</gray>");
        }
    }

    /** {@code /nerdadmin vote [status|reload|test <player> [site]]}. */
    public boolean admin(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("test")) {
            String service = args.length >= 4 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "Test";
            Chat.info(sender, "Counting a vote on <white>" + Chat.escape(service) + "</white> for <white>" + Chat.escape(args[2])
                + "</white>, as if a site had sent it. It pays like a real one.");
            receive(service, args[2]);
            return true;
        }
        if (args.length == 2 && args[1].equalsIgnoreCase("reload")) {
            // Read strictly: loadConfiguration would log a YAML mistake and carry on with an empty file, losing every site.
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file);
                settings = Settings.read(yaml);
                Chat.success(sender, "Reloaded votes.yml: " + settings.sites().size() + " site" + (settings.sites().size() == 1 ? "" : "s")
                    + ". Changes to the listener take a restart.");
            } catch (org.bukkit.configuration.InvalidConfigurationException invalid) {
                java.util.regex.Matcher where = java.util.regex.Pattern.compile("line (\\d+), column (\\d+)").matcher(String.valueOf(invalid.getMessage()));
                Chat.error(sender, "votes.yml isn't valid YAML" + (where.find() ? " near line " + where.group(1) + ", column " + where.group(2) : "")
                    + ". Check the indentation there. The old settings are still in use.");
            } catch (IOException | RuntimeException invalid) {
                Chat.error(sender, Chat.escape(String.valueOf(invalid.getMessage())) + ". The old settings are still in use.");
            }
            return true;
        }
        if (args.length > 1 && !args[1].equalsIgnoreCase("status")) {
            Chat.usage(sender, "/nerdadmin vote [status|reload|test <player> [site]]");
            return true;
        }
        if (listener != null) Chat.info(sender, "Votifier is listening on <white>" + settings.host() + ":" + listener.port() + "</white> (v1 and v2).");
        else if (!settings.enabled()) Chat.info(sender, "Votifier is off (<white>listener.enabled</white> in votes.yml).");
        else Chat.error(sender, "Votifier isn't running: " + Chat.escape(String.valueOf(problem)) + ". Check the startup log.");
        if (settings.goal().daily() > 0) {
            int[] today = goalToday();
            Chat.info(sender, "Today's vote goal: <white>" + today[0] + "/" + today[1] + "</white>" + (today[2] == 1 ? " (reached)" : "") + ".");
        }
        Chat.info(sender, "Sites need the token in <white>security/votifier-token.txt</white>, or for v1 the key in <white>security/votifier/public.key</white>.");
        if (recent.isEmpty()) Chat.info(sender, "No votes since the server started.");
        else Chat.info(sender, "Latest votes (the site name is what goes in votes.yml's <white>service</white>):");
        for (Received vote : recent) {
            Chat.send(sender, " <gray>" + Chat.duration(Duration.ofMillis(Math.max(1000, System.currentTimeMillis() - vote.at()))) + " ago</gray> <white>"
                + Chat.escape(vote.site()) + "</white> <gray>for</gray> <white>" + Chat.escape(vote.username()) + "</white> <gray>· " + Chat.escape(vote.outcome()) + "</gray>");
        }
        return true;
    }

    Settings settings() { return settings; }

    /** Today's goal as {votes, goal, 1 if reached}. */
    int[] goalToday() {
        java.time.LocalDate today = java.time.LocalDate.now(zone);
        return new int[]{goal.votes(today), settings.goal().daily(), goal.reached(today) ? 1 : 0};
    }

    ZoneId zone() { return zone; }

    /** This month's leaders, most votes first. */
    List<Map.Entry<String, Integer>> leaders(int limit) {
        if (!YearMonth.now(zone).toString().equals(month)) return List.of();
        return tally.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .limit(limit).toList();
    }

    int monthVotes(String name) { return monthCount(name); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
            if (account == null) return;
            long goalMarks = plugin.playerData().read(account, data -> data.getLong("votes.unseen-goal-marks"));
            if (goalMarks > 0) {
                plugin.playerData().update(account, data -> data.set("votes.unseen-goal-marks", null));
                Chat.success(player, "The server reached a daily vote goal while you were away. Your share: " + Chat.marksTag(goalMarks) + ".");
            }
            int votes = plugin.playerData().read(account, data -> data.getInt("votes.unseen-votes"));
            if (votes > 0) {
                long marks = plugin.playerData().read(account, data -> data.getLong("votes.unseen-marks"));
                plugin.playerData().update(account, data -> {
                    data.set("votes.unseen-votes", null);
                    data.set("votes.unseen-marks", null);
                });
                Chat.success(player, "While you were away, your " + (votes == 1 ? "vote" : votes + " votes") + " paid you " + Chat.marksTag(marks) + ". Thank you!");
                return;
            }
            long now = System.currentTimeMillis();
            boolean voter = plugin.playerData().read(account, data -> data.getInt("votes.total")) > 0;
            boolean ready = settings.sites().stream().anyMatch(site -> plugin.playerData().read(account, data -> VoteLedger.nextVote(data, site.service(), now)) == 0);
            if (voter && ready) Chat.info(player, "You can vote for the server again today: " + Chat.command("/vote"));
        }, 100L);
    }

    @Override
    public void close() {
        if (listener != null) listener.close();
    }
}

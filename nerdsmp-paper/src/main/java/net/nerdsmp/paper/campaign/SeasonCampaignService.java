package net.nerdsmp.paper.campaign;

import net.kyori.adventure.title.Title;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.service.CampaignService;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.campaign.season.SeasonDefinition;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.BoostKind;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Goal;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Milestone;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Reward;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Source;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.SourceType;
import net.nerdsmp.paper.campaign.season.SeasonLedger;
import net.nerdsmp.paper.campaign.season.SeasonLoader;
import net.nerdsmp.paper.campaign.season.SeasonValidator;
import net.nerdsmp.paper.expedition.InstanceWorlds;
import net.nerdsmp.paper.presentation.BrandPalette;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.presentation.VisualFormat;
import net.nerdsmp.paper.resource.HarvestClaims;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/**
 * The season campaign. Staff define goals, sources and milestone rewards in {@code campaign/season.yml};
 * player actions (mining, smelting, crafting, fishing, kills, bosses, expeditions and contributions)
 * feed server-wide counters under per-player daily caps. A milestone reached starts its timed buffs
 * and boosts for everyone, opens what it unlocks, and lets every account claim its items and Marks
 * once. Also serves the older {@link CampaignService} view (goals as categories) for the HUD.
 */
public final class SeasonCampaignService implements CampaignService, Listener, AutoCloseable {
    public static final String ADMIN = "nerdsmp.campaign.admin";
    private final NerdSmpPlugin plugin;
    private final File seasonFile;
    private SeasonDefinition season;
    private SeasonLedger ledger = new SeasonLedger();
    private File stateFile;
    private List<SeasonValidator.Issue> issues = List.of();
    private String loadError;
    private boolean dirty;
    private final Set<String> capNotices = new HashSet<>();
    private BukkitTask buffTask, saveTask;
    private CampaignMenu menu;

    public SeasonCampaignService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        File folder = new File(plugin.getDataFolder(), "campaign");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Could not create " + folder);
        seasonFile = new File(folder, "season.yml");
        if (!seasonFile.exists()) plugin.saveResource("campaign/season.yml", false);
        reload();
    }

    public void start() {
        menu = new CampaignMenu(plugin, this);
        Bukkit.getPluginManager().registerEvents(menu, plugin);
        buffTask = Bukkit.getScheduler().runTaskTimer(plugin, this::applyBuffs, 40L, 100L);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> save(true), 600L, 600L);
        evaluate(); // A threshold lowered while the server was off still fires its milestone.
    }

    // ---------------------------------------------------------------------------------------------
    // Loading
    // ---------------------------------------------------------------------------------------------

    /** Reloads the season file. An invalid file keeps the season that is already running. */
    public synchronized List<String> reload() {
        List<String> messages = new ArrayList<>();
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(seasonFile);
            String id = yaml.getString("id", "");
            if (!id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("id: use 1-64 lowercase letters, digits, - or _.");
            File state = new File(seasonFile.getParentFile(), "state-" + id + ".yml");
            SeasonLedger loaded = season != null && season.id().equals(id) ? ledger
                : state.isFile() ? SeasonLedger.load(YamlConfiguration.loadConfiguration(state)) : new SeasonLedger();
            SeasonDefinition parsed = SeasonLoader.parse(yaml, loaded.firstLoaded(Instant.now()));
            List<SeasonValidator.Issue> found = SeasonValidator.validate(parsed, catalog());
            if (!SeasonValidator.loadable(found)) {
                found.stream().filter(SeasonValidator.Issue::error).forEach(issue -> messages.add(issue.message()));
                loadError = "The season file has errors" + (season == null ? "; no campaign is running." : "; keeping the running season.");
                messages.add(loadError);
                return messages;
            }
            if (season != null && !season.id().equals(id)) save(false);
            season = parsed;
            ledger = loaded;
            stateFile = state;
            issues = found;
            loadError = null;
            dirty = true;
            found.forEach(issue -> messages.add("Warning: " + issue.message()));
        } catch (Exception failure) {
            loadError = failure.getMessage();
            messages.add("campaign/season.yml: " + failure.getMessage() + (season == null ? "" : " Keeping the running season."));
            plugin.getLogger().warning("Campaign season did not load: " + failure.getMessage());
        }
        return messages;
    }

    private SeasonValidator.Catalog catalog() {
        return new SeasonValidator.Catalog() {
            @Override public boolean material(String key) { return Material.matchMaterial(key) != null; }
            @Override public boolean mob(String id) {
                if (plugin.mobs() == null) return true; // Checked again by /campaign admin check once mobs load.
                try { plugin.mobs().encounterType(id); return true; } catch (IllegalArgumentException unknown) { return false; }
            }
            @Override public boolean item(String id) {
                if (plugin.items() == null) return true;
                try { plugin.items().create(new ItemId(id)); return true; } catch (IllegalArgumentException unknown) { return false; }
            }
            @Override public boolean effect(String key) {
                NamespacedKey parsed = NamespacedKey.fromString(key);
                return parsed != null && Registry.MOB_EFFECT.get(parsed) != null;
            }
            @Override public boolean expedition(String id) {
                return plugin.expeditions() == null || plugin.expeditions().definition(id).isPresent();
            }
        };
    }

    public Optional<SeasonDefinition> season() { return Optional.ofNullable(season); }
    public SeasonLedger ledger() { return ledger; }
    public List<SeasonValidator.Issue> issues() { return issues; }
    public Optional<String> loadError() { return Optional.ofNullable(loadError); }
    public boolean active() { return season != null && season.active(Instant.now()); }

    // ---------------------------------------------------------------------------------------------
    // Queries used by other systems
    // ---------------------------------------------------------------------------------------------

    /** The milestone title that still seals an expedition, or empty when it is open. */
    public Optional<String> expeditionLock(String expedition) {
        if (season == null) return Optional.empty();
        return season.unlocking(expedition).filter(milestone -> !ledger.reached(milestone.id())).map(Milestone::title);
    }

    public boolean featureUnlocked(String feature) {
        if (season == null) return false;
        return season.milestones().values().stream().anyMatch(milestone -> ledger.reached(milestone.id())
            && milestone.rewards().stream().anyMatch(reward -> reward instanceof Reward.Unlock unlock && feature.equals(unlock.feature())));
    }

    public int boostPercent(BoostKind kind) { return season == null ? 0 : ledger.boostPercent(kind, Instant.now()); }

    // ---------------------------------------------------------------------------------------------
    // Sources
    // ---------------------------------------------------------------------------------------------

    /** Credits one action to every goal that counts it. {@code tier} only matters for expeditions. */
    void credit(Player player, SourceType type, String value, long amount, String tier, boolean hostile) {
        if (season == null || amount <= 0 || !season.active(Instant.now())) return;
        Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        if (account.isEmpty()) return;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        ledger.name(account.get().toString(), player.getName());
        boolean counted = false;
        for (Goal goal : season.goals().values()) {
            long points = 0;
            for (Source source : goal.sources()) {
                if (source.type() != type) continue;
                boolean matches = source.accepts(value) || hostile && source.filters().contains("hostile");
                if (!matches) continue;
                double multiplier = tier == null ? 1 : source.tierMultipliers().getOrDefault(tier, 1.0);
                points += Math.round(source.points() * amount * multiplier);
            }
            if (points <= 0) continue;
            long cap = goal.dailyCap() != null ? goal.dailyCap() : season.playerDailyCap();
            long applied = ledger.add(account.get().toString(), goal.id(), points, today, cap);
            counted |= applied > 0;
            if (applied < points && capNotices.add(account.get() + "|" + goal.id() + "|" + today)) {
                player.sendActionBar(Chat.render("<yellow>You've reached today's limit for " + Chat.escape(goal.title()) + ".</yellow>"));
            }
        }
        if (counted) {
            dirty = true;
            evaluate();
        }
    }

    public void recordExpedition(Player player, String expedition, String tier) { credit(player, SourceType.EXPEDITION, expedition, 1, tier, false); }

    public void recordBoss(Player player, String mob, String expedition) { credit(player, SourceType.BOSS, mob, 1, null, true); }

    private boolean eligible(Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        if (!eligible(event.getPlayer()) || plugin.resources().claim(event, HarvestClaims.Channel.CAMPAIGN).isEmpty()) return;
        credit(event.getPlayer(), SourceType.MINE, key(event.getBlock().getType()), 1, null, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceExtractEvent event) {
        credit(event.getPlayer(), SourceType.SMELT, key(event.getItemType()), event.getItemAmount(), null, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) return;
        ItemStack result = event.getRecipe().getResult();
        credit(player, SourceType.CRAFT, key(result.getType()), Math.max(1, result.getAmount()), null, false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item item)) return;
        credit(event.getPlayer(), SourceType.FISH, key(item.getItemStack().getType()), item.getItemStack().getAmount(), null, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null || entity instanceof Player || plugin.mobs() == null) return;
        // Expedition runs with hired allies earn no campaign progress at all.
        if (plugin.expeditions() != null && plugin.expeditions().assisted(entity.getWorld())) return;
        String id = plugin.mobs().encounterId(entity);
        boolean hostile = entity instanceof Enemy;
        credit(killer, SourceType.KILL, id, 1, null, hostile);
        // Expedition bosses credit the whole party from the run instead.
        if (plugin.mobs().isBoss(entity) && !InstanceWorlds.isRunWorld(entity.getWorld())) credit(killer, SourceType.BOSS, id, 1, null, true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || season == null) return;
            plugin.sessions().account(player.getUniqueId()).ifPresent(account -> {
                if (!ledger.claimable(season, account.toString()).isEmpty())
                    Chat.notice(player, "You have campaign rewards waiting: " + Chat.command("campaign claim"));
            });
            applyBuffs(player, Instant.now());
        }, 60L);
    }

    private static String key(Material material) { return material.name().toLowerCase(Locale.ROOT); }

    // ---------------------------------------------------------------------------------------------
    // Milestones and rewards
    // ---------------------------------------------------------------------------------------------

    private void evaluate() {
        if (season == null) return;
        for (Milestone milestone : ledger.evaluate(season, Instant.now())) {
            dirty = true;
            announce(milestone);
        }
    }

    private void announce(Milestone milestone) {
        Bukkit.broadcast(Chat.render(BrandPalette.title("SEASON MILESTONE") + " <white>" + Chat.escape(milestone.title()) + " reached!</white>"));
        for (Reward reward : milestone.rewards()) {
            try {
                switch (reward) {
                    case Reward.Buff buff -> Bukkit.broadcast(Chat.render("<gray>  • Everyone gets</gray> <aqua>" + words(buff.effect())
                        + (buff.amplifier() > 0 ? " " + (buff.amplifier() + 1) : "") + "</aqua> <gray>for " + Chat.duration(buff.duration()) + ".</gray>"));
                    case Reward.Boost boost -> Bukkit.broadcast(Chat.render("<gray>  • </gray><aqua>+" + boost.percent() + "% " + boost.kind().label()
                        + "</aqua> <gray>for " + Chat.duration(boost.duration()) + ".</gray>"));
                    case Reward.Unlock unlock -> {
                        if (unlock.expedition() != null) {
                            String title = plugin.expeditions() == null ? unlock.expedition()
                                : plugin.expeditions().definition(unlock.expedition()).map(definition -> definition.title()).orElse(unlock.expedition());
                            Bukkit.broadcast(Chat.render("<gray>  • The expedition</gray> <gold>" + Chat.escape(title) + "</gold> <gray>is open:</gray> " + Chat.command("expedition")));
                        } else Bukkit.broadcast(Chat.render("<gray>  • </gray><aqua>" + words(unlock.feature()) + "</aqua> <gray>are now unlocked.</gray>"));
                    }
                    case Reward.Broadcast broadcast -> Bukkit.broadcast(Chat.render(broadcast.message()));
                    case Reward.Command command -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.command());
                    case Reward.Item item -> { }
                    case Reward.Marks marks -> { }
                }
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Campaign reward " + reward + " failed", failure);
            }
        }
        if (milestone.claimable()) Bukkit.broadcast(Chat.render("<gray>  • Claim your share with</gray> " + Chat.command("campaign claim") + "<gray>.</gray>"));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(Title.title(Chat.render("<gold>" + Chat.escape(milestone.title()) + "</gold>"),
                Chat.render("<gray>Season milestone reached</gray>")));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, .8F, 1F);
            applyBuffs(player, Instant.now());
        }
    }

    private void applyBuffs() {
        Instant now = Instant.now();
        for (Player player : Bukkit.getOnlinePlayers()) applyBuffs(player, now);
    }

    private void applyBuffs(Player player, Instant now) {
        for (SeasonLedger.ActiveBuff buff : ledger.activeBuffs(now)) {
            if (!buff.potion()) continue;
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(buff.effect()));
            if (type == null) continue;
            long left = SeasonLedger.remaining(buff, now).toSeconds();
            PotionEffect current = player.getPotionEffect(type);
            if (current != null && current.getAmplifier() > buff.amplifier()) continue; // Never weaken a stronger potion.
            if (current != null && current.getAmplifier() == buff.amplifier() && current.getDuration() > 300) continue;
            player.addPotionEffect(new PotionEffect(type, (int) Math.min(left, 30) * 20, buff.amplifier(), true, false, true));
        }
    }

    /** Delivers every unclaimed item and Marks reward for this player's account. */
    public void claim(Player player) {
        if (season == null) { Chat.notice(player, "No season is running."); return; }
        Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        if (account.isEmpty()) { Chat.accountLoading(player); return; }
        List<Milestone> claimable = ledger.claimable(season, account.get().toString());
        if (claimable.isEmpty()) { Chat.notice(player, "Nothing to claim right now. Milestone rewards appear here as the server reaches them."); return; }
        int claimed = 0;
        for (Milestone milestone : claimable) {
            List<ItemStack> items = new ArrayList<>();
            long marks = 0;
            for (Reward reward : milestone.rewards()) {
                if (reward instanceof Reward.Item item) items.add(item(item.item(), item.amount()));
                if (reward instanceof Reward.Marks value) marks += value.amount();
            }
            if (!fits(player, items)) {
                Chat.error(player, "Make room for " + items.size() + " item stacks to claim " + Chat.escape(milestone.title()) + ".");
                break;
            }
            if (!ledger.claim(account.get().toString(), milestone.id())) continue;
            dirty = true;
            player.getInventory().addItem(items.toArray(ItemStack[]::new));
            if (marks > 0) plugin.economy().credit(account.get(), marks, new EconomyService.TransactionReason("campaign.claim", season.id() + ":" + milestone.id()),
                "campaign-claim:" + season.id() + ":" + milestone.id() + ":" + account.get());
            Chat.success(player, "Claimed <white>" + Chat.escape(milestone.title()) + "</white>"
                + (items.isEmpty() ? "" : ": " + items.stream().mapToInt(ItemStack::getAmount).sum() + " items") + (marks > 0 ? " and " + Chat.marks(marks) : "") + ".");
            claimed++;
        }
        if (claimed > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, .7F, 1.2F);
            save(true);
        }
    }

    private boolean fits(Player player, List<ItemStack> items) {
        int free = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) if (slot == null || slot.getType().isAir()) free++;
        return free >= items.size();
    }

    ItemStack item(String id, int amount) {
        ItemStack item = null;
        try {
            item = plugin.items().create(new ItemId(id));
        } catch (IllegalArgumentException notAuthored) {
            Material material = Material.matchMaterial(id);
            if (material != null) item = new ItemStack(material);
        }
        if (item == null) item = new ItemStack(Material.PAPER);
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        return item;
    }

    /** {@code /campaign contribute [goal] [amount]}: hand in the held stack to a goal that takes it. */
    private void contribute(Player player, String[] args) {
        if (season == null || !season.active(Instant.now())) { Chat.notice(player, "No season is running."); return; }
        Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        if (account.isEmpty()) { Chat.accountLoading(player); return; }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) { Chat.error(player, "Hold the items you want to contribute."); return; }
        String material = key(held.getType());
        Map<Goal, Source> takers = new LinkedHashMap<>();
        for (Goal goal : season.goals().values()) for (Source source : goal.sources())
            if (source.type() == SourceType.CONTRIBUTE && source.accepts(material)) { takers.putIfAbsent(goal, source); }
        if (takers.isEmpty()) { Chat.error(player, "No campaign goal is collecting " + Chat.humanize(material) + " right now."); return; }
        String requested = args.length > 1 && !args[1].matches("\\d+") ? args[1].toLowerCase(Locale.ROOT) : null;
        Map.Entry<Goal, Source> target = takers.entrySet().stream().filter(entry -> requested == null || entry.getKey().id().equals(requested)).findFirst().orElse(null);
        if (target == null || requested == null && takers.size() > 1) {
            Chat.error(player, "Choose a goal: " + String.join(", ", takers.keySet().stream().map(Goal::id).toList()) + ".");
            return;
        }
        String rawAmount = args.length > 2 ? args[2] : args.length == 2 && args[1].matches("\\d+") ? args[1] : null;
        int amount = rawAmount == null ? held.getAmount() : Math.min(held.getAmount(), Integer.parseInt(rawAmount));
        Goal goal = target.getKey();
        long perItem = target.getValue().points();
        long cap = goal.dailyCap() != null ? goal.dailyCap() : season.playerDailyCap();
        long room = cap - ledger.today(account.get().toString(), goal.id(), LocalDate.now(ZoneOffset.UTC));
        int taken = (int) Math.min(amount, Math.max(0, room / perItem));
        if (taken <= 0) { Chat.notice(player, "You've reached today's limit for " + Chat.escape(goal.title()) + "."); return; }
        held.setAmount(held.getAmount() - taken);
        ledger.name(account.get().toString(), player.getName());
        long applied = ledger.add(account.get().toString(), goal.id(), taken * perItem, LocalDate.now(ZoneOffset.UTC), cap);
        dirty = true;
        Chat.success(player, "Contributed " + taken + " " + Chat.humanize(material) + " to <white>" + Chat.escape(goal.title()) + "</white> (+" + applied + ").");
        evaluate();
    }

    // ---------------------------------------------------------------------------------------------
    // Command
    // ---------------------------------------------------------------------------------------------

    public boolean command(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (action.equals("admin")) return admin(sender, args);
        if (!(sender instanceof Player player)) {
            if (action.equals("progress")) progress(sender);
            else Chat.playersOnly(sender);
            return true;
        }
        switch (action) {
            case "" -> menu.open(player);
            case "progress" -> progress(player);
            case "claim" -> claim(player);
            case "contribute" -> contribute(player, args);
            case "top" -> top(player, args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : null);
            default -> Chat.usage(player, "/campaign [progress|claim|contribute [goal] [amount]|top [goal]]");
        }
        return true;
    }

    private void progress(CommandSender sender) {
        if (season == null) { Chat.notice(sender, "No season is running."); return; }
        Instant now = Instant.now();
        Chat.send(sender, "<gold><bold>" + Chat.escape(season.title()) + "</bold></gold> <gray>· " + (season.active(now)
            ? "ends in " + Chat.duration(Duration.between(now, season.endsAt()).withNanos(0)) : now.isBefore(season.startsAt()) ? "starts soon" : "ended") + "</gray>");
        String account = sender instanceof Player player ? plugin.sessions().account(player.getUniqueId()).map(AccountId::toString).orElse(null) : null;
        for (Goal goal : season.goals().values()) {
            long points = ledger.points(goal.id());
            Chat.send(sender, "<aqua>" + Chat.escape(goal.title()) + "</aqua> " + VisualFormat.progressBar(points, goal.target(), 16)
                + " <white>" + VisualFormat.compact(points) + "/" + VisualFormat.compact(goal.target()) + "</white>"
                + (account == null ? "" : " <dark_gray>you: " + VisualFormat.compact(ledger.contributed(account, goal.id())) + "</dark_gray>"));
        }
        for (Milestone milestone : season.milestones().values()) {
            boolean reached = ledger.reached(milestone.id());
            Chat.send(sender, (reached ? "<green>✔ " : "<gray>○ ") + Chat.escape(milestone.title()) + (reached ? "</green>" : "</gray>")
                + " <dark_gray>" + Chat.escape(requirementText(milestone)) + "</dark_gray>");
        }
        for (SeasonLedger.ActiveBuff buff : ledger.activeBuffs(now))
            Chat.send(sender, "<light_purple>Active:</light_purple> <white>" + Chat.escape(buffName(buff)) + "</white> <gray>"
                + Chat.duration(SeasonLedger.remaining(buff, now).withNanos(0)) + " left</gray>");
        if (account != null && !ledger.claimable(season, account).isEmpty()) Chat.send(sender, "<yellow>Rewards to claim:</yellow> " + Chat.command("campaign claim"));
    }

    private void top(Player player, String goal) {
        if (season == null) { Chat.notice(player, "No season is running."); return; }
        if (goal != null && !season.goals().containsKey(goal)) { Chat.error(player, "Unknown goal. Goals: " + String.join(", ", season.goals().keySet()) + "."); return; }
        Chat.send(player, "<gold>Top contributors</gold> <gray>" + (goal == null ? "(all goals)" : "(" + Chat.escape(season.goals().get(goal).title()) + ")") + "</gray>");
        int rank = 1;
        for (var row : ledger.top(goal, 10)) {
            String name = ledger.name(row.getKey()).orElse("Someone");
            Chat.send(player, "<gray>" + rank++ + ".</gray> <white>" + Chat.escape(name) + "</white> <aqua>" + VisualFormat.compact(row.getValue()) + "</aqua>");
        }
    }

    private boolean admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN)) { Chat.noPermission(sender); return true; }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "reload" -> {
                List<String> messages = reload();
                if (loadError == null) Chat.success(sender, "Season reloaded: " + Chat.escape(season.title()) + ".");
                messages.forEach(message -> Chat.send(sender, "<yellow>" + Chat.escape(message) + "</yellow>"));
                evaluate();
            }
            case "check" -> {
                if (loadError != null) Chat.error(sender, Chat.escape(loadError));
                if (season == null) return true;
                List<SeasonValidator.Issue> found = SeasonValidator.validate(season, catalog());
                if (found.isEmpty()) Chat.success(sender, "The season file has no problems.");
                found.forEach(issue -> Chat.send(sender, (issue.error() ? "<red>✗ " : "<yellow>! ") + Chat.escape(issue.message())));
            }
            case "add" -> {
                if (args.length != 4 || season == null || !season.goals().containsKey(args[2].toLowerCase(Locale.ROOT))) {
                    Chat.usage(sender, "/campaign admin add <goal> <points, may be negative>");
                    return true;
                }
                ledger.adjust(args[2].toLowerCase(Locale.ROOT), Long.parseLong(args[3]));
                dirty = true;
                Chat.success(sender, "Goal adjusted.");
                evaluate();
            }
            default -> Chat.usage(sender, "/campaign admin <reload|check|add <goal> <points>>");
        }
        return true;
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("progress", "claim", "contribute", "top"));
            if (sender.hasPermission(ADMIN)) options.add("admin");
            return options;
        }
        if (season == null) return List.of();
        String action = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (action.equals("contribute") || action.equals("top"))) return List.copyOf(season.goals().keySet());
        if (action.equals("admin") && sender.hasPermission(ADMIN)) {
            if (args.length == 2) return List.of("reload", "check", "add");
            if (args.length == 3 && args[1].equalsIgnoreCase("add")) return List.copyOf(season.goals().keySet());
        }
        return List.of();
    }

    String requirementText(Milestone milestone) {
        List<String> parts = new ArrayList<>();
        milestone.requires().forEach((goal, amount) -> {
            Goal value = season.goals().get(goal);
            parts.add(VisualFormat.compact(amount) + " " + (value == null ? goal : value.title()));
        });
        return String.join(" + ", parts);
    }

    String buffName(SeasonLedger.ActiveBuff buff) { return plainBuffName(buff); }

    /** "Haste 2" or "+25% skill XP", for menus and the website. */
    public String plainBuffName(SeasonLedger.ActiveBuff buff) {
        return buff.potion() ? words(buff.effect()) + (buff.amplifier() > 0 ? " " + (buff.amplifier() + 1) : "")
            : "+" + buff.percent() + "% " + buff.boost().label();
    }

    /** {@code fire_resistance} → {@code Fire resistance}. */
    static String words(String id) {
        String text = id.replace('_', ' ').replace('-', ' ');
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // ---------------------------------------------------------------------------------------------
    // CampaignService (HUD, older callers)
    // ---------------------------------------------------------------------------------------------

    @Override
    public CompletionStage<Optional<CampaignSnapshot>> activeCampaign() {
        if (season == null || !season.active(Instant.now())) return CompletableFuture.completedFuture(Optional.empty());
        Map<String, Long> milestones = new LinkedHashMap<>();
        for (Milestone milestone : season.milestones().values())
            milestones.put(milestone.id(), milestone.requires().values().stream().mapToLong(Long::longValue).sum());
        return CompletableFuture.completedFuture(Optional.of(new CampaignSnapshot(season.id(), season.title(), season.startsAt(), season.endsAt(),
            ledger.total(), ledger.points(), milestones)));
    }

    @Override
    public CompletionStage<ContributionResult> contribute(AccountId accountId, String category, long points, String idempotencyKey) {
        if (season == null || !season.active(Instant.now())) return CompletableFuture.completedFuture(ContributionResult.NO_ACTIVE_CAMPAIGN);
        Goal goal = season.goals().get(category);
        if (goal == null) return CompletableFuture.completedFuture(ContributionResult.INVALID_CATEGORY);
        if (points <= 0) return CompletableFuture.completedFuture(ContributionResult.INVALID_POINTS);
        long applied = ledger.add(accountId.toString(), goal.id(), points, LocalDate.now(ZoneOffset.UTC),
            goal.dailyCap() != null ? goal.dailyCap() : season.playerDailyCap());
        if (applied == 0) return CompletableFuture.completedFuture(ContributionResult.DAILY_CAP_REACHED);
        dirty = true;
        Bukkit.getScheduler().runTask(plugin, this::evaluate);
        return CompletableFuture.completedFuture(ContributionResult.APPLIED);
    }

    @Override
    public CompletionStage<Boolean> claimReward(AccountId accountId, String milestoneId) {
        Optional<Player> player = plugin.sessions().player(accountId).map(Bukkit::getPlayer);
        if (player.isEmpty() || season == null || !ledger.reached(milestoneId) || ledger.claimed(accountId.toString(), milestoneId))
            return CompletableFuture.completedFuture(false);
        claim(player.get());
        return CompletableFuture.completedFuture(ledger.claimed(accountId.toString(), milestoneId));
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    private void save(boolean async) {
        if (!dirty || stateFile == null) return;
        dirty = false;
        YamlConfiguration snapshot = ledger.save();
        File target = stateFile;
        Runnable write = () -> {
            try {
                AtomicYamlFiles.save(snapshot, target);
            } catch (IOException failure) {
                dirty = true;
                plugin.getLogger().log(Level.SEVERE, "Could not save campaign progress", failure);
            }
        };
        if (async && plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }

    @Override
    public void close() {
        if (buffTask != null) buffTask.cancel();
        if (saveTask != null) saveTask.cancel();
        save(false);
    }
}

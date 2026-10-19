package net.nerdsmp.paper.engagement;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.session.SessionRegistry;
import net.nerdsmp.paper.resource.HarvestClaims;
import net.nerdsmp.paper.resource.ResourceMaterials;
import net.nerdsmp.paper.resource.ResourceProvenanceService;
import net.nerdsmp.paper.npc.ReputationContractService;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import net.nerdsmp.paper.storage.StablePlayerKey;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EngagementService implements Listener, AutoCloseable {
    public List<DailyQuestDefinition> definitions() { return List.copyOf(questPool); }
    private final JavaPlugin plugin;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final ReputationContractService reputationContracts;
    private final ResourceProvenanceService resources;
    private final String storageMode;
    private final String seasonId;
    private final File dataFile;
    private final List<DailyQuestDefinition> questPool;
    private final Map<Integer, Long> playtimeRewards;
    private final Map<String, PlayerProgress> progress = new HashMap<>();
    private final Map<UUID, Long> lastActiveAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSampleAt = new HashMap<>();
    private final Set<String> pendingRewards = new HashSet<>();
    private final int questsPerDay;
    private final long rotationSeed;
    private final long activeTimeoutMillis;
    private final int sampleSeconds;
    private final int saveEverySamples;
    private int samplesSinceSave;
    private LocalDate activeDate = LocalDate.now(ZoneOffset.UTC);
    private BukkitTask sampleTask;

    public EngagementService(
        JavaPlugin plugin,
        SessionRegistry sessions,
        EconomyService economy,
        ReputationContractService reputationContracts,
        ResourceProvenanceService resources
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.economy = economy;
        this.reputationContracts = reputationContracts;
        this.resources = resources;
        storageMode = plugin.getConfig().getString("storage.mode", "memory");
        seasonId = plugin.getConfig().getString("season.id", "season-1");
        String safeSeason = seasonId.replaceAll("[^a-zA-Z0-9_-]", "_");
        dataFile = new File(plugin.getDataFolder(), "engagement-" + safeSeason + ".yml");
        questPool = loadQuestPool();
        questsPerDay = Math.max(1, Math.min(questPool.size(),
            plugin.getConfig().getInt("engagement.daily-quests-per-day", 3)));
        rotationSeed = plugin.getConfig().getLong("engagement.daily-rotation-seed", 76123L);
        activeTimeoutMillis = Math.max(60_000L,
            plugin.getConfig().getLong("engagement.active-timeout-seconds", 300L) * 1000L);
        sampleSeconds = Math.max(10, Math.min(60,
            plugin.getConfig().getInt("engagement.sample-seconds", 20)));
        int saveMinutes = Math.max(1, plugin.getConfig().getInt("engagement.save-interval-minutes", 5));
        saveEverySamples = Math.max(1, saveMinutes * 60 / sampleSeconds);
        playtimeRewards = loadPlaytimeRewards();
        loadProgress();
    }

    public void start() {
        if (sampleTask != null) {
            return;
        }
        long period = sampleSeconds * 20L;
        sampleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sampleOnlinePlayers, period, period);
    }

    public void showDaily(Player player) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(player);
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        PlayerProgress playerProgress = progress(account, player.getUniqueId(), today);
        player.sendRichMessage(net.nerdsmp.paper.presentation.BrandPalette.title("Daily contracts") + " "
            + "<gray>Reset at 00:00 UTC</gray>");
        for (DailyQuestDefinition quest : quests(today)) {
            int current = Math.min(quest.target(), playerProgress.questProgress.getOrDefault(quest.id(), 0));
            boolean claimed = playerProgress.claimedQuests.contains(quest.id());
            player.sendRichMessage((claimed ? "<green>✓ " : "<gold>• ") + escape(quest.title())
                + "</" + (claimed ? "green" : "gold") + "> <gray>" + current + "/" + quest.target()
                + " — " + quest.markReward() + " Marks</gray>\n<dark_gray>  "
                + escape(quest.description()) + "</dark_gray>");
        }
    }

    public java.util.Optional<DailyJournal> dailyJournal(Player player) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            return java.util.Optional.empty();
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        PlayerProgress playerProgress = progress(account, player.getUniqueId(), today);
        List<DailyQuestView> entries = quests(today).stream().map(quest -> new DailyQuestView(
            quest.id(),
            quest.title(),
            quest.description(),
            quest.action(),
            Math.min(quest.target(), playerProgress.questProgress.getOrDefault(quest.id(), 0)),
            quest.target(),
            quest.markReward(),
            playerProgress.claimedQuests.contains(quest.id()), forestryQuestExperience(quest)
        )).toList();
        return java.util.Optional.of(new DailyJournal(today, entries));
    }

    public void showPlaytime(Player player) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) {
            Chat.accountLoading(player);
            return;
        }
        PlayerProgress playerProgress = progress(account, player.getUniqueId(), LocalDate.now(ZoneOffset.UTC));
        player.sendRichMessage("<aqua><bold>Active playtime</bold></aqua> <white>"
            + formatDuration(playerProgress.activeSeconds) + "</white>");
        playtimeRewards.forEach((minutes, marks) -> player.sendRichMessage(
            (playerProgress.claimedPlaytime.contains(minutes) ? "<green>✓ " : "<dark_gray>• ")
                + formatDuration(minutes * 60L) + "</" 
                + (playerProgress.claimedPlaytime.contains(minutes) ? "green" : "dark_gray") + ">"
                + " <gray>—</gray> " + Chat.marksTag(marks)
        ));
        player.sendRichMessage("<gray>AFK time does not count.</gray>");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        long now = System.currentTimeMillis();
        lastActiveAt.put(event.getPlayer().getUniqueId(), now);
        lastSampleAt.put(event.getPlayer().getUniqueId(), now);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        sample(event.getPlayer(), System.currentTimeMillis());
        lastActiveAt.remove(event.getPlayer().getUniqueId());
        lastSampleAt.remove(event.getPlayer().getUniqueId());
        saveProgress();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
            || event.getFrom().getBlockY() != event.getTo().getBlockY()
            || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            active(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        active(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        active(event.getPlayer());
        if (!isOre(event.getBlock().getType()) && !isLog(event.getBlock().getType())) return;
        if (resources.claim(event, HarvestClaims.Channel.QUESTS).isEmpty()) return;
        Player player=event.getPlayer();AccountId account=sessions.account(player.getUniqueId()).orElse(null);
        QuestAction action=isOre(event.getBlock().getType())?QuestAction.MINE_ORE:QuestAction.CHOP_LOG;
        String original=event.getBlock().getBlockData().getAsString();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!event.isCancelled()&&event.isDropItems()&&player.isOnline()&&Bukkit.getPlayer(player.getUniqueId())==player
                &&java.util.Objects.equals(account,sessions.account(player.getUniqueId()).orElse(null))
                &&!event.getBlock().getBlockData().getAsString().equals(original)) addQuestProgress(player,action,1);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && event.getEntity() instanceof Monster) {
            active(killer);
            addQuestProgress(killer, QuestAction.SLAY_MONSTER, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            active(player);
            addQuestProgress(player, QuestAction.CRAFT_ITEM,
                Math.max(1, event.getRecipe().getResult().getAmount()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            active(event.getPlayer());
            addQuestProgress(event.getPlayer(), QuestAction.CATCH_FISH, 1);
        }
    }

    private void sampleOnlinePlayers() {
        long now = System.currentTimeMillis();
        rotateDayIfNeeded();
        plugin.getServer().getOnlinePlayers().forEach(player -> sample(player, now));
        samplesSinceSave++;
        if (samplesSinceSave >= saveEverySamples) {
            saveProgress();
            samplesSinceSave = 0;
        }
    }

    private void sample(Player player, long now) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        Long previousSample = lastSampleAt.put(player.getUniqueId(), now);
        Long lastActive = lastActiveAt.get(player.getUniqueId());
        if (account == null || previousSample == null || lastActive == null || now - lastActive > activeTimeoutMillis) {
            return;
        }
        long elapsed = Math.max(0L, Math.min(sampleSeconds * 2L, (now - previousSample) / 1000L));
        if (elapsed == 0L) {
            return;
        }
        PlayerProgress playerProgress = progress(account, player.getUniqueId(), LocalDate.now(ZoneOffset.UTC));
        playerProgress.activeSeconds = Math.addExact(playerProgress.activeSeconds, elapsed);
        checkPlaytimeRewards(player, account, playerProgress);
    }

    private void addQuestProgress(Player player, QuestAction action, int amount) {
        reputationContracts.recordProgress(player, action, amount);
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null || amount <= 0) {
            return;
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        PlayerProgress playerProgress = progress(account, player.getUniqueId(), today);
        for (DailyQuestDefinition quest : quests(today)) {
            if (quest.action() != action || playerProgress.claimedQuests.contains(quest.id())) {
                continue;
            }
            int next = Math.min(quest.target(), playerProgress.questProgress.getOrDefault(quest.id(), 0) + amount);
            playerProgress.questProgress.put(quest.id(), next);
            if (next >= quest.target()) {
                rewardQuest(player, account, playerProgress, today, quest);
            }
        }
    }

    private void rewardQuest(
        Player player,
        AccountId account,
        PlayerProgress playerProgress,
        LocalDate date,
        DailyQuestDefinition quest
    ) {
        String pending = "quest:" + date + ":" + subject(account, player.getUniqueId()) + ":" + quest.id();
        if (!pendingRewards.add(pending)) {
            return;
        }
        economy.credit(account, quest.markReward(),
            new EconomyService.TransactionReason("daily.quest", quest.title()),
            "daily-quest:" + date + ":" + subject(account, player.getUniqueId()) + ":" + quest.id()
        ).thenCompose(result->{
            if(result==null||(result.status()!=EconomyService.TransactionResult.Status.APPLIED&&result.status()!=EconomyService.TransactionResult.Status.DUPLICATE))
                return java.util.concurrent.CompletableFuture.<EconomyService.TransactionResult>failedFuture(new IllegalStateException("Daily quest payment was rejected"));
            long experience=forestryQuestExperience(quest);
            if(experience>0&&plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core)
                return core.progression().awardExperience(account,net.nerdsmp.api.model.Skill.FORESTRY,experience,pending+":forestry-xp").thenApply(ignored->result);
            return java.util.concurrent.CompletableFuture.completedFuture(result);
        }).whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            pendingRewards.remove(pending);
            if (error == null && (result.status() == EconomyService.TransactionResult.Status.APPLIED
                || result.status() == EconomyService.TransactionResult.Status.DUPLICATE)) {
                if(playerProgress.questDate.equals(date)) playerProgress.claimedQuests.add(quest.id());
                saveProgress();
                if (player.isOnline()) {
                    player.sendRichMessage("<green>Daily complete:</green> <white>" + escape(quest.title())
                        + "</white> <gold>+" + Chat.marks(quest.markReward()) + "</gold>"
                        +(forestryQuestExperience(quest)>0?" <aqua>+"+forestryQuestExperience(quest)+" Forestry XP</aqua>":""));
                    if(plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core) core.presentation().refresh(player);
                }
            }
        }));
    }

    private long forestryQuestExperience(DailyQuestDefinition quest) {
        return quest.action()==QuestAction.CHOP_LOG?Math.clamp(plugin.getConfig().getLong("forestry.daily-contract-xp",500),0,10_000):0;
    }

    private void checkPlaytimeRewards(Player player, AccountId account, PlayerProgress playerProgress) {
        long totalMinutes = playerProgress.activeSeconds / 60L;
        playtimeRewards.forEach((minutes, marks) -> {
            if (totalMinutes < minutes || playerProgress.claimedPlaytime.contains(minutes)) {
                return;
            }
            String subject = subject(account, player.getUniqueId());
            String pending = "playtime:" + seasonId + ":" + subject + ":" + minutes;
            if (!pendingRewards.add(pending)) {
                return;
            }
            economy.credit(account, marks,
                new EconomyService.TransactionReason("playtime.milestone", minutes + " active minutes"),
                pending
            ).whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                pendingRewards.remove(pending);
                if (error == null && (result.status() == EconomyService.TransactionResult.Status.APPLIED
                    || result.status() == EconomyService.TransactionResult.Status.DUPLICATE)) {
                    playerProgress.claimedPlaytime.add(minutes);
                    saveProgress();
                    if (player.isOnline()) {
                        player.sendRichMessage("<aqua>Active playtime milestone:</aqua> <white>"
                            + formatDuration(minutes * 60L) + "</white> <gold>+" + Chat.marks(marks) + "</gold>");
                    }
                }
            }));
        });
    }

    private PlayerProgress progress(AccountId account, UUID playerId, LocalDate date) {
        PlayerProgress found = progress.computeIfAbsent(subject(account, playerId), ignored -> new PlayerProgress());
        found.resetDaily(date);
        return found;
    }

    private String subject(AccountId account, UUID playerId) {
        return StablePlayerKey.forStorage(storageMode, account, playerId);
    }

    private List<DailyQuestDefinition> quests(LocalDate date) {
        return DailyQuestRotation.select(questPool, date, questsPerDay, rotationSeed);
    }

    private void active(Player player) {
        lastActiveAt.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void rotateDayIfNeeded() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(activeDate)) {
            activeDate = today;
            progress.values().forEach(value -> value.resetDaily(today));
            saveProgress();
        }
    }

    private Map<Integer, Long> loadPlaytimeRewards() {
        return readPlaytimeRewards(plugin.getConfig());
    }

    static Map<Integer, Long> readPlaytimeRewards(org.bukkit.configuration.file.FileConfiguration config) {
        String path = "engagement.playtime-rewards";
        // A virtual default section has no local keys. Resolve the actual bundled section,
        // but never merge default milestones into an operator's explicitly authored schedule.
        ConfigurationSection section = net.nerdsmp.paper.storage.ConfigurationDefaults.section(config, path);
        if (section == null || section.getKeys(false).isEmpty()) {
            throw new IllegalStateException("engagement.playtime-rewards must define at least one milestone");
        }
        Map<Integer, Long> loaded = new LinkedHashMap<>();
        section.getKeys(false).stream().map(Integer::parseInt).sorted().forEach(minutes -> {
            long marks = section.getLong(Integer.toString(minutes));
            if (minutes < 1 || marks < 1L || marks > 10000L) {
                throw new IllegalStateException("Invalid playtime reward milestone: " + minutes);
            }
            loaded.put(minutes, marks);
        });
        return Map.copyOf(loaded);
    }

    private List<DailyQuestDefinition> loadQuestPool() {
        String resource = plugin instanceof net.nerdsmp.paper.NerdSmpPlugin nerd && nerd.skyblockEnabled()
            ? "content/skyblock-daily-quests.yml" : "content/daily-quests.yml";
        File target = new File(plugin.getDataFolder(), resource);
        if (!target.exists()) {
            plugin.saveResource(resource, false);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try { yaml.load(target); } catch (Exception error) { throw new IllegalStateException("Cannot load " + resource, error); }
        if (yaml.getInt("schema-version", 0) < 1) {
            try (var reader = new InputStreamReader(
                java.util.Objects.requireNonNull(plugin.getResource(resource)),
                StandardCharsets.UTF_8
            )) {
                yaml = YamlConfiguration.loadConfiguration(reader);
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to load bundled daily quests", exception);
            }
        }
        ConfigurationSection quests = yaml.getConfigurationSection("quests");
        if (quests == null) {
            throw new IllegalStateException("content/daily-quests.yml is missing the quests section");
        }
        List<DailyQuestDefinition> loaded = new ArrayList<>();
        for (String id : quests.getKeys(false)) {
            ConfigurationSection section = quests.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            QuestAction action;
            try {
                action = QuestAction.valueOf(section.getString("action", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid daily quest action: " + id, exception);
            }
            loaded.add(new DailyQuestDefinition(
                id, section.getString("title", id), section.getString("description", id), action,
                section.getInt("target"), section.getLong("marks")
            ));
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("At least one daily quest is required");
        }
        return List.copyOf(loaded);
    }

    private void loadProgress() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }
        for (String key : players.getKeys(false)) {
            ConfigurationSection section = players.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            PlayerProgress loaded = new PlayerProgress();
            loaded.activeSeconds = Math.max(0L, section.getLong("active-seconds"));
            loaded.claimedPlaytime.addAll(section.getIntegerList("claimed-playtime"));
            try {
                loaded.questDate = LocalDate.parse(section.getString("quest-date", activeDate.toString()));
            } catch (RuntimeException exception) {
                loaded.questDate = activeDate;
            }
            ConfigurationSection questProgress = section.getConfigurationSection("quest-progress");
            if (questProgress != null) {
                questProgress.getKeys(false).forEach(id ->
                    loaded.questProgress.put(id, Math.max(0, questProgress.getInt(id))));
            }
            loaded.claimedQuests.addAll(section.getStringList("claimed-quests"));
            loaded.resetDaily(activeDate);
            progress.put(key, loaded);
        }
    }

    private synchronized void saveProgress() {
        YamlConfiguration yaml = new YamlConfiguration();
        progress.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String path = "players." + entry.getKey();
            PlayerProgress value = entry.getValue();
            yaml.set(path + ".active-seconds", value.activeSeconds);
            yaml.set(path + ".claimed-playtime", value.claimedPlaytime.stream().sorted().toList());
            yaml.set(path + ".quest-date", value.questDate.toString());
            value.questProgress.forEach((id, amount) -> yaml.set(path + ".quest-progress." + id, amount));
            yaml.set(path + ".claimed-quests", value.claimedQuests.stream().sorted().toList());
        });
        try {
            AtomicYamlFiles.save(yaml, dataFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save engagement progress: " + exception.getMessage());
        }
    }

    private boolean isOre(Material material) {
        return ResourceMaterials.ore(material.name());
    }

    private boolean isLog(Material material) {
        return ResourceMaterials.log(material.name());
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        if (hours > 0L) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private String escape(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }

    @Override
    public void close() {
        if (sampleTask != null) {
            sampleTask.cancel();
            sampleTask = null;
        }
        long now = System.currentTimeMillis();
        plugin.getServer().getOnlinePlayers().forEach(player -> sample(player, now));
        saveProgress();
        lastActiveAt.clear();
        lastSampleAt.clear();
    }

    public record DailyJournal(LocalDate date, List<DailyQuestView> quests) {
        public DailyJournal {
            quests = List.copyOf(quests);
        }

        public int completed() {
            return (int) quests.stream().filter(DailyQuestView::complete).count();
        }
    }

    public record DailyQuestView(
        String id,
        String title,
        String description,
        QuestAction action,
        int progress,
        int target,
        long markReward,
        boolean complete,
        long forestryExperience
    ) {
        public DailyQuestView(String id,String title,String description,QuestAction action,int progress,int target,long marks,boolean complete) {
            this(id,title,description,action,progress,target,marks,complete,0);
        }
    }

    private static final class PlayerProgress {
        private long activeSeconds;
        private final Set<Integer> claimedPlaytime = new HashSet<>();
        private LocalDate questDate = LocalDate.now(ZoneOffset.UTC);
        private final Map<String, Integer> questProgress = new HashMap<>();
        private final Set<String> claimedQuests = new HashSet<>();

        private void resetDaily(LocalDate date) {
            if (!date.equals(questDate)) {
                questDate = date;
                questProgress.clear();
                claimedQuests.clear();
            }
        }
    }
}

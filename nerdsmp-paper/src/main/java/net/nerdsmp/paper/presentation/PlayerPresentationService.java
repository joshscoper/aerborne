package net.nerdsmp.paper.presentation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.AdventurerProgression;
import net.nerdsmp.api.model.PlatformIdentity;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.CampaignService;
import net.nerdsmp.api.service.EntitlementService;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerPresentationService implements Listener, AutoCloseable {
    private static final String OBJECTIVE_ID = "nerdhud";
    private static final int SIDEBAR_LINES = 9;

    private final NerdSmpPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerView> views = new ConcurrentHashMap<>();
    private final Map<UUID, ProgressionService.ProgressionSnapshot> progressionSnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> supporterStatus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> nextEntitlementRefresh = new ConcurrentHashMap<>();
    private final Set<UUID> hiddenHud = ConcurrentHashMap.newKeySet();
    private final boolean sidebarEnabled;
    private final boolean tabEnabled;
    private final boolean nameplatesEnabled;
    private final boolean chatEnabled;
    private BukkitTask refreshTask;
    private final Map<UUID, Object> refreshTokens = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> sidebarFrames = new java.util.HashMap<>();
    private final Map<UUID, TabFrame> tabFrames = new java.util.HashMap<>();
    private final Map<UUID, NameplateStyle> nameplateStyles = new java.util.HashMap<>();
    private volatile boolean closed;

    public PlayerPresentationService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        sidebarEnabled = plugin.getConfig().getBoolean("presentation.sidebar-enabled", true);
        tabEnabled = plugin.getConfig().getBoolean("presentation.tab-list-enabled", true);
        nameplatesEnabled = plugin.getConfig().getBoolean("presentation.nameplates-enabled", true);
        chatEnabled = plugin.getConfig().getBoolean("presentation.chat-format-enabled", true);
    }

    public void start() {
        long refreshTicks = Math.max(20L, plugin.getConfig().getLong("presentation.refresh-ticks", 20L));
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, refreshTicks);
    }

    public PlayerView view(Player player) {
        return views.getOrDefault(player.getUniqueId(), PlayerView.loading(platform(player)));
    }

    public ProgressionService.ProgressionSnapshot progression(Player player) {
        return progressionSnapshots.getOrDefault(player.getUniqueId(), ProgressionService.ProgressionSnapshot.empty());
    }

    public void updateSkillProgress(
        Player player,
        Skill skill,
        ProgressionService.SkillProgress incoming
    ) {
        ProgressionService.ProgressionSnapshot updated = progressionSnapshots.compute(player.getUniqueId(), (ignored, current) -> {
            ProgressionService.ProgressionSnapshot existing = current == null
                ? ProgressionService.ProgressionSnapshot.empty() : current;
            ProgressionService.SkillProgress stored = existing.skills().get(skill);
            if (stored != null && stored.experience() >= incoming.experience()) {
                return existing;
            }
            EnumMap<Skill, ProgressionService.SkillProgress> skills = new EnumMap<>(Skill.class);
            skills.putAll(existing.skills());
            skills.put(skill, incoming);
            return new ProgressionService.ProgressionSnapshot(
                skills, existing.masteries(), existing.talents(), existing.nextRespecAt());
        });
        PlayerView currentView = views.get(player.getUniqueId());
        if (currentView == null || !player.isOnline()) {
            return;
        }
        AdventurerProgression.Progress adventurer = updated.adventurer();
        PlayerView nextView = new PlayerView(
            currentView.marks(),
            adventurer.level(),
            adventurer.experienceIntoLevel(),
            adventurer.experienceNeeded(),
            adventurer.rank().displayName(),
            currentView.mastery(),
            currentView.campaignTitle(),
            currentView.campaignPoints(),
            currentView.milestone(),
            currentView.role(),
            currentView.platform()
        );
        views.put(player.getUniqueId(), nextView);
        apply(player, nextView);
    }

    public boolean toggleHud(Player player) {
        UUID playerId = player.getUniqueId();
        boolean visible;
        if (hiddenHud.remove(playerId)) {
            visible = true;
        } else {
            hiddenHud.add(playerId);
            visible = false;
        }
        apply(player, view(player));
        return visible;
    }

    public boolean hudVisible(Player player) {
        return sidebarEnabled && !hiddenHud.contains(player.getUniqueId());
    }

    public boolean chatEnabled() {
        return chatEnabled;
    }

    public void refresh(Player player) {
        if (closed || !player.isOnline()) return;
        AccountId accountId = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (accountId == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Object token = new Object();
        if (refreshTokens.putIfAbsent(playerId, token) != null) return;
        try {
        CompletableFuture<Long> balance = plugin.economy().balance(accountId).toCompletableFuture();
        CompletableFuture<ProgressionService.ProgressionSnapshot> progression =
            plugin.progression().snapshot(accountId).toCompletableFuture();
        CompletableFuture<java.util.Optional<CampaignService.CampaignSnapshot>> campaign =
            plugin.campaigns().activeCampaign().toCompletableFuture();
        long now = System.currentTimeMillis();
        boolean refreshEntitlements = nextEntitlementRefresh.getOrDefault(playerId, 0L) <= now;
        CompletableFuture<Boolean> supporter = !refreshEntitlements
            ? CompletableFuture.completedFuture(supporterStatus.getOrDefault(playerId, false))
            : plugin.entitlements().activeEntitlements(accountId, Instant.now()).toCompletableFuture()
                .thenApply(entitlements -> {
                    boolean active = entitlements.stream()
                        .map(EntitlementService.Entitlement::productId)
                        .anyMatch(product -> product.equals("supporter_monthly") || product.equals("supporter_annual"));
                    return active;
                });

        CompletableFuture.allOf(balance, progression, campaign, supporter)
            .orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).whenComplete((ignored, error) -> {
            if (error != null) {
                refreshTokens.remove(playerId, token);
                plugin.getLogger().warning("Could not refresh presentation for " + player.getName()
                    + ": " + error.getMessage());
                return;
            }
            runSync(() -> {
                if (!refreshTokens.remove(playerId, token) || !player.isOnline() || Bukkit.getPlayer(playerId) != player
                    || !plugin.sessions().account(playerId).filter(accountId::equals).isPresent()) {
                    return;
                }
                if (refreshEntitlements) { supporterStatus.put(playerId, supporter.join()); nextEntitlementRefresh.put(playerId, now + 60_000L); }
                // A skill award may arrive while the database snapshot is in flight. Do not roll its XP back.
                var latest = mergeSkillProgress(progression.join(), progressionSnapshots.get(playerId));
                PlayerView next = createView(
                    player,
                    balance.join(),
                    latest,
                    campaign.join().orElse(null),
                    supporter.join()
                );
                progressionSnapshots.put(player.getUniqueId(), latest);
                views.put(player.getUniqueId(), next);
                apply(player, next);
            });
        });
        } catch (RuntimeException error) {
            refreshTokens.remove(playerId, token);
            plugin.getLogger().warning("Could not start presentation refresh for " + player.getName() + ": " + error.getMessage());
        }
    }

    public Component chatPrefix(Player player) {
        PlayerView view = view(player);
        return switch (view.role()) {
            case STAFF -> miniMessage.deserialize("<red><bold>STAFF</bold></red> <dark_gray>•</dark_gray> ");
            case SUPPORTER -> miniMessage.deserialize("<gold>✦</gold> ");
            case ADVENTURER -> Component.empty();
        };
    }

    static ProgressionService.ProgressionSnapshot mergeSkillProgress(
        ProgressionService.ProgressionSnapshot fetched, ProgressionService.ProgressionSnapshot current
    ) {
        if (current == null) return fetched;
        EnumMap<Skill, ProgressionService.SkillProgress> skills = new EnumMap<>(Skill.class);
        skills.putAll(fetched.skills());
        current.skills().forEach((skill, progress) -> skills.merge(skill, progress,
            (left, right) -> left.experience() >= right.experience() ? left : right));
        // Masteries and talents can change independently (including a respec); use the fresh snapshot.
        return new ProgressionService.ProgressionSnapshot(skills, fetched.masteries(), fetched.talents(), fetched.nextRespecAt());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        event.joinMessage(miniMessage.deserialize("<dark_gray>[<green>+</green>]</dark_gray> <gray>"
            + player.getName() + " joined.</gray>"));
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(player), 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(miniMessage.deserialize("<dark_gray>[<red>−</red>]</dark_gray> <gray>"
            + player.getName() + " left.</gray>"));
        boards.values().forEach(board -> {
            Team team = board.getEntryTeam(player.getName());
            if (team != null && team.getName().startsWith("np")) {
                team.removeEntry(player.getName());
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        });
        views.remove(player.getUniqueId());
        progressionSnapshots.remove(player.getUniqueId());
        boards.remove(player.getUniqueId());
        supporterStatus.remove(player.getUniqueId());
        nextEntitlementRefresh.remove(player.getUniqueId());
        hiddenHud.remove(player.getUniqueId());
        clearFrames(player.getUniqueId());
    }

    @Override
    public void close() {
        closed = true;
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        boards.clear();
        views.clear();
        progressionSnapshots.clear();
        supporterStatus.clear();
        nextEntitlementRefresh.clear();
        hiddenHud.clear();
        refreshTokens.clear(); sidebarFrames.clear(); tabFrames.clear(); nameplateStyles.clear();
    }
    private void clearFrames(UUID id) { refreshTokens.remove(id); sidebarFrames.remove(id); tabFrames.remove(id); nameplateStyles.remove(id); }

    private void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }

    private PlayerView createView(
        Player player,
        long marks,
        ProgressionService.ProgressionSnapshot progression,
        CampaignService.CampaignSnapshot campaign,
        boolean supporter
    ) {
        AdventurerProgression.Progress adventurer = progression.adventurer();
        String mastery = progression.masteries().stream()
            .map(Skill::displayName)
            .sorted()
            .reduce((left, right) -> left + " / " + right)
            .orElse("Undecided");
        Role role = player.hasPermission("nerdsmp.admin") ? Role.STAFF
            : supporter ? Role.SUPPORTER : Role.ADVENTURER;
        String campaignTitle = campaign == null ? "No campaign" : campaign.title();
        long campaignPoints = campaign == null ? 0L : campaign.totalPoints();
        String milestone = campaign == null ? "Nothing scheduled" : VisualFormat.nextMilestone(
            campaign.totalPoints(), campaign.milestones()
        ).map(next -> VisualFormat.compact(next.remaining()) + " to " + VisualFormat.titleCase(next.id()))
            .orElse("All milestones reached");
        return new PlayerView(
            marks,
            adventurer.level(),
            adventurer.experienceIntoLevel(),
            adventurer.experienceNeeded(),
            adventurer.rank().displayName(),
            mastery,
            campaignTitle,
            campaignPoints,
            milestone,
            role,
            platform(player)
        );
    }

    private void apply(Player player, PlayerView view) {
        float experienceBar = view.adventurerExperienceNeeded() <= 0L ? 1.0F
            : (float) Math.max(0.0D, Math.min(1.0D,
                (double) view.adventurerExperience() / view.adventurerExperienceNeeded()));
        // Keep the periodic XP correction: vanilla orb/respawn packets can overwrite the client bar.
        player.sendExperienceChange(experienceBar, view.adventurerLevel());
        boolean newBoard = !boards.containsKey(player.getUniqueId());
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), ignored -> createBoard(player));
        if (player.getScoreboard() != board) {
            player.setScoreboard(board);
        }
        applySidebar(board, player, view);
        applyTab(player, view);
        if (newBoard) {
            applyNameplates(board);
        }
        updateNameplate(player);
    }

    private Scoreboard createBoard(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective(
            OBJECTIVE_ID,
            Criteria.DUMMY,
            miniMessage.deserialize(plugin.branding().sidebarTitleMarkup())
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (int index = 0; index < SIDEBAR_LINES; index++) {
            String entry = entry(index);
            Team team = board.registerNewTeam("hud_" + index);
            team.addEntry(entry);
            objective.getScore(entry).setScore(SIDEBAR_LINES - index);
        }
        player.setScoreboard(board);
        return board;
    }

    private void applySidebar(Scoreboard board, Player player, PlayerView view) {
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            return;
        }
        if (!sidebarEnabled || hiddenHud.contains(player.getUniqueId())) {
            if (objective.getDisplaySlot() != null) board.clearSlot(DisplaySlot.SIDEBAR);
            sidebarFrames.remove(player.getUniqueId());
            return;
        }
        if (objective.getDisplaySlot() != DisplaySlot.SIDEBAR) objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        var island = plugin.skyblock() == null ? null : plugin.skyblock().islands()
            .at(player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4)
            .filter(i -> plugin.skyblock().isIslandWorld(player.getWorld())).orElse(null);
        var islandProgress = island == null ? null : plugin.skyblock().development().progress(island.owner());
        List<String> lines = List.of(
            "<dark_gray>────────────</dark_gray>",
            "<gray>Player</gray> <white>" + player.getName() + "</white>",
            "<gray>Rank</gray> <light_purple>" + view.adventurerRank() + "</light_purple>",
            "<gray>Adventurer</gray> <aqua>Lv " + view.adventurerLevel() + "</aqua>",
            view.adventurerExperienceNeeded() <= 0L
                ? "<gray>XP</gray> <gold>MAX</gold>"
                : "<gray>XP</gray> <white>" + VisualFormat.compact(view.adventurerExperience()) + "/"
                    + VisualFormat.compact(view.adventurerExperienceNeeded()) + "</white>",
            "<gray>Marks</gray> <gold>" + VisualFormat.compact(view.marks()) + " ✦</gold>",
            " ",
            island == null ? "<white>" + escape(view.campaignTitle()) + "</white>"
                : "<white>" + escape(island.name()) + "</white> <aqua>Lv " + islandProgress.level() + "</aqua>",
            island == null ? "<gray>Progress</gray> <aqua>" + VisualFormat.compact(view.campaignPoints()) + "</aqua>"
                : "<gray>" + islandProgress.progressLabel() + "</gray>"
        );
        List<String> previous = sidebarFrames.put(player.getUniqueId(), lines);
        for (int index = 0; index < lines.size(); index++) {
            if (previous != null && lines.get(index).equals(previous.get(index))) continue;
            Team team = board.getTeam("hud_" + index);
            if (team != null) {
                team.prefix(miniMessage.deserialize(lines.get(index)));
            }
        }
    }

    private void applyTab(Player player, PlayerView view) {
        if (!tabEnabled) {
            if (tabFrames.remove(player.getUniqueId()) != null) {
                player.playerListName(null);
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            }
            return;
        }
        String role = switch (view.role()) {
            case STAFF -> "<red><bold>STAFF</bold></red>";
            case SUPPORTER -> "<gold>✦</gold>";
            case ADVENTURER -> "";
        };
        String separator = role.isEmpty() ? "" : " ";
        String listName = "<dark_gray>[</dark_gray><aqua>Lv " + view.adventurerLevel()
            + "</aqua> <dark_gray>•</dark_gray> <light_purple>" + escape(view.adventurerRank())
            + "</light_purple><dark_gray>]</dark_gray> " + role + separator
            + "<white>" + player.getName() + "</white>";
        String header = plugin.branding().tabHeaderMarkup();
        String footer = "<gray>" + Bukkit.getOnlinePlayers().size() + " online</gray> <dark_gray>•</dark_gray> "
                + "<gold>" + VisualFormat.compact(view.marks()) + " Marks</gold>\n"
                + "<gray>" + escape(plugin.branding().mode()) + "</gray>";
        TabFrame frame = new TabFrame(listName, header, footer), previous = tabFrames.put(player.getUniqueId(), frame);
        if (previous == null || !previous.name().equals(listName)) player.playerListName(miniMessage.deserialize(listName));
        if (previous == null || !previous.header().equals(header) || !previous.footer().equals(footer))
            player.sendPlayerListHeaderAndFooter(miniMessage.deserialize(header), miniMessage.deserialize(footer));
    }

    private void applyNameplates(Scoreboard board) {
        if (!nameplatesEnabled) {
            return;
        }
        Bukkit.getOnlinePlayers().forEach(target -> applyNameplate(board, target));
    }

    private void updateNameplate(Player target) {
        if (!nameplatesEnabled) {
            return;
        }
        PlayerView view = view(target);
        NameplateStyle next = new NameplateStyle(view.adventurerLevel(), view.adventurerRank(), view.role(), view.platform());
        if (next.equals(nameplateStyles.put(target.getUniqueId(), next))) return;
        boards.values().forEach(board -> applyNameplate(board, target));
    }

    private void applyNameplate(Scoreboard board, Player target) {
        PlayerView targetView = view(target);
        String teamId = nameplateTeamId(target, targetView);
        Team previousTeam = board.getEntryTeam(target.getName());
        if (previousTeam != null && previousTeam.getName().startsWith("np")
            && !previousTeam.getName().equals(teamId)) {
            previousTeam.removeEntry(target.getName());
            if (previousTeam.getEntries().isEmpty()) {
                previousTeam.unregister();
            }
        }
        Team team = board.getTeam(teamId);
        if (team == null) {
            team = board.registerNewTeam(teamId);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
        }
        team.prefix(nameplatePrefix(targetView));
        team.color(nameplateColor(targetView));
        team.addEntry(target.getName());
    }

    private String nameplateTeamId(Player target, PlayerView view) {
        String order = switch (view.role()) {
            case STAFF -> "00";
            case SUPPORTER -> "10";
            case ADVENTURER -> "30";
        };
        String playerKey = target.getUniqueId().toString().replace("-", "").substring(0, 10);
        return "np" + order + playerKey;
    }

    private Component nameplatePrefix(PlayerView view) {
        Component level = miniMessage.deserialize("<dark_gray>[</dark_gray><aqua>Lv " + view.adventurerLevel()
            + "</aqua> <dark_gray>•</dark_gray> <light_purple>" + escape(view.adventurerRank())
            + "</light_purple><dark_gray>]</dark_gray> ");
        return switch (view.role()) {
            case STAFF -> level.append(miniMessage.deserialize("<red><bold>STAFF</bold></red> <dark_gray>◆</dark_gray> "));
            case SUPPORTER -> level.append(miniMessage.deserialize("<gold>✦</gold> "));
            case ADVENTURER -> level;
        };
    }

    private NamedTextColor nameplateColor(PlayerView view) {
        return switch (view.role()) {
            case STAFF -> NamedTextColor.RED;
            case SUPPORTER -> NamedTextColor.GOLD;
            case ADVENTURER -> NamedTextColor.WHITE;
        };
    }

    private PlatformIdentity.Platform platform(Player player) {
        return plugin.identities().identity(player).platform();
    }

    private String entry(int index) {
        return "§" + Integer.toHexString(index);
    }

    private String escape(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }

    private void runSync(Runnable task) {
        if (closed) return;
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
    private record TabFrame(String name, String header, String footer) { }
    private record NameplateStyle(int level, String rank, Role role, PlatformIdentity.Platform platform) { }

    public enum Role {
        STAFF,
        SUPPORTER,
        ADVENTURER
    }

    public record PlayerView(
        long marks,
        int adventurerLevel,
        long adventurerExperience,
        long adventurerExperienceNeeded,
        String adventurerRank,
        String mastery,
        String campaignTitle,
        long campaignPoints,
        String milestone,
        Role role,
        PlatformIdentity.Platform platform
    ) {
        private static PlayerView loading(PlatformIdentity.Platform platform) {
            return new PlayerView(0L, 1, 0L, 200L, "Wanderer", "Undecided", "No campaign", 0L,
                "Nothing scheduled", Role.ADVENTURER, platform);
        }
    }
}

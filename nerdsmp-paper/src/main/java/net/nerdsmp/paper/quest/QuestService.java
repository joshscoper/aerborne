package net.nerdsmp.paper.quest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.AdventurerProgression;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.ReputationService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.presentation.Chat;
import net.nerdsmp.paper.resource.ResourceMaterials;
import net.nerdsmp.paper.storage.OrderedYamlWriter;
import net.nerdsmp.paper.zone.ZoneDefinition;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;

/**
 * NPC story quests for the shared world. Quest givers open a quest board from their dialogue; objectives
 * count kills, gathering-node harvests, dig-site finds, visits and conversations inside world zones.
 */
public final class QuestService implements Listener, AutoCloseable {
    public static final String BUNDLED = "content/world-quests.yml";
    /** The Wardens' Reach's quests ship alongside; a server's own world-quests.yml never hides them. */
    public static final String REACH = "content/reach-quests.yml";
    private final NerdSmpPlugin plugin;
    private final File progressFile;
    private final Map<AccountId, QuestProgress> progress = new HashMap<>();
    private final Map<UUID, Integer> levels = new HashMap<>();
    private final Set<String> paying = new HashSet<>();
    private final Map<AccountId, String> tracked = new HashMap<>();
    private final QuestGuide guide;
    private BukkitTask levelTask;
    private QuestCatalog catalog = QuestCatalog.empty();
    private OrderedYamlWriter writer;
    private BukkitTask visitTask;

    public QuestService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        progressFile = new File(plugin.getDataFolder(), "quest-progress.yml");
        reload();
        load();
        guide = new QuestGuide(plugin, this);
    }

    public QuestCatalog catalog() { return catalog; }

    /** Uses a server's own content/world-quests.yml when present, otherwise the quests shipped in the jar. */
    public void reload() {
        File override = new File(plugin.getDataFolder(), BUNDLED);
        try {
            String text;
            if (override.isFile()) text = Files.readString(override.toPath(), StandardCharsets.UTF_8);
            else {
                var resource = plugin.getResource(BUNDLED);
                if (resource == null) { catalog = QuestCatalog.empty(); return; }
                try (var in = resource) { text = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
            }
            QuestCatalog loaded = QuestCatalog.parse(text);
            var reach = plugin.getResource(REACH);
            if (reach != null) try (var in = reach) {
                loaded = loaded.merge(QuestCatalog.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            }
            catalog = loaded;
        } catch (Exception failure) {
            plugin.getLogger().log(Level.SEVERE, "Story quests could not load; the previous quests stay active", failure);
        }
    }

    public void start() {
        visitTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkVisits, 40L, 20L);
        levelTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refreshLevel), 60L, 1200L);
        guide.start();
        List<String> problems = new ArrayList<>();
        for (QuestDefinition quest : catalog.quests().values()) {
            for (String npc : List.of(quest.giver(), quest.turnIn()))
                if (!plugin.npcs().definitionIds().contains(npc) && !expeditionGivers().containsKey(npc)) problems.add(quest.id() + " uses unknown NPC " + npc);
            for (QuestDefinition.Objective objective : quest.objectives()) {
                for (String target : objective.targets()) if (objective.type() == QuestDefinition.Type.KILL && target.startsWith("custom:")
                    && !plugin.mobs().ids().contains(target.substring(7))) problems.add(quest.id() + " hunts unknown mob " + target);
                if (objective.type() == QuestDefinition.Type.TALK) for (String npc : objective.targets())
                    if (!plugin.npcs().definitionIds().contains(npc)) problems.add(quest.id() + " talks to unknown NPC " + npc);
            }
        }
        problems.forEach(problem -> plugin.getLogger().warning("Story quest content: " + problem));
    }

    @Override public void close() {
        if (visitTask != null) visitTask.cancel();
        if (levelTask != null) levelTask.cancel();
        guide.close();
        save();
    }

    // ---------------------------------------------------------------- objectives

    /** A finished expedition run counts toward CLEAR objectives naming that expedition. */
    public void expeditionCleared(Player player, String expedition) {
        record(player, QuestDefinition.Type.CLEAR, expedition, null, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        Player killer = dead.getKiller();
        if (killer == null || dead instanceof Player) return;
        String mob = plugin.mobs().encounterId(dead);
        String zone = zoneAt(dead.getLocation());
        record(killer, QuestDefinition.Type.KILL, mob, zone, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;
        if (event.getBlock().getBlockData() instanceof org.bukkit.block.data.Ageable age
            && age.getAge() < age.getMaximumAge()) return;
        if (plugin.resources().claim(event, net.nerdsmp.paper.resource.HarvestClaims.Channel.STORY_QUESTS).isEmpty()) return;
        String material = event.getBlock().getType().name();
        String original = event.getBlock().getBlockData().getAsString();
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        String zone = zoneAt(event.getBlock().getLocation().add(0.5, 0.5, 0.5));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.isCancelled() || !event.isDropItems() || !player.isOnline()
                || Bukkit.getPlayer(player.getUniqueId()) != player || account == null
                || !plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent()
                || event.getBlock().getBlockData().getAsString().equals(original)) return;
            record(player, QuestDefinition.Type.HARVEST, material, zone, 1);
            if (ResourceMaterials.excavation(material)) record(player, QuestDefinition.Type.DIG, material, zone, 1);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        levels.remove(event.getPlayer().getUniqueId());
        guide.forget(event.getPlayer());
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) { refreshLevel(player); guide.refresh(player); } }, 40L);
    }

    /** Adventurer levels gate quests; markers need them without opening a board first. */
    private void refreshLevel(Player player) {
        refreshLevel(player, () -> { });
    }

    private void refreshLevel(Player player, Runnable refreshed) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) return;
        plugin.progression().snapshot(account).whenComplete((snapshot, error) -> {
            if (error != null || snapshot == null) return;
            int level = Math.max(1, AdventurerProgression.fromSkills(snapshot.skills()).level());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && plugin.sessions().account(player.getUniqueId()).filter(account::equals).isPresent()) {
                    levels.put(player.getUniqueId(), level);
                    publish(player);
                    refreshed.run();
                }
            });
        });
    }

    int level(Player player) {
        return plugin.presentation() == null ? levels.getOrDefault(player.getUniqueId(), 1)
            : Math.max(levels.getOrDefault(player.getUniqueId(), 1), plugin.presentation().view(player).adventurerLevel());
    }

    public record Counts(int available, int ready) { }

    /** Quests this NPC can give the player now, and quests the player can hand in to them. */
    public Counts counts(Player player, String npc) {
        QuestProgress log = log(player);
        if (log == null) return new Counts(0, 0);
        int available = 0, ready = 0;
        for (QuestDefinition quest : catalog.offeredBy(npc)) {
            QuestProgress.Status status = status(player, log, quest);
            if (status == QuestProgress.Status.READY && quest.turnIn().equals(npc)) ready++;
            else if (status == QuestProgress.Status.AVAILABLE && quest.giver().equals(npc)) available++;
        }
        return new Counts(available, ready);
    }

    /** The marker over an NPC for this player: a hand-in beats a new quest, which beats one in progress. */
    QuestGuide.Mark mark(Player player, String npc) {
        QuestProgress log = log(player);
        if (log == null) return QuestGuide.Mark.NONE;
        boolean ready = false, dailyReady = false, available = false, dailyAvailable = false, active = false, locked = false;
        for (QuestDefinition quest : catalog.involving(npc)) {
            QuestProgress.Status status = status(player, log, quest);
            boolean turnIn = quest.turnIn().equals(npc), giver = quest.giver().equals(npc);
            switch (status) {
                case READY -> { if (turnIn) { if (quest.daily()) dailyReady = true; else ready = true; } }
                case AVAILABLE -> { if (giver) { if (quest.daily()) dailyAvailable = true; else available = true; } }
                case ACTIVE -> {
                    if (turnIn) active = true;
                    int[] counts = counts(player, quest);
                    for (int i = 0; i < quest.objectives().size(); i++) {
                        var objective = quest.objectives().get(i);
                        if (objective.type() == QuestDefinition.Type.TALK && objective.matches(npc) && counts[i] < objective.count()) active = true;
                    }
                }
                case LOCKED -> { if (giver) locked = true; }
                default -> { }
            }
        }
        if (ready) return QuestGuide.Mark.READY;
        if (dailyReady) return QuestGuide.Mark.DAILY_READY;
        if (available) return QuestGuide.Mark.AVAILABLE;
        if (dailyAvailable) return QuestGuide.Mark.DAILY_AVAILABLE;
        if (active) return QuestGuide.Mark.ACTIVE;
        if (locked) return QuestGuide.Mark.LOCKED;
        return QuestGuide.Mark.NONE;
    }

    /** The quest the HUD and the trail follow: the chosen one while active, else the oldest active quest. */
    Optional<QuestDefinition> tracked(Player player) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        QuestProgress log = account == null ? null : progress.get(account);
        if (log == null || log.active().isEmpty()) return Optional.empty();
        String chosen = tracked.get(account);
        if (chosen != null && log.active().containsKey(chosen) && catalog.quests().containsKey(chosen))
            return Optional.of(catalog.quests().get(chosen));
        return log.active().keySet().stream().map(catalog.quests()::get).filter(java.util.Objects::nonNull).findFirst();
    }

    int[] counts(Player player, QuestDefinition quest) {
        QuestProgress log = log(player);
        if (log == null || !log.active().containsKey(quest.id())) return null;
        int[] result = log.counts(quest).clone();
        if (quest.objectives().stream().noneMatch(objective -> objective.type() == QuestDefinition.Type.DELIVER)) return result;
        int[] deliveries = deliveryAllocation(player, quest).counts();
        for (int i = 0; i < result.length; i++)
            if (quest.objectives().get(i).type() == QuestDefinition.Type.DELIVER) result[i] = deliveries[i];
        return result;
    }

    private QuestProgress.Status status(Player player, QuestProgress log, QuestDefinition quest) {
        return log.active().containsKey(quest.id())
            ? QuestProgress.done(quest, counts(player, quest)) ? QuestProgress.Status.READY : QuestProgress.Status.ACTIVE
            : log.status(quest, level(player));
    }

    private void publish(Player player) {
        guide.refresh(player);
        QuestProgress log = log(player);
        if (log != null) sendBook(player, log, false);
    }

    String npcTitle(String npc) { return npcName(npc); }

    /** How many quests this player has under way. */
    int activeCount(Player player) { QuestProgress log = log(player); return log == null ? 0 : log.active().size(); }

    /** Whether the player has taken on any quest, now or before. */
    public boolean started(Player player) { QuestProgress log = log(player); return log != null && (!log.active().isEmpty() || !log.completed().isEmpty()); }

    /** The quest chains this account has finished: every non-daily quest of the chain completed. */
    public Set<String> completedChains(AccountId account) {
        QuestProgress log = progress.get(account);
        if (log == null) return Set.of();
        Map<String, Boolean> chains = new HashMap<>();
        for (QuestDefinition quest : catalog.quests().values()) {
            if (quest.daily()) continue;
            chains.merge(quest.chain(), log.completed().contains(quest.id()), Boolean::logicalAnd);
        }
        Set<String> done = new java.util.TreeSet<>();
        chains.forEach((chain, complete) -> { if (complete) done.add(chain); });
        return done;
    }

    /** Whether the player has finished this quest. */
    public boolean completed(Player player, String quest) { QuestProgress log = log(player); return log != null && log.completed().contains(quest); }

    String zoneName(String zone) { return zoneTitle(zone); }

    private void track(Player player, String quest) {
        plugin.sessions().account(player.getUniqueId()).ifPresent(account -> {
            if (quest == null) tracked.remove(account); else tracked.put(account, quest);
        });
        guide.show(player);
        save();
        publish(player);
    }

    private void checkVisits() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            QuestProgress log = log(player);
            if (log == null || log.active().isEmpty()) continue;
            boolean changed = false;
            for (String id : List.copyOf(log.active().keySet())) {
                QuestDefinition quest = catalog.quests().get(id);
                if (quest == null) continue;
                int[] stored = log.counts(quest), current = counts(player, quest);
                boolean wasDone = QuestProgress.done(quest, stored);
                if (!java.util.Arrays.equals(stored, current)) {
                    System.arraycopy(current, 0, stored, 0, current.length);
                    changed = true;
                    if (!wasDone && QuestProgress.done(quest, current)) readyMessage(player, quest);
                }
            }
            if (changed) { save(); publish(player); }
            String zone = zoneAt(player.getLocation());
            if (zone != null) record(player, QuestDefinition.Type.VISIT, zone, zone, 1);
        }
    }

    private void record(Player player, QuestDefinition.Type type, String target, String zone, int amount) {
        record(player, type, target, zone, amount, null);
    }

    private void record(Player player, QuestDefinition.Type type, String target, String zone, int amount, String onlyQuest) {
        if (plugin.spirits()!=null && plugin.spirits().ghost(player.getUniqueId())) return;
        QuestProgress log = log(player);
        if (log == null || log.active().isEmpty()) return;
        boolean changed = false;
        for (String id : List.copyOf(log.active().keySet())) {
            if (onlyQuest != null && !onlyQuest.equals(id)) continue;
            QuestDefinition quest = catalog.quests().get(id);
            if (quest == null) continue;
            boolean wasDone = QuestProgress.done(quest, log.counts(quest));
            int[] current = counts(player, quest);
            if (!java.util.Arrays.equals(log.counts(quest), current)) {
                System.arraycopy(current, 0, log.counts(quest), 0, current.length);
                changed = true;
            }
            for (int index = 0; index < quest.objectives().size(); index++) {
                QuestDefinition.Objective objective = quest.objectives().get(index);
                if (objective.type() != type || !objective.matches(target)
                    || objective.zone() != null && !objective.zone().equals(zone)) continue;
                int before = log.counts(quest)[index];
                boolean finished = log.advance(quest, index, amount);
                if (log.counts(quest)[index] == before) continue;
                changed = true;
                int now = log.counts(quest)[index];
                if (finished) player.sendRichMessage("<green>✔</green> <white>" + Chat.escape(objective.description())
                    + "</white> <gray>(" + Chat.escape(quest.title()) + ")</gray>");
                else if (objective.count() > 1) net.nerdsmp.paper.item.PlayerStatsService.message(player, Chat.render("<gold>" + Chat.escape(quest.title()) + "</gold> <gray>·</gray> <white>"
                    + Chat.escape(objective.description()) + "</white> <yellow>" + now + "/" + objective.count() + "</yellow>"));
            }
            if (!wasDone && QuestProgress.done(quest, counts(player, quest))) readyMessage(player, quest);
        }
        if (changed) { save(); publish(player); }
    }

    private void readyMessage(Player player, QuestDefinition quest) {
        player.sendRichMessage("<gold><bold>Quest ready:</bold></gold> <white>" + Chat.escape(quest.title())
            + "</white> <gray>Return to " + Chat.escape(npcName(quest.turnIn())) + ".</gray>");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.4F);
    }

    private String zoneAt(Location location) {
        if (location.getWorld() == null) return null;
        return plugin.zones().at(location.getWorld().getUID(), location.getX(), location.getY(), location.getZ())
            .filter(ZoneDefinition::enabled).map(ZoneDefinition::id).orElse(null);
    }

    // ---------------------------------------------------------------- quest board

    public List<QuestDialogue.Topic> dialogueTopics(Player player, String npc) {
        QuestProgress log = log(player);
        if (log == null) return List.of();
        List<QuestDialogue.Topic> topics = new ArrayList<>();
        for (QuestDefinition quest : catalog.involving(npc)) {
            boolean giver = quest.giver().equals(npc), turnIn = quest.turnIn().equals(npc);
            QuestProgress.Status state = status(player, log, quest);
            int[] counts = counts(player, quest);
            boolean talk = false, contact = false;
            List<String> report = new ArrayList<>();
            for (int i = 0; i < quest.objectives().size(); i++) {
                var objective = quest.objectives().get(i);
                if (objective.type() != QuestDefinition.Type.TALK || !objective.matches(npc)) continue;
                contact = true;
                report.addAll(objective.dialogue());
                if (counts != null && counts[i] < objective.count()
                    && (objective.zone() == null || objective.zone().equals(zoneAt(player.getLocation())))) talk = true;
            }
            if (state == QuestProgress.Status.LOCKED || state == QuestProgress.Status.AVAILABLE && !giver
                || state == QuestProgress.Status.COMPLETED && (!turnIn || quest.daily())
                || !giver && !turnIn && !(contact && counts != null)) continue;
            List<String> body = new ArrayList<>();
            body.add("<gold>" + Chat.escape(quest.title()) + "</gold>");
            if (state == QuestProgress.Status.COMPLETED) {
                quest.complete().forEach(line -> body.add(questLine(player, line)));
            } else {
                if (giver) quest.offer().forEach(line -> body.add(questLine(player, line)));
                else body.add(talk ? "You wanted to ask me about this?" : "Let's see how you're getting on.");
                for (int i = 0; i < quest.objectives().size(); i++) {
                    var objective = quest.objectives().get(i);
                    body.add("<white>" + Chat.escape(objective.description()) + "</white> <yellow>"
                        + (counts == null ? 0 : counts[i]) + "/" + objective.count() + "</yellow>"
                        + (objective.zone() == null ? "" : " <gray>(" + Chat.escape(zoneTitle(objective.zone())) + ")</gray>"));
                }
                if (!turnIn) body.add("<gray>Report to " + Chat.escape(npcName(quest.turnIn())) + " when finished.</gray>");
                // Keep the page inside the client's 24-line limit; rewards remain visible before acceptance.
                body.addAll(rewardLore(quest.rewards()).stream().limit(Math.max(0, 24 - body.size())).toList());
            }
            if (report.isEmpty()) report.add("Your message has reached me. See the remaining objectives in your journal, then report to "
                + npcName(quest.turnIn()) + ".");
            topics.add(new QuestDialogue.Topic(quest.id(), quest.title(), state, giver, turnIn, talk,
                QuestDialogue.plain(body.stream().limit(24).toList()),
                QuestDialogue.plain(report.stream().map(line -> questLine(player, line)).toList())));
        }
        topics.sort(java.util.Comparator.comparingInt(topic -> switch (topic.status()) {
            case READY -> topic.turnIn() ? 0 : 3;
            case ACTIVE -> topic.talk() ? 1 : 3;
            case AVAILABLE -> 2;
            default -> 4;
        }));
        return List.copyOf(topics);
    }

    private String questLine(Player player, String text) {
        return Chat.escape(text.replace("{player}", player.getName()));
    }

    /** Called only from the NPC's validated, single-use conversation response. Rechecks live quest state. */
    public String dialogueAction(Player player, String npc, String action, String id) {
        QuestDefinition quest = catalog.quests().get(id);
        QuestProgress log = log(player);
        if (quest == null || log == null) return null;
        QuestDialogue.Topic topic = dialogueTopics(player, npc).stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
        if (topic == null) return null;
        switch (action) {
            case "accept" -> {
                if (topic.status() != QuestProgress.Status.AVAILABLE || !topic.giver()) break;
                accept(player, quest, log);
                return QuestDialogue.DETAIL + id;
            }
            case "talk" -> {
                if (!topic.talk()) break;
                recordTalk(player, quest, npc);
                return QuestDialogue.REPORT + id;
            }
            case "complete" -> {
                if (topic.status() != QuestProgress.Status.READY || !topic.turnIn()) break;
                turnIn(player, quest, log, npc);
                return quest.daily() ? null : QuestDialogue.DETAIL + id;
            }
            case "track" -> {
                if (!log.active().containsKey(id)) break;
                track(player, id);
                return QuestDialogue.DETAIL + id;
            }
            default -> { }
        }
        Chat.notice(player, "That quest has changed. Here's your current progress.");
        return QuestDialogue.DETAIL + id;
    }

    private void recordTalk(Player player, QuestDefinition quest, String npc) {
        // A quest-specific reply must not silently finish every other conversation with this NPC.
        record(player, QuestDefinition.Type.TALK, npc, zoneAt(player.getLocation()), 1, quest.id());
    }

    /** Opens an NPC's quest board once the player's adventurer level is known. */
    public void open(Player player, String npc) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) { Chat.accountLoading(player); return; }
        plugin.progression().snapshot(account).whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            int level = error == null && snapshot != null ? AdventurerProgression.fromSkills(snapshot.skills()).level() : 1;
            levels.put(player.getUniqueId(), Math.max(1, level));
            render(player, npc);
        }));
    }

    private void render(Player player, String npc) {
        render(player, npc, 0);
    }

    private void render(Player player, String npc, int requestedPage) {
        QuestProgress log = log(player);
        if (log == null) return;
        int level = level(player);
        List<QuestDefinition> quests = catalog.offeredBy(npc);
        int pages = Math.max(1, (quests.size() + 27) / 28);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        Board board = new Board(npc, page, pages);
        Inventory inventory = Bukkit.createInventory(board, 54, Chat.render("<dark_gray>" + Chat.escape(npcName(npc)) + " · Quests</dark_gray>"));
        board.inventory = inventory;
        ItemStack pane = icon(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < 54; slot++) inventory.setItem(slot, pane);
        int index = 0;
        if (quests.isEmpty()) inventory.setItem(13, icon(Material.BOOK, "<gray>No quests here yet</gray>", List.of()));
        for (QuestDefinition quest : quests.stream().skip(page * 28L).limit(28).toList()) {
            int slot = 10 + (index / 7) * 9 + index % 7;
            QuestProgress.Status status = status(player, log, quest);
            inventory.setItem(slot, questIcon(player, quest, status, npc, log, level));
            board.slots.put(slot, quest.id());
            index++;
        }
        if (page > 0) inventory.setItem(45, icon(Material.ARROW, "<gold>Previous quests</gold>", List.of()));
        if (page + 1 < pages) inventory.setItem(53, icon(Material.ARROW, "<gold>More quests</gold>", List.of()));
        inventory.setItem(49, icon(Material.WRITABLE_BOOK, "<gold>Quest journal</gold>", List.of("<gray>Your active quests: <white>/journal</white></gray>")));
        player.openInventory(inventory);
    }

    private ItemStack questIcon(Player player, QuestDefinition quest, QuestProgress.Status status, String npc, QuestProgress log, int level) {
        List<String> lore = new ArrayList<>();
        lore.add(quest.daily() ? "<blue>Daily quest · resets at midnight UTC</blue>"
            : "<dark_gray>" + Chat.escape(chainTitle(quest)) + " · Level " + quest.minimumLevel() + "+</dark_gray>");
        lore.add("");
        quest.offer().forEach(line -> lore.add("<white>" + questLine(player, line) + "</white>"));
        lore.add("");
        int[] counts = counts(player, quest);
        for (int i = 0; i < quest.objectives().size(); i++) {
            QuestDefinition.Objective objective = quest.objectives().get(i);
            int have = counts == null ? 0 : counts[i];
            boolean met = counts != null && have >= objective.count();
            lore.add((met ? "<green>✔ " : "<gray>• ") + Chat.escape(objective.description())
                + (objective.count() > 1 && counts != null ? " <yellow>" + have + "/" + objective.count() + "</yellow>" : "")
                + (met ? "</green>" : "</gray>"));
        }
        lore.add("");
        lore.addAll(rewardLore(quest.rewards()));
        lore.add("");
        Material material;
        String name;
        switch (status) {
            case AVAILABLE -> {
                material = Material.WRITABLE_BOOK;
                name = quest.daily() ? "<blue>! " + Chat.escape(quest.title()) + "</blue>" : "<yellow>! " + Chat.escape(quest.title()) + "</yellow>";
                lore.add(quest.giver().equals(npc) ? "<green>Click to accept</green>" : "<gray>Offered by " + Chat.escape(npcName(quest.giver())) + "</gray>");
            }
            case ACTIVE -> {
                material = Material.CLOCK; name = "<gold>" + Chat.escape(quest.title()) + "</gold>";
                lore.add("<gray>In progress.</gray>");
            }
            case READY -> {
                material = Material.EMERALD;
                name = quest.daily() ? "<blue>? " + Chat.escape(quest.title()) + "</blue>" : "<green>? " + Chat.escape(quest.title()) + "</green>";
                lore.add(quest.turnIn().equals(npc) ? "<green>Click to turn in</green>" : "<gray>Return to " + Chat.escape(npcName(quest.turnIn())) + "</gray>");
            }
            case COMPLETED -> {
                material = Material.LIME_DYE; name = "<dark_green>" + Chat.escape(quest.title()) + "</dark_green>";
                lore.add(quest.daily() ? "<dark_green>Done for today. Back tomorrow.</dark_green>" : "<dark_green>Completed</dark_green>");
            }
            default -> {
                material = Material.BOOK; name = "<dark_gray>" + Chat.escape(quest.title()) + "</dark_gray>";
                if (level < quest.minimumLevel()) lore.add("<red>Requires adventurer level " + quest.minimumLevel() + "</red>");
                for (String required : quest.requires()) if (!log.completed().contains(required)) {
                    QuestDefinition before = catalog.quests().get(required);
                    lore.add("<red>Complete " + Chat.escape(before == null ? required : before.title()) + " first</red>");
                }
            }
        }
        return icon(material, name, lore);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Board board)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 49 || event.getRawSlot() == 45 || event.getRawSlot() == 53) {
            int slot = event.getRawSlot();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != board.inventory) return;
                if (slot == 49) { player.closeInventory(); journal(player); }
                else if (slot == 45 && board.page > 0) render(player, board.npc, board.page - 1);
                else if (slot == 53 && board.page + 1 < board.pages) render(player, board.npc, board.page + 1);
            });
            return;
        }
        String id = board.slots.get(event.getRawSlot());
        QuestDefinition quest = id == null ? null : catalog.quests().get(id);
        QuestProgress log = log(player);
        if (quest == null || log == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory() != board.inventory || log(player) != log) return;
            QuestProgress.Status status = status(player, log, quest);
            if (status == QuestProgress.Status.AVAILABLE && quest.giver().equals(board.npc)) {
                accept(player, quest, log);
                render(player, board.npc, board.page);
            } else if (status == QuestProgress.Status.READY && quest.turnIn().equals(board.npc)) {
                turnIn(player, quest, log, board.npc);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Board) event.setCancelled(true);
    }

    private void accept(Player player, QuestDefinition quest, QuestProgress log) {
        if (!log.accept(quest)) return;
        save();
        plugin.metrics().event(player,"quest.accepted",1);
        track(player, quest.id());
        String speaker = npcName(quest.giver());
        player.sendMessage(Component.empty());
        for (String line : quest.offer()) player.sendRichMessage("<gold>" + Chat.escape(speaker) + ":</gold> <white>" + Chat.escape(line.replace("{player}", player.getName())) + "</white>");
        player.sendRichMessage("<yellow>Quest accepted:</yellow> <white>" + Chat.escape(quest.title()) + "</white>");
        for (QuestDefinition.Objective objective : quest.objectives())
            player.sendRichMessage(" <gray>•</gray> <white>" + Chat.escape(objective.description()) + "</white>"
                + (objective.zone() != null ? " <dark_gray>(" + Chat.escape(zoneTitle(objective.zone())) + ")</dark_gray>" : ""));
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8F, 1.0F);
    }

    private void turnIn(Player player, QuestDefinition quest, QuestProgress log, String npc) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account == null) { Chat.accountLoading(player); return; }
        if (!quest.turnIn().equals(npc) || status(player, log, quest) != QuestProgress.Status.READY) {
            Chat.error(player, "Finish the objectives and bring the requested items before turning this quest in.");
            publish(player);
            return;
        }
        QuestDeliveries.Allocation delivery = deliveryAllocation(player, quest);
        String operation = "story-quest:" + account + ":" + quest.id() + (quest.daily() ? ":" + QuestProgress.today() : "");
        if (!paying.add(operation)) return;
        take(player, delivery.consumed());
        log.complete(quest);
        save();
        plugin.metrics().event(player,"quest.completed",1);
        plugin.metrics().event(player,"questComplete."+net.nerdsmp.paper.metrics.MetricsLedger.dimension(quest.id()),1);
        publish(player);
        player.closeInventory();
        player.sendMessage(Component.empty());
        for (String line : quest.complete()) player.sendRichMessage("<gold>" + Chat.escape(npcName(npc)) + ":</gold> <white>" + Chat.escape(line.replace("{player}", player.getName())) + "</white>");
        player.sendRichMessage("<green><bold>Quest complete:</bold></green> <white>" + Chat.escape(quest.title()) + "</white>");
        net.nerdsmp.paper.presentation.ClientFeeds.toast(player, "quest", Chat.render("<green>Quest complete</green>"),
            Chat.render("<white>" + Chat.escape(quest.title()) + "</white>"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.2F);
        grant(player, account, quest, operation).whenComplete((ignored, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            paying.remove(operation);
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Story quest reward failed for " + account + " on " + quest.id(), error);
                if (player.isOnline()) Chat.error(player, "Part of the reward could not be paid. Staff can see the details in the server log.");
            }
            if (player.isOnline()) {
                plugin.presentation().refresh(player);
                refreshLevel(player, () -> {
                    for (QuestDefinition follow : catalog.quests().values()) {
                        if (!follow.requires().contains(quest.id()) || status(player, log, follow) != QuestProgress.Status.AVAILABLE) continue;
                        player.sendRichMessage("<yellow>New quest:</yellow> <white>" + Chat.escape(follow.title())
                            + "</white> <gray>from " + Chat.escape(npcName(follow.giver())) + "</gray>");
                    }
                });
            }
        }));
        for (QuestDefinition.ItemReward reward : quest.rewards().items()) {
            ItemStack stack = rewardStack(reward);
            if (stack == null) continue;
            player.sendRichMessage(" <white>+" + reward.amount() + "× " + Chat.escape(rewardName(reward)) + "</white>");
            for (int remaining = reward.amount(); remaining > 0;) {
                ItemStack portion = stack.clone();
                int amount = Math.min(remaining, portion.getMaxStackSize());
                portion.setAmount(amount);
                player.getInventory().addItem(portion).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
                remaining -= amount;
            }
        }
    }

    private CompletionStage<Void> grant(Player player, AccountId account, QuestDefinition quest, String operation) {
        QuestDefinition.Rewards rewards = quest.rewards();
        CompletionStage<?> chain = CompletableFuture.completedFuture(null);
        if (rewards.marks() > 0) {
            chain = chain.thenCompose(ignored -> plugin.economy().credit(account, rewards.marks(),
                new EconomyService.TransactionReason("story.quest", quest.title()), operation + ":marks"));
            player.sendRichMessage(" <gold>+" + Chat.marks(rewards.marks()) + "</gold>");
        }
        for (Map.Entry<String, Long> entry : rewards.experience().entrySet()) {
            Skill skill = Skill.valueOf(entry.getKey());
            chain = chain.thenCompose(ignored -> plugin.progression().awardExperience(account, skill, entry.getValue(), operation + ":" + skill.name()));
            player.sendRichMessage(" <aqua>+" + entry.getValue() + " " + skill.displayName() + " XP</aqua>");
        }
        for (Map.Entry<String, Integer> entry : rewards.reputation().entrySet()) {
            FactionId faction;
            try { faction = new FactionId(entry.getKey()); } catch (IllegalArgumentException invalid) { continue; }
            if (!plugin.npcs().content().factions().containsKey(faction)) continue;
            chain = chain.thenCompose(ignored -> plugin.reputation().adjust(account, faction, entry.getValue(),
                new ReputationService.ReputationReason("story.quest", quest.title()), operation + ":" + faction));
            player.sendRichMessage(" <light_purple>" + (entry.getValue() > 0 ? "+" : "") + entry.getValue() + " "
                + Chat.escape(plugin.npcs().content().faction(faction).name()) + " reputation</light_purple>");
        }
        return chain.thenApply(ignored -> null);
    }

    private ItemStack rewardStack(QuestDefinition.ItemReward reward) {
        try {
            if (reward.kind().equals("material")) {
                Material material = Material.matchMaterial(reward.id());
                if (material == null || !material.isItem()) return null;
                ItemStack stack = material == Material.EXPERIENCE_BOTTLE && plugin.skillLamps() != null
                    ? plugin.skillLamps().bottle(net.nerdsmp.paper.item.SkillLamps.Grade.PLAIN, reward.amount())
                    : new ItemStack(material, Math.min(material.getMaxStackSize(), reward.amount()));
                if (reward.name() != null || !reward.lore().isEmpty()) {
                    ItemMeta meta = stack.getItemMeta();
                    if (reward.name() != null) meta.displayName(Chat.render("<aqua>" + Chat.escape(reward.name()) + "</aqua>").decoration(TextDecoration.ITALIC, false));
                    if (!reward.lore().isEmpty()) meta.lore(reward.lore().stream().map(line ->
                        Chat.render("<gray>" + Chat.escape(line) + "</gray>").decoration(TextDecoration.ITALIC, false)).toList());
                    stack.setItemMeta(meta);
                }
                return stack;
            }
            ItemStack stack = plugin.items().create(new ItemId(reward.id()));
            stack.setAmount(Math.min(stack.getMaxStackSize(), reward.amount()));
            return stack;
        } catch (RuntimeException failure) {
            plugin.getLogger().warning("Story quest reward item " + reward.id() + " is unavailable: " + failure.getMessage());
            return null;
        }
    }

    private QuestDeliveries.Allocation deliveryAllocation(Player player, QuestDefinition quest) {
        Map<String, Integer> inventory = new HashMap<>();
        for (ItemStack stack : player.getInventory().getStorageContents())
            if (stack != null && net.nerdsmp.paper.item.FoodItems.ordinary(stack))
                inventory.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
        return QuestDeliveries.allocate(quest, inventory);
    }

    private void take(Player player, Map<String, Integer> allocated) {
        Map<String, Integer> remaining = new HashMap<>(allocated);
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || !net.nerdsmp.paper.item.FoodItems.ordinary(stack)) continue;
            String material = stack.getType().name();
            int used = Math.min(remaining.getOrDefault(material, 0), stack.getAmount());
            stack.setAmount(stack.getAmount() - used);
            remaining.computeIfPresent(material, (key, count) -> count - used);
            contents[i] = stack.getAmount() <= 0 ? null : stack;
        }
        player.getInventory().setStorageContents(contents);
    }

    // ---------------------------------------------------------------- journal command

    public static final String ADMIN_PERMISSION = "nerdsmp.quests.admin";

    public boolean command(org.bukkit.command.CommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) return admin(sender, args);
        if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
        if (args.length == 2 && args[0].equalsIgnoreCase("track")) {
            QuestProgress log = log(player);
            QuestDefinition quest = catalog.quests().get(args[1].toLowerCase(Locale.ROOT));
            if (log == null || quest == null || !log.active().containsKey(quest.id())) { Chat.error(player, "You are not on that quest."); return true; }
            track(player, quest.id());
            Chat.success(player, "Tracking <white>" + Chat.escape(quest.title()) + "</white>. Follow the gold trail.");
            sendBook(player, log, false);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("untrack")) {
            guide.hide(player);
            Chat.notice(player, "Quest trail hidden. <white>/journal track <quest></white> shows it again.");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("abandon")) {
            QuestProgress log = log(player);
            QuestDefinition quest = catalog.quests().get(args[1].toLowerCase(Locale.ROOT));
            if (log == null || quest == null || !log.abandon(quest.id())) { Chat.error(player, "You are not on that quest."); return true; }
            save();
            guide.refresh(player);
            Chat.notice(player, "Abandoned <white>" + Chat.escape(quest.title()) + "</white>. Its giver can offer it again.");
            sendBook(player, log, false);
            return true;
        }
        journal(player);
        return true;
    }

    /** Staff support: /journal admin <player> info | complete <quest> | reset [quest]. Completing grants no rewards. */
    private boolean admin(org.bukkit.command.CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) { Chat.noPermission(sender); return true; }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[1]) : null;
        QuestProgress log = target == null ? null : log(target);
        if (log == null) { Chat.usage(sender, "/journal admin <online player> info|complete <quest>|reset [quest]"); return true; }
        String action = args[2].toLowerCase(Locale.ROOT);
        QuestDefinition quest = args.length >= 4 ? catalog.quests().get(args[3].toLowerCase(Locale.ROOT)) : null;
        switch (action) {
            case "info" -> {
                sender.sendRichMessage("<gold>" + Chat.escape(target.getName()) + "</gold> <gray>active:</gray> <white>" + log.active().keySet()
                    + "</white> <gray>completed:</gray> <white>" + log.completed() + "</white>");
                return true;
            }
            case "complete" -> {
                if (quest == null) { Chat.error(sender, "Unknown quest."); return true; }
                log.complete(quest);
            }
            case "reset" -> {
                if (args.length >= 4 && quest == null) { Chat.error(sender, "Unknown quest."); return true; }
                if (quest == null) { log.active().clear(); log.completed().clear(); log.dailyDone().clear(); }
                else { log.active().remove(quest.id()); log.completed().remove(quest.id()); log.dailyDone().remove(quest.id()); }
            }
            default -> { Chat.usage(sender, "/journal admin <online player> info|complete <quest>|reset [quest]"); return true; }
        }
        save();
        publish(target);
        Chat.success(sender, "Updated " + Chat.escape(target.getName()) + "'s quest journal.");
        return true;
    }

    public List<String> complete(String[] args, org.bukkit.command.CommandSender sender) {
        boolean staff = sender.hasPermission(ADMIN_PERMISSION);
        if (staff && args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            if (args.length == 3) return List.of("info", "complete", "reset");
            if (args.length == 4) return List.copyOf(catalog.quests().keySet());
            return List.of();
        }
        if (args.length == 1) return staff ? List.of("track", "untrack", "abandon", "admin") : List.of("track", "untrack", "abandon");
        if (args.length == 2 && args[0].equalsIgnoreCase("track") && sender instanceof Player player) {
            QuestProgress log = log(player);
            return log == null ? List.of() : List.copyOf(log.active().keySet());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("abandon") && sender instanceof Player player) {
            QuestProgress log = log(player);
            return log == null ? List.of() : List.copyOf(log.active().keySet());
        }
        return List.of();
    }

    private void journal(Player player) {
        QuestProgress log = log(player);
        if (log == null) { Chat.accountLoading(player); return; }
        // The Aerborne client opens its own quest book instead of the chat listing.
        if (sendBook(player, log, true)) return;
        player.sendRichMessage("<gold><bold>Quest journal</bold></gold> <gray>(" + log.completed().size() + " completed)</gray>");
        if (log.active().isEmpty()) {
            player.sendRichMessage("<gray>No active quests. Look for NPCs with quests in Highcrown and the outposts.</gray>");
            return;
        }
        for (String id : log.active().keySet()) {
            QuestDefinition quest = catalog.quests().get(id);
            if (quest == null) continue;
            int[] counts = counts(player, quest);
            boolean ready = QuestProgress.done(quest, counts);
            boolean following = tracked(player).map(QuestDefinition::id).filter(id::equals).isPresent();
            player.sendRichMessage((following ? "<gold>➤ </gold>" : "") + (ready ? "<green>? " : "<yellow>! ") + Chat.escape(quest.title()) + (ready ? "</green>" : "</yellow>")
                + " <dark_gray>· " + (ready ? "return to " : "from ") + Chat.escape(npcName(ready ? quest.turnIn() : quest.giver())) + "</dark_gray>");
            for (int i = 0; i < quest.objectives().size(); i++) {
                QuestDefinition.Objective objective = quest.objectives().get(i);
                int have = counts[i];
                player.sendRichMessage("  " + (have >= objective.count() ? "<green>✔" : "<gray>•") + " " + Chat.escape(objective.description())
                    + (objective.count() > 1 ? " " + have + "/" + objective.count() : "")
                    + (objective.zone() != null ? " <dark_gray>(" + Chat.escape(zoneTitle(objective.zone())) + ")</dark_gray>" : "")
                    + (have >= objective.count() ? "</green>" : "</gray>"));
            }
        }
    }

    /**
     * The quest book for the Aerborne client (ClientFeeds.QUESTS), version 1: open (boolean: show the book now rather
     * than refresh it), completed count (int), tracked quest id (string), then quests (int count) each with id,
     * title, chain title (strings), status (byte: 0 available, 1 active, 2 ready, 3 completed), giver and turn-in
     * names (strings), minimum level (int), daily (boolean), offer lines (int count, strings), objectives (int count;
     * each description string, have and count ints, zone string) and rewards: Marks (long), XP (int count; skill
     * string, amount long) and items (int count, strings).
     */
    public boolean sendBook(Player player, QuestProgress log, boolean open) {
        if (!net.nerdsmp.paper.presentation.ClientFeeds.listens(player, net.nerdsmp.paper.presentation.ClientFeeds.QUESTS)) return false;
        try {
            int level = level(player);
            List<QuestDefinition> shown = new java.util.ArrayList<>();
            for (String id : log.active().keySet()) if (catalog.quests().containsKey(id)) shown.add(catalog.quests().get(id));
            for (QuestDefinition quest : catalog.quests().values())
                if (!log.active().containsKey(quest.id()) && log.status(quest, level) == QuestProgress.Status.AVAILABLE && shown.size() < 60) shown.add(quest);
            for (QuestDefinition quest : catalog.quests().values())
                if (log.completed().contains(quest.id()) && !shown.contains(quest) && shown.size() < 90) shown.add(quest);
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(4096);
            java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
            out.writeInt(1);
            out.writeBoolean(open);
            out.writeInt(log.completed().size());
            net.nerdsmp.paper.presentation.ClientFeeds.writeString(out, tracked(player).map(QuestDefinition::id).orElse(""));
            out.writeInt(shown.size());
            for (QuestDefinition quest : shown) {
                boolean active = log.active().containsKey(quest.id());
                int[] counts = active ? counts(player, quest) : new int[quest.objectives().size()];
                boolean ready = active && QuestProgress.done(quest, counts);
                int status = active ? (ready ? 2 : 1) : log.completed().contains(quest.id()) && !quest.daily() ? 3 : 0;
                str(out, quest.id()); str(out, quest.title()); str(out, chainTitle(quest));
                out.writeByte(status);
                str(out, npcName(quest.giver())); str(out, npcName(quest.turnIn()));
                out.writeInt(quest.minimumLevel());
                out.writeBoolean(quest.daily());
                List<String> offer = quest.offer().stream().limit(8).toList();
                out.writeInt(offer.size());
                for (String line : offer) str(out, line);
                out.writeInt(quest.objectives().size());
                for (int i = 0; i < quest.objectives().size(); i++) {
                    QuestDefinition.Objective objective = quest.objectives().get(i);
                    int have = !active ? (status == 3 ? objective.count() : 0)
                        : counts[i];
                    str(out, objective.description());
                    out.writeInt(have);
                    out.writeInt(objective.count());
                    str(out, objective.zone() == null ? "" : zoneTitle(objective.zone()));
                }
                out.writeLong(quest.rewards().marks());
                out.writeInt(quest.rewards().experience().size());
                for (var xp : quest.rewards().experience().entrySet()) { str(out, xp.getKey()); out.writeLong(xp.getValue()); }
                out.writeInt(quest.rewards().items().size());
                for (var item : quest.rewards().items()) str(out, item.amount() + "x " + rewardName(item));
            }
            out.flush();
            return net.nerdsmp.paper.presentation.ClientFeeds.questBook(player, bytes.toByteArray());
        } catch (java.io.IOException | RuntimeException failure) {
            return false;
        }
    }

    private static void str(java.io.DataOutputStream out, String value) throws java.io.IOException {
        net.nerdsmp.paper.presentation.ClientFeeds.writeString(out, net.nerdsmp.paper.presentation.ClientFeeds.clean(value));
    }

    // ---------------------------------------------------------------- helpers and storage

    QuestProgress log(Player player) {
        Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        return account.map(id -> progress.computeIfAbsent(id, ignored -> new QuestProgress())).orElse(null);
    }

    public Optional<QuestProgress> progress(AccountId account) { return Optional.ofNullable(progress.get(account)); }

    private String npcName(String npc) {
        var definition = plugin.npcs().content().npcs().get(npc);
        return definition == null ? expeditionGivers().getOrDefault(npc, npc) : definition.name();
    }

    /** Quest givers who stand inside expeditions rather than the world's NPC list. */
    private Map<String, String> expeditionGivers() {
        return plugin.expeditions() == null ? Map.of() : plugin.expeditions().questGivers();
    }

    private String zoneTitle(String zone) {
        return plugin.zones().find(zone).map(ZoneDefinition::title).orElse(zone);
    }

    private String chainTitle(QuestDefinition quest) {
        String[] words = quest.chain().split("[_-]");
        StringBuilder title = new StringBuilder();
        for (String word : words) if (!word.isEmpty()) title.append(title.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return title.toString();
    }

    private List<String> rewardLore(QuestDefinition.Rewards rewards) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Rewards:</gray>");
        if (rewards.marks() > 0) lore.add(" <gold>" + Chat.marks(rewards.marks()) + "</gold>");
        rewards.experience().forEach((skill, xp) -> lore.add(" <aqua>" + xp + " " + Skill.valueOf(skill).displayName() + " XP</aqua>"));
        rewards.reputation().forEach((faction, points) -> {
            String name;
            try { name = plugin.npcs().content().faction(new FactionId(faction)).name(); } catch (IllegalArgumentException missing) { name = faction; }
            lore.add(" <light_purple>" + (points > 0 ? "+" : "") + points + " " + Chat.escape(name) + "</light_purple>");
        });
        for (QuestDefinition.ItemReward item : rewards.items()) {
            lore.add(" <white>" + item.amount() + "× " + Chat.escape(rewardName(item)) + "</white>");
        }
        return lore;
    }

    private String rewardName(QuestDefinition.ItemReward item) {
        if (item.name() != null) return item.name();
        if (item.kind().equals("material") && item.id().equals("experience_bottle")) return "Skill Lamp";
        ItemStack stack = rewardStack(item);
        if (stack != null && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            Component name = meta.hasDisplayName() ? meta.displayName() : meta.hasItemName() ? meta.itemName() : null;
            if (name != null) return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        }
        return item.id().replace('_', ' ');
    }

    private ItemStack icon(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Chat.render(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> line.isEmpty() ? Component.empty() : Chat.render(line).decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.values());
        stack.setItemMeta(meta);
        return stack;
    }

    private void load() {
        if (!progressFile.isFile()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(progressFile);
        ConfigurationSection accounts = yaml.getConfigurationSection("accounts");
        if (accounts == null) return;
        for (String key : accounts.getKeys(false)) {
            try {
                QuestProgress log = new QuestProgress();
                ConfigurationSection active = accounts.getConfigurationSection(key + ".active");
                if (active != null) for (String quest : active.getKeys(false))
                    log.active().put(quest, active.getIntegerList(quest).stream().mapToInt(Integer::intValue).toArray());
                log.completed().addAll(accounts.getStringList(key + ".completed"));
                ConfigurationSection daily = accounts.getConfigurationSection(key + ".daily");
                if (daily != null) for (String quest : daily.getKeys(false)) {
                    try { log.dailyDone().put(quest, java.time.LocalDate.parse(daily.getString(quest, ""))); }
                    catch (java.time.format.DateTimeParseException ignored) { }
                }
                AccountId account = AccountId.parse(key);
                progress.put(account, log);
                String following = accounts.getString(key + ".tracked");
                if (following != null) tracked.put(account, following);
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().warning("Ignoring invalid quest progress for " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        progress.forEach((account, log) -> {
            if (log.active().isEmpty() && log.completed().isEmpty() && log.dailyDone().isEmpty()) return;
            String path = "accounts." + account;
            log.active().forEach((quest, counts) -> yaml.set(path + ".active." + quest, java.util.Arrays.stream(counts).boxed().toList()));
            yaml.set(path + ".completed", List.copyOf(log.completed()));
            log.dailyDone().forEach((quest, day) -> yaml.set(path + ".daily." + quest, day.toString()));
            if (tracked.containsKey(account)) yaml.set(path + ".tracked", tracked.get(account));
        });
        if (writer == null) writer = new OrderedYamlWriter(plugin, progressFile, "quest progress");
        writer.submit(yaml.saveToString());
    }

    private static final class Board implements InventoryHolder {
        private final String npc;
        private final int page;
        private final int pages;
        private final Map<Integer, String> slots = new HashMap<>();
        private Inventory inventory;
        private Board(String npc, int page, int pages) { this.npc = npc; this.page = page; this.pages = pages; }
        @Override public Inventory getInventory() { return inventory; }
    }
}

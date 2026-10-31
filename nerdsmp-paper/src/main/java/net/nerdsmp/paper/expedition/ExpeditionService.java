package net.nerdsmp.paper.expedition;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ItemId;
import net.nerdsmp.api.service.PartyService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.expedition.content.Allies;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionLoader;
import net.nerdsmp.paper.expedition.content.ExpeditionValidator;
import net.nerdsmp.paper.expedition.content.Keystone;
import net.nerdsmp.paper.expedition.content.Modifier;
import net.nerdsmp.paper.expedition.content.PartyScaling;
import net.nerdsmp.paper.expedition.content.Tier;
import net.nerdsmp.paper.expedition.flow.ExpeditionRecords;
import net.nerdsmp.paper.expedition.flow.KeystoneStore;
import net.nerdsmp.paper.expedition.flow.RaidLockouts;
import net.nerdsmp.paper.expedition.flow.Matchmaker;
import net.nerdsmp.paper.expedition.flow.ReadyCheck;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Instanced party expeditions: loading and validating expedition files, the party queue and ready
 * checks, building run worlds from templates, routing game events to runs, and sending everyone home.
 * Nether and End travel go through here too: vanilla portals stay sealed unless config reopens them.
 */
public final class ExpeditionService implements Listener, AutoCloseable {
    public static final String ADMIN = "nerdsmp.expedition.admin";
    public static final List<String> SAMPLES = List.of("first_flight", "hollow_barrow", "thornwood_canopy", "sunken_aqueduct",
        "gloomforge_mines", "hall_of_wardens", "cinder_keep");
    /** The tutorial expedition, suggested to anyone who hasn't cleared it. */
    public static final String TUTORIAL = "first_flight";

    private final NerdSmpPlugin plugin;
    private final File folder;
    private final InstanceWorlds worlds;
    private final ExpeditionRecords records;
    private final KeystoneStore keystones;
    private final RaidLockouts lockouts;
    /** Accounts whose key is in a ready check or a live run, so one key can't run twice. */
    private final Set<String> keysInUse = new HashSet<>();
    /** Ready checks that start a keystone, by check id. */
    private final Map<UUID, ExpeditionRun.KeyRun> keyChecks = new HashMap<>();
    private final ReturnStore returns;
    private final Map<String, ExpeditionDefinition> definitions = new TreeMap<>();
    private final Map<String, YamlConfiguration> files = new HashMap<>();
    private final Map<String, List<ExpeditionValidator.Issue>> issues = new HashMap<>();
    private final Map<String, String> loadErrors = new TreeMap<>();
    private Map<String, net.nerdsmp.paper.expedition.content.ExpeditionLoot.Table> lootTables = Map.of();
    private Map<String, Tier> tiers = Tier.DEFAULTS;
    private final Matchmaker matchmaker;
    private final Map<UUID, ReadyCheck> checks = new LinkedHashMap<>();
    private final Map<UUID, ExpeditionRun> runs = new LinkedHashMap<>();
    private final Map<UUID, ExpeditionRun> runByPlayer = new HashMap<>();
    private final Map<UUID, ExpeditionRun> runByWorld = new HashMap<>();
    private final Set<UUID> teleporting = new HashSet<>();
    private final Map<UUID, Long> portalNotices = new HashMap<>();
    private final int maxInstances;
    private final Duration readyTimeout;
    private final Duration rejoinWindow;
    private final Duration linger;
    private final boolean vanillaPortals;
    private PartyScaling scaling = PartyScaling.DEFAULT;
    private Allies allies = Allies.DEFAULT;
    private final Set<UUID> allyOffers = new HashSet<>();
    private final BukkitTask task;
    private ExpeditionSetup setup;
    private ExpeditionMenu menu;
    private final ExpeditionForge forge;

    public ExpeditionService(NerdSmpPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "expeditions");
        var config = plugin.getConfig();
        this.maxInstances = Math.max(1, config.getInt("expeditions.max-instances", 6));
        this.readyTimeout = Duration.ofSeconds(Math.max(10, config.getLong("expeditions.ready-check-seconds", 30)));
        this.rejoinWindow = Duration.ofMinutes(Math.max(1, config.getLong("expeditions.rejoin-minutes", 5)));
        this.linger = Duration.ofSeconds(Math.max(15, config.getLong("expeditions.linger-after-finish-seconds", 180)));
        this.vanillaPortals = config.getBoolean("expeditions.vanilla-portals", false);
        this.matchmaker = new Matchmaker(Duration.ofSeconds(Math.max(0, config.getLong("expeditions.queue-fill-seconds", 90))));
        this.worlds = new InstanceWorlds(plugin);
        this.records = new ExpeditionRecords(new File(plugin.getDataFolder(), "expedition-records.yml"));
        this.forge = new ExpeditionForge(plugin, this);
        this.keystones = new KeystoneStore(new File(plugin.getDataFolder(), "expedition-keystones.yml"));
        this.lockouts = new RaidLockouts(new File(plugin.getDataFolder(), "expedition-raid-lockouts.yml"), raidWeek());
        this.returns = new ReturnStore(new File(plugin.getDataFolder(), "expedition-returns.yml"), plugin.getLogger());
        installSamples();
        worlds.cleanupLeftovers();
        reload();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getPluginManager().registerEvents(new ExpeditionCombatListener(this), plugin);
    }

    public void attach(ExpeditionSetup setup, ExpeditionMenu menu) {
        this.setup = setup;
        this.menu = menu;
    }

    // ---------------------------------------------------------------------------------------------
    // Content
    // ---------------------------------------------------------------------------------------------

    /** SHA-256 of sample files as earlier releases shipped them; an exact match is safe to replace. */
    static final Map<String, Set<String>> PREVIOUS_SAMPLES = Map.of(
        "first_flight", Set.of("5930e8755e216bd2a32f176825bada635ec4d26cd0f407c99d799bd1cfa08bad",
            "cd56c677ebc280c6fe935fa0510d8114edd81d34d4d3b1de04c682ac87fa52be"),
        "thornwood_canopy", Set.of("363bab2993b0d86720b4348a6ba320ac75bca0b14e920a32e0bf05300692a5ba",
            "cbd9898be9c3d1d8b04026bd9eead04f4554cde6e688dc6197868e5aec5c9e62"),
        "sunken_aqueduct", Set.of("805c8a9d7c6ac4beea0684c163532fdeb96d52bcbc59cabf3c057c05150e6c38",
            "211143e698ae6e4f09b27ce5072f2d42893f6410552911370546b4af8e551f85",
            "b88195d3d642eaad1da15d2ef31e583ee259af233ff105fd5add54565f01c1b5"),
        "gloomforge_mines", Set.of("66fe0c92770df4d03f3bd6cff50b6495456f02ecaa3bf1f1d09bef9fcdb8fd07",
            "68ecbced26e68dd599978d1b9a11bd1931208749eb5b583399a2a7720ed87781",
            "6d82451550d698f60690d95f326fe3b2dfa112c62dc8f9266e88b467d9ea5d13"),
        "hall_of_wardens", Set.of("5b2c69d285025bd7f0b9af878ef4fc513782d4f5398da5f6cef4a8cce7a111d9",
            "8fea84e3654465e6795274aa04f601a7ac837600bbfe81c0de63c562fc621e31",
            "434cacf4dc64073543d4cd3ee72faac553495b06ab4c423cc240669e7016c0c3"),
        "hollow_barrow", Set.of("e6f4341ca87e703ee9acd3c86d96f5c2a75718a99cc23b24df0f769a2996e797",
            "d962e922f84c8636bd3ac0f78b9362652144e60a110b2a2e29f30e199998d6e0",
            "d872519fa5b9f187bdc082f9c059752aa91f6cca3d5b7a956b03ec1c89ee3bbf",
            "6bb0974b9dfb88ee02a529a45ba0615994c88ddd18a37ff5ee1078769b1a8b0a",
            "3d9bd7d0ba9d95b03480111ce1647ccf19da0d2905c056a1e565b7a5cf8ff658",
            "d302132dbe72606870496c166365aa9de3fffef02217ca09d9ee34c8a9096a99",
            "d96eee9528e7984a340de9a5624d13b6031a7f7c9800c2431cc0cf3589b54796",
            "fe527944259a82ca2c74dcdc70d52083f844a768a91647b4d0e0c6b64dc8b05d"),
        "cinder_keep", Set.of("99082b410867688a23332d256868dfe20760341d8667b61258fde6936fc1a2a1",
            "41dbd3e34f7b8570f73afb098bf83f947273254aaecf6cebcbcc6716104b477e",
            "ca7699f1252bdc2c6fdc93e7c9feb580f76483225bb8fba6c0e1fa0af1e6040e",
            "fb3085e71b27aee47b1a1788465423c60fb2a5c8fd9fdf6b73bb12c5aba18dd3",
            "fe527944259a82ca2c74dcdc70d52083f844a768a91647b4d0e0c6b64dc8b05d"));

    private void installSamples() {
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Could not create " + folder);
        for (String sample : SAMPLES) {
            File file = new File(folder, sample + ".yml");
            if (!file.exists()) plugin.saveResource("expeditions/" + sample + ".yml", false);
            else if (PREVIOUS_SAMPLES.getOrDefault(sample, Set.of()).contains(sha256(file))) {
                // An untouched earlier sample picks up new tuning and guides; any staff edit keeps the file as it is.
                plugin.saveResource("expeditions/" + sample + ".yml", true);
                plugin.getLogger().info("Updated the unedited sample expedition " + sample + ".yml to the current version.");
            }
        }
    }

    /** SHA-256 of the file with CRLF read as LF, so jars built on Windows and elsewhere match. */
    static String sha256(File file) {
        try {
            String text = java.nio.file.Files.readString(file.toPath()).replace("\r\n", "\n");
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException failure) {
            return "";
        }
    }

    /** The rooms of the run this player is in, for staff tools. */
    public List<String> rooms(Player player) {
        ExpeditionRun run = runByPlayer.get(player.getUniqueId());
        return run == null ? List.of() : run.expedition.roomList().stream().map(ExpeditionDefinition.Room::id).toList();
    }

    /** Staff testing: jumps to a room of the run this player is in. False when there's no such room. */
    public boolean jump(Player player, String room) {
        ExpeditionRun run = runByPlayer.get(player.getUniqueId());
        Location spot = run == null ? null : run.roomSpot(room);
        if (spot == null) return false;
        teleport(player, spot);
        return true;
    }

    /** Characters inside expeditions who give quests, by quest-giver id, with their names. */
    public Map<String, String> questGivers() {
        Map<String, String> givers = new HashMap<>();
        for (ExpeditionDefinition definition : definitions.values()) for (var npc : definition.story().npcs().values())
            if (npc.quests() != null) givers.put(npc.quests(), npc.name());
        return givers;
    }

    /** Reloads tiers and every expedition file. A file that no longer parses keeps its last good version. */
    public synchronized List<String> reload() {
        List<String> messages = new ArrayList<>();
        try {
            tiers = Tier.load(plugin.getConfig().getConfigurationSection("expeditions.tiers"));
            scaling = PartyScaling.load(plugin.getConfig().getConfigurationSection("expeditions.scaling"));
            allies = Allies.load(plugin.getConfig().getConfigurationSection("expeditions.allies"));
        } catch (IllegalArgumentException invalid) {
            messages.add("expeditions.tiers in config.yml: " + invalid.getMessage() + " Using the built-in tiers.");
            tiers = Tier.DEFAULTS;
        }
        loadErrors.clear();
        Set<String> seen = new HashSet<>();
        File[] found = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (found != null) for (File file : found) {
            String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            seen.add(id);
            try {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(file);
                ExpeditionDefinition definition = ExpeditionLoader.parse(id, yaml);
                definitions.put(id, definition);
                files.put(id, yaml);
                if (records.startNewTimes(id, definition.story().recordsVersion())) keystones.clearBests(id);
            } catch (Exception invalid) {
                loadErrors.put(id, invalid.getMessage());
                messages.add(id + ".yml: " + invalid.getMessage() + (definitions.containsKey(id) ? " Keeping the previous version." : ""));
            }
        }
        definitions.keySet().removeIf(id -> !seen.contains(id));
        files.keySet().removeIf(id -> !seen.contains(id));
        lootTables = loadLootTables(messages);
        issues.clear();
        for (ExpeditionDefinition definition : definitions.values()) issues.put(definition.id(), validate(definition));
        return messages;
    }

    /**
     * Each expedition's loot (materials, set gear, signatures) from the plugin's
     * content/expedition-loot.yml; an entry in the data folder's copy replaces the shipped one.
     */
    private Map<String, net.nerdsmp.paper.expedition.content.ExpeditionLoot.Table> loadLootTables(List<String> messages) {
        Map<String, net.nerdsmp.paper.expedition.content.ExpeditionLoot.Table> tables = new HashMap<>();
        List<YamlConfiguration> sources = new ArrayList<>();
        try (var stream = plugin.getResource("content/expedition-loot.yml")) {
            if (stream != null) sources.add(YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.io.IOException unreadable) {
            messages.add("content/expedition-loot.yml in the plugin could not be read.");
        }
        File override = new File(plugin.getDataFolder(), "content/expedition-loot.yml");
        if (override.isFile()) sources.add(YamlConfiguration.loadConfiguration(override));
        for (YamlConfiguration source : sources) {
            var section = source.getConfigurationSection("expeditions");
            if (section == null) continue;
            for (String id : section.getKeys(false))
                tables.put(id.toLowerCase(Locale.ROOT), new net.nerdsmp.paper.expedition.content.ExpeditionLoot.Table(
                    section.getStringList(id + ".materials"), section.getStringList(id + ".gear"), section.getStringList(id + ".signatures"), bossLoot(section, id)));
        }
        return Map.copyOf(tables);
    }

    private static Map<String, List<String>> bossLoot(org.bukkit.configuration.ConfigurationSection section, String id) {
        Map<String, List<String>> rooms = new HashMap<>();
        var bosses = section.getConfigurationSection(id + ".bosses");
        if (bosses != null) for (String room : bosses.getKeys(false)) rooms.put(room.toLowerCase(Locale.ROOT), bosses.getStringList(room));
        return rooms;
    }

    /** An expedition's loot table, or an empty one (experience bottles only) for expeditions without one. */
    public net.nerdsmp.paper.expedition.content.ExpeditionLoot.Table lootTable(String expedition) {
        return lootTables.getOrDefault(expedition, net.nerdsmp.paper.expedition.content.ExpeditionLoot.Table.EMPTY);
    }

    public List<ExpeditionValidator.Issue> validate(ExpeditionDefinition definition) {
        List<ExpeditionValidator.Issue> found = new ArrayList<>(ExpeditionValidator.validate(definition, tiers, catalog()));
        if (definition.sample() == null && !worlds.templateExists(definition))
            found.add(0, new ExpeditionValidator.Issue(ExpeditionValidator.Severity.ERROR,
                "The template world " + definition.templateWorld() + " does not exist.", "/expedition create " + definition.id()));
        if (definition.sample() != null && SampleTemplates.plan(definition.sample()) == null)
            found.add(0, new ExpeditionValidator.Issue(ExpeditionValidator.Severity.ERROR,
                "Unknown sample build '" + definition.sample() + "'.", "Remove sample: from the file"));
        return List.copyOf(found);
    }

    public ExpeditionValidator.Catalog catalog() {
        return new ExpeditionValidator.Catalog() {
            @Override public boolean mob(String id) {
                try { plugin.mobs().encounterType(id); return true; } catch (IllegalArgumentException unknown) { return false; }
            }
            @Override public boolean boss(String id) { return plugin.mobs().encounterBoss(id); }
            @Override public boolean lootProfile(String id) { return plugin.rewardDrops().profiles().find(id).isPresent(); }
            @Override public boolean item(String id) {
                if (plugin.skillLamps() != null && plugin.skillLamps().parse(id).isPresent()) return true;
                try { return plugin.rewardDrops().profiles().knownItems().contains(new ItemId(id)); } catch (IllegalArgumentException invalid) { return false; }
            }
            @Override public boolean effect(String key) { return registered(Registry.MOB_EFFECT, key); }
            @Override public boolean sound(String key) { return registered(Registry.SOUND_EVENT, key); }
            @Override public boolean material(String key) { return Material.matchMaterial(key) != null; }
            @Override public boolean expedition(String id) { return definitions.containsKey(id); }
            @Override public boolean biome(String key) {
                try { TemplateGenerator.resolve(key); return true; } catch (RuntimeException unknown) { return false; }
            }
        };
    }

    private static boolean registered(Registry<?> registry, String key) {
        NamespacedKey parsed = NamespacedKey.fromString(key.toLowerCase(Locale.ROOT));
        return parsed != null && registry.get(parsed) != null;
    }

    public Collection<ExpeditionDefinition> definitions() { return List.copyOf(definitions.values()); }
    public Optional<ExpeditionDefinition> definition(String id) { return Optional.ofNullable(definitions.get(id.toLowerCase(Locale.ROOT))); }
    public List<ExpeditionValidator.Issue> issues(String id) { return issues.getOrDefault(id, List.of()); }
    public Map<String, String> loadErrors() { return Map.copyOf(loadErrors); }
    public Map<String, Tier> tiers() { return tiers; }
    public YamlConfiguration file(String id) { return files.get(id); }
    public File fileFor(String id) { return new File(folder, id + ".yml"); }
    public InstanceWorlds worlds() { return worlds; }
    ExpeditionRecords records() { return records; }
    public KeystoneStore keystones() { return keystones; }
    public ExpeditionForge forge() { return forge; }
    public RaidLockouts lockouts() { return lockouts; }

    /** Raid lockouts and keystone affixes reset together, every Monday (UTC). */
    public long raidWeek() { return Keystone.week(LocalDate.now(ZoneOffset.UTC)); }

    /** Whether this player cleared a room that unlocks {@code key} (such as the Nether's Cinder Shelf route). */
    public boolean raidUnlocked(Player player, String key) {
        return plugin.sessions().account(player.getUniqueId()).map(account -> records.unlocked(account.toString(), key)).orElse(false);
    }

    /** A room cleared in a real run: its unlocks and its own loot. */
    void roomCleared(ExpeditionRun run, ExpeditionDefinition.Room room) {
        for (String key : room.unlocks()) unlock(run, key);
        run.payRoom(room);
    }

    /** A room unlocked something for everyone present; tells those it's new for. */
    void unlock(ExpeditionRun run, String key) {
        for (UUID member : run.progress.present()) {
            Player player = Bukkit.getPlayer(member);
            Optional<AccountId> account = plugin.sessions().account(member);
            if (player == null || account.isEmpty() || !records.unlock(account.get().toString(), key)) continue;
            if (key.equals("nether")) {
                player.showTitle(net.kyori.adventure.title.Title.title(Chat.render("<red><bold>The Nether opens</bold></red>"),
                    Chat.render("<gray>The Cinder Shelf route is open to you</gray>")));
                Chat.success(player, "<red>The Nether is open to you.</red> Travel to the Cinder Shelf for quartz, gold and glowstone with "
                    + Chat.command("expedition routes") + ".");
                player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, .7F, 1.2F);
            }
        }
    }
    ReturnStore returns() { return returns; }
    Duration rejoinWindow() { return rejoinWindow; }
    Duration lingerAfterFinish() { return linger; }
    public boolean vanillaPortals() { return vanillaPortals; }

    /** Stores an edited file from the setup flow and makes it live. */
    public synchronized void store(String id, YamlConfiguration yaml) throws java.io.IOException {
        ExpeditionDefinition definition = ExpeditionLoader.parse(id, yaml);
        net.nerdsmp.paper.storage.AtomicYamlFiles.save(yaml, fileFor(id));
        definitions.put(id, definition);
        files.put(id, yaml);
        loadErrors.remove(id);
        issues.put(id, validate(definition));
    }

    /** Re-runs validation, e.g. once a new template world exists. */
    public void revalidate(String id) {
        ExpeditionDefinition definition = definitions.get(id);
        if (definition != null) issues.put(id, validate(definition));
    }

    public boolean playable(ExpeditionDefinition definition) {
        return definition.published() && ExpeditionValidator.publishable(issues(definition.id()));
    }

    /** Tiers the expedition offers, in config order, with its overrides applied. */
    public List<Tier> tiersFor(ExpeditionDefinition definition) {
        List<Tier> offered = new ArrayList<>();
        for (Tier tier : tiers.values()) {
            Tier applied = tier.apply(definition.tiers().get(tier.id()), definition.lives());
            if (applied != null) offered.add(applied);
        }
        return offered;
    }

    public Optional<Tier> tier(ExpeditionDefinition definition, String id) {
        return tiersFor(definition).stream().filter(tier -> tier.id().equalsIgnoreCase(id)).findFirst();
    }

    public Set<Modifier> modifiers(ExpeditionDefinition definition, Tier tier) {
        Set<Modifier> active = new HashSet<>(Modifier.parseAll(definition.modifiers()));
        active.addAll(Modifier.parseAll(tier.modifiers()));
        return active;
    }

    /** Why this player cannot run this tier right now, or empty when they can. */
    public Optional<String> problem(Player player, ExpeditionDefinition definition, Tier tier) {
        if (!player.isOnline()) return Optional.of(player.getName() + " is offline.");
        Optional<AccountId> account = plugin.sessions().account(player.getUniqueId());
        if (account.isEmpty()) return Optional.of(player.getName() + "'s account is still loading.");
        if (runByPlayer.containsKey(player.getUniqueId())) return Optional.of(player.getName() + " is already on an expedition.");
        if (InstanceWorlds.isRunWorld(player.getWorld())) return Optional.of(player.getName() + " is still inside an expedition.");
        boolean bypass = player.hasPermission("nerdsmp.progression.bypass") || player.hasPermission(ADMIN);
        Optional<String> lock = plugin.campaign().expeditionLock(definition.id());
        if (lock.isPresent() && !bypass) return Optional.of(definition.title() + " is sealed until the campaign reaches " + lock.get() + ".");
        if (!bypass) for (String required : definition.story().requires()) {
            if (records.clears(account.get().toString(), required) > 0) continue;
            String title = definition(required).map(ExpeditionDefinition::title).orElse(required);
            return Optional.of(player.getName() + " needs to clear " + title + " before " + definition.title() + ".");
        }
        if (tier.requires() != null && !bypass && !records.cleared(account.get().toString(), definition.id(), tier.requires()))
            return Optional.of(player.getName() + " needs to clear " + definition.title() + " on " + tiers.get(tier.requires()).title() + " first.");
        return Optional.empty();
    }

    /** The first expedition this player must still clear before {@code definition} opens to them. */
    public Optional<ExpeditionDefinition> missingRequirement(Player player, ExpeditionDefinition definition) {
        if (player.hasPermission("nerdsmp.progression.bypass") || player.hasPermission(ADMIN)) return Optional.empty();
        String account = plugin.sessions().account(player.getUniqueId()).map(Object::toString).orElse("");
        for (String required : definition.story().requires())
            if (records.clears(account, required) == 0) return Optional.of(definition(required).orElse(null)).filter(java.util.Objects::nonNull);
        return Optional.empty();
    }

    public boolean cleared(Player player, ExpeditionDefinition definition, String tier) {
        return plugin.sessions().account(player.getUniqueId()).map(account -> records.cleared(account.toString(), definition.id(), tier)).orElse(false);
    }

    public long clears(Player player, ExpeditionDefinition definition) {
        return plugin.sessions().account(player.getUniqueId()).map(account -> records.clears(account.toString(), definition.id())).orElse(0L);
    }

    public Map<String, ExpeditionRecords.Totals> totals(String expedition) { return records.totals(expedition); }
    public int waiting(String expedition, String tier) { return matchmaker.waiting(expedition, tier); }
    public int activeRuns() { return runs.size(); }
    public int maxInstances() { return maxInstances; }

    public long activeRuns(String expedition) {
        return runs.values().stream().filter(run -> run.expedition.id().equals(expedition)).count();
    }

    // ---------------------------------------------------------------------------------------------
    // Joining
    // ---------------------------------------------------------------------------------------------

    /** The leader starts (queue=false) or queues (queue=true) their whole party, or themselves solo. */
    public void join(Player leader, String id, String tierId, boolean queue) {
        ExpeditionDefinition definition = definitions.get(id.toLowerCase(Locale.ROOT));
        if (definition == null || !playable(definition) && !leader.hasPermission(ADMIN)) {
            Chat.error(leader, "There is no open expedition called " + Chat.escape(id) + ". Browse them with " + Chat.command("expedition") + ".");
            return;
        }
        if (!playable(definition)) {
            Chat.error(leader, Chat.escape(definition.title()) + " isn't published or has setup errors. Staff can use " + Chat.command("expedition test " + definition.id()) + ".");
            return;
        }
        Optional<Tier> tier = tier(definition, tierId == null ? tiersFor(definition).get(0).id() : tierId);
        if (tier.isEmpty()) {
            Chat.error(leader, "That difficulty isn't offered. Choose " + String.join(", ", tiersFor(definition).stream().map(Tier::id).toList()) + ".");
            return;
        }
        if (matchmaker.ticketOf(leader.getUniqueId()).isPresent() || checkOf(leader.getUniqueId()).isPresent()) {
            Chat.error(leader, "You're already queued or in a ready check. Use " + Chat.command("expedition leave") + " first.");
            return;
        }
        Optional<AccountId> account = plugin.sessions().account(leader.getUniqueId());
        if (account.isEmpty()) { Chat.accountLoading(leader); return; }
        partyOf(leader, account.get(), "start or queue an expedition", members -> {
            if (members.size() > definition.maxParty()) {
                Chat.error(leader, Chat.escape(definition.title()) + " allows up to " + definition.maxParty() + " players; your party has " + members.size() + ".");
                return;
            }
            for (Player member : members) {
                Optional<String> problem = problem(member, definition, tier.get());
                if (problem.isEmpty() && (matchmaker.ticketOf(member.getUniqueId()).isPresent() || checkOf(member.getUniqueId()).isPresent()))
                    problem = Optional.of(member.getName() + " is already queued.");
                if (problem.isPresent()) { Chat.error(leader, Chat.escape(problem.get())); return; }
            }
            List<UUID> ids = members.stream().map(Player::getUniqueId).toList();
            boolean full = members.size() >= definition.minParty();
            if (!queue && full && runs.size() + checks.size() < maxInstances) {
                if (ids.size() == 1) launch(definition, tier.get(), ids, List.of(), false);
                else startCheck(definition, tier.get(), ids, List.of(), leader.getUniqueId());
                return;
            }
            var ticket = new Matchmaker.Ticket(UUID.randomUUID(), definition.id(), tier.get().id(), ids, Instant.now());
            var result = matchmaker.enqueue(ticket, new Matchmaker.Rules(definition.minParty(), definition.maxParty()));
            if (result != Matchmaker.QueueResult.QUEUED) { Chat.error(leader, "Could not join the queue: " + Chat.humanize(result.name()) + "."); return; }
            String why = !queue && full ? "Every expedition instance is busy, so you're in line for the next one."
                : !queue ? "You need at least " + definition.minParty() + " players, so the finder will fill your group."
                : "The finder will match you with other players.";
            for (Player member : members)
                Chat.success(member, "Queued for <white>" + Chat.escape(definition.title()) + "</white> (" + Chat.escape(tier.get().title()) + "). "
                    + why + " Leave with " + Chat.command("expedition leave") + ".");
        });
    }

    /** The leader's online party, leader first, or just the leader when solo. Tells a non-leader why not instead. */
    private void partyOf(Player leader, AccountId account, String action, java.util.function.Consumer<List<Player>> then) {
        plugin.parties().partyOf(account).thenAccept(party -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!leader.isOnline()) return;
            List<Player> members = new ArrayList<>();
            if (party.isEmpty()) members.add(leader);
            else {
                PartyService.PartyView view = party.get();
                if (!view.leader().equals(account)) {
                    Chat.error(leader, "Only your party leader can " + action + ". Leave the party to go solo.");
                    return;
                }
                members.add(leader); // The leader leads the ticket, so only they can accept hired allies.
                for (AccountId member : view.members())
                    plugin.sessions().player(member).map(Bukkit::getPlayer).filter(player -> !player.equals(leader)).ifPresent(members::add);
                if (members.size() < view.members().size())
                    Chat.notice(leader, "Offline party members stay behind.");
            }
            then.accept(members);
        }));
    }

    // ---------------------------------------------------------------------------------------------
    // Mythic keystones
    // ---------------------------------------------------------------------------------------------

    /** Expeditions a keystone can roll: open campaign expeditions with a Mythic tier, not the tutorial or raids. */
    public List<ExpeditionDefinition> keyPool() {
        return definitions.values().stream().filter(definition -> playable(definition) && !definition.id().equals(TUTORIAL)
            && !definition.story().raid() && tier(definition, "mythic").isPresent()).toList();
    }

    public Optional<Keystone.Key> key(Player player) {
        return plugin.sessions().account(player.getUniqueId()).flatMap(account -> keystones.key(account.toString()));
    }

    /** This week's affixes at a key level. */
    public List<Modifier> affixes(int level) { return Keystone.affixes(level, LocalDate.now(ZoneOffset.UTC)); }

    /** A random key-pool expedition, avoiding {@code avoid} when there is a choice. */
    private Optional<String> rollExpedition(String avoid) {
        List<String> pool = new ArrayList<>(keyPool().stream().map(ExpeditionDefinition::id).toList());
        if (pool.size() > 1) pool.remove(avoid);
        if (pool.isEmpty()) return Optional.empty();
        return Optional.of(pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size())));
    }

    /** Members with no keystone yet earn a +2 key from their first Mythic (or keyed) clear. */
    void grantFirstKeys(Collection<String> accounts) {
        for (String account : accounts) {
            if (keystones.key(account).isPresent()) continue;
            Optional<String> expedition = rollExpedition(null);
            if (expedition.isEmpty()) return;
            Keystone.Key key = new Keystone.Key(expedition.get(), Keystone.MIN_LEVEL);
            keystones.setKey(account, key);
            plugin.sessions().player(AccountId.parse(account)).map(Bukkit::getPlayer).ifPresent(player -> {
                Chat.send(player, "<light_purple><bold>Mythic keystone earned!</bold></light_purple> <white>" + keyName(key) + "</white>. <gray>Start it with your party: "
                    + Chat.command("expedition key start") + ". Beat its timer to upgrade it; see " + Chat.command("expedition key") + ".</gray>");
                player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1F, 1.2F);
            });
        }
    }

    String keyName(Keystone.Key key) {
        return Chat.escape(definition(key.expedition()).map(ExpeditionDefinition::title).orElse(key.expedition())) + " +" + key.level();
    }

    /** {@code /expedition key}: the player's keystone, this week's affixes and the expedition's best. */
    private void showKey(Player player) {
        Optional<Keystone.Key> key = key(player);
        if (key.isEmpty()) {
            Chat.notice(player, "You don't have a Mythic keystone. Clear any campaign expedition on Mythic to earn one.");
            return;
        }
        int level = key.get().level();
        Chat.send(player, "<light_purple><bold>Keystone:</bold></light_purple> <white>" + keyName(key.get()) + "</white>");
        definition(key.get().expedition()).ifPresent(definition -> Chat.send(player, "<gray>Timer: <white>"
            + Chat.duration(Duration.ofSeconds(Keystone.timerSeconds(definition.timeLimitMinutes()))) + "</white> · enemies <white>+"
            + Math.round((Keystone.scale(level) - 1) * 100) + "%</white> over Mythic · each death <white>+" + Keystone.DEATH_PENALTY_SECONDS + "s</white></gray>"));
        Chat.send(player, "<gray>Affixes this week: <white>" + Chat.escape(Keystone.describe(affixes(level))) + "</white>"
            + (level < 4 ? " <dark_gray>(Fortified or Tyrannical joins at +4, a rotating affix at +7)</dark_gray>" : level < 7 ? " <dark_gray>(a rotating affix joins at +7)</dark_gray>" : "") + "</gray>");
        keystones.best(key.get().expedition()).ifPresent(best -> Chat.send(player, "<gray>Server best here: <white>+" + best.level() + "</white> in "
            + Chat.duration(Duration.ofSeconds(best.seconds())) + " by " + Chat.escape(String.join(", ", best.party())) + "</gray>"));
        Chat.send(player, "<green><click:run_command:'/expedition key start'><hover:show_text:'Start your keystone with your party'>[Start keystone]</hover></click></green>");
    }

    /** Staff: {@code /expedition key give <player> <expedition> <level>} sets a player's keystone. */
    private void giveKey(Player staff, String[] args) {
        if (args.length != 5) { Chat.usage(staff, "/expedition key give <player> <expedition> <level>"); return; }
        Player target = Bukkit.getPlayerExact(args[2]);
        Optional<AccountId> account = target == null ? Optional.empty() : plugin.sessions().account(target.getUniqueId());
        if (account.isEmpty()) { Chat.error(staff, "That player isn't online."); return; }
        Optional<ExpeditionDefinition> definition = definition(args[3]).filter(keyPool()::contains);
        if (definition.isEmpty()) { Chat.error(staff, "Keys go to open campaign expeditions with a Mythic tier."); return; }
        int level;
        try {
            level = Integer.parseInt(args[4]);
        } catch (NumberFormatException invalid) {
            Chat.error(staff, "The level must be a number.");
            return;
        }
        Keystone.Key key = new Keystone.Key(definition.get().id(), level);
        keystones.setKey(account.get().toString(), key);
        Chat.success(staff, Chat.escape(target.getName()) + " now holds <white>" + keyName(key) + "</white>.");
    }

    /** {@code /expedition key start}: the keyholder starts their key for their party. No queue, no hired allies. */
    public void startKey(Player leader) {
        Optional<AccountId> account = plugin.sessions().account(leader.getUniqueId());
        if (account.isEmpty()) { Chat.accountLoading(leader); return; }
        String holder = account.get().toString();
        Optional<Keystone.Key> held = keystones.key(holder);
        if (held.isEmpty()) {
            Chat.error(leader, "You don't have a Mythic keystone. Clear any campaign expedition on Mythic to earn one.");
            return;
        }
        if (keysInUse.contains(holder)) { Chat.error(leader, "Your keystone is already in a ready check or a run."); return; }
        if (matchmaker.ticketOf(leader.getUniqueId()).isPresent() || checkOf(leader.getUniqueId()).isPresent()) {
            Chat.error(leader, "You're already queued or in a ready check. Use " + Chat.command("expedition leave") + " first.");
            return;
        }
        Keystone.Key key = held.get();
        ExpeditionDefinition definition = definitions.get(key.expedition());
        if (definition == null || !keyPool().contains(definition)) {
            // The expedition was retired or closed: move the key somewhere it can be run.
            Optional<String> moved = rollExpedition(key.expedition());
            if (moved.isEmpty()) { Chat.error(leader, "No expedition can take a keystone right now."); return; }
            key = new Keystone.Key(moved.get(), key.level());
            keystones.setKey(holder, key);
            Chat.notice(leader, "Your key's expedition is closed, so the key moved to <white>" + keyName(key) + "</white>.");
            definition = definitions.get(key.expedition());
        }
        if (runs.size() + checks.size() >= maxInstances) {
            Chat.error(leader, "Every expedition instance is busy. Try again in a few minutes.");
            return;
        }
        ExpeditionDefinition chosen = definition;
        Keystone.Key chosenKey = key;
        List<Modifier> affixes = affixes(key.level());
        Tier tier = Keystone.tier(tier(definition, "mythic").orElseThrow(), key.level(), affixes);
        partyOf(leader, account.get(), "start a keystone", members -> {
            if (members.size() > chosen.maxParty()) {
                Chat.error(leader, Chat.escape(chosen.title()) + " allows up to " + chosen.maxParty() + " players; your party has " + members.size() + ".");
                return;
            }
            if (keysInUse.contains(holder) || runs.size() + checks.size() >= maxInstances) return;
            for (Player member : members) {
                Optional<String> problem = problem(member, chosen, tier);
                if (problem.isEmpty() && (matchmaker.ticketOf(member.getUniqueId()).isPresent() || checkOf(member.getUniqueId()).isPresent()))
                    problem = Optional.of(member.getName() + " is already queued.");
                if (problem.isPresent()) { Chat.error(leader, Chat.escape(problem.get())); return; }
            }
            ExpeditionRun.KeyRun run = new ExpeditionRun.KeyRun(leader.getUniqueId(), holder, chosenKey, affixes,
                Keystone.timerSeconds(chosen.timeLimitMinutes()));
            keysInUse.add(holder);
            List<UUID> ids = members.stream().map(Player::getUniqueId).toList();
            if (ids.size() == 1) launch(chosen, tier, ids, List.of(), false, 0, run);
            else startCheck(chosen, tier, ids, List.of(), leader.getUniqueId(), 0, run);
        });
    }

    /** A keyed run cleared: upgrade or deplete the key, record a timed best, and give members their first keys. */
    void settleKey(ExpeditionRun run, long seconds, Map<String, String> party) {
        ExpeditionRun.KeyRun key = run.key;
        run.keySettled = true;
        long timer = run.keyTimer();
        int change = Keystone.change(seconds, timer);
        String next = change > 0 ? rollExpedition(key.key().expedition()).orElse(key.key().expedition()) : key.key().expedition();
        Keystone.Key upgraded = Keystone.after(key.key(), change, next);
        keystones.setKey(key.account(), upgraded);
        boolean record = change > 0 && !party.isEmpty() && keystones.recordTimed(run.expedition.id(), key.key().level(), seconds, party);
        Player holder = Bukkit.getPlayer(key.holder());
        String who = holder == null ? "The keyholder" : Chat.escape(holder.getName());
        run.broadcast(change > 0
            ? "<light_purple><bold>Keystone timed!</bold></light_purple> <gray>" + Chat.duration(Duration.ofSeconds(seconds)) + " of "
                + Chat.duration(Duration.ofSeconds(timer)) + ". " + who + "'s key upgrades <white>+" + change + "</white> to <white>"
                + keyName(upgraded) + "</white>.</gray>" + (record ? " <light_purple>New best key on this expedition!</light_purple>" : "")
            : "<yellow>Keystone over time.</yellow> <gray>" + Chat.duration(Duration.ofSeconds(seconds)) + " of " + Chat.duration(Duration.ofSeconds(timer))
                + ". The loot is yours, but " + who + "'s key drops to <white>" + keyName(upgraded) + "</white>.</gray>");
        grantFirstKeys(party.keySet());
    }

    /** A keyed run failed or was abandoned: the key drops a level and stays on the same expedition. */
    void depleteKey(ExpeditionRun run) {
        ExpeditionRun.KeyRun key = run.key;
        run.keySettled = true;
        Keystone.Key depleted = Keystone.after(key.key(), -1, key.key().expedition());
        keystones.setKey(key.account(), depleted);
        run.broadcast("<yellow>The keystone is depleted.</yellow> <gray>It drops to <white>" + keyName(depleted) + "</white>.</gray>");
    }

    public void leave(Player player) {
        UUID uuid = player.getUniqueId();
        ExpeditionRun run = runByPlayer.get(uuid);
        if (run != null && run.state != ExpeditionRun.State.PREPARING) {
            sendHome(player, run, "<gray>You left " + Chat.escape(run.expedition.title()) + ".</gray>");
            run.broadcast("<gray>" + Chat.escape(player.getName()) + " left the expedition.</gray>");
            return;
        }
        Optional<ReadyCheck> check = checkOf(uuid);
        if (check.isPresent()) { decline(player); return; }
        Optional<Matchmaker.Ticket> ticket = matchmaker.leave(uuid);
        if (ticket.isPresent()) {
            for (UUID member : ticket.get().members()) {
                Player other = Bukkit.getPlayer(member);
                if (other != null) Chat.notice(other, member.equals(uuid) ? "You left the expedition queue."
                    : Chat.escape(player.getName()) + " left the queue, so your group was removed from it.");
            }
            return;
        }
        Chat.notice(player, "You aren't queued or on an expedition.");
    }

    private Optional<ReadyCheck> checkOf(UUID player) {
        return checks.values().stream().filter(check -> check.members().contains(player)).findFirst();
    }

    private void startCheck(ExpeditionDefinition definition, Tier tier, List<UUID> members, List<Matchmaker.Ticket> tickets, UUID autoAccept) {
        startCheck(definition, tier, members, tickets, autoAccept, 0);
    }

    private void startCheck(ExpeditionDefinition definition, Tier tier, List<UUID> members, List<Matchmaker.Ticket> tickets, UUID autoAccept, int allyCount) {
        startCheck(definition, tier, members, tickets, autoAccept, allyCount, null);
    }

    private void startCheck(ExpeditionDefinition definition, Tier tier, List<UUID> members, List<Matchmaker.Ticket> tickets, UUID autoAccept,
                            int allyCount, ExpeditionRun.KeyRun key) {
        ReadyCheck check = new ReadyCheck(definition.id(), tier.id(), members, tickets, Instant.now(), readyTimeout, allyCount);
        checks.put(check.id(), check);
        if (key != null) keyChecks.put(check.id(), key);
        if (autoAccept != null) check.accept(autoAccept);
        for (UUID member : members) {
            Player player = Bukkit.getPlayer(member);
            if (player == null) continue;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1F, 1.2F);
            if (member.equals(autoAccept)) {
                Chat.info(player, "Ready check sent to your party for <white>" + Chat.escape(definition.title()) + "</white>.");
                continue;
            }
            Chat.send(player, "<gold>Ready check:</gold> <white>" + Chat.escape(definition.title()) + "</white> <gray>(" + Chat.escape(tier.title())
                + (key != null ? " keystone, " + Chat.escape(Keystone.describe(key.affixes())) : "")
                + ", " + members.size() + " players" + (allyCount > 0 ? " + " + allyCount + " hired allies" : "") + ")</gray>  <green><click:run_command:'/expedition ready'><hover:show_text:'Join the run'>[Ready]</hover></click></green>"
                + "  <red><click:run_command:'/expedition decline'><hover:show_text:'Decline'>[Decline]</hover></click></red>"
                + " <dark_gray>" + readyTimeout.toSeconds() + "s</dark_gray>");
        }
        resolve(check);
    }

    public void ready(Player player) {
        Optional<ReadyCheck> check = checkOf(player.getUniqueId());
        if (check.isEmpty()) { Chat.notice(player, "There's no ready check waiting for you."); return; }
        check.get().accept(player.getUniqueId());
        for (UUID member : check.get().members()) {
            Player other = Bukkit.getPlayer(member);
            if (other != null) Chat.info(other, Chat.escape(player.getName()) + " is ready <dark_gray>(" + check.get().acceptedCount() + "/" + check.get().members().size() + ")</dark_gray>.");
        }
        resolve(check.get());
    }

    public void decline(Player player) {
        Optional<ReadyCheck> check = checkOf(player.getUniqueId());
        if (check.isEmpty()) { Chat.notice(player, "There's no ready check waiting for you."); return; }
        check.get().decline(player.getUniqueId());
        resolve(check.get());
    }

    private void resolve(ReadyCheck check) {
        if (check.state() == ReadyCheck.State.PENDING) return;
        checks.remove(check.id());
        ExpeditionDefinition definition = definitions.get(check.expedition());
        ExpeditionRun.KeyRun key = keyChecks.remove(check.id());
        Optional<Tier> tier = definition == null ? Optional.empty() : key != null
            ? tier(definition, "mythic").map(mythic -> Keystone.tier(mythic, key.key().level(), key.affixes())) : tier(definition, check.tier());
        if (check.state() == ReadyCheck.State.READY && tier.isPresent()) {
            launch(definition, tier.get(), check.orderedMembers(), check.tickets(), false, check.allies(), key);
            return;
        }
        if (key != null) keysInUse.remove(key.account());
        Player decliner = check.decliner() == null ? null : Bukkit.getPlayer(check.decliner());
        String reason = decliner != null ? Chat.escape(decliner.getName()) + " declined" : "Not everyone was ready in time";
        List<Matchmaker.Ticket> back = check.tickets().isEmpty() ? List.of() : check.innocentTickets();
        matchmaker.requeue(back);
        Set<UUID> requeued = new HashSet<>();
        back.forEach(ticket -> requeued.addAll(ticket.members()));
        for (UUID member : check.members()) {
            Player player = Bukkit.getPlayer(member);
            if (player == null) continue;
            Chat.notice(player, reason + ", so the run didn't start." + (requeued.contains(member) ? " You're back in the queue at your old place." : ""));
        }
    }

    /** Builds the run world, then brings everyone in. Staff test runs skip records, Marks and campaign credit. */
    void launch(ExpeditionDefinition definition, Tier tier, List<UUID> members, List<Matchmaker.Ticket> tickets, boolean test) {
        launch(definition, tier, members, tickets, test, 0);
    }

    /** {@code allyCount} hired NPC allies join the run; any allies make it an assisted run. */
    void launch(ExpeditionDefinition definition, Tier tier, List<UUID> members, List<Matchmaker.Ticket> tickets, boolean test, int allyCount) {
        launch(definition, tier, members, tickets, test, allyCount, null);
    }

    /** {@code key} is the keystone a keyed run is on, or null. */
    void launch(ExpeditionDefinition definition, Tier tier, List<UUID> members, List<Matchmaker.Ticket> tickets, boolean test, int allyCount,
                ExpeditionRun.KeyRun key) {
        List<UUID> online = members.stream().filter(member -> Bukkit.getPlayer(member) != null && !runByPlayer.containsKey(member)).toList();
        if (online.isEmpty() || online.size() + allyCount < (test ? 1 : definition.minParty())) {
            if (key != null) keysInUse.remove(key.account());
            matchmaker.requeue(tickets.stream().filter(ticket -> online.containsAll(ticket.members())).toList());
            online.forEach(member -> Chat.notice(Bukkit.getPlayer(member), "A player dropped out before the run began."));
            return;
        }
        BuildCanvas.Plan plan = definition.sample() == null ? null : SampleTemplates.plan(definition.sample());
        List<Allies.Role> hired = allies.pick(Math.min(allyCount, allies.slots(online.size(), definition.maxParty())));
        ExpeditionRun run = new ExpeditionRun(this, plugin, definition, tier, modifiers(definition, tier), online, test, plan, hired, key);
        runs.put(run.id, run);
        for (UUID member : online) {
            runByPlayer.put(member, run);
            Player player = Bukkit.getPlayer(member);
            Chat.info(player, "Preparing <white>" + Chat.escape(definition.title()) + "</white>…");
        }
        worlds.createRun(definition, plan).whenComplete((world, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (failure != null || world == null) {
                plugin.getLogger().log(Level.SEVERE, "Could not build expedition " + definition.id(), failure);
                runs.remove(run.id);
                if (key != null) keysInUse.remove(key.account());
                online.forEach(member -> {
                    runByPlayer.remove(member, run);
                    Player player = Bukkit.getPlayer(member);
                    if (player != null) Chat.error(player, "The expedition instance could not be built. Staff have been told.");
                });
                return;
            }
            runByWorld.put(world.getUID(), run);
            if (run.state == ExpeditionRun.State.CLOSED) { worlds.destroy(world); return; }
            run.begin(world);
        }));
    }

    public void test(Player staff, ExpeditionDefinition definition, String tierId) {
        if (!ExpeditionValidator.publishable(issues(definition.id()))) {
            Chat.error(staff, "Fix the setup errors first: " + Chat.command("expedition check " + definition.id()) + ".");
            return;
        }
        if (runByPlayer.containsKey(staff.getUniqueId())) { Chat.error(staff, "Leave your current expedition first."); return; }
        Optional<Tier> tier = tier(definition, tierId == null ? tiersFor(definition).get(0).id() : tierId);
        if (tier.isEmpty()) { Chat.error(staff, "Unknown tier for this expedition."); return; }
        matchmaker.leave(staff.getUniqueId());
        launch(definition, tier.get(), List.of(staff.getUniqueId()), List.of(), true);
    }

    /** Offers hired allies, once, to queue tickets that have waited past the offer time. */
    private void offerAllies(Map<String, Matchmaker.Rules> rules, Instant now) {
        if (!allies.enabled()) return;
        Set<UUID> live = new HashSet<>();
        for (Matchmaker.Ticket ticket : matchmaker.tickets()) {
            live.add(ticket.id());
            Matchmaker.Rules rule = rules.get(ticket.expedition());
            if (rule == null || allyOffers.contains(ticket.id()) || !allies.offerable(ticket.queuedAt(), now, ticket.size(), rule.max())) continue;
            if (definition(ticket.expedition()).map(definition -> definition.story().raid()).orElse(false)) continue; // Raids are players only.
            allyOffers.add(ticket.id());
            Player leader = Bukkit.getPlayer(ticket.members().get(0));
            if (leader == null) continue;
            int slots = allies.slots(ticket.size(), rule.max());
            leader.playSound(leader.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1F, 1.2F);
            Chat.send(leader, "<gold>Still waiting?</gold> <gray>Start now with " + slots + " hired " + (slots == 1 ? "ally" : "allies")
                + ". Allies fight beside you, but an assisted run doesn't count toward the campaign or the expedition records.</gray> "
                + "<green><click:run_command:'/expedition allies'><hover:show_text:'Start with hired allies'>[Start with allies]</hover></click></green>");
        }
        allyOffers.retainAll(live);
    }

    /** {@code /expedition allies}: the ticket leader skips the rest of the wait and fills the empty slots with NPCs. */
    public void startWithAllies(Player player) {
        Optional<Matchmaker.Ticket> ticket = matchmaker.ticketOf(player.getUniqueId());
        if (ticket.isEmpty()) {
            Chat.notice(player, "Hired allies are offered once you've waited in the queue for a while.");
            return;
        }
        if (!ticket.get().members().get(0).equals(player.getUniqueId())) {
            Chat.error(player, "Only the player who queued the group can hire allies.");
            return;
        }
        ExpeditionDefinition definition = definitions.get(ticket.get().expedition());
        Optional<Tier> tier = definition == null ? Optional.empty() : tier(definition, ticket.get().tier());
        if (definition == null || tier.isEmpty() || !playable(definition)) {
            Chat.error(player, "That expedition is no longer open.");
            return;
        }
        Instant now = Instant.now();
        if (definition.story().raid()) {
            Chat.notice(player, "Raids are for players only; hired allies can't come.");
            return;
        }
        if (!allies.offerable(ticket.get().queuedAt(), now, ticket.get().size(), definition.maxParty())) {
            Duration left = Duration.between(now, ticket.get().queuedAt().plus(allies.offerAfter()));
            Chat.notice(player, allies.slots(ticket.get().size(), definition.maxParty()) == 0 ? "Your group is already full."
                : "Allies are offered after " + Chat.duration(allies.offerAfter()) + " in the queue; " + Chat.duration(left.withNanos(0)) + " to go.");
            return;
        }
        if (runs.size() + checks.size() >= maxInstances) {
            Chat.error(player, "Every expedition instance is busy. You keep your place in the queue.");
            return;
        }
        matchmaker.leave(player.getUniqueId());
        int slots = allies.slots(ticket.get().size(), definition.maxParty());
        if (ticket.get().size() == 1) launch(definition, tier.get(), ticket.get().members(), List.of(ticket.get()), false, slots);
        else startCheck(definition, tier.get(), ticket.get().members(), List.of(ticket.get()), player.getUniqueId(), slots);
    }

    public PartyScaling scaling() { return scaling; }
    public Allies allies() { return allies; }

    /** True inside a run that hired allies; such runs earn no campaign progress or records. */
    public boolean assisted(World world) {
        ExpeditionRun run = runIn(world);
        return run != null && run.assisted();
    }

    // ---------------------------------------------------------------------------------------------
    // Ending
    // ---------------------------------------------------------------------------------------------

    void teleport(Player player, Location location) {
        teleporting.add(player.getUniqueId());
        try {
            player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        } finally {
            teleporting.remove(player.getUniqueId());
        }
    }

    private Location fallbackHome() {
        Location spawn = plugin.travel() == null ? null : plugin.travel().publicSpawn();
        return spawn != null ? spawn : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    /** Sends one member back to where they entered from, with any loot that didn't fit. */
    void sendHome(Player player, ExpeditionRun run, String message) {
        UUID uuid = player.getUniqueId();
        runByPlayer.remove(uuid, run);
        run.left(uuid);
        if (player.isDead()) player.spigot().respawn();
        Location home = returns.location(uuid).orElseGet(this::fallbackHome);
        teleport(player, home);
        returns.forget(uuid);
        RunSupplies.strip(plugin, player);
        List<ItemStack> loot = run.takePendingLoot(uuid);
        if (!loot.isEmpty()) {
            player.getInventory().addItem(loot.toArray(ItemStack[]::new)).values()
                .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            Chat.notice(player, "The loot that didn't fit is at your feet.");
        }
        if (message != null) Chat.send(player, message);
    }

    boolean attached(ExpeditionRun run) { return runByPlayer.containsValue(run); }

    /** A member timed out while offline. Their return point stays on disk for when they next join. */
    void detach(ExpeditionRun run, UUID member) {
        runByPlayer.remove(member, run);
        run.left(member);
    }

    void close(ExpeditionRun run, String message) {
        if (run.state == ExpeditionRun.State.CLOSED) return;
        for (UUID member : run.members) {
            Player player = Bukkit.getPlayer(member);
            if (player != null && runByPlayer.get(member) == run) sendHome(player, run, message);
            else runByPlayer.remove(member, run);
        }
        run.close();
        runs.remove(run.id);
        if (run.key != null) keysInUse.remove(run.key.account());
        World world = run.world;
        if (world == null) return;
        runByWorld.remove(world.getUID());
        for (Player straggler : world.getPlayers()) {
            if (straggler.isDead()) straggler.spigot().respawn();
            teleport(straggler, returns.location(straggler.getUniqueId()).orElseGet(this::fallbackHome));
            returns.forget(straggler.getUniqueId());
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> worlds.destroy(world), 2L);
    }

    private void tick() {
        Instant now = Instant.now();
        for (ReadyCheck check : List.copyOf(checks.values())) {
            check.tick(now);
            resolve(check);
        }
        Map<String, Matchmaker.Rules> rules = new HashMap<>();
        for (ExpeditionDefinition definition : definitions.values())
            if (playable(definition)) rules.put(definition.id(), new Matchmaker.Rules(definition.minParty(), definition.maxParty()));
        int free = maxInstances - runs.size() - checks.size();
        if (free > 0) for (Matchmaker.Group group : matchmaker.match(rules, free, now)) {
            ExpeditionDefinition definition = definitions.get(group.expedition());
            Optional<Tier> tier = tier(definition, group.tier());
            if (tier.isEmpty()) continue;
            List<UUID> members = group.members();
            if (members.size() == 1) launch(definition, tier.get(), members, group.tickets(), false);
            else startCheck(definition, tier.get(), members, group.tickets(), null);
        }
        offerAllies(rules, now);
        long millis = System.currentTimeMillis();
        for (ExpeditionRun run : List.copyOf(runs.values())) {
            try {
                run.tick(millis);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.SEVERE, "Expedition run " + run.id + " failed; closing it", failure);
                close(run, "<red>The expedition hit a problem and closed. Staff have been told.</red>");
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------

    ExpeditionRun runIn(World world) { return world == null ? null : runByWorld.get(world.getUID()); }

    /** An expedition enemy: a run fights it, so claim protection leaves its hoglins and golems alone. */
    public boolean enemy(Entity entity) {
        ExpeditionRun run = runIn(entity.getWorld());
        return run != null && entity instanceof LivingEntity living && run.tracks(living);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        ExpeditionRun run = runByPlayer.get(event.getPlayer().getUniqueId());
        if (run != null && run.world != null && run.world.equals(event.getTo().getWorld())) run.moved(event.getPlayer(), event.getTo());
    }

    /** Ender pearls, chorus fruit and staff teleports inside a run count as walking for room triggers and the exit. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportWithin(PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        ExpeditionRun run = runByPlayer.get(event.getPlayer().getUniqueId());
        if (run != null && run.world != null && run.world.equals(event.getTo().getWorld()) && run.world.equals(event.getFrom().getWorld()))
            run.moved(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (!InstanceWorlds.isRunWorld(player.getWorld())) return;
        // Runs never cost gear: no drops, so the grave system has nothing to collect.
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        ExpeditionRun run = runByPlayer.get(player.getUniqueId());
        if (run != null && run.world == player.getWorld()) run.died(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        ExpeditionRun run = runByPlayer.get(event.getPlayer().getUniqueId());
        if (run == null || run.world == null || run.state == ExpeditionRun.State.CLOSED) return;
        if (!run.world.equals(event.getPlayer().getWorld())) return;
        event.setRespawnLocation(run.respawned(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        checkOf(uuid).ifPresent(check -> { check.decline(uuid); resolve(check); });
        matchmaker.leave(uuid).ifPresent(ticket -> ticket.members().forEach(member -> {
            Player other = Bukkit.getPlayer(member);
            if (other != null && !member.equals(uuid)) Chat.notice(other, Chat.escape(event.getPlayer().getName()) + " logged off, so your group left the queue.");
        }));
        ExpeditionRun run = runByPlayer.get(uuid);
        if (run != null) {
            if (run.state == ExpeditionRun.State.ACTIVE) run.quit(event.getPlayer());
            else if (run.state == ExpeditionRun.State.ENDING) detach(run, uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        RunStage.restoreAfterCutscene(player, plugin);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            ExpeditionRun run = runByPlayer.get(player.getUniqueId());
            if (run != null && run.rejoin(player)) {
                Chat.info(player, "Welcome back. Your party is still in <white>" + Chat.escape(run.expedition.title()) + "</white>.");
                return;
            }
            if (returns.has(player.getUniqueId()) || InstanceWorlds.isRunWorld(player.getWorld())) {
                teleport(player, returns.location(player.getUniqueId()).orElseGet(this::fallbackHome));
                returns.forget(player.getUniqueId());
                Chat.notice(player, "Your expedition ended while you were away, so you've been returned.");
            }
            if (!InstanceWorlds.isRunWorld(player.getWorld())) RunSupplies.strip(plugin, player);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        ExpeditionRun run = runByPlayer.get(player.getUniqueId());
        if (run == null || run.world == null || !run.world.equals(event.getFrom()) || teleporting.contains(player.getUniqueId())) return;
        runByPlayer.remove(player.getUniqueId(), run);
        run.left(player.getUniqueId());
        returns.forget(player.getUniqueId());
        run.broadcast("<gray>" + Chat.escape(player.getName()) + " left the expedition.</gray>");
        Chat.notice(player, "You left " + Chat.escape(run.expedition.title()) + ".");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() == null || teleporting.contains(event.getPlayer().getUniqueId()) || event instanceof PlayerPortalEvent) return;
        World to = event.getTo().getWorld();
        Player player = event.getPlayer();
        if (to == null || to.equals(event.getFrom().getWorld()) || player.hasPermission(ADMIN)) return;
        if (InstanceWorlds.isRunWorld(to)) {
            event.setCancelled(true);
            Chat.error(player, "That expedition is a private instance. Join through " + Chat.command("expedition") + ".");
        } else if (definitions.values().stream().anyMatch(definition -> definition.templateWorld().equals(to.getName()))) {
            event.setCancelled(true);
            Chat.error(player, "That world is a staff expedition template.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (vanillaPortals) return;
        var cause = event.getCause();
        if (cause != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && cause != PlayerTeleportEvent.TeleportCause.END_PORTAL
            && cause != PlayerTeleportEvent.TeleportCause.END_GATEWAY) return;
        if (InstanceWorlds.isRunWorld(event.getFrom().getWorld()) || isTemplate(event.getFrom().getWorld())) {
            if (cause == PlayerTeleportEvent.TeleportCause.END_GATEWAY) return;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        Long last = portalNotices.get(event.getPlayer().getUniqueId());
        if (last == null || now - last > 5000) {
            portalNotices.put(event.getPlayer().getUniqueId(), now);
            Chat.notice(event.getPlayer(), "The portals are sealed. Reach the Nether and the End with your party through "
                + Chat.command("expedition") + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!vanillaPortals && event.getPortalType() != org.bukkit.PortalType.END_GATEWAY) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (vanillaPortals || event.getReason() == PortalCreateEvent.CreateReason.END_PLATFORM) return;
        if (isTemplate(event.getWorld()) || InstanceWorlds.isRunWorld(event.getWorld())) return;
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player)
            Chat.notice(player, "The portal won't take. Reach the Nether with your party through " + Chat.command("expedition") + ".");
    }

    private boolean isTemplate(World world) {
        return world != null && definitions.values().stream().anyMatch(definition -> definition.templateWorld().equals(world.getName()));
    }

    /**
     * Expedition enemies drop no vanilla loot (no saddles, emeralds or worn gear): what they carry is
     * the expedition's own. Runs before the reward drops, relics and purses are added at MONITOR.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnemyDrops(EntityDeathEvent event) {
        ExpeditionRun run = runIn(event.getEntity().getWorld());
        if (run == null || event.getEntity() instanceof Player || !run.tracks(event.getEntity())) return;
        event.getDrops().clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        ExpeditionRun run = runIn(event.getEntity().getWorld());
        if (run == null || event.getEntity() instanceof Player) return;
        if (run.isAlly(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            run.allyDied(event.getEntity());
        } else if (run.tracks(event.getEntity())) run.mobDied(event.getEntity());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        ExpeditionRun run = runIn(event.getEntity().getWorld());
        if (run == null) return;
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
        Entity victim = event.getEntity();
        boolean friendlyVictim = victim instanceof Player || run.isAlly(victim);
        boolean friendlySource = source instanceof Player || run.isAlly(source);
        // Allies and players never hurt each other; allies hit enemies harder as tiers toughen them.
        if (friendlyVictim && friendlySource && (run.isAlly(victim) || run.isAlly(source))) {
            event.setCancelled(true);
            return;
        }
        if (run.isAlly(source) && victim instanceof LivingEntity living && run.tracks(living)) {
            event.setDamage(event.getDamage() * run.allyDamageMultiplier());
            return;
        }
        // Enemies never hurt each other: a council's arrows or a slam shouldn't thin their own ranks.
        if (victim instanceof LivingEntity hit && run.tracks(hit) && source instanceof LivingEntity attacker && run.tracks(attacker)) {
            event.setCancelled(true);
            return;
        }
        if (victim instanceof Player hurt) run.combat(hurt);
        if (source instanceof Player attacker) run.combat(attacker);
        if (run.isNpc(victim)) {
            event.setCancelled(true);
            return;
        }
        if (friendlyVictim && source instanceof LivingEntity living && run.tracks(living)) event.setDamage(event.getDamage() * run.damageMultiplier(living));
    }

    /** Fire left by hazards and abilities is for players; it doesn't burn the enemies who cast it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnemyBurns(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof LivingEntity living)) return;
        var cause = event.getCause();
        if (cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FIRE && cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.CAMPFIRE) return;
        ExpeditionRun run = runIn(living.getWorld());
        if (run != null && run.tracks(living)) {
            event.setCancelled(true);
            living.setFireTicks(0);
        }
    }

    /**
     * Thrown items and mobs press pressure plates inside expeditions: plate puzzles rely on it, and
     * these private instances sit outside claimable land, where claim protection blocks it.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlate(org.bukkit.event.entity.EntityInteractEvent event) {
        if (!event.isCancelled() || !event.getBlock().getType().name().endsWith("PRESSURE_PLATE")) return;
        World world = event.getBlock().getWorld();
        if (InstanceWorlds.isRunWorld(world) || isTemplate(world)) event.setCancelled(false);
    }

    /** Gear doesn't wear down inside a run: a long key shouldn't cost a repair bill on top. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onWear(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if (runIn(event.getPlayer().getWorld()) != null) event.setCancelled(true);
    }

    /** Poison clouds from abilities and hazards touch only players. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloud(org.bukkit.event.entity.AreaEffectCloudApplyEvent event) {
        if (runIn(event.getEntity().getWorld()) != null) event.getAffectedEntities().removeIf(entity -> !(entity instanceof Player));
    }

    /** Campfires and puzzle blocks. Levers and buttons still work as normal; the run reads them. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
            || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        ExpeditionRun run = runByPlayer.get(event.getPlayer().getUniqueId());
        if (run == null || run.world == null || !run.world.equals(event.getClickedBlock().getWorld())) return;
        var type = event.getClickedBlock().getType();
        // Food on a campfire or flint on it would change the rest point; the run handles campfires itself.
        if (type == org.bukkit.Material.CAMPFIRE || type == org.bukkit.Material.SOUL_CAMPFIRE) event.setCancelled(true);
        // Chests, barrels and crates in a run are the run's: loot, reward chests and supply crates.
        if (type == org.bukkit.Material.CHEST || type == org.bukkit.Material.TRAPPED_CHEST || type == org.bukkit.Material.BARREL
            || type == org.bukkit.Material.ENDER_CHEST) event.setCancelled(true);
        var block = event.getClickedBlock();
        Bukkit.getScheduler().runTask(plugin, () -> run.interact(event.getPlayer(), block));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTargetHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (event.getHitBlock() == null || !(event.getEntity().getShooter() instanceof Player)) return;
        ExpeditionRun run = runIn(event.getHitBlock().getWorld());
        if (run != null) run.hitBlock(event.getHitBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTalk(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        ExpeditionRun run = runIn(event.getRightClicked().getWorld());
        if (run == null || !run.isNpc(event.getRightClicked())) return;
        event.setCancelled(true); // No trading with a dungeon guide.
        if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) run.talk(event.getPlayer(), event.getRightClicked());
    }

    /** Encounter magma cubes don't split and encounter piglins and hoglins don't turn: every enemy is one the run counts. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplit(org.bukkit.event.entity.SlimeSplitEvent event) {
        if (runIn(event.getEntity().getWorld()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(org.bukkit.event.entity.EntityTransformEvent event) {
        if (runIn(event.getEntity().getWorld()) != null) event.setCancelled(true);
    }

    /** Loot windows: take items out freely; an unbought bonus chest only offers its price button. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLootClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RunLoot.Window window)) return;
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(window.player)) {
            event.setCancelled(true);
            return;
        }
        boolean top = event.getClickedInventory() == event.getView().getTopInventory();
        if (window.price > 0 && !window.paid) {
            event.setCancelled(true);
            if (top && event.getRawSlot() == RunLoot.BUY_SLOT) window.loot.buy(player, window);
            return;
        }
        // Nothing goes in: only taking items out.
        if (!top && event.isShiftClick() || top && !event.getCursor().getType().isAir()
            || event.getAction() == org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP || event.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR)
            event.setCancelled(true);
    }

    @EventHandler
    public void onLootDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof RunLoot.Window) event.setCancelled(true);
    }

    @EventHandler
    public void onLootClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof RunLoot.Window window && event.getPlayer() instanceof Player player
            && player.getUniqueId().equals(window.player)) window.loot.closed(player, window);
    }

    /** Falling rocks from a hazard never become blocks. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRockLands(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.FallingBlock)) return;
        ExpeditionRun run = runIn(event.getBlock().getWorld());
        if (run != null && run.ownsRock(event.getEntity())) {
            event.setCancelled(true);
            event.getEntity().remove();
        }
    }

    /** The Hedge-witch's harming potions only touch enemies. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAllyPotion(org.bukkit.event.entity.PotionSplashEvent event) {
        ExpeditionRun run = runIn(event.getEntity().getWorld());
        if (run == null || !(event.getPotion().getShooter() instanceof Entity thrower) || !run.isAlly(thrower)) return;
        for (LivingEntity hit : event.getAffectedEntities())
            if (hit instanceof Player || run.isAlly(hit)) event.setIntensity(hit, 0);
    }

    /** Allies never turn on the party, and nothing in a run makes them. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAllyTarget(org.bukkit.event.entity.EntityTargetEvent event) {
        ExpeditionRun run = runIn(event.getEntity().getWorld());
        if (run != null && run.isAlly(event.getEntity()) && (event.getTarget() instanceof Player || run.isAlly(event.getTarget())))
            event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (InstanceWorlds.isRunWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission(ADMIN)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (InstanceWorlds.isRunWorld(event.getBlock().getWorld()) && !event.getPlayer().hasPermission(ADMIN)) event.setCancelled(true);
    }

    // ---------------------------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------------------------

    public boolean command(CommandSender sender, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        // "/expedition guide <id>" is the player's guide book; bare "/expedition guide" is the staff walkthrough.
        if (action.equals("guide") && args.length == 2 && sender instanceof Player reader) {
            definition(args[1]).filter(definition -> playable(definition) || reader.hasPermission(ADMIN))
                .ifPresentOrElse(definition -> menu.openGuide(reader, definition),
                    () -> Chat.error(reader, "Unknown expedition. See " + Chat.command("expedition list") + "."));
            return true;
        }
        if (setup != null && setup.handles(action)) return setup.command(sender, args);
        if (!(sender instanceof Player player)) {
            Chat.playersOnly(sender);
            return true;
        }
        switch (action) {
            case "" -> menu.open(player);
            case "start", "queue" -> {
                if (args.length < 2 || args.length > 3) Chat.usage(player, "/expedition " + action + " <expedition> [tier]");
                else join(player, args[1], args.length == 3 ? args[2] : null, action.equals("queue"));
            }
            case "leave" -> leave(player);
            case "ready", "accept" -> ready(player);
            case "decline" -> decline(player);
            case "allies" -> startWithAllies(player);
            case "forge" -> forge.open(player);
            case "rewards", "bonus" -> {
                ExpeditionRun run = runByPlayer.get(player.getUniqueId());
                if (run == null) Chat.notice(player, "Reward chests open when your party wins an expedition.");
                else if (action.equals("rewards")) run.loot.openReward(player);
                else run.loot.openBonus(player);
            }
            case "key", "keystone" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("start")) startKey(player);
                else if (args.length >= 2 && args[1].equalsIgnoreCase("give") && player.hasPermission(ADMIN)) giveKey(player, args);
                else showKey(player);
            }
            case "list" -> list(player);
            case "info" -> {
                if (args.length != 2) Chat.usage(player, "/expedition info <expedition>");
                else definition(args[1]).filter(definition -> playable(definition) || player.hasPermission(ADMIN))
                    .ifPresentOrElse(definition -> menu.openDetail(player, definition),
                        () -> Chat.error(player, "Unknown expedition. See " + Chat.command("expedition list") + "."));
            }
            case "status" -> status(player);
            case "routes" -> {
                if (plugin.expeditionRoutes() == null) Chat.notice(player, "Travel with " + Chat.command("warps") + ".");
                else plugin.expeditionRoutes().command(player, java.util.Arrays.copyOfRange(args, 1, args.length));
            }
            default -> {
                Chat.error(player, "Unknown expedition command.");
                Chat.usage(player, "/expedition [start|queue|leave|ready|decline|key|forge|list|info|status]");
                if (plugin.travel() != null && plugin.travel().warpNames().contains(action))
                    Chat.info(player, "Looking for the warp? Use " + Chat.command("warp " + action) + ".");
            }
        }
        return true;
    }

    private void list(Player player) {
        List<ExpeditionDefinition> open = definitions.values().stream().filter(this::playable).toList();
        if (open.isEmpty()) { Chat.notice(player, "No expeditions are open yet."); return; }
        Chat.send(player, "<gold><bold>Expeditions</bold></gold>");
        for (ExpeditionDefinition definition : open) {
            Optional<String> lock = plugin.campaign().expeditionLock(definition.id());
            int rating = net.nerdsmp.paper.expedition.content.Difficulty.rating(definition);
            Chat.send(player, "<gray>•</gray> <click:run_command:'/expedition info " + definition.id() + "'><aqua>" + Chat.escape(definition.title())
                + "</aqua></click> <gold>" + net.nerdsmp.paper.expedition.content.Difficulty.stars(rating) + "</gold> <dark_gray>"
                + definition.dimension().key() + " · level " + definition.level() + " · "
                + definition.minParty() + "-" + definition.maxParty() + " players</dark_gray>"
                + lock.map(milestone -> " <red>sealed: " + Chat.escape(milestone) + "</red>").orElse(""));
        }
    }

    private void status(Player player) {
        UUID uuid = player.getUniqueId();
        ExpeditionRun run = runByPlayer.get(uuid);
        if (run != null) {
            Chat.info(player, "You're in <white>" + Chat.escape(run.expedition.title()) + "</white> (" + Chat.escape(run.tier.title()) + ")"
                + (run.progress == null ? ", still preparing." : ", room " + Math.min(run.progress.roomCount(), run.progress.roomIndex() + 1)
                + "/" + run.progress.roomCount() + (run.key != null ? ", " + run.progress.deaths() + " deaths." : ", " + run.progress.lives() + " lives left.")));
            return;
        }
        Optional<ReadyCheck> check = checkOf(uuid);
        if (check.isPresent()) { Chat.info(player, "A ready check is waiting: " + Chat.command("expedition ready") + "."); return; }
        Optional<Matchmaker.Ticket> ticket = matchmaker.ticketOf(uuid);
        if (ticket.isPresent()) {
            var definition = definitions.get(ticket.get().expedition());
            Chat.info(player, "Queued for <white>" + Chat.escape(definition == null ? ticket.get().expedition() : definition.title()) + "</white> for "
                + Chat.duration(Duration.between(ticket.get().queuedAt(), Instant.now())) + ". "
                + matchmaker.waiting(ticket.get().expedition(), ticket.get().tier()) + " players are waiting for this run.");
            if (definition != null && ticket.get().members().get(0).equals(uuid)
                && !definition.story().raid() && allies.offerable(ticket.get().queuedAt(), Instant.now(), ticket.get().size(), definition.maxParty()))
                Chat.info(player, "Tired of waiting? " + Chat.command("expedition allies") + " fills the empty places with hired allies.");
            return;
        }
        Chat.notice(player, "You aren't queued. Browse expeditions with " + Chat.command("expedition") + ".");
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("start", "queue", "leave", "ready", "decline", "allies", "key", "forge", "list", "info", "guide", "status"));
            if (sender.hasPermission(ADMIN) && setup != null) options.addAll(setup.subcommands());
            return options;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (setup != null && setup.handles(action) && !action.equals("guide")) return setup.complete(sender, args);
        if (args.length == 2 && List.of("key", "keystone").contains(action))
            return sender.hasPermission(ADMIN) ? List.of("start", "give") : List.of("start");
        if (args.length == 2 && List.of("start", "queue", "info", "guide").contains(action))
            return definitions.values().stream().filter(definition -> playable(definition) || sender.hasPermission(ADMIN)).map(ExpeditionDefinition::id).toList();
        if (args.length == 3 && List.of("start", "queue").contains(action))
            return definition(args[1]).map(definition -> tiersFor(definition).stream().map(Tier::id).toList()).orElse(List.of());
        return List.of();
    }

    /** Staff overview of live runs. */
    public List<String> describeRuns() {
        List<String> lines = new ArrayList<>();
        int index = 1;
        for (ExpeditionRun run : runs.values()) {
            String names = String.join(", ", run.members.stream().map(Bukkit::getOfflinePlayer).map(player -> String.valueOf(player.getName())).toList());
            lines.add(index++ + ". " + run.expedition.id() + " (" + run.tier.id() + (run.test ? ", test" : "") + ") " + run.state.name().toLowerCase(Locale.ROOT)
                + (run.progress == null ? "" : ", room " + Math.min(run.progress.roomCount(), run.progress.roomIndex() + 1) + "/" + run.progress.roomCount()
                + ", lives " + run.progress.lives()) + " — " + names);
        }
        return lines;
    }

    public boolean stopRun(int index) {
        List<ExpeditionRun> list = List.copyOf(runs.values());
        if (index < 1 || index > list.size()) return false;
        close(list.get(index - 1), "<yellow>Staff closed this expedition.</yellow>");
        return true;
    }

    @Override
    public void close() {
        task.cancel();
        for (ExpeditionRun run : List.copyOf(runs.values())) {
            for (UUID member : run.members) {
                Player player = Bukkit.getPlayer(member);
                if (player == null || runByPlayer.get(member) != run) continue;
                if (player.isDead()) player.spigot().respawn();
                teleport(player, returns.location(member).orElseGet(this::fallbackHome));
                returns.forget(member);
                run.takePendingLoot(member).forEach(item -> player.getInventory().addItem(item).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left)));
            }
            run.close();
            if (run.world != null) Bukkit.unloadWorld(run.world, false); // Folder is removed on the next start.
        }
        runs.clear();
        runByPlayer.clear();
        runByWorld.clear();
    }
}

package net.nerdsmp.paper;

import net.nerdsmp.api.service.AccountService;
import net.nerdsmp.api.service.CampaignService;
import net.nerdsmp.api.service.ClaimService;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.EntitlementService;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.api.service.PartyService;
import net.nerdsmp.api.service.ReputationService;
import net.nerdsmp.paper.command.CommandRouter;
import net.nerdsmp.paper.chat.ChannelChatManager;
import net.nerdsmp.paper.activity.ExcavationService;
import net.nerdsmp.paper.activity.FishingService;
import net.nerdsmp.paper.achievement.AchievementService;
import net.nerdsmp.paper.alchemy.AlchemyService;
import net.nerdsmp.paper.anticheat.WatchdogService;
import net.nerdsmp.paper.campaign.ServerProgressionService;
import net.nerdsmp.paper.engagement.EngagementService;
import net.nerdsmp.paper.forge.ArcaneForgeService;
import net.nerdsmp.paper.forge.ArcaneEnchantingService;
import net.nerdsmp.paper.gui.MenuManager;
import net.nerdsmp.paper.grave.GraveManager;
import net.nerdsmp.paper.identity.IdentityProvider;
import net.nerdsmp.paper.item.GearSetService;
import net.nerdsmp.paper.item.CustomRecipeService;
import net.nerdsmp.paper.item.PaperItemService;
import net.nerdsmp.paper.item.IdentificationService;
import net.nerdsmp.paper.item.RepairService;
import net.nerdsmp.paper.item.StarterKitService;
import net.nerdsmp.paper.item.MerchantTradeService;
import net.nerdsmp.paper.job.DiminishingJobService;
import net.nerdsmp.paper.job.ServerOrderService;
import net.nerdsmp.paper.listener.ClaimProtectionListener;
import net.nerdsmp.paper.listener.SessionListener;
import net.nerdsmp.paper.listener.SkillListener;
import net.nerdsmp.paper.mob.CustomMobManager;
import net.nerdsmp.paper.mob.VanillaBossMechanicsService;
import net.nerdsmp.paper.npc.NpcManager;
import net.nerdsmp.paper.npc.ReputationContractService;
import net.nerdsmp.paper.loot.RewardDropService;
import net.nerdsmp.paper.player.PlayerGuideService;
import net.nerdsmp.paper.player.PlayerSettingsService;
import net.nerdsmp.paper.player.BankService;
import net.nerdsmp.paper.pvp.PvpManager;
import net.nerdsmp.paper.presentation.DamageDisplayService;
import net.nerdsmp.paper.presentation.MobHealthBarService;
import net.nerdsmp.paper.presentation.PlayerPresentationService;
import net.nerdsmp.paper.service.InMemoryAccountService;
import net.nerdsmp.paper.service.InMemoryCampaignService;
import net.nerdsmp.paper.service.InMemoryClaimService;
import net.nerdsmp.paper.service.InMemoryEconomyService;
import net.nerdsmp.paper.service.InMemoryEntitlementService;
import net.nerdsmp.paper.market.DurableMarketService;
import net.nerdsmp.paper.market.MarketController;
import net.nerdsmp.paper.market.AuctionController;
import net.nerdsmp.paper.auction.DurableAuctionService;
import net.nerdsmp.paper.service.InMemoryPartyService;
import net.nerdsmp.paper.service.InMemoryProgressionService;
import net.nerdsmp.paper.reputation.PlayerYamlReputationService;
import net.nerdsmp.paper.session.SessionRegistry;
import net.nerdsmp.paper.storage.JdbcAccountService;
import net.nerdsmp.paper.storage.JdbcEconomyService;
import net.nerdsmp.paper.storage.JdbcEntitlementService;
import net.nerdsmp.paper.storage.JdbcProgressionService;
import net.nerdsmp.paper.storage.JdbcReputationService;
import net.nerdsmp.paper.storage.PostgresDatabase;
import net.nerdsmp.paper.storage.PlayerDataStore;
import net.nerdsmp.paper.staff.StaffManager;
import net.nerdsmp.paper.social.SocialManager;
import net.nerdsmp.paper.trade.TradeManager;
import net.nerdsmp.paper.travel.TravelManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class NerdSmpPlugin extends JavaPlugin {
    private PostgresDatabase database;
    private AccountService accounts;
    private EconomyService economy;
    private ProgressionService progression;
    private ClaimService claims;
    private net.nerdsmp.paper.claim.HomesteadController homesteads;
    private CampaignService campaigns;
    private PartyService parties;
    private EntitlementService entitlements;
    private ReputationService reputation;
    private DurableMarketService market;
    private MarketController marketController;
    private net.nerdsmp.paper.permission.StaffPermissions staffPermissions;
    private net.nerdsmp.paper.anticheat.WatchdogPanel watchdogPanel;
    private net.nerdsmp.paper.webapi.WebApi webApi;
    private net.nerdsmp.paper.webapi.WebsiteRewards websiteRewards;
    public net.nerdsmp.paper.webapi.WebApi webApi() { return webApi; }
    public net.nerdsmp.paper.permission.StaffPermissions staffPermissions() { return staffPermissions; }
    private DurableAuctionService auctions;
    private AuctionController auctionController;
    private PaperItemService items;
    private GearSetService gearSets;
    private net.nerdsmp.paper.economy.PaymentController payments;
    private net.nerdsmp.paper.leaderboard.LeaderboardService leaderboards;
    public net.nerdsmp.paper.economy.PaymentController payments() { return payments; }
    public net.nerdsmp.paper.leaderboard.LeaderboardService leaderboards() { return leaderboards; }
    private CustomRecipeService recipes;
    private EngagementService engagement;
    private ReputationContractService reputationContracts;
    private StarterKitService starterKit;
    private SessionRegistry sessions;
    private GraveManager graves;
    private PvpManager pvp;
    private IdentityProvider identities;
    private DiminishingJobService jobs;
    private ServerOrderService orders;
    private CustomMobManager mobs;
    private VanillaBossMechanicsService bossMechanics;
    private PlayerPresentationService presentation;
    private DamageDisplayService damageDisplays;
    private MobHealthBarService mobHealthBars;
    private MenuManager menus;
    private ArcaneForgeService forge;
    private NpcManager npcs;
    private StaffManager staff;
    private PlayerSettingsService settings;
    private SocialManager social;
    private ChannelChatManager chat;
    private TradeManager trade;
    private TravelManager travel;
    private PlayerGuideService guide;
    private RewardDropService rewardDrops;
    private net.nerdsmp.paper.loot.profile.MobLootCommand mobLootCommands;
    private net.nerdsmp.paper.engine.ItemEngine itemEngine;
    private net.nerdsmp.paper.engine.mob.MobEngine mobEngine;
    private net.nerdsmp.paper.engine.ContentEditor contentEditor;
    private net.nerdsmp.paper.studio.StudioUi studioUi;
    private net.nerdsmp.paper.studio.DeveloperStudio developerStudio;
    private net.nerdsmp.paper.studio.LootStudio lootStudio;
    private net.nerdsmp.paper.studio.WorldStudio worldStudio;
    private net.nerdsmp.paper.studio.qa.QAStudio qaStudio;
    private net.nerdsmp.paper.studio.npc.NpcEngine npcEngine;
    private net.nerdsmp.paper.studio.npc.NPCStudio npcStudio;
    private net.nerdsmp.paper.studio.RecipeEngine recipeEngine;
    private net.nerdsmp.paper.studio.RecipeStudio recipeStudio;
    private final java.util.ArrayDeque<java.util.UUID> contentRefreshQueue=new java.util.ArrayDeque<>();
    private org.bukkit.scheduler.BukkitTask contentRefreshTask;
    private SkillListener skillListener;
    private net.nerdsmp.paper.gathering.HoeHarvestService hoeHarvest;
    private net.nerdsmp.paper.gathering.OreSmeltingService oreSmelting;
    private net.nerdsmp.paper.gathering.AutoSmeltService autoSmelt;
    private net.nerdsmp.paper.loot.ExpansionLootService expansionLoot;
    private net.nerdsmp.paper.gathering.ForestryService forestry;
    public SkillListener skills() { return skillListener; }
    public net.nerdsmp.paper.gathering.HoeHarvestService hoeHarvest() { return hoeHarvest; }
    public net.nerdsmp.paper.gathering.OreSmeltingService oreSmelting() { return oreSmelting; }
    public net.nerdsmp.paper.gathering.AutoSmeltService autoSmelt() { return autoSmelt; }
    public net.nerdsmp.paper.loot.ExpansionLootService expansionLoot() { return expansionLoot; }
    public net.nerdsmp.paper.gathering.ForestryService forestry() { return forestry; }
    private net.nerdsmp.paper.resource.ResourceProvenanceService resources;
    private ArcaneEnchantingService enchanting;
    private net.nerdsmp.paper.forge.arcane.ArcaneForge arcane;
    private net.nerdsmp.paper.forge.arcane.ArcaneMenus arcaneMenus;
    private net.nerdsmp.paper.forge.arcane.ArcaneSignatureService arcaneSignatures;
    private net.nerdsmp.paper.forge.arcane.ArcaneCommands arcaneCommands;
    private net.nerdsmp.paper.forge.arcane.ArcaneEffectService arcaneEffects;
    public net.nerdsmp.paper.forge.arcane.ArcaneEffectService arcaneEffects() { return arcaneEffects; }
    public net.nerdsmp.paper.forge.arcane.ArcaneForge arcane() { return arcane; }
    public net.nerdsmp.paper.forge.arcane.ArcaneMenus arcaneMenus() { return arcaneMenus; }
    public net.nerdsmp.paper.forge.arcane.ArcaneSignatureService arcaneSignatures() { return arcaneSignatures; }
    private AlchemyService alchemy;
    private FishingService fishing;
    private ExcavationService excavation;
    private net.nerdsmp.paper.activity.CollectionMasteryService collections;
    private WatchdogService watchdog;
    private net.nerdsmp.paper.anticheat.ConnectionLog connections;
    private IdentificationService identification;
    private BankService bank;
    private RepairService repairs;
    private PlayerDataStore playerData;
    private ServerProgressionService serverProgression;
    private MerchantTradeService merchantTrades;
    private AchievementService achievements;
    private net.nerdsmp.paper.skyblock.SkyblockController skyblock;
    private boolean skyblockEnabled;
    private net.nerdsmp.paper.expedition.ExpeditionController expeditions;
    public net.nerdsmp.paper.expedition.ExpeditionController expeditions() { return expeditions; }
    private net.nerdsmp.paper.zone.ZoneStore zones;
    private net.nerdsmp.paper.zone.ZoneCommand zoneCommands;
    private net.nerdsmp.paper.zone.ZoneSpawner zoneSpawner;
    private net.nerdsmp.paper.zone.AreaWand areaWand;
    private net.nerdsmp.paper.digsite.DigSiteService digSites;
    private net.nerdsmp.paper.digsite.DigSiteCommand digSiteCommands;
    public net.nerdsmp.paper.digsite.DigSiteService digSites() { return digSites; }
    public net.nerdsmp.paper.digsite.DigSiteCommand digSiteCommands() { return digSiteCommands; }
    public net.nerdsmp.paper.zone.ZoneStore zones() { return zones; }
    public net.nerdsmp.paper.zone.ZoneCommand zoneCommands() { return zoneCommands; }
    public net.nerdsmp.paper.zone.ZoneSpawner zoneSpawner() { return zoneSpawner; }

    public boolean skyblockEnabled() { return skyblockEnabled; }
    public net.nerdsmp.paper.presentation.ServerBranding branding() {
        return net.nerdsmp.paper.presentation.ServerBranding.read(getConfig(), displayName(), skyblockEnabled);
    }
    /** Display branding only; storage keys, plugin identity and item namespaces never change. */
    public String displayName() {
        return net.nerdsmp.paper.presentation.ServerBranding.publicName(getConfig().getString(
            skyblockEnabled ? "skyblock.display-name" : "presentation.display-name",
            net.nerdsmp.paper.presentation.BrandPalette.NAME));
    }
    public net.nerdsmp.paper.skyblock.SkyblockController skyblock() { return skyblock; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            String gameplay = System.getProperty("nerdsmp.gameplay", getConfig().getString("gameplay.mode", "skyblock"));
            if (!gameplay.equals("smp") && !gameplay.equals("skyblock")) throw new IllegalArgumentException("gameplay.mode must be smp or skyblock");
            skyblockEnabled = gameplay.equals("skyblock");
            initializeServices();
            registerListeners();
            registerCommands();
        } catch (Exception exception) {
            getLogger().severe("NerdSMP failed to initialize: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            // A loaded island world must never remain online without its protection service.
            if (skyblockEnabled) getServer().shutdown();
            return;
        }

        getLogger().info("NerdSMP " + getPluginMeta().getVersion() + " enabled with "
            + getConfig().getString("storage.mode", "memory") + " storage.");
    }

    @Override
    public void onDisable() {
        if (websiteRewards != null) websiteRewards.close();
        if (webApi != null) webApi.close();
        if (autoSmelt != null) autoSmelt.close();
        if (forestry != null) forestry.close();
        if (identification != null) identification.close();
        if (payments != null) payments.close();
        if (leaderboards != null) leaderboards.close();
        if (watchdogPanel != null) watchdogPanel.close();
        if (staffPermissions != null) staffPermissions.close();
        if (oreSmelting != null) oreSmelting.close();
        if (recipeStudio != null) recipeStudio.close();
        if (npcStudio != null) npcStudio.close();
        if (lootStudio != null) lootStudio.close();
        if (studioUi != null) studioUi.close();
        if (recipeEngine != null) recipeEngine.close();
        if (contentEditor != null) contentEditor.close();
        if (contentRefreshTask != null) { contentRefreshTask.cancel(); contentRefreshTask=null; }
        contentRefreshQueue.clear();
        if (menus != null) menus.close();
        if (digSites != null) digSites.close();
        if (zoneSpawner != null) zoneSpawner.close();
        if (mobs != null) mobs.close();
        if (skyblock != null) skyblock.close();
        if (expeditions != null) expeditions.close();
        if (marketController != null) marketController.close();
        if (auctionController != null) auctionController.close();
        if (alchemy != null) {
            alchemy.close();
        }
        if (serverProgression != null) {
            serverProgression.close();
        }
        if (arcaneEffects != null) arcaneEffects.close();
        if (enchanting != null) {
            enchanting.close();
        }
        if (connections != null) connections.close();
        if (watchdog != null) {
            watchdog.close();
        }
        if (guide != null) {
            guide.close();
        }
        if (trade != null) {
            trade.close();
        }
        if (travel != null) {
            travel.close();
        }
        if (social != null) {
            social.close();
        }
        if (settings != null) {
            settings.close();
        }
        if (staff != null) {
            staff.close();
        }
        if (npcs != null) {
            npcs.close();
        }
        if (bank != null) {
            bank.close();
        }
        if (engagement != null) {
            engagement.close();
        }
        if (reputationContracts != null) {
            reputationContracts.close();
        }
        if (recipes != null) {
            recipes.close();
        }
        if (damageDisplays != null) {
            damageDisplays.close();
        }
        if (mobHealthBars != null) {
            mobHealthBars.close();
        }
        if (bossMechanics != null) {
            bossMechanics.close();
        }
        if (presentation != null) {
            presentation.close();
        }
        if (reputation instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                getLogger().severe("Could not flush reputation storage: " + exception.getMessage());
            }
        }
        if (database != null) {
            database.close();
        }
    }

    private void initializeServices() throws Exception {
        String storageMode = getConfig().getString("storage.mode", "yaml").toLowerCase(Locale.ROOT);
        playerData = new PlayerDataStore(getDataFolder(), getLogger());
        staffPermissions = new net.nerdsmp.paper.permission.StaffPermissions(this);
        if (storageMode.equals("postgres")) {
            database = new PostgresDatabase(
                resolveSetting("storage.postgres.jdbc-url"),
                resolveSetting("storage.postgres.username"),
                resolveSetting("storage.postgres.password"),
                getConfig().getInt("storage.postgres.maximum-pool-size", 8),
                getLogger()
            );
            accounts = new JdbcAccountService(database.dataSource(), database.executor());
            economy = new JdbcEconomyService(database.dataSource(), database.executor());
            entitlements = new JdbcEntitlementService(database.dataSource(), database.executor());
            progression = new JdbcProgressionService(
                database.dataSource(),
                database.executor(),
                getConfig().getString("season.id", "season-1"),
                Duration.ofDays(getConfig().getLong("season.mastery-respec-cooldown-days", 14L))
            );
            reputation = new JdbcReputationService(
                database.dataSource(), database.executor(), getConfig().getString("season.id", "season-1")
            );
        } else if (storageMode.equals("yaml") || storageMode.equals("memory")) {
            accounts = new InMemoryAccountService(playerData);
            economy = new InMemoryEconomyService(playerData);
            entitlements = new InMemoryEntitlementService(playerData);
            progression = new InMemoryProgressionService(playerData, getConfig().getString("season.id", "season-1"));
            reputation = new PlayerYamlReputationService(playerData,
                getConfig().getString("season.id", "season-1"));
            getLogger().info("Using local per-player YAML storage in player_data/<uuid>.yml.");
        } else {
            throw new IllegalArgumentException("Unsupported storage.mode: " + storageMode);
        }

        var regionPolicy = new net.nerdsmp.paper.claim.ClaimRegionPolicy(getConfig(), getServer().getWorlds());
        claims = new InMemoryClaimService(
            getConfig().getInt("claims.starter-capacity", 4),
            getConfig().getInt("claims.maximum-capacity", 64),
            playerData,
            getConfig().getString("season.id", "season-1"),
            getConfig().getInt("claims.maximum-residents", 32),
            chunk -> skyblockEnabled || regionPolicy.test(chunk)
        );
        parties = new InMemoryPartyService(
            getConfig().getInt("parties.maximum-size", 5),
            Duration.ofSeconds(getConfig().getLong("parties.invite-lifetime-seconds", 60L))
        );
        String marketSeason = getConfig().getString("season.id", "season-1");
        if (!marketSeason.matches("[a-zA-Z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Season id must be a safe market storage identifier");
        }
        market = new DurableMarketService(new java.io.File(getDataFolder(), "market/" + marketSeason + ".yml"), economy);
        auctions = new DurableAuctionService(new java.io.File(getDataFolder(), "auctions/" + marketSeason + ".yml"), market, economy,
            getConfig().getInt("economy.market-sales-tax-percent", 5));
        Set<String> categories = new HashSet<>(getConfig().getStringList("campaign.categories"));
        Map<String, Long> milestones = new HashMap<>();
        var milestoneSection = net.nerdsmp.paper.storage.ConfigurationDefaults.section(getConfig(), "campaign.milestones");
        if (milestoneSection != null) {
            for (String key : milestoneSection.getKeys(false)) {
                milestones.put(key, milestoneSection.getLong(key));
            }
        }
        Instant startsAt = Instant.now().minus(Duration.ofMinutes(1));
        Instant endsAt = startsAt.plus(Duration.ofDays(getConfig().getLong("campaign.duration-days", 14L)));
        String campaignId = getConfig().getString("campaign.id", "veil-awakening");
        campaigns = new InMemoryCampaignService(
            campaignId,
            getConfig().getString("campaign.title", "The Veil Awakens"),
            startsAt,
            endsAt,
            getConfig().getLong("campaign.daily-contribution-cap", 2500L),
            categories,
            milestones,
            new java.io.File(getDataFolder(), "campaign-state.yml"), marketSeason
        );
        serverProgression = new ServerProgressionService(this, campaigns);
        items = new PaperItemService(this);
        itemEngine = new net.nerdsmp.paper.engine.ItemEngine(this,items);
        gearSets = new GearSetService(this, items, player -> presentation == null
            ? ProgressionService.ProgressionSnapshot.empty() : presentation.progression(player));
        recipes = new CustomRecipeService(this, items);
        starterKit = new StarterKitService(this, items);
        sessions = new SessionRegistry();
        resources = new net.nerdsmp.paper.resource.ResourceProvenanceService(this, sessions);
        reputationContracts = new ReputationContractService(this, sessions, economy, reputation);
        engagement = new EngagementService(this, sessions, economy, reputationContracts, resources);
        jobs = new DiminishingJobService(
            economy,
            getConfig().getLong("jobs.full-rate-units", 200L),
            getConfig().getLong("jobs.half-rate-units", 200L),
            getConfig().getLong("jobs.quarter-rate-units", 400L), playerData, marketSeason
        );
        orders = new ServerOrderService(this, economy);
        identities = new IdentityProvider();
        presentation = new PlayerPresentationService(this);
        damageDisplays = new DamageDisplayService(this);
        settings = new PlayerSettingsService(this, playerData);
        mobs = new CustomMobManager(
            this,
            sessions,
            economy,
            parties,
            items,
            getConfig().getDouble("mobs.ambient-conversion-chance", 0.015D),
            serverProgression::worldEventsUnlocked
        );
        bossMechanics = new VanillaBossMechanicsService(this, mobs);
        mobHealthBars = new MobHealthBarService(this, mobs, settings);
        achievements = new AchievementService(this, sessions, economy, progression, mobs, playerData);
        enchanting = new ArcaneEnchantingService(this, items);
        forge = new ArcaneForgeService(this, items, gearSets, enchanting, mobs, presentation);
        merchantTrades = new MerchantTradeService(this, items, forge);
        skillListener = new SkillListener(this, sessions, progression, jobs, gearSets, forge, presentation, achievements, resources);
        arcane = new net.nerdsmp.paper.forge.arcane.ArcaneForge(this);
        arcaneMenus = new net.nerdsmp.paper.forge.arcane.ArcaneMenus(this, arcane);
        arcaneSignatures = new net.nerdsmp.paper.forge.arcane.ArcaneSignatureService(this, arcane);
        arcaneEffects = new net.nerdsmp.paper.forge.arcane.ArcaneEffectService(this, arcane);
        arcaneCommands = new net.nerdsmp.paper.forge.arcane.ArcaneCommands(this, arcane, arcaneMenus, arcaneSignatures);
        hoeHarvest = new net.nerdsmp.paper.gathering.HoeHarvestService(this, sessions, skillListener);
        oreSmelting = new net.nerdsmp.paper.gathering.OreSmeltingService(this, skillListener);
        autoSmelt = new net.nerdsmp.paper.gathering.AutoSmeltService(this, skillListener);
        autoSmelt.start();
        forestry = new net.nerdsmp.paper.gathering.ForestryService(this);
        forestry.start();
        alchemy = new AlchemyService(this, items, skillListener);
        collections = new net.nerdsmp.paper.activity.CollectionMasteryService(this, skillListener);
        fishing = new FishingService(this, skillListener);
        excavation = new ExcavationService(this, skillListener);
        identification = new IdentificationService(this, sessions, economy, items);
        bank = new BankService(this, sessions, economy);
        repairs = new RepairService(this, sessions, economy);
        menus = new MenuManager(this);
        homesteads = new net.nerdsmp.paper.claim.HomesteadController(this);
        marketController = new MarketController(this);
        auctionController = new AuctionController(this);
        pvp = new PvpManager(sessions, parties);
        graves = new GraveManager(
            this,
            sessions,
            Duration.ofHours(getConfig().getLong("graves.lifetime-hours", 24L))
        );
        npcs = new NpcManager(this, sessions, economy, reputation, progression, items, bank, identification, repairs,
            reputationContracts, excavation, fishing);
        staff = new StaffManager(this);
        social = new SocialManager(this, settings);
        chat = new ChannelChatManager(this, settings, social, presentation, sessions);
        trade = new TradeManager(this, settings, social);
        if (skyblockEnabled) skyblock = new net.nerdsmp.paper.skyblock.SkyblockController(this);
        if (skyblockEnabled) expeditions = new net.nerdsmp.paper.expedition.ExpeditionController(this);
        // Restore managed and legacy worlds before resolving saved travel destinations.
        travel = new TravelManager(this, settings, social);
        zones = new net.nerdsmp.paper.zone.ZoneStore(new java.io.File(getDataFolder(),"zones.yml"));
        zoneCommands = new net.nerdsmp.paper.zone.ZoneCommand(this,zones);
        areaWand = new net.nerdsmp.paper.zone.AreaWand(this);
        zoneCommands.wand(areaWand);
        zoneSpawner = new net.nerdsmp.paper.zone.ZoneSpawner(this,zones);
        if (skyblockEnabled) {
            digSites = new net.nerdsmp.paper.digsite.DigSiteService(this,
                new net.nerdsmp.paper.digsite.DigSiteStore(new java.io.File(getDataFolder(), "dig-sites.yml")));
        }
        digSiteCommands = new net.nerdsmp.paper.digsite.DigSiteCommand(this);
        guide = new PlayerGuideService(this, settings);
        rewardDrops = new RewardDropService(this, mobs, forge, items, sessions, economy);
        expansionLoot = new net.nerdsmp.paper.loot.ExpansionLootService(this);
        mobEngine = new net.nerdsmp.paper.engine.mob.MobEngine(this,mobs);
        rewardDrops.profiles().externalReferences(() -> mobEngine.all().stream()
            .map(net.nerdsmp.paper.engine.mob.MobBlueprint::lootProfile).filter(java.util.Objects::nonNull)
            .filter(id -> !id.isBlank()).collect(java.util.stream.Collectors.toUnmodifiableSet()));
        itemEngine.onChange(this::refreshAuthoredContent);
        contentEditor = new net.nerdsmp.paper.engine.ContentEditor(this,itemEngine,mobEngine);
        mobs.replacementLoot(rewardDrops::replacesDefaultLoot);
        mobLootCommands = new net.nerdsmp.paper.loot.profile.MobLootCommand(this);
        npcEngine = new net.nerdsmp.paper.studio.npc.NpcEngine(this);
        recipeEngine = new net.nerdsmp.paper.studio.RecipeEngine(this,recipes,itemEngine);
        studioUi = new net.nerdsmp.paper.studio.StudioUi(this);
        studioUi.beforeOpen(player -> {
            contentEditor.cancelEditing(player);
            if(recipeStudio!=null)recipeStudio.cancel(player);
        });
        lootStudio = new net.nerdsmp.paper.studio.LootStudio(this,studioUi);
        worldStudio = new net.nerdsmp.paper.studio.WorldStudio(this,studioUi);
        qaStudio = new net.nerdsmp.paper.studio.qa.QAStudio(this,studioUi);
        npcStudio = new net.nerdsmp.paper.studio.npc.NPCStudio(this,studioUi);
        recipeStudio = new net.nerdsmp.paper.studio.RecipeStudio(this,studioUi,recipeEngine);
        developerStudio = new net.nerdsmp.paper.studio.DeveloperStudio(this,studioUi);
        studioUi.setHome(developerStudio::open);
        watchdog = new WatchdogService(this);
        connections = new net.nerdsmp.paper.anticheat.ConnectionLog(this);

    }

    private void registerListeners() {
        PluginManager plugins = getServer().getPluginManager();
        plugins.registerEvents(identities, this);
        plugins.registerEvents(new SessionListener(
            this,
            identities,
            accounts,
            economy,
            sessions,
            starterKit,
            playerData,
            achievements,
            getConfig().getLong("economy.starter-marks", 250L)
        ), this);
        plugins.registerEvents(presentation, this);
        plugins.registerEvents(new net.nerdsmp.paper.presentation.ServerBrandingListener(this), this);
        plugins.registerEvents(resources, this);
        plugins.registerEvents(areaWand, this);
        plugins.registerEvents(contentEditor, this);
        plugins.registerEvents(studioUi, this);
        plugins.registerEvents(lootStudio, this);
        plugins.registerEvents(npcStudio, this);
        plugins.registerEvents(recipeStudio, this);
        if (digSites != null) plugins.registerEvents(digSites, this);
        if (skyblock != null) plugins.registerEvents(skyblock, this);
        plugins.registerEvents(damageDisplays, this);
        plugins.registerEvents(menus, this);
        plugins.registerEvents(homesteads, this);
        plugins.registerEvents(marketController, this);
        plugins.registerEvents(auctionController, this);
        registerResourcePackProvider(plugins);
        plugins.registerEvents(graves, this);
        plugins.registerEvents(pvp, this);
        plugins.registerEvents(mobs, this);
        plugins.registerEvents(mobHealthBars, this);
        plugins.registerEvents(achievements, this);
        plugins.registerEvents(forge, this);
        plugins.registerEvents(enchanting, this);
        plugins.registerEvents(arcaneMenus, this);
        plugins.registerEvents(arcaneSignatures, this);
        plugins.registerEvents(arcaneEffects, this);
        plugins.registerEvents(alchemy, this);
        plugins.registerEvents(fishing, this);
        plugins.registerEvents(excavation, this);
        plugins.registerEvents(identification, this);
        plugins.registerEvents(bank, this);
        plugins.registerEvents(repairs, this);
        plugins.registerEvents(gearSets, this);
        plugins.registerEvents(bossMechanics, this);
        plugins.registerEvents(recipes, this);
        plugins.registerEvents(engagement, this);
        plugins.registerEvents(reputationContracts, this);
        plugins.registerEvents(npcs, this);
        plugins.registerEvents(staff, this);
        plugins.registerEvents(social, this);
        plugins.registerEvents(chat, this);
        plugins.registerEvents(trade, this);
        plugins.registerEvents(travel, this);
        plugins.registerEvents(new net.nerdsmp.paper.item.VanillaTextureListener(this), this);
        plugins.registerEvents(zoneSpawner, this);
        plugins.registerEvents(new net.nerdsmp.paper.zone.ZoneProtection(zones), this);
        plugins.registerEvents(rewardDrops, this);
        plugins.registerEvents(expansionLoot, this);
        plugins.registerEvents(merchantTrades, this);
        plugins.registerEvents(serverProgression, this);
        plugins.registerEvents(watchdog, this);
        plugins.registerEvents(connections, this);
        plugins.registerEvents(staffPermissions, this);
        plugins.registerEvents(new ClaimProtectionListener(claims, sessions, graves,
            event -> digSites != null && digSites.mayHarvest(event)
                || expeditions != null && expeditions.mayHarvest(event),
            event -> zoneSpawner != null && zoneSpawner.zoneId(event.getEntity()).isPresent()
                && event.getDamageSource().getCausingEntity() instanceof org.bukkit.entity.Player attacker
                && zoneSpawner.canFight(attacker,event.getEntity())), this);
        plugins.registerEvents(skillListener, this);
        plugins.registerEvents(new net.nerdsmp.paper.gathering.LivestockService(this), this);
        plugins.registerEvents(hoeHarvest, this);
        plugins.registerEvents(oreSmelting, this);
        plugins.registerEvents(autoSmelt, this);
        plugins.registerEvents(forestry, this);
        if (expeditions != null) plugins.registerEvents(expeditions, this);
        presentation.start();
        damageDisplays.start();
        mobHealthBars.start();
        bossMechanics.start();
        recipes.start();
        recipeEngine.start();
        enchanting.start();
        arcaneEffects.start();
        reputationContracts.start();
        engagement.start();
        npcs.start();
        guide.start();
        serverProgression.start();
        marketController.start();
        staffPermissions.start();
        connections.start();
        watchdog.start();
        try { watchdogPanel = net.nerdsmp.paper.anticheat.WatchdogPanel.start(this); }
        catch (Exception failure) { getLogger().log(java.util.logging.Level.SEVERE, "Watchdog panel unavailable; no HTTP listener enabled", failure); }
        auctionController.start();
        payments = new net.nerdsmp.paper.economy.PaymentController(this); payments.start();
        leaderboards = new net.nerdsmp.paper.leaderboard.LeaderboardService(this); leaderboards.start();
        zoneSpawner.start();
        if (digSites != null) digSites.start();
        try { webApi = net.nerdsmp.paper.webapi.WebApi.start(this); }
        catch (Exception failure) { getLogger().log(java.util.logging.Level.SEVERE, "Web API unavailable; no port-9028 listener enabled", failure); }
        try { websiteRewards = net.nerdsmp.paper.webapi.WebsiteRewards.start(this); }
        catch (Exception failure) { getLogger().log(java.util.logging.Level.SEVERE, "Website Arcade rewards unavailable", failure); }
    }

    private void registerResourcePackProvider(PluginManager plugins) {
        if (Boolean.getBoolean("nerdsmp.vanillaTextures")) { getLogger().info("Vanilla textures enabled; no resource pack is required."); return; }
        String provider = getConfig().getString("resource-pack.provider", "none").toLowerCase(Locale.ROOT);
        if ("false".equalsIgnoreCase(System.getProperty("nerdsmp.vanillaTextures"))) provider = "nexo";
        if (provider.equals("nexo")) {
            var nexo = plugins.getPlugin("Nexo");
            if (nexo == null || !nexo.isEnabled()) {
                if (getConfig().getBoolean("resource-pack.require-provider-plugin", true)) {
                    throw new IllegalStateException(
                        "resource-pack.provider is nexo, but Nexo is not installed and enabled. "
                            + "Place a licensed Nexo 1.25+ jar in local-plugins and rerun the test server."
                    );
                }
                getLogger().warning("Nexo pack provider is selected but Nexo is unavailable; no pack will be dispatched.");
                return;
            }
            getLogger().info("Nexo " + nexo.getPluginMeta().getVersion()
                + " owns resource-pack generation, hosting, and dispatch. NerdSMP direct dispatch is disabled.");
            return;
        }
        if (!provider.equals("none")) {
            throw new IllegalArgumentException("Unsupported resource-pack.provider: " + provider);
        }
        getLogger().info("Vanilla textures enabled; no resource pack is required.");
    }

    private void registerCommands() {
        CommandRouter router = new CommandRouter(this);
        List<String> names = List.of(
            "permissions", "island", "marks", "pay", "leaderboard", "weblink", "cooking", "skills", "adventurer", "mastery", "jobs", "market", "ah", "store", "shops", "claim", "party", "partychat", "relics",
            "campaign", "grave", "duel", "cosmetics", "menu", "profile", "hud", "expedition", "zone", "digsite", "mobloot", "itemengine", "mobengine", "studio", "lootstudio", "worldstudio", "npcstudio", "recipestudio",
            "forge", "enchants", "recipes", "daily", "playtime", "npc", "reputation", "nerdadmin", "staff", "report",
            "settings", "social", "friends", "ignore", "unignore", "msg", "reply", "channel", "globalchat",
            "localchat", "tradechat", "staffchat", "trade", "spawn", "warp", "warps", "setwarp", "delwarp",
            "setspawn", "tpa", "tpahere", "tpaccept", "tpdeny", "tpcancel", "back", "quickstart", "tips"
            , "alchemy", "fishing", "excavation", "achievements", "watchdog", "identify", "bank", "repairall"
        );
        for (String name : names) {
            PluginCommand command = getCommand(name);
            if (command == null) {
                throw new IllegalStateException("Command missing from plugin.yml: " + name);
            }
            command.setExecutor(router);
            command.setTabCompleter(router);
        }
        getServer().getPluginManager().registerEvents(router, this);
        for (String name : List.of("arcane", "forgeadmin")) {
            PluginCommand command = getCommand(name);
            if (command == null) throw new IllegalStateException("Command missing from plugin.yml: " + name);
            command.setExecutor(arcaneCommands);
            command.setTabCompleter(arcaneCommands);
        }
    }

    private String resolveSetting(String path) {
        String value = getConfig().getString(path, "").trim();
        if (value.startsWith("${") && value.endsWith("}")) {
            String environmentName = value.substring(2, value.length() - 1);
            value = System.getenv(environmentName);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("Required environment variable is not set: " + environmentName);
            }
        }
        if (value.isBlank()) {
            throw new IllegalStateException("Required configuration value is blank: " + path);
        }
        return value;
    }

    public AccountService accounts() {
        return accounts;
    }

    public EconomyService economy() {
        return economy;
    }

    public ProgressionService progression() {
        return progression;
    }

    public ClaimService claims() {
        return claims;
    }

    public net.nerdsmp.paper.claim.HomesteadController homesteads() {
        return homesteads;
    }

    public CampaignService campaigns() {
        return campaigns;
    }

    public ServerProgressionService serverProgression() {
        return serverProgression;
    }

    public PartyService parties() {
        return parties;
    }

    public EntitlementService entitlements() {
        return entitlements;
    }

    public DurableMarketService market() {
        return market;
    }

    public MarketController marketController() {
        return marketController;
    }
    public DurableAuctionService auctions() { return auctions; }
    public AuctionController auctionController() { return auctionController; }

    public PaperItemService items() {
        return items;
    }

    public GearSetService gearSets() {
        return gearSets;
    }

    public CustomRecipeService recipes() {
        return recipes;
    }

    public EngagementService engagement() {
        return engagement;
    }

    public ReputationContractService reputationContracts() {
        return reputationContracts;
    }

    public StarterKitService starterKit() {
        return starterKit;
    }

    public SessionRegistry sessions() {
        return sessions;
    }

    public net.nerdsmp.paper.resource.ResourceProvenanceService resources() {
        return resources;
    }

    public GraveManager graves() {
        return graves;
    }

    public PvpManager pvp() {
        return pvp;
    }

    public IdentityProvider identities() {
        return identities;
    }

    public DiminishingJobService jobs() {
        return jobs;
    }

    public ServerOrderService orders() {
        return orders;
    }

    public CustomMobManager mobs() {
        return mobs;
    }

    public RewardDropService rewardDrops() { return rewardDrops; }

    public net.nerdsmp.paper.loot.profile.MobLootCommand mobLootCommands() { return mobLootCommands; }

    public net.nerdsmp.paper.engine.ItemEngine itemEngine() { return itemEngine; }
    public net.nerdsmp.paper.engine.mob.MobEngine mobEngine() { return mobEngine; }
    public net.nerdsmp.paper.engine.ContentEditor contentEditor() { return contentEditor; }
    public net.nerdsmp.paper.studio.StudioUi studioUi() { return studioUi; }
    public net.nerdsmp.paper.studio.DeveloperStudio developerStudio() { return developerStudio; }
    public net.nerdsmp.paper.studio.LootStudio lootStudio() { return lootStudio; }
    public net.nerdsmp.paper.studio.WorldStudio worldStudio() { return worldStudio; }
    public net.nerdsmp.paper.studio.qa.QAStudio qaStudio() { return qaStudio; }
    public net.nerdsmp.paper.studio.npc.NpcEngine npcEngine() { return npcEngine; }
    public net.nerdsmp.paper.studio.npc.NPCStudio npcStudio() { return npcStudio; }
    public net.nerdsmp.paper.studio.RecipeEngine recipeEngine() { return recipeEngine; }
    public net.nerdsmp.paper.studio.RecipeStudio recipeStudio() { return recipeStudio; }

    private void refreshAuthoredContent() {
        rewardDrops.refreshItemCatalog();
        if (recipeEngine != null) recipeEngine.refresh();
        // Refresh one online inventory per tick, not every player's tooltip in the publishing tick.
        contentRefreshQueue.clear();
        getServer().getOnlinePlayers().stream().limit(1024).forEach(player -> contentRefreshQueue.add(player.getUniqueId()));
        if(contentRefreshTask!=null || contentRefreshQueue.isEmpty()) return;
        contentRefreshTask=getServer().getScheduler().runTaskTimer(this,()-> {
            java.util.UUID id=contentRefreshQueue.poll();
            if(id!=null) {
                var player=getServer().getPlayer(id);
                if(player!=null && player.isOnline()) gearSets.refreshInventory(player);
            }
            if(contentRefreshQueue.isEmpty()) { contentRefreshTask.cancel(); contentRefreshTask=null; }
        },1L,1L);
    }

    public PlayerPresentationService presentation() {
        return presentation;
    }

    public MenuManager menus() {
        return menus;
    }

    public ArcaneForgeService forge() {
        return forge;
    }

    public ArcaneEnchantingService enchanting() {
        return enchanting;
    }

    public ReputationService reputation() {
        return reputation;
    }

    public NpcManager npcs() {
        return npcs;
    }

    public StaffManager staff() {
        return staff;
    }

    public PlayerSettingsService settings() {
        return settings;
    }

    public SocialManager social() {
        return social;
    }

    public ChannelChatManager chat() {
        return chat;
    }

    public TradeManager trade() {
        return trade;
    }

    public TravelManager travel() {
        return travel;
    }

    public PlayerGuideService guide() {
        return guide;
    }

    public AlchemyService alchemy() {
        return alchemy;
    }

    public FishingService fishing() {
        return fishing;
    }

    public ExcavationService excavation() {
        return excavation;
    }

    public net.nerdsmp.paper.activity.CollectionMasteryService collections() {
        return collections;
    }

    public AchievementService achievements() {
        return achievements;
    }

    public WatchdogService watchdog() {
        return watchdog;
    }

    public net.nerdsmp.paper.anticheat.ConnectionLog connections() {
        return connections;
    }

    public IdentificationService identification() {
        return identification;
    }

    public BankService bank() {
        return bank;
    }

    public RepairService repairs() {
        return repairs;
    }

    public PlayerDataStore playerData() {
        return playerData;
    }
}

package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.AdventurerProgression;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.achievement.AchievementService;
import net.nerdsmp.paper.forge.ArcaneForgeService;
import net.nerdsmp.paper.item.GearSetService;
import net.nerdsmp.paper.job.DiminishingJobService;
import net.nerdsmp.paper.presentation.VisualFormat;
import net.nerdsmp.paper.presentation.PlayerPresentationService;
import net.nerdsmp.paper.session.SessionRegistry;
import net.nerdsmp.paper.resource.HarvestClaims;
import net.nerdsmp.paper.resource.ResourceMaterials;
import net.nerdsmp.paper.resource.ResourceProvenanceService;
import net.nerdsmp.paper.gathering.CropReplanting;
import net.nerdsmp.paper.gathering.GatheringRhythm;
import net.nerdsmp.paper.gathering.FarmingExperience;
import net.nerdsmp.paper.gathering.HoeHarvestBreakEvent;
import net.nerdsmp.paper.gathering.HoeHarvestService;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class SkillListener implements Listener {
    private final JavaPlugin plugin;
    private final SessionRegistry sessions;
    private final ProgressionService progression;
    private final DiminishingJobService jobs;
    private final GearSetService gearSets;
    private final ArcaneForgeService forge;
    private final PlayerPresentationService presentation;
    private final AchievementService achievements;
    private final ResourceProvenanceService resources;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, BrewerUse> lastBrewer = new ConcurrentHashMap<>();
    private final Map<String, Double> experienceCarry = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastBuildingAt = new ConcurrentHashMap<>();
    private final NamespacedKey bossObjectiveKey;
    private final GatheringRhythm gatheringRhythm = new GatheringRhythm();
    private final Map<String, UUID> pendingReplants = new HashMap<>();

    public SkillListener(
        JavaPlugin plugin,
        SessionRegistry sessions,
        ProgressionService progression,
        DiminishingJobService jobs,
        GearSetService gearSets,
        ArcaneForgeService forge,
        PlayerPresentationService presentation,
        AchievementService achievements,
        ResourceProvenanceService resources
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.progression = progression;
        this.jobs = jobs;
        this.gearSets = gearSets;
        this.forge = forge;
        this.presentation = presentation;
        this.achievements = achievements;
        this.resources = resources;
        bossObjectiveKey = new NamespacedKey(plugin, "boss_objective");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String fingerprint = blockFingerprint(event.getBlock());
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        BuildingMaterialCatalog.Category category = BuildingMaterialCatalog.category(event.getBlock().getType());
        if (category == BuildingMaterialCatalog.Category.NONE) {
            return;
        }
        lastBuildingAt.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        long day = LocalDate.now(ZoneOffset.UTC).toEpochDay();
        award(event.getPlayer(), Skill.BUILDING, buildingExperience(category),
            "building:" + day + ":" + fingerprint);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
            || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        long lastPlacement = lastBuildingAt.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() - lastPlacement > 8_000L) {
            return;
        }
        double reduction = Math.min(90.0D, Math.max(0.0D, presentation.progression(player)
            .talentStat(Skill.BUILDING, "building-fall-reduction-percent")));
        if (reduction > 0.0D) {
            event.setDamage(event.getDamage() * (1.0D - reduction / 100.0D));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        boolean hoeHarvest = event instanceof HoeHarvestBreakEvent;
        SkillAward award = hoeHarvest ? new SkillAward(Skill.FARMING, FarmingExperience.baseXp(plugin, event.getBlock().getType()))
            : skillFor(event.getBlock());
        if (award != null) {
            var harvest = resources.claim(event, HarvestClaims.Channel.SKILLS);
            if (harvest.isEmpty()) return;
            Material forestryMaterial = event.getBlock().getType();
            if (award.skill() == Skill.FORESTRY && net.nerdsmp.paper.gathering.TreeHarvestBridge.capture(event,
                award.experience(), harvest.get().operationId(), () -> applyTalentDropBonus(event, Skill.FORESTRY,
                    forestryMaterial))) return;
            if (event instanceof HoeHarvestBreakEvent manual) manual.markRewardEligible();
            if (award.skill() != Skill.FARMING) forge.applyHarvestBonus(event);
            forge.rollGeode(event, award.skill());
            if (award.skill() == Skill.MINING && plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core
                && core.arcaneSignatures() != null) core.arcaneSignatures().onVerifiedMining(event);
            Player player = event.getPlayer();
            Material held = player.getInventory().getItemInMainHand().getType();
            boolean gatheringTool = award.skill() == Skill.FORESTRY && CropReplanting.axe(held)
                || award.skill() == Skill.FARMING && CropReplanting.hoe(held);
            String harvestedMaterial = event.getBlock().getType().name();
            UUID harvestedWorld = event.getBlock().getWorld().getUID();
            long harvestedAt = System.nanoTime() / 1_000_000L;
            AccountId harvestingAccount = sessions.account(player.getUniqueId()).orElse(null);
            // A later listener may still cancel the original break. Neither XP, job payout nor
            // rhythm progress may be published until all break listeners have returned.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.isCancelled() || !event.isDropItems() || !player.isOnline() || harvestingAccount == null
                    || Bukkit.getPlayer(player.getUniqueId()) != player
                    || event instanceof HoeHarvestBreakEvent manual && !manual.committed()
                    || !sessions.account(player.getUniqueId()).filter(harvestingAccount::equals).isPresent()) return;
                if (award.skill() == Skill.FARMING && !hoeHarvest) {
                    java.util.List<ItemStack> bonus = new java.util.ArrayList<>();
                    addFarmingHarvestBonuses(player, Material.valueOf(harvestedMaterial), bonus);
                    for (ItemStack item : bonus) event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), item);
                }
                if (award.skill() == Skill.FORESTRY) applyTalentDropBonus(event, award.skill(), Material.valueOf(harvestedMaterial));
                GatheringRhythm.Result rhythm = gatheringTool ? gatheringRhythm.record(player.getUniqueId(),
                    harvestedWorld, award.skill(), harvestedMaterial, harvest.get().operationId(), harvestedAt)
                    : GatheringRhythm.Result.NONE;
                award(player, award.skill(), award.experience(), harvest.get().operationId(), true, rhythm);
            });
            if (award.skill() == Skill.FARMING && !hoeHarvest) queueReplant(event);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player
            || event.getEntity().getPersistentDataContainer().has(bossObjectiveKey, PersistentDataType.BYTE)) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        award(killer, Skill.HUNTING, Math.max(xp("hunting-base", 8L), Math.round(event.getDroppedExp() * 1.5D)),
            "mob:" + event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        Skill skill = isAlchemy(result.getType()) ? Skill.ALCHEMY
            : isSmithing(result.getType()) ? Skill.SMITHING : null;
        if (skill == null) {
            return;
        }
        int crafted = Math.max(1, result.getAmount());
        long perItem = skill == Skill.ALCHEMY ? xp("alchemy-craft", 6L) : xp("smithing-craft", 8L);
        award(player, skill, Math.min(150L, crafted * perItem), "craft:" + UUID.randomUUID());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrewingStandOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
            || !(event.getView().getTopInventory() instanceof BrewerInventory inventory)
            || !(inventory.getHolder() instanceof BrewingStand stand)) {
            return;
        }
        lastBrewer.put(blockFingerprint(stand.getBlock()), new BrewerUse(player.getUniqueId(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        if (!(event.getContents().getHolder() instanceof BrewingStand stand)) {
            return;
        }
        String standId = blockFingerprint(stand.getBlock());
        BrewerUse use = lastBrewer.get(standId);
        if (use == null || System.currentTimeMillis() - use.usedAt() > 300_000L) {
            return;
        }
        Player player = Bukkit.getPlayer(use.playerId());
        if (player != null && player.isOnline()) {
            long potions = java.util.stream.IntStream.range(0, 3)
                .mapToObj(event.getContents()::getItem)
                .filter(item -> item != null && !item.getType().isAir())
                .count();
            award(player, Skill.ALCHEMY, Math.max(1L, potions) * xp("alchemy-brew", 18L),
                "brew:" + standId + ":" + System.nanoTime());
            trySaveReagent(player, event.getContents());
        }
    }

    private void award(Player player, Skill skill, long experience, String fingerprint) {
        award(player, skill, experience, fingerprint, true);
    }

    private void award(Player player, Skill skill, long experience, String fingerprint, boolean recordJobWork) {
        award(player, skill, experience, fingerprint, recordJobWork, GatheringRhythm.Result.NONE);
    }

    private void award(Player player, Skill skill, long experience, String fingerprint, boolean recordJobWork,
                       GatheringRhythm.Result rhythm) {
        award(player, skill, experience, fingerprint, recordJobWork, rhythm, null, false);
    }

    private void award(Player player, Skill skill, long experience, String fingerprint, boolean recordJobWork,
                       GatheringRhythm.Result rhythm, AccountId islandOwner, boolean explicitIsland) {
        if (recordJobWork && player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) return;
        AccountId accountId = sessions.account(player.getUniqueId()).orElse(null);
        if (accountId == null) {
            return;
        }
        net.nerdsmp.paper.skyblock.SkyblockController skyblock = plugin instanceof net.nerdsmp.paper.NerdSmpPlugin server ? server.skyblock() : null;
        if (explicitIsland && islandOwner != null && (skyblock == null || !skyblock.isIslandWorld(player.getWorld())
            || skyblock.islands().owned(islandOwner).filter(island -> island.member(accountId)).isEmpty())) return;
        GearSetService.GearProfile profile = gearSets.profile(player);
        ProgressionService.ProgressionSnapshot snapshot = presentation.progression(player);
        AccountId bonusIsland = skyblock == null ? null : explicitIsland ? islandOwner : skyblock.activityIsland(player);
        ActivityBonuses bonuses=ActivityBonuses.resolve(skill,profile.stats(),snapshot,
            bonusIsland==null?0:skyblock.development().progress(bonusIsland).skillBonus(skill),rhythm.bonusPercent());
        long adjustedExperience = adjustedExperience(accountId, skill, experience, bonuses.experiencePercent());
        double marksBonus = bonuses.marksPercent();
        if (recordJobWork) {
            jobs.recordWork(accountId, skill, jobUnits(skill), fingerprint, marksBonus);
        }
        AccountId activityIsland = skyblock == null || !recordJobWork ? null : explicitIsland ? islandOwner : skyblock.activityIsland(player);
        progression.awardExperience(accountId, skill, adjustedExperience, fingerprint).thenAccept(result -> {
            if (!result.awarded()) {
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (activityIsland != null) skyblock.development().record(accountId, activityIsland, experience);
                if (!player.isOnline() || Bukkit.getPlayer(player.getUniqueId()) != player
                    || !sessions.account(player.getUniqueId()).filter(accountId::equals).isPresent()) return;
                boolean adventurerLeveledUp = showProgress(player, skill, adjustedExperience, result, snapshot, rhythm);
                if (rhythm.milestone()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.22F,
                        1.0F + rhythm.chain() / 40.0F);
                    player.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.0D, 0),
                        3, 0.3D, 0.2D, 0.3D, 0.0D);
                }
                presentation.updateSkillProgress(player, skill, result.progress());
                if (result.leveledUp() || adventurerLeveledUp) {
                    presentation.refresh(player);
                }
            });
        });
    }

    public void awardActivity(Player player, Skill skill, long experience, String fingerprint) {
        award(player, skill, Math.max(1L, experience), fingerprint);
    }

    /** A validated catch can belong to the hook's co-op, even when the player is on its shore. */
    public void awardActivityAtIsland(Player player, Skill skill, long experience, String fingerprint, AccountId islandOwner) {
        award(player, skill, Math.max(1L, experience), fingerprint, true, GatheringRhythm.Result.NONE, islandOwner, true);
    }

    public void awardMilestone(Player player, Skill skill, long experience, String fingerprint) {
        award(player, skill, Math.max(1L, experience), fingerprint, false);
    }

    public record ProcessingContext(AccountId account, UUID player, UUID world, double experienceBonus) { }

    /** Snapshot Smithing bonuses before the mining tool's last durability point is consumed. */
    public ProcessingContext processingContext(Player player) {
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) return null;
        var progress = presentation.progression(player);
        var profile = gearSets.profile(player);
        var skyblock = plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core ? core.skyblock() : null;
        AccountId island = skyblock == null ? null : skyblock.activityIsland(player);
        var bonuses = ActivityBonuses.resolve(Skill.SMITHING, profile.stats(), progress,
            island == null ? 0 : skyblock.development().progress(island).skillBonus(Skill.SMITHING), 0);
        return new ProcessingContext(account, player.getUniqueId(), player.getWorld().getUID(), bonuses.experiencePercent());
    }

    /** Verified secondary processing grants XP only: mining already paid the job and island-work reward. */
    public void awardProcessing(Player player, ProcessingContext context, long experience, String fingerprint) {
        if (context == null || experience <= 0 || fingerprint == null || fingerprint.isBlank()
            || !context.player().equals(player.getUniqueId()) || !context.world().equals(player.getWorld().getUID())
            || !player.isOnline() || player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE
            || !sessions.account(context.player()).filter(context.account()::equals).isPresent()) return;
        long earned = adjustedExperience(context.account(), Skill.SMITHING, experience, context.experienceBonus());
        progression.awardExperience(context.account(), Skill.SMITHING, earned, fingerprint).thenAccept(result -> {
            if (!result.awarded() || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || Bukkit.getPlayer(context.player()) != player
                    || !sessions.account(context.player()).filter(context.account()::equals).isPresent()) return;
                boolean adventurerLeveled = showProgress(player, Skill.SMITHING, earned, result,
                    presentation.progression(player), GatheringRhythm.Result.NONE);
                presentation.updateSkillProgress(player, Skill.SMITHING, result.progress());
                if (result.leveledUp() || adventurerLeveled) presentation.refresh(player);
            });
        }).exceptionally(error -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Auto-smelt XP held for " + context.account(), error);
            return null;
        });
    }

    private boolean showProgress(
        Player player,
        Skill skill,
        long earned,
        ProgressionService.AwardResult result,
        ProgressionService.ProgressionSnapshot before,
        GatheringRhythm.Result rhythm
    ) {
        if (!player.isOnline()) {
            return false;
        }
        int level = result.progress().level();
        EnumMap<Skill, ProgressionService.SkillProgress> updatedSkills = new EnumMap<>(Skill.class);
        updatedSkills.putAll(before.skills());
        updatedSkills.put(skill, result.progress());
        AdventurerProgression.Progress previousAdventurer = before.adventurer();
        AdventurerProgression.Progress adventurer = AdventurerProgression.fromSkills(updatedSkills);
        achievements.updateProgression(player, updatedSkills);
        boolean adventurerLeveledUp = adventurer.level() > previousAdventurer.level();
        long skillFloor = ProgressionService.experienceForLevel(level);
        long skillCeiling = level >= ProgressionService.MAX_LEVEL
            ? skillFloor : ProgressionService.experienceForLevel(level + 1);
        long skillExperience = Math.max(0L, result.progress().experience() - skillFloor);
        long skillNeeded = Math.max(0L, skillCeiling - skillFloor);
        String progress = level >= ProgressionService.MAX_LEVEL
            ? "<gold>MAX</gold>"
            : VisualFormat.progressBar(skillExperience, skillNeeded, 8)
                + " <gray>" + VisualFormat.compact(skillExperience) + "/"
                + VisualFormat.compact(skillNeeded) + "</gray>";
        player.sendActionBar(miniMessage.deserialize("<aqua>" + skill.displayName() + " " + level
            + "</aqua> <white>+" + earned + " XP</white> <dark_gray>•</dark_gray> " + progress
            + (rhythm.bonusPercent() > 0 ? " <dark_gray>•</dark_gray> <green>"
                + (skill == Skill.FORESTRY ? "Timber" : "Harvest") + " Rhythm " + rhythm.chain()
                + " (+" + (int) rhythm.bonusPercent() + "%)</green>" : "")));
        if (adventurerLeveledUp) {
            boolean rankedUp = adventurer.rank() != previousAdventurer.rank();
            player.showTitle(Title.title(
                miniMessage.deserialize("<light_purple><bold>Adventurer " + adventurer.level()
                    + "</bold></light_purple>"),
                miniMessage.deserialize(rankedUp
                    ? "<gold>Rank earned: " + adventurer.rank().displayName() + "</gold>"
                    : "<gray>Your journey continues.</gray>"),
                Title.Times.times(Duration.ofMillis(250L), Duration.ofSeconds(2L), Duration.ofMillis(500L))
            ));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.1F);
        } else if (result.leveledUp()) {
            player.showTitle(Title.title(
                miniMessage.deserialize("<aqua><bold>" + skill.displayName() + " " + level + "</bold></aqua>"),
                miniMessage.deserialize(level == ProgressionService.MASTERY_LEVEL
                    ? "<gold>Mastery is now available.</gold>"
                    : "<gray>New skill level reached.</gray>"),
                Title.Times.times(Duration.ofMillis(250L), Duration.ofSeconds(2L), Duration.ofMillis(500L))
            ));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.15F);
        }
        return adventurerLeveledUp;
    }

    public record ForestryContext(AccountId account, UUID player, UUID world, double experienceBonus,
                                  double marksBonus, AccountId island) { }

    /** Capture equipment before axe wear/switching. Only the feller's committed native breaks consume this context. */
    public ForestryContext forestryContext(Player player) {
        AccountId account=sessions.account(player.getUniqueId()).orElse(null);
        if(account==null) return null;
        var profile=gearSets.profile(player); var progress=presentation.progression(player);
        net.nerdsmp.paper.skyblock.SkyblockController skyblock=plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core?core.skyblock():null;
        AccountId island=skyblock==null?null:skyblock.activityIsland(player);
        ActivityBonuses bonuses=ActivityBonuses.resolve(Skill.FORESTRY,profile.stats(),progress,
            island==null?0:skyblock.development().progress(island).skillBonus(Skill.FORESTRY),0);
        return new ForestryContext(account,player.getUniqueId(),player.getWorld().getUID(),bonuses.experiencePercent(),bonuses.marksPercent(),island);
    }

    /** One XP/job write per actual tree, including partial trees, instead of one player-file rewrite per log. */
    public void awardTree(Player player, ForestryContext context, String material, long baseExperience, int logs, String operation) {
        if(context==null||logs<1||logs>net.nerdsmp.paper.gathering.ForestryRules.MAX_LOGS||baseExperience<1
            ||!context.player().equals(player.getUniqueId())) return;
        var rhythm=gatheringRhythm.record(context.player(),context.world(),Skill.FORESTRY,material,operation,System.nanoTime()/1_000_000L);
        long earned=adjustedExperience(context.account(),Skill.FORESTRY,baseExperience,context.experienceBonus()+rhythm.bonusPercent());
        jobs.recordWork(context.account(),Skill.FORESTRY,logs,operation,context.marksBonus()).exceptionally(error->{
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Forestry work payment held for "+context.account(),error);return null;
        });
        progression.awardExperience(context.account(),Skill.FORESTRY,earned,operation).thenAccept(result->{
            if(!result.awarded()) return;
            if(!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(context.island()!=null&&plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core&&core.skyblock()!=null
                    &&core.skyblock().islands().owned(context.island()).filter(island->island.member(context.account())).isPresent()) {
                    // IslandDevelopment batches these in memory; retain its per-log cap and daily allowance.
                    long perLog=baseExperience/logs;
                    for(int index=0;index<logs;index++) core.skyblock().development().record(context.account(),context.island(),perLog);
                }
                if(!player.isOnline()||Bukkit.getPlayer(context.player())!=player
                    ||!sessions.account(context.player()).filter(context.account()::equals).isPresent()) return;
                var before=presentation.progression(player);
                showProgress(player,Skill.FORESTRY,earned,result,before,rhythm);
                presentation.updateSkillProgress(player,Skill.FORESTRY,result.progress());
                if(result.leveledUp()) presentation.refresh(player);
            });
        }).exceptionally(error->{plugin.getLogger().log(java.util.logging.Level.SEVERE,"Forestry XP held for "+context.account(),error);return null;});
    }

    private void queueReplant(BlockBreakEvent harvest) {
        Player player = harvest.getPlayer();
        CropReplanting.Plant plant = CropReplanting.plant(harvest.getBlock().getType());
        if (plant == null || !player.isSneaking() || player.getGameMode() != GameMode.SURVIVAL
            || !CropReplanting.hoe(player.getInventory().getItemInMainHand().getType())
            || presentation.progression(player).talentStat(Skill.FARMING, "extra-drop-percent") <= 0
            || pendingReplants.size() >= 128
            || CropReplanting.seedSlot(player.getInventory().getStorageContents(), plant.seed()) < 0) return;
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        if (account == null) return;
        Block original = harvest.getBlock();
        UUID worldId = original.getWorld().getUID();
        int x = original.getX(), y = original.getY(), z = original.getZ();
        String key = blockFingerprint(original);
        UUID token = UUID.randomUUID();
        if (pendingReplants.putIfAbsent(key, token) != null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!pendingReplants.remove(key, token) || harvest.isCancelled() || !harvest.isDropItems()
                || !player.isOnline() || player.getGameMode() != GameMode.SURVIVAL || !player.isSneaking()
                || !sessions.account(player.getUniqueId()).filter(account::equals).isPresent()
                || !player.getWorld().getUID().equals(worldId)
                || !player.getWorld().isChunkLoaded(x >> 4, z >> 4)
                || !CropReplanting.hoe(player.getInventory().getItemInMainHand().getType())
                || presentation.progression(player).talentStat(Skill.FARMING, "extra-drop-percent") <= 0) return;
            Block target = player.getWorld().getBlockAt(x, y, z);
            Block soil = target.getRelative(BlockFace.DOWN);
            if (!target.getType().isAir() || soil.getType() != plant.soil()
                || player.getLocation().distanceSquared(target.getLocation().add(0.5D, 0.5D, 0.5D)) > 49.0D
                || CropReplanting.seedSlot(player.getInventory().getStorageContents(), plant.seed()) < 0) return;
            BlockState before = target.getState();
            Ageable planted = (Ageable) plant.crop().createBlockData();
            planted.setAge(0);
            target.setBlockData(planted, false);
            try {
                BlockPlaceEvent placement = new BlockPlaceEvent(target, before, soil,
                    new ItemStack(plant.seed()), player, true, EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(placement);
                boolean unchanged = target.getBlockData().getAsString().equals(planted.getAsString());
                int slot = CropReplanting.seedSlot(player.getInventory().getStorageContents(), plant.seed());
                if (placement.isCancelled() || !placement.canBuild() || !unchanged || slot < 0) {
                    if (unchanged) before.update(true, false);
                    return;
                }
                ItemStack seed = player.getInventory().getItem(slot).clone();
                seed.setAmount(seed.getAmount() - 1);
                player.getInventory().setItem(slot, seed.getAmount() == 0 ? null : seed);
            } catch (RuntimeException failure) {
                if (target.getBlockData().getAsString().equals(planted.getAsString())) before.update(true, false);
                plugin.getLogger().warning("Replanting was stopped safely at " + key + ": " + failure.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        gatheringRhythm.clear(playerId);
        lastBuildingAt.remove(playerId);
        lastBrewer.entrySet().removeIf(entry -> entry.getValue().playerId().equals(playerId));
        sessions.account(playerId).ifPresent(account -> experienceCarry.keySet().removeIf(key -> key.startsWith(account + ":")));
    }

    private void applyTalentDropBonus(BlockBreakEvent event, Skill skill, Material harvested) {
        if (skill != Skill.FORESTRY && skill != Skill.FARMING) {
            return;
        }
        double chance = Math.max(0, presentation.progression(event.getPlayer()).talentStat(skill, "extra-drop-percent")
            + gearSets.profile(event.getPlayer()).stat("harvest-extra-percent") + islandYield(event.getPlayer(), skill)) / 100.0D;
        int bonus = Math.min(16, (int) Math.floor(chance))
            + (ThreadLocalRandom.current().nextDouble() < chance - Math.floor(chance) ? 1 : 0);
        Material material = skill == Skill.FORESTRY ? harvested : cropDrop(harvested);
        if (material != null && bonus > 0) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(material, bonus));
        }
    }

    private void trySaveReagent(Player player, BrewerInventory inventory) {
        ItemStack ingredient = inventory.getIngredient();
        double chance = presentation.progression(player).talentStat(Skill.ALCHEMY, "reagent-save-percent") / 100.0D;
        if (ingredient == null || ingredient.getType().isAir()
            || ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }
        ItemStack saved = ingredient.clone();
        saved.setAmount(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack current = inventory.getIngredient();
            if (current == null || current.getType().isAir()) {
                inventory.setIngredient(saved);
            } else if (current.isSimilar(saved) && current.getAmount() < current.getMaxStackSize()) {
                current.add(1);
            } else {
                player.getInventory().addItem(saved).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
            player.sendActionBar(miniMessage.deserialize("<light_purple>You saved a brewing reagent.</light_purple>"));
        });
    }

    private Material cropDrop(Material crop) {
        return switch (crop) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case NETHER_WART -> Material.NETHER_WART;
            case COCOA -> Material.COCOA_BEANS;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            case CAVE_VINES, CAVE_VINES_PLANT -> Material.GLOW_BERRIES;
            case TORCHFLOWER -> Material.TORCHFLOWER;
            case PITCHER_CROP -> Material.PITCHER_PLANT;
            case MELON -> Material.MELON_SLICE;
            case PUMPKIN, SUGAR_CANE, CACTUS, BAMBOO -> crop;
            default -> null;
        };
    }

    /** Bonuses join the cancellable harvested-item list; no loot is emitted before commit. */
    public void addFarmingHarvestBonuses(Player player, Material crop, java.util.List<ItemStack> drops) {
        Material produce = cropDrop(crop);
        if (produce == null) return;
        double chance = Math.max(0, gearSets.profile(player).stat("harvest-extra-percent")
            + presentation.progression(player).talentStat(Skill.FARMING, "extra-drop-percent") + islandYield(player, Skill.FARMING)) / 100.0D;
        int guaranteed = (int) Math.min(16, Math.floor(chance));
        int bonus = guaranteed + (ThreadLocalRandom.current().nextDouble() < chance - Math.floor(chance) ? 1 : 0);
        if (bonus > 0) drops.add(new ItemStack(produce, bonus));
    }
    private double islandYield(Player player, Skill skill) {
        if (!(plugin instanceof net.nerdsmp.paper.NerdSmpPlugin core) || core.skyblock() == null) return 0;
        AccountId owner = core.skyblock().activityIsland(player);
        if (owner == null) return 0;
        var progress = core.skyblock().development().progress(owner);
        return skill == Skill.FORESTRY ? 5 * progress.rank(net.nerdsmp.paper.skyblock.IslandTalent.TIMBERLAND)
            : skill == Skill.FARMING ? 4 * progress.rank(net.nerdsmp.paper.skyblock.IslandTalent.BOUNTIFUL) : 0;
    }
    public boolean canRewardFarmingHarvest(Block crop) { return resources.cropRewardEligible(crop); }

    /** An uncommitted attempt must not consume a proven growth cycle when the ripe crop was restored. */
    public void restoreAbortedHarvest(HoeHarvestBreakEvent event) {
        if (!event.committed() && event.rewardEligible()) resources.renewManaged(event.getBlock(), event.getBlock().getType());
    }

    private long jobUnits(Skill skill) {
        return switch (skill) {
            case FISHING -> 3L;
            case HUNTING -> 2L;
            default -> 1L;
        };
    }

    private long buildingExperience(BuildingMaterialCatalog.Category category) {
        return switch (category) {
            case STRUCTURE -> xp("building-structure", 3L);
            case DETAIL -> xp("building-detail", 5L);
            case LANDMARK -> xp("building-landmark", 8L);
            case NONE -> 0L;
        };
    }

    private synchronized long adjustedExperience(
        AccountId accountId,
        Skill skill,
        long baseExperience,
        double bonusPercent
    ) {
        String key = accountId + ":" + skill;
        double exact = baseExperience * (1.0D + Math.max(0.0D, bonusPercent) / 100.0D)
            + experienceCarry.getOrDefault(key, 0.0D);
        long awarded = Math.max(1L, (long) Math.floor(exact + 1.0E-9D));
        experienceCarry.put(key, Math.max(0.0D, exact - awarded));
        return awarded;
    }

    private SkillAward skillFor(Block block) {
        String name = block.getType().name();
        if (ResourceMaterials.ore(name)) {
            return new SkillAward(Skill.MINING, xp("mining-ore", 12L));
        }
        if (ResourceMaterials.log(name)) {
            return new SkillAward(Skill.FORESTRY, net.nerdsmp.paper.gathering.ForestryRules.experience(xp("forestry-log", 6L),
                plugin.getConfig().getDouble("forestry.experience-multiplier", 3.0D)));
        }
        if (block.getBlockData() instanceof Ageable ageable
            && CropHarvestRules.mature(name, ageable.getAge(), ageable.getMaximumAge())) {
            return new SkillAward(Skill.FARMING, FarmingExperience.baseXp(plugin, block.getType()));
        }
        if (HoeHarvestService.mature(block) || java.util.Set.of("MELON", "PUMPKIN", "SUGAR_CANE", "CACTUS", "BAMBOO").contains(name))
            return new SkillAward(Skill.FARMING, FarmingExperience.baseXp(plugin, block.getType()));
        if (ResourceMaterials.excavation(name)) {
            return new SkillAward(Skill.EXCAVATION, xp("excavation-block", 3L));
        }
        return null;
    }

    private boolean isAlchemy(Material material) {
        String name = material.name();
        return name.contains("POTION") || name.contains("FERMENTED") || name.contains("BLAZE_POWDER")
            || name.contains("GLISTERING_MELON");
    }

    private boolean isSmithing(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE")
            || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_HELMET")
            || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
            || material == Material.SHIELD || material == Material.ANVIL;
    }

    private long xp(String key, long fallback) {
        return Math.max(1L, plugin.getConfig().getLong("progression.xp." + key, fallback));
    }

    private String blockFingerprint(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private record SkillAward(Skill skill, long experience) {
    }

    private record BrewerUse(UUID playerId, long usedAt) {
    }
}

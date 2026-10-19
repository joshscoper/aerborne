package net.nerdsmp.paper.forge.arcane;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.service.ProgressionService;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.forge.ArcaneEnchantment;
import net.nerdsmp.paper.forge.GemType;
import net.nerdsmp.paper.item.PaperItemService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

/**
 * Coordinates Arcane Forge 2.0: research knowledge, Chosen Imbuement, Arcane Signature binding and
 * stabilization. Every operation that spends items follows the same transaction:
 *
 * <ol>
 *   <li><b>Validate</b> on the main thread against the live inventory and the expected item.</li>
 *   <li><b>Reserve</b> by building the complete new inventory on clones. Nothing is touched yet.</li>
 *   <li><b>Commit</b> the inventory in one call, then save any knowledge change. If the save fails,
 *       the original inventory is put back in the same tick and the player is told nothing was spent.</li>
 * </ol>
 *
 * Knowledge is saved through {@link net.nerdsmp.paper.storage.PlayerDataStore}, which publishes a
 * change only after its atomic write succeeds. Each research run carries a receipt id, so it can never
 * be applied twice, and its Enchanting XP uses the same id as its idempotent fingerprint.
 */
public final class ArcaneForge {
    static final String PROFILE_PATH = "arcane";
    private static final DateTimeFormatter HISTORY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

    private final NerdSmpPlugin plugin;
    private final ArcaneRegistry registry = ArcaneRegistry.defaults();
    private final AtomicReference<ArcaneSettings> settings = new AtomicReference<>(ArcaneSettings.defaults());
    private final ArcaneTelemetry telemetry = new ArcaneTelemetry();
    private final Set<UUID> busy = new HashSet<>();
    private final Set<AccountId> unavailableProfiles = new HashSet<>();
    private final java.util.function.Predicate<Player> currentPlayer;
    private final java.util.function.ToIntFunction<Player> levelOverride;

    public ArcaneForge(NerdSmpPlugin plugin) {
        this(plugin, player -> Bukkit.getPlayer(player.getUniqueId()) == player, null);
    }

    /** Disposable smoke fixtures supply stand-in players; production always uses the public constructor. */
    public static ArcaneForge fixture(NerdSmpPlugin plugin, java.util.function.Predicate<Player> currentPlayer,
                                      java.util.function.ToIntFunction<Player> levelOverride) {
        return new ArcaneForge(plugin, currentPlayer, levelOverride);
    }

    private ArcaneForge(NerdSmpPlugin plugin, java.util.function.Predicate<Player> currentPlayer,
                        java.util.function.ToIntFunction<Player> levelOverride) {
        this.plugin = plugin;
        this.currentPlayer = currentPlayer;
        this.levelOverride = levelOverride;
        List<String> errors = reload();
        if (!errors.isEmpty()) {
            plugin.getLogger().warning("arcane-forge config problems; defaults used for those keys: " + String.join("; ", errors));
        }
    }

    public ArcaneRegistry registry() {
        return registry;
    }

    public ArcaneSettings settings() {
        return settings.get();
    }

    public ArcaneTelemetry telemetry() {
        return telemetry;
    }

    /**
     * Builds a complete settings snapshot from the plugin config. A snapshot with errors is still
     * published for the keys that were valid (each bad key falls back to its default), so a typo never
     * switches a feature on. Operations already running keep the snapshot they started with.
     */
    public List<String> reload() {
        ArcaneSettings next = ArcaneSettings.from(plugin.getConfig());
        settings.set(next);
        ArcaneItemEffects.publish(next);
        if (!next.errors().isEmpty()) telemetry.add(ArcaneTelemetry.Metric.CONFIG_REJECTED, next.errors().size());
        return next.errors();
    }

    /** Publishes a fully validated snapshot in one atomic swap; {@code /forgeadmin reload} refuses any snapshot with errors. */
    public void apply(ArcaneSettings next) {
        if (!next.errors().isEmpty()) throw new IllegalArgumentException("Refusing to apply settings with errors");
        settings.set(next);
        ArcaneItemEffects.publish(next);
    }

    // ---------------------------------------------------------------------------------------------
    // Knowledge
    // ---------------------------------------------------------------------------------------------

    public Optional<AccountId> account(Player player) {
        return plugin.sessions().account(player.getUniqueId());
    }

    public ResearchProfile profile(AccountId account) {
        return plugin.playerData().read(account, yaml -> {
            if (yaml.isSet(PROFILE_PATH) && !yaml.isConfigurationSection(PROFILE_PATH)) {
                throw new IllegalStateException("Arcane knowledge must be a section; refusing to replace it");
            }
            return ResearchProfile.read(yaml.getConfigurationSection(PROFILE_PATH));
        });
    }

    /**
     * The player's knowledge, seeding it once from enchantments already on their gear. Seeding only
     * adds knowledge, runs once per account, and is saved before it is returned.
     */
    public Optional<ResearchProfile> profile(Player player) {
        AccountId account = account(player).orElse(null);
        if (account == null) return Optional.empty();
        try {
            ResearchProfile profile = profile(account);
            unavailableProfiles.remove(account);
            if (profile.seeded()) return Optional.of(profile);
            ResearchProfile seeded = profile.seed(ownedEnchantmentIds(player));
            return save(account, seeded) ? Optional.of(seeded) : Optional.empty();
        } catch (RuntimeException failure) {
            if (unavailableProfiles.add(account)) {
                plugin.getLogger().log(Level.SEVERE, "Arcane knowledge unavailable for " + account
                    + "; existing data was preserved and research is locked", failure);
            }
            return Optional.empty();
        }
    }

    private Set<String> ownedEnchantmentIds(Player player) {
        Set<String> ids = new HashSet<>();
        List<ItemStack> owned = new ArrayList<>(Arrays.asList(player.getInventory().getContents()));
        owned.addAll(Arrays.asList(player.getEnderChest().getContents()));
        for (ItemStack item : owned) {
            if (item == null || item.getType().isAir()) continue;
            plugin.items().enchantments(item).keySet().forEach(enchantment -> ids.add(enchantment.id()));
            plugin.items().arcaneSignatureId(item).flatMap(ArcaneSignature::fromId).ifPresent(signature -> ids.add(signature.id()));
        }
        return ids;
    }

    boolean save(AccountId account, ResearchProfile profile) {
        try {
            plugin.playerData().update(account, yaml -> {
                if (yaml.isSet(PROFILE_PATH) && !yaml.isConfigurationSection(PROFILE_PATH)) {
                    throw new IllegalStateException("Refusing to overwrite malformed Arcane knowledge");
                }
                var section = yaml.getConfigurationSection(PROFILE_PATH);
                ResearchProfile.read(section); // A future or malformed profile must never be downgraded.
                profile.write(section == null ? yaml.createSection(PROFILE_PATH) : section);
            });
            return true;
        } catch (RuntimeException failure) {
            telemetry.increment(ArcaneTelemetry.Metric.PROFILE_SAVE_FAILURES);
            plugin.getLogger().log(Level.SEVERE, "Could not save Arcane knowledge for " + account, failure);
            return false;
        }
    }

    /**
     * Called after the table or a tome binds an enchantment: binding teaches it, and binding it at its
     * top level counts toward mastery. A failure here is logged and never undoes the enchant.
     */
    public void recordBind(Player player, ArcaneEnchantment enchantment, int level) {
        if (!settings().enabled() || enchantment == null) return;
        try {
            AccountId account = account(player).orElse(null);
            if (account == null) return;
            ResearchProfile profile = profile(player).orElse(null);
            if (profile == null) return;
            ResearchProfile next = profile.recordBind(enchantment.id(), level, enchantment.maximumLevel());
            if (next != profile) save(account, next);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not record an Arcane bind for " + player.getUniqueId(), failure);
        }
    }

    /**
     * The level every Arcane gate reads: the higher of Enchanting and Smithing. Smithing gated
     * enchanting before Enchanting existed, so nobody loses an unlock they already had; new players
     * reach the same gates by enchanting.
     */
    public int arcaneLevel(Player player) {
        if (levelOverride != null) return levelOverride.applyAsInt(player);
        var skills = plugin.presentation().progression(player).skills();
        ProgressionService.SkillProgress none = new ProgressionService.SkillProgress(0L, 0);
        return Math.max(skills.getOrDefault(Skill.ENCHANTING, none).level(), skills.getOrDefault(Skill.SMITHING, none).level());
    }

    /** An Enchanting talent value in percentage points; 0 for stand-in fixture players. */
    public double talent(Player player, String stat) {
        if (levelOverride != null) return 0.0D;
        return Math.max(0.0D, plugin.presentation().progression(player).talentStat(Skill.ENCHANTING, stat));
    }

    /** True for the effect enchantments added by Forge 2.0, which only exist while their switch is on. */
    public static boolean effectEnchantment(ArcaneEnchantment enchantment) {
        return enchantment == ArcaneEnchantment.BLOODLETTER || enchantment == ArcaneEnchantment.VEINSEEKER
            || enchantment == ArcaneEnchantment.CHAIN_HARVEST;
    }

    /**
     * The enchantments table offers, tome drops and merchants may hand out. With every effect switch
     * off this is exactly the pre-2.0 list in the pre-2.0 order, so seeded rolls are unchanged.
     */
    public static ArcaneEnchantment[] obtainable(org.bukkit.plugin.Plugin owner) {
        ArcaneSettings settings = owner instanceof NerdSmpPlugin core && core.arcane() != null ? core.arcane().settings() : null;
        return Arrays.stream(ArcaneEnchantment.values())
            .filter(enchantment -> !effectEnchantment(enchantment)
                || settings != null && settings.enabled() && settings.effects().enabled(enchantment))
            .toArray(ArcaneEnchantment[]::new);
    }

    /** Where a table or tome bind came from, for its Enchanting XP. */
    public enum BindSource { TABLE, TOME }

    /** Records a bind from the table or a tome and pays its Enchanting XP. Never affects the enchant itself. */
    public void recordLegacyBind(Player player, ArcaneEnchantment enchantment, int level, BindSource source, int dust) {
        recordBind(player, enchantment, level);
        if (!settings().enabled()) return;
        ArcaneSettings.Experience xp = settings().experience();
        long earned = source == BindSource.TABLE ? (long) xp.tablePerDust() * Math.max(0, dust) : (long) xp.tomePerLevel() * level;
        awardEnchanting(player, earned, "arcane-" + source.name().toLowerCase(java.util.Locale.ROOT) + ":" + UUID.randomUUID());
    }

    /** True for a player in Survival with a loaded account, standing near an enchanting table. */
    public boolean atStation(Player player) {
        return validPlayer(player) && plugin.enchanting().atTable(player);
    }

    private boolean validPlayer(Player player) {
        return Bukkit.isPrimaryThread() && player != null && player.isOnline() && !player.isDead()
            && player.getGameMode() == GameMode.SURVIVAL && account(player).isPresent()
            && currentPlayer.test(player);
    }

    // ---------------------------------------------------------------------------------------------
    // Item views
    // ---------------------------------------------------------------------------------------------

    /** Everything the Forge shows about one item, read from server-side data only (never lore). */
    public record ItemView(boolean gear, String rarity, int itemLevel, int qualityTier, int capacity, int load,
                           Map<ArcaneEnchantment, Integer> enchantments, List<String> preserved,
                           ArcaneSignature signature, String unknownSignature, int instability,
                           ArcaneCorruption corruption, String unknownCorruption, ArcaneAspect attunement) {
        public boolean corrupted() {
            return corruption != null || unknownCorruption != null;
        }

        public boolean grandfathered() {
            return ArcaneCapacity.grandfathered(capacity, load);
        }
    }

    public ItemView view(ItemStack item) {
        PaperItemService items = plugin.items();
        var gear = items.gear(item);
        if (gear.isEmpty()) {
            return new ItemView(false, null, 0, 0, 0, 0, Map.of(), List.of(), null, null, 0, null, null, null);
        }
        ArcaneSettings.Capacity rules = settings().capacity();
        String rawSignature = items.arcaneSignatureId(item).orElse(null);
        ArcaneSignature signature = rawSignature == null ? null : ArcaneSignature.fromId(rawSignature).orElse(null);
        Map<ArcaneEnchantment, Integer> enchantments = items.enchantments(item);
        int quality = items.quality(item).tier();
        int level = items.itemLevel(item);
        String rawCorruption = items.arcaneCorruptionId(item).orElse(null);
        ArcaneCorruption corruption = rawCorruption == null ? null : ArcaneCorruption.fromId(rawCorruption).orElse(null);
        int load = ArcaneCapacity.load(rules, registry, enchantments, signature)
            + (corruption == null ? 0 : settings().corruption().capacityCost());
        return new ItemView(true, gear.get().rarity(), level, quality,
            ArcaneCapacity.capacity(rules, gear.get().rarity(), level, quality), load,
            enchantments, items.preservedEnchantmentEntries(item), signature,
            signature == null ? rawSignature : null, items.arcaneInstability(item),
            corruption, corruption == null ? rawCorruption : null, items.arcaneAttunement(item).orElse(null));
    }

    // ---------------------------------------------------------------------------------------------
    // Chosen Imbuement
    // ---------------------------------------------------------------------------------------------

    public enum Outcome {
        APPLIED,
        DISABLED,
        NOT_AT_STATION,
        BUSY,
        ITEM_CHANGED,
        NOT_GEAR,
        UNIDENTIFIED,
        UNDISCOVERED,
        INCOMPATIBLE,
        NO_UPGRADE,
        NEEDS_LEVEL,
        UNSTABLE,
        CORRUPTED,
        OVER_CAPACITY,
        MISSING_DUST,
        MISSING_GEMS,
        SAVE_FAILED,
        RECOVERY_REQUIRED,
        FAILED
    }

    public ImbueRules.Quote imbueQuote(Player player, ResearchProfile profile, ItemStack item, ArcaneDefinition definition, int target) {
        ArcaneEnchantment enchantment = definition.enchantment();
        int current = plugin.items().enchantmentLevel(item, enchantment);
        boolean compatible = plugin.items().supportsEnchantment(item, enchantment);
        ResearchProfile.State state = profile.state(definition.id(), settings().research().masteryBinds());
        ImbueRules.Quote quote = ImbueRules.quote(settings().imbuement(), definition, state.known(),
            state == ResearchProfile.State.MASTERED, compatible, current, target, arcaneLevel(player));
        double thrift = Math.min(50.0D, talent(player, "imbue-dust-discount-percent"));
        if (thrift <= 0.0D) return quote;
        int dust = (int) Math.ceil(quote.dust() * (100.0D - thrift) / 100.0D);
        return new ImbueRules.Quote(quote.status(), quote.fromLevel(), quote.toLevel(), dust, quote.requiredLevel(),
            quote.highestAllowedLevel(), true);
    }

    /** Raises one discovered enchantment on the held item to an exact level for a fixed Dust price. */
    public Outcome imbue(Player player, ItemStack expected, int heldSlot, ArcaneDefinition definition, int target) {
        ArcaneSettings snapshot = settings();
        if (!snapshot.enabled() || !snapshot.imbuement().enabled() || definition.isSignature()
            || effectEnchantment(definition.enchantment()) && !snapshot.effects().enabled(definition.enchantment())) return Outcome.DISABLED;
        telemetry.increment(ArcaneTelemetry.Metric.IMBUE_STARTED);
        Outcome outcome = guarded(player, () -> {
            if (!atStation(player)) return Outcome.NOT_AT_STATION;
            ItemStack held = heldIfUnchanged(player, expected, heldSlot);
            if (held == null) return Outcome.ITEM_CHANGED;
            Outcome item = gearCheck(held);
            if (item != null) return item;
            if (plugin.items().arcaneInstability(held) > 0) return Outcome.UNSTABLE;
            if (plugin.items().arcaneCorruptionId(held).isPresent()) return Outcome.CORRUPTED;
            ResearchProfile profile = profile(player).orElse(null);
            if (profile == null) return Outcome.FAILED;
            ImbueRules.Quote quote = imbueQuote(player, profile, held, definition, target);
            Outcome refused = switch (quote.status()) {
                case READY -> null;
                case UNDISCOVERED -> Outcome.UNDISCOVERED;
                case INCOMPATIBLE -> Outcome.INCOMPATIBLE;
                case NO_UPGRADE -> Outcome.NO_UPGRADE;
                case NEEDS_LEVEL -> Outcome.NEEDS_LEVEL;
            };
            if (refused != null) return refused;
            ItemStack[] original = cloneContents(player.getInventory().getStorageContents());
            ItemStack[] updated = cloneContents(original);
            ItemStack result = updated[heldSlot];
            if (plugin.items().enchant(result, definition.enchantment(), target) != PaperItemService.UpgradeResult.APPLIED) {
                return Outcome.ITEM_CHANGED;
            }
            if (!takeDust(updated, quote.dust(), heldSlot)) return Outcome.MISSING_DUST;
            ResearchProfile next = profile.recordBind(definition.id(), target, definition.maximumLevel())
                .log(historyLine("Imbued " + definition.displayName() + " " + roman(target)));
            Outcome committed = commit(player, original, updated, account(player).orElseThrow(), next);
            if (committed != Outcome.APPLIED) return committed;
            awardEnchanting(player, (long) snapshot.experience().imbuePerDust() * quote.dust(), "arcane-imbue:" + UUID.randomUUID());
            finish(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.15F);
            return Outcome.APPLIED;
        });
        telemetry.increment(outcome == Outcome.APPLIED ? ArcaneTelemetry.Metric.IMBUE_COMPLETED
            : outcome == Outcome.FAILED || outcome == Outcome.SAVE_FAILED ? ArcaneTelemetry.Metric.IMBUE_FAILED
            : ArcaneTelemetry.Metric.IMBUE_REJECTED);
        return outcome;
    }

    // ---------------------------------------------------------------------------------------------
    // Arcane Signatures
    // ---------------------------------------------------------------------------------------------

    public enum SignatureStatus { READY, DISABLED, UNDISCOVERED, INCOMPATIBLE, ALREADY_BOUND, NEEDS_LEVEL, UNSTABLE, CORRUPTED, OVER_CAPACITY }

    public record SignatureQuote(SignatureStatus status, ArcaneSignature signature, ArcaneSignature replaced,
                                 ArcaneCapacity.Evaluation capacity, int dust, int gemTier, int requiredLevel) {
        public boolean ready() {
            return status == SignatureStatus.READY;
        }
    }

    public SignatureQuote signatureQuote(Player player, ResearchProfile profile, ItemStack item, ArcaneSignature signature) {
        ArcaneSettings snapshot = settings();
        ItemView view = view(item);
        ArcaneSettings.Capacity capacityRules = snapshot.capacity();
        int cost = ArcaneCapacity.cost(capacityRules, registry.of(signature), 1);
        int removed = view.signature() == null ? 0 : ArcaneCapacity.cost(capacityRules, registry.of(view.signature()), 1);
        int arcane = arcaneLevel(player);
        boolean overcharge = snapshot.overcharge().enabled() && arcane >= snapshot.overcharge().requiredLevel();
        ArcaneCapacity.Evaluation evaluation = ArcaneCapacity.evaluate(view.capacity(), view.load(), removed, cost,
            overcharge, snapshot.overcharge().maximumPercent());
        int required = snapshot.research().signatureLevel();
        SignatureStatus status;
        if (!snapshot.enabled() || !snapshot.imbuement().enabled() || !definitionActive(registry.of(signature), snapshot)) status = SignatureStatus.DISABLED;
        else if (!profile.known(signature.id())) status = SignatureStatus.UNDISCOVERED;
        else if (!view.gear() || !signature.supports(item.getType())) status = SignatureStatus.INCOMPATIBLE;
        else if (view.signature() == signature) status = SignatureStatus.ALREADY_BOUND;
        else if (arcane < required) status = SignatureStatus.NEEDS_LEVEL;
        else if (view.instability() > 0) status = SignatureStatus.UNSTABLE;
        else if (view.corrupted()) status = SignatureStatus.CORRUPTED;
        else if (!evaluation.allowed()) status = SignatureStatus.OVER_CAPACITY;
        else status = SignatureStatus.READY;
        return new SignatureQuote(status, signature, view.signature(), evaluation, snapshot.imbuement().signatureDust(),
            snapshot.imbuement().signatureGemTier(), required);
    }

    /** Binds a discovered Arcane Signature to the held item, replacing any signature it had. */
    public Outcome bindSignature(Player player, ItemStack expected, int heldSlot, ArcaneSignature signature) {
        telemetry.increment(ArcaneTelemetry.Metric.IMBUE_STARTED);
        Outcome outcome = guarded(player, () -> {
            if (!atStation(player)) return Outcome.NOT_AT_STATION;
            ItemStack held = heldIfUnchanged(player, expected, heldSlot);
            if (held == null) return Outcome.ITEM_CHANGED;
            Outcome item = gearCheck(held);
            if (item != null) return item;
            ResearchProfile profile = profile(player).orElse(null);
            if (profile == null) return Outcome.FAILED;
            SignatureQuote quote = signatureQuote(player, profile, held, signature);
            Outcome refused = switch (quote.status()) {
                case READY -> null;
                case DISABLED -> Outcome.DISABLED;
                case UNDISCOVERED -> Outcome.UNDISCOVERED;
                case INCOMPATIBLE, ALREADY_BOUND -> Outcome.INCOMPATIBLE;
                case NEEDS_LEVEL -> Outcome.NEEDS_LEVEL;
                case UNSTABLE -> Outcome.UNSTABLE;
                case CORRUPTED -> Outcome.CORRUPTED;
                case OVER_CAPACITY -> Outcome.OVER_CAPACITY;
            };
            if (refused != null) return refused;
            ItemStack[] original = cloneContents(player.getInventory().getStorageContents());
            ItemStack[] updated = cloneContents(original);
            if (plugin.items().bindArcaneSignature(updated[heldSlot], signature, quote.capacity().overcharge())
                != PaperItemService.UpgradeResult.APPLIED) return Outcome.ITEM_CHANGED;
            if (!takeGems(updated, signature.aspects(), quote.gemTier(), heldSlot)) return Outcome.MISSING_GEMS;
            if (!takeDust(updated, quote.dust(), heldSlot)) return Outcome.MISSING_DUST;
            ResearchProfile next = profile.recordBind(signature.id(), 1, 1).log(historyLine("Bound " + signature.displayName()));
            Outcome committed = commit(player, original, updated, account(player).orElseThrow(), next);
            if (committed != Outcome.APPLIED) return committed;
            telemetry.increment(ArcaneTelemetry.Metric.SIGNATURES_BOUND);
            awardEnchanting(player, settings().experience().signatureBind(), "arcane-signature:" + UUID.randomUUID());
            finish(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.2F);
            return Outcome.APPLIED;
        });
        telemetry.increment(outcome == Outcome.APPLIED ? ArcaneTelemetry.Metric.IMBUE_COMPLETED
            : outcome == Outcome.FAILED || outcome == Outcome.SAVE_FAILED ? ArcaneTelemetry.Metric.IMBUE_FAILED
            : ArcaneTelemetry.Metric.IMBUE_REJECTED);
        return outcome;
    }

    public int stabilizeCost(ItemStack item) {
        return plugin.items().arcaneInstability(item) * settings().overcharge().stabilizeDustPerPoint();
    }

    /** Removes all instability from the held item for a fixed Dust price. Works even when overcharge is later disabled. */
    public Outcome stabilize(Player player, ItemStack expected, int heldSlot) {
        if (!settings().enabled()) return Outcome.DISABLED;
        return guarded(player, () -> {
            if (!atStation(player)) return Outcome.NOT_AT_STATION;
            ItemStack held = heldIfUnchanged(player, expected, heldSlot);
            if (held == null) return Outcome.ITEM_CHANGED;
            if (plugin.items().arcaneInstability(held) <= 0) return Outcome.NO_UPGRADE;
            int cost = stabilizeCost(held);
            ItemStack[] original = cloneContents(player.getInventory().getStorageContents());
            ItemStack[] updated = cloneContents(original);
            if (!plugin.items().stabilize(updated[heldSlot])) return Outcome.ITEM_CHANGED;
            if (!takeDust(updated, cost, heldSlot)) return Outcome.MISSING_DUST;
            Outcome committed = commit(player, original, updated, null, null);
            if (committed == Outcome.APPLIED) {
                telemetry.increment(ArcaneTelemetry.Metric.STABILIZED);
                finish(player, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.3F);
            }
            return committed;
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Corruption and cleansing
    // ---------------------------------------------------------------------------------------------

    public enum CorruptionStatus { READY, DISABLED, NEEDS_LEVEL, INCOMPATIBLE, ALREADY_CORRUPTED, UNSTABLE, OVER_CAPACITY }

    public record CorruptionQuote(CorruptionStatus status, ArcaneCorruption corruption, ArcaneCapacity.Evaluation capacity,
                                  int dust, int requiredLevel) {
        public boolean ready() {
            return status == CorruptionStatus.READY;
        }
    }

    public CorruptionQuote corruptionQuote(Player player, ItemStack item, ArcaneCorruption corruption) {
        ArcaneSettings snapshot = settings();
        ItemView view = view(item);
        ArcaneSettings.Corruption rules = snapshot.corruption();
        ArcaneCapacity.Evaluation capacity = ArcaneCapacity.evaluate(view.capacity(), view.load(), 0, rules.capacityCost(), false, 0);
        CorruptionStatus status;
        if (!snapshot.enabled() || !rules.enabled()) status = CorruptionStatus.DISABLED;
        else if (!view.gear() || !corruption.supports(item.getType())) status = CorruptionStatus.INCOMPATIBLE;
        else if (view.corrupted()) status = CorruptionStatus.ALREADY_CORRUPTED;
        else if (arcaneLevel(player) < rules.requiredLevel()) status = CorruptionStatus.NEEDS_LEVEL;
        else if (view.instability() > 0) status = CorruptionStatus.UNSTABLE;
        else if (!capacity.allowed()) status = CorruptionStatus.OVER_CAPACITY;
        else status = CorruptionStatus.READY;
        return new CorruptionQuote(status, corruption, capacity, rules.dust(), rules.requiredLevel());
    }

    /** Corrupts the held item with exactly the chosen corruption, for a fixed Dust price. */
    public Outcome corrupt(Player player, ItemStack expected, int heldSlot, ArcaneCorruption corruption) {
        return guarded(player, () -> {
            if (!atStation(player)) return Outcome.NOT_AT_STATION;
            ItemStack held = heldIfUnchanged(player, expected, heldSlot);
            if (held == null) return Outcome.ITEM_CHANGED;
            Outcome item = gearCheck(held);
            if (item != null) return item;
            CorruptionQuote quote = corruptionQuote(player, held, corruption);
            Outcome refused = switch (quote.status()) {
                case READY -> null;
                case DISABLED -> Outcome.DISABLED;
                case NEEDS_LEVEL -> Outcome.NEEDS_LEVEL;
                case INCOMPATIBLE -> Outcome.INCOMPATIBLE;
                case ALREADY_CORRUPTED -> Outcome.CORRUPTED;
                case UNSTABLE -> Outcome.UNSTABLE;
                case OVER_CAPACITY -> Outcome.OVER_CAPACITY;
            };
            if (refused != null) return refused;
            ResearchProfile profile = profile(player).orElse(null);
            if (profile == null) return Outcome.FAILED;
            ItemStack[] original = cloneContents(player.getInventory().getStorageContents());
            ItemStack[] updated = cloneContents(original);
            if (plugin.items().corrupt(updated[heldSlot], corruption) != PaperItemService.UpgradeResult.APPLIED) return Outcome.ITEM_CHANGED;
            if (!takeDust(updated, quote.dust(), heldSlot)) return Outcome.MISSING_DUST;
            Outcome committed = commit(player, original, updated, account(player).orElseThrow(),
                profile.log(historyLine("Corrupted with " + corruption.displayName())));
            if (committed != Outcome.APPLIED) return committed;
            telemetry.increment(ArcaneTelemetry.Metric.CORRUPTED);
            awardEnchanting(player, settings().experience().corruption(), "arcane-corruption:" + UUID.randomUUID());
            finish(player, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.4F);
            return Outcome.APPLIED;
        });
    }

    public int cleanseCost() {
        return settings().corruption().cleanseDust();
    }

    /**
     * Removes the held item's corruption, always, for a fixed Dust price. Works while corruption is
     * switched off, and for corruptions this build does not recognise, so no player is ever stuck.
     */
    public Outcome cleanse(Player player, ItemStack expected, int heldSlot) {
        if (!settings().enabled()) return Outcome.DISABLED;
        return guarded(player, () -> {
            if (!atStation(player)) return Outcome.NOT_AT_STATION;
            ItemStack held = heldIfUnchanged(player, expected, heldSlot);
            if (held == null) return Outcome.ITEM_CHANGED;
            if (plugin.items().arcaneCorruptionId(held).isEmpty()) return Outcome.NO_UPGRADE;
            ItemStack[] original = cloneContents(player.getInventory().getStorageContents());
            ItemStack[] updated = cloneContents(original);
            if (!plugin.items().cleanse(updated[heldSlot])) return Outcome.ITEM_CHANGED;
            if (!takeDust(updated, cleanseCost(), heldSlot)) return Outcome.MISSING_DUST;
            Outcome committed = commit(player, original, updated, null, null);
            if (committed == Outcome.APPLIED) {
                telemetry.increment(ArcaneTelemetry.Metric.CLEANSED);
                finish(player, Sound.BLOCK_BEACON_DEACTIVATE, 1.4F);
            }
            return committed;
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Attunement
    // ---------------------------------------------------------------------------------------------

    public enum AttunementStatus { READY, DISABLED, NEEDS_LEVEL, CORRUPTED, NO_MATCH, ALREADY_ATTUNED }

    /** The Aspects an item can be attuned to: those of its enchantments and its signature. */
    public java.util.Set<ArcaneAspect> attunableAspects(ItemStack item) {
        java.util.Set<ArcaneAspect> aspects = EnumSet.noneOf(ArcaneAspect.class);
        ItemView view = view(item);
        view.enchantments().keySet().forEach(enchantment -> aspects.addAll(registry.of(enchantment).aspects()));
        if (view.signature() != null) aspects.addAll(view.signature().aspects());
        return aspects;
    }

    public AttunementStatus attunementStatus(Player player, ItemStack item, ArcaneAspect aspect) {
        ArcaneSettings snapshot = settings();
        ItemView view = view(item);
        if (!snapshot.enabled() || !snapshot.attunement().enabled()) return AttunementStatus.DISABLED;
        if (arcaneLevel(player) < snapshot.attunement().requiredLevel()) return AttunementStatus.NEEDS_LEVEL;
        if (view.corrupted()) return AttunementStatus.CORRUPTED;
        if (!attunableAspects(item).contains(aspect)) return AttunementStatus.NO_MATCH;
        if (view.attunement() == aspect) return AttunementStatus.ALREADY_ATTUNED;
        return AttunementStatus.READY;
    }

    /** Attunes the held item to one Aspect, replacing any earlier attunement, for a fixed Dust price. */
    public Outcome attune(Player player, ItemStack expected, int heldSlot, ArcaneAspect aspect) {
        return guarded(player, () -> {
            if (!atStation(player)) return Outcome.NOT_AT_STATION;
            ItemStack held = heldIfUnchanged(player, expected, heldSlot);
            if (held == null) return Outcome.ITEM_CHANGED;
            Outcome item = gearCheck(held);
            if (item != null) return item;
            Outcome refused = switch (attunementStatus(player, held, aspect)) {
                case READY -> null;
                case DISABLED -> Outcome.DISABLED;
                case NEEDS_LEVEL -> Outcome.NEEDS_LEVEL;
                case CORRUPTED -> Outcome.CORRUPTED;
                case NO_MATCH -> Outcome.INCOMPATIBLE;
                case ALREADY_ATTUNED -> Outcome.NO_UPGRADE;
            };
            if (refused != null) return refused;
            ItemStack[] original = cloneContents(player.getInventory().getStorageContents());
            ItemStack[] updated = cloneContents(original);
            if (plugin.items().attune(updated[heldSlot], aspect) != PaperItemService.UpgradeResult.APPLIED) return Outcome.ITEM_CHANGED;
            if (!takeDust(updated, settings().attunement().dust(), heldSlot)) return Outcome.MISSING_DUST;
            Outcome committed = commit(player, original, updated, null, null);
            if (committed == Outcome.APPLIED) {
                telemetry.increment(ArcaneTelemetry.Metric.ATTUNED);
                finish(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6F);
            }
            return committed;
        });
    }

    /** Removes an attunement for free. Always allowed, so the choice is never a trap. */
    public Outcome clearAttunement(Player player, ItemStack expected, int heldSlot) {
        if (!settings().enabled()) return Outcome.DISABLED;
        return guarded(player, () -> {
            if (!atStation(player)) return Outcome.NOT_AT_STATION;
            ItemStack held = heldIfUnchanged(player, expected, heldSlot);
            if (held == null) return Outcome.ITEM_CHANGED;
            if (plugin.items().arcaneAttunementId(held).isEmpty()) return Outcome.NO_UPGRADE;
            ItemStack[] original = cloneContents(player.getInventory().getStorageContents());
            ItemStack[] updated = cloneContents(original);
            if (plugin.items().attune(updated[heldSlot], null) != PaperItemService.UpgradeResult.APPLIED) return Outcome.ITEM_CHANGED;
            return commit(player, original, updated, null, null);
        });
    }

    /**
     * A signature's real cooldown: overcharge lengthens it; the Signature Focus talent and an
     * attunement to one of the signature's Aspects shorten it. Never below a quarter of the base.
     */
    public long signatureCooldownMillis(Player player, ItemStack item, ArcaneSignature signature, long baseSeconds) {
        ArcaneSettings snapshot = settings();
        long cooldown = SignatureRules.cooldownMillis(baseSeconds, plugin.items().arcaneInstability(item),
            snapshot.overcharge().cooldownPercentPerPoint());
        double reduction = Math.min(50.0D, talent(player, "signature-cooldown-reduction-percent"));
        if (ArcaneItemEffects.attunementActive() && plugin.items().arcaneAttunement(item).filter(signature.aspects()::contains).isPresent()) {
            reduction += snapshot.attunement().signatureCooldownPercent();
        }
        long reduced = Math.round(cooldown * (100.0D - Math.min(75.0D, reduction)) / 100.0D);
        return Math.max(baseSeconds * 250L, reduced);
    }

    // ---------------------------------------------------------------------------------------------
    // Research
    // ---------------------------------------------------------------------------------------------

    /** One gem chosen for research. */
    public record GemChoice(GemType type, int tier) {
    }

    /** Shared availability for discovery, binding and previews; never hides legacy item metadata. */
    public static boolean definitionActive(ArcaneDefinition definition, ArcaneSettings snapshot) {
        if (!snapshot.enabled()) return false;
        if (definition.isSignature()) {
            if (!snapshot.signatures().active(definition.signature())) return false;
            return definition.signature() != ArcaneSignature.STORMWAKE || snapshot.anticheatBridge();
        }
        return !effectEnchantment(definition.enchantment()) || snapshot.effects().enabled(definition.enchantment());
    }

    /** Disabled powers do not absorb research progress or produce misleading paid clues. */
    public ArcaneRegistry activeResearchRegistry() {
        ArcaneSettings snapshot = settings();
        return new ArcaneRegistry(registry.all().stream().filter(definition -> definitionActive(definition, snapshot)).toList());
    }

    public ArcaneResearch.Plan researchPlan(Player player, ResearchProfile profile, List<GemChoice> choices, boolean prismatic) {
        List<ArcaneResearch.Reagent> reagents = new ArrayList<>();
        for (GemChoice choice : choices) {
            ArcaneAspect aspect = ArcaneAspect.ofGem(choice.type()).orElse(null);
            if (aspect == null) {
                return new ArcaneResearch.Plan(ArcaneResearch.Status.INVALID, Set.of(), "", 0, 1.0D, 0, prismatic);
            }
            reagents.add(new ArcaneResearch.Reagent(aspect, choice.tier()));
        }
        ArcaneSettings snapshot = settings();
        Set<ArcaneAspect> aspects = reagents.isEmpty() ? Set.of() : EnumSet.copyOf(reagents.stream().map(ArcaneResearch.Reagent::aspect).toList());
        if (registry.exact(aspects).filter(definition -> !definitionActive(definition, snapshot)).isPresent()) {
            return new ArcaneResearch.Plan(ArcaneResearch.Status.FEATURE_DISABLED, aspects, ArcaneAspect.key(aspects),
                0, 1.0D, snapshot.research().requiredLevel(ArcaneTier.forAspectCount(aspects.size())), prismatic);
        }
        return ArcaneResearch.plan(activeResearchRegistry(), snapshot.research(), profile, reagents, prismatic, arcaneLevel(player),
            Math.min(100.0D, talent(player, "research-progress-percent")));
    }

    public record ResearchOutcome(Outcome outcome, ArcaneResearch.Result result) {
    }

    /** Runs one experiment: consumes the chosen gems, the Dust and an optional Prismatic gem. */
    public ResearchOutcome research(Player player, List<GemChoice> choices, boolean prismatic) {
        ArcaneSettings snapshot = settings();
        if (!snapshot.enabled() || !snapshot.research().enabled()) return new ResearchOutcome(Outcome.DISABLED, null);
        telemetry.increment(ArcaneTelemetry.Metric.RESEARCH_STARTED);
        ArcaneResearch.Result[] produced = {null};
        Outcome outcome = guarded(player, () -> {
            if (!atStation(player)) return Outcome.NOT_AT_STATION;
            AccountId account = account(player).orElse(null);
            ResearchProfile profile = profile(player).orElse(null);
            if (account == null || profile == null) return Outcome.FAILED;
            ArcaneResearch.Plan plan = researchPlan(player, profile, choices, prismatic);
            if (!plan.ready()) return plan.status() == ArcaneResearch.Status.FEATURE_DISABLED ? Outcome.DISABLED : Outcome.NO_UPGRADE;
            ItemStack[] original = cloneContents(player.getInventory().getStorageContents());
            ItemStack[] updated = cloneContents(original);
            for (GemChoice choice : choices) {
                if (!takeGem(updated, gem -> gem.type() == choice.type() && gem.tier() == choice.tier(), -1)) return Outcome.MISSING_GEMS;
            }
            if (prismatic && !takeGem(updated, gem -> gem.type() == GemType.PRISMATIC, -1)) return Outcome.MISSING_GEMS;
            if (!takeDust(updated, plan.dustCost(), -1)) return Outcome.MISSING_DUST;
            String receipt = UUID.randomUUID().toString();
            ArcaneResearch.Result result = ArcaneResearch.apply(activeResearchRegistry(), snapshot.research(), profile, plan, receipt,
                historyLine("Researched " + ArcaneAspect.key(plan.aspects()).replace('+', ' ')));
            if (result.duplicate()) {
                telemetry.increment(ArcaneTelemetry.Metric.DUPLICATE_RECEIPTS);
                return Outcome.FAILED;
            }
            Outcome committed = commit(player, original, updated, account, result.profile());
            if (committed != Outcome.APPLIED) return committed;
            produced[0] = result;
            result.outcomes().forEach(found -> {
                if (found.kind() == ArcaneResearch.OutcomeKind.DISCOVERED) telemetry.increment(ArcaneTelemetry.Metric.DISCOVERIES);
                if (found.kind() == ArcaneResearch.OutcomeKind.CLUE) telemetry.increment(ArcaneTelemetry.Metric.CLUES);
            });
            awardEnchanting(player, result.experience(), "arcane-research:" + receipt);
            finish(player, result.discovered() ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0F);
            return Outcome.APPLIED;
        });
        telemetry.increment(outcome == Outcome.APPLIED ? ArcaneTelemetry.Metric.RESEARCH_COMPLETED
            : outcome == Outcome.FAILED || outcome == Outcome.SAVE_FAILED ? ArcaneTelemetry.Metric.RESEARCH_FAILED
            : ArcaneTelemetry.Metric.RESEARCH_REJECTED);
        return new ResearchOutcome(outcome, produced[0]);
    }

    /** Enchanting XP through the shared skill pipeline; the fingerprint makes it idempotent. Enchanting pays no job Marks. */
    public void awardEnchanting(Player player, long experience, String fingerprint) {
        if (experience <= 0L) return;
        try {
            plugin.skills().awardMilestone(player, Skill.ENCHANTING, experience, fingerprint);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Enchanting XP was not awarded for " + player.getUniqueId(), failure);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Inventory transaction helpers
    // ---------------------------------------------------------------------------------------------

    private Outcome guarded(Player player, java.util.function.Supplier<Outcome> work) {
        if (!validPlayer(player)) return Outcome.NOT_AT_STATION;
        if (!busy.add(player.getUniqueId())) return Outcome.BUSY;
        try {
            return work.get();
        } catch (RuntimeException failure) {
            telemetry.increment(ArcaneTelemetry.Metric.GUI_ERRORS);
            plugin.getLogger().log(Level.SEVERE, "Arcane Forge operation failed for " + player.getUniqueId(), failure);
            return Outcome.FAILED;
        } finally {
            busy.remove(player.getUniqueId());
        }
    }

    private ItemStack heldIfUnchanged(Player player, ItemStack expected, int heldSlot) {
        if (expected == null || player.getInventory().getHeldItemSlot() != heldSlot) return null;
        ItemStack held = player.getInventory().getItem(heldSlot);
        return held != null && held.equals(expected) && held.getAmount() == 1 ? held : null;
    }

    private Outcome gearCheck(ItemStack held) {
        if (!plugin.items().isAuthentic(held)) return Outcome.NOT_GEAR;
        if (plugin.items().isUnidentified(held)) return Outcome.UNIDENTIFIED;
        if (plugin.items().gear(held).isEmpty()) return Outcome.NOT_GEAR;
        return null;
    }

    /**
     * Publishes {@code updated} and then, when given, the knowledge change. If the knowledge cannot be
     * saved, the original inventory is restored before this method returns.
     */
    private Outcome commit(Player player, ItemStack[] original, ItemStack[] updated, AccountId account, ResearchProfile knowledge) {
        var inventory = player.getInventory();
        try {
            inventory.setStorageContents(updated);
        } catch (RuntimeException failure) {
            return restore(player, original, failure) ? Outcome.FAILED : Outcome.RECOVERY_REQUIRED;
        }
        if (account != null && knowledge != null && !save(account, knowledge)) {
            return restore(player, original, null) ? Outcome.SAVE_FAILED : Outcome.RECOVERY_REQUIRED;
        }
        return Outcome.APPLIED;
    }

    private boolean restore(Player player, ItemStack[] original, RuntimeException cause) {
        telemetry.increment(ArcaneTelemetry.Metric.ROLLBACKS);
        try {
            player.getInventory().setStorageContents(original);
        } catch (RuntimeException rollback) {
            if (cause != null) cause.addSuppressed(rollback);
            plugin.getLogger().log(Level.SEVERE, "RECOVERY REQUIRED: could not restore the inventory of "
                + player.getUniqueId() + " after a failed Arcane Forge operation", rollback);
            return false;
        }
        if (cause != null) plugin.getLogger().log(Level.SEVERE, "Arcane Forge inventory commit failed and was rolled back for "
            + player.getUniqueId(), cause);
        return true;
    }

    /** Feedback after a commit. It can never turn a finished operation into a reported failure. */
    private void finish(Player player, Sound sound, float pitch) {
        try {
            plugin.gearSets().refreshInventory(player);
            player.playSound(player.getLocation(), sound, 1.0F, pitch);
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Arcane Forge feedback failed after a completed operation for "
                + player.getUniqueId(), failure);
        }
    }

    public int countDust(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (plugin.enchanting().isArcaneDust(item)) total += item.getAmount();
        }
        return total;
    }

    /** Socket gems the player carries, by type and tier. */
    public Map<GemChoice, Integer> gems(Player player) {
        Map<GemChoice, Integer> gems = new java.util.TreeMap<>(java.util.Comparator
            .comparing((GemChoice choice) -> choice.type().ordinal()).thenComparing(GemChoice::tier));
        for (ItemStack item : player.getInventory().getStorageContents()) {
            plugin.forge().gem(item).ifPresent(gem -> gems.merge(new GemChoice(gem.type(), gem.tier()), item.getAmount(), Integer::sum));
        }
        return gems;
    }

    private boolean takeDust(ItemStack[] storage, int amount, int skipSlot) {
        int remaining = amount;
        for (int slot = 0; slot < storage.length && remaining > 0; slot++) {
            if (slot == skipSlot || !plugin.enchanting().isArcaneDust(storage[slot])) continue;
            int taken = Math.min(remaining, storage[slot].getAmount());
            storage[slot].setAmount(storage[slot].getAmount() - taken);
            if (storage[slot].getAmount() <= 0) storage[slot] = null;
            remaining -= taken;
        }
        return remaining == 0;
    }

    private boolean takeGem(ItemStack[] storage, java.util.function.Predicate<net.nerdsmp.paper.forge.ArcaneForgeService.Gem> wanted, int skipSlot) {
        for (int slot = 0; slot < storage.length; slot++) {
            if (slot == skipSlot) continue;
            var gem = plugin.forge().gem(storage[slot]);
            if (gem.isEmpty() || !wanted.test(gem.get())) continue;
            storage[slot].setAmount(storage[slot].getAmount() - 1);
            if (storage[slot].getAmount() <= 0) storage[slot] = null;
            return true;
        }
        return false;
    }

    /** One gem of each Aspect at or above {@code minimumTier}, always spending the lowest qualifying tier. */
    private boolean takeGems(ItemStack[] storage, Set<ArcaneAspect> aspects, int minimumTier, int skipSlot) {
        for (ArcaneAspect aspect : EnumSet.copyOf(aspects)) {
            boolean taken = false;
            for (int tier = minimumTier; tier <= 4 && !taken; tier++) {
                int wantedTier = tier;
                taken = takeGem(storage, gem -> ArcaneAspect.ofGem(gem.type()).filter(aspect::equals).isPresent()
                    && gem.tier() == wantedTier, skipSlot);
            }
            if (!taken) return false;
        }
        return true;
    }

    /** True when the player carries one gem of each Aspect at or above the tier. */
    public boolean hasGems(Player player, Set<ArcaneAspect> aspects, int minimumTier) {
        return takeGems(cloneContents(player.getInventory().getStorageContents()), aspects, minimumTier, player.getInventory().getHeldItemSlot());
    }

    static ItemStack[] cloneContents(ItemStack[] contents) {
        return Arrays.stream(contents).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
    }

    private static String historyLine(String text) {
        return HISTORY_TIME.format(Instant.now()) + " UTC  " + text;
    }

    public static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(value);
        };
    }

    // ---------------------------------------------------------------------------------------------
    // Administration
    // ---------------------------------------------------------------------------------------------

    /** Grants knowledge for QA or recovery. Writes nothing to items. */
    public boolean grant(AccountId account, ArcaneDefinition definition) {
        ResearchProfile profile = profile(account);
        return profile.known(definition.id()) || save(account, profile.discover(definition.id())
            .log(historyLine("Granted " + definition.displayName() + " by staff")));
    }

    /** A read-only report of how Forge 2.0 reads every item a player carries. Writes nothing. */
    public List<String> dryRun(Player player) {
        List<String> lines = new ArrayList<>();
        ItemStack[] contents = player.getInventory().getContents();
        int legacy = 0;
        int grandfathered = 0;
        int preserved = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getType().isAir() || !plugin.items().isAuthentic(item)) continue;
            ItemView view = view(item);
            if (!view.gear()) continue;
            if (!view.enchantments().isEmpty()) legacy++;
            if (view.grandfathered()) grandfathered++;
            preserved += view.preserved().size();
            if (view.enchantments().isEmpty() && view.signature() == null && view.preserved().isEmpty() && view.unknownSignature() == null) continue;
            lines.add("slot " + slot + " " + item.getType().name().toLowerCase(java.util.Locale.ROOT)
                + " | load " + view.load() + "/" + view.capacity() + (view.grandfathered() ? " (grandfathered)" : "")
                + " | enchants " + view.enchantments().size()
                + (view.signature() == null ? "" : " | signature " + view.signature().id())
                + (view.unknownSignature() == null ? "" : " | dormant signature " + view.unknownSignature())
                + (view.preserved().isEmpty() ? "" : " | preserved " + view.preserved())
                + (view.instability() > 0 ? " | unstable " + view.instability() : ""));
        }
        lines.addFirst("Enchanted gear: " + legacy + ", grandfathered: " + grandfathered + ", preserved unknown entries: " + preserved
            + ". No item needs rewriting; nothing was changed.");
        return lines;
    }

    /** Detached copy of a player's knowledge, for staff debugging. */
    public String describe(AccountId account) {
        YamlConfiguration yaml = new YamlConfiguration();
        profile(account).write(yaml.createSection(PROFILE_PATH));
        return yaml.saveToString();
    }
}

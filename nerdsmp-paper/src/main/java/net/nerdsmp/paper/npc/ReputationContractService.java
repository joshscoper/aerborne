package net.nerdsmp.paper.npc;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.service.EconomyService;
import net.nerdsmp.api.service.ReputationService;
import net.nerdsmp.paper.engagement.QuestAction;
import net.nerdsmp.paper.session.SessionRegistry;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public final class ReputationContractService implements Listener, AutoCloseable {
    private static final int SCHEMA_VERSION = 2;

    private final JavaPlugin plugin;
    private final SessionRegistry sessions;
    private final EconomyService economy;
    private final ReputationService reputation;
    private final File progressFile;
    private final long rotationSeed;
    private final long savePeriodTicks;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<AccountId, Map<String, Progress>> playerProgress = new HashMap<>();
    private final Set<String> pendingClaims = new HashSet<>();
    private Map<String, ReputationContract> definitions;
    private BukkitTask saveTask;
    private boolean dirty;

    public ReputationContractService(
        JavaPlugin plugin,
        SessionRegistry sessions,
        EconomyService economy,
        ReputationService reputation
    ) {
        this.plugin = plugin;
        this.sessions = sessions;
        this.economy = economy;
        this.reputation = reputation;
        String season = plugin.getConfig().getString("season.id", "season-1")
            .replaceAll("[^a-zA-Z0-9_-]", "_");
        progressFile = new File(plugin.getDataFolder(), "reputation-contracts-" + season + ".yml");
        rotationSeed = plugin.getConfig().getLong("npcs.reputation-contract-rotation-seed", 89_021L);
        int saveMinutes = Math.max(1, plugin.getConfig().getInt("npcs.reputation-contract-save-minutes", 5));
        savePeriodTicks = saveMinutes * 60L * 20L;
        definitions = loadDefinitions();
        loadProgress();
    }

    public void start() {
        if (saveTask == null) {
            saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::flushIfDirty,
                savePeriodTicks, savePeriodTicks);
        }
    }

    public synchronized void reload() {
        definitions = loadDefinitions();
    }

    public synchronized void validateFactions(Set<FactionId> knownFactions) {
        Set<FactionId> configured = new HashSet<>();
        definitions.values().forEach(contract -> configured.add(contract.factionId()));
        Set<FactionId> unknown = new HashSet<>(configured);
        unknown.removeAll(knownFactions);
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("Reputation contracts reference unknown factions: " + unknown);
        }
        for (FactionId faction : knownFactions) {
            Map<ReputationContract.Cadence, Long> counts = new EnumMap<>(ReputationContract.Cadence.class);
            for (ReputationContract.Cadence cadence : ReputationContract.Cadence.values()) {
                long count = definitions.values().stream().filter(contract -> contract.factionId().equals(faction)
                    && contract.cadence() == cadence).count();
                counts.put(cadence, count);
                if (count < 2L) {
                    throw new IllegalStateException("Faction " + faction + " needs at least two "
                        + cadence.name().toLowerCase(Locale.ROOT) + " reputation contracts");
                }
            }
        }
    }

    public synchronized List<ContractView> views(
        AccountId account,
        FactionId faction,
        int adventurerLevel,
        Instant now
    ) {
        Map<String, Progress> accountProgress = playerProgress.getOrDefault(account, Map.of());
        List<ContractView> views = new ArrayList<>();
        for (ReputationContract contract : active(faction, now)) {
            if (contract.cadence() == ReputationContract.Cadence.STORY) {
                continue;
            }
            ReputationContract.Period period = contract.cadence().period(now);
            Progress progress = accountProgress.get(contract.id());
            if (progress != null && !period.key().equals(progress.periodKey)) {
                progress = null;
            }
            ContractStatus status;
            int current = 0;
            if (progress == null) {
                status = adventurerLevel < contract.minimumAdventurerLevel()
                    ? ContractStatus.LOCKED : ContractStatus.AVAILABLE;
            } else {
                current = Math.min(contract.target(), progress.amount);
                if (progress.claimed) {
                    status = ContractStatus.CLAIMED;
                } else if (current >= contract.target()) {
                    status = ContractStatus.READY;
                } else {
                    status = ContractStatus.ACTIVE;
                }
            }
            views.add(new ContractView(contract, period, status, current));
        }
        for (ReputationContract contract : active(faction, now)) {
            if (contract.cadence() != ReputationContract.Cadence.STORY) {
                continue;
            }
            Progress progress = accountProgress.get(contract.id());
            if (progress != null && progress.claimed) {
                continue;
            }
            boolean prerequisiteMet = contract.prerequisiteId() == null
                || java.util.Optional.ofNullable(accountProgress.get(contract.prerequisiteId()))
                    .map(value -> value.claimed).orElse(false);
            if (!prerequisiteMet && progress == null) {
                continue;
            }
            ContractStatus status = adventurerLevel < contract.minimumAdventurerLevel()
                ? ContractStatus.LOCKED
                : progress == null ? ContractStatus.AVAILABLE
                : progress.amount >= contract.target() ? ContractStatus.READY : ContractStatus.ACTIVE;
            views.add(new ContractView(contract, contract.cadence().period(now), status,
                progress == null ? 0 : Math.min(contract.target(), progress.amount)));
            break;
        }
        return List.copyOf(views);
    }

    public synchronized AcceptResult accept(
        AccountId account,
        FactionId faction,
        String contractId,
        int adventurerLevel,
        Instant now
    ) {
        ReputationContract contract = active(faction, now).stream()
            .filter(candidate -> candidate.id().equals(contractId)).findFirst().orElse(null);
        if (contract == null) {
            return AcceptResult.NOT_OFFERED;
        }
        if (adventurerLevel < contract.minimumAdventurerLevel()) {
            return AcceptResult.LOCKED;
        }
        if (contract.prerequisiteId() != null) {
            Progress prerequisite = playerProgress.getOrDefault(account, Map.of()).get(contract.prerequisiteId());
            if (prerequisite == null || !prerequisite.claimed) {
                return AcceptResult.PREREQUISITE_LOCKED;
            }
        }
        ReputationContract.Period period = contract.cadence().period(now);
        Map<String, Progress> accountProgress = playerProgress.computeIfAbsent(account, ignored -> new HashMap<>());
        Progress existing = accountProgress.get(contract.id());
        if (existing != null && period.key().equals(existing.periodKey)) {
            return AcceptResult.ALREADY_ACCEPTED;
        }
        accountProgress.put(contract.id(), new Progress(period.key(), 0, false));
        dirty = true;
        saveProgress();
        return AcceptResult.ACCEPTED;
    }

    public CompletionStage<ClaimResult> claim(
        AccountId account,
        FactionId faction,
        String contractId,
        Instant now
    ) {
        ReputationContract contract;
        ReputationContract.Period period;
        String pendingKey;
        synchronized (this) {
            contract = active(faction, now).stream()
                .filter(candidate -> candidate.id().equals(contractId)).findFirst().orElse(null);
            if (contract == null) {
                return CompletableFuture.completedFuture(ClaimResult.NOT_OFFERED);
            }
            period = contract.cadence().period(now);
            Progress progress = playerProgress.getOrDefault(account, Map.of()).get(contract.id());
            if (progress == null || !period.key().equals(progress.periodKey)
                || progress.amount < contract.target() || progress.claimed) {
                return CompletableFuture.completedFuture(ClaimResult.NOT_READY);
            }
            pendingKey = account + ":" + contract.id() + ":" + period.key();
            if (!pendingClaims.add(pendingKey)) {
                return CompletableFuture.completedFuture(ClaimResult.PENDING);
            }
        }

        String ledgerKey = "reputation-contract:" + pendingKey;
        CompletionStage<Void> reward = reputation.adjust(
            account,
            contract.factionId(),
            contract.reputationReward(),
            new ReputationService.ReputationReason("contract.reputation", contract.title()),
            ledgerKey + ":reputation"
        ).thenAccept(result -> {
            if (result.status() == ReputationService.AdjustmentResult.Status.INVALID_AMOUNT) {
                throw new CompletionException(new IllegalStateException("Reputation reward was rejected."));
            }
        });
        if (contract.markReward() > 0L) {
            reward = reward.thenCompose(ignored -> economy.credit(
                account,
                contract.markReward(),
                new EconomyService.TransactionReason("contract.reward", contract.title()),
                ledgerKey + ":marks"
            )).thenAccept(result -> {
                if (result.status() != EconomyService.TransactionResult.Status.APPLIED
                    && result.status() != EconomyService.TransactionResult.Status.DUPLICATE) {
                    throw new CompletionException(new IllegalStateException("Marks reward was rejected."));
                }
            });
        }
        CompletionStage<ClaimResult> outcome = reward.thenApply(ignored -> {
            synchronized (this) {
                Progress progress = playerProgress.getOrDefault(account, Map.of()).get(contract.id());
                if (progress != null && period.key().equals(progress.periodKey)) {
                    progress.claimed = true;
                    dirty = true;
                    saveProgress();
                }
            }
            return ClaimResult.CLAIMED;
        });
        return outcome.whenComplete((result, error) -> {
            synchronized (this) {
                pendingClaims.remove(pendingKey);
            }
        });
    }

    public synchronized void recordProgress(Player player, QuestAction action, int amount) {
        if (amount <= 0) {
            return;
        }
        AccountId account = sessions.account(player.getUniqueId()).orElse(null);
        Map<String, Progress> accountProgress = account == null ? null : playerProgress.get(account);
        if (accountProgress == null) {
            return;
        }
        Instant now = Instant.now();
        boolean completedAny = false;
        for (FactionId faction : factions()) {
            for (ReputationContract contract : active(faction, now)) {
                if (contract.action() != action) {
                    continue;
                }
                ReputationContract.Period period = contract.cadence().period(now);
                Progress progress = accountProgress.get(contract.id());
                if (progress == null || !period.key().equals(progress.periodKey) || progress.claimed
                    || progress.amount >= contract.target()) {
                    continue;
                }
                int previous = progress.amount;
                progress.amount = Math.min(contract.target(), Math.addExact(progress.amount, amount));
                dirty = true;
                if (progress.amount >= contract.target()) {
                    completedAny = true;
                    player.sendRichMessage("<green>Contract complete:</green> <white>" + escape(contract.title())
                        + "</white> <gray>Return to the " + escape(contract.factionId().value().replace('_', ' '))
                        + " quest giver to claim it.</gray>");
                } else if ((previous * 4L) / contract.target() < (progress.amount * 4L) / contract.target()) {
                    player.sendActionBar(miniMessage.deserialize("<aqua>" + escape(contract.title()) + "</aqua>"
                        + " <gray>" + progress.amount + "/" + contract.target() + "</gray>"));
                }
            }
        }
        if (completedAny) {
            saveProgress();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        flushIfDirty();
    }

    private synchronized Collection<FactionId> factions() {
        return definitions.values().stream().map(ReputationContract::factionId).distinct().sorted().toList();
    }

    private synchronized List<ReputationContract> active(FactionId faction, Instant now) {
        return ReputationContractRotation.active(definitions.values(), faction, now, rotationSeed);
    }

    private Map<String, ReputationContract> loadDefinitions() {
        YamlConfiguration yaml = contractContent();
        ConfigurationSection root = yaml.getConfigurationSection("contracts");
        if (root == null) {
            throw new IllegalStateException("content/reputation-contracts.yml is missing contracts");
        }
        Map<String, ReputationContract> loaded = new LinkedHashMap<>();
        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) {
                continue;
            }
            String id = rawId.toLowerCase(Locale.ROOT);
            ReputationContract.Cadence cadence;
            QuestAction action;
            try {
                cadence = ReputationContract.Cadence.valueOf(section.getString("cadence", "")
                    .toUpperCase(Locale.ROOT));
                action = QuestAction.valueOf(section.getString("action", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Invalid cadence or action on reputation contract " + id, exception);
            }
            ReputationContract contract = new ReputationContract(
                id,
                new FactionId(section.getString("faction", "")),
                cadence,
                section.getString("title", ""),
                section.getString("description", ""),
                action,
                section.getInt("target"),
                section.getInt("reputation"),
                section.getLong("marks", 0L),
                section.getInt("minimum-adventurer-level", 1),
                section.getString("prerequisite"),
                section.getString("chapter", "")
            );
            if (loaded.put(id, contract) != null) {
                throw new IllegalStateException("Duplicate reputation contract id: " + id);
            }
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("At least one reputation contract is required");
        }
        return Map.copyOf(loaded);
    }

    private YamlConfiguration contractContent() {
        File target = new File(plugin.getDataFolder(), "content/reputation-contracts.yml");
        if (!target.exists()) {
            plugin.saveResource("content/reputation-contracts.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(target);
        if (yaml.getInt("schema-version", 0) >= SCHEMA_VERSION) {
            return yaml;
        }
        try (var reader = new InputStreamReader(java.util.Objects.requireNonNull(
            plugin.getResource("content/reputation-contracts.yml")), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load bundled reputation contracts", exception);
        }
    }

    private void loadProgress() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(progressFile);
        ConfigurationSection accounts = yaml.getConfigurationSection("accounts");
        if (accounts == null) {
            return;
        }
        for (String rawAccount : accounts.getKeys(false)) {
            try {
                AccountId account = new AccountId(java.util.UUID.fromString(rawAccount));
                ConfigurationSection contracts = accounts.getConfigurationSection(rawAccount + ".contracts");
                if (contracts == null) {
                    continue;
                }
                Map<String, Progress> loaded = new HashMap<>();
                for (String contractId : contracts.getKeys(false)) {
                    ConfigurationSection section = contracts.getConfigurationSection(contractId);
                    if (section == null || !definitions.containsKey(contractId)) {
                        continue;
                    }
                    String period = section.getString("period", "");
                    if (!period.isBlank()) {
                        loaded.put(contractId, new Progress(period, Math.max(0, section.getInt("progress")),
                            section.getBoolean("claimed", false)));
                    }
                }
                if (!loaded.isEmpty()) {
                    playerProgress.put(account, loaded);
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore hand-edited invalid account sections without discarding other players.
            }
        }
    }

    private synchronized void flushIfDirty() {
        if (dirty) {
            saveProgress();
        }
    }

    private synchronized void saveProgress() {
        YamlConfiguration yaml = new YamlConfiguration();
        playerProgress.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(AccountId::toString)))
            .forEach(account -> account.getValue().entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(contract -> {
                    String path = "accounts." + account.getKey() + ".contracts." + contract.getKey();
                    yaml.set(path + ".period", contract.getValue().periodKey);
                    yaml.set(path + ".progress", contract.getValue().amount);
                    yaml.set(path + ".claimed", contract.getValue().claimed);
                }));
        try {
            AtomicYamlFiles.save(yaml, progressFile);
            dirty = false;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist reputation contract progress", exception);
        }
    }

    private String escape(String input) {
        return input.replace("<", "\\<").replace(">", "\\>");
    }

    @Override
    public synchronized void close() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (dirty) {
            saveProgress();
        }
    }

    public record ContractView(
        ReputationContract contract,
        ReputationContract.Period period,
        ContractStatus status,
        int progress
    ) {
    }

    public enum ContractStatus {
        LOCKED,
        AVAILABLE,
        ACTIVE,
        READY,
        CLAIMED
    }

    public enum AcceptResult {
        ACCEPTED,
        ALREADY_ACCEPTED,
        LOCKED,
        PREREQUISITE_LOCKED,
        NOT_OFFERED
    }

    public enum ClaimResult {
        CLAIMED,
        NOT_READY,
        NOT_OFFERED,
        PENDING
    }

    private static final class Progress {
        private final String periodKey;
        private int amount;
        private boolean claimed;

        private Progress(String periodKey, int amount, boolean claimed) {
            this.periodKey = periodKey;
            this.amount = amount;
            this.claimed = claimed;
        }
    }
}

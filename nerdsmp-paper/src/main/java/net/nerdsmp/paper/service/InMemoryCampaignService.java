package net.nerdsmp.paper.service;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.service.CampaignService;
import net.nerdsmp.paper.storage.AtomicYamlFiles;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryCampaignService implements CampaignService {
    private final String id;
    private final String title;
    private Instant startsAt;
    private Instant endsAt;
    private final long dailyCap;
    private final Set<String> categories;
    private final Map<String, Long> milestones;
    private final Map<String, Long> pointsByCategory = new ConcurrentHashMap<>();
    private final Map<DailyAccount, Long> dailyTotals = new ConcurrentHashMap<>();
    private final Set<String> idempotencyKeys = ConcurrentHashMap.newKeySet();
    private final Set<String> claimedRewards = ConcurrentHashMap.newKeySet();
    private final File persistenceFile;
    private final String season, root;

    public InMemoryCampaignService(
        String id,
        String title,
        Instant startsAt,
        Instant endsAt,
        long dailyCap,
        Set<String> categories,
        Map<String, Long> milestones
    ) {
        this(id, title, startsAt, endsAt, dailyCap, categories, milestones, null);
    }

    public InMemoryCampaignService(
        String id,
        String title,
        Instant startsAt,
        Instant endsAt,
        long dailyCap,
        Set<String> categories,
        Map<String, Long> milestones,
        File persistenceFile
    ) {
        this(id,title,startsAt,endsAt,dailyCap,categories,milestones,persistenceFile,"legacy");
    }

    public InMemoryCampaignService(String id,String title,Instant startsAt,Instant endsAt,long dailyCap,
        Set<String> categories,Map<String,Long> milestones,File persistenceFile,String season) {
        if (!id.matches("[A-Za-z0-9_-]{1,64}") || !season.matches("[A-Za-z0-9_-]{1,64}") || dailyCap<0 || !endsAt.isAfter(startsAt)
            || categories.stream().anyMatch(value->!value.matches("[a-z0-9_-]{1,64}"))
            || milestones.entrySet().stream().anyMatch(entry->!entry.getKey().matches("[a-z0-9_-]{1,64}") || entry.getValue()<0))
            throw new IllegalArgumentException("Invalid campaign configuration");
        this.season=season; this.root="seasons."+season+".campaigns."+id;
        this.id = id;
        this.title = title;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.dailyCap = dailyCap;
        this.categories = Set.copyOf(categories);
        this.milestones = Map.copyOf(milestones);
        this.persistenceFile = persistenceFile;
        categories.forEach(category -> pointsByCategory.put(category, 0L));
        load();
        save(); // Freeze schedule before the first contribution or restart.
    }

    @Override
    public synchronized CompletionStage<Optional<CampaignSnapshot>> activeCampaign() {
        Instant now = Instant.now();
        if (now.isBefore(startsAt) || !now.isBefore(endsAt)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long total = pointsByCategory.values().stream().reduce(0L, Math::addExact);
        return CompletableFuture.completedFuture(Optional.of(new CampaignSnapshot(
            id,
            title,
            startsAt,
            endsAt,
            total,
            new HashMap<>(pointsByCategory),
            milestones
        )));
    }

    @Override
    public synchronized CompletionStage<ContributionResult> contribute(
        AccountId accountId,
        String category,
        long points,
        String idempotencyKey
    ) {
        Instant now = Instant.now();
        if (now.isBefore(startsAt) || !now.isBefore(endsAt)) {
            return completed(ContributionResult.NO_ACTIVE_CAMPAIGN);
        }
        if (points <= 0L) {
            return completed(ContributionResult.INVALID_POINTS);
        }
        if (!categories.contains(category)) {
            return completed(ContributionResult.INVALID_CATEGORY);
        }
        if (idempotencyKey==null || idempotencyKey.isBlank() || idempotencyKey.length()>256) throw new IllegalArgumentException("Invalid contribution receipt");
        if (idempotencyKeys.contains(idempotencyKey)) {
            return completed(ContributionResult.DUPLICATE);
        }

        DailyAccount dailyAccount = new DailyAccount(accountId, LocalDate.now(ZoneOffset.UTC));
        long current = dailyTotals.getOrDefault(dailyAccount, 0L);
        if (points > dailyCap - current) {
            return completed(ContributionResult.DAILY_CAP_REACHED);
        }
        long previous=pointsByCategory.get(category);
        Math.addExact(pointsByCategory.values().stream().reduce(0L, Math::addExact),points);
        dailyTotals.put(dailyAccount,current+points); pointsByCategory.put(category,Math.addExact(previous,points));
        idempotencyKeys.add(idempotencyKey);
        try { save(); } catch(RuntimeException failure) {
            dailyTotals.put(dailyAccount,current); pointsByCategory.put(category,previous);
            idempotencyKeys.remove(idempotencyKey); throw failure;
        }
        return completed(ContributionResult.APPLIED);
    }

    @Override
    public synchronized CompletionStage<Boolean> claimReward(AccountId accountId, String milestoneId) {
        Long required = milestones.get(milestoneId);
        if (required == null) {
            return CompletableFuture.completedFuture(false);
        }
        long total = pointsByCategory.values().stream().reduce(0L, Math::addExact);
        boolean claimed = total >= required && claimedRewards.add(accountId + ":" + milestoneId);
        if (claimed) {
            try { save(); } catch(RuntimeException failure) { claimedRewards.remove(accountId+":"+milestoneId); throw failure; }
        }
        return CompletableFuture.completedFuture(claimed);
    }

    private CompletionStage<ContributionResult> completed(ContributionResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private void load() {
        if (persistenceFile == null || !persistenceFile.isFile()) {
            return;
        }
        YamlConfiguration yaml = net.nerdsmp.paper.storage.SeasonalYaml.load(persistenceFile, season, "campaigns");
        if(yaml.contains(root+".starts-at") != yaml.contains(root+".ends-at")) throw new IllegalStateException("Incomplete campaign schedule");
        if(yaml.contains(root+".starts-at")) {
            startsAt=Instant.parse(yaml.getString(root+".starts-at")); endsAt=Instant.parse(yaml.getString(root+".ends-at"));
            if(!endsAt.isAfter(startsAt)) throw new IllegalStateException("Invalid campaign schedule");
        }
        categories.forEach(category-> {
            String key=root+".categories."+category;
            if(yaml.contains(key) && !yaml.isLong(key) && !yaml.isInt(key)) throw new IllegalStateException("Invalid campaign points");
            pointsByCategory.put(category,nonnegative(yaml.getLong(key)));
        });
        pointsByCategory.values().stream().reduce(0L,Math::addExact);
        var daily = yaml.getConfigurationSection(root + ".daily");
        if(yaml.contains(root+".daily") && daily==null) throw new IllegalStateException("Invalid campaign daily section");
        if (daily != null) {
            for (String date : daily.getKeys(false)) {
                try {
                    LocalDate parsedDate = LocalDate.parse(date);
                    var accounts = daily.getConfigurationSection(date);
                    if (accounts == null) {
                        throw new IllegalStateException("Invalid daily account section");
                    }
                    for (String rawAccount : accounts.getKeys(false)) {
                        if(!accounts.isLong(rawAccount) && !accounts.isInt(rawAccount)) throw new IllegalStateException("Invalid daily points");
                        dailyTotals.put(new DailyAccount(AccountId.parse(rawAccount), parsedDate),
                            nonnegative(accounts.getLong(rawAccount)));
                    }
                } catch (IllegalArgumentException ignored) {
                    throw new IllegalStateException("Invalid campaign daily state", ignored);
                }
            }
        }
        idempotencyKeys.addAll(receipts(yaml,root+".idempotency-keys"));
        claimedRewards.addAll(receipts(yaml,root+".claimed-rewards"));
    }

    private void save() {
        if (persistenceFile == null) {
            return;
        }
        YamlConfiguration yaml = net.nerdsmp.paper.storage.SeasonalYaml.load(persistenceFile, season, "campaigns");
        yaml.set(root+".starts-at",startsAt.toString()); yaml.set(root+".ends-at",endsAt.toString());
        pointsByCategory.forEach((category, points) ->
            yaml.set(root + ".categories." + category, points));
        yaml.set(root + ".daily", null);
        dailyTotals.forEach((key, points) -> yaml.set(root + ".daily."
            + key.date + "." + key.accountId, points));
        yaml.set(root + ".idempotency-keys", idempotencyKeys.stream().sorted().toList());
        yaml.set(root + ".claimed-rewards", claimedRewards.stream().sorted().toList());
        try {
            AtomicYamlFiles.save(yaml, persistenceFile);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not persist campaign state", exception);
        }
    }

    private static long nonnegative(long value) { if(value<0) throw new IllegalStateException("Negative daily points"); return value; }
    private static java.util.List<String> receipts(YamlConfiguration yaml,String key) {
        if(!yaml.contains(key)) return java.util.List.of();
        if(!yaml.isList(key)) throw new IllegalStateException("Invalid campaign receipts");
        var values=yaml.getStringList(key);
        if(values.size()!=yaml.getList(key).size() || values.stream().anyMatch(value->value.isBlank() || value.length()>256))
            throw new IllegalStateException("Malformed campaign receipts");
        return values;
    }

    private record DailyAccount(AccountId accountId, LocalDate date) {
    }
}

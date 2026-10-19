package net.nerdsmp.paper.activity;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.listener.SkillListener;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Records collection progress only after an NPC has accepted the physical item.
 * Catching or finding an item is deliberately not sufficient.
 */
public final class CollectionMasteryService {
    private static final String ROOT = "collections.entries.";

    private final NerdSmpPlugin plugin;
    private final CollectionRewardDelivery rewards;
    private final Set<String> pendingRewards = ConcurrentHashMap.newKeySet();
    private final Map<String, Entry> entries;
    private final Map<String, Entry> fishingEntries;
    private final Map<Material, Entry> archaeologyEntries;

    public CollectionMasteryService(NerdSmpPlugin plugin, SkillListener skills) {
        this.plugin = plugin;
        this.rewards = new CollectionRewardDelivery(plugin.playerData(), plugin.progression(), plugin.economy());
        Map<String, Entry> all = new LinkedHashMap<>();
        Map<String, Entry> fishing = new LinkedHashMap<>();
        for (FishingCatch catchType : FishingCatch.values()) {
            Reward reward = fishingReward(catchType.rarity());
            Entry entry = new Entry(
                "fishing_" + catchType.itemId().value(),
                title(catchType.itemId().value()),
                Category.FISHING,
                Skill.FISHING,
                reward.required(),
                reward.experience(),
                reward.marks()
            );
            all.put(entry.id(), entry);
            fishing.put(catchType.itemId().value(), entry);
        }
        Map<Material, Entry> archaeology = new LinkedHashMap<>();
        Arrays.stream(Material.values())
            .filter(material -> material.name().endsWith("_POTTERY_SHERD"))
            .sorted(Comparator.comparing(Enum::name))
            .forEach(material -> addArchaeology(all, archaeology, material, 5, 200L, 60L));
        addArchaeology(all, archaeology, Material.MUSIC_DISC_RELIC, 3, 450L, 150L);
        addArchaeology(all, archaeology, Material.SNIFFER_EGG, 1, 900L, 300L);
        entries = Map.copyOf(all);
        fishingEntries = Map.copyOf(fishing);
        archaeologyEntries = Map.copyOf(archaeology);
    }

    public Entry fishingEntry(String itemId) {
        return fishingEntries.get(itemId);
    }

    public Entry archaeologyEntry(Material material) {
        return archaeologyEntries.get(material);
    }

    public List<Map.Entry<Material, Entry>> archaeologyEntries() {
        return archaeologyEntries.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getValue().displayName())).toList();
    }

    public void onSessionReady(Player player) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        if (account != null) {
            retryRewards(player, account);
        }
    }

    public Progress progress(AccountId account, Entry entry) {
        int count = plugin.playerData().read(account,
            yaml -> Math.max(0, yaml.getInt(path(entry))));
        return new Progress(count, entry.required(), count >= entry.required());
    }

    public int recordedCount(AccountId account, Category category) {
        return (int) entries.values().stream()
            .filter(entry -> entry.category() == category && progress(account, entry).count() > 0)
            .count();
    }

    public int masteredCount(AccountId account, Category category) {
        return (int) entries.values().stream()
            .filter(entry -> entry.category() == category && progress(account, entry).mastered())
            .count();
    }

    public RecordResult record(Player player, Map<Entry, Integer> turnIns) {
        AccountId account = plugin.sessions().account(player.getUniqueId()).orElse(null);
        return record(player, account, turnIns);
    }

    public RecordResult record(Player player, AccountId account, Map<Entry, Integer> turnIns) {
        if (account == null || turnIns.isEmpty()) {
            return new RecordResult(0, List.of());
        }
        Map<Entry, Integer> accepted = new LinkedHashMap<>();
        turnIns.forEach((entry, amount) -> {
            if (entry != null && entry.equals(entries.get(entry.id())) && amount != null && amount > 0) {
                accepted.merge(entry, amount, Integer::sum);
            }
        });
        if (accepted.isEmpty()) {
            return new RecordResult(0, List.of());
        }
        List<Entry> mastered = new ArrayList<>();
        int[] recorded = {0};
        plugin.playerData().update(account, yaml -> accepted.forEach((entry, amount) -> {
            String path = path(entry);
            int before = Math.max(0, yaml.getInt(path));
            int after = (int) Math.min(Integer.MAX_VALUE, (long) before + amount);
            yaml.set(path, after);
            recorded[0] += amount;
            if (before < entry.required() && after >= entry.required()) {
                mastered.add(entry);
            }
        }));
        try {
            retryRewards(player, account);
        } catch (RuntimeException exception) {
            // The turn-in is committed; a delivery failure must never refund its consumed items.
            plugin.getLogger().severe("Collection rewards remain pending for " + account + ": " + exception.getMessage());
        }
        return new RecordResult(recorded[0], List.copyOf(mastered));
    }

    private void retryRewards(Player player, AccountId account) {
        entries.values().stream().filter(entry -> progress(account, entry).mastered())
            .filter(entry -> !rewards.delivered(account, entry.id()))
            .forEach(entry -> awardMastery(player, account, entry));
    }

    private void awardMastery(Player player, AccountId account, Entry entry) {
        String key = "collection-mastery:" + account + ":" + entry.id();
        if (!pendingRewards.add(key)) {
            return;
        }
        try {
            rewards.deliver(account, entry.id(), entry.displayName(), entry.skill(), entry.experienceReward(),
                entry.marksReward()).whenComplete((delivered, error) -> {
                    pendingRewards.remove(key);
                    if (error != null) {
                        plugin.getLogger().severe("Collection mastery reward remains pending for " + account
                            + "/" + entry.id() + ": " + error.getMessage());
                        return;
                    }
                    if (!Boolean.TRUE.equals(delivered) || !plugin.isEnabled()) {
                        return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()
                            || plugin.sessions().account(player.getUniqueId()).filter(account::equals).isEmpty()) {
                            return;
                        }
                        plugin.presentation().refresh(player);
                        plugin.achievements().onSessionReady(player);
                        player.sendRichMessage("<gold><bold>Collection Mastered</bold></gold> <white>"
                            + entry.displayName() + "</white> <gray>Your XP and Marks rewards have been delivered.</gray>");
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 1.2F);
                    });
                });
        } catch (RuntimeException exception) {
            pendingRewards.remove(key);
            plugin.getLogger().severe("Collection mastery reward remains pending for " + account
                + "/" + entry.id() + ": " + exception.getMessage());
        }
    }

    private static void addArchaeology(
        Map<String, Entry> all,
        Map<Material, Entry> archaeology,
        Material material,
        int required,
        long experience,
        long marks
    ) {
        Entry entry = new Entry("excavation_" + material.name().toLowerCase(Locale.ROOT), title(material.name()),
            Category.EXCAVATION, Skill.EXCAVATION, required, experience, marks);
        all.put(entry.id(), entry);
        archaeology.put(material, entry);
    }

    private static Reward fishingReward(FishingRolls.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> new Reward(5, 150L, 50L);
            case UNCOMMON -> new Reward(4, 250L, 75L);
            case RARE -> new Reward(3, 450L, 125L);
            case LEGENDARY -> new Reward(2, 800L, 250L);
        };
    }

    private static String path(Entry entry) {
        return ROOT + entry.id() + ".turn-ins";
    }

    private static String title(String raw) {
        return Arrays.stream(raw.toLowerCase(Locale.ROOT).split("[_-]"))
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .collect(java.util.stream.Collectors.joining(" "));
    }

    public enum Category {
        FISHING,
        EXCAVATION
    }

    public record Entry(
        String id,
        String displayName,
        Category category,
        Skill skill,
        int required,
        long experienceReward,
        long marksReward
    ) {
    }

    public record Progress(int count, int required, boolean mastered) {
        public int remaining() {
            return Math.max(0, required - count);
        }
    }

    public record RecordResult(int recorded, List<Entry> newlyMastered) {
        public RecordResult {
            newlyMastered = List.copyOf(newlyMastered);
        }
    }

    private record Reward(int required, long experience, long marks) {
    }
}

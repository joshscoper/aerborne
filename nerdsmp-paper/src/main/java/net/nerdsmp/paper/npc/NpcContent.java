package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.FactionId;
import net.nerdsmp.api.model.ItemId;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record NpcContent(
    Map<FactionId, FactionDefinition> factions,
    Map<String, NpcDefinition> npcs,
    Map<String, DialogueNode> dialogues,
    Map<String, ShopDefinition> shops
) {
    public NpcContent {
        factions = Map.copyOf(factions);
        npcs = Map.copyOf(npcs);
        dialogues = Map.copyOf(dialogues);
        shops = Map.copyOf(shops);
    }

    public FactionDefinition faction(FactionId id) {
        FactionDefinition found = factions.get(id);
        if (found == null) {
            throw new IllegalArgumentException("Unknown faction: " + id);
        }
        return found;
    }

    public ReputationView reputation(FactionId id, int points) {
        FactionDefinition faction = faction(id);
        TierDefinition tier = faction.tiers().getFirst();
        for (TierDefinition candidate : faction.tiers()) {
            if (points >= candidate.threshold()) {
                tier = candidate;
            }
        }
        return new ReputationView(faction, tier, points);
    }

    public record FactionDefinition(
        FactionId id,
        String name,
        String description,
        List<TierDefinition> tiers
    ) {
        public FactionDefinition {
            tiers = List.copyOf(tiers);
            if (tiers.isEmpty()) {
                throw new IllegalArgumentException("Faction " + id + " needs at least one tier");
            }
            for (int index = 1; index < tiers.size(); index++) {
                if (tiers.get(index - 1).threshold() >= tiers.get(index).threshold()) {
                    throw new IllegalArgumentException("Faction tiers must have ascending unique thresholds: " + id);
                }
            }
        }
    }

    public record TierDefinition(
        String id,
        String name,
        String color,
        int threshold,
        double shopPriceMultiplier,
        boolean canTrade
    ) {
        public TierDefinition {
            if (!id.matches("[a-z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("Invalid reputation tier id: " + id);
            }
            if (!color.matches("[a-z_]{3,24}")) {
                throw new IllegalArgumentException("Invalid MiniMessage color name: " + color);
            }
            if (!Double.isFinite(shopPriceMultiplier) || shopPriceMultiplier < 0.25D
                || shopPriceMultiplier > 4.0D) {
                throw new IllegalArgumentException("Shop multiplier must be between 0.25 and 4.0");
            }
            if (threshold < -1000 || threshold > 1000) {
                throw new IllegalArgumentException("Reputation tier threshold is outside -1000..1000");
            }
        }
    }

    public record ReputationView(FactionDefinition faction, TierDefinition tier, int points) {
        public long price(long basePrice) {
            return Math.max(1L, (long) Math.ceil(basePrice * tier.shopPriceMultiplier()));
        }

        public long payout(long basePrice) {
            double multiplier = Math.max(0.50D, Math.min(1.25D, 2.0D - tier.shopPriceMultiplier()));
            return Math.max(1L, (long) Math.floor(basePrice * multiplier));
        }
    }

    public record NpcDefinition(
        String id,
        String name,
        String title,
        EntityType entityType,
        FactionId factionId,
        String rootDialogue,
        String shopId,
        double lookRadius
    ) {
    }

    public record DialogueNode(String id, List<String> text, List<DialogueChoice> choices) {
        public DialogueNode {
            text = List.copyOf(text);
            choices = List.copyOf(choices);
            if (text.isEmpty()) {
                throw new IllegalArgumentException("Dialogue node needs text: " + id);
            }
            if (choices.size() > 5) {
                throw new IllegalArgumentException("Dialogue node supports at most five choices: " + id);
            }
            Set<String> responseText = new HashSet<>();
            for (DialogueChoice choice : choices) {
                String normalized = choice.text().strip().toLowerCase(Locale.ROOT);
                if (!responseText.add(normalized)) {
                    throw new IllegalArgumentException("Dialogue node repeats a response: " + id);
                }
            }
        }
    }

    public record DialogueChoice(
        String id,
        String text,
        String nextNode,
        boolean close,
        boolean once,
        Duration cooldown,
        Requirements requirements,
        List<DialogueEffect> effects
    ) {
        public DialogueChoice {
            effects = List.copyOf(effects);
            if (nextNode != null && close) {
                throw new IllegalArgumentException("A dialogue choice cannot both continue and close: " + id);
            }
        }
    }

    public record Requirements(
        int minimumReputation,
        int maximumReputation,
        long minimumMarks,
        int minimumAdventurerLevel,
        Set<String> requiredFlags,
        Set<String> absentFlags
    ) {
        public Requirements {
            requiredFlags = Set.copyOf(requiredFlags);
            absentFlags = Set.copyOf(absentFlags);
            if (minimumReputation > maximumReputation) {
                throw new IllegalArgumentException("Minimum reputation cannot exceed maximum reputation");
            }
            if (minimumMarks < 0L || minimumAdventurerLevel < 1) {
                throw new IllegalArgumentException("Dialogue requirements cannot be negative");
            }
            if (minimumReputation < -1000 || maximumReputation > 1000) {
                throw new IllegalArgumentException("Dialogue reputation requirements are outside -1000..1000");
            }
        }

        public static Requirements none() {
            return new Requirements(-1000, 1000, 0L, 1, Set.of(), Set.of());
        }
    }

    public enum EffectType {
        REPUTATION,
        MARKS,
        FLAG,
        OPEN_SHOP,
        OPEN_BANK,
        OPEN_IDENTIFIER,
        OPEN_REPAIR,
        OPEN_CONTRACTS,
        OPEN_ARCHIVE,
        OPEN_FISHERY,
        OPEN_ALCHEMY,
        OPEN_FORGE,
        OPEN_ENCHANTMENTS,
        OPEN_COMMAND
    }

    public record DialogueEffect(
        EffectType type,
        FactionId factionId,
        int amount,
        String key,
        boolean flagValue,
        String shopId,
        String command
    ) {
    }

    public record ShopDefinition(String id, String title, String description, List<ShopProduct> products) {
        public ShopDefinition {
            products = List.copyOf(products);
            if (products.isEmpty() || products.size() > 126) {
                throw new IllegalArgumentException("Shop " + id + " needs 1-126 products");
            }
        }

        public ShopDefinition(String id, String title, List<ShopProduct> products) {
            this(id, title, "", products);
        }
    }

    public record ShopProduct(
        String id,
        Material material,
        ItemId customItem,
        int amount,
        long basePrice,
        long sellPrice,
        int dailySellLimit,
        int minimumReputation,
        String description
    ) {
        public ShopProduct {
            if ((material == null) == (customItem == null)) {
                throw new IllegalArgumentException("Shop product must have exactly one material or custom item: " + id);
            }
            if (amount < 1 || amount > 64 || basePrice < 0L || sellPrice < 0L
                || basePrice > 1_000_000L || sellPrice > 1_000_000L
                || (basePrice == 0L && sellPrice == 0L)) {
                throw new IllegalArgumentException("Invalid amount or price for shop product: " + id);
            }
            if (sellPrice > 0L && (dailySellLimit < 1 || dailySellLimit > 100)) {
                throw new IllegalArgumentException("Sellable product needs a daily bundle limit: " + id);
            }
            if (sellPrice == 0L && dailySellLimit != 0) {
                throw new IllegalArgumentException("Buy-only product cannot have a sell limit: " + id);
            }
            if (basePrice > 0L && sellPrice >= basePrice) {
                throw new IllegalArgumentException("Vendor sell price must stay below buy price: " + id);
            }
            if (minimumReputation < -1000 || minimumReputation > 1000) {
                throw new IllegalArgumentException("Shop reputation requirement is outside -1000..1000: " + id);
            }
        }

        public Optional<ItemId> customItemId() {
            return Optional.ofNullable(customItem);
        }
    }
}

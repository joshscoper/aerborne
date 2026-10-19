package net.nerdsmp.paper.activity;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.List;

enum ExcavationReward {
    BUILDING_BRICKS(Material.BRICK, FishingRolls.Rarity.COMMON, 40, 2, 5),
    KNAPPING_FLINT(Material.FLINT, FishingRolls.Rarity.COMMON, 35, 2, 4),
    MINER_SHERD(Material.MINER_POTTERY_SHERD, FishingRolls.Rarity.COMMON, 25, 1, 1),

    BURIED_COPPER(Material.COPPER_INGOT, FishingRolls.Rarity.UNCOMMON, 40, 2, 4),
    AMETHYST_CLUSTER(Material.AMETHYST_SHARD, FishingRolls.Rarity.UNCOMMON, 35, 2, 4),
    OLD_COINAGE(Material.GOLD_NUGGET, FishingRolls.Rarity.UNCOMMON, 25, 4, 8),

    RELIC_RECORD(Material.MUSIC_DISC_RELIC, FishingRolls.Rarity.RARE, 55, 1, 1),
    ECHO_SHARDS(Material.ECHO_SHARD, FishingRolls.Rarity.RARE, 45, 1, 2),

    SNIFFER_EGG(Material.SNIFFER_EGG, FishingRolls.Rarity.LEGENDARY, 100, 1, 1);

    private final Material material;
    private final FishingRolls.Rarity rarity;
    private final int weight;
    private final int minimumAmount;
    private final int maximumAmount;

    ExcavationReward(
        Material material,
        FishingRolls.Rarity rarity,
        int weight,
        int minimumAmount,
        int maximumAmount
    ) {
        this.material = material;
        this.rarity = rarity;
        this.weight = weight;
        this.minimumAmount = minimumAmount;
        this.maximumAmount = maximumAmount;
    }

    Material material() {
        return material;
    }

    int weight() {
        return weight;
    }

    int amount(double roll) {
        double bounded = Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll));
        return minimumAmount + (int) Math.floor(bounded * (maximumAmount - minimumAmount + 1));
    }

    static List<ExcavationReward> pool(FishingRolls.Rarity rarity) {
        return Arrays.stream(values()).filter(reward -> reward.rarity == rarity).toList();
    }

    static ExcavationReward select(FishingRolls.Rarity rarity, double roll) {
        List<ExcavationReward> pool = pool(rarity);
        if (pool.isEmpty()) {
            throw new IllegalArgumentException("No excavation rewards are configured for " + rarity);
        }
        int totalWeight = pool.stream().mapToInt(ExcavationReward::weight).sum();
        double target = Math.max(0.0D, Math.min(Math.nextDown(1.0D), roll)) * totalWeight;
        int running = 0;
        for (ExcavationReward reward : pool) {
            running += reward.weight;
            if (target < running) {
                return reward;
            }
        }
        return pool.getLast();
    }
}

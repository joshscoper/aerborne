package net.nerdsmp.paper.expedition.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Expedition loot rules, free of Bukkit: what an enemy drops by its role, what a crate, chest or
 * secret cache holds, and what the end-of-run reward chests hold for a run's grade. Every
 * expedition has its own {@link Table} of materials (common, uncommon, rare), signature set pieces
 * and boss signatures; harder tiers raise the odds ({@code luck}).
 */
public final class ExpeditionLoot {
    /** One expedition's loot: materials from common to rare, its set pieces and its boss signatures. */
    public record Table(List<String> materials, List<String> gear, List<String> signatures) {
        public static final Table EMPTY = new Table(List.of(), List.of(), List.of());

        public Table {
            materials = List.copyOf(materials);
            gear = List.copyOf(gear);
            signatures = List.copyOf(signatures);
        }

        String material(int grade) { return materials.isEmpty() ? null : materials.get(Math.min(grade, materials.size() - 1)); }
    }

    public enum Source { NORMAL, ELITE, MINIBOSS, BOSS, CRATE, CHEST, SECRET }

    public enum Kind { ITEM, GEAR, LAMP }

    /**
     * One rolled reward: a catalog item (material or signature), a piece of set gear with affixes
     * (possibly unidentified), or an experience bottle ({@code item} is plain, greater or grand).
     */
    public record Drop(Kind kind, String item, int amount, int affixes, boolean unidentified) {
        static Drop item(String item, int amount) { return new Drop(Kind.ITEM, item, amount, 0, false); }
        static Drop lamp(String grade, int amount) { return new Drop(Kind.LAMP, grade, amount, 0, false); }
    }

    /** Run grades from the score, worst to best. */
    public enum Grade {
        D(0), C(140), B(180), A(220), S(255), S_PLUS(285);

        private final int threshold;

        Grade(int threshold) { this.threshold = threshold; }

        public String title() { return this == S_PLUS ? "S+" : name(); }

        public static Grade of(int score) {
            Grade grade = D;
            for (Grade candidate : values()) if (score >= candidate.threshold) grade = candidate;
            return grade;
        }
    }

    /** The run's score: speed, survival and exploration, each out of 100. */
    public record Score(int speed, int survival, int exploration) {
        public int total() { return speed + survival + exploration; }
        public Grade grade() { return Grade.of(total()); }

        /**
         * Speed is full within half the time limit and falls to 40 at the limit. Survival loses 25 per
         * death per player. Exploration starts at 40 and fills with the secrets found and optional
         * rooms cleared (full when the map has neither).
         */
        public static Score of(long seconds, long limitSeconds, int deaths, int partySize, int found, int findable) {
            double used = limitSeconds <= 0 ? 1 : seconds / (double) limitSeconds;
            int speed = used <= .5 ? 100 : (int) Math.round(Math.max(40, 100 - 60 * (used - .5) / .5));
            int survival = (int) Math.round(Math.max(0, 100 - deaths * 25.0 / Math.max(1, partySize)));
            int exploration = findable <= 0 ? 100 : (int) Math.round(40 + 60 * Math.min(1, found / (double) findable));
            return new Score(speed, survival, exploration);
        }
    }

    private ExpeditionLoot() { }

    /** How much harder tiers improve the odds: Normal 1, Heroic 1.35, Mythic 1.7, keys 0.05 more per level. */
    public static double luck(String tier, int keyLevel) {
        double luck = switch (tier) {
            case "heroic" -> 1.35;
            case "mythic", "keystone" -> 1.7;
            default -> 1.0;
        };
        return luck + (keyLevel > 0 ? .05 * keyLevel : 0);
    }

    /** A rare drop's chance on this tier: harder tiers raise it with their luck. */
    public static double rareChance(double chance, double luck) { return Math.min(1, chance * Math.max(1, luck)); }

    /** The most item levels a reward can gain over its catalog level. */
    public static final int MAX_ITEM_LEVEL_BONUS = 40;

    /**
     * Item levels a piece of gear gains over its catalog level: Heroic adds 6, Mythic 12 and each key
     * level 2 more, and a player above the expedition's level gets 1 for every two levels (at most 15).
     */
    public static int itemLevelBonus(String tier, int keyLevel, int expeditionLevel, int playerLevel) {
        int difficulty = switch (tier) {
            case "heroic" -> 6;
            case "mythic", "keystone" -> 12 + 2 * Math.max(0, keyLevel);
            default -> 0;
        };
        return Math.min(MAX_ITEM_LEVEL_BONUS, difficulty + Math.clamp((playerLevel - expeditionLevel) / 2, 0, 15));
    }

    /**
     * A stack of rewards for one player: more on harder tiers (by {@code luck}) and 1% more per level
     * above the expedition, at most half again. A single item stays single, so gear and signatures never double.
     */
    public static int scaled(int amount, double luck, int expeditionLevel, int playerLevel) {
        if (amount < 2) return amount;
        double level = 1 + Math.clamp((playerLevel - expeditionLevel) * .01, 0D, .5D);
        return Math.min(64, (int) Math.round(amount * Math.max(1, luck) * level));
    }

    /**
     * One possible drop of a source: {@code chance} (0-1, already scaled by luck) of {@code minimum}
     * to {@code maximum} of an item. {@link #GEAR} stands for a random set piece and
     * {@link #SIGNATURE} for a boss signature.
     */
    public record Line(Kind kind, String item, int minimum, int maximum, double chance, int minimumAffixes, int maximumAffixes) {
        static Line item(String item, int minimum, int maximum, double chance) { return new Line(Kind.ITEM, item, minimum, maximum, Math.min(1, chance), 0, 0); }
        static Line lamp(String grade, int minimum, int maximum, double chance) { return new Line(Kind.LAMP, grade, minimum, maximum, Math.min(1, chance), 0, 0); }
        static Line gear(int minimumAffixes, int maximumAffixes, double chance) { return new Line(Kind.GEAR, GEAR, 1, 1, Math.min(1, chance), minimumAffixes, maximumAffixes); }
        static Line signature(double chance) { return new Line(Kind.ITEM, SIGNATURE, 1, 1, Math.min(1, chance), 0, 0); }
    }

    public static final String GEAR = "@gear";
    public static final String SIGNATURE = "@signature";

    /** Everything an enemy, crate, chest or secret cache can give, with its odds. */
    public static List<Line> lines(Table table, Source source, double luck) {
        List<Line> lines = new ArrayList<>();
        switch (source) {
            case NORMAL -> {
                lines.add(Line.item(table.material(0), 1, 2, .20 * luck));
                lines.add(Line.gear(1, 1, .008 * luck));
                lines.add(Line.lamp("plain", 1, 1, .01 * luck));
            }
            case ELITE -> {
                lines.add(Line.item(table.material(0), 1, 3, .70));
                lines.add(Line.item(table.material(1), 1, 1, .20 * luck));
                lines.add(Line.gear(1, 2, .05 * luck));
                lines.add(Line.lamp("plain", 1, 1, .05 * luck));
            }
            case MINIBOSS -> {
                lines.add(Line.item(table.material(0), 2, 4, 1));
                lines.add(Line.item(table.material(1), 1, 2, .60 * luck));
                lines.add(Line.item(table.material(2), 1, 1, .15 * luck));
                lines.add(Line.gear(2, 2, .25 * luck));
                lines.add(Line.lamp("greater", 1, 1, .10 * luck));
            }
            case BOSS -> {
                lines.add(Line.item(table.material(1), 1, 3, 1));
                lines.add(Line.item(table.material(2), 1, 1, .35 * luck));
                lines.add(Line.signature(.04 * luck));
            }
            case CRATE -> {
                lines.add(Line.item(table.material(0), 2, 4, 1));
                lines.add(Line.item(table.material(1), 1, 1, .25 * luck));
                lines.add(Line.lamp("plain", 1, 1, .20));
            }
            case CHEST -> {
                lines.add(Line.item(table.material(0), 3, 5, 1));
                lines.add(Line.item(table.material(1), 1, 2, .50 * luck));
                lines.add(Line.item(table.material(2), 1, 1, .08 * luck));
                lines.add(Line.gear(1, 2, .10 * luck));
                lines.add(Line.lamp("plain", 1, 2, .30));
                lines.add(Line.lamp("greater", 1, 1, .05 * luck));
            }
            case SECRET -> {
                lines.add(Line.item(table.material(1), 1, 2, 1));
                lines.add(Line.item(table.material(2), 1, 1, .25 * luck));
                lines.add(Line.gear(2, 2, .35 * luck));
                lines.add(Line.lamp("greater", 1, 1, .25 * luck));
                lines.add(Line.signature(.015 * luck));
            }
        }
        lines.removeIf(line -> line.item() == null);
        return List.copyOf(lines);
    }

    /** What an enemy, crate, chest or secret cache gives. Reward chests use {@link #chest}. */
    public static List<Drop> roll(Table table, Source source, double luck, Random random) {
        List<Drop> drops = new ArrayList<>();
        for (Line line : lines(table, source, luck)) {
            if (random.nextDouble() >= line.chance()) continue;
            int amount = between(random, line.minimum(), line.maximum());
            drops.add(switch (line.item()) {
                case GEAR -> gear(table, random, between(random, line.minimumAffixes(), line.maximumAffixes()), .5);
                case SIGNATURE -> signature(table, random);
                default -> new Drop(line.kind(), line.item(), amount, 0, false);
            });
        }
        drops.removeIf(drop -> drop == null || drop.item() == null);
        return merge(drops);
    }

    /**
     * An end-of-run chest: more rolls and better odds for a better grade. The bonus chest (bought
     * with Marks) has two more rolls, a grade's better odds and always one piece of set gear.
     */
    public static List<Drop> chest(Table table, boolean bonus, Grade grade, double luck, Random random) {
        int rank = grade.ordinal() + (bonus ? 2 : 0); // D 0 .. S+ 5, a bonus chest up to 7.
        int rolls = 2 + grade.ordinal() + (bonus ? 2 : 0);
        double rare = luck * (1 + rank * .25);
        List<Drop> drops = new ArrayList<>();
        if (bonus) drops.add(gear(table, random, 3, .5));
        for (int roll = 0; roll < rolls; roll++) {
            double[] weights = {40, 22, 6 * rare, 12 * rare, 10, 4 * rare, rare, .4 * rare};
            double pick = random.nextDouble() * sum(weights);
            int slot = 0;
            while (slot < weights.length - 1 && pick >= weights[slot]) pick -= weights[slot++];
            drops.add(switch (slot) {
                case 0 -> Drop.item(table.material(0), between(random, 2, 5));
                case 1 -> Drop.item(table.material(1), between(random, 1, 3));
                case 2 -> Drop.item(table.material(2), 1);
                case 3 -> gear(table, random, Math.min(3, 1 + rank / 2), .5);
                case 4 -> Drop.lamp("plain", between(random, 1, 3));
                case 5 -> Drop.lamp("greater", 1);
                case 6 -> Drop.lamp("grand", 1);
                default -> signature(table, random);
            });
        }
        drops.removeIf(drop -> drop == null || drop.item() == null);
        return merge(drops);
    }

    /** What forging one set piece costs: the expedition's materials and some Marks. */
    public record Cost(java.util.Map<String, Integer> items, long marks) {
        public Cost { items = java.util.Map.copyOf(items); }
    }

    /**
     * The Expedition Forge's price for a set piece: 16 common and 4 uncommon materials, or for the
     * chest piece and the weapon 24 common, 6 uncommon and one rare, plus Marks by the expedition's level.
     */
    public static Cost forgeCost(Table table, String piece, int level) {
        int index = table.gear().indexOf(piece);
        if (index < 0 || table.materials().isEmpty()) throw new IllegalArgumentException("Not a set piece of this expedition: " + piece);
        boolean major = index == 1 || index == table.gear().size() - 1;
        java.util.Map<String, Integer> items = new java.util.LinkedHashMap<>();
        items.merge(table.material(0), major ? 24 : 16, Integer::sum);
        if (table.materials().size() > 1) items.merge(table.material(1), major ? 6 : 4, Integer::sum);
        if (major && table.materials().size() > 2) items.merge(table.material(2), 1, Integer::sum);
        return new Cost(items, Math.round((40 + level * 6) * (major ? 1.5 : 1)));
    }

    /** The bonus chest's price in Marks: grows with the expedition's level and the tier's Marks. */
    public static long bonusPrice(int level, double tierMarks) {
        return Math.round((60 + level * 12) * Math.max(1, tierMarks));
    }

    private static Drop gear(Table table, Random random, int affixes, double unidentified) {
        if (table.gear().isEmpty()) return Drop.item(table.material(1), 1); // No set: more materials instead.
        return new Drop(Kind.GEAR, table.gear().get(random.nextInt(table.gear().size())), 1, affixes, random.nextDouble() < unidentified);
    }

    private static Drop signature(Table table, Random random) {
        if (table.signatures().isEmpty()) return Drop.item(table.material(2), 1);
        return Drop.item(table.signatures().get(random.nextInt(table.signatures().size())), 1);
    }

    private static void chance(List<Drop> drops, Random random, double chance, java.util.function.Supplier<Drop> drop) {
        if (random.nextDouble() < Math.min(1, chance)) drops.add(drop.get());
    }

    private static int between(Random random, int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private static double sum(double[] values) {
        double sum = 0;
        for (double value : values) sum += value;
        return sum;
    }

    /** Stacks of the same item and kind are merged; gear never stacks. */
    static List<Drop> merge(List<Drop> drops) {
        List<Drop> merged = new ArrayList<>();
        for (Drop drop : drops) {
            int match = -1;
            if (drop.kind() != Kind.GEAR) for (int index = 0; index < merged.size(); index++)
                if (merged.get(index).kind() == drop.kind() && merged.get(index).item().equals(drop.item())) match = index;
            if (match < 0) merged.add(drop);
            else merged.set(match, new Drop(drop.kind(), drop.item(), Math.min(64, merged.get(match).amount() + drop.amount()), 0, false));
        }
        return merged;
    }
}

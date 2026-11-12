package net.nerdsmp.paper.quest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One story quest offered by an NPC. Quests chain through {@code requires}; a quest is offered once its
 * prerequisites are complete and the player's adventurer level reaches {@code minimumLevel}. A
 * {@code daily} quest can be done again every UTC day.
 */
public record QuestDefinition(
    String id,
    String chain,
    String title,
    String giver,
    String turnIn,
    int minimumLevel,
    List<String> requires,
    List<String> offer,
    List<String> complete,
    List<Objective> objectives,
    Rewards rewards,
    boolean daily
) {
    public static final int MAX_OBJECTIVES = 6;

    public QuestDefinition {
        if (!validId(id)) throw new IllegalArgumentException("Quest ids use 1-48 lowercase letters, numbers, underscores or dashes: " + id);
        if (!validId(chain)) throw new IllegalArgumentException("Quest " + id + " has an invalid chain id");
        if (title == null || title.isBlank() || title.length() > 48) throw new IllegalArgumentException("Quest " + id + " needs a 1-48 character title");
        if (!validId(giver) || !validId(turnIn)) throw new IllegalArgumentException("Quest " + id + " needs NPC ids for its giver and turn-in");
        if (minimumLevel < 1 || minimumLevel > 100) throw new IllegalArgumentException("Quest " + id + " level must be 1-100");
        requires = List.copyOf(requires);
        offer = List.copyOf(offer);
        complete = List.copyOf(complete);
        objectives = List.copyOf(objectives);
        Objects.requireNonNull(rewards, "rewards");
        if (requires.contains(id)) throw new IllegalArgumentException("Quest " + id + " cannot require itself");
        if (offer.isEmpty() || complete.isEmpty()) throw new IllegalArgumentException("Quest " + id + " needs offer and complete lines");
        if (objectives.isEmpty() || objectives.size() > MAX_OBJECTIVES)
            throw new IllegalArgumentException("Quest " + id + " needs 1-" + MAX_OBJECTIVES + " objectives");
        for (String line : offer) checkLine(id, line);
        for (String line : complete) checkLine(id, line);
    }

    private static void checkLine(String id, String line) {
        if (line == null || line.isBlank() || line.length() > 240) throw new IllegalArgumentException("Quest " + id + " has an empty or overlong line");
    }

    public static boolean validId(String id) {
        return id != null && id.matches("[a-z0-9][a-z0-9_-]{0,47}");
    }

    public enum Type { KILL, HARVEST, DIG, VISIT, TALK, DELIVER, CLEAR }

    /**
     * {@code targets} are mob ids ({@code custom:x}, {@code vanilla:zombie}) for KILL, material names for
     * HARVEST, DIG and DELIVER (DIG may leave them empty for any soil), NPC ids for TALK and expedition ids for
     * CLEAR (finish a run of that expedition on any tier). {@code zone}
     * restricts where the objective counts; VISIT needs it.
     */
    public record Objective(Type type, Set<String> targets, String zone, int count, String description, List<String> dialogue) {
        public Objective(Type type, Set<String> targets, String zone, int count, String description) {
            this(type, targets, zone, count, description, List.of());
        }
        public Objective {
            Objects.requireNonNull(type, "type");
            targets = Set.copyOf(targets);
            dialogue = List.copyOf(dialogue);
            if (!dialogue.isEmpty() && type != Type.TALK) throw new IllegalArgumentException("Only talk objectives have dialogue");
            if (dialogue.size() > 3) throw new IllegalArgumentException("Talk objectives support at most three dialogue lines");
            dialogue.forEach(line -> checkLine("talk objective", line));
            if (zone != null && !validId(zone)) throw new IllegalArgumentException("Invalid objective zone " + zone);
            if (count < 1 || count > 500) throw new IllegalArgumentException("Objective counts must be 1-500");
            if (description == null || description.isBlank() || description.length() > 80)
                throw new IllegalArgumentException("Objectives need a 1-80 character description");
            switch (type) {
                case VISIT -> { if (zone == null || count != 1) throw new IllegalArgumentException("Visit objectives need a zone and a count of 1"); }
                case DIG -> { }
                default -> { if (targets.isEmpty()) throw new IllegalArgumentException(type + " objectives need at least one target"); }
            }
            if (type == Type.TALK && count != 1) throw new IllegalArgumentException("Talk objectives use a count of 1");
            for (String target : targets) {
                boolean valid = switch (type) {
                    case KILL -> target.matches("(custom|vanilla):[a-z0-9_-]{1,64}");
                    case TALK, CLEAR -> validId(target);
                    default -> target.matches("[A-Z0-9_]{1,64}");
                };
                if (!valid) throw new IllegalArgumentException("Invalid " + type + " objective target " + target);
            }
        }

        public boolean matches(String target) {
            return targets.isEmpty() || targets.contains(target);
        }
    }

    /** Items are {@code material:<name>} or {@code item:<custom item id>}. */
    public record Rewards(long marks, Map<String, Long> experience, Map<String, Integer> reputation, List<ItemReward> items) {
        public Rewards {
            experience = Map.copyOf(experience);
            reputation = Map.copyOf(reputation);
            items = List.copyOf(items);
            if (marks < 0 || marks > 100_000) throw new IllegalArgumentException("Quest Marks must be 0-100,000");
            experience.forEach((skill, xp) -> { if (xp < 1 || xp > 1_000_000) throw new IllegalArgumentException("Quest XP must be 1-1,000,000"); });
            reputation.forEach((faction, points) -> { if (points == 0 || Math.abs(points) > 1000) throw new IllegalArgumentException("Quest reputation must be non-zero and within 1,000"); });
            if (items.size() > 8) throw new IllegalArgumentException("A quest gives at most eight item stacks");
        }
    }

    public record ItemReward(String kind, String id, int amount, String name, List<String> lore) {
        public ItemReward(String kind, String id, int amount) { this(kind, id, amount, null, List.of()); }
        public ItemReward {
            if (!kind.equals("material") && !kind.equals("item")) throw new IllegalArgumentException("Reward items are material:<name> or item:<id>");
            if (id == null || !id.matches("[a-z0-9_]{1,64}")) throw new IllegalArgumentException("Invalid reward item " + id);
            if (amount < 1 || amount > 64) throw new IllegalArgumentException("Reward stacks hold 1-64 items");
            lore = List.copyOf(lore);
            if (name != null) checkLine("reward name", name);
            if (lore.size() > 4) throw new IllegalArgumentException("Reward items support at most four lore lines");
            lore.forEach(line -> checkLine("reward lore", line));
            if (kind.equals("item") && (name != null || !lore.isEmpty()))
                throw new IllegalArgumentException("Custom items keep their catalog name and lore");
        }
    }
}

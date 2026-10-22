package net.nerdsmp.paper.quest;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One account's quest log: objective counters for accepted quests and the ids of finished ones. */
public final class QuestProgress {
    public enum Status { LOCKED, AVAILABLE, ACTIVE, READY, COMPLETED }

    private final Map<String, int[]> active = new LinkedHashMap<>();
    private final Set<String> completed = new LinkedHashSet<>();

    public Map<String, int[]> active() { return active; }
    public Set<String> completed() { return completed; }

    public Status status(QuestDefinition quest, int adventurerLevel) {
        if (completed.contains(quest.id())) return Status.COMPLETED;
        int[] counts = active.get(quest.id());
        if (counts != null) return done(quest, counts) ? Status.READY : Status.ACTIVE;
        if (adventurerLevel < quest.minimumLevel() || !completed.containsAll(quest.requires())) return Status.LOCKED;
        return Status.AVAILABLE;
    }

    public boolean accept(QuestDefinition quest) {
        if (completed.contains(quest.id()) || active.containsKey(quest.id())) return false;
        active.put(quest.id(), new int[quest.objectives().size()]);
        return true;
    }

    public int[] counts(QuestDefinition quest) {
        int[] counts = active.get(quest.id());
        if (counts == null) return null;
        if (counts.length != quest.objectives().size()) {
            // The quest was edited after it was accepted: keep what still lines up.
            counts = Arrays.copyOf(counts, quest.objectives().size());
            active.put(quest.id(), counts);
        }
        return counts;
    }

    /** Adds progress and reports whether the objective just finished. Deliver objectives are checked at turn-in. */
    public boolean advance(QuestDefinition quest, int objective, int amount) {
        int[] counts = counts(quest);
        if (counts == null || amount <= 0) return false;
        int target = quest.objectives().get(objective).count();
        if (counts[objective] >= target) return false;
        counts[objective] = Math.min(target, counts[objective] + amount);
        return counts[objective] >= target;
    }

    public static boolean done(QuestDefinition quest, int[] counts) {
        for (int i = 0; i < quest.objectives().size(); i++) {
            if (quest.objectives().get(i).type() == QuestDefinition.Type.DELIVER) continue;
            if (i >= counts.length || counts[i] < quest.objectives().get(i).count()) return false;
        }
        return true;
    }

    public void complete(QuestDefinition quest) {
        active.remove(quest.id());
        completed.add(quest.id());
    }

    public boolean abandon(String id) {
        return active.remove(id) != null;
    }
}

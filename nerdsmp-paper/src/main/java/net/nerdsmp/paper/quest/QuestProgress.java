package net.nerdsmp.paper.quest;

import java.time.LocalDate;
import java.time.ZoneOffset;
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
    /** Daily quests: the UTC day each was last handed in. */
    private final Map<String, LocalDate> dailyDone = new LinkedHashMap<>();

    public Map<String, int[]> active() { return active; }
    public Set<String> completed() { return completed; }
    public Map<String, LocalDate> dailyDone() { return dailyDone; }

    static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }

    public Status status(QuestDefinition quest, int adventurerLevel) {
        return status(quest, adventurerLevel, today());
    }

    public Status status(QuestDefinition quest, int adventurerLevel, LocalDate today) {
        if (quest.daily() ? today.equals(dailyDone.get(quest.id())) && !active.containsKey(quest.id()) : completed.contains(quest.id()))
            return Status.COMPLETED;
        int[] counts = active.get(quest.id());
        if (counts != null) return done(quest, counts) ? Status.READY : Status.ACTIVE;
        if (adventurerLevel < quest.minimumLevel() || !completed.containsAll(quest.requires())) return Status.LOCKED;
        return Status.AVAILABLE;
    }

    public boolean accept(QuestDefinition quest) {
        return accept(quest, today());
    }

    public boolean accept(QuestDefinition quest, LocalDate today) {
        boolean done = quest.daily() ? today.equals(dailyDone.get(quest.id())) : completed.contains(quest.id());
        if (done || active.containsKey(quest.id())) return false;
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

    /** Adds progress and reports whether the objective just finished. */
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
            if (counts == null || i >= counts.length || counts[i] < quest.objectives().get(i).count()) return false;
        }
        return true;
    }

    public void complete(QuestDefinition quest) {
        complete(quest, today());
    }

    public void complete(QuestDefinition quest, LocalDate today) {
        active.remove(quest.id());
        if (quest.daily()) dailyDone.put(quest.id(), today);
        else completed.add(quest.id());
    }

    public boolean abandon(String id) {
        return active.remove(id) != null;
    }
}

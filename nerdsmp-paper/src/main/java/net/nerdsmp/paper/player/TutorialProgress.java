package net.nerdsmp.paper.player;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.storage.PlayerDataStore;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read checkpoints, not gameplay achievements. Replaying never pays currency, items, or XP. */
public final class TutorialProgress {
    public static final List<String> LESSONS = List.of("welcome", "home", "skills", "equipment", "talents", "economy", "adventures", "community");
    private final PlayerDataStore data;
    public TutorialProgress(PlayerDataStore data) { this.data = data; }
    public State state(AccountId actor) {
        return data.read(actor, yaml -> new State(new HashSet<>(yaml.getStringList("tutorial.v1.read")), yaml.getBoolean("tutorial.v1.paused"), yaml.getBoolean("tutorial.v1.invited")));
    }
    public void acknowledge(AccountId actor, String lesson) {
        if (!LESSONS.contains(lesson)) throw new IllegalArgumentException("Unknown tutorial lesson");
        Set<String> read = new HashSet<>(state(actor).read()); read.add(lesson);
        data.update(actor, yaml -> { yaml.set("tutorial.v1.read", LESSONS.stream().filter(read::contains).toList()); yaml.set("tutorial.v1.invited", true); });
    }
    public void pause(AccountId actor, boolean paused) { data.update(actor, yaml -> { yaml.set("tutorial.v1.paused", paused); yaml.set("tutorial.v1.invited", true); }); }
    public void invite(AccountId actor) { data.update(actor, yaml -> yaml.set("tutorial.v1.invited", true)); }
    public void restart(AccountId actor) { data.update(actor, yaml -> { yaml.set("tutorial.v1.read", List.of()); yaml.set("tutorial.v1.paused", false); yaml.set("tutorial.v1.invited", true); }); }
    public record State(Set<String> read, boolean paused, boolean invited) {
        public State { read = read.stream().filter(LESSONS::contains).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
        public int next() { for (int i = 0; i < LESSONS.size(); i++) if (!read.contains(LESSONS.get(i))) return i; return LESSONS.size() - 1; }
        public boolean complete() { return read.size() == LESSONS.size(); }
    }
}

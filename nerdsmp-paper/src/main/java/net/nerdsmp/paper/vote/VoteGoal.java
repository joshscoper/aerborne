package net.nerdsmp.paper.vote;

import org.bukkit.configuration.ConfigurationSection;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The server's daily vote goal: paid votes from everyone today, counted towards {@code goal}. It starts again at
 * midnight. Reaching it rewards everyone who voted that day, and anyone who votes later the same day too.
 */
final class VoteGoal {
    /** What one more vote did: the count after it, whether it reached the goal, and any quarter it passed (25, 50 or 75). */
    record Step(int votes, int goal, boolean reachedNow, boolean reached, int quarter) {}

    private String day = "";
    private int votes;
    private final Set<String> voters = new LinkedHashSet<>();
    private boolean reached;

    void load(ConfigurationSection section) {
        if (section == null) return;
        day = section.getString("day", "");
        votes = section.getInt("votes");
        voters.addAll(section.getStringList("voters"));
        reached = section.getBoolean("reached");
    }

    void save(ConfigurationSection section) {
        section.set("day", day);
        section.set("votes", votes);
        section.set("voters", new ArrayList<>(voters));
        section.set("reached", reached);
    }

    /** Counts one paid vote by {@code voter} (an account id) on {@code today}. */
    Step add(String voter, LocalDate today, int goal) {
        roll(today);
        int before = votes;
        votes++;
        voters.add(voter);
        boolean reachedNow = goal > 0 && !reached && votes >= goal;
        if (reachedNow) reached = true;
        int quarter = 0;
        if (goal > 0 && !reached) {
            for (int q = 3; q >= 1; q--) {
                if (before * 4 < goal * q && votes * 4 >= goal * q) { quarter = q * 25; break; }
            }
        }
        return new Step(votes, goal, reachedNow, reached, quarter);
    }

    /** Today's count, after starting a new day if the date has moved on. */
    int votes(LocalDate today) {
        roll(today);
        return votes;
    }

    boolean reached(LocalDate today) {
        roll(today);
        return reached;
    }

    /** Everyone who has voted today, as account ids. */
    List<String> voters(LocalDate today) {
        roll(today);
        return List.copyOf(voters);
    }

    String day() { return day; }

    private void roll(LocalDate today) {
        if (today.toString().equals(day)) return;
        day = today.toString();
        votes = 0;
        voters.clear();
        reached = false;
    }
}

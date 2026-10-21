package net.nerdsmp.paper.campaign.season;

import net.nerdsmp.paper.campaign.season.SeasonDefinition.Goal;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Milestone;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Reward;
import net.nerdsmp.paper.campaign.season.SeasonDefinition.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Checks a loaded season against what the server knows. Errors block loading; warnings are advice. */
public final class SeasonValidator {
    public static final Set<String> FEATURES = Set.of("world-events", "nether", "end");

    public interface Catalog {
        boolean material(String key);
        boolean mob(String id);
        boolean item(String id);
        boolean effect(String key);
        boolean expedition(String id);
    }

    public record Issue(boolean error, String message) { }

    public static List<Issue> validate(SeasonDefinition season, Catalog catalog) {
        List<Issue> issues = new ArrayList<>();
        if (season.playerDailyCap() < 1) issues.add(new Issue(true, "player-daily-cap must be at least 1."));
        for (Goal goal : season.goals().values()) {
            String name = "Goal " + goal.id();
            if (goal.target() < 1) issues.add(new Issue(true, name + ": target must be at least 1."));
            if (goal.dailyCap() != null && goal.dailyCap() < 1) issues.add(new Issue(true, name + ": daily-cap must be at least 1."));
            if (goal.sources().isEmpty()) issues.add(new Issue(false, name + " has no sources, so only staff can move it."));
            if (!catalog.material(goal.icon())) issues.add(new Issue(false, name + ": unknown icon " + goal.icon() + "."));
            for (Source source : goal.sources()) {
                for (String filter : source.filters()) {
                    boolean known = switch (source.type()) {
                        case MINE, SMELT, CRAFT, FISH, CONTRIBUTE -> catalog.material(filter);
                        case KILL, BOSS -> filter.equals("hostile") || catalog.mob(filter);
                        case EXPEDITION -> catalog.expedition(filter);
                    };
                    if (!known) issues.add(new Issue(true, name + " " + source.type().key() + " source: unknown '" + filter + "'."));
                }
                if (source.type() != SeasonDefinition.SourceType.EXPEDITION && !source.tierMultipliers().isEmpty())
                    issues.add(new Issue(false, name + ": tier multipliers only apply to expedition sources."));
            }
        }
        for (Milestone milestone : season.milestones().values()) {
            String name = "Milestone " + milestone.id();
            milestone.requires().forEach((goal, amount) -> {
                Goal target = season.goals().get(goal);
                if (target == null) issues.add(new Issue(true, name + " requires unknown goal " + goal + "."));
                else if (amount > target.target()) issues.add(new Issue(false, name + " needs " + amount + " " + goal + ", above the goal's target of " + target.target() + "."));
                if (amount < 1) issues.add(new Issue(true, name + ": thresholds must be at least 1."));
            });
            if (milestone.rewards().isEmpty()) issues.add(new Issue(false, name + " has no rewards."));
            for (Reward reward : milestone.rewards()) {
                switch (reward) {
                    case Reward.Buff buff -> {
                        if (!catalog.effect(buff.effect())) issues.add(new Issue(true, name + ": unknown effect " + buff.effect() + "."));
                        if (buff.amplifier() < 0 || buff.amplifier() > 4) issues.add(new Issue(true, name + ": buff amplifier must be 0-4."));
                    }
                    case Reward.Boost boost -> {
                        if (boost.percent() < 1 || boost.percent() > 200) issues.add(new Issue(true, name + ": boost percent must be 1-200."));
                    }
                    case Reward.Item item -> {
                        if (!catalog.item(item.item()) && !catalog.material(item.item())) issues.add(new Issue(true, name + ": unknown item " + item.item() + "."));
                        if (item.amount() < 1 || item.amount() > 64) issues.add(new Issue(true, name + ": item amount must be 1-64."));
                    }
                    case Reward.Marks marks -> {
                        if (marks.amount() < 1 || marks.amount() > 100_000) issues.add(new Issue(true, name + ": Marks must be 1-100000."));
                    }
                    case Reward.Unlock unlock -> {
                        if (unlock.expedition() != null && !catalog.expedition(unlock.expedition()))
                            issues.add(new Issue(true, name + " unlocks unknown expedition " + unlock.expedition() + "."));
                        if (unlock.feature() != null && !FEATURES.contains(unlock.feature()))
                            issues.add(new Issue(true, name + " unlocks unknown feature " + unlock.feature() + ". Use " + String.join(", ", FEATURES) + "."));
                    }
                    case Reward.Broadcast broadcast -> { }
                    case Reward.Command command -> { }
                }
            }
        }
        long unlocks = season.milestones().values().stream().flatMap(milestone -> milestone.rewards().stream())
            .filter(reward -> reward instanceof Reward.Unlock unlock && unlock.expedition() != null)
            .map(reward -> ((Reward.Unlock) reward).expedition()).count();
        long distinct = season.milestones().values().stream().flatMap(milestone -> milestone.rewards().stream())
            .filter(reward -> reward instanceof Reward.Unlock unlock && unlock.expedition() != null)
            .map(reward -> ((Reward.Unlock) reward).expedition()).distinct().count();
        if (unlocks != distinct) issues.add(new Issue(false, "An expedition is unlocked by more than one milestone; the first one listed seals it."));
        return List.copyOf(issues);
    }

    public static boolean loadable(List<Issue> issues) { return issues.stream().noneMatch(Issue::error); }
}

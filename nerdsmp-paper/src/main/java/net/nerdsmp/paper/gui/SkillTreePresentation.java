package net.nerdsmp.paper.gui;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.api.model.SkillTreeCatalog;
import net.nerdsmp.api.service.ProgressionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static net.nerdsmp.api.service.ProgressionService.ProgressionSnapshot;
import static net.nerdsmp.api.model.SkillTreeCatalog.*;

/** Pure tree decisions and geometry; tested without a running Bukkit server. */
public final class SkillTreePresentation {
    public enum State { UNLOCKED, AVAILABLE, LEVEL_LOCKED, PATH_LOCKED, NO_POINTS }
    public record NodeView(State state, List<String> lore) {
        public NodeView { lore = List.copyOf(lore); }
    }
    private SkillTreePresentation() { }
    public static int branchSlot(int branch) { checkBranch(branch); return 1 + branch * 2; }
    public static int nodeSlot(int branch, int tier) {
        checkBranch(branch);
        if (tier < 1 || tier > 3) throw new IllegalArgumentException("Talent tier must be 1–3");
        return 10 + branch * 2 + (tier - 1) * 18;
    }
    public static int linkSlot(int branch, int precedingTier) {
        if (precedingTier < 1 || precedingTier > 2) throw new IllegalArgumentException("Connector must follow tier 1 or 2");
        return nodeSlot(branch, precedingTier) + 9;
    }
    private static void checkBranch(int branch) {
        if (branch < 0 || branch > 3) throw new IllegalArgumentException("Talent branch must be 0–3");
    }
    public static List<String> overview(ProgressionSnapshot snapshot, Skill skill) {
        var progress = snapshot.skills().getOrDefault(skill, new ProgressionService.SkillProgress(0, 0));
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Unspent points</gray> <yellow>" + snapshot.talentPointsAvailable(skill) + "</yellow>");
        lore.add("<gray>Earned</gray> <white>" + snapshot.talentPointsEarned(skill) + "/" + maximumPoints() + "</white>"
            + " <dark_gray>|</dark_gray> <gray>Spent</gray> <white>" + snapshot.talentPointsSpent(skill) + "</white>");
        var next = nextPointLevel(progress.level());
        if (next.isPresent()) {
            long remaining = Math.max(0, ProgressionService.experienceForLevel(next.getAsInt()) - progress.experience());
            lore.add("<aqua>Next point: " + skill.displayName() + " " + next.getAsInt() + "</aqua>");
            lore.add("<gray>" + String.format(Locale.ROOT, "%,d", remaining) + " skill XP to go.</gray>");
        } else lore.add("<gold>All eight talent points earned.</gold>");
        lore.add("");
        lore.add("<gray>Eight points. Twelve talents. Build your path.</gray>");
        lore.add("<dark_gray>Each talent costs one point for this skill.</dark_gray>");
        if(skill==Skill.FORESTRY) {
            lore.add("");
            lore.add("<green>Tree felling: up to "+net.nerdsmp.paper.gathering.ForestryRules.capacity(progress.level())+" logs</green>");
            lore.add("<gray>Capacity at 0 / 10 / 20 / 35 / 50:</gray>");
            lore.add("<white>16 / 32 / 64 / 128 / 192 logs</white>");
            lore.add("<gray>Wildwood recipes unlock at 10, 20 and 35.</gray>");
            lore.add("<dark_gray>Sneak for single logs; replanting uses your saplings.</dark_gray>");
        }
        return List.copyOf(lore);
    }
    public static List<String> branch(ProgressionSnapshot snapshot, Skill skill, TalentBranch branch) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + branch.description() + "</gray>");
        double current = branch.nodes().stream().filter(node -> snapshot.talents().contains(node.id())).mapToDouble(TalentNode::value).sum();
        long count = branch.nodes().stream().filter(node -> snapshot.talents().contains(node.id())).count();
        lore.add("<gray>Your path</gray> <green>+" + shown(current) + "%</green> <dark_gray>(" + count + "/3 talents)</dark_gray>");
        lore.add("<gray>Complete path</gray> <white>+" + shown(branch.maximumValue()) + "%</white> <dark_gray>(3 points)</dark_gray>");
        if (branch.nodes().getFirst().stat().equals("job-marks-percent")) lore.add("<dark_gray>Daily work limits still apply.</dark_gray>");
        if (skill == Skill.FARMING && branch.id().equals("bumper_crop")) {
            lore.add("");
            lore.add("<aqua>Right-click ripe crops with any hoe.</aqua>");
            lore.add("<gray>Harvests and replants without crouching.</gray>");
            lore.add("<gray>One seed is kept from the harvest</gray>");
            lore.add("<gray>or taken from your ordinary inventory.</gray>");
            lore.add("<dark_gray>Replanting is available without talents.</dark_gray>");
        }
        if (skill == Skill.FORESTRY) {
            lore.add("<gray>Use an axe and mix tree species</gray>");
            lore.add("<gray>to build Timber Rhythm.</gray>");
        }
        if (branch.nodes().getFirst().stat().equals("skill-xp-percent")) {
            lore.add("<dark_gray>XP bonuses help you reach skill level 50.</dark_gray>");
            if (snapshot.skills().getOrDefault(skill, new ProgressionService.SkillProgress(0, 0)).level() >= 50)
                lore.add("<yellow>This skill is capped; more XP has no effect.</yellow>");
        }
        return List.copyOf(lore);
    }
    public static NodeView node(ProgressionSnapshot snapshot, TalentNode node) {
        var progress = snapshot.skills().getOrDefault(node.skill(), new ProgressionService.SkillProgress(0, 0));
        boolean owned = snapshot.talents().contains(node.id());
        boolean level = progress.level() >= node.requiredLevel();
        boolean prerequisite = node.previousId() == null || snapshot.talents().contains(node.previousId());
        State state = owned ? State.UNLOCKED : !level ? State.LEVEL_LOCKED : !prerequisite ? State.PATH_LOCKED
            : snapshot.talentPointsAvailable(node.skill()) > 0 ? State.AVAILABLE : State.NO_POINTS;
        TalentBranch branch = tree(node.skill()).branches().stream().filter(b -> b.id().equals(node.branchId())).findFirst().orElseThrow();
        List<String> lore = new ArrayList<>();
        lore.add("<white>" + node.effect() + "</white>");
        lore.add("<gray>Path total at this tier</gray> <aqua>+" + shown(branch.valueThrough(node.tier())) + "%</aqua>");
        lore.add("");
        lore.add("<gray>Cost</gray> <yellow>1 " + node.skill().displayName() + " point</yellow>");
        lore.add((level ? "<green>" : "<red>") + node.skill().displayName() + " " + node.requiredLevel()
            + (level ? " reached</green>" : " required</red>"));
        if (node.previousId() != null) {
            String previous = SkillTreeCatalog.node(node.previousId()).orElseThrow().name();
            lore.add((prerequisite ? "<green>" : "<red>") + previous + (prerequisite ? " learned</green>" : " required</red>"));
        }
        boolean cappedXp = node.stat().equals("skill-xp-percent") && progress.level() >= 50;
        if (cappedXp) lore.add("<yellow>Level 50: this skill no longer gains XP.</yellow>");
        lore.add("");
        lore.add(switch (state) {
            case UNLOCKED -> cappedXp ? "<green>Learned. This skill has reached its cap.</green>" : "<green>Learned. This bonus is active.</green>";
            case AVAILABLE -> "<yellow>Click to review this choice.</yellow>";
            case LEVEL_LOCKED -> "<gray>Keep training to reach this tier.</gray>";
            case PATH_LOCKED -> "<gray>Learn the named prerequisite first.</gray>";
            case NO_POINTS -> "<red>No unspent points for this skill.</red>";
        });
        return new NodeView(state, lore);
    }
    private static String shown(double value) { return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value); }
}

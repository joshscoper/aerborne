package net.nerdsmp.api.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public final class SkillTreeCatalog {
    private static final int[] POINT_LEVELS = {5, 10, 18, 27, 37, 40, 47, 50};
    private static final int[] NODE_LEVELS = {5, 20, 35};
    private static final Map<Skill, SkillTree> TREES = buildTrees();
    private static final Map<String, TalentNode> NODES = indexNodes();

    private SkillTreeCatalog() {
    }

    public static SkillTree tree(Skill skill) {
        return TREES.get(skill);
    }

    public static Optional<TalentNode> node(String id) {
        return Optional.ofNullable(id == null ? null : NODES.get(id.toLowerCase(Locale.ROOT)));
    }

    public static List<TalentNode> nodes(Skill skill) {
        SkillTree tree = tree(skill);
        return java.util.stream.Stream.concat(tree.branches().stream().flatMap(branch -> branch.nodes().stream()), tree.junctions().stream()).toList();
    }

    public static int earnedPoints(int skillLevel) {
        int points = 0;
        for (int required : POINT_LEVELS) {
            if (skillLevel >= required) {
                points++;
            }
        }
        return points;
    }

    public static int spentPoints(Set<String> talents, Skill skill) {
        return (int) talents.stream().map(SkillTreeCatalog::node).flatMap(Optional::stream)
            .filter(node -> node.skill() == skill).count();
    }

    public static int maximumPoints() { return POINT_LEVELS.length; }

    public static List<Integer> pointLevels() {
        return java.util.Arrays.stream(POINT_LEVELS).boxed().toList();
    }

    public static OptionalInt nextPointLevel(int skillLevel) {
        return java.util.Arrays.stream(POINT_LEVELS).filter(level -> level > skillLevel).findFirst();
    }

    public static double stat(Set<String> talents, Skill skill, String stat) {
        return talents.stream().map(SkillTreeCatalog::node).flatMap(Optional::stream)
            .filter(node -> node.skill() == skill && node.stat().equals(stat))
            .mapToDouble(TalentNode::value).sum();
    }

    private static Map<Skill, SkillTree> buildTrees() {
        EnumMap<Skill, SkillTree> trees = new EnumMap<>(Skill.class);
        trees.put(Skill.MINING, tree(Skill.MINING,
            xp("ore_study", "Ore Study"),
            jobs("contract_miner", "Contract Miner"),
            special("prospector", "Prospector", "geode-find-percent", new double[]{10, 15, 25},
                "geode odds while mining")));
        trees.put(Skill.FORESTRY, tree(Skill.FORESTRY,
            xp("woodcraft", "Woodcraft"),
            jobs("timber_trade", "Timber Trade"),
            special("clean_cuts", "Clean Cuts", "extra-drop-percent", new double[]{2, 3, 5},
                "extra-log chance")));
        trees.put(Skill.FARMING, tree(Skill.FARMING,
            xp("fieldwork", "Fieldwork"),
            jobs("farmstand", "Farmstand"),
            special("bumper_crop", "Bumper Crop", "extra-drop-percent", new double[]{3, 5, 7},
                "extra-crop chance")));
        trees.put(Skill.FISHING, tree(Skill.FISHING,
            xp("watercraft", "Watercraft"),
            jobs("fish_market", "Fish Market"),
            special("lucky_line", "Lucky Line", "treasure-find-percent", new double[]{2, 3, 5},
                "bonus-treasure chance")));
        trees.put(Skill.HUNTING, tree(Skill.HUNTING,
            xp("tracking", "Tracking"),
            jobs("bounties", "Bounties"),
            special("slayer", "Slayer", "hunting-damage-percent", new double[]{3, 5, 7},
                "PvE weapon damage")));
        trees.put(Skill.EXCAVATION, tree(Skill.EXCAVATION,
            xp("surveying", "Surveying"),
            jobs("salvage_trade", "Salvage Trade"),
            special("sifter", "Sifter", "geode-find-percent", new double[]{10, 15, 25},
                "geode odds while excavating")));
        trees.put(Skill.SMITHING, tree(Skill.SMITHING,
            xp("practice", "Practice"),
            jobs("commissions", "Commissions"),
            special("tempering", "Tempering", "durability-save-percent", new double[]{3, 5, 7},
                "durability-save chance")));
        trees.put(Skill.ALCHEMY, tree(Skill.ALCHEMY,
            xp("research", "Research"),
            jobs("apothecary", "Apothecary"),
            special("careful_measure", "Careful Measure", "reagent-save-percent", new double[]{3, 5, 7},
                "reagent-save chance")));
        trees.put(Skill.BUILDING, tree(Skill.BUILDING,
            xp("site_practice", "Site Practice"),
            jobs("guild_commissions", "Guild Commissions"),
            special("sure_footing", "Sure Footing", "building-fall-reduction-percent",
                new double[]{10, 15, 25}, "fall-damage reduction while building")));
        trees.put(Skill.COOKING, tree(Skill.COOKING,
            xp("kitchen_practice", "Kitchen Practice"),
            jobs("catering", "Catering"),
            special("seasoning", "Seasoning", "meal-duration-percent", new double[]{10, 15, 25},
                "duration of meals you prepare")));
        // Enchanting earns no job Marks, so its second branch is Dust thrift instead of Commissions.
        trees.put(Skill.ENCHANTING, tree(Skill.ENCHANTING,
            xp("arcane_study", "Arcane Study"),
            special("dust_thrift", "Dust Thrift", "imbue-dust-discount-percent", new double[]{5, 8, 12},
                "Dust discount on Chosen Imbuement"),
            special("keen_insight", "Keen Insight", "research-progress-percent", new double[]{10, 15, 25},
                "Arcane Research progress")));
        for (Skill skill : Skill.values()) {
            BranchSeed seed = switch (skill) {
                case MINING -> special("deep_reserves", "Deep Reserves", "expedition-geode-percent", new double[]{15, 20, 30}, "geode odds while gathering");
                case FORESTRY -> special("careful_tools", "Careful Tools", "equipment-preservation-percent", new double[]{3, 4, 5}, "equipment durability preservation");
                case FARMING -> special("stockbreeder", "Stockbreeder", "husbandry-quality-percent", new double[]{3, 4, 5}, "chance for stronger inherited animal traits");
                case FISHING -> special("tidal_instinct", "Tidal Instinct", "fishing-luck-percent", new double[]{4, 6, 10}, "fishing luck");
                case HUNTING -> special("second_wind", "Second Wind", "life-steal-percent", new double[]{0.5, 0.75, 1}, "PvE life steal");
                case EXCAVATION -> special("relic_sense", "Relic Sense", "excavation-find-percent", new double[]{4, 6, 10}, "excavation discovery chance");
                case SMITHING -> special("lasting_work", "Lasting Work", "equipment-preservation-percent", new double[]{3, 4, 5}, "equipment durability preservation");
                case ALCHEMY -> special("concentrates", "Concentrates", "poison-potency-percent", new double[]{4, 6, 10}, "weapon poison potency");
                case BUILDING -> special("workshop_care", "Workshop Care", "equipment-preservation-percent", new double[]{3, 4, 5}, "equipment durability preservation");
                case COOKING -> special("nourishment", "Nourishment", "meal-nutrition-percent", new double[]{5, 10, 15}, "extra Well Fed recovery from meals you prepare");
                case ENCHANTING -> special("signature_focus", "Signature Focus", "signature-cooldown-reduction-percent", new double[]{4, 6, 10}, "shorter Arcane Signature cooldowns");
            };
            List<TalentBranch> branches = new ArrayList<>(trees.get(skill).branches());
            branches.add(tree(skill, seed).branches().getFirst());
            trees.put(skill, new SkillTree(skill, branches, junctions(skill, branches)));
        }
        return Map.copyOf(trees);
    }

    private static List<TalentNode> junctions(Skill skill, List<TalentBranch> branches) {
        String prefix = skill.name().toLowerCase(Locale.ROOT) + ".crossroads.";
        String[] names = switch (skill) {
            case MINING -> new String[]{"Surveyor's Insight", "Guild Cartography", "Crystal Resonance", "Heart of the Mountain"};
            case FORESTRY -> new String[]{"Forest Wisdom", "Lumber Exchange", "Living Canopy", "Ancient Grove"};
            case FARMING -> new String[]{"Seasonal Wisdom", "Harvest Exchange", "Fields of Plenty", "Keeper of the Fields"};
            case FISHING -> new String[]{"Reading the Tides", "Dockside Exchange", "Hidden Shoals", "Voice of the Ocean"};
            case HUNTING -> new String[]{"Know Your Quarry", "Hunter's Guild", "Predator's Focus", "Unbroken Hunter"};
            case EXCAVATION -> new String[]{"Buried Knowledge", "Relic Exchange", "Echoes Below", "Keeper of Lost Things"};
            case SMITHING -> new String[]{"Forge Wisdom", "Master's Commission", "Perfect Temper", "Anvil Eternal"};
            case ALCHEMY -> new String[]{"Shared Research", "Apothecary Network", "Precise Formula", "Master of Venoms"};
            case BUILDING -> new String[]{"Master's Blueprint", "Builder's Guild", "Steady Foundations", "Architect's Legacy"};
            case COOKING -> new String[]{"Family Recipes", "Banquet Commission", "Lasting Flavors", "Feast of the Isles"};
            case ENCHANTING -> new String[]{"Arcane Thesis", "Dust Reclamation", "Deep Research", "Signature of the Archmage"};
        };
        List<TalentNode> result = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            TalentNode source = branches.get(i).nodes().getFirst();
            double value = i == 0 || i == 1 ? 3 : i == 2 ? source.value() : branches.get(3).nodes().get(1).value();
            TalentRequirement requirement = i < 3 ? TalentRequirement.any(branches.get(i).nodes().get(1).id(), branches.get(i + 1).nodes().get(1).id())
                : new TalentRequirement(List.of(prefix + "1", prefix + "2", prefix + "3"), 2);
            String effect = "+" + (value == Math.rint(value) ? Long.toString((long)value) : Double.toString(value))
                + source.effect().substring(source.effect().indexOf('%'));
            result.add(new TalentNode(prefix + (i < 3 ? i + 1 : "capstone"), skill, "crossroads", "Crossroads", names[i], 1,
                i < 3 ? 30 : 50, null, source.stat(), value, effect, requirement));
        }
        return List.copyOf(result);
    }

    private static BranchSeed xp(String id, String name) {
        return new BranchSeed(id, name, "Learn this skill faster.", "skill-xp-percent",
            new double[]{5, 5, 10}, "Skill XP");
    }

    private static BranchSeed jobs(String id, String name) {
        return new BranchSeed(id, name, "Earn more Marks from this work.", "job-marks-percent",
            new double[]{5, 5, 10}, "Work Marks");
    }

    private static BranchSeed special(
        String id,
        String name,
        String stat,
        double[] values,
        String effect
    ) {
        return new BranchSeed(id, name, Character.toUpperCase(effect.charAt(0)) + effect.substring(1) + ".",
            stat, values, effect);
    }

    private static SkillTree tree(Skill skill, BranchSeed... seeds) {
        List<TalentBranch> branches = new ArrayList<>();
        String skillId = skill.name().toLowerCase(Locale.ROOT);
        for (BranchSeed seed : seeds) {
            List<TalentNode> nodes = new ArrayList<>();
            String previous = null;
            for (int index = 0; index < 3; index++) {
                int tier = index + 1;
                String id = skillId + "." + seed.id() + "." + tier;
                double value = seed.values()[index];
                String shown = Math.rint(value) == value ? Long.toString((long) value) : Double.toString(value);
                nodes.add(new TalentNode(
                    id,
                    skill,
                    seed.id(),
                    seed.name(),
                    seed.name() + " " + roman(tier),
                    tier,
                    NODE_LEVELS[index],
                    previous,
                    seed.stat(),
                    value,
                    "+" + shown + "% " + seed.effectLabel() + "."
                ));
                previous = id;
            }
            branches.add(new TalentBranch(seed.id(), seed.name(), seed.description(), nodes));
        }
        return new SkillTree(skill, branches);
    }

    private static Map<String, TalentNode> indexNodes() {
        Map<String, TalentNode> nodes = new HashMap<>();
        for (Skill skill : Skill.values()) nodes(skill).forEach(node -> nodes.put(node.id(), node));
        return Map.copyOf(nodes);
    }

    private static String roman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            default -> Integer.toString(value);
        };
    }

    public record SkillTree(Skill skill, List<TalentBranch> branches, List<TalentNode> junctions) {
        public SkillTree {
            branches = List.copyOf(branches);
            junctions = List.copyOf(junctions);
        }
        public SkillTree(Skill skill, List<TalentBranch> branches) { this(skill, branches, List.of()); }
    }

    public record TalentBranch(String id, String name, String description, List<TalentNode> nodes) {
        public TalentBranch {
            nodes = List.copyOf(nodes);
        }
        public double maximumValue() { return nodes.stream().mapToDouble(TalentNode::value).sum(); }
        public double valueThrough(int tier) { return nodes.stream().filter(node -> node.tier() <= tier).mapToDouble(TalentNode::value).sum(); }
    }

    public record TalentNode(
        String id,
        Skill skill,
        String branchId,
        String branchName,
        String name,
        int tier,
        int requiredLevel,
        String previousId,
        String stat,
        double value,
        String effect,
        TalentRequirement requirement
    ) {
        public TalentNode(String id, Skill skill, String branchId, String branchName, String name, int tier, int requiredLevel,
                          String previousId, String stat, double value, String effect) {
            this(id, skill, branchId, branchName, name, tier, requiredLevel, previousId, stat, value, effect,
                previousId == null ? TalentRequirement.NONE : TalentRequirement.any(previousId));
        }
    }

    private record BranchSeed(
        String id,
        String name,
        String description,
        String stat,
        double[] values,
        String effectLabel
    ) {
    }
}

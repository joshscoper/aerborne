package net.nerdsmp.api.model;

import java.util.*;

/** Personal, seasonal Adventurer choices. Existing automatic rank perks remain separate and intact. */
public final class AdventurerTalents {
    public static final List<Integer> POINT_LEVELS = List.of(5,10,20,30,40,50,60,70,80,90,100);
    private static final List<Node> NODES = build();
    private AdventurerTalents() { }
    public record Node(String id, String name, int branch, int rank, int requiredLevel, String stat, double value,
                       String effect, TalentRequirement requirement) { }
    public static List<Node> nodes() { return NODES; }
    public static Optional<Node> node(String id) { return NODES.stream().filter(n -> id != null && n.id().equalsIgnoreCase(id)).findFirst(); }
    public static int earnedPoints(int level) { return (int)POINT_LEVELS.stream().filter(required -> level >= required).count(); }
    public static int spentPoints(Set<String> talents) { return (int)talents.stream().filter(id -> node(id).isPresent()).count(); }
    public static double stat(Set<String> talents, String stat) { return NODES.stream().filter(n -> talents.contains(n.id()) && n.stat().equals(stat)).mapToDouble(Node::value).sum(); }
    public static List<Node> branch(int index) { return NODES.stream().filter(n -> n.branch() == index && n.rank() > 0).toList(); }
    public static List<Node> junctions() { return NODES.stream().filter(n -> n.rank() == 0).toList(); }
    public static String branchName(int index) { return List.of("Wayfarer", "Vanguard", "Warden", "Fortune").get(index); }
    private static List<Node> build() {
        List<Node> nodes = new ArrayList<>();
        String[] stats = {"skill-xp-percent","damage-percent","defense","job-marks-percent"};
        String[] effects = {"% XP from all skills.","% weapon damage."," Defense.","% Marks from skill work (daily limits apply)."};
        int[] levels = {5,30,60};
        for (int branch = 0; branch < 4; branch++) for (int rank = 1; rank <= 3; rank++) {
            String id = "adventurer." + branchName(branch).toLowerCase(Locale.ROOT) + "." + rank;
            double value = rank == 3 ? 2 : 1;
            nodes.add(new Node(id,branchName(branch)+" "+List.of("I","II","III").get(rank-1),branch,rank,levels[rank-1],
                stats[branch],value,"+"+(int)value+effects[branch],rank==1?TalentRequirement.NONE:TalentRequirement.any(id.substring(0,id.length()-1)+(rank-1))));
        }
        String[] names = {"Frontier Wisdom","Stand Together","Trusted Caravan","Legend of the Isles"};
        String[] crossStats = {"treasure-find-percent","defense","job-marks-percent","skill-xp-percent"};
        String[] crossEffects = {"+2% fishing treasure and excavation find odds.","+2 Defense.","+2% Marks from all skill work (daily limits apply).","+3% XP from all skills."};
        for (int i=0;i<4;i++) {
            TalentRequirement requirement = i<3 ? TalentRequirement.any("adventurer."+branchName(i).toLowerCase(Locale.ROOT)+".2","adventurer."+branchName(i+1).toLowerCase(Locale.ROOT)+".2")
                : new TalentRequirement(List.of("adventurer.crossroads.1","adventurer.crossroads.2","adventurer.crossroads.3"),2);
            nodes.add(new Node("adventurer.crossroads."+(i<3?i+1:"capstone"),names[i],4,0,i<3?45:90,crossStats[i],i<3?2:3,crossEffects[i],requirement));
        }
        return List.copyOf(nodes);
    }
}

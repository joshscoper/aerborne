package net.nerdsmp.paper.gui;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ProgressionService.*;
import java.util.*;

/** Ranked cards and purchase previews derived from the same rules used by both storage backends. */
public final class TalentTreePresentation {
    private TalentTreePresentation() { }
    public record Rank(String id,String name,int level,String effect,TalentRequirement requirement) { }
    public record Card(String nextId,String name,int rank,int maximum,int slot,SkillTreePresentation.State state,List<String> lore) {
        public Card { lore=List.copyOf(lore); }
    }
    public static List<Card> cards(ProgressionSnapshot snapshot, Skill skill) {
        List<Card> cards = new ArrayList<>();
        for(int branch=0;branch<4;branch++) {
            final int index=branch;
            List<Rank> ranks = skill == null ? AdventurerTalents.branch(branch).stream().map(TalentTreePresentation::rank).toList()
                : SkillTreeCatalog.tree(skill).branches().get(branch).nodes().stream().map(TalentTreePresentation::rank).toList();
            String name=skill==null?AdventurerTalents.branchName(branch):SkillTreeCatalog.tree(skill).branches().get(branch).name();
            cards.add(card(snapshot,skill,name,ranks,TalentTreeLayout.root(index)));
        }
        List<Rank> junctions=skill==null?AdventurerTalents.junctions().stream().map(TalentTreePresentation::rank).toList()
            :SkillTreeCatalog.tree(skill).junctions().stream().map(TalentTreePresentation::rank).toList();
        for(int i=0;i<junctions.size();i++) cards.add(card(snapshot,skill,junctions.get(i).name(),List.of(junctions.get(i)),TalentTreeLayout.junction(i)));
        return List.copyOf(cards);
    }
    private static Rank rank(SkillTreeCatalog.TalentNode n) { return new Rank(n.id(),n.name(),n.requiredLevel(),n.effect(),n.requirement()); }
    private static Rank rank(AdventurerTalents.Node n) { return new Rank(n.id(),n.name(),n.requiredLevel(),n.effect(),n.requirement()); }
    private static Card card(ProgressionSnapshot snapshot,Skill skill,String name,List<Rank> ranks,int slot) {
        int learned=(int)ranks.stream().filter(n->snapshot.talents().contains(n.id())).count();
        Rank next=ranks.stream().filter(n->!snapshot.talents().contains(n.id())).findFirst().orElse(ranks.getLast());
        TalentResult result=TalentRules.check(snapshot,next.id());
        SkillTreePresentation.State state=switch(result) {
            case UNLOCKED->SkillTreePresentation.State.AVAILABLE;
            case ALREADY_UNLOCKED->SkillTreePresentation.State.UNLOCKED;
            case LEVEL_TOO_LOW->SkillTreePresentation.State.LEVEL_LOCKED;
            case PREREQUISITE_MISSING->SkillTreePresentation.State.PATH_LOCKED;
            default->SkillTreePresentation.State.NO_POINTS;
        };
        String currency=skill==null?"Adventurer":skill.displayName();
        List<String> lore=new ArrayList<>(List.of("<gray>Rank</gray> <gold>"+learned+"/"+ranks.size()+"</gold>",""));
        for(Rank rank:ranks) lore.add((snapshot.talents().contains(rank.id())?"<gold>✓ ":"<gray>○ ")+rank.effect()+"</"+(snapshot.talents().contains(rank.id())?"gold":"gray")+">");
        lore.add("");
        if(learned<ranks.size()) {
            lore.add("<white>Next: "+next.name()+"</white>");
            lore.add("<gray>Requires "+currency+" level "+next.level()+"</gray>");
            lore.add("<yellow>Cost: 1 "+currency+" talent point</yellow>");
            if(next.requirement().minimum()>0) {
                lore.add("<gray>Requires "+(next.requirement().options().size()>1?"any "+next.requirement().minimum()+" of:":"this rank:")+"</gray>");
                for(String id:next.requirement().options()) lore.add((snapshot.talents().contains(id)?"<green>✓ ":"<red>○ ")+name(id)+(snapshot.talents().contains(id)?"</green>":"</red>"));
            }
            lore.add("");
            lore.add(switch(state) {case AVAILABLE->"<green>Click to review this rank.</green>";case LEVEL_LOCKED->"<gray>Keep training to reach the required level.</gray>";
                case PATH_LOCKED->"<gray>Follow a connected path first.</gray>";default->"<gray>Earn another point to continue.</gray>";});
        } else lore.add("<gold>Fully learned. This bonus is active.</gold>");
        if(learned>0 && learned<ranks.size()) lore.add("<aqua>Your purchased ranks remain active.</aqua>");
        if(skill!=null && snapshot.skills().get(skill).level()>=50 && ranks.stream().anyMatch(r->r.effect().contains("Skill XP")))
            lore.add("<yellow>This skill is level 50; further skill XP has no effect.</yellow>");
        return new Card(next.id(),name,learned,ranks.size(),slot,state,lore);
    }
    public static String name(String id) { return SkillTreeCatalog.node(id).map(SkillTreeCatalog.TalentNode::name)
        .orElseGet(()->AdventurerTalents.node(id).map(AdventurerTalents.Node::name).orElse(id)); }
    public static String effect(String id) { return SkillTreeCatalog.node(id).map(SkillTreeCatalog.TalentNode::effect)
        .orElseGet(()->AdventurerTalents.node(id).map(AdventurerTalents.Node::effect).orElse("")); }
    public static List<String> overview(ProgressionSnapshot snapshot,Skill skill) {
        if(skill!=null) {
            List<String> lore=new ArrayList<>(SkillTreePresentation.overview(snapshot,skill));
            lore.removeIf(line->line.contains("Twelve talents"));
            lore.add("<gold>Ranked paths → shared crossroads → capstone</gold>");
            lore.add("<gray>16 ranks available; choose with 8 points.</gray>");
            return List.copyOf(lore);
        }
        int level=snapshot.adventurer().level();
        List<String> lore=new ArrayList<>(List.of("<gray>Adventurer Level</gray> <light_purple>"+level+"/100</light_purple>",
            "<gray>Available</gray> <yellow>"+snapshot.adventurerPointsAvailable()+"</yellow> <gray>Spent</gray> <white>"+snapshot.adventurerPointsSpent()+" / "+snapshot.adventurerPointsEarned()+"</white>",
            "<gray>11 points across 16 ranks. Shape your journey.</gray>","<gray>These points belong to your Adventurer tree.</gray>","<gray>Automatic rank perks remain active.</gray>"));
        AdventurerTalents.POINT_LEVELS.stream().filter(required->required>level).findFirst().ifPresent(required->lore.add("<aqua>Next point: Adventurer "+required+"</aqua>"));
        return List.copyOf(lore);
    }
}

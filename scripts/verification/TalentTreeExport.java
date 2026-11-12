package net.nerdsmp.probe;

import com.google.gson.GsonBuilder;
import net.nerdsmp.api.model.*;
import net.nerdsmp.paper.skyblock.IslandTalent;
import java.nio.file.*;
import java.util.*;

/** Export the real catalog for a local interactive build planner; no server or player state needed. */
public final class TalentTreeExport {
    record Rank(String id,String name,int level,String effect,TalentRequirement requirement) { }
    record Node(String key,String name,int branch,List<Rank> ranks,boolean capstone) { }
    record Tree(String id,String name,int maxLevel,List<Integer> pointLevels,List<Node> nodes,String command) { }
    public static void main(String[] args) throws Exception {
        List<Tree> trees=new ArrayList<>();
        List<Node> general=new ArrayList<>();
        for(int branch=0;branch<4;branch++) general.add(new Node("adventurer."+branch,AdventurerTalents.branchName(branch),branch,
            AdventurerTalents.branch(branch).stream().map(n->new Rank(n.id(),n.name(),n.requiredLevel(),n.effect(),n.requirement())).toList(),false));
        for(var n:AdventurerTalents.junctions()) general.add(new Node(n.id(),n.name(),4,List.of(new Rank(n.id(),n.name(),n.requiredLevel(),n.effect(),n.requirement())),n.id().endsWith("capstone")));
        trees.add(new Tree("adventurer","Adventurer",100,AdventurerTalents.POINT_LEVELS,general,"/adventurer"));
        for(Skill skill:Skill.values()) {
            List<Node> nodes=new ArrayList<>(); var tree=SkillTreeCatalog.tree(skill);
            for(int branch=0;branch<4;branch++) {
                var b=tree.branches().get(branch);
                nodes.add(new Node(b.id(),b.name(),branch,b.nodes().stream().map(n->new Rank(n.id(),n.name(),n.requiredLevel(),n.effect(),n.requirement())).toList(),false));
            }
            for(var n:tree.junctions()) nodes.add(new Node(n.id(),n.name(),4,List.of(new Rank(n.id(),n.name(),n.requiredLevel(),n.effect(),n.requirement())),n.id().endsWith("capstone")));
            trees.add(new Tree(skill.name().toLowerCase(Locale.ROOT),skill.displayName(),50,SkillTreeCatalog.pointLevels(),nodes,"/skills"));
        }
        List<Node> island=new ArrayList<>();
        for(int branch=0;branch<4;branch++) {
            final int column=branch;
            for(var n:Arrays.stream(IslandTalent.values()).filter(n->n.column()==column).sorted(Comparator.comparingInt(IslandTalent::depth)).toList()) {
                var options=n.prerequisiteOptions().stream().map(parent->"island."+parent.id()+".2").toList();
                List<Rank> ranks=new ArrayList<>();
                for(int rank=1;rank<=3;rank++) {
                    TalentRequirement requirement=rank==1?new TalentRequirement(options,options.isEmpty()?0:1):TalentRequirement.any("island."+n.id()+"."+(rank-1));
                    ranks.add(new Rank("island."+n.id()+"."+rank,n.title()+" "+rank,n.requiredLevel(),n.effect(rank),requirement));
                }
                island.add(new Node(n.id(),n.title(),column,ranks,n.depth()==3));
            }
        }
        trees.add(new Tree("island","Island",50,java.util.stream.IntStream.rangeClosed(1,25).map(level->level*2).boxed().toList(),island,"/island talents"));
        Path target=Path.of(args[0]); Files.createDirectories(target.toAbsolutePath().getParent());
        Files.writeString(target,new GsonBuilder().setPrettyPrinting().create().toJson(trees));
        System.out.println("Exported "+trees.size()+" trees, "+trees.stream().mapToInt(t->t.nodes().stream().mapToInt(n->n.ranks().size()).sum()).sum()+" purchasable ranks");
    }
}

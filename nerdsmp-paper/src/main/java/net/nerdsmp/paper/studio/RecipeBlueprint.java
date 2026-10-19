package net.nerdsmp.paper.studio;

import net.nerdsmp.api.model.Skill;
import net.nerdsmp.paper.engine.ItemBlueprint;
import org.bukkit.Material;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A staff-authored shaped recipe uses nine plain material samples, not inventory items. */
public record RecipeBlueprint(String id,String output,int amount,Skill skill,int level,boolean enabled,List<Material> grid) {
    public RecipeBlueprint {
        id=ItemBlueprint.identifier(id); output=ItemBlueprint.identifier(output);
        if(amount<1||amount>64) throw new IllegalArgumentException("Output amount must be 1–64; equipment is limited to one.");
        Objects.requireNonNull(skill,"A crafting skill is required");
        if(level<1||level>50) throw new IllegalArgumentException("Required skill level must be 1–50.");
        grid=List.copyOf(Objects.requireNonNull(grid));
        if(grid.size()!=9) throw new IllegalArgumentException("The recipe grid must contain exactly nine slots.");
        if(grid.stream().anyMatch(m->m==Material.CAVE_AIR||m==Material.VOID_AIR||m==Material.BARRIER||m==Material.STRUCTURE_VOID))
            throw new IllegalArgumentException("Choose ordinary obtainable ingredient materials; AIR represents an empty slot.");
        if(enabled&&grid.stream().allMatch(m->m==Material.AIR)) throw new IllegalArgumentException("Add at least one ingredient before enabling the recipe.");
    }
    public RecipeBlueprint slot(int index,Material material) {
        if(index<0||index>=9) throw new IllegalArgumentException("Choose a slot inside the recipe grid.");
        ArrayList<Material> next=new ArrayList<>(grid); next.set(index,Objects.requireNonNull(material));
        return new RecipeBlueprint(id,output,amount,skill,level,enabled,next);
    }
    public Pattern pattern() {
        int minX=3,maxX=-1,minY=3,maxY=-1;
        for(int i=0;i<9;i++) if(grid.get(i)!=Material.AIR) {minX=Math.min(minX,i%3);maxX=Math.max(maxX,i%3);minY=Math.min(minY,i/3);maxY=Math.max(maxY,i/3);}
        if(maxX<0) throw new IllegalArgumentException("Add at least one ingredient to the recipe grid.");
        LinkedHashMap<Material,Character> symbols=new LinkedHashMap<>(); LinkedHashMap<Character,Material> ingredients=new LinkedHashMap<>(); ArrayList<String> rows=new ArrayList<>();
        for(int y=minY;y<=maxY;y++) {
            StringBuilder row=new StringBuilder();
            for(int x=minX;x<=maxX;x++) {Material material=grid.get(y*3+x); if(material==Material.AIR) row.append(' ');
                else {char symbol=symbols.computeIfAbsent(material,m->(char)('A'+symbols.size()));ingredients.put(symbol,material);row.append(symbol);}}
            rows.add(row.toString());
        }
        return new Pattern(rows,ingredients);
    }
    public record Pattern(List<String> shape,Map<Character,Material> ingredients) {
        public Pattern {shape=List.copyOf(shape);ingredients=Map.copyOf(ingredients);}
    }
}

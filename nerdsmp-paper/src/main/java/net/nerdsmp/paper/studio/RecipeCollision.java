package net.nerdsmp.paper.studio;

import org.bukkit.Material;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Detects collisions with ordinary-material shaped and shapeless recipes without a server registry. */
public final class RecipeCollision {
    private RecipeCollision() { }
    public static boolean shaped(List<String> shape,Map<Character,Set<Material>> choices,RecipeBlueprint candidate) {
        if(shape.isEmpty()||shape.size()>3||shape.getFirst().length()>3)return false;
        int minX=3,maxX=-1,minY=3,maxY=-1;
        for(int y=0;y<shape.size();y++)for(int x=0;x<shape.get(y).length();x++)if(shape.get(y).charAt(x)!=' ') {minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}
        if(maxX<0)return false;
        RecipeBlueprint.Pattern p=candidate.pattern();int width=maxX-minX+1,height=maxY-minY+1;
        if(p.shape().size()!=height||p.shape().getFirst().length()!=width)return false;
        for(boolean mirror:List.of(false,true)) {
            boolean matches=true;
            for(int y=0;y<height;y++)for(int x=0;x<width;x++) {
                char existing=shape.get(y+minY).charAt(x+minX),symbol=p.shape().get(y).charAt(mirror?width-1-x:x);
                if(existing==' '||symbol==' ') {if(existing!=symbol)matches=false;}
                else if(!choices.getOrDefault(existing,Set.of()).contains(p.ingredients().get(symbol)))matches=false;
            }
            if(matches)return true;
        }
        return false;
    }
    public static boolean shapeless(List<Set<Material>> choices,RecipeBlueprint candidate) {
        List<Material> input=candidate.grid().stream().filter(m->m!=Material.AIR).toList();
        if(input.size()!=choices.size()||input.isEmpty())return false;
        return assignment(choices,input,0,new boolean[input.size()]);
    }
    private static boolean assignment(List<Set<Material>> choices,List<Material> input,int index,boolean[] used) {
        if(index==choices.size())return true;
        // Deduplicate materials at a depth: identical stacks need not produce factorial retry branches.
        ArrayList<Material> attempted=new ArrayList<>();
        for(int i=0;i<input.size();i++)if(!used[i]&&choices.get(index).contains(input.get(i))&&!attempted.contains(input.get(i))) {
            attempted.add(input.get(i));used[i]=true;
            if(assignment(choices,input,index+1,used))return true;
            used[i]=false;
        }
        return false;
    }
}

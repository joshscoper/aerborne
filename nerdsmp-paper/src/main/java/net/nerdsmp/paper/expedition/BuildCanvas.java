package net.nerdsmp.paper.expedition;

import org.bukkit.Material;
import java.util.*;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.Cell;

/** Offline block plan. Explicit block states preserve stairs, slabs, foliage and wall connections. */
public final class BuildCanvas {
    public record BlockSpec(Material material,String properties) {
        public BlockSpec {
            Objects.requireNonNull(material);
            properties=properties==null?"":properties;
            if(!properties.matches("[a-z0-9_=,.-]*")) throw new IllegalArgumentException("Invalid block properties");
        }
        public String state() { return "minecraft:"+material.name().toLowerCase(Locale.ROOT)+(properties.isEmpty()?"":"["+properties+"]"); }
    }
    public record Placement(Cell cell,BlockSpec block) { }
    private record Chunk(int x,int z) { }
    private final Map<Cell,BlockSpec> blocks=new LinkedHashMap<>();
    public void block(int x,int y,int z,Material material) { block(x,y,z,material,""); }
    public void block(int x,int y,int z,Material material,String properties) {
        if(Math.abs(x)>1024 || Math.abs(z)>1024 || y<-64 || y>=320) throw new IllegalArgumentException("Architecture outside reviewed bounds");
        blocks.put(new Cell(x,y,z),new BlockSpec(material,properties));
    }
    public void box(int x1,int y1,int z1,int x2,int y2,int z2,Material material) {
        if(x2<x1 || y2<y1 || z2<z1) throw new IllegalArgumentException("Reversed architecture box");
        for(int x=x1;x<=x2;x++) for(int z=z1;z<=z2;z++) for(int y=y1;y<=y2;y++) block(x,y,z,material);
    }
    public void disk(int x,int y,int z,int radius,Material material) {
        if(radius<0 || radius>128) throw new IllegalArgumentException("Invalid disc radius");
        for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++)
            if(dx*dx+dz*dz<=radius*radius) block(x+dx,y,z+dz,material);
    }
    public Map<Cell,BlockSpec> blocks() { return Collections.unmodifiableMap(blocks); }
    public Plan freeze() { return new Plan(blocks); }
    public static final class Plan {
        private final Map<Chunk,List<Placement>> chunks;
        private final int size;
        private Plan(Map<Cell,BlockSpec> source) {
            Map<Chunk,List<Placement>> mutable=new HashMap<>();
            source.forEach((cell,state)->mutable.computeIfAbsent(new Chunk(cell.x()>>4,cell.z()>>4),ignored->new ArrayList<>()).add(new Placement(cell,state)));
            Map<Chunk,List<Placement>> complete=new HashMap<>(); mutable.forEach((key,value)->complete.put(key,List.copyOf(value)));
            chunks=Map.copyOf(complete); size=source.size();
        }
        public List<Placement> chunk(int x,int z) { return chunks.getOrDefault(new Chunk(x,z),List.of()); }
        public int size() { return size; }
        public List<Placement> placements() { return chunks.values().stream().flatMap(Collection::stream).toList(); }
    }
}

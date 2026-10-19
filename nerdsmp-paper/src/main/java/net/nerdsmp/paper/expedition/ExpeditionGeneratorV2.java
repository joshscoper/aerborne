package net.nerdsmp.paper.expedition;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.*;

/** Revision-isolated, chunk-local generator. Geometry can be rendered and checked without a server. */
public final class ExpeditionGeneratorV2 extends ChunkGenerator {
    @FunctionalInterface public interface BlockSink { void set(int x,int y,int z,Material material,String properties); }
    private static final class Architecture { static final BuildCanvas.Plan PLAN=createArchitecture(); }
    private final Map<BuildCanvas.BlockSpec,BlockData> states=new ConcurrentHashMap<>();
    public static BuildCanvas.Plan architecture() { return Architecture.PLAN; }
    private static BuildCanvas.Plan createArchitecture() {
        BuildCanvas b=new BuildCanvas();
        // The other districts retain their reviewed landmarks while the city and forest are fully replaced.
        ExpeditionArchitecture original=ExpeditionArchitecture.create();
        for(Zone zone:Zone.values()) {
            if(zone==Zone.CITY || zone==Zone.GROVE) continue;
            for(int cx=(zone.x-55)>>4;cx<=(zone.x+55)>>4;cx++) for(int cz=(zone.z-55)>>4;cz<=(zone.z+55)>>4;cz++)
                for(var placement:original.chunk(cx,cz)) {
                    Cell c=placement.cell();
                    if(Math.hypot(c.x()-zone.x,c.z()-zone.z)>55) continue;
                    b.block(c.x()-zone.x+ExpeditionPlanV2.centerX(zone),c.y(),c.z()-zone.z+ExpeditionPlanV2.centerZ(zone),placement.material());
                }
        }
        HearthportV2.build(b); WindrootV2.build(b); bridges(b);
        // Public arrival capsules and their foundations cannot be obstructed by landscaping.
        for(Zone zone:Zone.values()) {
            int x=ExpeditionPlanV2.centerX(zone),z=ExpeditionPlanV2.centerZ(zone)+(zone==Zone.CITY?0:zone==Zone.HARBOR?23:19);
            b.box(x-1,96,z-1,x+1,96,z+1,zone==Zone.CITY?Material.POLISHED_ANDESITE:Material.MOSSY_STONE_BRICKS);
            b.box(x-1,97,z-1,x+1,99,z+1,Material.AIR);
        }
        ExpeditionPlanV2.NODES.values().forEach(node->{Cell c=node.cell(); b.block(c.x(),c.y(),c.z(),node.material());});
        return b.freeze();
    }
    private static void bridges(BuildCanvas b) {
        for(int direction=0;direction<4;direction++) for(int length=80;length<=183;length++) for(int width=-4;width<=4;width++) {
            int x=direction==0?length:direction==1?-length:width;
            int z=direction==2?length:direction==3?-length:width;
            boolean side=Math.abs(width)==4,alongX=direction<2;
            b.block(x,96,z,side?Material.POLISHED_DEEPSLATE:Math.floorMod(length,9)==0?Material.CHISELED_STONE_BRICKS:Material.STONE_BRICKS);
            if(side) {
                String connections=alongX?"east=low,west=low":"north=low,south=low";
                b.block(x,97,z,Material.STONE_BRICK_WALL,connections+",up=false");
                int rib=(int)(5+18*Math.pow(Math.abs(length-132)/52D,1.4));
                b.box(x,96-rib,z,x,95,z,Math.floorMod(length,13)==0?Material.CHISELED_STONE_BRICKS:Material.STONE_BRICKS);
                if(length%13==2) {
                    b.block(x,98,z,Material.CHISELED_STONE_BRICKS);
                    b.block(x,99,z,Material.LANTERN,"hanging=false");
                }
            } else b.box(x,97,z,x,99,z,Material.AIR);
            if(length%13==2) b.block(x,94,z,Material.POLISHED_DEEPSLATE);
        }
    }
    /** Outputs world coordinates; no block/chunk/world reads, including across chunk boundaries. */
    public static void emitChunk(int chunkX,int chunkZ,BlockSink sink) {
        for(Zone zone:Zone.values()) {
            int centerX=ExpeditionPlanV2.centerX(zone),centerZ=ExpeditionPlanV2.centerZ(zone);
            if(Math.abs(chunkX*16+8-centerX)>ExpeditionPlanV2.radius(zone)+24 || Math.abs(chunkZ*16+8-centerZ)>ExpeditionPlanV2.radius(zone)+24) continue;
            for(int lx=0;lx<16;lx++) for(int lz=0;lz<16;lz++) {
                int x=chunkX*16+lx,z=chunkZ*16+lz,dx=x-centerX,dz=z-centerZ;
                if(Math.hypot(dx,dz)>ExpeditionTerrainV2.edge(zone,Math.atan2(dz,dx))) continue;
                int top=terrainHeight(zone,dx,dz),bottom=ExpeditionTerrainV2.bottom(zone,dx,dz,top);
                for(int y=bottom;y<=top;y++) sink.set(x,y,z,y==top?ExpeditionTerrainV2.surface(zone,dx,dz):ExpeditionTerrainV2.rock(zone,dx,y,dz,top),"");
                if(zone==Zone.HARBOR && Math.abs(dx)<=17 && dz>=-22 && dz<=12)
                    for(int y=93;y<=96;y++) sink.set(x,y,z,Material.WATER,"");
            }
        }
        for(var p:architecture().chunk(chunkX,chunkZ)) { Cell c=p.cell(); sink.set(c.x(),c.y(),c.z(),p.block().material(),p.block().properties()); }
    }
    public static int terrainHeight(Zone zone,int x,int z) {
        // Existing landmarks in the remaining districts stand on their original Y96 foundations.
        if(zone!=Zone.CITY && zone!=Zone.GROVE && Math.hypot(x,z)<48) return 96;
        return ExpeditionTerrainV2.height(zone,x,z);
    }
    @Override public void generateNoise(WorldInfo info,Random random,int chunkX,int chunkZ,ChunkData data) {
        emitChunk(chunkX,chunkZ,(x,y,z,material,properties)-> {
            if(properties.isEmpty()) data.setBlock(x&15,y,z&15,material);
            else data.setBlock(x&15,y,z&15,states.computeIfAbsent(new BuildCanvas.BlockSpec(material,properties),spec->Bukkit.createBlockData(spec.state())));
        });
    }
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
    @Override public Location getFixedSpawnLocation(World world,Random random) { return new Location(world,.5,97,.5); }
    @Override public int getBaseHeight(WorldInfo info,Random random,int x,int z,HeightMap map) { return info.getMinHeight(); }
    @Override public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo world,int x,int y,int z) { return ExpeditionPlanV2.zoneAt(x,z).map(zone->resolve(zone.biome)).orElse(Biome.THE_VOID); }
            @Override public List<Biome> getBiomes(WorldInfo world) {
                List<Biome> biomes=new ArrayList<>(Arrays.stream(Zone.values()).map(zone->resolve(zone.biome)).distinct().toList());
                biomes.add(Biome.THE_VOID); return List.copyOf(biomes);
            }
        };
    }
    private static Biome resolve(String name) {
        return Objects.requireNonNull(io.papermc.paper.registry.RegistryAccess.registryAccess()
            .getRegistry(io.papermc.paper.registry.RegistryKey.BIOME).get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT))));
    }
}

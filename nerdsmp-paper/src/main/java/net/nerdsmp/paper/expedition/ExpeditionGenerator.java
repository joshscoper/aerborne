package net.nerdsmp.paper.expedition;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.generator.*;
import java.util.*;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.*;

/** Pure terrain plus a chunk-indexed authored architecture plan. Never reads neighboring chunks. */
public final class ExpeditionGenerator extends ChunkGenerator {
    private static final ExpeditionArchitecture ARCHITECTURE=ExpeditionArchitecture.create();
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
    @Override public Location getFixedSpawnLocation(World world,Random random) { return new Location(world,.5,SURFACE+1,.5); }
    @Override public int getBaseHeight(WorldInfo info,Random random,int x,int z,HeightMap map) { return info.getMinHeight(); }
    @Override public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return new BiomeProvider() {
            @Override public Biome getBiome(WorldInfo world,int x,int y,int z) { return zoneAt(x,z).map(zone->resolve(zone.biome)).orElse(Biome.THE_VOID); }
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
    @Override public void generateNoise(WorldInfo info,Random random,int chunkX,int chunkZ,ChunkData data) {
        for(Zone zone:Zone.values()) {
            if(Math.abs(chunkX*16+8-zone.x)>zone.radius+18 || Math.abs(chunkZ*16+8-zone.z)>zone.radius+18) continue;
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
                int dx=chunkX*16+x-zone.x,dz=chunkZ*16+z-zone.z;
                double distance=Math.hypot(dx,dz),angle=Math.atan2(dz,dx);
                double edge=zone.radius+(zone==Zone.CITY?0:3*Math.sin(angle*5)+2*Math.cos(angle*3));
                if(distance>edge) continue;
                int top=surfaceY(zone,dx,dz);
                int depth=6+(int)((edge-distance)*.83)+Math.floorMod(dx*13+dz*17,3);
                for(int d=depth;d>=0;d--) {
                    Material material=d==0?surface(zone,dx,dz):stone(zone,dx,dz,d);
                    data.setBlock(x,top-d,z,material);
                }
                if(zone==Zone.CITY && distance>edge-1.5 && Math.abs(dx)>4 && Math.abs(dz)>4)
                    data.setBlock(x,top+1,z,Material.STONE_BRICK_WALL);
                if(zone==Zone.HARBOR && Math.abs(dx)<=17 && dz>=-22 && dz<=12)
                    for(int y=SURFACE-3;y<=SURFACE;y++) data.setBlock(x,y,z,Material.WATER);
                // Low planting stays away from the central paths, resource clearings and architecture.
                if((zone==Zone.GROVE || zone==Zone.HARBOR) && distance>37 && distance<edge-3
                    && Math.abs(dx)>5 && Math.abs(dz)>5 && Math.floorMod(dx*37+dz*61,29)==0)
                    data.setBlock(x,top+1,z,Material.FLOWERING_AZALEA);
            }
        }
        for(var placement:ARCHITECTURE.chunk(chunkX,chunkZ)) {
            Cell cell=placement.cell();
            data.setBlock(cell.x()&15,cell.y(),cell.z()&15,placement.material());
        }
    }
    static int surfaceY(Zone zone,int x,int z) {
        if(zone==Zone.CITY || Math.hypot(x,z)<35 || Math.abs(x)<=4 || Math.abs(z)<=4) return SURFACE;
        double rise=Math.max(0,Math.hypot(x,z)-35);
        // Mining and ruined temple ridges rise toward their landmarks; garden shores remain gentle.
        double scale=zone==Zone.QUARRY || zone==Zone.DIGSITE ? .35 : .18;
        return SURFACE+(int)Math.max(0,rise*scale+Math.sin(x*.19)*1.5+Math.cos(z*.16));
    }
    private static Material stone(Zone zone,int x,int z,int depth) {
        if(depth<=2 && (zone==Zone.GROVE || zone==Zone.HARBOR)) return Material.DIRT;
        int band=Math.floorMod(depth+x/9+z/11,13);
        return switch(zone) {
            case CINDER -> band<3?Material.BLACKSTONE:band<5?Material.BASALT:Material.NETHERRACK;
            case ASTRAL -> band<3?Material.OBSIDIAN:Material.END_STONE;
            case DIGSITE -> band<3?Material.TERRACOTTA:Material.SANDSTONE;
            default -> band<2?Material.DEEPSLATE:band<5?Material.TUFF:band==6?Material.ANDESITE:Material.STONE;
        };
    }
    private static Material surface(Zone zone,int x,int z) {
        double bend=3*Math.sin(z*.06);
        boolean path=Math.abs(x-bend)<=2 || Math.abs(z-3*Math.sin(x*.06))<=2;
        if(zone==Zone.CITY) {
            double ring=Math.hypot(x,z);
            if(Math.abs(x)<=3 || Math.abs(z)<=3 || ring>47 && ring<51) return Math.floorMod(x+z,7)==0?Material.CHISELED_STONE_BRICKS:Material.SMOOTH_QUARTZ;
            if(Math.abs(x)>43 && Math.abs(z)>6) return Material.GRASS_BLOCK;
            return Math.floorMod(x*17+z*13,11)==0?Material.CRACKED_STONE_BRICKS:Material.STONE_BRICKS;
        }
        if(path) return zone==Zone.CINDER?Material.POLISHED_BLACKSTONE_BRICKS:zone==Zone.ASTRAL?Material.PURPUR_BLOCK:Material.COARSE_DIRT;
        return switch(zone) {
            case CINDER -> Material.BLACKSTONE;
            case ASTRAL -> Material.END_STONE_BRICKS;
            case QUARRY -> Math.floorMod(x+z,5)==0?Material.TUFF:Material.ANDESITE;
            case DIGSITE -> Material.SANDSTONE;
            default -> Material.GRASS_BLOCK;
        };
    }
}

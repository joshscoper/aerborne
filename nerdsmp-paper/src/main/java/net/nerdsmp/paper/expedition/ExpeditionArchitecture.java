package net.nerdsmp.paper.expedition;

import org.bukkit.Material;
import java.util.*;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.*;

/** Authored silhouettes and interiors, baked once into a bounded chunk-indexed plan. No live-world edits. */
public final class ExpeditionArchitecture {
    public record Placement(Cell cell,Material material) { }
    private record Chunk(int x,int z) { }
    private final Map<Chunk,List<Placement>> chunks;
    private ExpeditionArchitecture(Map<Cell,Material> plan) {
        Map<Chunk,List<Placement>> grouped=new HashMap<>();
        plan.forEach((cell,material)->grouped.computeIfAbsent(new Chunk(cell.x()>>4,cell.z()>>4),ignored->new ArrayList<>()).add(new Placement(cell,material)));
        Map<Chunk,List<Placement>> frozen=new HashMap<>(); grouped.forEach((key,value)->frozen.put(key,List.copyOf(value)));
        chunks=Map.copyOf(frozen);
    }
    public List<Placement> chunk(int x,int z) { return chunks.getOrDefault(new Chunk(x,z),List.of()); }
    public static ExpeditionArchitecture create() {
        Builder b=new Builder();
        b.city(); b.quarry(); b.grove(); b.digsite(); b.harbor(); b.cinder(); b.astral();
        b.bridges();
        // Resource-bearing coordinates are the final layer, not decoration that can replace rewards.
        NODES.values().forEach(node->b.put(node.cell().x(),node.cell().y(),node.cell().z(),node.material()));
        return new ExpeditionArchitecture(b.blocks);
    }
    private static final class Builder {
        final Map<Cell,Material> blocks=new LinkedHashMap<>();
        void put(int x,int y,int z,Material material) { blocks.put(new Cell(x,y,z),material); }
        void box(int x1,int y1,int z1,int x2,int y2,int z2,Material material) {
            for(int x=x1;x<=x2;x++) for(int z=z1;z<=z2;z++) for(int y=y1;y<=y2;y++) put(x,y,z,material);
        }
        void ring(int x,int y,int z,int radius,Material material) {
            for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++)
                if(dx*dx+dz*dz<=radius*radius && dx*dx+dz*dz>(radius-1)*(radius-1)) put(x+dx,y,z+dz,material);
        }
        void disc(int x,int y,int z,int radius,Material material) {
            for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++)
                if(dx*dx+dz*dz<=radius*radius) put(x+dx,y,z+dz,material);
        }
        void lamp(int x,int y,int z) {
            box(x,y+1,z,x,y+3,z,Material.DEEPSLATE_TILE_WALL); put(x,y+4,z,Material.SEA_LANTERN);
            put(x,y+5,z,Material.CUT_COPPER_SLAB);
        }
        void spire(int x,int y,int z,int radius,int height,Material roof) {
            disc(x,y,z,radius+1,Material.POLISHED_DEEPSLATE);
            for(int h=1;h<=height;h++) {
                ring(x,y+h,z,radius,h%7==0?Material.CHISELED_STONE_BRICKS:Material.CALCITE);
                if(h%7==0) disc(x,y+h,z,radius-1,Material.BIRCH_PLANKS);
                if(h%7>=2 && h%7<=4) {
                    put(x,y+h,z-radius,Material.CYAN_STAINED_GLASS); put(x,y+h,z+radius,Material.CYAN_STAINED_GLASS);
                    put(x-radius,y+h,z,Material.CYAN_STAINED_GLASS); put(x+radius,y+h,z,Material.CYAN_STAINED_GLASS);
                }
            }
            for(int h=0;h<=radius*3;h++) disc(x,y+height+h,z,Math.max(0,radius+1-h/3),roof);
            put(x,y+height+radius*3+1,z,Material.GOLD_BLOCK);
            box(x-1,y+1,z+radius-1,x+1,y+4,z+radius,Material.AIR);
        }
        void house(int x,int z,int width,int depth,int height,Material roof,Material plaster) {
            int y=SURFACE;
            box(x-width,y,z-depth,x+width,y,z+depth,Material.STONE_BRICKS);
            for(int h=1;h<=height;h++) {
                box(x-width,y+h,z-depth,x+width,y+h,z-depth,plaster);
                box(x-width,y+h,z+depth,x+width,y+h,z+depth,plaster);
                box(x-width,y+h,z-depth,x-width,y+h,z+depth,plaster);
                box(x+width,y+h,z-depth,x+width,y+h,z+depth,plaster);
                for(int dx:new int[]{-width,width}) box(x+dx,y+h,z-depth,x+dx,y+h,z+depth,Material.DARK_OAK_LOG);
                if(h==1 || h==height || h==height/2) {
                    box(x-width,y+h,z-depth,x+width,y+h,z-depth,Material.DARK_OAK_LOG);
                    box(x-width,y+h,z+depth,x+width,y+h,z+depth,Material.DARK_OAK_LOG);
                }
            }
            // Tall glazed windows, an open central door and furnished counter make façades usable.
            for(int dx:new int[]{-width+2,width-2}) box(x+dx,y+3,z+depth,x+dx+1,y+5,z+depth,Material.LIGHT_BLUE_STAINED_GLASS);
            box(x-1,y+1,z+depth-1,x+1,y+4,z+depth,Material.AIR);
            for(int h=0;h<=width+1;h++) {
                int reach=width+1-h;
                box(x-reach,y+height+h,z-depth-1,x-reach,y+height+h,z+depth+1,roof);
                box(x+reach,y+height+h,z-depth-1,x+reach,y+height+h,z+depth+1,roof);
                if(h>0 && reach>0) {
                    box(x-reach+1,y+height+h,z-depth,x+reach-1,y+height+h,z-depth,plaster);
                    box(x-reach+1,y+height+h,z+depth,x+reach-1,y+height+h,z+depth,plaster);
                }
            }
            box(x-width+2,y+1,z-depth+2,x+width-2,y+1,z-depth+2,Material.SPRUCE_SLAB);
            box(x+width-2,y+height,z-depth+2,x+width-1,y+height+5,z-depth+3,Material.BRICKS);
            put(x,y+height-1,z,Material.LANTERN);
        }
        void stall(int x,int z,Material awning) {
            for(int dx:new int[]{-3,3}) for(int dz:new int[]{-2,2}) box(x+dx,SURFACE+1,z+dz,x+dx,SURFACE+4,z+dz,Material.SPRUCE_FENCE);
            for(int dx=-4;dx<=4;dx++) box(x+dx,SURFACE+5,z-3,x+dx,SURFACE+5,z+3,dx%2==0?awning:Material.WHITE_WOOL);
            box(x-2,SURFACE+1,z-1,x+2,SURFACE+1,z-1,Material.SPRUCE_SLAB);
            put(x,SURFACE+4,z,Material.LANTERN);
        }
        void crystal(int x,int y,int z,int height,Material material) {
            for(int h=0;h<height;h++) disc(x,y+h,z,h<2 || h>height-3?1:2,material);
            put(x,y+height,z,Material.SEA_LANTERN);
        }
        void tree(int x,int z,int height,Material trunk,Material leaves) {
            box(x,SURFACE+1,z,x,SURFACE+height,z,trunk);
            for(int h=-2;h<=2;h++) {
                int radius=h==2?2:h==-2?3:4;
                disc(x,SURFACE+height+h,z,radius,leaves);
            }
            box(x,SURFACE+height-2,z,x,SURFACE+height,z,trunk);
        }
        void city() {
            // The skyline is anchored by a northern arcane hall, not repeated market roofs.
            house(0,-35,12,10,16,Material.DARK_PRISMARINE,Material.SMOOTH_QUARTZ);
            spire(-18,SURFACE,-39,5,27,Material.DARK_PRISMARINE);
            spire(18,SURFACE,-39,5,34,Material.WARPED_PLANKS);
            spire(0,SURFACE+17,-40,4,29,Material.PURPUR_BLOCK);
            // Public market west, guildhall east, garden and workshops south.
            house(-35,-7,8,8,11,Material.MUD_BRICKS,Material.SMOOTH_SANDSTONE);
            house(35,-7,9,9,14,Material.OXIDIZED_CUT_COPPER,Material.CALCITE);
            house(-35,30,8,8,9,Material.DEEPSLATE_TILES,Material.STRIPPED_BIRCH_WOOD);
            house(35,30,8,8,11,Material.DARK_PRISMARINE,Material.SMOOTH_QUARTZ);
            for(int x:new int[]{-20,20}) for(int z:new int[]{5,18}) stall(x,z,x<0?Material.PURPLE_WOOL:Material.CYAN_WOOL);
            // Arcane fountain placed off the spawn landing and main northbound approach.
            disc(-12,SURFACE, -13,6,Material.POLISHED_ANDESITE);
            ring(-12,SURFACE+1,-13,6,Material.STONE_BRICKS);
            disc(-12,SURFACE+1,-13,5,Material.WATER);
            disc(-12,SURFACE+1,-13,2,Material.QUARTZ_BLOCK);
            crystal(-12,SURFACE+2,-13,7,Material.AMETHYST_BLOCK);
            for(int x:new int[]{-8,8}) for(int z:new int[]{-20,14,38}) lamp(x,SURFACE,z);
            for(int x:new int[]{-47,47}) tree(x,18,9,Material.OAK_LOG,Material.AZALEA_LEAVES);
            // Public station court: clear access in front of every station.
            Material[] stations={Material.CRAFTING_TABLE,Material.ANVIL,Material.ENCHANTING_TABLE,Material.BREWING_STAND,Material.SMITHING_TABLE};
            for(int i=0;i<stations.length;i++) put(-8+i*4,SURFACE+1,26,stations[i]);
            put(0,SURFACE+1,8,Material.LECTERN);
            // Southern arrival arch frames the first view of the citadel.
            arch(0,42,7,9,Material.QUARTZ_BRICKS);
        }
        void arch(int x,int z,int halfWidth,int height,Material stone) {
            box(x-halfWidth,SURFACE+1,z-1,x-halfWidth+1,SURFACE+height,z+1,stone);
            box(x+halfWidth-1,SURFACE+1,z-1,x+halfWidth,SURFACE+height,z+1,stone);
            box(x-halfWidth,SURFACE+height,z-1,x+halfWidth,SURFACE+height+1,z+1,stone);
            for(int step=1;step<halfWidth-1;step++) {
                put(x-halfWidth+step,SURFACE+height-step/2,z,stone);
                put(x+halfWidth-step,SURFACE+height-step/2,z,stone);
            }
            put(x,SURFACE+height+2,z,Material.CHISELED_QUARTZ_BLOCK);
        }
        void quarry() {
            int x=Zone.QUARRY.x,z=Zone.QUARRY.z;
            // Retaining terraces, a mine arch, ore bins and a timber gantry tell the mining story.
            for(int step=0;step<4;step++) box(x-28,SURFACE+step,z-28-step*3,x+28,SURFACE+step,z-26-step*3,Material.TUFF_BRICKS);
            arch(x,z-25,7,8,Material.DEEPSLATE_BRICKS);
            for(int dx:new int[]{-27,27}) {
                box(x+dx,SURFACE+1,z-15,x+dx,SURFACE+13,z-15,Material.STRIPPED_SPRUCE_LOG);
                box(x+dx,SURFACE+13,z-15,x+dx,SURFACE+13,z+3,Material.STRIPPED_SPRUCE_LOG);
                box(x+dx,SURFACE+5,z+3,x+dx,SURFACE+12,z+3,Material.IRON_CHAIN);
                lamp(x+dx,SURFACE,z+16);
            }
            house(x+29,z+22,5,5,6,Material.DEEPSLATE_TILES,Material.MUD_BRICKS);
        }
        void grove() {
            int x=Zone.GROVE.x,z=Zone.GROVE.z;
            // A colossal living tree on the northern ridge is the navigation landmark.
            for(int dx=-2;dx<=2;dx++) for(int dz=-2;dz<=2;dz++) box(x+dx,SURFACE+1,z-29+dz,x+dx,SURFACE+23,z-29+dz,Material.OAK_LOG);
            for(int h=-5;h<=5;h++) disc(x,SURFACE+24+h,z-29,Math.max(4,13-Math.abs(h)),Material.AZALEA_LEAVES);
            arch(x,z-24,5,8,Material.MOSSY_STONE_BRICKS);
            for(Node node:NODES.values()) if(node.zone()==Zone.GROVE && node.cell().y()==SURFACE+3)
                tree(node.cell().x(),node.cell().z(),6,node.material(),Material.OAK_LEAVES);
            for(int dx:new int[]{-29,29}) for(int dz:new int[]{-13,15}) tree(x+dx,z+dz,10,Material.SPRUCE_LOG,Material.SPRUCE_LEAVES);
            house(x+27,z+26,5,5,6,Material.MOSS_BLOCK,Material.STRIPPED_OAK_WOOD);
        }
        void digsite() {
            int x=Zone.DIGSITE.x,z=Zone.DIGSITE.z;
            // An excavated temple façade, fractured columns and a field camp.
            for(int step=0;step<4;step++) box(x-24+step*2,SURFACE+step,z-33,x+24-step*2,SURFACE+step,z-25-step*2,Material.CUT_SANDSTONE);
            arch(x,z-26,8,12,Material.SMOOTH_SANDSTONE);
            for(int dx:new int[]{-23,-14,14,23}) {
                int height=dx==-14?7:12;
                box(x+dx,SURFACE+1,z-27,x+dx+1,SURFACE+height,z-26,Material.SANDSTONE);
                box(x+dx-1,SURFACE+height,z-28,x+dx+2,SURFACE+height,z-25,Material.CHISELED_SANDSTONE);
            }
            stall(x+28,z+21,Material.ORANGE_WOOL); lamp(x-26,SURFACE,z+20);
            box(x-30,SURFACE+1,z-12,x-19,SURFACE+2,z-11,Material.SANDSTONE);
        }
        void harbor() {
            int x=Zone.HARBOR.x,z=Zone.HARBOR.z;
            // Pool remains open sky. Docks, lighthouse, boathouse and a dry-docked skiff frame it.
            box(x-23,SURFACE,z-25,x-20,SURFACE,z+24,Material.SPRUCE_PLANKS);
            box(x+20,SURFACE,z-25,x+23,SURFACE,z+24,Material.SPRUCE_PLANKS);
            box(x-23,SURFACE,z+15,x+23,SURFACE,z+18,Material.SPRUCE_PLANKS);
            for(int dx:new int[]{-22,22}) for(int dz:new int[]{-23,-5,17}) lamp(x+dx,SURFACE,z+dz);
            spire(x+32,SURFACE,z-24,4,22,Material.DARK_PRISMARINE);
            disc(x+32,SURFACE+19,z-24,3,Material.SEA_LANTERN);
            house(x-31,z+25,6,6,7,Material.OXIDIZED_CUT_COPPER,Material.STRIPPED_SPRUCE_WOOD);
            for(int dz=-6;dz<=6;dz++) {
                int width=Math.abs(dz)>4?1:3;
                box(x+32-width,SURFACE+1,z+15+dz,x+32+width,SURFACE+1,z+15+dz,Material.DARK_OAK_PLANKS);
            }
            box(x+32,SURFACE+2,z+15,x+32,SURFACE+10,z+15,Material.STRIPPED_SPRUCE_LOG);
            box(x+33,SURFACE+5,z+15,x+37,SURFACE+9,z+15,Material.WHITE_WOOL);
        }
        void cinder() {
            int x=Zone.CINDER.x,z=Zone.CINDER.z;
            arch(x,z-24,8,14,Material.POLISHED_BLACKSTONE_BRICKS);
            for(int dx:new int[]{-29,29}) spire(x+dx,SURFACE,z-24,4,18,Material.RED_NETHER_BRICKS);
            for(int dx:new int[]{-28,28}) for(int dz:new int[]{0,22}) {
                disc(x+dx,SURFACE+1,z+dz,3,Material.POLISHED_BASALT);
                disc(x+dx,SURFACE+2,z+dz,2,Material.SHROOMLIGHT);
                crystal(x+dx,SURFACE+3,z+dz,7,Material.RED_STAINED_GLASS);
            }
            house(x+29,z+28,6,6,9,Material.DEEPSLATE_TILES,Material.POLISHED_BLACKSTONE_BRICKS);
        }
        void astral() {
            int x=Zone.ASTRAL.x,z=Zone.ASTRAL.z;
            for(int h=0;h<4;h++) ring(x,SURFACE+h,z-29,13-h,Material.PURPUR_BLOCK);
            for(int dx:new int[]{-18,18}) spire(x+dx,SURFACE,z-27,3,16,Material.PURPUR_BLOCK);
            crystal(x,SURFACE+5,z-29,15,Material.AMETHYST_BLOCK);
            for(int dx:new int[]{-28,28}) for(int dz:new int[]{-8,17}) crystal(x+dx,SURFACE+1,z+dz,8,Material.PURPLE_STAINED_GLASS);
            arch(x,z+33,7,10,Material.END_STONE_BRICKS);
        }
        void bridges() {
            for(int direction=0;direction<4;direction++) for(int length=53;length<=112;length++) for(int width=-3;width<=3;width++) {
                int x=direction==0?length:direction==1?-length:width;
                int z=direction==2?length:direction==3?-length:width;
                put(x,SURFACE,z,Material.STONE_BRICKS);
                if(Math.abs(width)==3) put(x,SURFACE+1,z,Material.STONE_BRICK_WALL);
                if(length%12==0 && Math.abs(width)==3) { put(x,SURFACE+2,z,Material.SEA_LANTERN); put(x,SURFACE-1,z,Material.CHISELED_STONE_BRICKS); }
            }
        }
    }
}

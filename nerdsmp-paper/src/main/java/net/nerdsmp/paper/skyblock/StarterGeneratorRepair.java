package net.nerdsmp.paper.skyblock;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.Levelled;

/** Only corrects the second source in a recognizable, otherwise unchanged starter fixture. */
public final class StarterGeneratorRepair {
    private StarterGeneratorRepair() { }
    public static boolean repair(World world,int slot) {
        int x=IslandGrid.blockX(slot),y=IslandGrid.SURFACE_Y,z=IslandGrid.blockZ(slot)-4;
        if(!world.isChunkLoaded(x>>4,z>>4)) return false;
        if(world.getBlockAt(x,y,z+4).getType()!=Material.BEDROCK
            || world.getBlockAt(x+1,y,z).getType()!=Material.COBBLESTONE
            || !source(world,x-1,y,z,Material.WATER) || !source(world,x,y,z,Material.WATER)
            || !source(world,x+2,y,z,Material.LAVA)) return false;
        Material drain=world.getBlockAt(x,y-1,z).getType();
        if(drain!=Material.AIR && drain!=Material.WATER) return false;
        Material floor=world.getBlockAt(x,y-2,z).getType();
        if(floor!=Material.DIRT && floor!=Material.STONE && floor!=Material.ANDESITE) return false;
        // Both historical fixtures have solid grassy or stone-brick side banks.
        for(int dx=-1;dx<=2;dx++) for(int dz:new int[]{-1,1}) {
            Material side=world.getBlockAt(x+dx,y,z+dz).getType();
            if(side!=Material.GRASS_BLOCK && side!=Material.STONE_BRICKS) return false;
        }
        var water=(Levelled)Material.WATER.createBlockData(); water.setLevel(1);
        world.getBlockAt(x,y,z).setBlockData(water,true);
        return true;
    }
    private static boolean source(World world,int x,int y,int z,Material material) {
        var block=world.getBlockAt(x,y,z);
        return block.getType()==material && block.getBlockData() instanceof Levelled fluid && fluid.getLevel()==0;
    }
}

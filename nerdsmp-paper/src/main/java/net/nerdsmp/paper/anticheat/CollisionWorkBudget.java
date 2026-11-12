package net.nerdsmp.paper.anticheat;

import net.nerdsmp.paper.anticheat.physics.MovementSimulator.Box;

/** Never enumerate a teleport-sized prediction volume or grow a move's collision cache without a bound. */
public final class CollisionWorkBudget {
    public static final int QUERY_CELLS=4096, CACHED_CELLS=8192;
    private CollisionWorkBudget(){}
    public static boolean allows(Box area,int minimumY,int maximumY,int cached){
        if(cached<0||cached>=CACHED_CELLS||minimumY>=maximumY)return false;
        double[] values={area.minX(),area.minY(),area.minZ(),area.maxX(),area.maxY(),area.maxZ()};
        for(double value:values)if(!Double.isFinite(value)||Math.abs(value)>31_000_000)return false;
        if(area.minX()>area.maxX()||area.minY()>area.maxY()||area.minZ()>area.maxZ())return false;
        double x=Math.floor(area.maxX())-Math.floor(area.minX())+1,z=Math.floor(area.maxZ())-Math.floor(area.minZ())+1;
        double y=Math.min(maximumY-1,Math.floor(area.maxY()))-Math.max(minimumY,Math.floor(area.minY())-1)+1;
        return x>0&&z>0&&(y<=0||x*z*y<=QUERY_CELLS);
    }
}

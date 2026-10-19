package net.nerdsmp.paper.expedition;

import org.bukkit.Material;
import static net.nerdsmp.paper.expedition.ExpeditionLayout.*;

/** Deterministic landforms and routes. Stable sampling never touches a live world. */
public final class ExpeditionTerrainV2 {
    private ExpeditionTerrainV2() { }
    public static double edge(Zone zone,double angle) {
        return ExpeditionPlanV2.radius(zone)+(zone==Zone.CITY?3:7)*Math.sin(angle*3+.7)
            +(zone==Zone.CITY?2:4)*Math.cos(angle*5-1.2)+2*Math.sin(angle*9+.5);
    }
    public static int height(Zone zone,int x,int z) {
        if(zone==Zone.CITY) return 96; // Architectural terraces have authored retaining walls and stairs.
        if(Math.abs(x)<=25 && z>=-20 && z<=24) return 96; // Reviewed gathering clearing and arrival.
        if(zone==Zone.HARBOR && Math.abs(x)<=24 && z>=-29 && z<=26) return 96;
        if(pathDistance(zone,x,z)<3.2) return 96;
        if(zone==Zone.GROVE) {
            if(Math.abs(x+44)<13 && Math.abs(z-32)<12) return 97;
            if(Math.abs(x+33)<14 && Math.abs(z+37)<12) return 98;
            if(Math.abs(x-41)<10 && Math.abs(z+31)<10) return 99;
        }
        double clearing=Math.clamp((Math.hypot(x,z)-25)/19D,0,1);
        double hills=6*gaussian(x,z,-40,-36,25)+11*gaussian(x,z,34,-46,24)
            +4*gaussian(x,z,53,31,21)+3*Math.sin(x*.065+z*.04)*Math.cos(z*.065);
        double shore=-5*Math.max(0,(Math.hypot(x,z)-60)/23D);
        double detail=valueNoise(x,z,19)*2+valueNoise(x,z,7)*.65;
        return 96+(int)Math.round(clearing*(hills+shore+detail));
    }
    private static double gaussian(double x,double z,double cx,double cz,double radius) {
        return Math.exp(-((x-cx)*(x-cx)+(z-cz)*(z-cz))/(radius*radius));
    }
    public static double pathDistance(Zone zone,double x,double z) {
        if(zone==Zone.CITY) return Math.min(Math.abs(x),Math.abs(z));
        // Curved entries and two loops replace a ruler-straight dirt cross.
        double[][] route=zone==Zone.GROVE ? new double[][]{{80,0},{61,0},{42,4},{25,20},{0,19},{-24,24},{-45,48},{-29,43},{-26,22},{-28,-12},{-33,-37},{0,-40}}
            : new double[][]{{0,80},{3,55},{-7,33},{0,19},{-25,17},{-31,-6},{-19,-27},{8,-28},{28,-11},{27,14},{0,19}};
        double best=Double.MAX_VALUE;
        for(int i=1;i<route.length;i++) best=Math.min(best,segment(x,z,route[i-1][0],route[i-1][1],route[i][0],route[i][1]));
        // Bridge approaches remain step-free despite varied surrounding land.
        if(zone==Zone.QUARRY && x<=-28) best=Math.min(best,Math.abs(z));
        if(zone==Zone.DIGSITE && z<=-28) best=Math.min(best,Math.abs(x));
        if(zone==Zone.HARBOR && z>=28) best=Math.min(best,Math.abs(x));
        return best;
    }
    private static double segment(double x,double z,double ax,double az,double bx,double bz) {
        double t=Math.clamp(((x-ax)*(bx-ax)+(z-az)*(bz-az))/((bx-ax)*(bx-ax)+(bz-az)*(bz-az)),0,1);
        return Math.hypot(x-ax-t*(bx-ax),z-az-t*(bz-az));
    }
    public static int bottom(Zone zone,int x,int z,int top) {
        double distance=Math.hypot(x,z),edge=edge(zone,Math.atan2(z,x));
        double taper=Math.pow(Math.max(0,1-distance/edge),.63);
        double ribs=Math.sin(Math.atan2(z,x)*7+distance*.06)*4+valueNoise(x,z,13)*4;
        return Math.max(8,top-7-(int)(taper*(zone==Zone.CITY?72:57)+ribs));
    }
    public static Material surface(Zone zone,int x,int z) {
        int texture=Math.floorMod(hash(x,z),100);
        if(zone==Zone.CITY) {
            double ring=Math.hypot(x,z);
            if(Math.abs(x)<=3 || Math.abs(z)<=3) return texture<14?Material.ANDESITE:Material.STONE_BRICKS;
            if(ring>58 && ring<63) return texture<30?Material.COBBLESTONE:Material.STONE_BRICKS;
            if(Math.abs(x)>69 || z>62 || x<-46 && z<-40) return texture<15?Material.MOSS_BLOCK:Material.GRASS_BLOCK;
            return texture<14?Material.ANDESITE:texture<23?Material.MOSSY_STONE_BRICKS:Material.STONE_BRICKS;
        }
        if(pathDistance(zone,x,z)<2.2) return texture<30?Material.ROOTED_DIRT:texture<65?Material.COARSE_DIRT:Material.DIRT_PATH;
        return switch(zone) {
            case QUARRY -> texture<32?Material.TUFF:texture<56?Material.GRAVEL:Material.ANDESITE;
            case DIGSITE -> texture<24?Material.CUT_SANDSTONE:texture<45?Material.TERRACOTTA:Material.SANDSTONE;
            case CINDER -> texture<25?Material.BASALT:texture<50?Material.NETHERRACK:Material.BLACKSTONE;
            case ASTRAL -> texture<15?Material.PURPUR_BLOCK:texture<50?Material.END_STONE_BRICKS:Material.END_STONE;
            case GROVE -> texture<17?Material.PODZOL:texture<25?Material.ROOTED_DIRT:Material.GRASS_BLOCK;
            default -> texture<10?Material.COARSE_DIRT:Material.GRASS_BLOCK;
        };
    }
    public static Material rock(Zone zone,int x,int y,int z,int top) {
        if(top-y<3 && (zone==Zone.GROVE || zone==Zone.HARBOR)) return Material.DIRT;
        double seam=y*.16+valueNoise(x,z,24)*1.4;
        int band=Math.floorMod((int)Math.floor(seam),9);
        if(zone==Zone.CINDER) return band<3?Material.BASALT:Material.BLACKSTONE;
        if(zone==Zone.ASTRAL) return band<2?Material.OBSIDIAN:Material.END_STONE;
        if(zone==Zone.DIGSITE) return band<2?Material.TERRACOTTA:Material.SANDSTONE;
        return band<2?Material.DEEPSLATE:band<4?Material.TUFF:band==6?Material.ANDESITE:Material.STONE;
    }
    public static int hash(int x,int z) { int h=x*374761393+z*668265263; h=(h^(h>>>13))*1274126177; return h^(h>>>16); }
    private static double valueNoise(int x,int z,int scale) {
        int ax=Math.floorDiv(x,scale),az=Math.floorDiv(z,scale);
        double u=Math.floorMod(x,scale)/(double)scale,v=Math.floorMod(z,scale)/(double)scale; u=u*u*(3-2*u);v=v*v*(3-2*v);
        double a=(hash(ax,az)&65535)/32767.5-1,b=(hash(ax+1,az)&65535)/32767.5-1;
        double c=(hash(ax,az+1)&65535)/32767.5-1,d=(hash(ax+1,az+1)&65535)/32767.5-1;
        return (a+(b-a)*u)*(1-v)+(c+(d-c)*u)*v;
    }
}

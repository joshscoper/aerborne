package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

/**
 * Authored woodland composition for the second expedition world, in grove-local coordinates.
 * The clearing is deliberately open below its canopy: only the finite node catalog supplies
 * harvestable trunks. Nothing here touches a live world, creates loot, or places NPCs.
 */
public final class WindrootV2 {
    private static final int ORIGIN_X = -256;
    private static final String LEAVES = "persistent=true,distance=1,waterlogged=false";
    private final BuildCanvas canvas;

    public static void build(BuildCanvas canvas) {
        new WindrootV2(canvas).build();
    }

    private WindrootV2(BuildCanvas canvas) { this.canvas = canvas; }

    private void build() {
        woodlandPaths();
        sanctuary();
        spring();
        ancientTree();
        forestersLodge();
        forestCanopy();
        harvestCanopy();
        woodlandFloor();
        lodgeApproach();
        arrival();
    }

    private void woodlandPaths() {
        path(new int[][]{{77,0},{64,0},{53,2},{43,6},{34,15},{25,21},{12,24},{0,21},
            {-12,23},{-22,29},{-30,37},{-38,45},{-45,48}}, 2);
        path(new int[][]{{1,19},{1,8},{2,-2},{-1,-13},{-4,-22},{-9,-29},{-10,-38}}, 2);
        path(new int[][]{{-11,-26},{-22,-28},{-30,-30},{-34,-34}}, 1);
        path(new int[][]{{31,16},{36,3},{36,-9},{31,-17},{30,-26}}, 1);
        path(new int[][]{{-24,29},{-29,19},{-33,6},{-40,-2},{-43,-16}}, 1);
        // A pair of old milestone posts establishes the east approach without a boxed gate.
        for (int z : new int[]{-5,5}) {
            int y = ground(65,z);
            stoneBox(64,y-1,z-1,66,y+2,z+1,83+z);
            block(65,y+3,z,Material.MOSSY_STONE_BRICK_WALL);
            block(65,y+4,z,Material.LANTERN,"hanging=false,waterlogged=false");
            block(64,y+1,z+(z<0?-2:2),Material.MOSS_BLOCK);
        }
    }

    private void path(int[][] points, int width) {
        for (int i=1;i<points.length;i++) {
            int[] a=points[i-1], c=points[i];
            int steps=Math.max(Math.abs(c[0]-a[0]),Math.abs(c[1]-a[1]));
            for (int s=0;s<=steps;s++) {
                int x=(int)Math.round(a[0]+(c[0]-a[0])*(double)s/Math.max(steps,1));
                int z=(int)Math.round(a[1]+(c[1]-a[1])*(double)s/Math.max(steps,1));
                for(int dx=-width;dx<=width;dx++) for(int dz=-width;dz<=width;dz++) {
                    if (dx*dx+dz*dz>width*width+1) continue;
                    int px=x+dx,pz=z+dz,h=hash(px,pz,719);
                    Material m=h%11==0?Material.MOSSY_COBBLESTONE:h%7==0?Material.GRAVEL:
                        h%3==0?Material.ROOTED_DIRT:Material.COARSE_DIRT;
                    block(px,ground(px,pz),pz,m);
                }
            }
        }
    }

    private void ancientTree() {
        int y=ground(1,-43);
        // Leaning, tapering trunk; high forks have individual silhouettes rather than a cylinder.
        woodTube(new int[][]{{1,y,-43},{0,y+8,-43},{2,y+16,-44},{1,y+25,-43},{-2,y+34,-44}},
            5.5,1.7,Material.DARK_OAK_WOOD,92);
        woodTube(new int[][]{{2,y+12,-43},{7,y+21,-41},{11,y+28,-39},{14,y+33,-36}},
            3.4,.9,Material.DARK_OAK_WOOD,19);
        woodTube(new int[][]{{0,y+16,-43},{-7,y+23,-45},{-12,y+28,-43},{-18,y+31,-42}},
            3.0,.9,Material.DARK_OAK_WOOD,31);
        woodTube(new int[][]{{1,y+22,-44},{2,y+29,-51},{0,y+35,-57},{-3,y+39,-60}},
            2.6,.65,Material.DARK_OAK_WOOD,57);
        woodTube(new int[][]{{3,y+17,-42},{4,y+23,-35},{0,y+29,-29},{-3,y+33,-27}},
            2.7,.7,Material.DARK_OAK_WOOD,72);
        woodTube(new int[][]{{-4,y+23,-44},{-11,y+29,-50},{-16,y+34,-51}},
            1.9,.6,Material.DARK_OAK_WOOD,41);
        woodTube(new int[][]{{7,y+23,-41},{13,y+28,-47},{19,y+32,-49}},
            1.9,.65,Material.DARK_OAK_WOOD,44);
        // Buttress roots descend to actual terrain and snake out in separate directions.
        root(1,-43,-13,-29,y,101);
        root(2,-43,17,-28,y,102);
        root(2,-44,23,-48,y,103);
        root(-1,-44,-16,-57,y,104);
        root(1,-46,6,-64,y,105);
        root(-3,-42,-22,-40,y,106);
        // Bark scars, shelving fungus and luminous lichens are sparse details, not stripes.
        for(int i=0;i<9;i++) {
            int py=y+3+i*2;
            block(-4+(i/5),py,-43+(i%3),i%3==0?Material.MOSS_BLOCK:Material.STRIPPED_DARK_OAK_WOOD);
        }
        for(int i=0;i<4;i++) mushroomShelf(5+i/2,y+6+i*5,-41+i%2,2,210+i);
        foliage(-17,y+33,-41,12,7,9,Material.OAK_LEAVES,111);
        foliage(-8,y+38,-46,12,8,10,Material.OAK_LEAVES,112);
        foliage(3,y+40,-47,12,9,12,Material.OAK_LEAVES,113);
        foliage(15,y+35,-37,13,7,11,Material.OAK_LEAVES,114);
        foliage(20,y+35,-50,9,7,9,Material.AZALEA_LEAVES,115);
        foliage(-4,y+42,-59,12,7,9,Material.OAK_LEAVES,116);
        foliage(-18,y+37,-53,10,6,10,Material.AZALEA_LEAVES,117);
        foliage(-1,y+36,-29,12,7,10,Material.OAK_LEAVES,118);
        foliage(10,y+42,-43,8,6,7,Material.FLOWERING_AZALEA_LEAVES,119);
        // Small hanging lights illuminate the root court, with vertical supports into limbs.
        hangingLight(-12,y+28,-40,7);
        hangingLight(8,y+29,-34,9);
        hangingLight(14,y+31,-46,6);
        hangingLight(-2,y+30,-29,8);
        // Root-court benches and a worked stone shrine at a human rather than monumental scale.
        int py=ground(-10,-34);
        stoneBox(-13,py-1,-36,-8,py,-32,307);
        block(-11,py+1,-35,Material.CHISELED_STONE_BRICKS);
        block(-11,py+2,-35,Material.MOSSY_STONE_BRICK_SLAB,"type=bottom,waterlogged=false");
        block(-11,py+3,-35,Material.FLOWERING_AZALEA);
        bench(-17,ground(-17,-31)+1,-31,4,"north");
    }

    private void root(int x,int z,int tx,int tz,int base,int seed) {
        int end=ground(tx,tz);
        woodTube(new int[][]{{x,base+4,z},{(x+tx)/2,Math.max(base+1,end+1),(z+tz)/2},
            {tx,end,tz}},2.5,.65,Material.DARK_OAK_WOOD,seed);
        block(tx,end+1,tz,Material.MOSS_CARPET);
    }

    private void forestCanopy() {
        // Every tree has an individually directed fork; gaps frame lodge, spring and sanctuary.
        tree(-57,-20,18,8,5,1);
        tree(-58,4,20,9,-5,2);
        tree(-35,-5,16,7,3,3);
        tree(-44,-56,20,8,-3,4);
        tree(-24,-64,18,9,4,5);
        tree(26,-55,21,9,5,6);
        tree(56,-22,18,8,-5,7);
        tree(55,17,21,10,4,8);
        tree(33,41,20,9,-4,9);
        tree(7,54,24,11,4,10);
        tree(-17,55,17,9,-3,11);
        tree(-56,43,16,8,4,12);
        tree(42,57,15,7,-2,13);
        tree(-37,59,14,7,3,14);
        // Fallen trunks become compositional diagonals, not identical decorative stumps.
        woodTube(new int[][]{{29,ground(29,30)+1,30},{35,ground(35,35)+1,35},
            {44,ground(44,39)+1,39}},1.4,.9,Material.OAK_WOOD,932);
        block(29,ground(29,30)+1,30,Material.STRIPPED_OAK_LOG,"axis=x");
        for(int i=0;i<7;i++) block(33+i,ground(33+i,34+i/2)+3,34+i/2,Material.MOSS_CARPET);
        woodTube(new int[][]{{-55,ground(-55,-38)+1,-38},{-49,ground(-49,-43)+1,-43}},
            1.0,.8,Material.BIRCH_WOOD,940);
    }

    private void tree(int x,int z,int height,int spread,int lean,int seed) {
        int y=ground(x,z);
        Material wood=seed%5==0?Material.BIRCH_WOOD:seed%3==0?Material.SPRUCE_WOOD:Material.OAK_WOOD;
        Material leaf=seed%3==0?Material.DARK_OAK_LEAVES:Material.OAK_LEAVES;
        int crownX=x+lean;
        woodTube(new int[][]{{x,y,z},{x+lean/3,y+height/2,z-1},{crownX,y+height,z+1}},
            1.6,.55,wood,seed*11);
        woodTube(new int[][]{{x+lean/3,y+height/2,z},{x-4,y+height-3,z+4},
            {x-7,y+height,z+5}},1.1,.45,wood,seed*11+1);
        woodTube(new int[][]{{x+lean/2,y+height/2+2,z},{x+4,y+height-2,z-4},
            {x+7,y+height,z-5}},1.05,.45,wood,seed*11+2);
        for(int side=-1;side<=1;side+=2)
            woodTube(new int[][]{{x,y+2,z},{x+side*3,ground(x+side*3,z+2),z+2}},
                1.0,.5,wood,seed+side);
        foliage(crownX,y+height+2,z,spread,5,spread-1,leaf,seed*31);
        foliage(x-6,y+height,z+5,spread-2,4,spread-2,leaf,seed*31+1);
        foliage(x+6,y+height+1,z-5,spread-2,4,spread-2,leaf,seed*31+2);
        foliage(crownX+1,y+height+6,z-1,spread-3,4,spread-3,leaf,seed*31+3);
        if(seed%3==0) hangingLight(x+4,y+height-1,z-4,5);
    }

    private void harvestCanopy() {
        int[][] clusters={{-14,-11},{13,-12},{-14,8},{14,8}};
        int[][] offsets={{-6,-3},{0,-5},{6,-2},{-7,3},{-1,1},{5,4},{0,7}};
        Material[] woods={Material.OAK_WOOD,Material.BIRCH_WOOD,Material.SPRUCE_WOOD,Material.JUNGLE_WOOD};
        Material[] leaves={Material.OAK_LEAVES,Material.BIRCH_LEAVES,Material.SPRUCE_LEAVES,Material.JUNGLE_LEAVES};
        for(int row=0;row<4;row++) for(int col=0;col<7;col++) {
            int x=clusters[row][0]+offsets[col][0],z=clusters[row][1]+offsets[col][1];
            int seed=170+row*17+col;
            int species=(row+col)%woods.length;
            int top=104+(seed%4);
            // Deliberately no permanent wood in the three harvest slots (Y97 through Y99).
            for(int y=100;y<=top;y++) block(x,y,z,woods[species]);
            int lean=col%2==0?2:-2;
            woodTube(new int[][]{{x,top-1,z},{x+lean,top+2,z+1}},.65,.4,woods[species],seed);
            foliage(x+lean,top+4,z+1,species==2?3:4+col%2,species==2?4:3,4,leaves[species],seed);
            if(species==2) foliage(x+lean,top+8,z+1,2,3,2,leaves[species],seed+12);
            else if(col%3==1) foliage(x-lean,top+3,z-2,3,2,3,leaves[species],seed+13);
        }
    }

    private void sanctuary() {
        int x=-34,z=-36,y=ground(x,z);
        // Broken, octagonal apse: only portions of the walls survived beneath the roots.
        for(int dx=-10;dx<=10;dx++) for(int dz=-8;dz<=8;dz++) {
            if(Math.abs(dx)+Math.abs(dz)>15) continue;
            int h=hash(dx,dz,403);
            block(x+dx,y,z+dz,h%7==0?Material.MOSS_BLOCK:h%4==0?Material.CRACKED_STONE_BRICKS:
                Material.MOSSY_STONE_BRICKS);
            block(x+dx,y-1,z+dz,Material.TUFF);
        }
        for(int dx=-8;dx<=8;dx++) {
            int h=dx<-2?4:dx>4?2:1;
            stoneBox(x+dx,y+1,z-7,x+dx,y+h,z-7,dx+29);
            if(dx%3==0) block(x+dx,y+h+1,z-7,Material.MOSSY_STONE_BRICK_SLAB,"type=bottom,waterlogged=false");
        }
        for(int dz=-6;dz<=2;dz++)
            stoneBox(x-9,y+1,z+dz,x-9,y+2+Math.floorMod(dz,3),z+dz,59+dz);
        for(int[] p:new int[][]{{-7,-5},{7,-5},{-7,5},{7,5}}) {
            int height=p[0]<0?8:p[1]<0?6:3;
            stoneBox(x+p[0]-1,y+1,z+p[1]-1,x+p[0]+1,y+1,z+p[1]+1,818);
            for(int i=2;i<=height;i++) block(x+p[0],y+i,z+p[1],
                i%4==0?Material.CHISELED_STONE_BRICKS:Material.MOSSY_STONE_BRICKS);
            block(x+p[0],y+height+1,z+p[1],Material.MOSSY_STONE_BRICK_SLAB,"type=bottom,waterlogged=false");
        }
        // A surviving arch and a collapsed column explain the ruin's original shape.
        for(int dx=-7;dx<=0;dx++) {
            int rise=Math.min(3,Math.min(dx+7,-dx));
            block(x+dx,y+8+rise,z-5,Material.MOSSY_STONE_BRICKS);
        }
        for(int i=0;i<6;i++) {
            block(x+4-i,y+1,z+2+i/2,Material.QUARTZ_PILLAR,"axis=x");
            if(i%2==0) block(x+4-i,y+2,z+2+i/2,Material.MOSS_CARPET);
        }
        stoneBox(x-2,y+1,z-3,x+2,y+1,z-1,109);
        block(x,y+2,z-2,Material.CHISELED_STONE_BRICKS);
        block(x,y+3,z-2,Material.CRYING_OBSIDIAN);
        block(x,y+4,z-2,Material.SMALL_AMETHYST_BUD,"facing=up,waterlogged=false");
        woodTube(new int[][]{{x-12,y+1,z-1},{x-8,y+3,z-5},{x-4,y+4,z-7},{x+1,y+2,z-8}},
            1.25,.55,Material.DARK_OAK_WOOD,739);
        foliage(x-8,y+4,z-7,4,2,3,Material.AZALEA_LEAVES,740);
        foliage(x+6,y+2,z-5,3,2,3,Material.FLOWERING_AZALEA_LEAVES,741);
        for(int i=0;i<7;i++) block(x-8+i,y+1,z+6,Material.MOSS_CARPET);
    }

    private void spring() {
        int cx=42,cz=-30,water=ground(cx,cz)-1;
        // Explicit floor and retaining bank contain source water even after neighbor updates.
        for(int dx=-13;dx<=13;dx++) for(int dz=-10;dz<=10;dz++) {
            double edge=dx*dx/144.0+dz*dz/64.0;
            if(edge>1.25) continue;
            int x=cx+dx,z=cz+dz;
            if(edge<.83) {
                box(x,water-3,z,x,water+8,z,Material.AIR);
                block(x,water-3,z,hash(x,z,423)%3==0?Material.CLAY:Material.GRAVEL);
                for(int y=water-2;y<=water;y++) block(x,y,z,Material.WATER);
                if(hash(x,z,49)%23==0) block(x,water+1,z,Material.LILY_PAD);
            } else {
                stoneBox(x,water-3,z,x,water+1,z,633);
                if(hash(x,z,66)%4==0) block(x,water+2,z,Material.MOSS_CARPET);
            }
        }
        // Short fall has a sealed back and sides and discharges entirely inside the pool.
        int fx=cx+2,fz=cz-6;
        for(int y=water+1;y<=water+8;y++) {
            stoneBox(fx-2,y,fz-2,fx+2,y,fz,312+y);
            block(fx-1,y,fz+1,Material.MOSSY_COBBLESTONE);
            block(fx+1,y,fz+1,Material.MOSSY_COBBLESTONE);
            block(fx,y,fz+1,Material.WATER);
        }
        stoneBox(fx-2,water+9,fz-2,fx+2,water+9,fz,357);
        foliage(fx+1,water+10,fz-1,4,2,3,Material.AZALEA_LEAVES,358);
        for(int[] p:new int[][]{{-9,-5},{-10,2},{7,5},{10,-2},{-3,7}}) {
            int x=cx+p[0],z=cz+p[1];
            if(p[0]>0) boulder(x,water+1,z,2,170+p[0]);
            else {
                block(x,water+1,z,Material.MOSS_BLOCK);
                block(x,water+2,z,Material.FERN);
            }
        }
        // Small lookout deck sits on stone piers beside, not over, the walking arrival.
        box(26,water+1,-31,29,water+1,-24,Material.SPRUCE_PLANKS);
        for(int z:new int[]{-31,-24}) {
            box(26,water-2,z,26,water+2,z,Material.STRIPPED_SPRUCE_LOG);
            block(26,water+3,z,Material.LANTERN,"hanging=false,waterlogged=false");
        }
        bench(27,water+2,-29,3,"east");
    }

    private void forestersLodge() {
        int x=-45,z=29,y=98;
        // Deep masonry plinth handles undulating terrain, while the house is actually enterable.
        stoneBox(x-9,91,z-10,x+9,y,z+10,816);
        box(x-8,y+1,z-9,x+8,y+17,z+9,Material.AIR);
        box(x-8,y,z-9,x+8,y,z+9,Material.SPRUCE_PLANKS);
        for(int px=x-8;px<=x+8;px++) for(int pz=z-9;pz<=z+9;pz++) {
            if(px!=x-8 && px!=x+8 && pz!=z-9 && pz!=z+9) continue;
            for(int py=y+1;py<=y+8;py++) block(px,py,pz,
                py<=2+y?Material.STONE_BRICKS:hash(px,pz,py)%7==0?Material.CALCITE:Material.WHITE_TERRACOTTA);
        }
        for(int px : new int[]{x-8,x-4,x+4,x+8}) for(int pz:new int[]{z-9,z+9})
            box(px,y+1,pz,px,y+9,pz,Material.STRIPPED_DARK_OAK_LOG);
        for(int pz : new int[]{z-9,z-3,z+3,z+9}) for(int px:new int[]{x-8,x+8})
            box(px,y+1,pz,px,y+9,pz,Material.STRIPPED_DARK_OAK_LOG);
        for(int level:new int[]{3,7,9}) {
            line(x-8,y+level,z-9,x+8,y+level,z-9,Material.DARK_OAK_WOOD);
            line(x-8,y+level,z+9,x+8,y+level,z+9,Material.DARK_OAK_WOOD);
            line(x-8,y+level,z-9,x-8,y+level,z+9,Material.DARK_OAK_WOOD);
            line(x+8,y+level,z-9,x+8,y+level,z+9,Material.DARK_OAK_WOOD);
        }
        // Jettied front balcony, upper gallery and a real internal stair.
        box(x-7,y+7,z-8,x+7,y+7,z-1,Material.SPRUCE_PLANKS);
        box(x-7,y+7,z,x-5,y+7,z+8,Material.SPRUCE_PLANKS);
        box(x-7,y+7,z+7,x+7,y+7,z+8,Material.SPRUCE_PLANKS);
        box(x+5,y+7,z,x+6,y+7,z,Material.SPRUCE_PLANKS);
        for(int i=0;i<7;i++) {
            block(x+5,y+1+i,z+7-i,Material.SPRUCE_STAIRS,"facing=north,half=bottom,shape=straight,waterlogged=false");
            block(x+6,y+1+i,z+7-i,Material.SPRUCE_STAIRS,"facing=north,half=bottom,shape=straight,waterlogged=false");
        }
        for(int px=x-6;px<=x+4;px++) block(px,y+8,z-1,Material.SPRUCE_FENCE,
            "north=false,south=false,east=true,west=true,waterlogged=false");
        for(int pz=z;pz<=z+6;pz++) block(x-5,y+8,pz,Material.SPRUCE_FENCE,
            "north=true,south=true,east=false,west=false,waterlogged=false");
        for(int px=x-4;px<=x+3;px++) block(px,y+8,z+7,Material.SPRUCE_FENCE,
            "north=false,south=false,east=true,west=true,waterlogged=false");
        box(x-4,y+7,z+10,x+4,y+7,z+12,Material.SPRUCE_PLANKS);
        for(int px=x-4;px<=x+4;px++) block(px,y+8,z+12,Material.SPRUCE_FENCE,
            "north="+(Math.abs(px-x)==4)+",south=false,east="+(px<x+4)+",west="+(px>x-4)+",waterlogged=false");
        for(int px:new int[]{x-4,x+4}) {
            box(px,y+1,z+12,px,y+6,z+12,Material.STRIPPED_DARK_OAK_LOG);
            block(px,y+6,z+11,Material.DARK_OAK_STAIRS,"facing="+(px<x?"west":"east")+",half=top,shape=straight,waterlogged=false");
            for(int pz=z+10;pz<=z+11;pz++) block(px,y+8,pz,Material.SPRUCE_FENCE,
                "north=true,south=true,east=false,west=false,waterlogged=false");
        }
        // Steep, layered roof, overhanging eaves, ridge caps and timber framed gables.
        for(int dx=-10;dx<=10;dx++) {
            int rise=10-Math.abs(dx);
            for(int pz=z-11;pz<=z+11;pz++) {
                int py=y+9+rise;
                Material roof=hash(dx,pz,782)%11==0?Material.MOSSY_COBBLESTONE_STAIRS:Material.SPRUCE_STAIRS;
                block(x+dx,py,pz,roof,"facing="+(dx<0?"east":"west")+",half=bottom,shape=straight,waterlogged=false");
                if(Math.abs(dx)==10) block(x+dx,py-1,pz,Material.DARK_OAK_SLAB,"type=top,waterlogged=false");
                if((pz==z-9 || pz==z+9) && Math.abs(dx)<8)
                    for(int py2=y+10;py2<py;py2++) block(x+dx,py2,pz,
                        dx==0||py2==y+13?Material.DARK_OAK_WOOD:Material.WHITE_TERRACOTTA);
            }
        }
        for(int pz=z-12;pz<=z+12;pz++) block(x,y+20,pz,Material.DARK_OAK_SLAB,"type=bottom,waterlogged=false");
        for(int pz:new int[]{z-12,z+12}) block(x,y+21,pz,Material.DARK_OAK_FENCE);
        // Two-story windows sit in recessed, shuttered bays, not blank wall faces.
        for(int side:new int[]{-1,1}) for(int pz:new int[]{z-6,z,z+6}) {
            int px=x+side*8;
            for(int py=y+4;py<=y+6;py++) for(int dz=-1;dz<=1;dz++)
                block(px,py,pz+dz,Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                    "north=true,south=true,east=false,west=false,waterlogged=false");
            for(int dz:new int[]{-2,2}) for(int py=y+4;py<=y+6;py++)
                block(px+side,py,pz+dz,Material.SPRUCE_TRAPDOOR,
                    "facing="+(side<0?"west":"east")+",half=bottom,open=true,powered=false,waterlogged=false");
            block(px+side,y+3,pz,Material.SPRUCE_STAIRS,
                "facing="+(side<0?"west":"east")+",half=top,shape=straight,waterlogged=false");
            block(px+side,y+4,pz,Material.POTTED_FERN);
        }
        for(int dx:new int[]{-6,6}) {
            for(int py=y+4;py<=y+6;py++) block(x+dx,py,z+9,Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                "north=false,south=false,east=true,west=true,waterlogged=false");
            block(x+dx,y+3,z+10,Material.SPRUCE_SLAB,"type=top,waterlogged=false");
            block(x+dx,y+4,z+10,Material.POTTED_RED_TULIP);
        }
        for(int px=x-1;px<=x+1;px++) for(int py=y+11;py<=y+14;py++)
            block(px,py,z+9,Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                "north=false,south=false,east=true,west=true,waterlogged=false");
        // A roofed porch and broad steps connect the dirt trail with no jump requirement.
        box(x-3,y,z+10,x+3,y,z+13,Material.SPRUCE_PLANKS);
        box(x-1,y+1,z+9,x+1,y+3,z+9,Material.AIR);
        block(x,y+1,z+9,Material.SPRUCE_DOOR,"facing=south,half=lower,hinge=left,open=false,powered=false");
        block(x,y+2,z+9,Material.SPRUCE_DOOR,"facing=south,half=upper,hinge=left,open=false,powered=false");
        block(x,y+8,z+9,Material.AIR);
        block(x,y+9,z+9,Material.AIR);
        for(int px:new int[]{x-2,x+2}) block(px,y+4,z+10,Material.LANTERN,"hanging=true,waterlogged=false");
        // Furnished downstairs: hearth, tool bench, shelving, table and a bed in the gallery.
        stoneBox(x-6,y+1,z-7,x-2,y+4,z-5,490);
        box(x-5,y+1,z-5,x-3,y+2,z-5,Material.AIR);
        block(x-4,y+1,z-6,Material.CAMPFIRE,"facing=south,lit=true,signal_fire=false,waterlogged=false");
        for(int px=x-5;px<=x-3;px++) block(px,y+1,z-5,Material.IRON_BARS,
            "north=false,south=false,east=true,west=true,waterlogged=false");
        stoneBox(x-5,y+5,z-7,x-3,y+23,z-6,495);
        box(x-6,y+24,z-8,x-2,y+24,z-5,Material.STONE_BRICK_SLAB);
        block(x-4,y+23,z-6,Material.CAMPFIRE,"facing=south,lit=true,signal_fire=false,waterlogged=false");
        block(x+6,y+1,z-6,Material.CRAFTING_TABLE);
        block(x+6,y+1,z-5,Material.SMITHING_TABLE);
        block(x+6,y+1,z-4,Material.BARREL,"facing=up,open=false");
        block(x+6,y+1,z-3,Material.FLETCHING_TABLE);
        for(int pz=z-7;pz<=z-3;pz+=2) block(x+7,y+4,pz,Material.SPRUCE_SLAB,"type=bottom,waterlogged=false");
        block(x+7,y+5,z-5,Material.POTTED_OAK_SAPLING);
        for(int px=x-2;px<=x+1;px++) block(px,y+1,z+2,Material.SPRUCE_FENCE);
        box(x-2,y+2,z+1,x+1,y+2,z+3,Material.SPRUCE_SLAB);
        bench(x-2,y+1,z,4,"south");
        bench(x-2,y+1,z+4,4,"north");
        hangingLight(x,y+8,z+2,2);
        for(int px=x-7;px<=x-5;px++) box(px,y+8,z-8,px,y+10,z-8,Material.BOOKSHELF);
        block(x+2,y+8,z-7,Material.GREEN_BED,"facing=north,part=foot,occupied=false");
        block(x+2,y+8,z-8,Material.GREEN_BED,"facing=north,part=head,occupied=false");
        block(x+3,y+8,z-8,Material.BARREL,"facing=up,open=false");
        block(x+3,y+9,z-8,Material.LANTERN,"hanging=false,waterlogged=false");
        // Attached log shelter gives the house an asymmetrical roofline and a practical identity.
        for(int px=x+10;px<=x+15;px++) for(int pz=z-5;pz<=z+5;pz++) {
            block(px,y-1,pz,Material.COBBLESTONE);
            block(px,y,pz,Material.COARSE_DIRT);
            block(px,y+5-(px-(x+10))/2,pz,Material.SPRUCE_SLAB,"type=top,waterlogged=false");
        }
        for(int pz:new int[]{z-5,z+5}) box(x+15,y+1,pz,x+15,y+4,pz,Material.STRIPPED_SPRUCE_LOG);
        for(int px=x+11;px<=x+14;px++) for(int py=y+1;py<=y+2;py++)
            block(px,py,z-4,Material.OAK_LOG,"axis=z");
        block(x+12,y+1,z+3,Material.GRINDSTONE,"face=floor,facing=east");
        block(x+11,y+1,z+3,Material.OAK_LOG,"axis=y");
        block(x+11,y+2,z+3,Material.STONE_BUTTON,"face=floor,facing=north,powered=false");
        block(x+14,y+1,z+4,Material.BARREL,"facing=up,open=false");
        block(x+14,y+2,z+4,Material.LANTERN,"hanging=false,waterlogged=false");
    }

    private void woodlandFloor() {
        int[][] groupings={{-60,-28},{-49,-15},{-38,-13},{-49,-52},{-24,-57},{18,-59},
            {56,-36},{59,5},{46,28},{26,46},{11,62},{-20,47},{-49,52},{-61,23},
            {28,-12},{-28,10},{-32,-25},{21,31}};
        for(int g=0;g<groupings.length;g++) {
            int cx=groupings[g][0],cz=groupings[g][1];
            for(int dx=-5;dx<=5;dx++) for(int dz=-4;dz<=4;dz++) {
                if(dx*dx/25.0+dz*dz/16.0>1.0) continue;
                int x=cx+dx,z=cz+dz,y=ground(x,z),n=hash(x,z,g*17);
                if(Math.abs(x)<=25&&Math.abs(z)<=20) continue;
                if(n%4==0) block(x,y,z,n%3==0?Material.ROOTED_DIRT:Material.PODZOL);
                if(n%11==0) block(x,y+1,z,Material.FERN);
                else if(n%17==0) block(x,y+1,z,Material.BROWN_MUSHROOM);
                else if(n%23==0) block(x,y+1,z,Material.RED_MUSHROOM);
                else if(n%29==0) block(x,y+1,z,Material.FLOWERING_AZALEA);
                else if(n%7==0) block(x,y+1,z,Material.MOSS_CARPET);
            }
            if(g%3==0) boulder(cx+2,ground(cx+2,cz),cz,2,g+811);
            if(g%4==1) foliage(cx,ground(cx,cz)+1,cz,3,1,2,Material.AZALEA_LEAVES,g+913);
        }
        // Lantern waymarkers are intentionally clustered at decisions rather than a regular grid.
        for(int[] p:new int[][]{{40,11},{26,24},{-22,33},{-28,5},{-8,-26},{28,-15}}) {
            int y=ground(p[0],p[1]);
            block(p[0],y+1,p[1],Material.MOSSY_COBBLESTONE_WALL);
            block(p[0],y+2,p[1],Material.LANTERN,"hanging=false,waterlogged=false");
        }
    }

    private void lodgeApproach() {
        // Grade the short approach after planting so there is an uninterrupted stair to the porch.
        for(int z=43;z<=51;z++) for(int x=-47;x<=-43;x++) {
            int floor=Math.max(96,98-(z-43));
            box(x,91,z,x,floor-1,z,Material.COBBLESTONE);
            box(x,floor+1,z,x,104,z,Material.AIR);
            if(z<=45) block(x,floor,z,Material.MOSSY_COBBLESTONE_STAIRS,
                "facing=north,half=bottom,shape=straight,waterlogged=false");
            else block(x,floor,z,hash(x,z,66)%3==0?Material.ROOTED_DIRT:Material.COARSE_DIRT);
        }
    }

    private void arrival() {
        // Leave the full warp capsule clear regardless of canopy or future planting changes.
        for(int x=-2;x<=3;x++) for(int z=17;z<=22;z++) {
            block(x,96,z,hash(x,z,71)%3==0?Material.MOSSY_STONE_BRICKS:Material.STONE_BRICKS);
            for(int y=97;y<=101;y++) block(x,y,z,Material.AIR);
        }
        for(int x:new int[]{-3,4}) {
            block(x,97,20,Material.MOSSY_STONE_BRICK_WALL);
            block(x,98,20,Material.LANTERN,"hanging=false,waterlogged=false");
        }
    }

    private void foliage(int x,int y,int z,int rx,int ry,int rz,Material material,int seed) {
        for(int dx=-rx-1;dx<=rx+1;dx++) for(int dy=-ry;dy<=ry;dy++) for(int dz=-rz-1;dz<=rz+1;dz++) {
            double shape=dx*dx/(double)(rx*rx)+dy*dy/(double)(ry*ry)+dz*dz/(double)(rz*rz);
            double ripple=.10*Math.sin(dx*.9+seed)+.11*Math.cos(dz*.78-seed)+.07*Math.sin(dy*1.7+dx*.4);
            if(shape>1+ripple || (shape>.84&&hash(dx,dy+dz*7,seed)%13==0)) continue;
            if(Math.abs(x+dx)<=25 && Math.abs(z+dz)<=20 && y+dy<104) continue;
            Material leaf=material;
            if(material==Material.OAK_LEAVES&&hash(dx,dy+dz,seed)%19==0) leaf=Material.AZALEA_LEAVES;
            block(x+dx,y+dy,z+dz,leaf,LEAVES);
        }
    }

    private void woodTube(int[][] points,double start,double end,Material wood,int seed) {
        double total=0;
        for(int i=1;i<points.length;i++) total+=distance(points[i-1],points[i]);
        double traversed=0;
        for(int i=1;i<points.length;i++) {
            int[] a=points[i-1],b=points[i];
            double length=distance(a,b);
            int steps=Math.max(1,(int)Math.ceil(length*1.8));
            for(int s=0;s<=steps;s++) {
                double t=s/(double)steps,progress=(traversed+t*length)/Math.max(total,1);
                double radius=start+(end-start)*progress;
                int cx=(int)Math.round(a[0]+(b[0]-a[0])*t),cy=(int)Math.round(a[1]+(b[1]-a[1])*t),cz=(int)Math.round(a[2]+(b[2]-a[2])*t);
                int bound=(int)Math.ceil(radius);
                for(int dx=-bound;dx<=bound;dx++) for(int dy=-bound;dy<=bound;dy++) for(int dz=-bound;dz<=bound;dz++) {
                    double r=radius+(hash(cx+dx,cz+dz,seed)%5-2)*.06;
                    if(dx*dx+dy*dy+dz*dz>r*r+.28) continue;
                    String axis=Math.abs(b[0]-a[0])>Math.abs(b[1]-a[1])?"x":Math.abs(b[2]-a[2])>Math.abs(b[1]-a[1])?"z":"y";
                    block(cx+dx,cy+dy,cz+dz,wood,"axis="+axis);
                }
            }
            traversed+=length;
        }
    }

    private static double distance(int[] a,int[] b) {
        return Math.sqrt(Math.pow(a[0]-b[0],2)+Math.pow(a[1]-b[1],2)+Math.pow(a[2]-b[2],2));
    }

    private void mushroomShelf(int x,int y,int z,int radius,int seed) {
        for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
            if(dx*dx+dz*dz>radius*radius || hash(dx,dz,seed)%9==0) continue;
            block(x+dx,y,z+dz,Material.BROWN_MUSHROOM_BLOCK);
        }
    }

    private void boulder(int x,int y,int z,int radius,int seed) {
        for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) for(int dy=0;dy<=radius;dy++)
            if(dx*dx+dz*dz+dy*dy<radius*radius+1)
                block(x+dx,y+dy,z+dz,hash(dx,dz,seed)%3==0?Material.MOSSY_COBBLESTONE:Material.TUFF);
    }

    private void hangingLight(int x,int y,int z,int length) {
        for(int i=0;i<length;i++) block(x,y-i,z,Material.IRON_CHAIN,"axis=y,waterlogged=false");
        block(x,y-length,z,Material.LANTERN,"hanging=true,waterlogged=false");
    }

    private void bench(int x,int y,int z,int length,String facing) {
        boolean alongZ=facing.equals("east")||facing.equals("west");
        for(int i=0;i<length;i++) block(x+(alongZ?0:i),y,z+(alongZ?i:0),Material.SPRUCE_STAIRS,
            "facing="+facing+",half=bottom,shape=straight,waterlogged=false");
    }

    private void stoneBox(int x1,int y1,int z1,int x2,int y2,int z2,int seed) {
        for(int x=x1;x<=x2;x++) for(int y=y1;y<=y2;y++) for(int z=z1;z<=z2;z++) {
            int n=hash(x+y*5,z,seed);
            block(x,y,z,n%7==0?Material.MOSSY_STONE_BRICKS:n%11==0?Material.CRACKED_STONE_BRICKS:Material.STONE_BRICKS);
        }
    }

    private void line(int x1,int y1,int z1,int x2,int y2,int z2,Material material) {
        int steps=Math.max(Math.max(Math.abs(x2-x1),Math.abs(y2-y1)),Math.abs(z2-z1));
        for(int i=0;i<=steps;i++) {
            double t=i/(double)Math.max(1,steps);
            block((int)Math.round(x1+(x2-x1)*t),(int)Math.round(y1+(y2-y1)*t),(int)Math.round(z1+(z2-z1)*t),material);
        }
    }

    private int ground(int x,int z) { return ExpeditionTerrainV2.height(ExpeditionLayout.Zone.GROVE,x,z); }
    private static int hash(int x,int z,int seed) { return Math.floorMod(x*73428767 ^ z*912931 ^ seed*1376312589,104729); }
    private void block(int x,int y,int z,Material material) { canvas.block(ORIGIN_X+x,y,z,material); }
    private void block(int x,int y,int z,Material material,String state) { canvas.block(ORIGIN_X+x,y,z,material,state); }
    private void box(int x1,int y1,int z1,int x2,int y2,int z2,Material material) { canvas.box(ORIGIN_X+x1,y1,z1,ORIGIN_X+x2,y2,z2,material); }
}

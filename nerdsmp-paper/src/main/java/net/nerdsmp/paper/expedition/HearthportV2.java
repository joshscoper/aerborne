package net.nerdsmp.paper.expedition;

import org.bukkit.Material;

/**
 * Hearthport's second, deliberately composed building pass. Coordinates describe architecture,
 * not edits to a live world. Streets retain their original datum so old service coordinates are
 * useful in a newly generated world; no player world or NPC is touched by this plan.
 */
public final class HearthportV2 {
    private static final int FLOOR = 96;
    private HearthportV2() { }

    public static void build(BuildCanvas canvas) {
        Brush city = new Brush(canvas, 0, 0, 0);
        streets(city);
        citadel(new Brush(canvas, -35, -50, 3));
        observatory(new Brush(canvas, 35, -49, 0));

        // Deliberately different footprints, rooflines and functions on each street front.
        townhouse(new Brush(canvas, -41, -17, 3), 10, 9, 13, House.TAVERN);
        townhouse(new Brush(canvas, -43, 24, 3), 11, 9, 11, House.FORGE);
        townhouse(new Brush(canvas, 42, -18, 1), 11, 10, 14, House.BANK);
        townhouse(new Brush(canvas, 43, 24, 1), 9, 9, 12, House.APOTHECARY);
        townhouse(new Brush(canvas, -26, 57, 2), 8, 8, 10, House.HOME);
        townhouse(new Brush(canvas, 26, 57, 2), 9, 8, 11, House.GUILD);
        townhouse(new Brush(canvas, -67, 13, 3), 6, 7, 10, House.HOME);
        townhouse(new Brush(canvas, 67, 11, 1), 6, 8, 12, House.HOME);
        townhouse(new Brush(canvas, -67, -20, 3), 6, 8, 9, House.APOTHECARY);

        market(city);
        stationCourt(city);
        arrivalGate(city);
        gardens(city);
        streetFurniture(city);
    }

    private enum House { TAVERN, FORGE, BANK, APOTHECARY, HOME, GUILD }

    private static void streets(Brush p) {
        // Narrow granite gutters and warm paving distinguish the square from the grey foundations.
        for (int x = -77; x <= 77; x++) for (int z = -77; z <= 77; z++) {
            boolean northRoad = Math.abs(x) <= 5;
            boolean eastRoad = Math.abs(z) <= 5;
            boolean marketSquare = Math.abs(x) <= 23 && z >= -27 && z <= 39;
            boolean sideLane = Math.abs(Math.abs(x) - 27) <= 3 && z >= -53 && z <= 63;
            boolean crossLane = (Math.abs(z + 28) <= 3 || Math.abs(z - 41) <= 3) && Math.abs(x) <= 69;
            if (!northRoad && !eastRoad && !marketSquare && !sideLane && !crossLane) continue;
            Material tile = Math.floorMod(x * 7 + z * 13, 19) == 0 ? Material.ANDESITE : Material.STONE_BRICKS;
            if ((Math.abs(x) == 5 || Math.abs(z) == 5) && !marketSquare) tile = Material.POLISHED_GRANITE;
            if (marketSquare) tile = Math.floorMod(x + z, 7) == 0 ? Material.SMOOTH_SANDSTONE : Material.SANDSTONE;
            if (marketSquare && (Math.abs(x) == 22 || z == -26 || z == 38)) tile = Material.POLISHED_ANDESITE;
            p.at(x, FLOOR, z, tile);
        }
        // The arrival medallion is flush with the paving, never a spawn obstruction.
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
            int taxi = Math.abs(x) + Math.abs(z);
            if (taxi == 9 || taxi == 6) p.at(x, FLOOR, z, Material.POLISHED_DEEPSLATE);
            else if (taxi < 6 && (x == 0 || z == 0)) p.at(x, FLOOR, z, Material.WAXED_OXIDIZED_COPPER);
            else if (taxi < 9) p.at(x, FLOOR, z, Material.SMOOTH_SANDSTONE);
        }
        for (int x : new int[]{-18, 18}) for (int z : new int[]{-21, 34}) {
            p.box(x - 3, FLOOR, z - 2, x + 3, FLOOR, z + 2, Material.POLISHED_ANDESITE);
            p.box(x - 2, FLOOR + 1, z - 1, x + 2, FLOOR + 1, z + 1, Material.MOSS_BLOCK);
            for (int dx = -3; dx <= 3; dx++) for (int dz : new int[]{-2, 2})
                p.at(x + dx, FLOOR + 1, z + dz, Material.STONE_BRICK_SLAB, "type=bottom");
            p.at(x - 1, FLOOR + 2, z, Material.AZALEA);
            p.at(x + 1, FLOOR + 2, z, Material.FLOWERING_AZALEA);
        }
    }

    private static void townhouse(Brush p, int w, int d, int h, House kind) {
        boolean stone = kind == House.BANK;
        Material wall = stone ? Material.SMOOTH_SANDSTONE : kind == House.APOTHECARY ? Material.MUSHROOM_STEM : Material.WHITE_TERRACOTTA;
        Material timber = stone ? Material.CUT_SANDSTONE : kind == House.FORGE ? Material.STRIPPED_DARK_OAK_LOG : Material.STRIPPED_SPRUCE_LOG;
        Material stair = kind == House.BANK || kind == House.APOTHECARY ? Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS
            : kind == House.GUILD ? Material.DARK_PRISMARINE_STAIRS : kind == House.FORGE ? Material.DEEPSLATE_TILE_STAIRS : Material.SPRUCE_STAIRS;
        Material roof = kind == House.BANK || kind == House.APOTHECARY ? Material.WAXED_OXIDIZED_CUT_COPPER
            : kind == House.GUILD ? Material.DARK_PRISMARINE : kind == House.FORGE ? Material.DEEPSLATE_TILES : Material.SPRUCE_PLANKS;
        p.box(-w - 1, FLOOR - 1, -d - 1, w + 1, FLOOR, d + 1, Material.STONE_BRICKS);
        p.box(-w + 1, FLOOR, -d + 1, w - 1, FLOOR, d - 1, Material.SPRUCE_PLANKS);
        // Use small material variation only in the stone base, not random decorative noise.
        for (int x = -w; x <= w; x++) for (int z = -d; z <= d; z++) {
            if (Math.abs(x) != w && Math.abs(z) != d) continue;
            for (int y = 1; y <= h; y++) {
                Material material = y <= 2 ? (Math.floorMod(x + z, 7) == 0 ? Material.ANDESITE : Material.STONE_BRICKS) : wall;
                if (Math.abs(x) == w && Math.abs(z) == d || y == 7 || y == h) material = timber;
                p.at(x, FLOOR + y, z, material);
            }
        }
        // Structural bays break the facade into readable storefronts rather than one huge white panel.
        for (int x : new int[]{-w + 4, w - 4}) {
            p.box(x, FLOOR + 2, d, x, FLOOR + h, d, timber);
            p.box(x, FLOOR + 2, -d, x, FLOOR + h, -d, timber);
        }
        for (int z : new int[]{-d + 4, d - 4}) for (int x : new int[]{-w, w})
            p.box(x, FLOOR + 2, z, x, FLOOR + h, z, timber);
        for (int x : new int[]{-w + 2, w - 2}) {
            window(p, x, FLOOR + 3, d, 2, false);
            window(p, x, FLOOR + 9, d, Math.max(1, h - 10), false);
            window(p, x, FLOOR + 3, -d, 2, true);
        }
        for (int z : new int[]{-d + 2, 0, d - 2}) for (int side : new int[]{-1, 1}) {
            for (int y = 3; y <= 5; y++) p.at(side * w, FLOOR + y, z, Material.LIGHT_BLUE_STAINED_GLASS);
            p.at(side * (w + 1), FLOOR + 2, z, Material.SPRUCE_SLAB, "type=top");
            if (h > 11) for (int y = 9; y <= 11; y++) p.at(side * w, FLOOR + y, z, Material.LIGHT_BLUE_STAINED_GLASS);
        }
        // Door is open architecture: visitors never depend on an inaccessible protected door.
        p.box(-1, FLOOR + 1, d - 1, 1, FLOOR + 4, d + 1, Material.AIR);
        p.at(-2, FLOOR + 4, d + 1, Material.STONE_BRICK_STAIRS, "facing=east,half=top,shape=straight");
        p.at(2, FLOOR + 4, d + 1, Material.STONE_BRICK_STAIRS, "facing=west,half=top,shape=straight");
        p.box(-1, FLOOR + 5, d + 1, 1, FLOOR + 5, d + 1, Material.STONE_BRICK_SLAB);
        for (int x : new int[]{-3, 3}) {
            p.at(x, FLOOR + 4, d + 1, Material.SPRUCE_FENCE);
            p.at(x, FLOOR + 3, d + 1, Material.LANTERN, "hanging=true");
        }
        int upper = FLOOR + 7;
        p.box(-w + 1, upper, -d + 1, w - 1, upper, d - 1, Material.SPRUCE_PLANKS);
        interiorStairs(p, w, d, upper);
        gabledRoof(p, w + 1, d + 1, FLOOR + h, stair, roof, wall, timber);
        // Attic diamond, deep sill and ridge finials make the gable a destination detail.
        int gy = FLOOR + h + 3;
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
            if (Math.abs(dx) + Math.abs(dy) <= 1) p.at(dx, gy + dy, d + 1, Material.LIGHT_BLUE_STAINED_GLASS);
        p.at(0, gy - 2, d + 2, Material.SPRUCE_SLAB, "type=top");
        p.at(0, FLOOR + h + w + 2, d + 1, Material.SPRUCE_FENCE);
        p.at(0, FLOOR + h + w + 3, d + 1, Material.LANTERN);
        if (kind != House.BANK) chimney(p, -w + 3, -d + 3, FLOOR + h + 6);
        if (kind == House.TAVERN || kind == House.GUILD) balcony(p, d, FLOOR + 7);
        furnish(p, w, d, kind);
        if (kind == House.BANK) bankDetails(p, w, d);
        if (kind == House.FORGE) forgeDetails(p, w, d);
        if (kind == House.APOTHECARY) apothecaryDetails(p, w, d);
        shopSign(p, w - 1, d + 2, kind);
    }

    private static void window(Brush p, int x, int y, int z, int height, boolean back) {
        int out = back ? -1 : 1;
        p.box(x - 1, y, z, x + 1, y + height, z, Material.AIR);
        p.box(x - 1, y, z - out, x + 1, y + height, z - out, Material.LIGHT_BLUE_STAINED_GLASS);
        p.box(x - 2, y - 1, z, x + 2, y - 1, z, Material.STONE_BRICKS);
        for (int dx = -2; dx <= 2; dx++) p.at(x + dx, y - 1, z + out, Material.STONE_BRICK_SLAB, "type=top");
        p.box(x - 2, y, z, x - 2, y + height, z, Material.SPRUCE_FENCE);
        p.box(x + 2, y, z, x + 2, y + height, z, Material.SPRUCE_FENCE);
        p.at(x - 1, y + height + 1, z, Material.STONE_BRICK_STAIRS, "facing=east,half=top,shape=straight");
        p.at(x + 1, y + height + 1, z, Material.STONE_BRICK_STAIRS, "facing=west,half=top,shape=straight");
    }

    private static void gabledRoof(Brush p, int w, int d, int y, Material stair, Material roof, Material plaster, Material timber) {
        for (int level = 0; level <= w; level++) {
            int reach = w - level;
            for (int z = -d; z <= d; z++) {
                p.at(-reach, y + level, z, stair, "facing=east,half=bottom,shape=straight");
                p.at(reach, y + level, z, stair, "facing=west,half=bottom,shape=straight");
            }
            if (reach > 0) for (int z : new int[]{-d, d}) {
                p.box(-reach + 1, y + level, z, reach - 1, y + level, z, plaster);
                p.at(-reach, y + level, z, timber);
                p.at(reach, y + level, z, timber);
                p.at(0, y + level, z, timber);
            }
        }
        p.box(0, y + w + 1, -d - 1, 0, y + w + 1, d + 1, roof);
        for (int z = -d; z <= d; z += 3) for (int side : new int[]{-1, 1})
            p.at(side * w, y - 1, z, Material.SPRUCE_STAIRS,
                "facing=" + (side < 0 ? "east" : "west") + ",half=top,shape=straight");
        // A dormer on each roof flank, staggered rather than mirror duplicated.
        if (w >= 8) for (int side : new int[]{-1, 1}) {
            int x = side * (w - 4), z = side < 0 ? -d / 2 : d / 3, base = y + 4;
            p.box(x - 1, base, z - 2, x + 1, base + 3, z + 2, plaster);
            p.box(x + side, base, z - 1, x + side, base + 2, z + 1, Material.LIGHT_BLUE_STAINED_GLASS);
            for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++)
                p.at(x + dx, base + 4 - Math.abs(dz), z + dz, stair,
                    "facing=" + (dz < 0 ? "south" : "north") + ",half=bottom,shape=straight");
        }
    }

    private static void interiorStairs(Brush p, int w, int d, int upper) {
        for (int step = 0; step < 7; step++) {
            int z = -d + 2 + step;
            p.box(-w + 1, FLOOR + 1 + step, z, -w + 3, upper + 2, z, Material.AIR);
            for (int x = -w + 1; x <= -w + 3; x++)
                p.at(x, FLOOR + 1 + step, z, Material.SPRUCE_STAIRS, "facing=south,half=bottom,shape=straight");
            if (step > 1) p.at(-w + 4, FLOOR + 2 + step, z, Material.SPRUCE_FENCE);
        }
        p.at(0, upper + 1, 0, Material.RED_CARPET);
        p.at(0, FLOOR + 6, 0, Material.IRON_CHAIN, "axis=y");
        p.at(0, FLOOR + 5, 0, Material.LANTERN, "hanging=true");
    }

    private static void balcony(Brush p, int d, int y) {
        p.box(-4, y, d, 4, y, d + 3, Material.SPRUCE_SLAB);
        for (int x = -4; x <= 4; x++) p.at(x, y + 1, d + 3, Material.SPRUCE_FENCE);
        for (int z = d; z <= d + 3; z++) for (int x : new int[]{-4, 4}) p.at(x, y + 1, z, Material.SPRUCE_FENCE);
        p.box(-1, y + 1, d, 1, y + 3, d, Material.AIR);
        for (int x : new int[]{-4, 4}) {
            p.at(x, y - 1, d + 1, Material.SPRUCE_STAIRS, "facing=north,half=top,shape=straight");
            p.at(x, y + 1, d + 2, Material.FLOWER_POT);
        }
    }

    private static void chimney(Brush p, int x, int z, int top) {
        p.box(x, FLOOR + 1, z, x + 1, top, z + 1, Material.BRICKS);
        p.box(x - 1, top, z - 1, x + 2, top, z + 2, Material.BRICK_SLAB);
        p.at(x, top + 1, z, Material.COBBLESTONE_WALL);
        p.at(x + 1, top + 1, z + 1, Material.COBBLESTONE_WALL);
    }

    private static void furnish(Brush p, int w, int d, House kind) {
        // Empty furniture only: there is no generated inventory or free currency behind a counter.
        p.box(w - 4, FLOOR + 1, -d + 2, w - 2, FLOOR + 1, d - 4, Material.SPRUCE_PLANKS);
        for (int z = -d + 2; z <= d - 4; z++) p.at(w - 4, FLOOR + 2, z, Material.SPRUCE_TRAPDOOR, "facing=west,half=bottom,open=false");
        for (int z : new int[]{-2, 3}) {
            p.at(0, FLOOR + 1, z, Material.SPRUCE_FENCE);
            p.at(0, FLOOR + 2, z, Material.SPRUCE_PRESSURE_PLATE);
            p.at(-2, FLOOR + 1, z, Material.SPRUCE_STAIRS, "facing=east,half=bottom,shape=straight");
            p.at(2, FLOOR + 1, z, Material.SPRUCE_STAIRS, "facing=west,half=bottom,shape=straight");
        }
        p.box(-2, FLOOR + 1, -d + 1, 2, FLOOR + 3, -d + 1,
            kind == House.GUILD || kind == House.APOTHECARY ? Material.BOOKSHELF : Material.SPRUCE_PLANKS);
        p.at(0, FLOOR + 4, -d + 2, Material.LANTERN);
        for (int x : new int[]{-w + 3, w - 3}) {
            p.at(x, FLOOR + 8, d - 2, Material.SPRUCE_FENCE);
            p.at(x, FLOOR + 9, d - 2, Material.SPRUCE_PRESSURE_PLATE);
            p.at(x, FLOOR + 10, d - 2, Material.POTTED_FERN);
        }
    }

    private static void bankDetails(Brush p, int w, int d) {
        for (int x : new int[]{-w, -4, 4, w}) {
            p.box(x, FLOOR + 1, d + 2, x, FLOOR + 6, d + 2, Material.SANDSTONE);
            p.at(x, FLOOR + 6, d + 2, Material.CHISELED_SANDSTONE);
        }
        p.box(-w, FLOOR + 7, d + 2, w, FLOOR + 7, d + 2, Material.SMOOTH_SANDSTONE_SLAB);
        p.box(-2, FLOOR + 9, d + 1, 2, FLOOR + 11, d + 1, Material.POLISHED_DEEPSLATE);
        p.at(0, FLOOR + 10, d + 2, Material.GOLD_BLOCK);
        p.at(-1, FLOOR + 10, d + 2, Material.WAXED_CUT_COPPER);
        p.at(1, FLOOR + 10, d + 2, Material.WAXED_CUT_COPPER);
    }

    private static void forgeDetails(Brush p, int w, int d) {
        p.box(w - 4, FLOOR + 1, -d + 2, w - 1, FLOOR + 4, -d + 5, Material.POLISHED_BLACKSTONE_BRICKS);
        p.box(w - 3, FLOOR + 2, -d + 1, w - 2, FLOOR + 3, -d + 2, Material.MAGMA_BLOCK);
        p.box(w - 3, FLOOR + 2, -d + 3, w - 2, FLOOR + 3, -d + 3, Material.IRON_BARS);
        p.at(w - 3, FLOOR + 1, 0, Material.ANVIL);
        p.at(w - 2, FLOOR + 1, 3, Material.GRINDSTONE, "face=floor,facing=north");
        for (int x = -5; x <= 5; x++) for (int z = d + 1; z <= d + 4; z++)
            p.at(x, FLOOR + 5 - (z - d) / 3, z, Material.WAXED_CUT_COPPER_SLAB, "type=bottom");
        for (int x : new int[]{-5, 5}) p.box(x, FLOOR + 1, d + 4, x, FLOOR + 4, d + 4, Material.DARK_OAK_FENCE);
    }

    private static void apothecaryDetails(Brush p, int w, int d) {
        for (int x : new int[]{-w + 3, w - 3}) {
            p.box(x - 1, FLOOR + 1, d + 2, x + 1, FLOOR + 1, d + 2, Material.MOSS_BLOCK);
            p.at(x - 1, FLOOR + 2, d + 2, Material.POTTED_AZALEA_BUSH);
            p.at(x + 1, FLOOR + 2, d + 2, Material.POTTED_RED_MUSHROOM);
        }
        p.box(w - 2, FLOOR + 3, -d + 2, w - 2, FLOOR + 3, 0, Material.SPRUCE_SLAB);
        p.at(w - 2, FLOOR + 4, -d + 3, Material.POTTED_ALLIUM);
        p.at(w - 2, FLOOR + 4, -d + 5, Material.POTTED_BLUE_ORCHID);
        p.at(w - 2, FLOOR + 4, -d + 7, Material.POTTED_FERN);
    }

    private static void shopSign(Brush p, int x, int z, House kind) {
        p.box(x, FLOOR + 7, z - 1, x, FLOOR + 7, z + 1, Material.SPRUCE_FENCE);
        p.at(x, FLOOR + 6, z + 1, Material.IRON_CHAIN, "axis=y");
        p.at(x, FLOOR + 5, z + 1, switch (kind) {
            case BANK -> Material.WAXED_CUT_COPPER;
            case FORGE -> Material.ANVIL;
            case APOTHECARY -> Material.AMETHYST_BLOCK;
            case GUILD -> Material.CHISELED_BOOKSHELF;
            default -> Material.CARVED_PUMPKIN;
        });
    }

    private static void citadel(Brush p) {
        // A low, inhabited chapterhouse connects two unequal towers. The taller tower is set back.
        townhouse(p, 13, 13, 17, House.GUILD);
        tower(p, -17, -4, 5, 31, false);
        tower(p, 16, -10, 6, 42, true);
        p.box(-10, FLOOR + 1, 15, 10, FLOOR + 2, 20, Material.STONE_BRICKS);
        for (int x = -10; x <= 10; x++) {
            p.at(x, FLOOR + 1, 22, Material.STONE_BRICK_STAIRS, "facing=north,half=bottom,shape=straight");
            p.at(x, FLOOR + 2, 21, Material.STONE_BRICK_STAIRS, "facing=north,half=bottom,shape=straight");
        }
        // Raised terrace is connected back down to the ground-floor entrance, not a sealed doorstep.
        for (int x = -2; x <= 2; x++) {
            p.at(x, FLOOR + 2, 17, Material.STONE_BRICK_STAIRS, "facing=south,half=bottom,shape=straight");
            p.at(x, FLOOR + 1, 16, Material.STONE_BRICK_STAIRS, "facing=south,half=bottom,shape=straight");
            p.at(x, FLOOR + 2, 16, Material.AIR);
            p.at(x, FLOOR + 1, 15, Material.AIR);
            p.at(x, FLOOR + 2, 15, Material.AIR);
        }
        for (int x : new int[]{-10, 10}) {
            for (int z = 15; z <= 20; z++) p.at(x, FLOOR + 3, z, Material.STONE_BRICK_WALL);
            p.at(x, FLOOR + 4, 20, Material.SOUL_LANTERN);
        }
        // Nave windows rise into the gable, while buttresses give the hall an external rhythm.
        for (int z : new int[]{-10, -3, 4, 11}) for (int side : new int[]{-1, 1}) {
            p.box(side * 14, FLOOR + 1, z, side * 14, FLOOR + 11, z, Material.STONE_BRICKS);
            p.at(side * 14, FLOOR + 12, z, Material.STONE_BRICK_STAIRS,
                "facing=" + (side < 0 ? "east" : "west") + ",half=bottom,shape=straight");
        }
        for (int x : new int[]{-7, 7}) {
            p.box(x - 1, FLOOR + 10, 13, x + 1, FLOOR + 15, 13, Material.PURPLE_STAINED_GLASS);
            p.at(x, FLOOR + 16, 13, Material.AMETHYST_BLOCK);
            p.box(x, FLOOR + 9, 14, x, FLOOR + 14, 14, Material.CUT_SANDSTONE);
        }
        // A covered elevated connection is an architectural silhouette, with accessible interior stairs.
        p.box(9, FLOOR + 18, -11, 16, FLOOR + 18, -8, Material.STONE_BRICKS);
        for (int x = 9; x <= 16; x++) for (int z : new int[]{-11, -8}) p.at(x, FLOOR + 19, z, Material.STONE_BRICK_WALL);
    }

    private static void tower(Brush p, int cx, int cz, int radius, int height, boolean arcane) {
        // Chamfered square plan, deep vertical piers and restrained cornices, not striped cylinders.
        for (int y = 0; y <= height; y++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.abs(dx) == radius && Math.abs(dz) == radius) continue;
            boolean edge = Math.abs(dx) == radius || Math.abs(dz) == radius
                || Math.abs(dx) == radius - 1 && Math.abs(dz) == radius - 1;
            if (edge) {
                boolean pier = Math.abs(dx) >= radius - 1 && Math.abs(dz) >= radius - 2
                    || Math.abs(dz) >= radius - 1 && Math.abs(dx) >= radius - 2;
                p.at(cx + dx, FLOOR + y, cz + dz,
                    y < 4 || pier ? Material.STONE_BRICKS : Material.SMOOTH_SANDSTONE);
            } else if (y == 0 || y == 9 || y == 19 || y == height - 2) p.at(cx + dx, FLOOR + y, cz + dz, Material.SPRUCE_PLANKS);
        }
        for (int level : new int[]{5, 13, 23, height - 7}) for (int side = 0; side < 4; side++) {
            for (int yy = 0; yy < 4; yy++) for (int width = -1; width <= 1; width++) {
                int x = side < 2 ? cx + width : cx + (side == 2 ? -radius : radius);
                int z = side < 2 ? cz + (side == 0 ? -radius : radius) : cz + width;
                p.at(x, FLOOR + level + yy, z, arcane ? Material.PURPLE_STAINED_GLASS : Material.CYAN_STAINED_GLASS);
            }
        }
        for (int level : new int[]{height - 2, height}) {
            for (int n = -radius; n <= radius; n++) for (int side : new int[]{-1, 1}) {
                p.at(cx + n, FLOOR + level, cz + side * (radius + 1), Material.STONE_BRICK_SLAB, "type=top");
                p.at(cx + side * (radius + 1), FLOOR + level, cz + n, Material.STONE_BRICK_SLAB, "type=top");
            }
        }
        Material roof = arcane ? Material.PURPUR_BLOCK : Material.WAXED_OXIDIZED_CUT_COPPER;
        Material stairs = arcane ? Material.PURPUR_STAIRS : Material.WAXED_OXIDIZED_CUT_COPPER_STAIRS;
        for (int y = 0; y <= radius * 2 + 3; y++) {
            int reach = Math.max(0, radius + 1 - (y + 1) / 2);
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                if (Math.abs(dx) == reach && Math.abs(dz) == reach && reach > 1) continue;
                if (Math.abs(dx) != reach && Math.abs(dz) != reach && y > 0) continue;
                if ((y & 1) == 0 && reach > 0 && Math.abs(dx) != Math.abs(dz))
                    p.at(cx + dx, FLOOR + height + 1 + y, cz + dz, stairs,
                        "facing=" + (Math.abs(dx) > Math.abs(dz) ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north"))
                            + ",half=bottom,shape=straight");
                else p.at(cx + dx, FLOOR + height + 1 + y, cz + dz, roof);
            }
        }
        int tip = FLOOR + height + radius * 2 + 5;
        p.at(cx, tip, cz, Material.LIGHTNING_ROD, "facing=up");
        p.at(cx, tip - 1, cz, Material.GOLD_BLOCK);
        p.box(cx - 1, FLOOR + 1, cz + radius - 1, cx + 1, FLOOR + 4, cz + radius, Material.AIR);
        // Continuous spiral staircase around the inner perimeter, with clearance cut from each landing.
        int r = radius - 2, step = 0;
        while (step < height - 2) for (int side = 0; side < 4 && step < height - 2; side++)
            for (int n = -r; n < r && step < height - 2; n++, step++) {
                int x = side == 0 ? n : side == 1 ? r : side == 2 ? -n : -r;
                int z = side == 0 ? -r : side == 1 ? n : side == 2 ? r : -n;
                int y = FLOOR + 1 + step;
                p.box(cx + x, y, cz + z, cx + x, y + 2, cz + z, Material.AIR);
                p.at(cx + x, y, cz + z, Material.SPRUCE_STAIRS,
                    "facing=" + new String[]{"east", "south", "west", "north"}[side] + ",half=bottom,shape=straight");
            }
    }

    private static void observatory(Brush p) {
        townhouse(p, 12, 11, 13, House.BANK);
        tower(p, 14, -9, 5, 30, true);
        // Copper armillary atop a square roof lantern, rather than another identical conical keep.
        int cy = FLOOR + 31;
        p.box(-3, FLOOR + 20, -2, 3, FLOOR + 25, 4, Material.CUT_SANDSTONE);
        p.box(-2, FLOOR + 22, -3, 2, FLOOR + 24, -3, Material.LIGHT_BLUE_STAINED_GLASS);
        for (int dx = -7; dx <= 7; dx++) for (int dy = -7; dy <= 7; dy++) {
            double distance = Math.hypot(dx, dy);
            if (distance >= 6.2 && distance <= 7.0) {
                p.at(dx, cy + dy, 1, Material.WAXED_CUT_COPPER);
                if (Math.abs(dx) <= 5) p.at(dx, cy + dy, dx / 2 + 1, Material.WAXED_OXIDIZED_CUT_COPPER);
            }
        }
        p.box(0, cy - 1, 0, 0, cy + 1, 2, Material.AMETHYST_BLOCK);
        p.box(-1, cy, 0, 1, cy, 2, Material.AMETHYST_BLOCK);
        p.at(0, cy, 1, Material.SEA_LANTERN);
        // A real reading room with low desks and an upper gallery.
        for (int x : new int[]{-8, -4, 4, 8}) {
            p.box(x, FLOOR + 1, -8, x, FLOOR + 4, -7, Material.BOOKSHELF);
            p.at(x, FLOOR + 1, -4, Material.LECTERN, "facing=south");
        }
    }

    private static void market(Brush p) {
        stall(p, -14, -9, Material.PURPLE_WOOL, 0);
        stall(p, 14, -9, Material.CYAN_WOOL, 1);
        stall(p, -15, 13, Material.ORANGE_WOOL, 2);
        stall(p, 15, 13, Material.GREEN_WOOL, 3);
        // Off-axis reflecting basin leaves the board and spawn completely clear.
        int cx = 12, cz = -29;
        p.box(cx - 5, FLOOR, cz - 4, cx + 5, FLOOR, cz + 4, Material.STONE_BRICKS);
        for (int x = -5; x <= 5; x++) for (int z = -4; z <= 4; z++) {
            if (Math.abs(x) == 5 || Math.abs(z) == 4) p.at(cx + x, FLOOR + 1, cz + z, Material.STONE_BRICK_SLAB);
            else p.at(cx + x, FLOOR, cz + z, Material.WATER);
        }
        p.box(cx - 1, FLOOR, cz - 1, cx + 1, FLOOR + 2, cz + 1, Material.POLISHED_DEEPSLATE);
        p.at(cx, FLOOR + 3, cz, Material.SEA_LANTERN);
        p.at(cx, FLOOR + 4, cz, Material.AMETHYST_BLOCK);
        p.at(cx, FLOOR + 5, cz, Material.AMETHYST_BLOCK);
        p.at(cx, FLOOR + 6, cz, Material.LARGE_AMETHYST_BUD, "facing=up");
    }

    private static void stall(Brush p, int cx, int cz, Material cloth, int goods) {
        for (int dx : new int[]{-4, 4}) for (int dz : new int[]{-2, 2}) {
            p.box(cx + dx, FLOOR + 1, cz + dz, cx + dx, FLOOR + 4, cz + dz, Material.SPRUCE_FENCE);
            p.at(cx + dx, FLOOR + 5, cz + dz, Material.SPRUCE_SLAB);
        }
        for (int x = -4; x <= 4; x++) for (int z = -3; z <= 3; z++)
            p.at(cx + x, FLOOR + 5 + (Math.abs(z) <= 1 ? 1 : 0), cz + z, (x & 1) == 0 ? cloth : Material.WHITE_WOOL);
        p.box(cx - 3, FLOOR + 1, cz + 1, cx + 3, FLOOR + 1, cz + 1, Material.SPRUCE_PLANKS);
        p.box(cx - 3, FLOOR + 2, cz + 1, cx + 3, FLOOR + 2, cz + 1, Material.SPRUCE_SLAB);
        for (int x : new int[]{-2, 0, 2}) p.at(cx + x, FLOOR + 3, cz + 1,
            goods == 0 ? Material.POTTED_ALLIUM : goods == 1 ? Material.POTTED_BLUE_ORCHID : goods == 2 ? Material.MELON : Material.POTTED_FERN);
        p.at(cx, FLOOR + 4, cz - 1, Material.LANTERN, "hanging=true");
        p.at(cx - 3, FLOOR + 1, cz - 1, Material.HAY_BLOCK, "axis=x");
        p.at(cx - 2, FLOOR + 1, cz - 1, Material.COMPOSTER, "level=0");
    }

    private static void stationCourt(Brush p) {
        p.box(-12, FLOOR, 22, 12, FLOOR, 31, Material.POLISHED_ANDESITE);
        for (int x = -11; x <= 11; x++) for (int z = 23; z <= 30; z++)
            p.at(x, FLOOR, z, Math.floorMod(x + z, 4) == 0 ? Material.WAXED_OXIDIZED_CUT_COPPER : Material.SMOOTH_SANDSTONE);
        for (int x : new int[]{-12, 12}) for (int z : new int[]{22, 31}) {
            p.box(x, FLOOR + 1, z, x, FLOOR + 6, z, Material.STONE_BRICKS);
            p.at(x, FLOOR + 7, z, Material.CHISELED_STONE_BRICKS);
            p.at(x, FLOOR + 8, z, Material.LANTERN);
        }
        // Split pergola canopy keeps the main avenue and station approach bright and open.
        for (int x = -12; x <= 12; x++) if (Math.abs(x) >= 4) for (int z = 22; z <= 31; z++) {
            if ((x & 1) == 0 || z == 22 || z == 31) p.at(x, FLOOR + 7, z, Material.DARK_OAK_SLAB, "type=bottom");
            if (Math.floorMod(x * 13 + z * 7, 11) == 0) p.at(x, FLOOR + 8, z, Material.AZALEA_LEAVES, "persistent=true");
        }
        for (int x : new int[]{-10, -6, 6, 10}) p.at(x, FLOOR + 6, 30, Material.LANTERN, "hanging=true");
        // Stations themselves are placed by the shared final catalog layer at their stable coordinates.
    }

    private static void arrivalGate(Brush p) {
        for (int side : new int[]{-1, 1}) {
            int x = side * 11;
            p.box(x - 3, FLOOR, 70, x + 3, FLOOR + 8, 77, Material.STONE_BRICKS);
            p.box(x - 2, FLOOR + 1, 70, x + 2, FLOOR + 6, 76, Material.AIR);
            p.box(x - 1, FLOOR + 1, 77, x + 1, FLOOR + 3, 77, Material.AIR);
            p.box(x - 2, FLOOR + 5, 77, x + 2, FLOOR + 6, 77, Material.CYAN_STAINED_GLASS);
            for (int h = 0; h < 5; h++) for (int z = 69; z <= 78; z++) {
                p.at(x - 4 + h, FLOOR + 9 + h, z, Material.DARK_PRISMARINE_STAIRS, "facing=east,half=bottom,shape=straight");
                p.at(x + 4 - h, FLOOR + 9 + h, z, Material.DARK_PRISMARINE_STAIRS, "facing=west,half=bottom,shape=straight");
            }
            for (int z : new int[]{71, 76}) p.at(side * 7, FLOOR + 4, z, Material.LANTERN);
        }
        // High pointed arch frames the skyline from the south without blocking the road.
        for (int x = -8; x <= 8; x++) {
            int y = FLOOR + 13 - Math.abs(x) / 2;
            p.box(x, y, 72, x, y + 1, 74, Material.STONE_BRICKS);
            p.at(x, y - 1, 72, Material.STONE_BRICK_STAIRS,
                "facing=" + (x < 0 ? "east" : "west") + ",half=top,shape=straight");
        }
        p.at(0, FLOOR + 15, 73, Material.CHISELED_STONE_BRICKS);
        p.at(0, FLOOR + 16, 73, Material.WAXED_OXIDIZED_CUT_COPPER);
        p.at(0, FLOOR + 17, 73, Material.LIGHTNING_ROD, "facing=up");
    }

    private static void gardens(Brush p) {
        garden(p, 61, 45);
        garden(p, -61, 44);
        tree(p, 62, -39, 8);
        tree(p, -59, 47, 7);
        tree(p, 54, 59, 6);
        tree(p, -56, 62, 6);
        tree(p, 14, -70, 6);
        for (int side : new int[]{-1, 1}) for (int z = -57; z <= 61; z += 12) {
            if (z >= -6 && z <= 7) continue;
            int x = side * 77;
            p.at(x, FLOOR + 1, z, Material.MOSS_BLOCK);
            p.at(x, FLOOR + 2, z, Material.FLOWERING_AZALEA);
        }
    }

    private static void garden(Brush p, int cx, int cz) {
        for (int x = -9; x <= 9; x++) for (int z = -8; z <= 8; z++) {
            if (Math.abs(x) + Math.abs(z) > 14) continue;
            p.at(cx + x, FLOOR, cz + z, Math.abs(x) <= 1 || z == 0 ? Material.GRAVEL : Material.GRASS_BLOCK);
            if (Math.abs(x) > 3 && Math.abs(z) > 2 && Math.floorMod(x * 11 + z * 7, 13) == 0)
                p.at(cx + x, FLOOR + 1, cz + z, (x + z) % 2 == 0 ? Material.AZURE_BLUET : Material.ALLIUM);
        }
        for (int x : new int[]{-7, 7}) for (int z = -4; z <= 4; z++) {
            p.at(cx + x, FLOOR + 1, cz + z, Material.STONE_BRICK_SLAB);
            if ((z & 1) == 0) p.at(cx + x, FLOOR + 2, cz + z, Material.AZALEA_LEAVES, "persistent=true");
        }
        bench(p, cx - 4, cz - 5);
        bench(p, cx + 4, cz + 5);
    }

    private static void tree(Brush p, int x, int z, int h) {
        p.box(x, FLOOR + 1, z, x, FLOOR + h, z, Material.OAK_LOG);
        p.box(x + 1, FLOOR + h - 3, z, x + 2, FLOOR + h - 3, z, Material.OAK_LOG, "axis=x");
        p.box(x - 2, FLOOR + h - 2, z - 1, x - 1, FLOOR + h - 2, z - 1, Material.OAK_LOG, "axis=x");
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) for (int dy = -2; dy <= 2; dy++)
            if (dx * dx + dz * dz + dy * dy * 3 <= 20 && Math.floorMod(dx * 11 + dz * 7 + dy, 17) != 0)
                p.at(x + dx, FLOOR + h + dy, z + dz, Math.floorMod(dx + dz, 7) == 0 ? Material.FLOWERING_AZALEA_LEAVES : Material.OAK_LEAVES, "persistent=true");
    }

    private static void streetFurniture(Brush p) {
        for (int z : new int[]{-77, -35, 45, 64}) for (int x : new int[]{-6, 6}) lamp(p, x, z);
        for (int x : new int[]{-78, -58, -27, 27, 58, 78}) for (int z : new int[]{-6, 6}) lamp(p, x, z);
        for (int x : new int[]{-22, 22}) for (int z : new int[]{-15, 7, 36}) lamp(p, x, z);
        bench(p, -10, -19);
        bench(p, 9, 39);
        // Supply handcart by the west workshop, offset from both the avenue and storefront.
        p.box(-30, FLOOR + 1, 32, -27, FLOOR + 1, 35, Material.SPRUCE_SLAB, "type=top");
        for (int x : new int[]{-31, -26}) for (int z : new int[]{32, 35}) p.at(x, FLOOR + 1, z, Material.POLISHED_BLACKSTONE_BUTTON,
            "face=wall,facing=" + (x < -28 ? "west" : "east"));
        p.box(-30, FLOOR + 2, 32, -27, FLOOR + 2, 34, Material.HAY_BLOCK, "axis=x");
        p.box(-30, FLOOR + 1, 36, -30, FLOOR + 1, 38, Material.SPRUCE_FENCE);
        p.box(-27, FLOOR + 1, 36, -27, FLOOR + 1, 38, Material.SPRUCE_FENCE);
    }

    private static void lamp(Brush p, int x, int z) {
        p.at(x, FLOOR + 1, z, Material.CHISELED_STONE_BRICKS);
        p.box(x, FLOOR + 2, z, x, FLOOR + 4, z, Material.COBBLESTONE_WALL);
        p.at(x, FLOOR + 5, z, Material.SPRUCE_FENCE);
        p.at(x + (x < 0 ? 1 : -1), FLOOR + 5, z, Material.SPRUCE_FENCE);
        p.at(x + (x < 0 ? 1 : -1), FLOOR + 4, z, Material.LANTERN, "hanging=true");
    }

    private static void bench(Brush p, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) p.at(x + dx, FLOOR + 1, z, Material.SPRUCE_STAIRS, "facing=north,half=bottom,shape=straight");
        for (int dx : new int[]{-3, 3}) p.at(x + dx, FLOOR + 1, z, Material.SPRUCE_TRAPDOOR, "facing=north,open=true,half=bottom");
    }

    /** Local coordinates keep street fronts deliberate; facing and log axes rotate with the building. */
    private record Brush(BuildCanvas canvas, int originX, int originZ, int turns) {
        void at(int x, int y, int z, Material material) { canvas.block(gx(x, z), y, gz(x, z), material); }
        void at(int x, int y, int z, Material material, String properties) {
            String rotated = properties;
            String[] directions = {"north", "east", "south", "west"};
            for (int i = 0; i < directions.length; i++) if (properties.contains("facing=" + directions[i])) {
                rotated = rotated.replace("facing=" + directions[i], "facing=" + directions[(i + turns) & 3]);
                break;
            }
            if ((turns & 1) != 0) {
                if (properties.contains("axis=x")) rotated = rotated.replace("axis=x", "axis=z");
                else if (properties.contains("axis=z")) rotated = rotated.replace("axis=z", "axis=x");
            }
            canvas.block(gx(x, z), y, gz(x, z), material, rotated);
        }
        void box(int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    for (int y = y1; y <= y2; y++) at(x, y, z, material);
        }
        void box(int x1, int y1, int z1, int x2, int y2, int z2, Material material, String properties) {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    for (int y = y1; y <= y2; y++) at(x, y, z, material, properties);
        }
        private int gx(int x, int z) { return originX + switch (turns) { case 1 -> -z; case 2 -> -x; case 3 -> z; default -> x; }; }
        private int gz(int x, int z) { return originZ + switch (turns) { case 1 -> x; case 2 -> -z; case 3 -> -x; default -> z; }; }
    }
}

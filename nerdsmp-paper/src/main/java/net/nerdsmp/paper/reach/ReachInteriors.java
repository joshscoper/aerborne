package net.nerdsmp.paper.reach;

import org.bukkit.Material;

/**
 * Furnishes a house built by {@link ReachKit#house}: a hearth at the chimney, a ladder between storeys, and rooms laid
 * out for what the building is for. Homes get a table, chairs and beds; shops a counter with the keeper's place
 * behind it, shelves and goods by trade; inns a bar and tables; warehouses stacked cargo; workshops their tools;
 * barracks rows of bunks; stables stalls and hay; offices desks, lecterns and maps. Everything goes only into empty
 * air, so walls, doors and windows are never cut. All positions are in the house's own frame (front at z = 0).
 */
final class ReachInteriors {
    private ReachInteriors() { }

    enum Use { HOME, SHOP, INN, WAREHOUSE, WORKSHOP, BARRACKS, STABLES, OFFICE }

    /** What a shop or workshop deals in, which picks its goods, shelves and tools. */
    enum Trade {
        GENERAL(new Material[] {Material.BARREL, Material.CHEST, Material.HAY_BLOCK}),
        GROCER(new Material[] {Material.MELON, Material.PUMPKIN, Material.HAY_BLOCK, Material.BEEHIVE}),
        BAKER(new Material[] {Material.HAY_BLOCK, Material.SMOKER, Material.BARREL}),
        TIMBER(new Material[] {Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG, Material.STRIPPED_BIRCH_LOG}),
        STONE(new Material[] {Material.POLISHED_ANDESITE, Material.POLISHED_GRANITE, Material.POLISHED_DIORITE, Material.CHISELED_STONE_BRICKS}),
        BUILDER(new Material[] {Material.BRICKS, Material.MUD_BRICKS, Material.SMOOTH_STONE, Material.TERRACOTTA}),
        MECHANIST(new Material[] {Material.REDSTONE_LAMP, Material.PISTON, Material.OBSERVER, Material.COPPER_BLOCK}),
        TAILOR(new Material[] {Material.RED_WOOL, Material.BLUE_WOOL, Material.YELLOW_WOOL, Material.LOOM}),
        ARCANE(new Material[] {Material.AMETHYST_BLOCK, Material.ENCHANTING_TABLE, Material.CHISELED_BOOKSHELF}),
        RELICS(new Material[] {Material.DECORATED_POT, Material.CHISELED_TUFF, Material.LODESTONE}),
        SMITH(new Material[] {Material.ANVIL, Material.BLAST_FURNACE, Material.SMITHING_TABLE, Material.GRINDSTONE}),
        ALCHEMY(new Material[] {Material.BREWING_STAND, Material.CAULDRON, Material.BOOKSHELF}),
        ENCHANTER(new Material[] {Material.ENCHANTING_TABLE, Material.BOOKSHELF, Material.AMETHYST_BLOCK}),
        QUARTERMASTER(new Material[] {Material.ANVIL, Material.BARREL, Material.TARGET}),
        MAPS(new Material[] {Material.CARTOGRAPHY_TABLE, Material.LECTERN, Material.BOOKSHELF}),
        BANK(new Material[] {Material.GOLD_BLOCK, Material.CHEST, Material.IRON_BLOCK});

        final Material[] goods;
        Trade(Material[] goods) { this.goods = goods; }
    }

    /** The shape of the house being furnished. */
    record Layout(int w, int d, int floors, int front, int chimX) {
        int storey() { return 4; }
        int ladderX() { return chimX == 1 ? w - 2 : 1; }
    }

    /** Puts a block only into air, so nothing structural is ever overwritten. */
    static void put(ReachBrush b, int x, int y, int z, Material m, String props) {
        ReachCanvas.Spec s = b.canvas.get(b.wx(x, z), b.oy + y, b.wz(x, z));
        if (s != null && !s.state().equals("minecraft:air")) return;
        b.set(x, y, z, m, props);
    }

    static void put(ReachBrush b, int x, int y, int z, Material m) { put(b, x, y, z, m, ""); }

    /**
     * Furnishes the house and returns where its keeper stands (local x, y, z), or null when it has none. A shop's
     * keeper stands behind the counter facing the door.
     */
    static int[] furnish(ReachBrush b, Layout lay, Use use, Trade trade, long seed) {
        int w = lay.w(), d = lay.d();
        // A ladder up the back corner through every floor.
        int lx = lay.ladderX();
        int topFloor = 4 + (lay.floors() - 1) * lay.storey();
        if (lay.floors() >= 1) for (int y = 0; y <= topFloor; y++) {
            if (y % 4 == 0 && y > 0) b.set(lx, y, d - 2, Material.AIR);
            put(b, lx, y, d - 2, Material.LADDER, "facing=north");
        }
        // The hearth at the foot of the chimney.
        b.set(lay.chimX(), 0, d - 2, use == Use.STABLES ? Material.COMPOSTER : Material.FURNACE, use == Use.STABLES ? "level=4" : "facing=north,lit=true");
        put(b, w / 2, 3, d / 2, Material.LANTERN, "hanging=true");
        int[] keeper = null;
        switch (use) {
            case SHOP -> keeper = shop(b, lay, trade, seed);
            case INN -> keeper = inn(b, lay, seed);
            case WAREHOUSE -> warehouse(b, lay, seed);
            case WORKSHOP -> keeper = workshop(b, lay, trade, seed);
            case BARRACKS -> barracks(b, lay, 0);
            case STABLES -> stables(b, lay);
            case OFFICE -> keeper = office(b, lay, trade, seed);
            default -> home(b, lay, seed);
        }
        // Upper storeys: bedrooms in homes and shops, bunks in barracks, stores in warehouses and stables.
        for (int f = 0; f < lay.floors(); f++) {
            int fy = 4 + f * lay.storey() + 1;
            switch (use) {
                case BARRACKS -> barracks(b, lay, fy);
                case WAREHOUSE, STABLES -> loft(b, lay, fy, seed + f);
                case INN -> bedrooms(b, lay, fy, seed + f, true);
                default -> bedrooms(b, lay, fy, seed + f, false);
            }
        }
        return keeper;
    }

    private static void home(ReachBrush b, Layout lay, long seed) {
        int w = lay.w(), d = lay.d();
        int tx = w / 2, tz = Math.max(2, d / 2);
        put(b, tx, 0, tz, Material.SPRUCE_FENCE);
        put(b, tx, 1, tz, Material.SPRUCE_PRESSURE_PLATE);
        put(b, tx - 1, 0, tz, Material.SPRUCE_STAIRS, "facing=west,half=bottom");
        put(b, tx + 1, 0, tz, Material.SPRUCE_STAIRS, "facing=east,half=bottom");
        put(b, tx, 0, tz + 1 < d - 1 ? tz + 1 : tz, Material.WHITE_CARPET);
        put(b, 1, 0, 1, Material.CRAFTING_TABLE);
        put(b, w - 2, 0, 1, Material.BARREL, "facing=up");
        put(b, w - 2, 1, 1, Material.POTTED_RED_TULIP);
        put(b, 1, 0, d - 3, Material.CHEST, "facing=east,type=single");
        if ((seed & 1) == 0) put(b, w - 2, 0, d - 3, Material.BOOKSHELF);
        else put(b, w - 2, 0, d - 3, Material.BARREL, "facing=west");
    }

    private static int[] shop(ReachBrush b, Layout lay, Trade trade, long seed) {
        int w = lay.w(), d = lay.d(), door = w / 2;
        int cz = Math.min(3, d - 4);
        // The counter across the room with a gap at one end, goods on top.
        for (int x = 2; x <= w - 2; x++) {
            if (x == lay.ladderX() && cz == d - 2) continue;
            put(b, x, 0, cz, (x & 1) == 0 ? Material.SPRUCE_PLANKS : Material.BARREL, (x & 1) == 0 ? "" : "facing=north");
            if (x != door) put(b, x, 1, cz, (x % 3 == 0) ? Material.POTTED_FERN : Material.SPRUCE_TRAPDOOR, (x % 3 == 0) ? "" : "facing=north,half=bottom,open=false");
        }
        // Shelves and stock along the back and side walls behind the counter.
        Material[] goods = trade.goods;
        for (int x = 1; x <= w - 2; x++) for (int y = 0; y <= 2; y++) {
            if (x == lay.ladderX() || x == lay.chimX()) continue;
            Material m = y == 1 ? goods[(int) Math.floorMod(seed + x, (long) goods.length)] : Material.BOOKSHELF;
            put(b, x, y, d - 2, m, props(m, "north"));
        }
        for (int z = cz + 1; z <= d - 3; z++) {
            put(b, 1, 0, z, Material.BARREL, "facing=east");
            put(b, w - 2, 0, z, goods[(int) Math.floorMod(seed + z, (long) goods.length)], props(goods[(int) Math.floorMod(seed + z, (long) goods.length)], "west"));
        }
        // Wares out front either side of the door, a runner on the floor.
        put(b, 1, 0, 1, goods[0], props(goods[0], "south"));
        put(b, w - 2, 0, 1, goods[goods.length - 1], props(goods[goods.length - 1], "south"));
        for (int z = 0; z < cz; z++) put(b, door, 0, z, Material.RED_CARPET);
        return new int[] {door, 0, cz + 1};
    }

    private static int[] inn(ReachBrush b, Layout lay, long seed) {
        int w = lay.w(), d = lay.d();
        // A bar along one side with kegs behind it, tables and stools in the room.
        int bx = w - 3;
        for (int z = 2; z <= d - 3; z++) {
            put(b, bx, 0, z, Material.SPRUCE_PLANKS);
            put(b, bx, 1, z, Material.SPRUCE_SLAB, "type=bottom");
            if (z == d / 2) continue;
            put(b, w - 2, 0, z, Material.BARREL, "facing=west");
            put(b, w - 2, 1, z, Material.BARREL, "facing=west");
        }
        for (int z = 2; z <= d - 4; z += 3) for (int x = 2; x < bx - 1; x += 3) {
            put(b, x, 0, z, Material.DARK_OAK_FENCE);
            put(b, x, 1, z, Material.DARK_OAK_PRESSURE_PLATE);
            put(b, x - 1, 0, z, Material.SPRUCE_STAIRS, "facing=west,half=bottom");
            put(b, x + 1, 0, z, Material.SPRUCE_STAIRS, "facing=east,half=bottom");
            put(b, x, 1, z + 1, Material.AIR);
        }
        put(b, 1, 0, d - 3, Material.CAULDRON);
        return new int[] {bx + 1, 0, d / 2};
    }

    private static void warehouse(ReachBrush b, Layout lay, long seed) {
        int w = lay.w(), d = lay.d();
        for (int x = 1; x <= w - 2; x++) for (int z = 2; z <= d - 2; z++) {
            boolean aisle = x == w / 2 || z == d / 2;
            if (aisle) continue;
            long h = ReachFlora.hash(x * 31 + (int) seed, z * 17);
            int stack = 1 + (int) Math.floorMod(h, 3L);
            for (int y = 0; y < stack; y++) {
                Material m = switch ((int) Math.floorMod(h >> (y + 2), 4L)) { case 0 -> Material.HAY_BLOCK; case 1 -> Material.SPRUCE_PLANKS; default -> Material.BARREL; };
                put(b, x, y, z, m, m == Material.BARREL ? "facing=up" : m == Material.HAY_BLOCK ? "axis=y" : "");
            }
        }
    }

    private static int[] workshop(ReachBrush b, Layout lay, Trade trade, long seed) {
        int w = lay.w(), d = lay.d();
        Material[] tools = trade.goods;
        for (int x = 1; x <= w - 2; x++) {
            if (x == lay.ladderX() || x == lay.chimX()) continue;
            Material m = tools[(int) Math.floorMod(seed + x, (long) tools.length)];
            put(b, x, 0, d - 2, m, props(m, "north"));
        }
        for (int z = 2; z <= d - 3; z += 2) {
            Material m = tools[(int) Math.floorMod(seed + z * 3, (long) tools.length)];
            put(b, 1, 0, z, m, props(m, "east"));
        }
        put(b, w - 2, 0, 2, Material.CRAFTING_TABLE);
        put(b, w - 2, 0, 3, Material.BARREL, "facing=west");
        return new int[] {w / 2, 0, Math.min(d - 3, 3)};
    }

    private static void barracks(ReachBrush b, Layout lay, int y) {
        int w = lay.w(), d = lay.d();
        // Bunks head to the side walls, a chest at each foot, an aisle down the middle.
        for (int z = 2; z <= d - 3; z += 2) {
            put(b, 1, y, z, Material.GREEN_BED, "facing=west,part=head");
            put(b, 2, y, z, Material.GREEN_BED, "facing=west,part=foot");
            put(b, 3, y, z, Material.CHEST, "facing=east,type=single");
            if (w >= 9) {
                put(b, w - 2, y, z, Material.GREEN_BED, "facing=east,part=head");
                put(b, w - 3, y, z, Material.GREEN_BED, "facing=east,part=foot");
                put(b, w - 4, y, z, Material.CHEST, "facing=west,type=single");
            }
        }
        // Weapon racks down the aisle: posts hung with spears and a barrel of arrows.
        if (y == 0) for (int z = 3; z <= d - 4; z += 4) {
            put(b, w / 2, 0, z, Material.SPRUCE_FENCE);
            put(b, w / 2, 1, z, Material.LIGHTNING_ROD);
        }
    }

    private static void stables(ReachBrush b, Layout lay) {
        int w = lay.w(), d = lay.d();
        for (int z = 1; z <= d - 2; z++) {
            boolean divider = z % 3 == 0;
            for (int x = 1; x <= w - 2; x++) {
                if (x == w / 2) continue;
                if (divider && (x < w / 2 - 1 || x > w / 2 + 1)) put(b, x, 0, z, Material.SPRUCE_FENCE);
                else if (x == 1 || x == w - 2) put(b, x, 0, z, z % 3 == 1 ? Material.HAY_BLOCK : Material.COMPOSTER, z % 3 == 1 ? "axis=y" : "level=5");
            }
        }
    }

    private static int[] office(ReachBrush b, Layout lay, Trade trade, long seed) {
        int w = lay.w(), d = lay.d();
        for (int x = 1; x <= w - 2; x++) for (int y = 0; y <= 2; y++) {
            if (x == lay.ladderX() || x == lay.chimX()) continue;
            put(b, x, y, d - 2, y == 1 && x % 3 == 0 ? Material.CHISELED_BOOKSHELF : Material.BOOKSHELF, y == 1 && x % 3 == 0 ? "facing=north" : "");
        }
        // Desks: a lectern or a map table with a chair, along the sides.
        for (int z = 2; z <= d - 4; z += 3) {
            put(b, 1, 0, z, trade == Trade.MAPS ? Material.CARTOGRAPHY_TABLE : Material.LECTERN, trade == Trade.MAPS ? "" : "facing=east");
            put(b, w - 2, 0, z, Material.LECTERN, "facing=west");
        }
        put(b, w / 2, 0, d / 2, Material.DARK_OAK_FENCE);
        put(b, w / 2, 1, d / 2, Material.DARK_OAK_PRESSURE_PLATE);
        return new int[] {w / 2, 0, Math.min(d - 3, d / 2 + 1)};
    }

    private static void bedrooms(ReachBrush b, Layout lay, int y, long seed, boolean many) {
        int w = lay.w(), d = lay.d();
        Material[] beds = {Material.RED_BED, Material.BLUE_BED, Material.GREEN_BED, Material.YELLOW_BED, Material.WHITE_BED};
        int count = many ? Math.max(1, (d - 4) / 3) : 1;
        for (int k = 0; k < count; k++) {
            int z = 2 + k * 3;
            if (z + 1 > d - 2) break;
            Material bed = beds[(int) Math.floorMod(seed + k, (long) beds.length)];
            put(b, 1, y, z, bed, "facing=north,part=head");
            put(b, 1, y, z + 1, bed, "facing=north,part=foot");
            put(b, 2, y, z, Material.CHEST, "facing=west,type=single");
        }
        put(b, w - 2, y, 2, Material.BOOKSHELF);
        put(b, w - 2, y + 1, 2, Material.POTTED_AZURE_BLUET);
        put(b, w / 2, y, d / 2, Material.BROWN_CARPET);
        put(b, w / 2 + 1, y, d / 2, Material.BROWN_CARPET);
        put(b, w / 2, y + 2, d / 2, Material.LANTERN, "hanging=true");
    }

    private static void loft(ReachBrush b, Layout lay, int y, long seed) {
        for (int x = 1; x <= lay.w() - 2; x += 2) put(b, x, y, lay.d() - 3, (x + seed) % 3 == 0 ? Material.BARREL : Material.HAY_BLOCK,
            (x + seed) % 3 == 0 ? "facing=up" : "axis=y");
    }

    /** The properties a block needs to stand facing a direction, if it takes any. */
    static String props(Material m, String facing) {
        return switch (m) {
            case BARREL, CHISELED_BOOKSHELF, LOOM, SMOKER, BLAST_FURNACE, FURNACE, LECTERN, DECORATED_POT, OBSERVER, PISTON, CARVED_PUMPKIN, BEEHIVE ->
                m == Material.BARREL ? "facing=" + facing : m == Material.PISTON ? "facing=up,extended=false" : m == Material.SMOKER || m == Material.BLAST_FURNACE || m == Material.FURNACE ? "facing=" + facing + ",lit=false" : "facing=" + facing;
            case ANVIL -> "facing=" + (facing.equals("north") || facing.equals("south") ? "east" : "north");
            case GRINDSTONE -> "face=floor,facing=" + facing;
            case CHEST -> "facing=" + facing + ",type=single";
            case HAY_BLOCK, STRIPPED_OAK_LOG, STRIPPED_SPRUCE_LOG, STRIPPED_BIRCH_LOG -> "axis=y";
            case BREWING_STAND -> "has_bottle_0=true,has_bottle_1=true,has_bottle_2=false";
            case REDSTONE_LAMP -> "lit=false";
            case COPPER_BLOCK, GOLD_BLOCK, IRON_BLOCK -> "";
            default -> "";
        };
    }
}

package net.nerdsmp.paper.reach;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * Highcrown, the capital (docs/vault/30 Zones/Highcrown.md). One axis runs from the sky docks on the southern lip,
 * up the market avenue (ref 4) to the Kingsgate, then over the Kingsbridge and the Goldwater gorge to the Vale.
 * The keep stands on its crag to the east, Crafters' Row works the river to the west, and the docks' tall towers
 * (ref 5) and moored airships hang over the cloud sea.
 */
final class HighcrownCity {
    /** Street level: the city platform's top block is 107, so people stand at 108. */
    static final int STREET = 108;
    static final int GATE_Z = 822, AVENUE_HALF = 6, DOCK_SQUARE_Z = 1000;

    private record Mark(String id, int x, int y, int z, float yaw) { }

    private final ReachTerrain terrain;
    private final ReachKit kit;
    private final ReachCanvas canvas = new ReachCanvas();
    private final List<Mark> marks = new ArrayList<>();

    private HighcrownCity(ReachTerrain terrain) {
        this.terrain = terrain;
        this.kit = new ReachKit(terrain);
    }

    static ReachFeatures.Built build(ReachTerrain terrain) {
        HighcrownCity city = new HighcrownCity(terrain);
        city.streets();
        city.kingsbridge();
        city.kingsgate();
        city.northWall();
        city.plaza();
        city.avenue();
        city.citadelSteps();
        city.keep();
        city.districts();
        city.dockSquare();
        city.docks();
        city.crafters();
        return new ReachFeatures.Built(city.canvas, city.marks.stream()
            .map(m -> new ReachFeatures.Spot(m.id(), m.x() + 0.5, m.y(), m.z() + 0.5, m.yaw())).toList());
    }

    private ReachBrush brush(int x, int z, int turn) { return new ReachBrush(canvas, x, STREET, z, turn); }

    private boolean onPlatform(int x, int z) {
        ReachTerrain.Column c = terrain.column(x, z);
        return c.land() && !c.wet() && c.top() >= STREET - 2 && c.river() > 4;
    }

    // ------------------------------------------------------------------ paving

    /** Paving with a value ramp: worn path down the middle, stone bricks, andesite edges, moss in the cracks. */
    private void pave(int x, int z, double centre) {
        double p = terrain.patch(x, z, 0.18), g = terrain.grain(x, z, 0.35);
        Material m;
        if (centre < 0.25) m = g > 0.55 ? Material.COBBLESTONE : p > 0.25 ? Material.STONE_BRICKS : Material.POLISHED_ANDESITE;
        else if (centre < 0.7) m = g > 0.6 ? Material.CRACKED_STONE_BRICKS : p < -0.45 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS;
        else m = g > 0.4 ? Material.ANDESITE : Material.STONE_BRICKS;
        canvas.set(x, STREET - 1, z, m);
        // Carry the paving down to the ground where the platform dips, so no street floats.
        ReachTerrain.Column c = terrain.column(x, z);
        if (c.land() && !c.wet()) for (int y = c.top() + 1; y < STREET - 1; y++) if (!canvas.has(x, y, z)) canvas.set(x, y, z, Material.STONE);
        for (int y = STREET; y < STREET + 5; y++) if (!canvas.has(x, y, z)) canvas.set(x, y, z, Material.AIR);
    }

    private void streets() {
        // The avenue from the gate to the docks.
        for (int z = GATE_Z; z <= DOCK_SQUARE_Z + 30; z++) for (int x = -AVENUE_HALF - 6; x <= AVENUE_HALF + 6; x++)
            pave(x, z, Math.min(1, Math.abs(x) / (double) AVENUE_HALF));
        // Cross streets every 40 blocks, east and west.
        for (int z : new int[] {900, 940, 980}) for (int x = -60; x <= 150; x++) for (int dz = -2; dz <= 2; dz++)
            if (onPlatform(x, z + dz)) pave(x, z + dz, Math.abs(dz) / 2.0);
        // A street along the gorge rim, and one round the east.
        for (int z = 860; z <= 1030; z++) for (int dx = -2; dx <= 2; dx++) if (onPlatform(70 + dx, z)) pave(70 + dx, z, Math.abs(dx) / 2.0);
    }

    // ------------------------------------------------------------------ the arrival from the Vale

    /** A three-arch stone bridge over the gorge, carrying the Kingsroad to the gate. */
    private void kingsbridge() {
        int z0 = 764, z1 = GATE_Z - 4, half = 5;
        for (int z = z0; z <= z1; z++) {
            for (int x = -half; x <= half; x++) {
                canvas.set(x, STREET - 1, z, Math.abs(x) <= 2 ? (terrain.grain(x, z, 0.4) > 0.5 ? Material.COBBLESTONE : Material.STONE_BRICKS) : Material.POLISHED_ANDESITE);
                for (int y = STREET; y < STREET + 4; y++) canvas.set(x, y, z, Material.AIR);
            }
            // Parapets with a coping of slabs and lamps on the piers.
            for (int x : new int[] {-half - 1, half + 1}) {
                canvas.set(x, STREET - 1, z, Material.STONE_BRICKS);
                canvas.set(x, STREET, z, Material.STONE_BRICKS);
                canvas.set(x, STREET + 1, z, Material.STONE_BRICK_SLAB, "type=bottom");
            }
            // The deck's underside: arches springing from three piers.
            int span = (z - z0) % 18;
            double arch = Math.sin(Math.PI * span / 18.0);
            int soffit = STREET - 2 - (int) Math.round(9 * (1 - arch));
            for (int x = -half - 1; x <= half + 1; x++) for (int y = soffit; y < STREET - 1; y++)
                canvas.set(x, y, z, y == soffit ? Material.STONE_BRICKS : x == -half - 1 || x == half + 1 ? Material.STONE_BRICKS : Material.STONE);
            if (span == 0) for (int x = -half - 2; x <= half + 2; x++) for (int dz = -1; dz <= 1; dz++) {
                ReachTerrain.Column c = terrain.column(x, z + dz);
                int floor = c.land() ? c.top() : STREET - 30;
                for (int y = floor; y < STREET - 1; y++) canvas.set(x, y, z + dz, y < 94 ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS);
            }
            if (span == 0) for (int x : new int[] {-half - 1, half + 1}) {
                canvas.set(x, STREET + 1, z, Material.STONE_BRICKS);
                canvas.set(x, STREET + 2, z, Material.LANTERN);
            }
        }
        marks.add(new Mark("kingsbridge", 0, STREET, 790, 180F));
    }

    /** The Kingsgate (ref 4): twin towers, a wide arch, the green emblem of Highcrown over it. */
    private void kingsgate() {
        ReachKit.Style s = ReachKit.Style.SLATE;
        int z0 = GATE_Z - 3, z1 = GATE_Z + 11;
        // The central block with the gate passage.
        for (int x = -9; x <= 9; x++) for (int z = z0; z <= z1; z++) for (int y = STREET - 1; y < STREET + 28; y++) {
            boolean passage = Math.abs(x) <= 4 && y < STREET + 11 - (Math.abs(x) == 4 ? 2 : Math.abs(x) == 3 ? 1 : 0);
            boolean shell = Math.abs(x) == 9 || z == z0 || z == z1 || y == STREET + 27 || y == STREET + 12;
            if (passage) { canvas.set(x, y, z, y == STREET - 1 ? Material.STONE_BRICKS : Material.AIR); continue; }
            if (!shell && y > STREET - 1) { canvas.set(x, y, z, Material.AIR); continue; }
            canvas.set(x, y, z, kitWeather(x, y, z));
        }
        // Portcullis raised in the arch, iron bars hanging.
        for (int x = -3; x <= 3; x++) canvas.set(x, STREET + 10, z0 + 1, Material.IRON_BARS, "east=true,west=true");
        // Crenellations along the top.
        for (int x = -9; x <= 9; x++) for (int z : new int[] {z0, z1}) if (Math.floorMod(x, 2) == 0) canvas.set(x, STREET + 28, z, Material.STONE_BRICKS);
        // Buttresses stepping out from each face, and a row of arrow slits under the parapet.
        for (int x : new int[] {-9, -5, 5, 9}) for (int z : new int[] {z0 - 1, z1 + 1}) for (int y = STREET - 1; y < STREET + 24; y++) {
            int step = y < STREET + 8 ? 0 : y < STREET + 16 ? 1 : 2;
            if (step == 2 && Math.abs(x) == 5) continue;
            canvas.set(x, y, z, step == 0 ? Material.TUFF_BRICKS : kitWeather(x, y, z));
            if (y == STREET + 7 || y == STREET + 15) canvas.set(x, y + 1, z, Material.STONE_BRICK_STAIRS, z < z0 ? "facing=south,half=bottom" : "facing=north,half=bottom");
        }
        for (int x = -7; x <= 7; x += 2) for (int z : new int[] {z0, z1}) canvas.set(x, STREET + 24, z, Material.AIR);
        // The emblem on both faces: a green roundel with a gold crown, framed in dark timber.
        for (int ez : new int[] {z0 - 1, z1 + 1}) {
            int ey = STREET + 18;
            for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) {
                double r = Math.hypot(dx, dy);
                if (r > 4.4) continue;
                Material m = r > 3.5 ? Material.STRIPPED_DARK_OAK_WOOD : Material.GREEN_TERRACOTTA;
                if (r < 3.5 && (dy == 1 && Math.abs(dx) <= 2 || dy == 2 && Math.abs(dx) % 2 == 0 && Math.abs(dx) <= 2 || dy == 0 && Math.abs(dx) <= 2)) m = Material.GOLD_BLOCK;
                canvas.set(dx, ey + dy, ez, m);
            }
        }
        // Banners on the passage walls.
        for (int x : new int[] {-5, 5}) canvas.set(x, STREET + 7, z0 - 1, Material.GREEN_WALL_BANNER, "facing=north");
        // Twin towers, stone almost to the top, with tall flared spires.
        for (int sx : new int[] {-1, 1}) {
            int cx = sx * 16;
            ReachBrush t = new ReachBrush(canvas, cx - 5, STREET, z0 - 2, 0);
            kit.keepTower(t, 11, 40, s, 7 + sx);
        }
        marks.add(new Mark("kingsgate", 0, STREET, GATE_Z + 14, 180F));
    }

    private Material kitWeather(int x, int y, int z) {
        double c = terrain.patch(x + y * 0.7, z - y * 0.5, 0.2);
        if (y < STREET + 2 && c > 0.3) return Material.MOSSY_STONE_BRICKS;
        if (c < -0.55) return Material.CRACKED_STONE_BRICKS;
        if (c > 0.6) return Material.TUFF_BRICKS;
        return Material.STONE_BRICKS;
    }

    /** The north wall from the gate east to the lip, crenellated, with a walk and small towers. */
    private void northWall() {
        for (int x = 20; x <= 175; x++) {
            int z = GATE_Z + 3 + (int) Math.round(6 * Math.sin(x / 40.0));
            if (!terrain.column(x, z).land()) break;
            int ground = terrain.column(x, z).top();
            for (int dz = 0; dz < 4; dz++) {
                for (int y = ground; y < STREET + 12; y++) canvas.set(x, y, z + dz, kitWeather(x, y, z + dz));
                canvas.set(x, STREET + 12, z + dz, dz == 0 || dz == 3 ? Material.STONE_BRICKS : Material.STONE_BRICK_SLAB, dz == 0 || dz == 3 ? "" : "type=bottom");
            }
            if (Math.floorMod(x, 2) == 0) canvas.set(x, STREET + 13, z, Material.STONE_BRICKS);
            if (x % 40 == 0) {
                ReachBrush t = new ReachBrush(canvas, x - 3, STREET, z - 1, 0);
                kit.tower(t, 7, 20, ReachKit.Style.SLATE, x);
            }
        }
    }

    // ------------------------------------------------------------------ plaza and avenue

    private void plaza() {
        int z0 = GATE_Z + 12, z1 = GATE_Z + 44;
        for (int x = -32; x <= 32; x++) for (int z = z0; z <= z1; z++) if (onPlatform(x, z)) {
            double ring = Math.hypot(x, (z - (z0 + z1) / 2.0) * 1.3);
            pave(x, z, ring < 6 ? 0.9 : Math.abs(Math.sin(ring / 3.0)) * 0.6);
        }
        // A fountain on the axis: two basins and a statue of a Warden with the empty crown.
        int cz = (z0 + z1) / 2;
        for (int x = -5; x <= 5; x++) for (int z = cz - 5; z <= cz + 5; z++) {
            double r = Math.hypot(x, z - cz);
            if (r > 5.4) continue;
            if (r > 4.4) { canvas.set(x, STREET, z, Material.STONE_BRICK_SLAB, "type=bottom"); canvas.set(x, STREET - 1, z, Material.STONE_BRICKS); }
            else { canvas.set(x, STREET - 1, z, Material.STONE_BRICKS); canvas.set(x, STREET, z, Material.WATER); }
        }
        for (int y = STREET; y < STREET + 4; y++) canvas.set(0, y, cz, Material.STONE_BRICKS);
        canvas.set(0, STREET + 4, cz, Material.CHISELED_STONE_BRICKS);
        for (int y = STREET + 5; y < STREET + 9; y++) canvas.set(0, y, cz, y == STREET + 8 ? Material.POLISHED_DEEPSLATE : Material.POLISHED_ANDESITE);
        canvas.set(0, STREET + 9, cz, Material.GOLD_BLOCK);
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) canvas.set(d[0], STREET + 4, cz + d[1], Material.WATER);
        // Lamps round the plaza.
        for (int[] p : new int[][] {{-26, z0 + 3}, {26, z0 + 3}, {-26, z1 - 3}, {26, z1 - 3}, {-12, z0 + 1}, {12, z0 + 1}})
            kit.lamp(brush(p[0], p[1], 0), ReachKit.Style.HIGHCROWN);
        marks.add(new Mark("plaza", 0, STREET, cz + 8, 180F));
        marks.add(new Mark("plaza_guide", 6, STREET, z0 + 4, 180F));
        // The Gate Ring: travel and field services along the plaza's north side, facing the city.
        int[] ring = {-24, -18, -12, 12, 18, 24, -6};
        for (int i = 0; i < ring.length; i++) marks.add(new Mark("gate_ring_" + i, ring[i], STREET, z0 + 4, 0F));
        // The Hall of Callings: skills, mastery, jobs and parties on the plaza's west side.
        for (int i = 0; i < 3; i++) marks.add(new Mark("hall_" + i, -28, STREET, cz - 6 + i * 6, 270F));
    }

    /** Market stalls both sides of the avenue in front of an unbroken row of houses (ref 4). */
    private void avenue() {
        Material[][] awnings = {{Material.RED_WOOL, Material.WHITE_WOOL}, {Material.BLUE_WOOL, Material.WHITE_WOOL},
            {Material.PURPLE_WOOL, Material.LIGHT_GRAY_WOOL}, {Material.BROWN_WOOL, Material.WHITE_WOOL}};
        Material[] goods = {Material.MELON, Material.PUMPKIN, Material.HAY_BLOCK, Material.BARREL, Material.DRIED_KELP_BLOCK};
        int i = 0;
        for (int z = GATE_Z + 50; z < DOCK_SQUARE_Z - 8; z += 8, i++) {
            Material[] a = awnings[i % awnings.length];
            // West side faces east (turn 1, depth runs west); east side faces west (turn 3, depth runs east).
            kit.stall(new ReachBrush(canvas, -AVENUE_HALF - 1, STREET, z, 1), a[0], a[1], goods[i % goods.length], ReachKit.Style.HIGHCROWN);
            Material[] b = awnings[(i + 2) % awnings.length];
            kit.stall(new ReachBrush(canvas, AVENUE_HALF + 1, STREET, z + 4, 3), b[0], b[1], goods[(i + 3) % goods.length], ReachKit.Style.HIGHCROWN);
            if (i % 2 == 0) {
                kit.lamp(brush(-AVENUE_HALF, z + 7, 0), ReachKit.Style.HIGHCROWN);
                kit.lamp(brush(AVENUE_HALF, z + 7, 2), ReachKit.Style.HIGHCROWN);
            }
            marks.add(new Mark("stall_w_" + i, -AVENUE_HALF + 1, STREET, z + 2, 90F));
            marks.add(new Mark("stall_e_" + i, AVENUE_HALF - 1, STREET, z + 2, 270F));
        }
        // House fronts behind the stalls, both sides, with varied widths and roofs.
        row(-AVENUE_HALF - 7, GATE_Z + 48, DOCK_SQUARE_Z - 4, 1, 1001);
        row(AVENUE_HALF + 7, GATE_Z + 48, DOCK_SQUARE_Z - 4, 3, 2002);
    }

    /**
     * A terrace of houses along z from {@code z0} to {@code z1} with fronts on the line x = {@code frontX},
     * facing the avenue: {@code turn} 1 faces east (the west row), 3 faces west (the east row).
     */
    private void row(int frontX, int z0, int z1, int turn, long seed) {
        int z = z0;
        int n = 0;
        ReachKit.Style[] styles = {ReachKit.Style.HIGHCROWN, ReachKit.Style.SLATE, ReachKit.Style.HIGHCROWN, ReachKit.Style.SLATE};
        while (z < z1 - 6) {
            long h = ReachFlora.hash((int) seed + n * 31, n * 17);
            int w = 7 + (int) (h & 3);
            if (z + w > z1) break;
            int floors = 1 + (int) ((h >> 3) % 3);
            int depth = 10 + (int) ((h >> 6) & 3);
            boolean gableFront = ((h >> 9) & 1) == 0;
            ReachKit.Style s = styles[(int) ((h >> 11) & 3)];
            ReachBrush b;
            if (turn == 1) b = new ReachBrush(canvas, frontX, STREET, z, 1);
            else b = new ReachBrush(canvas, frontX, STREET, z + w - 1, 3);
            kit.house(b, w, depth, floors, s, floors > 1, gableFront, h);
            z += w;
            n++;
        }
    }

    // ------------------------------------------------------------------ keep hill

    private void citadelSteps() {
        // A broad stair from the plaza's east side up to the keep's gate, rising one block every two.
        int x0 = 34, z0 = GATE_Z + 26, top = 124;
        for (int i = 0; i < 40; i++) {
            int x = x0 + i;
            int y = STREET - 1 + Math.min(top - STREET + 1, i / 2);
            for (int dz = -3; dz <= 3; dz++) {
                for (int yy = terrain.column(x, z0 + dz).top(); yy < y; yy++) canvas.set(x, yy, z0 + dz, Material.STONE_BRICKS);
                boolean step = i % 2 == 1 && y < top;
                canvas.set(x, y, z0 + dz, step ? Material.STONE_BRICK_STAIRS : Material.POLISHED_ANDESITE, step ? "facing=east,half=bottom" : "");
                for (int yy = y + 1; yy < y + 5; yy++) canvas.set(x, yy, z0 + dz, Material.AIR);
            }
            for (int dz : new int[] {-4, 4}) {
                canvas.set(x, y, z0 + dz, Material.STONE_BRICKS);
                canvas.set(x, y + 1, z0 + dz, Material.STONE_BRICK_WALL, "up=true");
                if (i % 8 == 0) canvas.set(x, y + 2, z0 + dz, Material.LANTERN);
            }
        }
        marks.add(new Mark("citadel_steps", x0 - 2, STREET, z0, 270F));
    }

    /** Highcrown Keep on its crag: a curtain wall with corner towers and a tall donjon holding the empty crown. */
    private void keep() {
        int cx = 112, cz = 868, top = 125, half = 22;
        ReachKit.Style s = ReachKit.Style.SLATE;
        // Courtyard paving and curtain walls.
        for (int x = cx - half; x <= cx + half; x++) for (int z = cz - half; z <= cz + half; z++) {
            boolean wall = Math.abs(x - cx) >= half - 2 || Math.abs(z - cz) >= half - 2;
            int ground = terrain.column(x, z).top();
            for (int y = Math.min(ground, top - 1); y < top - 1; y++) canvas.set(x, y, z, Material.STONE_BRICKS);
            if (!wall) {
                canvas.set(x, top - 1, z, terrain.patch(x, z, 0.2) > 0.3 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                for (int y = top; y < top + 6; y++) canvas.set(x, y, z, Material.AIR);
                continue;
            }
            for (int y = top - 1; y < top + 12; y++) canvas.set(x, y, z, kitWeather(x, y, z));
            if (Math.floorMod(x + z, 2) == 0 && (Math.abs(x - cx) == half || Math.abs(z - cz) == half)) canvas.set(x, top + 12, z, Material.STONE_BRICKS);
        }
        // The keep gate on the west side, toward the steps.
        for (int z = cz - 3; z <= cz + 3; z++) for (int y = top; y < top + 7; y++) for (int x = cx - half; x <= cx - half + 2; x++)
            canvas.set(x, y, z, Material.AIR);
        for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}})
            kit.tower(new ReachBrush(canvas, cx + c[0] * half - 4, top, cz + c[1] * half - 4, 0), 9, 22, s, c[0] * 3 + c[1]);
        // The donjon.
        ReachBrush donjon = new ReachBrush(canvas, cx + 2, top, cz - 8, 0);
        int dw = 15, dd = 15, dh = 34;
        for (int x = 0; x < dw; x++) for (int z = 0; z < dd; z++) for (int y = 0; y < dh; y++) {
            boolean wall = x == 0 || x == dw - 1 || z == 0 || z == dd - 1;
            boolean floor = y % 8 == 7;
            if (!wall) { donjon.set(x, y, z, floor ? Material.SPRUCE_PLANKS : Material.AIR); continue; }
            boolean pier = x % 7 == 0 || z % 7 == 0;
            donjon.set(x, y, z, pier ? Material.POLISHED_DEEPSLATE : kitWeather(donjon.wx(x, z), STREET + y, donjon.wz(x, z)));
        }
        for (int y = 2; y < dh - 3; y += 8) for (int k : new int[] {3, 4, 10, 11}) {
            donjon.set(k, y, 0, Material.GLASS_PANE, "east=true,west=true"); donjon.set(k, y + 1, 0, Material.GLASS_PANE, "east=true,west=true");
            donjon.set(0, y, k, Material.GLASS_PANE, "north=true,south=true"); donjon.set(0, y + 1, k, Material.GLASS_PANE, "north=true,south=true");
        }
        donjon.air(6, 0, 0, 8, 4, 0);
        kit.spire(donjon, -1, dw, dh, s);
        // The empty crown on its stand in the hall.
        donjon.set(7, 0, 9, Material.POLISHED_BLACKSTONE_BRICKS);
        donjon.set(7, 1, 9, Material.GOLD_BLOCK);
        donjon.set(7, 2, 9, Material.LIGHTNING_ROD);
        marks.add(new Mark("keep_hall", cx + 9, top, cz - 2, 180F));
        marks.add(new Mark("keep_court", cx - 8, top, cz + 6, 270F));
    }

    // ------------------------------------------------------------------ districts

    /** Houses filling the blocks between the cross streets, east and west of the avenue. */
    private void districts() {
        int n = 0;
        for (int z : new int[] {905, 945}) for (int x = 24; x <= 150; x += 13) {
            if (!onPlatform(x, z) || !onPlatform(x + 10, z + 30) || Math.hypot(x - 112, z - 868) < 50) continue;
            long h = ReachFlora.hash(x * 3 + 7, z);
            int w = 8 + (int) (h & 3);
            kit.house(new ReachBrush(canvas, x, STREET, z + 2, 2).at(-w + 1, 0, -12, 0), w, 11, 1 + (int) ((h >> 4) % 3), ReachKit.Style.HIGHCROWN, true, (h & 8) == 0, h);
            n++;
        }
        for (int z = 905; z <= 985; z += 40) for (int x = -40; x >= -60; x -= 14) {
            if (!onPlatform(x, z) || !onPlatform(x - 10, z + 20)) continue;
            long h = ReachFlora.hash(x * 5 - 3, z);
            kit.house(new ReachBrush(canvas, x, STREET, z + 3, 0), 9, 10, 1 + (int) ((h >> 4) % 2), ReachKit.Style.SLATE, true, (h & 8) == 0, h);
        }
    }

    // ------------------------------------------------------------------ the sky docks

    private void dockSquare() {
        for (int x = -30; x <= 30; x++) for (int z = DOCK_SQUARE_Z; z <= DOCK_SQUARE_Z + 40; z++) if (onPlatform(x, z))
            pave(x, z, 0.5 + 0.5 * Math.sin(x / 4.0) * Math.sin(z / 4.0));
        // The quay along the lip, from tower to tower.
        for (int x = -62; x <= 62; x++) for (int z = DOCK_SQUARE_Z + 41; z <= 1092; z++) {
            ReachTerrain.Column c = terrain.column(x, z);
            if (c.land() && !c.wet() && c.edge() > 2) pave(x, z, Math.abs(x) < 30 ? 0.4 : 0.8);
        }
        // The Tethered Gull: a wide three-storey inn on the square's east side.
        kit.house(new ReachBrush(canvas, 32, STREET, DOCK_SQUARE_Z + 2, 1), 15, 13, 2, ReachKit.Style.HARBOUR, true, false, 77);
        canvas.set(31, STREET + 3, DOCK_SQUARE_Z + 9, Material.SPRUCE_WALL_SIGN, "facing=west");
        marks.add(new Mark("tethered_gull", 28, STREET, DOCK_SQUARE_Z + 9, 90F));
        marks.add(new Mark("dock_square", 0, STREET, DOCK_SQUARE_Z + 20, 180F));
        // The arrival line where islanders step off the piers, and the memorial at the square's west end.
        for (int i = 0; i < 3; i++) marks.add(new Mark("arrival_" + i, -12 + i * 12, STREET, DOCK_SQUARE_Z + 30, 0F));
        marks.add(new Mark("memorial", -24, STREET, DOCK_SQUARE_Z + 24, 270F));
    }

    /** Three piers over the cloud sea, the Dockmaster's Tower and the Guildhall, and two moored airships. */
    private void docks() {
        int lip = 1094;
        for (int px : new int[] {-24, 0, 24}) {
            for (int z = lip - 12; z <= lip + 52; z++) for (int x = px - 2; x <= px + 2; x++) {
                canvas.set(x, STREET - 1, z, Math.abs(x - px) == 2 ? Material.STRIPPED_SPRUCE_LOG : Material.SPRUCE_PLANKS,
                    Math.abs(x - px) == 2 ? "axis=z" : "");
                for (int y = STREET; y < STREET + 4; y++) if (!canvas.has(x, y, z)) canvas.set(x, y, z, Material.AIR);
            }
            for (int z = lip - 8; z <= lip + 52; z += 6) for (int x : new int[] {px - 2, px + 2}) {
                canvas.set(x, STREET, z, Material.SPRUCE_FENCE);
                canvas.set(x, STREET + 1, z, z % 12 == 0 ? Material.LANTERN : Material.SPRUCE_FENCE);
                // Struts back to the cliff and chains down.
                for (int k = 1; k <= 6 && z - k * 2 > lip - 14; k++) canvas.set(x, STREET - 1 - k, z - k * 2 + 1, Material.STRIPPED_SPRUCE_LOG, "axis=y");
                canvas.set(x, STREET - 2, z, Material.IRON_CHAIN, "axis=y");
                canvas.set(x, STREET - 3, z, Material.IRON_CHAIN, "axis=y");
            }
            // Bollards and cargo at the pier head.
            kit.clutter(brush(px - 1, lip + 48, 0), px);
            marks.add(new Mark("pier_" + (px < 0 ? "west" : px > 0 ? "east" : "mid"), px, STREET, lip + 4, 0F));
        }
        // The two tall towers of the docks (ref 5).
        kit.tower(new ReachBrush(canvas, -48, STREET, lip - 18, 0), 9, 36, ReachKit.Style.HARBOUR, 41);
        kit.tower(new ReachBrush(canvas, 40, STREET, lip - 18, 0), 9, 32, ReachKit.Style.HARBOUR, 42);
        marks.add(new Mark("dockmaster", -44, STREET, lip - 21, 180F));
        marks.add(new Mark("guildhall", 44, STREET, lip - 21, 180F));
        // Airships moored between the piers, bows to the south, using the expedition airship model.
        net.nerdsmp.paper.expedition.BuildCanvas ships = new net.nerdsmp.paper.expedition.BuildCanvas();
        net.nerdsmp.paper.expedition.SkyShip.moor(ships, 0, 0, 0);
        stamp(ships, new ReachBrush(canvas, -12, STREET + 1, lip + 34, 1));
        stamp(ships, new ReachBrush(canvas, 12, STREET + 1, lip + 30, 1));
    }

    /** Copies a local expedition-kit canvas into the city through a brush, turning block states with it. */
    private static void stamp(net.nerdsmp.paper.expedition.BuildCanvas source, ReachBrush at) {
        source.blocks().forEach((cell, spec) -> at.set(cell.x(), cell.y(), cell.z(), spec.material(), spec.properties()));
    }

    // ------------------------------------------------------------------ crafters' row

    /** Workshops on the gorge's east rim, with a waterwheel turning in the river below. */
    private void crafters() {
        // Workshops along a back street between the avenue's west row and the gorge, fronts facing east.
        int i = 0;
        for (int z = GATE_Z + 50; z <= DOCK_SQUARE_Z - 20 && i < 5; z += 13) {
            int front = -AVENUE_HALF - 7 - 14 - 6, w = 9, d = 10;
            boolean clear = true;
            for (int x = front - d - 1; x <= front + 1 && clear; x++) for (int dz = -1; dz <= w; dz++) if (!onPlatform(x, z + dz)) { clear = false; break; }
            if (!clear) continue;
            kit.house(new ReachBrush(canvas, front, STREET, z, 1), w, d, 1 + (i % 2), ReachKit.Style.VALE_SPRUCE, false, true, 300 + i);
            for (int dz = -1; dz <= w; dz++) for (int dx = 1; dx <= 5; dx++) pave(front + dx, z + dz, 0.4);
            marks.add(new Mark("crafter_" + i, front + 3, STREET, z + w / 2, 270F));
            i++;
        }
        waterwheel(-104, 878);
    }

    private void waterwheel(int cx, int cz) {
        ReachTerrain.Column c = terrain.column(cx, cz);
        int water = c.wet() ? c.water() : 91;
        int axleY = water + 4;
        for (int a = 0; a < 360; a += 8) {
            double rad = Math.toRadians(a);
            int y = axleY + (int) Math.round(5 * Math.sin(rad)), z = cz + (int) Math.round(5 * Math.cos(rad));
            canvas.set(cx, y, z, Material.STRIPPED_SPRUCE_LOG, "axis=x");
            canvas.set(cx + 1, y, z, Material.SPRUCE_TRAPDOOR, "facing=east,half=bottom,open=true");
        }
        for (int r = 0; r < 5; r++) {
            canvas.set(cx, axleY + r, cz, Material.SPRUCE_FENCE);
            canvas.set(cx, axleY - r, cz, Material.SPRUCE_FENCE);
            canvas.set(cx, axleY, cz + r, Material.SPRUCE_FENCE);
            canvas.set(cx, axleY, cz - r, Material.SPRUCE_FENCE);
        }
        for (int x = cx; x <= cx + 8; x++) canvas.set(x, axleY, cz, Material.STRIPPED_DARK_OAK_LOG, "axis=x");
    }
}

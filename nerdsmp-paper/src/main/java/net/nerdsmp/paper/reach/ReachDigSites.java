package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.digsite.DigSiteDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The Reach's dig sites: terraced excavation pits where the ground may be dug, and only there. Each pit's floors
 * and faces are its diggable blocks (clay and gravel at Millbrook, the barrow's soil, ore in the Ironspine's cut,
 * red sand in the Sunscar, mud on Mistmere's flats); a rope cordon runs round it, steps lead down, and a canvas
 * shelter keeps the sieve and the tools. When the Reach starts, every pit's diggable blocks are registered with
 * the dig-site service as nodes, inside the pit's own safe zone: those blocks can be broken, regrow after a while,
 * and nothing else nearby can be touched.
 */
public final class ReachDigSites {
    /** One dig site: where it is planned, what can be dug there, and what its floor and faces are made of. */
    record Spec(String id, String title, int x, int z, int search, List<Material> floor, List<Material> face, boolean ore) {
        String featureId() { return "dig_" + id; }
        String zoneId() { return "reach_dig_" + id; }
        Set<Material> nodes() {
            java.util.HashSet<Material> all = new java.util.HashSet<>(floor);
            all.addAll(face);
            all.removeIf(m -> m == Material.STONE || m == Material.ANDESITE || m == Material.TUFF);
            return all;
        }
    }

    static final List<Spec> SITES = List.of(
        new Spec("goldwater_clay", "Goldwater Clay Pits", -650, 345, 70, List.of(Material.CLAY, Material.CLAY, Material.GRAVEL),
            List.of(Material.CLAY, Material.COARSE_DIRT, Material.DIRT), false),
        new Spec("barrow_diggings", "The Barrow Diggings", -460, 330, 60, List.of(Material.COARSE_DIRT, Material.GRAVEL, Material.DIRT),
            List.of(Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT), false),
        new Spec("foremans_cut", "The Foreman's Open Cut", 540, -640, 70, List.of(Material.GRAVEL),
            List.of(Material.IRON_ORE, Material.COAL_ORE, Material.COPPER_ORE, Material.STONE, Material.ANDESITE, Material.STONE), true),
        new Spec("redsand_diggings", "Redsand Diggings", 900, 120, 70, List.of(Material.RED_SAND, Material.RED_SAND, Material.GRAVEL),
            List.of(Material.RED_SAND, Material.COARSE_DIRT, Material.GRAVEL), false),
        new Spec("mistmere_mudflats", "Mistmere Mudflats", -60, -620, 120, List.of(Material.MUD, Material.MUD, Material.CLAY),
            List.of(Material.MUD, Material.CLAY, Material.DIRT), false));

    private static final int HX = 7, HZ = 5;

    static List<ReachFeatures.Feature> features(ReachTerrain terrain) {
        List<ReachFeatures.Feature> out = new ArrayList<>();
        for (Spec spec : SITES) {
            int[] at = siteFor(terrain, spec);
            if (at == null) continue;
            out.add(new ReachFeatures.Feature(spec.featureId(), new ReachFeatures.Footprint(at[0] - 22, at[1] - 22, at[0] + 22, at[1] + 22),
                t -> build(t, spec, at)));
        }
        return out;
    }

    /**
     * The flattest dry ground near the planned point with room for the pit, its apron and the shelter, clear of roads
     * and water, or null where there is none (the site is then left out rather than dug into a marsh).
     */
    static int[] siteFor(ReachTerrain terrain, Spec spec) {
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -spec.search(); dx <= spec.search(); dx += 4) for (int dz = -spec.search(); dz <= spec.search(); dz += 4) {
            int x = spec.x() + dx, z = spec.z() + dz;
            if (terrain.roadDistance(x, z, 26) < 24) continue;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            boolean ok = true;
            for (int sx = -HX - 7; sx <= HX + 7 && ok; sx += 2) for (int sz = -HZ - 12; sz <= HZ + 7; sz += 2) {
                ReachTerrain.Column c = terrain.column(x + sx, z + sz);
                if (!c.land() || c.wet() || c.edge() < 12 || c.river() < 4 || c.lake() < 1.4) { ok = false; break; }
                lo = Math.min(lo, c.top()); hi = Math.max(hi, c.top());
            }
            if (!ok) continue;
            double score = (hi - lo) + Math.hypot(dx, dz) * 0.04;
            if (score < bestScore) { bestScore = score; best = new int[] {x, z}; }
        }
        return best;
    }

    static ReachFeatures.Built build(ReachTerrain terrain, Spec spec, int[] at) {
        ReachCanvas canvas = new ReachCanvas();
        List<ReachFeatures.Spot> spots = new ArrayList<>();
        int cx = at[0], cz = at[1];
        int g = terrain.column(cx, cz).top() + 1;
        // The pit: terraces stepping down three blocks to the floor, every floor and face the site's own ground, in a
        // levelled apron that eases back into the slope over three blocks.
        for (int dx = -HX - 6; dx <= HX + 6; dx++) for (int dz = -HZ - 6; dz <= HZ + 6; dz++) {
            int x = cx + dx, z = cz + dz;
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet()) continue;
            int outside = Math.max(Math.abs(dx) - (HX + 3), Math.abs(dz) - (HZ + 3));
            if (outside > 0) {
                double ease = 1 - outside / 4.0;
                int t = c.top(), target = (int) Math.round(t + (g - 1 - t) * ease);
                for (int y = t + 1; y < target; y++) canvas.set(x, y, z, Material.DIRT);
                for (int y = target + 1; y <= t; y++) canvas.set(x, y, z, Material.AIR);
                if (target != t) canvas.set(x, target, z, Material.GRASS_BLOCK);
                continue;
            }
            int fromEdge = Math.min(HX + 1 - Math.abs(dx), HZ + 1 - Math.abs(dz));
            int depth = Math.max(0, Math.min(3, fromEdge - 1));
            int floor = g - 1 - depth;
            for (int y = floor + 1; y <= Math.max(c.top(), g + 3); y++) canvas.set(x, y, z, Material.AIR);
            for (int y = c.top() + 1; y < floor - 1; y++) canvas.set(x, y, z, Material.DIRT);
            if (depth == 0) {
                canvas.set(x, floor, z, Material.COARSE_DIRT);
                continue;
            }
            canvas.set(x, floor, z, pick(spec.floor(), x, floor, z));
            canvas.set(x, floor - 1, z, pick(spec.face(), x, floor - 1, z));
            canvas.set(x, floor - 2, z, spec.ore() ? pick(spec.face(), x, floor - 2, z) : Material.DIRT);
        }
        // The terrace faces: the two blocks of each step showing in the pit are dig ground too.
        for (int dx = -HX; dx <= HX; dx++) for (int dz = -HZ; dz <= HZ; dz++) {
            int x = cx + dx, z = cz + dz;
            int fromEdge = Math.min(HX + 1 - Math.abs(dx), HZ + 1 - Math.abs(dz));
            int depth = Math.max(0, Math.min(3, fromEdge - 1));
            if (depth >= 3) continue;
            for (int y = g - 1 - depth - 1; y > g - 1 - 3 - 1 && y >= g - 1 - depth - 2; y--)
                if (canvas.get(x, y, z) != null && !canvas.get(x, y, z).state().equals("minecraft:air")) canvas.set(x, y, z, pick(spec.face(), x, y, z));
        }
        // Steps down the south side.
        for (int k = 0; k < 3; k++) for (int dx = 0; dx <= 1; dx++) {
            int x = cx + dx, z = cz + HZ - k;
            int y = g - 1 - k;
            canvas.set(x, y, z, Material.SPRUCE_STAIRS, "facing=south,half=bottom");
            canvas.set(x, y - 1, z, Material.DIRT);
            for (int yy = y + 1; yy <= g + 2; yy++) canvas.set(x, yy, z, Material.AIR);
        }
        // The cordon: a rope of fence on posts round the rim, open at the steps.
        for (int dx = -HX - 1; dx <= HX + 1; dx++) for (int dz = -HZ - 1; dz <= HZ + 1; dz++) {
            if (Math.abs(dx) != HX + 1 && Math.abs(dz) != HZ + 1) continue;
            if (dz == HZ + 1 && (dx == 0 || dx == 1)) continue;
            int x = cx + dx, z = cz + dz;
            ReachTerrain.Column c = terrain.column(x, z);
            if (!c.land() || c.wet()) continue;
            boolean post = Math.floorMod(dx, 3) == 0 && Math.floorMod(dz, 3) == 0 || Math.abs(dx) == HX + 1 && Math.abs(dz) == HZ + 1;
            canvas.set(x, g, z, Material.SPRUCE_FENCE);
            if (post) canvas.set(x, g + 1, z, Material.SPRUCE_FENCE);
            if (post && Math.floorMod(dx + dz, 6) == 0) canvas.set(x, g + 2, z, Material.LANTERN, "hanging=false");
        }
        // The shelter on the north side: canvas over four posts, the sieve, a barrel of tools and the finds crate.
        int sz = cz - HZ - 5;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = cx + dx, z = sz + dz;
            ReachTerrain.Column c = terrain.column(x, z);
            for (int y = c.top() + 1; y < g - 1; y++) canvas.set(x, y, z, Material.DIRT);
            canvas.set(x, g - 1, z, Material.COARSE_DIRT);
            for (int y = g; y <= Math.max(c.top(), g + 4); y++) canvas.set(x, y, z, Material.AIR);
            if (Math.abs(dx) == 3 && Math.abs(dz) == 2) for (int y = g; y < g + 3; y++) canvas.set(x, y, z, Material.SPRUCE_FENCE);
            canvas.set(x, g + 3 + (Math.abs(dz) == 2 ? 0 : 1), z, Math.floorMod(dx, 2) == 0 ? Material.WHITE_WOOL : Material.LIGHT_GRAY_WOOL);
        }
        canvas.set(cx - 1, g, sz, Material.SCAFFOLDING, "bottom=false,distance=0,waterlogged=false");
        canvas.set(cx - 1, g + 1, sz, Material.SPRUCE_TRAPDOOR, "facing=north,half=bottom,open=false,powered=false,waterlogged=false");
        canvas.set(cx + 1, g, sz, Material.BARREL, "facing=up,open=false");
        canvas.set(cx + 2, g, sz - 1, Material.BARREL, "facing=north,open=false");
        canvas.set(cx, g + 2, sz, Material.LANTERN, "hanging=true");
        canvas.set(cx, g + 3, sz, Material.SPRUCE_FENCE);
        // The board at the head of the steps.
        int bx = cx + 3, bz = cz + HZ + 2;
        canvas.set(bx, g, bz, Material.STRIPPED_SPRUCE_LOG, "axis=y");
        canvas.set(bx, g + 1, bz, Material.STRIPPED_SPRUCE_LOG, "axis=y");
        canvas.set(bx, g + 1, bz + 1, Material.SPRUCE_WALL_SIGN, "facing=south");
        String[] words = spec.title().split(" ", 2);
        canvas.text(bx, g + 1, bz + 1, "DIG SITE", words[0], words.length > 1 ? words[1] : "", spec.ore() ? "Mine the ore" : "Dig the pit");
        spots.add(new ReachFeatures.Spot("place", cx + 0.5, g, cz + HZ + 2.5, 180F));
        spots.add(new ReachFeatures.Spot("dig", cx + 0.5, g - 3, cz + 0.5, 180F));
        return new ReachFeatures.Built(canvas, spots);
    }

    private static Material pick(List<Material> from, int x, int y, int z) {
        return from.get(Math.floorMod(x * 73 + y * 31 + z * 17 + (x ^ z), from.size()));
    }

    // ------------------------------------------------------------------ registration with the dig-site service

    private final NerdSmpPlugin plugin;
    private final ReachService reach;

    ReachDigSites(NerdSmpPlugin plugin, ReachService reach) {
        this.plugin = plugin;
        this.reach = reach;
    }

    /**
     * Registers every pit's diggable blocks as one dig site each, once their zones exist. A site whose nodes are
     * unchanged is left alone, so its regrowth state survives restarts; a regenerated pit gets a fresh generation.
     * Returns false when the world or a zone isn't ready yet, so the caller can try again.
     */
    boolean register() {
        if (plugin.digSites() == null) return true;
        World world = reach.world();
        if (world == null) return false;
        boolean all = true;
        for (Spec spec : SITES) {
            if (plugin.zones().find(spec.zoneId()).isEmpty()) { all = false; continue; }
            ReachFeatures.Feature feature = reach.features().feature(spec.featureId()).orElse(null);
            if (feature == null) continue;
            ReachFeatures.Spot dig = reach.features().spots().get(spec.featureId() + ".dig");
            if (dig == null) continue;
            // Only the pit's own floors and faces: the apron, the shelter's floor and the fill under them are not dug.
            int cx = (int) Math.floor(dig.x()), cz = (int) Math.floor(dig.z()), g = (int) Math.floor(dig.y()) + 3;
            List<DigSiteDefinition.Node> nodes = nodes(reach.features().plan(feature), spec.nodes()).stream()
                .filter(n -> Math.abs(n.x() - cx) <= HX && Math.abs(n.z() - cz) <= HZ && n.y() >= g - 6 && n.y() <= g - 2).toList();
            if (nodes.isEmpty()) continue;
            String id = "reach_" + spec.id();
            UUID generation = UUID.nameUUIDFromBytes((id + "|" + world.getUID() + "|" + nodes.hashCode()).getBytes(StandardCharsets.UTF_8));
            var existing = plugin.digSites().store().find(id);
            if (existing.isPresent() && existing.get().generation().equals(generation)) continue;
            try {
                plugin.digSites().register(new DigSiteDefinition(id, spec.zoneId(), world.getUID(), generation, true, 45, nodes));
                plugin.getLogger().info("Reach dig site " + id + " registered with " + nodes.size() + " nodes.");
            } catch (Exception failure) {
                plugin.getLogger().log(Level.WARNING, "Reach dig site " + id + " could not be registered", failure);
            }
        }
        return all;
    }

    static List<DigSiteDefinition.Node> nodes(ReachCanvas.Frozen plan, Set<Material> kinds) {
        List<DigSiteDefinition.Node> out = new ArrayList<>();
        for (int cx = plan.minX >> 4; cx <= plan.maxX >> 4; cx++) for (int cz = plan.minZ >> 4; cz <= plan.maxZ >> 4; cz++) {
            int bx = cx << 4, bz = cz << 4;
            plan.forEach(cx, cz, cell -> {
                String state = cell.spec().state();
                String name = state.contains("[") ? state.substring(0, state.indexOf('[')) : state;
                Material m = Material.matchMaterial(name);
                if (m == null || !kinds.contains(m)) return;
                String data = Bukkit.createBlockData(state).getAsString();
                out.add(new DigSiteDefinition.Node(bx + cell.lx(), cell.y(), bz + cell.lz(), m.name(), data));
            });
        }
        out.sort((a, b) -> a.x() != b.x() ? Integer.compare(a.x(), b.x()) : a.z() != b.z() ? Integer.compare(a.z(), b.z()) : Integer.compare(a.y(), b.y()));
        return out.size() > DigSiteDefinition.MAX_NODES ? out.subList(0, DigSiteDefinition.MAX_NODES) : out;
    }

    static String title(String zoneId) {
        for (Spec s : SITES) if (s.zoneId().equals(zoneId)) return s.title();
        return zoneId.toLowerCase(Locale.ROOT);
    }
}

package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.campaign.ServerProgressionService.Unlock;
import org.bukkit.Material;
import java.util.*;

/** Versioned, finite resource catalog. Coordinates, not material names, grant harvest rights. */
public final class ExpeditionLayout {
    public static final int VERSION = 1, SURFACE = 96;
    public enum Zone {
        CITY("city", "Hearthport", 0, 0, 64, Material.QUARTZ_PILLAR, "PLAINS", null,
            "Market square, workshops and the expedition board."),
        QUARRY("quarry", "Cloudbreak Quarry", 160, 0, 54, Material.IRON_PICKAXE, "STONY_PEAKS", null,
            "Mine stone, coal, copper and iron. Veins replenish over time."),
        GROVE("grove", "Windroot Grove", -160, 0, 54, Material.OAK_SAPLING, "FOREST", null,
            "Gather timber from marked trunks beneath the canopy."),
        DIGSITE("digsite", "Forgotten Terrace", 0, 160, 54, Material.BRUSH, "DESERT", null,
            "Excavate sand, clay and gravel for finds and collections."),
        HARBOR("harbor", "Stillwater Basin", 0, -160, 54, Material.FISHING_ROD, "RIVER", null,
            "Cast into open water. Bring a rod and your favorite bait."),
        CINDER("cinder", "Cinder Shelf", 160, 160, 54, Material.NETHER_QUARTZ_ORE, "NETHER_WASTES", Unlock.NETHER,
            "A campaign-unlocked source of quartz, gold and glowstone."),
        ASTRAL("astral", "Astral Reach", -160, -160, 54, Material.END_STONE, "THE_END", Unlock.END,
            "A campaign-unlocked source of end stone and rare ore.");
        public final String id, title, description;
        public final int x, z, radius;
        public final Material icon;
        public final String biome;
        public final Unlock unlock;
        Zone(String id, String title, int x, int z, int radius, Material icon, String biome, Unlock unlock, String description) {
            this.id=id; this.title=title; this.x=x; this.z=z; this.radius=radius;
            this.icon=icon; this.biome=biome; this.unlock=unlock; this.description=description;
        }
        public boolean contains(double px, double pz) { return Math.abs(px-x) <= radius+12 && Math.abs(pz-z) <= radius+12; }
    }
    public record Cell(int x, int y, int z) { }
    public record Node(Cell cell, Material material, Zone zone, int cooldownSeconds) { }
    public static Material depleted(Node node) {
        return switch(node.zone()) {
            case GROVE -> Material.STRIPPED_OAK_LOG;
            case DIGSITE -> Material.ROOTED_DIRT;
            case CINDER -> Material.POLISHED_BASALT;
            case ASTRAL -> Material.PURPUR_BLOCK;
            default -> Material.COBBLESTONE;
        };
    }
    public static final Map<Cell, Node> NODES = nodes();
    public static Optional<Zone> zoneAt(double x, double z) {
        return Arrays.stream(Zone.values()).filter(zone -> zone.contains(x,z)).findFirst();
    }
    public static Optional<Zone> named(String id) {
        return Arrays.stream(Zone.values()).filter(zone -> zone.id.equalsIgnoreCase(id)).findFirst();
    }
    private static Map<Cell, Node> nodes() {
        Map<Cell, Node> nodes = new LinkedHashMap<>();
        for (Zone zone : Zone.values()) {
            if (zone == Zone.CITY || zone == Zone.HARBOR) continue;
            Material[] materials = switch (zone) {
                case QUARRY -> new Material[]{Material.STONE, Material.COAL_ORE, Material.COPPER_ORE, Material.IRON_ORE};
                case GROVE -> new Material[]{Material.OAK_LOG, Material.BIRCH_LOG, Material.SPRUCE_LOG, Material.JUNGLE_LOG};
                case DIGSITE -> new Material[]{Material.SAND, Material.GRAVEL, Material.CLAY, Material.RED_SAND};
                case CINDER -> new Material[]{Material.NETHERRACK, Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE, Material.GLOWSTONE};
                case ASTRAL -> new Material[]{Material.END_STONE, Material.AMETHYST_BLOCK, Material.REDSTONE_ORE, Material.DIAMOND_ORE};
                default -> throw new IllegalStateException();
            };
            int[][] clusters={{-14,-11},{13,-12},{-14,8},{14,8}};
            int[][] offsets={{-6,-3},{0,-5},{6,-2},{-7,3},{-1,1},{5,4},{0,7}};
            for (int row=0; row<4; row++) for (int col=0; col<7; col++) {
                int x=zone.x+clusters[row][0]+offsets[col][0], z=zone.z+clusters[row][1]+offsets[col][1];
                Material material=materials[Math.floorMod(row+col, materials.length)];
                int height=zone==Zone.GROVE ? 3 : 1;
                for (int h=1; h<=height; h++) {
                    Cell cell=new Cell(x,SURFACE+h,z);
                    int seconds=zone==Zone.ASTRAL ? 240 : zone==Zone.CINDER ? 120 : zone==Zone.GROVE ? 75 : 45;
                    nodes.put(cell,new Node(cell,material,zone,seconds));
                }
            }
        }
        return Collections.unmodifiableMap(nodes);
    }
    private ExpeditionLayout() { }
}

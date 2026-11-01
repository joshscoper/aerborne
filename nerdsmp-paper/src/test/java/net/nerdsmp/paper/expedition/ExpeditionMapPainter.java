package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import org.bukkit.Material;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static net.nerdsmp.paper.expedition.ExpeditionLayout.Cell;

/**
 * Draws a dungeon's map for the website from its real template, so a map can't drift from the place
 * it shows. It walks the floor from everywhere a party has to go, colours the boss rooms and paints
 * the plan onto parchment; the page puts the names and medallions on top. Hidden caches are left off.
 */
final class ExpeditionMapPainter {
    /** Where things are, as percentages of the sheet. */
    record Pin(String name, String room, String icon, String color, boolean optional, double x, double y) { }
    record Place(String name, String kind, double x, double y) { }

    private static final int MAX_SIDE = 1400, PADDING = 12;
    private static final double MOST_PIXELS_PER_BLOCK = 7, TALLEST_SHEET = 1.35;
    private static final int[][] ZONES = {{198, 86, 66}, {226, 160, 48}, {150, 104, 182}, {84, 130, 168}, {88, 150, 96}, {196, 96, 140}};

    private final ExpeditionDefinition expedition;
    private final Map<Cell, Material> blocks;
    private final Map<String, Map<String, String>> mobs;
    private final Set<Long> floor = new HashSet<>();
    /** Open water nobody is meant to stand in: anything a fall would catch them from. */
    private final Set<Long> lake = new HashSet<>();
    private final Set<Cell> walked = new HashSet<>();
    private int minX, minZ, maxX, maxZ;
    private double scale;
    private final List<Pin> pins = new ArrayList<>();
    private final List<Place> places = new ArrayList<>();

    ExpeditionMapPainter(ExpeditionDefinition expedition, Map<Cell, Material> plan, Map<String, Map<String, String>> mobs) {
        this.expedition = expedition;
        this.blocks = new HashMap<>(plan);
        this.mobs = mobs;
        open();
        walk();
        drown();
        bounds();
        mark();
    }

    private static long key(int x, int z) { return ((long) x << 32) | (z & 0xffffffffL); }

    /** The map as a party finishes it: gates up, spans down, walls through. */
    private void open() {
        for (var piece : expedition.story().setpieces().values()) fill(piece.box(), Material.matchMaterial(piece.block()));
        for (var room : expedition.roomList()) if (room.gate() != null) fill(room.gate(), Material.AIR);
    }

    private void fill(ExpeditionDefinition.Box box, Material material) {
        for (int x = box.minX(); x <= box.maxX(); x++) for (int y = box.minY(); y <= box.maxY(); y++) for (int z = box.minZ(); z <= box.maxZ(); z++) {
            if (material == null || material == Material.AIR) blocks.remove(new Cell(x, y, z));
            else blocks.put(new Cell(x, y, z), material);
        }
    }

    private static boolean open(Material material) {
        if (material == null) return true;
        String name = material.name();
        return material == Material.AIR || material == Material.WATER || material == Material.FIRE || material == Material.COBWEB || material == Material.LEVER
            || material == Material.LADDER || material == Material.IRON_BARS || material == Material.IRON_CHAIN || material == Material.HANGING_ROOTS || material == Material.VINE
            || material == Material.FERN || material == Material.POPPY || material == Material.DANDELION || material == Material.CORNFLOWER || material == Material.OXEYE_DAISY
            || name.endsWith("CARPET") || name.endsWith("LANTERN") || name.endsWith("CANDLE") || name.endsWith("MUSHROOM") || name.endsWith("BUSH") || name.endsWith("BUTTON")
            || name.endsWith("TORCH") || name.endsWith("SIGN") || name.endsWith("PRESSURE_PLATE") || name.endsWith("RAIL") || name.endsWith("GRASS") || name.contains("CAVE_VINES")
            || name.endsWith("CAMPFIRE") || name.endsWith("SKULL") || name.endsWith("BANNER");
    }

    private boolean belowTheFalls(int y) {
        var falls = expedition.story().falls();
        return falls != null && y < falls.below();
    }

    /** The water, and anything built out over it, such as a ship's cabins, which nobody walks on but which aren't lake. */
    private void drown() {
        for (var entry : blocks.entrySet())
            if (entry.getValue() == Material.WATER && belowTheFalls(entry.getKey().y())) lake.add(key(entry.getKey().x(), entry.getKey().z()));
        var falls = expedition.story().falls();
        if (falls == null) return;
        for (var entry : blocks.entrySet()) {
            var at = entry.getKey();
            if (at.y() >= falls.below() && at.y() <= falls.below() + 8 && lake.contains(key(at.x(), at.z())) && !open(entry.getValue())) floor.add(key(at.x(), at.z()));
        }
    }

    private boolean stands(Cell at) {
        if (belowTheFalls(at.y())) return false;
        Material below = blocks.get(new Cell(at.x(), at.y() - 1, at.z()));
        boolean ground = below == Material.WATER || !open(below) && !below.name().endsWith("FENCE") && !below.name().endsWith("WALL");
        return ground && open(blocks.get(at)) && open(blocks.get(new Cell(at.x(), at.y() + 1, at.z())));
    }

    /** Fills the floor on foot from everywhere a party has to go. No jumps, so hidden places stay hidden. */
    private void walk() {
        List<ExpeditionDefinition.Point> seeds = new ArrayList<>(List.of(expedition.spawn()));
        if (expedition.exit() != null) seeds.add(expedition.exit());
        expedition.story().packs().values().forEach(pack -> seeds.add(pack.at()));
        expedition.story().campfires().values().forEach(fire -> seeds.add(fire.at()));
        for (var chest : expedition.story().chests().values()) if (chest.kind() != ExpeditionDefinition.ChestKind.SECRET) seeds.add(chest.at());
        for (var room : expedition.roomList()) {
            seeds.addAll(room.points());
            if (room.checkpoint() != null) seeds.add(room.checkpoint());
            for (var puzzle : room.puzzles().values()) {
                if (puzzle.start() != null) seeds.add(puzzle.start());
                seeds.addAll(puzzle.blocks());
            }
        }
        ArrayDeque<Cell> open = new ArrayDeque<>();
        for (var seed : seeds) for (int dy = 0; dy <= 3; dy++) for (int sign : new int[]{1, -1}) {
            Cell at = new Cell(seed.x(), seed.y() + dy * sign, seed.z());
            if (stands(at)) open.add(at);
        }
        while (!open.isEmpty()) {
            Cell at = open.poll();
            if (!walked.add(at)) continue;
            floor.add(key(at.x(), at.z()));
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) for (int reach = 1; reach <= (course(at) ? 4 : 1); reach++) for (int dy = -2; dy <= 1; dy++) {
                Cell next = new Cell(at.x() + step[0] * reach, at.y() + dy, at.z() + step[1] * reach);
                if (stands(next)) open.add(next);
            }
        }
    }

    /** On a jump course the way on really is a jump. Everywhere else a jump leads somewhere hidden. */
    private boolean course(Cell at) {
        for (var room : expedition.roomList())
            if (room.region() != null && room.region().contains(at.x() + .5, at.y(), at.z() + .5)
                && room.puzzles().values().stream().anyMatch(puzzle -> puzzle.type() == ExpeditionDefinition.PuzzleType.REACH)) return true;
        return false;
    }

    private void bounds() {
        minX = minZ = Integer.MAX_VALUE;
        maxX = maxZ = Integer.MIN_VALUE;
        Set<Long> drawn = new HashSet<>(floor);
        drawn.addAll(lake);
        for (long cell : drawn) {
            int x = (int) (cell >> 32), z = (int) cell;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        minX -= PADDING;
        minZ -= PADDING;
        maxX += PADDING;
        maxZ += PADDING;
        // A long, thin dungeon gets a wider sheet, or it would run off down the page.
        int missing = (int) Math.ceil((maxZ - minZ + 1) / TALLEST_SHEET) - (maxX - minX + 1);
        if (missing > 0) {
            minX -= missing / 2;
            maxX += missing - missing / 2;
        }
        scale = Math.min(MOST_PIXELS_PER_BLOCK, MAX_SIDE / (double) Math.max(maxX - minX + 1, maxZ - minZ + 1));
    }

    private double percentX(double x) { return Math.round((x + .5 - minX) / (maxX - minX + 1) * 1000) / 10.0; }

    private double percentZ(double z) { return Math.round((z + .5 - minZ) / (maxZ - minZ + 1) * 1000) / 10.0; }

    /** The middle of a box's floor, or null if none of it is floor. */
    private double[] middle(ExpeditionDefinition.Box box) {
        double x = 0, z = 0;
        int count = 0;
        for (long cell : floor) {
            int cx = (int) (cell >> 32), cz = (int) cell;
            if (cx < box.minX() || cx > box.maxX() || cz < box.minZ() || cz > box.maxZ()) continue;
            x += cx;
            z += cz;
            count++;
        }
        return count == 0 ? null : new double[]{x / count, z / count};
    }

    private String mobName(String id, String fallback) {
        var mob = mobs.get(id.replace("custom:", ""));
        return mob == null || mob.get("name") == null ? fallback : mob.get("name").replaceAll("<[^>]+>", "");
    }

    private String icon(String id) {
        var mob = mobs.get(id.replace("custom:", ""));
        return switch (mob == null ? "" : mob.getOrDefault("type", "")) {
            case "skeleton", "wither_skeleton", "stray", "husk", "zombie" -> "skull";
            case "bogged", "pillager" -> "bow";
            case "spider", "cave_spider" -> "spider";
            case "evoker", "witch", "vindicator" -> "staff";
            case "blaze", "magma_cube", "piglin_brute", "hoglin" -> "flame";
            case "drowned" -> "wave";
            case "ravager", "iron_golem" -> "shield";
            default -> "crown";
        };
    }

    private void mark() {
        int zone = 0;
        Set<String> placed = new TreeSet<>();
        for (var room : expedition.roomList()) {
            double[] middle = room.region() == null ? null : middle(room.region());
            if (room.bossRoom()) {
                var encounter = room.encounter();
                List<String> ids = encounter.bosses() != null && !encounter.bosses().isEmpty() ? encounter.bosses() : encounter.boss() == null ? List.of() : List.of(encounter.boss());
                // A pair reads as a pair, but a boss with a court is named on its own.
                List<String> names = ids.stream().map(id -> mobName(id, room.title())).distinct().toList();
                String name = names.isEmpty() ? room.title() : names.size() == 2 ? String.join(" & ", names) : names.getFirst();
                double x = room.points().isEmpty() ? middle == null ? 0 : middle[0] : room.points().getFirst().x(), z = room.points().isEmpty() ? middle == null ? 0 : middle[1] : room.points().getFirst().z();
                int[] color = ZONES[zone++ % ZONES.length];
                pins.add(new Pin(name, room.title().equals(name) ? "" : room.title(), ids.isEmpty() ? "crown" : icon(ids.getFirst()), String.format(Locale.ROOT, "#%02x%02x%02x", color[0], color[1], color[2]),
                    room.optional(), percentX(x), percentZ(z)));
            } else if (middle != null) {
                places.add(new Place(room.title(), "room", percentX(middle[0]), percentZ(middle[1])));
                placed.add(room.title());
            }
        }
        for (var fire : expedition.story().campfires().values()) {
            boolean atTheDoor = Math.hypot(fire.at().x() - expedition.spawn().x(), fire.at().z() - expedition.spawn().z()) < 9;
            if (!atTheDoor && placed.add(fire.title()) && floor.contains(key(fire.at().x(), fire.at().z()))) places.add(new Place(fire.title(), "fire", percentX(fire.at().x()), percentZ(fire.at().z())));
        }
    }

    private int width() { return (int) Math.round((maxX - minX + 1) * scale); }

    private int height() { return (int) Math.round((maxZ - minZ + 1) * scale); }

    private String floorHash() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (long cell : new TreeSet<>(floor)) digest.update(Long.toString(cell).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            for (var room : expedition.roomList()) if (room.bossRoom() && room.region() != null) digest.update(room.region().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String quote(String text) { return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"'; }

    /** What the page needs to lay names over the image. */
    String json() {
        StringBuilder out = new StringBuilder("{\n  \"version\": ").append(quote(floorHash()))
            .append(",\n  \"width\": ").append(width()).append(",\n  \"height\": ").append(height())
            .append(",\n  \"entrance\": {\"x\": ").append(percentX(expedition.spawn().x())).append(", \"y\": ").append(percentZ(expedition.spawn().z())).append("},\n  \"pins\": [");
        for (int index = 0; index < pins.size(); index++) {
            Pin pin = pins.get(index);
            out.append(index == 0 ? "\n" : ",\n").append("    {\"name\": ").append(quote(pin.name())).append(", \"room\": ").append(quote(pin.room())).append(", \"icon\": ").append(quote(pin.icon()))
                .append(", \"color\": ").append(quote(pin.color())).append(", \"optional\": ").append(pin.optional()).append(", \"x\": ").append(pin.x()).append(", \"y\": ").append(pin.y()).append('}');
        }
        out.append("\n  ],\n  \"places\": [");
        for (int index = 0; index < places.size(); index++) {
            Place place = places.get(index);
            out.append(index == 0 ? "\n" : ",\n").append("    {\"name\": ").append(quote(place.name())).append(", \"kind\": ").append(quote(place.kind())).append(", \"x\": ").append(place.x()).append(", \"y\": ").append(place.y()).append('}');
        }
        return out.append("\n  ]\n}\n").toString();
    }

    List<Pin> pins() { return List.copyOf(pins); }

    List<Place> places() { return List.copyOf(places); }

    int floorCells() { return floor.size(); }

    boolean floorAt(int x, int z) { return floor.contains(key(x, z)); }

    // ---------------------------------------------------------------------------------------------
    // Painting
    // ---------------------------------------------------------------------------------------------

    private static float[] blur(float[] field, int width, int height, int radius) {
        float[] from = field.clone(), to = new float[field.length];
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < height; y++) {
                float sum = 0;
                for (int x = -radius; x <= radius; x++) sum += from[y * width + Math.clamp(x, 0, width - 1)];
                for (int x = 0; x < width; x++) {
                    to[y * width + x] = sum / (radius * 2 + 1);
                    sum += from[y * width + Math.min(width - 1, x + radius + 1)] - from[y * width + Math.max(0, x - radius)];
                }
            }
            for (int x = 0; x < width; x++) {
                float sum = 0;
                for (int y = -radius; y <= radius; y++) sum += to[Math.clamp(y, 0, height - 1) * width + x];
                for (int y = 0; y < height; y++) {
                    from[y * width + x] = sum / (radius * 2 + 1);
                    sum += to[Math.min(height - 1, y + radius + 1) * width + x] - to[Math.max(0, y - radius) * width + x];
                }
            }
        }
        return from;
    }

    private static double mix(double from, double to, double share) { return from + (to - from) * Math.clamp(share, 0D, 1D); }

    private static double paper(int x, int y) {
        return SampleKit.smooth(x, y, 140, 3) * .5 + SampleKit.smooth(x, y, 48, 5) * .3 + SampleKit.smooth(x, y, 13, 7) * .15 + SampleKit.smooth(x, y, 4, 9) * .05;
    }

    byte[] paint() throws IOException {
        int width = width(), height = height(), block = (int) Math.ceil(scale);
        float[] mask = new float[width * height];
        int[] zones = new int[width * height];
        int zone = 0;
        List<ExpeditionDefinition.Box> regions = new ArrayList<>();
        for (var room : expedition.roomList()) if (room.bossRoom()) regions.add(room.region());
        for (int py = 0; py < height; py++) for (int px = 0; px < width; px++) {
            int x = minX + (int) (px / scale), z = minZ + (int) (py / scale);
            // Narrow bridges would vanish in the blur, so the floor is drawn a little fat.
            boolean walked = floor.contains(key(x, z)) || floor.contains(key(x + 1, z)) || floor.contains(key(x, z + 1));
            mask[py * width + px] = walked || lake.contains(key(x, z)) ? 1 : 0;
            zones[py * width + px] = -1;
            for (int index = 0; index < regions.size(); index++) {
                var region = regions.get(index);
                if (region != null && walked && x >= region.minX() && x <= region.maxX() && z >= region.minZ() && z <= region.maxZ()) zones[py * width + px] = index;
            }
        }
        float[] field = blur(mask, width, height, Math.max(2, (int) (block * .55))), wall = blur(mask, width, height, Math.max(3, (int) (block * 1.15)));
        float[] zoneField = new float[width * height];
        for (int index = 0; index < zoneField.length; index++) zoneField[index] = zones[index] >= 0 ? 1 : 0;
        zoneField = blur(zoneField, width, height, Math.max(2, block));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int py = 0; py < height; py++) for (int px = 0; px < width; px++) {
            int at = py * width + px;
            double grain = paper(px, py), edge = Math.min(Math.min(px, py), Math.min(width - 1 - px, height - 1 - py)) + SampleKit.smooth(px, py, 30, 11) * 26;
            double burn = Math.clamp(1 - edge / 70, 0D, 1D), value = .82 + grain * .3 - burn * burn * .5;
            double r = 196 * value, g = 162 * value, b = 104 * value;
            if (edge < 9) {
                r = 44;
                g = 30;
                b = 18;
            }
            double f = field[at], w = wall[at];
            if (w > .035 && f <= .5) {
                // The wall band: a hard outer line, then darker towards the floor.
                double depth = Math.clamp((w - .035) / .3, 0D, 1D), shade = .8 + grain * .3;
                r = mix(150 * shade, 106 * shade, depth);
                g = mix(116 * shade, 76 * shade, depth);
                b = mix(66 * shade, 38 * shade, depth);
                if (w < .05) {
                    r = 88;
                    g = 62;
                    b = 32;
                }
            }
            if (f > .5) {
                double inside = Math.clamp((f - .5) * 3.5, 0D, 1D), light = .9 + grain * .17;
                double fr = 234 * light, fg = 214 * light, fb = 164 * light;
                int cellX = minX + (int) (px / scale), cellZ = minZ + (int) (py / scale);
                if (lake.contains(key(cellX, cellZ)) && !floor.contains(key(cellX, cellZ))) {
                    double swell = .86 + SampleKit.smooth(px, py, 26, 13) * .3;
                    fr = 74 * swell;
                    fg = 108 * swell;
                    fb = 132 * swell;
                }
                int index = zones[at];
                if (index >= 0 || zoneField[at] > .1) {
                    int[] color = ZONES[Math.max(index, nearestZone(zones, at, width, height)) % ZONES.length];
                    double share = Math.clamp(zoneField[at] * 1.1, 0D, 1D) * .62;
                    fr = mix(fr, color[0], share);
                    fg = mix(fg, color[1], share);
                    fb = mix(fb, color[2], share);
                }
                r = mix(fr * .8, fr, inside);
                g = mix(fg * .8, fg, inside);
                b = mix(fb * .8, fb, inside);
            }
            if (Math.abs(f - .5) < .05) {
                r = 72;
                g = 50;
                b = 26;
            }
            image.setRGB(px, py, ((int) Math.clamp(r, 0, 255) << 16) | ((int) Math.clamp(g, 0, 255) << 8) | (int) Math.clamp(b, 0, 255));
        }
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var settings = writer.getDefaultWriteParam();
        settings.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        settings.setCompressionQuality(.82F);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), settings);
        }
        writer.dispose();
        return out.toByteArray();
    }

    /** At a zone's soft edge, the zone it belongs to. */
    private static int nearestZone(int[] zones, int at, int width, int height) {
        int x = at % width, y = at / width;
        for (int reach = 2; reach <= 32; reach += 2) for (int[] step : new int[][]{{reach, 0}, {-reach, 0}, {0, reach}, {0, -reach}}) {
            int nx = x + step[0], ny = y + step[1];
            if (nx >= 0 && ny >= 0 && nx < width && ny < height && zones[ny * width + nx] >= 0) return zones[ny * width + nx];
        }
        return 0;
    }
}

package net.nerdsmp.paper.reach;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.nerdsmp.paper.worldpack.WorldPack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Exports the Wardens' Reach for the website's interactive map: the relief atlas as an image, and every named place,
 * zone, townsperson, flight roost, dig site, rare beast and expedition door as JSON in world coordinates. Everything is
 * planned from the generator alone, so no server or world is needed. Set REACH_MAP_DIR to a folder to run it:
 * it writes reach.png and reach.json there (REACH_MAP_SCALE sets blocks per pixel, 2 by default).
 */
class ReachMapExport {
    @Test
    void exportWhenAsked() throws Exception {
        String target = System.getenv("REACH_MAP_DIR");
        if (target == null || target.isBlank()) return;
        File dir = new File(target);
        dir.mkdirs();
        int scale = Integer.parseInt(System.getenv().getOrDefault("REACH_MAP_SCALE", "2"));
        ReachTerrain terrain = new ReachTerrain();
        TERRAIN = terrain;
        ReachFeatures features = new ReachFeatures(terrain, ReachBuilds.all(terrain));
        Map<String, ReachFeatures.Spot> spots = features.spots();

        var image = ReachAtlas.render(terrain, ReachTerrain.MIN_X, ReachTerrain.MIN_Z, ReachTerrain.MAX_X, ReachTerrain.MAX_Z, scale);
        ImageIO.write(image, "png", new File(dir, "reach.png"));

        JsonObject root = new JsonObject();
        JsonObject bounds = new JsonObject();
        bounds.addProperty("minX", ReachTerrain.MIN_X);
        bounds.addProperty("minZ", ReachTerrain.MIN_Z);
        bounds.addProperty("maxX", ReachTerrain.MAX_X);
        bounds.addProperty("maxZ", ReachTerrain.MAX_Z);
        root.add("bounds", bounds);
        root.addProperty("width", image.getWidth());
        root.addProperty("height", image.getHeight());
        root.addProperty("revision", ReachGenerator.REVISION);

        // Regions: where each part of the Reach sits, from the middle of its land.
        Map<ReachTerrain.Zone, double[]> sums = new EnumMap<>(ReachTerrain.Zone.class);
        for (int x = ReachTerrain.MIN_X; x <= ReachTerrain.MAX_X; x += 16)
            for (int z = ReachTerrain.MIN_Z; z <= ReachTerrain.MAX_Z; z += 16) {
                if (!terrain.column(x, z).land()) continue;
                double[] s = sums.computeIfAbsent(terrain.zoneAt(x, z), ignored -> new double[3]);
                s[0] += x; s[1] += z; s[2]++;
            }
        JsonArray regions = new JsonArray();
        sums.forEach((zone, s) -> {
            if (zone == ReachTerrain.Zone.SKY || s[2] < 4) return;
            JsonObject row = point(s[0] / s[2], s[1] / s[2]);
            row.addProperty("id", zone.id());
            row.addProperty("title", zone.title());
            row.addProperty("levels", zone.levels());
            regions.add(row);
        });
        root.add("regions", regions);

        WorldPack pack = ReachPack.build("aerborne_reach", terrain, features);
        JsonArray zones = new JsonArray();
        pack.zones().values().forEach(zone -> {
            if (zone.id().startsWith("reach_dig_")) return;
            JsonObject row = new JsonObject();
            row.addProperty("id", zone.id());
            row.addProperty("title", zone.title());
            row.addProperty("levels", zone.levels());
            row.addProperty("safe", zone.safe());
            row.addProperty("x0", zone.bounds().minX());
            row.addProperty("z0", zone.bounds().minZ());
            row.addProperty("x1", zone.bounds().maxX());
            row.addProperty("z1", zone.bounds().maxZ());
            zones.add(row);
        });
        root.add("zones", zones);

        YamlConfiguration npcYaml = resource("content/reach-npcs.yml");
        YamlConfiguration questYaml = resource("content/reach-quests.yml");
        Set<String> givers = new HashSet<>();
        ConfigurationSection quests = questYaml.getConfigurationSection("quests");
        if (quests != null) for (String id : quests.getKeys(false)) {
            String giver = quests.getString(id + ".giver");
            if (giver != null) givers.add(giver);
        }
        JsonArray npcs = new JsonArray();
        Set<String> placed = new HashSet<>();
        pack.npcs().values().forEach(anchor -> {
            ConfigurationSection def = npcYaml.getConfigurationSection("npcs." + anchor.npc());
            if (def == null || !placed.add(anchor.npc())) return;
            JsonObject row = point(anchor.point().x(), anchor.point().z());
            row.addProperty("id", anchor.npc());
            row.addProperty("name", def.getString("name", anchor.npc()));
            row.addProperty("title", def.getString("title", ""));
            row.addProperty("quests", givers.contains(anchor.npc()));
            npcs.add(row);
        });
        root.add("npcs", npcs);

        JsonArray places = new JsonArray();
        for (ReachGameplay.Place place : ReachGameplay.PLACES) {
            ReachFeatures.Spot spot = spots.get(place.spot());
            if (spot == null) continue;
            JsonObject row = point(spot.x(), spot.z());
            row.addProperty("id", place.spot());
            row.addProperty("title", place.title());
            if (place.arrivalLine() != null) row.addProperty("line", place.arrivalLine());
            places.add(row);
        }
        root.add("places", places);

        JsonArray flights = new JsonArray();
        for (ReachFlights.Point roost : ReachFlights.POINTS) {
            for (String id : roost.spots().split("\\|")) {
                ReachFeatures.Spot spot = spots.get(id);
                if (spot == null) continue;
                JsonObject row = point(spot.x(), spot.z());
                row.addProperty("id", roost.id());
                row.addProperty("title", roost.name());
                flights.add(row);
                break;
            }
        }
        root.add("flights", flights);

        JsonArray digs = new JsonArray();
        for (ReachDigSites.Spec site : ReachDigSites.SITES) {
            ReachFeatures.Spot spot = spots.get(site.featureId() + ".dig");
            if (spot == null) continue;
            JsonObject row = point(spot.x(), spot.z());
            row.addProperty("id", site.id());
            row.addProperty("title", site.title());
            row.addProperty("ore", site.ore());
            digs.add(row);
        }
        root.add("digs", digs);

        YamlConfiguration mobs = resource("content/reach-mobs.yml");
        JsonArray beasts = new JsonArray();
        for (ReachGameplay.Encounter encounter : ReachGameplay.ENCOUNTERS) {
            ReachFeatures.Spot spot = spots.get(encounter.spot());
            if (spot == null) continue;
            JsonObject row = point(spot.x(), spot.z());
            row.addProperty("id", encounter.mob());
            row.addProperty("title", name(mobs, encounter.mob()));
            row.addProperty("night", encounter.nightOnly());
            row.addProperty("respawnMinutes", encounter.respawnSeconds() / 60);
            beasts.add(row);
        }
        ReachFeatures.Spot rings = spots.get("stone_rings.place");
        if (rings != null) {
            JsonObject boss = point(rings.x(), rings.z());
            boss.addProperty("id", "thunderhide");
            boss.addProperty("title", "Thunderhide");
            boss.addProperty("worldBoss", true);
            beasts.add(boss);
        }
        root.add("beasts", beasts);

        Map<String, String> titles = new HashMap<>();
        File expeditions = new File("src/main/resources/expeditions");
        File[] files = expeditions.listFiles((d, n) -> n.endsWith(".yml"));
        if (files != null) for (File file : files)
            titles.put(file.getName().replace(".yml", ""), YamlConfiguration.loadConfiguration(file).getString("title", file.getName()));
        JsonArray doors = new JsonArray();
        spots.forEach((id, spot) -> {
            String local = id.substring(id.indexOf('.') + 1);
            if (!local.startsWith("door_")) return;
            String expedition = local.substring("door_".length());
            JsonObject row = point(spot.x(), spot.z());
            row.addProperty("id", expedition);
            row.addProperty("title", titles.getOrDefault(expedition, expedition));
            doors.add(row);
        });
        root.add("doors", doors);

        Files.writeString(new File(dir, "reach.json").toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
    }

    private static ReachTerrain TERRAIN;

    private static JsonObject point(double x, double z) {
        JsonObject row = new JsonObject();
        if (TERRAIN != null) row.addProperty("region", TERRAIN.zoneAt(x, z).id());
        row.addProperty("x", Math.round(x));
        row.addProperty("z", Math.round(z));
        return row;
    }

    private static YamlConfiguration resource(String path) throws Exception {
        try (var reader = new InputStreamReader(ReachMapExport.class.getResourceAsStream("/" + path), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private static String name(YamlConfiguration mobs, String id) {
        for (String root : new String[] {"mobs.", ""}) {
            String raw = mobs.getString(root + id + ".name");
            if (raw != null) return raw.replaceAll("<[^>]*>", "");
        }
        return id;
    }
}

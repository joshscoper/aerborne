package net.nerdsmp.paper.worldpack;

import net.nerdsmp.paper.digsite.DigSiteDefinition;
import net.nerdsmp.paper.quest.QuestCatalog;
import net.nerdsmp.paper.quest.QuestDefinition;
import net.nerdsmp.paper.resource.ResourceMaterials;
import net.nerdsmp.paper.zone.ZoneDefinition;
import net.nerdsmp.paper.zone.ZoneMobTypes;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped Highcrown Isles content must hang together: every reference resolves before a server ever loads it. */
final class HighcrownContentTest {
    private static final UUID WORLD = UUID.fromString("eeed88d5-e3d0-4b13-a7d4-6cb36df3ae86");

    @Test
    void packZonesAreValidZoneDefinitions() throws IOException {
        WorldPack pack = pack();
        assertEquals("highcrown", pack.id());
        assertEquals(27594263L, pack.seed());
        assertEquals(3, pack.landmarks().size());
        assertTrue(pack.zones().size() <= ZoneDefinition.MAX_ZONES);
        int combat = 0;
        for (WorldPack.Zone zone : pack.zones().values()) {
            WorldPack.Box b = zone.bounds();
            List<ZoneDefinition.SpawnPoint> points = zone.points().stream()
                .map(p -> new ZoneDefinition.SpawnPoint(p.x(), p.y(), p.z(), p.yaw())).toList();
            List<ZoneDefinition.MobEntry> mobs = zone.mobs().stream()
                .map(m -> new ZoneDefinition.MobEntry(m.id(), m.weight(), m.level())).toList();
            // Throws for bounds that are too large, points outside the zone or invalid mob entries.
            new ZoneDefinition(zone.id(), zone.title(), WORLD, new ZoneDefinition.Bounds(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()),
                true, zone.safe(), zone.cap(), zone.respawnSeconds(), zone.radius(), zone.safe() ? List.of() : points, zone.safe() ? List.of() : mobs);
            if (!zone.safe()) {
                combat++;
                assertTrue(zone.points().size() >= 6, zone.id() + " needs enough spawn points");
                assertTrue(zone.levels().matches("\\d+-\\d+"), zone.id() + " states its level band");
            }
        }
        assertEquals(18, combat);
    }

    @Test
    void borderKeepsTheRawGeneratedLandOutOfReach() throws IOException {
        WorldPack.Border border = pack().border();
        assertTrue(border != null, "the map needs a border");
        // The southern continent starts near z 600 and generated islets lie north of z -560: both stay outside.
        assertTrue(border.maxZ() < 600 && border.minZ() > -560, "border reaches raw generated land: " + border);
        assertTrue(border.contains(777.5, -35.5), "spawn must be inside");
    }

    @Test
    void zoneCreaturesExistAndAreGroundSpawnable() throws IOException {
        Map<String, Map<String, Object>> mobs = mobs();
        for (WorldPack.Zone zone : pack().zones().values()) for (WorldPack.MobEntry entry : zone.mobs()) {
            assertTrue(ZoneMobTypes.validId(entry.id()), entry.id());
            EntityType type;
            if (entry.id().startsWith("custom:")) {
                Map<String, Object> mob = mobs.get(entry.id().substring(7));
                assertTrue(mob != null, zone.id() + " uses unknown mob " + entry.id());
                assertFalse(Boolean.TRUE.equals(mob.get("boss")), entry.id() + " is a boss and cannot be a zone creature");
                type = EntityType.valueOf(String.valueOf(mob.get("type")).toUpperCase(Locale.ROOT));
            } else type = EntityType.valueOf(entry.id().substring(8).toUpperCase(Locale.ROOT));
            assertTrue(ZoneMobTypes.allowed(type), entry.id() + " (" + type + ") cannot spawn in zones");
        }
    }

    @Test
    void npcAnchorsUseDefinedNpcsAndTheirShopsExist() throws IOException {
        Map<String, Object> npcs = new HashMap<>();
        npcs.putAll(section(yaml("/content/npcs.yml"), "npcs"));
        Map<String, Object> world = section(yaml("/content/world-npcs.yml"), "npcs");
        npcs.putAll(world);
        Set<String> placed = new HashSet<>();
        // Guard posts reuse one definition; everyone else is a single person.
        Set<String> repeatable = Set.of("highcrown_guard", "sandmere_sentry", "stone_sentinel");
        for (WorldPack.Anchor anchor : pack().npcs().values()) {
            assertTrue(npcs.containsKey(anchor.npc()), anchor.key() + " places unknown NPC " + anchor.npc());
            assertTrue(placed.add(anchor.npc()) || repeatable.contains(anchor.npc()), anchor.npc() + " is placed twice");
        }
        for (String id : world.keySet()) assertTrue(placed.contains(id), "World NPC " + id + " is never placed");
        assertTrue(pack().npcs().size() <= 100, "pack NPCs must fit the default npcs.maximum-instances");
    }

    @Test
    void nodeSitesUseRenewableMaterials() throws IOException {
        for (WorldPack.Site site : pack().sites().values()) {
            assertFalse(site.materials().isEmpty());
            for (String material : site.materials())
                assertTrue(ResourceMaterials.excavation(material) || ResourceMaterials.ore(material), site.id() + " uses " + material);
            WorldPack.Box b = site.bounds();
            long cells = (long) (b.maxX() - b.minX() + 1) * (b.maxY() - b.minY() + 1) * (b.maxZ() - b.minZ() + 1);
            assertTrue(cells <= 600_000, site.id() + " scans too large a volume at startup");
            assertTrue(site.cooldownSeconds() >= 5 && site.cooldownSeconds() <= 3600);
        }
        assertTrue(pack().sites().size() <= DigSiteDefinition.MAX_SITES);
    }

    @Test
    void questsReferenceRealNpcsZonesMobsAndNodes() throws IOException {
        QuestCatalog catalog = QuestCatalog.parse(text("/content/world-quests.yml"));
        WorldPack pack = pack();
        Set<String> npcs = new HashSet<>(section(yaml("/content/npcs.yml"), "npcs").keySet());
        npcs.addAll(section(yaml("/content/world-npcs.yml"), "npcs").keySet());
        Set<String> placed = new HashSet<>();
        pack.npcs().values().forEach(anchor -> placed.add(anchor.npc()));
        Map<String, Map<String, Object>> mobs = mobs();
        Set<String> spawnable = new HashSet<>();
        pack.zones().values().forEach(zone -> zone.mobs().forEach(mob -> spawnable.add(mob.id() + "@" + zone.id())));
        for (QuestDefinition quest : catalog.quests().values()) {
            for (String npc : List.of(quest.giver(), quest.turnIn())) {
                assertTrue(npcs.contains(npc), quest.id() + " uses unknown NPC " + npc);
                assertTrue(placed.contains(npc), quest.id() + " needs " + npc + " to be placed on the map");
            }
            for (QuestDefinition.Objective objective : quest.objectives()) {
                if (objective.zone() != null) assertTrue(pack.zones().containsKey(objective.zone()), quest.id() + " uses unknown zone " + objective.zone());
                switch (objective.type()) {
                    case KILL -> {
                        for (String target : objective.targets()) {
                            if (target.startsWith("custom:")) assertTrue(mobs.containsKey(target.substring(7)), quest.id() + " hunts unknown " + target);
                            boolean found = spawnable.stream().anyMatch(entry -> entry.startsWith(target + "@")
                                && (objective.zone() == null || entry.endsWith("@" + objective.zone())));
                            assertTrue(found, quest.id() + ": no zone spawns " + target + (objective.zone() == null ? "" : " in " + objective.zone()));
                        }
                    }
                    case TALK -> objective.targets().forEach(npc -> assertTrue(placed.contains(npc), quest.id() + " talks to unplaced " + npc));
                    case HARVEST, DIG -> {
                        boolean node = pack.sites().values().stream().anyMatch(site -> (objective.zone() == null || site.zone().equals(objective.zone()))
                            && (objective.targets().isEmpty() || site.materials().stream().anyMatch(objective.targets()::contains)));
                        assertTrue(node, quest.id() + " has no node site for " + objective.targets() + " in " + objective.zone());
                    }
                    default -> { }
                }
            }
        }
        assertTrue(catalog.quests().size() >= 30);
    }

    @Test
    void everyQuestGiverHasRoomForTheQuestReply() throws IOException {
        // The plugin adds the quest reply to a giver's greeting; a node shows at most five replies.
        QuestCatalog catalog = QuestCatalog.parse(text("/content/world-quests.yml"));
        Map<String, Object> npcs = new HashMap<>(section(yaml("/content/npcs.yml"), "npcs"));
        npcs.putAll(section(yaml("/content/world-npcs.yml"), "npcs"));
        Map<String, Object> dialogues = new HashMap<>(section(yaml("/content/npcs.yml"), "dialogues"));
        dialogues.putAll(section(yaml("/content/world-npcs.yml"), "dialogues"));
        for (QuestDefinition quest : catalog.quests().values()) for (String npc : new HashSet<>(List.of(quest.giver(), quest.turnIn()))) {
            @SuppressWarnings("unchecked") Map<String, Object> definition = (Map<String, Object>) npcs.get(npc);
            assertTrue(definition != null, npc + " gives quests but is not defined");
            @SuppressWarnings("unchecked") Map<String, Object> root = (Map<String, Object>) dialogues.get(String.valueOf(definition.get("root-dialogue")));
            @SuppressWarnings("unchecked") Map<String, Map<String, Object>> choices = (Map<String, Map<String, Object>>) root.get("choices");
            long others = choices.values().stream().filter(choice -> !String.valueOf(choice.get("effects")).contains("open_quests")).count();
            assertTrue(others <= 4, npc + "'s greeting has " + others + " replies; the quest reply needs a free slot");
        }
    }

    @Test
    void guardPatrolsAndTheNpcCapFit() throws IOException {
        WorldPack pack = pack();
        long guards = pack.npcs().values().stream().filter(anchor -> !anchor.route().isEmpty()).count();
        assertTrue(guards >= 10, "the towns need patrolling guards");
        assertTrue(pack.npcs().size() <= 100, "pack NPCs must fit the default npcs.maximum-instances");
        for (WorldPack.Anchor anchor : pack.npcs().values()) for (WorldPack.Point step : anchor.route()) {
            double distance = Math.hypot(step.x() + 0.5 - anchor.point().x(), step.z() + 0.5 - anchor.point().z());
            assertTrue(distance < 96, anchor.key() + " patrols too far from its post");
        }
    }

    @Test
    void warpsAndDigSiteZonesShareNamesForTravelLinks() throws IOException {
        WorldPack pack = pack();
        for (WorldPack.Site site : pack.sites().values())
            assertTrue(pack.warps().containsKey(site.zone()), site.zone() + " needs a warp so /digsite can link to it");
    }

    private static WorldPack pack() throws IOException {
        return WorldPack.parse(text("/worldpacks/highcrown.yml"));
    }

    private static Map<String, Map<String, Object>> mobs() throws IOException {
        Map<String, Map<String, Object>> all = new HashMap<>();
        for (String path : List.of("/content/mobs.yml", "/content/expedition-mobs.yml", "/content/world-mobs.yml"))
            section(yaml(path), "mobs").forEach((id, value) -> {
                @SuppressWarnings("unchecked") Map<String, Object> mob = (Map<String, Object>) value;
                all.put(id, mob);
            });
        return all;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String key) {
        return (Map<String, Object>) Objects.requireNonNull(root.get(key), key);
    }

    private static Map<String, Object> yaml(String path) throws IOException {
        return new Yaml().load(text(path));
    }

    private static String text(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(HighcrownContentTest.class.getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

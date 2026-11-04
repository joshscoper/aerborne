package net.nerdsmp.paper.reach;

import net.nerdsmp.paper.quest.QuestCatalog;
import net.nerdsmp.paper.quest.QuestDefinition;
import net.nerdsmp.paper.worldpack.WorldPack;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReachPackTest {
    private static final ReachTerrain TERRAIN = new ReachTerrain();
    private static final ReachFeatures FEATURES = new ReachFeatures(TERRAIN, ReachBuilds.all(TERRAIN));
    private static final WorldPack PACK = ReachPack.build("aerborne_reach", TERRAIN, FEATURES);

    private static String resource(String path) throws IOException {
        try (InputStream in = ReachPackTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> keys(String path, String section) throws IOException {
        Map<String, Object> root = new Yaml().load(resource(path));
        Object node = root.get(section);
        return node instanceof Map<?, ?> map ? new HashSet<>((Set<String>) map.keySet()) : new HashSet<>();
    }

    @Test
    void thePackHasTheVale() {
        assertEquals("aerborne_reach", PACK.world());
        for (String zone : List.of("reach_highcrown", "reach_millbrook", "reach_kingsgate_fields", "reach_goldwater_terraces",
            "reach_brindle_orchards", "reach_windmill_ridge", "reach_tollroad", "reach_black_granary", "reach_hollow_road", "reach_granary_cellars"))
            assertTrue(PACK.zones().containsKey(zone), "missing zone " + zone);
        for (WorldPack.Zone zone : PACK.zones().values()) {
            if (!zone.safe()) assertTrue(zone.points().size() >= 4, zone.id() + " has too few spawn points");
            // The zone store's own limits: 2,048 a side, 16,777,216 blocks, a cap of 1-64.
            WorldPack.Box b = zone.bounds();
            long volume = (long) (b.maxX() - b.minX() + 1) * (b.maxY() - b.minY() + 1) * (b.maxZ() - b.minZ() + 1);
            assertTrue(volume <= 16_777_216L && b.maxX() - b.minX() < 2048 && b.maxZ() - b.minZ() < 2048, zone.id() + " is too big: " + volume);
            assertTrue(zone.cap() >= 1 && zone.cap() <= 64, zone.id() + " cap " + zone.cap());
        }
        assertNotNull(PACK.spawn());
        assertTrue(PACK.warps().containsKey("reach"));
    }

    @Test
    void theNpcFileHasEverySectionTheLoaderNeeds() throws IOException {
        Map<String, Object> root = new Yaml().load(resource("content/reach-npcs.yml"));
        for (String section : List.of("shops", "npcs", "dialogues")) assertTrue(root.containsKey(section), "reach-npcs.yml needs a " + section + " section");
    }

    @Test
    void everyAnchoredNpcHasADefinition() throws IOException {
        Set<String> defined = new HashSet<>();
        for (String path : List.of("content/npcs.yml", "content/world-npcs.yml", "content/reach-npcs.yml")) defined.addAll(keys(path, "npcs"));
        Set<String> dialogues = new HashSet<>();
        for (String path : List.of("content/npcs.yml", "content/world-npcs.yml", "content/reach-npcs.yml")) dialogues.addAll(keys(path, "dialogues"));
        List<String> missing = new ArrayList<>();
        for (WorldPack.Anchor anchor : PACK.npcs().values()) if (!defined.contains(anchor.npc())) missing.add(anchor.key() + " -> " + anchor.npc());
        assertEquals(List.of(), missing);
        // Every Reach NPC's dialogue tree resolves.
        Map<String, Object> reach = new Yaml().load(resource("content/reach-npcs.yml"));
        @SuppressWarnings("unchecked") Map<String, Map<String, Object>> npcs = (Map<String, Map<String, Object>>) reach.get("npcs");
        for (var entry : npcs.entrySet()) assertTrue(dialogues.contains(String.valueOf(entry.getValue().get("root-dialogue"))), entry.getKey() + " root dialogue");
        @SuppressWarnings("unchecked") Map<String, Map<String, Object>> nodes = (Map<String, Map<String, Object>>) reach.get("dialogues");
        for (var node : nodes.entrySet()) {
            @SuppressWarnings("unchecked") Map<String, Map<String, Object>> choices = (Map<String, Map<String, Object>>) node.getValue().get("choices");
            if (choices == null) continue;
            for (var choice : choices.values()) if (choice.get("next") != null) assertTrue(dialogues.contains(String.valueOf(choice.get("next"))), node.getKey() + " -> " + choice.get("next"));
        }
    }

    @Test
    void zoneCreaturesAndEncountersExist() throws IOException {
        Set<String> mobs = new HashSet<>();
        for (String path : List.of("content/mobs.yml", "content/world-mobs.yml", "content/reach-mobs.yml", "content/expedition-mobs.yml")) mobs.addAll(keys(path, "mobs"));
        for (WorldPack.Zone zone : PACK.zones().values()) for (WorldPack.MobEntry mob : zone.mobs())
            assertTrue(mobs.contains(mob.id().substring("custom:".length())), zone.id() + " spawns unknown " + mob.id());
        for (String mob : ReachGameplay.encounterMobs()) assertTrue(mobs.contains(mob), "encounter " + mob);
        for (ReachGameplay.Encounter e : ReachGameplay.ENCOUNTERS) assertTrue(FEATURES.spots().containsKey(e.spot()), "encounter spot " + e.spot());
        for (ReachGameplay.Place p : ReachGameplay.PLACES) assertTrue(FEATURES.spots().containsKey(p.spot()), "place spot " + p.spot());
    }

    @Test
    void questsResolveAgainstThePack() throws IOException {
        QuestCatalog reach = QuestCatalog.parse(resource("content/reach-quests.yml"));
        assertFalse(reach.quests().isEmpty());
        Set<String> npcs = new HashSet<>();
        for (String path : List.of("content/npcs.yml", "content/world-npcs.yml", "content/reach-npcs.yml")) npcs.addAll(keys(path, "npcs"));
        Set<String> mobs = new HashSet<>();
        for (String path : List.of("content/mobs.yml", "content/world-mobs.yml", "content/reach-mobs.yml")) mobs.addAll(keys(path, "mobs"));
        Set<String> placed = new HashSet<>();
        PACK.npcs().values().forEach(a -> placed.add(a.npc()));
        List<String> problems = new ArrayList<>();
        for (QuestDefinition quest : reach.quests().values()) {
            for (String npc : List.of(quest.giver(), quest.turnIn())) if (!npcs.contains(npc) || !placed.contains(npc)) problems.add(quest.id() + " needs NPC " + npc);
            for (QuestDefinition.Objective o : quest.objectives()) {
                if (o.zone() != null && !PACK.zones().containsKey(o.zone())) problems.add(quest.id() + " uses zone " + o.zone());
                if (o.type() == QuestDefinition.Type.KILL) for (String t : o.targets()) if (!mobs.contains(t.substring(7))) problems.add(quest.id() + " hunts " + t);
                if (o.type() == QuestDefinition.Type.TALK) for (String t : o.targets()) if (!placed.contains(t)) problems.add(quest.id() + " talks to unplaced " + t);
                if (o.type() == QuestDefinition.Type.KILL && o.zone() != null) {
                    WorldPack.Zone zone = PACK.zones().get(o.zone());
                    boolean spawns = zone != null && zone.mobs().stream().anyMatch(m -> o.targets().contains(m.id()));
                    boolean encounter = o.targets().stream().anyMatch(t -> ReachGameplay.encounterMobs().contains(t.substring(7)));
                    if (!spawns && !encounter) problems.add(quest.id() + " hunts " + o.targets() + " where none spawn");
                }
            }
        }
        assertEquals(List.of(), problems);
    }
}

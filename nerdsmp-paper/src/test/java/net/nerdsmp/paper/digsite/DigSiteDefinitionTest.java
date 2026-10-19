package net.nerdsmp.paper.digsite;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.nerdsmp.paper.digsite.DigSiteDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class DigSiteDefinitionTest {
    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID GENERATION = UUID.randomUUID();

    private DigSiteDefinition site(String id, String zone, boolean enabled, int cooldown, List<Node> nodes) {
        return new DigSiteDefinition(id, zone, WORLD, GENERATION, enabled, cooldown, nodes);
    }

    private Node dirt(int x, int y, int z) {
        return new Node(x, y, z, "DIRT", "minecraft:dirt");
    }

    @Test void identifiersAreBoundedLowercaseAndCannotBecomeYamlPaths() {
        for (String id : List.of("a", "a_1-b", "a".repeat(48))) {
            assertTrue(validId(id));
            assertEquals(id, site(id, id, false, 20, List.of()).id());
        }
        for (String id : List.of("", "Upper", "a.b", "a b", "../a", "_a", "-a", "a".repeat(49), "é", "a\n")) {
            assertFalse(validId(id), id);
            assertThrows(IllegalArgumentException.class, () -> site(id, "quarry", false, 20, List.of()));
            assertThrows(IllegalArgumentException.class, () -> site("pit", id, false, 20, List.of()));
        }
        assertFalse(validId(null));
        assertThrows(IllegalArgumentException.class, () -> site(null, "quarry", false, 20, List.of()));
    }

    @Test void worldGenerationAndNodeListAreRequired() {
        assertThrows(NullPointerException.class, () -> new DigSiteDefinition("pit", "quarry", null, GENERATION, false, 20, List.of()));
        assertThrows(NullPointerException.class, () -> new DigSiteDefinition("pit", "quarry", WORLD, null, false, 20, List.of()));
        assertThrows(NullPointerException.class, () -> site("pit", "quarry", false, 20, null));
        ArrayList<Node> nodes = new ArrayList<>();
        nodes.add(null);
        assertThrows(NullPointerException.class, () -> site("pit", "quarry", false, 20, nodes));
    }

    @Test void cooldownAndEmptyEnabledSitesFailClosed() {
        assertEquals(5, site("pit", "quarry", false, 5, List.of()).cooldownSeconds());
        assertEquals(3_600, site("pit", "quarry", false, 3_600, List.of()).cooldownSeconds());
        for (int cooldown : new int[]{Integer.MIN_VALUE, 0, 4, 3_601, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> site("pit", "quarry", false, cooldown, List.of()));
        }
        assertThrows(IllegalArgumentException.class, () -> site("pit", "quarry", true, 20, List.of()));
        assertTrue(site("pit", "quarry", true, 20, List.of(dirt(0, 60, 0))).enabled());
        assertEquals(20, DEFAULT_COOLDOWN_SECONDS);
    }

    @Test void nodesAreImmutableBoundedAndUniqueByPositionNotMaterial() {
        List<Node> input = new ArrayList<>(List.of(dirt(0, 60, 0)));
        DigSiteDefinition definition = site("pit", "quarry", true, 20, input);
        input.clear();
        assertEquals(1, definition.nodes().size());
        assertThrows(UnsupportedOperationException.class, () -> definition.nodes().clear());
        assertThrows(IllegalArgumentException.class, () -> site("pit", "quarry", true, 20,
            List.of(dirt(0, 60, 0), new Node(0, 60, 0, "SAND", "minecraft:sand"))));
        List<Node> maximum = java.util.stream.IntStream.range(0, MAX_NODES)
            .mapToObj(x -> dirt(x, 60, 0)).toList();
        assertEquals(MAX_NODES, site("pit", "quarry", true, 20, maximum).nodes().size());
        ArrayList<Node> tooMany = new ArrayList<>(maximum);
        tooMany.add(dirt(MAX_NODES, 60, 0));
        assertThrows(IllegalArgumentException.class, () -> site("pit", "quarry", true, 20, tooMany));
    }

    @Test void coordinatesAreInclusiveWithinVanillaWorldLimits() {
        assertNotNull(dirt(-WORLD_COORDINATE_LIMIT, MIN_Y, -WORLD_COORDINATE_LIMIT));
        assertNotNull(dirt(WORLD_COORDINATE_LIMIT, MAX_Y, WORLD_COORDINATE_LIMIT));
        for (int coordinate : new int[]{-WORLD_COORDINATE_LIMIT - 1, WORLD_COORDINATE_LIMIT + 1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> dirt(coordinate, 60, 0));
            assertThrows(IllegalArgumentException.class, () -> dirt(0, 60, coordinate));
        }
        assertThrows(IllegalArgumentException.class, () -> dirt(0, MIN_Y - 1, 0));
        assertThrows(IllegalArgumentException.class, () -> dirt(0, MAX_Y + 1, 0));
    }

    @Test void onlyOrdinaryExcavationMaterialsCanBecomeRenewableNodes() {
        for (String material : List.of("DIRT", "COARSE_DIRT", "ROOTED_DIRT", "GRASS_BLOCK", "SAND",
            "RED_SAND", "GRAVEL", "CLAY", "MUD", "SOUL_SAND")) {
            assertNotNull(new Node(0, 60, 0, material, "minecraft:" + material.toLowerCase(java.util.Locale.ROOT)));
        }
        for (String material : List.of("SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL", "STONE", "DIAMOND_ORE",
            "AIR", "CHEST", "LAVA", "dirt", "minecraft:dirt", "DIRT ", "")) {
            assertThrows(IllegalArgumentException.class, () -> new Node(0, 60, 0, material, "minecraft:dirt"));
        }
        assertThrows(IllegalArgumentException.class, () -> new Node(0, 60, 0, null, "minecraft:dirt"));
    }

    @Test void blockDataMustUseTheExactMaterialPrefixAndSafePropertySyntax() {
        assertNotNull(new Node(0, 60, 0, "GRASS_BLOCK", "minecraft:grass_block[snowy=false]"));
        // Property registry validity is deliberately checked by Paper, not by the storage model.
        assertNotNull(new Node(0, 60, 0, "DIRT", "minecraft:dirt[a=true,b=1]"));
        for (String text : List.of("", "dirt", "custom:dirt", "minecraft:sand", "minecraft:dirt_extra",
            "minecraft:dirt[]", "minecraft:dirt[a=true,a=false]", "minecraft:dirt[a]",
            "minecraft:dirt[a=True]", "minecraft:dirt[A=true]", "minecraft:dirt[a=true,]",
            "minecraft:dirt[a=true][b=false]", "minecraft:dirt[a=true]suffix",
            "minecraft:dirt[a=true, b=false]", " minecraft:dirt", "minecraft:dirt\n",
            "minecraft:dirt[a=true\u0000]", "minecraft:dirt[a=" + "a".repeat(256) + "]")) {
            assertThrows(IllegalArgumentException.class, () -> new Node(0, 60, 0, "DIRT", text), text);
        }
        assertThrows(IllegalArgumentException.class, () -> new Node(0, 60, 0, "DIRT", null));
    }
}

package net.nerdsmp.paper.cosmetic;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The block-sculpture companions: the bundled models are valid and their animations pivot where they should. */
class SculptureTest {
    private static Map<String, SculptureModel> bundled() throws Exception {
        try (var reader = new InputStreamReader(SculptureTest.class.getResourceAsStream("/companion-models.yml"), StandardCharsets.UTF_8)) {
            return SculptureModel.read(YamlConfiguration.loadConfiguration(reader));
        }
    }

    @Test void everyCompanionHasAValidSculpture() throws Exception {
        Map<String, SculptureModel> models = bundled();
        for (var companion : CosmeticCatalog.defaults().companions()) {
            SculptureModel model = models.get(companion.model());
            assertNotNull(model, companion.slug() + " has a model");
            assertTrue(model.parts().size() >= 8 && model.parts().size() <= SculptureModel.MAX_PARTS, companion.slug() + " part count");
            for (var part : model.parts()) {
                Material material = Material.getMaterial(part.block());
                assertNotNull(material, companion.slug() + " uses a real block: " + part.block());
            }
            assertTrue(model.parts().stream().anyMatch(SculptureModel.Part::animated), companion.slug() + " has something that moves");
        }
    }

    @Test void sculpturesAreCompanionSized() throws Exception {
        for (var model : bundled().values()) {
            double top = model.parts().stream().mapToDouble(p -> p.y() + p.height() / 2).max().orElse(0);
            double bottom = model.parts().stream().mapToDouble(p -> p.y() - p.height() / 2).min().orElse(0);
            assertTrue(top <= 16, model.name() + " is under a block tall");
            assertTrue(bottom >= -0.01, model.name() + " stands on the ground");
        }
    }

    @Test void legsSwingInPairsOnlyWhileWalking() throws Exception {
        SculptureModel fox = bundled().get("kitekit");
        var legA = fox.parts().stream().filter(p -> p.group().equals("leg_a")).findFirst().orElseThrow();
        var legB = fox.parts().stream().filter(p -> p.group().equals("leg_b")).findFirst().orElseThrow();
        var still = SculptureMath.pose(fox, legA, 2, false, false, 1);
        assertEquals(0f, angle(still.rotation()), 1e-4f, "legs rest when standing");
        var a = SculptureMath.pose(fox, legA, 2, true, false, 1);
        var b = SculptureMath.pose(fox, legB, 2, true, false, 1);
        assertTrue(angle(a.rotation()) > 0.1f, "legs swing while walking");
        assertEquals(-a.rotation().x, b.rotation().x, 1e-5f, "the pairs swing opposite ways");
        // The hip stays attached: the top centre of the leg doesn't move as it swings.
        assertEquals(hip(fox, legA, still), hip(fox, legA, a), "hip");
    }

    @Test void wingsFlapOppositeAndFlamesKeepTheirBase() throws Exception {
        SculptureModel moth = bundled().get("moonmoth");
        var left = moth.parts().stream().filter(p -> p.group().equals("wing_left")).findFirst().orElseThrow();
        var right = moth.parts().stream().filter(p -> p.group().equals("wing_right")).findFirst().orElseThrow();
        var l = SculptureMath.pose(moth, left, 3, false, true, 1);
        var r = SculptureMath.pose(moth, right, 3, false, true, 1);
        assertEquals(-l.rotation().z, r.rotation().z, 1e-5f);
        SculptureModel ember = bundled().get("cindlet");
        var flame = ember.parts().stream().filter(p -> p.group().equals("flame")).findFirst().orElseThrow();
        for (long tick = 0; tick < 12; tick++) {
            var pose = SculptureMath.pose(ember, flame, tick, false, true, 1);
            assertEquals((flame.y() - flame.height() / 2) / 16.0, pose.translation().y, 1e-4, "flame base stays put while it flickers");
        }
    }

    @Test void scaleAndBounce() throws Exception {
        SculptureModel bunny = bundled().get("mossbun");
        var body = bunny.parts().getFirst();
        var normal = SculptureMath.pose(bunny, body, 0, false, false, 1);
        var big = SculptureMath.pose(bunny, body, 0, false, false, 2);
        assertEquals(normal.scale().x * 2, big.scale().x, 1e-5f);
        assertEquals(0.0D, SculptureMath.bounce("hop", 5, false, 1), "no hopping while standing");
        assertTrue(SculptureMath.bounce("hop", 5, true, 1) > SculptureMath.bounce("trot", 5, true, 1), "bunnies hop higher than foxes trot");
    }

    @Test void newBuiltInModelsFillGapsWithoutReplacingServerEdits() throws Exception {
        Map<String, SculptureModel> builtIn = bundled();
        var old = new YamlConfiguration();
        old.set("pufflet.parts", java.util.List.of(Map.of("block", "STONE", "at", java.util.List.of(0, 4, 0), "size", java.util.List.of(8, 8, 8))));
        var merged = SculptureModel.merge(builtIn, SculptureModel.read(old));
        assertEquals("STONE", merged.models().get("pufflet").parts().getFirst().block(), "the server's own edit is kept");
        assertTrue(merged.models().containsKey("lilypip"), "a companion added in an update still has a model");
        assertTrue(merged.added().contains("lilypip"));
        assertFalse(merged.added().contains("pufflet"));
    }

    @Test void mistakesInTheModelFileAreExplained() {
        var yaml = new YamlConfiguration();
        yaml.set("blob.parts", java.util.List.of(Map.of("block", "STONE", "at", java.util.List.of(0, 0, 0), "size", java.util.List.of(0, 1, 1))));
        var error = assertThrows(IllegalArgumentException.class, () -> SculptureModel.read(yaml));
        assertTrue(error.getMessage().contains("blob part 1"));
        var groups = new YamlConfiguration();
        groups.set("blob.parts", java.util.List.of(Map.of("block", "STONE", "at", java.util.List.of(0, 0, 0), "size", java.util.List.of(1, 1, 1), "group", "spin")));
        assertThrows(IllegalArgumentException.class, () -> SculptureModel.read(groups));
    }

    private static float angle(org.joml.Quaternionf q) {
        return (float) (2 * Math.acos(Math.min(1.0, Math.abs(q.w))));
    }

    /** Where the top centre of a part ends up, in blocks, for a pose. */
    private static Vector3f hip(SculptureModel model, SculptureModel.Part part, SculptureMath.Pose pose) {
        // The block's local top-centre is (0.5, 1, 0.5) before scaling; apply scale, rotation, then translation.
        Vector3f local = new Vector3f(0.5f, 1f, 0.5f).mul(pose.scale());
        pose.rotation().transform(local);
        Vector3f out = local.add(pose.translation());
        return new Vector3f(Math.round(out.x * 1000) / 1000f, Math.round(out.y * 1000) / 1000f, Math.round(out.z * 1000) / 1000f);
    }
}

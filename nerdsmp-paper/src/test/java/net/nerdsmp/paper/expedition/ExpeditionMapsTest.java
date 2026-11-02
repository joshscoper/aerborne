package net.nerdsmp.paper.expedition;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.expedition.content.ExpeditionLoader;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static net.nerdsmp.paper.expedition.ExpeditionLayout.Cell;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The website's dungeon maps are drawn from the templates, and these tests fail when one goes stale.
 * To redraw them, run this class with the environment variable WRITE_MAPS=1.
 */
class ExpeditionMapsTest {
    private static final Path FOLDER = Path.of("../Website/aerborne/static/img/maps");

    private static YamlConfiguration yaml(String path) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(ExpeditionMapsTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static ExpeditionMapPainter painter(String id) {
        ExpeditionDefinition expedition = ExpeditionLoader.parse(id, yaml("/expeditions/" + id + ".yml"));
        Map<Cell, Material> blocks = new HashMap<>();
        for (var placement : SampleTemplates.plan(id).placements()) blocks.put(placement.cell(), placement.block().material());
        Map<String, Map<String, String>> mobs = new HashMap<>();
        for (String file : new String[]{"/content/mobs.yml", "/content/expedition-mobs.yml"}) {
            var section = yaml(file).getConfigurationSection("mobs");
            for (String mob : section.getKeys(false))
                mobs.put(mob, Map.of("name", section.getString(mob + ".name", mob), "type", section.getString(mob + ".type", "")));
        }
        return new ExpeditionMapPainter(expedition, blocks, mobs);
    }

    @Test void everyDungeonHasAnUpToDateMap() throws IOException {
        boolean write = System.getenv("WRITE_MAPS") != null;
        if (write) Files.createDirectories(FOLDER);
        for (String id : ExpeditionService.SAMPLES) {
            ExpeditionMapPainter painter = painter(id);
            // Only a stale map is rewritten: a running website holds the rest open.
            boolean stale = !Files.isRegularFile(FOLDER.resolve(id + ".json")) || !Files.readString(FOLDER.resolve(id + ".json")).replace("\r\n", "\n").equals(painter.json());
            if (write && stale) {
                Files.write(FOLDER.resolve(id + ".jpg"), painter.paint());
                Files.writeString(FOLDER.resolve(id + ".json"), painter.json());
            }
            assertTrue(Files.isRegularFile(FOLDER.resolve(id + ".jpg")), id + " has no map: run this test with WRITE_MAPS=1");
            assertEquals(Files.readString(FOLDER.resolve(id + ".json")).replace("\r\n", "\n"), painter.json(), id + "'s map is out of date: run this test with WRITE_MAPS=1");
        }
    }

    @Test void everyBossIsOnItsMapAndEveryPinIsOnTheSheet() {
        for (String id : ExpeditionService.SAMPLES) {
            ExpeditionDefinition expedition = ExpeditionLoader.parse(id, yaml("/expeditions/" + id + ".yml"));
            ExpeditionMapPainter painter = painter(id);
            assertTrue(painter.floorCells() > 200, id + " has hardly any floor");
            assertEquals(expedition.publicRooms().stream().filter(ExpeditionDefinition.Room::bossRoom).count(), painter.pins().size(), id);
            assertTrue(painter.pins().stream().noneMatch(pin -> pin.name().contains("Colossus")), "a hidden boss stays off the map");
            for (var pin : painter.pins()) {
                assertTrue(pin.x() > 0 && pin.x() < 100 && pin.y() > 0 && pin.y() < 100, id + " " + pin);
                assertFalse(pin.name().contains("<"), id + " " + pin);
            }
            for (var place : painter.places()) assertTrue(place.x() > 0 && place.x() < 100 && place.y() > 0 && place.y() < 100, id + " " + place);
        }
    }

    @Test void bossesAreNamedRatherThanTheirRooms() {
        assertTrue(painter("hollow_barrow").pins().stream().anyMatch(pin -> pin.name().contains("Huntsman")));
        for (String id : ExpeditionService.SAMPLES) for (var pin : painter(id).pins()) assertTrue(pin.name().length() < 48, id + " " + pin.name());
    }

    /** A hidden cache is for players to find, so the way to one isn't on the map. */
    @Test void theCanopysHiddenHollowStaysOffItsMap() {
        ExpeditionMapPainter painter = painter("thornwood_canopy");
        assertFalse(painter.floorAt(-41, 26));
        assertFalse(painter.floorAt(-29, 28));
        assertTrue(painter.floorAt(0, 65));
    }
}

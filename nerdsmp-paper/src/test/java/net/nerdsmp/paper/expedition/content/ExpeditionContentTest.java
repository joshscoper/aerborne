package net.nerdsmp.paper.expedition.content;

import net.nerdsmp.paper.expedition.content.ExpeditionDefinition.EncounterType;
import net.nerdsmp.paper.expedition.content.ExpeditionValidator.Issue;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ExpeditionContentTest {
    /** Accepts everything except names containing "unknown", so tests can trip each lookup. */
    static final ExpeditionValidator.Catalog CATALOG = new ExpeditionValidator.Catalog() {
        @Override public boolean mob(String id) { return id.matches("(custom|vanilla):[a-z_]+") && !id.contains("unknown"); }
        @Override public boolean boss(String id) { return id.startsWith("custom:") && !id.contains("unknown"); }
        @Override public boolean lootProfile(String id) { return !id.contains("unknown"); }
        @Override public boolean item(String id) { return !id.contains("unknown"); }
        @Override public boolean effect(String key) { return !key.contains("unknown"); }
        @Override public boolean sound(String key) { return !key.contains("unknown"); }
        @Override public boolean material(String key) { return !key.contains("unknown"); }
        @Override public boolean biome(String key) { return !key.contains("unknown"); }
    };

    static YamlConfiguration resource(String path) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(ExpeditionContentTest.class.getResourceAsStream(path), path), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    static ExpeditionDefinition sample(String id) {
        return ExpeditionLoader.parse(id, resource("/expeditions/" + id + ".yml"));
    }

    private static List<String> errors(ExpeditionDefinition definition) {
        return ExpeditionValidator.validate(definition, Tier.DEFAULTS, CATALOG).stream().filter(Issue::error).map(Issue::message).toList();
    }

    @Test void shippedSamplesLoadAndAreReadyToPublish() {
        for (String id : net.nerdsmp.paper.expedition.ExpeditionService.SAMPLES) {
            ExpeditionDefinition definition = sample(id);
            assertTrue(definition.published(), id);
            assertEquals(id, definition.sample());
            assertEquals(List.of(), errors(definition), id);
            assertEquals(EncounterType.BOSS, definition.roomList().get(definition.rooms().size() - 1).encounter().type(), id);
            assertFalse(definition.guide().isEmpty(), id + " ships a guide");
            assertEquals(definition.publicRooms().stream().map(room -> room.id()).collect(java.util.stream.Collectors.toSet()), definition.guide().rooms().keySet(), id + ": a guide line for every room");
        }
    }

    @Test void theCampaignExpeditionsOpenOneAfterAnother() {
        List<String> path = List.of("hollow_barrow", "thornwood_canopy", "sunken_aqueduct", "gloomforge_mines", "hall_of_wardens");
        for (int index = 1; index < path.size(); index++)
            assertEquals(List.of(path.get(index - 1)), sample(path.get(index)).story().requires(), path.get(index));
        assertEquals(ExpeditionDefinition.Goal.FORCES, sample("thornwood_canopy").story().goal());
        assertEquals(ExpeditionDefinition.Goal.TIMED, sample("sunken_aqueduct").story().goal());
        assertEquals(ExpeditionDefinition.Goal.BOSSES, sample("hall_of_wardens").story().goal());
        assertEquals(6, sample("hall_of_wardens").roomList().stream().filter(ExpeditionDefinition.Room::bossRoom).count());
    }

    @Test void barrowRoomsWavesScriptsAndLootParse() {
        ExpeditionDefinition barrow = sample("hollow_barrow");
        assertEquals(List.of("lever_vault", "wardens_hall", "widows_cell", "kings_rest"), List.copyOf(barrow.rooms().keySet()));
        var vault = barrow.rooms().get("lever_vault");
        assertEquals(EncounterType.PUZZLE, vault.encounter().type());
        assertEquals(ExpeditionDefinition.Box.of(-13, 64, 66, -13, 67, 70), vault.gate());
        var warden = barrow.rooms().get("wardens_hall");
        assertEquals(List.of("lever_vault"), warden.requires());
        assertEquals(4, warden.requiresPacks().size());
        assertEquals(ExpeditionDefinition.Box.of(-52, 64, 66, -52, 67, 70), warden.seal());
        assertTrue(barrow.rooms().get("widows_cell").optional());
        assertEquals(ScriptAction.Verb.CUTSCENE, warden.encounter().script("start").get(0).verb());
        var walkers = barrow.story().packs().get("processional_walkers");
        assertEquals(Map.of("custom:bone_lurcher", 1, "custom:barrow_thrall", 2), walkers.mobs());
        assertEquals(2, walkers.patrol().size());
        assertEquals(7, walkers.aggro());
        assertEquals(ExpeditionDefinition.Pack.DEFAULT_LEASH, walkers.leash());
        assertEquals(ExpeditionDefinition.Behavior.CASTER, barrow.story().mob("custom:barrow_keener").behavior());
        assertEquals(1, barrow.story().recordsVersion());
        var boss = barrow.rooms().get("kings_rest").encounter();
        assertEquals("custom:the_hollow_huntsman", boss.boss());
        assertEquals(List.of(66, 33), boss.addsAtHealth());
        assertEquals(3, boss.script("health-50").size());
        assertEquals(120, barrow.loot().marks());
        assertEquals(6, barrow.loot().items().get("grave_dust"));
        assertEquals(new ExpeditionDefinition.Point(0, 64, 4, 0), barrow.spawn());
    }

    @Test void cinderKeepIsAnEightPlayerNetherRaidWithThreeTunedTiers() {
        ExpeditionDefinition keep = sample("cinder_keep");
        assertEquals(ExpeditionDefinition.Dimension.NETHER, keep.dimension());
        assertEquals("basalt_deltas", keep.biome());
        assertTrue(keep.story().raid());
        assertEquals(8, keep.maxParty());
        assertEquals(List.of("nether"), keep.rooms().get("kilnbreaker").unlocks());
        assertEquals(10, keep.rooms().get("cinderhounds").encounter().linkSeconds());
        assertEquals(30, keep.rooms().get("molten_heart").encounter().phases().getFirst().submergeSeconds());
        assertEquals(List.of("ember_gate", "chain_bridge", "kilnbreaker", "cinderhounds", "slag_priest", "bellows", "steward", "molten_heart"), List.copyOf(keep.rooms().keySet()));
        assertTrue(keep.rooms().get("molten_heart").loot().marks() > 0);
        Tier mythic = Tier.DEFAULTS.get("mythic").apply(keep.tiers().get("mythic"), keep.lives());
        assertEquals(3.0, mythic.health());
        assertEquals(List.of("fortified", "tyrannical"), mythic.modifiers());
        assertTrue(Tier.DEFAULTS.get("normal").apply(keep.tiers().get("normal"), keep.lives()).runBack());
        assertEquals(List.of(), errors(keep));
    }

    @Test void blankFileFromCreateLoadsAndListsTheMissingSteps() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("dimension", "end");
        ExpeditionDefinition fresh = ExpeditionLoader.parse("star_vault", yaml);
        assertEquals("Star Vault", fresh.title());
        assertEquals("nerdsmp_exp_tpl_star_vault", fresh.templateWorld());
        assertEquals(5, fresh.maxParty());
        List<String> errors = errors(fresh);
        assertTrue(errors.stream().anyMatch(message -> message.contains("spawn")), errors.toString());
        assertTrue(errors.stream().anyMatch(message -> message.contains("exit")), errors.toString());
        assertTrue(errors.stream().anyMatch(message -> message.contains("No rooms")), errors.toString());
        assertTrue(ExpeditionValidator.validate(fresh, Tier.DEFAULTS, CATALOG).stream().allMatch(issue -> !issue.fix().isBlank()));
    }

    @Test void structuralMistakesNameThePathToFix() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("rooms.hall.region", List.of(1, 2, 3, 4, 5));
        var failure = assertThrows(IllegalArgumentException.class, () -> ExpeditionLoader.parse("broken", yaml));
        assertTrue(failure.getMessage().contains("rooms.hall.region"), failure.getMessage());
        YamlConfiguration script = new YamlConfiguration();
        script.set("rooms.hall.encounter.scripts.start", List.of("explode everything"));
        failure = assertThrows(IllegalArgumentException.class, () -> ExpeditionLoader.parse("broken", script));
        assertTrue(failure.getMessage().contains("scripts.start[1]"), failure.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ExpeditionLoader.parse("Bad Id", new YamlConfiguration()));
    }

    @Test void validatorCatchesBrokenRoomsGatesAndReferences() {
        YamlConfiguration yaml = resource("/expeditions/hollow_barrow.yml");
        yaml.set("rooms.wardens_hall.points", List.of(List.of(40, 64, 20)));
        yaml.set("rooms.widows_cell.gate", List.of(-1, 64, 3, 1, 66, 5));
        yaml.set("rooms.widows_cell.encounter.scripts.clear", List.of("open nowhere"));
        yaml.set("rooms.kings_rest.requires-packs", List.of("nobody"));
        yaml.set("packs.door_guard.aggro", 40);
        yaml.set("packs.processional_walkers.patrol", List.of(List.of(0, 64, 50)));
        yaml.set("packs.far_aisle.mobs.custom:unknown_thing", 2);
        yaml.set("rooms.kings_rest.encounter.boss", "custom:unknown_king");
        yaml.set("loot.profile", "unknown_profile");
        yaml.set("party.min", 6);
        List<String> errors = errors(ExpeditionLoader.parse("hollow_barrow", yaml));
        for (String expected : List.of("outside the room", "covers the party spawn", "unknown room 'nowhere'", "not a known mob",
            "Loot profile", "Party size", "unknown pack 'nobody'", "aggro must be", "two patrol stops", "unknown mob 'custom:unknown_thing'"))
            assertTrue(errors.stream().anyMatch(message -> message.contains(expected)), expected + " in " + errors);
    }

    @Test void overlappingRoomsAndEncountersWithoutPointsAreErrors() {
        YamlConfiguration yaml = resource("/expeditions/hollow_barrow.yml");
        yaml.set("rooms.widows_cell.region", List.of(-18, 64, 100, 18, 78, 136));
        yaml.set("rooms.kings_rest.points", List.of());
        List<String> errors = errors(ExpeditionLoader.parse("hollow_barrow", yaml));
        assertTrue(errors.stream().anyMatch(message -> message.contains("overlaps")), errors.toString());
        assertTrue(errors.stream().anyMatch(message -> message.contains("no mob spawn points")), errors.toString());
    }

    @Test void scriptActionsParseAndRejectBadArguments() {
        assertEquals(List.of("custom:cinder_wraith", "2", "3"), ScriptAction.parse("spawn custom:cinder_wraith 2 3").args());
        assertEquals(List.of("<red>Hello there</red>"), ScriptAction.parse("message <red>Hello there</red>").args());
        assertEquals(ScriptAction.Verb.CHECKPOINT, ScriptAction.parse("checkpoint").verb());
        assertThrows(IllegalArgumentException.class, () -> ScriptAction.parse("wait forever"));
        assertThrows(IllegalArgumentException.class, () -> ScriptAction.parse("spawn custom:x 99"));
        assertThrows(IllegalArgumentException.class, () -> ScriptAction.parse("effect speed"));
        assertThrows(IllegalArgumentException.class, () -> ScriptAction.parse("teleport home"));
        assertTrue(ScriptAction.TRIGGER.matcher("health-50").matches());
        assertTrue(ScriptAction.TRIGGER.matcher("timer-120").matches());
        assertFalse(ScriptAction.TRIGGER.matcher("health-150").matches());
    }

    @Test void tiersLoadFromConfigAndApplyExpeditionOverrides() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("tiers.normal.lives", 8);
        config.set("tiers.heroic.requires", "normal");
        config.set("tiers.legend.level-bonus", 30);
        config.set("tiers.legend.requires", "heroic");
        var tiers = Tier.load(config.getConfigurationSection("tiers"));
        assertEquals(List.of("normal", "heroic", "legend"), List.copyOf(tiers.keySet()));
        assertEquals(8, tiers.get("normal").lives());
        assertEquals("Legend", tiers.get("legend").title());
        var override = new ExpeditionDefinition.TierOverride(null, 2, null, 4, List.of("volatile"));
        Tier heroic = Tier.DEFAULTS.get("heroic").apply(override, null);
        assertEquals(2, heroic.levelBonus());
        assertEquals(4, heroic.lootRolls());
        assertEquals(List.of("volatile"), heroic.modifiers());
        Tier tuned = Tier.DEFAULTS.get("mythic").apply(new ExpeditionDefinition.TierOverride(null, null, 12, null, List.of(), 3.4, 2.0), null);
        assertEquals(3.4, tuned.health());
        assertEquals(2.0, tuned.damage());
        assertEquals(12, tuned.lives());
        assertEquals(Tier.DEFAULTS.get("heroic").health(), heroic.health(), "Unset multipliers keep the global value");
        assertEquals(12, Tier.DEFAULTS.get("normal").apply(null, 12).lives());
        assertEquals(7, Tier.DEFAULTS.get("heroic").apply(null, 12).lives());
        YamlConfiguration broken = new YamlConfiguration();
        broken.set("tiers.hard.requires", "missing");
        assertThrows(IllegalArgumentException.class, () -> Tier.load(broken.getConfigurationSection("tiers")));
    }

    @Test void modifiersScaleTheRightEnemies() {
        Set<Modifier> active = Modifier.parseAll(List.of("fortified", "TYRANNICAL", "nonsense"));
        assertEquals(Set.of(Modifier.FORTIFIED, Modifier.TYRANNICAL), active);
        assertEquals(1.3, Modifier.healthMultiplier(Set.of(Modifier.FORTIFIED), false));
        assertEquals(1.0, Modifier.healthMultiplier(Set.of(Modifier.FORTIFIED), true));
        assertEquals(1.15, Modifier.damageMultiplier(Set.of(Modifier.TYRANNICAL), true));
        assertEquals(1080, Modifier.parSeconds(30));
        assertEquals(60, Modifier.parSeconds(1));
    }
}

package net.nerdsmp.paper.loot.profile;

import net.nerdsmp.api.model.ItemId;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.nerdsmp.paper.loot.profile.LootProfileDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class MobLootCommandTest {
    @TempDir Path directory;
    final Context context = new Context();
    final Actor staff = new Actor(true);
    LootProfileStore store() throws IOException { return new LootProfileStore(directory.resolve("mob-loot.yml").toFile(), Set.of(new ItemId("crafted_diamond_sword")), Set.of(new ItemId("crafted_diamond_sword")), MobLootCommandTest::itemMaterial); }
    private static boolean itemMaterial(Material material) { return material == Material.BONE || material == Material.DIAMOND; }
    void run(MobLootCommand command, Actor actor, String input) { assertTrue(command.command(actor.sender, input.split(" "))); }

    @Test void permissionsProtectAllMutationAndCatalogs() throws Exception {
        var store = store(); var command = new MobLootCommand(store, context); var player = new Actor(false);
        run(command, player, "create crypt_rewards"); run(command, player, "bindings");
        assertTrue(store.all().isEmpty()); assertTrue(command.complete(player.sender, new String[]{"assign", "mob", ""}).isEmpty());
        assertTrue(player.messages.stream().allMatch(message -> message.contains("permission")));
    }
    @Test void typedEntriesUsePercentagesAndRequireExplicitSetToModify() throws Exception {
        var store = store(); var command = new MobLootCommand(store, context);
        run(command, staff, "create crypt_rewards");
        run(command, staff, "add crypt_rewards bones material bone 25 1 4");
        run(command, staff, "add crypt_rewards coins marks marks 35 2 6");
        run(command, staff, "add crypt_rewards sword item crafted_diamond_sword 1 1 1 true 2");
        var profile = store.find("crypt_rewards").orElseThrow();
        assertEquals(3, profile.entries().size()); assertEquals(0.25, profile.entries().getFirst().chance());
        assertEquals("BONE", profile.entries().getFirst().source());
        run(command, staff, "add crypt_rewards bones material diamond 100 64 64");
        assertEquals(profile, store.find("crypt_rewards").orElseThrow());
        run(command, staff, "set crypt_rewards bones material bone 50 2 5");
        assertEquals(0.5, store.find("crypt_rewards").orElseThrow().entries().stream().filter(entry -> entry.id().equals("bones")).findFirst().orElseThrow().chance());
        run(command, staff, "remove crypt_rewards coins");
        assertEquals(2, store.find("crypt_rewards").orElseThrow().entries().size());
        assertEquals(store.all(), new LootProfileStore(directory.resolve("mob-loot.yml").toFile(), store.knownItems(), store.gearItems(), MobLootCommandTest::itemMaterial).all());
    }
    @Test void invalidValuesUnknownItemsAndUnexpectedFlagsNeverPublish() throws Exception {
        var store = store(); var command = new MobLootCommand(store, context); run(command, staff, "create crypt_rewards");
        for (String commandText : List.of(
            "add crypt_rewards invalid material unknown 100 1 1",
            "add crypt_rewards invalid material water 100 1 1",
            "add crypt_rewards invalid item missing_sword 100 1 1",
            "add crypt_rewards invalid material bone NaN 1 1",
            "add crypt_rewards invalid material bone 101 1 1",
            "add crypt_rewards invalid material bone 100 1 1 true 2",
            "add crypt_rewards invalid marks marks 100 1 10001",
            "add crypt_rewards invalid item crafted_diamond_sword 100 1 1 yes 1",
            "add crypt_rewards invalid material bone 100 0 1")) run(command, staff, commandText);
        assertTrue(store.find("crypt_rewards").orElseThrow().entries().isEmpty());
    }
    @Test void replacementAndAssignmentChangesRequireConfirmation() throws Exception {
        var store = store(); var command = new MobLootCommand(store, context);
        run(command, staff, "create crypt_rewards"); run(command, staff, "create alternate");
        run(command, staff, "mode crypt_rewards replace"); assertEquals(Mode.ADDITIVE, store.find("crypt_rewards").orElseThrow().mode());
        run(command, staff, "mode crypt_rewards replace confirm"); assertEquals(Mode.REPLACE, store.find("crypt_rewards").orElseThrow().mode());
        run(command, staff, "assign zone crypts crypt_rewards"); assertTrue(store.zoneAssignments().isEmpty());
        run(command, staff, "assign zone crypts crypt_rewards confirm");
        assertEquals("crypt_rewards", store.zoneAssignments().get("crypts"));
        run(command, staff, "assign zone crypts alternate"); assertEquals("crypt_rewards", store.zoneAssignments().get("crypts"));
        run(command, staff, "assign zone crypts alternate confirm"); assertEquals("alternate", store.zoneAssignments().get("crypts"));
    }
    @Test void zoneAssignmentOverridesCustomAndVanillaFallbacksWithoutRollingBoth() throws Exception {
        var store = store(); var command = new MobLootCommand(store, context);
        run(command, staff, "create global_rewards"); run(command, staff, "create crypt_rewards");
        run(command, staff, "assign mob custom:veil_stalker global_rewards");
        run(command, staff, "assign mob vanilla:zombie global_rewards");
        run(command, staff, "assign zone crypts crypt_rewards");
        assertEquals("global_rewards", store.mobAssignments().get("veil_stalker"));
        assertEquals("global_rewards", store.mobAssignments().get("vanilla:zombie"));
        assertEquals("crypt_rewards", store.resolve("crypts", "veil_stalker").orElseThrow().id());
        assertEquals("global_rewards", store.resolve(null, "vanilla:zombie").orElseThrow().id());
        run(command, staff, "assign mob vanilla:player global_rewards");
        run(command, staff, "assign mob custom:missing global_rewards");
        run(command, staff, "assign zone missing global_rewards");
        assertEquals(2, store.mobAssignments().size()); assertEquals(1, store.zoneAssignments().size());
    }
    @Test void assignedProfilesCannotBeDeletedAndUnassignmentIsExplicit() throws Exception {
        var store = store(); var command = new MobLootCommand(store, context);
        run(command, staff, "create crypt_rewards"); run(command, staff, "assign zone crypts crypt_rewards");
        run(command, staff, "delete crypt_rewards confirm"); assertTrue(store.find("crypt_rewards").isPresent());
        run(command, staff, "unassign zone crypts wrong"); assertTrue(store.zoneAssignments().containsKey("crypts"));
        run(command, staff, "unassign zone crypts confirm"); run(command, staff, "delete crypt_rewards confirm");
        assertTrue(store.find("crypt_rewards").isEmpty());
    }
    @Test void inspectionAndCompletionAreReadOnlyAndContextAware() throws Exception {
        var store = store(); var command = new MobLootCommand(store, context);
        run(command, staff, "create crypt_rewards"); run(command, staff, "add crypt_rewards bones material bone 25 1 4");
        String before = Files.readString(directory.resolve("mob-loot.yml"));
        run(command, staff, "list"); run(command, staff, "show crypt_rewards"); run(command, staff, "bindings");
        assertEquals(before, Files.readString(directory.resolve("mob-loot.yml")));
        assertTrue(staff.messages.stream().anyMatch(message -> message.contains("25.00%")));
        assertEquals(List.of("crypts"), command.complete(staff.sender, new String[]{"assign", "zone", "c"}));
        assertEquals(List.of("custom:veil_stalker"), command.complete(staff.sender, new String[]{"assign", "mob", "custom:"}));
        var vanillaMatches = command.complete(staff.sender, new String[]{"assign", "mob", "vanilla:zombie"});
        assertTrue(vanillaMatches.contains("vanilla:zombie"));
        assertTrue(vanillaMatches.stream().allMatch(id -> id.startsWith("vanilla:zombie")));
        assertEquals(List.of("crafted_diamond_sword"), command.complete(staff.sender, new String[]{"add", "crypt_rewards", "sword", "item", "crafted_d"}));
        assertEquals(List.of("bones"), command.complete(staff.sender, new String[]{"remove", "crypt_rewards", ""}));
        assertEquals(List.of("bone"), command.complete(staff.sender, new String[]{"add", "crypt_rewards", "bones", "material", "bo"}));
    }
    @Test void orphanAssignmentsCanBeCleanedUpAndCustomAliasesResolveTheSameTarget() throws Exception {
        var store = store(); var command = new MobLootCommand(store, context);
        run(command, staff, "create old_rewards");
        store.assignZone("removed_zone", "old_rewards"); store.assignMob("removed_mob", "old_rewards");
        run(command, staff, "unassign zone removed_zone confirm");
        run(command, staff, "unassign mob custom:removed_mob confirm");
        assertTrue(store.zoneAssignments().isEmpty()); assertTrue(store.mobAssignments().isEmpty());
        run(command, staff, "assign mob veil_stalker old_rewards");
        run(command, staff, "unassign mob custom:veil_stalker confirm");
        assertTrue(store.mobAssignments().isEmpty());
        var vanillaMatches = command.complete(staff.sender, new String[]{"assign", "mob", "VaNiLlA:ZoMbIe"});
        assertTrue(vanillaMatches.contains("vanilla:zombie"));
        assertTrue(vanillaMatches.stream().allMatch(id -> id.startsWith("vanilla:zombie")));
    }
    @Test void diskFailureDoesNotPublishOrReportSuccess() throws Exception {
        AtomicBoolean fail = new AtomicBoolean();
        var store = new LootProfileStore(directory.resolve("mob-loot.yml").toFile(), Set.of(), Set.of(), MobLootCommandTest::itemMaterial, (path, text) -> {
            if (fail.get()) throw new IOException("disk full"); Files.writeString(path, text);
        });
        var command = new MobLootCommand(store, context); run(command, staff, "create crypt_rewards"); fail.set(true); staff.messages.clear();
        run(command, staff, "add crypt_rewards coins marks marks 100 1 5");
        assertTrue(store.find("crypt_rewards").orElseThrow().entries().isEmpty());
        assertEquals(1, context.errors.size()); assertEquals(1, staff.messages.size()); assertTrue(staff.messages.getFirst().contains("could not be saved"));
    }
    private static final class Context implements MobLootCommand.Context {
        final List<IOException> errors = new ArrayList<>();
        public boolean zoneExists(String id) { return id.equals("crypts"); }
        public List<String> zoneIds() { return List.of("crypts"); }
        public List<String> mobIds() { return List.of("veil_stalker"); }
        public List<String> materials() { return List.of("bone", "diamond"); }
        public void saveFailed(IOException error) { errors.add(error); }
    }
    private static final class Actor {
        final List<String> messages = new ArrayList<>();
        final CommandSender sender;
        Actor(boolean permitted) {
            sender = (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class<?>[]{CommandSender.class}, (proxy, method, args) -> switch (method.getName()) {
                case "hasPermission" -> permitted;
                case "getName", "toString" -> "Staff";
                case "sendMessage" -> { for (Object value : args) if (value instanceof TextComponent text) messages.add(text.content()); yield null; }
                default -> throw new AssertionError("Unexpected command side effect: " + method.getName());
            });
        }
    }
}

package net.nerdsmp.paper.zone;

import net.kyori.adventure.text.TextComponent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import static net.nerdsmp.paper.zone.ZoneDefinition.*;
import static org.junit.jupiter.api.Assertions.*;

class ZoneCommandTest {
    @TempDir Path directory;
    private final Context context = new Context();
    private final World world = context.addWorld("hub");
    private static final Bounds BOUNDS = new Bounds(0, 60, 0, 10, 80, 10);

    private ZoneDefinition zone(boolean enabled, boolean safe) {
        return new ZoneDefinition("crypts", "Old Crypts", world.getUID(), BOUNDS, enabled, safe, 8, 20, 48,
            List.of(new SpawnPoint(2.5, 65, 2.5, 0)), List.of(new MobEntry("vanilla:zombie", 5, 10)));
    }
    private ZoneStore store() throws IOException { return new ZoneStore(directory.resolve("zones.yml").toFile()); }
    private void run(ZoneCommand command, Actor actor, String text) { assertTrue(command.command(actor.sender, text.isEmpty() ? new String[0] : text.split(" "))); }

    @Test void unauthorizedSendersCannotMutateOrDiscoverZoneNames() throws Exception {
        ZoneStore store = store(); store.put(zone(false, false));
        ZoneCommand command = new ZoneCommand(context, store);
        Actor actor = new Actor(false, false, world);
        for (String input : List.of("list", "title crypts Hacked", "delete crypts confirm", "pos1", "enable crypts")) run(command, actor, input);
        assertEquals(zone(false, false), store.find("crypts").orElseThrow());
        assertTrue(command.tabComplete(actor.sender, new String[]{""}).isEmpty());
        assertTrue(command.tabComplete(actor.sender, new String[]{"info", ""}).isEmpty());
        assertTrue(actor.messages.stream().allMatch(text -> text.contains("permission")));
    }
    @Test void consoleCanManageExistingZonesButCannotCreateLocationSelections() throws Exception {
        ZoneStore store = store(); store.put(zone(false, false));
        ZoneCommand command = new ZoneCommand(context, store);
        Actor console = new Actor(false, true, world);
        run(command, console, "title crypts The Forgotten Crypts");
        run(command, console, "cap crypts 16");
        run(command, console, "respawn crypts 45");
        run(command, console, "radius crypts 64");
        run(command, console, "enable crypts");
        var updated = store.find("crypts").orElseThrow();
        assertEquals("The Forgotten Crypts", updated.title());
        assertEquals(16, updated.cap()); assertEquals(45, updated.respawnSeconds()); assertEquals(64, updated.activationRadius()); assertTrue(updated.enabled());
        assertEquals(updated, new ZoneStore(directory.resolve("zones.yml").toFile()).find("crypts").orElseThrow());
        run(command, console, "pos1"); run(command, console, "point crypts add");
        assertTrue(console.messages.getLast().contains("Stand in the world"));
        assertEquals(1, store.find("crypts").orElseThrow().points().size());
        run(command, console, "delete crypts");
        assertTrue(store.find("crypts").isPresent());
        run(command, console, "delete crypts confirm");
        assertTrue(store.find("crypts").isEmpty());
        assertTrue(console.messages.getLast().contains("Terrain, builds and NPCs were not changed"));
    }
    @Test void selectionUsesTwoCornersFromTheSameNonIslandWorld() throws Exception {
        ZoneStore store = store(); ZoneCommand command = new ZoneCommand(context, store);
        Actor player = new Actor(true, true, world);
        player.location = new Location(world, 0, 60, 0); run(command, player, "pos1");
        World other = context.addWorld("dungeons");
        player.location = new Location(other, 10, 80, 10); run(command, player, "pos2");
        run(command, player, "create crypts");
        assertTrue(store.all().isEmpty());
        assertTrue(player.messages.getLast().contains("Select two"));
        player.location = new Location(other, 0, 60, 0); run(command, player, "pos1");
        run(command, player, "create crypts");
        ZoneDefinition created = store.find("crypts").orElseThrow();
        assertEquals(other.getUID(), created.worldId()); assertEquals(BOUNDS, created.bounds());
        assertFalse(created.enabled()); assertFalse(created.safe()); assertTrue(created.points().isEmpty()); assertTrue(created.mobs().isEmpty());
        context.islands.add(other.getUID());
        run(command, player, "pos1"); assertTrue(player.messages.getLast().contains("not player islands"));
        run(command, player, "create forbidden"); assertTrue(store.find("forbidden").isEmpty());
        run(command, player, "point crypts add"); assertTrue(store.find("crypts").orElseThrow().points().isEmpty());
        run(command, player, "safe crypts on"); run(command, player, "enable crypts");
        assertFalse(store.find("crypts").orElseThrow().enabled());
    }
    @Test void targetedCornersIncludeSoilRatherThanThePlayersFeet() throws Exception {
        ZoneStore store=store(); ZoneCommand command=new ZoneCommand(context,store);
        Actor actor=new Actor(true,true,world);
        actor.location=new Location(world,5,70,5);
        actor.target=targetBlock(new Location(world,0,60,0)); run(command,actor,"pos1 target");
        actor.target=targetBlock(new Location(world,10,60,10)); run(command,actor,"pos2 target");
        run(command,actor,"create soil");
        assertEquals(new Bounds(0,60,0,10,60,10),store.find("soil").orElseThrow().bounds());
        assertEquals(List.of("target"),command.tabComplete(actor.sender,new String[]{"pos1","t"}));
    }
    @Test void aMissingTargetDoesNotInventASelectionOrCreateTerrain() throws Exception {
        ZoneStore store=store(); ZoneCommand command=new ZoneCommand(context,store);
        Actor actor=new Actor(true,true,world);
        run(command,actor,"pos1 target");
        assertTrue(actor.messages.getLast().contains("Look at a block"));
        run(command,actor,"create soil");
        assertTrue(store.all().isEmpty());
    }
    private static org.bukkit.block.Block targetBlock(Location location) {
        return (org.bukkit.block.Block)Proxy.newProxyInstance(org.bukkit.block.Block.class.getClassLoader(),
            new Class<?>[]{org.bukkit.block.Block.class},(proxy,method,args) -> {
                if(method.getName().equals("getLocation")) return location.clone();
                throw new AssertionError("Selecting a corner must not mutate a block: "+method.getName());
            });
    }
    @Test void incompleteZonesMustBeCompletedOrMarkedSafeBeforeEnabling() throws Exception {
        ZoneStore store = store();
        store.put(new ZoneDefinition("hub", "Hearthport", world.getUID(), BOUNDS, false, false, 8, 20, 48, List.of(), List.of()));
        ZoneCommand command = new ZoneCommand(context, store); Actor console = new Actor(false, true, world);
        run(command, console, "enable hub"); assertFalse(store.find("hub").orElseThrow().enabled());
        run(command, console, "safe hub on"); run(command, console, "enable hub");
        assertTrue(store.find("hub").orElseThrow().enabled()); assertTrue(store.find("hub").orElseThrow().safe());
        run(command, console, "safe hub off");
        assertTrue(store.find("hub").orElseThrow().safe(), "Failed unsafe transition keeps the prior safe state");
        run(command, console, "disable hub"); run(command, console, "safe hub off");
        assertFalse(store.find("hub").orElseThrow().safe());
        context.worlds.clear(); run(command, console, "enable hub");
        assertTrue(console.messages.getLast().contains("Load this zone's world"));
    }
    @Test void rosterAndPointEditsCannotInvalidateAnEnabledZone() throws Exception {
        ZoneStore store = store(); store.put(zone(true, false));
        ZoneCommand command = new ZoneCommand(context, store); Actor player = new Actor(true, true, world);
        run(command, player, "point crypts remove 1"); run(command, player, "mob crypts remove vanilla:zombie");
        assertEquals(zone(true, false), store.find("crypts").orElseThrow());
        player.location = new Location(context.addWorld("elsewhere"), 3, 65, 3);
        run(command, player, "point crypts add"); assertTrue(player.messages.getLast().contains("zone's world"));
        player.location = new Location(world, 11, 65, 3);
        run(command, player, "point crypts add"); assertTrue(player.messages.getLast().contains("inside its zone"));
        player.location = new Location(world, 4.5, 65, 4.5, 90, 0); run(command, player, "point crypts add");
        assertEquals(2, store.find("crypts").orElseThrow().points().size());
        run(command, player, "mob crypts add custom:veil_stalker 3 15");
        assertEquals(2, store.find("crypts").orElseThrow().mobs().size());
        run(command, player, "mob crypts add custom:boss 1 50");
        assertEquals(2, store.find("crypts").orElseThrow().mobs().size());
        run(command, player, "mob crypts add vanilla:zombie 7 12");
        assertEquals(2, store.find("crypts").orElseThrow().mobs().size());
        assertTrue(store.find("crypts").orElseThrow().mobs().contains(new MobEntry("vanilla:zombie", 7, 12)));
        run(command, player, "point crypts remove 0"); assertEquals(2, store.find("crypts").orElseThrow().points().size());
    }
    @Test void completionsArePermissionAndContextAwareAndExcludeUnsafeCustomBosses() throws Exception {
        ZoneStore store = store(); store.put(zone(false, false));
        ZoneCommand command = new ZoneCommand(context, store); Actor console = new Actor(false, true, world);
        assertEquals(List.of("crypts"), command.tabComplete(console.sender, new String[]{"info", "cr"}));
        assertEquals(List.of("add"), command.tabComplete(console.sender, new String[]{"point", "crypts", "a"}));
        assertEquals(List.of("1"), command.tabComplete(console.sender, new String[]{"point", "crypts", "remove", ""}));
        assertEquals(List.of("custom:veil_stalker"), command.tabComplete(console.sender, new String[]{"mob", "crypts", "add", "custom:"}));
        assertEquals(List.of("vanilla:zombie"), command.tabComplete(console.sender, new String[]{"mob", "crypts", "remove", ""}));
        assertEquals(List.of("confirm"), command.tabComplete(console.sender, new String[]{"delete", "crypts", ""}));
        assertTrue(command.tabComplete(console.sender, new String[]{"create", ""}).isEmpty());
    }
    @Test void failedSaveReportsFailureWithoutSuccessOrPublishedMutation() throws Exception {
        AtomicBoolean fail = new AtomicBoolean();
        ZoneStore store = new ZoneStore(directory.resolve("zones.yml").toFile(), (path, yaml) -> {
            if (fail.get()) throw new IOException("disk full"); Files.writeString(path, yaml);
        });
        store.put(zone(false, false)); fail.set(true);
        ZoneCommand command = new ZoneCommand(context, store); Actor console = new Actor(false, true, world);
        run(command, console, "cap crypts 32");
        assertEquals(zone(false, false), store.find("crypts").orElseThrow());
        assertEquals(1, context.errors.size()); assertEquals(1, console.messages.size());
        assertTrue(console.messages.getFirst().contains("could not be saved"));
        run(command, console, "delete crypts confirm"); assertTrue(store.find("crypts").isPresent());
        assertEquals(2, context.errors.size());
    }
    @Test void spawnShortcutAddsPointAndRosterTogetherWithoutOverwritingExistingSettings() throws Exception {
        ZoneStore store = store(); store.put(zone(false, false));
        ZoneCommand command = new ZoneCommand(context, store); Actor actor = new Actor(true, true, world);
        actor.location = new Location(world, 4.5, 65, 4.5, 90, 0);
        run(command, actor, "spawn crypts custom:veil_stalker 15 3");
        var saved = store.find("crypts").orElseThrow();
        assertEquals(2, saved.points().size()); assertEquals(2, saved.mobs().size()); assertFalse(saved.enabled());
        assertTrue(saved.mobs().contains(new MobEntry("custom:veil_stalker", 3, 15)));
        run(command, actor, "spawn crypts custom:veil_stalker");
        assertEquals(saved, store.find("crypts").orElseThrow(), "Same point and roster entry must not duplicate");
        actor.location = new Location(world, 6.5, 65, 6.5);
        run(command, actor, "spawn crypts custom:veil_stalker 25 7");
        assertEquals(saved, store.find("crypts").orElseThrow());
        assertTrue(actor.messages.getLast().contains("No spawn point was added"));
        actor.location = new Location(world, 12.5, 65, 6.5);
        run(command, actor, "spawn crypts vanilla:cow 10");
        assertEquals(saved, store.find("crypts").orElseThrow(), "An out-of-zone landing must not add a roster entry either");
    }
    @Test void spawnShortcutRejectsConsolePrivateWorldAndUnknownMobs() throws Exception {
        ZoneStore store = store(); store.put(zone(false, false));
        ZoneCommand command = new ZoneCommand(context, store); Actor console = new Actor(false, true, world);
        run(command, console, "spawn crypts vanilla:zombie"); assertTrue(console.messages.getLast().contains("Stand in the world"));
        Actor actor = new Actor(true, true, world); run(command, actor, "spawn crypts custom:boss");
        assertEquals(zone(false, false), store.find("crypts").orElseThrow());
        context.islands.add(world.getUID()); run(command, actor, "spawn crypts vanilla:zombie");
        assertTrue(actor.messages.getLast().contains("not player islands"));
        assertEquals(zone(false, false), store.find("crypts").orElseThrow());
    }
    @Test void catalogAndPointListsAreBoundedAndRosterWeightsAreClear() throws Exception {
        ZoneStore store = store(); store.put(zone(false, false));
        ZoneCommand command = new ZoneCommand(context, store); Actor actor = new Actor(false, true, world);
        run(command, actor, "catalog custom");
        assertTrue(actor.messages.contains("  custom:veil_stalker"));
        assertTrue(actor.messages.stream().noneMatch(message -> message.contains("custom:boss")));
        actor.messages.clear(); run(command, actor, "point crypts list");
        assertTrue(actor.messages.stream().anyMatch(message -> message.contains("2.5, 65.0, 2.5")));
        actor.messages.clear(); run(command, actor, "mob crypts list");
        assertTrue(actor.messages.stream().anyMatch(message -> message.contains("100.0%")));
        assertTrue(actor.messages.getFirst().contains("shared by all spawn points"));
        assertEquals(List.of("custom:veil_stalker"), command.tabComplete(actor.sender, new String[]{"spawn", "crypts", "custom:"}));
        assertEquals(List.of("custom"), command.tabComplete(actor.sender, new String[]{"catalog", "c"}));
        assertEquals(List.of("list"), command.tabComplete(actor.sender, new String[]{"point", "crypts", "l"}));
    }

    private static final class Context implements ZoneCommand.Context {
        final Map<UUID, World> worlds = new HashMap<>();
        final Set<UUID> islands = new HashSet<>();
        final List<IOException> errors = new ArrayList<>();
        World addWorld(String name) {
            UUID id = UUID.randomUUID();
            World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getUID" -> id;
                case "getName" -> name;
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                case "toString" -> name;
                default -> throw new AssertionError("Unexpected world operation: " + method.getName());
            });
            worlds.put(id, world); return world;
        }
        public World world(UUID id) { return worlds.get(id); }
        public boolean islandWorld(World world) { return islands.contains(world.getUID()); }
        public void validateMob(String id) {
            if (!ZoneMobTypes.validId(id) || id.startsWith("custom:") && !id.equals("custom:veil_stalker")) throw new IllegalArgumentException("Unknown or unsafe zone mob: " + id);
        }
        public List<String> customMobIds() { return List.of("veil_stalker", "boss"); }
        public void saveFailed(IOException error) { errors.add(error); }
    }
    private static final class Actor {
        final List<String> messages = new ArrayList<>();
        final CommandSender sender;
        Location location;
        org.bukkit.block.Block target;
        Actor(boolean player, boolean permitted, World world) {
            UUID id = UUID.randomUUID(); location = new Location(world, 1, 65, 1);
            Class<?> type = player ? Player.class : CommandSender.class;
            sender = (CommandSender) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
                case "hasPermission" -> permitted;
                case "getUniqueId" -> id;
                case "getLocation" -> location.clone();
                case "getTargetBlockExact" -> target;
                case "getWorld" -> location.getWorld();
                case "getName" -> "Staff";
                case "sendMessage" -> {
                    for (Object arg : args) if (arg instanceof TextComponent text) messages.add(text.content());
                    yield null;
                }
                case "toString" -> "Staff";
                case "hashCode" -> id.hashCode();
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Unexpected player mutation or read: " + method.getName());
            });
        }
    }
}

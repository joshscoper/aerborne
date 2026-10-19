package net.nerdsmp.paper.travel;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

final class TravelRouteGuardTest {
    @TempDir Path directory;

    @Test void unchangedRoutesPermitFreshResolvedLocationInstances() {
        World world = world();
        Location original = new Location(world, 3.5, 97, -2.5, 40, 15);
        var guard = TravelManager.unchangedDestination(original, original::clone);
        assertTrue(guard.getAsBoolean());
        assertTrue(guard.getAsBoolean());
    }

    @Test void deletedOrUnloadedRoutesCancelInsteadOfUsingTheirOldDestination() {
        Location original = new Location(world(), 3.5, 97, -2.5);
        AtomicReference<Location> resolved = new AtomicReference<>(original.clone());
        var guard = TravelManager.unchangedDestination(original, resolved::get);
        assertTrue(guard.getAsBoolean());
        resolved.set(null);
        assertFalse(guard.getAsBoolean());
    }

    @Test void movingRoutesOrChangingTheirWorldOrFacingCancelsTheTrip() {
        Location original = new Location(world(), 3.5, 97, -2.5, 40, 15);
        AtomicReference<Location> resolved = new AtomicReference<>(original.clone());
        var guard = TravelManager.unchangedDestination(original, resolved::get);
        for (Location moved : new Location[] {
            original.clone().add(1, 0, 0), original.clone().add(0, 1, 0), original.clone().add(0, 0, 1),
            new Location(world(), 3.5, 97, -2.5, 40, 15),
            new Location(original.getWorld(), 3.5, 97, -2.5, 41, 15),
            new Location(original.getWorld(), 3.5, 97, -2.5, 40, 16)}) {
            resolved.set(moved);
            assertFalse(guard.getAsBoolean());
        }
    }

    @Test void snapshotDoesNotFollowMutationOfTheCallerLocation() {
        Location original = new Location(world(), 3.5, 97, -2.5);
        AtomicReference<Location> resolved = new AtomicReference<>(original.clone());
        var guard = TravelManager.unchangedDestination(original, resolved::get);
        original.setX(500);
        assertTrue(guard.getAsBoolean());
        resolved.set(original);
        assertFalse(guard.getAsBoolean());
    }

    @Test void savedSpawnChangesInvalidateButFailedChangesKeepThePublishedRoute() {
        World world = world();
        var file = directory.resolve("travel.yml").toFile();
        var store = new TravelStore(file);
        var original = TravelStore.Destination.from(new Location(world, 3.5, 97, -2.5));
        store.setSpawn(original);
        var guard = TravelManager.unchangedDestination(original.resolve(id -> world).orElseThrow(),
            () -> store.spawn().flatMap(point -> point.resolve(id -> world)).orElse(null));
        assertTrue(guard.getAsBoolean());
        var replacement = TravelStore.Destination.from(new Location(world, 30, 90, 50));
        store.setSpawn(replacement);
        assertFalse(guard.getAsBoolean());

        var failing = new TravelStore(file, (yaml, target) -> { throw new IOException("simulated full disk"); });
        var unchanged = TravelManager.unchangedDestination(replacement.resolve(id -> world).orElseThrow(),
            () -> failing.spawn().flatMap(point -> point.resolve(id -> world)).orElse(null));
        assertThrows(IllegalStateException.class, () -> failing.setSpawn(original));
        assertTrue(unchanged.getAsBoolean());
    }

    private static World world() {
        UUID id = UUID.randomUUID();
        return (World) Proxy.newProxyInstance(TravelRouteGuardTest.class.getClassLoader(), new Class<?>[]{World.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUID" -> id;
                case "equals" -> proxy == args[0];
                case "hashCode" -> id.hashCode();
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}

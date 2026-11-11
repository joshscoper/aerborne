package net.aerborne.client.protocol;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClientPreferencesTest {
    @TempDir Path directory;

    @Test void recognizesEquivalentAddressesWithoutMatchingOtherHostsOrPorts() {
        for (String host : new String[] {"play.aerborne.net", "PLAY.AERBORNE.NET:25565", "play.aerborne.net.", " play.aerborne.net.:25565 "})
            assertTrue(ClientPreferences.isDefaultServer(host), host);
        for (String host : new String[] {"play.aerborne.net.evil.com", "play.aerborne.net:25566", "example.com", ""})
            assertFalse(ClientPreferences.isDefaultServer(host), host);
    }
    @Test void roundTripsPreferencesAndScopesTrackingToWorldAndAccount() throws Exception {
        Path file = directory.resolve("client.properties");
        ClientPreferences p = new ClientPreferences();
        p.serverSeeded = true; p.hud = false; p.theme = ClientPreferences.Theme.CELESTIAL; p.scale = 125;
        p.waypoints.add(new ClientPreferences.Waypoint("one", "server/account/world", "Island Home", 12, 80, -30));
        p.tracked = "one"; p.save(file);
        ClientPreferences loaded = ClientPreferences.load(file);
        assertTrue(loaded.serverSeeded); assertFalse(loaded.hud);
        assertEquals(p.theme, loaded.theme); assertEquals(125, loaded.scale);
        assertEquals(p.waypoints, loaded.waypoints);
        assertTrue(loaded.trackedFor("server/account/world").isPresent());
        assertTrue(loaded.trackedFor("server/account/another-world").isEmpty());
        assertTrue(loaded.trackedFor("server/other-account/world").isEmpty());
    }
    @Test void malformedValuesCannotProduceInvalidHudSizes() throws Exception {
        Path file = directory.resolve("client.properties");
        Files.writeString(file, "theme=unknown\nscale=999999\nwaypoints=9999\nwaypoint.0.x=broken\n");
        ClientPreferences loaded = ClientPreferences.load(file);
        assertEquals(ClientPreferences.Theme.SKY, loaded.theme);
        assertEquals(125, loaded.scale);
        assertTrue(loaded.waypoints.isEmpty());
    }
    @Test void waypointRejectsBadCoordinatesAndNames() {
        assertThrows(IllegalArgumentException.class, () -> new ClientPreferences.Waypoint("id", "scope", "x", Integer.MIN_VALUE, 80, 0));
        assertThrows(IllegalArgumentException.class, () -> new ClientPreferences.Waypoint("id", "scope", "\nbad", 0, 80, 0));
        assertEquals(5, new ClientPreferences.Waypoint("id", "scope", "Home", 0, 0, 0).distance(3, 4, 0));
    }
}

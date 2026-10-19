package net.nerdsmp.paper.anticheat;

import net.nerdsmp.paper.anticheat.geo.GeoLocation;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ConnectionLogTest {
    @Test void knownChannelNamespacesBecomeReadableModNames() {
        var hints = ConnectionLog.modHints(ConnectionLog.Profile.of("fabric", List.of(
            "fabric-screen-handler-api-v1:open_screen", "xaerominimap:main", "voicechat:secret", "minecraft:register", "c:version")));
        assertEquals(List.of("Fabric API", "Simple Voice Chat", "Xaero's Minimap"), hints);
    }
    @Test void unknownNamespacesAreShownRawAndClientBrandsAreRecognised() {
        var hints = ConnectionLog.modHints(ConnectionLog.Profile.of("lunarclient:v2.18.3", List.of("mysterymod:sync", "bungeecord:main")));
        assertEquals(List.of("Lunar Client", "mysterymod"), hints);
    }
    @Test void lastKnownLocationComesFromTheMostRecentLocatedAddress() {
        var london = new GeoLocation("GB", "United Kingdom", "England", "London");
        var paris = new GeoLocation("FR", "France", "Île-de-France", "Paris");
        var profile = ConnectionLog.Profile.withAddresses(Map.of("1.1.1.1", 100L, "2.2.2.2", 300L, "3.3.3.3", 500L),
            Map.of("1.1.1.1", london, "2.2.2.2", paris));
        assertEquals(paris, ConnectionLog.lastLocation(profile), "the newest address has no location yet, so the next newest wins");
        assertNull(ConnectionLog.lastLocation(ConnectionLog.Profile.withAddresses(Map.of("1.1.1.1", 1L), Map.of())));
    }
    @Test void vanillaClientsHaveNoHints() {
        assertTrue(ConnectionLog.modHints(ConnectionLog.Profile.of("vanilla", List.of("minecraft:brand"))).isEmpty());
        assertTrue(ConnectionLog.modHints(ConnectionLog.Profile.of(null, List.of())).isEmpty());
    }
}

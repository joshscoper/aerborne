package net.aerborne.client.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClientAccessTest {
    @Test void officialServerWorksWithoutCompanionPlugin() {
        assertTrue(ClientAccess.localToolsAvailable(true, "play.aerborne.net", false));
        assertTrue(ClientAccess.localToolsAvailable(true, "PLAY.AERBORNE.NET.:25565", false));
    }
    @Test void otherServersRequireFreshAdvertisement() {
        assertFalse(ClientAccess.localToolsAvailable(true, "example.com", false));
        assertFalse(ClientAccess.localToolsAvailable(true, "play.aerborne.net.evil.com", false));
        assertTrue(ClientAccess.localToolsAvailable(true, "127.0.0.1:25566", true));
    }
    @Test void disconnectAndSingleplayerDoNotInheritOfficialServerAccess() {
        assertFalse(ClientAccess.localToolsAvailable(false, "play.aerborne.net", true));
        assertFalse(ClientAccess.localToolsAvailable(true, null, false));
    }
}

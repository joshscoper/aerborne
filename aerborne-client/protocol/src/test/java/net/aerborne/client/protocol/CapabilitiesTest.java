package net.aerborne.client.protocol;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapabilitiesTest {
    @Test void wireFormatMatchesPaperBigEndianIntegers() {
        byte[] expected = {0, 0, 0, 1, 0, 0, 0, 1};
        assertArrayEquals(expected, new Capabilities(1, 1).encode());
        assertTrue(Capabilities.decode(expected).supportsMenu());
    }

    @Test void malformedPacketsAreRejected() {
        for (int length : new int[] {0, 4, 7, 9, 1024}) {
            assertThrows(IllegalArgumentException.class, () -> Capabilities.decode(new byte[length]));
        }
    }

    @Test void incompatibleVersionAndAbsentCapabilityDisableMenu() {
        assertFalse(new Capabilities(2, 1).supportsMenu());
        assertFalse(new Capabilities(1, 0).supportsMenu());
        assertTrue(new Capabilities(1, 3).supportsMenu());
    }

    @Test void sessionExpiresAndCannotLeakAfterDisconnect() {
        ClientSession session = new ClientSession();
        assertFalse(session.menuAvailable(100));
        session.receive(new Capabilities(1, 1), 100);
        assertTrue(session.menuAvailable(100 + ClientSession.LEASE_NANOS - 1));
        assertFalse(session.menuAvailable(100 + ClientSession.LEASE_NANOS));
        session.receive(new Capabilities(1, 1), 200);
        session.reset();
        assertFalse(session.menuAvailable(201));
    }

    @Test void revocationImmediatelyReplacesPreviousCapabilities() {
        ClientSession session = new ClientSession();
        session.receive(new Capabilities(1, 1), 0);
        session.receive(new Capabilities(1, 0), 1);
        assertFalse(session.menuAvailable(2));
    }
}

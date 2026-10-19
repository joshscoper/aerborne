package net.nerdsmp.paper.identity;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class JavaIdentityTest {
    @Test void rejectsOfflineAndBedrockIdentityFormatsButKeepsAuthenticatedJavaIds() {
        assertTrue(IdentityProvider.supported(UUID.randomUUID()));
        assertFalse(IdentityProvider.supported(UUID.nameUUIDFromBytes("OfflinePlayer:Test".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        assertFalse(IdentityProvider.supported(UUID.fromString("00000000-0000-0000-0009-01f7a4166244")));
        assertFalse(IdentityProvider.supported(null));
    }
    @Test void runtimeNoLongerRegistersAccountLinkingOrFloodgateDependencies() throws Exception {
        String manifest=Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertFalse(manifest.toLowerCase().contains("floodgate")); assertFalse(manifest.contains("linkaccount:"));
        String docker=Files.readString(Path.of("../docker-compose.yml"));
        assertFalse(docker.contains("19132"));
    }
}

package net.nerdsmp.paper.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class ExcavationContentTest {
    @Test
    void excavationUsesVanillaFindsAndTheDedicatedArchive() throws IOException {
        String items = resource("/content/relics.yml");
        String npcs = resource("/content/npcs.yml");

        for (String retired : new String[] {
            "civic_clay_seal", "carved_wayfinder", "tarnished_watch_badge", "merchant_tally",
            "inscribed_hearth_sherd", "veilglass_fragment", "sunken_medallion",
            "runed_boundary_tablet", "skyward_astrolabe"
        }) {
            assertFalse(items.contains("  " + retired + ":"), "Retired filler item remains: " + retired);
            assertFalse(npcs.contains("custom-item: " + retired), "Retired filler item is still sold: " + retired);
        }
        assertTrue(npcs.contains("type: open_archive"), "Elian does not expose the archive appraisal desk");
    }

    private String resource(String path) throws IOException {
        try (InputStream stream = Objects.requireNonNull(getClass().getResourceAsStream(path), path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

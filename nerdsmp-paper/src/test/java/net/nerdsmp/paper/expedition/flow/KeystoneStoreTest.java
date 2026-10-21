package net.nerdsmp.paper.expedition.flow;

import net.nerdsmp.paper.expedition.content.Keystone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeystoneStoreTest {
    @TempDir Path folder;

    @Test void keysAndBestsSurviveAReload() {
        File file = folder.resolve("keystones.yml").toFile();
        KeystoneStore store = new KeystoneStore(file);
        assertTrue(store.key("a").isEmpty());
        store.setKey("a", new Keystone.Key("hollow_barrow", 4));
        assertTrue(store.recordTimed("hollow_barrow", 4, 900, party("a", "Ash", "b", "Birch")));

        KeystoneStore reloaded = new KeystoneStore(file);
        assertEquals(new Keystone.Key("hollow_barrow", 4), reloaded.key("a").orElseThrow());
        assertEquals(new KeystoneStore.Best(4, 900, List.of("Ash", "Birch")), reloaded.best("hollow_barrow").orElseThrow());
        assertEquals(4, reloaded.personal("b").get("hollow_barrow").level());
    }

    @Test void higherKeysBeatFasterLowerOnes() {
        KeystoneStore store = new KeystoneStore(null);
        assertTrue(store.recordTimed("x", 5, 900, party("a", "Ash")));
        assertFalse(store.recordTimed("x", 4, 300, party("b", "Birch")));
        assertFalse(store.recordTimed("x", 5, 901, party("b", "Birch")));
        assertTrue(store.recordTimed("x", 5, 850, party("b", "Birch")));
        assertEquals(List.of("Birch"), store.best("x").orElseThrow().party());
        assertEquals(5, store.personal("a").get("x").level());
        assertEquals(900, store.personal("a").get("x").seconds());
        assertTrue(new KeystoneStore(null).personal("b").isEmpty());
    }

    private static Map<String, String> party(String... pairs) {
        Map<String, String> party = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) party.put(pairs[index], pairs[index + 1]);
        return party;
    }
}

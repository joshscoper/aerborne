package net.nerdsmp.paper.item;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RelicCatalogFilesTest {
    @TempDir Path directory;
    private YamlConfiguration yaml(String source) throws Exception {
        var yaml = new YamlConfiguration(); yaml.loadFromString(source); return yaml;
    }
    private YamlConfiguration bundled() throws Exception {
        return yaml("schema-version: 11\nsets:\n  grove: {bonus: 2}\nrelics:\n  old: {damage: 4}\n  rod: {power: 3}\n");
    }
    @Test void mergesOnlyMissingIdsAndBacksUpBeforeWriting() throws Exception {
        Path file=directory.resolve("relics.yml");
        String original="schema-version: 10\nsets:\n  grove: {bonus: 99}\nrelics:\n  old: {damage: 42}\n  operator: {power: 8}\nnotes: keep\n";
        Files.writeString(file,original);
        var result=RelicCatalogFiles.load(file.toFile(),bundled(),11,ignored->{});
        assertEquals(42,result.getInt("relics.old.damage"));
        assertEquals(99,result.getInt("sets.grove.bonus"));
        assertEquals(8,result.getInt("relics.operator.power"));
        assertEquals(3,result.getInt("relics.rod.power"));
        assertEquals("keep",result.getString("notes"));
        assertEquals(original,Files.readString(directory.resolve("relics.yml.before-v11.bak")));
        assertEquals(11,YamlConfiguration.loadConfiguration(file.toFile()).getInt("schema-version"));
    }
    @Test void invalidContentNeverRewritesFile() throws Exception {
        Path file=directory.resolve("relics.yml"); String original="schema-version: 10\nsets: {}\nrelics: {}\n";
        Files.writeString(file,original);
        assertThrows(IllegalArgumentException.class,()->RelicCatalogFiles.load(file.toFile(),bundled(),11,
            ignored->{throw new IllegalArgumentException("bad item");}));
        assertEquals(original,Files.readString(file));
        assertFalse(Files.exists(directory.resolve("relics.yml.before-v11.bak")));
    }
    @Test void rejectsFutureOrMalformedFilesWithoutWriting() throws Exception {
        Path file=directory.resolve("relics.yml");
        for(String original:new String[]{"schema-version: 12\nsets: {}\nrelics: {}\n","relics: [unterminated"}) {
            Files.writeString(file,original);
            assertThrows(Exception.class,()->RelicCatalogFiles.load(file.toFile(),bundled(),11,ignored->{}));
            assertEquals(original,Files.readString(file));
        }
    }
    @Test void currentSchemaDoesNotResurrectDeletedEntries() throws Exception {
        Path file=directory.resolve("relics.yml"); Files.writeString(file,"schema-version: 11\nsets: {}\nrelics: {}\n");
        var result=RelicCatalogFiles.load(file.toFile(),bundled(),11,ignored->{});
        assertFalse(result.contains("relics.rod"));
        assertFalse(Files.exists(directory.resolve("relics.yml.before-v11.bak")));
    }
}

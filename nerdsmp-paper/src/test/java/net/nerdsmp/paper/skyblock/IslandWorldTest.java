package net.nerdsmp.paper.skyblock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class IslandWorldTest {
    @TempDir Path temp;

    @Test void usesUnifiedDimensionStorageNotLegacySiblingWorlds() {
        Path level = temp.resolve("world");
        Path dimension = IslandWorld.dimensionDirectory(level, "nerdsmp_skyblock_local-season");
        assertEquals(level.resolve("dimensions/minecraft/nerdsmp_skyblock_local-season"), dimension);
        assertNotEquals(temp.resolve("nerdsmp_skyblock_local-season"), dimension);
    }

    @Test void honorsConfiguredLevelDirectoryAndNormalizesIt() {
        Path level = temp.resolve("universe/custom-level/../main-level");
        assertEquals(temp.resolve("universe/main-level/dimensions/minecraft/nerdsmp_skyblock_season-2"),
            IslandWorld.dimensionDirectory(level, "nerdsmp_skyblock_season-2"));
    }

    @Test void refusesNamesThatCouldEscapeOrAdoptAnotherDimension() {
        for (String name : new String[]{"../world", "nerdsmp_skyblock_../world", "minecraft:overworld", "nerdsmp_skyblock_", "overworld"})
            assertThrows(IllegalArgumentException.class, () -> IslandWorld.dimensionDirectory(temp, name));
    }
}

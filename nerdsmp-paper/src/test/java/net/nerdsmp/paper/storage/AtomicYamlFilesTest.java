package net.nerdsmp.paper.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AtomicYamlFilesTest {
    @TempDir Path directory;
    @Test void transientSharingDenialRetriesTheSameContentAndPublishesOnce() throws Exception {
        Path target = directory.resolve("player.yml"); Files.writeString(target, "old");
        List<Long> delays = new ArrayList<>(); int[] moves = {0};
        AtomicYamlFiles.write("new", target.toFile(), (source, destination) -> {
            assertEquals("old", Files.readString(destination));
            assertEquals("new", Files.readString(source));
            if (++moves[0] <= 2) throw new AccessDeniedException(destination.toString());
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }, delays::add);
        assertEquals(List.of(10L, 20L), delays);
        assertEquals("new", Files.readString(target));
        try (var files = Files.list(directory)) { assertEquals(List.of(target), files.toList()); }
    }
    @Test void permanentSharingDenialIsBoundedAndLeavesOldFileIntact() throws Exception {
        Path target = directory.resolve("player.yml"); Files.writeString(target, "old");
        List<Long> delays = new ArrayList<>(); int[] moves = {0};
        assertThrows(AccessDeniedException.class, () -> AtomicYamlFiles.write("new", target.toFile(), (source, destination) -> {
            moves[0]++; throw new AccessDeniedException(destination.toString());
        }, delays::add));
        assertEquals(5, moves[0]); assertEquals(150, delays.stream().mapToLong(Long::longValue).sum());
        assertEquals("old", Files.readString(target));
        try (var files = Files.list(directory)) { assertEquals(List.of(target), files.toList()); }
    }
    @Test void unrelatedIoFailureIsNotRetried() throws Exception {
        List<Long> delays = new ArrayList<>();
        assertThrows(IOException.class, () -> AtomicYamlFiles.write("new", directory.resolve("player.yml").toFile(),
            (source, destination) -> { throw new IOException("disk failure"); }, delays::add));
        assertTrue(delays.isEmpty());
    }
}

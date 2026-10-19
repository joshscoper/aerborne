package net.nerdsmp.paper.social;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialGraphTest {
    @TempDir Path directory;
    private SocialGraph graph() { return new SocialGraph(directory.resolve("social.yml").toFile(), id -> id); }
    @Test void namesFriendshipsAndBlocksSurviveRestart() {
        var graph = graph(); UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        graph.remember(a, "Alice"); graph.remember(b, "Bob"); graph.friend(a, b); graph.ignore(a, c, true);
        var restored = graph(); assertEquals("Alice", restored.profile(a).name());
        assertTrue(restored.profile(a).friends().contains(b)); assertTrue(restored.profile(b).friends().contains(a));
        assertTrue(restored.blocked(c, a));
    }
    @Test void blockingRemovesBothFriendEdgesAndUnblockingDoesNotRestoreThem() {
        var graph = graph(); UUID a = UUID.randomUUID(), b = UUID.randomUUID(); graph.friend(a, b); graph.ignore(a, b, true);
        assertTrue(graph.profile(a).friends().isEmpty()); assertTrue(graph.profile(b).friends().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> graph.friend(b, a));
        graph.ignore(a, b, false); assertFalse(graph.blocked(a, b)); assertTrue(graph.profile(b).friends().isEmpty());
    }
    @Test void failedWriteDoesNotChangeEitherSideOrGetPublishedLater() throws Exception {
        var graph = graph(); UUID a = UUID.randomUUID(), b = UUID.randomUUID(); graph.friend(a, b);
        byte[] before = Files.readAllBytes(directory.resolve("social.yml")); Files.createDirectory(directory.resolve("social.yml.tmp"));
        assertThrows(IllegalStateException.class, () -> graph.ignore(a, b, true));
        assertFalse(graph.blocked(a, b)); assertTrue(graph.profile(a).friends().contains(b));
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("social.yml")));
    }
    @Test void legacyIdentityMigrationPreservesFriendshipAndCanonicalBlocks() throws Exception {
        UUID oldA = UUID.randomUUID(), oldB = UUID.randomUUID(), canonical = UUID.randomUUID();
        var yaml = new YamlConfiguration();
        yaml.set("players." + oldA + ".name", "Alice"); yaml.set("players." + oldA + ".friends", List.of(oldB.toString()));
        yaml.set("players." + oldB + ".name", "Bob"); yaml.set("players." + oldB + ".friends", List.of(oldA.toString()));
        yaml.save(directory.resolve("social.yml").toFile());
        var graph = new SocialGraph(directory.resolve("social.yml").toFile(), id -> id.equals(oldA) ? canonical : id);
        assertEquals(Set.of(oldB), graph.profile(canonical).friends()); assertEquals(Set.of(canonical), graph.profile(oldB).friends());
        assertFalse(graph.profiles().containsKey(oldA)); assertEquals(graph.profiles(), graph().profiles());
    }
    @Test void corruptOrAsymmetricFriendshipsFailClosed() throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(); var yaml = new YamlConfiguration();
        yaml.set("schema", 2); yaml.set("players." + a + ".name", "Alice"); yaml.set("players." + a + ".friends", List.of(b.toString()));
        yaml.save(directory.resolve("social.yml").toFile()); assertThrows(IllegalStateException.class, this::graph);
    }
    @Test void publicSnapshotsAreImmutableAndSelfRelationshipsRejected() {
        var graph = graph(); UUID a = UUID.randomUUID(), b = UUID.randomUUID(); graph.friend(a, b);
        assertThrows(UnsupportedOperationException.class, () -> graph.profile(a).friends().clear());
        assertThrows(UnsupportedOperationException.class, () -> graph.profiles().clear());
        assertThrows(IllegalArgumentException.class, () -> graph.friend(a, a));
        assertThrows(IllegalArgumentException.class, () -> graph.ignore(a, a, true));
    }
}

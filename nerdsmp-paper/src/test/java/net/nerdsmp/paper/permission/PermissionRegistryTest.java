package net.nerdsmp.paper.permission;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PermissionRegistryTest {
    @TempDir Path directory;
    PermissionRegistry registry() throws Exception { return new PermissionRegistry(directory.resolve("permissions.yml").toFile()); }
    @Test void builtInTiersNeverMakeOrdinaryAdministratorsPermissionOwners() throws Exception {
        var r = registry(); UUID id = UUID.randomUUID(); r.membership("CONSOLE", id, "administrator", true);
        var access = PermissionRegistry.resolve(r.snapshot(), id, Set.of(), Map.of());
        assertTrue(access.get("nerdsmp.staff")); assertTrue(access.get("nerdsmp.staff.moderate"));
        assertFalse(access.getOrDefault("nerdsmp.permissions.manage", false)); assertFalse(access.getOrDefault("nerdsmp.admin", false));
        r.membership("CONSOLE", id, "owner", true);
        assertTrue(PermissionRegistry.resolve(r.snapshot(), id, Set.of(), Map.of()).get("nerdsmp.permissions.manage"));
    }
    @Test void testerGroupGrantsOnlyTheTesterBundle() throws Exception {
        var r = registry(); UUID id = UUID.randomUUID(); r.membership("CONSOLE", id, "tester", true);
        var access = PermissionRegistry.resolve(r.snapshot(), id, Set.of(), Map.of());
        assertEquals(Map.of("nerdsmp.studio.tester", true), access);
    }
    @Test void moderationTiersProtectPeersAndOwnersWithoutMakingBuildersModerators() {
        assertEquals(100, PermissionRegistry.tier(Map.of("nerdsmp.permissions.manage",true)));
        assertEquals(60, PermissionRegistry.tier(Map.of("nerdsmp.market.review",true)));
        assertEquals(50, PermissionRegistry.tier(Map.of("nerdsmp.staff.moderate",true)));
        assertEquals(40, PermissionRegistry.tier(Map.of("nerdsmp.studio.builder",true)));
        assertEquals(30, PermissionRegistry.tier(Map.of("nerdsmp.staff",true)));
        assertEquals(0, PermissionRegistry.tier(Map.of()));
    }
    @Test void parentDeniesAndUserOverridesAreDeterministicIncludingWildcardsAndChildren() throws Exception {
        var r = registry(); UUID id = UUID.randomUUID();
        r.permission("CONSOLE", "helper", null, "nerdsmp.staff.moderate", false);
        r.membership("CONSOLE", id, "moderator", true);
        assertFalse(PermissionRegistry.resolve(r.snapshot(), id, Set.of(), Map.of()).get("nerdsmp.staff.moderate"));
        r.permission("CONSOLE", null, id, "nerdsmp.staff.moderate", true);
        assertTrue(PermissionRegistry.resolve(r.snapshot(), id, Set.of(), Map.of()).get("nerdsmp.staff.moderate"));
        r.permission("CONSOLE", "moderator", null, "nerdsmp.*", true);
        r.permission("CONSOLE", null, id, "nerdsmp.staff.moderate", false);
        var access = PermissionRegistry.resolve(r.snapshot(), id, Set.of("nerdsmp.staff", "nerdsmp.staff.moderate", "minecraft.op"),
            Map.of("nerdsmp.staff", Map.of("nerdsmp.staff.moderate", true)));
        assertFalse(access.get("nerdsmp.staff.moderate")); assertFalse(access.containsKey("minecraft.op"));
    }
    @Test void cyclesUnknownGroupsAndForeignPermissionsAreRejectedWithoutPublishing() throws Exception {
        var r = registry(); var before = r.snapshot();
        assertThrows(IllegalArgumentException.class, () -> r.group("CONSOLE", "helper", "owner"));
        assertThrows(IllegalArgumentException.class, () -> r.group("CONSOLE", "new", "missing"));
        assertThrows(IllegalArgumentException.class, () -> r.permission("CONSOLE", "helper", null, "minecraft.command.op", true));
        assertThrows(IllegalArgumentException.class, () -> r.permission("CONSOLE", "helper", null, "*", true));
        assertEquals(before, r.snapshot());
    }
    @Test void assignmentsAndAuditSurviveRestartAndUnknownPlayersStayUnprivileged() throws Exception {
        var r = registry(); UUID id = UUID.randomUUID(); r.membership("CONSOLE", id, "designer", true);
        assertEquals(r.snapshot(), registry().snapshot()); assertEquals(1, r.snapshot().audit().size());
        assertTrue(PermissionRegistry.resolve(r.snapshot(), UUID.randomUUID(), Set.of(), Map.of()).isEmpty());
        r.membership("CONSOLE", id, "designer", false);
        assertTrue(PermissionRegistry.resolve(r.snapshot(), id, Set.of(), Map.of()).isEmpty());
    }
    @Test void corruptAndDuplicateDocumentsNeverResetExistingAssignments() throws Exception {
        var r = registry(); Path file = directory.resolve("permissions.yml");
        String old = Files.readString(file);
        for (String text : List.of("schema: 1\nschema: 1\ngroups: {}\nusers: {}\naudit: {}\n", old + "unknown: true\n", "[unterminated")) {
            Files.writeString(file, text); assertThrows(Exception.class, this::registry); assertEquals(text, Files.readString(file));
        }
    }
    @Test void aFailedSaveKeepsTheLastPublishedPermissions() throws Exception {
        var r = registry(); var old = r.snapshot(); Path file = directory.resolve("permissions.yml"); Files.delete(file); Files.createDirectory(file); Files.createFile(file.resolve("occupied"));
        assertThrows(Exception.class, () -> r.membership("CONSOLE", UUID.randomUUID(), "owner", true)); assertEquals(old, r.snapshot());
    }
}

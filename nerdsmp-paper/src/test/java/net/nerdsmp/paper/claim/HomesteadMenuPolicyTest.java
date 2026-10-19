package net.nerdsmp.paper.claim;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ClaimService.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class HomesteadMenuPolicyTest {
    private final AccountId owner = AccountId.random(), resident = AccountId.random();
    @Test void paginationNeverOverlapsHeaderOrFooter() {
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i < 21; i++) {
            int slot = HomesteadMenuPolicy.slot(i);
            assertTrue(slot > 8 && slot < 36);
            assertNotEquals(0, slot % 9); assertNotEquals(8, slot % 9);
            assertTrue(slots.add(slot));
        }
        assertThrows(IllegalArgumentException.class, () -> HomesteadMenuPolicy.slot(21));
        assertThrows(IllegalArgumentException.class, () -> HomesteadMenuPolicy.slot(-1));
    }
    @Test void pageIsClampedAfterEntriesAreRemoved() {
        assertEquals(0, HomesteadMenuPolicy.page(3, 0));
        assertEquals(0, HomesteadMenuPolicy.page(-1, 100));
        assertEquals(0, HomesteadMenuPolicy.page(99, 21));
        assertEquals(1, HomesteadMenuPolicy.page(99, 22));
        assertEquals(4, HomesteadMenuPolicy.page(99, 100));
    }
    @Test void rolesDoNotExposeOwnerOrStewardControlsToSharedManagers() {
        var home = new HomesteadView(owner, "Home", Map.of(resident, ClaimRole.MANAGE), Set.of(), 1, 4);
        assertTrue(HomesteadMenuPolicy.manager(resident, home));
        assertFalse(HomesteadMenuPolicy.manager(AccountId.random(), home));
        var plot = plot(PlotType.RESIDENTIAL, null, Map.of());
        assertFalse(HomesteadMenuPolicy.guests(resident, plot));
        assertTrue(HomesteadMenuPolicy.guests(owner, plot));
        assertTrue(HomesteadMenuPolicy.guests(resident, plot(PlotType.RESIDENTIAL, resident, Map.of())));
        assertFalse(HomesteadMenuPolicy.member(null, home));
    }
    @Test void confirmationRejectsChangedPlotEvenWhenClaimIdIsUnchanged() {
        var original = plot(PlotType.RESIDENTIAL, resident, Map.of());
        assertTrue(HomesteadMenuPolicy.unchanged(original, original));
        var changed = new ClaimView(original.claimId(), owner, original.chunk(), original.name(), Map.of(), Set.of(),
            PlotType.RESIDENTIAL, AccountId.random(), Map.of(), Map.of());
        assertFalse(HomesteadMenuPolicy.unchanged(original, changed));
        assertFalse(HomesteadMenuPolicy.unchanged(original, plot(PlotType.RESIDENTIAL, resident, Map.of())));
    }
    @Test void longNoticesWrapWithoutOversizedLoreLines() {
        for (String text : List.of("Word ".repeat(32), "x".repeat(160), "")) {
            assertTrue(HomesteadController.wrap(text).stream().allMatch(line -> line.length() <= 42));
        }
    }
    private ClaimView plot(PlotType type, AccountId steward, Map<ClaimFlag, Boolean> flags) {
        return new ClaimView(UUID.randomUUID(), owner, new ChunkKey(UUID.randomUUID(), 0, 0), "Home", Map.of(), Set.of(), type, steward, flags, Map.of());
    }
}

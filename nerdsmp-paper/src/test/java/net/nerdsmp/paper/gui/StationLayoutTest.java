package net.nerdsmp.paper.gui;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StationLayoutTest {
    @Test void brewingMatrixIsCenteredAndNeverOverlapsPreviewOrNavigation() {
        Set<Integer> cells = new HashSet<>(Set.of(4,45,49,53));
        for (int row=0; row<3; row++) for(int column=0; column<3; column++) {
            int slot = StationLayout.variant(row,column); assertTrue(cells.add(slot)); assertTrue(slot%9>0 && slot%9<8);
        }
        assertThrows(IllegalArgumentException.class, () -> StationLayout.variant(3,0));
    }
    @Test void ingredientGridNeverOverlapsConfirmRefreshOrNavigation() {
        Set<Integer> cells = new HashSet<>(Set.of(4,40,45,49,53));
        for(int i=0; i<14; i++) assertTrue(cells.add(StationLayout.ingredient(i)));
        assertThrows(IllegalArgumentException.class, () -> StationLayout.ingredient(14));
    }
}

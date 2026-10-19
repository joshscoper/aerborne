package net.nerdsmp.paper.npc;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ShopLayoutTest {
    @Test void browseCellsStayInThreeEvenRowsAndAwayFromNavigation() {
        var cells = new HashSet<Integer>();
        for (int i=0;i<21;i++) {
            int slot=ShopLayout.slot(i);
            assertTrue(slot/9>=2 && slot/9<=4 && slot%9>=1 && slot%9<=7);
            assertTrue(cells.add(slot));
            assertFalse(List.of(4,8,45,47,49,51,53).contains(slot));
        }
        assertThrows(IllegalArgumentException.class,()->ShopLayout.slot(21));
    }
    @Test void pagedStockIncludesEveryProductExactlyOnce() {
        var stock=IntStream.range(0,126).boxed().toList();
        var found=new java.util.ArrayList<Integer>();
        for (int page=0;page<6;page++) {
            var view=ShopLayout.page(stock,page);
            assertEquals(6,view.pages()); assertEquals(page,view.number());
            found.addAll(view.entries());
        }
        assertEquals(stock,found);
        assertEquals(5,ShopLayout.page(stock,99).number());
        assertEquals(0,ShopLayout.page(stock,-2).number());
    }
    @Test void emptyAndPartialPagesRemainBounded() {
        assertTrue(ShopLayout.page(List.of(),3).entries().isEmpty());
        assertEquals(1,ShopLayout.page(List.of(),3).pages());
        assertEquals(List.of(21),ShopLayout.page(IntStream.range(0,22).boxed().toList(),1).entries());
    }
}

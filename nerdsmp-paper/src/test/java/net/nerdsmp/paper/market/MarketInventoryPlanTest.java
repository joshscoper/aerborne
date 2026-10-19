package net.nerdsmp.paper.market;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class MarketInventoryPlanTest {
    @Test void fullInventoryDoesNotProduceAPartialDelivery() {
        var original = List.of(new MarketInventoryPlan.Stack<>("stone", 63, 64));
        assertTrue(MarketInventoryPlan.insert(original, new MarketInventoryPlan.Stack<>("stone", 2, 64), String::equals).isEmpty());
        assertEquals(63, original.getFirst().amount());
    }
    @Test void compatibleStackSpaceAndEmptySlotsCanBeCombined() {
        var original = Arrays.asList(new MarketInventoryPlan.Stack<>("stone", 63, 64), null);
        var result = MarketInventoryPlan.insert(original, new MarketInventoryPlan.Stack<>("stone", 3, 64), String::equals).orElseThrow();
        assertEquals(64, result.getFirst().amount());
        assertEquals(2, result.get(1).amount());
        assertNull(original.get(1));
    }
    @Test void distinctMetadataCannotStack() {
        var original = List.of(new MarketInventoryPlan.Stack<>("sword:affix-a", 1, 1));
        assertTrue(MarketInventoryPlan.insert(original, new MarketInventoryPlan.Stack<>("sword:affix-b", 1, 1), String::equals).isEmpty());
    }
}

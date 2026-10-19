package net.nerdsmp.paper.item;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import static org.junit.jupiter.api.Assertions.*;

class LootProcessingRulesTest {
    final LootProcessingRules defaults = new LootProcessingRules(75, 25, 1, LootProcessingRules.DEFAULT_DUST);

    @Test void identificationRetainsTheExistingPerItemPriceAndHasNoHiddenBatchFee() {
        assertEquals(75, defaults.cost(0)); assertEquals(100, defaults.cost(1)); assertEquals(150, defaults.cost(3));
        assertEquals(325, defaults.cost(0) + defaults.cost(1) + defaults.cost(3));
    }
    @Test void dustScalesWithRarityAndExistingAffixes() {
        assertEquals(2, defaults.dust("CRAFTED", 0)); assertEquals(5, defaults.dust("CRAFTED", 3));
        assertEquals(5, defaults.dust("RARE", 0)); assertEquals(9, defaults.dust("EPIC", 1));
        assertEquals(15, defaults.dust("LEGENDARY", 3)); assertEquals(23, defaults.dust("MYTHIC", 3));
        assertEquals(8, defaults.dust("RELIC", 0));
    }
    @Test void ratesCannotWrapOverflowOrAcceptMalformedAffixCounts() {
        assertThrows(IllegalArgumentException.class, () -> defaults.cost(-1));
        assertThrows(IllegalArgumentException.class, () -> defaults.dust("RARE", 1000));
        assertThrows(NullPointerException.class, () -> defaults.dust("FAKE", 1));
        assertThrows(IllegalArgumentException.class, () -> new LootProcessingRules(Long.MAX_VALUE, 0, 1, LootProcessingRules.DEFAULT_DUST));
        assertThrows(IllegalArgumentException.class, () -> new LootProcessingRules(0, -1, 1, LootProcessingRules.DEFAULT_DUST));
        assertThrows(IllegalArgumentException.class, () -> new LootProcessingRules(0, 0, 65, LootProcessingRules.DEFAULT_DUST));
    }
    @Test void operatorRatesAreBoundedAndDefensivelyCopied() {
        var rates = new HashMap<>(LootProcessingRules.DEFAULT_DUST); rates.put("RARE", 30);
        var custom = new LootProcessingRules(0, 0, 2, rates); rates.put("RARE", 1);
        assertEquals(0, custom.cost(3)); assertEquals(36, custom.dust("RARE", 3));
        rates.remove("EPIC"); assertThrows(IllegalArgumentException.class, () -> new LootProcessingRules(0,0,1,rates));
        rates.put("EPIC",1025); assertThrows(IllegalArgumentException.class, () -> new LootProcessingRules(0,0,1,rates));
    }
}

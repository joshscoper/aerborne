package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.ItemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

final class NpcShopProductTest {
    @Test
    void aCustomItemCanBeConfiguredAsAQuotaLimitedSellOnlyAppraisal() {
        assertDoesNotThrow(() -> new NpcContent.ShopProduct(
            "river_sprat",
            null,
            new ItemId("river_sprat"),
            1,
            0L,
            8L,
            20,
            -249,
            "The fishmonger pays extra for a fresh catch."
        ));
    }
}

package net.nerdsmp.paper.npc;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ShopStockBoundsTest {
    @Test void shopPricesFailBeforeTheyCanOverflowAQuote() {
        assertDoesNotThrow(() -> product("stock", 1_000_000L, 999_999L, 1));
        assertThrows(IllegalArgumentException.class, () -> product("stock", 1_000_001L, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> product("stock", 0, 1_000_001L, 1));
    }

    @Test void buybacksRequireAQuotaAndCannotMeetThePurchasePrice() {
        assertThrows(IllegalArgumentException.class, () -> product("stock", 10, 5, 0));
        assertThrows(IllegalArgumentException.class, () -> product("stock", 10, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> product("stock", 10, 10, 1));
    }

    @Test void largerShopsAreBoundedToSixCompletePages() {
        var products = IntStream.range(0, 126).mapToObj(index -> product("stock_" + index, 10, 0, 0)).toList();
        assertDoesNotThrow(() -> new NpcContent.ShopDefinition("shop", "Shop", "Supplies", products));
        assertThrows(IllegalArgumentException.class, () -> new NpcContent.ShopDefinition("shop", "Shop", "Supplies", List.of()));
        var tooMany = new java.util.ArrayList<>(products);
        tooMany.add(product("extra", 10, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new NpcContent.ShopDefinition("shop", "Shop", "Supplies", tooMany));
    }

    private NpcContent.ShopProduct product(String id, long buy, long sell, int limit) {
        return new NpcContent.ShopProduct(id, Material.STONE, null, 1, buy, sell, limit, 0, "Building stone");
    }
}

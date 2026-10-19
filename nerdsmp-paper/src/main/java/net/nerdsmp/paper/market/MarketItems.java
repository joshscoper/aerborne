package net.nerdsmp.paper.market;

import org.bukkit.inventory.ItemStack;
import java.util.Base64;

/** Paper serialization preserves affixes, sockets, enchantments and persistent item metadata. */
public final class MarketItems {
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private MarketItems() { }

    public static String encode(ItemStack item) {
        validate(item);
        byte[] data = item.serializeAsBytes();
        if (data.length > MAX_BYTES) throw new IllegalArgumentException("Item data is too large for a market listing");
        return Base64.getEncoder().encodeToString(data);
    }

    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.length() > (MAX_BYTES * 4L / 3L + 8)) {
            throw new IllegalArgumentException("Invalid market item snapshot size");
        }
        byte[] data = Base64.getDecoder().decode(encoded);
        if (data.length == 0 || data.length > MAX_BYTES) throw new IllegalArgumentException("Invalid market item data");
        ItemStack item = ItemStack.deserializeBytes(data);
        validate(item);
        return item;
    }

    private static void validate(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() < 1
            || item.getAmount() > item.getMaxStackSize()) throw new IllegalArgumentException("Invalid market stack");
    }
}

package net.nerdsmp.paper.item;

import org.bukkit.entity.Player;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

/** Bounded post-transaction receipt: both ingredients and output must actually have moved. */
public final class CraftingReceipt {
    private final ItemStack[] before;
    private final ItemStack output;
    private final int outputBefore, maximum;
    public CraftingReceipt(CraftingInventory inventory, Player player, ItemStack result, boolean shift) {
        before = java.util.Arrays.stream(inventory.getMatrix()).map(s -> s == null ? null : s.clone()).toArray(ItemStack[]::new);
        output = result.clone(); outputBefore = count(player);
        maximum = shift ? java.util.Arrays.stream(before).filter(s -> s != null && !s.getType().isAir()).mapToInt(ItemStack::getAmount).min().orElse(0) : 1;
    }
    public int crafts(CraftingInventory inventory, Player player) {
        ItemStack[] after = inventory.getMatrix();
        if (after.length != before.length) return 0;
        int spent = maximum;
        for (int i = 0; i < before.length; i++) if (before[i] != null && !before[i].getType().isAir()) {
            int remains = after[i] != null && before[i].isSimilar(after[i]) ? after[i].getAmount() : 0;
            spent = Math.min(spent, before[i].getAmount() - remains);
        }
        return Math.max(0, Math.min(spent, Math.max(0, count(player) - outputBefore) / output.getAmount()));
    }
    private int count(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) if (stack != null && stack.isSimilar(output)) count += stack.getAmount();
        ItemStack cursor = player.getItemOnCursor(); if (cursor != null && cursor.isSimilar(output)) count += cursor.getAmount();
        return count;
    }
}

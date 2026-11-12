package net.nerdsmp.paper.loot;

import org.bukkit.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Packs identical rewards before spawning, without changing metadata or legal stack limits. */
public final class DropStacks {
    private DropStacks() { }
    public static List<ItemStack> compact(List<ItemStack> drops) {
        List<ItemStack> packed = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir() || drop.getAmount() <= 0) continue;
            int remaining = drop.getAmount(), limit = drop.getMaxStackSize();
            for (ItemStack target : packed) {
                if (remaining == 0) break;
                if (target.getAmount() >= limit || !target.isSimilar(drop)) continue;
                int moved = Math.min(remaining, limit - target.getAmount());
                target.setAmount(target.getAmount() + moved); remaining -= moved;
            }
            while (remaining > 0) {
                ItemStack stack = drop.clone();
                stack.setAmount(Math.min(limit, remaining)); packed.add(stack); remaining -= stack.getAmount();
            }
        }
        return packed;
    }
}

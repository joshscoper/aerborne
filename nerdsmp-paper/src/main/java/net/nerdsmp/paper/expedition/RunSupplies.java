package net.nerdsmp.paper.expedition;

import net.kyori.adventure.text.format.TextDecoration;
import net.nerdsmp.paper.expedition.content.ExpeditionDefinition;
import net.nerdsmp.paper.presentation.Chat;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Puzzle supply crates: a target puzzle's crate hands out a bow and arrows, a plate puzzle's crate
 * hands out counterweights, so nobody fails a room for not packing the right thing. Supplies are
 * tagged and taken back when the player leaves the run.
 */
final class RunSupplies {
    private static NamespacedKey key;

    private RunSupplies() { }

    static NamespacedKey key(Plugin plugin) {
        if (key == null) key = new NamespacedKey(plugin, "expedition_supply");
        return key;
    }

    /** What a puzzle's crate hands out, or nothing for puzzles that need no items. */
    static List<ItemStack> kit(Plugin plugin, ExpeditionDefinition.Puzzle puzzle) {
        List<ItemStack> kit = new ArrayList<>();
        switch (puzzle.type()) {
            case TARGETS -> {
                ItemStack bow = tagged(plugin, new ItemStack(Material.BOW), "<aqua>Supply Bow</aqua>");
                var meta = bow.getItemMeta();
                meta.setUnbreakable(true);
                bow.setItemMeta(meta);
                kit.add(bow);
                kit.add(tagged(plugin, new ItemStack(Material.ARROW, 16), "<aqua>Supply Arrows</aqua>"));
            }
            case PLATES -> kit.add(tagged(plugin, new ItemStack(Material.COBBLESTONE, Math.min(64, 4 * puzzle.blocks().size())),
                "<aqua>Counterweight</aqua>"));
            default -> { }
        }
        return kit;
    }

    static String describe(ExpeditionDefinition.Puzzle puzzle) {
        return switch (puzzle.type()) {
            case TARGETS -> "a bow and arrows";
            case PLATES -> "counterweights to throw onto the plates";
            default -> "supplies";
        };
    }

    /** True when the player already carries this kind of supply, so the crate doesn't hand out more. */
    static boolean carries(Plugin plugin, Player player, ExpeditionDefinition.Puzzle puzzle) {
        Material wanted = puzzle.type() == ExpeditionDefinition.PuzzleType.TARGETS ? Material.ARROW : Material.COBBLESTONE;
        for (ItemStack item : player.getInventory().getContents())
            if (item != null && item.getType() == wanted && supply(plugin, item)) return true;
        return false;
    }

    static boolean supply(Plugin plugin, ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(key(plugin), PersistentDataType.BYTE);
    }

    /** Takes every supply item back; returns how many stacks were removed. */
    static int strip(Plugin plugin, Player player) {
        int removed = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (!supply(plugin, inventory.getItem(slot))) continue;
            inventory.setItem(slot, null);
            removed++;
        }
        if (supply(plugin, player.getItemOnCursor())) player.setItemOnCursor(null);
        return removed;
    }

    private static ItemStack tagged(Plugin plugin, ItemStack item, String name) {
        var meta = item.getItemMeta();
        meta.itemName(Chat.render(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Chat.render("<gray>Expedition supplies: returned when you leave.</gray>").decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }
}

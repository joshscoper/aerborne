package net.nerdsmp.paper.item;

import org.bukkit.Material;
import org.bukkit.inventory.PlayerInventory;
import net.nerdsmp.paper.item.PaperItemService.GearSlot;

/** Physical armor/weapons stay in their vanilla slots; accessories are server-owned. */
public enum CharacterEquipmentSlot {
    HEAD("Head", GearSlot.HEAD, 10, 39, Material.IRON_HELMET),
    CHEST("Chest", GearSlot.CHEST, 19, 38, Material.IRON_CHESTPLATE),
    LEGS("Legs", GearSlot.LEGS, 28, 37, Material.IRON_LEGGINGS),
    FEET("Feet", GearSlot.FEET, 37, 36, Material.IRON_BOOTS),
    SHOULDERS("Shoulders", GearSlot.SHOULDERS, 12, -1, Material.LEATHER),
    WRISTS("Wrists", GearSlot.WRISTS, 21, -1, Material.COPPER_INGOT),
    HANDS("Hands", GearSlot.HANDS, 30, -1, Material.RABBIT_HIDE),
    WAIST("Belt", GearSlot.WAIST, 39, -1, Material.LEAD),
    NECK("Necklace", GearSlot.NECK, 14, -1, Material.GOLD_NUGGET),
    BACK("Back", GearSlot.BACK, 23, -1, Material.PHANTOM_MEMBRANE),
    RING_1("Ring 1", GearSlot.RING, 32, -1, Material.GOLD_NUGGET),
    RING_2("Ring 2", GearSlot.RING, 41, -1, Material.GOLD_NUGGET),
    TRINKET_1("Trinket 1", GearSlot.TRINKET, 16, -1, Material.AMETHYST_SHARD),
    TRINKET_2("Trinket 2", GearSlot.TRINKET, 25, -1, Material.AMETHYST_SHARD),
    MAIN_HAND("Main Hand", GearSlot.MAIN_HAND, 34, -2, Material.IRON_SWORD),
    OFF_HAND("Off Hand", GearSlot.OFF_HAND, 43, 40, Material.SHIELD);

    private final String title;
    private final GearSlot type;
    private final int menuSlot, inventorySlot;
    private final Material icon;
    CharacterEquipmentSlot(String title, GearSlot type, int menuSlot, int inventorySlot, Material icon) {
        this.title=title; this.type=type; this.menuSlot=menuSlot; this.inventorySlot=inventorySlot; this.icon=icon;
    }
    public String title() { return title; }
    public GearSlot type() { return type; }
    public int menuSlot() { return menuSlot; }
    public Material icon() { return icon; }
    public boolean accessory() { return type.accessory(); }
    public int inventorySlot(PlayerInventory inventory) { return inventorySlot == -2 ? inventory.getHeldItemSlot() : inventorySlot; }
}

package net.nerdsmp.paper.item;

import org.bukkit.Material;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * What rarer gear can do besides carry stats. Like signature affixes these are bound to catalog items
 * (content/item-abilities.yml), so nothing on an item stack can claim one; {@link ItemAbilityService}
 * makes them happen.
 */
public enum ItemAbility {
    SOUL_SIPHON("soul_siphon", "Soul Siphon", Trigger.HIT, "A chance to draw back 6% of your health as you strike.", 25, 4),
    BONE_SHARDS("bone_shards", "Bone Shards", Trigger.HIT, "A chance to burst shards into enemies around your target for 40% of the hit.", 20, 5),
    EMBER_BRAND("ember_brand", "Ember Brand", Trigger.HIT, "A chance to set your target alight for 4 seconds.", 30, 6),
    GRAVE_CHILL("grave_chill", "Grave Chill", Trigger.HIT, "A chance to slow your target for 3 seconds.", 30, 8),
    THUNDERCLAP("thunderclap", "Thunderclap", Trigger.HIT, "A chance to call a strike on your target that shocks everything close by for 60% of the hit.", 15, 10),
    HUNTERS_MARK("hunters_mark", "Hunter's Mark", Trigger.HIT, "Marks what you hit for 6 seconds. It glows, and takes 15% more from you.", 100, 10),
    EMBER_SWEEP("ember_sweep", "Ember Sweep", Trigger.USE, "Sweep a wave of flame in front of you for 150% of your weapon's damage, setting enemies alight.", 100, 12),
    SOUL_NOVA("soul_nova", "Soul Nova", Trigger.USE, "A ring of soul fire bursts from you for 120% of your weapon's damage and slows what it hits.", 100, 16),
    SHADOW_STEP("shadow_step", "Shadow Step", Trigger.USE, "Step up to 8 blocks forward through the air, stopping short of walls.", 100, 14),
    BULWARK("bulwark", "Bulwark", Trigger.USE, "Raise a ward that soaks up hits for 8 seconds.", 100, 30),
    EMBER_AURA("ember_aura", "Ember Aura", Trigger.EQUIP, "Enemies within 3 blocks of you burn.", 100, 2),
    GRAVE_SIGHT("grave_sight", "Grave Sight", Trigger.EQUIP, "You see in the dark.", 100, 0),
    FLEET_STRIDE("fleet_stride", "Fleet Stride", Trigger.EQUIP, "You move faster.", 100, 0),
    LAST_STAND("last_stand", "Last Stand", Trigger.EQUIP, "Falling below 30% health hardens you against hits for 5 seconds.", 100, 60);

    /** When an ability goes off. */
    public enum Trigger {
        HIT("On Hit"), USE("Right Click"), EQUIP("Equipped");

        private final String label;

        Trigger(String label) { this.label = label; }

        public String label() { return label; }
    }

    private static final Set<String> RARITIES = Set.of("RARE", "EPIC", "LEGENDARY", "MYTHIC", "RELIC");
    /** Right-clicking these already does something. */
    private static final Set<Material> BUSY_RIGHT_CLICK = Set.of(Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.SHIELD, Material.FISHING_ROD);

    private final String id;
    private final String displayName;
    private final Trigger trigger;
    private final String description;
    private final int chance;
    private final int cooldownSeconds;

    ItemAbility(String id, String displayName, Trigger trigger, String description, int chance, int cooldownSeconds) {
        this.id = id;
        this.displayName = displayName;
        this.trigger = trigger;
        this.description = description;
        this.chance = chance;
        this.cooldownSeconds = cooldownSeconds;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Trigger trigger() { return trigger; }
    public int chance() { return chance; }
    public int cooldownSeconds() { return cooldownSeconds; }

    /** The tooltip's line: what it does and how often. */
    public String description() {
        String odds = chance < 100 ? " " + chance + "% chance." : "";
        return cooldownSeconds > 2 ? description + odds + " Cooldown " + cooldownSeconds + "s." : description + odds;
    }

    public static Optional<ItemAbility> fromId(String id) {
        return Arrays.stream(values()).filter(ability -> ability.id.equals(id)).findFirst();
    }

    /** Only rarer gear carries abilities, and hit and right-click abilities need something swung from the main hand. */
    public boolean fits(Material material, PaperItemService.GearSlot slot, String rarity) {
        if (!RARITIES.contains(rarity) || slot == PaperItemService.GearSlot.NONE) return false;
        return switch (trigger) {
            case HIT -> slot == PaperItemService.GearSlot.MAIN_HAND;
            case USE -> slot == PaperItemService.GearSlot.MAIN_HAND && !BUSY_RIGHT_CLICK.contains(material);
            case EQUIP -> true;
        };
    }
}

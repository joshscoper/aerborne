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
    SOUL_SIPHON("soul_siphon", "Soul Siphon", Trigger.HIT, "Heals 6% of your health.", 25, 4),
    BONE_SHARDS("bone_shards", "Bone Shards", Trigger.HIT, "Shards hit nearby enemies for 40% of the hit.", 20, 5),
    EMBER_BRAND("ember_brand", "Ember Brand", Trigger.HIT, "Burns your target for 4s.", 30, 6),
    GRAVE_CHILL("grave_chill", "Grave Chill", Trigger.HIT, "Slows your target for 3s.", 30, 8),
    THUNDERCLAP("thunderclap", "Thunderclap", Trigger.HIT, "Lightning shocks nearby enemies for 60% of the hit.", 15, 10),
    HUNTERS_MARK("hunters_mark", "Hunter's Mark", Trigger.HIT, "Your target glows and takes 15% more from you for 6s.", 100, 10),
    EMBER_SWEEP("ember_sweep", "Ember Sweep", Trigger.USE, "Flame sweeps ahead for 150% weapon damage.", 100, 12),
    SOUL_NOVA("soul_nova", "Soul Nova", Trigger.USE, "Soul fire bursts around you for 120% weapon damage and slows.", 100, 16),
    SHADOW_STEP("shadow_step", "Shadow Step", Trigger.USE, "Step up to 8 blocks forward.", 100, 14),
    BULWARK("bulwark", "Bulwark", Trigger.USE, "A ward soaks up hits for 8s.", 100, 30),
    EMBER_AURA("ember_aura", "Ember Aura", Trigger.EQUIP, "Enemies within 3 blocks burn.", 100, 2),
    GRAVE_SIGHT("grave_sight", "Grave Sight", Trigger.EQUIP, "You see in the dark.", 100, 0),
    FLEET_STRIDE("fleet_stride", "Fleet Stride", Trigger.EQUIP, "You move faster.", 100, 0),
    LAST_STAND("last_stand", "Last Stand", Trigger.EQUIP, "Below 30% health, you harden against hits for 5s.", 100, 60);

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
        String often = chance < 100 && cooldownSeconds > 2 ? chance + "% chance, " + cooldownSeconds + "s cooldown."
            : chance < 100 ? chance + "% chance." : cooldownSeconds > 2 ? cooldownSeconds + "s cooldown." : "";
        return often.isEmpty() ? description : description + " " + often;
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

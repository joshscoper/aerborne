package net.nerdsmp.paper.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemAbilityTest {
    private static YamlConfiguration resource(String path) {
        try (var reader = new InputStreamReader(Objects.requireNonNull(ItemAbilityTest.class.getResourceAsStream(path)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static ConfigurationSection item(String id) {
        YamlConfiguration abilities = resource("/content/item-abilities.yml");
        ConfigurationSection found = abilities.getConfigurationSection("items." + id);
        if (found == null) found = resource("/content/relics.yml").getConfigurationSection("relics." + id);
        if (found == null) found = resource("/content/skyforge-items.yml").getConfigurationSection("items." + id);
        return found;
    }

    /** A typo here would stop the whole item catalog loading, so every binding is checked as the server would. */
    @Test void everyBindingNamesARealItemAndAnAbilityThatFitsIt() {
        ConfigurationSection bindings = resource("/content/item-abilities.yml").getConfigurationSection("abilities");
        assertFalse(bindings.getKeys(false).isEmpty());
        for (String id : bindings.getKeys(false)) {
            ConfigurationSection item = item(id);
            assertNotNull(item, id + " isn't in the catalog");
            Material material = Material.matchMaterial(item.getString("material"));
            String slotName = item.getString("slot", "NONE").toUpperCase(Locale.ROOT);
            // The catalog leaves out an item whose slot this build doesn't have.
            if (java.util.Arrays.stream(PaperItemService.GearSlot.values()).noneMatch(known -> known.name().equals(slotName))) continue;
            var slot = PaperItemService.GearSlot.valueOf(slotName);
            String rarity = item.getString("rarity", "RELIC").toUpperCase(Locale.ROOT);
            for (String ability : bindings.getStringList(id)) {
                var bound = ItemAbility.fromId(ability);
                assertTrue(bound.isPresent(), id + " names unknown ability " + ability);
                assertTrue(bound.get().fits(material, slot, rarity), ability + " doesn't fit " + id);
            }
        }
    }

    @Test void everyAddedEpicHasAnAbility() {
        YamlConfiguration file = resource("/content/item-abilities.yml");
        for (String id : file.getConfigurationSection("items").getKeys(false))
            if ("epic".equals(file.getString("items." + id + ".rarity"))) assertFalse(file.getStringList("abilities." + id).isEmpty(), id);
    }

    @Test void noAddedItemTalksTheTooltipLong() {
        ConfigurationSection items = resource("/content/item-abilities.yml").getConfigurationSection("items");
        for (String id : items.getKeys(false)) for (String line : items.getStringList(id + ".lore"))
            assertTrue(TooltipWrap.wrap(line).size() <= 2, id);
        for (ItemAbility ability : ItemAbility.values()) assertTrue(TooltipWrap.wrap(ability.description()).size() <= 3, ability.id());
    }

    @Test void onlyRarerGearCarriesAbilities() {
        var hand = PaperItemService.GearSlot.MAIN_HAND;
        assertFalse(ItemAbility.SOUL_SIPHON.fits(Material.IRON_SWORD, hand, "UNCOMMON"));
        assertTrue(ItemAbility.SOUL_SIPHON.fits(Material.IRON_SWORD, hand, "RARE"));
        assertTrue(ItemAbility.SOUL_SIPHON.fits(Material.IRON_SWORD, hand, "RELIC"));
    }

    @Test void rightClicksStayOffItemsThatAlreadyUseThem() {
        var hand = PaperItemService.GearSlot.MAIN_HAND;
        assertFalse(ItemAbility.EMBER_SWEEP.fits(Material.BOW, hand, "EPIC"));
        assertFalse(ItemAbility.EMBER_SWEEP.fits(Material.TRIDENT, hand, "EPIC"));
        assertTrue(ItemAbility.EMBER_SWEEP.fits(Material.MACE, hand, "EPIC"));
        assertTrue(ItemAbility.SOUL_SIPHON.fits(Material.BOW, hand, "EPIC"), "Arrows can still set off a hit ability");
    }

    @Test void hitAbilitiesBelongInTheHandAndEquippedOnesAnywhere() {
        assertFalse(ItemAbility.SOUL_SIPHON.fits(Material.IRON_HELMET, PaperItemService.GearSlot.HEAD, "EPIC"));
        assertTrue(ItemAbility.EMBER_AURA.fits(Material.IRON_HELMET, PaperItemService.GearSlot.HEAD, "EPIC"));
        assertFalse(ItemAbility.EMBER_AURA.fits(Material.BONE, PaperItemService.GearSlot.NONE, "EPIC"));
    }

    @Test void theTooltipSaysHowOftenAndHowLong() {
        assertEquals("On Hit", ItemAbility.SOUL_SIPHON.trigger().label());
        assertTrue(ItemAbility.SOUL_SIPHON.description().contains("25% chance"));
        assertTrue(ItemAbility.EMBER_SWEEP.description().contains("12s cooldown"));
        assertFalse(ItemAbility.EMBER_AURA.description().contains("cooldown"), "A two-second pulse isn't a cooldown anyone plans around");
    }
}

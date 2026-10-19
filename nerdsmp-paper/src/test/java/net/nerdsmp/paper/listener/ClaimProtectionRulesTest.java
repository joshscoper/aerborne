package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ChunkKey;
import net.nerdsmp.api.model.ClaimFlag;
import net.nerdsmp.api.model.PlotType;
import net.nerdsmp.api.service.ClaimService.ClaimView;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClaimProtectionRulesTest {
    @Test
    void privateAndSharedAutomationNeverMixEvenUnderTheSameOwner() {
        AccountId owner = AccountId.random(), steward = AccountId.random();
        var home = residence(owner, steward);
        assertFalse(ClaimProtectionRules.sameTerritory(home, false, claim(owner), false));
        assertFalse(ClaimProtectionRules.sameTerritory(claim(owner), false, home, false));
        assertTrue(ClaimProtectionRules.sameTerritory(home, false, residence(owner, steward), false));
        assertFalse(ClaimProtectionRules.sameTerritory(home, false, residence(owner, AccountId.random()), false));
        assertFalse(ClaimProtectionRules.sameTerritory(home, false, residence(AccountId.random(), steward), false));
    }

    @Test
    void unassignedHomesRemainIsolatedExceptWithinTheSamePlot() {
        AccountId owner = AccountId.random();
        var home = residence(owner, null);
        assertTrue(ClaimProtectionRules.sameTerritory(home, false, home, false));
        assertFalse(ClaimProtectionRules.sameTerritory(home, false, residence(owner, null), false));
        assertFalse(ClaimProtectionRules.sameTerritory(home, false, residence(owner, AccountId.random()), false));
    }

    private static Optional<ClaimView> residence(AccountId owner, AccountId steward) {
        return Optional.of(new ClaimView(UUID.randomUUID(), owner, new ChunkKey(UUID.randomUUID(), 0, 0), "Home",
            Map.of(), Set.of(), PlotType.RESIDENTIAL, steward, Map.of(), Map.of()));
    }
    @Test
    void automationCannotCrossInEitherDirectionBetweenWildernessAndAClaim() {
        Optional<ClaimView> claim = claim(AccountId.random());
        assertFalse(ClaimProtectionRules.sameTerritory(claim, false, Optional.empty(), false));
        assertFalse(ClaimProtectionRules.sameTerritory(Optional.empty(), false, claim, false));
        assertTrue(ClaimProtectionRules.sameTerritory(Optional.empty(), false, Optional.empty(), false));
    }

    @Test
    void cooperativeAutomationCrossesPlotsWithinOneHomesteadOnly() {
        AccountId owner = AccountId.random();
        assertTrue(ClaimProtectionRules.sameTerritory(claim(owner), false, claim(owner), false));
        assertFalse(ClaimProtectionRules.sameTerritory(claim(owner), false, claim(AccountId.random()), false));
    }

    @Test
    void reservedRegionsRejectAutomationEvenFromAnotherReservedPlot() {
        assertFalse(ClaimProtectionRules.sameTerritory(Optional.empty(), true, Optional.empty(), true));
        assertFalse(ClaimProtectionRules.sameTerritory(Optional.empty(), true, Optional.empty(), false));
        assertFalse(ClaimProtectionRules.sameTerritory(Optional.empty(), false, Optional.empty(), true));
    }

    @Test
    void publicStorageDoesNotAuthorizeHoppersAcrossTerritoryBorders() {
        assertFalse(ClaimProtectionRules.sameTerritory(claim(AccountId.random()), false,
            claim(AccountId.random()), false));
    }

    @Test
    void publicHarvestOnlyAcceptsFullyGrownFoodCrops() {
        assertTrue(ClaimProtectionRules.harvestCrop(Material.WHEAT, 7, 7));
        assertTrue(ClaimProtectionRules.harvestCrop(Material.NETHER_WART, 3, 3));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.WHEAT, 6, 7));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.OAK_SAPLING, 1, 1));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.FIRE, 15, 15));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.STONE, 0, 0));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.TORCHFLOWER_CROP, 1, 1));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.CAVE_VINES, 25, 25));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.WHEAT, 8, 7));
    }

    @Test
    void finishedFlowersAndBerryVinesHaveExplicitHarvestRules() {
        assertTrue(ClaimProtectionRules.harvestCrop(Material.TORCHFLOWER, null));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.TORCHFLOWER_CROP, age(1, 1)));
        assertTrue(ClaimProtectionRules.harvestCrop(Material.CAVE_VINES, vine(true)));
        assertTrue(ClaimProtectionRules.harvestCrop(Material.CAVE_VINES_PLANT, vine(true)));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.CAVE_VINES, vine(false)));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.STONE, vine(true)));
        assertFalse(ClaimProtectionRules.harvestCrop(Material.CAVE_VINES, age(25, 25)));
    }

    @Test
    void publicReplantingCannotBeUsedForBuilding() {
        assertTrue(ClaimProtectionRules.seedCrop(Material.CARROTS));
        assertTrue(ClaimProtectionRules.seedCrop(Material.TORCHFLOWER_CROP));
        assertTrue(ClaimProtectionRules.seedCrop(Material.PITCHER_CROP));
        assertTrue(ClaimProtectionRules.seedCrop(Material.CAVE_VINES));
        assertTrue(ClaimProtectionRules.seedCrop(Material.CAVE_VINES_PLANT));
        assertFalse(ClaimProtectionRules.seedCrop(Material.TORCHFLOWER));
        assertFalse(ClaimProtectionRules.seedCrop(Material.AIR));
        assertFalse(ClaimProtectionRules.seedCrop(Material.FARMLAND));
        assertFalse(ClaimProtectionRules.seedCrop(Material.CHEST));
        assertFalse(ClaimProtectionRules.seedCrop(Material.OAK_LOG));
    }

    @Test
    void publicStationAccessNeverIncludesSharedStorage() {
        assertTrue(ClaimProtectionRules.publicStation(Material.CRAFTING_TABLE));
        assertTrue(ClaimProtectionRules.publicStation(Material.DAMAGED_ANVIL));
        assertTrue(ClaimProtectionRules.publicStation(Material.ENCHANTING_TABLE));
        assertTrue(ClaimProtectionRules.publicStation(Material.BREWING_STAND));
        assertFalse(ClaimProtectionRules.publicStation(Material.CHEST));
        assertFalse(ClaimProtectionRules.publicStation(Material.BARREL));
        assertFalse(ClaimProtectionRules.publicStation(Material.FURNACE));
    }

    private static Optional<ClaimView> claim(AccountId owner) {
        return Optional.of(new ClaimView(UUID.randomUUID(), owner, new ChunkKey(UUID.randomUUID(), 0, 0),
            "Homestead", Map.of(), Set.of(ClaimFlag.PUBLIC_CONTAINERS, ClaimFlag.PUBLIC_INTERACT)));
    }

    private static BlockData age(int age, int maximum) {
        return (Ageable) Proxy.newProxyInstance(Ageable.class.getClassLoader(), new Class<?>[] {Ageable.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getAge" -> age;
                case "getMaximumAge" -> maximum;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static BlockData vine(boolean berries) {
        return (CaveVinesPlant) Proxy.newProxyInstance(CaveVinesPlant.class.getClassLoader(),
            new Class<?>[] {CaveVinesPlant.class}, (proxy, method, args) -> {
                if (method.getName().equals("hasBerries")) return berries;
                throw new UnsupportedOperationException(method.getName());
            });
    }
}

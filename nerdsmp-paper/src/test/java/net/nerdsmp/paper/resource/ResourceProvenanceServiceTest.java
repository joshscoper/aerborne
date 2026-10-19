package net.nerdsmp.paper.resource;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static net.nerdsmp.paper.resource.HarvestClaims.Channel.EXCAVATION;
import static net.nerdsmp.paper.resource.HarvestClaims.Channel.QUESTS;
import static net.nerdsmp.paper.resource.HarvestClaims.Channel.SKILLS;
import static net.nerdsmp.paper.resource.HarvestClaims.Channel.SIGNATURES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Event binding tests without a running server, registry bootstrap, or mocking library. */
final class ResourceProvenanceServiceTest {
    @Test void managedRenewalRequiresLoadedChunkAndExactRestoredMaterial() {
        Fixture f=new Fixture(); TestBlock block=f.block(3,97,4);
        assertTrue(f.eligible(block)); assertFalse(f.eligible(block));
        f.service.renewManaged(block.block,Material.IRON_ORE); assertFalse(f.eligible(block));
        f.loaded=false; f.service.renewManaged(block.block,Material.STONE); f.loaded=true;
        assertFalse(f.eligible(block));
        f.service.renewManaged(block.block,Material.STONE); assertTrue(f.eligible(block));
        assertFalse(f.eligible(block));
    }
    @Test
    void allRewardChannelsShareOnePersistedDecisionAndEachConsumesItOnce() {
        Fixture fixture = new Fixture();
        TestBlock block = fixture.block(3, 64, 5);
        BlockBreakEvent event = fixture.breakEvent(block);
        var skill = fixture.service.claim(event, SKILLS).orElseThrow();
        var excavation = fixture.service.claim(event, EXCAVATION).orElseThrow();
        var quests = fixture.service.claim(event, QUESTS).orElseThrow();
        var signatures = fixture.service.claim(event, SIGNATURES).orElseThrow();
        assertSame(skill, excavation);
        assertSame(skill, quests);
        assertSame(skill, signatures);
        assertEquals(1, block.chunk().writes);
        assertArrayEquals(new long[] {1, (64L << 8) | (3L << 4) | 5}, block.stored());
        for (HarvestClaims.Channel channel : HarvestClaims.Channel.values()) {
            assertTrue(fixture.service.claim(event, channel).isEmpty());
        }
        assertTrue(fixture.service.claim(fixture.breakEvent(block), SKILLS).isEmpty());
    }

    @Test
    void placementAndHarvestMarkersSurviveACompletelyNewServiceInstance() {
        Fixture fixture = new Fixture();
        TestBlock placed = fixture.block(-1, -64, -17);
        TestBlock harvested = fixture.block(1, 64, 1);
        fixture.place(placed);
        assertTrue(fixture.eligible(harvested));
        ResourceProvenanceService restarted = fixture.newService();
        assertTrue(restarted.claim(fixture.breakEvent(placed), SKILLS).isEmpty());
        assertTrue(restarted.claim(fixture.breakEvent(harvested), EXCAVATION).isEmpty());
        assertTrue(restarted.claim(fixture.breakEvent(fixture.block(2, 64, 1)), QUESTS).isPresent());
    }

    @Test
    void cancelledBreakDoesNotWriteOrConsumeTheEventDecision() {
        Fixture fixture = new Fixture();
        TestBlock block = fixture.block(0, 64, 0);
        BlockBreakEvent event = fixture.breakEvent(block);
        event.setCancelled(true);
        assertTrue(fixture.service.claim(event, SKILLS).isEmpty());
        assertEquals(0, block.chunk().writes);
        assertTrue(block.chunk().metadata.isEmpty());
        event.setCancelled(false);
        assertTrue(fixture.service.claim(event, SKILLS).isPresent());
    }

    @Test
    void creativeSpectatorMissingAccountsAndDisabledDropsNeverEarnResourceRewards() {
        Fixture fixture = new Fixture();
        List<TestBlock> consumed = new ArrayList<>();
        for (GameMode mode : List.of(GameMode.CREATIVE, GameMode.SPECTATOR)) {
            fixture.mode = mode;
            TestBlock block = fixture.block(consumed.size(), 64, 0);
            consumed.add(block);
            assertFalse(fixture.eligible(block));
        }
        fixture.mode = GameMode.SURVIVAL;
        fixture.sessions.quit(fixture.playerId);
        TestBlock anonymous = fixture.block(2, 64, 0);
        consumed.add(anonymous);
        assertFalse(fixture.eligible(anonymous));
        fixture.sessions.join(fixture.playerId, AccountId.random());
        TestBlock noDrops = fixture.block(3, 64, 0);
        consumed.add(noDrops);
        BlockBreakEvent event = fixture.breakEvent(noDrops);
        event.setDropItems(false);
        assertTrue(fixture.service.claim(event, SKILLS).isEmpty());
        for (TestBlock block : consumed) assertFalse(fixture.eligible(block));
        fixture.mode = GameMode.ADVENTURE;
        assertTrue(fixture.eligible(fixture.block(4, 64, 0)));
    }

    @Test
    void badSchemaDuplicateWrongSectionAndWrongTypeFailClosedWithoutReplacingEvidence() {
        for (Object invalid : List.of(new long[0], new long[] {2}, new long[] {1, 0},
            new long[] {1, 64L << 8, 64L << 8}, new long[] {1, Long.MAX_VALUE}, "wrong-type")) {
            Fixture fixture = new Fixture();
            TestBlock block = fixture.block(0, 64, 0);
            block.chunk().metadata.put(key(64), invalid);
            assertFalse(fixture.eligible(block));
            assertSame(invalid, block.chunk().metadata.get(key(64)));
            assertEquals(0, block.chunk().writes);
            // Quarantine prevents a later silent replacement from reopening the section.
            block.chunk().metadata.put(key(64), new long[] {1});
            assertFalse(fixture.eligible(fixture.block(1, 64, 0)));
            assertTrue(fixture.eligible(fixture.block(0, 80, 0)));
            assertTrue(fixture.eligible(fixture.block(16, 64, 0)));
        }
    }

    @Test
    void oversizedSectionDataIsRejectedBeforeItCanBeLoaded() {
        Fixture fixture = new Fixture();
        TestBlock block = fixture.block(0, 64, 0);
        long[] oversized = new long[4098];
        oversized[0] = 1;
        block.chunk().metadata.put(key(64), oversized);
        assertFalse(fixture.eligible(block));
        assertSame(oversized, block.chunk().metadata.get(key(64)));
        assertEquals(0, block.chunk().writes);
    }

    @Test
    void persistenceFailurePreventsEveryChannelFromPaying() {
        Fixture fixture = new Fixture();
        TestBlock block = fixture.block(0, 64, 0);
        block.chunk().failWrites = true;
        BlockBreakEvent event = fixture.breakEvent(block);
        for (HarvestClaims.Channel channel : HarvestClaims.Channel.values()) {
            assertTrue(fixture.service.claim(event, channel).isEmpty());
        }
        assertTrue(block.chunk().metadata.isEmpty());
        block.chunk().failWrites = false;
        assertFalse(fixture.eligible(fixture.block(1, 64, 0)));
    }

    @Test
    void negativeCoordinatesDoNotAliasAdjacentChunksOrVerticalSections() {
        Fixture fixture = new Fixture();
        TestBlock negative = fixture.block(-1, -1, -1);
        fixture.place(negative);
        assertArrayEquals(new long[] {1, -1}, negative.stored());
        assertFalse(fixture.eligible(negative));
        assertTrue(fixture.eligible(fixture.block(15, -1, -1)));
        assertTrue(fixture.eligible(fixture.block(-1, -1, 15)));
        assertTrue(fixture.eligible(fixture.block(-1, 0, -1)));
        assertTrue(fixture.eligible(fixture.block(-1, -17, -1)));
    }

    @Test
    void callsOffTheServerThreadAreRejectedBeforeMutation() {
        Fixture fixture = new Fixture();
        fixture.mainThread = false;
        TestBlock block = fixture.block(0, 64, 0);
        assertThrows(IllegalStateException.class, () -> fixture.eligible(block));
        assertEquals(0, block.chunk().writes);
    }

    @Test
    void pistonsBlockBothSourceAndDestinationAcrossAChunkBoundary() {
        Fixture fixture = new Fixture();
        TestBlock source = fixture.block(15, 64, 0);
        fixture.service.onPistonExtend(new BlockPistonExtendEvent(fixture.block(14, 64, 0).block,
            List.of(source.block), BlockFace.EAST));
        assertFalse(fixture.eligible(source));
        assertFalse(fixture.eligible(fixture.block(16, 64, 0)));
        assertTrue(fixture.eligible(fixture.block(17, 64, 0)));
    }

    @Test
    void fallingBlocksAndEndermenCannotCreateEligibleResourcesByMovingThem() {
        Fixture fixture = new Fixture();
        TestBlock fallingTarget = fixture.block(1, 64, 0);
        TestBlock endermanTarget = fixture.block(2, 64, 0);
        fixture.service.onEntityChange(new EntityChangeBlockEvent(proxy(FallingBlock.class, unused()),
            fallingTarget.block, data(Material.SAND)));
        fixture.service.onEntityChange(new EntityChangeBlockEvent(proxy(Enderman.class, unused()),
            endermanTarget.block, data(Material.DIRT)));
        assertFalse(fixture.eligible(fallingTarget));
        assertFalse(fixture.eligible(endermanTarget));
    }

    @Test
    void actualNaturalAndPlayerBonemealGrowthRenewOnlyOnTheFollowingTick() {
        for (boolean bonemeal : List.of(false, true)) {
            Fixture fixture = new Fixture();
            TestBlock log = fixture.blockedAir(0, 64, 0);
            fixture.service.onGrow(fixture.growth(log, bonemeal, List.of(log.state(Material.OAK_LOG))));
            assertArrayEquals(new long[] {1, 64L << 8}, log.stored());
            assertEquals(1, fixture.tasks.size());
            log.material = Material.OAK_LOG;
            fixture.tick();
            assertArrayEquals(new long[] {1}, log.stored());
            assertTrue(fixture.eligible(log));
        }
    }

    @Test
    void cancelledOrUnappliedGrowthDoesNotRenewResources() {
        for (boolean cancelled : List.of(false, true)) {
            Fixture fixture = new Fixture();
            TestBlock log = fixture.blockedAir(0, 64, 0);
            StructureGrowEvent growth = fixture.growth(log, false, List.of(log.state(Material.OAK_LOG)));
            fixture.service.onGrow(growth);
            if (cancelled) {
                growth.setCancelled(true);
                log.material = Material.OAK_LOG;
            }
            fixture.tick();
            assertFalse(fixture.eligible(log));
        }
    }

    @Test
    void aCancelledFertilizeEventVetoesTheEarlierDispenserGrowthBatch() {
        Fixture fixture = new Fixture();
        TestBlock log = fixture.blockedAir(0, 64, 0);
        List<BlockState> states = List.of(log.state(Material.OAK_LOG));
        fixture.service.onGrow(fixture.growth(log, false, states));
        BlockFertilizeEvent fertilize = new BlockFertilizeEvent(log.block, null, states);
        fertilize.setCancelled(true);
        fixture.service.onFertilize(fertilize);
        log.material = Material.OAK_LOG;
        fixture.tick();
        assertFalse(fixture.eligible(log));
    }

    @Test
    void dispenserFertilizationOnlyRenewsItsFinalRetainedLogCoordinates() {
        Fixture fixture = new Fixture();
        TestBlock kept = fixture.blockedAir(0, 64, 0);
        TestBlock removed = fixture.blockedAir(0, 65, 0);
        fixture.service.onGrow(fixture.growth(kept, false,
            List.of(kept.state(Material.OAK_LOG), removed.state(Material.OAK_LOG))));
        fixture.service.onFertilize(new BlockFertilizeEvent(kept.block, null,
            List.of(kept.state(Material.OAK_LOG))));
        kept.material = Material.OAK_LOG;
        removed.material = Material.OAK_LOG;
        fixture.tick();
        assertTrue(fixture.eligible(kept));
        assertFalse(fixture.eligible(removed));
    }

    @Test
    void cancellationOrListFilteringAfterOurHandlerStillPreventsRenewal() {
        for (boolean cancellation : List.of(false, true)) {
            Fixture fixture = new Fixture();
            TestBlock log = fixture.blockedAir(0, 64, 0);
            List<BlockState> finalStates = new ArrayList<>(List.of(log.state(Material.OAK_LOG)));
            BlockFertilizeEvent fertilize = new BlockFertilizeEvent(log.block, fixture.player, finalStates);
            fixture.service.onFertilize(fertilize);
            if (cancellation) fertilize.setCancelled(true);
            else finalStates.clear();
            log.material = Material.OAK_LOG;
            fixture.tick();
            assertFalse(fixture.eligible(log));
        }
    }

    @Test
    void aPlayerReplacementWithIdenticalBlockDataDoesNotInheritTheGrowthRenewal() {
        Fixture fixture = new Fixture();
        TestBlock log = fixture.blockedAir(0, 64, 0);
        fixture.service.onGrow(fixture.growth(log, false, List.of(log.state(Material.OAK_LOG))));
        log.material = Material.OAK_LOG;
        fixture.place(log);
        fixture.tick();
        assertFalse(fixture.eligible(log));
    }

    @Test
    void existingLogsAndDifferentAppliedBlockDataAreNotRenewedByNearbyGrowth() {
        Fixture fixture = new Fixture();
        TestBlock existing = fixture.block(0, 64, 0);
        existing.material = Material.OAK_LOG;
        fixture.place(existing);
        TestBlock changed = fixture.blockedAir(0, 65, 0);
        fixture.service.onGrow(fixture.growth(existing, false,
            List.of(existing.state(Material.OAK_LOG), changed.state(Material.OAK_LOG))));
        changed.material = Material.BIRCH_LOG;
        fixture.tick();
        assertFalse(fixture.eligible(existing));
        assertFalse(fixture.eligible(changed));
    }

    @Test
    void growthVerificationDoesNotForceLoadAnUnloadedChunk() {
        Fixture fixture = new Fixture();
        TestBlock log = fixture.blockedAir(0, 64, 0);
        fixture.service.onGrow(fixture.growth(log, false, List.of(log.state(Material.OAK_LOG))));
        log.material = Material.OAK_LOG;
        fixture.loaded = false;
        fixture.tick();
        fixture.loaded = true;
        assertFalse(fixture.eligible(log));
    }

    @Test
    void replantedCropsEarnFreshRewardsOnlyAfterEachMatureHarvest() {
        Fixture fixture = new Fixture();
        TestBlock crop = fixture.block(0, 64, 0);
        crop.material = Material.WHEAT;
        Set<String> harvestIds = new HashSet<>();
        for (int harvest = 0; harvest < 3; harvest++) {
            crop.overrideData = ageable(Material.WHEAT, 0, 7);
            fixture.place(crop);
            assertFalse(fixture.eligible(crop));
            crop.overrideData = ageable(Material.WHEAT, 7, 7);
            BlockBreakEvent event = fixture.breakEvent(crop);
            var skill = fixture.service.claim(event, SKILLS).orElseThrow();
            assertSame(skill, fixture.service.claim(event, QUESTS).orElseThrow());
            assertTrue(fixture.service.claim(event, SKILLS).isEmpty());
            assertTrue(harvestIds.add(skill.operationId()));
        }
        assertEquals(3, harvestIds.size());
        assertArrayEquals(new long[] {1, 64L << 8}, crop.stored());
    }

    @Test
    void matureAgePropertyOnANonCropDoesNotBypassPlacementMarkers() {
        Fixture fixture = new Fixture();
        TestBlock fire = fixture.block(0, 64, 0);
        fire.material = Material.FIRE;
        fire.overrideData = ageable(Material.FIRE, 15, 15);
        fixture.place(fire);
        assertFalse(fixture.eligible(fire));
    }

    @Test
    void aMatureCropCannotBypassCorruptSectionQuarantine() {
        Fixture fixture = new Fixture();
        TestBlock crop = fixture.block(0, 64, 0);
        crop.material = Material.WHEAT;
        crop.overrideData = ageable(Material.WHEAT, 7, 7);
        crop.chunk().metadata.put(key(64), new long[] {2});
        assertFalse(fixture.eligible(crop));
        assertArrayEquals(new long[] {2}, crop.stored());
    }

    @Test
    void cancelledSingleAndMultiPlacementDoNotCreateMarkers() {
        Fixture fixture = new Fixture();
        TestBlock single = fixture.block(0, 64, 0);
        BlockPlaceEvent place = new BlockPlaceEvent(single.block, single.state(Material.AIR), single.block,
            new EventItem(), fixture.player, true, EquipmentSlot.HAND);
        place.setCancelled(true);
        fixture.service.onPlace(place);
        TestBlock lower = fixture.block(2, 64, 0);
        TestBlock upper = fixture.block(2, 65, 0);
        BlockMultiPlaceEvent multi = new BlockMultiPlaceEvent(
            List.of(lower.state(Material.AIR), upper.state(Material.AIR)), lower.block,
            new EventItem(), fixture.player, true, EquipmentSlot.HAND);
        multi.setCancelled(true);
        fixture.service.onPlace(multi);
        assertEquals(0, single.chunk().writes);
        assertTrue(single.chunk().metadata.isEmpty());
        assertTrue(fixture.eligible(single));
        assertTrue(fixture.eligible(lower));
        assertTrue(fixture.eligible(upper));
    }

    @Test
    void multiPlacementMarksEveryReplacedCoordinateAcrossVerticalSections() {
        Fixture fixture = new Fixture();
        TestBlock lower = fixture.block(15, 15, 0);
        TestBlock upper = fixture.block(15, 16, 0);
        fixture.service.onPlace(new BlockMultiPlaceEvent(
            List.of(lower.state(Material.AIR), upper.state(Material.AIR)), lower.block,
            new EventItem(), fixture.player, true, EquipmentSlot.HAND));
        assertArrayEquals(new long[] {1, (15L << 8) | (15L << 4)}, lower.stored());
        assertArrayEquals(new long[] {1, (16L << 8) | (15L << 4)}, upper.stored());
        assertFalse(fixture.eligible(lower));
        assertFalse(fixture.eligible(upper));
        assertTrue(fixture.eligible(fixture.block(14, 15, 0)));
    }

    @Test
    void retractUsesTheOppositeOfPistonFacingInsteadOfTheReportedEventDirection() {
        Fixture fixture = new Fixture();
        TestBlock piston = fixture.block(14, 64, 0);
        piston.overrideData = proxy(Directional.class, (instance, method, args) -> switch (method.getName()) {
            case "getFacing" -> BlockFace.EAST;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        TestBlock source = fixture.block(16, 64, 0);
        fixture.service.onPistonRetract(new BlockPistonRetractEvent(piston.block,
            List.of(source.block), BlockFace.EAST));
        assertFalse(fixture.eligible(source));
        assertFalse(fixture.eligible(fixture.block(15, 64, 0)));
        assertTrue(fixture.eligible(fixture.block(17, 64, 0)));
    }

    @Test
    void cancelledPistonExtensionAndRetractionDoNotPoisonNaturalResources() {
        Fixture fixture = new Fixture();
        TestBlock source = fixture.block(15, 64, 0);
        TestBlock piston = fixture.block(14, 64, 0);
        BlockPistonExtendEvent extend = new BlockPistonExtendEvent(piston.block,
            List.of(source.block), BlockFace.EAST);
        BlockPistonRetractEvent retract = new BlockPistonRetractEvent(piston.block,
            List.of(source.block), BlockFace.WEST);
        extend.setCancelled(true);
        retract.setCancelled(true);
        fixture.service.onPistonExtend(extend);
        fixture.service.onPistonRetract(retract);
        assertEquals(0, source.chunk().writes);
        assertTrue(fixture.eligible(source));
        assertTrue(fixture.eligible(fixture.block(16, 64, 0)));
        assertTrue(fixture.eligible(fixture.block(14, 64, 0)));
    }

    @Test
    void confirmedGeneratorRenewalAllowsAnotherHarvestButNotAnotherReplay() {
        Fixture f = new Fixture(); TestBlock block = f.block(0, 64, 0);
        assertTrue(f.eligible(block)); assertFalse(f.eligible(block));
        block.material = Material.LAVA;
        var event = new org.bukkit.event.block.BlockFormEvent(block.block, block.state(Material.IRON_ORE));
        f.service.renewGenerator(event); block.material = Material.IRON_ORE; f.tick();
        assertTrue(f.eligible(block)); assertFalse(f.eligible(block));
    }

    @Test
    void cancelledChangedAndUnloadedGeneratorResultsDoNotRenew() {
        for (int variant = 0; variant < 3; variant++) {
            Fixture f = new Fixture(); TestBlock block = f.block(0, 64, 0); f.place(block);
            var event = new org.bukkit.event.block.BlockFormEvent(block.block, block.state(Material.IRON_ORE));
            f.service.renewGenerator(event); block.material = Material.IRON_ORE;
            if (variant == 0) event.setCancelled(true);
            if (variant == 1) block.material = Material.STONE;
            if (variant == 2) f.loaded = false;
            f.tick(); f.loaded = true; assertFalse(f.eligible(block));
        }
    }

    @Test
    void placingOrBreakingTheSameOreBeforeRenewalCannotLaunderAPlacedBlock() {
        for (boolean place : List.of(true, false)) {
            Fixture f = new Fixture(); TestBlock block = f.block(0, 64, 0); f.place(block);
            var event = new org.bukkit.event.block.BlockFormEvent(block.block, block.state(Material.IRON_ORE));
            f.service.renewGenerator(event); block.material = Material.IRON_ORE;
            if (place) f.place(block); else assertFalse(f.eligible(block));
            f.tick(); assertFalse(f.eligible(block));
        }
    }

    @Test
    void verifiedFruitStalkAndTorchflowerGrowthRenewsConsumedCoordinates() {
        for (Material material : List.of(Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE,
            Material.CACTUS, Material.BAMBOO, Material.TORCHFLOWER)) {
            Fixture f = new Fixture();
            TestBlock block = f.blockedAir(0, 64, 0);
            for (int cycle = 0; cycle < 3; cycle++) {
                if (material == Material.TORCHFLOWER) block.material = Material.TORCHFLOWER_CROP;
                BlockGrowEvent event = new BlockGrowEvent(block.block, block.state(material));
                f.service.onFarmGrow(event);
                assertEquals(1, f.tasks.size());
                block.material = material;
                f.tick();
                assertTrue(f.eligible(block), material.name());
                assertFalse(f.eligible(block), material.name());
                block.material = Material.AIR;
            }
        }
    }

    @Test
    void farmGrowthRequiresAcceptedUnchangedLoadedWorldAndActualApplication() {
        for (int variant = 0; variant < 5; variant++) {
            Fixture f = new Fixture();
            TestBlock block = f.blockedAir(0, 64, 0);
            BlockGrowEvent event = new BlockGrowEvent(block.block, block.state(Material.PUMPKIN));
            f.service.onFarmGrow(event);
            block.material = Material.PUMPKIN;
            switch (variant) {
                case 0 -> event.setCancelled(true);
                case 1 -> block.material = Material.AIR;
                case 2 -> block.material = Material.MELON;
                case 3 -> f.loaded = false;
                case 4 -> f.worldPresent = false;
                default -> throw new IllegalStateException();
            }
            f.tick();
            f.loaded = true;
            f.worldPresent = true;
            assertFalse(f.eligible(block));
        }
    }

    @Test
    void farmPlacementOrHarvestBeforeVerificationInvalidatesEvenIdenticalGrowthData() {
        for (boolean place : List.of(true, false)) {
            Fixture f = new Fixture();
            TestBlock block = f.blockedAir(0, 64, 0);
            f.service.onFarmGrow(new BlockGrowEvent(block.block, block.state(Material.MELON)));
            block.material = Material.MELON;
            if (place) f.place(block);
            else assertFalse(f.eligible(block));
            f.tick();
            assertFalse(f.eligible(block));
        }
    }

    @Test
    void onlyBambooSpreadRenewsTheNewCellAndNeverTheSource() {
        Fixture f = new Fixture();
        TestBlock source = f.block(0, 64, 0);
        source.material = Material.BAMBOO;
        f.place(source);
        TestBlock target = f.blockedAir(0, 65, 0);
        BlockSpreadEvent event = new BlockSpreadEvent(target.block, source.block, target.state(Material.BAMBOO));
        f.service.onFarmGrow(event);
        assertEquals(0, f.tasks.size());
        f.service.onFarmSpread(event);
        target.material = Material.BAMBOO;
        f.tick();
        assertTrue(f.eligible(target));
        assertFalse(f.eligible(source));
        TestBlock other = f.blockedAir(1, 65, 0);
        f.service.onFarmSpread(new BlockSpreadEvent(other.block, source.block, other.state(Material.MELON)));
        source.material = Material.OAK_LOG;
        f.service.onFarmSpread(new BlockSpreadEvent(other.block, source.block, other.state(Material.BAMBOO)));
        assertEquals(0, f.tasks.size());
        other.material = Material.BAMBOO;
        assertFalse(f.eligible(other));
    }

    @Test
    void cancelledBambooSpreadCannotRenewItsTarget() {
        Fixture f = new Fixture();
        TestBlock source = f.block(0, 64, 0);
        source.material = Material.BAMBOO;
        TestBlock target = f.blockedAir(0, 65, 0);
        BlockSpreadEvent event = new BlockSpreadEvent(target.block, source.block, target.state(Material.BAMBOO));
        f.service.onFarmSpread(event);
        event.setCancelled(true);
        target.material = Material.BAMBOO;
        f.tick();
        assertFalse(f.eligible(target));
    }

    @Test
    void fertilizationRenewsOnlyFinalRetainedFarmCellsThatActuallyChangedMaterial() {
        Fixture f = new Fixture();
        TestBlock kept = f.blockedAir(0, 64, 0);
        TestBlock removed = f.blockedAir(1, 64, 0);
        TestBlock existing = f.block(2, 64, 0);
        existing.material = Material.PUMPKIN;
        f.place(existing);
        TestBlock unrelated = f.blockedAir(3, 64, 0);
        List<BlockState> states = new ArrayList<>(List.of(kept.state(Material.SUGAR_CANE),
            removed.state(Material.CACTUS), existing.state(Material.PUMPKIN), unrelated.state(Material.STONE)));
        BlockFertilizeEvent event = new BlockFertilizeEvent(kept.block, f.player, states);
        f.service.onFertilize(event);
        states.remove(1);
        kept.material = Material.SUGAR_CANE;
        removed.material = Material.CACTUS;
        unrelated.material = Material.STONE;
        f.tick();
        assertTrue(f.eligible(kept));
        assertFalse(f.eligible(removed));
        assertFalse(f.eligible(existing));
        assertFalse(f.eligible(unrelated));
    }

    @Test
    void cancelledFertilizationAlsoVetoesAnEarlierFarmGrowthRenewal() {
        Fixture f = new Fixture();
        TestBlock target = f.blockedAir(0, 64, 0);
        BlockState state = target.state(Material.TORCHFLOWER);
        f.service.onFarmGrow(new BlockGrowEvent(target.block, state));
        BlockFertilizeEvent event = new BlockFertilizeEvent(target.block, f.player, List.of(state));
        event.setCancelled(true);
        f.service.onFertilize(event);
        target.material = Material.TORCHFLOWER;
        f.tick();
        assertFalse(f.eligible(target));
    }

    @Test
    void lateFertilizationCancellationAndStateFilteringCannotRenewFarmCells() {
        for (boolean cancelled : List.of(false, true)) {
            Fixture f = new Fixture();
            TestBlock target = f.blockedAir(0, 64, 0);
            List<BlockState> states = new ArrayList<>(List.of(target.state(Material.BAMBOO)));
            BlockFertilizeEvent event = new BlockFertilizeEvent(target.block, f.player, states);
            f.service.onFertilize(event);
            if (cancelled) event.setCancelled(true);
            else states.clear();
            target.material = Material.BAMBOO;
            f.tick();
            assertFalse(f.eligible(target));
        }
    }

    @Test
    void farmGrowthWorkIsBoundedAndOversizedFertilizationIsIgnored() {
        Fixture f = new Fixture();
        TestBlock target = f.blockedAir(0, 64, 0);
        for (int attempt = 0; attempt < 4097; attempt++) {
            f.service.onFarmGrow(new BlockGrowEvent(target.block, target.state(Material.MELON)));
        }
        assertEquals(4096, f.tasks.size());
        f.tick();
        assertFalse(f.eligible(target));
        f.service.onFertilize(new BlockFertilizeEvent(target.block, null,
            java.util.Collections.nCopies(4097, target.state(Material.MELON))));
        assertEquals(0, f.tasks.size());
        f.service.onFarmGrow(new BlockGrowEvent(target.block, target.state(Material.MELON)));
        assertEquals(1, f.tasks.size());
        target.material = Material.MELON;
        f.tick();
        assertTrue(f.eligible(target));
    }

    @Test
    void ripeGlowBerryVinesAreRenewedOnlyAfterAnActualBerryGrowthTransition() {
        for (Material material : List.of(Material.CAVE_VINES, Material.CAVE_VINES_PLANT)) {
            Fixture f = new Fixture();
            TestBlock vine = f.block(0, 64, 0);
            vine.material = material;
            vine.overrideData = berries(material, false);
            f.place(vine);
            assertFalse(f.eligible(vine));
            for (int cycle = 0; cycle < 3; cycle++) {
                BlockGrowEvent growth = new BlockGrowEvent(vine.block, vine.state(material, berries(material, true)));
                f.service.onFarmGrow(growth);
                vine.overrideData = berries(material, true);
                f.tick();
                assertTrue(f.eligible(vine));
                assertFalse(f.eligible(vine));
                // Nearby growth must not reopen an already ripe, player-placed/consumed vine.
                f.service.onFarmGrow(new BlockGrowEvent(vine.block, vine.state(material, berries(material, true))));
                assertEquals(0, f.tasks.size());
                assertFalse(f.eligible(vine));
                vine.overrideData = berries(material, false);
            }
        }
    }

    @Test
    void glowBerryFertilizationStillRequiresRetainedAcceptedUnchangedGrowth() {
        for (int variant = 0; variant < 4; variant++) {
            Fixture f = new Fixture();
            TestBlock vine = f.block(0, 64, 0);
            vine.material = Material.CAVE_VINES;
            vine.overrideData = berries(Material.CAVE_VINES, false);
            f.place(vine);
            List<BlockState> states = new ArrayList<>(List.of(vine.state(Material.CAVE_VINES,
                berries(Material.CAVE_VINES, true))));
            BlockFertilizeEvent event = new BlockFertilizeEvent(vine.block, null, states);
            f.service.onFertilize(event);
            vine.overrideData = berries(Material.CAVE_VINES, true);
            if (variant == 1) event.setCancelled(true);
            if (variant == 2) states.clear();
            if (variant == 3) f.place(vine);
            f.tick();
            assertEquals(variant == 0, f.eligible(vine));
        }
    }

    @Test
    void sweetBerryPartialHarvestXpRequiresVerifiedGrowthRatherThanPlacement() {
        Fixture f = new Fixture();
        TestBlock bush = f.block(0, 64, 0);
        bush.material = Material.SWEET_BERRY_BUSH;
        bush.overrideData = ageable(Material.SWEET_BERRY_BUSH, 1, 3);
        f.place(bush);
        bush.overrideData = ageable(Material.SWEET_BERRY_BUSH, 2, 3);
        assertFalse(f.eligible(bush));
        bush.overrideData = ageable(Material.SWEET_BERRY_BUSH, 1, 3);
        f.service.onFarmGrow(new BlockGrowEvent(bush.block,
            bush.state(Material.SWEET_BERRY_BUSH, ageable(Material.SWEET_BERRY_BUSH, 2, 3))));
        bush.overrideData = ageable(Material.SWEET_BERRY_BUSH, 2, 3);
        f.tick();
        assertTrue(f.eligible(bush));
        assertFalse(f.eligible(bush));
        f.service.onFarmGrow(new BlockGrowEvent(bush.block,
            bush.state(Material.SWEET_BERRY_BUSH, ageable(Material.SWEET_BERRY_BUSH, 2, 3))));
        assertEquals(0, f.tasks.size());
        assertFalse(f.eligible(bush));
    }

    private static NamespacedKey key(int y) {
        return new NamespacedKey("nerdsmp", "resource_cells_" + Math.floorDiv(y, 16));
    }

    private static BlockData data(Material material) {
        String encoded = "minecraft:" + material.name().toLowerCase(Locale.ROOT);
        return proxy(BlockData.class, (instance, method, args) -> switch (method.getName()) {
            case "getAsString" -> encoded;
            case "getMaterial" -> material;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Ageable ageable(Material material, int age, int maximumAge) {
        return proxy(Ageable.class, (instance, method, args) -> switch (method.getName()) {
            case "getAge" -> age;
            case "getMaximumAge" -> maximumAge;
            case "getMaterial" -> material;
            case "getAsString" -> "minecraft:" + material.name().toLowerCase(Locale.ROOT) + "[age=" + age + "]";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static CaveVinesPlant berries(Material material, boolean ripe) {
        return proxy(CaveVinesPlant.class, (instance, method, args) -> switch (method.getName()) {
            case "hasBerries", "isBerries" -> ripe;
            case "getMaterial" -> material;
            case "getAsString" -> "minecraft:" + material.name().toLowerCase(Locale.ROOT) + "[berries=" + ripe + "]";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static InvocationHandler unused() {
        return (instance, method, args) -> { throw new UnsupportedOperationException(method.getName()); };
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            (instance, method, args) -> switch (method.getName()) {
                case "equals" -> instance == args[0];
                case "hashCode" -> System.identityHashCode(instance);
                case "toString" -> "Test" + type.getSimpleName();
                default -> handler.invoke(instance, method, args);
            }));
    }

    private static final class EventItem extends ItemStack {
        private EventItem() { super(); }
    }

    private record Position(int x, int y, int z) { }
    private record ChunkPosition(int x, int z) { }

    private static final class Fixture {
        private final UUID worldId = UUID.randomUUID();
        private final UUID playerId = UUID.randomUUID();
        private final SessionRegistry sessions = new SessionRegistry();
        private final Map<Position, TestBlock> blocks = new HashMap<>();
        private final Map<ChunkPosition, TestChunk> chunks = new HashMap<>();
        private final List<Runnable> tasks = new ArrayList<>();
        private final Logger logger = Logger.getAnonymousLogger();
        private GameMode mode = GameMode.SURVIVAL;
        private boolean mainThread = true;
        private boolean loaded = true;
        private boolean worldPresent = true;
        private final World world = proxy(World.class, (instance, method, args) -> switch (method.getName()) {
            case "getUID" -> worldId;
            case "isChunkLoaded" -> loaded;
            case "getBlockAt" -> {
                if (args[0] instanceof Location location) {
                    yield block(location.getBlockX(), location.getBlockY(), location.getBlockZ()).block;
                }
                yield block((Integer) args[0], (Integer) args[1], (Integer) args[2]).block;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        private final Player player = proxy(Player.class, (instance, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> playerId;
            case "getGameMode" -> mode;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        private final ResourceProvenanceService service;

        private Fixture() {
            logger.setLevel(Level.OFF);
            sessions.join(playerId, AccountId.random());
            service = newService();
        }

        private ResourceProvenanceService newService() {
            return new ResourceProvenanceService(sessions, name -> new NamespacedKey("nerdsmp", name),
                tasks::add, () -> mainThread, id -> worldPresent && worldId.equals(id) ? world : null, logger);
        }

        private TestBlock block(int x, int y, int z) {
            return blocks.computeIfAbsent(new Position(x, y, z), position -> new TestBlock(this, position));
        }

        private TestBlock blockedAir(int x, int y, int z) {
            TestBlock block = block(x, y, z);
            place(block);
            block.material = Material.AIR;
            return block;
        }

        private void place(TestBlock block) {
            service.onPlace(new BlockPlaceEvent(block.block, block.state(Material.AIR), block.block,
                new EventItem(), player, true, EquipmentSlot.HAND));
        }

        private BlockBreakEvent breakEvent(TestBlock block) { return new BlockBreakEvent(block.block, player); }
        private boolean eligible(TestBlock block) { return service.claim(breakEvent(block), SKILLS).isPresent(); }

        private StructureGrowEvent growth(TestBlock origin, boolean bonemeal, List<BlockState> states) {
            return new StructureGrowEvent(new Location(world, origin.position.x(), origin.position.y(), origin.position.z()),
                TreeType.TREE, bonemeal, bonemeal ? player : null, states);
        }

        private void tick() {
            List<Runnable> scheduled = List.copyOf(tasks);
            tasks.clear();
            scheduled.forEach(Runnable::run);
        }
    }

    private static final class TestChunk {
        private final Map<NamespacedKey, Object> metadata = new HashMap<>();
        private int writes;
        private boolean failWrites;
        private final PersistentDataContainer pdc = proxy(PersistentDataContainer.class,
            (instance, method, args) -> switch (method.getName()) {
                case "has" -> metadata.containsKey(args[0]);
                case "get" -> metadata.get(args[0]) instanceof long[] value ? value.clone() : null;
                case "set" -> {
                    if (failWrites) throw new IllegalStateException("Simulated chunk storage failure");
                    metadata.put((NamespacedKey) args[0], ((long[]) args[2]).clone());
                    writes++;
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        private final Chunk chunk = proxy(Chunk.class, (instance, method, args) -> switch (method.getName()) {
            case "getPersistentDataContainer" -> pdc;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static final class TestBlock {
        private final Fixture fixture;
        private final Position position;
        private final Block block;
        private Material material = Material.STONE;
        private BlockData overrideData;

        private TestBlock(Fixture fixture, Position position) {
            this.fixture = fixture;
            this.position = position;
            block = proxy(Block.class, (instance, method, args) -> switch (method.getName()) {
                case "getWorld" -> fixture.world;
                case "getX" -> position.x();
                case "getY" -> position.y();
                case "getZ" -> position.z();
                case "getChunk" -> chunk().chunk;
                case "getType" -> material;
                case "getBlockData" -> overrideData == null ? data(material) : overrideData;
                case "getRelative" -> {
                    BlockFace direction = (BlockFace) args[0];
                    yield fixture.block(position.x() + direction.getModX(), position.y() + direction.getModY(),
                        position.z() + direction.getModZ()).block;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }

        private TestChunk chunk() {
            return fixture.chunks.computeIfAbsent(new ChunkPosition(position.x() >> 4, position.z() >> 4),
                ignored -> new TestChunk());
        }

        private long[] stored() { return (long[]) chunk().metadata.get(key(position.y())); }

        private BlockState state(Material type) {
            return state(type, data(type));
        }

        private BlockState state(Material type, BlockData expectedData) {
            return proxy(BlockState.class, (instance, method, args) -> switch (method.getName()) {
                case "getBlock" -> block;
                case "getType" -> type;
                case "getBlockData" -> expectedData;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }
}

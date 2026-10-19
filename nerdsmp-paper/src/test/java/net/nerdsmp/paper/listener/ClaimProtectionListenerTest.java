package net.nerdsmp.paper.listener;

import net.nerdsmp.api.model.AccountId;
import net.nerdsmp.api.model.ChunkKey;
import net.nerdsmp.api.model.ClaimFlag;
import net.nerdsmp.paper.service.InMemoryClaimService;
import net.nerdsmp.paper.session.SessionRegistry;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.CaveVinesPlant;
import org.bukkit.block.data.type.PitcherCrop;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClaimProtectionListenerTest {
    private final UUID worldId = UUID.randomUUID();
    private final World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
        (proxy, method, args) -> switch (method.getName()) {
            case "getUID" -> worldId;
            case "toString" -> "claim-test-world";
            default -> throw new UnsupportedOperationException(method.getName());
        });

    @Test
    void aFluidSourceOutsideAClaimCannotFloodIt() {
        InMemoryClaimService claims = new InMemoryClaimService(4);
        claims.createClaim(AccountId.random(), chunk(0)).toCompletableFuture().join();
        BlockFromToEvent event = new BlockFromToEvent(block(-1), block(0));

        listener(claims).onFlow(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void aPistonCannotPushAClaimedBlockAcrossItsBorder() {
        InMemoryClaimService claims = new InMemoryClaimService(4);
        claims.createClaim(AccountId.random(), chunk(0)).toCompletableFuture().join();
        BlockPistonExtendEvent event = new BlockPistonExtendEvent(block(14), List.of(block(15)), BlockFace.EAST);

        listener(claims).onPistonExtend(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void stickyPistonCannotPullBlocksOutOfAnotherOwnersPlot() {
        InMemoryClaimService claims = new InMemoryClaimService(4);
        claims.createClaim(AccountId.random(), chunk(0)).toCompletableFuture().join();
        claims.createClaim(AccountId.random(), chunk(1)).toCompletableFuture().join();
        BlockPistonRetractEvent event = new BlockPistonRetractEvent(block(14), List.of(block(16)), BlockFace.WEST);

        listener(claims).onPistonRetract(event);

        assertTrue(event.isCancelled());
    }

    @Test
    void sharedFarmMachineryWorksAcrossConnectedHomesteadChunks() {
        InMemoryClaimService claims = new InMemoryClaimService(4);
        AccountId owner = AccountId.random();
        claims.createClaim(owner, chunk(0)).toCompletableFuture().join();
        claims.createClaim(owner, chunk(1)).toCompletableFuture().join();
        BlockPistonExtendEvent event = new BlockPistonExtendEvent(block(14), List.of(block(15)), BlockFace.EAST);

        listener(claims).onPistonExtend(event);

        assertFalse(event.isCancelled());
    }

    @Test
    void publicHarvestAllowsFinishedFlowersAndBerryVinesButNotImmatureOrEmptyPlants() {
        Farm farm = new Farm();
        assertFalse(farm.harvest(farm.cell(64, Material.TORCHFLOWER, null)).isCancelled());
        assertTrue(farm.harvest(farm.cell(64, Material.TORCHFLOWER_CROP, age(1, 1))).isCancelled());
        assertFalse(farm.harvest(farm.cell(64, Material.CAVE_VINES, vine(true))).isCancelled());
        assertFalse(farm.harvest(farm.cell(64, Material.CAVE_VINES_PLANT, vine(true))).isCancelled());
        assertTrue(farm.harvest(farm.cell(64, Material.CAVE_VINES, vine(false))).isCancelled());
        farm.publicHarvest(false);
        assertTrue(farm.harvest(farm.cell(64, Material.TORCHFLOWER, null)).isCancelled());
        assertTrue(farm.harvest(farm.cell(64, Material.CAVE_VINES_PLANT, vine(true))).isCancelled());
    }

    @Test
    void publicPitcherHarvestChecksBothHalvesAndRejectsStaleOrOrphanedCrops() {
        Farm farm = new Farm();
        Cell lower = farm.cell(64, Material.PITCHER_CROP, pitcher(4, Bisected.Half.BOTTOM));
        Cell upper = farm.cell(65, Material.PITCHER_CROP, pitcher(4, Bisected.Half.TOP));
        assertFalse(farm.harvest(lower).isCancelled());
        assertFalse(farm.harvest(upper).isCancelled());
        lower.data = pitcher(3, Bisected.Half.BOTTOM);
        assertTrue(farm.harvest(upper).isCancelled());
        lower.material = Material.AIR;
        assertTrue(farm.harvest(upper).isCancelled());
        lower.material = Material.PITCHER_CROP;
        lower.data = pitcher(4, Bisected.Half.TOP);
        assertTrue(farm.harvest(upper).isCancelled());
    }

    @Test
    void publicPitcherReplantPermitsOnlyItsMatureTopRemovalAndAgeZeroBase() {
        Farm farm = new Farm();
        BlockMultiPlaceEvent accepted = farm.replant(4, 4, Material.PITCHER_CROP, Material.PITCHER_POD, false);
        farm.listener.onPlace(accepted);
        assertFalse(accepted.isCancelled());
        BlockMultiPlaceEvent immature = farm.replant(3, 4, Material.PITCHER_CROP, Material.PITCHER_POD, false);
        farm.listener.onPlace(immature);
        assertTrue(immature.isCancelled());
        BlockMultiPlaceEvent arbitraryTop = farm.replant(4, 4, Material.STONE, Material.PITCHER_POD, false);
        farm.listener.onPlace(arbitraryTop);
        assertTrue(arbitraryTop.isCancelled());
        BlockMultiPlaceEvent wrongSeed = farm.replant(4, 4, Material.PITCHER_CROP, Material.WHEAT_SEEDS, false);
        farm.listener.onPlace(wrongSeed);
        assertTrue(wrongSeed.isCancelled());
        BlockMultiPlaceEvent specialSeed = farm.replant(4, 4, Material.PITCHER_CROP, Material.PITCHER_POD, true);
        farm.listener.onPlace(specialSeed);
        assertTrue(specialSeed.isCancelled());
    }

    @Test
    void revokedPublicHarvestAndChangedReplantBlocksFailClosedWithoutGrantingBuildAccess() {
        Farm farm = new Farm();
        BlockMultiPlaceEvent revoked = farm.replant(4, 4, Material.PITCHER_CROP, Material.PITCHER_POD, false);
        farm.publicHarvest(false);
        farm.listener.onPlace(revoked);
        assertTrue(revoked.isCancelled());
        farm.publicHarvest(true);
        BlockMultiPlaceEvent changed = farm.replant(4, 4, Material.PITCHER_CROP, Material.PITCHER_POD, false);
        farm.cells.get(64).data = pitcher(1, Bisected.Half.BOTTOM);
        farm.listener.onPlace(changed);
        assertTrue(changed.isCancelled());
    }

    @Test
    void ownerBuildRightsDoNotDependOnPublicHarvestOrSpecialReplantRules() {
        Farm farm = new Farm();
        farm.publicHarvest(false);
        farm.sessions.join(farm.playerId, farm.owner);
        assertFalse(farm.harvest(farm.cell(64, Material.TORCHFLOWER_CROP, age(1, 1))).isCancelled());
        BlockMultiPlaceEvent ordinaryBuild = farm.replant(3, 4, Material.STONE, Material.WHEAT_SEEDS, true);
        farm.listener.onPlace(ordinaryBuild);
        assertFalse(ordinaryBuild.isCancelled());
    }

    private ClaimProtectionListener listener(InMemoryClaimService claims) {
        return new ClaimProtectionListener(claims, new SessionRegistry(), null);
    }

    private ChunkKey chunk(int x) {
        return new ChunkKey(worldId, x, 0);
    }

    private Block block(int x) {
        Directional data = (Directional) Proxy.newProxyInstance(Directional.class.getClassLoader(),
            new Class<?>[]{Directional.class}, (proxy, method, args) -> {
                if (method.getName().equals("getFacing")) return BlockFace.EAST;
                throw new UnsupportedOperationException(method.getName());
            });
        return (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[]{Block.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getWorld" -> world;
                case "getX" -> x;
                case "getY" -> 64;
                case "getZ" -> 0;
                case "getBlockData" -> data;
                case "getRelative" -> block(x + ((BlockFace) args[0]).getModX());
                case "toString" -> "Block[x=" + x + "]";
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private final class Farm {
        final InMemoryClaimService claims = new InMemoryClaimService(4);
        final AccountId owner = AccountId.random();
        final SessionRegistry sessions = new SessionRegistry();
        final UUID playerId = UUID.randomUUID();
        final Map<Integer, Cell> cells = new HashMap<>();
        final Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "hasPermission" -> false;
                case "sendActionBar" -> null;
                case "toString" -> "visiting-farmer";
                default -> throw new UnsupportedOperationException(method.getName());
            });
        final ClaimProtectionListener listener = new ClaimProtectionListener(claims, sessions, null);
        Farm() {
            claims.createClaim(owner, chunk(0)).toCompletableFuture().join();
            sessions.join(playerId, AccountId.random());
            publicHarvest(true);
        }
        void publicHarvest(boolean allowed) {
            claims.setFlag(owner, chunk(0), ClaimFlag.PUBLIC_HARVEST, allowed).toCompletableFuture().join();
        }
        Cell cell(int y, Material type, BlockData data) {
            Cell cell = cells.computeIfAbsent(y, height -> new Cell(this, height));
            cell.material = type;
            cell.data = data;
            return cell;
        }
        PlayerHarvestBlockEvent harvest(Cell cell) {
            var event = new PlayerHarvestBlockEvent(player, cell.block, EquipmentSlot.HAND, List.of());
            listener.onHarvest(event);
            return event;
        }
        BlockMultiPlaceEvent replant(int lowerAge, int upperAge, Material previousUpper, Material seed, boolean special) {
            Cell lower = cell(64, Material.PITCHER_CROP, pitcher(lowerAge, Bisected.Half.BOTTOM));
            Cell upper = cell(65, previousUpper, pitcher(upperAge, Bisected.Half.TOP));
            List<BlockState> previous = List.of(lower.snapshot(), upper.snapshot());
            lower.data = pitcher(0, Bisected.Half.BOTTOM);
            upper.material = Material.AIR;
            upper.data = null;
            return new BlockMultiPlaceEvent(previous, cell(63, Material.FARMLAND, null).block,
                new Seed(seed, special), player, true, EquipmentSlot.HAND);
        }
    }

    private final class Cell {
        final Farm farm;
        final int y;
        Material material;
        BlockData data;
        final Block block;
        Cell(Farm farm, int y) {
            this.farm = farm;
            this.y = y;
            block = (Block) Proxy.newProxyInstance(Block.class.getClassLoader(), new Class<?>[] {Block.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getWorld" -> world;
                    case "getX", "getZ" -> 1;
                    case "getY" -> y;
                    case "getType" -> material;
                    case "getBlockData" -> data;
                    case "getRelative" -> farm.cells.get(y + ((BlockFace) args[0]).getModY()).block;
                    case "getState" -> snapshot();
                    case "toString" -> "farm-cell-" + y;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }
        BlockState snapshot() {
            Material previous = material;
            BlockData previousData = data;
            return (BlockState) Proxy.newProxyInstance(BlockState.class.getClassLoader(), new Class<?>[] {BlockState.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBlock" -> block;
                    case "getWorld" -> world;
                    case "getX", "getZ" -> 1;
                    case "getY" -> y;
                    case "getType" -> previous;
                    case "getBlockData" -> previousData;
                    case "toString" -> "farm-snapshot-" + y;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }
    }

    private static BlockData age(int age, int maximum) {
        return (Ageable) Proxy.newProxyInstance(Ageable.class.getClassLoader(), new Class<?>[] {Ageable.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getAge" -> age;
                case "getMaximumAge" -> maximum;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static BlockData pitcher(int age, Bisected.Half half) {
        return (PitcherCrop) Proxy.newProxyInstance(PitcherCrop.class.getClassLoader(), new Class<?>[] {PitcherCrop.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getAge" -> age;
                case "getMaximumAge" -> 4;
                case "getHalf" -> half;
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

    private static final class Seed extends ItemStack {
        private final Material type;
        private final boolean special;
        Seed(Material type, boolean special) { super(); this.type = type; this.special = special; }
        @Override public Material getType() { return type; }
        @Override public int getAmount() { return 1; }
        @Override public boolean hasItemMeta() { return special; }
    }
}

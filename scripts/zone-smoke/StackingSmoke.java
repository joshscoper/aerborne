package net.nerdsmp.smoke;

import net.nerdsmp.paper.NerdSmpPlugin;
import net.nerdsmp.paper.skyblock.IslandAutomation;
import net.nerdsmp.paper.skyblock.IslandGrid;
import net.nerdsmp.paper.skyblock.IslandMobStackPolicy;
import net.nerdsmp.paper.skyblock.IslandService;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Spider;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Controlled real-Paper checks; not a player/load benchmark. Invoked by the zone smoke harness. */
final class StackingSmoke {
    private StackingSmoke() { }

    static void run(NerdSmpPlugin core, IslandService.Island island, World world, BiConsumer<Boolean, String> check) {
        IslandAutomation automation = core.skyblock().automation();
        List<LivingEntity> cleanup = new ArrayList<>();
        NamespacedKey source = new NamespacedKey(core, "island_mob_source");
        NamespacedKey count = new NamespacedKey(core, "island_mob_count");
        int boundary = IslandGrid.chunkX(island.slot()) * 16 + 16;
        int z = IslandGrid.blockZ(island.slot()), y = IslandGrid.SURFACE_Y + 18;
        for (int x = boundary - 3; x <= boundary + 4; x++) for (int dz = -3; dz <= 3; dz++)
            world.getBlockAt(x, y - 1, z + dz).setType(Material.STONE, false);
        try {
            long before = automation.stackDiagnostics().mergedBodies();
            Spider left = spider(world, new Location(world, boundary - .75, y, z + .5), cleanup);
            Spider right = spider(world, new Location(world, boundary + .75, y, z + .5), cleanup);
            queue(automation, left); queue(automation, right); drain(automation);
            check.accept(left.isValid() != right.isValid(), "stack reconciliation merges across a loaded chunk boundary");
            LivingEntity stack = left.isValid() ? left : right;
            check.accept(automation.mobCount(stack) == 2 && automation.stackDiagnostics().mergedBodies() > before,
                "cross-chunk consolidation preserves members and publishes actual merge counters");
            stack.remove();

            Spider first = spider(world, new Location(world, boundary + 1.5, y, z + .5), cleanup);
            Spider temporary = spider(world, new Location(world, boundary + 3.5, y, z + .5), cleanup);
            // Saved ordinary members simulate entities restored before the plugin starts.
            for (Spider member : List.of(first, temporary)) {
                member.getPersistentDataContainer().set(source, PersistentDataType.STRING, island.id().toString());
                member.getPersistentDataContainer().set(count, PersistentDataType.INTEGER, 1);
            }
            temporary.setFireTicks(100); queue(automation, temporary); drain(automation);
            check.accept(temporary.isValid() && automation.mobCount(temporary) == 1,
                "burning members are not absorbed while their state differs");
            temporary.setFireTicks(0);
            automation.queueLoaded(new ChunkLoadEvent(temporary.getChunk(), false)); drain(automation);
            check.accept(first.isValid() != temporary.isValid(),
                "paced loaded-chunk reconciliation retries a member after a temporary blocker clears");
            for (Spider member : List.of(first, temporary)) if (member.isValid()) member.remove();

            List<Spider> crowded = new ArrayList<>();
            for (int n = 0; n < 18; n++) {
                Spider member = spider(world, new Location(world, boundary + 1.5 + (n % 2), y, z + .5), cleanup);
                member.getPersistentDataContainer().set(source, PersistentDataType.STRING, island.id().toString());
                member.getPersistentDataContainer().set(count, PersistentDataType.INTEGER, 15);
                crowded.add(member);
            }
            Spider incoming = spider(world, new Location(world, boundary + 3.5, y, z + .5), cleanup);
            queue(automation, incoming); drain(automation);
            int members = crowded.stream().filter(LivingEntity::isValid).mapToInt(automation::mobCount).sum()
                + (incoming.isValid() ? automation.mobCount(incoming) : 0);
            check.accept(!incoming.isValid() && members == 271,
                "a crowded existing grinder consolidates without losing members or importing extra population");
            check.accept(crowded.stream().anyMatch(member -> member.isValid() && automation.mobCount(member) == 16),
                "crowded recovery still enforces the per-body sixteen-member limit");
            var stats = automation.stackDiagnostics();
            check.accept(stats.lastCandidates() <= IslandMobStackPolicy.WORK_PER_PASS
                && stats.lastNearbyChecks() <= IslandMobStackPolicy.NEARBY_CHECKS_PER_PASS
                && stats.lastChunkSnapshots() <= IslandMobStackPolicy.MAX_CHUNKS_PER_PASS,
                "the entire stack pass respects global candidate, neighbor and snapshot budgets");
            boolean immutable = false;
            try { stats.rejections().put("mutation", 1L); } catch (UnsupportedOperationException expected) { immutable = true; }
            check.accept(immutable, "stack diagnostics are immutable snapshots rather than live mutable maps");
        } finally {
            cleanup.forEach(entity -> { if (entity.isValid()) entity.remove(); });
        }
    }
    private static Spider spider(World world, Location location, List<LivingEntity> cleanup) {
        Spider spider = world.spawn(location, Spider.class, CreatureSpawnEvent.SpawnReason.CUSTOM, false,
            entity -> entity.setCanPickupItems(false));
        cleanup.add(spider); return spider;
    }
    private static void queue(IslandAutomation automation, LivingEntity entity) {
        automation.queueNatural(new CreatureSpawnEvent(entity, CreatureSpawnEvent.SpawnReason.NATURAL));
    }
    private static void drain(IslandAutomation automation) {
        for (int pass = 0; pass < 16; pass++) automation.processMobStacks();
    }
}

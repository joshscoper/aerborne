package net.nerdsmp.paper.skyblock;

import net.nerdsmp.api.model.*;
import net.nerdsmp.api.service.ClaimService;
import net.nerdsmp.paper.claim.TerritoryOverlay;
import java.util.*;

public final class IslandTerritories implements TerritoryOverlay {
    private final UUID world;
    private final IslandService islands;
    static final int CACHE_LIMIT = 2048;
    private final int cacheLimit;
    private final Map<ChunkKey, Cached> cache = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<ChunkKey, Cached> entry) { return size() > cacheLimit; }
    };
    public IslandTerritories(UUID world, IslandService islands) { this(world, islands, CACHE_LIMIT); }
    IslandTerritories(UUID world, IslandService islands, int cacheLimit) {
        this.world = world; this.islands = islands; this.cacheLimit = Math.clamp(cacheLimit, 1, CACHE_LIMIT);
    }
    @Override public boolean manages(UUID id) { return world.equals(id); }
    @Override public synchronized Optional<ClaimService.ClaimView> find(ChunkKey chunk) {
        if (!manages(chunk.worldId())) return Optional.empty();
        return islands.at(chunk.x(), chunk.z()).map(i -> {
            Cached cached = cache.get(chunk);
            if (cached != null && cached.island == i) return cached.view;
            Map<AccountId, ClaimRole> members = new HashMap<>();
            i.members().forEach(member -> members.put(member, ClaimRole.RESIDENT));
            var view = new ClaimService.ClaimView(i.id(), i.owner(), chunk, i.name(), members,
                Set.of(ClaimFlag.HOSTILE_SPAWNING));
            cache.put(chunk, new Cached(i, view));
            return view;
        });
    }
    synchronized int cachedChunks() { return cache.size(); }
    private record Cached(IslandService.Island island, ClaimService.ClaimView view) { }
}

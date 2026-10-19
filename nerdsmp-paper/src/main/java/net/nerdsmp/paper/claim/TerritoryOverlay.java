package net.nerdsmp.paper.claim;

import net.nerdsmp.api.model.ChunkKey;
import net.nerdsmp.api.service.ClaimService;
import java.util.Optional;
import java.util.UUID;

/** Read-only protection supplied by another land system; manual claims cannot replace it. */
public interface TerritoryOverlay {
    boolean manages(UUID world);
    Optional<ClaimService.ClaimView> find(ChunkKey chunk);
}

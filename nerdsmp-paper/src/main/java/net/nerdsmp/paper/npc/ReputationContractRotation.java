package net.nerdsmp.paper.npc;

import net.nerdsmp.api.model.FactionId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class ReputationContractRotation {
    private ReputationContractRotation() {
    }

    static List<ReputationContract> active(
        Collection<ReputationContract> definitions,
        FactionId faction,
        Instant now,
        long seed
    ) {
        List<ReputationContract> active = new ArrayList<>();
        for (ReputationContract.Cadence cadence : ReputationContract.Cadence.values()) {
            List<ReputationContract> candidates = definitions.stream()
                .filter(contract -> contract.factionId().equals(faction) && contract.cadence() == cadence)
                .sorted(Comparator.comparing(ReputationContract::id))
                .toList();
            if (candidates.isEmpty()) {
                continue;
            }
            if (cadence == ReputationContract.Cadence.STORY) {
                active.addAll(candidates);
                continue;
            }
            ReputationContract.Period period = cadence.period(now);
            long offset = seed + faction.value().hashCode() + cadence.ordinal() * 31L + period.serial();
            active.add(candidates.get(Math.floorMod(offset, candidates.size())));
        }
        return List.copyOf(active);
    }
}

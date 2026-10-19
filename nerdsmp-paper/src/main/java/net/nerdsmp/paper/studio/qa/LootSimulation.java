package net.nerdsmp.paper.studio.qa;

import net.nerdsmp.paper.loot.profile.LootProfileDefinition;
import net.nerdsmp.paper.loot.profile.LootProfileRolls;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Pure preview only: no ItemStacks, entities, balances, pickup IDs or reward delivery. */
public final class LootSimulation {
    private LootSimulation() { }
    public static final int SAMPLES = 1_000;
    public record EntryResult(String id, LootProfileDefinition.Kind kind, String source, int successfulRolls,
                              long quantity, double expectedPerKill, boolean unidentified, int affixes) { }
    public record Result(String profileId, LootProfileDefinition.Mode mode, int samples, long seed,
                         List<EntryResult> entries, double expectedMarksPerKill, double sampledMarksPerKill) {
        public Result { entries = List.copyOf(entries); }
    }
    public static Result simulate(LootProfileDefinition profile, long seed) {
        Random random = new Random(seed);
        Map<String, Integer> hits = new LinkedHashMap<>(); Map<String, Long> quantities = new LinkedHashMap<>();
        for (int kill = 0; kill < SAMPLES; kill++) for (var roll : LootProfileRolls.roll(profile, random::nextDouble)) {
            hits.merge(roll.entry().id(), 1, Integer::sum); quantities.merge(roll.entry().id(), (long) roll.amount(), Long::sum);
        }
        List<EntryResult> entries = profile.entries().stream().map(entry -> new EntryResult(entry.id(), entry.kind(), entry.source(),
            hits.getOrDefault(entry.id(), 0), quantities.getOrDefault(entry.id(), 0L),
            entry.chance() * (entry.minimum() + entry.maximum()) / 2.0, entry.unidentified(), entry.affixes())).toList();
        double expectedMarks = entries.stream().filter(entry -> entry.kind() == LootProfileDefinition.Kind.MARKS)
            .mapToDouble(EntryResult::expectedPerKill).sum();
        double sampledMarks = entries.stream().filter(entry -> entry.kind() == LootProfileDefinition.Kind.MARKS)
            .mapToLong(EntryResult::quantity).sum() / (double) SAMPLES;
        return new Result(profile.id(), profile.mode(), SAMPLES, seed, entries, expectedMarks, sampledMarks);
    }
}

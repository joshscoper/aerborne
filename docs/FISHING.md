# Fishing on your island

Fish anywhere Minecraft lets you land a catch. There is **no minimum pond size or depth**, source-water requirement, or overhead-clearance check. Small, shallow, flowing-water, and covered spots can award catches if Minecraft produces a valid catch event. The plugin does not create catches when Minecraft itself cannot fish there.

You can fish on your own island and any co-op you belong to. Keep the bobber itself inside that island's boundary; surrounding water does not need a border buffer. Visitors cannot use another island's pond to earn catches or island work. Fishing checks loaded chunks only; it does not load or generate distant terrain.

Open `/fishing` and select **Pond Habitat** to cycle through your co-op's unlocked habitats. The selection is shared by everyone fishing on that island, does not change its biome or terrain, and costs nothing.

| Island resource stage | Pond habitat unlocked |
| --- | --- |
| Settler | Freshwater |
| Workshop | River |
| Sanctuary | Wetland |
| Foundry | Ocean |
| Aetherworks | Cold Water |

Species that favor the selected habitat are four times more likely. Every species can still appear elsewhere, so changing your habitat never makes existing collections impossible to finish. Resource stages also add up to 5% fishing luck. Every eligible island catch retains the existing island rarity bonus regardless of pond size. Shared-zone open water still provides a small rarity bonus, but is not required to earn any rarity of catch.

In shared zones, the **bobber's biome**, not the ground under your feet, decides the habitat. Long streaks can attract creatures there. Fishing never summons encounter mobs onto private islands.

Successful catches award one combined Fishing XP grant: the configured base plus 12 XP for uncommon, 40 for rare or 120 for legendary catches. With the default 25 XP base, that is 25 / 37 / 65 / 145 XP. Cancelled catches grant no XP, job work, streak progress or island XP. An owner or co-op member's island work is credited to the island containing the bobber, even when casting from its edge.

Ivo still records catches when you physically turn them in. Existing catch totals, streaks, reputation, collection mastery and items are unchanged. Habitat selection adds one seasonal field to the island owner's player-data file; no island or player reset is needed.

## Staff checks

- Test valid catches in a one-block pool, shallow/flowing water, and under cover.
- Cast near an island boundary: only the bobber must stay inside the claim.
- Test an owner, a co-op member and a visitor.
- Confirm an island catch adds Fishing and island XP once; a cancelled catch adds neither.
- Unlock another resource stage and cycle habitats in `/fishing`.
- Check ordinary shared-zone fishing and Ivo's collection turn-ins.

## Relaxed catch requirements — verification, October 17, 2026

The location policy now checks only the bobber's claimed island, loaded chunk, and world-height bounds. It performs no water, depth, cover, or surrounding-column scan. The reward handler still requires an uncancelled `CAUGHT_FISH` event with a caught item, a loaded account, Survival/Adventure mode, and valid island membership. Deferred identity/context checks and duplicate-hook protection remain unchanged.

- Offline `qualityGate build`: PASS; 1,324 tests passed, one skipped, zero failures/errors. Log: `build/fishing-eligibility-quality.log`.
- Disposable Paper 26.2 build 121: PASS; runtime `runtime/zone-smoke-20261017-234328`, log `build/fishing-eligibility-paper.log`.
- The Paper fixture verifies actual reward preparation for supplied catch events in a one-block-wide/one-block-deep pool, flowing water, and a covered pool; cancellation and ordinary reel-in checks still reject rewards. It also tests the last claimed island block, co-op access/revocation, unloaded spots, retained habitats, and updated journal hints.
- These are server-side catch-event tests, not an authenticated-client demonstration that vanilla produces a bite in every possible pool layout. Vanilla bite/catch behavior is unchanged.
- Build: `nerdsmp-paper/build/libs/nerdsmp-paper-0.1.0-SNAPSHOT.jar`; release copy `build/releases/fishing-eligibility-20261017/NerdSMP.jar`.
- SHA-256: `EE30914EA33B6E43CA8D5A1F8303A50869AD26160316EBBD5BDB7F8CFF7BFD29`; build and tested jar match.

No player/island data reset, configuration replacement, or main-server installation/restart was performed. Deploy with a normal full restart, not a plugin reloader.

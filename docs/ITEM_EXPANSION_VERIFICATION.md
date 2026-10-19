# Item expansion verification — 2026-10-18

## Artifact

- Build output: `nerdsmp-paper/build/libs/nerdsmp-paper-0.1.0-SNAPSHOT.jar`
- Separate delivery copy: `build/releases/item-expansion-20261018-125424/NerdSMP.jar`
- Size: 4,609,962 bytes.
- SHA-256: `1F7FB4BE61ECD1C43BA5B6B89B47540A5D21BAC8145840D96A36C00B879E7640`
- The build output, delivery copy, and exact jar used by the passing Paper run have matching hashes.

## Automated suite

Command: `gradlew.bat --offline qualityGate build --console=plain`, Java 25.

Result: **BUILD SUCCESSFUL**, 1,371 tests discovered; 1,370 passed, zero failures/errors, one existing `ExpeditionPreviewExportTest` skipped. Log: `build/item-expansion-quality-final.log`.

The expansion adds 37 focused tests: six content tests, twelve signature/loader tests, eleven loot-table tests, and eight smelting-rule/receipt tests. The existing resource-provenance integration test now verifies all four reward channels share one persisted eligibility decision and individually consume it once.

Coverage includes exactly 100 additional IDs; rarity/acquisition totals; eight valid six-piece sets; all ten compatible, drop-only signatures; supported stat limits; thirty recipes without normalized/mirrored collisions; preservation of edited/deleted operator content; malformed files and canonical-ID collisions; weighted rarity and item-bin boundaries; absolute Mythic odds; Fortune quantity preservation; and receipt replay/cancellation/identity rejection.

## Disposable Paper check

Command: `scripts/run-zone-smoke.ps1 -ReleaseProfile`.

- Runtime: `runtime/zone-smoke-20261018-125424`.
- Bound to `127.0.0.1:25579`; separate generated fixture worlds and accounts.
- Paper 26.2 build 121, Java 25; no Bedrock or resource-pack dependency.
- Result file: `PASS`; clean shutdown, exit code zero.
- Log: `build/item-expansion-paper-final.log`.
- 2,051 general `ZONE_SMOKE_PASS` markers plus the separately asserted `ZONE_SMOKE_AUTO_SMELT_COMPLETE PASS` suite.

Actual Paper item factories and serialization verified every new item, socket capacity, texture fallback, recipe registration, acquisition route, signature concealment/identification, workmanship/socket persistence, rejected forged signature metadata, loot identities, and all equipped set totals. Forced deterministic live loot rolls confirmed drop-only selection, sealed drops, three-affix Mythics, and the configured chance boundary. Re-loading the item service left the installed expansion file byte-for-byte unchanged. A material-trimming regression check proved an authenticated Sunforge survives while an ordinary pickaxe is removed by the same vanilla trim rule.

The controlled auto-smelt fixture used real Paper blocks and item entities, with a deliberately injected actor predicate rather than a logged-in client. It verified raw iron/copper/gold and debris conversion; native-result quantities; protected one-tick pickup/merge fencing; placed ore, Silk Touch, named drops, ordinary/unidentified picks, stale events and duplicate events; late cancellation; final tool durability; captured Scholar bonuses; disconnect and shutdown rollback. The resulting Smithing XP was exactly 153 with the fixture's default values. The shared original Mining receipt was explicitly sent through the existing activity-award path, yielding 15 Mining XP, one Mining job work unit, and zero Smithing job units. This proves reward-path compatibility; it does **not** claim native authenticated-client mining was exercised.

The broader harness also passed its existing item-engine, NPC, shop, forge/repair, progression, farming, forestry, dig-site, furnace, auction/store, stack, and generator checks. Expected injected corruption/quarantine logs are part of those negative tests. Windows OSHI performance-counter diagnostics are environmental and did not prevent startup or the passing result.

## Remaining checks and deployment

Authenticated Survival client/visual tests, full inventories, native protection-plugin interactions, load testing, and forced-crash production certification remain manual release gates. The pinned Paper runtime also reports that it is outdated; this expansion does not upgrade the server runtime.

The main player server was not replaced, restarted, reset, or migrated by these checks. Deploy the jar with a full restart. The independent `skyforge-items.yml`, `skyforge-recipes.yml`, and `skyforge-loot.yml` files install when absent. See [ITEM_EXPANSION_IMPLEMENTATION.md](ITEM_EXPANSION_IMPLEMENTATION.md) and [100_ITEM_CATALOG.md](100_ITEM_CATALOG.md) for configuration, effects, crafting routes, and drop odds.

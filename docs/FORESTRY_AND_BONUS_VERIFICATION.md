# Forestry and equipment bonus verification — 2026-10-17

## Artifact

- Build output: `nerdsmp-paper/build/libs/nerdsmp-paper-0.1.0-SNAPSHOT.jar`
- Release copy: `build/releases/forestry-bonuses-20261017/NerdSMP.jar`
- Size: 4,546,302 bytes
- SHA-256: `112189F44533DFE13B52C5A6ADF674E4F5CA762B63DF15F7E549C1C5778561A2`
- The disposable Paper server's installed jar matches this checksum.

## Automated checks

`gradlew.bat --offline qualityGate build --console=plain` — PASS.

1,327 tests: 1,326 passed, one skipped, zero failures/errors. Output: `build/forestry-bonus-quality.log`.

New/regression coverage includes:

- Every supported equipment stat, affix stat, gem stat, enchantment stat, and skill-tree stat has a named consumer/classification. This source-level coverage guard is not by itself runtime proof.
- Explicit global passives stack once; activity-scoped talents do not leak across skills.
- Activity XP composition includes equipment, matching power, skill talents, Adventurer bonuses, island XP, and rhythm exactly once.
- Life steal bounds, reagent-saving thresholds and container exclusions.
- Tree discovery, placed/decorative log exclusions, foliage requirements, trunk footprints, unloaded areas, maximum sizes, level capacity milestones, and Forestry XP rates.
- Schema-4 recipe migration preserves edited/removed old recipes, creates an exact pre-migration backup, and adds only newly introduced Forestry routes. New routes do not collide with existing normal/mirrored patterns.
- New settings default on without changing previous setting values.

## Disposable Paper check

`scripts/run-zone-smoke.ps1` — PASS.

- Runtime: `runtime/zone-smoke-20261017-214512`
- Result file: `zone-smoke-result.txt` contains `PASS`.
- Console output: `build/forestry-bonus-paper.log`.
- Paper: 26.2 build 121; bundled Java 25; isolated loopback port 25579.
- `ZONE_SMOKE_BONUS_PIPELINE_COMPLETE PASS`
- `ZONE_SMOKE_FORESTRY_COMPLETE PASS`
- `ZONE_SMOKE_COMPLETE PASS`, followed by orderly plugin/server shutdown.

The bonus fixture creates real item stacks with improved quality, affixes, two socket gems, and four enchantments. It equips four Stormbound pieces and seeds a cached training profile. It checks actual combined item stats, simultaneous set thresholds, passive contributions, exact stored activity XP, and Paper damage-event calculations including wounded-target Executioner and Hunting multipliers. Ordinary-weapon fallback damage is checked separately.

The Forestry fixture controls native-break dispatch to verify accepted work only, final cancellation, restored eligibility after canceled breaks, partial-tree XP/Marks, tool-change stops, global work budgeting, and transactional one-/four-sapling replanting. Native wear calls are counted by its deterministic substitute; actual Minecraft client axe wear/drops are not certified by this fixture.

Existing integration checks also run, including bulk identify/scrap, station inventories, cooking, gear bonuses, item validation, progression persistence, permissions, markets, island systems, excavation sites, native furnace/hopper behavior, mob stacks, and cobblestone-generator cycles. Existing fixtures deliberately provoke some validation errors to verify fail-closed behavior. Windows OSHI performance-counter warnings are environmental, not a clean-console certification.

## Deployment and limits

No production or main test-server files, inventories, player data, islands, or worlds were replaced/reset. The artifact is built but not installed into the main server.

Back up the server, stop it normally, replace the plugin jar, and perform a full restart. Avoid `/reload` and hot-swapping plugin jars. Existing configs use the new Forestry defaults without replacement; recipe upgrades back up their old catalog.

Remaining staging work: authenticated-client play, visual/menu review, actual native tree drops/durability with the deployed protection plugins, load testing, and hard-crash recovery. Paper build 129 was not used in this run. Fixed quest/collection milestone XP is deliberately separate from activity XP boosts; the bonus audit does not promise every XP/currency grant receives every equipment modifier.

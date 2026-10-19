# Content Studio verification

Verified 2026-10-11 against Paper 26.2 build 121 and Java 25.

## Automated results

- Offline quality gate and packaging: 851 tests, 850 passed, one skipped, zero failures or errors.
- Disposable loopback Paper fixture passed and shut down cleanly.
- Nineteen vanilla-texture item template previews are inert and lack authentic item IDs.
- Published items immediately enter combat and loot catalogs. Socket/enchant/durability metadata survives item revisions.
- Authored two-piece set bonuses contribute real defense through the existing gear profile.
- Item/set content round-trips through a fresh file reader, stale edits are refused, and core item IDs cannot be overwritten.
- Authored mob level, rank, health, labels, XP and default loot profiles integrate with the existing manager.
- Balance edits preserve existing encounter snapshots and affect new spawns. Configured ranger arrow power applies to living targets.
- Encounter loot creates the chosen unidentified gear once; XP and replacement gear require a player-credited kill. Referenced loot profiles cannot be deleted.
- Real Paper editor inventories render in 54-slot layouts. Extraction/drag events are blocked; mob previews create no entities.
- Existing shops, repair, sockets, NPCs, zone spawning, area wand, dig-site renewal, island stacking and generator checks passed in the same fixture.

## Installed artifact

`runtime/skyblock-test/plugins/NerdSMP.jar` matches both the built jar and disposable-tested jar.

`SHA-256: 0EAD2D5155E6DCBB432E44E6A2358391E43618C1D2E8247A7D869F71D25F88B1`

All 26 preexisting plugin-data files remained byte-for-byte unchanged. No live item definition, mob definition, zone, warp or NPC was automatically published. The main test server remains stopped.

Backup: `D:/SMP/Plugin/runtime/backups/content-engine-update-20261011-111400`.

Fixture: `D:/SMP/Plugin/runtime/zone-smoke-20261011-111110`.

Logs: `build/content-engine-final-quality.log`, `build/content-engine-paper-smoke.log`, `build/content-engine-deploy.log`.

## Remaining manual verification

This is not a real two-client editor journey, an ability-telegraph combat playtest, or a concurrency/load test. Staff should complete the manual checklist in [CONTENT_ENGINE.md](../CONTENT_ENGINE.md), particularly private text prompts, simultaneous editors, and Pulse/Heal Self combat behavior. Existing Windows performance-counter warnings precede plugin startup and are unrelated to this update.

Item level is presentation/balancing metadata, not an equip gate. Published content does not automatically create a crafting recipe, shop offer or spawn source. Those connections remain explicit staff choices. Existing mobs preserve their default loot-profile ID; edits to that profile's entries still apply at defeat.


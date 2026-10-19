# RPG zones and island stacking verification

Verified on 2026-10-11 using Paper 26.2 build 121 and Java 25.

## Results

- Offline quality gate and build passed: 804 tests, 803 passed, one skipped, zero failures or errors.
- Disposable loopback Paper fixture passed and shut down cleanly.
- Verified routed staff loot commands, atomic file reload, live material-registry rejection, zone-over-mob precedence, vanilla/custom mob fallback, separate unidentified gear identities, real purse metadata and hopper protection.
- Verified same-chunk island merges, combat-stat mismatch, named/inactive/canceled/staff-created/livestock exclusions, queued saved-count convergence, corrupt count handling, and one real death removing one member while preserving the successor's level and health.
- Existing shop, repair, socket, area-wand, NPC, zone-spawn, renewable dig-site and starter-generator integration checks also passed.

## Installed build

`runtime/skyblock-test/plugins/NerdSMP.jar` matches the built and disposable-tested jar.

`SHA-256: 81F01C5E5F724113737A69B2F5AC9EB88FDDED6287880A456E2A784E972DF284`

All 26 preexisting NerdSMP data files are byte-for-byte unchanged after staging. No production world, NPC, warp, encounter or loot assignment was created. The normal test server remains stopped.

Backup: `D:/SMP/Plugin/runtime/backups/rpg-zones-update-20261011-103800`.

Disposable fixture: `D:/SMP/Plugin/runtime/zone-smoke-20261011-103539`.

Logs: `build/rpg-zones-final-quality.log`, `build/rpg-zones-paper-smoke.log`, `build/rpg-zones-deploy.log`.

## Limits

This is an integration fixture, not a real two-client journey or a concurrency/load test. The existing Windows performance-counter warnings precede plugin startup. The stack death-to-successor handoff is deferred by one tick and is not transactionally crash-proof. Profile replacement affects physical rewards owned by this plugin; other plugins must not add conflicting post-handler rewards.

Authoring workflow: see [RPG_ZONES.md](../RPG_ZONES.md).


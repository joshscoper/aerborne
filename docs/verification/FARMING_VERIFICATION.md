# Farming update verification

Verified and staged on 2026-10-12 using Paper 26.2 build 121 and Java 25.0.4.1.

## Automated checks

- Offline quality gate and build passed: 1,033 tests, 1,032 passed, one skipped, zero failures/errors.
- Rules cover crop maturity, non-crop exclusions, seed reservation, metadata preservation, cocoa/berry/flower/pitcher handling and the new XP categories. Old saved configs receive the multiplier without replacing configuration or skill curves.
- Public-farm protection tests cover mature/immature plants, both pitcher halves, restricted replant transitions, revoked flags and owner rights.
- Growth provenance tests cover recurring fruits/stalks/berries, canceled or filtered fertilization, player replacements and unloaded chunks. Reading eligibility cannot renew a placed block.
- The disposable Paper fixture passed actual field-crop drops and replants, one committed harvest per ripe plant, no repeat harvesting while young, offhand rejection, cancellation rollback, stale-tool refusal, cocoa facing, planted berry/vine retention, special seed refusal and two-block pitcher replanting. Runtime XP defaults match the catalog.
- The same fixture passed the existing chat, studio, item, mob, shops, repairs, NPC placement, zones, dig sites, island machinery, stacking and fluid-generator checks.

## Installed artifact

Built, tested and installed jar:

`SHA-256: 37725063617C006235C5419F388C2E430CD6ABC668148C4AE1FF8E9132C79ECB`

Fixture: `D:/SMP/Plugin/runtime/zone-smoke-20261012-143742`.

Logs: `build/hoe-harvest-final-quality.log`, `build/hoe-harvest-paper-smoke.log`, `build/hoe-harvest-deploy.log`.

Backup: `D:/SMP/Plugin/runtime/backups/farming-update-20261012-143822`, including the prior jar, all plugin data, the complete world and server settings. Original and backup hashes match.

All 26 original plugin-data files and 87 world files remain byte-for-byte unchanged; file counts are unchanged. No world reset, progress migration or configuration refresh was performed. The main test server remains stopped.

## Manual checks still needed

Use an actual Java client to harvest each crop, inspect the Farming action bar and Adventurer/island progress, and test native hoe durability, Preservation, yield/rhythm bonuses and native bare-hand berry XP. Test public-farm and co-op permissions with two real accounts, canceled replanting with special seeds, and a nearly broken hoe. Confirm mature torchflower/pitcher seed costs and legitimate repeat harvests after growth/restart.

The fixture uses controlled player proxies and a counted durability callback; it is not a connected-player XP/Marks journey, a real native durability playtest, or a multiplayer load test. Expected injected failures and preexisting Windows performance-counter warnings appear in the logs. Existing persistence guarantees are not expanded into a full multi-service transaction by this change.

Player guide: [FARMING.md](../FARMING.md).

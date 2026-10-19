# Code review: September 2026

## September 29 Skyblock follow-up

The subsequent [release-hardening record](HEARTHSKY_RELEASE.md) supersedes the
in-memory player-trade findings below: preview-only offers and a two-party recovery
journal are implemented, with replay/save-failure tests. Live crash and multiplayer
acceptance remain open. It also fixes canonical social persistence, private co-op
chat, request/teleport consent, session-bound login and the recursive social button.
Legacy remote server orders are disabled in Skyblock in favor of city NPC vendors.
Other listed inventory/economy and content blockers remain unresolved.

The [Hearthsky implementation record](HEARTHSKY_PROGRESSION.md) covers island XP
and talent persistence, loaded-chunk automation, city-only dynamic vendor pricing,
durable vendor recovery boundaries, tooltip/HUD polish and the approved full local
test reset. `qualityGate build` passes **440 tests**. Paper/Nexo startup, command
diagnostics, new-template block checks and orderly shutdown passed; no players
joined. Full Java/Bedrock gameplay, destructive crash drills and farm load testing
remain outstanding. Original SMP data is unchanged; the isolated reset is backed up.

## Scope

This pass concentrated on land/territory protection, account persistence, bank and
trade inventories, starter grants, collection rewards, NPC conversation lifecycle,
and harvesting progression. It includes implemented fixes and regression tests,
not a claim that every gameplay system has been exhaustively verified.

## Changes

- **Homesteads:** named territories, persistent residents and invitations, scoped
  roles, future-plot membership, private plot overrides, GUI map/rules/residents,
  connected release checks, per-dimension expansion, reserves and staff bypass.
- **Protection:** direct and indirect grief routes now check ownership boundaries;
  cooperative machinery still works inside one homestead.
- **Persistence:** player YAML mutations publish after successful disk writes,
  malformed files fail closed, and claim ownership conflicts stop loading.
- **Bank and trades:** authoritative views, linked-account locks, safe inventory
  routing, stale-view rejection, full-inventory checks and invitation validation.
- **Collections:** recoverable reward receipts, fixed advertised XP, identity-bound
  turn-ins and inventory restoration on save failure.
- **NPC dialogue:** stale/closed/offline/relinked conversations cannot deliver late
  menu actions; concurrent choices are guarded.
- **Progression:** planted mature crops earn XP on each harvest; non-crop Ageable
  blocks and repeatedly hooking entities no longer grant farming/fishing XP.
- **Capacity purchases:** synchronous save failures reach compensation, purchases
  serialize per account, and failed refunds are not reported as successful.
- **Graves:** owner-only inventory access, automation/destruction guards and
  owner-plus-expiry checks prevent stale records taking a replacement grave.

See [Homesteads](HOMESTEADS.md) for player commands, migration and staff configuration.

## Still required before public release

1. **Market live acceptance and recovery drills.** The separately approved durable
   rewrite is implemented; see [Durable market](DURABLE_MARKET.md). Real inventory
   transfers, staff resolutions, PostgreSQL and Java/Bedrock journeys still require QA.
2. **Crash-safe inventory transfers.** Trades, starter delivery and bank/economy
   updates cross separate persistence boundaries. Claim capacity likewise needs
   reconciliation for an abrupt crash between debit and the claim save.
3. **Resource provenance live QA and recovery.** Shared chunk-persistent markers
   and verified tree renewal are implemented; see the progression review. Legacy
   placements, map-reset integration and crash/rollback reconciliation remain open.
4. **Live multiplayer and parity QA.** Java/Bedrock GUI layout, claim flags and
   multi-player grief attempts still require in-game testing.
5. **Load and recovery exercises.** Synchronous YAML writes need tick profiling;
   successful unit tests are not evidence of production load or backup recovery.
6. **Grave lifecycle and capacity.** The remote-recovery index is memory-only and
   a barrel's 27-slot limit can spill excess death items without grave protection.

Details: [Economy review](economy-review-2026-09.md) and
[Progression review](PROGRESSION_REVIEW.md), plus
[Grave review](GRAVE_REVIEW.md). Treat these as release blockers, not
completed features. The market rewrite has no old on-disk listings to migrate.

## Latest follow-up verification

The September 29 island showcase/optimization build has **413 passing tests**.
It adds bounded, per-viewer display visibility; guaranteed chapter cosmetics;
indexed island lookups; an invalidation-safe protection cache; unchanged HUD update
suppression; and no-op/failed-save safeguards for preferences and skill proofs.
See [showcase verification and load-test requirements](SKYBLOCK_SHOWCASES.md).
This is not a claim of improved TPS under player load or resolved world-save I/O.

### Previous Skyblock foundation verification

The September 28 hybrid RPG Skyblock foundation has **395 passing tests**. It adds
an isolated test runtime, protected co-op islands, persistent skill-led chapters
and tiered generators. Startup/restart checks caught and fixed a Paper 26.2
dimension-path bug. The original SMP runtime remains unchanged. See the
[Skyblock verification record](SKYBLOCK.md#verification---september-28-2026)
for the final artifact, observed save-I/O stall and remaining live/content gates.
Shared RPG expedition zones, the floating city and a complete resource progression
path are not implemented in this first pass.

### Previous settlement verification

The September 26 settlement expansion has **356 passing tests**. The staged
artifact passed local Paper/Nexo startup, command registration and clean shutdown;
all six backed-up player save files remained unchanged. See the
[Homesteads verification record](HOMESTEADS.md#verification--september-26-2026)
for the artifact hash, backup location and outstanding live acceptance checks.
Private residential plots, land-use presets, recruitment, notices, bounded history
and private settlement chat are implemented; shared treasuries, leadership
transfer and nations/outposts are not part of this pass.

### Previous harvest-protection verification

The September 24 harvest-protection build has **314 passing tests**. Its staged
artifact, local Paper/Nexo startup, command registration and clean shutdown passed;
all backed-up player save files remained unchanged. See the
[progression verification record](PROGRESSION_REVIEW.md#verification--september-24-2026)
for the artifact hash, backup location, scope and remaining live acceptance checks.

## Original Homesteads verification (before the market follow-up)

The September 13 market build had 261 passing tests and a different staged artifact.
Its corrected-version startup, approved Nexo upload and clean shutdown passed; see the
historical [market verification record](DURABLE_MARKET.md#implementation-verification--september-13-2026).
The results below describe the earlier Homesteads build only.

- `gradlew.bat --no-daemon qualityGate build`: passed, **207 tests**, zero failures,
  errors or skipped tests.
- `run-test-server.cmd -PrepareOnly`: passed; staged jar SHA-256 matches the
  built artifact: `83AE8D046615ECBB8DF8405C3C6C70D89496FB292CCFA23F436A6BE1D8A49D6B`.
- Local startup on Paper 26.2 build 121 / Java 25: NerdSMP and Nexo enabled;
  Nexo generated its pack; `help claim`, `homestead` and `territory` resolved.
- Clean shutdown with exit code 0. No players joined during the smoke test.
- Existing player-data file hash matched the pre-deployment backup afterward.
- Startup logged a Windows OSHI/Perflib performance-counter error before plugin
  enablement. No NerdSMP initialization or shutdown exception was observed.

The test server is stopped. Start it with `run-test-server.cmd`.
The previous jar, NerdSMP data and server properties were backed up to
`runtime/review-backups/homestead-20260913-085823/`.
No world wipe, account reset or market migration was performed.

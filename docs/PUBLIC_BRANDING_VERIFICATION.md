# Public branding verification — October 18, 2026

## Final artifact

- Build: `nerdsmp-paper/build/libs/nerdsmp-paper-0.1.0-SNAPSHOT.jar`
- Release copy: `build/releases/public-branding-20261018/NerdSMP.jar`
- Size: 4,550,970 bytes
- SHA-256: `EB18ABABD22D019579E92291765EAEACE6EE46E2273EED4802091D0541D9DB10`
- The jar installed in the final passing disposable Paper runtime matches this checksum.

## Regression build

`gradlew.bat --offline qualityGate build --console=plain` — PASS.

1,334 tests: 1,333 passed, one skipped, zero failures/errors. Log: `build/release-branding-quality.log`.

New coverage checks consistent two-line text, literal markup handling, bounded/configurable copy, legacy-mode fallback, conservative candidate properties, matching Velocity MOTD text, corrected old stock tips, overflow-safe timing, starter-key equivalence across `memory`/`yaml`, and missing-section defaults without merging or overwriting explicit operator schedules.

## Final candidate-profile Paper run

`scripts/run-zone-smoke.ps1 -ReleaseProfile` — PASS.

- Runtime: `runtime/zone-smoke-20261018-102235`
- Log: `build/release-branding-paper.log`
- Result: `zone-smoke-result.txt` contains `PASS`.
- `ZONE_SMOKE_PUBLIC_BRANDING_COMPLETE PASS`, followed by full `ZONE_SMOKE_COMPLETE PASS` and orderly shutdown.
- The fixture runs pinned Paper 26.2 build 121 / Java 25, using the candidate plugin configuration on isolated authenticated loopback port 25579. Its server properties retain the fixture's one-player/two-chunk limits, not the public profile's 30-player budget.

The fixture verifies listener registration, actual Paper ping-event components, unchanged real player/max counts, the branding opt-out, escaped configured names, actual tab rendering with preserved Adventurer rank/level, unchanged-frame suppression, welcome branding, and nonempty campaign defaults. Existing gameplay, inventory, economy, NPC, auction/store, skill/bonus, Forestry, fishing, furnace, mob-stack and generator fixtures also run.

A separate compatibility run using the existing `deploy/test/config.yml` and legacy `memory` label passed in `runtime/zone-smoke-20261018-101830`, log `build/release-branding-compat-paper.log`. That run used the immediately preceding runtime revision, before the final preflight-review wording change; the final artifact's exact-checksum run is the candidate run above.

The first candidate attempt caught the omitted-playtime-section startup failure and silently empty campaign-default risk. Both were fixed and regression-tested before the passing final run. Existing deliberate invalid-content fixtures emit expected validation errors; Windows OSHI counter warnings are environmental. This is not a claim that every console line is warning-free.

## Scope and remaining evidence

No main-server restart/install, player/island reset, storage-backend migration, permissions grant, public port exposure, or configuration overwrite was performed. Deployment-file changes are templates only. Internal identifiers and saved receipt keys are preserved.

This is server-side component and gameplay verification, not authenticated-client visual acceptance, a live Velocity launch, a multiplayer protocol-status probe, a 30-player load test, store/legal certification, or a forced-crash/restore drill. Paper itself reports the pinned runtime as outdated. The explicit unresolved inventory/content/performance/recovery gates in `HEARTHSKY_RELEASE.md` remain open.

Use [PUBLIC_BRANDING.md](PUBLIC_BRANDING.md) for settings, defaults, proxy/container topology, data-preservation warnings and deployment steps. Keep a consistent backup and fully restart to deploy; do not hot-reload.

# Aerborne rebrand verification — 2026-10-18

## Artifacts

Paper output: `nerdsmp-paper/build/libs/nerdsmp-paper-0.1.0-SNAPSHOT.jar`.
Delivery copy: `build/releases/aerborne-rebrand-20261018-152407/NerdSMP.jar`.

- Size: 4,833,403 bytes.
- SHA-256: `BBF71EE9855095F40A09147B8C312887292D80E429C82B1DD8DF088081E22AE7`.
- The build output, delivery copy and both passing Paper runtime copies have identical hashes.

The optional Velocity artifact is also built and copied into that delivery directory as
`nerdsmp-edge.jar`. Its SHA-256 is
`E0E3FD72E3B49008165F3816871EABC7731EFC1C8E42D8854A96ED1A67D37ED5`.
Velocity itself was not launched; proxy MOTD/template consistency is tested statically.

## Build and automated tests

Baseline: `gradlew.bat --offline qualityGate build --console=plain` succeeded with existing
cached outputs; log `build/aerborne-baseline.log`. Existing Forge code and progress were not
removed or reverted to obtain a baseline.

Final command: `gradlew.bat --offline qualityGate build --console=plain` using Java 25.
Result: **BUILD SUCCESSFUL**, 1,435 tests discovered, 1,434 passed, zero failures/errors and one
existing `ExpeditionPreviewExportTest` skipped. Log: `build/aerborne-quality-final.log`.

Fourteen new presentation/compatibility test methods cover:

- Exact historical stock-name variants, stock-copy projection, literal custom copy, no config writes,
  bounded invalid text, official colors and matching Paper/Velocity MOTDs.
- Unchanged plugin/main/API identity, all-command alias collision detection and existing permissions.
- Actual `PlayerDataStore` reload preserving the fixture's currency, XP, island ownership,
  opaque equipment payload and purchase marker without rewriting its file.
- Existing item schema/enchantment codec/stat values, legacy world-path resolution, and normalized
  SHA-256 guards on historical V001–V004 SQL migration contents.
- Unchanged Watchdog math/default thresholds, panel security defaults and release network topology.
- Staff-console HTML/asset routes, real data-directory hints, SVG colors and scoped contrast checks.

The existing authenticated HTTP panel tests also verify that the actual served HTML, CSS and
favicon contain the new identity. Authentication, CSRF/origin/method protections and no-secret
checks remain in place. Color tests cover primary body text/brand accents, not a full accessibility
or browser visual certification.

An initial new-test compilation failure was corrected by disambiguating a JUnit Boolean assertion;
no production checks were weakened or deleted. The complete suite then passed.

## Isolated Paper verification

Both runs used the same jar, Paper 26.2 build 121 and Java 25. They ran on `127.0.0.1:25579` in
separate disposable worlds/accounts, reusing the recorded owner-approved EULA acceptance.

| Profile/command | Runtime | Result |
| --- | --- | --- |
| `scripts/run-zone-smoke.ps1 -ReleaseProfile` | `runtime/zone-smoke-20261018-152407` | PASS, exit 0; 2,067 general assertion markers |
| `scripts/run-zone-smoke.ps1` | `runtime/zone-smoke-20261018-152544` | PASS, exit 0; 2,087 general assertion markers |

Logs: `build/aerborne-paper-release.log` and `build/aerborne-paper-development.log`.

Paper checked the actual ping component, tab rendering/cache, welcome, legacy-name adapter,
custom literal names, opt-out behavior, plugin/data-folder identity, command alias resolution
and shared frame geometry. The broader harness passed its existing item metadata/serialization,
set/socket/upgrade, shops/repair, NPC, Forge, trade/auction, progression, farming/forestry, ore
smelting, auto-smelt, dig-site, mob-stack and island-generator assertions.

The first release-profile attempt stopped at an existing Forge fixture assertion that assumed
development flags were enabled. The fixture was corrected to test both supported profiles:
release verifies disabled research/imbuement refuse requests without changing inventory or
knowledge and legacy enchantments still work; development retains the existing enabled-feature
assertions. No production feature flags were changed to make the test pass. The failed attempt
is retained in `build/aerborne-paper.log` and its disposable runtime for diagnosis.

Expected corruption/quarantine logs come from intentional negative fixtures. Windows OSHI
performance-counter diagnostics and the Paper outdated-build warning were observed; neither
prevented successful startup, completion or clean shutdown. This rebrand does not upgrade Paper.

## Final reference audit

The final active Java/resources/deploy/assets scan reports only the `/hearthsky` compatibility
alias and the release-check path to `docs/HEARTHSKY_RELEASE.md`. The adapter's regex, regression
fixtures, compatibility documentation, old filenames and historical records are intentional.
No packages, permission/PDC namespaces, SQL identifiers or external endpoints were renamed.
The only repository image asset found in the audited asset/deploy/resource trees was the existing
panel favicon SVG; it was recolored, not replaced by an uncreated logo or Minecraft PNG icon.

## Limits and deployment status

No main player-server jar/configuration was installed, restarted or reset. No existing worlds,
player files, inventories, progress, purchases, credentials or external accounts were migrated.
Tests demonstrate the stated fixture boundaries, not a zero-data-loss guarantee for arbitrary
production failures.

Authenticated-client visuals, live proxy pings, cheat-client/packet testing, browser screenshots,
multi-client inventory cases, production load, recovery drills and real returning-player checks
remain manual. Passing existing Watchdog tests does not certify every existing Arcane effect;
the rebrand changes no anticheat behavior or signature rollout state.

Deploy with a normal full restart and the unchanged plugin data folder. Follow
[AERBORNE_REBRAND.md](AERBORNE_REBRAND.md) for backups, presentation-only proxy merges, rollback
boundaries and external-service/artwork follow-up. Existing public-release gates remain open.

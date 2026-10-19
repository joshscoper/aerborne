# Developer suite verification

Verified on 2026-10-12 against Paper 26.2 build 121 and Java 25.

## Automated checks

- Offline quality gate and packaging passed: 938 tests, 937 passed, one skipped, zero failures or errors.
- Disposable loopback Paper fixture passed and shut down cleanly.
- Studio, loot, world, NPC and recipe commands are routed. Team permission bundles do not grant player moderation or territory bypass; designers retain direct publishing.
- QA reports enumerate real item/quest catalogs and never claim release approval. Deterministic 1,000-roll simulations create no entities or player records.
- Workboard bugs, owners, notes and typed content references survive a fresh load; stale writes are refused. Completed work archives durably, frees active capacity and reserves archived IDs.
- Corrupt optional workboard data leaves gameplay and content validation available while preserving the invalid file for review.
- Authored NPC graphs publish real branches, reputation/level gates and safely escaped text. Service links work at the definition level; publication creates no placement anchors or bodies. Catalog reload and stale-draft protection passed.
- Disabled recipes remain absent from the native registry; enabled recipes register authentic outputs and skill requirements. Public previews have no authentic metadata.
- Recipe gates reject insufficient skill and creative-mode bypass. Automated crafting and modified ingredients are blocked. Failed disk saves restore the old registry and gate; built-in IDs remain protected.
- Shared studio inventories block extraction and dragging. Existing item/mob, shops, forge, NPC placement, zones, wand, dig sites, island stacks and generator checks passed in the same fixture.

## Installed artifact

`runtime/skyblock-test/plugins/NerdSMP.jar` matches the built and disposable-tested jar.

`SHA-256: C63F62B3F3ED75B8F9821BDB3890721EF1AAEA8A1140ACD6220023FB807C8983`

All 26 preexisting plugin-data files remained byte-for-byte unchanged after staging. Worlds were not reset. No live content or placement was automatically published. The normal test server remains stopped.

Backup: `D:/SMP/Plugin/runtime/backups/developer-suite-update-20261012-092200`.

Fixture: `D:/SMP/Plugin/runtime/zone-smoke-20261012-091842`.

Logs: `build/developer-suite-final-quality.log`, `build/developer-suite-paper-smoke.log`, `build/developer-suite-deploy.log`.

## Manual gates and scope

This fixture is not a real two-client GUI journey, a boss ability playtest, a production load test or a backup-restore drill. Follow the manual checklist in [DEVELOPER_STUDIO.md](../DEVELOPER_STUDIO.md) before wider staff use. Expected fixture fault injection and preexisting Windows performance-counter warnings appear in the logs.

Quest, faction and existing shop catalogs have read-only reference views, not new definition editors. NPC authoring links existing contract services and safe stations; it does not introduce arbitrary command execution or financial grant scripts. Reviews are optional team tracking, not publication approval.


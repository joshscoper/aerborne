# Island workshop verification

Verified and staged on 2026-10-12 against Paper 26.2 build 121 and Java 25.0.4.1.

## Automated checks

- Offline `qualityGate build` passed: 999 tests, 998 passed, one skipped, zero failures or errors.
- Legacy stage 0–3 generator ticket results remain identical. New tables total exactly 100 tickets and retain every previously unlocked ore.
- Existing island reads do not rewrite saves. New advanced stages preserve island IDs, coordinates, names, membership, chapter receipts, XP and talents. Co-op certifications, island-level gates, stale-write refusal, malformed-data refusal and failed-write recovery are covered.
- Machine recipe plans consume exact inputs and fuel, preserve metadata-bearing items, refuse partial output capacity and enforce resource unlocks. Index tests cover additive persistence, territorial limits, stale writers, malformed files and failed saves.
- The disposable loopback Paper fixture passed using the same jar that was installed. Real dropper inventories process one bounded batch, respect cooldowns, ignore named ingredients and remain unchanged when world/index identities mismatch.
- Native container handlers allow valid co-op input but reject output insertion, output dragging, out-of-range use, closed sessions, changed tile tokens and same-token replacement of the underlying world container. Hopper transfers, dispensing, piston movement, explosions and automated crafting are blocked.
- Original chapters advance, Foundry and Aetherworks unlock and reload, and advanced generator blocks reform through three real water/lava cycles without consuming source water or lava.
- Existing item, mob, studio, shop, repair, NPC-placement, zone, excavation, island-stack and generator checks passed in the same fixture. NPC chat ownership, replay, expiry, literal text, public command routing and reload invalidation also passed.

## Installed artifact and preservation

The built, tested and installed `runtime/skyblock-test/plugins/NerdSMP.jar` hashes match:

`SHA-256: DDF090112411C8A9C2D63A80925767EB558ADB4BD09D15BD47314B537CA6A9F7`

All 26 preexisting NerdSMP plugin-data files and all 87 world files remained byte-for-byte unchanged after staging; file counts are unchanged. The normal test server is stopped. No island reset, template replacement, NPC placement, content publication or live migration was run.

Backup: `D:/SMP/Plugin/runtime/backups/island-workshop-update-20261012-135400`. Includes the prior jar, complete plugin data, complete world directory (dimensions and inventories included), server settings, SHA-256 manifest and restore notes. Backup hashes were also verified.

Disposable fixture: `D:/SMP/Plugin/runtime/zone-smoke-20261012-140027`.

Logs: `build/island-workshop-final-quality.log`, `build/island-workshop-paper-smoke.log`, `build/island-workshop-deploy.log`.

## Manual playtest gates

1. Start the normal server, open `/island journal`, and check an existing co-op's inherited stage, checklist and generator percentages. Ensure the old land chapters, members, name and talents still match.
2. Train an eligible skill on the island, share its progress, and have another member cover a different requirement. Check unlocked and still-locked stages and recipes.
3. Craft/place all four machines at the appropriate stages. Try ordinary drops and altered items, dragging and shift-clicking, full output, fuel exhaustion, redstone pause and chunk unload/reload. Rejoin after restart and check inventory/index persistence.
4. Have two members open one machine, then empty and recover it. Both views must close, no cursor item may be overwritten, and exactly one machine must be recovered. Test inventory-full and nonempty refusal, outsider access and stale menus.
5. Verify the player-facing journal/workshop layout and NPC chat in an actual Java client. Follow [NPC_CHAT_VERIFICATION.md](NPC_CHAT_VERIFICATION.md) for branch and reward checks.

This is not a two-client network playtest, a 30-player load test, a restore drill or public-release approval. Processing-machine hopper transfers are intentionally unavailable; loading is manual. Windows performance-counter warnings, helper API deprecation warnings and deliberate fault-injection diagnostics remain in the fixture logs. Existing multi-service persistence limitations are not claimed fixed by this update.

Player guide: [ISLAND_WORKSHOP.md](../ISLAND_WORKSHOP.md).

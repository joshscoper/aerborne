# Island activity update verification

Verified and staged on 2026-10-12 with Paper 26.2 build 121 and Java 25.0.4.1.

## Automated checks

- Offline quality gate/build passed: 1,064 tests, 1,063 passed, one skipped, no failures/errors.
- Boundary geometry matches protected chunk edges for every land tier. Rendering samples stay on edges, within range and within the private particle budget. The Paper fixture confirms viewing does not rewrite island progress or change the real world border.
- All thirteen spawner recipes register their actual ingredient positions/costs and correct native entity metadata. New gates, legacy acceptance, modified ingredient refusal, auto-Crafter refusal and trait-safe stack rules are covered. Actual charged creepers and block-carrying endermen remain individual.
- Pond tests cover source/flowing water, depth, cover, lily pads, claim edges, unloaded chunks, owner/co-op/visitor rights and habitat persistence. Journal controls are read-only and bound to their account/island. Canceled, late-canceled and forged-player catch paths leave saved progress unchanged.
- Live furnace and real hopper tests passed: native ore/fuel consumption produces one verified ingot receipt without overwriting inventory, controlled extraction consumes it once, same-tick extraction is supported, old/unverified output cannot turn stored vanilla XP into Smithing work, and hopper output retires credits without skill XP. Smelting canceled after the production MONITOR leaves the input and grants no credit.
- The native smelt event's source becomes AIR/count 0 after its last item is consumed. Receipt verification uses the captured source and actual input/output delta rather than trusting the later live source mirror.
- Existing farming, chat, studio, shops, repairs, NPC placement, zones, excavation, machines, stack deaths and generator fluid cycles also passed in the same fixture.

## Installed artifact and preservation

Built, disposable-tested and installed `runtime/skyblock-test/plugins/NerdSMP.jar`:

`SHA-256: 627CD2395C3E179E32D40EBE0F97EE3D9769464CCABDB4FD7C71AC5E11932D1C`

All 26 preexisting plugin-data files and all 87 world files remain byte-for-byte unchanged after staging; file counts are unchanged. No reset, forced config refresh, live content publication or terrain conversion was run. The player server remains stopped.

Backup: `D:/SMP/Plugin/runtime/backups/island-activities-update-20261012-203027`, including the previous jar, complete plugin data, complete world, settings, SHA-256 manifest and restore notes. Backup hashes match original files.

Fixture: `D:/SMP/Plugin/runtime/zone-smoke-20261012-203012`.

Logs: `build/island-activities-final-quality.log`, `build/island-activities-paper-smoke.log`, `build/island-activities-deploy.log`.

## Manual client gates

1. Toggle `/island border` near each edge and at different build heights. Check only that player sees it, land expansion updates it, and leaving/logout removes it. Check the island menu keeps separate border, talents and fishing controls.
2. Craft/place new spawners before and after their milestone with ordinary and modified ingredients. Stack matching species, recover them, restart and exercise each new mob's combat/death behavior. Playtest creeper explosion continuation and enderman teleport containment.
3. Use two real accounts to fish in valid/invalid private ponds and shared zones. Confirm one catch updates skill, job, streak and the bobber's island once. Test habitat changes, co-op removal and ordinary Ivo turn-ins.
4. Collect ore-smelted output in a real furnace and blast furnace via normal/shift clicks. Check per-item Smithing XP, jobs and island progress, native vanilla XP, mixed input recipes, partial stacks, hopper output, restart persistence and permissions.

The fixture uses controlled player proxies for permission/UI/refusal checks; it does not prove an accepted connected-player Fishing/Smithing XP journey, visual appearance, every new mob mechanic, multiplayer performance or crash-restoration behavior. These are manual gates, not claimed passed. Persistent furnace credits are at-most-once; an asynchronous XP-save failure cannot replay retired work. Preexisting Windows performance-counter warnings and deliberate fault-injection diagnostics remain in logs.

Guide: [ISLAND_ACTIVITIES.md](../ISLAND_ACTIVITIES.md).

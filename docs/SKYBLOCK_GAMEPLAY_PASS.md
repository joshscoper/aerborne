# Skyblock gameplay integration — October 2, 2026

## Starting and upgrading

`run-test-server.cmd` now starts Java-only Skyblock at `127.0.0.1:25566`, using `runtime/skyblock-test`. `run-skyblock-test-server.cmd` remains an alias. The legacy SMP runtime is available only through `run-test-server.ps1 -LegacySmp` and stays separate.

Vanilla textures are the default: no client pack download, Nexo, or client mods are required. Item stats, identities, sockets, enchantments and lore still work. Existing NerdSMP model overrides are cleared when inventories are opened, moved or picked up, and on login. Other plugins' model namespaces are not touched. `-Nexo` explicitly enables optional model support and installs a licensed compatible jar from `local-plugins`; default launches park installed Nexo jars recoverably in `optional-plugins`, leaving their data intact.

Never replace the plugin jar while Paper is running. No world or player reset is required. Relic schema upgrades fill missing entries and retain existing definitions, with a `relics.yml.before-v11.bak` backup. Recipe upgrades add only newly introduced recipe IDs and also keep a backup. Malformed or future schemas fail closed.

## Islands and gathering

- Starter template 3 uses one source block and a flowing outlet above the cobblestone-generator drain. Existing world-template manifests remain unchanged.
- Returning to an island repairs the old second water source only when the unchanged starter fixture can be recognized. Owners can also use `/island fixgenerator`. It does not replace player builds, missing lava, obsidian, or an altered generator.
- Rename through the island menu or `/island rename Cloud Orchard`. Renaming is free, owner-only, 3–32 plain-text characters, and preserves ownership, progress and co-op membership. The showcase button no longer covers co-op chat.
- Harvest Rhythm rewards successive eligible mature-crop harvests with a hoe; Timber Rhythm rewards eligible natural/regrown logs with an axe. Chains reach 20 and expire after 12 seconds for crops or 20 seconds for logs. Variety and chain length together add at most 20% skill XP, not Marks. Anti-farm and skill XP caps still apply.
- Right-click ripe crops with any hoe to harvest and replant, without crouching or spending a talent point. Replanting reserves one ordinary planting item from the harvest first, then inventory if necessary. Bumper Crop still improves crop yield; its older crouch-and-break field replant remains available. See [FARMING.md](FARMING.md) for supported crops and increased XP.
- No automatic mass tree-felling or neighboring-chunk terrain scans were added.

## Skill trees

`/skills` now lays out the three branches in consistent columns. Talents show exact prerequisites, point costs, current branch totals and the next upgrade's totals. The overview shows the next talent-point milestone and XP remaining; the confirmation explains that talent choices last for the season.

All 81 saved talent IDs, their effects, six-point skill budgets and existing unlock levels are preserved. The pass improves clarity and connects existing Farming/Forestry progression to gathering; it does not erase choices or inflate every old stat.

## Workshop, kitchen and alchemy

`/recipes` opens a read-only recipe book with Equipment and Cooking tabs and individual crafting patterns. Craft at an ordinary crafting table; the menu does not dispense items. Recipes recheck skill/session eligibility and reject modified ingredients. Automated crafters cannot bypass skill gates.

Four useful additions join the existing sets:

| Item | Requirement | Purpose |
| --- | --- | --- |
| Ironroot Shovel | Smithing 12 | Excavation option in the Ironroot family |
| Ironroot Shield | Smithing 15 | Off-hand defense in the Ironroot family |
| Wildwood Rod | Fishing 20 | Fishing option in the Wildwood family |
| Riftglass Bow | Smithing 35 | Ranged option in the Riftglass family |

Six meals use harvests and cooked ingredients: Orchard Loaf, Miner's Potato, Angler's Chowder, Forager's Stew, Homestead Supper and Warden's Roast. Farming requirements range from 1 to 40; they retain normal food value and provide modest level-I effects. Eating is not an XP or currency source. Effects do not add their durations or amplifier levels together.

Alchemy gains Field Remedy (Alchemy 5: brief regeneration and speed) and Tidewalker Draught (Alchemy 18: water breathing and swimming assistance), alongside the existing traditional potions, poisons, antidote and advanced brews. Brewing plans ingredient consumption, output capacity and returned containers before changing inventory. A missing ingredient or full inventory leaves everything untouched.

## Shared areas

Use your own builds. `/setspawn` defines the public hub, `/setwarp` saves destinations, and `/expedition` presents those destinations. `/zone` defines bounded, weighted, level-authored encounters with exact spawn points. See [manual zone setup](MANUAL_ZONES.md).

Zone processing is bounded, only inspects loaded chunks and never force-generates terrain. Mob cleanup includes ability/combat caches, and cancelled spawns do not consume population capacity. NPC placement remains manual. Existing prototype worlds are preserved; new generated expedition art is off by default.

## Verification and remaining playtest

Run `gradlew.bat qualityGate build` for automated tests. `scripts/run-zone-smoke.ps1` creates a disposable loopback Paper server, reusing the existing owner-approved test EULA acceptance. It checks real zone lifecycles, item models, safe generator repair and repeated fluid regeneration. It never copies player saves or installs its fixture plugin into the playable runtime.

Still perform a Java-client playtest: create/visit an island, mine several generator blocks, rename, inspect all skill branches, harvest/replant with and without permissions, chop regrown trees, craft a meal and set item, brew with a full inventory, and travel between manually built zones. Multiplayer load targets, final visual quality and a complete public-release certification are not established by unit or headless tests.

### Verified handoff

- Full gate: **606 passed, 0 failed, 1 skipped**. The skip is the optional legacy city/grove preview exporter.
- Disposable Paper: zone persistence, safe/cancelled/blocked spawns, authored mob levels, cleanup, vanilla item models, safe/idempotent legacy-generator repair, and three fluid regeneration cycles passed. The cycles produced cobblestone and coal ore without consuming either source.
- Existing Skyblock runtime: initialized with no Nexo dependency, migrated relics/recipes with backups, and shut down cleanly. All three island/prototype world manifests and the existing player YAML were byte-for-byte unchanged.
- The actual Windows PowerShell launcher passed `-PrepareOnly`; both CMD launchers target Skyblock, port 25566. The server is **stopped**, ready for you to start.
- Full pre-upgrade backup: `runtime/backups/skyblock-before-gameplay-20261002-164726/skyblock-test`, with all 790 files checked against `sha256-manifest.csv` in its parent directory.
- Installed NerdSMP SHA-256: `757BC2F568973CC3EED0F3D37221929935E2E52D5E8B748A055643CE522A1004`.

The runtime currently has no saved public spawn, warps or zones. Set `/setspawn` at your arrival pad, `/setwarp <name>` in each public area, and define encounters using the manual-zone guide. Place service NPCs yourself; the plugin does not invent a new hub or populate it automatically.

Paper emits pre-existing Windows performance-counter/OSHI warnings on this host; they did not prevent startup or these checks. The release checker still flags campaign/bank/starter/grave crash boundaries, multiplayer/load/restore tests, and store/permission review as public-launch work. This handoff is a verified development update, not a public-release approval.

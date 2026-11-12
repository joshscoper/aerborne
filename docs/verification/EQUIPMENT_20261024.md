# Character equipment verification — 2026-10-24

## Candidate

`output/equipment-20261024/highcrown-equipment-20261024.jar`

SHA-256: `df7bdb642628b6c49862bf1204a0dac8db9fd6a96a31859740ffd6bbc9c14a43`.

The delivered JAR matches the final native fixture's JAR exactly. It contains the current working tree, including earlier repairs/talent trees and other pre-existing changes. **Not deployed to production.** Feature behavior and operator guidance are in `../CHARACTER_EQUIPMENT.md`.

## Delivered behavior

Six real Minecraft armor/weapon slots plus ten saved accessory slots, a 54-slot character equipment menu, `/equipment` with `/gear` and `/equip` aliases, Character-menu navigation and a Sneak + F shortcut. Standard clients remain supported; the native E inventory remains available. Ten craftable introductory accessories and staff authoring templates/slot selection are included. Extra gear feeds existing live stats and set bonuses. Account equipment survives season-ID changes and accessory metadata survives death/respawn; native armor and weapons retain existing grave rules.

## Automated checks

Final `qualityGate :nerdsmp-paper:shadowJar --offline --console=plain`: **passed**. Paper test results: **1,673 total, 1,672 passed, no failures/errors, one existing skip** (`ExpeditionPreviewExportTest`).

New tests cover durable intent reload, acknowledgements, stale/repeated requests, before/after and partly applied recovery states, divergent inventories/wrong client UUID, failed initial writes, malformed records, native-slot exclusion, layout collisions, accessory authoring persistence, fixed published slots and legacy material inference. Existing content assertions were updated for catalog version 15 and 62 recipes. Python/JavaScript harness syntax and `git diff --check` passed.

An initial full suite reported three expected-to-fix failures: two stale catalog/recipe count assertions and inconsistent exception classification for malformed Base64. These were corrected; the final suite has zero failures. No tests were disabled for this feature.

## Native Paper/Mineflayer evidence

**158/158 assertions passed** on the final candidate, with **28 completed native client clicks** and **44 client window-open events**. The fixture used loopback only, two bots, a converted map and seeded items. Its main character was invulnerable during transaction checks, then subjected to a controlled real death and respawn. This is functional equipment verification, not an earned-progression or balance cohort.

Verified:

- All 16 equipment slots reached the client and accepted native bag clicks. Each transfer emptied its source exactly once and conserved all owned items.
- Ten accessory definitions authenticated to their intended slots, were non-stackable and had registered recipes.
- Live profile values included five accessory Defense, six maximum health, a socketed ring's three-percent XP bonus and both trinket bonuses. Actual maximum player health became 26.
- An authored two-piece accessory set contributed its bonus; duplicate catalog pieces could not inflate stats or set counts. A held accessory and a wrong-slot accessory granted no equipped stats.
- Ordinary lookalike materials, unidentified gear, oversized stacks, duplicate rings and removal of Curse-of-Binding armor were rejected.
- Full bags prevented unequipping while preserving the item exactly. An explicit occupied-slot swap returned the old ring to the same bag slot.
- Socket metadata survived equipping, native swapping, unequipping and re-equipping. A ring left in bags granted no vitality.
- Native number-key, drop, shift and outside clicks did not extract menu copies into inventory, cursor or the world. No click errors occurred. Drag cancellation is covered by the handler, but native drag gestures were not part of this run.
- An interrupted unequip was simulated by persisting its real intent before applying the inventory change. A fresh service completed it, verified the Minecraft player save and acknowledged it once. A normal reconnect restored the recovered item and equipment state.
- Ten accessory records matched a separate disk-store reload. All equipped accessory metadata survived actual death/respawn.
- Stats, Back, Character → Equipment, Close, both command aliases and the native Sneak + F packet reached their intended screens. Close returned to the normal CRAFTING inventory view.

Captured 3 server inventory receipts with item names/materials/lore; these are receipts, **not screenshots**. Paper exited 0, both bots stopped, and task listeners were closed. The bot logged one ECONNRESET after the final successful assertion while the server was shutting down; there were no transaction/click errors. Host OSHI performance-counter warnings preceded plugin startup and were unrelated to equipment.

The earlier 147-check successful run remains under `runtime/equipment-20261024/attempt1`; the final run adds set-bonus, navigation, aliases, shortcut and world/cursor extraction checks.

## Durability model and limits

Accessory state and a transfer intent are committed before inventory mutation. The existing Minecraft player-save verifier confirms a fresh persisted PDC receipt before the pending record is cleared. Failed initial writes do not publish a transfer. Post-intent failures preserve the record and disconnect the player for recovery. Login recovery precedes starter/delivery grants and refuses to overwrite a slot that matches neither recorded endpoint. Session ownership prevents a second linked client from equipping the same account simultaneously.

The tests simulate transaction interruption and reconstruct services; they do not certify sudden power loss, an abruptly killed JVM, every third-party plugin interaction, SQL/cluster inventory coordination or human UI usability. Accessory storage uses the existing local PlayerDataStore even when progression uses JDBC. All new recipes were checked for registration; they were not each crafted in a live crafting grid. Random proc frequencies, the additional power curve, active trinket abilities and a client E-screen replacement are outside these checks.

## Evidence and reproduction

`output/equipment-20261024/validation.json`, `equipment-result.txt`, `equipment-screens.json`, `test-summary.json`, `equipment-evidence.zip` and `checksums.sha256` accompany the candidate. Raw final logs are under `runtime/equipment-20261024/`.

The fixture harness is in `testing/equipment-20261024/`, with `scripts/verification/EquipmentProbe.java`. Setup refuses to overwrite prior evidence. The probe requires its explicit JVM property and loopback binding and shuts down its disposable server when complete. Evidence contains no production credentials or private configuration.

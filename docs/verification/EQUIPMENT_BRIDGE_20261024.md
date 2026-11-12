# Equipment integration verification — 2026-10-24

## Delivered candidate

`output/equipment-bridge-20261024/highcrown-equipment-bridge-20261024.jar`

SHA-256: `b5bcb2030ce134522defbb5e32b59470cc7a92ccacc722a2db7b46f1e1c90139`.

The delivered JAR exactly matches the final native fixture's candidate. It supersedes the first equipment build, includes the current working tree and earlier repairs/talent-tree work, and **has not been deployed to production**. The design/operator guide is `../CHARACTER_EQUIPMENT.md`.

## One equipment workflow

- **E inventory:** Minecraft continues handling ordinary armor and weapon movement. Shift-clicking an authenticated accessory in the same inventory now uses the durable equipment backend. A single slot can swap directly, and a second different ring/trinket fills its empty slot.
- **Replacement choice:** if two different rings/trinkets already fill their slots, quick-equip opens a focused chooser. The incoming item remains in its bag. Each candidate slot shows projected gear-bonus gains/losses, socket effects and gained/lost set thresholds. Left-click chooses; right-click cancels the preview without unequipping the old item. Closing the chooser is also non-mutating.
- **Unique upgrades:** a new roll of an already-equipped unique identity replaces that identity's slot instead of trying to equip a second copy.
- **Live character view:** native armor, durability and hotbar changes update the same open inventory. Equip/unequip actions render in place, and manual selections survive background refreshes until an action consumes them. Native slot-change events are coalesced; a five-tick poll of open screens covers changes without relying on the event's raw-slot mapping.
- **Controls and feedback:** the character screen accepts the shared quick-equip gesture and retains its direct bag-click controls. Sneak + F opens from normal play; the F inventory key closes the character screen. Other containers keep their controls. Equipment feedback uses the existing vitality/action-bar owner, and accessory tooltips describe quick-equip.

No buttons are inserted into real storage or crafting slots. Standard clients remain supported. The character screen is a view/controller of actual armor/hand slots plus the saved accessory slots; it is not a second armor inventory.

`GearSetService.projectedProfile` uses the same gear and set calculation as committed equipment. It also projects the outgoing item into a selected hotbar source when necessary. Preview never mutates equipment. Identical-item swaps return without constructing an invalid no-change transfer. Existing slot, identification, binding, unique-item and save-recovery checks remain authoritative.

## Automated verification

Final `qualityGate :nerdsmp-paper:shadowJar --offline --console=plain`: **passed**.

Paper tests: **1,677 total; 1,676 passed; zero failures/errors; one existing skip** (`ExpeditionPreviewExportTest`). New policy tests cover empty second slots, filled-pair choices, same-identity upgrades, single-slot swaps and invalid non-equipment. Existing recovery, item authoring, content and equipment tests remain enabled. `git diff --check` and Python/JavaScript harness syntax checks passed.

## Native Paper/Mineflayer verification

**179/179 assertions passed**. Recorded **43 completed client clicks**, **1 raw native offhand-key packet**, **32 window-open events**, and **4 inventory receipts**. Zero client/click errors. The loopback-only fixture ran Paper 26.2-121 with two Mineflayer 26.1 bots through ViaVersion/ViaBackwards. Gear and eligibility were seeded, with invulnerability during transaction checks and a controlled actual death/respawn later. This is a functional test.

New integration checks:

- Native E Shift-click equipped ordinary armor through Minecraft's normal path.
- Removing native armor updated the already-open character screen without reopening it.
- A native hotbar-selection packet changed Main Hand in that same screen; native armor durability changes also appeared there.
- E Shift-click equipped an accessory without opening another screen. Native Shift-right-click from character bags also equipped successfully.
- Shift-click from an ordinary chest moved the accessory into storage rather than activating quick-equip.
- A filled ring pair opened a chooser with the source item still in its bag and both old pieces equipped.
- Actual comparison lore showed **Defense +4**, **Skill XP -3%**, and a gained **Probe Jewels 2pc** threshold for the fixture's socketed-ring replacement. The projected profile exactly equaled the profile after committing the other candidate slot.
- Right-click cancelled the preview without unequipping a ring; closing it also preserved all items.
- One chooser click committed the chosen swap and returned the old piece to the original source bag slot.
- Same-identity rolls replaced their existing unique slot directly, and the old socketed item could be restored exactly once.
- E quick-equip rejected sealed gear without moving it. Identical native equipment was a safe no-op with no pending transfer.
- A real window-click offhand-key packet closed the character screen. Original command aliases, navigation and Sneak + F checks still passed.
- All original owned items were conserved across the integration sequence.

The prior equipment regression checks also passed: all 16 slots, real vitality/Defense/trinket/socket consumers, authored accessory sets, invalid gear, duplicate prevention, binding, full bags, metadata-preserving swaps, world/cursor/menu-copy extraction protection, simulated interrupted-transfer recovery, reconnect persistence and actual accessory retention through death/respawn.

The final server exited 0; both bots stopped. Inventory receipts contain names/materials/lore, not rendered Minecraft screenshots. Native drag gestures were not tested. Host OSHI counter warnings preceded plugin initialization and were unrelated to this change.

## Iteration records and limits

`runtime/equipment-bridge-20261024/attempt1` retains a failed fixture assertion that assumed unequipping would deliver into bag slot 9. The actual first empty slot was in the hotbar. The harness now locates the returned item and maps its real inventory slot before sending the click; no production change was needed for that fixture error. `attempt2` retains the successful 177-check intermediate run. The final candidate adds explicit safe right-click cancellation and identical-item coverage.

The native run validates representative real transactions, client packets and projected/committed stats. It does not certify human usability, random proc distributions, capacity under load, a forcibly killed JVM, power-loss durability or third-party plugins rewriting inventory during a transfer. The underlying equipment recovery/storage limits are described in `EQUIPMENT_20261024.md`. Accessories still follow their existing retained-on-death policy; native armor/weapons follow the existing grave policy. No client E-screen replacement or new SQL schema is introduced.

## Evidence

`output/equipment-bridge-20261024/validation.json`, `equipment-result.txt`, `equipment-screens.json`, `test-summary.json`, `equipment-bridge-evidence.zip` and `checksums.sha256` accompany the candidate. Raw final logs are under `runtime/equipment-bridge-20261024/`. Harness/probe sources are in `testing/equipment-bridge-20261024/` and `scripts/verification/EquipmentBridgeProbe.java`. The harness refuses to overwrite evidence; its reset script verifies owned paths and a clean previous shutdown before archiving a run. No production credentials/configuration are included in the evidence archive.

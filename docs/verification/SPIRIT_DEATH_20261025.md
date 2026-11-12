# Spirit death system verification — 2026-10-25

Candidate SHA-256: `02dca2716ca27839d0514dd9125ceea345cf144f9cdaf886943041dad4c9e3ce`.

## Result

- Full plugin quality gate and shaded JAR build passed: **1,718 JUnit tests, 1,717 passed, zero failures/errors, one existing skip**.
- Website application/guide regression suite: **84 passed**.
- Two disposable Paper sessions: **105 server assertions** (26 + 79) and **20 independent evidence checks passed**.
- Two standard Mineflayer clients, **five actual PlayerDeathEvents**, a real stop/start while ghost, four native menu clicks, direct NPC interaction, a client corpse run and a client attack. Mineflayer emitted four death notifications; the fifth, below-world death is verified by the server event, respawn, unreachable-body state, healer confirmation and retained-inventory assertions.
- Both server processes exited with code 0, clients stopped, and PIDs 41696/10848 plus loopback ports 25598/25599 were confirmed stopped/closed.

## Fixture and scope

Paper **26.2-121**, loopback **127.0.0.1:25598**, RCON 25599, existing ViaVersion/ViaBackwards test bridge, and Mineflayer 26.1. The map is the converted Highcrown fixture. Tests seed inventory and build a small walkable platform at Y=160, create one custom sanctuary, then use actual deaths and native client packets. The probe is separately packaged, property-gated and loopback-gated; it is excluded from the deliverable JAR.

This is focused lifecycle/integration verification, not a complete new 70-minute playtest, production deployment or sustained load benchmark. Expedition checkpoint/life implementation remains in its existing service and is covered by the full unit suite; the native fixture directly exercises adventure, island and ordinary-world deaths.

## Verified behavior

**Inventory:** bag and carried cursor diamonds remain conserved exactly once. Curse of Vanishing armor remains equipped with its original name and durability. Main-hand sword, offhand shield, custom ring, native levels and total XP remain through adventure death, restart, corpse resurrection, healer resurrection, island death, ordinary-world death and unreachable-body recovery. Actual death events have keep-inventory/keep-level enabled and zero item/XP drops. No item-storage grave or dropped inventory appears on the platform.

**Spirit:** death respawns at the nearest safe sanctuary. The player becomes a protected, non-flying Adventure-mode ghost hidden from a living observer. Standard client menus, the private healer and body guidance work. Ordinary plugin teleport, equipment/shop/travel commands, opening external containers, native Q item drop and XP-orb pickup are blocked. The orb remains in the world instead of being consumed. Ghost state is read back from the canonical account store.

**Restart and body recovery:** the server fully stops while the player remains a ghost, then starts with the same data. Ghost restrictions and retained gear persist; logging back in does not grant resurrection. A client walks toward the body and invokes corpse resurrection. Survival mode, flight permission, original invulnerability and compass target are restored; health returns to exactly half the actual maximum, without sickness. Natural regeneration is disabled only in the probe so it cannot obscure that immediate health assertion. Compass comparisons use the API's saved block target, not an assumed fractional location.

**Healer:** the client reviews and cancels a healer confirmation, then confirms a subsequent one. The player returns with full health and the persistent sickness deadline. A real client attack ends the short resurrection protection. `/back` reports no previous location after healer resurrection, closing the old death-return shortcut.

**Damage:** the same player's actual damage against a controlled target is **6 while sick and 8 without sickness**. The fixture temporarily clears and restores the actual healer-generated deadline to obtain a control with identical actor and gear. A different control player was found to have a 10% equipment bonus, so that comparison was discarded. Both final damage events are recorded uncancelled in the server log.

**Other deaths:** a private island uses its direct island respawn with gear and XP retained. An ordinary generated world uses normal respawn with retention. A below-world body is marked unreachable and the confirmed healer recovery waives sickness. Equipment menus work again after returning to life. Existing `nerdsmp.admin` permission inherits death recovery controls without requiring a separate grant.

## Persistence and work limits

New unit coverage exercises durable souls, original ability/compass settings, pending resurrection and acknowledgement, linked clients, save failures that must preserve the ghost, malformed records, sickness/protection deadlines, three-dimensional/world-specific range checks, unreachable-body rules, sanctuary atomic writes, navigation direction and legacy guidance migration.

An earlier native attempt exposed unbounded anticheat collision work following a large position correction after login/respawn. That attempt exhausted the fixture's 6 GiB heap and is preserved in the investigation evidence. The final code limits each collision query to 4,096 cells and its cache to 8,192 cells, rejects invalid/overflowing coordinate ranges, and immediately returns for empty height ranges. An over-budget replay is marked uncertain, reset and resynchronized. Spirit entry/exit also receives authorized movement grace. Six focused budget regression tests pass. The final two-session run has no memory exhaustion, watchdog stall, plugin errors or client errors.

## Host diagnostic

Each Paper startup reports the existing Windows OSHI error for missing English Perflib 009 performance-counter names and associated process-statistics warnings. These exact startup diagnostics are recorded separately in validation JSON. There are no other ERROR entries in the successful run. No Windows registry changes were made.

## Reproduction and artifacts

Harness: `testing/spirit-death-20261025/`. Server probe: `scripts/verification/SpiritDeathProbe.java`. `setup.py` creates a new fixture without overwriting an existing directory. Copy the built NerdSMP JAR into `plugins/NerdSMP.jar`, build the isolated probe with `build-probe.ps1`, run `run.py`, then `validate.py`. `run.py` performs both server sessions. `reset-fixture.ps1` verifies that prior processes stopped and archives prior plugin data/results before a repeat. It validates paths inside the fixture before moving anything.

`output/spirit-death-20261025/` contains the exact tested JAR, player/operator guide, validation JSON, server assertions, native menu receipts and a ZIP of logs, harness, unit reports and the earlier anticheat investigation. Production plugin/world files and the hosted website were not deployed or restarted.

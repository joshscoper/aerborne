# Hearthsky — island progression and test reset

September 29, 2026. Hearthsky is the working display name, configurable through
`skyblock.display-name`. This is not a trademark/domain clearance. The technical
plugin name, commands, item namespace and storage paths stay `NerdSMP` so a display
rename does not invalidate items or saves.

## What players can use

- `/island level` or `/island talents`: shared island XP and a three-branch tree.
- `/island machines`: recipes, current machine limits and operating instructions.
- `/island journal`: skill contributions that unlock territory and generator tiers.
- `/quickstart`: the updated starting guide; `/tips` controls feature reminders.
- `/spawn`: the city, where manually placed NPC merchants buy and sell.

The main menu, scoreboard, tab header, quickstart and local server-list description
use the new name. Equipment no longer repeats the generic crafted-item sentence or
shows a `+0%` quality line. Stat labels read `Skill XP`, `Poison Potency`, `Life Steal`,
etc. Percentage units appear with the value, not as part of the stat name. Sealed
equipment directs players to an Identifier rather than promising a remote command.

HUD snapshots now refresh every 20 ticks instead of 100. Skill XP still updates
immediately; an older asynchronous snapshot cannot undo a newer award. Sidebar/tab
packets remain diffed, with only one outstanding snapshot per player. This is a
one-second data refresh, not database polling on every tick. Island XP becomes
visible after its next successful ten-second save.

## Island levels and talents

Accepted skill activity in Survival, on an island where the player is currently a
co-op member, adds island XP. Replayed/rejected activity, reward XP and off-island
work do not count. Membership is checked again when the award completes.

- Maximum level: **50**, starting at level 1.
- Total XP for level L: `200*(L-1) + 50*(L-1)*(L-2)`; level 50 needs 127,400 XP.
- Maximum 25 XP per activity and 10,000 XP per island per UTC day.
- One talent point at each multiple of five levels: ten points at level 50.
- Six talents, three ranks each. Ten points cannot buy all eighteen ranks.
- Only the owner spends points. A separate confirmation explains that choices
  last for the season. Stale confirmations cannot buy another rank accidentally.
- Second-tier nodes require rank 2 in their branch's first node.

| Branch | First talent | Second talent |
| --- | --- | --- |
| Industry | Prospecting: 10% per rank to reroll a stone generator result | Rich seams: 10% per rank to roll an ore again and retain the rarer result |
| Nature | Cultivation: 15% per rank for an extra natural crop growth stage | Nursery: one extra fertilized crop stage per rank |
| Automation | Compact spawners: stack limits of 4 / 8 / 16 | Collection network: 2 / 4 / 8 chunk hoppers; 16 / 32 / 64 items per pass |

Talents never introduce an ore outside the island's unlocked generator tier. These
are earned gameplay upgrades, not store benefits. Island chapters remain separate:
they unlock territory and ore access through the existing co-op skill proofs.

Progress is saved atomically to the owner's `player_data/<uuid>.yml` under
`seasons.<season>.island-development`. XP is buffered for ten seconds and flushed
on orderly shutdown. A failed save keeps the buffer for retry; a hard process kill
can lose the unsaved interval. Talent changes publish only after a successful save.

## Machines and safety

Chunk hopper recipe: a hopper surrounded by eight copper ingots. Its talent must
be unlocked before placement. There is at most one per chunk; unloaded collectors
still count against the island limit. The owner-file index is durable, while the
block marker and optional filter live in the world's block data.

- Sneak + right-click with an item: collect dropped items of that material only.
- Sneak + right-click with an empty hand: clear the filter. Nothing is consumed.
- Ordinary right-click: open its normal five-slot inventory.
- Redstone power pauses chunk collection; the normal hopper output still applies.
- Empty it, then sneak-break to recover it. Its filter resets on pickup.
- Coin purses, owned/protected drops and player-thrown items are not collected.
  Both nearby vanilla pickup and the chunk sweep respect the material filter.
- This is a **drop** filter, not an inventory-transfer filter: normal pipes can
  insert items. Full inventories leave drops in the world; partial transfers leave
  the remainder. Nothing automatically sells items or loads chunks.

Spawner recipe: four iron blocks in the corners, a diamond in the center and four
matching ingredients on the sides: rotten flesh (zombie), bones (skeleton), string
(spider), or blaze rods (blaze). Right-click a placed spawner with a matching one to
stack it. Sneak-break recovers the entire stack. No Silk Touch is required.

Healthy ordinary island hostiles may combine within six blocks in the same loaded
chunk, up to sixteen represented mobs per entity. This includes naturally spawned
adult zombies, husks, drowned, skeletons, strays, spiders, cave spiders and blazes,
as well as the existing crafted spawners. Elites, bosses, zone encounters, named,
modified, damaged, burning, baby, mounted or equipped mobs are excluded (an ordinary
skeleton or stray bow is allowed). Animals and pets remain individual, so breeding,
milking, shearing and unique traits are unchanged. Members must have matching levels,
combat attributes and reward scaling; different encounters never collapse together. A
stack dies one mob at a time: each actual death runs one normal loot/XP roll. Rolled
gear and coin-purse identifiers are never multiplied. The stack count appears in
the existing player-specific, line-of-sight health display. Counts and member combat
attributes persist with the entity. Remaining members preserve their ordinary weapon
and original combat attributes; respawning never rerolls bonus armor. A rapid grinder
does not lose reserved spawner members if its first mob dies before the merge pass.

The sweep checks at most 32 loaded collectors per ten ticks, and samples at most
64 entities per collector per pass with a rotating cursor. Stacking checks at most
32 queued candidates per ten ticks, with a queue bounded at 1,024 and at most 512
entities inspected in a candidate's chunk. Candidates are evaluated after spawn
callbacks finish, preventing canceled spawns or elite conversions from being merged.
Chunk loads recheck compatible saved mobs; no background world-wide sweep runs.
Spawner and managed stacks are capped at 256 represented mobs per destination chunk
per island. Overcrowded natural spawns stay individual rather than being deleted.
Stacks with multiple members do not disappear through ordinary distance despawning.
Machines cannot
be moved by pistons or destroyed by explosions. Placement, stacking and recovery
require co-op membership and existing claim permissions. `/island performance`
reports bounded-work diagnostics to staff. This still needs representative load
testing; chunk entity enumeration and synchronous YAML saves are not free.

## City merchant economy

In Skyblock mode, server shops require the actual NPC within the configured city
radius (`skyblock.market.hub-radius`, default 256 blocks around `/setspawn`), the
player within ten blocks of that NPC, line of sight and the same authenticated
session. Moving away closes the opportunity to trade. Vanilla villager/wandering
trader GUIs are blocked in this mode. Player stalls remain their separate market.

NPCs **do not spawn automatically**. Staff use `/npc definitions`, then
`/npc create <definition>` at the intended city location. Existing shop catalogs
and faction trading requirements still determine who buys/sells each item.

One seasonal, server-wide commodity price is shared across merchants. Buying adds
demand; selling adds supply. Completed units shift a bounded pressure from -2048 to
+2048, producing 50–150% of the catalog rate. Bundle quotes integrate each marginal
unit and expire after 30 seconds. A changed quote requires another click. Reputation
still affects prices, while the selling spread is capped against the cheapest
cross-vendor ask so immediate buy/resell loops cannot profit.

This first version has no finite warehouse inventory or time-based price decay.
Existing per-player daily selling limits remain. Prices and unsettled trades are
stored in `npc-market-<season>.yml`; malformed data fails closed instead of resetting.
The latest 1,000 completed/cancelled records are retained there, with pending records
retained until resolved and payment receipts in the economy ledger.

Transactions reserve the account and commodity, use idempotent Marks operations,
and verify the saved player inventory. Safe payment boundaries recover on startup.
An ambiguous crash during inventory movement is held for staff review instead of
guessing whether to duplicate or remove items. Staff tools:

- `/npc shopreview`: outstanding trades and their phases.
- `/npc shopretry <id>`: retry a safe recovery boundary.
- `/npc shopresolve <id> received|missing|removed|kept <verified reason>`: resolve
  an inventory-boundary case only after checking evidence. Buy resolutions are
  `received`/`missing`; sale resolutions are `removed`/`kept`. This action is logged.

Failed or ambiguous sales may conservatively retain their daily quota. They must
not silently repeat a payout to restore that quota. The Windows test runtime checks
real Paper save receipts, but multi-client trade/crash journeys remain a launch gate.

## Fresh starter island

Template 2 is a roughly 23-block-wide tapered island with a clear landing, paths,
wheat/carrot/potato plots, a pond, sugar cane, cactus, melon/pumpkin, an oak and spare
sapling, workbench, furnace, empty barrel and lanterns. The generator retains the
familiar mining block at `(9,96,4)` in the center chunk; iron bars guard its lava.
It never grants another kit or fills storage during generation.

Generation writes only the supplied chunk data. The template version is recorded
in the island-world manifest. Existing manifests without a version retain template
1, preventing a quiet terrain replacement or mixed old/new templates.

## Approved local reset and recovery

The user explicitly selected **reset all player data and progress**. Only
`runtime/skyblock-test` was reset. A full backup of 752 files was verified by file
count and SHA-256 before moving any targets:

`D:\SMP\Plugin\runtime\review-backups\skyblock-full-reset-20260929-190007-1eee1e9b`

The `snapshot` folder contains the entire pre-reset runtime; `removed` additionally
holds the exact moved targets (0: plugin data, 1: island dimension, 2: vanilla
players, 3: user cache, 4: player bans, 5: IP bans, where present).

Reset: plugin accounts/links, purchases/cosmetics, moderation, balances, skills,
quests, banks, shops, campaign state, starter delivery history, island records and
dimension, vanilla inventory/ender chest saves, statistics and advancements.
Preserved: hub/Nether/End terrain, configurations/content, manual NPC anchors,
public travel destinations, ops/whitelist, licensed Nexo and EULA acceptance.
The original `runtime/paper-test` was not modified.

For another deliberately full local reset, stop Paper cleanly, run
`powershell -ExecutionPolicy Bypass -File scripts/reset-skyblock-test.ps1` to preview,
then append `-Apply` only when ready. The script refuses running servers, non-local
database mode, unexpected world layout and reparse-point targets; it does not
recursively delete data. Do not use it for a production season reset.

To restore: stop Paper, verify this exact backup, move the current isolated runtime
to a new recovery directory, then copy the entire `snapshot` back as
`runtime/skyblock-test`. Restore the whole snapshot, not selected inventory/market
or island records: those states must agree. Keep both copies until verified.

## Verification

- Java 25 `qualityGate build`: **440 tests**, no failures, errors or skipped tests.
- Built/staged plugin SHA-256:
  `72B51342C07378BBE8A4446B42BACA65B0A36EB8B24B9A9F9381B06FC548E7A1`.
- Original SMP jar remains
  `C915A46FE1F558C44C66E584A5755B398B08D5DA15EC04E55BE2FD4555C8DD9D`.
- Paper 26.2 build 121 / Java 25 and Nexo started on `127.0.0.1:25566`.
  The fresh island manifest records template 2 and world UUID
  `02cceb0d-24b5-459d-83b5-5675b0d22c25`. Player YAML and vanilla player saves were empty.
- Live block assertions passed for the landing/headroom, generator/lava guard,
  workstation/storage, crops, sapling, lantern and cross-chunk island edges.
  `/island performance`, `/npc definitions` and `/npc shopreview` resolved. No
  pending vendors or collectors remained from the reset. No players joined.
- Nine temporary chunk tickets were removed and `forceload query` confirmed none
  remained. Paper saved all dimensions and both plugins shut down normally.
- Startup still reports the pre-existing Windows OSHI/Perflib counter error before
  plugin enablement. No NerdSMP initialization, worldgen or shutdown error occurred.
  Paper reported newer builds; this pass intentionally retains the pinned build.
- Tests cover island XP curves/caps/failed saves, talent prerequisites and stale
  upgrades, collector persistence/filter rules, market quotes/spreads/recovery
  phases, template boundaries and no-world-read generation, tooltip labels and
  delayed HUD snapshots. Source-contract tests are not substitutes for live play.

Before public testing: validate Java/Bedrock GUI layout, first join and starter
delivery, cooperative XP, all talent effects, filters/stack loot under real claims,
full-inventory and disconnect merchant cases, crash recovery and loaded farms.
Name clearance, city construction, expedition content and economic tuning remain
separate work. No real-client playtest or 30-player performance claim is implied.

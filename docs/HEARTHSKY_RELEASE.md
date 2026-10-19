# Aerborne: release hardening and remaining gates

This document keeps its historical filename so existing operational links remain valid.

Status: private development build, not approved for public launch. Java-only release work now includes an isolated city/gathering dimension, durable job allowances and season-scoped campaign schedules. Dungeons, final world art, manual NPC placement and remaining inventory crash boundaries are still open. See `RELEASE_HUB.md` for the latest world and migration notes.

## What changed

- `/trade <player>` requests a remote, two-player item exchange across islands or
  worlds. Both players must be in Survival with loaded, distinct accounts, empty cursors and
  trade requests enabled. Blocks and visibility restrictions apply in both directions.
- The 54-slot trade screen has two nine-stack offers. Left-click a stack in your
  inventory to offer it, right-click to offer one, and click your preview to remove
  it. Offers are previews: items stay in the original inventory until both confirm.
- An offer change clears both confirmations and starts a two-second review delay.
  Originals and capacity are rechecked on every confirmation. Closing either review
  window, travelling, dying or disconnecting cancels an uncommitted
  trade without needing to return escrow. Full-inventory swaps can use outgoing slots.
- Both original inventories are saved and verified before the exchange decision is
  journaled. Each participant is then saved and acknowledged separately. Recovery
  happens before starter-kit delivery, even if the other player is offline.
- Friendships and blocks use canonical accounts and persist before publishing the
  change. Legacy platform-UUID social files migrate to schema 2 using known links.
  Blocking removes friendship in both directions and cancels request/reply context.
- Friend, party, co-op, trade and teleport acceptance checks apply their relevant
  privacy controls. Pending party/teleport requests cannot be overwritten to change
  consent or repeatedly extended. Parties reserve invited seats and support
  `/party deny`; leaders can kick or promote known offline members.
- `/island chat [owner]` selects one private co-op channel. The island menu has a
  chat button; `/channel global` leaves it. Membership is checked on each message.
  Visitors and invitees are not members. Removal, relinking or a replaced island
  invalidates selection; the message never falls back into global chat. After a
  reconnect, reselect the private island before sending.
- `/settings` now includes party and co-op invitations. The Community screen's
  self-reopening Social Panel button is replaced by Chat Channels. Quickstart,
  trade help and tips explain the real controls.
- Teleports recheck the original accounts and request privacy during warmup and at
  the final travel event. Hazardous floors/blocks and out-of-border locations are
  rejected. Movement, damage and quitting cancel warmup. Island entry protection
  still decides whether the destination is accessible.
- Login callbacks are tied to a particular player connection. A second linked
  client cannot play the same canonical account concurrently. Accounts with an
  island/co-op membership or an unsettled trade are not considered fresh for linking.
- Skyblock uses `content/skyblock-daily-quests.yml`, preserving contract IDs, actions,
  thresholds and Marks payouts. Descriptions explain generators, grown trees and
  island fishing. Existing SMP daily content remains separate and untouched.
- Skyblock no longer loads or accepts legacy server buy orders. `/jobs fulfill`
  does not remove items; it directs players to the city. `/jobs orders` displays
  the NPC merchant guide. Buying/selling uses the supply-and-demand vendor
  transaction path, accessible remotely through `/shops` or through placed NPCs.
  Work rewards and dailies remain.
- Staff `/nerdadmin releasecheck` reports mode/season, trade holds and missing live
  city NPC anchors, and lists unresolved release gates. It never grants launch approval.
  NPCs still require manual placement. The separate expedition dimension creates terrain and workstations, not NPC anchors or owned player islands.

## State and update procedure

No reset is part of this update. The stable plugin name, namespaces, gear IDs,
account IDs and world names remain unchanged. Only `runtime/skyblock-test` is staged;
the original SMP runtime is not upgraded automatically.

Player trade decisions live in `plugins/NerdSMP/player-trades/<trade-uuid>.yml`.
Completed decisions are archived under `player-trades/completed/`. Include this
directory with player/world data in consistent backups. Keep completed records for
the season and its support retention window; retention cleanup is not automated.
There may be at most 128 unresolved trades. Malformed/conflicting journals prevent
startup rather than discarding exchange state.

Social state lives in `plugins/NerdSMP/social.yml`; settings remain in player YAML.
Friend requests, reply targets, teleport requests, parties and chat selections are
session-only. Co-op membership is durable and separate from temporary parties.
One person may own an island and belong to several co-ops; private chat always
targets one island, never every co-op at once.

Stop Paper cleanly and back up the whole isolated runtime before replacing the jar.
The launcher installs the Skyblock daily catalog only if it is absent. Existing
edited catalogs are not overwritten; copy desired text into the new Skyblock
catalog when migrating an installation with local daily-quest changes.

## Investigating an interrupted player trade

1. Run `/trade review` from staff or console. It lists trade IDs, physical player
   UUIDs and each side's durable acknowledgment. Zero holds is not proof that every
   other economy subsystem is healthy; also check `/market review` and `/npc shopreview`.
2. For an unacknowledged side, login recovery compares the actual inventory against
   the stored before/after snapshots. Before applies the committed result; after
   saves/acknowledges it without giving items again. An acknowledged side is not
   overwritten while its partner is offline, but both accounts are blocked from
   starting another trade until settlement completes.
3. If neither snapshot matches, the player is disconnected with the trade ID and
   the failure is logged. Stop and preserve the journal, world/player saves and logs.
   Investigate other plugins' inventory changes and backup consistency. There is no
   blind force-refund, delete-hold, or replay-delivery command.
4. Do not delete the journal or restore only one inventory. Any rollback must use
   a consistent snapshot of all involved inventories, ledgers, worlds and database
   state, with staff-reviewed reconciliation for later activity.

This is process-crash recovery, not a proven host power-loss durability guarantee.
Player saves and YAML journals are separate files; restore and destructive crash
drills are mandatory before trusting the path with public inventories.

## Release gates still open

| Gate | Required work/evidence |
| --- | --- |
| Renewable resource progression | Every skill, recipe, collection and chapter needs a reachable resource path. Sanctuary requires Excavation 15; the Forgotten Terrace now provides renewable digging nodes, pending live journey verification. Do not grant XP for repeatedly placed dirt as a shortcut. |
| City, zones and dungeons | The new hub and gathering routes require in-game world-art review and manual service/quest/merchant NPC placement. Build and test authored encounters and dungeons; shared gathering alone is not the full RPG experience. |
| Campaign lifecycle | Dates and season-scoped campaign/world unlock state now persist with failed-save protection. Item contribution and reward-delivery boundaries still need transactional recovery. |
| Other inventory boundaries | Bank, starter-kit delivery, claim/mastery purchases and graves retain outstanding crash risks. The grave index/capacity findings in `GRAVE_REVIEW.md` remain open. |
| Economy | Verify durable vendor/player-market operations on real inventories, the newly persistent job allowance/outbox path under load and restart, anti-farm rules, supply/demand tuning and full resource acquisition. Legacy SMP buy orders are disabled in Skyblock, not rewritten. |
| Identity/social | Authenticated Java identities, preserved historical account mappings, failure handling, permissions, muted/vanished players and offline party management require real-client tests. |
| Monetization | Audit all permission grants and products. Remote station permissions must not create paid gameplay advantages; this change does not revise the store or grant Premium. |
| Performance and recovery | Profile tick-bound YAML/player saves, loaded co-op farms, bosses and shops at 30 concurrent players. Restore a consistent offsite backup within the target RTO. No unit test proves this. |
| Java client distribution | Test the Java pack, inventory actions, NPC interactions, displays and menus. Verify authenticated proxy forwarding and private backend/database interfaces. |

## Two-client acceptance checklist

- Trade ordinary, affixed/unidentified, enchanted, socketed and damaged equipment;
  trade partial stacks, identical stacks and full inventories. Confirm no item or
  amount changes are accepted without renewed review.
- Shift/number/double-click, drag, drop, swap hands, close, teleport, die and disconnect
  from each stage. Creative and self-linked trades must fail. Attempt a second trade.
- Kill the test process at each decision/save/ack boundary, restore/rejoin in both
  orders and verify exact item totals. Inject inventory mismatch: expect a hold,
  not a refund, overwrite or duplicate. Keep these drills separate from real data.
- Block, mute, hide or change settings before invite, after invite, during accept
  and during teleport warmup/chunk load. No private channel message may reach global.
- Join two co-ops; select one; send with both clients. Kick the sender and recipient
  independently and retry. Check invitees, visitors, reconnects and linked clients.
- Check `/social`, `/friends`, `/settings`, `/jobs`, `/jobs orders`, `/daily`, city
  merchants and island chat on two authenticated Java clients. Screenshots/visual sign-off
  remain required; source-contract checks only verify wiring.

## Previous verification record (before the Java-only hub update)

- September 29, 2026: Java 25 `qualityGate build` passed **479 tests** with zero
  failures, errors or skipped tests. That is 39 additional tests, including
  randomized item conservation, interrupted journal receipts, failed-write
  publication, account migration, private chat membership and source wiring.
- Built and staged plugin SHA-256:
  `E540ECE3A5681024814D1A7585C252A54A488C361FAEB0E8169EDC5040D55B70`.
- Before staging, the stopped isolated runtime was copied and all **746 files**
  SHA-256 verified under
  `D:\SMP\Plugin\runtime\review-backups\skyblock-release-20260929-195555-85e1d1d3`.
  `snapshot/` is the pre-update runtime; `manifest.csv` records its files.
  `release-smoke.log` preserves the subsequent run. No player/world reset occurred.
- Paper 26.2 build 121 and Nexo 1.28-dev enabled successfully on authenticated,
  localhost-only `127.0.0.1:25566`. The plugin loaded its catalogs and Nexo generated
  and uploaded the existing pack. The pinned Paper build was not upgraded.
- `/nerdadmin releasecheck`, `/trade review`, `/market review`, `/npc shopreview`,
  `/island performance` and `/list` executed without command exceptions. All three
  trade/market review paths reported zero holds. The release check correctly
  identified missing hub NPC anchors and the incomplete content/recovery gates.
- No player joined and no player profiles were created. This was a startup and
  diagnostic smoke test, not two-client trading, visual GUI, Bedrock, load,
  forced-crash or backup-restore acceptance.
- `stop` disabled both plugins, saved all four dimensions and exited **0**. No
  Paper process remains. The pre-existing Windows OSHI/Perflib warning still
  occurs before plugin enablement; there was no NerdSMP initialization/command/
  shutdown failure or watchdog dump in this run.
- The original SMP jar remained
  `C915A46FE1F558C44C66E584A5755B398B08D5DA15EC04E55BE2FD4555C8DD9D`;
  its runtime was not staged or started.

Start the staged build with `run-skyblock-test-server.cmd`. Keep testing private
until the open gates above have evidence and sign-off.

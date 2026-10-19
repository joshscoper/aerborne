# Durable player market

The market now keeps stalls, item escrow, reservations, payment receipts and delivery
state across restarts. It uses one gameplay backend and the configured economy
service (local player YAML or PostgreSQL). Do not share its journal between servers.
Timed auctions use the same item escrow and delivery inbox with a separate bid
journal. See [Player commerce](PLAYER_COMMERCE.md) for the current player workflow.

## Player workflow

- `/market` opens the fixed-price market index; `/store` opens player storefronts.
  The index preserves the original item name and lore. Stores, purchases and
  deliveries are accessible remotely; travel to a physical stall is not required.
- `/market stall` remains an optional physical registration on land where you have
  build and container access. Collect or finish outstanding listings before moving
  it. A missing store registration is created when you first list an item.
- Hold an item and use `/market sell <total-price> [amount]`. Amount defaults to one.
  `/store sell` uses the same listing flow. A confirmation shows the listing fee.
- `/market reserve <id>` holds an available listing for five minutes. Reservations
  are free and do not purchase or deliver anything.
- `/market buy <id>` pays remotely. Only one buyer can check out a
  listing. Once payment starts, the buyer lock no longer expires with a reservation.
- `/market inbox` shows paid purchases and returned listings. Use its collection
  button or `/market collect <id>` anywhere in Survival with enough room for the whole item.
- `/market mine` shows your listings, pending transactions and history.
- `/market cancel <id>` withdraws an unsold listing into your collection inbox.
  Listing fees are not refunded. `/market retry <id>` safely retries pending payments.
- `/market inspect <id>` shows details. Unique short IDs work in player commands;
  tab completion supplies full IDs. Bound cosmetic items cannot be listed.

Purchase completion does not push items into an offline player or drop overflow
on the ground. Closing a menu, disconnecting, or having a full inventory leaves a
paid item available for collection. Purchases cannot be cancelled after payment starts.

## Payment and inventory boundaries

The journal is saved before each externally visible state change:

1. Prepare an immutable item snapshot in `DEPOSIT_PENDING`.
2. Remove the offered amount, save the player's Minecraft inventory and verify a
   fresh save receipt in the player's on-disk NBT. Paper's `saveData()` call alone
   is not proof of success: Paper may log a failure without throwing.
3. Persist `FEE_PENDING`, then debit the listing fee using a stable receipt. Publish
   `ACTIVE` only after payment confirmation. A declined fee returns the item to
   the seller's collection inbox; an unknown result stays pending.
4. Persist `CHECKOUT` with an immutable buyer, attempt ID and tax percentage. Debit
   the gross price, then credit the seller's net amount with separate receipt keys.
   The difference is the tax sink. Tax is `floor(price * percent / 100)`, computed
   without overflowing a long. Repeated confirmations do not repeat either payment.
5. Persist `READY`. For collection, preflight the full inventory, persist a unique
   `DELIVERING` token, insert the exact saved item, and verify the inventory save.
   Only then record `SOLD` (buyer) or `CANCELLED` (seller's returned listing).

Startup automatically retries pending listing fees and checkout payments, including
those for offline accounts. A lost payment response leaves the original receipt
locked for retry. A confirmed insufficient balance permits a new checkout attempt;
an unknown outcome does not release the listing to another buyer.

Inventory files and the market journal are separate commits, not a single database
transaction. Restarting during a deposit or delivery places it in `REVIEW` instead
of blindly creating or refunding another item. Live verification failures also
quarantine the transfer. Disabled player saving, unreadable files, incompatible
player NBT or unsupported storage must fail closed. The save receipt uses the
authenticated Minecraft UUID, while market ownership uses the canonical account.
The verifier uses Paper's `Server.getLevelDirectory()` and the pinned 26.2
`players/data/<minecraft-uuid>.dat` layout, not a dimension's `getWorldFolder()`.
Keep that standard player-save storage available.

## Staff recovery

Permission: `nerdsmp.market.review` (operators and `nerdsmp.admin` by default).
`/market review` is available in game and from the server console. Payment-pending
entries are displayed alongside inventory reviews, but only `REVIEW` entries can
be manually resolved. Do not manually credit a pending checkout: retry its receipts.

Use the server console or an authenticated staff player:

```
market resolve <full-listing-uuid> <resolution> <evidence-based reason>
```

Use a reason of 3-180 characters, the same rule as `/staff` and the Staff Console. Check inventory saves, logs and rollback evidence;
player recollection alone is not sufficient. Quarantine the affected player's
activity while investigating, because they may have moved items since the incident.

| Review | Resolution | Result |
| --- | --- | --- |
| Deposit | `deposit-kept` | Player retained the item; close the listing without another item or fee. |
| Deposit | `deposit-taken` | Item was removed; continue the listing fee and publication flow. |
| Delivery | `delivery-received` | Player received the item; record completion without reissuing it. |
| Delivery | `delivery-missing` | Verify the item did not reach the player; permit a new collection attempt. |

Every resolution records its actor, reason and transaction references. The wrong
resolution can duplicate or destroy value, so these commands are not normal refunds.
If the disk is unwritable, fix storage first and restart cleanly to persist recovery
quarantine. Do not delete or hand-edit the journal to unlock transactions.

## Storage, deployment and limits

- Journal: `plugins/NerdSMP/market/<season-id>.yml`, schema version 1 with additive
  venue metadata. Older listings without that field load as fixed-price stores,
  retaining their item snapshots, owners, payments and delivery state. Item bytes are
  Base64 Paper item snapshots with SHA-256 checksums; lore is not the source of truth.
- Writes use a forced temporary file and atomic same-directory replacement. Invalid
  schema, duplicate keys, broken item snapshots and impossible states stop loading;
  they are never silently replaced with an empty market.
- Back up the journal, canonical `player_data`, Minecraft `world/players/data`,
  account links and the configured economy ledger as one consistent set. Stop the
  server before replacing the jar. Never use `/reload` or edit a live journal.
- Old in-memory listings have no durable source to migrate. This rewrite cannot
  recover items already lost by restarting the old implementation. Return any old
  listings before stopping an older server when possible.
- Changing season ID starts a different market. Settle outstanding items before
  a seasonal wipe; retain the old journal and its matching economy backup for audit.
- Guarantees target process crashes and detected write failures. Sudden power loss,
  damaged storage, external inventory editors and inconsistent backup restoration
  still require reconciliation. Save receipt reads do not promise hardware fsync.
- Journal snapshots and inventory receipts currently involve synchronous disk I/O.
  Profile listing volume, journal growth and p95 tick time before public release.
  This is not evidence of a 100-player performance certification.
- The player-trade journal now records recoverable exchange decisions separately;
  it is not part of one shared database transaction. Bank transfers, starter kits,
  claim-capacity purchases and general `/marks pay` remain separate crash boundaries.

## Required live acceptance checks

Use two authenticated players with real named, enchanted, socketed and stacked
items. Test simultaneous buyers, logout during payment, full inventories, repeated
clicks, every inventory click/drag variant and travelling before confirmation.
Restart with active listings and paid uncollected purchases. Confirm both balances,
tax and exact item metadata afterward. Test disabled saving on a disposable server:
no listing or delivery may finalize without a fresh save receipt. Exercise each
staff resolution against a verified disposable inventory and test backup restore.
Complete the UI journey with two Java clients; a console startup is not multiplayer QA.

## Implementation verification — September 13, 2026

- `gradlew.bat --no-daemon qualityGate build`: passed, 261 tests, zero failures,
  errors or skips. Coverage includes persisted restarts, lost payment responses,
  competing buyers, review resolution, storage faults, >3 MiB journal reload,
  inventory capacity, local economy receipt failures and bounded save-file parsing.
- `run-test-server.cmd -PrepareOnly`: passed. Built and staged plugin SHA-256:
  `8F62040D483DD139946042CBD0AA4DFE5C63DC3D44B0BD4BEF6B4F06AFE07D92`.
- Initial startup exposed the old playerdata-path assumption and correctly refused
  to enable the plugin. That was corrected to Paper's level-directory API and
  `players/data` layout, with three new path regression tests included above.
- After explicit owner approval for Nexo's upload, the corrected jar started
  successfully on Paper 26.2 build 121 / Java 25 at 09:35. NerdSMP enabled and the
  durable market initialized with zero listings, pending payments or reviews.
  Nexo generated and uploaded the resource pack to its configured Atlas host.
- Console checks passed: `help market`, `market review`, and rejection of malformed
  staff-resolution input. No NerdSMP initialization or command exception occurred.
  The server shut down cleanly with exit code 0; no Java server process remained.
  Log: `runtime/review-backups/durable-market-20260913-092645/verified-startup.log`.
- Server stopped; no players joined. Existing canonical player YAML hashes match
  the pre-deployment backup. No account reset, world wipe or listing migration.
- Backup: `runtime/review-backups/durable-market-20260913-092645/` contains the
  original plugin jar, NerdSMP data, server properties and Minecraft player saves.
- Startup still logged the pre-existing Windows OSHI/Perflib counter
  error. The registry was not changed.

Run the existing `run-test-server.cmd` to play, then perform the live acceptance
checks above. This was an empty-market console smoke test; nonempty restart recovery
is covered by automated tests, not a live multiplayer exercise. PostgreSQL and
actual Java/Bedrock inventory interaction were not exercised by this pass.

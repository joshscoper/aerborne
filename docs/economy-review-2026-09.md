# Economy and item safety review

## Implemented fixes

- Bank pages now have one authoritative viewer per account. Reopening a bank closes and commits the old page before constructing a new inventory snapshot. Previously a withdrawal followed by `/bank` could restore the withdrawn item from the stale vault snapshot.
- Each open bank page remembers which slots were writable when opened. A slot purchase can no longer cause its old locked-slot glass icon to be saved as an owned item.
- Bank shift-clicks target storage only; double-click collection and creative cloning cannot extract menu controls. Linked characters cannot simultaneously edit independent copies of the same vault.
- Bank upgrade failures release the in-flight request lock, and successful callbacks do not reopen a bank after the player has left that screen.
- Bank slot payment keys identify the season, account, and purchased slot. Retrying after a failed completion reuses the original payment and accepts a confirmed duplicate instead of charging again.
- Lowering the configured maximum bank capacity preserves and exposes already-purchased slots and their contents.
- Malformed bank storage and starter grant history stop loading with an error instead of silently treating the files as empty and overwriting vaults or granting duplicate kits.
- Accepting another pending trade no longer replaces an active trade session. Starting a trade removes obsolete outgoing requests for both participants; offers remain inventory previews until confirmation.
- Both players must have enough storage for the full trade before either offer is transferred. Cancelling or removing a preview moves no items and creates no overflow drops.
- Bank page navigation and trade confirmation/cancellation run after the inventory click event, with the view/session checked again. Expired invitations and invitations involving an offline, dead, busy, or ignored participant cannot open a trade.
- Claim expansion now permits only one purchase per account at a time. Synchronous and asynchronous capacity-update failures both attempt compensation; a refund is announced only after an applied or confirmed duplicate credit. Offline or replaced sessions receive no stale completion messages.

## Approved market follow-up

The separately approved durable-market rewrite replaces the old volatile listing
store and pay-before-settle command. Listings and exact item snapshots now survive
restart, checkout binds a buyer and attempt before payment, and buyer debits and
seller net credits use separate persistent idempotency receipts. Unknown outcomes
remain locked for retry. Paid items wait in the remote delivery inbox, including offline
buyers and full inventories. Ambiguous inventory mutations require audited staff
review. See [Durable market](DURABLE_MARKET.md) for the workflow and operational limits.

Local economy credit/debit now persist balance and receipt before publishing either
in-memory value. A failed save leaves both unchanged for a safe same-key retry.
The general two-account transfer API is unchanged; market checkout does not use it.

Real multiplayer inventory, disabled-saving, PostgreSQL and backup-restore exercises
remain release gates; unit tests cannot certify those environments.

### Remaining crash boundaries

The old in-memory player-trade escrow has been replaced by inventory previews and a durable exchange decision journal. Offers stay in the original inventory until both players confirm, and interrupted committed exchanges recover before login grants. Real multiplayer and crash-injection checks remain required. Starter kit history is saved before inventory delivery, so a crash between those actions can record an incomplete kit as delivered. Bank snapshots and player inventory saves are separate files, and slot purchases cross the economy/vault persistence boundary. Resolving those remaining boundaries needs durable transfer journals and tested recovery across both economy and Minecraft inventory saves.

Claim expansion likewise still has a crash window between its economic debit and the capacity save/refund. The new compensation flow handles ordinary failures while the process remains alive; it does not make those separate writes atomic or reconcile a process crash. Logs include the account and operation reference when payment/refund outcomes require review.

## Deployment note

The earlier review did not apply this rewrite because it required broader approval;
the owner subsequently explicitly approved it. Old in-memory listings have no
durable migration source. Return any remaining listings before stopping the old
server, and back up world/player inventory data together with the NerdSMP folder
and economy ledger. Never hot-replace the plugin or use `/reload`.

## Manual regression checks

- Deposit and withdraw items, run `/bank` repeatedly with the bank open, and verify every item count after close/reopen.
- Buy a bank slot while moving items in the current page; the new slot must be empty and old items unchanged.
- Try double-click, shift-click, hotbar swaps, and drag operations against storage and menu controls.
- Open the same canonical account's bank from linked identities; only one view may edit it.
- With three players and overlapping requests, accepting another request during a trade must preserve the original session and both offers.
- Fill a recipient inventory before trade confirmation. Neither offer should move, and both confirmations should reset.

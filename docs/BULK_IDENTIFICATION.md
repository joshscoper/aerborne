# Bulk identification and scrapping

## Player workflow

Visit Nyra and choose her identification service. **Arcane Identification** now offers:

- **Identify Held**: reveal the sealed equipment in your main hand.
- **Identify All**: reveal all eligible sealed equipment in your backpack and hotbar, with one combined Marks payment.
- **Scrap All**: permanently destroy eligible sealed equipment in those same slots and receive Arcane Dust instead. No Marks are spent.

The menu works without holding an unidentified item. Each action opens a separate confirmation showing the item count, rarity breakdown and total price or dust return. Canceling or closing the confirmation does nothing. Put sealed gear you want to keep outside your storage inventory before confirming Scrap All; there is no rarity exclusion filter in this release.

The command shortcuts `/identify held`, `/identify all` and `/identify scrap` open these confirmations directly. `/identify` opens the three-option menu. They retain the existing remote-station permission; this update does not make remote stations available to everyone. Nyra remains usable through her in-world service. Creative and Spectator modes cannot process equipment.

Armor slots, the off-hand, the cursor, bank contents, open station inputs and other containers are not included. Identified gear, gems, materials and named vanilla items are never eligible merely because of their name. Equipment needs a valid item definition/material/schema, unidentified marker, unique loot UUID, socket data and valid affix data. Stacked unique equipment is excluded; duplicate UUIDs among quoted gear and its storage inventory are refused.

## Costs and rewards

Identification retains the existing price: **75 Marks + 25 per affix**, per item. Bulk identification is the sum of those individual prices, with no batch surcharge. Identification reveals the existing roll; it does not reroll the item, affixes or sockets.

Scrapping grants the following base Arcane Dust, **plus 1 per existing affix**:

| Rarity | Base dust |
| --- | ---: |
| Common / Crafted | 2 |
| Uncommon | 3 |
| Rare | 5 |
| Epic / legacy Relic | 8 |
| Legendary | 12 |
| Mythic | 20 |

For example, a three-affix Mythic item gives 23 dust. Scrap rewards use the same Arcane Dust item as enchanting, unsocketing and the lapis/amethyst recipe. Scrapping does not award Marks or skill XP.

The operation first removes its quoted inputs from a cloned inventory, merges dust into compatible existing stacks, and then uses available/freed slots. If the complete reward cannot fit, scrapping is refused before consuming anything. Dust never spills onto the ground.

## Configuration and existing installations

The bundled `config.yml` adds `identification.scrapping.dust-per-affix` and `identification.scrapping.dust-by-rarity.<lowercase-rarity>`. Existing configuration files work without edits: all new keys have fallback defaults. Existing base/per-affix identification price keys remain unchanged.

Rates are bounded: price components 0–1,000,000,000 Marks; base dust 1–1,024 per supported rarity; bonus dust 0–64 per affix. Invalid rates fail configuration validation rather than wrapping a price or creating negative rewards.

Nyra's bundled dialogue now mentions batches and scrapping. Operator-authored/installed NPC dialogue is not replaced; an existing dialogue option using `open_identifier` opens the new menu automatically, even if its older flavor text still mentions one held item. No NPC is automatically placed or moved.

## Transaction boundaries and review

Confirmation snapshots all 36 storage slots. Any changed stack, amount, metadata or slot placement invalidates the quote. The entire batch is rechecked before charging and before application. A confirmed debit followed by a changed inventory/session is refunded using a stable refund idempotency key; ambiguous payment errors are not blindly retried or refunded.

Item removal and dust delivery use one exact-snapshot inventory replacement. A plan is single-use; a failed or ambiguous replacement cannot be replayed. Single and bulk identification use the same machinery. Item handling is confined to the server thread; asynchronous economy completions return there and verify the original online player session.

Each account has at most one latest receipt at `plugins/NerdSMP/item-processing/<canonical-account-uuid>.yml`, atomically replaced. It records the operation UUID, action, item UUIDs, cost, dust, time, phase and before/after storage images. These files contain private inventory data and belong in private backups, not public downloads. The in-memory receipt cache is bounded to 128 accounts.

The phases are `PREPARED`, `APPLYING`, then `COMPLETE`, `DECLINED` or `REFUNDED`. Native player inventory data is saved before marking a successful receipt complete. An interrupted pending receipt, malformed file, ambiguous debit/refund or uncertain inventory write blocks further processing for that account and asks for staff review. Startup does **not** reapply items or generate refunds from an uncertain receipt. This is a review barrier, not a claim of atomic transactions across the economy ledger and Minecraft's native player files.

Staff should back up the receipt, inspect its operation and inventory images, compare actual native inventory state and the wallet ledger, and resolve the discrepancy before releasing the hold. Economy keys are `identify:<operation-uuid>` and `identify:<operation-uuid>:refund`. Do not delete a pending receipt or change its phase as a generic recovery step. Any offline repair requires a stopped server, a consistent backup and reconciliation of items **and** Marks. Completed/declined/refunded receipts permit the next batch and are replaced by its new receipt.

## Installation and manual acceptance

Stop the server, back up plugin data and native player inventories, replace the existing NerdSMP jar, and restart normally. Keep only one NerdSMP jar. Do not wipe progress, replace existing content wholesale or use `/reload`.

Before public rollout, use authenticated clients to verify:

1. Nyra and permitted remote shortcuts open the same menu; ordinary players still use Nyra in-world.
2. Identify one and several items; verify the total charge, preserved roll and item formatting. Insufficient funds leaves every item sealed.
3. Confirm Scrap All's rarity/count warning, cancel once, then complete a known batch and compare its dust with the table.
4. Include identified equipment, gems, food, equipped armor/off-hand and a cursor item; verify they are untouched.
5. Move an item or disconnect while a delayed payment is pending; verify no identification and one refund, not repeated credits.
6. Check click/drag/shift/number-key behavior and GUI readability at supported client scales.
7. On a disposable copy only, interrupt a pending operation and verify its receipt holds the account for review rather than automatically consuming, crediting or replaying anything.

Automated coverage and artifact checksums are recorded in `BULK_IDENTIFICATION_VERIFICATION.md`.

# Auctions, player stores and trades

All three systems work remotely. You do not need to leave your island, meet at a
stall or stand beside another player. Use Survival mode with an empty inventory
cursor when moving items. Account-bound items and paid cosmetics cannot be listed.
Prices are in earned Marks; the store does not sell Marks or trading advantages.

## Auction house

Open `/ah` to browse timed auctions, inspect the item and review its current bid,
Buy Now price and remaining time. Your own auctions and the delivery inbox are
available from the menu. Item previews preserve the original name, lore, sockets,
enchantments and other item data.

To list your held item:

```text
/ah sell <starting-bid> [buy-now-or-0] [hours] [amount]
```

The default duration is 24 hours and the default amount is one item. Durations
must be 1–72 hours. Use `0` or `none` for no Buy Now price. Prices must be positive
and no greater than one trillion Marks; a Buy Now price cannot be below the
starting bid. For example:

```text
/ah sell 500 1500 24 1
/ah sell 500 0 12 16
```

Review the listing fee before confirming. The default fee is **10 Marks** and is
not refunded when an auction is cancelled or expires without a bid. The listed
items leave your inventory only through the confirmed deposit flow.

The first bid must meet the starting price. After that, the next bid must exceed
the current bid by at least **5%, rounded up**, with a minimum increase of one
Mark. A current bid of 101 Marks therefore needs at least 107 Marks. Buy Now can
finish the auction at its listed price without using the ordinary bid increment.

Bid money is charged when the bid is accepted, not only at the end. A replaced
high bid is refunded through its saved payment receipt. A pending payment remains
locked until its result is known; clicking again does not intentionally create a
second charge. You cannot bid on your own auction or withdraw an accepted bid.

When time runs out, the highest bidder wins. Buy Now ends bidding immediately
after payment confirmation. The seller receives the sale price minus the default
**5% sales tax**, rounded down to whole Marks. The item goes to the winner's
delivery inbox. With no bids, it returns to the seller's inbox. Sellers can cancel
only active auctions with no accepted bids.

Fees and tax are configurable. The review screen shows the applicable values;
an existing auction keeps its recorded tax rate.

## Player stores

Open `/store` to browse storefronts or `/store <player>` to visit a known player's
store. Stores use fixed prices: there is no bidding. `/store mine` manages your
listings, and `/store search <text>` searches listed items.

Set up your storefront with:

```text
/store name Cloudside Supply
/store description Seeds, lumber and tools from my island.
/store sell
/store sell <total-price> [amount]
```

`/store sell` opens the listing wizard. With a price supplied, it lists the held
item through the confirmation flow; the amount defaults to one. The price is for
the **whole listed bundle**, not each item. Names use 3–48 letters, numbers, spaces,
apostrophes, underscores or hyphens. Descriptions are one plain line of up to 120
characters. Formatting codes and line breaks are not supported.

Fixed-price listings use the same default 10-Mark listing fee and 5% sales tax.
Only one buyer can check out each listing. Unsold items can be withdrawn to the
seller's inbox; the listing fee is not refunded. Once payment starts, the item is
locked to that buyer until payment succeeds, fails definitively or needs review.
Paid purchases cannot be cancelled as unsold listings.

`/market` remains the fixed-price index. Existing stall registrations and listings
are preserved; a physical stall is not needed for remote sales or collection.

## Delivery inbox

Use `/store inbox`, `/market inbox` or the auction menu's inbox button for purchases,
auction winnings, unsold returns and cancelled listings. Collect items from the
menu or use `/market collect <listing-id>`; `/store collect` uses the same action.

The entire saved item stack must fit in your main inventory. A full inventory,
logout or closed menu leaves it waiting; items are not dropped on the ground or
pushed into an offline player. Collection requires a verified inventory save.
Items with an uncertain deposit or delivery are held for staff review rather than
blindly duplicated or refunded.

## Remote item trades

Use `/trade <player>` from any island or world. The other player uses `/trade accept`
or `/trade deny`. Both players must be online, alive and in Survival, with loaded,
distinct accounts, empty cursors and trade requests enabled in `/settings`.
Ignore and visibility rules still apply in both directions.

Left-click an inventory stack to offer all of it, right-click to offer one, or use
`/trade offer [amount]` for a chosen quantity from your held stack. Click your own
preview to remove it. Each player has nine offer slots. Your items remain yours
until both players confirm the same offer. Every change clears confirmations and
starts a two-second review period.

Closing the menu, teleporting, dying, disconnecting or reaching the five-minute
session limit cancels an uncommitted trade. Start trading after you travel; you
never need to travel to the other player. `/trade cancel` also cancels requests.
Committed exchanges use a durable recovery journal. See [Remote trading](REMOTE_TRADING.md)
for the full controls and recovery behavior.

## Progress and operational safety

This update does not reset islands, accounts, Marks, gear or existing listings.
Store branding adds one seasonal player-data section. The market journal keeps
schema 1 with additive venue metadata; old entries without a venue remain
fixed-price listings. Auction records use their own versioned journal and the
existing item escrow/delivery flow.

Staff should back up the complete NerdSMP data directory, Minecraft inventory
saves and the matching economy ledger together before installing the update.
`/market review`, `/ah review` and `/trade review` show unresolved work. Do not
delete journals, manually repeat payments or edit live transaction files to clear
a hold. See [Durable market](DURABLE_MARKET.md) for inventory reconciliation.

Two-client journeys, restart/crash tests and load profiling remain necessary
before public release. Automated checks and a console startup do not replace them.

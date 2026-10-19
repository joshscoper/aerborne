# Remote player trades

Use `/trade <player>` to send an item-trade request from anywhere on the server.
You can be on different islands or in different worlds. The other player accepts
with `/trade accept [player]` or declines with `/trade deny`.

Both players must be online, alive and in Survival, with loaded, distinct accounts,
empty inventory cursors and trade requests enabled in `/settings`. Ignoring a
player or hiding them also prevents trading. A trade awaiting recovery blocks
new trades until staff resolves it; remote access does not bypass these checks.

## Making an offer

- Left-click a stack in your inventory to offer the whole stack.
- Right-click a stack to offer one item.
- Click one of your own offer previews to remove it.
- Use `/trade offer [amount]` to offer a chosen quantity from your held stack.
- Review both sides, then click your own Confirm button.

Offers are previews. Your items stay in your inventory until both players confirm
the same offer. Any change clears both confirmations and starts a two-second
review period. There are nine offer slots per player. Full inventories must have
space for the exchange after outgoing items are removed. Item names, sockets,
enchantments and other metadata are preserved.

Shift-clicking, number keys, double-clicking, middle-clicking, off-hand swapping,
dropping and dragging cannot alter trade previews or move items through the menu.
An interaction cancelled by another plugin is not treated as a trade action.

## Cancelling and recovery

Use `/trade cancel` or close the menu to cancel. Teleporting, dying, disconnecting,
changing to an ineligible state or reaching the five-minute session limit also
cancels an active trade. Start the trade after travelling to your destination;
the two players do not need to travel to the same place. No items move on an
ordinary cancellation.

When both confirmations commit, the server records one durable exchange decision
before changing inventories. Interrupted exchanges are recovered on login before
starter items or other login grants. Staff can inspect unresolved entries with
`/trade review`. Do not delete trade journals to clear an unresolved exchange.

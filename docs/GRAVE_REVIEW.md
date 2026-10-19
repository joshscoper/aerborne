# Grave protection review

Graves are private recovery containers. Claim membership, public storage flags and claim staff bypass do not grant access to another player's grave.

## Implemented protections

- Owner-only opening, clicks and drags, including a session change while an inventory is open. Invalid owner metadata fails closed.
- Automated transfers into and out of graves are blocked, including hoppers and hopper minecarts.
- Existing direct block-break protection remains. Explosions, fire, entity changes, pistons, fluid, placement, vegetation growth and portals cannot destroy or replace grave containers through their normal Paper events.
- Recovery and expiration validate both the stored account and expiry timestamp. A stale record cannot take or delete another grave that later occupies the same block.
- Empty-grave closure removes the matching location record and checks that the current block still represents that grave.

These protections are independent of territory flags. Programmatic world edits by other plugins or staff commands that do not emit normal protection events can still bypass them.

## Remaining lifecycle and capacity limits

- The location index is still held in memory. Although the barrel's owner/expiry metadata survives in the world, the index is not rebuilt after a restart. Remote recovery and scheduled expiry for pre-restart graves need a separate persistence/reconciliation change.
- A barrel holds 27 stacks. A full player inventory, equipment and other death drops can exceed that capacity; the current creation path drops overflow beside the grave. Those overflow drops are ordinary world items and are not protected by the grave container. A larger or virtual grave inventory is required before claiming complete death-inventory protection.
- Grave identity currently uses owner plus expiry timestamp; introducing persistent grave UUIDs belongs with the lifecycle redesign.

## Manual regression checks

1. Die with fewer than 27 item stacks. Confirm the owner can collect everything, while a second player cannot open or break the grave. Repeat inside wilderness, a private homestead and a public-storage homestead.
2. Place hoppers and hopper minecarts beside/below a grave. Confirm neither extraction nor insertion succeeds. Ordinary neighboring storage must continue working.
3. Test TNT, creepers, beds/anchors, pistons, water/lava, tree growth and portal generation against the grave. Its contents must remain available to its owner.
4. Empty and close a grave, then create another at the same location. The previous owner's remote recovery and the old expiry must not affect the new grave.
5. Confirm matching remote recovery and normal timed expiration still work. Expiry intentionally releases the contents as ordinary drops under the existing rules.
6. Separately reproduce the documented restart and over-27-stack limitations before any public release; they are not resolved by this protection patch.

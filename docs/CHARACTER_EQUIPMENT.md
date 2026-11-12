# Character equipment

Implemented 2026-10-24. The server supports standard Minecraft clients, with no required client mod or resource pack. The normal E inventory stays available for crafting and storage. `/equipment` opens the RPG equipment screen; `/gear` and `/equip` are aliases. Character → Equipment and Sneak + F open the same screen. Operators can disable the shortcut with `equipment.sneak-swap-shortcut: false`.

## Slots

| Physical Minecraft equipment | Additional character equipment |
| --- | --- |
| Head, chest, legs, feet | Shoulders, wrists, hands, belt |
| Main hand, off hand | Necklace, back |
| | Ring 1, Ring 2, Trinket 1, Trinket 2 |

There are **16 slots: six native and ten additional**. Native equipment stays in the real armor/hand slots, retaining visible armor, weapon use, normal armor attributes and durability. Accessory icons use vanilla items; shoulders, cloaks and jewelry do not add visible layers to the player model.

## Using the screen

- Shift-click an accessory in your normal E inventory to equip it. Ordinary armor keeps Minecraft's own Shift-click behavior. Chest, workbench and storage transfers keep their normal controls.
- Click or Shift-click a bag item in the character screen for the same quick-equip behavior. Empty compatible slots fill first; a new roll of an already-equipped unique accessory replaces that identity's existing slot.
- Single slots swap directly. If both rings or both trinkets are occupied by different items, the screen asks which one to replace. Hover the highlighted slots for build gains/losses, including sockets and set thresholds. Left-click to replace; right-click cancels the choice. Closing or pressing Escape leaves all items where they were.
- You can still select an occupied slot explicitly before choosing a bag item. The old piece returns to the source bag slot, so a swap works with full bags.
- Right-click an equipped slot to unequip it. One empty bag slot is required; items are never dropped to make room.
- An accessory must be identified, authenticated catalog gear and a single item. Ordinary gold nuggets or amethyst do not become equipment by their appearance.
- Equip only one accessory of a given catalog identity. Two rings and two trinkets must be different items, including when their rolls or sockets differ.
- The menu contains display copies. Number-key swaps, drop clicks, shift-clicks on display icons and drags cannot take those copies. Ordinary bag rearrangement remains available in the normal inventory.
- Change equipment while alive in Survival or Adventure mode, with an empty cursor. Native Curse of Binding remains enforced.

The Stats button opens `/stats`, and Talents opens the Adventurer tree. Changes refresh real player stats immediately. The open screen updates armor, durability and your selected main hand in place; it does not reopen after each equip. Manual selections survive background refreshes and are consumed by a successful equip. New accessories also describe Shift-click quick-equip in their tooltips.

Sneak + F opens the screen from normal play, and the F inventory key closes it. Other container screens keep their existing key behavior. No items or buttons are inserted into the player's ordinary inventory or crafting slots to provide this shortcut.

## Bonuses and existing systems

Correctly equipped accessories contribute to the existing `GearSetService` profile. Defense, vitality, combat modifiers, gathering bonuses, work rewards and other supported stats use their existing gameplay consumers. Bagged or held jewelry provides no equipped bonus. Wrong-slot items and duplicate accessory identities cannot inflate stats or set membership.

Sockets, rolled affixes, quality and custom enchantment metadata are preserved by transfers. Accessory sets count distinct catalog items and use the existing set-bonus rules. Defensive accessories have defensive sockets; offensive accessory definitions use combat sockets, and existing gathering/utility socket rules continue to apply.

Accessories remain equipped through death and respawn. Native armor and weapons retain the server's existing survival/grave rules. Accessory storage follows the account's physical items rather than seasonal talent progression: changing the season ID does not discard equipped jewelry. Trinkets currently grant passive bonuses; this change does not add active trinket abilities or cooldowns.

## Obtaining equipment

Ten introductory accessories are added to the existing authenticated crafting system. Their recipes unlock at Smithing level 5 or 10 and are discoverable with the other custom recipes.

| Item | Slot | Base bonus | Smithing |
| --- | --- | --- | ---: |
| Traveler's Pendant | Necklace | +2 maximum health | 5 |
| Trail Shoulders | Shoulders | +1 Defense | 5 |
| Trail Cloak | Back | +2 maximum health | 5 |
| Copper Bracers | Wrists | +1 Defense | 5 |
| Leather Handguards | Hands | +1 Defense | 5 |
| Trail Belt | Belt | +1 Defense | 5 |
| Copper Band | Ring | +1 Defense | 5 |
| Amethyst Band | Ring | +2 maximum health | 10 |
| Surveyor's Charm | Trinket | +2% geode-find bonus | 10 |
| Tide Charm | Trinket | +1% fishing-luck bonus | 10 |

Each has one socket. These bonuses use existing percentage semantics; a +2% geode-find bonus is not a guaranteed two-percentage-point absolute drop increase. Recipe material costs remain authoritative in `content/recipes.yml`.

Staff can create necklaces, shoulders, backs, wrists, hands, belts, rings and trinkets through the existing item editor and templates. The Equipment Slot field is explicit; it is fixed after publication, like the item material. Copy a published item to a new identity to change its slot. Existing authored files without a slot field still infer native equipment from their material. Catalog version 15 adds missing accessory IDs without replacing operator edits; recipe version 5 adds only newly introduced recipes. Existing socket and quality rules remain available.

## Persistence and recovery

The existing per-player YAML document stores the account's additional equipment under `equipment`, using exact serialized item stacks. Native equipment is never copied into that persistent accessory map.

Each swap is recorded as a durable pending transfer before either inventory slot changes. The server then applies the affected native slot(s), saves Minecraft player data, verifies a fresh persisted PDC receipt using the existing save verifier, and clears the pending record. A failed initial write makes no inventory change. A failure after the intent disconnects the player with the recoverable record intact.

Login recovery runs before starter items and other inventory grants. It accepts an affected slot only if it matches a recorded before/after endpoint; it can complete an interrupted or partly applied transfer without re-granting a second copy. It tolerates item presentation updates through the item updater. If inventory contents have diverged or belong to a different linked client UUID, recovery refuses to overwrite them and asks for staff review. The original items and record remain available for reconciliation; operators should not clear a pending record without checking both inventory endpoints.

All mutations run on the server thread. Normal stat reads use cached accessory stacks and do not write to disk. Item metadata is revalidated by the current catalog when calculating bonuses. A second linked client is already prevented by the existing session system.

This is a server equipment system, not a replacement for the client-owned E screen. A client mod would be needed to replace that screen's layout and controls. The selected implementation keeps standard clients compatible.

See `verification/EQUIPMENT_20261024.md` for candidate checksums, native client evidence and verification limits. Production deployment is separate from building this candidate.

The integrated E-inventory controls and live character view are verified in `verification/EQUIPMENT_BRIDGE_20261024.md`; its candidate supersedes the first equipment build.

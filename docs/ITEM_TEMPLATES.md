# Item tooltip templates

Obtainable items share a consistent tooltip frame within their family: a non-italic item name,
dark dividers, a category label and useful description or existing item instructions. Native
names remain localized. Native names use their rarity color; food keeps its gold name.

Catalog equipment, forge items, geodes, gems, tomes and prepared meals keep their richer existing
templates, including requirements, rolled stats, sockets, quality, effects and chef information.
Ordinary food keeps its PROVISIONS template and configured Well Fed values.

## Families

| Family | Examples |
| --- | --- |
| Building blocks, decoration and storage | Stone, banners, chests, bundles, shulker boxes |
| Crafting materials, fuel and planting | Ingots, coal, seeds, saplings |
| Redstone | Dust, circuits, hoppers and automated crafters |
| Tools, weapons, armor and off-hand | Pickaxes, swords, armor, shields |
| Ammunition, potions and poison | Arrows, native potions, alchemy brews and poison vials |
| Provisions and prepared meals | Native food and named Cooking recipes |
| Skill lamps, machines and spawners | XP bottles, lamp grades, processing machines, spawners |
| Expedition supplies and currency | Puzzle supplies, coin purses and reward tokens |
| Books, music, exploration, consumables and utility | Books, discs, maps, boats, buckets |

An unclassified native item falls back to CRAFTING MATERIAL. Existing custom instructions are
framed rather than replaced. Player-assigned names are preserved. Vanilla attribute, enchantment,
potion, map and other client-provided tooltip details remain available.

## Updating existing items

The item updater applies current templates to player inventories and ender chests on join, real
storage when opened, item drops and pickups, loaded item entities and equipment displays, and
persisted bank, market and auction items when read. Nested shulker boxes and bundles update too.
Inventory changes schedule a coalesced pass, bounded to four players per tick, so grants from other
plugins also receive a template. Plugin menu buttons retain their own interface descriptions.

Crafting, automatic crafting, anvil, smithing and furnace output previews use the current template.
Native cooking recipes are registered with matching output components, preserving their original
keys, ingredients, experience, times, groups and categories. This lets repeated furnace, blast
furnace and smoker outputs merge correctly. Existing furnace inventories update when their chunk
loads; already loaded furnaces update during initialization. Recipe replacements are restored on
plugin shutdown. Restart after installing this build.

## Recipe and metadata compatibility

The shared frame records its own original lore and generated name. It removes only those exact,
validated presentation fields when comparing native ingredients. It preserves real metadata,
including enchantments, durability, potion contents, custom names, custom lore and persistent tags.
Malformed or edited presentation markers are left untouched and do not gain ordinary-ingredient
status. Protected special items remain protected.

Native crafting/fuel checks, replanting, quest delivery, alchemy, recipe tools, vendor presentation,
economy recipe checks and island processing recognize the canonical ordinary item. Quantity and
identity are unchanged. Alchemy compares native ingredients without presentation fields, then
delivers current templates after its atomic inventory transaction.

Presentation updates are idempotent and survive binary item serialization. The system uses native
server item components and needs no client mod or resource pack. No item stats are added to native
materials by this presentation layer.

## Installation

Replace the plugin JAR and restart. Existing items migrate as they are encountered; no player-data
reset is needed. There is no new configuration section. The accompanying complete default config
keeps Watchdog at `alerts-only` with `anti-cheat.ml.autonomous: false`; retain any additional live
configuration customizations.

See the dated verification report for exact coverage and native client checks. The generated
preview uses real exported tooltip text and colors with a local preview font, rather than an
in-game screenshot.

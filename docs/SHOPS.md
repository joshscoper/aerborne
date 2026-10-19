# Shops

Ten merchants cover ordinary island supplies. They sell vanilla items rather than finished relics, rare progression rewards, or upgraded equipment. Buying a tool or weapon gives the same gear presentation used elsewhere in the game.

## Place the merchants

Use `/npc catalog shopkeepers`, select a definition, and click **Place here**. Search by name, title, shop, or stock, for example `/npc catalog shopkeepers sapling`. The shortcut `/npc place <definition>` places one at your position.

NPC storefronts are optional for shopping. Skyblock players can buy and sell through `/shops` from anywhere in Survival, even before merchants are placed. Each shop still uses its merchant definition and faction reputation for pricing and access. NPC definitions do not automatically spawn; place them wherever you want physical dialogue and services. Existing placements remain separate from the stock definitions. Legacy SMP retains its existing merchant access rules.

| NPC definition | Counter | Products | Purpose |
| --- | --- | ---: | --- |
| `mara_venn` | Warden Road Stores | 19 | Food, camp supplies, basic weapons, shields, and leather armour |
| `elian_quill` | Scholar Field Supplies | 12 | Paper, books, inks, glass, and research materials |
| `bram_tallow` | Bram's Expedition Packs | 12 | Scaffolds, ladders, field tools, and expedition supplies |
| `alba_meadow` | Alba's Farmstead & Pantry | 26 | Seeds, crops, food, and replacement water |
| `rowan_ashwood` | Rowan's Timber Yard | 26 | Logs, saplings, greenery, axes, and shears |
| `dain_slate` | Dain's Quarry Counter | 21 | Ore, fuel, mining tools, Arcane Dust ingredients, and replacement lava |
| `petra_mason` | Petra's Building Yard | 30 | Soil, stone, boards, glass, and finishing blocks |
| `ivo_reed` | Ivo's Fishery Counter | 24 | Ordinary fish, fishing rods, and pond supplies |
| `sella_mire` | Sella's Apothecary | 29 | Brewing reagents, bottles, and alchemy equipment |
| `hob_copper` | Hob's Mechanism Workshop | 27 | Plain redstone components, storage, and transport |

There are 226 product entries. Larger counters have multiple pages.

## Player flow

Use `/shops` to choose a merchant, or `/shops sapling` to search by item, shop, or merchant. Selecting a shop opens its live stock and your reputation-adjusted prices. Search matches filter the stock where possible. A shop ID opens that shop directly, for example `/shops quarry_supplies`. No placed NPC, hub location, or teleport is required.

Left-click a product to review a purchase, or right-click it to review a sale. Purchase quantities include **1**, the configured bundle, **16**, and **64**, with duplicates and oversized choices removed. Tools and filled buckets have only a one-item option. Review the total and click **Confirm Purchase** to pay and collect. The entire purchase must fit in your main inventory; a disabled button explains whether you need more Marks or more space.

Selling uses the product's fixed configured bundle, regardless of the selected purchase quantity. The menu shows how many matching items are in your bag and how many sales remain today. Review the payout and click **Confirm Sale** to turn in one matching bundle. Named, enchanted, and improved gear is not buyback stock. Both Buy and Sell stay visible, with a clear reason when unavailable. Quotes last 30 seconds; changed prices need a fresh review. Physical NPC supplies branches open the same trading controls. Player markets remain the place to trade upgraded or player-made gear.

Ivo's ordinary fish counter is separate from his collection appraisal desk. Named catches still use the fishery collections flow. Sella sells ingredients, not ready-made poisons or potions; her alchemy bench remains the place to brew them.

Starter seeds, saplings, water, lava, plain gear, and Arcane Dust ingredients are available at neutral reputation. Each starter recovery purchase costs no more than the initial 250 Marks. Water and lava buckets are source replacements, not island-wide collector upgrades.

## Pricing and buybacks

All prices use earned Marks. The server-wide market tracks each material across merchants: purchases raise demand and sales add supply. Reputation also affects prices. A quote is checked again when the trade starts, so an old menu is not a promise of an expired price.

Ordinary commodities have capped daily buybacks. The displayed sale amount is a bundle; `daily-sell-limit` counts bundles, not individual items, and resets at 00:00 UTC. The buyback price is lower than the corresponding purchase price. Equipment, assembled utilities, and returned containers are buy-only. That includes buckets, bottles, arrows, ladders, scaffolds, campfires, leads, brushes, compasses, spyglasses, beds, and rockets.

Recognised vanilla recipe chains and immediate block conversions have an additional conservative buyback ceiling. This covers ingots, sticks, bone meal, paper, gravel-to-flint, and dirt-to-mud-brick routes when their inputs are cheap and their outputs are in demand. The configured `sell-price` is an upper bound, not a guaranteed payout. Selling resources gathered through normal gameplay remains available.

The market records price pressure; these counters do not pretend to hold a finite chest of physical stock. NPC shops provide starter access and capped surplus sales. Player stalls should provide the better destination for valuable gear and larger trading opportunities.

## Forge repairs

Right-click an anvil to open the forge. Put damaged equipment in the left input, select **Repair Item**, review the Marks cost, then select **Confirm Item Repair**. The equipment stays in that slot; gems, enchantments, quality, names, affixes, and poison coatings remain intact. The right-hand catalyst is not consumed.

**Repair All Equipment** returns both forge inputs before opening a quote for your inventory, hotbar, armor, and off-hand. If there is no room to return the inputs safely, the forge asks you to make space instead. Nothing is charged until you confirm the repair quote.

Changing an input clears its repair review. Closing the window or changing the item before a confirmed repair applies cancels it and refunds a confirmed payment. Ambiguous payment or inventory errors require staff review rather than automatic retries.

## Editing stock

Bundled definitions are in `nerdsmp-paper/src/main/resources/content/npcs.yml`; the installed server copy is under `plugins/NerdSMP/content/npcs.yml`. Schema 10 introduces the expanded shop roster. Existing faction IDs, services, quests, and dialogue flags are preserved.

Each product has a vanilla `material`, bundle `amount`, `buy-price`, `sell-price`, `daily-sell-limit`, `minimum-reputation`, and a short player-facing `description`. Use `sell-price: 0` and `daily-sell-limit: 0` for buy-only products. A sellable product must have a positive bundle limit, and its sale price must stay below its purchase price.

Use the same per-unit base price when a material appears at multiple merchants. Avoid selling nether stars, echo shards, enchanted books, spawners, netherite, finished relics, or island collector upgrades from ordinary shelves. Change rare progression access through its gameplay system rather than adding a shortcut to the shop.

Every merchant needs a reachable `open_shop` dialogue effect for its configured shop. Keep the supplies branch separate from existing collection and station actions. Automated content tests check stock validity, bundle sizes, quotas, starter access, repeated dialogue labels, and shop reachability.

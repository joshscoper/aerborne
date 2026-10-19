"""Player-facing reference copy reviewed against the checked-in gameplay code.

Operational tuning stays in the in-game menus; this is not a snapshot of a remote server's flags.
See CONTENT_REVIEW.md for the source map and verification limits.
"""


def _guide(slug, title, summary, body, category, related='', availability='released'):
    return dict(slug=slug, title=title, summary=summary, body=body.strip() + '\n',
                category=category, related=related, availability=availability)


GUIDES = [
    _guide('first-island', 'Join and create your island',
           'Connect, claim your home and find your first goals.', """
## Join Aerborne
Use Minecraft: **Java Edition**. The [Play page](/play) has the current version, server address and live status. Add the address under **Multiplayer → Add Server**, then join.

## Claim your home
Run `/island create`. Repeating it takes you to your existing island; it does not create another one. Your starter island includes a tree, water, lava, crops and a renewable block generator.

1. Gather wood and make basic tools.
2. Mine the generator's output; keep its water and lava intact.
3. Run `/quickstart` for first-session goals.
4. Open `/island journal` to see your next unlock.

Use `/island home` to return and `/menu` for the main game menus. Falling into the island void returns you safely without losing your items. Ordinary deaths use the separate [grave system](/guide/graves).
""", 'getting-started', 'Island resources | /guide/island-resources'),
    _guide('island-basics', 'Grow your island',
           'Understand resources, shared progress and co-op access.', """
## Three ways your island develops
| Menu | Purpose |
|---|---|
| `/island journal` | Certify trained skills to unlock resources and territory. |
| `/island talents` | Spend shared island talent points. The owner chooses upgrades. |
| `/island machines` | Browse processing machines, spawners and their recipes. |

Skill activity on the island contributes to island XP, subject to activity and daily limits. Machine output and valuable blocks placed on the ground do not replace that work.

## Build with friends
Use `/island invite <player>`. A co-op has **six places including the owner**. You can own one island and join up to **three other co-ops** while keeping your inventory and personal skills.

Members can build and use storage. The owner manages invitations, removals and visitor settings. Islands start private; opening sightseeing access does not give visitors building or storage rights.
""", 'getting-started', 'Co-op and visitors | /guide/island-coop\nResource milestones | /guide/island-resources'),
    _guide('first-gear', 'Upgrade your first gear',
           'Identify sealed equipment, add gems and review each change.', """
## Reveal sealed gear
Visit **Nyra**, the identifier. Sealed gear hides its bonuses until identification. Review the Marks price before confirming. Identification reveals the item's existing roll; it does not reroll it.

## Add a socket gem
1. Right-click an **anvil** to open Forge & Sockets.
2. Put identified equipment in the left input and a compatible gem in the right.
3. Review the result, then choose **Confirm Change**.

The preview shows compatibility, available sockets and the bonus. Moving an input or waiting too long requires a fresh review. Remote `/forge` access needs a separate permission; visiting an anvil works without it.

Two matching gems of the same tier merge into the next tier, up to **IV**. Gems can also be recovered for Arcane Dust. Keep inventory space when using or closing the forge.

Craft **4 Arcane Dust** from **1 lapis lazuli + 1 amethyst shard**.
""", 'getting-started', 'Gear and sockets | /guide/gear-and-sockets\nGem recovery costs | /guide/gem-merging'),
    _guide('first-enchantment', 'Train skills and enchant',
           'Find your skill progress and make your first Arcane upgrade.', """
## Learn through play
`/skills` shows eleven skills: Mining, Forestry, Farming, Fishing, Hunting, Excavation, Smithing, Alchemy, Building, Cooking and Enchanting. Eligible activity earns XP; placing and repeatedly breaking your own materials does not provide an unlimited reward loop.

## Use an enchanting table
Right-click an **enchanting table** for Arcane Enchanting. Inspect the three offers and their Arcane Dust costs, then confirm a compatible upgrade. Successful binds are recorded in your personal **Arcane Codex**, available anywhere with `/arcane`.

Craft 4 Arcane Dust from 1 lapis lazuli and 1 amethyst shard. Keep useful gems for sockets or research instead of treating them as ordinary crafting materials.

## Choose your next upgrade
Research discovers enchantments; Chosen Imbuement applies a discovery at a chosen level. Those actions have station, level and feature requirements. Their menus show what you can use and the current cost before spending anything.
""", 'getting-started', 'Skills and talents | /guide/skills-and-talents\nArcane Forge | /guide/arcane-forge'),
    _guide('beyond-your-island', 'Trade and explore',
           'Find services, travel safely and prepare for public destinations.', """
## Visit the hub—or use a remote menu
`/spawn` travels to the configured server hub. The final city build and public destinations can change as staff develop the world.

| Need | Open |
|---|---|
| Merchant supplies and buyback | `/shops` |
| Timed auctions | `/ah` |
| Player stores | `/store` |
| A confirmed item exchange | `/trade <player>` |
| Public travel destinations | `/expedition` |

Shopping and player commerce work remotely; you do not need to leave your island just to browse. Review fees, prices and delivery information before confirming.

## Prepare before travelling
Keep some inventory space, take suitable equipment and learn [grave recovery](/guide/graves). Make a [party](/guide/parties) to keep expedition chat separate from your island co-op.

The [Explore page](/explore) distinguishes available island features from city and expedition previews. Use the in-game destination list for the routes currently open.
""", 'getting-started', 'Explore the skies | /explore\nChoose how to trade | /guide/trading-safely'),
    _guide('milestone-planning', 'Plan your next milestone',
           'Choose one unlock, share the work and put it to use.', """
## Check before gathering
Open `/island journal`, pick the next unfinished milestone and compare its requirements with `/skills`. The [resource table](/guide/island-resources) lists each stage.

## Share your progress
Co-op members can cover different skills. Click **Share your skill progress** in the journal after reaching a requirement. Certifications stay with the island even if their contributor later leaves.

Unlocks do not consume skill XP, Marks, items or talent points. Some later resource stages also require an island level, so keep doing eligible skill work on the island.

## Use the unlock
Check `/island machines` for newly available recipes. Choose a machine you can feed, leave room for its output and stock ordinary coal or charcoal. Machine production itself does not earn island or skill XP.

Before logging out, share new progress, empty outputs and choose your next requirement.
""", 'island', 'Resource milestones | /guide/island-resources\nMachines | /guide/island-machines'),
    _guide('island-resources', 'Resources and milestones',
           'The skill requirements for generator tiers, machines and land.', """
## Certify your skills
Open `/island journal` and choose **Share your skill progress**. Different co-op members can supply different requirements; saved certifications remain after they leave. Unlocks consume no XP, Marks or items.

| Stage | Requirements | Main additions |
|---|---|---|
| Settler | Starting island | Coal, copper and iron in the generator; Crusher. |
| Homestead | Mining, Farming, Building 5 | Gold and lapis; Compost Bin; first land expansion. |
| Workshop | Smithing, Forestry, Hunting 10 | Redstone; Sawmill; second land expansion. |
| Sanctuary | Fishing, Excavation, Alchemy 15 | Diamonds; Mineral Sifter; third land expansion. |
| Foundry | Island 15; Mining 25, Smithing 20, Excavation 20 | Quartz ore and stronger generator rolls. |
| Aetherworks | Island 25; Mining 35, Alchemy 30, Farming 30 | Emerald ore, occasional obsidian and end-stone sifting. |

The first three expansions increase the protected square from **48×48** to **80×80**, **112×112** and **144×144**. Later resource stages improve resources without adding another land expansion.

Generator output is random. Unlocking an ore makes it possible; it does not guarantee your next block. Island XP comes from eligible skill work done there, not machine output.
""", 'island', 'Plan a milestone | /guide/milestone-planning\nMachines | /guide/island-machines'),
    _guide('island-machines', 'Machines and automation',
           'Craft processing stations and keep their inputs, fuel and outputs clear.', """
## Craft a station
`/island machines` shows recipes and unlocks, and can add recipes to your recipe book.

```text
I I I
C D C
I I I
```

**I:** iron ingot. **D:** ordinary dropper. **C:** the material below.

| Station | C ingredient | Required stage |
|---|---|---|
| Crusher | Cobblestone | Settler |
| Compost Bin | Wheat | Homestead |
| Sawmill | Oak log | Workshop |
| Mineral Sifter | Gravel | Sanctuary |

## Run it on your island
Place the station and right-click it. The **top row** holds ingredients, **middle row** coal or charcoal, and **bottom row** output. Recipes and processing speed depend on the station, resource stage and island talents.

The Crusher processes 8 cobblestone into 8 gravel, or 8 gravel into 8 sand, for 1 fuel each. A full output row pauses processing. Special, named or enchanted items are not consumed as ordinary ingredients.

Automation supplies materials; it does not train your skills. Keep doing gathering, crafting and other eligible island work to level up.
""", 'island', 'Resource milestones | /guide/island-resources\nSpawners | /guide/island-spawners'),
    _guide('island-spawners', 'Spawners and mob stacks',
           'Unlock spawner recipes and build compact island farms.', """
## Find the recipe
Open `/island machines` → **Spawner recipes**. Recipes use iron blocks in the four corners, a diamond in the centre and the matching ingredient on all four sides.

| Spawner | Side ingredient | Stage |
|---|---|---|
| Zombie / Skeleton / Spider / Blaze | Rotten flesh / Bone / String / Blaze rod | Settler |
| Creeper / Drowned / Husk | Gunpowder / Copper ingot / Sand | Homestead |
| Stray / Cave Spider | Ice / Fermented spider eye | Workshop |
| Enderman / Zombified Piglin / Witch | Ender pearl / Gold nugget / Glowstone dust | Sanctuary |
| Breeze | Breeze rod | Foundry |

## Stack matching spawners
Right-click a placed spawner with a matching spawner item. Sneak and break it to retrieve the stack. Island automation talents affect stacking and collection, so check their current descriptions.

Eligible matching mobs can share a body to reduce crowding. The displayed stack represents multiple mobs, not one unusually valuable monster. Farm output remains subject to population and processing limits; more stacked spawners do not bypass every limit.
""", 'island', 'Island talents | /guide/island-basics\nResource milestones | /guide/island-resources'),
    _guide('island-coop', 'Co-op, visitors and borders',
           'Share building access, invite visitors and find your protected edges.', """
## Manage membership
| Command | Purpose |
|---|---|
| `/island invite <player>` | Owner sends an invitation; it expires after five minutes. |
| `/island invitations` | View invitations you received. |
| `/island accept <owner>` | Join an invited co-op. |
| `/island homes` | List your own and joined homes. |
| `/island home <owner>` | Travel to a joined island. |
| `/island kick <player>` | Owner removes a member. |
| `/island leave <owner>` | Leave someone else's co-op. |

A co-op holds **six people including the owner**. You may own one island and join **three others**. Members can build and use storage; membership does not grant owner controls.

## Welcome visitors
The owner uses `/island visitors on` or `off`. Public islands appear in `/island directory`; `/island visit <owner>` visits an open home. Sightseeing does not grant building, storage, item pickup, redstone or fishing-reward access.

## See your boundary
`/island border` toggles a private particle outline near your protected edges, visible within 40 blocks. It creates no walls or barrier blocks.
""", 'island', 'Resource milestones | /guide/island-resources'),
    _guide('trading-safely', 'Choose how to trade',
           'Use merchants, auctions, stores or a confirmed exchange.', """
| What you need | Best starting point |
|---|---|
| Ordinary materials or a place to sell surplus | `/shops <item>` |
| Competitive bidding or a Buy Now listing | `/ah` |
| Fixed-price player listings | `/store` |
| An agreed item-for-item exchange | `/trade <player>` |

## Check the terms
Merchant prices depend on supply, demand and reputation. Auctions may charge a listing fee and sales tax; review the confirmation and delivery inbox. Inspect sealed gear and socketed items carefully before buying.

Direct trades require both players to confirm the same offer. Changing either offer clears both confirmations and starts another review period. Do not substitute dropped items for a confirmed exchange.

## Keep accounts separate from deals
Never share passwords, `/weblink` codes or payment details. Another player does not need those to trade.

In-game trading uses earned **Marks** and items. The [website store](/store) is a separate cosmetic catalog; its checkout availability is shown on each product.
""", 'economy', 'Auctions and stores | /guide/trading\nRemote trades | /guide/remote-trades'),
    _guide('trading', 'Auctions and player stores',
           'List held items, review prices and collect deliveries.', """
## Auction house
Open `/ah` to browse, inspect lots, bid or use **Buy Now**. Use Survival mode and an empty inventory cursor for transactions.

`/ah sell` opens the listing editor. Exact terms can also be entered as:

```text
/ah sell <starting-bid> [buy-now-or-0] [hours] [amount]
```

Use `0` for no Buy Now price. Duration is **1–72 hours**; defaults are 24 hours and one item. Buy Now cannot be below the starting bid. Review the listing fee before depositing items.

Accepted bids reserve your Marks immediately. You cannot withdraw an accepted bid; an outbid high bid is refunded through the payment system. Sold items, returns and other deliveries are collected through the inbox.

## Player stores
`/store` browses stores; `/store mine` manages yours. Hold the item and use `/store sell <total-price> [amount]` for a fixed-price listing. Search with `/store search <item>` and check `/store inbox` for deliveries.

Both systems work remotely. Account-bound items and paid cosmetics cannot be listed. The confirmation screen shows current fees and terms; they are not the same as the website cosmetic store.
""", 'economy', 'Direct trades | /guide/remote-trades\nCity merchants | /guide/city-shops'),
    _guide('city-shops', 'City merchants',
           'Buy ordinary supplies and sell eligible surplus from anywhere.', """
## Open a merchant
Use `/shops` in Survival, or search with `/shops <item>`. You do not need to stand beside an NPC. Left-click a listing to review a purchase; right-click to review a sale, then confirm.

## Understand the price
- Buying and selling affect shared supply and demand.
- Your reputation with the merchant can change the quoted price.
- Ordinary goods have daily buyback limits, reset at **00:00 UTC**.
- A merchant may refuse modified items or a sale beyond its remaining allowance.

Check the price on the confirmation screen rather than assuming yesterday's price still applies. Merchants supply ordinary materials; they are not a shortcut to finished relics or upgraded player gear.

For player-made stock, compare the [auction house and stores](/guide/trading). All gameplay currency is earned; website purchases do not sell Marks.
""", 'economy', 'Choose how to trade | /guide/trading-safely'),
    _guide('remote-trades', 'Trade with another player',
           'Offer items remotely and confirm an exchange safely.', """
## Start an exchange
Send `/trade <player>`; they accept with `/trade accept`. You can be on different islands. Both players must be alive, in Survival, with an empty inventory cursor and trade requests enabled. Ignore settings can prevent requests.

## Review both offers
- Left-click a stack to offer it, or right-click to offer one item.
- `/trade offer <amount>` offers a specific amount.
- Items remain in your inventory until both players confirm the same offer.
- Every change clears confirmations and starts a **two-second** review period.

Before the exchange completes, `/trade cancel`, closing the menu, teleporting or disconnecting cancels the session. Review the complete offer, not just the other player's chat message.

If an exchange reports a recovery problem, keep the receipt or message and contact staff. Repeated clicks are not a reason to start dropping replacement items on the ground.
""", 'economy', 'Auctions and stores | /guide/trading'),
    _guide('bank-and-pay', 'Bank storage and payments',
           'Keep items in your bank and send whole Marks to online players.', """
## Store items
`/bank` opens your account bank. Its menu shows available storage and the price of additional slots. This is item storage; `/marks` shows your spendable currency balance.

## Send Marks
Use `/pay <player> <amount>` for an **online player**. Amounts are whole, positive Marks. Check the recipient and amount before sending; the command starts a payment rather than an item trade.

`/pay status` shows your recent payment receipts. If a payment is pending, wait for it to settle and keep the receipt for staff. Do not assume that a delayed result means no Marks were debited.

Marks are earned through gameplay. The website store does not sell Marks, gear strength or island progress.
""", 'economy', 'Choose how to trade | /guide/trading-safely'),
    _guide('skills-and-talents', 'Skills and talents',
           'What trains each skill and how personal and island progress differ.', """
## Train by doing
| Skill | Eligible work |
|---|---|
| Mining | Ores and stone. |
| Forestry | Harvesting logs and related forestry activity. |
| Farming | Mature crops, fruit and naturally regrown stalks. |
| Fishing | Accepted catches and collection turn-ins. |
| Hunting | Defeating mobs and encounters. |
| Excavation | Natural soil, brushes and enabled dig sites. |
| Smithing | Eligible equipment crafting and upgrades. |
| Alchemy | Eligible brewing. |
| Building | Eligible block placement. |
| Cooking | Successfully crafting food and meals. |
| Enchanting | Eligible Arcane binds, discoveries and other rewarded actions. |

Open `/skills` for levels, talent requirements and bonuses. Repeating placed-resource loops, cancelled actions and machine output do not provide unrestricted XP. Rates depend on the activity, equipment, talents and server tuning.

## Keep the progress tracks separate
**Personal skills and talents** belong to your account. **Adventurer Level** represents overall character progress, from 1 to 100. **Island XP and talents** belong to the island; `/island journal` separately certifies skill requirements for resource unlocks.

Use `/achievements` for milestones and `/leaderboard` for rankings. Enchanting XP does not pay job Marks by itself.
""", 'skills', 'Fishing | /guide/fishing\nFarming | /guide/farming\nDaily goals | /guide/daily-goals'),
    _guide('fishing', "Fishing and the Angler's Journal",
           'Choose pond habitats, earn catch XP and record collections.', """
## Choose a fishing spot
You can earn catches wherever Minecraft produces a valid catch. Aerborne adds no minimum pond size, depth or open-sky requirement. It does not force vanilla fish to bite in an otherwise unusable spot.

On islands, the **bobber** must be inside your own island or a joined co-op. Visitors cannot earn catches in someone else's island pond. In shared waters, the bobber's biome determines the habitat.

## Set an island habitat
In `/fishing`, **Pond Habitat** changes the shared pond selection without changing terrain or biome. Favoured species are four times as likely; other species remain possible.

| Stage | Habitat |
|---|---|
| Settler | Freshwater |
| Workshop | River |
| Sanctuary | Wetland |
| Foundry | Ocean |
| Aetherworks | Cold Water |

## Keep your catches
Turn fish in to **Ivo** to record collections and earn angling reputation. With default tuning, common/uncommon/rare/legendary catches give **25/37/65/145 Fishing XP**, before applicable bonuses. Cancelled catches give no rewards.

Shared-zone streaks can attract creatures. Fishing does not summon encounter mobs onto private islands.
""", 'skills', 'Resource milestones | /guide/island-resources'),
    _guide('farming', 'Farming and replanting',
           'Harvest mature crops, reserve planting items and avoid wasted work.', """
## Harvest with a hoe
Right-click a **ripe crop with a hoe** to harvest and replant in one action. No sneak key or talent is required. One ordinary seed or planting item is reserved from the drops, or taken from your inventory if needed.

If no suitable planting item is available, the crop stays ripe. Named, enchanted and special seeds are not consumed. Torchflowers and pitcher crops need a spare ordinary seed or pod because their mature harvest does not return one.

## Match the crop
| Crop | Harvest method | Default base XP |
|---|---|---:|
| Wheat, carrot, potato, beetroot | Hoe right-click and replant. | 15 |
| Nether wart, cocoa | Hoe right-click and replant. | 18 |
| Sweet/glow berries | Harvest fruit; bush/vine remains. | 15 |
| Torchflower, pitcher crop | Hoe right-click with a spare seed/pod. | 24 |
| Melon, pumpkin | Break the grown fruit; keep the stem. | 24 |
| Sugar cane, cactus, bamboo | Break grown stalk blocks. | 9 |

XP values are before equipment, talents and server tuning. Immature crops and placed fruit/stalk blocks do not grant repeat rewards. A successful hoe harvest uses one durability; Preservation can help.
""", 'skills', 'Cooking | /guide/cooking\nSkills | /guide/skills-and-talents'),
    _guide('excavation', 'Excavation and dig sites',
           'Find renewable sites and collect finds from eligible soil.', """
## Use an excavation tool
Shovels and brushes train Excavation through eligible digging and brushing. Soil you placed yourself gives no Excavation XP or finds. Tool bonuses can improve rewards.

## Find an enabled site
- `/digsite list` lists sites available to players.
- `/digsite info <site>` shows a site's position and readiness.
- `/excavation` includes a **Dig Sites** button and travel options.

Staff configure these shared areas. Eligible site soil gives XP, finds and geodes, then regenerates after its configured cooldown. An empty list means there is no enabled public site to visit; it does not promise an undiscovered route.

Follow the site's boundaries and recovery timer. For better find odds, compare equipment and the Archaeologist enchantment in the [Codex](/codex).
""", 'skills', 'Explore | /explore\nGear and sockets | /guide/gear-and-sockets'),
    _guide('cooking', 'Cooking and meals',
           'Read recipes, craft meals and improve their quality.', """
## Open the cookbook
Use `/cooking`, `/cookbook` or `/kitchen` to see meals, ingredients, Cooking requirements and a preview at your current quality. The catalog can expand; the in-game book is the current recipe list.

## Craft at a table
The cookbook does not produce food itself. Gather the listed ingredients and craft at a **crafting table**. Level-one recipes are available to beginners; higher meals require the displayed Cooking level.

Successful eligible food crafts award Cooking XP. Default ordinary food recipes give **12 XP**; named meals can give more. Creative crafts and unsuccessful previews do not count as completed work.

## Improve your meals
Cooking level affects meal quality. Island Stewardship talents can improve Cooking XP and extend meals prepared there. Read the actual meal's description for its effects and duration; not every recipe has the same benefit.

Ordinary food and prepared named meals are not interchangeable ingredients. Keep special food out of unrelated vanilla recipes unless the recipe explicitly accepts it.
""", 'skills', 'Farming | /guide/farming\nSkills and talents | /guide/skills-and-talents'),
    _guide('daily-goals', 'Daily goals and achievements',
           'Pick a contract, track a milestone or compare your progress.', """
| Command | What to check |
|---|---|
| `/daily` | Rotating contracts, requirements and current progress. |
| `/achievements` | Milestone journal and unfinished-achievement filter. |
| `/playtime` | Active playtime and seasonal milestones. |
| `/leaderboard` | Player and island rankings, including offline accounts. |

## Choose a useful goal
Pick a contract that matches your current tools and island stage. Read its reward and completion requirements before gathering; progress comes from eligible completed actions, not menu previews.

Leaderboards cover Marks, Adventurer progress, total skills, island XP, achievements and Cooking. Updates are cached rather than instantaneous. Personal scores and co-op island progress are different tracks.

For a longer project, combine a daily goal with the next requirement in `/island journal`.
""", 'skills', 'Milestone planning | /guide/milestone-planning'),
    _guide('gear-and-sockets', 'Gear, identification and sockets',
           'Reveal equipment rolls and use gems without losing track of costs.', """
## Identify before upgrading
Sealed equipment hides its rolled bonuses. **Nyra** offers identification and salvage; review the item count, Marks cost or Dust return before confirming. Identifying reveals the original roll rather than rerolling it.

Rarity, quality and item level affect different parts of equipment. Socket limits and Arcane Capacity are separate: a free gem socket is not a promise of free enchantment capacity.

## Use Forge & Sockets
Right-click an anvil. Put identified equipment on the left and a compatible socket gem on the right. Review the exact result, then confirm. The menu checks available sockets and rejects incompatible or invalid equipment.

Gem types have their own bonuses. Their **Aspect** is a classification used by research; the Aspect name alone does not add another stat. A Prismatic gem is a research catalyst and carries no Aspect.

## Keep a safe working inventory
Merge matching gems up to tier IV, or recover installed gems for Arcane Dust. Leave room for results and returned inputs. Closing the forge with no storage space can drop returned inputs at your feet.
""", 'equipment', 'Gem merging and recovery | /guide/gem-merging\nArcane Capacity | /guide/capacity-and-corruption'),
    _guide('gem-merging', 'Gem merging and recovery',
           'Combine matching gems or remove one without destroying it.', """
## Merge two gems
At an anvil's **Forge & Sockets**, put gems of the **same type and tier** into the two inputs. One gem from each input makes one gem of the next tier, up to **IV**. Review and confirm the result.

## Recover an installed gem
Put the equipment on the left and **Arcane Dust** on the right, then select the filled socket you want to empty.

| Installed tier | Recovery cost |
|---|---:|
| I | 8 Arcane Dust |
| II | 12 Arcane Dust |
| III | 16 Arcane Dust |
| IV | 20 Arcane Dust |

Recovered gems keep their type and tier. Other installed gems remain. A review expires after 15 seconds or an input change, so check it again before confirming.

Recovery and merging require inventory room for the result; a full inventory spends nothing. Closing the forge is different: returned inputs that do not fit can drop at your feet. Work somewhere safe.

**Dust recipe:** 1 lapis lazuli + 1 amethyst shard → 4 Arcane Dust.
""", 'equipment', 'Gear and sockets | /guide/gear-and-sockets'),
    _guide('arcane-forge', 'Arcane Forge: enchanting and discovery',
           'Choose between random offers, research and exact imbuement.', """
## Pick an action
| Action | Where | Result |
|---|---|---|
| Arcane Enchanting | Right-click an enchanting table. | Choose from three offers for Arcane Dust. |
| Personal Codex | `/arcane` | Read discoveries, clues and mastery anywhere. |
| Research | `/arcane research` beside a table. | Consume gems and Dust to discover enchantments. |
| Chosen Imbuement | `/arcane imbue` beside a table. | Apply a known enchantment at an exact chosen level. |
| Mastery | `/arcane mastery` | Review mastery earned from top-level binds. |

## Check the requirements
Research and imbuement require Survival and a nearby enchanting table. Remote station permissions are separate. The effective Arcane level uses the higher of **Enchanting or Smithing**, preserving earlier Smithing progress.

The menu shows compatibility, required level and current Dust price before confirmation. Changing the held item requires another review. A locked tile explains an unmet level, station or feature requirement.

## Learn, then specialise
Successful eligible binds enter your Codex. With default rules, three top-level binds master an enchantment and reduce its Chosen Imbuement cost by **25%**. Research rewards discoveries and clues, not endless unproductive repeats.

The website Codex is a public reference, not a copy of your personal discoveries. Preview effects and Signatures remain separate from ordinary available enchantments.
""", 'arcane', 'Research | /guide/aspects-and-research\nCapacity and corruption | /guide/capacity-and-corruption\nPublic Codex | /codex'),
    _guide('aspects-and-research', 'Aspects and research',
           'Learn what an experiment consumes and how discoveries advance.', """
## Choose different Aspects
Socket gems carry one of nine Aspects. Research uses **one to three gems with different Aspects**; Prismatic gems have no Aspect and can instead serve as an optional catalyst.

Run `/arcane research` beside an enchanting table in Survival. The default minimum effective Arcane levels are **5** for Fundamental and **10** for Specialized research. Effective Arcane level is the higher of Enchanting and Smithing.

| Exact recipe | Tier | Base experiments to discover |
|---|---|---:|
| 1 Aspect | Fundamental | 1 |
| 2 Aspects | Specialized | 2 |
| 3 Aspects | Arcane Signature preview | 4 |

Those counts use tier-I reagents and default tuning. Higher-tier gems, talents and a Prismatic catalyst can accelerate progress. Signature research also needs its feature unlocks; it is not made available merely by collecting three gems.

## Review before spending
An experiment consumes its selected gems, optional catalyst and Dust. Default Dust cost is **4 per reagent**. Partial combinations can build clues toward a larger recipe, but partial progress alone cannot finish that recipe.

The menu keeps unknown outcomes hidden and refuses combinations with nothing left to teach before spending. Your personal Codex tracks discoveries and clues; the website keeps hidden recipe combinations private too.
""", 'arcane', 'The nine Aspects | /codex\nEnchantment tiers | /guide/enchantment-tiers'),
    _guide('capacity-and-corruption', 'Capacity, corruption and attunement',
           'Understand item limits before committing to an Arcane change.', """
## Capacity and instability
Arcane Capacity depends on rarity, quality and item level. Existing enchantments are not stripped because an item exceeds capacity. Free capacity matters when adding a Signature or corruption; the menu evaluates the exact item.

When an enabled overcharge operation exceeds normal capacity, the item becomes **unstable**. Stabilise it with the displayed Arcane Dust cost before further binding. Overcharge is a separately controlled feature, not a benefit every item can always use.

## Corruption
Choose one corruption for a fixed bonus. It uses capacity, increases durability wear and blocks further Arcane work until cleansed. Cleansing is guaranteed, but it is not free.

Default tuning requires effective Arcane level **25**, **48 Dust** to corrupt, **8 capacity**, and **24 Dust** to cleanse. Durability wear doubles. Review the current quote; server tuning can change costs.

## Attunement
Focus on an Aspect already present in the item's enchantments. Default tuning requires effective Arcane level **15** and **16 Dust**, strengthening matching enchantments by **10%**. Clearing attunement is free.

Effective Arcane level uses the higher of Enchanting and Smithing. An enabled feature and compatible item are still required. Locked menus explain unavailable operations.
""", 'arcane', 'Arcane Forge | /guide/arcane-forge\nEnchantment tiers | /guide/enchantment-tiers'),
    _guide('enchantment-tiers', 'Enchantment tiers and Signatures',
           'Compare ordinary enchantments with features still in preview.', """
| Tier | Aspects | Role |
|---|---:|---|
| Fundamental | 1 | Broad equipment and gathering bonuses. |
| Specialized | 2 | More focused effects; some are preview-only. |
| Arcane Signature | 3 | One build-defining Signature per item, when enabled. |

## Available and preview are different
The [public Codex](/codex) labels each entry. Bloodletter, Veinseeker, Chain Harvest and the three Signatures are documented as previews under the current release defaults. A discovered name or a website entry does not make a disabled effect active.

## Signature requirements
When enabled, Signatures use three-Aspect research, mastery requirements, compatible gear and free Arcane Capacity. Default research level is **30**; binding also requires Dust and suitable gems. The in-game menu checks all requirements before confirmation.

**Stormwake** builds charge for an arc attack. **Heart of the Mountain** builds mining Resonance. **Last Bastion** provides a survival trigger with a cooldown. Their recipe combinations remain hidden on the website.
""", 'arcane', 'Public Codex | /codex\nResearch | /guide/aspects-and-research', 'preview'),
    _guide('world-overview', 'Find your way around Aerborne',
           'Understand islands, the shared hub and public travel routes.', """
## Your island
`/island home` returns to your own home. `/island homes` lists joined co-ops, and `/island directory` lists islands open to visitors. Public visiting does not grant building or storage access.

## The shared hub
`/spawn` takes you to the configured hub. The Arcane City artwork is an illustration of the intended capital, not proof that every district or service is finished. Use `/shops` for remote merchant access.

## Destinations beyond home
`/expedition` lists staff-published public routes. `/warp <name>` travels to a known public warp. Destinations and encounters depend on what staff have opened; a planned region on the website is not automatically a playable map.

Some travel uses a warmup. Stay still and avoid damage until it finishes. A [party](/guide/parties) helps you coordinate without sharing island membership.

Check [Explore](/explore) for available features and previews, and read [grave recovery](/guide/graves) before risking valuable equipment.
""", 'world', 'Explore | /explore\nGraves | /guide/graves\nParties | /guide/parties'),
    _guide('graves', 'Graves and item recovery',
           'Know the recovery fee, storage limit and reasons to return promptly.', """
## Read the death message
When a grave can be created, it stores dropped items in an owner-only barrel and reports its location and protection time. Other players, co-op members and hoppers cannot collect from that container.

Graves hold **27 stacks**. Overflow drops beside the barrel as ordinary unprotected items. If a grave cannot be placed, normal death drops remain. Do not treat the system as guaranteed protection for everything you carry.

## Recover your belongings
| Option | Cost and behaviour |
|---|---|
| Return to the barrel | Opening your own grave is free. |
| `/grave locate` | Shows the latest indexed active grave's coordinates. |
| `/grave recover` | Paid remote recovery; default **250 Marks**. The command starts the payment immediately. |

Make inventory space and stand somewhere safe before remote recovery: items that do not fit can drop at your feet. Grave protection defaults to **24 hours**; use the time reported by the server. Expiry releases contents as ordinary drops.

## Return promptly
A server restart can prevent older graves from appearing in locate/remote recovery, even while the physical barrel remains. Keep the coordinates from your death message and contact staff if needed.

Falling into the island void is different: island rescue returns you with your items instead of creating a grave.
""", 'world', 'World travel | /guide/world-overview'),
    _guide('parties', 'Parties and expedition chat',
           'Group up without sharing your island or storage permissions.', """
| Command | Purpose |
|---|---|
| `/party invite <player>` | Start a party or invite another player. |
| `/party accept` | Accept a pending invitation. |
| `/party info` | See the current roster. |
| `/partychat <message>` | Send a message only to the party. |
| `/party leave` | Leave the group. |
| `/party kick <player>` | Leader removes a member. |
| `/party promote <player>` | Leader hands over leadership. |
| `/party disband` | Leader closes the party. |

Party membership is separate from island co-op access. Joining a party does not grant building, storage or visitor access to another player's island. Use it to coordinate travel and encounters with people from different co-ops.

Check `/expedition` for routes currently open, and agree where to meet before travelling.
""", 'world', 'World travel | /guide/world-overview\nCo-op permissions | /guide/island-coop'),
    _guide('website-and-arcade', 'Website profiles and Arcade rewards',
           'Link Minecraft, personalise your profile and understand queued Marks.', """
## Link your Minecraft account
Create a [website account](/account/register), then run `/weblink` in Aerborne. Enter the private code on the [linking page](/account/link). It expires after **ten minutes**, works once and links one Minecraft account to one website account.

## Build your profile
[Edit your profile](/account) to choose an avatar and banner, introduce yourself and add optional island, skill and social details. These details are public. Game-stat visibility has a separate setting. Staff assign website ranks and display tags.

## Play the Arcade
Visitors can practise; signed-in members can save accepted scores. Marks require a linked Minecraft account, available daily allowance and an accepted run. Check each game's limits in the [Arcade](/arcade/).

Rewards enter a queue. Delivery requires the game-server connection; the Arcade displays a notice while it is still being connected. Daily allowances reset at **00:00 UTC**. Pausing does not reset a run or its limits.

## Fix account problems
Expired code: run `/weblink` again. Already linked: use the original website account. Forgotten password: use [Minecraft account recovery](/account/recover). For private issues, open an [account help thread](/forum/support-requests); never post passwords or linking codes publicly.
""", 'troubleshooting', 'Arcade | /arcade/'),
    _guide('connection-help', 'Fix a connection problem',
           'Check the client version, copied address and live status first.', """
## Check the basics
Use the address and Java Edition version on the [Play page](/play). Paste the address without leading or trailing spaces. Aerborne is open to public players; no tester invitation is required.

## Match the message
| Message | Try this |
|---|---|
| Outdated client / server | Select the supported version in the launcher's Installations tab. |
| Cannot connect / timeout | Check [live status](/status), recopy the address and allow time for a restart. |
| Teleport cancelled | Stay still during its warmup and avoid damage. |

Bedrock, console and mobile clients need a separately enabled Bedrock connection. Use only connection details actually listed on Play.

If the server is online and the problem continues, ask in [Discord](/discord) or the [help forum](/forum/help). Include the exact error, client edition and version. Keep account passwords and `/weblink` codes private.
""", 'troubleshooting', 'Play | /play\nLive status | /status'),
    _guide('commands', 'Commands quick reference',
           'The everyday menus, travel routes and recovery commands.', """
## Character and progress
| Command | Purpose |
|---|---|
| `/menu` / `/quickstart` | Main menus / first-session goals. |
| `/skills` / `/adventurer` | Skills and talents / overall level. |
| `/daily` / `/achievements` | Contracts / milestone journal. |
| `/cooking` / `/fishing` / `/excavation` | Activity journals. |
| `/arcane` | Personal discoveries, research and imbuement menus. |

## Island and travel
| Command | Purpose |
|---|---|
| `/island create` / `/island home` | Create once / return home. |
| `/island journal` / `/island talents` | Shared unlocks / island talent tree. |
| `/island machines` | Machine and spawner recipes. |
| `/island homes` / `/island directory` | Joined co-ops / public visiting. |
| `/spawn` / `/expedition` | Hub / published public destinations. |

## Trading and help
| Command | Purpose |
|---|---|
| `/marks` / `/bank` | Currency balance / item bank. |
| `/shops` / `/ah` / `/store` | Merchants / auctions / player stores. |
| `/trade <player>` / `/pay <player> <amount>` | Item exchange / online-player payment. |
| `/grave locate` / `/grave recover` | Latest grave / paid remote recovery. |
| `/weblink` | Private, one-use website linking code. |
| `/report <player> <reason>` | Private report to staff. |
| `/settings` | Message, social and travel preferences. |

Read confirmation screens and costs before committing. Research and imbuement need a station unless separate remote access is available; `/arcane` itself can be read anywhere.
""", 'troubleshooting', 'Connection help | /guide/connection-help\nWebsite account | /guide/website-and-arcade'),
]

REGIONS = {
    'your-island': dict(summary='Your private workshop in the sky. Grow resources, build with your co-op and unlock more space.',
        requirements='Create a home with /island create; return with /island home.',
        activities='Gather renewable generator resources\nFarm and fish with your co-op\nCertify skill progress in the island journal\nProcess materials with island machines',
        points_of_interest='Starter tree, water and lava\nRenewable block generator\nProtected building area',
        body='''## Start small, build steadily
Your island begins with a tree, water, lava, crops and a renewable generator. Run `/quickstart` for first-session goals and `/island journal` for your next resource or territory unlock.

## Make it a shared home
A co-op holds six people including the owner. Members can build and use storage; the owner manages invitations and visitors. You may keep your own island while joining up to three other co-ops.

## Choose what comes next
Gathering, crafting and other eligible skill work contribute to island progress. Machines process materials without replacing that work. The [resource guide](/guide/island-resources) explains the stages and land expansions.

Island void rescue returns you safely with your items. Ordinary deaths still use the [grave system](/guide/graves).'''),
    'arcane-city': dict(summary='The shared hub and the planned city above the clouds. Public services and the finished build are separate.',
        requirements='/spawn travels to the configured hub. The finished city remains a preview.',
        activities='Travel to the current shared hub\nOpen remote merchants with /shops\nCheck /expedition for published destinations',
        points_of_interest='', body='''## Visit the current hub
Use `/spawn` for the server's configured hub. Staff can change its build and available services as the city develops.

## Use the services already exposed in game
`/shops`, `/ah`, `/store` and `/bank` open their respective menus without requiring a completed city district. Physical service NPCs depend on staff placement.

## About this preview
The artwork illustrates the intended Arcane City. It is not an in-game screenshot or a promise that every pictured route, district or NPC is available. Use `/expedition` for currently published travel destinations.'''),
    'expeditions': dict(summary='Shared adventure areas published by staff, with their own encounters and resources. This overview is a preview.',
        requirements='Use /expedition to see the destinations currently published in game.',
        activities='Browse enabled destinations\nCoordinate with a party\nPrepare gear and recovery supplies', points_of_interest='',
        body='''## Check the destination list
`/expedition` shows staff-published public travel routes. If a route is not listed or is locked, this preview does not make it accessible. Individual zones can have their own entry requirements.

## Prepare before leaving
Take suitable equipment, leave inventory space and understand [grave recovery](/guide/graves). A [party](/guide/parties) lets you coordinate with players outside your co-op.

## Maps and encounters
Staff author the zones, spawn points and encounter populations. No specific finished region, loot table or opening date is promised by this overview. New public areas can receive their own Explore entries as they open.'''),
}

CODEX_NOTES = {
    'bloodletter': 'Preview effect. It remains dormant while its runtime feature is disabled; its presence on equipment does not override that switch.',
    'veinseeker': 'Preview effect. Eligible natural ores and normal protection checks apply when enabled; player-placed ores are not an unlimited reward source.',
    'chain-harvest': 'Preview effect. Applies to eligible mature crops when enabled, with normal harvest and protection checks.',
    'stormwake': 'Preview Signature. One Signature per compatible item. Binding and the runtime effect have separate feature requirements; the recipe is intentionally undisclosed.',
    'heart-of-the-mountain': 'Preview Signature. Natural-ore eligibility applies. One Signature per compatible item; the recipe is intentionally undisclosed.',
    'last-bastion': 'Preview Signature. A triggered survival effect with a cooldown, not permanent invulnerability. One Signature per compatible item; the recipe is intentionally undisclosed.',
}

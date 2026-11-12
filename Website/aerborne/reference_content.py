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

Use `/island home` to return and `/menu` for the main game menus. Falling into the island void returns you safely without losing your items. Death keeps inventory and XP; adventure worlds use [spirits and resurrection](/guide/graves).
""", 'getting-started', 'Island resources | /guide/island-resources'),
    _guide('island-basics', 'Grow your island',
           'Understand resources, shared progress and co-op access.', """
## Three ways your island develops
- **Unlock resources and land:** open `/island journal` and share your trained skill progress.
- **Choose island bonuses:** open `/island talents`. Shared points belong to the island; the owner chooses upgrades.
- **Build automation:** open `/island machines` for processing stations, spawners and their recipes.

These menus serve different purposes. A talent purchase does not replace a journal skill requirement.

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

Each socket takes certain gems: combat gems in weapons, defensive gems in armour, gathering gems in tools, and any gem in the **Universal** socket that legendary and better gear ends with. Two matching gems of the same grade merge into the next grade, from **Rough** up to **Perfect**. Gems can also be recovered for Arcane Dust. Keep inventory space when using or closing the forge.

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
| Party adventures, the Nether and the End | `/expedition` |
| Public warps | `/warps` |

Shopping and player commerce work remotely; you do not need to leave your island just to browse. Review fees, prices and delivery information before confirming.

## Prepare before an expedition
[Expeditions](/guide/expeditions) are private adventures for you and up to four friends. Dying inside one never costs your gear, and on Normal and Heroic you simply run back from the last campfire. Make a [party](/guide/parties) first, or queue on your own and the finder groups you with other players.

The [expedition board](/expeditions) lists what is open right now, and the [campaign page](/campaign) shows which expeditions the server is still working to unlock.
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
| Zombie | Rotten flesh | Settler |
| Skeleton | Bone | Settler |
| Spider | String | Settler |
| Blaze | Blaze rod | Settler |
| Creeper | Gunpowder | Homestead |
| Drowned | Copper ingot | Homestead |
| Husk | Sand | Homestead |
| Stray | Ice | Workshop |
| Cave Spider | Fermented spider eye | Workshop |
| Enderman | Ender pearl | Sanctuary |
| Zombified Piglin | Gold nugget | Sanctuary |
| Witch | Glowstone dust | Sanctuary |
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
## Pick the kind of trade
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

## Experience bottles
Experience bottles hold what someone else already learned. Right-click a **Bottled Lesson** to uncork it and choose a skill: that skill gains 10% of the XP it needs for its next level. A **Bottled Lifetime** gives 25% and a **Bottled Legend** 60%; they come from expedition chests. Shift-click a skill to use the whole stack. Bottles fired from a dispenser still shatter as usual.

Open `/skills` for levels, talent requirements and bonuses. Repeating placed-resource loops, cancelled actions and machine output do not provide unrestricted XP. Rates depend on the activity, equipment, talents and server tuning.

## Build a connected talent tree
Each of the 11 skills has four ranked paths, shared crossroads and a capstone. Reach rank II in either connected path to learn a shared talent; two shared talents open the level-50 capstone. Each profession keeps its own eight-point budget.

Open `/adventurer` for a separate personal tree. Its eleven points unlock at Adventurer levels 5, 10, 20 and every ten levels through 100. Existing automatic rank perks remain active.

`/island talents` has four branching co-op specializations across two pages. Either middle path can reach its capstone. Island points are shared and only the owner spends them. Talent purchases are seasonal; mastery respecs do not refund them.

## Keep the progress tracks separate
**Personal skills and talents** belong to your account. **Adventurer Level** represents overall character progress, from 1 to 100. **Island XP and talents** belong to the island; `/island journal` separately certifies skill requirements for resource unlocks.

## Equip your character
Open `/equipment` (`/gear` or `/equip`), choose Equipment in the Character menu, or press Sneak + F. The screen has 16 slots: head, chest, legs, feet, shoulders, wrists, hands, belt, necklace, back, two rings, two trinkets, main hand and off hand. Your normal E inventory remains available for storage and crafting.

Shift-click an accessory in your normal E inventory to equip it, just as you Shift-click armor. The character screen offers the same quick-equip controls, and single slots swap directly. If both rings or trinkets are occupied, choose which to replace after comparing build gains/losses, including sockets and set bonuses. Right-click cancels a replacement choice; closing it leaves your gear in place. Normal chest transfers keep working.

The character screen updates armor, durability and your selected main hand automatically. F closes it. Right-click an equipped slot outside a replacement choice to unequip it; you need an empty bag slot. Rings and trinkets must have different item identities. All equipment, including armor, weapons and accessories, stays with you through death. Ten early accessories can be crafted at Smithing levels 5–10; staff can author more in the item editor.

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
Hunger stays full in Survival and Adventure. After **8 seconds** without dealing or taking damage, health recovers at **5% of maximum health per second** by default. Food is optional preparation; recovery consumes no food, XP or Marks.

Eat any food, even while full, for **Well Fed**: **25% faster rest recovery for 5 minutes**. The latest serving replaces the benefit instead of stacking it. Nourishment talents improve the recovery bonus on meals you prepare. The benefit expires while offline, clears on death or after drinking milk, and never enables passive recovery during combat. Your vitals show the remaining time.

Prepared meals also retain their listed Cooking effects. Healing potions and Regeneration effects still work during combat; ordinary hunger regeneration is replaced. Poison and Wither prevent passive rest recovery. These rates can be tuned by staff.

Cooking level affects meal quality. Island Stewardship talents can improve Cooking XP and extend meals prepared there. Read the actual meal's description for its effects and duration; not every recipe has the same benefit.

Ordinary food and prepared named meals are not interchangeable ingredients. Keep special food out of unrelated vanilla recipes unless the recipe explicitly accepts it.
""", 'skills', 'Farming | /guide/farming\nSkills and talents | /guide/skills-and-talents'),
    _guide('daily-goals', 'Daily goals and achievements',
           'Pick a contract, track a milestone or compare your progress.', """
## Find a goal
| Command | What to check |
|---|---|
| `/daily` | Rotating contracts, requirements and current progress. |
| `/achievements` | Milestone journal and unfinished-achievement filter. |
| `/playtime` | Active playtime and seasonal milestones. |
| `/leaderboard` | Player and island rankings, including offline accounts. |

## Choose a useful goal
Pick a contract that matches your current tools and island stage. Read its reward and completion requirements before gathering; progress comes from eligible completed actions, not menu previews.

Leaderboards cover Marks, Adventurer XP, total skill levels, island XP, achievement points and all 11 individual skills. Individual skill boards rank by exact XP, not level. Updates are cached rather than instantaneous. Personal scores and co-op island progress are different tracks.

For a longer project, combine a daily goal with the next requirement in `/island journal`.
""", 'skills', 'Milestone planning | /guide/milestone-planning'),
    _guide('gear-and-sockets', 'Gear, identification and sockets',
           'Reveal equipment rolls and use gems without losing track of costs.', """
## Identify before upgrading
Sealed equipment hides its rolled bonuses. **Nyra** offers identification and salvage; review the item count, Marks cost or Dust return before confirming. Identifying reveals the original roll rather than rerolling it.

Rarity, quality and item level affect different parts of equipment. Socket limits and Arcane Capacity are separate: a free gem socket is not a promise of free enchantment capacity.

## Item abilities
Rare and better gear can do more than carry stats. Its tooltip names each ability and when it works: **On Hit** has a chance to go off when you strike, **Right Click** is yours to use on a cooldown, and **Equipped** works for as long as you wear or hold the item. Sealed gear does nothing until it's identified.

## Use Forge & Sockets
Right-click an anvil. Put identified equipment on the left and a compatible socket gem on the right. Review the exact result, then confirm. The menu checks available sockets and rejects incompatible or invalid equipment.

Gem types have their own bonuses. Their **Aspect** is a classification used by research; the Aspect name alone does not add another stat. A Prismatic gem is a research catalyst and carries no Aspect.

## Socket types
Gear tooltips list their sockets under **Gemstone Slots**, and the forge names the next one before you confirm.

| Socket | Found on | Takes |
|---|---|---|
| `[C]` Combat | Weapons | Ruby, Amethyst |
| `[D]` Defensive | Armour and shields | Sapphire, Onyx |
| `[G]` Gathering | Tools, rods and other gathering gear | Topaz, Aquamarine, Jade, Citrine |
| `[U]` Universal | The last socket of legendary, mythic and relic gear | Any gem |

Emerald and Moonstone fit any socket. Prismatic gems fit only a Universal socket.

Gems and geodes can't be smelted, crafted, placed or traded as the vanilla item they look like. Merging is the only way to change a gem.

## Keep a safe working inventory
Merge matching gems up to Perfect, or recover installed gems for Arcane Dust. Leave room for results and returned inputs. Closing the forge with no storage space can drop returned inputs at your feet.
""", 'equipment', 'Gem merging and recovery | /guide/gem-merging\nArcane Capacity | /guide/capacity-and-corruption'),
    _guide('gem-merging', 'Gem merging and recovery',
           'Combine matching gems or remove one without destroying it.', """
## Merge two matching gems
1. Right-click an **anvil** to open **Forge & Sockets**.
2. Put a gem in each input. Both must have the **same gem type and the same grade**.
3. Review the result, then choose **Confirm Change**.

One gem from each input is consumed to make **one gem of the next grade**. Gems climb five grades: **Rough, Flawed, Fine, Flawless and Perfect**. For example, two Rough Rubies become one Flawed Ruby. **Perfect is the highest grade.** Each grade adds the gem's bonus once more: a Perfect gem gives five times a Rough one.

## Where gems come from
Geodes found while mining and digging crack into Rough or Flawed gems; merging climbs the rest of the ladder.

| Geode | Rough | Flawed | Fine |
|---|---|---|---|
| Rough Geode | 85% | 15% | — |
| Resonant Geode | 50% | 50% | — |
| Luminous Geode | 15% | 70% | 15% |

## Recover an installed gem
Recovering means **taking one gem back out of your equipment**. It does not upgrade that gem or replace it with Dust.

1. Put **identified equipment in the left input** and **Arcane Dust in the right input**.
2. Select the **filled socket** you want to empty.
3. Review the selected gem and Dust cost, then confirm.

The cost depends on the grade of **that one installed gem**, not the rarity or level of the equipment:

| Installed gem grade | Dust needed for one gem |
|---|---|
| Rough | 8 Arcane Dust |
| Flawed | 12 Arcane Dust |
| Fine | 16 Arcane Dust |
| Flawless | 20 Arcane Dust |
| Perfect | 24 Arcane Dust |

You get the selected gem back with its **original type and grade**. Your other installed gems stay in place. Each recovery is a separate change. A review expires after **15 seconds**, an input change or choosing another socket; review again before confirming.

## Prepare Dust and inventory space
Craft **4 Arcane Dust** from **1 lapis lazuli + 1 amethyst shard**. The amounts above are Dust items, not recipe batches.

Recovery and merging require room for the result. A full inventory spends nothing. **Closing the forge is different:** returned inputs that do not fit can drop at your feet. Leave space and work somewhere safe.
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

| Different Aspects | Research tier | Base experiments for a matching recipe |
|---|---|---:|
| 1 Aspect | Fundamental | 1 |
| 2 Aspects | Specialized | 2 |
| 3 Aspects | Arcane Signature preview | 4 |

These are recipe sizes, not the hidden Aspect combinations. The counts assume a matching recipe, Rough reagents and default tuning. Higher-grade gems, talents and a Prismatic catalyst can accelerate progress. Signature research also needs its feature unlocks; it is not made available merely by collecting three gems.

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
## Compare the three tiers
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
`/warps` lists public warps and `/warp <name>` travels to one. Some travel uses a warmup. Stay still and avoid damage until it finishes.

## Expeditions, the Nether and the End
`/expedition` opens the expedition board: short adventures for a party of up to five, each in its own private copy of the map. The Nether and the End are reached this way. Vanilla portals stay sealed, and Nether and End expeditions run in real Nether and End dimensions. Some expeditions open only once the [season campaign](/guide/season-campaign) reaches a milestone. Read the [expedition guide](/guide/expeditions) before your first run.

Check [Explore](/explore) for available features and previews, and read [spirits and resurrection](/guide/graves) before your next adventure.
""", 'world', 'Expeditions | /guide/expeditions\nGraves | /guide/graves\nParties | /guide/parties'),
    _guide('graves', 'Death, spirits and resurrection',
           'Keep your equipment and XP, then choose how to return to life.', """
## Your belongings stay with you
Death keeps your **inventory and XP in every world**, including worn armor, weapons and accessories. New deaths create no item-storage graves and charge no recovery fee. Island deaths use a safe respawn; falling into the island void still triggers island rescue.

## Release your spirit in the adventure world
After dying in the MMORPG adventure world, respawn to release your spirit at a nearby sanctuary. You are hidden from living players and cannot fight, loot, gather, craft, trade or use gameplay travel commands. A boss bar and your compass point toward your body. Ghost state survives logging out and server restarts.

Use `/spirit` to open your choices. Resurrection becomes available after **10 seconds** by default:

| Choice | Result |
|---|---|
| Return to your body, then `/spirit resurrect` | Stand on safe ground within **16 blocks**. Return with **50% health**, without resurrection sickness. |
| Visit the Spirit Healer, then `/spirit healer` | Review and confirm a sanctuary resurrection with **full health** and **60 seconds** of resurrection sickness: **25% less damage dealt**. |
| `/spirit return` | Return your ghost to its sanctuary, including when you become stuck. |

If your body is unreachable, the Spirit Healer waives sickness. Neither option costs Marks, items or durability. Five seconds of resurrection protection helps you move away from danger; attacking ends that protection. Staff support remains available with `/helpme`.

## Expeditions and old graves
Expeditions keep their own checkpoint and shared-life rules; keyed runs retain their timer penalties. They keep your gear and do not start a separate open-world corpse run.

Old grave containers are still protected for their owners. `/grave locate` and `/grave recover` remain compatibility commands; remote recovery of an indexed old grave is free. Their old expiry/storage limits still apply, so collect remaining contents promptly. A restart can leave an old physical barrel unindexed; use its saved coordinates or contact staff. New deaths never put items into these containers.
""", 'world', 'World travel | /guide/world-overview'),
    _guide('parties', 'Parties and expedition chat',
           'Group up, find a party and run expeditions together, without sharing your island.', """
## Start a party
A party is a group for playing together: expeditions, party chat and shared kill rewards. It does **not** add anyone to your island co-op.

Type `/party` to open the party menu. It shows everyone in your party with their health and where they are, and has buttons to invite, chat and start an expedition.

| Command | Purpose |
|---|---|
| `/party invite <player>` | Invite a player. Leave out the name to pick from who's online. |
| `/party accept` | Join the party that invited you. Or click **[Accept]** on the invite in chat. |
| `/party deny` | Decline an invite. |
| `/party list` | Find an open party to join. |
| `/party join <player>` | Join the open party that player is in. |
| `/pc <message>` | Talk to your party only. |
| `/party leave` | Leave the party. |

Invites last a minute. You can hold invites from up to three parties at once and choose one. Joining a party withdraws the rest.

## Lead the group
The leader has a few more tools, in the menu or as commands:

| Command | Purpose |
|---|---|
| `/party kick <player>` | Remove a member. In the menu, right-click their head. |
| `/party promote <player>` | Hand leadership to a member. In the menu, left-click their head. |
| `/party open` | List the party in the finder so anyone can join. `/party close` makes it invite-only again. |
| `/party note <text>` | Add a short note, like *Barrow run at 8, bring food*. |
| `/party invites members` | Let every member invite players. `/party invites leader` changes it back. |
| `/party disband` | Close the party. |

If the leader leaves, the party passes to the member who has been there longest.

## When someone goes offline
Your party stays together when people log out, and through server restarts. If the leader is offline for a minute, the next online member takes over. A member who is offline for 15 minutes leaves the party. When you log back in, you're told which party you're in and about any invites waiting for you.

## Run expeditions together
A party holds up to **eight** players. Expeditions take up to five; [raids](/guide/expeditions) take all eight. The leader starts a run with `/expedition start <expedition>` or from the `/expedition` menu. Everyone online gets a ready check. Click **[Ready]** or type `/expedition ready`, and the run starts once everyone has accepted. Offline members stay behind.

Short of players? The leader can use `/expedition queue <expedition>` instead, and the finder fills the empty places with other queued players. See the [expedition guide](/guide/expeditions).

Party membership does not grant building, storage or visitor access to another player's island. For a shared home, use the separate [co-op system](/guide/island-coop).
""", 'world', 'Expeditions | /guide/expeditions\nCo-op permissions | /guide/island-coop'),
    _guide('expeditions', 'Expeditions: party adventures',
           'Form a party, clear the rooms, beat the boss and leave with loot. Raids take up to eight.', """
## What an expedition is
An expedition is a short adventure for **one to five players** (a raid takes up to eight) in a private instance: your party gets its own copy of the map, so nobody else's fight gets in your way. Each run is a set of rooms. Enter a room to start it. Clear its waves, survive its timer, solve its puzzle or defeat its boss to open the way forward. Some maps branch, so you choose the order. Win, collect your loot and walk into the exit light.

New to expeditions? Start with **First Flight**, a gentle tour across the sky islands with Captain Wren. It's marked *Start here* on the board.

The Nether and the End are reached through expeditions. Vanilla portals are sealed. Nether and End expeditions run in real Nether and End dimensions, with the same sky, fog and creatures.

## Start a run
| How | Use it when |
|---|---|
| `/expedition` | Browse the board and choose a difficulty. Left-click starts with your party; right-click queues. |
| `/expedition start <expedition> [tier]` | Your party is ready. The leader runs it and everyone gets a ready check. |
| `/expedition queue <expedition> [tier]` | You are short of players. The finder groups queued players and parties, oldest first. |
| `/expedition leave` | Leave the queue, or step out of a run. |

A full group starts straight away. A smaller group that meets the expedition's minimum starts after a short wait. When every instance is busy, you keep your place in line. If someone declines a ready check, everyone else goes back to the queue at their old place.

## Short of players?
Smaller groups meet fewer, softer enemies, so a pair can clear an expedition built for five. If you have waited a while in the finder, you'll be offered **hired allies**. Click **[Start with allies]** or type `/expedition allies` to fill the empty places with labelled NPCs: a Bulwark that soaks up hits, a Blade, a Hedge-witch who heals the most hurt player nearby, and an Archer. Allies never cost the party a life and come back shortly after falling.

A run with hired allies still pays loot and Marks, but it doesn't count toward the [season campaign](/guide/season-campaign), the expedition records or unlocking the next difficulty.

## Lives, deaths and disconnects
- **Your gear is safe.** Dying in an expedition never drops items or creates a grave.
- **Normal and Heroic forgive.** Fall and you run back from the last campfire or checkpoint, as often as it takes. There are no lives and no time limit, except on expeditions where beating the clock is the goal.
- **Mythic shares lives.** Every death spends one of the party's lives. When they run out, the run fails. Keystones have no lives: each death costs time instead.
- **Wipes reset the fight.** If the whole party is down at once, or nobody is left alive behind a sealed boss door, that fight resets and the door opens. Everything you've already killed stays dead.
- **Checkpoints.** Clearing a room can move the party's respawn point forward.
- **Disconnects.** Rejoin within a few minutes and you're put back into the run.

While you're in a run, your sidebar shows the clock, your lives or deaths, every objective and your party's health. The bar at the top of the screen appears only for a boss or mini-boss.

## Packs, threat and interrupts
The Hollow Barrow plays like a proper dungeon, and the other expeditions will follow:
- **Enemies stand in packs.** They wait until you come close in plain sight, hit one, or a fight spills next to them. Then the whole pack fights together. Patrols walk set routes, so watch them before you pull.
- **Pull one group at a time.** A pack you've led too far from home, or lost, gives up, walks back and heals.
- **Threat decides who they hit.** Enemies fight whoever worries them most, not whoever is nearest. Dealing damage builds threat, and a raised shield that takes a hit builds far more.
- **Taunt with a shield.** Anyone can do it: sneak and right-click with a shield, and everything close turns on you for a few seconds. It has a 12-second cooldown.
- **Stop casts.** A bar over an enemy means it's casting. A yellow bar can be stopped with a fully charged hit or a full-draw arrow, and strong casts take several. A red bar with a ring can't be stopped: raise a shield or step out.
- **Boss doors seal.** A boss door can stay barred until the packs guarding it are dead, and it shuts behind you during the fight.
- **Boss rooms join in.** Fire can run down the floor, chandeliers can fall, and candles and braziers can need you to right-click them mid-fight. Watch the room as well as the boss.

## Puzzles, campfires and bosses
- **Puzzles**: pull levers, press buttons in the order shown, weigh down pressure plates by throwing items on them, shoot targets before time runs out, or cross a jump course. Falling off a course puts you back at its start without costing a life. Puzzles that need a bow or something to throw have a **supply crate**: right-click it for what you need, and it's taken back when you leave.
- **Campfires**: right-click to light one between fights. Rest beside it out of combat to heal, and the party respawns there.
- **Warnings**: elites, mini-bosses and bosses have special attacks, and every one is shown first: a red ring, cone or line on the ground, or a marker under a player. Step out of it. A meteor shadow is the exception: stand in it with the marked player to share the hit.
- **Different goals**: most runs end at the boss, but some ask you to slay enough enemies first, beat the clock (each cleared room adds time), or defeat several bosses in any order.

## The first campaign
Each expedition opens once you've cleared the one before it: **The Hollow Barrow** (a crypt), **Thornwood Canopy** (treetops at night), **The Sunken Aqueduct** (a race against the water), **Gloomforge Mines** (three foremen and the Deep Foreman) and **The Hall of the Three Wardens** (three wings, three bosses). Each one's guide on the [expedition board](/expeditions) lists the route, the bosses and what they drop.

## Difficulty
| Tier | What changes |
|---|---|
| **Normal** | The expedition's base level, one loot roll. Fall and you run back. |
| **Heroic** | Stronger enemies, two loot rolls. Fall and you run back. Clear Normal first. |
| **Mythic** | Much stronger enemies, 3 lives, three loot rolls and extra modifiers. Clear Heroic first. |

Modifiers add twists: **Fortified** (tougher regular enemies), **Tyrannical** (tougher bosses), **Bolstering** (enemies grow stronger when allies fall), **Volatile** (enemies burst after dying), **Raging** (wounded enemies hit harder), **Sanguine** (fallen enemies leave healing pools) and **Timed** (finish under par for an extra loot roll). The board shows which apply before you start.

## Mythic keystones
Your first Mythic clear of a campaign expedition earns a **keystone**: a key to a random campaign expedition at +2. Start it with your party using `/expedition key start` or the keystone card on the board. Each level makes enemies 8% tougher, and weekly affixes join at +4, +7 and +10. A key run has no lives: each death adds 10 seconds to the clock instead. Beat the timer and the key goes up one to three levels and moves to a new expedition; finish late or fail and it drops a level. `/expedition key` shows your key and this week's affixes, and each expedition's page shows the highest timed key.

## Raids
A raid is a bigger, harder expedition for **up to eight players**, with no hired allies. The first is **Cinder Keep**: nine volcanic caverns under the keep and five boss fights, ending at Pyrrhax, the Ashen Sovereign. Normal is tuned for pickup groups; Heroic and Mythic are for organised raids.

- **Weekly loot.** Each boss, and the finish, pays its loot once per difficulty each week. You can keep helping after that; the lockouts reset every Monday. The board's difficulty cards show how much you've looted this week.
- **The Nether.** Defeating the first boss, Gorrak the Kilnbreaker, on any difficulty opens the Nether's **Cinder Shelf** route to you (`/expedition routes`).
- **Know the fights.** Skarn and Vesh must die within ten seconds of each other. Turn the three rune valves to bring out the Steward. Pyrrhax's Wrath throws you outward, so keep your back to solid rock, not the lava.

## Rewards
Every expedition has its own loot: **materials** from common to rare, a **signature set** of armour and a weapon, and rare boss **signatures**.

- **Enemies** drop the expedition's materials, and elites, mini-bosses and bosses drop more. Nobody gets saddles or emeralds.
- **Crates, chests and hidden caches** stand in the rooms. They're private: each player loots each one once with their own roll, and sees a glimmer over the ones they haven't opened. Hidden caches only glimmer from close by, and count toward your score.
- **Reward chests.** Winning grades your run from D to S+ by speed, survival and the secrets you found. Each player opens a free reward chest whose contents grow with the grade, and can open a **bonus chest** for Marks, with better odds and always a set piece. Use the chests by the exit, or `/expedition rewards` and `/expedition bonus`.
- **The Expedition Forge.** Bring materials home and forge the set from them with `/expedition forge`, once you've cleared the expedition. Pieces come out sealed; [identify](/guide/gear-and-sockets) them to reveal their affixes.
- **Rewards grow with you.** Set gear gains item levels on harder difficulties and when you outlevel the dungeon, and stacks of items grow with them.
- **Bottled Lessons** from chests are skill lamps: uncork one to choose the skill that gets its XP.
- **Bosses can drop a rare charm.** Right-click it to bind a companion you can't buy, or trade it to someone who wants it more.
- **Bosses leave a chest.** Everyone has their own share: materials, a good chance at set gear, and sometimes one of that boss's own items.

Marks are paid as soon as you win, and items that don't fit wait until you leave. Expeditions also count towards the [season campaign](/guide/season-campaign), with harder tiers counting for more. The [expedition board](/expeditions) shows clear counts and the fastest parties.
""", 'world', 'Expedition board | /expeditions\nParties | /guide/parties\nSeason campaign | /guide/season-campaign'),
    _guide('season-campaign', 'The season campaign',
           'Work towards server-wide goals together and unlock rewards for everyone.', """
## One goal, the whole server
Each season brings a themed campaign with a handful of **goals** that every player feeds: ore mined, metal smelted, hostile creatures defeated, harvests and catches, and expeditions cleared. Progress is shared. Each player's contribution to a goal is capped per day, so everyone's effort counts.

## Milestones and rewards
When the server reaches a **milestone**, everyone benefits:

- **Server buffs:** a potion effect or a skill XP or job Marks boost for everyone online, and for anyone who joins while it's running.
- **Rewards to claim:** items and Marks every account can collect once with `/campaign claim`, whether or not you were online when the milestone fell.
- **Unlocks:** some expeditions stay sealed until a milestone opens them for good.

## Commands
| Command | What it does |
|---|---|
| `/campaign` | Goals, your contribution, milestones, running buffs and your rewards to claim. |
| `/campaign progress` | The same summary in chat. |
| `/campaign claim` | Collect every reward you haven't claimed yet. Make inventory space first. |
| `/campaign contribute [goal] [amount]` | Hand in the stack you are holding to a goal that collects it. |
| `/campaign top [goal]` | The season's top contributors. |

Follow the season on the [campaign page](/campaign).
""", 'world', 'Campaign page | /campaign\nExpeditions | /guide/expeditions'),
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
Commands with `<placeholders>` need your own value in place of the brackets. `[Optional parts]` may be omitted. Copying a command from this guide does not run it.

| Command | What it does |
|---|---|
| `/menu` | Open the main game menus. |
| `/quickstart` | Show first-session goals. |
| `/skills` | Open personal skills and talents. |
| `/adventurer` | Show overall character progress. |
| `/daily` | Open rotating contracts. |
| `/achievements` | Open the milestone journal. |
| `/cooking` | Open the cookbook. |
| `/fishing` | Open the Angler's Journal. |
| `/excavation` | Open the excavation menu. |
| `/arcane` | Read personal discoveries, clues and mastery. Research and imbuement have separate station requirements. |

## Island and travel
| Command | What it does |
|---|---|
| `/island create` | Create an island, or return to your existing island. |
| `/island home` | Return to your own island. |
| `/island journal` | Review resource requirements and share skill progress. |
| `/island talents` | Review the shared island talent tree. |
| `/island machines` | Browse machine and spawner recipes. |
| `/island homes` | List your own and joined co-ops. |
| `/island directory` | Browse islands open to visitors. |
| `/spawn` | Travel to the configured hub. |
| `/warps` | List public warps. |

For invitations and visitor permissions, use the [co-op guide](/guide/island-coop).

## Trading and recovery
| Command | What it does |
|---|---|
| `/marks` | Show your spendable currency balance. |
| `/bank` | Open item storage, not a Marks deposit account. |
| `/shops` | Browse merchant supplies and buyback. |
| `/ah` | Browse auctions and their delivery inbox. |
| `/store` | Browse in-game player stores, separate from the website cosmetic store. |
| `/trade <player>` | Request a confirmed item exchange. |
| `/pay <player> <amount>` | Start a payment of whole Marks to an online player. Check the name and amount first. |
| `/grave locate` | Show the latest indexed active grave's location. |
| `/grave recover` | Recover an indexed legacy grave without a fee; make inventory space first. |
| `/spirit` | Choose body recovery or a Spirit Healer resurrection after an adventure-world death. |

Read the [trade guide](/guide/trading-safely) and [spirits and legacy graves](/guide/graves) before moving valuables.

## Expeditions and the campaign
| Command | What it does |
|---|---|
| `/expedition` | Open the expedition board and choose a difficulty. |
| `/expedition start <expedition> [tier]` | Party leaders: start a run with a ready check. |
| `/expedition queue <expedition> [tier]` | Join the finder, alone or with your party. |
| `/expedition ready` / `decline` | Answer a ready check. |
| `/expedition leave` | Leave the queue or the run you are in. |
| `/campaign` | See the season's goals, milestones and server buffs. |
| `/campaign claim` | Collect milestone rewards for your account. |
| `/campaign contribute [goal]` | Hand in the held stack to a goal that collects it. |

## Account and help
| Command | What it does |
|---|---|
| `/weblink` | Generate a private, one-use website linking code. Never post the code publicly. |
| `/report <player> <reason>` | Send a private report to staff. |
| `/settings` | Manage message, social and travel preferences. |

## Wardrobe
| Command | What it does |
|---|---|
| `/cosmetics` | Open companions, trails, name colour, presets and keepsake. |
| `/companion [name]` | Open or summon a companion you own. |
| `/companion dismiss` | Send your companion home. |
| `/trail [name]` | Open or wear a particle trail you own. |
| `/trail off` | Take off your trail. |
| `/emote <name>` | Use an available Cloudkeeper or Starweaver emote for nearby players. |

Commands do not grant ownership or unlock disabled features. Review the in-game menu for your available cosmetics and permissions.
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

Island void rescue returns you safely with your items. Death keeps inventory and XP; adventure worlds use [spirits and resurrection](/guide/graves).'''),
    'arcane-city': dict(summary='The shared hub and the planned city above the clouds. Public services and the finished build are separate.',
        requirements='/spawn travels to the configured hub. The finished city remains a preview.',
        activities='Travel to the current shared hub\nOpen remote merchants with /shops\nCheck /expedition for published destinations',
        points_of_interest='', body='''## Visit the current hub
Use `/spawn` for the server's configured hub. Staff can change its build and available services as the city develops.

## Use the services already exposed in game
`/shops`, `/ah`, `/store` and `/bank` open their respective menus without requiring a completed city district. Physical service NPCs depend on staff placement.

## About this preview
The artwork illustrates the intended Arcane City. It is not an in-game screenshot or a promise that every pictured route, district or NPC is available. Use `/expedition` for currently published travel destinations.'''),
    'expeditions': dict(name='Expeditions', theme='Party adventures', availability='released',
        summary='Short adventures for a party of up to five in a private instance: rooms, bosses, loot, and the way into the Nether and the End.',
        requirements='Open /expedition in game. Some expeditions open only when the season campaign reaches a milestone.',
        activities='Clear rooms of waves and survive timed holds\nSolve lever, chime, plate, target and jump puzzles\nDefeat bosses with telegraphed attacks and claim your own loot\nTry Heroic and Mythic for bigger rewards, then push Mythic keystones\nRaid Cinder Keep with up to eight players\nQueue alone and let the finder fill your group',
        points_of_interest='First Flight, the tutorial across the sky islands\nThe Hollow Barrow, a crypt beneath the old hills\nThornwood Canopy, treetops at night\nThe Sunken Aqueduct, flooded waterworks\nGloomforge Mines, deep galleries and their foremen\nThe Hall of the Three Wardens, a citadel in the sky\nCinder Keep, a raid for up to eight in the Nether',
        body='''## Your party, your instance
Every run is a private copy of the map, built for your group alone. Gather a [party](/guide/parties) of up to five, start from `/expedition`, and answer the ready check. Or queue alone and the finder fills the empty places.

## Into the Nether and the End
Vanilla portals are sealed. The Nether and the End are reached through themed expeditions that run in those dimensions. The campaign may seal one until the server reaches a milestone; the [expedition board](/expeditions) shows what is open now.

## Safe to try
Dying inside an expedition never drops your items, and on Normal and Heroic you simply run back. The [expedition guide](/guide/expeditions) covers difficulty tiers, modifiers and rewards.'''),
}

CODEX_NOTES = {
    'bloodletter': 'Preview effect. It remains dormant while its runtime feature is disabled; its presence on equipment does not override that switch.',
    'veinseeker': 'Preview effect. Eligible natural ores and normal protection checks apply when enabled; player-placed ores are not an unlimited reward source.',
    'chain-harvest': 'Preview effect. Applies to eligible mature crops when enabled, with normal harvest and protection checks.',
    'stormwake': 'Preview Signature. One Signature per compatible item. Binding and the runtime effect have separate feature requirements; the recipe is intentionally undisclosed.',
    'heart-of-the-mountain': 'Preview Signature. Natural-ore eligibility applies. One Signature per compatible item; the recipe is intentionally undisclosed.',
    'last-bastion': 'Preview Signature. A triggered survival effect with a cooldown, not permanent invulnerability. One Signature per compatible item; the recipe is intentionally undisclosed.',
}

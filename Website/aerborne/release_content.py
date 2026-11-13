"""Site content for the November 2026 updates (seed revision 32): the Wardens' Reach, the island and economy systems
that came with it, and patch notes. Applied by seed_updates._rev32 only where staff haven't already written their own."""

REACH_REGION = dict(
    name="The Wardens' Reach", slug="wardens-reach", theme="sky", availability="released", sort_order=1,
    summary="The central world: Highcrown and its Sky Docks, the farms of Goldenreach Vale, and wilder lands out to the Ashen Scar.",
    image="static:img/maps/reach.webp", image_alt="Relief map of the Wardens' Reach, with Highcrown in the south and the Ashen Scar in the middle",
    requirements="Everyone can go: /reach go, or fly from any roost you've found.",
    activities="Quests from the people of Highcrown and Millbrook\nHunt creatures from level 1 to 30\nRare beasts and the Thunderhide world boss\nFlight roosts between the regions\nDig sites, and doors into every expedition",
    points_of_interest="Highcrown and the Sky Docks\nMillbrook and Kingsgate Fields\nThe Huntsman's Barrow and the Stone Rings\nThe Heart Tree, the Great Aqueduct and the Headframe\nThe Ember Gate and the Seal Spire",
    body="""## A world to share
The Wardens' Reach is the land everyone shares. Your island is your home; the Reach is where the stories are. Open the [interactive map](/reach) to see every region, quest giver, flight roost and expedition door.

## Where to start
Arrive at **Highcrown**, the capital, by its Sky Docks. Talk to **Captain Wren** and **Steward Aldric**, then cross the Kingsbridge to **Warden Hale** in Goldenreach Vale. Anyone with a **[!]** over them has quests for you.

## Regions and levels
| Region | Levels |
|---|---|
| Goldenreach Vale | 1-7 |
| The Barrowdowns | 6-11 |
| Thornwood | 10-15 |
| Mistmere | 14-19 |
| Ironspine Highlands | 18-23 |
| Sunscar Breaks | 21-25 |
| The Archive Isle | 22-26 |
| The Ashen Scar | 25-30 |

## Getting around
Find a **flight roost** once and you can fly there from any other: `/reach flights`. Every expedition has its door somewhere in the Reach, and **Thunderhide** comes to the Stone Rings every two hours (`/worldboss go`).
""")

YOUR_ISLAND_BODY = """## Start small, build steadily
Your island begins with a tree, water, lava, crops and a renewable generator. Run `/start` for your first steps and `/island journal` for your next unlock.

## Folk come to visit
Visitors turn up at your island spawn over time, even while you're away. Bring them what they ask for and they pay Marks, island XP and **Favour**. Read [Island visitors and Favour](/guide/island-visitors).

## A farm worth having
The **Farmstead** (`/island farm`) turns surplus crops into Compost, counts your crop milestones and runs weekend farming contests. Read [the Farmstead guide](/guide/the-farmstead).

## Workers, showcases and rankings
Hire workers at the **Workers' Lodge** (`/island workers`) to gather while you're away, display a **showcase** above your island for what you've achieved, and race other co-ops on the weekly board (`/island top`).
"""

WORLD_OVERVIEW_BODY = """## Your island
`/island home` returns to your own home. `/island homes` lists joined co-ops, and `/island directory` lists islands open to visitors. Public visiting does not grant building or storage access.

## The Wardens' Reach
`/reach go` takes you to the central world: Highcrown, Goldenreach Vale and the wilder regions beyond. See the [interactive map](/reach) and the [Wardens' Reach guide](/guide/the-wardens-reach).

## Getting around
Flight roosts carry you between the Reach's regions once you've found them (`/reach flights`). `/warps` lists public warps and `/warp <name>` travels to one; some travel uses a warmup, so stay still until it finishes.

## Expeditions, the Nether and the End
`/expedition` opens the expedition board: short adventures for a party of up to five, each in its own private copy of the map. The Nether and the End are reached this way. Vanilla portals stay sealed, and Nether and End expeditions run in real Nether and End dimensions. Some expeditions open only once the [season campaign](/guide/season-campaign) reaches a milestone. Every expedition also has a door somewhere in the Reach. Read the [expedition guide](/guide/expeditions) before your first run.

## What's on
`/calendar` shows the Double XP Weekend, farming contests, the world boss, festivals and the council vote. The same calendar is on the [website](/calendar).
"""

GUIDES = [
    dict(title="The Wardens' Reach", slug="the-wardens-reach", category="world",
         summary="The central world: where to start, its regions and levels, and how to get around.",
         related="world-overview, world-boss, expeditions", body="""
## Arriving
`/reach go` brings you to **Highcrown** by the Sky Docks. The [interactive map](/reach) shows every region, quest giver, flight roost, dig site, rare beast and expedition door.

## Your first quests
Talk to **Captain Wren** at the docks and **Steward Aldric** in Highcrown, then cross the **Kingsbridge** to **Warden Hale** in Goldenreach Vale. A gold trail on the ground leads you to your next objective, and objective areas are outlined in blue. Anyone with a **[!]** has quests.

## Regions
Goldenreach Vale (levels 1-7) is farmland around Millbrook. Beyond it lie the Barrowdowns, Thornwood, Mistmere, the Ironspine Highlands, the Sunscar Breaks, the Archive Isle and, in the middle, the Ashen Scar (levels 25-30). Creatures get tougher the further out you go.

## Rare beasts
Named beasts return a while after they fall: Gilt-Tooth in Kingsgate Fields, Old Grizzlehide in the orchards, the Scarecrow King, Toll-Captain Brask, and Hollow Jack, who only walks at night. [Thunderhide](/guide/world-boss) is the world boss.

## Flight roosts
Find a roost once and it's yours: `/reach flights` flies you between any roosts you know. `/reach places` lists the places you've discovered.
"""),
    dict(title="Thunderhide, the world boss", slug="world-boss", category="world",
         summary="When Thunderhide comes to the Stone Rings, how to join the fight, and how the reward is shared.",
         related="the-wardens-reach, parties", body="""
## When and where
**Thunderhide** comes to the **Stone Rings** in the Barrowdowns every two hours. The server warns everyone a few minutes before it arrives, and `/calendar` shows the next time.

## Joining the fight
`/worldboss go` takes you to the Stone Rings. Anyone can join; Thunderhide grows tougher with every fighter.

## The reward
Everyone who does a fair share of the damage is paid when it falls, and every kill counts toward island showcases. `/worldboss status` shows how the current fight is going.
"""),
    dict(title="Island visitors and Favour", slug="island-visitors", category="island",
         summary="Who comes to your island, what they want, and how Favour, friendships and milestones work.",
         related="the-farmstead, island-coop", body="""
## Who comes calling
Folk of the Reach visit your island spawn over time, even while you're away, and wander around until you meet them. Right-click a visitor, or open `/island guests`, to see their offer: what they want and what they'll pay. **Accept** it to hand the items over, **refuse** it to send them home, or spend a little Favour to ask for something else.

## Rarities
| Rarity | Comes from | Pays |
|---|---|---|
| Uncommon | Any island | ×1 |
| Rare | Island level 5 | ×1.4 |
| Legendary | Island level 15 | ×2 |
| Mythic | Island level 30 | ×3.5 |
| Special | Double XP Weekends and festivals | ×2.5 |

Rarer visitors ask for more (sometimes a cooked meal, a rare fish or a refined metal, marked ★) and may bring a bonus item. Some visitors only come once your co-op has done something: finished a quest chain, fought Thunderhide, cleared an expedition, reached a crop milestone or won a contest medal. The **visitor log** shows how to meet each one.

## Favour and the Visitor's Stall
Every accepted offer pays **Favour**. Spend it at the **Visitor's Stall** (`/island stall`) on an extra bench, swifter arrivals, a lure, Name Tags, Compost, expedition materials or an XP Booster.

## Friends and milestones
Serve the same visitor 5, 15 and 30 times to make a friend of them, with a gift each time. Milestones for visitors met and offers accepted pay more Favour, an extra bench and faster arrivals.
"""),
    dict(title="The Farmstead", slug="the-farmstead", category="island",
         summary="The composter, crop milestones and weekend farming contests.",
         related="island-visitors, farming, collections", body="""
## The composter
Open `/island composter` and add surplus crops, seeds, leaves and saplings. Every 40 organic matter becomes one **Compost**, one every four minutes, even while you're away. Right-click a crop with Compost to ripen every crop in a 5×5 patch.

## Crop milestones
Every ripe crop you harvest on an island counts toward that crop's milestone (`/island crops`). Each of the six tiers adds a 1.5% chance of an extra crop and pays Favour. Placed or unripe crops don't count.

## Farming contests
Through the Double XP Weekend, a twenty-minute contest on three crops runs every two hours. Harvest as many as you can: each crop pays bronze, silver or gold medals by how much you bring in, and the top farmer of each crop goes up a medal. Spend medals at the Contest Stand (`/island contest`) on farming gear, Compost, Favour and boosters.
"""),
    dict(title="The Bazaar", slug="the-bazaar", category="economy",
         summary="Buy and sell bulk goods instantly, or with your own buy orders and sell offers.",
         related="trading, collections", body="""
## What it's for
The **Bazaar** (`/bazaar`) is the market for bulk goods: crops, ore, logs, fish, mob drops and expedition materials. Single items like gear still go to the auction house (`/ah`) and player stores.

## Buying and selling now
Pick a good to see its **buy price** (the cheapest offer) and **sell price** (the best bid). **Buy** takes from the cheapest offers; **Sell inventory** sells everything you hold to the best bids.

## Your own orders
A **buy order** names a price and an amount; your Marks are held until sellers fill it, and you claim what it bought from **Your orders**. A **sell offer** puts your goods up at your price; Marks arrive as they sell. Orders can't undercut the book: if someone already sells cheaper, buy from them instead. Click an order once to claim, or twice to cancel it.

## The tax
Every sale pays a 1.25% tax. Live prices are on the [Bazaar page](/market).
"""),
    dict(title="Collections", slug="collections", category="skills",
         summary="Collection tiers for what you mine, fell, catch and slay, and the titles they earn.",
         related="the-farmstead, skills-and-talents", body="""
## What counts
Every natural ore and stone you mine, log you fell, fish you catch and beast you slay counts toward that item's collection (`/collections`). Farming collections are your crop milestones. Blocks you placed yourself don't count.

## Tiers and rewards
Each tier pays Marks, skill XP in the matching skill, and Favour. Rare catches (the custom fish) and the Reach's own beasts have collections of their own.

## Titles
Bring every collection in a category to tier III to earn its title: **Harvest Keeper**, **Stoneheart**, **Timberwarden**, **Tidecaller** or **Beastbane**. Reach fifteen tiers in all and a collector comes to visit your island.
"""),
    dict(title="The Aerborne client", slug="aerborne-client", category="getting-started", sort_order=60,
         summary="The optional mod: what it adds and how to install it.",
         related="connection-help", body="""
## Optional, and better with it
Aerborne plays fine in plain Minecraft. The **Aerborne client** is an optional Fabric mod for Minecraft Java 26.2 that adds an RPG HUD, target frames, card menus, drawn talent trees, a quest book, a minimap and waypoints. It never gives an advantage: everything still goes through the server.

## Installing
1. Make a Fabric profile for Minecraft 26.2.
2. Put Fabric API and the Aerborne client in its `mods` folder.
3. Launch it and join `play.aerborne.net`.

Press **B** for the Aerborne menu, **H** to show or hide the HUD and **J** for waypoints. See the [client page](/client) for the download and more detail.
"""),
    dict(title="Companion bonds", slug="companion-bonds", category="getting-started", sort_order=61,
         summary="How your companion's bond grows, and the names, tricks and looks it unlocks.",
         related="aerborne-client", body="""
## Time together
The longer your companion keeps you company (while you're out and about, not standing idle), the stronger its bond: `/companion bond` shows its level, from 1 to 10.

## What bonds unlock
| Level | Unlocks |
|---|---|
| 2 | A name over its head: `/companion name` |
| 3 | Trick: Spin |
| 4 | Sparkles that follow it |
| 6 | Trick: Hop |
| 8 | Trick: Twirl |
| 10 | A radiant glow |

Tricks play with `/companion trick <spin|hop|twirl>`. Bonds only change how a companion looks and behaves; companions never give stats.
"""),
    dict(title="The calendar, festivals and the Wardens' Council", slug="calendar-and-council", category="world",
         summary="What's on, the monthly festivals, and voting for the council seat.",
         related="the-wardens-reach, the-farmstead", body="""
## The Reach Calendar
`/calendar` shows everything on the schedule: the Double XP Weekend, farming contests, Thunderhide, the current festival, the council vote and the Monday island race payout. The [Reach Calendar](/calendar) page shows it too.

## Festivals
Each month has its own festival for a few days mid-month, from Frostfall in January to Winterlight in December. Special visitors come to every island, and anyone who joins in (collecting, harvesting, serving visitors) earns that festival's title and a purse of Favour.

## The Wardens' Council
For the first five days of each month, everyone votes for one of three candidates (`/council`). The winner holds the seat for the month, and their perk helps the whole server: visitors arriving sooner, richer crop milestones, a lower Bazaar tax, better collection rewards, more Favour or more farming contests.
"""),
]

NEWS = [
    dict(title="Visitors, the Farmstead and names of your own", slug="update-island-visitors-and-the-farmstead", category="major",
         version_tag="2026-11-12", featured=None, summary="Folk of the Reach now visit your island in person, the Farmstead arrives, and you can name your gear and your beasts.",
         related="island-visitors, the-farmstead", body="""
## Island visitors
Visitors now turn up at your island spawn and wander around until you meet them. They come in five rarities, from Uncommon to Mythic, plus Special visitors on Double XP Weekends; some only come once your co-op has done something special. Accept, refuse or ask for something else, and earn **Favour** to spend at the Visitor's Stall. [Read the guide](/guide/island-visitors).

## The Farmstead
A composter that turns surplus crops into Compost, crop milestones that raise your yield, and twenty-minute farming contests through the weekend with medals for farming gear. [Read the guide](/guide/the-farmstead).

## Names of your own
Nickname your gear at the forge, in plain colours or gradients, and shift-right-click a beast on your island with a Name Tag to name it.

## Also in this update
A Double XP Weekend every week, island milestones and prestige stars, 43 island showcases, the Workers' Lodge, weekly island rankings, and **Thunderhide**, the world boss at the Stone Rings.
"""),
    dict(title="The Bazaar, Collections and the Wardens' Council", slug="update-bazaar-collections-and-council", category="major",
         version_tag="2026-11-12b", featured=None, summary="A market for bulk goods, collections for everything you gather, companion bonds, monthly festivals and a council to vote for.",
         related="the-bazaar, collections, calendar-and-council", body="""
## The Bazaar
Buy and sell crops, ore, logs, fish and drops instantly, or post your own buy orders and sell offers. Prices are live on the [Bazaar page](/market). [Read the guide](/guide/the-bazaar).

## Collections
Natural ore, logs, fish and beasts all count, and every tier pays Marks, skill XP and Favour. A whole category at tier III earns a title. [Read the guide](/guide/collections).

## Companion bonds
Companions grow closer the more time you spend together, unlocking a name, tricks, sparkles and a glow. Just for looks, never stats. [Read the guide](/guide/companion-bonds).

## Calendar, festivals and the council
`/calendar` shows everything coming up. Each month brings a festival with its own title, and the first five days of each month are for voting on the Wardens' Council, whose perk helps everyone. [Read the guide](/guide/calendar-and-council).

## On the website
An [interactive map of the Wardens' Reach](/reach), a page for the [Aerborne client](/client), and a new menu to find things faster.
"""),
]

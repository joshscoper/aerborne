# Expedition metallurgy

Bring expedition materials home and use a **furnace or blast furnace** to refine them. Open
`/recipes` and select **Smelting & Refining** for the input, output and processing time. Workshop
Recipes shows the alloy and equipment patterns, their exact custom ingredients and skill requirements.

## Refining

One input makes one output. Use ordinary furnace fuel. Blast furnaces take half the listed time.
Refining itself has no player skill requirement and can use hoppers; recipe skill requirements apply
when crafting alloys and equipment. Personally collecting proven refined outputs grants Smithing XP
as well as the configured vanilla furnace experience. Hoppers do not assign skill XP to an absent player.
See [Player stats and experience](PLAYER_STATS.md) for receipt protection and crafting awards.

| Expedition material | Refined output | Furnace time | Used for |
| --- | --- | ---: | --- |
| Gloomsteel Ore | Gloomsteel Ingot | 10 seconds | All four alloys |
| Cinder Shard | Cindersteel Ingot | 12 seconds | Cindersteel Alloy |
| Tidepearl | Tideglass | 10 seconds | Tideforged Alloy |
| Sigil Shard | Tempered Sigil | 12 seconds | Warded Alloy |
| Thornheart Sap | Briar Resin | 8 seconds | Briarbound Hoe and Tideforged Rod |

## Alloys

Craft one alloy at a table using the pattern in `/recipes`.

| Alloy | Ingredients | Smithing |
| --- | --- | ---: |
| Gloomsteel Alloy | 2 Gloomsteel Ingots, 1 ordinary Iron Ingot, 1 Blasting Powder | 24 |
| Tideforged Alloy | 2 Tideglass, 1 Gloomsteel Ingot, 1 Sluice Cog | 26 |
| Warded Alloy | 2 Tempered Sigils, 1 Gloomsteel Alloy, 1 Warden's Seal | 30 |
| Cindersteel Alloy | 2 Cindersteel Ingots, 1 Gloomsteel Ingot, 1 Smoldering Core | 36 |

## Equipment

These are additional crafting routes, separate from expedition set forging and boss signatures.
Existing expedition drops, set bonuses and Expedition Forge costs are unchanged.

| Equipment | Ingredients | Requirement | Role |
| --- | --- | --- | --- |
| Briarbound Hoe | 2 Briar Resin, 1 Matron's Fang, 2 Briar Silk | Farming 20 | Gathering power and extra harvests |
| Gloomsteel Mattock | 3 Gloomsteel Alloys, 1 stick, 1 Foreman's Lampglow | Mining 24 | Mining power and geode discovery |
| Gloomsteel Hauberk | 7 Gloomsteel Alloys, 1 Blasting Powder | Smithing 26 | Defense and durability protection |
| Tideforged Rod | 3 Tideforged Alloys, 1 Briar Resin, 1 Briar Silk, 1 Tidewarden's Scale | Fishing 28 | Fishing power, luck and durability |
| Warded Buckler | 3 Warded Alloys, 1 Archive Key, 1 ordinary Oak Planks | Smithing 32 | Defense, durability and skill experience |
| Cindersteel Cleaver | 3 Cindersteel Alloys, 1 Sovereign's Ember, 1 stick | Smithing 38 | Melee damage and execute damage |

Only equipment has gemstone sockets. Materials, refined products, alloys, currencies and consumables
always have zero, even if an old catalog entry omitted or explicitly configured sockets. The normal
item updater refreshes old tooltips as items are encountered. Equipment socket capacities and installed
gems retain their existing behavior; stored gem payloads are never erased by this fix.

## Configuration and item protection

`content/metallurgy.yml` is installed in the plugin data folder on first startup. Existing files are
validated and kept; server operators can edit recipes there. `content/relics.yml` upgrades to schema 14,
backs up the previous file as `relics.yml.before-v14.bak`, and adds missing material/equipment IDs
without overwriting existing item definitions.

Each smelting entry accepts `smithing-xp` (1–1000) per personally collected item. The bundled defaults
are Gloomsteel 10, Cindersteel 16, Tideglass 8, Tempered Sigil 12 and Briar Resin 6. Existing entries
that omit this field grant 8 per item. The `experience` field remains vanilla furnace XP. Custom
smelting outputs must be unique so an output stack has one unambiguous skill-XP rate.

Crafting ingredients written as `item:gloomsteel_ingot` require the authenticated catalog identity,
origin, schema and correct base material. A renamed ordinary ingot, a different custom ingot sharing
the same texture, or an editor preview cannot substitute. Display-name and lore changes do not prevent
an authentic item from working. Ordinary recipe symbols still require ordinary ingredients.

Paper recipe predicates perform native ingredient matching. The crafting and furnace event guards
revalidate before processing; the general protection against using special items in vanilla recipes,
as fuel or in automated skill-gated crafting remains active. Crafting uses native inventory transactions,
including mirroring, offsets and shift crafting. Smelting uses native furnace consumption and output
capacity checks, with no inventory mutation by scheduled callbacks.

Verification: `:nerdsmp-paper:test :nerdsmp-paper:shadowJar` and
`scripts/verification/run-metallurgy-probe.ps1` (disposable Paper 26.2 server, loopback port 25598).
The probe reuses an already approved local test EULA, starts no player client and does not install
anything in an existing server. Crafting lookup and furnace transactions are exercised on real Paper;
authenticated-client clicks, shift crafting and recipe-journal navigation remain manual gameplay checks.

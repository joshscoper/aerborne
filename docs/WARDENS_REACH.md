# The Wardens' Reach (the new central map)

The Reach is the story map that links skyblock islands and expeditions. It replaces the old "MMORPG" map (the
Highcrown Isles) once it is complete. The plugin generates it from code and populates it itself: no world
download, no staff placement. The full design (research, lore, zones, story, phases) is the Obsidian vault in
[`docs/vault`](vault/Home.md); open that folder as a vault in Obsidian.

**Status (phase 1).** The whole continent's terrain exists, with every zone shaped and planted. Highcrown (the
capital) and Goldenreach Vale (levels 1–7) are built and populated. The other zones are terrain only until their
phases ([vault: Phases](vault/80%20Build/Phases.md)).

## Making the world

| Command | Does |
| --- | --- |
| `/reach generate` | Creates the world `aerborne_reach` and pre-generates all of it (about 30,000 chunks, five or six minutes on a fast machine). When it finishes, the world pack populates it: zones, NPCs, warps and the arrival spawn. |
| `/reach stop` | Stops pre-generation (run `generate` again to continue). |
| `/reach rebuild <feature\|all>` | Redraws a built feature (`highcrown`, `goldenreach`, `bridge_N`) into the live world from the current code, a slice per tick. |
| `/reach tp <x> <z>` | Staff teleport to the ground at x, z in the Reach. |
| `/reach where` | Which zone you are in and its levels. |
| `/reach status` | Revision, world and pre-generation progress. |
| `/reach` or `/reach go` | Travel to the sky docks (`nerdsmp.reach.visit`; staff only until cutover). |
| `/reach places` | The places you have discovered. |

The world loads with its generator on every start once it exists. Settings (`config.yml`):

```yaml
reach:
  enabled: true
  world: aerborne_reach
  pregen-in-flight: 24
```

Changing the generator's output means bumping `ReachGenerator.REVISION`: a new revision is a new world folder
(`aerborne_reach_r2`), never a regeneration in place.

## What's in phase 1

- **Terrain.** A floating continent of seven zones around the Ashen Scar (a crater with a rift through the
  middle), the Archive Isle to the east, broken-off islets round the edges and a cloud sea below. The Goldwater
  river runs from the downs through the Vale and pours off the lip at Highfall beside Highcrown; Mistmere's lake
  spills off the north edge. Roads join every zone, with stone bridges wherever they cross water.
- **Highcrown.** The Kingsbridge over the Goldwater gorge, the Kingsgate with its twin towers and emblem, the
  plaza and fountain, the market avenue with stalls and timber-framed houses, the keep on its crag with the empty
  crown, Crafters' Row, the Tethered Gull inn, and the sky docks with three piers, two tall towers and two moored
  airships.
- **Goldenreach Vale.** Millbrook on the river (inn, reeve's hall, chapel, smithy, mill), terraced field plots
  with hedges, windmills on the high ground (one burned), Thistle Farm, the Brindle Orchards and cider press, the
  Tollkeepers' camp and toll house, the burned Black Granary, the Old Granary Cellars dungeon under it, the Hollow
  Road and the Warden's post at the Kingsbridge.
- **Zones.** Highcrown, Millbrook and Thistle Farm are safe. Eight combat zones run from level 1 at the Kingsgate
  to level 7 in the Cellars, each with its own creatures (`content/reach-mobs.yml` plus the old map's gnawers,
  boars and scarecrows).
- **NPCs.** The service NPCs stand in Highcrown's districts (the arrival line, the Gate Ring, the Hall of
  Callings, Crafters' Row, the exchange stalls, the keep). Twenty-four new characters
  (`content/reach-npcs.yml`) give the Reach's story and gossip. Watch guards patrol the avenue and Millbrook's
  green.
- **Quests.** Eighteen (`content/reach-quests.yml`): the prologue from Captain Wren to the Steward, First Flight
  and the Kingsgate; the main chain *The Quiet Fields* through the Vale to the Scarecrow King; three side chains;
  two dailies. A new objective type, `clear`, counts finishing an expedition (`targets: [first_flight]`).
- **Places.** Sixteen named places; walking into one shows *Discovered*, pays a few Marks and counts toward
  `/reach places`.
- **World chests.** Real chests in four tiers, placed by the builds: tier I by the roads, II off them, III high
  up or deep in the Cellars. Opening one rolls that player's own loot and Marks (plus a first-find bonus), then it
  refills for them after 10, 20, 45 or 120 minutes. A sparkle shows over chests you can open.
- **Encounters.** Rares and the dungeon boss appear at their spots on their own timers when someone is near:
  Toll-Captain Brask (5 min), the Scarecrow King (12 min), Old Grizzlehide (30 min), Gilt-Tooth (25 min) and
  Hollow Jack (40 min, at night only).
- **Landmarks.** Each outer zone's hero landmark stands as a named place with a world chest (vault: Landmarks):
  - the Huntsman's Barrow, the Stone Rings and Stonewatch (the Barrowdowns);
  - the Heart Tree (Thornwood);
  - the Great Aqueduct (Mistmere);
  - the Deep Foreman's Headframe (the Ironspine);
  - the Sunscar Arch (the Sunscar Breaks);
  - the Ember Gate (the Ashen Scar);
  - the Seal Spire (the Archive Isle).

  A waypoint (camp, ruined waystation or cairn) with a chest stands about every 350 blocks of road through those
  zones.
- **The Wardens' Bridge.** Where the Spine ends on the Sunscar's east lip, a paved bridgehead between pale towers
  looks out over the cloud to the Archive Isle. The bridge's first spans still hang from the towers on their chains
  and end, broken, over the drop. The isle keeps its own broken half. Travellers cross by air.
- **The Rift.** The Nether's corruption spills out of the Rift across the Ashen Scar in long tongues: netherrack,
  crimson nylium, nether wart and magma, with crimson roots, fungi and the odd eternal fire. The Rift's walls bleed
  with magma, crying obsidian and nether gold, and lava pours over its lip into the dark. No cloud lies under it.
- **Expedition doors.** Six landmarks hold an expedition's door: a starlit gateway in a carved frame, braziers
  burning before it and the expedition's name and level lettered on their pedestals. Stepping in opens that
  expedition's board (`/expedition info`); the player is set back on the threshold, facing the door.
  - the Huntsman's Barrow: the Hollow Barrow (level 8);
  - the Heart Tree: Thornwood Canopy (level 12);
  - the Great Aqueduct's sunken cistern court: the Sunken Aqueduct (level 16);
  - the Headframe's shaft: Gloomforge Mines (level 20);
  - the Seal Spire: the Hall of the Three Wardens (level 24);
  - the Ember Gate: the Cinder Keep raid (level 26). It is a great square portal of black stone on a raised dais,
    between colossal guardians at the head of a grand stair.
- **The skyfarer's line.** Anyone who falls off the edge is hauled back to the last solid ground they stood on,
  with no fall damage.

## Climate: no day and night
The Reach has no day and night cycle. The world's clock stands at noon, and each zone shows its own hour, weather
and air to each player, eased from one zone's hour to the next as they walk (`ReachClimate`):

| Zone | Hour | Weather | Air |
| --- | --- | --- | --- |
| Highcrown | late morning | clear | a bell now and then |
| Goldenreach Vale | golden early morning | clear | drifting pollen, bees |
| The Barrowdowns | long sunset | clear | low mist on the heather, a wolf far off |
| Thornwood | midday under rain | rain | spores and drip under the canopy |
| Mistmere | grey dawn | drizzle | mist on the water, frogs |
| The Ironspine | bright morning | clear | snow blowing on the heights, wind |
| The Sunscar Breaks | hot afternoon | clear | red dust on the wind |
| The Ashen Scar | ember-lit night | none | falling ash and embers, the deltas' rumble |
| The Archive Isle | still twilight | clear | motes of light, chimes |

A few places keep their own hour inside their zone: Windmill Ridge is always moonlit, so Hollow Jack still walks
there. Night-only encounters read the hour of their own place. Leaving the Reach restores a player's own time and
weather.

## Life
No vanilla creatures live in the Reach. The game rules stop natural spawning and spawners, and `ReachGameplay`
cancels the other ways the game brings creatures in (patrols, raids, lightning, traps, jockeys). Everything that
moves is the Reach's own: its NPCs, the zones' creatures and the encounters, all spawned by the plugin. The
country's life is in each zone's air and sound (see Climate).

## Protection (anti-grief)
The Reach is public land, held by the plugin's own claim protection.
- **What players can't do:**
  - break or place blocks;
  - pour or take water or lava;
  - light fires;
  - trample farmland or pick the fields' crops;
  - take flowers from pots, edit signs (they're waxed), or use containers, cauldrons or composters.
- **What still works:** doors, trapdoors, gates, buttons and bells; NPCs; and the world chests, which roll their
  own loot.
- **The world itself:** mobs can't change blocks, explosions and fire don't damage it, and the gamerules turn off
  mob griefing, fire spread and random ticks. Crops don't grow, leaves don't decay and grass doesn't spread.
- **Mode and config:** the protection holds in any gameplay mode, because `claims.protected-worlds` (default
  `[aerborne_reach]`) always reserves the world.
- **Who bypasses it:** staff with `nerdsmp.claim.bypass` (ops by default), so builders can still edit the map.

A bot playing as a plain player checks this on the scratch server: it is denied breaking, building, water, fire,
pot flowers, trampling and crop picking, and it can still open doors.

The one exception is the dig sites (below): their marked blocks can be dug by anyone.

## Flight points
Ten roosts stand across the Reach, at Highcrown, Millbrook, each outer zone's landmark and both ends of the
Wardens' Bridge. Each roost is a perch post with a bell. Highcrown's is Kestrel's navigator waystone on the plaza.
- **Learning one:** walk within ten blocks of a roost.
- **Flying:** ring its bell (or touch the waystone), or use `/reach flights` beside it. The list shows every flight
  point, the known ones ready to fly to. Choosing one puts you on a harnessed sky beast. It rises from the roost,
  crosses high over the country on a smooth arc and sets you down at the other roost, in about a minute for the
  longest crossing.
- **Safety** (`ReachFlights`):
  - You can't fall, be hurt or get off on the way.
  - The beast is never saved with the world.
  - Each flight is written to `reach-flights.yml` as it leaves. Quitting, being kicked or a server stop lands you
    at the destination. A crash sets you down there when you next join.
  - Dying, changing world, being teleported by something else or the beast vanishing ends the flight without
    leaving you in the air.

## Dig sites
Five excavation pits, each a terraced pit in its own small safe zone (`ReachDigSites`):

| Pit | Zone | What's dug |
| --- | --- | --- |
| Goldwater Clay Pits | the Barrowdowns' stretch of the river | clay and gravel |
| The Barrow Diggings | by the Huntsman's Barrow | earth |
| The Foreman's Open Cut | the Ironspine | iron, coal and copper ore in its faces, gravel on its floors |
| Redsand Diggings | the Sunscar | red sand |
| Mistmere Mudflats | Mistmere | mud and clay |

When the Reach starts, each pit's diggable blocks are registered with the dig-site service as nodes:
- only those blocks can be broken there, in survival;
- each regrows 45 seconds after it's dug;
- digging soil trains Excavation, and digging ore trains Mining.

The dig-site service now runs in every gameplay mode, not only skyblock.

## Editor mode (for staff)
Staff with `nerdsmp.reach.admin` can fix the map by hand as they walk it, and the fix survives the world being
generated again.

| Command | What it does |
| --- | --- |
| `/reach edit` | Turns editor mode on: creative mode, the protection bypass and a debug stick. Run it again to turn it off. |
| `/reach edit undo` | Takes back your last change. |
| `/reach edit revert <radius>` | Puts every edited block within the radius back the way the generator made it. |
| `/reach edit note <text>` | Leaves a located note (place, facing, zone, build and your words) in `reach-notes.yml` for a builder to fix in the generator. |
| `/reach edit notes` | Lists the latest notes. |
| `/reach edit count` | Says how many edits are kept. |

While editor mode is on, every block you place, break, fill, empty, turn with the stick or letter is recorded.
Its final state, and what the generator first put there, go to the overlay `reach-edits.yml` in the plugin's folder
(`ReachEdits`).
- **When it applies:** the overlay is laid down last whenever a chunk of the Reach is generated, and again after
  `/reach rebuild`.
- **Baking edits into the map:** copy `reach-edits.yml` to `nerdsmp-paper/src/main/resources/reach/edits.yml`.
  Every server then gets them.

## How it's built (for developers)

| Piece | Class |
| --- | --- |
| Terrain as pure functions (zones, heights, rivers, lake, roads, underside, fields) | `reach/ReachTerrain` |
| The chunk generator, surface painter, plants and biomes | `ReachGenerator`, `ReachSurface`, `ReachFlora` |
| Built features and their named spots | `ReachFeatures`, `ReachCanvas`, `ReachBuilds` |
| Building kit (houses, towers, stalls, lamps), trees | `ReachKit`, `ReachBrush`, `ReachTrees` |
| Highcrown, its castle, cathedral and bank; the Vale; bridges | `HighcrownCity`, `HighcrownCastle`, `HighcrownCathedral`, `HighcrownExchequer`, `GoldenreachVale`, `ReachBridges` |
| Zone landmarks, roadside waypoints | `ReachLandmarks`, `ReachRoadside` |
| Each zone's hour, weather and ambience | `ReachClimate` |
| Paths from every door to the streets | `ReachPaths` |
| The world pack, built in code | `ReachPack` (registered with `WorldPackService`, which now also matches a world by name) |
| Places, world chests, encounters | `ReachGameplay` (progress in `plugins/NerdSMP/reach-progress.yml`) |
| World, pre-generation, rebuild, rescue, `/reach` | `ReachService` |

Tests: `ReachTerrainTest` (shape, water never leaks, rivers pour off the lip; `REACH_ATLAS=<file.png>` draws the
map from above), `ReachBuildsTest` (every spot has a floor and headroom; `REACH_SPOTS=<file.json>` dumps the spots),
`ReachPackTest` (zones validate, every NPC, mob and quest reference resolves). For checking a build's layout and
elevations without generating a world:
- `REACH_PLAN=<file.png>` draws a feature from above;
- `REACH_PLAN_VIEW=west|east|north|south` draws a side view instead;
- `REACH_PLAN_BOX`, `REACH_PLAN_SCALE` and `REACH_PLAN_Y` set the area, scale and height range.

## Cutover from the MMORPG map

Agreed (vault `Decisions`): the Reach becomes the central world and the old map is retired with its records kept.
Old quest progress stays in the files, but those quests aren't offered, and the old map stays loadable for staff.
That switch happens in the last phase. Until then both maps can be loaded together; the service NPCs then stand in
both, so raise `npcs.maximum-instances` if needed.

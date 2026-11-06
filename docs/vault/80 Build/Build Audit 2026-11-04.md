---
tags: [build, audit]
---
# Build audit, 2026-11-04

The user's verdict on the second pass was "meh, not wow": too little detail, too little attention to where things
go and how they flow, buildings built into the terrain, a spawn city that doesn't feel like a city, roads that don't
feel like roads. Measured against [[Shovel Study]] and [[Build Style Guide]].

## Faults, worst first
1. **Buildings sunk into terrain.** Houses take a floor height and then build only their walls, so where the ground
   is higher than the floor the hillside runs straight into the walls and doors. Unacceptable anywhere.
   **Fix:** grade every building's site before it's built: cut the ground to the floor in a ring round the
   footprint, with a stone retaining wall where the cut is deep; fill and build a plinth where the ground is low. A
   test fails if terrain stands inside any building's footprint above its floor.
2. **Roads are painted stripes.** A road is a dirt-path band blended into the slope. It has no edges, no cuttings or
   embankments, no bridges' approaches, no milestones, signposts or lamps.
   **Fix:** level the carriageway to sharp edges; add retaining walls in cuttings and stone faces on embankments;
   give it ruts and verges in the country and paving near towns; add milestones, signposts at junctions, lamps
   near towns and wayside shrines.
3. **The city has no purpose.**
   - Most of Highcrown is the same generic house.
   - There are no landmarks besides the gate and the keep, and no districts you could tell apart.
   - There is no industry, no temple, and no walls round the city.

   **Fix:** a temple whose spire marks the skyline, warehouses and cranes in the dock quarter, real workshops on
   Crafters' Row, walls and towers round the lip, and a landmark at the end of every street.
4. **The houses are thin.** Shallow eaves, single timbers and no balconies. Roof colour doesn't change by district.
   **Fix:** deep eaves on diagonal struts, timbers doubled at corners and floor lines with beam ends, balconies,
   and roof colour chosen per district.
5. **No street life.** Streets are empty between buildings.
   **Fix:** a props library (carts, sacks, crates, signs, laundry lines, flower boxes, benches, troughs, wells) set
   along streets in groups.
6. **Flat city.** Shovel's cities climb in tiers. Later: terrace Highcrown from the docks up to the keep. That
   change touches every street height, so it waits until the faults above are fixed.

## Status (end of 2026-11-04)
1. **Fixed.** `ReachKit.site` grades every building: it cuts and walls the ground above the floor and fills a
   faced plinth below it. The test `noBuildingIsSunkIntoTheTerrain` fails if terrain stands inside any lot.
2. **Fixed.** Roads are engineered:
   - a level carriageway with sharp edges, and stone walls in cuttings and on embankments;
   - ruts, a crown and gravel edges in the country, and setts near towns;
   - `ReachRoadside` lines them with field fences and gates, hedgerows, dry-stone walls, milestones, lamps into the
     towns, shrines, benches, cairns, marsh lights and embankment parapets.
3. **Fixed.** Highcrown is rebuilt on three terraces with a purpose for each ward (see [[Highcrown#Terraces (as built, 2026-11-04)]]).
   It has the Lantern Fane, the Market Hall, the Hall of Callings, Crafters' Row workshops with trade props,
   warehouses with hoists, a ring wall with towers, and grand stairs.
4. **Improved.** The house kit has:
   - a second eave course on struts;
   - doubled corner posts;
   - top-floor balconies with doors and planters;
   - hanging trade signs;
   - a red-roofed style, so the wards read by roof colour.
5. **Improved.** `ReachProps` adds carts, crates, troughs, planters, benches, woodpiles, notice boards, smith's yards,
   market crates and lamps. The city sets them along street edges, never within two blocks of a door.
6. **Done.** The city is terraced (this was the "later" item).

Still open: laundry lines and hanging greenery, bigger clumped canopies and roots on hero trees, and the west bank
across the gorge (planned as the Memorial Terrace by Highfall).

## Fourth review (2026-11-05)
The user asked for more detail and for professional standard. Answered as follows:
- **The church.** It's now a real cathedral (see [[Highcrown#The fourth review (2026-11-05)]]).
- **The bank.** It's a marble temple front with a counting hall and a vault door.
- **Plain houses.** Houses now vary their plaster and weather their roofs, and add plinths, braces and shutters.
- **Roads into hills.** A road's cutting has a retaining wall of at most three blocks, then the hillside battered
  back one-for-one to the natural ground. Before, the ground kept its full height beside the road behind a sheer
  wall up to twenty blocks tall. Embankments over dry ground get one-for-one side slopes.
- **Terrain that stops.** Zone borders were ruled lines where one ground palette and forest ended and the next
  began. `ReachTerrain.groundZone` now pushes each border back and forth by noise, up to twenty blocks either way,
  so grounds and woods interfinger. The Ashen Scar's ash frays over its rim the same way.
- **Rough trees.**
  - Leaf clusters used to roughen their edges with per-block random noise (speckle and holes). They now swell and
    dip in a few broad lumps.
  - Limbs used to step diagonally (blocks touching at corners). They're now face-joined.
  - Limbs and roots use all-bark wood.
- **Empty zones.** Each zone's hero landmark now stands where the zone notes put it, as a named place with a chest
  (see [[Landmarks]]). A waypoint stands about every 350 blocks of road through the outer zones: a traveller's
  camp, a ruined waystation or a cairn, each with a chest of the zone's tier.

## Fifth review (2026-11-05)
The cathedral landed. The faults raised, and the fixes:
- **The bank looked foreign.** It's restyled in the city's stone and slate (see
  [[Highcrown#The fifth review (2026-11-05)]]).
- **No parapet past the churchyard.** The terrace-wall pass skipped any edge another build had reserved. It now
  walls every edge where nothing is built.
- **Terrain cut off outside Highcrown.** The capital's terrace steps leaked into the town platform's falloff and
  across the gorge. They now ease into a smooth slope outside the walls.
- **Ship wings in the piers.** Moored ships furl their wings.
- **Cart fronts in other things.** A cart checks it fits before it is placed, and keeps away from doors.
- **Houses not joined to a road.** Every outside door is pathed to the nearest street (`ReachPaths`).
- **The warehouse in the trade district.** The Market Hall is rebuilt as a cloth hall with a belfry.

## Sixth review (2026-11-05)
The user cleared work on the whole world. The faults, and the fixes:
- **A stub path outside the walls.** The city's streets now stop inside the walls, and the posterns are closed.
- **Wall piers standing in the river.** The Kingsbridge was rebuilt with slimmer piers, round arches and a lower
  arch tier. The west wall's run down into the river is gone.
- **A pit between walls.** Earth banks now rise against the citadel stairs' parapets.
- **Doors facing outwards.** The gate towers now open into the city.
- **The bank's top.** A new gable, pinnacles and lantern.
- **No day and night.** Each zone keeps its own hour, weather and ambience (`ReachClimate`).
- **Roads that flow.** The routes' corners are rounded by Chaikin cutting, and Ember Road joins the Spine's curve.
- **A living world.** Herds and wildlife suited to each zone, plus each zone's ambience.
- **Terrain clipping.** The aqueduct runs into its hill through a tunnel instead of a cut trench.

## Seventh review (2026-11-05)
The faults, and the fixes:
- **The Kingsbridge looked unreal.** It's one great arch now, with solid spandrels, buttresses and corbelled
  refuges for the Wardens (see [[Highcrown#The seventh review (2026-11-05)]]).
- **Paths that missed their bridges.** Road bridges follow the road's own line from bank to bank, and their walk
  meets the road at each end (`ReachBridges`).
- **Millbrook's wheels sat in the ground and did nothing.** The city's wheel is gone. The mill stands on the
  riverbank, clear of the road, its wheel turning in a stone race of real water on an axle into the mill wall.
- **A bandit camp on the road out of Millbrook.** The Tollkeepers' camp now sits at least 21 blocks from any road,
  and each landmark keeps its own clearance. A test fails if any build stands on a road (`noBuildBlocksARoad`).
- **The Black Granary looked rough.** The soot climbs in broad bands instead of speckle. A long barn joins the
  tower, its near half burned to the rafters and its far half still roofed.
- **Rough roads that stopped for no reason.** The country road is a dirt path core with coarse dirt and gravel at
  its edges, in broad patches. Where it climbs a block, a slab makes the step. Lots are kept off the carriageway.
- **Buildings you couldn't enter.** Doors with nowhere to stand are walled. A test checks every remaining door
  has standing room on both sides (`everyDoorCanBeWalkedThrough`).
- **Trees and foliage.** Spruces are whorled cones with drooping tips, oaks carry a cluster at mid-limb, and
  flowers grow in fewer, broader drifts.
- **Expedition entrances were easy to miss.** Each is now a starlit gateway with braziers, banners and lettered
  pedestals (see [[Landmarks#Expedition doors]]).
- **Vanilla mobs spawned.** None do now, and the herds are gone. Only the Reach's own creatures and NPCs appear.

## Eighth review (2026-11-05)
The faults, and the fixes:
- **Blocks not properly placed.** Every plan is now secured before it's settled (`ReachSupport`):
  - lanterns hang on a chain from the ceiling over them, from a bracket out of the wall beside them, or stand on a
    floor;
  - chains run up to their beam, and loose links turn to what they touch;
  - ladders lean on a wall, and pots sit on a window box;
  - anything that can't be held up goes.

  The windmills' caps rest on their towers, and their sails hang on a shaft. The Heart Tree's thorns grow from its
  bark, and the headframe's braces are unbroken timbers. Tests fail on anything hanging from nothing or on any piece
  of a build adrift (`ReachSupportTest`).
- **Doors in the way.** A door whose doorway holds a roof, stair or wall is walled up on every plan. A test checks
  every doorway is clear at feet and head, over a floor or one step, including other builds' blocks.
- **The river under the bridge.** A build's air below the water line stays water, so an arch never empties a river.
- **Holes in Millbrook's bridge.** Road bridge decks are laid by measuring each block's distance from the road's
  line, with no gaps on a bend.
- **The mill's water.** The wheel now turns in the river's current, between a stone revetment and a pier carrying
  its axle, its paddles in the water.
- **Camps floating and clipping.** Every camp stands on a levelled campground eased into the slope. The Choir camp
  moved clear of the granary, its barn and loading bay, and the road.
- **The Hollow Barrow.** Rebuilt (see [[Landmarks]]). Its crown stones stand on the mound, and an avenue of stones
  leads to it from the road.
- **Trees.** Roots climb down to the ground or stop at a drop. Hanging strands are cave vines that hang
  unbroken. Thick spruces keep their spike.
- **The junction in the hills.** Ember Road leaves the Spine at the Spine's own height. Half steps are laid only on
  paved roads, since no slab looks like dirt. Parapets and verge walls run in lengths instead of lone posts.
- **The mesa road that went nowhere.** The Spine now runs on to the Wardens' Bridge.
- **The Ember Gate.** Rebuilt as a great portal over the corrupted Rift (see [[Landmarks]]).
- **New: dig sites, flight points and editor mode.** See the guide (`docs/WARDENS_REACH.md`).


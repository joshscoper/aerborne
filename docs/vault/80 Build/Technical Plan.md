---
tags: [build, technical]
status: proposal
---
# Technical plan

How the plugin generates and populates the Reach. Principle carried over from expeditions: **no binary worlds in
the repo**. The world comes from code, so any server can make it and every change is reviewable.

## 1. The world: `ReachGenerator`
A Paper `ChunkGenerator` for a world named `aerborne_reach` (configurable), with a fixed seed so everything is
deterministic.

- **Terrain from pure functions** (`ReachTerrain`, Bukkit-free, unit-testable):
  - `mask(x, z)`: is this column part of the continent or an isle? A noisy outline (domain-warped ellipse with lobes
    per zone), with sheer edges and a lip.
  - `top(x, z)`: surface height. Each zone has its own height recipe (terraced slopes for Goldenreach, rolling
    downs with tors, forest hills, lake basin, ridged peaks, canyon mesas, the Scar's crater and rift), blended
    across borders with smooth weights so there are no seams. Rivers and roads are splines that cut and grade the
    surface.
  - `bottom(x, z)`: the underside, thick in the middle and thin at the lip, with hanging drips, strata bands and
    roots near the edge.
  - `water(x, z)`: lake and river levels; falls where a river meets the lip.
  - `surface(x, y, z)`: the top few blocks, painted per zone with value ramps (coarse dirt and gravel on banks and
    roads, moss under forest, podzol under spruce, terraced field soil) instead of vanilla's surface rules.
- **Biomes** from a `BiomeProvider` per zone, for grass tint, sky and fog colour, ambient particles and music:
  plains and sunflower plains (Goldenreach), meadow (Barrowdowns), old-growth spruce taiga and dark forest
  (Thornwood), river and swamp (Mistmere), stony and jagged peaks (Ironspine), badlands (Sunscar), basalt deltas for
  falling ash (the Scar), cherry grove (the Archive Isle's courts).
- **Vanilla generation off** (noise, surface, caves, decorations, structures, mobs): no dungeon spawners, no stray
  villages. Trees, plants and ores come from our own placement.
- **Features** (`ReachFeatures`): every town, building, tree grove, bridge, camp, ruin, dungeon and chest is a
  feature with a bounding box and a deterministic `place(clip)` that writes only the blocks inside the chunk being
  generated. Nothing is held in memory for the whole map; a feature runs once per chunk it touches. Kits are
  code, like the expedition kits (`CanopyKit`, `MineKit`, `SkyKit`…), reused where they fit.
- **Custom trees**: procedural big trees (tapered trunks, buttress roots, branch arms, leaf clusters) per species,
  so groves look like ref 7 rather than vanilla trees.
- **Clouds** at about y −40: chunky white blobs, purely scenery.
- **Pre-generation**: `/reach generate` makes the world and walks every chunk in a spiral off the main thread's
  budget (a few chunks a tick), with a progress bar; a rerun skips finished regions. About 18,000 chunks.
- **Revisions**: the generator has a `REVISION`. A new revision makes a new world folder (`aerborne_reach_r2`)
  and the pack moves over; nothing is regenerated in place (the expeditions' `ExpeditionWorld` rule).

## 2. The world pack: `worldpacks/reach.yml`
The existing world-pack system (zones, NPC anchors with patrols, warps, node sites, spawn) with:
- **Match by world**, as well as by seed and landmarks: `match: {world: aerborne_reach, generator: reach}`.
- **`auto-points`**: a combat zone can ask for N spawn points; the pack finds them on real ground at apply time
  (solid floor, two blocks of air, not water, not inside a building, slope ≤ 1).
- **New sections** (each a small Bukkit-free record parsed and validated like the others):

| Section | Holds | Used by |
| --- | --- | --- |
| `pois` | id, title, centre, radius, adventurer XP | discovery |
| `chests` | position, tier (1–4), zone | world chests |
| `camps` | centre, members `[mob, dx, dz]`, leash radius, respawn | world encounters |
| `rares` | mob, spawn point, roam radius, respawn minutes, night-only | world encounters |
| `dungeons` | zone, boss mob, boss point, respawn, exit point, first-clear reward | open-world dungeons |
| `doors` | expedition id, door box | expedition doors |
| `skiffs` | id, title, landing point | skiff travel |

A test builds the pack against `ReachTerrain` and the features, without a server, and checks every NPC anchor,
chest, door and skiff stands on a real floor and inside its zone.

## 3. New systems
| System | What it does | Built on |
| --- | --- | --- |
| **Discovery** | Entering a POI flashes "Discovered: Windmill Ridge", grants adventurer XP once, and fills a Places page in `/journal` with counts per zone | Zone titles, quest journal |
| **World chests** | Real chests placed by the generator. Opening one shows that player's own loot, rolled by tier and zone level, with a glimmer when it's ready and a cooldown (I: 10 min, II: 20 min, III: 45 min, IV: 2 h). First find gives a bonus. Protected from breaking | Expedition private chests (`RunLoot`), loot profiles, relic tables |
| **World encounters** | Camps (linked aggro, leash, respawn when cleared), patrol pairs on roads, rares (long respawn, night-only option, a nearby "rare" notice) | `CustomMobManager.spawnEncounter`, the expedition mob engine's threat, casts and telegraphs brought out of runs |
| **Open-world dungeons** | Boss respawn, tier III chest per player, first-clear reward per player, rope/lift out | World encounters, world chests |
| **Expedition doors** | Walking into a door's archway opens the expedition menu for that expedition; runs return you to the door | Expedition menu and return store |
| **Quest objectives** | `loot` (quest drops with rising chance, counted in the log, no inventory clutter), `interact` (right-click marked blocks), `discover` (a POI), `clear` (finish an expedition) | QuestService, QuestGuide (markers, trail) |
| **Skiffs** | A skiff master flies you between discovered skiff points for a few Marks (short fade, then teleport) | Travel manager, NPC actions |
| **The skyfarer's line** | Falling below the lip (y < 0) returns you to the last solid ground you stood on, with no fall damage and a short slow-falling | Map fence (replaces the square border) |
| **Arrival** | `/reach` and the island dock send islanders to Highcrown's sky docks; the world's spawn is there | Travel manager |

## 4. Content files
- `content/reach-npcs.yml`, `reach-quests.yml`, `reach-mobs.yml`, `reach-loot.yml` (same formats as the world-*
  files), merged like `world-npcs.yml` is today.
- The service NPC definitions are shared; only their anchors change world.

## 5. Tests and checks
- Unit: terrain functions (no holes in the continent, every zone reachable on foot from Highcrown, roads never
  steeper than 1 in 1, rivers always flow downhill), pack validity, anchors on floors, walk routes two abreast and
  three high (the Mines lesson), quest references resolve.
- Scratch server: generate the world, bots walk the main route, do the first chain, open chests, clear the
  Granary Cellars (see memory `bot-e2e-testing`).
- Previews: the camera bot and prismarine viewer for screenshots; the user's own eyes in real lighting.

## 6. Cutover from the MMORPG map
Both worlds can be loaded together. A config flag `reach.central: true` makes the Reach the spawn world, moves the
service NPC anchors to Highcrown-in-the-Reach, and leaves the old map's pack in place for staff. See
[[Phases#Checkpoint questions]] for what happens to the old map's quests.

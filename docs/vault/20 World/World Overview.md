---
tags: [world, design]
status: proposal
---
# World overview: the Wardens' Reach

> When Pyrrhax set the old world burning, the Wardens tore the survivors' lands free and raised them above the
> smoke. The largest piece they lifted whole: a country of fields, downs, forest, lakes and mountains, with a
> wound in its middle where the fire still shows through. Its people call it the Reach.

![[reach-layout.png]]

## Shape
- **One sky continent**, about 2,300 × 2,100 blocks, centred on (0, 0) in a world of its own (`aerborne_reach`).
  The top surface sits between y 70 and y 200; the underside hangs down to about y −20, hanging rock and roots.
  Below is a **cloud sea** of chunky white clouds (ref 1), then nothing.
- **A bowl around a wound.** The zones form a ring round **the Ashen Scar**, a burned crater in the middle of the
  continent with a rift at its heart. Smoke rises from the rift all day and glows at night: every zone can see
  it, and it's where the story ends.
- **Outlying isles** (Decision, 2026-11-03): **the Archive Isle** off the east coast, reached by the Wardens' Bridge
  and by skiff. More isles come with later seasons (see [[#Later isles]]).
- **The edges are the border.** Walk off the lip and you fall into cloud; a skyfarer's line hauls you back to where
  you last stood (like First Flight's safety line). No walls or barriers needed.

## Level ladder
Zones go clockwise from the capital. Each zone's story ends at the door of the expedition with the same level.

| Zone | Levels | Biome feel | Main hub | Expedition door | Open-world dungeon |
| --- | --- | --- | --- | --- | --- |
| [[Highcrown]] | safe | Capital on a promontory, sky docks | Highcrown | First Flight (2), from Wren's dock | – |
| [[Goldenreach Vale]] | 1–7 | Golden wheat terraces, mills, a river | Millbrook | – | The Old Granary Cellars |
| [[The Barrowdowns]] | 6–11 | Heather moor, standing stones, mist | Lantern Hill | The Hollow Barrow (8) | Ledmoor Crypts |
| [[Thornwood]] | 10–15 | Giant old-growth forest | Bough Hollow | Thornwood Canopy (12) | Weaver's Hollow |
| [[Mistmere]] | 14–19 | Great lake, terracotta port, marsh, aqueduct | Saltreach | The Sunken Aqueduct (16) | Smugglers' Grotto |
| [[Ironspine Highlands]] | 18–23 | Mountains, quarries, mine towns | Cragholm | Gloomforge Mines (20) | The Old Dig |
| [[Sunscar Breaks]] | 21–25 | Baked red canyons near the Scar | Dustwatch | – (world boss) | Brimstone Warrens |
| [[The Archive Isle]] | 22–26 | Pale sky ruins, levitating stone | Vell's Camp | Hall of the Three Wardens (24) | – (the isle is the dungeon) |
| [[The Ashen Scar]] | 25–30 | Ash, basalt, black glass, fire | Last Light Bastion | Cinder Keep raid (26) | The Choir's Undercroft |

Adventurer levels run to 100 and skills to 50; this season's map tops out at 30. Later seasons add isles above it.

## Layout (block coordinates, north is −z)
| Zone | Rough centre | Rough extent |
| --- | --- | --- |
| Highcrown | (0, 900) | promontory x −180..180, z 760..1080; docks on the south lip |
| Goldenreach Vale | (0, 520) | x −340..340, z 300..760 |
| The Barrowdowns | (−650, 420) | x −1050..−340, z 80..760 |
| Thornwood | (−760, −280) | x −1120..−380, z −700..80 |
| Mistmere | (−200, −800) | x −620..220, z −1050..−520 |
| Ironspine Highlands | (560, −620) | x 220..1080, z −1000..−200 |
| Sunscar Breaks | (780, 260) | x 380..1120, z −200..700 |
| The Ashen Scar | (0, −140) | bowl x −340..360, z −520..260, rift ~120 across at the centre |
| The Archive Isle | (1520, −80) | its own island, radius ~260, top y ~150–230 |

## Passes (the doors between zones)
| From → to | Pass | What it is |
| --- | --- | --- |
| Highcrown → Goldenreach | **Kingsgate** | The capital's great north gate at the top of the market avenue (ref 4) |
| Goldenreach → Barrowdowns | **The Hollow Road** | A sunken lane through low hills where the Huntsman held the line |
| Barrowdowns → Thornwood | **Stonewatch Ford** | A paved ford under a ruined watchtower |
| Thornwood → Mistmere | **Rootgate** | The road passes under an arch of a fallen giant's roots |
| Mistmere → Ironspine | **Aqueduct Road** | A road along the top of the aqueduct's arches |
| Ironspine → Sunscar | **Brimstone Stair** | A switchback stair cut down a red cliff |
| Ironspine / Sunscar → Ashen Scar | **Emberpass** | The only way into the bowl, held by Last Light Bastion |
| Sunscar → Archive Isle | **The Wardens' Bridge** | A ruined chain bridge across the sky, plus skiffs |

Between passes, borders are ridges, cliffs, a river or (round the Scar) **the Brimwall**, a ring of black cliffs.

## Water
- **The Goldwater** rises in the Barrowdown hills, winds east through Goldenreach and drops off the southern lip as
  **Highfall**, beside Highcrown's docks: the first thing new arrivals see.
- **Mistmere** is a great lake in the north. It spills off the north edge as **the Long Fall**, and its aqueduct
  carries cold water east and down toward the Scar (the Sunken Aqueduct's lore).
- Smaller streams fall off the lip all round; every fall is a landmark and many hide a chest behind them.

## Layers (ref 2's Y-section)
| Layer | Y | What's there |
| --- | --- | --- |
| Sky isles | 150–260 | The Archive Isle, later isles, airship lanes |
| Surface | 70–200 | Zones, towns, roads |
| Under the lip | 20–90 | Cliff paths, root caves, hanging villages and tier IV chests under the edges |
| The underside | −20–40 | Hanging rock, the Scar's rift going through, later content |
| Cloud sea | about −30 | Clouds; falling here returns you to the lip |

## Travel
- **On foot** along the road spine; every fork has a signpost.
- **Skiffs** (small airships) between discovered hubs, run by the Skyfarers' Guild for a few Marks. You discover a
  skiff point by talking to its master.
- **Your island**: the Highcrown sky docks are where islanders land. `/island` takes you home; the docks bring you
  back.

## Later isles
The Frostveil Isle (frozen, 28–34), the Drift (a chain of small isles, 30–36) and the Underside proper are
reserved
for later seasons. Nothing is built for them this season.

---
tags: [research, building, technique]
---
# Builders and terraformers

Earlier sessions researched top builders for the expedition maps (memory notes `builder-technique-notes`,
`dwarven-hall-technique-notes` and `halls-of-valor-reference`). This note copies their rules here and adds what we
learned about terrain at the scale of a whole map.

## Process (studios and level designers)
- **Massing first, then texture.** Define the shape and volume before any detail block. The classic fault is "the
  box": a plain cuboid with detail stuck on. *(S14)*
- **Blockout → playtest → detail.** The standard pro workflow; you only feel a map at the end, so test the blockout.
  *(S15)*
- **fWhip** spends hours on massing in plain concrete before any texture. Start from the biggest wow element.
- **MCME:** block out in wool, pillars proud, openings one back, then a ruining pass.
- **Level design book:** light tells players where to go; the main exit is lit; dark means dead end.
- **Teams:** a head builder sets the design, builders claim tasks (Wynncraft) *(S13)*. For us the "head builder" is
  the zone note plus the [[Build Style Guide]].

## Judging (the Bakery's four categories, 5 points each)
Technical skill, composition, theme, creativity. Points lost for: no slab or stair smoothing on big forms;
smoothing in some places and not others; shading that ignores one light source; flat low-effort terrain floors;
details in a line instead of placed; solid unbroken block areas; focal points finished less than the rest;
nothing in the middle of the height. Rewarded: small readable things next to big ones; dark recesses; light that
falls off with distance.

## Terrain at map scale
- **Pipeline used by terraformers:** a heightmap from Gaea or World Machine (erosion done there) → WorldPainter
  (import with headroom, paint biomes and layers, add custom trees) → in-game sculpting with VoxelSniper or FAWE
  (erosion brushes melt and fill) → texturing → decoration last. *(S16, S17)*
- **Heightmaps can't make overhangs** or caves: those are added afterwards. *(S18)*
- **Nothing in nature is straight.** Irregular edges on hills, cliffs and lakes; vary elevation, depth and slope.
  Cliffs get overhangs. *(S19)*
- **Rivers erode their banks:** coarse dirt and plants on the banks, not pristine grass; bigger rivers have wider
  bends. *(S19)*
- **Value ramps, not splatter** (Conquest and ArdaCraft gradient guides): only neighbouring tones touch, in
  clusters, darkest at the foot of a slope.
- **No run longer than 7 blocks straight.** Hero elements 25% bigger than feels right.

## Floating islands
- **The underside is half the build.** Never a flat pancake: sculpt cliffs, caves and hanging gardens. *(S20)*
- **Solid inside**, layered stone, dirt, gravel, deepslate and ores, not a hollow shell. *(S20)*
- **Think about what's below** (clouds, sea, another realm). *(S20)*
- Our own additions from the expedition sky maps (First Flight): hanging rock with strata bands, roots and vines
  trailing below, falls pouring off the lip into cloud.

## Towns
- Pick a **grid or organic** layout to suit the land: raise towns on platforms or set them in basins. *(S21)*
- A **central district of 15–25 buildings** with a market at its heart, not a whole city at once. *(S21)*
- **Industry by the water:** docks, mills, smithy and warehouses at the waterfront; wells, fountains and canals in
  plazas. *(S21)*
- Frame first, walls and windows last; curves and diagonals for medieval houses. *(S21)*
- From the refs: one roof colour per town (ref 6), stone plinths under timber (ref 7), tall narrow towers (ref 5),
  a gate as the hero of the arrival avenue (ref 4).

## Recipes already proven on our expedition maps
- **Quarry (MrMattRanger):** tuff → cobble → andesite → light grey concrete powder; deepslate and tuff only in
  corners and cracks, stair streaks; spruce props; coarse dirt round the rim; a crane and a green-roofed shack.
- **Underground (TrixyBlox):** floor and ceiling spikes joined into arches; a ceiling crack letting light in;
  glowing mushrooms and crystals in varied sizes; curved bridges with rock beneath; water through arches.
- **Detail (TrixyBlox 50 tips):** depth in and out; sideways barrels as banded beams; buttons as rivets; open fence
  gates as braces; trapdoor arch supports; composite lamps; campfire under lava for smoke; dead coral in stone.
- **Dwarven (fWhip, Necron):** one axis aimed at the hero; carve into the cliff, never free-standing; a 9-wide
  flared doorway; tuff, cyan terracotta, deepslate, waxed copper bands; frog lights behind glass.

## Rules for the Reach
Every build passes the judges' faults list before anyone calls it done, and is compared with the reference images
side by side, shape first. See [[Build Style Guide]] for the per-zone palettes and the checklist.

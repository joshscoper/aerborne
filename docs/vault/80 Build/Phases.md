---
tags: [build, plan]
status: proposal
---
# Phases

Big and long, so it goes in slices, each ending with bot tests, preview screenshots, docs, a commit and a stop for
the user's playtest.

| Phase | What | Ends with |
| --- | --- | --- |
| 0 | Research and design (this vault) | The user agrees the plan and the first slice |
| **1** | **First slice: the whole continent's terrain plus Highcrown and Goldenreach Vale, fully populated.** Generator, world-pack extensions, the new systems (discovery, world chests, camps and rares, open-world dungeons, expedition doors, new quest objectives, the skyfarer's line, arrival). Highcrown built and staffed; Goldenreach built with Millbrook, the terraces, the windmills, the orchards, the Tollroad, the Black Granary and the Old Granary Cellars; ~35 NPCs, ~18 quests, the Vale's creatures, 13 chests, 10 POIs. The other zones exist as shaped, planted terrain with roads, but no towns yet | A playtest on the test server |
| 2 | The Barrowdowns, Lantern Hill, Ledmoor Crypts, the Hollow Barrow's door; skiffs | Playtest |
| 3 | Thornwood, Bough Hollow, Weaver's Hollow, the Heart Tree and Canopy door | Playtest |
| 4 | Mistmere, Saltreach (the port city), Smugglers' Grotto, the aqueduct and its door | Playtest |
| 5 | Ironspine Highlands and Sunscar Breaks, Cragholm, the Old Dig, Brimstone Warrens, the Gloomforge door, the Brimfather | Playtest |
| 6 | The Archive Isle, the Ashen Scar, the Choir's Undercroft, the Ember Gate, the epilogue; cutover from MMORPG | Production release |

## Inside phase 1 (order)
1. `ReachTerrain` and `ReachGenerator`: the continent blockout, all zones' shapes, rivers and roads. **Look at it**
   (previews from the air and from the road) before going further.
2. Highcrown and Goldenreach massing (blockout), looked at from the arrival path; then texture and detail.
3. Pack extensions and the new systems, each with unit tests.
4. Content: NPCs, quests, mobs, loot, chests, POIs.
5. Bot runs on the scratch server: arrival, the main chain, chests, the Cellars.
6. Docs (`docs/WARDENS_REACH.md`, this vault), commit, then stop for the playtest.

## Checkpoint questions
To agree with the user before phase 1 starts:
1. Is the first slice right (the whole continent's terrain, Highcrown and Goldenreach finished)?
2. What happens to the old MMORPG map and its 58 quests at cutover?
3. Fast travel: paid skiffs between discovered hubs, free skiffs, or none?
4. Are zone levels guidance only (WoW-style, anyone can walk anywhere), or are passes gated by the story?

# Renewable Excavation

Excavation now uses the `excavation-power` on shovels and brushes for its equipment XP bonus. Base block XP remains unchanged. Ordinary player-placed, replaced, and moved soil still gives no XP or finds.

Staff can register the existing soil in a shared-world zone as a dig site. Registered blocks give the normal Excavation XP, finds, geodes, job work, and quest progress. After a break, each block has its own cooldown, then regrows in place. No pit, building, world, NPC, or warp is generated automatically.

## Set up a peaceful dig site

Build your pit first, with a solid floor and safe access. Use dirt, coarse/rooted dirt, grass blocks, sand, red sand, gravel, clay, mud, or soul sand. Suspicious sand/gravel are excluded because their archaeology loot needs separate handling.

1. Run `/digsite wand` (or `/zone wand`) to get the staff-only Area Wand.
2. Left-click a block for **pos1** and right-click the opposite corner for **pos2**. The selection is an inclusive 3D cuboid, shared by zone and dig-site commands. Clicked blocks are never broken or activated by the wand.
3. Check the one-burst outline or run `/zone selection`. Use `/zone clear` to discard the selection.
4. Run `/digsite create training_pit 20`. Only eligible soil within that selected cuboid is captured, with a 20-second cooldown.
5. Stand at a safe entrance and run `/setwarp <parent-zone-id>` using the zone ID shown after saving.

When the cuboid is inside an existing enabled zone, that zone is reused without changing its bounds, mobs, safety, or other settings. Outside existing zones, saving creates a matching enabled safe zone named after the dig site; it still generates no terrain. Selections crossing zone boundaries are rejected instead of overwriting them. A safe region covers exactly the selected volume: include the walking and headroom space if you want the safety rules to cover players above the soil, not only the soil layer.

The same wand supports `/zone create <id>` for encounter areas; those zones keep their ordinary disabled-until-configured workflow. Typed `/zone pos1 [target]` and `/zone pos2 [target]` remain available. Selections are temporary and clear when staff disconnect.

The older `/digsite create <id> <zone> [seconds]` syntax still captures that entire existing zone. With a numeric zone ID, include the fourth cooldown argument to distinguish it from a wand cooldown. A scan may cover at most 32,768 cells and capture at most 2,048 soil nodes. Within a large encounter zone, the wand can capture a smaller cuboid without rescanning the whole region. Zone regions cannot overlap.

Staff authoring uses `nerdsmp.zone.admin`. Private island worlds cannot host these public sites. All selected chunks must already be loaded; registration never force-loads terrain.

## Player controls

- `/digsite list` (or `/digsites`) lists open sites.
- `/digsite info training_pit` shows location, readiness, cooldown, and blocked nodes.
- `/excavation` has a Dig Sites button. A matching saved warp provides a travel link.

Digging requires Survival and a loaded player profile. Only captured nodes bypass public-terrain protection; everything around the pit remains protected. A better excavation tool improves the matching skill's XP rather than Mining XP.

## Maintenance and safety

Use `/digsite disable training_pit` before editing. `/digsite enable training_pit` resumes the existing catalog. To capture a changed layout, disable it, use `/digsite delete training_pit confirm`, then create a new snapshot. Deleting stops renewal but does not replace or remove terrain.

Regrowth only restores empty cells with their exact captured block data. It waits if a player, living mob, or vehicle occupies the cell and never replaces a foreign block. Placement, pistons, fluids, explosions, and natural transformations cannot normally alter active nodes. Corrupt cooldown markers fail closed for staff review.

Definitions live in `plugins/NerdSMP/dig-sites.yml`. Generation IDs prevent old markers from controlling recreated sites. Cooldowns live in chunk persistent data and survive normal saves/restarts. Renewal checks at most 128 loaded cells per 10 ticks, without chunk tickets; very large catalogs can add sweep delay to the stated cooldown. Host power-loss durability and multiplayer/load acceptance remain separate tests.

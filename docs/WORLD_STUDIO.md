# World workshop

`/worldstudio` opens the builder's dashboard. It connects encounter zones, dig sites, the NPC catalogue, and public destinations. Your team still builds the actual map by hand. Opening the workshop never builds terrain, teleports a player, or spawns a preview mob.

Shortcuts:

- `/worldstudio zones`
- `/worldstudio zone <id>`
- `/worldstudio digsites`
- `/worldstudio npcs`
- `/worldstudio warps`

## Create an encounter

1. Choose **Get area wand**. Left-click a block for position 1 and right-click another for position 2. Include the encounter's floor, walking area, and overhead space in the cuboid.
2. Open **Encounter zones → New zone from selection** and enter an ID such as `cinder_orchard`. The zone is saved disabled.
3. Stand on each desired spawn landing, reopen the zone, and choose **Spawn points → Add point at my feet**. Exact position and facing are recorded. No teleport is performed.
4. Choose **Mob roster → Add mob**. Filter authored or vanilla creatures, or search an ID. Set level and spawn weight in the draft, then confirm **Save roster entry**.
5. Tune the zone's title, population cap, respawn delay, and activation radius. Select **Outline zone** to see nearby loaded edges and points, visible only to you.
6. Choose **Enable zone** and confirm. A combat zone needs at least one valid mob and one spawn point. The shared world must already be loaded.

Every point draws from the same weighted roster. Use separate zones for fixed encounters with different populations. A higher weight increases a creature's share of spawn rolls; it is not an additional spawn count. World bosses outside the reviewed zone catalogue need their dedicated encounter logic.

The spawn controller still requires loaded chunks, nearby players, a supported clear landing, and the normal zone protections. The editor does not bypass these rules. A combat zone cannot lose its last point or mob while enabled; disable it first.

## Capture a dig site

Build the area first, select its cuboid with the same wand, then choose **Dig sites → Capture selected soil**. Enter an ID and renewal delay and review the confirmation.

The existing dig-site service scans up to 32,768 selected blocks in already loaded chunks and captures eligible soil, not arbitrary build materials. A selection inside an existing enabled parent zone keeps that zone's settings. Outside existing zones, it can create a peaceful parent. Partial overlap is rejected. A site supports at most 2,048 captured nodes.

The site page shows ready, cooling, blocked, and inactive node counts. Enable, disable, and remove actions require confirmation. Removing a site stops renewal but does not erase or restore terrain. Removing its parent zone leaves the site saved but inactive while its parent is absent.

## Place NPCs

**NPC catalogue** opens the existing searchable placement catalogue. Browse shopkeepers, guides, and quest givers, inspect a definition, then use its **Place here** control. Existing NPC account, footprint, placement, and persistence checks still apply. Opening the world workshop does not place NPCs automatically.

## Set public destinations

**Warps and spawn** lists saved destinations, including ones whose worlds are currently unavailable. Inspecting an unavailable destination does not load its world or silently replace it with another world.

- **Set warp at my feet** creates a named public warp, or asks you to confirm replacing that warp.
- A warp page offers **Replace with my position** and confirmed removal.
- **Set public spawn here** confirms the central spawn and the world's fallback spawn together. It does not move online players.

Stay at the intended position and facing until confirmation. If you move, turn, or another staff member changes that destination, reopen the action. Public warps and spawn cannot be placed on private islands. There is deliberately no instant teleport button in these authoring views.

## Permissions and editing safety

The workshop requires `nerdsmp.studio.world` and an authenticated, loaded player profile. Mutations additionally retain their original permissions:

- Encounter zones and dig sites: `nerdsmp.zone.admin`.
- NPC catalogue and placement: `nerdsmp.npc.admin`.
- Public destinations: `nerdsmp.travel.admin`.

Designers with these permissions publish directly. There is no mandatory reviewer step. Destructive changes, encounter enable/disable changes, roster publication, and destination replacement use confirmations. Title and numeric zone edits publish after valid input.

Views are read-only inventories: moving items into them is not a content-editing operation. Chat answers to editor prompts are private, session-bound, permission-checked, and expire. Each edit compares the saved definition it was opened against; another staff member's change requires reopening the editor. A failed zone write leaves its previously published settings active.

Saved-zone previews use at most 204 edge samples plus 128 spawn-point markers, within 64 blocks of the viewer and only in loaded chunks. They create no persistent entities or chunk tickets.

## Verification scope

`WorldStudioEditsTest` covers immutable field edits, stale definitions, catalogue pagination, roster replacement, spawn-point validation, active-encounter safeguards, and bounded outlines. Full two-staff GUI journeys, actual proximity-based spawning on your final map, and NPC placement should also be played through on staging. Do not treat unit tests as proof that an unconfigured map is ready for release.

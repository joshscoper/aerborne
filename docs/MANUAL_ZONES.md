# Manual hubs, warps and mob zones

Build or import the shared hub and adventure areas, place NPCs yourself, and save their travel destinations. The plugin provides travel and bounded encounter spawning; it does not build the map for you.

`expeditions.generated-worlds` defaults to `false`. Existing generated prototype worlds with valid manifests are retained without regenerating their terrain. Existing worlds, islands, player progress, NPC placements and saved warps are not reset. The old generated-world switch remains an explicit preview option. Do not remove world directories referenced by an existing manifest.

## Permissions and names

- `nerdsmp.travel.admin`: set/remove public warps and spawn. A player must stand at the destination.
- `nerdsmp.zone.admin`: manage zones. Console can edit existing zones; selecting corners and adding points require a player.

Zone IDs use 1–48 lowercase letters, numbers, underscores or dashes. Warp names allow letters, numbers, underscores or dashes (1–24 characters) and are saved lowercase. Zone titles are plain text, up to 64 characters.

## Central hub

Stand on the finished arrival pad, facing the direction players should look:

```text
/setspawn
/setwarp hearthport
```

`/spawn` uses saved public spawn; `/warp hearthport` uses the saved destination and facing. `/warps` lists saved warps. In Skyblock, `/expedition` opens the saved-warp menu. A warp does not create a zone, and a zone does not create a warp. Neither can be placed in the active private-island world.

Use `/setwarp <existing-name>` to move a destination and `/delwarp <name>` to remove it. `/setspawn` also updates the chosen world's fallback spawn if the world accepts the change. An unavailable destination world is reported, not silently replaced with a different world.

To define the peaceful hub, stand at one lower corner of its three-dimensional volume and run `/zone pos1`. Move to the opposite upper corner, including the full playable height, then:

```text
/zone pos2
/zone create hearthport
/zone title hearthport Hearthport
/zone safe hearthport on
/zone enable hearthport
```

`safe on` disables managed zone mob spawning, blocks ordinary player damage (except void and explicit kill/suicide), hostile targeting, player attacks and splash/cloud effects on players. Managed encounters cannot attack across a zone boundary. This is not a terrain-protection flag or a purge of unrelated mobs; existing claim and public-world building protections still apply. NPCs stay manually placed. PvP is disabled inside enabled public zones, including combat zones.

Zones cannot overlap in the same world, even when disabled. Place combat regions beside the hub, not inside it. Adjacent regions may share an edge plane without sharing a block. Vertically separated floors can be separate zones.

## Crypt encounter

Finish the room, walkways and return route first. At one lower corner run `/zone pos1`; at the opposite upper corner:

```text
/zone pos2
/zone create crypts
/zone title crypts The Forgotten Crypts
```

The zone starts disabled. Include mob headroom and combat space in the selection; selecting two ground-level corners creates a one-block-high box that cannot fit ordinary mobs.

Stand at each intended spawn's **feet position** on a solid, unobstructed floor, face the intended direction, and run:

```text
/zone point crypts add
```

Move to a different spot before adding another point. The exact position and facing are saved. The spawner does not search for a replacement location or change terrain. Leave space for the full mob, not just its center. Water, hazards, occupied spaces and unsupported landings are skipped.

Add the roster:

```text
/zone mob crypts add vanilla:zombie 6 8
/zone mob crypts add vanilla:skeleton 3 10
/zone mob crypts add custom:veil_stalker 1 12
/zone cap crypts 8
/zone respawn crypts 25
/zone radius crypts 48
/zone info crypts
/zone enable crypts
```

The final numbers are **weight, then level**. Weights 6:3:1 select those mobs at approximately 60%, 30% and 10%, not guaranteed quotas. Re-adding an ID updates its weight/level. Custom IDs must exist in loaded content and pass ground-spawn validation. Bosses, flying/aquatic types, evoker summons and breeze wind-charge encounters are excluded from this first ground-spawn release. Tab completion suggests valid choices.

Stand at the intended safe arrival point and use `/setwarp crypts`. Editing a mob table does not move the warp. The normal RPG mob and loot systems handle rewards; a zone is not a separate loot-table editor or campaign-unlock rule.

## Spawning behavior

- Only enabled, non-safe zones spawn mobs; combat zones need at least one point and one mob entry.
- A session-ready Survival or Adventure player must be inside the zone and within a point's three-dimensional activation radius. Creative and Spectator staff do not activate encounters.
- The spawner uses loaded chunks only, never generates terrain or keeps chunks loaded.
- Each point holds at most one managed mob. Concurrency is bounded by point count, zone cap and the global cap of 256.
- Respawn delay starts when the point's mob dies or is removed. Failed or blocked attempts retry later without occupying capacity. Scheduling is once per second with bounded work.
- Managed mobs cannot be led across region boundaries to attack elsewhere. Cross-boundary combat/projectiles and direct ability damage are guarded. Managed mobs cannot pick up items, change blocks, breed, transform or split to bypass capacity.
- After 30 seconds without an eligible player inside, the zone's managed mobs are removed. Chunk/world unload and orderly plugin shutdown remove them too. These are encounters, not persistent pets or livestock.
- Editing, disabling or deleting a zone retires its managed mobs on the next scheduler pass. Unrelated mobs, builds and NPCs are not deleted. Old marked encounter leftovers are cleaned when their chunks load.

## Editing

```text
/zone list
/zone list 2
/zone info crypts
/zone disable crypts
/zone point crypts remove 2
/zone mob crypts remove vanilla:skeleton
/zone mob crypts add vanilla:stray 3 15
/zone enable crypts
```

Point indices start at **1**, as shown by `info`. Disable a zone before removing its final point or mob; enabled combat zones must stay valid. Bounds are fixed at creation. To redraw them, disable/delete the definition, select new corners and recreate it:

```text
/zone delete crypts confirm
```

Deletion removes zone settings only, not warps, terrain, builds, unrelated mobs or NPCs. Keep backups; there is no automatic undo of the settings.

| Setting | Limit |
| --- | --- |
| Zones | 128 total |
| Points / mob entries | 128 / 32 per zone |
| Mob cap | 1–64 per zone; 256 globally |
| Respawn / activation radius | 5–3,600 seconds / 8–128 blocks |
| Mob level / weight | 1–100 / 1–10,000 |
| Horizontal extent / volume | 2,048 blocks per axis / 16,777,216 blocks |
| Vertical bounds | Y -64 through 319 |

## Persistence and opening checks

Definitions live in `plugins/NerdSMP/zones.yml`, schema version 1, keyed to world UUIDs. Unloaded worlds do not lose their definitions. Edits are written atomically before becoming active; write failure leaves the previous settings active and reports the failure. Corrupt, ambiguous, unsupported, overlapping or out-of-range files are rejected rather than silently reset. Back up the file and stop the server before hand editing; there is no live reload command for YAML edits.

Before inviting players:

1. Visit every warp in Survival: check footing, facing, headroom and return access.
2. Test every mob type at each point, including its full body size and combat space.
3. Use two actual players to test hub boundaries, ranged attacks, crowding, kills and respawns.
4. Leave zones, unload/reload chunks, then restart: settings should persist without duplicated encounters.
5. Check manually placed NPCs and shops. Moving public spawn may change shop proximity checks; no NPC is automatically relocated.

These tools do not certify handcrafted-map quality or live multiplayer encounter balance.

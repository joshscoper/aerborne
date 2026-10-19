# Shared RPG zones and mob loot

Your islands and shared map have different roles. Ordinary compatible mobs on islands may stack; authored encounters on the shared map remain individual enemies. Build your hub, forests, mines and dungeons yourself, then use staff tools to define where each encounter belongs. These commands never build terrain, move existing NPCs or set warps.

Staff need `nerdsmp.zone.admin` for zone setup and `nerdsmp.loot.admin` for loot editing. Both default to operators. Normal players cannot inspect the staff catalogs or edit these settings.

## Place an encounter

1. Get `/zone wand`. Left-click one block for pos1 and right-click the opposite block for pos2. Include the floor, walking space and enough headroom for the intended mobs. `/zone selection` previews the cuboid.
2. Save `/zone create old_crypts`. New zones start disabled. Zones belong in shared worlds, cannot overlap other zones and cannot be placed on private islands.
3. Browse `/zone catalog custom` or `/zone catalog vanilla`. The catalog is paginated and only includes mobs supported by ambient zone spawning. World bosses require their dedicated encounter setup.
4. Stand on a clear, supported landing and use `/zone spawn old_crypts custom:veil_stalker 15`. This saves your position and adds that level-15 mob to the roster in one operation. Nothing spawns yet.
5. Walk to more landing positions and repeat `/zone spawn old_crypts custom:veil_stalker`. Existing roster levels and weights are preserved. For a second type, use `/zone spawn old_crypts vanilla:skeleton 12 3`.
6. Tune `/zone cap old_crypts 8`, `/zone respawn old_crypts 20` and `/zone radius old_crypts 48`.
7. Review `/zone info old_crypts`, then `/zone enable old_crypts`.

Every point draws from the **same zone-wide weighted roster**. A weight of 3 versus 1 means roughly three out of four roster rolls select that mob, not three mobs at that point. The spawn shortcut does not pin a mob to one point. Use separate, non-overlapping zones for distinct encounter populations. `/zone mob old_crypts add custom:veil_stalker 1 20` explicitly changes that roster entry to level 20 and weight 1.

Mobs spawn only near eligible players in already loaded chunks, on clear supported landings. The cap counts living zone mobs; blocked or cancelled attempts do not consume it. Encounters are kept inside their zone, and idle or disabled-zone mobs are cleaned up. No forced chunk loading is used.

Useful edits:

```text
/zone point old_crypts list
/zone point old_crypts list 2
/zone point old_crypts add
/zone point old_crypts remove 3
/zone mob old_crypts list
/zone mob old_crypts remove vanilla:skeleton
/zone title old_crypts The Old Crypts
/zone disable old_crypts
```

Disable a hostile zone before removing its final point or final roster entry. `/zone safe harbor on` makes a peaceful hub; switching an enabled safe zone to hostile requires a complete roster and points. Deleting a zone requires `/zone delete old_crypts confirm`; it removes the region definition, not your builds.

## Author a drop table in-game

Loot profiles are separate from the shipped default tables. A new profile is additive: it adds its rewards without removing normal drops.

```text
/mobloot create crypt_rewards
/mobloot add crypt_rewards bones material bone 45 1 3
/mobloot add crypt_rewards coins marks marks 35 2 6
/mobloot add crypt_rewards rare_blade item crafted_iron_sword 2 1 1 true 2
/mobloot show crypt_rewards
/mobloot assign zone old_crypts crypt_rewards
```

The number after the source is a **percentage**, not a fraction: `2` means a 2% chance. Each entry rolls independently once per actual player-credited mob death. Minimum and maximum are inclusive. The example blade is always unidentified and receives two affixes if its entry succeeds. Use existing item IDs from tab completion; do not type an item display name.

Supported reward types:

| Type | Source | Result |
| --- | --- | --- |
| `material` | A vanilla material, such as `bone` or `iron_ingot` | Ordinary supplies; equipment receives the server's gear presentation |
| `item` | An existing item ID, such as `crafted_iron_sword` | An authored item; supported gear may use `true 2` for unidentified/two affixes |
| `marks` | The literal word `marks` | A pickup-only coin purse; no direct account credit until collected |

Materials and ordinary items cannot request unidentified status or affixes; those options are limited to supported gear item IDs. Purses cannot be collected by hoppers, and their value is paid through the existing idempotent economy flow. Loot profiles do not multiply payouts by mob stack size. There are limits of 32 entries per profile, 64 items per entry, 256 total item rewards and 10,000 total Marks per kill.

Change an existing entry explicitly:

```text
/mobloot set crypt_rewards bones material bone 60 2 4
/mobloot remove crypt_rewards rare_blade
```

`add` refuses an existing entry ID; `set` refuses a missing entry ID. Both validate the complete table before publishing it.

## Replace default loot, or target a mob type

Use replacement mode when you want full control over physical rewards:

```text
/mobloot mode crypt_rewards replace confirm
/mobloot assign zone old_crypts crypt_rewards confirm
```

Replacement removes normal item drops, default bonus gear/purses and the mob's ordinary authored item drops. It leaves XP behavior, quest credit and boss participation awards unchanged. An empty replacement profile intentionally drops no items. Profile rewards require a player-credited kill, so replacement also suppresses ordinary item drops from uncredited environmental or automation kills. Review boss rewards carefully before replacing a boss's table.

You can give a mob type a fallback table wherever it appears:

```text
/mobloot assign mob custom:veil_stalker crypt_rewards confirm
/mobloot assign mob vanilla:zombie crypt_rewards confirm
```

A zone profile has priority over a mob profile for that zone's managed spawns. A naturally wandering mob is not part of the zone's encounter population and uses its mob fallback instead. Only one authored profile rolls; assigning both never doubles the rewards. A custom mob uses its authored ID rather than its underlying vanilla entity type for the mob fallback. Unassigned mobs retain the existing `content/drop-tables.yml` behavior.

Inspect and clean up:

```text
/mobloot list
/mobloot bindings
/mobloot unassign zone old_crypts confirm
/mobloot unassign mob custom:veil_stalker confirm
/mobloot delete crypt_rewards confirm
```

Replacing an existing assignment or assigning a replacement-mode profile requires `confirm`. A profile cannot be deleted while assigned. Unassigning a zone may reveal a mob fallback underneath it; without a fallback, normal drops resume. Zone deletion does not delete its loot assignment: unassign first if you want to reuse that zone ID without its old rewards. Orphaned assignments can still be removed with the unassign command.

## Persistence and deployment

Zones persist in `plugins/NerdSMP/zones.yml`; loot profiles and assignments persist in `plugins/NerdSMP/mob-loot.yml`. Successful commands save atomically before changing the active settings. Failed writes retain the previous settings.

Prefer the commands so validation and permissions are applied. If you edit `mob-loot.yml` externally, back it up first and use `/mobloot reload confirm`. Invalid files are rejected without replacing the active snapshot. The shipped default drop table is unchanged by this editor.

Only settings are installed with the feature update. No live zone, mob spawn, warp, loot assignment or NPC is automatically added to your map.

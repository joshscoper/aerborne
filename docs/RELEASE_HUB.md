# Aerborne — Java-only hub candidate

This is a private release candidate under construction, not a public-release sign-off.
The visual target is a coherent Warcraft-inspired world: strong silhouettes,
districts with a purpose, layered terrain, landmarks and environmental storytelling.
The builds are original Minecraft block architecture, not imported Warcraft assets.
In-game composition, interiors, lighting and navigation still require an art pass;
unit tests and a successful startup cannot establish AAA visual quality.

## Visiting the build

Run `run-skyblock-test-server.cmd`, join authenticated Java Edition at
`127.0.0.1:25566`, and use `/spawn` or `/expedition`. World & Trade → Waypoints
opens the same destination screen. The lectern at Hearthport `(0,97,8)` is the
expedition board. Tab completion lists the routes.

| Route | Purpose and landmark | Arrival |
| --- | --- | --- |
| city | Hearthport: arcane citadel, market quarter, guildhall, workshops, fountain and arrival arch | 0.5, 97, 0.5 |
| quarry | Cloudbreak Quarry: stone, coal, copper and iron; mine entrance and timber gantries | 160.5, 97, 19.5 |
| grove | Windroot Grove: four timber varieties, ancient tree, ruins and forester's lodge | -159.5, 97, 19.5 |
| digsite | Forgotten Terrace: renewable sand, gravel and clay; temple excavation and field camp | 0.5, 97, 179.5 |
| harbor | Stillwater Basin: open-water fishing, docks, boathouse and lighthouse | 0.5, 97, -136.5 |
| cinder | Cinder Shelf: quartz, gold and glowstone; blackstone gate and foundry | 160.5, 97, 179.5 |
| astral | Astral Reach: end stone, amethyst, redstone and diamond; observatory ruins and crystals | -159.5, 97, -140.5 |

Cinder and Astral access requires the campaign's Nether and End unlocks.
These are themed gathering zones, not completed Nether/End dungeons or boss arenas.
Teleport, walking/flying across the boundary and reconnecting inside a sealed zone
are guarded. The four starting gathering islands have railed walking bridges.

## World ownership and protection

- Only Skyblock mode creates `minecraft:nerdsmp_expeditions_<season>`.
  Its manifest is `plugins/NerdSMP/expeditions/<season>-world.yml` and its Paper
  dimension is under `world/dimensions/minecraft/nerdsmp_expeditions_<season>`.
- A name/path/UUID/template mismatch fails startup. Existing dimensions are not
  adopted or rewritten. The old spawn and saved warps remain in `travel.yml`.
  `/spawn` defaults to Hearthport; a staff `/setspawn` must be within Hearthport.
- No NPCs spawn automatically. Existing NPC anchors stay where staff put them.
  City-only merchants now require a Hearthport location, not merely a large radius
  around any dimension's spawn. Use `/npc definitions`, `/npc create <definition>`
  or `/npc movehere <instance>` deliberately; `/nerdadmin releasecheck` lists gaps.
  Suggested market locations are the west/east stalls around `(±20,97,5/18)`;
  faction and service NPCs can occupy the guildhall and workshop courts.
- Public workshops are at `x=-8,-4,0,4,8`, `y=97`, `z=26`: crafting, forge,
  enchanting, alchemy and smithing. No station permission or paid entitlement is granted.
- Normal territory protections cover building, containers, fluids, explosions and
  machinery. Only 196 cataloged resource coordinates permit public harvesting,
  in Survival with a loaded account and the relevant campaign unlock.
- Nodes replenish after 45 seconds (quarry/digsite), 75 (timber), 120 (Cinder) or
  240 (Astral), plus up to seven seconds for the bounded scan. Used nodes have
  distinct spent block states. Cooldowns live in chunk PDC, survive clean restarts
  and are checked without force-loading chunks. Unrecognized replacements are
  preserved for staff review. Corrupt marker types fail closed.
- Only verified renewal clears provenance at that exact block. Placed ores/dirt
  cannot earn repeat XP. The existing skill, excavation, job and collection paths
  receive the ordinary accepted break events.
- Random block ticks, natural mob spawning, weather changes, PvP and mob griefing
  are disabled in this public dimension only. Foliage is architectural, not a tree
  farm. Private islands retain their own normal growth and spawn settings.
- Public-world void falls return players to the city. This does not make other
  damage harmless or solve the existing grave durability/capacity release gate.

Do not change layout version 1 in place once chunks exist. A revised map needs an
explicit migration/build-edit plan and a verified backup, not silent regeneration.

## Java-only deployment

Geyser/Floodgate runtime identity handling, `/linkaccount`, Bedrock nameplate rules,
Bedrock manifest identifiers and the UDP port exposure are removed. Nexo remains
the sole Java pack owner. The obsolete Geyser template was removed after copying
it to `runtime/review-backups/java-release-hub-20260929-213706-1af177da/retired-geyser-config.yml.template`.
Old identity and moderation records remain readable for support recovery.

UUID format checks are not authentication. Keep standalone Paper in online mode,
or use online-mode Velocity with modern forwarding and an inaccessible backend.
Do not install cross-play plugins on this deployment. No firewall, public DNS,
store or production server has been changed by this pass.

## Persistence changes

Job allowances, fractional bonus carry, bounded daily activity receipts and a
pending payment record now live in `player_data/<uuid>.yml` under `jobs.<season>`.
The allowance and payment decision persist before an idempotent ledger credit.
Reconnect/next activity retries an incomplete credit; duplicate acknowledgment
does not pay twice. A UTC day change first settles any old payment, then starts a
fresh allowance. Earlier builds never saved job limits, so unavailable historical
daily counts cannot be reconstructed. Monitor tick-bound YAML I/O under real load.

`campaign-state.yml` now freezes start/end dates and stores campaign state under
`seasons.<season>.campaigns`. `server-progression.yml` similarly scopes unlocks.
Both use schema 2. Legacy state imports exactly once into the current season and
is retained at its old path as a historical snapshot. Failed saves do not publish
new contribution totals, reward claims or unlocks. Legacy files without dates
receive their first fixed schedule on upgrade. Expired campaigns do not restart
automatically; campaign rotation/content still needs a complete seasonal design.

Campaign inventory handoffs, banks, starter delivery and graves still have separate
crash-recovery work. These state fixes are not a claim that every item transaction
is safe yet. Use [release hardening and remaining gates](HEARTHSKY_RELEASE.md) for the remaining public launch gates. The historical filename is retained for existing links.

## Verification and review

September 29, 2026: Java 25 `qualityGate build` passed 498 tests, zero failures,
errors or skips. The staged plugin SHA-256 is
`89A033BB7491E3C0285992979F0971B05556FD50DE44BBCDB1A0849366AD7E62`.

Paper 26.2 build 121 and Nexo enabled on authenticated localhost-only port 25566.
Console diagnostics reported zero trade/market/vendor recovery holds and the
expected manual NPC placement list. Live block probes verified the city landing,
lectern, citadel spire and one resource/water position in each of the six zones.
The new world generated and saved 1,804 block chunks; old dimensions saved no new
block chunks during this smoke test. All temporary force-load tickets were removed.
The NPC anchor file remained byte-identical and no player profiles were created.
Logs are preserved in the backup folder as `release-hub-smoke.log`.

A second clean startup retained byte-identical campaign dates and the world
manifest/UUID. Its force-load query reported no remaining tickets. Both runs
saved all dimensions and exited 0; Paper is left stopped. Restart evidence is
preserved as `release-hub-restart.log` in the same backup folder.

There were no NerdSMP initialization, command, world-generation or shutdown errors.
The pre-existing Windows OSHI/Perflib performance-counter warning remains.
This was server-side smoke testing, not a real-player visual, harvest/recovery,
proxy-authentication, load, forced-crash or offsite-restore acceptance test.

The pre-update isolated runtime backup contains 743 SHA-256-verified files:
`runtime/review-backups/java-release-hub-20260929-213706-1af177da/snapshot`.
Its `manifest.csv` records every copied file. No world/player reset was performed.

Before public launch, walk every district with ordinary movement, test every
station/NPC and route with two authenticated Java clients, test resource depletion
across save/unload/restart, verify XP/collection delivery, audit every resource path,
and review the skyline from spawn, bridges and ground level. Capture day/night
screenshots with the actual Nexo pack. Encounters, dungeons, sound design and visual
sign-off remain unfinished; compare against the stated world-design target rather
than treating successful generation as finished art.

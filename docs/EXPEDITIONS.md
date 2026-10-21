# Expeditions

Expeditions are short party adventures, like dungeons in an MMO. One to five players get a private
copy of the map. They clear a chain of rooms, beat a boss and leave with loot. They are also the
only way into the Nether and the End. Vanilla portals are sealed. Nether and End expeditions run
in real Nether and End dimensions.

Code: `nerdsmp-paper/.../expedition/` (service, runs, setup, menu, worlds), with the pure rules in
`expedition/content/` (file format, validation, tiers, modifiers, scripts) and `expedition/flow/`
(run state machine, matchmaker, ready checks, records). Sample files ship in
`src/main/resources/expeditions/`. The season campaign that can seal or reward expeditions is in
[CAMPAIGN.md](CAMPAIGN.md).

## For players

| Command | What it does |
|---|---|
| `/expedition` | Board of open expeditions; pick one, then a difficulty. Left-click starts, right-click queues. |
| `/expedition start <id> [tier]` | Party leader (or solo): start now with a ready check. |
| `/expedition queue <id> [tier]` | Join the finder, alone or with your whole party. |
| `/expedition ready` / `decline` | Answer a ready check (also clickable in chat). |
| `/expedition allies` | After a long wait in the finder: start now with hired NPC allies in the empty slots. |
| `/expedition leave` | Leave the queue, a ready check, or the run you're in. |
| `/expedition list` / `info <id>` / `status` | Open expeditions, one expedition's tiers and records, your queue or run. |
| `/expedition guide <id>` | The expedition's guide book: route, boss notes, every mob with its possible drops, and rewards. |

**Parties.** The existing `/party` system is used as is: its maximum of five is the expedition cap.
Only the leader can start or queue. Online members get a 30-second ready check, and offline members
stay behind. Solo players are a party of one.

**The finder.** Queued tickets (a solo player or a whole party) are packed into groups for the same
expedition and tier, oldest first. A full group starts at once. A group that meets the expedition's
minimum starts once its oldest ticket has waited `queue-fill-seconds`. Starts pause while every
instance is busy, and the queue keeps its order. If anyone declines or times out, the other tickets
go back to their old places.

**Party-size scaling.** Expeditions are authored for a full party, and a smaller group meets fewer,
softer enemies. With a fraction *f* of the full party present, each wave's mob counts scale by
`count-floor + (1 − count-floor) × f` (never below one of each authored mob). Enemy health, the
boss included, scales the same way with `health-floor`. With the defaults, a solo player in a
five-player expedition meets 52% of the mobs at 60% health. Hired allies count as `ally-weight` of a
player each. Scaling is recalculated whenever a wave or boss spawns, so it follows players leaving
or allies falling. Script `spawn` counts are used as written, but their health scales.

**Hired allies.** Once a ticket has waited `allies.offer-after-seconds` in the finder, whoever
queued it gets a clickable offer: **[Start with allies]**, or `/expedition allies`. It fills the
empty slots, up to `allies.max`, with labelled NPCs. A party still gets its ready check. Allies:
- **Roles**, filled in order: **Bulwark** (iron golem, tank), **Blade** (vindicator with an axe),
  **Hedge-witch** (throws harming potions at enemies and heals the most-hurt nearby player by two
  hearts every 3 s) and **Archer** (skeleton with a bow).
- Pick the nearest encounter enemy within 24 blocks, otherwise follow the party, and catch up by
  teleport if they fall more than 32 blocks behind. They never target or hurt players or each other.
  Their potions don't touch the party.
- Health is the role's base times the tier's health multiplier. Damage against enemies is
  `allies.damage-multiplier` times the tier's health multiplier.
- Don't spend party lives, aren't counted for wipes, and rejoin at the checkpoint 15 s after
  falling. They regroup at the checkpoint after a wipe and are removed when the run closes.
- Take no loot, and drop nothing when they die.

A run with any hired ally is **assisted**. It still pays loot and Marks, but it records no clears
(no tier unlocks, server totals, best times or best parties). It also gives no season-campaign
progress: no expedition credit, no boss credit, and no kill credit for anything killed inside it.
Players are told when they accept the offer, at the start and at the finish.

**In a run.** A boss bar shows the expedition, current room, lives and time left.
- Walking (or teleporting) into the next room starts it. Rooms must be done in order.
- Room types: **waves** (clear each wave, then the next is released), **survive** (waves keep
  coming until the timer ends) and **boss** (adds are released at health thresholds).
- Clearing a room opens its gate and can move the party's checkpoint forward.
- **Deaths never drop gear** (keep inventory, no grave) but spend one shared party life. If everyone
  is down at once, it's a wipe: the room resets and the party regroups at the last checkpoint. At
  zero lives, or when the time limit runs out, the run fails and everyone is returned.
- A disconnected player has `rejoin-minutes` to come back and is put straight back in. If the run
  ended meanwhile (or the server restarted), they are returned to where they entered from on join.
- After the boss: each member gets their own loot (profile rolls per tier, fixed items, Marks).
  Overflow waits until they leave. The exit lights up. Walk into it, or `/expedition leave`. The
  instance closes `linger-after-finish-seconds` later, or as soon as everyone has left.
- Players return to exactly where they entered from.

**Difficulty and guides.** Every card shows a 1–5 star rating for Normal: Easy, Moderate,
Challenging, Hard or Brutal. Staff set it with `difficulty:`; without one it's estimated from the
level. The detail view has a **Read the guide** book, also opened by `/expedition guide <id>`. It
holds the staff-written overview, tips, a line per room and boss notes. It then has a page for
every mob the expedition can field, built automatically from its waves, bosses and script spawns,
with base health, damage and ability, and possible drops. Those drops come from the same tables
the death handler rolls: relics, loot profiles, the supplemental drop tables, sealed gear and coin
purses. The guide ends with the rewards. The website shows the same guide at
`/expeditions/<id>`.

**Records.** Each member's clears are kept per tier in `expedition-records.yml`. Heroic needs a
Normal clear of that expedition, and Mythic a Heroic clear. Server totals, best times and best
parties show in the menu and on the website.

## Sample expeditions

| Id | Where | Party | Rooms |
|---|---|---|---|
| `hollow_barrow` | Overworld crypt, level 8 | 1–5 | Crypt Gallery (2 waves) → Ossuary (survive 45 s) → King's Rest (The Hollow Huntsman, adds at 66%/33%) |
| `cinder_keep` | Nether (basalt deltas), level 20 | 2–5 | Gatehouse (2 waves) → Blaze Forge (survive 60 s) → Throne of Embers (Keeper of Embers, adds at 75%/40%) |

Ratings: Hollow Barrow 2 (Moderate), Cinder Keep 3 (Challenging). Both ship guides. Their bosses
are tuned down from the authored mobs: the Hollow Huntsman to 60% damage and 85% health, and the
Keeper of Embers to 70% damage. An unarmoured player now survives a Huntsman hit, measured at 9–11
damage instead of a one-shot.

A server that already installed an earlier sample file gets the current one on the next start, but
only when the installed file is byte-for-byte an earlier shipped version (line endings ignored).
Any staff edit keeps the file as it is.

Their templates are built from code (`SampleTemplates`), so the repository ships no world files.
The first time a sample is edited or run, the build is generated from its plan. Edits staff make in
the template carry into every later run. Cinder Keep is sealed by the sample season until the
**Ember Gate** milestone.

## Building an expedition (staff)

Permission: `nerdsmp.expedition.admin` (operators by default; included in the builder and admin
bundles). `/expedition guide` prints this walkthrough in game.

1. `/expedition create <id> [overworld|nether|end]` makes `expeditions/<id>.yml` and a void
   template world (`nerdsmp_exp_tpl_<id>`) of that dimension with a small platform. It also
   teleports you there and hands you the **Expedition Wand**. To use a world you already built,
   run `/expedition create <id> from <world>` (not one of the three main worlds).
2. Build the dungeon. While you stand in a template world, every setup command applies to that
   expedition. `/expedition edit <id>` takes you back later.
3. Stand at the start and run `/expedition set spawn`.
4. Wand left-click and right-click opposite corners of a room, then `/expedition room add <name>`.
   Rooms run in the order you add them. Running it again with the same name resizes the room.
5. Shift-right-click the floor with the wand to drop mob points in the room you click.
   `/expedition point <room> add|list|remove <n>|clear` does the same by command.
6. Give the room an encounter:
   - `/expedition wave <room> <mob> [count] [wave#]` adds mobs to a wave. Naming the next wave
     number starts a new wave.
   - `/expedition survive <room> <seconds>` makes its waves repeat until the timer ends.
   - `/expedition boss <room> <mob>` makes the room a boss fight. The boss rises at the first point,
     and waves become adds.
   Mob ids: `custom:<id>` (authored mobs, including bosses) or `vanilla:<type>`.
7. Optional: build a door, select it with the wand and run `/expedition gate <room>`. Those blocks
   vanish when the room is cleared. `/expedition checkpoint <room>` sets where the party regroups
   after clearing it.
8. Stand at the finish and run `/expedition set exit`. Then use `/expedition loot <profile>`,
   `/expedition loot marks <n>`, `/expedition loot item <item> <amount>`, and
   `/expedition set title|description|level|party <min> <max>|time|icon|modifiers`.
9. `/expedition check` lists what's done (✔), errors (✗, block publishing) and advice (!), each
   with a clickable fix. Every setup command also names the next missing step.
10. `/expedition test [tier]` runs it solo, bypassing the campaign seal. A test run grants no Marks,
    records or campaign credit. Then run `/expedition publish`.

Holding the wand in a template draws every room outline, gate, mob point, checkpoint, the spawn
and the exit with particles, plus your current selection.

Other staff commands: `/expedition reload` (config tiers and every file), `/expedition runs`
(live runs and instance use), `/expedition stop <n>`, `/expedition unpublish`.

## File reference

`plugins/NerdSMP/expeditions/<id>.yml`. Everything except rooms, spawn and exit has a default. Setup
commands edit this file in place and keep your comments. Edits by hand take effect with
`/expedition reload`. A file that no longer parses keeps its last good version live and reports
the line to fix.

```yaml
title: Cinder Keep
description: One line for the menu and website.
icon: blackstone                 # menu item
dimension: nether                # overworld | nether | end
biome: basalt_deltas             # optional; fills chunks the template never generated
template-world: nerdsmp_exp_tpl_cinder_keep   # default; or the world given to "create ... from"
sample: cinder_keep              # only for the shipped code-built samples
published: true
level: 20                        # base mob level; tiers add their bonus
party: {min: 2, max: 5}          # 1-5
time-limit-minutes: 30           # 5-180
lives: 10                        # optional; sets Normal's lives, other tiers keep their ratio
spawn: [0, 64, 4, 0]             # [x, y, z] or [x, y, z, yaw]
exit: [0, 64, 83]
modifiers: []                    # always on for this expedition
rooms:
  gatehouse:                     # rooms run top to bottom
    title: The Gatehouse
    region: [-7, 64, 12, 7, 70, 30]       # [x1, y1, z1, x2, y2, z2], inclusive
    checkpoint: [0, 64, 28, 0]            # optional respawn after clearing
    gate: [-1, 64, 31, 1, 66, 31]         # optional; blocks removed on clear (max 2048)
    points: [[-4, 64, 17], [4, 64, 17]]   # mob spawn points, inside the region
    encounter:
      type: waves                # waves | survive | boss (inferred when omitted)
      waves:
        - {"vanilla:wither_skeleton": 3}
        - {mobs: {"custom:oathbreaker": 1}, delay-seconds: 4}
      boss: custom:keeper_of_embers       # boss rooms
      boss-level-bonus: 4
      adds-at-health: [75, 40]            # boss rooms: release the next wave at these health %
      survive-seconds: 60                 # survive rooms
      boss-damage: 0.7                    # boss rooms: scale this boss's damage (melee, projectiles, shockwave)
      boss-health: 1.0                    # boss rooms: scale this boss's health (both 0.1-5, default 1)
      scripts:                            # optional, see below
        start: ["message <gold>The gate groans open.</gold>"]
loot:
  profile: cinder_keep_rewards   # a /mobloot profile, rolled once per tier loot roll
  marks: 250                     # times the tier's Marks multiplier
  items: {blaze_rod: 4}          # given once; NerdSMP item ids or vanilla materials
tiers:                           # optional per-expedition overrides
  mythic: {enabled: false}
  heroic: {lives: 5, loot-rolls: 3, modifiers: [volatile]}
difficulty: 3                    # 1-5 stars for Normal; estimated from the level when unset
guide:                           # shown in /expedition guide <id> and on the website
  overview: One or two sentences on what to expect.
  tips: [Bring fire resistance., Wither skeletons inflict Wither.]
  rooms: {gatehouse: Wither skeletons first, then an Oathbreaker.}
  boss: How the boss fight goes and what to watch for.
```

`/expedition check` warns (but doesn't block publishing) when an expedition has no rating or no
guide, and when the guide has a tip for a room that doesn't exist.

Quote mob ids inside `{}` maps (`"vanilla:zombie": 3`). The key is `scripts`, not `on`, because
YAML reads a bare `on` as true.

### Scripts

Optional actions run on encounter events. Triggers: `start`, `clear`, `wave-N` (when wave N is
released), `health-N` (boss at or below N%, once) and `timer-N` (N seconds after the room starts).

| Action | Example |
|---|---|
| `message <MiniMessage>` | `message <red>Something stirs.</red>` |
| `title <title>[|<subtitle>]` | `title <gold>Hold the line|<gray>60 seconds</gray>` |
| `sound <key> [pitch]` | `sound entity.ravager.roar 0.7` |
| `spawn <mob> [count] [point#]` | `spawn vanilla:blaze 2` (counts towards clearing the room) |
| `open <room>` / `close <room>` | Remove or restore a room's gate blocks |
| `effect <potion> <seconds> [amp]` | `effect fire_resistance 20` (whole party) |
| `checkpoint` | Move the respawn point to this room now |
| `wait <seconds>` | Delay the actions after it |
| `command <console command>` | `{player}` runs it once per member; `{run}` is the run id |

Scripts are parsed on load, so `/expedition check` reports unknown actions, rooms, mobs, effects
or sounds before anyone plays.

### Tiers and modifiers

Global tiers are in `config.yml` under `expeditions.tiers` (defaults below). Each sets a level
bonus, health and damage multipliers, lives, loot rolls, Marks multiplier, an optional `requires`
tier, and modifiers.

| Tier | Level | Health | Damage | Lives | Loot rolls | Requires | Modifiers |
|---|---|---|---|---|---|---|---|
| normal | +0 | 1.0× | 1.0× | 10 | 1 | — | — |
| heroic | +8 | 1.6× | 1.3× | 6 | 2 | normal | — |
| mythic | +16 | 2.4× | 1.6× | 3 | 3 | heroic | fortified, timed |

| Modifier | Effect |
|---|---|
| `fortified` | Regular enemies +30% health, +15% damage |
| `tyrannical` | Bosses +30% health, +15% damage |
| `bolstering` | A dying enemy makes nearby enemies 15% tougher and 10% stronger |
| `volatile` | Enemies burst 1.5 s after death (4 damage within 3 blocks) |
| `timed` | Clear within 60% of the time limit for an extra loot roll |

## Configuration

`config.yml → expeditions`: `max-instances` (6), `ready-check-seconds` (30), `queue-fill-seconds`
(90), `rejoin-minutes` (5), `linger-after-finish-seconds` (180), `vanilla-portals` (false) and
`tiers`, plus:
- `scaling`: `count-floor` (0.4), `health-floor` (0.5) and `ally-weight` (0.6).
- `allies`: `enabled` (true), `offer-after-seconds` (120), `max` (4), `damage-multiplier` (1.5) and
  `roles` (bulwark, blade, hedge-witch, archer).

The setting `generated-worlds` only concerns the old open-world expedition prototypes.

With `vanilla-portals: false`, Nether and End portal travel is cancelled for players and entities,
and new Nether portals won't light outside expedition worlds.

## How instances work

- Each run copies only the template's `region`, `entities` and `poi` folders into
  `dimensions/minecraft/nerdsmp_exp_run_<id>_<n>`, off the main thread. It then loads that copy
  with the template's generator and dimension. Copying only those folders keeps the copy from
  inheriting the template's identity or level data. A template that is loaded for editing is
  saved first.
- Run worlds never autosave. They have mob spawning, weather, time and fire spread off, PVP off,
  keep-inventory on and difficulty Normal. Hard would multiply every mob's melee and ability damage
by 1.5; tiers and boss multipliers set the difficulty instead. Only the party can teleport in, and staff with the admin
  permission can watch. Blocks can't be broken or placed except by staff.
- The room footprint is kept loaded with plugin chunk tickets while the run lasts.
- On close, players are sent home, the world is unloaded and its folder deleted. Leftover run
  folders from a crash are deleted on the next start, and `expedition-returns.yml` sends affected
  players home when they rejoin.
- Encounter mobs are spawned with `CustomMobManager.spawnEncounter` at the tier level. Tier and
  modifier health is applied to max health, and damage scales on hit (including projectiles).
  Bosses get their own boss bar.

## Verification

`./gradlew :nerdsmp-paper:build` runs the unit tests:
- `ExpeditionContentTest`: shipped files, loader errors, validator, scripts, tiers and modifiers.
- `RunProgressTest`: room order, waves, survive, boss adds, lives, wipes, time limit, the timed bonus.
- `MatchmakerTest`: finder packing, fill wait, instance cap, requeue, ready checks, records.
- `SampleTemplatesTest`: every spawn, point, checkpoint and exit in the sample files has a floor
  and headroom in the build, gates are built, and the route is open.
- `PartyScalingAndAlliesTest`: scaling factors and counts, ally weight, the offer timing, slots,
  role order, config loading, and ready checks carrying allies.

It was also driven end to end on a local Paper 26.2 server with five bot players (mineflayer
through ViaBackwards, offline mode, console kills in place of combat):
- A party of five ran the Hollow Barrow from the ready check through the gate, a death and
  checkpoint respawn, the survive room, the boss and its health-50 script, loot and the exit.
- Two solo queuers were matched by the finder into Cinder Keep, which ran in the Nether. A wipe
  reset the room, and the run completed.
- A staff bot built a new End expedition with the wand and setup commands, then checked, published
  and test-ran it.
- A solo player queued, was offered four allies after the wait, and accepted. With no console
  kills, the allies cleared the gallery and defeated the Hollow Huntsman on their own. Loot was
  paid, while the campaign and the public clear counts stayed at zero.

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
| `/expedition key [start]` | Show your Mythic keystone, or start it with your party. |
| `/expedition forge` | The Expedition Forge: trade an expedition's materials for pieces of its set. |
| `/expedition rewards` / `bonus` | Open your reward chest, or the bonus chest (for Marks), after a win. |
| `/expedition leave` | Leave the queue, a ready check, or the run you're in. |
| `/expedition list` / `info <id>` / `status` | Open expeditions, one expedition's tiers and records, your queue or run. |
| `/expedition guide <id>` | The expedition's guide book: route, boss notes, every mob with its possible drops, and rewards. |

**Parties.** The [party system](PARTIES.md) holds up to eight; an expedition takes as many as its
`party.max` (five at most, eight for a raid).
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

**In a run.** The sidebar becomes the run panel: tier and modifiers, the clock, party lives, every
objective (✔ done, ▶ in progress, ○ open, ▪ locked; puzzles show their progress under the active
room), and each member's health. There is one boss bar, and only for the boss (a council shares
one bar) or, when no boss is up, a living mini-boss.
- Walking (or teleporting) into a room starts it when the rooms it requires are cleared. By default
  that's the room before it, so most maps run in order; a hub map can open several wings at once.
  One room runs at a time.
- Room types: **waves** (clear each wave, then the next is released), **clear** (every wave at once;
  kill them all), **survive** (waves keep coming until the timer ends), **boss** (adds at health
  thresholds), **council** (two to four bosses at once) and **puzzle** (no fight). Any room can also
  have puzzles that must be solved before it clears.
- **Puzzles**: levers to pull, buttons to press in order (some flash the order first; a mistake
  resets it), pressure plates to hold down at once, targets to hit within a time, and jump courses.
  Falling off a course puts you back at its start without spending a life.
- **Campfires**: right-click to light one between fights. Out of combat, it heals two health a
  second, tops up food and puts out fire, and the party then respawns there.
- **Elites, mini-bosses and bosses** use the ability kit below. Every ability is telegraphed: a red
  ring, cone or line, a marker under a player, or a chain, plus a sound, a moment before it lands.
  Bosses can change abilities in phases and **enrage** if a fight runs too long.
- **Arena events**: dialogue from characters and enemies, short cutscenes (the camera is taken for a
  few seconds), falling rocks and other hazards, and parts of the map that rise, crumble or change.
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

**Mythic keystones.** A member's first Mythic clear of any campaign expedition earns a personal
keystone: a random open expedition with a Mythic tier (not the tutorial or a raid; staff-made
expeditions with a Mythic tier count) at +2. `/expedition key` shows it, this week's affixes and the
expedition's best key; `/expedition key start` (or left-clicking the board's keystone card) starts
it for the holder's party. The holder must lead the party; there is no queue and no hired allies,
and other members need only be allowed into the expedition itself.

- **Scaling.** The run uses the expedition's Mythic tier with enemy health and damage ×1.08 per
  level above +2 (compounding), two enemy levels per key level, an extra loot roll at +5 and +10,
  and 10% more Marks per level.
- **Affixes** change every Monday (UTC). From +4, Fortified or Tyrannical alternate weekly; from +7
  a second affix rotates through Bolstering, Raging, Sanguine and Volatile; from +10 both Fortified
  and Tyrannical apply. The Timed modifier is replaced by the key's own timer.
- **The clock.** The timer is 60% of the time limit (plus any Timed-goal room bonuses). A key run
  has no lives: each death adds 10 seconds. The sidebar shows the key's time left (or how far over
  it is) and the deaths.
- **Results.** Within 60% of the timer the key upgrades +3, within 80% +2, within the timer +1, and
  it moves to another expedition. Over time it still pays out but drops one level and stays. A
  failure, everyone leaving or staff stopping the run drops it one level too. Keys stay between +2
  and +30.
- **Records.** Timed keys are kept per player and per expedition in `expedition-keystones.yml`
  (higher keys win; at the same level the faster clock wins). Key runs don't count toward the
  regular tier records but do credit the campaign as Mythic clears. Members with no key earn one
  from a key clear too.
- **Staff.** `/expedition key give <player> <expedition> <level>` sets a player's key.

## Sample expeditions

| Id | Where | Party | Rooms |
|---|---|---|---|
| `first_flight` | The Wardens' Road at sunset, level 2 (tutorial) | 1–5 | The Landing → The Trial Path (cross the islets, Cloudlings) → Trial's End (campfire) → The Guardian Yard (3 waves, a Stonebound Keeper) → The Rune Gate (3 levers) → The Final Test (The Old Sentinel: slam, gusts below 66%, Cinderlings at 50%, frost below 33%) |
| `hollow_barrow` | Overworld crypt, level 8, rooms | 1–5 | Crypt Gallery (2 waves, Veil Stalker elites) → Ossuary (survive 45 s under bone rain) → Lever Vault (Barrow Warden mini-boss + four levers in the candles' order) → antechamber brazier → King's Rest (The Hollow Huntsman: cleave, summon; below 50% slam, tether, falling masonry) |
| `thornwood_canopy` | Treetops at night, level 12, forces (80) | 1–5 | Rootgate → Low Boughs → the Fork (hub) → east: Signal Boughs (four boards lower the span), then optional Verrin's Perch (boss in the wind) → west: Silk Walk, then the Briar Matron's Nest (boss; breaks open a catwalk) → the climb → Heart Gate → Thornheart Grove (The Thornheart Shaman) |
| `sunken_aqueduct` | Flooded waterworks, level 16, timed (10 min, +2 min per room) | 1–5 | Sluice Hall (waves + three valves in the shown order, which drain the channel) → Cistern (survive 50 s, geysers) → Floodgate Control (clear + two counterweight plates) → Pumphouse Stove → Tidewarden's Basin (The Tidewarden: tether, frost nova; below 50% meteor, summon, geysers) |
| `gloomforge_mines` | Deep mines, level 20, rooms | 1–5 | Ore Galleries (waves + three alarm targets in 25 s) → Grell's Shaft (Overseer Grell) → Blasting Hall (the Blastmaster + two ore-cart plates, blasting charges) → Slag Pits (the Slag Hound, rockfall) → Miners' Rest → Foreman's Delve (The Deep Foreman: slam, summon; below 50% meteor, charge, collapsing pillars) |
| `hall_of_wardens` | Sky citadel at dusk, level 24, bosses | 1–5 | From the concourse, three wings in any order: Ember Antechamber (four brazier targets) → Ember Throne; Frost Antechamber (four chimes in the shown order) → Frost Hall; Stone Antechamber (three plates) → Stone Court |
| `cinder_keep` | **Raid**: Nether (basalt deltas), level 26 | 2–8 | Scorched Descent (clear) → Gorrak's Kiln (Gorrak the Kilnbreaker; opens the Cinder Shelf) → Slagfalls (3 waves, falling slag) → Hound Pens (Skarn and Vesh, linked) → Rune Galleries (clear + three rune valves) → Slag Chapel (Vael, the Slag Priest) → Great Bellows (survive 75 s, vents) → Steward's Hall (Steward Ashkar and his guard; raises the causeway) → Molten Heart (Pyrrhax, the Ashen Sovereign) |

**The campaign path.** Each campaign expedition opens once you've cleared the one before it
(`requires:`): the Hollow Barrow, then Thornwood Canopy, the Sunken Aqueduct, Gloomforge Mines and
the Hall of the Three Wardens. In a party, every member must have cleared the earlier one. Staff and
`nerdsmp.progression.bypass` skip the check, and the board shows "Clear … first" on locked cards.

Ratings: First Flight 1, Hollow Barrow 2, Thornwood Canopy 3, Sunken Aqueduct 3, Gloomforge Mines 4,
Hall of the Three Wardens 4, Cinder Keep 5. All ship guides. The Hollow Huntsman is tuned down from
the authored mob to 60% damage and 85% health. An unarmoured player now survives a Huntsman hit, measured at 9–11
damage instead of a one-shot.

A server that already installed an earlier sample file gets the current one on the next start, but
only when the installed file is byte-for-byte an earlier shipped version (line endings ignored).
Any staff edit keeps the file as it is.

**First Flight** is the tutorial. Players who haven't cleared it see it first on the board, marked
**Start here**. Captain Wren at the pier explains the basics when right-clicked, and talks the party
through each room. It has only the Normal tier.

Their templates are built from code (`SampleTemplates`, `FirstFlightTemplate`), so the repository
ships no world files. The first time a sample is edited or run, the build is generated from its
plan, and the template is stamped with the plan's fingerprint (`nerdsmp-plan.txt`). When an update
changes the plan, the next edit or run rebuilds the template from the new one. To build on a sample
by hand, delete its `sample:` line first: templates without it are never rebuilt. Cinder Keep is sealed by the sample season until the
**Ember Gate** milestone, and also needs a clear of the Hall of the Three Wardens.

## Loot

Every expedition has its own loot, listed in `content/expedition-loot.yml` (shipped in the plugin; an
entry in the data folder's copy replaces the shipped one): three **materials** from common to rare,
a five-piece **signature set** (four armour pieces and a weapon, with 2, 3 and 5-piece bonuses), and
rare boss **signatures**. The items live in `content/relics.yml` (schema 13 adds them to existing
servers without touching staff edits). The Cinder Keep raid's signatures are mythic, with signature
affixes.

| Source | What it gives |
|---|---|
| Regular enemy | 25% common material (1–2), rare chance of a sealed set piece or a bottle |
| Elite | Common material (1–3), 25% uncommon, 7% set piece, 5% bottle |
| Mini-boss | Common (2–4), 60% uncommon, 15% rare, 25% set piece (2 affixes), 10% Greater bottle |
| Boss chest (each player's own) | Uncommon (1–3), 35% rare, 50% set piece, 35% one of that boss's items, 30% Greater bottle, 4% signature |
| Crate | Common (2–4), 25% uncommon, 5% set piece, 20% bottle |
| Chest | Common (3–5), 50% uncommon, 8% rare, 15% set piece, bottles |
| Hidden cache | Uncommon (1–2), 25% rare, 35% set piece, 25% Greater bottle, 1.5% signature |

A boss's chest stands where it fell, and each player opens their own share once. `bosses:` in
`content/expedition-loot.yml` lists what each boss room can give, likeliest first: with three items,
50%, 33% and 17% of the time. `finds:` lists gear for the slots a set doesn't cover. It drops wherever
set pieces do, each piece as likely as a set piece, but can't be forged.

`companion-drops:` puts a rare companion's charm in a boss's chest, on each player's own roll as they
open it. Keep these out of `guide:`: the website doesn't list them, and a drop companion stays out of the
wardrobe and `/companion list` until a player owns it.

Rarer gear can carry abilities: **on hit** (a chance, with a cooldown), **right-click** (a cooldown),
or **equipped** (for as long as it's worn or held). `content/item-abilities.yml` binds them to items
and adds the gear that needs no entry in `relics.yml`, so an ability can change without touching a
server's edited catalog. Abilities belong to rare or better items in the slots they suit: nothing that
already uses its right-click gets a right-click ability. They only touch hostile mobs, never players.

Heroic multiplies the odds of the better lines by 1.35, Mythic and keys by 1.7 (keys add 0.05 per
level), and raids by a further 1.1. Expedition enemies still drop coin purses and their relic, but no
vanilla loot, no worn gear and none of the overworld drop tables. Set pieces come out sealed half the
time; players identify them as usual.

**Crates and chests.** `chests:` places loot containers: `{at: [x, y, z], kind: crate|chest|secret,
title: ...}`. The template needs a barrel (crate) or chest there. Every chest is private: each player
loots it once, with their own roll, in a window whose leftovers go to their inventory when closed,
and sees a glimmer over it until they have. Hidden caches only glimmer from close by. Other barrels
in a map are scenery and do nothing. Secrets are announced to the party and count toward the score.

**Rewards grow with the player and the difficulty.** Set gear from a run gains item levels over its
catalog level: 6 on Heroic, 12 on Mythic and 2 more per key level, plus 1 for every two adventurer
levels the player has above the expedition's level (at most 15), up to 40 in all. Each item level
adds 1% to the piece's base stats and shows in its tooltip. Stacks of items grow the same way, by the
tier's luck and 1% per level above the expedition (at most half again); single items stay single.

**Skill XP and wear.** Everyone present gets Hunting XP for each enemy that falls (10, 30, 90 or 300
for normal, elite, miniboss and boss, plus 10% per expedition level, times the tier's luck), and 600
on the same scale for finishing. Gear doesn't lose durability inside a run.

**Rare companions.** `companion-drops: {<room>: {companion: <slug>, chance: 0.005}}` gives each
player present their own roll when that room is cleared, multiplied by the tier's luck. A win is a
charm that can be traded until someone right-clicks it to bind the companion to their account. Test
runs don't roll.

**Reward chests and the score.** When a run is won, it's scored out of 300: speed (full within half
the time limit, 40 at the limit), survival (25 lost per death per player) and exploration (40, plus
the share of secrets found and optional rooms cleared; full on maps with neither). The total grades
the run D (under 140), C, B (180), A (220), S (255) or S+ (285). Each player gets a free **reward
chest** with 2 rolls at D up to 7 at S+, better odds at better grades, plus the expedition's fixed
`loot:` items and profile rolls. A **bonus chest** costs `(60 + 12 × level) × the tier's Marks
multiplier` Marks, holds two more rolls and always a set piece with three affixes. With
`reward-chests: {free: [x, y, z], bonus: [x, y, z]}` they appear as a chest and an ender chest at
those (empty) spots; `/expedition rewards` and `/expedition bonus` open them anywhere in the run. An
unopened free chest goes home with its player. Marks are credited at the finish as before. A raid's
weekly finish lockout withholds both chests.

**The Expedition Forge** (`/expedition forge`, or the board's anvil button) trades materials and
Marks for set pieces, once the player has cleared that expedition: 16 common and 4 uncommon
materials for most pieces; 24 common, 6 uncommon and a rare for the chest piece and the weapon; and
`(40 + 6 × level)` Marks (half again for the big pieces). Forged pieces are sealed with two affixes.

The guide book's **Expedition loot** page and each mob's drops show the same odds, and the web API's
`rewards` adds `materials`, `gear` and `signatures` (item names).

## Raids

A raid is an expedition file with `raid: true`: parties of up to eight, no hired allies, and loot
paid per boss. **Cinder Keep** is the first: nine volcanic caverns under the keep with five boss
fights, ending at Pyrrhax, the Ashen Sovereign, in the Molten Heart.

- **Tiers.** Normal is tuned for pickup groups (15 lives). Heroic (10 lives, ×1.9 health, ×1.4
  damage) and Mythic (6 lives, ×3.0 health, ×1.8 damage, Fortified and Tyrannical) are for organised
  raids. They're set with `tiers:` overrides, which now accept `health-multiplier` and
  `damage-multiplier`. Keystones never roll raids.
- **Boss loot and weekly lockouts.** A room with its own `loot:` pays it to everyone present when it
  clears. In a raid each boss room, and the finish, pays each player once per tier per week; after
  that they can still help, and are told the loot is spent. Lockouts reset on Monday (UTC) with the
  keystone affixes, and live in `expedition-raid-lockouts.yml`. The board's tier cards show
  "Looted this week: n/6".
- **Unlocks.** `unlocks: [nether]` on a room records the unlock for everyone present when it clears
  (`expedition-records.yml`). Gorrak's Kiln has it: beating Gorrak on any tier opens the Nether's
  **Cinder Shelf** route (`/expedition routes`) to that player, even before the season unlocks it
  for everyone. Vanilla portals stay sealed.
- **Linked councils.** `link-seconds: 10` on a council: when one member falls, the rest must follow
  within that time or it rises again at full health (a `revive` script trigger fires). Skarn and
  Vesh use it.
- **Submerging bosses.** A phase with `submerge-seconds: 30` makes the boss sink out of reach:
  untouchable, unseen and silent until the time is up or its adds are dead. Pyrrhax submerges at
  66% and 33% and sends his Sons of Flame.
- **Wrath** joins the ability kit: a wide ring, then a blast that throws everyone inside outward.
- Encounter magma cubes no longer split and encounter piglins and hoglins no longer convert, and
  claim protection no longer shields expedition enemies that count as animals (hoglins).

When Cinder Keep's sample file changes, a server whose copy is unedited gets the raid on the next
start; its template rebuilds from the new plan (`CinderKeepTemplate`).

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
party: {min: 2, max: 5}          # 1-5 (1-8 with raid: true)
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

### Goals, rooms and puzzles

```yaml
goal: forces              # rooms (default) | bosses | forces | timed
forces: 120               # forces: enemy forces needed before the final room opens
time-bonus-seconds: 45    # timed: seconds added to the clock per cleared room
time: 13600               # optional time of day for the run (0-23999)
raid: false               # true allows parties of up to 8 (no hired allies)
requires: [hollow_barrow] # expeditions each player must have cleared first
rooms:
  east_wing:
    requires: []          # rooms that must be cleared first; omitted = the room above it
    optional: false       # optional rooms aren't needed to finish
    puzzles:
      levers: {type: levers, title: Pull the three levers, blocks: [[3, 65, 40], [-3, 65, 40], [0, 65, 44]]}
      chimes: {type: sequence, blocks: [[...], [...], [...]], order: [2, 1, 3], show: true}
      plates: {type: plates, blocks: [[...], [...]]}          # all held down at once
      targets: {type: targets, blocks: [[...], [...]], seconds: 20, supply: [4, 64, 16]}   # supply: a barrel handing out the kit
      course: {type: reach, area: [x1, y1, z1, x2, y2, z2], start: [0, 64, 10], fail-below: 55}
    encounter:
      type: council       # waves | clear | survive | boss | council | puzzle
      bosses: [custom:a, custom:b, custom:c]   # council: 2-4, spawned at the first points
      abilities: [cleave, slam]                # boss abilities from the kit
      phases:                                  # switch abilities below a health %
        - {at: 50, abilities: [meteor, summon], title: The council rages}
        - {at: 25, abilities: [meteor], title: Sinking, submerge-seconds: 20}   # vanish until adds die or time is up
      enrage-seconds: 240                      # bosses hit 50% harder and move faster after this
      link-seconds: 10                         # council: a fallen member rises unless the rest fall in time
    loot: {marks: 120, items: {gold_ingot: 6}} # paid when this room clears (raids: once a week)
    unlocks: [nether]                          # recorded for everyone present (opens the Cinder Shelf)
```

The **goal** decides when a run is won:

| Goal | Win when |
|---|---|
| `rooms` | Every room that isn't optional is cleared. |
| `bosses` | Every boss and council room is cleared, in any order the map allows. Other rooms are optional. |
| `forces` | Every required room is cleared. The last one stays locked until enough enemy forces are slain; the sidebar shows the percentage. |
| `timed` | Every required room is cleared before the clock runs out. Each cleared room adds `time-bonus-seconds`. |

Smaller parties meet fewer enemies (see party-size scaling), so each kill is worth correspondingly
more toward the forces goal; a solo player can always reach it.

**Supply crates.** A `targets` or `plates` puzzle should name a `supply:` barrel. Right-clicking it
hands the player a bow and 16 arrows (targets) or four counterweights per plate (plates), unless
they already carry some. Supplies are tagged and taken back when the player leaves the run or next
joins outside one, so nobody fails a room for not packing the right thing. `/expedition check` warns
about item puzzles without a crate.

**Drops.** Expedition enemies drop none of their vanilla loot or worn gear (no saddles, emeralds or
rotten flesh); only the server's reward drops, relics and purses.

`/expedition check` reports rooms whose requirements can never be met, puzzle blocks outside their
room, a forces goal without a target, and councils without enough spawn points.

### Mobs, roles and abilities

```yaml
mobs:
  "custom:emberbound": {role: miniboss, abilities: [charge, leap], forces: 12}
  "vanilla:husk": {role: elite, abilities: [cleave], title: Drillmaster}
```

Roles: `normal` (forces 1), `elite` (1.6× health, 1.15× damage, forces 4), `miniboss` (3× health,
1.3× damage, gets the boss bar when no boss is up, forces 12). An elite with no abilities listed
rolls one from the elite pool; a mini-boss rolls two. A custom mob whose tier is already elite
counts as one. Elites cast every 5 s at most, bosses every 3.5 s, each ability on its own cooldown.
Ability damage is dealt as the caster, so tier, modifier and `boss-damage` multipliers apply.

| Ability | Telegraph and effect | Cooldown |
|---|---|---|
| `charge` | A red line to the farthest player, then a rush that hits and knocks aside anyone in the way | 10 s |
| `cleave` | A red cone in front, then a sweep | 7 s |
| `slam` | A red ring around the caster, then a shockwave that launches everyone inside | 12 s |
| `leap` | A red disc on a far player, then a landing that hits and slows | 14 s |
| `blink` | Smoke, then it reappears behind a player | 9 s |
| `fire_pools` | Orange discs under each player, then fire | 15 s |
| `meteor` | A shadow follows one player, then locks; the impact is split between everyone standing in it | 18 s |
| `summon` | Calls two helpers (the room's first wave mob) | 25 s |
| `shield` | Summons two helpers and can't be hurt until they die | 30 s |
| `frost_nova` | A pale ring, then freezes and slows everyone close | 14 s |
| `poison_cloud` | A green disc, then a poison cloud that lingers | 12 s |
| `tether` | A chain to the nearest player, then a pull; breaking line of sight snaps it | 16 s |
| `barrage` | A volley of arrows at random players | 10 s |
| `mend` | Cast (2.5 s, 1 hit stops it): heals itself and wounded allies by 15% | 16 s |
| `web` | Pins a player in a cobweb | 12 s |
| `wrath` | A ring 9 blocks wide, then a blast that throws everyone inside outward | 20 s |
| `volatile_mark` | A dark ring follows one player for 4 s, then bursts on everyone near them | 17 s |
| `bolt` | Cast (1.8 s, 1 hit): a bolt at its threat target; needs line of sight | 5 s |
| `ward` | Cast (2 s, 1 hit): its most wounded ally takes far less damage for 10 s | 14 s |
| `dread_howl` | Cast (2.5 s, 1 hit): slows and blinds everyone within 9 blocks | 16 s |
| `rally` | Cast (2 s, 1 hit): nearby allies hit harder for 10 s | 18 s |
| `heavy_blow` | Cast (2 s, can't be stopped): a huge hit on its threat target; a raised shield takes it and stepping out of reach avoids it | 11 s |
| `soul_rend` | Cast (4 s, 3 hits): tears at the whole party when it finishes | 26 s |

**Casts.** An ability with a cast time shows a bar over the mob and roots it. A yellow bar can be
stopped with charged hits (a full swing or a full-draw arrow); a red bar can't. An interrupted cast
is lost, its cooldown still runs, and whoever landed the last hit gains threat. Set
`behavior: caster` on a mob's profile and it keeps its distance and fights with its abilities
instead of closing to melee.

Expedition creatures ship in the plugin as `content/expedition-mobs.yml`, so updates bring new ones.
An entry with the same id in `content/mobs.yml` overrides the shipped one. Mob entries accept
`scale` (size), `speed`, `equipment` (`head`, `chest`, `legs`, `feet`, `hand`, `off-hand`) and
`ability: none` (no built-in pulse, for mobs whose attacks come from the kit).

Enemies never hurt each other, poison clouds touch only players, and fire left by abilities or
hazards doesn't burn enemies (a player's fire aspect still does).

### Packs and threat

```yaml
packs:
  door_guard: {at: [0, 64, 24], mobs: {"custom:barrow_thrall": 3, "custom:crypt_archer": 1}, aggro: 8}
  walkers:
    at: [0, 64, 50]
    mobs: {"custom:bone_lurcher": 1}
    aggro: 7            # default 9
    leash: 36           # default 36
    patrol: [[0, 64, 50], [0, 64, 36]]

rooms:
  wardens_hall:
    requires-packs: [door_guard]         # stays barred until these are dead
    seal: [-52, 64, 66, -52, 67, 70]     # open air in the map; barred by the run
```

A pack stands in the map from the start of a run with its AI off. It wakes when someone comes
within `aggro` blocks in plain sight, when a member is hit or taunted, or when a fight comes within
6 blocks of it in plain sight. All its members then fight together. It gives up when the party is
beyond `leash` blocks of `at`, when a member is dragged that far, or after 4 s with nobody to fight:
members walk home unhurt, heal and wait again. Dead members stay dead. Counts scale with party size
like waves, and forces and drops work the same way.

A `seal` is one block thick. It's barred while its room's `requires-packs` live and during the
room's fight. If nobody alive is left inside for 3 s, the fight counts as a wipe. Rooms with a
`requires-packs` list show how many packs are left on the sidebar.

Every enemy in a run keeps a threat table and fights the top of it. A hit adds its damage as threat
(Bulwark allies add three times that), and a raised shield taking a hit adds four times the hit.
A new target must pass 110% of the current one's threat in melee reach, or 130% from further away.
Sneaking and right-clicking with a shield taunts every enemy within 10 blocks for 4 s, once every
12 s. All of this applies to wave and boss rooms too.

`records-version: <n>` at the top of a file clears that expedition's best times and best keys once,
for when a map changes. Clears, keys and lockouts stay.

### Campfires, set pieces, hazards, cutscenes and characters

```yaml
campfires:
  wayfarers_fire: {at: [3, 64, 68], lit: false, title: Wayfarer's Fire}
setpieces:
  bridge: {box: [-1, 63, 60, 1, 63, 70], block: spruce_planks, style: rise}   # instant | rise | crumble
hazards:
  rockfall: {type: rocks, area: [-9, 63, 78, 9, 63, 94], every: 6, count: 3, damage: 4}  # rocks | fire | eruption | gas
  lances: {type: lane, area: [-30, 64, 0, 30, 64, 40], every: 12, count: 1, damage: 7, soul: true, lanes: [[-29, 5, 29, 5], [-29, 15, 29, 15]]}
  channels: {type: lane, area: [-18, 64, 100, 18, 64, 136], every: 10, count: 2, damage: 7, burn: 5, lanes: [[0, 120, -9, 112]]}
  roof: {type: collapse, area: [-18, 64, 100, 18, 64, 136], damage: 9, spots: [[-8, 73, 118]]}
  grief: {type: vigil, area: [-46, 64, 30, -30, 64, 50], every: 15, count: 1, damage: 2, devices: [candle_one]}
devices:
  candle_one: {at: [-44, 65, 34], title: Vigil Candle}
ambience:
  - {sound: ambient.cave, every: 40, volume: 0.7, pitch: 0.7}
  - {sound: block.fire.ambient, every: 8, area: [-18, 64, 100, 18, 78, 136]}
intro: prologue           # a cutscene played for the party as the run starts
outro:                    # narration lines after the win, a few seconds apart
  - "The Old Sentinel lowers its blade and steps aside."
cutscenes:
  boss_wakes:
    - {at: [0.5, 70, 105, 0, 22], seconds: 3, text: <gray>Something stirs…</gray>, sound: "block.stone.break 0.5",
       lines: ["|Dust falls from its shoulders."]}
    - {at: [0.5, 67, 113, 0, 8], seconds: 4, title: <gold>The Old Sentinel</gold>, text: <gray>Last guardian of the road</gray>,
       fade: true, lines: ["The Old Sentinel|Who walks the Wardens' road?"], actions: ["build bridge"]}
npcs:
  wren: {name: Captain Wren, entity: villager, at: [2, 64, 3, 90], lines: [Welcome aboard!, ...]}
```

- **Campfires** are placed (unlit unless `lit: true`) when the run starts. Lighting one makes it the
  respawn point, until another is lit or a room checkpoint is reached.
- **Set pieces** are changed by scripts: `build` fills the box, `break` empties it and `restore` puts
  back what the template had. `rise` builds layer by layer; `crumble` breaks in dusty batches.
- **Hazards** always telegraph (a disc on the floor for about 1.5 s). About half of each strike aims
  at players inside the area. `rocks` drop falling rock, `fire` leaves brief fire, `eruption`
  launches players and `gas` leaves a poison cloud. Wipes stop every hazard.
- **Hazards that use the room.** A `lane` marks a strip of floor, then rolls down it from `[x1, z1]` to
  `[x2, z2]` and hits everyone in its path. A wall or column two blocks above the floor stops it, so
  columns give cover; `burn` leaves real fire behind it for that many seconds, and `soul: true` draws it
  blue and slows instead of burning. A `collapse` brings down the next of its `spots` each time it's
  fired (a chandelier hub, with its arms and chain) after a three-second warning. It hits anyone
  underneath, and a boss caught there loses 6% of its health and staggers, so it can be lured. A
  `vigil` snuffs `count` of its `devices` every `every` seconds, and a pool spreads from each until a
  player right-clicks it to light it again. With `trail: cobweb` a lane leaves that block behind for `burn`
  seconds instead of fire, and poisons. A `brood` swells `count` of its `devices` every `every` seconds;
  each hatches two of its `mob` after nine seconds unless a player right-clicks it first, and a sac that's
  gone stays gone. A `burndown` lights one of its `sections` each time it's fired, each
  `{box: [...], device: id}`: the box falls away after thirteen seconds unless a player right-clicks its
  device, which works once. Keep the boss's spawn, its adds' spots and the reward chests out of every box.
  A `gust` picks one of its `lanes` as a wind direction, warns for two and a half seconds, then pushes
  anyone in `area` who has nothing solid within five blocks upwind.
- **Falls.** `falls: {below: 52, damage: 4, message: "..."}` catches anyone below that height and puts
  them back where they last stood, or at the room's checkpoint in a boss fight. It never kills. Enemies
  that go over are put back too.
- **Motes.** `motes: firefly` drifts that particle round each player. It has to be one that needs no data.
- **Website maps.** Each sample dungeon's guide page shows a parchment map drawn from its real template:
  floor plan, boss rooms in colour, a medallion per boss, its fires and its rooms. `ExpeditionMapsTest` fails
  when a map goes stale. Redraw them by running that test with the environment variable `WRITE_MAPS=1`, then
  commit `Website/aerborne/static/img/maps`. Secret caches stay off, because the walk over the floor only
  jumps on `reach` puzzles.
- **Devices** are blocks players right-click in a fight: campfires, candles, sacs, cisterns.
- **Ambience** plays each sound to each player from a spot off in the dark, about once every `every`
  seconds, and only inside `area` if it has one. A boss fight also has a heartbeat, which quickens as the
  boss weakens.
- **Cutscenes** glide the camera between shots (eased), or within one with `to: [x, y, z, yaw,
  pitch]`, where the shot moves from `at` to `to` over its own length. Each plays once a run, so a
  second try at a boss skips its scene, and sneaking skips one for that player. A shot's `title` and
  subtitle (`text`) hang in front of the camera and wrap to fit, at any interface scale. A shot can
  also play a `sound` (`key [pitch]`) and speak `lines` spread across its length: `"Speaker|line"` (an
  NPC id or any name) or `"|line"` for narration. `fade: true` cuts straight to the shot instead of
  gliding to it. `actions` are script lines run as the shot starts, once for
  the party, so a scene can raise a bridge or start a hazard. Players are spectators meanwhile;
  anyone who logs off mid-scene gets their game mode back on join. A scene can run 45 seconds.
- **Intro and outro.** `intro:` plays a cutscene for the party as the run starts; `outro:` lines are
  narrated after the win. `guide.lore` is the expedition's backstory: the guide book's first page
  after the overview, and the website shows it.
- **Boss name cards.** A boss's name appears as a title when it spawns, with the `title` from its
  `mobs:` profile underneath, unless a cutscene is introducing it.
- **Boss entrances.** `entrance: {style: rise|reanimate|summon|descend|bloom, delay: 3, seconds: 5}` on a boss encounter
  holds the boss out of sight, untouchable, and brings it in `delay` seconds into the fight. `rise`
  climbs steadily through breaking floor. `reanimate` scatters a skeleton around the room's first point
  from the start of the run. List soul fires under `sources: [[x, y, z], ...]` and they go out one by
  one, their flames fly into the bones, the bones build a body from the feet up (sized to the boss,
  carrying its weapon and shield) and char black as fire climbs them, and the boss takes the figure's
  place as the fires come back. `summon` lights its `sources` one by one, runs their flames to a
  turning circle around the first point, then raises the boss in a column of fire and slams it
  down; those fires stay lit for the fight. Either takes about a dozen seconds to read well. The boss is
  let loose once every camera is back, and a second attempt takes a second with no wait. Time a
  scene around it.
- **Characters** stand where placed, can't be hurt or traded with, and play their next line when
  right-clicked. Scripts make them (or anyone) speak with `say`, and move them with `npc`. With
  `quests: <giver id>` a character is a quest giver for `content/world-quests.yml`: it shows a blue
  mark to each player it has a quest for, and opens its board instead of talking while it does.

### Scripts

Optional actions run on encounter events. Triggers: `start`, `clear`, `wave-N` (when wave N is
released), `health-N` (boss at or below N%, once; councils use their average), `timer-N` (N seconds
after the room starts), `down-N` (the Nth council boss falls), `solved-<puzzle>`, `enrage`, and
`ward-broken` and `ward-failed`, for how a ward ended.

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
| `say <speaker>\|<line>` | `say wren\|Mind the gap!` (an npc id or any name) |
| `cutscene <id>` | Play a cutscene for the party |
| `build <piece>` / `break <piece>` / `restore <piece>` | Change a set piece |
| `hazard <start\|stop\|pulse> <id>` | Run, stop or fire once a hazard |
| `campfire <id>` | Light a campfire for the party |
| `npc <id> <x> <y> <z> [yaw]` | Move a character, say to meet the party at the end |
| `ward <seconds> <device>...` | Lift the boss out of reach until the party douses the devices. Takes two plus the party's size, at most all of them |
| `cast <ability>` | Every boss in the room uses an ability now |

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
| `raging` | Regular enemies deal 50% more damage below 30% health |
| `sanguine` | A dying enemy leaves an 8 s pool (2.5 blocks) that heals other non-boss enemies 8% a second and stings players |
| `timed` | Clear within 60% of the time limit for an extra loot roll |

## Configuration

`config.yml → expeditions`: `max-instances` (6), `ready-check-seconds` (30), `queue-fill-seconds`
(90), `rejoin-minutes` (5), `linger-after-finish-seconds` (180), `vanilla-portals` (false) and
`tiers`, plus:
- `scaling`: `count-floor` (0.4), `health-floor` (0.5) and `ally-weight` (0.6).
- `allies`: `enabled` (true), `offer-after-seconds` (120), `max` (4), `damage-multiplier` (1.5) and
  `roles` (bulwark, blade, hedge-witch, archer).
- `log-abilities` (false): write a console line for every ability cast, for tuning fights.

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

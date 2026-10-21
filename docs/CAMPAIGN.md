# Season campaign

Each season is a themed set of goals the whole server works towards together. Player actions feed
server-wide counters. Reaching a milestone gives everyone timed buffs, claimable items and Marks,
and can unlock expeditions or features. Staff define a season entirely in
`plugins/NerdSMP/campaign/season.yml`; no code changes are needed.

Code: `campaign/SeasonCampaignService.java` (sources, rewards, buffs, claims, `/campaign`),
`campaign/CampaignMenu.java`, and the Bukkit-free rules in `campaign/season/` (`SeasonLoader`,
`SeasonValidator`, `SeasonLedger`). The shipped sample is `src/main/resources/campaign/season.yml`
(**The Ember Accord**).

## For players

| Command | What it does |
|---|---|
| `/campaign` | Board: goals with progress and your share, milestones with their rewards, running buffs, claim button, top contributors. |
| `/campaign progress` | The same summary in chat (also works from the console). |
| `/campaign claim` | Collect every unclaimed item and Marks reward. Needs one free slot per item stack. |
| `/campaign contribute [goal] [amount]` | Hand in the held stack to a goal that collects that item. |
| `/campaign top [goal]` | Top contributors this season. |

- Progress counts from the moment the season starts until it ends.
- Each player's contribution is capped **per goal per day** (UTC). Hitting the cap shows a short
  notice once per day.
- Reached milestones stay reached for the whole season.
- Buffs reach everyone online and anyone who joins while they run, including players in
  expeditions. A milestone that repeats a running buff extends it rather than stacking it.
- Item and Marks rewards are **claim-once per account**, including players who were offline when
  the milestone fell. Players are reminded on join while they have something to claim.

## Writing a season

```yaml
id: ember-accord                 # a new id starts a fresh season with its own progress
title: The Ember Accord
description: One or two sentences for the board and the website.
starts-at: 2026-11-01T00:00:00Z  # optional; without it the season starts the first time it loads
duration-days: 42                # or ends-at: 2026-12-13T00:00:00Z
player-daily-cap: 2500           # per goal, per player, per UTC day

goals:
  forge-fires:
    title: Stoke the Forges
    description: Ore, fuel and smelted metal.
    icon: blast_furnace
    target: 50000
    daily-cap: 2000              # optional override of player-daily-cap
    sources:
      - {type: mine, points: 2, materials: [iron_ore, deepslate_iron_ore]}
      - {type: smelt, points: 1}                     # no filter = anything
      - {type: contribute, points: 2, materials: [coal, charcoal]}
  delvers:
    title: Delve Together
    target: 400
    sources:
      - {type: expedition, points: 1, tiers: {heroic: 2, mythic: 4}}   # multipliers by tier
      - {type: expedition, points: 3, expeditions: [cinder_keep]}       # or name expeditions
      - {type: boss, points: 1}
  wardens:
    target: 25000
    sources:
      - {type: kill, points: 1, mobs: [hostile]}     # or custom:<id> / vanilla:<type>

milestones:
  first-spark:
    title: First Spark
    description: Optional flavour.
    icon: flint_and_steel
    requires: {forge-fires: 8000}                    # every listed goal must reach its number
    rewards:
      - {type: buff, effect: haste, amplifier: 0, hours: 6}
      - {type: unlock, feature: world-events}
  ember-gate:
    requires: {forge-fires: 20000, delvers: 40}
    rewards:
      - {type: unlock, expedition: cinder_keep}      # sealed until this milestone
      - {type: boost, kind: skill-xp, percent: 25, hours: 12}
      - {type: item, item: golden_apple, amount: 2}  # NerdSMP item id or vanilla material
      - {type: marks, amount: 250}
      - {type: broadcast, message: "<gold>The Ember Gate is open!</gold>"}
      - {type: command, command: "say The gate is open"}   # console, once
```

### Sources

| Type | Counts | Filter key |
|---|---|---|
| `mine` | Natural blocks broken in survival or adventure mode. Uses the same placed-block protection as skill XP, so placing and re-breaking earns nothing. | `materials` |
| `smelt` | Items taken out of furnaces, per item. | `materials` |
| `craft` | Items crafted, per recipe result. | `materials` |
| `fish` | Catches, per item. | `materials` |
| `kill` | Mobs a player kills. `hostile` matches any hostile mob. | `mobs` |
| `boss` | Boss kills. Inside an expedition, every party member present gets the credit. | `mobs` |
| `expedition` | Each member's completed run, times the tier multiplier. Staff test runs don't count. | `expeditions`, `tiers` |
| `contribute` | Items handed in with `/campaign contribute`, per item. | `materials` |

One action can feed several goals. Points are clamped to what's left of the player's daily cap
for each goal.

### Rewards

| Type | Fields | Behaviour |
|---|---|---|
| `buff` | `effect`, `amplifier` (0–4), `minutes`/`hours`/`days` | Server-wide potion effect, refreshed every 5 s, icon shown, no particles. Never weakens a stronger potion a player already has. |
| `boost` | `kind` (`skill-xp` or `job-marks`), `percent` (1–200), duration | Adds to skill XP and job Marks bonuses everywhere they're earned. |
| `item` | `item`, `amount` (1–64) | Claim once per account. |
| `marks` | `amount` | Claim once per account (idempotent payment). |
| `unlock` | `expedition: <id>` or `feature: world-events` | An expedition named here stays sealed until the milestone. `world-events` enables rifts and frontier events. |
| `broadcast` | `message` | MiniMessage to everyone when the milestone is reached. |
| `command` | `command` | Console command, run once. |

Timed rewards last at most 30 days.

## Staff

Permission: `nerdsmp.campaign.admin`.

- `/campaign admin reload`: re-reads `season.yml`. If it has errors, the running season keeps going
  and the errors are listed. Changing anything but the `id` keeps progress.
- `/campaign admin check`: validates names (materials, mobs, items, effects, expeditions,
  features, goals in `requires`) against the running server.
- `/campaign admin add <goal> <points>`: adjust a counter (may be negative, never below zero). It
  credits no player and can trigger milestones.

Progress is stored per season in `campaign/state-<id>.yml`: counters, each player's season and
daily contributions and last known name, reached milestones, running buffs and claim receipts. It
is written every 30 seconds off the main thread and on shutdown.

## Compatibility

- The older `CampaignService` view (HUD and older callers) is served from the season: goals act as
  categories and the sum of goal points as the total.
- `ServerProgressionService` now only answers feature unlocks. Unlocks recorded by the previous
  campaign in `server-progression.yml` stay unlocked. It no longer gates Nether and End teleports;
  [expeditions](EXPEDITIONS.md) seal the portals instead.
- The old `campaign:` keys in `config.yml` (id, title, categories, milestones, progression) are no
  longer read. Totals from the previous campaign (`campaign-state.yml`) are left on disk and not
  carried into the new season.

## Website

The game API serves the running season publicly at `/api/v1/campaign` (goals, milestones, rewards,
buffs and top contributors by in-game name) and the open expeditions at `/api/v1/expeditions`. The
website shows them at `/campaign` and `/expeditions`. Console commands and broadcasts are never
published.

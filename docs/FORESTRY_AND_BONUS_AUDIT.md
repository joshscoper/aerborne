# Forestry improvements and bonus audit

## Player changes

- Forestry activity XP is three times the configured log XP. The existing default of 6 becomes 18 XP per eligible log, before bonuses.
- Break the base of a natural or legitimately grown tree with an axe to fell it. Sneak to cut a single block instead. Placed logs and persistent decorative leaves do not make eligible trees.
- Tree capacity increases at Forestry levels 1/10/20/35/50: 16/32/64/128/192 logs. Oversized, ambiguous, or partly unloaded trees fall back to ordinary harvesting; discovery never loads chunks.
- Automatic work uses normal `Player.breakBlock` calls, respecting block protections, native drops, and tool wear. It stops on a denied log, tool change, logout, leaving the area, sneaking, or changing settings. Partial work only awards harvested logs.
- Finished trees can replant using plain saplings from inventory. A single trunk costs one; a two-by-two trunk costs four. Replanting fires placement events and checks the inventory and supporting blocks again before charging seeds. It does not instantly grow trees.
- Tree Felling and Tree Replanting can be toggled independently in player settings.
- Forestry rhythm lasts 60 seconds. A felled tree advances the chain once, not once per automatic log; its bonus applies to the tree's earned XP. Manual harvesting still advances normally. Farming's rhythm is unchanged.
- Forestry recipes provide alternate routes to the existing Wildwood Axe (10), Boots (20), and Coat (35). Existing Smithing recipes remain available.
- Completing a timber daily contract now also grants 500 Forestry XP. This fixed reward uses a separate idempotency key; retrying a failed delivery cannot pay Marks or XP twice. Already claimed contracts are not retroactively rewarded.

## Bonus pipeline

An equipped profile combines identified items in their correct equipment slots, quality-scaled base stats, rolled affixes, socket gems, enchantments, active set thresholds, and explicitly global skill-tree passives. Duplicate copies of the same set item do not count as different set pieces. Unidentified items do not provide their hidden stats.

Activity XP then adds matching tool power, the matching skill's XP talents, Adventurer XP, island activity perks where eligible, and gathering rhythm. Each percentage is included once. Tree harvesting captures its XP and Marks profile when work starts, before tool wear or switching.

| Stat | Gameplay consumer / scope |
| --- | --- |
| `damage`, `projectile-damage` | Melee or projectile base damage; quality affects base stats |
| `damage-flat`, `damage-percent` | Equipped attack modifiers |
| `projectile-damage-percent` | Arrow attacks, using the fired weapon snapshot |
| `defense` | Incoming player damage mitigation |
| `execute-damage-percent` | Attacks against targets at or below 30% health |
| `life-steal-percent` | PvE hits, excluding armor stands; capped at 4 health per hit and the target's available health/absorption |
| `mining-power`, `excavation-power` | +5% matching activity XP per power |
| `gathering-power` | +5% Forestry and Farming activity XP per power |
| `fishing-power` | Matching XP and fishing luck |
| `skill-xp-percent` | Activity XP, not fixed quest/collection milestone grants |
| `job-marks-percent` | Diminishing-work job payouts, not all currency grants |
| `durability-save-percent` | Equipment wear preservation; existing 85% cap remains |
| `harvest-extra-percent` | Eligible crop produce and extra Forestry logs |
| `geode-find-percent` | Mining/Excavation geode rolls; relative to base drop odds |
| `fishing-luck-percent` | Fishing reward selection and existing bounded bite-time improvements |
| `treasure-find-percent` | Fishing reward luck and Excavation discovery rolls |
| `excavation-find-percent` | Excavation discovery rolls; existing final 15% discovery cap remains |
| `poison-potency-percent` | Applied weapon/arrow poison strength and duration |

Skill-scoped talents remain scoped: XP, work Marks, crop/log yields, Hunting damage, Mining/Excavation geodes, Fishing treasure, building fall reduction, animal inheritance, and prepared meal duration/nutrition. Alchemy's Careful Measure can save one non-container reagent during menu brewing; it never saves or duplicates the carrier bottle, bucket, potion, or arrow.

Global passives are resolved by their stat identity rather than their position in the GUI. This includes Tempering, Careful Tools, Lasting Work, Workshop Care, Deep Reserves, Tidal Instinct, Second Wind, Relic Sense, and Concentrates. Their existing node IDs and player selections are unchanged.

## Bugs repaired

- Passive lookup no longer depends on which branch happens to appear last in the GUI. Tempering now appears in the equipped profile, and its former separate durability adjustment was removed so wear preservation still applies it exactly once.
- Extra-harvest item bonuses now work on timber, including Reaping and suitable affixes/gems.
- Excavation includes equipped treasure bonuses and Adventurer treasure bonuses.
- Ordinary-weapon fallback damage now includes Executioner and composes Hunting damage consistently with templated weapons, without multiplying vanilla critical/cooldown effects again.
- Arrow poison uses the weapon recorded on the projectile instead of whichever weapon happens to be held when it lands.
- Life steal waits until damage listeners finish, rechecks cancellation, and cannot heal based on overkill damage.
- Careful Measure is now used by the alchemy menu's inventory transaction.
- Daily block progress waits for final cancellation and a real block-state change instead of advancing on a later-canceled event.

## Configuration and migration

New defaults in `config.yml`:

```yaml
forestry:
  enabled: true
  experience-multiplier: 3.0
  blocks-per-tick: 12
  maximum-active-trees: 16
  daily-contract-xp: 500
```

Existing configurations use these fallback values without needing replacement. Existing `progression.xp.forestry-log` values are retained and multiplied. Automatic work has a global per-tick budget and bounded concurrent trees; player XP and job persistence are batched per completed/partial tree instead of per automatic log.

Recipe schema 4 adds only the three newly introduced Forestry recipe IDs. Operator edits and deliberately removed old recipes stay intact. The prior file is backed up as `content/recipes.yml.before-v4.bak` before migration. No player, account, island, inventory, or world reset is performed.

## Verification limits

Unit tests check stat coverage, passive stacking, activity XP composition, life-steal caps, tree discovery/capacity, and recipe migration. A disposable Paper fixture additionally checks real item metadata, equipped profiles, set thresholds, actual progression writes, damage-event calculations, and a controlled tree-break event lifecycle.

The controlled tree fixture dispatches Bukkit events and substitutes deterministic block removal/wear calls. It is not an authenticated-client test of native drops, axe wear, latency, GUIs, or another protection plugin. Perform those in staging before public deployment. Batched work is flushed during orderly shutdown; this change does not add an atomic power-loss transaction joining world saves, XP, job currency, quests, and island XP.

Keep a backup and perform a full restart to deploy. Do not hot-swap the jar or use plugin reloaders.

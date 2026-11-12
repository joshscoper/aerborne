# Player stats and the vitality action bar

`/stats` opens the player's current health, defense, offense, utility, gathering, progression and
active set bonuses. It is also available under Character and Profile. The page is a snapshot of
the equipped build; reopen it after changing equipment.

## Real health, compact hearts

Health remains Minecraft's actual server health. Damage, healing, potion effects, party health
displays, life steal and expedition mechanics continue reading the real health attribute. The
client's display is scaled to **20 display points (ten hearts)**, so a larger maximum does not add
rows of hearts.

The action bar shows current/max health, absorption when present, and total RPG defense. It refreshes
every ten ticks. The plugin's temporary XP, quest, gathering and combat notices share that bar for
three seconds instead of replacing the vitals or flickering against a second sender.

The same bar shows the Well Fed countdown and recovery delay. [Food and recovery](FOOD_AND_RECOVERY.md)
replaces hunger regeneration with free, maximum-health-scaled recovery after combat.

Default maximum health before external attributes is:

`(20 + 2 × (Adventurer level − 1) + equipped health) × (1 + health bonus / 100)`

That result is capped at 512. For example, Adventurer 25 grants 48 extra health. Wearing an item-level
24 chestplate adds 6, for 74 total before other bonuses.

Every leveled catalog helmet, chestplate, leggings and boots gains `floor(item-level / 4)` innate
health unless the catalog entry explicitly specifies `stats.max-health`. The derived stat appears in
the item tooltip and improves with workmanship quality. Materials, weapons and shields do not inherit
armor vitality. Gear and authored set bonuses support:

- `max-health`: flat health, quality-scaled when innate to equipment.
- `max-health-percent`: percentage bonus after base, progression and flat equipment health.

Existing item IDs, rolls and sockets are preserved. Item presentation version 2 causes old gear to
refresh through the normal item updater. No operator item catalog needs to be overwritten.

Maximum increases during normal play do not heal the player. Removing health equipment clamps current
health to the lower maximum. On joining, quitting, entering/leaving creative or shutting down, the
service preserves the health fraction while adding/removing its own transient modifier. It restores
the previous heart scale and retains other plugins' modifiers. A normal respawn restores full health.

The service waits for the cached progression snapshot before changing maximum health, preventing a
loading profile from temporarily stripping a veteran's health. There are no per-tick database reads.

## Defense and the stats page

The defense value matches combat: equipped bonuses plus passive talents and Adventurer rank defense.
Its attack damage reduction is `defense / (100 + defense)`. The page shows that reduction and the
corresponding effective maximum health. Vanilla armor, resistance and absorption remain separate.
RPG defense applies to entity attacks, including projectiles; it does not claim to mitigate falls,
fire or starvation.

The offense panel distinguishes full-charge custom melee damage from flat, percentage, projectile
and execute bonuses. Critical hits, target defenses and situational effects are applied during combat.
Gathering and progression panels identify equipped/global bonuses; activity-specific skill trees,
masteries and island bonuses continue to apply through the existing activity resolver.

## Configuration

Defaults are read even when an existing `config.yml` omits the new section:

```yaml
player-stats:
  enabled: true
  action-bar: true
  base-health: 20
  health-per-adventurer-level: 2
  maximum-health: 512
```

Set `action-bar: false` to retain ordinary temporary action-bar notices without the persistent vitals.
Set `enabled: false` to retain vanilla health. The configured cap may not exceed Minecraft's 1024-point
attribute limit. `/hud` still controls the sidebar separately.

## Crafting and refining experience

All custom Workshop recipes award Smithing XP, including alloys, fishing rods, bows and gathering
tools. A recipe's gathering-skill unlock requirement does not change the skill trained by manufacturing
that equipment. Authored Cooking/Alchemy recipes train their explicit skill instead. Harvest Kitchen
owns its meal awards and is excluded from the generic crafting listener to avoid duplicate XP.

Vanilla equipment and smithing stations train Smithing; supported reagent/brewing recipes train
Alchemy. Reversible raw-material packing/unpacking has no crafting award. Existing per-craft rates
remain configurable through `progression.xp.smithing-craft`, `alchemy-craft` and `cooking-craft`.

Successful quality improvements also train Smithing: `smithing-quality-upgrade` (default 20) times
the new quality tier. The Expedition Forge grants `smithing-expedition-forge` (default 40) after
materials are consumed and a piece is produced. Previews, declined upgrades, failed requirements and
refunded/offline forge payments grant no experience.

Crafting uses a post-transaction receipt: ingredients must actually decrease and matching output must
reach the player's storage or cursor. Shift crafting counts completed operations. Cancelled events,
failed/full-inventory crafts and repeated event objects do not award XP. Survival mode, the same
connected player/account and a real output transfer are required.

Ordinary ores and custom furnace refinements train Smithing when a player personally collects proven
outputs. The custom recipe's `smithing-xp` controls XP per item; an older metallurgy entry without it
defaults to 8. Ordinary metal rates retain their existing settings. Native food smelting, including
smokers, trains Cooking at `progression.xp.cooking-smelt` (default 6 per item).

Furnace receipts persist the exact output identity and proven quantity only after a consumed input
and finalized output are observed. Inserted ingots/food, output replacement, cancelled smelts, repeated
withdrawals and banked vanilla furnace XP cannot create skill credits. Hoppers retire the corresponding
credits without assigning XP to an absent player. Collection keeps the existing nearby-player and
container-permission checks. Only verified outputs are credited; vanilla furnace XP remains separate.

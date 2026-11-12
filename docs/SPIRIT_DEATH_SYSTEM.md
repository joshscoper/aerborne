# Death, spirits and resurrection

Every player death keeps the actual Minecraft inventory and XP: armor, weapons, offhand, bag items and equipped accessories. The plugin clears death drops and dropped XP without copying inventory into another store. It adds no death fee or durability damage. Normal wear from combat still applies.

New deaths create no storage graves. Existing grave containers remain protected and accessible under their original rules; indexed legacy `/grave recover` requests are now free. When no legacy grave is indexed, `/grave` explains the new system or opens the player's spirit choices.

## Adventure-world flow

1. Die in `MMORPG` or a matched shipped adventure map. Your belongings remain with you.
2. Click Minecraft's **Respawn** button to release your spirit at the nearest safe sanctuary. The resurrection menu opens automatically.
3. Run back to your body, or choose the Spirit Healer. A private body marker, compass target and directional boss bar guide the corpse run.
4. Resurrect when the ten-second release delay has elapsed. The menu countdown updates while open.

| Choice | Requirements | Result |
| --- | --- | --- |
| Return to body | Stand on safe ground within 16 blocks, including vertical distance | 50% of your actual maximum health; no resurrection sickness. You return where you stand, so you can choose a safer spot near the body. |
| Spirit Healer | Return to your sanctuary, approach within eight blocks, and confirm | Full health; 60 seconds of resurrection sickness, reducing outgoing damage by 25%. |
| Unreachable body | A safe body location could not be found, or a saved sanctuary needs an emergency fallback | Spirit Healer returns you without sickness. |

Both resurrection choices include five seconds of incoming-damage protection. Attacking ends that protection. Food and ordinary healing work normally after resurrection. Neither choice costs Marks, consumes gear, or adds durability damage.

Adventure deaths clear the old `/back` death destination, so healer resurrection cannot be used as a shortcut back to the body.

Commands:

| Command | Use |
| --- | --- |
| `/spirit` | Open the resurrection choices. `/soul` and `/resurrect` are menu aliases. |
| `/spirit locate` | Show your body's coordinates. |
| `/spirit resurrect` | Resurrect near your body when ready and safely grounded. |
| `/spirit healer` | Review and confirm resurrection at your sanctuary. |
| `/spirit return` or `/spirit unstuck` | Return your ghost to the sanctuary. This keeps your original death and recovery timer. |
| `/helpme <description>` | Contact staff privately while still a spirit. |

Right-clicking the private Spirit Healer or body marker also opens the menu. Only the dead player sees their marker and healer. Living players cannot see or interact with their ghost.

## Other worlds

- **Islands:** retain inventory and XP and use the island's existing safe respawn. Void rescue remains a rescue rather than a death.
- **Expeditions:** retain their own checkpoint and shared-life behavior. Keyed expedition timer penalties remain governed by the run. They do not start an open-world corpse run.
- **Other survival worlds:** retain inventory and XP and use the normal travel/bed/anchor respawn rules.
- **Creative/Spectator staff:** retain inventory without entering the adventure-world ghost flow.

Ghosts use Adventure mode with a small movement bonus. They cannot fly or pass through walls. Damage, fire, drowning, hunger, looting, XP-orb pickup, inventory movement, crafting, gathering, item drops, mounts, combat and gameplay travel are blocked. Ghosts do not activate encounter zones, count toward boss scaling, receive new activity/visit progress, or display companion cosmetics. Gameplay commands and external inventory screens are blocked; chat, private staff help and related communication commands remain available. Login interaction is blocked until the account's death state has loaded.

## Sanctuaries and tuning

Default safe-town warps are **Highcrown**, **Druid's Ward** and **Sandmere**. Custom sanctuaries in the same world are considered alongside these, and the nearest candidate with a valid floor is selected. If none is available, the world spawn and then the public hub are checked. No terrain is placed or changed to make a sanctuary.

Configuration defaults, including on existing installations:

```yaml
death:
  spirit-worlds: [MMORPG]
  sanctuary-warps: [highcrown, druids_ward, sandmere]
  resurrection-delay-seconds: 10
  corpse-radius: 16
  healer-sickness-seconds: 60
```

Matching shipped adventure maps also enable spirits even when their world folder has another name. Instance and island worlds are always excluded from corpse runs. Delay is bounded to 0–60 seconds, body radius to 4–32 blocks, and healer sickness to 0–300 seconds. Inventory retention applies independently of these settings and of the vanilla world gamerule.

Staff with **`nerdsmp.death.admin`** can use:

- `/spirit sanctuary set <id> [display name]` while standing on safe ground in a public adventure world.
- `/spirit sanctuary remove <id>` and `/spirit sanctuary list`.
- `/spirit restore <online player>` for a stuck spirit. This safely restores them at the sanctuary without sickness and logs the acting staff member. It is an explicit staff recovery command, not a player shortcut.

Custom sanctuary definitions are atomic YAML in `spirit-sanctuaries.yml`. Existing dead players retain the sanctuary recorded for their death; moving or deleting a definition does not redirect a corpse run that is already underway.

`nerdsmp.admin` includes the recovery permission. Other staff can receive `nerdsmp.death.admin` explicitly.

## Persistence and compatibility

Canonical account records store `death.v1`: body and sanctuary world UUIDs/coordinates, the death identifier and release time, original mode/flight/invulnerability/compass settings, and sickness/protection deadlines. Linked clients share this state. Disconnecting or restarting does not create a free resurrection or reset sickness.

Resurrection first saves a pending destination and health fraction. It then teleports safely, restores the original abilities, saves Minecraft player data, and acknowledges the completed resurrection in the account store. Until acknowledgement succeeds, the player remains restricted and recovery can be retried. No inventory items or currency are replayed through this process. Invalid records fail closed and preserve their source data for staff recovery. Ordinary server stop/start is supported; in-flight spirits remain durable.

The tutorial, rotating tips, Tamsin's guidance and the website's existing `/guide/graves` article now explain this flow. Known obsolete stock grave-fee tips/dialogue are corrected on read without replacing operators' custom content. The website source changes still require the website's normal deployment.

The live death/restart fixture exposed an anticheat collision-prediction stall following a large position correction. Collision queries now have a 4,096-cell work limit, an 8,192-cell cache limit, finite coordinate bounds and an immediate return for empty vertical ranges. Over-budget predictions are discarded and resynchronized without treating them as cheat evidence. Authorized spirit transitions also grant movement synchronization grace.

The corpse-run and Spirit Healer choice follows the concept described in [Blizzard's World of Warcraft manual](https://assets.blz-contentstack.com/v3/assets/blt3452e3b114fab0cd/blt2e9295db02a222fc/6025bcbb6968b53d529edb2a/media_manual_classic_enUS.pdf). Health amounts, grace time and sickness here are Aerborne tuning choices.

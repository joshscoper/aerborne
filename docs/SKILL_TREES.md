# Connected talent trees

Implemented 2026-10-24. The reference image informed connected paths, ranked icons, shared prerequisites and capstones. The in-game implementation uses ordinary Minecraft inventory menus and item tooltips; it does not require a client mod or resource pack. The accompanying planner is a schematic of the real catalog, not a Minecraft screenshot.

## Trees and points

| Tree | Purchasable ranks | Maximum points | Open |
| --- | ---: | ---: | --- |
| Each of the 11 professions | 16 | 8 | `/skills`, then choose a profession |
| General Adventurer | 16 | 11 | `/adventurer` |
| Shared island, four specializations | 48 | 25 | `/island talents` |

There are 13 trees and 240 rank choices in total. This adds 44 profession crossroads/capstone ranks and 16 Adventurer ranks; the 48 existing island ranks are reorganized into branching paths.

Profession points arrive at skill levels **5, 10, 18, 27, 37, 40, 47 and 50**, preserving the existing budget. Four starting paths each hold three cumulative ranks at levels 5, 20 and 35. The three shared talents require level 30 and rank II in either connected starting path. The capstone requires level 50 and any two shared talents. It is possible to reach a capstone using five points; remaining points let players deepen or broaden their build.

Adventurer points arrive at levels **5, 10, 20, 30, 40, 50, 60, 70, 80, 90 and 100**, derived from existing overall progression. Its ranked paths unlock at levels 5, 30 and 60, shared talents at 45, and capstone at 90. Automatic Adventurer rank perks still apply. These points cannot be spent in profession trees.

Island points remain one per two island levels, capped at 25 at level 50. Each specialization is a diamond: root, two alternative middle paths, capstone. A middle talent needs root rank II; the capstone needs rank II in either middle talent. Existing level gates remain 2, 6, 14 and 26 for their respective talents. The two middle paths can therefore become available at different levels. Island points are shared; only the owner spends them, while co-op members can inspect the trees.

## Profession capstones

| Profession | Capstone | Bonus |
| --- | --- | --- |
| Mining | Heart of the Mountain | +20% geode odds while gathering. |
| Forestry | Ancient Grove | +4% equipment durability preservation. |
| Farming | Keeper of the Fields | +4% chance for stronger inherited animal traits. |
| Fishing | Voice of the Ocean | +6% fishing luck. |
| Hunting | Unbroken Hunter | +0.75% PvE life steal. |
| Excavation | Keeper of Lost Things | +6% excavation discovery chance. |
| Smithing | Anvil Eternal | +4% equipment durability preservation. |
| Alchemy | Master of Venoms | +6% weapon poison potency. |
| Building | Architect's Legacy | +4% equipment durability preservation. |
| Cooking | Feast of the Isles | +10% extra saturation from meals you prepare. |
| Enchanting | Signature of the Archmage | +6% shorter Arcane Signature cooldowns. |

Each profession also has three named crossroads. They add a modest skill XP bonus, work Marks bonus (Dust discount for Enchanting), and a profession-specific bonus. Their values feed existing gameplay consumers and daily payout limits remain in force.

## General Adventurer choices

- **Wayfarer:** +1%, +1%, +2% global skill XP across its three ranks.
- **Vanguard:** +1%, +1%, +2% weapon damage.
- **Warden:** +1, +1, +2 Defense.
- **Fortune:** +1%, +1%, +2% work Marks, subject to daily limits.
- **Frontier Wisdom:** +2% fishing treasure and excavation find odds; either Wayfarer II or Vanguard II.
- **Stand Together:** +2 Defense; either Vanguard II or Warden II.
- **Trusted Caravan:** +2% work Marks; either Warden II or Fortune II.
- **Legend of the Isles:** +3% global skill XP; any two shared talents and Adventurer level 90.

These are additional to the existing automatic rank perks. Defense, combat damage, XP, work Marks and treasure bonuses use the same live consumers as existing progression bonuses.

## Island specializations

| Specialization | Root | Alternative middle paths | Capstone |
| --- | --- | --- | --- |
| Industry | Prospecting | Rich seams / Miner's guild | Deep veins |
| Nature | Cultivation | Nursery / Bountiful harvest | Timber reserve |
| Automation | Compact spawners | Collection network / Busy workshop | Freight network |
| Stewardship | Living waters | Prize livestock / Angler's lodge | Co-op kitchen |

The first page shows Industry and Nature; the second shows Automation and Stewardship. For example, Busy workshop II can now lead to Freight network without Collection network. Existing collection limits already support that route; the workshop help now names both ways to unlock hoppers. Journal resource gates are preserved.

## Interaction and storage

Each icon shows learned ranks, all rank effects, the next cost, level gate and connected prerequisite names. Learned ranks glow gold, available choices are green, and locked choices are gray. Connector panes show which purchased paths meet rank requirements. Confirmation explains that the choice lasts for the season. Next/Previous, Back and Close use distinct footer slots. Island confirmations return to the same page.

Every purchase is validated again by the service, including duplicate ranks, prerequisites and the correct point budget. Island purchases retain owner checks and expected-rank checks against stale confirmations. A shared `TalentRules` implementation serves menus, YAML storage and JDBC storage. Personal choices are stored in the existing seasonal talents set/table; no SQL schema migration is needed. Stable profession and island IDs preserve existing purchases. Island prerequisite changes only relax old paths, so existing valid builds remain valid.

The existing seasonal policy is retained: Mastery respec does not refund talent points. At profession level 50, extra XP no longer helps that skill; the menu warns about this before spending on XP talents.

## Tuning position

The initial tuning preserves profession and island point totals and existing rewards, while adding a separate, modest Adventurer budget. Branching makes island capstones cheaper to reach than the former linear chains, so players can specialize earlier without receiving extra island points. These are implementation choices, not measured human balance conclusions. Compare popular build paths, capstone timing and combat/work reward rates in the next earned-progression cohort before changing point budgets. Free or paid talent respec is a separate design decision; this change follows the existing seasonal policy.

See `verification/SKILL_TREES_20261024.md` for build, native client and persistence evidence, including limitations. No production deployment is included.

# Progression expansion — first gameplay pass

This is an additive gameplay update, not a world or player reset. Existing talent IDs, learned ranks, gear IDs, affixes, sockets and enchantments are preserved.

## Player and island trees

Each of the nine player skills now has four paths and twelve talents. Eight points are earned at levels 5, 10, 18, 27, 37, 40, 47 and 50. Existing training automatically qualifies for the added points; nothing needs to be reclaimed.

The new paths improve expedition geode odds (Mining), equipment preservation (Forestry, Smithing and Building), inherited livestock quality (Farming), fishing luck (Fishing), PvE life steal (Hunting), discovery odds (Excavation) and weapon-poison potency (Alchemy). These effects have gameplay consumers, not just descriptive lore. Equipment preservation is capped at 85%.

Island talent points are earned every three levels, for sixteen at level 50. Living Waters adds 5% pond luck per rank. Prize Livestock requires Living Waters rank two and adds 4% inherited-trait improvement chance per rank. Existing generator, crop and automation choices remain intact.

## Earned collections

Each collection contains a helm, cuirass, greaves, boots, blade and fishing rod. Set bonuses activate at two, four and five distinct equipped pieces. The rod can replace the blade to retain the five-piece bonus while fishing. Use `/recipes` for ingredients and requirements.

| Collection | Rarity | Craft skill level | Four-piece specialty |
| --- | --- | ---: | --- |
| Wayfarer | Uncommon | 5 | +8% harvest yield chance |
| Deepcurrent | Rare | 15 | +12% fishing luck |
| Stormbound | Epic | 25 | +10% damage |
| Dawnguard | Legendary | 35 | +20% equipment preservation |
| Astral Covenant | Mythic | 45 | +3% PvE life steal |

Armor/blades require Smithing; rods require Fishing. High tiers use earned materials including echo shards, netherite and nether stars. This pass adds crafting routes, not a full rarity-based mob-loot rebalance. Mythic is also available in the item editor.

Identified earned equipment now accepts the existing Tempered → Refined → Masterwork → Peerless upgrades at the forge. Starter items remain excluded. Workmanship raises base equipment stats, including projectile damage and excavation power; it does not multiply socket or enchantment bonuses. Improvements preserve the same item's other data. Normal Smithing/material costs and confirmation safeguards remain.

Content catalogs migrate by adding missing new IDs, with a pre-migration backup. Operator-edited IDs are not overwritten. The GUI pass shortens forge/alchemy guidance and widens the skill tree into four aligned paths; it is not a complete replacement of all station interfaces.

## Livestock

Eligible Survival breeding on a co-op's own island produces inheritable Vitality, Growth and Fertility values from 0–10. Applies to cows/mooshrooms, sheep, pigs, chickens and rabbits, not custom mobs, stacked monsters or horses. Existing livestock retains ordinary traits. Sneak-interact to inspect an animal.

Offspring inherits each trait from one parent, with a base 25% chance to improve it by one; player and island breeding talents raise that chance. Traits never exceed ten. Maximum Vitality adds 50% health; Growth reduces baby time from 20 to 15 minutes; Fertility reduces the parent's breeding cooldown from five to four minutes. Traits persist in entity data. Horse-native inheritance is deliberately excluded to avoid compounding attribute multipliers.

## Follow-up scope from this first pass

The subsequent staff/fishing/station update implements the panel, internal staff groups, co-op fishery progression and station review flows. See [STAFF_FISHING_STATIONS.md](STAFF_FISHING_STATIONS.md) for current operation. The list below records what was pending when this first gameplay pass shipped.

- A larger fishing progression/content loop beyond pond and equipment integration.
- Deeper station GUI redesign and visual client acceptance.
- A secure Watchdog panel on port 9029, with the requested account bootstrapped using a password hash. No panel is enabled and no password has been written to source or logs in this pass.
- The internal permission/group engine, staff tiers, inheritance, explicit denies, audit history and privilege-escalation tests. Existing permissions remain unchanged; this pass does not replace LuckPerms.

Before public release: playtest gear acquisition costs, set balance, real-player breeding/territory protection, inventory/save interruption, and menus using authenticated clients. Automated checks do not certify the full server for release.

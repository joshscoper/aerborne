# The 100-item equipment expansion

All 100 additions use Minecraft's normal item textures. There are no decorative filler items, new currencies, or resource-pack requirements. Existing item IDs and player equipment are unchanged.

The catalog adds 20 Uncommon, 25 Rare, 25 Epic, 20 Legendary, and 10 Mythic items. Thirty are crafted; seventy are found through the expansion loot pool. All ten named Mythics are drop-only. Crafting gates run from skill level 5 to 25; drop equipment reaches item level 50. Item level is a content tier, not an equip requirement.

Eight six-piece sets contain four armor pieces, a working main-hand item, and an off-hand shield. Their 2/4/5-piece bonuses stack. A five-piece threshold leaves room to swap one piece for a named item. Shields must be held in the off hand, and tools in the main hand, to contribute their stats and set membership.

## Set paths

| Set | Tier | Role | 2 pieces | 4 pieces | 5 pieces |
|---|---|---|---|---|---|
| Copperfield | uncommon | Farming | gathering-power +1 | harvest-extra-percent +8 | job-marks-percent +5 |
| Quarryward | uncommon | Mining | mining-power +1 | durability-save-percent +10 | job-marks-percent +5 |
| Brinewatch | rare | Fishing | fishing-power +1 | fishing-luck-percent +8 | treasure-find-percent +6 |
| Briarpath | rare | Forestry | gathering-power +1 | harvest-extra-percent +10 | skill-xp-percent +5 |
| Dustward | epic | Excavation | excavation-power +1 | excavation-find-percent +5 | geode-find-percent +12 |
| Nightglass | epic | Ranged combat | projectile-damage-percent +5 | execute-damage-percent +8 | job-marks-percent +7 |
| Ashen Oath | legendary | Melee combat | defense +4 | damage-percent +8 | execute-damage-percent +10 |
| Starwarden | legendary | Defense | defense +5 | durability-save-percent +15 | life-steal-percent +1 |

Power values increase matching gathering XP through the existing skill system; they do not change vanilla block-breaking speed. Harvest yield applies to eligible farming and forestry work. Geode find applies to eligible Mining and Excavation work. Fixed quest and collection grants are not activity XP. See the bonus audit for existing caps and eligibility checks.

## Complete catalog

Crafted items have a corresponding `skyforge_<item_id>` recipe in `content/skyforge-recipes.yml`. Drop items have no crafting route in this catalog. Signature identifiers name actual runtime effects, not ordinary rerollable numeric affixes; see [the implementation notes](ITEM_EXPANSION_IMPLEMENTATION.md) for their trigger, cooldown, and limits.

| Item ID | Name | Rarity | Role | Signature | Acquisition | Set |
|---|---|---|---|---|---|---|
| `copperfield_helm` | Copperfield Helm | uncommon | Farming | — | craft | copperfield |
| `copperfield_coat` | Copperfield Coat | uncommon | Farming | — | craft | copperfield |
| `copperfield_greaves` | Copperfield Greaves | uncommon | Farming | — | craft | copperfield |
| `copperfield_boots` | Copperfield Boots | uncommon | Farming | — | craft | copperfield |
| `copperfield_hoe` | Copperfield Hoe | uncommon | Farming | — | craft | copperfield |
| `copperfield_buckler` | Copperfield Buckler | uncommon | Farming | — | craft | copperfield |
| `quarryward_helm` | Quarryward Helm | uncommon | Mining | — | craft | quarryward |
| `quarryward_coat` | Quarryward Coat | uncommon | Mining | — | craft | quarryward |
| `quarryward_greaves` | Quarryward Greaves | uncommon | Mining | — | craft | quarryward |
| `quarryward_boots` | Quarryward Boots | uncommon | Mining | — | craft | quarryward |
| `quarryward_pickaxe` | Quarryward Pickaxe | uncommon | Mining | — | craft | quarryward |
| `quarryward_buckler` | Quarryward Buckler | uncommon | Mining | — | craft | quarryward |
| `brinewatch_helm` | Brinewatch Helm | rare | Fishing | — | craft | brinewatch |
| `brinewatch_coat` | Brinewatch Coat | rare | Fishing | — | craft | brinewatch |
| `brinewatch_greaves` | Brinewatch Greaves | rare | Fishing | — | craft | brinewatch |
| `brinewatch_boots` | Brinewatch Boots | rare | Fishing | — | craft | brinewatch |
| `brinewatch_rod` | Brinewatch Rod | rare | Fishing | — | craft | brinewatch |
| `brinewatch_buckler` | Brinewatch Buckler | rare | Fishing | — | craft | brinewatch |
| `briarpath_helm` | Briarpath Helm | rare | Forestry | — | craft | briarpath |
| `briarpath_coat` | Briarpath Coat | rare | Forestry | — | craft | briarpath |
| `briarpath_greaves` | Briarpath Greaves | rare | Forestry | — | craft | briarpath |
| `briarpath_boots` | Briarpath Boots | rare | Forestry | — | craft | briarpath |
| `briarpath_hatchet` | Briarpath Hatchet | rare | Forestry | — | craft | briarpath |
| `briarpath_buckler` | Briarpath Buckler | rare | Forestry | — | craft | briarpath |
| `dustward_helm` | Dustward Helm | epic | Excavation | — | drop | dustward |
| `dustward_coat` | Dustward Coat | epic | Excavation | — | drop | dustward |
| `dustward_greaves` | Dustward Greaves | epic | Excavation | — | drop | dustward |
| `dustward_boots` | Dustward Boots | epic | Excavation | — | drop | dustward |
| `dustward_shovel` | Dustward Shovel | epic | Excavation | — | drop | dustward |
| `dustward_buckler` | Dustward Buckler | epic | Excavation | — | drop | dustward |
| `nightglass_helm` | Nightglass Helm | epic | Ranged combat | — | drop | nightglass |
| `nightglass_coat` | Nightglass Coat | epic | Ranged combat | — | drop | nightglass |
| `nightglass_greaves` | Nightglass Greaves | epic | Ranged combat | — | drop | nightglass |
| `nightglass_boots` | Nightglass Boots | epic | Ranged combat | — | drop | nightglass |
| `nightglass_bow` | Nightglass Bow | epic | Ranged combat | — | drop | nightglass |
| `nightglass_buckler` | Nightglass Buckler | epic | Ranged combat | — | drop | nightglass |
| `ashen_oath_helm` | Ashen Oath Helm | legendary | Melee combat | — | drop | ashen_oath |
| `ashen_oath_coat` | Ashen Oath Coat | legendary | Melee combat | — | drop | ashen_oath |
| `ashen_oath_greaves` | Ashen Oath Greaves | legendary | Melee combat | — | drop | ashen_oath |
| `ashen_oath_boots` | Ashen Oath Boots | legendary | Melee combat | — | drop | ashen_oath |
| `ashen_oath_blade` | Ashen Oath Blade | legendary | Melee combat | — | drop | ashen_oath |
| `ashen_oath_buckler` | Ashen Oath Buckler | legendary | Melee combat | — | drop | ashen_oath |
| `starwarden_helm` | Starwarden Helm | legendary | Defense | — | drop | starwarden |
| `starwarden_coat` | Starwarden Coat | legendary | Defense | — | drop | starwarden |
| `starwarden_greaves` | Starwarden Greaves | legendary | Defense | — | drop | starwarden |
| `starwarden_boots` | Starwarden Boots | legendary | Defense | — | drop | starwarden |
| `starwarden_mace` | Starwarden Mace | legendary | Defense | — | drop | starwarden |
| `starwarden_buckler` | Starwarden Buckler | legendary | Defense | — | drop | starwarden |
| `cobblebreak_pickaxe` | Cobblebreak Pickaxe | uncommon | mining | — | craft | — |
| `rootcut_hatchet` | Rootcut Hatchet | uncommon | forestry | — | craft | — |
| `furrowkeeper_hoe` | Furrowkeeper Hoe | uncommon | farming | — | craft | — |
| `river_reed_rod` | River Reed Rod | uncommon | fishing | — | craft | — |
| `surveyors_shovel` | Surveyor's Shovel | uncommon | excavation | — | craft | — |
| `watchmans_sword` | Watchman's Sword | uncommon | smithing | — | craft | — |
| `forgehand_boots` | Forgehand Boots | uncommon | Durability | — | drop | — |
| `coppersight_crossbow` | Coppersight Crossbow | uncommon | Ranged combat | — | drop | — |
| `prospectors_pickaxe` | Prospector's Pickaxe | rare | Mining | — | drop | — |
| `orchard_hoe` | Orchard Hoe | rare | Farming | — | drop | — |
| `resinbite_axe` | Resinbite Axe | rare | Forestry | — | drop | — |
| `siltwalker_shovel` | Siltwalker Shovel | rare | Excavation | — | drop | — |
| `raincall_rod` | Raincall Rod | rare | Fishing | — | drop | — |
| `marshguard_trident` | Marshguard Trident | rare | Hybrid combat | — | drop | — |
| `rangers_crossbow` | Ranger's Crossbow | rare | Ranged combat | — | drop | — |
| `quarry_maul` | Quarry Maul | rare | Melee combat | — | drop | — |
| `venomthorn_sword` | Venomthorn Sword | rare | Poisons | — | drop | — |
| `counterweight_shield` | Counterweight Shield | rare | Defense | — | drop | — |
| `miners_lantern_helm` | Miner's Lantern Helm | rare | Mining | — | drop | — |
| `tanners_coat` | Tanner's Coat | rare | Hunting work | — | drop | — |
| `mossstep_boots` | Mossstep Boots | rare | Gathering | — | drop | — |
| `thunderhead_mace` | Thunderhead Mace | epic | Melee combat | — | drop | — |
| `glacial_trident` | Glacial Trident | epic | Hybrid combat | — | drop | — |
| `starling_bow` | Starling Bow | epic | Ranged combat | — | drop | — |
| `siegebreaker_crossbow` | Siegebreaker Crossbow | epic | Ranged combat | — | drop | — |
| `obsidian_edge` | Obsidian Edge | epic | Melee combat | — | drop | — |
| `saltwind_cutlass` | Saltwind Cutlass | epic | Sustain | — | drop | — |
| `ambervein_pickaxe` | Ambervein Pickaxe | epic | Mining | — | drop | — |
| `ironbark_axe` | Ironbark Axe | epic | Forestry | — | drop | — |
| `moonfurrow_hoe` | Moonfurrow Hoe | epic | Farming | — | drop | — |
| `relicseekers_shovel` | Relicseeker's Shovel | epic | Excavation | — | drop | — |
| `pearlwake_rod` | Pearlwake Rod | epic | Fishing | — | drop | — |
| `cryptward_shield` | Cryptward Shield | epic | Defense | — | drop | — |
| `runebinders_greaves` | Runebinder's Greaves | epic | Learning | — | drop | — |
| `kingsfall_blade` | Kingsfall Blade | legendary | Melee combat | — | drop | — |
| `voidglass_crossbow` | Voidglass Crossbow | legendary | Ranged combat | — | drop | — |
| `volcanic_maul` | Volcanic Maul | legendary | Melee combat | — | drop | — |
| `deepsong_pickaxe` | Deepsong Pickaxe | legendary | Mining | — | drop | — |
| `worldroot_hatchet` | Worldroot Hatchet | legendary | Forestry | — | drop | — |
| `sunsown_hoe` | Sunsown Hoe | legendary | Farming | — | drop | — |
| `tombward_shovel` | Tombward Shovel | legendary | Excavation | — | drop | — |
| `maelstrom_rod` | Maelstrom Rod | legendary | Fishing | — | drop | — |
| `sunforge_pickaxe` | Sunforge Pickaxe | mythic | Mining & Smithing | `furnace_heart` | drop | — |
| `faultbreaker` | Faultbreaker | mythic | Excavation & geodes | `deep_resonance` | drop | — |
| `harvest_moon_hoe` | Harvest Moon | mythic | Farming | `harvest_moon` | drop | — |
| `tideweaver_rod` | Tideweaver | mythic | Fishing | `tideweaver` | drop | — |
| `second_dawn_plate` | Second Dawn | mythic | Defense | `second_dawn` | drop | — |
| `mercy_of_the_hunt` | Mercy of the Hunt | mythic | Hunting & sustain | `hunters_mercy` | drop | — |
| `astral_insight_cowl` | Astral Insight | mythic | Learning | `astral_insight` | drop | — |
| `everlasting_hatchet` | Everlasting | mythic | Forestry & durability | `everlasting` | drop | — |
| `venom_heart_spear` | Venom Heart | mythic | Poisons | `venom_heart` | drop | — |
| `stormstring` | Stormstring | mythic | Ranged combat | `stormstring` | drop | — |

## Authoring and balance

Files: `content/skyforge-items.yml` (`schema-version: 1`, `sets` and `items`) and `content/skyforge-recipes.yml` (`schema-version: 1`, `recipes`). IDs are permanent once items enter player inventories. All stats use supported runtime consumers; percentages are percentage points. Numeric damage stays close to the existing 6/7/8/9/10 tier curve rather than replacing earlier equipment with an inflated ladder. Most new power is role specialization, set choice, or one named Mythic ability.

Names and lore intentionally avoid stat promises: the shared tooltip renders current values, sockets, upgrades, and signature details. Recipe ingredients are ordinary Minecraft materials; named equipment, gems, and other protected items are not substitutes. Full set recipes use farming, mining, fishing, or Forestry progression, with distinct material patterns and mirrored-pattern collision tests.

This catalog is additive. It does not wipe inventories, convert old equipment, or change existing recipe IDs. Operators can tune the expansion loot pool separately from the item definitions. A new item must have an acquisition route before release; adding a definition alone does not create a drop.


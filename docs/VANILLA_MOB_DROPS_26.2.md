# Vanilla mob drop review — Paper 26.2

Generated from the loot tables bundled with the pinned `paper-26.2.jar`. Regenerate with `scripts/export-vanilla-mob-drops.ps1` after every Paper upgrade.

- Source SHA-256: `4DCEFACECD1C67B4C277AE1F290C7273CC31DB590F72293A6880526BEEEBC05A`
- Top-level entity tables: 93
- Total entity loot assets, including variants/sub-tables: 109
- Player-killed hostiles roll sealed gear at 0.8% standard / 10% elite / two guaranteed boss rolls, before distance reward scaling.
- Coin purses roll at 20% for standard hostiles and are guaranteed for elites and bosses; pickup credits Marks through an idempotent ledger transaction.
- Vanilla loot remains enabled except for the narrow junk trims named in `vanilla-adjustments`; family groups are additive rolls from `content/drop-tables.yml`.
- “Declared drops” lists direct item/tag entries. The rules column flags conditions, count functions, smelting, Looting, and referenced tables that must be reviewed before changing exact odds.
- The sheet intentionally includes technical/entity tables such as Armor Stand, Giant, Illusioner, Mannequin, Player, and Villager so the audit has no silent gaps.

| # | Mob | Entity ID | Vanilla declared drops | Vanilla rules / references | Non-table rewards / notes | NerdSMP bonus groups | Hostile rewards | Decision |
|---:|---|---|---|---|---|---|---|---|
| 1 | Allay | `allay` | — | — | — | — | — | Keep |
| 2 | Armadillo | `armadillo` | — | — | — | — | — | Keep |
| 3 | Armor Stand | `armor_stand` | — | — | — | — | — | Keep |
| 4 | Axolotl | `axolotl` | — | — | — | — | — | Keep |
| 5 | Bat | `bat` | — | — | — | — | — | Keep |
| 6 | Bee | `bee` | — | — | — | — | — | Keep |
| 7 | Blaze | `blaze` | blaze_rod | conditions: killed_by_player; functions: enchanted_count_increase, set_count | — | blaze | sealed gear + coin purse | Add bonus |
| 8 | Bogged | `bogged` | arrow, bone, tipped_arrow | conditions: killed_by_player; functions: enchanted_count_increase, set_count, set_potion | — | skeletons | sealed gear + coin purse | Add bonus |
| 9 | Breeze | `breeze` | breeze_rod | conditions: killed_by_player; functions: enchanted_count_increase, set_count | — | trial-chambers | sealed gear + coin purse | Add bonus |
| 10 | Camel Husk | `camel_husk` | rotten_flesh | functions: enchanted_count_increase, set_count | — | common-undead | sealed gear + coin purse | Tune vanilla + add bonus |
| 11 | Camel | `camel` | — | — | — | — | — | Keep |
| 12 | Cat | `cat` | string | functions: set_count | — | — | — | Keep |
| 13 | Cave Spider | `cave_spider` | spider_eye, string | conditions: killed_by_player; functions: enchanted_count_increase, set_count | — | spiders | sealed gear + coin purse | Add bonus |
| 14 | Chicken | `chicken` | chicken, feather | conditions: any_of, entity_properties; functions: enchanted_count_increase, furnace_smelt, set_count | — | — | — | Keep |
| 15 | Cod | `cod` | bone_meal, cod | conditions: any_of, entity_properties, random_chance; functions: furnace_smelt | — | — | — | Keep |
| 16 | Copper Golem | `copper_golem` | copper_ingot | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 17 | Cow | `cow` | beef, leather | conditions: any_of, entity_properties; functions: enchanted_count_increase, furnace_smelt, set_count | — | — | — | Keep |
| 18 | Creaking | `creaking` | — | — | — | occult | sealed gear + coin purse | Add bonus |
| 19 | Creeper | `creeper` | #creeper_drop_music_discs, gunpowder | conditions: entity_properties; functions: enchanted_count_increase, set_count | — | volatile | sealed gear + coin purse | Add bonus |
| 20 | Dolphin | `dolphin` | cod | conditions: any_of, entity_properties; functions: enchanted_count_increase, furnace_smelt, set_count | — | — | — | Keep |
| 21 | Donkey | `donkey` | leather | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 22 | Drowned | `drowned` | copper_ingot, rotten_flesh | conditions: killed_by_player, random_chance_with_enchanted_bonus; functions: enchanted_count_increase, set_count | — | common-undead, ocean-hunters | sealed gear + coin purse | Tune vanilla + add bonus |
| 23 | Elder Guardian | `elder_guardian` | cod, prismarine_crystals, prismarine_shard, tide_armor_trim_smithing_template, wet_sponge | conditions: any_of, entity_properties, killed_by_player, random_chance_with_enchanted_bonus; functions: enchanted_count_increase, furnace_smelt, set_count; references: gameplay/fishing/fish | — | elder-reward, ocean-hunters | sealed gear + coin purse | Add bonus |
| 24 | Ender Dragon | `ender_dragon` | — | — | 12,000 XP on first defeat and 500 XP thereafter; dragon egg/gateway progression is outside the entity loot table. | dragon-reward | sealed gear + coin purse | Add bonus |
| 25 | Enderman | `enderman` | ender_pearl | functions: enchanted_count_increase, set_count | — | ender | sealed gear + coin purse | Add bonus |
| 26 | Endermite | `endermite` | — | — | — | burrowers | sealed gear + coin purse | Add bonus |
| 27 | Evoker | `evoker` | emerald, totem_of_undying | conditions: killed_by_player; functions: enchanted_count_increase, set_count | — | illagers | sealed gear + coin purse | Add bonus |
| 28 | Fox | `fox` | — | — | — | — | — | Keep |
| 29 | Frog | `frog` | — | — | — | — | — | Keep |
| 30 | Ghast | `ghast` | ghast_tear, gunpowder, music_disc_tears | conditions: damage_source_properties, killed_by_player; functions: enchanted_count_increase, set_count | — | nether-casters | sealed gear + coin purse | Add bonus |
| 31 | Giant | `giant` | — | — | — | — | sealed gear + coin purse | Add bonus |
| 32 | Glow Squid | `glow_squid` | glow_ink_sac | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 33 | Goat | `goat` | — | — | — | — | — | Keep |
| 34 | Guardian | `guardian` | cod, prismarine_crystals, prismarine_shard | conditions: any_of, entity_properties, killed_by_player, random_chance_with_enchanted_bonus; functions: enchanted_count_increase, furnace_smelt, set_count; references: gameplay/fishing/fish | — | ocean-hunters | sealed gear + coin purse | Add bonus |
| 35 | Happy Ghast | `happy_ghast` | — | — | — | — | — | Keep |
| 36 | Hoglin | `hoglin` | leather, porkchop | conditions: any_of, entity_properties; functions: enchanted_count_increase, furnace_smelt, set_count | — | nether-raiders | sealed gear + coin purse | Add bonus |
| 37 | Horse | `horse` | leather | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 38 | Husk | `husk` | carrot, iron_ingot, potato, rabbit_foot, rotten_flesh | conditions: any_of, entity_properties, killed_by_player, random_chance_with_enchanted_bonus; functions: enchanted_count_increase, furnace_smelt, set_count | — | common-undead | sealed gear + coin purse | Tune vanilla + add bonus |
| 39 | Illusioner | `illusioner` | — | — | — | illagers | sealed gear + coin purse | Add bonus |
| 40 | Iron Golem | `iron_golem` | iron_ingot, poppy | functions: set_count | — | — | — | Keep |
| 41 | Llama | `llama` | leather | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 42 | Magma Cube | `magma_cube` | magma_cream, ochre_froglight, pearlescent_froglight, verdant_froglight | conditions: damage_source_properties, entity_properties, inverted; functions: enchanted_count_increase, set_count | — | magma-family | sealed gear + coin purse | Add bonus |
| 43 | Mannequin | `mannequin` | — | — | — | — | — | Keep |
| 44 | Mooshroom | `mooshroom` | beef, leather | conditions: any_of, entity_properties; functions: enchanted_count_increase, furnace_smelt, set_count | — | — | — | Keep |
| 45 | Mule | `mule` | leather | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 46 | Nautilus | `nautilus` | nautilus_shell | conditions: killed_by_player, random_chance_with_enchanted_bonus | — | — | — | Keep |
| 47 | Ocelot | `ocelot` | — | — | — | — | — | Keep |
| 48 | Panda | `panda` | bamboo | functions: set_count | — | — | — | Keep |
| 49 | Parched | `parched` | arrow, bone, tipped_arrow | conditions: killed_by_player; functions: enchanted_count_increase, set_count, set_potion | — | skeletons | sealed gear + coin purse | Add bonus |
| 50 | Parrot | `parrot` | feather | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 51 | Phantom | `phantom` | phantom_membrane | conditions: killed_by_player; functions: enchanted_count_increase, set_count | — | phantoms | sealed gear + coin purse | Add bonus |
| 52 | Pig | `pig` | porkchop | conditions: any_of, entity_properties; functions: enchanted_count_increase, furnace_smelt, set_count | — | — | — | Keep |
| 53 | Piglin Brute | `piglin_brute` | — | — | — | nether-raiders | sealed gear + coin purse | Add bonus |
| 54 | Piglin | `piglin` | — | — | — | nether-raiders | sealed gear + coin purse | Add bonus |
| 55 | Pillager | `pillager` | ominous_bottle | conditions: entity_properties; functions: set_count, set_ominous_bottle_amplifier | — | illagers | sealed gear + coin purse | Add bonus |
| 56 | Player | `player` | — | — | — | — | — | Keep |
| 57 | Polar Bear | `polar_bear` | cod, salmon | conditions: any_of, entity_properties; functions: enchanted_count_increase, furnace_smelt, set_count | — | — | — | Keep |
| 58 | Pufferfish | `pufferfish` | bone_meal, pufferfish | conditions: random_chance; functions: set_count | — | — | — | Keep |
| 59 | Rabbit | `rabbit` | rabbit, rabbit_foot, rabbit_hide | conditions: any_of, entity_properties, killed_by_player, random_chance_with_enchanted_bonus; functions: enchanted_count_increase, furnace_smelt, set_count | — | — | — | Keep |
| 60 | Ravager | `ravager` | saddle | functions: set_count | — | illagers | sealed gear + coin purse | Add bonus |
| 61 | Salmon | `salmon` | bone_meal, salmon | conditions: any_of, entity_properties, random_chance; functions: furnace_smelt | — | — | — | Keep |
| 62 | Sheep | `sheep` | mutton | conditions: any_of, entity_properties; functions: enchanted_count_increase, furnace_smelt, set_count; references: entities/sheep/black, entities/sheep/blue, entities/sheep/brown, entities/sheep/cyan, entities/sheep/gray, entities/sheep/green, entities/sheep/light_blue, entities/sheep/light_gray, entities/sheep/lime, entities/sheep/magenta, entities/sheep/orange, entities/sheep/pink, entities/sheep/purple, entities/sheep/red, entities/sheep/white, entities/sheep/yellow | — | — | — | Keep |
| 63 | Shulker | `shulker` | shulker_shell | conditions: random_chance_with_enchanted_bonus | — | ender | sealed gear + coin purse | Add bonus |
| 64 | Silverfish | `silverfish` | — | — | — | burrowers | sealed gear + coin purse | Add bonus |
| 65 | Skeleton Horse | `skeleton_horse` | bone | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 66 | Skeleton | `skeleton` | arrow, bone | functions: enchanted_count_increase, set_count | — | skeletons | sealed gear + coin purse | Add bonus |
| 67 | Slime | `slime` | slime_ball | conditions: damage_source_properties, entity_properties, inverted; functions: enchanted_count_increase, set_count | — | slime-family | sealed gear + coin purse | Add bonus |
| 68 | Sniffer | `sniffer` | — | — | — | — | — | Keep |
| 69 | Snow Golem | `snow_golem` | snowball | functions: set_count | — | — | — | Keep |
| 70 | Spider | `spider` | spider_eye, string | conditions: killed_by_player; functions: enchanted_count_increase, set_count | — | spiders | sealed gear + coin purse | Add bonus |
| 71 | Squid | `squid` | ink_sac | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 72 | Stray | `stray` | arrow, bone, tipped_arrow | conditions: killed_by_player; functions: enchanted_count_increase, set_count, set_potion | — | skeletons | sealed gear + coin purse | Add bonus |
| 73 | Strider | `strider` | string | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 74 | Sulfur Cube | `sulfur_cube` | — | — | — | magma-family | sealed gear + coin purse | Add bonus |
| 75 | Tadpole | `tadpole` | — | — | — | — | — | Keep |
| 76 | Trader Llama | `trader_llama` | leather | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 77 | Tropical Fish | `tropical_fish` | bone_meal, tropical_fish | conditions: random_chance; functions: set_count | — | — | — | Keep |
| 78 | Turtle | `turtle` | bowl, seagrass | conditions: damage_source_properties; functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 79 | Vex | `vex` | — | — | — | occult | sealed gear + coin purse | Add bonus |
| 80 | Villager | `villager` | — | — | — | — | — | Keep |
| 81 | Vindicator | `vindicator` | emerald | conditions: killed_by_player; functions: enchanted_count_increase, set_count | — | illagers | sealed gear + coin purse | Add bonus |
| 82 | Wandering Trader | `wandering_trader` | — | — | — | — | — | Keep |
| 83 | Warden | `warden` | sculk_catalyst | — | — | warden-reward | sealed gear + coin purse | Add bonus |
| 84 | Witch | `witch` | glass_bottle, glowstone_dust, gunpowder, redstone, spider_eye, stick, sugar | functions: enchanted_count_increase, set_count | — | occult | sealed gear + coin purse | Tune vanilla + add bonus |
| 85 | Wither Skeleton | `wither_skeleton` | bone, coal, wither_skeleton_skull | conditions: killed_by_player, random_chance_with_enchanted_bonus; functions: enchanted_count_increase, set_count | — | withered | sealed gear + coin purse | Add bonus |
| 86 | Wither | `wither` | — | — | 1 nether star and 50 XP are awarded by entity code outside the JSON loot table. | wither-reward | sealed gear + coin purse | Add bonus |
| 87 | Wolf | `wolf` | — | — | — | — | — | Keep |
| 88 | Zoglin | `zoglin` | rotten_flesh | functions: enchanted_count_increase, set_count | — | common-undead | sealed gear + coin purse | Tune vanilla + add bonus |
| 89 | Zombie Horse | `zombie_horse` | rotten_flesh | functions: enchanted_count_increase, set_count | — | — | — | Keep |
| 90 | Zombie Nautilus | `zombie_nautilus` | rotten_flesh | conditions: killed_by_player; functions: enchanted_count_increase, set_count | — | common-undead | sealed gear + coin purse | Tune vanilla + add bonus |
| 91 | Zombie Villager | `zombie_villager` | carrot, iron_ingot, potato, rotten_flesh | conditions: any_of, entity_properties, killed_by_player, random_chance_with_enchanted_bonus; functions: enchanted_count_increase, furnace_smelt, set_count | — | common-undead | sealed gear + coin purse | Tune vanilla + add bonus |
| 92 | Zombie | `zombie` | carrot, iron_ingot, music_disc_lava_chicken, potato, red_mushroom, rotten_flesh | conditions: any_of, entity_properties, killed_by_player, random_chance_with_enchanted_bonus; functions: enchanted_count_increase, furnace_smelt, set_count | — | common-undead | sealed gear + coin purse | Tune vanilla + add bonus |
| 93 | Zombified Piglin | `zombified_piglin` | gold_ingot, gold_nugget, rotten_flesh | conditions: killed_by_player, random_chance_with_enchanted_bonus; functions: enchanted_count_increase, set_count | — | nether-raiders | sealed gear + coin purse | Tune vanilla + add bonus |

## Review decisions

The Decision column reflects the implemented policy. Exact chances and amounts live in `nerdsmp-paper/src/main/resources/content/drop-tables.yml`; rerun this export after Paper or loot-policy changes.

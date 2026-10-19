# Island activities

## See your boundary

Stand on an island and use `/island border` to toggle a private aqua outline. `/island border on` and `/island border off` are also available, along with a button in `/island`.

The outline follows your building height and shows the exact protected chunk edges. Walk within 40 blocks of an edge to see it. It is only a particle display: no barrier blocks, collision wall, world-border changes or terrain edits. Your outline updates after a land expansion and turns off when you leave the island, lose entry access or log out. Reduced client particle settings may make it harder to see.

Rendering visits at most 16 viewers each ten ticks and emits at most 64 nearby points per viewer. There are no display entities, chunk tickets or terrain reads; unloaded chunks are skipped.

## Craft more spawners

Open `/island machines` and select **Spawner recipes**. Use **Discover recipes** to add them to the crafting book. Every species uses this grid:

```text
I F I
F D F
I F I
```

`I` is an Iron Block, `D` is a Diamond, and `F` is the species ingredient below. Use ordinary, unmodified ingredients. Recipes can be crafted anywhere; new species require their island resource stage before placement or adding to a block stack. Existing spawners and the original four recipes remain available.

| Spawner | Ingredient | Island unlock |
| --- | --- | --- |
| Zombie | Rotten Flesh | Original recipe |
| Skeleton | Bone | Original recipe |
| Spider | String | Original recipe |
| Blaze | Blaze Rod | Original recipe |
| Creeper | Gunpowder | Homestead |
| Drowned | Copper Ingot | Homestead |
| Husk | Sand | Homestead |
| Stray | Ice | Workshop |
| Cave Spider | Fermented Spider Eye | Workshop |
| Enderman | Ender Pearl | Sanctuary |
| Zombified Piglin | Gold Nugget | Sanctuary |
| Witch | Glowstone Dust | Sanctuary |
| Breeze | Breeze Rod | Foundry |

Existing Compact Spawners talents still control block-stack limits. Right-click a matching placed spawner while holding another to add one; sneak and break to recover the stack. Each represented mob dies and rolls drops/XP one at a time, rather than multiplying a single loot roll.

Charged/ignited creepers, angry piglins, block-carrying endermen, elites and modified equipment do not merge into ordinary stacks. Enderman stacks cannot teleport into another island. An exploding creeper retires one member without a death-loot reward; remaining members continue as a new body. Slime and Magma Cube spawners are not included because their splitting needs separate duplicate-safe support.

## Build an island pond

Any spot where Minecraft produces a valid catch works. There is no minimum pond size, depth, source-water, or overhead-clearance requirement. Keep the bobber inside the island. Owners and co-op members can fish there; visitors cannot use another island's pond for rewards.

The `/fishing` journal has a **Pond Habitat** selector. Earned resource stages unlock Freshwater, River, Wetland, Ocean and Cold Water. The selected habitat is shared by the co-op, favors matching species and does not alter terrain or biome. Eligible catches credit the bobber's island, not a different island under the player's feet. No fishing encounter mobs spawn on private islands.

See [FISHING.md](FISHING.md) for pond requirements, habitat unlocks, luck, streaks and collections.

## Earn Smithing XP from ore

Smelt ore or raw metal in a normal furnace or blast furnace, then collect the output yourself. The Smithing action bar and existing progression system receive XP for verified results:

| Smelted result | Eligible input | Default Smithing XP per item |
| --- | --- | ---: |
| Iron Ingot | Raw Iron, Iron Ore, Deepslate Iron Ore | 4 |
| Copper Ingot | Raw Copper, Copper Ore, Deepslate Copper Ore | 4 |
| Gold Ingot | Raw Gold, Gold Ore, Deepslate Gold Ore, Nether Gold Ore | 6 |
| Netherite Scrap | Ancient Debris | 12 |

Hoppers may still move furnace items normally, but their collected output does not bank Smithing work. Food, glass, stone, recycled tools/armor/nuggets, modified ingredients and ordinary machine output do not qualify. Pre-update output has no retroactive receipts. Native fuel consumption, smelting recipes and vanilla furnace XP are unchanged.

The furnace stores a bounded, versioned receipt in its own persistent data. Collection retires the receipt before the asynchronous skill award, preventing replay. This favors at-most-once delivery: a failed XP save cannot reuse consumed furnace credit. Normal container permissions apply; denied/unlinked collection earns no skill reward.

Configure the per-item defaults under `progression.xp.smithing-smelt-iron`, `smithing-smelt-copper`, `smithing-smelt-gold` and `smithing-smelt-netherite`.

No world reset, island-layout change, player-data conversion or forced configuration refresh is part of these features.

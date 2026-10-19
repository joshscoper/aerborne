# Island resources and workshop

Open `/island journal` to see your next milestone. `/island resources` and `/island generator` open the same journal; `/island machines` opens the workshop.

Train skills, then click **Share your skill progress**. Co-op members can cover different requirements, and their certifications stay with the island if they leave. Unlocks do not spend XP, Marks, items or talent points. Island levels come from eligible skill activities on your island, not machine production.

## Resource milestones

| Stage | Requirements | New resources and workshop options |
| --- | --- | --- |
| Settler | Your existing starting island | Cobblestone, coal, copper and iron; Crusher |
| Homestead | Mining, Farming and Building 5 | Gold and lapis; Compost Bin; original first land expansion |
| Workshop | Smithing, Forestry and Hunting 10 | Redstone; Sawmill; original second land expansion |
| Sanctuary | Fishing, Excavation and Alchemy 15 | Diamonds; Mineral Sifter; original third land expansion |
| Foundry | Island level 15; Mining 25, Smithing 20, Excavation 20 | Quartz ore; improved generator chances; additional sifter recipes |
| Aetherworks | Island level 25; Mining 35, Alchemy 30, Farming 30 | Emerald ore and occasional obsidian; end-stone sifting |

Already-completed chapters immediately count toward the matching resource stage. The original four generator tables have not changed. Foundry and Aetherworks improve resource production without moving or enlarging the island.

The journal derives its base percentages from the actual generator code. Prospecting and Rich Seams still apply afterward. Resources require the appropriate normal tools to harvest. Netherite is not generated or sold by these machines.

## Machines

All four machines use vanilla dropper textures and normal, persistent container storage. Craft them with this grid:

```text
I I I
C D C
I I I
```

`I` is an iron ingot, `D` is a normal dropper, and `C` is the machine's ingredient: cobblestone for Crusher, wheat for Compost Bin, oak logs for Sawmill, or gravel for Mineral Sifter. The normal dropper recipe requires redstone; obtain it through unlocked generation, player trade or an available hub vendor. The workshop's recipe-book button discovers these crafting recipes, but does not give materials or bypass unlocks.

Place a machine on your co-op island, then right-click it:

- Top row: matching ingredients.
- Middle row: coal or charcoal.
- Bottom row: finished goods, which can be taken out normally.

Drag items into the appropriate rows. Shift-clicking into the machine and hopper transfers are blocked to prevent items entering the wrong row. Shift-clicking items out is supported. Named, enchanted or otherwise modified ingredients and fuel are not consumed. A full output row pauses work without spending input or fuel.

| Machine | Operation |
| --- | --- |
| Crusher | 8 cobblestone + 1 fuel → 8 gravel; 8 gravel + 1 fuel → 8 sand |
| Compost Bin | 8 matching supported crops or 16 matching seeds + 1 fuel → 4 bone meal |
| Sawmill | 4 matching supported logs or stems + 1 fuel → 24 matching planks |
| Mineral Sifter, Sanctuary | 16 gravel + 2 fuel → 4 iron nuggets; 16 sand + 2 fuel → 8 clay balls; 16 mud + 2 fuel → 1 raw copper |
| Mineral Sifter, Foundry | 16 soul sand + 4 fuel → 2 quartz; 16 red sand + 4 fuel → 1 raw gold; 16 netherrack + 4 fuel → 2 redstone |
| Mineral Sifter, Aetherworks | 32 end stone + 8 fuel → 1 diamond |

Machines do not generate their feedstock: farm crops, grow trees, use your generator, trade, or gather materials in staff-authored RPG zones. There is no random output, free currency, automatic selling or machine-generated skill XP.

An island can have 16 processing machines in addition to its existing spawners and chunk hoppers. A machine processes one batch per eligible visit, with a four-second minimum interval (six for sifters). The server visits at most 16 machine records each second, so busy servers can take longer. Unloaded chunks do not process, machines do not keep chunks loaded, and elapsed offline time never produces catch-up batches. Redstone pauses production; pistons, explosions and dropper dispensing cannot move or empty machines.

Empty every slot, make room in your inventory, then sneak and break to recover the machine. Broken/mismatched saved identities are paused and protected for staff review, not automatically deleted or replaced.

## Existing-player safety

No reset, template replacement or terrain regeneration is part of this update. Island IDs, coordinates, names, co-ops, original chapter receipts, skill XP, talents, inventories and balances stay in their existing paths.

New data uses separate, versioned paths inside the owner's existing `player_data/<uuid>.yml`:

- `seasons.<season>.island-resources` for new resource certifications.
- `seasons.<season>.skyblock-machines` for processing-machine placements.

Reading an old save does not write a migration. New writes use the existing atomic document writer and retain unrelated sections. Invalid schemas fail closed rather than reset progress. Machine contents remain in Minecraft's block-container data and require consistent world/player-data backups. A crash between placement/recovery and a world save can leave a protected orphan needing staff reconciliation; the index never automatically grants replacement items.

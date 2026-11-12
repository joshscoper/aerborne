# Gem sockets

Right-click an anvil to open **Forge & Sockets**. Remote `/forge` access keeps its existing permission requirement.

- Put one identified weapon, tool, or armor piece in the left input and a gem in the right input. The preview shows its effects and the next available socket.
- To recover a gem, replace the catalyst with Arcane Dust and select the filled socket you want below the inputs.
- Click the result to review, then click **Confirm Change**. Moving either input, selecting another socket, or waiting 15 seconds requires a fresh review.
- Recovered gems retain their type and grade. Your other gems remain; the packed socket list closes the gap after removal.

## The gemstone ladder

Gems come in five grades. Geodes crack into the bottom two; merging climbs the rest.

| Grade | Tier | Stats | Recovery cost |
| --- | ---: | ---: | ---: |
| Rough | 1 | ×1 | 8 Arcane Dust |
| Flawed | 2 | ×2 | 12 Arcane Dust |
| Fine | 3 | ×3 | 16 Arcane Dust |
| Flawless | 4 | ×4 | 20 Arcane Dust |
| Perfect | 5 | ×5 | 24 Arcane Dust |

To merge, put two gems of the same type and grade in the inputs. One from each input makes one gem of
the next grade, up to Perfect.

| Geode | Rough | Flawed | Fine |
| --- | ---: | ---: | ---: |
| Rough Geode | 85% | 15% | — |
| Resonant Geode | 50% | 50% | — |
| Luminous Geode | 15% | 70% | 15% |

Arcane Research weighs gems by grade: Rough 1.0, Flawed 1.25, Fine 1.5, Flawless and Perfect 2.0.

## Socket types

Each socket takes certain gems. Gear tooltips show the slots as `[C]`, `[D]`, `[G]` and `[U]` under
**Gemstone Slots**, and the forge names the next one.

Materials, ores, ingots, alloys, currencies and consumables have no sockets. Old material tooltips
that inherited the default two slots refresh through the item updater. Existing equipment capacities
and installed gems are preserved. Expedition refining and alloy recipes are described in [Metallurgy](METALLURGY.md).

| Socket | Found on | Takes |
| --- | --- | --- |
| `[C]` Combat | weapons | Ruby, Amethyst |
| `[D]` Defensive | armour and shields | Sapphire, Onyx |
| `[G]` Gathering | gear with any gathering power (tools, rods) | Topaz, Aquamarine, Jade, Citrine |
| `[U]` Universal | the last socket of legendary, mythic and relic gear with two or more | any gem |

Emerald and Moonstone are utility gems and fit any socket. Prismatic gems fit only a Universal socket.
Gems already in a socket stay there; the types apply when a gem is socketed.

## Gems are not ingredients

Gems and geodes are made on an inert base item (a firework star that wears the gem's look). Nothing in
vanilla smelts, brews, burns, trades or places it, and its two crafting recipes refuse NerdSMP items.
Merging is the only way to change a gem. Gems from before this change (on redstone, lapis, amethyst
and so on) are converted by the item updater the first time they're seen; one that is placed or used
on a mob first is converted in the hand instead.

## Arcane Dust and inventory space

Craft four Arcane Dust with one lapis lazuli and one amethyst shard. Recovery and merging require room in your inventory for the output; a full inventory spends nothing and does not drop the result on the ground.

Existing gem types, stat values, socket capacities, and saved item encoding are unchanged. Invalid or over-capacity socket data is rejected rather than silently repaired or stripped. Staff should inspect affected items before replacing their metadata.

Closing the forge returns its inputs. As with the previous forge, inputs that cannot fit when closing are dropped at your feet; close in a safe location with inventory space.

For manual shopkeeper placement, see [NPC catalog](NPC_CATALOG.md).

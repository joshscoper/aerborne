# Gem sockets

Right-click an anvil to open **Forge & Sockets**. Remote `/forge` access keeps its existing permission requirement.

- Put one identified weapon, tool, or armor piece in the left input and a gem in the right input. The preview shows its effects and the next available socket.
- To recover a gem, replace the catalyst with Arcane Dust and select the filled socket you want below the inputs.
- Click the result to review, then click **Confirm Change**. Moving either input, selecting another socket, or waiting 15 seconds requires a fresh review.
- Recovered gems retain their type and tier. Your other gems remain; the packed socket list closes the gap after removal.

| Gem tier | Recovery cost |
| --- | ---: |
| I | 8 Arcane Dust |
| II | 12 Arcane Dust |
| III | 16 Arcane Dust |
| IV | 20 Arcane Dust |

Craft four Arcane Dust with one lapis lazuli and one amethyst shard. Recovery and merging require room in your inventory for the output; a full inventory spends nothing and does not drop the result on the ground.

For merging, put two gems of the same type and tier in the inputs. One from each input produces one gem of the next tier, up to tier IV.

Existing gem types, stat values, socket capacities, and saved item encoding are unchanged. Invalid or over-capacity socket data is rejected rather than silently repaired or stripped. Staff should inspect affected items before replacing their metadata.

Closing the forge returns its inputs. As with the previous forge, inputs that cannot fit when closing are dropped at your feet; close in a safe location with inventory space.

For manual shopkeeper placement, see [NPC catalog](NPC_CATALOG.md).

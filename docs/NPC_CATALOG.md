# Placing NPCs

Use `/npc catalog` to browse the staff NPC library. The categories are derived from the dialogue and shop definitions, so the catalog stays current when you edit `plugins/NerdSMP/content/npcs.yml` and run `/npc reload`.

- `/npc catalog shopkeepers` shows merchants and their shops.
- `/npc catalog services` shows bankers, identifiers, forgekeepers, enchanters, collection keepers, travel guides, and other services.
- `/npc catalog quests` shows faction contract clerks and NPCs offering daily quests, server orders, or campaigns.
- `/npc catalog all <search>` searches names, definition IDs, roles, factions, services, and shop stock. For example, `/npc catalog shopkeepers bread` finds merchants with bread in their inventory.

Stand where the NPC should appear, open the catalog, select a definition, then click **Place here**. The preview shows their name, role, faction, services, stock, number of existing placements, and the placement point. They use your position and facing. **Refresh placement point** updates the preview after you move.

For a definition you already know, use `/npc place <definition>`; `/npc create <definition>` remains an alias. For example, `/npc place mara_venn` places the Warden Quartermaster. These commands have definition tab completion.

Placement requires `nerdsmp.npc.admin`, a loaded player account, a loaded chunk inside the world border, a solid floor, and enough clear space for the NPC's body. Cancelled spawns, blocked locations, the NPC cap, and failed saves do not produce a success message or a new saved anchor. NPCs are placed only when staff explicitly requests it.

Definitions remain in `content/npcs.yml`, while placed anchors remain in `npc-instances.yml`. You can place the same definition at multiple locations. The catalog does not rewrite either file just by browsing it.

Use `/npc nearby` to find nearby instance IDs, `/npc movehere <instance>` to move an existing anchor, and `/npc remove <instance>` to remove one. Reloaded definitions invalidate an older open catalog; open the catalog again to use the new content.

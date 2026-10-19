# NPC Studio

`/npcstudio` gives the content team an in-game NPC and branching-dialogue editor. It uses the existing manually placed NPC runtime and `nerdsmp.npc.admin`; designers can publish directly after reviewing a confirmation. It does not use Citizens, custom player skins, NMS or a resource pack.

## Create a character

1. Open **New NPC**, enter a new lowercase ID, then edit the character's name and title.
2. Choose a supported vanilla body and an existing faction. If the character sells goods, choose an existing shop definition.
3. Open **Dialogue Nodes**. Edit dialogue text, separating up to eight lines with `|`.
4. Add responses, select another local node or a safe terminal service, then set minimum Adventurer level and faction reputation where appropriate.
5. Use **Conversation Preview** to walk through the graph. Preview ignores level/reputation gates, displays their values, and never executes services, grants rewards or spawns a body.
6. Choose **Publish NPC** and confirm. All nodes must be connected to the root. The file is validated and durably saved before the active catalog changes.
7. Place the character with `/npc place <id>`, or use **Placement Catalog**. Publishing and copying do not create or move placement anchors.

Each character has up to 32 local dialogue nodes and each node up to five uniquely labelled responses. Player conversations use private, clickable chat; authored speech and reply labels are literal text, so typed markup is not executed. The editor and its draft preview remain inventory-based. Published body types are fixed; copy the character to a new ID if a different body is needed.

## Player conversations

Right-click a placed NPC, or use `/npc talk <instance>` nearby. Open chat and click a numbered response; `/npc reply <number>` also works. Hover a response to see costs, rewards, cooldowns or why it is unavailable. `/npc bye` or **Leave conversation** ends the conversation without choosing an outcome. Ordinary chat messages are not captured as replies.

Replies expire after two minutes. Leaving the NPC's range or line of sight, dying, logging out, opening a menu, or publishing/reloading dialogue ends the session. Old messages remain in chat history but cannot run again. Requirements are checked again when a response is chosen. Leaving does not cancel a reward/payment operation that has already started.

Shops, banks, collections, faction contracts and crafting stations still open their existing menus. A physical shop or contract menu's back button starts a fresh chat conversation.

## Services and reputation

Response destinations include closing the conversation, a local dialogue branch, shop, bank, identifier, repair, faction contracts, archaeology archive, fishery, alchemy, forge and enchantments. Shops must already exist. Faction contracts use the selected existing faction and its actual quest/reputation systems.

The editor intentionally offers no arbitrary command execution, free Marks or reputation grants, story-flag effects or copied reward loops. Use audited quest/contract content for earned rewards. Copying a built-in NPC imports only safe navigation and service responses; options with grants, commands, story-flag/Marks requirements or one-time state are omitted.

Built-in definitions and vendor inventory/price rules remain read-only here. This editor binds a character to an existing shop; it does not rewrite the economy's shop catalog.

## Safe iteration

Drafts are private, account-bound, read-only UI sessions. A replacement or discarded draft requires confirmation. Browsing retains the draft, but logout and shutdown discard unpublished work. Publishing a changed definition refreshes only its existing manually saved NPC anchors and invalidates older conversation requests; other NPCs and terrain are left alone.

Concurrent edits are compared with the draft's original published definition. Stale publication is rejected rather than overwriting another designer. A failed save retains the earlier live catalog and the draft. Source lives in `plugins/NerdSMP/content/engine-npcs.yml`, separate from the shipped/manual `content/npcs.yml`. Malformed files fail closed and are preserved for repair.

NPC-definition deletion is not exposed in this editor: removing a placed or referenced character needs a placement/quest reference review. Dialogue nodes can be removed once they are neither the root nor referenced by another response.

## Commands

- `/npcstudio [browse]` — open the library.
- `/npcstudio create <id>` — start a greeting template.
- `/npcstudio edit <id>` — edit an authored character.
- `/npcstudio copy <source> <new-id>` — copy an authored or built-in character into a new draft.
- `/npcstudio resume` — continue the draft.
- `/npcstudio cancel` — confirm discarding it.

The existing `/npc catalog`, `/npc place`, `/npc movehere` and `/npc remove` commands continue to manage physical placements independently.

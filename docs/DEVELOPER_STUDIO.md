# MMORPG team toolkit

Open `/studio` (`/content` and `/devstudio` are aliases). Every module uses an authenticated staff session, blocks preview extraction, checks its own permission and saves validated changes before making them active. Designers publish directly; optional review tasks do not impose a release gate.

## Tools

| Tool | Purpose |
| --- | --- |
| Items & Equipment Sets | Templates, stats, rarity, sockets, set bonuses and inert tooltip previews |
| Mobs & Abilities | Ground-mob archetypes, scaling, abilities, XP and loot-profile selection |
| Loot Tables & Bindings | Create/copy/edit tables, percentages, quantities, unidentified gear, purses and source bindings |
| Worlds & Encounters | Wand cuboids, spawn points, weighted rosters, limits, dig sites and public travel destinations |
| NPCs & Dialogue | Named characters, branching conversations, reputation/level gates and safe station/shop links |
| Crafting Recipes | Drag/click vanilla samples onto a ghost grid; choose authored output and skill requirement |
| Content Reference | Inspect existing NPCs, dialogue, daily quests, recipes, shops and factions |
| Quality Desk | Dependency checks, balance warnings, readiness limits and deterministic reward-free loot simulations |
| Team Workboard | Shared tasks, bugs, optional reviews, owners, notes and typed content references |

Reference-library entries are read-only. Daily-quest, faction and existing shop definitions are inspected here, not rewritten. The NPC editor links existing contract services; it does not add arbitrary commands, currency grants or a replacement story-quest engine.

## Recommended workflow

1. Publish equipment and any equipment set through the item editor.
2. Build a drop table and simulate it. Percentages are exact; a value of `5` means 5%.
3. Publish an encounter and select the table. Zone overrides and explicit mob bindings take priority over the encounter's default table.
4. Mark an area with the wand, configure supported landing points and its weighted roster, then enable it deliberately.
5. Publish NPC dialogue and place the NPC manually. No publication generates terrain, places a new NPC or enables a zone by itself.
6. Create a distinct crafting pattern using ordinary vanilla ingredients. Output must be an authored item; equipment produces one piece. Publish disabled until the pattern, output and skill gate are reviewed.
7. Run `/studio validate` and record playtest findings on the workboard. A clean dependency report is not release approval.

Recipe ghost samples do not consume inventory items. Number keys, creative cloning, double-clicks and preview extraction are blocked. Ordinary lower-inventory clicks remain usable only in the recipe grid so staff can pick up samples. Automated crafters cannot bypass the skill gate. Built-in recipes and IDs remain protected, and failed saves restore the prior live recipe registry.

NPC publishing keeps existing placement anchors. New NPCs remain unplaced; published body types are fixed. Dialogue previews never execute the services they display. New graphs permit safe service actions and level/reputation requirements, not new financial grants or arbitrary command execution.

Private text input expires after 60 seconds. `cancel` returns without changing the field. Starting another command ends the old private input. Concurrent edits use saved-definition/revision checks: reopen a stale draft rather than overwrite another teammate's work.

## Commands

```text
/studio
/studio items
/studio mobs
/studio loot
/studio world
/studio npcs
/studio recipes
/studio library quests
/studio validate
/studio board
/studio simulate <loot-profile>
/studio qa readiness
```

The individual `/itemengine`, `/mobengine`, `/lootstudio`, `/worldstudio`, `/npcstudio` and `/recipestudio` commands remain available, with domain-specific tab completion.

## Team permissions

Grant these bundles through your permission manager; they are not granted automatically:

- `nerdsmp.studio.builder`: world controls, NPC and travel administration, reference/QA/workboard access.
- `nerdsmp.studio.designer`: items, mobs, loot, NPC dialogue, crafting and QA/workboard access, with direct publishing.
- `nerdsmp.studio.tester`: reference, QA and workboard access without content publication.

Bundles do not grant player moderation, store fulfillment, economic administration or territory bypass. The existing NPC administrator permission includes its legacy NPC administration commands, so grant it only to trusted staff. Creative mode, external build tools and permission to edit protected terrain must be assigned separately. Operators retain the individual tools' default operator permissions.

## Guides and verification

- [Item and mob editors](CONTENT_ENGINE.md)
- [Loot editor](LOOT_STUDIO.md)
- [World controls](WORLD_STUDIO.md)
- [NPC and dialogue editor](NPC_STUDIO.md)
- [Recipe editor](RECIPE_STUDIO.md)
- [QA and workboard](QA_STUDIO.md)

Before wider use, test two simultaneous staff editors, private input cancellation/disconnect, every drag/shift/hotbar/creative interaction, NPC station access, actual skill-gated crafting, player encounter telegraphs, restart persistence, backup restoration and permission bundles. No studio scan can prove multiplayer behavior or a production load/recovery target.

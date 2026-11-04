---
tags: [brief]
---
# Brief

## The request (user's words, 2026-11-03)
> Utilize an obsidian vault for researching and referencing. I want to recreate the central map. Currently on
> the production server I'm using a map called MMORPG but I would like you to build and populate a new map with
> zones, NPCs, lore, quests, mobs, points of interest, lootable chests, and non-instanced dungeons. This will
> function as the story driven progression area that links skyblock and expeditions. Look at World of Warcraft
> for story, zone, npc, and mob design. I've provided some reference images. Beyond looking at the images look
> at popular builders, servers, terraformers, and more to learn how to produce a high quality studio grade world.

## Rules
- Original names, lore and art only. Use WoW for **feel and structure**, never its names, characters or assets.
  Don't recreate Wynncraft's or any other server's map.
- Build with the plugin's own systems (world packs, NPCs, quests, custom mobs, loot, expeditions). The plugin
  generates and populates the world itself, with no staff hand-placement.
- Other sessions share the repo. Commit only our own files and hunks; never stash, reset or switch branches;
  never push. A restart of any server the user plays on needs their OK.

## Definition of done
A new world the plugin can generate and populate, with zones, NPCs, quests, mobs, points of interest, chests and
open-world dungeons. It is checked on the scratch server, documented, and committed (not pushed).

## Context it plugs into
- **Skyblock**: every player starts on their own island ([[Decisions]]: the Reach is where the story happens).
- **Expeditions**: seven instanced dungeons and a raid, in a fixed ladder (see [[World Overview#Level ladder]]).
- **The old map**: `worldpacks/highcrown.yml` populates the MMORPG map (seed 27594263) with 94 NPCs and 58 quests.
  The new map keeps Highcrown as the capital, so the service NPCs' lore still holds. See [[Highcrown]].

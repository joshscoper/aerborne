# Highcrown Isles verification (2026-10-22)

Throwaway offline Paper 26.2 server with a copy of `MMORPG - 1.19.zip` as its main world, the plugin jar
built from this tree, and mineflayer bots (ViaBackwards). The original zip was not modified.

## Mapping

- Region files scanned (17,602 chunks): heights, top blocks, biomes, built blocks, 295 underground spawners,
  4,354 containers. A spectator camera bot toured the keep, bazaar, colosseum and gold mine.
- Every NPC anchor, warp and mob spawn point was checked in 3D for a solid floor and headroom.

## First startup

- `World pack highcrown v1 on world: 22 zones, 4 node sites, 50 NPCs, 16 warps` (after re-snapping seven NPCs
  that first stood on 15/16-tall dirt paths, which the NPC footing check rejects).
- The map loads on Peaceful; the pack raised it to Normal once and logged it. Before that, zone spawns failed
  with "Cannot spawn monster ... in Peaceful difficulty".
- Second startup: `already applied`. `/worldpack apply force`: only changed objects rewritten, warps the pack
  no longer lists retired, nothing duplicated.
- A new player joins at the keep gate (777.5, 101, −35.5).

## NPCs and quests (bot "Hero")

- All 50 NPCs answered a right-click with their dialogue (`npc list`: 50/100 anchors).
- A Crown of Welcome: accepted from Steward Aldric's quest board; talk (Cassia Dawn, Orin Ledger) and visit
  (`/warp market`) objectives ticked; turned in for 40 Marks, +10 Frontier Wardens, 8 bread; three follow-up
  quests announced.
- Sword and Shield: 5 Field Gnawers and 2 Bristle Boars killed in Goldenreach Fields; turned in to a different
  NPC (Captain Brenna) for Marks, Hunting XP, an iron sword and a shield.
- Sands of Sandmere: locked below adventurer level 6 (shown on the board), accepted after levelling; three talk
  objectives across the bazaar and colosseum.
- The Oasis Finds: 15 sand nodes dug at `oasis_dig`; 250 Marks, 700 Excavation XP, +30 Rift Scholars.
- Boars at the Fence: turn-in refused without porkchops, then took exactly 3 of 5.
- Gold Fever: 12 gold ore nodes mined at `gilded_veins`; 250 Marks, 600 Mining XP, iron pickaxe.
- `/journal` lists progress per objective; `/journal admin <player> complete <quest>` skips prerequisites.

## Zones, dig sites and shops

- Sweep of all 18 combat zones (bot teleported onto a spawn point, 22 s wait): every zone spawned its roster
  near the bot, e.g. Gilded Hollow 4 Overseers/Raiders/Lurkers, Colosseum 4 gladiators and 4 net-casters,
  Frostveil strays, a rime wolf and a polar bear. A first pass found Sunscar East too sparse (one point within
  the activation radius), so the large zones were resampled to 1,168 points in total with a 48-block radius.
- Dig nodes: `druid_peat` broke and regrew after 30 s; neighbouring andesite stayed protected for a non-op
  player. Gold vein nodes gave Mining XP, peat gave Excavation XP.
- Kasim's stall in Sandmere (about 1,100 blocks from spawn) sold cactus, because the bazaar is a safe zone.

## Map border

- Startup: `World border for world set to the edges of The Highcrown Isles (x -950..1000, z -540..580)`;
  `worldborder get` reports 1951 blocks. A restart leaves it unchanged.
- A bot swimming south from z 565 toward the southern continent was turned back at z 580 on every attempt,
  with the mist message.
- From z −530.5 an ender pearl thrown north (landing outside) did nothing; one thrown south moved the bot
  24 blocks.
- With `nerdsmp.worldpack.bypass` the bot swam out to z 587.8; when the permission was removed it was returned
  to z 578.6, just inside the edge.

## Tests

`./gradlew :nerdsmp-paper:build` passed, including `HighcrownContentTest` (every zone, creature, NPC, quest
objective and node site reference resolves) and `QuestRulesTest`. After the spawn-point resample the world-pack,
quest, dig-site, NPC, content and command suites were re-run and passed.

Bot note: mineflayer digs trip Watchdog's fast-break check, so the bot had only
`nerdsmp.anticheat.bypass` (not op) for the dig tests.

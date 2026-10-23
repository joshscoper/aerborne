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

## Quest markers, tracking and NPC pass

- Markers: a bot saw a gold `!` text display only over NPCs with a quest for it (Captain Brenna, Cassia
  Dawn), nothing over NPCs without one, and a gold `?` over Cassia once her quest was ready. Accepting
  Brenna's quest removed her marker.
- Greetings: quest givers' conversations opened with the gold reply ("! Do you have work for me?",
  "? I've finished what you asked.") and authored quest replies were folded into it.
- Trail: after accepting a Wildwood Vale quest in the keep the bot received gold dust particles stepping west
  along the ground; from 120 blocks away it also received an end-rod column over the nearest Wildwood Vale
  spawn point (x 436, z -30). The bot can't read action bars, so the distance text wasn't checked by bot.
- NPCs: version 2 of the pack placed 44 more NPCs (94/100 anchors). All 94 opened their dialogue for the bot;
  two patrolling guards had to be followed from their posts. The four golem sentinels needed a 2x2 footing
  pass before they would spawn.
- Patrols: all 11 routes were path-found on the walkable grid with the plugin's footing rules. In game the
  sergeant walked 26 blocks of his courtyard route in 20 seconds, stood still for 5 seconds while the bot
  stood beside him, and walked on (10.6 blocks in 8 seconds) once it left.
- The Lantern Route (Cassia's new personal quest): accepted from the gold reply, three talk objectives
  (lighthouse keeper, gardener, patrolling sergeant), turned in for 50 Marks and +10 Frontier Wardens.

## Marker colours and daily quests

- Grey `!` showed over Warden Halvard and Scout Yrsa, whose quests the bot couldn't take yet.
- Jory showed a gold `!` for his side quest; once that was completed his marker turned blue `!` for the daily
  Gnawer Cull. The bot accepted it, culled 10 gnawers, saw a blue `?`, and handed it in for 35 Marks and 120
  Hunting XP. The marker then cleared and the board read "Done for today. Back tomorrow."; the hand-in date
  was saved under `daily:` in `quest-progress.yml`. The next-day reset is covered by `QuestRulesTest`.

## Tests

`./gradlew :nerdsmp-paper:build` passed, including `HighcrownContentTest` (every zone, creature, NPC, quest
objective and node site reference resolves) and `QuestRulesTest`. After the spawn-point resample the world-pack,
quest, dig-site, NPC, content and command suites were re-run and passed.

Bot note: mineflayer digs trip Watchdog's fast-break check, so the bot had only
`nerdsmp.anticheat.bypass` (not op) for the dig tests.

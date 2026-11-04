---
tags: [research, wow]
---
# WoW NPC design

## What NPCs do in a WoW hub (play)
| Role | Job | In the Reach |
| --- | --- | --- |
| Story giver | Owns the zone's main chain; has a name, a stake and a problem | One per hub, plus the dungeon's lore giver (Pell, Ilsa, Cobb, Hesk, Vell, Wren) |
| Side givers | 1–3 small chains, each a person with a want | 2–4 per hub |
| Innkeeper | Rest, hearth, gossip that points at quests | One per hub; sets your return point |
| Flight master | Flies you between discovered hubs for a fee | **Skiff master** (airship skiffs) at every hub |
| Vendors | Food, repairs, reagents | One general goods vendor per hub, specialists in towns |
| Trainers | Teach skills | Capital only (existing service NPCs) |
| Guards | Patrol, give directions | Patrol routes (the pack already supports them) |
| Ambient townsfolk | Make the place alive; one line of gossip each | Several per town, with a line that points at a quest or a place |
| Quest-end NPC in the field | A scout, a wounded soldier, a hermit | The pack places them at outposts and camps |

## Character rules (play, plus Kaplan's "don't over-write" *(S1)*)
- **Every named NPC wants something.** Even a vendor has one line about it.
- **Recurring characters** carry the main story across zones (WoW's Westfall → Deadmines → Stormwind arc). Ours
  are Captain Wren, who meets you at the docks, and a Warden's herald who follows the trail of embers. See
  [[Main Story]].
- **NPCs move with the story.** After a chain, a giver turns up at the next hub. The pack gives an NPC one anchor
  per world, so a moved NPC is a second anchor with a different definition (e.g. `wren_docks`, `wren_scar`).
- **Silhouette and outfit say the role** from a distance: guards in iron, rangers in green, miners with lanterns.
  Villager profession looks (`look: armorer@snow`) exist already.
- **Gossip points somewhere.** Every ambient line names a place, a person or a danger.

## Rules for the Reach
1. Each hub: story giver, 2–4 side givers, innkeeper, skiff master, general vendor, 2–4 townsfolk, 1–3 guards.
2. Each zone: 1–3 field NPCs at camps or outposts (a scout, a hermit, a survivor).
3. Every quest NPC gets a backstory of three sentences and a branching reply; townsfolk get one or two lines.
4. Names are original, short and say the culture: Goldenreach farmers have plain English names, Thornwood rangers
   leaf-and-bough names, Ironspine miners hard consonants, Mistmere sailors sea names.

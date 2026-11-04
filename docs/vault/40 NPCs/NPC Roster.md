---
tags: [npcs]
status: proposal
---
# NPC roster

## Reused as they are
- **All 31 service NPCs** (bank, forge, shops, jobs, parties, graves, relics, expeditions, contracts…) move to
  their [[Highcrown#Shape (south to north, one axis)|Highcrown districts]]; their Highcrown backstories stay true.
- **Expedition givers** go to their zone's main hub: Sexton Pell → Lantern Hill, Ranger Ilsa → Bough Hollow,
  Engineer Cobb → Saltreach (later Cobb's Weir), Old Miner Hesk → Cragholm, Archivist Vell → Vell's Camp.
  Their dailies keep working.
- **Steward Aldric** stays the main-story voice in the keep.

## New in the first slice
| NPC | Where | Role | Hook |
| --- | --- | --- | --- |
| Captain Wren | Sky Docks | Skyfarers' Guild, story | Flew you in; sends you to Aldric; runs First Flight |
| Dockmaster Ebb Tallis | Sky Docks | skiff master | Grumbles about how many islanders arrive |
| Hollis Marr | Millbrook | Reeve, **main story giver** | "The fields have gone quiet, and quiet fields are wrong" |
| Wenna Marr | Millbrook | innkeeper (the Sheaf & Sickle) | Gossip; Hollis's sister |
| Garth Ploughwright | Millbrook | smith, vendor | Examines the ember-coals |
| Old Ada Miller | Millbrook | miller, side giver | Gnawers and the millstone pin |
| Brother Tobin | Millbrook | chapel keeper | Remembers the granary fire |
| Jory Fenn | Millbrook | skiff master | |
| Bess Thistle | Thistle Farm | side giver | Her son Tam followed the singing |
| Tam Thistle | Windmill Ridge (then Thistle Farm) | quest target | Dazed, humming the Choir's hymn |
| Pip Brindle | Brindle Orchards | cider-maker, side giver | Bees and boars |
| Warden Hale | Kingsgate Fields | Frontier Warden road guard | First kill quest; teaches the zone |
| Maren Ashdown | Hollow Road | Frontier Warden scout, recurring | Has tracked the closed-eye mark for a year |
| Millbrook Watch ×3 | Millbrook | patrolling guards | |
| Townsfolk ×6 | Millbrook, Thistle Farm, Orchards | ambient | Each line points at a place or danger |
| Highcrown Watch ×6 | Highcrown | patrolling guards | |
| Highcrown townsfolk ×8 | Highcrown | ambient | Gossip about the Vale and the Scar |

That's about 35 new NPC slots in the first slice, plus the 31 service NPCs. `npcs.maximum-instances` (default 100)
needs raising if both maps stay loaded.

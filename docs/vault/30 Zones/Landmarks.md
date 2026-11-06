# Landmarks

The zones beyond the Vale get their full towns, quests and dungeons in their own phases ([[Phases]]). Until then
each has its **hero landmark** standing where its zone note puts it. Every one is a named place (walk in to
discover it) with a world chest. They're built by `ReachLandmarks`; each is sited on the flattest dry ground near
its planned point, clear of roads, rivers and the lip.

| Landmark | Zone | What it is | Chest |
| --- | --- | --- | --- |
| The Huntsman's Barrow | [[The Barrowdowns]] | A long mound with a boulder kerb and a ring of standing stones on its crown, a crescent of tall stones and a capstone framing the Hollow Barrow's door, an avenue of paired stones from the road (with a footbridge over the Goldwater), two round barrows beside it | II (on the crown) |
| The Stone Rings | [[The Barrowdowns]] | A circle of eleven stones round an altar | II |
| Stonewatch | [[The Barrowdowns]] | A ruined round watchtower over the way to Thornwood, a ladder to its broken floor | III |
| The Heart Tree | [[Thornwood]] | A colossal tree, black thorn spiralling up the trunk, the Canopy's hollow door at its roots | III |
| The Great Aqueduct | [[Mistmere]] | Round arches in pale stone carrying a water channel east; copper sluices, a ladder to the walk; a sunken cistern court south of the arches | III |
| The Deep Foreman's Headframe | [[Ironspine Highlands]] | A black timber hoist with a sheave wheel over a shaft; winding house and spoil heaps | III |
| The Sunscar Arch | [[Sunscar Breaks]] | A natural arch of banded rock, a chest on its crown | III |
| The Ember Gate | [[The Ashen Scar]] | A great square portal of black stone on the Rift's lip: rune-lit pillars, a weeping eye on the lintel, the opening alight with the way to Cinder Keep; a raised dais and grand stair between two colossal guardians; behind it the corrupted ground runs out to the abyss | IV |
| The Wardens' Bridge | [[Sunscar Breaks]] / [[The Archive Isle]] | Where the Spine ends: a bridgehead between pale towers, the fallen chain bridge's first spans out over the cloud, its other half on the isle; a flight point at each end | none |
| The Seal Spire | [[The Archive Isle]] | A pale gold-banded tower with three floating stone rings, the Hall's door at its foot under three settings, one empty | IV |

## Expedition doors
Six landmarks hold an expedition's way in. Each door is an arched opening filled with the starlit dark of an end
gateway, in a frame two blocks deep with a keystone and cornice in the landmark's own stone. Banners hang either
side, and two braziers burn on pedestals before it. Each pedestal carries a glowing sign: the expedition's name on
the left as you walk up, its level on the right. Stepping in opens the expedition's board and sets the player back
on the threshold (spot `door_<expedition>`, `ReachGameplay.onDoor`).

| Landmark | Expedition | Level | The door |
| --- | --- | --- | --- |
| The Huntsman's Barrow | The Hollow Barrow | 8 | Just inside the passage mouth, mossy stone; soul fire; the mourners' lane leads to it |
| The Heart Tree | Thornwood Canopy | 12 | In the hollow between the roots, framed in black wood; soul fire |
| The Great Aqueduct | The Sunken Aqueduct | 16 | In the north wall of a sunken court of calcite, reached by a stair the court's width; lamp posts at its corners |
| The Headframe | Gloomforge Mines | 20 | The cage gate on the shaft's south rim, deepslate and dark timber |
| The Seal Spire | The Hall of the Three Wardens | 24 | The gilded portal at the spire's foot, open now; a paved walk before it |
| The Ember Gate | Cinder Keep (raid) | 26 | The great portal's whole opening, thirteen wide and twenty tall |

The First Flight tutorial starts with Captain Wren at the Sky Docks, not at a door.

## Waypoints
About every 350 blocks of road through the outer zones stands one of these:
- a traveller's camp (tent in the zone's cloth, fire, packs);
- a ruined waystation (broken walls, cold hearth);
- a cairn with an offering stone.

Each has a chest: tier I on the Downs and in Thornwood, II in Mistmere and the Ironspine, III beyond. With the
milestones and shrines this keeps the next thing in sight on any road, without crowding it.

---
tags: [mobs, bestiary]
status: proposal
---
# Bestiary

Four families per zone ([[WoW Mob Design#Rules for the Reach]]): **wildlife**, **local people**, **the story's
enemy**, **the zone's oddity**. Bodies are vanilla entities dressed with gear, names and particles (the content
bible's "parity-first" rule). Existing creatures from `content/world-mobs.yml` and `expedition-mobs.yml` are reused
where they fit.

## Goldenreach Vale (1–7)
| Creature | Family | Body | Levels | Where | Behaviour | Status |
| --- | --- | --- | --- | --- | --- | --- |
| Field Gnawer | wildlife | silverfish-sized rat (existing) | 1–3 | Kingsgate Fields, granary yard | swarms of 3 | exists |
| Bristle Boar | wildlife | hoglin-like boar (existing) | 2–5 | Orchards, Hollow Road | charges | exists |
| Wild bees | wildlife | bee | 2–4 | Orchards | neutral until a hive is hit | vanilla |
| Straw Scarecrow | oddity | (existing) | 2–5 | Terraces, Windmill Ridge | stands still in fields, wakes when you pass; drops **ember-coal** (quest) | exists, re-skin with ember glow |
| Tollkeeper Cutthroat | local people | vindicator in leather, red sash | 4–6 | Tollroad camp | camp member | new |
| Tollkeeper Bowman | local people | pillager | 4–6 | Tollroad camp, road patrol pairs | ranged, keeps distance | new |
| Toll-Captain Brask | named | vindicator, iron, banner | 6 | Tollroad camp centre | quest kill, cleave telegraph | new |
| Choir Initiate | story enemy | zombie villager in grey robes (leather dyed), ember particles | 5–7 | Black Granary, Windmill Ridge at night | melee, sings (ambient sound) | new |
| Choir Cantor | story enemy | evoker body without fangs; casts **Ember Hymn** (heals allies, interruptible) | 6–7 | Black Granary | caster at the back of camps | new |
| Ember-Hatched Scarecrow | oddity (dungeon) | straw scarecrow on fire | 6–7 | Granary Cellars | burns on hit | new |
| **The Scarecrow King** | boss | large scarecrow (husk scale 1.6 in a crown of wheat) | 7 | Granary Cellars | **Harvest** (telegraphed circle), calls 3 hatchlings at 50% | new |
| Old Grizzlehide | rare | big boar | 5 | Orchards | 30 min respawn; drops Grizzlehide Tusk (trinket) | new |
| Hollow Jack | rare | scarecrow with a lantern head | 6 | Windmill Ridge, **night only** | 40 min; drops Hollow Jack's Lantern | new |
| Gilt-Tooth | rare | gnawer queen | 3 | Millbrook drains | 25 min; drops a gilt tooth (sells well) | new |

## Later zones (families only, filled in their phase)
| Zone | Wildlife | Local people | Story enemy | Oddity |
| --- | --- | --- | --- | --- |
| Barrowdowns | moor wolves, ravens | Gravepickers | the risen (barrow thralls, crypt archers) + Choir diggers | lantern wisps |
| Thornwood | wolves, Mire Weavers | – (rangers are friendly) | thornfolk, Rotbark Acolytes | Old Bark (walking stumps) |
| Mistmere | lake crabs, herons, pike | smugglers | the drowned | bog lights |
| Ironspine | rams, goats, rock lurkers | claim-jumpers | deepfolk, Choir overseers | rock elementals |
| Sunscar | dune scorpions, vultures | dust raiders | heat-mad nomads | sand wraiths |
| Archive Isle | sky drakes | Choir thieves | Warden echoes | archive guardians |
| Ashen Scar | ash hounds, magma creepers | – | the Ember Choir at full strength | fire elementals |

## Rules
- Camp mobs share aggro; casters stand at the back.
- Mobs drop materials from the Reach's own tables, never vanilla junk (the expedition rule).
- Quest drops count only for players on the quest, with Kaplan's rising chance.
- Every new creature gets a bestiary entry on the website through the existing mob sheet.

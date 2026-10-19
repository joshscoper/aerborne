# Island presentation and optimization - September 29, 2026

This pass adds collectible-style island presentation and removes repeated lookup,
rendering and save work. It does not add a paid loot system, minions, a new currency,
stat boosts or another island reset. Existing islands keep their IDs and terrain.

## ManaCube research

The [official Season 14 changelog](https://manacube.com/skyblock14/changelog/),
published June 2026, emphasizes previewable island choices, browsable social warps,
GUI-based island logs, clearer permissions and visible progression. The earlier
[Season 9 announcement](https://manacube.com/threads/skyblock-revamp-season-9.54019/)
also brings island controls together in one interface and ties expansion to quests.

Our interpretation: make the next goal obvious, make progress visible to friends,
and let players preview what they are working toward. This pass applies those
principles to the landing, journal and directory; it is not a reproduction of
ManaCube's maps, art, items, economy or monetization. Party content and social warp
discovery remain separate roadmap work.

## Player-facing changes

Right-click the bedrock landing, or use `/island`, to open island controls.
Sneaking leaves the shortcut inactive. `/island showcase` opens the collection.

| Centerpiece | Unlock | Presentation tier |
| --- | --- | --- |
| Skybound Ember | Create an island | Foundation |
| Verdant Heart | Complete chapter 1 | Rare |
| Arcane Prism | Complete chapter 2 | Epic |
| Celestial Star | Complete chapter 3 | Legendary |

The labels describe presentation, not drop odds. Every unlock is guaranteed by its
chapter; there are no rolls, duplicates, near-misses, keys or purchases. Models use
vanilla items as display props, not inventory rewards. These earned choices belong
to the seasonal island, not paid entitlements. The owner chooses a centerpiece;
members contribute skill milestones but cannot change the owner's display.

- The gallery previews all four choices, identifies locked ones and highlights
  the selected centerpiece. A locked click explains the required chapter.
- A floating item and label show the island name, chapter and remaining certified
  milestones. Text respects terrain occlusion and visibility is per player.
- Nearby co-op members can see a three-second fade-in/out completion reveal, a
  small particle burst and a sound. Completion is saved before presentation runs.
  Replaying a completed chapter cannot produce another completion reward.
- The journal previews each chapter's display unlock. The island directory uses
  the selected icon, and the sidebar shows island progress while on an island.
- `/settings` includes **Island showcases** and **Island celebration effects**.
  Turning effects off keeps the static display but suppresses reveals, particles
  and sounds; turning showcases off hides both. Changes are saved to player YAML.
- `/island showcase hearth|verdant|arcane|celestial` selects an unlocked choice.

Only certified skill milestones count in the display, not raw partial skill XP.
The island journal still requires the player's explicit contribution. The existing
Adventurer experience bar remains separate.

## Optimization scope and evidence

| Area | Previous work | New behavior |
| --- | --- | --- |
| Co-op homes and invites | Scan every saved island | Reverse indexes read only relevant owners |
| Island allocation | Rescan occupied slots from zero | Advance a free-slot cursor after successful saves |
| Public directory | Filter and sort on every read | Reuse an immutable snapshot until an island changes |
| Claim protection views | Allocate a member map/view on every query | Reuse up to 2,048 chunk views; compare the current immutable island snapshot before reuse |
| Sidebar and tab | Reapply unchanged lines and headers | Send only changed text and names |
| Nameplates | Refresh every board on every XP/UI update | Broadcast only when rank, level, role or platform changes; initialize new boards separately |
| HUD data queries | Allow overlapping refreshes | One request group per player, ten-second timeout, session/account checks before applying |
| Preferences | Rewrite unchanged settings and all loaded profiles at shutdown | Skip no-ops; save before publishing; no redundant shutdown writes |
| Milestone proofs | Allow another certification to replace the recorded contributor | Preserve the first proof and skip unchanged saves |

Regression tests reuse the exact same protection view across **10,000 reads** and
the directory snapshot across **1,000 reads**, then verify invalidation. Failed
saves must leave membership indexes, display selection and the directory unchanged.
Other tests cover invite expiry/revocation, restart rebuilding, old showcase-free
saves, malformed/locked choices, display budgets, fades and preference migration.

Atomic saves still happen before authoritative changes become visible. This is
not an asynchronous player-storage rewrite; database load, synchronous YAML costs
and the previously observed world-save disk stall remain profiling work. No TPS,
MSPT or percentage speedup is claimed from unit tests or an empty-server smoke test.

## Display budget

`skyblock.displays` in `config.yml` accepts:

```yaml
skyblock:
  displays:
    enabled: true
    max-active-islands: 32
    range: 24
```

- One shared scheduler, every 10 ticks. It groups online island viewers by grid
  cell; it never scans all stored islands or the entire entity population.
- Two non-persistent entities per active island: one text display and one item
  display. The configured island limit is clamped to 1-64; range to 8-32 blocks.
- At most 64 temporary reveal labels, one per viewer. They expire after 60 ticks.
  No permanent idle particle loop and no separate task per entity.
- No chunk tickets or forced chunk loads. Displays need a nearby eligible player
  and a loaded center chunk; entities are removed when no longer needed.
- Visibility checks run every half second. Hiding a showcase, revoked entry,
  obstructed sight, departure, unload and shutdown all remove or hide the view.
- Staff or console can use `/island performance` to inspect the active budgets.

## Verification and deployment

- Full `qualityGate build`: **413 tests**, zero failures, errors or skipped tests.
- Built and staged jar SHA-256:
  `B13E3F4609C82E6330B47C33A81B19CED38AC364FD0FBAD5DEA61BF149C070E0`.
- Previous isolated plugin and NerdSMP data backed up to
  `runtime/review-backups/skyblock-showcase-20260929-175036/` before replacement.
- Deployment target is only `runtime/skyblock-test`. The SMP runtime's deployed
  jar and worlds are unchanged. No accounts were imported or reset.
- Local Paper/Nexo startup passed. `/island` help includes `showcase`, console
  access is correctly player-only for the gallery, and `/island performance`
  reported `0/32` anchors and `0/64` reveals with no players online.
- The existing world manifest hash stayed
  `2E14F69E36755C4BEFBE174E6D5488BA8BE11E20AA5CC55A6D9340AAFD3D89A4`.
  Nexo generated/uploaded its pack; shutdown saved all dimensions and exited 0.
  No NerdSMP exception or watchdog dump occurred. The pre-existing Windows
  OSHI/Perflib warning remains. The server is left stopped.
- No real player session was available: this smoke test does not exercise rendered
  display entities, GUI interactions, Bedrock translation or loaded-server scaling.

### Required in-game and load checks

1. Owner and visitor inspect the gallery and landing on Java and Bedrock. Confirm
   text scale, item alignment, opaque-wall occlusion and readable line lengths.
2. Complete a chapter with a teammate near the landing: one unlock, brief reveal,
   selectable keepsake, no inventory item. Reconnect/restart and verify selection.
3. Test effects off, all displays off, leaving range, changing worlds, logout and
   co-op removal during a reveal. No orphaned or visible-through-wall displays.
4. Visit enough separate islands to exceed the anchor budget; compare entity
   counts before/after travel and shutdown. No displays should keep chunks loaded.
5. Profile 30 players with generators, farms, combat and markets, first with island
   displays disabled, then enabled. Record p95 MSPT, entity counts, save latency and
   packet traffic. Do not infer loaded-server performance from idle diagnostics.

The broader conversion gates and prior release blockers in [SKYBLOCK.md](SKYBLOCK.md)
and [the code review](CODE_REVIEW_2026-09.md) still apply.

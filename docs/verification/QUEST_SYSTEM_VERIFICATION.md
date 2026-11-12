# Quest progression and conversation pass — 2026-11-11

Story quests now publish journal changes on acceptance, objective progress, hand-in, level refresh and administrative edits. Delivery counts come from ordinary items currently in storage slots. The same allocation drives readiness, journal rows, NPC markers and the tracker; overlapping delivery objectives cannot spend the same item twice. Inventory changes are reconciled once per second and hand-in rechecks them immediately.

Story harvesting uses a separate resource claim channel from daily contracts. It checks mature crops, resource provenance and the committed block break. Cancelled breaks and placed-resource farming cannot advance the story counter.

NPC greetings list named quest topics. Each topic has an offer/progress page, rewards, and the relevant accept, discuss, track or turn-in reply. Talk objectives advance only when the player selects that quest's conversation, including zone restrictions. These replies use the existing single-use NPC conversation session and its distance/visibility checks. Existing services remain under “Let's talk about something else.” Extra quest topics are paginated; the inventory quest board also paginates instead of stopping after seven entries.

The Reach prologue and Vale main chain now pay enough skill XP to reach the next story gate without requiring extra kills, optional quests or consumed lamps. Story milestones add useful equipment, supplies and skill lamps. Tam has an actual conversation about the song; his mother's response acknowledges a rescue report rather than pretending an escort occurred. Ada's mill quest now asks for grain instead of repeating the field-gnawer hunt. Existing completed quest IDs remain completed.

## Content format

Existing schema-1 files remain valid. A talk objective may include up to three short `dialogue` lines. They are spoken by the objective's target after the player chooses to discuss it. Quest text substitutes `{player}` safely.

```yaml
- type: talk
  targets: [tam_thistle]
  description: Find Tam at the burned mill
  dialogue:
    - "Bess sent you? I followed the singing up here."
```

Item rewards still accept strings. Material rewards may also use a mapping with a name and up to four lore lines. Custom `item:` rewards retain their catalog presentation. Experience bottles use the existing Bottled Lesson skill-lamp behavior.

```yaml
items:
  - item: "material:iron_sword 1"
    name: "Hale's Field Blade"
    lore: ["For the recruit who reopened the Millbrook road."]
  - "material:experience_bottle 1"
```

## Verification

Regression coverage includes inventory loss/reacquisition, overlapping material alternatives, delivery consumption totals, offer/hand-in conversation choices, talk report pages, topic pagination, the complete Reach main-story level ladder, bundled content parsing and resource-channel isolation. Reach pack validation checks that the referenced NPCs, zones and encounters exist.

The targeted Gradle run includes `quest.*`, `npc.*`, `resource.*`, `reach.ReachPackTest`, `content.*` and `worldpack.HighcrownContentTest`, plus `:nerdsmp-paper:shadowJar`.

This is local code and packaged-content verification. The running server has not been updated, and an in-game playthrough has not been performed. Live checks still needed: both vanilla chat and the Aerborne dialogue panel; acquire/drop/reacquire delivery items; turn in with a full inventory; reconnect during an active quest; follow the prologue through the Grain Stair. A server-owned `content/world-quests.yml` continues to override bundled world quest content, while bundled Reach quests merge alongside it.

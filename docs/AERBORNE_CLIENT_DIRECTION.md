# Aerborne client: visual cleanup and next features

## Current pass — 11 November 2026

The supplied `Aerborne_Agent_Ready_UI_Brief.zip` is reference material. Its embedded agent prompts
describe a possible full atlas production workflow; this pass does not claim to regenerate those
atlases. The current sprites include the brief's structural example art. The painted concept
board remains a visual target for a later production art pass.

This pass preserves Claude's client/server work and the existing assets, while improving their
presentation: grouped menu destinations, quieter cards and HUD panels, clear focus contrast,
unobstructed settings titles, stable bar fills, cleaner NPC labels and paginated companion settings.
Minecraft framebuffer screenshots show actual code running, with explicitly synthetic quest,
dialogue and HUD preview data. Live multiplayer behavior still needs verification.

## Competitive baseline

Lunar advertises a broad collection of customizable mods, settings and installation conveniences.
Its own waypoint guide describes configurable waypoint presentation. These are expected benefits,
not a distinctive Aerborne proposition. Sources: [Lunar features](https://www.lunarclient.com/features)
and [Lunar waypoints](https://www.lunarclient.com/news/everything-you-need-to-know-about-lunar-clients-waypoints-mod).

Wynntils already lists a custom quest book, quest tracking, item guide, minimap, waypoints, item
locking, party information, custom stat bars and toasts. Aerborne's existing integrations build a
useful baseline, but this list by itself does not distinguish it. Source:
[Wynntils feature list](https://wynntils.com/features.php).

The recommendations below are product judgments, not claims that no other client offers them.
The advantage is access to Aerborne's authoritative progression, island and expedition systems,
combined with a coherent interface for how people actually play Aerborne.

## Recommended order

| Priority | Feature | Concrete player experience | First useful slice |
| --- | --- | --- | --- |
| 1 | Journey Planner | Pin a goal such as a crafting unlock or island upgrade; see the unmet prerequisites and one useful next action. | One supported goal family, a prerequisite list, progress from the server, and a link to the relevant existing menu. |
| 2 | Island Project Board | Co-op members choose a shared upgrade, see remaining materials, and record contributions without maintaining an external checklist. | One active project per island, material totals and an explicit contribute action using the existing server transaction path. |
| 3 | Expedition Briefing | Before leaving, see the party's readiness, chosen activity, equipment durability and required supplies in one place. | Party ready checks, missing requirements, and a concise preparation summary. |
| 4 | Aerborne Chronicle | A personal record of discovered places, finished story chapters and island milestones, with earned presentation rewards. | Server-authored milestones in a timeline; earned frames/themes applied locally. |

## Start with Journey Planner

The player chooses a goal, sees what they already have, and pins the next unmet step. Completing
that step updates the goal automatically. The planner explains why a step is unavailable and
links to the appropriate quest, skill, recipe or island menu. It does not auto-spend resources.

The first iteration should support one real progression path selected from the current server
content, rather than imply that every goal has been mapped. Reuse the quest feed and HUD tracker
where they fit. Add a bounded server payload containing stable goal/step identifiers, visibility,
completion state, prerequisites, requirements and permitted action links. The server decides
unlock eligibility; client-side guesses must never award progress.

Acceptance criteria:

- A returning player can identify their next useful action from the goal screen.
- Completing a prerequisite refreshes the plan without rejoining.
- Unknown or hidden content stays undisclosed until the server reveals it.
- Reconnects, world changes and stale snapshots cannot show old progress as current.
- Any action still uses normal costs, permissions and confirmation rules.
- Vanilla players can inspect and pursue the same goal through server menus.

## Keep the client comfortable

Before layering on more HUD content, add a drag-and-snap HUD editor with a few restrained presets
(exploring, combat and building), text scale and high-contrast controls. This is useful groundwork,
not the headline differentiator. The current tall party/objective stacks also need a live-server
stress test so the eventual layout handles large groups without shrinking text excessively.

Favor earned island/story identity over a cosmetic catalog with no connection to play. Keep every
gameplay reward and route available to vanilla users; the optional client earns its place through
clarity, convenience and presentation.

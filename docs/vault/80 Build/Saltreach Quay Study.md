---
tags: [build, worked-example, mistmere]
status: design-exercise
---
# Saltreach Quay Study

**Design task.** A new arrival in [[Mistmere]] should be able to walk up from a fishing quay, find the weighhouse, and take the market road back down to the boats. From the lower landing the player should also recognize the Great Aqueduct as the way east. The project supplies Saltreach, its terracotta roofs, pale walls, timber harbor towers and the distant aqueduct. The weighhouse and all dimensions below are proposed for practice. Survey a real site before adapting them to the generator.

## Build a section that someone can walk

Local plot is 32 × 24 blocks. X increases east, Z south. Coordinates are inclusive. Y denotes the **top of a walking surface**, relative to the water surface at 0. Start at (5, 17) looking east. Keep the lake to the south. The quay must connect to the shore at the east and west ends if neighboring plots extend it.

| Part | X | Z | Elevation and construction |
| --- | --- | --- | --- |
| Lake | 0–31 | 19–23 | Water at 0. Keep the boat approach beside the landing open. |
| Lower quay | 0–31 | 15–18 | Deck at +1. Give the seaward edge a break where the boats unload. |
| Fish stair | 12–14 | 12–14 | Rise north one block per row. From south to north the stair tops reach +2, +3, +4. Keep the full three-block width clear. |
| Upper apron | 8–27 | 9–11 | Level at +4. Join the stair at its north edge and turn east toward the weighhouse door. |
| Weighhouse | 16–24 | 2–8 | Floor at +4. Door at (20, 8) opens south onto the apron. Put a scale against the inner east wall with room for a second player. |
| Market return | 25–27 | 12–14 | Three steps down toward the quay, from +4 to +1. Both routes meet on the same deck. |

**Section at X 13.** From Z 19 northward, the cross section is water 0 → deck +1 through Z 15 → stair at Z 14, 13, 12 → apron +4 at Z 11. Run the apron east on one level to X 20. At the building, carry the +4 floor through the doorway. Face exposed fill with stone and leave an outlet below the apron beside the stair. Continue the apron to the return stair. Walk both ways before selecting a roof.

**First view.** From the southwest landing, keep the entry stair visible to the northeast and a gap over the east quay for the aqueduct. The door can reveal itself on reaching the upper apron. Its awning should cast a shadow over the receiving bench. A quay crane in the protected view would compete with the distant arches. Move its boom landward before raising either landmark. This uses mine67's repeatable camera and Sucker Punch's distinction between seeing a destination and finding a way there. See [[Minecraft Builder Case Studies]] and [[AAA Level Design Case Studies]].

## Make one occupied bay

Put a wet basket at the unload point, the scale inside the receiving door, and stacked empty baskets near the return stair. Leave the transit route clear enough for two players to pass. In section, show what carries the upper yard and what keeps runoff off the stored goods. Use a pale stone base, a timber loading beam, a white upper wall and an orange terracotta roof from the zone palette. Try darkened stone under the outlet, then view it from the quay. If it looks like a random patch, move the patch to the actual water path or change the outlet construction.

To test repetition, give the quay a second bay with the same deck height and a different job, such as repairing nets. Keep shared posts aligned and allow one builder to own each boundary post. Turn a spare copy through ninety degrees and join it to the first around a corner. Check rail connections, stair direction, roof overlap and door clearance on both copies. If a joint fails on the rotated copy, repair the kit before extending the waterfront.

## Run the walk and keep the evidence

Give a player this instruction once: “Bring this catch to be weighed, then meet me back at the boats.” Keep a separate record of geometry checks, player behavior and human visual judgment. These are expected results for the exercise.

| Test | Observation to record | First revision if it fails |
| --- | --- | --- |
| From (5, 17), turn east and then toward the fish stair | Which stair the player sees first, and whether the distant arches remain distinct from the harbor tower | Change the obstruction at the decision point. Retest from the same camera and one boat-width to the side. |
| At (13, 11), face the weighhouse | Can the player identify the receiving door while another player leaves it? | Pull the basket stack off the route or shorten the awning that hides the door. |
| Inside, interact at the scale and return via the eastern stair | Can both players pass, descend and find the same boat without jumping or dropping into the lake? | Move the scale into its bay, clear the inside turn, or correct a reversed stair. |
| Repeat at night and with particles reduced | Does the entrance read from its shape and light? Can the player distinguish the two stair landings? | Light the doorway recess or expose the lip of the return stair. Preserve the darker store interior. |

Save a measured section, a daylight arrival shot and a matched return shot after any revision. Record the actual world, coordinates, facing, resource pack and render distance. If a tester, screenshot or playable site is unavailable, record the missing check. [[Building in the Reach]] explains how to inspect reserved bounds and named spots once an actual site is chosen.

## Handoff between Codex and Claude

The author leaves the local coordinate table, module edge ownership, intended view, source links and the revision tested. The reviewer receives the player task and spawn point first, walks it independently when possible, then compares the first hesitation to the author's prediction. Record the result with [[Review Record Template]]. If only one agent is available, keep the same order and label a self-review as such.

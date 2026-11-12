---
tags: [research, minecraft, building]
---
# Minecraft Builder Case Studies

Use these cases when a specific part of a build is failing. Each first paragraph reports the builder's published account. The experiments and acceptance checks are proposals for this project. Read [[AAA Level Design Case Studies]] for how a player experiences these choices while moving.

## Enderock — lay out mountain groups before sculpting slopes

For *Wild Freedom*, Enderock sketched the placement of mountains and islands before shaping the landscape with WorldMachine and WorldPainter. He describes drawing on the countryside around him. The order matters for a generated world. A convincing local slope can still occupy the only place from which a traveler could have seen the far ridge. [Builder interview, *Wild Thoughts*](https://www.minecraft.net/en-us/article/wild-thoughts)

**Try it in Ironspine.** Mark the skyline from Aqueduct Road on paper. Place the summit, a lower shoulder for the headframe, and a saddle through which the player can see it. Draw a second section along the haul road. Give the road a continuous grade up the shoulder and let the steeper rock fall away to its side. In the rough terrain, walk to the saddle from both ends. If the crane appears to grow out of the summit, lower the shoulder or move the approach sideways before changing the crane. See [[Ironspine Highlands]].

## PearlescentMoon — give an organic landmark a pose

In her account of making the *Slightly Surreal Wolf*, PearlescentMoon describes studying anatomy in three dimensions, roughing out the body, and attaching the limbs, head and tail. She considers pose early enough to change the character of the sculpture. This is especially useful in Minecraft, where two volumes can merge into one stair-stepped outline. [Builder interview, *Crafting a Creature*](https://www.minecraft.net/en-us/article/crafting-creature)

**Try it on a stone guardian.** Mark feet and hips, build a rib cage, then turn the head toward the approaching path. Leave daylight between the forward leg and chest. Inspect it from the path and at ninety degrees. If the player can read the pose only from the side, move the forward foot across the line of travel and turn the chest before adding fur, chipped stone or eyes. The same exercise works for a hero tree when trunk, fork and crown replace the body masses. See [[Landmark Shapes]].

## mine67 — use a fixed camera to diagnose a composition

For *Urban Buildings*, mine67 tested a perspective illusion by taking screenshots from its intended viewpoint and adjusting the build against them. The interesting constraint is the camera. A change to a roof can appear to fix an overlap when the actual difference was a sidestep by the photographer. [Builder interview, *Urban layers*](https://www.minecraft.net/en-us/article/urban-layers)

**Try it on a street.** Record a standing position and facing at the gate. Take the same shot before and after shifting a near awning. Then walk three blocks to each side and approach from the return route. A poster can depend on one exact camera. An MMO street needs enough separation between its awning and tower that a player can choose a side of the street and still find the gate. Save the matched pair and record the first position where the silhouette collapses.

## WesterosCraft — weather the part of the wall that actually gets wet

The team's facade guide separates the choice of a value-ordered palette from the pattern applied to the wall. Its worked cottage starts with a plain structure and places wear around openings, bases and runoff before filling in the gradient. Depth of palette depends on the size of the surface. [WesterosCraft, *Gradients*](https://westeroscraft.com/docs/building/fundamentals/gradients)

**Try it on a retaining wall.** Build two copies of one bay using the same stones. On one, distribute the darker stone evenly. On the other, put a dark patch below a drain and taper it across adjacent blocks, leaving the dry upper face mostly intact. Inspect from the far bank in the target resource pack. If both look speckled, reduce the contrast between neighboring stones. If both look flat, change the outlet depth and coping profile before adding another stone. See [[Surface and Light]].

For a five-row trial panel, read the rows from top to bottom. `O` is the drain opening, `L` the dry wall, `M` the transition and `D` the wettest stone. Inspect the cluster at actual block scale before repeating it. Orient a second bay to the drain location on that bay.

```text
L L L O L L L
L L M D M L L
L L M D M L L
L L M M L L L
L M M L L L L
```

## Blockworks — design a monumental building around what a visitor does

Blockworks describes *The Uncensored Library* as a place where visitors can access journalism. An index covering many countries and dedicated country exhibitions give the monument different scales of use. Its lesson for a public RPG building is to give the visitor a specific thing to find when they reach the impressive room. [Blockworks project account](https://www.blockworks.uk/projects/theuncensoredlibrary)

**Try it on a guild archive.** Draw the return route from a single readable item on a desk to the entry hall. Place the desk in a side bay, give that bay a recognizable silhouette from the main aisle, and reserve a standing spot where a second player can pass. Walk in with the task “find the ledger and get back outside.” If the vaulting dominates every view and testers walk past the desk, shorten the aisle view with a transverse arch and light the bay. Keep the room large enough for ceremony and the reading point small enough for use.

## How to choose between these examples

| Symptom from the player's view | First study | Keep fixed while comparing |
| --- | --- | --- |
| A mountain landmark appears only after the player reaches it | Enderock | The player's route and render distance |
| A statue looks like a pile from the road | PearlescentMoon | Overall height, camera, block palette |
| A good screenshot becomes a confusing street | mine67 | One matched camera plus the same walking route |
| A wall looks noisy from across the water | WesterosCraft | Surface size, light, available materials |
| A public hall feels empty despite abundant detail | Blockworks | The player's task, destination and return point |

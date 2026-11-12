---
tags: [research, level-design, worldbuilding]
---
# AAA Level Design Case Studies

These accounts address different production decisions. The studio paragraphs describe the sources. The Minecraft exercises are our adaptations. Choose a case by the problem observed on the walk and keep the first experiment small enough to discard.

## Bethesda Game Studios — test the joints of a modular kit

In their *Skyrim* account, Joel Burgess and Nathan Purkeypile describe a corridor that comes back around to a room and misses its second opening. The error is hard to see when trying pieces in a straight line. They describe testing unusual joins early, while changing a footprint is still inexpensive. [Authors' illustrated GDC write-up](https://www.gamedeveloper.com/design/skyrim-s-modular-approach-to-level-design)

**Minecraft adaptation.** Write down whether a module owns its end wall, the center of its doorway, its floor elevation and the space taken by projecting eaves. Dry-fit a straight, corner, mirrored corner and stair around one yard. Close the loop against the *original* doorway. Stack an occupied room above the corner and inspect the stair from below. Rotate the whole assembly a quarter turn. A doubled post, vanishing tread, sideways stair or one-block shift gives you a reproducible failing join. Fix the module convention before adding a custom filler for this one yard.

**Example failure.** A five-block bay has pillars at local X 0 and 4. If the next bay shares the last pillar, its origin advances four blocks. A doorway at X 2 then repeats at X 6. A generator advancing by five creates adjacent posts at X 4 and 5 and places the next doorway at X 7. Both bays may look plausible in isolation. Stake the expected doorway centers before placing either roof. Apply this arithmetic only to kits that deliberately share posts.

## Blizzard — give a landform a reason to be entered and a way out

The Nagrand designers describe an early landscape of carefully composed hills that left the quest team with little to do there. Revisiting the canyons, they identified a different problem. A player who fell in had trouble getting out. They widened the canyons, gave them distinct areas and added entrances and exits. Older ogre paving crossed by newer Warsong paths also let a player see successive occupations of the same ground. [Blizzard's Nagrand team, *Artcraft – Level Design Part 4*](https://worldofwarcraft.blizzard.com/en-gb/news/16595426)

**Minecraft adaptation.** In an Ironspine quarry, draw three walks on the same section: a worker hauling stone out, a player descending deliberately, and someone who dropped to the lowest bench by accident. Mark what the last player sees after landing. Put an accessible ramp or stair at that elevation and expose its start from the pit floor. Keep the route recognizable after the ore has been mined. At one crossing, run a newer timber haulway over worn stone paving to show the change in use. Test the escape in ordinary player mode with block placement restricted, since a creative flight or a dirt pillar would conceal a trap.

## Guerrilla — let the work explain the settlement

Guerrilla's Living World team describes building tribal identity through materials, tools and behavior. Carrying water looks different across tribes. The Nora's isolation and hunting economy are visible in how their homes are built and what people keep inside. An object can identify both a community and a particular job within it. [Guerrilla, *Horizon Forbidden West: An authentic world*](https://blog.playstation.com/2021/11/22/horizon-forbidden-west-an-authentic-world/)

**Minecraft adaptation.** For one Saltreach fisher, trace landing the catch, weighing it, preserving it, and taking it to market. Leave room for a boat, a dripping basket at the wet end, a bench inside the receiving door, and dry storage above the waterline. Choose one prop that changes meaning across the sequence, such as an empty basket by the return stair and a full one beside the scale. Ask a tester where the fish came from and where they go next. If they see a generic warehouse, repair the relationship between door, water and workbench before increasing the number of props.

## Sucker Punch — indicate the destination and leave a route choice

In *Ghost of Tsushima*, the wind points toward an objective while the player finds the traversable path. The effects team also describes different distant cues for different activities, including smoke and steam. A cue that points straight at an objective can be confusing where the terrain interrupts it. [Sucker Punch, *How stunning visual effects bring Ghost of Tsushima to life*](https://blog.playstation.com/?p=345372)

**Minecraft adaptation.** Place the distant aqueduct above the roofline to orient the player. At the fork beside the water, change the paving and expose the next landing so the player can find the walkable stair. At the actual sluice door, use a copper wheel or beam profile that reads at arm's length. Switch particles off and repeat the walk in rain and at the project's normal render distance. If testers walk into the water while aiming at the landmark, adjust the near turn. Raising the aqueduct would strengthen the misleading cue.

## Naughty Dog — put a player in the rough space before commissioning the finish

The *Uncharted 2* postmortem describes designers turning an outline of the player experience into low-detail geometry, testing navigation, camera views and elapsed time with colleagues, then introducing interaction and enemies before final art. It also records how quickly early layouts could become costly commitments when story needs changed. [Team postmortem, *Game Developer*, March 2010](https://media.gdcvault.com/GD_Mag_Archives/GDM_March_2010.pdf)

**Minecraft adaptation.** Give a player one task and no tour. Record their first unprompted turn, where they stop, what they can see at the stop, and whether they find their way back. Run it once with plain blocks and again after adding rails, carts and lamps. If the furnished route fails, remove the first object that changed either the sightline or the walking width. For an MMO, repeat with two players passing each other at the interaction point. Mark any untested night or multiplayer case in [[Review Record Template]].

## Respawn — prototype one playable idea before building the whole level

Christopher Dionne describes how Respawn used small prototypes called action blocks to explore gameplay for *Titanfall 2*, including *Into the Abyss*. The public GDC session overview identifies the technique and level. The drill below is a Minecraft design exercise. [Dionne, GDC session and recording](https://www.gdcvault.com/play/1025105/Designing-Unforgettable-Titanfall-Single-Player)

**Minecraft adaptation.** For a flooded mine, build only a dry landing, a visible lower seam, a descent, and an exit back to daylight. Try two versions of the descent while keeping the ore and exit fixed. Give the player a tool and ask them to harvest one registered block and return. Record where they commit to a route, whether they can retreat past a companion, and whether a neighboring structural block survives the attempt. Establish the live harvest behavior separately from the visual test. A pretty seam and a functional resource node require different evidence. Keep the better descent and throw away the weaker prototype before dressing the mine.

## Decide what to fix first

If the kit misses a doorway, fix the join before playtesting the room. If players can walk it but choose the wrong route, test the cues at the decision point. If they arrive easily and still cannot explain the place, trace a resident's work through it. If everyone agrees about the design but the proposed interaction has never worked in Minecraft, isolate that interaction on a test plot. [[Saltreach Quay Study]] puts these decisions in sequence.

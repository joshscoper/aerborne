# Floating city spawn blueprint

## Direction

Use normal Minecraft Overworld generation and place an original floating arcane capital above the natural spawn region. `Aetherhold` is a working internal name only. The city should capture the useful floating-capital structure—clear districts, a portal concourse, civic services, and a dramatic view—without copying another game's layout, names, silhouettes, or landmarks.

This is not a separate hub world. The survival terrain, caves, structures, and resource distribution below and beyond the city remain vanilla-generated. The production and local server templates explicitly use `minecraft:normal` with empty generator settings.

## Physical layout

Build the main deck roughly 80–120 blocks above the highest terrain in the protected spawn footprint. Use a broad central island with smaller attached platforms rather than one solid disc. The underside needs a deliberate silhouette, support crystals or engines, and a safe catch route so it does not read as an unfinished build from below.

Recommended districts:

| District | Purpose | NPCs |
|---|---|---|
| Arrival Concourse | First login, navigation, profile, Warden contracts | Cassia Dawn, Aeron Vale, Cato Flint |
| Hall of Callings | Skills, masteries, jobs, parties | Pell Wren, Fen Calder, Nima Hart |
| Arcane Works | Forge, recipes, alchemy, enchants, identification, relics | Torren Pike, Sella Mire, Veyra Loom, Nyra Vell, Rook Salen |
| Grand Exchange | Bank, market, supplies, cosmetics | Orin Ledger, Orris Bell, Mara Venn, Vera Lune |
| Gate Ring | Routes, public destinations, claims, field activities, Delver contracts | Kestrel Way, Elian Quill, Gilda Penn, Hessa Clay, Ivo Reed, Bram Tallow, Garrick Mott |
| Archive Walk | Research intake and Scholar contracts | Lyra Senn |
| Citadel Steps | Campaign briefings and consensual PvP | Arlen Voss, Dorian Ash |
| Memorial Terrace | Graves and recovery guidance | Tamsin Wake |

Keep the arrival platform visually quiet. A new player should see the City Guide first, the service directory second, and no more than three competing paths. Use signs, color, architecture, and sightlines before floating text.

## Travel and survival connection

- Set NerdSMP spawn on the arrival concourse with `/setspawn`; first joins and non-bed respawns already use this location.
- Provide at least two safe physical descents to the natural world: a lift or portal to a protected ground station and a slower scenic route.
- Do not turn `/warps` into a free replacement for exploration. Public warps should represent major established destinations; ordinary wilderness access should leave through the ground stations, roads, or paid travel network.
- Put Nether and End access in their own visible gate structures, while retaining normal survival unlock requirements.
- Add a fall-safe recovery volume or reachable catch platform below the city. Never rely on barriers alone to solve accidental falls or Bedrock movement differences.

## Protection envelope

Protect the full city, underside, ground stations, portal exits, and a buffer around every arrival point. Deny building, breaking, containers, item frames, fluids, pistons, vehicles, hostile spawning, and claim overlap in the civic envelope. Keep interaction enabled only for intended NPCs, doors, workstations, and decorative controls. Roads leaving the ground stations need a narrower protected corridor before transitioning cleanly into claimable survival land.

## Manual NPC placement

NPC definitions never place themselves. For each finished counter or landmark:

1. Stand exactly where the NPC should be anchored and face the desired direction.
2. Run `/npc create <definition>`.
3. Run `/npc list` and record the short instance id on the build manifest.
4. Use `/npc movehere <instance>` for adjustments and `/npc remove <instance>` before retiring a location.
5. Run `/npc respawn` after a placement pass and test every dialogue action as a normal player.

Use `/npc definitions` for the current definition list. Do not place duplicate service NPCs merely to fill empty streets; background population should use separate ambient actors when that system is built.

The faction commissioners are `cato_flint` for the Frontier Wardens, `lyra_senn` for the Rift Scholars, and `garrick_mott` for the Free Delvers. Give each board enough clear space for two or three players to stop without blocking a main path.

## Launch gate

Before opening the city, verify Java and Bedrock arrival positions, fall recovery, portal return paths, NPC reach and line-of-sight, dialogue GUIs, every service action, grave relocation, hostile-spawn suppression, claim buffers, and `/spawn` during combat/cooldown conditions. Walk every route without flight and confirm a new player can reach the wilderness without knowing a command.

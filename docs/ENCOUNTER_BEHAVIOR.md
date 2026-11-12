# Managed creature behavior

Custom combat creatures, authored mobs, zone/expedition encounters and expedition allies share
`EncounterBehavior`. It is applied during creation, and managed creatures are repaired when
already-loaded mobs are discovered or their entity chunk loads. No recurring world-wide mob
scan is added.

- Managed undead do not ignite from daylight. Zombie and skeleton native sunlight flags are
  disabled, and a combustion-event fallback covers the Paper 26.2 tag-based sunlight path that
  bypasses the zombie API flag. Protection does not depend on wearing a helmet.
- Real block/lava and combat combustion events retain their normal handling. Fire ticks and
  environmental damage are not globally cleared or cancelled; this is not fire immunity.
- Piglins and hoglins retain their identities outside the Nether. Drowning, freezing, infection,
  curing and lightning cannot silently replace a managed encounter with another species and
  lose its authored identity. These protections do not remove drowning/freezing damage.
- Managed creatures cannot pick up equipment and change their loadout. Zombies cannot break
  doors; managed silverfish cannot burrow into terrain, and managed endermen cannot move blocks.
- Ordinary vanilla mobs retain their normal sunlight, conversion and terrain behavior. Slime
  splitting, ordinary metamorphosis, shearing and unknown/scripted transformation reasons are
  not blanket-cancelled.

The behavior marker is plugin-owned entity PDC; item identities, drop rolls, encounter stats,
safe-zone rules and spawn caps are unchanged. Existing sunlight fire ticks are not erased on
reload because they cannot safely be distinguished from combat or terrain fire.

Regression checks live in `EncounterBehaviorTest` and `scripts/verification/EncounterBehaviorProbe.java`.
The latter exercises the real Paper archetypes and a sunny-noon window with helmetless protected
undead, an ordinary zombie control and a protected creature deliberately set on fire.

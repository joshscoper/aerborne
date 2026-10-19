# Harvest progression and resource provenance

Mature wheat, carrots, potatoes, beetroot, nether wart, cocoa, sweet berry bushes,
and pitcher crops grant Farming XP when broken, including crops planted by players.
Each accepted harvest creates one operation ID shared by skill XP and job work.
Regrowing a crop at the same coordinate therefore grants XP again, while replaying
that harvest's operation remains idempotent.

The listener reads material and growth state on the server event thread. Immature
crops, fire, frost, cactus, sugar cane, bamboo, fruit stems, and immature torchflower
stalks do not qualify merely because their block data has an age property. Mature
decorative flowers and right-click harvesting need separate lifecycle handling and
are outside this break-event fix.

## Shared resource protection — September 2026 follow-up

Mining, forestry, excavation, geodes, talent/enchantment harvest bonuses and
daily mining/woodcutting contracts now share one block eligibility decision.
The three separate in-memory placement trackers have been removed. The daily
quest reset no longer makes placed resources eligible again at midnight.

The record belongs to the world chunk, not to the player who placed or broke the
block. Moving between accounts cannot bypass it. Placement includes creative-mode
blocks and every coordinate of multi-block placement. Harvesting a marked block
does not clear its marker. Removing, replanting or transforming that block cannot
turn it into a fresh natural resource merely by reusing the coordinate.

The same accepted break has one shared operation ID and separate single-use reward
channels for base skills, excavation finds and daily quests. Base XP and job work
share that ID; excavation bonus XP has a distinct prefix so it does not suppress
base XP. New legitimate harvests receive new IDs rather than permanently reusing
coordinate-based XP receipts. Creative, spectator, cancelled, drop-disabled and
not-yet-loaded account breaks do not grant these gathering rewards.

The soil catalog is also shared: coarse/rooted dirt, suspicious sand/gravel and
soul sand now receive the same base Excavation XP treatment as other eligible
digging blocks. Woodcutting contracts recognize real logs and Nether fungus stems,
not pumpkin or melon stems. Building XP keeps its separate daily-coordinate rule.

## Movement and renewal

- Piston sources and destinations are marked, including movement across chunks.
  Falling blocks and Enderman block changes are marked too. This is conservative:
  piston-moved natural ore and fallen natural sand/gravel no longer grant gathering
  bonuses. Their normal survival drops are not removed by this system.
- Stripping logs, soil conversion, washout and explosions do not clear provenance.
  Generic block-form/spread/fade events are not treated as proof of a new resource.
- Tree growth schedules a next-tick verification. A candidate must remain in the
  accepted growth state list, have the expected actual block data, and have no
  intervening placement, movement or harvest. Pre-existing logs do not renew just
  because a tree grew around them.
- Bonemeal's later fertilize event can cancel or filter the earlier tree event;
  dispenser bonemeal is handled too. Only verified generated log coordinates renew.
  Regrowing trees at the same location can therefore award Forestry XP and quest
  progress again without enabling a place-and-break log loop.
- Mature farm crops remain intentionally renewable, including player-planted crops.

## Storage and limits

Markers use Paper's supported [chunk persistent data container](https://docs.papermc.io/paper/dev/pdc/).
Each 16x16x16 section stores a versioned, sorted long array under
`nerdsmp:resource_cells_<section-y>`, with negative section Y supported. Each update
touches only the relevant section rather than rewriting a global YAML file.
Duplicate/corrupt/out-of-section entries stop rewards for that section and log an
error; they are not silently discarded or reset to an empty record.

Paper persists these markers with normal chunk/world saves. An orderly saved
restart retains them, and daily quest rotation does not touch them. They are not
an immediate transaction with skill/economy files: a hard crash, world rollback or
inconsistent backup can still rewind world provenance independently of rewards.
Back up worlds and player/ledger data consistently. A full season world wipe
removes its markers naturally; merely changing the season ID does not erase them.

There is no trustworthy record of placements made before this implementation.
Existing player builds cannot be retroactively classified as natural or placed.
Map editors, restoration tools and future excavation-site/arcane-vein resets need
explicit provenance integration; no command to blanket-clear protection is added.
Trees interrupted before deferred verification conservatively retain their markers.

## Live acceptance checks still required

1. Place ore, logs and excavation soil; save, stop and restart. Break them with
   another account. Confirm no skill XP, job work, quest progress, geodes or finds.
2. Dig fresh natural terrain and check base XP and normal excavation chains. Confirm
   a find's bonus XP does not suppress the same break's base XP.
3. Harvest, replant and grow a tree in the same footprint. Repeat naturally, with
   player bonemeal and dispenser bonemeal; check Forestry XP and woodcutting quests.
4. Cancel or restrict growth using protected land; already-placed logs must not
   become eligible. Repeat with piston, falling-block and Enderman movement.
5. Repeat mature crop harvests and check Farming XP. Break immature crops and
   pumpkin/melon stems; they must not count as woodcutting.
6. Test chunk unload/reload and negative coordinates. Profile high-volume digging,
   building and large tree growth before opening the public season.

Unit tests and a console startup do not replace these real Java/Bedrock journeys.

## Verification — September 24, 2026

- `gradlew.bat --no-daemon qualityGate build` passed: **314 tests**, zero failures,
  errors or skipped tests. This includes 49 resource-provenance tests and four
  market-interface policy tests added since the September 13 market build.
- Resource tests cover immutable/validated section records, shared single-use
  reward channels, service recreation with retained chunk data, corrupt and failed
  writes, game modes, cancelled events, multi-block placement, piston direction,
  falling blocks, Endermen, mature replanted crops, and deferred natural/player/
  dispenser tree growth. They use event proxies, not a running game client.
- The market interface now explains why outstanding listings prevent moving a
  stall. Payment retry is offered only to transaction participants, not unrelated
  staff inspecting an entry or anyone awaiting inventory-transfer review.
- `run-test-server.cmd -PrepareOnly` passed. Built and staged jar SHA-256:
  `62458DECC79ADFCD3189D231E0964D4D8198214D2797CDEE034C9E96F0355561`.
- Local Paper 26.2 build 121 / Java 25 startup passed. NerdSMP enabled, the existing
  market loaded one listing with no pending recovery/review, and Nexo generated
  and uploaded its pack using the previously approved hosting configuration.
  `help skills`, `help excavation`, `help quests` and `market review` resolved.
- Clean shutdown exited with code 0. No players joined; all six backed-up player
  save files (NerdSMP YAML and Minecraft saves) retained their hashes.
- The existing Windows OSHI/Perflib counter error remains; there was no NerdSMP
  initialization or shutdown exception. The pinned Paper build was not upgraded.

The server is stopped. Start it with `run-test-server.cmd` and join
`127.0.0.1:25565`. Before staging, the previous jar, NerdSMP data, server properties,
Minecraft player files and replaced generated Nexo source pack were backed up to
`runtime/review-backups/harvest-20260924-061648/`. The same directory contains
`smoke-startup.log`. No world wipe, account reset or market resolution was performed.

PostgreSQL, live Java/Bedrock harvesting, abrupt-crash reconciliation and load
testing remain unverified. This pass does not close the other release blockers
in the September code review.

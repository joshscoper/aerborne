# Watchdog anti-cheat

Watchdog is NerdSMP's own anti-cheat. It needs no third-party plugin and works in three layers:

1. **Packet checks:** it reads every packet a player sends.
2. **Physics and geometry checks:** it compares what each player does against vanilla's rules.
3. **Combat model:** a machine-learning model that learns from fights your staff label.

All three feed the same evidence log, alerts and Staff Console.

## How it stays accurate

- **Buffered checks.** A single odd tick never flags anyone. Each check builds a buffer that grows with impossible behaviour and shrinks with normal play. A player is flagged only when the buffer passes a threshold. Lag spikes, edge cases and one-off glitches decay away; sustained cheating does not.
- **Vanilla movement, replayed tick by tick.** For every client tick, Watchdog re-runs vanilla's movement code against the real world and checks whether any input the player could have pressed explains where the client says it went. See [Movement simulation](#movement-simulation).
- **Vanilla numbers.** The player's live attribute values are read directly: speed, sneaking speed, gravity, jump strength, step height and movement efficiency (Soul Speed), including potions, boots and other modifiers.
- **Exact client timing.** Since 1.21.2 the client sends an end-of-tick packet every tick. Watchdog counts those, so it knows exactly how many client ticks each move covered and how fast the client's clock really runs. Network jitter and server lag don't affect this.
- **Lag compensation for combat.** For every hit, Watchdog considers:
  - every position the target held within the attacker's ping
  - the camera's position between ticks
  - every rotation between the attack and the next tick

  It then checks whether any combination puts the crosshair inside the hitbox within reach.
- **Situations it doesn't model.** These pause movement prediction instead of guessing:
  - water, lava and bubble columns
  - climbing, cobwebs, powder snow, scaffolding, big dripleaf and sliding down the side of honey
  - slime and bed bounces, shulker boxes opening
  - elytra, riptide, levitation and vehicles
  - knockback (including explosions and wind charges), teleports, pistons and crowding
- **Latency-aware world.** Some changes make a move uncertain. An uncertain move that doesn't match re-synchronises; it never counts. The changes are:
  - a block placed, broken, exploded, melted, formed, burnt, toggled by redstone or opened by hand within the player's round trip. Cancelled places and breaks count too, because the client briefly saw the ghost block.
  - a chunk the server hasn't loaded
  - a speed or jump attribute that changed within the player's round trip
- **Unit-tested against vanilla.** These are verified never to flag:
  - jump arcs, sprinting, sprint-jumps, Speed II and Slow Falling
  - walking into walls, stepping onto slabs and up stairs
  - sneaking to a block edge and stepping off it
  - ice, soul sand and honey

  The timer check is tested against a two-second freeze, vanilla's catch-up burst and a 250 ms ping drop.

## Checks

| Check | What it catches | How |
|---|---|---|
| Timer | Game sped up (timer, "blink" catch-up abuse) | End-of-tick arrival rate over 10 s, and a 3 s window for blatant cases |
| Bad packets | Impossible pitch, invalid coordinates, attacking yourself, attacking without swinging | Packet contents and ordering |
| Flight | Fly, hover, glide, high jump, tall step, fast fall, full jumps off honey | Vertical offset from the closest vanilla replay of each client tick |
| Speed | Speed hacks down to about 1.05x, strafe, bhop boosts, no-slow on soul sand and honey, phasing into walls | Horizontal offset from the closest vanilla replay of each client tick |
| Reach | Hitting from too far | Ray distance to lag-compensated hitboxes, against the player's interaction-range attribute |
| Hitbox | Kill aura, hitting what you're not looking at | No crosshair ray within the player's latency window passes through the target |
| Autoclicker | Fixed-delay clickers | Click intervals counted in client ticks, which removes jitter; machine-regular rhythm flags |
| Fast break | Nuker and fast-mine | Client ticks between dig start and finish, against the block's real break speed |
| Model | Everything combat-related that rules miss | See below |

Rule checks add to a player's suspicion score. When the score is high enough, Watchdog moves the player back to their last safe position. It kicks only on repeated evidence across checks. **Rule checks never ban.**

## Movement simulation

Watchdog's movement replay works like Grim's. It lives in `physics/MovementSimulator` and runs on the main thread from `PlayerMoveEvent`, so every block it reads comes from the server's own world, safely.

### What happens each client tick

1. **Reconstruct the client's ticks.** The packet layer records every position the client sent, with its client-tick count and yaw. Vanilla sends nothing while moving less than 0.0002 blocks a tick, so skipped ticks are replayed as near-still ticks.
2. **Try every input.** The candidates are:
   - the eight WASD directions or no keys
   - sprint (only with forward held, as in vanilla)
   - sneaking and item use (only while the server has recently seen them)
   - jump (only on the ground)

   That's 24 candidates on flat ground, and up to about 150 while sneaking and using an item.
3. **Replay vanilla's tick for each candidate.** In order:
   - the tiny-velocity clamp
   - the jump, with the block's jump factor and the sprint-jump boost
   - input acceleration scaled by the friction of the supporting block, using vanilla's float maths and its 65,536-entry sine table
   - sneak edge back-off
   - axis-by-axis collision against the real block collision shapes (slabs, stairs, fences and so on), with step-up
   - the block speed factor (soul sand and honey are 0.4)
   - gravity, drag and friction
4. **Judge.** The candidate that lands closest to the reported position explains the tick. If no candidate is within **0.001 blocks**, the tick is a violation.
   - Horizontal offsets feed the Speed check and vertical offsets feed the Flight check, through the same decaying buffers as every other check.
   - Moving *less* than vanilla allows counts at a quarter of the weight, because it gains nothing. It still counts, so a strafe that keeps the same speed is caught.
5. **Carry ambiguity forward.** Two inputs can leave a player in the same place with different velocities: for example, holding W against a sneak edge, or not. Up to four such states are kept until a later tick tells them apart. After a violation the state re-synchronises to what the client actually did, so one odd tick never cascades.

Sometimes the per-packet path isn't available: the packet layer is off on an unsupported server version, or the server rewrote the move. Then the earlier bounding model (`physics/MotionModel`) takes over. Set `anti-cheat.movement-simulation: false` to always use the bounding model.

### Assumptions to confirm during the alerts-only week

These follow the newest vanilla code we know of. If one is wrong for this game version, legitimate players will show small, repeated Speed or Flight offsets in exactly that situation. Watch the evidence log during the rollout week and correct `MovementSimulator` if needed.

- **Input scaling.** Input is scaled by 0.98 exactly once. Slowed diagonal input uses the 1.21.5 square-to-circle correction; the older scaling is also accepted, since it's never faster.
- **Tiny-velocity clamp.** Players clamp tiny horizontal velocity by its length. The older per-axis clamp is also tried whenever the two differ.
- **Sprinting.** Sprinting needs forward input, but is allowed while sneaking or using an item. That's lenient on purpose.

## Combat model

Each burst of fighting becomes a combat window. A window closes after 15 seconds or 30 hits; windows with fewer than six hits are discarded. Each window records 22 measurements:

- click rhythm
- turn speed, jerkiness and smoothing
- the mouse-step pattern
- snap-to-target turns
- how close to the target's centre the player aims
- reach
- hitbox misses
- missing swings
- attack rate
- target switching
- ping
- rule flags

The model is **gradient-boosted decision trees**. They're trained on the server, with no external service involved. Every verdict lists the measurements that pushed it towards "cheating".

Before anything is labelled, the Staff Console shows how unusual each fight is compared with everyone else. That score is advisory only and never acts.

### Training it

1. When staff confirm a player was cheating, open their profile, go to the **Watchdog** tab and choose **Label recent fights as cheating**. You can also run `/watchdog label <player> cheat [hours]`.
2. Label normal players as **legit** too. The model needs both kinds of example.
3. It retrains automatically a minute after the last label, or straight away with **Train now** or `/watchdog train`.

Validation keeps each player's fights together, so the model is always scored on **players it has never seen**. That's the honest test of whether it will catch a new cheater without hurting a new legitimate player.

### Autonomy

With `anti-cheat.ml.autonomous: true` (the default), the model kicks and then bans on its own. It does this only while its latest training run meets **all** of these conditions:

- at least 100 cheating fights from 10 players
- at least 300 legitimate fights from 30 players
- at least **99.5% precision** and 25% recall on unseen players

Until then it is advisory: it scores and explains but never acts. The Combat model panel shows progress against each requirement. If you raise the requirements in config, they apply immediately without retraining.

When autonomous, it works as a ladder:

1. Two flagged fights within 10 minutes → kick.
2. Another flagged fight within 24 hours of that kick → ban, for 7 days by default.

Every action goes into the audit log and the player's history as **Watchdog AI**, with the model's confidence and reasons. Wrong calls can be undone with Unban. Label the player's fights **legit** so the model learns from the mistake.

Settings are under `anti-cheat.ml` in `config.yml`. Data is stored in `plugins/NerdSMP/security/watchdog-ml/`:

- `windows.jsonl`: every combat window and its label
- `model.json`: the trained model and its validation results

## Recommended rollout

1. Run the new rule checks for a week with `anti-cheat.mode: alerts-only`. Read the Watchdog view and confirm normal players stay clean on your terrain, ping mix and plugins. Then switch back to `autonomous`.
2. Label fights as staff confirm cases. Label plenty of ordinary players as legit.
3. The model can act on its own only once its own validation clears the bar.

## Limits

- **Movement simulation covers walking, sprinting, sneaking, jumping and falling on solid blocks.** Water, lava, climbing, elytra, vehicles and the other states listed above still pause it instead of being replayed. Replaying those would be the next phase.
- **Entities aren't part of the collision world.** Boats, shulkers and crowds pause the check instead of being collided with.
- **The world is the server's, not the client's.** Moves near recent block changes or unloaded chunks aren't judged. Watchdog doesn't replay the client's delayed view of the world.
- **Bedrock players** joining through Geyser aren't specially handled yet.
- **The model only knows what it's taught.** Accuracy depends on how many and how varied the confirmed cases are.
- **Version-specific packet hook.** The packet layer hooks the server's own network pipeline using this Paper version's class names. On a version where they differ, it switches itself off with a warning, and the event-based checks keep running.

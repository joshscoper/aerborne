# Island mob stacking

Ordinary compatible hostiles share one body on a private island. The shared RPG map,
authored mobs, elites, bosses, livestock, pets, named mobs, riders, leashed mobs and
modified equipment remain separate. A body holds at most 16 members. Each actual
death runs the existing loot and XP pipeline once, then continues with one fewer
member. Gear, coin purses and loot-profile rewards are never multiplied by a stack.

## Recovery and merging

- The merge radius is six blocks, including across an already-loaded chunk boundary
  inside the same island. Island identity, species, level, combat scale, reward scale
  and ordinary weapon must match exactly.
- Hurt, burning, frozen, potion-affected or otherwise modified mobs do not merge.
  Loaded chunks are revisited so temporary blockers and moving mobs can be retried.
  Native random attribute modifiers and naturally equipped mobs stay separate; the
  system does not silently erase their different attributes or gear.
- Existing over-cap grinders can consolidate within their own chunk. This reduces
  physical bodies without adding represented members. The previous implementation
  refused such merges, leaving the most crowded grinders unable to recover.
- Spawner production and cross-chunk imports remain capped at 256 represented
  members in the destination chunk. Unknown populations in a dense snapshot block
  imports, not same-chunk consolidation. Stacked spawner rolls reserve all accepted
  members before processing; a rapid grinder death retains the pending roll.
- A queue no longer discards a living candidate merely because it waited five
  seconds. Backpressure cancels an additional spawner roll if the pending queue is
  full, rather than accepting a multi-member roll that cannot be retained.

## Bounded work

Every ten ticks, stacking processes at most 32 queued candidates, 1,024 entity
inspections and eight chunk snapshots globally. The inspection counter includes
every raw snapshot entry (including dropped items) and every nearby living-body
check, rather than hiding raw scans outside the budget. It also stops at an
approximately two-millisecond **soft** time budget. A single Bukkit chunk snapshot
or entity/API call cannot be preempted, so this is not a hard two-millisecond TPS
guarantee. No chunk tickets or unloaded-chunk requests are created.

The server tracks island chunks already loaded at startup and subsequent chunk
loads. A rotating scan inspects up to 64 relevant bodies from one chunk per pass
(every fourth pass when spawn work is waiting). Raw entity windows rotate, including
in chunks containing more than 512 physical entities. Chunk unloads release the
queue and cursor references. Pending multi-spawner rolls are written to their
body before chunk unload or orderly shutdown. Busy chunks are not globally
rescanned every tick.
Reconciliation latency increases with the number of loaded island chunks; it is a
recovery path, while new spawn candidates enter the priority queue immediately.

Spawner callbacks share a per-tick cached chunk population budget, capped at 256
spawning chunks per tick. Loaded reconciliation tracks at most 32,768 island
chunks; reaching this guard reports a diagnostic rather than growing forever.
Spawner callbacks no longer
walk the entire pending queue and full chunk separately for every spawner member.
Chunk hoppers keep their existing separate collection budget and ownership filters.

## Staff diagnostics

`/island performance` (staff) includes actual merged body/member counters, queued
work, loaded-chunk tracking, the previous pass duration and skip reasons. The web
telemetry integration can publish `IslandAutomation.stackDiagnostics()` from its
main-thread snapshot; it contains immutable counters, not entity references.

Skip counters are cumulative observations, not unique mob counts. Common reasons
include `hurt`, `active_effect`, `equipped`, `attribute_modifiers`,
`authored_or_elite`, `foreign_metadata`, `no_compatible_neighbor`, `pass_budget`,
`spawner_population_cap` and `spawner_queue_full`. Do not remove foreign metadata or
force elites into stacks to make a diagnostic counter disappear.

## Verification boundaries

Policy tests cover crowded consolidation, imports, numeric overflow and work
limits. The dedicated Paper fixture checks loaded cross-chunk merging, retry after
a temporary blocker, member-preserving crowded consolidation and immutable budget
telemetry, in addition to the existing one-death/one-loot and successor checks.
These are controlled integration checks, not a 100-player load certification.

Existing death-to-successor handoff is deferred by one tick and is not
transactionally crash-proof. An abrupt process crash in that window can lose the
remaining members. No inventory or island/player data is reset by this update.

# MMORPG hostile spawn recovery

Date: 2026-10-22

This records the hostile-spawn recovery release. The subsequent build is recorded in
[Encounter behavior verification](ENCOUNTER_BEHAVIOR_VERIFICATION.md).

Release: `NerdSMP-Paper.jar`, 6,688,945 bytes.
SHA-256: `c8a0f418a17e4a0d4d21cf7c713edfafe06dfe64b148b8403bd157933bf9e58a`.
The Gradle build, release and final disposable-server jar hashes match.

## Cause reproduced

The shipped MMORPG map was saved on Peaceful. `WorldPackService.ensureDifficulty` previously
returned immediately if `packs.<id>.<world>.difficulty-raised` was true. After a later Peaceful
reset, that permanent migration flag blocked recovery. Passive creatures were still permitted,
but Paper rejected a hostile zone spawn with:

`IllegalStateException: Cannot spawn monster for org.bukkit.entity.Zombie in Peaceful difficulty`

The fix repairs Peaceful on matched pack worlds at startup despite the old flag, and checks
those matched worlds every five seconds for subsequent resets. Easy/Normal/Hard remain intact.
`world-packs.maintain-combat-difficulty: false` preserves the previous one-time policy for
operators who intentionally want an already-applied world to remain Peaceful. Safe zones,
natural-monster suppression, activation requirements, spawn validation and caps are unchanged.

## Validation

- 53 targeted world-pack, zone and mob-scaling tests passed, zero failures/skips.
  Build and shadow jar succeeded. Log: `runtime/hostile-spawn-build.log`.
- Native Paper 26.2 build 121: **PASS 161 checks**, including ten hostile-spawn checks.
  Log: `runtime/hostile-spawn-probe.log`.
  Result: `runtime/metallurgy-probe-20261022-222507/metallurgy-probe-result.txt`.
- The regression fixture sets the old repair flag, confirms a cow can spawn while a zombie is
  rejected on Peaceful, invokes startup recovery, then successfully spawns a zombie, skeleton,
  spider and witch. It also checks later-reset recovery, Hard preservation and the opt-out.
- Existing native metallurgy, stats and item-stacking checks pass in the same run.
- `git diff --check` passed for the implementation, configuration, guide and probe sources.

This reproduces a confirmed plugin failure path matching the reported hostile-only symptom.
The running MMORPG server folder/log was not supplied, so its current live difficulty and
whether another plugin resets it have not been inspected. The probe used a disposable loopback
server, and no running production server was modified or restarted. Install the release jar
and restart normally to activate the recovery guard.

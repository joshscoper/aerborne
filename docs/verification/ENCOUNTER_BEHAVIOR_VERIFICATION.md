# Encounter behavior verification

Date: 2026-10-22

Release: `NerdSMP-Paper.jar`, 6,692,050 bytes.
SHA-256: `3b46fede1e8741fbe0dbd4cc8659afcd678956e551954ea91654512a1882284c`.
Release, Gradle output and final probe jar hashes match.

- 60 targeted mob, zone and world-pack tests passed, with no failures/errors/skips.
  Log: `runtime/encounter-behavior-build.log`.
- Native Paper 26.2 build 121: **PASS 223 checks**, including 62 new behavior checks.
  Log: `runtime/encounter-behavior-probe.log`.
  Result: `runtime/metallurgy-probe-20261022-222507/metallurgy-probe-result.txt`.
- `git diff --check` passed for changed implementation, tests, probe sources and guide.

The native probe spawns all 50 reviewed ground-mob archetypes with the shared behavior applied.
It checks sunlight flags, fixed loadouts/doors, direct custom-spawn protection, entity-load
repair, piglin/hoglin immunity, and conversion cancellation for managed versus ordinary mobs.

Helmetless managed zombies, a skeleton and a custom scarecrow remain unburnt across an actual
sunny-noon native tick window. An ordinary vanilla zombie in the same window burns. A protected
zombie deliberately set on fire loses health, and block/lava and combat combustion events remain
uncancelled. No general fire immunity was added.

The first native daylight run exposed a real Paper 26.2 compatibility issue: the zombie API's
`shouldBurnInDay=false` did not stop ignition. Local server bytecode confirmed that
`Mob.burnUndead` uses `BURN_IN_DAYLIGHT` and calls `igniteForSeconds`, bypassing the zombie flag.
The final implementation retains the native flags and adds a managed-entity fallback for the
base sunlight combustion event, excluding block and entity combustion subclasses. The native
sunlight regression passes with this fallback. Test creatures are explicitly persistent so
the absence of connected players cannot despawn them during the observation window.

The probe used a disposable loopback server and shut down after completion. No live server was
restarted or given a replacement plugin. These checks cover the reviewed behavior policy;
they do not guarantee unrelated future encounter bugs cannot occur.

See [Managed creature behavior](../ENCOUNTER_BEHAVIOR.md).

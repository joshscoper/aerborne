# Highcrown playtest repairs — 24 October 2026

This change follows `output/playtest-20261024/report.md`. The original report and raw evidence remain the historical baseline. The working tree already contained unrelated changes; these repairs preserve that work.

## Implemented repairs

| Finding | Repair | Regression coverage |
| --- | --- | --- |
| Ordinary tutorial levers blocked by claims | Claim protection delegates authored lever/button/plate use only to a present member of the active run, in its instance and active room, outside cutscenes. It grants no build or general redstone permission. | Real Paper events for member, outsider, unrelated lever and left-click; native Mineflayer activation of all three tutorial levers. |
| Patrolling Ottar rejects conversation | Range checks, `/npc talk`, dialogue/shop context and nearby distances use the valid live body. Account, current player session, anchor identity, body identity tag, world, distance and line-of-sight guards remain enforced. | Displaced-body conversation plus remote-anchor, wrong-account, stale-anchor and wrong-body-tag refusals. |
| Reconnect timeout closes remaining party's run | Snapshot expired UUIDs before invoking detach; remove deadlines outside traversal. Keep the offline member's return record. | Unit tests for multiple expiries, repeated cleanup and rejoin; live two-member normal run with an accelerated offline timestamp and a remaining online player. |
| Solved sequences throw; solved targets later reset | Track solved puzzle state, reject completed inputs before indexing, retire target timers, suppress stale visual cues and reset puzzle state on a wipe. | Real Paper sequence presses repeated 20 times; solved/incomplete target windows; replay after explicit reset. |
| Transient Windows save failures | Retry atomic replacement after access denial with 10/20/40/80 ms delays; five attempts maximum. Log recovery and exhaustion. Keep the atomic-move fallback and clean up the temporary file after replacement attempts. | Injected sharing denials, bounded permanent failure and unrelated I/O failure. |
| Progression memory/receipt could change before failed save | Copy the candidate progression state; write XP and operation identity together; publish both only after successful persistence. Apply the same order to talents, mastery selection and respec. Mark an account loaded only after a successful read. | Failed XP write, retry of same operation, restart deduplication, and failed talent/mastery/respec publication. Existing economy, island, market and trade failure tests remain enabled. |
| Fresh NPC placement can block startup on chunk I/O | Asynchronously prepare NPC chunks before initial world-pack application. Load and respawn saved NPC anchors one at a time across scheduler ticks. Guard obsolete reload callbacks and duplicate bodies. | Fresh-map startups with the normal 60,000-ms watchdog and all 94 NPC bodies present. |
| Protected landing silently rejects building/machines | Give a clear explanation for player placement, breaking and bucket attempts inside the protected 3-by-3 landing. | Small presentation change; protection geometry and outside placement rules are retained. |

Puzzle projectile handling also requires the shooter to be present in their own run; a projectile from an outsider cannot advance that run's puzzle. Deferred block callbacks recheck membership before acting.

## Combat reward correction and candidate tuning

RPG hunting XP now has a separate path for controlled zone, expedition and custom encounters. It no longer depends on vanilla orb drops for these encounters. Ordinary natural/farm mobs retain the existing fallback and receive no new managed-encounter multiplier. Mechanic-objective exclusions, kill fingerprints and the existing skill cap remain in place.

The candidate balanced defaults are in `progression.xp.hunting-managed`:

```yaml
base: 35
per-level: 5
elite-multiplier: 1.5
boss-multiplier: 3.0
```

Normal encounter XP is `35 + 5 × level`: 40 at level 1 and 135 at level 20. The level-20 elite amount is 203 after rounding; the boss amount is 405. Boss and elite multipliers do not stack. Configuration values and final rewards are bounded. A legacy `content/mobs.yml` entry may set `hunting-experience` to a final RPG XP amount, including zero; this does not change vanilla orb XP. The newer Mob Studio blueprint's existing `experienceReward` continues to control its vanilla orb reward separately.

All shipped world and expedition creatures now declare `rank`. Ordinary Goldenreach and tutorial-wave enemies are normal; the Gilded Overseer, arena Champion, tutorial Drillmaster and designated tougher expedition creatures remain elites. Boss flags retain boss behavior. This corrects more than the label: ordinary creatures no longer inherit elite stat scaling or an automatic random elite ability. Unmarked operator legacy entries keep the previous elite default for compatibility.

This implements the report's candidate curve, **not a claim that its A5/A10/hour targets have been met**. The earlier cohort had weapon, food and navigation confounds. Fishing/excavation rewards, all story reward budgets, spawn caps and later bosses are not arbitrarily multiplied or nerfed. The proposed 115–140k story budget still needs arc-by-arc authoring and matched activity/campaign validation. The three uncleared bosses and untested transaction/profession cases were coverage gaps, not demonstrated defects.

## Reproduction

Sources: `scripts/verification/PlaytestRepairProbe.java` and `testing/full-playtest-20261024/repair_probe/`.

1. Build with Java 25: `gradlew.bat qualityGate :nerdsmp-paper:shadowJar --offline`.
2. `setup.py --fresh N` creates a new isolated runtime, refuses overwrites, and extracts the original map without its author's player state. `N` is 1–4; the first run has no suffix. Existing owner-approved server EULA acceptance is reused.
3. Compile the probe against the plugin JAR, cached Paper API and fixture `libraries`. Package it with the probe `plugin.yml`, and copy both JARs into that runtime's `plugins` directory.
4. `run.py --fresh N` runs the full probe; `--startup-only` runs placement/startup checks. Bind is `127.0.0.1:25598`, RCON `25599`; the public API is disabled. The runner has a hard deadline and observes shutdown. Never point it at a production server.

The full probe uses three offline-admission Mineflayer accounts through protocol translation. It skips the unrelated opening camera/jump course, relocates players for bounded checks, and accelerates only a disconnected member's stored timeout timestamp. Its death-reward receipt uses a fixture-assigned killer and native server death, isolating award routing from hub combat protection; it is not an earned combat-rate measurement. All tutorial switch activations retain ordinary claim permissions. No currency or progression is seeded.

The initial sandbox launch could not migrate the fresh map; it was stopped before gameplay. A preliminary probe also stopped at an invalid hub combat fixture, and its older client protocol produced decode errors. These attempts are retained as failures and excluded from final pass claims. The corrected probe uses protocol 26.1, matching the original playtest harness.

Final results and remaining limits follow.

## Final validation

- Repository `qualityGate` and `shadowJar`: passed. Paper tests: 1,658; zero failures/errors; 1 existing skip.
- Live Paper/Mineflayer probe: **32/32 checks passed**, including the actual Ottar dialogue header, three native lever toggles, solved-puzzle replay and preserved two-player instance after expiry.
- Native Windows file-sharing probe: **4/4 checks passed**. A real delete-sharing denial produced a logged retry/recovery; a retained lock exhausted five attempts without changing the committed document.
- Final candidate: **3/3 fresh-map startups**, all 94 NPCs placed, 60,000-ms watchdog, no watchdog symptoms, each observed clean shutdown with exit 0. Fresh map includes original-map conversion; this is not an uncached-disk or player-capacity benchmark.

| Fresh fixture | Paper native Done | All 94 NPCs ready (MDT) | Shutdown |
| --- | --- | --- | --- |
| 2 | 52.808 s | 17:37:44 | exit 0 |
| 3 | 63.241 s | 17:40:20 | exit 0 |
| 4 | 56.115 s | 17:42:11 | exit 0 |

Paper `Done` precedes asynchronous NPC readiness. The separate ready timestamps prevent startup duration from being mistaken for full content readiness. The old-map migration includes Paper's own 30-second migration warning delay.

Candidate SHA-256: `bf0918fe88cda4a57787e5e780079bdd78734549a0914abe49f9cf40d7242c6b`.

Artifacts: `output/playtest-repairs-20261024/highcrown-playtest-repairs-20261024.jar`, `validation.json`, `repair-evidence.zip`, and a copy of these repair notes. The JAR is built from the current working tree, including pre-existing changes; it has not been deployed to a production server. All disposable servers and bots are stopped.

Remaining release evidence: matched activity/cohort pacing, normal-player campaign completion, the three previously uncleared bosses, and the profession/commerce fault cases listed in the original report. These checks do not certify power-loss recovery, SQL behavior, human usability or final season balance. No undocumented passes are inferred from static references or assisted coverage.

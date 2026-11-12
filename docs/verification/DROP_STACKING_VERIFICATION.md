# Drop stacking verification

Date: 2026-10-22

This records the drop-stacking release. The subsequent build is recorded in
[Hostile spawn verification](HOSTILE_SPAWN_VERIFICATION.md).

Release: `NerdSMP-Paper.jar`, 6,688,649 bytes.
SHA-256: `098c26a19114fb816d27626f99fc554233f3b9f991e732a4b60e6a96380a8816`.
The release, Gradle output and final probe plugin hashes match.

- Full Paper test suite: 1,632 tests total, 1,631 passed, one skipped, no failures/errors.
  Log: `runtime/drop-stacking-build.log`.
- Final age-zero correction compiled and packaged successfully.
  Log: `runtime/drop-stacking-package.log`.
- Native Paper 26.2 build 121: **PASS 151 checks**, including eight new drop checks and
  the existing metallurgy/player-stats checks.
  Log: `runtime/drop-stacking-probe-final.log`.
  Result: `runtime/metallurgy-probe-20261022-222507/metallurgy-probe-result.txt`.
- `git diff --check` passed for the implementation, verification sources and guide.

The drop fixture verifies that 130 identical single rewards pack into three legal stacks;
input stacks remain unchanged and different metadata remains separate. Real spawned Paper
items verify an 80-item merge preserves all 80 within stack limits, keeps the longer pickup
delay/oldest age, separates owners, respects merge-event cancellation, and combines two
matching single items into one physical entity.

The native check found Paper rejects `setTicksLived(0)` for fresh entities. The implementation
now only sets an older age when one actually exists, before transferring quantities. The
fresh-entity case passes on the final jar. The fixture explicitly loads its test chunk;
production consolidation never forces chunk loading.

The probe runs in a disposable loopback server and shuts itself down. No existing live server
was changed or restarted. No Minecraft client FPS benchmark was performed. Distinct gear and
unique currencies remain separate; this change reduces compatible item piles rather than
deleting rewards or bypassing normal stack limits.

See [Drop stacking](../DROP_STACKING.md) for behavior, queue budgets and configuration.

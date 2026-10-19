# Job recovery compatibility fix — 2026-10-17

## Cause and change

`DiminishingJobService` schema 1 was originally saved with nine skill entries. Adding Cooking extended `Skill.values()`, but the reader required counters for every current skill and rejected otherwise valid older saves with `Missing or malformed job counters` during login recovery.

The reader now permits an entirely absent `COOKING` entry in schema 1. Cooking starts at zero, using the existing missing-value behavior; the next normal successful job-state write includes its counters. Loading alone does not rewrite the document or reset any job state. All existing skills still require their counters, and partially present Cooking counters remain invalid. Validation errors now identify the offending skill.

Existing work allowances, earned Marks, fractional bonus carry, activity fingerprints, pending payment keys and unrelated player progress are preserved. Pending credits still use the same economy idempotency keys. Do not delete job state or player files to work around this error.

## Verification

- Targeted `net.nerdsmp.paper.job.*` run: **11 passed**.
- Full `gradlew.bat --offline qualityGate build --console=plain`: **BUILD SUCCESSFUL**, **1,292 tests**, **1,291 passed**, **1 skipped**, **0 failures/errors**.
- Regression coverage: pre-Cooking load is byte-for-byte read-only; existing daily caps and activity deduplication remain effective; Cooking can earn normally; unrelated progress survives the first subsequent write; fractional bonus carry survives migration/restart; interrupted credited payments recover once without a duplicate credit; missing old-skill counters and partial Cooking entries fail closed without rewriting the file.
- Disposable Paper integration: **PASS**, including clean startup and shutdown, using Java 25 and the locally pinned Paper 26.2 build 121. The reported production trace uses build 129; a direct build-129 server test was not performed.
- Fixture: `runtime/zone-smoke-20261017-203128`.
- Logs: `build/job-cooking-compatibility-test.log`, `build/job-cooking-compatibility-quality.log`, `build/job-cooking-compatibility-paper.log`.

This is automated/headless verification, not visual client acceptance, production load testing or a power-cut durability certification.

## Replacement artifact

`build/releases/job-recovery-20261017/NerdSMP.jar`

Size: **4,483,500 bytes**.

SHA-256: `B71014E0984E6CDA17BEC450F6D1FB1B55F197823B58E2C8ED6CC2F3C37E12EE`

The release copy, Gradle output and jar used by the passing Paper fixture have identical checksums. The jar also includes the previously implemented progression, gear-set, Cooking, achievement, crafting-protection, leaderboard and player-payment work; see `docs/ISLAND_COOKING_ECONOMY.md`.

## Install and remaining acceptance

The main server was not started, restarted or modified for this fix. No live player/world files were edited. The disposable smoke server shut down and its test listener closed.

1. Stop the destination server normally and back up its plugin and player data.
2. Replace its existing NerdSMP plugin jar with this jar. Keep only one NerdSMP jar in `plugins/`; do not add a second copy under another name.
3. Keep `plugins/NerdSMP/player_data` and all other existing configuration/progress files intact.
4. Start the server normally, not with `/reload`.
5. Have the affected player reconnect, verify job recovery no longer logs the error, and verify `/jobs`, skill progress and Marks. Compare existing daily counters/balances with the backup as appropriate; a legitimate UTC-day change resets daily allowances normally.

If another malformed-state error remains, the new error includes the skill name. Investigate that specific entry against a backup; do not blanket-reset the account.

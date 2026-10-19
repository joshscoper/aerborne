# Bulk identification verification — 2026-10-17

## Artifact

- Download: `build/releases/bulk-identification-20261017/NerdSMP.jar`.
- Size: **4,510,800 bytes**.
- SHA-256: `77DFED0892718132905C6C1C3107B554D54242384172A2808354A0C761B266B9`.
- The release copy, Gradle output and plugin used by the passing Paper fixture have matching checksums.

## Automated checks

`gradlew.bat --offline qualityGate build --console=plain`: **BUILD SUCCESSFUL**.

Combined API/Paper JUnit results: **1,308 total**, **1,307 passed**, **1 skipped**, **0 failures/errors**.

The 16 new unit tests cover:

- Unchanged per-item identification prices, exact summed costs, rarity/affix dust, operator overrides and bounded inputs.
- Single-use full-inventory replacement, preserving unrelated slots, scrap removal plus reward in one replacement, immutable list snapshots, stale-quote rejection, no write after a failed read, and holding ambiguous writes rather than retrying them.
- Prepared/applying receipt persistence across reconstruction, blocking another operation while pending, valid completion/decline/refund transitions, rejecting stale transitions, corrupt/duplicate-key/mismatched-account files, strict lists and inventory lengths, and not publishing a receipt after a failed save.

Disposable real-Paper run: **PASS**, clean startup/shutdown on Java 25 and pinned Paper 26.2 build 121.

Fixture: `runtime/zone-smoke-20261017-205240`.

The new `ZONE_SMOKE_BULK_IDENTIFICATION_COMPLETE PASS` checks cover:

- Genuine sealed crafted and earned Uncommon–Mythic gear, with inactive stats until identified.
- Rejecting stacked unique gear, malformed affixes, missing loot UUIDs and renamed vanilla imitations.
- Batch and held quotes honor the installed test configuration's lower fee, not hard-coded production defaults.
- Identification previews leave source items unchanged and preserve item UUIDs/affix rolls in the revealed output.
- Exact dust from Crafted/Mythic inputs, zero scrap Marks charge, merging an existing 60-dust stack to 64 and using freed slots for the remaining reward.
- Identified equipment, ordinary materials and gems remain untouched.
- Applying the real inventory plan to a native Paper inventory removes sealed inputs and gives the exact dust total, once only.
- Any post-quote storage change refuses the whole plan before mutation; duplicate loot UUIDs cannot both be processed; empty batches have no executable plan; insufficient reward space is detected.
- `/identify` executor/completion/help are wired and retain `nerdsmp.station.remote`.

The wider existing Paper fixture also passed its gear, progression, Cooking, auction/store, NPC, permissions/panel, harvest, generator, dig-site, workshop, smelting, mob-stack and protection checks.

Logs: `build/bulk-identification-test.log`, `build/bulk-identification-quality.log`, `build/bulk-identification-paper.log`.

## Boundaries and rollout

The main server was not changed or restarted. No live worlds, player progress, purchases, staff permissions or configuration were reset. Disposable tests do not contain real accounts or represent a production rollout.

These checks are headless: no authenticated client GUI walkthrough, delayed-payment/disconnect client test, actual process-crash drill, production load test or Paper build-129 runtime test was performed. Native inventory plan application is verified; the complete paid player-session journey still needs the manual acceptance steps in `BULK_IDENTIFICATION.md`. Do not interpret receipt review barriers as cross-file ACID transaction guarantees.

Replace the existing plugin jar while the destination server is stopped, preserve its data/configuration, and restart normally. Existing installed NPC dialogue is preserved; its `open_identifier` service opens the new bulk menu automatically.

# Skill trees verification — 2026-10-24

## Delivered

Connected trees for all 11 professions, general Adventurer progression, and shared island development: **13 trees, 240 rank choices**. The design and point schedules are in `../SKILL_TREES.md`. Vanilla inventory menus provide ranked icons, prerequisite links, readable costs and purchase confirmation. Existing profession/island purchase IDs and automatic Adventurer perks are preserved.

Candidate: `output/skill-trees-20261024/highcrown-skill-trees-20261024.jar`.

SHA-256: `99c365fac23532e7a8663b8435b2daaa089bb53c21c883e63048464e32698776`.

The tested fixture JAR and delivered JAR have identical hashes. This is a build of the current working tree, including earlier playtest repairs and other pre-existing changes. It has **not** been deployed to production.

## Automated verification

`qualityGate :nerdsmp-paper:shadowJar --offline --console=plain` passed after the final menu fix. Paper tests: **1,665 total; 1,664 passed; zero failures/errors; one existing skip** (`ExpeditionPreviewExportTest`). `git diff --check` passed. Python harness/reference-content syntax and Mineflayer harness syntax passed.

New coverage checks all 11 profession graphs, either-parent prerequisites, two-shared-talent capstones, Adventurer point isolation, canonical IDs, duplicate purchases, seasonal isolation and disk reloads. New island coverage checks alternate branches, real bonuses, owner-only spending and reloads. Layout checks reserve the shared MenuKit navigation positions. The stat-consumer catalog check now includes profession crossroads/capstones.

## Native Paper and Mineflayer verification

**226/226 assertions passed**, with **21 purchases through actual client inventory clicks**. Both bots used ordinary logged-in account sessions. The fixture seeded skill/island XP to make late-game choices eligible, and made players invulnerable; this was a functional transaction test, not earned progression or a pacing run.

- All 11 skill menus and their eight ranked cards reached the client inventory.
- A fresh account's locked click did not open confirmation or buy a rank.
- Native Next, Previous, Back and Close clicks reached the expected screens.
- `/adventurer` opened the general tree with its independent 11-point budget.
- Every tested confirmation spent exactly one point in the intended budget; the other personal budget stayed unchanged.
- Mining and Adventurer shared talents and capstones were purchased; repeat capstone purchase returned ALREADY_UNLOCKED without spending again.
- Purchased Mining ranks displayed 2/3. The Mining capstone reached the live gear/stat profile. Adventurer defense and XP combined once with existing automatic rank perks.
- Both island pages were navigable. Confirmations returned to their original page.
- Industry capstone was bought through Miner's guild without Rich seams. Automation capstone was bought through Busy workshop without Collection network. Actual Mining XP, machine-cycle and collection-limit values reflected those choices.
- Co-op members could inspect the island tree but could not open a spending confirmation or spend through the service.
- New instances of the disk-backed progression and island services restored all purchased choices exactly.

Captured 15 inventory receipts, 90 client window-open events and 54 completed client clicks. No bot errors/click errors. Paper exited 0 after clean shutdown; both bots stopped. Minecraft, RCON and task preview listeners were closed.

## Issues caught during verification

1. The first fixture inherited an island dimension without its ownership manifest. Initialization correctly refused to adopt it. The copied dimension was archived outside the active map and a fresh island world created; this was fixture setup, not a production defect. `setup.py` now excludes inherited island dimensions.
2. The first live menu pass caught a navigation collision: station-specific Back/Close constants occupied the intended Previous/Next slots. The new talent menus now use `MenuKit.backSlot(54)` and `closeSlot(54)`. The final run exercised all four buttons through native client clicks.
3. The planner initially treated profession level 0 as the maximum level. A nullish fallback now preserves zero; the logic check lowers every profession to zero and verifies zero points and locked purchases. Narrow diagrams keep four roots on one row to avoid routing links through other talent nodes, and labels are wrapped within lanes.

The two failed fixture attempts are retained under `runtime/talent-trees-20261024/attempt1` and `attempt2`. Final receipts and logs are separate.

## Planner

The inline planner embeds the catalog exported from the tested plugin. Its data was compared exactly with the final export. Logic/geometry checks passed for all 13 trees at 320, 360, 540, 736 and 1,024 pixels (65 diagrams), including prerequisite purchases, independent budgets, undo and low-level pruning. It is a build preview; its Undo button does not imply an in-game respec feature.

The app browser could not reach the local preview endpoint. **Browser rendering, pixel appearance and human usability are unverified**. The planner is a schematic, not a fabricated Minecraft screenshot.

## Limits and release position

Live storage verification used YAML. JDBC uses the same eligibility rules and existing account transaction lock/table, compiles and passes the repository checks, but no live PostgreSQL integration test was run. No SQL schema change is needed. The native probe verifies representative live consumers and purchases; it does not measure random proc frequencies for every bonus, human build preference, final season balance, power-loss recovery or earned campaign completion. Ordinary server startup includes a pre-existing host OSHI performance-counter warning; no talent-tree runtime failure appeared in the final run.

Profession and island point budgets are unchanged. The separate Adventurer budget and cheaper alternative island routes warrant an earned-progression cohort before further tuning. Seasonal permanent purchases and the existing Mastery respec policy are retained and stated in confirmation.

## Evidence and reproduction

`output/skill-trees-20261024/validation.json`, `talent-tree-result.txt`, `talent-tree-screens.json`, `catalog.json`, and `skill-tree-evidence.zip` contain the final receipts, summaries and relevant sources. Raw final runtime logs remain in `runtime/talent-trees-20261024/`. No production credentials/configuration are included in the evidence archive.

Harness: `testing/talent-trees-20261024/setup.py`, `build-probe.ps1`, `run.py`, `bots.js`, and `planner.test.cjs`; probes/exporter: `scripts/verification/TalentTreesProbe.java` and `TalentTreeExport.java`. The setup intentionally refuses to overwrite prior evidence; archive an owned fixture before another fresh setup. The probe requires the explicit JVM property and loopback binding and shuts down its disposable server when complete.

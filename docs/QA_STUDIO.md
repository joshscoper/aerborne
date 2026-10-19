# Content-team Quality Desk

The Quality Desk is a read-only content audit, loot preview and optional workboard inside Content Studio. Designers keep direct publishing access. Marking a review done does not publish content, approve a release or grant rewards.

## Access

Open the Quality Desk from `/content`, or use:

- `/content qa` — Quality Desk.
- `/content qa validate [page]` — dependency and tuning report.
- `/content qa board` — persistent team workboard.
- `/content qa simulate <loot-profile>` — deterministic loot preview.
- `/content qa readiness` — the current release-review diagnostics.

`nerdsmp.studio.use` grants the Content Studio entry point. `nerdsmp.studio.qa` permits reading these tools. `nerdsmp.studio.manage` additionally permits workboard changes. These default to operators; grant only the needed permissions to trusted team members. They do not replace the existing item, mob, zone or NPC publishing permissions.

## Validation

The report scans live catalog snapshots:

- Item IDs, equipment set links and authored item stat budgets.
- Mob blueprints, XP/damage budgets and loot profiles.
- Loot item references and zone/mob table assignments.
- Zone rosters, incomplete encounters, dense spawn settings, saved world availability, and ordinary spawn-point footing/headroom.
- Dig-site parent zones and captured node bounds.
- NPC root dialogue/shop/faction links, dialogue branches and shop/reputation actions, shop item products, and recipe result items (including disabled authored recipes).
- Daily quest objective, target and reward bounds, with reward-budget guidance.
- Dangling workboard references.

Errors indicate missing references or invalid shared-world/node relationships. Warnings identify tuning or setup risks, not mandatory publishing gates. Information includes intentional disabled content and default loot behavior. Manual findings explicitly require player journeys or operational acceptance evidence.

World checks inspect only already-loaded worlds and chunks. At most 256 spawn points are inspected per scan; the report states when points were unloaded or outside that budget. The ordinary two-block headroom test is a helpful sanity check, not a complete pathfinding, large-mob, line-of-sight or encounter-mechanic simulation. This tool never loads a chunk, spawns a mob, changes terrain or enables a zone.

Thresholds are guidance: high damage/defense relative to item level, percentage stats above 50%, generous mob XP or burst damage, expected Marks above 30 per kill, and expected item quantities above eight per kill. A deliberate late-game reward may trigger a warning; record the reason and playtest it rather than treating a warning as a ban.

Reports are capped at 512 findings and show the omitted count. Broken references sort first. The quality scan never declares the server released: crash recovery, multiplayer journeys, proxy security, load, backups, payments and the existing release-review blockers require separate acceptance evidence.

## Team workboard

Choose **New Work Item** and privately enter:

```text
crypt_playtest | REVIEW | Playtest the Crypt with a fresh party
```

The kinds are `TASK`, `BUG` and `REVIEW`. Confirm creation, then open the item to assign it to yourself, unassign it, add a team note, attach/remove references or change its state. States are `OPEN`, `IN_PROGRESS` and `DONE`; done items may be reopened.

References use stable typed IDs, for example `ZONE:crypt`, `MOB:veil_stalker`, `MOB:vanilla:zombie`, `ITEM:ember_blade`, `LOOT:crypt_rewards`, `QUEST:daily_miner` or `FACTION:wardens`. Supported types are ITEM, SET, MOB, LOOT, ZONE, DIGSITE, NPC, DIALOGUE, SHOP, RECIPE, WARP, QUEST and FACTION. The GUI attaches only references present in the current catalog; later removed content appears as a warning during validation.

Changes require confirmation and the management permission at the moment of saving. A stale item revision cannot overwrite another designer's changes: reopen the work item if it changed during your input. Private input expires after 60 seconds and may be cancelled without saving.

Active records are stored in `plugins/NerdSMP/studio/workboard.yml` using a strict versioned schema. There are at most 256 active items, 12 references per item and 32 recent notes per item. The GUI shows the newest seven notes; **Read All Notes** sends all retained notes privately to chat. The oldest notes roll out after the 32-note limit; this is bounded team tracking, not a complete audit history.

Completed items have an **Archive Completed Item** control. Confirmation first preserves that exact revision and its retained notes in `studio/workboard-archive/<id>-r<revision>.yml`, then replaces the active board without the item. This frees an active slot without deleting its data. An archived ID remains reserved, including after restart. Up to 4,096 revision snapshots are supported; staff must preserve/manage the archive offline if that limit is reached. No automated archive deletion or ID reuse is provided.

If archive preservation succeeds but the active-board write fails, the original active item and its archive both remain. Retrying the same revision reuses its matching archive rather than duplicating or overwriting it. Stale revisions and unfinished items cannot be archived. Keep both the active board and archive directory in backups; recover an archived record by examining/restoring its saved schema-one snapshot offline, not by creating a new item with the old ID.

Disk writes are flushed to a temporary file and atomically replace the board before publishing the new in-memory snapshot. Save failure leaves the previous active board unchanged. Invalid schemas, duplicate fields, oversized files, malformed UUIDs/timestamps and unexpected fields fail closed instead of silently replacing team notes. If the optional board cannot load, its data remains untouched and board editing is disabled with a storage warning; the rest of the plugin, content validation and loot preview continue to work. Staff must repair/restore the notes and restart before editing them again.

## Loot simulator

Choose a loot table to run 1,000 deterministic trials of the same `LootProfileRolls` used by live rewards. The report includes successful rolls, total quantities, mathematically expected quantity per kill, sampled Marks per kill, unidentified status and affix count. A stable profile-ID-based seed makes comparisons repeatable. The GUI displays the first 21 entries; **Full Private Report** includes all entries.

Simulation creates no entities, ItemStacks, purses, pickup IDs or account credits. It previews the selected table only. An ADDITIVE table's vanilla and other allowed default drops are not included; a REPLACE table's XP behavior is likewise outside the item/Marks simulation. It is not a full combat encounter or an economy forecast across player behavior.

## Suggested team loop

Create an optional task for a feature, author and publish through its existing editor, run dependency validation, inspect loot expectations, and then play the encounter using real player clients. Record tuning notes or bugs against the relevant stable references. Repeat until the player journey is satisfactory. Publishing remains the designer's action throughout this loop.

Headless tests cover atomic publication failure, strict reload/schema validation, revision conflicts, timestamp regression, record/reference/note limits, completed-only archival, preservation and idempotent retry after active-write failure, archive capacity and ID reservation, typed references, graph diagnostics, budget warnings and deterministic no-reward loot trials. Real multiplayer/editor interaction tests remain necessary before public release.

# Fewer dropped item entities

Identical mob rewards, hoe harvests, expedition enemy drops and signature-affix bonuses are
packed into legal item stacks before spawning. For example, 130 identical single-item rewards
become stacks of 64, 64 and 2 rather than 130 entities. Total rewards do not change.

New ground items are also queued for local consolidation, including vanilla block drops and
plugin overflow. Loaded chunks are checked gradually at startup and after chunk loading, so
existing piles can benefit. The service never loads a chunk to collect its items.

Ground consolidation waits at least two ticks for drop and auto-smelting receipts to settle,
then checks nearby compatible entities. It respects `ItemMergeEvent` cancellation, the item's
actual maximum stack size, exact stack metadata, pickup owner/thrower, player/mob pickup flags,
gravity, invulnerability and item health. Named/tagged entities, entity persistent data,
unlimited/non-aging items, burning items and pickup-locked transactions are left alone.

Merged items retain the longer pickup delay and older age, so merging cannot bypass pickup
protection or renew despawn time. Distinct rolled gear and unique coin purses remain distinct.
No virtual stack count, reward deletion, automatic inventory delivery or shortened item
lifetime is introduced. Dense piles of different unstackable items can still need collecting.

```yaml
drops:
  stacking:
    enabled: true
    radius: 2.5
    items-per-pass: 128
```

Defaults apply to existing configurations that omit this section. The pass runs every five
ticks, checks at most 64 nearby candidates per queued item, and scans at most one queued loaded
chunk per pass. Pending item/chunk queues are bounded to 8,192/1,024 entries. Overflow entries
retain normal Minecraft behavior; their rewards are not removed. Radius is bounded to 0.5–4
blocks and the per-pass item budget to 8–256. These limits prevent a consolidation pass from
becoming an unbounded world scan. A normal server restart activates a changed configuration.

Verification uses `scripts/verification/run-metallurgy-probe.ps1`. The native drop probe checks
130-to-3 reward packing, immutable inputs, different metadata, stack-limit conservation,
pickup delay/age, owner separation, cancelled merges and physical entity consolidation.
This verifies entity reduction and correctness; client FPS improvements require real-player
measurement under the affected farm/loot workload.

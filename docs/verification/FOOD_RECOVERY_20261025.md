# Food and recovery verification — 2026-10-25

Status: **PASS**. This is a packaged update, not a production deployment.

## Automated checks

- Offline Gradle `qualityGate :nerdsmp-paper:shadowJar`: passed.
- Module suites: 1728 tests, 0 failures, 0 errors,
  1 existing skip; 1727 passing.
- Website application/guide suite: 84 tests passed.
- New recovery tests cover health scaling, combat delay, expiration, non-stacking replacement,
  Nourishment quality, invalid tuning, atomic save failures and canonical linked accounts.

## Actual server/client verification

Paper 26.2-121 on loopback port 25598; two Mineflayer 26.1 clients through ViaVersion/ViaBackwards.
Meals, inventory, damage and test surfaces were seeded. This is controlled feature verification,
not earned progression or a new full-server endurance playtest.

- Phase 1: 46 server assertions passed.
- A real server shutdown and restart separated the phases.
- Phase 2: 19 server assertions passed.
- 22 independent evidence checks passed.
- 9 real full-hunger right-click consumption attempts, covering
  ordinary bread, prepared food, cancellation, milk, stew, golden apple, Adventure mode and ghosts.
- Native client attack, actual MMORPG death, Spirit Healer confirmation, private island entry and
  placed-cake interaction were exercised.

Mineflayer's `consume()` helper rejects full hunger without inspecting the food component.
The harness therefore uses its native `activateItem()`/`deactivateItem()` APIs. The client and
server hunger values remain 20 throughout those actions. No food-level spoof was used.

Mineflayer did not emit its `death` callback in the final run. The actual server death message,
client respawn, spirit-release menu, saved ghost state and subsequent resurrection establish the
death lifecycle; this report does not count a client death callback that was not received.

## Observed behavior

- Full food, zero saturation/exhaustion; sprinting and healing consume no hunger.
- Ordinary rest heals 1 HP per second at 20 max health after the eight-second damage delay.
- A prepared +15 Nourishment meal heals 1.4 HP per second at 20 max health and 14 HP at 200 max.
- Both dealing and receiving damage pause recovery. Poison and cancelled healing events suppress it.
- Real cancelled consumption spends nothing and grants nothing. Native bowls, milk buckets,
  golden-apple effects and Cooking speed effects remain correct.
- One Well Fed benefit survives restart with its original absolute deadline and no offline heal.
- Death removes food bonuses while retaining food inventory. Ghosts cannot eat or recover health.
- Resurrection and private-island recovery remain functional. Cake consumes exactly one slice.
- Ordinary upgraded carrots still work for replanting, quest delivery and compost-machine checks;
  renamed items and named meals remain protected. Some integration checks invoke the real consumer
  methods directly; they do not claim a full native crafting/quest/machine playthrough.

## Scope and limitations

The default hunger bar remains visible on unmodified clients. Hunger drain is removed server-side.
The website guide was edited and tested locally; it was not published. No live server was restarted.

Paper logs retain this Windows host's existing OSHI/Perflib 009 counter warning. There are no
unexpected server errors in the final phases. Both servers shut down cleanly and clients exited.

An earlier fixture copied an old island without its ownership registry, and startup refused it.
Another attempt's cake search ran before its block update reached the client. The fixture now
omits those old islands and waits for the block update. These were fixture failures; the final
two phases both passed. Earlier attempt logs are retained in the evidence archive.

## Delivery

- `highcrown-food-recovery-20261025.jar`: the exact playtested production JAR, without probe classes.
- `config.yml`: complete default configuration extracted from that JAR (761 lines).
- `FOOD_AND_RECOVERY.md`: behavior, tuning options, compatibility and upgrade instructions.
- `food-recovery-evidence.zip`: raw logs, receipts, harness, unit reports and build checks.

JAR SHA256: `394bd5af7c1b5d1e2e9fab4be39ce4d3c5f939d696ddb6dd2f5ea1516cd28631`

For an existing customized configuration, missing `food-and-recovery` settings take their defaults.
Add the small section only to tune it; do not replace customized settings wholesale.

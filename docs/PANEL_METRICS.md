# Staff console metrics

Open **Metrics** in the authenticated staff console. Players & retention, Gameplay,
and Economy share 7-, 30-, and 90-calendar-day filters. CSV exports contain daily
aggregates, system counters, and return cohorts. No additional endpoint, credentials,
or third-party analytics service is introduced.

## What is measured

- **Players:** daily and range-unique players, genuinely new joins, established players,
  peak concurrency, connected and active player-hours, completed-session lengths,
  new-player milestones, active time by world, and daily TPS/MSPT/entity observations.
- **Retention:** exact UTC Day 1, Day 7, and Day 30 return visits grouped by first new
  join date. Each percentage includes its returned/eligible denominator. Today and
  future targets are still maturing; target dates with less than 90% sampling coverage
  are excluded as unavailable. A zero return rate requires an observed eligible day.
  Select 90 days to see mature Day 30 cohorts; a 30-day join window cannot contain
  cohorts whose Day 30 has already completed.
- **Gameplay:** accepted skill XP rewards and award counts, XP by source category,
  XP per active player-hour, mob kills, deaths by damage cause, story-quest acceptances
  and completions, expedition starts/outcomes and average clear time.
- **Economy:** committed Marks credits, debits, transfers, net balance flow, and
  breakdowns by transaction reason. Failed or duplicate transactions do not count.

New means a Minecraft first join observed after tracking began. Existing players and
sessions recovered during plugin startup are never invented as new-player cohorts.
Range-returning players were established before the selected window; daily-returning
players were established before that individual day. A player can appear as new in
one day's chart and returning on later days. Onboarding milestones are independent
ever-reached flags for each selected new-player cohort, not an ordered funnel.

Active time uses the existing engagement activity timeout and loaded-account check;
Creative and Spectator time is excluded. Samples approximate activity and world
changes within ten seconds. Connected time and audience counts include all modes.
Completed session length includes connected AFK time and is attributed to logout day.
Sessions interrupted by restart/crash and those already open when tracking starts
are excluded from average lengths. Midnight samples split time across UTC dates.

XP represents accepted reward amounts before level caps, including quest, reward,
and administrative awards. It is not a count of crafted items or effective capped
character growth. XP rates use **all** active player-hours, not skill-specific time.
Quest completions are actual story turn-ins; administrative completions are excluded.
Expedition test runs are excluded, assisted runs included. Starts and outcomes may
fall in different windows: the clear percentage is completed / (completed + failed)
outcomes, not completed / starts. Active runs do not have an outcome yet.

Marks figures are gross balance movements. Bank deposits/withdrawals, escrow, refunds,
and reversals can appear in credits/debits. Transfers count once in a separate series.
Net balance flow is not a money-supply or inflation estimate. Metrics show correlations;
compare mechanic changes with population, coverage and server performance.

## Storage and operation

Tracking starts when the updated plugin runs; it cannot reconstruct earlier gameplay.
It continues while the panel is closed or disabled. Defaults:

```yaml
metrics:
  enabled: true
  retention-days: 180
```

Retention is bounded to 90–365 days; settings take effect on restart. Existing saved
history retains its configured retention period. Data is local to this server at
`plugins/NerdSMP/metrics/history-v1.json`. Back this file up alongside plugin data.
Its internal UUID visit indexes support deduplication and cohorts; names, IP addresses,
chat, precise movement, and full reward operation IDs are not collected by analytics.
The panel and CSV receive aggregate analytics only.

Main-thread aggregation samples every 200 ticks (roughly ten seconds). It reuses
engagement signals and observes successful service results without extra per-award
database reads. Encoding and atomic file replacement run on a background writer about
once a minute and during shutdown. A crash can lose observations since the last save;
this is a tuning dashboard, not a financial ledger. Long sample gaps are capped at
30 seconds and reported rather than extrapolated into active hours. Failed saves,
queue overflow and aggregation limits are visible in the tab.

Limits are 10,000 queued asynchronous observations, 100,000 retained identities,
20,000 daily visitors, 512 distinct daily system counters (plus overflow), and a
128 MiB history load limit. Counters and key dimensions are bounded. Check warnings
before interpreting a large or incomplete dataset. Malformed/unsupported history
disables tracking and preserves the original file; it does not silently reset history.

## Local visual preview

`testing/staff-console-kit/metrics-sample.json` contains explicitly fictional sample
data generated by `scripts/verification/MetricsPreview.java` using the real ledger.
The preview serves current panel assets and never connects to Minecraft. See that
folder's README. Never copy sample metrics into live plugin storage.

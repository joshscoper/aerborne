# Material sockets and metallurgy verification

Date: 2026-10-22

This records the initial metallurgy build. The current release and expanded checks are recorded
in [Player stats and processing XP verification](PLAYER_STATS_AND_XP_VERIFICATION.md).

Build: `nerdsmp-paper/build/libs/nerdsmp-paper-0.1.0-SNAPSHOT.jar`, copied to `NerdSMP-Paper.jar`.
SHA-256: `fd9f913c0ee1d0d39d3d58d79c83e62546c8b6a3102ba148cbeb5c6374934d2f`.

## Automated results

- `:nerdsmp-paper:test :nerdsmp-paper:shadowJar --offline`: successful. 1,622 tests total,
  1,621 passed, 1 skipped, no failures. Log: `runtime/metallurgy-build.log`.
- Disposable Paper 26.2 build 121 probe: **PASS, 106 checks**.
  Result: `runtime/metallurgy-probe-20261022-201932/metallurgy-probe-result.txt`.
- The release jar contains the metallurgy catalog, recipe predicate implementation and schema-14 items.
- `git diff --check` and the probe script's PowerShell syntax check passed.

The Paper probe checks every non-equipment catalog item's socket capacity and tooltip, refreshes a
legacy 17-item Gloomsteel Ore stack without losing quantity, refuses socketing that ore, and preserves
existing equipment capacities. Authenticated item predicates accept altered display metadata, reject
ordinary substitutes and reject other custom items using the same base material.

Native crafting lookup resolves the alloy pattern and rejects a substituted ordinary ingot. Native
furnaces process three inputs into three authenticated outputs for all five refining routes; the Cinder
route runs in a blast furnace. Ordinary raw iron still produces ordinary iron. Full outputs, incompatible
custom outputs, cancelled smelts and unauthenticated sources preserve their input quantities.

The probe ran in its own loopback-only Minecraft server and shut down after verification. Its final
helper configuration disables the unrelated read-only Web API and GeoIP download. No jar was installed
in an existing running server, and no live server was restarted.

## Manual gameplay checks

Authenticated-client crafting clicks, shift crafting, recipe-book autofill, hopper transfer timing and
navigation through `/recipes` remain manual checks. Native matching and furnace transactions were
verified with the disposable probe; inventory consumption uses Paper's normal transactions.

See [Metallurgy](../METALLURGY.md) for material sources, recipes and configuration migration behavior.

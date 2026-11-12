# Player stats and processing XP verification

Date: 2026-10-22

This records the player-stats build. The subsequent release is recorded in
[Drop stacking verification](DROP_STACKING_VERIFICATION.md).

Release: `NerdSMP-Paper.jar` (6,682,543 bytes), copied from
`nerdsmp-paper/build/libs/nerdsmp-paper-0.1.0-SNAPSHOT.jar`.

SHA-256: `bafa3d17810576fa203cb201623cccb1d06c98f255dfbef87f71459302767ffe`.

## Automated results

- `:nerdsmp-paper:test :nerdsmp-paper:shadowJar --offline`: successful;
  **1,632 tests total, 1,631 passed, 1 skipped, zero failures/errors**.
  Log: `runtime/player-stats-build.log`.
- The final Profile navigation correction compiled and packaged successfully with
  `:nerdsmp-paper:shadowJar --offline`. Log: `runtime/player-stats-package.log`.
- Final-jar disposable Paper 26.2 build 121 probe: **PASS, 143 checks**.
  Result: `runtime/metallurgy-probe-20261022-220428/metallurgy-probe-result.txt`.
  Log: `runtime/player-stats-final-probe.log`.
- Jar inspection confirms the player stats service/rules, crafting experience rules,
  metallurgy catalog, `/stats` declaration and default health configuration are packaged.
- `git diff --check` passed for the plugin, guides and verification sources.

## Verified behavior

Unit tests cover level/equipment/percentage health, armor-slot eligibility, quality scaling,
health caps, defense/effective-health calculations, health-preserving equipment changes,
crafting skill classification, bounded bulk XP and exact furnace receipt identities.

The disposable probe uses real Paper item metadata, registered recipe predicates, native
crafting lookup and native furnace/blast-furnace/smoker processing. It checks all five custom
refinements, ordinary iron, Cooking output, exact collectible receipt quantities and refusal
of substituted, unauthenticated, cancelled, full or incompatible work. Material socket and
legacy-stack normalization checks from the preceding metallurgy work remain covered.

A controlled Player facade delegates health to a real native Paper health attribute. It
verifies Adventurer 25 plus item-level 24 armor gives 74 real maximum health, the player heart
display remains scaled to 20 display points, external modifiers survive, equipment swaps do
not heal, quit restores the prior display/health fraction, and only this plugin's modifier
is removed. Captured Adventure components contain current/max HP, defense and the temporary
XP notice; the notice expires while vitals remain.

The same probe creates the native 54-slot stats inventory with vitality, defense and active
set panels, checks the Character stats tile and registered command, resolves metallurgy
crafting to Smithing regardless of unlock skill, and checks quality advancement consumes
its real catalyst before reaching the XP award. A simulated inventory transaction confirms
three consumed crafting operations and three actual outputs produce a three-craft receipt;
unchanged/full-output paths produce none.

The final release jar and the jar loaded by the final probe have the same SHA-256. The
server was disposable, bound to loopback, and shut down after the check. No existing live
server was restarted or given a replacement plugin.

## Client gameplay checks

No authenticated Minecraft client was connected. Client-rendered action-bar appearance,
mouse/shift/drop crafting interactions, recipe-book autofill, complete authenticated skill
account awards, hopper transfer timing, potion effects and inventory navigation clicks remain
manual gameplay checks. Native health/metadata/furnace behavior and server-side menu creation
were verified; the controlled Player facade does not substitute for a client visual review.

See [Player stats](../PLAYER_STATS.md) for defaults and XP rules, and
[Metallurgy](../METALLURGY.md) for custom materials and recipes.

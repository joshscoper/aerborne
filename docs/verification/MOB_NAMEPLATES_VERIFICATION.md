# MMORPG nameplate verification

October 23, 2026. Existing unrelated work was preserved. The live server was not
restarted; the release jar is ready for installation.

- Full Paper module suite: **1,645 passed, 1 skipped, 0 failures**.
  `runtime/mob-nameplates-final-build.log`.
- Native Paper 26.2 build 121: **247 combined checks passed**, including 17 new
  nameplate checks. `runtime/mob-nameplates-native.log`.
- `PlayerSettingsServiceTest` verifies default-on visibility, independent per-player
  opt-outs, persistence across reload, and re-enabling the existing preference.
- `scripts/verification/MobNameplateProbe.java` verifies actual Paper TextDisplay
  properties, pre-combat level/name text, nearest-target selection, range and cap,
  no duplicate entities on repeated discovery or combat, combat-to-idle text,
  per-viewer opt-out/re-enable, line of sight, hidden targets, NPC/objective exclusion,
  world scoping, changed nearby targets, removed-mob cleanup and shutdown cleanup.
- The native fixture uses real Paper mobs/displays and controlled Player facades for
  show/hide calls. It does not log in a Minecraft client or verify the client's font,
  display interpolation, or appearance under a crowded production population.

Display discovery uses nearby loaded entities, not global living-entity scans or
forced chunk loads. Defaults cap ambient names at 24 per viewer within 24 blocks,
sharing the existing global pool of 64 overhead indicators. Unchanged display
positions and text are not resent. NPC labels and unrelated player nameplates are
not modified. Existing mob-display preferences remain compatible.

Build jar, native-tested fixture jar and root `NerdSMP-Paper.jar` have SHA-256:

```text
45b55b351e608952f02dfa025af5a1c543ab125ece54cea0651416dbc252b408
```

See `docs/MOB_NAMEPLATES.md` for player controls, configuration and behavior.

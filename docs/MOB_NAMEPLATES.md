# MMORPG mob nameplates

Nearby enemies in `MMORPG` show their existing name, level and elite/boss rank
before combat. A normal enemy's label expands to show health when engaged and
returns to a single name line after the combat-display timeout. Boss health stays
in its existing boss bar. Native mob names are not changed.

Players can use **Settings → Mob nameplates & health bars** to hide these displays.
The existing `mob-health-bars` preference is reused, defaults to On, and persists
per player. Earlier opt-outs remain respected. The menu applies changes immediately;
turning labels off for one player does not hide them for anyone else. This toggle
also continues to control combat health bars outside MMORPG.

Defaults under `presentation.mob-health-bars`:

```yaml
nameplate-worlds: [MMORPG]
nameplate-range: 24
nameplates-per-viewer: 24
maximum-overhead-bars: 64
```

World names match without case sensitivity. An empty world list disables ambient
names while preserving existing combat displays. Restart after server configuration
changes. Nameplate range is bounded by the existing overhead range. Global cap
is shared with existing health displays; ambient names cannot evict combat labels.

Discovery runs every ten ticks against each opted-in player's already-loaded nearby
entities. It chooses the nearest eligible targets, checks distance and line of sight,
and excludes NPCs, tame pets and mechanic objectives. One nonpersistent TextDisplay
is shared by all viewers of a mob, with visibility controlled per player. Idle and
combat labels reuse that entity. Unchanged text/position is not resent each tick.
No extra mob AI, world-wide scans, chunk tickets or player-specific display clones
are introduced. Displays are removed when no viewers remain, the mob disappears,
or the plugin shuts down. Under the caps, some ambient names may be omitted.

Vanilla passive animals are not newly labeled; the feature covers hostile enemies
and plugin-defined encounters. Other plugins' native labels and NPC labels remain
owned by their existing systems. Native-client visual appearance still warrants an
in-game check after installation; the automated probe checks Paper display entities
and controlled per-viewer show/hide behavior without logging in a real client.

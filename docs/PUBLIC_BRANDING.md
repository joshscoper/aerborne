# Aerborne public presentation and release-candidate defaults

## Public identity

Name: **Aerborne**. Mode: **Minecraft Skyblock RPG**. Tagline: **Small beginnings. Endless skies.**

Supporting line: **Your adventure begins above the clouds.** See the
[brand reference](AERBORNE_BRAND.md) for the palette, voice and artwork requirements.

The multiplayer server-list description (Minecraft's MOTD) is:

```text
AERBORNE » A Skyblock RPG Adventure
Build your island. Explore the skies. Discover magic.
```

The Paper ping listener sends a styled two-line Adventure component. It does not spoof online players, maximum slots, server software, version compatibility or uptime. The tab header uses the same name and tagline; the footer shows real online population, the player's Marks and mode. Adventurer rank/level remain beside player names. Welcome messages share the same public name, with clickable help shortcuts. The scoreboard already uses that display name.

`skyblock.display-name` is still the public-name setting. Existing custom names are preserved. Stock historical names matching `Hearthsky`, `Hearth Sky`, `Hearth-Sky` or `Hearth_Sky` (case-insensitively, ignoring surrounding whitespace) display as Aerborne. Only the exact previous stock tagline and description are adapted to the new defaults. This happens in memory: existing configuration files are not rewritten. Operator-authored names and copy remain intact.

`branding.mode-label`, `branding.tagline`, and `branding.description` accept plain text, not MiniMessage; markup-like text stays literal. Blank values use mode-aware defaults. Invalid multiline/oversized copy falls back to bounded defaults instead of injecting extra lines. The default `RPG Skyblock` mode appears as `A Skyblock RPG Adventure` in the server-list heading. `branding.server-list-enabled: false` leaves the underlying MOTD alone.

`/aerborne` and the historical `/hearthsky` name are aliases for the existing main-menu handler, alongside `/menu` and `/nexus`. Existing commands and permission assignments remain valid.

Velocity or another front-end proxy may answer pings itself. The checked-in Velocity template now matches these two lines; merge that change into an existing proxy configuration separately. No domain, store URL, owner identity or external affiliation was invented. The working name has not received trademark/domain clearance. The internal plugin name, command permissions, namespaces, database keys, seasons and `plugins/NerdSMP` folder remain unchanged.

## Retained release-candidate tuning (unchanged by the rebrand)

| Area | Change / decision |
| --- | --- |
| Tips | Fresh defaults wait 120 seconds, then send a tip every 600 seconds, instead of 90/300. Explicit existing timings are retained within safe scheduler bounds. |
| Timing bounds | Initial delay is bounded to 30–86,400 seconds; tip interval to 60–86,400. Extreme values cannot overflow tick conversion. |
| Talent guidance | Corrects the obsolete one-point-every-five-levels text to the actual one-point-every-two-levels rule, including known stock text in old saved tip lists without overwriting those files. |
| Fishing guidance | No longer implies open water is required to catch fish. The previous valid-catch eligibility change is preserved. |
| Persistence label | `yaml` accurately names the existing persistent local backend. `memory` remains a compatible alias with the exact same player files. The misleading RAM-only/data-loss comments are removed. |
| Omitted reward sections | Fixes startup when a compact config omits the playtime schedule, and prevents silently empty campaign milestones: reads actual bundled default sections without writing them into the config or adding milestones to an explicit operator schedule. Explicit invalid reward schedules still fail closed. |
| Candidate capacity | Starts at 30 slots, view distance 8 and simulation distance 6. This is a planning limit, not a measured 30-player load certification. |
| Candidate enforcement | Alert-only Watchdog and disabled autonomous ML actions until false-positive tests pass. The jar does not overwrite existing owner-selected enforcement settings. |
| Existing progression/economy | Keeps level curves, XP multipliers, item stats, earned currency, vendor pricing, taxes, bank prices, quest rewards and drop chances unchanged. No reliable population play-rate/inflation evidence exists in this verification run to justify a global rebalance. |

For scale, level 50 still requires 250,000 skill XP. Before bonuses/rarity rewards, that represents about 13,889 eligible logs at default Forestry XP or 10,000 common catches. These are action counts, not measured playtime or retention targets. Existing bonus consumers, job diminishing returns, remote recovery prices and supply/demand guards remain in force. Gather time-to-unlock, earned/spent Marks, item supply and vendor/player-market telemetry during a real whitelisted playtest before changing those curves.

## Candidate deployment files

- `deploy/release/server.properties`: authenticated direct Java deployment; whitelist and enforce-whitelist are on; RCON/query/command blocks are off. The blank bind address is intended for this authenticated public-edge topology only.
- `deploy/release/config.yml`: a **fresh-server** overlay relying on bundled defaults for omitted sections, with vanilla textures and persistent YAML. It does not provision finished content, accounts, staff, store products or database migration.
- `deploy/paper/server.properties`: retains its existing container bind for the repository's `paper:25566` Velocity route. `docker-compose.yml` exposes no Paper host port and places Paper/PostgreSQL on an internal backend network. Do not copy this offline/all-interface template onto an exposed host; a non-container setup needs loopback or an explicitly chosen private interface and firewall. Its slot/view/simulation defaults now match the 30/8/6 candidate budget.
- Local test launchers remain loopback-only. Their fallback MOTDs no longer show development-server branding.

Do not blindly replace an existing configuration with the candidate overlay. In particular, it disables IP recording/geolocation; existing connection-history code can remove stored addresses on a later save when disabled. Preserve existing operator policy and privacy/retention requirements. Merge only reviewed presentation settings into an existing installation, and do not change its storage backend, season, world paths or network topology as a branding step.

Back up, stop normally, replace the jar and fully restart. Do not rename the plugin folder or use `/reload`. No main-server configuration, data or worlds are automatically modified by this update.

## Launch gates remain open

Run `/nerdadmin releasecheck`, `/market review`, `/trade review`, and `/npc shopreview`. The release check now also reports public display copy, actual persistence selection, authentication topology, slots above the initial envelope, and autonomous-enforcement review needs.

The existing campaign/bank/starter/grave crash-boundary findings, authored content and NPC placement, two-client inventory tests, Java visual sign-off, proxy authentication, load profiling, store/permissions review and restore drills remain required. Existing disclaimer/owner/contact/privacy/purchase disclosures and the current official Minecraft rules must be reviewed before public advertising or monetization. This branding pass does not certify legal compliance, complete those tests, publish the server or open its firewall.

The pinned Paper 26.2 build 121 reports itself as outdated during startup. This pass does not change the server version or Java baseline. Review and test a supported server build through staging before launch; do not silently replace the jar on an existing world.

See [release hardening and remaining gates](HEARTHSKY_RELEASE.md) for the existing unresolved gates and recovery procedures. Historical document filenames and dated verification records are retained for link and audit compatibility.

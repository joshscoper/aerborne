# Staff, fishing and station verification

Verified and staged on 2026-10-16 using Paper 26.2 build 121 and Java 25.0.4.1.

Subsequent panel-only update: [network binding verification](WATCHDOG_PANEL_NETWORK_VERIFICATION.md). The current staged installation uses 0.0.0.0:9029; the loopback and artifact statements below describe the earlier build.

## Automated evidence

- Final offline `qualityGate build`: **1,166 tests; 1,165 passed, one skipped, no failures/errors**. Final log: `build/staff-fishing-stations-verification-quality.log`.
- Permission tests cover defaults and capability tiers, inherited group denies, per-user overrides, registered-node wildcards, unknown groups, cyclic inheritance, foreign-node refusal, audit/assignment restart persistence, corrupt/duplicate YAML and failed-write nonpublication.
- HTTP tests open real ephemeral loopback listeners and verify correct/wrong passwords, authentication on HTML/JS/CSS/API routes, no-store/CSP protections, Host rejection, read-only methods, unsupported queries and JSON escaping. Password storage uses salted PBKDF2-HMAC-SHA256; no plaintext default password is embedded in the plugin.
- Fishing tests cover catch/habitat tier requirements, receipt replay, bounded history, persistence across a fresh store, unrelated-data preservation and malformed saved-list refusal without rewriting the file.
- Station geometry tests verify the centered brewing matrix and ingredient review grid avoid result, confirmation and navigation slots. Existing brewing inventory planning, forge review/capacity, repair, enchanting math and inventory safety tests remain passing.
- Disposable Paper fixture **`runtime/zone-smoke-20261016-204327` passed and shut down cleanly**. Real Bukkit permission attachments granted moderator tools without permission ownership; explicit denies removed access. The production panel startup/configuration path bound 127.0.0.1:9029, refused unauthenticated evidence requests and returned captured evidence/staff JSON after successful authentication. Fixture permissions never assigned real accounts or altered the core registry.
- Existing content, NPC, shops, auction/market metadata, island, gathering, fishing-refusal, livestock, furnace, stack and generator regression checks also passed. The fixture uses test-only panel credentials, not the installation password.

## Staged artifact and preservation

Built, disposable-tested and installed `runtime/skyblock-test/plugins/NerdSMP.jar` SHA-256:

`ACB4CC322FDC06C12EC5A62FDB3B84F2D4958B63AD30E2F92BB1110CD53F50DC`

The final two additional tests did not change the artifact; its hash still matches the passing Paper run. Staging used `run-test-server.ps1 -Skyblock -PrepareOnly`, without starting the player server or refreshing its settings.

All **26 preexisting plugin-data files**, **87 world files** and server.properties remain byte-for-byte unchanged. One intentional local file was added: `plugins/NerdSMP/security/watchdog-panel.yml`, containing the requested username and salted hash configuration. Plugin-data file count is now 27. No world, account, inventory, skill, currency, entitlement or market reset occurred. No Paper process remains running; the configured panel starts with the player server, not as a separate background service.

Verified backup: `runtime/backups/staff-fishing-stations-20261016-203927`, including previous jar, complete plugin data/world/settings, hash manifest and restore notes. It predates the new panel hash file. Retain the hash file separately if restoring gameplay data while keeping panel access.

Logs: `build/staff-fishing-stations-final-paper.log`, `build/staff-fishing-stations-verification-quality.log`, `build/staff-fishing-stations-deploy.log`. Expected deliberate fault-injection and preexisting Windows performance-counter warnings remain in disposable logs.

## Manual acceptance still required

1. Start the player server and log into `http://127.0.0.1:9029` with the configured account. Check dashboard layout, live evidence refresh, stale/offline feedback and credential rotation. No public exposure, TLS reverse proxy or public-network penetration test is claimed.
2. With authenticated Java accounts, assign helper/moderator/administrator/builder/designer/owner, reconnect, remove roles while a staff menu is open and check effective rights/command lists. Exercise equal/higher/offline staff protection and console recovery. No full LuckPerms parity or cross-plugin/world/network permission management is claimed.
3. Playtest every station at multiple GUI scales: forge focus tabs and input returns; enchant before/after confirmation with moved/changed items; all alchemy categories, missing ingredients, full inventory, returned containers, refresh, shift/hotbar/double clicks, death, teleport and disconnect. Automated checks do not constitute visual client approval or process-crash inventory certification.
4. Fish with multiple real co-op members in all unlocked habitats. Verify one accepted catch advances the correct island, duplicates/cancellations do not, limits and perks apply, prismarine feeds the intended recipes and Ivo still requires physical catch turn-ins. Validate pacing, load and restore behavior before public release.

Operator guide: [STAFF_FISHING_STATIONS.md](../STAFF_FISHING_STATIONS.md).

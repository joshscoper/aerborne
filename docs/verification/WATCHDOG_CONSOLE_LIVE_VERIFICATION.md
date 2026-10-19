# Watchdog security console: live-server verification

Verified on 2026-10-16 with Paper 26.2 build 121 and Java 25.0.4.1.

This covers the security console build: metrics, players, the Watchdog, reports, sanctions, audit and staff views, the player profile and web actions. The operator guide is [here](../STAFF_FISHING_STATIONS.md).

## Method

A disposable fixture, `runtime/panel-smoke-20261016-215846`, was staged from the skyblock-test install's server binaries only. It had no worlds and no player or plugin data. The fixture had:

- **Network:** game port 25591. The console was bound to 127.0.0.1 on a random free port.
- **Login:** a throwaway account generated for the run. Only its salted PBKDF2 hash was written. The password was never printed or stored.
- **Seeded offline player:** a playerdata file with the tags Paper writes for real players (`bukkit.lastKnownName`, first and last played, last login and last seen).
- **Seeded nameless account:** an empty playerdata file.
- **Seeded report:** one open report against the offline player.

A script started Paper, waited for startup and two snapshot cycles, and then drove every route over HTTP. Afterwards it sent `stop` and inspected the log and saved files.

## Results: 60 of 60 checks passed

- **Startup:** the server started in 10.8 s and logged the listener line. Shutdown was clean (exit 0). The log had no ERROR or SEVERE lines and no web-console warnings.
- **Read routes:**
  - Unauthenticated requests are refused (401). The page, script, stylesheet and icon load after login.
  - `/api/status` returned live data:
    - TPS, tick-time percentiles, CPU, memory and garbage-collection metrics.
    - Equal-length history arrays.
    - World counts, the offline-player directory, bans and the Watchdog configuration.
    - The seeded open report and the staff groups.
  - The JSON contains no raw `<` or `>`.
- **Player profile:** `/api/player/<uuid>` returned the seeded player's name, first and last seen times, statistics, Watchdog data and desk data. An unknown UUID returns 404.
- **Request protection:** actions without the session token, and cross-site actions, are refused (403).
- **Actions against the offline player:**
  - Note, warn, mute, unmute, freeze and unfreeze succeeded.
  - Timed ban, permanent ban and unban succeeded.
  - Report resolution, staff-group add and remove, and broadcast succeeded.
  - These were refused with 422 and a readable message:
    - a missing reason or an out-of-range mute length
    - unmuting a player who isn't muted, or banning one who's already banned
    - kicking or messaging an offline player, or clearing a score with no live buffer
    - resolving a report twice
    - an unknown group, an unknown action, a malformed target, or a player who never joined
- **Server state:**
  - Bans appear on the server's own ban list with an expiry and `Panel (<login>)` as the source. They are written to `banned-players.json`.
  - Every change appears in the player's history and in the staff audit log, attributed to `Panel (<login>)`.
  - Group changes appear in the access log as `panel:<login>`.
  - The broadcast reached server chat.
  - `staff-management.yml` persisted the panel actions and the resolved report after shutdown.
- **Fix found by this run:** accounts whose saved data has no last-known name were dropped from the player directory and returned no name. They are now listed under a short UUID label, which the run verified. The first run, before this fix, passed 57 of 59 checks.

Follow-up run `runtime/panel-smoke-20261016-220440`, after adding the connection log and Connection tab: **63 of 63** checks passed. The extra checks were:

- The player profile includes a `connection` block. It is empty for accounts with no recorded joins.
- Online-player rows carry client fields.
- `security/connections.yml` is written on shutdown with its privacy header.

That run also had no errors and shut down cleanly. Recording of addresses, brands, channels and sessions on a real join was not exercised, because no client connected.

Unit tests: the 3 connection-log tests (mod-channel classification), all 10 Watchdog panel tests, the 2 Watchdog maths tests and the 2 moderation-duration tests pass in the project tree.

## Not covered

- **Online-player paths:** no Minecraft client joined the fixture. This leaves untested:
  - live location, health, effects, inventory and ender-chest snapshots
  - kick and message against an online player
  - the live Watchdog score buffer
  - the online-player table

  To check these, join a test server with a real account, open a player's profile and use Kick on a second account.
- **Network setups:** remote access, TLS reverse proxies and firewall setups were not tested.

## Anti-cheat revamp run

Run `runtime/panel-smoke-20261016-224022`, after the anti-cheat rebuild ([guide](../WATCHDOG_ANTICHEAT.md)): **73 of 73** checks passed.

- **Packet layer:** it resolved every packet class against the real Paper 26.2 server, logged "Watchdog packet layer ready" and read zero unreadable packets.
- **Console:** it lists the new checks and shows the Combat model panel. The model stayed non-autonomous with no labels.
- **Training:** a training run with no labels explained what data it needs.
- **Labelling:** labelling a player with no fights, and an invalid label, were both refused.
- **Command:** `/watchdog status` reported the packet layer and the model state.

There were no errors and the server shut down cleanly.

Unit tests (1,211 in the full suite, 0 failures) replay vanilla jump arcs, sprint and sprint-jump speeds, Speed II and Slow Falling as clean. They catch hover, high jump, tall steps and ground speed. The timer check ignores freezes, catch-up bursts and ping drops but catches a client running 6% fast. The model learns and explains a pattern, and refuses autonomy on too little data or noisy data.

**Not covered:** live packet capture and detection against a real client. No client could connect to the fixture. Calibrate on the real server as described in the guide's rollout section.

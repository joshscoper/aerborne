# Watchdog network binding

Verified and staged on 2026-10-16 with Paper 26.2 build 121 and Java 25.0.4.1.

Subsequent jar-only upload fix: [first-run configuration verification](WATCHDOG_PANEL_FIRST_RUN_VERIFICATION.md). That newer staged artifact creates a disabled template when the private settings file is missing; this record describes the preceding network-binding build.

The stopped Skyblock installation now has `bind: '0.0.0.0'` in `runtime/skyblock-test/plugins/NerdSMP/security/watchdog-panel.yml`. Only that field was added; the existing username, credential hash, salt, work factor, enabled setting and port are unchanged. The listener activates on the next server start. No firewall, router or TLS changes were made.

## Verification

- Offline `qualityGate build`: 1,170 tests, 1,169 passed, one skipped, zero failures/errors. All eight Watchdog HTTP/credential tests passed, including authenticated requests through the machine's available IPv4 interfaces, wildcard binding, wrong-password refusal, exact Host aliases and rejection of invalid addresses/hosts. Setup-script syntax and address/host validation also passed without prompting for or changing credentials.
- Disposable Paper fixture `runtime/zone-smoke-20261016-205145`: PASS and clean shutdown. The production configuration loader preserved loopback behavior for legacy files, then successfully opened 0.0.0.0:9029 when explicitly configured. Both configurations required login and returned live game-thread evidence after authentication.
- The built, disposable-tested and installed jar all have SHA-256 `F5F1DAE220D98D229D7E011C0F4F662CB05A61AF63066BA3DE8664DA2B112BC0`.
- Staged using `run-test-server.ps1 -Skyblock -PrepareOnly`. No Paper process remains running. The player server was not started.
- All 114 other baseline files remain byte-for-byte unchanged: 26 plugin-data files, 87 world files and server.properties. Total plugin-data count remains 27. No player progress was reset.

Focused verified backup: `runtime/backups/panel-bind-20261016-205136`, containing the previous jar, original credential settings, preservation manifest and restore notes. Keep this directory private because it contains the credential hash.

Logs: `build/panel-bind-quality.log`, `build/panel-bind-paper.log`, `build/panel-bind-deploy.log`.

## Connection and limits

After starting the server, connect to `http://<server-LAN-IP>:9029` using the existing login. Local access remains `http://127.0.0.1:9029`. A remote-device/firewall/NAT test is not claimed. Domains or NAT public addresses require an exact `allowed-hosts` entry as described in [the operator guide](../STAFF_FISHING_STATIONS.md).

HTTP is unencrypted. Use a trusted network/VPN or HTTPS reverse proxy before public exposure. Authentication, Host validation, bounded workers, read-only routes and rate limiting remain enabled. New installations and old files without `bind` still default to loopback.

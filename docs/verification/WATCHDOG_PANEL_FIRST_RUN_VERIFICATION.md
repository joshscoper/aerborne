# Watchdog first-run configuration fix

Verified and staged on 2026-10-16, Paper 26.2 build 121 / Java 25.0.4.1.

## Cause and fix

The previous jar intentionally skipped panel startup when security/watchdog-panel.yml was absent. The configured hash file existed only in the local test installation, so uploading the jar alone did not transfer or create it.

The plugin now bundles a commented, disabled template and creates it at plugins/NerdSMP/security/watchdog-panel.yml when missing. Existing files are never overwritten. The template contains no usable password, salt or hash, and opens no listener. Users can upload their private configured file or run the secure local setup script with -Rotate to populate the template, then upload it. No per-installation credentials are embedded in the distributed jar.

The existing local file remains enabled at 0.0.0.0:9029 with its original login. Upload that file to the destination's plugins/NerdSMP/security/watchdog-panel.yml and restart; this also works with the previous network-enabled jar. Keep the file private.

## Evidence

- Offline qualityGate build: 1,171 tests; 1,170 passed, one skipped, zero failures/errors. The additional regression loads the packaged YAML and verifies it is disabled, loopback by default, uses port 9029 and contains no usable credentials.
- Jar archive inspection confirms security/watchdog-panel.yml is included.
- Disposable fresh-install Paper fixture runtime/zone-smoke-20261016-205931: PASS and clean shutdown. It verified automatic file creation, disabled/no-credential defaults, repeated startup without rewriting, legacy loopback settings, authenticated wildcard binding and byte-for-byte preservation of configured credentials/network settings.
- Built, fixture-tested and installed jar SHA-256: 114A2239F4D1C763BD3CAADB23DF5C055BF21F959C2D877A9F58178CFA69E743.
- All 115 baseline data files remain byte-for-byte unchanged, including the configured panel file: 27 plugin-data files, 87 world files and server.properties. The player server was not started; no Paper process remains running.

Backup: runtime/backups/panel-first-run-20261016-205919, containing the previous jar, private credential file, baseline manifest and restore notes. Logs: build/panel-first-run-quality.log, build/panel-first-run-paper.log, build/panel-first-run-deploy.log.

Remote hosting/firewall/TLS access still requires deployment-side verification. HTTP remains unencrypted; use trusted networking, VPN or HTTPS. See [the operator guide](../STAFF_FISHING_STATIONS.md).

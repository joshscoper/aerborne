# Tutorial and private support verification — 2026-10-24

Candidate SHA-256: `b409d8e7f5dc2c5277d19d136eedd9425d256dc27ecac0e827162e1fbfaeb20e`.

## Automated checks

`gradlew qualityGate :nerdsmp-paper:shadowJar --offline --console=plain` succeeded. JUnit reports contain **1,693 tests: 1,692 passed, zero failures/errors, one existing skip**. New tests cover support ownership and assignment, duplicate requests, persistent cooldowns/unread counts, release/close/reassignment, message limits, malformed YAML/schema handling, atomic-save failures, tutorial completion/replay/pause, canonical linked-account persistence, and preserving custom tips.

## Real-client fixture

Disposable Paper **26.2-121** on **127.0.0.1:25598**, with four Mineflayer 26.1 clients using the existing ViaVersion/ViaBackwards compatibility fixture. Fresh plugin/account data; the converted map was reused. Staff access was granted through ordinary permission attachments, without OP status. The separate probe plugin is not included in the candidate JAR.

**48 server assertions and 22 independent delivery/evidence checks passed.** Bots performed 10 native menu clicks and recorded 17 inventory windows. The complete fixture lasted about 61 seconds, stopped with exit code 0, and all bots stopped. PID 38540 and ports 25598/25599 were confirmed stopped/closed after the run.

Verified behavior:

- `/guide` opens the standard-client tutorial. Escaping/closing preserves unread state; all eight native Next clicks save their respective read checkpoints. A fresh `PlayerDataStore` reloads completion. The inventory's contents remain equal before and after tutorial interaction.
- The final lesson's website action returns to ordinary inventory and sends a real `open_url` chat component pointing to `https://www.aerborne.net`. `/tips off` persists the tip preference.
- `/helpme` creates one account-owned request and immediately puts follow-up chat into private support mode. Duplicate requests, unauthorized transcript/queue access, unauthorized claims, and replies from another helper are rejected.
- An existing non-OP staff permission inherits support access. Staff claim and select the conversation; plain player and staff chat enters the saved request. A muted player can still ask staff for help privately.
- Actual bot delivery confirms that the assigned helper receives the player's reply and the requester receives the staff reply. The unrelated observer and unassigned helper receive no private messages automatically.
- The requester disconnects; staff sends a saved offline reply. A fresh support store retains both unread staff replies. Reconnecting keeps the account/request but clears connection-local chat selection, delivers the unread notification, and lets the player read the saved reply.
- Release and reassignment work. A former helper's attempted private message is blocked. Closing the request keeps the player's private-chat mode locked; subsequent text is rejected and never becomes global chat. Explicit leave restores normal chat, which all four clients then receive.
- Synthetic duplicate/unassigned/revoked/closed secret markers are never delivered as support messages. No support secret appears in the website/public staff chat console. The requester has access to the saved closed transcript.
- The exact SHA-256 of the fixture's plugin matches the deliverable JAR.

This fixture tests menu and communication behavior, reconnects, and fresh-store reloads. It does not represent earned progression, a full production server restart, or a long-duration load test.

## Host diagnostic

Paper's OSHI system-information startup probe logged one Windows registry error: missing English Perflib 009 performance-counter names, followed by related process-information warnings. The evidence records this exact host diagnostic separately. There were no plugin or unclassified server ERROR entries, client errors, or rejected native clicks. No Windows registry settings were changed.

## Reproduction and evidence

Harness: `testing/tutorial-support-20261024/`. Server-side probe: `scripts/verification/TutorialSupportProbe.java`. `setup.py` creates a new fixture and refuses to overwrite an existing directory. `build-probe.ps1` packages only the isolated probe; copy the built NerdSMP JAR into the fixture's `plugins/NerdSMP.jar`, then run `run.py` and `validate.py`. The fixture's Java server uses a dedicated property and explicit loopback guard. `reset-fixture.ps1` archives prior evidence and plugin data before a repeat.

The output package contains the candidate JAR, command guide, validation JSON, server assertions, tutorial menu receipts and a ZIP of synthetic bot/server evidence and the build log. Production server files were not modified or restarted.

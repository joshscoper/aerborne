# Local Staff Console preview

From the repository root, run:

```powershell
.\testing\staff-console-kit\start-preview.ps1
```

Open http://127.0.0.1:64140/. The process is hidden, bound only to loopback, and
serves the plugin's current HTML, CSS and JavaScript directly. Changes to these
files automatically refresh the preview within a few seconds. Python 3 is required;
the launcher uses Codex's bundled Python if available, otherwise `python` on PATH.

All 123 accounts, reports, moderation evidence and server metrics are fictional.
Actions are intercepted and logged to `preview-actions.jsonl`; they do not contact
Minecraft or alter real accounts. Authentication/security behavior is covered by
`WatchdogPanelTest` and `PanelWebsiteSyncTest`, not by this layout fixture.

`preview.pid` records the helper process ID. Verify that the ID still belongs to
this `preview.py` before stopping it. Logs are in `preview-out.log` and
`preview-err.log`. The `/mobile?width=390` route shows the same UI in a narrow frame.

Create an empty `offline.flag` in this folder to simulate a connection failure;
remove it to restore sample responses. Never copy real credentials or player
records into this fixture.

The Metrics tab uses `metrics-sample.json`, a fictional 100-day history generated
with the production aggregation code by `scripts/verification/MetricsPreview.java`.
It exercises retention cohorts, gameplay and economy views, filters and CSV export.
It is never imported into the plugin. `PANEL_PREVIEW_PORT` can select a second
loopback port (the metrics preview uses 64141) without disturbing the default helper.

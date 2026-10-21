# Staff Console management-suite verification

The plugin-hosted console uses the supplied Aerborne artwork and a shared management
workspace across all nine sections. This is separate from the Flask website admin.

## Second visual pass

- Replaced the large repeated hero banners with compact section headings, restrained
  illustrations and snapshot context. Reworked the sidebar, shared typography,
  contrast, buttons, forms, tables, badges, empty states, inspector and dialogs.
- Overview: four health metrics, a labelled tick-rate trend and staff queues together,
  followed by player, evidence, report and audit feeds. Health messaging reflects
  server performance; moderation attention is shown separately in the queues.
- Players: full-width directory, scope counts, filters, sorting and 50-record paging.
- Locations: four balanced metrics, country distribution and database details.
- Watchdog: evidence and live suspicion before expandable policy and model sections.
- Reports: clearer case cards and search by player, case ID, reporter or report detail.
- Sanctions: full-width bans, mutes and frozen tabs with counts and a separate reason
  column, retaining the existing review/reversal flows.
- Server: consistent metric cards and six charts with time labels and accessible trend
  descriptions. Audit and Staff use the same table and panel system.
- Inspector: six consistent tabs, readable account statistics, grouped actions and
  background scroll containment. Existing focus trapping and return focus remain.
- Mobile: compact section picker instead of a horizontally clipped navigation bar;
  responsive cards, filters, dialogs and independently scrollable data tables.

## Validation

- Final offline Gradle run: all **19** tests in WatchdogPanelTest and
  PanelWebsiteSyncTest passed. These cover actual authenticated listeners, same-origin
  actions/CSRF, attribution, admin sync, strict Host validation, exact static asset
  allowlisting/MIME types, no-store responses, CSP and path traversal rejection.
- JavaScript syntax check passed; final shadow JAR built successfully.
- All nine desktop views were visually inspected in the loopback sample preview.
  All six inspector tabs rendered; the note and report resolution dialogs were
  inspected. Report search selected R002 and its intercepted resolve action retained
  the expected action/report/target/reason payload.
- Directory sorting, 123-record paging (51-100 on page 2), and filter reset to one
  matching player were exercised. Sanction tab switching and empty mutes rendered.
- Dark/light styling was inspected. At 390 CSS pixels the overview, player directory
  and section picker were inspected; selecting Players closed the menu and returned
  focus to its toggle. Wide tables remain horizontally scrollable inside their panel.
- Simulated status failure retained data with an explicit last-snapshot notice; after
  restoring sample responses the warning cleared on refresh.

## Local preview

Run `testing/staff-console-kit/start-preview.ps1` and open http://127.0.0.1:64140/.
It serves current source files and automatically reloads after HTML/CSS/JS edits.
The Python helper and its logs stay in the testing directory. All records are
fictional and actions are intercepted. No game server connection is involved.
See that directory's README for operation and connection-failure simulation.

## Deployment and limits

The rebuilt `nerdsmp-paper/build/libs/nerdsmp-paper-0.1.0-SNAPSHOT.jar` contains the
HTML, scripts, styles and artwork. Install it and restart to load the new resources;
the running plugin caches them. No production server was restarted or reconfigured,
and no live-player action, broadcast, permission change or model training occurred.
Existing authentication, authorization behavior and moderation payloads remain.

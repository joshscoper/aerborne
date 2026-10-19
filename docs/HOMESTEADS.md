# Homesteads: territory and cooperative building

Homesteads are persistent, peaceful group homes. They combine shared residents,
connected land, plot-specific access, and territory-wide rules. Parties remain
temporary adventure groups. There is no raiding, death-based land power, recurring
upkeep, or paid claim capacity.

## Settlement expansion

Homesteads now separate the shared settlement from the homes inside it. Residents
can build together on common land without automatically gaining access to each
other's houses. Menus show the selected homestead and the source of a plot's rules.
This is a focused settlement system, not a drop-in replacement for every Towny
feature or a claim that every Towny server shares the same problems.

| Friction to avoid | Homesteads behavior |
| --- | --- |
| Losing protection because someone missed a bill | No rent, recurring upkeep, automatic tax or inactivity eviction. |
| Town defaults unexpectedly opening a private plot | Explicit plot rules survive default changes; residential plots suppress public-access flags. |
| New members gaining access to every house | Residential access is owner, assigned steward and explicit plot guests only. |
| Having to leave friends to build your own base | Multiple homestead memberships, plus your own land, with a My Homesteads selector. |
| An application granting access before anyone reviews it | Opt-in directory and recruitment; applications require owner/manager approval. |
| Not knowing who changed permissions | Bounded, persisted change history with actor, time and details. |
| Unclear plot purposes | Commons, Residential, Farm, Workshop and Preserve presets, previewed before applying. |

Open `/claim` for the local settlement, `/claim mine` for your memberships or
`/claim directory` for published settlements. Directory publication is voluntary;
existing homesteads are not published during migration. Publication is not an open
invitation to build and does not grant teleportation or reveal a private chat.

## Player controls

- `/claim`, `/homestead`, or `/territory`: dashboard, local map, residents, invitations and rules.
- `/claim create`: claim the chunk underfoot. New chunks touch an existing plot on a side.
- `/claim charter <name>`: name your homestead; `/claim name <name>` names one plot.
- `/claim invite <player>`: invite a resident while standing in your homestead.
- `/claim invitations` or `/claim accept <owner>`: accept within five minutes.
- `/claim trustall <player> <role>`: owner's shared membership for current and future chunks.
- `/claim trust <player> <role>`: override access on one plot.
- `/claim leave <owner>`: leave a friend's homestead without losing your own land.
- `/claim create <owner>`: managers may use that homestead's available chunk allowance.
- `/claim expand`: purchase one extra chunk of your own seasonal allowance using Marks.
- `/claim flags`: switch between plot rules and whole-homestead defaults.
- `/claim flag <flag> <on|off> [all]`: command equivalent.
- `/claim map`, `/claim list`, `/claim show`: inspect territory and local boundaries.
- `/claim delete`: review and confirm release of the exact plot shown.
- `/claim plots` or `/claim plot`: browse land or inspect the current plot.
- `/claim plot type <commons|residential|farm|workshop|preserve>`: preview a preset.
- `/claim plot assign <resident>` or `/claim plot unassign`: owner-reviewed housing assignment.
- `/claim access [player]`: inspect effective access and its source on this plot.
- `/claim trust <player> inherit`: remove a local role override rather than deny access.
- `/claim flag <flag> inherit`: restore the homestead default for one rule.
- `/claim publish <on|off>` and `/claim recruiting <on|off>`: directory/recruitment controls.
- `/claim apply <owner>` or `/claim withdraw <owner>`: request or withdraw membership.
- `/claim applications`: owners/managers review applicants; applying grants no access.
- `/claim invite cancel <player>`: revoke an invitation; `/claim decline <owner>` declines one.
- `/claim bulletin <text>`: set a plain-text notice (160 characters); `clear` removes it.
- `/claim history`: inspect the latest 100 settlement changes as an owner/resident.
- `/claim chat <owner> [message]`: send a message or select resident-only chat.

For remote settlement management, `/claim home <owner> [subcommand]` selects an
explicit home. It supports the settlement-level controls shown in its help, not
remote block claiming or an implicit retarget of plot commands. Names must resolve
uniquely to an authenticated saved account; the canonical account UUID also works.

Default capacity is four chunks across dimensions. Each dimension may have its own
first plot, but subsequent plots in that dimension must be adjacent. Releasing a
bridge that would split land is refused. Old disconnected land may remain; removal
must not increase its number of connected components.

The map is inspection-only, not remote claiming. Destructive confirmations capture
the plot ID and coordinates, not the player's position when they eventually click.

## Trust roles

| Role | Granted access |
| --- | --- |
| Build | Building and landscaping, not storage destruction. |
| Container | Storage, livestock custody, mounts and decorations; no building. |
| Redstone | Controls, not building or storage. |
| Resident | Build, container and redstone together. |
| Manage | Resident access plus plot rules; homestead-wide managers also invite residents and claim adjacent land. |
| Owner | All of the above, role assignment and releasing land. |

Only owners assign shared roles or release land. A residential steward can assign
limited local guest roles on their own plot, but not Manage or Owner. Managers cannot grant ownership.
A local manager cannot manage other plots or spend the homestead's free capacity.

Use `none` with trust to deny a shared member access on one plot. With trustall it
removes membership. Changing an existing shared role preserves local overrides.
Removing or leaving a membership clears that player's local trust and stewardship.
Players can help multiple homesteads while retaining
their own; joining does not transfer Marks, capacity or ownership.

### Residential plots

Only the homestead owner can assign a housing steward or switch land into/out of
Residential. The steward must already be a shared member and gets Resident access
on that home. This is stewardship, not a transfer of the homestead or its capacity.
The owner and explicit staff bypass retain access; ordinary residents and shared
managers do not. The steward may name the plot and invite local guests through
plot trust, but cannot change environmental rules, release land, or promote a manager.

Assigning/reassigning a steward clears old plot guests. Removing the steward or
their membership clears those guests and leaves the Residential plot locked under
the owner, rather than opening an abandoned house to the whole settlement.
Converting a plot back to shared land deliberately ends that privacy; it requires
an owner confirmation. No automatic eviction, sale, rent, or property compensation
is implemented. Agree on expectations with the owner before building.

### Land-use presets

- Commons: inherit the homestead's default rules.
- Residential: private membership, no public access or consensual PvP by default.
- Farm: visitors can harvest mature crops, replant, care for animals and use ordinary
  stations; storage stays closed and building still requires a role.
- Workshop: public workstations and ordinary controls, not public storage or farming.
- Preserve: no public activity, terrain damage, natural hostile spawning or PvP by default.

Applying a preset resets that plot's flag overrides. The confirmation explains the
scope first. These are access presets, not special machines, production boosts or
safe zones that cancel all combat. Plugin/staff mob summons retain existing exemptions.

Names resolve through authenticated saved NerdSMP accounts, including offline
players. Ambiguous names are rejected; supply the canonical account UUID instead.
No offline-mode UUID is invented from a typed name.

## Rules

| Flag | Default | When enabled |
| --- | --- | --- |
| explosions | Off | Explosions may damage terrain. |
| fire-spread | Off | Fire may spread and burn blocks. |
| mob-griefing | Off | Mobs may alter terrain and interact with property. |
| public-interact | Off | Visitors may use ordinary controls and workstations. |
| public-containers | Off | Visitors may open storage, but cannot break it. |
| public-harvest | Off | Visitors may harvest mature crops and replant supported seeds. |
| public-animals | Off | Visitors may feed, breed, milk and shear sheep, not take animals away. |
| hostile-spawning | On | Allow ordinary hostile spawning; plugin/staff summons are exempt. |
| pvp | On | Allow otherwise-consensual PvP. Off vetoes combat; On never enables forced PvP. |

Whole-homestead changes update defaults for current and future plots, but preserve
explicit local overrides. A plot rule can be explicitly On/Off or Inherited.
Residential plots always suppress the public-access flags, even if a saved override
or the town default requests them. Public storage never permits cross-border hoppers.

Protection covers direct building/storage, multi-block placement, buckets,
livestock/vehicles, hanging objects, armor stands, projectiles, potions, explosions,
fire, portals and TNT. Fluids, pistons, dispensers, hoppers, tree growth and falling
blocks are checked at ownership boundaries. Automation can cross plots within the
same shared homestead. It cannot cross into wilderness, another owner's land, or a
private residence with a different steward. Adjacent Residential plots assigned to
the same steward can share automation; vacant residential plots are isolated from
each other. Mobile hopper transfers and item pickup inside residential land are
disabled, preventing carts from entering a house, collecting loot and leaving.

Both halves of a border-straddling double chest require permission. Graves retain
separate owner-only access. NerdSMP NPCs remain interactable through their manager.
Open physical storage is revalidated on click/drag, so keeping a chest open does not
retain access after trust is revoked. Custom menu inventories keep their own guards.

## Recruitment, notices and chat

Applications require both publication and recruitment to be enabled. Applications
last seven days, with at most 32 pending per homestead and five active per applicant.
Duplicates do not create additional entries or access. Approval rechecks the member
limit and seats reserved by current invitations; normal invitations still expire
after five minutes. Listing or recruitment changes do not revoke existing residents.

The bulletin is a short settlement notice, suitable for house rules or today's
building plans. It is visible with the settlement overview and is not a private
message or a quest/reward system. Published notices should not contain sensitive data.

Private chat includes the owner and current shared members, not applicants, invitees
or plot-only guests. It respects mutes and ignores. `/claim chat <owner>` selects
the channel; `/channel global` exits it. Choose again after reconnecting/restarting.
If membership or account identity changes, a private message is refused rather than
falling back to global chat. Each message rechecks current membership.

The change history is saved in the same update as the change itself; a failed write
publishes neither. It retains only the newest 100 entries per homestead and is not
a permanent, tamper-proof staff audit or rollback tool. No automatic block rollback
is performed when changing a role, steward or plot type.

## Server reserves

New configuration keys under `claims`:

```yaml
maximum-residents: 32
spawn-buffer-blocks: 0
spawn-worlds: [world]
reserved-regions:
  - world: world
    min-chunk-x: -4
    min-chunk-z: -4
    max-chunk-x: 4
    max-chunk-z: 4
    buffer-chunks: 1
```

Spawn reserves are opt-in so this update does not silently take existing homes
out of use. Buffers protect whole intersecting chunks. Explicit regions use inclusive
chunk coordinates. Malformed coordinates or unknown region worlds stop initialization.

Claims must fit entirely inside the startup world-border snapshot. Restart after
editing reserves, moving spawn, or resizing/recentering borders. Checks require no
terrain reads. Reserved regions retain public workstations, doors/buttons and NPC
access while blocking building and storage.

`nerdsmp.claim.bypass` (default: op) permits staff maintenance without transferring
ownership or granting owner-only management commands.

## Persistence and migration

Back up `player_data/` before deployment. No wipe is required.

- Existing chunks retain IDs, coordinates, names, trust and flags.
- Claims remain under `seasons.<season>.claims` in `player_data/<uuid>.yml`.
- Schema 3 is saved on the next change. Legacy plot IDs, ownership, capacity and
  effective flags are preserved. Old flag values differing from the homestead
  defaults become explicit overrides; matching values inherit. The old format
  cannot reveal whether a matching value was deliberately set, so review rules
  before changing settlement-wide defaults. Schema-1 plots retain their previous
  normal-spawning and consensual-PvP migration defaults.
- Existing plots start as Commons; no home becomes private or publicly listed automatically.
- Old trust stays plot-specific; use invitations/trustall for membership inherited by future plots.
- Build, container and redstone are now separate capabilities. Use Resident for combined everyday access.
- Lowering the configured maximum stops purchases without erasing already-purchased capacity.
- All known owners load at startup, protecting offline players' land.
- Conflicting ownership and malformed claim/YAML records fail closed.
- Failed writes do not publish partial player or protection state.
- A season change resets land/membership but keeps account identity.

Persistence remains synchronized local YAML. Profile frequent edits and larger land
counts before public scale. Claim-capacity payments still cross separate economic
and claim save boundaries; abrupt-crash reconciliation remains future work.

## Acceptance checks

Automated tests cover authority, member inheritance, overrides, invitation expiry,
replay and caps, manager limits, connectivity, dimensions, migration, restart,
conflicting records, failed writes, reserve bounds, and fluid/piston event handling.

Still perform a live Java/Bedrock pass with owner, resident, visitor and staff:

1. Claim, invite, accept, rename, override one private plot and restart.
2. Try each role with containers, controls, animals and decorations.
3. Test hoppers, double chests, piston push/pull, water/lava and falling sand on all borders.
4. Toggle each public rule and verify it grants only its stated access.
5. Check spawn workstations, NPCs, graves and staff maintenance.
6. Test rapid clicks, closing menus, changing ownership during confirmation, and both client layouts.

Unit tests and startup checks are not a substitute for client visual QA.

Additional settlement acceptance checks:

1. Publish/recruit, apply with another player, approve, and use My Homesteads remotely.
2. Assign two adjacent homes to one resident and a third to a different resident.
   Try all roles, visitors, public flags, hoppers, carts, fluids, pistons and double chests.
3. Keep storage open while another account revokes trust or reassigns the home.
4. Set a local rule, change its homestead default, and verify the override survives.
   Reset to Inherited and verify both present and future default changes take effect.
5. Change a shared role without losing a local denial; remove the steward and ensure
   the house stays private and old guests lose access.
6. Send settlement chat while muted, ignored, removed, reconnected and relinked.
   Confirm that no private message reaches outsiders or silently becomes global.
7. Restart with migrated data and check IDs, roles, effective flags, applications,
   notices and history. Test save failure before opening the public season.

Deferred: shared treasuries, paid plots, rent, ownership transfer/succession,
district-wide bulk edits, outposts, nations/diplomacy and resource-contribution
projects. Those are not hidden working features of this release. Existing separate
economy/claim-capacity crash boundaries remain listed in the code review.

## Verification — September 26, 2026

- `gradlew.bat --no-daemon qualityGate build`: passed, **356 tests**, zero failures,
  errors or skipped tests. This includes 42 new settlement, menu-policy, private
  chat and storage-protection tests.
- `run-test-server.cmd -PrepareOnly`: passed. Built and staged jar SHA-256:
  `C915A46FE1F558C44C66E584A5755B398B08D5DA15EC04E55BE2FD4555C8DD9D`.
- Paper 26.2 build 121 / Java 25 startup passed; NerdSMP and Nexo enabled,
  the approved Nexo pack upload completed, and claim/homestead/channel command
  registration resolved. The durable market loaded with no pending recoveries
  or review cases. No NerdSMP initialization or shutdown exception was observed.
- Clean shutdown, exit code 0. No players joined. All six backed-up player save
  files (plugin YAML and Minecraft saves) remained byte-for-byte unchanged.
- Previous plugin jar, plugin data, server properties, generated Nexo pack and
  player saves are in `runtime/review-backups/settlements-20260926-233225/`.
  Its `smoke-startup.log` records the local test. No wipe or account reset occurred.
- The pre-existing Windows OSHI/Perflib performance-counter error still appears;
  no registry changes were made. The pinned Paper version was not upgraded.

The test server is stopped; start it with `run-test-server.cmd`. These checks do
not establish live multiplayer protection, GUI appearance, Bedrock parity or
production load performance. Complete the acceptance checks above before release.

## Design references

[Towny](https://github.com/TownyAdvanced/Towny/wiki/How-Towny-Works) informed territory
defaults and plot exceptions. [Factions](https://factions.support/permissions/)
informed action-specific trust. [KingdomsX](https://kingdomsx.com/) informed central
group management. Homesteads deliberately omit the war, power and raiding loop.
Towny's official documentation already describes configurable roles, plot groups,
permissions and upkeep; those capabilities are not assumed to be missing. The
comparison above describes this server's chosen defaults and UX goals, not a
measurement of how frequently Towny players encounter any particular problem.

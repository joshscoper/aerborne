# Staff, Watchdog and workshop update

## Staff Console

This test installation is configured to bind **0.0.0.0:9029** (all IPv4 interfaces). After starting the Skyblock server, open **http://127.0.0.1:9029** locally or **http://<server-LAN-IP>:9029** from another computer. `0.0.0.0` is a listening address, not the address to enter in a browser. The requested `j0sh` account is configured; use the existing password. The password is not stored in source, application logs or plaintext configuration.

The Staff Console is the web page staff use to watch and moderate the server. Watchdog is one of its views. Its views:

- **Overview:** server status, open reports and the latest alerts.
- **Players:** everyone online, plus every account that has joined, with location, ping, health, client and live Watchdog score.
- **Locations:** players by country from each account's last-known location, with online counts and top regions and cities. See [PLAYER_LOCATIONS.md](PLAYER_LOCATIONS.md).
- **Watchdog:** evidence, which you can filter by check, time and outcome, with live suspicion scores.
- **Reports:** the report queue, where you can resolve cases.
- **Sanctions:** active bans, mutes and freezes.
- **Server:** TPS, tick time, CPU, memory and entity charts for the last hour, plus world and runtime details.
- **Audit log:** the staff audit log.
- **Staff:** staff groups and the access-change log.

Click any player to open their profile: live stats, inventory and ender chest, Watchdog evidence, case history and staff groups.

The profile's **Connection** tab shows:

- **Client:** client brand, game version compared with the server's, language, view distance, and the hostname they connected through.
- **Mods:** mods detected from the plugin channels the client announces, such as Fabric API, Xaero's Minimap or Simple Voice Chat. Clients can hide or fake these, so treat them as hints.
- **Addresses:** current and last known IP address, and the last 10 addresses with first seen, last seen, join counts and coarse location (city, region, country).
- **Shared accounts:** other accounts seen on the same address. These also appear as a warning on the Profile tab and a *Shared IP* badge in the player list.
- **Sessions:** session count, average and longest session, and total playtime.

Connection history is kept in `plugins/NerdSMP/security/connections.yml`. It contains IP addresses, so keep it private. Session averages count from the first join after this feature was installed; total playtime comes from the game's statistics. Set `anti-cheat.record-ip-addresses: false` to stop recording addresses; stored addresses are dropped on the next save. Locations are looked up offline against a local database, so the console never sends addresses to an outside geolocation service; `anti-cheat.geolocation.enabled: false` turns them off.

From the console you can message, warn, add notes, mute, freeze, kick, ban or unban players. You can also clear a live Watchdog score, resolve reports, add or remove staff groups and broadcast to everyone.

The Staff Console login acts with **server-console authority**. Every change is written to the staff audit log as `Panel (<username>)` and to the server log. Bans use the server's own ban list, so `/pardon` still works. There is deliberately no arbitrary console-command or payment endpoint.

Actions are protected in several ways:

- An HTTP Basic login is required.
- Each action carries a per-start session token in a header.
- Cross-site requests are refused, using `Origin` and `Sec-Fetch-Site`.
- Requests must be JSON bodies of at most 8 KB.
- Actions are limited to 40 a minute, on top of the general limit of 240 requests a minute.

The page refreshes every five seconds while it is open. Game-thread snapshots also refresh every five seconds, and the console keeps one hour of history. HTTP worker threads never touch Bukkit entities: actions and player profiles run on the main thread and wait at most five seconds.

Because the Staff Console can now ban players, keep it on loopback or behind a VPN or HTTPS reverse proxy.

Authentication uses salted PBKDF2-HMAC-SHA256, 600,000 iterations and a 256-bit derived key. Every route requires authentication. Responses are no-store, with framing/content-type/CSP protections; JSON is escaped and rendered with text nodes. The listener validates Host, accepts GET only, and limits requests to 120 per minute across the listener. Too many requests temporarily lock out the Staff Console, not Minecraft. Binding to loopback still refuses nonlocal peers; network bindings require the same login protections.

**HTTP does not encrypt your login credentials or staff evidence.** Restrict access to a trusted network or VPN, or put HTTPS in front of it before public exposure. Binding all interfaces does not configure Windows Firewall, NAT/port forwarding or TLS. Those settings are unchanged. The web account is separate from Minecraft staff permissions.

Settings are local to `plugins/NerdSMP/security/watchdog-panel.yml`. Uploading the jar does **not** transfer the private credential settings from your computer. To deploy this configured test installation, stop the destination server, upload `runtime/skyblock-test/plugins/NerdSMP/security/watchdog-panel.yml` to that same `plugins/NerdSMP/security/` path on the destination, then start the server. Keep this file private: it contains your salted password hash.

Fresh installs now generate a commented, disabled template at that path automatically. The template has empty salt/hash values and no usable default login. Copy a configured private file over it or use the secure setup script below; do not simply switch `enabled` to true or enter a plaintext password. Existing settings are never overwritten by the plugin. Malformed settings are preserved and do not create a listener. A port conflict disables the Staff Console rather than gameplay. Configure or rotate credentials on your computer with:

```powershell
.\scripts\configure-watchdog-panel.ps1 -Rotate -Bind 0.0.0.0
```

The script securely prompts for a replacement password and keeps the previous hash configuration as `.previous`. New configurations, including rotations without `-Bind`, default to loopback. To change the bind without changing credentials, edit only `bind: '0.0.0.0'` (network) or `bind: '127.0.0.1'` (local) in the existing file. Restart to activate changes. Old files without `bind` remain loopback-only. Clear browser site credentials when finished; HTTP Basic credential caching is browser-dependent.

Direct IPv4 requests are accepted only when Host matches the actual local destination IP and port. Local `localhost:<port>` also works. For a domain, public NAT address or reverse proxy, add exact HTTP authorities to the optional `allowed-hosts` list; for example:

```yaml
bind: '0.0.0.0'
port: 9029
allowed-hosts:
  - 'staff.example.com'       # HTTPS proxy forwarding this Host unchanged
  - 'staff.example.com:9029'  # Direct access with an explicit port
```

These are additional fields in the existing credential file, not a replacement for it. Do not remove its schema/enabled/username/salt/hash/iterations fields. Wildcards, URLs and unlisted hostnames are refused. The setup script accepts `-AllowedHosts 'staff.example.com','staff.example.com:9029'`; pass the list again when rotating. Authentication remains mandatory for every allowed host. Reverse proxies should use TLS, preserve an explicitly allowed Host or send the backend IP/port, and keep port 9029 restricted to trusted peers.

### Website administrators

The Staff Console can let every **admin** on the Aerborne website sign in with their website username and password, so you don't create panel logins by hand. Every minute or so the panel fetches the admin list from the website. The website sends only admin usernames and their salted PBKDF2-SHA256 password hashes (600,000 iterations), never a password. The panel checks a login against that hash.

- New website admins can sign in within a minute. Deactivated or demoted admins are refused at the next sync, even if their browser is still signed in.
- If the website can't be reached, the last list keeps working for `max-age-hours` (default 24), then website logins stop. The yml login keeps working as a break-glass account, and it always wins if a website admin has the same name.
- Website admins who still have the default password, or an older weaker hash, aren't sent until they sign in to the website once. The website's **Staff accounts** page shows who has panel access and when the panel last fetched the list.
- Actions are recorded as `Panel (<website username>)`, the same as other logins.

To turn it on, add this section to the existing `plugins/NerdSMP/security/watchdog-panel.yml`. The plugin never rewrites that file, so new keys aren't added for you.

```yaml
allowed-hosts:
  - 'panel.aerborne.net'   # the HTTPS reverse proxy, forwarding this Host unchanged
website-sync:
  enabled: true
  url: 'https://aerborne.net/admin/api/panel-accounts'
  interval-seconds: 60
  max-age-hours: 24
```

Restart the server. It creates `security/watchdog-website-token.txt`. Copy that value into the website's `AERBORNE_PANEL_SYNC_TOKEN` environment variable in cPanel and restart the website. Keep the token private: anyone who has it can read the admins' password hashes. With sync on, `salt` and `hash` may be left blank if you don't want a break-glass login; with both blank and the website unreachable, nobody can sign in.

The game server needs outbound HTTPS to the website. The sync URL must be `https://` (plain `http://` is allowed only for `localhost` testing), and redirects are not followed.

## Internal staff permissions

`/permissions` (aliases `/staffperms`, `/nperms`) manages global **NerdSMP** permission nodes through standard Bukkit attachments. Existing command, protection and GUI permission checks continue to work. No player receives operator automatically. Existing operators retain their normal access.

| Group | Access |
| --- | --- |
| helper | Staff desk, reports, read-only inspections and history |
| moderator | Helper access, moderation and Watchdog evidence |
| administrator | Moderator access, market reconciliation, NPC and warp management |
| builder | World/content building tools, not player moderation or permission ownership |
| designer | Item, mob, loot, recipe and NPC authoring, not permission ownership |
| owner | Full NerdSMP administration and permission ownership |

Only owners with `nerdsmp.permissions.manage` or the server console can edit access. Ordinary administrators do not receive this node. Moderation compares effective capability tiers and protects equal/higher staff, including offline assignments. Operators are protected above assigned owners; use console for owner/operator actions. Notes and report review remain available as administrative records.

Run from console to bootstrap an owner, using their online name or exact authenticated Java UUID:

```text
permissions user <online-name-or-UUID> add owner
permissions user <online-name-or-UUID> add moderator
permissions user <online-name-or-UUID> remove moderator
permissions user <online-name-or-UUID> info
permissions group eventguide create
permissions group eventguide parent helper
permissions group eventguide permission nerdsmp.travel.admin true
permissions user <online-name-or-UUID> permission nerdsmp.staff.moderate false
permissions user <online-name-or-UUID> permission nerdsmp.staff.moderate unset
permissions audit
```

Offline names are not guessed; supply a UUID. Custom group names are lowercase. Inheritance cycles and unknown groups are refused. Group-level denies win inherited conflicts; user-specific rules override groups. `unset` removes a rule. `nerdsmp.*` and narrower wildcards expand registered Bukkit nodes. Foreign plugins' permissions, world/server contexts, timed memberships and network synchronization are not implemented; this is an internal permissions engine, not full LuckPerms feature parity. Do not run competing grants/denies for the same nodes without reviewing the effective permissions.

Changes are atomically persisted to `permissions.yml` with the latest 2,000 audit entries, then applied to online players and their command lists. Malformed/unknown-schema files fail closed rather than resetting staff. Failed saves do not publish new assignments. Back up the registry and treat `permissions.manage` as ownership, not a convenience for helpers.

## Co-op fishing

Open `/fishing` or the island fishing journal to see the co-op fishery, habitat selection and equipment route. Only accepted, authenticated island catches count; canceled/forged events and nonmembers do not advance it. Progress belongs to the island owner's seasonal record and does not reset existing fishing or collection data.

| Fishery tier | Requirement | Shared bonus |
| --- | --- | --- |
| 1 | 100 island catches | +2% luck, +2% bite speed |
| 2 | 400 catches across 3 habitats | +4% luck, +4% bite speed |
| 3 | 1,000 catches across all 5 habitats | +8% luck, +6% bite speed |

Habitat access still follows island resource milestones. The existing combined bite-speed cap of 55% remains. Fishery luck stacks with island talents and equipment. Uncommon treasure can supply prismarine shards; rare treasure can supply prismarine crystals, giving anglers a route into Deepcurrent equipment. Authentic species still feed Ivo's reputation and physical collection turn-ins; those systems are not replaced by merely catching an item. Journal receipts are bounded to the latest 256 hooks, with the existing accepted-event replay guard retained.

## Station interfaces

- **Forge:** sockets, improvements, tomes and repairs have focus tabs. Auto Detect keeps the combined workflow available. Switching tabs keeps both inputs; repair focus presents item/all repairs together, and improvement focus shows the next tier and material/Smithing requirements. Existing movable inputs, return capacity and confirmation safeguards remain.
- **Enchanting:** selecting an offer now opens before/after equipment previews and a Dust-cost confirmation. Confirmation rechecks the exact held item, hotbar slot, offer, station and Dust. Changed offers/items are refused; no XP levels are spent.
- **Alchemy:** potion strength and bottle forms use a labeled, centered 3×3 matrix. All potion, arcane brew, poison and antidote production opens a result-and-ingredients review, with plain-material counts, refresh and a Brew One confirmation. Inventory planning rechecks ingredients and space for output and returned containers. Back/close controls are standardized. Poisons still support drag coating.
- Station screens use owner/account-bound click handling, strict normal clicks and deferred UI transitions. No shift/number/double-click action bypass is intended. Forge input movement remains an explicit exception governed by its slot policy.

No existing accounts, worlds, gear or currencies are reset. Real-player GUI, moderation, long-session fishing and save/restore acceptance remain necessary before public release; see the verification record for the automated evidence and its limits.

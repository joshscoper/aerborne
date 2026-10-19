# Aerborne website

The official site for Aerborne: home page with live server status, Play, Explore, the
Adventurer's Handbook, the Arcane Codex, the Companion encyclopedia, News with RSS, a cosmetic
Store catalog ready for Tebex links, community forums, member profiles, ranks, five Arcade games and Support. Staff edit everything from a built-in
admin at `/admin`. Built for **Python 3.6.15** on cPanel, with SQLite, so there's no database
server to set up.

```
Website/
  passenger_wsgi.py     cPanel entry point (exposes `application`)
  manage.py             create-admin, set-password, run (local)
  requirements.txt      Flask 2.0.3 and friends: the last releases that support Python 3.6
  aerborne/
    __init__.py         app factory, navigation, filters, error pages
    config.py           environment-variable settings
    content_types.py    every editable content type and its fields (the admin is built from this)
    public.py           public pages, RSS, sitemap
    api.py              read-only JSON at /api/v1/
    admin.py            staff admin: editors, publishing, media, staff, settings, audit log
    auth.py, security.py  staff sign-in, roles, CSRF, login throttling, security headers
    db.py               SQLite schema and automatic column migrations
    seed.py             starter content written from ../docs, added once on a fresh database
    status.py           live player count via Minecraft's Server List Ping (standard library only)
    media.py            upload checks and WebP re-encoding
    templates/, static/ pages, CSS, JS and artwork (branding kit images live in static/img/kit)
  tests/                python -m unittest discover tests
  instance/             created on first run: database, uploads, secret key (keep it private)
```

## Upload to cPanel

1. **Upload.** In File Manager, create a folder **outside `public_html`**, for example
   `/home/<you>/aerborne-website`. Upload `Website.zip` there and choose **Extract**.
   `passenger_wsgi.py` should sit at the top level of that folder.
2. **Create the app.** Open **Setup Python App** and click **Create Application**.
   - Python version: **3.6.15**
   - Application root: `aerborne-website`
   - Application URL: your domain or subdomain
   - Application startup file: `passenger_wsgi.py`
   - Application entry point: `application`
3. **Install packages.** Under *Configuration files* add `requirements.txt` and click
   **Run Pip Install**, or paste the `source …/activate` command shown at the top of the app page
   into **Terminal** and run `pip install -r requirements.txt`.
4. **Add settings** under *Environment variables* (table below). Set at least
   `AERBORNE_SERVER_ADDRESS` and `AERBORNE_SITE_URL`.
5. **Sign in at `/admin`** with the default admin account `j0sh` and its default password. The
   first sign-in makes you choose a new password (12+ characters) before anything else opens, so
   the default never stays in use. Add more staff under **Staff accounts**.
6. Click **Restart** and open the site.

After uploading new files or changing variables, click **Restart** (or touch `tmp/restart.txt`).
When you upload a new version, **don't overwrite the `instance/` folder**: it holds your
database, uploads and secret key. Back it up from time to time.

## Settings

| Variable | Default | What it does |
|---|---|---|
| `AERBORNE_SERVER_ADDRESS` | `play.aerborne.net` | Java address players type. |
| `AERBORNE_API_URL` | `https://api.aerborne.net` | The plugin's web API, for leaderboards, profiles and account linking. |
| `AERBORNE_API_TOKEN` | *(blank)* | Private token from `plugins/NerdSMP/security/web-api-token.txt`. Needed for `/weblink` account linking and password resets. Server-side only. |
| `AERBORNE_SERVER_PORT` | `25565` | Shown after the address only when it isn't 25565. |
| `AERBORNE_MINECRAFT_VERSIONS` | `26.2` | Versions named on the Play page, e.g. `26.2` or `26.1–26.2`. |
| `AERBORNE_BEDROCK_ADDRESS`, `AERBORNE_BEDROCK_PORT` | *(blank)*, `19132` | Leave blank: Aerborne is Java-only. Only set once Bedrock access is verified. |
| `AERBORNE_PANEL_SYNC_TOKEN` | *(blank)* | Lets the Watchdog panel fetch website admins. Copy it from `plugins/NerdSMP/security/watchdog-website-token.txt`. Blank turns sync off. |
| `AERBORNE_PANEL_URL` | `https://panel.aerborne.net` | Linked from the admin sidebar for admins. |
| `AERBORNE_SITE_URL` | *(request host)* | Full `https://` address of the site, used in RSS and the sitemap. |
| `AERBORNE_DISCORD_URL`, `AERBORNE_CONTACT_EMAIL` | *(blank)* | Shown in navigation, Support and the final call to action. |
| `AERBORNE_REWARDS_TOKEN` | *(blank)* | Private token for the game server to collect Arcade payouts. Blank leaves rewards queued and shows a delivery notice. |
| `AERBORNE_STATUS_HOST`, `AERBORNE_STATUS_PORT` | the address above | Where the site pings for live status, if different. |
| `AERBORNE_STATUS_CACHE_SECONDS` | `30` | How long one status check is reused. |
| `AERBORNE_SECRET_KEY` | generated | Session signing key. Generated into `instance/secret_key` if unset. |
| `AERBORNE_DATA_DIR` | `instance/` | Where the database and uploads live. |
| `AERBORNE_SECURE_COOKIES` | `1` | Staff cookies need HTTPS. Set `0` only for local testing. |
| `AERBORNE_SETUP_TOKEN` | *(blank)* | Unlocks `/admin/setup` while no staff exist. Remove after use. |

Announcements, maintenance mode, the Community intro and extra footer links are edited in
**Admin → Settings** instead.

## Staff admin

- **Roles.** Admin (everything), Editor (write and publish), Writer (draft and send for review;
  can't edit live pages), Moderator (community builds and forum moderation).
- **Workflow.** Draft → In review → Published, plus Archived. A future *Publish at* time schedules
  a post. **Preview** shows any draft exactly as it will look.
- **Audit log.** Every sign-in, edit, publish, upload, delete and settings change is recorded
  with who did it and what changed.
- **Media.** PNG, JPG, GIF or WebP up to 8 MB. With Pillow installed, uploads are verified,
  stripped of metadata and re-saved as WebP at 1600 px and 480 px.
- **Admin view and player view.** While signed in as staff, the public site shows a purple bar
  with links to the dashboard and an "Edit this…" shortcut for the page you're on, and staff with
  the admin or moderator role can moderate the forum in place. **View as a player** hides all of
  that so you see exactly what visitors see (a small pill brings the tools back). Sign in to a
  player account in that view to post like a regular player. Staff visits aren't counted in page
  views.
- **Safety rails.** Community builds can't be published without the builder's permission ticked.
  A store item can't be marked on sale without an exact price, a Tebex link and a published
  `store-terms` page. Hidden codex recipes never appear on the page, in filters or in the API.

## Watchdog panel sign-in

Website **admins** can sign in to the Watchdog panel at `panel.aerborne.net` with their website
username and password. The panel fetches the admin list from `/admin/api/panel-accounts` about
once a minute, using the shared `AERBORNE_PANEL_SYNC_TOKEN`. Only usernames and salted PBKDF2
hashes are sent, never passwords. Deactivating or demoting an admin removes their panel access at
the next fetch.

Staff passwords are hashed with 600,000 PBKDF2 iterations, the minimum the panel accepts. Older
hashes are upgraded the next time that person signs in. Until then, and until the default admin
changes its password, they don't get panel access. **Staff accounts** shows each admin's panel
status and when the panel last fetched the list. Server-side setup is in
`docs/STAFF_FISHING_STATIONS.md` under "Website administrators".

## Player accounts and the forum

- **Accounts.** Players sign up at `/account/register` with a username and password. No email is
  collected. Sign-ups are rate limited per connection and have a hidden field that traps bots.
- **Linking Minecraft.** In game, `/weblink` gives the player a private 8-character code valid
  for 10 minutes. They enter it at `/account/link`; the site asks the game API who owns the code
  and links that Minecraft account. Each code works once and each Minecraft account links to one
  website account. Linked players get a verified badge and a link to their player profile.
- **Password resets** use the same code at `/account/recover`, so a linked account can always be
  recovered without email.
- **Posting** needs a linked account by default (Settings → *Posting needs a linked Minecraft
  account*). Threads and replies are rate limited. Player posts allow basic Markdown only: no
  images, no headings, and all HTML is stripped.
- **Moderation.** Players can report posts. Staff with the admin or moderator role see reports under
  **Forum → Reports**, set member roles and bans under **Forum → Members**, and manage categories.
  Forum members can also be made *Moderator* (hide posts, pin and lock threads from the forum
  itself) or *Aerborne team* (also posts in Announcements). Every action is in the audit log.
- **Switches** in Settings: forum open, sign-ups open, linking required.

## Connecting the game API

Leaderboards, player profiles and account linking read from the plugin's web API. The updated
plugin jar adds these endpoints:

| Endpoint | Access | What it returns |
|---|---|---|
| `GET /api/v1/leaderboards` | public | Top 25 on each of the six boards, refreshed once a minute |
| `GET /api/v1/players/<name>` | public | Adventurer level and rank, skill levels, board standings |
| `GET /api/v1/links/<code>` | bearer token | Which player asked for a `/weblink` code (expires after 10 minutes) |

Profiles only include what players already see in `/leaderboard` in game.

To switch it on:

1. Deploy the new `NerdSMP` jar from `nerdsmp-paper/build/libs/` and restart the server.
2. In `plugins/NerdSMP/security/web-api.yml`, add `api.aerborne.net` to `allowed-hosts`, then restart.
3. Point `api.aerborne.net` at the API port (9028 by default) through an HTTPS reverse proxy. Today
   that address answers every path with an empty page and its HTTPS certificate doesn't match the
   name, so the site shows "offline" until this is set up.
4. Copy `plugins/NerdSMP/security/web-api-token.txt` into the website's `AERBORNE_API_TOKEN`
   variable. Keep it out of any browser-side code.

Until then the site works normally and says plainly that leaderboards and profiles are offline.

## What's in the starter content

Written from the plugin docs in `../docs`, so it matches the game today:

- **Published:** the five-step Getting Started path and other Handbook guides, all 15 Arcane
  enchantments and 3 Signatures, the nine Aspects, three regions, six companions and eight trails
  (all labelled as concept art / upcoming), FAQs, rules and support pages, one welcome post.
- **Marked Preview:** Arcane Signatures, Bloodletter, Veinseeker and Chain Harvest. They're off by
  default in production (`arcane-forge` config). Switch them to Released in the admin once enabled.
- **Hidden recipes:** only the one-Aspect Fundamentals show their Aspects. Tick *Show its Aspects
  publicly* on any entry you're happy to reveal.
- **Public catalog:** supporter ranks, bundles, companions and trails. Checkout stays unavailable until a price and Tebex link are added and availability is Released.
- **Published policies:** terms, privacy and store terms. Staff can edit them under Pages. Updates preserve staff edits. Have them checked by someone qualified before you rely on them, especially once Tebex sales start.
- **Expanded handbook:** island milestones, machines, spawners, co-ops, fishing, farming, excavation, cooking, daily goals, trading, banking, gems, graves and parties, plus session planning and website help. Search `/guide` by topic or command.

## Community, Discord and Tebex setup

**Discord:** Admin → Settings → Discord invite link. Until it is filled in, every Discord button opens a holding page. `AERBORNE_DISCORD_URL` is a fallback.

**Profiles:** members edit avatars, banners, headlines, bios, island names, favourite skills, social links, signatures and game-stat visibility at `/account`. Social details are public. Assign ranks and custom titles in Admin → Forum → Members; manage rank colours, frames and permissions under Ranks. The seven supplied avatar frames are included in `static/img/frames/` (256 and 512 px WebP, plus a mask of each frame's opening). Each rank picks its frame in the rank editor: Owner and Admin use Admin, the other staff ranks use Staff, supporter ranks use their own frame, and the Media rank (for content creators) uses Media. Members without a rank get the Member frame. Character avatars are cropped to their artwork so they fill the opening; other avatars fill it as they are.

The profile editor groups appearance, personal details and links into keyboard-accessible tabs with a live member-card preview. Sixteen supplied character avatars live in `static/img/avatars/` (128 and 256 px WebP); Sky Sentinel is the default. Existing classic avatar choices remain available. The Skybound city banner uses the supplied forum painting. Public profiles separate the member's introduction, forum activity and game details.

**Homepage:** `templates/index.html` and `static/css/home.css` provide the scenic hero, live server card, island/magic/community links, first steps, published news and Arcade feature. The page opens directly without an intro animation.

**Forum appearance:** forum pages share `templates/community/forum_base.html`, `static/css/forum.css` and `static/js/forum.js`. The transparent category illustrations are in `static/img/forum/`, mapped to existing category slugs in `community_data.FORUM_ICONS`. The original ten icons have 128 and 256 px WebP versions; the Events icon uses the supplied original PNG. Custom categories fall back to their admin-selected icon. The directory finder filters category names and descriptions locally; it does not search private threads. No example discussions are added to the release.

**Tebex:** create a webstore and packages in Tebex, configure delivery with its Minecraft plugin, then paste each package URL into the matching website product. Add its exact price, set availability to Released and publish. The website links to hosted checkout; no card handling or Tebex secret belongs in browser code. Website rank assignments are manual until a separate verified purchase sync is implemented. See [Tebex integration methods](https://docs.tebex.io/developers/integration-methods).

## Arcade reward setup

Sky Hop, Gem Rush, Cloud Catch, Codex Memory and Forge Strike support visitor practice and saved member scores. Minecraft linking is required to earn Marks. The default daily limit is 200 per member, with per-run caps and 15 rewarded runs per game per UTC day. Change the cap or close the Arcade in Admin → Settings. Closing also removes its navigation and home-page feature.

The existing game plugin includes a queue collector in `WebsiteRewards`. To enable delivery:

1. Deploy a plugin build containing `WebsiteRewards` and start the server to create `plugins/NerdSMP/security/website-rewards.yml`.
2. Set its `url` to `https://YOUR-WEBSITE/admin/api/arcade-payouts`, set `enabled: true`, and restart.
3. Copy the generated `security/website-rewards-token.txt` value into `AERBORNE_REWARDS_TOKEN` on the website. Restart the website.
4. Link a test Minecraft account, finish a round and verify its Marks arrive in game. Keep the queue database when updating the site.

Scores originate in the browser. Run tokens, rate checks, serialised payout creation, caps and game-server idempotency reduce abuse; they are not a full authoritative simulation. Test delivery before announcing rewards as live. The site shows a holding notice while its token is absent.

## Not built yet

A richer player dashboard (equipment, achievements, island details), the interactive Sky Atlas and
Tebex webhooks. The site doesn't advertise any of them. Store purchases keep being fulfilled in game by the Tebex
plugin, so the website never handles payments.

## Live status

The site connects to your Minecraft server (or Velocity proxy) the way the multiplayer screen
does: players online, version, ping bars and server icon. Each answer carries the time it was
checked, and offline is reported as offline, never as an old player count. Some shared hosts
block outgoing connections to port 25565; if status always reads offline while the server is up,
ask your host to allow it or point `AERBORNE_STATUS_HOST` at an address they allow.

## Run it locally

On this Windows workspace, run `Website/run-local.ps1` (or `./run-local.ps1` from this folder).
Open `http://127.0.0.1:5050/forum` for the forum or `http://127.0.0.1:5050/` for the home page.
The development server refreshes open pages automatically when source, templates or assets change.
Python changes restart the server automatically. Stop it with Ctrl+C. Use `-Port 5051` to choose another port.
This launcher uses the local `instance/` database; the sample discussions from design previews are separate.
The development helper is excluded from the cPanel release bundle.

```bash
python -m venv .venv
.venv/Scripts/pip install -r requirements.txt
.venv/Scripts/python manage.py run
```

Then open http://127.0.0.1:5000. It reloads itself when files change. Run the tests with
`python -m unittest discover tests`.

On a newer Python than 3.6, install a current Pillow (`pip install Pillow`) instead of the pinned
8.4.0, which only has wheels for older Pythons.

## Notes

- Fonts (Alegreya and Nunito) load from Google Fonts, which receives visitors' IP addresses.
  To avoid that, put the font files in `aerborne/static/fonts/` and replace the `<link>` in
  `templates/base.html` with `@font-face` rules.
- **Brand graphics** (logo, Skycrest, icons, hero painting, social image) are in
  `aerborne/static/img/brand/`, from the "Website Graphics Ready To Publish" pack. The pack's
  1920px backgrounds are the 1672px paintings padded onto a cream canvas, so the site uses the
  native 1672px render as its largest hero instead. The Arcane City painting is labelled
  "Artist's impression" because the city is still being built.
- **Store, companion and trail art** in `static/img/kit/` is concept art upscaled from small tiles.
  It's shown small and labelled "Concept art". Replace it in the admin when finished art arrives.
  `static/img/island.svg` is original, generated block by block for this site.
- The footer carries the "Not an official Minecraft product" notice. Keep it.

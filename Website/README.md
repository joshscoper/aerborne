# Aerborne website

The official site for Aerborne: home page with live server status, Play, Explore, the
Adventurer's Handbook, the Arcane Codex, the Companion encyclopedia, News with RSS, a cosmetic
Store catalog with Tebex Headless checkout, community forums, member profiles, ranks, six Arcade games and Support. Staff edit everything from a built-in
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

**If the site shows a 500 error right after creating the app:** cPanel replaces `passenger_wsgi.py` with its own starter file
when you click Create, and that file loads itself in a loop. Upload or extract the project's `passenger_wsgi.py` again
(or extract `Website.zip` again), then restart. Don't recreate the app for updates; extract the new zip over the old
files and restart instead.

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
| `AERBORNE_DISCORD_URL` | `https://discord.gg/AUvpKyemCj` | Discord invite destination. Admin Settings overrides it. |
| `AERBORNE_DISCORD_SERVER_ID` | `1562150950045491370` | Numeric Discord server ID for the official server widget. Enable Server Widget in Discord first. Admin Settings overrides it. |
| `AERBORNE_CONTACT_EMAIL` | `support@aerborne.net` | Support contact shown in the footer, Support and Join Us. |
| `AERBORNE_REWARDS_TOKEN` | *(blank)* | Private token for the game server to collect Arcade payouts. Blank leaves rewards queued and shows a delivery notice. |
| `AERBORNE_SOCIAL_TOKEN` | *(blank)* | Private token for the game server to sync friends, blocks and messages. Blank keeps them website-only. |
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

- **Accounts.** `/account/register` first checks a freely entered birth date, before displaying username/password fields. The date is processed and discarded; the session holds only an eligibility result and timestamp for up to 24 hours. An under-13 result blocks registration in that session, including direct POST and back-button attempts. Eligible players then create a username/password account with two required, initially unchecked confirmations: age 13+ and agreement to the Terms and rules. No email is required. Sign-ups are rate limited per connection and have a hidden field that traps bots.
- **Agreement records.** Registration validates both confirmations on the server, requires the current published Terms, and rejects a form if that Terms text changed. It records `terms_accepted_at`, `terms_version` and `age_confirmed_at` on the member. `registration_terms_versions` preserves the exact agreed title/text by SHA-256 version. Existing members are not backfilled with invented agreement dates. The record appears privately in Admin → Forum → Members and the member’s own data export; account erasure clears it.
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
| `GET /api/v1/expeditions` | public | Published expeditions with tiers, campaign seals, clear counts and best parties (`/expeditions`) |
| `GET /api/v1/campaign` | public | The running season's goals, milestones, rewards, buffs and top contributors (`/campaign`) |
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
  enchantments and 3 Signatures, the nine Aspects, three regions, seven companions and eight trails
  (all labelled as concept art / upcoming), FAQs, rules and support pages, one welcome post.
- **Marked Preview:** Arcane Signatures, Bloodletter, Veinseeker and Chain Harvest. They're off by
  default in production (`arcane-forge` config). Switch them to Released in the admin once enabled.
- **Hidden recipes:** only the one-Aspect Fundamentals show their Aspects. Tick *Show its Aspects
  publicly* on any entry you're happy to reveal.
- **Public catalog:** supporter ranks, bundles, companions and trails. Checkout stays unavailable until a price and Tebex link are added and availability is Released.
- **Published policies:** terms, privacy and store terms. Staff can edit them under Pages. Updates preserve staff edits. Have them checked by someone qualified before you rely on them, especially once Tebex sales start.
- **Policy reader:** rules, Terms, privacy, store terms, appeals, account help, bug reports and purchase help have plain-language summaries, section links, reading position and print/save controls. Content revision 18 refreshes untouched starter pages, preserves staff-authored copy and keeps the previous Terms text as a historical snapshot. The privacy disclosure covers Discord embeds, external fonts, applications, social features, account export/deletion and registration records.
- **Children’s privacy:** the applicable US law is COPPA. FTC guidance explicitly says a bare “13 or older” checkbox is not a neutral age screen. The date check and declarations implement Aerborne’s requested account policy; they are not identity verification, verifiable parental consent, or a certification of COPPA compliance. A qualified reviewer should assess whether the game, mascots, incentives and intended/actual audience make the service child-directed or mixed-audience, and review collection before screening (including public embeds/fonts), operator contact information, hosting logs/backups and procedures for known under-13 data. No under-13 account or parental-consent flow is implemented. References: [FTC age-screening FAQs](https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions), [current COPPA Rule resources](https://www.ftc.gov/business-guidance/privacy-security/childrens-privacy). Do not advertise this checkbox or age gate as “COPPA compliant.”
- **Expanded handbook:** island milestones, machines, spawners, co-ops, fishing, farming, excavation, cooking, daily goals, trading, banking, gems, graves and parties, plus session planning and website help. Search `/guide` by topic or command.

## Community, Discord and Tebex setup

**Discord:** Every Discord button opens `/discord`, the community landing page. Set **Admin → Settings → Discord invite link** for its Join button; `AERBORNE_DISCORD_URL` is a fallback. To show Discord’s official live server widget, enable **Server Widget** in the Discord server settings, then save the numeric **Discord server ID (widget)** in Admin → Settings (or set `AERBORNE_DISCORD_SERVER_ID`). No bot token is required. The official widget shows online members and public voice channels; visitors open Discord to read/send messages. The server checks widget availability with a 3-second timeout and a 60-second cache, so enabling it takes effect on a subsequent page load within about a minute. If the widget is blocked or disabled, the invite remains available. If neither a valid invite nor a widget ID is configured, the page keeps its forum, guide, news and rules links without fake activity or a broken embed. Only `/discord` allows the Discord frame origin in its content security policy.

**Profiles:** members edit avatars, banners, headlines, bios, island names, favourite skills, social links, signatures and game-stat visibility at `/account`. Social details are public. Assign ranks and custom titles in Admin → Forum → Members; manage rank colours, frames and permissions under Ranks. The seven supplied avatar frames are included in `static/img/frames/` (256 and 512 px WebP, plus a mask of each frame's opening). Each rank picks its frame in the rank editor: Owner and Admin use Admin, the other staff ranks use Staff, supporter ranks use their own frame, and the Media rank (for content creators) uses Media. Members without a rank get the Member frame. Character avatars are cropped to their artwork so they fill the opening; other avatars fill it as they are.

The profile editor groups appearance, personal details and links into keyboard-accessible tabs with a live member-card preview. Sixteen supplied character avatars live in `static/img/avatars/` (128 and 256 px WebP); Sky Sentinel is the default. Existing classic avatar choices remain available. The Skybound city banner uses the supplied forum painting. Public profiles separate the member's introduction, forum activity and game details.

**Homepage:** `templates/index.html` and `static/css/home.css` provide the scenic hero, live server card, island/magic/community links, first steps, published news and Arcade feature. The page opens directly without an intro animation.

**Joining and navigation:** `/play` presents the public server address and live status first, then three join steps, the first-island Handbook and expandable connection help. Its layout is in `static/css/play.css`. The former whitelist FAQ is retained as an unpublished draft on upgrades. Discord sits in the Community menu, with contextual links on Play and in the footer; one Play now button serves desktop and mobile navigation.

**Explore and Game Guide:** the reference pages share `static/css/reference.css`; article contents links and the mobile chapter drawer use `static/js/reference.js`. Revision 6 reviews all 33 Handbook guides, three Explore entries and the Codex/design catalogs. Copy is in `reference_content.py`. Matching original starter copy is refreshed while staff edits are retained. See `CONTENT_REVIEW.md` for sources, material corrections and the limits of checking local code against a remote server. Preview and planned labels do not assert that a feature is active on the live server.

**Forum appearance:** forum pages share `templates/community/forum_base.html`, `static/css/forum.css` and `static/js/forum.js`. The transparent category illustrations are in `static/img/forum/`, mapped to existing category slugs in `community_data.FORUM_ICONS`. The original ten icons have 128 and 256 px WebP versions; the Events icon uses the supplied original PNG. Custom categories fall back to their admin-selected icon. The directory finder filters category names and descriptions locally; it does not search private threads. No example discussions are added to the release.

**Tebex:** Aerborne remains the storefront; the Headless API supplies current package prices and builds the payment basket. Set `AERBORNE_TEBEX_PUBLIC_TOKEN` and `AERBORNE_TEBEX_PRIVATE_KEY` on the server, then run `python manage.py tebex-sync` to link existing products by unique category/name. The command preserves artwork, descriptions and publication status, links package IDs, saves a reference price and sets matched products to Released. New products can be linked by their Tebex package ID in Admin → Store items. A legacy price/package URL still works when the API is not configured. Website rank assignments remain manual; game delivery uses Tebex package commands.

Local development may instead read `public_token` and `private_key` from `instance/tebex.json`. That file is ignored by Git and excluded from `Website.zip`; provide fresh environment values on the production host. Rotate the supplied key after setup and replace the server value. Set `AERBORNE_SITE_URL` to the canonical HTTPS website URL before production checkout.

The account's checkout currently displays **Test Payments Active**. Enable live payments in Tebex after completing its account setup; a working API connection does not itself switch payment mode. A browser smoke check reached the actual Tebex payment form with Skyfarer and the correct 4.99 USD price; no payment was submitted.

The guest cart stores product slugs in the signed session, accepts a confirmed Java Edition username, rechecks products and live prices on checkout, and creates a basket with the customer's IP. Payment details are entered on Tebex. Cart changes and checkout require CSRF tokens; checkout attempts are limited. API failures preserve the cart and disable checkout. Redirects are restricted to Tebex payment hosts. The return page checks the session's basket with Tebex; it never grants ranks, Marks or entitlements. No payment is inferred from query parameters or a redirect. Discounts, taxes, package limits and ownership eligibility are calculated by Tebex. The catalog caches for 60 seconds and refreshes before checkout. See the official [Headless API checkout guide](https://docs.tebex.io/developers/headless-api/guides/checkout).

Before the first real sale, verify the delivery commands in Tebex for every package. The public Headless catalog does not expose those commands. All three bundles include **Skyfarer Rank** in addition to a companion and trail. The updated game plugin expands each bundle entitlement to all three cosmetics and upgrades the former default bundle lists on load, preserving custom configurations. Deploy the updated plugin before selling these bundles. Use the single bundle entitlement grant/revoke command so refunds remove the bundle's rank source without removing separately owned ranks. Do not rely on a website checkout return to grant purchases.

**Delivering purchases in game:** every store item has a *Game product id* (Admin → Store items), for example `cosmetic.companion.pufflet`, `cosmetic.trail.comet-tail`, `cosmetic.rank.starweaver` or `cosmetic.bundle.cloudberry`. In each Tebex package, add the command `nerdadmin entitlement grant {username} <game product id> {transaction}`, and for refunds and chargebacks `nerdadmin entitlement revoke {transaction} Refunded`. The server refuses unknown ids, never grants the same transaction twice, and lists every id with `/nerdadmin entitlement products`. Players use what they bought with `/cosmetics`, `/companion`, `/trail` and `/emote`. What each item does in game is described in `docs/COSMETICS.md` in the plugin repository.

## Arcade reward setup

Sky Hop, Gem Rush, Cloud Catch, Codex Memory, Forge Strike and Rune Breaker support visitor practice and saved member scores. Minecraft linking is required to earn Marks. The default daily limit is 1,000 per member, with 75–100 Marks per-run caps and 15 rewarded runs per game per UTC day. Revision 7 upgrades the original 200-Mark allowance; custom limits, including zero, are preserved. Change the cap or close the Arcade in Admin → Settings. Closing also removes its navigation and home-page feature.

Larger run rewards are split into payouts of at most 50 Marks, compatible with the existing collector ceiling. Finish requests return the saved receipt when retried, without issuing duplicate payouts. A failed save can be retried from the game-over screen.

The games use the supplied 115-sprite collection in `aerborne/static/img/arcade/sprites/`. Only the selected game's sprites load when Play is pressed; visible alpha bounds in `static/js/arcade/assets.js` preserve proportions and keep collision geometry separate from decorative glows. The source WebP files are copied unchanged. From the source checkout, re-import the complete archive with `python tools/import_arcade_sprites.py PATH_TO_ZIP` in an environment with Pillow; the import tool is not included in the upload bundle, and Pillow is not needed by the game runtime.

Sky Hop scores island gaps and collectibles. Gem Rush charges a burst after eight match groups and awards one five-second bonus for a third-step cascade. Cloud Catch has timed shields and magnets. Codex Memory has eight pairs with picture-and-name matching. Forge Strike changes hammer, anvil and ingot states as you play. Rune Breaker includes armour, explosive bricks, temporary wider paddles and a one-use floor shield.

The existing game plugin includes a queue collector in `WebsiteRewards`. To enable delivery:

1. Deploy a plugin build containing `WebsiteRewards` and start the server to create `plugins/NerdSMP/security/website-rewards.yml`.
2. Set its `url` to `https://YOUR-WEBSITE/admin/api/arcade-payouts`, set `enabled: true`, and restart.
3. Copy the generated `security/website-rewards-token.txt` value into `AERBORNE_REWARDS_TOKEN` on the website. Restart the website.
4. Link a test Minecraft account, finish a round and verify its Marks arrive in game. Keep the queue database when updating the site.

Scores originate in the browser. Run tokens, rate checks, serialised payout creation, caps and game-server idempotency reduce abuse; they are not a full authoritative simulation. Test delivery before announcing rewards as live. The site shows a holding notice while its token is absent.

## Friends, messages and notifications

Signed-in members get a mail and a bell icon in the nav, both with unread counts. They open three tabs:

- **Notifications:** friend requests, accepted requests, new messages, replies to your threads and likes on
  your posts. Notifications are kept for the newest 200 per member.
- **Messages:** one-to-one conversations in plain text, up to 1,000 characters each. Members choose who
  can message them (anyone, friends only, no one) under Account → Links & privacy. Sending needs a linked
  Minecraft account while *Posting and messaging need a linked Minecraft account* is on. Messages are rate
  limited. Recipients can report a message; staff see only reported messages, under Forum → Reports.
  Admin → Settings can pause new messages.
- **Friends:** send requests by name or from a profile, accept or decline, remove, and block. A block
  removes any friendship, stops messages and requests both ways, and folds the blocked member's forum
  posts away. Friends lists hold 200, the same as in game.

Profiles show *Add friend*, *Message* and *Block* buttons to other signed-in members.

## Syncing friends and messages with the game server

For players who linked their account with `/weblink`, the website and the server keep each other up to date:

- Friendships and blocks made on either side appear on the other: `/friends` and `/ignore` in game, the
  Friends page on the website. When a player links, their existing game friends and blocks are sent to
  the website, and their website friends and blocks to the game.
- A website message to a linked player who is online also appears in their game chat as `[Web] From …`.
  Clicking it starts a reply with `/msg`.
- `/msg` or `/reply` to a linked player who is offline goes to their website inbox instead of failing.
- On joining, players see how many website messages and notifications are waiting.

To switch it on:

1. Deploy the new plugin jar and start the server once. This creates `plugins/NerdSMP/security/website-social.yml`.
2. Set its `url` to `https://YOUR-WEBSITE/admin/api/social-sync`, set `enabled: true`, and restart.
3. Copy the generated `security/website-social-token.txt` value into `AERBORNE_SOCIAL_TOKEN` on the
   website, then restart the website.

The server sends its changes every 15 seconds (`interval-seconds`) and keeps unsent ones in
`plugins/NerdSMP/website-social-state.yml`, so neither side loses changes if the other is down. Without
the token, everything still works on the website alone and nothing is queued for the server.

## Profiles and badges

The member's name in the nav opens their public profile; *Edit your profile* on it leads to the editor.
Profiles show a stats strip (posts, likes, friends, badges, join date), the member's about text, badges,
game stats (Adventurer rank and level, all eleven skills, and their standing on every leaderboard, read
live from the game API; members can hide them, and the owner still sees them with a note), recent forum
activity, up to 12 friends with a *See all* link and mutual friends, Arcade best scores,
island and favourite skill, social links and in-game stats. Members can hide their friends list under
Account → Links & privacy.

**Badges** come in two kinds. Hover or tap one to see what it's for.

- **Automatic**, earned through activity and worked out each time the profile is shown: Aerborne team,
  Supporter, Content creator, Founding member (the first 100 accounts), Verified adventurer, A year in
  the skies, First words, Forum regular (50 posts), Forum veteran (250), Well liked (25 likes), Community
  favourite (250), Social butterfly (10 friends), Arcade player and High scorer (best score in any game).
  Change them in `aerborne/badges.py`.
- **Staff-awarded**, managed by admins under Admin → Forum → Badges. Six starter badges are included:
  Bug hunter, Event winner, Master builder, Helping hand, Idea maker and Beta tester. Each badge uses a
  picture from the site's gem, forum, companion or Arcade art and one of six colours. Admins and
  moderators award or take back badges from a member's page under Forum → Members, with an optional
  note. The member gets a notification.

## Members directory and member tools

**Members** (Community → Members) lists every active member with their rank frame. Visitors can search
by website or Minecraft name, filter by rank and sort by activity, join date, posts or name. Banned and
deleted members aren't listed.

Members can download everything the website holds about them from `/account/data`. The download is a
zip of JSON files covering profile, posts, messages, friends, blocks, notifications and Arcade history,
and it never includes the password.

Staff open a member from Forum → Members to see their activity, past staff actions and these tools:

| Tool | Who | What it does |
|---|---|---|
| Sign out everywhere | admins, moderators | Ends all their website sessions |
| Clear profile | admins, moderators | Removes headline, bio, signature, title, links, avatar and banner |
| Reset password | admins | Sets a temporary password shown once to the admin, and signs them out |
| Download their data | admins | The same zip members can download themselves |
| Delete account | admins | Type the username to confirm. Removes the profile, Minecraft link, messages, friends, blocks, notifications and likes, cancels pending Arcade payouts, and frees the username |

When an account is deleted, its posts stay and show as "Deleted member N", unless the admin also ticks
*remove their posts and threads*. Every tool is recorded in the audit log. Deleting a website account
doesn't touch the player's game data or in-game friends.

## Not built yet

A richer player dashboard (equipment, achievements, island details) and the interactive Sky Atlas.
Store purchases keep being fulfilled in game by the Tebex plugin. The website receives signed
payment notifications for creator metrics; it never accepts payment details or grants purchases.

## Creator program and payment metrics

The application at `/creator-program` is linked only from the footer. Sign in with an active
website account to apply. Administrators review applications under Admin → Creator program.
Accepting grants the website Media display rank (existing staff keep their staff rank) and
the Community → Creator dashboard entry. This does not grant staff access or an in-game rank;
assign the game Media rank through the server's existing administration tools.

Accepted creators can request a code and supply their **Tebex Wallet reference**. Wallet
references, contact details and priority tickets stay private to the creator and administrators.
Review the request, then provision the exact code in Tebex Control Panel with **25% revenue
share**, **10% customer discount**, and that wallet reference. Verify the settings and tick
the confirmation on the local code review form before marking it Active. Local approval does
not create or edit the code in Tebex. Only active, accepted members' codes can enter checkout.
The cart sends the code to the Headless API and reads the basket back to confirm attribution.
Tebex remains authoritative for discount eligibility, fees and actual wallet allocations.

Set **`AERBORNE_TEBEX_WEBHOOK_SECRET`** to the secret shown in Tebex → Developers → Webhooks
(separate from the Headless private key). Add the HTTPS production endpoint
`https://YOUR-SITE/integrations/tebex/webhook`, validate it in Tebex, and subscribe to
`payment.completed`, `payment.refunded`, `payment.dispute.opened`, `payment.dispute.won`,
`payment.dispute.lost`, and `payment.dispute.closed`. Preserve the raw request body and
`X-Signature` through the reverse proxy. The endpoint checks Tebex's SHA256/HMAC signature,
deduplicates event IDs and ignores older payment updates. Unknown codes are not attributed.
It stores no customer names, addresses or emails from notifications.

Metrics begin when notifications are received; there is no historical sales import. Refunds
and disputes remove the entire affected transaction from totals, including partial-refund
notifications. Each currency is displayed separately. The dashboard's 25% estimate uses reported
sales before fee and tax adjustments; it is not an allocation ledger, settlement balance or
payout tool. Actual earnings and withdrawals are in Tebex Wallet. Code share/discount on
subscriptions applies only to the initial payment under Tebex's creator-code rules.

When suspending a creator, deleting their website account, or disabling/replacing a code,
**disable the code in Tebex too**. The site blocks new checkouts, but cannot revoke checkout
links already issued or codes entered directly on Tebex. Account erasure clears the application,
wallet reference and tickets; minimal code and transaction records remain for reconciliation.
Keep the production database and backups private. No real payouts or purchases are made by
the local tests.

Reference: [Creator codes](https://docs.tebex.io/creators/tebex-control-panel/engagement/creator-codes),
[Headless endpoints](https://docs.tebex.io/developers/headless-api/endpoints),
[signed webhooks](https://docs.tebex.io/developers/webhooks/overview).

## Live status

The site connects to your Minecraft server (or Velocity proxy) the way the multiplayer screen
does: players online, version, ping bars and server icon. Each answer carries the time it was
checked, and offline is reported as offline, never as an old player count. Some shared hosts
block outgoing connections to port 25565; if status always reads offline while the server is up,
ask your host to allow it or point `AERBORNE_STATUS_HOST` at an address they allow.

Status automatically follows the public address's `_minecraft._tcp` SRV record when using the default Java port. Aerborne currently advertises port 9025, so players can keep copying `play.aerborne.net` without a port suffix. An explicit `AERBORNE_STATUS_HOST`, `AERBORNE_STATUS_PORT`, non-default `AERBORNE_SERVER_PORT`, or port in the public address bypasses SRV discovery. Install the updated requirements (including `dnspython==2.2.1`) when deploying this update.

Cards refresh every minute and when the tab becomes visible, with a manual Refresh status button. Counts, version, response time and server icon come from Minecraft's public status response; response time measures the website-to-server connection. Checks are reused for 30 seconds (15 seconds for failures), including manual refreshes. Failed website requests clear old counts and signal bars rather than showing stale online data.

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

## RSS subscriptions

The news RSS feed is `/news/feed.xml`. The News page's **Follow with RSS** link opens
`/news/subscribe`, where visitors can copy the address into any feed reader. Each category also
has its own feed, for example `/news/feed.xml?category=patch`. Feeds include the latest 20 public
posts, publication dates, stable permalink GUIDs and absolute links. Drafts, archived posts and
scheduled posts are excluded. ETag and Last-Modified headers support conditional reader requests.
Set `AERBORNE_SITE_URL=https://your-domain.example` on hosting so readers receive canonical links;
local development uses the local address. RSS is a pull subscription; it does not send email.

## Featured streamers

Open **Community → Featured streamers** (`/community/streamers`). To add someone, open
**Staff dashboard → Content → Featured streamers → New streamer**, choose Twitch, YouTube or
Kick, enter their channel, add optional creator art and a short introduction, and set **Published**.
Use Sort order to arrange channels; confirmed live channels appear first. Draft, scheduled and
archived entries stay hidden. Admins, editors and moderators can curate this list. Writers cannot.

Twitch and Kick accept a channel name or an HTTPS channel URL. YouTube accepts a `UC…` channel
ID, an `@handle`, or the corresponding channel URL. The site validates these and builds platform
links itself; arbitrary URLs are not fetched. Visitors watch on the creator's platform. No player,
chat or third-party tracking scripts are embedded.

Configure these environment variables on the web host, then restart the website:

| Platform | Variables | Official setup |
| --- | --- | --- |
| Twitch | `AERBORNE_TWITCH_CLIENT_ID`, `AERBORNE_TWITCH_CLIENT_SECRET` | [Register an application](https://dev.twitch.tv/docs/authentication/register-app/) |
| YouTube | `AERBORNE_YOUTUBE_API_KEY` | [Enable YouTube Data API v3 and create a server API key](https://developers.google.com/youtube/v3/getting-started) |
| Kick | `AERBORNE_KICK_CLIENT_ID`, `AERBORNE_KICK_CLIENT_SECRET` | [Create a Kick application](https://docs.kick.com/getting-started/kick-apps-setup) |

These belong only in the server environment. They are never included in browser responses, forms,
public status JSON or the release ZIP. Twitch and Kick app tokens are obtained and refreshed
server-side; creators do not need to sign in to Aerborne to authorize public live checks.

Status checks use the official [Twitch Get Streams](https://dev.twitch.tv/docs/api/reference/#get-streams),
[YouTube search](https://developers.google.com/youtube/data/reference/rest/v3/search/list) and
[Kick Channels](https://docs.kick.com/apis/channels) APIs. Results are cached in SQLite across web
workers. Twitch, Kick and already-discovered YouTube broadcasts refresh at most once a minute
while the page is in use. YouTube discovery searches run at least 30 minutes apart, stretched
according to the number of featured YouTube channels to target at most 80 searches/day for this
site. Quotas vary by project; check the Google Cloud console if the same project serves other apps.
There is no polling when nobody uses the page, and no external checks for unconfigured providers.
API errors and missing credentials show **status unavailable**, never a stale live badge or a false
offline claim. Staff see which platform connections still need setup on the streamer page.

## Profile badge artwork

Profiles and **Admin → Forum → Badges** use the 12 medallions from the supplied
`Aerborne_Badge_Pack_v1.zip`. Transparent 96px and 192px WebP versions are in
`aerborne/static/img/badges/`; the original artwork is fitted to a square without a circular crop
or an extra CSS border. Re-import with `python tools/import_badges.py path/to/Aerborne_Badge_Pack_v1.zip`
using a current Pillow installation.

Aerborne Team, Founding Member and High Scorer use the existing automatic rules. The remaining
pack badges are available for staff to award. Existing automatic achievements keep their names
and eligibility, with matching artwork from the new pack. Verified Adventurer still means a linked
Minecraft account; the separate Verified Helper badge is awarded by staff.

The content update replaces artwork in place, adds the missing badge types, and brings the original
Helping Hand and Master Builder entries under Community Helper and Island Builder. Existing badge
IDs, recipients, dates, award notes and custom descriptions stay intact. Old custom artwork keys
continue to resolve to the new medallions.

## Firstlight Sigil

Firstlight Sigil is a manually awarded profile badge using the separately supplied transparent
artwork (`static/img/badges/firstlight-sigil-{96,192}.webp`). Its rarity is **Mythic · Invitation Only**.
It is never sold or granted through activity. Creators award it through an administrator account:
**Admin → Forum → Members → member details → Badges**. Moderators cannot award or revoke it.
Badge descriptions, rarity and acquisition details appear when a player selects the medal on a
profile. Content revision 16 registers the badge without assigning recipients or changing existing awards.

Each administrator has **five lifetime Firstlight invitations**. The allowance is tracked by staff account
ID and survives revoking awards or deleting/recreating the badge. Duplicate awards use no extra slot;
historical grants are counted during revision 17. Member management and Badges show the administrator’s
remaining allowance. Ordinary badges are unaffected.

## Team applications

`/join-us` is linked only in the public footer. Visitors can apply without a website account for Helper,
Moderator, Forum Moderator, Builder, Developer, Artist, Writer, Events Organiser or another contribution.
Role selection supplies a relevant prompt. Answers and contact details are saved privately in SQLite;
submissions require CSRF protection, a form token and privacy acknowledgement, with spam throttling and
duplicate protection. No application email is sent automatically and no SMTP variables are needed.

Administrators review submissions at **Admin → Site → Team applications**, filter their status, keep
private notes and remove applications when requested or no longer needed. Other staff roles cannot
access the applications. Status labels never create staff accounts or grant permissions. Use
`support@aerborne.net` for applicant questions and removal requests; the privacy page documents collection.

## Illustrated store artwork

The store uses the six transparent illustrations from `Aerborne_Store_Illustrated_Assets.zip`.
The separately supplied Island Decor illustration completes the five collection cards and also
appears in the Island Decor page header, exported as `category-island-decor-{256,512}.webp`.
Category artwork appears on collection cards and headers, the shopkeeper links to purchase help,
and the empty basket is used for collections without listed products. Individual product artwork,
prices, descriptions and availability remain editable in the existing product editor.

Assets are exported as transparent 256px and 512px WebP images under `aerborne/static/img/store/`.
Re-import with `python tools/import_store_art.py path/to/Aerborne_Store_Illustrated_Assets.zip`
using a current Pillow installation. The shared layout is in `static/css/store.css` and
`templates/partials/store_macros.html`. Only released products with both a price and a checkout
link count as on sale; these illustrated UI changes do not configure payment credentials.

Supporter rank emblems come from `Aerborne_Supporter_Rank_Icons.zip` and appear on the store,
rank collection, comparison table and product pages. The original 128px, 256px and 512px WebP
exports are preserved under `static/img/store/ranks/`. Re-import with
`python tools/import_store_ranks.py path/to/Aerborne_Supporter_Rank_Icons.zip` using Pillow.
Content revision 13 replaces the original rank product tiles and clears their concept-art label,
preserving custom product artwork and other product settings. Profile frames and badges are separate.

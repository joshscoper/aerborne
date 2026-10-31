# Aerborne local test environment

A private copy of the whole setup (Paper server, NerdSMP plugin and website) wired together exactly as
the live site will be, so everything can be tested before it goes live. It uses its own ports and data and
never touches the live site, your website preview on port 5050, or the other test servers under `runtime/`.
The Minecraft port is 25565, the same as the older `run-test-server.cmd` SMP setup, so don't run both at once.

| Piece | Address |
|---|---|
| Minecraft server (Java 26.2) | `localhost:25565` |
| Website | http://127.0.0.1:5070 (staff admin at `/admin`) |
| Game API (used by the website) | http://127.0.0.1:9030 |
| Watchdog staff panel | http://127.0.0.1:9031 (sign in with a website admin account) |

## Start and stop

Double-click `start.cmd`, or run it from a terminal. It opens two windows:

- **Aerborne test website**, which reloads itself when you change the website's code, templates or styles.
- **Aerborne test server**, which builds the plugin, then starts Paper. The first time, it asks you to accept
  the Minecraft EULA by typing `I AGREE`.

Stop the server by typing `stop` in its window. Never `/reload` NerdSMP. Close the website window to stop it.

Run the pieces on their own with `start-server.ps1` (`-SkipBuild` reuses the last plugin build,
`-PrepareOnly` sets up without starting) or `start-website.ps1`.

To change the plugin: stop the server, then start it again. It rebuilds the jar each time.

`reset.ps1` wipes the worlds, player data and test website for a fresh install. It keeps the Paper jar,
your EULA answer, `server.properties` and the connection settings. Stop both windows first.

## What's connected

The scripts create shared tokens in `server/plugins/NerdSMP/security/` and give the same tokens to the
website, so these work straight away:

- **Leaderboards and player profiles:** the website reads the game API.
- **Account linking:** `/weblink` in game, then enter the code at http://127.0.0.1:5070/account/link.
- **Arcade payouts:** Marks earned on the website arrive in game within 15 seconds.
- **Friends, blocks and messages:** these sync both ways every 5 seconds.
- **Server status:** the status page and home page ping the test server.
- **Staff panel:** website admins can sign in to the Watchdog panel.

## First steps

1. Join `localhost:25565` with your Minecraft account. In the server window, run `op <your name>`.
2. Open http://127.0.0.1:5070/admin and sign in as `j0sh` with the default password; the site makes you
   change it straight away. This only affects the test site.
3. Create a player account on the website, then link it with `/weblink`.

## Developer commands

Ops can use `/dev` (`nerdsmp.dev`) to test without playing up to something. Run it alone for the list.

| Command | What it does |
|---|---|
| `/dev god [player]` | Take no damage and never go hungry, until you leave |
| `/dev heal [player]` | Full health and food, no fire or effects |
| `/dev fly [player]` | Toggle flight |
| `/dev speed <1-10> [player]` | Walk and fly speed; 1 is normal |
| `/dev item <id> [amount] [player]` | Any catalog item or vanilla item; `lesson`, `lifetime` and `legend` are the bottles |
| `/dev gear <id> [item levels] [affixes] [player]` | A rolled piece of loot, above its catalog level if you like |
| `/dev sealed <id> [player]` | An unidentified piece of loot |
| `/dev charm <companion> [player]` | A companion charm |
| `/dev level <skill\|all> <level> [player]` | Set a skill's level, up or down |
| `/dev marks <amount> [player]` | Add Marks |
| `/dev mob <id> [count]` | Spawn a creature where you stand |
| `/dev kill [radius]` | Kill nearby enemies, with credit for quests and room progress |
| `/dev goto <room>` | Jump to a room of the expedition you're in |

Mobs ignore creative mode, so use `/dev god` in survival to test a fight. Every use is written to the
server log.

## Test checklist

**Website and linking**
- [ ] Register, sign in, link Minecraft with `/weblink`, recover a password with a new code.
- [ ] Leaderboards and your profile's Game stats fill in after you've played a little.
- [ ] Status page shows the server online with the right player count.

**Companions, trails and supporter perks** (grant them from the server console)
- [ ] `nerdadmin entitlement products` lists every product id.
- [ ] `nerdadmin entitlement grant <you> cosmetic.companion.pufflet test-1`, and you're told it's ready.
- [ ] `/companion pufflet`. It follows you, walks or flies, animates and survives teleports, death and world changes.
- [ ] Try each companion; reshape one in `server/plugins/NerdSMP/companion-models.yml` and restart.
- [ ] `nerdadmin entitlement grant <you> cosmetic.trail.comet-tail test-2`, then `/trail comet-tail` while walking.
- [ ] `nerdadmin entitlement grant <you> cosmetic.rank.starweaver test-3`. Check the badge in chat, the tab list and
      above your head, the name colours in `/cosmetics`, `/emote wave`, presets and the Starweaver Lantern.
- [ ] `nerdadmin entitlement grant <you> cosmetic.bundle.cloudberry test-4` grants Pufflet and Cloudstep.
- [ ] `nerdadmin entitlement revoke test-1 Refunded`. Pufflet leaves within a minute.
- [ ] `/settings` → *Other players' companions and trails* hides them (needs a second player).

**Social, synced with the game** (needs two linked players, or a friend joining)
- [ ] Add a friend on the website; `/friends` in game shows them, and the other way round.
- [ ] Block on one side and check `/ignore` on the other.
- [ ] Message a player who's online in game from the website; it appears in their chat as `[Web] From …`.
- [ ] `/msg` a linked player who's offline; it lands in their website inbox.

**Arcade**
- [ ] Play a game on the website while linked; the Marks arrive in game.

**Staff**
- [ ] Sign in to the Watchdog panel at http://127.0.0.1:9031 with a website admin account.
- [ ] Website admin tools: reports, member manage page, badges, ranks, data download, delete.

## Notes

- `online-mode` is on, so joining needs a real Minecraft account; this is a local server, not a public one.
- The server runs the Skyblock game mode with vanilla textures, like `run-skyblock-test-server.cmd`.
- Everything under `server/` and `website-data/` is ignored by git. The scripts and this file are tracked.

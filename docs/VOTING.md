# Voting

Server list sites tell the server when someone votes through Votifier. The plugin speaks it itself, both
versions NuVotifier does, so there's no separate Votifier plugin to install. Code: `nerdsmp-paper/.../vote/`.

## Setting up a site

On first start the plugin writes `plugins/NerdSMP/votes.yml` and opens the Votifier port, **9027** unless
`listener.port` says otherwise. It also makes the secrets the sites need, which are never printed:

| File | What it's for |
|---|---|
| `security/votifier-token.txt` | Votifier v2 token. Paste this into a site's "Votifier token" box. Use it wherever a site takes it. |
| `security/votifier/public.key` | Votifier v1 public key, for sites that only take a key. Its partner `private.key` stays on the server. |

On the site's page for the server:

- **Address:** the server's public IP or domain, without a port.
- **Port:** 9027, or whatever `listener.port` is. It has to be reachable from the internet. On BisectHosting the
  server only gets the ports allocated to it, so ask them for an extra port (or add one in the panel's network
  settings if your plan allows it) and set `listener.port` to that number.
- **Token / public key:** the contents of one of the files above.

Then press the site's test button and run `/nerdadmin vote`. It shows whether the port is open and the latest
votes, with the name each site sends.

## The /vote menu

`/vote` opens a menu with a tile for each site in `votes.yml` under `sites`. A site the player can vote on now
glows; one they've voted on says when it opens again. A menu can't open a web page, so clicking a tile closes
it and puts that site's vote link in chat, ready to click. The menu also shows what voting pays, the player's
own record, and the month's top voters (click for the top 10). `/vote list` gives the same in chat, which is
also what the console gets.

Each site takes:

| Key | |
|---|---|
| `name` | What the tile says, up to 40 letters. |
| `url` | The site's vote page for the server. Must be `https://`. |
| `service` | The name the site sends with each vote (from `/nerdadmin vote`). Without the right one, votes still pay, but the tile can't tell when the player can vote again. Defaults to `name`. |
| `icon` | Any item, like `grass_block` or `diamond`. Defaults to `paper`. |
| `slot` | Optional: 10-16, 19-25 or 28-34. Sites without one line up in the middle row. |
| `description` | Optional text under the name. |

`menu.title` sets the menu's title, with colour tags. After editing, `/nerdadmin vote reload` applies the sites,
rewards and title without a restart. If the file has a mistake, the reload says what and keeps the old
settings. Changes to `listener` still take a restart.

Moving from NuVotifier: copy its `rsa/public.key` and `rsa/private.key` into `security/votifier/` before first
start and the sites keep working with the key they already have. Remove NuVotifier, or give it a different port.

## Rewards

All in `votes.yml` under `rewards`:

- `marks-per-vote` (100), plus `streak-bonus-marks` (10) for each day in a row after the first, up to
  `streak-cap-days` (7). A week's streak pays 160 a vote.
- Every `booster-every` (20) votes earns an XP booster, a once-only grant like the raid's.
- `repeat-hours` (6): the same site sending the same player again inside this window pays nothing. Sites allow
  one vote a day, so this only catches retries and replays.
- `max-paid-per-day` (8): a ceiling per player, whatever the sites send.

A vote for a name that has never played on the server pays nothing and is logged. Offline players are paid
straight away and told on their next join. Votes are never sold: see [MONETIZATION_COMPLIANCE.md](MONETIZATION_COMPLIANCE.md).

## Security

A v2 vote must be signed with the token and echo a fresh challenge the server sent on that connection, so a
recorded vote can't be replayed. A v1 vote must decrypt with the private key. Anyone with the token or the
private key can forge votes, so keep both private, and if one leaks delete it and restart: a new one is made and
each site needs the new value. Each connection gets 5 seconds and at most 30 connections a minute come from one
address. Refusals are logged at most once every 10 minutes per address.

## Data

- Player data, under `votes.`: total, streak, each site's last vote, today's paid count, and Marks earned while
  offline that haven't been mentioned yet.
- `vote-tally.yml`: this month's votes by player name, for `/vote top`. It starts again each month.

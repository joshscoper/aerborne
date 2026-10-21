# Parties

Parties group up to eight players for expeditions, raids, shared rewards and party chat. Use them from
`/party`, which opens the party menu, or with the subcommands below.

## For players

| Command | What it does |
| --- | --- |
| `/party` | Opens the party menu: members with health and location, invite, chat, expeditions, settings. |
| `/party invite <player>` | Invites a player. With no name, opens a picker of online players. |
| `/party accept [leader]` | Joins a party. The invite card in chat has **[Accept]** and **[Decline]** buttons. |
| `/party deny [leader]` | Declines one invite, or all of them with no name. |
| `/party list` | The party finder: open parties you can join without an invite. |
| `/party join <player>` | Joins the open party that player is in. |
| `/party create` | Starts a party of one, for example to open it to the finder. |
| `/party leave` | Leaves. A leaving leader hands the party to the longest-serving member, preferring someone online. |
| `/party info` | The roster in chat. |
| `/pc <message>` | Party chat. `/party chat <message>` works too. |

Leaders also have:

| Command | What it does |
| --- | --- |
| `/party kick <player>` / `/party promote <player>` | In the menu: right-click a member to remove them, left-click to make them leader. |
| `/party open` / `/party close` | Lists the party in the finder, or makes it invite-only again. |
| `/party note <text>` | A short note shown in the finder and to members, up to 48 characters. `/party note clear` removes it. |
| `/party invites leader` / `members` | Whether only the leader or every member can invite. |
| `/party disband` | Ends the party. |

How invites work:

- A player can hold invites from up to three parties at once and pick one. Joining a party
  withdraws the others.
- Invites last a minute. A pending invite holds a place, so a party can't be over-invited.
- When leadership changes, unanswered invites are withdrawn. The new leader can invite again.
- The party is told about every change: joins, leaves, kicks, new leaders, notes and settings.

Going offline:

- A party survives logouts and server restarts.
- When the leader logs out, the next online member takes over after a minute.
- A member offline for 15 minutes leaves the party. An empty party disbands.
- Logging back in reminds you which party you're in and of any pending invites.

## For staff

Settings in `config.yml`:

```yaml
parties:
  maximum-size: 8                # raids take 8; regular expeditions cap their own groups at 5
  invite-lifetime-seconds: 60
  max-pending-invites: 3        # invites one player can hold, from different parties
  leader-handover-seconds: 60   # an offline leader hands over after this long (minimum 10)
  offline-removal-minutes: 15   # members offline this long leave the party
```

Parties are saved to `plugins/NerdSMP/parties.yml`, which is written off the main thread after
each change and again on shutdown. Members restored after a restart count as offline until they
log back in, so the handover and removal timers run as usual.

Party membership feeds [expeditions](EXPEDITIONS.md) (party tickets and the ready check), shared
kill rewards near party members, and the party chat channel.

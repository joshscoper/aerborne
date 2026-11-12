# Aerborne tutorial, tips, website and staff support

Players can use a standard Minecraft client. These features use ordinary inventory menus and clickable chat.

## Player guide

`/tutorial` (alias `/guide`) opens eight short lessons: navigation, home/co-op protection, skills, equipment, talent trees, Marks/trading, adventures, and community/help. Tabs allow review in any order. **I've read this · Next** saves a read checkpoint; it does not certify a gameplay achievement or award items, Marks, XP, or talents. Feature buttons open the actual existing systems. `/tutorial` returns to the first unread lesson.

`/tutorial 1` through `/tutorial 8` revisit a lesson. `/tutorial pause` preserves progress. `/tutorial resume` resumes it. `/tutorial restart` uses a confirmation screen and resets only read checkpoints. Closing or escaping a lesson does not mark it read. Progress follows the canonical account, including linked clients, in `player_data/<canonical-uuid>.yml` under `tutorial.v1`.

A first-login invitation appears once after the account and starter kit are ready; it does not force a menu over another screen. The main menu and Community menu also link to the tutorial. `/quickstart` remains a compact chat reference.

## Tips and website

`/website` (alias `/web`) prints a clickable link to **https://www.aerborne.net**. Community and the final tutorial lesson also offer the link. This command opens no browser automatically and issues no account-link code; `/weblink` retains that separate function.

The normal tip rotation now includes tutorial, equipment/E inventory, connected talent trees, private support, and the website. Saved custom tip lists are preserved and receive missing feature tips once. Known obsolete stock wording is corrected when displayed. `/tips off` disables the entire automatic rotation, including the website promotion; `/tips next` remains a manual preview.

Operator configuration:

```yaml
tutorial:
  invite-on-first-login: true
website:
  promotion-in-tips: true
tips:
  include-feature-updates: true
  initial-delay-seconds: 120
  interval-seconds: 600
```

These options have safe defaults on existing installations; manually copying new config keys is optional. Promotion is one entry in the regular rotation. Turning off feature additions or promotion does not remove an operator's own authored tip text.

## Private help conversations

| Player command | Behavior |
| --- | --- |
| `/helpme <description>` or `/support new <description>` | Saves a request and immediately selects private support chat, protecting follow-up messages. `/helpop` is an alias for `/helpme`. |
| `/support` | Reads the selected or current open request. |
| `/support chat [id]` or `/channel support` | Selects private support chat for normal typed messages. |
| `/support reply <message>` | Sends a one-off reply to the selected/current request. |
| `/support leave` | Ends support chat and restores the previous channel. Explicitly selecting another channel also ends support chat. |
| `/support close [id]` | Closes the player's own request. |
| `/support history` | Lists the player's ten most recent retained requests and unread counts. |
| `/support read <id> [page]` | Reads a transcript in ten-message pages, including closed requests. Defaults to unread context or the latest page. |
| `/support help` | Shows instructions without opening a request. |

Only the requester and assigned staff member receive conversation messages automatically. Authorized support staff can review transcripts. The queue notification contains a request number and player name, without the request text. Replies are saved before delivery; offline players receive an unread notification on login. Reading consecutive transcript pages advances their read marker.

Private support bypasses normal DM/invitation preferences and public-chat mute/slow-mode restrictions so a player can ask staff for help or appeal a mute. It has separate message limits. It does not enter global/local/trade chat or the website chat console. As with other commands, server administrators may see command text in server command logs.

Chat selection lasts for the current connection. On disconnect it is cleared; login explains how to resume the saved request. Closed, reassigned, or permission-revoked conversations reject typed messages instead of routing them to global. After closing, use `/support leave` to return to ordinary chat.

`/report <player> <reason>` remains the misconduct-report workflow. `/helpme` previously aliased `/quickstart`; it now opens staff support. The tutorial and `/quickstart` provide general instructions.

## Staff workflow

1. Open `/support queue`, `/staff support`, or **Player Support** in the Staff Desk.
2. Review `/support read <id>` and claim with `/support claim <id>`.
3. Use `/support chat <id>` for a private conversation, or `/support reply #<id> <message>` for a one-off response. The `#` disambiguates the ticket number from message text.
4. Close with `/support close <id>` or hand off with `/support release <id>`.

Only the assigned staff member can reply or close another player's request. Claims cannot be stolen. A helper cannot claim their own request. Assignment survives disconnect/restart; support managers can release another helper's request if they become unavailable. Staff must still have their permission when a message is sent or received.

| Permission | Purpose |
| --- | --- |
| `nerdsmp.support.staff` | Review the queue/transcripts, claim requests, and handle assigned conversations. |
| `nerdsmp.support.manage` | Release another staff member's request for reassignment. |

Existing `nerdsmp.staff` grants support staff access as a child permission. Existing `nerdsmp.staff.moderate` grants support management. A dedicated support helper can receive only `nerdsmp.support.staff`; no OP status is required. Both new permissions otherwise default to OP.

## Storage and limits

`plugins/NerdSMP/support.yml` stores account identifiers, display names, assignment, creation timestamps, messages and unread positions. Back it up with player data and restrict file access to staff. Writes are atomic and publish changes only after successful persistence. Malformed or unsupported support data fails startup rather than silently replacing the queue.

Limits: one open request per account, 60 seconds between newly created requests, 100 open requests server-wide, 100 messages per conversation, 3–500 characters for an opening request and 1–500 for replies, no control characters, and two seconds between successful replies per connected account. The 200 most recently created closed requests are retained; older closed transcripts are pruned. At the message limit, close and open a follow-up. No ticket IDs are reused. Shutdown adds no unsaved mutations.

The support probe is a disposable loopback test plugin, excluded from the shipped server JAR. Its four bot accounts and synthetic secret markers are verification fixtures, not production player data.

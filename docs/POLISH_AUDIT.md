# Polish and consistency audit

Audit date: 2026-10-17. Read-only: no code was changed. Paths are relative to
`nerdsmp-paper/src/main/java/net/nerdsmp/paper/` unless they start with `nerdsmp-paper/`,
`docs/` or `README.md`. Line numbers are from the uncommitted working tree on that date.

## Summary

The plugin doesn't feel clunky because of any one bug. It feels clunky because it speaks
in **two house styles**, and a player crosses between them every few clicks.

| | The "rich" style (the majority) | The "plain" style |
|---|---|---|
| Chat | MiniMessage with colours that carry meaning: `<red>` errors, `<green>` success, `<yellow>` notices, `<gray>` detail (355 `sendRichMessage` calls) | One flat colour for every outcome (`tell`/`say` helpers in aqua or yellow), or raw white `sendMessage("...")` (224 calls) |
| Menus | Bold coloured titles, framed glass, a red **Close** barrier, an aqua or yellow **Back to …** arrow, and a "Click to …" hint | Single-colour titles, gold or aqua item names (so **Close** is gold or aqua too), no click hints, no route back to `/menu` |
| Who uses it | `CommandRouter`, `MenuManager`, alchemy, NPCs, staff desk, travel, fishing, excavation, achievements, bank, chat, guide | Skyblock, market, auctions, stores, homesteads, social, trade, payments, NPC vendor trading, the studios, zone and dig-site tools |

The fix that pays off most: write one small shared chat and menu kit in the rich style,
which most of the code already uses, and move the plain-style systems onto it. Most
of the items below are symptoms of this split.

---

## Progress

- **WP0, done 2026-10-17.** `presentation/Chat.java`, `gui/MenuKit.java`, `docs/STYLE_GUIDE.md`,
  plus tests `ChatTest` and `MenuKitTest`.
- **WP1, done 2026-10-17.** Rewrote the quickstart and fixed the hub (locked station tiles
  through the new `gui/RemoteStation.java`; Back buttons lead to the parent hub page; hub labels
  renamed to the feature names). `CommandRouter` now has no `return false`, maps result enums
  to sentences, and uses the standard refusals. The plugin.yml usage lines and
  permission-messages are fixed. Commands from the inactive game mode are hidden from players'
  command lists (one `registerEvents` line in `NerdSmpPlugin.java`). New test: `HubNavigationTest`.
  - Left for other packages: island `decline` → `deny` and the island help line (WP3, which
    then updates the `/island` usage in plugin.yml); `skyblock.tips` and the local-radius tip in
    `config.yml` (WP7); Back buttons on screens that other packages own (WP2 to WP4). Those
    screens can now call `plugin.menus().open(player, MenuManager.View.WORLD)` and similar,
    with `MenuKit.back("World & Trade")`.

## Ranked findings, most player-visible first

### 1. Skyblock is the default mode, but its first-run guidance is the weakest

- **Quickstart.** `player/PlayerGuideService.java:42-62` prints 17 grey lines of plain text with
  no clickable commands. The legacy-mode quickstart at `:64-74` is a short numbered list
  with a gradient header and a `<click:run_command>` on every command. New players in the
  default mode get the worse one.
- **Tips.** `config.yml:187-211` ships 21 tips, but in Skyblock mode the code reads
  `skyblock.tips` (`PlayerGuideService.java:27`), which isn't in `config.yml`, so those 21 are
  never shown. They also refer to `/claim create`, `/claim directory` and `/claim mine`, which in Skyblock
  mode only answer "Manage your island, co-op and upgrades with /island."
  (`command/CommandRouter.java:323-326`). One tip hard-codes "100 blocks" (`config.yml:194`)
  even though `chat.local-radius` (`config.yml:177`) is configurable.
- **Fix.** Rewrite the Skyblock quickstart in the legacy format: 6 to 8 numbered steps, clickable
  commands, and "Everything else is in /menu" at the end. Add a `skyblock.tips` list to
  `config.yml` and add comments saying which list each mode uses. Fill the local-radius tip in
  from config.

### 2. Hub buttons lead to dead ends and one-way trips

- **Premium-only buttons.** The `/menu` Workshop and World pages show Forge, Enchanting,
  Alchemy, Identify, Repair All and Bank Vault as ordinary buttons
  (`gui/MenuManager.java:143-156`, `:168-169`). For anyone without `nerdsmp.station.remote`,
  every one of them closes the menu and prints a refusal. The refusal comes in three
  different wordings:
  - plugin.yml `permission-message`, in plain white: "Speak with Orin to use the bank. Remote station
    commands require Premium." (`nerdsmp-paper/src/main/resources/plugin.yml:141-150`, `:267-296`).
  - `CommandRouter.canOpenRemoteStation`: "Visit a banker in the world…" (`command/CommandRouter.java:677-684`).
    This code never runs, because plugin.yml blocks the command first.
  - `MenuManager.open`: "Use an anvil in the world to open the forge." (`gui/MenuManager.java:207-214`).
- **No way back.** `commandButton` closes the hub and runs a command (`gui/MenuManager.java:1508-1527`).
  Of the screens it opens, only Alchemy offers "Back to Menu"
  (`alchemy/AlchemyService.java:127`). Market, auctions, stores, island, leaderboards,
  cooking, fishing, excavation, bank, achievements and trade all end in **Close**.
- **Fix.**
  - For players without remote access, show station tiles as locked: grey name, plus a lore
    line such as "Visit Orin at the bank (Premium: open from anywhere)". Clicking one should not
    close the menu.
  - Use one denial line, taken from the NPC and station names in plugin.yml.
  - Delete the unreachable `canOpenRemoteStation` text, or move the plugin.yml messages into it
    and drop `permission-message`.
  - Give every screen the hub opens a **Back** button in the shared slot (see 4) that returns to
    the hub page it came from. Today `detailMenu` always returns to HOME
    (`gui/MenuManager.java:1476`), not to Workshop or World.

### 3. Chat messages: one flat colour, raw white text and leaked enum names

- **Flat-colour helpers.** These use one colour for every outcome, so a failure looks the
  same as a success:
  - aqua: `market/MarketController.java:658`, `market/AuctionController.java:318`,
    `claim/HomesteadController.java:892`, `skyblock/SkyblockController.java:678`,
    `skyblock/IslandBorders.java:69`, `social/SocialManager.java:170`
  - yellow: `trade/TradeManager.java:280`, `studio/StudioUi.java:186`
  - default white: `loot/profile/MobLootCommand.java:246`, `studio/LootStudio.java:417`,
    `studio/npc/NPCStudio.java:195`, `zone/ZoneCommand.java:318`

  Example: in Skyblock, "The island change could not be saved. Please contact staff." is
  aqua, the same colour as "Co-op a…" success lines (`skyblock/SkyblockController.java:159-165`).
- **Raw white `sendMessage("...")` in player flows.** `economy/PaymentController.java` has 16 of
  these, including the receipt "Paid 100 Marks to Steve. Receipt: …" (`:63`) and "You do not have
  enough Marks." (`:62`). `npc/NpcVendorTrading.java` has 14 ("Purchased … for … Marks.", `:87`). Others:
  `skyblock/IslandAutomation.java` (11), `skyblock/machine/IslandMachines.java` (10) and
  `travel/TravelManager.java` (10).
- **Enum names shown to players:**
  - "Could not select mastery: " + result → e.g. `NOT_ELIGIBLE` (`command/CommandRouter.java:233`)
  - "Could not remove mastery: INSUFFICIENT_MARKS." (`:267`)
  - "Order fulfillment failed: " + result.status() (`:313`)
  - "Contribution failed: " + result (`:554`)
  - 25 sites send `exception.getMessage()` straight to the player, for example
    `skyblock/SkyblockController.java:164` and `skyblock/IslandAutomation.java:158`.
- **Fix.** Standardise on the rich palette, which most of the code already uses:
  - `<red>` failure or refusal
  - `<green>` completed action
  - `<yellow>` pending, partial or refunded
  - `<gray>` detail and hints, with `<white>` for commands and values
  - `<gold>` for Marks amounts

  Add a small `presentation/Chat.java` with `success`, `error`, `notice`, `hint` and `marks(long)`.
  Map every result enum to a sentence, the way `partyResult` already does
  (`command/CommandRouter.java:1072-1091`).

### 4. Menu layout: no shared slot, colour or wording for navigation

These numbers are for 54-slot screens.

| Screen | Back | Close | Prev / Next |
|---|---|---|---|
| Skill tree `gui/MenuManager.java:467-468` | 45 | 53 | |
| MenuManager `detailMenu` `:1475-1477` | size−5 (40 or 49) | size−1 | |
| Alchemy `alchemy/AlchemyService.java:127-128` | 45 "Back to Menu" | 53 | |
| Achievements `achievement/AchievementService.java:245-247` | | 49 | 45 / 53 |
| Leaderboards `leaderboard/LeaderboardService.java:113-115` | | 49 | 45 / 53 |
| Fishing `activity/FishingService.java:152` | | **53** | |
| Excavation archive `activity/ExcavationService.java:370-375` | **49** | | 47 / 51 |
| NPC shop `npc/NpcManager.java:1305-1307` | 49 | **46** | |
| Store sell `market/MarketController.java:429-430` | **48** | 49 | |
| Homestead `claim/HomesteadController.java:746` | | **50** | |
| Island pages `skyblock/SkyblockController.java:258, 308, 377, 448` | 49, **53**, 40, **53** | none | |
| Content editor `engine/ContentEditor.java:643-645` | | | **46 / 52** |
| Repair `item/RepairService.java:97` | | 22, as an **ARROW** | |

- **Labels and colours.** Variants include "Back", "<yellow>Back</yellow>", "<aqua>Back</aqua>",
  "<aqua>Back to Menu</aqua>" (MenuManager) and "<yellow>Back to Menu</yellow>" (Alchemy).
  Paging is labelled "Previous", "Previous page" or "Previous Page".
- **Item-name colours.** In the plain-style menus, every item name takes the helper's colour,
  so **Close** is gold in market, auctions and homesteads (`market/MarketController.java:635`,
  `market/AuctionController.java:307`, `claim/HomesteadController.java:789`), and aqua in
  island, leaderboard, cooking, trade and studio screens (`skyblock/SkyblockController.java:687`,
  `leaderboard/LeaderboardService.java:120`, `item/KitchenService.java:237`,
  `trade/TradeManager.java:261`, `studio/StudioUi.java:177`).
- **Names are set two ways.** Some screens use `itemName`, others `displayName`.
- **Fix.** Use the slots most screens already use:
  - Previous 45, Back 48, Close 49, Next 53
  - Back is an ARROW named `<yellow>← Back to <Parent></yellow>`
  - Close is a BARRIER named `<red>Close</red>`
  - Paging items are named "Previous page" and "Next page", with "Page n/m" in the lore

  Put all of this in a `gui/MenuKit.java` used by every holder.

### 5. Island tab completion suggests raw UUIDs

- **What happens.** `/island home|journal|level|machines|chat|advance|leave <TAB>` suggests
  `islands.homes(actor)…` as **account UUIDs**, and `visit`, `accept`, `decline`, `kick` and
  `cancel` suggest `owner().toString()` (`skyblock/SkyblockController.java:177-182`). The resolver
  already accepts names (`:668-674`), and its error says "Use a known player name or their
  account UUID."
- **Fix.** Suggest owner and member names (`plugin.social().accountName`), and keep UUIDs as
  input only.

### 6. Command usage text: three versions per command, two bracket conventions, and Bukkit's raw usage line

- **Different subcommand lists.** `/island` has one list in plugin.yml (`plugin.yml:70`), a second in
  tab completion (`skyblock/SkyblockController.java:170`) and a third in the fallback help line
  (`:162`, which omits `generator`, `border`, `fixgenerator`, `visit`, `decline` and `cancel`).
  Other usage lines that don't match the code:
  - `/party` usage omits `deny` (`plugin.yml:111`), but it's handled and tab-completed (`command/CommandRouter.java:425, 991`).
  - `/nerdadmin` usage omits `releasecheck` (`plugin.yml:303`).
  - `/npc` usage omits `shopreview|shopretry|shopresolve` (`plugin.yml:167`).
  - `/market` usage omits `stores`, `review` and `resolve` (`plugin.yml:92`).
  - `/mobloot` and `/zone` use `help` as their whole usage line (`plugin.yml:57, 61`).
- **Bracket conventions.** plugin.yml uses `<required> [optional]`. In-game rich messages use
  `[required]`, because `<x>` would be read as a MiniMessage tag. For example, "Hold the requested
  material and use /jobs fulfill [order] [amount]." (`command/CommandRouter.java:299`) and
  "Use /staff mute [player] [minutes] [reason]." (`staff/StaffManager.java:263`). Plain messages use `<x>`.
  Altogether 110 plain usage strings use `<>` and 29 rich ones use `[]`.
- **Bukkit's raw usage line.** `return false` makes Bukkit print the plugin.yml usage line unstyled.
  That happens in `/mastery`, `/jobs`, `/party`, `/relics`, `/campaign`, `/grave`, `/duel`, `/marks`
  and `/nerdadmin` (`command/CommandRouter.java:147, 221, 270, 319, 459, 505, 559, 569, 611, 619, 884`).
  `/market`, `/store` and `/ah` print their own styled help instead.
- **Fix.**
  - Use `<required> [optional]` everywhere, escaped as `\<order>` inside MiniMessage, with a
    `Chat.usage("/jobs fulfill <order> <amount>")` helper that escapes for you.
  - Never `return false`. Print a styled usage line with clickable subcommands.
  - Generate the tab completion and the help line from one subcommand list per command.

### 7. Same idea, different words across systems

- **Invitations.** Party, friends, trade and teleport requests use `accept`/`deny`. Island uses
  `accept`/**`decline`**, with `kick` aliased to `cancel` and `leave` sharing a handler with `decline`
  (`skyblock/SkyblockController.java:159-160`).
- **Where the target goes.** `/claim home <owner> <action>`
  (`claim/HomesteadController.java:677-680`) but `/island <action> [owner]` (`skyblock/SkyblockController.java:103-110`).
- **Durations.** `/staff mute` and `/staff ban` take **minutes**, such as `1440` (`staff/StaffManager.java:263, 272`),
  `/watchdog` takes **hours** (`plugin.yml:280`), and `/digsite create` takes **seconds**
  (`plugin.yml:73`). The web console offers presets like "30 minutes" and "1 day" instead.
- **Two ways to pay.** `/pay <player> <amount>` and `/marks pay <player> <amount>` (`plugin.yml:13, 77`).
- **"Account loading".** Seven wordings, including:
  - "Your account is still loading." (most common)
  - "Your NerdSMP account is still loading. Try again in a moment." (`command/CommandRouter.java:920`)
  - "Your profile is still loading. Try again in a few seconds." (`gui/MenuManager.java:803`)
  - "Your account must be loaded in game." (`social/SocialManager.java:168`)
- **"No permission".** Ten or more wordings:
  - "Permission denied." (`market/MarketController.java:73`)
  - "Staff only." (`npc/NpcVendorTrading.java:153`, `skyblock/SkyblockController.java:92`, `trade/TradeManager.java:52`)
  - "You do not have permission." (`command/CommandRouter.java:689`)
  - "Missing nerdsmp.store.fulfill." (`:854`), which shows a raw permission node
  - "You do not have permission to manage zones." (`zone/ZoneCommand.java:49`)
- **"In game only".** Twelve or more wordings, e.g. "This command requires an in-game player."
  (`command/CommandRouter.java:913`) and "Payments are sent by players in game."
  (`economy/PaymentController.java:30`).
- **Fix.** Use `accept`/`deny` everywhere and keep `decline` as a hidden alias. Put the subject
  first: `<action> [player]`. Parse durations as `30m`, `2h`, `7d` or `perm`, and keep bare
  numbers working. Make `/marks pay` a silent alias. Add three constants to `Chat`:
  `ACCOUNT_LOADING`, `NO_PERMISSION` and `PLAYERS_ONLY`.

### 8. A button's label, its command and the screen title often don't match

| Hub label (`gui/MenuManager.java`) | Command it runs | Title of the screen it opens |
|---|---|---|
| Bank Vault (`:168`) | `/bank` | "Frontier Bank" (`player/BankService.java:191`) |
| Fishing Journal (`:132`) | `/fishing` | "Angler's Journal" (`activity/FishingService.java:74`) |
| Excavation Notes (`:134`) | `/excavation` | "Excavation Field Notes" |
| Cooking (`:151`) | `/cooking` (aliases kitchen, cookbook) | "Harvest Kitchen" (`item/KitchenService.java`) |
| Crafted Gear (`:149`) | `/recipes` | "Workshop Recipes" |
| Gear & Relics (`:114`) | `/relics` | "Relic Collections" |
| Wardrobe (`:116`) | `/cosmetics` | "Cosmetic Wardrobe" |
| Waypoints (`:181`) | `/expedition` or `/warps` | "Expeditions" or "Travel • n" |
| Jobs & Orders (`:128`) | `/jobs` | "City Merchants" in Skyblock |
| Preferences, Privacy Settings, Settings | all `View.SETTINGS` | "Player Settings" |
| Daily Contracts | `/daily` (alias `/quests`, code `View.QUESTS`) | "Daily Contracts" |

- **Brand name.** The brand is configurable: Hearthsky in Skyblock, NerdSMP otherwise (`NerdSmpPlugin.java:192`).
  Player text still hard-codes "NerdSMP" in several places:
  - "NerdSMP Quickstart" (`player/PlayerGuideService.java:64`)
  - "Your NerdSMP account is still loading" (`command/CommandRouter.java:920`, `engagement/EngagementService.java:116, 156`, `npc/NpcManager.java:2759`)
  - "not an authentic NerdSMP relic" (`command/CommandRouter.java:511`)
- **Fix.** Give each feature one player-facing name and use it for the button, the screen title
  and the `/help` description. Use `plugin.displayName()` or drop the brand name.

### 9. Marks are formatted three ways

- `"1234 Marks"`: 73 places build this as a raw `+ " Marks"` with no thousands separator.
- `VisualFormat.compact(...) + " ✦"`, giving "1.2K ✦" (`gui/MenuManager.java:345, 621, 645`,
  `presentation/PlayerPresentationService.java:374`).
- "1,000,000,000 Marks", written out by hand (`economy/PaymentController.java:46-48`).
- Price labels also vary: "<gray>Cost</gray> <gold>", "<gray>Cost:</gray> <red>",
  "<gray>Payout:</gray> <green>" and "<gray>Payout</gray> <gold>".
- **Fix.** Use `Chat.marks(long)` → `<gold>1,234 Marks</gold>` in chat and lore.
  Keep the compact form with "✦" for the sidebar only. Write labels as
  `<gray>Label</gray> <value colour>value`, with no colon, as MenuManager already does.

### 10. Confirmation, click hints and click handling differ between menus

- **Confirming.** There are three patterns:
  - A separate confirm screen with LIME_CONCRETE in slot 24: `market/MarketController.java:595-602`,
    `market/AuctionController.java:283-285`, `claim/HomesteadController.java:484-487`,
    `engine/ContentEditor.java:469-473`.
  - "Click again to confirm." on the same button: `gui/MenuManager.java:535, 776, 878`.
  - A 27-slot "Spend a Talent Point?" screen.
- **Click hints.** Hub tiles that look the same say "Click to open." (`gui/MenuManager.java:1504`) or
  "Click to view." (`:1518`), depending on how they're wired. Most plain-style menus have no hint.
- **Which clicks count.** MenuManager accepts left clicks only (`gui/MenuManager.java:264`), NPC menus
  accept left and right (`npc/NpcManager.java:421-425`), and island menus accept left clicks but
  not shift-clicks (`skyblock/SkyblockController.java:711`). This is fine if each screen says so.
  Only some do.
- **Sounds.** Alchemy plays 5 sounds, fishing 2 and bank 1. MenuManager, market and NPC menus play
  none.
- **Fix.** Use a confirm screen for anything that spends Marks or moves items, and "Click again"
  only for free toggles. Every clickable item gets a final `<yellow>Click to …</yellow>`
  line, plus `<gray>Right-click to …</gray>` where it applies. MenuKit plays one shared click
  sound.

### 11. Commands from the other game mode still show up in tab completion

- In Skyblock mode: `/claim …` only redirects to /island (`command/CommandRouter.java:323-326`), and
  `/jobs fulfill` says to visit merchants (`:291-293`).
- In legacy mode: `/expedition` says "Expeditions are available in Skyblock." (`:55`). `/island`
  prints developer text: "Skyblock is disabled. Use the Skyblock test launcher or enable
  gameplay.mode: skyblock." (`:59`).
- **Fix.** On enable, unregister the commands the current mode doesn't use, or remove their
  aliases. At minimum, replace the developer text with a player sentence.

### 12. Stuck transactions have three review commands, none in usage text or the web console

- `/market review` and `/market resolve` (`market/MarketController.java:71-79, 346-362`).
- `/ah review`, which points back to `/market review` (`market/AuctionController.java:86-92`).
- `/npc shopreview`, `/npc shopretry` and `/npc shopresolve` (`npc/NpcVendorTrading.java:153-175`).
- Staff reasons must be 8 to 256 characters here (`market/MarketController.java:358`,
  `npc/NpcVendorTrading.java:165`), but 3 to 180 in the web console (`panel/panel.js:935`, `:917`).
- **Fix.** Use one staff entry point, such as `/staff transactions` or a web-console "Economy" view
  backed by the same queues. Use one reason-length rule.

### 13. The web console is consistent inside but clashes with the game

`nerdsmp-paper/src/main/resources/panel/`. The page itself is the most coherent part of the
project: one dialog system, `toast`, `empty()` and `table()`.

- **Name.** The page is titled and branded "Watchdog" (`index.html:7, 21`), but it covers players,
  reports, sanctions, the server and staff. The anti-cheat view is also called "Watchdog"
  (`panel.js:90`). The docs and comments call the page "web console", "staff panel",
  "Watchdog panel", "staff console" or "the panel".
- **"Location" means two things.** The Players table's Location column is world coordinates
  (`panel.js:390`), while the Locations view is GeoIP (`panel.js:89`), as is the Location column
  in the connection tab.
- **Different from the in-game staff desk.** The game calls it the "Staff Desk" (`/staff`,
  `gui/MenuManager.java:99`). Ban and mute lengths are presets in the console but minutes in game
  (see 7).
- **Small inconsistencies.**
  - "First joined" calls `toLocaleDateString()` directly (`panel.js:403`) instead of the shared
    date helper (`:46`).
  - One inline `style` (`panel.js:849`).
- **Fix.**
  - Call the page "Staff Console", with Watchdog as one of its views. Use that name in the docs and config comments.
  - Rename the coordinates column to "Position".
  - Use the date helper.
  - Match in-game duration input to the console presets.
  - Wait until the geolocation work lands. It is editing these same files.

### 14. Permission and command naming for staff tools

- **Permission nodes.**
  - Four patterns: `nerdsmp.<domain>.admin` (npc, zone, loot, travel),
    `nerdsmp.content.items|mobs`, `nerdsmp.studio.*`, and
    `nerdsmp.staff.anticheat` alongside `nerdsmp.anticheat.bypass` (`plugin.yml:306-432`).
  - `nerdsmp.studio.tester` is declared but no default group uses it
    (`permission/PermissionRegistry.java:156-161`).
  - `nerdsmp.premium` isn't checked directly; it only exists as a parent node.
- **Command names.** `/itemengine` and `/mobengine` sit beside `/lootstudio`, `/npcstudio`,
  `/recipestudio` and `/worldstudio`. `/mobloot` and `/lootstudio` both edit loot.
  `docs/RECIPE_STUDIO.md:3, 19, 23` documents `/recipeengine`, which doesn't exist; the command is
  `/recipestudio`.
- **Fix.** Low urgency, because only staff see these. Add `/itemstudio` and `/mobstudio` as aliases
  and fix the doc. Don't rename permission nodes, which would break existing grants. Write the
  `nerdsmp.<area>.<verb>` convention into the style guide for new nodes, and add a "tester"
  default group.

### 15. Setup: config.yml and docs/

- **Cost keys.** Three suffixes: `*-cost-marks` (`season`, `identification`, `bank`), `*-cost`
  (`economy.remote-grave-recovery-cost`, `economy.claim-expansion-cost`, `repairs.base-cost`), and
  `market-listing-fee`.
- **Unit-less keys.** `engagement.playtime-rewards` uses bare minute keys `'30': 75`
  (`config.yml:219-223`). Every other duration key ends in its unit.
- **Toggles.** Flat `sidebar-enabled` beside nested `damage-displays.enabled` (`config.yml:270-289`).
- **Order.** The main switch, `gameplay.mode`, sits near the bottom (`config.yml:293-295`) with no
  blank line before it. The top of the file is a legacy prototype flag (`expeditions.generated-worlds`, `:1-4`).
  Comments are thorough under `anti-cheat` and absent for most other sections.
- **Data files.** `player_data/` uses snake_case; every other data file is kebab-case `.yml`.
- **docs/.** 58 files in one flat folder with no index. Design docs and `*_VERIFICATION.md` test
  logs sit side by side. `README.md` reads as a changelog of passes ("October gameplay integration
  pass", "September 29 … pass") rather than an overview of what the plugin is and how to set it up.
- **Fix.**
  - Don't rename existing keys now; that breaks deployed configs unless the code reads both names.
    For new keys, the convention is `-marks` for prices and a unit suffix for durations.
  - Move `gameplay` and `storage` to the top, with a header comment for each section.
  - Add `skyblock.tips` (see 1).
  - Add `docs/README.md` as an index grouped into Player systems, Staff tools, Operations and
    Verification logs, and move `*_VERIFICATION.md` into `docs/verification/`.
  - Turn the root README into an overview and a quick start, with history in `docs/CHANGELOG.md`.

---

## Conventions to standardise on

Each choice below is the one most of the codebase already uses.

1. **Chat.** MiniMessage through `Chat` helpers. `<red>` error, `<green>` success, `<yellow>`
   pending or notice, `<gray>` detail, `<white>` commands and values, `<gold>` Marks. No chat prefix,
   since none exists today. Every command in a message is clickable.
2. **Usage.** `/cmd <required> [optional]`, escaped inside MiniMessage. No `return false`.
3. **Menus.** Bold coloured title. Framed glass from `MenuManager.decorate`. Previous 45, Back 48,
   Close 49, Next 53. Back is an ARROW in `<yellow>← Back to X</yellow>`; Close is a BARRIER in `<red>`.
   Every clickable item ends with `<yellow>Click to …</yellow>`. Item names set with
   `itemName`, as MenuManager already does.
4. **Money.** `1,234 Marks` in gold in chat and lore; compact `1.2K ✦` only on the HUD.
5. **Verbs.** `accept`/`deny`, `invite`/`kick`/`leave`, `<action> [player]`. Durations as
   `30m`, `2h`, `7d` or `perm`.
6. **Names.** One player-facing name per feature, used for the button, the title and the command
   description. Never "NerdSMP" in player text; use `plugin.displayName()`.

---

## Work packages

Each package lists the files it owns. No two packages share a file. **WP0 must land first**;
after that, WP1 to WP6 can run in parallel. Packages marked *wait* touch files that other
sessions are editing now.

### WP0: Shared chat and menu kit, plus style guide (new files only)
- New: `presentation/Chat.java`, with semantic sends, `usage()`, `marks()`, the three standard
  refusals and a duration parser. New: `gui/MenuKit.java`, with nav slots and buttons, the confirm
  screen, click hints and the click sound. New: `docs/STYLE_GUIDE.md`, containing the conventions above.
- Unit tests for `marks()`, `usage()` escaping and duration parsing.
- Done when it builds and the tests pass. Nothing uses the kit yet.

### WP1: Core commands, hub and onboarding (findings 1, 2, 6, 8, 11 and part of 3, 7, 9)
- Files: `command/CommandRouter.java`, `gui/MenuManager.java`, `player/PlayerGuideService.java`,
  `engagement/EngagementService.java`, `nerdsmp-paper/src/main/resources/plugin.yml`.
- Rewrite the Skyblock quickstart. Show locked station tiles. Use one remote-station denial.
  Back buttons return to the right hub page. Rename hub labels to match the feature names.
  Remove `return false`. Map enum results to sentences. Fix the plugin.yml usage lines. Hide
  commands the current mode doesn't use. Remove hard-coded "NerdSMP".

### WP2: Commerce (findings 3, 4, 9, 10, 12 for these files)
- Files: `market/MarketController.java`, `market/AuctionController.java`,
  `economy/PaymentController.java`, `npc/NpcVendorTrading.java`.
- Use `Chat` and `MenuKit`. Add Back to the hub's World page. Make `/marks pay` an alias, in
  coordination with WP1, which owns plugin.yml; WP2 only changes the handler. Merge the review
  commands behind one staff entry point.

### WP3: Skyblock and homesteads (findings 3, 4, 5, 7 for these files)
- Files: `skyblock/SkyblockController.java`, `skyblock/IslandBorders.java`,
  `skyblock/IslandAutomation.java`, `skyblock/machine/IslandMachines.java`,
  `claim/HomesteadController.java`, `expedition/ExpeditionController.java`,
  `travel/TravelManager.java`.
- Tab-complete names instead of UUIDs. Generate the usage line from one subcommand list. Use
  `accept`/`deny`, with `decline` kept as an alias. Coloured outcomes. Shared nav slots.
  Coloured titles.

### WP4: Activities, stations and social screens (findings 3, 4, 8, 9, 10)
- Files: `activity/FishingService.java`, `activity/ExcavationService.java`,
  `alchemy/AlchemyService.java`, `item/KitchenService.java`, `item/RepairService.java`,
  `item/IdentificationService.java`, `player/BankService.java`,
  `achievement/AchievementService.java`, `leaderboard/LeaderboardService.java`,
  `npc/NpcManager.java`, `social/SocialManager.java`, `trade/TradeManager.java`,
  `chat/ChannelChatManager.java`.
- Use `MenuKit` nav and Back to the hub. Use the standard refusals. Format Marks the same way.
  Use the same confirm pattern. This is the largest package; it can be split into
  activities/stations and NPC/social as long as the file lists stay separate.

### WP5: Staff tools (findings 3, 7, 14 for these files)
- Files: `studio/**`, `engine/ContentEditor.java`, `zone/**`, `digsite/**`,
  `loot/profile/MobLootCommand.java`, `staff/StaffManager.java`,
  `permission/PermissionRegistry.java`, `docs/RECIPE_STUDIO.md`.
- Use `Chat` in the studio `tell` helpers. Accept `30m`, `2h` and `7d` in `/staff`. Add a tester
  group. Fix the doc's command name. Request the `/itemstudio` and `/mobstudio` aliases from
  WP1, which owns plugin.yml.

### WP6: Web console (finding 13). *Wait for the geolocation session to land.*
- Files: `nerdsmp-paper/src/main/resources/panel/index.html`, `panel.js`, `panel.css`.
- Rename the page to "Staff Console" and make Watchdog one view. Rename the coordinates column
  to "Position". Use the date helper. Remove the inline style. Show the same reason-length rule
  as in game. An Economy review view would need `anticheat/PanelData.java`, so leave it until the
  Watchdog and geolocation sessions are done.

### WP7: Setup and docs (findings 1 and 15). *config.yml: wait for the geolocation session to land.*
- Files: `nerdsmp-paper/src/main/resources/config.yml`, `README.md`, `docs/README.md` (new),
  moving `docs/*_VERIFICATION.md` into `docs/verification/`, `docs/CHANGELOG.md` (new).
- Reorder and comment sections. Add `skyblock.tips`. Write the docs index. Turn the README into
  an overview. Don't rename keys.
- Moving the verification docs breaks links from other docs. Search for them first; if any
  other package's docs link to them, leave those links alone and fix them afterwards.

### Not covered and worth a later look
- Whether one Back button per screen is enough for deep NPC dialogue → shop → product chains
  (`npc/NpcManager.java:1446-1584`).
- Unescaped MiniMessage around names a player can choose. Most systems use `Component.text`,
  which is safe, but any rich message that includes a store or island name should go through
  `escape()`. This is a security check, not polish.

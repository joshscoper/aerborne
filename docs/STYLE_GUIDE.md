# Player-facing style guide

How Aerborne talks to players and lays out its menus. The shared helpers are
`presentation/Chat.java` for chat and `gui/MenuKit.java` for menus, both under
`nerdsmp-paper/src/main/java/net/nerdsmp/paper/`. New code uses them. Older code is moving over
system by system; [POLISH_AUDIT.md](POLISH_AUDIT.md) tracks what's left.

## Chat

Write every message in MiniMessage. Colour says what happened:

| Colour | Meaning | Helper |
|---|---|---|
| `<red>` | Refused or failed | `Chat.error` |
| `<green>` | Done | `Chat.success` |
| `<yellow>` | Pending, partial, refunded, or worth noticing | `Chat.notice` |
| `<gray>` | Detail, hints, status | `Chat.info` |
| `<white>` | Commands and values inside a sentence | `Chat.command` for commands |
| `<gold>` | Marks | `Chat.marksTag` |

- Messages have no prefix.
- Escape anything a player typed or named, such as island, store and player names: `Chat.escape(name)`.
- Every command mentioned in a message is clickable: `Chat.command("island create")`.
- Never show an enum name, exception text or permission node. Map each result to a sentence, as
  `CommandRouter.partyResult` does. `Chat.humanize` is only a fallback.
- Use the standard refusals: `Chat.accountLoading`, `Chat.noPermission`, `Chat.playersOnly`.
- Say the brand with `plugin.displayName()`. Never hard-code "NerdSMP" in player text.

## Brand identity

Aerborne's primary tagline is **Small beginnings. Endless skies.** Its supporting line is
**Your adventure begins above the clouds.** Use a warm, clear fantasy voice: a useful next step,
a little personality, and no invented lore or promises about unfinished systems.

Use Aerborne Sky (`#77C8F5`) for brand headers, Cloud White (`#F4F9FF`) for key text and
Breeze Cyan (`#8CE8ED`) for restrained magical accents. The full palette and artwork brief are
in [AERBORNE_BRAND.md](AERBORNE_BRAND.md). Keep established rarity, warning and outcome colors:
the rebrand must not change what a color means in gameplay. Familiar feature names such as
Arcane Forge and Your Island do not need an Aerborne prefix.

The internal `NerdSMP` plugin name, `net.nerdsmp` packages, `nerdsmp:` identifiers and existing
permission nodes are compatibility contracts, not visible branding to replace.

## Commands

- Write usage as `/cmd <required> [optional]`, with `a|b` for choices. Use `Chat.usage(...)`,
  which escapes the brackets for MiniMessage and puts the command into the chat box when
  clicked. Inside a sentence, use `Chat.syntax(...)`, the same clickable command without the
  "Usage:" label: `"Search with " + Chat.syntax("/store directory <text>") + "."`.
- A handler never returns `false`. Bukkit would print the plugin.yml usage line without
  styling. Print `Chat.usage` instead.
- Keep the plugin.yml `usage:`, the tab completions and the help line in step. List each
  subcommand once and build all three from that list.
- Subcommand first, target second: `/party invite <player>`, `/island visit <player>`.
- Answering a request is `accept` or `deny`.
- Durations are written as `30m`, `2h`, `7d`, `1w` or `perm`. Check `Chat.isPermanent` first,
  then parse with `Chat.duration`, which still accepts a bare number in the command's old unit.
- Tab completion suggests player names, never UUIDs.

## Money

- In chat and lore, write Marks as `1,234 Marks` in gold (`Chat.marks`, `Chat.marksTag`).
- The compact `1.2K ✦` (`Chat.marksCompact`) is only for the sidebar and other tight spaces.
- Label a price as `<gray>Cost</gray> <gold>250 Marks</gold>`, with no colon.

## Menus

- **Title.** Bold, one colour or gradient, and the feature's player-facing name.
- **Frame.** The glass frame from `MenuManager.decorate`. Outside MenuManager, `MenuKit.frame(inventory)`
  draws the same one.
- **Bottom row.** Previous, Back, Close and Next go in the slots from `MenuKit`. On a 54-slot
  screen that's 45, 48, 49 and 53; on a 45-slot screen, 36, 39, 40 and 44.
  - Back: `MenuKit.back("Workshop")` gives `<yellow>← Back to Workshop</yellow>`. It names the
    screen it returns to, which is the parent page, not always the hub.
  - Close: `MenuKit.close()` gives a red barrier.
  - Paging: `MenuKit.previous` and `MenuKit.next` show "Page n/m".
- **Station screens.** The forge, the skill tree, Adventurer Ranks and alchemy fill their bottom
  row with tools, so they keep `StationLayout`: Back at 45 and Close at 53, using the same
  `MenuKit` items.
- **Click hint.** Every clickable item ends with a blank line and `<yellow>Click to …</yellow>`
  (`MenuKit.withHint`). Add `<gray>Right-click to …</gray>` only where right-click does something.
- **Premium stations.** Use `RemoteStation` for the forge, enchanting, alchemy, identification,
  repairs and the bank. It holds the one refusal message, which plugin.yml's `permission-message`
  repeats word for word (a test checks this).
- **Locked features.** If a player can't use a feature from here, show it locked with
  `MenuKit.locked`: a grey name and a lore line saying where to go instead. Clicking it does
  nothing and the menu stays open.
- **Confirming.** Anything that spends Marks or moves items gets the shared confirm screen,
  `MenuKit.openConfirm`: summary at 13, Cancel at 20, Confirm at 24. "Click again to confirm" is
  only for free toggles. Re-check the balance or items inside the confirm callback.
- **Sound.** Play `MenuKit.clickSound(player)` when a button does something.
- **Names.** Use one name per feature, the same on the hub button, the screen title and the
  command description. Set item names with `itemName` (`MenuKit.item` does), which is never italic.

## Feature names

| Feature | Command | Name |
|---|---|---|
| Account bank | `/bank` | Frontier Bank |
| Fishing | `/fishing` | Angler's Journal |
| Excavation | `/excavation` | Excavation Field Notes |
| Cooking | `/cooking` | Harvest Kitchen |
| Crafted equipment | `/recipes` | Workshop Recipes |
| Gear sets | `/relics` | Relic Collections |
| Cosmetics | `/cosmetics` | Cosmetic Wardrobe |
| Daily goals | `/daily` | Daily Contracts |
| Travel | `/expedition`, `/warps` | Expeditions, Warps |
| Settings | `/settings` | Player Settings |
| Identification | `/identify` | Arcane Identification |
| Bulk repair | `/repairall` | Repair All |
| Enchanting knowledge | `/arcane` | Arcane Codex |
| Gem experiments | `/arcane research` | Arcane Research |
| Exact enchanting | `/arcane imbue` | Chosen Imbuement |
| Build-defining enchantments | (Chosen Imbuement) | Arcane Signatures |
| Trade a bonus for wear | (Chosen Imbuement) | Corruption, Cleanse |
| Aspect focus | (Chosen Imbuement) | Attunement |

## Config

- A price key ends in `-marks`, and a duration key ends in its unit (`-seconds`, `-minutes`, `-hours`,
  `-days`, `-ticks`).
- Each feature switch is a nested `enabled:`.
- These rules are for new keys. Existing keys keep their names so deployed configs don't break.

## Examples

```java
// Outcomes
Chat.success(player, "Paid " + Chat.marksTag(amount) + " to <white>" + Chat.escape(target.getName()) + "</white>.");
Chat.error(player, "You need " + Chat.marksTag(missing) + " more.");
Chat.notice(player, "Your listing expired. The items are back in " + Chat.command("market claims") + ".");

// Refusals and usage
if (!(sender instanceof Player player)) { Chat.playersOnly(sender); return true; }
if (account == null) { Chat.accountLoading(player); return true; }
if (args.length < 2) { Chat.usage(player, "/pay <player> <amount>"); return true; }

// Durations
if (Chat.isPermanent(args[2])) { muteForever(target); Chat.success(sender, "Muted permanently."); return true; }
Optional<Duration> length = Chat.duration(args[2], ChronoUnit.MINUTES); // bare "30" is still minutes
if (length.isEmpty()) { Chat.usage(sender, "/staff mute <player> <30m|2h|7d|perm> [reason]"); return true; }
Chat.success(sender, "Muted for <white>" + Chat.duration(length.get()) + "</white>.");

// Menus
int size = 54;
inventory.setItem(MenuKit.previousSlot(size), MenuKit.previous(page, pages));
inventory.setItem(MenuKit.backSlot(size), MenuKit.back("World & Trade"));
inventory.setItem(MenuKit.closeSlot(size), MenuKit.close());
inventory.setItem(MenuKit.nextSlot(size), MenuKit.next(page, pages));
ItemStack tile = MenuKit.item(Material.CHEST, "<gold>Your Store</gold>",
    MenuKit.withHint(List.of("<gray>Stock</gray> <white>12 items</white>"), "manage"));

// Confirm before spending
MenuKit.openConfirm(plugin, player, "<gold><bold>Buy Listing</bold></gold>", listingIcon,
    List.of("<gray>Cost</gray> " + Chat.marksTag(price)),
    buyer -> market.buy(buyer, listingId),   // re-checks the balance itself
    buyer -> openListings(buyer, page));     // Cancel returns to the list; may be null
```

## API summary

For the sessions migrating systems onto the kit. Every `String` argument is MiniMessage markup
unless it says otherwise: escape player text with `Chat.escape` before you build markup.

**`net.nerdsmp.paper.presentation.Chat`** (static; `Audience` covers `Player` and `CommandSender`)

| Member | Result |
|---|---|
| `success / error / notice / info(Audience, markup)` | Sends in green / red / yellow / gray |
| `send(Audience, markup)`, `render(markup)` | Sends or builds a component with no colour added |
| `accountLoading / noPermission / playersOnly(Audience)` | The three standard refusals (red) |
| `ACCOUNT_LOADING`, `NO_PERMISSION`, `PLAYERS_ONLY` | Their plain sentences, for lore or tests |
| `escape(text)` | Player text made safe inside markup |
| `command("island create")` | White `/island create`; clicking runs it |
| `usage("/cmd <req> [opt]")`, `usage(Audience, …)` | Escaped `Usage:` line; clicking types `/cmd ` |
| `syntax("/cmd <req> [opt]")` | The same clickable command without the `Usage:` label, for mid-sentence use |
| `marks(long)` / `marksTag(long)` / `marksCompact(long)` | `1,234 Marks` / gold markup / `1.2K ✦` (HUD only) |
| `humanize(ENUM_NAME)` | `enum name`; a fallback, never the main message |
| `isPermanent(raw)` | True for `perm`, `permanent`, `forever` |
| `duration(raw, bareUnit)` | `Optional<Duration>` from `45s 30m 2h 7d 1w` or a bare number in `bareUnit` |
| `duration(Duration)` | `1d 2h`, `1h 30m` |

**`net.nerdsmp.paper.gui.MenuKit`** (static)

| Member | Result |
|---|---|
| `previousSlot / backSlot / closeSlot / nextSlot(size)` | Bottom row: size−9, −6, −5, −1 (45, 48, 49, 53 on 54) |
| `CONFIRM_SUMMARY_SLOT`, `CONFIRM_CANCEL_SLOT`, `CONFIRM_SLOT` | 13, 20, 24 on the confirm screen |
| `item(material, name, lore)` | Non-italic `itemName`, lore, all flags hidden |
| `withHint(lore, "open")`, `hint("open")` | Lore plus blank line plus `<yellow>Click to open.</yellow>` |
| `back("Parent")`, `close()` | Yellow `← Back to Parent` arrow; red Close barrier |
| `previous(page, pages)`, `next(page, pages)`, `pageLine` | Paging arrows with `Page n/m` (page counts from 0) |
| `locked(material, plainName, reasonLore)` | Grey, inert tile for a feature not available here |
| `confirm(detail)`, `cancel()` | The two confirm-screen buttons, if you build your own |
| `openConfirm(plugin, player, title, summary, detail, onConfirm, onCancel)` | Opens the shared confirm screen; handles its own clicks |
| `frame(inventory)` | The `MenuManager.decorate` glass frame |
| `clickSound(player)` | The one shared click sound |

Menus keep their own holder and click handling; MenuKit only supplies items and slots, except
for `openConfirm`, which owns its screen.


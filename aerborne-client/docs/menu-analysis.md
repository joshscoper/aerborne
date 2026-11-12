# Aerborne client: menu analysis

The state of every plugin menu in the companion client after the November 11 menu pass. It covers what each menu
looks like now and what could be added next. Every click still goes to the server's own slot, so no transaction logic
lives in the client.

## How the client reads a menu

The native screen sorts each chest into these parts:

| Part | What it is | How it is drawn |
|---|---|---|
| Header | 1–3 items alone on the top row (a shop sign, a merchant's reputation, the forge sign) | Recessed plates under the title: icon, gold name, first useful line |
| Chips | Tabs, filters and quantities (lore says "shown now", "Selected…", "Click to change…", or the name is "Buy 16") | Button row; the active chip is lit gold. Forge tabs light when the server names them in green |
| Content | Everything else | Cards (icon, name, two key lines with prices first), or a ranking table when 3+ items are named `#N Name` |
| Sockets | Empty input slots in a decorated menu (at most 4), for example the forge's Primary and Catalyst inputs | A Workbench card with the server's label; click with a carried item to place it, click again to take it back |
| Navigation | Everything on the bottom row | Footer buttons with icons (back, close, pages, refresh) or the item's own icon |
| Bag | Your inventory (3 rows plus the hotbar) under stations and selling shops | Real slots: click, right-click, shift-click; carried item drawn at the cursor; keys 1–9 change the held item |
| Talent tree | `… Talent Tree` from MenuManager | Fixed graph: badges with rank pips, gold paths once open, legend |

**Classic view** stays on every native screen as a fallback.

## Menu by menu

| Menu (server class) | Now | Notes and next additions |
|---|---|---|
| Auction House / My Auctions (`AuctionController`) | Native, tabs as chips; bag shown, and clicking any bag stack starts Create Auction for it (the server brings it to hand) | Next: a listing detail panel (bid history, a bid field), sort chips (price, time left), and a "my bids" view |
| Market and player stores (`MarketController`) | Native cards | Next: a stock counter on the card and a seller banner |
| NPC shop directory (`NpcManager`) | Native, header plates | Next: one category chip row per district |
| NPC shop page | Native; Buy and Sell lead each card; bag shown when the merchant buys goods | Next: a "you own N" badge on each card (the server already sends "In your bag" in lore) |
| NPC product page | Native; quantity chips; Confirm is framed green | Left-click reviews a purchase and right-click reviews a sale (server design). Next: a hero layout with the item large on the left and Buy/Sell on the right |
| Forge (anvil, `MenuManager` FORGE) | **Fixed.** It used to open native with no way to place gear. Now it has sockets and the bag | Next: draw the result preview between the sockets, and show the socket row (28–34) as gem slots |
| Repair, Identification, Kitchen, Alchemy, Arcane, Expedition Forge, Skill Lamp | Moved from the slot view to native, with the bag | Not yet seen in game here (NPC- or premium-gated). Alchemy adds ingredients by clicking the bag, which goes to the same server slot as before |
| Bank, Trade, Character Equipment, Run Loot | Native storage screen (`StorageMenuScreen`): the server grid cell for cell on larger slots, locked/empty placeholders drawn as cells, footer buttons, bag with vanilla clicks, shift-click, 1–9 swaps, Q to drop, double-click to gather | Trade shows a gold seam between the offers, and a confirmed side reads green. Trade has not been tested live (it needs two players) |
| Recipe Studio, Studio UI, Content Editor, Staff | Classic slot view | Staff tools; leave as is |
| Leaderboards (`LeaderboardService`) | Native table: medal colours, your row highlighted, categories as chips, "Find me" jumps to your row | Next: a page count from the server |
| Achievements (`AchievementService`) | Native cards, filters as chips, a progress bar on any card with `Progress`/`Completed n/m` lore | Next: a points total in the header |
| Skills / Talent trees (`MenuManager`) | Native talent graph | Next: show unspent points in the title bar and animate a path when a rank is learned |
| Main menu, Profile, Stats, Quests, Party, Vote, Tutorial, Campaign, Expedition menus, Homestead, Spirit, Excavation, Fishing, ReachFlights, Skyblock | Native generic cards | Each follows the header, chips and cards rules. Expedition queue and party menus would benefit from a member list (heads plus ready state) |

## Gaps worth closing next

1. **Server hints instead of heuristics.** The client guesses chips, headers and sockets from lore and position. A
   one-byte role per slot in the `aerborne:menu` payload (header, chip, active, socket, nav, content) would remove the
   guesswork and let any new plugin menu render well with no client change.
2. **Detail layouts**: a hero layout for one-item pages (a product, an auction listing, a forge result).
3. **Testing the gated stations** (repair, identify, kitchen, alchemy, arcane) in game with an NPC or premium account.

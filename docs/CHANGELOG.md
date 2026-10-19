# Changelog

Notable changes, newest first. Dates come from each pass's own write-up; entries without a firm
date are placed where the README had them. Each linked doc has the detail.

## October 2026

### Arcane Forge 2.0 (October 18)
- [Arcane Forge 2.0](ARCANE_FORGE.md): the Arcane Codex (`/arcane`), Arcane Research with socket
  gems, deterministic Chosen Imbuement, Arcane Capacity, overcharge and three Arcane Signatures.
  Only the read-only Codex is on by default; the rest is switched on per feature.
- Fixed: enchanting an item silently deleted enchantment entries this build could not read. The
  enchantment data is now written back losslessly.
- Fixed: right-clicking an enchanting table refused players without Premium, telling them to
  visit an enchanting table. The table now opens Arcane Enchanting for everyone, as Veyra does.
- Fixed: the Forge's tome preview said a tome did not fit relic gear that it would in fact apply to.
- `/forgeadmin` and `nerdsmp.forge.admin` for status, strict reload, migration reports and
  audited knowledge grants. `/forge` and `/enchants` are unchanged.
- The **Enchanting** skill: XP from every Arcane action, a four-branch talent tree, and Arcane
  requirements that use the higher of Enchanting and Smithing.
- Effect enchantments **Bloodletter**, **Veinseeker** and **Chain Harvest**, deterministic
  **Corruption** with guaranteed cleansing, and **Attunement**. All off by default.
- Decisions and compatibility notes: [integration report](ARCANE_FORGE_AUDIT.md).

### Polish and consistency pass (October 17)
- The [polish audit](POLISH_AUDIT.md) found the plugin spoke in two house styles. A shared chat
  and menu kit (`presentation/Chat.java`, `gui/MenuKit.java`) and the [style guide](STYLE_GUIDE.md)
  now set one style, and systems are moving onto it package by package.
- The Skyblock quickstart is a short clickable list, the hub's Back buttons lead to the right
  page, and commands from the other game mode no longer show in players' command lists.
- `config.yml` is grouped into commented sections with the game mode at the top. Skyblock tips
  are now configurable under `skyblock.tips`. No keys were renamed.
- Docs have an [index](README.md); verification logs moved to [verification/](verification/).

### Staff console and Watchdog (October 16)
- The web staff console: metrics, players, Watchdog evidence, reports, sanctions, audit and staff
  views, player profiles and web actions. See the [operator guide](STAFF_FISHING_STATIONS.md).
- [Watchdog](WATCHDOG_ANTICHEAT.md) runs in confidence-scored autonomous mode, with a combat
  model and movement simulation. [Player locations](PLAYER_LOCATIONS.md) adds offline
  geolocation to the console.

### Staff studios and island activities (October 11 to 16)
- In-game editors for content, loot, NPCs, recipes and the world, plus a quality desk. See the
  [studio docs](README.md#staff-tools).
- [Farming](FARMING.md) replant, [renewable dig sites](DIG_SITES.md), [island
  activities](ISLAND_ACTIVITIES.md), the [island workshop](ISLAND_WORKSHOP.md) and the
  [progression expansion](PROGRESSION_EXPANSION.md).
- Shared hubs and adventure areas are staff-built: `/setspawn`, `/setwarp` and `/zone` connect
  builds to travel and bounded RPG encounters ([manual zones](MANUAL_ZONES.md)). Automatic
  expedition-world generation is off by default; old generated prototypes are kept.
- Job caps, payout recovery, campaign dates and world unlocks persist across restarts.

### Player commerce (October)
- [Timed auctions](PLAYER_COMMERCE.md) with optional Buy Now prices (`/ah`), remote player
  stores (`/store`) and remote item trades (`/trade <player>`). Purchases and returns wait in a
  shared delivery inbox. Existing listings, balances and progress were kept.

### Skyblock gameplay integration (October 2)
- The [gameplay pass](SKYBLOCK_GAMEPLAY_PASS.md) fixed the starter generator, clarified skill
  trees, added harvest and timber rhythms and useful cooking and crafting choices, and made
  vanilla textures the default.
- `run-test-server.cmd` and `run-skyblock-test-server.cmd` both start Skyblock on port 25566.
  Legacy SMP needs `run-test-server.ps1 -LegacySmp` (port 25565), with separate world and data.

## September 2026

### Release hardening (September 29)
- [Recoverable player trades](HEARTHSKY_RELEASE.md), canonical friends and blocks, private co-op
  chat, invitation controls and safer teleports. Skyblock daily contracts explain island
  progression; legacy remote server-order selling became a city merchant guide.
- Staff can run `/nerdadmin releasecheck` for diagnostics and open release gates.

### Island progression and showcases (September 29)
- [Island levels and talents](HEARTHSKY_PROGRESSION.md), stacked spawners and mobs, filtered
  chunk hoppers, city-only supply-and-demand NPC shops, a new starter island and a one-second
  HUD refresh. The isolated test's island and player data were reset, with approval and a
  verified backup. The original SMP runtime was not changed.
- [Showcases](SKYBLOCK_SHOWCASES.md): four earned centerpieces, chapter reveals, quieter HUD
  updates and indexed island lookups (`/island showcase`).

### Skyblock conversion begins (September 28)
- Direction approved: [private co-op islands plus shared RPG zones](SKYBLOCK.md), under the
  working display name Hearthsky (`skyblock.display-name`). Plugin and item IDs are unchanged.
- Foundation: persistent account-owned islands, six-person co-ops, private or open visits, island
  menus and journal, shared skill-based expansion, tiered ore generators, void recovery,
  protected land and landing pads. The chunk generator never reads neighbouring terrain.
- SMP homesteads and player data were kept, not converted or erased.

### Homesteads (September 26)
- Legacy land claims became [Homesteads](HOMESTEADS.md): named cooperative territories with
  residents, roles, plot overrides, a management menu and stronger grief protection, then
  private resident homes, land-use presets, recruitment and applications, notices, change
  history and resident-only chat.
- The same review fixed bank and trade menu races, collection reward retries, NPC conversation
  staleness and repeat-harvest XP.

### Durable market and reviews (September 13 onwards)
- The [durable market rewrite](DURABLE_MARKET.md) persists listings, locks checkout payments and
  keeps offline deliveries with audited recovery.
- Harvest protection shares chunk-persistent placement records across skill XP, excavation finds
  and daily contracts, with verified tree regrowth. See the [economy
  review](economy-review-2026-09.md), [progression review](PROGRESSION_REVIEW.md) and [code
  review](CODE_REVIEW_2026-09.md).

## Foundation

The original SMP build that Skyblock grew from: Marks and the economy ledger, dailies and faction
contracts, story chapters, playtime rewards, diminishing jobs, server orders, Adventurer Levels
1 to 100, distance-scaled mobs, elites and vanilla boss mechanics, claims, parties, friends and
private messages, item trading, service NPCs with branching dialogue and reputation, NPC stores,
repairs, account banks, item identification, graves, opt-in PvP, socketable gear and gems, custom
enchantments, affixed loot, alchemy and poisons, fishing and excavation, market stalls, campaigns,
cosmetics, achievements, the HUD and menus, travel commands, moderation tools, the Velocity edge
plugin and the PostgreSQL schema. See the [roadmap](ROADMAP.md).

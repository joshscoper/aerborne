# Changelog

Notable changes, newest first. Dates come from each pass's own write-up; entries without a firm
date are placed where the README had them. Each linked doc has the detail.

## October 2026

### Expedition loot overhaul (October 22)
- Every expedition now has its own loot: three **materials**, a five-piece **signature set** with set
  bonuses (Barrowguard, Thornwarden, Tidebound, Gloomforged, Wardensworn and the raid's Kilnborn),
  and rare boss **signatures** such as the Huntsman's Longbow and the raid's mythic Sovereign's
  Hammer and Crown of Embers.
- Enemies drop the expedition's materials by role, and elites, mini-bosses and bosses drop more.
- **Crates, chests and hidden caches** stand in every expedition. Each player loots each once, with
  their own roll; finding hidden caches raises the run's score.
- Winning grades the run from **D to S+** by speed, survival and exploration. Every player gets a free
  **reward chest** that grows with the grade, and can open a **bonus chest** for Marks.
- The **Expedition Forge** (`/expedition forge`) turns an expedition's materials into pieces of its set.
- The guide book, the bestiary, the web API and the website show the new loot.

### Fair puzzles, clean drops and skill lamps (October 22)
- Right-clicking an **experience bottle** now opens a skill picker instead of throwing it: the bottle
  grants that skill 10% of the XP it needs for its next level (at least 40). **Greater** (25%, at least
  100) and **Grand** (60%, at least 250) bottles can appear in expedition loot. Shift-click spends the
  whole stack. Dispensers still throw bottles.
- Target and plate puzzles have a **supply crate**: right-click it for a bow and arrows or
  counterweights. Supplies are taken back when you leave, so no room needs anything brought from home.
- Expedition enemies no longer drop vanilla junk such as saddles, emeralds or their worn gear.

### Cinder Keep becomes the first raid (October 21)
- **Cinder Keep** is remodelled into a raid for up to eight players: nine volcanic caverns under the
  keep with lava channels, lavafalls, a rune lake and a lava-ringed final arena. Five boss fights:
  Gorrak the Kilnbreaker, the linked hounds Skarn and Vesh, Vael the Slag Priest, Steward Ashkar and
  his guard, and Pyrrhax, the Ashen Sovereign, who submerges twice and calls his Sons of Flame.
- Normal is tuned for pickup groups; Heroic and Mythic are for organised raids. Each boss, and the
  finish, pays its loot once per tier per week; lockouts reset on Monday.
- Beating Gorrak on any tier opens the Nether's Cinder Shelf route to everyone who was there.
- Expedition files gain room `loot:` and `unlocks:`, linked councils (`link-seconds`), submerging
  boss phases (`submerge-seconds`), tier `health-multiplier`/`damage-multiplier` overrides and the
  Wrath ability.
- Hoglins in expeditions can be hurt again (claim protection treated them as livestock), and
  encounter magma cubes no longer split.

### Mythic keystones (October 21)
- Clearing any campaign expedition on Mythic earns a **keystone**: a personal key to a random
  campaign expedition at +2. The keyholder starts it for their party with `/expedition key start` or
  the board's keystone card; there is no queue and no hired allies.
- Each level adds 8% enemy health and damage over Mythic. Weekly affixes join at +4 (Fortified or
  Tyrannical, alternating), +7 (Bolstering, Raging, Sanguine or Volatile, rotating) and +10 (both
  Fortified and Tyrannical).
- Key runs have no lives; each death adds 10 seconds. Beat the timer to upgrade the key by 1 to 3
  levels and move it to a new expedition; finish late, fail or leave and it drops a level.
- New affixes: **Raging** (low enemies hit harder) and **Sanguine** (fallen enemies leave a healing pool).
- The highest timed keys are recorded per player and per expedition, and the website's expedition
  pages show the timer, this week's affixes and the best key.

### The first campaign's expeditions (October 21)
- The **Hollow Barrow** is rebuilt: a sexton at the mouth, bone rain in the ossuary, a new Lever
  Vault with the Barrow Warden and four levers pulled in the order the candles flash, a brazier in
  the antechamber, and a cutscene and phases for the Hollow Huntsman.
- Four new expeditions, each opening once the one before it is cleared: **Thornwood Canopy**
  (treetops, a jump course and an enemy-forces goal), the **Sunken Aqueduct** (a timed run with
  valves, counterweights and geysers), **Gloomforge Mines** (a gauntlet of three mini-bosses before
  the Deep Foreman) and the **Hall of the Three Wardens** (three wings and three bosses in any order).
- Thirteen new expedition creatures: mini-bosses, bosses and elites with their own looks and
  abilities.
- `requires:` in an expedition file gates it behind other expeditions; the board shows the lock.
- Cutscenes started by teleporting into a room now return players to where they arrived.
- The web API carries the expedition list up to 60 KB, dropping bestiaries before guides when it
  must, and says so once instead of on every refresh.

### Expedition engine and the First Flight tutorial (October 21)
- [Expeditions](EXPEDITIONS.md) gain new room and run types: clear-every-enemy rooms, councils of
  two to four bosses, puzzle rooms, and run goals for bosses in any order, enemy forces, and beating
  the clock. Rooms can require other rooms, so a map can be a hub with wings, and can be optional.
- Puzzles: levers, button sequences (with a shown order and a reset on mistakes), pressure plates
  held at once, targets against the clock, and jump courses that put fallers back at the start
  without costing a life.
- The sidebar becomes the run panel (clock, lives, objectives with puzzle progress, party health),
  and runs show a single boss bar: the boss (a council shares one) or a mini-boss. The old run
  status bar is gone, and the general mob health bar no longer doubles up on expedition bosses.
- Campfires heal out of combat and become the respawn point once lit.
- Elites, mini-bosses and bosses use a kit of 15 telegraphed abilities. Bosses get phases and enrage
  timers. Enemies no longer hurt each other.
- Roleplay and arena tools: dialogue (`say`), talking characters, cutscenes, set pieces that rise or
  crumble, and hazards (falling rocks, fire, eruptions, poison gas).
- **First Flight**, a gentle tutorial across the sky islands with Captain Wren, is offered first to
  anyone who hasn't cleared it.
- Parties can hold up to eight, for raids; regular expeditions still take five.
- Expedition creatures ship in `content/expedition-mobs.yml` and can set size, speed, equipment and
  `ability: none`.
- Code-built templates rebuild themselves when an update changes their map. Existing sample
  templates rebuild once on the first run after updating.

### Parties rebuilt, and a performance pass (October 20)
- [Parties](PARTIES.md) are rebuilt. `/party` opens a party menu; invites arrive as a chat card
  with Accept and Decline buttons; a player can weigh invites from up to three parties at once.
- Open parties and a party finder (`/party list`, `/party join`). Leaders can add a short note
  and let members invite.
- The whole party hears about every change. Parties survive restarts. An offline leader hands
  over after a minute, and members offline for 15 minutes drop out. A leaving leader no longer
  has to promote someone first.
- Custom-mob abilities no longer scan every loaded entity each second: only custom, elite and
  boss mobs are tracked.
- Bank vaults, parties and playtime progress save off the main thread, and a burst of vault
  closes writes once instead of once per close.
- The sidebar updates as soon as Marks change. Its fallback refresh is now every 5 seconds
  (`presentation.refresh-ticks: 100`) instead of a database read per player every second.
  Existing servers keep their configured value until it is changed.
- Removed the unused points-campaign service, which the season campaign replaced.

### Party expeditions and the season campaign (October 20)
- [Expeditions](EXPEDITIONS.md) are now instanced party adventures for one to five players. Each
  run is a private copy of a template world with rooms (waves, survive, boss), gates, checkpoints,
  shared lives, a time limit and per-player loot. Parties start with a ready check, and the finder
  groups solo players and partial parties.
- Normal, Heroic and Mythic tiers and five modifiers. Optional encounter scripts. Clears and best
  times are recorded.
- The Nether and the End are reached only through expeditions: vanilla portals are sealed.
  Samples: **The Hollow Barrow** (overworld) and **Cinder Keep** (Nether), built from code.
- Staff build expeditions in game with the Expedition Wand and `/expedition create`, `room add`,
  `wave`, `boss`, `gate`, `check`, `test` and `publish`. Validation names every missing step.
- The [season campaign](CAMPAIGN.md) replaces the single points campaign. Goals, sources and
  milestone rewards (server buffs, skill XP and job Marks boosts, claim-once items and Marks,
  expedition and feature unlocks) live in `campaign/season.yml`. Sample season: **The Ember Accord**.
- `/expedition` no longer lists warps; use `/warps`. The World menu has Expeditions and Warps.
- Party-size scaling: smaller groups meet fewer, softer enemies.
- Difficulty ratings on expedition cards, and a guide per expedition (`/expedition guide <id>` and
  `/expeditions/<id>` on the website) with the route, boss notes, every mob and its possible drops.
- The Hollow Huntsman no longer one-shots players: run worlds use Normal difficulty instead of
  Hard, and bosses take `boss-damage`/`boss-health` multipliers. The Barrow's boss is at 60% damage.
- Hired allies: after a long wait in the finder, the queue leader can fill empty slots with
  labelled NPC allies (Bulwark, Blade, Hedge-witch, Archer). These assisted runs pay loot but
  count toward neither the campaign nor the expedition records.
- Game API: public `/api/v1/expeditions` and `/api/v1/campaign`; the website has matching pages.

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

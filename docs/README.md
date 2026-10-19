# Aerborne documentation

Start with the [project README](../README.md) for what the plugin is and how to run it. Release
history is in the [changelog](CHANGELOG.md).

## Player systems

How each feature works for players, with its commands.

**Skyblock and islands**
- [RPG Skyblock conversion](SKYBLOCK.md): the game mode, what's built and what's still pending
- [Island resources and workshop](ISLAND_WORKSHOP.md): the island journal, generators and machines
- [Island activities](ISLAND_ACTIVITIES.md): borders, visits and other island tools
- [Island mob stacking](MOB_STACKING.md): compatible mobs, bounded reconciliation and diagnostics
- [Island progression, Cooking and player economy](ISLAND_COOKING_ECONOMY.md)
- [Fishing on your island](FISHING.md)
- [Farming](FARMING.md)
- [Renewable excavation](DIG_SITES.md)

**Economy and trading**
- [Auctions, player stores and trades](PLAYER_COMMERCE.md)
- [Remote player trades](REMOTE_TRADING.md)
- [Durable player market](DURABLE_MARKET.md)
- [Shops](SHOPS.md): the NPC merchants

**Gear and progression**
- [Gem sockets](GEM_SOCKETS.md)
- [Arcane Forge 2.0](ARCANE_FORGE.md): the Arcane Codex, research, Chosen Imbuement and Arcane Signatures ([integration report](ARCANE_FORGE_AUDIT.md))
- [Progression expansion](PROGRESSION_EXPANSION.md): skill and island talent trees
- [Harvest progression and resource provenance](PROGRESSION_REVIEW.md)
- [Vanilla mob drop review for Paper 26.2](VANILLA_MOB_DROPS_26.2.md)

**Legacy SMP mode**
- [Homesteads](HOMESTEADS.md): land claims, residents and plots

## Staff tools

In-game editors and moderation. Most need a staff permission.

- [Content Studio](CONTENT_ENGINE.md): items and encounters without YAML
- [MMORPG team toolkit](DEVELOPER_STUDIO.md): `/studio` and its modules
- [Loot Studio](LOOT_STUDIO.md)
- [NPC Studio](NPC_STUDIO.md)
- [Recipe workshop](RECIPE_STUDIO.md)
- [World workshop](WORLD_STUDIO.md)
- [Content-team Quality Desk](QA_STUDIO.md)
- [Placing NPCs](NPC_CATALOG.md)
- [Manual hubs, warps and mob zones](MANUAL_ZONES.md)
- [Shared RPG zones and mob loot](RPG_ZONES.md)
- [Staff, Watchdog and workshop update](STAFF_FISHING_STATIONS.md): the web staff console and its network setup
- [Watchdog anti-cheat](WATCHDOG_ANTICHEAT.md)
- [Player locations](PLAYER_LOCATIONS.md): where players connect from, in the staff console

## Operations

Running, deploying and planning the server.

- [Aerborne rebrand and deployment](AERBORNE_REBRAND.md): compatibility, aliases and safe rollout
- [Aerborne brand reference](AERBORNE_BRAND.md): palette, voice and future artwork direction

- [Operations runbook](OPERATIONS.md): proxy, Paper, backups and deployment
- [Port-9028 web API](WEB_API.md): public status, authenticated telemetry and network setup
- [Architecture](ARCHITECTURE.md)
- [Ten-week whitelisted beta](BETA_RUNBOOK.md)
- [Monetization and trust policy](MONETIZATION_COMPLIANCE.md)
- [Delivery roadmap](ROADMAP.md)
- [Aerborne: release hardening and remaining gates](HEARTHSKY_RELEASE.md)
- [Aerborne: Java-only hub candidate](RELEASE_HUB.md)
- [Public presentation and deployment defaults](PUBLIC_BRANDING.md)

**Design and world building**
- [Content bible](CONTENT_BIBLE.md): tone, factions and story
- [Player-facing style guide](STYLE_GUIDE.md): chat, commands and menus for developers
- [Aerborne brand reference](AERBORNE_BRAND.md): palette, voice, UI identity and future art direction
- [Floating city spawn blueprint](FLOATING_CITY_SPAWN.md)
- [Map commission brief](MAP_COMMISSION_BRIEF.md)

## Reviews and passes

Dated write-ups of past work. The [changelog](CHANGELOG.md) summarises them.

- [Polish and consistency audit](POLISH_AUDIT.md), October 2026
- [Skyblock gameplay integration](SKYBLOCK_GAMEPLAY_PASS.md), October 2026
- [Island progression and test reset](HEARTHSKY_PROGRESSION.md), September 2026
- [Island presentation and optimization](SKYBLOCK_SHOWCASES.md), September 2026
- [Code review](CODE_REVIEW_2026-09.md), September 2026
- [Economy and item safety review](economy-review-2026-09.md), September 2026
- [Grave protection review](GRAVE_REVIEW.md)

## Verification logs

Test records from specific builds, in [verification/](verification/). Each describes the build it
tested, so newer changes may not be covered.

- [Content Studio](verification/CONTENT_ENGINE_VERIFICATION.md)
- [Developer studio](verification/DEVELOPER_STUDIO_VERIFICATION.md)
- [Farming](verification/FARMING_VERIFICATION.md)
- [Island activities](verification/ISLAND_ACTIVITIES_VERIFICATION.md)
- [Island workshop](verification/ISLAND_WORKSHOP_VERIFICATION.md)
- [NPC chat](verification/NPC_CHAT_VERIFICATION.md)
- [Player commerce](verification/PLAYER_COMMERCE_VERIFICATION.md)
- [Progression expansion](verification/PROGRESSION_EXPANSION_VERIFICATION.md)
- [RPG zones](verification/RPG_ZONES_VERIFICATION.md)
- [Staff fishing stations](verification/STAFF_FISHING_STATIONS_VERIFICATION.md)
- [Watchdog console, live run](verification/WATCHDOG_CONSOLE_LIVE_VERIFICATION.md)
- [Watchdog panel, first-run configuration](verification/WATCHDOG_PANEL_FIRST_RUN_VERIFICATION.md)
- [Watchdog panel, network binding](verification/WATCHDOG_PANEL_NETWORK_VERIFICATION.md)

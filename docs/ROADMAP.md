# Delivery roadmap

## Implemented foundation

- Java 25 Gradle multi-module repository and CI.
- Paper 26.2 and Velocity 4.1.1 pinned APIs.
- Public account, economy, progression, item, market, claim, campaign, and entitlement contracts.
- Playable development implementations for Marks, rotating daily contracts, AFK-filtered playtime rewards, diminishing jobs, capped data-driven server orders, skills/masteries, connected chunk claims with trust roles and owner flags, parties with friendly-fire protection, physical stalls, graves, consensual PvP, campaign contributions, relic authenticity, cosmetics, and account linking.
- Data-driven custom-mob framework with telegraphed pulse abilities, twelve variants, three bosses, relic drops, Mark rewards, and staff spawn controls.
- Data-driven custom gear engine with six three-piece relic collections, three five-piece crafted collections spanning Smithing 10–30, a durable one-time seasonal starter set, authentic-item validation, hidden vanilla attributes, custom tooltips, custom damage, defense, recipes, and set-bonus evaluation.
- Random graded geodes, six tiered gems, persistent gear sockets, six custom enchantments, custom-mob tome drops, and a parity-safe forge GUI.
- Deterministic same-type/same-tier gem merging through Tier IV, with authenticated metadata and confirmation.
- Twenty-six manually placed civic NPCs with protected Paper-API bodies, player tracking, tuned branching dialogue menus, typed requirements/effects, story flags, cooldowns, three factions, ledgered reputation, rotating daily/weekly/monthly faction contracts, discounted supply shops, and validated entry points into every basic player service.
- Twenty-one persistent account achievements covering progression, professions, combat, fishing, excavation, and archive deposits, with idempotent Marks rewards and an in-game record.
- Field Archive reputation appraisals through Elian Quill for pottery sherds, the nine-piece excavation collection, relic discs, and sniffer eggs.
- A persistent twelve-species Angler's Journal with habitat-weighted catches, open-water rarity, valuable treasure, fishing encounters, a collection achievement, and Free Delver appraisals through Ivo Reed.
- Accurate XP-to-level feedback across mining, forestry, farming, fishing, hunting, excavation, smithing, crafting alchemy, and attributed brewing.
- Three-branch talent trees for all nine skills, six earned choices per maxed skill, and active XP, job, gathering, combat, fishing, geode, durability, brewing, and building effects.
- Combined level 1–100 Adventurer progression with seven earned ranks and passive rank perks, plus distance-scaled hostile mobs, combat-scaled elites, and mechanics-driven vanilla bosses.
- Reviewed Paper 26.2 mob loot with narrow junk trimming, themed family rewards, tiered unidentified gear, boss material payouts, and idempotent pickup-to-Marks Coin Purses.
- One-command localhost Paper test-server bootstrap with pinned-download checksum verification and explicit EULA handling.
- Nexo 1.25+ resource-pack ownership with an external source pack, stable ItemModel keys, licensed-jar installation hook, local/production settings fragments, and Scaffolding/NexoProxy deployment boundaries.
- PostgreSQL migrations plus persistent account, identity, economy-ledger, entitlement, skill, talent, mastery, and respec-cooldown adapters.
- Java/Bedrock shared identity manifest and Nexo source-pack fallbacks.
- Audited staff desk with reports, player/gameplay snapshots, read-only inventories, notes, warnings, timed mutes, freezes, confirmed kicks, histories, and case resolution.
- Secure topology templates and operational/compliance/map/beta briefs.

## Persistent alpha gate

- Add PostgreSQL repositories for claims/trust, stalls/listings, campaign state/contributions, and grave indices.
- Persist daily job budgets and order fulfillment, add rotation scheduling, and implement utility-only server shops.
- Add repair/reforge currency sinks, roll provenance, and more situational ability types to the relic-set engine.
- Expand custom mobs from the shared pulse behavior into bespoke ability profiles and add scheduled encounter orchestration.
- Replace Nexo's vanilla model fallbacks with commissioned assets and validate the Scaffolding-generated Bedrock pack/Geyser mappings.
- Add LuckPerms/CoreProtect integration tests, staff audit commands, telemetry, and seasonal reset tooling.

## Content-complete beta gate

- Import and verify all commissioned worlds and resettable regions.
- Deliver three ambient templates and one full campaign with two finale windows.
- Complete Bedrock forms and client parity screenshots.
- Execute economy simulation, exploit testing, load testing, restore drill, moderation rehearsal, and the complete commerce lifecycle suite.

# Explore and Game Guide review

Reviewed October 19, 2026 against the repository's Java implementation, bundled configuration and gameplay documentation. Scope: 33 Handbook guides, three Explore entries, 18 public Codex entries, nine Aspects, six companion designs and eight trail designs.

The website's local database contained starter-authored content for these entries. Revision 6 refreshes matching original guide and region copy, adds preview usage notes, and labels supplied companion art as illustrations. It compares original text as well as authorship so staff edits, including direct database edits, are preserved. Existing records and URLs remain in place.

## Evidence map

Paths below are relative to the repository root. This is an editorial record, not player-facing instructions to change the server.

| Content | Primary implementation / configuration |
|---|---|
| Join and create an island; island basics | `nerdsmp-paper/src/main/java/net/nerdsmp/paper/skyblock/IslandGenerator.java`, `IslandService.java`, `SkyblockController.java`; `resources/plugin.yml` |
| Milestones and resource stages | `skyblock/IslandChapter.java`, `IslandResourceStage.java`, `IslandResourceProgression.java`, `IslandDevelopment.java`; `docs/SKYBLOCK.md` |
| Machines | `skyblock/machine/MachineKind.java`, `MachineRecipes.java`, `IslandMachines.java`; `docs/ISLAND_WORKSHOP.md` |
| Spawner ingredients and unlocks | `skyblock/IslandSpawnerCatalog.java`, `IslandMobStackPolicy.java`; `docs/MOB_STACKING.md` |
| Co-op, visitors and borders | `skyblock/IslandService.java`, `SkyblockController.java`, `IslandBorders.java`; `docs/SKYBLOCK.md` |
| Personal skills and talents | `nerdsmp-api/src/main/java/net/nerdsmp/api/model/Skill.java`, `SkillTreeCatalog.java`; `docs/PROGRESSION_EXPANSION.md` |
| Fishing | `activity/IslandFishingPolicy.java`, `FishingService.java`, `FishingCatch.java`; `docs/FISHING.md` |
| Farming | `docs/FARMING.md`, `docs/verification/FARMING_VERIFICATION.md`; bundled progression XP configuration |
| Excavation | `digsite/` implementation; `docs/DIG_SITES.md`; public command registration |
| Cooking and daily goals | `item/KitchenService.java`, `command/CommandRouter.java`, `content/cooking.yml`, `content/daily-quests.yml`, `content/achievements.yml`; `docs/ISLAND_COOKING_ECONOMY.md` |
| Merchants | `npc/NpcVendorTrading.java`, `ShopControls.java`; `docs/SHOPS.md` |
| Auctions and player stores | `market/AuctionController.java`, `MarketController.java`, `DurableMarketService.java`; `docs/PLAYER_COMMERCE.md` |
| Remote trades | `trade/RemoteTradePolicy.java`, `TradeManager.java`; `docs/REMOTE_TRADING.md` |
| Bank and payments | command/menu implementation; `docs/ISLAND_COOKING_ECONOMY.md` |
| Gear, gems and recovery | `forge/ArcaneForgeService.java`, `item/IdentificationService.java`, `SocketRules.java`; `docs/GEM_SOCKETS.md` |
| Arcane Forge, research and mastery | `forge/arcane/ArcaneForge.java`, `ArcaneResearch.java`, `ImbueRules.java`, `ResearchProfile.java`, `ArcaneSettings.java`; `resources/config.yml` |
| Capacity, corruption, attunement and tiers | `forge/arcane/ArcaneCapacity.java`, `ArcaneCorruption.java`, `ArcaneSettings.java`; bundled `arcane-forge` configuration |
| All 18 Codex entries and nine Aspects | `forge/ArcaneEnchantment.java`, `forge/arcane/ArcaneRegistry.java`, `ArcaneSignature.java`, `ArcaneAspect.java`. Names, equipment, maximum levels and effect summaries match these definitions. Private Aspect combinations remain private in HTML, filters and JSON. |
| World travel and Explore entries | `travel/TravelManager.java`, public `/expedition` and `/warp` registration; `docs/SKYBLOCK.md`, `docs/RPG_ZONES.md`. City artwork is not evidence of a completed map. |
| Graves | `grave/GraveManager.java`, `command/CommandRouter.java`, `resources/config.yml`; `docs/GRAVE_REVIEW.md` |
| Parties and commands | `resources/plugin.yml`, command handlers; island membership remains distinct from parties |
| Website linking and Arcade | `webapi/WebLinkCodes.java`; website `community.py`, `arcade.py`; `webapi/WebsiteRewards.java` |
| Companions and trails | Existing authored design catalog and supplied illustrations. No matching implementation proves that these named pets or trails are equipable, so they remain planned designs with acquisition unannounced. |

Except where a full path is specified, Java paths are under `nerdsmp-paper/src/main/java/net/nerdsmp/paper/`; resource paths are under `nerdsmp-paper/src/main/resources/`.

## Material corrections

- Graves are not complete inventory insurance: 27 stacks, unprotected overflow, expiry, possible placement failure, and an index that is not rebuilt after restart. Physical recovery is free; `/grave recover` starts a paid shortcut (default 250 Marks) and inventory overflow can drop at the player's feet.
- Arcane level gates use the higher of Enchanting and Smithing. Research consumes selected gems and Dust; the default is 4 Dust per reagent, with Fundamental/Specialized level gates of 5/10. Signatures and the three new runtime effects are disabled in the bundled release defaults and shown as previews.
- Chosen Imbuement uses a reviewed exact cost, not a blanket per-item price. Corruption, cleansing and attunement costs are labelled as defaults; the current quote is authoritative.
- Auction bids reserve Marks immediately. Listing fees, sales tax, inbox collection and accepted-bid restrictions matter; closing a menu is not a refund guarantee.
- Farming separates ripe field crops, berries, specialty seeds, fruit and stalks. Not every listed crop supports right-click replanting.
- The cooking catalog count is no longer hard-coded. The in-game cookbook shows current recipes and requirements.
- Explore separates the usable island system, the city preview and planned expedition overview. Public routes come from `/expedition`; no finished map or opening date is invented.
- Companion illustrations do not represent finished in-game models. No invented animations, colour variants, unlock methods or release dates were added.

## Limits of the review

This checks the supplied source and configuration. It does not establish which jar, private configuration overrides, NPC placements or destination definitions the remote public server is running. The public status ping confirms reachability, version and player count, not gameplay flags or world contents. Variable prices, XP rates and feature gates are therefore tied to in-game menus and labelled default tuning where numerical examples are useful. No server gameplay settings or worlds were changed.

Further updates should compare content with the new implementation/configuration and review staff-authored overrides before changing them. Run the website reference-page and hidden-recipe checks after updating copy or filters.

# Arcane Forge 2.0: codebase integration report

This report records what the repository actually contained before Arcane Forge 2.0, and every
compatibility decision that follows from it. The upgrade brief described a "historical" system
with nine Aspects, Arcane Capacity, instability, corruption, Bloodletter, Veinseeker and an
Enchanting profession. **None of those exist in this codebase.** The brief's own rule applies:
the code is authoritative, so Forge 2.0 extends what is here instead of pretending to migrate
data that was never written.

## 1. Platform

| Item | Brief assumed | Actual |
|---|---|---|
| Server API | Paper 1.21.11 | Paper API `26.2.build.121-stable` (`api-version: '26.2'`) |
| Java | 21 | 25 (bundled at `.toolchains/jdk25`) |
| Build | Gradle multi-module | Gradle 9.4 multi-module: `nerdsmp-api`, `nerdsmp-paper`, `nerdsmp-edge` |
| Public name | Aerborne | **Hearthsky** (`skyblock.display-name`); plugin and item IDs stay `nerdsmp` |
| Geyser/Floodgate | Retain | Not integrated. The README describes a Java-only server; `deploy/geyser` is empty. |
| Folia | Only if supported | Not supported; everything runs on the Bukkit main thread. |

## 2. Modules and ownership

| Area | Owner | Notes |
|---|---|---|
| Arcane Enchanting (table) | `forge/ArcaneEnchantingService` | Right-click an enchanting table: three seeded random offers, paid in Arcane Dust. Vanilla enchanting and enchanted-book anvil merges are cancelled. |
| Forge (anvil) | `forge/ArcaneForgeService` + `gui/MenuManager` (`View.FORGE`) | Sockets gems, merges gems, recovers gems for Dust, improves workmanship quality, applies enchantment tomes, repairs. |
| Enchantment definitions | `forge/ArcaneEnchantment` (enum, 12 entries) | Every enchantment is a passive stat bonus that feeds the shared gear-stat pipeline. |
| Item data | `item/PaperItemService` | The only reader and writer of gear PDC. Rebuilds lore from data on every `refresh`. |
| Stats in combat and gathering | `item/GearSetService`, `listener/SkillListener`, `forge/ArcaneForgeService` | `GearSetService.onDamage` (HIGHEST) applies weapon damage, execute and defense; `onLifeSteal` (MONITOR) heals. |
| Profession | Smithing (`Skill.SMITHING`, max level 50) | Smithing gates enchant offer strength (10/25/40) and quality tiers. There is **no Enchanting skill.** |
| Player persistence | `storage/PlayerDataStore` | One atomic YAML document per account in both storage modes; services own namespaced paths. |
| Skill XP | `ProgressionService.awardExperience(account, skill, xp, fingerprint)` | Fingerprints are idempotent (durable in both YAML and PostgreSQL). |
| Commands | `command/CommandRouter` + `plugin.yml` | Flat routing. `/forge` and `/enchants` require `nerdsmp.station.remote` in `plugin.yml`; a test pins this. |
| Permissions | `permission/StaffPermissions` + `plugin.yml` | Internal registry applied as Bukkit attachments; checks use `hasPermission`. |
| Menus | `gui/MenuKit` + per-service holders | `docs/STYLE_GUIDE.md` fixes navigation slots, confirm screens and wording. |
| Anti-cheat | `anticheat/WatchdogService` ("Watchdog") | Built in; GrimAC is only a soft-dependency. |

## 3. Enchantment data flow (before)

1. **Table.** `ArcaneEnchantingService.view` finds the nearest enchanting table within 4 blocks,
   counts bookshelves (max 15), reads Smithing, and rolls three offers from a seed of
   `player.getEnchantmentSeed()`, the UUID, the material and the item's current enchantments.
2. **Review.** `MenuManager.reviewEnchantment` renders a before/after clone.
3. **Apply.** `applyReviewed` re-checks game mode, held slot and item equality, re-rolls the view,
   requires the same offer, then `PaperItemService.enchant` writes the item and Dust is removed.
   Both happen in one main-thread call; a fresh enchantment seed is drawn afterwards.
4. **Tomes.** Custom mobs (4%) and bosses (75%) drop tomes. The Forge applies a tome from its
   catalyst slot and consumes it.
5. **Effect.** `PaperItemService.combinedStats` sums definition stats × quality, gems,
   enchantments, affixes and signature affix stats. Consumers read `GearProfile.stat(...)`.

## 4. Stored data formats

All keys are namespaced `nerdsmp:`.

| Key | Type | Meaning | Readers / writers |
|---|---|---|---|
| `item_id`, `item_schema` (5), `item_origin` | STRING/INT/STRING | Item identity. `isAuthentic` requires schema exactly 5. | `PaperItemService` only |
| `custom_enchantments` | STRING `id:level\|id:level` | Arcane enchantments | `PaperItemService` only |
| `socket_capacity`, `sockets` | INT / STRING | Gem sockets | `PaperItemService`, `SocketCodec` |
| `gear_quality` | INT | Workmanship tier 0–4 | `PaperItemService` |
| `loot_affixes`, `unidentified`, `loot_instance`, `signature_affix` | various | Loot rolls and sealed state | `PaperItemService` |
| `forge_kind`, `geode_grade`, `gem_type`, `gem_tier`, `arcane_enchantment`, `arcane_enchantment_level` | various | Geodes, gems and tomes | `ArcaneForgeService` |
| `arcane_dust` | BYTE | Arcane Dust marker | `ArcaneEnchantingService` |

None of `arcane_instability`, `arcane_finalized`, `ce_<id>`, capacity, aspect or corruption keys
exist anywhere in source, configs or docs.

## 5. Commands and permissions (before)

| Command | Gate | Effect |
|---|---|---|
| `/forge` | `nerdsmp.station.remote` (plugin.yml) | Opens the Forge remotely |
| `/enchants` | `nerdsmp.station.remote` (plugin.yml) | Opens Arcane Enchanting remotely |
| `/nerdadmin tome <player> <enchantment> <level>` | `nerdsmp.admin` | Grants a tome |

## 6. GUI flows (before)

- **Forge** (anvil, 54 slots, station layout): primary input 10, catalyst 14, action 22, socket
  row 28–34, tabs 1/3/5/7, Back 45, Close 53. It is **not** the brief's 13/23/45/46/49/52 layout.
  Inputs return to the player on close; clicks are generation-checked; changes need a review click.
- **Arcane Enchanting** (54 slots): held item at 13, offers 29/31/33, info 4 and 40, Back 48,
  Close 49. Offers open a before/after review screen with Confirm at 24.

## 7. Anti-cheat integration (before)

Watchdog's relevant behaviour:

- **Combat** (`onAttack`, HIGHEST): only `DamageCause.ENTITY_ATTACK` from a `Player` damager is
  checked for reach and hitbox. The combat ML model is fed from attack packets, so server-made
  damage never enters it.
- **Fast break** (`onBlockBreak`, HIGHEST): compares client dig ticks with break speed. It already
  skips **one** trusted pathway, `TreeHarvestBridge.automated(event)`: a thread-local scope bound
  to one player and one block for the duration of one synchronous `Player.breakBlock`.
- **Movement:** velocity, knockback, teleport and world-change events grant a short grace and
  reset movement prediction. There is no per-effect authorization API.
- **Bypass:** `nerdsmp.anticheat.bypass` and creative/spectator modes exempt a player entirely.

There is no existing trusted-action API for combat effects. Forge 2.0 adds one narrow scope
modelled on `TreeHarvestBridge` (see section 10).

## 8. Technical debt found

1. **Silent enchantment loss on re-encode (fixed).** `PaperItemService.enchantments` skipped
   unknown IDs and `encodeEnchantments` wrote back only known ones. Any enchant from a newer
   build, or one later renamed, was deleted the next time the item was enchanted. Forge 2.0 makes
   the codec lossless (`ArcaneEnchantmentCodec`).
2. **Tome preview disagreed with tome application (fixed).** The Forge preview used the material
   check `ArcaneEnchantment.supports`, while `PaperItemService.enchant` also accepts gear by stat
   (relic and authored items). Relic gear could be enchanted by a tome but the preview said it
   would not fit. The preview now uses `supportsEnchantment`.
3. **The enchanting table refused ordinary players (fixed).** `ArcaneEnchantingService.onTableUse`
   opened the screen through `MenuManager.open`, which requires the Premium remote permission
   (`nerdsmp.station.remote`, default false). Players at a table were told to visit a table. The
   table now uses `openStation`, as the Veyra NPC already did.
4. **Per-hit stat parsing.** `GearSetService.profile` re-decodes PDC on every damage event. Not
   changed; signature lookups read one PDC string per hit.
5. **Player-attributed damage is assumed to be a weapon swing.** `GearSetService.onDamage`,
   `onLifeSteal` and `AlchemyService.onPoisonStrike` treat every damage event with a player
   damager as a melee hit. Server-generated effect damage must be excluded explicitly.

## 9. Compatibility risks and decisions

| Brief requirement | Decision |
|---|---|
| Preserve the nine Aspects | They do not exist. Forge 2.0 introduces them as a **classification** of existing enchantments and gems for research and the Codex. They never change a stored value or a stat. |
| Grandfather Arcane Capacity | No capacity existed, so capacity is **derived** from rarity and quality, never stored. It is enforced only on new Forge 2.0 actions (Arcane Signatures, overcharge). The table, tomes and every existing item are unaffected. |
| Preserve corruption | No corruption existed. Added as an **opt-in, deterministic** trade (fixed bonus; faster wear, capacity and a work lock as the price) with guaranteed cleansing. No random penalties. Off by default. |
| Preserve Bloodletter, Veinseeker, Chain Harvest | They did not exist. **Created** as Specialized effect enchantments, each behind its own switch. The chaining ones use the Forestry pattern: native breaks through the full pipeline in a one-break trusted scope. |
| Enchanting profession 1–100 | Added `Skill.ENCHANTING` (0–50, the server's skill cap), following how Cooking was added. Gates read the higher of Enchanting and Smithing, so no unlock is lost. Enchanting pays no job Marks. |
| Attunement | No workflow existed. Added a bounded focus (+10% to matching enchantments' own bonus, capped at 25% by config; base stats untouched), free to clear. Off by default. |
| Finalized-item locks | None exist. Nothing to preserve. |
| Existing discoveries | None exist. Research knowledge is seeded once from enchantments already on a player's gear, and every table or tome enchant teaches it too. |
| `/forge` subcommands | `/forge` is a premium remote-station command pinned by `HubNavigationTest`. Changing its gate would alter existing access. The new, ungated reference command is **`/arcane`**; `/forge` is unchanged. |
| Signature naming | `SignatureAffix` already means catalog-bound relic effects. New build-defining enchantments are called **Arcane Signatures** to avoid two meanings for one word. |
| Bedrock | Not supported by the server today. All new screens still use plain left-clicks and lore, with no hover-only or resource-pack-only information. |

## 10. Modifications (summary)

| File | Purpose |
|---|---|
| `forge/arcane/*` (new) | Aspects, definitions and registry, codec, capacity, research rules, imbue pricing, feature flags, signature rules, telemetry. Mostly pure logic with unit tests. |
| `forge/arcane/ArcaneForge.java` (new) | Coordinator: research profile persistence, chosen imbuement and research transactions, signature binding. |
| `forge/arcane/ArcaneSignatureService.java` (new) | Stormwake, Heart of the Mountain and Last Bastion runtime, each off by default. |
| `forge/arcane/ArcaneMenus.java` (new) | Arcane Codex, Chosen Imbuement and Arcane Research screens. |
| `anticheat/ArcaneEffectScope.java` (new) | One-use, server-only trusted scope for one signature effect against one target. |
| `item/PaperItemService` | Lossless enchant codec, Arcane Signature data and tooltip section, item level accessor. |
| `item/GearSetService`, `alchemy/AlchemyService`, `anticheat/WatchdogService` | Skip signature damage inside an active `ArcaneEffectScope` only. |
| `listener/SkillListener` | Hands verified mining claims to Heart of the Mountain. |
| `forge/ArcaneEnchantingService`, `forge/ArcaneForgeService` | Record discoveries after table and tome enchants. |
| `gui/MenuManager` | Adds Codex, Imbuement and Research tiles to Arcane Enchanting; fixes tome preview. |
| `command/ArcaneCommands.java` (new), `CommandRouter`, `plugin.yml` | `/arcane` and `/forgeadmin`, `nerdsmp.forge.admin`. |
| `config.yml`, `deploy/test/config.yml`, `deploy/release/config.yml` | `arcane-forge` section with feature flags. |

## 11. Migration requirements

- **Items:** none. Legacy items are read as they are. Forge 2.0 writes one new optional key
  (`arcane_signature`) and only when a player binds a signature. Older builds ignore it.
- **Player data:** a new `arcane` section in each player document, created on first use. It has
  a schema version, and its seed from existing gear runs once and is idempotent.
- **Config:** new keys only; every default keeps current behaviour.

## 12. Tests required for behavioural equivalence

- Codec: legacy strings decode unchanged; unknown and malformed entries survive a re-encode.
- Capacity: a legacy item over the new capacity is reported as grandfathered and loses nothing.
- Discovery: seeding is idempotent and never removes knowledge.
- Table and tome paths: unchanged costs and rules (the existing `EnchantingRollsTest` still passes).
- Station refusals and plugin.yml gates unchanged (`HubNavigationTest`).
- Real Paper: item round trips through PDC with unknown entries, signature binding and tooltip.

See [ARCANE_FORGE.md](ARCANE_FORGE.md) for the finished design, and
[verification/ARCANE_FORGE_VERIFICATION.md](verification/ARCANE_FORGE_VERIFICATION.md) for results.

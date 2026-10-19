# Arcane Forge 2.0 verification

Date: 2026-10-18. Build: Paper API 26.2.build.121, JDK 25.0.4 (`.toolchains/jdk25`).

## Commands run

```powershell
$env:JAVA_HOME = (Resolve-Path .toolchains\jdk25\jdk-25.0.4.1+1)
./gradlew.bat --offline :nerdsmp-paper:build
.\scripts\run-zone-smoke.ps1
```

`run-zone-smoke.ps1` stops on any stderr when its output is redirected (`*>`), because a
pre-existing smoke helper prints a javac warning. Run it without redirection.

## Automated tests

`qualityGate` (all modules) passed: 1,421 tests across 249 classes in `nerdsmp-paper`, 0 failures,
0 errors (1 skipped, pre-existing).
The new tests:

| Class | What it proves |
|---|---|
| `ArcaneEnchantmentCodecTest` (6) | Known entries decode exactly as the pre-2.0 reader for 5,000 random strings; known-only items re-encode byte for byte; unknown and malformed entries survive an upgrade; a preserved entry can never override a known level. |
| `ArcaneRegistryTest` (6) | All 12 enchantments keep id, level, description and equipment rules on every material; every Aspect set resolves to at most one definition; validation rejects ambiguous recipes and wrong Aspect counts; every Aspect has a gem. |
| `ArcaneRulesTest` (8) | Defaults keep every new feature off except the Codex; invalid config is reported and falls back; capacity maths; grandfathered items can shrink but not grow; overcharge bounds; imbuement prices and Smithing gates; charges (one per tick, decay, cooldown); chain lightning bounded, no revisits, falloff. |
| `ArcaneResearchTest` (11) | Fundamentals in one experiment, Specialized in two; gem tier and Prismatic speed; fruitless repeats refused before spending and grant no XP; partial progress diminishes and never completes a larger recipe; signature research needs Smithing and a related mastery; receipts are idempotent; seeding runs once and only adds; YAML round trip keeps unknown ids; history and receipts bounded. |
| `ArcaneEffectScopeTest` (6) | One player, one target, bounded damage; ends with the call, even on exception; no nesting; malformed authorizations refused; thread-confined. |
| `ArcaneIntegrationTest` (9) | The Enchanting tree has four branches and no job branch, and every Enchanting talent has a consumer; effect enchantments have unique recipes and the right tools; with effects off the obtainable list is exactly the pre-2.0 list in order; attunement raises only matching enchantments, is capped and goes dormant when off; corruptions are valid fixed trades; new config sections are off in production and on in testing; the research talent scales progress only; the chain-break scope is one-use and non-nesting. |
| `ArcaneContractTest` (4) | Production config valid and all new features off; test config valid with all on; `/arcane`, `/forgeadmin` usage and `nerdsmp.forge.admin` match plugin.yml; `/forge` still Premium-gated; every outcome has a sentence and spending failures say nothing was spent. |

Existing tests, including `EnchantingRollsTest` (table costs and levels) and `HubNavigationTest`
(station gates), pass unchanged. Three contract tests were updated on purpose:
`GameplayExpansionContractTest` now pins the original twelve enchantments in order with three
appended, and `BonusConsumerCoverageTest` names the consumers of the three new stats and three new
talent stats.

## Real Paper server (disposable smoke)

Result: **PASS** (2,084 checks, all pre-existing checks included). Forge 2.0 checks, on real
`ItemStack`s, PDC, `PlayerDataStore` files and inventories driven through a stand-in player:

- legacy reader result is unchanged for items with unreadable entries
- enchanting keeps entries this build cannot read
- item schema stays authentic after Forge 2.0 writes
- signature and instability are stored on real PDC
- tooltip renders the Arcane Signature section
- refresh keeps the signature; stabilize clears only instability
- a weapon signature cannot be bound to a pickaxe
- an unknown signature id is kept, shown as dormant and never activates
- knowledge is seeded once from gear the player already owns
- no table: no station; a nearby enchanting table is a station
- research consumes exactly one gem and its Dust and discovers the fundamental
- discovery is persisted in the player document
- a known recipe is refused and spends nothing
- imbuement applies the exact level for the quoted Dust
- a stale reviewed item is rejected without spending
- too little Dust changes neither the item nor the Dust
- undiscovered enchantments cannot be imbued
- a full legacy item keeps everything and a signature that does not fit spends nothing
- binding spends 64 Dust and one gem of each Aspect exactly once and records the overcharge
- with effects on, table, tome and merchant pools include the effect enchantments
- Veinseeker stores on pickaxes only and exposes its vein size as a gear stat
- corruption applies exactly its listed bonus for its listed price
- a corrupted item takes no imbuement and spends nothing
- cleansing without enough Dust changes nothing
- cleansing removes exactly the corruption for its price
- attuning to an unrelated Aspect is refused for free
- attunement raises only the attuned enchantment's own bonus
- clearing an attunement is free and exact
- the Enchanting skill and its talent tree are loaded
- migration dry run is read-only and reports

Expected warnings: Enchanting XP was not awarded to the stand-in player, because the XP path needs
a real player. It is caught and logged; each action itself completed.

## Not verified

These need a real client on a staging server before the matching switch goes on:

| Area | Why not verified | Gate |
|---|---|---|
| Stormwake against Watchdog | No real attacking client in the harness. Unit tests cover the scope; the arc path is reasoned from code (cause `LIGHTNING`, scope check). | `signatures.stormwake.enabled` |
| Stormwake with PvP, parties, claims, NPCs | Each arc is a normal damage event, so these listeners run, but no real-world test was made. | same |
| Heart of the Mountain | `Block.getDrops(tool, player)` needs a real player. | `signatures.heart-of-the-mountain.enabled` |
| Bloodletter | Needs real melee hits; bleed ticks follow the Stormwake damage path. | `effects.bloodletter.enabled` |
| Veinseeker, Chain Harvest | `Player.breakBlock` needs a real player. Watchdog fast-break, claim and island-border behaviour untested live. | `effects.veinseeker.enabled`, `effects.chain-harvest.enabled` |
| Corruption wear | The doubled durability loss needs a real tool being used. | `corruption.enabled` |
| Enchanting XP and talents | Needs a real player's progression. | — |
| PostgreSQL rollback | A pre-Enchanting build rejects an `ENCHANTING` skill row; not exercised. | — |
| Last Bastion | Needs a real death. The totem fallback was confirmed in the server bytecode (`DeathProtection.TOTEM_OF_UNDYING` when no totem; damage bypassing invulnerability never fires the event). | `signatures.last-bastion.enabled` |
| Menus with a real client | Rendering, click flow and confirm screens were not clicked through. All clicks are cancelled by design. | `research.enabled`, `imbuement.enabled` |
| Knowledge save failure rollback | Not induced on a real disk. The code restores the original inventory in the same tick. | — |

| Bedrock | The server does not run Geyser. | — |
| Performance under load | Not measured. Stormwake is bounded (24 candidates, 3 targets, 12 particles per arc) and its slow-path counter is in `/forgeadmin telemetry`. | — |

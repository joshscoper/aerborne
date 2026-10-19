# Arcane Forge 2.0

Arcane Forge 2.0 adds knowledge and choice to enchanting. Players research enchantments by
combining socket gems, browse what they know in the **Arcane Codex**, bind a discovered
enchantment at an exact level with **Chosen Imbuement**, and, later, bind one build-defining
**Arcane Signature** per item. It also adds the **Enchanting** skill, three effect enchantments
(**Bloodletter**, **Veinseeker**, **Chain Harvest**), deterministic **Corruption** with guaranteed
cleansing, and **Attunement**.

Nothing that existed before changes. Arcane Enchanting offers, tomes, the Forge, every existing
item and every stat work exactly as they did. Everything new except the read-only Codex starts
**off** in production. Why each decision was made is in the
[integration report](ARCANE_FORGE_AUDIT.md); test results are in
[verification/ARCANE_FORGE_VERIFICATION.md](verification/ARCANE_FORGE_VERIFICATION.md).

## For players

| Feature | Where | What it does |
|---|---|---|
| Arcane Enchanting | Right-click an enchanting table | Unchanged: three random offers for Arcane Dust. Every enchantment you bind here, or with a tome, is recorded in your Codex. |
| Arcane Codex | `/arcane`, or the Codex tile on the table screen | Everything you have discovered, research clues, the Aspect encyclopedia, your history and a short guide. Works anywhere. |
| Arcane Research | `/arcane research` at a table | Pick one to three socket gems with different Aspects. One Aspect discovers a Fundamental enchantment, two a Specialized one, three an Arcane Signature. |
| Chosen Imbuement | `/arcane imbue` at a table | Pick a discovered enchantment and an exact level for the item in your hand. The price is shown first. Nothing is random. |
| Arcane Signatures | Chosen Imbuement | One per item: Stormwake, Heart of the Mountain, Last Bastion. Need free Arcane Capacity. |
| Enchanting skill | `/skills` | Every Arcane action earns Enchanting XP. Arcane requirements use your Enchanting level, or Smithing if that is higher. Talents: Arcane Study (XP), Dust Thrift (imbuement discount), Keen Insight (research speed), Signature Focus (shorter signature cooldowns). Enchanting pays no job Marks. |
| Corruption | Chosen Imbuement, Corruption tile | Pick one corruption for a fixed bonus. The item wears faster, uses capacity and takes no new Arcane work until cleansed. Cleansing always works. |
| Attunement | Chosen Imbuement, Attunement tile | Focus an item on one of its Aspects: its enchantments of that Aspect grow 10% stronger, and a matching signature cools down faster. Clearing it is free. |
| Mastery | `/arcane mastery` | Bind an enchantment at its top level three times, from any source, to master it: Chosen Imbuement then costs 25% less, and three-Aspect research opens for its Aspects. |

### Aspects

Every enchantment belongs to one, two or three of nine Aspects. Every socket gem carries one, and
that is what research uses. Prismatic gems carry none; they speed research up instead.

| Aspect | Gems | Enchantments |
|---|---|---|
| Might | Ruby | Keen Edge; with Precision, Executioner; with Vitality, Soulsiphon |
| Precision | Amethyst | Marksman |
| Aegis | Sapphire | Warding |
| Ward | Moonstone | with Affinity, Venombound |
| Celerity | Aquamarine | with Fortune, Angler |
| Vitality | Jade | Reaping |
| Resolve | Onyx | Preservation; with Fortune, Archaeologist |
| Affinity | Emerald | Scholar |
| Fortune | Topaz, Citrine | Prospector |

Signatures: **Stormwake** is Might + Celerity + Affinity, **Heart of the Mountain** is Fortune +
Resolve + Affinity, **Last Bastion** is Aegis + Vitality + Resolve. Aspects are a classification
only. They are not stored on items and never change a stat.

### Research, in short

- An exact match advances that recipe. Fundamentals are found in one experiment, Specialized
  recipes in two, Signatures in four (fewer with better gems or a Prismatic catalyst).
- A combination that is part of a bigger recipe gives smaller progress toward it, and a clue
  once enough is known. It shrinks with every repeat and never completes the bigger recipe.
- Using an Aspect for the first time studies it for the encyclopedia.
- The menu never reveals what an untried combination does, and it refuses, before anything is
  spent, a recipe you already know or a combination with nothing left to teach.
- Research XP comes only from discoveries and clues, so cheap repeats are worthless for XP.

### Messages players may see

| Message | Meaning |
|---|---|
| Stand beside an enchanting table in Survival, then try again. | Research and Chosen Imbuement are station actions. |
| Your held item changed. Review it again. Nothing was spent. | The item in hand is not the one that was reviewed. |
| You need more Arcane Dust. Nothing was spent. | The price was not met at the moment of confirming. |
| This item does not have enough Arcane Capacity. | Only signatures need free capacity. The tile states how much is missing. |
| This item is unstable. Stabilize it first. | An overcharged item must be stabilized before further binding. |
| An Arcane Forge operation is already in progress. | A second confirm arrived while the first was running. |
| Your research could not be saved, so nothing was spent. | The player file could not be written; the inventory was put back. |

## Screens

All screens use `MenuKit`: Back at 48, Close at 49, paging at 45 and 53, and the shared confirm
screen (summary 13, Cancel 20, Confirm 24) before anything is spent. No slot accepts items; every
click is cancelled and only left-clicks act. All information is in names and lore: nothing needs
a resource pack, hover-only text or a Java-only key.

```
Arcane Enchanting (table)            /arcane
 ├─ 37 Arcane Codex ───────────────┐  │
 ├─ 39 Arcane Research             ├──┴─ Arcane Codex
 └─ 41 Chosen Imbuement            │      ├─ Fundamental / Specialized / Arcane Signatures
                                   │      │    └─ entry detail (costs, how to get it, mastery)
                                   │      ├─ Aspect Encyclopedia
                                   │      ├─ Research in Progress, Research History
                                   │      ├─ How Enchanting Works
                                   │      ├─ 38 Chosen Imbuement ─ enchantment ─ level ─ confirm
                                   │      └─ 42 Arcane Research ─ pick gems ─ confirm
```

Tiles appear on the table screen only when their feature is on. In the Codex, a feature that is
off, or needs a table, is shown locked with the reason.

## Commands and permissions

| Command | Permission | Notes |
|---|---|---|
| `/arcane [codex\|research\|imbue\|mastery\|help]` (alias `/codex`) | none | Research and imbue need a nearby enchanting table. |
| `/forgeadmin help` | `nerdsmp.forge.admin` | |
| `/forgeadmin status` | | Switches, definition count, config problems, live signature state. |
| `/forgeadmin validate` | | Checks definitions and the `arcane-forge` section of `config.yml` on disk. Applies nothing. |
| `/forgeadmin reload` | | Applies the section only if it has no problems; otherwise the live settings stay. Audited. |
| `/forgeadmin debug <player>` | | That player's knowledge document and held item's capacity, signature and instability. |
| `/forgeadmin migration status` | | How many accounts have a profile. Items never need rewriting. |
| `/forgeadmin migration dry-run <player>` | | Read-only report of how every carried item is read. |
| `/forgeadmin anticheat status` | | Trusted-effect counters. |
| `/forgeadmin telemetry` | | Operation counters since start. |
| `/forgeadmin grant <player> <id> confirm` | | Grants knowledge for QA or recovery. Audited. Writes nothing to items. |

`nerdsmp.forge.admin` defaults to op and is a child of `nerdsmp.admin`. Audit lines go to the
server log and `plugins/NerdSMP/arcane/admin-audit.log`. `/forge` and `/enchants` are unchanged.

## Architecture

All new code is in `nerdsmp-paper/src/main/java/net/nerdsmp/paper/forge/arcane/`, except the two
anti-cheat scopes and the Enchanting skill (`Skill.ENCHANTING` and its tree in `nerdsmp-api`).

| Class | Responsibility |
|---|---|
| `ArcaneEnchantmentCodec` | Lossless `custom_enchantments` reader and writer. Pinned to the old reader by a randomized test. |
| `ArcaneAspect`, `ArcaneTier`, `ArcaneSignature`, `ArcaneDefinition` | The model. |
| `ArcaneRegistry` | All 18 definitions by id and by exact Aspect set. Validation rejects duplicate ids, ambiguous recipes and wrong Aspect counts. |
| `ArcaneSettings` | Immutable, validated snapshot of `arcane-forge`. Bad keys fall back to defaults (off) at startup; `/forgeadmin reload` refuses any snapshot with a problem. |
| `ArcaneCapacity`, `ImbueRules`, `ArcaneResearch`, `SignatureRules` | Pure rules, unit-tested without a server. |
| `ResearchProfile` | Copy-on-write account knowledge. Keeps ids it does not know. Bounded history and receipts. |
| `ArcaneForge` | Coordinator: knowledge persistence, Chosen Imbuement, signature binding, stabilization and research transactions. |
| `ArcaneMenus` | The screens. |
| `ArcaneSignatureService` | Signature runtime. |
| `ArcaneEffectService` | Bloodletter bleeds, Veinseeker and Chain Harvest chains, corruption wear. |
| `ArcaneCorruption`, `ArcaneItemEffects` | Corruption definitions; which item effects are live, read by the shared stat pipeline. |
| `ArcaneCommands` | `/arcane`, `/forgeadmin`. |
| `ArcaneTelemetry` | Fixed, anonymous counters. |
| `anticheat/ArcaneEffectScope` | The single trusted path for arc and bleed damage. |
| `anticheat/ArcaneBreakScope` | The single trusted path for chain breaks. |

Existing code touched, minimally: `PaperItemService` (codec; signature, corruption and attunement
data, stats and tooltips; item level), `ArcaneEnchantment` (three appended entries), `ItemStats`
(three stats), `ArcaneEnchantingService`, `ArcaneForgeService` and `MerchantTradeService` (record
binds, Arcane level, effect gating), `MenuManager` (three tiles, Enchanting in the skills menu,
tome-preview fix), `GearSetService`, `AlchemyService`, `WatchdogService` (scope checks),
`SkillListener` (one Heart of the Mountain hook), `DiminishingJobService` (older files without
Enchanting), `SkillTreeCatalog`, `NerdSmpPlugin` (wiring).

### Data ownership

| Data | Where | Owner |
|---|---|---|
| Enchantments | item PDC `nerdsmp:custom_enchantments` (unchanged format) | `PaperItemService` |
| Arcane Signature | item PDC `nerdsmp:arcane_signature` (STRING id) | `PaperItemService` |
| Instability | item PDC `nerdsmp:arcane_instability` (INTEGER, absent = 0) | `PaperItemService` |
| Corruption | item PDC `nerdsmp:arcane_corruption` (STRING id) | `PaperItemService` |
| Attunement | item PDC `nerdsmp:arcane_attunement` (STRING Aspect id) | `PaperItemService` |
| Enchanting skill | the existing progression storage, skill name `ENCHANTING` | `ProgressionService` |
| Arcane Capacity | derived from rarity, item level and quality; never stored | `ArcaneCapacity` |
| Knowledge | `player_data/<uuid>.yml`, section `arcane` (schema 1) | `ArcaneForge` |
| Signature runtime state | memory: charges and resonance per player, Last Bastion cooldown per account | `ArcaneSignatureService` |
| Effect runtime state | memory: bleeds per target (bounded), chains per player (bounded) | `ArcaneEffectService` |

Knowledge is account-wide and keyed by the stable account id, never by name.

### Transactions

Every action that spends items runs on the main thread in one tick:

1. **Validate** against the live inventory: survival, loaded account, nearby table, held slot and
   held item identical to what was reviewed, feature switches, knowledge, Arcane level, capacity.
2. **Reserve** by building the whole new inventory on clones. A missing gem or Dust ends here,
   with nothing touched.
3. **Commit** the inventory in one call, then save knowledge. If the save fails, the original
   inventory is put back before the method returns and the player is told nothing was spent.

A per-player lock refuses a second operation while one runs, the shared confirm screen accepts
one answer, and each research run carries a receipt id: applying it twice changes nothing, and its
Enchanting XP uses the same id as its idempotent fingerprint. Inventory, item and knowledge all live
in the same process and are committed in the same tick, so there is no cross-system window for a
journal to cover. A failed rollback is logged as `RECOVERY REQUIRED`.

## Anti-cheat compatibility

Watchdog's thresholds, checks and exemptions are unchanged. No player is exempted for wearing or
holding anything.

| Effect | How it meets Watchdog | Watchdog still sees |
|---|---|---|
| Stormwake arcs | Each arc is a new damage event with cause `LIGHTNING`, attributed to the player, inside an `ArcaneEffectScope` for that player, that one target and at most that damage, for one synchronous call. Reach and hitbox checks skip only that event. | Every real swing, all movement, clicks and packets. The combat model is fed from attack packets, which arcs never produce. |
| Heart of the Mountain | Breaks nothing. Its reward is extra drops of an already-final natural break. | Every break, including fast-break. |
| Last Bastion | Uses the server's own death-protection step; no movement, no velocity. | Everything. |
| Bloodletter bleeds | Each tick is a new damage event with cause `MAGIC`, attributed to the player, in the same one-use `ArcaneEffectScope`. | Every real swing. |
| Veinseeker, Chain Harvest | Each extra block is a native `Player.breakBlock` inside an `ArcaneBreakScope` for that player and that block. Watchdog's fast-break check skips only that break, which has no client dig to time. Protection, provenance, XP, drops and durability run as normal; a cancelled break ends the chain. | The player's own breaks, including the one that started the chain. |
| Codex, Research, Imbuement | Menus with no input slots; inventory written server-side in one call. | Everything. |

The scope also keeps weapon stats (`GearSetService.onDamage`), life steal and poison coatings from
treating an arc as a swing, and stops an arc from triggering another. It cannot be created from
any client input, cannot be nested, ends when the call ends even on an exception, and is confined
to the main thread. PvP opt-in, party, claim and NPC protections still apply to every arc.
Stormwake, Bloodletter, Veinseeker and Chain Harvest will not run with
`anticheat-bridge.enabled: false`.

**Not verified with real clients.** Watchdog's live behaviour against real players using Stormwake,
Bloodletter, Veinseeker or Chain Harvest has not been tested. Keep those switches off until the
checks under Rollout pass.

## Balancing

All numbers are in `config.yml` under `arcane-forge`; these are the defaults.

**Chosen Imbuement.** Price per level gained is the per-level price times the level: Fundamental 6,
Specialized 8. A Fundamental from none to V costs 6 × 15 = 90 Dust (about 23 lapis and 23
amethyst); Specialized costs 120. Mastery takes 25% off; Dust Thrift takes up to 12% more. Arcane level 10, 25 and 40 unlock levels
III, IV and V. The table's random offers stay at 4, 8 and 12 Dust, so luck remains the cheap
route and choice the reliable one.

**Research.** 4 Dust per gem plus the gems. Progress per experiment: Fundamental 100,
Specialized 50, Signature 25, times the average gem-tier multiplier (1.0, 1.25, 1.5, 2.0) and
1.5 with a Prismatic catalyst. Partial progress is 10 × multiplier ÷ (1 + repeats), capped at 60,
with a clue at 30. Unlocks: research at Arcane level 5, two Aspects at 10, three at 30 plus one
mastered related enchantment. XP: 40, 80 and 200 Enchanting for discoveries, 10 for a clue. Keen
Insight raises progress by up to 25%.

**Arcane level and Enchanting XP.** Every gate reads the higher of Enchanting and Smithing, including
the table's offer strength, so no existing player loses an unlock. Enchanting XP: 3 per Dust spent
at the table, 15 per tome level, 1 per Dust of Chosen Imbuement, 150 for binding a signature, 40 for
a corruption. Each award is fingerprinted and paid once.

**Arcane Capacity.** Rarity base (Starter 6, Common and Crafted 10, Uncommon 14, Rare 18, Epic
and Relic 22, Legendary 26, Mythic 30) + 2 per quality tier + 1 per 10 item levels. Costs:
Fundamental 1 per level, Specialized 2 per level, Signature 12. A signature on a full crafted
weapon does not fit: players choose between stacking enchantments and a signature, or use
better gear.

**Overcharge.** From Arcane level 20, a signature may exceed capacity by up to 25%. The excess becomes
Instability: the signature's cooldown is 5% longer per point, and the item takes no more binding
until it is stabilized for 4 Dust per point. Nothing is random and nothing is destroyed.

**Signatures.**

| Signature | Defaults |
|---|---|
| Stormwake | 5 full-strength (90%+) hits build charge, fading after 8 s idle. The fifth arcs to up to 3 enemies within 4 blocks of each other with line of sight, for 40% of that hit, 70% per jump. 8 s cooldown. Never strikes players, own pets, NPCs or armor stands unless `pvp: true`. |
| Heart of the Mountain | 48 natural ores (provenance-verified; placed ore never counts), then the next natural ore drops its vanilla drops a second time, at most 8 stacks. |
| Last Bastion | Lethal damage leaves you at 1 health, then 4 absorption hearts and Resistance II for 4 s. 600 s cooldown per account, kept across reconnects. A totem is used first. Void and `/kill` cannot be survived. Off against player killers unless `pvp: true`. Like a totem, it clears active potion effects. |

**Effect enchantments** (Specialized, each with its own switch, off by default):

| Enchantment | Aspects | Effect |
|---|---|---|
| Bloodletter | Precision + Vitality | Full-strength melee hits make the target bleed for 8% of the hit per level, over 3 s. A stronger bleed replaces a weaker one; bleeds never stack. Never against players unless `pvp: true`. At most 256 bleeding targets. |
| Veinseeker | Resolve + Affinity | Mining natural ore also mines up to 2 connected ores of the same kind per level. |
| Chain Harvest | Vitality + Celerity | Breaking a ripe crop also harvests up to 2 connected ripe crops of the same kind per level. Right-click harvesting is unchanged. |

Veinseeker and Chain Harvest are capped at 10 blocks per chain, 8 blocks per tick across the server
and 16 active chains. Sneaking, switching tool, walking more than 12 blocks away or any cancelled
break ends a chain. While an effect is off, its enchantment is never offered and stays dormant on
items.

**Corruption** is opt-in and deterministic: there are no random penalties.

| Corruption | Fits | Bonus |
|---|---|---|
| Bloodthirst | Melee weapons | +10% damage |
| Ironblood | Armor and shields | +4 defense |
| Greed | Tools and fishing rods | +8% skill XP, +8% work Marks |
| Fever | Bows and crossbows | +10% projectile damage |

The price: 48 Dust at Arcane level 25, 8 Arcane Capacity, durability lost 2x faster, and no Chosen
Imbuement, signature or attunement until cleansed. Cleansing costs 24 Dust, always succeeds, removes
exactly the corruption, and works even while corruption is switched off.

**Attunement** is bounded: at Arcane level 15, for 16 Dust, an item is attuned to one Aspect that
its enchantments or signature have. Those enchantments' own bonus rises by 10% (at most 25% by
config); the item's base stats never change. A signature sharing the Aspect cools down 15% faster.
Re-attuning costs the same; clearing is free. Corrupted items cannot be attuned.

## Migration, backup and rollback

- **Items need no migration.** Forge 2.0 reads the existing schema-5 items directly. New item keys
  (`arcane_signature`, `arcane_instability`, `arcane_corruption`, `arcane_attunement`) are written only
  when a player chooses those actions. The three effect enchantments are new ids in the existing
  `custom_enchantments` string.
- **Enchanting skill.** Existing players start at Enchanting 0 with nothing lost: every gate uses the
  higher of Enchanting and Smithing. Both storage backends store skills by name; YAML files without
  Enchanting load as level 0. Job files written before Enchanting existed load unchanged.
- **Older builds.** A build without Forge 2.0 ignores `arcane_signature` and
  `arcane_instability` and leaves them in place; the signature is simply dormant until Forge 2.0
  returns. Its old enchantment writer would drop entries it cannot read, which is why that writer
  was fixed here: any later build keeps unknown entries.
- **Knowledge** is created on first use and seeded once from the enchantments on the player's
  inventory, armour and ender chest. Seeding only adds.
- **Rollback** limits: an older build ignores signatures, corruption and attunement (dormant, kept).
  A pre-2.0 build would drop the three effect enchantments the next time such an item is enchanted,
  because its writer was not lossless. With **PostgreSQL**, a pre-Enchanting build cannot load a
  `skill_progress` row named `ENCHANTING` (it rejects unknown skill names). Before rolling back on
  PostgreSQL, move those rows aside; YAML storage is unaffected.

## Rollout

1. **Before.** Back up `plugins/NerdSMP/player_data/` and the worlds (see
   [operations](OPERATIONS.md)). Confirm the restore. Deploy with the shipped defaults: only the
   Codex is on. Run `/forgeadmin validate`, `/forgeadmin migration status` and
   `/forgeadmin migration dry-run <player>` on a few veterans.
2. **Research and Imbuement.** Set `research.enabled` and `imbuement.enabled`, then
   `/forgeadmin reload`. Watch `/forgeadmin telemetry` for `profile save failures`, `rollbacks`
   and `gui errors`, and the economy for Dust and gem prices.
3. **Overcharge**, when signatures are close.
4. **Attunement, then corruption.** Watch durability complaints and the economy.
5. **Each effect enchantment and each signature on its own,** first on a staff canary with `alerts-only` Watchdog: check that
   Stormwake produces no Reach or Hitbox evidence for legitimate fighters, that arcs respect PvP
   and claims, that Heart of the Mountain never pays on placed ore, and that Last Bastion never
   saves twice inside its cooldown. For Bloodletter, Veinseeker and Chain Harvest, also check that
   Watchdog shows no Fast-break, Reach or Hitbox evidence for legitimate players, that claims and
   island borders stop chains, and that placed ore pays nothing extra.
6. **Emergency stop.** Set `arcane-forge.signatures.enabled: false` (or the master `enabled`) and
   `/forgeadmin reload`. Bound signatures, effect enchantments, corruptions and attunements go
   dormant; nothing is removed, and cleansing and clearing still work.

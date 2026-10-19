# Island progression, Cooking and player economy

## Player entry points

- `/skills`: ten skills, including Cooking and its four talent paths.
- `/cooking` (also `/cookbook`, `/kitchen`): meal recipes, ingredients, level requirements and previews at your current Cooking quality. Craft at a table; the journal itself never grants items.
- `/island talents`: four vertical island specializations, with current and next-rank effects.
- `/achievements`: categorized milestone journal, unfinished filter and pagination.
- `/leaderboard [marks|adventurer|skills|island|achievements|cooking]`: offline-inclusive rankings and your position. Aliases: `/leaderboards`, `/top`, `/baltop`.
- `/pay <online-player> <whole-Marks>`: send earned Marks. `/marks pay` uses the same safe payment service. `/pay status` shows your latest eight incoming/outgoing receipts.
- Character and Workshop menus, quickstart and island tips point to the new systems.

## Gear sets

Wear matching armor and hold the matching weapon or tool. Only authentic, identified gear in its intended equipment slot counts; backpack items and duplicate/wrong-slot pieces do not. Set thresholds stack. The collection screen shows active/locked thresholds and progress to the final bonus, rather than asking players to equip every catalog variant at once.

Equipment damage bonuses now also affect ordinary weapons. Projectile profiles use the weapon recorded on the arrow, not whatever the shooter swaps to afterward. Existing defense, PvE life steal, XP, fishing, preservation and gathering consumers remain in place. Real Paper checks exercise all five earned rarity families, 4-piece armor thresholds, final 5-piece XP thresholds and Stormbound's actual damage-event increase on a plain sword.

## Build your island around a goal

Island levels remain capped at 50 and use the existing XP curve. Each even level awards a talent point: **25 total**, choosing from **48 ranks across 16 nodes**. Existing XP and purchased ranks are preserved; existing islands receive the additional point budget automatically. The owner spends points for the co-op.

Each node has three one-point ranks. Later nodes require rank two in their predecessor. Column depths open at island levels 2, 6, 14 and 26. Existing saved ranks are not retroactively removed.

| Path | Early investment | Deeper specialization |
| --- | --- | --- |
| Industry | Generator stone rerolls and better ore rolls | Mining/Smithing XP; stronger ore rerolls without bypassing resource stages |
| Nature | Crop growth and fertilizer | Eligible crop and log bonus yields |
| Automation | Stacked spawners and collection network | Shorter machine cycles; additional collector capacity and throughput |
| Stewardship | Pond luck and inherited livestock traits | Fishing/Cooking XP; longer-lasting meals prepared on the island |

At full Automation investment, existing spawner stacks still cap at 16, while Freight network raises the maximum to **14 chunk hoppers** and **112 items per collection pass**. Collectors still require one per chunk and enough claimed island space. The global per-pass work budget remains bounded. Busy workshop reduces machine cycle time by up to 30%; it does not reduce inputs/fuel, create extra outputs, load chunks or enable offline production.

Nature yield bonuses use the existing eligible-harvest/provenance checks. Industry never rolls ore ahead of the resource journal. Stewardship meal bonuses are saved into food at preparation time, so the co-op can trade its better meals.

Choices remain seasonal; this update does not add unrestricted island respecs or bypass installed automation capacity.

## Cooking and meals

There are **32 new recipes**, plus the six existing meals: **38 total**. Cooking is its own use-based skill, initially level zero for existing accounts; Mining, Farming, masteries and other existing progress are not reset. Level-one recipes are usable by beginners. Existing crafted legacy meals remain consumable.

Older schema-1 job saves do not contain Cooking counters. The job loader accepts that missing entry as zero without changing existing allowances, earned Marks, fractional bonuses, activity receipts or pending payments. The next successful job-state save adds the Cooking counters. Missing or malformed counters for an existing skill, or a partially present Cooking entry, still fail closed. No manual player-data edit or reset is required.

Successful table crafts award Cooking XP after confirming both ingredient consumption and received output. Shift-crafts count actual completed recipes. Eligible ordinary food crafts grant 12 XP per recipe; named meals grant 40 + 6 × required Cooking level, capped at 5,000 per crafting action before normal progression bonuses. Furnace extraction of ordinary cooked staples grants 6 XP per item, capped at 768 per extraction. Eating never grants XP or Marks. Decompressing honey/kelp/melon is not cooking work; hoppers do not award player extraction XP.

Chef quality is fixed when food is made, survives trading and cannot be upgraded by passing food to a higher-level eater:

- Duration = base × (1 + Cooking level/100 + Seasoning bonus/100 + co-op kitchen bonus/100), rounded and bounded to 5–300 seconds.
- Speed, Haste and Jump Boost meals reach level II at Cooking 40. Regeneration, Resistance and the other meal effects remain level I.
- Seasoning increases meal duration; Nourishment adds a percentage of the meal's restored saturation, within the normal food/saturation cap. Other Cooking paths offer skill XP and work-Marks bonuses.
- Hunger, saturation and container behavior use the underlying vanilla item. Existing native stew properties are preserved when styling ordinary crafted food.
- Named skill-gated meals cannot be made by automatic crafters. Ordinary automated food gets novice-quality buffs but no player Cooking XP.
- Prepared meals are not raw ingredients. Styled ordinary staples can still be used in appropriate recipes, so making bread or stew does not break later ingredient use.

Base durations below are before chef/talent bonuses. The in-game journal contains the exact patterns.

| Cooking | Meal | Buff | Base duration |
| ---: | --- | --- | ---: |
| 1 | Orchard Loaf | speed | 30s |
| 5 | Miner's Potato | haste | 45s |
| 12 | Angler's Chowder | water breathing | 60s |
| 20 | Forager's Stew | night vision | 90s |
| 30 | Homestead Supper | regeneration | 10s |
| 40 | Warden's Roast | resistance | 30s |

## Achievements and leaderboards

The default catalog now has **37 milestones**, including island development, resource stages, talent investment, Cooking, meal production and active set bonuses. The journal supports categories, unfinished/all filtering, progress, automatic one-time Marks rewards and multiple pages.

Existing unlock IDs are retained, including retired entries; their rewards are not replayed. Reward idempotency keys are unchanged. Catalog upgrades add new IDs while preserving operator edits and deliberately removed old entries. Cooking and achievement content keep pre-migration backups on upgrade.

Crafting achievements require actual ingredient/output movement and ignore creative mode. Fishing achievements follow accepted catch receipts rather than raw fish-event previews.

Leaderboards cache canonical account scores, including offline accounts. A bounded refresh processes at most 12 accounts per half-second, with at most 24 in-flight reads; after a complete successful snapshot it waits a minute. Failed reads keep the previous complete snapshot. GUI opening does not scan player files. Island rankings use owner island XP; total skill rankings use summed skill levels; Adventurer and Cooking use their XP; achievement rankings use points; Marks use spendable balances.

## Crafting protection

Gems, geodes, gear, machine items, pouches and other NerdSMP-tagged items cannot silently substitute for their vanilla base material in table/crafter recipes, smelting or fuel. Checks run at preview and commit. Smithing template/mineral slots, anvil repair ingredients and native brewing ingredients also reject special items. Intended forge, socketing, alchemy and item-recovery interfaces remain separate workflows.

Ordinary materials remain usable. Styled ordinary food is the narrow intentional ingredient exception; prepared named meals are not. This policy does not grant a generic bypass for arbitrary third-party item metadata or add special-ingredient authoring to the recipe studio.

## Payment recovery and operations

Payments accept whole positive Marks, up to 1,000,000,000 per send, between distinct authenticated online accounts. A short send cooldown reduces spam. Each receipt is stored at `plugins/NerdSMP/payments/<receipt-UUID>.yml`.

A receipt is durably prepared before any debit, then advances through debited to complete. Debit and credit have separate stable idempotency keys. Interrupted receipts retry automatically, including after restart; `/pay status` is the support reference. A sender cannot start another payment while a previous one is unresolved. Insufficient funds records a declined receipt. If a destination cannot accept funds (for example numeric overflow), the debited payment remains recoverable pending escrow; there is no ambiguous automatic refund.

The archive is bounded at 50,000 receipts and fails closed when full; plan retention/archival before reaching that limit. Do not delete pending receipts or mix player wallets and receipt directories from different backups. Existing local EconomyService.transfer is not crash-atomic; player payment commands no longer use it.

Deploy by backing up the server and uploading the rebuilt jar, then restart. Do **not** wipe player_data, replace existing content wholesale or use RefreshConfig. The operator's existing watchdog-panel.yml is separate private configuration and is not overwritten by this gameplay update.


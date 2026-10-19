# Equipment expansion

This is an additive expansion: 100 equipment IDs, eight six-piece sets, thirty crafting recipes, and ten drop-only Mythics. The full list is in [100_ITEM_CATALOG.md](100_ITEM_CATALOG.md). Everything uses vanilla textures. Existing worlds, inventories, accounts, recipes, and authored loot profiles are retained.

## Installation and configuration

Deploy the rebuilt plugin with a full server restart; do not use `/reload` or replace a running plugin jar. On first startup the plugin installs these independent files under its existing data directory:

- `content/skyforge-items.yml`: item, set, acquisition, and signature definitions.
- `content/skyforge-recipes.yml`: thirty skill-gated recipes, also available in the existing recipe browser.
- `content/skyforge-loot.yml`: one optional additional equipment roll for eligible hostile kills and vanilla chest generation.

The original `relics.yml`, `recipes.yml`, and `drop-tables.yml` are not replaced by this expansion. Existing expansion files are validated rather than overwritten or automatically refilled. Duplicate item/set IDs, malformed stats, incompatible signatures, or missing enabled loot pools stop initialization with an error instead of silently publishing partial content. Back up operator edits before changing these files. Set `enabled: false` in `skyforge-loot.yml` to disable the extra loot roll without deleting catalog items already owned by players.

## Acquisition and chase odds

Thirty Uncommon/Rare items are crafted. Seventy items are loot-only; craft-only items are excluded from the extra loot pools. A successful roll grants one item, with one normal affix on Uncommon/Rare, two on Epic/Legendary, and three on Mythic. Eighty percent arrive unidentified. The usual identifier, bulk identification, scrapping, trading, banking, sockets, and workmanship systems apply. Mythic signatures stay concealed and inactive until identification.

Default odds are per eligible event, not per item in a mob stack:

| Source | Extra equipment | Any Mythic, absolute | A particular Mythic, absolute |
|---|---:|---:|---:|
| Standard hostile | 1.2% | Never | Never |
| Elite | 12% | 0.06% | 0.006% |
| Boss | 50% | 2.5% | 0.25% |
| Vanilla chest | 10% | 0.01% | 0.001% |

The YAML rarity weights are conditional on the extra-equipment roll succeeding. Selection within a rarity is uniform. These are independent random chances, not guaranteed drops or a pity timer. Distance and Looting do not multiply these chase odds. Player kills, unlinked/creative killers, mechanic objectives, and staff-authored `REPLACE` loot tables do not receive the extra kill roll. Plugin-generated inventories and non-chest loot tables do not receive the chest roll. Existing base loot continues unchanged.

## Named Mythics

Each signature is bound to its authenticated catalog ID and equipment type. Renaming an item or copying a lore line cannot activate one. Normal equipped-slot requirements, existing bonus caps, and activity eligibility checks still apply. Signature bonuses are added once after workmanship scaling; they are not multiplied by the item's quality tier.

| Item | Signature | Additional effect |
|---|---|---|
| Sunforge Pickaxe | Furnace Heart | Converts eligible native ore drops and grants Smithing XP alongside normal Mining XP. |
| Faultbreaker | Deep Resonance | +35% geode chance and +4% excavation finds. |
| Harvest Moon | Harvest Moon | +20% harvest yield and +2 gathering power. |
| Tideweaver | Tideweaver | +20% fishing luck and +2 fishing power. |
| Second Dawn | Second Dawn | +2% life steal and +3 defense. |
| Mercy of the Hunt | Hunter's Mercy | +25% damage against enemies below 30% health. |
| Astral Insight | Astral Insight | +10% activity skill XP and +5% job Marks. |
| Everlasting | Everlasting | +30% chance to prevent durability loss. |
| Venom Heart | Venom Heart | +35% poison potency; requires a poison coating. |
| Stormstring | Stormstring | +25% projectile damage. |

These effects are in addition to each item's displayed base stats and rolled ordinary affixes. Percentages above are bonus contributions, not promises of an uncapped final value. For example, execute damage still requires a wounded target, and life steal follows the existing eligible-combat rules.

## Furnace Heart safeguards

The server's smelting progression skill is named **Smithing**. Furnace Heart leaves the existing Mining reward path intact and adds verified processing XP for converted drops. It does not issue a second job payment, duplicate island work, synthesize furnace extraction rewards, or consume fuel. The XP uses the existing ore-smelting values and normal processing bonuses.

Conversion preserves native drop quantities, including Fortune results. Silk Touch, player-placed ore, cancelled harvest/drop events, unidentified items, changed tools, and non-native/named drops are not eligible. Resource provenance and a separate signature receipt prevent replaying the same harvest. Pending conversions are bounded and cleared during orderly shutdown. This is a native block-drop effect, not a machine, furnace, inventory-wide smelter, or automatic collection system.

## Verification limits

Automated tests cover catalog counts and supported stats, recipe collisions, strict file validation, weighted loot boundaries, metadata/serialization, identification, actual equipped set totals, and the processing receipt path. The disposable Paper harness uses an isolated loopback-only server and does not modify the player server.

Before public deployment, run an authenticated Survival client pass for native Fortune/Silk Touch mining, full inventories, hopper pickup, protection-plugin cancellation, tool breakage, GUI identification, market transfer, and tooltip appearance. Automated controlled-event tests are not a substitute for native-client or production-load certification. Existing release gates still apply.

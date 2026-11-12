# Item template verification — 2026-10-25

**PASS.** Packaged locally; not deployed to the live server.

Every native item type and the server's known obtainable custom families now have a category
template or retain their existing richer template. The shared frame uses dark dividers, a green
category, a description or original instructions, and non-italic text. Native names remain
localized and use rarity colors. Food retains its gold PROVISIONS template; prepared meals,
catalog equipment and forge items preserve their detailed stats, effects and identities.

## Coverage

The Paper 26.2-121 fixture passed **23,253 assertions** over
**3,907 variants/results**. The native-material sweep includes
creative-only types as a coverage superset; it does not make them obtainable in Survival.
Recipe results overlap item types and are counted separately, rather than as unique items.

| Factory or result family | Variants/results |
| --- | ---: |
| native | 1536 |
| catalog | 351 |
| geode | 3 |
| gem | 55 |
| tome | 75 |
| skill-lamp | 3 |
| machine | 4 |
| collector | 1 |
| coin-purse | 1 |
| expedition-supply | 3 |
| native-potion | 138 |
| arcane-brew | 8 |
| poison-vial | 8 |
| poison-arrows | 8 |
| antidote | 1 |
| recipe-result | 1712 |

For every entry, the audit checked a nonempty template, unchanged input object, idempotent updates,
unchanged quantity and binary item serialization. Native items also round-tripped to their exact
semantic original, except food's established deliberate canAlwaysEat override. Catalog identities
and stats stayed authentic. Potion contents, lamp grades, currency identity and expedition cleanup
tags were retained.

Additional checks covered enchanted/damaged tools, player custom names, custom lore and quest
metadata, edited-tooltip rejection, nested shulker contents, material and potion name changes,
ordinary recipe eligibility, alchemy's actual inventory transaction, immediate drop presentation,
and automatic presentation after raw inventory grants.

## Native client actions

One Mineflayer 26.1 client connected through ViaVersion/ViaBackwards. On a seeded disposable arena,
it crafted four planks from one templated log at a real crafting table, took exactly three ingots
from a real furnace, and mined one stone into one templated cobblestone using a templated tool.
The server also completed three consecutive native smelts in each of a furnace, blast furnace and
smoker, checking output merging and templates. All final inventory stacks had templates.
The fixture uses a fresh arena and test-account land-protection and Watchdog bypass permissions
to isolate item behavior. These permissions are not granted by the delivered production plugin.
This audit does not validate claim rules or Watchdog enforcement.

Alchemy compatibility was tested through the real server inventory transaction method. Custom
factory audits call the real item constructors. This is focused template and compatibility
verification, rather than a claim of a full progression or economy playthrough.

## System fixes

The visual layer only strips exact fields it owns for semantic comparisons. Real item metadata
remains protected. Recipe tools, vendor items, economy recipe inspection, replanting, quest
delivery, alchemy and island processing recognize ordinary templated materials.

Native furnaces compare output components before firing the smelt event. Cooking recipe outputs
therefore use the same canonical templates as inventory stacks; recipe keys, inputs, XP, time,
group and category remain unchanged. Existing furnace storage migrates at initialization and
chunk load. Player inventory changes use a coalesced queue bounded to four players per tick.

Early failed audits caught an explicit-rarity-only API call, mutation of the raw input during
presentation, and general metadata writes synthesizing blank signed-book content. These were
corrected before this passing run. The shared frame writes only its own components and tags.
Repeated-smelting checks also caught an in-event output-slot replacement detaching the native
stack reference and losing subsequent results. That write was removed; migration happens before
smelting through recipe registration and storage updates.
Earlier failed-run evidence is
included separately in the evidence ZIP.

## Build and delivery

The offline Gradle qualityGate and shadowJar passed: **1,727 tests
passing**, 0 failures, 0 errors and 1 existing skip.
The packaged plugin is byte-identical to the tested JAR and contains no probe classes.

Watchdog's complete bundled config uses `alerts-only` and `ml.autonomous: false`. No new
configuration section is required. The fixture's server and bot exited cleanly. The host's
existing Windows OSHI Perflib 009 error is the only allowed ERROR log line; it is unrelated to
item templates. This bulk factory audit is not a server performance benchmark.

Replace the plugin JAR and restart. Existing items migrate when encountered, with no player-data
reset. The included config is the complete bundled default; preserve additional live customizations.
The PNG preview uses actual exported components with a local preview font and English native
names. Minecraft renders localized names in its own font.

JAR SHA256: `1bfecfde25b433dc536d99c9599e923b4201f14d259e196c64776856dfb220bb`

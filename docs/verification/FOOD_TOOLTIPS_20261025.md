# Ordinary-food tooltips — 2026-10-25

Status: **PASS**. Packaged locally; not deployed to the live server.

Ordinary food has a localized gold name, dark dividers, a green PROVISIONS category,
white Well Fed duration and gray benefit/stacking guidance. Values come from the active
recovery configuration. Prepared Cooking meals keep their HARVEST MEAL template.

The native fixture passed **282 assertions across all 44
native food materials**, including exact lore/color checks, non-italic text, stacking,
idempotent updates and binary item serialization. Another configuration was temporarily
applied to verify old templates refresh without duplicate lines, then restored.

Renamed food, extra lore, unrelated custom tags and existing custom presentations remain
protected. The real recipe predicate accepts ordinary provisions and rejects renamed ones.
NPC preview/delivery/buyback templates match independently styled food. Replanting still
accepts ordinary carrots.

One native Mineflayer 26.1 client on Paper 26.2-121, with ViaVersion/ViaBackwards, ate one
templated bread at full hunger and received Well Fed. It then crafted a golden carrot
at a real crafting table using the templated carrot and eight nuggets. The result kept
the Cooking staple template, consumed exactly the inputs, and remained eligible for
later recipes. Inventory and surfaces were seeded; this is targeted feature verification.
Some compatibility assertions call the real consumer methods directly; they do not claim
a full native vendor or farming playthrough.

The offline Gradle qualityGate and shadowJar passed: **1727 passing
tests, 0 failures, 0 errors and 1 existing skip**.
Watchdog remains configured to `alerts-only` with model autonomy disabled. No anti-cheat
enforcement code was changed. The server and bot exited cleanly; the log contains the host's
existing Windows OSHI Perflib 009 warning and no unexpected errors.

The preview PNG renders actual exported component text and colors using a local preview
font. It is not an in-game screenshot; Minecraft renders the tooltip in its own font.

Install the new JAR and restart. Existing ordinary food updates through the item updater
and inventory refresh without losing quantity. No player-data reset or new config section
is needed. The included full config preserves the alert-only Watchdog settings; preserve
any additional live configuration customizations.

JAR SHA256: `c8a3cf5cc0b3dab11b46ae975bc4f9e8ef482eeff33ecca1129a0ef9f14913f2`

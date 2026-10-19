# Aerborne brand reference

**Minecraft Skyblock RPG** · **Small beginnings. Endless skies.**

Supporting line: **Your adventure begins above the clouds.**

Aerborne is a welcoming fantasy adventure above the clouds: co-op islands, purposeful
exploration, crafting and discoveries. Present deep RPG systems through clear choices, familiar
Minecraft objects and short, useful explanations. This reference changes identity and presentation,
not balance, progression or access to features.

## Palette

| Color | Hex | Use |
| --- | --- | --- |
| Aerborne Sky | `#77C8F5` | Primary brand, headers |
| Cloud White | `#F4F9FF` | Key text, highlights |
| Breeze Cyan | `#8CE8ED` | Magical accents |
| Cloud Mint | `#70DBC3` | Nature and success accents |
| Dream Lavender | `#C59AFA` | Celestial details |
| Relic Gold | `#F4C76B` | Important details |
| Sunset Peach | `#FFBBA0` | Warm accents |
| Deep Twilight | `#283752` | Dark surfaces |
| Midnight Sky | `#17243B` | Backgrounds |
| Soft Stone | `#8E9AAF` | Neutral accents |

Use one main color and one accent per surface. Prefer light text on dark surfaces. Check labels
against actual Minecraft sky, terrain and GUI backgrounds; a hex value alone is not a contrast
test. Preserve established rarity colors, red refusal/warning signals and green success feedback.
Do not recolor gameplay categories merely to match a promotional palette.

## Voice and interface conventions

- Write warmly and directly: “Your island is ready.” or “Bring Arcane Dust to the forge.”
- Name the next action and explain missing requirements. Avoid technical jargon, enum labels,
  repeated brand prefixes and claims that a system is complete when it is not.
- Keep existing feature identities: Your Island, Arcane Forge, Angler's Journal and Quest Log
  remain distinct destinations. Do not rename mechanics just to make them sound branded.
- Use Minecraft's readable default font and familiar icons. No paid font, client mod or resource
  pack is required for essential information. Optional promotional type must be properly licensed.
- Keep transaction confirmations, navigation positions, permission checks and item-return behavior.
  Branding must not occupy functional slots or remove useful scoreboard information.
- Use `plugin.displayName()` for the public name and shared Chat/MenuKit helpers for messages
  and inventories. Literal player/configuration text must remain escaped.

The [style guide](STYLE_GUIDE.md) owns detailed chat, command and menu conventions.

## Compatibility boundaries

Aerborne is the public identity. The internal plugin name remains `NerdSMP`; module filenames,
`plugins/NerdSMP`, `net.nerdsmp` Java packages, `nerdsmp:` item/PDC/asset namespaces, permission
nodes, account IDs, seasons, world names and database identifiers stay unchanged. Do not rename
them as a cosmetic cleanup. Existing operator-selected names and external URLs require deliberate
review, not a broad replacement. Only recognized stock legacy names and stock presentation copy
are adapted to Aerborne in memory; existing configuration files are not rewritten. `/aerborne`
and the compatibility alias `/hearthsky` use the existing menu handler rather than new commands
with separate behavior.

Staff may use `/aerborneadmin` as an alias for `/nerdadmin`; it retains the same restricted
handler and permission. Store fulfillment and existing staff commands remain valid.

Historical filenames such as `HEARTHSKY_RELEASE.md` and dated verification records remain in
place so links and evidence still refer to their original builds. A historical name in one of
those records is not a current player-facing brand leak.

This project remains Java-only. The identity change does not introduce Bedrock support or add
new cross-play dependencies. Previously saved identities retain their existing recovery behavior.

## Artwork and cosmetic direction — not implemented content

Use chunky, block-built fantasy silhouettes, expressive faces, pastel accents and small magical
details. Future companions may use the classic armor-stand sculpture aesthetic: distinct block,
head and item components with independently animated features. This is an art direction, not a
new rendering dependency or a claim that these companions have been built.

Reserved concepts for future, separately reviewed content:

| Category | Concepts |
| --- | --- |
| Supporter rank concepts | Skyfarer, Cloudkeeper, Starweaver |
| Companion concepts | Pufflet, Kitekit, Mossbun, Cindlet, Moonmoth, Starwhisk |
| Trail concepts | Cloudstep, Petal Skip, Firefly Drift, Rune Ripple, Bubblehop, Comet Tail, Dandelion Drift, Stardust Waltz |

These names do not create products, ranks, permissions, ownership grants or fulfillment packages.
Existing subscriptions and entitlements remain authoritative. Any future store change requires a
separate compliance and entitlement review; gameplay power is not a cosmetic benefit.

## Manual asset and external-service checklist

- Commission an original Aerborne wordmark and a readable 64×64 PNG Minecraft server icon.
  No replacement icon or logo is delivered by this text-only reference.
- Prepare licensed floating-island illustrations, cloud backgrounds, social banners and
  promotional captures. Do not substitute unrelated art or copy another server's assets.
- Review the actual Tebex storefront, Discord artwork, social profiles and public listings with
  the owner. Repository text changes do not modify those external accounts.
- Keep existing domains, server addresses, webhook URLs, authentication callbacks, tokens,
  channel IDs and Tebex package IDs. No new address or live redirect is implied.
- Before public use, verify name/domain clearance, store/legal disclosures, authentic client
  presentation and the existing [release gates](HEARTHSKY_RELEASE.md).

The release remains a development candidate until its separate gameplay, recovery, security,
content and performance gates are met. A consistent brand is not a public-launch certification.

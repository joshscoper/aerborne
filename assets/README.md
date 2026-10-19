# Asset pipeline

`shared-manifest.json` is the source of truth for custom item identity. `nexo-pack/` is the optional Java source pack that Nexo merges, generates, hosts, and dispatches. Stable `nerdsmp:<item-id>` ItemModel keys remain owned by the existing NerdSMP plugin; the Aerborne rebrand does not change them.

The checked-in Nexo pack contains valid vanilla-model fallbacks until commissioned textures, models, sounds, and licensed fonts are delivered. Production packaging must fail if any manifest item lacks:

1. a Nexo-merged Java ItemModel and appropriately licensed model/texture;
2. an in-game Java visual review at supported GUI scales.

Aerborne is Java-only. Bedrock packs, mappings and Scaffolding are not release dependencies. The manifest deliberately contains no Bedrock identifiers. Historical account identities remain readable for recovery, but new cross-platform links are not supported.

Do not substitute copied Minecraft assets or unlicensed marketplace content.

No pack is required for the default vanilla-texture presentation. If Nexo is enabled, do not configure a second Java resource-pack dispatcher. Nexo is the only supported owner of pack generation, hosting, hashes, prompts, and reload dispatch.

Aerborne's new server icon, wordmark, companion art and promotional images are not included. See [the brand reference](../docs/AERBORNE_BRAND.md) for the artwork brief and manual follow-up checklist. Do not rename existing asset namespaces or the `external_packs/NerdSMP` import directory for cosmetic consistency.

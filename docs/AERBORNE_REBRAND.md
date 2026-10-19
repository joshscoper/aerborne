# Aerborne rebrand: integration and deployment

This release changes identity and presentation only. It does not rebalance gameplay, alter Forge
calculations, activate signatures, add cosmetic products, or certify a public launch. Existing
in-progress Forge code is preserved; its separate audit and rollout requirements still apply.

## Audit and compatibility classification

The source audit searched case-insensitively for `HearthSky`, `Hearth Sky`, `hearth-sky` and
`hearth_sky`, including project configuration. Public-name consumers were traced separately:
the name feeds presentation and release diagnostics, not persistent keys. Git internals,
dependency caches, binaries and generated installations are not current deployment templates.

| Class | References and decision |
| --- | --- |
| A — current public branding | Shared Paper branding, sidebar/tab/welcome, quickstart/menu chrome, plugin descriptions, edge status, deployment MOTDs/defaults, staff-console resources and current guides now use Aerborne. |
| B — implementation identity | Keep `NerdSMP`, `NerdSMP Edge`, `nerdsmp-edge`, `net.nerdsmp`, Gradle modules/artifacts, command handlers and permissions. New entry points are aliases, not duplicate handlers. |
| C — persistent identity | Keep `plugins/NerdSMP`, `player_data/<uuid>.yml`, account IDs, PDC/asset namespaces, item schema/enchantment IDs, season/world/island/region IDs, SQL migrations and store receipts. No migration is required. |
| D — external integrations | Preserve addresses, ports, domains, webhook/callback URLs, credentials, channel IDs, proxy secrets and Tebex package IDs. No live external-account or firewall changes were made. |
| E — historical evidence | Retain dated changelog/audit/verification records and old `HEARTHSKY_*.md` filenames. Current guide headings/link labels use Aerborne; historical links remain valid. |

Remaining old-name occurrences are intentional: `/hearthsky`, legacy-setting regression fixtures,
the legacy-name adapter, and the release-check link to `HEARTHSKY_RELEASE.md`. Historical records
include `CHANGELOG.md`, `CODE_REVIEW_2026-09.md`, `POLISH_AUDIT.md`, `ARCANE_FORGE_AUDIT.md`,
`HEARTHSKY_PROGRESSION.md`, and `PUBLIC_BRANDING_VERIFICATION.md`. They describe earlier builds
and were not rewritten. Previous jars, logs and generated runtimes remain recovery/evidence
artifacts. They are not silently updated or deployed as part of the rebrand.

## Implementation

- `BrandPalette` owns the ten colors, public name, primary tagline and supporting line.
- `ServerBranding` supplies escaped components for the MOTD, tab, welcome and sidebar. The
  server-list/sidebar headline is uppercase; other copy uses Aerborne naturally.
- `NerdSmpPlugin.displayName()` retains the original configuration paths. `plugin.yml` retains
  `name: NerdSMP`, the main class, API version and every historical permission namespace.
- `MenuKit.frame` and the main-menu renderer share the existing geometry with sky-blue accents.
  Forge input, confirmation, close, item-return and navigation behavior are unchanged.
- Quickstart, daily-contract and faction/milestone headings share the palette. The Codex title
  uses Lavender/Breeze. Rarity, gem/signature, warning and health colors retain their meanings.
- The staff console keeps every route, authentication rule and action handler. Only visible
  titles, CSS and colors on its existing shield SVG change. Light-theme colors use readable
  derivatives; dark-theme body copy and brand accents are contrast-tested. No PNG server icon
  or newly commissioned logo is claimed.

## Commands and permissions

`/aerborne`, `/hearthsky`, `/nexus` and `/menu` resolve to the same existing menu command.
`/aerborneadmin` aliases `/nerdadmin`, retaining `nerdsmp.admin` and the original store-fulfillment
handler. Other commands and permissions remain valid. The complete alias roster is collision-tested.

## Read-only configuration compatibility

Fresh templates use Aerborne; existing files are never replaced or rewritten by the adapter.

- Exact stock names matching `hearth[ _-]?sky`, case-insensitively after trimming, display as Aerborne.
- The exact stock tagline `Your island. A wider world.` displays as `Small beginnings. Endless skies.`
- The exact stock description `Co-op islands • Quests • Player economy`, including its ASCII-hyphen
  variant, displays as `Build your island. Explore the skies. Discover magic.`
- Custom names/copy remain escaped and intact, even if a longer custom title contains the old word.
  Owners must review deliberate custom branding themselves.
- Invalid/blank/oversized/multiline values use bounded defaults. The original server-list opt-out
  still works. A live proxy's independent MOTD needs a separately reviewed presentation-only merge.

No storage backend, anti-cheat policy/threshold, privacy retention, season, world, bind address,
credential, purchase or progression value is migrated. The existing Java-only target is retained;
the rebrand does not add Geyser/Floodgate or change historical identity recovery.

## Deployment checklist

1. Verify recoverable backups of plugin data, worlds, player files, databases and store receipts.
   Keep the previous jar/configuration/dependency versions and known-good restore instructions.
2. Stage the jar and check authenticated login, old enchanted equipment, island home, currency,
   XP, purchases, permissions, Forge operations and console authentication.
3. Stop the player server normally. Replace one gameplay jar; retain the original plugin data
   directory and worlds. Do not install duplicate jars or use `/reload`.
4. Merge only reviewed MOTD/presentation settings into an existing Velocity/server-properties file
   if required. Preserve addresses, topology, forwarding secrets and authentication decisions.
5. Start and check `/aerborne`, `/hearthsky`, `/menu`, `/aerborneadmin releasecheck`, the tab,
   sidebar, welcome, quickstart, shops, Forge and NPC interfaces.
6. Compare returning-player state to backup. Verify staff access and Watchdog with the original
   thresholds/policy. The rebrand grants no exemptions or moderation permissions.
7. Access the staff console through its existing protected route. This release neither publishes
   a listener nor sets credentials, changes ports, adds TLS or changes firewall rules.

Emergency presentation rollback: stop normally and restore the previous jar. Do not delete player
files, rename worlds/data folders, rewrite PDC or down-convert newer equipment. Rollback of separate
Forge changes must follow that work's data-version guidance; branding writes no new item/player state.

## Manual follow-up and limits

Review the external Tebex storefront, Discord identity/artwork, listings, social profiles and
domain/name clearance with the owner. Keep webhook/package/authentication IDs unchanged.
Commission the actual wordmark, 64×64 Minecraft icon and licensed promotional art separately.
Rank/companion/trail names in [AERBORNE_BRAND.md](AERBORNE_BRAND.md) are future concepts, not products
or ownership grants delivered by this release.

Authenticated-client visuals, live proxy deployment, multi-client interactions, load testing,
forced-crash/restore drills and external-service verification remain manual gates. See the
[release checklist](HEARTHSKY_RELEASE.md); branding consistency is not launch approval.

Exact commands, test results and artifact fingerprints are in
[AERBORNE_REBRAND_VERIFICATION.md](AERBORNE_REBRAND_VERIFICATION.md).
